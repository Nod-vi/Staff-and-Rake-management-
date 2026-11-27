package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FactCheck
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.EventBusy
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.ManageAccounts
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Train
import androidx.compose.material.icons.filled.Warning
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.domain.LiveShiftStaffSnapshot
import com.example.model.DepotUserProfile
import com.example.model.ResolvedRoleAccess
import com.example.model.RoleAccessPolicy
import com.example.model.UserRole
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
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private val HERO_CLOCK_FMT = DateTimeFormatter.ofPattern("hh:mm a", Locale.ENGLISH)
private val HERO_DATE_FMT = DateTimeFormatter.ofPattern("EEE, dd MMM yyyy", Locale.ENGLISH)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SupervisorAdminHeroHeaderCard(
  userRole: UserRole,
  selectedDate: LocalDate,
  today: LocalDate,
  nowDateTime: LocalDateTime,
  liveSnapshot: LiveShiftStaffSnapshot,
  onSelectDate: (LocalDate) -> Unit
) {
  val isAdmin = userRole == UserRole.ADMIN
  val prevDateStr = remember(selectedDate) { selectedDate.minusDays(1).toString() }
  val liveTotalOnDuty = remember(liveSnapshot) {
    liveSnapshot.currentShiftStaffMembers.size
  }

  GlassTile(
    shape = RoundedCornerShape(24.dp),
    topColor = GlassSurfaceBright,
    bottomColor = GlassSurfaceMid,
    tintColor = if (isAdmin) RailwayCobalt.copy(alpha = 0.08f) else DepotTeal.copy(alpha = 0.08f),
    modifier = Modifier
      .fillMaxWidth()
      .testTag("supervisor_admin_hero_header")
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
          shape = RoundedCornerShape(15.dp),
          tintColor = if (isAdmin) RailwayCobalt.copy(alpha = 0.16f) else DepotTeal.copy(alpha = 0.16f),
          modifier = Modifier.size(48.dp)
        ) {
          Icon(
            imageVector = if (isAdmin) Icons.Default.AdminPanelSettings else Icons.Default.Dashboard,
            contentDescription = null,
            tint = if (isAdmin) RailwayCobalt else DepotTeal
          )
        }
        Column(modifier = Modifier.weight(1f)) {
          val dashboardTitle = when (userRole) {
            UserRole.ADMIN -> stringResource(R.string.admin_dashboard_title)
            UserRole.SSE -> "SSE Command Dashboard"
            UserRole.JE -> "JE Roster Dashboard"
            else -> stringResource(R.string.supervisor_dashboard_title)
          }
          Text(
            text = dashboardTitle,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            maxLines = 2
          )
          Text(
            text = "${nowDateTime.format(HERO_CLOCK_FMT)} · Duty cycle $prevDateStr (22/06) → $selectedDate",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2
          )
        }
      }

      FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
      ) {
        GlassPillBadge(
          text = "LIVE ${liveSnapshot.activeShift.code} (${liveSnapshot.activeShiftWindowLabel})",
          tintColor = SignalEmeraldContainer.copy(alpha = 0.85f),
          textColor = SignalEmeraldOnContainer,
          borderColor = SignalEmerald,
          modifier = Modifier.testTag("supervisor_live_shift_badge")
        )
        GlassPillBadge(
          text = "$liveTotalOnDuty ON DUTY NOW",
          tintColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f),
          textColor = MaterialTheme.colorScheme.onPrimaryContainer,
          borderColor = RailwayCobalt
        )
        if (liveSnapshot.generalShiftActive && liveSnapshot.generalShiftStaff.isNotEmpty()) {
          GlassPillBadge(
            text = "08/17 GEN: ${liveSnapshot.generalShiftStaff.size}",
            tintColor = DepotTealContainer.copy(alpha = 0.75f),
            textColor = DepotTealOnContainer,
            borderColor = DepotTeal
          )
        }
      }

      // Interactive Duty Date Switcher
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .liquidGlassSurface(
            shape = RoundedCornerShape(14.dp),
            topColor = GlassSurfaceBright,
            bottomColor = GlassSurfaceSoft,
            borderWidth = 1.dp
          )
          .padding(horizontal = 4.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
      ) {
        IconButton(
          onClick = { onSelectDate(selectedDate.minusDays(1)) },
          modifier = Modifier
            .size(48.dp)
            .testTag("supervisor_dash_prev_day_btn")
        ) {
          Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
            contentDescription = stringResource(R.string.dashboard_prev_day_cd)
          )
        }

        Column(
          horizontalAlignment = Alignment.CenterHorizontally,
          modifier = Modifier
            .weight(1f)
            .padding(horizontal = 4.dp)
        ) {
          Text(
            text = selectedDate.format(HERO_DATE_FMT),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 2
          )
          val dayTag = when (selectedDate) {
            today -> "Today's Duty Roster"
            today.plusDays(1) -> "Tomorrow's Duty Roster"
            today.minusDays(1) -> "Yesterday's Duty Roster"
            else -> "Selected Duty Date"
          }
          Text(
            text = dayTag,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 2
          )
        }

        if (selectedDate != today) {
          TextButton(
            onClick = { onSelectDate(today) },
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
            modifier = Modifier
              .heightIn(min = 48.dp)
              .testTag("supervisor_dash_today_btn")
          ) {
            Text("Today", fontWeight = FontWeight.Bold, maxLines = 1)
          }
        }

        IconButton(
          onClick = { onSelectDate(selectedDate.plusDays(1)) },
          modifier = Modifier
            .size(48.dp)
            .testTag("supervisor_dash_next_day_btn")
        ) {
          Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = stringResource(R.string.dashboard_next_day_cd)
          )
        }
      }
    }
  }
}

private data class QuickTileSpec(
  val title: String,
  val subtitle: String,
  val icon: ImageVector,
  val accent: Color,
  val badgeText: String? = null,
  val enabled: Boolean = true,
  val testTag: String,
  val onClick: () -> Unit
)

@Composable
fun SupervisorQuickActionsGrid(
  userRole: UserRole,
  roleAccess: ResolvedRoleAccess = RoleAccessPolicy.resolve(userRole, emptyMap()),
  compliantShifts: Int,
  totalShifts: Int,
  pendingApprovalsTotal: Int,
  todayPermissionsCount: Int,
  activeStaffCount: Int,
  totalUsersCount: Int,
  unlinkedUsersCount: Int,
  isBusy: Boolean,
  onOpenShiftsTab: () -> Unit,
  onOpenDutiesTab: () -> Unit,
  onOpenLeaveTab: () -> Unit,
  onOpenPermissionsTab: () -> Unit,
  onOpenStaffTab: () -> Unit,
  onOpenUsersTab: () -> Unit,
  onOpenRolesTab: () -> Unit = {},
  rakesCount: Int = 0,
  onOpenRakesTab: () -> Unit = {},
  onAutoAssignWeek: (() -> Unit)?
) {
  val tiles = buildList {
    if (roleAccess.canViewShifts) {
      add(
        QuickTileSpec(
          title = "Shifts Roster",
          subtitle = "$compliantShifts/$totalShifts optimal",
          icon = Icons.Default.CalendarMonth,
          accent = RailwayCobalt,
          testTag = "quick_action_shifts",
          onClick = onOpenShiftsTab
        )
      )
    }
    if (roleAccess.canViewDuties) {
      add(
        QuickTileSpec(
          title = "Tomorrow Duties",
          subtitle = if (roleAccess.canExportDuties) "Review & share" else "View roster",
          icon = Icons.AutoMirrored.Filled.FactCheck,
          accent = DepotTeal,
          testTag = "quick_action_duties",
          onClick = onOpenDutiesTab
        )
      )
    }
    if (roleAccess.canViewLeave) {
      add(
        QuickTileSpec(
          title = "Leaves & Rest",
          subtitle = if (pendingApprovalsTotal > 0) "$pendingApprovalsTotal pending" else "All clear",
          icon = Icons.Default.EventBusy,
          accent = if (pendingApprovalsTotal > 0) SafetyAmber else SignalEmerald,
          badgeText = if (pendingApprovalsTotal > 0) "$pendingApprovalsTotal" else null,
          testTag = "quick_action_leave",
          onClick = onOpenLeaveTab
        )
      )
    }
    if (roleAccess.canViewPermissions) {
      add(
        QuickTileSpec(
          title = "Permissions",
          subtitle = "$todayPermissionsCount relief today",
          icon = Icons.Default.Schedule,
          accent = DepotTeal,
          badgeText = if (todayPermissionsCount > 0) "$todayPermissionsCount" else null,
          testTag = "quick_action_permissions",
          onClick = onOpenPermissionsTab
        )
      )
    }
    if (roleAccess.canViewStaff) {
      add(
        QuickTileSpec(
          title = "Staff Directory",
          subtitle = "$activeStaffCount active staff",
          icon = Icons.Default.Badge,
          accent = RailwayCobalt,
          testTag = "quick_action_staff",
          onClick = onOpenStaffTab
        )
      )
    }
    if (roleAccess.canViewRakes) {
      add(
        QuickTileSpec(
          title = "Rake Manager",
          subtitle = "$rakesCount EMU · MEMU · VB",
          icon = Icons.Default.Train,
          accent = DepotTeal,
          testTag = "quick_action_rakes",
          onClick = onOpenRakesTab
        )
      )
    }
    if (roleAccess.canViewUsers) {
      add(
        QuickTileSpec(
          title = "Users & Roles",
          subtitle = if (unlinkedUsersCount > 0) "$unlinkedUsersCount unlinked" else "$totalUsersCount accounts",
          icon = Icons.Default.ManageAccounts,
          accent = if (unlinkedUsersCount > 0) SafetyAmber else SignalEmerald,
          badgeText = if (unlinkedUsersCount > 0) "$unlinkedUsersCount" else null,
          testTag = "quick_action_users",
          onClick = onOpenUsersTab
        )
      )
    }
    if (roleAccess.canAccessRolesManager) {
      add(
        QuickTileSpec(
          title = "Roles Manager",
          subtitle = "Menu access matrix",
          icon = Icons.Default.AdminPanelSettings,
          accent = SignalCrimson,
          testTag = "quick_action_roles",
          onClick = onOpenRolesTab
        )
      )
    }
    if (roleAccess.canDirectEditDuties && onAutoAssignWeek != null) {
      add(
        QuickTileSpec(
          title = "Auto-Assign Week",
          subtitle = if (isBusy) "Scheduling..." else "Fill empty slots",
          icon = Icons.Default.AutoFixHigh,
          accent = SignalEmerald,
          enabled = !isBusy,
          testTag = "quick_action_auto_assign_week",
          onClick = onAutoAssignWeek
        )
      )
    }
  }

  if (tiles.isEmpty()) return

  GlassTile(
    shape = RoundedCornerShape(24.dp),
    topColor = GlassSurfaceBright,
    bottomColor = GlassSurfaceMid,
    modifier = Modifier
      .fillMaxWidth()
      .testTag("supervisor_quick_actions_card")
  ) {
    Column(
      modifier = Modifier.padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
      Text(
        text = stringResource(R.string.dashboard_quick_actions_title),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold
      )

      tiles.chunked(2).forEach { rowTiles ->
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
          rowTiles.forEach { spec ->
            QuickCommandTile(
              title = spec.title,
              subtitle = spec.subtitle,
              icon = spec.icon,
              accent = spec.accent,
              badgeText = spec.badgeText,
              enabled = spec.enabled,
              onClick = spec.onClick,
              testTag = spec.testTag,
              modifier = Modifier.weight(1f)
            )
          }
          if (rowTiles.size == 1) {
            Spacer(modifier = Modifier.weight(1f))
          }
        }
      }
    }
  }
}

@Composable
private fun QuickCommandTile(
  title: String,
  subtitle: String,
  icon: ImageVector,
  accent: Color,
  onClick: () -> Unit,
  testTag: String,
  modifier: Modifier = Modifier,
  badgeText: String? = null,
  enabled: Boolean = true
) {
  Surface(
    shape = RoundedCornerShape(16.dp),
    color = GlassSurfaceSoft,
    border = BorderStroke(1.dp, accent.copy(alpha = 0.30f)),
    modifier = modifier
      .heightIn(min = 84.dp)
      .clickable(enabled = enabled, onClick = onClick)
      .testTag(testTag)
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
        GlassIconOrb(
          shape = RoundedCornerShape(12.dp),
          tintColor = accent.copy(alpha = 0.16f),
          modifier = Modifier.size(36.dp)
        ) {
          Icon(
            imageVector = icon,
            contentDescription = null,
            tint = accent,
            modifier = Modifier.size(19.dp)
          )
        }
        if (!badgeText.isNullOrBlank()) {
          GlassPillBadge(
            text = badgeText,
            tintColor = accent.copy(alpha = 0.22f),
            textColor = accent,
            borderColor = accent
          )
        }
      }
      Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
          text = title,
          style = MaterialTheme.typography.titleSmall,
          fontWeight = FontWeight.Bold,
          maxLines = 2
        )
        Text(
          text = subtitle,
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 2
        )
      }
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LiveActiveShiftSnapshotCard(
  liveSnapshot: LiveShiftStaffSnapshot,
  onOpenShiftsTab: () -> Unit
) {
  var expanded by remember { mutableStateOf(false) }
  val pmCount = liveSnapshot.pointsmanStaff.size
  val shunterCount = liveSnapshot.shunterStaff.size
  val shedCount = liveSnapshot.shedStaff.size
  val stationCount = liveSnapshot.activeStationStaff.size
  val generalCount = if (liveSnapshot.generalShiftActive) liveSnapshot.generalShiftStaff.size else 0
  val totalLive = liveSnapshot.currentShiftStaffMembers.size

  GlassTile(
    shape = RoundedCornerShape(24.dp),
    topColor = GlassSurfaceBright,
    bottomColor = GlassSurfaceMid,
    tintColor = SignalEmeraldContainer.copy(alpha = 0.14f),
    modifier = Modifier
      .fillMaxWidth()
      .testTag("supervisor_live_shift_snapshot_card")
  ) {
    Column(
      modifier = Modifier.padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .heightIn(min = 48.dp)
          .clickable { expanded = !expanded }
          .testTag("supervisor_live_shift_expand_toggle"),
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
            tintColor = SignalEmeraldContainer.copy(alpha = 0.72f),
            modifier = Modifier.size(38.dp)
          ) {
            Icon(
              imageVector = Icons.Default.AccessTime,
              contentDescription = null,
              tint = SignalEmerald,
              modifier = Modifier.size(20.dp)
            )
          }
          Column {
            Text(
              text = "On Duty Now · ${liveSnapshot.activeShift.code} ${liveSnapshot.activeShift.displayName}",
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold,
              maxLines = 2
            )
            Text(
              text = "${liveSnapshot.activeShiftWindowLabel} · Tap to ${if (expanded) "hide" else "inspect"} active roster",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
        }
        Icon(
          imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
          contentDescription = if (expanded) "Collapse live shift staff" else "Expand live shift staff",
          tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }

      FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
      ) {
        if (pmCount > 0) {
          GlassPillBadge(
            text = "PM: $pmCount",
            tintColor = SafetyAmberContainer.copy(alpha = 0.75f),
            textColor = SafetyAmberOnContainer,
            borderColor = SafetyAmber
          )
        }
        GlassPillBadge(
          text = "SHED: $shedCount",
          tintColor = SignalEmeraldContainer.copy(alpha = 0.75f),
          textColor = SignalEmeraldOnContainer,
          borderColor = SignalEmerald
        )
        GlassPillBadge(
          text = "${liveSnapshot.activeShift.stationArea?.shortCode ?: "STN"}: $stationCount",
          tintColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.75f),
          textColor = MaterialTheme.colorScheme.onPrimaryContainer,
          borderColor = RailwayCobalt
        )
        if (shunterCount > 0) {
          GlassPillBadge(
            text = "SHUNTER: $shunterCount",
            tintColor = GlassSurfaceSoft,
            textColor = MaterialTheme.colorScheme.onSurface,
            borderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)
          )
        }
        if (liveSnapshot.generalShiftActive) {
          GlassPillBadge(
            text = "08/17 GEN: $generalCount",
            tintColor = DepotTealContainer.copy(alpha = 0.75f),
            textColor = DepotTealOnContainer,
            borderColor = DepotTeal
          )
        }
      }

      AnimatedVisibility(visible = expanded) {
        Column(
          verticalArrangement = Arrangement.spacedBy(8.dp),
          modifier = Modifier.padding(top = 4.dp)
        ) {
          HorizontalDivider(color = GlassRimHighlight)
          if (totalLive == 0) {
            Text(
              text = "No personnel are currently assigned to the live ${liveSnapshot.activeShift.code} shift.",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          } else {
            if (liveSnapshot.pointsmanStaff.isNotEmpty()) {
              LiveAreaStaffLine(
                areaLabel = "Pointsman",
                names = liveSnapshot.pointsmanStaff.map { it.displayName }
              )
            }
            if (liveSnapshot.shedStaff.isNotEmpty()) {
              LiveAreaStaffLine(
                areaLabel = "Shed Duty",
                names = liveSnapshot.shedStaff.map { it.displayName }
              )
            }
            if (liveSnapshot.activeStationStaff.isNotEmpty()) {
              LiveAreaStaffLine(
                areaLabel = liveSnapshot.activeShift.stationArea?.label ?: "Station Duty",
                names = liveSnapshot.activeStationStaff.map { "${it.displayName} (${it.assignment.dutyAreaEnum.shortCode})" }
              )
            }
            if (liveSnapshot.shunterStaff.isNotEmpty()) {
              LiveAreaStaffLine(
                areaLabel = "Shunter",
                names = liveSnapshot.shunterStaff.map { it.displayName }
              )
            }
            if (liveSnapshot.generalShiftActive && liveSnapshot.generalShiftStaff.isNotEmpty()) {
              LiveAreaStaffLine(
                areaLabel = "08/17 General Shift",
                names = liveSnapshot.generalShiftStaff.map { it.displayName }
              )
            }
          }

          OutlinedButton(
            onClick = onOpenShiftsTab,
            modifier = Modifier
              .fillMaxWidth()
              .heightIn(min = 48.dp)
              .testTag("live_shift_open_roster_btn")
          ) {
            Icon(Icons.Default.CalendarMonth, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Edit Shift Roster")
          }
        }
      }
    }
  }
}

@Composable
private fun LiveAreaStaffLine(
  areaLabel: String,
  names: List<String>
) {
  Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
    Text(
      text = "$areaLabel (${names.size})",
      style = MaterialTheme.typography.labelMedium,
      fontWeight = FontWeight.Bold,
      color = MaterialTheme.colorScheme.primary
    )
    Text(
      text = names.joinToString(", "),
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurface
    )
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AdminUserGovernanceCard(
  userProfiles: List<DepotUserProfile>,
  totalStaffCount: Int,
  onOpenUsersTab: () -> Unit,
  onOpenStaffTab: () -> Unit,
  onOpenRolesTab: () -> Unit = {},
  onOpenAppUpdatesManager: () -> Unit = {},
  publishedAppVersionLabel: String = ""
) {
  val roleCounts = remember(userProfiles) {
    userProfiles.groupingBy { it.roleEnum }.eachCount()
  }
  val unlinkedTechs = remember(userProfiles) {
    userProfiles.count { it.roleEnum == UserRole.TECHNICIAN && it.staffId.isBlank() }
  }

  GlassTile(
    shape = RoundedCornerShape(24.dp),
    topColor = GlassSurfaceBright,
    bottomColor = GlassSurfaceMid,
    tintColor = RailwayCobalt.copy(alpha = 0.08f),
    modifier = Modifier
      .fillMaxWidth()
      .testTag("admin_governance_summary_card")
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
            tintColor = RailwayCobalt.copy(alpha = 0.16f),
            modifier = Modifier.size(38.dp)
          ) {
            Icon(
              imageVector = Icons.Default.ManageAccounts,
              contentDescription = null,
              tint = RailwayCobalt,
              modifier = Modifier.size(20.dp)
            )
          }
          Column {
            Text(
              text = "Admin Access & Roster Control",
              style = MaterialTheme.typography.titleMedium,
              fontWeight = FontWeight.Bold
            )
            Text(
              text = "${userProfiles.size} registered accounts · $totalStaffCount depot staff records",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
        }
        GlassPillBadge(
          text = "ADMIN",
          tintColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.78f),
          textColor = MaterialTheme.colorScheme.onPrimaryContainer,
          borderColor = RailwayCobalt
        )
      }

      FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
      ) {
        UserRole.entries.forEach { role ->
          val count = roleCounts[role] ?: 0
          if (count > 0) {
            GlassPillBadge(
              text = "${role.label.uppercase()}: $count",
              tintColor = GlassSurfaceSoft,
              textColor = MaterialTheme.colorScheme.onSurface,
              borderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
            )
          }
        }
      }

      if (unlinkedTechs > 0) {
        Surface(
          shape = RoundedCornerShape(14.dp),
          color = SafetyAmberContainer.copy(alpha = 0.55f),
          border = BorderStroke(1.dp, SafetyAmber.copy(alpha = 0.65f)),
          modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(onClick = onOpenUsersTab)
            .testTag("admin_unlinked_techs_alert")
        ) {
          Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            Icon(
              imageVector = Icons.Default.Warning,
              contentDescription = null,
              tint = SafetyAmber,
              modifier = Modifier.size(18.dp)
            )
            Text(
              text = "$unlinkedTechs technician ${if (unlinkedTechs == 1) "account is" else "accounts are"} not linked to a staff profile yet. Tap to link.",
              style = MaterialTheme.typography.bodySmall,
              fontWeight = FontWeight.SemiBold,
              color = SafetyAmberOnContainer
            )
          }
        }
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
      ) {
        Button(
          onClick = onOpenUsersTab,
          elevation = ButtonDefaults.buttonElevation(0.dp, 0.dp, 0.dp, 0.dp, 0.dp),
          colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent, contentColor = Color.White),
          shape = RoundedCornerShape(14.dp),
          contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
          modifier = Modifier
            .weight(1f)
            .heightIn(min = 48.dp)
            .liquidGlassAccent(shape = RoundedCornerShape(14.dp))
            .testTag("admin_dash_manage_users_btn")
        ) {
          Text(
            text = "Manage Users",
            maxLines = 2,
            textAlign = TextAlign.Center
          )
        }
        OutlinedButton(
          onClick = onOpenRolesTab,
          colors = ButtonDefaults.outlinedButtonColors(containerColor = GlassSurfaceSoft),
          border = BorderStroke(1.dp, SignalCrimson.copy(alpha = 0.45f)),
          shape = RoundedCornerShape(14.dp),
          contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
          modifier = Modifier
            .weight(1f)
            .heightIn(min = 48.dp)
            .testTag("admin_dash_roles_manager_btn")
        ) {
          Text(
            text = "Roles Manager",
            maxLines = 2,
            textAlign = TextAlign.Center
          )
        }
      }

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
      ) {
        OutlinedButton(
          onClick = onOpenStaffTab,
          colors = ButtonDefaults.outlinedButtonColors(containerColor = GlassSurfaceSoft),
          border = BorderStroke(1.dp, GlassRimHighlight),
          shape = RoundedCornerShape(14.dp),
          contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
          modifier = Modifier
            .weight(1f)
            .heightIn(min = 48.dp)
            .testTag("admin_dash_manage_staff_btn")
        ) {
          Text(
            text = "Staff Directory",
            maxLines = 2,
            textAlign = TextAlign.Center
          )
        }
        OutlinedButton(
          onClick = onOpenAppUpdatesManager,
          colors = ButtonDefaults.outlinedButtonColors(containerColor = GlassSurfaceSoft),
          border = BorderStroke(1.dp, DepotTeal.copy(alpha = 0.5f)),
          shape = RoundedCornerShape(14.dp),
          contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
          modifier = Modifier
            .weight(1f)
            .heightIn(min = 48.dp)
            .testTag("admin_dash_app_updates_btn")
        ) {
          Text(
            text = if (publishedAppVersionLabel.isNotBlank()) {
              "App Updates (v$publishedAppVersionLabel)"
            } else {
              "App Updates"
            },
            maxLines = 2,
            textAlign = TextAlign.Center
          )
        }
      }
    }
  }
}
