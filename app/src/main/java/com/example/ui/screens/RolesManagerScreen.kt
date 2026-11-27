package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.EventBusy
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.ManageAccounts
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Train
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.outlined.FactCheck
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.model.DepotMenuItem
import com.example.model.DepotSettings
import com.example.model.MenuAccessLevel
import com.example.model.RoleAccessPolicy
import com.example.model.UserRole
import com.example.ui.theme.DepotTeal
import com.example.ui.theme.DepotTealContainer
import com.example.ui.theme.DepotTealOnContainer
import com.example.ui.theme.GlassSurfaceBright
import com.example.ui.theme.GlassSurfaceMid
import com.example.ui.theme.GlassSurfaceSoft
import com.example.ui.theme.NavyInk
import com.example.ui.theme.RailwayCobalt
import com.example.ui.theme.RailwayCobaltContainer
import com.example.ui.theme.RailwayCobaltOnContainer
import com.example.ui.theme.SafetyAmber
import com.example.ui.theme.SafetyAmberContainer
import com.example.ui.theme.SafetyAmberOnContainer
import com.example.ui.theme.SignalCrimson
import com.example.ui.theme.SignalCrimsonContainer
import com.example.ui.theme.SignalCrimsonOnContainer
import com.example.ui.theme.SignalEmerald
import com.example.ui.theme.SignalEmeraldContainer
import com.example.ui.theme.SignalEmeraldOnContainer
import com.example.ui.theme.SteelSecondaryInk
import com.example.ui.theme.GlassIconOrb
import com.example.ui.theme.GlassPillBadge
import com.example.ui.theme.GlassTile

private val ROLE_MANAGER_ORDER: List<UserRole> = listOf(
  UserRole.SSE,
  UserRole.JE,
  UserRole.SUPERVISOR,
  UserRole.SHIFT_INCHARGE,
  UserRole.STOREMATE,
  UserRole.TECHNICIAN,
  UserRole.ADMIN
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RolesManagerScreen(
  userRole: UserRole,
  depotSettings: DepotSettings,
  isBusy: Boolean,
  onSaveRoleAccess: (UserRole, Map<DepotMenuItem, MenuAccessLevel>) -> Unit,
  onResetRoleAccess: (UserRole) -> Unit,
  modifier: Modifier = Modifier
) {
  if (userRole != UserRole.ADMIN) {
    Box(
      modifier = modifier
        .fillMaxSize()
        .padding(20.dp),
      contentAlignment = Alignment.Center
    ) {
      GlassTile(
        modifier = Modifier
          .fillMaxWidth()
          .widthIn(max = 520.dp),
        tintColor = SignalCrimsonContainer.copy(alpha = 0.25f)
      ) {
        Column(
          modifier = Modifier.padding(24.dp),
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
          GlassIconOrb(
            modifier = Modifier.size(56.dp),
            tintColor = SignalCrimsonContainer.copy(alpha = 0.7f)
          ) {
            Icon(
              imageVector = Icons.Filled.Lock,
              contentDescription = null,
              tint = SignalCrimsonOnContainer,
              modifier = Modifier.size(28.dp)
            )
          }
          Text(
            text = stringResource(R.string.roles_manager_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = NavyInk
          )
          Text(
            text = stringResource(R.string.roles_manager_restricted),
            style = MaterialTheme.typography.bodyMedium,
            color = SteelSecondaryInk
          )
        }
      }
    }
    return
  }

  var selectedRole by remember { mutableStateOf(UserRole.SSE) }
  val persistedForSelected = remember(selectedRole, depotSettings.roleMenuAccess) {
    RoleAccessPolicy.resolve(selectedRole, depotSettings.roleMenuAccess).levels
  }
  val draftLevels = remember { mutableStateMapOf<DepotMenuItem, MenuAccessLevel>() }
  val lastEnabledLevels = remember { mutableStateMapOf<DepotMenuItem, MenuAccessLevel>() }

  LaunchedEffect(selectedRole, persistedForSelected) {
    draftLevels.clear()
    lastEnabledLevels.clear()
    DepotMenuItem.entries.forEach { menu ->
      val level = persistedForSelected[menu] ?: RoleAccessPolicy.defaultAccess(selectedRole, menu)
      draftLevels[menu] = level
      if (level != MenuAccessLevel.NONE) {
        lastEnabledLevels[menu] = level
      }
    }
  }

  val isAdminSelected = selectedRole == UserRole.ADMIN
  val hasUnsavedChanges = !isAdminSelected && DepotMenuItem.entries.any { menu ->
    val draft = draftLevels[menu] ?: persistedForSelected[menu]
    draft != persistedForSelected[menu]
  }
  val hasCustomOverrides = !isAdminSelected && depotSettings.roleMenuAccess.containsKey(selectedRole.name)

  val enabledCount = DepotMenuItem.entries.count { (draftLevels[it] ?: MenuAccessLevel.NONE).isAccessible }
  val fullCount = DepotMenuItem.entries.count { draftLevels[it] == MenuAccessLevel.FULL_CONTROL }
  val approvalCount = DepotMenuItem.entries.count { draftLevels[it] == MenuAccessLevel.EDIT_WITH_APPROVAL }
  val readOnlyCount = DepotMenuItem.entries.count { draftLevels[it] == MenuAccessLevel.READ_ONLY }

  Box(
    modifier = modifier.fillMaxSize(),
    contentAlignment = Alignment.TopCenter
  ) {
    LazyColumn(
      modifier = Modifier
        .fillMaxSize()
        .widthIn(max = 720.dp)
        .testTag("roles_manager_list"),
      contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 120.dp),
      verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
      // Hero Header Card
      item(key = "roles_header") {
        GlassTile(
          modifier = Modifier.fillMaxWidth(),
          tintColor = RailwayCobaltContainer.copy(alpha = 0.24f)
        ) {
          Column(
            modifier = Modifier
              .fillMaxWidth()
              .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
          ) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
              GlassIconOrb(
                modifier = Modifier.size(48.dp),
                tintColor = RailwayCobaltContainer.copy(alpha = 0.72f)
              ) {
                Icon(
                  imageVector = Icons.Filled.AdminPanelSettings,
                  contentDescription = null,
                  tint = RailwayCobaltOnContainer,
                  modifier = Modifier.size(26.dp)
                )
              }
              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text = stringResource(R.string.roles_manager_title),
                  style = MaterialTheme.typography.titleLarge,
                  fontWeight = FontWeight.Bold,
                  color = NavyInk
                )
                Text(
                  text = stringResource(R.string.roles_manager_subtitle),
                  style = MaterialTheme.typography.bodySmall,
                  color = SteelSecondaryInk
                )
              }
            }

            // Legend Pills for the 3 levels
            FlowRow(
              horizontalArrangement = Arrangement.spacedBy(8.dp),
              verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
              GlassPillBadge(
                text = "Read Only",
                tintColor = RailwayCobaltContainer.copy(alpha = 0.6f),
                textColor = RailwayCobaltOnContainer
              )
              GlassPillBadge(
                text = "Editable with Approval",
                tintColor = SafetyAmberContainer.copy(alpha = 0.7f),
                textColor = SafetyAmberOnContainer
              )
              GlassPillBadge(
                text = "Full Control",
                tintColor = SignalEmeraldContainer.copy(alpha = 0.7f),
                textColor = SignalEmeraldOnContainer
              )
            }
          }
        }
      }

      // Role Selector Card
      item(key = "role_selector") {
        GlassTile(
          modifier = Modifier.fillMaxWidth(),
          topColor = GlassSurfaceBright,
          bottomColor = GlassSurfaceMid
        ) {
          Column(
            modifier = Modifier
              .fillMaxWidth()
              .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
          ) {
            Row(
              modifier = Modifier.fillMaxWidth(),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.SpaceBetween
            ) {
              Text(
                text = "Select Role to Configure",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = NavyInk
              )
              if (hasCustomOverrides) {
                GlassPillBadge(
                  text = "Customized",
                  tintColor = DepotTealContainer.copy(alpha = 0.75f),
                  textColor = DepotTealOnContainer
                )
              } else {
                GlassPillBadge(
                  text = "Default",
                  tintColor = GlassSurfaceSoft,
                  textColor = SteelSecondaryInk
                )
              }
            }

            FlowRow(
              modifier = Modifier.fillMaxWidth(),
              horizontalArrangement = Arrangement.spacedBy(8.dp),
              verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
              ROLE_MANAGER_ORDER.forEach { role ->
                val isSelected = selectedRole == role
                val isCustom = role != UserRole.ADMIN && depotSettings.roleMenuAccess.containsKey(role.name)
                RoleSelectorChip(
                  role = role,
                  selected = isSelected,
                  isCustomized = isCustom,
                  onClick = { selectedRole = role }
                )
              }
            }

            // Active Role Summary & Actions
            Surface(
              modifier = Modifier.fillMaxWidth(),
              shape = RoundedCornerShape(16.dp),
              color = GlassSurfaceSoft.copy(alpha = 0.72f),
              border = BorderStroke(1.dp, Color.White.copy(alpha = 0.65f))
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
                  Column(modifier = Modifier.weight(1f)) {
                    Text(
                      text = roleDisplayHeading(selectedRole),
                      style = MaterialTheme.typography.titleSmall,
                      fontWeight = FontWeight.Bold,
                      color = NavyInk
                    )
                    Text(
                      text = "$enabledCount/${DepotMenuItem.entries.size} menus enabled · $fullCount Full · $approvalCount Approval · $readOnlyCount Read Only",
                      style = MaterialTheme.typography.labelSmall,
                      color = SteelSecondaryInk
                    )
                  }
                  if (!isAdminSelected) {
                    OutlinedButton(
                      onClick = { onResetRoleAccess(selectedRole) },
                      enabled = !isBusy && (hasCustomOverrides || hasUnsavedChanges),
                      contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                      modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("roles_reset_button")
                    ) {
                      Icon(
                        imageVector = Icons.Filled.RestartAlt,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                      )
                      Spacer(modifier = Modifier.width(4.dp))
                      Text(
                        text = stringResource(R.string.roles_manager_reset_button),
                        style = MaterialTheme.typography.labelMedium
                      )
                    }
                  }
                }

                if (isAdminSelected) {
                  Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                  ) {
                    Icon(
                      imageVector = Icons.Filled.Lock,
                      contentDescription = null,
                      tint = SignalEmeraldOnContainer,
                      modifier = Modifier.size(16.dp)
                    )
                    Text(
                      text = stringResource(R.string.roles_manager_admin_locked),
                      style = MaterialTheme.typography.bodySmall,
                      color = SignalEmeraldOnContainer,
                      fontWeight = FontWeight.Medium
                    )
                  }
                }
              }
            }
          }
        }
      }

      // Unsaved Changes Save Bar (right above menu cards for immediate reachability)
      if (hasUnsavedChanges) {
        item(key = "unsaved_bar") {
          GlassTile(
            modifier = Modifier.fillMaxWidth(),
            tintColor = SignalEmeraldContainer.copy(alpha = 0.45f)
          ) {
            Row(
              modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text = "Unsaved changes for ${selectedRole.label}",
                  style = MaterialTheme.typography.labelLarge,
                  fontWeight = FontWeight.Bold,
                  color = NavyInk
                )
                Text(
                  text = "Applies live across all ${selectedRole.label} devices",
                  style = MaterialTheme.typography.labelSmall,
                  color = SteelSecondaryInk
                )
              }
              OutlinedButton(
                onClick = {
                  DepotMenuItem.entries.forEach { menu ->
                    draftLevels[menu] = persistedForSelected[menu] ?: RoleAccessPolicy.defaultAccess(selectedRole, menu)
                  }
                },
                enabled = !isBusy,
                modifier = Modifier
                  .heightIn(min = 48.dp)
                  .testTag("roles_discard_button")
              ) {
                Text("Discard")
              }
              Button(
                onClick = {
                  val snapshot = DepotMenuItem.entries.associateWith { menu ->
                    draftLevels[menu] ?: persistedForSelected[menu] ?: RoleAccessPolicy.defaultAccess(selectedRole, menu)
                  }
                  onSaveRoleAccess(selectedRole, snapshot)
                },
                enabled = !isBusy,
                colors = ButtonDefaults.buttonColors(
                  containerColor = RailwayCobalt,
                  contentColor = Color.White
                ),
                modifier = Modifier
                  .heightIn(min = 48.dp)
                  .testTag("roles_save_button")
              ) {
                Icon(
                  imageVector = Icons.Filled.Save,
                  contentDescription = null,
                  modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(stringResource(R.string.roles_manager_save_button))
              }
            }
          }
        }
      }

      // Per-Menu Access Level Cards
      items(
        items = DepotMenuItem.entries,
        key = { it.name }
      ) { menuItem ->
        val currentLevel = if (isAdminSelected) {
          MenuAccessLevel.FULL_CONTROL
        } else {
          draftLevels[menuItem] ?: persistedForSelected[menuItem] ?: RoleAccessPolicy.defaultAccess(selectedRole, menuItem)
        }
        MenuAccessCard(
          menuItem = menuItem,
          level = currentLevel,
          locked = isAdminSelected,
          onToggleEnabled = { enabled ->
            if (!isAdminSelected) {
              if (enabled) {
                val restored = lastEnabledLevels[menuItem]
                  ?.takeIf { it != MenuAccessLevel.NONE }
                  ?: RoleAccessPolicy.defaultAccess(selectedRole, menuItem)
                    .takeIf { it != MenuAccessLevel.NONE }
                  ?: MenuAccessLevel.READ_ONLY
                draftLevels[menuItem] = restored
              } else {
                if (currentLevel != MenuAccessLevel.NONE) {
                  lastEnabledLevels[menuItem] = currentLevel
                }
                draftLevels[menuItem] = MenuAccessLevel.NONE
              }
            }
          },
          onSelectLevel = { chosen ->
            if (!isAdminSelected) {
              draftLevels[menuItem] = chosen
              if (chosen != MenuAccessLevel.NONE) {
                lastEnabledLevels[menuItem] = chosen
              }
            }
          }
        )
      }

      // Bottom Save Action when changes exist (convenient after scrolling to bottom)
      if (hasUnsavedChanges) {
        item(key = "bottom_save_action") {
          Button(
            onClick = {
              val snapshot = DepotMenuItem.entries.associateWith { menu ->
                draftLevels[menu] ?: persistedForSelected[menu] ?: RoleAccessPolicy.defaultAccess(selectedRole, menu)
              }
              onSaveRoleAccess(selectedRole, snapshot)
            },
            enabled = !isBusy,
            colors = ButtonDefaults.buttonColors(
              containerColor = RailwayCobalt,
              contentColor = Color.White
            ),
            modifier = Modifier
              .fillMaxWidth()
              .heightIn(min = 52.dp)
              .testTag("roles_save_bottom_button"),
            shape = RoundedCornerShape(16.dp)
          ) {
            Icon(
              imageVector = Icons.Filled.Save,
              contentDescription = null,
              modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
              text = "${stringResource(R.string.roles_manager_save_button)} for ${selectedRole.label}",
              style = MaterialTheme.typography.titleSmall,
              fontWeight = FontWeight.SemiBold
            )
          }
        }
      }
    }
  }
}

@Composable
private fun RoleSelectorChip(
  role: UserRole,
  selected: Boolean,
  isCustomized: Boolean,
  onClick: () -> Unit
) {
  val bgColor = when {
    selected -> RailwayCobalt
    else -> GlassSurfaceBright.copy(alpha = 0.85f)
  }
  val contentColor = if (selected) Color.White else NavyInk
  val borderColor = when {
    selected -> RailwayCobalt
    isCustomized -> DepotTeal.copy(alpha = 0.6f)
    else -> Color.White.copy(alpha = 0.7f)
  }

  Surface(
    modifier = Modifier
      .heightIn(min = 48.dp)
      .clip(RoundedCornerShape(14.dp))
      .clickable(role = Role.RadioButton, onClick = onClick)
      .testTag("role_chip_${role.name.lowercase()}"),
    shape = RoundedCornerShape(14.dp),
    color = bgColor,
    border = BorderStroke(1.2.dp, borderColor)
  ) {
    Row(
      modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
      if (role == UserRole.ADMIN) {
        Icon(
          imageVector = Icons.Filled.Lock,
          contentDescription = null,
          tint = contentColor,
          modifier = Modifier.size(14.dp)
        )
      } else if (selected) {
        Icon(
          imageVector = Icons.Filled.Check,
          contentDescription = null,
          tint = contentColor,
          modifier = Modifier.size(14.dp)
        )
      }
      Text(
        text = role.label,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = contentColor
      )
      if (isCustomized && !selected) {
        Box(
          modifier = Modifier
            .size(7.dp)
            .clip(RoundedCornerShape(50))
            .background(DepotTeal)
        )
      }
    }
  }
}

@Composable
private fun MenuAccessCard(
  menuItem: DepotMenuItem,
  level: MenuAccessLevel,
  locked: Boolean,
  onToggleEnabled: (Boolean) -> Unit,
  onSelectLevel: (MenuAccessLevel) -> Unit
) {
  val isEnabled = level.isAccessible
  val cardTint = when (level) {
    MenuAccessLevel.NONE -> Color.Transparent
    MenuAccessLevel.READ_ONLY -> RailwayCobaltContainer.copy(alpha = 0.14f)
    MenuAccessLevel.EDIT_WITH_APPROVAL -> SafetyAmberContainer.copy(alpha = 0.22f)
    MenuAccessLevel.FULL_CONTROL -> SignalEmeraldContainer.copy(alpha = 0.22f)
  }

  GlassTile(
    modifier = Modifier
      .fillMaxWidth()
      .testTag("role_menu_card_${menuItem.name.lowercase()}"),
    topColor = if (isEnabled) GlassSurfaceBright else GlassSurfaceMid,
    bottomColor = GlassSurfaceSoft,
    tintColor = cardTint
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
        horizontalArrangement = Arrangement.spacedBy(12.dp)
      ) {
        GlassIconOrb(
          modifier = Modifier.size(42.dp),
          tintColor = levelBadgeBg(level)
        ) {
          Icon(
            imageVector = iconForMenuItem(menuItem),
            contentDescription = menuItem.label,
            tint = levelBadgeFg(level),
            modifier = Modifier.size(22.dp)
          )
        }
        Column(modifier = Modifier.weight(1f)) {
          Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            Text(
              text = menuItem.label,
              style = MaterialTheme.typography.titleSmall,
              fontWeight = FontWeight.Bold,
              color = NavyInk,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis
            )
            GlassPillBadge(
              text = level.shortLabel,
              tintColor = levelBadgeBg(level),
              textColor = levelBadgeFg(level),
              maxLines = 1
            )
          }
          Text(
            text = menuItem.subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = SteelSecondaryInk,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
          )
        }
        Switch(
          checked = isEnabled,
          onCheckedChange = { if (!locked) onToggleEnabled(it) },
          enabled = !locked,
          colors = SwitchDefaults.colors(
            checkedThumbColor = Color.White,
            checkedTrackColor = RailwayCobalt
          ),
          modifier = Modifier.testTag("role_menu_toggle_${menuItem.name.lowercase()}")
        )
      }

      if (isEnabled) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
          MenuAccessLevel.activeLevels.forEach { option ->
            val chosen = level == option
            AccessLevelOptionPill(
              level = option,
              selected = chosen,
              enabled = !locked,
              onClick = { onSelectLevel(option) },
              modifier = Modifier
                .weight(1f)
                .testTag("role_level_${menuItem.name.lowercase()}_${option.name.lowercase()}")
            )
          }
        }
      }

      Text(
        text = menuItem.levelHint(level),
        style = MaterialTheme.typography.labelSmall,
        color = if (isEnabled) NavyInk.copy(alpha = 0.78f) else SteelSecondaryInk
      )

      if (menuItem == DepotMenuItem.USERS && !locked && level == MenuAccessLevel.FULL_CONTROL) {
        Text(
          text = "Security policy: Non-Admin roles can only manage non-Admin accounts and cannot grant Admin privileges.",
          style = MaterialTheme.typography.labelSmall,
          color = SafetyAmberOnContainer,
          fontWeight = FontWeight.Medium
        )
      }
    }
  }
}

@Composable
private fun AccessLevelOptionPill(
  level: MenuAccessLevel,
  selected: Boolean,
  enabled: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier
) {
  val (selectedBg, selectedFg, icon) = when (level) {
    MenuAccessLevel.READ_ONLY -> Triple(RailwayCobalt, Color.White, Icons.Filled.Visibility)
    MenuAccessLevel.EDIT_WITH_APPROVAL -> Triple(SafetyAmber, NavyInk, Icons.Filled.EditNote)
    MenuAccessLevel.FULL_CONTROL -> Triple(SignalEmerald, Color.White, Icons.Filled.VerifiedUser)
    MenuAccessLevel.NONE -> Triple(SteelSecondaryInk, Color.White, Icons.Filled.Lock)
  }

  val container = if (selected) selectedBg else GlassSurfaceBright.copy(alpha = 0.8f)
  val content = if (selected) selectedFg else SteelSecondaryInk
  val border = if (selected) selectedBg else Color.White.copy(alpha = 0.75f)

  Surface(
    modifier = modifier
      .heightIn(min = 48.dp)
      .clip(RoundedCornerShape(12.dp))
      .clickable(enabled = enabled, role = Role.RadioButton, onClick = onClick),
    shape = RoundedCornerShape(12.dp),
    color = container,
    border = BorderStroke(1.dp, border)
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 6.dp, vertical = 7.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.Center
    ) {
      Icon(
        imageVector = icon,
        contentDescription = null,
        tint = content,
        modifier = Modifier.size(16.dp)
      )
      Spacer(modifier = Modifier.height(2.dp))
      Text(
        text = level.shortLabel,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
        color = content,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
      )
    }
  }
}

private fun iconForMenuItem(item: DepotMenuItem): ImageVector = when (item) {
  DepotMenuItem.DASHBOARD -> Icons.Filled.Dashboard
  DepotMenuItem.SCHEDULE -> Icons.Filled.CalendarMonth
  DepotMenuItem.DUTIES -> Icons.Outlined.FactCheck
  DepotMenuItem.LEAVE -> Icons.Filled.EventBusy
  DepotMenuItem.PERMISSIONS -> Icons.Filled.Schedule
  DepotMenuItem.RAKES -> Icons.Filled.Train
  DepotMenuItem.STAFF -> Icons.Filled.Groups
  DepotMenuItem.USERS -> Icons.Filled.ManageAccounts
  DepotMenuItem.AI -> Icons.Filled.AutoAwesome
}

private fun levelBadgeBg(level: MenuAccessLevel): Color = when (level) {
  MenuAccessLevel.NONE -> GlassSurfaceSoft
  MenuAccessLevel.READ_ONLY -> RailwayCobaltContainer.copy(alpha = 0.7f)
  MenuAccessLevel.EDIT_WITH_APPROVAL -> SafetyAmberContainer.copy(alpha = 0.8f)
  MenuAccessLevel.FULL_CONTROL -> SignalEmeraldContainer.copy(alpha = 0.75f)
}

private fun levelBadgeFg(level: MenuAccessLevel): Color = when (level) {
  MenuAccessLevel.NONE -> SteelSecondaryInk
  MenuAccessLevel.READ_ONLY -> RailwayCobaltOnContainer
  MenuAccessLevel.EDIT_WITH_APPROVAL -> SafetyAmberOnContainer
  MenuAccessLevel.FULL_CONTROL -> SignalEmeraldOnContainer
}

private fun roleDisplayHeading(role: UserRole): String = when (role) {
  UserRole.SSE -> "SSE · Senior Section Engineer"
  UserRole.JE -> "JE · Junior Engineer"
  UserRole.ADMIN -> "Admin · System Administrator"
  UserRole.SUPERVISOR -> "Supervisor · Depot Supervision"
  UserRole.SHIFT_INCHARGE -> "ShiftIncharge · Live Shift Control"
  UserRole.STOREMATE -> "Storemate · Roster Drafting"
  UserRole.TECHNICIAN -> "Technician · Depot Field Staff"
}
