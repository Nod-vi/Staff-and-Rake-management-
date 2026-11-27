package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FactCheck
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.EventBusy
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Weekend
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.domain.LeaveBalanceEngine
import com.example.domain.StaffDutyItem
import com.example.domain.StaffUpcomingDutySummary
import com.example.model.LeaveRecord
import com.example.model.LeaveStatus
import com.example.model.LeaveType
import com.example.model.ShiftSlotAssignment
import com.example.model.ShiftType
import com.example.model.SlotRoleType
import com.example.model.StaffLeaveAccount
import com.example.model.StaffMember
import com.example.model.WeeklyRestDay
import com.example.notifications.ScheduledDutyReminderInfo
import com.example.notifications.TechnicianDutyNotifier
import com.example.ui.components.TechnicianDutyNotificationCard
import com.example.ui.theme.DepotTeal
import com.example.ui.theme.DepotTealContainer
import com.example.ui.theme.DepotTealOnContainer
import com.example.ui.theme.GlassIconOrb
import com.example.ui.theme.GlassPillBadge
import com.example.ui.theme.GlassRimHighlight
import com.example.ui.theme.GlassSurfaceBright
import com.example.ui.theme.GlassSurfaceMid
import com.example.ui.theme.GlassSurfaceSoft
import com.example.ui.theme.GlassTile
import com.example.ui.theme.RailwayCobalt
import com.example.ui.theme.SafetyAmber
import com.example.ui.theme.SafetyAmberContainer
import com.example.ui.theme.SafetyAmberOnContainer
import com.example.ui.theme.SignalCrimson
import com.example.ui.theme.SignalCrimsonContainer
import com.example.ui.theme.SignalCrimsonOnContainer
import com.example.ui.theme.SignalEmerald
import com.example.ui.theme.SignalEmeraldContainer
import com.example.ui.theme.SignalEmeraldOnContainer
import com.example.ui.theme.liquidGlassAccent
import com.example.ui.theme.liquidGlassSurface
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val techDashDateFormatter = DateTimeFormatter.ofPattern("EEE, dd MMM yyyy")

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TechnicianDashboardContent(
  staffList: List<StaffMember>,
  assignments: List<ShiftSlotAssignment>,
  leaves: List<LeaveRecord>,
  leaveAccounts: List<StaffLeaveAccount>,
  linkedStaffId: String,
  isBusy: Boolean,
  onUpdateLapBalance: (StaffMember, Int, Int, String, Int) -> Unit,
  onOpenDutiesTab: () -> Unit,
  onOpenLeaveTab: () -> Unit,
  onApplyTrainingLeave: () -> Unit = onOpenLeaveTab,
  dutyNotificationsEnabled: Boolean = true,
  dutyReminderLeadMinutes: Int = TechnicianDutyNotifier.DEFAULT_LEAD_MINUTES,
  nextScheduledReminder: ScheduledDutyReminderInfo? = null,
  onToggleDutyNotifications: (Boolean) -> Unit = {},
  onSelectDutyReminderLeadMinutes: (Int) -> Unit = {},
  onNotificationPermissionChanged: () -> Unit = {},
  onSendTestDutyNotification: () -> Unit = {}
) {
  val today = remember { LocalDate.now() }
  var selectedYear by rememberSaveable { mutableIntStateOf(today.year) }
  var showLapDialog by remember { mutableStateOf(false) }

  val linkedStaff = remember(staffList, linkedStaffId) {
    linkedStaffId.trim().takeIf { it.isNotBlank() }?.let { id ->
      staffList.firstOrNull { it.id == id }
    }
  }

  val linkedAccount = remember(linkedStaff, leaveAccounts) {
    linkedStaff?.let { staff ->
      leaveAccounts.firstOrNull { it.staffId == staff.id || it.id == staff.id }
    }
  }

  val balanceSummary = remember(linkedStaff, selectedYear, linkedAccount, leaves, assignments) {
    linkedStaff?.let { staff ->
      LeaveBalanceEngine.computeBalanceSummary(
        staff = staff,
        year = selectedYear,
        account = linkedAccount,
        allLeaves = leaves,
        allAssignments = assignments
      )
    }
  }

  val upcomingDuties = remember(linkedStaff, today, assignments, leaves) {
    linkedStaff?.let { staff ->
      LeaveBalanceEngine.buildUpcomingDutiesForStaff(
        staff = staff,
        baseToday = today,
        allAssignments = assignments,
        allLeaves = leaves
      )
    }
  }

  val activePersonalTrainingLeave = remember(linkedStaff, today, leaves) {
    val staff = linkedStaff ?: return@remember null
    val todayStr = today.toString()
    leaves
      .filter {
        it.staffId == staff.id &&
          it.leaveTypeEnum == LeaveType.TRAINING &&
          (it.statusEnum == LeaveStatus.APPROVED || it.statusEnum == LeaveStatus.PENDING) &&
          it.endDate >= todayStr
      }
      .sortedBy { it.startDate }
      .firstOrNull()
  }

  Box(
    modifier = Modifier.fillMaxSize(),
    contentAlignment = Alignment.TopCenter
  ) {
    LazyColumn(
      modifier = Modifier
        .fillMaxSize()
        .widthIn(max = 780.dp)
        .testTag("technician_dashboard_list"),
      contentPadding = PaddingValues(16.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
      if (linkedStaff == null || balanceSummary == null || upcomingDuties == null) {
        item(key = "tech_unlinked_card") {
          GlassTile(
            shape = RoundedCornerShape(24.dp),
            topColor = GlassSurfaceBright,
            bottomColor = GlassSurfaceMid,
            modifier = Modifier
              .fillMaxWidth()
              .testTag("technician_unlinked_banner")
          ) {
            Column(
              modifier = Modifier.padding(18.dp),
              verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
              ) {
                GlassIconOrb(
                  shape = RoundedCornerShape(14.dp),
                  tintColor = SafetyAmberContainer.copy(alpha = 0.7f),
                  modifier = Modifier.size(44.dp)
                ) {
                  Icon(Icons.Default.Warning, contentDescription = null, tint = SafetyAmber)
                }
                Column {
                  Text(
                    text = "Technician Dashboard",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                  )
                  Text(
                    text = "Staff profile not linked yet",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                  )
                }
              }
              Text(
                text = "Ask your Depot Admin to link your account to your staff profile in the Users tab. Once linked, your personal Tomorrow's Duties, Jan–Dec Leave History, LAP/LHAP/CL balances, and Special Leave credits will appear right here.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
              )
              Button(
                onClick = onOpenDutiesTab,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                  .fillMaxWidth()
                  .heightIn(min = 48.dp)
                  .testTag("tech_unlinked_open_duties_button")
              ) {
                Icon(Icons.AutoMirrored.Filled.FactCheck, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("View Depot Tomorrow's Duties")
              }
            }
          }
        }
      } else {
        // 1. Personal Technician Header + Year selector
        item(key = "tech_profile_header") {
          GlassTile(
            shape = RoundedCornerShape(24.dp),
            topColor = GlassSurfaceBright,
            bottomColor = GlassSurfaceMid,
            modifier = Modifier
              .fillMaxWidth()
              .testTag("technician_profile_header")
          ) {
            Column(
              modifier = Modifier.padding(16.dp),
              verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
              ) {
                GlassIconOrb(
                  shape = RoundedCornerShape(14.dp),
                  tintColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                  modifier = Modifier.size(44.dp)
                ) {
                  Icon(
                    imageVector = Icons.Default.Badge,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                  )
                }
                Column(modifier = Modifier.weight(1f)) {
                  Text(
                    text = linkedStaff.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2
                  )
                  Text(
                    text = "#${linkedStaff.serialNo} · ${linkedStaff.designationEnum.label}" +
                      if (linkedStaff.isPointsman) " · Pointsman" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                  )
                }
              }

              if (linkedStaff.restDayEnum != WeeklyRestDay.NONE) {
                FlowRow(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.spacedBy(6.dp),
                  verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                  GlassPillBadge(
                    text = "REST: ${linkedStaff.restDayEnum.label.uppercase()}",
                    tintColor = DepotTealContainer.copy(alpha = 0.65f),
                    textColor = DepotTealOnContainer,
                    borderColor = DepotTeal
                  )
                }
              }

              // Year Picker Row for Jan-Dec Accounting
              Row(
                modifier = Modifier
                  .fillMaxWidth()
                  .liquidGlassSurface(
                    shape = RoundedCornerShape(14.dp),
                    topColor = GlassSurfaceBright,
                    bottomColor = GlassSurfaceSoft,
                    borderWidth = 1.dp
                  )
                  .padding(horizontal = 6.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
              ) {
                IconButton(
                  onClick = { if (selectedYear > 2020) selectedYear -= 1 },
                  modifier = Modifier
                    .size(48.dp)
                    .testTag("tech_year_prev_button")
                ) {
                  Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous Year")
                }
                Row(
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                  Icon(
                    imageVector = Icons.Default.CalendarMonth,
                    contentDescription = null,
                    tint = RailwayCobalt,
                    modifier = Modifier.size(18.dp)
                  )
                  Text(
                    text = "Leave Year: Jan – Dec $selectedYear",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                  )
                }
                IconButton(
                  onClick = { if (selectedYear < 2100) selectedYear += 1 },
                  modifier = Modifier
                    .size(48.dp)
                    .testTag("tech_year_next_button")
                ) {
                  Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next Year")
                }
              }
            }
          }
        }

        // 2. Tomorrow's Duties Card (Hero Card for Technician)
        item(key = "tech_tomorrow_duties") {
          TechnicianTomorrowDutiesCard(
            upcoming = upcomingDuties,
            onOpenDutiesTab = onOpenDutiesTab,
            onOpenLeaveTab = onOpenLeaveTab
          )
        }

        // 2a. Duty Notifications & Pre-Shift Reminders Controls Card
        item(key = "tech_duty_notifications") {
          TechnicianDutyNotificationCard(
            enabled = dutyNotificationsEnabled,
            leadMinutes = dutyReminderLeadMinutes,
            nextReminder = nextScheduledReminder,
            onToggleEnabled = onToggleDutyNotifications,
            onSelectLeadMinutes = onSelectDutyReminderLeadMinutes,
            onPermissionChanged = onNotificationPermissionChanged,
            onSendTestNotification = onSendTestDutyNotification
          )
        }

        // 2b. Personal Training Due Card (when technician has a periodic training due date)
        if (linkedStaff.hasTrainingDue) {
          item(key = "tech_training_due_notice") {
            TechnicianPersonalTrainingDueCard(
              staff = linkedStaff,
              today = today,
              activeTrainingLeave = activePersonalTrainingLeave,
              onApplyTrainingLeave = onApplyTrainingLeave
            )
          }
        }

        // 3. Leave Balance Account Card (CL / LAP / LHAP / Special + Update LAP)
        item(key = "tech_leave_balance_card") {
          LeaveBalanceOverviewCard(
            summary = balanceSummary,
            canUpdateLap = true,
            isApprover = false,
            isBusy = isBusy,
            onClickUpdateLap = { showLapDialog = true }
          )
        }

        // 4. Special Leave Account Card (Rest Day Duty & 2 Duties in Single Day)
        item(key = "tech_special_leave_card") {
          SpecialLeaveCreditsCard(summary = balanceSummary)
        }

        // 5. Leaves Availed This Year (Jan - Dec)
        item(key = "tech_leaves_availed_header") {
          GlassTile(
            shape = RoundedCornerShape(22.dp),
            topColor = GlassSurfaceBright,
            bottomColor = GlassSurfaceMid,
            modifier = Modifier
              .fillMaxWidth()
              .testTag("tech_leaves_availed_summary_card")
          ) {
            Column(
              modifier = Modifier.padding(16.dp),
              verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
              ) {
                Row(
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                  Icon(Icons.Default.EventBusy, contentDescription = null, tint = SignalCrimson)
                  Text(
                    text = "Leaves Availed in $selectedYear (Jan–Dec)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                  )
                }
              }

              val totalAvailedDays = balanceSummary.clAvailed +
                balanceSummary.lapAvailed +
                balanceSummary.lhapAvailed +
                balanceSummary.specialAvailedThisYear

              FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
              ) {
                GlassPillBadge(
                  text = "CL: ${balanceSummary.clAvailed} / ${balanceSummary.clEntitlement}",
                  tintColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f),
                  textColor = MaterialTheme.colorScheme.onPrimaryContainer,
                  borderColor = RailwayCobalt
                )
                GlassPillBadge(
                  text = "LAP: ${balanceSummary.lapAvailed} DAYS",
                  tintColor = DepotTealContainer.copy(alpha = 0.65f),
                  textColor = DepotTealOnContainer,
                  borderColor = DepotTeal
                )
                GlassPillBadge(
                  text = "LHAP: ${balanceSummary.lhapAvailed} DAYS",
                  tintColor = SafetyAmberContainer.copy(alpha = 0.65f),
                  textColor = SafetyAmberOnContainer,
                  borderColor = SafetyAmber
                )
                GlassPillBadge(
                  text = "SPECIAL: ${balanceSummary.specialAvailedThisYear} DAYS",
                  tintColor = SignalEmeraldContainer.copy(alpha = 0.65f),
                  textColor = SignalEmeraldOnContainer,
                  borderColor = SignalEmerald
                )
                if (balanceSummary.trainingAvailed > 0) {
                  GlassPillBadge(
                    text = "TRAINING: ${balanceSummary.trainingAvailed} DAYS",
                    tintColor = DepotTealContainer.copy(alpha = 0.72f),
                    textColor = DepotTealOnContainer,
                    borderColor = DepotTeal
                  )
                }
                GlassPillBadge(
                  text = "TOTAL: $totalAvailedDays DAYS",
                  tintColor = SignalCrimsonContainer.copy(alpha = 0.65f),
                  textColor = SignalCrimsonOnContainer,
                  borderColor = SignalCrimson
                )
              }
            }
          }
        }

        if (balanceSummary.leavesInYear.isEmpty()) {
          item(key = "tech_leaves_empty") {
            GlassTile(
              shape = RoundedCornerShape(20.dp),
              topColor = GlassSurfaceBright,
              bottomColor = GlassSurfaceSoft,
              modifier = Modifier.fillMaxWidth()
            ) {
              Text(
                text = "No leaves availed or applied between Jan 1 and Dec 31, $selectedYear.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp)
              )
            }
          }
        } else {
          items(
            items = balanceSummary.leavesInYear,
            key = { "tech_leave_${it.id}" }
          ) { leave ->
            TechnicianYearLeaveRow(leave = leave, year = selectedYear)
          }
        }
      }

      item(key = "tech_dashboard_bottom_spacer") {
        Spacer(modifier = Modifier.height(16.dp))
      }
    }
  }

  if (showLapDialog && linkedStaff != null && balanceSummary != null) {
    LapUpdateDialog(
      summary = balanceSummary,
      isApprover = false,
      isBusy = isBusy,
      onDismiss = { showLapDialog = false },
      onConfirm = { newLap, newLhap, reason ->
        onUpdateLapBalance(linkedStaff, newLap, newLhap, reason, selectedYear)
        showLapDialog = false
      }
    )
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TechnicianTomorrowDutiesCard(
  upcoming: StaffUpcomingDutySummary,
  onOpenDutiesTab: () -> Unit,
  onOpenLeaveTab: () -> Unit
) {
  GlassTile(
    shape = RoundedCornerShape(24.dp),
    topColor = GlassSurfaceBright,
    bottomColor = GlassSurfaceMid,
    tintColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.16f),
    modifier = Modifier
      .fillMaxWidth()
      .testTag("technician_tomorrow_duties_card")
  ) {
    Column(
      modifier = Modifier.padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
      ) {
        GlassIconOrb(
          shape = RoundedCornerShape(14.dp),
          tintColor = SignalEmeraldContainer.copy(alpha = 0.7f),
          modifier = Modifier.size(42.dp)
        ) {
          Icon(Icons.Default.Schedule, contentDescription = null, tint = SignalEmerald)
        }
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = "Your Tomorrow's Duties",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 2
          )
          Text(
            text = upcoming.tomorrowDutyDate.format(techDashDateFormatter),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
      }

      val isTomorrowTraining = upcoming.tomorrowApprovedLeave?.leaveTypeEnum == LeaveType.TRAINING
      FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
      ) {
        GlassPillBadge(
          text = when {
            upcoming.tomorrowDuties.isNotEmpty() -> "${upcoming.tomorrowDuties.size} DUTY"
            isTomorrowTraining -> "TRAINING"
            upcoming.tomorrowApprovedLeave != null -> "ON LEAVE"
            upcoming.isTomorrowWeeklyRest -> "WEEKLY REST"
            else -> "UNASSIGNED"
          },
          tintColor = when {
            upcoming.tomorrowDuties.isNotEmpty() -> SignalEmeraldContainer.copy(alpha = 0.75f)
            isTomorrowTraining -> DepotTealContainer.copy(alpha = 0.75f)
            upcoming.tomorrowApprovedLeave != null -> SignalCrimsonContainer.copy(alpha = 0.75f)
            upcoming.isTomorrowWeeklyRest -> DepotTealContainer.copy(alpha = 0.75f)
            else -> SafetyAmberContainer.copy(alpha = 0.75f)
          },
          textColor = when {
            upcoming.tomorrowDuties.isNotEmpty() -> SignalEmeraldOnContainer
            isTomorrowTraining -> DepotTealOnContainer
            upcoming.tomorrowApprovedLeave != null -> SignalCrimsonOnContainer
            upcoming.isTomorrowWeeklyRest -> DepotTealOnContainer
            else -> SafetyAmberOnContainer
          },
          borderColor = when {
            upcoming.tomorrowDuties.isNotEmpty() -> SignalEmerald
            isTomorrowTraining -> DepotTeal
            upcoming.tomorrowApprovedLeave != null -> SignalCrimson
            upcoming.isTomorrowWeeklyRest -> DepotTeal
            else -> SafetyAmber
          }
        )
      }

      if (upcoming.tomorrowDuties.isNotEmpty()) {
        upcoming.tomorrowDuties.forEach { duty ->
          TomorrowDutyItemRow(duty = duty, tonightDate = upcoming.tonightDate)
        }
      } else if (upcoming.tomorrowApprovedLeave != null) {
        val leave = upcoming.tomorrowApprovedLeave
        val isTraining = leave.leaveTypeEnum == LeaveType.TRAINING
        Surface(
          color = (if (isTraining) DepotTealContainer else SignalCrimsonContainer).copy(alpha = 0.65f),
          shape = RoundedCornerShape(14.dp),
          modifier = Modifier.fillMaxWidth()
        ) {
          Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
          ) {
            Icon(
              imageVector = if (isTraining) Icons.Default.School else Icons.Default.EventBusy,
              contentDescription = null,
              tint = if (isTraining) DepotTeal else SignalCrimson
            )
            Text(
              text = if (isTraining) {
                "Relieved for Technical Training (${leave.startDate} to ${leave.endDate})."
              } else {
                "Approved ${leave.leaveTypeEnum.code} Leave (${leave.startDate} to ${leave.endDate})."
              },
              style = MaterialTheme.typography.bodyMedium,
              fontWeight = FontWeight.SemiBold,
              color = if (isTraining) DepotTealOnContainer else SignalCrimsonOnContainer
            )
          }
        }
      } else if (upcoming.isTomorrowWeeklyRest) {
        Surface(
          color = DepotTealContainer.copy(alpha = 0.65f),
          shape = RoundedCornerShape(14.dp),
          modifier = Modifier.fillMaxWidth()
        ) {
          Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
          ) {
            Icon(Icons.Default.Weekend, contentDescription = null, tint = DepotTeal)
            Text(
              text = "Tomorrow is your scheduled Weekly Rest Day.",
              style = MaterialTheme.typography.bodyMedium,
              fontWeight = FontWeight.SemiBold,
              color = DepotTealOnContainer
            )
          }
        }
      } else {
        Surface(
          color = GlassSurfaceSoft,
          shape = RoundedCornerShape(14.dp),
          modifier = Modifier.fillMaxWidth()
        ) {
          Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
          ) {
            Icon(Icons.Default.EventAvailable, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
              text = "No shift assigned yet for tonight 22/06 or tomorrow (${upcoming.tomorrowDutyDate}). Check again after Supervisor publishes duties.",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
        }
      }

      if (upcoming.todayDuties.isNotEmpty()) {
        HorizontalDivider(color = GlassRimHighlight)
        val todayText = upcoming.todayDuties.joinToString(" · ") {
          "${it.shiftType.code} ${it.dutyArea.label}"
        }
        Text(
          text = "Today's Duty: $todayText",
          style = MaterialTheme.typography.bodySmall,
          fontWeight = FontWeight.Medium,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
      ) {
        Button(
          onClick = onOpenDutiesTab,
          elevation = ButtonDefaults.buttonElevation(0.dp, 0.dp, 0.dp, 0.dp, 0.dp),
          colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent, contentColor = Color.White),
          contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
          shape = RoundedCornerShape(14.dp),
          modifier = Modifier
            .weight(1f)
            .heightIn(min = 48.dp)
            .liquidGlassAccent(shape = RoundedCornerShape(14.dp))
            .testTag("tech_dash_open_duties_button")
        ) {
          Text("Full Duties", maxLines = 2, textAlign = TextAlign.Center)
        }
        OutlinedButton(
          onClick = onOpenLeaveTab,
          colors = ButtonDefaults.outlinedButtonColors(containerColor = GlassSurfaceSoft),
          border = BorderStroke(1.dp, GlassRimHighlight),
          contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
          shape = RoundedCornerShape(14.dp),
          modifier = Modifier
            .weight(1f)
            .heightIn(min = 48.dp)
            .testTag("tech_dash_apply_leave_button")
        ) {
          Text("Apply Leave", maxLines = 2, textAlign = TextAlign.Center)
        }
      }
    }
  }
}

@Composable
private fun TomorrowDutyItemRow(
  duty: StaffDutyItem,
  tonightDate: LocalDate
) {
  val isNight = duty.shiftType == ShiftType.SHIFT_22_06
  val accent = when (duty.shiftType) {
    ShiftType.SHIFT_22_06 -> RailwayCobalt
    ShiftType.SHIFT_06_14 -> SignalEmerald
    ShiftType.SHIFT_14_22 -> SafetyAmber
    ShiftType.SHIFT_08_17 -> DepotTeal
  }
  val dateNote = if (isNight && duty.assignment.date == tonightDate.toString()) {
    "Tonight (${duty.assignment.date})"
  } else {
    duty.assignment.date
  }
  Row(
    modifier = Modifier
      .fillMaxWidth()
      .liquidGlassSurface(
        shape = RoundedCornerShape(16.dp),
        topColor = GlassSurfaceBright,
        bottomColor = GlassSurfaceSoft,
        tintColor = accent.copy(alpha = 0.12f),
        borderWidth = 1.dp
      )
      .padding(12.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.SpaceBetween
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(10.dp),
      modifier = Modifier.weight(1f)
    ) {
      GlassIconOrb(
        shape = RoundedCornerShape(12.dp),
        tintColor = accent.copy(alpha = 0.18f),
        modifier = Modifier.size(38.dp)
      ) {
        Icon(
          imageVector = if (isNight) Icons.Default.NightsStay else Icons.Default.Schedule,
          contentDescription = null,
          tint = accent,
          modifier = Modifier.size(20.dp)
        )
      }
      Column(modifier = Modifier.padding(end = 8.dp)) {
        Text(
          text = "${duty.shiftType.code} ${duty.shiftType.displayName} · ${duty.dutyArea.label}",
          style = MaterialTheme.typography.titleSmall,
          fontWeight = FontWeight.Bold,
          maxLines = 2
        )
        Text(
          text = "${duty.shiftType.timeWindow} · $dateNote" +
            if (duty.assignment.roleTypeEnum == SlotRoleType.POINTSMAN) " · Pointsman" else "",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 2
        )
      }
    }
    if (duty.assignment.approvedLocked) {
      GlassPillBadge(
        text = "APPROVED",
        tintColor = SignalEmeraldContainer.copy(alpha = 0.7f),
        textColor = SignalEmeraldOnContainer,
        borderColor = SignalEmerald
      )
    }
  }
}

@Composable
private fun TechnicianYearLeaveRow(
  leave: LeaveRecord,
  year: Int
) {
  val daysInSelectedYear = LeaveBalanceEngine.daysInYear(leave, year)
  val (statusTint, statusText, statusBorder) = when (leave.statusEnum) {
    LeaveStatus.APPROVED -> Triple(SignalEmeraldContainer.copy(alpha = 0.75f), SignalEmeraldOnContainer, SignalEmerald)
    LeaveStatus.PENDING -> Triple(SafetyAmberContainer.copy(alpha = 0.75f), SafetyAmberOnContainer, SafetyAmber)
    LeaveStatus.REJECTED, LeaveStatus.CANCELLED -> Triple(SignalCrimsonContainer.copy(alpha = 0.75f), SignalCrimsonOnContainer, SignalCrimson)
  }

  GlassTile(
    shape = RoundedCornerShape(18.dp),
    topColor = GlassSurfaceBright,
    bottomColor = GlassSurfaceSoft,
    modifier = Modifier.fillMaxWidth()
  ) {
    Column(
      modifier = Modifier.padding(14.dp),
      verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          GlassPillBadge(
            text = leave.leaveTypeEnum.code,
            tintColor = DepotTealContainer.copy(alpha = 0.7f),
            textColor = DepotTealOnContainer,
            borderColor = DepotTeal
          )
          Text(
            text = "${leave.startDate} → ${leave.endDate}",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold
          )
        }
        GlassPillBadge(
          text = leave.statusEnum.label.uppercase(),
          tintColor = statusTint,
          textColor = statusText,
          borderColor = statusBorder
        )
      }
      Text(
        text = "$daysInSelectedYear ${if (daysInSelectedYear == 1) "day" else "days"} in $year · ${leave.leaveTypeEnum.label}" +
          if (leave.reviewedByName.isNotBlank()) " · By ${leave.reviewedByName}" else "",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
      if (leave.notes.isNotBlank()) {
        Text(
          text = leave.notes,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurface
        )
      }
    }
  }
}
