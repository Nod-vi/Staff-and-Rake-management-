package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.unit.dp
import com.example.model.DepotMenuItem
import com.example.model.ResolvedRoleAccess
import com.example.model.RoleAccessPolicy
import com.example.model.StaffMember
import com.example.model.UserRole
import com.example.ui.theme.GlassPillBadge
import com.example.ui.theme.GlassPopupSurface
import com.example.ui.theme.SignalCrimson

@Composable
fun UserDetailsDialog(
  displayName: String,
  username: String?,
  email: String,
  role: UserRole,
  authMethod: String,
  linkedStaff: StaffMember?,
  phoneNumber1: String = "",
  phoneNumber2: String = "",
  onDismiss: () -> Unit
) {
  AlertDialog(
    onDismissRequest = onDismiss,
    containerColor = GlassPopupSurface,
    title = { Text("User Details", fontWeight = FontWeight.Bold) },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        DetailLine(label = "Name", value = displayName)
        if (!username.isNullOrBlank()) {
          DetailLine(label = "Username", value = "@$username")
        }
        if (email.isNotBlank()) {
          DetailLine(label = "Email", value = email)
        }
        if (phoneNumber1.isNotBlank()) {
          DetailLine(label = "Phone Number 1", value = phoneNumber1)
        }
        if (phoneNumber2.isNotBlank()) {
          DetailLine(label = "Phone Number 2", value = phoneNumber2)
        }
        DetailLine(label = "Assigned Role", value = role.label)
        DetailLine(label = "Sign-In Method", value = authMethod)
        if (linkedStaff != null) {
          DetailLine(
            label = "Linked Roster Profile",
            value = "#${linkedStaff.serialNo} ${linkedStaff.name} (${linkedStaff.designationEnum.label})"
          )
        }
      }
    },
    confirmButton = {
      TextButton(
        onClick = onDismiss,
        modifier = Modifier.heightIn(min = 48.dp)
      ) {
        Text("Close")
      }
    }
  )
}

@Composable
private fun DetailLine(label: String, value: String) {
  Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
    Text(
      text = label,
      style = MaterialTheme.typography.labelSmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Text(
      text = value,
      style = MaterialTheme.typography.bodyMedium,
      fontWeight = FontWeight.SemiBold,
      color = MaterialTheme.colorScheme.onSurface
    )
  }
}

@Composable
fun ChangePasswordDialog(
  isBusy: Boolean,
  onChangePassword: (
    currentPassword: String,
    newPassword: String,
    confirmPassword: String,
    onResult: (Boolean, String) -> Unit
  ) -> Unit,
  onDismiss: () -> Unit
) {
  var currentPassword by remember { mutableStateOf("") }
  var newPassword by remember { mutableStateOf("") }
  var confirmPassword by remember { mutableStateOf("") }
  var showPasswords by remember { mutableStateOf(false) }
  var statusError by remember { mutableStateOf<String?>(null) }

  AlertDialog(
    onDismissRequest = onDismiss,
    containerColor = GlassPopupSurface,
    title = { Text("Change Password", fontWeight = FontWeight.Bold) },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (statusError != null) {
          Text(
            text = statusError!!,
            style = MaterialTheme.typography.bodySmall,
            color = SignalCrimson
          )
        }
        val visual = if (showPasswords) VisualTransformation.None else PasswordVisualTransformation()
        val toggleButton: @Composable () -> Unit = {
          IconButton(
            onClick = { showPasswords = !showPasswords },
            modifier = Modifier.size(48.dp)
          ) {
            Icon(
              imageVector = if (showPasswords) Icons.Default.VisibilityOff else Icons.Default.Visibility,
              contentDescription = if (showPasswords) "Hide passwords" else "Show passwords"
            )
          }
        }

        OutlinedTextField(
          value = currentPassword,
          onValueChange = {
            currentPassword = it
            statusError = null
          },
          label = { Text("Current Password") },
          singleLine = true,
          visualTransformation = visual,
          trailingIcon = toggleButton,
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
          shape = RoundedCornerShape(14.dp),
          modifier = Modifier
            .fillMaxWidth()
            .testTag("change_pwd_current_input")
        )

        OutlinedTextField(
          value = newPassword,
          onValueChange = {
            newPassword = it
            statusError = null
          },
          label = { Text("New Password (min 6 chars)") },
          singleLine = true,
          visualTransformation = visual,
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
          shape = RoundedCornerShape(14.dp),
          modifier = Modifier
            .fillMaxWidth()
            .testTag("change_pwd_new_input")
        )

        OutlinedTextField(
          value = confirmPassword,
          onValueChange = {
            confirmPassword = it
            statusError = null
          },
          label = { Text("Confirm New Password") },
          singleLine = true,
          visualTransformation = visual,
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
          shape = RoundedCornerShape(14.dp),
          modifier = Modifier
            .fillMaxWidth()
            .testTag("change_pwd_confirm_input")
        )
      }
    },
    confirmButton = {
      Button(
        onClick = {
          onChangePassword(currentPassword, newPassword, confirmPassword) { ok, message ->
            if (ok) {
              onDismiss()
            } else {
              statusError = message
            }
          }
        },
        enabled = !isBusy,
        modifier = Modifier
          .heightIn(min = 48.dp)
          .testTag("confirm_change_password_button")
      ) {
        Text("Update Password")
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

@Composable
fun RolePermissionsDialog(
  role: UserRole,
  badgeTint: Color,
  badgeInk: Color,
  badgeBorder: Color,
  roleAccess: ResolvedRoleAccess = RoleAccessPolicy.resolve(role, emptyMap()),
  onDismiss: () -> Unit
) {
  val capabilities = remember(role, roleAccess) {
    buildList {
      if (roleAccess.canAccessRolesManager) {
        add("Roles Manager: Full Control — Configure per-role menu access & levels")
      }
      DepotMenuItem.entries.forEach { menu ->
        val level = roleAccess.accessFor(menu)
        if (level != com.example.model.MenuAccessLevel.NONE) {
          add("${menu.label}: ${level.label} — ${menu.levelHint(level)}")
        }
      }
    }
  }

  AlertDialog(
    onDismissRequest = onDismiss,
    containerColor = GlassPopupSurface,
    title = { Text("Role & Permissions", fontWeight = FontWeight.Bold) },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        GlassPillBadge(
          text = role.label.uppercase(),
          tintColor = badgeTint,
          textColor = badgeInk,
          borderColor = badgeBorder
        )
        capabilities.forEach { cap ->
          Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.Top
          ) {
            Icon(
              imageVector = Icons.Default.CheckCircle,
              contentDescription = null,
              tint = badgeBorder,
              modifier = Modifier
                .padding(top = 2.dp)
                .size(16.dp)
            )
            Text(
              text = cap,
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurface
            )
          }
        }
      }
    },
    confirmButton = {
      TextButton(
        onClick = onDismiss,
        modifier = Modifier.heightIn(min = 48.dp)
      ) {
        Text("Close")
      }
    }
  )
}
