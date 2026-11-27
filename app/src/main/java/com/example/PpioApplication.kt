package com.example

import android.app.Application
import com.example.data.FirestoreProvider
import com.example.notifications.TechnicianDutyNotifier

/**
 * Application entry point ensuring Firestore durable disk offline persistence and
 * technician duty notification channels are configured at process startup.
 */
class PpioApplication : Application() {
  override fun onCreate() {
    super.onCreate()
    FirestoreProvider.getInstance(this)
    TechnicianDutyNotifier.createNotificationChannels(this)
  }
}
