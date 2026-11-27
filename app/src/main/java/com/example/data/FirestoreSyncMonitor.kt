package com.example.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Snapshot-level metadata aggregated across primary Firestore listeners.
 */
data class FirestoreCacheSnapshotState(
  val isFromCache: Boolean = false,
  val hasPendingWrites: Boolean = false,
  val lastServerSyncEpochMs: Long? = null
)

/**
 * Combined UI-ready connectivity and Firestore offline cache status.
 */
data class FirestoreSyncStatus(
  val isNetworkAvailable: Boolean = true,
  val isFromCache: Boolean = false,
  val hasPendingWrites: Boolean = false,
  val lastSyncedEpochMs: Long? = null,
  val isOfflineTestMode: Boolean = false
) {
  val isOfflineOrCached: Boolean
    get() = !isNetworkAvailable || isFromCache || isOfflineTestMode

  val isSyncingPendingWrites: Boolean
    get() = !isOfflineTestMode && isNetworkAvailable && hasPendingWrites
}

/**
 * Observes real-time Android network connectivity using [ConnectivityManager.NetworkCallback].
 */
class NetworkConnectivityObserver(context: Context) {
  private val appContext = context.applicationContext
  private val connectivityManager =
    appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

  fun isOnlineNow(): Boolean {
    val cm = connectivityManager ?: return true
    return runCatching {
      val activeNetwork = cm.activeNetwork ?: return@runCatching false
      val caps = cm.getNetworkCapabilities(activeNetwork) ?: return@runCatching false
      caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
        (
          caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) ||
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
          )
    }.getOrDefault(true)
  }

  fun observeConnectivity(): Flow<Boolean> = callbackFlow {
    val cm = connectivityManager
    if (cm == null) {
      trySend(true)
      awaitClose {}
      return@callbackFlow
    }

    trySend(isOnlineNow())

    val callback = object : ConnectivityManager.NetworkCallback() {
      override fun onAvailable(network: Network) {
        trySend(isOnlineNow())
      }

      override fun onLost(network: Network) {
        trySend(isOnlineNow())
      }

      override fun onCapabilitiesChanged(
        network: Network,
        networkCapabilities: NetworkCapabilities
      ) {
        trySend(isOnlineNow())
      }

      override fun onUnavailable() {
        trySend(false)
      }
    }

    val registered = runCatching {
      cm.registerDefaultNetworkCallback(callback)
      true
    }.getOrDefault(false)

    awaitClose {
      if (registered) {
        runCatching { cm.unregisterNetworkCallback(callback) }
      }
    }
  }.distinctUntilChanged()
}

/**
 * Persists the timestamp of the last server-confirmed Firestore sync so users launching offline
 * can still see how fresh their cached depot data is.
 */
class SyncTimestampStore(context: Context) {
  private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

  fun getLastSyncedEpochMs(): Long? {
    val saved = prefs.getLong(KEY_LAST_SYNCED_MS, 0L)
    return saved.takeIf { it > 0L }
  }

  fun recordSync(epochMs: Long = System.currentTimeMillis()) {
    prefs.edit().putLong(KEY_LAST_SYNCED_MS, epochMs).apply()
  }

  companion object {
    private const val PREFS_NAME = "ppio_firestore_sync_meta"
    private const val KEY_LAST_SYNCED_MS = "last_server_sync_epoch_ms"
  }
}
