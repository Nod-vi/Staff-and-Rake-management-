package com.example

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ApplicationProvider
import com.example.auth.DepotRoleManager
import com.example.data.LocalRosterRepository
import com.example.ui.theme.MyApplicationTheme
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class LoginFlowRobolectricTest {

  @get:Rule
  val composeTestRule = createComposeRule()

  private lateinit var context: Context

  @Before
  fun setUp() {
    context = ApplicationProvider.getApplicationContext()
    if (FirebaseApp.getApps(context).isEmpty()) {
      FirebaseApp.initializeApp(
        context,
        FirebaseOptions.Builder()
          .setApplicationId("1:1234567890:android:abcdef123456")
          .setApiKey("AIzaSyTestKeyForRobolectricOnly123456789")
          .setProjectId("ppio-test-project")
          .build()
      )
    }
    LocalRosterRepository.resetSingletonForTests()
    DepotRoleManager.saveActiveCredentialUid(context, null)
    File(context.filesDir, "ppio_local_roster.json").delete()
    File(context.filesDir, "ppio_local_roster.json.tmp").delete()
    File(context.filesDir, "ppio_local_roster.json.bak").delete()
    File(context.filesDir, "ppio_offline_test_sandbox.json").delete()
    File(context.filesDir, "ppio_offline_test_sandbox.json.tmp").delete()
    File(context.filesDir, "ppio_offline_test_sandbox.json.bak").delete()
  }

  @Test
  fun credentialLogin_logout_andRelogin_opensRosterWithoutCrashing() {
    composeTestRule.setContent {
      MyApplicationTheme {
        AppNavigation()
      }
    }

    // Wait for AuthScreen to finish initial auto-check and enable Sign In
    composeTestRule.waitUntil(timeoutMillis = 5_000L) {
      composeTestRule.onAllNodesWithTag("login_username_input").fetchSemanticsNodes().isNotEmpty()
    }
    composeTestRule.waitForIdle()

    // Enter admin / admin and press Sign In
    composeTestRule.onNodeWithTag("login_username_input").performScrollTo().performTextInput("admin")
    composeTestRule.onNodeWithTag("login_password_input").performScrollTo().performTextInput("admin")
    composeTestRule.onNodeWithTag("credential_sign_in_button").performScrollTo().performClick()
    composeTestRule.waitForIdle()

    // Verify PpioRosterShell loads cleanly in isolated Offline Test Mode without Gmail or server sync
    composeTestRule.waitUntil(timeoutMillis = 8_000L) {
      composeTestRule.onAllNodesWithTag("active_role_badge").fetchSemanticsNodes().isNotEmpty()
    }
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("active_role_badge").assertIsDisplayed()
    composeTestRule.onNodeWithTag("user_menu_button").assertIsDisplayed()
    composeTestRule.onNodeWithTag("sync_status_banner").assertIsDisplayed()

    // Verify cloud local cache file was never written by the offline test sandbox
    val cloudCacheFile = File(context.filesDir, "ppio_local_roster.json")
    org.junit.Assert.assertFalse("Offline test sandbox must not write to cloud cache file", cloudCacheFile.exists())

    // Verify sandbox admin has no Gmail address attached
    val sandboxSession = DepotRoleManager.resolveOfflineTestAdminSession()
    org.junit.Assert.assertTrue(sandboxSession.email.isNullOrBlank())
    org.junit.Assert.assertFalse(sandboxSession.isGoogleAuthenticated)
    org.junit.Assert.assertTrue(sandboxSession.isOfflineTestMode)

    // Logout via top-right user menu
    composeTestRule.onNodeWithTag("user_menu_button").performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("menu_logout").performClick()

    // Verify return to AuthScreen
    composeTestRule.waitUntil(timeoutMillis = 5_000L) {
      composeTestRule.onAllNodesWithTag("credential_sign_in_button").fetchSemanticsNodes().isNotEmpty()
    }
    composeTestRule.waitForIdle()

    // Wrong short password for admin shows offline test error without launching Gmail picker
    composeTestRule.onNodeWithTag("login_username_input").performScrollTo().performTextInput("admin")
    composeTestRule.onNodeWithTag("login_password_input").performScrollTo().performTextInput("wrong")
    composeTestRule.onNodeWithTag("credential_sign_in_button").performScrollTo().performClick()
    composeTestRule.waitForIdle()
    composeTestRule.onNodeWithTag("login_error_banner").assertIsDisplayed()
  }
}
