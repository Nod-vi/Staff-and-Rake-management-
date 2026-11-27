package com.example.data

import android.content.Context
import com.example.R
import com.example.auth.DepotRoleManager
import com.example.model.AppReleaseConfig
import com.example.model.DepotSettings
import com.example.model.DepotUserProfile
import com.example.model.DutyApprovalRequest
import com.example.model.DutyDateLock
import com.example.model.DutyDraftStatus
import com.example.model.DutyPermissionRecord
import com.example.model.EmergencyOverrideLog
import com.example.model.IMPORTANT_DEPOT_CONTACTS
import com.example.model.ImportantContact
import com.example.domain.RakeFormationEngine
import com.example.model.LeaveRecord
import com.example.model.LeaveStatus
import com.example.model.LeaveType
import com.example.model.RakeFormation
import com.example.model.RoleAccessPolicy
import com.example.model.RoleInvite
import com.example.model.ShiftSlotAssignment
import com.example.model.StaffLeaveAccount
import com.example.model.StaffMember
import com.example.model.UserRole
import com.example.model.WeeklyRestDay
import com.google.android.gms.tasks.Task
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.PersistentCacheSettings
import com.google.firebase.firestore.Source
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID

class FirestoreRosterRepository(private val db: FirebaseFirestore) : RosterRepository {

  constructor(context: Context) : this(FirestoreProvider.getInstance(context))

  private val auth = Firebase.auth
  private val depotId = DepotRoleManager.SHARED_DEPOT_ID
  private fun depotDoc() = db.collection("depots").document(depotId)

  private fun requireAuthUid(): String {
    return auth.currentUser?.uid
      ?: throw IllegalStateException("User must be signed in with Google before accessing shared depot Firestore.")
  }

  /**
   * Reads a single document with a short timeout, seamlessly falling back to the on-disk
   * Firestore persistent cache when offline or experiencing a poor/intermittent connection.
   */
  private suspend fun DocumentReference.getOfflineAware(timeoutMs: Long = 2_500L): DocumentSnapshot? {
    val defaultSnap = try {
      withTimeoutOrNull(timeoutMs) {
        get(Source.DEFAULT).await()
      }
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      if (e is FirebaseFirestoreException && e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
        throw e
      }
      null
    }
    if (defaultSnap != null) return defaultSnap
    return runCatching { get(Source.CACHE).await() }.getOrNull()
  }

  private suspend fun DocumentReference.existsOfflineAware(): Boolean =
    getOfflineAware()?.exists() == true

  /**
   * Firestore offline persistence commits mutations into the local persistent disk queue
   * immediately (updating all active UI snapshot listeners within milliseconds), while the
   * returned Task only completes once the remote server acknowledges the write.
   *
   * Waiting briefly allows fast server confirmation and immediate permission/validation feedback
   * when online, while returning cleanly without freezing the UI when offline or on a poor link.
   * Any delayed server rejection upon reconnection is logged via [handleFirestoreError].
   */
  private suspend fun Task<Void>.awaitLocalOrServer(
    operationType: OperationType,
    path: String,
    timeoutMs: Long = 2_500L
  ) {
    addOnFailureListener { error ->
      if (auth.currentUser != null) {
        handleFirestoreError(error, operationType, path)
      }
    }
    withTimeoutOrNull(timeoutMs) {
      await()
    }
  }

  override fun observeSyncMetadata(depotId: String): Flow<FirestoreCacheSnapshotState> = flow {
    requireAuthUid()
    val depotRef = db.collection("depots").document(depotId)
    val staffMetaFlow = depotRef.collection("staff")
      .snapshots(MetadataChanges.INCLUDE)
      .map { snap -> snap.metadata.isFromCache to snap.metadata.hasPendingWrites() }
      .catch { emit(true to false) }
    val assignmentsMetaFlow = depotRef.collection("assignments")
      .snapshots(MetadataChanges.INCLUDE)
      .map { snap -> snap.metadata.isFromCache to snap.metadata.hasPendingWrites() }
      .catch { emit(true to false) }
    val leavesMetaFlow = depotRef.collection("leaves")
      .snapshots(MetadataChanges.INCLUDE)
      .map { snap -> snap.metadata.isFromCache to snap.metadata.hasPendingWrites() }
      .catch { emit(true to false) }

    emitAll(
      combine(staffMetaFlow, assignmentsMetaFlow, leavesMetaFlow) { staffMeta, assignMeta, leaveMeta ->
        val isFromCache = staffMeta.first || assignMeta.first || leaveMeta.first
        val hasPendingWrites = staffMeta.second || assignMeta.second || leaveMeta.second
        FirestoreCacheSnapshotState(
          isFromCache = isFromCache,
          hasPendingWrites = hasPendingWrites,
          lastServerSyncEpochMs = if (!isFromCache && !hasPendingWrites) System.currentTimeMillis() else null
        )
      }
    )
  }

  override suspend fun waitForPendingSync() {
    if (auth.currentUser == null) return
    runCatching {
      withTimeoutOrNull(8_000L) {
        db.waitForPendingWrites().await()
      }
    }
  }

  override fun observeStaff(depotId: String): Flow<List<StaffMember>> = flow {
    requireAuthUid()
    val col = db.collection("depots").document(depotId).collection("staff")
    emitAll(
      col.snapshots()
        .map { snapshot ->
          snapshot.toObjects(
            StaffMember::class.java,
            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
          ).sortedBy { it.serialNo }
        }
        .catch { error ->
          logStreamError(error, OperationType.LIST, col.path)
          throw error
        }
    )
  }

  override fun observeAssignments(depotId: String): Flow<List<ShiftSlotAssignment>> = flow {
    requireAuthUid()
    val col = db.collection("depots").document(depotId).collection("assignments")
    emitAll(
      col.snapshots()
        .map { snapshot ->
          snapshot.toObjects(
            ShiftSlotAssignment::class.java,
            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
          )
        }
        .catch { error ->
          logStreamError(error, OperationType.LIST, col.path)
          throw error
        }
    )
  }

  override fun observeLeaves(depotId: String): Flow<List<LeaveRecord>> = flow {
    requireAuthUid()
    val col = db.collection("depots").document(depotId).collection("leaves")
    emitAll(
      col.snapshots()
        .map { snapshot ->
          snapshot.toObjects(
            LeaveRecord::class.java,
            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
          ).sortedByDescending { it.startDate }
        }
        .catch { error ->
          logStreamError(error, OperationType.LIST, col.path)
          throw error
        }
    )
  }

  override fun observeOverrides(depotId: String): Flow<List<EmergencyOverrideLog>> = flow {
    requireAuthUid()
    val col = db.collection("depots").document(depotId).collection("overrides")
    emitAll(
      col.snapshots()
        .map { snapshot ->
          snapshot.toObjects(
            EmergencyOverrideLog::class.java,
            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
          ).sortedByDescending { it.createdAt?.seconds ?: 0L }
        }
        .catch { error ->
          logStreamError(error, OperationType.LIST, col.path)
          throw error
        }
    )
  }

  override fun observeDutyDrafts(depotId: String): Flow<List<DutyApprovalRequest>> = flow {
    requireAuthUid()
    val col = db.collection("depots").document(depotId).collection("dutyDrafts")
    emitAll(
      col.snapshots()
        .map { snapshot ->
          snapshot.toObjects(
            DutyApprovalRequest::class.java,
            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
          ).sortedWith(
            compareBy<DutyApprovalRequest>(
              { it.statusEnum != DutyDraftStatus.PENDING },
              { it.dutyDate }
            )
          )
        }
        .catch { error ->
          logStreamError(error, OperationType.LIST, col.path)
          throw error
        }
    )
  }

  override fun observeDutyLocks(depotId: String): Flow<List<DutyDateLock>> = flow {
    requireAuthUid()
    val col = db.collection("depots").document(depotId).collection("dutyLocks")
    emitAll(
      col.snapshots()
        .map { snapshot ->
          snapshot.toObjects(
            DutyDateLock::class.java,
            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
          )
        }
        .catch { error ->
          logStreamError(error, OperationType.LIST, col.path)
          throw error
        }
    )
  }

  override fun observeUserProfile(uid: String): Flow<DepotUserProfile?> = flow {
    requireAuthUid()
    val docRef = depotDoc().collection("userRoles").document(uid)
    emitAll(
      docRef.snapshots()
        .map { snapshot ->
          if (snapshot.exists()) {
            snapshot.toObject(
              DepotUserProfile::class.java,
              DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            )
          } else {
            null
          }
        }
        .catch { error ->
          logStreamError(error, OperationType.GET, docRef.path)
          throw error
        }
    )
  }

  override fun observeDepotSettings(depotId: String): Flow<DepotSettings> = flow {
    requireAuthUid()
    val docRef = db.collection("depots").document(depotId).collection("settings").document("config")
    emitAll(
      docRef.snapshots()
        .map { snapshot ->
          if (snapshot.exists()) {
            val base = runCatching {
              snapshot.toObject(
                DepotSettings::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
              )
            }.getOrNull() ?: DepotSettings(
              id = snapshot.id,
              allowTechnicianShare = snapshot.getBoolean("allowTechnicianShare") ?: true,
              showShunterForTechnicians = snapshot.getBoolean("showShunterForTechnicians") ?: false,
              includeShunterInExport = snapshot.getBoolean("includeShunterInExport") ?: false,
              importantContactsInitialized = snapshot.getBoolean("importantContactsInitialized") ?: false,
              rakesInitialized = snapshot.getBoolean("rakesInitialized") ?: false,
              rakesSchemaVersion = snapshot.getLong("rakesSchemaVersion") ?: 0L,
              updatedByUid = snapshot.getString("updatedByUid").orEmpty()
            )
            val parsedAccess = parseRoleMenuAccessFromSnapshot(snapshot.get("roleMenuAccess"))
            base.copy(roleMenuAccess = parsedAccess)
          } else {
            DepotSettings()
          }
        }
        .catch { error ->
          logStreamError(error, OperationType.GET, docRef.path)
          throw error
        }
    )
  }

  private fun parseRoleMenuAccessFromSnapshot(raw: Any?): Map<String, Map<String, String>> {
    val outer = raw as? Map<*, *> ?: return emptyMap()
    val parsed = linkedMapOf<String, Map<String, String>>()
    outer.forEach { (roleKey, menuValue) ->
      val roleName = roleKey as? String ?: return@forEach
      val inner = menuValue as? Map<*, *> ?: return@forEach
      val menuMap = linkedMapOf<String, String>()
      inner.forEach { (menuKey, levelValue) ->
        val menuName = menuKey as? String ?: return@forEach
        val levelName = levelValue as? String ?: return@forEach
        menuMap[menuName] = levelName
      }
      if (menuMap.isNotEmpty()) {
        parsed[roleName] = menuMap
      }
    }
    return RoleAccessPolicy.sanitizeRoleAccessMap(parsed)
  }

  override suspend fun updateAllowTechnicianShare(allowed: Boolean, updatedByUid: String) {
    requireAuthUid()
    val docRef = depotDoc().collection("settings").document("config")
    val actorUid = updatedByUid.trim().take(128).ifBlank { requireAuthUid() }
    try {
      val exists = docRef.existsOfflineAware()
      if (exists) {
        docRef.update(
          mapOf(
            "allowTechnicianShare" to allowed,
            "updatedByUid" to actorUid,
            "updatedAt" to FieldValue.serverTimestamp()
          )
        ).awaitLocalOrServer(OperationType.UPDATE, docRef.path)
      } else {
        docRef.set(
          mapOf(
            "allowTechnicianShare" to allowed,
            "showShunterForTechnicians" to false,
            "includeShunterInExport" to false,
            "updatedByUid" to actorUid,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
          )
        ).awaitLocalOrServer(OperationType.CREATE, docRef.path)
      }
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      logStreamError(e, OperationType.WRITE, docRef.path)
      throw e
    }
  }

  override suspend fun updateShunterSettings(
    showForTechnicians: Boolean,
    includeInExport: Boolean,
    updatedByUid: String
  ) {
    requireAuthUid()
    val docRef = depotDoc().collection("settings").document("config")
    val actorUid = updatedByUid.trim().take(128).ifBlank { requireAuthUid() }
    try {
      val exists = docRef.existsOfflineAware()
      if (exists) {
        docRef.update(
          mapOf(
            "showShunterForTechnicians" to showForTechnicians,
            "includeShunterInExport" to includeInExport,
            "updatedByUid" to actorUid,
            "updatedAt" to FieldValue.serverTimestamp()
          )
        ).awaitLocalOrServer(OperationType.UPDATE, docRef.path)
      } else {
        docRef.set(
          mapOf(
            "allowTechnicianShare" to true,
            "showShunterForTechnicians" to showForTechnicians,
            "includeShunterInExport" to includeInExport,
            "updatedByUid" to actorUid,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
          )
        ).awaitLocalOrServer(OperationType.CREATE, docRef.path)
      }
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      logStreamError(e, OperationType.WRITE, docRef.path)
      throw e
    }
  }

  override suspend fun updateRoleMenuAccess(
    roleMenuAccess: Map<String, Map<String, String>>,
    updatedByUid: String
  ) {
    requireAuthUid()
    val docRef = depotDoc().collection("settings").document("config")
    val actorUid = updatedByUid.trim().take(128).ifBlank { requireAuthUid() }
    val cleanAccess = RoleAccessPolicy.sanitizeRoleAccessMap(roleMenuAccess)
    try {
      val exists = docRef.existsOfflineAware()
      if (exists) {
        docRef.update(
          mapOf(
            "roleMenuAccess" to cleanAccess,
            "updatedByUid" to actorUid,
            "updatedAt" to FieldValue.serverTimestamp()
          )
        ).awaitLocalOrServer(OperationType.UPDATE, docRef.path)
      } else {
        docRef.set(
          mapOf(
            "allowTechnicianShare" to true,
            "showShunterForTechnicians" to false,
            "includeShunterInExport" to false,
            "roleMenuAccess" to cleanAccess,
            "updatedByUid" to actorUid,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
          )
        ).awaitLocalOrServer(OperationType.CREATE, docRef.path)
      }
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.WRITE, docRef.path)
      throw e
    }
  }

  override fun observeAppRelease(depotId: String): Flow<AppReleaseConfig> = flow {
    requireAuthUid()
    val docRef = db.collection("depots")
      .document(depotId)
      .collection("appRelease")
      .document(AppReleaseConfig.LATEST_DOC_ID)
    emitAll(
      docRef.snapshots()
        .map { snapshot ->
          if (snapshot.exists()) {
            runCatching {
              snapshot.toObject(
                AppReleaseConfig::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
              )
            }.getOrNull() ?: AppReleaseConfig(
              id = snapshot.id,
              latestVersionCode = (snapshot.get("latestVersionCode") as? Number)?.toInt() ?: 0,
              latestVersionName = snapshot.getString("latestVersionName").orEmpty(),
              minSupportedVersionCode = (snapshot.get("minSupportedVersionCode") as? Number)?.toInt() ?: 0,
              mandatory = snapshot.getBoolean("mandatory") ?: false,
              downloadUrl = snapshot.getString("downloadUrl").orEmpty(),
              releaseNotes = snapshot.getString("releaseNotes").orEmpty(),
              sha256Checksum = snapshot.getString("sha256Checksum").orEmpty(),
              publishedByUid = snapshot.getString("publishedByUid").orEmpty(),
              publishedByName = snapshot.getString("publishedByName").orEmpty(),
              createdAt = snapshot.getTimestamp("createdAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE),
              updatedAt = snapshot.getTimestamp("updatedAt", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            )
          } else {
            AppReleaseConfig()
          }
        }
        .catch { error ->
          logStreamError(error, OperationType.GET, docRef.path)
          throw error
        }
    )
  }

  override suspend fun publishAppRelease(release: AppReleaseConfig) {
    val authUid = requireAuthUid()
    val docRef = depotDoc().collection("appRelease").document(AppReleaseConfig.LATEST_DOC_ID)
    val actorUid = release.publishedByUid.trim().take(128).ifBlank { authUid }
    val actorName = release.publishedByName.trim().take(100).ifBlank { DepotRoleManager.ADMIN_DISPLAY_NAME }
    val cleanVersionCode = release.latestVersionCode.coerceIn(0, 999_999)
    val cleanMinCode = release.minSupportedVersionCode.coerceIn(0, cleanVersionCode)
    try {
      val exists = docRef.existsOfflineAware()
      val baseMap = mutableMapOf<String, Any>(
        "latestVersionCode" to cleanVersionCode,
        "latestVersionName" to release.latestVersionName.trim().take(40),
        "minSupportedVersionCode" to cleanMinCode,
        "mandatory" to release.mandatory,
        "downloadUrl" to release.downloadUrl.trim().take(500),
        "releaseNotes" to release.releaseNotes.trim().take(1500),
        "sha256Checksum" to release.sha256Checksum.trim().take(64),
        "publishedByUid" to actorUid,
        "publishedByName" to actorName,
        "updatedAt" to FieldValue.serverTimestamp()
      )
      if (exists) {
        docRef.update(baseMap).awaitLocalOrServer(OperationType.UPDATE, docRef.path)
      } else {
        baseMap["createdAt"] = FieldValue.serverTimestamp()
        docRef.set(baseMap).awaitLocalOrServer(OperationType.CREATE, docRef.path)
      }
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.WRITE, docRef.path)
      throw e
    }
  }

  override suspend fun clearAppRelease(updatedByUid: String, updatedByName: String) {
    publishAppRelease(
      AppReleaseConfig(
        id = AppReleaseConfig.LATEST_DOC_ID,
        latestVersionCode = 0,
        latestVersionName = "",
        minSupportedVersionCode = 0,
        mandatory = false,
        downloadUrl = "",
        releaseNotes = "",
        sha256Checksum = "",
        publishedByUid = updatedByUid,
        publishedByName = updatedByName
      )
    )
  }

  override fun observeLeaveAccounts(depotId: String): Flow<List<StaffLeaveAccount>> = flow {
    requireAuthUid()
    val col = db.collection("depots").document(depotId).collection("leaveAccounts")
    emitAll(
      col.snapshots()
        .map { snapshot ->
          snapshot.toObjects(
            StaffLeaveAccount::class.java,
            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
          )
        }
        .catch { error ->
          logStreamError(error, OperationType.LIST, col.path)
          throw error
        }
    )
  }

  override suspend fun saveLeaveAccount(
    account: StaffLeaveAccount,
    isTechnicianCorrectionRequest: Boolean
  ) {
    requireAuthUid()
    val staffId = account.staffId.ifBlank { account.id }.trim().take(128)
    require(staffId.isNotBlank()) { "Staff ID is required for leave account." }
    val docRef = depotDoc().collection("leaveAccounts").document(staffId)
    val cleanStaffName = account.staffName.trim().ifBlank { staffId }.take(100)
    val cleanReason = account.pendingLapReason.trim().take(300)
    val cleanReqUid = account.pendingLapRequestedByUid.trim().take(128)
    val cleanReviewer = account.pendingLapReviewedByName.trim().take(100)
    try {
      val cleanPendingLhap = account.pendingLhapBalance.coerceIn(0, 999)
      val exists = docRef.existsOfflineAware()
      if (isTechnicianCorrectionRequest && exists) {
        docRef.update(
          mapOf(
            "staffName" to cleanStaffName,
            "pendingLapBalance" to account.pendingLapBalance.coerceIn(0, 999),
            "pendingLhapBalance" to cleanPendingLhap,
            "pendingLapYear" to account.pendingLapYear,
            "pendingLapReason" to cleanReason,
            "pendingLapStatus" to account.pendingLapStatusEnum.name,
            "pendingLapRequestedByUid" to cleanReqUid,
            "updatedAt" to FieldValue.serverTimestamp()
          )
        ).awaitLocalOrServer(OperationType.UPDATE, docRef.path)
      } else if (exists) {
        docRef.update(
          mapOf(
            "staffName" to cleanStaffName,
            "baseYear" to account.baseYear,
            "lapOpeningBalance" to account.lapOpeningBalance.coerceIn(0, 999),
            "lhapOpeningBalance" to account.lhapOpeningBalance.coerceIn(0, 999),
            "lapSelfUpdated" to account.lapSelfUpdated,
            "pendingLapBalance" to account.pendingLapBalance.coerceIn(0, 999),
            "pendingLhapBalance" to cleanPendingLhap,
            "pendingLapYear" to account.pendingLapYear,
            "pendingLapReason" to cleanReason,
            "pendingLapStatus" to account.pendingLapStatusEnum.name,
            "pendingLapRequestedByUid" to cleanReqUid,
            "pendingLapReviewedByName" to cleanReviewer,
            "updatedAt" to FieldValue.serverTimestamp()
          )
        ).awaitLocalOrServer(OperationType.UPDATE, docRef.path)
      } else {
        docRef.set(
          mapOf(
            "staffId" to staffId,
            "staffName" to cleanStaffName,
            "baseYear" to account.baseYear,
            "lapOpeningBalance" to account.lapOpeningBalance.coerceIn(0, 999),
            "lhapOpeningBalance" to account.lhapOpeningBalance.coerceIn(0, 999),
            "lapSelfUpdated" to account.lapSelfUpdated,
            "pendingLapBalance" to account.pendingLapBalance.coerceIn(0, 999),
            "pendingLhapBalance" to cleanPendingLhap,
            "pendingLapYear" to account.pendingLapYear,
            "pendingLapReason" to cleanReason,
            "pendingLapStatus" to account.pendingLapStatusEnum.name,
            "pendingLapRequestedByUid" to cleanReqUid,
            "pendingLapReviewedByName" to cleanReviewer,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
          )
        ).awaitLocalOrServer(OperationType.CREATE, docRef.path)
      }
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.WRITE, docRef.path)
      throw e
    }
  }

  override fun observeDutyPermissions(depotId: String): Flow<List<DutyPermissionRecord>> = flow {
    requireAuthUid()
    val col = db.collection("depots").document(depotId).collection("dutyPermissions")
    emitAll(
      col.snapshots()
        .map { snapshot ->
          snapshot.toObjects(
            DutyPermissionRecord::class.java,
            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
          ).sortedWith(
            compareByDescending<DutyPermissionRecord> { it.date }
              .thenByDescending { it.createdAt?.seconds ?: 0L }
          )
        }
        .catch { error ->
          logStreamError(error, OperationType.LIST, col.path)
          throw error
        }
    )
  }

  override suspend fun saveDutyPermission(
    record: DutyPermissionRecord,
    existingDocPresent: Boolean
  ): String {
    val authUid = requireAuthUid()
    val id = record.id.takeIf { it.isNotBlank() }
      ?: "perm_${UUID.randomUUID().toString().replace("-", "").take(16)}"
    val docRef = depotDoc().collection("dutyPermissions").document(id)
    val cleanStaffId = record.staffId.trim().take(128)
    val cleanStaffName = record.staffName.trim().ifBlank { cleanStaffId }.take(100)
    val cleanHours = record.hours.coerceIn(0.5, 12.0)
    val cleanReason = record.reason.trim().take(300)
    val cleanGrantedByUid = record.grantedByUid.trim().ifBlank { authUid }.take(128)
    val cleanGrantedByName = record.grantedByName.trim().ifBlank { "Supervisor" }.take(100)
    try {
      val shouldUpdate = existingDocPresent || docRef.existsOfflineAware()
      if (shouldUpdate) {
        docRef.update(
          mapOf(
            "staffId" to cleanStaffId,
            "staffName" to cleanStaffName,
            "date" to record.date,
            "shiftType" to record.shiftTypeEnum.name,
            "hours" to cleanHours,
            "reliefType" to record.reliefTypeEnum.name,
            "reason" to cleanReason,
            "grantedByUid" to cleanGrantedByUid,
            "grantedByName" to cleanGrantedByName,
            "updatedAt" to FieldValue.serverTimestamp()
          )
        ).awaitLocalOrServer(OperationType.UPDATE, docRef.path)
      } else {
        docRef.set(
          mapOf(
            "userId" to depotId,
            "staffId" to cleanStaffId,
            "staffName" to cleanStaffName,
            "date" to record.date,
            "shiftType" to record.shiftTypeEnum.name,
            "hours" to cleanHours,
            "reliefType" to record.reliefTypeEnum.name,
            "reason" to cleanReason,
            "grantedByUid" to cleanGrantedByUid,
            "grantedByName" to cleanGrantedByName,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
          )
        ).awaitLocalOrServer(OperationType.CREATE, docRef.path)
      }
      return id
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.WRITE, docRef.path)
      throw e
    }
  }

  override fun observeImportantContacts(depotId: String): Flow<List<ImportantContact>> = flow {
    requireAuthUid()
    val col = db.collection("depots").document(depotId).collection("importantContacts")
    emitAll(
      col.snapshots()
        .map { snapshot ->
          snapshot.toObjects(
            ImportantContact::class.java,
            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
          ).sortedWith(
            compareBy<ImportantContact>({ it.sortOrder }, { it.label.uppercase() })
          )
        }
        .catch { error ->
          logStreamError(error, OperationType.LIST, col.path)
          throw error
        }
    )
  }

  override suspend fun seedDefaultImportantContactsIfNeeded(updatedByUid: String): Boolean {
    val authUid = requireAuthUid()
    val actorUid = updatedByUid.trim().take(128).ifBlank { authUid }
    val settingsRef = depotDoc().collection("settings").document("config")
    val col = depotDoc().collection("importantContacts")
    try {
      // Only seed default contacts when server state is verified (avoid duplicate offline seeding on empty cache)
      val settingsSnap = withTimeoutOrNull(4_000L) {
        runCatching { settingsRef.get(Source.SERVER).await() }.getOrNull()
      }
      val alreadyInitialized = settingsSnap?.getBoolean("importantContactsInitialized") == true
      if (alreadyInitialized) return false

      val existingDocs = withTimeoutOrNull(4_000L) {
        runCatching { col.limit(1).get(Source.SERVER).await() }.getOrNull()
      } ?: return false

      val batch = db.batch()
      val didSeedDefaults = existingDocs.isEmpty
      if (didSeedDefaults) {
        IMPORTANT_DEPOT_CONTACTS.forEach { contact ->
          val docRef = col.document(contact.id)
          batch.set(
            docRef,
            mapOf(
              "userId" to depotId,
              "label" to contact.label.trim().take(80),
              "phoneNumber" to contact.phoneNumber.trim().take(20),
              "category" to contact.category.trim().take(40),
              "sortOrder" to contact.sortOrder,
              "createdAt" to FieldValue.serverTimestamp(),
              "updatedAt" to FieldValue.serverTimestamp()
            )
          )
        }
      }
      if (settingsSnap != null && settingsSnap.exists()) {
        batch.update(
          settingsRef,
          mapOf(
            "importantContactsInitialized" to true,
            "updatedByUid" to actorUid,
            "updatedAt" to FieldValue.serverTimestamp()
          )
        )
      } else {
        batch.set(
          settingsRef,
          mapOf(
            "allowTechnicianShare" to true,
            "showShunterForTechnicians" to false,
            "includeShunterInExport" to false,
            "importantContactsInitialized" to true,
            "updatedByUid" to actorUid,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
          )
        )
      }
      batch.commit().awaitLocalOrServer(OperationType.WRITE, col.path)
      return didSeedDefaults
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      logStreamError(e, OperationType.WRITE, col.path)
      throw e
    }
  }

  override suspend fun addImportantContact(
    label: String,
    phoneNumber: String,
    category: String,
    sortOrder: Long,
    updatedByUid: String
  ): String {
    requireAuthUid()
    seedDefaultImportantContactsIfNeeded(updatedByUid)
    val id = "contact_${UUID.randomUUID().toString().replace("-", "").take(16)}"
    val docRef = depotDoc().collection("importantContacts").document(id)
    val cleanLabel = label.trim().uppercase().take(80)
    val cleanPhone = phoneNumber.trim().take(20)
    val cleanCategory = category.trim().ifBlank { "Control" }.take(40)
    val cleanOrder = sortOrder.coerceIn(0L, 999999L)
    try {
      docRef.set(
        mapOf(
          "userId" to depotId,
          "label" to cleanLabel,
          "phoneNumber" to cleanPhone,
          "category" to cleanCategory,
          "sortOrder" to cleanOrder,
          "createdAt" to FieldValue.serverTimestamp(),
          "updatedAt" to FieldValue.serverTimestamp()
        )
      ).awaitLocalOrServer(OperationType.CREATE, docRef.path)
      return id
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.WRITE, docRef.path)
      throw e
    }
  }

  override suspend fun deleteImportantContact(contactId: String, updatedByUid: String) {
    requireAuthUid()
    seedDefaultImportantContactsIfNeeded(updatedByUid)
    val cleanId = contactId.trim()
    if (cleanId.isBlank()) return
    val docRef = depotDoc().collection("importantContacts").document(cleanId)
    try {
      docRef.delete().awaitLocalOrServer(OperationType.DELETE, docRef.path)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.DELETE, docRef.path)
      throw e
    }
  }

  override fun observeRakes(depotId: String): Flow<List<RakeFormation>> = flow {
    requireAuthUid()
    val col = db.collection("depots").document(depotId).collection("rakes")
    emitAll(
      col.snapshots()
        .map { snapshot ->
          snapshot.toObjects(
            RakeFormation::class.java,
            DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
          ).sortedWith(compareBy<RakeFormation>({ it.sortOrder }, { it.rakeName.uppercase() }))
        }
        .catch { error ->
          logStreamError(error, OperationType.LIST, col.path)
          throw error
        }
    )
  }

  override suspend fun seedDefaultRakesIfNeeded(
    updatedByUid: String,
    updatedByName: String
  ): Boolean {
    val authUid = requireAuthUid()
    val actorUid = updatedByUid.trim().take(128).ifBlank { authUid }
    val actorName = updatedByName.trim().take(100).ifBlank { "Depot Control" }
    val settingsRef = depotDoc().collection("settings").document("config")
    val col = depotDoc().collection("rakes")
    try {
      val settingsSnap = withTimeoutOrNull(4_000L) {
        runCatching { settingsRef.get(Source.SERVER).await() }.getOrNull()
      }
      val alreadyInitialized = settingsSnap?.getBoolean("rakesInitialized") == true
      val currentSchemaVersion = settingsSnap?.getLong("rakesSchemaVersion") ?: 0L
      if (alreadyInitialized && currentSchemaVersion >= RakeFormationEngine.CURRENT_RAKES_SCHEMA_VERSION) {
        return false
      }

      if (alreadyInitialized) {
        val existingSnap = withTimeoutOrNull(5_000L) {
          runCatching { col.get(Source.SERVER).await() }.getOrNull()
        } ?: return false
        val batch = db.batch()
        var migratedAny = false
        existingSnap.toObjects(
          RakeFormation::class.java,
          DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
        ).forEach { rake ->
          val migrated = RakeFormationEngine.migrateLegacyCoachRolesIfNeeded(rake)
          if (migrated != null && rake.id.isNotBlank()) {
            migratedAny = true
            batch.update(
              col.document(rake.id),
              mapOf(
                "rakeName" to migrated.rakeName.trim().take(80),
                "coaches" to RakeFormationEngine.reindexCoaches(migrated.sortedCoaches).map { it.toFirestoreMap() },
                "remarks" to migrated.remarks.trim().take(300),
                "updatedByUid" to actorUid,
                "updatedByName" to actorName,
                "updatedAt" to FieldValue.serverTimestamp()
              )
            )
          }
        }
        if (settingsSnap != null && settingsSnap.exists()) {
          batch.update(
            settingsRef,
            mapOf(
              "rakesSchemaVersion" to RakeFormationEngine.CURRENT_RAKES_SCHEMA_VERSION,
              "updatedByUid" to actorUid,
              "updatedAt" to FieldValue.serverTimestamp()
            )
          )
        }
        batch.commit().awaitLocalOrServer(OperationType.WRITE, col.path)
        return migratedAny
      }

      val existingDocs = withTimeoutOrNull(4_000L) {
        runCatching { col.limit(1).get(Source.SERVER).await() }.getOrNull()
      } ?: return false

      val batch = db.batch()
      val didSeedDefaults = existingDocs.isEmpty
      if (didSeedDefaults) {
        DefaultRakeFormations.all.forEach { rake ->
          val docRef = col.document(rake.id)
          val coaches = RakeFormationEngine.reindexCoaches(rake.sortedCoaches)
          batch.set(
            docRef,
            mapOf(
              "userId" to depotId,
              "rakeName" to rake.rakeName.trim().take(80),
              "category" to rake.categoryEnum.name,
              "make" to rake.make.trim().take(30),
              "tcasFitted" to rake.tcasFitted,
              "startEnd" to rake.startEnd.trim().take(20),
              "finishEnd" to rake.finishEnd.trim().take(20),
              "remarks" to rake.remarks.trim().take(300),
              "sortOrder" to rake.sortOrder.coerceIn(0L, 999999L),
              "coaches" to coaches.map { it.toFirestoreMap() },
              "updatedByUid" to actorUid,
              "updatedByName" to actorName,
              "createdAt" to FieldValue.serverTimestamp(),
              "updatedAt" to FieldValue.serverTimestamp()
            )
          )
        }
      }
      if (settingsSnap != null && settingsSnap.exists()) {
        batch.update(
          settingsRef,
          mapOf(
            "rakesInitialized" to true,
            "rakesSchemaVersion" to RakeFormationEngine.CURRENT_RAKES_SCHEMA_VERSION,
            "updatedByUid" to actorUid,
            "updatedAt" to FieldValue.serverTimestamp()
          )
        )
      } else {
        batch.set(
          settingsRef,
          mapOf(
            "allowTechnicianShare" to true,
            "showShunterForTechnicians" to false,
            "includeShunterInExport" to false,
            "rakesInitialized" to true,
            "rakesSchemaVersion" to RakeFormationEngine.CURRENT_RAKES_SCHEMA_VERSION,
            "updatedByUid" to actorUid,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
          )
        )
      }
      batch.commit().awaitLocalOrServer(OperationType.WRITE, col.path)
      return didSeedDefaults
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      logStreamError(e, OperationType.WRITE, col.path)
      throw e
    }
  }

  override suspend fun saveRakeFormation(
    rake: RakeFormation,
    existingDocPresent: Boolean,
    updatedByUid: String,
    updatedByName: String
  ): String {
    val authUid = requireAuthUid()
    val actorUid = updatedByUid.trim().take(128).ifBlank { authUid }
    val actorName = updatedByName.trim().take(100).ifBlank { "Depot Control" }
    seedDefaultRakesIfNeeded(actorUid, actorName)
    val id = rake.id.trim().takeIf { it.isNotBlank() }
      ?: "rake_${UUID.randomUUID().toString().replace("-", "").take(16)}"
    val docRef = depotDoc().collection("rakes").document(id)
    val cleanName = rake.rakeName.trim().take(80)
    val cleanMake = rake.make.trim().take(30)
    val cleanStart = rake.startEnd.trim().take(20)
    val cleanFinish = rake.finishEnd.trim().take(20)
    val cleanRemarks = rake.remarks.trim().take(300)
    val cleanSortOrder = rake.sortOrder.coerceIn(0L, 999999L)
    val coaches = RakeFormationEngine.reindexCoaches(rake.sortedCoaches).map { it.toFirestoreMap() }
    try {
      val exists = existingDocPresent || docRef.existsOfflineAware()
      if (exists) {
        docRef.update(
          mapOf(
            "rakeName" to cleanName,
            "category" to rake.categoryEnum.name,
            "make" to cleanMake,
            "tcasFitted" to rake.tcasFitted,
            "startEnd" to cleanStart,
            "finishEnd" to cleanFinish,
            "remarks" to cleanRemarks,
            "sortOrder" to cleanSortOrder,
            "coaches" to coaches,
            "updatedByUid" to actorUid,
            "updatedByName" to actorName,
            "updatedAt" to FieldValue.serverTimestamp()
          )
        ).awaitLocalOrServer(OperationType.UPDATE, docRef.path)
      } else {
        docRef.set(
          mapOf(
            "userId" to depotId,
            "rakeName" to cleanName,
            "category" to rake.categoryEnum.name,
            "make" to cleanMake,
            "tcasFitted" to rake.tcasFitted,
            "startEnd" to cleanStart,
            "finishEnd" to cleanFinish,
            "remarks" to cleanRemarks,
            "sortOrder" to cleanSortOrder,
            "coaches" to coaches,
            "updatedByUid" to actorUid,
            "updatedByName" to actorName,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
          )
        ).awaitLocalOrServer(OperationType.CREATE, docRef.path)
      }
      return id
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.WRITE, docRef.path)
      throw e
    }
  }

  override suspend fun deleteRakeFormation(
    rakeId: String,
    updatedByUid: String,
    updatedByName: String
  ) {
    val authUid = requireAuthUid()
    val actorUid = updatedByUid.trim().take(128).ifBlank { authUid }
    val actorName = updatedByName.trim().take(100).ifBlank { "Depot Control" }
    seedDefaultRakesIfNeeded(actorUid, actorName)
    val cleanId = rakeId.trim()
    if (cleanId.isBlank()) return
    val docRef = depotDoc().collection("rakes").document(cleanId)
    try {
      docRef.delete().awaitLocalOrServer(OperationType.DELETE, docRef.path)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.DELETE, docRef.path)
      throw e
    }
  }

  override suspend fun deleteDutyPermission(permissionId: String) {
    requireAuthUid()
    val docRef = depotDoc().collection("dutyPermissions").document(permissionId)
    try {
      docRef.delete().awaitLocalOrServer(OperationType.DELETE, docRef.path)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.DELETE, docRef.path)
      throw e
    }
  }

  private fun normalizeEmailKey(email: String): String = email.trim().lowercase().take(200)

  private fun isValidEmailKey(emailKey: String): Boolean =
    emailKey.length in 3..200 && emailKey.matches(EMAIL_KEY_REGEX)

  private fun inviteDocRef(emailKey: String) =
    depotDoc().collection("roleInvites").document(emailKey)

  private suspend fun upsertRoleInvite(
    emailKey: String,
    username: String,
    displayName: String,
    role: UserRole,
    staffId: String,
    updatedByUid: String
  ) {
    if (!isValidEmailKey(emailKey)) return
    val ref = inviteDocRef(emailKey)
    val exists = runCatching { ref.get().await().exists() }.getOrDefault(false)
    if (exists) {
      ref.update(
        mapOf(
          "username" to username.take(64),
          "displayName" to displayName.take(100),
          "role" to role.name,
          "staffId" to staffId.take(128),
          "updatedByUid" to updatedByUid.take(128),
          "updatedAt" to FieldValue.serverTimestamp()
        )
      ).await()
    } else {
      ref.set(
        mapOf(
          "email" to emailKey,
          "username" to username.take(64),
          "displayName" to displayName.take(100),
          "role" to role.name,
          "staffId" to staffId.take(128),
          "updatedByUid" to updatedByUid.take(128),
          "createdAt" to FieldValue.serverTimestamp(),
          "updatedAt" to FieldValue.serverTimestamp()
        )
      ).await()
    }
  }

  override fun observeAllUserProfiles(): Flow<List<DepotUserProfile>> = flow {
    requireAuthUid()
    val rolesCol = depotDoc().collection("userRoles")
    val invitesCol = depotDoc().collection("roleInvites")
    val rolesFlow = rolesCol.snapshots()
      .map { snapshot ->
        snapshot.toObjects(
          DepotUserProfile::class.java,
          DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
        )
      }
      .catch { error ->
        logStreamError(error, OperationType.LIST, rolesCol.path)
        throw error
      }
    val invitesFlow = invitesCol.snapshots()
      .map { snapshot ->
        snapshot.toObjects(
          RoleInvite::class.java,
          DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
        )
      }
      .catch { error ->
        logStreamError(error, OperationType.LIST, invitesCol.path)
        emit(emptyList())
      }

    emitAll(
      combine(rolesFlow, invitesFlow) { activeProfiles, invites ->
        val registeredEmails = activeProfiles
          .mapNotNull { it.email.trim().lowercase().takeIf { e -> e.isNotBlank() } }
          .toHashSet()
        val pendingInvites = invites
          .filter { invite ->
            val key = normalizeEmailKey(invite.email.ifBlank { invite.id })
            key.isNotBlank() && key !in registeredEmails
          }
          .map { invite ->
            val key = normalizeEmailKey(invite.email.ifBlank { invite.id })
            val inviteUid = "${DepotUserProfile.CLOUD_INVITE_UID_PREFIX}$key"
            DepotUserProfile(
              id = inviteUid,
              uid = inviteUid,
              username = invite.username,
              email = key,
              displayName = invite.displayName.ifBlank { key },
              role = invite.role,
              staffId = invite.staffId,
              updatedByUid = invite.updatedByUid,
              createdAt = invite.createdAt,
              updatedAt = invite.updatedAt
            )
          }
        (activeProfiles + pendingInvites).sortedBy { it.displayName.uppercase() }
      }
    )
  }

  override suspend fun ensureUserProfile(
    uid: String,
    email: String,
    displayName: String
  ): DepotUserProfile {
    requireAuthUid()
    val docRef = depotDoc().collection("userRoles").document(uid)
    return try {
      val snapshot = docRef.getOfflineAware()
      val effectiveAuthEmail = email.trim().ifBlank { auth.currentUser?.email?.trim().orEmpty() }
      val cleanEmail = effectiveAuthEmail.take(200)
      val emailKey = normalizeEmailKey(cleanEmail)
      val cleanName = displayName.trim().ifBlank { cleanEmail.ifBlank { "Depot Member" } }.take(100)
      val isBootstrapAdmin = cleanEmail.equals(DepotRoleManager.ADMIN_EMAIL, ignoreCase = true)

      val matchingInvite = if (isValidEmailKey(emailKey)) {
        runCatching {
          val invSnap = inviteDocRef(emailKey).getOfflineAware(timeoutMs = 2_000L)
          if (invSnap != null && invSnap.exists()) {
            invSnap.toObject(RoleInvite::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
          } else {
            null
          }
        }.getOrNull()
      } else {
        null
      }

      if (snapshot == null || !snapshot.exists()) {
        val assignedRole = when {
          isBootstrapAdmin -> UserRole.ADMIN
          matchingInvite != null -> matchingInvite.roleEnum
          else -> UserRole.TECHNICIAN
        }
        val assignedStaffId = matchingInvite?.staffId?.trim()?.take(128).orEmpty()
        val assignedUsername = matchingInvite?.username?.trim()?.lowercase()?.take(64).orEmpty()
        val assignedDisplayName = matchingInvite?.displayName?.trim()?.takeIf { it.isNotBlank() }?.take(100)
          ?: cleanName

        val createPayload = mutableMapOf<String, Any>(
          "uid" to uid,
          "email" to cleanEmail,
          "displayName" to assignedDisplayName,
          "role" to assignedRole.name,
          "staffId" to assignedStaffId,
          "updatedByUid" to uid,
          "createdAt" to FieldValue.serverTimestamp(),
          "updatedAt" to FieldValue.serverTimestamp()
        )
        if (assignedUsername.isNotBlank()) {
          createPayload["username"] = assignedUsername
        }
        docRef.set(createPayload).awaitLocalOrServer(OperationType.CREATE, docRef.path)
        DepotUserProfile(
          id = uid,
          uid = uid,
          username = assignedUsername,
          email = cleanEmail,
          displayName = assignedDisplayName,
          role = assignedRole.name,
          staffId = assignedStaffId,
          updatedByUid = uid
        )
      } else {
        val existing = snapshot.toObject(DepotUserProfile::class.java)
          ?: DepotUserProfile(id = uid, uid = uid, email = cleanEmail, displayName = cleanName)
        val effectiveIsAdmin = isBootstrapAdmin || existing.email.equals(DepotRoleManager.ADMIN_EMAIL, ignoreCase = true)
        val needsAdminUpgrade = effectiveIsAdmin && existing.roleEnum != UserRole.ADMIN
        val needsInviteSync = !effectiveIsAdmin && matchingInvite != null && (
          existing.roleEnum != matchingInvite.roleEnum ||
            existing.staffId != matchingInvite.staffId ||
            (matchingInvite.username.isNotBlank() && existing.username != matchingInvite.username)
          )
        val effectiveName = matchingInvite?.displayName?.trim()?.takeIf { it.isNotBlank() }?.take(100)
          ?: cleanName
        val needsMetadataUpdate = existing.displayName != effectiveName ||
          (cleanEmail.isNotBlank() && existing.email != cleanEmail)

        if (needsAdminUpgrade) {
          docRef.update(
            mapOf(
              "displayName" to cleanName,
              "email" to cleanEmail.ifBlank { existing.email },
              "role" to UserRole.ADMIN.name,
              "updatedByUid" to uid,
              "updatedAt" to FieldValue.serverTimestamp()
            )
          ).awaitLocalOrServer(OperationType.UPDATE, docRef.path)
          existing.copy(
            role = UserRole.ADMIN.name,
            displayName = cleanName,
            email = cleanEmail.ifBlank { existing.email },
            updatedByUid = uid
          )
        } else if (needsInviteSync) {
          val invite = checkNotNull(matchingInvite)
          val inviteUsername = invite.username.trim().lowercase().take(64)
          val inviteStaffId = invite.staffId.trim().take(128)
          val updates = mutableMapOf<String, Any>(
            "role" to invite.roleEnum.name,
            "staffId" to inviteStaffId,
            "displayName" to effectiveName,
            "email" to cleanEmail.ifBlank { existing.email },
            "updatedAt" to FieldValue.serverTimestamp()
          )
          if (inviteUsername.isNotBlank()) {
            updates["username"] = inviteUsername
          }
          docRef.update(updates).awaitLocalOrServer(OperationType.UPDATE, docRef.path)
          existing.copy(
            role = invite.roleEnum.name,
            staffId = inviteStaffId,
            username = inviteUsername.ifBlank { existing.username },
            displayName = effectiveName,
            email = cleanEmail.ifBlank { existing.email }
          )
        } else if (needsMetadataUpdate) {
          docRef.update(
            mapOf(
              "displayName" to cleanName,
              "email" to cleanEmail.ifBlank { existing.email },
              "updatedAt" to FieldValue.serverTimestamp()
            )
          ).awaitLocalOrServer(OperationType.UPDATE, docRef.path)
          existing.copy(displayName = cleanName, email = cleanEmail.ifBlank { existing.email })
        } else {
          existing
        }
      }
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.WRITE, docRef.path)
      throw e
    }
  }

  override suspend fun updateUserRole(
    targetUid: String,
    role: UserRole,
    staffId: String,
    updatedByUid: String
  ) {
    val authUid = requireAuthUid()
    val cleanStaffId = staffId.trim().take(128)
    val actorUid = updatedByUid.ifBlank { authUid }.take(128)
    if (targetUid.startsWith(DepotUserProfile.CLOUD_INVITE_UID_PREFIX)) {
      val emailKey = normalizeEmailKey(targetUid.removePrefix(DepotUserProfile.CLOUD_INVITE_UID_PREFIX))
      val invRef = inviteDocRef(emailKey)
      try {
        invRef.update(
          mapOf(
            "role" to role.name,
            "staffId" to cleanStaffId,
            "updatedByUid" to actorUid,
            "updatedAt" to FieldValue.serverTimestamp()
          )
        ).await()
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        handleFirestoreError(e, OperationType.UPDATE, invRef.path)
        throw e
      }
      return
    }

    val docRef = depotDoc().collection("userRoles").document(targetUid)
    try {
      val currentSnap = runCatching { docRef.get().await() }.getOrNull()
      val existingProfile = currentSnap?.toObject(DepotUserProfile::class.java)
      docRef.update(
        mapOf(
          "role" to role.name,
          "staffId" to cleanStaffId,
          "updatedByUid" to actorUid,
          "updatedAt" to FieldValue.serverTimestamp()
        )
      ).await()
      val emailKey = normalizeEmailKey(existingProfile?.email.orEmpty())
      if (isValidEmailKey(emailKey)) {
        runCatching {
          upsertRoleInvite(
            emailKey = emailKey,
            username = existingProfile?.username.orEmpty(),
            displayName = existingProfile?.displayName?.ifBlank { emailKey } ?: emailKey,
            role = role,
            staffId = cleanStaffId,
            updatedByUid = actorUid
          )
        }
      }
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, docRef.path)
      throw e
    }
  }

  override suspend fun authenticateLocalUser(username: String, password: String): DepotUserProfile? = null

  override suspend fun createUserAccount(
    username: String,
    password: String,
    displayName: String,
    email: String,
    role: UserRole,
    staffId: String,
    createdByUid: String
  ): DepotUserProfile {
    val authUid = requireAuthUid()
    val cleanEmail = normalizeEmailKey(email)
    require(isValidEmailKey(cleanEmail)) {
      "Enter the user's Google Email (e.g. name@gmail.com) so they can sign in on their phone."
    }
    val derivedUsername = username.trim().lowercase().ifBlank {
      cleanEmail.substringBefore("@").replace(Regex("[^a-z0-9._-]"), "")
    }.take(64)
    val cleanName = displayName.trim().take(100)
    require(cleanName.isNotBlank()) { "Full name is required." }

    val cleanStaffId = staffId.trim().take(128)
    val actorUid = createdByUid.ifBlank { authUid }.take(128)
    val effectiveRole = if (DepotRoleManager.isBootstrapAdminEmail(cleanEmail, cleanName)) {
      UserRole.ADMIN
    } else {
      role
    }

    try {
      // Save/update the verified email role invite so cross-device Google Sign-In claims it automatically.
      upsertRoleInvite(
        emailKey = cleanEmail,
        username = derivedUsername,
        displayName = cleanName,
        role = effectiveRole,
        staffId = cleanStaffId,
        updatedByUid = actorUid
      )

      // If this user has ALREADY signed in with Google on another phone, update their live userRoles doc too.
      val existingUserDoc = runCatching {
        depotDoc().collection("userRoles").get().await().documents.firstOrNull { doc ->
          doc.getString("email")?.trim()?.equals(cleanEmail, ignoreCase = true) == true
        }
      }.getOrNull()

      if (existingUserDoc != null) {
        existingUserDoc.reference.update(
          mapOf(
            "username" to derivedUsername,
            "displayName" to cleanName,
            "email" to cleanEmail,
            "role" to effectiveRole.name,
            "staffId" to cleanStaffId,
            "updatedByUid" to actorUid,
            "updatedAt" to FieldValue.serverTimestamp()
          )
        ).await()
        return DepotUserProfile(
          id = existingUserDoc.id,
          uid = existingUserDoc.id,
          username = derivedUsername,
          email = cleanEmail,
          displayName = cleanName,
          role = effectiveRole.name,
          staffId = cleanStaffId,
          updatedByUid = actorUid
        )
      }

      val inviteUid = "${DepotUserProfile.CLOUD_INVITE_UID_PREFIX}$cleanEmail"
      return DepotUserProfile(
        id = inviteUid,
        uid = inviteUid,
        username = derivedUsername,
        email = cleanEmail,
        displayName = cleanName,
        role = effectiveRole.name,
        staffId = cleanStaffId,
        updatedByUid = actorUid
      )
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.CREATE, inviteDocRef(cleanEmail).path)
      throw e
    }
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
    val authUid = requireAuthUid()
    val cleanEmail = normalizeEmailKey(email)
    val cleanUsername = username.trim().lowercase().take(64)
    val cleanName = displayName.trim().take(100)
    val cleanStaffId = staffId.trim().take(128)
    val actorUid = updatedByUid.ifBlank { authUid }.take(128)
    require(cleanName.isNotBlank()) { "Full name is required." }

    if (targetUid.startsWith(DepotUserProfile.CLOUD_INVITE_UID_PREFIX)) {
      val oldEmailKey = normalizeEmailKey(targetUid.removePrefix(DepotUserProfile.CLOUD_INVITE_UID_PREFIX))
      val targetEmailKey = if (isValidEmailKey(cleanEmail)) cleanEmail else oldEmailKey
      require(isValidEmailKey(targetEmailKey)) { "A valid Google Email is required." }
      try {
        upsertRoleInvite(
          emailKey = targetEmailKey,
          username = cleanUsername,
          displayName = cleanName,
          role = role,
          staffId = cleanStaffId,
          updatedByUid = actorUid
        )
        if (targetEmailKey != oldEmailKey && isValidEmailKey(oldEmailKey)) {
          runCatching { inviteDocRef(oldEmailKey).delete().await() }
        }
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        handleFirestoreError(e, OperationType.UPDATE, inviteDocRef(targetEmailKey).path)
        throw e
      }
      return
    }

    val docRef = depotDoc().collection("userRoles").document(targetUid)
    try {
      docRef.update(
        mapOf(
          "username" to cleanUsername,
          "displayName" to cleanName,
          "email" to cleanEmail,
          "role" to role.name,
          "staffId" to cleanStaffId,
          "updatedByUid" to actorUid,
          "updatedAt" to FieldValue.serverTimestamp()
        )
      ).await()
      if (isValidEmailKey(cleanEmail)) {
        runCatching {
          upsertRoleInvite(
            emailKey = cleanEmail,
            username = cleanUsername,
            displayName = cleanName,
            role = role,
            staffId = cleanStaffId,
            updatedByUid = actorUid
          )
        }
      }
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, docRef.path)
      throw e
    }
  }

  override suspend fun deleteUserAccount(targetUid: String) {
    val authUid = requireAuthUid()
    require(targetUid != authUid) { "Cannot delete the currently signed-in account." }
    if (targetUid.startsWith(DepotUserProfile.CLOUD_INVITE_UID_PREFIX)) {
      val emailKey = normalizeEmailKey(targetUid.removePrefix(DepotUserProfile.CLOUD_INVITE_UID_PREFIX))
      val invRef = inviteDocRef(emailKey)
      try {
        invRef.delete().await()
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        handleFirestoreError(e, OperationType.DELETE, invRef.path)
        throw e
      }
      return
    }

    val docRef = depotDoc().collection("userRoles").document(targetUid)
    try {
      val existingSnap = runCatching { docRef.get().await() }.getOrNull()
      val existingEmail = existingSnap?.getString("email").orEmpty()
      val existingUsername = existingSnap?.getString("username")?.trim()?.lowercase().orEmpty()
      docRef.delete().await()
      val emailKey = normalizeEmailKey(existingEmail)
      if (isValidEmailKey(emailKey)) {
        runCatching { inviteDocRef(emailKey).delete().await() }
      }
      if (existingUsername.isNotBlank()) {
        runCatching { depotDoc().collection("usernames").document(existingUsername).delete().await() }
      }
      runCatching { depotDoc().collection("userCredentials").document(targetUid).delete().await() }
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.DELETE, docRef.path)
      throw e
    }
  }

  override suspend fun changeUserPassword(
    targetUid: String,
    currentPassword: String,
    newPassword: String
  ): Boolean {
    val authUid = requireAuthUid()
    if (authUid != targetUid || newPassword.length < 6) return false
    val credRef = depotDoc().collection("userCredentials").document(authUid)
    return try {
      val credSnap = credRef.get().await()
      if (!credSnap.exists()) return false
      val existingSalt = credSnap.getString("passwordSalt").orEmpty()
      val existingHash = credSnap.getString("passwordHash").orEmpty()
      if (!com.example.auth.PasswordHasher.verifyPassword(currentPassword, existingSalt, existingHash)) {
        return false
      }
      val (newSalt, newHash) = com.example.auth.PasswordHasher.hashPassword(newPassword)
      credRef.update(
        mapOf(
          "passwordSalt" to newSalt,
          "passwordHash" to newHash,
          "updatedAt" to FieldValue.serverTimestamp()
        )
      ).await()
      true
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, credRef.path)
      false
    }
  }

  override suspend fun seedDefaultRosterIfEmpty(): Boolean {
    requireAuthUid()
    val staffCol = depotDoc().collection("staff")
    return try {
      // Strictly verify emptiness against SERVER so an empty offline cache never queues duplicate seed writes
      val existing = withTimeoutOrNull(5_000L) {
        runCatching { staffCol.limit(1).get(Source.SERVER).await() }.getOrNull()
      } ?: return false
      if (!existing.isEmpty) return false

      val initial = DefaultPpioRoster.initialStaff(depotId)
      val batch = db.batch()
      for (member in initial) {
        val docRef = staffCol.document(member.id)
        batch.set(
          docRef,
          mapOf(
            "userId" to depotId,
            "serialNo" to member.serialNo,
            "name" to member.name,
            "designation" to member.designation,
            "competency" to member.competency,
            "stationDuty" to member.stationDuty,
            "shedDuty" to member.shedDuty,
            "weeklyRestDay" to member.weeklyRestDay,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
          )
        )
      }
      batch.commit().awaitLocalOrServer(OperationType.WRITE, staffCol.path)
      true
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.WRITE, staffCol.path)
      throw e
    }
  }

  override suspend fun addStaffMember(member: StaffMember): String {
    requireAuthUid()
    val id = member.id.takeIf { it.isNotBlank() }
      ?: "staff_${UUID.randomUUID().toString().replace("-", "").take(12)}"
    val docRef = depotDoc().collection("staff").document(id)
    val cleanDueDate = member.trainingDueDate.trim().take(10)
    val cleanCourse = if (cleanDueDate.isBlank()) "" else member.trainingCourse.trim().take(100)
    val cleanPhone = member.phoneNumber.trim().take(20)
    val cleanSecondaryPhone = member.secondaryPhoneNumber.trim().take(20)
    try {
      val exists = docRef.existsOfflineAware()
      if (exists) {
        docRef.update(
          mapOf(
            "serialNo" to member.serialNo.coerceIn(1L, 9999L),
            "name" to member.name.trim().take(100),
            "designation" to member.designation,
            "competency" to member.competency,
            "stationDuty" to member.stationDuty,
            "shedDuty" to member.shedDuty,
            "weeklyRestDay" to member.weeklyRestDay,
            "trainingDueDate" to cleanDueDate,
            "trainingCourse" to cleanCourse,
            "phoneNumber" to cleanPhone,
            "secondaryPhoneNumber" to cleanSecondaryPhone,
            "updatedAt" to FieldValue.serverTimestamp()
          )
        ).awaitLocalOrServer(OperationType.UPDATE, docRef.path)
      } else {
        docRef.set(
          mapOf(
            "userId" to depotId,
            "serialNo" to member.serialNo.coerceIn(1L, 9999L),
            "name" to member.name.trim().take(100),
            "designation" to member.designation,
            "competency" to member.competency,
            "stationDuty" to member.stationDuty,
            "shedDuty" to member.shedDuty,
            "weeklyRestDay" to member.weeklyRestDay,
            "trainingDueDate" to cleanDueDate,
            "trainingCourse" to cleanCourse,
            "phoneNumber" to cleanPhone,
            "secondaryPhoneNumber" to cleanSecondaryPhone,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
          )
        ).awaitLocalOrServer(OperationType.CREATE, docRef.path)
      }
      return id
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.CREATE, docRef.path)
      throw e
    }
  }

  override suspend fun updateStaffMember(member: StaffMember) {
    requireAuthUid()
    val docRef = depotDoc().collection("staff").document(member.id)
    val cleanDueDate = member.trainingDueDate.trim().take(10)
    val cleanCourse = if (cleanDueDate.isBlank()) "" else member.trainingCourse.trim().take(100)
    val cleanPhone = member.phoneNumber.trim().take(20)
    val cleanSecondaryPhone = member.secondaryPhoneNumber.trim().take(20)
    try {
      docRef.update(
        mapOf(
          "serialNo" to member.serialNo.coerceIn(1L, 9999L),
          "name" to member.name.trim().take(100),
          "designation" to member.designation,
          "competency" to member.competency,
          "stationDuty" to member.stationDuty,
          "shedDuty" to member.shedDuty,
          "weeklyRestDay" to member.weeklyRestDay,
          "trainingDueDate" to cleanDueDate,
          "trainingCourse" to cleanCourse,
          "phoneNumber" to cleanPhone,
          "secondaryPhoneNumber" to cleanSecondaryPhone,
          "updatedAt" to FieldValue.serverTimestamp()
        )
      ).awaitLocalOrServer(OperationType.UPDATE, docRef.path)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, docRef.path)
      throw e
    }
  }

  override suspend fun deleteStaffMember(staffId: String) {
    requireAuthUid()
    val docRef = depotDoc().collection("staff").document(staffId)
    try {
      docRef.delete().awaitLocalOrServer(OperationType.DELETE, docRef.path)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.DELETE, docRef.path)
      throw e
    }
  }

  override suspend fun updateStaffRestDay(staffId: String, restDay: WeeklyRestDay) {
    requireAuthUid()
    val docRef = depotDoc().collection("staff").document(staffId)
    try {
      docRef.update(
        mapOf(
          "weeklyRestDay" to restDay.name,
          "updatedAt" to FieldValue.serverTimestamp()
        )
      ).awaitLocalOrServer(OperationType.UPDATE, docRef.path)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, docRef.path)
      throw e
    }
  }

  override suspend fun updateStaffTrainingDue(
    staffId: String,
    trainingDueDate: String,
    trainingCourse: String
  ) {
    requireAuthUid()
    val docRef = depotDoc().collection("staff").document(staffId)
    val cleanDueDate = trainingDueDate.trim().take(10)
    val cleanCourse = if (cleanDueDate.isBlank()) "" else trainingCourse.trim().take(100)
    try {
      docRef.update(
        mapOf(
          "trainingDueDate" to cleanDueDate,
          "trainingCourse" to cleanCourse,
          "updatedAt" to FieldValue.serverTimestamp()
        )
      ).awaitLocalOrServer(OperationType.UPDATE, docRef.path)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, docRef.path)
      throw e
    }
  }

  override suspend fun batchUpdateRestDays(updates: Map<String, WeeklyRestDay>) {
    requireAuthUid()
    val staffCol = depotDoc().collection("staff")
    try {
      val batch = db.batch()
      updates.forEach { (staffId, restDay) ->
        batch.update(
          staffCol.document(staffId),
          mapOf(
            "weeklyRestDay" to restDay.name,
            "updatedAt" to FieldValue.serverTimestamp()
          )
        )
      }
      batch.commit().awaitLocalOrServer(OperationType.UPDATE, staffCol.path)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, staffCol.path)
      throw e
    }
  }

  override suspend fun saveSlotAssignment(assignment: ShiftSlotAssignment, existingDocPresent: Boolean) {
    requireAuthUid()
    val docId = assignment.id.ifBlank {
      "${assignment.date}_${assignment.shiftType}_${assignment.dutyArea}_${assignment.slotIndex}"
    }
    val docRef = depotDoc().collection("assignments").document(docId)
    try {
      val shouldUpdate = existingDocPresent || docRef.existsOfflineAware()
      if (shouldUpdate) {
        docRef.update(
          mapOf(
            "date" to assignment.date,
            "shiftType" to assignment.shiftType,
            "dutyArea" to assignment.dutyArea,
            "roleType" to assignment.roleType,
            "slotIndex" to assignment.slotIndex,
            "staffId" to assignment.staffId,
            "staffName" to assignment.staffName,
            "isEmergencyOverride" to assignment.isEmergencyOverride,
            "overrideReason" to assignment.overrideReason.take(300),
            "approvedLocked" to assignment.approvedLocked,
            "approvedByName" to assignment.approvedByName.take(100),
            "updatedAt" to FieldValue.serverTimestamp()
          )
        ).awaitLocalOrServer(OperationType.UPDATE, docRef.path)
      } else {
        docRef.set(
          mapOf(
            "userId" to depotId,
            "date" to assignment.date,
            "shiftType" to assignment.shiftType,
            "dutyArea" to assignment.dutyArea,
            "roleType" to assignment.roleType,
            "slotIndex" to assignment.slotIndex,
            "staffId" to assignment.staffId,
            "staffName" to assignment.staffName,
            "isEmergencyOverride" to assignment.isEmergencyOverride,
            "overrideReason" to assignment.overrideReason.take(300),
            "approvedLocked" to assignment.approvedLocked,
            "approvedByName" to assignment.approvedByName.take(100),
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
          )
        ).awaitLocalOrServer(OperationType.CREATE, docRef.path)
      }
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.WRITE, docRef.path)
      throw e
    }
  }

  override suspend fun batchSaveAssignments(assignments: List<ShiftSlotAssignment>, existingIds: Set<String>) {
    requireAuthUid()
    val col = depotDoc().collection("assignments")
    try {
      val verifiedExistingIds = withTimeoutOrNull(2_500L) {
        runCatching { col.get(Source.DEFAULT).await().documents.mapTo(HashSet()) { it.id } }.getOrNull()
      } ?: runCatching {
        col.get(Source.CACHE).await().documents.mapTo(HashSet()) { it.id }
      }.getOrElse { existingIds }
      val chunks = assignments.chunked(400)
      for (chunk in chunks) {
        val batch = db.batch()
        for (assignment in chunk) {
          val docId = assignment.id.ifBlank {
            "${assignment.date}_${assignment.shiftType}_${assignment.dutyArea}_${assignment.slotIndex}"
          }
          val docRef = col.document(docId)
          if (verifiedExistingIds.contains(docId)) {
            batch.update(
              docRef,
              mapOf(
                "date" to assignment.date,
                "shiftType" to assignment.shiftType,
                "dutyArea" to assignment.dutyArea,
                "roleType" to assignment.roleType,
                "slotIndex" to assignment.slotIndex,
                "staffId" to assignment.staffId,
                "staffName" to assignment.staffName,
                "isEmergencyOverride" to assignment.isEmergencyOverride,
                "overrideReason" to assignment.overrideReason.take(300),
                "approvedLocked" to assignment.approvedLocked,
                "approvedByName" to assignment.approvedByName.take(100),
                "updatedAt" to FieldValue.serverTimestamp()
              )
            )
          } else {
            batch.set(
              docRef,
              mapOf(
                "userId" to depotId,
                "date" to assignment.date,
                "shiftType" to assignment.shiftType,
                "dutyArea" to assignment.dutyArea,
                "roleType" to assignment.roleType,
                "slotIndex" to assignment.slotIndex,
                "staffId" to assignment.staffId,
                "staffName" to assignment.staffName,
                "isEmergencyOverride" to assignment.isEmergencyOverride,
                "overrideReason" to assignment.overrideReason.take(300),
                "approvedLocked" to assignment.approvedLocked,
                "approvedByName" to assignment.approvedByName.take(100),
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
              )
            )
          }
        }
        batch.commit().awaitLocalOrServer(OperationType.WRITE, col.path)
      }
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.WRITE, col.path)
      throw e
    }
  }

  override suspend fun clearSlotAssignment(assignmentId: String) {
    requireAuthUid()
    val docRef = depotDoc().collection("assignments").document(assignmentId)
    try {
      docRef.delete().awaitLocalOrServer(OperationType.DELETE, docRef.path)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.DELETE, docRef.path)
      throw e
    }
  }

  override suspend fun batchClearAssignments(assignmentIds: List<String>) {
    if (assignmentIds.isEmpty()) return
    requireAuthUid()
    val col = depotDoc().collection("assignments")
    try {
      for (chunk in assignmentIds.distinct().chunked(400)) {
        val batch = db.batch()
        for (id in chunk) {
          batch.delete(col.document(id))
        }
        batch.commit().awaitLocalOrServer(OperationType.DELETE, col.path)
      }
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.DELETE, col.path)
      throw e
    }
  }

  override suspend fun submitDutyDraft(draft: DutyApprovalRequest): String {
    val uid = requireAuthUid()
    val id = draft.id.takeIf { it.isNotBlank() }
      ?: "draft_${UUID.randomUUID().toString().replace("-", "").take(14)}"
    val docRef = depotDoc().collection("dutyDrafts").document(id)
    try {
      val exists = docRef.existsOfflineAware()
      val serializedSlots = draft.proposedSlots.map { it.toFirestoreMap() }
      if (exists) {
        docRef.update(
          mapOf(
            "proposedSlots" to serializedSlots,
            "submittedByName" to draft.submittedByName.trim().take(100),
            "updatedAt" to FieldValue.serverTimestamp()
          )
        ).awaitLocalOrServer(OperationType.UPDATE, docRef.path)
      } else {
        docRef.set(
          mapOf(
            "depotId" to depotId,
            "dutyDate" to draft.dutyDate,
            "status" to DutyDraftStatus.PENDING.name,
            "submittedByUid" to uid,
            "submittedByName" to draft.submittedByName.trim().take(100),
            "reviewedByUid" to "",
            "reviewedByName" to "",
            "reviewNotes" to "",
            "proposedSlots" to serializedSlots,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
          )
        ).awaitLocalOrServer(OperationType.CREATE, docRef.path)
      }
      return id
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.WRITE, docRef.path)
      throw e
    }
  }

  override suspend fun approveDutyDraft(
    draft: DutyApprovalRequest,
    reviewerUid: String,
    reviewerName: String,
    cycleAssignmentIdsToClear: List<String>,
    existingAssignmentIds: Set<String>
  ) {
    val uid = requireAuthUid()
    val effectiveReviewerUid = reviewerUid.ifBlank { uid }
    val cleanReviewerName = reviewerName.trim().ifBlank { "Supervisor" }.take(100)

    val assignmentsCol = depotDoc().collection("assignments")
    val draftRef = depotDoc().collection("dutyDrafts").document(draft.id)
    val lockRef = depotDoc().collection("dutyLocks").document(draft.dutyDate)

    try {
      val lockExists = lockRef.existsOfflineAware()
      val proposedIds = draft.proposedSlots.mapTo(HashSet()) { it.slotId }
      val batch = db.batch()

      // Remove existing cycle slots that are no longer in the approved draft
      cycleAssignmentIdsToClear
        .distinct()
        .filter { it !in proposedIds }
        .forEach { staleId ->
          batch.delete(assignmentsCol.document(staleId))
        }

      // Publish all proposed slots as approved & locked
      draft.proposedSlots.forEach { slotEntry ->
        val slotDocRef = assignmentsCol.document(slotEntry.slotId)
        if (slotEntry.slotId in existingAssignmentIds) {
          batch.update(
            slotDocRef,
            mapOf(
              "date" to slotEntry.date,
              "shiftType" to slotEntry.shiftType,
              "dutyArea" to slotEntry.dutyArea,
              "roleType" to slotEntry.roleType,
              "slotIndex" to slotEntry.slotIndex,
              "staffId" to slotEntry.staffId,
              "staffName" to slotEntry.staffName,
              "isEmergencyOverride" to slotEntry.isEmergencyOverride,
              "overrideReason" to slotEntry.overrideReason.take(300),
              "approvedLocked" to true,
              "approvedByName" to cleanReviewerName,
              "updatedAt" to FieldValue.serverTimestamp()
            )
          )
        } else {
          batch.set(
            slotDocRef,
            mapOf(
              "userId" to depotId,
              "date" to slotEntry.date,
              "shiftType" to slotEntry.shiftType,
              "dutyArea" to slotEntry.dutyArea,
              "roleType" to slotEntry.roleType,
              "slotIndex" to slotEntry.slotIndex,
              "staffId" to slotEntry.staffId,
              "staffName" to slotEntry.staffName,
              "isEmergencyOverride" to slotEntry.isEmergencyOverride,
              "overrideReason" to slotEntry.overrideReason.take(300),
              "approvedLocked" to true,
              "approvedByName" to cleanReviewerName,
              "createdAt" to FieldValue.serverTimestamp(),
              "updatedAt" to FieldValue.serverTimestamp()
            )
          )
        }
      }

      // Lock dutyDate so Storemate cannot alter approved duties
      if (lockExists) {
        batch.update(
          lockRef,
          mapOf(
            "lockedByUid" to effectiveReviewerUid,
            "lockedByName" to cleanReviewerName,
            "updatedAt" to FieldValue.serverTimestamp()
          )
        )
      } else {
        batch.set(
          lockRef,
          mapOf(
            "dutyDate" to draft.dutyDate,
            "lockedByUid" to effectiveReviewerUid,
            "lockedByName" to cleanReviewerName,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
          )
        )
      }

      // Mark draft as APPROVED
      batch.update(
        draftRef,
        mapOf(
          "status" to DutyDraftStatus.APPROVED.name,
          "reviewedByUid" to effectiveReviewerUid,
          "reviewedByName" to cleanReviewerName,
          "reviewNotes" to "Approved and published to shared depot roster.",
          "updatedAt" to FieldValue.serverTimestamp()
        )
      )

      batch.commit().awaitLocalOrServer(OperationType.WRITE, draftRef.path)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.WRITE, draftRef.path)
      throw e
    }
  }

  override suspend fun rejectDutyDraft(
    draftId: String,
    reviewerUid: String,
    reviewerName: String,
    notes: String
  ) {
    val uid = requireAuthUid()
    val effectiveReviewerUid = reviewerUid.ifBlank { uid }
    val docRef = depotDoc().collection("dutyDrafts").document(draftId)
    try {
      docRef.update(
        mapOf(
          "status" to DutyDraftStatus.REJECTED.name,
          "reviewedByUid" to effectiveReviewerUid,
          "reviewedByName" to reviewerName.trim().ifBlank { "Supervisor" }.take(100),
          "reviewNotes" to notes.trim().take(300),
          "updatedAt" to FieldValue.serverTimestamp()
        )
      ).awaitLocalOrServer(OperationType.UPDATE, docRef.path)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, docRef.path)
      throw e
    }
  }

  override suspend fun addLeaveRecord(leave: LeaveRecord): String {
    val uid = requireAuthUid()
    val id = leave.id.takeIf { it.isNotBlank() }
      ?: "leave_${UUID.randomUUID().toString().replace("-", "").take(16)}"
    val docRef = depotDoc().collection("leaves").document(id)
    try {
      val exists = docRef.existsOfflineAware()
      if (exists) {
        docRef.update(
          mapOf(
            "staffId" to leave.staffId,
            "staffName" to leave.staffName,
            "leaveType" to leave.leaveType,
            "startDate" to leave.startDate,
            "endDate" to leave.endDate,
            "status" to leave.statusEnum.name,
            "forwardedToStaffId" to leave.forwardedToStaffId.trim().take(128),
            "forwardedToName" to leave.forwardedToName.trim().take(100),
            "reviewedByName" to leave.reviewedByName.trim().take(100),
            "notes" to leave.notes.trim().take(300),
            "updatedAt" to FieldValue.serverTimestamp()
          )
        ).awaitLocalOrServer(OperationType.UPDATE, docRef.path)
      } else {
        docRef.set(
          mapOf(
            "userId" to depotId,
            "staffId" to leave.staffId,
            "staffName" to leave.staffName,
            "leaveType" to leave.leaveType,
            "startDate" to leave.startDate,
            "endDate" to leave.endDate,
            "status" to leave.statusEnum.name,
            "appliedByUid" to leave.appliedByUid.ifBlank { uid },
            "appliedByName" to leave.appliedByName.trim().take(100),
            "forwardedToStaffId" to leave.forwardedToStaffId.trim().take(128),
            "forwardedToName" to leave.forwardedToName.trim().take(100),
            "reviewedByName" to leave.reviewedByName.trim().take(100),
            "notes" to leave.notes.trim().take(300),
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
          )
        ).awaitLocalOrServer(OperationType.CREATE, docRef.path)
      }
      return id
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.CREATE, docRef.path)
      throw e
    }
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
    requireAuthUid()
    val docRef = depotDoc().collection("leaves").document(leaveId)
    try {
      docRef.update(
        mapOf(
          "leaveType" to leaveType.name,
          "startDate" to startDate,
          "endDate" to endDate,
          "status" to status.name,
          "reviewedByName" to reviewedByName.trim().take(100),
          "notes" to notes.trim().take(300),
          "updatedAt" to FieldValue.serverTimestamp()
        )
      ).awaitLocalOrServer(OperationType.UPDATE, docRef.path)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.UPDATE, docRef.path)
      throw e
    }
  }

  override suspend fun deleteLeaveRecord(leaveId: String) {
    requireAuthUid()
    val docRef = depotDoc().collection("leaves").document(leaveId)
    try {
      docRef.delete().awaitLocalOrServer(OperationType.DELETE, docRef.path)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.DELETE, docRef.path)
      throw e
    }
  }

  override suspend fun logEmergencyOverride(log: EmergencyOverrideLog): String {
    requireAuthUid()
    val id = "ovr_${UUID.randomUUID().toString().replace("-", "").take(16)}"
    val docRef = depotDoc().collection("overrides").document(id)
    try {
      docRef.set(
        mapOf(
          "userId" to depotId,
          "date" to log.date,
          "shiftType" to log.shiftType,
          "dutyArea" to log.dutyArea,
          "staffId" to log.staffId,
          "staffName" to log.staffName,
          "conflictSummary" to log.conflictSummary.take(300),
          "reason" to log.reason.trim().ifBlank { "Emergency operational override" }.take(300),
          "createdAt" to FieldValue.serverTimestamp(),
          "updatedAt" to FieldValue.serverTimestamp()
        )
      ).awaitLocalOrServer(OperationType.CREATE, docRef.path)
      return id
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      handleFirestoreError(e, OperationType.CREATE, docRef.path)
      throw e
    }
  }

  private fun logStreamError(
    error: Throwable,
    operationType: OperationType,
    path: String
  ) {
    val firestoreError = generateSequence(error) { it.cause }
      .filterIsInstance<FirebaseFirestoreException>()
      .firstOrNull()
    if (firestoreError == null && error is CancellationException) return
    val exceptionToLog = firestoreError ?: error
    if (auth.currentUser != null) {
      handleFirestoreError(exceptionToLog, operationType, path)
    }
  }

  companion object {
    private val EMAIL_KEY_REGEX = Regex("^[^/\\s@]+@[^/\\s@]+\\.[^/\\s@]+$")

    fun configureOfflinePersistence(db: FirebaseFirestore) {
      FirestoreProvider.applyOfflinePersistenceSettings(db)
    }
  }
}
