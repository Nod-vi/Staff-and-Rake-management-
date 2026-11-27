package com.example.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.model.Competency
import com.example.model.Designation
import com.example.model.DutyArea
import com.example.model.EmergencyOverrideLog
import com.example.model.LeaveRecord
import com.example.model.LeaveStatus
import com.example.model.LeaveType
import com.example.model.ShiftSlotAssignment
import com.example.model.ShiftType
import com.example.model.SlotRoleType
import com.example.model.StaffMember
import com.example.model.WeeklyRestDay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class LocalRosterRepositoryTest {

  private lateinit var context: Context

  @Before
  fun cleanLocalFile() {
    context = ApplicationProvider.getApplicationContext()
    LocalRosterRepository.resetSingletonForTests()
    File(context.filesDir, "ppio_local_roster.json").delete()
    File(context.filesDir, "ppio_local_roster.json.tmp").delete()
    File(context.filesDir, "ppio_local_roster.json.bak").delete()
  }

  @Test
  fun localModePersistence_seedsAndReloadsAcrossInstances(): Unit = runBlocking {
    val repo1 = LocalRosterRepository(context)
    assertTrue(repo1.seedDefaultRosterIfEmpty())
    assertFalse(repo1.seedDefaultRosterIfEmpty())

    val staff = repo1.observeStaff(LocalRosterRepository.LOCAL_USER_ID).first()
    assertEquals(35, staff.size)
    assertTrue(staff.any { it.name == "VIVEK CHOUDHARY" && it.isPointsman })

    repo1.updateStaffRestDay("staff_7", WeeklyRestDay.SUNDAY)
    repo1.saveSlotAssignment(
      ShiftSlotAssignment(
        id = "2026-11-16_SHIFT_06_14_STATION_SC_0",
        userId = LocalRosterRepository.LOCAL_USER_ID,
        date = "2026-11-16",
        shiftType = ShiftType.SHIFT_06_14.name,
        dutyArea = DutyArea.STATION_SC.name,
        roleType = SlotRoleType.GENERAL_DUTY.name,
        slotIndex = 0L,
        staffId = "staff_7",
        staffName = "P. RAM KUMAR"
      ),
      existingDocPresent = false
    )
    repo1.saveSlotAssignment(
      ShiftSlotAssignment(
        id = "2026-11-16_SHIFT_08_17_SHED_0",
        userId = LocalRosterRepository.LOCAL_USER_ID,
        date = "2026-11-16",
        shiftType = ShiftType.SHIFT_08_17.name,
        dutyArea = DutyArea.SHED.name,
        roleType = SlotRoleType.GENERAL_DUTY.name,
        slotIndex = 0L,
        staffId = "staff_8",
        staffName = "M. ANJANEYULU"
      ),
      existingDocPresent = false
    )
    val leaveId = repo1.addLeaveRecord(
      LeaveRecord(
        userId = LocalRosterRepository.LOCAL_USER_ID,
        staffId = "staff_1",
        staffName = "V. SREEDHAR",
        leaveType = LeaveType.CL.name,
        startDate = "2026-11-18",
        endDate = "2026-11-20",
        notes = "Personal"
      )
    )
    val ovrId = repo1.logEmergencyOverride(
      EmergencyOverrideLog(
        userId = LocalRosterRepository.LOCAL_USER_ID,
        date = "2026-11-16",
        shiftType = ShiftType.SHIFT_22_06.name,
        dutyArea = DutyArea.STATION_MED.name,
        staffId = "staff_7",
        staffName = "P. RAM KUMAR",
        conflictSummary = "Post-night rest conflict",
        reason = "Urgent train set movement"
      )
    )
    assertTrue(leaveId.isNotBlank())
    assertTrue(ovrId.isNotBlank())

    // Reload from disk with a fresh instance
    val repo2 = LocalRosterRepository(context)
    val reloadedStaff = repo2.observeStaff(LocalRosterRepository.LOCAL_USER_ID).first()
    assertEquals(35, reloadedStaff.size)
    assertEquals(WeeklyRestDay.SUNDAY.name, reloadedStaff.first { it.id == "staff_7" }.weeklyRestDay)
    val reloadedAssignments = repo2.observeAssignments(LocalRosterRepository.LOCAL_USER_ID).first()
    assertEquals(2, reloadedAssignments.size)
    assertTrue(reloadedAssignments.any { it.shiftType == ShiftType.SHIFT_08_17.name })
    assertEquals(1, repo2.observeLeaves(LocalRosterRepository.LOCAL_USER_ID).first().size)
    assertEquals(1, repo2.observeOverrides(LocalRosterRepository.LOCAL_USER_ID).first().size)

    // Test staff add, edit, delete & leave update persistence
    val addedId = repo2.addStaffMember(
      StaffMember(
        serialNo = 39L,
        name = "NEW DEPOT TECH",
        designation = Designation.TECH_III.name,
        competency = Competency.NONE.name,
        stationDuty = false,
        shedDuty = true,
        weeklyRestDay = WeeklyRestDay.FRIDAY.name
      )
    )
    repo2.updateStaffMember(
      repo2.observeStaff(LocalRosterRepository.LOCAL_USER_ID).first().first { it.id == addedId }
        .copy(name = "NEW DEPOT TECH UPDATED", stationDuty = true)
    )
    repo2.updateLeaveRecord(
      leaveId = leaveId,
      leaveType = LeaveType.LAP,
      startDate = "2026-11-19",
      endDate = "2026-11-22",
      status = LeaveStatus.PENDING,
      notes = "Period extended by supervisor"
    )

    val repo3 = LocalRosterRepository(context)
    val updatedStaff = repo3.observeStaff(LocalRosterRepository.LOCAL_USER_ID).first().first { it.id == addedId }
    assertEquals("NEW DEPOT TECH UPDATED", updatedStaff.name)
    assertTrue(updatedStaff.stationDuty)
    val updatedLeave = repo3.observeLeaves(LocalRosterRepository.LOCAL_USER_ID).first().first { it.id == leaveId }
    assertEquals(LeaveType.LAP.name, updatedLeave.leaveType)
    assertEquals("2026-11-19", updatedLeave.startDate)
    assertEquals("2026-11-22", updatedLeave.endDate)
    assertEquals(LeaveStatus.PENDING.name, updatedLeave.status)

    repo3.deleteStaffMember(addedId)
    assertFalse(repo3.observeStaff(LocalRosterRepository.LOCAL_USER_ID).first().any { it.id == addedId })

    // Verify duty draft, approval lock, and user role assignment persistence
    val draftId = repo3.submitDutyDraft(
      com.example.model.DutyApprovalRequest(
        depotId = LocalRosterRepository.LOCAL_USER_ID,
        dutyDate = "2026-11-22",
        status = com.example.model.DutyDraftStatus.PENDING.name,
        submittedByUid = "storemate_1",
        submittedByName = "Depot Storemate",
        proposedSlots = listOf(
          com.example.model.DraftSlotEntry(
            slotId = "2026-11-22_SHIFT_06_14_SHED_0",
            date = "2026-11-22",
            shiftType = ShiftType.SHIFT_06_14.name,
            dutyArea = DutyArea.SHED.name,
            roleType = SlotRoleType.GENERAL_DUTY.name,
            slotIndex = 0L,
            staffId = "staff_7",
            staffName = "P. RAM KUMAR"
          )
        )
      )
    )
    val savedDraft = repo3.observeDutyDrafts(LocalRosterRepository.LOCAL_USER_ID).first().first { it.id == draftId }
    repo3.approveDutyDraft(
      draft = savedDraft,
      reviewerUid = "sup_1",
      reviewerName = "VENKATA KRISHNA",
      cycleAssignmentIdsToClear = emptyList(),
      existingAssignmentIds = setOf("2026-11-16_SHIFT_06_14_STATION_SC_0")
    )
    repo3.updateUserRole(
      targetUid = "tech_1",
      role = com.example.model.UserRole.STOREMATE,
      staffId = "staff_7",
      updatedByUid = "admin_vinod"
    )

    val repo4 = LocalRosterRepository(context)
    assertTrue(repo4.observeDutyLocks(LocalRosterRepository.LOCAL_USER_ID).first().any { it.dutyDate == "2026-11-22" })
    assertEquals(
      com.example.model.DutyDraftStatus.APPROVED,
      repo4.observeDutyDrafts(LocalRosterRepository.LOCAL_USER_ID).first().first { it.id == draftId }.statusEnum
    )
    assertEquals(
      com.example.model.UserRole.STOREMATE,
      repo4.observeUserProfile("tech_1").first()?.roleEnum
    )
  }

  @Test
  fun crossModeSyncAndMirror_preservesShiftsAndLeavesAcrossLogoutAndModeSwitch(): Unit = runBlocking {
    val repo1 = LocalRosterRepository(context)
    repo1.seedDefaultRosterIfEmpty()

    // 1. User logs in as admin/admin, saves a shift and a leave
    val shift = ShiftSlotAssignment(
      id = "2026-11-25_SHIFT_08_17_SHED_0",
      userId = LocalRosterRepository.LOCAL_USER_ID,
      date = "2026-11-25",
      shiftType = ShiftType.SHIFT_08_17.name,
      dutyArea = DutyArea.SHED.name,
      roleType = SlotRoleType.GENERAL_DUTY.name,
      slotIndex = 0L,
      staffId = "staff_7",
      staffName = "P. RAM KUMAR"
    )
    repo1.saveSlotAssignment(shift, existingDocPresent = false)
    val leaveId = repo1.addLeaveRecord(
      LeaveRecord(
        userId = LocalRosterRepository.LOCAL_USER_ID,
        staffId = "staff_2",
        staffName = "PRAKASH",
        leaveType = LeaveType.LAP.name,
        startDate = "2026-11-26",
        endDate = "2026-11-28",
        notes = "Annual leave"
      )
    )

    // 2. Simulate app restart / logout -> fresh repository still has pending sync IDs intact
    val repoAfterRestart = LocalRosterRepository(context)
    val pending = repoAfterRestart.snapshotPendingLocalChanges()
    assertTrue(pending.hasChanges)
    assertEquals(1, pending.dirtyAssignments.size)
    assertEquals(1, pending.dirtyLeaves.size)

    // 3. Before pending changes are uploaded, an empty cloud snapshot must NOT wipe local edits
    repoAfterRestart.mirrorCloudSnapshot(assignments = emptyList(), leaves = emptyList())
    assertEquals(1, repoAfterRestart.observeAssignments(LocalRosterRepository.LOCAL_USER_ID).first().size)
    assertEquals(1, repoAfterRestart.observeLeaves(LocalRosterRepository.LOCAL_USER_ID).first().size)

    // 4. Mark synced and mirror cloud snapshot with an additional cloud shift + leave
    repoAfterRestart.markLocalChangesSynced(
      syncedAssignmentIds = setOf(shift.id),
      syncedLeaveIds = setOf(leaveId)
    )
    assertFalse(repoAfterRestart.snapshotPendingLocalChanges().hasChanges)

    val cloudShift = ShiftSlotAssignment(
      id = "2026-11-25_SHIFT_22_06_STATION_UR_0",
      userId = LocalRosterRepository.LOCAL_USER_ID,
      date = "2026-11-25",
      shiftType = ShiftType.SHIFT_22_06.name,
      dutyArea = DutyArea.STATION_UR.name,
      roleType = SlotRoleType.GENERAL_DUTY.name,
      slotIndex = 0L,
      staffId = "staff_9",
      staffName = "N. VENKATESH"
    )
    repoAfterRestart.mirrorCloudSnapshot(
      assignments = listOf(shift, cloudShift),
      leaves = repoAfterRestart.observeLeaves(LocalRosterRepository.LOCAL_USER_ID).first()
    )

    // 5. Simulate logout from Google and re-login with admin/admin -> both shifts, leave, and admin credentials survive
    val repoAfterGoogleLogout = LocalRosterRepository(context)
    assertEquals(2, repoAfterGoogleLogout.observeAssignments(LocalRosterRepository.LOCAL_USER_ID).first().size)
    assertEquals(1, repoAfterGoogleLogout.observeLeaves(LocalRosterRepository.LOCAL_USER_ID).first().size)
    assertTrue(repoAfterGoogleLogout.authenticateLocalUser("admin", "admin") != null)
  }
}
