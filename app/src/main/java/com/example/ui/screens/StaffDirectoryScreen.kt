package com.example.ui.screens

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.example.model.Competency
import com.example.model.Designation
import com.example.model.LeaveRecord
import com.example.model.StaffMember
import com.example.model.UserRole
import com.example.model.WeeklyRestDay
import com.example.ui.theme.DepotTeal
import com.example.ui.theme.DepotTealContainer
import com.example.ui.theme.DepotTealOnContainer
import com.example.ui.theme.DutySheetShunterBg
import com.example.ui.theme.DutySheetShunterInk
import com.example.ui.theme.GlassIconOrb
import com.example.ui.theme.GlassPillBadge
import com.example.ui.theme.GlassPopupSurface
import com.example.ui.theme.GlassRimHighlight
import com.example.ui.theme.GlassSurfaceBright
import com.example.ui.theme.GlassSurfaceMid
import com.example.ui.theme.GlassSurfaceSoft
import com.example.ui.theme.GlassTile
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
import java.time.temporal.ChronoUnit

private enum class StaffFilter(val label: String) {
  ALL("All"),
  TRAINING_DUE("Training Due"),
  SHED("Shed"),
  STATION("Station"),
  POINTSMAN("Pointsman"),
  SHUNTER("Shunter")
}

@Composable
fun StaffDirectoryScreen(
  selectedDate: LocalDate,
  staffList: List<StaffMember>,
  leaves: List<LeaveRecord>,
  userRole: UserRole = UserRole.SUPERVISOR,
  roleAccess: com.example.model.ResolvedRoleAccess = com.example.model.RoleAccessPolicy.resolve(userRole, emptyMap()),
  isBusy: Boolean = false,
  onAddStaffMember: (Long, String, Designation, Competency, Boolean, Boolean, WeeklyRestDay, String, String, String, String) -> Unit = { _, _, _, _, _, _, _, _, _, _, _ -> },
  onUpdateStaffMember: (StaffMember) -> Unit = {},
  onUpdateStaffTrainingDue: (String, String, String, String) -> Unit = { _, _, _, _ -> },
  onDeleteStaffMember: (String, String) -> Unit = { _, _ -> }
) {
  val phoneActions = rememberPhoneActionController()
  var searchQuery by remember { mutableStateOf("") }
  var selectedFilter by remember { mutableStateOf(StaffFilter.ALL) }
  var showAddDialog by remember { mutableStateOf(false) }
  var editingMember by remember { mutableStateOf<StaffMember?>(null) }
  var trainingDueMember by remember { mutableStateOf<StaffMember?>(null) }
  var pendingDeleteMember by remember { mutableStateOf<StaffMember?>(null) }
  var contactDialogMember by remember { mutableStateOf<StaffMember?>(null) }

  val canManageStaff = roleAccess.canManageStaff
  val canManageTrainingDue = roleAccess.canManageTrainingDue
  val today = remember { LocalDate.now() }
  val dateStr = selectedDate.toString()
  val dutyStaff = remember(staffList) { staffList.filter { !it.isSupervisory } }
  val trainingDueCount = remember(dutyStaff) { dutyStaff.count { it.hasTrainingDue } }
  val nextSerialNo = remember(staffList) {
    (staffList.maxOfOrNull { it.serialNo } ?: 38L) + 1L
  }
  val onLeaveIdsToday = remember(leaves, dateStr) {
    leaves.filter { it.isBlockingDate(dateStr) }.mapTo(HashSet()) { it.staffId }
  }

  val filteredStaff = remember(dutyStaff, searchQuery, selectedFilter) {
    dutyStaff.filter { member ->
      val matchesQuery = searchQuery.isBlank() ||
        member.name.contains(searchQuery, ignoreCase = true) ||
        member.designationEnum.label.contains(searchQuery, ignoreCase = true) ||
        member.trainingCourse.contains(searchQuery, ignoreCase = true) ||
        member.serialNo.toString() == searchQuery.trim()
      val matchesFilter = when (selectedFilter) {
        StaffFilter.ALL -> true
        StaffFilter.TRAINING_DUE -> member.hasTrainingDue
        StaffFilter.SHED -> member.shedDuty
        StaffFilter.STATION -> member.stationDuty
        StaffFilter.POINTSMAN -> member.isPointsman
        StaffFilter.SHUNTER -> member.isShunter
      }
      matchesQuery && matchesFilter
    }
  }

  Box(
    modifier = Modifier.fillMaxSize(),
    contentAlignment = Alignment.TopCenter
  ) {
    LazyColumn(
      modifier = Modifier
        .fillMaxSize()
        .widthIn(max = 780.dp),
      contentPadding = PaddingValues(16.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
      item(key = "staff_role_header", contentType = "role_banner") {
        GlassTile(
          shape = RoundedCornerShape(22.dp),
          topColor = GlassSurfaceBright,
          bottomColor = GlassSurfaceMid,
          tintColor = if (canManageStaff) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.30f)
          } else {
            Color.Transparent
          }
        ) {
          Column(
            modifier = Modifier
              .fillMaxWidth()
              .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
          ) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
              GlassIconOrb(
                shape = RoundedCornerShape(12.dp),
                tintColor = if (canManageStaff) SignalCrimsonContainer.copy(alpha = 0.55f) else DepotTealContainer.copy(alpha = 0.55f),
                modifier = Modifier.size(40.dp)
              ) {
                Icon(
                  imageVector = if (canManageStaff) Icons.Default.AdminPanelSettings else Icons.Default.Lock,
                  contentDescription = null,
                  tint = if (canManageStaff) SignalCrimson else DepotTeal
                )
              }
              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text = when {
                    canManageStaff -> "Staff Directory Management"
                    canManageTrainingDue -> "Depot Staff & Training Tracker"
                    else -> "Depot Staff Directory (Read-Only)"
                  },
                  style = MaterialTheme.typography.titleSmall,
                  fontWeight = FontWeight.Bold
                )
                Text(
                  text = when {
                    canManageStaff -> "Add/edit staff profiles or tap the graduation cap icon to set Training Due dates."
                    canManageTrainingDue -> "Tap the graduation cap icon on any technician to set or update periodic Training Due dates."
                    else -> "Read-only access to PPIO depot staff profiles and contact numbers."
                  },
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant
                )
              }
            }

            if (canManageStaff) {
              Button(
                onClick = { showAddDialog = true },
                enabled = !isBusy,
                elevation = ButtonDefaults.buttonElevation(0.dp, 0.dp, 0.dp, 0.dp, 0.dp),
                colors = ButtonDefaults.buttonColors(
                  containerColor = Color.Transparent,
                  contentColor = Color.White
                ),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                  .fillMaxWidth()
                  .heightIn(min = 48.dp)
                  .liquidGlassAccent(shape = RoundedCornerShape(14.dp))
                  .testTag("add_staff_button")
              ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Add Staff", fontWeight = FontWeight.SemiBold)
              }
            }
          }
        }
      }

        item(key = "staff_search_and_filters", contentType = "search_and_filters") {
          Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            GlassTile(
              shape = RoundedCornerShape(22.dp),
              topColor = GlassSurfaceBright,
              bottomColor = GlassSurfaceMid
            ) {
              OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search name, S.NO, or designation...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(22.dp),
                colors = OutlinedTextFieldDefaults.colors(
                  unfocusedContainerColor = Color.Transparent,
                  focusedContainerColor = Color.Transparent,
                  unfocusedBorderColor = Color.Transparent,
                  focusedBorderColor = Color.Transparent
                ),
                modifier = Modifier
                  .fillMaxWidth()
                  .testTag("staff_search_input")
              )
            }

            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              items(StaffFilter.entries) { filter ->
                val isSelected = selectedFilter == filter
                val labelText = when (filter) {
                  StaffFilter.ALL -> "All (${dutyStaff.size})"
                  StaffFilter.TRAINING_DUE -> "Training Due ($trainingDueCount)"
                  else -> filter.label
                }
                Box(
                  modifier = (if (isSelected) {
                    Modifier.liquidGlassAccent(shape = RoundedCornerShape(50))
                  } else {
                    Modifier.liquidGlassSurface(
                      shape = RoundedCornerShape(50),
                      topColor = GlassSurfaceBright,
                      bottomColor = GlassSurfaceSoft,
                      borderWidth = 1.dp
                    )
                  })
                    .heightIn(min = 48.dp)
                    .clickable { selectedFilter = filter }
                    .padding(horizontal = 16.dp, vertical = 10.dp)
                    .testTag("staff_filter_${filter.name.lowercase()}"),
                  contentAlignment = Alignment.Center
                ) {
                  Text(
                    text = labelText,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface
                  )
                }
              }
            }
          }
        }

      items(
        items = filteredStaff,
        key = { it.id },
        contentType = { "staff_card" }
      ) { member ->
        val isOnLeaveToday = onLeaveIdsToday.contains(member.id)
        val isOnRestToday = member.restDayEnum.dayOfWeek == selectedDate.dayOfWeek
        StaffMemberCard(
          member = member,
          today = today,
          isOnLeaveToday = isOnLeaveToday,
          isOnRestToday = isOnRestToday,
          canManageStaff = canManageStaff,
          canManageTrainingDue = canManageTrainingDue,
          onClickStaffContact = {
            if (member.hasPhoneNumber || member.stationDuty) {
              contactDialogMember = member
            }
          },
          onQuickCall = {
            if (member.availablePhones.size > 1) {
              contactDialogMember = member
            } else {
              phoneActions.placeCall(member.preferredPhoneNumber, member.name)
            }
          },
          onQuickMessage = {
            if (member.availablePhones.size > 1) {
              contactDialogMember = member
            } else {
              phoneActions.sendMessage(member.preferredPhoneNumber)
            }
          },
          onEditTrainingDue = { trainingDueMember = member },
          onEdit = { editingMember = member },
          onDelete = { pendingDeleteMember = member }
        )
      }

      item(key = "staff_bottom_spacer", contentType = "spacer") {
        Spacer(modifier = Modifier.height(16.dp))
      }
    }
  }

  if (showAddDialog && canManageStaff) {
    StaffEditDialog(
      initialMember = null,
      nextSuggestedSerialNo = nextSerialNo,
      isBusy = isBusy,
      onDismiss = { showAddDialog = false },
      onSave = { serialNo, name, designation, competency, stationDuty, shedDuty, weeklyRestDay, trainingDueDate, trainingCourse, phoneNumber, secondaryPhoneNumber ->
        onAddStaffMember(
          serialNo,
          name,
          designation,
          competency,
          stationDuty,
          shedDuty,
          weeklyRestDay,
          trainingDueDate,
          trainingCourse,
          phoneNumber,
          secondaryPhoneNumber
        )
        showAddDialog = false
      }
    )
  }

  val targetEdit = editingMember
  if (targetEdit != null && canManageStaff) {
    StaffEditDialog(
      initialMember = targetEdit,
      nextSuggestedSerialNo = targetEdit.serialNo,
      isBusy = isBusy,
      onDismiss = { editingMember = null },
      onSave = { serialNo, name, designation, competency, stationDuty, shedDuty, weeklyRestDay, trainingDueDate, trainingCourse, phoneNumber, secondaryPhoneNumber ->
        onUpdateStaffMember(
          targetEdit.copy(
            serialNo = serialNo,
            name = name,
            designation = designation.name,
            competency = competency.name,
            stationDuty = stationDuty,
            shedDuty = shedDuty,
            weeklyRestDay = weeklyRestDay.name,
            trainingDueDate = trainingDueDate,
            trainingCourse = trainingCourse,
            phoneNumber = phoneNumber,
            secondaryPhoneNumber = secondaryPhoneNumber
          )
        )
        editingMember = null
      }
    )
  }

  val targetTrainingDue = trainingDueMember
  if (targetTrainingDue != null && canManageTrainingDue) {
    SetTrainingDueDialog(
      fixedStaff = targetTrainingDue,
      isBusy = isBusy,
      onDismiss = { trainingDueMember = null },
      onSave = { staff, dueDate, course ->
        onUpdateStaffTrainingDue(staff.id, staff.name, dueDate, course)
        trainingDueMember = null
      }
    )
  }

  val targetDelete = pendingDeleteMember
  if (targetDelete != null && canManageStaff) {
    AlertDialog(
      onDismissRequest = { pendingDeleteMember = null },
      containerColor = GlassPopupSurface,
      title = { Text("Remove Staff Member?", fontWeight = FontWeight.Bold) },
      text = {
        Text(
          "Remove #${targetDelete.serialNo} ${targetDelete.name} (${targetDelete.designationEnum.label}) from the PPIO roster and vacate any assigned shift slots?"
        )
      },
      confirmButton = {
        TextButton(
          onClick = {
            onDeleteStaffMember(targetDelete.id, targetDelete.name)
            pendingDeleteMember = null
          },
          modifier = Modifier
            .heightIn(min = 48.dp)
            .testTag("confirm_delete_staff_button")
        ) {
          Text("Remove", color = SignalCrimson, fontWeight = FontWeight.Bold)
        }
      },
      dismissButton = {
        TextButton(
          onClick = { pendingDeleteMember = null },
          modifier = Modifier.heightIn(min = 48.dp)
        ) {
          Text("Cancel")
        }
      }
    )
  }
  val contactTarget = contactDialogMember
  if (contactTarget != null) {
    StationStaffContactDialog(
      staff = contactTarget,
      activeStationBadge = if (contactTarget.stationDuty) "STATION STAFF" else "DEPOT STAFF",
      phoneActions = phoneActions,
      onDismiss = { contactDialogMember = null }
    )
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StaffMemberCard(
  member: StaffMember,
  today: LocalDate,
  isOnLeaveToday: Boolean,
  isOnRestToday: Boolean,
  canManageStaff: Boolean,
  canManageTrainingDue: Boolean,
  onClickStaffContact: () -> Unit,
  onQuickCall: () -> Unit,
  onQuickMessage: () -> Unit,
  onEditTrainingDue: () -> Unit,
  onEdit: () -> Unit,
  onDelete: () -> Unit
) {
  val canOpenContact = member.hasPhoneNumber || member.stationDuty
  GlassTile(
    shape = RoundedCornerShape(22.dp),
    topColor = GlassSurfaceBright,
    bottomColor = GlassSurfaceMid,
    modifier = Modifier.then(
      if (canOpenContact) {
        Modifier
          .clickable(onClick = onClickStaffContact)
          .testTag("station_staff_card_${member.id}")
      } else {
        Modifier
      }
    )
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(14.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
      // Full-width top identity header so long names never get squeezed by status pills or action icons
      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
      ) {
        GlassIconOrb(
          shape = RoundedCornerShape(12.dp),
          tintColor = GlassSurfaceSoft,
          modifier = Modifier.size(40.dp)
        ) {
          Text(
            text = "#${member.serialNo}",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
          )
        }
        Column(
          modifier = Modifier.weight(1f),
          verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
          Text(
            text = member.name,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
          )
          Text(
            text = member.designationEnum.label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
          )
        }
      }

      // Full-width status & qualification badges so AVAILABLE / ON LEAVE / REST TODAY & duty roles never truncate
      FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
      ) {
        if (isOnLeaveToday) {
          BadgePill("ON LEAVE", SignalCrimsonContainer, SignalCrimsonOnContainer, SignalCrimson)
        } else if (isOnRestToday) {
          BadgePill("REST TODAY", SafetyAmberContainer, SafetyAmberOnContainer, SafetyAmber)
        } else {
          BadgePill("AVAILABLE", SignalEmeraldContainer, SignalEmeraldOnContainer, SignalEmerald)
        }
        if (member.isShunter) {
          BadgePill("SHUNTER", DutySheetShunterBg, DutySheetShunterInk, DepotTeal)
        }
        if (member.isPointsman) {
          BadgePill("POINTSMAN", MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer, MaterialTheme.colorScheme.primary)
        }
        if (member.stationDuty) {
          BadgePill("STATION", DepotTealContainer, DepotTealOnContainer, DepotTeal)
        }
        if (member.shedDuty) {
          BadgePill("SHED", MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer, DepotTeal)
        }
      }

      val parsedTrainingDue = remember(member.trainingDueDate) {
        runCatching { LocalDate.parse(member.trainingDueDate.trim()) }.getOrNull()
      }
      if (member.restDayEnum != WeeklyRestDay.NONE || parsedTrainingDue != null) {
        FlowRow(
          modifier = Modifier
            .fillMaxWidth()
            .testTag("staff_rest_tag_${member.id}"),
          horizontalArrangement = Arrangement.spacedBy(6.dp),
          verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
          if (member.restDayEnum != WeeklyRestDay.NONE) {
            BadgePill(
              text = "REST: ${member.restDayEnum.label.uppercase()}",
              bg = GlassSurfaceSoft,
              fg = MaterialTheme.colorScheme.onSurfaceVariant,
              borderColor = GlassRimHighlight
            )
          }
          if (parsedTrainingDue != null) {
            val daysUntil = ChronoUnit.DAYS.between(today, parsedTrainingDue)
            val (bg, fg, border) = when {
              daysUntil < 0L -> Triple(SignalCrimsonContainer, SignalCrimsonOnContainer, SignalCrimson)
              daysUntil <= 30L -> Triple(SafetyAmberContainer, SafetyAmberOnContainer, SafetyAmber)
              else -> Triple(DepotTealContainer, DepotTealOnContainer, DepotTeal)
            }
            val coursePart = member.trainingCourse.trim().takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""
            BadgePill(
              text = "TRAINING DUE: $parsedTrainingDue$coursePart",
              bg = bg,
              fg = fg,
              borderColor = border
            )
          }
        }
      }

      if (member.hasPhoneNumber || canManageTrainingDue || canManageStaff) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween
        ) {
          if (member.hasPhoneNumber) {
            val phonesDisplay = member.availablePhones.joinToString(" · ") { it.second }
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(6.dp),
              modifier = Modifier.weight(1f)
            ) {
              Icon(
                imageVector = Icons.Default.Phone,
                contentDescription = null,
                tint = DepotTeal,
                modifier = Modifier.size(16.dp)
              )
              Text(
                text = phonesDisplay,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                color = DepotTealOnContainer,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
              )
            }
          } else {
            Spacer(modifier = Modifier.weight(1f))
          }

          Row(verticalAlignment = Alignment.CenterVertically) {
            if (member.hasPhoneNumber) {
              IconButton(
                onClick = onQuickMessage,
                modifier = Modifier
                  .size(48.dp)
                  .testTag("staff_card_msg_${member.id}")
              ) {
                Icon(
                  imageVector = Icons.Default.Sms,
                  contentDescription = "Message ${member.name}",
                  tint = MaterialTheme.colorScheme.primary
                )
              }
              IconButton(
                onClick = onQuickCall,
                modifier = Modifier
                  .size(48.dp)
                  .testTag("staff_card_call_${member.id}")
              ) {
                Icon(
                  imageVector = Icons.Default.Call,
                  contentDescription = "Call ${member.name}",
                  tint = SignalEmerald
                )
              }
            }
            if (canManageTrainingDue) {
              IconButton(
                onClick = onEditTrainingDue,
                modifier = Modifier
                  .size(48.dp)
                  .testTag("training_due_staff_${member.id}")
              ) {
                Icon(
                  imageVector = Icons.Default.School,
                  contentDescription = "Set training due for ${member.name}",
                  tint = if (member.hasTrainingDue) DepotTeal else MaterialTheme.colorScheme.onSurfaceVariant
                )
              }
            }
            if (canManageStaff) {
              IconButton(
                onClick = onEdit,
                modifier = Modifier
                  .size(48.dp)
                  .testTag("edit_staff_${member.id}")
              ) {
                Icon(
                  imageVector = Icons.Default.Edit,
                  contentDescription = "Edit ${member.name}",
                  tint = MaterialTheme.colorScheme.primary
                )
              }
              IconButton(
                onClick = onDelete,
                modifier = Modifier
                  .size(48.dp)
                  .testTag("delete_staff_${member.id}")
              ) {
                Icon(
                  imageVector = Icons.Default.Delete,
                  contentDescription = "Remove ${member.name}",
                  tint = SignalCrimson
                )
              }
            }
          }
        }
      }
    }
  }
}

@Composable
private fun BadgePill(
  text: String,
  bg: Color,
  fg: Color,
  borderColor: Color = GlassRimHighlight
) {
  GlassPillBadge(
    text = text,
    tintColor = bg.copy(alpha = 0.65f),
    textColor = fg,
    borderColor = borderColor
  )
}
