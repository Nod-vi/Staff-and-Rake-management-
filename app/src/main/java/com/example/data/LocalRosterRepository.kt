package com.example.data

import android.content.Context
import android.util.Log
import com.example.auth.DepotRoleManager
import com.example.model.AppReleaseConfig
import com.example.model.DepotSettings
import com.example.model.DepotUserProfile
import com.example.model.DraftSlotEntry
import com.example.model.DutyApprovalRequest
import com.example.model.DutyDateLock
import com.example.model.DutyDraftStatus
import com.example.model.DutyPermissionRecord
import com.example.model.EmergencyOverrideLog
import com.example.model.IMPORTANT_DEPOT_CONTACTS
import com.example.model.ImportantContact
import com.example.model.LapUpdateStatus
import com.example.domain.RakeFormationEngine
import com.example.model.LeaveRecord
import com.example.model.LeaveStatus
import com.example.model.LeaveType
import com.example.model.RakeCoach
import com.example.model.RakeFormation
import com.example.model.RoleAccessPolicy
import com.example.model.ShiftSlotAssignment
import com.example.model.StaffLeaveAccount
import com.example.model.StaffMember
import com.example.model.UserRole
import com.example.model.WeeklyRestDay
import com.google.firebase.Timestamp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.UUID

data class PendingLocalSyncSnapshot(
  val dirtyStaff: List<StaffMember>,
  val deletedStaffIds: Set<String>,
  val dirtyAssignments: List<ShiftSlotAssignment>,
  val deletedAssignmentIds: Set<String>,
  val dirtyLeaves: List<LeaveRecord>,
  val deletedLeaveIds: Set<String>
) {
  val hasChanges: Boolean
    get() = dirtyStaff.isNotEmpty() ||
      deletedStaffIds.isNotEmpty() ||
      dirtyAssignments.isNotEmpty() ||
      deletedAssignmentIds.isNotEmpty() ||
      dirtyLeaves.isNotEmpty() ||
      deletedLeaveIds.isNotEmpty()
}

class LocalRosterRepository(
  context: Context,
  storageFileName: String = DEFAULT_STORAGE_FILE_NAME,
  private val isTestSandbox: Boolean = false
) : RosterRepository {

  private val storeFile = File(context.applicationContext.filesDir, storageFileName)
  private val mutex = Mutex()
  private val credentialsByUid = mutableMapOf<String, String>()

  private val dirtyStaffIds = mutableSetOf<String>()
  private val deletedStaffIds = mutableSetOf<String>()
  private val dirtyAssignmentIds = mutableSetOf<String>()
  private val deletedAssignmentIds = mutableSetOf<String>()
  private val dirtyLeaveIds = mutableSetOf<String>()
  private val deletedLeaveIds = mutableSetOf<String>()

  private val _staff = MutableStateFlow<List<StaffMember>>(emptyList())
  private val _assignments = MutableStateFlow<List<ShiftSlotAssignment>>(emptyList())
  private val _leaves = MutableStateFlow<List<LeaveRecord>>(emptyList())
  private val _overrides = MutableStateFlow<List<EmergencyOverrideLog>>(emptyList())
  private val _dutyDrafts = MutableStateFlow<List<DutyApprovalRequest>>(emptyList())
  private val _dutyLocks = MutableStateFlow<List<DutyDateLock>>(emptyList())
  private val _userProfiles = MutableStateFlow(
    if (isTestSandbox) defaultOfflineTestUserProfiles() else defaultLocalUserProfiles()
  )
  private val _depotSettings = MutableStateFlow(DepotSettings())
  private val _appRelease = MutableStateFlow(AppReleaseConfig())
  private val _leaveAccounts = MutableStateFlow<List<StaffLeaveAccount>>(emptyList())
  private val _dutyPermissions = MutableStateFlow<List<DutyPermissionRecord>>(emptyList())
  private val _importantContacts = MutableStateFlow<List<ImportantContact>>(IMPORTANT_DEPOT_CONTACTS)
  private val _rakes = MutableStateFlow<List<RakeFormation>>(DefaultRakeFormations.all)

  init {
    loadFromDisk()
    ensureDefaultAdminCredentialSeeded()
  }

  override fun observeStaff(depotId: String): Flow<List<StaffMember>> = _staff.asStateFlow()

  override fun observeAssignments(depotId: String): Flow<List<ShiftSlotAssignment>> = _assignments.asStateFlow()

  override fun observeLeaves(depotId: String): Flow<List<LeaveRecord>> = _leaves.asStateFlow()

  override fun observeOverrides(depotId: String): Flow<List<EmergencyOverrideLog>> = _overrides.asStateFlow()

  override fun observeDutyDrafts(depotId: String): Flow<List<DutyApprovalRequest>> = _dutyDrafts.asStateFlow()

  override fun observeDutyLocks(depotId: String): Flow<List<DutyDateLock>> = _dutyLocks.asStateFlow()

  override fun observeUserProfile(uid: String): Flow<DepotUserProfile?> =
    _userProfiles.map { list -> list.firstOrNull { it.uid == uid } }

  override fun observeAllUserProfiles(): Flow<List<DepotUserProfile>> = _userProfiles.asStateFlow()

  override fun observeDepotSettings(depotId: String): Flow<DepotSettings> = _depotSettings.asStateFlow()

  override suspend fun updateAllowTechnicianShare(allowed: Boolean, updatedByUid: String) {
    mutex.withLock {
      _depotSettings.value = _depotSettings.value.copy(
        allowTechnicianShare = allowed,
        updatedByUid = updatedByUid.trim().take(128),
        updatedAt = Timestamp.now()
      )
      persistToDisk()
    }
  }

  override suspend fun updateShunterSettings(
    showForTechnicians: Boolean,
    includeInExport: Boolean,
    updatedByUid: String
  ) {
    mutex.withLock {
      _depotSettings.value = _depotSettings.value.copy(
        showShunterForTechnicians = showForTechnicians,
        includeShunterInExport = includeInExport,
        updatedByUid = updatedByUid.trim().take(128),
        updatedAt = Timestamp.now()
      )
      persistToDisk()
    }
  }

  override suspend fun updateRoleMenuAccess(
    roleMenuAccess: Map<String, Map<String, String>>,
    updatedByUid: String
  ) {
    mutex.withLock {
      _depotSettings.value = _depotSettings.value.copy(
        roleMenuAccess = RoleAccessPolicy.sanitizeRoleAccessMap(roleMenuAccess),
        updatedByUid = updatedByUid.trim().take(128),
        updatedAt = Timestamp.now()
      )
      persistToDisk()
    }
  }

  override fun observeAppRelease(depotId: String): Flow<AppReleaseConfig> = _appRelease.asStateFlow()

  override suspend fun publishAppRelease(release: AppReleaseConfig) {
    mutex.withLock {
      val now = Timestamp.now()
      val existingCreatedAt = _appRelease.value.createdAt ?: now
      _appRelease.value = release.copy(
        id = AppReleaseConfig.LATEST_DOC_ID,
        latestVersionCode = release.latestVersionCode.coerceIn(0, 999_999),
        latestVersionName = release.latestVersionName.trim().take(40),
        minSupportedVersionCode = release.minSupportedVersionCode.coerceIn(0, release.latestVersionCode.coerceAtLeast(0)),
        downloadUrl = release.downloadUrl.trim().take(500),
        releaseNotes = release.releaseNotes.trim().take(1500),
        sha256Checksum = release.sha256Checksum.trim().take(64),
        publishedByUid = release.publishedByUid.trim().take(128).ifBlank { "admin" },
        publishedByName = release.publishedByName.trim().take(100).ifBlank { DepotRoleManager.ADMIN_DISPLAY_NAME },
        createdAt = existingCreatedAt,
        updatedAt = now
      )
      persistToDisk()
    }
  }

  override suspend fun clearAppRelease(updatedByUid: String, updatedByName: String) {
    mutex.withLock {
      val now = Timestamp.now()
      _appRelease.value = AppReleaseConfig(
        id = AppReleaseConfig.LATEST_DOC_ID,
        latestVersionCode = 0,
        latestVersionName = "",
        minSupportedVersionCode = 0,
        mandatory = false,
        downloadUrl = "",
        releaseNotes = "",
        sha256Checksum = "",
        publishedByUid = updatedByUid.trim().take(128).ifBlank { "admin" },
        publishedByName = updatedByName.trim().take(100).ifBlank { DepotRoleManager.ADMIN_DISPLAY_NAME },
        createdAt = _appRelease.value.createdAt ?: now,
        updatedAt = now
      )
      persistToDisk()
    }
  }

  override fun observeLeaveAccounts(depotId: String): Flow<List<StaffLeaveAccount>> = _leaveAccounts.asStateFlow()

  override suspend fun saveLeaveAccount(
    account: StaffLeaveAccount,
    isTechnicianCorrectionRequest: Boolean
  ) {
    mutex.withLock {
      val staffId = account.staffId.ifBlank { account.id }.trim().take(128)
      require(staffId.isNotBlank()) { "Staff ID is required." }
      val now = Timestamp.now()
      val existing = _leaveAccounts.value.firstOrNull { it.staffId == staffId || it.id == staffId }
      val updated = if (isTechnicianCorrectionRequest && existing != null) {
        existing.copy(
          staffName = account.staffName.ifBlank { existing.staffName },
          pendingLapBalance = account.pendingLapBalance.coerceIn(0, 999),
          pendingLhapBalance = account.pendingLhapBalance.coerceIn(0, 999),
          pendingLapYear = account.pendingLapYear,
          pendingLapReason = account.pendingLapReason.trim().take(300),
          pendingLapStatus = account.pendingLapStatusEnum.name,
          pendingLapRequestedByUid = account.pendingLapRequestedByUid.trim().take(128),
          updatedAt = now
        )
      } else {
        account.copy(
          id = staffId,
          staffId = staffId,
          staffName = account.staffName.trim().take(100),
          lapOpeningBalance = account.lapOpeningBalance.coerceIn(0, 999),
          lhapOpeningBalance = account.lhapOpeningBalance.coerceIn(0, 999),
          pendingLapBalance = account.pendingLapBalance.coerceIn(0, 999),
          pendingLhapBalance = account.pendingLhapBalance.coerceIn(0, 999),
          pendingLapReason = account.pendingLapReason.trim().take(300),
          pendingLapStatus = account.pendingLapStatusEnum.name,
          pendingLapRequestedByUid = account.pendingLapRequestedByUid.trim().take(128),
          pendingLapReviewedByName = account.pendingLapReviewedByName.trim().take(100),
          createdAt = existing?.createdAt ?: now,
          updatedAt = now
        )
      }
      _leaveAccounts.value = _leaveAccounts.value.filterNot { it.staffId == staffId || it.id == staffId } + updated
      persistToDisk()
    }
  }

  override fun observeDutyPermissions(depotId: String): Flow<List<DutyPermissionRecord>> =
    _dutyPermissions.asStateFlow()

  override suspend fun saveDutyPermission(
    record: DutyPermissionRecord,
    existingDocPresent: Boolean
  ): String = mutex.withLock {
    val id = record.id.takeIf { it.isNotBlank() }
      ?: "perm_${UUID.randomUUID().toString().replace("-", "").take(16)}"
    val now = Timestamp.now()
    val existing = _dutyPermissions.value.firstOrNull { it.id == id }
    val saved = record.copy(
      id = id,
      userId = LOCAL_USER_ID,
      staffId = record.staffId.trim().take(128),
      staffName = record.staffName.trim().take(100),
      shiftType = record.shiftTypeEnum.name,
      hours = record.hours.coerceIn(0.5, 12.0),
      reliefType = record.reliefTypeEnum.name,
      reason = record.reason.trim().take(300),
      grantedByUid = record.grantedByUid.trim().take(128),
      grantedByName = record.grantedByName.trim().ifBlank { "Supervisor" }.take(100),
      createdAt = existing?.createdAt ?: now,
      updatedAt = now
    )
    _dutyPermissions.value = (listOf(saved) + _dutyPermissions.value.filterNot { it.id == id })
      .sortedWith(
        compareByDescending<DutyPermissionRecord> { it.date }
          .thenByDescending { it.createdAt?.seconds ?: 0L }
      )
    persistToDisk()
    id
  }

  override suspend fun deleteDutyPermission(permissionId: String) {
    mutex.withLock {
      _dutyPermissions.value = _dutyPermissions.value.filterNot { it.id == permissionId }
      persistToDisk()
    }
  }

  override fun observeImportantContacts(depotId: String): Flow<List<ImportantContact>> =
    _importantContacts.asStateFlow()

  override suspend fun seedDefaultImportantContactsIfNeeded(updatedByUid: String): Boolean = mutex.withLock {
    if (_depotSettings.value.importantContactsInitialized) return@withLock false
    val didSeed = _importantContacts.value.isEmpty()
    if (didSeed) {
      _importantContacts.value = IMPORTANT_DEPOT_CONTACTS
    }
    _depotSettings.value = _depotSettings.value.copy(
      importantContactsInitialized = true,
      updatedByUid = updatedByUid.trim().take(128).ifBlank { DepotRoleManager.ADMIN_ACCOUNT_ID },
      updatedAt = Timestamp.now()
    )
    persistToDisk()
    didSeed
  }

  override suspend fun addImportantContact(
    label: String,
    phoneNumber: String,
    category: String,
    sortOrder: Long,
    updatedByUid: String
  ): String = mutex.withLock {
    val now = Timestamp.now()
    val id = "contact_${UUID.randomUUID().toString().replace("-", "").take(16)}"
    val created = ImportantContact(
      id = id,
      userId = LOCAL_USER_ID,
      label = label.trim().uppercase().take(80),
      phoneNumber = phoneNumber.trim().take(20),
      category = category.trim().ifBlank { "Control" }.take(40),
      sortOrder = sortOrder.coerceIn(0L, 999999L),
      createdAt = now,
      updatedAt = now
    )
    _importantContacts.value = (_importantContacts.value + created)
      .sortedWith(compareBy<ImportantContact>({ it.sortOrder }, { it.label.uppercase() }))
    _depotSettings.value = _depotSettings.value.copy(
      importantContactsInitialized = true,
      updatedByUid = updatedByUid.trim().take(128).ifBlank { DepotRoleManager.ADMIN_ACCOUNT_ID },
      updatedAt = now
    )
    persistToDisk()
    id
  }

  override suspend fun deleteImportantContact(contactId: String, updatedByUid: String) {
    mutex.withLock {
      _importantContacts.value = _importantContacts.value.filterNot { it.id == contactId }
      _depotSettings.value = _depotSettings.value.copy(
        importantContactsInitialized = true,
        updatedByUid = updatedByUid.trim().take(128).ifBlank { DepotRoleManager.ADMIN_ACCOUNT_ID },
        updatedAt = Timestamp.now()
      )
      persistToDisk()
    }
  }

  override fun observeRakes(depotId: String): Flow<List<RakeFormation>> = _rakes.asStateFlow()

  override suspend fun seedDefaultRakesIfNeeded(
    updatedByUid: String,
    updatedByName: String
  ): Boolean = mutex.withLock {
    val currentSettings = _depotSettings.value
    if (currentSettings.rakesInitialized &&
      currentSettings.rakesSchemaVersion >= RakeFormationEngine.CURRENT_RAKES_SCHEMA_VERSION
    ) {
      return@withLock false
    }
    var changed = false
    if (!currentSettings.rakesInitialized && _rakes.value.isEmpty()) {
      _rakes.value = DefaultRakeFormations.all
      changed = true
    } else if (_rakes.value.isNotEmpty() &&
      currentSettings.rakesSchemaVersion < RakeFormationEngine.CURRENT_RAKES_SCHEMA_VERSION
    ) {
      val upgraded = _rakes.value.map { rake ->
        RakeFormationEngine.migrateLegacyCoachRolesIfNeeded(rake)?.also { changed = true } ?: rake
      }
      if (changed) _rakes.value = upgraded
    }
    _depotSettings.value = currentSettings.copy(
      rakesInitialized = true,
      rakesSchemaVersion = RakeFormationEngine.CURRENT_RAKES_SCHEMA_VERSION,
      updatedByUid = updatedByUid.trim().take(128).ifBlank { DepotRoleManager.ADMIN_ACCOUNT_ID },
      updatedAt = Timestamp.now()
    )
    persistToDisk()
    changed
  }

  override suspend fun saveRakeFormation(
    rake: RakeFormation,
    existingDocPresent: Boolean,
    updatedByUid: String,
    updatedByName: String
  ): String = mutex.withLock {
    val now = Timestamp.now()
    val id = rake.id.trim().takeIf { it.isNotBlank() }
      ?: "rake_${UUID.randomUUID().toString().replace("-", "").take(16)}"
    val existing = _rakes.value.firstOrNull { it.id == id }
    val saved = rake.copy(
      id = id,
      userId = LOCAL_USER_ID,
      rakeName = rake.rakeName.trim().take(80),
      category = rake.categoryEnum.name,
      make = rake.make.trim().take(30),
      startEnd = rake.startEnd.trim().take(20),
      finishEnd = rake.finishEnd.trim().take(20),
      remarks = rake.remarks.trim().take(300),
      sortOrder = rake.sortOrder.coerceIn(0L, 999999L),
      coaches = RakeFormationEngine.reindexCoaches(rake.sortedCoaches),
      updatedByUid = updatedByUid.trim().take(128).ifBlank { DepotRoleManager.ADMIN_ACCOUNT_ID },
      updatedByName = updatedByName.trim().take(100).ifBlank { "Depot Control" },
      createdAt = existing?.createdAt ?: now,
      updatedAt = now
    )
    _rakes.value = (_rakes.value.filterNot { it.id == id } + saved)
      .sortedWith(compareBy<RakeFormation>({ it.sortOrder }, { it.rakeName.uppercase() }))
    _depotSettings.value = _depotSettings.value.copy(
      rakesInitialized = true,
      updatedByUid = saved.updatedByUid,
      updatedAt = now
    )
    persistToDisk()
    id
  }

  override suspend fun deleteRakeFormation(
    rakeId: String,
    updatedByUid: String,
    updatedByName: String
  ) {
    mutex.withLock {
      _rakes.value = _rakes.value.filterNot { it.id == rakeId }
      _depotSettings.value = _depotSettings.value.copy(
        rakesInitialized = true,
        updatedByUid = updatedByUid.trim().take(128).ifBlank { DepotRoleManager.ADMIN_ACCOUNT_ID },
        updatedAt = Timestamp.now()
      )
      persistToDisk()
    }
  }

  override suspend fun ensureUserProfile(
    uid: String,
    email: String,
    displayName: String
  ): DepotUserProfile = mutex.withLock {
    val existing = _userProfiles.value.firstOrNull { it.uid == uid }
    if (existing != null) {
      val isAdminMatch = DepotRoleManager.isBootstrapAdminEmail(email.ifBlank { existing.email }, displayName.ifBlank { existing.displayName })
      if (isAdminMatch && existing.roleEnum != UserRole.ADMIN) {
        val upgraded = existing.copy(role = UserRole.ADMIN.name, updatedAt = Timestamp.now())
        _userProfiles.value = _userProfiles.value.map { if (it.uid == uid) upgraded else it }
        persistToDisk()
        return@withLock upgraded
      }
      return@withLock existing
    }

    val role = when {
      uid == DepotRoleManager.OFFLINE_TEST_ADMIN_UID -> UserRole.ADMIN
      !isTestSandbox && DepotRoleManager.isBootstrapAdminEmail(email, displayName) -> UserRole.ADMIN
      else -> DepotRoleManager.roleAccounts.firstOrNull { it.id == uid }?.role ?: UserRole.TECHNICIAN
    }
    val created = DepotUserProfile(
      id = uid,
      uid = uid,
      username = if (uid == DepotRoleManager.OFFLINE_TEST_ADMIN_UID) DepotRoleManager.OFFLINE_TEST_ADMIN_USERNAME else "",
      email = if (isTestSandbox) "" else email.trim(),
      displayName = displayName.trim().ifBlank { "Depot User" },
      role = role.name,
      staffId = "",
      updatedByUid = uid,
      createdAt = Timestamp.now(),
      updatedAt = Timestamp.now()
    )
    _userProfiles.value = (_userProfiles.value + created).sortedBy { it.displayName.uppercase() }
    persistToDisk()
    created
  }

  override suspend fun updateUserRole(
    targetUid: String,
    role: UserRole,
    staffId: String,
    updatedByUid: String
  ) {
    mutex.withLock {
      _userProfiles.value = _userProfiles.value.map { profile ->
        if (profile.uid == targetUid) {
          val enforcedRole = if (isPrimaryAdminProfile(profile)) UserRole.ADMIN else role
          profile.copy(
            role = enforcedRole.name,
            staffId = staffId,
            updatedByUid = updatedByUid,
            updatedAt = Timestamp.now()
          )
        } else {
          profile
        }
      }
      persistToDisk()
    }
  }

  override suspend fun authenticateLocalUser(username: String, password: String): DepotUserProfile? =
    mutex.withLock {
      val cleanUsername = username.trim()
      if (cleanUsername.isEmpty() || password.isEmpty()) return@withLock null
      val profile = _userProfiles.value.firstOrNull { candidate ->
        (candidate.username.isNotBlank() && candidate.username.equals(cleanUsername, ignoreCase = true)) ||
          (candidate.email.isNotBlank() && candidate.email.equals(cleanUsername, ignoreCase = true))
      } ?: return@withLock null

      val storedHash = credentialsByUid[profile.uid] ?: return@withLock null
      if (!verifyPassword(password, storedHash)) return@withLock null
      profile.copy(hasPassword = true)
    }

  override suspend fun createUserAccount(
    username: String,
    password: String,
    displayName: String,
    email: String,
    role: UserRole,
    staffId: String,
    createdByUid: String
  ): DepotUserProfile = mutex.withLock {
    val cleanUsername = username.trim().lowercase().take(64)
    val cleanName = displayName.trim().take(100)
    val cleanEmail = email.trim().take(200)
    require(cleanUsername.length >= 2 && !cleanUsername.contains(" ")) {
      "Username must be at least 2 characters with no spaces."
    }
    require(password.length >= 4) { "Password must be at least 4 characters." }
    require(cleanName.isNotBlank()) { "Full name is required." }

    val duplicateUsername = _userProfiles.value.any {
      it.username.equals(cleanUsername, ignoreCase = true)
    }
    require(!duplicateUsername) { "Username '$cleanUsername' is already in use." }

    val uid = "user_${UUID.randomUUID().toString().replace("-", "").take(12)}"
    val effectiveRole = if (DepotRoleManager.isBootstrapAdminEmail(cleanEmail, cleanName)) {
      UserRole.ADMIN
    } else {
      role
    }
    credentialsByUid[uid] = hashPassword(password)
    val now = Timestamp.now()
    val created = DepotUserProfile(
      id = uid,
      uid = uid,
      username = cleanUsername,
      email = cleanEmail,
      displayName = cleanName,
      role = effectiveRole.name,
      staffId = staffId.trim().take(128),
      updatedByUid = createdByUid,
      hasPassword = true,
      createdAt = now,
      updatedAt = now
    )
    _userProfiles.value = (_userProfiles.value + created).sortedBy { it.displayName.uppercase() }
    persistToDisk()
    created
  }

  override suspend fun updateUserAccount(
    targetUid: String,
    username: String,
    displayName: String,
    email: String,
    role: UserRole,
    staffId: String,
    newPassword: String?,
    updatedByUid: String
  ) {
    mutex.withLock {
      val existing = _userProfiles.value.firstOrNull { it.uid == targetUid }
        ?: throw IllegalArgumentException("User account not found.")
      val cleanUsername = username.trim().lowercase().take(64)
      val cleanName = displayName.trim().take(100)
      val cleanEmail = email.trim().take(200)
      require(cleanUsername.length >= 2 && !cleanUsername.contains(" ")) {
        "Username must be at least 2 characters with no spaces."
      }
      require(cleanName.isNotBlank()) { "Full name is required." }

      val duplicateUsername = _userProfiles.value.any {
        it.uid != targetUid && it.username.equals(cleanUsername, ignoreCase = true)
      }
      require(!duplicateUsername) { "Username '$cleanUsername' is already assigned to another user." }

      if (!newPassword.isNullOrBlank()) {
        require(newPassword.length >= 4) { "New password must be at least 4 characters." }
        credentialsByUid[targetUid] = hashPassword(newPassword)
      }

      val isPrimary = isPrimaryAdminProfile(existing)
      val effectiveRole = if (isPrimary) UserRole.ADMIN else role

      _userProfiles.value = _userProfiles.value.map { profile ->
        if (profile.uid == targetUid) {
          profile.copy(
            username = cleanUsername,
            displayName = cleanName,
            email = cleanEmail,
            role = effectiveRole.name,
            staffId = staffId.trim().take(128),
            hasPassword = credentialsByUid.containsKey(targetUid),
            updatedByUid = updatedByUid,
            updatedAt = Timestamp.now()
          )
        } else {
          profile
        }
      }.sortedBy { it.displayName.uppercase() }
      persistToDisk()
    }
  }

  override suspend fun deleteUserAccount(targetUid: String) {
    mutex.withLock {
      val existing = _userProfiles.value.firstOrNull { it.uid == targetUid } ?: return@withLock
      require(!isPrimaryAdminProfile(existing)) {
        "Primary Admin account cannot be deleted."
      }
      _userProfiles.value = _userProfiles.value.filterNot { it.uid == targetUid }
      credentialsByUid.remove(targetUid)
      persistToDisk()
    }
  }

  override suspend fun changeUserPassword(
    targetUid: String,
    currentPassword: String,
    newPassword: String
  ): Boolean = mutex.withLock {
    val cleanNew = newPassword
    if (cleanNew.length < 4) return@withLock false
    val existingHash = credentialsByUid[targetUid]
    if (existingHash != null && !verifyPassword(currentPassword, existingHash)) {
      return@withLock false
    }
    credentialsByUid[targetUid] = hashPassword(cleanNew)
    _userProfiles.value = _userProfiles.value.map { profile ->
      if (profile.uid == targetUid) {
        profile.copy(hasPassword = true, updatedAt = Timestamp.now())
      } else {
        profile
      }
    }
    persistToDisk()
    true
  }

  override suspend fun seedDefaultRosterIfEmpty(): Boolean = mutex.withLock {
    if (_staff.value.isNotEmpty()) return false
    _staff.value = DefaultPpioRoster.initialStaff(LOCAL_USER_ID).sortedBy { it.serialNo }
    if (_userProfiles.value.isEmpty()) {
      _userProfiles.value = if (isTestSandbox) {
        defaultOfflineTestUserProfiles()
      } else {
        defaultLocalUserProfiles()
      }
    }
    persistToDisk()
    true
  }

  override suspend fun addStaffMember(member: StaffMember): String = mutex.withLock {
    val id = member.id.takeIf { it.isNotBlank() }
      ?: "staff_${UUID.randomUUID().toString().replace("-", "").take(12)}"
    val created = member.copy(
      id = id,
      userId = LOCAL_USER_ID,
      serialNo = member.serialNo.coerceIn(1L, 9999L),
      name = member.name.trim().take(100),
      trainingDueDate = member.trainingDueDate.trim().take(10),
      trainingCourse = member.trainingCourse.trim().take(100),
      phoneNumber = member.phoneNumber.trim().take(20),
      secondaryPhoneNumber = member.secondaryPhoneNumber.trim().take(20)
    )
    _staff.value = (_staff.value + created).sortedBy { it.serialNo }
    dirtyStaffIds.add(id)
    deletedStaffIds.remove(id)
    persistToDisk()
    id
  }

  override suspend fun updateStaffMember(member: StaffMember) {
    mutex.withLock {
      _staff.value = _staff.value.map { existing ->
        if (existing.id == member.id) {
          member.copy(
            userId = LOCAL_USER_ID,
            serialNo = member.serialNo.coerceIn(1L, 9999L),
            name = member.name.trim().take(100),
            trainingDueDate = member.trainingDueDate.trim().take(10),
            trainingCourse = member.trainingCourse.trim().take(100),
            phoneNumber = member.phoneNumber.trim().take(20),
            secondaryPhoneNumber = member.secondaryPhoneNumber.trim().take(20)
          )
        } else {
          existing
        }
      }.sortedBy { it.serialNo }
      dirtyStaffIds.add(member.id)
      deletedStaffIds.remove(member.id)
      persistToDisk()
    }
  }

  override suspend fun deleteStaffMember(staffId: String) {
    mutex.withLock {
      _staff.value = _staff.value.filterNot { it.id == staffId }
      dirtyStaffIds.remove(staffId)
      deletedStaffIds.add(staffId)
      persistToDisk()
    }
  }

  override suspend fun updateStaffRestDay(staffId: String, restDay: WeeklyRestDay) {
    mutex.withLock {
      _staff.value = _staff.value.map { member ->
        if (member.id == staffId) member.copy(weeklyRestDay = restDay.name) else member
      }
      dirtyStaffIds.add(staffId)
      deletedStaffIds.remove(staffId)
      persistToDisk()
    }
  }

  override suspend fun updateStaffTrainingDue(
    staffId: String,
    trainingDueDate: String,
    trainingCourse: String
  ) {
    mutex.withLock {
      val cleanDate = trainingDueDate.trim().take(10)
      val cleanCourse = if (cleanDate.isBlank()) "" else trainingCourse.trim().take(100)
      _staff.value = _staff.value.map { member ->
        if (member.id == staffId) {
          member.copy(
            trainingDueDate = cleanDate,
            trainingCourse = cleanCourse
          )
        } else {
          member
        }
      }
      dirtyStaffIds.add(staffId)
      deletedStaffIds.remove(staffId)
      persistToDisk()
    }
  }

  override suspend fun batchUpdateRestDays(updates: Map<String, WeeklyRestDay>) {
    mutex.withLock {
      _staff.value = _staff.value.map { member ->
        val next = updates[member.id]
        if (next != null) member.copy(weeklyRestDay = next.name) else member
      }
      dirtyStaffIds.addAll(updates.keys)
      deletedStaffIds.removeAll(updates.keys)
      persistToDisk()
    }
  }

  override suspend fun saveSlotAssignment(assignment: ShiftSlotAssignment, existingDocPresent: Boolean) {
    mutex.withLock {
      val docId = assignment.id.ifBlank {
        "${assignment.date}_${assignment.shiftType}_${assignment.dutyArea}_${assignment.slotIndex}"
      }
      val normalized = assignment.copy(id = docId, userId = LOCAL_USER_ID)
      _assignments.value = _assignments.value.filterNot { it.id == docId } + normalized
      dirtyAssignmentIds.add(docId)
      deletedAssignmentIds.remove(docId)
      persistToDisk()
    }
  }

  override suspend fun batchSaveAssignments(assignments: List<ShiftSlotAssignment>, existingIds: Set<String>) {
    mutex.withLock {
      val incomingById = assignments.associateBy { a ->
        a.id.ifBlank { "${a.date}_${a.shiftType}_${a.dutyArea}_${a.slotIndex}" }
      }
      val remaining = _assignments.value.filterNot { incomingById.containsKey(it.id) }
      val normalizedIncoming = incomingById.map { (id, item) ->
        item.copy(id = id, userId = LOCAL_USER_ID)
      }
      _assignments.value = remaining + normalizedIncoming
      dirtyAssignmentIds.addAll(incomingById.keys)
      deletedAssignmentIds.removeAll(incomingById.keys)
      persistToDisk()
    }
  }

  override suspend fun clearSlotAssignment(assignmentId: String) {
    mutex.withLock {
      _assignments.value = _assignments.value.filterNot { it.id == assignmentId }
      dirtyAssignmentIds.remove(assignmentId)
      deletedAssignmentIds.add(assignmentId)
      persistToDisk()
    }
  }

  override suspend fun batchClearAssignments(assignmentIds: List<String>) {
    if (assignmentIds.isEmpty()) return
    val toRemove = assignmentIds.toSet()
    mutex.withLock {
      _assignments.value = _assignments.value.filterNot { it.id in toRemove }
      dirtyAssignmentIds.removeAll(toRemove)
      deletedAssignmentIds.addAll(toRemove)
      persistToDisk()
    }
  }

  override suspend fun submitDutyDraft(draft: DutyApprovalRequest): String = mutex.withLock {
    val id = draft.id.takeIf { it.isNotBlank() }
      ?: "draft_${UUID.randomUUID().toString().replace("-", "").take(14)}"
    val now = Timestamp.now()
    val normalized = draft.copy(
      id = id,
      depotId = LOCAL_USER_ID,
      status = DutyDraftStatus.PENDING.name,
      updatedAt = now,
      createdAt = draft.createdAt ?: now
    )
    _dutyDrafts.value = (listOf(normalized) + _dutyDrafts.value.filterNot { it.id == id })
      .sortedWith(compareBy({ it.statusEnum != DutyDraftStatus.PENDING }, { it.dutyDate }))
    persistToDisk()
    id
  }

  override suspend fun approveDutyDraft(
    draft: DutyApprovalRequest,
    reviewerUid: String,
    reviewerName: String,
    cycleAssignmentIdsToClear: List<String>,
    existingAssignmentIds: Set<String>
  ) {
    mutex.withLock {
      val removeSet = cycleAssignmentIdsToClear.toSet()
      val remaining = _assignments.value.filterNot { it.id in removeSet }
      val approvedAssignments = draft.proposedSlots.map {
        it.toAssignment(LOCAL_USER_ID, reviewerName)
      }
      val approvedIds = approvedAssignments.mapTo(HashSet()) { it.id }
      _assignments.value = remaining + approvedAssignments
      deletedAssignmentIds.addAll(removeSet - approvedIds)
      dirtyAssignmentIds.removeAll(removeSet - approvedIds)
      dirtyAssignmentIds.addAll(approvedIds)
      deletedAssignmentIds.removeAll(approvedIds)

      val lock = DutyDateLock(
        id = draft.dutyDate,
        dutyDate = draft.dutyDate,
        lockedByUid = reviewerUid,
        lockedByName = reviewerName,
        createdAt = Timestamp.now(),
        updatedAt = Timestamp.now()
      )
      _dutyLocks.value = _dutyLocks.value.filterNot { it.dutyDate == draft.dutyDate } + lock

      _dutyDrafts.value = _dutyDrafts.value.map { existing ->
        if (existing.id == draft.id) {
          existing.copy(
            status = DutyDraftStatus.APPROVED.name,
            reviewedByUid = reviewerUid,
            reviewedByName = reviewerName,
            reviewNotes = "Approved and published to shared depot roster.",
            updatedAt = Timestamp.now()
          )
        } else {
          existing
        }
      }
      persistToDisk()
    }
  }

  override suspend fun rejectDutyDraft(
    draftId: String,
    reviewerUid: String,
    reviewerName: String,
    notes: String
  ) {
    mutex.withLock {
      _dutyDrafts.value = _dutyDrafts.value.map { existing ->
        if (existing.id == draftId) {
          existing.copy(
            status = DutyDraftStatus.REJECTED.name,
            reviewedByUid = reviewerUid,
            reviewedByName = reviewerName,
            reviewNotes = notes.trim().take(300),
            updatedAt = Timestamp.now()
          )
        } else {
          existing
        }
      }
      persistToDisk()
    }
  }

  override suspend fun addLeaveRecord(leave: LeaveRecord): String = mutex.withLock {
    val id = leave.id.takeIf { it.isNotBlank() }
      ?: "leave_${UUID.randomUUID().toString().replace("-", "").take(16)}"
    val created = leave.copy(
      id = id,
      userId = LOCAL_USER_ID,
      status = leave.statusEnum.name,
      notes = leave.notes.trim().take(300),
      createdAt = leave.createdAt ?: Timestamp.now()
    )
    _leaves.value = (listOf(created) + _leaves.value.filterNot { it.id == id })
      .sortedByDescending { it.startDate }
    dirtyLeaveIds.add(id)
    deletedLeaveIds.remove(id)
    persistToDisk()
    id
  }

  override suspend fun updateLeaveRecord(
    leaveId: String,
    leaveType: LeaveType,
    startDate: String,
    endDate: String,
    status: LeaveStatus,
    notes: String,
    reviewedByName: String
  ) {
    mutex.withLock {
      _leaves.value = _leaves.value.map { existing ->
        if (existing.id == leaveId) {
          existing.copy(
            leaveType = leaveType.name,
            startDate = startDate,
            endDate = endDate,
            status = status.name,
            reviewedByName = reviewedByName.trim().take(100),
            notes = notes.trim().take(300),
            updatedAt = Timestamp.now()
          )
        } else {
          existing
        }
      }.sortedByDescending { it.startDate }
      dirtyLeaveIds.add(leaveId)
      deletedLeaveIds.remove(leaveId)
      persistToDisk()
    }
  }

  override suspend fun deleteLeaveRecord(leaveId: String) {
    mutex.withLock {
      _leaves.value = _leaves.value.filterNot { it.id == leaveId }
      dirtyLeaveIds.remove(leaveId)
      deletedLeaveIds.add(leaveId)
      persistToDisk()
    }
  }

  override suspend fun logEmergencyOverride(log: EmergencyOverrideLog): String = mutex.withLock {
    val id = "ovr_${UUID.randomUUID().toString().replace("-", "").take(16)}"
    val now = Timestamp.now()
    val created = log.copy(id = id, userId = LOCAL_USER_ID, createdAt = now, updatedAt = now)
    _overrides.value = listOf(created) + _overrides.value
    persistToDisk()
    id
  }

  suspend fun snapshotPendingLocalChanges(): PendingLocalSyncSnapshot = mutex.withLock {
    val staffById = _staff.value.associateBy { it.id }
    val assignById = _assignments.value.associateBy { it.id }
    val leaveById = _leaves.value.associateBy { it.id }
    PendingLocalSyncSnapshot(
      dirtyStaff = dirtyStaffIds.mapNotNull { staffById[it] },
      deletedStaffIds = deletedStaffIds.toSet(),
      dirtyAssignments = dirtyAssignmentIds.mapNotNull { assignById[it] },
      deletedAssignmentIds = deletedAssignmentIds.toSet(),
      dirtyLeaves = dirtyLeaveIds.mapNotNull { leaveById[it] },
      deletedLeaveIds = deletedLeaveIds.toSet()
    )
  }

  suspend fun markLocalChangesSynced(
    syncedStaffIds: Set<String> = emptySet(),
    syncedDeletedStaffIds: Set<String> = emptySet(),
    syncedAssignmentIds: Set<String> = emptySet(),
    syncedDeletedAssignmentIds: Set<String> = emptySet(),
    syncedLeaveIds: Set<String> = emptySet(),
    syncedDeletedLeaveIds: Set<String> = emptySet()
  ) {
    mutex.withLock {
      val changed = dirtyStaffIds.removeAll(syncedStaffIds) or
        deletedStaffIds.removeAll(syncedDeletedStaffIds) or
        dirtyAssignmentIds.removeAll(syncedAssignmentIds) or
        deletedAssignmentIds.removeAll(syncedDeletedAssignmentIds) or
        dirtyLeaveIds.removeAll(syncedLeaveIds) or
        deletedLeaveIds.removeAll(syncedDeletedLeaveIds)
      if (changed) {
        persistToDisk()
      }
    }
  }

  suspend fun mirrorCloudSnapshot(
    staff: List<StaffMember>? = null,
    assignments: List<ShiftSlotAssignment>? = null,
    leaves: List<LeaveRecord>? = null,
    overrides: List<EmergencyOverrideLog>? = null,
    dutyDrafts: List<DutyApprovalRequest>? = null,
    dutyLocks: List<DutyDateLock>? = null,
    leaveAccounts: List<StaffLeaveAccount>? = null,
    dutyPermissions: List<DutyPermissionRecord>? = null,
    rakes: List<RakeFormation>? = null
  ) {
    mutex.withLock {
      var changed = false
      if (staff != null && dirtyStaffIds.isEmpty() && deletedStaffIds.isEmpty() && staff.isNotEmpty()) {
        val sorted = staff.sortedBy { it.serialNo }
        if (_staff.value != sorted) {
          _staff.value = sorted
          changed = true
        }
      }
      if (assignments != null && dirtyAssignmentIds.isEmpty() && deletedAssignmentIds.isEmpty()) {
        if (_assignments.value != assignments) {
          _assignments.value = assignments
          changed = true
        }
      }
      if (leaves != null && dirtyLeaveIds.isEmpty() && deletedLeaveIds.isEmpty()) {
        val sorted = leaves.sortedByDescending { it.startDate }
        if (_leaves.value != sorted) {
          _leaves.value = sorted
          changed = true
        }
      }
      if (overrides != null && _overrides.value != overrides) {
        _overrides.value = overrides
        changed = true
      }
      if (dutyDrafts != null && _dutyDrafts.value != dutyDrafts) {
        _dutyDrafts.value = dutyDrafts
        changed = true
      }
      if (dutyLocks != null && _dutyLocks.value != dutyLocks) {
        _dutyLocks.value = dutyLocks
        changed = true
      }
      if (leaveAccounts != null && _leaveAccounts.value != leaveAccounts) {
        _leaveAccounts.value = leaveAccounts
        changed = true
      }
      if (dutyPermissions != null && _dutyPermissions.value != dutyPermissions) {
        _dutyPermissions.value = dutyPermissions
        changed = true
      }
      if (rakes != null) {
        val sorted = rakes.sortedWith(compareBy<RakeFormation>({ it.sortOrder }, { it.rakeName.uppercase() }))
        if (_rakes.value != sorted) {
          _rakes.value = sorted
          changed = true
        }
      }
      if (changed) {
        persistToDisk()
      }
    }
  }

  private fun loadFromDisk() {
    val sourceFile = when {
      storeFile.exists() && storeFile.length() > 0L -> storeFile
      else -> {
        val backup = File(storeFile.parentFile, "${storeFile.name}.bak")
        if (backup.exists() && backup.length() > 0L) backup else return
      }
    }
    val root = try {
      JSONObject(sourceFile.readText())
    } catch (e: Exception) {
      Log.w(TAG, "Failed to parse local roster JSON", e)
      return
    }

    runCatching {
      val staffArr = root.optJSONArray("staff") ?: JSONArray()
      _staff.value = (0 until staffArr.length()).mapNotNull { idx ->
        runCatching {
          val o = staffArr.getJSONObject(idx)
          val id = o.optString("id", "")
          val name = o.optString("name", "")
          if (id.isBlank() || name.isBlank()) return@runCatching null
          StaffMember(
            id = id,
            userId = LOCAL_USER_ID,
            serialNo = o.optLong("serialNo", (idx + 1).toLong()),
            name = name,
            designation = o.optString("designation", "KHP"),
            competency = o.optString("competency", "NONE"),
            stationDuty = o.optBoolean("stationDuty", false),
            shedDuty = o.optBoolean("shedDuty", false),
            weeklyRestDay = o.optString("weeklyRestDay", WeeklyRestDay.NONE.name),
            trainingDueDate = o.optString("trainingDueDate", ""),
            trainingCourse = o.optString("trainingCourse", ""),
            phoneNumber = o.optString("phoneNumber", ""),
            secondaryPhoneNumber = o.optString("secondaryPhoneNumber", "")
          )
        }.getOrNull()
      }.sortedBy { it.serialNo }
    }.onFailure { Log.w(TAG, "Failed loading staff section", it) }

    runCatching {
      val assignArr = root.optJSONArray("assignments") ?: JSONArray()
      _assignments.value = (0 until assignArr.length()).mapNotNull { idx ->
        runCatching {
          val o = assignArr.getJSONObject(idx)
          val id = o.optString("id", "")
          val date = o.optString("date", "")
          val staffId = o.optString("staffId", "")
          if (id.isBlank() || date.isBlank() || staffId.isBlank()) return@runCatching null
          ShiftSlotAssignment(
            id = id,
            userId = LOCAL_USER_ID,
            date = date,
            shiftType = o.optString("shiftType", "SHIFT_06_14"),
            dutyArea = o.optString("dutyArea", "SHED"),
            roleType = o.optString("roleType", "GENERAL_DUTY"),
            slotIndex = o.optLong("slotIndex", 0L),
            staffId = staffId,
            staffName = o.optString("staffName", ""),
            isEmergencyOverride = o.optBoolean("isEmergencyOverride", false),
            overrideReason = o.optString("overrideReason", ""),
            approvedLocked = o.optBoolean("approvedLocked", false),
            approvedByName = o.optString("approvedByName", "")
          )
        }.getOrNull()
      }
    }.onFailure { Log.w(TAG, "Failed loading assignments section", it) }

    runCatching {
      val leaveArr = root.optJSONArray("leaves") ?: JSONArray()
      _leaves.value = (0 until leaveArr.length()).mapNotNull { idx ->
        runCatching {
          val o = leaveArr.getJSONObject(idx)
          val id = o.optString("id", "")
          val staffId = o.optString("staffId", "")
          val startDate = o.optString("startDate", "")
          val endDate = o.optString("endDate", startDate)
          if (id.isBlank() || staffId.isBlank() || startDate.isBlank()) return@runCatching null
          LeaveRecord(
            id = id,
            userId = LOCAL_USER_ID,
            staffId = staffId,
            staffName = o.optString("staffName", ""),
            leaveType = o.optString("leaveType", LeaveType.CL.name),
            startDate = startDate,
            endDate = endDate,
            status = o.optString("status", LeaveStatus.APPROVED.name),
            appliedByUid = o.optString("appliedByUid", ""),
            appliedByName = o.optString("appliedByName", ""),
            forwardedToStaffId = o.optString("forwardedToStaffId", ""),
            forwardedToName = o.optString("forwardedToName", ""),
            reviewedByName = o.optString("reviewedByName", ""),
            notes = o.optString("notes", "")
          )
        }.getOrNull()
      }.sortedByDescending { it.startDate }
    }.onFailure { Log.w(TAG, "Failed loading leaves section", it) }

    runCatching {
      val ovrArr = root.optJSONArray("overrides") ?: JSONArray()
      _overrides.value = (0 until ovrArr.length()).mapNotNull { idx ->
        runCatching {
          val o = ovrArr.getJSONObject(idx)
          val id = o.optString("id", "")
          if (id.isBlank()) return@runCatching null
          val sec = o.optLong("createdSec", 0L)
          EmergencyOverrideLog(
            id = id,
            userId = LOCAL_USER_ID,
            date = o.optString("date", ""),
            shiftType = o.optString("shiftType", "SHIFT_06_14"),
            dutyArea = o.optString("dutyArea", "SHED"),
            staffId = o.optString("staffId", ""),
            staffName = o.optString("staffName", ""),
            conflictSummary = o.optString("conflictSummary", ""),
            reason = o.optString("reason", ""),
            createdAt = if (sec > 0L) Timestamp(sec, 0) else null
          )
        }.getOrNull()
      }
    }.onFailure { Log.w(TAG, "Failed loading overrides section", it) }

    runCatching {
      val draftsArr = root.optJSONArray("dutyDrafts") ?: JSONArray()
      _dutyDrafts.value = (0 until draftsArr.length()).mapNotNull { idx ->
        runCatching {
          val o = draftsArr.getJSONObject(idx)
          val id = o.optString("id", "")
          val dutyDate = o.optString("dutyDate", "")
          if (id.isBlank() || dutyDate.isBlank()) return@runCatching null
          val slotsArr = o.optJSONArray("proposedSlots") ?: JSONArray()
          val slots = (0 until slotsArr.length()).mapNotNull { sIdx ->
            runCatching {
              val so = slotsArr.getJSONObject(sIdx)
              DraftSlotEntry(
                slotId = so.optString("slotId", ""),
                date = so.optString("date", ""),
                shiftType = so.optString("shiftType", "SHIFT_06_14"),
                dutyArea = so.optString("dutyArea", "SHED"),
                roleType = so.optString("roleType", "GENERAL_DUTY"),
                slotIndex = so.optLong("slotIndex", 0L),
                staffId = so.optString("staffId", ""),
                staffName = so.optString("staffName", ""),
                isEmergencyOverride = so.optBoolean("isEmergencyOverride", false),
                overrideReason = so.optString("overrideReason", "")
              )
            }.getOrNull()
          }
          DutyApprovalRequest(
            id = id,
            depotId = LOCAL_USER_ID,
            dutyDate = dutyDate,
            status = o.optString("status", DutyDraftStatus.PENDING.name),
            submittedByUid = o.optString("submittedByUid", ""),
            submittedByName = o.optString("submittedByName", ""),
            reviewedByUid = o.optString("reviewedByUid", ""),
            reviewedByName = o.optString("reviewedByName", ""),
            reviewNotes = o.optString("reviewNotes", ""),
            proposedSlots = slots
          )
        }.getOrNull()
      }
    }.onFailure { Log.w(TAG, "Failed loading dutyDrafts section", it) }

    runCatching {
      val locksArr = root.optJSONArray("dutyLocks") ?: JSONArray()
      _dutyLocks.value = (0 until locksArr.length()).mapNotNull { idx ->
        runCatching {
          val o = locksArr.getJSONObject(idx)
          val date = o.optString("dutyDate", "")
          if (date.isBlank()) return@runCatching null
          DutyDateLock(
            id = date,
            dutyDate = date,
            lockedByUid = o.optString("lockedByUid", ""),
            lockedByName = o.optString("lockedByName", "")
          )
        }.getOrNull()
      }
    }.onFailure { Log.w(TAG, "Failed loading dutyLocks section", it) }

    runCatching {
      val credsObj = root.optJSONObject("credentials")
      if (credsObj != null) {
        credentialsByUid.clear()
        credsObj.keys().forEach { uidKey ->
          val hash = credsObj.optString(uidKey, "")
          if (hash.isNotBlank()) {
            credentialsByUid[uidKey] = hash
          }
        }
      }
    }.onFailure { Log.w(TAG, "Failed loading credentials section", it) }

    runCatching {
      val rolesArr = root.optJSONArray("userRoles")
      if (rolesArr != null && rolesArr.length() > 0) {
        val loaded = (0 until rolesArr.length()).mapNotNull { idx ->
          runCatching {
            val o = rolesArr.getJSONObject(idx)
            val uid = o.optString("uid", "")
            if (uid.isBlank()) return@runCatching null
            DepotUserProfile(
              id = uid,
              uid = uid,
              username = o.optString("username", ""),
              email = o.optString("email", ""),
              displayName = o.optString("displayName", ""),
              role = o.optString("role", UserRole.TECHNICIAN.name),
              staffId = o.optString("staffId", ""),
              updatedByUid = o.optString("updatedByUid", ""),
              hasPassword = credentialsByUid.containsKey(uid)
            )
          }.getOrNull()
        }
        if (loaded.isNotEmpty()) {
          _userProfiles.value = loaded
        }
      }
    }.onFailure { Log.w(TAG, "Failed loading userRoles section", it) }

    runCatching {
      val settingsObj = root.optJSONObject("depotSettings")
      if (settingsObj != null) {
        val parsedRoleMenuAccess = linkedMapOf<String, Map<String, String>>()
        val accessObj = settingsObj.optJSONObject("roleMenuAccess")
        if (accessObj != null) {
          val roleKeys = accessObj.keys()
          while (roleKeys.hasNext()) {
            val roleKey = roleKeys.next()
            val menuObj = accessObj.optJSONObject(roleKey) ?: continue
            val menuMap = linkedMapOf<String, String>()
            val menuKeys = menuObj.keys()
            while (menuKeys.hasNext()) {
              val menuKey = menuKeys.next()
              val levelVal = menuObj.optString(menuKey, "")
              if (levelVal.isNotBlank()) {
                menuMap[menuKey] = levelVal
              }
            }
            if (menuMap.isNotEmpty()) {
              parsedRoleMenuAccess[roleKey] = menuMap
            }
          }
        }
        _depotSettings.value = DepotSettings(
          id = "config",
          allowTechnicianShare = settingsObj.optBoolean("allowTechnicianShare", true),
          showShunterForTechnicians = settingsObj.optBoolean("showShunterForTechnicians", false),
          includeShunterInExport = settingsObj.optBoolean("includeShunterInExport", false),
          importantContactsInitialized = settingsObj.optBoolean("importantContactsInitialized", false),
          rakesInitialized = settingsObj.optBoolean("rakesInitialized", false),
          rakesSchemaVersion = settingsObj.optLong("rakesSchemaVersion", 0L),
          roleMenuAccess = RoleAccessPolicy.sanitizeRoleAccessMap(parsedRoleMenuAccess),
          updatedByUid = settingsObj.optString("updatedByUid", "")
        )
      }
    }.onFailure { Log.w(TAG, "Failed loading depotSettings section", it) }

    runCatching {
      val releaseObj = root.optJSONObject("appRelease")
      if (releaseObj != null) {
        _appRelease.value = AppReleaseConfig(
          id = AppReleaseConfig.LATEST_DOC_ID,
          latestVersionCode = releaseObj.optInt("latestVersionCode", 0),
          latestVersionName = releaseObj.optString("latestVersionName", ""),
          minSupportedVersionCode = releaseObj.optInt("minSupportedVersionCode", 0),
          mandatory = releaseObj.optBoolean("mandatory", false),
          downloadUrl = releaseObj.optString("downloadUrl", ""),
          releaseNotes = releaseObj.optString("releaseNotes", ""),
          sha256Checksum = releaseObj.optString("sha256Checksum", ""),
          publishedByUid = releaseObj.optString("publishedByUid", ""),
          publishedByName = releaseObj.optString("publishedByName", "")
        )
      }
    }.onFailure { Log.w(TAG, "Failed loading appRelease section", it) }

    runCatching {
      val accArr = root.optJSONArray("leaveAccounts") ?: JSONArray()
      _leaveAccounts.value = (0 until accArr.length()).mapNotNull { idx ->
        runCatching {
          val o = accArr.getJSONObject(idx)
          val staffId = o.optString("staffId", "")
          if (staffId.isBlank()) return@runCatching null
          StaffLeaveAccount(
            id = staffId,
            staffId = staffId,
            staffName = o.optString("staffName", ""),
            baseYear = o.optInt("baseYear", 0),
            lapOpeningBalance = o.optInt("lapOpeningBalance", 30),
            lhapOpeningBalance = o.optInt("lhapOpeningBalance", 20),
            lapSelfUpdated = o.optBoolean("lapSelfUpdated", false),
            pendingLapBalance = o.optInt("pendingLapBalance", 0),
            pendingLhapBalance = o.optInt("pendingLhapBalance", -1),
            pendingLapYear = o.optInt("pendingLapYear", 0),
            pendingLapReason = o.optString("pendingLapReason", ""),
            pendingLapStatus = o.optString("pendingLapStatus", LapUpdateStatus.NONE.name),
            pendingLapRequestedByUid = o.optString("pendingLapRequestedByUid", ""),
            pendingLapReviewedByName = o.optString("pendingLapReviewedByName", "")
          )
        }.getOrNull()
      }
    }.onFailure { Log.w(TAG, "Failed loading leaveAccounts section", it) }

    runCatching {
      val permArr = root.optJSONArray("dutyPermissions") ?: JSONArray()
      _dutyPermissions.value = (0 until permArr.length()).mapNotNull { idx ->
        runCatching {
          val o = permArr.getJSONObject(idx)
          val id = o.optString("id", "")
          val staffId = o.optString("staffId", "")
          val date = o.optString("date", "")
          if (id.isBlank() || staffId.isBlank() || date.isBlank()) return@runCatching null
          val createdSec = o.optLong("createdSec", 0L)
          DutyPermissionRecord(
            id = id,
            userId = LOCAL_USER_ID,
            staffId = staffId,
            staffName = o.optString("staffName", ""),
            date = date,
            shiftType = o.optString("shiftType", "SHIFT_06_14"),
            hours = o.optDouble("hours", 1.0).coerceIn(0.5, 12.0),
            reliefType = o.optString("reliefType", "EARLY_RELIEF"),
            reason = o.optString("reason", ""),
            grantedByUid = o.optString("grantedByUid", ""),
            grantedByName = o.optString("grantedByName", ""),
            createdAt = if (createdSec > 0L) Timestamp(createdSec, 0) else null
          )
        }.getOrNull()
      }.sortedWith(
        compareByDescending<DutyPermissionRecord> { it.date }
          .thenByDescending { it.createdAt?.seconds ?: 0L }
      )
    }.onFailure { Log.w(TAG, "Failed loading dutyPermissions section", it) }

    runCatching {
      if (root.has("importantContacts")) {
        val contactsArr = root.optJSONArray("importantContacts") ?: JSONArray()
        _importantContacts.value = (0 until contactsArr.length()).mapNotNull { idx ->
          runCatching {
            val o = contactsArr.getJSONObject(idx)
            val id = o.optString("id", "")
            val label = o.optString("label", "")
            val phone = o.optString("phoneNumber", "")
            if (id.isBlank() || label.isBlank() || phone.isBlank()) return@runCatching null
            ImportantContact(
              id = id,
              userId = LOCAL_USER_ID,
              label = label,
              phoneNumber = phone,
              category = o.optString("category", "Control"),
              sortOrder = o.optLong("sortOrder", idx.toLong())
            )
          }.getOrNull()
        }.sortedWith(compareBy<ImportantContact>({ it.sortOrder }, { it.label.uppercase() }))
      } else {
        _importantContacts.value = IMPORTANT_DEPOT_CONTACTS
      }
    }.onFailure { Log.w(TAG, "Failed loading importantContacts section", it) }

    runCatching {
      if (root.has("rakes")) {
        val rakesArr = root.optJSONArray("rakes") ?: JSONArray()
        _rakes.value = (0 until rakesArr.length()).mapNotNull { idx ->
          runCatching {
            val o = rakesArr.getJSONObject(idx)
            val id = o.optString("id", "")
            val rakeName = o.optString("rakeName", "")
            if (id.isBlank() || rakeName.isBlank()) return@runCatching null
            val coachesArr = o.optJSONArray("coaches") ?: JSONArray()
            val coaches = (0 until coachesArr.length()).mapNotNull { cIdx ->
              runCatching {
                val co = coachesArr.getJSONObject(cIdx)
                RakeCoach(
                  position = co.optLong("position", (cIdx + 1).toLong()),
                  coachNumber = co.optString("coachNumber", ""),
                  coachType = co.optString("coachType", "TC"),
                  altNumber = co.optString("altNumber", ""),
                  endTag = co.optString("endTag", "")
                )
              }.getOrNull()
            }
            RakeFormation(
              id = id,
              userId = LOCAL_USER_ID,
              rakeName = rakeName,
              category = o.optString("category", "EMU_3PHASE_12CAR"),
              make = o.optString("make", ""),
              tcasFitted = o.optBoolean("tcasFitted", false),
              startEnd = o.optString("startEnd", ""),
              finishEnd = o.optString("finishEnd", ""),
              remarks = o.optString("remarks", ""),
              sortOrder = o.optLong("sortOrder", idx.toLong()),
              coaches = coaches,
              updatedByUid = o.optString("updatedByUid", ""),
              updatedByName = o.optString("updatedByName", "")
            )
          }.getOrNull()
        }.sortedWith(compareBy<RakeFormation>({ it.sortOrder }, { it.rakeName.uppercase() }))
      } else {
        _rakes.value = DefaultRakeFormations.all
      }
    }.onFailure { Log.w(TAG, "Failed loading rakes section", it) }

    runCatching {
      val pendingObj = root.optJSONObject("pendingSync")
      if (pendingObj != null) {
        readStringSetInto(pendingObj.optJSONArray("dirtyStaffIds"), dirtyStaffIds)
        readStringSetInto(pendingObj.optJSONArray("deletedStaffIds"), deletedStaffIds)
        readStringSetInto(pendingObj.optJSONArray("dirtyAssignmentIds"), dirtyAssignmentIds)
        readStringSetInto(pendingObj.optJSONArray("deletedAssignmentIds"), deletedAssignmentIds)
        readStringSetInto(pendingObj.optJSONArray("dirtyLeaveIds"), dirtyLeaveIds)
        readStringSetInto(pendingObj.optJSONArray("deletedLeaveIds"), deletedLeaveIds)
      }
    }.onFailure { Log.w(TAG, "Failed loading pendingSync section", it) }
  }

  private fun readStringSetInto(array: JSONArray?, target: MutableSet<String>) {
    target.clear()
    if (array == null) return
    for (i in 0 until array.length()) {
      val value = array.optString(i, "")
      if (value.isNotBlank()) target.add(value)
    }
  }

  private fun ensureDefaultAdminCredentialSeeded() {
    if (isTestSandbox) {
      val sandboxUid = DepotRoleManager.OFFLINE_TEST_ADMIN_UID
      val existingSandboxAdmin = _userProfiles.value.firstOrNull { it.uid == sandboxUid }
      if (!credentialsByUid.containsKey(sandboxUid)) {
        credentialsByUid[sandboxUid] = hashPassword(DepotRoleManager.OFFLINE_TEST_ADMIN_PASSWORD)
      }
      if (existingSandboxAdmin == null) {
        val fallbackSandboxAdmin = defaultOfflineTestUserProfiles().first()
        _userProfiles.value = (listOf(fallbackSandboxAdmin) + _userProfiles.value).sortedBy { it.displayName.uppercase() }
      } else {
        _userProfiles.value = _userProfiles.value.map { profile ->
          if (profile.uid == sandboxUid) {
            profile.copy(
              username = profile.username.ifBlank { DepotRoleManager.OFFLINE_TEST_ADMIN_USERNAME },
              email = "",
              displayName = profile.displayName.ifBlank { DepotRoleManager.OFFLINE_TEST_ADMIN_DISPLAY_NAME },
              role = UserRole.ADMIN.name,
              hasPassword = true
            )
          } else {
            profile.copy(hasPassword = credentialsByUid.containsKey(profile.uid))
          }
        }
      }
      return
    }

    val existingAdmin = _userProfiles.value.firstOrNull { it.uid == DepotRoleManager.ADMIN_ACCOUNT_ID }
    if (!credentialsByUid.containsKey(DepotRoleManager.ADMIN_ACCOUNT_ID)) {
      credentialsByUid[DepotRoleManager.ADMIN_ACCOUNT_ID] = hashPassword(DepotRoleManager.DEFAULT_ADMIN_PASSWORD)
    }
    if (existingAdmin == null) {
      val fallbackAdmin = defaultLocalUserProfiles().first { it.uid == DepotRoleManager.ADMIN_ACCOUNT_ID }
      _userProfiles.value = (listOf(fallbackAdmin) + _userProfiles.value).sortedBy { it.displayName.uppercase() }
    } else {
      _userProfiles.value = _userProfiles.value.map { profile ->
        if (profile.uid == DepotRoleManager.ADMIN_ACCOUNT_ID) {
          profile.copy(
            username = profile.username.ifBlank { DepotRoleManager.ADMIN_USERNAME },
            email = profile.email.ifBlank { DepotRoleManager.ADMIN_EMAIL },
            displayName = profile.displayName.ifBlank { DepotRoleManager.ADMIN_DISPLAY_NAME },
            role = UserRole.ADMIN.name,
            hasPassword = true
          )
        } else {
          profile.copy(hasPassword = credentialsByUid.containsKey(profile.uid))
        }
      }
    }
  }

  private suspend fun persistToDisk() = withContext(NonCancellable + Dispatchers.IO) {
    try {
      val root = JSONObject()

      val staffArr = JSONArray()
      _staff.value.forEach { m ->
        staffArr.put(
          JSONObject().apply {
            put("id", m.id)
            put("serialNo", m.serialNo)
            put("name", m.name)
            put("designation", m.designation)
            put("competency", m.competency)
            put("stationDuty", m.stationDuty)
            put("shedDuty", m.shedDuty)
            put("weeklyRestDay", m.weeklyRestDay)
            put("trainingDueDate", m.trainingDueDate)
            put("trainingCourse", m.trainingCourse)
            put("phoneNumber", m.phoneNumber)
            put("secondaryPhoneNumber", m.secondaryPhoneNumber)
          }
        )
      }
      root.put("staff", staffArr)

      val assignArr = JSONArray()
      _assignments.value.forEach { a ->
        assignArr.put(
          JSONObject().apply {
            put("id", a.id)
            put("date", a.date)
            put("shiftType", a.shiftType)
            put("dutyArea", a.dutyArea)
            put("roleType", a.roleType)
            put("slotIndex", a.slotIndex)
            put("staffId", a.staffId)
            put("staffName", a.staffName)
            put("isEmergencyOverride", a.isEmergencyOverride)
            put("overrideReason", a.overrideReason)
            put("approvedLocked", a.approvedLocked)
            put("approvedByName", a.approvedByName)
          }
        )
      }
      root.put("assignments", assignArr)

      val leaveArr = JSONArray()
      _leaves.value.forEach { l ->
        leaveArr.put(
          JSONObject().apply {
            put("id", l.id)
            put("staffId", l.staffId)
            put("staffName", l.staffName)
            put("leaveType", l.leaveType)
            put("startDate", l.startDate)
            put("endDate", l.endDate)
            put("status", l.statusEnum.name)
            put("appliedByUid", l.appliedByUid)
            put("appliedByName", l.appliedByName)
            put("forwardedToStaffId", l.forwardedToStaffId)
            put("forwardedToName", l.forwardedToName)
            put("reviewedByName", l.reviewedByName)
            put("notes", l.notes)
          }
        )
      }
      root.put("leaves", leaveArr)

      val ovrArr = JSONArray()
      _overrides.value.forEach { ov ->
        ovrArr.put(
          JSONObject().apply {
            put("id", ov.id)
            put("date", ov.date)
            put("shiftType", ov.shiftType)
            put("dutyArea", ov.dutyArea)
            put("staffId", ov.staffId)
            put("staffName", ov.staffName)
            put("conflictSummary", ov.conflictSummary)
            put("reason", ov.reason)
            put("createdSec", ov.createdAt?.seconds ?: 0L)
          }
        )
      }
      root.put("overrides", ovrArr)

      val draftsArr = JSONArray()
      _dutyDrafts.value.forEach { d ->
        draftsArr.put(
          JSONObject().apply {
            put("id", d.id)
            put("dutyDate", d.dutyDate)
            put("status", d.status)
            put("submittedByUid", d.submittedByUid)
            put("submittedByName", d.submittedByName)
            put("reviewedByUid", d.reviewedByUid)
            put("reviewedByName", d.reviewedByName)
            put("reviewNotes", d.reviewNotes)
            val slotsArr = JSONArray()
            d.proposedSlots.forEach { s ->
              slotsArr.put(
                JSONObject().apply {
                  put("slotId", s.slotId)
                  put("date", s.date)
                  put("shiftType", s.shiftType)
                  put("dutyArea", s.dutyArea)
                  put("roleType", s.roleType)
                  put("slotIndex", s.slotIndex)
                  put("staffId", s.staffId)
                  put("staffName", s.staffName)
                  put("isEmergencyOverride", s.isEmergencyOverride)
                  put("overrideReason", s.overrideReason)
                }
              )
            }
            put("proposedSlots", slotsArr)
          }
        )
      }
      root.put("dutyDrafts", draftsArr)

      val locksArr = JSONArray()
      _dutyLocks.value.forEach { lock ->
        locksArr.put(
          JSONObject().apply {
            put("dutyDate", lock.dutyDate)
            put("lockedByUid", lock.lockedByUid)
            put("lockedByName", lock.lockedByName)
          }
        )
      }
      root.put("dutyLocks", locksArr)

      val rolesArr = JSONArray()
      _userProfiles.value.forEach { p ->
        rolesArr.put(
          JSONObject().apply {
            put("uid", p.uid)
            put("username", p.username)
            put("email", p.email)
            put("displayName", p.displayName)
            put("role", p.role)
            put("staffId", p.staffId)
            put("updatedByUid", p.updatedByUid)
          }
        )
      }
      root.put("userRoles", rolesArr)

      val credsObj = JSONObject()
      credentialsByUid.forEach { (uid, hash) ->
        credsObj.put(uid, hash)
      }
      root.put("credentials", credsObj)

      val settingsObj = JSONObject().apply {
        put("allowTechnicianShare", _depotSettings.value.allowTechnicianShare)
        put("showShunterForTechnicians", _depotSettings.value.showShunterForTechnicians)
        put("includeShunterInExport", _depotSettings.value.includeShunterInExport)
        put("importantContactsInitialized", _depotSettings.value.importantContactsInitialized)
        put("rakesInitialized", _depotSettings.value.rakesInitialized)
        put("rakesSchemaVersion", _depotSettings.value.rakesSchemaVersion)
        val accessObj = JSONObject()
        _depotSettings.value.roleMenuAccess.forEach { (roleKey, menuMap) ->
          val menuObj = JSONObject()
          menuMap.forEach { (menuKey, levelVal) ->
            menuObj.put(menuKey, levelVal)
          }
          accessObj.put(roleKey, menuObj)
        }
        put("roleMenuAccess", accessObj)
        put("updatedByUid", _depotSettings.value.updatedByUid)
      }
      root.put("depotSettings", settingsObj)

      val releaseVal = _appRelease.value
      val releaseObj = JSONObject().apply {
        put("latestVersionCode", releaseVal.latestVersionCode)
        put("latestVersionName", releaseVal.latestVersionName)
        put("minSupportedVersionCode", releaseVal.minSupportedVersionCode)
        put("mandatory", releaseVal.mandatory)
        put("downloadUrl", releaseVal.downloadUrl)
        put("releaseNotes", releaseVal.releaseNotes)
        put("sha256Checksum", releaseVal.sha256Checksum)
        put("publishedByUid", releaseVal.publishedByUid)
        put("publishedByName", releaseVal.publishedByName)
      }
      root.put("appRelease", releaseObj)

      val accArr = JSONArray()
      _leaveAccounts.value.forEach { acc ->
        accArr.put(
          JSONObject().apply {
            put("staffId", acc.staffId)
            put("staffName", acc.staffName)
            put("baseYear", acc.baseYear)
            put("lapOpeningBalance", acc.lapOpeningBalance)
            put("lhapOpeningBalance", acc.lhapOpeningBalance)
            put("lapSelfUpdated", acc.lapSelfUpdated)
            put("pendingLapBalance", acc.pendingLapBalance)
            put("pendingLhapBalance", acc.pendingLhapBalance)
            put("pendingLapYear", acc.pendingLapYear)
            put("pendingLapReason", acc.pendingLapReason)
            put("pendingLapStatus", acc.pendingLapStatusEnum.name)
            put("pendingLapRequestedByUid", acc.pendingLapRequestedByUid)
            put("pendingLapReviewedByName", acc.pendingLapReviewedByName)
          }
        )
      }
      root.put("leaveAccounts", accArr)

      val permArr = JSONArray()
      _dutyPermissions.value.forEach { p ->
        permArr.put(
          JSONObject().apply {
            put("id", p.id)
            put("staffId", p.staffId)
            put("staffName", p.staffName)
            put("date", p.date)
            put("shiftType", p.shiftTypeEnum.name)
            put("hours", p.hours)
            put("reliefType", p.reliefTypeEnum.name)
            put("reason", p.reason)
            put("grantedByUid", p.grantedByUid)
            put("grantedByName", p.grantedByName)
            put("createdSec", p.createdAt?.seconds ?: 0L)
          }
        )
      }
      root.put("dutyPermissions", permArr)

      val contactsArr = JSONArray()
      _importantContacts.value.forEach { c ->
        contactsArr.put(
          JSONObject().apply {
            put("id", c.id)
            put("label", c.label)
            put("phoneNumber", c.phoneNumber)
            put("category", c.category)
            put("sortOrder", c.sortOrder)
          }
        )
      }
      root.put("importantContacts", contactsArr)

      val rakesArr = JSONArray()
      _rakes.value.forEach { rake ->
        val coachesArr = JSONArray()
        rake.sortedCoaches.forEach { coach ->
          coachesArr.put(
            JSONObject().apply {
              put("position", coach.position)
              put("coachNumber", coach.coachNumber)
              put("coachType", coach.coachType)
              put("altNumber", coach.altNumber)
              put("endTag", coach.endTag)
            }
          )
        }
        rakesArr.put(
          JSONObject().apply {
            put("id", rake.id)
            put("rakeName", rake.rakeName)
            put("category", rake.category)
            put("make", rake.make)
            put("tcasFitted", rake.tcasFitted)
            put("startEnd", rake.startEnd)
            put("finishEnd", rake.finishEnd)
            put("remarks", rake.remarks)
            put("sortOrder", rake.sortOrder)
            put("coaches", coachesArr)
            put("updatedByUid", rake.updatedByUid)
            put("updatedByName", rake.updatedByName)
          }
        )
      }
      root.put("rakes", rakesArr)

      val pendingObj = JSONObject().apply {
        put("dirtyStaffIds", JSONArray(dirtyStaffIds.toList()))
        put("deletedStaffIds", JSONArray(deletedStaffIds.toList()))
        put("dirtyAssignmentIds", JSONArray(dirtyAssignmentIds.toList()))
        put("deletedAssignmentIds", JSONArray(deletedAssignmentIds.toList()))
        put("dirtyLeaveIds", JSONArray(dirtyLeaveIds.toList()))
        put("deletedLeaveIds", JSONArray(deletedLeaveIds.toList()))
      }
      root.put("pendingSync", pendingObj)

      val payload = root.toString().toByteArray(Charsets.UTF_8)
      storeFile.parentFile?.mkdirs()
      val tmpFile = File(storeFile.parentFile, "${storeFile.name}.tmp")
      val bakFile = File(storeFile.parentFile, "${storeFile.name}.bak")
      FileOutputStream(tmpFile).use { fos ->
        fos.write(payload)
        fos.flush()
        runCatching { fos.fd.sync() }
      }
      if (storeFile.exists()) {
        runCatching { storeFile.copyTo(bakFile, overwrite = true) }
      }
      if (!tmpFile.renameTo(storeFile)) {
        FileOutputStream(storeFile).use { fos ->
          fos.write(payload)
          fos.flush()
          runCatching { fos.fd.sync() }
        }
        tmpFile.delete()
      }
    } catch (e: Exception) {
      Log.w(TAG, "Failed to persist local roster cache", e)
    }
  }

  private fun isPrimaryAdminProfile(profile: DepotUserProfile): Boolean =
    profile.uid == DepotRoleManager.OFFLINE_TEST_ADMIN_UID ||
      profile.uid == DepotRoleManager.ADMIN_ACCOUNT_ID ||
      profile.email.equals(DepotRoleManager.ADMIN_EMAIL, ignoreCase = true) ||
      profile.username.equals(DepotRoleManager.ADMIN_USERNAME, ignoreCase = true)

  companion object {
    const val LOCAL_USER_ID = DepotRoleManager.SHARED_DEPOT_ID
    private const val DEFAULT_STORAGE_FILE_NAME = "ppio_local_roster.json"
    private const val TEST_SANDBOX_FILE_NAME = "ppio_offline_test_sandbox.json"
    private const val TAG = "LocalRosterRepo"
    private val secureRandom = SecureRandom()

    @Volatile
    private var singletonInstance: LocalRosterRepository? = null

    @Volatile
    private var testSandboxInstance: LocalRosterRepository? = null

    fun getInstance(context: Context): LocalRosterRepository {
      val existing = singletonInstance
      if (existing != null) return existing
      return synchronized(this) {
        singletonInstance ?: LocalRosterRepository(context.applicationContext).also {
          singletonInstance = it
        }
      }
    }

    fun getTestSandboxInstance(context: Context): LocalRosterRepository {
      val existing = testSandboxInstance
      if (existing != null) return existing
      return synchronized(this) {
        testSandboxInstance ?: LocalRosterRepository(
          context = context.applicationContext,
          storageFileName = TEST_SANDBOX_FILE_NAME,
          isTestSandbox = true
        ).also {
          testSandboxInstance = it
        }
      }
    }

    internal fun resetSingletonForTests() {
      synchronized(this) {
        singletonInstance = null
        testSandboxInstance = null
      }
    }

    private fun hashPassword(password: String): String {
      val salt = ByteArray(16)
      secureRandom.nextBytes(salt)
      val digest = sha256(salt, password)
      return "${toHex(salt)}:${toHex(digest)}"
    }

    private fun verifyPassword(password: String, storedHash: String): Boolean {
      val parts = storedHash.split(":")
      if (parts.size != 2) return false
      val salt = fromHex(parts[0]) ?: return false
      val expectedDigest = fromHex(parts[1]) ?: return false
      val actualDigest = sha256(salt, password)
      return MessageDigest.isEqual(expectedDigest, actualDigest)
    }

    private fun sha256(salt: ByteArray, password: String): ByteArray {
      val md = MessageDigest.getInstance("SHA-256")
      md.update(salt)
      return md.digest(password.toByteArray(Charsets.UTF_8))
    }

    private fun toHex(bytes: ByteArray): String =
      bytes.joinToString("") { "%02x".format(it) }

    private fun fromHex(hex: String): ByteArray? {
      if (hex.length % 2 != 0) return null
      return runCatching {
        ByteArray(hex.length / 2) { idx ->
          hex.substring(idx * 2, idx * 2 + 2).toInt(16).toByte()
        }
      }.getOrNull()
    }

    private fun defaultOfflineTestUserProfiles(): List<DepotUserProfile> = listOf(
      DepotUserProfile(
        id = DepotRoleManager.OFFLINE_TEST_ADMIN_UID,
        uid = DepotRoleManager.OFFLINE_TEST_ADMIN_UID,
        username = DepotRoleManager.OFFLINE_TEST_ADMIN_USERNAME,
        email = "",
        displayName = DepotRoleManager.OFFLINE_TEST_ADMIN_DISPLAY_NAME,
        role = UserRole.ADMIN.name,
        staffId = "",
        updatedByUid = DepotRoleManager.OFFLINE_TEST_ADMIN_UID,
        hasPassword = true
      )
    )

    private fun defaultLocalUserProfiles(): List<DepotUserProfile> =
      DepotRoleManager.roleAccounts.map { account ->
        val isAdmin = account.id == DepotRoleManager.ADMIN_ACCOUNT_ID
        DepotUserProfile(
          id = account.id,
          uid = account.id,
          username = if (isAdmin) DepotRoleManager.ADMIN_USERNAME else account.id,
          email = account.emailHint.orEmpty(),
          displayName = account.displayName,
          role = account.role.name,
          staffId = if (account.id == "tech_1") "staff_7" else "",
          updatedByUid = DepotRoleManager.ADMIN_ACCOUNT_ID,
          hasPassword = isAdmin
        )
      }
  }
}
