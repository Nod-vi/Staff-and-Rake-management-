package com.example.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.domain.ShiftSchedulerEngine
import com.example.domain.SupervisorAnalyticsEngine
import com.example.model.DepotUserProfile
import com.example.model.DutyApprovalRequest
import com.example.model.DutyDateLock
import com.example.model.DutyDraftStatus
import com.example.model.DutyPermissionRecord
import com.example.model.EmergencyOverrideLog
import com.example.model.IMPORTANT_DEPOT_CONTACTS
import com.example.model.ImportantContact
import com.example.model.LapUpdateStatus
import com.example.model.LeaveRecord
import com.example.model.LeaveStatus
import com.example.model.ShiftSlotAssignment
import com.example.model.ShiftType
import com.example.model.StaffLeaveAccount
import com.example.model.StaffMember
import com.example.model.UserRole
import com.example.notifications.ScheduledDutyReminderInfo
import com.example.notifications.TechnicianDutyNotifier
import com.example.ui.theme.DepotTeal
import com.example.ui.theme.GlassIconOrb
import com.example.ui.theme.GlassPillBadge
import com.example.ui.theme.GlassSurfaceBright
import com.example.ui.theme.GlassSurfaceMid
import com.example.ui.theme.GlassSurfaceSoft
import com.example.ui.theme.GlassTile
import com.example.ui.theme.RailwayCobalt
import com.example.ui.theme.SafetyAmber
import com.example.ui.theme.SafetyAmberContainer
import com.example.ui.theme.SafetyAmberOnContainer
import com.example.ui.theme.SignalCrimson
import com.example.ui.theme.SignalCrimsonOnContainer
import com.example.ui.theme.SignalEmerald
import com.example.ui.theme.SignalEmeraldContainer
import com.example.ui.theme.SignalEmeraldOnContainer
import com.example.ui.theme.liquidGlassAccent
import com.example.ui.theme.liquidGlassSurface
import kotlinx.coroutines.delay
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.temporal.TemporalAdjusters

private enum class SupervisorDashboardMode(val label: String, val tag: String) {
  OVERVIEW("Overview", "dashboard_mode_analytics"),
  PERFORMANCE("Performance", "dashboard_mode_performance"),
  WEEKLY_GRID("Weekly Grid", "dashboard_mode_weekly_calendar")
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DashboardScreen(
  selectedDate: LocalDate,
  staffList: List<StaffMember>,
  assignments: List<ShiftSlotAssignment>,
  leaves: List<LeaveRecord>,
  overrides: List<EmergencyOverrideLog>,
  dutyDrafts: List<DutyApprovalRequest> = emptyList(),
  dutyLocks: List<DutyDateLock> = emptyList(),
  leaveAccounts: List<StaffLeaveAccount> = emptyList(),
  dutyPermissions: List<DutyPermissionRecord> = emptyList(),
  importantContacts: List<ImportantContact> = IMPORTANT_DEPOT_CONTACTS,
  allUserProfiles: List<DepotUserProfile> = emptyList(),
  linkedStaffId: String = "",
  userRole: UserRole = UserRole.SUPERVISOR,
  roleAccess: com.example.model.ResolvedRoleAccess = com.example.model.RoleAccessPolicy.resolve(userRole, emptyMap()),
  isBusy: Boolean = false,
  onSelectDate: (LocalDate) -> Unit = {},
  onAutoAssignWeek: (() -> Unit)? = null,
  onUpdateLapBalance: (StaffMember, Int, Int, String, Int) -> Unit = { _, _, _, _, _ -> },
  onUpdateStaffTrainingDue: (String, String, String, String) -> Unit = { _, _, _, _ -> },
  onRelieveForTraining: (StaffMember) -> Unit = {},
  onAddImportantContact: (String, String, String) -> Unit = { _, _, _ -> },
  onDeleteImportantContact: (ImportantContact) -> Unit = {},
  onOpenShiftsTab: () -> Unit = {},
  onOpenDutiesTab: () -> Unit = {},
  onOpenLeaveTab: () -> Unit = {},
  onOpenLeaveCalendar: () -> Unit = {},
  onOpenPermissionsTab: () -> Unit = {},
  onOpenStaffTab: () -> Unit = {},
  onOpenUsersTab: () -> Unit = {},
  onOpenRolesTab: () -> Unit = {},
  onOpenAppUpdatesManager: () -> Unit = {},
  publishedAppVersionLabel: String = "",
  rakesCount: Int = 0,
  onOpenRakesTab: () -> Unit = {},
  dutyNotificationsEnabled: Boolean = true,
  dutyReminderLeadMinutes: Int = TechnicianDutyNotifier.DEFAULT_LEAD_MINUTES,
  nextScheduledReminder: ScheduledDutyReminderInfo? = null,
  onToggleDutyNotifications: (Boolean) -> Unit = {},
  onSelectDutyReminderLeadMinutes: (Int) -> Unit = {},
  onNotificationPermissionChanged: () -> Unit = {},
  onSendTestDutyNotification: () -> Unit = {}
) {
  if (userRole == UserRole.SHIFT_INCHARGE) {
    ShiftInchargeDashboardContent(
      staffList = staffList,
      assignments = assignments,
      leaves = leaves,
      dutyPermissions = dutyPermissions,
      importantContacts = importantContacts,
      onOpenPermissionsTab = onOpenPermissionsTab
    )
    return
  }

  if (userRole == UserRole.TECHNICIAN) {
    TechnicianDashboardContent(
      staffList = staffList,
      assignments = assignments,
      leaves = leaves,
      leaveAccounts = leaveAccounts,
      linkedStaffId = linkedStaffId,
      isBusy = isBusy,
      onUpdateLapBalance = onUpdateLapBalance,
      onOpenDutiesTab = onOpenDutiesTab,
      onOpenLeaveTab = onOpenLeaveTab,
      onApplyTrainingLeave = {
        val me = staffList.firstOrNull { it.id == linkedStaffId.trim() }
        if (me != null) onRelieveForTraining(me) else onOpenLeaveTab()
      },
      dutyNotificationsEnabled = dutyNotificationsEnabled,
      dutyReminderLeadMinutes = dutyReminderLeadMinutes,
      nextScheduledReminder = nextScheduledReminder,
      onToggleDutyNotifications = onToggleDutyNotifications,
      onSelectDutyReminderLeadMinutes = onSelectDutyReminderLeadMinutes,
      onNotificationPermissionChanged = onNotificationPermissionChanged,
      onSendTestDutyNotification = onSendTestDutyNotification
    )
    return
  }

  val phoneActions = rememberPhoneActionController()
  val isSupervisorOrAdmin = roleAccess.hasFullDashboard || roleAccess.canApproveDuties
  val availableModes = remember(isSupervisorOrAdmin) {
    if (isSupervisorOrAdmin) {
      SupervisorDashboardMode.entries.toList()
    } else {
      listOf(SupervisorDashboardMode.OVERVIEW, SupervisorDashboardMode.WEEKLY_GRID)
    }
  }
  var activeMode by rememberSaveable { mutableStateOf(SupervisorDashboardMode.OVERVIEW) }
  val effectiveMode = if (activeMode in availableModes) activeMode else SupervisorDashboardMode.OVERVIEW

  val dateStr = selectedDate.toString()
  val prevDateStr = remember(selectedDate) { selectedDate.minusDays(1).toString() }
  val coverage = remember(selectedDate, assignments) {
    ShiftSchedulerEngine.computeDutyDayCoverage(selectedDate, assignments)
  }

  val activeDutyStaff = remember(staffList) { staffList.filter { !it.isSupervisory } }
  val onLeaveCount = remember(activeDutyStaff, leaves, dateStr) {
    activeDutyStaff.count { m -> leaves.any { it.staffId == m.id && it.isBlockingDate(dateStr) } }
  }
  val onRestCount = remember(activeDutyStaff, selectedDate) {
    activeDutyStaff.count { it.restDayEnum.dayOfWeek == selectedDate.dayOfWeek }
  }
  val assignedTodayIds = remember(assignments, selectedDate, dateStr, prevDateStr) {
    assignments.filter {
      val sType = ShiftType.fromRaw(it.shiftType)
      it.date == sType.startDateForDutyDate(selectedDate).toString()
    }.map { it.staffId }.toSet()
  }
  val pointsmenCount = remember(activeDutyStaff) { activeDutyStaff.count { it.isPointsman } }
  val pointsmenAssignedToday = remember(activeDutyStaff, assignedTodayIds) {
    activeDutyStaff.count { it.isPointsman && assignedTodayIds.contains(it.id) }
  }

  val overFiveShiftAlerts = remember(assignments) {
    SupervisorAnalyticsEngine.findOverFiveStaffShiftDates(assignments)
  }
  val today = remember { LocalDate.now() }
  var nowDateTime by remember { mutableStateOf(LocalDateTime.now()) }
  LaunchedEffect(isSupervisorOrAdmin) {
    if (isSupervisorOrAdmin) {
      while (true) {
        delay(30_000L)
        nowDateTime = LocalDateTime.now()
      }
    }
  }
  val liveShiftSnapshot = remember(nowDateTime, staffList, assignments, leaves) {
    SupervisorAnalyticsEngine.resolveLiveShiftSnapshot(
      now = nowDateTime,
      staffList = staffList,
      assignments = assignments,
      leaves = leaves
    )
  }
  val unlinkedUsersCount = remember(allUserProfiles) {
    allUserProfiles.count { it.roleEnum == UserRole.TECHNICIAN && it.staffId.isBlank() }
  }
  val tightStaffForecasts = remember(today, staffList, leaves) {
    SupervisorAnalyticsEngine.forecastTightStaffDates(
      baseDate = today,
      horizonDays = 30,
      staffList = staffList,
      leaves = leaves
    )
  }
  val trainingDueItems = remember(today, staffList, leaves) {
    SupervisorAnalyticsEngine.computeTrainingDueTechnicians(
      baseDate = today,
      staffList = staffList,
      leaves = leaves
    )
  }
  var showAssignTrainingDueDialog by remember { mutableStateOf(false) }
  var editingTrainingDueStaff by remember { mutableStateOf<StaffMember?>(null) }
  val todayPermissions = remember(dutyPermissions, dateStr, isSupervisorOrAdmin) {
    if (!isSupervisorOrAdmin) emptyList()
    else dutyPermissions.filter { it.date == dateStr }
  }
  val currentMonthPrefix = remember(selectedDate) { YearMonth.from(selectedDate).toString() }
  val monthPermissionHours = remember(dutyPermissions, currentMonthPrefix, isSupervisorOrAdmin) {
    if (!isSupervisorOrAdmin) 0.0
    else dutyPermissions.filter { it.date.startsWith(currentMonthPrefix) }.sumOf { it.hours }
  }

  val pendingLeavesCount = remember(leaves) { leaves.count { it.statusEnum == LeaveStatus.PENDING } }
  val pendingDraftsCount = remember(dutyDrafts) { dutyDrafts.count { it.statusEnum == DutyDraftStatus.PENDING } }
  val pendingLapUpdatesCount = remember(leaveAccounts) {
    leaveAccounts.count { it.pendingLapStatusEnum == LapUpdateStatus.PENDING }
  }
  val pendingApprovalsTotal = pendingLeavesCount + pendingDraftsCount + pendingLapUpdatesCount

  val weekStart = remember(selectedDate) {
    selectedDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
  }
  val weekEnd = remember(weekStart) { weekStart.plusDays(6) }
  val weeklyCounts = remember(assignments, weekStart, weekEnd, activeDutyStaff) {
    val startStr = weekStart.toString()
    val endStr = weekEnd.toString()
    val map = assignments
      .filter { it.date in startStr..endStr }
      .groupingBy { it.staffId }
      .eachCount()
    activeDutyStaff.map { it.name.substringBefore(" ") + " " + it.name.substringAfter(" ", "").take(1) to (map[it.id] ?: 0) }
      .sortedByDescending { it.second }
      .take(12)
  }
  val compliantShifts = coverage.count { it.isFullyCompliant }

  Column(
    modifier = Modifier.fillMaxSize(),
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    // Segmented Mode Switcher
    GlassTile(
      modifier = Modifier
        .fillMaxWidth()
        .widthIn(max = 780.dp)
        .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp),
      shape = RoundedCornerShape(18.dp),
      topColor = GlassSurfaceBright,
      bottomColor = GlassSurfaceMid
    ) {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
      ) {
        availableModes.forEach { mode ->
          val selected = effectiveMode == mode
          Box(
            modifier = (if (selected) {
              Modifier.liquidGlassAccent(shape = RoundedCornerShape(14.dp))
            } else {
              Modifier.liquidGlassSurface(
                shape = RoundedCornerShape(14.dp),
                topColor = GlassSurfaceBright,
                bottomColor = GlassSurfaceSoft,
                borderWidth = 1.dp
              )
            })
              .weight(1f)
              .heightIn(min = 48.dp)
              .clickable { activeMode = mode }
              .padding(horizontal = 8.dp, vertical = 10.dp)
              .testTag(mode.tag),
            contentAlignment = Alignment.Center
          ) {
            Text(
              text = mode.label,
              style = MaterialTheme.typography.labelLarge,
              fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
              color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface,
              textAlign = TextAlign.Center,
              maxLines = 2
            )
          }
        }
      }
    }

    when (effectiveMode) {
      SupervisorDashboardMode.WEEKLY_GRID -> {
        WeeklyRosterCalendarGrid(
          selectedDate = selectedDate,
          staffList = staffList,
          assignments = assignments,
          leaves = leaves,
          dutyLocks = dutyLocks,
          userRole = userRole,
          roleAccess = roleAccess,
          isBusy = isBusy,
          onSelectDate = onSelectDate,
          onAutoAssignWeek = onAutoAssignWeek,
          modifier = Modifier.weight(1f)
        )
      }

      SupervisorDashboardMode.PERFORMANCE -> {
        SupervisorPerformanceTab(
          selectedDate = selectedDate,
          staffList = staffList,
          assignments = assignments,
          leaves = leaves,
          overrides = overrides,
          permissions = if (isSupervisorOrAdmin) dutyPermissions else emptyList(),
          modifier = Modifier.weight(1f)
        )
      }

      SupervisorDashboardMode.OVERVIEW -> {
        Box(
          modifier = Modifier
            .weight(1f)
            .fillMaxWidth(),
          contentAlignment = Alignment.TopCenter
        ) {
          LazyColumn(
            modifier = Modifier
              .fillMaxSize()
              .widthIn(max = 780.dp),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
          ) {
            item(key = "dash_header", contentType = "header") {
              if (isSupervisorOrAdmin) {
                SupervisorAdminHeroHeaderCard(
                  userRole = userRole,
                  selectedDate = selectedDate,
                  today = today,
                  nowDateTime = nowDateTime,
                  liveSnapshot = liveShiftSnapshot,
                  onSelectDate = onSelectDate
                )
              } else {
                GlassTile(
                  shape = RoundedCornerShape(22.dp),
                  topColor = GlassSurfaceBright,
                  bottomColor = GlassSurfaceMid
                ) {
                  Row(
                    modifier = Modifier
                      .fillMaxWidth()
                      .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                  ) {
                    GlassIconOrb(
                      shape = RoundedCornerShape(14.dp),
                      tintColor = RailwayCobalt.copy(alpha = 0.14f),
                      modifier = Modifier.size(44.dp)
                    ) {
                      Icon(Icons.Default.Assessment, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    }
                    Column {
                      Text(
                        text = "Depot Roster Overview",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                      )
                      Text(
                        text = "Duty cycle $prevDateStr (22/06) → $dateStr (${selectedDate.dayOfWeek})",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                      )
                    }
                  }
                }
              }
            }

            if (isSupervisorOrAdmin) {
              item(key = "dash_pending_approvals", contentType = "pending_approvals") {
                PendingApprovalsBannerCard(
                  pendingLeavesCount = pendingLeavesCount,
                  pendingDraftsCount = pendingDraftsCount,
                  pendingLapUpdatesCount = pendingLapUpdatesCount,
                  onOpenLeaveTab = onOpenLeaveTab,
                  onOpenShiftsTab = onOpenShiftsTab
                )
              }

              item(key = "dash_quick_actions", contentType = "quick_actions") {
                SupervisorQuickActionsGrid(
                  userRole = userRole,
                  roleAccess = roleAccess,
                  compliantShifts = compliantShifts,
                  totalShifts = coverage.size,
                  pendingApprovalsTotal = pendingApprovalsTotal,
                  todayPermissionsCount = todayPermissions.size,
                  activeStaffCount = activeDutyStaff.size,
                  totalUsersCount = allUserProfiles.size,
                  unlinkedUsersCount = unlinkedUsersCount,
                  isBusy = isBusy,
                  onOpenShiftsTab = onOpenShiftsTab,
                  onOpenDutiesTab = onOpenDutiesTab,
                  onOpenLeaveTab = onOpenLeaveTab,
                  onOpenPermissionsTab = onOpenPermissionsTab,
                  onOpenStaffTab = onOpenStaffTab,
                  onOpenUsersTab = onOpenUsersTab,
                  onOpenRolesTab = onOpenRolesTab,
                  rakesCount = rakesCount,
                  onOpenRakesTab = onOpenRakesTab,
                  onAutoAssignWeek = onAutoAssignWeek
                )
              }

              item(key = "dash_live_shift_snapshot", contentType = "live_shift_snapshot") {
                LiveActiveShiftSnapshotCard(
                  liveSnapshot = liveShiftSnapshot,
                  onOpenShiftsTab = onOpenShiftsTab
                )
              }

              if (roleAccess.isAdmin) {
                item(key = "dash_admin_governance", contentType = "admin_governance") {
                  AdminUserGovernanceCard(
                    userProfiles = allUserProfiles,
                    totalStaffCount = staffList.size,
                    onOpenUsersTab = onOpenUsersTab,
                    onOpenStaffTab = onOpenStaffTab,
                    onOpenRolesTab = onOpenRolesTab,
                    onOpenAppUpdatesManager = onOpenAppUpdatesManager,
                    publishedAppVersionLabel = publishedAppVersionLabel
                  )
                }
              }
            }

            item(key = "dash_kpi_1", contentType = "kpi_row") {
              Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                KpiCard(
                  title = "Shifts Optimal",
                  value = "$compliantShifts / ${coverage.size}",
                  subtitle = "Coverage targets met",
                  accent = if (compliantShifts == coverage.size) SignalEmerald else SafetyAmber,
                  modifier = Modifier.weight(1f)
                )
                KpiCard(
                  title = "Active Assigned",
                  value = "${assignedTodayIds.size} / ${activeDutyStaff.size}",
                  subtitle = "On duty today",
                  accent = RailwayCobalt,
                  modifier = Modifier.weight(1f)
                )
              }
            }

            item(key = "dash_kpi_2", contentType = "kpi_row") {
              Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                KpiCard(
                  title = "Leave / Rest",
                  value = "$onLeaveCount / $onRestCount",
                  subtitle = "Leave · Weekly Rest",
                  accent = SignalCrimson,
                  modifier = Modifier.weight(1f)
                )
                KpiCard(
                  title = "Pointsmen",
                  value = "$pointsmenAssignedToday / $pointsmenCount",
                  subtitle = "Assigned to PM slots",
                  accent = DepotTeal,
                  modifier = Modifier.weight(1f)
                )
              }
            }

            // Dedicated Section: Training Due Technicians (Periodic Technical Training Tracker)
            item(key = "dash_training_due_technicians", contentType = "training_due") {
              TrainingDueTechniciansCard(
                items = trainingDueItems,
                canManageTrainingDue = roleAccess.canManageTrainingDue,
                isBusy = isBusy,
                onClickAssignNew = { showAssignTrainingDueDialog = true },
                onClickEditMember = { member -> editingTrainingDueStaff = member },
                onClickRelieveForTraining = onRelieveForTraining
              )
            }

            // Dedicated Alert 1: Dates With >5 Staff in Shift
            item(key = "dash_over_five_shifts", contentType = "over_five_shifts") {
              OverFiveStaffShiftDatesCard(
                alerts = overFiveShiftAlerts,
                onJumpToDate = { targetDate ->
                  onSelectDate(targetDate)
                  onOpenShiftsTab()
                }
              )
            }

            // Dedicated Alert 2: Future Tight Staff Position Dates (Next 30 Days Based on Leave)
            item(key = "dash_tight_staff_forecast", contentType = "tight_staff_forecast") {
              FutureTightStaffForecastCard(
                forecasts = tightStaffForecasts,
                onJumpToDate = { targetDate ->
                  onSelectDate(targetDate)
                  onOpenShiftsTab()
                },
                onOpenLeaveCalendar = onOpenLeaveCalendar
              )
            }

            // Active Duty Relief Permissions Summary (only if role has Permissions access)
            if (isSupervisorOrAdmin && roleAccess.canViewPermissions) {
              item(key = "dash_permissions_summary", contentType = "permissions_summary") {
                ActivePermissionsSummaryCard(
                  todayPermissions = todayPermissions,
                  monthTotalHours = monthPermissionHours,
                  onOpenPermissionManager = onOpenPermissionsTab
                )
              }
            }

            item(key = "dash_coverage", contentType = "coverage_card") {
              GlassTile(
                shape = RoundedCornerShape(24.dp),
                topColor = GlassSurfaceBright,
                bottomColor = GlassSurfaceMid
              ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                  Text("Shift Coverage Breakdown", style = MaterialTheme.typography.titleMedium)
                  coverage.forEach { item ->
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                      val labelPrefix = if (item.shiftType == ShiftType.SHIFT_22_06) {
                        "$prevDateStr · ${item.shiftType.code} ${item.shiftType.displayName}"
                      } else {
                        "${item.shiftType.code} ${item.shiftType.displayName}"
                      }
                      Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                      ) {
                        Text(
                          text = labelPrefix,
                          style = MaterialTheme.typography.titleSmall,
                          fontWeight = FontWeight.SemiBold,
                          modifier = Modifier
                            .weight(1f)
                            .padding(end = 8.dp),
                          maxLines = 2
                        )
                        Icon(
                          imageVector = if (item.isFullyCompliant) Icons.Default.CheckCircle else Icons.Default.Warning,
                          contentDescription = if (item.isFullyCompliant) "Shift targets met" else "Coverage attention needed",
                          tint = if (item.isFullyCompliant) SignalEmerald else SafetyAmber,
                          modifier = Modifier.size(18.dp)
                        )
                      }
                      FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                      ) {
                        if (item.shiftType.isGeneralShift) {
                          GlassPillBadge(
                            text = "GEN ${item.shedFilled}/0–3",
                            tintColor = GlassSurfaceSoft,
                            textColor = MaterialTheme.colorScheme.onSurface,
                            borderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
                          )
                        } else {
                          val scCode = item.shiftType.stationArea?.shortCode ?: "STN"
                          GlassPillBadge(
                            text = "PM ${item.pointsmanFilled}/1",
                            tintColor = if (item.pointsmanFilled >= 1) SignalEmeraldContainer.copy(alpha = 0.7f) else SafetyAmberContainer.copy(alpha = 0.7f),
                            textColor = if (item.pointsmanFilled >= 1) SignalEmeraldOnContainer else SafetyAmberOnContainer,
                            borderColor = if (item.pointsmanFilled >= 1) SignalEmerald else SafetyAmber
                          )
                          GlassPillBadge(
                            text = "SHED ${item.shedFilled}/3–5",
                            tintColor = if (item.shedFilled in 3..5) SignalEmeraldContainer.copy(alpha = 0.7f) else SafetyAmberContainer.copy(alpha = 0.7f),
                            textColor = if (item.shedFilled in 3..5) SignalEmeraldOnContainer else SafetyAmberOnContainer,
                            borderColor = if (item.shedFilled in 3..5) SignalEmerald else SafetyAmber
                          )
                          GlassPillBadge(
                            text = "$scCode ${item.stationFilled}/2",
                            tintColor = if (item.stationFilled >= 2) SignalEmeraldContainer.copy(alpha = 0.7f) else SafetyAmberContainer.copy(alpha = 0.7f),
                            textColor = if (item.stationFilled >= 2) SignalEmeraldOnContainer else SafetyAmberOnContainer,
                            borderColor = if (item.stationFilled >= 2) SignalEmerald else SafetyAmber
                          )
                          if (item.medActive) {
                            GlassPillBadge(
                              text = "MED ${item.medFilled}/1",
                              tintColor = if (item.medFilled >= 1) SignalEmeraldContainer.copy(alpha = 0.7f) else SafetyAmberContainer.copy(alpha = 0.7f),
                              textColor = if (item.medFilled >= 1) SignalEmeraldOnContainer else SafetyAmberOnContainer,
                              borderColor = if (item.medFilled >= 1) SignalEmerald else SafetyAmber
                            )
                          }
                        }
                      }
                      val progress = if (item.shiftType.isGeneralShift) {
                        (item.shedFilled.toFloat() / 3f).coerceIn(0f, 1f)
                      } else {
                        val requiredTotal = item.pointsmanTarget + item.shedMinTarget + item.stationTarget + item.medTarget
                        val filledRequired = item.pointsmanFilled.coerceAtMost(item.pointsmanTarget) +
                          item.shedFilled.coerceAtMost(item.shedMinTarget) +
                          item.stationFilled.coerceAtMost(item.stationTarget) +
                          item.medFilled.coerceAtMost(item.medTarget)
                        (filledRequired.toFloat() / requiredTotal.coerceAtLeast(1)).coerceIn(0f, 1f)
                      }
                      LinearProgressIndicator(
                        progress = { progress },
                        color = if (item.isFullyCompliant) SignalEmerald else RailwayCobalt,
                        trackColor = GlassSurfaceSoft,
                        modifier = Modifier
                          .fillMaxWidth()
                          .height(8.dp)
                      )
                    }
                  }
                }
              }
            }

            item(key = "dash_workload", contentType = "workload_card") {
              GlassTile(
                shape = RoundedCornerShape(24.dp),
                topColor = GlassSurfaceBright,
                bottomColor = GlassSurfaceMid
              ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                  Text("Weekly Workload Distribution ($weekStart – $weekEnd)", style = MaterialTheme.typography.titleMedium)
                  Text(
                    "Top active duty personnel by assigned shifts this week.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                  )

                  if (weeklyCounts.all { it.second == 0 }) {
                    Text(
                      text = "No shifts assigned for this week yet. Use Auto-Assign Week on the Schedule tab.",
                      style = MaterialTheme.typography.bodySmall,
                      color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                  } else {
                    val maxCount = (weeklyCounts.maxOfOrNull { it.second } ?: 1).coerceAtLeast(1)
                    val barColor = MaterialTheme.colorScheme.primary
                    val trackColor = MaterialTheme.colorScheme.surfaceVariant
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                      weeklyCounts.forEach { (shortName, count) ->
                        Row(
                          modifier = Modifier.fillMaxWidth(),
                          verticalAlignment = Alignment.CenterVertically,
                          horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                          Text(
                            text = shortName,
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 2,
                            modifier = Modifier.widthIn(min = 96.dp, max = 112.dp)
                          )
                          Canvas(
                            modifier = Modifier
                              .weight(1f)
                              .height(14.dp)
                          ) {
                            drawRoundRect(
                              color = trackColor,
                              size = size,
                              cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx())
                            )
                            val ratio = count.toFloat() / maxCount.toFloat()
                            if (ratio > 0f) {
                              drawRoundRect(
                                color = barColor,
                                topLeft = Offset.Zero,
                                size = Size(size.width * ratio, size.height),
                                cornerRadius = CornerRadius(6.dp.toPx(), 6.dp.toPx())
                              )
                            }
                          }
                          Text(
                            text = "$count",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                          )
                        }
                      }
                    }
                  }
                }
              }
            }

            item(key = "dash_overrides_title", contentType = "section_title") {
              Text(
                text = "Emergency Override Audit Log (${overrides.size})",
                style = MaterialTheme.typography.titleMedium
              )
            }

            if (overrides.isEmpty()) {
              item(key = "dash_overrides_empty", contentType = "empty_overrides") {
                GlassTile(
                  shape = RoundedCornerShape(22.dp),
                  topColor = GlassSurfaceBright,
                  bottomColor = GlassSurfaceMid,
                  tintColor = SignalEmeraldContainer.copy(alpha = 0.40f),
                  modifier = Modifier.fillMaxWidth()
                ) {
                  Text(
                    text = "Zero emergency overrides recorded. All assignments comply with safety & rest rules.",
                    style = MaterialTheme.typography.bodySmall,
                    color = SignalEmeraldOnContainer,
                    modifier = Modifier.padding(14.dp)
                  )
                }
              }
            } else {
              items(
                items = overrides,
                key = { it.id },
                contentType = { "override_log" }
              ) { log ->
                GlassTile(
                  shape = RoundedCornerShape(22.dp),
                  topColor = GlassSurfaceBright,
                  bottomColor = GlassSurfaceMid,
                  tintColor = SafetyAmberContainer.copy(alpha = 0.25f)
                ) {
                  Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                      modifier = Modifier.fillMaxWidth(),
                      horizontalArrangement = Arrangement.SpaceBetween,
                      verticalAlignment = Alignment.CenterVertically
                    ) {
                      Text(
                        text = "${log.staffName} · ${log.date}",
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 2,
                        modifier = Modifier
                          .weight(1f)
                          .padding(end = 8.dp)
                      )
                      GlassPillBadge(
                        text = log.shiftType.removePrefix("SHIFT_").replace("_", "/"),
                        tintColor = SafetyAmberContainer.copy(alpha = 0.65f),
                        textColor = SafetyAmberOnContainer,
                        borderColor = SafetyAmber
                      )
                    }
                    Text(
                      text = "Bypassed: ${log.conflictSummary}",
                      style = MaterialTheme.typography.bodySmall,
                      color = SignalCrimsonOnContainer
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
                    Text(
                      text = "Reason: ${log.reason}",
                      style = MaterialTheme.typography.bodySmall,
                      fontWeight = FontWeight.Medium
                    )
                  }
                }
              }
            }

            if (isSupervisorOrAdmin || roleAccess.canViewImportantContacts) {
              item(key = "dash_important_phone_numbers", contentType = "important_phones") {
                ImportantPhoneNumbersCard(
                  phoneActions = phoneActions,
                  contacts = importantContacts,
                  canManageContacts = roleAccess.isAdmin,
                  isBusy = isBusy,
                  onAddContact = onAddImportantContact,
                  onDeleteContact = onDeleteImportantContact
                )
              }
            }

            item(key = "dash_bottom_spacer", contentType = "spacer") {
              Spacer(modifier = Modifier.height(16.dp))
            }
          }
        }
      }
    }
  }

  if (showAssignTrainingDueDialog && roleAccess.canManageTrainingDue) {
    SetTrainingDueDialog(
      fixedStaff = null,
      selectableStaff = activeDutyStaff,
      isBusy = isBusy,
      onDismiss = { showAssignTrainingDueDialog = false },
      onSave = { staff, dueDate, course ->
        onUpdateStaffTrainingDue(staff.id, staff.name, dueDate, course)
        showAssignTrainingDueDialog = false
      }
    )
  }

  val targetTrainingStaff = editingTrainingDueStaff
  if (targetTrainingStaff != null && roleAccess.canManageTrainingDue) {
    SetTrainingDueDialog(
      fixedStaff = targetTrainingStaff,
      isBusy = isBusy,
      onDismiss = { editingTrainingDueStaff = null },
      onSave = { staff, dueDate, course ->
        onUpdateStaffTrainingDue(staff.id, staff.name, dueDate, course)
        editingTrainingDueStaff = null
      }
    )
  }
}

@Composable
private fun KpiCard(
  title: String,
  value: String,
  subtitle: String,
  accent: Color,
  modifier: Modifier = Modifier
) {
  GlassTile(
    modifier = modifier,
    shape = RoundedCornerShape(24.dp),
    topColor = GlassSurfaceBright,
    bottomColor = GlassSurfaceMid,
    tintColor = accent.copy(alpha = 0.08f)
  ) {
    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
      Text(
        text = title,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
      Text(
        text = value,
        style = MaterialTheme.typography.headlineMedium,
        color = accent,
        fontWeight = FontWeight.Bold
      )
      Text(
        text = subtitle,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
    }
  }
}
