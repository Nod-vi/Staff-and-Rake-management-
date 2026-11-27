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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
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
import com.example.domain.SupervisorAnalyticsEngine
import com.example.model.DutyPermissionRecord
import com.example.model.LeaveRecord
import com.example.model.PermissionReliefType
import com.example.model.ShiftSlotAssignment
import com.example.model.ShiftType
import com.example.model.StaffMember
import com.example.model.UserRole
import com.example.ui.theme.DepotTeal
import com.example.ui.theme.DepotTealContainer
import com.example.ui.theme.DepotTealOnContainer
import com.example.ui.theme.GlassIconOrb
import com.example.ui.theme.GlassPillBadge
import com.example.ui.theme.GlassPopupSurface
import com.example.ui.theme.GlassSurfaceBright
import com.example.ui.theme.GlassSurfaceMid
import com.example.ui.theme.GlassSurfaceSoft
import com.example.ui.theme.GlassTile
import com.example.ui.theme.RailwayCobalt
import com.example.ui.theme.SafetyAmber
import com.example.ui.theme.SafetyAmberContainer
import com.example.ui.theme.SafetyAmberOnContainer
import com.example.ui.theme.SignalCrimson
import com.example.ui.theme.SignalEmerald
import com.example.ui.theme.SignalEmeraldContainer
import com.example.ui.theme.SignalEmeraldOnContainer
import com.example.ui.theme.liquidGlassAccent
import com.example.ui.theme.liquidGlassSurface
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth

private enum class PermissionHistoryFilter(val label: String) {
  TODAY("Today"),
  THIS_MONTH("This Month"),
  ALL("All")
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PermissionManagerScreen(
  selectedDate: LocalDate,
  staffList: List<StaffMember>,
  assignments: List<ShiftSlotAssignment>,
  leaves: List<LeaveRecord> = emptyList(),
  permissions: List<DutyPermissionRecord>,
  userRole: UserRole,
  roleAccess: com.example.model.ResolvedRoleAccess = com.example.model.RoleAccessPolicy.resolve(userRole, emptyMap()),
  isBusy: Boolean,
  onGrantPermission: (StaffMember, LocalDate, ShiftType, Double, PermissionReliefType, String, String) -> Unit,
  onDeletePermission: (String) -> Unit
) {
  if (!roleAccess.canViewPermissions) {
    Box(
      modifier = Modifier
        .fillMaxSize()
        .padding(24.dp),
      contentAlignment = Alignment.Center
    ) {
      GlassTile(shape = RoundedCornerShape(22.dp)) {
        Text(
          text = "Permission Manager is not enabled for your current role.",
          style = MaterialTheme.typography.bodyMedium,
          modifier = Modifier.padding(16.dp)
        )
      }
    }
    return
  }

  val canGrantPermissions = roleAccess.canGrantPermissions
  val canDeletePermissions = roleAccess.canDeletePermissions

  val isShiftIncharge = userRole == UserRole.SHIFT_INCHARGE
  var nowDateTime by remember { mutableStateOf(LocalDateTime.now()) }
  LaunchedEffect(isShiftIncharge) {
    if (isShiftIncharge) {
      while (true) {
        delay(30_000L)
        nowDateTime = LocalDateTime.now()
      }
    }
  }

  val liveSnapshot = remember(nowDateTime, staffList, assignments, leaves) {
    SupervisorAnalyticsEngine.resolveLiveShiftSnapshot(
      now = nowDateTime,
      staffList = staffList,
      assignments = assignments,
      leaves = leaves
    )
  }

  val liveEntryByStaffId = remember(liveSnapshot) {
    liveSnapshot.allPresentShiftEntries.associateBy { it.assignment.staffId }
  }

  val liveDutyTagByStaffId = remember(liveSnapshot) {
    liveSnapshot.allPresentShiftEntries.associate { entry ->
      val area = if (entry.isStationArea) {
        entry.assignment.dutyAreaEnum.shortCode
      } else if (entry.assignment.roleTypeEnum == com.example.model.SlotRoleType.POINTSMAN) {
        "PM"
      } else if (entry.assignment.roleTypeEnum == com.example.model.SlotRoleType.SHUNTER) {
        "SHUNTER"
      } else if (entry.assignment.shiftTypeEnum == ShiftType.SHIFT_08_17) {
        "08/17 GEN"
      } else {
        "SHED"
      }
      entry.assignment.staffId to "${entry.assignment.shiftTypeEnum.code} · $area"
    }
  }

  val selectableStaff = remember(staffList, isShiftIncharge, liveSnapshot) {
    if (isShiftIncharge) {
      liveSnapshot.currentShiftStaffMembers
    } else {
      staffList.filter { !it.isSupervisory }
    }
  }
  val selectableStaffIds = remember(selectableStaff) {
    selectableStaff.mapTo(HashSet()) { it.id }
  }

  val today = remember(nowDateTime) { nowDateTime.toLocalDate() }
  val currentMonthPrefix = remember(today) { YearMonth.from(today).toString() }

  var selectedStaffId by rememberSaveable {
    mutableStateOf("")
  }
  LaunchedEffect(selectableStaff) {
    if (selectedStaffId.isBlank() || !selectableStaff.any { it.id == selectedStaffId }) {
      selectedStaffId = selectableStaff.firstOrNull()?.id.orEmpty()
    }
  }
  val selectedStaff = remember(selectableStaff, selectedStaffId) {
    selectableStaff.firstOrNull { it.id == selectedStaffId } ?: selectableStaff.firstOrNull()
  }

  var permDate by rememberSaveable { mutableStateOf(selectedDate.toString()) }
  val parsedPermDate = remember(permDate) {
    runCatching { LocalDate.parse(permDate) }.getOrDefault(today)
  }
  var selectedShift by remember { mutableStateOf(ShiftType.SHIFT_06_14) }
  var selectedHours by rememberSaveable { mutableDoubleStateOf(2.0) }
  var selectedReliefType by remember { mutableStateOf(PermissionReliefType.EARLY_RELIEF) }
  var reason by rememberSaveable { mutableStateOf("") }
  var editingPermissionId by rememberSaveable { mutableStateOf("") }
  var showStaffPicker by remember { mutableStateOf(false) }

  var historyFilter by rememberSaveable { mutableStateOf(PermissionHistoryFilter.THIS_MONTH) }
  var historyQuery by rememberSaveable { mutableStateOf("") }

  // For ShiftIncharge, lock duty date & shift to the selected current-shift staff member's live shift.
  // For Supervisor/Admin, auto-detect staff's shift on chosen date when creating a new permission.
  val matchedAssignmentShift = remember(selectedStaff, permDate, assignments, isShiftIncharge, liveEntryByStaffId) {
    if (selectedStaff == null) null
    else if (isShiftIncharge) liveEntryByStaffId[selectedStaff.id]?.assignment?.shiftTypeEnum
    else assignments.firstOrNull { it.staffId == selectedStaff.id && it.date == permDate }?.shiftTypeEnum
  }
  LaunchedEffect(selectedStaff?.id, permDate, editingPermissionId, isShiftIncharge, liveSnapshot) {
    if (isShiftIncharge) {
      val liveEntry = selectedStaff?.let { liveEntryByStaffId[it.id] }
      permDate = liveEntry?.assignment?.date ?: liveSnapshot.activeShiftAssignmentDate.toString()
      selectedShift = liveEntry?.assignment?.shiftTypeEnum ?: liveSnapshot.activeShift
    } else if (editingPermissionId.isBlank() && matchedAssignmentShift != null) {
      selectedShift = matchedAssignmentShift
    }
  }

  val todayStr = today.toString()
  val todayPermissionsCount = remember(permissions, todayStr) {
    permissions.count { it.date == todayStr }
  }
  val monthPermissions = remember(permissions, currentMonthPrefix) {
    permissions.filter { it.date.startsWith(currentMonthPrefix) }
  }
  val monthTotalHours = remember(monthPermissions) {
    monthPermissions.sumOf { it.hours }
  }
  val monthStaffBreakdown = remember(monthPermissions) {
    monthPermissions
      .groupBy { it.staffId }
      .values
      .map { list ->
        (list.first().staffName.ifBlank { list.first().staffId }) to list.sumOf { it.hours }
      }
      .sortedByDescending { it.second }
      .take(6)
  }

  val filteredPermissions = remember(permissions, historyFilter, historyQuery, todayStr, currentMonthPrefix) {
    val q = historyQuery.trim().lowercase()
    permissions.filter { p ->
      val matchesPeriod = when (historyFilter) {
        PermissionHistoryFilter.TODAY -> p.date == todayStr
        PermissionHistoryFilter.THIS_MONTH -> p.date.startsWith(currentMonthPrefix)
        PermissionHistoryFilter.ALL -> true
      }
      val matchesQuery = q.isEmpty() ||
        p.staffName.lowercase().contains(q) ||
        p.reason.lowercase().contains(q) ||
        p.date.contains(q)
      matchesPeriod && matchesQuery
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
      verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
      // Header + KPI Summary
      item(key = "perm_header", contentType = "header") {
        GlassTile(
          shape = RoundedCornerShape(24.dp),
          topColor = GlassSurfaceBright,
          bottomColor = GlassSurfaceMid,
          modifier = Modifier.fillMaxWidth()
        ) {
          Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
          ) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
              GlassIconOrb(
                shape = RoundedCornerShape(14.dp),
                tintColor = DepotTeal.copy(alpha = 0.16f),
                modifier = Modifier.size(44.dp)
              ) {
                Icon(Icons.Default.Schedule, contentDescription = null, tint = DepotTeal)
              }
              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text = "Duty Permission Manager",
                  style = MaterialTheme.typography.titleLarge,
                  fontWeight = FontWeight.Bold
                )
                Text(
                  text = when {
                    canDeletePermissions -> "Grant, update, or remove duty relief hours for staff."
                    canGrantPermissions -> "Grant or update duty relief hours (delete requires Full Control)."
                    else -> "Read-only view of duty relief permissions."
                  },
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant
                )
              }
            }

            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
              PermMetricChip(
                label = "Today",
                value = "$todayPermissionsCount",
                accent = RailwayCobalt,
                modifier = Modifier.weight(1f)
              )
              PermMetricChip(
                label = "Month Hours",
                value = SupervisorAnalyticsEngine.formatHours(monthTotalHours),
                accent = DepotTeal,
                modifier = Modifier.weight(1f)
              )
              PermMetricChip(
                label = "Month Grants",
                value = "${monthPermissions.size}",
                accent = SafetyAmber,
                modifier = Modifier.weight(1f)
              )
            }
          }
        }
      }

      // Grant / Edit Duty Permission Form
      if (canGrantPermissions) {
        item(key = "perm_form", contentType = "form") {
        GlassTile(
          shape = RoundedCornerShape(24.dp),
          topColor = GlassSurfaceBright,
          bottomColor = GlassSurfaceMid,
          modifier = Modifier
            .fillMaxWidth()
            .testTag("permission_grant_form")
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
              Text(
                text = if (editingPermissionId.isBlank()) "Grant Duty Relief Permission" else "Edit Permission Record",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
              )
              if (editingPermissionId.isNotBlank()) {
                TextButton(
                  onClick = {
                    editingPermissionId = ""
                    reason = ""
                    selectedHours = 2.0
                  },
                  modifier = Modifier.heightIn(min = 48.dp)
                ) {
                  Text("Cancel Edit", color = SignalCrimson)
                }
              }
            }

            if (isShiftIncharge) {
              Surface(
                shape = RoundedCornerShape(12.dp),
                color = SignalEmeraldContainer.copy(alpha = 0.45f),
                border = BorderStroke(1.dp, SignalEmerald.copy(alpha = 0.45f)),
                modifier = Modifier.fillMaxWidth()
              ) {
                Text(
                  text = if (selectableStaff.isNotEmpty()) {
                    "Current Shift Only (${liveSnapshot.activeShift.code} · ${liveSnapshot.activeShiftAssignmentDate}): showing ${selectableStaff.size} on-duty staff."
                  } else {
                    "No staff are assigned to the current shift (${liveSnapshot.activeShift.code} · ${liveSnapshot.activeShiftAssignmentDate}) yet."
                  },
                  style = MaterialTheme.typography.bodySmall,
                  color = SignalEmeraldOnContainer,
                  modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                )
              }
            }

            // Staff Picker
            Surface(
              shape = RoundedCornerShape(14.dp),
              color = GlassSurfaceSoft,
              border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
              modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp)
                .clickable(enabled = selectableStaff.isNotEmpty()) { showStaffPicker = true }
                .testTag("perm_select_staff_button")
            ) {
              Row(
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(horizontal = 14.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
              ) {
                Column(modifier = Modifier.weight(1f)) {
                  Text(
                    text = if (isShiftIncharge) "Current Shift Staff Member" else "Staff Member",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                  )
                  val liveTag = selectedStaff?.let { liveDutyTagByStaffId[it.id] }
                  Text(
                    text = selectedStaff?.let { member ->
                      val serialText = if (member.serialNo > 0L) "#${member.serialNo} " else ""
                      val suffix = if (isShiftIncharge && liveTag != null) " · $liveTag" else " (${member.designationEnum.label})"
                      "$serialText${member.name}$suffix"
                    } ?: if (isShiftIncharge) "No staff currently on duty" else "Select Staff",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2
                  )
                }
                Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Select staff")
              }
            }

            if (isShiftIncharge) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
              ) {
                Text(
                  text = "Active Duty Shift",
                  style = MaterialTheme.typography.labelMedium,
                  color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                GlassPillBadge(
                  text = "$permDate · ${selectedShift.code}",
                  tintColor = DepotTealContainer.copy(alpha = 0.78f),
                  textColor = DepotTealOnContainer,
                  borderColor = DepotTeal
                )
              }
            } else {
              // Date Stepper
              Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
              ) {
                Text(
                  text = "Duty Date",
                  style = MaterialTheme.typography.labelMedium,
                  color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.SpaceBetween
                ) {
                  IconButton(
                    onClick = { permDate = parsedPermDate.minusDays(1).toString() },
                    modifier = Modifier
                      .size(48.dp)
                      .testTag("perm_date_prev")
                  ) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous day")
                  }
                  Text(
                    text = "$permDate (${parsedPermDate.dayOfWeek.name.take(3)})",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                  )
                  if (permDate != todayStr) {
                    TextButton(
                      onClick = { permDate = todayStr },
                      contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                      modifier = Modifier.heightIn(min = 48.dp)
                    ) {
                      Text("Today", maxLines = 1)
                    }
                  }
                  IconButton(
                    onClick = { permDate = parsedPermDate.plusDays(1).toString() },
                    modifier = Modifier
                      .size(48.dp)
                      .testTag("perm_date_next")
                  ) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next day")
                  }
                }
              }

              // Shift Selector
              Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.SpaceBetween,
                  verticalAlignment = Alignment.CenterVertically
                ) {
                  Text(
                    text = "Shift",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                  )
                  if (matchedAssignmentShift != null) {
                    Text(
                      text = "Assigned: ${matchedAssignmentShift.code}",
                      style = MaterialTheme.typography.labelSmall,
                      color = SignalEmeraldOnContainer,
                      fontWeight = FontWeight.SemiBold
                    )
                  }
                }
                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                  ShiftType.entries.forEach { shift ->
                    val isSelected = selectedShift == shift
                    Box(
                      modifier = (if (isSelected) {
                        Modifier.liquidGlassAccent(shape = RoundedCornerShape(12.dp))
                      } else {
                        Modifier.liquidGlassSurface(
                          shape = RoundedCornerShape(12.dp),
                          topColor = GlassSurfaceBright,
                          bottomColor = GlassSurfaceSoft,
                          borderWidth = 1.dp
                        )
                      })
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .clickable { selectedShift = shift }
                        .testTag("perm_shift_${shift.name}"),
                      contentAlignment = Alignment.Center
                    ) {
                      Text(
                        text = shift.code,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface
                      )
                    }
                  }
                }
              }
            }

            // Hours Selector (Quick Chips + 0.5h Stepper)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
              ) {
                Text(
                  text = "Permission Relief Hours",
                  style = MaterialTheme.typography.labelMedium,
                  color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                  IconButton(
                    onClick = { selectedHours = (selectedHours - 0.5).coerceAtLeast(0.5) },
                    modifier = Modifier
                      .size(48.dp)
                      .testTag("perm_hours_minus")
                  ) {
                    Icon(Icons.Default.Remove, contentDescription = "Decrease relief hours")
                  }
                  GlassPillBadge(
                    text = "${SupervisorAnalyticsEngine.formatHours(selectedHours)} RELIEF",
                    tintColor = DepotTealContainer.copy(alpha = 0.85f),
                    textColor = DepotTealOnContainer,
                    borderColor = DepotTeal,
                    modifier = Modifier.testTag("perm_selected_hours_badge")
                  )
                  IconButton(
                    onClick = { selectedHours = (selectedHours + 0.5).coerceAtMost(12.0) },
                    modifier = Modifier
                      .size(48.dp)
                      .testTag("perm_hours_plus")
                  ) {
                    Icon(Icons.Default.Add, contentDescription = "Increase relief hours")
                  }
                }
              }

              FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
              ) {
                listOf(1.0, 2.0, 3.0, 4.0, 6.0, 8.0).forEach { preset ->
                  val isPicked = kotlin.math.abs(selectedHours - preset) < 0.05
                  Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (isPicked) DepotTeal else GlassSurfaceSoft,
                    border = BorderStroke(
                      1.dp,
                      if (isPicked) DepotTeal else MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
                    ),
                    modifier = Modifier
                      .heightIn(min = 48.dp)
                      .clickable { selectedHours = preset }
                      .testTag("perm_hours_preset_${preset.toInt()}h")
                  ) {
                    Box(
                      modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                      contentAlignment = Alignment.Center
                    ) {
                      Text(
                        text = "${preset.toInt()} hr${if (preset > 1.0) "s" else ""}",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (isPicked) FontWeight.Bold else FontWeight.Medium,
                        color = if (isPicked) Color.White else MaterialTheme.colorScheme.onSurface
                      )
                    }
                  }
                }
              }
            }

            // Relief Type Selector
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
              Text(
                text = "Relief Type",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
              ) {
                PermissionReliefType.entries.forEach { type ->
                  val active = selectedReliefType == type
                  Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (active) RailwayCobalt.copy(alpha = 0.14f) else GlassSurfaceSoft,
                    border = BorderStroke(
                      1.dp,
                      if (active) RailwayCobalt else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                    ),
                    modifier = Modifier
                      .weight(1f)
                      .heightIn(min = 48.dp)
                      .clickable { selectedReliefType = type }
                      .testTag("perm_type_${type.name}")
                  ) {
                    Box(
                      modifier = Modifier.padding(horizontal = 6.dp, vertical = 10.dp),
                      contentAlignment = Alignment.Center
                    ) {
                      Text(
                        text = type.label,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                        color = if (active) RailwayCobalt else MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                        maxLines = 2
                      )
                    }
                  }
                }
              }
            }

            OutlinedTextField(
              value = reason,
              onValueChange = { reason = it.take(300) },
              label = { Text("Reason / Remarks (optional)") },
              placeholder = { Text("e.g., Personal work, medical visit, early relief approved") },
              singleLine = false,
              maxLines = 2,
              modifier = Modifier
                .fillMaxWidth()
                .testTag("perm_reason_input")
            )

            Button(
              onClick = {
                selectedStaff?.let { staff ->
                  onGrantPermission(
                    staff,
                    parsedPermDate,
                    selectedShift,
                    selectedHours,
                    selectedReliefType,
                    reason,
                    editingPermissionId
                  )
                  editingPermissionId = ""
                  reason = ""
                }
              },
              enabled = !isBusy && selectedStaff != null,
              modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .testTag("perm_submit_button")
            ) {
              Text(
                text = if (editingPermissionId.isBlank()) {
                  "Grant ${SupervisorAnalyticsEngine.formatHours(selectedHours)} Relief Permission"
                } else {
                  "Save Updated Permission"
                },
                fontWeight = FontWeight.Bold
              )
            }
          }
        }
        }
      }

      // Monthly Top Relief Hours Summary
      if (monthStaffBreakdown.isNotEmpty()) {
        item(key = "perm_month_summary", contentType = "month_summary") {
          GlassTile(
            shape = RoundedCornerShape(22.dp),
            topColor = GlassSurfaceBright,
            bottomColor = GlassSurfaceMid
          ) {
            Column(
              modifier = Modifier.padding(14.dp),
              verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
              Text(
                text = "This Month's Relief Hours by Staff",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
              )
              FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
              ) {
                monthStaffBreakdown.forEach { (name, hrs) ->
                  val high = hrs >= 6.0
                  GlassPillBadge(
                    text = "$name · ${SupervisorAnalyticsEngine.formatHours(hrs)}",
                    tintColor = if (high) SafetyAmberContainer.copy(alpha = 0.75f) else DepotTealContainer.copy(alpha = 0.55f),
                    textColor = if (high) SafetyAmberOnContainer else DepotTealOnContainer,
                    borderColor = if (high) SafetyAmber else DepotTeal
                  )
                }
              }
            }
          }
        }
      }

      // Filter & Search Header
      item(key = "perm_history_controls", contentType = "controls") {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Text(
              text = "Granted Permissions (${filteredPermissions.size})",
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold
            )
          }
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
          ) {
            PermissionHistoryFilter.entries.forEach { filter ->
              val selected = historyFilter == filter
              Box(
                modifier = (if (selected) {
                  Modifier.liquidGlassAccent(shape = RoundedCornerShape(12.dp))
                } else {
                  Modifier.liquidGlassSurface(
                    shape = RoundedCornerShape(12.dp),
                    topColor = GlassSurfaceBright,
                    bottomColor = GlassSurfaceSoft,
                    borderWidth = 1.dp
                  )
                })
                  .weight(1f)
                  .heightIn(min = 48.dp)
                  .clickable { historyFilter = filter }
                  .testTag("perm_filter_${filter.name.lowercase()}"),
                contentAlignment = Alignment.Center
              ) {
                Text(
                  text = filter.label,
                  style = MaterialTheme.typography.labelMedium,
                  fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                  color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface
                )
              }
            }
          }
          OutlinedTextField(
            value = historyQuery,
            onValueChange = { historyQuery = it },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            placeholder = { Text("Search staff, date, or reason...") },
            singleLine = true,
            modifier = Modifier
              .fillMaxWidth()
              .testTag("perm_history_search")
          )
        }
      }

      if (filteredPermissions.isEmpty()) {
        item(key = "perm_empty", contentType = "empty") {
          GlassTile(
            shape = RoundedCornerShape(20.dp),
            topColor = GlassSurfaceBright,
            bottomColor = GlassSurfaceMid,
            modifier = Modifier.fillMaxWidth()
          ) {
            Text(
              text = "No duty relief permissions recorded for this filter.",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.padding(16.dp)
            )
          }
        }
      } else {
        items(
          items = filteredPermissions,
          key = { it.id },
          contentType = { "perm_row" }
        ) { record ->
          val canEditThisRecord = canGrantPermissions && (!isShiftIncharge || selectableStaffIds.contains(record.staffId))
          DutyPermissionCard(
            record = record,
            isBusy = isBusy,
            canEdit = canEditThisRecord,
            canDelete = canDeletePermissions,
            onEdit = {
              editingPermissionId = record.id
              selectedStaffId = record.staffId
              permDate = record.date
              selectedShift = record.shiftTypeEnum
              selectedHours = record.hours
              selectedReliefType = record.reliefTypeEnum
              reason = record.reason
            },
            onDelete = { onDeletePermission(record.id) }
          )
        }
      }

      item(key = "perm_bottom_spacer") {
        Spacer(modifier = Modifier.height(16.dp))
      }
    }
  }

  if (showStaffPicker) {
    PermissionStaffPickerDialog(
      title = if (isShiftIncharge) {
        "Current Shift Staff (${liveSnapshot.activeShift.code})"
      } else {
        "Select Staff for Relief Permission"
      },
      staffList = selectableStaff,
      selectedStaffId = selectedStaff?.id.orEmpty(),
      liveDutyTagByStaffId = if (isShiftIncharge) liveDutyTagByStaffId else emptyMap(),
      onSelect = { chosen ->
        selectedStaffId = chosen.id
        showStaffPicker = false
      },
      onDismiss = { showStaffPicker = false }
    )
  }
}

@Composable
private fun PermMetricChip(
  label: String,
  value: String,
  accent: Color,
  modifier: Modifier = Modifier
) {
  Surface(
    shape = RoundedCornerShape(14.dp),
    color = accent.copy(alpha = 0.10f),
    border = BorderStroke(1.dp, accent.copy(alpha = 0.35f)),
    modifier = modifier
  ) {
    Column(
      modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
      horizontalAlignment = Alignment.CenterHorizontally
    ) {
      Text(
        text = value,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = accent
      )
      Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        maxLines = 2
      )
    }
  }
}

@Composable
private fun DutyPermissionCard(
  record: DutyPermissionRecord,
  isBusy: Boolean,
  canEdit: Boolean = true,
  canDelete: Boolean = true,
  onEdit: () -> Unit,
  onDelete: () -> Unit
) {
  GlassTile(
    shape = RoundedCornerShape(20.dp),
    topColor = GlassSurfaceBright,
    bottomColor = GlassSurfaceMid,
    modifier = Modifier
      .fillMaxWidth()
      .testTag("perm_card_${record.id}")
  ) {
    Column(
      modifier = Modifier.padding(14.dp),
      verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
          Text(
            text = record.staffName,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            maxLines = 2
          )
          Text(
            text = "${record.date} · Shift ${record.shiftTypeEnum.code} · ${record.reliefTypeEnum.label}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
        GlassPillBadge(
          text = SupervisorAnalyticsEngine.formatHours(record.hours),
          tintColor = DepotTealContainer.copy(alpha = 0.85f),
          textColor = DepotTealOnContainer,
          borderColor = DepotTeal
        )
      }

      if (record.reason.isNotBlank()) {
        Text(
          text = "Reason: ${record.reason}",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurface
        )
      }

      HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
      ) {
        Text(
          text = "Granted by ${record.grantedByName.ifBlank { "Supervisor" }}",
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
          if (canEdit) {
            IconButton(
              onClick = onEdit,
              enabled = !isBusy,
              modifier = Modifier
                .size(48.dp)
                .testTag("perm_edit_${record.id}")
            ) {
              Icon(Icons.Default.Edit, contentDescription = "Edit permission", tint = RailwayCobalt)
            }
          }
          if (canDelete) {
            IconButton(
              onClick = onDelete,
              enabled = !isBusy,
              modifier = Modifier
                .size(48.dp)
                .testTag("perm_delete_${record.id}")
            ) {
              Icon(Icons.Default.Delete, contentDescription = "Delete permission", tint = SignalCrimson)
            }
          }
        }
      }
    }
  }
}

@Composable
private fun PermissionStaffPickerDialog(
  title: String,
  staffList: List<StaffMember>,
  selectedStaffId: String,
  liveDutyTagByStaffId: Map<String, String> = emptyMap(),
  onSelect: (StaffMember) -> Unit,
  onDismiss: () -> Unit
) {
  var query by remember { mutableStateOf("") }
  val filtered = remember(staffList, query) {
    val q = query.trim().lowercase()
    if (q.isEmpty()) staffList
    else staffList.filter {
      it.name.lowercase().contains(q) ||
        it.designationEnum.label.lowercase().contains(q) ||
        it.serialNo.toString().contains(q)
    }
  }

  AlertDialog(
    onDismissRequest = onDismiss,
    containerColor = GlassPopupSurface,
    title = { Text(title) },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
          value = query,
          onValueChange = { query = it },
          leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
          placeholder = { Text("Search name or serial #") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth()
        )
        LazyColumn(
          modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 320.dp),
          verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
          items(filtered, key = { it.id }) { member ->
            val selected = member.id == selectedStaffId
            val liveTag = liveDutyTagByStaffId[member.id]
            Surface(
              shape = RoundedCornerShape(12.dp),
              color = if (selected) SignalEmeraldContainer.copy(alpha = 0.7f) else GlassSurfaceSoft,
              border = BorderStroke(
                1.dp,
                if (selected) SignalEmerald else MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
              ),
              modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clickable { onSelect(member) }
                .testTag("perm_picker_staff_${member.id}")
            ) {
              Row(
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
              ) {
                Column(modifier = Modifier.weight(1f)) {
                  val serialPrefix = member.serialNo.takeIf { it > 0L }?.let { "#$it " } ?: ""
                  Text(
                    text = "$serialPrefix${member.name}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                  )
                  Text(
                    text = "${member.designationEnum.label} · Rest: ${member.restDayEnum.label}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                  )
                }
                if (liveTag != null) {
                  GlassPillBadge(
                    text = liveTag,
                    tintColor = SignalEmeraldContainer,
                    textColor = SignalEmeraldOnContainer,
                    borderColor = SignalEmerald
                  )
                } else if (member.isPointsman) {
                  GlassPillBadge(
                    text = "PM",
                    tintColor = DepotTealContainer,
                    textColor = DepotTealOnContainer,
                    borderColor = DepotTeal
                  )
                }
              }
            }
          }
        }
      }
    },
    confirmButton = {
      OutlinedButton(onClick = onDismiss) { Text("Close") }
    }
  )
}
