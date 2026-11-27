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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EventBusy
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.School
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.domain.TrainingDueStatus
import com.example.domain.TrainingDueTechnicianItem
import com.example.model.LeaveRecord
import com.example.model.LeaveStatus
import com.example.model.StaffMember
import com.example.ui.theme.DepotTeal
import com.example.ui.theme.DepotTealContainer
import com.example.ui.theme.DepotTealOnContainer
import com.example.ui.theme.GlassIconOrb
import com.example.ui.theme.GlassPillBadge
import com.example.ui.theme.GlassPopupSurface
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
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit

private val TRAINING_COURSE_PRESETS = listOf(
  "Technical Refresher",
  "Safety / PME",
  "Traction / Rolling Stock",
  "Pointsman Refresher"
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TrainingDueTechniciansCard(
  items: List<TrainingDueTechnicianItem>,
  canManageTrainingDue: Boolean,
  isBusy: Boolean,
  onClickAssignNew: () -> Unit,
  onClickEditMember: (StaffMember) -> Unit,
  onClickRelieveForTraining: (StaffMember) -> Unit,
  modifier: Modifier = Modifier
) {
  val overdueCount = remember(items) { items.count { it.status == TrainingDueStatus.OVERDUE } }
  val dueSoonCount = remember(items) { items.count { it.status == TrainingDueStatus.DUE_SOON } }
  val scheduledCount = remember(items) { items.count { it.activeTrainingLeave != null } }

  val accentTint = when {
    overdueCount > 0 -> SignalCrimsonContainer.copy(alpha = 0.22f)
    dueSoonCount > 0 -> SafetyAmberContainer.copy(alpha = 0.22f)
    else -> DepotTealContainer.copy(alpha = 0.16f)
  }

  GlassTile(
    shape = RoundedCornerShape(24.dp),
    topColor = GlassSurfaceBright,
    bottomColor = GlassSurfaceMid,
    tintColor = accentTint,
    modifier = modifier
      .fillMaxWidth()
      .testTag("dashboard_training_due_card")
  ) {
    Column(
      modifier = Modifier.padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(10.dp),
          modifier = Modifier.weight(1f)
        ) {
          GlassIconOrb(
            shape = RoundedCornerShape(14.dp),
            tintColor = when {
              overdueCount > 0 -> SignalCrimsonContainer.copy(alpha = 0.7f)
              dueSoonCount > 0 -> SafetyAmberContainer.copy(alpha = 0.7f)
              else -> DepotTealContainer.copy(alpha = 0.7f)
            },
            modifier = Modifier.size(42.dp)
          ) {
            Icon(
              imageVector = Icons.Default.School,
              contentDescription = null,
              tint = when {
                overdueCount > 0 -> SignalCrimson
                dueSoonCount > 0 -> SafetyAmber
                else -> DepotTeal
              }
            )
          }
          Column {
            Text(
              text = "Training Due Technicians (${items.size})",
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold
            )
            Text(
              text = "Periodic technical training schedule & duty relief status",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
        }
      }

      if (items.isNotEmpty()) {
        FlowRow(
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
          if (overdueCount > 0) {
            GlassPillBadge(
              text = "$overdueCount OVERDUE",
              tintColor = SignalCrimsonContainer.copy(alpha = 0.8f),
              textColor = SignalCrimsonOnContainer,
              borderColor = SignalCrimson
            )
          }
          if (dueSoonCount > 0) {
            GlassPillBadge(
              text = "$dueSoonCount DUE ≤30D",
              tintColor = SafetyAmberContainer.copy(alpha = 0.8f),
              textColor = SafetyAmberOnContainer,
              borderColor = SafetyAmber
            )
          }
          GlassPillBadge(
            text = "$scheduledCount RELIEVED / SCHEDULED",
            tintColor = SignalEmeraldContainer.copy(alpha = 0.75f),
            textColor = SignalEmeraldOnContainer,
            borderColor = SignalEmerald
          )
        }
      }

      if (items.isEmpty()) {
        Surface(
          color = GlassSurfaceSoft,
          shape = RoundedCornerShape(14.dp),
          border = BorderStroke(1.dp, GlassRimHighlight),
          modifier = Modifier.fillMaxWidth()
        ) {
          Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
          ) {
            Icon(
              imageVector = Icons.Default.CheckCircle,
              contentDescription = null,
              tint = DepotTeal,
              modifier = Modifier.size(20.dp)
            )
            Text(
              text = "No technicians currently marked for periodic training due. Use 'Set Training Due' below or the Staff tab to schedule due dates.",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
        }
      } else {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          items.forEach { item ->
            TrainingDueRow(
              item = item,
              canManage = canManageTrainingDue,
              isBusy = isBusy,
              onEdit = { onClickEditMember(item.staff) },
              onRelieve = { onClickRelieveForTraining(item.staff) }
            )
          }
        }
      }

      if (canManageTrainingDue) {
        OutlinedButton(
          onClick = onClickAssignNew,
          enabled = !isBusy,
          colors = ButtonDefaults.outlinedButtonColors(containerColor = GlassSurfaceSoft),
          border = BorderStroke(1.dp, DepotTeal.copy(alpha = 0.65f)),
          shape = RoundedCornerShape(14.dp),
          modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .testTag("dashboard_set_training_due_button")
        ) {
          Icon(Icons.Default.Add, contentDescription = null, tint = DepotTeal, modifier = Modifier.size(18.dp))
          Spacer(modifier = Modifier.width(6.dp))
          Text(
            text = "Set Training Due for Technician",
            color = DepotTealOnContainer,
            fontWeight = FontWeight.SemiBold
          )
        }
      }
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TrainingDueRow(
  item: TrainingDueTechnicianItem,
  canManage: Boolean,
  isBusy: Boolean,
  onEdit: () -> Unit,
  onRelieve: () -> Unit
) {
  val (statusText, statusTint, statusFg, statusBorder) = when (item.status) {
    TrainingDueStatus.OVERDUE -> {
      val daysOverdue = -item.daysUntilDue
      Quad(
        "OVERDUE ${daysOverdue}D",
        SignalCrimsonContainer,
        SignalCrimsonOnContainer,
        SignalCrimson
      )
    }
    TrainingDueStatus.DUE_SOON -> {
      val label = if (item.daysUntilDue == 0L) "DUE TODAY" else "IN ${item.daysUntilDue}D"
      Quad(
        label,
        SafetyAmberContainer,
        SafetyAmberOnContainer,
        SafetyAmber
      )
    }
    TrainingDueStatus.UPCOMING -> Quad(
      "IN ${item.daysUntilDue}D",
      DepotTealContainer,
      DepotTealOnContainer,
      DepotTeal
    )
  }

  Surface(
    color = GlassSurfaceSoft,
    shape = RoundedCornerShape(16.dp),
    border = BorderStroke(1.dp, statusBorder.copy(alpha = 0.45f)),
    modifier = Modifier
      .fillMaxWidth()
      .testTag("training_due_row_${item.staff.id}")
  ) {
    Column(
      modifier = Modifier.padding(12.dp),
      verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
      ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
          Text(
            text = "#${item.staff.serialNo} ${item.staff.name}",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            maxLines = 2
          )
          Text(
            text = "${item.staff.designationEnum.label} · Due: ${item.dueDate}" +
              if (item.course.isNotBlank()) " · ${item.course}" else "",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
          )
        }
        Spacer(modifier = Modifier.width(6.dp))
        GlassPillBadge(
          text = statusText,
          tintColor = statusTint.copy(alpha = 0.78f),
          textColor = statusFg,
          borderColor = statusBorder
        )
      }

      val activeLeave = item.activeTrainingLeave
      if (activeLeave != null) {
        val isApproved = activeLeave.statusEnum == LeaveStatus.APPROVED
        GlassPillBadge(
          text = if (isApproved) {
            "TRAINING RELIEVED: ${activeLeave.startDate} → ${activeLeave.endDate}"
          } else {
            "TRAINING PENDING: ${activeLeave.startDate} → ${activeLeave.endDate}"
          },
          tintColor = if (isApproved) SignalEmeraldContainer.copy(alpha = 0.75f) else SafetyAmberContainer.copy(alpha = 0.75f),
          textColor = if (isApproved) SignalEmeraldOnContainer else SafetyAmberOnContainer,
          borderColor = if (isApproved) SignalEmerald else SafetyAmber
        )
      }

      if (canManage) {
        HorizontalDivider(color = GlassRimHighlight)
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          OutlinedButton(
            onClick = onEdit,
            enabled = !isBusy,
            colors = ButtonDefaults.outlinedButtonColors(containerColor = GlassSurfaceBright),
            border = BorderStroke(1.dp, GlassRimHighlight),
            shape = RoundedCornerShape(12.dp),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
            modifier = Modifier
              .weight(1f)
              .heightIn(min = 48.dp)
              .testTag("training_due_edit_${item.staff.id}")
          ) {
            Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Update Due", maxLines = 2, textAlign = TextAlign.Center)
          }

          Button(
            onClick = onRelieve,
            enabled = !isBusy,
            elevation = ButtonDefaults.buttonElevation(0.dp, 0.dp, 0.dp, 0.dp, 0.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent, contentColor = Color.White),
            shape = RoundedCornerShape(12.dp),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
            modifier = Modifier
              .weight(1.2f)
              .heightIn(min = 48.dp)
              .liquidGlassAccent(shape = RoundedCornerShape(12.dp))
              .testTag("training_due_relieve_${item.staff.id}")
          ) {
            Icon(Icons.Default.EventBusy, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(
              text = if (activeLeave != null) "View Training" else "Relieve (Training)",
              maxLines = 2,
              textAlign = TextAlign.Center
            )
          }
        }
      }
    }
  }
}

@Composable
fun TechnicianPersonalTrainingDueCard(
  staff: StaffMember,
  today: LocalDate,
  activeTrainingLeave: LeaveRecord?,
  onApplyTrainingLeave: () -> Unit,
  modifier: Modifier = Modifier
) {
  val parsedDue = remember(staff.trainingDueDate) {
    runCatching { LocalDate.parse(staff.trainingDueDate.trim()) }.getOrNull()
  } ?: return

  val daysUntil = remember(today, parsedDue) { ChronoUnit.DAYS.between(today, parsedDue) }
  val (badgeText, tintBg, tintFg, border) = when {
    daysUntil < 0L -> Quad("OVERDUE ${-daysUntil}D", SignalCrimsonContainer, SignalCrimsonOnContainer, SignalCrimson)
    daysUntil == 0L -> Quad("DUE TODAY", SignalCrimsonContainer, SignalCrimsonOnContainer, SignalCrimson)
    daysUntil <= 30L -> Quad("DUE IN ${daysUntil}D", SafetyAmberContainer, SafetyAmberOnContainer, SafetyAmber)
    else -> Quad("DUE IN ${daysUntil}D", DepotTealContainer, DepotTealOnContainer, DepotTeal)
  }

  GlassTile(
    shape = RoundedCornerShape(22.dp),
    topColor = GlassSurfaceBright,
    bottomColor = GlassSurfaceMid,
    tintColor = tintBg.copy(alpha = 0.28f),
    modifier = modifier
      .fillMaxWidth()
      .testTag("technician_training_due_card")
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
          horizontalArrangement = Arrangement.spacedBy(10.dp),
          modifier = Modifier.weight(1f)
        ) {
          GlassIconOrb(
            shape = RoundedCornerShape(12.dp),
            tintColor = tintBg.copy(alpha = 0.7f),
            modifier = Modifier.size(40.dp)
          ) {
            Icon(Icons.Default.School, contentDescription = null, tint = border)
          }
          Column {
            Text(
              text = "Technical Training Due",
              style = MaterialTheme.typography.titleSmall,
              fontWeight = FontWeight.Bold
            )
            Text(
              text = "Due Date: $parsedDue" +
                if (staff.trainingCourse.isNotBlank()) " · ${staff.trainingCourse}" else "",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
        }
        GlassPillBadge(
          text = badgeText,
          tintColor = tintBg.copy(alpha = 0.8f),
          textColor = tintFg,
          borderColor = border
        )
      }

      if (activeTrainingLeave != null) {
        val isApproved = activeTrainingLeave.statusEnum == LeaveStatus.APPROVED
        Text(
          text = if (isApproved) {
            "You are relieved for Training from ${activeTrainingLeave.startDate} to ${activeTrainingLeave.endDate}."
          } else {
            "Training leave request pending approval (${activeTrainingLeave.startDate} to ${activeTrainingLeave.endDate})."
          },
          style = MaterialTheme.typography.bodySmall,
          fontWeight = FontWeight.Medium,
          color = if (isApproved) SignalEmeraldOnContainer else SafetyAmberOnContainer
        )
      } else {
        OutlinedButton(
          onClick = onApplyTrainingLeave,
          colors = ButtonDefaults.outlinedButtonColors(containerColor = GlassSurfaceBright),
          border = BorderStroke(1.dp, border.copy(alpha = 0.7f)),
          shape = RoundedCornerShape(14.dp),
          modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .testTag("tech_apply_training_leave_button")
        ) {
          Icon(Icons.Default.School, contentDescription = null, tint = border, modifier = Modifier.size(18.dp))
          Spacer(modifier = Modifier.width(6.dp))
          Text("Apply for Training Relief", color = tintFg, fontWeight = FontWeight.SemiBold)
        }
      }
    }
  }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun SetTrainingDueDialog(
  fixedStaff: StaffMember?,
  selectableStaff: List<StaffMember> = emptyList(),
  isBusy: Boolean,
  onDismiss: () -> Unit,
  onSave: (staff: StaffMember, trainingDueDate: String, trainingCourse: String) -> Unit
) {
  val activeOptions = remember(selectableStaff) {
    selectableStaff.filter { !it.isSupervisory }.sortedBy { it.serialNo }
  }
  var selectedStaffId by remember(fixedStaff, activeOptions) {
    mutableStateOf(fixedStaff?.id ?: activeOptions.firstOrNull()?.id ?: "")
  }
  val activeStaff = remember(fixedStaff, activeOptions, selectedStaffId) {
    fixedStaff ?: activeOptions.firstOrNull { it.id == selectedStaffId } ?: activeOptions.firstOrNull()
  }

  var dueDateStr by remember(activeStaff?.id) {
    mutableStateOf(activeStaff?.trainingDueDate?.trim().orEmpty())
  }
  var courseText by remember(activeStaff?.id) {
    mutableStateOf(activeStaff?.trainingCourse?.trim().orEmpty())
  }
  var showStaffDropdown by remember { mutableStateOf(false) }
  var showDatePicker by remember { mutableStateOf(false) }

  val today = remember { LocalDate.now() }
  val parsedDueDate = remember(dueDateStr) {
    runCatching { LocalDate.parse(dueDateStr) }.getOrNull()
  }

  AlertDialog(
    onDismissRequest = onDismiss,
    containerColor = GlassPopupSurface,
    title = {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
      ) {
        Icon(Icons.Default.School, contentDescription = null, tint = DepotTeal)
        Text(
          text = "Set Technical Training Due",
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold
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
        if (fixedStaff != null) {
          Surface(
            color = GlassSurfaceSoft,
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, GlassRimHighlight),
            modifier = Modifier.fillMaxWidth()
          ) {
            Column(modifier = Modifier.padding(12.dp)) {
              Text(
                text = "Technician",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
              Text(
                text = "#${fixedStaff.serialNo} ${fixedStaff.name} (${fixedStaff.designationEnum.label})",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold
              )
            }
          }
        } else {
          Box(modifier = Modifier.fillMaxWidth()) {
            Surface(
              color = GlassSurfaceSoft,
              shape = RoundedCornerShape(14.dp),
              border = BorderStroke(1.dp, GlassRimHighlight),
              modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp)
                .clickable(enabled = activeOptions.isNotEmpty()) { showStaffDropdown = true }
                .testTag("training_due_staff_dropdown")
            ) {
              Row(
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
              ) {
                Column(modifier = Modifier.weight(1f)) {
                  Text(
                    text = "Select Technician",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                  )
                  Text(
                    text = activeStaff?.let {
                      "#${it.serialNo} ${it.name} (${it.designationEnum.label})"
                    } ?: "No active technicians available",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2
                  )
                }
                Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Select technician")
              }
            }

            DropdownMenu(
              expanded = showStaffDropdown,
              onDismissRequest = { showStaffDropdown = false },
              containerColor = GlassPopupSurface,
              modifier = Modifier.heightIn(max = 320.dp)
            ) {
              activeOptions.forEach { member ->
                DropdownMenuItem(
                  text = {
                    Column {
                      Text(
                        text = "#${member.serialNo} ${member.name} (${member.designationEnum.label})",
                        fontWeight = if (member.id == selectedStaffId) FontWeight.Bold else FontWeight.Normal
                      )
                      if (member.hasTrainingDue) {
                        Text(
                          text = "Currently due: ${member.trainingDueDate}",
                          style = MaterialTheme.typography.labelSmall,
                          color = DepotTeal
                        )
                      }
                    }
                  },
                  onClick = {
                    selectedStaffId = member.id
                    dueDateStr = member.trainingDueDate.trim()
                    courseText = member.trainingCourse.trim()
                    showStaffDropdown = false
                  }
                )
              }
            }
          }
        }

        // Training Due Date Picker Field
        Text("Training Due Date", style = MaterialTheme.typography.labelLarge)
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          Surface(
            color = GlassSurfaceSoft,
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, if (parsedDueDate != null) DepotTeal else GlassRimHighlight),
            modifier = Modifier
              .weight(1f)
              .heightIn(min = 48.dp)
              .clickable { showDatePicker = true }
              .testTag("training_due_date_picker_field")
          ) {
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.SpaceBetween
            ) {
              Text(
                text = dueDateStr.ifBlank { "Tap to pick due date" },
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (parsedDueDate != null) FontWeight.Bold else FontWeight.Normal,
                color = if (parsedDueDate != null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
              )
              Icon(Icons.Default.CalendarMonth, contentDescription = "Pick training due date", tint = DepotTeal)
            }
          }

          if (dueDateStr.isNotBlank()) {
            IconButton(
              onClick = {
                dueDateStr = ""
                courseText = ""
              },
              modifier = Modifier
                .size(48.dp)
                .testTag("training_due_clear_date_button")
            ) {
              Icon(Icons.Default.Clear, contentDescription = "Clear training due date", tint = SignalCrimson)
            }
          }
        }

        // Quick date presets
        FlowRow(
          horizontalArrangement = Arrangement.spacedBy(6.dp),
          verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
          listOf(
            "Today" to today,
            "+15d" to today.plusDays(15),
            "+30d" to today.plusDays(30),
            "+60d" to today.plusDays(60),
            "+90d" to today.plusDays(90)
          ).forEach { (label, presetDate) ->
            val selected = dueDateStr == presetDate.toString()
            Box(
              modifier = (if (selected) {
                Modifier.liquidGlassAccent(shape = RoundedCornerShape(50))
              } else {
                Modifier.liquidGlassSurface(
                  shape = RoundedCornerShape(50),
                  topColor = GlassSurfaceBright,
                  bottomColor = GlassSurfaceSoft,
                  borderWidth = 1.dp
                )
              })
                .heightIn(min = 40.dp)
                .clickable { dueDateStr = presetDate.toString() }
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .testTag("training_due_preset_${label.lowercase().replace("+", "plus_")}"),
              contentAlignment = Alignment.Center
            ) {
              Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface
              )
            }
          }
        }

        // Course label + presets
        Text("Training Course / Module (optional)", style = MaterialTheme.typography.labelLarge)
        FlowRow(
          horizontalArrangement = Arrangement.spacedBy(6.dp),
          verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
          TRAINING_COURSE_PRESETS.forEach { preset ->
            val selected = courseText.equals(preset, ignoreCase = true)
            Box(
              modifier = (if (selected) {
                Modifier.liquidGlassAccent(
                  shape = RoundedCornerShape(50),
                  baseColor = DepotTeal,
                  highlightColor = DepotTeal.copy(alpha = 0.85f)
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
                .clickable { courseText = preset }
                .padding(horizontal = 10.dp, vertical = 8.dp),
              contentAlignment = Alignment.Center
            ) {
              Text(
                text = preset,
                style = MaterialTheme.typography.labelSmall,
                color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface
              )
            }
          }
        }

        OutlinedTextField(
          value = courseText,
          onValueChange = { if (it.length <= 100) courseText = it },
          label = { Text("Course / Training Details") },
          placeholder = { Text("e.g., Technical Refresher / Safety PME") },
          singleLine = true,
          enabled = dueDateStr.isNotBlank(),
          shape = RoundedCornerShape(14.dp),
          colors = OutlinedTextFieldDefaults.colors(
            unfocusedContainerColor = GlassSurfaceSoft,
            focusedContainerColor = GlassSurfaceBright
          ),
          modifier = Modifier
            .fillMaxWidth()
            .testTag("training_due_course_input")
        )
      }
    },
    confirmButton = {
      val staff = activeStaff
      val canSave = staff != null && !isBusy && (dueDateStr.isBlank() || parsedDueDate != null)
      Button(
        onClick = {
          if (staff != null) {
            onSave(staff, dueDateStr.trim(), if (dueDateStr.isBlank()) "" else courseText.trim())
          }
        },
        enabled = canSave,
        elevation = ButtonDefaults.buttonElevation(0.dp, 0.dp, 0.dp, 0.dp, 0.dp),
        colors = ButtonDefaults.buttonColors(
          containerColor = if (canSave) Color.Transparent else RailwayCobalt.copy(alpha = 0.5f),
          contentColor = Color.White
        ),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
          .heightIn(min = 48.dp)
          .let { mod -> if (canSave) mod.liquidGlassAccent(shape = RoundedCornerShape(14.dp)) else mod }
          .testTag("training_due_save_button")
      ) {
        Text(if (dueDateStr.isBlank()) "Clear Due Date" else "Save Training Due")
      }
    },
    dismissButton = {
      TextButton(
        onClick = onDismiss,
        modifier = Modifier
          .heightIn(min = 48.dp)
          .testTag("training_due_cancel_button")
      ) {
        Text("Cancel")
      }
    }
  )

  if (showDatePicker) {
    val initialDate = parsedDueDate ?: today.plusDays(30)
    val initMillis = remember(initialDate) {
      initialDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    }
    val pickerState = rememberDatePickerState(initialSelectedDateMillis = initMillis)
    DatePickerDialog(
      onDismissRequest = { showDatePicker = false },
      confirmButton = {
        TextButton(
          onClick = {
            val millis = pickerState.selectedDateMillis ?: initMillis
            val picked = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
            dueDateStr = picked.toString()
            showDatePicker = false
          }
        ) { Text("Apply") }
      },
      dismissButton = {
        TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
      }
    ) {
      DatePicker(state = pickerState)
    }
  }
}

private data class Quad<A, B, C, D>(
  val first: A,
  val second: B,
  val third: C,
  val fourth: D
)
