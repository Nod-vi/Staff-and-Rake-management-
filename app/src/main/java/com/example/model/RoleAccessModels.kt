package com.example.model

enum class DepotMenuItem(
  val label: String,
  val subtitle: String
) {
  DASHBOARD("Dashboard", "Depot summary, live shifts, and operational reports"),
  SCHEDULE("Shifts", "Daily & weekly shift roster assignments and approvals"),
  DUTIES("Duties", "Tomorrow's duties sheet, exports, and auto-fill"),
  LEAVE("Leave & Rest", "Leave applications, approvals, balances, and weekly rest"),
  PERMISSIONS("Permissions", "Duty relief permissions and control contacts"),
  RAKES("Rake Manager", "EMU, MEMU, Spare Unit, and Vande Bharat formations"),
  STAFF("Staff Directory", "Depot staff roster, contacts, and training due dates"),
  USERS("Users", "User accounts, invites, and role assignments"),
  AI("AI Assistant", "Depot rules and roster intelligence assistant");

  fun levelHint(level: MenuAccessLevel): String = when (this) {
    DASHBOARD -> when (level) {
      MenuAccessLevel.NONE -> "Hidden from navigation"
      MenuAccessLevel.READ_ONLY -> "View overview metrics and schedules"
      MenuAccessLevel.EDIT_WITH_APPROVAL -> "View overview, weekly grid & request actions"
      MenuAccessLevel.FULL_CONTROL -> "Full command dashboard & supervisor analytics"
    }
    SCHEDULE -> when (level) {
      MenuAccessLevel.NONE -> "Hidden from navigation"
      MenuAccessLevel.READ_ONLY -> "View shift board & weekly roster only"
      MenuAccessLevel.EDIT_WITH_APPROVAL -> "Draft shift assignments & submit for approval"
      MenuAccessLevel.FULL_CONTROL -> "Direct edit shifts, overrides & approve drafts"
    }
    DUTIES -> when (level) {
      MenuAccessLevel.NONE -> "Hidden from navigation"
      MenuAccessLevel.READ_ONLY -> "View Tomorrow's Duties sheet only"
      MenuAccessLevel.EDIT_WITH_APPROVAL -> "View & export WhatsApp / PDF duty sheets"
      MenuAccessLevel.FULL_CONTROL -> "Full export controls & auto-fill range"
    }
    LEAVE -> when (level) {
      MenuAccessLevel.NONE -> "Hidden from navigation"
      MenuAccessLevel.READ_ONLY -> "View leave records & balances only"
      MenuAccessLevel.EDIT_WITH_APPROVAL -> "Apply for leave & submit balance requests for approval"
      MenuAccessLevel.FULL_CONTROL -> "Approve/edit/delete leaves & manage weekly rest"
    }
    PERMISSIONS -> when (level) {
      MenuAccessLevel.NONE -> "Hidden from navigation"
      MenuAccessLevel.READ_ONLY -> "View duty relief permissions & contacts only"
      MenuAccessLevel.EDIT_WITH_APPROVAL -> "Grant relief permissions (no deletion)"
      MenuAccessLevel.FULL_CONTROL -> "Grant, view, and remove duty relief permissions"
    }
    RAKES -> when (level) {
      MenuAccessLevel.NONE -> "Hidden from navigation"
      MenuAccessLevel.READ_ONLY -> "View & copy rake formations only"
      MenuAccessLevel.EDIT_WITH_APPROVAL -> "Create, edit & reverse rake formations (no delete)"
      MenuAccessLevel.FULL_CONTROL -> "Create, edit, reverse & delete rake formations"
    }
    STAFF -> when (level) {
      MenuAccessLevel.NONE -> "Hidden from navigation"
      MenuAccessLevel.READ_ONLY -> "View staff directory & contact numbers only"
      MenuAccessLevel.EDIT_WITH_APPROVAL -> "View staff & update Training Due dates"
      MenuAccessLevel.FULL_CONTROL -> "Add, edit, delete staff profiles & training dates"
    }
    USERS -> when (level) {
      MenuAccessLevel.NONE -> "Hidden from navigation"
      MenuAccessLevel.READ_ONLY -> "View user directory only"
      MenuAccessLevel.EDIT_WITH_APPROVAL -> "View user directory & invites (Admin required to change roles)"
      MenuAccessLevel.FULL_CONTROL -> "Manage non-Admin user accounts & role assignments"
    }
    AI -> when (level) {
      MenuAccessLevel.NONE -> "Hidden from navigation"
      MenuAccessLevel.READ_ONLY -> "Ask depot roster & railway policy questions"
      MenuAccessLevel.EDIT_WITH_APPROVAL -> "Ask roster analysis & shift recommendation questions"
      MenuAccessLevel.FULL_CONTROL -> "Full AI assistant access with depot context"
    }
  }

  companion object {
    fun fromRaw(value: String?): DepotMenuItem? =
      entries.firstOrNull { it.name.equals(value?.trim(), ignoreCase = true) }
  }
}

enum class MenuAccessLevel(
  val label: String,
  val shortLabel: String,
  val description: String
) {
  NONE("No Access", "Hidden", "Menu item is hidden and inaccessible"),
  READ_ONLY("Read Only", "Read Only", "View records only; editing is disabled"),
  EDIT_WITH_APPROVAL("Editable with Approval", "With Approval", "Submit drafts or limited edits requiring approval"),
  FULL_CONTROL("Full Control", "Full Control", "Direct create, edit, approve, and delete access");

  val isAccessible: Boolean get() = this != NONE
  val isEditableWithApprovalOrAbove: Boolean get() = this == EDIT_WITH_APPROVAL || this == FULL_CONTROL
  val isFullControl: Boolean get() = this == FULL_CONTROL

  companion object {
    val activeLevels: List<MenuAccessLevel> = listOf(READ_ONLY, EDIT_WITH_APPROVAL, FULL_CONTROL)

    fun fromRaw(value: String?): MenuAccessLevel? =
      entries.firstOrNull { it.name.equals(value?.trim(), ignoreCase = true) }
  }
}

data class ResolvedRoleAccess(
  val role: UserRole,
  val levels: Map<DepotMenuItem, MenuAccessLevel>
) {
  fun accessFor(item: DepotMenuItem): MenuAccessLevel {
    if (role == UserRole.ADMIN) return MenuAccessLevel.FULL_CONTROL
    return levels[item] ?: RoleAccessPolicy.defaultAccess(role, item)
  }

  fun hasAccess(item: DepotMenuItem): Boolean = accessFor(item).isAccessible

  val isAdmin: Boolean get() = role == UserRole.ADMIN
  val canAccessRolesManager: Boolean get() = role == UserRole.ADMIN

  val dashboardAccess: MenuAccessLevel get() = accessFor(DepotMenuItem.DASHBOARD)
  val scheduleAccess: MenuAccessLevel get() = accessFor(DepotMenuItem.SCHEDULE)
  val dutiesAccess: MenuAccessLevel get() = accessFor(DepotMenuItem.DUTIES)
  val leaveAccess: MenuAccessLevel get() = accessFor(DepotMenuItem.LEAVE)
  val permissionsAccess: MenuAccessLevel get() = accessFor(DepotMenuItem.PERMISSIONS)
  val rakesAccess: MenuAccessLevel get() = accessFor(DepotMenuItem.RAKES)
  val staffAccess: MenuAccessLevel get() = accessFor(DepotMenuItem.STAFF)
  val usersAccess: MenuAccessLevel get() = accessFor(DepotMenuItem.USERS)
  val aiAccess: MenuAccessLevel get() = accessFor(DepotMenuItem.AI)

  // Dashboard
  val canViewDashboard: Boolean get() = dashboardAccess.isAccessible
  val hasFullDashboard: Boolean get() = dashboardAccess.isFullControl

  // Shifts / Schedule
  val canViewShifts: Boolean get() = scheduleAccess.isAccessible
  val canDirectEditDuties: Boolean get() = scheduleAccess.isFullControl
  val canApproveDuties: Boolean get() = scheduleAccess.isFullControl
  val canDraftDuties: Boolean get() = scheduleAccess == MenuAccessLevel.EDIT_WITH_APPROVAL

  // Tomorrow's Duties
  val canViewDuties: Boolean get() = dutiesAccess.isAccessible
  val canExportDuties: Boolean get() = dutiesAccess.isEditableWithApprovalOrAbove
  val hasFullDutiesControl: Boolean get() = dutiesAccess.isFullControl

  // Leave & Weekly Rest
  val canViewLeave: Boolean get() = leaveAccess.isAccessible
  val isReadOnlyLeave: Boolean get() = leaveAccess == MenuAccessLevel.READ_ONLY
  val canApplyLeaveOnly: Boolean get() = leaveAccess == MenuAccessLevel.EDIT_WITH_APPROVAL
  val canApproveLeaves: Boolean get() = leaveAccess.isFullControl
  val canManageLeaves: Boolean get() = leaveAccess.isFullControl
  val canManageRestDays: Boolean get() = leaveAccess.isFullControl

  // Duty Relief Permissions
  val canViewPermissions: Boolean get() = permissionsAccess.isAccessible
  val canGrantPermissions: Boolean get() = permissionsAccess.isEditableWithApprovalOrAbove
  val canDeletePermissions: Boolean get() = permissionsAccess.isFullControl

  // Rake Manager
  val canViewRakes: Boolean get() = rakesAccess.isAccessible
  val canEditRakes: Boolean get() = rakesAccess.isEditableWithApprovalOrAbove
  val canDeleteRakes: Boolean get() = rakesAccess.isFullControl

  // Staff Directory
  val canViewStaff: Boolean get() = staffAccess.isAccessible
  val canManageTrainingDue: Boolean get() = staffAccess.isEditableWithApprovalOrAbove
  val canManageStaff: Boolean get() = staffAccess.isFullControl

  // Users
  val canViewUsers: Boolean get() = usersAccess.isAccessible
  val canManageUserRoles: Boolean get() = usersAccess.isFullControl

  // AI
  val canUseAi: Boolean get() = aiAccess.isAccessible

  // Important contacts visibility (Permissions readers or full dashboard managers)
  val canViewImportantContacts: Boolean get() = canViewPermissions || hasFullDashboard
}

object RoleAccessPolicy {

  fun defaultAccess(role: UserRole, menu: DepotMenuItem): MenuAccessLevel {
    if (role == UserRole.ADMIN) return MenuAccessLevel.FULL_CONTROL
    return when (role) {
      UserRole.ADMIN -> MenuAccessLevel.FULL_CONTROL
      UserRole.SUPERVISOR, UserRole.SSE -> when (menu) {
        DepotMenuItem.DASHBOARD -> MenuAccessLevel.FULL_CONTROL
        DepotMenuItem.SCHEDULE -> MenuAccessLevel.FULL_CONTROL
        DepotMenuItem.DUTIES -> MenuAccessLevel.FULL_CONTROL
        DepotMenuItem.LEAVE -> MenuAccessLevel.FULL_CONTROL
        DepotMenuItem.PERMISSIONS -> MenuAccessLevel.FULL_CONTROL
        DepotMenuItem.RAKES -> MenuAccessLevel.FULL_CONTROL
        DepotMenuItem.STAFF -> MenuAccessLevel.EDIT_WITH_APPROVAL
        DepotMenuItem.USERS -> MenuAccessLevel.NONE
        DepotMenuItem.AI -> MenuAccessLevel.FULL_CONTROL
      }
      UserRole.JE -> when (menu) {
        DepotMenuItem.DASHBOARD -> MenuAccessLevel.READ_ONLY
        DepotMenuItem.SCHEDULE -> MenuAccessLevel.EDIT_WITH_APPROVAL
        DepotMenuItem.DUTIES -> MenuAccessLevel.EDIT_WITH_APPROVAL
        DepotMenuItem.LEAVE -> MenuAccessLevel.EDIT_WITH_APPROVAL
        DepotMenuItem.PERMISSIONS -> MenuAccessLevel.EDIT_WITH_APPROVAL
        DepotMenuItem.RAKES -> MenuAccessLevel.FULL_CONTROL
        DepotMenuItem.STAFF -> MenuAccessLevel.READ_ONLY
        DepotMenuItem.USERS -> MenuAccessLevel.NONE
        DepotMenuItem.AI -> MenuAccessLevel.READ_ONLY
      }
      UserRole.SHIFT_INCHARGE -> when (menu) {
        DepotMenuItem.DASHBOARD -> MenuAccessLevel.FULL_CONTROL
        DepotMenuItem.SCHEDULE -> MenuAccessLevel.READ_ONLY
        DepotMenuItem.DUTIES -> MenuAccessLevel.EDIT_WITH_APPROVAL
        DepotMenuItem.LEAVE -> MenuAccessLevel.READ_ONLY
        DepotMenuItem.PERMISSIONS -> MenuAccessLevel.FULL_CONTROL
        DepotMenuItem.RAKES -> MenuAccessLevel.FULL_CONTROL
        DepotMenuItem.STAFF -> MenuAccessLevel.READ_ONLY
        DepotMenuItem.USERS -> MenuAccessLevel.NONE
        DepotMenuItem.AI -> MenuAccessLevel.READ_ONLY
      }
      UserRole.STOREMATE -> when (menu) {
        DepotMenuItem.DASHBOARD -> MenuAccessLevel.READ_ONLY
        DepotMenuItem.SCHEDULE -> MenuAccessLevel.EDIT_WITH_APPROVAL
        DepotMenuItem.DUTIES -> MenuAccessLevel.READ_ONLY
        DepotMenuItem.LEAVE -> MenuAccessLevel.EDIT_WITH_APPROVAL
        DepotMenuItem.PERMISSIONS -> MenuAccessLevel.NONE
        DepotMenuItem.RAKES -> MenuAccessLevel.READ_ONLY
        DepotMenuItem.STAFF -> MenuAccessLevel.READ_ONLY
        DepotMenuItem.USERS -> MenuAccessLevel.NONE
        DepotMenuItem.AI -> MenuAccessLevel.READ_ONLY
      }
      UserRole.TECHNICIAN -> when (menu) {
        DepotMenuItem.DASHBOARD -> MenuAccessLevel.READ_ONLY
        DepotMenuItem.SCHEDULE -> MenuAccessLevel.READ_ONLY
        DepotMenuItem.DUTIES -> MenuAccessLevel.READ_ONLY
        DepotMenuItem.LEAVE -> MenuAccessLevel.EDIT_WITH_APPROVAL
        DepotMenuItem.PERMISSIONS -> MenuAccessLevel.NONE
        DepotMenuItem.RAKES -> MenuAccessLevel.READ_ONLY
        DepotMenuItem.STAFF -> MenuAccessLevel.READ_ONLY
        DepotMenuItem.USERS -> MenuAccessLevel.NONE
        DepotMenuItem.AI -> MenuAccessLevel.READ_ONLY
      }
    }
  }

  fun defaultMatrixForRole(role: UserRole): Map<DepotMenuItem, MenuAccessLevel> =
    DepotMenuItem.entries.associateWith { defaultAccess(role, it) }

  fun resolve(
    role: UserRole,
    overrides: Map<String, Map<String, String>> = emptyMap()
  ): ResolvedRoleAccess {
    if (role == UserRole.ADMIN) {
      return ResolvedRoleAccess(
        role = UserRole.ADMIN,
        levels = DepotMenuItem.entries.associateWith { MenuAccessLevel.FULL_CONTROL }
      )
    }
    val roleOverrides = overrides[role.name].orEmpty()
    val resolved = DepotMenuItem.entries.associateWith { menu ->
      MenuAccessLevel.fromRaw(roleOverrides[menu.name]) ?: defaultAccess(role, menu)
    }
    return ResolvedRoleAccess(role = role, levels = resolved)
  }

  fun sanitizeRoleAccessMap(raw: Map<String, Map<String, String>>): Map<String, Map<String, String>> {
    val clean = linkedMapOf<String, Map<String, String>>()
    raw.forEach { (roleKey, menuMap) ->
      val role = UserRole.entries.firstOrNull { it.name.equals(roleKey.trim(), ignoreCase = true) }
        ?: return@forEach
      if (role == UserRole.ADMIN) return@forEach
      val cleanMenus = linkedMapOf<String, String>()
      menuMap.forEach { (menuKey, levelKey) ->
        val menu = DepotMenuItem.fromRaw(menuKey) ?: return@forEach
        val level = MenuAccessLevel.fromRaw(levelKey) ?: return@forEach
        cleanMenus[menu.name] = level.name
      }
      if (cleanMenus.isNotEmpty()) {
        clean[role.name] = cleanMenus
      }
    }
    return clean
  }

  fun withRoleOverride(
    current: Map<String, Map<String, String>>,
    role: UserRole,
    menuLevels: Map<DepotMenuItem, MenuAccessLevel>
  ): Map<String, Map<String, String>> {
    if (role == UserRole.ADMIN) return sanitizeRoleAccessMap(current)
    val next = sanitizeRoleAccessMap(current).toMutableMap()
    next[role.name] = DepotMenuItem.entries.associate { menu ->
      menu.name to (menuLevels[menu] ?: defaultAccess(role, menu)).name
    }
    return next
  }

  fun withoutRoleOverride(
    current: Map<String, Map<String, String>>,
    role: UserRole
  ): Map<String, Map<String, String>> {
    val next = sanitizeRoleAccessMap(current).toMutableMap()
    next.remove(role.name)
    return next
  }
}
