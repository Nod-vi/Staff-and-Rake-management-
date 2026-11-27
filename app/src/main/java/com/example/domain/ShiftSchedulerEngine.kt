package com.example.domain

import com.example.model.DutyArea
import com.example.model.LeaveRecord
import com.example.model.ShiftSlotAssignment
import com.example.model.ShiftSlotSpec
import com.example.model.ShiftType
import com.example.model.SlotRoleType
import com.example.model.StaffMember
import com.example.model.WeeklyRestDay
import com.example.model.isMedDutyActive
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

enum class ConflictReason(val label: String, val isHardExclusion: Boolean) {
  ON_LEAVE("On approved leave", true),
  SUPERVISORY_ONLY("Supervisory (SSE/JE) — Non-duty", false),
  WEEKLY_REST("Scheduled weekly rest day", false),
  REQUIRES_POINTSMAN("Slot requires Pointsman certification", false),
  REQUIRES_SHUNTER("Slot requires Shunter designation", true),
  SHUNTER_ONLY("Shunter can only be assigned to Shunter slots", true),
  DUTY_AREA_MISMATCH("Not qualified for this duty area", false),
  SAME_SHIFT_DUPLICATE("Already assigned in this shift", false),
  CONSECUTIVE_SHIFT_REST("Already assigned for this duty day / mandatory rest violation", false)
}

data class CandidateEvaluation(
  val staff: StaffMember,
  val conflicts: List<ConflictReason>,
  val weeklyShiftCount: Int
) {
  val isFullyEligible: Boolean get() = conflicts.isEmpty()
  val isOnLeave: Boolean get() = conflicts.any { it == ConflictReason.ON_LEAVE }
  val primaryConflictLabel: String?
    get() = conflicts.firstOrNull()?.label
}

data class ShiftCoverageSummary(
  val shiftType: ShiftType,
  val pointsmanFilled: Int,
  val pointsmanTarget: Int,
  val shedFilled: Int,
  val shedMinTarget: Int,
  val shedMaxTarget: Int,
  val stationFilled: Int,
  val stationTarget: Int,
  val medActive: Boolean,
  val medFilled: Int,
  val medTarget: Int,
  val shunterFilled: Int = 0
) {
  val isPointsmanCompliant: Boolean get() = pointsmanFilled >= pointsmanTarget
  val isShedCompliant: Boolean get() = shedFilled in shedMinTarget..shedMaxTarget
  val isStationCompliant: Boolean get() = stationFilled == stationTarget
  val isMedCompliant: Boolean get() = !medActive || medFilled == medTarget
  val isFullyCompliant: Boolean
    get() = isPointsmanCompliant && isShedCompliant && isStationCompliant && isMedCompliant
}

object ShiftSchedulerEngine {
  const val SHUNTER_SLOT_INDEX = 6

  fun buildDaySlots(date: LocalDate): List<ShiftSlotSpec> {
    val slots = mutableListOf<ShiftSlotSpec>()
    ShiftType.entries.forEach { shift ->
      slots += buildSlotsForShift(date, shift)
    }
    return slots
  }

  /**
   * Builds the operational duty cycle for [dutyDate]:
   * starts with previous day's 22/06 night shift (e.g. 16th 22/06 for 17th duty),
   * followed by [dutyDate]'s 06/14, 14/22, and 08/17 shifts.
   */
  fun buildDutyDaySlots(dutyDate: LocalDate): List<ShiftSlotSpec> {
    val slots = mutableListOf<ShiftSlotSpec>()
    ShiftType.entries.forEach { shift ->
      slots += buildSlotsForShift(shift.startDateForDutyDate(dutyDate), shift)
    }
    return slots
  }

  fun buildSlotsForShift(date: LocalDate, shift: ShiftType): List<ShiftSlotSpec> {
    val dateStr = date.toString()
    val slots = mutableListOf<ShiftSlotSpec>()
    if (shift.isGeneralShift) {
      // Dedicated optional Shunter slot in General Shift (08/17)
      slots += ShiftSlotSpec(
        date = dateStr,
        shiftType = shift,
        dutyArea = DutyArea.SHED,
        roleType = SlotRoleType.SHUNTER,
        slotIndex = SHUNTER_SLOT_INDEX,
        label = "General Shunter (Optional)",
        isRequired = false
      )
      // General Shift (08/17): 0 to 3 personnel depending on availability
      for (idx in 0..2) {
        slots += ShiftSlotSpec(
          date = dateStr,
          shiftType = shift,
          dutyArea = DutyArea.SHED,
          roleType = SlotRoleType.GENERAL_DUTY,
          slotIndex = idx,
          label = "General Staff ${idx + 1} (Optional)",
          isRequired = false
        )
      }
    } else {
      // Dedicated Pointsman slot (1 per shift)
      slots += ShiftSlotSpec(
        date = dateStr,
        shiftType = shift,
        dutyArea = DutyArea.SHED,
        roleType = SlotRoleType.POINTSMAN,
        slotIndex = 0,
        label = "Shed Pointsman",
        isRequired = true
      )
      // Dedicated Shunter slot (1 per shift, similar to Pointsman)
      slots += ShiftSlotSpec(
        date = dateStr,
        shiftType = shift,
        dutyArea = DutyArea.SHED,
        roleType = SlotRoleType.SHUNTER,
        slotIndex = SHUNTER_SLOT_INDEX,
        label = "Shed Shunter",
        isRequired = false
      )
      // Other than Pointsman & Shunter: 3 to 5 personnel in Shed per shift
      for (idx in 1..3) {
        slots += ShiftSlotSpec(
          date = dateStr,
          shiftType = shift,
          dutyArea = DutyArea.SHED,
          roleType = SlotRoleType.GENERAL_DUTY,
          slotIndex = idx,
          label = "Shed Duty $idx",
          isRequired = true
        )
      }
      for (idx in 4..5) {
        slots += ShiftSlotSpec(
          date = dateStr,
          shiftType = shift,
          dutyArea = DutyArea.SHED,
          roleType = SlotRoleType.GENERAL_DUTY,
          slotIndex = idx,
          label = "Shed Duty $idx (Optional)",
          isRequired = false
        )
      }

      // Station Duty: 2 personnel per shift (SC for 06/14 and 14/22; UR for 22/06)
      val stationArea = shift.stationArea
      if (stationArea != null) {
        for (idx in 0..1) {
          slots += ShiftSlotSpec(
            date = dateStr,
            shiftType = shift,
            dutyArea = stationArea,
            roleType = SlotRoleType.GENERAL_DUTY,
            slotIndex = idx,
            label = "${stationArea.label} ${idx + 1}",
            isRequired = true
          )
        }
      }

      // Medchal (MED) Station Duty: 1 Station-qualified person in 22/06 Night Shift Mon Night – Thu Night only
      if (shift == ShiftType.SHIFT_22_06 && date.isMedDutyActive()) {
        slots += ShiftSlotSpec(
          date = dateStr,
          shiftType = shift,
          dutyArea = DutyArea.STATION_MED,
          roleType = SlotRoleType.GENERAL_DUTY,
          slotIndex = 0,
          label = "Medchal (MED) (Mon–Thu Night)",
          isRequired = true
        )
      }
    }
    return slots
  }

  fun evaluateCandidatesForSlot(
    slot: ShiftSlotSpec,
    staffList: List<StaffMember>,
    assignments: List<ShiftSlotAssignment>,
    leaves: List<LeaveRecord>
  ): List<CandidateEvaluation> {
    val targetDate = LocalDate.parse(slot.date)
    val dutyDate = slot.shiftType.dutyDateForStartDate(targetDate)
    val dutyDateStr = dutyDate.toString()
    val weekStart = dutyDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    val weekEnd = weekStart.plusDays(6)
    val weekStartStr = weekStart.toString()
    val weekEndStr = weekEnd.toString()
    val prevDateStr = targetDate.minusDays(1).toString()
    val nextDateStr = targetDate.plusDays(1).toString()

    val weeklyCountByStaff = HashMap<String, Int>(staffList.size * 2)
    val relevantAssignmentsByStaff = HashMap<String, MutableList<ShiftSlotAssignment>>(staffList.size * 2)
    for (a in assignments) {
      if (a.dutyDateStr in weekStartStr..weekEndStr) {
        weeklyCountByStaff[a.staffId] = (weeklyCountByStaff[a.staffId] ?: 0) + 1
      }
      if (a.id != slot.documentId && (a.date == slot.date || a.date == prevDateStr || a.date == nextDateStr)) {
        relevantAssignmentsByStaff.getOrPut(a.staffId) { ArrayList(3) }.add(a)
      }
    }

    val leavesByStaff = HashMap<String, MutableList<LeaveRecord>>()
    for (l in leaves) {
      if (l.isBlockingDate(dutyDateStr)) {
        leavesByStaff.getOrPut(l.staffId) { ArrayList(1) }.add(l)
      }
    }

    val dutyStaff = staffList.filter { staff ->
      if (slot.roleType == SlotRoleType.SHUNTER) {
        staff.isShunter
      } else {
        !staff.isSupervisory && !staff.isShunter
      }
    }

    return dutyStaff
      .map { staff ->
        val conflicts = detectConflictsIndexed(
          staff = staff,
          slot = slot,
          dutyDate = dutyDate,
          prevDateStr = prevDateStr,
          nextDateStr = nextDateStr,
          staffRelevantAssignments = relevantAssignmentsByStaff[staff.id] ?: emptyList(),
          hasActiveLeaveToday = leavesByStaff.containsKey(staff.id)
        )
        CandidateEvaluation(
          staff = staff,
          conflicts = conflicts,
          weeklyShiftCount = weeklyCountByStaff[staff.id] ?: 0
        )
      }
      .sortedWith(
        compareBy<CandidateEvaluation>(
          { !it.isFullyEligible },
          { if (slot.roleType == SlotRoleType.SHUNTER) !it.staff.isShunter else false },
          { it.isOnLeave },
          { it.conflicts.size },
          { it.weeklyShiftCount },
          { it.staff.serialNo }
        )
      )
  }

  fun detectConflicts(
    staff: StaffMember,
    slot: ShiftSlotSpec,
    targetDate: LocalDate,
    assignments: List<ShiftSlotAssignment>,
    leaves: List<LeaveRecord>
  ): List<ConflictReason> {
    val dateStr = slot.date
    val dutyDate = slot.shiftType.dutyDateForStartDate(targetDate)
    val dutyDateStr = dutyDate.toString()
    val prevDateStr = targetDate.minusDays(1).toString()
    val nextDateStr = targetDate.plusDays(1).toString()
    val relevant = assignments.filter {
      it.id != slot.documentId &&
        it.staffId == staff.id &&
        (it.date == dateStr || it.date == prevDateStr || it.date == nextDateStr)
    }
    val hasLeave = leaves.any { it.staffId == staff.id && it.isBlockingDate(dutyDateStr) }
    return detectConflictsIndexed(
      staff = staff,
      slot = slot,
      dutyDate = dutyDate,
      prevDateStr = prevDateStr,
      nextDateStr = nextDateStr,
      staffRelevantAssignments = relevant,
      hasActiveLeaveToday = hasLeave
    )
  }

  private fun detectConflictsIndexed(
    staff: StaffMember,
    slot: ShiftSlotSpec,
    dutyDate: LocalDate,
    prevDateStr: String,
    nextDateStr: String,
    staffRelevantAssignments: List<ShiftSlotAssignment>,
    hasActiveLeaveToday: Boolean
  ): List<ConflictReason> {
    val conflicts = ArrayList<ConflictReason>(3)
    val dateStr = slot.date

    if (hasActiveLeaveToday) {
      conflicts += ConflictReason.ON_LEAVE
    }
    if (staff.isSupervisory) {
      conflicts += ConflictReason.SUPERVISORY_ONLY
    }
    if (staff.restDayEnum.dayOfWeek == dutyDate.dayOfWeek) {
      conflicts += ConflictReason.WEEKLY_REST
    }

    // Pointsman role strictly requires Pointsman competency;
    // Pointsmen are also permitted in Shed/Station/General roles if qualified via shedDuty / stationDuty.
    if (slot.roleType == SlotRoleType.POINTSMAN && !staff.isPointsman) {
      conflicts += ConflictReason.REQUIRES_POINTSMAN
    }

    // Shunter slots strictly require Shunter-designation staff, and Shunter staff can ONLY
    // be assigned to Shunter slots (never Shed, Station, Pointsman, or regular General Shift).
    if (staff.isShunter) {
      if (slot.roleType != SlotRoleType.SHUNTER) {
        conflicts += ConflictReason.SHUNTER_ONLY
      }
    } else if (slot.roleType == SlotRoleType.SHUNTER) {
      conflicts += ConflictReason.REQUIRES_SHUNTER
    } else {
      // Duty qualification rules for non-Shunter staff:
      // Shed & General Shift require shedDuty; SC, UR, and MED require stationDuty.
      when (slot.dutyArea) {
        DutyArea.SHED -> if (!staff.shedDuty) conflicts += ConflictReason.DUTY_AREA_MISMATCH
        DutyArea.STATION_SC,
        DutyArea.STATION_UR,
        DutyArea.STATION_MED -> if (!staff.stationDuty) conflicts += ConflictReason.DUTY_AREA_MISMATCH
      }
    }

    // Same shift duplicate check
    if (staffRelevantAssignments.any { it.date == dateStr && it.shiftType == slot.shiftType.name }) {
      conflicts += ConflictReason.SAME_SHIFT_DUPLICATE
    }

    // One assignment per duty day (prev day 22/06 + day 06/14, 14/22, 08/17) + transition rest check
    val hasRestViolation = when (slot.shiftType) {
      ShiftType.SHIFT_22_06 -> staffRelevantAssignments.any {
        (it.date == dateStr && (
          it.shiftType == ShiftType.SHIFT_14_22.name ||
            it.shiftType == ShiftType.SHIFT_08_17.name
          )) ||
          (it.date == nextDateStr && (
            it.shiftType == ShiftType.SHIFT_06_14.name ||
              it.shiftType == ShiftType.SHIFT_14_22.name ||
              it.shiftType == ShiftType.SHIFT_08_17.name
            ))
      }
      ShiftType.SHIFT_06_14 -> staffRelevantAssignments.any {
        (it.date == prevDateStr && it.shiftType == ShiftType.SHIFT_22_06.name) ||
          (it.date == dateStr && (
            it.shiftType == ShiftType.SHIFT_14_22.name ||
              it.shiftType == ShiftType.SHIFT_08_17.name
            ))
      }
      ShiftType.SHIFT_14_22 -> staffRelevantAssignments.any {
        (it.date == prevDateStr && it.shiftType == ShiftType.SHIFT_22_06.name) ||
          (it.date == dateStr && (
            it.shiftType == ShiftType.SHIFT_06_14.name ||
              it.shiftType == ShiftType.SHIFT_22_06.name ||
              it.shiftType == ShiftType.SHIFT_08_17.name
            ))
      }
      ShiftType.SHIFT_08_17 -> staffRelevantAssignments.any {
        (it.date == prevDateStr && it.shiftType == ShiftType.SHIFT_22_06.name) ||
          (it.date == dateStr && (
            it.shiftType == ShiftType.SHIFT_06_14.name ||
              it.shiftType == ShiftType.SHIFT_14_22.name ||
              it.shiftType == ShiftType.SHIFT_22_06.name
            ))
      }
    }
    if (hasRestViolation) {
      conflicts += ConflictReason.CONSECUTIVE_SHIFT_REST
    }

    return conflicts
  }

  fun suggestReplacements(
    slot: ShiftSlotSpec,
    staffList: List<StaffMember>,
    assignments: List<ShiftSlotAssignment>,
    leaves: List<LeaveRecord>,
    excludeStaffId: String? = null,
    limit: Int = 5
  ): List<CandidateEvaluation> {
    return evaluateCandidatesForSlot(slot, staffList, assignments, leaves)
      .filter { it.staff.id != excludeStaffId && it.isFullyEligible }
      .take(limit)
  }

  fun computeShiftCoverage(
    date: LocalDate,
    assignments: List<ShiftSlotAssignment>
  ): List<ShiftCoverageSummary> {
    val dateStr = date.toString()
    val dayAssignments = assignments.filter { it.date == dateStr }
    val medActive = date.isMedDutyActive()

    return ShiftType.entries.map { shift ->
      computeCoverageForShift(
        shift = shift,
        shiftItems = dayAssignments.filter { it.shiftType == shift.name },
        medActiveForDate = medActive
      )
    }
  }

  /**
   * Computes coverage for the operational duty cycle of [dutyDate]:
   * previous day's 22/06 night shift + [dutyDate]'s 06/14, 14/22, and 08/17 shifts.
   */
  fun computeDutyDayCoverage(
    dutyDate: LocalDate,
    assignments: List<ShiftSlotAssignment>
  ): List<ShiftCoverageSummary> {
    val prevDate = dutyDate.minusDays(1)
    val dutyDateStr = dutyDate.toString()
    val prevDateStr = prevDate.toString()
    val relevant = assignments.filter { it.date == dutyDateStr || it.date == prevDateStr }

    return ShiftType.entries.map { shift ->
      val shiftDate = shift.startDateForDutyDate(dutyDate)
      val shiftDateStr = shiftDate.toString()
      computeCoverageForShift(
        shift = shift,
        shiftItems = relevant.filter { it.date == shiftDateStr && it.shiftType == shift.name },
        medActiveForDate = shiftDate.isMedDutyActive()
      )
    }
  }

  private fun computeCoverageForShift(
    shift: ShiftType,
    shiftItems: List<ShiftSlotAssignment>,
    medActiveForDate: Boolean
  ): ShiftCoverageSummary {
    return if (shift.isGeneralShift) {
      val shunterCount = shiftItems.count {
        it.dutyArea == DutyArea.SHED.name && it.roleType == SlotRoleType.SHUNTER.name
      }
      val generalCount = shiftItems.count {
        it.dutyArea == DutyArea.SHED.name && it.roleType != SlotRoleType.SHUNTER.name
      }
      ShiftCoverageSummary(
        shiftType = shift,
        pointsmanFilled = 0,
        pointsmanTarget = 0,
        shedFilled = generalCount,
        shedMinTarget = 0,
        shedMaxTarget = 3,
        stationFilled = 0,
        stationTarget = 0,
        medActive = false,
        medFilled = 0,
        medTarget = 0,
        shunterFilled = shunterCount
      )
    } else {
      val pointsmanCount = shiftItems.count {
        it.dutyArea == DutyArea.SHED.name && it.roleType == SlotRoleType.POINTSMAN.name
      }
      val shunterCount = shiftItems.count {
        it.dutyArea == DutyArea.SHED.name && it.roleType == SlotRoleType.SHUNTER.name
      }
      val shedNonPointsmanCount = shiftItems.count {
        it.dutyArea == DutyArea.SHED.name &&
          it.roleType != SlotRoleType.POINTSMAN.name &&
          it.roleType != SlotRoleType.SHUNTER.name
      }
      val stationCount = shift.stationArea?.let { area ->
        shiftItems.count { it.dutyArea == area.name }
      } ?: 0
      val medCount = shiftItems.count { it.dutyArea == DutyArea.STATION_MED.name }

      ShiftCoverageSummary(
        shiftType = shift,
        pointsmanFilled = pointsmanCount,
        pointsmanTarget = 1,
        shedFilled = shedNonPointsmanCount,
        shedMinTarget = 3,
        shedMaxTarget = 5,
        stationFilled = stationCount,
        stationTarget = 2,
        medActive = shift == ShiftType.SHIFT_22_06 && medActiveForDate,
        medFilled = medCount,
        medTarget = if (shift == ShiftType.SHIFT_22_06 && medActiveForDate) 1 else 0,
        shunterFilled = shunterCount
      )
    }
  }

  /**
   * Automatically assigns missing required slots (Pointsman, Station, MED, 3..5 Shed per shift)
   * and optional General Shift (08/17) 0..3 personnel depending on remaining availability.
   */
  fun generateAssignmentsForDates(
    userId: String,
    dates: List<LocalDate>,
    staffList: List<StaffMember>,
    existingAssignments: List<ShiftSlotAssignment>,
    leaves: List<LeaveRecord>,
    overwriteNonOverrides: Boolean = false
  ): List<ShiftSlotAssignment> {
    val slotGroups = dates.map { date -> buildDaySlots(date) }
    val targetSlotIds = slotGroups.flatten().mapTo(HashSet()) { it.documentId }
    return generateAssignmentsForSlotGroups(
      userId = userId,
      slotGroups = slotGroups,
      targetSlotIds = targetSlotIds,
      staffList = staffList,
      existingAssignments = existingAssignments,
      leaves = leaves,
      overwriteNonOverrides = overwriteNonOverrides
    )
  }

  /**
   * Automatically assigns missing slots across operational duty cycles for [dutyDates],
   * where each duty date starts with `(dutyDate - 1) 22/06` followed by `(dutyDate) 06/14, 14/22, 08/17`.
   */
  fun generateAssignmentsForDutyDates(
    userId: String,
    dutyDates: List<LocalDate>,
    staffList: List<StaffMember>,
    existingAssignments: List<ShiftSlotAssignment>,
    leaves: List<LeaveRecord>,
    overwriteNonOverrides: Boolean = false
  ): List<ShiftSlotAssignment> {
    val slotGroups = dutyDates.map { dutyDate -> buildDutyDaySlots(dutyDate) }
    val targetSlotIds = slotGroups.flatten().mapTo(HashSet()) { it.documentId }
    return generateAssignmentsForSlotGroups(
      userId = userId,
      slotGroups = slotGroups,
      targetSlotIds = targetSlotIds,
      staffList = staffList,
      existingAssignments = existingAssignments,
      leaves = leaves,
      overwriteNonOverrides = overwriteNonOverrides
    )
  }

  private fun generateAssignmentsForSlotGroups(
    userId: String,
    slotGroups: List<List<ShiftSlotSpec>>,
    targetSlotIds: Set<String>,
    staffList: List<StaffMember>,
    existingAssignments: List<ShiftSlotAssignment>,
    leaves: List<LeaveRecord>,
    overwriteNonOverrides: Boolean
  ): List<ShiftSlotAssignment> {
    val workingAssignments = if (overwriteNonOverrides) {
      existingAssignments.filter { it.isEmergencyOverride || !targetSlotIds.contains(it.id) }.toMutableList()
    } else {
      existingAssignments.toMutableList()
    }

    val createdOrUpdated = mutableListOf<ShiftSlotAssignment>()

    for (daySlots in slotGroups) {
      val orderedSlots = daySlots.sortedWith(
        compareBy<ShiftSlotSpec>(
          { spec ->
            when {
              spec.roleType == SlotRoleType.POINTSMAN -> 0
              spec.dutyArea == DutyArea.STATION_MED -> 1
              spec.dutyArea == DutyArea.STATION_SC || spec.dutyArea == DutyArea.STATION_UR -> 2
              !spec.shiftType.isGeneralShift && spec.isRequired -> 3
              !spec.shiftType.isGeneralShift && spec.slotIndex == 4 -> 4
              spec.shiftType.isGeneralShift -> 5
              else -> 6
            }
          },
          { it.slotIndex },
          { it.shiftType.ordinal }
        )
      )

      for (slot in orderedSlots) {
        // Shunter is assigned manually per shift by Supervisor/Admin/Storemate so auto-assign
        // preserves existing Shed/Station/Pointsman technician allocation.
        if (slot.roleType == SlotRoleType.SHUNTER) continue
        val docId = slot.documentId
        val alreadyAssigned = workingAssignments.any { it.id == docId }
        if (alreadyAssigned) continue

        val candidates = evaluateCandidatesForSlot(slot, staffList, workingAssignments, leaves)
          .filter { it.isFullyEligible }
          .sortedWith(
            compareBy<CandidateEvaluation>(
              { it.weeklyShiftCount },
              // For non-Pointsman slots, prefer non-Pointsman staff first so Pointsmen remain available for Pointsman slots
              {
                if (slot.roleType != SlotRoleType.POINTSMAN && it.staff.isPointsman) 1 else 0
              },
              // For Station/MED slots, prefer Station-only staff first so Dual staff remain available for Shed
              {
                if (slot.dutyArea != DutyArea.SHED && it.staff.shedDuty) 1 else 0
              },
              // For Shed/General slots, prefer Shed-only staff first so Dual staff remain available for Station
              {
                if (slot.dutyArea == DutyArea.SHED && it.staff.stationDuty) 1 else 0
              },
              { it.staff.serialNo }
            )
          )

        val chosen = candidates.firstOrNull()?.staff ?: continue
        val newAssignment = ShiftSlotAssignment(
          id = docId,
          userId = userId,
          date = slot.date,
          shiftType = slot.shiftType.name,
          dutyArea = slot.dutyArea.name,
          roleType = slot.roleType.name,
          slotIndex = slot.slotIndex.toLong(),
          staffId = chosen.id,
          staffName = chosen.name,
          isEmergencyOverride = false,
          overrideReason = ""
        )
        workingAssignments += newAssignment
        createdOrUpdated += newAssignment
      }
    }

    return createdOrUpdated
  }

  /**
   * Distributes staggered weekly rest days fairly across active duty staff.
   */
  fun computeBalancedRestDays(staffList: List<StaffMember>): Map<String, WeeklyRestDay> {
    val active = staffList.filter { !it.isSupervisory }
    val days = listOf(
      WeeklyRestDay.SUNDAY,
      WeeklyRestDay.MONDAY,
      WeeklyRestDay.TUESDAY,
      WeeklyRestDay.WEDNESDAY,
      WeeklyRestDay.THURSDAY,
      WeeklyRestDay.FRIDAY,
      WeeklyRestDay.SATURDAY
    )
    val pointsmen = active.filter { it.isPointsman }
    val stationNonPm = active.filter { !it.isPointsman && it.stationDuty }
    val shedOnly = active.filter { !it.isPointsman && !it.stationDuty }

    val result = mutableMapOf<String, WeeklyRestDay>()
    listOf(pointsmen, stationNonPm, shedOnly).forEach { group ->
      group.forEachIndexed { index, member ->
        result[member.id] = days[index % days.size]
      }
    }
    return result
  }
}
