package com.example.notifications

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.model.DutyArea
import com.example.model.ShiftSlotAssignment
import com.example.model.ShiftType
import com.example.model.SlotRoleType
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.abs

data class ScheduledDutyReminderInfo(
  val assignment: ShiftSlotAssignment,
  val shiftType: ShiftType,
  val dutyArea: DutyArea,
  val shiftStart: LocalDateTime,
  val reminderTime: LocalDateTime?,
  val isReminderScheduled: Boolean,
  val upcomingCount: Int
)

/**
 * Play-policy compliant local notification & shift reminder coordinator for Technicians.
 *
 * - Uses standard POST_NOTIFICATIONS runtime permission (API 33+).
 * - Uses AlarmManager.setAndAllowWhileIdle (no restricted SCHEDULE_EXACT_ALARM / USE_EXACT_ALARM).
 * - Deduplicates live duty change alerts via persisted assignment signatures.
 * - Tapping any notification opens MainActivity directly on the Duties tab.
 */
object TechnicianDutyNotifier {
  const val CHANNEL_DUTY_ALERTS = "technician_duty_alerts"
  const val CHANNEL_DUTY_REMINDERS = "technician_duty_reminders"

  const val EXTRA_OPEN_TAB = "com.example.EXTRA_OPEN_TAB"
  const val TAB_DUTIES = "DUTIES"

  const val EXTRA_REMINDER_TITLE = "com.example.EXTRA_REMINDER_TITLE"
  const val EXTRA_REMINDER_BODY = "com.example.EXTRA_REMINDER_BODY"
  const val EXTRA_REMINDER_BIG_TEXT = "com.example.EXTRA_REMINDER_BIG_TEXT"
  const val EXTRA_REMINDER_NOTIFICATION_ID = "com.example.EXTRA_REMINDER_NOTIFICATION_ID"

  const val DEFAULT_LEAD_MINUTES = 60
  val ALLOWED_LEAD_MINUTES = listOf(30, 60, 120)

  private const val PREFS_NAME = "ppio_tech_duty_notifications"
  private const val KEY_ENABLED = "duty_notifications_enabled"
  private const val KEY_LEAD_MINUTES = "duty_reminder_lead_minutes"
  private const val KEY_SCHEDULED_CODES = "scheduled_alarm_request_codes"
  private const val PREFIX_NOTIFIED_SIGS = "notified_sigs_"
  private const val PREFIX_BASELINE_INIT = "baseline_init_"

  private const val TEST_NOTIFICATION_ID = 9001
  private const val SUMMARY_NOTIFICATION_ID = 9002
  private const val MAX_SCHEDULED_REMINDERS = 7

  private val dateLabelFormatter = DateTimeFormatter.ofPattern("EEE, dd MMM")
  private val dateTimeLabelFormatter = DateTimeFormatter.ofPattern("EEE, dd MMM · HH:mm")

  private fun prefs(context: Context) =
    context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

  fun isEnabled(context: Context): Boolean =
    prefs(context).getBoolean(KEY_ENABLED, true)

  fun setEnabled(context: Context, enabled: Boolean) {
    prefs(context).edit().putBoolean(KEY_ENABLED, enabled).apply()
    if (!enabled) {
      cancelAllScheduledReminders(context)
    }
  }

  fun getLeadMinutes(context: Context): Int {
    val stored = prefs(context).getInt(KEY_LEAD_MINUTES, DEFAULT_LEAD_MINUTES)
    return if (stored in ALLOWED_LEAD_MINUTES) stored else DEFAULT_LEAD_MINUTES
  }

  fun setLeadMinutes(context: Context, minutes: Int) {
    val safe = if (minutes in ALLOWED_LEAD_MINUTES) minutes else DEFAULT_LEAD_MINUTES
    prefs(context).edit().putInt(KEY_LEAD_MINUTES, safe).apply()
  }

  fun hasNotificationPermission(context: Context): Boolean {
    val runtimeGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.POST_NOTIFICATIONS
      ) == PackageManager.PERMISSION_GRANTED
    } else {
      true
    }
    return runtimeGranted && NotificationManagerCompat.from(context).areNotificationsEnabled()
  }

  fun createNotificationChannels(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
    val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
      ?: return

    val alertsChannel = NotificationChannel(
      CHANNEL_DUTY_ALERTS,
      context.getString(R.string.notif_channel_duty_alerts_name),
      NotificationManager.IMPORTANCE_HIGH
    ).apply {
      description = context.getString(R.string.notif_channel_duty_alerts_desc)
      enableVibration(true)
    }

    val remindersChannel = NotificationChannel(
      CHANNEL_DUTY_REMINDERS,
      context.getString(R.string.notif_channel_duty_reminders_name),
      NotificationManager.IMPORTANCE_HIGH
    ).apply {
      description = context.getString(R.string.notif_channel_duty_reminders_desc)
      enableVibration(true)
    }

    manager.createNotificationChannel(alertsChannel)
    manager.createNotificationChannel(remindersChannel)
  }

  fun shiftStartDateTime(assignment: ShiftSlotAssignment): LocalDateTime? {
    val startDate = runCatching { LocalDate.parse(assignment.date) }.getOrNull() ?: return null
    val startHour = when (ShiftType.fromRaw(assignment.shiftType)) {
      ShiftType.SHIFT_06_14 -> 6
      ShiftType.SHIFT_08_17 -> 8
      ShiftType.SHIFT_14_22 -> 14
      ShiftType.SHIFT_22_06 -> 22
    }
    return startDate.atTime(startHour, 0)
  }

  fun formatLeadLabel(minutes: Int): String = when (minutes) {
    30 -> "30 min"
    60 -> "1 hr"
    120 -> "2 hr"
    else -> "$minutes min"
  }

  fun formatDateTimeLabel(dateTime: LocalDateTime): String =
    dateTime.format(dateTimeLabelFormatter)

  /**
   * Evaluates upcoming duties for the signed-in technician:
   * 1. Posts instant notifications for newly assigned/updated upcoming shifts (deduplicated).
   * 2. Schedules pre-shift reminders via AlarmManager.setAndAllowWhileIdle.
   * 3. Returns info about the technician's next upcoming duty & scheduled reminder time for the UI.
   */
  fun syncTechnicianDutyNotifications(
    context: Context,
    staffId: String,
    staffName: String,
    assignments: List<ShiftSlotAssignment>,
    showShunterForTechnicians: Boolean
  ): ScheduledDutyReminderInfo? {
    val cleanStaffId = staffId.trim()
    if (cleanStaffId.isBlank()) {
      cancelAllScheduledReminders(context)
      return null
    }

    val appContext = context.applicationContext
    val now = LocalDateTime.now()
    val leadMinutes = getLeadMinutes(appContext)

    val upcoming = assignments
      .asSequence()
      .filter { it.staffId == cleanStaffId }
      .filter { it.roleTypeEnum != SlotRoleType.SHUNTER || showShunterForTechnicians }
      .mapNotNull { assignment ->
        val start = shiftStartDateTime(assignment) ?: return@mapNotNull null
        // Include shifts starting in the future or started within the last 15 minutes
        if (start.isAfter(now.minusMinutes(15))) {
          UpcomingDutyEntry(
            assignment = assignment,
            shiftType = ShiftType.fromRaw(assignment.shiftType),
            dutyArea = DutyArea.fromRaw(assignment.dutyArea),
            shiftStart = start,
            signature = buildSignature(assignment)
          )
        } else {
          null
        }
      }
      .sortedBy { it.shiftStart }
      .toList()

    val earliest = upcoming.firstOrNull()
    val enabled = isEnabled(appContext)
    val permitted = hasNotificationPermission(appContext)

    if (!enabled || !permitted) {
      cancelAllScheduledReminders(appContext)
      return earliest?.let {
        val candidateReminder = it.shiftStart.minusMinutes(leadMinutes.toLong())
        ScheduledDutyReminderInfo(
          assignment = it.assignment,
          shiftType = it.shiftType,
          dutyArea = it.dutyArea,
          shiftStart = it.shiftStart,
          reminderTime = candidateReminder.takeIf { time -> time.isAfter(now) },
          isReminderScheduled = false,
          upcomingCount = upcoming.size
        )
      }
    }

    createNotificationChannels(appContext)

    // 1. Live Duty Assignment / Update Alerts
    val sharedPrefs = prefs(appContext)
    val sigKey = "$PREFIX_NOTIFIED_SIGS$cleanStaffId"
    val baselineKey = "$PREFIX_BASELINE_INIT$cleanStaffId"
    val previousSigs = sharedPrefs.getStringSet(sigKey, emptySet())?.toSet() ?: emptySet()
    val hasBaseline = sharedPrefs.getBoolean(baselineKey, false)

    val currentUpcomingSigs = upcoming.map { it.signature }.toSet()
    val newlyChanged = upcoming.filter { it.signature !in previousSigs }

    if (newlyChanged.isNotEmpty()) {
      if (!hasBaseline) {
        // First time syncing on this device/profile: only alert if an upcoming duty is within 36 hours
        val imminent = newlyChanged.firstOrNull { !it.shiftStart.isAfter(now.plusHours(36)) }
        if (imminent != null) {
          postAssignmentAlert(
            context = appContext,
            entry = imminent,
            isUpdate = false,
            staffName = staffName
          )
        }
      } else {
        if (newlyChanged.size <= 2) {
          newlyChanged.forEach { entry ->
            val wasUpdated = previousSigs.any { it.startsWith("${entry.assignment.id}|") }
            postAssignmentAlert(
              context = appContext,
              entry = entry,
              isUpdate = wasUpdated,
              staffName = staffName
            )
          }
        } else {
          val first = newlyChanged.first()
          val firstDateLabel = first.shiftStart.format(dateLabelFormatter)
          val title = "${newlyChanged.size} New Duties Published"
          val shortBody = "Next: ${first.shiftType.code} ${first.dutyArea.label} on $firstDateLabel (${first.shiftType.timeWindow})."
          val bigText = buildString {
            append("${newlyChanged.size} upcoming depot shifts have been assigned or updated")
            if (staffName.isNotBlank()) append(" for $staffName")
            append(".\nNext duty: ${first.shiftType.displayName} (${first.shiftType.code}) at ${first.dutyArea.label} on $firstDateLabel (${first.shiftType.timeWindow}).\nTap to view your full schedule in Duties.")
          }
          postDutyNotification(
            context = appContext,
            channelId = CHANNEL_DUTY_ALERTS,
            notificationId = SUMMARY_NOTIFICATION_ID,
            title = title,
            shortBody = shortBody,
            bigText = bigText
          )
        }
      }
    }

    // Persist current upcoming signatures & mark baseline initialized
    sharedPrefs.edit()
      .putStringSet(sigKey, currentUpcomingSigs)
      .putBoolean(baselineKey, true)
      .apply()

    // 2. Schedule Pre-Shift Alarms via AlarmManager.setAndAllowWhileIdle
    cancelAllScheduledReminders(appContext)
    val alarmManager = appContext.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
    val newScheduledCodes = mutableSetOf<String>()
    var earliestReminderTime: LocalDateTime? = null
    var earliestReminderScheduled = false

    if (alarmManager != null) {
      val zoneId = ZoneId.systemDefault()
      upcoming.take(MAX_SCHEDULED_REMINDERS).forEachIndexed { idx, entry ->
        val triggerDateTime = entry.shiftStart.minusMinutes(leadMinutes.toLong())
        if (triggerDateTime.isAfter(now)) {
          val triggerMillis = triggerDateTime.atZone(zoneId).toInstant().toEpochMilli()
          val requestCode = requestCodeFor(entry.assignment.id, leadMinutes)
          val roleNote = if (entry.assignment.roleTypeEnum == SlotRoleType.POINTSMAN) " · Pointsman" else ""
          val title = "Shift in ${formatLeadLabel(leadMinutes)}: ${entry.shiftType.code} ${entry.shiftType.displayName}"
          val shortBody = "${entry.dutyArea.label}$roleNote · ${entry.shiftType.timeWindow}"
          val bigText = buildString {
            append("Your ${entry.shiftType.displayName} (${entry.shiftType.code}) starts at ${entry.shiftStart.format(dateTimeLabelFormatter)}.\n")
            append("Duty Area: ${entry.dutyArea.label}$roleNote (${entry.shiftType.timeWindow}).\n")
            append("Tap to open Tomorrow's Duties.")
          }

          val intent = Intent(appContext, DutyReminderReceiver::class.java).apply {
            putExtra(EXTRA_REMINDER_TITLE, title)
            putExtra(EXTRA_REMINDER_BODY, shortBody)
            putExtra(EXTRA_REMINDER_BIG_TEXT, bigText)
            putExtra(EXTRA_REMINDER_NOTIFICATION_ID, requestCode)
          }
          val pendingIntent = PendingIntent.getBroadcast(
            appContext,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
          )

          runCatching {
            alarmManager.setAndAllowWhileIdle(
              AlarmManager.RTC_WAKEUP,
              triggerMillis,
              pendingIntent
            )
            newScheduledCodes.add(requestCode.toString())
            if (idx == 0) {
              earliestReminderTime = triggerDateTime
              earliestReminderScheduled = true
            }
          }
        }
      }
    }

    sharedPrefs.edit()
      .putStringSet(KEY_SCHEDULED_CODES, newScheduledCodes)
      .apply()

    return earliest?.let {
      ScheduledDutyReminderInfo(
        assignment = it.assignment,
        shiftType = it.shiftType,
        dutyArea = it.dutyArea,
        shiftStart = it.shiftStart,
        reminderTime = earliestReminderTime,
        isReminderScheduled = earliestReminderScheduled,
        upcomingCount = upcoming.size
      )
    }
  }

  fun sendTestDutyNotification(
    context: Context,
    nextDutyInfo: ScheduledDutyReminderInfo?
  ): Boolean {
    val appContext = context.applicationContext
    if (!hasNotificationPermission(appContext)) return false
    createNotificationChannels(appContext)

    val title: String
    val shortBody: String
    val bigText: String

    if (nextDutyInfo != null) {
      val dateLabel = nextDutyInfo.shiftStart.format(dateLabelFormatter)
      val roleNote = if (nextDutyInfo.assignment.roleTypeEnum == SlotRoleType.POINTSMAN) " · Pointsman" else ""
      title = "Duty Alert Preview: ${nextDutyInfo.shiftType.code} ${nextDutyInfo.shiftType.displayName}"
      shortBody = "${nextDutyInfo.dutyArea.label}$roleNote · $dateLabel (${nextDutyInfo.shiftType.timeWindow})"
      bigText = "Your next scheduled duty is ${nextDutyInfo.shiftType.displayName} (${nextDutyInfo.shiftType.code}) at ${nextDutyInfo.dutyArea.label}$roleNote on $dateLabel (${nextDutyInfo.shiftType.timeWindow}). Tap to view your full schedule."
    } else {
      title = "Duty Alert Preview: 06/14 Morning Shift"
      shortBody = "Shed Duty · 06:00 – 14:00 (Sample Technician Alert)"
      bigText = "Duty notifications are active! You will be alerted whenever a new shift is assigned or updated, and reminded ${formatLeadLabel(getLeadMinutes(appContext))} before your shift starts. Tap to open Duties."
    }

    return postDutyNotification(
      context = appContext,
      channelId = CHANNEL_DUTY_ALERTS,
      notificationId = TEST_NOTIFICATION_ID,
      title = title,
      shortBody = shortBody,
      bigText = bigText
    )
  }

  @SuppressLint("MissingPermission")
  fun postDutyNotification(
    context: Context,
    channelId: String,
    notificationId: Int,
    title: String,
    shortBody: String,
    bigText: String
  ): Boolean {
    val appContext = context.applicationContext
    if (!hasNotificationPermission(appContext)) return false
    createNotificationChannels(appContext)

    val launchIntent = Intent(appContext, MainActivity::class.java).apply {
      flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
      putExtra(EXTRA_OPEN_TAB, TAB_DUTIES)
    }
    val contentIntent = PendingIntent.getActivity(
      appContext,
      notificationId,
      launchIntent,
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    val notification = NotificationCompat.Builder(appContext, channelId)
      .setSmallIcon(R.drawable.ic_duty_notification)
      .setContentTitle(title)
      .setContentText(shortBody)
      .setStyle(NotificationCompat.BigTextStyle().bigText(bigText))
      .setPriority(NotificationCompat.PRIORITY_HIGH)
      .setCategory(NotificationCompat.CATEGORY_REMINDER)
      .setAutoCancel(true)
      .setContentIntent(contentIntent)
      .build()

    return runCatching {
      NotificationManagerCompat.from(appContext).notify(notificationId, notification)
      true
    }.getOrDefault(false)
  }

  fun cancelAllScheduledReminders(context: Context) {
    val appContext = context.applicationContext
    val sharedPrefs = prefs(appContext)
    val codes = sharedPrefs.getStringSet(KEY_SCHEDULED_CODES, emptySet())?.toSet() ?: emptySet()
    val alarmManager = appContext.getSystemService(Context.ALARM_SERVICE) as? AlarmManager

    if (alarmManager != null) {
      codes.forEach { codeStr ->
        val code = codeStr.toIntOrNull() ?: return@forEach
        val intent = Intent(appContext, DutyReminderReceiver::class.java)
        val existing = PendingIntent.getBroadcast(
          appContext,
          code,
          intent,
          PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (existing != null) {
          alarmManager.cancel(existing)
          existing.cancel()
        }
      }
    }

    sharedPrefs.edit().remove(KEY_SCHEDULED_CODES).apply()
  }

  private fun postAssignmentAlert(
    context: Context,
    entry: UpcomingDutyEntry,
    isUpdate: Boolean,
    staffName: String
  ) {
    val dateLabel = entry.shiftStart.format(dateLabelFormatter)
    val roleNote = if (entry.assignment.roleTypeEnum == SlotRoleType.POINTSMAN) " · Pointsman" else ""
    val approvalNote = if (entry.assignment.approvedLocked) " (Approved)" else ""
    val prefix = if (isUpdate) "Duty Updated" else "Duty Assigned"
    val title = "$prefix: ${entry.shiftType.code} ${entry.shiftType.displayName}"
    val shortBody = "${entry.dutyArea.label}$roleNote · $dateLabel (${entry.shiftType.timeWindow})"
    val bigText = buildString {
      if (staffName.isNotBlank()) append("$staffName — ")
      append("${entry.shiftType.displayName} (${entry.shiftType.code})$approvalNote on $dateLabel.\n")
      append("Area: ${entry.dutyArea.label}$roleNote · Timing: ${entry.shiftType.timeWindow}.\n")
      append("Tap to open Tomorrow's Duties.")
    }
    postDutyNotification(
      context = context,
      channelId = CHANNEL_DUTY_ALERTS,
      notificationId = notificationIdFor(entry.assignment.id),
      title = title,
      shortBody = shortBody,
      bigText = bigText
    )
  }

  private fun buildSignature(assignment: ShiftSlotAssignment): String =
    "${assignment.id}|${assignment.date}|${assignment.shiftType}|${assignment.dutyArea}|${assignment.roleType}|${assignment.approvedLocked}"

  private fun notificationIdFor(assignmentId: String): Int =
    10_000 + abs(assignmentId.hashCode() % 40_000)

  private fun requestCodeFor(assignmentId: String, leadMinutes: Int): Int =
    50_000 + abs("${assignmentId}_$leadMinutes".hashCode() % 40_000)

  private data class UpcomingDutyEntry(
    val assignment: ShiftSlotAssignment,
    val shiftType: ShiftType,
    val dutyArea: DutyArea,
    val shiftStart: LocalDateTime,
    val signature: String
  )
}
