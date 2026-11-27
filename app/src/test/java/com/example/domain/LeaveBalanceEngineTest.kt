package com.example.domain

import com.example.model.Designation
import com.example.model.DutyArea
import com.example.model.LeaveRecord
import com.example.model.LeaveStatus
import com.example.model.LeaveType
import com.example.model.ShiftSlotAssignment
import com.example.model.ShiftType
import com.example.model.SlotRoleType
import com.example.model.StaffLeaveAccount
import com.example.model.StaffMember
import com.example.model.WeeklyRestDay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class LeaveBalanceEngineTest {

  private val tech = StaffMember(
    id = "staff_7",
    serialNo = 7L,
    name = "P. RAM KUMAR",
    designation = Designation.SR_TECH.name,
    stationDuty = true,
    shedDuty = true,
    weeklyRestDay = WeeklyRestDay.SUNDAY.name
  )

  @Test
  fun clExpiresAtYearEnd_whileLapAndLhapCarryForwardAcrossYears() {
    val account2026 = StaffLeaveAccount(
      id = tech.id,
      staffId = tech.id,
      staffName = tech.name,
      baseYear = 2026,
      lapOpeningBalance = 120,
      lhapOpeningBalance = 40,
      lapSelfUpdated = true
    )

    val leaves = listOf(
      // 3 days CL in 2026 (7 unused CL must expire Dec 31, 2026)
      LeaveRecord(
        id = "l1",
        staffId = tech.id,
        staffName = tech.name,
        leaveType = LeaveType.CL.name,
        startDate = "2026-03-10",
        endDate = "2026-03-12",
        status = LeaveStatus.APPROVED.name
      ),
      // 5 days LAP in 2026 -> 115 unused LAP carries forward + 30 in 2027 = 145
      LeaveRecord(
        id = "l2",
        staffId = tech.id,
        staffName = tech.name,
        leaveType = LeaveType.LAP.name,
        startDate = "2026-06-01",
        endDate = "2026-06-05",
        status = LeaveStatus.APPROVED.name
      ),
      // 4 days LHAP in 2026 -> 36 unused LHAP carries forward + 20 in 2027 = 56
      LeaveRecord(
        id = "l3",
        staffId = tech.id,
        staffName = tech.name,
        leaveType = LeaveType.LHAP.name,
        startDate = "2026-08-10",
        endDate = "2026-08-13",
        status = LeaveStatus.APPROVED.name
      ),
      // Pending CL should not deduct from approved remaining balance
      LeaveRecord(
        id = "l4",
        staffId = tech.id,
        staffName = tech.name,
        leaveType = LeaveType.CL.name,
        startDate = "2026-12-01",
        endDate = "2026-12-02",
        status = LeaveStatus.PENDING.name
      )
    )

    val summary2026 = LeaveBalanceEngine.computeBalanceSummary(
      staff = tech,
      year = 2026,
      account = account2026,
      allLeaves = leaves,
      allAssignments = emptyList()
    )
    assertEquals(10, summary2026.clEntitlement)
    assertEquals(3, summary2026.clAvailed)
    assertEquals(2, summary2026.clPending)
    assertEquals(7, summary2026.clRemaining)
    assertEquals(LocalDate.of(2026, 12, 31), summary2026.clExpiresOn)

    assertEquals(120, summary2026.lapOpeningForYear)
    assertEquals(5, summary2026.lapAvailed)
    assertEquals(115, summary2026.lapRemaining)

    assertEquals(40, summary2026.lhapOpeningForYear)
    assertEquals(4, summary2026.lhapAvailed)
    assertEquals(36, summary2026.lhapRemaining)

    // Next year 2027: CL resets to fresh 10 (unused 7 from 2026 expired), LAP = 115 + 30 = 145, LHAP = 36 + 20 = 56
    val summary2027 = LeaveBalanceEngine.computeBalanceSummary(
      staff = tech,
      year = 2027,
      account = account2026,
      allLeaves = leaves,
      allAssignments = emptyList()
    )
    assertEquals(10, summary2027.clRemaining)
    assertEquals(0, summary2027.clAvailed)
    assertEquals(145, summary2027.lapOpeningForYear)
    assertEquals(145, summary2027.lapRemaining)
    assertEquals(56, summary2027.lhapOpeningForYear)
    assertEquals(56, summary2027.lhapRemaining)
  }

  @Test
  fun specialLeaveAwardedForRestDayDutyAndDoubleDuty_andDeductedWhenAvailed() {
    val assignments = listOf(
      // 2026-11-22 is SUNDAY -> tech's weekly rest day -> +1 Special Leave (REST_DAY_DUTY)
      ShiftSlotAssignment(
        id = "a1",
        date = "2026-11-22",
        shiftType = ShiftType.SHIFT_06_14.name,
        dutyArea = DutyArea.SHED.name,
        roleType = SlotRoleType.GENERAL_DUTY.name,
        staffId = tech.id,
        staffName = tech.name
      ),
      // 2026-11-24 is TUESDAY -> 2 duties on same day (06/14 + 14/22) -> +1 Special Leave (DOUBLE_DUTY)
      ShiftSlotAssignment(
        id = "a2",
        date = "2026-11-24",
        shiftType = ShiftType.SHIFT_06_14.name,
        dutyArea = DutyArea.STATION_SC.name,
        roleType = SlotRoleType.GENERAL_DUTY.name,
        staffId = tech.id,
        staffName = tech.name
      ),
      ShiftSlotAssignment(
        id = "a3",
        date = "2026-11-24",
        shiftType = ShiftType.SHIFT_14_22.name,
        dutyArea = DutyArea.SHED.name,
        roleType = SlotRoleType.GENERAL_DUTY.name,
        staffId = tech.id,
        staffName = tech.name
      )
    )

    val leaves = listOf(
      LeaveRecord(
        id = "sp1",
        staffId = tech.id,
        staffName = tech.name,
        leaveType = LeaveType.SPECIAL.name,
        startDate = "2026-12-05",
        endDate = "2026-12-05",
        status = LeaveStatus.APPROVED.name
      )
    )

    val summary = LeaveBalanceEngine.computeBalanceSummary(
      staff = tech,
      year = 2026,
      account = null,
      allLeaves = leaves,
      allAssignments = assignments
    )

    assertEquals(1, summary.specialRestDayCreditsThisYear)
    assertEquals(1, summary.specialDoubleDutyCreditsThisYear)
    assertEquals(2, summary.specialEarnedThisYear)
    assertEquals(2, summary.specialEarnedTotal)
    assertEquals(1, summary.specialAvailedThisYear)
    assertEquals(1, summary.specialRemaining)
    assertEquals(2, summary.specialCreditEventsInYear.size)
  }

  @Test
  fun legacyLeaveCodesMapCleanlyToLapAndLhap_andTomorrowDutiesSummaryDetectsNightAndDayShifts() {
    assertEquals(LeaveType.LAP, LeaveType.fromRaw("EL"))
    assertEquals(LeaveType.LHAP, LeaveType.fromRaw("SICK"))
    assertEquals(LeaveType.LAP, LeaveType.fromRaw("LAP"))
    assertEquals(LeaveType.LHAP, LeaveType.fromRaw("LHAP"))

    val today = LocalDate.of(2026, 11, 20) // Friday
    val tomorrow = today.plusDays(1) // Saturday
    val assignments = listOf(
      // Tonight's 22/06 shift counts toward Tomorrow's Duties cycle
      ShiftSlotAssignment(
        id = "night_slot",
        date = today.toString(),
        shiftType = ShiftType.SHIFT_22_06.name,
        dutyArea = DutyArea.STATION_UR.name,
        staffId = tech.id,
        staffName = tech.name,
        approvedLocked = true
      ),
      ShiftSlotAssignment(
        id = "morn_slot",
        date = tomorrow.toString(),
        shiftType = ShiftType.SHIFT_06_14.name,
        dutyArea = DutyArea.STATION_SC.name,
        staffId = tech.id,
        staffName = tech.name,
        approvedLocked = true
      )
    )

    val upcoming = LeaveBalanceEngine.buildUpcomingDutiesForStaff(
      staff = tech,
      baseToday = today,
      allAssignments = assignments,
      allLeaves = emptyList()
    )

    assertEquals(tomorrow, upcoming.tomorrowDutyDate)
    assertEquals(2, upcoming.tomorrowDuties.size)
    assertEquals(ShiftType.SHIFT_22_06, upcoming.tomorrowDuties[0].shiftType)
    assertEquals(ShiftType.SHIFT_06_14, upcoming.tomorrowDuties[1].shiftType)
    assertFalse(upcoming.isTomorrowWeeklyRest)
    assertTrue(upcoming.tomorrowApprovedLeave == null)
  }

  @Test
  fun trainingLeave_blocksDutyWithoutDeductingClLapLhapOrSpecialBalances() {
    assertEquals(LeaveType.TRAINING, LeaveType.fromRaw("TRAINING"))
    assertEquals(LeaveType.TRAINING, LeaveType.fromRaw("TRG"))

    val today = LocalDate.of(2026, 11, 20)
    val tomorrow = today.plusDays(1)
    val trainingLeave = LeaveRecord(
      id = "trg_1",
      staffId = tech.id,
      staffName = tech.name,
      leaveType = LeaveType.TRAINING.name,
      startDate = tomorrow.toString(),
      endDate = tomorrow.plusDays(3).toString(), // 4 days
      status = LeaveStatus.APPROVED.name,
      notes = "Technical Refresher Course"
    )

    assertTrue(trainingLeave.isBlockingDate(tomorrow.toString()))

    val summary = LeaveBalanceEngine.computeBalanceSummary(
      staff = tech,
      year = 2026,
      account = null,
      allLeaves = listOf(trainingLeave),
      allAssignments = emptyList()
    )
    assertEquals(4, summary.trainingAvailed)
    assertEquals(0, summary.clAvailed)
    assertEquals(10, summary.clRemaining)
    assertEquals(0, summary.lapAvailed)
    assertEquals(30, summary.lapRemaining)
    assertEquals(0, summary.lhapAvailed)
    assertEquals(20, summary.lhapRemaining)

    val upcoming = LeaveBalanceEngine.buildUpcomingDutiesForStaff(
      staff = tech,
      baseToday = today,
      allAssignments = emptyList(),
      allLeaves = listOf(trainingLeave)
    )
    assertEquals(LeaveType.TRAINING, upcoming.tomorrowApprovedLeave?.leaveTypeEnum)
  }
}
