package com.example.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.Exclude
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.PropertyName
import com.google.firebase.firestore.ServerTimestamp
import java.time.DayOfWeek
import java.time.LocalDate

enum class Designation(val label: String) {
  SSE("SSE"),
  JE("JE"),
  SR_TECH("SR.TECH"),
  TECH_I("TECH-I"),
  TECH_II("TECH-II"),
  TECH_III("TECH-III"),
  KHP("KHP"),
  SHUNTER("SHUNTER");

  companion object {
    fun fromRaw(value: String): Designation =
      entries.firstOrNull { it.name == value } ?: KHP
  }
}

enum class Competency(val label: String) {
  NONE("General"),
  POINTSMAN("Pointsman");

  companion object {
    fun fromRaw(value: String): Competency =
      if (value.equals("POINTSMAN", ignoreCase = true) || value.equals("PIONTSMAN", ignoreCase = true)) {
        POINTSMAN
      } else {
        NONE
      }
  }
}

enum class WeeklyRestDay(val label: String, val dayOfWeek: DayOfWeek?) {
  NONE("Not Set", null),
  MONDAY("Monday", DayOfWeek.MONDAY),
  TUESDAY("Tuesday", DayOfWeek.TUESDAY),
  WEDNESDAY("Wednesday", DayOfWeek.WEDNESDAY),
  THURSDAY("Thursday", DayOfWeek.THURSDAY),
  FRIDAY("Friday", DayOfWeek.FRIDAY),
  SATURDAY("Saturday", DayOfWeek.SATURDAY),
  SUNDAY("Sunday", DayOfWeek.SUNDAY);

  companion object {
    fun fromRaw(value: String): WeeklyRestDay =
      entries.firstOrNull { it.name == value } ?: NONE
  }
}

enum class ShiftType(
  val code: String,
  val displayName: String,
  val timeWindow: String,
  val stationArea: DutyArea?,
  val isGeneralShift: Boolean = false
) {
  SHIFT_22_06("22/06", "Night Shift", "22:00 – 06:00", DutyArea.STATION_UR),
  SHIFT_06_14("06/14", "Morning Shift", "06:00 – 14:00", DutyArea.STATION_SC),
  SHIFT_14_22("14/22", "Afternoon Shift", "14:00 – 22:00", DutyArea.STATION_SC),
  SHIFT_08_17("08/17", "General Shift", "08:00 – 17:00", null, isGeneralShift = true);

  fun startDateForDutyDate(dutyDate: LocalDate): LocalDate =
    if (this == SHIFT_22_06) dutyDate.minusDays(1) else dutyDate

  fun dutyDateForStartDate(startDate: LocalDate): LocalDate =
    if (this == SHIFT_22_06) startDate.plusDays(1) else startDate

  companion object {
    fun fromRaw(value: String): ShiftType =
      entries.firstOrNull { it.name == value } ?: SHIFT_06_14
  }
}

enum class DutyArea(val label: String, val shortCode: String) {
  SHED("Shed Duty", "SHED"),
  STATION_SC("SC Station", "SC"),
  STATION_UR("UR Station", "UR"),
  STATION_MED("Medchal (MED)", "MED");

  companion object {
    fun fromRaw(value: String): DutyArea =
      entries.firstOrNull { it.name == value } ?: SHED
  }
}

enum class SlotRoleType(val label: String) {
  POINTSMAN("Pointsman"),
  SHUNTER("Shunter"),
  GENERAL_DUTY("Technical / Duty");

  companion object {
    fun fromRaw(value: String): SlotRoleType =
      entries.firstOrNull { it.name == value } ?: GENERAL_DUTY
  }
}

enum class UserRole(val label: String) {
  ADMIN("Admin"),
  SUPERVISOR("Supervisor"),
  SSE("SSE"),
  JE("JE"),
  SHIFT_INCHARGE("ShiftIncharge"),
  STOREMATE("Storemate"),
  TECHNICIAN("Technician");

  val canManageUserRoles: Boolean get() = this == ADMIN
  val canManageStaff: Boolean get() = this == ADMIN
  val canApproveDuties: Boolean get() = this == ADMIN || this == SUPERVISOR || this == SSE
  val canDirectEditDuties: Boolean get() = this == ADMIN || this == SUPERVISOR || this == SSE
  val canDraftDuties: Boolean get() = this == STOREMATE || this == JE
  val canApproveLeaves: Boolean get() = this == ADMIN || this == SUPERVISOR || this == SSE
  val canManageLeaves: Boolean get() = this == ADMIN || this == SUPERVISOR || this == SSE
  val canManageRestDays: Boolean get() = this == ADMIN || this == SUPERVISOR || this == SSE
  val canManagePermissions: Boolean get() = this == ADMIN || this == SUPERVISOR || this == SSE || this == JE || this == SHIFT_INCHARGE
  val canManageRakes: Boolean get() = this == ADMIN || this == SUPERVISOR || this == SSE || this == JE || this == SHIFT_INCHARGE
  val canApplyLeaveOnly: Boolean get() = this == STOREMATE || this == TECHNICIAN || this == JE

  companion object {
    fun fromRaw(value: String?): UserRole =
      entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: TECHNICIAN
  }
}

enum class DutyDraftStatus(val label: String) {
  PENDING("Pending Approval"),
  APPROVED("Approved & Locked"),
  REJECTED("Returned / Rejected");

  companion object {
    fun fromRaw(value: String?): DutyDraftStatus =
      entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: PENDING
  }
}

enum class LeaveStatus(val label: String) {
  APPROVED("Approved"),
  PENDING("Pending"),
  REJECTED("Rejected"),
  CANCELLED("Cancelled");

  val blocksShiftDuty: Boolean get() = this == APPROVED

  companion object {
    fun fromRaw(value: String): LeaveStatus =
      entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: APPROVED
  }
}

enum class LeaveType(val code: String, val label: String) {
  CL("CL", "Casual Leave"),
  LAP("LAP", "Leave on Average Pay"),
  LHAP("LHAP", "Leave on Half Average Pay"),
  SPECIAL("SPECIAL", "Special Leave (CR)"),
  TRAINING("TRAINING", "Technical Training");

  companion object {
    fun fromRaw(value: String): LeaveType = when (value.trim().uppercase()) {
      "CL" -> CL
      "LAP", "EL" -> LAP
      "LHAP", "SICK" -> LHAP
      "SPECIAL" -> SPECIAL
      "TRAINING", "TRG" -> TRAINING
      else -> CL
    }
  }
}

enum class LapUpdateStatus(val label: String) {
  NONE("None"),
  PENDING("Pending Approval"),
  APPROVED("Approved"),
  REJECTED("Rejected");

  companion object {
    fun fromRaw(value: String?): LapUpdateStatus =
      entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: NONE
  }
}

@Keep
@IgnoreExtraProperties
data class StaffMember(
  @DocumentId val id: String = "",
  val userId: String = "",
  val serialNo: Long = 0L,
  val name: String = "",
  val designation: String = Designation.KHP.name,
  val competency: String = Competency.NONE.name,
  val stationDuty: Boolean = false,
  val shedDuty: Boolean = false,
  val weeklyRestDay: String = WeeklyRestDay.NONE.name,
  val trainingDueDate: String = "",
  val trainingCourse: String = "",
  val phoneNumber: String = "",
  val secondaryPhoneNumber: String = "",
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null
) {
  @get:Exclude val designationEnum: Designation get() = Designation.fromRaw(designation)
  @get:Exclude val competencyEnum: Competency get() = Competency.fromRaw(competency)
  @get:Exclude val restDayEnum: WeeklyRestDay get() = WeeklyRestDay.fromRaw(weeklyRestDay)
  @get:Exclude val isPointsman: Boolean get() = competencyEnum == Competency.POINTSMAN
  @get:Exclude val isShunter: Boolean get() = designationEnum == Designation.SHUNTER
  @get:Exclude val isSupervisory: Boolean get() = !isShunter && !stationDuty && !shedDuty
  @get:Exclude val hasTrainingDue: Boolean get() = trainingDueDate.isNotBlank()
  @get:Exclude val primaryPhoneTrimmed: String get() = phoneNumber.trim()
  @get:Exclude val secondaryPhoneTrimmed: String get() = secondaryPhoneNumber.trim()
  @get:Exclude val availablePhones: List<Pair<String, String>>
    get() = buildList {
      if (primaryPhoneTrimmed.isNotBlank()) add("Primary" to primaryPhoneTrimmed)
      if (secondaryPhoneTrimmed.isNotBlank() && secondaryPhoneTrimmed != primaryPhoneTrimmed) {
        add("Alternate" to secondaryPhoneTrimmed)
      }
    }
  @get:Exclude val hasPhoneNumber: Boolean get() = availablePhones.isNotEmpty()
  @get:Exclude val preferredPhoneNumber: String
    get() = primaryPhoneTrimmed.ifBlank { secondaryPhoneTrimmed }
}

@Keep
@IgnoreExtraProperties
data class ImportantContact(
  @DocumentId val id: String = "",
  val userId: String = "",
  val label: String = "",
  val phoneNumber: String = "",
  val category: String = "Control",
  val sortOrder: Long = 0L,
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null
)

val IMPORTANT_DEPOT_CONTACTS: List<ImportantContact> = listOf(
  ImportantContact(id = "rri_kzj", label = "RRI KZJ", phoneNumber = "7815913694", category = "Signal / RRI", sortOrder = 1L),
  ImportantContact(id = "kcg_panel", label = "KCG PANEL", phoneNumber = "9281035602", category = "Panel", sortOrder = 2L),
  ImportantContact(id = "tlc_sc", label = "TLC SC", phoneNumber = "8790992825", category = "Traction Control", sortOrder = 3L),
  ImportantContact(id = "ss_med", label = "SS MED", phoneNumber = "9281035656", category = "Station Master", sortOrder = 4L),
  ImportantContact(id = "ss_hyb_cabin", label = "SS HYB CABIN", phoneNumber = "7815901066", category = "Station / Cabin", sortOrder = 5L),
  ImportantContact(id = "ss_gt", label = "SS GT", phoneNumber = "7815905925", category = "Station Master", sortOrder = 6L),
  ImportantContact(id = "pcor_hyb", label = "PCOR HYB", phoneNumber = "7670905120", category = "Power Control", sortOrder = 7L),
  ImportantContact(id = "pcor_sc", label = "PCOR SC", phoneNumber = "8790993339", category = "Power Control", sortOrder = 8L),
  ImportantContact(id = "sse_ele_maint_mly", label = "SSE ELE MAINT MLY", phoneNumber = "9701372320", category = "Maintenance", sortOrder = 9L)
)

@Keep
@IgnoreExtraProperties
data class ShiftSlotAssignment(
  @DocumentId val id: String = "",
  val userId: String = "",
  val date: String = "",
  val shiftType: String = ShiftType.SHIFT_06_14.name,
  val dutyArea: String = DutyArea.SHED.name,
  val roleType: String = SlotRoleType.GENERAL_DUTY.name,
  val slotIndex: Long = 0L,
  val staffId: String = "",
  val staffName: String = "",
  @get:PropertyName("isEmergencyOverride")
  @field:PropertyName("isEmergencyOverride")
  val isEmergencyOverride: Boolean = false,
  val overrideReason: String = "",
  val approvedLocked: Boolean = false,
  val approvedByName: String = "",
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null
) {
  @get:Exclude val shiftTypeEnum: ShiftType get() = ShiftType.fromRaw(shiftType)
  @get:Exclude val dutyAreaEnum: DutyArea get() = DutyArea.fromRaw(dutyArea)
  @get:Exclude val roleTypeEnum: SlotRoleType get() = SlotRoleType.fromRaw(roleType)
  @get:Exclude val dutyDateStr: String
    get() = runCatching {
      shiftTypeEnum.dutyDateForStartDate(LocalDate.parse(date)).toString()
    }.getOrDefault(date)
}

@Keep
@IgnoreExtraProperties
data class DraftSlotEntry(
  val slotId: String = "",
  val date: String = "",
  val shiftType: String = ShiftType.SHIFT_06_14.name,
  val dutyArea: String = DutyArea.SHED.name,
  val roleType: String = SlotRoleType.GENERAL_DUTY.name,
  val slotIndex: Long = 0L,
  val staffId: String = "",
  val staffName: String = "",
  @get:PropertyName("isEmergencyOverride")
  @field:PropertyName("isEmergencyOverride")
  val isEmergencyOverride: Boolean = false,
  val overrideReason: String = ""
) {
  fun toAssignment(depotId: String, approverName: String): ShiftSlotAssignment =
    ShiftSlotAssignment(
      id = slotId,
      userId = depotId,
      date = date,
      shiftType = shiftType,
      dutyArea = dutyArea,
      roleType = roleType,
      slotIndex = slotIndex,
      staffId = staffId,
      staffName = staffName,
      isEmergencyOverride = isEmergencyOverride,
      overrideReason = overrideReason,
      approvedLocked = true,
      approvedByName = approverName
    )

  fun toFirestoreMap(): Map<String, Any> = mapOf(
    "slotId" to slotId,
    "date" to date,
    "shiftType" to shiftType,
    "dutyArea" to dutyArea,
    "roleType" to roleType,
    "slotIndex" to slotIndex,
    "staffId" to staffId,
    "staffName" to staffName,
    "isEmergencyOverride" to isEmergencyOverride,
    "overrideReason" to overrideReason
  )
}

@Keep
@IgnoreExtraProperties
data class DutyApprovalRequest(
  @DocumentId val id: String = "",
  val depotId: String = "ppio_main",
  val dutyDate: String = "",
  val status: String = DutyDraftStatus.PENDING.name,
  val submittedByUid: String = "",
  val submittedByName: String = "",
  val reviewedByUid: String = "",
  val reviewedByName: String = "",
  val reviewNotes: String = "",
  val proposedSlots: List<DraftSlotEntry> = emptyList(),
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null
) {
  @get:Exclude val statusEnum: DutyDraftStatus get() = DutyDraftStatus.fromRaw(status)
}

@Keep
@IgnoreExtraProperties
data class DutyDateLock(
  @DocumentId val id: String = "",
  val dutyDate: String = "",
  val lockedByUid: String = "",
  val lockedByName: String = "",
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class DepotUserProfile(
  @DocumentId val id: String = "",
  val uid: String = "",
  val username: String = "",
  val email: String = "",
  val displayName: String = "",
  val role: String = UserRole.TECHNICIAN.name,
  val staffId: String = "",
  val phoneNumber: String = "",
  val secondaryPhoneNumber: String = "",
  val registered: Boolean = false,
  val updatedByUid: String = "",
  val hasPassword: Boolean = false,
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null
) {
  @get:Exclude val roleEnum: UserRole get() = UserRole.fromRaw(role)
  @get:Exclude val isPendingCloudInvite: Boolean get() = uid.startsWith(CLOUD_INVITE_UID_PREFIX)

  companion object {
    const val CLOUD_INVITE_UID_PREFIX = "invite:"
  }
}

@Keep
@IgnoreExtraProperties
data class RoleInvite(
  @DocumentId val id: String = "",
  val email: String = "",
  val username: String = "",
  val displayName: String = "",
  val role: String = UserRole.TECHNICIAN.name,
  val staffId: String = "",
  val updatedByUid: String = "",
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null
) {
  @get:Exclude val roleEnum: UserRole get() = UserRole.fromRaw(role)
}

@Keep
@IgnoreExtraProperties
data class DepotSettings(
  @DocumentId val id: String = "config",
  val allowTechnicianShare: Boolean = true,
  val showShunterForTechnicians: Boolean = false,
  val includeShunterInExport: Boolean = false,
  val importantContactsInitialized: Boolean = false,
  val rakesInitialized: Boolean = false,
  val rakesSchemaVersion: Long = 0L,
  val roleMenuAccess: Map<String, Map<String, String>> = emptyMap(),
  val updatedByUid: String = "",
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class LeaveRecord(
  @DocumentId val id: String = "",
  val userId: String = "",
  val staffId: String = "",
  val staffName: String = "",
  val leaveType: String = LeaveType.CL.name,
  val startDate: String = "",
  val endDate: String = "",
  val status: String = LeaveStatus.APPROVED.name,
  val appliedByUid: String = "",
  val appliedByName: String = "",
  val forwardedToStaffId: String = "",
  val forwardedToName: String = "",
  val reviewedByName: String = "",
  val notes: String = "",
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null
) {
  @get:Exclude val leaveTypeEnum: LeaveType get() = LeaveType.fromRaw(leaveType)
  @get:Exclude val statusEnum: LeaveStatus get() = LeaveStatus.fromRaw(status)
  @get:Exclude val isBlockingDuty: Boolean get() = statusEnum.blocksShiftDuty

  fun coversDate(dateStr: String): Boolean {
    return dateStr >= startDate && dateStr <= endDate
  }

  fun isBlockingDate(dateStr: String): Boolean {
    return isBlockingDuty && coversDate(dateStr)
  }
}

@Keep
@IgnoreExtraProperties
data class StaffLeaveAccount(
  @DocumentId val id: String = "",
  val staffId: String = "",
  val staffName: String = "",
  val baseYear: Int = 0,
  val lapOpeningBalance: Int = 30,
  val lhapOpeningBalance: Int = 20,
  val lapSelfUpdated: Boolean = false,
  val pendingLapBalance: Int = 0,
  val pendingLhapBalance: Int = -1,
  val pendingLapYear: Int = 0,
  val pendingLapReason: String = "",
  val pendingLapStatus: String = LapUpdateStatus.NONE.name,
  val pendingLapRequestedByUid: String = "",
  val pendingLapReviewedByName: String = "",
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null
) {
  @get:Exclude val pendingLapStatusEnum: LapUpdateStatus get() = LapUpdateStatus.fromRaw(pendingLapStatus)
}

@Keep
@IgnoreExtraProperties
data class EmergencyOverrideLog(
  @DocumentId val id: String = "",
  val userId: String = "",
  val date: String = "",
  val shiftType: String = ShiftType.SHIFT_06_14.name,
  val dutyArea: String = DutyArea.SHED.name,
  val staffId: String = "",
  val staffName: String = "",
  val conflictSummary: String = "",
  val reason: String = "",
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null
)

enum class PermissionReliefType(val label: String) {
  EARLY_RELIEF("Early Relief"),
  LATE_REPORTING("Late Reporting"),
  MID_SHIFT("Mid-Shift Relief");

  companion object {
    fun fromRaw(value: String?): PermissionReliefType =
      entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: EARLY_RELIEF
  }
}

@Keep
@IgnoreExtraProperties
data class DutyPermissionRecord(
  @DocumentId val id: String = "",
  val userId: String = "",
  val staffId: String = "",
  val staffName: String = "",
  val date: String = "",
  val shiftType: String = ShiftType.SHIFT_06_14.name,
  val hours: Double = 1.0,
  val reliefType: String = PermissionReliefType.EARLY_RELIEF.name,
  val reason: String = "",
  val grantedByUid: String = "",
  val grantedByName: String = "",
  @ServerTimestamp val createdAt: Timestamp? = null,
  @ServerTimestamp val updatedAt: Timestamp? = null
) {
  @get:Exclude val shiftTypeEnum: ShiftType get() = ShiftType.fromRaw(shiftType)
  @get:Exclude val reliefTypeEnum: PermissionReliefType get() = PermissionReliefType.fromRaw(reliefType)
}

data class ShiftSlotSpec(
  val date: String,
  val shiftType: ShiftType,
  val dutyArea: DutyArea,
  val roleType: SlotRoleType,
  val slotIndex: Int,
  val label: String,
  val isRequired: Boolean = true
) {
  val documentId: String
    get() = "${date}_${shiftType.name}_${dutyArea.name}_${slotIndex}"
}

fun LocalDate.isMedDutyActive(): Boolean {
  return dayOfWeek == DayOfWeek.MONDAY ||
    dayOfWeek == DayOfWeek.TUESDAY ||
    dayOfWeek == DayOfWeek.WEDNESDAY ||
    dayOfWeek == DayOfWeek.THURSDAY
}
