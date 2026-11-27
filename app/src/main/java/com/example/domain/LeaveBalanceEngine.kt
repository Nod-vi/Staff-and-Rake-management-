package com.example.domain

import com.example.model.DutyArea
import com.example.model.LapUpdateStatus
import com.example.model.LeaveRecord
import com.example.model.LeaveStatus
import com.example.model.LeaveType
import com.example.model.ShiftSlotAssignment
import com.example.model.ShiftType
import com.example.model.StaffLeaveAccount
import com.example.model.StaffMember
import com.example.model.WeeklyRestDay
import java.time.LocalDate
import java.time.temporal.ChronoUnit

enum class SpecialLeaveCreditReason(val label: String) {
  REST_DAY_DUTY("Rest Day Duty"),
  DOUBLE_DUTY("2 Duties in Single Day")
}

data class SpecialLeaveCreditEvent(
  val date: LocalDate,
  val reason: SpecialLeaveCreditReason,
  val detail: String,
  val credits: Int = 1
)

data class StaffLeaveBalanceSummary(
  val staff: StaffMember,
  val year: Int,
  val clEntitlement: Int,
  val clAvailed: Int,
  val clPending: Int,
  val clRemaining: Int,
  val clExpiresOn: LocalDate,
  val lapOpeningForYear: Int,
  val lapAnnualCredit: Int,
  val lapAvailed: Int,
  val lapPending: Int,
  val lapRemaining: Int,
  val lhapOpeningForYear: Int,
  val lhapAnnualCredit: Int,
  val lhapAvailed: Int,
  val lhapPending: Int,
  val lhapRemaining: Int,
  val specialEarnedThisYear: Int,
  val specialEarnedTotal: Int,
  val specialRestDayCreditsThisYear: Int,
  val specialDoubleDutyCreditsThisYear: Int,
  val specialAvailedThisYear: Int,
  val specialAvailedTotal: Int,
  val specialPendingThisYear: Int,
  val specialRemaining: Int,
  val specialCreditEventsInYear: List<SpecialLeaveCreditEvent>,
  val specialCreditEventsAll: List<SpecialLeaveCreditEvent>,
  val leavesInYear: List<LeaveRecord>,
  val approvedLeavesInYear: List<LeaveRecord>,
  val account: StaffLeaveAccount?,
  val lapSelfUpdated: Boolean,
  val hasPendingLapRequest: Boolean,
  val trainingAvailed: Int = 0,
  val trainingPending: Int = 0
)

data class StaffDutyItem(
  val assignment: ShiftSlotAssignment,
  val shiftType: ShiftType,
  val dutyArea: DutyArea
)

data class StaffUpcomingDutySummary(
  val tomorrowDutyDate: LocalDate,
  val tonightDate: LocalDate,
  val tomorrowDuties: List<StaffDutyItem>,
  val todayDuties: List<StaffDutyItem>,
  val isTomorrowWeeklyRest: Boolean,
  val tomorrowApprovedLeave: LeaveRecord?
)

object LeaveBalanceEngine {

  const val ANNUAL_CL = 10
  const val ANNUAL_LAP = 30
  const val ANNUAL_LHAP = 20

  fun daysInYear(leave: LeaveRecord, year: Int): Int {
    val start = runCatching { LocalDate.parse(leave.startDate) }.getOrNull() ?: return 0
    val end = runCatching { LocalDate.parse(leave.endDate) }.getOrNull() ?: return 0
    if (end.isBefore(start)) return 0
    val yearStart = LocalDate.of(year, 1, 1)
    val yearEnd = LocalDate.of(year, 12, 31)
    val clippedStart = if (start.isBefore(yearStart)) yearStart else start
    val clippedEnd = if (end.isAfter(yearEnd)) yearEnd else end
    if (clippedEnd.isBefore(clippedStart)) return 0
    return (ChronoUnit.DAYS.between(clippedStart, clippedEnd) + 1L).toInt()
  }

  fun approvedDaysInYear(
    staffId: String,
    leaveType: LeaveType,
    year: Int,
    allLeaves: List<LeaveRecord>
  ): Int = allLeaves
    .asSequence()
    .filter { it.staffId == staffId && it.leaveTypeEnum == leaveType && it.statusEnum == LeaveStatus.APPROVED }
    .sumOf { daysInYear(it, year) }

  fun pendingDaysInYear(
    staffId: String,
    leaveType: LeaveType,
    year: Int,
    allLeaves: List<LeaveRecord>
  ): Int = allLeaves
    .asSequence()
    .filter { it.staffId == staffId && it.leaveTypeEnum == leaveType && it.statusEnum == LeaveStatus.PENDING }
    .sumOf { daysInYear(it, year) }

  fun computeOpeningBalanceForEnteredRemaining(enteredRemaining: Int, availedInYear: Int): Int {
    return (enteredRemaining.coerceAtLeast(0) + availedInYear.coerceAtLeast(0)).coerceIn(0, 999)
  }

  /**
   * Computes carry-forward opening balance for [targetYear] for a carry-forward leave type (LAP or LHAP).
   * Unused balances at the end of each year carry forward into the next year and receive [annualCredit].
   */
  fun computeCarryForwardOpeningForYear(
    staffId: String,
    leaveType: LeaveType,
    targetYear: Int,
    annualCredit: Int,
    accountBaseYear: Int?,
    accountBaseOpening: Int?,
    allLeaves: List<LeaveRecord>
  ): Int {
    val baseYear = accountBaseYear?.takeIf { it in 2020..2100 } ?: targetYear
    val baseOpening = (accountBaseOpening ?: annualCredit).coerceAtLeast(0)
    if (targetYear == baseYear) {
      return baseOpening
    }
    if (targetYear > baseYear) {
      var runningOpening = baseOpening
      for (y in baseYear until targetYear) {
        val used = approvedDaysInYear(staffId, leaveType, y, allLeaves)
        val unusedEndOfYear = (runningOpening - used).coerceAtLeast(0)
        runningOpening = unusedEndOfYear + annualCredit
      }
      return runningOpening
    }
    // Looking at a year before the recorded baseYear: show that year's standard annual entitlement
    return annualCredit
  }

  /**
   * Awards Special Leave credits whenever a technician:
   * 1. Performs any duty on their scheduled Weekly Rest Day (+1 Special Leave per rest day worked)
   * 2. Performs 2 or more duties on the same calendar date (+1 Special Leave per extra duty)
   */
  fun computeSpecialLeaveCredits(
    staff: StaffMember,
    allAssignments: List<ShiftSlotAssignment>
  ): List<SpecialLeaveCreditEvent> {
    val staffAssignments = allAssignments.filter { it.staffId == staff.id }
    if (staffAssignments.isEmpty()) return emptyList()

    val events = mutableListOf<SpecialLeaveCreditEvent>()
    val byDate = staffAssignments.mapNotNull { assign ->
      val startDate = runCatching { LocalDate.parse(assign.date) }.getOrNull() ?: return@mapNotNull null
      val dutyDate = assign.shiftTypeEnum.dutyDateForStartDate(startDate)
      dutyDate to assign
    }.groupBy({ it.first }, { it.second })

    val shiftOrder = mapOf(
      ShiftType.SHIFT_22_06 to 0,
      ShiftType.SHIFT_06_14 to 1,
      ShiftType.SHIFT_08_17 to 2,
      ShiftType.SHIFT_14_22 to 3
    )

    byDate.forEach { (date, daySlots) ->
      val ordered = daySlots.sortedBy { shiftOrder[it.shiftTypeEnum] ?: 9 }
      val summaryText = ordered.joinToString(" + ") {
        "${it.shiftTypeEnum.code} ${it.dutyAreaEnum.shortCode}"
      }

      if (staff.restDayEnum != WeeklyRestDay.NONE && date.dayOfWeek == staff.restDayEnum.dayOfWeek) {
        events.add(
          SpecialLeaveCreditEvent(
            date = date,
            reason = SpecialLeaveCreditReason.REST_DAY_DUTY,
            detail = "Worked on ${staff.restDayEnum.label} Rest ($summaryText)",
            credits = 1
          )
        )
      }

      if (ordered.size >= 2) {
        val extraDuties = ordered.size - 1
        events.add(
          SpecialLeaveCreditEvent(
            date = date,
            reason = SpecialLeaveCreditReason.DOUBLE_DUTY,
            detail = "${ordered.size} duties on same day ($summaryText)",
            credits = extraDuties
          )
        )
      }
    }

    return events.sortedWith(compareByDescending<SpecialLeaveCreditEvent> { it.date }.thenBy { it.reason.ordinal })
  }

  fun computeBalanceSummary(
    staff: StaffMember,
    year: Int,
    account: StaffLeaveAccount?,
    allLeaves: List<LeaveRecord>,
    allAssignments: List<ShiftSlotAssignment>
  ): StaffLeaveBalanceSummary {
    val clAvailed = approvedDaysInYear(staff.id, LeaveType.CL, year, allLeaves)
    val clPending = pendingDaysInYear(staff.id, LeaveType.CL, year, allLeaves)
    val clRemaining = (ANNUAL_CL - clAvailed).coerceAtLeast(0)

    val lapOpening = computeCarryForwardOpeningForYear(
      staffId = staff.id,
      leaveType = LeaveType.LAP,
      targetYear = year,
      annualCredit = ANNUAL_LAP,
      accountBaseYear = account?.baseYear,
      accountBaseOpening = account?.lapOpeningBalance,
      allLeaves = allLeaves
    )
    val lapAvailed = approvedDaysInYear(staff.id, LeaveType.LAP, year, allLeaves)
    val lapPending = pendingDaysInYear(staff.id, LeaveType.LAP, year, allLeaves)
    val lapRemaining = (lapOpening - lapAvailed).coerceAtLeast(0)

    val lhapOpening = computeCarryForwardOpeningForYear(
      staffId = staff.id,
      leaveType = LeaveType.LHAP,
      targetYear = year,
      annualCredit = ANNUAL_LHAP,
      accountBaseYear = account?.baseYear,
      accountBaseOpening = account?.lhapOpeningBalance,
      allLeaves = allLeaves
    )
    val lhapAvailed = approvedDaysInYear(staff.id, LeaveType.LHAP, year, allLeaves)
    val lhapPending = pendingDaysInYear(staff.id, LeaveType.LHAP, year, allLeaves)
    val lhapRemaining = (lhapOpening - lhapAvailed).coerceAtLeast(0)

    val allSpecialEvents = computeSpecialLeaveCredits(staff, allAssignments)
    val eventsUpToYear = allSpecialEvents.filter { it.date.year <= year }
    val eventsInYear = allSpecialEvents.filter { it.date.year == year }

    val specialEarnedThisYear = eventsInYear.sumOf { it.credits }
    val specialEarnedTotal = eventsUpToYear.sumOf { it.credits }
    val specialRestDayThisYear = eventsInYear
      .filter { it.reason == SpecialLeaveCreditReason.REST_DAY_DUTY }
      .sumOf { it.credits }
    val specialDoubleDutyThisYear = eventsInYear
      .filter { it.reason == SpecialLeaveCreditReason.DOUBLE_DUTY }
      .sumOf { it.credits }

    val specialAvailedThisYear = approvedDaysInYear(staff.id, LeaveType.SPECIAL, year, allLeaves)
    val specialPendingThisYear = pendingDaysInYear(staff.id, LeaveType.SPECIAL, year, allLeaves)
    val specialAvailedTotal = allLeaves
      .asSequence()
      .filter { it.staffId == staff.id && it.leaveTypeEnum == LeaveType.SPECIAL && it.statusEnum == LeaveStatus.APPROVED }
      .sumOf { leave ->
        val start = runCatching { LocalDate.parse(leave.startDate) }.getOrNull() ?: return@sumOf 0
        val end = runCatching { LocalDate.parse(leave.endDate) }.getOrNull() ?: return@sumOf 0
        val cap = LocalDate.of(year, 12, 31)
        val clippedEnd = if (end.isAfter(cap)) cap else end
        if (clippedEnd.isBefore(start)) 0 else (ChronoUnit.DAYS.between(start, clippedEnd) + 1L).toInt()
      }
    val specialRemaining = (specialEarnedTotal - specialAvailedTotal).coerceAtLeast(0)
    val trainingAvailed = approvedDaysInYear(staff.id, LeaveType.TRAINING, year, allLeaves)
    val trainingPending = pendingDaysInYear(staff.id, LeaveType.TRAINING, year, allLeaves)

    val leavesInYear = allLeaves
      .filter { it.staffId == staff.id && daysInYear(it, year) > 0 }
      .sortedByDescending { it.startDate }
    val approvedLeavesInYear = leavesInYear.filter { it.statusEnum == LeaveStatus.APPROVED }

    return StaffLeaveBalanceSummary(
      staff = staff,
      year = year,
      clEntitlement = ANNUAL_CL,
      clAvailed = clAvailed,
      clPending = clPending,
      clRemaining = clRemaining,
      clExpiresOn = LocalDate.of(year, 12, 31),
      lapOpeningForYear = lapOpening,
      lapAnnualCredit = ANNUAL_LAP,
      lapAvailed = lapAvailed,
      lapPending = lapPending,
      lapRemaining = lapRemaining,
      lhapOpeningForYear = lhapOpening,
      lhapAnnualCredit = ANNUAL_LHAP,
      lhapAvailed = lhapAvailed,
      lhapPending = lhapPending,
      lhapRemaining = lhapRemaining,
      specialEarnedThisYear = specialEarnedThisYear,
      specialEarnedTotal = specialEarnedTotal,
      specialRestDayCreditsThisYear = specialRestDayThisYear,
      specialDoubleDutyCreditsThisYear = specialDoubleDutyThisYear,
      specialAvailedThisYear = specialAvailedThisYear,
      specialAvailedTotal = specialAvailedTotal,
      specialPendingThisYear = specialPendingThisYear,
      specialRemaining = specialRemaining,
      specialCreditEventsInYear = eventsInYear,
      specialCreditEventsAll = eventsUpToYear,
      leavesInYear = leavesInYear,
      approvedLeavesInYear = approvedLeavesInYear,
      account = account,
      lapSelfUpdated = account?.lapSelfUpdated == true,
      hasPendingLapRequest = account?.pendingLapStatusEnum == LapUpdateStatus.PENDING,
      trainingAvailed = trainingAvailed,
      trainingPending = trainingPending
    )
  }

  /**
   * Builds a technician's Tomorrow's Duties summary (covering tonight's 22/06 shift +
   * tomorrow's 06/14, 08/17, 14/22 shifts) plus today's shifts for quick reference.
   */
  fun buildUpcomingDutiesForStaff(
    staff: StaffMember,
    baseToday: LocalDate,
    allAssignments: List<ShiftSlotAssignment>,
    allLeaves: List<LeaveRecord>
  ): StaffUpcomingDutySummary {
    val tomorrow = baseToday.plusDays(1)
    val todayStr = baseToday.toString()
    val tomorrowStr = tomorrow.toString()

    val shiftOrder = mapOf(
      ShiftType.SHIFT_22_06 to 0,
      ShiftType.SHIFT_06_14 to 1,
      ShiftType.SHIFT_08_17 to 2,
      ShiftType.SHIFT_14_22 to 3
    )

    val staffAssignments = allAssignments.filter { it.staffId == staff.id }

    val tomorrowDuties = staffAssignments
      .filter { assignment ->
        val sType = assignment.shiftTypeEnum
        assignment.date == sType.startDateForDutyDate(tomorrow).toString()
      }
      .map { StaffDutyItem(it, it.shiftTypeEnum, it.dutyAreaEnum) }
      .sortedBy { shiftOrder[it.shiftType] ?: 9 }

    val todayDuties = staffAssignments
      .filter { assignment ->
        val sType = assignment.shiftTypeEnum
        assignment.date == sType.startDateForDutyDate(baseToday).toString()
      }
      .map { StaffDutyItem(it, it.shiftTypeEnum, it.dutyAreaEnum) }
      .sortedBy { shiftOrder[it.shiftType] ?: 9 }

    val tomorrowLeave = allLeaves.firstOrNull {
      it.staffId == staff.id && it.isBlockingDate(tomorrowStr)
    }

    val isTomorrowRest = staff.restDayEnum != WeeklyRestDay.NONE &&
      staff.restDayEnum.dayOfWeek == tomorrow.dayOfWeek

    return StaffUpcomingDutySummary(
      tomorrowDutyDate = tomorrow,
      tonightDate = baseToday,
      tomorrowDuties = tomorrowDuties,
      todayDuties = todayDuties,
      isTomorrowWeeklyRest = isTomorrowRest,
      tomorrowApprovedLeave = tomorrowLeave
    )
  }
}
