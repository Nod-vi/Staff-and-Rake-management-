package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.EventBusy
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.domain.ShiftOverFiveAlert
import com.example.domain.StaffTightnessLevel
import com.example.domain.SupervisorAnalyticsEngine
import com.example.domain.TightStaffDayForecast
import com.example.model.DutyPermissionRecord
import com.example.ui.theme.DepotTeal
import com.example.ui.theme.DepotTealContainer
import com.example.ui.theme.DepotTealOnContainer
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
import com.example.ui.theme.SignalCrimsonContainer
import com.example.ui.theme.SignalCrimsonOnContainer
import com.example.ui.theme.SignalEmerald
import com.example.ui.theme.SignalEmeraldContainer
import com.example.ui.theme.SignalEmeraldOnContainer
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val ALERT_DATE_FMT = DateTimeFormatter.ofPattern("EEE, dd MMM", Locale.ENGLISH)

@Composable
fun OverFiveStaffShiftDatesCard(
  alerts: List<ShiftOverFiveAlert>,
  onJumpToDate: (LocalDate) -> Unit
) {
  var showAll by remember { mutableStateOf(false) }
  val displayed = if (showAll) alerts else alerts.take(6)

  GlassTile(
    shape = RoundedCornerShape(24.dp),
    topColor = GlassSurfaceBright,
    bottomColor = GlassSurfaceMid,
    tintColor = if (alerts.isNotEmpty()) RailwayCobalt.copy(alpha = 0.06f) else GlassSurfaceMid,
    modifier = Modifier
      .fillMaxWidth()
      .testTag("dashboard_over_five_shifts_card")
  ) {
    Column(
      modifier = Modifier.padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(10.dp),
          modifier = Modifier.weight(1f)
        ) {
          GlassIconOrb(
            shape = RoundedCornerShape(12.dp),
            tintColor = RailwayCobalt.copy(alpha = 0.14f),
            modifier = Modifier.size(38.dp)
          ) {
            Icon(Icons.Default.Groups, contentDescription = null, tint = RailwayCobalt, modifier = Modifier.size(20.dp))
          }
          Column {
            Text(
              text = "Shifts With >5 Staff Assigned",
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold
            )
            Text(
              text = "Dates & shifts exceeding 5 assigned personnel (tap to open)",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
        }
        GlassPillBadge(
          text = "${alerts.size} FOUND",
          tintColor = if (alerts.isNotEmpty()) SafetyAmberContainer.copy(alpha = 0.78f) else SignalEmeraldContainer.copy(alpha = 0.7f),
          textColor = if (alerts.isNotEmpty()) SafetyAmberOnContainer else SignalEmeraldOnContainer,
          borderColor = if (alerts.isNotEmpty()) SafetyAmber else SignalEmerald
        )
      }

      if (alerts.isEmpty()) {
        Surface(
          shape = RoundedCornerShape(14.dp),
          color = SignalEmeraldContainer.copy(alpha = 0.35f),
          modifier = Modifier.fillMaxWidth()
        ) {
          Text(
            text = "No shifts currently have more than 5 staff assigned.",
            style = MaterialTheme.typography.bodySmall,
            color = SignalEmeraldOnContainer,
            modifier = Modifier.padding(12.dp)
          )
        }
      } else {
        displayed.forEach { alert ->
          val parsedDate = remember(alert.date) { runCatching { LocalDate.parse(alert.date) }.getOrNull() }
          Surface(
            shape = RoundedCornerShape(14.dp),
            color = GlassSurfaceSoft,
            border = BorderStroke(
              1.dp,
              if (alert.isShedOverFive) SignalCrimson.copy(alpha = 0.55f) else RailwayCobalt.copy(alpha = 0.30f)
            ),
            modifier = Modifier
              .fillMaxWidth()
              .heightIn(min = 48.dp)
              .clickable(enabled = parsedDate != null) {
                parsedDate?.let(onJumpToDate)
              }
              .testTag("over_five_row_${alert.date}_${alert.shiftType.name}")
          ) {
            Column(
              modifier = Modifier.padding(12.dp),
              verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
              ) {
                Text(
                  text = "${parsedDate?.format(ALERT_DATE_FMT) ?: alert.date} (${alert.date}) · ${alert.shiftType.code}",
                  style = MaterialTheme.typography.titleSmall,
                  fontWeight = FontWeight.Bold,
                  modifier = Modifier.weight(1f),
                  maxLines = 2
                )
                Spacer(modifier = Modifier.width(6.dp))
                GlassPillBadge(
                  text = "${alert.totalStaff} STAFF",
                  tintColor = if (alert.isShedOverFive) SignalCrimsonContainer else SafetyAmberContainer,
                  textColor = if (alert.isShedOverFive) SignalCrimsonOnContainer else SafetyAmberOnContainer,
                  borderColor = if (alert.isShedOverFive) SignalCrimson else SafetyAmber
                )
              }
              val breakdown = buildList {
                if (alert.pointsmanCount > 0) add("PM ${alert.pointsmanCount}")
                if (alert.shedCount > 0) add("Shed ${alert.shedCount}")
                if (alert.stationCount > 0) add("Station ${alert.stationCount}")
                if (alert.medCount > 0) add("MED ${alert.medCount}")
              }.joinToString(" · ")
              Text(
                text = "${alert.shiftType.displayName} — $breakdown",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
              Text(
                text = alert.staffNames.joinToString(", "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
              )
            }
          }
        }

        if (alerts.size > 6) {
          TextButton(
            onClick = { showAll = !showAll },
            modifier = Modifier
              .align(Alignment.End)
              .heightIn(min = 48.dp)
              .testTag("over_five_toggle_all")
          ) {
            Text(if (showAll) "Show Less" else "Show All ${alerts.size} Shifts")
          }
        }
      }
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FutureTightStaffForecastCard(
  forecasts: List<TightStaffDayForecast>,
  onJumpToDate: (LocalDate) -> Unit,
  onOpenLeaveCalendar: () -> Unit
) {
  var showAll by remember { mutableStateOf(false) }
  val displayed = if (showAll) forecasts else forecasts.take(6)
  val criticalCount = remember(forecasts) { forecasts.count { it.severity == StaffTightnessLevel.CRITICAL } }

  GlassTile(
    shape = RoundedCornerShape(24.dp),
    topColor = GlassSurfaceBright,
    bottomColor = GlassSurfaceMid,
    tintColor = if (criticalCount > 0) SignalCrimsonContainer.copy(alpha = 0.20f) else SafetyAmberContainer.copy(alpha = 0.16f),
    modifier = Modifier
      .fillMaxWidth()
      .testTag("dashboard_tight_staff_forecast_card")
  ) {
    Column(
      modifier = Modifier.padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(10.dp),
          modifier = Modifier.weight(1f)
        ) {
          GlassIconOrb(
            shape = RoundedCornerShape(12.dp),
            tintColor = SignalCrimsonContainer.copy(alpha = 0.65f),
            modifier = Modifier.size(38.dp)
          ) {
            Icon(Icons.Default.Warning, contentDescription = null, tint = SignalCrimson, modifier = Modifier.size(20.dp))
          }
          Column {
            Text(
              text = "Future Tight Staff Position (30d)",
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold
            )
            Text(
              text = "Upcoming dates stressed by approved/pending leaves & rest",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
        }
        GlassPillBadge(
          text = if (forecasts.isEmpty()) "CLEAR" else "${forecasts.size} DATES",
          tintColor = if (criticalCount > 0) SignalCrimsonContainer else if (forecasts.isNotEmpty()) SafetyAmberContainer else SignalEmeraldContainer,
          textColor = if (criticalCount > 0) SignalCrimsonOnContainer else if (forecasts.isNotEmpty()) SafetyAmberOnContainer else SignalEmeraldOnContainer,
          borderColor = if (criticalCount > 0) SignalCrimson else if (forecasts.isNotEmpty()) SafetyAmber else SignalEmerald
        )
      }

      if (forecasts.isEmpty()) {
        Surface(
          shape = RoundedCornerShape(14.dp),
          color = SignalEmeraldContainer.copy(alpha = 0.40f),
          modifier = Modifier.fillMaxWidth()
        ) {
          Text(
            text = "Staff position looks comfortable for the next 30 days based on current leave records.",
            style = MaterialTheme.typography.bodySmall,
            color = SignalEmeraldOnContainer,
            modifier = Modifier.padding(12.dp)
          )
        }
      } else {
        displayed.forEach { day ->
          val isCritical = day.severity == StaffTightnessLevel.CRITICAL
          val cardTint = if (isCritical) SignalCrimsonContainer.copy(alpha = 0.35f) else SafetyAmberContainer.copy(alpha = 0.35f)
          val border = if (isCritical) SignalCrimson.copy(alpha = 0.65f) else SafetyAmber.copy(alpha = 0.65f)

          Surface(
            shape = RoundedCornerShape(14.dp),
            color = cardTint,
            border = BorderStroke(1.dp, border),
            modifier = Modifier
              .fillMaxWidth()
              .heightIn(min = 48.dp)
              .clickable { onJumpToDate(day.date) }
              .testTag("tight_staff_day_${day.date}")
          ) {
            Column(
              modifier = Modifier.padding(12.dp),
              verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
              ) {
                Text(
                  text = "${day.date.format(ALERT_DATE_FMT)} (${day.date})",
                  style = MaterialTheme.typography.titleSmall,
                  fontWeight = FontWeight.Bold,
                  modifier = Modifier.weight(1f),
                  maxLines = 2
                )
                Spacer(modifier = Modifier.width(6.dp))
                GlassPillBadge(
                  text = day.severity.label.uppercase(),
                  tintColor = if (isCritical) SignalCrimsonContainer else SafetyAmberContainer,
                  textColor = if (isCritical) SignalCrimsonOnContainer else SafetyAmberOnContainer,
                  borderColor = if (isCritical) SignalCrimson else SafetyAmber
                )
              }

              Text(
                text = day.summaryReason,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                color = if (isCritical) SignalCrimsonOnContainer else SafetyAmberOnContainer
              )

              FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
              ) {
                GlassPillBadge(
                  text = "FREE ${day.availableProjected}/${day.minRequiredTotal} MIN",
                  tintColor = GlassSurfaceBright,
                  textColor = MaterialTheme.colorScheme.onSurface,
                  borderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                )
                GlassPillBadge(
                  text = "PM ${day.availablePointsmen}/${day.requiredPointsmen}",
                  tintColor = GlassSurfaceBright,
                  textColor = if (day.availablePointsmen < day.requiredPointsmen) SignalCrimson else MaterialTheme.colorScheme.onSurface,
                  borderColor = if (day.availablePointsmen < day.requiredPointsmen) SignalCrimson else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                )
                GlassPillBadge(
                  text = "STN ${day.availableStationQualified}/${day.requiredStationQualified}",
                  tintColor = GlassSurfaceBright,
                  textColor = if (day.availableStationQualified < day.requiredStationQualified) SignalCrimson else MaterialTheme.colorScheme.onSurface,
                  borderColor = if (day.availableStationQualified < day.requiredStationQualified) SignalCrimson else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                )
                GlassPillBadge(
                  text = "REST ${day.restStaff.size}",
                  tintColor = DepotTealContainer.copy(alpha = 0.55f),
                  textColor = DepotTealOnContainer,
                  borderColor = DepotTeal.copy(alpha = 0.55f)
                )
              }

              if (day.approvedLeaves.isNotEmpty()) {
                Text(
                  text = "Approved Leave (${day.approvedLeaves.size}): ${day.approvedLeaves.joinToString(", ") { "${it.staffName} (${it.leaveTypeEnum.code})" }}",
                  style = MaterialTheme.typography.labelSmall,
                  color = SignalCrimsonOnContainer,
                  maxLines = 2,
                  overflow = TextOverflow.Ellipsis
                )
              }
              if (day.pendingLeaves.isNotEmpty()) {
                Text(
                  text = "Pending Leave (${day.pendingLeaves.size}): ${day.pendingLeaves.joinToString(", ") { "${it.staffName} (${it.leaveTypeEnum.code})" }}",
                  style = MaterialTheme.typography.labelSmall,
                  color = SafetyAmberOnContainer,
                  maxLines = 2,
                  overflow = TextOverflow.Ellipsis
                )
              }
            }
          }
        }

        if (forecasts.size > 6) {
          TextButton(
            onClick = { showAll = !showAll },
            modifier = Modifier
              .align(Alignment.End)
              .heightIn(min = 48.dp)
              .testTag("tight_staff_toggle_all")
          ) {
            Text(if (showAll) "Show Less" else "Show All ${forecasts.size} Tight Dates")
          }
        }
      }

      OutlinedButton(
        onClick = onOpenLeaveCalendar,
        modifier = Modifier
          .fillMaxWidth()
          .heightIn(min = 48.dp)
          .testTag("dashboard_open_leave_calendar_btn")
      ) {
        Icon(Icons.Default.CalendarMonth, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(6.dp))
        Text("Open Monthly Leave Calendar")
      }
    }
  }
}

@Composable
fun ActivePermissionsSummaryCard(
  todayPermissions: List<DutyPermissionRecord>,
  monthTotalHours: Double,
  onOpenPermissionManager: () -> Unit
) {
  GlassTile(
    shape = RoundedCornerShape(24.dp),
    topColor = GlassSurfaceBright,
    bottomColor = GlassSurfaceMid,
    tintColor = DepotTealContainer.copy(alpha = 0.22f),
    modifier = Modifier
      .fillMaxWidth()
      .testTag("dashboard_permissions_summary_card")
  ) {
    Column(
      modifier = Modifier.padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(10.dp),
          modifier = Modifier.weight(1f)
        ) {
          GlassIconOrb(
            shape = RoundedCornerShape(12.dp),
            tintColor = DepotTeal.copy(alpha = 0.16f),
            modifier = Modifier.size(38.dp)
          ) {
            Icon(Icons.Default.Schedule, contentDescription = null, tint = DepotTeal, modifier = Modifier.size(20.dp))
          }
          Column {
            Text(
              text = "Duty Relief Permissions",
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold
            )
            Text(
              text = "Month total: ${SupervisorAnalyticsEngine.formatHours(monthTotalHours)} · Supervisor & Admin only",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
        }
        GlassPillBadge(
          text = "${todayPermissions.size} TODAY",
          tintColor = DepotTealContainer.copy(alpha = 0.8f),
          textColor = DepotTealOnContainer,
          borderColor = DepotTeal
        )
      }

      if (todayPermissions.isEmpty()) {
        Text(
          text = "No staff relieved on hourly permission for the selected date.",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      } else {
        todayPermissions.take(5).forEach { perm ->
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = "${perm.staffName} (${perm.shiftTypeEnum.code})",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2
              )
              Text(
                text = perm.reliefTypeEnum.label + if (perm.reason.isNotBlank()) " · ${perm.reason}" else "",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2
              )
            }
            Spacer(modifier = Modifier.width(8.dp))
            GlassPillBadge(
              text = SupervisorAnalyticsEngine.formatHours(perm.hours),
              tintColor = DepotTealContainer,
              textColor = DepotTealOnContainer,
              borderColor = DepotTeal
            )
          }
          HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.18f))
        }
      }

      OutlinedButton(
        onClick = onOpenPermissionManager,
        modifier = Modifier
          .fillMaxWidth()
          .heightIn(min = 48.dp)
          .testTag("dashboard_open_permission_manager_btn")
      ) {
        Icon(Icons.Default.Schedule, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(6.dp))
        Text("Open Permission Manager")
      }
    }
  }
}

@Composable
fun PendingApprovalsBannerCard(
  pendingLeavesCount: Int,
  pendingDraftsCount: Int,
  pendingLapUpdatesCount: Int,
  onOpenLeaveTab: () -> Unit,
  onOpenShiftsTab: () -> Unit
) {
  val total = pendingLeavesCount + pendingDraftsCount + pendingLapUpdatesCount
  if (total == 0) return

  GlassTile(
    shape = RoundedCornerShape(22.dp),
    topColor = GlassSurfaceBright,
    bottomColor = GlassSurfaceMid,
    tintColor = SafetyAmberContainer.copy(alpha = 0.35f),
    modifier = Modifier
      .fillMaxWidth()
      .testTag("dashboard_pending_approvals_card")
  ) {
    Column(
      modifier = Modifier.padding(14.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        Icon(Icons.Default.NotificationsActive, contentDescription = null, tint = SafetyAmber)
        Text(
          text = "Pending Supervisor Approvals ($total)",
          style = MaterialTheme.typography.titleSmall,
          fontWeight = FontWeight.Bold,
          color = SafetyAmberOnContainer
        )
      }
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        if (pendingLeavesCount > 0 || pendingLapUpdatesCount > 0) {
          OutlinedButton(
            onClick = onOpenLeaveTab,
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
            modifier = Modifier
              .weight(1f)
              .heightIn(min = 48.dp)
              .testTag("pending_approvals_open_leaves")
          ) {
            Icon(Icons.Default.EventBusy, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text(
              text = "Leaves ($pendingLeavesCount) · Balance ($pendingLapUpdatesCount)",
              maxLines = 2,
              textAlign = TextAlign.Center
            )
          }
        }
        if (pendingDraftsCount > 0) {
          OutlinedButton(
            onClick = onOpenShiftsTab,
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
            modifier = Modifier
              .weight(1f)
              .heightIn(min = 48.dp)
              .testTag("pending_approvals_open_drafts")
          ) {
            Text(
              text = "Duty Drafts ($pendingDraftsCount)",
              maxLines = 2,
              textAlign = TextAlign.Center
            )
          }
        }
      }
    }
  }
}
