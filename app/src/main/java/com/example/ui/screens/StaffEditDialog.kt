package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.model.Competency
import com.example.model.Designation
import com.example.model.StaffMember
import com.example.model.WeeklyRestDay
import com.example.ui.theme.DepotTeal
import com.example.ui.theme.DepotTealContainer
import com.example.ui.theme.DepotTealOnContainer
import com.example.ui.theme.GlassPopupSurface
import com.example.ui.theme.GlassRimHighlight
import com.example.ui.theme.GlassSurfaceBright
import com.example.ui.theme.GlassSurfaceSoft
import com.example.ui.theme.SafetyAmber
import com.example.ui.theme.SafetyAmberContainer
import com.example.ui.theme.SafetyAmberOnContainer
import com.example.ui.theme.SignalCrimson
import com.example.ui.theme.liquidGlassAccent
import com.example.ui.theme.liquidGlassSurface
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun StaffEditDialog(
  initialMember: StaffMember?,
  nextSuggestedSerialNo: Long,
  isBusy: Boolean,
  onDismiss: () -> Unit,
  onSave: (
    serialNo: Long,
    name: String,
    designation: Designation,
    competency: Competency,
    stationDuty: Boolean,
    shedDuty: Boolean,
    weeklyRestDay: WeeklyRestDay,
    trainingDueDate: String,
    trainingCourse: String,
    phoneNumber: String,
    secondaryPhoneNumber: String
  ) -> Unit
) {
  val isEditing = initialMember != null
  var serialText by remember(initialMember) {
    mutableStateOf((initialMember?.serialNo ?: nextSuggestedSerialNo).toString())
  }
  var nameText by remember(initialMember) {
    mutableStateOf(initialMember?.name ?: "")
  }
  var designation by remember(initialMember) {
    mutableStateOf(initialMember?.designationEnum ?: Designation.KHP)
  }
  var competency by remember(initialMember) {
    mutableStateOf(initialMember?.competencyEnum ?: Competency.NONE)
  }
  var stationDuty by remember(initialMember) {
    mutableStateOf(initialMember?.stationDuty ?: false)
  }
  var shedDuty by remember(initialMember) {
    mutableStateOf(initialMember?.shedDuty ?: true)
  }
  var weeklyRestDay by remember(initialMember) {
    mutableStateOf(initialMember?.restDayEnum ?: WeeklyRestDay.NONE)
  }
  var trainingDueDate by remember(initialMember) {
    mutableStateOf(initialMember?.trainingDueDate?.trim().orEmpty())
  }
  var trainingCourse by remember(initialMember) {
    mutableStateOf(initialMember?.trainingCourse?.trim().orEmpty())
  }
  var phoneNumber by remember(initialMember) {
    mutableStateOf(initialMember?.phoneNumber?.trim().orEmpty())
  }
  var secondaryPhoneNumber by remember(initialMember) {
    mutableStateOf(initialMember?.secondaryPhoneNumber?.trim().orEmpty())
  }
  var showTrainingDatePicker by remember { mutableStateOf(false) }

  val parsedSerial = serialText.trim().toLongOrNull()
  val isValid = nameText.trim().isNotBlank() && parsedSerial != null && parsedSerial in 1L..9999L

  AlertDialog(
    onDismissRequest = onDismiss,
    containerColor = GlassPopupSurface,
    title = {
      Text(
        text = if (isEditing) "Edit Staff Profile" else "Add Depot Staff Member",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold
      )
    },
    text = {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          OutlinedTextField(
            value = serialText,
            onValueChange = { input ->
              if (input.length <= 4 && input.all { it.isDigit() }) {
                serialText = input
              }
            },
            label = { Text("S.NO") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
              unfocusedContainerColor = GlassSurfaceSoft,
              focusedContainerColor = GlassSurfaceBright
            ),
            modifier = Modifier
              .weight(0.32f)
              .testTag("staff_edit_serial_input")
          )

          OutlinedTextField(
            value = nameText,
            onValueChange = { if (it.length <= 100) nameText = it },
            label = { Text("Staff Name") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
              unfocusedContainerColor = GlassSurfaceSoft,
              focusedContainerColor = GlassSurfaceBright
            ),
            modifier = Modifier
              .weight(0.68f)
              .testTag("staff_edit_name_input")
          )
        }

        Text("Designation", style = MaterialTheme.typography.labelLarge)
        FlowRow(
          horizontalArrangement = Arrangement.spacedBy(6.dp),
          verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
          Designation.entries.forEach { d ->
            val selected = designation == d
            StaffDialogChip(
              label = d.label,
              selected = selected,
              onClick = {
                designation = d
                if (d == Designation.SSE || d == Designation.JE || d == Designation.SHUNTER) {
                  stationDuty = false
                  shedDuty = false
                  competency = Competency.NONE
                } else if (!stationDuty && !shedDuty) {
                  shedDuty = true
                }
              },
              testTag = "staff_edit_desig_${d.name}"
            )
          }
        }

        if (designation == Designation.SHUNTER) {
          Surface(
            color = DepotTealContainer.copy(alpha = 0.45f),
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, DepotTeal.copy(alpha = 0.55f)),
            modifier = Modifier
              .fillMaxWidth()
              .testTag("staff_edit_shunter_only_notice")
          ) {
            Column(
              modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
              verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
              Text(
                text = "Shunter Duty Only",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = DepotTealOnContainer
              )
              Text(
                text = "Assigned exclusively to Shunter slots in Shifts (not Shed or Station).",
                style = MaterialTheme.typography.bodySmall,
                color = DepotTealOnContainer
              )
            }
          }
        } else {
          Text("Competency", style = MaterialTheme.typography.labelLarge)
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Competency.entries.forEach { c ->
              StaffDialogChip(
                label = c.label,
                selected = competency == c,
                onClick = { competency = c },
                modifier = Modifier.weight(1f),
                testTag = "staff_edit_comp_${c.name}"
              )
            }
          }

          Surface(
            color = GlassSurfaceSoft,
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
          ) {
            Column(
              modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
              verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
              ) {
                Column(modifier = Modifier.weight(1f)) {
                  Text("Station Duty (SC / UR / MED)", style = MaterialTheme.typography.bodyMedium)
                  Text(
                    "Eligible for Station & Night MED assignments",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                  )
                }
                Switch(
                  checked = stationDuty,
                  onCheckedChange = { stationDuty = it },
                  modifier = Modifier.testTag("staff_edit_station_switch")
                )
              }

              Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
              ) {
                Column(modifier = Modifier.weight(1f)) {
                  Text("Shed Duty", style = MaterialTheme.typography.bodyMedium)
                  Text(
                    "Eligible for Shed & General (08/17) duties",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                  )
                }
                Switch(
                  checked = shedDuty,
                  onCheckedChange = { shedDuty = it },
                  modifier = Modifier.testTag("staff_edit_shed_switch")
                )
              }
            }
          }
        }

        Text("Phone Numbers (Optional · All Staff)", style = MaterialTheme.typography.labelLarge)
        OutlinedTextField(
          value = phoneNumber,
          onValueChange = { input ->
            val filtered = input.filter { it.isDigit() || it == '+' || it == '-' || it == ' ' }
            if (filtered.length <= 20) {
              phoneNumber = filtered
            }
          },
          label = { Text("Phone Number 1 (Primary)") },
          placeholder = { Text("e.g. 9876543210") },
          leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null) },
          singleLine = true,
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
          shape = RoundedCornerShape(14.dp),
          colors = OutlinedTextFieldDefaults.colors(
            unfocusedContainerColor = GlassSurfaceSoft,
            focusedContainerColor = GlassSurfaceBright
          ),
          modifier = Modifier
            .fillMaxWidth()
            .testTag("staff_edit_phone_input")
        )

        OutlinedTextField(
          value = secondaryPhoneNumber,
          onValueChange = { input ->
            val filtered = input.filter { it.isDigit() || it == '+' || it == '-' || it == ' ' }
            if (filtered.length <= 20) {
              secondaryPhoneNumber = filtered
            }
          },
          label = { Text("Phone Number 2 (Alternate)") },
          placeholder = { Text("e.g. 9123456789") },
          leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null) },
          supportingText = { Text("Optional — enables one-tap Call & Message from Dashboard and Staff tab") },
          singleLine = true,
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
          shape = RoundedCornerShape(14.dp),
          colors = OutlinedTextFieldDefaults.colors(
            unfocusedContainerColor = GlassSurfaceSoft,
            focusedContainerColor = GlassSurfaceBright
          ),
          modifier = Modifier
            .fillMaxWidth()
            .testTag("staff_edit_phone2_input")
        )

        if (!stationDuty && !shedDuty) {
          Surface(
            color = SafetyAmberContainer,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
          ) {
            Row(
              modifier = Modifier.padding(10.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
              Icon(Icons.Default.Warning, contentDescription = null, tint = SafetyAmber)
              Text(
                text = "Station = NO & Shed = NO marks this member as Supervisory (hidden from shift duty lists).",
                style = MaterialTheme.typography.bodySmall,
                color = SafetyAmberOnContainer
              )
            }
          }
        }

        Text("Weekly Rest Day", style = MaterialTheme.typography.labelLarge)
        FlowRow(
          horizontalArrangement = Arrangement.spacedBy(6.dp),
          verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
          WeeklyRestDay.entries.forEach { day ->
            StaffDialogChip(
              label = if (day == WeeklyRestDay.NONE) "Not Set" else day.label.take(3),
              selected = weeklyRestDay == day,
              onClick = { weeklyRestDay = day },
              testTag = "staff_edit_rest_${day.name}"
            )
          }
        }

        Text("Technical Training Due (optional)", style = MaterialTheme.typography.labelLarge)
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          Surface(
            color = GlassSurfaceSoft,
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, if (trainingDueDate.isNotBlank()) DepotTeal else GlassRimHighlight),
            modifier = Modifier
              .weight(1f)
              .heightIn(min = 48.dp)
              .clickable { showTrainingDatePicker = true }
              .testTag("staff_edit_training_due_picker")
          ) {
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.SpaceBetween
            ) {
              Text(
                text = trainingDueDate.ifBlank { "Tap to set training due date" },
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (trainingDueDate.isNotBlank()) FontWeight.Bold else FontWeight.Normal,
                color = if (trainingDueDate.isNotBlank()) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
              )
              Icon(Icons.Default.CalendarMonth, contentDescription = "Pick training due date", tint = DepotTeal)
            }
          }
          if (trainingDueDate.isNotBlank()) {
            IconButton(
              onClick = {
                trainingDueDate = ""
                trainingCourse = ""
              },
              modifier = Modifier
                .heightIn(min = 48.dp)
                .testTag("staff_edit_training_due_clear")
            ) {
              Icon(Icons.Default.Clear, contentDescription = "Clear training due date", tint = SignalCrimson)
            }
          }
        }

        if (trainingDueDate.isNotBlank()) {
          OutlinedTextField(
            value = trainingCourse,
            onValueChange = { if (it.length <= 100) trainingCourse = it },
            label = { Text("Training Course (optional)") },
            placeholder = { Text("e.g., Technical Refresher / PME") },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
              unfocusedContainerColor = GlassSurfaceSoft,
              focusedContainerColor = GlassSurfaceBright
            ),
            modifier = Modifier
              .fillMaxWidth()
              .testTag("staff_edit_training_course_input")
          )
        }
      }
    },
    confirmButton = {
      Button(
        onClick = {
          val sNo = parsedSerial ?: return@Button
          val isShunterDesig = designation == Designation.SHUNTER
          onSave(
            sNo,
            nameText.trim(),
            designation,
            if (isShunterDesig) Competency.NONE else competency,
            if (isShunterDesig) false else stationDuty,
            if (isShunterDesig) false else shedDuty,
            weeklyRestDay,
            trainingDueDate.trim(),
            if (trainingDueDate.isBlank()) "" else trainingCourse.trim(),
            phoneNumber.trim(),
            secondaryPhoneNumber.trim()
          )
        },
        enabled = isValid && !isBusy,
        elevation = ButtonDefaults.buttonElevation(0.dp, 0.dp, 0.dp, 0.dp, 0.dp),
        colors = ButtonDefaults.buttonColors(
          containerColor = if (isValid && !isBusy) Color.Transparent else MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
          contentColor = Color.White
        ),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
          .heightIn(min = 48.dp)
          .let { mod ->
            if (isValid && !isBusy) mod.liquidGlassAccent(shape = RoundedCornerShape(14.dp)) else mod
          }
          .testTag("staff_edit_save_button")
      ) {
        Text(if (isEditing) "Update Staff" else "Add Staff")
      }
    },
    dismissButton = {
      TextButton(
        onClick = onDismiss,
        modifier = Modifier
          .heightIn(min = 48.dp)
          .testTag("staff_edit_cancel_button")
      ) {
        Text("Cancel")
      }
    }
  )

  if (showTrainingDatePicker) {
    val initialLocal = runCatching { LocalDate.parse(trainingDueDate) }.getOrNull() ?: LocalDate.now().plusDays(30)
    val initMillis = remember(initialLocal) {
      initialLocal.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    }
    val datePickerState = rememberDatePickerState(initialSelectedDateMillis = initMillis)
    DatePickerDialog(
      onDismissRequest = { showTrainingDatePicker = false },
      confirmButton = {
        TextButton(
          onClick = {
            val millis = datePickerState.selectedDateMillis ?: initMillis
            val picked = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
            trainingDueDate = picked.toString()
            showTrainingDatePicker = false
          }
        ) { Text("Apply") }
      },
      dismissButton = {
        TextButton(onClick = { showTrainingDatePicker = false }) { Text("Cancel") }
      }
    ) {
      DatePicker(state = datePickerState)
    }
  }
}

@Composable
private fun StaffDialogChip(
  label: String,
  selected: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  testTag: String
) {
  Box(
    modifier = modifier
      .let {
        if (selected) {
          it.liquidGlassAccent(shape = RoundedCornerShape(50))
        } else {
          it.liquidGlassSurface(
            shape = RoundedCornerShape(50),
            topColor = GlassSurfaceBright,
            bottomColor = GlassSurfaceSoft,
            borderWidth = 1.dp
          )
        }
      }
      .heightIn(min = 40.dp)
      .clickable(onClick = onClick)
      .padding(horizontal = 12.dp, vertical = 8.dp)
      .testTag(testTag),
    contentAlignment = Alignment.Center
  ) {
    Text(
      text = label,
      style = MaterialTheme.typography.labelMedium,
      fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
      color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface
    )
  }
}
