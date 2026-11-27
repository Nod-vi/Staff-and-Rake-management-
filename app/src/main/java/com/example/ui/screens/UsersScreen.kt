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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.ManageAccounts
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.auth.DepotRoleManager
import com.example.model.DepotUserProfile
import com.example.model.ResolvedRoleAccess
import com.example.model.RoleAccessPolicy
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
import com.example.ui.theme.SignalCrimsonContainer
import com.example.ui.theme.SignalCrimsonOnContainer
import com.example.ui.theme.SignalEmerald
import com.example.ui.theme.SignalEmeraldContainer
import com.example.ui.theme.SignalEmeraldOnContainer
import com.example.ui.theme.liquidGlassAccent
import com.example.ui.theme.liquidGlassSurface

private enum class UserFilterOption(val label: String, val role: UserRole?) {
  ALL("All", null),
  ADMIN("Admin", UserRole.ADMIN),
  SSE("SSE", UserRole.SSE),
  SUPERVISOR("Supervisor", UserRole.SUPERVISOR),
  JE("JE", UserRole.JE),
  SHIFT_INCHARGE("ShiftIncharge", UserRole.SHIFT_INCHARGE),
  STOREMATE("Storemate", UserRole.STOREMATE),
  TECHNICIAN("Technician", UserRole.TECHNICIAN)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun UsersScreen(
  userProfiles: List<DepotUserProfile>,
  staffList: List<StaffMember>,
  currentActorUid: String,
  isCloudMode: Boolean,
  userRole: UserRole = UserRole.ADMIN,
  roleAccess: ResolvedRoleAccess = RoleAccessPolicy.resolve(userRole, emptyMap()),
  allowTechnicianShare: Boolean = true,
  isBusy: Boolean,
  onToggleTechnicianShare: (Boolean) -> Unit = {},
  onCreateUser: (username: String, password: String, displayName: String, email: String, role: UserRole, staffId: String) -> Unit,
  onUpdateUser: (profile: DepotUserProfile, username: String, displayName: String, email: String, role: UserRole, staffId: String, newPassword: String?) -> Unit,
  onDeleteUser: (DepotUserProfile) -> Unit,
  onAssignUserRole: (DepotUserProfile, UserRole, String) -> Unit
) {
  val canManageUsers = roleAccess.canManageUserRoles
  val isAdminActor = roleAccess.isAdmin
  var searchQuery by remember { mutableStateOf("") }
  var selectedRoleFilter by remember { mutableStateOf(UserFilterOption.ALL) }
  var showCreateDialog by remember { mutableStateOf(false) }
  var editingUser by remember { mutableStateOf<DepotUserProfile?>(null) }
  var pendingDeleteUser by remember { mutableStateOf<DepotUserProfile?>(null) }

  val filteredUsers = remember(userProfiles, searchQuery, selectedRoleFilter) {
    userProfiles.filter { profile ->
      val matchesQuery = searchQuery.isBlank() ||
        profile.displayName.contains(searchQuery, ignoreCase = true) ||
        profile.username.contains(searchQuery, ignoreCase = true) ||
        profile.email.contains(searchQuery, ignoreCase = true)
      val matchesRole = selectedRoleFilter.role == null || profile.roleEnum == selectedRoleFilter.role
      matchesQuery && matchesRole
    }
  }

  val staffById = remember(staffList) { staffList.associateBy { it.id } }

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
      item(key = "users_header", contentType = "header") {
        GlassTile(
          shape = RoundedCornerShape(22.dp),
          topColor = GlassSurfaceBright,
          bottomColor = GlassSurfaceMid,
          tintColor = SignalCrimsonContainer.copy(alpha = 0.28f)
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
                tintColor = SignalCrimsonContainer.copy(alpha = 0.65f),
                modifier = Modifier.size(42.dp)
              ) {
                Icon(Icons.Default.ManageAccounts, contentDescription = null, tint = SignalCrimson)
              }
              Column(modifier = Modifier.weight(1f)) {
                Text(
                  text = if (canManageUsers) "User Accounts & Roles" else "User Accounts (Read-Only)",
                  style = MaterialTheme.typography.titleSmall,
                  fontWeight = FontWeight.Bold
                )
                Text(
                  text = if (canManageUsers) {
                    "Create, edit, or remove depot user logins and assign roles."
                  } else {
                    "View depot user accounts and assigned roles."
                  },
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant
                )
              }
            }

            if (canManageUsers) {
              Button(
                onClick = { showCreateDialog = true },
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
                  .testTag("create_user_button")
              ) {
                Icon(Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("New User", fontWeight = FontWeight.SemiBold)
              }
            }
          }
        }
      }

      item(key = "users_sync_mode_info", contentType = "sync_info") {
        GlassTile(
          shape = RoundedCornerShape(18.dp),
          topColor = GlassSurfaceBright,
          bottomColor = GlassSurfaceSoft,
          tintColor = if (isCloudMode) {
            SignalEmeraldContainer.copy(alpha = 0.45f)
          } else {
            SafetyAmberContainer.copy(alpha = 0.55f)
          }
        ) {
          Column(
            modifier = Modifier
              .fillMaxWidth()
              .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
          ) {
            Text(
              text = if (isCloudMode) {
                "Shared Cloud Sync Active"
              } else {
                "Offline Test Mode · Local Sandbox Only"
              },
              style = MaterialTheme.typography.labelLarge,
              fontWeight = FontWeight.Bold,
              color = if (isCloudMode) SignalEmeraldOnContainer else SafetyAmberOnContainer
            )
            Text(
              text = if (isCloudMode) {
                "Add users with their Google Email. When they tap Sign in with Google on their phone, they receive their assigned role and live shifts automatically."
              } else {
                "Changes made in this offline test login stay in device test storage only and never sync to the server or affect other users."
              },
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
        }
      }

      if (isAdminActor) {
        item(key = "users_technician_share_toggle", contentType = "settings_toggle") {
        GlassTile(
          shape = RoundedCornerShape(20.dp),
          topColor = GlassSurfaceBright,
          bottomColor = GlassSurfaceMid,
          tintColor = if (allowTechnicianShare) {
            SignalEmeraldContainer.copy(alpha = 0.25f)
          } else {
            SafetyAmberContainer.copy(alpha = 0.28f)
          }
        ) {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(horizontal = 14.dp, vertical = 12.dp),
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
                tintColor = if (allowTechnicianShare) {
                  SignalEmeraldContainer.copy(alpha = 0.7f)
                } else {
                  SafetyAmberContainer.copy(alpha = 0.7f)
                },
                modifier = Modifier.size(40.dp)
              ) {
                Icon(
                  imageVector = Icons.Default.Share,
                  contentDescription = null,
                  tint = if (allowTechnicianShare) SignalEmerald else SafetyAmber
                )
              }
              Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                FlowRow(
                  horizontalArrangement = Arrangement.spacedBy(6.dp),
                  verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                  Text(
                    text = "Technician Share Options",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                  )
                  GlassPillBadge(
                    text = if (allowTechnicianShare) "SHOWN" else "HIDDEN",
                    tintColor = if (allowTechnicianShare) {
                      SignalEmeraldContainer.copy(alpha = 0.75f)
                    } else {
                      SafetyAmberContainer.copy(alpha = 0.75f)
                    },
                    textColor = if (allowTechnicianShare) SignalEmeraldOnContainer else SafetyAmberOnContainer,
                    borderColor = if (allowTechnicianShare) SignalEmerald else SafetyAmber
                  )
                }
                Text(
                  text = if (allowTechnicianShare) {
                    "Technicians can see Share WhatsApp & PDF buttons in Duties."
                  } else {
                    "Share WhatsApp & PDF options are hidden for Technicians."
                  },
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant
                )
              }
            }
            Spacer(modifier = Modifier.width(8.dp))
            Switch(
              checked = allowTechnicianShare,
              onCheckedChange = onToggleTechnicianShare,
              enabled = !isBusy,
              modifier = Modifier.testTag("toggle_technician_share_switch")
            )
          }
        }
        }
      }

      item(key = "users_search_and_filter", contentType = "filters") {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
          GlassTile(
            shape = RoundedCornerShape(22.dp),
            topColor = GlassSurfaceBright,
            bottomColor = GlassSurfaceMid
          ) {
            OutlinedTextField(
              value = searchQuery,
              onValueChange = { searchQuery = it },
              placeholder = { Text("Search name, username, or email...") },
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
                .testTag("users_search_input")
            )
          }

          LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(UserFilterOption.entries) { option ->
              val isSelected = selectedRoleFilter == option
              val countText = if (option == UserFilterOption.ALL) {
                "All (${userProfiles.size})"
              } else {
                val c = userProfiles.count { it.roleEnum == option.role }
                "${option.label} ($c)"
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
                  .clickable { selectedRoleFilter = option }
                  .padding(horizontal = 14.dp, vertical = 10.dp)
                  .testTag("users_filter_${option.name.lowercase()}"),
                contentAlignment = Alignment.Center
              ) {
                Text(
                  text = countText,
                  style = MaterialTheme.typography.labelLarge,
                  color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface
                )
              }
            }
          }
        }
      }

      if (filteredUsers.isEmpty()) {
        item(key = "users_empty", contentType = "empty") {
          GlassTile(
            shape = RoundedCornerShape(22.dp),
            topColor = GlassSurfaceBright,
            bottomColor = GlassSurfaceMid,
            modifier = Modifier.fillMaxWidth()
          ) {
            Column(
              modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
              horizontalAlignment = Alignment.CenterHorizontally,
              verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
              Icon(
                imageVector = Icons.Default.Person,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(32.dp)
              )
              Text(
                text = "No matching user accounts",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
              )
              Text(
                text = "Tap New User to create a login account and assign a role.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
            }
          }
        }
      } else {
        items(
          items = filteredUsers,
          key = { it.uid },
          contentType = { "user_account_card" }
        ) { profile ->
          val isProtected = isProtectedAdmin(profile, currentActorUid)
          val linkedStaff = staffById[profile.staffId]

          UserAccountCard(
            profile = profile,
            linkedStaffName = linkedStaff?.let { "#${it.serialNo} ${it.name}" },
            isProtectedPrimaryAdmin = isProtected,
            canManageUsers = canManageUsers,
            isAdminActor = isAdminActor,
            isBusy = isBusy,
            onEdit = { editingUser = profile },
            onDelete = { pendingDeleteUser = profile },
            onSelectRole = { newRole -> onAssignUserRole(profile, newRole, profile.staffId) }
          )
        }
      }

      item(key = "users_bottom_spacer", contentType = "spacer") {
        Spacer(modifier = Modifier.height(16.dp))
      }
    }
  }

  if (showCreateDialog && canManageUsers) {
    UserAccountDialog(
      initialProfile = null,
      staffList = staffList,
      isCloudMode = isCloudMode,
      isProtectedPrimaryAdmin = false,
      isAdminActor = isAdminActor,
      onDismiss = { showCreateDialog = false },
      onSave = { username, password, displayName, email, role, staffId ->
        onCreateUser(username, password.orEmpty(), displayName, email, role, staffId)
        showCreateDialog = false
      }
    )
  }

  val targetEdit = editingUser
  if (targetEdit != null && canManageUsers) {
    UserAccountDialog(
      initialProfile = targetEdit,
      staffList = staffList,
      isCloudMode = isCloudMode,
      isProtectedPrimaryAdmin = isProtectedAdmin(targetEdit, currentActorUid),
      isAdminActor = isAdminActor,
      onDismiss = { editingUser = null },
      onSave = { username, newPassword, displayName, email, role, staffId ->
        onUpdateUser(targetEdit, username, displayName, email, role, staffId, newPassword)
        editingUser = null
      }
    )
  }

  val targetDelete = pendingDeleteUser
  if (targetDelete != null) {
    AlertDialog(
      onDismissRequest = { pendingDeleteUser = null },
      containerColor = GlassPopupSurface,
      title = { Text("Remove User Account?", fontWeight = FontWeight.Bold) },
      text = {
        val handle = targetDelete.username.ifBlank { targetDelete.email.ifBlank { targetDelete.uid } }
        Text("Delete ${targetDelete.displayName} (@$handle) from PPIO Roster? They will no longer be able to sign in.")
      },
      confirmButton = {
        TextButton(
          onClick = {
            onDeleteUser(targetDelete)
            pendingDeleteUser = null
          },
          modifier = Modifier
            .heightIn(min = 48.dp)
            .testTag("confirm_delete_user_button")
        ) {
          Text("Remove", color = SignalCrimson, fontWeight = FontWeight.Bold)
        }
      },
      dismissButton = {
        TextButton(
          onClick = { pendingDeleteUser = null },
          modifier = Modifier.heightIn(min = 48.dp)
        ) {
          Text("Cancel")
        }
      }
    )
  }
}

private fun isProtectedAdmin(profile: DepotUserProfile, currentActorUid: String): Boolean =
  profile.uid == currentActorUid ||
    profile.uid == DepotRoleManager.ADMIN_ACCOUNT_ID ||
    profile.email.equals(DepotRoleManager.ADMIN_EMAIL, ignoreCase = true) ||
    profile.username.equals(DepotRoleManager.ADMIN_USERNAME, ignoreCase = true)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun UserAccountCard(
  profile: DepotUserProfile,
  linkedStaffName: String?,
  isProtectedPrimaryAdmin: Boolean,
  canManageUsers: Boolean,
  isAdminActor: Boolean,
  isBusy: Boolean,
  onEdit: () -> Unit,
  onDelete: () -> Unit,
  onSelectRole: (UserRole) -> Unit
) {
  val activeRole = profile.roleEnum
  val canModifyThisUser = canManageUsers && (isAdminActor || activeRole != UserRole.ADMIN)
  val (roleTint, roleInk, roleBorder) = when (activeRole) {
    UserRole.ADMIN -> Triple(SignalCrimsonContainer, SignalCrimsonOnContainer, SignalCrimson)
    UserRole.SSE -> Triple(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer, RailwayCobalt)
    UserRole.SUPERVISOR -> Triple(DepotTealContainer, DepotTealOnContainer, DepotTeal)
    UserRole.JE -> Triple(MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.colorScheme.onTertiaryContainer, MaterialTheme.colorScheme.tertiary)
    UserRole.SHIFT_INCHARGE -> Triple(MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer, MaterialTheme.colorScheme.secondary)
    UserRole.STOREMATE -> Triple(SafetyAmberContainer, SafetyAmberOnContainer, SafetyAmber)
    UserRole.TECHNICIAN -> Triple(SignalEmeraldContainer, SignalEmeraldOnContainer, SignalEmerald)
  }

  GlassTile(
    shape = RoundedCornerShape(22.dp),
    topColor = GlassSurfaceBright,
    bottomColor = GlassSurfaceMid
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
        horizontalArrangement = Arrangement.SpaceBetween
      ) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(10.dp),
          modifier = Modifier.weight(1f)
        ) {
          GlassIconOrb(
            shape = RoundedCornerShape(12.dp),
            tintColor = roleTint.copy(alpha = 0.65f),
            modifier = Modifier.size(40.dp)
          ) {
            Icon(
              imageVector = Icons.Default.Person,
              contentDescription = null,
              tint = roleBorder
            )
          }
          Text(
            text = profile.displayName.ifBlank { "Depot User" },
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
          )
        }

        if (canModifyThisUser) {
          Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(
              onClick = onEdit,
              enabled = !isBusy,
              modifier = Modifier
                .size(48.dp)
                .testTag("edit_user_${profile.uid}")
            ) {
              Icon(
                imageVector = Icons.Default.Edit,
                contentDescription = "Edit ${profile.displayName}",
                tint = MaterialTheme.colorScheme.primary
              )
            }
            if (!isProtectedPrimaryAdmin) {
              IconButton(
                onClick = onDelete,
                enabled = !isBusy,
                modifier = Modifier
                  .size(48.dp)
                  .testTag("delete_user_${profile.uid}")
              ) {
                Icon(
                  imageVector = Icons.Default.Delete,
                  contentDescription = "Remove ${profile.displayName}",
                  tint = SignalCrimson
                )
              }
            }
          }
        }
      }

      val usernamePart = profile.username.takeIf { it.isNotBlank() }?.let { "@$it" }
      val subText = listOfNotNull(usernamePart, profile.email.takeIf { it.isNotBlank() })
        .joinToString(" · ")
      if (subText.isNotBlank()) {
        Text(
          text = subText,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 2,
          overflow = TextOverflow.Ellipsis
        )
      }

      FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
      ) {
        GlassPillBadge(
          text = activeRole.label.uppercase(),
          tintColor = roleTint.copy(alpha = 0.70f),
          textColor = roleInk,
          borderColor = roleBorder
        )
        if (!linkedStaffName.isNullOrBlank()) {
          GlassPillBadge(
            text = "STAFF: $linkedStaffName",
            tintColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
            textColor = MaterialTheme.colorScheme.onPrimaryContainer,
            borderColor = MaterialTheme.colorScheme.primary
          )
        }
        if (profile.isPendingCloudInvite) {
          GlassPillBadge(
            text = "PENDING GOOGLE SIGN-IN",
            tintColor = SafetyAmberContainer.copy(alpha = 0.78f),
            textColor = SafetyAmberOnContainer,
            borderColor = SafetyAmber
          )
        }
      }

      if (canModifyThisUser) {
        val assignableRoles = if (isAdminActor) UserRole.entries else UserRole.entries.filter { it != UserRole.ADMIN }
        FlowRow(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(6.dp),
          verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
          assignableRoles.forEach { roleOption ->
            val selected = activeRole == roleOption
            val enabled = !isBusy && (!isProtectedPrimaryAdmin || roleOption == UserRole.ADMIN)
            val accent = when (roleOption) {
              UserRole.ADMIN -> SignalCrimson
              UserRole.SSE -> RailwayCobalt
              UserRole.SUPERVISOR -> DepotTeal
              UserRole.JE -> MaterialTheme.colorScheme.tertiary
              UserRole.SHIFT_INCHARGE -> MaterialTheme.colorScheme.secondary
              UserRole.STOREMATE -> SafetyAmber
              UserRole.TECHNICIAN -> SignalEmerald
            }
            Box(
              modifier = (if (selected) {
                Modifier.liquidGlassAccent(
                  shape = RoundedCornerShape(12.dp),
                  baseColor = accent,
                  highlightColor = accent.copy(alpha = 0.85f)
                )
              } else {
                Modifier.liquidGlassSurface(
                  shape = RoundedCornerShape(12.dp),
                  topColor = GlassSurfaceBright,
                  bottomColor = GlassSurfaceSoft,
                  borderWidth = 1.dp
                )
              })
                .heightIn(min = 48.dp)
                .clickable(enabled = enabled) {
                  if (!selected) onSelectRole(roleOption)
                }
                .padding(horizontal = 10.dp, vertical = 6.dp)
                .testTag("assign_role_${profile.uid}_${roleOption.name.lowercase()}"),
              contentAlignment = Alignment.Center
            ) {
              Text(
                text = roleOption.label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = if (selected) Color.White else if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
              )
            }
          }
        }
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun UserAccountDialog(
  initialProfile: DepotUserProfile?,
  staffList: List<StaffMember>,
  isCloudMode: Boolean,
  isProtectedPrimaryAdmin: Boolean,
  isAdminActor: Boolean,
  onDismiss: () -> Unit,
  onSave: (
    username: String,
    password: String?,
    displayName: String,
    email: String,
    role: UserRole,
    staffId: String
  ) -> Unit
) {
  val isEdit = initialProfile != null
  var displayName by remember(initialProfile) { mutableStateOf(initialProfile?.displayName.orEmpty()) }
  var username by remember(initialProfile) {
    mutableStateOf(
      initialProfile?.username?.takeIf { it.isNotBlank() }
        ?: initialProfile?.uid?.takeIf { isEdit }.orEmpty()
    )
  }
  var email by remember(initialProfile) { mutableStateOf(initialProfile?.email.orEmpty()) }
  var password by remember(initialProfile) { mutableStateOf("") }
  var passwordVisible by remember { mutableStateOf(false) }
  var selectedRole by remember(initialProfile) { mutableStateOf(initialProfile?.roleEnum ?: UserRole.TECHNICIAN) }
  var selectedStaffId by remember(initialProfile) { mutableStateOf(initialProfile?.staffId.orEmpty()) }
  var staffMenuExpanded by remember { mutableStateOf(false) }
  var validationError by remember { mutableStateOf<String?>(null) }

  val selectedStaffLabel = remember(selectedStaffId, staffList) {
    if (selectedStaffId.isBlank()) {
      "None (Unlinked)"
    } else {
      staffList.firstOrNull { it.id == selectedStaffId }
        ?.let { "#${it.serialNo} ${it.name} (${it.designationEnum.label})" }
        ?: "None (Unlinked)"
    }
  }

  AlertDialog(
    onDismissRequest = onDismiss,
    containerColor = GlassPopupSurface,
    title = {
      Text(
        text = if (isEdit) "Edit User Account" else "Create New User",
        fontWeight = FontWeight.Bold
      )
    },
    text = {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
      ) {
        if (validationError != null) {
          Text(
            text = validationError!!,
            style = MaterialTheme.typography.bodySmall,
            color = SignalCrimson
          )
        }

        if (isCloudMode) {
          Text(
            text = "Enter the user's Google Email. When they tap Sign in with Google on their phone, they will automatically get this role and see live synced shifts.",
            style = MaterialTheme.typography.bodySmall,
            color = SignalEmeraldOnContainer
          )
        }

        OutlinedTextField(
          value = displayName,
          onValueChange = { displayName = it },
          label = { Text("Full Name") },
          singleLine = true,
          shape = RoundedCornerShape(14.dp),
          modifier = Modifier
            .fillMaxWidth()
            .testTag("user_dialog_name_input")
        )

        if (isCloudMode) {
          OutlinedTextField(
            value = email,
            onValueChange = { email = it.trim() },
            label = { Text("Google Email (required)") },
            placeholder = { Text("user@gmail.com") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
              .fillMaxWidth()
              .testTag("user_dialog_email_input")
          )

          OutlinedTextField(
            value = username,
            onValueChange = { username = it.replace(" ", "") },
            label = { Text("Short Handle (optional)") },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
              .fillMaxWidth()
              .testTag("user_dialog_username_input")
          )
        } else {
          OutlinedTextField(
            value = username,
            onValueChange = { username = it.replace(" ", "") },
            label = { Text("Username") },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
              .fillMaxWidth()
              .testTag("user_dialog_username_input")
          )

          OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Email (optional)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
              .fillMaxWidth()
              .testTag("user_dialog_email_input")
          )
        }

        if (!isCloudMode) {
          OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = {
              Text(if (isEdit) "Reset Password (leave blank to keep)" else "Password (min 4 chars)")
            },
            leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
            trailingIcon = {
              IconButton(
                onClick = { passwordVisible = !passwordVisible },
                modifier = Modifier.size(48.dp)
              ) {
                Icon(
                  imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                  contentDescription = if (passwordVisible) "Hide password" else "Show password"
                )
              }
            },
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
              .fillMaxWidth()
              .testTag("user_dialog_password_input")
          )
        }

        Text(
          text = "Assign Role",
          style = MaterialTheme.typography.labelLarge,
          fontWeight = FontWeight.SemiBold
        )
        val dialogAssignableRoles = if (isAdminActor) UserRole.entries else UserRole.entries.filter { it != UserRole.ADMIN }
        FlowRow(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(6.dp),
          verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
          dialogAssignableRoles.forEach { role ->
            val selected = selectedRole == role
            val enabled = !isProtectedPrimaryAdmin || role == UserRole.ADMIN
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
                .heightIn(min = 48.dp)
                .clickable(enabled = enabled) { selectedRole = role }
                .padding(horizontal = 10.dp, vertical = 6.dp)
                .testTag("user_dialog_role_${role.name.lowercase()}"),
              contentAlignment = Alignment.Center
            ) {
              Text(
                text = role.label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
              )
            }
          }
        }

        ExposedDropdownMenuBox(
          expanded = staffMenuExpanded,
          onExpandedChange = { staffMenuExpanded = it }
        ) {
          OutlinedTextField(
            value = selectedStaffLabel,
            onValueChange = {},
            readOnly = true,
            label = { Text("Link to Staff Profile (Optional)") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = staffMenuExpanded) },
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
              .fillMaxWidth()
              .menuAnchor(MenuAnchorType.PrimaryNotEditable)
          )
          ExposedDropdownMenu(
            expanded = staffMenuExpanded,
            onDismissRequest = { staffMenuExpanded = false }
          ) {
            DropdownMenuItem(
              text = { Text("None (Unlinked)") },
              onClick = {
                selectedStaffId = ""
                staffMenuExpanded = false
              }
            )
            staffList.forEach { member ->
              DropdownMenuItem(
                text = { Text("#${member.serialNo} ${member.name} (${member.designationEnum.label})") },
                onClick = {
                  selectedStaffId = member.id
                  staffMenuExpanded = false
                }
              )
            }
          }
        }
      }
    },
    confirmButton = {
      Button(
        onClick = {
          val cleanName = displayName.trim()
          val cleanEmail = email.trim().lowercase()
          val cleanUser = username.trim().ifBlank {
            if (isCloudMode && cleanEmail.contains("@")) {
              cleanEmail.substringBefore("@").replace(Regex("[^a-zA-Z0-9._-]"), "")
            } else {
              ""
            }
          }
          when {
            cleanName.isBlank() -> validationError = "Full name is required."
            isCloudMode && (!cleanEmail.contains("@") || !cleanEmail.contains(".") || cleanEmail.contains(" ")) ->
              validationError = "Enter a valid Google Email (e.g. name@gmail.com)."
            !isCloudMode && (cleanUser.length < 2 || cleanUser.contains(" ")) ->
              validationError = "Username must be at least 2 characters without spaces."
            !isCloudMode && !isEdit && password.length < 4 ->
              validationError = "Password must be at least 4 characters."
            !isCloudMode && isEdit && password.isNotBlank() && password.length < 4 ->
              validationError = "New password must be at least 4 characters."
            else -> onSave(
              cleanUser,
              password.takeIf { it.isNotBlank() },
              cleanName,
              cleanEmail,
              selectedRole,
              selectedStaffId
            )
          }
        },
        modifier = Modifier
          .heightIn(min = 48.dp)
          .testTag("user_dialog_save_button")
      ) {
        Text(if (isEdit) "Save Changes" else if (isCloudMode) "Invite User" else "Create User")
      }
    },
    dismissButton = {
      TextButton(
        onClick = onDismiss,
        modifier = Modifier.heightIn(min = 48.dp)
      ) {
        Text("Cancel")
      }
    }
  )
}
