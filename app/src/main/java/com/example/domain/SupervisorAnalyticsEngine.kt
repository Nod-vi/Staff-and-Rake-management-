package com.example.domain

import com.example.model.DutyArea
import com.example.model.DutyPermissionRecord
import com.example.model.EmergencyOverrideLog
import com.example.model.LeaveRecord
import com.example.model.LeaveStatus
import com.example.model.LeaveType
import com.example.model.ShiftSlotAssignment
import com.example.model.ShiftType
import com.example.model.StaffMember
import com.example.model.isMedDutyActive
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import kotlin.math.abs

enum class StaffTightnessLevel(val label: String) {
  NORMAL("Normal"),
  TIGHT("Very Tight"),
  CRITICAL("Critical Shortfall")
}

data class LeaveCalendarDayInfo(
  val date: LocalDate,
  val approvedLeaves: List<LeaveRecord>,
  val pendingLeaves: List<LeaveRecord>,
  val restStaff: List<StaffMember>,
  val activeDutyCount: Int,
  val availableConfirmedCount: Int,
  val availableProjectedCount: Int,
  val minRequiredTotal: Int,
  val tightnessLevel: StaffTightnessLevel
)

data class MonthlyLeaveCalendarData(
  val yearMonth: YearMonth,
  val days: List<LeaveCalendarDayInfo>,
  val totalApprovedLeaveDays: Int,
  val totalPendingLeaveDays: Int,
  val uniqueStaffOnLeave: Int,
  val tightDaysCount: Int,
  val criticalDaysCount: Int
)

data class ShiftOverFiveAlert(
  val date: String,
  val shiftType: ShiftType,
  val totalStaff: Int,
  val pointsmanCount: Int,
  val shedCount: Int,
  val stationCount: Int,
  val medCount: Int,
  val staffNames: List<String>,
  val isShedOverFive: Boolean
)

data class TightStaffDayForecast(
  val date: LocalDate,
  val approvedLeaves: List<LeaveRecord>,
  val pendingLeaves: List<LeaveRecord>,
  val restStaff: List<StaffMember>,
  val totalActiveDutyStaff: Int,
  val availableConfirmed: Int,
  val availableProjected: Int,
  val minRequiredTotal: Int,
  val availablePointsmen: Int,
  val requiredPointsmen: Int,
  val availableStationQualified: Int,
  val requiredStationQualified: Int,
  val severity: StaffTightnessLevel,
  val summaryReason: String
)

data class StaffPerformanceRow(
  val staff: StaffMember,
  val totalDuties: Int,
  val nightDuties: Int,
  val morningDuties: Int,
  val afternoonDuties: Int,
  val generalDuties: Int,
  val restDayDuties: Int,
  val doubleDutyDays: Int,
  val specialLeaveCreditsEarned: Int,
  val approvedLeaveDays: Int,
  val approvedClDays: Int,
  val approvedLapDays: Int,
  val approvedLhapDays: Int,
  val approvedSpecialDays: Int,
  val approvedTrainingDays: Int = 0,
  val pendingLeaveCount: Int,
  val permissionCount: Int,
  val totalPermissionHours: Double,
  val emergencyOverridesCount: Int,
  val performanceTag: String
)

enum class TrainingDueStatus(val label: String) {
  OVERDUE("Overdue"),
  DUE_SOON("Due Soon"),
  UPCOMING("Scheduled")
}

data class TrainingDueTechnicianItem(
  val staff: StaffMember,
  val dueDate: LocalDate,
  val daysUntilDue: Long,
  val status: TrainingDueStatus,
  val course: String,
  val activeTrainingLeave: LeaveRecord?
)

data class LiveDutyStaffEntry(
  val assignment: ShiftSlotAssignment,
  val staff: StaffMember?
) {
  val displayName: String
    get() = staff?.name?.ifBlank { assignment.staffName } ?: assignment.staffName.ifBlank { assignment.staffId }
  val phoneNumber: String
    get() = staff?.primaryPhoneTrimmed.orEmpty()
  val secondaryPhoneNumber: String
    get() = staff?.secondaryPhoneTrimmed.orEmpty()
  val preferredPhoneNumber: String
    get() = staff?.preferredPhoneNumber.orEmpty()
  val isStationArea: Boolean
    get() = assignment.dutyAreaEnum != DutyArea.SHED
}

data class LiveShiftStaffSnapshot(
  val now: LocalDateTime,
  val activeShift: ShiftType,
  val activeShiftAssignmentDate: LocalDate,
  val operationalDutyDate: LocalDate,
  val activeShiftWindowLabel: String,
  val pointsmanStaff: List<LiveDutyStaffEntry>,
  val shunterStaff: List<LiveDutyStaffEntry> = emptyList(),
  val shedStaff: List<LiveDutyStaffEntry>,
  val activeStationStaff: List<LiveDutyStaffEntry>,
  val generalShiftActive: Boolean,
  val generalShiftStaff: List<LiveDutyStaffEntry>,
  val allStationQualifiedStaff: List<StaffMember>,
  val onLeaveTodayCount: Int,
  val onRestTodayCount: Int
) {
  val allPresentShiftEntries: List<LiveDutyStaffEntry>
    get() = pointsmanStaff + shunterStaff + shedStaff + activeStationStaff + generalShiftStaff

  val currentShiftStaffMembers: List<StaffMember>
    get() = allPresentShiftEntries
      .distinctBy { it.assignment.staffId }
      .map { entry ->
        entry.staff ?: StaffMember(
          id = entry.assignment.staffId,
          name = entry.displayName,
          stationDuty = entry.isStationArea,
          shedDuty = !entry.isStationArea
        )
      }
      .sortedWith(compareBy<StaffMember>({ if (it.serialNo > 0L) it.serialNo else Long.MAX_VALUE }, { it.name }))

  val totalPresentShiftStaff: Int
    get() = allPresentShiftEntries
      .map { it.assignment.staffId }
      .distinct()
      .size
}

object SupervisorAnalyticsEngine {

  private const val MIN_POINTSMEN_PER_DAY = 3
  private const val BASE_MIN_DAILY_DUTY_STAFF = 18 // 3 PM + 9 min Shed + 6 Station
  private const val BASE_MIN_STATION_STAFF = 6

  fun formatHours(hours: Double): String {
    val rounded = kotlin.math.round(hours * 10.0) / 10.0
    return if (abs(rounded - rounded.toInt().toDouble()) < 0.05) {
      "${rounded.toInt()}h"
    } else {
      "${rounded}h"
    }
  }

  private fun evaluateDayStaffing(
    date: LocalDate,
    activeDutyStaff: List<StaffMember>,
    leaves: List<LeaveRecord>
  ): TightStaffDayForecast {
    val dateStr = date.toString()
    val approvedOnDay = leaves
      .filter { it.statusEnum == LeaveStatus.APPROVED && it.coversDate(dateStr) }
      .distinctBy { it.staffId }
    val pendingOnDay = leaves
      .filter { it.statusEnum == LeaveStatus.PENDING && it.coversDate(dateStr) }
      .distinctBy { it.staffId }

    val approvedIds = approvedOnDay.mapTo(HashSet()) { it.staffId }
    val allLeaveIds = (approvedOnDay + pendingOnDay).mapTo(HashSet()) { it.staffId }

    val restStaff = activeDutyStaff.filter {
      it.restDayEnum.dayOfWeek == date.dayOfWeek && it.id !in allLeaveIds
    }
    val restIds = restStaff.mapTo(HashSet()) { it.id }

    val confirmedUnavailableIds = approvedIds + activeDutyStaff
      .filter { it.restDayEnum.dayOfWeek == date.dayOfWeek }
      .map { it.id }
    val projectedUnavailableIds = allLeaveIds + restIds

    val coreDutyStaff = activeDutyStaff.filter { !it.isShunter }
    val availableConfirmedList = coreDutyStaff.filter { it.id !in confirmedUnavailableIds }
    val availableProjectedList = coreDutyStaff.filter { it.id !in projectedUnavailableIds }

    val medActive = date.minusDays(1).isMedDutyActive()
    val minRequiredTotal = BASE_MIN_DAILY_DUTY_STAFF + if (medActive) 1 else 0
    val requiredStation = BASE_MIN_STATION_STAFF + if (medActive) 1 else 0

    val availablePointsmen = availableProjectedList.count { it.isPointsman }
    val availableStation = availableProjectedList.count { it.stationDuty && !it.isPointsman }
    val totalLeaveCount = approvedOnDay.size + pendingOnDay.size

    val reasons = mutableListOf<String>()
    if (availableProjectedList.size < minRequiredTotal) {
      val deficit = minRequiredTotal - availableProjectedList.size
      reasons.add("Short by $deficit duty staff (need $minRequiredTotal, only ${availableProjectedList.size} free)")
    }
    if (availablePointsmen < MIN_POINTSMEN_PER_DAY) {
      reasons.add("Pointsmen short ($availablePointsmen/$MIN_POINTSMEN_PER_DAY)")
    }
    if (availableStation < requiredStation) {
      reasons.add("Station-qualified short ($availableStation/$requiredStation)")
    }

    val severity = when {
      coreDutyStaff.isEmpty() -> StaffTightnessLevel.NORMAL
      availableProjectedList.size < minRequiredTotal ||
        availablePointsmen < MIN_POINTSMEN_PER_DAY ||
        availableStation < requiredStation ||
        totalLeaveCount >= 5 -> StaffTightnessLevel.CRITICAL
      availableProjectedList.size <= minRequiredTotal + 3 ||
        availablePointsmen == MIN_POINTSMEN_PER_DAY ||
        availableStation <= requiredStation + 1 ||
        totalLeaveCount >= 3 -> StaffTightnessLevel.TIGHT
      else -> StaffTightnessLevel.NORMAL
    }

    if (reasons.isEmpty() && severity != StaffTightnessLevel.NORMAL) {
      val buffer = (availableProjectedList.size - minRequiredTotal).coerceAtLeast(0)
      reasons.add(
        buildString {
          append("Only $buffer spare staff")
          if (totalLeaveCount > 0) {
            append(" · $totalLeaveCount on leave")
          }
          if (restStaff.isNotEmpty()) {
            append(" · ${restStaff.size} on rest")
          }
        }
      )
    }

    return TightStaffDayForecast(
      date = date,
      approvedLeaves = approvedOnDay,
      pendingLeaves = pendingOnDay,
      restStaff = restStaff,
      totalActiveDutyStaff = coreDutyStaff.size,
      availableConfirmed = availableConfirmedList.size,
      availableProjected = availableProjectedList.size,
      minRequiredTotal = minRequiredTotal,
      availablePointsmen = availablePointsmen,
      requiredPointsmen = MIN_POINTSMEN_PER_DAY,
      availableStationQualified = availableStation,
      requiredStationQualified = requiredStation,
      severity = severity,
      summaryReason = reasons.joinToString(" · ").ifBlank { "Adequate coverage" }
    )
  }

  fun buildMonthlyLeaveCalendar(
    yearMonth: YearMonth,
    staffList: List<StaffMember>,
    leaves: List<LeaveRecord>
  ): MonthlyLeaveCalendarData {
    val activeDutyStaff = staffList.filter { !it.isSupervisory }
    val daysInMonth = yearMonth.lengthOfMonth()
    val dayInfos = ArrayList<LeaveCalendarDayInfo>(daysInMonth)
    val uniqueStaffIds = HashSet<String>()
    var totalApprovedDays = 0
    var totalPendingDays = 0
    var tightCount = 0
    var criticalCount = 0

    for (dayOfMonth in 1..daysInMonth) {
      val date = yearMonth.atDay(dayOfMonth)
      val eval = evaluateDayStaffing(date, activeDutyStaff, leaves)
      totalApprovedDays += eval.approvedLeaves.size
      totalPendingDays += eval.pendingLeaves.size
      eval.approvedLeaves.forEach { uniqueStaffIds.add(it.staffId) }
      eval.pendingLeaves.forEach { uniqueStaffIds.add(it.staffId) }
      when (eval.severity) {
        StaffTightnessLevel.CRITICAL -> criticalCount++
        StaffTightnessLevel.TIGHT -> tightCount++
        StaffTightnessLevel.NORMAL -> Unit
      }
      dayInfos.add(
        LeaveCalendarDayInfo(
          date = date,
          approvedLeaves = eval.approvedLeaves,
          pendingLeaves = eval.pendingLeaves,
          restStaff = eval.restStaff,
          activeDutyCount = eval.totalActiveDutyStaff,
          availableConfirmedCount = eval.availableConfirmed,
          availableProjectedCount = eval.availableProjected,
          minRequiredTotal = eval.minRequiredTotal,
          tightnessLevel = eval.severity
        )
      )
    }

    return MonthlyLeaveCalendarData(
      yearMonth = yearMonth,
      days = dayInfos,
      totalApprovedLeaveDays = totalApprovedDays,
      totalPendingLeaveDays = totalPendingDays,
      uniqueStaffOnLeave = uniqueStaffIds.size,
      tightDaysCount = tightCount,
      criticalDaysCount = criticalCount
    )
  }

  /**
   * Finds every (date, shiftType) where more than 5 staff are assigned in the shift
   * (e.g., 6+ staff in a shift), sorted chronologically with most recent/upcoming dates visible.
   */
  fun findOverFiveStaffShiftDates(
    assignments: List<ShiftSlotAssignment>
  ): List<ShiftOverFiveAlert> {
    if (assignments.isEmpty()) return emptyList()

    val grouped = assignments
      .filter { it.date.isNotBlank() && it.staffId.isNotBlank() }
      .groupBy { it.date to ShiftType.fromRaw(it.shiftType) }

    return grouped.mapNotNull { (key, slotAssignments) ->
      val distinctByStaff = slotAssignments.distinctBy { it.staffId }
      val totalStaff = distinctByStaff.size
      if (totalStaff <= 5) return@mapNotNull null

      val pointsmanCount = slotAssignments.count {
        it.roleTypeEnum == com.example.model.SlotRoleType.POINTSMAN
      }
      val shedCount = slotAssignments.count {
        it.dutyAreaEnum == DutyArea.SHED &&
          it.roleTypeEnum != com.example.model.SlotRoleType.POINTSMAN &&
          it.roleTypeEnum != com.example.model.SlotRoleType.SHUNTER
      }
      val stationCount = slotAssignments.count {
        it.dutyAreaEnum == DutyArea.STATION_SC || it.dutyAreaEnum == DutyArea.STATION_UR
      }
      val medCount = slotAssignments.count { it.dutyAreaEnum == DutyArea.STATION_MED }

      ShiftOverFiveAlert(
        date = key.first,
        shiftType = key.second,
        totalStaff = totalStaff,
        pointsmanCount = pointsmanCount,
        shedCount = shedCount,
        stationCount = stationCount,
        medCount = medCount,
        staffNames = distinctByStaff.map { it.staffName.ifBlank { it.staffId } }.sorted(),
        isShedOverFive = shedCount > 5
      )
    }.sortedWith(
      compareByDescending<ShiftOverFiveAlert> { it.date }
        .thenBy { it.shiftType.ordinal }
    )
  }

  /**
   * Scans future dates from [baseDate] through [baseDate] + [horizonDays] (inclusive)
   * and returns all dates where leave + weekly rest cause VERY TIGHT or CRITICAL staffing.
   * Prioritize dates impacted by leaves so supervisors see actionable leave-driven tightness.
   */
  fun forecastTightStaffDates(
    baseDate: LocalDate,
    horizonDays: Int = 30,
    staffList: List<StaffMember>,
    leaves: List<LeaveRecord>
  ): List<TightStaffDayForecast> {
    val activeDutyStaff = staffList.filter { !it.isSupervisory }
    if (activeDutyStaff.isEmpty()) return emptyList()

    val results = mutableListOf<TightStaffDayForecast>()
    for (offset in 0..horizonDays.coerceAtLeast(1)) {
      val date = baseDate.plusDays(offset.toLong())
      val eval = evaluateDayStaffing(date, activeDutyStaff, leaves)
      val hasLeaveImpact = eval.approvedLeaves.isNotEmpty() || eval.pendingLeaves.isNotEmpty()
      val isCriticalRegardless = eval.severity == StaffTightnessLevel.CRITICAL
      val isTightWithLeave = eval.severity == StaffTightnessLevel.TIGHT && hasLeaveImpact
      if (isCriticalRegardless || isTightWithLeave) {
        results.add(eval)
      }
    }
    return results.sortedWith(
      compareBy<TightStaffDayForecast> { it.date }
    )
  }

  fun computeStaffPerformanceMetrics(
    staffList: List<StaffMember>,
    assignments: List<ShiftSlotAssignment>,
    leaves: List<LeaveRecord>,
    overrides: List<EmergencyOverrideLog>,
    permissions: List<DutyPermissionRecord>,
    startDate: LocalDate,
    endDate: LocalDate
  ): List<StaffPerformanceRow> {
    val startStr = startDate.toString()
    val endStr = endDate.toString()
    val activeDutyStaff = staffList.filter { !it.isSupervisory }

    val rangeAssignmentsByStaff = assignments
      .filter { it.dutyDateStr in startStr..endStr && it.staffId.isNotBlank() }
      .groupBy { it.staffId }

    val rangeOverridesByStaff = overrides
      .filter { it.date in startStr..endStr && it.staffId.isNotBlank() }
      .groupBy { it.staffId }

    val rangePermissionsByStaff = permissions
      .filter { it.date in startStr..endStr && it.staffId.isNotBlank() }
      .groupBy { it.staffId }

    val leavesByStaff = leaves.groupBy { it.staffId }

    return activeDutyStaff.map { staff ->
      val staffAssigns = rangeAssignmentsByStaff[staff.id].orEmpty()
      val nightDuties = staffAssigns.count { it.shiftTypeEnum == ShiftType.SHIFT_22_06 }
      val morningDuties = staffAssigns.count { it.shiftTypeEnum == ShiftType.SHIFT_06_14 }
      val afternoonDuties = staffAssigns.count { it.shiftTypeEnum == ShiftType.SHIFT_14_22 }
      val generalDuties = staffAssigns.count { it.shiftTypeEnum == ShiftType.SHIFT_08_17 }

      // Rest-day & double-duty special leave credit calculation within [startDate, endDate]
      val restDayOfWeek = staff.restDayEnum.dayOfWeek
      val byDutyDate = staffAssigns.groupBy { assign ->
        val startLocal = runCatching { LocalDate.parse(assign.date) }.getOrNull() ?: return@groupBy null
        assign.shiftTypeEnum.dutyDateForStartDate(startLocal)
      }.filterKeys { it != null }

      var restDayDuties = 0
      var doubleDutyDays = 0
      var specialCredits = 0
      byDutyDate.forEach { (dutyDate, entries) ->
        if (dutyDate != null) {
          val distinctShifts = entries.map { it.shiftTypeEnum }.distinct()
          if (restDayOfWeek != null && dutyDate.dayOfWeek == restDayOfWeek && distinctShifts.isNotEmpty()) {
            restDayDuties++
            specialCredits++
          }
          if (distinctShifts.size >= 2) {
            doubleDutyDays++
            specialCredits += (distinctShifts.size - 1)
          }
        }
      }

      val staffLeaves = leavesByStaff[staff.id].orEmpty()
      var clDays = 0
      var lapDays = 0
      var lhapDays = 0
      var specialDays = 0
      var trainingDays = 0
      var pendingCount = 0

      staffLeaves.forEach { leave ->
        val lStart = runCatching { LocalDate.parse(leave.startDate) }.getOrNull() ?: return@forEach
        val lEnd = runCatching { LocalDate.parse(leave.endDate) }.getOrNull() ?: return@forEach
        if (lEnd.isBefore(startDate) || lStart.isAfter(endDate)) return@forEach

        if (leave.statusEnum == LeaveStatus.PENDING) {
          pendingCount++
        } else if (leave.statusEnum == LeaveStatus.APPROVED) {
          val clampedStart = if (lStart.isBefore(startDate)) startDate else lStart
          val clampedEnd = if (lEnd.isAfter(endDate)) endDate else lEnd
          val overlapDays = (ChronoUnit.DAYS.between(clampedStart, clampedEnd) + 1L).toInt().coerceAtLeast(0)
          when (leave.leaveTypeEnum) {
            LeaveType.CL -> clDays += overlapDays
            LeaveType.LAP -> lapDays += overlapDays
            LeaveType.LHAP -> lhapDays += overlapDays
            LeaveType.SPECIAL -> specialDays += overlapDays
            LeaveType.TRAINING -> trainingDays += overlapDays
          }
        }
      }

      val approvedTotalDays = clDays + lapDays + lhapDays + specialDays
      val staffPermissions = rangePermissionsByStaff[staff.id].orEmpty()
      val totalPermHours = staffPermissions.sumOf { it.hours }
      val overrideCount = rangeOverridesByStaff[staff.id]?.size ?: 0

      val tag = when {
        specialCredits >= 2 || (staffAssigns.size >= 15 && approvedTotalDays == 0 && totalPermHours <= 2.0) -> "Top Performer"
        approvedTotalDays >= 6 || totalPermHours >= 8.0 -> "High Absence / Relief"
        staffAssigns.isEmpty() -> "No Duties Logged"
        else -> "Steady"
      }

      StaffPerformanceRow(
        staff = staff,
        totalDuties = staffAssigns.size,
        nightDuties = nightDuties,
        morningDuties = morningDuties,
        afternoonDuties = afternoonDuties,
        generalDuties = generalDuties,
        restDayDuties = restDayDuties,
        doubleDutyDays = doubleDutyDays,
        specialLeaveCreditsEarned = specialCredits,
        approvedLeaveDays = approvedTotalDays,
        approvedClDays = clDays,
        approvedLapDays = lapDays,
        approvedLhapDays = lhapDays,
        approvedSpecialDays = specialDays,
        approvedTrainingDays = trainingDays,
        pendingLeaveCount = pendingCount,
        permissionCount = staffPermissions.size,
        totalPermissionHours = totalPermHours,
        emergencyOverridesCount = overrideCount,
        performanceTag = tag
      )
    }.sortedWith(
      compareByDescending<StaffPerformanceRow> { it.totalDuties }
        .thenBy { it.staff.serialNo }
    )
  }

  /**
   * Computes all duty technicians who have a technical training due date set,
   * ordered by urgency (overdue first, then due within 30 days, then upcoming).
   */
  fun computeTrainingDueTechnicians(
    baseDate: LocalDate,
    staffList: List<StaffMember>,
    leaves: List<LeaveRecord>
  ): List<TrainingDueTechnicianItem> {
    val baseStr = baseDate.toString()
    val dutyStaff = staffList.filter { !it.isSupervisory && it.trainingDueDate.isNotBlank() }
    return dutyStaff.mapNotNull { staff ->
      val parsedDue = runCatching { LocalDate.parse(staff.trainingDueDate.trim()) }.getOrNull()
        ?: return@mapNotNull null
      val daysUntil = ChronoUnit.DAYS.between(baseDate, parsedDue)
      val status = when {
        daysUntil < 0L -> TrainingDueStatus.OVERDUE
        daysUntil <= 30L -> TrainingDueStatus.DUE_SOON
        else -> TrainingDueStatus.UPCOMING
      }
      val activeTrainingLeave = leaves
        .filter {
          it.staffId == staff.id &&
            it.leaveTypeEnum == LeaveType.TRAINING &&
            (it.statusEnum == LeaveStatus.APPROVED || it.statusEnum == LeaveStatus.PENDING) &&
            it.endDate >= baseStr
        }
        .sortedBy { it.startDate }
        .firstOrNull()

      TrainingDueTechnicianItem(
        staff = staff,
        dueDate = parsedDue,
        daysUntilDue = daysUntil,
        status = status,
        course = staff.trainingCourse.trim(),
        activeTrainingLeave = activeTrainingLeave
      )
    }.sortedWith(
      compareBy<TrainingDueTechnicianItem> { it.dueDate }
        .thenBy { it.staff.serialNo }
    )
  }

  /**
   * Resolves the currently active shift and on-duty personnel for the given local date/time:
   * - 06:00–13:59 (e.g. 11:34 AM) -> today's 06/14 Morning Shift
   * - 14:00–21:59                 -> today's 14/22 Afternoon Shift
   * - 22:00–23:59                 -> today's 22/06 Night Shift
   * - 00:00–05:59 (e.g. 00:32 AM) -> yesterday's 22/06 Night Shift (overnight shift continuing to 06:00)
   * Also includes active 08/17 General Shift personnel between 08:00 and 16:59.
   */
  fun resolveLiveShiftSnapshot(
    now: LocalDateTime,
    staffList: List<StaffMember>,
    assignments: List<ShiftSlotAssignment>,
    leaves: List<LeaveRecord>
  ): LiveShiftStaffSnapshot {
    val today = now.toLocalDate()
    val hour = now.hour

    val activeShift: ShiftType
    val assignmentDate: LocalDate
    val operationalDutyDate: LocalDate
    val windowLabel: String

    when (hour) {
      in 6..13 -> {
        activeShift = ShiftType.SHIFT_06_14
        assignmentDate = today
        operationalDutyDate = today
        windowLabel = "06:00 – 14:00"
      }
      in 14..21 -> {
        activeShift = ShiftType.SHIFT_14_22
        assignmentDate = today
        operationalDutyDate = today
        windowLabel = "14:00 – 22:00"
      }
      in 22..23 -> {
        activeShift = ShiftType.SHIFT_22_06
        assignmentDate = today
        operationalDutyDate = today.plusDays(1)
        windowLabel = "22:00 – 06:00"
      }
      else -> {
        // 00:00 – 05:59: overnight 22/06 shift started yesterday at 22:00
        activeShift = ShiftType.SHIFT_22_06
        assignmentDate = today.minusDays(1)
        operationalDutyDate = today
        windowLabel = "22:00 – 06:00 (Overnight)"
      }
    }

    val staffById = staffList.associateBy { it.id }
    val assignmentDateStr = assignmentDate.toString()
    val todayStr = today.toString()

    val primaryShiftAssignments = assignments
      .filter { it.date == assignmentDateStr && it.shiftTypeEnum == activeShift && it.staffId.isNotBlank() }
      .sortedWith(compareBy<ShiftSlotAssignment>({ it.dutyAreaEnum.ordinal }, { it.roleTypeEnum.ordinal }, { it.slotIndex }))

    val primaryEntries = primaryShiftAssignments.map { assign ->
      LiveDutyStaffEntry(assignment = assign, staff = staffById[assign.staffId])
    }

    val pointsmanEntries = primaryEntries.filter {
      it.assignment.dutyAreaEnum == DutyArea.SHED && it.assignment.roleTypeEnum == com.example.model.SlotRoleType.POINTSMAN
    }
    val shunterEntries = primaryEntries.filter {
      it.assignment.dutyAreaEnum == DutyArea.SHED && it.assignment.roleTypeEnum == com.example.model.SlotRoleType.SHUNTER
    }
    val shedEntries = primaryEntries.filter {
      it.assignment.dutyAreaEnum == DutyArea.SHED &&
        it.assignment.roleTypeEnum != com.example.model.SlotRoleType.POINTSMAN &&
        it.assignment.roleTypeEnum != com.example.model.SlotRoleType.SHUNTER
    }
    val activeStationEntries = primaryEntries.filter { it.isStationArea }

    val generalShiftActive = hour in 8..16
    val generalEntries = if (generalShiftActive) {
      assignments
        .filter { it.date == todayStr && it.shiftTypeEnum == ShiftType.SHIFT_08_17 && it.staffId.isNotBlank() }
        .sortedBy { it.slotIndex }
        .map { assign -> LiveDutyStaffEntry(assignment = assign, staff = staffById[assign.staffId]) }
    } else {
      emptyList()
    }

    val allStationQualified = staffList
      .filter { !it.isSupervisory && it.stationDuty }
      .sortedBy { it.serialNo }

    val dutyStaff = staffList.filter { !it.isSupervisory }
    val onLeaveTodayCount = dutyStaff.count { m -> leaves.any { it.staffId == m.id && it.isBlockingDate(todayStr) } }
    val onRestTodayCount = dutyStaff.count { it.restDayEnum.dayOfWeek == today.dayOfWeek }

    return LiveShiftStaffSnapshot(
      now = now,
      activeShift = activeShift,
      activeShiftAssignmentDate = assignmentDate,
      operationalDutyDate = operationalDutyDate,
      activeShiftWindowLabel = windowLabel,
      pointsmanStaff = pointsmanEntries,
      shunterStaff = shunterEntries,
      shedStaff = shedEntries,
      activeStationStaff = activeStationEntries,
      generalShiftActive = generalShiftActive,
      generalShiftStaff = generalEntries,
      allStationQualifiedStaff = allStationQualified,
      onLeaveTodayCount = onLeaveTodayCount,
      onRestTodayCount = onRestTodayCount
    )
  }
}
