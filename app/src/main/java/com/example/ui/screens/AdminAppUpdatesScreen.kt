package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.BuildConfig
import com.example.R
import com.example.model.AppReleaseConfig
import com.example.model.AppUpdateEvaluator
import com.example.model.UserRole
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
import com.example.ui.theme.liquidGlassSurface

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AdminAppUpdatesScreen(
  userRole: UserRole,
  currentRelease: AppReleaseConfig,
  isBusy: Boolean,
  onPublishRelease: (
    latestVersionCode: Int,
    latestVersionName: String,
    minSupportedVersionCode: Int,
    mandatory: Boolean,
    downloadUrl: String,
    releaseNotes: String,
    sha256Checksum: String,
    onResult: (Boolean, String) -> Unit
  ) -> Unit,
  onClearRelease: (onResult: (Boolean, String) -> Unit) -> Unit,
  onNotifyMessage: (String) -> Unit,
  onClose: () -> Unit,
  modifier: Modifier = Modifier
) {
  BackHandler(enabled = true, onBack = onClose)

  val context = LocalContext.current
  val installedCode = BuildConfig.VERSION_CODE
  val installedName = BuildConfig.VERSION_NAME

  var versionCodeText by remember {
    mutableStateOf(
      if (currentRelease.latestVersionCode > 0) currentRelease.latestVersionCode.toString()
      else (installedCode + 1).toString()
    )
  }
  var versionNameText by remember {
    mutableStateOf(
      currentRelease.latestVersionName.ifBlank { "${installedName}.1" }
    )
  }
  var minSupportedCodeText by remember {
    mutableStateOf(
      if (currentRelease.minSupportedVersionCode > 0) currentRelease.minSupportedVersionCode.toString()
      else installedCode.toString()
    )
  }
  var mandatoryToggle by remember { mutableStateOf(currentRelease.mandatory) }
  var downloadUrlText by remember { mutableStateOf(currentRelease.downloadUrl) }
  var releaseNotesText by remember { mutableStateOf(currentRelease.releaseNotes) }
  var sha256Text by remember { mutableStateOf(currentRelease.sha256Checksum) }
  var formError by remember { mutableStateOf<String?>(null) }
  var formSuccess by remember { mutableStateOf<String?>(null) }
  var showPauseConfirmDialog by remember { mutableStateOf(false) }

  LaunchedEffect(
    currentRelease.latestVersionCode,
    currentRelease.latestVersionName,
    currentRelease.minSupportedVersionCode,
    currentRelease.mandatory,
    currentRelease.downloadUrl,
    currentRelease.releaseNotes,
    currentRelease.sha256Checksum
  ) {
    if (currentRelease.hasActiveRelease) {
      versionCodeText = currentRelease.latestVersionCode.toString()
      versionNameText = currentRelease.latestVersionName
      minSupportedCodeText = currentRelease.minSupportedVersionCode.toString()
      mandatoryToggle = currentRelease.mandatory
      downloadUrlText = currentRelease.downloadUrl
      releaseNotesText = currentRelease.releaseNotes
      sha256Text = currentRelease.sha256Checksum
    }
  }

  GlassBackdrop(modifier = modifier.fillMaxSize()) {
    Column(
      modifier = Modifier
        .fillMaxSize()
        .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
      // Liquid Glass Header Bar
      Box(
        modifier = Modifier
          .fillMaxWidth()
          .liquidGlassSurface(
            shape = RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp),
            topColor = GlassSurfaceBright,
            bottomColor = GlassSurfaceMid,
            elevation = 8.dp
          )
          .padding(horizontal = 10.dp, vertical = 10.dp)
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          IconButton(
            onClick = onClose,
            modifier = Modifier
              .size(48.dp)
              .testTag("admin_updates_back_btn")
          ) {
            Icon(
              imageVector = Icons.AutoMirrored.Filled.ArrowBack,
              contentDescription = stringResource(R.string.app_update_close_manager_cd)
            )
          }
          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = stringResource(R.string.app_update_manager_title),
              style = MaterialTheme.typography.titleLarge,
              fontWeight = FontWeight.Bold,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis
            )
            Text(
              text = stringResource(R.string.app_update_manager_subtitle),
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              maxLines = 2
            )
          }
          GlassPillBadge(
            text = "ADMIN ONLY",
            tintColor = SignalCrimsonContainer,
            textColor = SignalCrimsonOnContainer,
            borderColor = SignalCrimson
          )
        }
      }

      if (userRole != UserRole.ADMIN) {
        Box(
          modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
          contentAlignment = Alignment.Center
        ) {
          Text(
            text = "App Updates Manager is restricted to Admin accounts.",
            style = MaterialTheme.typography.bodyMedium,
            color = SignalCrimson
          )
        }
        return@Column
      }

      Box(
        modifier = Modifier
          .weight(1f)
          .fillMaxWidth(),
        contentAlignment = Alignment.TopCenter
      ) {
        LazyColumn(
          modifier = Modifier
            .fillMaxSize()
            .widthIn(max = 640.dp),
          contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
          verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
          // 1. Installed Device Build & Live Published Release Summary
          item(key = "ota_status_summary") {
            GlassTile(
              shape = RoundedCornerShape(22.dp),
              topColor = GlassSurfaceBright,
              bottomColor = GlassSurfaceMid,
              tintColor = RailwayCobalt.copy(alpha = 0.08f),
              modifier = Modifier
                .fillMaxWidth()
                .testTag("admin_updates_status_card")
            ) {
              Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
              ) {
                Row(
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                  GlassIconOrb(
                    shape = RoundedCornerShape(14.dp),
                    tintColor = RailwayCobalt.copy(alpha = 0.14f),
                    modifier = Modifier.size(40.dp)
                  ) {
                    Icon(
                      imageVector = Icons.Default.SystemUpdate,
                      contentDescription = null,
                      tint = RailwayCobalt
                    )
                  }
                  Column(modifier = Modifier.weight(1f)) {
                    Text(
                      text = "Current Broadcast Status",
                      style = MaterialTheme.typography.titleMedium,
                      fontWeight = FontWeight.Bold
                    )
                    Text(
                      text = "This device: v$installedName (code $installedCode)",
                      style = MaterialTheme.typography.bodySmall,
                      color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                  }
                }

                FlowRow(
                  horizontalArrangement = Arrangement.spacedBy(8.dp),
                  verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                  if (currentRelease.hasActiveRelease) {
                    GlassPillBadge(
                      text = "LIVE v${currentRelease.latestVersionName} (CODE ${currentRelease.latestVersionCode})",
                      tintColor = SignalEmeraldContainer,
                      textColor = SignalEmeraldOnContainer,
                      borderColor = SignalEmerald
                    )
                    GlassPillBadge(
                      text = if (currentRelease.mandatory) "MANDATORY LOCK" else "OPTIONAL BANNER",
                      tintColor = if (currentRelease.mandatory) SignalCrimsonContainer else RailwayCobaltContainer,
                      textColor = if (currentRelease.mandatory) SignalCrimsonOnContainer else RailwayCobaltOnContainer,
                      borderColor = if (currentRelease.mandatory) SignalCrimson else RailwayCobalt
                    )
                    if (currentRelease.minSupportedVersionCode > 0) {
                      GlassPillBadge(
                        text = "MIN CODE ${currentRelease.minSupportedVersionCode}",
                        tintColor = SafetyAmberContainer,
                        textColor = SafetyAmberOnContainer,
                        borderColor = SafetyAmber
                      )
                    }
                  } else {
                    GlassPillBadge(
                      text = "NO ACTIVE UPDATE NOTICE",
                      tintColor = GlassSurfaceSoft,
                      textColor = MaterialTheme.colorScheme.onSurfaceVariant,
                      borderColor = GlassRimHighlight
                    )
                  }
                }

                if (currentRelease.hasActiveRelease) {
                  val updatedStr = formatReleaseTimestamp(currentRelease.updatedAt?.seconds)
                  Text(
                    text = currentRelease.downloadUrl,
                    style = MaterialTheme.typography.labelSmall,
                    color = RailwayCobalt,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                  )
                  if (updatedStr.isNotBlank() || currentRelease.publishedByName.isNotBlank()) {
                    Text(
                      text = listOfNotNull(
                        currentRelease.publishedByName.takeIf { it.isNotBlank() }?.let { "Last published by $it" },
                        updatedStr.takeIf { it.isNotBlank() }
                      ).joinToString(" · "),
                      style = MaterialTheme.typography.labelSmall,
                      color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                  }
                }
              }
            }
          }

          // 2. Publish New Release Form
          item(key = "ota_publish_form") {
            GlassTile(
              shape = RoundedCornerShape(22.dp),
              topColor = GlassSurfaceBright,
              bottomColor = GlassSurfaceMid,
              modifier = Modifier
                .fillMaxWidth()
                .testTag("admin_updates_publish_form")
            ) {
              Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
              ) {
                Text(
                  text = "Publish Release to All Depot Users",
                  style = MaterialTheme.typography.titleMedium,
                  fontWeight = FontWeight.Bold
                )

                if (formError != null) {
                  Surface(
                    color = SignalCrimsonContainer,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                  ) {
                    Text(
                      text = formError!!,
                      style = MaterialTheme.typography.bodySmall,
                      color = SignalCrimsonOnContainer,
                      fontWeight = FontWeight.SemiBold,
                      modifier = Modifier.padding(10.dp)
                    )
                  }
                }
                if (formSuccess != null) {
                  Surface(
                    color = SignalEmeraldContainer,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                  ) {
                    Text(
                      text = formSuccess!!,
                      style = MaterialTheme.typography.bodySmall,
                      color = SignalEmeraldOnContainer,
                      fontWeight = FontWeight.SemiBold,
                      modifier = Modifier.padding(10.dp)
                    )
                  }
                }

                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                  OutlinedTextField(
                    value = versionCodeText,
                    onValueChange = {
                      versionCodeText = it.filter { ch -> ch.isDigit() }.take(6)
                      formError = null
                    },
                    label = { Text("Version Code") },
                    placeholder = { Text("e.g., 2") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                      .weight(1f)
                      .testTag("ota_version_code_input")
                  )
                  OutlinedTextField(
                    value = versionNameText,
                    onValueChange = {
                      versionNameText = it.take(40)
                      formError = null
                    },
                    label = { Text("Version Name") },
                    placeholder = { Text("e.g., 1.1.0") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                      .weight(1.3f)
                      .testTag("ota_version_name_input")
                  )
                }

                OutlinedTextField(
                  value = minSupportedCodeText,
                  onValueChange = {
                    minSupportedCodeText = it.filter { ch -> ch.isDigit() }.take(6)
                    formError = null
                  },
                  label = { Text("Minimum Supported Version Code") },
                  supportingText = {
                    Text("Devices with a lower version code will be forced to update before using the app.")
                  },
                  singleLine = true,
                  keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                  shape = RoundedCornerShape(14.dp),
                  modifier = Modifier
                    .fillMaxWidth()
                    .testTag("ota_min_supported_input")
                )

                Surface(
                  color = if (mandatoryToggle) SignalCrimsonContainer.copy(alpha = 0.5f) else GlassSurfaceSoft,
                  shape = RoundedCornerShape(14.dp),
                  border = BorderStroke(
                    1.dp,
                    if (mandatoryToggle) SignalCrimson.copy(alpha = 0.45f) else GlassRimHighlight
                  ),
                  modifier = Modifier.fillMaxWidth()
                ) {
                  Row(
                    modifier = Modifier
                      .fillMaxWidth()
                      .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                  ) {
                    Column(modifier = Modifier.weight(1f)) {
                      Text(
                        text = "Mandatory Update Lock",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (mandatoryToggle) SignalCrimsonOnContainer else MaterialTheme.colorScheme.onSurface
                      )
                      Text(
                        text = if (mandatoryToggle) {
                          "Blocks older versions immediately until updated (Admin keeps manager bypass)."
                        } else {
                          "Shows a dismissible optional update banner with download prompt."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                      )
                    }
                    Switch(
                      checked = mandatoryToggle,
                      onCheckedChange = { mandatoryToggle = it },
                      colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = SignalCrimson
                      ),
                      modifier = Modifier.testTag("ota_mandatory_switch")
                    )
                  }
                }

                OutlinedTextField(
                  value = downloadUrlText,
                  onValueChange = {
                    downloadUrlText = it.take(500)
                    formError = null
                  },
                  label = { Text("GitHub Releases APK URL (https://...)") },
                  placeholder = { Text("https://github.com/org/repo/releases/download/v1.1.0/app.apk") },
                  singleLine = false,
                  maxLines = 3,
                  keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                  shape = RoundedCornerShape(14.dp),
                  modifier = Modifier
                    .fillMaxWidth()
                    .testTag("ota_download_url_input")
                )

                OutlinedTextField(
                  value = releaseNotesText,
                  onValueChange = {
                    releaseNotesText = it.take(1500)
                    formError = null
                  },
                  label = { Text("Release Notes / What's New") },
                  placeholder = { Text("• Added SSE & JE roles\n• Fixed shift filtering bugs") },
                  minLines = 3,
                  maxLines = 6,
                  shape = RoundedCornerShape(14.dp),
                  modifier = Modifier
                    .fillMaxWidth()
                    .testTag("ota_release_notes_input")
                )

                OutlinedTextField(
                  value = sha256Text,
                  onValueChange = {
                    sha256Text = it.trim().take(64)
                    formError = null
                  },
                  label = { Text("SHA-256 Checksum (Optional)") },
                  placeholder = { Text("64-character hex checksum for integrity check") },
                  singleLine = true,
                  shape = RoundedCornerShape(14.dp),
                  modifier = Modifier
                    .fillMaxWidth()
                    .testTag("ota_sha256_input")
                )

                Row(
                  modifier = Modifier.fillMaxWidth(),
                  horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                  OutlinedButton(
                    onClick = {
                      val trimmed = downloadUrlText.trim()
                      if (!AppUpdateEvaluator.isValidHttpsReleaseUrl(trimmed)) {
                        formError = "Enter a valid https:// GitHub Releases APK link to test."
                      } else if (!openVerifiedUpdateUrl(context, trimmed)) {
                        onNotifyMessage("No browser app found to open the release link.")
                      }
                    },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                      .weight(1f)
                      .heightIn(min = 48.dp)
                      .testTag("ota_test_link_btn")
                  ) {
                    Icon(
                      imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                      contentDescription = null,
                      modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.app_update_test_link_button))
                  }

                  Button(
                    onClick = {
                      val code = versionCodeText.trim().toIntOrNull() ?: 0
                      val minCode = minSupportedCodeText.trim().toIntOrNull() ?: 0
                      val err = AppUpdateEvaluator.validatePublishInput(
                        latestVersionCode = code,
                        latestVersionName = versionNameText,
                        minSupportedVersionCode = minCode,
                        downloadUrl = downloadUrlText,
                        releaseNotes = releaseNotesText,
                        sha256Checksum = sha256Text
                      )
                      if (err != null) {
                        formError = err
                        formSuccess = null
                        return@Button
                      }
                      formError = null
                      onPublishRelease(
                        code,
                        versionNameText.trim(),
                        minCode,
                        mandatoryToggle,
                        downloadUrlText.trim(),
                        releaseNotesText.trim(),
                        sha256Text.trim()
                      ) { ok, message ->
                        if (ok) {
                          formError = null
                          formSuccess = message
                        } else {
                          formSuccess = null
                          formError = message
                        }
                      }
                    },
                    enabled = !isBusy,
                    elevation = ButtonDefaults.buttonElevation(0.dp, 0.dp, 0.dp, 0.dp, 0.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent, contentColor = Color.White),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                      .weight(1.35f)
                      .heightIn(min = 48.dp)
                      .liquidGlassAccent(shape = RoundedCornerShape(14.dp))
                      .testTag("ota_publish_btn")
                  ) {
                    if (isBusy) {
                      CircularProgressIndicator(
                        color = Color.White,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(18.dp)
                      )
                    } else {
                      Icon(
                        imageVector = Icons.Default.CloudUpload,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                      )
                      Spacer(modifier = Modifier.width(6.dp))
                      Text(
                        text = stringResource(R.string.app_update_publish_button),
                        fontWeight = FontWeight.Bold
                      )
                    }
                  }
                }

                if (currentRelease.hasActiveRelease) {
                  OutlinedButton(
                    onClick = { showPauseConfirmDialog = true },
                    enabled = !isBusy,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = SignalCrimson),
                    border = BorderStroke(1.dp, SignalCrimson.copy(alpha = 0.45f)),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                      .fillMaxWidth()
                      .heightIn(min = 48.dp)
                      .testTag("ota_pause_release_btn")
                  ) {
                    Icon(
                      imageVector = Icons.Default.PauseCircle,
                      contentDescription = null,
                      modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Pause / Clear Active Update Notice")
                  }
                }
              }
            }
          }

          // 3. Built-in 4-Step GitHub Releases Procedure Checklist
          item(key = "ota_procedure_guide") {
            GlassTile(
              shape = RoundedCornerShape(22.dp),
              topColor = GlassSurfaceBright,
              bottomColor = GlassSurfaceMid,
              tintColor = DepotTeal.copy(alpha = 0.08f),
              modifier = Modifier
                .fillMaxWidth()
                .testTag("admin_updates_procedure_card")
            ) {
              Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
              ) {
                Row(
                  verticalAlignment = Alignment.CenterVertically,
                  horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                  Icon(
                    imageVector = Icons.Default.VerifiedUser,
                    contentDescription = null,
                    tint = DepotTeal,
                    modifier = Modifier.size(20.dp)
                  )
                  Text(
                    text = "GitHub Releases Rollout Procedure",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                  )
                }

                ProcedureStepRow(
                  stepNumber = "1",
                  title = "Increment versionCode & build signed APK",
                  detail = "Increase versionCode (e.g., $installedCode -> ${installedCode + 1}) and versionName, then generate the APK signed with the exact same keystore so users keep their sign-in & data."
                )
                ProcedureStepRow(
                  stepNumber = "2",
                  title = "Upload APK to GitHub Releases",
                  detail = "Open your GitHub repository -> Releases -> Draft a new release, attach the .apk asset, and publish."
                )
                ProcedureStepRow(
                  stepNumber = "3",
                  title = "Copy Direct HTTPS APK Link",
                  detail = "Copy the direct download URL (https://github.com/<owner>/<repo>/releases/download/<tag>/<file>.apk) and tap Test Link above."
                )
                ProcedureStepRow(
                  stepNumber = "4",
                  title = "Publish Optional or Mandatory Update",
                  detail = "Tap Publish Update above. All signed-in depot devices receive the prompt immediately in real time."
                )
              }
            }
          }

          item(key = "ota_bottom_spacer") {
            Spacer(modifier = Modifier.height(16.dp))
          }
        }
      }
    }
  }

  if (showPauseConfirmDialog) {
    AlertDialog(
      onDismissRequest = { showPauseConfirmDialog = false },
      containerColor = GlassPopupSurface,
      title = { Text("Pause Active Update Notice?", fontWeight = FontWeight.Bold) },
      text = {
        Text(
          "This removes the current update banner and mandatory update lock for all depot users immediately. You can publish a new release again anytime."
        )
      },
      confirmButton = {
        Button(
          onClick = {
            showPauseConfirmDialog = false
            onClearRelease { ok, message ->
              if (ok) {
                formError = null
                formSuccess = message
              } else {
                formSuccess = null
                formError = message
              }
            }
          },
          colors = ButtonDefaults.buttonColors(containerColor = SignalCrimson, contentColor = Color.White),
          modifier = Modifier
            .heightIn(min = 48.dp)
            .testTag("confirm_pause_release_btn")
        ) {
          Text("Pause Notice")
        }
      },
      dismissButton = {
        TextButton(
          onClick = { showPauseConfirmDialog = false },
          modifier = Modifier.heightIn(min = 48.dp)
        ) {
          Text("Cancel")
        }
      }
    )
  }
}

@Composable
private fun ProcedureStepRow(
  stepNumber: String,
  title: String,
  detail: String
) {
  Row(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.spacedBy(10.dp),
    verticalAlignment = Alignment.Top
  ) {
    Surface(
      color = DepotTeal.copy(alpha = 0.14f),
      shape = RoundedCornerShape(10.dp),
      modifier = Modifier.size(26.dp)
    ) {
      Box(contentAlignment = Alignment.Center) {
        Text(
          text = stepNumber,
          style = MaterialTheme.typography.labelMedium,
          fontWeight = FontWeight.Bold,
          color = DepotTeal
        )
      }
    }
    Column(
      modifier = Modifier.weight(1f),
      verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
      Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface
      )
      Text(
        text = detail,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
    }
    Icon(
      imageVector = Icons.Default.CheckCircle,
      contentDescription = null,
      tint = DepotTeal.copy(alpha = 0.45f),
      modifier = Modifier
        .padding(top = 2.dp)
        .size(16.dp)
    )
  }
}
