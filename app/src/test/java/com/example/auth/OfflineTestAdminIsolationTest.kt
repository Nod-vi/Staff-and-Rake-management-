package com.example.auth

import com.example.data.FirestoreSyncStatus
import com.example.model.UserRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OfflineTestAdminIsolationTest {

  @Test
  fun offlineTestCredentials_acceptOnlyAdminAndAdmin() {
    assertTrue(DepotRoleManager.isOfflineTestAdminCredentials("admin", "admin"))
    assertTrue(DepotRoleManager.isOfflineTestAdminCredentials("  Admin  ", "admin"))
    assertFalse(DepotRoleManager.isOfflineTestAdminCredentials("admin", "Admin"))
    assertFalse(DepotRoleManager.isOfflineTestAdminCredentials("admin", "wrong"))
    assertFalse(DepotRoleManager.isOfflineTestAdminCredentials("vinod", "admin"))
    assertFalse(DepotRoleManager.isOfflineTestAdminCredentials("", ""))
  }

  @Test
  fun offlineTestSession_hasAdminRoleNoGmailAndNoServerSync() {
    val session = DepotRoleManager.resolveOfflineTestAdminSession()
    assertEquals(DepotRoleManager.OFFLINE_TEST_ADMIN_UID, session.id)
    assertEquals(UserRole.ADMIN, session.role)
    assertNull(session.email)
    assertFalse(session.isGoogleAuthenticated)
    assertTrue(session.isOfflineTestMode)
    assertFalse(DepotRoleManager.isBootstrapAdminEmail(session.email, session.displayName))
  }

  @Test
  fun syncStatus_offlineTestModeNeverReportsPendingServerWrites() {
    val status = FirestoreSyncStatus(
      isNetworkAvailable = true,
      isFromCache = false,
      hasPendingWrites = true,
      isOfflineTestMode = true
    )
    assertTrue(status.isOfflineOrCached)
    assertFalse(status.isSyncingPendingWrites)
  }
}
