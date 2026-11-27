package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.model.AppUpdateEvaluation
import com.example.model.AppUpdateEvaluator
import com.example.model.AppUpdateStatus
import com.example.ui.theme.DepotTeal
import com.example.ui.theme.GlassBackdrop
import com.example.ui.theme.GlassIconOrb
import com.example.ui.theme.GlassPillBadge
import com.example.ui.theme.GlassPopupSurface
import com.example.ui.theme.GlassRimHighlight
import com.example.ui.theme.GlassSurfaceBright
import com.example.ui.theme.GlassSurfaceMid
import com.example.ui.theme.GlassSurfaceSoft
import com.example.ui.theme.GlassTile
import com.example.ui.theme.RailwayCobalt
import com.example.ui.theme.RailwayCobaltContainer
import com.example.ui.theme.RailwayCobaltOnContainer
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun openVerifiedUpdateUrl(context: Context, url: String): Boolean {
  val trimmed = url.trim()
  if (!AppUpdateEvaluator.isValidHttpsReleaseUrl(trimmed)) return false
  return runCatching {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(trimmed)).apply {
      addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(intent)
    true
  }.getOrDefault(false)
}

internal fun copyTextToClipboard(context: Context, label: String, text: String) {
  val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
  clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
}

internal fun formatReleaseTimestamp(epochSeconds: Long?): String {
  if (epochSeconds == null || epochSeconds <= 0L) return ""
  return runCatching {
    val fmt = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
    fmt.format(Date(epochSeconds * 1000L))
  }.getOrDefault("")
}

@Composable
fun OptionalUpdateBanner(
  evaluation: AppUpdateEvaluation,
  visible: Boolean,
  onOpenDetails: () -> Unit,
  onDismiss: () -> Unit,
  modifier: Modifier = Modifier
) {
  AnimatedVisibility(
    visible = visible && evaluation.status == AppUpdateStatus.OPTIONAL_AVAILABLE,
    enter = fadeIn() + expandVertically(),
    exit = fadeOut() + shrinkVertically(),
    modifier = modifier
  ) {
    GlassTile(
      shape = RoundedCornerShape(18.dp),
      topColor = GlassSurfaceBright,
      bottomColor = GlassSurfaceMid,
      tintColor = RailwayCobalt.copy(alpha = 0.12f),
      modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 14.dp, vertical = 6.dp)
        .clickable(onClick = onOpenDetails)
        .testTag("optional_update_banner")
    ) {
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
      ) {
        GlassIconOrb(
          shape = RoundedCornerShape(12.dp),
          tintColor = RailwayCobalt.copy(alpha = 0.16f),
          modifier = Modifier.size(38.dp)
        ) {
          Icon(
            imageVector = Icons.Default.SystemUpdate,
            contentDescription = null,
            tint = RailwayCobalt,
            modifier = Modifier.size(20.dp)
          )
        }
        Column(
          modifier = Modifier.weight(1f),
          verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
          Text(
            text = "Update v${evaluation.release.latestVersionName} available",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )
          Text(
            text = evaluation.release.releaseNotes.ifBlank {
              "Tap to view changelog and download the latest APK."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
          )
        }
        Button(
          onClick = onOpenDetails,
          elevation = ButtonDefaults.buttonElevation(0.dp, 0.dp, 0.dp, 0.dp, 0.dp),
          colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent, contentColor = Color.White),
          shape = RoundedCornerShape(12.dp),
          contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
          modifier = Modifier
            .heightIn(min = 48.dp)
            .liquidGlassAccent(shape = RoundedCornerShape(12.dp))
            .testTag("optional_update_banner_btn")
        ) {
          Text(
            text = stringResource(R.string.app_update_banner_action),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold
          )
        }
        IconButton(
          onClick = onDismiss,
          modifier = Modifier
            .size(48.dp)
            .testTag("optional_update_dismiss_btn")
        ) {
          Icon(
            imageVector = Icons.Default.Close,
            contentDescription = stringResource(R.string.app_update_dismiss_banner_cd),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp)
          )
        }
      }
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AppUpdateStatusDialog(
  evaluation: AppUpdateEvaluation,
  isAdmin: Boolean,
  onDownload: (String) -> Unit,
  onOpenAdminManager: () -> Unit,
  onNotifyMessage: (String) -> Unit,
  onDismiss: () -> Unit
) {
  val context = LocalContext.current
  val release = evaluation.release
  val updatedText = formatReleaseTimestamp(release.updatedAt?.seconds)

  AlertDialog(
    onDismissRequest = onDismiss,
    containerColor = GlassPopupSurface,
    icon = {
      GlassIconOrb(
        shape = RoundedCornerShape(16.dp),
        tintColor = when (evaluation.status) {
          AppUpdateStatus.UP_TO_DATE -> SignalEmerald.copy(alpha = 0.16f)
          AppUpdateStatus.OPTIONAL_AVAILABLE -> RailwayCobalt.copy(alpha = 0.16f)
          AppUpdateStatus.MANDATORY_REQUIRED -> SignalCrimson.copy(alpha = 0.16f)
        },
        modifier = Modifier.size(48.dp)
      ) {
        Icon(
          imageVector = if (evaluation.isUpdateAvailable) Icons.Default.SystemUpdate else Icons.Default.CheckCircle,
          contentDescription = null,
          tint = when (evaluation.status) {
            AppUpdateStatus.UP_TO_DATE -> SignalEmerald
            AppUpdateStatus.OPTIONAL_AVAILABLE -> RailwayCobalt
            AppUpdateStatus.MANDATORY_REQUIRED -> SignalCrimson
          }
        )
      }
    },
    title = {
      Text(
        text = when (evaluation.status) {
          AppUpdateStatus.UP_TO_DATE -> "You're Up to Date"
          AppUpdateStatus.OPTIONAL_AVAILABLE -> "New App Update Available"
          AppUpdateStatus.MANDATORY_REQUIRED -> "Required App Update"
        },
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center
      )
    },
    text = {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
      ) {
        FlowRow(
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
          GlassPillBadge(
            text = "INSTALLED v${evaluation.installedVersionName} (${evaluation.installedVersionCode})",
            tintColor = GlassSurfaceSoft,
            textColor = MaterialTheme.colorScheme.onSurface,
            borderColor = GlassRimHighlight
          )
          if (evaluation.isUpdateAvailable) {
            GlassPillBadge(
              text = "LATEST v${release.latestVersionName} (${release.latestVersionCode})",
              tintColor = if (evaluation.isMandatory) SignalCrimsonContainer else RailwayCobaltContainer,
              textColor = if (evaluation.isMandatory) SignalCrimsonOnContainer else RailwayCobaltOnContainer,
              borderColor = if (evaluation.isMandatory) SignalCrimson else RailwayCobalt
            )
          } else {
            GlassPillBadge(
              text = "LATEST INSTALLED",
              tintColor = SignalEmeraldContainer,
              textColor = SignalEmeraldOnContainer,
              borderColor = SignalEmerald
            )
          }
        }

        if (evaluation.isUpdateAvailable) {
          Surface(
            color = GlassSurfaceSoft,
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, GlassRimHighlight),
            modifier = Modifier.fillMaxWidth()
          ) {
            Column(
              modifier = Modifier.padding(12.dp),
              verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
              Text(
                text = "What's New",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
              )
              Text(
                text = release.releaseNotes.ifBlank {
                  "Includes the latest depot roster updates, bug fixes, and security improvements."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface
              )
              if (updatedText.isNotBlank() || release.publishedByName.isNotBlank()) {
                Text(
                  text = listOfNotNull(
                    release.publishedByName.takeIf { it.isNotBlank() }?.let { "Published by $it" },
                    updatedText.takeIf { it.isNotBlank() }
                  ).joinToString(" · "),
                  style = MaterialTheme.typography.labelSmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant
                )
              }
            }
          }

          if (release.sha256Checksum.isNotBlank()) {
            Surface(
              color = GlassSurfaceSoft,
              shape = RoundedCornerShape(12.dp),
              border = BorderStroke(1.dp, GlassRimHighlight),
              modifier = Modifier.fillMaxWidth()
            ) {
              Row(
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
              ) {
                Column(modifier = Modifier.weight(1f)) {
                  Text(
                    text = "SHA-256 Checksum",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                  )
                  Text(
                    text = release.sha256Checksum,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                  )
                }
                IconButton(
                  onClick = {
                    copyTextToClipboard(context, "APK SHA-256", release.sha256Checksum)
                    onNotifyMessage("SHA-256 checksum copied.")
                  },
                  modifier = Modifier
                    .size(48.dp)
                    .testTag("copy_update_sha256_btn")
                ) {
                  Icon(
                    imageVector = Icons.Default.ContentCopy,
                    contentDescription = "Copy SHA-256 checksum",
                    tint = DepotTeal
                  )
                }
              }
            }
          }

          Text(
            text = "Tapping Download opens the verified GitHub Releases APK link. Once downloaded, tap the APK notification to upgrade while keeping your sign-in and data.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        } else {
          Text(
            text = "This device is running the latest published PPIO Roster build. When the depot Admin publishes a newer GitHub Release, you'll be notified automatically.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }

        if (isAdmin) {
          HorizontalDivider()
          OutlinedButton(
            onClick = {
              onDismiss()
              onOpenAdminManager()
            },
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
              .fillMaxWidth()
              .heightIn(min = 48.dp)
              .testTag("dialog_open_admin_updates_btn")
          ) {
            Icon(
              imageVector = Icons.Default.AdminPanelSettings,
              contentDescription = null,
              modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("Manage App Releases (Admin)")
          }
        }
      }
    },
    confirmButton = {
      if (evaluation.isUpdateAvailable) {
        Button(
          onClick = { onDownload(release.downloadUrl) },
          modifier = Modifier
            .heightIn(min = 48.dp)
            .testTag("dialog_download_update_btn")
        ) {
          Icon(
            imageVector = Icons.Default.Download,
            contentDescription = null,
            modifier = Modifier.size(18.dp)
          )
          Spacer(modifier = Modifier.width(6.dp))
          Text(stringResource(R.string.app_update_download_button))
        }
      } else {
        TextButton(
          onClick = onDismiss,
          modifier = Modifier
            .heightIn(min = 48.dp)
            .testTag("dialog_update_close_btn")
        ) {
          Text("Close")
        }
      }
    },
    dismissButton = if (evaluation.isUpdateAvailable) {
      {
        TextButton(
          onClick = onDismiss,
          modifier = Modifier
            .heightIn(min = 48.dp)
            .testTag("dialog_update_later_btn")
        ) {
          Text(stringResource(R.string.app_update_later_button))
        }
      }
    } else null
  )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MandatoryUpdateGateScreen(
  evaluation: AppUpdateEvaluation,
  isAdmin: Boolean,
  onDownload: (String) -> Unit,
  onRecheck: () -> Unit,
  onOpenAdminManager: () -> Unit,
  onNotifyMessage: (String) -> Unit,
  modifier: Modifier = Modifier
) {
  val context = LocalContext.current
  val release = evaluation.release
  val updatedText = formatReleaseTimestamp(release.updatedAt?.seconds)

  // Block hardware/gesture back press while mandatory update is enforced
  BackHandler(enabled = true) {}

  GlassBackdrop(modifier = modifier.fillMaxSize()) {
    Box(
      modifier = Modifier
        .fillMaxSize()
        .background(Color.White.copy(alpha = 0.25f))
        .windowInsetsPadding(WindowInsets.safeDrawing)
        .padding(20.dp),
      contentAlignment = Alignment.Center
    ) {
      GlassTile(
        shape = RoundedCornerShape(28.dp),
        topColor = GlassSurfaceBright,
        bottomColor = GlassSurfaceMid,
        tintColor = SignalCrimson.copy(alpha = 0.08f),
        modifier = Modifier
          .fillMaxWidth()
          .widthIn(max = 520.dp)
          .testTag("mandatory_update_gate")
      ) {
        Column(
          modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(22.dp),
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
          GlassIconOrb(
            shape = RoundedCornerShape(20.dp),
            tintColor = SignalCrimson.copy(alpha = 0.16f),
            modifier = Modifier.size(64.dp)
          ) {
            Icon(
              imageVector = Icons.Default.Warning,
              contentDescription = null,
              tint = SignalCrimson,
              modifier = Modifier.size(34.dp)
            )
          }

          Text(
            text = "Update Required to Continue",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
          )

          Text(
            text = "A critical PPIO Roster update has been published by Depot Admin. Please install the latest version to continue using live roster features.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
          )

          FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(6.dp)
          ) {
            GlassPillBadge(
              text = "CURRENT v${evaluation.installedVersionName} (${evaluation.installedVersionCode})",
              tintColor = SafetyAmberContainer,
              textColor = SafetyAmberOnContainer,
              borderColor = SafetyAmber
            )
            GlassPillBadge(
              text = "REQUIRED v${release.latestVersionName} (${release.latestVersionCode})",
              tintColor = SignalCrimsonContainer,
              textColor = SignalCrimsonOnContainer,
              borderColor = SignalCrimson
            )
          }

          Surface(
            color = GlassSurfaceSoft,
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, GlassRimHighlight),
            modifier = Modifier.fillMaxWidth()
          ) {
            Column(
              modifier = Modifier.padding(14.dp),
              verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
              Text(
                text = "Release Notes",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
              )
              Text(
                text = release.releaseNotes.ifBlank {
                  "Mandatory stability, roster rule, or security update."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface
              )
              if (updatedText.isNotBlank() || release.publishedByName.isNotBlank()) {
                Text(
                  text = listOfNotNull(
                    release.publishedByName.takeIf { it.isNotBlank() }?.let { "Published by $it" },
                    updatedText.takeIf { it.isNotBlank() }
                  ).joinToString(" · "),
                  style = MaterialTheme.typography.labelSmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant
                )
              }
            }
          }

          if (release.sha256Checksum.isNotBlank()) {
            Surface(
              color = GlassSurfaceSoft,
              shape = RoundedCornerShape(14.dp),
              border = BorderStroke(1.dp, GlassRimHighlight),
              modifier = Modifier.fillMaxWidth()
            ) {
              Row(
                modifier = Modifier
                  .fillMaxWidth()
                  .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
              ) {
                Column(modifier = Modifier.weight(1f)) {
                  Text(
                    text = "SHA-256 Verification Hash",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                  )
                  Text(
                    text = release.sha256Checksum,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                  )
                }
                IconButton(
                  onClick = {
                    copyTextToClipboard(context, "APK SHA-256", release.sha256Checksum)
                    onNotifyMessage("SHA-256 checksum copied.")
                  },
                  modifier = Modifier.size(48.dp)
                ) {
                  Icon(
                    imageVector = Icons.Default.ContentCopy,
                    contentDescription = "Copy SHA-256 checksum",
                    tint = DepotTeal
                  )
                }
              }
            }
          }

          Spacer(modifier = Modifier.height(2.dp))

          Button(
            onClick = { onDownload(release.downloadUrl) },
            elevation = ButtonDefaults.buttonElevation(0.dp, 0.dp, 0.dp, 0.dp, 0.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent, contentColor = Color.White),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
              .fillMaxWidth()
              .heightIn(min = 52.dp)
              .liquidGlassAccent(shape = RoundedCornerShape(16.dp))
              .testTag("mandatory_download_update_btn")
          ) {
            Icon(
              imageVector = Icons.Default.Download,
              contentDescription = null,
              modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
              text = stringResource(R.string.app_update_download_required_button),
              fontWeight = FontWeight.Bold
            )
          }

          OutlinedButton(
            onClick = onRecheck,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
              .fillMaxWidth()
              .heightIn(min = 48.dp)
              .testTag("mandatory_recheck_btn")
          ) {
            Icon(
              imageVector = Icons.Default.Refresh,
              contentDescription = null,
              modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.app_update_recheck_button))
          }

          if (isAdmin) {
            HorizontalDivider()
            OutlinedButton(
              onClick = onOpenAdminManager,
              colors = ButtonDefaults.outlinedButtonColors(contentColor = SignalCrimson),
              border = BorderStroke(1.dp, SignalCrimson.copy(alpha = 0.5f)),
              shape = RoundedCornerShape(14.dp),
              modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .testTag("mandatory_admin_override_manager_btn")
            ) {
              Icon(
                imageVector = Icons.Default.AdminPanelSettings,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
              )
              Spacer(modifier = Modifier.width(8.dp))
              Text("Admin: Manage or Pause Release")
            }
          }
        }
      }
    }
  }
}
