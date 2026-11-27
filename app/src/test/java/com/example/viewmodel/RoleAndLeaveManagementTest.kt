package com.example.viewmodel

import android.content.Context
import android.os.Looper
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import com.example.auth.DepotRoleManager
import com.example.data.LocalRosterRepository
import com.example.domain.ShiftSchedulerEngine
import com.example.model.Competency
import com.example.model.Designation
import com.example.model.DutyArea
import com.example.model.DutyDraftStatus
import com.example.model.LeaveStatus
import com.example.model.LeaveType
import com.example.model.ShiftType
import com.example.model.UserRole
import com.example.model.WeeklyRestDay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class RoleAndLeaveManagementTest {

  private lateinit var context: Context
  private val storesToClear = mutableListOf<ViewModelStore>()

  @Before
  fun cleanLocalFile() {
    Dispatchers.setMain(UnconfinedTestDispatcher())
    context = ApplicationProvider.getApplicationContext()
    LocalRosterRepository.resetSingletonForTests()
    File(context.filesDir, "ppio_local_roster.json").delete()
    File(context.filesDir, "ppio_local_roster.json.tmp").delete()
    File(context.filesDir, "ppio_local_roster.json.bak").delete()
  }

  @After
  fun tearDown() {
    storesToClear.forEach { it.clear() }
    storesToClear.clear()
    Dispatchers.resetMain()
  }

  private fun createManagedViewModel(
    store: ViewModelStore = ViewModelStore().also { storesToClear.add(it) },
    key: String = "test_vm_${storesToClear.size}",
    builder: () -> RosterViewModel
  ): RosterViewModel {
    val factory = object : ViewModelProvider.Factory {
      @Suppress("UNCHECKED_CAST")
      override fun <T : ViewModel> create(modelClass: Class<T>): T = builder() as T
    }
    return ViewModelProvider(store, factory)[key, RosterViewModel::class.java]
  }

  private suspend fun awaitMainAndCondition(
    timeoutMs: Long = 3500L,
    predicate: suspend () -> Boolean
  ) {
    val deadline = System.currentTimeMillis() + timeoutMs
    while (System.currentTimeMillis() < deadline) {
      shadowOf(Looper.getMainLooper()).idle()
      if (predicate()) return
      delay(20)
    }
    shadowOf(Looper.getMainLooper()).idle()
  }

  @Test
  fun roleDirectory_assignsAllFourRolesAndVinodAsAdmin() {
    val adminSession = DepotRoleManager.resolveLocalSession(DepotRoleManager.ADMIN_ACCOUNT_ID)
    assertEquals(UserRole.ADMIN, adminSession.role)
    assertEquals("Vinod Chinthawar", adminSession.displayName)
    assertTrue(adminSession.role.canManageStaff)
    assertTrue(adminSession.role.canManageUserRoles)

    val supervisorIds = listOf("sup_1", "sup_2", "sup_3", "sup_5", "sup_6")
    supervisorIds.forEach { supId ->
      val supSession = DepotRoleManager.resolveLocalSession(supId)
      assertEquals(UserRole.SUPERVISOR, supSession.role)
      assertFalse(supSession.role.canManageStaff)
      assertFalse(supSession.role.canManageUserRoles)
      assertTrue(supSession.role.canApproveDuties)
      assertTrue(supSession.role.canDirectEditDuties)
      assertTrue(supSession.role.canManageLeaves)
    }

    val storemateSession = DepotRoleManager.resolveLocalSession("storemate_1")
    assertEquals(UserRole.STOREMATE, storemateSession.role)
    assertTrue(storemateSession.role.canDraftDuties)
    assertFalse(storemateSession.role.canDirectEditDuties)
    assertFalse(storemateSession.role.canApproveDuties)
    assertTrue(storemateSession.role.canApplyLeaveOnly)

    val techSession = DepotRoleManager.resolveLocalSession("tech_1")
    assertEquals(UserRole.TECHNICIAN, techSession.role)
    assertFalse(techSession.role.canDraftDuties)
    assertFalse(techSession.role.canDirectEditDuties)
    assertFalse(techSession.role.canApproveDuties)
    assertFalse(techSession.role.canManageLeaves)
    assertTrue(techSession.role.canApplyLeaveOnly)
  }

  @Test
  fun technician_hasReadOnlyShiftAccess_andCanOnlyApplyPendingLeaveToSse(): Unit = runBlocking {
    val repo = LocalRosterRepository(context)
    repo.seedDefaultRosterIfEmpty()

    val vm = createManagedViewModel {
      RosterViewModel(
        repository = repo,
        depotId = DepotRoleManager.SHARED_DEPOT_ID,
        initialActorUid = "tech_1",
        initialActorName = "P. RAM KUMAR",
        actorEmail = "",
        isCloudMode = false,
        initialRole = UserRole.TECHNICIAN
      )
    }
    awaitMainAndCondition { vm.staffState.value is UiState.Success }

    val staff = repo.observeStaff(DepotRoleManager.SHARED_DEPOT_ID).first()
    val dutyDate = LocalDate.of(2026, 11, 20)
    val slot = ShiftSchedulerEngine.buildDutyDaySlots(dutyDate)
      .first { it.shiftType == ShiftType.SHIFT_06_14 && it.dutyArea == DutyArea.STATION_SC }
    val ramKumar = staff.first { it.name == "P. RAM KUMAR" }
    val jeSupervisor = staff.first { it.designationEnum == Designation.JE }
    val sseSupervisor = staff.first { it.designationEnum == Designation.SSE }

    // Technician cannot assign or auto-assign duties
    vm.assignStaffToSlot(slot, ramKumar, isEmergencyOverride = false, overrideReason = "", conflictSummary = "")
    awaitMainAndCondition { vm.actionStatus.value?.contains("read-only", ignoreCase = true) == true }
    assertTrue(repo.observeAssignments(DepotRoleManager.SHARED_DEPOT_ID).first().isEmpty())

    // Technician cannot apply leave for another staff member
    val otherStaff = staff.first { !it.isSupervisory && it.id != ramKumar.id }
    vm.addLeave(
      staff = otherStaff,
      leaveType = LeaveType.CL,
      startDate = dutyDate,
      endDate = dutyDate.plusDays(1),
      notes = "Someone else",
      autoClearConflictingShifts = true,
      leaveStatus = LeaveStatus.PENDING,
      forwardedToSupervisor = sseSupervisor
    )
    awaitMainAndCondition { vm.actionStatus.value?.contains("own linked staff", ignoreCase = true) == true }
    assertTrue(repo.observeLeaves(DepotRoleManager.SHARED_DEPOT_ID).first().isEmpty())

    // Unlinked non-approver (storemate_1 has no linked staffId) cannot apply leave
    vm.updateSessionActor("storemate_1", "Depot Storemate", UserRole.STOREMATE)
    awaitMainAndCondition { vm.currentUserProfile.value?.uid == "storemate_1" }
    vm.addLeave(
      staff = ramKumar,
      leaveType = LeaveType.CL,
      startDate = dutyDate,
      endDate = dutyDate.plusDays(1),
      notes = "Unlinked attempt",
      autoClearConflictingShifts = true,
      leaveStatus = LeaveStatus.PENDING,
      forwardedToSupervisor = sseSupervisor
    )
    awaitMainAndCondition { vm.actionStatus.value?.contains("Only linked staff", ignoreCase = true) == true }
    assertTrue(repo.observeLeaves(DepotRoleManager.SHARED_DEPOT_ID).first().isEmpty())

    // Switch back to linked Technician (tech_1 -> staff_7 P. RAM KUMAR)
    vm.updateSessionActor("tech_1", "P. RAM KUMAR", UserRole.TECHNICIAN)
    awaitMainAndCondition { vm.currentUserProfile.value?.uid == "tech_1" }

    // Technician cannot forward leave to non-SSE (e.g. JE)
    vm.addLeave(
      staff = ramKumar,
      leaveType = LeaveType.CL,
      startDate = dutyDate,
      endDate = dutyDate.plusDays(1),
      notes = "Home visit",
      autoClearConflictingShifts = true,
      leaveStatus = LeaveStatus.APPROVED,
      forwardedToSupervisor = jeSupervisor
    )
    awaitMainAndCondition { vm.actionStatus.value?.contains("SSE Supervisor", ignoreCase = true) == true }
    assertTrue(repo.observeLeaves(DepotRoleManager.SHARED_DEPOT_ID).first().isEmpty())

    // Technician forwards leave to SSE -> saved strictly as PENDING even if caller passed APPROVED
    vm.addLeave(
      staff = ramKumar,
      leaveType = LeaveType.CL,
      startDate = dutyDate,
      endDate = dutyDate.plusDays(1),
      notes = "Family function",
      autoClearConflictingShifts = true,
      leaveStatus = LeaveStatus.APPROVED,
      forwardedToSupervisor = sseSupervisor
    )
    awaitMainAndCondition { repo.observeLeaves(DepotRoleManager.SHARED_DEPOT_ID).first().isNotEmpty() }
    val saved = repo.observeLeaves(DepotRoleManager.SHARED_DEPOT_ID).first().first()
    assertEquals(LeaveStatus.PENDING.name, saved.status)
    assertEquals(sseSupervisor.id, saved.forwardedToStaffId)
    assertEquals(sseSupervisor.name, saved.forwardedToName)
    assertEquals("tech_1", saved.appliedByUid)

    // Technician cannot approve/update leave
    vm.approvePendingLeave(saved)
    awaitMainAndCondition { vm.actionStatus.value?.contains("Supervisor or Admin", ignoreCase = true) == true }
    assertEquals(
      LeaveStatus.PENDING.name,
      repo.observeLeaves(DepotRoleManager.SHARED_DEPOT_ID).first().first().status
    )

    // Switch to Supervisor (SSE) and approve
    vm.updateSessionActor("sup_1", sseSupervisor.name, UserRole.SUPERVISOR)
    vm.approvePendingLeave(saved)
    awaitMainAndCondition {
      repo.observeLeaves(DepotRoleManager.SHARED_DEPOT_ID).first().first().status == LeaveStatus.APPROVED.name
    }
    val approved = repo.observeLeaves(DepotRoleManager.SHARED_DEPOT_ID).first().first()
    assertEquals(LeaveStatus.APPROVED.name, approved.status)
    assertEquals(sseSupervisor.name, approved.reviewedByName)
  }

  @Test
  fun storemate_draftsPublishOnlyOnSupervisorApproval_andLockAgainstFurtherStoremateEdits(): Unit = runBlocking {
    val repo = LocalRosterRepository(context)
    repo.seedDefaultRosterIfEmpty()

    val vm = createManagedViewModel {
      RosterViewModel(
        repository = repo,
        depotId = DepotRoleManager.SHARED_DEPOT_ID,
        initialActorUid = "storemate_1",
        initialActorName = "Depot Storemate",
        actorEmail = "",
        isCloudMode = false,
        initialRole = UserRole.STOREMATE
      )
    }
    awaitMainAndCondition { vm.staffState.value is UiState.Success }

    val staff = repo.observeStaff(DepotRoleManager.SHARED_DEPOT_ID).first()
    val dutyDate = LocalDate.of(2026, 11, 21)
    val slot = ShiftSchedulerEngine.buildDutyDaySlots(dutyDate)
      .first { it.shiftType == ShiftType.SHIFT_06_14 && it.dutyArea == DutyArea.STATION_SC }
    val candidateA = staff.first { !it.isSupervisory && it.stationDuty }
    val candidateB = staff.first { !it.isSupervisory && it.stationDuty && it.id != candidateA.id }

    // 1. Storemate stages a slot in draft -> NOT published to DB yet
    vm.assignStaffToSlot(slot, candidateA, isEmergencyOverride = false, overrideReason = "", conflictSummary = "")
    awaitMainAndCondition { vm.stagedDraftsByDate.value[dutyDate.toString()]?.isNotEmpty() == true }
    assertTrue(repo.observeAssignments(DepotRoleManager.SHARED_DEPOT_ID).first().isEmpty())

    // 2. Storemate submits draft -> creates PENDING approval request, assignments still empty
    vm.submitStagedDraftForApproval(dutyDate)
    awaitMainAndCondition { repo.observeDutyDrafts(DepotRoleManager.SHARED_DEPOT_ID).first().isNotEmpty() }
    assertTrue(repo.observeAssignments(DepotRoleManager.SHARED_DEPOT_ID).first().isEmpty())
    val pendingDraft = repo.observeDutyDrafts(DepotRoleManager.SHARED_DEPOT_ID).first().first()
    assertEquals(DutyDraftStatus.PENDING, pendingDraft.statusEnum)

    // 3. Supervisor approves draft -> updates shared DB assignments & locks the date
    vm.updateSessionActor("sup_1", "VENKATA KRISHNA", UserRole.SUPERVISOR)
    vm.approveDutyDraft(pendingDraft)
    awaitMainAndCondition {
      repo.observeAssignments(DepotRoleManager.SHARED_DEPOT_ID).first().any { it.id == slot.documentId } &&
        repo.observeDutyLocks(DepotRoleManager.SHARED_DEPOT_ID).first().any { it.dutyDate == dutyDate.toString() }
    }
    val published = repo.observeAssignments(DepotRoleManager.SHARED_DEPOT_ID).first().first { it.id == slot.documentId }
    assertEquals(candidateA.id, published.staffId)
    assertTrue(published.approvedLocked)
    assertEquals("VENKATA KRISHNA", published.approvedByName)
    assertEquals(
      DutyDraftStatus.APPROVED,
      repo.observeDutyDrafts(DepotRoleManager.SHARED_DEPOT_ID).first().first { it.id == pendingDraft.id }.statusEnum
    )

    // 4. Storemate can NO LONGER alter the approved/locked date
    vm.updateSessionActor("storemate_1", "Depot Storemate", UserRole.STOREMATE)
    awaitMainAndCondition { vm.dutyLocksState.value.any { it.dutyDate == dutyDate.toString() } }
    vm.assignStaffToSlot(slot, candidateB, isEmergencyOverride = false, overrideReason = "", conflictSummary = "")
    awaitMainAndCondition { vm.actionStatus.value?.contains("locked", ignoreCase = true) == true }
    assertEquals(
      candidateA.id,
      repo.observeAssignments(DepotRoleManager.SHARED_DEPOT_ID).first().first { it.id == slot.documentId }.staffId
    )

    // 5. Supervisor CAN directly modify already-approved/locked duties in DB
    vm.updateSessionActor("sup_1", "VENKATA KRISHNA", UserRole.SUPERVISOR)
    vm.assignStaffToSlot(slot, candidateB, isEmergencyOverride = false, overrideReason = "", conflictSummary = "")
    awaitMainAndCondition {
      repo.observeAssignments(DepotRoleManager.SHARED_DEPOT_ID).first()
        .first { it.id == slot.documentId }.staffId == candidateB.id
    }
    assertEquals(
      candidateB.id,
      repo.observeAssignments(DepotRoleManager.SHARED_DEPOT_ID).first().first { it.id == slot.documentId }.staffId
    )
  }

  @Test
  fun admin_canAssignUserRoles_andManageStaffRoster(): Unit = runBlocking {
    val repo = LocalRosterRepository(context)
    repo.seedDefaultRosterIfEmpty()

    val vm = createManagedViewModel {
      RosterViewModel(
        repository = repo,
        depotId = DepotRoleManager.SHARED_DEPOT_ID,
        initialActorUid = DepotRoleManager.ADMIN_ACCOUNT_ID,
        initialActorName = DepotRoleManager.ADMIN_DISPLAY_NAME,
        actorEmail = DepotRoleManager.ADMIN_EMAIL,
        isCloudMode = false,
        initialRole = UserRole.ADMIN
      )
    }
    awaitMainAndCondition {
      vm.staffState.value is UiState.Success && vm.allUserProfilesState.value.isNotEmpty()
    }

    // Admin assigns role TECHNICIAN -> STOREMATE -> SUPERVISOR
    val techProfile = repo.observeAllUserProfiles().first().first { it.uid == "tech_1" }
    assertEquals(UserRole.TECHNICIAN, techProfile.roleEnum)

    vm.assignUserRole(techProfile, UserRole.STOREMATE)
    awaitMainAndCondition {
      repo.observeAllUserProfiles().first().first { it.uid == "tech_1" }.roleEnum == UserRole.STOREMATE
    }
    assertEquals(
      UserRole.STOREMATE,
      repo.observeAllUserProfiles().first().first { it.uid == "tech_1" }.roleEnum
    )

    // Admin cannot remove own Admin role
    val adminProfile = repo.observeAllUserProfiles().first().first { it.uid == DepotRoleManager.ADMIN_ACCOUNT_ID }
    vm.assignUserRole(adminProfile, UserRole.TECHNICIAN)
    awaitMainAndCondition { vm.actionStatus.value?.contains("cannot remove Admin", ignoreCase = true) == true }
    assertEquals(
      UserRole.ADMIN,
      repo.observeAllUserProfiles().first().first { it.uid == DepotRoleManager.ADMIN_ACCOUNT_ID }.roleEnum
    )

    // Admin adds, updates, deletes staff
    vm.addStaffMember(
      serialNo = 40L,
      name = "S. ARJUN",
      designation = Designation.TECH_II,
      competency = Competency.NONE,
      stationDuty = true,
      shedDuty = true,
      weeklyRestDay = WeeklyRestDay.WEDNESDAY
    )
    awaitMainAndCondition { repo.observeStaff(DepotRoleManager.SHARED_DEPOT_ID).first().size == 36 }
    val arjun = repo.observeStaff(DepotRoleManager.SHARED_DEPOT_ID).first().first { it.name == "S. ARJUN" }

    vm.updateStaffMember(arjun.copy(name = "S. ARJUN RAO", competency = Competency.POINTSMAN.name))
    awaitMainAndCondition {
      repo.observeStaff(DepotRoleManager.SHARED_DEPOT_ID).first()
        .firstOrNull { it.id == arjun.id }?.name == "S. ARJUN RAO"
    }
    val afterEdit = repo.observeStaff(DepotRoleManager.SHARED_DEPOT_ID).first().first { it.id == arjun.id }
    assertEquals("S. ARJUN RAO", afterEdit.name)
    assertTrue(afterEdit.isPointsman)

    vm.deleteStaffMember(arjun.id, arjun.name)
    awaitMainAndCondition { repo.observeStaff(DepotRoleManager.SHARED_DEPOT_ID).first().size == 35 }
    assertEquals(35, repo.observeStaff(DepotRoleManager.SHARED_DEPOT_ID).first().size)

    // Verify chinthawarvinod@gmail.com (case-insensitive) is always provisioned and upgraded to ADMIN
    val freshVinod = repo.ensureUserProfile("google_vinod_uid", "ChinthawarVinod@Gmail.com", "Vinod")
    assertEquals(UserRole.ADMIN, freshVinod.roleEnum)
    assertTrue(DepotRoleManager.isBootstrapAdminEmail("ChinthawarVinod@Gmail.com", "Any Display Name"))
  }

  @Test
  fun defaultAdminCredentials_createEditDeleteUsers_andChangePassword(): Unit = runBlocking {
    val repo = LocalRosterRepository(context)
    repo.seedDefaultRosterIfEmpty()

    // Default admin / admin credential sign-in succeeds, wrong password fails
    val wrongLogin = repo.authenticateLocalUser("admin", "wrong")
    assertEquals(null, wrongLogin)
    val adminLogin = repo.authenticateLocalUser("admin", "admin")
    assertTrue(adminLogin != null)
    assertEquals(UserRole.ADMIN, adminLogin!!.roleEnum)
    assertEquals("admin", adminLogin.username)

    val vm = createManagedViewModel {
      RosterViewModel(
        repository = repo,
        depotId = DepotRoleManager.SHARED_DEPOT_ID,
        initialActorUid = adminLogin.uid,
        initialActorName = adminLogin.displayName,
        actorEmail = adminLogin.email,
        isCloudMode = false,
        initialRole = adminLogin.roleEnum
      )
    }
    awaitMainAndCondition { vm.allUserProfilesState.value.isNotEmpty() }

    // Create a Storemate user account
    vm.createUserAccount(
      username = "ravi_store",
      password = "pass123",
      displayName = "Ravi Kumar",
      email = "ravi@example.com",
      role = UserRole.STOREMATE,
      staffId = "staff_10"
    )
    awaitMainAndCondition {
      repo.observeAllUserProfiles().first().any { it.username == "ravi_store" }
    }
    val createdUser = repo.observeAllUserProfiles().first().first { it.username == "ravi_store" }
    assertEquals(UserRole.STOREMATE, createdUser.roleEnum)
    assertTrue(repo.authenticateLocalUser("ravi_store", "pass123") != null)

    // Edit user role to SUPERVISOR and reset password
    vm.updateUserAccount(
      profile = createdUser,
      username = "ravi_sse",
      displayName = "Ravi Kumar SSE",
      email = "ravi.sse@example.com",
      role = UserRole.SUPERVISOR,
      staffId = "staff_1",
      newPassword = "newpass99"
    )
    awaitMainAndCondition {
      repo.observeUserProfile(createdUser.uid).first()?.username == "ravi_sse"
    }
    assertEquals(null, repo.authenticateLocalUser("ravi_sse", "pass123"))
    val editedAuth = repo.authenticateLocalUser("ravi_sse", "newpass99")
    assertTrue(editedAuth != null)
    assertEquals(UserRole.SUPERVISOR, editedAuth!!.roleEnum)

    // User changes own password via RosterViewModel
    vm.updateSessionActor(editedAuth.uid, editedAuth.displayName, editedAuth.roleEnum)
    var pwdResult = false
    vm.changeCurrentUserPassword("wrong", "brandnew", "brandnew") { ok, _ -> pwdResult = ok }
    awaitMainAndCondition { !vm.isBusy.value }
    assertFalse(pwdResult)

    vm.changeCurrentUserPassword("newpass99", "brandnew", "brandnew") { ok, _ -> pwdResult = ok }
    awaitMainAndCondition {
      repo.authenticateLocalUser("ravi_sse", "brandnew") != null
    }
    assertTrue(pwdResult)
    assertEquals(null, repo.authenticateLocalUser("ravi_sse", "newpass99"))

    // Switch back to Admin and delete user (primary admin remains protected)
    vm.updateSessionActor(adminLogin.uid, adminLogin.displayName, UserRole.ADMIN)
    vm.deleteUserAccount(adminLogin)
    awaitMainAndCondition { vm.actionStatus.value?.contains("cannot be removed", ignoreCase = true) == true }
    assertTrue(repo.observeUserProfile(adminLogin.uid).first() != null)

    vm.deleteUserAccount(editedAuth)
    awaitMainAndCondition {
      repo.observeAllUserProfiles().first().none { it.uid == editedAuth.uid }
    }
    assertFalse(repo.observeAllUserProfiles().first().any { it.uid == editedAuth.uid })
  }

  @Test
  fun logoutAndRelogin_preservesAllShiftAssignmentsAndLeaves(): Unit = runBlocking {
    // Session 1: Login as Admin, assign shifts across all 4 shifts (including 08/17) and add a leave
    val repoSession1 = LocalRosterRepository(context)
    repoSession1.seedDefaultRosterIfEmpty()

    val store1 = ViewModelStore()
    val vm1 = createManagedViewModel(store = store1, key = "session_1") {
      RosterViewModel(
        repository = repoSession1,
        depotId = DepotRoleManager.SHARED_DEPOT_ID,
        initialActorUid = DepotRoleManager.ADMIN_ACCOUNT_ID,
        initialActorName = DepotRoleManager.ADMIN_DISPLAY_NAME,
        actorEmail = DepotRoleManager.ADMIN_EMAIL,
        isCloudMode = false,
        initialRole = UserRole.ADMIN
      )
    }
    awaitMainAndCondition { vm1.staffState.value is UiState.Success }

    val staff = (vm1.staffState.value as UiState.Success).data
    val dutyDate = LocalDate.of(2026, 11, 24)
    val daySlots = ShiftSchedulerEngine.buildDutyDaySlots(dutyDate)
    val nightSlot = daySlots.first { it.shiftType == ShiftType.SHIFT_22_06 }
    val morningSlot = daySlots.first { it.shiftType == ShiftType.SHIFT_06_14 }
    val afternoonSlot = daySlots.first { it.shiftType == ShiftType.SHIFT_14_22 }
    val generalSlot = daySlots.first { it.shiftType == ShiftType.SHIFT_08_17 }

    val dutyStaff = staff.filter { !it.isSupervisory }
    vm1.assignStaffToSlot(nightSlot, dutyStaff[0], false, "", "")
    vm1.assignStaffToSlot(morningSlot, dutyStaff[1], false, "", "")
    vm1.assignStaffToSlot(afternoonSlot, dutyStaff[2], false, "", "")
    vm1.assignStaffToSlot(generalSlot, dutyStaff[3], false, "", "")
    vm1.addLeave(
      staff = dutyStaff[4],
      leaveType = LeaveType.CL,
      startDate = dutyDate.plusDays(1),
      endDate = dutyDate.plusDays(3),
      notes = "Family visit",
      autoClearConflictingShifts = true,
      leaveStatus = LeaveStatus.APPROVED
    )
    awaitMainAndCondition {
      val a = (vm1.assignmentsState.value as? UiState.Success)?.data ?: emptyList()
      val l = (vm1.leavesState.value as? UiState.Success)?.data ?: emptyList()
      a.size == 4 && l.size == 1
    }

    // Simulate Logout: dispose Session 1 ViewModelStore cleanly
    store1.clear()

    // Session 2: Reload fresh LocalRosterRepository from disk and create fresh Session 2 ViewModel
    val repoSession2 = LocalRosterRepository(context)
    val store2 = ViewModelStore().also { storesToClear.add(it) }
    val vm2 = createManagedViewModel(store = store2, key = "session_2") {
      RosterViewModel(
        repository = repoSession2,
        depotId = DepotRoleManager.SHARED_DEPOT_ID,
        initialActorUid = DepotRoleManager.ADMIN_ACCOUNT_ID,
        initialActorName = DepotRoleManager.ADMIN_DISPLAY_NAME,
        actorEmail = DepotRoleManager.ADMIN_EMAIL,
        isCloudMode = false,
        initialRole = UserRole.ADMIN
      )
    }
    awaitMainAndCondition {
      vm2.assignmentsState.value is UiState.Success && vm2.leavesState.value is UiState.Success
    }

    val reloadedAssignments = (vm2.assignmentsState.value as UiState.Success).data
    val reloadedLeaves = (vm2.leavesState.value as UiState.Success).data

    assertEquals(4, reloadedAssignments.size)
    assertTrue(reloadedAssignments.any { it.shiftType == ShiftType.SHIFT_22_06.name && it.staffId == dutyStaff[0].id })
    assertTrue(reloadedAssignments.any { it.shiftType == ShiftType.SHIFT_06_14.name && it.staffId == dutyStaff[1].id })
    assertTrue(reloadedAssignments.any { it.shiftType == ShiftType.SHIFT_14_22.name && it.staffId == dutyStaff[2].id })
    assertTrue(reloadedAssignments.any { it.shiftType == ShiftType.SHIFT_08_17.name && it.staffId == dutyStaff[3].id })
    assertEquals(1, reloadedLeaves.size)
    assertEquals(dutyStaff[4].id, reloadedLeaves.first().staffId)
    assertEquals(LeaveStatus.APPROVED.name, reloadedLeaves.first().status)
  }

  @Test
  fun adminToggle_controlsAndPersistsTechnicianShareVisibility_andMedchalEnforcesMonToThuNights(): Unit = runBlocking {
    val repo = LocalRosterRepository(context)
    repo.seedDefaultRosterIfEmpty()

    val vm = createManagedViewModel {
      RosterViewModel(
        repository = repo,
        depotId = DepotRoleManager.SHARED_DEPOT_ID,
        initialActorUid = DepotRoleManager.ADMIN_ACCOUNT_ID,
        initialActorName = DepotRoleManager.ADMIN_DISPLAY_NAME,
        actorEmail = DepotRoleManager.ADMIN_EMAIL,
        isCloudMode = false,
        initialRole = UserRole.ADMIN
      )
    }
    awaitMainAndCondition { vm.staffState.value is UiState.Success }

    // Default Technician share setting is enabled
    assertTrue(vm.depotSettingsState.value.allowTechnicianShare)

    // Non-admin cannot change Technician share setting
    vm.updateSessionActor("tech_1", "P. RAM KUMAR", UserRole.TECHNICIAN)
    vm.setTechnicianShareEnabled(false)
    awaitMainAndCondition { vm.actionStatus.value?.contains("Only Admin", ignoreCase = true) == true }
    assertTrue(vm.depotSettingsState.value.allowTechnicianShare)

    // Admin disables Technician share options -> persisted across reload
    vm.updateSessionActor(DepotRoleManager.ADMIN_ACCOUNT_ID, DepotRoleManager.ADMIN_DISPLAY_NAME, UserRole.ADMIN)
    vm.setTechnicianShareEnabled(false)
    awaitMainAndCondition { !vm.depotSettingsState.value.allowTechnicianShare && !vm.isBusy.value }
    assertFalse(repo.observeDepotSettings(DepotRoleManager.SHARED_DEPOT_ID).first().allowTechnicianShare)

    val reloadedRepo = LocalRosterRepository(context)
    assertFalse(reloadedRepo.observeDepotSettings(DepotRoleManager.SHARED_DEPOT_ID).first().allowTechnicianShare)

    // Medchal (MED) operates Monday night through Thursday night ONLY (blocked on Friday/Saturday/Sunday nights)
    val monday = LocalDate.of(2026, 11, 23) // Monday night
    val thursday = LocalDate.of(2026, 11, 26) // Thursday night
    val friday = LocalDate.of(2026, 11, 27) // Friday night
    val sunday = LocalDate.of(2026, 11, 22) // Sunday night

    assertTrue(ShiftSchedulerEngine.buildSlotsForShift(monday, ShiftType.SHIFT_22_06).any { it.dutyArea == DutyArea.STATION_MED })
    assertTrue(ShiftSchedulerEngine.buildSlotsForShift(thursday, ShiftType.SHIFT_22_06).any { it.dutyArea == DutyArea.STATION_MED })
    assertFalse(ShiftSchedulerEngine.buildSlotsForShift(friday, ShiftType.SHIFT_22_06).any { it.dutyArea == DutyArea.STATION_MED })
    assertFalse(ShiftSchedulerEngine.buildSlotsForShift(sunday, ShiftType.SHIFT_22_06).any { it.dutyArea == DutyArea.STATION_MED })

    val stationStaff = (vm.staffState.value as UiState.Success).data.first { !it.isSupervisory && it.stationDuty }
    val invalidFridayMedSlot = com.example.model.ShiftSlotSpec(
      date = friday.toString(),
      shiftType = ShiftType.SHIFT_22_06,
      dutyArea = DutyArea.STATION_MED,
      roleType = com.example.model.SlotRoleType.GENERAL_DUTY,
      slotIndex = 0,
      label = "Medchal (MED)"
    )
    vm.assignStaffToSlot(invalidFridayMedSlot, stationStaff, false, "", "")
    awaitMainAndCondition { vm.actionStatus.value?.contains("Monday night to Thursday night", ignoreCase = true) == true }
    assertTrue(repo.observeAssignments(DepotRoleManager.SHARED_DEPOT_ID).first().isEmpty())
  }

  @Test
  fun technicianLapSelfUpdateOnce_thenRequiresSupervisorApproval_andPersists(): Unit = runBlocking {
    val repo = LocalRosterRepository(context)
    repo.seedDefaultRosterIfEmpty()
    val techUser = repo.createUserAccount(
      username = "ram_tech",
      password = "password123",
      displayName = "P. RAM KUMAR",
      email = "ram@example.com",
      role = UserRole.TECHNICIAN,
      staffId = "staff_7",
      createdByUid = DepotRoleManager.ADMIN_ACCOUNT_ID
    )

    val vm = createManagedViewModel {
      RosterViewModel(
        repository = repo,
        depotId = DepotRoleManager.SHARED_DEPOT_ID,
        initialActorUid = techUser.uid,
        initialActorName = techUser.displayName,
        actorEmail = techUser.email,
        isCloudMode = false,
        initialRole = UserRole.TECHNICIAN
      )
    }
    awaitMainAndCondition {
      vm.staffState.value is UiState.Success && vm.currentUserProfile.value?.staffId == "staff_7"
    }
    val staffList = (vm.staffState.value as UiState.Success).data
    val ramStaff = staffList.first { it.id == "staff_7" }

    // 1st update: Technician updates own LAP directly (one-time self-update)
    vm.updateLapBalance(ramStaff, enteredRemainingLap = 140, enteredRemainingLhap = 20, reason = "", targetYear = 2026)
    awaitMainAndCondition {
      vm.leaveAccountsState.value.any { it.staffId == "staff_7" && it.lapOpeningBalance == 140 && it.lapSelfUpdated }
    }

    // 2nd update: Technician made a mistake (wanted 152) -> must require Supervisor/Admin approval
    vm.updateLapBalance(ramStaff, enteredRemainingLap = 152, enteredRemainingLhap = 20, reason = "Typo on first entry", targetYear = 2026)
    awaitMainAndCondition {
      val acc = vm.leaveAccountsState.value.firstOrNull { it.staffId == "staff_7" }
      acc != null &&
        acc.lapOpeningBalance == 140 && // unchanged until approved!
        acc.pendingLapBalance == 152 &&
        acc.pendingLapStatus == com.example.model.LapUpdateStatus.PENDING.name
    }

    // Supervisor approves the pending LAP correction -> updates live balance to 152
    val pendingAcc = vm.leaveAccountsState.value.first { it.staffId == "staff_7" }
    vm.updateSessionActor("sup_1", "VENKATA KRISHNA", UserRole.SUPERVISOR)
    vm.approveLapCorrectionRequest(pendingAcc)
    awaitMainAndCondition {
      val acc = vm.leaveAccountsState.value.firstOrNull { it.staffId == "staff_7" }
      acc != null &&
        acc.lapOpeningBalance == 152 &&
        acc.pendingLapStatus == com.example.model.LapUpdateStatus.APPROVED.name &&
        !vm.isBusy.value
    }

    // Verify persistence across repository reload
    val reloadedRepo = LocalRosterRepository(context)
    val persistedAcc = reloadedRepo.observeLeaveAccounts(DepotRoleManager.SHARED_DEPOT_ID).first()
      .first { it.staffId == "staff_7" }
    assertEquals(152, persistedAcc.lapOpeningBalance)
    assertTrue(persistedAcc.lapSelfUpdated)
    assertEquals(com.example.model.LapUpdateStatus.APPROVED.name, persistedAcc.pendingLapStatus)
  }

  @Test
  fun permissionManager_restrictedToSupervisorAndAdmin_persistsAcrossReload(): Unit = runBlocking {
    val repo = LocalRosterRepository(context)
    repo.seedDefaultRosterIfEmpty()

    val vm = createManagedViewModel {
      RosterViewModel(
        repository = repo,
        depotId = DepotRoleManager.SHARED_DEPOT_ID,
        initialActorUid = "tech_1",
        initialActorName = "P. RAM KUMAR",
        actorEmail = "",
        isCloudMode = false,
        initialRole = UserRole.TECHNICIAN
      )
    }
    awaitMainAndCondition { vm.staffState.value is UiState.Success }
    val targetStaff = (vm.staffState.value as UiState.Success).data.first { !it.isSupervisory }
    val dutyDate = LocalDate.of(2026, 11, 22)

    // Technician is blocked from granting duty permissions
    vm.grantDutyPermission(
      staff = targetStaff,
      date = dutyDate,
      shiftType = ShiftType.SHIFT_06_14,
      hours = 2.0,
      reliefType = com.example.model.PermissionReliefType.EARLY_RELIEF,
      reason = "Unauthorized attempt"
    )
    awaitMainAndCondition { vm.actionStatus.value?.contains("Supervisor or Admin", ignoreCase = true) == true }
    assertTrue(repo.observeDutyPermissions(DepotRoleManager.SHARED_DEPOT_ID).first().isEmpty())
    assertTrue(vm.dutyPermissionsState.value.isEmpty())

    // Storemate is also blocked from granting duty permissions
    vm.updateSessionActor("storemate_1", "Depot Storemate", UserRole.STOREMATE)
    vm.grantDutyPermission(
      staff = targetStaff,
      date = dutyDate,
      shiftType = ShiftType.SHIFT_06_14,
      hours = 2.0,
      reliefType = com.example.model.PermissionReliefType.EARLY_RELIEF,
      reason = "Storemate attempt"
    )
    awaitMainAndCondition { vm.actionStatus.value?.contains("Supervisor or Admin", ignoreCase = true) == true }
    assertTrue(repo.observeDutyPermissions(DepotRoleManager.SHARED_DEPOT_ID).first().isEmpty())

    // Supervisor grants 2.5 hours Early Relief permission
    vm.updateSessionActor("sup_1", "VENKATA KRISHNA", UserRole.SUPERVISOR)
    vm.grantDutyPermission(
      staff = targetStaff,
      date = dutyDate,
      shiftType = ShiftType.SHIFT_06_14,
      hours = 2.5,
      reliefType = com.example.model.PermissionReliefType.EARLY_RELIEF,
      reason = "Hospital appointment"
    )
    awaitMainAndCondition {
      vm.dutyPermissionsState.value.size == 1 &&
        !vm.isBusy.value &&
        vm.actionStatus.value?.startsWith("Granted", ignoreCase = true) == true
    }
    val granted = vm.dutyPermissionsState.value.first()
    assertEquals(targetStaff.id, granted.staffId)
    assertEquals(2.5, granted.hours, 0.001)
    assertEquals("VENKATA KRISHNA", granted.grantedByName)

    // Verify persistence across repository reload
    val reloadedRepo = LocalRosterRepository(context)
    val persisted = reloadedRepo.observeDutyPermissions(DepotRoleManager.SHARED_DEPOT_ID).first()
    assertEquals(1, persisted.size)
    assertEquals(2.5, persisted.first().hours, 0.001)

    // Technician switching back observes empty dutyPermissionsState (hidden)
    vm.updateSessionActor("tech_1", "P. RAM KUMAR", UserRole.TECHNICIAN)
    awaitMainAndCondition { vm.dutyPermissionsState.value.isEmpty() }
    assertTrue(vm.dutyPermissionsState.value.isEmpty())

    // Admin sees the record and can delete it
    vm.updateSessionActor(DepotRoleManager.ADMIN_ACCOUNT_ID, DepotRoleManager.ADMIN_DISPLAY_NAME, UserRole.ADMIN)
    awaitMainAndCondition { vm.dutyPermissionsState.value.size == 1 }
    vm.deleteDutyPermission(granted.id)
    awaitMainAndCondition {
      vm.dutyPermissionsState.value.isEmpty() &&
        !vm.isBusy.value &&
        vm.actionStatus.value?.contains("removed", ignoreCase = true) == true
    }
    assertTrue(repo.observeDutyPermissions(DepotRoleManager.SHARED_DEPOT_ID).first().isEmpty())
  }

  @Test
  fun trainingLeaveAndTrainingDue_enforceRoleGatesAndPersistAcrossReload(): Unit = runBlocking {
    val repo = LocalRosterRepository(context)
    repo.seedDefaultRosterIfEmpty()

    val vm = createManagedViewModel {
      RosterViewModel(
        repository = repo,
        depotId = DepotRoleManager.SHARED_DEPOT_ID,
        initialActorUid = "tech_1",
        initialActorName = "P. RAM KUMAR",
        actorEmail = "",
        isCloudMode = false,
        initialRole = UserRole.TECHNICIAN
      )
    }
    awaitMainAndCondition { vm.staffState.value is UiState.Success }
    val targetStaff = (vm.staffState.value as UiState.Success).data.first { !it.isSupervisory }

    // Technician is blocked from setting training due date
    vm.updateStaffTrainingDue(targetStaff.id, targetStaff.name, "2026-12-18", "Safety / PME")
    awaitMainAndCondition { vm.actionStatus.value?.contains("Supervisor or Admin", ignoreCase = true) == true }
    assertEquals("", repo.observeStaff(DepotRoleManager.SHARED_DEPOT_ID).first().first { it.id == targetStaff.id }.trainingDueDate)

    // Supervisor can set training due date & course AND relieve staff with APPROVED Training leave
    vm.updateSessionActor("sup_1", "VENKATA KRISHNA", UserRole.SUPERVISOR)
    vm.updateStaffTrainingDue(targetStaff.id, targetStaff.name, "2026-12-18", "Technical Refresher")
    awaitMainAndCondition {
      !vm.isBusy.value &&
        repo.observeStaff(DepotRoleManager.SHARED_DEPOT_ID).first().first { it.id == targetStaff.id }.trainingDueDate == "2026-12-18"
    }

    val start = LocalDate.of(2026, 12, 16)
    val end = LocalDate.of(2026, 12, 19)
    vm.addLeave(
      staff = targetStaff,
      leaveType = LeaveType.TRAINING,
      startDate = start,
      endDate = end,
      notes = "Periodic Refresher Course",
      autoClearConflictingShifts = true,
      leaveStatus = LeaveStatus.APPROVED
    )
    awaitMainAndCondition {
      !vm.isBusy.value &&
        repo.observeLeaves(DepotRoleManager.SHARED_DEPOT_ID).first().any {
          it.staffId == targetStaff.id && it.leaveTypeEnum == LeaveType.TRAINING
        }
    }

    // Verify persistence across fresh LocalRosterRepository reload from disk
    val reloadedRepo = LocalRosterRepository(context)
    val reloadedStaff = reloadedRepo.observeStaff(DepotRoleManager.SHARED_DEPOT_ID).first().first { it.id == targetStaff.id }
    val reloadedLeaves = reloadedRepo.observeLeaves(DepotRoleManager.SHARED_DEPOT_ID).first()
    assertEquals("2026-12-18", reloadedStaff.trainingDueDate)
    assertEquals("Technical Refresher", reloadedStaff.trainingCourse)
    assertEquals(1, reloadedLeaves.size)
    assertEquals(LeaveType.TRAINING, reloadedLeaves.first().leaveTypeEnum)
    assertTrue(reloadedLeaves.first().isBlockingDate("2026-12-17"))

    // Clearing training due date clears both date & course
    vm.updateStaffTrainingDue(targetStaff.id, targetStaff.name, "", "")
    awaitMainAndCondition {
      !vm.isBusy.value &&
        repo.observeStaff(DepotRoleManager.SHARED_DEPOT_ID).first().first { it.id == targetStaff.id }.trainingDueDate.isEmpty()
    }
    assertEquals("", repo.observeStaff(DepotRoleManager.SHARED_DEPOT_ID).first().first { it.id == targetStaff.id }.trainingCourse)
  }
}


