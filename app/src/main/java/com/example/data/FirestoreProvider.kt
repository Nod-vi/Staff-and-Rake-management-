package com.example.data

import android.content.Context
import android.util.Log
import com.example.R
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.PersistentCacheSettings

/**
 * Single centralized entry point for obtaining the provisioned custom-ID [FirebaseFirestore]
 * instance with durable on-disk offline persistence enabled BEFORE any read or write occurs.
 */
object FirestoreProvider {
  private const val TAG = "FirestoreProvider"

  @Volatile
  private var instance: FirebaseFirestore? = null

  @Volatile
  var isOfflineCacheConfigured: Boolean = false
    private set

  fun getInstance(context: Context): FirebaseFirestore {
    instance?.let { return it }
    return synchronized(this) {
      instance?.let { return@synchronized it }
      val appContext = context.applicationContext
      val databaseId = appContext.getString(R.string.firestore_database_id)
      val db = FirebaseFirestore.getInstance(databaseId)
      applyOfflinePersistenceSettings(db)
      instance = db
      db
    }
  }

  fun applyOfflinePersistenceSettings(db: FirebaseFirestore) {
    runCatching {
      val cacheSettings = PersistentCacheSettings.newBuilder()
        .setSizeBytes(FirebaseFirestoreSettings.CACHE_SIZE_UNLIMITED)
        .build()
      db.firestoreSettings = FirebaseFirestoreSettings.Builder(db.firestoreSettings)
        .setLocalCacheSettings(cacheSettings)
        .build()
      isOfflineCacheConfigured = true
      Log.i(TAG, "Firestore persistent offline cache configured (unlimited local cache size).")
    }.onFailure { error ->
      // If Firestore was already started elsewhere in the process, its persistent cache
      // remains active (Android SDK enables disk cache by default when persistent settings apply).
      Log.w(TAG, "Firestore settings already initialized: ${error.localizedMessage}")
    }
  }
}
