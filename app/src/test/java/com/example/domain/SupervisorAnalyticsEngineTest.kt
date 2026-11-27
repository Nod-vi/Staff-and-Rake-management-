package com.example.domain

import com.example.data.DefaultPpioRoster
import com.example.model.DutyArea
import com.example.model.DutyPermissionRecord
import com.example.model.EmergencyOverrideLog
import com.example.model.LeaveRecord
import com.example.model.LeaveStatus
import com.example.model.LeaveType
import com.example.model.PermissionReliefType
import com.example.model.ShiftSlotAssignment
import com.example.model.ShiftType
import com.example.model.SlotRoleType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class SupervisorAnalyticsEngineTest {

  private val depotStaff = DefaultPpioRoster.initialStaff("ppio_main")

  @Test
  fun buildMonthlyLeaveCalendar_aggregatesApprovedPendingAndRestDays() {
    val dutyStaff = depotStaff.filter { !it.isSupervisory }
    val staffA = dutyStaff[0]
    val staffB = dutyStaff[1]

    val leaves = listOf(
      LeaveRecord(
        id = "l1",
        staffId = staffA.id,
        staffName = staffA.name,
        leaveType = LeaveType.CL.name,
        startDate = "2026-11-29",
        endDate = "2026-12-02", // crosses month boundary!
        status = LeaveStatus.APPROVED.name
      ),
      LeaveRecord(
        id = "l2",
        staffId = staffB.id,
        staffName = staffB.name,
        leaveType = LeaveType.LAP.name,
        startDate = "2026-11-30",
        endDate = "2026-11-30",
        status = LeaveStatus.PENDING.name
      ),
      LeaveRecord(
        id = "l3",
        staffId = staffB.id,
        staffName = staffB.name,
        leaveType = LeaveType.CL.name,
        startDate = "2026-11-15",
        endDate = "2026-11-15",
        status = LeaveStatus.REJECTED.name // rejected must NOT appear as approved/pending
      )
    )

    val novCalendar = SupervisorAnalyticsEngine.buildMonthlyLeaveCalendar(
      yearMonth = YearMonth.of(2026, 11),
      staffList = depotStaff,
      leaves = leaves
    )

    assertEquals(30, novCalendar.days.size)
    assertEquals(2, novCalendar.totalApprovedLeaveDays) // Nov 29 & Nov 30
    assertEquals(1, novCalendar.totalPendingLeaveDays) // Nov 30
    assertEquals(2, novCalendar.uniqueStaffOnLeave)

    val nov30 = novCalendar.days.first { it.date == LocalDate.of(2026, 11, 30) }
    assertEquals(1, nov30.approvedLeaves.size)
    assertEquals(staffA.id, nov30.approvedLeaves.first().staffId)
    assertEquals(1, nov30.pendingLeaves.size)
    assertEquals(staffB.id, nov30.pendingLeaves.first().staffId)

    val nov15 = novCalendar.days.first { it.date == LocalDate.of(2026, 11, 15) }
    assertTrue(nov15.approvedLeaves.isEmpty())
    assertTrue(nov15.pendingLeaves.isEmpty())
  }

  @Test
  fun findOverFiveStaffShiftDates_detectsOnlyShiftsWithMoreThanFiveStaff() {
    val dutyStaff = depotStaff.filter { !it.isSupervisory }

    // 2026-11-23 SHIFT_06_14 has 7 distinct staff -> MUST be flagged (>5)
    val shiftOverFive = listOf(
      ShiftSlotAssignment(id = "a1", date = "2026-11-23", shiftType = ShiftType.SHIFT_06_14.name, dutyArea = DutyArea.SHED.name, roleType = SlotRoleType.POINTSMAN.name, slotIndex = 0, staffId = dutyStaff[0].id, staffName = dutyStaff[0].name),
      ShiftSlotAssignment(id = "a2", date = "2026-11-23", shiftType = ShiftType.SHIFT_06_14.name, dutyArea = DutyArea.SHED.name, roleType = SlotRoleType.GENERAL_DUTY.name, slotIndex = 1, staffId = dutyStaff[1].id, staffName = dutyStaff[1].name),
      ShiftSlotAssignment(id = "a3", date = "2026-11-23", shiftType = ShiftType.SHIFT_06_14.name, dutyArea = DutyArea.SHED.name, roleType = SlotRoleType.GENERAL_DUTY.name, slotIndex = 2, staffId = dutyStaff[2].id, staffName = dutyStaff[2].name),
      ShiftSlotAssignment(id = "a4", date = "2026-11-23", shiftType = ShiftType.SHIFT_06_14.name, dutyArea = DutyArea.SHED.name, roleType = SlotRoleType.GENERAL_DUTY.name, slotIndex = 3, staffId = dutyStaff[3].id, staffName = dutyStaff[3].name),
      ShiftSlotAssignment(id = "a5", date = "2026-11-23", shiftType = ShiftType.SHIFT_06_14.name, dutyArea = DutyArea.SHED.name, roleType = SlotRoleType.GENERAL_DUTY.name, slotIndex = 4, staffId = dutyStaff[4].id, staffName = dutyStaff[4].name),
      ShiftSlotAssignment(id = "a6", date = "2026-11-23", shiftType = ShiftType.SHIFT_06_14.name, dutyArea = DutyArea.STATION_SC.name, roleType = SlotRoleType.GENERAL_DUTY.name, slotIndex = 0, staffId = dutyStaff[5].id, staffName = dutyStaff[5].name),
      ShiftSlotAssignment(id = "a7", date = "2026-11-23", shiftType = ShiftType.SHIFT_06_14.name, dutyArea = DutyArea.STATION_SC.name, roleType = SlotRoleType.GENERAL_DUTY.name, slotIndex = 1, staffId = dutyStaff[6].id, staffName = dutyStaff[6].name)
    )

    // 2026-11-23 SHIFT_14_22 has exactly 5 distinct staff -> must NOT be flagged
    val shiftExactlyFive = (0 until 5).map { idx ->
      ShiftSlotAssignment(
        id = "b$idx",
        date = "2026-11-23",
        shiftType = ShiftType.SHIFT_14_22.name,
        dutyArea = DutyArea.SHED.name,
        roleType = SlotRoleType.GENERAL_DUTY.name,
        slotIndex = idx.toLong(),
        staffId = dutyStaff[10 + idx].id,
        staffName = dutyStaff[10 + idx].name
      )
    }

    val alerts = SupervisorAnalyticsEngine.findOverFiveStaffShiftDates(shiftOverFive + shiftExactlyFive)
    assertEquals(1, alerts.size)
    val alert = alerts.first()
    assertEquals("2026-11-23", alert.date)
    assertEquals(ShiftType.SHIFT_06_14, alert.shiftType)
    assertEquals(7, alert.totalStaff)
    assertEquals(1, alert.pointsmanCount)
    assertEquals(4, alert.shedCount)
    assertEquals(2, alert.stationCount)
    assertFalse(alert.isShedOverFive)
  }

  @Test
  fun forecastTightStaffDates_flagsFutureDatesStressedByLeaves() {
    val dutyStaff = depotStaff.filter { !it.isSupervisory }
    val baseDate = LocalDate.of(2026, 11, 20)
    val stressedDate = baseDate.plusDays(5) // 2026-11-25

    // Put 8 staff on leave on 2026-11-25 (5 approved + 3 pending)
    val leaves = (0 until 8).map { i ->
      val m = dutyStaff[i]
      LeaveRecord(
        id = "lv_$i",
        staffId = m.id,
        staffName = m.name,
        leaveType = LeaveType.CL.name,
        startDate = stressedDate.toString(),
        endDate = stressedDate.toString(),
        status = if (i < 5) LeaveStatus.APPROVED.name else LeaveStatus.PENDING.name
      )
    }

    val forecasts = SupervisorAnalyticsEngine.forecastTightStaffDates(
      baseDate = baseDate,
      horizonDays = 30,
      staffList = depotStaff,
      leaves = leaves
    )

    val stressedForecast = forecasts.firstOrNull { it.date == stressedDate }
    assertTrue("Expected stressed future date $stressedDate to be flagged", stressedForecast != null)
    assertEquals(StaffTightnessLevel.CRITICAL, stressedForecast!!.severity)
    assertEquals(5, stressedForecast.approvedLeaves.size)
    assertEquals(3, stressedForecast.pendingLeaves.size)
  }

  @Test
  fun computeStaffPerformanceMetrics_tracksDutiesSpecialCreditsLeavesAndReliefHours() {
    val baseStaff = depotStaff.first { !it.isSupervisory }
    val targetStaff = baseStaff.copy(weeklyRestDay = com.example.model.WeeklyRestDay.WEDNESDAY.name)
    val staffWithRest = depotStaff.map { if (it.id == targetStaff.id) targetStaff else it }
    val restDate = LocalDate.of(2026, 11, 4) // Wednesday
    val normalDate = LocalDate.of(2026, 11, 5) // Thursday

    val assignments = listOf(
      // Rest-day duty -> awards +1 Special Leave credit
      ShiftSlotAssignment(
        id = "p1",
        date = restDate.toString(),
        shiftType = ShiftType.SHIFT_06_14.name,
        dutyArea = DutyArea.SHED.name,
        staffId = targetStaff.id,
        staffName = targetStaff.name
      ),
      // Double duty on normalDate -> awards +1 Special Leave credit
      ShiftSlotAssignment(
        id = "p2",
        date = normalDate.toString(),
        shiftType = ShiftType.SHIFT_06_14.name,
        dutyArea = DutyArea.SHED.name,
        staffId = targetStaff.id,
        staffName = targetStaff.name
      ),
      ShiftSlotAssignment(
        id = "p3",
        date = normalDate.toString(),
        shiftType = ShiftType.SHIFT_14_22.name,
        dutyArea = DutyArea.SHED.name,
        staffId = targetStaff.id,
        staffName = targetStaff.name
      )
    )

    val leaves = listOf(
      LeaveRecord(
        id = "pl1",
        staffId = targetStaff.id,
        staffName = targetStaff.name,
        leaveType = LeaveType.CL.name,
        startDate = "2026-11-18",
        endDate = "2026-11-19",
        status = LeaveStatus.APPROVED.name
      )
    )

    val permissions = listOf(
      DutyPermissionRecord(
        id = "perm1",
        staffId = targetStaff.id,
        staffName = targetStaff.name,
        date = normalDate.toString(),
        shiftType = ShiftType.SHIFT_06_14.name,
        hours = 2.5,
        reliefType = PermissionReliefType.EARLY_RELIEF.name,
        reason = "Clinic visit",
        grantedByUid = "sup_1",
        grantedByName = "VENKATA KRISHNA"
      ),
      DutyPermissionRecord(
        id = "perm2",
        staffId = targetStaff.id,
        staffName = targetStaff.name,
        date = "2026-11-20",
        shiftType = ShiftType.SHIFT_08_17.name,
        hours = 1.5,
        reliefType = PermissionReliefType.LATE_REPORTING.name,
        reason = "Personal",
        grantedByUid = "sup_1",
        grantedByName = "VENKATA KRISHNA"
      )
    )

    val overrides = listOf(
      EmergencyOverrideLog(
        id = "ov1",
        date = normalDate.toString(),
        shiftType = ShiftType.SHIFT_14_22.name,
        dutyArea = DutyArea.SHED.name,
        staffId = targetStaff.id,
        staffName = targetStaff.name,
        conflictSummary = "Double shift",
        reason = "Urgent train rake"
      )
    )

    val rows = SupervisorAnalyticsEngine.computeStaffPerformanceMetrics(
      staffList = staffWithRest,
      assignments = assignments,
      leaves = leaves,
      overrides = overrides,
      permissions = permissions,
      startDate = LocalDate.of(2026, 11, 1),
      endDate = LocalDate.of(2026, 11, 30)
    )

    val targetRow = rows.first { it.staff.id == targetStaff.id }
    assertEquals(3, targetRow.totalDuties)
    assertEquals(2, targetRow.morningDuties)
    assertEquals(1, targetRow.afternoonDuties)
    assertEquals(1, targetRow.restDayDuties)
    assertEquals(1, targetRow.doubleDutyDays)
    assertEquals(2, targetRow.specialLeaveCreditsEarned)
    assertEquals(2, targetRow.approvedLeaveDays)
    assertEquals(2, targetRow.approvedClDays)
    assertEquals(2, targetRow.permissionCount)
    assertEquals(4.0, targetRow.totalPermissionHours, 0.001)
    assertEquals(1, targetRow.emergencyOverridesCount)
  }

  @Test
  fun computeTrainingDueTechnicians_ordersByUrgencyAndDetectsScheduledTrainingLeave() {
    val dutyStaff = depotStaff.filter { !it.isSupervisory }
    val overdueTech = dutyStaff[0].copy(trainingDueDate = "2026-11-15", trainingCourse = "Safety / PME")
    val dueSoonTech = dutyStaff[1].copy(trainingDueDate = "2026-12-05", trainingCourse = "Technical Refresher")
    val upcomingTech = dutyStaff[2].copy(trainingDueDate = "2027-02-10", trainingCourse = "Traction / Rolling Stock")
    val noDueTech = dutyStaff[3].copy(trainingDueDate = "")
    val supervisoryWithDue = depotStaff.first { it.isSupervisory }.copy(trainingDueDate = "2026-11-10")

    val baseDate = LocalDate.of(2026, 11, 20)
    val leaves = listOf(
      LeaveRecord(
        id = "trg_1",
        staffId = dueSoonTech.id,
        staffName = dueSoonTech.name,
        leaveType = LeaveType.TRAINING.name,
        startDate = "2026-11-25",
        endDate = "2026-11-28",
        status = LeaveStatus.APPROVED.name
      )
    )

    val items = SupervisorAnalyticsEngine.computeTrainingDueTechnicians(
      baseDate = baseDate,
      staffList = listOf(upcomingTech, noDueTech, overdueTech, dueSoonTech, supervisoryWithDue),
      leaves = leaves
    )

    assertEquals(3, items.size)
    assertEquals(overdueTech.id, items[0].staff.id)
    assertEquals(TrainingDueStatus.OVERDUE, items[0].status)
    assertEquals(-5L, items[0].daysUntilDue)
    assertEquals(null, items[0].activeTrainingLeave)

    assertEquals(dueSoonTech.id, items[1].staff.id)
    assertEquals(TrainingDueStatus.DUE_SOON, items[1].status)
    assertEquals(15L, items[1].daysUntilDue)
    assertEquals("trg_1", items[1].activeTrainingLeave?.id)

    assertEquals(upcomingTech.id, items[2].staff.id)
    assertEquals(TrainingDueStatus.UPCOMING, items[2].status)
    assertTrue(items[2].daysUntilDue > 30L)
  }

  @Test
  fun computeStaffPerformanceMetrics_tracksTrainingDaysSeparatelyFromPersonalAbsence() {
    val targetStaff = depotStaff.first { !it.isSupervisory }
    val trainingLeave = LeaveRecord(
      id = "trg_perf",
      staffId = targetStaff.id,
      staffName = targetStaff.name,
      leaveType = LeaveType.TRAINING.name,
      startDate = "2026-11-10",
      endDate = "2026-11-19", // 10 training days must NOT trigger "High Absence / Relief"
      status = LeaveStatus.APPROVED.name
    )

    val rows = SupervisorAnalyticsEngine.computeStaffPerformanceMetrics(
      staffList = listOf(targetStaff),
      assignments = listOf(
        ShiftSlotAssignment(
          id = "a_trg",
          date = "2026-11-05",
          shiftType = ShiftType.SHIFT_06_14.name,
          dutyArea = DutyArea.SHED.name,
          staffId = targetStaff.id,
          staffName = targetStaff.name
        )
      ),
      leaves = listOf(trainingLeave),
      overrides = emptyList(),
      permissions = emptyList(),
      startDate = LocalDate.of(2026, 11, 1),
      endDate = LocalDate.of(2026, 11, 30)
    )

    val row = rows.first()
    assertEquals(10, row.approvedTrainingDays)
    assertEquals(0, row.approvedLeaveDays)
    assertEquals("Steady", row.performanceTag)
  }

  @Test
  fun resolveLiveShiftSnapshot_mapsCurrentTimeToActiveShiftIncludingOvernight() {
    val dutyStaff = depotStaff.filter { !it.isSupervisory }
    val stationStaff = dutyStaff.first { it.stationDuty }.copy(
      phoneNumber = "9848012345",
      secondaryPhoneNumber = "9123456789"
    )
    val pointsmanStaff = dutyStaff.first { it.isPointsman }.copy(
      phoneNumber = "9000011111"
    )
    val shedStaff = dutyStaff.first { it.shedDuty && it.id != stationStaff.id && it.id != pointsmanStaff.id }
    val nightStaff = dutyStaff.first { it.id !in setOf(stationStaff.id, pointsmanStaff.id, shedStaff.id) }
    val generalStaff = dutyStaff.first { it.id !in setOf(stationStaff.id, pointsmanStaff.id, shedStaff.id, nightStaff.id) }

    val updatedStaff = depotStaff.map {
      when (it.id) {
        stationStaff.id -> stationStaff
        pointsmanStaff.id -> pointsmanStaff
        else -> it
      }
    }

    val assignments = listOf(
      // Today (2026-11-21) 06/14 shift
      ShiftSlotAssignment(
        id = "live_m_pm",
        date = "2026-11-21",
        shiftType = ShiftType.SHIFT_06_14.name,
        dutyArea = DutyArea.SHED.name,
        roleType = SlotRoleType.POINTSMAN.name,
        staffId = pointsmanStaff.id,
        staffName = pointsmanStaff.name
      ),
      ShiftSlotAssignment(
        id = "live_m_shed",
        date = "2026-11-21",
        shiftType = ShiftType.SHIFT_06_14.name,
        dutyArea = DutyArea.SHED.name,
        roleType = SlotRoleType.GENERAL_DUTY.name,
        staffId = shedStaff.id,
        staffName = shedStaff.name
      ),
      ShiftSlotAssignment(
        id = "live_m_stn",
        date = "2026-11-21",
        shiftType = ShiftType.SHIFT_06_14.name,
        dutyArea = DutyArea.STATION_SC.name,
        roleType = SlotRoleType.GENERAL_DUTY.name,
        staffId = stationStaff.id,
        staffName = stationStaff.name
      ),
      // Today 08/17 general shift
      ShiftSlotAssignment(
        id = "live_gen",
        date = "2026-11-21",
        shiftType = ShiftType.SHIFT_08_17.name,
        dutyArea = DutyArea.SHED.name,
        roleType = SlotRoleType.GENERAL_DUTY.name,
        staffId = generalStaff.id,
        staffName = generalStaff.name
      ),
      // Yesterday (2026-11-20) 22/06 night shift -> active at 00:32 AM on 2026-11-21
      ShiftSlotAssignment(
        id = "live_prev_night_shed",
        date = "2026-11-20",
        shiftType = ShiftType.SHIFT_22_06.name,
        dutyArea = DutyArea.SHED.name,
        roleType = SlotRoleType.GENERAL_DUTY.name,
        staffId = nightStaff.id,
        staffName = nightStaff.name
      ),
      ShiftSlotAssignment(
        id = "live_prev_night_stn",
        date = "2026-11-20",
        shiftType = ShiftType.SHIFT_22_06.name,
        dutyArea = DutyArea.STATION_UR.name,
        roleType = SlotRoleType.GENERAL_DUTY.name,
        staffId = stationStaff.id,
        staffName = stationStaff.name
      ),
      // Today (2026-11-21) 22/06 night shift -> active at 22:30 on 2026-11-21
      ShiftSlotAssignment(
        id = "live_curr_night",
        date = "2026-11-21",
        shiftType = ShiftType.SHIFT_22_06.name,
        dutyArea = DutyArea.SHED.name,
        roleType = SlotRoleType.GENERAL_DUTY.name,
        staffId = shedStaff.id,
        staffName = shedStaff.name
      )
    )

    // 1) 11:34 AM -> 06/14 shift on today (2026-11-21), plus 08/17 general
    val morningSnap = SupervisorAnalyticsEngine.resolveLiveShiftSnapshot(
      now = java.time.LocalDateTime.of(2026, 11, 21, 11, 34),
      staffList = updatedStaff,
      assignments = assignments,
      leaves = emptyList()
    )
    assertEquals(ShiftType.SHIFT_06_14, morningSnap.activeShift)
    assertEquals(LocalDate.of(2026, 11, 21), morningSnap.activeShiftAssignmentDate)
    assertEquals(1, morningSnap.pointsmanStaff.size)
    assertEquals(1, morningSnap.shedStaff.size)
    assertEquals(1, morningSnap.activeStationStaff.size)
    assertEquals("9848012345", morningSnap.activeStationStaff.first().staff?.phoneNumber)
    assertEquals("9123456789", morningSnap.activeStationStaff.first().staff?.secondaryPhoneNumber)
    assertEquals(2, morningSnap.activeStationStaff.first().staff?.availablePhones?.size)
    assertEquals("9000011111", morningSnap.pointsmanStaff.first().preferredPhoneNumber)
    assertEquals(1, morningSnap.generalShiftStaff.size)
    assertEquals(
      setOf(pointsmanStaff.id, shedStaff.id, stationStaff.id, generalStaff.id),
      morningSnap.currentShiftStaffMembers.map { it.id }.toSet()
    )
    assertFalse(morningSnap.currentShiftStaffMembers.any { it.id == nightStaff.id })

    // 2) 00:32 AM -> overnight 22/06 shift stored on yesterday (2026-11-20)
    val postMidnightSnap = SupervisorAnalyticsEngine.resolveLiveShiftSnapshot(
      now = java.time.LocalDateTime.of(2026, 11, 21, 0, 32),
      staffList = updatedStaff,
      assignments = assignments,
      leaves = emptyList()
    )
    assertEquals(ShiftType.SHIFT_22_06, postMidnightSnap.activeShift)
    assertEquals(LocalDate.of(2026, 11, 20), postMidnightSnap.activeShiftAssignmentDate)
    assertEquals(1, postMidnightSnap.shedStaff.size)
    assertEquals(nightStaff.id, postMidnightSnap.shedStaff.first().assignment.staffId)
    assertEquals(1, postMidnightSnap.activeStationStaff.size)
    assertEquals(0, postMidnightSnap.generalShiftStaff.size)
    assertEquals(
      setOf(nightStaff.id, stationStaff.id),
      postMidnightSnap.currentShiftStaffMembers.map { it.id }.toSet()
    )

    // 3) 22:45 -> today's 22/06 shift (2026-11-21)
    val lateNightSnap = SupervisorAnalyticsEngine.resolveLiveShiftSnapshot(
      now = java.time.LocalDateTime.of(2026, 11, 21, 22, 45),
      staffList = updatedStaff,
      assignments = assignments,
      leaves = emptyList()
    )
    assertEquals(ShiftType.SHIFT_22_06, lateNightSnap.activeShift)
    assertEquals(LocalDate.of(2026, 11, 21), lateNightSnap.activeShiftAssignmentDate)
    assertEquals(1, lateNightSnap.shedStaff.size)
    assertEquals(shedStaff.id, lateNightSnap.shedStaff.first().assignment.staffId)
  }

  @Test
  fun shiftInchargeRole_hasOnlyPermissionManagerAccess_andImportantContactsAreComplete() {
    val role = com.example.model.UserRole.SHIFT_INCHARGE
    assertTrue(role.canManagePermissions)
    assertFalse(role.canDirectEditDuties)
    assertFalse(role.canDraftDuties)
    assertFalse(role.canApproveDuties)
    assertFalse(role.canApproveLeaves)
    assertFalse(role.canManageLeaves)
    assertFalse(role.canApplyLeaveOnly)
    assertFalse(role.canManageStaff)
    assertFalse(role.canManageUserRoles)

    val contacts = com.example.model.IMPORTANT_DEPOT_CONTACTS
    assertEquals(9, contacts.size)
    assertEquals("7815913694", contacts.first { it.label == "RRI KZJ" }.phoneNumber)
    assertEquals("9281035602", contacts.first { it.label == "KCG PANEL" }.phoneNumber)
    assertEquals("8790992825", contacts.first { it.label == "TLC SC" }.phoneNumber)
    assertEquals("9281035656", contacts.first { it.label == "SS MED" }.phoneNumber)
    assertEquals("7815901066", contacts.first { it.label == "SS HYB CABIN" }.phoneNumber)
    assertEquals("7815905925", contacts.first { it.label == "SS GT" }.phoneNumber)
    assertEquals("7670905120", contacts.first { it.label == "PCOR HYB" }.phoneNumber)
    assertEquals("8790993339", contacts.first { it.label == "PCOR SC" }.phoneNumber)
    assertEquals("9701372320", contacts.first { it.label == "SSE ELE MAINT MLY" }.phoneNumber)
  }
}

