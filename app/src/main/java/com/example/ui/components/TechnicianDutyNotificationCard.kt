package com.example.ui.components

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.NotificationsPaused
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.R
import com.example.notifications.ScheduledDutyReminderInfo
import com.example.notifications.TechnicianDutyNotifier
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
import com.example.ui.theme.SignalEmerald
import com.example.ui.theme.SignalEmeraldContainer
import com.example.ui.theme.SignalEmeraldOnContainer
import com.example.ui.theme.liquidGlassAccent
import com.example.ui.theme.liquidGlassSurface

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TechnicianDutyNotificationCard(
  enabled: Boolean,
  leadMinutes: Int,
  nextReminder: ScheduledDutyReminderInfo?,
  onToggleEnabled: (Boolean) -> Unit,
  onSelectLeadMinutes: (Int) -> Unit,
  onPermissionChanged: () -> Unit,
  onSendTestNotification: () -> Unit,
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  val lifecycleOwner = LocalLifecycleOwner.current

  var hasPermission by remember(context) {
    mutableStateOf(TechnicianDutyNotifier.hasNotificationPermission(context))
  }
  var permissionDeniedOnce by remember { mutableStateOf(false) }
  var pendingTestAfterGrant by remember { mutableStateOf(false) }

  DisposableEffect(lifecycleOwner, context) {
    val observer = LifecycleEventObserver { _, event ->
      if (event == Lifecycle.Event.ON_RESUME) {
        val current = TechnicianDutyNotifier.hasNotificationPermission(context)
        if (current != hasPermission) {
          hasPermission = current
          onPermissionChanged()
        }
      }
    }
    lifecycleOwner.lifecycle.addObserver(observer)
    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
  }

  val permissionLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.RequestPermission()
  ) { granted ->
    val effectiveGranted = granted && TechnicianDutyNotifier.hasNotificationPermission(context)
    hasPermission = effectiveGranted
    permissionDeniedOnce = !effectiveGranted
    if (effectiveGranted) {
      if (!enabled) {
        onToggleEnabled(true)
      }
      onPermissionChanged()
      if (pendingTestAfterGrant) {
        pendingTestAfterGrant = false
        onSendTestNotification()
      }
    } else {
      pendingTestAfterGrant = false
    }
  }

  val openNotificationSettings = {
    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
      putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
    }
    runCatching { context.startActivity(intent) }
  }

  val requestOrOpenSettings = { runTestAfter: Boolean ->
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !permissionDeniedOnce) {
      pendingTestAfterGrant = runTestAfter
      permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    } else {
      openNotificationSettings()
    }
  }

  val isActive = enabled && hasPermission
  val needsPermission = enabled && !hasPermission

  val (badgeText, badgeTint, badgeInk, badgeBorder) = when {
    isActive -> QuadStyle("ACTIVE", SignalEmeraldContainer.copy(alpha = 0.75f), SignalEmeraldOnContainer, SignalEmerald)
    needsPermission -> QuadStyle("ALLOW ALERTS", SafetyAmberContainer.copy(alpha = 0.78f), SafetyAmberOnContainer, SafetyAmber)
    else -> QuadStyle("PAUSED", GlassSurfaceSoft, MaterialTheme.colorScheme.onSurfaceVariant, GlassRimHighlight)
  }

  val switchContentDescription = stringResource(R.string.tech_duty_notif_enable_cd)

  GlassTile(
    shape = RoundedCornerShape(24.dp),
    topColor = GlassSurfaceBright,
    bottomColor = GlassSurfaceMid,
    tintColor = when {
      isActive -> DepotTeal.copy(alpha = 0.08f)
      needsPermission -> SafetyAmber.copy(alpha = 0.10f)
      else -> Color.Transparent
    },
    modifier = modifier
      .fillMaxWidth()
      .testTag("tech_duty_notifications_card")
  ) {
    Column(
      modifier = Modifier.padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
      // Header row with icon, title, status pill, and toggle switch
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
              isActive -> DepotTealContainer.copy(alpha = 0.75f)
              needsPermission -> SafetyAmberContainer.copy(alpha = 0.75f)
              else -> GlassSurfaceSoft
            },
            modifier = Modifier.size(42.dp)
          ) {
            Icon(
              imageVector = when {
                isActive -> Icons.Default.NotificationsActive
                needsPermission -> Icons.Default.NotificationsPaused
                else -> Icons.Default.NotificationsOff
              },
              contentDescription = null,
              tint = when {
                isActive -> DepotTeal
                needsPermission -> SafetyAmber
                else -> MaterialTheme.colorScheme.onSurfaceVariant
              }
            )
          }
          Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            FlowRow(
              verticalArrangement = Arrangement.spacedBy(4.dp),
              horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
              Text(
                text = stringResource(R.string.tech_duty_notif_card_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 2
              )
              GlassPillBadge(
                text = badgeText,
                tintColor = badgeTint,
                textColor = badgeInk,
                borderColor = badgeBorder
              )
            }
            Text(
              text = stringResource(R.string.tech_duty_notif_card_subtitle),
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              maxLines = 2,
              overflow = TextOverflow.Ellipsis
            )
          }
        }

        Switch(
          checked = enabled,
          onCheckedChange = { checked ->
            if (checked && !hasPermission) {
              onToggleEnabled(true)
              requestOrOpenSettings(false)
            } else {
              onToggleEnabled(checked)
            }
          },
          modifier = Modifier
            .heightIn(min = 48.dp)
            .semantics { contentDescription = switchContentDescription }
            .testTag("tech_duty_notifications_switch")
        )
      }

      // Permission notice if notifications are toggled on but OS permission is missing
      if (needsPermission) {
        Surface(
          color = SafetyAmberContainer.copy(alpha = 0.65f),
          shape = RoundedCornerShape(14.dp),
          modifier = Modifier.fillMaxWidth()
        ) {
          Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            Text(
              text = if (permissionDeniedOnce) {
                "Notifications are currently blocked by Android. Open Notification Settings to receive instant duty alerts and shift reminders."
              } else {
                "Allow notifications so you receive instant alerts when new duties are assigned and reminders before your shift starts."
              },
              style = MaterialTheme.typography.bodySmall,
              color = SafetyAmberOnContainer,
              fontWeight = FontWeight.Medium
            )
            Button(
              onClick = { requestOrOpenSettings(false) },
              elevation = ButtonDefaults.buttonElevation(0.dp, 0.dp, 0.dp, 0.dp, 0.dp),
              colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent, contentColor = Color.White),
              shape = RoundedCornerShape(12.dp),
              modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .liquidGlassAccent(shape = RoundedCornerShape(12.dp))
                .testTag("tech_duty_allow_notifications_button")
            ) {
              Icon(
                imageVector = if (permissionDeniedOnce) Icons.Default.Settings else Icons.Default.NotificationsActive,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
              )
              Spacer(modifier = Modifier.width(8.dp))
              Text(
                text = if (permissionDeniedOnce) {
                  stringResource(R.string.tech_duty_notif_open_settings)
                } else {
                  stringResource(R.string.tech_duty_notif_allow_permission)
                },
                fontWeight = FontWeight.SemiBold
              )
            }
          }
        }
      }

      if (enabled) {
        HorizontalDivider(color = GlassRimHighlight)

        // Lead time selector chips (30 min / 1 hr / 2 hr)
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
          Text(
            text = stringResource(R.string.tech_duty_notif_lead_label),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            TechnicianDutyNotifier.ALLOWED_LEAD_MINUTES.forEach { minutes ->
              val selected = leadMinutes == minutes
              val label = TechnicianDutyNotifier.formatLeadLabel(minutes)
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
                  .clickable { onSelectLeadMinutes(minutes) }
                  .padding(horizontal = 8.dp, vertical = 10.dp)
                  .testTag("tech_reminder_lead_$minutes"),
                contentAlignment = Alignment.Center
              ) {
                Text(
                  text = label,
                  style = MaterialTheme.typography.labelLarge,
                  fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                  color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface,
                  textAlign = TextAlign.Center,
                  maxLines = 2
                )
              }
            }
          }
        }

        // Next scheduled reminder preview row
        Surface(
          color = GlassSurfaceSoft,
          shape = RoundedCornerShape(14.dp),
          modifier = Modifier
            .fillMaxWidth()
            .testTag("tech_next_reminder_preview")
        ) {
          Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
          ) {
            Icon(
              imageVector = Icons.Default.Alarm,
              contentDescription = null,
              tint = if (nextReminder?.isReminderScheduled == true) RailwayCobalt else MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.size(20.dp)
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
              if (nextReminder != null) {
                Text(
                  text = "Next shift: ${nextReminder.shiftType.code} ${nextReminder.shiftType.displayName} · ${nextReminder.dutyArea.label}",
                  style = MaterialTheme.typography.bodySmall,
                  fontWeight = FontWeight.SemiBold,
                  color = MaterialTheme.colorScheme.onSurface,
                  maxLines = 2
                )
                val reminderSubtitle = when {
                  nextReminder.isReminderScheduled && nextReminder.reminderTime != null ->
                    "Reminder set for ${TechnicianDutyNotifier.formatDateTimeLabel(nextReminder.reminderTime)}"
                  !hasPermission ->
                    "Starts ${TechnicianDutyNotifier.formatDateTimeLabel(nextReminder.shiftStart)} · Grant permission to arm alarm"
                  else ->
                    "Starts ${TechnicianDutyNotifier.formatDateTimeLabel(nextReminder.shiftStart)} · Within ${TechnicianDutyNotifier.formatLeadLabel(leadMinutes)}"
                }
                Text(
                  text = reminderSubtitle,
                  style = MaterialTheme.typography.labelSmall,
                  color = if (nextReminder.isReminderScheduled) DepotTealOnContainer else MaterialTheme.colorScheme.onSurfaceVariant
                )
              } else {
                Text(
                  text = "Standing by for new duty assignments",
                  style = MaterialTheme.typography.bodySmall,
                  fontWeight = FontWeight.SemiBold,
                  color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                  text = "You will be notified automatically as soon as your Supervisor publishes your next shift.",
                  style = MaterialTheme.typography.labelSmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant
                )
              }
            }
          }
        }

        // Send Test Alert button
        OutlinedButton(
          onClick = {
            if (hasPermission) {
              onSendTestNotification()
            } else {
              requestOrOpenSettings(true)
            }
          },
          colors = ButtonDefaults.outlinedButtonColors(containerColor = GlassSurfaceSoft),
          border = BorderStroke(1.dp, GlassRimHighlight),
          shape = RoundedCornerShape(14.dp),
          modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .testTag("tech_test_duty_notification_button")
        ) {
          Icon(
            imageVector = Icons.Default.Send,
            contentDescription = null,
            modifier = Modifier.size(18.dp)
          )
          Spacer(modifier = Modifier.width(8.dp))
          Text(
            text = stringResource(R.string.tech_duty_notif_test_button),
            fontWeight = FontWeight.SemiBold
          )
        }
      }
    }
  }
}

private data class QuadStyle(
  val text: String,
  val tint: Color,
  val ink: Color,
  val border: Color
)
