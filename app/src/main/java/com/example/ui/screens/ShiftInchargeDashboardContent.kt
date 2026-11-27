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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Engineering
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.Train
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.unit.sp
import com.example.domain.LiveDutyStaffEntry
import com.example.domain.SupervisorAnalyticsEngine
import com.example.model.DutyPermissionRecord
import com.example.model.IMPORTANT_DEPOT_CONTACTS
import com.example.model.ImportantContact
import com.example.model.LeaveRecord
import com.example.model.ShiftSlotAssignment
import com.example.model.SlotRoleType
import com.example.model.StaffMember
import com.example.ui.theme.DepotTeal
import com.example.ui.theme.DepotTealContainer
import com.example.ui.theme.DepotTealOnContainer
import com.example.ui.theme.DutySheetShunterInk
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
import com.example.ui.theme.SignalEmerald
import com.example.ui.theme.SignalEmeraldContainer
import com.example.ui.theme.SignalEmeraldOnContainer
import com.example.ui.theme.liquidGlassAccent
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

private val LIVE_TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("hh:mm a")

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ShiftInchargeDashboardContent(
  staffList: List<StaffMember>,
  assignments: List<ShiftSlotAssignment>,
  leaves: List<LeaveRecord>,
  dutyPermissions: List<DutyPermissionRecord>,
  importantContacts: List<ImportantContact> = IMPORTANT_DEPOT_CONTACTS,
  onOpenPermissionsTab: () -> Unit,
  initialDateTime: LocalDateTime? = null
) {
  val phoneActions = rememberPhoneActionController()
  var nowDateTime by remember { mutableStateOf(initialDateTime ?: LocalDateTime.now()) }
  var selectedStationStaff by remember { mutableStateOf<Pair<StaffMember, String?>?>(null) }

  // Automatically refresh current time every 30 seconds so active shift flips live at 06:00, 14:00, 22:00
  LaunchedEffect(initialDateTime) {
    if (initialDateTime == null) {
      while (true) {
        delay(30_000L)
        nowDateTime = LocalDateTime.now()
      }
    }
  }

  val snapshot = remember(nowDateTime, staffList, assignments, leaves) {
    SupervisorAnalyticsEngine.resolveLiveShiftSnapshot(
      now = nowDateTime,
      staffList = staffList,
      assignments = assignments,
      leaves = leaves
    )
  }

  val todayStr = remember(nowDateTime) { nowDateTime.toLocalDate().toString() }
  val activeShiftDateStr = remember(snapshot) { snapshot.activeShiftAssignmentDate.toString() }
  val activeShiftPermissionsByStaff = remember(dutyPermissions, todayStr, activeShiftDateStr, snapshot.activeShift) {
    dutyPermissions
      .filter {
        (it.date == todayStr || it.date == activeShiftDateStr) &&
          it.shiftTypeEnum == snapshot.activeShift
      }
      .groupBy { it.staffId }
  }
  val todayPermissions = remember(dutyPermissions, todayStr) {
    dutyPermissions.filter { it.date == todayStr }
  }

  Box(
    modifier = Modifier.fillMaxSize(),
    contentAlignment = Alignment.TopCenter
  ) {
    LazyColumn(
      modifier = Modifier
        .fillMaxSize()
        .widthIn(max = 780.dp)
        .testTag("shift_incharge_dashboard_list"),
      contentPadding = PaddingValues(16.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
      // 1. Live Shift Header & Quick Actions
      item(key = "si_live_header") {
        GlassTile(
          shape = RoundedCornerShape(24.dp),
          topColor = GlassSurfaceBright,
          bottomColor = GlassSurfaceMid,
          tintColor = RailwayCobalt.copy(alpha = 0.08f),
          modifier = Modifier
            .fillMaxWidth()
            .testTag("shift_incharge_live_header")
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
                tintColor = RailwayCobalt.copy(alpha = 0.16f),
                modifier = Modifier.size(46.dp)
              ) {
                Icon(Icons.Default.AccessTime, contentDescription = null, tint = RailwayCobalt)
              }
              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text = "ShiftIncharge Live Dashboard",
                  style = MaterialTheme.typography.titleLarge,
                  fontWeight = FontWeight.Bold
                )
                Text(
                  text = "${nowDateTime.format(LIVE_TIME_FORMATTER)} · ${nowDateTime.toLocalDate()} (${nowDateTime.dayOfWeek.name.take(3)})",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant
                )
              }
            }

            FlowRow(
              horizontalArrangement = Arrangement.spacedBy(8.dp),
              verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
              GlassPillBadge(
                text = "LIVE SHIFT: ${snapshot.activeShift.code} (${snapshot.activeShiftWindowLabel})",
                tintColor = SignalEmeraldContainer.copy(alpha = 0.85f),
                textColor = SignalEmeraldOnContainer,
                borderColor = SignalEmerald,
                modifier = Modifier.testTag("shift_incharge_active_shift_badge")
              )
              if (snapshot.generalShiftActive && snapshot.generalShiftStaff.isNotEmpty()) {
                GlassPillBadge(
                  text = "08/17 GENERAL ACTIVE (${snapshot.generalShiftStaff.size})",
                  tintColor = DepotTealContainer.copy(alpha = 0.78f),
                  textColor = DepotTealOnContainer,
                  borderColor = DepotTeal
                )
              }
            }

            Row(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
              ShiftInchargeMetricChip(
                label = "On Duty Now",
                value = "${snapshot.totalPresentShiftStaff}",
                accent = SignalEmerald,
                modifier = Modifier.weight(1f)
              )
              ShiftInchargeMetricChip(
                label = "Station Live",
                value = "${snapshot.activeStationStaff.size}",
                accent = DepotTeal,
                modifier = Modifier.weight(1f)
              )
              ShiftInchargeMetricChip(
                label = "Permits Today",
                value = "${todayPermissions.size}",
                accent = RailwayCobalt,
                modifier = Modifier.weight(1f)
              )
              ShiftInchargeMetricChip(
                label = "Leave / Rest",
                value = "${snapshot.onLeaveTodayCount}/${snapshot.onRestTodayCount}",
                accent = SafetyAmber,
                modifier = Modifier.weight(1f)
              )
            }

            Button(
              onClick = onOpenPermissionsTab,
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
                .testTag("shift_incharge_open_permissions_btn")
            ) {
              Icon(Icons.Default.Schedule, contentDescription = null, modifier = Modifier.size(18.dp))
              Spacer(modifier = Modifier.width(8.dp))
              Text("Open Permission Manager (Relieve Staff)", fontWeight = FontWeight.Bold)
            }
          }
        }
      }

      // 2. Present Duty Staff Section (Active Shift — Shed & Pointsman + 08/17 General)
      item(key = "si_present_duty_staff") {
        GlassTile(
          shape = RoundedCornerShape(24.dp),
          topColor = GlassSurfaceBright,
          bottomColor = GlassSurfaceMid,
          modifier = Modifier
            .fillMaxWidth()
            .testTag("shift_incharge_present_duty_card")
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
                shape = RoundedCornerShape(12.dp),
                tintColor = RailwayCobalt.copy(alpha = 0.14f),
                modifier = Modifier.size(38.dp)
              ) {
                Icon(Icons.Default.Engineering, contentDescription = null, tint = RailwayCobalt)
              }
              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text = "Present Duty Staff (${snapshot.activeShift.code} ${snapshot.activeShift.displayName})",
                  style = MaterialTheme.typography.titleMedium,
                  fontWeight = FontWeight.Bold
                )
                Text(
                  text = "Shift date ${snapshot.activeShiftAssignmentDate} · ${snapshot.activeShiftWindowLabel} · Tap any staff to Call or Message",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant
                )
              }
            }

            if (
              snapshot.pointsmanStaff.isEmpty() &&
              snapshot.shunterStaff.isEmpty() &&
              snapshot.shedStaff.isEmpty() &&
              snapshot.generalShiftStaff.isEmpty()
            ) {
              Surface(
                shape = RoundedCornerShape(14.dp),
                color = SafetyAmberContainer.copy(alpha = 0.45f),
                border = BorderStroke(1.dp, SafetyAmber.copy(alpha = 0.45f)),
                modifier = Modifier.fillMaxWidth()
              ) {
                Text(
                  text = "No ${snapshot.activeShift.code} Shed/Pointsman/Shunter duties assigned for ${snapshot.activeShiftAssignmentDate} yet.",
                  style = MaterialTheme.typography.bodySmall,
                  color = SafetyAmberOnContainer,
                  modifier = Modifier.padding(12.dp)
                )
              }
            } else {
              if (snapshot.pointsmanStaff.isNotEmpty()) {
                Text(
                  text = "Pointsman (${snapshot.pointsmanStaff.size})",
                  style = MaterialTheme.typography.labelLarge,
                  fontWeight = FontWeight.Bold,
                  color = DepotTeal
                )
                snapshot.pointsmanStaff.forEach { entry ->
                  val member = entry.staff ?: StaffMember(
                    id = entry.assignment.staffId,
                    name = entry.displayName,
                    shedDuty = true
                  )
                  PresentDutyStaffRow(
                    entry = entry,
                    roleTag = "POINTSMAN",
                    permissions = activeShiftPermissionsByStaff[entry.assignment.staffId].orEmpty(),
                    phoneActions = phoneActions,
                    onClickMember = { selectedStationStaff = member to "POINTSMAN · ${snapshot.activeShift.code}" }
                  )
                }
              }

              if (snapshot.shunterStaff.isNotEmpty()) {
                if (snapshot.pointsmanStaff.isNotEmpty()) {
                  HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                }
                Text(
                  text = "Shunter (${snapshot.shunterStaff.size})",
                  style = MaterialTheme.typography.labelLarge,
                  fontWeight = FontWeight.Bold,
                  color = DutySheetShunterInk
                )
                snapshot.shunterStaff.forEach { entry ->
                  val member = entry.staff ?: StaffMember(
                    id = entry.assignment.staffId,
                    name = entry.displayName,
                    shedDuty = true
                  )
                  PresentDutyStaffRow(
                    entry = entry,
                    roleTag = "SHUNTER",
                    permissions = activeShiftPermissionsByStaff[entry.assignment.staffId].orEmpty(),
                    phoneActions = phoneActions,
                    onClickMember = { selectedStationStaff = member to "SHUNTER · ${snapshot.activeShift.code}" }
                  )
                }
              }

              if (snapshot.shedStaff.isNotEmpty()) {
                if (snapshot.pointsmanStaff.isNotEmpty() || snapshot.shunterStaff.isNotEmpty()) {
                  HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                }
                Text(
                  text = "Shed Duty Staff (${snapshot.shedStaff.size})",
                  style = MaterialTheme.typography.labelLarge,
                  fontWeight = FontWeight.Bold,
                  color = RailwayCobalt
                )
                snapshot.shedStaff.forEach { entry ->
                  val member = entry.staff ?: StaffMember(
                    id = entry.assignment.staffId,
                    name = entry.displayName,
                    shedDuty = true
                  )
                  PresentDutyStaffRow(
                    entry = entry,
                    roleTag = "SHED",
                    permissions = activeShiftPermissionsByStaff[entry.assignment.staffId].orEmpty(),
                    phoneActions = phoneActions,
                    onClickMember = { selectedStationStaff = member to "SHED · ${snapshot.activeShift.code}" }
                  )
                }
              }

              if (snapshot.generalShiftActive && snapshot.generalShiftStaff.isNotEmpty()) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                Text(
                  text = "General Shift 08/17 (${snapshot.generalShiftStaff.size})",
                  style = MaterialTheme.typography.labelLarge,
                  fontWeight = FontWeight.Bold,
                  color = SignalEmerald
                )
                snapshot.generalShiftStaff.forEach { entry ->
                  val member = entry.staff ?: StaffMember(
                    id = entry.assignment.staffId,
                    name = entry.displayName,
                    shedDuty = true
                  )
                  val isGenShunter = entry.assignment.roleTypeEnum == SlotRoleType.SHUNTER
                  PresentDutyStaffRow(
                    entry = entry,
                    roleTag = if (isGenShunter) "08/17 SHUNTER" else "08/17 GEN",
                    permissions = dutyPermissions.filter {
                      it.date == todayStr && it.staffId == entry.assignment.staffId
                    },
                    phoneActions = phoneActions,
                    onClickMember = {
                      selectedStationStaff = member to if (isGenShunter) "08/17 SHUNTER" else "08/17 GENERAL"
                    }
                  )
                }
              }
            }
          }
        }
      }

      // 3. Current Station Staff Section (Active Shift Station Staff ONLY — Clickable for Call & Message)
      item(key = "si_station_staff") {
        GlassTile(
          shape = RoundedCornerShape(24.dp),
          topColor = GlassSurfaceBright,
          bottomColor = GlassSurfaceMid,
          modifier = Modifier
            .fillMaxWidth()
            .testTag("shift_incharge_station_staff_card")
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
                shape = RoundedCornerShape(12.dp),
                tintColor = DepotTealContainer.copy(alpha = 0.7f),
                modifier = Modifier.size(38.dp)
              ) {
                Icon(Icons.Default.Train, contentDescription = null, tint = DepotTeal)
              }
              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text = "Current Station Staff (${snapshot.activeShift.code})",
                  style = MaterialTheme.typography.titleMedium,
                  fontWeight = FontWeight.Bold
                )
                Text(
                  text = "On station duty right now (${snapshot.activeStationStaff.size}) · Tap to Call or Message",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant
                )
              }
            }

            if (snapshot.activeStationStaff.isEmpty()) {
              Surface(
                shape = RoundedCornerShape(14.dp),
                color = GlassSurfaceSoft,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
                modifier = Modifier.fillMaxWidth()
              ) {
                Text(
                  text = "No station staff assigned to ${snapshot.activeShift.code} on ${snapshot.activeShiftAssignmentDate} yet.",
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                  modifier = Modifier.padding(12.dp)
                )
              }
            } else {
              snapshot.activeStationStaff.forEach { entry ->
                val member = entry.staff ?: StaffMember(
                  id = entry.assignment.staffId,
                  name = entry.displayName,
                  stationDuty = true
                )
                val areaLabel = entry.assignment.dutyAreaEnum.shortCode
                StationStaffClickableRow(
                  staff = member,
                  stationBadge = "$areaLabel · LIVE",
                  isCurrentlyOnDuty = true,
                  phoneActions = phoneActions,
                  onClickMember = { selectedStationStaff = member to "$areaLabel · ${snapshot.activeShift.code}" }
                )
              }
            }
          }
        }
      }

      // 4. Important Phone Numbers Section (Direct In-App Calling · Read-Only for ShiftIncharge)
      item(key = "si_important_numbers") {
        ImportantPhoneNumbersCard(
          phoneActions = phoneActions,
          contacts = importantContacts,
          canManageContacts = false
        )
      }

      item(key = "si_bottom_spacer") {
        Spacer(modifier = Modifier.height(16.dp))
      }
    }
  }

  val contactPair = selectedStationStaff
  if (contactPair != null) {
    StationStaffContactDialog(
      staff = contactPair.first,
      activeStationBadge = contactPair.second,
      phoneActions = phoneActions,
      onDismiss = { selectedStationStaff = null }
    )
  }
}

@Composable
private fun ShiftInchargeMetricChip(
  label: String,
  value: String,
  accent: Color,
  modifier: Modifier = Modifier
) {
  Surface(
    shape = RoundedCornerShape(14.dp),
    color = accent.copy(alpha = 0.10f),
    border = BorderStroke(1.dp, accent.copy(alpha = 0.32f)),
    modifier = modifier
  ) {
    Column(
      modifier = Modifier.padding(horizontal = 6.dp, vertical = 8.dp),
      horizontalAlignment = Alignment.CenterHorizontally
    ) {
      Text(
        text = value,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = accent,
        maxLines = 1
      )
      Text(
        text = label,
        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        maxLines = 2
      )
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PresentDutyStaffRow(
  entry: LiveDutyStaffEntry,
  roleTag: String,
  permissions: List<DutyPermissionRecord>,
  phoneActions: PhoneActionController,
  onClickMember: () -> Unit
) {
  val totalReliefHours = remember(permissions) { permissions.sumOf { it.hours } }
  val availablePhones = entry.staff?.availablePhones.orEmpty()
  val hasPhone = availablePhones.isNotEmpty()
  val phonesText = availablePhones.joinToString(" / ") { it.second }
  val serialPrefix = entry.staff?.serialNo?.takeIf { it > 0L }?.let { "#$it " } ?: ""

  Surface(
    shape = RoundedCornerShape(14.dp),
    color = GlassSurfaceSoft,
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.22f)),
    modifier = Modifier
      .fillMaxWidth()
      .heightIn(min = 56.dp)
      .clickable(onClick = onClickMember)
      .testTag("present_duty_staff_${entry.assignment.staffId}")
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(start = 12.dp, end = if (hasPhone) 6.dp else 12.dp, top = 10.dp, bottom = 8.dp),
      verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
      Text(
        text = "$serialPrefix${entry.displayName}",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
          .fillMaxWidth()
          .padding(end = if (hasPhone) 6.dp else 0.dp)
      )

      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
      ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
          FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
          ) {
            GlassPillBadge(
              text = roleTag,
              tintColor = DepotTealContainer.copy(alpha = 0.65f),
              textColor = DepotTealOnContainer,
              borderColor = DepotTeal
            )
            if (totalReliefHours > 0.0) {
              GlassPillBadge(
                text = "RELIEF ${SupervisorAnalyticsEngine.formatHours(totalReliefHours)}",
                tintColor = SafetyAmberContainer.copy(alpha = 0.85f),
                textColor = SafetyAmberOnContainer,
                borderColor = SafetyAmber
              )
            }
          }
          Text(
            text = if (hasPhone) {
              "${entry.staff?.designationEnum?.label ?: "Duty Staff"} · $phonesText"
            } else {
              "${entry.staff?.designationEnum?.label ?: "Duty Staff"} · Tap for options (no phone saved)"
            },
            style = MaterialTheme.typography.bodySmall,
            color = if (hasPhone) RailwayCobalt else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
          )
        }

        if (hasPhone) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(
              onClick = {
                if (availablePhones.size > 1) onClickMember()
                else phoneActions.sendMessage(availablePhones.first().second)
              },
              modifier = Modifier
                .size(48.dp)
                .testTag("present_staff_msg_${entry.assignment.staffId}")
            ) {
              Icon(
                imageVector = Icons.Default.Sms,
                contentDescription = "Message ${entry.displayName}",
                tint = RailwayCobalt
              )
            }
            IconButton(
              onClick = {
                if (availablePhones.size > 1) onClickMember()
                else phoneActions.placeCall(availablePhones.first().second, entry.displayName)
              },
              modifier = Modifier
                .size(48.dp)
                .testTag("present_staff_call_${entry.assignment.staffId}")
            ) {
              Icon(
                imageVector = Icons.Default.Call,
                contentDescription = "Call ${entry.displayName}",
                tint = SignalEmerald
              )
            }
          }
        }
      }
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StationStaffClickableRow(
  staff: StaffMember,
  stationBadge: String,
  isCurrentlyOnDuty: Boolean,
  phoneActions: PhoneActionController,
  onClickMember: () -> Unit
) {
  val availablePhones = remember(staff.phoneNumber, staff.secondaryPhoneNumber) { staff.availablePhones }
  val hasPhone = availablePhones.isNotEmpty()
  val phonesDisplay = availablePhones.joinToString(" / ") { it.second }
  val serialPrefix = staff.serialNo.takeIf { it > 0L }?.let { "#$it " } ?: ""

  Surface(
    shape = RoundedCornerShape(14.dp),
    color = if (isCurrentlyOnDuty) SignalEmeraldContainer.copy(alpha = 0.26f) else GlassSurfaceSoft,
    border = BorderStroke(
      1.dp,
      if (isCurrentlyOnDuty) SignalEmerald.copy(alpha = 0.45f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.22f)
    ),
    modifier = Modifier
      .fillMaxWidth()
      .heightIn(min = 56.dp)
      .clickable(onClick = onClickMember)
      .testTag("station_staff_row_${staff.id}")
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(start = 12.dp, end = if (hasPhone) 6.dp else 12.dp, top = 10.dp, bottom = 8.dp),
      verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
      // Full-width clear staff name so long names are never truncated by badges or action buttons
      Text(
        text = "$serialPrefix${staff.name}",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
          .fillMaxWidth()
          .padding(end = if (hasPhone) 6.dp else 0.dp)
      )

      Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
      ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
          FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
          ) {
            GlassPillBadge(
              text = stationBadge,
              tintColor = if (isCurrentlyOnDuty) SignalEmeraldContainer.copy(alpha = 0.85f) else DepotTealContainer.copy(alpha = 0.65f),
              textColor = if (isCurrentlyOnDuty) SignalEmeraldOnContainer else DepotTealOnContainer,
              borderColor = if (isCurrentlyOnDuty) SignalEmerald else DepotTeal
            )
            GlassPillBadge(
              text = staff.designationEnum.label,
              tintColor = GlassSurfaceBright.copy(alpha = 0.85f),
              textColor = MaterialTheme.colorScheme.onSurfaceVariant,
              borderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
            )
          }
          Text(
            text = if (hasPhone) {
              phonesDisplay
            } else {
              "Tap for options (no phone saved)"
            },
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (hasPhone) FontWeight.Medium else FontWeight.Normal,
            color = if (hasPhone) RailwayCobalt else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
          )
        }

        if (hasPhone) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(
              onClick = {
                if (availablePhones.size > 1) onClickMember()
                else phoneActions.sendMessage(availablePhones.first().second)
              },
              modifier = Modifier
                .size(48.dp)
                .testTag("station_staff_msg_${staff.id}")
            ) {
              Icon(
                imageVector = Icons.Default.Sms,
                contentDescription = "Message ${staff.name}",
                tint = RailwayCobalt
              )
            }
            IconButton(
              onClick = {
                if (availablePhones.size > 1) onClickMember()
                else phoneActions.placeCall(availablePhones.first().second, staff.name)
              },
              modifier = Modifier
                .size(48.dp)
                .testTag("station_staff_call_${staff.id}")
            ) {
              Icon(
                imageVector = Icons.Default.Call,
                contentDescription = "Call ${staff.name}",
                tint = SignalEmerald
              )
            }
          }
        }
      }
    }
  }
}
