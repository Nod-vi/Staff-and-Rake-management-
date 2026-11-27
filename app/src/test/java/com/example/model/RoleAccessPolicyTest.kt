package com.example.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RoleAccessPolicyTest {

  @Test
  fun sseRoleDefaultsMatchFullOperationalSupervision() {
    val access = RoleAccessPolicy.resolve(UserRole.SSE, emptyMap())
    assertEquals(UserRole.SSE, access.role)
    assertFalse(access.isAdmin)
    assertFalse(access.canAccessRolesManager)
    assertEquals(MenuAccessLevel.FULL_CONTROL, access.accessFor(DepotMenuItem.DASHBOARD))
    assertEquals(MenuAccessLevel.FULL_CONTROL, access.accessFor(DepotMenuItem.SCHEDULE))
    assertEquals(MenuAccessLevel.FULL_CONTROL, access.accessFor(DepotMenuItem.DUTIES))
    assertEquals(MenuAccessLevel.FULL_CONTROL, access.accessFor(DepotMenuItem.LEAVE))
    assertEquals(MenuAccessLevel.FULL_CONTROL, access.accessFor(DepotMenuItem.PERMISSIONS))
    assertEquals(MenuAccessLevel.FULL_CONTROL, access.accessFor(DepotMenuItem.RAKES))
    assertEquals(MenuAccessLevel.EDIT_WITH_APPROVAL, access.accessFor(DepotMenuItem.STAFF))
    assertEquals(MenuAccessLevel.NONE, access.accessFor(DepotMenuItem.USERS))
    assertEquals(MenuAccessLevel.FULL_CONTROL, access.accessFor(DepotMenuItem.AI))

    assertTrue(access.canDirectEditDuties)
    assertTrue(access.canApproveDuties)
    assertTrue(access.canApproveLeaves)
    assertTrue(access.canManageRestDays)
    assertTrue(access.canGrantPermissions)
    assertTrue(access.canDeletePermissions)
    assertTrue(access.canEditRakes)
    assertTrue(access.canDeleteRakes)
    assertTrue(access.canManageTrainingDue)
    assertFalse(access.canManageStaff)
    assertFalse(access.canViewUsers)
  }

  @Test
  fun jeRoleDefaultsUseDraftWithApprovalForScheduleAndLeave() {
    val access = RoleAccessPolicy.resolve(UserRole.JE, emptyMap())
    assertEquals(UserRole.JE, access.role)
    assertFalse(access.isAdmin)
    assertFalse(access.canAccessRolesManager)
    assertEquals(MenuAccessLevel.EDIT_WITH_APPROVAL, access.accessFor(DepotMenuItem.SCHEDULE))
    assertEquals(MenuAccessLevel.EDIT_WITH_APPROVAL, access.accessFor(DepotMenuItem.LEAVE))
    assertEquals(MenuAccessLevel.EDIT_WITH_APPROVAL, access.accessFor(DepotMenuItem.PERMISSIONS))
    assertEquals(MenuAccessLevel.FULL_CONTROL, access.accessFor(DepotMenuItem.RAKES))
    assertEquals(MenuAccessLevel.READ_ONLY, access.accessFor(DepotMenuItem.STAFF))
    assertEquals(MenuAccessLevel.NONE, access.accessFor(DepotMenuItem.USERS))

    assertTrue(access.canDraftDuties)
    assertFalse(access.canDirectEditDuties)
    assertFalse(access.canApproveDuties)
    assertTrue(access.canApplyLeaveOnly)
    assertFalse(access.canApproveLeaves)
    assertTrue(access.canGrantPermissions)
    assertFalse(access.canDeletePermissions)
    assertTrue(access.canEditRakes)
    assertTrue(access.canDeleteRakes)
  }

  @Test
  fun adminAlwaysRetainsFullControlEvenWhenOverridesAttemptToRestrictAdmin() {
    val tampered = mapOf(
      UserRole.ADMIN.name to mapOf(
        DepotMenuItem.SCHEDULE.name to MenuAccessLevel.NONE.name,
        DepotMenuItem.USERS.name to MenuAccessLevel.READ_ONLY.name
      )
    )
    val sanitized = RoleAccessPolicy.sanitizeRoleAccessMap(tampered)
    assertFalse(sanitized.containsKey(UserRole.ADMIN.name))

    val adminAccess = RoleAccessPolicy.resolve(UserRole.ADMIN, tampered)
    assertTrue(adminAccess.isAdmin)
    assertTrue(adminAccess.canAccessRolesManager)
    DepotMenuItem.entries.forEach { menu ->
      assertEquals(MenuAccessLevel.FULL_CONTROL, adminAccess.accessFor(menu))
    }
  }

  @Test
  fun customOverridesGrantAndRestrictMenuLevelsDynamically() {
    val jeCustom = RoleAccessPolicy.defaultMatrixForRole(UserRole.JE).toMutableMap().apply {
      this[DepotMenuItem.SCHEDULE] = MenuAccessLevel.FULL_CONTROL
      this[DepotMenuItem.RAKES] = MenuAccessLevel.EDIT_WITH_APPROVAL
      this[DepotMenuItem.AI] = MenuAccessLevel.NONE
      this[DepotMenuItem.USERS] = MenuAccessLevel.READ_ONLY
    }
    val overrides = RoleAccessPolicy.withRoleOverride(emptyMap(), UserRole.JE, jeCustom)
    val resolved = RoleAccessPolicy.resolve(UserRole.JE, overrides)

    assertTrue(resolved.canDirectEditDuties)
    assertTrue(resolved.canApproveDuties)
    assertFalse(resolved.canDraftDuties)
    assertTrue(resolved.canEditRakes)
    assertFalse(resolved.canDeleteRakes)
    assertFalse(resolved.canUseAi)
    assertTrue(resolved.canViewUsers)
    assertFalse(resolved.canManageUserRoles)

    // Resetting restores defaults
    val resetMap = RoleAccessPolicy.withoutRoleOverride(overrides, UserRole.JE)
    val afterReset = RoleAccessPolicy.resolve(UserRole.JE, resetMap)
    assertTrue(afterReset.canDraftDuties)
    assertFalse(afterReset.canDirectEditDuties)
    assertTrue(afterReset.canUseAi)
    assertFalse(afterReset.canViewUsers)
  }

  @Test
  fun sanitizeDropsUnknownRolesMenusAndLevels() {
    val dirty = mapOf(
      "BOGUS_ROLE" to mapOf("SCHEDULE" to "FULL_CONTROL"),
      "ADMIN" to mapOf("SCHEDULE" to "NONE"),
      "SSE" to mapOf(
        "SCHEDULE" to "READ_ONLY",
        "UNKNOWN_MENU" to "FULL_CONTROL",
        "RAKES" to "SUPER_POWER"
      )
    )
    val clean = RoleAccessPolicy.sanitizeRoleAccessMap(dirty)
    assertEquals(setOf("SSE"), clean.keys)
    assertEquals(mapOf("SCHEDULE" to "READ_ONLY"), clean["SSE"])
  }
}
