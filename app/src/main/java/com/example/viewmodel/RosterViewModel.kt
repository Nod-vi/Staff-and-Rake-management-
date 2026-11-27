package com.example.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.BuildConfig
import com.example.auth.DepotRoleManager
import com.example.data.DefaultRakeFormations
import com.example.data.FirestoreCacheSnapshotState
import com.example.data.LocalRosterRepository
import com.example.data.RosterRepository
import com.example.domain.LeaveBalanceEngine
import com.example.domain.RakeFormationEngine
import com.example.domain.ShiftSchedulerEngine
import com.example.model.AppReleaseConfig
import com.example.model.AppUpdateEvaluation
import com.example.model.AppUpdateEvaluator
import com.example.model.Competency
import com.example.model.DepotMenuItem
import com.example.model.DepotSettings
import com.example.model.DepotUserProfile
import com.example.model.Designation
import com.example.model.DraftSlotEntry
import com.example.model.DutyArea
import com.example.model.DutyApprovalRequest
import com.example.model.DutyDateLock
import com.example.model.DutyDraftStatus
import com.example.model.DutyPermissionRecord
import com.example.model.EmergencyOverrideLog
import com.example.model.IMPORTANT_DEPOT_CONTACTS
import com.example.model.ImportantContact
import com.example.model.LapUpdateStatus
import com.example.model.LeaveRecord
import com.example.model.LeaveStatus
import com.example.model.LeaveType
import com.example.model.MenuAccessLevel
import com.example.model.PermissionReliefType
import com.example.model.RakeFormation
import com.example.model.ResolvedRoleAccess
import com.example.model.RoleAccessPolicy
import com.example.model.ShiftSlotAssignment
import com.example.model.ShiftSlotSpec
import com.example.model.ShiftType
import com.example.model.SlotRoleType
import com.example.model.StaffLeaveAccount
import com.example.model.StaffMember
import com.example.model.UserRole
import com.example.model.WeeklyRestDay
import com.example.model.isMedDutyActive
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

sealed interface UiState<out T> {
  data object Loading : UiState<Nothing>
  data class Success<T>(val data: T) : UiState<T>
  data class Error(val message: String) : UiState<Nothing>
}

@OptIn(ExperimentalCoroutinesApi::class)
class RosterViewModel(
  private val repository: RosterRepository,
  private val depotId: String = DepotRoleManager.SHARED_DEPOT_ID,
  initialActorUid: String = depotId,
  initialActorName: String = DepotRoleManager.ADMIN_DISPLAY_NAME,
  private val actorEmail: String = "",
  private val isCloudMode: Boolean = false,
  initialRole: UserRole = UserRole.SUPERVISOR,
  private val localCacheRepository: LocalRosterRepository? = null
) : ViewModel() {

  private val _actorUid = MutableStateFlow(initialActorUid)
  private val _actorName = MutableStateFlow(initialActorName)

  private val _userRole = MutableStateFlow(initialRole)
  val userRole: StateFlow<UserRole> = _userRole.asStateFlow()

  private val _selectedDate = MutableStateFlow(LocalDate.now())
  val selectedDate: StateFlow<LocalDate> = _selectedDate.asStateFlow()

  private val _actionStatus = MutableStateFlow<String?>(null)
  val actionStatus: StateFlow<String?> = _actionStatus.asStateFlow()

  private val _isBusy = MutableStateFlow(false)
  val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

  private val _syncRetryTrigger = MutableStateFlow(0)

  // Unsubmitted or locally edited Storemate drafts keyed by dutyDate (YYYY-MM-DD)
  private val _stagedDraftsByDate = MutableStateFlow<Map<String, List<DraftSlotEntry>>>(emptyMap())
  val stagedDraftsByDate: StateFlow<Map<String, List<DraftSlotEntry>>> = _stagedDraftsByDate.asStateFlow()

  fun retrySync() {
    _syncRetryTrigger.value += 1
  }

  fun syncPendingChanges(showToast: Boolean = false) {
    if (!isCloudMode) return
    viewModelScope.launch {
      try {
        repository.waitForPendingSync()
        if (showToast) {
          _actionStatus.value = "Depot roster synced with cloud."
        }
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        Log.w(TAG, "Pending sync flush deferred", e)
        if (showToast) {
          _actionStatus.value = "Still offline — local changes will sync automatically when connected."
        }
      }
    }
  }

  val cacheSyncState: StateFlow<FirestoreCacheSnapshotState> = _syncRetryTrigger
    .flatMapLatest {
      repository.observeSyncMetadata(depotId)
        .catch { error ->
          Log.w(TAG, "Error observing sync metadata", unwrapFirestoreError(error))
          emit(FirestoreCacheSnapshotState(isFromCache = true))
        }
    }
    .stateIn(viewModelScope, SharingStarted.Eagerly, FirestoreCacheSnapshotState())

  fun updateUserRole(role: UserRole) {
    _userRole.value = role
  }

  fun updateSessionActor(uid: String, displayName: String, role: UserRole) {
    _actorUid.value = uid
    _actorName.value = displayName
    _userRole.value = role
  }

  val staffState: StateFlow<UiState<List<StaffMember>>> = _syncRetryTrigger
    .flatMapLatest {
      repository.observeStaff(depotId)
        .map<List<StaffMember>, UiState<List<StaffMember>>> { UiState.Success(it) }
        .catch { error ->
          val root = unwrapFirestoreError(error)
          Log.w(TAG, "Error observing staff", root)
          emit(UiState.Error(root.message ?: "Failed to load PPIO staff roster"))
        }
    }
    .stateIn(viewModelScope, SharingStarted.Eagerly, UiState.Loading)

  val assignmentsState: StateFlow<UiState<List<ShiftSlotAssignment>>> = _syncRetryTrigger
    .flatMapLatest {
      repository.observeAssignments(depotId)
        .map<List<ShiftSlotAssignment>, UiState<List<ShiftSlotAssignment>>> { UiState.Success(it) }
        .catch { error ->
          val root = unwrapFirestoreError(error)
          Log.w(TAG, "Error observing assignments", root)
          emit(UiState.Error(root.message ?: "Failed to load shift assignments"))
        }
    }
    .stateIn(viewModelScope, SharingStarted.Eagerly, UiState.Loading)

  val leavesState: StateFlow<UiState<List<LeaveRecord>>> = _syncRetryTrigger
    .flatMapLatest {
      repository.observeLeaves(depotId)
        .map<List<LeaveRecord>, UiState<List<LeaveRecord>>> { UiState.Success(it) }
        .catch { error ->
          val root = unwrapFirestoreError(error)
          Log.w(TAG, "Error observing leaves", root)
          emit(UiState.Error(root.message ?: "Failed to load leave records"))
        }
    }
    .stateIn(viewModelScope, SharingStarted.Eagerly, UiState.Loading)

  val overridesState: StateFlow<UiState<List<EmergencyOverrideLog>>> = _syncRetryTrigger
    .flatMapLatest {
      repository.observeOverrides(depotId)
        .map<List<EmergencyOverrideLog>, UiState<List<EmergencyOverrideLog>>> { UiState.Success(it) }
        .catch { error ->
          val root = unwrapFirestoreError(error)
          Log.w(TAG, "Error observing override logs", root)
          emit(UiState.Error(root.message ?: "Failed to load override logs"))
        }
    }
    .stateIn(viewModelScope, SharingStarted.Eagerly, UiState.Loading)

  val dutyDraftsState: StateFlow<List<DutyApprovalRequest>> = _syncRetryTrigger
    .flatMapLatest {
      repository.observeDutyDrafts(depotId)
        .catch { error ->
          Log.w(TAG, "Error observing duty drafts", unwrapFirestoreError(error))
          emit(emptyList())
        }
    }
    .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

  val dutyLocksState: StateFlow<List<DutyDateLock>> = _syncRetryTrigger
    .flatMapLatest {
      repository.observeDutyLocks(depotId)
        .catch { error ->
          Log.w(TAG, "Error observing duty locks", unwrapFirestoreError(error))
          emit(emptyList())
        }
    }
    .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

  val depotSettingsState: StateFlow<DepotSettings> = _syncRetryTrigger
    .flatMapLatest {
      repository.observeDepotSettings(depotId)
        .catch { error ->
          Log.w(TAG, "Error observing depot settings", unwrapFirestoreError(error))
          emit(DepotSettings())
        }
    }
    .stateIn(viewModelScope, SharingStarted.Eagerly, DepotSettings())

  val appReleaseState: StateFlow<AppReleaseConfig> = _syncRetryTrigger
    .flatMapLatest {
      repository.observeAppRelease(depotId)
        .catch { error ->
          Log.w(TAG, "Error observing app release config", unwrapFirestoreError(error))
          emit(AppReleaseConfig())
        }
    }
    .stateIn(viewModelScope, SharingStarted.Eagerly, AppReleaseConfig())

  val appUpdateEvaluationState: StateFlow<AppUpdateEvaluation> = appReleaseState
    .map { release ->
      AppUpdateEvaluator.evaluate(
        installedVersionCode = BuildConfig.VERSION_CODE,
        installedVersionName = BuildConfig.VERSION_NAME,
        release = release
      )
    }
    .stateIn(
      viewModelScope,
      SharingStarted.Eagerly,
      AppUpdateEvaluator.evaluate(
        installedVersionCode = BuildConfig.VERSION_CODE,
        installedVersionName = BuildConfig.VERSION_NAME,
        release = AppReleaseConfig()
      )
    )

  val resolvedRoleAccessState: StateFlow<ResolvedRoleAccess> = combine(
    _userRole,
    depotSettingsState
  ) { role, settings ->
    RoleAccessPolicy.resolve(role, settings.roleMenuAccess)
  }.stateIn(
    viewModelScope,
    SharingStarted.Eagerly,
    RoleAccessPolicy.resolve(initialRole, emptyMap())
  )

  private fun currentAccess(): ResolvedRoleAccess =
    RoleAccessPolicy.resolve(_userRole.value, depotSettingsState.value.roleMenuAccess)

  val leaveAccountsState: StateFlow<List<StaffLeaveAccount>> = _syncRetryTrigger
    .flatMapLatest {
      repository.observeLeaveAccounts(depotId)
        .catch { error ->
          Log.w(TAG, "Error observing leave accounts", unwrapFirestoreError(error))
          emit(emptyList())
        }
    }
    .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

  val currentUserProfile: StateFlow<DepotUserProfile?> = combine(_actorUid, _syncRetryTrigger) { uid, _ -> uid }
    .flatMapLatest { uid ->
      if (uid.isBlank() || (isCloudMode && uid == depotId)) {
        flowOf(null)
      } else {
        repository.observeUserProfile(uid)
          .catch { error ->
            Log.w(TAG, "Error observing own profile", unwrapFirestoreError(error))
            emit(null)
          }
      }
    }
    .stateIn(viewModelScope, SharingStarted.Eagerly, null)

  val allUserProfilesState: StateFlow<List<DepotUserProfile>> = combine(
    resolvedRoleAccessState,
    _syncRetryTrigger
  ) { access, _ -> access.canViewUsers }
    .flatMapLatest { canViewUsers ->
      if (canViewUsers) {
        repository.observeAllUserProfiles()
          .catch { error ->
            Log.w(TAG, "Error observing user roles list", unwrapFirestoreError(error))
            emit(emptyList())
          }
      } else {
        flowOf(emptyList())
      }
    }
    .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

  val dutyPermissionsState: StateFlow<List<DutyPermissionRecord>> = combine(
    resolvedRoleAccessState,
    _syncRetryTrigger
  ) { access, _ -> access.canViewPermissions }
    .flatMapLatest { canViewPermissions ->
      if (canViewPermissions) {
        repository.observeDutyPermissions(depotId)
          .catch { error ->
            Log.w(TAG, "Error observing duty permissions", unwrapFirestoreError(error))
            emit(emptyList())
          }
      } else {
        flowOf(emptyList())
      }
    }
    .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

  val importantContactsState: StateFlow<List<ImportantContact>> = combine(
    resolvedRoleAccessState,
    _syncRetryTrigger
  ) { access, _ -> access.canViewImportantContacts }
    .flatMapLatest { canViewContacts ->
      if (canViewContacts) {
        combine(
          repository.observeImportantContacts(depotId)
            .catch { error ->
              Log.w(TAG, "Error observing important contacts", unwrapFirestoreError(error))
              emit(emptyList())
            },
          depotSettingsState
        ) { storedContacts, settings ->
          if (storedContacts.isEmpty() && !settings.importantContactsInitialized) {
            IMPORTANT_DEPOT_CONTACTS
          } else {
            storedContacts
          }
        }
      } else {
        flowOf(emptyList())
      }
    }
    .stateIn(viewModelScope, SharingStarted.Eagerly, IMPORTANT_DEPOT_CONTACTS)

  // Shared EMU, MEMU, Spare Unit, and Vande Bharat rake formations visible to all depot staff
  val rakesState: StateFlow<List<RakeFormation>> = _syncRetryTrigger
    .flatMapLatest {
      combine(
        repository.observeRakes(depotId)
          .catch { error ->
            Log.w(TAG, "Error observing rake formations", unwrapFirestoreError(error))
            emit(emptyList())
          },
        depotSettingsState
      ) { storedRakes, settings ->
        if (storedRakes.isEmpty() && !settings.rakesInitialized) {
          DefaultRakeFormations.all
        } else {
          storedRakes
        }
      }
    }
    .stateIn(viewModelScope, SharingStarted.Eagerly, DefaultRakeFormations.all)

  init {
    viewModelScope.launch {
      try {
        var resolvedRole = _userRole.value
        val uid = _actorUid.value
        if (uid.isNotBlank() && (!isCloudMode || uid != depotId)) {
          val ensured = repository.ensureUserProfile(uid, actorEmail, _actorName.value)
          resolvedRole = if (DepotRoleManager.isBootstrapAdminEmail(actorEmail, _actorName.value)) {
            UserRole.ADMIN
          } else if (isCloudMode) {
            ensured.roleEnum
          } else {
            _userRole.value
          }
          if (isCloudMode && resolvedRole != _userRole.value) {
            _userRole.value = resolvedRole
          }
        }
        if (!isCloudMode || resolvedRole.canManageStaff) {
          val seeded = repository.seedDefaultRosterIfEmpty()
          if (seeded) {
            _actionStatus.value = "Loaded 35 PPIO depot staff into shared roster."
          }
          runCatching { repository.seedDefaultImportantContactsIfNeeded(uid) }
        }
        if (!isCloudMode || resolvedRole.canManageRakes) {
          runCatching { repository.seedDefaultRakesIfNeeded(uid, _actorName.value) }
        }
        if (isCloudMode && localCacheRepository != null) {
          syncPendingLocalChangesToCloud(resolvedRole)
        }
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        Log.e(TAG, "Failed initialization / seeding PPIO roster", e)
        _actionStatus.value = "Roster sync warning: ${e.localizedMessage ?: "Check connection"}"
      }
    }

    if (isCloudMode) {
      viewModelScope.launch {
        currentUserProfile.collect { profile ->
          if (profile != null) {
            val nextRole = if (
              DepotRoleManager.isBootstrapAdminEmail(
                actorEmail.ifBlank { profile.email },
                _actorName.value.ifBlank { profile.displayName }
              )
            ) {
              UserRole.ADMIN
            } else {
              profile.roleEnum
            }
            if (_userRole.value != nextRole) {
              _userRole.value = nextRole
            }
            if (nextRole.canManageStaff && (staffState.value as? UiState.Success)?.data?.isEmpty() == true) {
              runCatching { repository.seedDefaultRosterIfEmpty() }
            }
            if (localCacheRepository != null) {
              runCatching { syncPendingLocalChangesToCloud(nextRole) }
            }
          }
        }
      }

      if (localCacheRepository != null) {
        viewModelScope.launch {
          staffState.collect { state ->
            if (state is UiState.Success) {
              localCacheRepository.mirrorCloudSnapshot(staff = state.data)
            }
          }
        }
        viewModelScope.launch {
          assignmentsState.collect { state ->
            if (state is UiState.Success) {
              localCacheRepository.mirrorCloudSnapshot(assignments = state.data)
            }
          }
        }
        viewModelScope.launch {
          leavesState.collect { state ->
            if (state is UiState.Success) {
              localCacheRepository.mirrorCloudSnapshot(leaves = state.data)
            }
          }
        }
        viewModelScope.launch {
          overridesState.collect { state ->
            if (state is UiState.Success) {
              localCacheRepository.mirrorCloudSnapshot(overrides = state.data)
            }
          }
        }
        viewModelScope.launch {
          dutyDraftsState.collect { drafts ->
            localCacheRepository.mirrorCloudSnapshot(dutyDrafts = drafts)
          }
        }
        viewModelScope.launch {
          dutyLocksState.collect { locks ->
            localCacheRepository.mirrorCloudSnapshot(dutyLocks = locks)
          }
        }
        viewModelScope.launch {
          leaveAccountsState.collect { accounts ->
            localCacheRepository.mirrorCloudSnapshot(leaveAccounts = accounts)
          }
        }
        viewModelScope.launch {
          dutyPermissionsState.collect { permissions ->
            if (currentAccess().canViewPermissions) {
              localCacheRepository.mirrorCloudSnapshot(dutyPermissions = permissions)
            }
          }
        }
        viewModelScope.launch {
          rakesState.collect { rakes ->
            localCacheRepository.mirrorCloudSnapshot(rakes = rakes)
          }
        }
      }
    }
  }

  private suspend fun syncPendingLocalChangesToCloud(role: UserRole) {
    val cache = localCacheRepository ?: return
    val pending = cache.snapshotPendingLocalChanges()
    if (!pending.hasChanges) return
    val access = RoleAccessPolicy.resolve(role, depotSettingsState.value.roleMenuAccess)

    val syncedStaffIds = mutableSetOf<String>()
    val syncedDeletedStaffIds = mutableSetOf<String>()
    val syncedAssignmentIds = mutableSetOf<String>()
    val syncedDeletedAssignmentIds = mutableSetOf<String>()
    val syncedLeaveIds = mutableSetOf<String>()
    val syncedDeletedLeaveIds = mutableSetOf<String>()

    withContext(NonCancellable) {
      if (access.canManageStaff) {
        pending.dirtyStaff.forEach { member ->
          runCatching { repository.addStaffMember(member) }
            .onSuccess { syncedStaffIds.add(member.id) }
            .onFailure { Log.w(TAG, "Deferred staff sync failed for ${member.id}", it) }
        }
        pending.deletedStaffIds.forEach { staffId ->
          runCatching { repository.deleteStaffMember(staffId) }
            .onSuccess { syncedDeletedStaffIds.add(staffId) }
            .onFailure { Log.w(TAG, "Deferred staff delete failed for $staffId", it) }
        }
      } else if (access.canManageRestDays && pending.dirtyStaff.isNotEmpty()) {
        val restUpdates = pending.dirtyStaff.associate { it.id to it.restDayEnum }
        runCatching { repository.batchUpdateRestDays(restUpdates) }
          .onSuccess { syncedStaffIds.addAll(restUpdates.keys) }
          .onFailure { Log.w(TAG, "Deferred rest-day sync failed", it) }
      }

      if (access.canDirectEditDuties) {
        if (pending.dirtyAssignments.isNotEmpty()) {
          val existingIds = ((assignmentsState.value as? UiState.Success)?.data ?: emptyList())
            .mapTo(HashSet()) { it.id }
          runCatching { repository.batchSaveAssignments(pending.dirtyAssignments, existingIds) }
            .onSuccess { syncedAssignmentIds.addAll(pending.dirtyAssignments.map { it.id }) }
            .onFailure { Log.w(TAG, "Deferred assignments sync failed", it) }
        }
        if (pending.deletedAssignmentIds.isNotEmpty()) {
          runCatching { repository.batchClearAssignments(pending.deletedAssignmentIds.toList()) }
            .onSuccess { syncedDeletedAssignmentIds.addAll(pending.deletedAssignmentIds) }
            .onFailure { Log.w(TAG, "Deferred assignment clears failed", it) }
        }
      }

      if (access.canManageLeaves) {
        pending.dirtyLeaves.forEach { leave ->
          runCatching { repository.addLeaveRecord(leave) }
            .onSuccess { syncedLeaveIds.add(leave.id) }
            .onFailure { Log.w(TAG, "Deferred leave sync failed for ${leave.id}", it) }
        }
        pending.deletedLeaveIds.forEach { leaveId ->
          runCatching { repository.deleteLeaveRecord(leaveId) }
            .onSuccess { syncedDeletedLeaveIds.add(leaveId) }
            .onFailure { Log.w(TAG, "Deferred leave delete failed for $leaveId", it) }
        }
      }

      cache.markLocalChangesSynced(
        syncedStaffIds = syncedStaffIds,
        syncedDeletedStaffIds = syncedDeletedStaffIds,
        syncedAssignmentIds = syncedAssignmentIds,
        syncedDeletedAssignmentIds = syncedDeletedAssignmentIds,
        syncedLeaveIds = syncedLeaveIds,
        syncedDeletedLeaveIds = syncedDeletedLeaveIds
      )
    }

    val totalSynced = syncedAssignmentIds.size + syncedLeaveIds.size + syncedStaffIds.size
    if (totalSynced > 0) {
      _actionStatus.value = "Synced $totalSynced saved depot record(s) to cloud."
    }
  }

  fun selectDate(date: LocalDate) {
    _selectedDate.value = date
  }

  fun previousDay() {
    _selectedDate.value = _selectedDate.value.minusDays(1)
  }

  fun nextDay() {
    _selectedDate.value = _selectedDate.value.plusDays(1)
  }

  fun postStatusMessage(message: String) {
    _actionStatus.value = message
  }

  fun clearStatusMessage() {
    _actionStatus.value = null
  }

  fun isDutyDateLocked(dutyDate: LocalDate): Boolean {
    val dutyDateStr = dutyDate.toString()
    if (dutyLocksState.value.any { it.dutyDate == dutyDateStr }) return true
    val assignments = (assignmentsState.value as? UiState.Success)?.data ?: return false
    val cycleIds = ShiftSchedulerEngine.buildDutyDaySlots(dutyDate).mapTo(HashSet()) { it.documentId }
    return assignments.any { it.id in cycleIds && it.approvedLocked }
  }

  fun assignStaffToSlot(
    slot: ShiftSlotSpec,
    staff: StaffMember,
    isEmergencyOverride: Boolean,
    overrideReason: String,
    conflictSummary: String
  ) {
    val role = _userRole.value
    val access = currentAccess()
    if (!access.canDirectEditDuties && !access.canDraftDuties) {
      _actionStatus.value = "${role.label} has read-only access to shift duties."
      return
    }

    val slotStartDate = LocalDate.parse(slot.date)
    if (staff.isShunter && slot.roleType != SlotRoleType.SHUNTER) {
      _actionStatus.value = "Shunter staff can only be assigned to Shunter slots (not Shed or Station)."
      return
    }
    if (slot.roleType == SlotRoleType.SHUNTER && !staff.isShunter) {
      _actionStatus.value = "Only staff with Shunter designation can be assigned to Shunter slots."
      return
    }
    if (slot.dutyArea == DutyArea.STATION_MED &&
      (slot.shiftType != ShiftType.SHIFT_22_06 || !slotStartDate.isMedDutyActive())
    ) {
      _actionStatus.value = "Medchal (MED) duty operates only from Monday night to Thursday night (22/06)."
      return
    }

    val dutyDate = slot.shiftType.dutyDateForStartDate(slotStartDate)
    val dutyDateStr = dutyDate.toString()

    val activeLeaves = (leavesState.value as? UiState.Success)?.data ?: emptyList()
    if (activeLeaves.any { it.staffId == staff.id && it.isBlockingDate(dutyDateStr) }) {
      _actionStatus.value = "${staff.name} is on approved leave for duty date $dutyDateStr and cannot be assigned."
      return
    }

    if (access.canDraftDuties) {
      if (isDutyDateLocked(dutyDate)) {
        _actionStatus.value = "Duties for $dutyDateStr are approved & locked by Supervisor."
        return
      }
      val baseEntries = currentWorkingDraftForDutyDate(dutyDate)
        .filterNot { it.slotId == slot.documentId }
      val newEntry = DraftSlotEntry(
        slotId = slot.documentId,
        date = slot.date,
        shiftType = slot.shiftType.name,
        dutyArea = slot.dutyArea.name,
        roleType = slot.roleType.name,
        slotIndex = slot.slotIndex.toLong(),
        staffId = staff.id,
        staffName = staff.name,
        isEmergencyOverride = isEmergencyOverride,
        overrideReason = if (isEmergencyOverride) overrideReason.trim() else ""
      )
      _stagedDraftsByDate.value = _stagedDraftsByDate.value + (dutyDateStr to (baseEntries + newEntry))
      _actionStatus.value = "Staged ${staff.name} in ${slot.label} draft (send for approval)."
      return
    }

    // Direct DB save for Supervisor / Admin
    val existingAssignments = (assignmentsState.value as? UiState.Success)?.data ?: emptyList()
    val exists = existingAssignments.any { it.id == slot.documentId }
    val assignment = ShiftSlotAssignment(
      id = slot.documentId,
      userId = depotId,
      date = slot.date,
      shiftType = slot.shiftType.name,
      dutyArea = slot.dutyArea.name,
      roleType = slot.roleType.name,
      slotIndex = slot.slotIndex.toLong(),
      staffId = staff.id,
      staffName = staff.name,
      isEmergencyOverride = isEmergencyOverride,
      overrideReason = if (isEmergencyOverride) overrideReason.trim() else "",
      approvedLocked = true,
      approvedByName = _actorName.value
    )

    viewModelScope.launch {
      try {
        withContext(NonCancellable) {
          repository.saveSlotAssignment(assignment, existingDocPresent = exists)
          if (isEmergencyOverride) {
            repository.logEmergencyOverride(
              EmergencyOverrideLog(
                userId = depotId,
                date = slot.date,
                shiftType = slot.shiftType.name,
                dutyArea = slot.dutyArea.name,
                staffId = staff.id,
                staffName = staff.name,
                conflictSummary = conflictSummary,
                reason = overrideReason.trim().ifBlank { "Emergency operational requirement" }
              )
            )
          }
        }
        if (isEmergencyOverride) {
          _actionStatus.value = "Emergency override saved for ${staff.name} (${slot.shiftType.code})."
        } else {
          _actionStatus.value = "Saved ${staff.name} to ${slot.label}."
        }
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        Log.e(TAG, "Failed to assign slot", e)
        _actionStatus.value = "Failed to save assignment: ${e.localizedMessage ?: "Error"}"
      }
    }
  }

  fun clearSlot(slotId: String) {
    val role = _userRole.value
    val access = currentAccess()
    if (!access.canDirectEditDuties && !access.canDraftDuties) {
      _actionStatus.value = "${role.label} cannot alter shift duties."
      return
    }

    if (access.canDraftDuties) {
      val dutyDate = _selectedDate.value
      val dutyDateStr = dutyDate.toString()
      if (isDutyDateLocked(dutyDate)) {
        _actionStatus.value = "Duties for $dutyDateStr are approved & locked by Supervisor."
        return
      }
      val updated = currentWorkingDraftForDutyDate(dutyDate)
        .filterNot { it.slotId == slotId }
      _stagedDraftsByDate.value = _stagedDraftsByDate.value + (dutyDateStr to updated)
      _actionStatus.value = "Removed slot from staged draft."
      return
    }

    viewModelScope.launch {
      try {
        withContext(NonCancellable) {
          repository.clearSlotAssignment(slotId)
        }
        _actionStatus.value = "Cleared slot assignment."
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        _actionStatus.value = "Failed to clear slot: ${e.localizedMessage ?: "Error"}"
      }
    }
  }

  fun autoAssignSelectedDay(overwriteExisting: Boolean = false) {
    val role = _userRole.value
    val access = currentAccess()
    if (access.canDraftDuties) {
      autoStageStoremateDraft(_selectedDate.value, overwriteExisting)
    } else if (access.canDirectEditDuties) {
      autoAssignDutyDates(listOf(_selectedDate.value), overwriteExisting, "duty day")
    } else {
      _actionStatus.value = "${role.label} cannot assign shift duties."
    }
  }

  fun autoAssignCurrentWeek(overwriteExisting: Boolean = false) {
    if (!currentAccess().canDirectEditDuties) {
      _actionStatus.value = "Full Control on Shifts is required for full-week direct scheduling."
      return
    }
    val start = _selectedDate.value.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    val dates = (0L..6L).map { start.plusDays(it) }
    autoAssignDutyDates(dates, overwriteExisting, "week")
  }

  fun autoAssignTomorrowsDuties(baseToday: LocalDate) {
    if (!currentAccess().canDirectEditDuties) {
      _actionStatus.value = "Full Control on Shifts is required to publish Tomorrow's Duties."
      return
    }
    val tomorrowDutyDate = baseToday.plusDays(1)
    autoAssignDutyDates(listOf(tomorrowDutyDate), overwriteExisting = false, scopeLabel = "Today 22/06 & Tomorrow")
  }

  private fun autoStageStoremateDraft(dutyDate: LocalDate, overwriteExisting: Boolean) {
    val dutyDateStr = dutyDate.toString()
    if (isDutyDateLocked(dutyDate)) {
      _actionStatus.value = "Duties for $dutyDateStr are approved & locked by Supervisor."
      return
    }
    val staff = (staffState.value as? UiState.Success)?.data ?: return
    val publishedAssignments = (assignmentsState.value as? UiState.Success)?.data ?: emptyList()
    val leaves = (leavesState.value as? UiState.Success)?.data ?: emptyList()

    val cycleIds = ShiftSchedulerEngine.buildDutyDaySlots(dutyDate).mapTo(HashSet()) { it.documentId }
    val currentDraftAsAssignments = currentWorkingDraftForDutyDate(dutyDate).map {
      it.toAssignment(depotId, "").copy(approvedLocked = false)
    }
    val mergedBaseline = publishedAssignments.filterNot { it.id in cycleIds } + currentDraftAsAssignments

    viewModelScope.launch {
      _isBusy.value = true
      try {
        val generated = withContext(Dispatchers.Default) {
          ShiftSchedulerEngine.generateAssignmentsForDutyDates(
            userId = depotId,
            dutyDates = listOf(dutyDate),
            staffList = staff,
            existingAssignments = mergedBaseline,
            leaves = leaves,
            overwriteNonOverrides = overwriteExisting
          )
        }
        val baseEntries = if (overwriteExisting) emptyList() else currentWorkingDraftForDutyDate(dutyDate)
        val combinedBySlot = LinkedHashMap<String, DraftSlotEntry>()
        baseEntries.forEach { combinedBySlot[it.slotId] = it }
        generated.forEach { a ->
          combinedBySlot[a.id] = DraftSlotEntry(
            slotId = a.id,
            date = a.date,
            shiftType = a.shiftType,
            dutyArea = a.dutyArea,
            roleType = a.roleType,
            slotIndex = a.slotIndex,
            staffId = a.staffId,
            staffName = a.staffName,
            isEmergencyOverride = a.isEmergencyOverride,
            overrideReason = a.overrideReason
          )
        }
        _stagedDraftsByDate.value = _stagedDraftsByDate.value + (dutyDateStr to combinedBySlot.values.toList())
        _actionStatus.value = if (generated.isEmpty()) {
          "All eligible draft slots are filled. Tap Send for Approval to submit to Supervisor."
        } else {
          "Staged ${generated.size} shift slots in draft. Tap Send for Supervisor Approval to submit."
        }
      } finally {
        _isBusy.value = false
      }
    }
  }

  fun discardStagedDraft(dutyDate: LocalDate) {
    _stagedDraftsByDate.value = _stagedDraftsByDate.value - dutyDate.toString()
    _actionStatus.value = "Discarded local unsubmitted draft for $dutyDate."
  }

  fun submitStagedDraftForApproval(dutyDate: LocalDate) {
    if (!currentAccess().canDraftDuties) {
      _actionStatus.value = "Current role is not configured for approval-based shift drafts."
      return
    }
    val dutyDateStr = dutyDate.toString()
    if (isDutyDateLocked(dutyDate)) {
      _actionStatus.value = "Duties for $dutyDateStr are already approved and locked."
      return
    }
    val proposedSlots = _stagedDraftsByDate.value[dutyDateStr]
      ?: dutyDraftsState.value.firstOrNull {
        it.dutyDate == dutyDateStr && it.statusEnum == DutyDraftStatus.PENDING
      }?.proposedSlots
      ?: emptyList()

    if (proposedSlots.isEmpty()) {
      _actionStatus.value = "Assign at least 1 shift duty before sending for approval."
      return
    }

    val existingPendingId = dutyDraftsState.value.firstOrNull {
      it.dutyDate == dutyDateStr &&
        it.statusEnum == DutyDraftStatus.PENDING &&
        it.submittedByUid == _actorUid.value
    }?.id.orEmpty()

    val draft = DutyApprovalRequest(
      id = existingPendingId,
      depotId = depotId,
      dutyDate = dutyDateStr,
      status = DutyDraftStatus.PENDING.name,
      submittedByUid = _actorUid.value,
      submittedByName = _actorName.value,
      proposedSlots = proposedSlots
    )

    viewModelScope.launch {
      _isBusy.value = true
      try {
        withContext(NonCancellable) {
          repository.submitDutyDraft(draft)
        }
        _stagedDraftsByDate.value = _stagedDraftsByDate.value - dutyDateStr
        _actionStatus.value = "Sent $dutyDateStr (${proposedSlots.size} slots) for approval."
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        _actionStatus.value = "Failed to send draft: ${e.localizedMessage ?: "Error"}"
      } finally {
        _isBusy.value = false
      }
    }
  }

  fun approveDutyDraft(draft: DutyApprovalRequest) {
    if (!currentAccess().canApproveDuties) {
      _actionStatus.value = "Full Control on Shifts is required to approve shift duties."
      return
    }
    val dutyDate = runCatching { LocalDate.parse(draft.dutyDate) }.getOrNull() ?: return
    val allAssignments = (assignmentsState.value as? UiState.Success)?.data ?: emptyList()
    val existingIds = allAssignments.map { it.id }.toSet()
    val cycleIds = ShiftSchedulerEngine.buildDutyDaySlots(dutyDate).mapTo(HashSet()) { it.documentId }
    val cycleAssignmentIdsToClear = allAssignments.filter { it.id in cycleIds }.map { it.id }

    viewModelScope.launch {
      _isBusy.value = true
      try {
        withContext(NonCancellable) {
          repository.approveDutyDraft(
            draft = draft,
            reviewerUid = _actorUid.value,
            reviewerName = _actorName.value,
            cycleAssignmentIdsToClear = cycleAssignmentIdsToClear,
            existingAssignmentIds = existingIds
          )
        }
        _stagedDraftsByDate.value = _stagedDraftsByDate.value - draft.dutyDate
        _actionStatus.value = "Approved & locked ${draft.proposedSlots.size} duties for ${draft.dutyDate}. Visible to all depot users."
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        _actionStatus.value = "Failed to approve draft: ${e.localizedMessage ?: "Error"}"
      } finally {
        _isBusy.value = false
      }
    }
  }

  fun rejectDutyDraft(draft: DutyApprovalRequest, notes: String = "Returned for revision") {
    if (!currentAccess().canApproveDuties) {
      _actionStatus.value = "Full Control on Shifts is required to reject drafts."
      return
    }
    viewModelScope.launch {
      _isBusy.value = true
      try {
        withContext(NonCancellable) {
          repository.rejectDutyDraft(
            draftId = draft.id,
            reviewerUid = _actorUid.value,
            reviewerName = _actorName.value,
            notes = notes.ifBlank { "Returned by Supervisor" }
          )
        }
        _actionStatus.value = "Returned duty draft for ${draft.dutyDate} to ${draft.submittedByName}."
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        _actionStatus.value = "Failed to reject draft: ${e.localizedMessage ?: "Error"}"
      } finally {
        _isBusy.value = false
      }
    }
  }

  private fun currentWorkingDraftForDutyDate(dutyDate: LocalDate): List<DraftSlotEntry> {
    val dutyDateStr = dutyDate.toString()
    _stagedDraftsByDate.value[dutyDateStr]?.let { return it }

    val pendingDraft = dutyDraftsState.value.firstOrNull {
      it.dutyDate == dutyDateStr && it.statusEnum == DutyDraftStatus.PENDING
    }
    if (pendingDraft != null) return pendingDraft.proposedSlots

    val cycleIds = ShiftSchedulerEngine.buildDutyDaySlots(dutyDate).mapTo(HashSet()) { it.documentId }
    val published = (assignmentsState.value as? UiState.Success)?.data ?: emptyList()
    return published.filter { it.id in cycleIds }.map { a ->
      DraftSlotEntry(
        slotId = a.id,
        date = a.date,
        shiftType = a.shiftType,
        dutyArea = a.dutyArea,
        roleType = a.roleType,
        slotIndex = a.slotIndex,
        staffId = a.staffId,
        staffName = a.staffName,
        isEmergencyOverride = a.isEmergencyOverride,
        overrideReason = a.overrideReason
      )
    }
  }

  private fun autoAssignDutyDates(dutyDates: List<LocalDate>, overwriteExisting: Boolean, scopeLabel: String) {
    if (!currentAccess().canDirectEditDuties) {
      _actionStatus.value = "Full Control on Shifts is required to save shift duties directly."
      return
    }
    val staff = (staffState.value as? UiState.Success)?.data ?: return
    val assignments = (assignmentsState.value as? UiState.Success)?.data ?: emptyList()
    val leaves = (leavesState.value as? UiState.Success)?.data ?: emptyList()

    viewModelScope.launch {
      _isBusy.value = true
      try {
        val generated = withContext(Dispatchers.Default) {
          ShiftSchedulerEngine.generateAssignmentsForDutyDates(
            userId = depotId,
            dutyDates = dutyDates,
            staffList = staff,
            existingAssignments = assignments,
            leaves = leaves,
            overwriteNonOverrides = overwriteExisting
          )
        }.map { it.copy(approvedLocked = true, approvedByName = _actorName.value) }
        if (generated.isEmpty()) {
          _actionStatus.value = "All eligible slots for this $scopeLabel are already filled."
        } else {
          val existingIds = assignments.map { it.id }.toSet()
          withContext(NonCancellable) {
            repository.batchSaveAssignments(generated, existingIds)
          }
          _actionStatus.value = "Saved ${generated.size} shift slots for $scopeLabel."
        }
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        Log.e(TAG, "Auto-assign failed", e)
        _actionStatus.value = "Auto-assign error: ${e.localizedMessage ?: "Failed"}"
      } finally {
        _isBusy.value = false
      }
    }
  }

  fun distributeBalancedRestDays() {
    val access = currentAccess()
    if (!access.canManageRestDays && !access.canManageStaff) {
      _actionStatus.value = "Full Control on Leave & Rest is required to update weekly rest days."
      return
    }
    val staff = (staffState.value as? UiState.Success)?.data ?: return

    viewModelScope.launch {
      _isBusy.value = true
      try {
        val distribution = withContext(Dispatchers.Default) {
          ShiftSchedulerEngine.computeBalancedRestDays(staff)
        }
        if (distribution.isEmpty()) return@launch
        withContext(NonCancellable) {
          repository.batchUpdateRestDays(distribution)
        }
        _actionStatus.value = "Distributed staggered weekly rest days across ${distribution.size} active staff."
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        _actionStatus.value = "Failed to distribute rest days: ${e.localizedMessage ?: "Error"}"
      } finally {
        _isBusy.value = false
      }
    }
  }

  fun addStaffMember(
    serialNo: Long,
    name: String,
    designation: Designation,
    competency: Competency,
    stationDuty: Boolean,
    shedDuty: Boolean,
    weeklyRestDay: WeeklyRestDay,
    trainingDueDate: String = "",
    trainingCourse: String = "",
    phoneNumber: String = "",
    secondaryPhoneNumber: String = ""
  ) {
    if (!currentAccess().canManageStaff) {
      _actionStatus.value = "Full Control on Staff Directory is required to add staff."
      return
    }
    val trimmedName = name.trim()
    if (trimmedName.isBlank()) {
      _actionStatus.value = "Staff name cannot be blank."
      return
    }
    if (serialNo !in 1L..9999L) {
      _actionStatus.value = "Serial number must be between 1 and 9999."
      return
    }
    val cleanDueDate = trainingDueDate.trim()
    if (cleanDueDate.isNotBlank() && runCatching { LocalDate.parse(cleanDueDate) }.isFailure) {
      _actionStatus.value = "Training due date must be a valid YYYY-MM-DD date."
      return
    }
    val cleanCourse = if (cleanDueDate.isBlank()) "" else trainingCourse.trim().take(100)
    val cleanPhone = phoneNumber.trim().take(20)
    val cleanSecondaryPhone = secondaryPhoneNumber.trim().take(20)

    val isShunterDesig = designation == Designation.SHUNTER
    val effectiveCompetency = if (isShunterDesig) Competency.NONE else competency
    val effectiveStationDuty = if (isShunterDesig) false else stationDuty
    val effectiveShedDuty = if (isShunterDesig) false else shedDuty

    viewModelScope.launch {
      _isBusy.value = true
      try {
        withContext(NonCancellable) {
          repository.addStaffMember(
            StaffMember(
              userId = depotId,
              serialNo = serialNo,
              name = trimmedName.uppercase(),
              designation = designation.name,
              competency = effectiveCompetency.name,
              stationDuty = effectiveStationDuty,
              shedDuty = effectiveShedDuty,
              weeklyRestDay = weeklyRestDay.name,
              trainingDueDate = cleanDueDate,
              trainingCourse = cleanCourse,
              phoneNumber = cleanPhone,
              secondaryPhoneNumber = cleanSecondaryPhone
            )
          )
        }
        _actionStatus.value = "Added staff #$serialNo ${trimmedName.uppercase()}."
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        _actionStatus.value = "Failed to add staff: ${e.localizedMessage ?: "Error"}"
      } finally {
        _isBusy.value = false
      }
    }
  }

  fun updateStaffMember(member: StaffMember) {
    if (!currentAccess().canManageStaff) {
      _actionStatus.value = "Full Control on Staff Directory is required to edit staff."
      return
    }
    val trimmedName = member.name.trim()
    if (trimmedName.isBlank()) {
      _actionStatus.value = "Staff name cannot be blank."
      return
    }
    if (member.serialNo !in 1L..9999L) {
      _actionStatus.value = "Serial number must be between 1 and 9999."
      return
    }

    val isShunterDesig = member.isShunter
    viewModelScope.launch {
      _isBusy.value = true
      try {
        withContext(NonCancellable) {
          repository.updateStaffMember(
            member.copy(
              name = trimmedName.uppercase(),
              competency = if (isShunterDesig) Competency.NONE.name else member.competency,
              stationDuty = if (isShunterDesig) false else member.stationDuty,
              shedDuty = if (isShunterDesig) false else member.shedDuty,
              phoneNumber = member.phoneNumber.trim().take(20),
              secondaryPhoneNumber = member.secondaryPhoneNumber.trim().take(20)
            )
          )
        }
        _actionStatus.value = "Updated staff profile for ${trimmedName.uppercase()}."
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        _actionStatus.value = "Failed to update staff: ${e.localizedMessage ?: "Error"}"
      } finally {
        _isBusy.value = false
      }
    }
  }

  fun deleteStaffMember(staffId: String, staffName: String) {
    if (!currentAccess().canManageStaff) {
      _actionStatus.value = "Full Control on Staff Directory is required to remove staff."
      return
    }
    viewModelScope.launch {
      _isBusy.value = true
      try {
        val orphanedAssignments = ((assignmentsState.value as? UiState.Success)?.data ?: emptyList())
          .filter { it.staffId == staffId }
          .map { it.id }
        withContext(NonCancellable) {
          repository.deleteStaffMember(staffId)
          if (orphanedAssignments.isNotEmpty()) {
            repository.batchClearAssignments(orphanedAssignments)
          }
        }
        _actionStatus.value = "Removed $staffName from depot staff list."
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        _actionStatus.value = "Failed to remove staff: ${e.localizedMessage ?: "Error"}"
      } finally {
        _isBusy.value = false
      }
    }
  }

  private fun isProtectedAdminProfile(profile: DepotUserProfile): Boolean =
    profile.uid == _actorUid.value ||
      profile.uid == DepotRoleManager.ADMIN_ACCOUNT_ID ||
      profile.email.equals(DepotRoleManager.ADMIN_EMAIL, ignoreCase = true) ||
      profile.username.equals(DepotRoleManager.ADMIN_USERNAME, ignoreCase = true)

  fun assignUserRole(profile: DepotUserProfile, newRole: UserRole, staffId: String = profile.staffId) {
    val access = currentAccess()
    if (!access.canManageUserRoles) {
      _actionStatus.value = "Full Control on Users is required to assign or change user roles."
      return
    }
    if (!access.isAdmin && (newRole == UserRole.ADMIN || profile.roleEnum == UserRole.ADMIN)) {
      _actionStatus.value = "Only Admin can assign or modify Admin accounts."
      return
    }
    if (isProtectedAdminProfile(profile) && newRole != UserRole.ADMIN) {
      _actionStatus.value = "Primary Admin cannot remove Admin role."
      return
    }
    viewModelScope.launch {
      _isBusy.value = true
      try {
        repository.updateUserRole(
          targetUid = profile.uid,
          role = newRole,
          staffId = staffId,
          updatedByUid = _actorUid.value
        )
        _actionStatus.value = "Updated ${profile.displayName} role to ${newRole.label}."
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        _actionStatus.value = "Failed to update user role: ${e.localizedMessage ?: "Error"}"
      } finally {
        _isBusy.value = false
      }
    }
  }

  fun setTechnicianShareEnabled(enabled: Boolean) {
    if (!currentAccess().isAdmin) {
      _actionStatus.value = "Only Admin can change Technician share options."
      return
    }
    viewModelScope.launch {
      _isBusy.value = true
      try {
        withContext(NonCancellable) {
          repository.updateAllowTechnicianShare(enabled, _actorUid.value)
        }
        _actionStatus.value = if (enabled) {
          "Share options enabled for Technicians."
        } else {
          "Share options hidden for Technicians."
        }
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        _actionStatus.value = "Failed to update share setting: ${e.localizedMessage ?: "Error"}"
      } finally {
        _isBusy.value = false
      }
    }
  }

  fun setShowShunterForTechnicians(enabled: Boolean) {
    if (!currentAccess().canApproveDuties) {
      _actionStatus.value = "Full Control on Shifts is required to change Shunter visibility."
      return
    }
    val current = depotSettingsState.value
    viewModelScope.launch {
      _isBusy.value = true
      try {
        withContext(NonCancellable) {
          repository.updateShunterSettings(
            showForTechnicians = enabled,
            includeInExport = current.includeShunterInExport,
            updatedByUid = _actorUid.value
          )
        }
        _actionStatus.value = if (enabled) {
          "Shunter is now visible to Technicians."
        } else {
          "Shunter hidden from Technicians."
        }
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        _actionStatus.value = "Failed to update Shunter visibility: ${e.localizedMessage ?: "Error"}"
      } finally {
        _isBusy.value = false
      }
    }
  }

  fun setIncludeShunterInExport(enabled: Boolean) {
    if (!currentAccess().canApproveDuties) {
      _actionStatus.value = "Full Control on Shifts is required to change Shunter export setting."
      return
    }
    val current = depotSettingsState.value
    viewModelScope.launch {
      _isBusy.value = true
      try {
        withContext(NonCancellable) {
          repository.updateShunterSettings(
            showForTechnicians = current.showShunterForTechnicians,
            includeInExport = enabled,
            updatedByUid = _actorUid.value
          )
        }
        _actionStatus.value = if (enabled) {
          "Shunter will be included in WhatsApp & PDF exports."
        } else {
          "Shunter excluded from WhatsApp & PDF exports."
        }
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        _actionStatus.value = "Failed to update Shunter export setting: ${e.localizedMessage ?: "Error"}"
      } finally {
        _isBusy.value = false
      }
    }
  }

  fun updateRoleMenuAccess(
    targetRole: UserRole,
    menuLevels: Map<DepotMenuItem, MenuAccessLevel>
  ) {
    if (!currentAccess().canAccessRolesManager) {
      _actionStatus.value = "Only Admin can configure role menu access."
      return
    }
    if (targetRole == UserRole.ADMIN) {
      _actionStatus.value = "Admin always retains Full Control on all menu items."
      return
    }
    val updatedMatrix = RoleAccessPolicy.withRoleOverride(
      current = depotSettingsState.value.roleMenuAccess,
      role = targetRole,
      menuLevels = menuLevels
    )
    viewModelScope.launch {
      _isBusy.value = true
      try {
        withContext(NonCancellable) {
          repository.updateRoleMenuAccess(
            roleMenuAccess = updatedMatrix,
            updatedByUid = _actorUid.value
          )
        }
        _actionStatus.value = "Saved menu access rules for ${targetRole.label}."
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        _actionStatus.value = "Failed to save role access: ${e.localizedMessage ?: "Error"}"
      } finally {
        _isBusy.value = false
      }
    }
  }

  fun resetRoleMenuAccess(targetRole: UserRole) {
    if (!currentAccess().canAccessRolesManager) {
      _actionStatus.value = "Only Admin can reset role menu access."
      return
    }
    if (targetRole == UserRole.ADMIN) {
      _actionStatus.value = "Admin already has default Full Control."
      return
    }
    val updatedMatrix = RoleAccessPolicy.withoutRoleOverride(
      current = depotSettingsState.value.roleMenuAccess,
      role = targetRole
    )
    viewModelScope.launch {
      _isBusy.value = true
      try {
        withContext(NonCancellable) {
          repository.updateRoleMenuAccess(
            roleMenuAccess = updatedMatrix,
            updatedByUid = _actorUid.value
          )
        }
        _actionStatus.value = "Reset ${targetRole.label} access to defaults."
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        _actionStatus.value = "Failed to reset role access: ${e.localizedMessage ?: "Error"}"
      } finally {
        _isBusy.value = false
      }
    }
  }

  fun publishAppRelease(
    latestVersionCode: Int,
    latestVersionName: String,
    minSupportedVersionCode: Int,
    mandatory: Boolean,
    downloadUrl: String,
    releaseNotes: String,
    sha256Checksum: String,
    onResult: (Boolean, String) -> Unit = { _, _ -> }
  ) {
    if (!currentAccess().isAdmin) {
      val msg = "Only Admin can publish app updates."
      _actionStatus.value = msg
      onResult(false, msg)
      return
    }
    val validationError = AppUpdateEvaluator.validatePublishInput(
      latestVersionCode = latestVersionCode,
      latestVersionName = latestVersionName,
      minSupportedVersionCode = minSupportedVersionCode,
      downloadUrl = downloadUrl,
      releaseNotes = releaseNotes,
      sha256Checksum = sha256Checksum
    )
    if (validationError != null) {
      _actionStatus.value = validationError
      onResult(false, validationError)
      return
    }
    viewModelScope.launch {
      _isBusy.value = true
      try {
        val release = AppReleaseConfig(
          id = AppReleaseConfig.LATEST_DOC_ID,
          latestVersionCode = latestVersionCode,
          latestVersionName = latestVersionName.trim(),
          minSupportedVersionCode = minSupportedVersionCode,
          mandatory = mandatory,
          downloadUrl = downloadUrl.trim(),
          releaseNotes = releaseNotes.trim(),
          sha256Checksum = sha256Checksum.trim(),
          publishedByUid = _actorUid.value,
          publishedByName = _actorName.value.ifBlank { DepotRoleManager.ADMIN_DISPLAY_NAME }
        )
        withContext(NonCancellable) {
          repository.publishAppRelease(release)
        }
        val modeLabel = if (mandatory) "mandatory" else "optional"
        val successMsg = "Published v${release.latestVersionName} ($modeLabel update) to all depot users."
        _actionStatus.value = successMsg
        onResult(true, successMsg)
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        val errMsg = "Failed to publish update: ${e.localizedMessage ?: "Permission or network error"}"
        _actionStatus.value = errMsg
        onResult(false, errMsg)
      } finally {
        _isBusy.value = false
      }
    }
  }

  fun clearAppRelease(onResult: (Boolean, String) -> Unit = { _, _ -> }) {
    if (!currentAccess().isAdmin) {
      val msg = "Only Admin can pause or clear update notices."
      _actionStatus.value = msg
      onResult(false, msg)
      return
    }
    viewModelScope.launch {
      _isBusy.value = true
      try {
        withContext(NonCancellable) {
          repository.clearAppRelease(
            updatedByUid = _actorUid.value,
            updatedByName = _actorName.value.ifBlank { DepotRoleManager.ADMIN_DISPLAY_NAME }
          )
        }
        val msg = "App update notice paused for all users."
        _actionStatus.value = msg
        onResult(true, msg)
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        val errMsg = "Failed to pause update notice: ${e.localizedMessage ?: "Error"}"
        _actionStatus.value = errMsg
        onResult(false, errMsg)
      } finally {
        _isBusy.value = false
      }
    }
  }

  fun createUserAccount(
    username: String,
    password: String,
    displayName: String,
    email: String,
    role: UserRole,
    staffId: String = ""
  ) {
    val access = currentAccess()
    if (!access.canManageUserRoles) {
      _actionStatus.value = "Full Control on Users is required to create user accounts."
      return
    }
    if (!access.isAdmin && role == UserRole.ADMIN) {
      _actionStatus.value = "Only Admin can create Admin accounts."
      return
    }
    val cleanEmail = email.trim().lowercase()
    val cleanUsername = username.trim().ifBlank {
      if (isCloudMode && cleanEmail.contains("@")) {
        cleanEmail.substringBefore("@").replace(Regex("[^a-zA-Z0-9._-]"), "")
      } else {
        ""
      }
    }
    val cleanName = displayName.trim()
    if (cleanName.isBlank()) {
      _actionStatus.value = "User full name is required."
      return
    }
    if (isCloudMode) {
      if (!cleanEmail.contains("@") || !cleanEmail.contains(".") || cleanEmail.contains(" ")) {
        _actionStatus.value = "Enter a valid Google Email so the user can sign in on another phone."
        return
      }
    } else {
      if (cleanUsername.length < 2 || cleanUsername.contains(" ")) {
        _actionStatus.value = "Username must be at least 2 characters without spaces."
        return
      }
      if (password.length < 4) {
        _actionStatus.value = "Password must be at least 4 characters."
        return
      }
    }
    viewModelScope.launch {
      _isBusy.value = true
      try {
        val created = withContext(NonCancellable) {
          repository.createUserAccount(
            username = cleanUsername,
            password = password,
            displayName = cleanName,
            email = cleanEmail,
            role = role,
            staffId = staffId,
            createdByUid = _actorUid.value
          )
        }
        _actionStatus.value = when {
          isCloudMode && created.isPendingCloudInvite ->
            "Invited ${created.email} as ${created.roleEnum.label}. Ask them to tap Sign in with Google on their phone."
          isCloudMode ->
            "Updated ${created.email} (${created.displayName}) to ${created.roleEnum.label} live across devices."
          else ->
            "Created local ${created.roleEnum.label} user @${created.username} (${created.displayName}) on this phone."
        }
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        _actionStatus.value = e.message ?: "Failed to create user."
      } finally {
        _isBusy.value = false
      }
    }
  }

  fun updateUserAccount(
    profile: DepotUserProfile,
    username: String,
    displayName: String,
    email: String,
    role: UserRole,
    staffId: String = profile.staffId,
    newPassword: String? = null
  ) {
    val access = currentAccess()
    if (!access.canManageUserRoles) {
      _actionStatus.value = "Full Control on Users is required to edit user accounts."
      return
    }
    if (!access.isAdmin && (role == UserRole.ADMIN || profile.roleEnum == UserRole.ADMIN)) {
      _actionStatus.value = "Only Admin can assign or edit Admin accounts."
      return
    }
    val cleanEmail = email.trim().lowercase()
    val cleanUsername = username.trim().ifBlank {
      if (isCloudMode && cleanEmail.contains("@")) {
        cleanEmail.substringBefore("@").replace(Regex("[^a-zA-Z0-9._-]"), "")
      } else {
        ""
      }
    }
    val cleanName = displayName.trim()
    if (cleanName.isBlank()) {
      _actionStatus.value = "User full name is required."
      return
    }
    if (!isCloudMode && (cleanUsername.length < 2 || cleanUsername.contains(" "))) {
      _actionStatus.value = "Username must be at least 2 characters without spaces."
      return
    }
    if (isCloudMode && profile.isPendingCloudInvite && (!cleanEmail.contains("@") || !cleanEmail.contains("."))) {
      _actionStatus.value = "Enter a valid Google Email for this invited user."
      return
    }
    if (!newPassword.isNullOrBlank() && newPassword.length < 4) {
      _actionStatus.value = "New password must be at least 4 characters."
      return
    }
    if (isProtectedAdminProfile(profile) && role != UserRole.ADMIN) {
      _actionStatus.value = "Primary Admin cannot remove Admin role."
      return
    }
    viewModelScope.launch {
      _isBusy.value = true
      try {
        withContext(NonCancellable) {
          repository.updateUserAccount(
            targetUid = profile.uid,
            username = cleanUsername,
            displayName = cleanName,
            email = cleanEmail,
            role = role,
            staffId = staffId,
            newPassword = newPassword?.takeIf { it.isNotBlank() },
            updatedByUid = _actorUid.value
          )
        }
        if (profile.uid == _actorUid.value) {
          _actorName.value = cleanName
        }
        _actionStatus.value = "Updated user ${cleanName} (${role.label})."
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        _actionStatus.value = e.message ?: "Failed to update user."
      } finally {
        _isBusy.value = false
      }
    }
  }

  fun deleteUserAccount(profile: DepotUserProfile) {
    val access = currentAccess()
    if (!access.canManageUserRoles) {
      _actionStatus.value = "Full Control on Users is required to remove user accounts."
      return
    }
    if (!access.isAdmin && profile.roleEnum == UserRole.ADMIN) {
      _actionStatus.value = "Only Admin can remove Admin accounts."
      return
    }
    if (isProtectedAdminProfile(profile)) {
      _actionStatus.value = "Primary Admin account cannot be removed."
      return
    }
    viewModelScope.launch {
      _isBusy.value = true
      try {
        repository.deleteUserAccount(profile.uid)
        _actionStatus.value = "Removed user ${profile.displayName} (@${profile.username.ifBlank { profile.uid }})."
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        _actionStatus.value = e.message ?: "Failed to remove user."
      } finally {
        _isBusy.value = false
      }
    }
  }

  fun changeCurrentUserPassword(
    currentPassword: String,
    newPassword: String,
    confirmPassword: String,
    onResult: (Boolean, String) -> Unit = { _, _ -> }
  ) {
    if (newPassword.length < 6) {
      val msg = "New password must be at least 6 characters."
      onResult(false, msg)
      return
    }
    if (newPassword != confirmPassword) {
      val msg = "New password and confirmation do not match."
      onResult(false, msg)
      return
    }
    viewModelScope.launch {
      _isBusy.value = true
      try {
        val ok = repository.changeUserPassword(_actorUid.value, currentPassword, newPassword)
        if (ok) {
          val msg = "Password updated successfully."
          _actionStatus.value = msg
          onResult(true, msg)
        } else {
          val msg = "Current password is incorrect."
          onResult(false, msg)
        }
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        val msg = e.localizedMessage ?: "Failed to change password."
        onResult(false, msg)
      } finally {
        _isBusy.value = false
      }
    }
  }

  fun updateStaffRestDay(staffId: String, restDay: WeeklyRestDay) {
    val access = currentAccess()
    if (!access.canManageRestDays && !access.canManageStaff) {
      _actionStatus.value = "Full Control on Leave & Rest is required to update weekly rest days."
      return
    }
    viewModelScope.launch {
      try {
        withContext(NonCancellable) {
          repository.updateStaffRestDay(staffId, restDay)
        }
        _actionStatus.value = "Updated weekly rest day to ${restDay.label}."
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        _actionStatus.value = "Failed to update rest day: ${e.localizedMessage ?: "Error"}"
      }
    }
  }

  fun updateStaffTrainingDue(
    staffId: String,
    staffName: String,
    trainingDueDate: String,
    trainingCourse: String
  ) {
    if (!currentAccess().canManageTrainingDue) {
      _actionStatus.value = "Edit access on Staff Directory is required to update training due dates."
      return
    }
    val cleanDueDate = trainingDueDate.trim()
    if (cleanDueDate.isNotBlank() && runCatching { LocalDate.parse(cleanDueDate) }.isFailure) {
      _actionStatus.value = "Training due date must be a valid YYYY-MM-DD date."
      return
    }
    val cleanCourse = if (cleanDueDate.isBlank()) "" else trainingCourse.trim().take(100)
    viewModelScope.launch {
      _isBusy.value = true
      try {
        withContext(NonCancellable) {
          repository.updateStaffTrainingDue(staffId, cleanDueDate, cleanCourse)
        }
        _actionStatus.value = if (cleanDueDate.isBlank()) {
          "Cleared training due date for $staffName."
        } else {
          val courseSuffix = if (cleanCourse.isNotBlank()) " ($cleanCourse)" else ""
          "Training due set for $staffName on $cleanDueDate$courseSuffix."
        }
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        _actionStatus.value = "Failed to update training due: ${e.localizedMessage ?: "Error"}"
      } finally {
        _isBusy.value = false
      }
    }
  }

  fun addLeave(
    staff: StaffMember,
    leaveType: LeaveType,
    startDate: LocalDate,
    endDate: LocalDate,
    notes: String,
    autoClearConflictingShifts: Boolean = true,
    leaveStatus: LeaveStatus = LeaveStatus.APPROVED,
    forwardedToSupervisor: StaffMember? = null
  ) {
    if (endDate.isBefore(startDate)) {
      _actionStatus.value = "Leave end date cannot be before start date."
      return
    }
    val role = _userRole.value
    val access = currentAccess()
    if (!access.canViewLeave || access.isReadOnlyLeave) {
      _actionStatus.value = "${role.label} has read-only access to leave records."
      return
    }
    val isSelfApplicantOnly = !access.canApproveLeaves
    if (isSelfApplicantOnly) {
      val linkedStaffId = currentUserProfile.value?.staffId?.trim().orEmpty()
      if (linkedStaffId.isBlank()) {
        _actionStatus.value = "Only linked staff can apply for leave. Ask Admin to link your account in the Users tab."
        return
      }
      if (staff.id != linkedStaffId) {
        _actionStatus.value = "You can only apply leave for your own linked staff profile."
        return
      }
      if (forwardedToSupervisor == null || forwardedToSupervisor.designationEnum != Designation.SSE) {
        _actionStatus.value = "Select an SSE Supervisor to forward this leave request."
        return
      }
    }

    val effectiveStatus = if (isSelfApplicantOnly) LeaveStatus.PENDING else leaveStatus
    val startStr = startDate.toString()
    val endStr = endDate.toString()
    val existingLeaves = (leavesState.value as? UiState.Success)?.data ?: emptyList()
    val hasOverlap = existingLeaves.any { existing ->
      existing.staffId == staff.id &&
        existing.statusEnum != LeaveStatus.REJECTED &&
        existing.statusEnum != LeaveStatus.CANCELLED &&
        existing.startDate <= endStr &&
        existing.endDate >= startStr
    }
    if (hasOverlap) {
      _actionStatus.value = "${staff.name} already has a leave record overlapping $startStr to $endStr."
      return
    }

    viewModelScope.launch {
      _isBusy.value = true
      try {
        val existingAssignments = (assignmentsState.value as? UiState.Success)?.data ?: emptyList()
        val impactedIds = if (!isSelfApplicantOnly && effectiveStatus.blocksShiftDuty) {
          existingAssignments
            .filter { it.staffId == staff.id && it.dutyDateStr in startStr..endStr }
            .map { it.id }
        } else {
          emptyList()
        }

        withContext(NonCancellable) {
          repository.addLeaveRecord(
            LeaveRecord(
              userId = depotId,
              staffId = staff.id,
              staffName = staff.name,
              leaveType = leaveType.name,
              startDate = startStr,
              endDate = endStr,
              status = effectiveStatus.name,
              appliedByUid = _actorUid.value,
              appliedByName = _actorName.value,
              forwardedToStaffId = forwardedToSupervisor?.id.orEmpty(),
              forwardedToName = forwardedToSupervisor?.name.orEmpty(),
              reviewedByName = if (effectiveStatus != LeaveStatus.PENDING && access.canApproveLeaves) _actorName.value else "",
              notes = notes.trim()
            )
          )
          if (!isSelfApplicantOnly && impactedIds.isNotEmpty() && autoClearConflictingShifts) {
            repository.batchClearAssignments(impactedIds)
          }
        }

        if (isSelfApplicantOnly) {
          _actionStatus.value = "Leave application for ${staff.name} forwarded to SSE ${forwardedToSupervisor?.name}."
        } else if (impactedIds.isNotEmpty() && autoClearConflictingShifts) {
          _actionStatus.value =
            "${effectiveStatus.label} leave saved for ${staff.name} ($startStr to $endStr). Vacated ${impactedIds.size} overlapping shift slot(s)."
        } else if (impactedIds.isNotEmpty()) {
          _actionStatus.value =
            "${effectiveStatus.label} leave saved for ${staff.name}. Warning: ${impactedIds.size} existing shift assignment(s) now have a leave conflict!"
        } else {
          _actionStatus.value = "${effectiveStatus.label} leave recorded for ${staff.name} ($startStr to $endStr)."
        }
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        _actionStatus.value = "Failed to save leave: ${e.localizedMessage ?: "Error"}"
      } finally {
        _isBusy.value = false
      }
    }
  }

  fun updateLeavePeriodAndStatus(
    leave: LeaveRecord,
    leaveType: LeaveType,
    startDate: LocalDate,
    endDate: LocalDate,
    status: LeaveStatus,
    notes: String,
    autoClearConflictingShifts: Boolean = true
  ) {
    if (!currentAccess().canManageLeaves) {
      _actionStatus.value = "Full Control on Leave & Rest is required to approve or update leave records."
      return
    }
    if (endDate.isBefore(startDate)) {
      _actionStatus.value = "Leave end date cannot be before start date."
      return
    }
    val startStr = startDate.toString()
    val endStr = endDate.toString()
    if (status == LeaveStatus.APPROVED || status == LeaveStatus.PENDING) {
      val existingLeaves = (leavesState.value as? UiState.Success)?.data ?: emptyList()
      val hasOverlap = existingLeaves.any { existing ->
        existing.id != leave.id &&
          existing.staffId == leave.staffId &&
          existing.statusEnum != LeaveStatus.REJECTED &&
          existing.statusEnum != LeaveStatus.CANCELLED &&
          existing.startDate <= endStr &&
          existing.endDate >= startStr
      }
      if (hasOverlap) {
        _actionStatus.value = "Updated dates overlap another active leave for ${leave.staffName}."
        return
      }
    }

    viewModelScope.launch {
      _isBusy.value = true
      try {
        val existingAssignments = (assignmentsState.value as? UiState.Success)?.data ?: emptyList()
        val impactedIds = if (status.blocksShiftDuty) {
          existingAssignments
            .filter { it.staffId == leave.staffId && it.dutyDateStr in startStr..endStr }
            .map { it.id }
        } else {
          emptyList()
        }

        withContext(NonCancellable) {
          repository.updateLeaveRecord(
            leaveId = leave.id,
            leaveType = leaveType,
            startDate = startStr,
            endDate = endStr,
            status = status,
            notes = notes.trim(),
            reviewedByName = _actorName.value
          )
          if (impactedIds.isNotEmpty() && autoClearConflictingShifts) {
            repository.batchClearAssignments(impactedIds)
          }
        }

        if (impactedIds.isNotEmpty() && autoClearConflictingShifts) {
          _actionStatus.value =
            "Updated ${leave.staffName} (${status.label}: $startStr to $endStr). Vacated ${impactedIds.size} overlapping shift slot(s)."
        } else if (!status.blocksShiftDuty) {
          _actionStatus.value =
            "Updated ${leave.staffName} to ${status.label} ($startStr to $endStr) — staff available for shift duty."
        } else {
          _actionStatus.value =
            "Updated leave for ${leave.staffName} (${status.label}: $startStr to $endStr)."
        }
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        _actionStatus.value = "Failed to update leave: ${e.localizedMessage ?: "Error"}"
      } finally {
        _isBusy.value = false
      }
    }
  }

  fun approvePendingLeave(leave: LeaveRecord) {
    updateLeavePeriodAndStatus(
      leave = leave,
      leaveType = leave.leaveTypeEnum,
      startDate = LocalDate.parse(leave.startDate),
      endDate = LocalDate.parse(leave.endDate),
      status = LeaveStatus.APPROVED,
      notes = leave.notes,
      autoClearConflictingShifts = true
    )
  }

  fun rejectPendingLeave(leave: LeaveRecord) {
    updateLeavePeriodAndStatus(
      leave = leave,
      leaveType = leave.leaveTypeEnum,
      startDate = LocalDate.parse(leave.startDate),
      endDate = LocalDate.parse(leave.endDate),
      status = LeaveStatus.REJECTED,
      notes = leave.notes,
      autoClearConflictingShifts = false
    )
  }

  fun deleteLeave(leaveId: String) {
    val existing = ((leavesState.value as? UiState.Success)?.data ?: emptyList())
      .firstOrNull { it.id == leaveId }
    val access = currentAccess()
    val canDelete = access.canManageLeaves ||
      (access.canApplyLeaveOnly &&
        existing != null &&
        existing.statusEnum == LeaveStatus.PENDING &&
        existing.appliedByUid == _actorUid.value)
    if (!canDelete) {
      _actionStatus.value = "Full Control on Leave & Rest is required to remove approved or other users' leave records."
      return
    }
    viewModelScope.launch {
      try {
        withContext(NonCancellable) {
          repository.deleteLeaveRecord(leaveId)
        }
        _actionStatus.value = "Leave record removed."
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        _actionStatus.value = "Failed to delete leave: ${e.localizedMessage ?: "Error"}"
      }
    }
  }

  /**
   * Updates a staff member's LAP and LHAP remaining leave balances for [targetYear]:
   * - Full Control on Leave: updates both balances directly (and clears any pending request).
   * - Editable with Approval on Leave (linked own profile only):
   *   - First update (`lapSelfUpdated == false`): updates both LAP and LHAP directly and marks `lapSelfUpdated = true`.
   *   - Subsequent updates (`lapSelfUpdated == true`): submits a PENDING correction request
   *     requiring approval.
   */
  fun updateLapBalance(
    staff: StaffMember,
    enteredRemainingLap: Int,
    enteredRemainingLhap: Int,
    reason: String = "",
    targetYear: Int = LocalDate.now().year
  ) {
    if (enteredRemainingLap !in 0..999 || enteredRemainingLhap !in 0..999) {
      _actionStatus.value = "LAP and LHAP balances must be between 0 and 999 days."
      return
    }
    val role = _userRole.value
    val access = currentAccess()
    if (!access.canViewLeave || access.isReadOnlyLeave) {
      _actionStatus.value = "${role.label} has read-only access to leave records."
      return
    }
    val isApprover = access.canApproveLeaves
    if (!isApprover) {
      val linkedStaffId = currentUserProfile.value?.staffId?.trim().orEmpty()
      if (linkedStaffId.isBlank() || linkedStaffId != staff.id) {
        _actionStatus.value = "You can only update leave balance for your own linked staff profile."
        return
      }
    }

    val allLeaves = (leavesState.value as? UiState.Success)?.data ?: emptyList()
    val existing = leaveAccountsState.value.firstOrNull { it.staffId == staff.id || it.id == staff.id }
    val availedLapInYear = LeaveBalanceEngine.approvedDaysInYear(
      staffId = staff.id,
      leaveType = LeaveType.LAP,
      year = targetYear,
      allLeaves = allLeaves
    )
    val availedLhapInYear = LeaveBalanceEngine.approvedDaysInYear(
      staffId = staff.id,
      leaveType = LeaveType.LHAP,
      year = targetYear,
      allLeaves = allLeaves
    )
    val newLapOpening = LeaveBalanceEngine.computeOpeningBalanceForEnteredRemaining(
      enteredRemaining = enteredRemainingLap,
      availedInYear = availedLapInYear
    )
    val newLhapOpening = LeaveBalanceEngine.computeOpeningBalanceForEnteredRemaining(
      enteredRemaining = enteredRemainingLhap,
      availedInYear = availedLhapInYear
    )

    // Non-approver who already used their one-time self-update MUST submit a correction request
    if (!isApprover && existing?.lapSelfUpdated == true) {
      val cleanReason = reason.trim()
      if (cleanReason.isBlank()) {
        _actionStatus.value = "Please enter a brief reason for Supervisor/Admin leave balance approval."
        return
      }
      val correctionAccount = existing.copy(
        staffName = staff.name,
        pendingLapBalance = enteredRemainingLap,
        pendingLhapBalance = enteredRemainingLhap,
        pendingLapYear = targetYear,
        pendingLapReason = cleanReason.take(300),
        pendingLapStatus = LapUpdateStatus.PENDING.name,
        pendingLapRequestedByUid = _actorUid.value
      )
      viewModelScope.launch {
        _isBusy.value = true
        try {
          withContext(NonCancellable) {
            repository.saveLeaveAccount(correctionAccount, isTechnicianCorrectionRequest = true)
          }
          _actionStatus.value =
            "Leave balance update request (LAP: $enteredRemainingLap · LHAP: $enteredRemainingLhap) submitted for Supervisor / Admin approval."
        } catch (e: CancellationException) {
          throw e
        } catch (e: Exception) {
          _actionStatus.value = "Failed to request leave balance update: ${e.localizedMessage ?: "Error"}"
        } finally {
          _isBusy.value = false
        }
      }
      return
    }

    val nextAccount = if (isApprover) {
      StaffLeaveAccount(
        id = staff.id,
        staffId = staff.id,
        staffName = staff.name,
        baseYear = targetYear,
        lapOpeningBalance = newLapOpening,
        lhapOpeningBalance = newLhapOpening,
        lapSelfUpdated = existing?.lapSelfUpdated ?: false,
        pendingLapBalance = 0,
        pendingLhapBalance = 0,
        pendingLapYear = 0,
        pendingLapReason = "",
        pendingLapStatus = if (existing?.pendingLapStatusEnum == LapUpdateStatus.PENDING) {
          LapUpdateStatus.APPROVED.name
        } else {
          LapUpdateStatus.NONE.name
        },
        pendingLapRequestedByUid = "",
        pendingLapReviewedByName = _actorName.value
      )
    } else {
      // One-time self-update for both LAP & LHAP
      StaffLeaveAccount(
        id = staff.id,
        staffId = staff.id,
        staffName = staff.name,
        baseYear = targetYear,
        lapOpeningBalance = newLapOpening,
        lhapOpeningBalance = newLhapOpening,
        lapSelfUpdated = true,
        pendingLapBalance = 0,
        pendingLhapBalance = 0,
        pendingLapYear = 0,
        pendingLapReason = "",
        pendingLapStatus = LapUpdateStatus.NONE.name,
        pendingLapRequestedByUid = "",
        pendingLapReviewedByName = ""
      )
    }

    viewModelScope.launch {
      _isBusy.value = true
      try {
        withContext(NonCancellable) {
          repository.saveLeaveAccount(nextAccount, isTechnicianCorrectionRequest = false)
        }
        _actionStatus.value = if (isApprover) {
          "Updated leave balance for ${staff.name} (LAP: $enteredRemainingLap · LHAP: $enteredRemainingLhap)."
        } else {
          "Leave balance updated (LAP: $enteredRemainingLap · LHAP: $enteredRemainingLhap). Future changes require Supervisor or Admin approval."
        }
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        _actionStatus.value = "Failed to update leave balance: ${e.localizedMessage ?: "Error"}"
      } finally {
        _isBusy.value = false
      }
    }
  }

  fun approveLapCorrectionRequest(account: StaffLeaveAccount) {
    if (!currentAccess().canApproveLeaves) {
      _actionStatus.value = "Full Control on Leave & Rest is required to approve leave balance updates."
      return
    }
    val targetYear = account.pendingLapYear.takeIf { it in 2020..2100 } ?: LocalDate.now().year
    val allLeaves = (leavesState.value as? UiState.Success)?.data ?: emptyList()
    val availedLapInYear = LeaveBalanceEngine.approvedDaysInYear(
      staffId = account.staffId,
      leaveType = LeaveType.LAP,
      year = targetYear,
      allLeaves = allLeaves
    )
    val newLapOpening = LeaveBalanceEngine.computeOpeningBalanceForEnteredRemaining(
      enteredRemaining = account.pendingLapBalance,
      availedInYear = availedLapInYear
    )
    val newLhapOpening = if (account.pendingLhapBalance >= 0) {
      val availedLhapInYear = LeaveBalanceEngine.approvedDaysInYear(
        staffId = account.staffId,
        leaveType = LeaveType.LHAP,
        year = targetYear,
        allLeaves = allLeaves
      )
      LeaveBalanceEngine.computeOpeningBalanceForEnteredRemaining(
        enteredRemaining = account.pendingLhapBalance,
        availedInYear = availedLhapInYear
      )
    } else {
      LeaveBalanceEngine.computeCarryForwardOpeningForYear(
        staffId = account.staffId,
        leaveType = LeaveType.LHAP,
        targetYear = targetYear,
        annualCredit = LeaveBalanceEngine.ANNUAL_LHAP,
        accountBaseYear = account.baseYear,
        accountBaseOpening = account.lhapOpeningBalance,
        allLeaves = allLeaves
      )
    }
    val approved = account.copy(
      baseYear = targetYear,
      lapOpeningBalance = newLapOpening,
      lhapOpeningBalance = newLhapOpening,
      lapSelfUpdated = true,
      pendingLapStatus = LapUpdateStatus.APPROVED.name,
      pendingLapReviewedByName = _actorName.value
    )
    viewModelScope.launch {
      _isBusy.value = true
      try {
        withContext(NonCancellable) {
          repository.saveLeaveAccount(approved, isTechnicianCorrectionRequest = false)
        }
        val lhapSummary = if (account.pendingLhapBalance >= 0) " · LHAP: ${account.pendingLhapBalance}" else ""
        _actionStatus.value =
          "Approved leave balance update for ${account.staffName} (LAP: ${account.pendingLapBalance}$lhapSummary)."
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        _actionStatus.value = "Failed to approve leave balance update: ${e.localizedMessage ?: "Error"}"
      } finally {
        _isBusy.value = false
      }
    }
  }

  fun rejectLapCorrectionRequest(account: StaffLeaveAccount) {
    if (!currentAccess().canApproveLeaves) {
      _actionStatus.value = "Full Control on Leave & Rest is required to reject leave balance updates."
      return
    }
    val rejected = account.copy(
      pendingLapStatus = LapUpdateStatus.REJECTED.name,
      pendingLapReviewedByName = _actorName.value
    )
    viewModelScope.launch {
      _isBusy.value = true
      try {
        withContext(NonCancellable) {
          repository.saveLeaveAccount(rejected, isTechnicianCorrectionRequest = false)
        }
        _actionStatus.value = "Rejected leave balance update request for ${account.staffName}."
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        _actionStatus.value = "Failed to reject leave balance update: ${e.localizedMessage ?: "Error"}"
      } finally {
        _isBusy.value = false
      }
    }
  }

  fun grantDutyPermission(
    staff: StaffMember,
    date: LocalDate,
    shiftType: ShiftType,
    hours: Double,
    reliefType: PermissionReliefType,
    reason: String,
    editingPermissionId: String = ""
  ) {
    if (!currentAccess().canGrantPermissions) {
      _actionStatus.value = "Edit or Full Control on Permissions is required to grant duty relief."
      return
    }
    if (_userRole.value == UserRole.SHIFT_INCHARGE) {
      val currentStaffList = (staffState.value as? UiState.Success)?.data ?: emptyList()
      val currentAssignments = (assignmentsState.value as? UiState.Success)?.data ?: emptyList()
      val currentLeaves = (leavesState.value as? UiState.Success)?.data ?: emptyList()
      val liveSnapshot = com.example.domain.SupervisorAnalyticsEngine.resolveLiveShiftSnapshot(
        now = java.time.LocalDateTime.now(),
        staffList = currentStaffList,
        assignments = currentAssignments,
        leaves = currentLeaves
      )
      val currentShiftStaffIds = liveSnapshot.currentShiftStaffMembers.mapTo(HashSet()) { it.id }
      if (!currentShiftStaffIds.contains(staff.id)) {
        _actionStatus.value = "ShiftIncharge can grant relief permission only to staff currently on duty (${liveSnapshot.activeShift.code})."
        return
      }
    }
    if (hours <= 0.0 || hours > 12.0) {
      _actionStatus.value = "Permission hours must be between 0.5 and 12 hours."
      return
    }
    val roundedHours = kotlin.math.round(hours * 2.0) / 2.0
    val isEditing = editingPermissionId.isNotBlank()
    val record = DutyPermissionRecord(
      id = editingPermissionId,
      userId = depotId,
      staffId = staff.id,
      staffName = staff.name,
      date = date.toString(),
      shiftType = shiftType.name,
      hours = roundedHours,
      reliefType = reliefType.name,
      reason = reason.trim().take(300),
      grantedByUid = _actorUid.value,
      grantedByName = _actorName.value.ifBlank { _userRole.value.label }
    )
    viewModelScope.launch {
      _isBusy.value = true
      try {
        withContext(NonCancellable) {
          repository.saveDutyPermission(record, existingDocPresent = isEditing)
        }
        val hrsText = com.example.domain.SupervisorAnalyticsEngine.formatHours(roundedHours)
        _actionStatus.value = if (isEditing) {
          "Updated duty permission for ${staff.name} ($hrsText · ${reliefType.label})."
        } else {
          "Granted $hrsText ${reliefType.label.lowercase()} permission to ${staff.name} on $date."
        }
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        _actionStatus.value = "Failed to save duty permission: ${e.localizedMessage ?: "Error"}"
      } finally {
        _isBusy.value = false
      }
    }
  }

  fun deleteDutyPermission(permissionId: String) {
    if (!currentAccess().canDeletePermissions) {
      _actionStatus.value = "Full Control on Permissions is required to remove duty permissions."
      return
    }
    viewModelScope.launch {
      _isBusy.value = true
      try {
        withContext(NonCancellable) {
          repository.deleteDutyPermission(permissionId)
        }
        _actionStatus.value = "Duty permission record removed."
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        _actionStatus.value = "Failed to remove duty permission: ${e.localizedMessage ?: "Error"}"
      } finally {
        _isBusy.value = false
      }
    }
  }

  fun addImportantContact(label: String, phoneNumber: String, category: String) {
    if (!currentAccess().isAdmin) {
      _actionStatus.value = "Only Admin can add or remove Important Phone Numbers."
      return
    }
    val cleanLabel = label.trim().uppercase()
    val cleanPhone = phoneNumber.trim()
    val digitsOnly = cleanPhone.filter { it.isDigit() }
    if (cleanLabel.isBlank()) {
      _actionStatus.value = "Contact name cannot be blank."
      return
    }
    if (digitsOnly.length < 3 || cleanPhone.length > 20) {
      _actionStatus.value = "Enter a valid phone number (3–20 characters)."
      return
    }
    val cleanCategory = category.trim().ifBlank { "Control" }.take(40)
    val nextOrder = (importantContactsState.value.maxOfOrNull { it.sortOrder } ?: 0L) + 1L

    viewModelScope.launch {
      _isBusy.value = true
      try {
        withContext(NonCancellable) {
          repository.addImportantContact(
            label = cleanLabel,
            phoneNumber = cleanPhone,
            category = cleanCategory,
            sortOrder = nextOrder,
            updatedByUid = _actorUid.value
          )
        }
        _actionStatus.value = "Added $cleanLabel to Important Phone Numbers."
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        _actionStatus.value = "Failed to add contact: ${e.localizedMessage ?: "Error"}"
      } finally {
        _isBusy.value = false
      }
    }
  }

  fun deleteImportantContact(contact: ImportantContact) {
    if (!currentAccess().isAdmin) {
      _actionStatus.value = "Only Admin can add or remove Important Phone Numbers."
      return
    }
    viewModelScope.launch {
      _isBusy.value = true
      try {
        withContext(NonCancellable) {
          repository.deleteImportantContact(
            contactId = contact.id,
            updatedByUid = _actorUid.value
          )
        }
        _actionStatus.value = "Removed ${contact.label} from Important Phone Numbers."
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        _actionStatus.value = "Failed to remove contact: ${e.localizedMessage ?: "Error"}"
      } finally {
        _isBusy.value = false
      }
    }
  }

  fun saveRakeFormation(rake: RakeFormation, isEdit: Boolean) {
    if (!currentAccess().canEditRakes) {
      _actionStatus.value = "Edit or Full Control on Rake Manager is required to modify rake formations."
      return
    }
    val cleanName = rake.rakeName.trim().take(80)
    if (cleanName.isBlank()) {
      _actionStatus.value = "Rake name cannot be blank."
      return
    }
    val cleanedCoaches = RakeFormationEngine.reindexCoaches(
      rake.sortedCoaches.map { c ->
        c.copy(
          coachNumber = c.coachNumber.trim().take(24),
          altNumber = c.altNumber.trim().take(24),
          endTag = c.endTag.trim().take(16)
        )
      }
    )
    if (cleanedCoaches.isEmpty()) {
      _actionStatus.value = "Add at least one coach to the rake formation."
      return
    }
    if (cleanedCoaches.any { it.coachNumber.isBlank() }) {
      _actionStatus.value = "Every coach in the formation must have a coach number."
      return
    }

    val existingRakes = rakesState.value
    val nextSortOrder = if (isEdit && rake.sortOrder > 0L) {
      rake.sortOrder
    } else {
      val categoryBase = (rake.categoryEnum.ordinal + 1) * 100L
      val maxInCategory = existingRakes
        .filter { it.categoryEnum == rake.categoryEnum }
        .maxOfOrNull { it.sortOrder } ?: categoryBase
      (maxInCategory + 1L).coerceIn(1L, 999999L)
    }

    val normalized = rake.copy(
      rakeName = cleanName,
      make = rake.make.trim().take(30),
      startEnd = rake.startEnd.trim().take(20),
      finishEnd = rake.finishEnd.trim().take(20),
      remarks = rake.remarks.trim().take(300),
      sortOrder = nextSortOrder,
      coaches = cleanedCoaches
    )

    viewModelScope.launch {
      _isBusy.value = true
      try {
        withContext(NonCancellable) {
          repository.saveRakeFormation(
            rake = normalized,
            existingDocPresent = isEdit && normalized.id.isNotBlank(),
            updatedByUid = _actorUid.value,
            updatedByName = _actorName.value
          )
        }
        _actionStatus.value = if (isEdit) {
          "Updated $cleanName (${cleanedCoaches.size}-Car)."
        } else {
          "Created $cleanName (${cleanedCoaches.size}-Car)."
        }
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        _actionStatus.value = "Failed to save rake: ${e.localizedMessage ?: "Error"}"
      } finally {
        _isBusy.value = false
      }
    }
  }

  fun reverseRakeFormation(rake: RakeFormation) {
    if (!currentAccess().canEditRakes) {
      _actionStatus.value = "Edit or Full Control on Rake Manager is required to reverse rake formations."
      return
    }
    val reversed = RakeFormationEngine.reverseFormation(rake)
    saveRakeFormation(reversed, isEdit = true)
  }

  fun deleteRakeFormation(rake: RakeFormation) {
    if (!currentAccess().canDeleteRakes) {
      _actionStatus.value = "Full Control on Rake Manager is required to delete rake formations."
      return
    }
    if (rake.id.isBlank()) return
    viewModelScope.launch {
      _isBusy.value = true
      try {
        withContext(NonCancellable) {
          repository.deleteRakeFormation(
            rakeId = rake.id,
            updatedByUid = _actorUid.value,
            updatedByName = _actorName.value
          )
        }
        _actionStatus.value = "Deleted ${rake.rakeName}."
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        _actionStatus.value = "Failed to delete rake: ${e.localizedMessage ?: "Error"}"
      } finally {
        _isBusy.value = false
      }
    }
  }

  fun buildAiContextSummary(): String {
    val date = _selectedDate.value
    val prevDate = date.minusDays(1)
    val today = LocalDate.now()
    val dateStr = date.toString()
    val prevDateStr = prevDate.toString()
    val staff = (staffState.value as? UiState.Success)?.data ?: emptyList()
    val assignments = (assignmentsState.value as? UiState.Success)?.data ?: emptyList()
    val leaves = (leavesState.value as? UiState.Success)?.data ?: emptyList()
    val rakes = rakesState.value
    val role = _userRole.value
    val actorName = _actorName.value
    val profile = currentUserProfile.value

    val coverage = ShiftSchedulerEngine.computeDutyDayCoverage(date, assignments)
    val onLeaveToday = leaves.filter { it.isBlockingDate(dateStr) }
      .distinctBy { it.staffId }
      .map { "${it.staffName} (${it.leaveType})" }
    val onRestToday = staff.filter { it.restDayEnum.dayOfWeek == date.dayOfWeek }.map { it.name }
    val dutyAssignments = assignments.filter {
      val sType = ShiftType.fromRaw(it.shiftType)
      it.date == sType.startDateForDutyDate(date).toString()
    }
    val pendingLeaves = leaves.count { it.statusEnum == LeaveStatus.PENDING }
    val pendingDrafts = dutyDraftsState.value.count { it.statusEnum == DutyDraftStatus.PENDING }
    val pendingBalances = leaveAccountsState.value.count { it.pendingLapStatusEnum == LapUpdateStatus.PENDING }
    val trainingDueCount = staff.count { it.hasTrainingDue }

    return buildString {
      appendLine("Signed-in User: $actorName (Role: ${role.label})")
      appendLine("Selected Duty Date: $dateStr (${date.dayOfWeek}) — Cycle: $prevDateStr 22/06 + $dateStr 06/14, 14/22, 08/17")
      appendLine("Total Staff: ${staff.size} (${staff.count { !it.isSupervisory }} duty staff, ${staff.count { it.isPointsman }} Pointsmen, $trainingDueCount with Training Due date)")
      appendLine("Depot Rake Fleet: ${rakes.size} rakes (${rakes.count { it.tcasFitted }} TCAS fitted)")
      appendLine("Pending Approvals: Leaves=$pendingLeaves, DutyDrafts=$pendingDrafts, BalanceUpdates=$pendingBalances")
      appendLine("Shift Coverage:")
      coverage.forEach { c ->
        if (c.shiftType.isGeneralShift) {
          appendLine("- ${c.shiftType.code} General Shift: ${c.shedFilled}/0-3")
        } else {
          val stLabel = c.shiftType.stationArea?.label ?: "Station"
          appendLine("- ${c.shiftType.code}: PM ${c.pointsmanFilled}/1, Shed ${c.shedFilled}/3-5, $stLabel ${c.stationFilled}/2" +
            if (c.medActive) ", MED ${c.medFilled}/1" else "")
        }
      }
      appendLine("On Leave (Duty Cycle): ${if (onLeaveToday.isEmpty()) "None" else onLeaveToday.joinToString(", ")}")
      appendLine("Weekly Rest Today: ${if (onRestToday.isEmpty()) "None" else onRestToday.joinToString(", ")}")
      appendLine("Assigned Slots in Duty Cycle (${dutyAssignments.size}): " +
        dutyAssignments.joinToString("; ") { "${it.date}/${it.shiftType}/${it.dutyArea}:${it.staffName}${if (it.isEmergencyOverride) "[OVR]" else ""}" })

      val linkedId = profile?.staffId?.trim().orEmpty()
      if (role == UserRole.TECHNICIAN && linkedId.isNotBlank()) {
        val linkedStaff = staff.firstOrNull { it.id == linkedId }
        if (linkedStaff != null) {
          val account = leaveAccountsState.value.firstOrNull { it.staffId == linkedId || it.id == linkedId }
          val balance = LeaveBalanceEngine.computeBalanceSummary(
            staff = linkedStaff,
            year = today.year,
            account = account,
            allLeaves = leaves,
            allAssignments = assignments
          )
          val upcoming = LeaveBalanceEngine.buildUpcomingDutiesForStaff(
            staff = linkedStaff,
            baseToday = today,
            allAssignments = assignments,
            allLeaves = leaves
          )
          appendLine("Personal Staff Profile: #${linkedStaff.serialNo} ${linkedStaff.name} (${linkedStaff.designationEnum.label}, Rest=${linkedStaff.restDayEnum.label})")
          appendLine("Personal Leave Balances (${today.year}): CL Remaining=${balance.clRemaining}/${balance.clEntitlement}, LAP Balance=${balance.lapRemaining}, LHAP Balance=${balance.lhapRemaining}, Special CR=${balance.specialRemaining}")
          appendLine("Personal Tomorrow's Duties (${upcoming.tomorrowDutyDate}): " +
            if (upcoming.tomorrowDuties.isEmpty()) "None assigned"
            else upcoming.tomorrowDuties.joinToString("; ") { "${it.shiftType.code} ${it.dutyArea.label} (${it.assignment.date})" })
        }
      }
    }
  }

  private fun unwrapFirestoreError(error: Throwable): Throwable {
    val firestoreError = generateSequence(error) { it.cause }
      .filterIsInstance<FirebaseFirestoreException>()
      .firstOrNull()
    if (firestoreError == null && error is CancellationException) throw error
    return firestoreError ?: error
  }

  private companion object {
    const val TAG = "RosterViewModel"
  }
}
