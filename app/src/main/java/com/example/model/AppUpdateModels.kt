package com.example.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties

@Keep
@IgnoreExtraProperties
data class AppReleaseConfig(
  @DocumentId val id: String = LATEST_DOC_ID,
  val latestVersionCode: Int = 0,
  val latestVersionName: String = "",
  val minSupportedVersionCode: Int = 0,
  val mandatory: Boolean = false,
  val downloadUrl: String = "",
  val releaseNotes: String = "",
  val sha256Checksum: String = "",
  val publishedByUid: String = "",
  val publishedByName: String = "",
  val createdAt: Timestamp? = null,
  val updatedAt: Timestamp? = null
) {
  val hasActiveRelease: Boolean
    get() = latestVersionCode > 0 && latestVersionName.isNotBlank() && AppUpdateEvaluator.isValidHttpsReleaseUrl(downloadUrl)

  companion object {
    const val LATEST_DOC_ID = "latest"
  }
}

enum class AppUpdateStatus {
  UP_TO_DATE,
  OPTIONAL_AVAILABLE,
  MANDATORY_REQUIRED
}

data class AppUpdateEvaluation(
  val installedVersionCode: Int,
  val installedVersionName: String,
  val release: AppReleaseConfig,
  val status: AppUpdateStatus,
  val isSafeDownloadUrl: Boolean
) {
  val isUpdateAvailable: Boolean
    get() = status == AppUpdateStatus.OPTIONAL_AVAILABLE || status == AppUpdateStatus.MANDATORY_REQUIRED

  val isMandatory: Boolean
    get() = status == AppUpdateStatus.MANDATORY_REQUIRED
}

object AppUpdateEvaluator {
  private val HTTPS_URL_REGEX = Regex("^https://[A-Za-z0-9._~:/?#\\[\\]@!$&'()*+,;=%\\-]+$")
  private val SHA256_REGEX = Regex("^[0-9a-fA-F]{64}$")

  fun isValidHttpsReleaseUrl(url: String): Boolean {
    val trimmed = url.trim()
    if (trimmed.isEmpty() || trimmed.length > 500) return false
    if (!trimmed.startsWith("https://", ignoreCase = false)) return false
    val afterScheme = trimmed.removePrefix("https://")
    if (afterScheme.isBlank() || afterScheme.startsWith("/") || afterScheme.startsWith(".")) return false
    val host = afterScheme.substringBefore('/').substringBefore('?').substringBefore('#')
    if (host.isBlank() || !host.contains('.')) return false
    return HTTPS_URL_REGEX.matches(trimmed)
  }

  fun isValidOptionalSha256(checksum: String): Boolean {
    val trimmed = checksum.trim()
    return trimmed.isEmpty() || SHA256_REGEX.matches(trimmed)
  }

  fun evaluate(
    installedVersionCode: Int,
    installedVersionName: String,
    release: AppReleaseConfig
  ): AppUpdateEvaluation {
    val safeUrl = isValidHttpsReleaseUrl(release.downloadUrl)
    val hasNewerCode = release.latestVersionCode > installedVersionCode &&
      release.latestVersionName.isNotBlank() &&
      safeUrl

    val status = when {
      !hasNewerCode -> AppUpdateStatus.UP_TO_DATE
      release.mandatory || installedVersionCode < release.minSupportedVersionCode -> AppUpdateStatus.MANDATORY_REQUIRED
      else -> AppUpdateStatus.OPTIONAL_AVAILABLE
    }

    return AppUpdateEvaluation(
      installedVersionCode = installedVersionCode,
      installedVersionName = installedVersionName,
      release = release,
      status = status,
      isSafeDownloadUrl = safeUrl
    )
  }

  fun validatePublishInput(
    latestVersionCode: Int,
    latestVersionName: String,
    minSupportedVersionCode: Int,
    downloadUrl: String,
    releaseNotes: String,
    sha256Checksum: String
  ): String? {
    if (latestVersionCode < 1 || latestVersionCode > 999_999) {
      return "Latest version code must be a positive number (e.g., 2)."
    }
    if (latestVersionName.trim().isEmpty() || latestVersionName.trim().length > 40) {
      return "Enter a valid version name (e.g., 1.1.0)."
    }
    if (minSupportedVersionCode < 0 || minSupportedVersionCode > latestVersionCode) {
      return "Minimum supported version code must be between 0 and $latestVersionCode."
    }
    if (!isValidHttpsReleaseUrl(downloadUrl)) {
      return "Download URL must be a valid https:// link (e.g., GitHub Releases APK link)."
    }
    if (releaseNotes.trim().length > 1500) {
      return "Release notes must be 1500 characters or fewer."
    }
    if (!isValidOptionalSha256(sha256Checksum)) {
      return "SHA-256 checksum must be blank or exactly 64 hexadecimal characters."
    }
    return null
  }
}
