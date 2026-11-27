package com.example.data

import com.example.auth.DepotRoleManager
import com.example.base.FirestoreEmulatorTestBase
import com.example.model.DraftSlotEntry
import com.example.model.DutyArea
import com.example.model.DutyApprovalRequest
import com.example.model.DutyDraftStatus
import com.example.model.EmergencyOverrideLog
import com.example.model.LeaveRecord
import com.example.model.LeaveStatus
import com.example.model.LeaveType
import com.example.model.ShiftSlotAssignment
import com.example.model.ShiftType
import com.example.model.SlotRoleType
import com.example.model.UserRole
import com.example.model.WeeklyRestDay
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

class RosterRepositoryRuleTest : FirestoreEmulatorTestBase() {

  private suspend fun signInVerifiedAdmin(): String {
    signInTestUser(DepotRoleManager.ADMIN_EMAIL)
    if (auth.currentUser?.isEmailVerified != true) {
      withContext(Dispatchers.IO) {
        val token = auth.currentUser?.getIdToken(false)?.await()?.token.orEmpty()
        val url = URL("http://127.0.0.1:9099/identitytoolkit.googleapis.com/v1/accounts:update?key=fake-api-key-for-emulator")
        val conn = (url.openConnection() as HttpURLConnection).apply {
          requestMethod = "POST"
          doOutput = true
          setRequestProperty("Content-Type", "application/json")
        }
        conn.outputStream.use { os ->
          os.write("""{"idToken":"$token","emailVerified":true,"displayName":"Vinod Chinthawar"}""".toByteArray())
        }
        val code = conn.responseCode
        check(code in 200..299) { "Auth emulator accounts:update failed with HTTP $code" }
        conn.inputStream.use { it.readBytes() }
        conn.disconnect()
      }
      auth.signOut()
      signInTestUser(DepotRoleManager.ADMIN_EMAIL)
    }
    return checkNotNull(auth.currentUser?.uid) { "Verified admin uid missing" }
  }

  @Test
  fun sharedDepot_adminSeedsRoster_andSignedInTechnicianCanReadSharedData(): Unit = runBlocking {
    val adminUid = signInVerifiedAdmin()
    val adminRepo = FirestoreRosterRepository(firestore)
    adminRepo.ensureUserProfile(adminUid, DepotRoleManager.ADMIN_EMAIL, DepotRoleManager.ADMIN_DISPLAY_NAME)
    withTimeout(DEFAULT_TIMEOUT_MS) { adminRepo.seedDefaultRosterIfEmpty() }

    val techEmail = "tech_${UUID.randomUUID()}@depot.test"
    val techUid = signInTestUser(techEmail)
    val techRepo = FirestoreRosterRepository(firestore)
    val profile = techRepo.ensureUserProfile(techUid, techEmail, "Tech Viewer")
    assertEquals(UserRole.TECHNICIAN, profile.roleEnum)

    val staff = withTimeout(FLOW_TIMEOUT_MS) {
      techRepo.observeStaff(DepotRoleManager.SHARED_DEPOT_ID).first { it.size == 35 }
    }
    assertEquals(35, staff.size)
    assertTrue(staff.any { it.name == "VIVEK CHOUDHARY" && it.isPointsman })

    // Technician cannot read all userRoles (PII isolation) or add staff
    try {
      withTimeout(FLOW_TIMEOUT_MS) { techRepo.observeAllUserProfiles().first() }
      fail("Expected PERMISSION_DENIED when non-Admin lists userRoles")
    } catch (e: Exception) {
      val ffe = e.unwrapFirestoreException()
      assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, ffe?.code)
    }
  }

  @Test
  fun supervisorApprovalAndLocking_updatesSharedAssignmentsAndBlocksStoremateReEdit(): Unit = runBlocking {
    val adminUid = signInVerifiedAdmin()
    val adminRepo = FirestoreRosterRepository(firestore)
    adminRepo.ensureUserProfile(adminUid, DepotRoleManager.ADMIN_EMAIL, DepotRoleManager.ADMIN_DISPLAY_NAME)
    withTimeout(DEFAULT_TIMEOUT_MS) { adminRepo.seedDefaultRosterIfEmpty() }

    val supEmail = "sup_${UUID.randomUUID()}@depot.test"
    val supUid = signInTestUser(supEmail)
    val storeEmail = "store_${UUID.randomUUID()}@depot.test"
    val storeUid = signInTestUser(storeEmail)

    // Admin assigns Supervisor & Storemate roles
    signInVerifiedAdmin()
    adminRepo.updateUserRole(supUid, UserRole.SUPERVISOR, "staff_1", adminUid)
    adminRepo.updateUserRole(storeUid, UserRole.STOREMATE, "", adminUid)

    // Storemate submits a draft for 2026-11-22
    signInTestUser(storeEmail)
    val storeRepo = FirestoreRosterRepository(firestore)
    val slotId = "2026-11-22_SHIFT_06_14_STATION_SC_0"
    val draftId = withTimeout(DEFAULT_TIMEOUT_MS) {
      storeRepo.submitDutyDraft(
        DutyApprovalRequest(
          depotId = DepotRoleManager.SHARED_DEPOT_ID,
          dutyDate = "2026-11-22",
          status = DutyDraftStatus.PENDING.name,
          submittedByUid = storeUid,
          submittedByName = "Depot Storemate",
          proposedSlots = listOf(
            DraftSlotEntry(
              slotId = slotId,
              date = "2026-11-22",
              shiftType = ShiftType.SHIFT_06_14.name,
              dutyArea = DutyArea.STATION_SC.name,
              roleType = SlotRoleType.GENERAL_DUTY.name,
              slotIndex = 0L,
              staffId = "staff_7",
              staffName = "P. RAM KUMAR"
            )
          )
        )
      )
    }
    assertTrue(draftId.isNotBlank())

    // Supervisor approves draft -> publishes assignment & locks 2026-11-22
    signInTestUser(supEmail)
    val supRepo = FirestoreRosterRepository(firestore)
    val pendingDraft = withTimeout(FLOW_TIMEOUT_MS) {
      supRepo.observeDutyDrafts(DepotRoleManager.SHARED_DEPOT_ID).first { list -> list.any { it.id == draftId } }
    }.first { it.id == draftId }

    withTimeout(DEFAULT_TIMEOUT_MS) {
      supRepo.approveDutyDraft(
        draft = pendingDraft,
        reviewerUid = supUid,
        reviewerName = "VENKATA KRISHNA",
        cycleAssignmentIdsToClear = emptyList(),
        existingAssignmentIds = emptySet()
      )
    }

    val published = withTimeout(FLOW_TIMEOUT_MS) {
      supRepo.observeAssignments(DepotRoleManager.SHARED_DEPOT_ID).first { list -> list.any { it.id == slotId } }
    }
    assertEquals("P. RAM KUMAR", published.first { it.id == slotId }.staffName)
    assertTrue(published.first { it.id == slotId }.approvedLocked)

    // Storemate cannot submit another draft for locked date 2026-11-22
    signInTestUser(storeEmail)
    try {
      withTimeout(DEFAULT_TIMEOUT_MS) {
        storeRepo.submitDutyDraft(
          DutyApprovalRequest(
            depotId = DepotRoleManager.SHARED_DEPOT_ID,
            dutyDate = "2026-11-22",
            status = DutyDraftStatus.PENDING.name,
            submittedByUid = storeUid,
            submittedByName = "Depot Storemate",
            proposedSlots = pendingDraft.proposedSlots
          )
        )
      }
      fail("Expected PERMISSION_DENIED when Storemate writes draft on locked date")
    } catch (e: Exception) {
      val ffe = e.unwrapFirestoreException()
      assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, ffe?.code)
    }

    // Supervisor can still directly edit already approved assignment & logs override
    signInTestUser(supEmail)
    withTimeout(DEFAULT_TIMEOUT_MS) {
      supRepo.saveSlotAssignment(
        ShiftSlotAssignment(
          id = slotId,
          userId = DepotRoleManager.SHARED_DEPOT_ID,
          date = "2026-11-22",
          shiftType = ShiftType.SHIFT_06_14.name,
          dutyArea = DutyArea.STATION_SC.name,
          roleType = SlotRoleType.GENERAL_DUTY.name,
          slotIndex = 0L,
          staffId = "staff_8",
          staffName = "K. DANIEL RAJ",
          approvedLocked = true,
          approvedByName = "VENKATA KRISHNA"
        ),
        existingDocPresent = true
      )
    }
    val updated = withTimeout(FLOW_TIMEOUT_MS) {
      supRepo.observeAssignments(DepotRoleManager.SHARED_DEPOT_ID)
        .first { list -> list.any { it.id == slotId && it.staffId == "staff_8" } }
    }
    assertEquals("K. DANIEL RAJ", updated.first { it.id == slotId }.staffName)
  }

  @Test
  fun leaveForwardingAndRestDayUpdate_enforcesSseOnlyForTechnician(): Unit = runBlocking {
    val adminUid = signInVerifiedAdmin()
    val adminRepo = FirestoreRosterRepository(firestore)
    adminRepo.ensureUserProfile(adminUid, DepotRoleManager.ADMIN_EMAIL, DepotRoleManager.ADMIN_DISPLAY_NAME)
    withTimeout(DEFAULT_TIMEOUT_MS) { adminRepo.seedDefaultRosterIfEmpty() }

    val techUid = signInTestUser("tech_leave_${UUID.randomUUID()}@depot.test")
    val techRepo = FirestoreRosterRepository(firestore)

    // Technician forwarding leave to non-SSE JE ("staff_3") fails rules
    try {
      withTimeout(DEFAULT_TIMEOUT_MS) {
        techRepo.addLeaveRecord(
          LeaveRecord(
            userId = DepotRoleManager.SHARED_DEPOT_ID,
            staffId = "staff_7",
            staffName = "P. RAM KUMAR",
            leaveType = LeaveType.CL.name,
            startDate = "2026-11-24",
            endDate = "2026-11-25",
            status = LeaveStatus.PENDING.name,
            appliedByUid = techUid,
            appliedByName = "P. RAM KUMAR",
            forwardedToStaffId = "staff_3",
            forwardedToName = "Y. SAI KRISHNA",
            notes = "Invalid JE forward"
          )
        )
      }
      fail("Expected PERMISSION_DENIED when Technician forwards leave to JE instead of SSE")
    } catch (e: Exception) {
      val ffe = e.unwrapFirestoreException()
      assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, ffe?.code)
    }

    // Technician forwarding PENDING leave to SSE ("staff_1") succeeds
    val leaveId = withTimeout(DEFAULT_TIMEOUT_MS) {
      techRepo.addLeaveRecord(
        LeaveRecord(
          userId = DepotRoleManager.SHARED_DEPOT_ID,
          staffId = "staff_7",
          staffName = "P. RAM KUMAR",
          leaveType = LeaveType.CL.name,
          startDate = "2026-11-24",
          endDate = "2026-11-25",
          status = LeaveStatus.PENDING.name,
          appliedByUid = techUid,
          appliedByName = "P. RAM KUMAR",
          forwardedToStaffId = "staff_1",
          forwardedToName = "VENKATA KRISHNA",
          notes = "Valid SSE forward"
        )
      )
    }
    assertTrue(leaveId.isNotBlank())

    // Admin updates weekly rest day & logs override
    signInVerifiedAdmin()
    withTimeout(DEFAULT_TIMEOUT_MS) { adminRepo.updateStaffRestDay("staff_7", WeeklyRestDay.SUNDAY) }
    val overrideId = withTimeout(DEFAULT_TIMEOUT_MS) {
      adminRepo.logEmergencyOverride(
        EmergencyOverrideLog(
          userId = DepotRoleManager.SHARED_DEPOT_ID,
          date = "2026-11-24",
          shiftType = ShiftType.SHIFT_22_06.name,
          dutyArea = DutyArea.STATION_MED.name,
          staffId = "staff_7",
          staffName = "P. RAM KUMAR",
          conflictSummary = "Rest day override",
          reason = "Train breakdown emergency"
        )
      )
    }
    assertTrue(overrideId.isNotBlank())
    assertFalse(adminUid.isBlank())
  }

  @Test
  fun observeStaff_unauthenticated_failsWithPermissionDenied(): Unit = runBlocking {
    auth.signOut()
    val repo = FirestoreRosterRepository(firestore)
    try {
      withTimeout(FLOW_TIMEOUT_MS) { repo.observeStaff(DepotRoleManager.SHARED_DEPOT_ID).first() }
      fail("Expected PERMISSION_DENIED when unauthenticated")
    } catch (e: Exception) {
      val ffe = e.unwrapFirestoreException()
      assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, ffe?.code)
    }
  }

  private companion object {
    const val DEFAULT_TIMEOUT_MS = 5000L
    const val FLOW_TIMEOUT_MS = 3000L
  }
}
