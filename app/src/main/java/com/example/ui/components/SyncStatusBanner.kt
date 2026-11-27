package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.FirestoreSyncStatus
import com.example.ui.theme.SafetyAmber
import com.example.ui.theme.SafetyAmberContainer
import com.example.ui.theme.SafetyAmberOnContainer
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun SyncStatusBanner(
  syncStatus: FirestoreSyncStatus,
  onSyncNow: () -> Unit,
  modifier: Modifier = Modifier
) {
  val visible = syncStatus.isOfflineTestMode || syncStatus.isOfflineOrCached || syncStatus.hasPendingWrites
  AnimatedVisibility(
    visible = visible,
    enter = fadeIn() + expandVertically(),
    exit = fadeOut() + shrinkVertically(),
    modifier = modifier
  ) {
    val isPendingOnly = !syncStatus.isOfflineTestMode &&
      syncStatus.isNetworkAvailable &&
      !syncStatus.isFromCache &&
      syncStatus.hasPendingWrites
    val containerColor = if (isPendingOnly) {
      MaterialTheme.colorScheme.primaryContainer
    } else {
      SafetyAmberContainer
    }
    val contentColor = if (isPendingOnly) {
      MaterialTheme.colorScheme.onPrimaryContainer
    } else {
      SafetyAmberOnContainer
    }
    val borderColor = if (isPendingOnly) {
      MaterialTheme.colorScheme.primary.copy(alpha = 0.28f)
    } else {
      SafetyAmber.copy(alpha = 0.35f)
    }

    val headline = when {
      syncStatus.isOfflineTestMode ->
        "Offline Test Mode · Local Sandbox Only"
      !syncStatus.isNetworkAvailable && syncStatus.hasPendingWrites ->
        "Offline · Changes saved locally"
      !syncStatus.isNetworkAvailable ->
        "Offline · Showing last synced data"
      syncStatus.hasPendingWrites ->
        "Syncing pending changes to cloud..."
      else ->
        "Poor network · Showing cached data"
    }

    val lastSyncFormatted = remember(syncStatus.lastSyncedEpochMs) {
      syncStatus.lastSyncedEpochMs?.let { formatLastSyncedTime(it) }
    }
    val supporting = when {
      syncStatus.isOfflineTestMode ->
        "No Gmail linked. Changes stay on this device only and never reflect on the server or for other users."
      !syncStatus.isNetworkAvailable && syncStatus.hasPendingWrites ->
        buildString {
          append("Updates will sync automatically when network returns.")
          if (lastSyncFormatted != null) append(" Last synced $lastSyncFormatted.")
        }
      !syncStatus.isNetworkAvailable ->
        if (lastSyncFormatted != null) {
          "Last synced $lastSyncFormatted · Live sync resumes automatically."
        } else {
          "Live sync resumes automatically once connection returns."
        }
      syncStatus.hasPendingWrites ->
        "Your offline updates are uploading in the background."
      else ->
        if (lastSyncFormatted != null) {
          "Last synced $lastSyncFormatted · Waiting for stronger connection."
        } else {
          "Waiting for a stronger connection to refresh from cloud."
        }
    }

    Surface(
      color = containerColor,
      shape = RoundedCornerShape(16.dp),
      border = BorderStroke(1.dp, borderColor),
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 16.dp, vertical = 6.dp)
        .testTag("sync_status_banner")
    ) {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
      ) {
        if (isPendingOnly) {
          CircularProgressIndicator(
            modifier = Modifier.size(18.dp),
            strokeWidth = 2.dp,
            color = contentColor
          )
        } else {
          Icon(
            imageVector = if (!syncStatus.isOfflineTestMode && syncStatus.isNetworkAvailable) {
              Icons.Default.CloudSync
            } else {
              Icons.Default.CloudOff
            },
            contentDescription = if (!syncStatus.isOfflineTestMode && syncStatus.isNetworkAvailable) {
              "Syncing cached data"
            } else {
              "Offline mode"
            },
            tint = contentColor,
            modifier = Modifier.size(20.dp)
          )
        }
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = headline,
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
            color = contentColor,
            maxLines = 2
          )
          Text(
            text = supporting,
            style = MaterialTheme.typography.labelSmall,
            color = contentColor.copy(alpha = 0.85f),
            maxLines = 3,
            overflow = TextOverflow.Ellipsis
          )
        }
        if (syncStatus.isNetworkAvailable && !syncStatus.isOfflineTestMode) {
          TextButton(
            onClick = onSyncNow,
            modifier = Modifier.testTag("sync_status_retry_button")
          ) {
            Icon(
              imageVector = Icons.Default.Refresh,
              contentDescription = "Refresh sync",
              tint = contentColor,
              modifier = Modifier.size(16.dp)
            )
            Text(
              text = "Sync",
              color = contentColor,
              style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
              modifier = Modifier.padding(start = 4.dp)
            )
          }
        }
      }
    }
  }
}

private fun formatLastSyncedTime(epochMs: Long): String {
  return runCatching {
    val zone = ZoneId.systemDefault()
    val zoned = Instant.ofEpochMilli(epochMs).atZone(zone)
    val today = LocalDate.now(zone)
    if (zoned.toLocalDate() == today) {
      zoned.format(DateTimeFormatter.ofPattern("hh:mm a", Locale.getDefault()))
    } else {
      zoned.format(DateTimeFormatter.ofPattern("dd MMM, hh:mm a", Locale.getDefault()))
    }
  }.getOrDefault("recently")
}
