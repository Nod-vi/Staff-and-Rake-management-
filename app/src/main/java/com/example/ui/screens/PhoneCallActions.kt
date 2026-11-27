package com.example.ui.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.PhoneInTalk
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.model.IMPORTANT_DEPOT_CONTACTS
import com.example.model.ImportantContact
import com.example.model.StaffMember
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

fun sanitizePhoneNumber(raw: String): String {
  val trimmed = raw.trim()
  if (trimmed.isEmpty()) return ""
  val sb = StringBuilder()
  trimmed.forEachIndexed { idx, ch ->
    if (ch.isDigit() || (ch == '+' && idx == 0)) {
      sb.append(ch)
    }
  }
  return sb.toString()
}

@Stable
class PhoneActionController(
  private val context: Context,
  private val requestCallPermission: (String) -> Unit
) {
  fun placeCall(rawPhone: String, label: String = "") {
    val sanitized = sanitizePhoneNumber(rawPhone)
    if (sanitized.length < 3) {
      Toast.makeText(context, "No valid phone number available.", Toast.LENGTH_SHORT).show()
      return
    }
    val hasPermission = ContextCompat.checkSelfPermission(
      context,
      Manifest.permission.CALL_PHONE
    ) == PackageManager.PERMISSION_GRANTED

    if (hasPermission) {
      launchDirectCallOrDial(context, sanitized, label)
    } else {
      requestCallPermission(sanitized)
    }
  }

  fun sendMessage(rawPhone: String) {
    val sanitized = sanitizePhoneNumber(rawPhone)
    if (sanitized.length < 3) {
      Toast.makeText(context, "No valid phone number available.", Toast.LENGTH_SHORT).show()
      return
    }
    val smsIntent = Intent(Intent.ACTION_SENDTO).apply {
      data = Uri.parse("smsto:$sanitized")
      addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    val launched = runCatching {
      context.startActivity(smsIntent)
      true
    }.getOrDefault(false)
    if (!launched) {
      val fallbackIntent = Intent(Intent.ACTION_VIEW, Uri.parse("sms:$sanitized")).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      }
      runCatching { context.startActivity(fallbackIntent) }
        .onFailure {
          Toast.makeText(context, "No messaging app available on this device.", Toast.LENGTH_SHORT).show()
        }
    }
  }
}

private fun launchDirectCallOrDial(context: Context, sanitizedNumber: String, label: String) {
  val callUri = Uri.parse("tel:$sanitizedNumber")
  val callIntent = Intent(Intent.ACTION_CALL, callUri).apply {
    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
  }
  val directStarted = runCatching {
    context.startActivity(callIntent)
    true
  }.getOrDefault(false)

  if (!directStarted) {
    launchDialerFallback(context, sanitizedNumber, label)
  }
}

private fun launchDialerFallback(context: Context, sanitizedNumber: String, label: String) {
  val dialIntent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$sanitizedNumber")).apply {
    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
  }
  runCatching { context.startActivity(dialIntent) }
    .onFailure {
      val suffix = if (label.isNotBlank()) " ($label)" else ""
      Toast.makeText(context, "Unable to place call$suffix on this device.", Toast.LENGTH_SHORT).show()
    }
}

@Composable
fun rememberPhoneActionController(): PhoneActionController {
  val context = LocalContext.current
  var pendingCallNumber by remember { mutableStateOf<String?>(null) }

  val callPermissionLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.RequestPermission()
  ) { granted ->
    val number = pendingCallNumber
    pendingCallNumber = null
    if (!number.isNullOrBlank()) {
      if (granted) {
        launchDirectCallOrDial(context, number, "")
      } else {
        Toast.makeText(
          context,
          "Phone permission denied — opening dialer with number ready.",
          Toast.LENGTH_SHORT
        ).show()
        launchDialerFallback(context, number, "")
      }
    }
  }

  return remember(context, callPermissionLauncher) {
    PhoneActionController(
      context = context,
      requestCallPermission = { number ->
        pendingCallNumber = number
        callPermissionLauncher.launch(Manifest.permission.CALL_PHONE)
      }
    )
  }
}

@Composable
fun StationStaffContactDialog(
  staff: StaffMember,
  activeStationBadge: String? = null,
  phoneActions: PhoneActionController,
  onDismiss: () -> Unit
) {
  val phones = remember(staff.phoneNumber, staff.secondaryPhoneNumber) { staff.availablePhones }
  val hasPhone = phones.isNotEmpty()

  AlertDialog(
    onDismissRequest = onDismiss,
    containerColor = GlassPopupSurface,
    title = {
      Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        val serialPrefix = staff.serialNo.takeIf { it > 0L }?.let { "#$it " } ?: ""
        Text(
          text = "$serialPrefix${staff.name}",
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold
        )
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
          Text(
            text = staff.designationEnum.label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
          GlassPillBadge(
            text = activeStationBadge ?: if (staff.stationDuty) "STATION STAFF" else "DUTY STAFF",
            tintColor = DepotTealContainer.copy(alpha = 0.75f),
            textColor = DepotTealOnContainer,
            borderColor = DepotTeal
          )
        }
      }
    },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (hasPhone) {
          phones.forEachIndexed { index, (label, number) ->
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
              Surface(
                shape = RoundedCornerShape(14.dp),
                color = SignalEmeraldContainer.copy(alpha = 0.35f),
                border = BorderStroke(1.dp, SignalEmerald.copy(alpha = 0.45f)),
                modifier = Modifier.fillMaxWidth()
              ) {
                Row(
                  modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                  Icon(
                    imageVector = Icons.Default.PhoneInTalk,
                    contentDescription = null,
                    tint = SignalEmerald
                  )
                  Column {
                    Text(
                      text = if (phones.size > 1) "$label Number" else "Contact Number",
                      style = MaterialTheme.typography.labelSmall,
                      color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                      text = number,
                      style = MaterialTheme.typography.titleMedium,
                      fontWeight = FontWeight.Bold,
                      color = SignalEmeraldOnContainer
                    )
                  }
                }
              }

              Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
              ) {
                Button(
                  onClick = {
                    onDismiss()
                    phoneActions.placeCall(number, staff.name)
                  },
                  colors = ButtonDefaults.buttonColors(
                    containerColor = SignalEmerald,
                    contentColor = Color.White
                  ),
                  shape = RoundedCornerShape(14.dp),
                  modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .testTag("contact_dialog_call_button_$index")
                ) {
                  Icon(Icons.Default.Call, contentDescription = null, modifier = Modifier.size(18.dp))
                  Spacer(modifier = Modifier.width(6.dp))
                  Text("Call", fontWeight = FontWeight.Bold)
                }

                OutlinedButton(
                  onClick = {
                    onDismiss()
                    phoneActions.sendMessage(number)
                  },
                  border = BorderStroke(1.dp, RailwayCobalt),
                  shape = RoundedCornerShape(14.dp),
                  modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .testTag("contact_dialog_message_button_$index")
                ) {
                  Icon(
                    Icons.Default.Sms,
                    contentDescription = null,
                    tint = RailwayCobalt,
                    modifier = Modifier.size(18.dp)
                  )
                  Spacer(modifier = Modifier.width(6.dp))
                  Text("Message", fontWeight = FontWeight.Bold, color = RailwayCobalt)
                }
              }
            }
          }
        } else {
          Surface(
            shape = RoundedCornerShape(14.dp),
            color = SafetyAmberContainer.copy(alpha = 0.55f),
            border = BorderStroke(1.dp, SafetyAmber.copy(alpha = 0.5f)),
            modifier = Modifier.fillMaxWidth()
          ) {
            Text(
              text = "No phone number saved for ${staff.name}. Admin can add up to two optional phone numbers in the Staff tab.",
              style = MaterialTheme.typography.bodySmall,
              color = SafetyAmberOnContainer,
              modifier = Modifier.padding(12.dp)
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ImportantPhoneNumbersCard(
  phoneActions: PhoneActionController,
  contacts: List<ImportantContact> = IMPORTANT_DEPOT_CONTACTS,
  canManageContacts: Boolean = false,
  isBusy: Boolean = false,
  onAddContact: (label: String, phoneNumber: String, category: String) -> Unit = { _, _, _ -> },
  onDeleteContact: (ImportantContact) -> Unit = {},
  modifier: Modifier = Modifier
) {
  var selectedContactForDialog by remember { mutableStateOf<ImportantContact?>(null) }
  var showAddContactDialog by remember { mutableStateOf(false) }
  var pendingDeleteContact by remember { mutableStateOf<ImportantContact?>(null) }

  GlassTile(
    shape = RoundedCornerShape(24.dp),
    topColor = GlassSurfaceBright,
    bottomColor = GlassSurfaceMid,
    modifier = modifier
      .fillMaxWidth()
      .testTag("important_phone_numbers_card")
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
          tintColor = SignalEmeraldContainer.copy(alpha = 0.7f),
          modifier = Modifier.size(40.dp)
        ) {
          Icon(
            imageVector = Icons.Default.PhoneInTalk,
            contentDescription = null,
            tint = SignalEmerald
          )
        }
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = "Important Phone Numbers",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
          )
          Text(
            text = if (canManageContacts) {
              "Railway Control, Panels & Station Masters · Admin can add or remove numbers"
            } else {
              "Railway Control, Panels & Station Masters · Tap Call to dial directly from app"
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
        if (canManageContacts) {
          Button(
            onClick = { showAddContactDialog = true },
            enabled = !isBusy,
            colors = ButtonDefaults.buttonColors(
              containerColor = SignalEmerald,
              contentColor = Color.White
            ),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
              .heightIn(min = 48.dp)
              .testTag("important_contact_add_button")
          ) {
            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("Add", fontWeight = FontWeight.Bold)
          }
        }
      }

      if (contacts.isEmpty()) {
        Surface(
          shape = RoundedCornerShape(14.dp),
          color = GlassSurfaceSoft,
          border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.22f)),
          modifier = Modifier.fillMaxWidth()
        ) {
          Text(
            text = if (canManageContacts) {
              "No important phone numbers saved. Tap Add above to add a contact."
            } else {
              "No important phone numbers configured yet."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(14.dp)
          )
        }
      } else {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
          contacts.forEachIndexed { index, contact ->
            Surface(
              shape = RoundedCornerShape(14.dp),
              color = GlassSurfaceSoft,
              border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.22f)),
              modifier = Modifier
                .fillMaxWidth()
                .clickable { selectedContactForDialog = contact }
                .testTag("important_contact_row_${contact.id}")
            ) {
              Row(
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
              ) {
                Column(
                  modifier = Modifier.weight(1f),
                  verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                  Text(
                    text = contact.label,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                  )
                  Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                  ) {
                    Text(
                      text = contact.phoneNumber,
                      style = MaterialTheme.typography.bodyMedium,
                      fontWeight = FontWeight.SemiBold,
                      color = RailwayCobalt
                    )
                    if (contact.category.isNotBlank()) {
                      Text(
                        text = "· ${contact.category}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2
                      )
                    }
                  }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                  IconButton(
                    onClick = { phoneActions.sendMessage(contact.phoneNumber) },
                    modifier = Modifier
                      .size(48.dp)
                      .testTag("important_msg_${contact.id}")
                  ) {
                    Icon(
                      imageVector = Icons.Default.Sms,
                      contentDescription = "Message ${contact.label}",
                      tint = RailwayCobalt
                    )
                  }
                  IconButton(
                    onClick = { phoneActions.placeCall(contact.phoneNumber, contact.label) },
                    modifier = Modifier
                      .size(48.dp)
                      .testTag("important_call_${contact.id}")
                  ) {
                    Box(
                      modifier = Modifier.size(34.dp),
                      contentAlignment = Alignment.Center
                    ) {
                      Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = SignalEmerald,
                        modifier = Modifier.size(34.dp)
                      ) {
                        Box(contentAlignment = Alignment.Center) {
                          Icon(
                            imageVector = Icons.Default.Call,
                            contentDescription = "Call ${contact.label}",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                          )
                        }
                      }
                    }
                  }
                  if (canManageContacts) {
                    IconButton(
                      onClick = { pendingDeleteContact = contact },
                      enabled = !isBusy,
                      modifier = Modifier
                        .size(48.dp)
                        .testTag("important_delete_${contact.id}")
                    ) {
                      Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Remove ${contact.label}",
                        tint = SignalCrimson
                      )
                    }
                  }
                }
              }
            }
            if (index < contacts.lastIndex) {
              HorizontalDivider(
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.08f)
              )
            }
          }
        }
      }
    }
  }

  val activeContact = selectedContactForDialog
  if (activeContact != null) {
    AlertDialog(
      onDismissRequest = { selectedContactForDialog = null },
      containerColor = GlassPopupSurface,
      title = {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
          Text(activeContact.label, fontWeight = FontWeight.Bold)
          Text(
            text = "${activeContact.category} · ${activeContact.phoneNumber}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
      },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
          ) {
            Button(
              onClick = {
                selectedContactForDialog = null
                phoneActions.placeCall(activeContact.phoneNumber, activeContact.label)
              },
              colors = ButtonDefaults.buttonColors(
                containerColor = SignalEmerald,
                contentColor = Color.White
              ),
              shape = RoundedCornerShape(14.dp),
              modifier = Modifier
                .weight(1f)
                .heightIn(min = 48.dp)
            ) {
              Icon(Icons.Default.Call, contentDescription = null, modifier = Modifier.size(18.dp))
              Spacer(modifier = Modifier.width(6.dp))
              Text("Call Now", fontWeight = FontWeight.Bold)
            }
            OutlinedButton(
              onClick = {
                selectedContactForDialog = null
                phoneActions.sendMessage(activeContact.phoneNumber)
              },
              border = BorderStroke(1.dp, RailwayCobalt),
              shape = RoundedCornerShape(14.dp),
              modifier = Modifier
                .weight(1f)
                .heightIn(min = 48.dp)
            ) {
              Icon(
                Icons.Default.Sms,
                contentDescription = null,
                tint = RailwayCobalt,
                modifier = Modifier.size(18.dp)
              )
              Spacer(modifier = Modifier.width(6.dp))
              Text("Message", fontWeight = FontWeight.Bold, color = RailwayCobalt)
            }
          }
          if (canManageContacts) {
            OutlinedButton(
              onClick = {
                val target = activeContact
                selectedContactForDialog = null
                pendingDeleteContact = target
              },
              enabled = !isBusy,
              border = BorderStroke(1.dp, SignalCrimson.copy(alpha = 0.65f)),
              shape = RoundedCornerShape(14.dp),
              modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .testTag("important_dialog_remove_button")
            ) {
              Icon(Icons.Default.Delete, contentDescription = null, tint = SignalCrimson, modifier = Modifier.size(18.dp))
              Spacer(modifier = Modifier.width(6.dp))
              Text("Remove Number", fontWeight = FontWeight.Bold, color = SignalCrimson)
            }
          }
        }
      },
      confirmButton = {
        TextButton(
          onClick = { selectedContactForDialog = null },
          modifier = Modifier.heightIn(min = 48.dp)
        ) {
          Text("Close")
        }
      }
    )
  }

  if (showAddContactDialog && canManageContacts) {
    AddImportantContactDialog(
      isBusy = isBusy,
      onDismiss = { showAddContactDialog = false },
      onConfirm = { label, phone, category ->
        onAddContact(label, phone, category)
        showAddContactDialog = false
      }
    )
  }

  val deleteTarget = pendingDeleteContact
  if (deleteTarget != null && canManageContacts) {
    AlertDialog(
      onDismissRequest = { pendingDeleteContact = null },
      containerColor = GlassPopupSurface,
      title = { Text("Remove Important Number?", fontWeight = FontWeight.Bold) },
      text = {
        Text(
          text = "Remove ${deleteTarget.label} (${deleteTarget.phoneNumber}) from Important Phone Numbers for all Shift Incharges and Supervisors?",
          style = MaterialTheme.typography.bodyMedium
        )
      },
      confirmButton = {
        Button(
          onClick = {
            onDeleteContact(deleteTarget)
            pendingDeleteContact = null
          },
          enabled = !isBusy,
          colors = ButtonDefaults.buttonColors(
            containerColor = SignalCrimson,
            contentColor = Color.White
          ),
          shape = RoundedCornerShape(12.dp),
          modifier = Modifier
            .heightIn(min = 48.dp)
            .testTag("important_delete_confirm_button")
        ) {
          Text("Remove", fontWeight = FontWeight.Bold)
        }
      },
      dismissButton = {
        TextButton(
          onClick = { pendingDeleteContact = null },
          modifier = Modifier.heightIn(min = 48.dp)
        ) {
          Text("Cancel")
        }
      }
    )
  }
}

private val IMPORTANT_CONTACT_CATEGORIES = listOf(
  "Control",
  "Station Master",
  "Panel",
  "Signal / RRI",
  "Power Control",
  "Traction Control",
  "Maintenance",
  "Emergency"
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AddImportantContactDialog(
  isBusy: Boolean,
  onDismiss: () -> Unit,
  onConfirm: (label: String, phoneNumber: String, category: String) -> Unit
) {
  var label by remember { mutableStateOf("") }
  var phone by remember { mutableStateOf("") }
  var category by remember { mutableStateOf("Control") }

  val cleanDigits = phone.filter { it.isDigit() }
  val isValid = label.trim().isNotBlank() && cleanDigits.length >= 3 && phone.trim().length <= 20

  AlertDialog(
    onDismissRequest = onDismiss,
    containerColor = GlassPopupSurface,
    title = {
      Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text("Add Important Number", fontWeight = FontWeight.Bold)
        Text(
          text = "Visible to Shift Incharge, Supervisors & Admin",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }
    },
    text = {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
      ) {
        OutlinedTextField(
          value = label,
          onValueChange = { if (it.length <= 80) label = it },
          label = { Text("Name / Desk Label") },
          placeholder = { Text("e.g. RRI KZJ or SS HYB") },
          singleLine = true,
          keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
          shape = RoundedCornerShape(14.dp),
          colors = OutlinedTextFieldDefaults.colors(
            unfocusedContainerColor = GlassSurfaceSoft,
            focusedContainerColor = GlassSurfaceBright
          ),
          modifier = Modifier
            .fillMaxWidth()
            .testTag("important_add_label_input")
        )
        OutlinedTextField(
          value = phone,
          onValueChange = { input ->
            val filtered = input.filter { it.isDigit() || it == '+' || it == '-' || it == ' ' }
            if (filtered.length <= 20) phone = filtered
          },
          label = { Text("Phone Number") },
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
            .testTag("important_add_phone_input")
        )
        Text(
          text = "Category",
          style = MaterialTheme.typography.labelLarge,
          fontWeight = FontWeight.SemiBold
        )
        FlowRow(
          horizontalArrangement = Arrangement.spacedBy(6.dp),
          verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
          IMPORTANT_CONTACT_CATEGORIES.forEach { preset ->
            FilterChip(
              selected = category.equals(preset, ignoreCase = true),
              onClick = { category = preset },
              label = { Text(preset, style = MaterialTheme.typography.labelSmall) }
            )
          }
        }
        OutlinedTextField(
          value = category,
          onValueChange = { if (it.length <= 40) category = it },
          label = { Text("Custom Category (Optional)") },
          singleLine = true,
          shape = RoundedCornerShape(14.dp),
          colors = OutlinedTextFieldDefaults.colors(
            unfocusedContainerColor = GlassSurfaceSoft,
            focusedContainerColor = GlassSurfaceBright
          ),
          modifier = Modifier
            .fillMaxWidth()
            .testTag("important_add_category_input")
        )
      }
    },
    confirmButton = {
      Button(
        onClick = { onConfirm(label.trim(), phone.trim(), category.trim().ifBlank { "Control" }) },
        enabled = isValid && !isBusy,
        colors = ButtonDefaults.buttonColors(
          containerColor = SignalEmerald,
          contentColor = Color.White
        ),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
          .heightIn(min = 48.dp)
          .testTag("important_add_save_button")
      ) {
        Text("Save Number", fontWeight = FontWeight.Bold)
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
