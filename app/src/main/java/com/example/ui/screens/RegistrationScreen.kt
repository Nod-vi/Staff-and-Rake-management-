package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AlternateEmail
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.auth.AccountRegistrationStatus
import com.example.auth.OnlineAccountService
import com.example.auth.UsernameAvailability
import com.example.model.DepotUserProfile
import com.example.model.StaffMember
import com.example.ui.theme.GlassRimHighlight
import com.example.ui.theme.GlassSurfaceBright
import com.example.ui.theme.GlassSurfaceMid
import com.example.ui.theme.GlassSurfaceSoft
import com.example.ui.theme.GlassTile
import com.example.ui.theme.SafetyAmber
import com.example.ui.theme.SafetyAmberContainer
import com.example.ui.theme.SafetyAmberOnContainer
import com.example.ui.theme.SignalCrimson
import com.example.ui.theme.SignalEmerald
import com.example.ui.theme.liquidGlassAccent
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegistrationScreen(
  firebaseUser: FirebaseUser,
  registrationInfo: AccountRegistrationStatus.NeedsRegistration,
  accountService: OnlineAccountService,
  onRegistrationSuccess: (DepotUserProfile) -> Unit,
  onCancelRegistration: () -> Unit
) {
  val coroutineScope = rememberCoroutineScope()

  BackHandler(onBack = onCancelRegistration)

  var fullName by rememberSaveable(firebaseUser.uid) {
    mutableStateOf(registrationInfo.gmailDisplayName)
  }
  val gmailEmail = remember(registrationInfo) { registrationInfo.gmailEmail }
  var username by rememberSaveable(firebaseUser.uid) {
    mutableStateOf(registrationInfo.prefillUsername)
  }
  var password by rememberSaveable(firebaseUser.uid) { mutableStateOf("") }
  var confirmPassword by rememberSaveable(firebaseUser.uid) { mutableStateOf("") }
  var passwordVisible by rememberSaveable(firebaseUser.uid) { mutableStateOf(false) }
  var selectedStaffId by rememberSaveable(firebaseUser.uid) {
    mutableStateOf(registrationInfo.prefillStaffId)
  }
  var phoneNumber1 by rememberSaveable(firebaseUser.uid) {
    mutableStateOf(registrationInfo.prefillPhone1)
  }
  var phoneNumber2 by rememberSaveable(firebaseUser.uid) {
    mutableStateOf(registrationInfo.prefillPhone2)
  }

  var staffList by remember { mutableStateOf<List<StaffMember>>(emptyList()) }
  var isLoadingStaff by remember { mutableStateOf(true) }
  var staffMenuExpanded by remember { mutableStateOf(false) }

  var usernameStatus by remember { mutableStateOf<UsernameAvailability>(UsernameAvailability.Idle) }
  var isSubmitting by remember { mutableStateOf(false) }
  var submitError by remember { mutableStateOf<String?>(null) }

  LaunchedEffect(firebaseUser.uid) {
    isLoadingStaff = true
    staffList = accountService.fetchStaffForRegistration()
    isLoadingStaff = false
  }

  // Live debounced online username uniqueness check against Cloud Firestore
  LaunchedEffect(username, firebaseUser.uid) {
    val clean = accountService.normalizeUsernameKey(username)
    if (clean.isEmpty()) {
      usernameStatus = UsernameAvailability.Idle
      return@LaunchedEffect
    }
    val formatErr = accountService.validateUsernameFormat(clean)
    if (formatErr != null) {
      usernameStatus = UsernameAvailability.Unavailable(formatErr)
      return@LaunchedEffect
    }
    usernameStatus = UsernameAvailability.Checking
    delay(350L)
    usernameStatus = accountService.checkUsernameAvailable(clean, firebaseUser.uid)
  }

  val selectedStaff = remember(staffList, selectedStaffId) {
    staffList.firstOrNull { it.id == selectedStaffId }
  }

  val fieldColors = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = GlassSurfaceSoft.copy(alpha = 0.72f),
    unfocusedContainerColor = GlassSurfaceSoft.copy(alpha = 0.55f),
    disabledContainerColor = GlassSurfaceSoft.copy(alpha = 0.38f),
    unfocusedBorderColor = GlassRimHighlight,
    focusedBorderColor = MaterialTheme.colorScheme.primary
  )

  fun submitRegistration() {
    submitError = null
    val cleanName = fullName.trim()
    if (cleanName.isBlank()) {
      submitError = "Please enter your Full Name."
      return
    }
    val userFormatErr = accountService.validateUsernameFormat(username)
    if (userFormatErr != null) {
      submitError = userFormatErr
      return
    }
    if (usernameStatus is UsernameAvailability.Unavailable) {
      submitError = (usernameStatus as UsernameAvailability.Unavailable).reason
      return
    }
    if (password.length < 6) {
      submitError = "Password must be at least 6 characters."
      return
    }
    if (password != confirmPassword) {
      submitError = "Passwords do not match."
      return
    }
    val phone1Err = accountService.validatePhoneNumber(phoneNumber1, required = true)
    if (phone1Err != null) {
      submitError = phone1Err
      return
    }
    val phone2Err = accountService.validatePhoneNumber(phoneNumber2, required = false)
    if (phone2Err != null) {
      submitError = phone2Err
      return
    }

    isSubmitting = true
    coroutineScope.launch {
      try {
        val latestAvailability = accountService.checkUsernameAvailable(username, firebaseUser.uid)
        usernameStatus = latestAvailability
        if (latestAvailability is UsernameAvailability.Unavailable) {
          submitError = latestAvailability.reason
          isSubmitting = false
          return@launch
        }

        val profile = accountService.completeRegistration(
          user = firebaseUser,
          fullName = cleanName,
          username = username,
          password = password,
          staffId = selectedStaffId,
          phoneNumber1 = phoneNumber1,
          phoneNumber2 = phoneNumber2
        )
        onRegistrationSuccess(profile)
      } catch (e: Exception) {
        submitError = e.localizedMessage ?: "Registration failed. Please try again."
      } finally {
        isSubmitting = false
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
      .padding(20.dp),
    contentAlignment = Alignment.TopCenter
  ) {
    GlassTile(
      modifier = Modifier
        .fillMaxWidth()
        .widthIn(max = 460.dp),
      shape = RoundedCornerShape(28.dp),
      topColor = GlassSurfaceBright,
      bottomColor = GlassSurfaceMid,
      elevation = 14.dp
    ) {
      Column(
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 20.dp, vertical = 22.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
      ) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
          IconButton(
            onClick = onCancelRegistration,
            enabled = !isSubmitting,
            modifier = Modifier
              .size(48.dp)
              .testTag("reg_back_button")
          ) {
            Icon(
              imageVector = Icons.AutoMirrored.Filled.ArrowBack,
              contentDescription = "Back to Login"
            )
          }
          Column(modifier = Modifier.weight(1f)) {
            Text(
              text = "Create Account",
              style = MaterialTheme.typography.headlineSmall,
              fontWeight = FontWeight.Bold,
              color = MaterialTheme.colorScheme.onSurface
            )
            Text(
              text = "Complete your PPIO Roster online registration",
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
        }

        // Required "Account does not exist" banner
        Surface(
          color = SafetyAmberContainer.copy(alpha = 0.88f),
          shape = RoundedCornerShape(14.dp),
          border = BorderStroke(1.dp, SafetyAmber.copy(alpha = 0.55f)),
          modifier = Modifier
            .fillMaxWidth()
            .testTag("registration_account_not_exist_banner")
        ) {
          Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
          ) {
            Icon(
              imageVector = Icons.Default.Info,
              contentDescription = null,
              tint = SafetyAmber
            )
            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = "Account does not exist",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = SafetyAmberOnContainer
              )
              Text(
                text = "Please register below to link your Gmail account and create your online login.",
                style = MaterialTheme.typography.bodySmall,
                color = SafetyAmberOnContainer
              )
            }
          }
        }

        if (submitError != null) {
          Surface(
            color = MaterialTheme.colorScheme.errorContainer,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
              .fillMaxWidth()
              .testTag("registration_error_banner")
          ) {
            Text(
              text = submitError!!,
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onErrorContainer,
              modifier = Modifier.padding(12.dp)
            )
          }
        }

        // (i) Full Name — prefilled from Gmail, editable
        OutlinedTextField(
          value = fullName,
          onValueChange = {
            fullName = it.take(100)
            submitError = null
          },
          label = { Text("Full Name *") },
          leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
          singleLine = true,
          enabled = !isSubmitting,
          keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
          shape = RoundedCornerShape(16.dp),
          colors = fieldColors,
          modifier = Modifier
            .fillMaxWidth()
            .testTag("reg_full_name_input")
        )

        // (ii) Mail Id — Gmail account, non-editable
        OutlinedTextField(
          value = gmailEmail,
          onValueChange = {},
          label = { Text("Mail Id (Gmail)") },
          leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) },
          readOnly = true,
          enabled = false,
          singleLine = true,
          shape = RoundedCornerShape(16.dp),
          colors = fieldColors,
          supportingText = { Text("Verified from Gmail · Cannot be edited") },
          modifier = Modifier
            .fillMaxWidth()
            .testTag("reg_email_input")
        )

        // (iii) User Name — live online duplicate check
        OutlinedTextField(
          value = username,
          onValueChange = { raw ->
            username = raw.replace(" ", "").take(32)
            submitError = null
          },
          label = { Text("User Name *") },
          placeholder = { Text("e.g. ram.kumar") },
          leadingIcon = { Icon(Icons.Default.AlternateEmail, contentDescription = null) },
          trailingIcon = {
            when (usernameStatus) {
              UsernameAvailability.Checking -> CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp
              )
              UsernameAvailability.Available -> Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = "Username available",
                tint = SignalEmerald
              )
              is UsernameAvailability.Unavailable -> Icon(
                imageVector = Icons.Default.ErrorOutline,
                contentDescription = "Username unavailable",
                tint = SignalCrimson
              )
              UsernameAvailability.Idle -> Unit
            }
          },
          isError = usernameStatus is UsernameAvailability.Unavailable,
          supportingText = {
            when (val st = usernameStatus) {
              UsernameAvailability.Checking -> Text("Checking availability online...")
              UsernameAvailability.Available -> Text(
                "Username is available",
                color = SignalEmerald,
                fontWeight = FontWeight.Medium
              )
              is UsernameAvailability.Unavailable -> Text(
                st.reason,
                color = SignalCrimson
              )
              UsernameAvailability.Idle -> Text("3–32 chars (a-z, 0-9, ., _, -). Must be unique.")
            }
          },
          singleLine = true,
          enabled = !isSubmitting,
          keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Ascii,
            imeAction = ImeAction.Next
          ),
          shape = RoundedCornerShape(16.dp),
          colors = fieldColors,
          modifier = Modifier
            .fillMaxWidth()
            .testTag("reg_username_input")
        )

        // (iv) Create Password + Confirm Password
        val passwordVisual = if (passwordVisible) {
          VisualTransformation.None
        } else {
          PasswordVisualTransformation()
        }
        OutlinedTextField(
          value = password,
          onValueChange = {
            password = it.take(64)
            submitError = null
          },
          label = { Text("Create Password *") },
          leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
          trailingIcon = {
            IconButton(
              onClick = { passwordVisible = !passwordVisible },
              modifier = Modifier
                .size(48.dp)
                .testTag("reg_toggle_password_visibility")
            ) {
              Icon(
                imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                contentDescription = if (passwordVisible) "Hide password" else "Show password"
              )
            }
          },
          visualTransformation = passwordVisual,
          singleLine = true,
          enabled = !isSubmitting,
          supportingText = { Text("Minimum 6 characters · Synced online with your Gmail") },
          keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Password,
            imeAction = ImeAction.Next
          ),
          shape = RoundedCornerShape(16.dp),
          colors = fieldColors,
          modifier = Modifier
            .fillMaxWidth()
            .testTag("reg_password_input")
        )

        OutlinedTextField(
          value = confirmPassword,
          onValueChange = {
            confirmPassword = it.take(64)
            submitError = null
          },
          label = { Text("Confirm Password *") },
          leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
          visualTransformation = passwordVisual,
          isError = confirmPassword.isNotEmpty() && confirmPassword != password,
          supportingText = if (confirmPassword.isNotEmpty() && confirmPassword != password) {
            { Text("Passwords do not match", color = SignalCrimson) }
          } else {
            null
          },
          singleLine = true,
          enabled = !isSubmitting,
          keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Password,
            imeAction = ImeAction.Next
          ),
          shape = RoundedCornerShape(16.dp),
          colors = fieldColors,
          modifier = Modifier
            .fillMaxWidth()
            .testTag("reg_confirm_password_input")
        )

        // (v) Drop-down field with staff names for linking staff to account
        ExposedDropdownMenuBox(
          expanded = staffMenuExpanded,
          onExpandedChange = {
            if (!isSubmitting && !isLoadingStaff) {
              staffMenuExpanded = !staffMenuExpanded
            }
          }
        ) {
          val staffDisplayLabel = when {
            isLoadingStaff -> "Loading staff directory..."
            selectedStaff != null -> "#${selectedStaff.serialNo} ${selectedStaff.name} (${selectedStaff.designationEnum.label})"
            else -> "Not linked (Select staff name)"
          }
          OutlinedTextField(
            value = staffDisplayLabel,
            onValueChange = {},
            readOnly = true,
            enabled = !isSubmitting && !isLoadingStaff,
            label = { Text("Link Staff Profile") },
            leadingIcon = { Icon(Icons.Default.Badge, contentDescription = null) },
            trailingIcon = {
              ExposedDropdownMenuDefaults.TrailingIcon(expanded = staffMenuExpanded)
            },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            colors = fieldColors,
            modifier = Modifier
              .menuAnchor()
              .fillMaxWidth()
              .testTag("reg_staff_dropdown")
          )
          ExposedDropdownMenu(
            expanded = staffMenuExpanded,
            onDismissRequest = { staffMenuExpanded = false },
            modifier = Modifier.heightIn(max = 280.dp)
          ) {
            DropdownMenuItem(
              text = {
                Text(
                  text = "None / Not in staff list",
                  style = MaterialTheme.typography.bodyMedium,
                  color = MaterialTheme.colorScheme.onSurfaceVariant
                )
              },
              onClick = {
                selectedStaffId = ""
                staffMenuExpanded = false
              }
            )
            staffList.forEach { staff ->
              DropdownMenuItem(
                text = {
                  Text(
                    text = "#${staff.serialNo} ${staff.name} · ${staff.designationEnum.label}",
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                  )
                },
                onClick = {
                  selectedStaffId = staff.id
                  if (phoneNumber1.isBlank() && staff.phoneNumber.isNotBlank()) {
                    phoneNumber1 = staff.phoneNumber
                  }
                  if (phoneNumber2.isBlank() && staff.secondaryPhoneNumber.isNotBlank()) {
                    phoneNumber2 = staff.secondaryPhoneNumber
                  }
                  staffMenuExpanded = false
                }
              )
            }
          }
        }

        // (vi) Phone Number 1 (Required)
        OutlinedTextField(
          value = phoneNumber1,
          onValueChange = { raw ->
            phoneNumber1 = raw.filter { it.isDigit() || it == '+' || it == '-' || it == ' ' }.take(20)
            submitError = null
          },
          label = { Text("Phone Number 1 *") },
          placeholder = { Text("e.g. 9876543210") },
          leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null) },
          singleLine = true,
          enabled = !isSubmitting,
          keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Phone,
            imeAction = ImeAction.Next
          ),
          shape = RoundedCornerShape(16.dp),
          colors = fieldColors,
          modifier = Modifier
            .fillMaxWidth()
            .testTag("reg_phone_1_input")
        )

        // (vii) Phone Number 2 (Optional)
        OutlinedTextField(
          value = phoneNumber2,
          onValueChange = { raw ->
            phoneNumber2 = raw.filter { it.isDigit() || it == '+' || it == '-' || it == ' ' }.take(20)
            submitError = null
          },
          label = { Text("Phone Number 2 (Optional)") },
          placeholder = { Text("Alternate contact number") },
          leadingIcon = { Icon(Icons.Default.Phone, contentDescription = null) },
          singleLine = true,
          enabled = !isSubmitting,
          keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Phone,
            imeAction = ImeAction.Done
          ),
          shape = RoundedCornerShape(16.dp),
          colors = fieldColors,
          modifier = Modifier
            .fillMaxWidth()
            .testTag("reg_phone_2_input")
        )

        Spacer(modifier = Modifier.height(6.dp))

        val canSubmit = !isSubmitting &&
          usernameStatus !is UsernameAvailability.Checking &&
          usernameStatus !is UsernameAvailability.Unavailable

        Button(
          onClick = { submitRegistration() },
          enabled = canSubmit,
          elevation = ButtonDefaults.buttonElevation(0.dp, 0.dp, 0.dp, 0.dp, 0.dp),
          colors = ButtonDefaults.buttonColors(
            containerColor = if (canSubmit) Color.Transparent else MaterialTheme.colorScheme.primary.copy(alpha = 0.55f),
            contentColor = Color.White
          ),
          shape = RoundedCornerShape(16.dp),
          modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .let { mod ->
              if (canSubmit) mod.liquidGlassAccent(shape = RoundedCornerShape(16.dp), elevation = 6.dp)
              else mod
            }
            .testTag("reg_submit_button")
        ) {
          if (isSubmitting) {
            CircularProgressIndicator(
              modifier = Modifier.size(20.dp),
              color = Color.White,
              strokeWidth = 2.dp
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text("Creating Account...", style = MaterialTheme.typography.labelLarge)
          } else {
            Text(
              text = "Create Account",
              style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
            )
          }
        }

        OutlinedButton(
          onClick = onCancelRegistration,
          enabled = !isSubmitting,
          colors = ButtonDefaults.outlinedButtonColors(containerColor = GlassSurfaceSoft),
          border = BorderStroke(1.dp, GlassRimHighlight),
          shape = RoundedCornerShape(16.dp),
          modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .testTag("reg_cancel_button")
        ) {
          Text("Back to Login", fontWeight = FontWeight.SemiBold)
        }
      }
    }
  }
}
