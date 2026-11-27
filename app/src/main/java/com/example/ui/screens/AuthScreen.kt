package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import com.example.R
import com.example.auth.DepotRoleManager
import com.example.auth.GoogleSignInAttemptResult
import com.example.auth.OnlineCredentialVerifyResult
import com.example.auth.authenticateLinkedGmailOnline
import com.example.auth.onGoogleSignInClicked
import com.example.auth.signOutAndWait
import com.example.ui.theme.GlassRimHighlight
import com.example.ui.theme.GlassSurfaceBright
import com.example.ui.theme.GlassSurfaceMid
import com.example.ui.theme.GlassSurfaceSoft
import com.example.ui.theme.GlassTile
import com.example.ui.theme.liquidGlassAccent
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.launch

@Composable
fun AuthScreen(
  onCredentialCheckStateChange: (Boolean) -> Unit,
  onVerifyOnlineCredentials: suspend (FirebaseUser, String, String) -> OnlineCredentialVerifyResult,
  onOnlineCredentialVerified: (FirebaseUser, OnlineCredentialVerifyResult) -> Unit,
  onGoogleAuthSuccess: () -> Unit,
  onOfflineTestLogin: () -> Unit
) {
  val context = LocalContext.current
  val coroutineScope = rememberCoroutineScope()
  val focusManager = LocalFocusManager.current
  val credentialManager = remember(context) { CredentialManager.create(context) }

  var username by rememberSaveable { mutableStateOf("") }
  var password by rememberSaveable { mutableStateOf("") }
  var passwordVisible by rememberSaveable { mutableStateOf(false) }
  var isCredentialLoading by remember { mutableStateOf(false) }
  var isGoogleLoading by remember { mutableStateOf(false) }
  var errorMessage by remember { mutableStateOf<String?>(null) }

  fun submitOnlineCredentialLogin() {
    val cleanUser = username.trim()
    if (cleanUser.isBlank() || password.isEmpty()) {
      errorMessage = "Enter your username and password."
      return
    }
    focusManager.clearFocus()
    errorMessage = null

    if (DepotRoleManager.isOfflineTestAdminCredentials(cleanUser, password)) {
      onOfflineTestLogin()
      return
    }
    if (cleanUser.equals(DepotRoleManager.OFFLINE_TEST_ADMIN_USERNAME, ignoreCase = true) && password.length < 6) {
      errorMessage = "Invalid offline test password."
      return
    }

    isCredentialLoading = true
    onCredentialCheckStateChange(true)
    coroutineScope.launch {
      try {
        when (val googleResult = authenticateLinkedGmailOnline(context, credentialManager)) {
          is GoogleSignInAttemptResult.Cancelled -> {
            errorMessage = null
            onCredentialCheckStateChange(false)
          }
          is GoogleSignInAttemptResult.Error -> {
            errorMessage = googleResult.message
            onCredentialCheckStateChange(false)
          }
          is GoogleSignInAttemptResult.Success -> {
            val verifyResult = onVerifyOnlineCredentials(googleResult.user, cleanUser, password)
            when (verifyResult) {
              is OnlineCredentialVerifyResult.InvalidCredentials -> {
                signOutAndWait(credentialManager)
                errorMessage = verifyResult.message
                onCredentialCheckStateChange(false)
              }
              is OnlineCredentialVerifyResult.AccountDoesNotExist,
              is OnlineCredentialVerifyResult.Verified -> {
                onOnlineCredentialVerified(googleResult.user, verifyResult)
                onCredentialCheckStateChange(false)
              }
            }
          }
        }
      } catch (e: Exception) {
        signOutAndWait(credentialManager)
        errorMessage = e.localizedMessage ?: "Online sign in failed. Please try again."
        onCredentialCheckStateChange(false)
      } finally {
        isCredentialLoading = false
      }
    }
  }

  Box(
    modifier = Modifier
      .fillMaxSize()
      .background(Color.Transparent)
      .safeDrawingPadding()
      .imePadding()
      .verticalScroll(rememberScrollState())
      .padding(24.dp),
    contentAlignment = Alignment.Center
  ) {
    GlassTile(
      modifier = Modifier
        .fillMaxWidth()
        .widthIn(max = 420.dp),
      shape = RoundedCornerShape(30.dp),
      topColor = GlassSurfaceBright,
      bottomColor = GlassSurfaceMid,
      elevation = 14.dp
    ) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 24.dp, vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally
      ) {
        GlassTile(
          shape = RoundedCornerShape(24.dp),
          topColor = GlassSurfaceBright,
          bottomColor = GlassSurfaceSoft,
          elevation = 8.dp,
          modifier = Modifier.padding(2.dp)
        ) {
          Image(
            painter = painterResource(id = R.drawable.ic_launcher_ppio_fg_1794862891068),
            contentDescription = "PPIO Roster depot emblem",
            modifier = Modifier
              .size(78.dp)
              .clip(RoundedCornerShape(22.dp)),
            contentScale = ContentScale.Crop
          )
        }

        Spacer(modifier = Modifier.height(14.dp))

        Text(
          text = "PPIO ROSTER",
          style = MaterialTheme.typography.headlineMedium,
          fontWeight = FontWeight.Bold,
          color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
          text = "Online Depot Roster & Shift Sync",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(22.dp))

        if (errorMessage != null) {
          Surface(
            color = MaterialTheme.colorScheme.errorContainer,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
              .fillMaxWidth()
              .padding(bottom = 14.dp)
              .testTag("login_error_banner")
          ) {
            Text(
              text = errorMessage!!,
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onErrorContainer,
              modifier = Modifier.padding(12.dp)
            )
          }
        }

        // 1. Username field
        OutlinedTextField(
          value = username,
          onValueChange = {
            username = it
            if (errorMessage != null) errorMessage = null
          },
          label = { Text("Username") },
          leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
          singleLine = true,
          enabled = !isCredentialLoading && !isGoogleLoading,
          keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Text,
            imeAction = ImeAction.Next
          ),
          keyboardActions = KeyboardActions(
            onNext = { focusManager.moveFocus(FocusDirection.Down) }
          ),
          shape = RoundedCornerShape(16.dp),
          colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = GlassSurfaceSoft.copy(alpha = 0.7f),
            unfocusedContainerColor = GlassSurfaceSoft.copy(alpha = 0.55f),
            unfocusedBorderColor = GlassRimHighlight,
            focusedBorderColor = MaterialTheme.colorScheme.primary
          ),
          modifier = Modifier
            .fillMaxWidth()
            .testTag("login_username_input")
        )

        Spacer(modifier = Modifier.height(12.dp))

        // 2. Password field
        OutlinedTextField(
          value = password,
          onValueChange = {
            password = it
            if (errorMessage != null) errorMessage = null
          },
          label = { Text("Password") },
          leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
          trailingIcon = {
            IconButton(
              onClick = { passwordVisible = !passwordVisible },
              modifier = Modifier
                .size(48.dp)
                .testTag("toggle_password_visibility")
            ) {
              Icon(
                imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                contentDescription = if (passwordVisible) "Hide password" else "Show password"
              )
            }
          },
          singleLine = true,
          enabled = !isCredentialLoading && !isGoogleLoading,
          visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
          keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Password,
            imeAction = ImeAction.Done
          ),
          keyboardActions = KeyboardActions(
            onDone = { submitOnlineCredentialLogin() }
          ),
          shape = RoundedCornerShape(16.dp),
          colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = GlassSurfaceSoft.copy(alpha = 0.7f),
            unfocusedContainerColor = GlassSurfaceSoft.copy(alpha = 0.55f),
            unfocusedBorderColor = GlassRimHighlight,
            focusedBorderColor = MaterialTheme.colorScheme.primary
          ),
          modifier = Modifier
            .fillMaxWidth()
            .testTag("login_password_input")
        )

        Spacer(modifier = Modifier.height(16.dp))

        // 3. Login button (Online)
        val loginEnabled = !isCredentialLoading && !isGoogleLoading
        Button(
          onClick = { submitOnlineCredentialLogin() },
          enabled = loginEnabled,
          elevation = ButtonDefaults.buttonElevation(0.dp, 0.dp, 0.dp, 0.dp, 0.dp),
          colors = ButtonDefaults.buttonColors(
            containerColor = if (loginEnabled) Color.Transparent else MaterialTheme.colorScheme.primary.copy(alpha = 0.55f),
            contentColor = Color.White
          ),
          shape = RoundedCornerShape(16.dp),
          modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .let { mod ->
              if (loginEnabled) mod.liquidGlassAccent(shape = RoundedCornerShape(16.dp), elevation = 6.dp)
              else mod
            }
            .testTag("credential_sign_in_button")
        ) {
          if (isCredentialLoading) {
            CircularProgressIndicator(
              modifier = Modifier.size(20.dp),
              color = Color.White,
              strokeWidth = 2.dp
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text("Signing in online...", style = MaterialTheme.typography.labelLarge)
          } else {
            Icon(
              imageVector = Icons.AutoMirrored.Filled.Login,
              contentDescription = null,
              modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
              text = "Login",
              style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
            )
          }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // 4. "or" divider text
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
          HorizontalDivider(modifier = Modifier.weight(1f), color = GlassRimHighlight)
          Text(
            text = "or",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
          HorizontalDivider(modifier = Modifier.weight(1f), color = GlassRimHighlight)
        }

        Spacer(modifier = Modifier.height(18.dp))

        // 5. Login with Gmail button
        OutlinedButton(
          onClick = {
            errorMessage = null
            isGoogleLoading = true
            onGoogleSignInClicked(
              context = context,
              credentialManager = credentialManager,
              onAuthSuccess = {
                isGoogleLoading = false
                onGoogleAuthSuccess()
              },
              onAuthError = { msg ->
                isGoogleLoading = false
                errorMessage = msg
              },
              scope = coroutineScope,
              onAuthCancelled = {
                isGoogleLoading = false
              }
            )
          },
          enabled = !isCredentialLoading && !isGoogleLoading,
          colors = ButtonDefaults.outlinedButtonColors(containerColor = GlassSurfaceSoft),
          border = BorderStroke(1.dp, GlassRimHighlight),
          shape = RoundedCornerShape(16.dp),
          modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .testTag("google_sign_in_button")
        ) {
          if (isGoogleLoading) {
            CircularProgressIndicator(
              modifier = Modifier.size(20.dp),
              color = MaterialTheme.colorScheme.primary,
              strokeWidth = 2.dp
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text("Connecting Gmail...", style = MaterialTheme.typography.labelLarge)
          } else {
            Icon(
              imageVector = Icons.Default.Email,
              contentDescription = null,
              modifier = Modifier.size(18.dp),
              tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
              text = "Login with Gmail",
              style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
            )
          }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
          text = "Cloud accounts sync via Gmail. Offline admin test login stays on-device with no server sync.",
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          textAlign = TextAlign.Center
        )
      }
    }
  }
}
