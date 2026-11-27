package com.example.auth

import android.content.Context
import com.example.model.DepotUserProfile
import com.example.model.UserRole
import com.google.firebase.auth.FirebaseUser

data class DepotRoleAccount(
  val id: String,
  val displayName: String,
  val role: UserRole,
  val subtitle: String,
  val emailHint: String? = null
)

data class DepotSessionUser(
  val id: String,
  val displayName: String,
  val email: String?,
  val role: UserRole,
  val subtitle: String,
  val isGoogleAuthenticated: Boolean,
  val isOfflineTestMode: Boolean = false
)

object DepotRoleManager {
  const val SHARED_DEPOT_ID = "ppio_main"
  const val ADMIN_EMAIL = "chinthawarvinod@gmail.com"
  const val ADMIN_DISPLAY_NAME = "Vinod Chinthawar"
  const val ADMIN_ACCOUNT_ID = "admin_vinod"
  const val ADMIN_USERNAME = "admin"
  const val DEFAULT_ADMIN_PASSWORD = "admin"

  const val OFFLINE_TEST_ADMIN_UID = "offline_test_admin"
  const val OFFLINE_TEST_ADMIN_DISPLAY_NAME = "Offline Test Admin"
  const val OFFLINE_TEST_ADMIN_USERNAME = "admin"
  const val OFFLINE_TEST_ADMIN_PASSWORD = "admin"

  private const val PREFS_NAME = "ppio_role_prefs"
  private const val KEY_LOCAL_ACCOUNT_ID = "local_account_id"
  private const val KEY_ACTIVE_CREDENTIAL_UID = "active_credential_uid"

  val roleAccounts: List<DepotRoleAccount> = listOf(
    DepotRoleAccount(
      id = ADMIN_ACCOUNT_ID,
      displayName = ADMIN_DISPLAY_NAME,
      role = UserRole.ADMIN,
      subtitle = "Admin · Role Assignment, Staff & Full Depot Control",
      emailHint = ADMIN_EMAIL
    ),
    DepotRoleAccount(
      id = "sup_1",
      displayName = "VENKATA KRISHNA",
      role = UserRole.SUPERVISOR,
      subtitle = "Supervisor · SSE · Approve Shifts, Edit Duties & Leaves"
    ),
    DepotRoleAccount(
      id = "sup_2",
      displayName = "PRAKASH",
      role = UserRole.SUPERVISOR,
      subtitle = "Supervisor · SSE · Approve Shifts, Edit Duties & Leaves"
    ),
    DepotRoleAccount(
      id = "sup_3",
      displayName = "Y. SAI KRISHNA",
      role = UserRole.SUPERVISOR,
      subtitle = "Supervisor · JE · Shift & Leave Supervision"
    ),
    DepotRoleAccount(
      id = "sup_5",
      displayName = "N. CHAITANYA PRASAD",
      role = UserRole.SUPERVISOR,
      subtitle = "Supervisor · JE · Shift & Leave Supervision"
    ),
    DepotRoleAccount(
      id = "sup_6",
      displayName = "M. PRAVEEN KUMAR",
      role = UserRole.SUPERVISOR,
      subtitle = "Supervisor · JE · Shift & Leave Supervision"
    ),
    DepotRoleAccount(
      id = "sse_1",
      displayName = "SSE In-Charge",
      role = UserRole.SSE,
      subtitle = "SSE · Senior Section Engineer · Shift Approvals, Roster & Leaves"
    ),
    DepotRoleAccount(
      id = "je_1",
      displayName = "Junior Engineer",
      role = UserRole.JE,
      subtitle = "JE · Draft Shifts for Approval, Permissions & Rake Manager"
    ),
    DepotRoleAccount(
      id = "shift_incharge_1",
      displayName = "Shift Incharge",
      role = UserRole.SHIFT_INCHARGE,
      subtitle = "ShiftIncharge · Live Duty Dashboard & Permission Manager Only"
    ),
    DepotRoleAccount(
      id = "storemate_1",
      displayName = "Depot Storemate",
      role = UserRole.STOREMATE,
      subtitle = "Storemate · Draft Duties for Supervisor Approval & Apply Leave"
    ),
    DepotRoleAccount(
      id = "tech_1",
      displayName = "P. RAM KUMAR",
      role = UserRole.TECHNICIAN,
      subtitle = "Technician · Read-Only Roster & Apply Leave to SSE"
    )
  )

  fun isOfflineTestAdminCredentials(username: String, password: String): Boolean =
    username.trim().equals(OFFLINE_TEST_ADMIN_USERNAME, ignoreCase = true) &&
      password == OFFLINE_TEST_ADMIN_PASSWORD

  fun resolveOfflineTestAdminSession(): DepotSessionUser = DepotSessionUser(
    id = OFFLINE_TEST_ADMIN_UID,
    displayName = OFFLINE_TEST_ADMIN_DISPLAY_NAME,
    email = null,
    role = UserRole.ADMIN,
    subtitle = "Offline Test Mode · Local Device Sandbox (No Gmail / No Server Sync)",
    isGoogleAuthenticated = false,
    isOfflineTestMode = true
  )

  fun isBootstrapAdminEmail(email: String?, displayName: String?): Boolean {
    val normalizedEmail = email?.trim().orEmpty()
    if (normalizedEmail.isNotEmpty()) {
      return normalizedEmail.equals(ADMIN_EMAIL, ignoreCase = true)
    }
    val normalizedName = displayName?.trim().orEmpty()
    return normalizedName.equals(ADMIN_DISPLAY_NAME, ignoreCase = true)
  }

  fun resolveGoogleSession(
    user: FirebaseUser,
    persistedProfile: DepotUserProfile? = null
  ): DepotSessionUser {
    val email = user.email?.trim()?.takeIf { it.isNotBlank() }
      ?: user.providerData.firstNotNullOfOrNull { it.email?.trim()?.takeIf { e -> e.isNotBlank() } }
      ?: persistedProfile?.email?.trim().orEmpty()
    val name = user.displayName?.trim()?.takeIf { it.isNotBlank() }
      ?: user.providerData.firstNotNullOfOrNull { it.displayName?.trim()?.takeIf { n -> n.isNotBlank() } }
      .orEmpty()
    val isVinodAdmin = email.equals(ADMIN_EMAIL, ignoreCase = true) ||
      persistedProfile?.roleEnum == UserRole.ADMIN

    val effectiveRole = when {
      email.equals(ADMIN_EMAIL, ignoreCase = true) -> UserRole.ADMIN
      persistedProfile != null -> persistedProfile.roleEnum
      else -> UserRole.TECHNICIAN
    }

    val effectiveName = when {
      email.equals(ADMIN_EMAIL, ignoreCase = true) -> name.ifBlank { ADMIN_DISPLAY_NAME }
      !persistedProfile?.displayName.isNullOrBlank() -> persistedProfile!!.displayName
      name.isNotBlank() -> name
      email.isNotBlank() -> email
      else -> "Depot Member"
    }

    val subtitle = when (effectiveRole) {
      UserRole.ADMIN -> "Admin · Roles Manager, User Accounts & Full Depot Control"
      UserRole.SSE -> "SSE · Senior Section Engineer · Shift Approvals, Roster & Leaves"
      UserRole.JE -> "JE · Junior Engineer · Draft Duties, Permissions & Rakes"
      UserRole.SUPERVISOR -> "Supervisor · Shift Approvals, Direct Roster & Leave Control"
      UserRole.SHIFT_INCHARGE -> "ShiftIncharge · Live Duty Dashboard & Permission Manager"
      UserRole.STOREMATE -> "Storemate · Draft Duties for Approval & Forward Leave"
      UserRole.TECHNICIAN -> "Technician · Read Access & Forward Leave to SSE"
    }

    return DepotSessionUser(
      id = user.uid,
      displayName = effectiveName,
      email = email.ifBlank { if (isVinodAdmin) ADMIN_EMAIL else null },
      role = effectiveRole,
      subtitle = subtitle,
      isGoogleAuthenticated = true
    )
  }

  fun resolveLocalSession(accountId: String?): DepotSessionUser {
    val account = roleAccounts.firstOrNull { it.id == accountId } ?: roleAccounts.first()
    return DepotSessionUser(
      id = account.id,
      displayName = account.displayName,
      email = account.emailHint,
      role = account.role,
      subtitle = account.subtitle,
      isGoogleAuthenticated = false
    )
  }

  fun loadSavedLocalAccountId(context: Context): String {
    return context.applicationContext
      .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
      .getString(KEY_LOCAL_ACCOUNT_ID, ADMIN_ACCOUNT_ID)
      ?: ADMIN_ACCOUNT_ID
  }

  fun saveLocalAccountId(context: Context, accountId: String) {
    context.applicationContext
      .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
      .edit()
      .putString(KEY_LOCAL_ACCOUNT_ID, accountId)
      .apply()
  }

  fun loadActiveCredentialUid(context: Context): String? {
    return context.applicationContext
      .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
      .getString(KEY_ACTIVE_CREDENTIAL_UID, null)
      ?.takeIf { it.isNotBlank() }
  }

  fun saveActiveCredentialUid(context: Context, uid: String?) {
    context.applicationContext
      .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
      .edit()
      .apply {
        if (uid.isNullOrBlank()) remove(KEY_ACTIVE_CREDENTIAL_UID)
        else putString(KEY_ACTIVE_CREDENTIAL_UID, uid)
      }
      .apply()
  }

  fun resolveSessionFromProfile(profile: DepotUserProfile): DepotSessionUser {
    val role = profile.roleEnum
    val subtitle = when (role) {
      UserRole.ADMIN -> "Admin · Roles Manager, User Accounts & Full Depot Control"
      UserRole.SSE -> "SSE · Senior Section Engineer · Shift Approvals, Roster & Leaves"
      UserRole.JE -> "JE · Junior Engineer · Draft Duties, Permissions & Rakes"
      UserRole.SUPERVISOR -> "Supervisor · Shift Approvals, Direct Roster & Leave Control"
      UserRole.SHIFT_INCHARGE -> "ShiftIncharge · Live Duty Dashboard & Permission Manager"
      UserRole.STOREMATE -> "Storemate · Draft Duties for Approval & Forward Leave"
      UserRole.TECHNICIAN -> "Technician · Read Access & Forward Leave to SSE"
    }
    return DepotSessionUser(
      id = profile.uid,
      displayName = profile.displayName.ifBlank { profile.username.ifBlank { "Depot User" } },
      email = profile.email.ifBlank { null },
      role = role,
      subtitle = subtitle,
      isGoogleAuthenticated = false
    )
  }
}
