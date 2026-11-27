package com.example.auth

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.Companion.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

private const val TAG = "Auth"

fun FirebaseAuth.authStateFlow(): Flow<FirebaseUser?> = callbackFlow {
  val listener = FirebaseAuth.AuthStateListener { auth ->
    trySend(auth.currentUser)
  }
  addAuthStateListener(listener)
  awaitClose { removeAuthStateListener(listener) }
}

private fun resolveDefaultWebClientId(context: Context): String? {
  return try {
    val resId = context.resources.getIdentifier("default_web_client_id", "string", context.packageName)
    if (resId != 0) context.getString(resId).takeIf { it.isNotBlank() } else null
  } catch (_: Exception) {
    null
  }
}

fun attemptAutoSignIn(
  context: Context,
  credentialManager: CredentialManager,
  onAuthSuccess: () -> Unit,
  onUnauthenticated: () -> Unit,
  scope: CoroutineScope
) {
  if (Firebase.auth.currentUser != null) {
    onAuthSuccess()
    return
  }
  val clientId = resolveDefaultWebClientId(context)
  if (clientId == null) {
    onUnauthenticated()
    return
  }

  val googleIdOption = GetGoogleIdOption.Builder()
    .setFilterByAuthorizedAccounts(true)
    .setServerClientId(clientId)
    .setAutoSelectEnabled(true)
    .build()

  val request = GetCredentialRequest.Builder()
    .addCredentialOption(googleIdOption)
    .build()

  scope.launch {
    try {
      val result = credentialManager.getCredential(context, request)
      val credential = result.credential
      if (credential is CustomCredential && credential.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
        val googleIdToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
        val authCredential = GoogleAuthProvider.getCredential(googleIdToken, null)
        Firebase.auth.signInWithCredential(authCredential).await()
        onAuthSuccess()
      } else {
        onUnauthenticated()
      }
    } catch (_: Exception) {
      onUnauthenticated()
    }
  }
}

fun onGoogleSignInClicked(
  context: Context,
  credentialManager: CredentialManager,
  onAuthSuccess: () -> Unit,
  onAuthError: (String) -> Unit,
  scope: CoroutineScope,
  onAuthCancelled: () -> Unit = {}
) {
  val clientId = resolveDefaultWebClientId(context)
  if (clientId == null) {
    onAuthError("Google Sign-In configuration missing: default_web_client_id not found")
    return
  }

  val signInOption = GetSignInWithGoogleOption.Builder(serverClientId = clientId).build()
  val request = GetCredentialRequest.Builder()
    .addCredentialOption(signInOption)
    .build()

  scope.launch {
    try {
      val activityContext = (context as? Activity) ?: context
      val result = credentialManager.getCredential(activityContext, request)
      val credential = result.credential
      if (credential is CustomCredential && credential.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
        val googleIdToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
        val authCredential = GoogleAuthProvider.getCredential(googleIdToken, null)
        Firebase.auth.signInWithCredential(authCredential).await()
        onAuthSuccess()
      } else {
        onAuthError("Unexpected credential type returned.")
      }
    } catch (e: GetCredentialCancellationException) {
      Log.w(TAG, "Google Sign-In flow cancelled or dismissed: ${e.message}", e)
      onAuthCancelled()
    } catch (e: Exception) {
      Log.e(TAG, "Google Sign-In failed", e)
      onAuthError(e.localizedMessage ?: "Sign in failed. Please try again.")
    }
  }
}

sealed interface GoogleSignInAttemptResult {
  data class Success(val user: FirebaseUser) : GoogleSignInAttemptResult
  data object Cancelled : GoogleSignInAttemptResult
  data class Error(val message: String) : GoogleSignInAttemptResult
}

/**
 * Establishes an online Firebase Auth session for the user's linked Gmail account via Jetpack
 * Credential Manager (silent authorized account first, falling back to interactive Gmail chooser).
 * Uses strictly one credential option per GetCredentialRequest for Android API 36 compatibility.
 */
suspend fun authenticateLinkedGmailOnline(
  context: Context,
  credentialManager: CredentialManager
): GoogleSignInAttemptResult {
  val existing = Firebase.auth.currentUser
  if (existing != null) {
    return GoogleSignInAttemptResult.Success(existing)
  }

  val clientId = resolveDefaultWebClientId(context)
    ?: return GoogleSignInAttemptResult.Error(
      "Google Sign-In configuration missing: default_web_client_id not found"
    )

  val activityContext = (context as? Activity) ?: context

  // 1. Try silent sign-in for already-authorized Gmail account on this device
  val silentOption = GetGoogleIdOption.Builder()
    .setFilterByAuthorizedAccounts(true)
    .setServerClientId(clientId)
    .setAutoSelectEnabled(true)
    .build()
  val silentRequest = GetCredentialRequest.Builder()
    .addCredentialOption(silentOption)
    .build()

  val silentSuccess = try {
    val result = credentialManager.getCredential(activityContext, silentRequest)
    val credential = result.credential
    if (credential is CustomCredential && credential.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
      val idToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
      val authCredential = GoogleAuthProvider.getCredential(idToken, null)
      val authResult = Firebase.auth.signInWithCredential(authCredential).await()
      authResult.user
    } else {
      null
    }
  } catch (_: Exception) {
    null
  }

  if (silentSuccess != null) {
    return GoogleSignInAttemptResult.Success(silentSuccess)
  }

  // 2. Fallback to interactive Gmail chooser so user verifies the Gmail account linked to their username
  val interactiveOption = GetSignInWithGoogleOption.Builder(serverClientId = clientId).build()
  val interactiveRequest = GetCredentialRequest.Builder()
    .addCredentialOption(interactiveOption)
    .build()

  return try {
    val result = credentialManager.getCredential(activityContext, interactiveRequest)
    val credential = result.credential
    if (credential is CustomCredential && credential.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
      val idToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
      val authCredential = GoogleAuthProvider.getCredential(idToken, null)
      val authResult = Firebase.auth.signInWithCredential(authCredential).await()
      val signedInUser = authResult.user
      if (signedInUser != null) {
        GoogleSignInAttemptResult.Success(signedInUser)
      } else {
        GoogleSignInAttemptResult.Error("Unable to complete online sign-in.")
      }
    } else {
      GoogleSignInAttemptResult.Error("Unexpected credential type returned.")
    }
  } catch (e: GetCredentialCancellationException) {
    Log.w(TAG, "Online Gmail verification cancelled: ${e.message}", e)
    GoogleSignInAttemptResult.Cancelled
  } catch (e: Exception) {
    Log.e(TAG, "Online Gmail verification failed", e)
    GoogleSignInAttemptResult.Error(e.localizedMessage ?: "Online sign-in failed. Please try again.")
  }
}

suspend fun signOutAndWait(credentialManager: CredentialManager) {
  withContext(NonCancellable) {
    try {
      credentialManager.clearCredentialState(ClearCredentialStateRequest())
    } catch (e: Exception) {
      Log.w(TAG, "Failed to clear credential state: ${e.message}")
    } finally {
      Firebase.auth.signOut()
    }
  }
}

fun signOut(
  credentialManager: CredentialManager,
  scope: CoroutineScope,
  onSignOutComplete: () -> Unit = {}
) {
  scope.launch {
    signOutAndWait(credentialManager)
    onSignOutComplete()
  }
}
