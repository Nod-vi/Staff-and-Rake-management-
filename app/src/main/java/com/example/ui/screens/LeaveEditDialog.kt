package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.example.model.LeaveRecord
import com.example.model.LeaveStatus
import com.example.model.LeaveType
import com.example.model.ShiftSlotAssignment
import com.example.ui.theme.DepotTeal
import com.example.ui.theme.GlassPopupSurface
import com.example.ui.theme.GlassRimHighlight
import com.example.ui.theme.GlassSurfaceBright
import com.example.ui.theme.GlassSurfaceSoft
import com.example.ui.theme.SafetyAmber
import com.example.ui.theme.SafetyAmberContainer
import com.example.ui.theme.SafetyAmberOnContainer
import com.example.ui.theme.SignalCrimson
import com.example.ui.theme.SignalCrimsonContainer
import com.example.ui.theme.SignalCrimsonOnContainer
import com.example.ui.theme.SignalEmerald
import com.example.ui.theme.liquidGlassAccent
import com.example.ui.theme.liquidGlassSurface
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

private val COMPACT_RANGE_DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("dd MMM yyyy")
private val COMPACT_RANGE_SHORT_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("dd MMM")

private enum class EditLeaveDateTarget {
  START_DATE,
  END_DATE,
  DATE_RANGE
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun LeaveEditDialog(
  leave: LeaveRecord,
  existingLeaves: List<LeaveRecord>,
  assignments: List<ShiftSlotAssignment>,
  isBusy: Boolean,
  onDismiss: () -> Unit,
  onSave: (
    leaveType: LeaveType,
    startDate: LocalDate,
    endDate: LocalDate,
    status: LeaveStatus,
    notes: String,
    autoClearConflictingShifts: Boolean
  ) -> Unit
) {
  var selectedStatus by remember(leave) { mutableStateOf(leave.statusEnum) }
  var selectedType by remember(leave) { mutableStateOf(leave.leaveTypeEnum) }
  var startDate by remember(leave) { mutableStateOf(LocalDate.parse(leave.startDate)) }
  var endDate by remember(leave) { mutableStateOf(LocalDate.parse(leave.endDate)) }
  var notes by remember(leave) { mutableStateOf(leave.notes) }
  var autoClearConflictingShifts by remember(leave) { mutableStateOf(true) }
  var dateTarget by remember { mutableStateOf<EditLeaveDateTarget?>(null) }

  val startStr = startDate.toString()
  val endStr = endDate.toString()
  val dayCount = remember(startDate, endDate) {
    ChronoUnit.DAYS.between(startDate, endDate).coerceAtLeast(0L) + 1L
  }

  val overlappingOtherLeave = remember(leave, existingLeaves, startStr, endStr, selectedStatus) {
    if (selectedStatus == LeaveStatus.REJECTED || selectedStatus == LeaveStatus.CANCELLED) {
      null
    } else {
      existingLeaves.firstOrNull { other ->
        other.id != leave.id &&
          other.staffId == leave.staffId &&
          other.statusEnum != LeaveStatus.REJECTED &&
          other.statusEnum != LeaveStatus.CANCELLED &&
          other.startDate <= endStr &&
          other.endDate >= startStr
      }
    }
  }

  val impactedAssignmentCount = remember(leave, assignments, startStr, endStr, selectedStatus) {
    if (!selectedStatus.blocksShiftDuty) 0
    else assignments.count { it.staffId == leave.staffId && it.dutyDateStr in startStr..endStr }
  }

  AlertDialog(
    onDismissRequest = onDismiss,
    containerColor = GlassPopupSurface,
    title = {
      Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
          text = "Update Leave & Status",
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold
        )
        Text(
          text = leave.staffName,
          style = MaterialTheme.typography.labelLarge,
          color = MaterialTheme.colorScheme.primary
        )
      }
    },
    text = {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
      ) {
        Text("Leave Status", style = MaterialTheme.typography.labelLarge)
        FlowRow(
          horizontalArrangement = Arrangement.spacedBy(6.dp),
          verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
          LeaveStatus.entries.forEach { st ->
            val selected = selectedStatus == st
            val accentColor = when (st) {
              LeaveStatus.APPROVED -> SignalEmerald
              LeaveStatus.PENDING -> SafetyAmber
              LeaveStatus.REJECTED, LeaveStatus.CANCELLED -> SignalCrimson
            }
            Box(
              modifier = (if (selected) {
                Modifier.liquidGlassAccent(
                  shape = RoundedCornerShape(50),
                  baseColor = accentColor,
                  highlightColor = accentColor.copy(alpha = 0.85f)
                )
              } else {
                Modifier.liquidGlassSurface(
                  shape = RoundedCornerShape(50),
                  topColor = GlassSurfaceBright,
                  bottomColor = GlassSurfaceSoft,
                  borderWidth = 1.dp
                )
              })
                .heightIn(min = 40.dp)
                .clickable { selectedStatus = st }
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .testTag("edit_leave_status_${st.name}"),
              contentAlignment = Alignment.Center
            ) {
              Text(
                text = st.label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface
              )
            }
          }
        }

        Text(
          text = if (selectedStatus.blocksShiftDuty) {
            "Approved status automatically excludes ${leave.staffName} from shift availability."
          } else {
            "${selectedStatus.label} status keeps ${leave.staffName} available for shift scheduling."
          },
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Text("Leave Type", style = MaterialTheme.typography.labelLarge)
        FlowRow(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(6.dp),
          verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
          LeaveType.entries.forEach { type ->
            val selected = selectedType == type
            val isTraining = type == LeaveType.TRAINING
            Box(
              modifier = (if (selected) {
                if (isTraining) {
                  Modifier.liquidGlassAccent(
                    shape = RoundedCornerShape(50),
                    baseColor = DepotTeal,
                    highlightColor = DepotTeal.copy(alpha = 0.85f)
                  )
                } else {
                  Modifier.liquidGlassAccent(shape = RoundedCornerShape(50))
                }
              } else {
                Modifier.liquidGlassSurface(
                  shape = RoundedCornerShape(50),
                  topColor = GlassSurfaceBright,
                  bottomColor = GlassSurfaceSoft,
                  borderWidth = 1.dp
                )
              })
                .heightIn(min = 42.dp)
                .clickable { selectedType = type }
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .testTag("edit_leave_type_${type.code}"),
              contentAlignment = Alignment.Center
            ) {
              Text(
                text = if (isTraining) "Training" else type.code,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface
              )
            }
          }
        }

        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          Text(
            text = "Leave Period ($dayCount ${if (dayCount == 1L) "day" else "days"})",
            style = MaterialTheme.typography.labelLarge,
            maxLines = 2,
            modifier = Modifier.weight(1f)
          )
          OutlinedButton(
            onClick = { dateTarget = EditLeaveDateTarget.DATE_RANGE },
            colors = ButtonDefaults.outlinedButtonColors(containerColor = GlassSurfaceSoft),
            border = BorderStroke(1.dp, GlassRimHighlight),
            shape = RoundedCornerShape(12.dp),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
            modifier = Modifier
              .heightIn(min = 44.dp)
              .testTag("edit_leave_pick_range_button")
          ) {
            Icon(Icons.Default.DateRange, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text(
              text = "Pick Range",
              style = MaterialTheme.typography.labelMedium,
              maxLines = 1,
              softWrap = false
            )
          }
        }

        Column(
          modifier = Modifier.fillMaxWidth(),
          verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          EditDateStepper(
            label = "Start Date",
            date = startDate,
            onPickDate = { dateTarget = EditLeaveDateTarget.START_DATE },
            onDec = {
              startDate = startDate.minusDays(1)
              if (endDate.isBefore(startDate)) endDate = startDate
            },
            onInc = {
              startDate = startDate.plusDays(1)
              if (endDate.isBefore(startDate)) endDate = startDate
            },
            modifier = Modifier.fillMaxWidth(),
            tagPrefix = "edit_leave_start"
          )

          EditDateStepper(
            label = "End Date",
            date = endDate,
            onPickDate = { dateTarget = EditLeaveDateTarget.END_DATE },
            onDec = {
              val next = endDate.minusDays(1)
              if (!next.isBefore(startDate)) endDate = next
            },
            onInc = { endDate = endDate.plusDays(1) },
            modifier = Modifier.fillMaxWidth(),
            tagPrefix = "edit_leave_end"
          )
        }

        if (overlappingOtherLeave != null) {
          Surface(
            color = SignalCrimsonContainer,
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth()
          ) {
            Row(
              modifier = Modifier.padding(10.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
              Icon(Icons.Default.Warning, contentDescription = null, tint = SignalCrimson)
              Text(
                text = "Overlaps another ${overlappingOtherLeave.leaveTypeEnum.code} (${overlappingOtherLeave.startDate} to ${overlappingOtherLeave.endDate}).",
                style = MaterialTheme.typography.bodySmall,
                color = SignalCrimsonOnContainer
              )
            }
          }
        } else if (impactedAssignmentCount > 0) {
          Surface(
            color = SafetyAmberContainer,
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth()
          ) {
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 6.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.SpaceBetween
            ) {
              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text = "$impactedAssignmentCount assigned shift(s) overlap",
                  style = MaterialTheme.typography.labelMedium,
                  color = SafetyAmberOnContainer
                )
                Text(
                  text = "Vacate overlapping roster slots",
                  style = MaterialTheme.typography.bodySmall,
                  color = SafetyAmberOnContainer
                )
              }
              Switch(
                checked = autoClearConflictingShifts,
                onCheckedChange = { autoClearConflictingShifts = it }
              )
            }
          }
        }

        OutlinedTextField(
          value = notes,
          onValueChange = { if (it.length <= 300) notes = it },
          label = { Text("Remarks / Reason") },
          singleLine = true,
          shape = RoundedCornerShape(14.dp),
          colors = OutlinedTextFieldDefaults.colors(
            unfocusedContainerColor = GlassSurfaceSoft,
            focusedContainerColor = GlassSurfaceBright
          ),
          modifier = Modifier
            .fillMaxWidth()
            .testTag("edit_leave_notes_input")
        )
      }
    },
    confirmButton = {
      val canSave = overlappingOtherLeave == null && !isBusy
      Button(
        onClick = {
          onSave(
            selectedType,
            startDate,
            endDate,
            selectedStatus,
            notes,
            autoClearConflictingShifts
          )
        },
        enabled = canSave,
        elevation = ButtonDefaults.buttonElevation(0.dp, 0.dp, 0.dp, 0.dp, 0.dp),
        colors = ButtonDefaults.buttonColors(
          containerColor = if (canSave) Color.Transparent else MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
          contentColor = Color.White
        ),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
          .heightIn(min = 48.dp)
          .let { mod -> if (canSave) mod.liquidGlassAccent(shape = RoundedCornerShape(14.dp)) else mod }
          .testTag("edit_leave_save_button")
      ) {
        Text("Save Changes")
      }
    },
    dismissButton = {
      TextButton(
        onClick = onDismiss,
        modifier = Modifier
          .heightIn(min = 48.dp)
          .testTag("edit_leave_cancel_button")
      ) {
        Text("Cancel")
      }
    }
  )

  when (dateTarget) {
    EditLeaveDateTarget.START_DATE -> {
      val initMillis = remember(startDate) {
        startDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
      }
      val state = rememberDatePickerState(initialSelectedDateMillis = initMillis)
      DatePickerDialog(
        onDismissRequest = { dateTarget = null },
        confirmButton = {
          TextButton(
            onClick = {
              val millis = state.selectedDateMillis ?: initMillis
              val picked = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
              startDate = picked
              if (endDate.isBefore(picked)) endDate = picked
              dateTarget = null
            }
          ) { Text("Apply") }
        },
        dismissButton = {
          TextButton(onClick = { dateTarget = null }) { Text("Cancel") }
        }
      ) {
        DatePicker(state = state)
      }
    }
    EditLeaveDateTarget.END_DATE -> {
      val initMillis = remember(endDate) {
        endDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
      }
      val state = rememberDatePickerState(initialSelectedDateMillis = initMillis)
      DatePickerDialog(
        onDismissRequest = { dateTarget = null },
        confirmButton = {
          TextButton(
            onClick = {
              val millis = state.selectedDateMillis ?: initMillis
              val picked = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
              if (picked.isBefore(startDate)) startDate = picked
              endDate = picked
              dateTarget = null
            }
          ) { Text("Apply") }
        },
        dismissButton = {
          TextButton(onClick = { dateTarget = null }) { Text("Cancel") }
        }
      ) {
        DatePicker(state = state)
      }
    }
    EditLeaveDateTarget.DATE_RANGE -> {
      CompactDateRangePickerDialog(
        initialStart = startDate,
        initialEnd = endDate,
        onDismiss = { dateTarget = null },
        onConfirm = { s, e ->
          startDate = s
          endDate = if (e.isBefore(s)) s else e
          dateTarget = null
        }
      )
    }
    null -> Unit
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompactDateRangePickerDialog(
  initialStart: LocalDate,
  initialEnd: LocalDate,
  onDismiss: () -> Unit,
  onConfirm: (LocalDate, LocalDate) -> Unit
) {
  val startMillis = remember(initialStart) {
    initialStart.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
  }
  val endMillis = remember(initialEnd) {
    initialEnd.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
  }
  val state = rememberDateRangePickerState(
    initialSelectedStartDateMillis = startMillis,
    initialSelectedEndDateMillis = endMillis
  )

  val selectedStart = state.selectedStartDateMillis?.let {
    Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()
  }
  val selectedEnd = state.selectedEndDateMillis?.let {
    Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()
  }
  val headlineText = remember(selectedStart, selectedEnd) {
    when {
      selectedStart != null && selectedEnd != null -> {
        val days = (ChronoUnit.DAYS.between(selectedStart, selectedEnd) + 1L).coerceAtLeast(1L)
        val startPart = if (selectedStart.year == selectedEnd.year) {
          selectedStart.format(COMPACT_RANGE_SHORT_FORMATTER)
        } else {
          selectedStart.format(COMPACT_RANGE_DATE_FORMATTER)
        }
        val endPart = selectedEnd.format(COMPACT_RANGE_DATE_FORMATTER)
        "$startPart – $endPart ($days ${if (days == 1L) "day" else "days"})"
      }
      selectedStart != null -> "${selectedStart.format(COMPACT_RANGE_DATE_FORMATTER)} – End date"
      else -> "Select start & end date"
    }
  }

  DatePickerDialog(
    onDismissRequest = onDismiss,
    properties = DialogProperties(usePlatformDefaultWidth = false),
    modifier = Modifier
      .fillMaxWidth()
      .padding(horizontal = 12.dp)
      .widthIn(max = 420.dp),
    confirmButton = {
      TextButton(
        onClick = {
          val s = Instant.ofEpochMilli(state.selectedStartDateMillis ?: startMillis)
            .atZone(ZoneOffset.UTC).toLocalDate()
          val e = Instant.ofEpochMilli(state.selectedEndDateMillis ?: state.selectedStartDateMillis ?: endMillis)
            .atZone(ZoneOffset.UTC).toLocalDate()
          onConfirm(s, if (e.isBefore(s)) s else e)
        },
        modifier = Modifier.heightIn(min = 48.dp)
      ) {
        Text("Apply Range", maxLines = 1, softWrap = false)
      }
    },
    dismissButton = {
      TextButton(
        onClick = onDismiss,
        modifier = Modifier.heightIn(min = 48.dp)
      ) {
        Text("Cancel", maxLines = 1, softWrap = false)
      }
    }
  ) {
    DateRangePicker(
      state = state,
      showModeToggle = false,
      title = {
        Text(
          text = "Select Leave Date Range",
          style = MaterialTheme.typography.labelLarge,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 4.dp)
        )
      },
      headline = {
        Text(
          text = headlineText,
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold,
          color = MaterialTheme.colorScheme.primary,
          maxLines = 2,
          modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, bottom = 10.dp)
        )
      },
      modifier = Modifier
        .fillMaxWidth()
        .height(480.dp)
    )
  }
}

@Composable
private fun EditDateStepper(
  label: String,
  date: LocalDate,
  onPickDate: () -> Unit,
  onDec: () -> Unit,
  onInc: () -> Unit,
  modifier: Modifier = Modifier,
  tagPrefix: String
) {
  Box(
    modifier = modifier.liquidGlassSurface(
      shape = RoundedCornerShape(14.dp),
      topColor = GlassSurfaceBright,
      bottomColor = GlassSurfaceSoft,
      borderWidth = 1.dp
    )
  ) {
    Column(modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp)) {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text(
          text = label,
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 1
        )
        Icon(
          imageVector = Icons.Default.CalendarMonth,
          contentDescription = null,
          tint = DepotTeal,
          modifier = Modifier.size(14.dp)
        )
      }
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
      ) {
        IconButton(
          onClick = onDec,
          modifier = Modifier
            .size(48.dp)
            .testTag("${tagPrefix}_dec")
        ) {
          Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous day")
        }
        Box(
          modifier = Modifier
            .weight(1f)
            .heightIn(min = 48.dp)
            .clickable(onClick = onPickDate)
            .testTag("${tagPrefix}_picker"),
          contentAlignment = Alignment.Center
        ) {
          Text(
            text = "${date} (${date.dayOfWeek.name.take(3)})",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis
          )
        }
        IconButton(
          onClick = onInc,
          modifier = Modifier
            .size(48.dp)
            .testTag("${tagPrefix}_inc")
        ) {
          Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next day")
        }
      }
    }
  }
}
