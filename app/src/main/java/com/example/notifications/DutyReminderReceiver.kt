package com.example.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Non-exported BroadcastReceiver triggered by AlarmManager.setAndAllowWhileIdle
 * to display scheduled pre-shift duty reminders for Technicians.
 */
class DutyReminderReceiver : BroadcastReceiver() {
  override fun onReceive(context: Context, intent: Intent) {
    if (!TechnicianDutyNotifier.isEnabled(context)) return

    val title = intent.getStringExtra(TechnicianDutyNotifier.EXTRA_REMINDER_TITLE)
      ?.takeIf { it.isNotBlank() }
      ?: return
    val body = intent.getStringExtra(TechnicianDutyNotifier.EXTRA_REMINDER_BODY).orEmpty()
    val bigText = intent.getStringExtra(TechnicianDutyNotifier.EXTRA_REMINDER_BIG_TEXT)
      ?.takeIf { it.isNotBlank() }
      ?: body
    val notificationId = intent.getIntExtra(
      TechnicianDutyNotifier.EXTRA_REMINDER_NOTIFICATION_ID,
      50_001
    )

    TechnicianDutyNotifier.postDutyNotification(
      context = context,
      channelId = TechnicianDutyNotifier.CHANNEL_DUTY_REMINDERS,
      notificationId = notificationId,
      title = title,
      shortBody = body,
      bigText = bigText
    )
  }
}
