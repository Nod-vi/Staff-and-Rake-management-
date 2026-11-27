package com.example.data

import com.example.model.AppReleaseConfig
import com.example.model.DepotSettings
import com.example.model.DepotUserProfile
import com.example.model.DutyApprovalRequest
import com.example.model.DutyDateLock
import com.example.model.DutyPermissionRecord
import com.example.model.EmergencyOverrideLog
import com.example.model.ImportantContact
import com.example.model.LeaveRecord
import com.example.model.LeaveStatus
import com.example.model.LeaveType
import com.example.model.RakeFormation
import com.example.model.ShiftSlotAssignment
import com.example.model.StaffLeaveAccount
import com.example.model.StaffMember
import com.example.model.UserRole
import com.example.model.WeeklyRestDay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

interface RosterRepository {
  fun observeSyncMetadata(depotId: String): Flow<FirestoreCacheSnapshotState> =
    flowOf(FirestoreCacheSnapshotState())

  suspend fun waitForPendingSync() {}

  fun observeStaff(depotId: String): Flow<List<StaffMember>>
  fun observeAssignments(depotId: String): Flow<List<ShiftSlotAssignment>>
  fun observeLeaves(depotId: String): Flow<List<LeaveRecord>>
  fun observeOverrides(depotId: String): Flow<List<EmergencyOverrideLog>>
  fun observeDutyDrafts(depotId: String): Flow<List<DutyApprovalRequest>>
  fun observeDutyLocks(depotId: String): Flow<List<DutyDateLock>>
  fun observeUserProfile(uid: String): Flow<DepotUserProfile?>
  fun observeAllUserProfiles(): Flow<List<DepotUserProfile>>
  fun observeDepotSettings(depotId: String): Flow<DepotSettings>
  suspend fun updateAllowTechnicianShare(allowed: Boolean, updatedByUid: String)
  suspend fun updateShunterSettings(
    showForTechnicians: Boolean,
    includeInExport: Boolean,
    updatedByUid: String
  )
  suspend fun updateRoleMenuAccess(
    roleMenuAccess: Map<String, Map<String, String>>,
    updatedByUid: String
  )
  fun observeAppRelease(depotId: String): Flow<AppReleaseConfig>
  suspend fun publishAppRelease(release: AppReleaseConfig)
  suspend fun clearAppRelease(updatedByUid: String, updatedByName: String)
  fun observeLeaveAccounts(depotId: String): Flow<List<StaffLeaveAccount>>
  suspend fun saveLeaveAccount(account: StaffLeaveAccount, isTechnicianCorrectionRequest: Boolean = false)
  fun observeDutyPermissions(depotId: String): Flow<List<DutyPermissionRecord>>
  suspend fun saveDutyPermission(record: DutyPermissionRecord, existingDocPresent: Boolean = false): String
  suspend fun deleteDutyPermission(permissionId: String)
  fun observeImportantContacts(depotId: String): Flow<List<ImportantContact>>
  suspend fun seedDefaultImportantContactsIfNeeded(updatedByUid: String): Boolean
  suspend fun addImportantContact(
    label: String,
    phoneNumber: String,
    category: String,
    sortOrder: Long,
    updatedByUid: String
  ): String
  suspend fun deleteImportantContact(contactId: String, updatedByUid: String)

  fun observeRakes(depotId: String): Flow<List<RakeFormation>>
  suspend fun seedDefaultRakesIfNeeded(updatedByUid: String, updatedByName: String): Boolean
  suspend fun saveRakeFormation(
    rake: RakeFormation,
    existingDocPresent: Boolean,
    updatedByUid: String,
    updatedByName: String
  ): String
  suspend fun deleteRakeFormation(rakeId: String, updatedByUid: String, updatedByName: String)

  suspend fun ensureUserProfile(uid: String, email: String, displayName: String): DepotUserProfile
  suspend fun updateUserRole(targetUid: String, role: UserRole, staffId: String, updatedByUid: String)
  suspend fun authenticateLocalUser(username: String, password: String): DepotUserProfile?
  suspend fun createUserAccount(
    username: String,
    password: String,
    displayName: String,
    email: String,
    role: UserRole,
    staffId: String,
    createdByUid: String
  ): DepotUserProfile
  suspend fun updateUserAccount(
    targetUid: String,
    username: String,
    displayName: String,
    email: String,
    role: UserRole,
    staffId: String,
    newPassword: String?,
    updatedByUid: String
  )
  suspend fun deleteUserAccount(targetUid: String)
  suspend fun changeUserPassword(targetUid: String, currentPassword: String, newPassword: String): Boolean

  suspend fun seedDefaultRosterIfEmpty(): Boolean
  suspend fun addStaffMember(member: StaffMember): String
  suspend fun updateStaffMember(member: StaffMember)
  suspend fun deleteStaffMember(staffId: String)
  suspend fun updateStaffRestDay(staffId: String, restDay: WeeklyRestDay)
  suspend fun updateStaffTrainingDue(staffId: String, trainingDueDate: String, trainingCourse: String)
  suspend fun batchUpdateRestDays(updates: Map<String, WeeklyRestDay>)
  suspend fun saveSlotAssignment(assignment: ShiftSlotAssignment, existingDocPresent: Boolean)
  suspend fun batchSaveAssignments(assignments: List<ShiftSlotAssignment>, existingIds: Set<String>)
  suspend fun clearSlotAssignment(assignmentId: String)
  suspend fun batchClearAssignments(assignmentIds: List<String>)

  suspend fun submitDutyDraft(draft: DutyApprovalRequest): String
  suspend fun approveDutyDraft(
    draft: DutyApprovalRequest,
    reviewerUid: String,
    reviewerName: String,
    cycleAssignmentIdsToClear: List<String>,
    existingAssignmentIds: Set<String>
  )
  suspend fun rejectDutyDraft(
    draftId: String,
    reviewerUid: String,
    reviewerName: String,
    notes: String
  )

  suspend fun addLeaveRecord(leave: LeaveRecord): String
  suspend fun updateLeaveRecord(
    leaveId: String,
    leaveType: LeaveType,
    startDate: String,
    endDate: String,
    status: LeaveStatus,
    notes: String,
    reviewedByName: String = ""
  )
  suspend fun deleteLeaveRecord(leaveId: String)
  suspend fun logEmergencyOverride(log: EmergencyOverrideLog): String
}
