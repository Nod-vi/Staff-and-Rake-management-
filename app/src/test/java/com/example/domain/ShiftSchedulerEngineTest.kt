package com.example.domain

import com.example.data.DefaultPpioRoster
import com.example.model.DutyArea
import com.example.model.LeaveRecord
import com.example.model.LeaveStatus
import com.example.model.LeaveType
import com.example.model.ShiftSlotAssignment
import com.example.model.ShiftType
import com.example.model.SlotRoleType
import com.example.model.WeeklyRestDay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ShiftSchedulerEngineTest {

  private val staff = DefaultPpioRoster.initialStaff("test_uid")

  @Test
  fun buildDaySlots_includesMedOnlyMondayToThursday() {
    val monday = LocalDate.of(2026, 11, 16) // Monday
    val thursday = LocalDate.of(2026, 11, 19) // Thursday
    val friday = LocalDate.of(2026, 11, 20) // Friday
    val sunday = LocalDate.of(2026, 11, 22) // Sunday

    assertTrue(ShiftSchedulerEngine.buildDaySlots(monday).any { it.dutyArea == DutyArea.STATION_MED })
    assertTrue(ShiftSchedulerEngine.buildDaySlots(thursday).any { it.dutyArea == DutyArea.STATION_MED })
    assertFalse(ShiftSchedulerEngine.buildDaySlots(friday).any { it.dutyArea == DutyArea.STATION_MED })
    assertFalse(ShiftSchedulerEngine.buildDaySlots(sunday).any { it.dutyArea == DutyArea.STATION_MED })
  }

  @Test
  fun pointsmanAndShedStationRules_enforcePointsmanCompetencyAndHideSupervisors() {
    val monday = LocalDate.of(2026, 11, 16)
    val slots = ShiftSchedulerEngine.buildDaySlots(monday)
    val pointsmanSlot = slots.first { it.shiftType == ShiftType.SHIFT_06_14 && it.roleType == SlotRoleType.POINTSMAN }
    val generalShedSlot = slots.first { it.shiftType == ShiftType.SHIFT_06_14 && it.dutyArea == DutyArea.SHED && it.roleType == SlotRoleType.GENERAL_DUTY }
    val scStationSlot = slots.first { it.shiftType == ShiftType.SHIFT_06_14 && it.dutyArea == DutyArea.STATION_SC }

    val gRajesh = staff.first { it.name == "G. RAJESH" } // Station=YES, Shed=YES, Competency=POINTSMAN
    val rNagaraju = staff.first { it.name == "R. NAGARAJU" } // Station=NO, Shed=YES, Competency=POINTSMAN
    val rYadagiri = staff.first { it.name == "R. YADAGIRI" } // Station=NO, Shed=YES, Competency=NONE
    val pRamKumar = staff.first { it.name == "P. RAM KUMAR" } // Station=YES, Shed=NO, Competency=NONE

    // Pointsmen can work Pointsman, Shed (if shed=YES), and Station (if station=YES)
    assertTrue(ShiftSchedulerEngine.detectConflicts(gRajesh, pointsmanSlot, monday, emptyList(), emptyList()).isEmpty())
    assertTrue(ShiftSchedulerEngine.detectConflicts(gRajesh, generalShedSlot, monday, emptyList(), emptyList()).isEmpty())
    assertTrue(ShiftSchedulerEngine.detectConflicts(gRajesh, scStationSlot, monday, emptyList(), emptyList()).isEmpty())

    assertTrue(ShiftSchedulerEngine.detectConflicts(rNagaraju, pointsmanSlot, monday, emptyList(), emptyList()).isEmpty())
    assertTrue(ShiftSchedulerEngine.detectConflicts(rNagaraju, generalShedSlot, monday, emptyList(), emptyList()).isEmpty())
    assertTrue(
      ShiftSchedulerEngine.detectConflicts(rNagaraju, scStationSlot, monday, emptyList(), emptyList())
        .contains(ConflictReason.DUTY_AREA_MISMATCH)
    )

    // Non-Pointsmen cannot be assigned to Pointsman slots
    assertTrue(
      ShiftSchedulerEngine.detectConflicts(rYadagiri, pointsmanSlot, monday, emptyList(), emptyList())
        .contains(ConflictReason.REQUIRES_POINTSMAN)
    )
    assertTrue(
      ShiftSchedulerEngine.detectConflicts(pRamKumar, pointsmanSlot, monday, emptyList(), emptyList())
        .contains(ConflictReason.REQUIRES_POINTSMAN)
    )

    // Supervisors must not appear in candidate evaluation lists
    val evals = ShiftSchedulerEngine.evaluateCandidatesForSlot(generalShedSlot, staff, emptyList(), emptyList())
    assertFalse(evals.any { it.staff.isSupervisory })
    assertEquals(staff.count { !it.isSupervisory }, evals.size)
  }

  @Test
  fun medDuty_restrictedToStationStaffOnly() {
    val monday = LocalDate.of(2026, 11, 16)
    val medSlot = ShiftSchedulerEngine.buildDaySlots(monday).first { it.dutyArea == DutyArea.STATION_MED }

    val stationOnlyStaff = staff.first { it.name == "P. RAM KUMAR" }
    val shedOnlyStaff = staff.first { it.name == "R. YADAGIRI" }

    assertTrue(ShiftSchedulerEngine.detectConflicts(stationOnlyStaff, medSlot, monday, emptyList(), emptyList()).isEmpty())
    assertTrue(
      ShiftSchedulerEngine.detectConflicts(shedOnlyStaff, medSlot, monday, emptyList(), emptyList())
        .contains(ConflictReason.DUTY_AREA_MISMATCH)
    )
  }

  @Test
  fun leaveAndPostNightRest_blockAssignmentAndSuggestReplacements() {
    val monday = LocalDate.of(2026, 11, 16)
    val tuesday = LocalDate.of(2026, 11, 17)
    val scMorningTue = ShiftSchedulerEngine.buildDaySlots(tuesday)
      .first { it.shiftType == ShiftType.SHIFT_06_14 && it.dutyArea == DutyArea.STATION_SC }

    val ramKumar = staff.first { it.name == "P. RAM KUMAR" }
    val danielRaj = staff.first { it.name == "K. DANIEL RAJ" }

    val leaves = listOf(
      LeaveRecord(
        id = "l1",
        staffId = ramKumar.id,
        staffName = ramKumar.name,
        leaveType = LeaveType.CL.name,
        startDate = "2026-11-16",
        endDate = "2026-11-18"
      )
    )
    assertTrue(
      ShiftSchedulerEngine.detectConflicts(ramKumar, scMorningTue, tuesday, emptyList(), leaves)
        .contains(ConflictReason.ON_LEAVE)
    )

    val prevNightAssignment = listOf(
      ShiftSlotAssignment(
        id = "2026-11-16_SHIFT_22_06_STATION_UR_0",
        date = monday.toString(),
        shiftType = ShiftType.SHIFT_22_06.name,
        dutyArea = DutyArea.STATION_UR.name,
        roleType = SlotRoleType.GENERAL_DUTY.name,
        slotIndex = 0,
        staffId = danielRaj.id,
        staffName = danielRaj.name
      )
    )
    assertTrue(
      ShiftSchedulerEngine.detectConflicts(danielRaj, scMorningTue, tuesday, prevNightAssignment, emptyList())
        .contains(ConflictReason.CONSECUTIVE_SHIFT_REST)
    )

    val suggestions = ShiftSchedulerEngine.suggestReplacements(
      slot = scMorningTue,
      staffList = staff,
      assignments = prevNightAssignment,
      leaves = leaves,
      excludeStaffId = ramKumar.id
    )
    assertTrue(suggestions.isNotEmpty())
    assertFalse(suggestions.any { it.staff.id == danielRaj.id || it.staff.id == ramKumar.id })
  }

  @Test
  fun autoAssignDay_achievesFullComplianceWithBalancedRest() {
    val monday = LocalDate.of(2026, 11, 16)
    val restMap = ShiftSchedulerEngine.computeBalancedRestDays(staff)
    val staffWithRest = staff.map { m ->
      m.copy(weeklyRestDay = (restMap[m.id] ?: WeeklyRestDay.NONE).name)
    }

    val generated = ShiftSchedulerEngine.generateAssignmentsForDates(
      userId = "test_uid",
      dates = listOf(monday),
      staffList = staffWithRest,
      existingAssignments = emptyList(),
      leaves = emptyList()
    )

    val coverage = ShiftSchedulerEngine.computeShiftCoverage(monday, generated)
    assertEquals(4, coverage.size)
    coverage.forEach { summary ->
      assertTrue("Pointsman failed for ${summary.shiftType}", summary.isPointsmanCompliant)
      assertTrue("Shed target failed for ${summary.shiftType}", summary.isShedCompliant)
      assertTrue("Station target failed for ${summary.shiftType}", summary.isStationCompliant)
      assertTrue("MED target failed for ${summary.shiftType}", summary.isMedCompliant)
    }

    val generalSummary = coverage.first { it.shiftType == ShiftType.SHIFT_08_17 }
    assertTrue("General shift must be 0..3", generalSummary.shedFilled in 0..3)
    ShiftType.entries.filter { !it.isGeneralShift }.forEach { regularShift ->
      val s = coverage.first { it.shiftType == regularShift }
      assertEquals(1, s.pointsmanFilled)
      assertTrue("Non-Pointsman Shed must be 3..5 for $regularShift", s.shedFilled in 3..5)
    }
  }

  @Test
  fun generalShift_08_17_preventsSameDayOrPostNightDoubleBooking() {
    val monday = LocalDate.of(2026, 11, 16)
    val tuesday = LocalDate.of(2026, 11, 17)
    val yadagiri = staff.first { it.name == "R. YADAGIRI" } // Shed=YES, Pointsman=NONE

    val generalSlotTue = ShiftSchedulerEngine.buildDaySlots(tuesday)
      .first { it.shiftType == ShiftType.SHIFT_08_17 && it.roleType != SlotRoleType.SHUNTER }
    assertEquals(
      3,
      ShiftSchedulerEngine.buildDaySlots(tuesday)
        .count { it.shiftType == ShiftType.SHIFT_08_17 && it.roleType != SlotRoleType.SHUNTER }
    )

    val nightMonAssignment = listOf(
      ShiftSlotAssignment(
        id = "2026-11-16_SHIFT_22_06_SHED_1",
        date = monday.toString(),
        shiftType = ShiftType.SHIFT_22_06.name,
        dutyArea = DutyArea.SHED.name,
        roleType = SlotRoleType.GENERAL_DUTY.name,
        slotIndex = 1,
        staffId = yadagiri.id,
        staffName = yadagiri.name
      )
    )
    assertTrue(
      ShiftSchedulerEngine.detectConflicts(yadagiri, generalSlotTue, tuesday, nightMonAssignment, emptyList())
        .contains(ConflictReason.CONSECUTIVE_SHIFT_REST)
    )

    val morningTueAssignment = listOf(
      ShiftSlotAssignment(
        id = "2026-11-17_SHIFT_06_14_SHED_1",
        date = tuesday.toString(),
        shiftType = ShiftType.SHIFT_06_14.name,
        dutyArea = DutyArea.SHED.name,
        roleType = SlotRoleType.GENERAL_DUTY.name,
        slotIndex = 1,
        staffId = yadagiri.id,
        staffName = yadagiri.name
      )
    )
    assertTrue(
      ShiftSchedulerEngine.detectConflicts(yadagiri, generalSlotTue, tuesday, morningTueAssignment, emptyList())
        .contains(ConflictReason.CONSECUTIVE_SHIFT_REST)
    )
  }

  @Test
  fun leavePeriod_excludesStaffAcrossInclusiveRangeAndRestoresAfterEnd() {
    val gRajesh = staff.first { it.name == "G. RAJESH" }
    val ramKumar = staff.first { it.name == "P. RAM KUMAR" }
    val leaves = listOf(
      LeaveRecord(
        id = "leave_rajesh",
        staffId = gRajesh.id,
        staffName = gRajesh.name,
        leaveType = LeaveType.LAP.name,
        startDate = "2026-11-17",
        endDate = "2026-11-19"
      ),
      LeaveRecord(
        id = "leave_ram",
        staffId = ramKumar.id,
        staffName = ramKumar.name,
        leaveType = LeaveType.LHAP.name,
        startDate = "2026-11-17",
        endDate = "2026-11-19"
      )
    )

    val dayBefore = LocalDate.of(2026, 11, 16)
    val startDay = LocalDate.of(2026, 11, 17)
    val midDay = LocalDate.of(2026, 11, 18)
    val endDay = LocalDate.of(2026, 11, 19)
    val dayAfter = LocalDate.of(2026, 11, 20)

    listOf(startDay, midDay, endDay).forEach { day ->
      val scSlot = ShiftSchedulerEngine.buildDaySlots(day)
        .first { it.shiftType == ShiftType.SHIFT_06_14 && it.dutyArea == DutyArea.STATION_SC }
      val evals = ShiftSchedulerEngine.evaluateCandidatesForSlot(scSlot, staff, emptyList(), leaves)
      assertTrue(evals.first { it.staff.id == ramKumar.id }.isOnLeave)
      assertFalse(evals.first { it.staff.id == ramKumar.id }.isFullyEligible)
    }

    val slotBefore = ShiftSchedulerEngine.buildDaySlots(dayBefore)
      .first { it.shiftType == ShiftType.SHIFT_06_14 && it.dutyArea == DutyArea.STATION_SC }
    val slotAfter = ShiftSchedulerEngine.buildDaySlots(dayAfter)
      .first { it.shiftType == ShiftType.SHIFT_06_14 && it.dutyArea == DutyArea.STATION_SC }
    assertTrue(
      ShiftSchedulerEngine.evaluateCandidatesForSlot(slotBefore, staff, emptyList(), leaves)
        .first { it.staff.id == ramKumar.id }.isFullyEligible
    )
    assertTrue(
      ShiftSchedulerEngine.evaluateCandidatesForSlot(slotAfter, staff, emptyList(), leaves)
        .first { it.staff.id == ramKumar.id }.isFullyEligible
    )

    val generated = ShiftSchedulerEngine.generateAssignmentsForDutyDates(
      userId = "test_uid",
      dutyDates = listOf(startDay, midDay, endDay),
      staffList = staff,
      existingAssignments = emptyList(),
      leaves = leaves
    )
    assertFalse(generated.any { it.staffId == gRajesh.id || it.staffId == ramKumar.id })
  }

  @Test
  fun leaveToday_allowsTodayNight2206DutyForTomorrow_andBlocksYesterdayNight2206BelongingToToday() {
    val yesterday = LocalDate.of(2026, 11, 16)
    val today = LocalDate.of(2026, 11, 17)
    val tomorrow = LocalDate.of(2026, 11, 18)

    val ramKumar = staff.first { it.name == "P. RAM KUMAR" } // Station=YES, Rest=NONE
    val leaveTodayOnly = listOf(
      LeaveRecord(
        id = "leave_today_only",
        staffId = ramKumar.id,
        staffName = ramKumar.name,
        leaveType = LeaveType.CL.name,
        startDate = today.toString(),
        endDate = today.toString(),
        status = LeaveStatus.APPROVED.name
      )
    )

    // Yesterday's 22/06 shift (2026-11-16 22:00 - 2026-11-17 06:00) belongs to Today's duty date (2026-11-17)
    val yesterdaysNightSlot = ShiftSchedulerEngine.buildDutyDaySlots(today)
      .first { it.shiftType == ShiftType.SHIFT_22_06 && it.dutyArea == DutyArea.STATION_UR }
    assertEquals(yesterday.toString(), yesterdaysNightSlot.date)

    val yesterdayNightConflicts = ShiftSchedulerEngine.detectConflicts(
      staff = ramKumar,
      slot = yesterdaysNightSlot,
      targetDate = yesterday,
      assignments = emptyList(),
      leaves = leaveTodayOnly
    )
    assertTrue(yesterdayNightConflicts.contains(ConflictReason.ON_LEAVE))
    val yesterdayNightCandidate = ShiftSchedulerEngine.evaluateCandidatesForSlot(
      slot = yesterdaysNightSlot,
      staffList = staff,
      assignments = emptyList(),
      leaves = leaveTodayOnly
    ).first { it.staff.id == ramKumar.id }
    assertTrue(yesterdayNightCandidate.isOnLeave)
    assertFalse(yesterdayNightCandidate.isFullyEligible)

    // Today's 22/06 shift (2026-11-17 22:00 - 2026-11-18 06:00) belongs to Tomorrow's duty date (2026-11-18)
    val todaysNightSlot = ShiftSchedulerEngine.buildDutyDaySlots(tomorrow)
      .first { it.shiftType == ShiftType.SHIFT_22_06 && it.dutyArea == DutyArea.STATION_UR }
    assertEquals(today.toString(), todaysNightSlot.date)

    val todayNightConflicts = ShiftSchedulerEngine.detectConflicts(
      staff = ramKumar,
      slot = todaysNightSlot,
      targetDate = today,
      assignments = emptyList(),
      leaves = leaveTodayOnly
    )
    assertFalse(todayNightConflicts.contains(ConflictReason.ON_LEAVE))
    val todayNightCandidate = ShiftSchedulerEngine.evaluateCandidatesForSlot(
      slot = todaysNightSlot,
      staffList = staff,
      assignments = emptyList(),
      leaves = leaveTodayOnly
    ).first { it.staff.id == ramKumar.id }
    assertFalse(todayNightCandidate.isOnLeave)
    assertTrue(todayNightCandidate.isFullyEligible)

    // Today's morning shift (2026-11-17 06/14) still belongs to Today's duty date -> blocked
    val todaysMorningSlot = ShiftSchedulerEngine.buildDutyDaySlots(today)
      .first { it.shiftType == ShiftType.SHIFT_06_14 && it.dutyArea == DutyArea.STATION_SC }
    assertTrue(
      ShiftSchedulerEngine.detectConflicts(
        staff = ramKumar,
        slot = todaysMorningSlot,
        targetDate = today,
        assignments = emptyList(),
        leaves = leaveTodayOnly
      ).contains(ConflictReason.ON_LEAVE)
    )
  }

  @Test
  fun dutyDayCycle_startsWithPrevDayNightShift2206AndEnforcesPostNightRest() {
    val dutyDate17 = LocalDate.of(2026, 11, 17) // Tuesday
    val prevDate16 = LocalDate.of(2026, 11, 16) // Monday

    val dutySlots = ShiftSchedulerEngine.buildDutyDaySlots(dutyDate17)
    // First slot must be 22/06 on 16th
    assertEquals(ShiftType.SHIFT_22_06, dutySlots.first().shiftType)
    assertEquals(prevDate16.toString(), dutySlots.first().date)
    // MED slot is active because 16th is Monday
    assertTrue(dutySlots.any { it.dutyArea == DutyArea.STATION_MED && it.date == prevDate16.toString() })

    // Verify order of distinct shift types: 22/06 -> 06/14 -> 14/22 -> 08/17
    val shiftOrder = dutySlots.map { it.shiftType }.distinct()
    assertEquals(
      listOf(ShiftType.SHIFT_22_06, ShiftType.SHIFT_06_14, ShiftType.SHIFT_14_22, ShiftType.SHIFT_08_17),
      shiftOrder
    )
    // Verify 06/14, 14/22, 08/17 belong to 17th
    dutySlots.filter { it.shiftType != ShiftType.SHIFT_22_06 }.forEach { slot ->
      assertEquals(dutyDate17.toString(), slot.date)
    }

    val generated = ShiftSchedulerEngine.generateAssignmentsForDutyDates(
      userId = "test_uid",
      dutyDates = listOf(dutyDate17),
      staffList = staff,
      existingAssignments = emptyList(),
      leaves = emptyList()
    )

    val coverage = ShiftSchedulerEngine.computeDutyDayCoverage(dutyDate17, generated)
    assertEquals(4, coverage.size)
    assertEquals(ShiftType.SHIFT_22_06, coverage.first().shiftType)
    coverage.forEach { summary ->
      assertTrue("Pointsman failed for ${summary.shiftType}", summary.isPointsmanCompliant)
      assertTrue("Shed failed for ${summary.shiftType}", summary.isShedCompliant)
      assertTrue("Station failed for ${summary.shiftType}", summary.isStationCompliant)
      assertTrue("MED failed for ${summary.shiftType}", summary.isMedCompliant)
    }

    // Staff assigned to 16th 22/06 must not be double-booked onto 17th 06/14, 14/22, or 08/17 (same duty day)
    val nightStaffIds = generated
      .filter { it.date == prevDate16.toString() && it.shiftType == ShiftType.SHIFT_22_06.name }
      .map { it.staffId }
      .toSet()
    val dayConflictIds = generated
      .filter { it.date == dutyDate17.toString() }
      .map { it.staffId }
      .toSet()
    assertTrue(nightStaffIds.intersect(dayConflictIds).isEmpty())

    // Explicit check: someone assigned to 16th 22/06 is blocked from 17th 14/22 as well
    val afternoonSlot17 = dutySlots.first { it.shiftType == ShiftType.SHIFT_14_22 && it.dutyArea == DutyArea.STATION_SC }
    val scMorningSlot17 = dutySlots.first { it.shiftType == ShiftType.SHIFT_06_14 && it.dutyArea == DutyArea.STATION_SC }
    val ramKumar = staff.first { it.name == "P. RAM KUMAR" }
    val nightAssignment16 = listOf(
      ShiftSlotAssignment(
        id = "2026-11-16_SHIFT_22_06_STATION_UR_0",
        date = prevDate16.toString(),
        shiftType = ShiftType.SHIFT_22_06.name,
        dutyArea = DutyArea.STATION_UR.name,
        roleType = SlotRoleType.GENERAL_DUTY.name,
        slotIndex = 0,
        staffId = ramKumar.id,
        staffName = ramKumar.name
      )
    )
    assertTrue(
      ShiftSchedulerEngine.detectConflicts(ramKumar, afternoonSlot17, dutyDate17, nightAssignment16, emptyList())
        .contains(ConflictReason.CONSECUTIVE_SHIFT_REST)
    )
    assertTrue(
      ShiftSchedulerEngine.detectConflicts(ramKumar, scMorningSlot17, dutyDate17, nightAssignment16, emptyList())
        .contains(ConflictReason.CONSECUTIVE_SHIFT_REST)
    )
  }

  @Test
  fun leaveStatus_onlyApprovedBlocksShiftDutyAvailability() {
    val targetDate = LocalDate.of(2026, 11, 18)
    val slot = ShiftSchedulerEngine.buildDaySlots(targetDate)
      .first { it.shiftType == ShiftType.SHIFT_06_14 && it.dutyArea == DutyArea.STATION_SC }
    val ramKumar = staff.first { it.name == "P. RAM KUMAR" }

    val approvedLeave = LeaveRecord(
      id = "l_app",
      staffId = ramKumar.id,
      staffName = ramKumar.name,
      leaveType = LeaveType.CL.name,
      startDate = "2026-11-17",
      endDate = "2026-11-19",
      status = LeaveStatus.APPROVED.name
    )
    assertTrue(
      ShiftSchedulerEngine.detectConflicts(ramKumar, slot, targetDate, emptyList(), listOf(approvedLeave))
        .contains(ConflictReason.ON_LEAVE)
    )

    listOf(LeaveStatus.PENDING, LeaveStatus.REJECTED, LeaveStatus.CANCELLED).forEach { nonBlockingStatus ->
      val nonBlockingLeave = approvedLeave.copy(status = nonBlockingStatus.name)
      assertFalse(
        "Status $nonBlockingStatus should not block availability",
        ShiftSchedulerEngine.detectConflicts(ramKumar, slot, targetDate, emptyList(), listOf(nonBlockingLeave))
          .contains(ConflictReason.ON_LEAVE)
      )
    }
  }

  @Test
  fun weeklyRest_filtersTodayNightDutyByTomorrowRest_andYesterdayNightPlusDayShiftsByTodayRest() {
    val yesterday = LocalDate.of(2026, 11, 16) // Monday
    val today = LocalDate.of(2026, 11, 17)     // Tuesday
    val tomorrow = LocalDate.of(2026, 11, 18)  // Wednesday

    val baseStaff = staff.first { it.name == "P. RAM KUMAR" }.copy(stationDuty = true, shedDuty = true)
    val restYesterday = baseStaff.copy(id = "s_mon", name = "REST MON", weeklyRestDay = WeeklyRestDay.MONDAY.name)
    val restToday = baseStaff.copy(id = "s_tue", name = "REST TUE", weeklyRestDay = WeeklyRestDay.TUESDAY.name)
    val restTomorrow = baseStaff.copy(id = "s_wed", name = "REST WED", weeklyRestDay = WeeklyRestDay.WEDNESDAY.name)
    val testRoster = listOf(restYesterday, restToday, restTomorrow)

    // 1. Today's 22/06 night duty (starts today 17th, attendance belongs to tomorrow 18th = Wednesday)
    val todaysNightSlot = ShiftSchedulerEngine.buildDutyDaySlots(tomorrow)
      .first { it.shiftType == ShiftType.SHIFT_22_06 && it.dutyArea == DutyArea.STATION_UR }
    assertEquals(today.toString(), todaysNightSlot.date)

    val tonightEvals = ShiftSchedulerEngine.evaluateCandidatesForSlot(
      slot = todaysNightSlot,
      staffList = testRoster,
      assignments = emptyList(),
      leaves = emptyList()
    )
    assertTrue("Rest-today staff can work tonight's 22/06 (tomorrow's duty)", tonightEvals.first { it.staff.id == restToday.id }.isFullyEligible)
    assertFalse("Rest-tomorrow staff must be filtered out of tonight's 22/06", tonightEvals.first { it.staff.id == restTomorrow.id }.isFullyEligible)
    assertTrue(tonightEvals.first { it.staff.id == restTomorrow.id }.conflicts.contains(ConflictReason.WEEKLY_REST))

    // 2. Yesterday's 22/06 night + Today's 06/14, 14/22, 08/17 (all belong to today 17th = Tuesday)
    val todayCycleSlots = ShiftSchedulerEngine.buildDutyDaySlots(today)
    val yestNight = todayCycleSlots.first { it.shiftType == ShiftType.SHIFT_22_06 && it.dutyArea == DutyArea.STATION_UR }
    val todayMorning = todayCycleSlots.first { it.shiftType == ShiftType.SHIFT_06_14 && it.dutyArea == DutyArea.STATION_SC }
    val todayAfternoon = todayCycleSlots.first { it.shiftType == ShiftType.SHIFT_14_22 && it.dutyArea == DutyArea.STATION_SC }
    val todayGeneral = todayCycleSlots.first { it.shiftType == ShiftType.SHIFT_08_17 && it.roleType != SlotRoleType.SHUNTER }
    assertEquals(yesterday.toString(), yestNight.date)

    listOf(yestNight, todayMorning, todayAfternoon, todayGeneral).forEach { slot ->
      val evals = ShiftSchedulerEngine.evaluateCandidatesForSlot(
        slot = slot,
        staffList = testRoster,
        assignments = emptyList(),
        leaves = emptyList()
      )
      assertFalse("Rest-today staff must be filtered out for ${slot.shiftType}", evals.first { it.staff.id == restToday.id }.isFullyEligible)
      assertTrue(evals.first { it.staff.id == restToday.id }.conflicts.contains(ConflictReason.WEEKLY_REST))
      assertTrue("Rest-yesterday staff must be eligible for ${slot.shiftType}", evals.first { it.staff.id == restYesterday.id }.isFullyEligible)
      assertTrue("Rest-tomorrow staff must be eligible for ${slot.shiftType}", evals.first { it.staff.id == restTomorrow.id }.isFullyEligible)
    }
  }

  @Test
  fun arbitraryDates_applyDutyDateLeaveAndRestFilteringAcrossFullWeek() {
    val weekDates = (0L..6L).map { LocalDate.of(2027, 3, 14).plusDays(it) } // Sun..Sat across week boundary
    val baseStaff = staff.first { it.name == "P. RAM KUMAR" }.copy(stationDuty = true, shedDuty = true)
    val sundayRestStaff = baseStaff.copy(id = "s_sun", name = "SUN REST", weeklyRestDay = WeeklyRestDay.SUNDAY.name)
    val fridayLeaveStaff = baseStaff.copy(id = "s_fri", name = "FRI LEAVE", weeklyRestDay = WeeklyRestDay.NONE.name)
    val testRoster = listOf(sundayRestStaff, fridayLeaveStaff)

    val fridayDate = LocalDate.of(2027, 3, 19)
    val fridayLeave = listOf(
      LeaveRecord(
        id = "leave_fri",
        staffId = fridayLeaveStaff.id,
        staffName = fridayLeaveStaff.name,
        leaveType = LeaveType.CL.name,
        startDate = fridayDate.toString(),
        endDate = fridayDate.toString(),
        status = LeaveStatus.APPROVED.name
      )
    )

    weekDates.forEach { dutyDate ->
      val cycleSlots = ShiftSchedulerEngine.buildDutyDaySlots(dutyDate)
      val sampleSlots = listOf(
        cycleSlots.first { it.shiftType == ShiftType.SHIFT_22_06 && it.dutyArea == DutyArea.STATION_UR },
        cycleSlots.first { it.shiftType == ShiftType.SHIFT_06_14 && it.dutyArea == DutyArea.STATION_SC },
        cycleSlots.first { it.shiftType == ShiftType.SHIFT_14_22 && it.dutyArea == DutyArea.STATION_SC },
        cycleSlots.first { it.shiftType == ShiftType.SHIFT_08_17 && it.roleType != SlotRoleType.SHUNTER }
      )
      sampleSlots.forEach { slot ->
        val evals = ShiftSchedulerEngine.evaluateCandidatesForSlot(slot, testRoster, emptyList(), fridayLeave)
        val sunEval = evals.first { it.staff.id == sundayRestStaff.id }
        val friEval = evals.first { it.staff.id == fridayLeaveStaff.id }

        if (dutyDate.dayOfWeek == java.time.DayOfWeek.SUNDAY) {
          assertFalse("Sunday rest must block $dutyDate ${slot.shiftType}", sunEval.isFullyEligible)
          assertTrue(sunEval.conflicts.contains(ConflictReason.WEEKLY_REST))
        } else {
          assertTrue("Sunday rest must not block $dutyDate ${slot.shiftType}", sunEval.isFullyEligible)
        }

        if (dutyDate == fridayDate) {
          assertFalse("Friday leave must block $dutyDate ${slot.shiftType}", friEval.isFullyEligible)
          assertTrue(friEval.isOnLeave)
        } else {
          assertTrue("Friday leave must not block $dutyDate ${slot.shiftType}", friEval.isFullyEligible)
        }
      }
    }
  }
}
