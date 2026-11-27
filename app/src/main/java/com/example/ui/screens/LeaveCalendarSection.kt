package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EventBusy
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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.LeaveCalendarDayInfo
import com.example.domain.MonthlyLeaveCalendarData
import com.example.domain.StaffTightnessLevel
import com.example.model.LeaveRecord
import com.example.model.StaffMember
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
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

private val MONTH_HEADER_FORMATTER = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH)
private val DAY_DETAIL_FORMATTER = DateTimeFormatter.ofPattern("EEE, dd MMM yyyy", Locale.ENGLISH)

fun LazyListScope.leaveCalendarItems(
  calendarData: MonthlyLeaveCalendarData,
  focusedDate: LocalDate,
  today: LocalDate,
  canApproveLeaves: Boolean,
  isBusy: Boolean,
  onPrevMonth: () -> Unit,
  onNextMonth: () -> Unit,
  onJumpToday: () -> Unit,
  onSelectCalendarDate: (LocalDate) -> Unit,
  onApprovePendingLeave: (LeaveRecord) -> Unit,
  onRejectPendingLeave: (LeaveRecord) -> Unit
) {
  item(key = "leave_cal_header", contentType = "cal_header") {
    LeaveCalendarMonthCard(
      calendarData = calendarData,
      focusedDate = focusedDate,
      today = today,
      onPrevMonth = onPrevMonth,
      onNextMonth = onNextMonth,
      onJumpToday = onJumpToday,
      onSelectDate = onSelectCalendarDate
    )
  }

  val selectedDayInfo = calendarData.days.firstOrNull { it.date == focusedDate }
    ?: calendarData.days.firstOrNull()
  if (selectedDayInfo != null) {
    item(key = "leave_cal_day_detail_${selectedDayInfo.date}", contentType = "cal_day_detail") {
      LeaveCalendarDayDetailCard(
        dayInfo = selectedDayInfo,
        canApproveLeaves = canApproveLeaves,
        isBusy = isBusy,
        onApprovePendingLeave = onApprovePendingLeave,
        onRejectPendingLeave = onRejectPendingLeave
      )
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LeaveCalendarMonthCard(
  calendarData: MonthlyLeaveCalendarData,
  focusedDate: LocalDate,
  today: LocalDate,
  onPrevMonth: () -> Unit,
  onNextMonth: () -> Unit,
  onJumpToday: () -> Unit,
  onSelectDate: (LocalDate) -> Unit
) {
  val yearMonth = calendarData.yearMonth
  val firstDayOfMonth = yearMonth.atDay(1)
  val leadingBlankCells = firstDayOfMonth.dayOfWeek.value - DayOfWeek.MONDAY.value
  val dayLabels = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

  GlassTile(
    shape = RoundedCornerShape(24.dp),
    topColor = GlassSurfaceBright,
    bottomColor = GlassSurfaceMid,
    modifier = Modifier
      .fillMaxWidth()
      .testTag("leave_calendar_month_card")
  ) {
    Column(
      modifier = Modifier.padding(14.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
      // Month Navigation Header
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          modifier = Modifier.weight(1f)
        ) {
          GlassIconOrb(
            shape = RoundedCornerShape(12.dp),
            tintColor = RailwayCobalt.copy(alpha = 0.14f),
            modifier = Modifier.size(38.dp)
          ) {
            Icon(
              imageVector = Icons.Default.CalendarMonth,
              contentDescription = null,
              tint = RailwayCobalt,
              modifier = Modifier.size(20.dp)
            )
          }
          Column {
            Text(
              text = yearMonth.format(MONTH_HEADER_FORMATTER),
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold,
              modifier = Modifier.testTag("leave_calendar_month_title")
            )
            Text(
              text = "${calendarData.uniqueStaffOnLeave} staff · ${calendarData.totalApprovedLeaveDays} approved · ${calendarData.totalPendingLeaveDays} pending days",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              maxLines = 2
            )
          }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
          if (yearMonth != YearMonth.from(today)) {
            TextButton(
              onClick = onJumpToday,
              modifier = Modifier
                .heightIn(min = 48.dp)
                .testTag("leave_calendar_today_btn")
            ) {
              Text("Today", style = MaterialTheme.typography.labelMedium)
            }
          }
          IconButton(
            onClick = onPrevMonth,
            modifier = Modifier
              .size(48.dp)
              .testTag("leave_calendar_prev_month")
          ) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous month")
          }
          IconButton(
            onClick = onNextMonth,
            modifier = Modifier
              .size(48.dp)
              .testTag("leave_calendar_next_month")
          ) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next month")
          }
        }
      }

      // Summary Pills
      FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
      ) {
        GlassPillBadge(
          text = "APPROVED: ${calendarData.totalApprovedLeaveDays}d",
          tintColor = SignalCrimsonContainer.copy(alpha = 0.65f),
          textColor = SignalCrimsonOnContainer,
          borderColor = SignalCrimson
        )
        if (calendarData.totalPendingLeaveDays > 0) {
          GlassPillBadge(
            text = "PENDING: ${calendarData.totalPendingLeaveDays}d",
            tintColor = SafetyAmberContainer.copy(alpha = 0.70f),
            textColor = SafetyAmberOnContainer,
            borderColor = SafetyAmber
          )
        }
        if (calendarData.criticalDaysCount > 0) {
          GlassPillBadge(
            text = "${calendarData.criticalDaysCount} CRITICAL DAYS",
            tintColor = SignalCrimsonContainer.copy(alpha = 0.85f),
            textColor = SignalCrimsonOnContainer,
            borderColor = SignalCrimson
          )
        }
        if (calendarData.tightDaysCount > 0) {
          GlassPillBadge(
            text = "${calendarData.tightDaysCount} TIGHT DAYS",
            tintColor = SafetyAmberContainer.copy(alpha = 0.75f),
            textColor = SafetyAmberOnContainer,
            borderColor = SafetyAmber
          )
        }
      }

      // Day-of-week header row
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
      ) {
        dayLabels.forEach { label ->
          Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = if (label == "Sun") SignalCrimson else MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f)
          )
        }
      }

      // Month Grid (6 rows max, 7 columns)
      val totalSlots = leadingBlankCells + calendarData.days.size
      val rowCount = (totalSlots + 6) / 7
      Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        for (row in 0 until rowCount) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
          ) {
            for (col in 0 until 7) {
              val cellIndex = row * 7 + col
              val dayIdx = cellIndex - leadingBlankCells
              if (dayIdx in calendarData.days.indices) {
                val info = calendarData.days[dayIdx]
                CalendarDayCell(
                  dayInfo = info,
                  isSelected = info.date == focusedDate,
                  isToday = info.date == today,
                  onClick = { onSelectDate(info.date) },
                  modifier = Modifier.weight(1f)
                )
              } else {
                Spacer(modifier = Modifier.weight(1f).height(52.dp))
              }
            }
          }
        }
      }

      // Compact Legend
      FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
      ) {
        CalendarLegendDot(color = SignalCrimson, label = "Approved Leave")
        CalendarLegendDot(color = SafetyAmber, label = "Pending Leave / Tight")
        CalendarLegendDot(color = RailwayCobalt, label = "Selected / Today")
      }
    }
  }
}

@Composable
private fun CalendarDayCell(
  dayInfo: LeaveCalendarDayInfo,
  isSelected: Boolean,
  isToday: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier
) {
  val approvedCount = dayInfo.approvedLeaves.size
  val pendingCount = dayInfo.pendingLeaves.size

  val containerColor = when {
    isSelected -> RailwayCobalt
    dayInfo.tightnessLevel == StaffTightnessLevel.CRITICAL -> SignalCrimsonContainer.copy(alpha = 0.65f)
    dayInfo.tightnessLevel == StaffTightnessLevel.TIGHT && (approvedCount + pendingCount > 0) ->
      SafetyAmberContainer.copy(alpha = 0.55f)
    approvedCount > 0 -> SignalCrimsonContainer.copy(alpha = 0.28f)
    pendingCount > 0 -> SafetyAmberContainer.copy(alpha = 0.30f)
    else -> GlassSurfaceSoft.copy(alpha = 0.65f)
  }

  val borderColor = when {
    isSelected -> RailwayCobalt
    isToday -> RailwayCobalt
    dayInfo.tightnessLevel == StaffTightnessLevel.CRITICAL -> SignalCrimson
    dayInfo.tightnessLevel == StaffTightnessLevel.TIGHT && (approvedCount + pendingCount > 0) -> SafetyAmber
    else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.22f)
  }

  val numberColor = when {
    isSelected -> Color.White
    dayInfo.date.dayOfWeek == DayOfWeek.SUNDAY -> SignalCrimson
    else -> MaterialTheme.colorScheme.onSurface
  }

  Box(
    modifier = modifier
      .heightIn(min = 52.dp)
      .clip(RoundedCornerShape(12.dp))
      .background(containerColor)
      .border(
        width = if (isSelected || isToday) 1.5.dp else 0.75.dp,
        color = borderColor,
        shape = RoundedCornerShape(12.dp)
      )
      .clickable(onClick = onClick)
      .padding(vertical = 4.dp, horizontal = 2.dp)
      .testTag("leave_cal_day_${dayInfo.date}"),
    contentAlignment = Alignment.TopCenter
  ) {
    Column(
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
      Text(
        text = dayInfo.date.dayOfMonth.toString(),
        style = MaterialTheme.typography.labelMedium,
        fontWeight = if (isSelected || isToday) FontWeight.Bold else FontWeight.Medium,
        color = numberColor
      )
      if (approvedCount > 0 || pendingCount > 0) {
        Row(
          horizontalArrangement = Arrangement.spacedBy(2.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          if (approvedCount > 0) {
            Box(
              modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(if (isSelected) Color.White.copy(alpha = 0.25f) else SignalCrimson)
                .padding(horizontal = 4.dp, vertical = 1.dp)
            ) {
              Text(
                text = "$approvedCount",
                fontSize = 9.sp,
                lineHeight = 10.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
              )
            }
          }
          if (pendingCount > 0) {
            Box(
              modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(if (isSelected) SafetyAmberContainer else SafetyAmber)
                .padding(horizontal = 4.dp, vertical = 1.dp)
            ) {
              Text(
                text = "${pendingCount}P",
                fontSize = 9.sp,
                lineHeight = 10.sp,
                fontWeight = FontWeight.Bold,
                color = if (isSelected) SafetyAmberOnContainer else Color.White
              )
            }
          }
        }
      } else {
        Spacer(modifier = Modifier.height(12.dp))
      }
    }
  }
}

@Composable
private fun CalendarLegendDot(color: Color, label: String) {
  Row(
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(5.dp)
  ) {
    Box(
      modifier = Modifier
        .size(9.dp)
        .clip(CircleShape)
        .background(color)
    )
    Text(
      text = label,
      style = MaterialTheme.typography.labelSmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant
    )
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LeaveCalendarDayDetailCard(
  dayInfo: LeaveCalendarDayInfo,
  canApproveLeaves: Boolean,
  isBusy: Boolean,
  onApprovePendingLeave: (LeaveRecord) -> Unit,
  onRejectPendingLeave: (LeaveRecord) -> Unit
) {
  val (statusBg, statusFg, statusBorder) = when (dayInfo.tightnessLevel) {
    StaffTightnessLevel.CRITICAL -> Triple(SignalCrimsonContainer, SignalCrimsonOnContainer, SignalCrimson)
    StaffTightnessLevel.TIGHT -> Triple(SafetyAmberContainer, SafetyAmberOnContainer, SafetyAmber)
    StaffTightnessLevel.NORMAL -> Triple(SignalEmeraldContainer, SignalEmeraldOnContainer, SignalEmerald)
  }

  GlassTile(
    shape = RoundedCornerShape(24.dp),
    topColor = GlassSurfaceBright,
    bottomColor = GlassSurfaceMid,
    modifier = Modifier
      .fillMaxWidth()
      .testTag("leave_calendar_day_detail_card")
  ) {
    Column(
      modifier = Modifier.padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = dayInfo.date.format(DAY_DETAIL_FORMATTER),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
          )
          Text(
            text = "Available: ${dayInfo.availableProjectedCount} / Min ${dayInfo.minRequiredTotal} · Rest: ${dayInfo.restStaff.size}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
        GlassPillBadge(
          text = dayInfo.tightnessLevel.label.uppercase(),
          tintColor = statusBg.copy(alpha = 0.8f),
          textColor = statusFg,
          borderColor = statusBorder
        )
      }

      if (dayInfo.approvedLeaves.isEmpty() && dayInfo.pendingLeaves.isEmpty()) {
        Surface(
          shape = RoundedCornerShape(14.dp),
          color = SignalEmeraldContainer.copy(alpha = 0.40f),
          modifier = Modifier.fillMaxWidth()
        ) {
          Text(
            text = "No staff on approved or pending leave on this date.",
            style = MaterialTheme.typography.bodySmall,
            color = SignalEmeraldOnContainer,
            modifier = Modifier.padding(12.dp)
          )
        }
      }

      if (dayInfo.approvedLeaves.isNotEmpty()) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
          ) {
            Icon(
              imageVector = Icons.Default.EventBusy,
              contentDescription = null,
              tint = SignalCrimson,
              modifier = Modifier.size(16.dp)
            )
            Text(
              text = "Approved Leave (${dayInfo.approvedLeaves.size})",
              style = MaterialTheme.typography.titleSmall,
              fontWeight = FontWeight.SemiBold,
              color = SignalCrimsonOnContainer
            )
          }
          dayInfo.approvedLeaves.forEach { leave ->
            CalendarLeaveEntryRow(
              leave = leave,
              isPending = false,
              canApproveLeaves = false,
              isBusy = isBusy,
              onApprove = {},
              onReject = {}
            )
          }
        }
      }

      if (dayInfo.pendingLeaves.isNotEmpty()) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
          ) {
            Icon(
              imageVector = Icons.Default.Warning,
              contentDescription = null,
              tint = SafetyAmber,
              modifier = Modifier.size(16.dp)
            )
            Text(
              text = "Pending Approval (${dayInfo.pendingLeaves.size})",
              style = MaterialTheme.typography.titleSmall,
              fontWeight = FontWeight.SemiBold,
              color = SafetyAmberOnContainer
            )
          }
          dayInfo.pendingLeaves.forEach { leave ->
            CalendarLeaveEntryRow(
              leave = leave,
              isPending = true,
              canApproveLeaves = canApproveLeaves,
              isBusy = isBusy,
              onApprove = { onApprovePendingLeave(leave) },
              onReject = { onRejectPendingLeave(leave) }
            )
          }
        }
      }

      if (dayInfo.restStaff.isNotEmpty()) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
          ) {
            Icon(
              imageVector = Icons.Default.Weekend,
              contentDescription = null,
              tint = DepotTeal,
              modifier = Modifier.size(16.dp)
            )
            Text(
              text = "Weekly Rest (${dayInfo.restStaff.size})",
              style = MaterialTheme.typography.titleSmall,
              fontWeight = FontWeight.SemiBold,
              color = DepotTealOnContainer
            )
          }
          FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
          ) {
            dayInfo.restStaff.forEach { staff: StaffMember ->
              GlassPillBadge(
                text = staff.name,
                tintColor = DepotTealContainer.copy(alpha = 0.55f),
                textColor = DepotTealOnContainer,
                borderColor = DepotTeal.copy(alpha = 0.6f)
              )
            }
          }
        }
      }
    }
  }
}

@Composable
private fun CalendarLeaveEntryRow(
  leave: LeaveRecord,
  isPending: Boolean,
  canApproveLeaves: Boolean,
  isBusy: Boolean,
  onApprove: () -> Unit,
  onReject: () -> Unit
) {
  val tint = if (isPending) SafetyAmberContainer.copy(alpha = 0.40f) else SignalCrimsonContainer.copy(alpha = 0.30f)
  val border = if (isPending) SafetyAmber.copy(alpha = 0.55f) else SignalCrimson.copy(alpha = 0.45f)

  Surface(
    shape = RoundedCornerShape(14.dp),
    color = tint,
    border = BorderStroke(1.dp, border),
    modifier = Modifier.fillMaxWidth()
  ) {
    Column(
      modifier = Modifier.padding(10.dp),
      verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = leave.staffName,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2
          )
          Text(
            text = "${leave.startDate} to ${leave.endDate}" +
              if (leave.notes.isNotBlank()) " · ${leave.notes}" else "",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
          )
        }
        Spacer(modifier = Modifier.width(8.dp))
        GlassPillBadge(
          text = leave.leaveTypeEnum.code,
          tintColor = if (isPending) SafetyAmberContainer else SignalCrimsonContainer,
          textColor = if (isPending) SafetyAmberOnContainer else SignalCrimsonOnContainer,
          borderColor = if (isPending) SafetyAmber else SignalCrimson
        )
      }

      if (isPending && canApproveLeaves) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          Button(
            onClick = onApprove,
            enabled = !isBusy,
            colors = ButtonDefaults.buttonColors(containerColor = SignalEmerald),
            modifier = Modifier
              .weight(1f)
              .heightIn(min = 48.dp)
              .testTag("cal_approve_leave_${leave.id}")
          ) {
            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("Approve")
          }
          OutlinedButton(
            onClick = onReject,
            enabled = !isBusy,
            colors = ButtonDefaults.outlinedButtonColors(contentColor = SignalCrimson),
            border = BorderStroke(1.dp, SignalCrimson),
            modifier = Modifier
              .weight(1f)
              .heightIn(min = 48.dp)
              .testTag("cal_reject_leave_${leave.id}")
          ) {
            Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("Reject")
          }
        }
      }
    }
  }
}
