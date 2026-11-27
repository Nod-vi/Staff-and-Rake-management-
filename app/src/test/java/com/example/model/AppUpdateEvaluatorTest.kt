package com.example.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUpdateEvaluatorTest {

  private val validGithubApkUrl =
    "https://github.com/ppio-depot/ppio-roster/releases/download/v1.1.0/ppio-roster-v1.1.0.apk"

  @Test
  fun upToDateWhenNoReleaseConfiguredOrInstalledMatchesLatest() {
    val emptyEval = AppUpdateEvaluator.evaluate(1, "1.0", AppReleaseConfig())
    assertEquals(AppUpdateStatus.UP_TO_DATE, emptyEval.status)
    assertFalse(emptyEval.isUpdateAvailable)

    val sameVersion = AppReleaseConfig(
      latestVersionCode = 2,
      latestVersionName = "1.1.0",
      minSupportedVersionCode = 2,
      mandatory = true,
      downloadUrl = validGithubApkUrl
    )
    val matchedEval = AppUpdateEvaluator.evaluate(2, "1.1.0", sameVersion)
    assertEquals(AppUpdateStatus.UP_TO_DATE, matchedEval.status)
    assertFalse(matchedEval.isUpdateAvailable)
  }

  @Test
  fun optionalUpdateWhenNewerReleaseIsNotMandatoryAndMeetsMinSupported() {
    val release = AppReleaseConfig(
      latestVersionCode = 3,
      latestVersionName = "1.2.0",
      minSupportedVersionCode = 1,
      mandatory = false,
      downloadUrl = validGithubApkUrl,
      releaseNotes = "Added SSE & JE roles"
    )
    val eval = AppUpdateEvaluator.evaluate(2, "1.1.0", release)
    assertEquals(AppUpdateStatus.OPTIONAL_AVAILABLE, eval.status)
    assertTrue(eval.isUpdateAvailable)
    assertFalse(eval.isMandatory)
  }

  @Test
  fun mandatoryUpdateTriggeredByMandatoryFlagOrMinSupportedVersion() {
    val forcedRelease = AppReleaseConfig(
      latestVersionCode = 2,
      latestVersionName = "1.1.0",
      minSupportedVersionCode = 1,
      mandatory = true,
      downloadUrl = validGithubApkUrl
    )
    assertEquals(
      AppUpdateStatus.MANDATORY_REQUIRED,
      AppUpdateEvaluator.evaluate(1, "1.0", forcedRelease).status
    )

    val minVersionRelease = AppReleaseConfig(
      latestVersionCode = 4,
      latestVersionName = "1.3.0",
      minSupportedVersionCode = 3,
      mandatory = false,
      downloadUrl = validGithubApkUrl
    )
    assertEquals(
      AppUpdateStatus.MANDATORY_REQUIRED,
      AppUpdateEvaluator.evaluate(2, "1.1.0", minVersionRelease).status
    )
    assertEquals(
      AppUpdateStatus.OPTIONAL_AVAILABLE,
      AppUpdateEvaluator.evaluate(3, "1.2.0", minVersionRelease).status
    )
  }

  @Test
  fun unsafeOrNonHttpsDownloadUrlsAreRejectedSafely() {
    val insecureUrls = listOf(
      "",
      "http://github.com/org/repo/releases/download/v2/app.apk",
      "javascript:alert(1)",
      "file:///sdcard/app.apk",
      "intent://scan/#Intent;end",
      "https://localhost/app.apk",
      "https://github.com/org/repo/releases/download/v2/app with spaces.apk"
    )
    insecureUrls.forEach { badUrl ->
      assertFalse("Expected $badUrl to be rejected", AppUpdateEvaluator.isValidHttpsReleaseUrl(badUrl))
      val eval = AppUpdateEvaluator.evaluate(
        installedVersionCode = 1,
        installedVersionName = "1.0",
        release = AppReleaseConfig(
          latestVersionCode = 5,
          latestVersionName = "2.0.0",
          mandatory = true,
          downloadUrl = badUrl
        )
      )
      assertEquals(AppUpdateStatus.UP_TO_DATE, eval.status)
      assertFalse(eval.isSafeDownloadUrl)
    }
  }

  @Test
  fun publishValidationChecksVersionCodesUrlAndOptionalSha256() {
    val validSha = "a".repeat(64)
    assertNull(
      AppUpdateEvaluator.validatePublishInput(
        latestVersionCode = 2,
        latestVersionName = "1.1.0",
        minSupportedVersionCode = 1,
        downloadUrl = validGithubApkUrl,
        releaseNotes = "Bug fixes",
        sha256Checksum = validSha
      )
    )
    assertNull(
      AppUpdateEvaluator.validatePublishInput(
        latestVersionCode = 2,
        latestVersionName = "1.1.0",
        minSupportedVersionCode = 0,
        downloadUrl = validGithubApkUrl,
        releaseNotes = "",
        sha256Checksum = ""
      )
    )
    assertNotNull(
      AppUpdateEvaluator.validatePublishInput(
        latestVersionCode = 2,
        latestVersionName = "1.1.0",
        minSupportedVersionCode = 3,
        downloadUrl = validGithubApkUrl,
        releaseNotes = "",
        sha256Checksum = ""
      )
    )
    assertNotNull(
      AppUpdateEvaluator.validatePublishInput(
        latestVersionCode = 2,
        latestVersionName = "1.1.0",
        minSupportedVersionCode = 1,
        downloadUrl = "http://example.com/app.apk",
        releaseNotes = "",
        sha256Checksum = ""
      )
    )
    assertNotNull(
      AppUpdateEvaluator.validatePublishInput(
        latestVersionCode = 2,
        latestVersionName = "1.1.0",
        minSupportedVersionCode = 1,
        downloadUrl = validGithubApkUrl,
        releaseNotes = "",
        sha256Checksum = "not-a-valid-sha"
      )
    )
  }
}
