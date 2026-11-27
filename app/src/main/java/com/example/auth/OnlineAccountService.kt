package com.example.auth

import com.example.data.DefaultPpioRoster
import com.example.model.DepotUserProfile
import com.example.model.RoleInvite
import com.example.model.StaffMember
import com.example.model.UserRole
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Source
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

sealed interface AccountRegistrationStatus {
  data class Registered(val profile: DepotUserProfile) : AccountRegistrationStatus
  data class NeedsRegistration(
    val gmailDisplayName: String,
    val gmailEmail: String,
    val prefillUsername: String = "",
    val prefillStaffId: String = "",
    val prefillPhone1: String = "",
    val prefillPhone2: String = "",
    val existingRole: UserRole? = null
  ) : AccountRegistrationStatus
}

sealed interface OnlineCredentialVerifyResult {
  data class Verified(val profile: DepotUserProfile) : OnlineCredentialVerifyResult
  data class AccountDoesNotExist(val needsRegistration: AccountRegistrationStatus.NeedsRegistration) : OnlineCredentialVerifyResult
  data class InvalidCredentials(val message: String) : OnlineCredentialVerifyResult
}

sealed interface UsernameAvailability {
  data object Idle : UsernameAvailability
  data object Checking : UsernameAvailability
  data object Available : UsernameAvailability
  data class Unavailable(val reason: String) : UsernameAvailability
}

class OnlineAccountService(
  private val db: FirebaseFirestore,
  private val depotId: String = DepotRoleManager.SHARED_DEPOT_ID
) {
  private fun depotDoc() = db.collection("depots").document(depotId)

  fun normalizeUsernameKey(raw: String): String = raw.trim().lowercase()

  fun validateUsernameFormat(raw: String): String? {
    val key = normalizeUsernameKey(raw)
    if (key.length < 3) return "Username must be at least 3 characters."
    if (key.length > 32) return "Username must be 32 characters or fewer."
    if (!USERNAME_REGEX.matches(key)) {
      return "Use only lowercase letters, numbers, '.', '_' or '-'."
    }
    return null
  }

  fun validatePhoneNumber(raw: String, required: Boolean): String? {
    val clean = raw.trim()
    if (clean.isEmpty()) {
      return if (required) "Phone number 1 is required." else null
    }
    val digitCount = clean.count { it.isDigit() }
    if (clean.length > 20 || digitCount < 7) {
      return "Enter a valid phone number (7–20 characters)."
    }
    return null
  }

  private fun extractGmailEmail(user: FirebaseUser): String {
    return (
      user.email?.trim()?.takeIf { it.isNotBlank() }
        ?: user.providerData.firstNotNullOfOrNull { it.email?.trim()?.takeIf { e -> e.isNotBlank() } }
        ?: ""
      ).lowercase().take(200)
  }

  private fun extractGmailDisplayName(user: FirebaseUser): String {
    return (
      user.displayName?.trim()?.takeIf { it.isNotBlank() }
        ?: user.providerData.firstNotNullOfOrNull { it.displayName?.trim()?.takeIf { n -> n.isNotBlank() } }
        ?: ""
      ).take(100)
  }

  private suspend fun getDocumentOfflineAware(
    docRef: DocumentReference,
    serverTimeoutMs: Long = 4_000L
  ): Pair<DocumentSnapshot?, Boolean> {
    var servedFromCache = false
    val defaultSnap = try {
      withTimeoutOrNull(serverTimeoutMs) {
        docRef.get(Source.DEFAULT).await()
      }
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      if (e is FirebaseFirestoreException && e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
        throw e
      }
      null
    }
    if (defaultSnap != null) {
      return defaultSnap to defaultSnap.metadata.isFromCache
    }
    val cachedSnap = try {
      servedFromCache = true
      docRef.get(Source.CACHE).await()
    } catch (e: CancellationException) {
      throw e
    } catch (_: Exception) {
      null
    }
    return cachedSnap to servedFromCache
  }

  suspend fun checkRegistrationStatus(user: FirebaseUser): AccountRegistrationStatus {
    val uid = user.uid
    val email = extractGmailEmail(user)
    val displayName = extractGmailDisplayName(user)

    val (roleSnap, roleFromCache) = getDocumentOfflineAware(
      depotDoc().collection("userRoles").document(uid)
    )
    val (credSnap, _) = getDocumentOfflineAware(
      depotDoc().collection("userCredentials").document(uid),
      serverTimeoutMs = 3_000L
    )

    val existingProfile = if (roleSnap != null && roleSnap.exists()) {
      roleSnap.toObject(DepotUserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
    } else {
      null
    }

    val hasValidCredentials = credSnap != null &&
      credSnap.exists() &&
      !credSnap.getString("passwordHash").isNullOrBlank() &&
      !credSnap.getString("passwordSalt").isNullOrBlank()

    // Allow already-registered accounts to open immediately from Firestore persistent cache
    // when the network is poor or unavailable.
    if (existingProfile != null && existingProfile.username.isNotBlank() &&
      (hasValidCredentials || (roleFromCache && existingProfile.registered))
    ) {
      return AccountRegistrationStatus.Registered(existingProfile)
    }

    // If the server could not be reached and no cached registered profile exists yet,
    // do NOT falsely redirect to "Account does not exist" registration.
    if (roleSnap == null) {
      throw IllegalStateException(
        "Unable to reach cloud server and no cached account was found. Please check your internet connection and tap Retry."
      )
    }

    val invite = if (email.isNotBlank() && EMAIL_KEY_REGEX.matches(email)) {
      runCatching {
        val (invSnap, _) = getDocumentOfflineAware(
          depotDoc().collection("roleInvites").document(email),
          serverTimeoutMs = 3_000L
        )
        if (invSnap != null && invSnap.exists()) {
          invSnap.toObject(RoleInvite::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
        } else {
          null
        }
      }.getOrNull()
    } else {
      null
    }

    return AccountRegistrationStatus.NeedsRegistration(
      gmailDisplayName = existingProfile?.displayName?.takeIf { it.isNotBlank() }
        ?: invite?.displayName?.takeIf { it.isNotBlank() }
        ?: displayName,
      gmailEmail = email.ifBlank { existingProfile?.email.orEmpty() },
      prefillUsername = existingProfile?.username?.takeIf { it.isNotBlank() }
        ?: invite?.username?.takeIf { it.isNotBlank() }
        ?: "",
      prefillStaffId = existingProfile?.staffId?.takeIf { it.isNotBlank() }
        ?: invite?.staffId?.takeIf { it.isNotBlank() }
        ?: "",
      prefillPhone1 = existingProfile?.phoneNumber.orEmpty(),
      prefillPhone2 = existingProfile?.secondaryPhoneNumber.orEmpty(),
      existingRole = existingProfile?.roleEnum ?: invite?.roleEnum
    )
  }

  suspend fun verifyOnlineUsernamePassword(
    user: FirebaseUser,
    enteredUsername: String,
    enteredPassword: String
  ): OnlineCredentialVerifyResult {
    val cleanUserKey = normalizeUsernameKey(enteredUsername)
    val status = checkRegistrationStatus(user)
    if (status is AccountRegistrationStatus.NeedsRegistration) {
      return OnlineCredentialVerifyResult.AccountDoesNotExist(
        status.copy(
          prefillUsername = status.prefillUsername.ifBlank { cleanUserKey }
        )
      )
    }
    val registered = status as AccountRegistrationStatus.Registered
    val credSnap = depotDoc().collection("userCredentials").document(user.uid).get().await()
    if (!credSnap.exists()) {
      return OnlineCredentialVerifyResult.InvalidCredentials(
        "No online password configured for this account. Please login with Gmail."
      )
    }
    val storedUsername = normalizeUsernameKey(
      credSnap.getString("username").orEmpty().ifBlank { registered.profile.username }
    )
    val salt = credSnap.getString("passwordSalt").orEmpty()
    val hash = credSnap.getString("passwordHash").orEmpty()

    if (storedUsername != cleanUserKey) {
      return OnlineCredentialVerifyResult.InvalidCredentials(
        "Username does not match the registered account for ${registered.profile.email}."
      )
    }
    if (!PasswordHasher.verifyPassword(enteredPassword, salt, hash)) {
      return OnlineCredentialVerifyResult.InvalidCredentials(
        "Incorrect password. Please try again."
      )
    }
    return OnlineCredentialVerifyResult.Verified(registered.profile)
  }

  suspend fun checkUsernameAvailable(rawUsername: String, currentUid: String): UsernameAvailability {
    val formatError = validateUsernameFormat(rawUsername)
    if (formatError != null) {
      return UsernameAvailability.Unavailable(formatError)
    }
    val key = normalizeUsernameKey(rawUsername)
    return try {
      val snap = depotDoc().collection("usernames").document(key).get().await()
      if (!snap.exists() || snap.getString("uid") == currentUid) {
        UsernameAvailability.Available
      } else {
        UsernameAvailability.Unavailable("Username '$key' is already taken. Choose a different username.")
      }
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      UsernameAvailability.Unavailable(e.localizedMessage ?: "Unable to verify username availability.")
    }
  }

  suspend fun fetchStaffForRegistration(): List<StaffMember> {
    return try {
      val snapshot = depotDoc().collection("staff").get().await()
      val list = snapshot.toObjects(
        StaffMember::class.java,
        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
      ).sortedBy { it.serialNo }
      if (list.isNotEmpty()) list else DefaultPpioRoster.initialStaff(depotId)
    } catch (e: CancellationException) {
      throw e
    } catch (_: Exception) {
      DefaultPpioRoster.initialStaff(depotId)
    }
  }

  suspend fun completeRegistration(
    user: FirebaseUser,
    fullName: String,
    username: String,
    password: String,
    staffId: String,
    phoneNumber1: String,
    phoneNumber2: String
  ): DepotUserProfile {
    val uid = user.uid
    val cleanName = fullName.trim().take(100)
    require(cleanName.isNotBlank()) { "Full name is required." }

    val usernameError = validateUsernameFormat(username)
    require(usernameError == null) { usernameError ?: "Invalid username." }
    val usernameKey = normalizeUsernameKey(username)

    require(password.length >= 6) { "Password must be at least 6 characters." }

    val phone1Error = validatePhoneNumber(phoneNumber1, required = true)
    require(phone1Error == null) { phone1Error ?: "Phone number 1 is required." }
    val phone2Error = validatePhoneNumber(phoneNumber2, required = false)
    require(phone2Error == null) { phone2Error ?: "Invalid Phone number 2." }

    val cleanPhone1 = phoneNumber1.trim().take(20)
    val cleanPhone2 = phoneNumber2.trim().take(20)
    val cleanStaffId = staffId.trim().take(128)
    val cleanEmail = extractGmailEmail(user)
    require(cleanEmail.isNotBlank()) { "Verified Gmail address is required." }

    // 1. Atomically reserve username in global online registry (rejects duplicates)
    val usernameRef = depotDoc().collection("usernames").document(usernameKey)
    val existingUsernameSnap = usernameRef.get().await()
    if (existingUsernameSnap.exists()) {
      val ownerUid = existingUsernameSnap.getString("uid").orEmpty()
      if (ownerUid != uid) {
        throw IllegalStateException("Username '$usernameKey' already exists. Please choose another username.")
      }
    } else {
      try {
        usernameRef.set(
          mapOf(
            "username" to usernameKey,
            "uid" to uid,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp()
          )
        ).await()
      } catch (e: FirebaseFirestoreException) {
        if (e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
          throw IllegalStateException("Username '$usernameKey' already exists. Please choose another username.")
        }
        throw e
      }
    }

    // 2. Resolve role (Bootstrap Admin -> ADMIN, Admin Invite -> Invite role, else existing or TECHNICIAN)
    val isBootstrapAdmin = DepotRoleManager.isBootstrapAdminEmail(cleanEmail, cleanName)
    val roleDocRef = depotDoc().collection("userRoles").document(uid)
    val existingRoleSnap = runCatching { roleDocRef.get().await() }.getOrNull()
    val existingProfile = if (existingRoleSnap?.exists() == true) {
      existingRoleSnap.toObject(DepotUserProfile::class.java)
    } else {
      null
    }
    val matchingInvite = if (EMAIL_KEY_REGEX.matches(cleanEmail)) {
      runCatching {
        val invSnap = depotDoc().collection("roleInvites").document(cleanEmail).get().await()
        if (invSnap.exists()) invSnap.toObject(RoleInvite::class.java) else null
      }.getOrNull()
    } else {
      null
    }

    val effectiveRole = when {
      isBootstrapAdmin -> UserRole.ADMIN
      matchingInvite != null -> matchingInvite.roleEnum
      existingProfile != null -> existingProfile.roleEnum
      else -> UserRole.TECHNICIAN
    }

    // Release old username claim if user previously held a different username
    val previousUsername = normalizeUsernameKey(existingProfile?.username.orEmpty())
    if (previousUsername.isNotBlank() && previousUsername != usernameKey) {
      runCatching {
        depotDoc().collection("usernames").document(previousUsername).delete().await()
      }
    }

    // 3. Save userRoles/{uid} profile online
    if (existingRoleSnap?.exists() == true) {
      val updateFields = mutableMapOf<String, Any>(
        "username" to usernameKey,
        "email" to cleanEmail,
        "displayName" to cleanName,
        "staffId" to cleanStaffId,
        "phoneNumber" to cleanPhone1,
        "secondaryPhoneNumber" to cleanPhone2,
        "registered" to true,
        "updatedAt" to FieldValue.serverTimestamp()
      )
      if (effectiveRole != existingProfile?.roleEnum) {
        updateFields["role"] = effectiveRole.name
      }
      roleDocRef.update(updateFields).await()
    } else {
      roleDocRef.set(
        mapOf(
          "uid" to uid,
          "username" to usernameKey,
          "email" to cleanEmail,
          "displayName" to cleanName,
          "role" to effectiveRole.name,
          "staffId" to cleanStaffId,
          "phoneNumber" to cleanPhone1,
          "secondaryPhoneNumber" to cleanPhone2,
          "registered" to true,
          "updatedByUid" to uid,
          "createdAt" to FieldValue.serverTimestamp(),
          "updatedAt" to FieldValue.serverTimestamp()
        )
      ).await()
    }

    // 4. Save PBKDF2-salted password credential in owner-only userCredentials/{uid}
    val (salt, hash) = PasswordHasher.hashPassword(password)
    val credRef = depotDoc().collection("userCredentials").document(uid)
    val credExists = runCatching { credRef.get().await().exists() }.getOrDefault(false)
    if (credExists) {
      credRef.update(
        mapOf(
          "username" to usernameKey,
          "email" to cleanEmail,
          "passwordSalt" to salt,
          "passwordHash" to hash,
          "updatedAt" to FieldValue.serverTimestamp()
        )
      ).await()
    } else {
      credRef.set(
        mapOf(
          "uid" to uid,
          "username" to usernameKey,
          "email" to cleanEmail,
          "passwordSalt" to salt,
          "passwordHash" to hash,
          "createdAt" to FieldValue.serverTimestamp(),
          "updatedAt" to FieldValue.serverTimestamp()
        )
      ).await()
    }

    // 5. Best-effort sync of phone numbers to the user's linked StaffMember record
    if (cleanStaffId.isNotBlank()) {
      runCatching {
        val staffRef = depotDoc().collection("staff").document(cleanStaffId)
        val staffSnap = staffRef.get().await()
        if (staffSnap.exists()) {
          staffRef.update(
            mapOf(
              "phoneNumber" to cleanPhone1,
              "secondaryPhoneNumber" to cleanPhone2,
              "updatedAt" to FieldValue.serverTimestamp()
            )
          ).await()
        }
      }
    }

    return DepotUserProfile(
      id = uid,
      uid = uid,
      username = usernameKey,
      email = cleanEmail,
      displayName = cleanName,
      role = effectiveRole.name,
      staffId = cleanStaffId,
      phoneNumber = cleanPhone1,
      secondaryPhoneNumber = cleanPhone2,
      registered = true,
      updatedByUid = uid,
      hasPassword = true
    )
  }

  companion object {
    private val USERNAME_REGEX = Regex("^[a-z0-9._-]+$")
    private val EMAIL_KEY_REGEX = Regex("^[a-z0-9._%+\\-]+@[a-z0-9.\\-]+\\.[a-z]{2,}$")
  }
}
