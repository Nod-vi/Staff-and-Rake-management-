package com.example

import android.app.Application
import android.content.Intent
import android.graphics.Color as AndroidColor
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.FactCheck
import androidx.compose.material.icons.automirrored.outlined.FactCheck
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.EventBusy
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.ManageAccounts
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Train
import androidx.compose.material.icons.outlined.AdminPanelSettings
import androidx.compose.material.icons.outlined.Assessment
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.EventBusy
import androidx.compose.material.icons.outlined.ManageAccounts
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Train
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.credentials.CredentialManager
import androidx.lifecycle.HasDefaultViewModelProviderFactory
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.MutableCreationExtras
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.auth.AccountRegistrationStatus
import com.example.auth.DepotRoleManager
import com.example.auth.DepotSessionUser
import com.example.auth.OnlineAccountService
import com.example.auth.OnlineCredentialVerifyResult
import com.example.auth.authStateFlow
import com.example.auth.configureAppCheck
import com.example.auth.signOutAndWait
import com.example.data.FirestoreProvider
import com.example.data.FirestoreRosterRepository
import com.example.data.FirestoreSyncStatus
import com.example.data.LocalRosterRepository
import com.example.data.NetworkConnectivityObserver
import com.example.data.RosterRepository
import com.example.data.SyncTimestampStore
import com.example.model.DepotUserProfile
import com.example.model.LeaveType
import com.example.model.UserRole
import com.example.notifications.ScheduledDutyReminderInfo
import com.example.notifications.TechnicianDutyNotifier
import com.example.ui.components.SyncStatusBanner
import com.example.ui.screens.AdminAppUpdatesScreen
import com.example.ui.screens.AiAssistantScreen
import com.example.ui.screens.AppUpdateStatusDialog
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.ChangePasswordDialog
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.LeaveAndRestScreen
import com.example.ui.screens.MandatoryUpdateGateScreen
import com.example.ui.screens.OptionalUpdateBanner
import com.example.ui.screens.PermissionManagerScreen
import com.example.ui.screens.RakeManagerScreen
import com.example.ui.screens.RegistrationScreen
import com.example.ui.screens.RolePermissionsDialog
import com.example.ui.screens.RolesManagerScreen
import com.example.ui.screens.ScheduleScreen
import com.example.ui.screens.StaffDirectoryScreen
import com.example.ui.screens.TomorrowsDutiesScreen
import com.example.ui.screens.UserDetailsDialog
import com.example.ui.screens.UsersScreen
import com.example.ui.screens.openVerifiedUpdateUrl
import com.example.ui.theme.DepotTeal
import com.example.ui.theme.DepotTealContainer
import com.example.ui.theme.DepotTealOnContainer
import com.example.ui.theme.GlassBackdrop
import com.example.ui.theme.GlassIconOrb
import com.example.ui.theme.GlassPillBadge
import com.example.ui.theme.GlassPopupSurface
import com.example.ui.theme.GlassSurfaceBright
import com.example.ui.theme.GlassSurfaceMid
import com.example.ui.theme.GlassSurfaceSoft
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.RailwayCobalt
import com.example.ui.theme.SafetyAmber
import com.example.ui.theme.SafetyAmberContainer
import com.example.ui.theme.SafetyAmberOnContainer
import com.example.ui.theme.SignalCrimson
import com.example.ui.theme.SignalCrimsonContainer
import com.example.ui.theme.SignalCrimsonOnContainer
import com.example.ui.theme.SignalEmerald
import com.example.ui.theme.SignalEmeraldContainer
import com.example.ui.theme.SignalEmeraldOnContainer
import com.example.ui.theme.liquidGlassSurface
import com.example.viewmodel.AiChatViewModel
import com.example.viewmodel.RosterViewModel
import com.example.viewmodel.UiState
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore

private enum class DepotTab(
  val label: String,
  val selectedIcon: ImageVector,
  val unselectedIcon: ImageVector,
  val testTag: String
) {
  SCHEDULE("Shifts", Icons.Filled.CalendarMonth, Icons.Outlined.CalendarMonth, "nav_shifts"),
  DUTIES("Duties", Icons.AutoMirrored.Filled.FactCheck, Icons.AutoMirrored.Outlined.FactCheck, "nav_duties"),
  LEAVE("Leave", Icons.Filled.EventBusy, Icons.Outlined.EventBusy, "nav_leave"),
  PERMISSIONS("Permit", Icons.Filled.Schedule, Icons.Outlined.Schedule, "nav_permissions"),
  STAFF("Staff", Icons.Filled.Badge, Icons.Outlined.Badge, "nav_staff"),
  RAKES("Rakes", Icons.Filled.Train, Icons.Outlined.Train, "nav_rakes"),
  USERS("Users", Icons.Filled.ManageAccounts, Icons.Outlined.ManageAccounts, "nav_users"),
  ROLES("Roles", Icons.Filled.AdminPanelSettings, Icons.Outlined.AdminPanelSettings, "nav_roles"),
  DASHBOARD("Report", Icons.Filled.Dashboard, Icons.Outlined.Dashboard, "nav_dashboard"),
  AI("AI", Icons.Filled.AutoAwesome, Icons.Outlined.AutoAwesome, "nav_assistant")
}

class MainActivity : ComponentActivity() {
  private var openTabRequest by mutableStateOf<String?>(null)
  private var openTabEventId by mutableIntStateOf(0)

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    // Mandatory Step 1: Configure Firebase App Check with launch intent before UI/ViewModels
    configureAppCheck(this, intent)
    handleNavigationIntent(intent)

    enableEdgeToEdge(
      statusBarStyle = SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT),
      navigationBarStyle = SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT)
    )
    setContent {
      MyApplicationTheme {
        GlassBackdrop {
          Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color.Transparent
          ) {
            AppNavigation(
              openTabRequest = openTabRequest,
              openTabEventId = openTabEventId
            )
          }
        }
      }
    }
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    setIntent(intent)
    handleNavigationIntent(intent)
  }

  private fun handleNavigationIntent(intent: Intent?) {
    val requestedTab = intent?.getStringExtra(TechnicianDutyNotifier.EXTRA_OPEN_TAB)
    if (!requestedTab.isNullOrBlank()) {
      openTabRequest = requestedTab
      openTabEventId += 1
      intent.removeExtra(TechnicianDutyNotifier.EXTRA_OPEN_TAB)
    }
  }
}

private class SessionViewModelStoreOwner(
  private val application: Application?
) : ViewModelStoreOwner, HasDefaultViewModelProviderFactory {
  override val viewModelStore: ViewModelStore = ViewModelStore()

  override val defaultViewModelProviderFactory: ViewModelProvider.Factory
    get() = application?.let { ViewModelProvider.AndroidViewModelFactory.getInstance(it) }
      ?: ViewModelProvider.NewInstanceFactory()

  override val defaultViewModelCreationExtras: CreationExtras
    get() = MutableCreationExtras().apply {
      if (application != null) {
        set(APPLICATION_KEY, application)
      }
    }
}

private sealed interface OnlineGateState {
  data object Checking : OnlineGateState
  data class NeedsRegistration(val info: AccountRegistrationStatus.NeedsRegistration) : OnlineGateState
  data class Registered(val profile: DepotUserProfile) : OnlineGateState
  data class Error(val message: String) : OnlineGateState
}

@Composable
fun AppNavigation(
  auth: FirebaseAuth = Firebase.auth,
  openTabRequest: String? = null,
  openTabEventId: Int = 0
) {
  val context = LocalContext.current
  val appContext = context.applicationContext
  val credentialManager = remember(context) { CredentialManager.create(context) }
  val localRepository = remember(context) { LocalRosterRepository.getInstance(context) }
  val firestoreDb = remember(appContext) {
    FirestoreProvider.getInstance(appContext)
  }
  val accountService = remember(firestoreDb) { OnlineAccountService(firestoreDb) }

  val currentUser by auth.authStateFlow().collectAsStateWithLifecycle(initialValue = auth.currentUser)

  var sessionEpoch by rememberSaveable { mutableIntStateOf(0) }
  var isOfflineTestSession by rememberSaveable { mutableStateOf(false) }
  var isSigningOut by remember { mutableStateOf(false) }
  var isCredentialCheckInFlight by remember { mutableStateOf(false) }
  var precomputedUid by remember { mutableStateOf<String?>(null) }
  var precomputedGateState by remember { mutableStateOf<OnlineGateState?>(null) }
  var gateRetrySignal by remember { mutableIntStateOf(0) }

  // Clear any stale legacy offline credential pointer so offline login is never restored
  LaunchedEffect(Unit) {
    DepotRoleManager.saveActiveCredentialUid(context, null)
  }

  if (isSigningOut) {
    LaunchedEffect(Unit) {
      TechnicianDutyNotifier.cancelAllScheduledReminders(appContext)
      precomputedUid = null
      precomputedGateState = null
      isOfflineTestSession = false
      if (auth.currentUser != null) {
        signOutAndWait(credentialManager)
      }
      isSigningOut = false
    }
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
      Column(horizontalAlignment = Alignment.CenterHorizontally) {
        CircularProgressIndicator()
        Spacer(modifier = Modifier.height(12.dp))
        Text("Signing out safely...", style = MaterialTheme.typography.bodyMedium)
      }
    }
    return
  }

  if (isOfflineTestSession) {
    val sessionUser = remember { DepotRoleManager.resolveOfflineTestAdminSession() }
    val shellKey = "offline_test_${sessionUser.id}_$sessionEpoch"
    val application = appContext as? Application
    val sessionStoreOwner = remember(shellKey, application) {
      SessionViewModelStoreOwner(application)
    }
    DisposableEffect(sessionStoreOwner) {
      onDispose {
        sessionStoreOwner.viewModelStore.clear()
      }
    }
    CompositionLocalProvider(LocalViewModelStoreOwner provides sessionStoreOwner) {
      key(shellKey) {
        PpioRosterShell(
          currentUser = null,
          sessionUser = sessionUser,
          firestoreDb = null,
          localRepository = localRepository,
          shellKey = shellKey,
          openTabRequest = openTabRequest,
          openTabEventId = openTabEventId,
          onLogout = {
            sessionEpoch += 1
            isSigningOut = true
          }
        )
      }
    }
    return
  }

  val user = currentUser
  if (user == null || isCredentialCheckInFlight) {
    AuthScreen(
      onCredentialCheckStateChange = { inFlight ->
        isCredentialCheckInFlight = inFlight
      },
      onVerifyOnlineCredentials = { firebaseUser, enteredUsername, enteredPassword ->
        accountService.verifyOnlineUsernamePassword(firebaseUser, enteredUsername, enteredPassword)
      },
      onOnlineCredentialVerified = { firebaseUser, verifyResult ->
        precomputedUid = firebaseUser.uid
        precomputedGateState = when (verifyResult) {
          is OnlineCredentialVerifyResult.Verified -> OnlineGateState.Registered(verifyResult.profile)
          is OnlineCredentialVerifyResult.AccountDoesNotExist -> OnlineGateState.NeedsRegistration(verifyResult.needsRegistration)
          is OnlineCredentialVerifyResult.InvalidCredentials -> null
        }
      },
      onGoogleAuthSuccess = {
        isOfflineTestSession = false
        precomputedUid = null
        precomputedGateState = null
      },
      onOfflineTestLogin = {
        precomputedUid = null
        precomputedGateState = null
        sessionEpoch += 1
        isOfflineTestSession = true
      }
    )
    return
  }

  var gateState by remember(user.uid) {
    mutableStateOf(
      if (precomputedUid == user.uid && precomputedGateState != null) {
        precomputedGateState!!
      } else {
        OnlineGateState.Checking
      }
    )
  }

  LaunchedEffect(user.uid, gateRetrySignal) {
    if (precomputedUid == user.uid && precomputedGateState != null) {
      gateState = precomputedGateState!!
      precomputedUid = null
      precomputedGateState = null
      return@LaunchedEffect
    }
    if (gateState is OnlineGateState.Registered) return@LaunchedEffect
    gateState = OnlineGateState.Checking
    gateState = try {
      when (val status = accountService.checkRegistrationStatus(user)) {
        is AccountRegistrationStatus.Registered -> OnlineGateState.Registered(status.profile)
        is AccountRegistrationStatus.NeedsRegistration -> OnlineGateState.NeedsRegistration(status)
      }
    } catch (e: Exception) {
      OnlineGateState.Error(e.localizedMessage ?: "Unable to verify your online account.")
    }
  }

  when (val state = gateState) {
    OnlineGateState.Checking -> {
      Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
          CircularProgressIndicator()
          Spacer(modifier = Modifier.height(12.dp))
          Text("Verifying online depot account...", style = MaterialTheme.typography.bodyMedium)
        }
      }
    }
    is OnlineGateState.Error -> {
      Box(
        modifier = Modifier
          .fillMaxSize()
          .padding(24.dp),
        contentAlignment = Alignment.Center
      ) {
        Column(
          horizontalAlignment = Alignment.CenterHorizontally,
          verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
          Text(
            text = state.message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error
          )
          Button(
            onClick = { gateRetrySignal += 1 },
            modifier = Modifier.testTag("retry_account_check_button")
          ) {
            Text("Retry")
          }
          TextButton(
            onClick = { isSigningOut = true },
            modifier = Modifier.testTag("back_to_login_from_error_button")
          ) {
            Text("Back to Login")
          }
        }
      }
    }
    is OnlineGateState.NeedsRegistration -> {
      RegistrationScreen(
        firebaseUser = user,
        registrationInfo = state.info,
        accountService = accountService,
        onRegistrationSuccess = { registeredProfile ->
          gateState = OnlineGateState.Registered(registeredProfile)
          sessionEpoch += 1
        },
        onCancelRegistration = {
          isSigningOut = true
        }
      )
    }
    is OnlineGateState.Registered -> {
      val sessionUser = remember(user, state.profile) {
        DepotRoleManager.resolveGoogleSession(user, persistedProfile = state.profile)
      }
      val shellKey = "cloud_${user.uid}_$sessionEpoch"
      val application = appContext as? Application
      val sessionStoreOwner = remember(shellKey, application) {
        SessionViewModelStoreOwner(application)
      }
      DisposableEffect(sessionStoreOwner) {
        onDispose {
          sessionStoreOwner.viewModelStore.clear()
        }
      }
      CompositionLocalProvider(LocalViewModelStoreOwner provides sessionStoreOwner) {
        key(shellKey) {
          PpioRosterShell(
            currentUser = user,
            sessionUser = sessionUser,
            firestoreDb = firestoreDb,
            localRepository = localRepository,
            shellKey = shellKey,
            openTabRequest = openTabRequest,
            openTabEventId = openTabEventId,
            onLogout = {
              sessionEpoch += 1
              isSigningOut = true
            }
          )
        }
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PpioRosterShell(
  currentUser: FirebaseUser?,
  sessionUser: DepotSessionUser,
  firestoreDb: FirebaseFirestore?,
  localRepository: LocalRosterRepository,
  shellKey: String,
  openTabRequest: String? = null,
  openTabEventId: Int = 0,
  onLogout: () -> Unit
) {
  val shellContext = LocalContext.current
  val appContext = shellContext.applicationContext
  val isCloudMode = !sessionUser.isOfflineTestMode && firestoreDb != null

  val rosterViewModel: RosterViewModel = viewModel(
    key = shellKey,
    factory = viewModelFactory {
      initializer {
        val repository: RosterRepository = if (isCloudMode) {
          FirestoreRosterRepository(firestoreDb!!)
        } else {
          LocalRosterRepository.getTestSandboxInstance(appContext)
        }
        RosterViewModel(
          repository = repository,
          depotId = DepotRoleManager.SHARED_DEPOT_ID,
          initialActorUid = sessionUser.id,
          initialActorName = sessionUser.displayName,
          actorEmail = if (isCloudMode) sessionUser.email.orEmpty() else "",
          isCloudMode = isCloudMode,
          initialRole = sessionUser.role,
          localCacheRepository = if (isCloudMode) localRepository else null
        )
      }
    }
  )

  val aiViewModel: AiChatViewModel = viewModel(
    key = "ai_$shellKey",
    factory = viewModelFactory {
      initializer { AiChatViewModel() }
    }
  )

  val activeRole by rosterViewModel.userRole.collectAsStateWithLifecycle()
  val resolvedRoleAccess by rosterViewModel.resolvedRoleAccessState.collectAsStateWithLifecycle()
  val currentUserProfile by rosterViewModel.currentUserProfile.collectAsStateWithLifecycle()
  val allUserProfiles by rosterViewModel.allUserProfilesState.collectAsStateWithLifecycle()
  val selectedDate by rosterViewModel.selectedDate.collectAsStateWithLifecycle()
  val staffState by rosterViewModel.staffState.collectAsStateWithLifecycle()
  val assignmentsState by rosterViewModel.assignmentsState.collectAsStateWithLifecycle()
  val leavesState by rosterViewModel.leavesState.collectAsStateWithLifecycle()
  val overridesState by rosterViewModel.overridesState.collectAsStateWithLifecycle()
  val dutyDrafts by rosterViewModel.dutyDraftsState.collectAsStateWithLifecycle()
  val dutyLocks by rosterViewModel.dutyLocksState.collectAsStateWithLifecycle()
  val depotSettings by rosterViewModel.depotSettingsState.collectAsStateWithLifecycle()
  val leaveAccounts by rosterViewModel.leaveAccountsState.collectAsStateWithLifecycle()
  val dutyPermissions by rosterViewModel.dutyPermissionsState.collectAsStateWithLifecycle()
  val importantContacts by rosterViewModel.importantContactsState.collectAsStateWithLifecycle()
  val rakes by rosterViewModel.rakesState.collectAsStateWithLifecycle()
  val stagedDraftsByDate by rosterViewModel.stagedDraftsByDate.collectAsStateWithLifecycle()
  val isBusy by rosterViewModel.isBusy.collectAsStateWithLifecycle()
  val cacheSyncState by rosterViewModel.cacheSyncState.collectAsStateWithLifecycle()

  val networkObserver = remember(shellContext) { NetworkConnectivityObserver(shellContext) }
  val syncTimestampStore = remember(shellContext) { SyncTimestampStore(shellContext) }
  val isNetworkAvailable by networkObserver.observeConnectivity()
    .collectAsStateWithLifecycle(initialValue = networkObserver.isOnlineNow())
  var lastSyncedEpochMs by remember(shellKey) {
    mutableStateOf(if (isCloudMode) syncTimestampStore.getLastSyncedEpochMs() else null)
  }
  LaunchedEffect(cacheSyncState.lastServerSyncEpochMs, isCloudMode) {
    if (!isCloudMode) return@LaunchedEffect
    val serverSync = cacheSyncState.lastServerSyncEpochMs
    if (serverSync != null) {
      lastSyncedEpochMs = serverSync
      syncTimestampStore.recordSync(serverSync)
    }
  }
  var wasOffline by remember(shellKey) { mutableStateOf(!isNetworkAvailable) }
  LaunchedEffect(isNetworkAvailable, isCloudMode) {
    if (isCloudMode && isNetworkAvailable && wasOffline) {
      rosterViewModel.syncPendingChanges(showToast = false)
    }
    wasOffline = !isNetworkAvailable
  }
  val syncStatus = remember(isCloudMode, isNetworkAvailable, cacheSyncState, lastSyncedEpochMs) {
    if (!isCloudMode) {
      FirestoreSyncStatus(
        isNetworkAvailable = false,
        isFromCache = false,
        hasPendingWrites = false,
        lastSyncedEpochMs = null,
        isOfflineTestMode = true
      )
    } else {
      FirestoreSyncStatus(
        isNetworkAvailable = isNetworkAvailable,
        isFromCache = cacheSyncState.isFromCache,
        hasPendingWrites = cacheSyncState.hasPendingWrites,
        lastSyncedEpochMs = cacheSyncState.lastServerSyncEpochMs ?: lastSyncedEpochMs
      )
    }
  }

  var openLeaveCalendarSignal by rememberSaveable(shellKey) { mutableIntStateOf(0) }
  var prefillLeaveStaffId by rememberSaveable(shellKey) { mutableStateOf<String?>(null) }
  var prefillLeaveTypeName by rememberSaveable(shellKey) { mutableStateOf<String?>(null) }
  var prefillLeaveSignal by rememberSaveable(shellKey) { mutableIntStateOf(0) }

  val visibleTabs = remember(activeRole, resolvedRoleAccess) {
    val preferredOrder = when (activeRole) {
      UserRole.SHIFT_INCHARGE -> listOf(
        DepotTab.DASHBOARD,
        DepotTab.PERMISSIONS,
        DepotTab.RAKES,
        DepotTab.SCHEDULE,
        DepotTab.DUTIES,
        DepotTab.LEAVE,
        DepotTab.STAFF,
        DepotTab.USERS,
        DepotTab.AI
      )
      UserRole.ADMIN -> listOf(
        DepotTab.DASHBOARD,
        DepotTab.SCHEDULE,
        DepotTab.DUTIES,
        DepotTab.LEAVE,
        DepotTab.PERMISSIONS,
        DepotTab.RAKES,
        DepotTab.STAFF,
        DepotTab.USERS,
        DepotTab.ROLES,
        DepotTab.AI
      )
      else -> listOf(
        DepotTab.DASHBOARD,
        DepotTab.SCHEDULE,
        DepotTab.DUTIES,
        DepotTab.LEAVE,
        DepotTab.PERMISSIONS,
        DepotTab.RAKES,
        DepotTab.STAFF,
        DepotTab.USERS,
        DepotTab.AI
      )
    }
    val allowed = preferredOrder.filter { tab ->
      when (tab) {
        DepotTab.DASHBOARD -> resolvedRoleAccess.canViewDashboard
        DepotTab.SCHEDULE -> resolvedRoleAccess.canViewShifts
        DepotTab.DUTIES -> resolvedRoleAccess.canViewDuties
        DepotTab.LEAVE -> resolvedRoleAccess.canViewLeave
        DepotTab.PERMISSIONS -> resolvedRoleAccess.canViewPermissions
        DepotTab.RAKES -> resolvedRoleAccess.canViewRakes
        DepotTab.STAFF -> resolvedRoleAccess.canViewStaff
        DepotTab.USERS -> resolvedRoleAccess.canViewUsers
        DepotTab.ROLES -> resolvedRoleAccess.canAccessRolesManager
        DepotTab.AI -> resolvedRoleAccess.canUseAi
      }
    }
    allowed.ifEmpty { listOf(DepotTab.SCHEDULE) }
  }

  val landsOnDashboard = resolvedRoleAccess.canViewDashboard && (
    activeRole == UserRole.ADMIN ||
      activeRole == UserRole.SSE ||
      activeRole == UserRole.SUPERVISOR ||
      activeRole == UserRole.JE ||
      activeRole == UserRole.TECHNICIAN ||
      activeRole == UserRole.SHIFT_INCHARGE
    )
  val defaultHomeTab = if (landsOnDashboard && DepotTab.DASHBOARD in visibleTabs) {
    DepotTab.DASHBOARD
  } else {
    visibleTabs.firstOrNull() ?: DepotTab.SCHEDULE
  }
  var currentTab by rememberSaveable(shellKey) {
    mutableStateOf(
      if (sessionUser.role == UserRole.ADMIN ||
        sessionUser.role == UserRole.SSE ||
        sessionUser.role == UserRole.SUPERVISOR ||
        sessionUser.role == UserRole.JE ||
        sessionUser.role == UserRole.TECHNICIAN ||
        sessionUser.role == UserRole.SHIFT_INCHARGE
      ) {
        DepotTab.DASHBOARD
      } else {
        DepotTab.SCHEDULE
      }
    )
  }
  var appliedRoleDefaultTab by rememberSaveable(shellKey) { mutableStateOf(false) }
  LaunchedEffect(openTabEventId, openTabRequest) {
    if (openTabEventId > 0 && openTabRequest == TechnicianDutyNotifier.TAB_DUTIES && DepotTab.DUTIES in visibleTabs) {
      currentTab = DepotTab.DUTIES
      appliedRoleDefaultTab = true
    }
  }
  LaunchedEffect(activeRole, landsOnDashboard) {
    if (!appliedRoleDefaultTab && landsOnDashboard) {
      currentTab = DepotTab.DASHBOARD
      appliedRoleDefaultTab = true
    }
  }
  LaunchedEffect(visibleTabs) {
    if (currentTab !in visibleTabs) {
      currentTab = defaultHomeTab
    }
  }
  BackHandler(enabled = currentTab != defaultHomeTab) {
    currentTab = defaultHomeTab
  }

  // Technician Duty Notification preferences, live assignment change detector, & pre-shift reminder scheduler
  var dutyNotificationsEnabled by remember(shellKey) {
    mutableStateOf(TechnicianDutyNotifier.isEnabled(shellContext))
  }
  var dutyReminderLeadMinutes by remember(shellKey) {
    mutableIntStateOf(TechnicianDutyNotifier.getLeadMinutes(shellContext))
  }
  var notificationSyncTrigger by remember(shellKey) { mutableIntStateOf(0) }
  var nextScheduledReminder by remember(shellKey) { mutableStateOf<ScheduledDutyReminderInfo?>(null) }

  val linkedStaffIdForNotif = currentUserProfile?.staffId.orEmpty().trim()
  LaunchedEffect(
    activeRole,
    linkedStaffIdForNotif,
    assignmentsState,
    depotSettings.showShunterForTechnicians,
    dutyNotificationsEnabled,
    dutyReminderLeadMinutes,
    notificationSyncTrigger
  ) {
    if (activeRole != UserRole.TECHNICIAN) {
      nextScheduledReminder = null
      TechnicianDutyNotifier.cancelAllScheduledReminders(shellContext)
      return@LaunchedEffect
    }
    val successAssignments = (assignmentsState as? UiState.Success)?.data ?: return@LaunchedEffect
    val staffListNow = (staffState as? UiState.Success)?.data ?: emptyList()
    val linkedStaffName = staffListNow.firstOrNull { it.id == linkedStaffIdForNotif }?.name
      ?: currentUserProfile?.displayName
      ?: sessionUser.displayName
    nextScheduledReminder = TechnicianDutyNotifier.syncTechnicianDutyNotifications(
      context = shellContext,
      staffId = linkedStaffIdForNotif,
      staffName = linkedStaffName,
      assignments = successAssignments,
      showShunterForTechnicians = depotSettings.showShunterForTechnicians
    )
  }

  val displayedName = currentUserProfile?.displayName?.takeIf { it.isNotBlank() } ?: sessionUser.displayName
  val displayedActorUid = currentUser?.uid ?: sessionUser.id
  val displayedUsername = currentUserProfile?.username?.takeIf { it.isNotBlank() }
  val displayedEmail = currentUserProfile?.email?.takeIf { it.isNotBlank() } ?: sessionUser.email.orEmpty()

  val appReleaseConfig by rosterViewModel.appReleaseState.collectAsStateWithLifecycle()
  val appUpdateEvaluation by rosterViewModel.appUpdateEvaluationState.collectAsStateWithLifecycle()

  var showUserMenu by remember { mutableStateOf(false) }
  var showUserDetailsDialog by remember { mutableStateOf(false) }
  var showChangePasswordDialog by remember { mutableStateOf(false) }
  var showRolePermissionsDialog by remember { mutableStateOf(false) }
  var showAppUpdateDialog by remember { mutableStateOf(false) }
  var showAdminAppUpdatesScreen by rememberSaveable(shellKey) { mutableStateOf(false) }
  var dismissedUpdateVersionCode by rememberSaveable(shellKey) { mutableIntStateOf(0) }

  val snackbarHostState = remember { SnackbarHostState() }
  StatusSnackbarEffect(
    rosterViewModel = rosterViewModel,
    snackbarHostState = snackbarHostState
  )
  val tabStateHolder = rememberSaveableStateHolder()

  BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
    val isCompactHeight = maxHeight < 520.dp
    // Only use a vertical side NavigationRail on genuinely large/tall screens (tablets/unfolded).
    // Rotated phones have compact height (< 520dp), so keeping a wide, compact bottom bar makes
    // all tabs easy to see and switch with a single thumb tap without cramping on the side.
    val useNavRail = maxWidth >= 840.dp && !isCompactHeight
    val safeSystemInsets = WindowInsets.systemBars.union(WindowInsets.displayCutout)

    val syncTag = when {
      !isCloudMode -> "Offline Test Sandbox"
      !syncStatus.isNetworkAvailable && syncStatus.hasPendingWrites -> "Offline · Pending Sync"
      !syncStatus.isNetworkAvailable -> "Offline Cache"
      syncStatus.hasPendingWrites -> "Syncing…"
      syncStatus.isFromCache -> "Cached Sync"
      else -> "Cloud Sync"
    }
    val subHandle = displayedUsername?.let { "@$it" }
      ?: displayedEmail.takeIf { it.isNotBlank() }
      ?: ""
    val subtitleLine = listOfNotNull(
      displayedName,
      subHandle.takeIf { it.isNotBlank() },
      syncTag
    ).joinToString(" · ")
    val compactSubtitleLine = listOfNotNull(
      displayedName.takeIf { it.isNotBlank() },
      syncTag
    ).joinToString(" · ")
    val subtitleColor = when {
      !isCloudMode -> SafetyAmberOnContainer
      !syncStatus.isNetworkAvailable || syncStatus.isFromCache -> SafetyAmberOnContainer
      syncStatus.hasPendingWrites -> MaterialTheme.colorScheme.primary
      else -> SignalEmeraldOnContainer
    }

    Row(modifier = Modifier.fillMaxSize()) {
      if (useNavRail) {
        NavigationRail(
          modifier = Modifier
            .fillMaxHeight()
            .liquidGlassSurface(
              shape = RoundedCornerShape(topEnd = 26.dp, bottomEnd = 26.dp),
              topColor = GlassSurfaceBright,
              bottomColor = GlassSurfaceMid,
              elevation = 8.dp
            ),
          containerColor = Color.Transparent,
          windowInsets = safeSystemInsets.only(WindowInsetsSides.Vertical + WindowInsetsSides.Start)
        ) {
          Column(
            modifier = Modifier
              .fillMaxHeight()
              .verticalScroll(rememberScrollState())
              .padding(vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
          ) {
            visibleTabs.forEach { tab ->
              val selected = currentTab == tab
              val tabLabel = dashboardTabLabel(tab, activeRole)
              NavigationRailItem(
                selected = selected,
                onClick = { currentTab = tab },
                icon = {
                  Icon(
                    imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                    contentDescription = tabLabel
                  )
                },
                label = {
                  Text(
                    text = tabLabel,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                  )
                },
                modifier = Modifier.testTag(tab.testTag)
              )
            }
          }
        }
      }

      Scaffold(
        modifier = Modifier.weight(1f),
        containerColor = Color.Transparent,
        contentWindowInsets = safeSystemInsets.only(
          if (useNavRail) {
            WindowInsetsSides.Vertical + WindowInsetsSides.End
          } else {
            WindowInsetsSides.Horizontal + WindowInsetsSides.Vertical
          }
        ),
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
          if (isCompactHeight) {
            CompactLandscapeTopBar(
              activeRole = activeRole,
              subtitleLine = compactSubtitleLine,
              subtitleColor = subtitleColor,
              insets = safeSystemInsets.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
              actions = {
                UserAccountMenuAction(
                  activeRole = activeRole,
                  displayedName = displayedName,
                  displayedUsername = displayedUsername,
                  displayedEmail = displayedEmail,
                  canChangePassword = isCloudMode,
                  isAdmin = resolvedRoleAccess.isAdmin,
                  installedVersionName = appUpdateEvaluation.installedVersionName,
                  isUpdateAvailable = appUpdateEvaluation.isUpdateAvailable,
                  showUserMenu = showUserMenu,
                  onShowUserMenuChange = { showUserMenu = it },
                  onOpenUserDetails = { showUserDetailsDialog = true },
                  onOpenChangePassword = { showChangePasswordDialog = true },
                  onOpenRolePermissions = { showRolePermissionsDialog = true },
                  onOpenAppUpdates = { showAppUpdateDialog = true },
                  onOpenAdminAppUpdates = { showAdminAppUpdatesScreen = true },
                  onLogout = onLogout
                )
              }
            )
          } else {
            Box(
              modifier = Modifier
                .fillMaxWidth()
                .liquidGlassSurface(
                  shape = RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp),
                  topColor = GlassSurfaceBright,
                  bottomColor = GlassSurfaceMid,
                  elevation = 8.dp
                )
            ) {
              TopAppBar(
                windowInsets = safeSystemInsets.only(
                  if (useNavRail) {
                    WindowInsetsSides.Top + WindowInsetsSides.End
                  } else {
                    WindowInsetsSides.Top + WindowInsetsSides.Horizontal
                  }
                ),
                colors = TopAppBarDefaults.topAppBarColors(
                  containerColor = Color.Transparent,
                  titleContentColor = MaterialTheme.colorScheme.onSurface
                ),
                title = {
                  Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    @OptIn(ExperimentalLayoutApi::class)
                    FlowRow(
                      horizontalArrangement = Arrangement.spacedBy(8.dp),
                      verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                      Text(
                        text = "PPIO ROSTER",
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 1
                      )
                      val (badgeTint, badgeInk, badgeBorder) = roleBadgeStyle(activeRole)
                      GlassPillBadge(
                        text = activeRole.label.uppercase(),
                        tintColor = badgeTint,
                        textColor = badgeInk,
                        borderColor = badgeBorder,
                        modifier = Modifier.testTag("active_role_badge")
                      )
                    }
                    Text(
                      text = subtitleLine,
                      style = MaterialTheme.typography.labelSmall,
                      color = subtitleColor,
                      maxLines = 2,
                      overflow = TextOverflow.Ellipsis
                    )
                  }
                },
                actions = {
                  UserAccountMenuAction(
                    activeRole = activeRole,
                    displayedName = displayedName,
                    displayedUsername = displayedUsername,
                    displayedEmail = displayedEmail,
                    canChangePassword = isCloudMode,
                    isAdmin = resolvedRoleAccess.isAdmin,
                    installedVersionName = appUpdateEvaluation.installedVersionName,
                    isUpdateAvailable = appUpdateEvaluation.isUpdateAvailable,
                    showUserMenu = showUserMenu,
                    onShowUserMenuChange = { showUserMenu = it },
                    onOpenUserDetails = { showUserDetailsDialog = true },
                    onOpenChangePassword = { showChangePasswordDialog = true },
                    onOpenRolePermissions = { showRolePermissionsDialog = true },
                    onOpenAppUpdates = { showAppUpdateDialog = true },
                    onOpenAdminAppUpdates = { showAdminAppUpdatesScreen = true },
                    onLogout = onLogout
                  )
                }
              )
            }
          }
        },
        bottomBar = {
          if (!useNavRail) {
            FloatingGlassNavBar(
              tabs = visibleTabs,
              currentTab = currentTab,
              activeRole = activeRole,
              isCompactHeight = isCompactHeight,
              onSelectTab = { currentTab = it }
            )
          }
        }
      ) { innerPadding ->
        Box(
          modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
        ) {
          when {
            staffState is UiState.Loading -> {
              Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                  CircularProgressIndicator()
                  Spacer(modifier = Modifier.height(12.dp))
                  Text("Syncing PPIO depot roster...", style = MaterialTheme.typography.bodyMedium)
                }
              }
            }
            staffState is UiState.Error -> {
              val err = (staffState as UiState.Error).message
              Box(
                modifier = Modifier
                  .fillMaxSize()
                  .padding(24.dp),
                contentAlignment = Alignment.Center
              ) {
                Column(
                  horizontalAlignment = Alignment.CenterHorizontally,
                  verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                  Text(
                    text = "Unable to load roster: $err",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                  )
                  Button(
                    onClick = { rosterViewModel.retrySync() },
                    modifier = Modifier.testTag("retry_roster_sync_button")
                  ) {
                    Text("Retry Sync")
                  }
                }
              }
            }
            else -> {
              val staffList = (staffState as? UiState.Success)?.data ?: emptyList()
              val assignments = (assignmentsState as? UiState.Success)?.data ?: emptyList()
              val leaves = (leavesState as? UiState.Success)?.data ?: emptyList()
              val overrides = (overridesState as? UiState.Success)?.data ?: emptyList()
              val streamSyncError = (assignmentsState as? UiState.Error)?.message
                ?: (leavesState as? UiState.Error)?.message

              Column(modifier = Modifier.fillMaxSize()) {
                SyncStatusBanner(
                  syncStatus = syncStatus,
                  onSyncNow = {
                    rosterViewModel.syncPendingChanges(showToast = true)
                    rosterViewModel.retrySync()
                  }
                )
                OptionalUpdateBanner(
                  evaluation = appUpdateEvaluation,
                  visible = dismissedUpdateVersionCode != appUpdateEvaluation.release.latestVersionCode,
                  onOpenDetails = { showAppUpdateDialog = true },
                  onDismiss = {
                    dismissedUpdateVersionCode = appUpdateEvaluation.release.latestVersionCode
                  }
                )
                if (streamSyncError != null) {
                  Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                      .fillMaxWidth()
                      .padding(horizontal = 16.dp, vertical = 8.dp)
                      .testTag("stream_sync_error_banner")
                  ) {
                    Row(
                      modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                      verticalAlignment = Alignment.CenterVertically,
                      horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                      Text(
                        text = "Sync interrupted: $streamSyncError",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.weight(1f)
                      )
                      TextButton(
                        onClick = { rosterViewModel.retrySync() },
                        modifier = Modifier.testTag("retry_stream_sync_button")
                      ) {
                        Text("Retry", fontWeight = FontWeight.Bold)
                      }
                    }
                  }
                }

                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                  tabStateHolder.SaveableStateProvider(currentTab.name) {
                when (currentTab) {
                  DepotTab.SCHEDULE -> ScheduleScreen(
                    selectedDate = selectedDate,
                    staffList = staffList,
                    assignments = assignments,
                    leaves = leaves,
                    userRole = activeRole,
                    roleAccess = resolvedRoleAccess,
                    showShunterForTechnicians = depotSettings.showShunterForTechnicians,
                    dutyDrafts = dutyDrafts,
                    dutyLocks = dutyLocks,
                    stagedDraftsByDate = stagedDraftsByDate,
                    isBusy = isBusy,
                    onSelectDate = rosterViewModel::selectDate,
                    onPrevDay = rosterViewModel::previousDay,
                    onNextDay = rosterViewModel::nextDay,
                    onAutoAssignDay = { rosterViewModel.autoAssignSelectedDay(overwriteExisting = false) },
                    onAutoAssignWeek = { rosterViewModel.autoAssignCurrentWeek(overwriteExisting = false) },
                    onAssignStaff = rosterViewModel::assignStaffToSlot,
                    onClearSlot = rosterViewModel::clearSlot,
                    onSubmitDraftForApproval = rosterViewModel::submitStagedDraftForApproval,
                    onDiscardDraft = rosterViewModel::discardStagedDraft,
                    onApproveDraft = rosterViewModel::approveDutyDraft,
                    onRejectDraft = { draft -> rosterViewModel.rejectDutyDraft(draft) }
                  )
                  DepotTab.DUTIES -> TomorrowsDutiesScreen(
                    staffList = staffList,
                    assignments = assignments,
                    userRole = activeRole,
                    roleAccess = resolvedRoleAccess,
                    dutyLocks = dutyLocks,
                    allowTechnicianShare = depotSettings.allowTechnicianShare,
                    showShunterForTechnicians = depotSettings.showShunterForTechnicians,
                    includeShunterInExport = depotSettings.includeShunterInExport,
                    isBusy = isBusy,
                    onToggleTechnicianShare = rosterViewModel::setTechnicianShareEnabled,
                    onToggleShowShunterForTechnicians = rosterViewModel::setShowShunterForTechnicians,
                    onToggleIncludeShunterInExport = rosterViewModel::setIncludeShunterInExport,
                    onAutoFillRange = rosterViewModel::autoAssignTomorrowsDuties,
                    onNotifyMessage = rosterViewModel::postStatusMessage
                  )
                  DepotTab.LEAVE -> LeaveAndRestScreen(
                    selectedDate = selectedDate,
                    staffList = staffList,
                    assignments = assignments,
                    leaves = leaves,
                    leaveAccounts = leaveAccounts,
                    userRole = activeRole,
                    roleAccess = resolvedRoleAccess,
                    currentActorUid = displayedActorUid,
                    linkedStaffId = currentUserProfile?.staffId.orEmpty(),
                    isBusy = isBusy,
                    onAddLeave = rosterViewModel::addLeave,
                    onUpdateLeave = rosterViewModel::updateLeavePeriodAndStatus,
                    onApprovePendingLeave = rosterViewModel::approvePendingLeave,
                    onRejectPendingLeave = rosterViewModel::rejectPendingLeave,
                    onDeleteLeave = rosterViewModel::deleteLeave,
                    onUpdateRestDay = rosterViewModel::updateStaffRestDay,
                    onDistributeBalancedRestDays = rosterViewModel::distributeBalancedRestDays,
                    onUpdateLapBalance = rosterViewModel::updateLapBalance,
                    onApproveLapCorrection = rosterViewModel::approveLapCorrectionRequest,
                    onRejectLapCorrection = rosterViewModel::rejectLapCorrectionRequest,
                    openCalendarSignal = openLeaveCalendarSignal,
                    prefillLeaveStaffId = prefillLeaveStaffId,
                    prefillLeaveType = prefillLeaveTypeName?.let { LeaveType.fromRaw(it) },
                    prefillLeaveSignal = prefillLeaveSignal
                  )
                  DepotTab.PERMISSIONS -> PermissionManagerScreen(
                    selectedDate = selectedDate,
                    staffList = staffList,
                    assignments = assignments,
                    leaves = leaves,
                    permissions = dutyPermissions,
                    userRole = activeRole,
                    roleAccess = resolvedRoleAccess,
                    isBusy = isBusy,
                    onGrantPermission = rosterViewModel::grantDutyPermission,
                    onDeletePermission = rosterViewModel::deleteDutyPermission
                  )
                  DepotTab.STAFF -> StaffDirectoryScreen(
                    selectedDate = selectedDate,
                    staffList = staffList,
                    leaves = leaves,
                    userRole = activeRole,
                    roleAccess = resolvedRoleAccess,
                    isBusy = isBusy,
                    onAddStaffMember = rosterViewModel::addStaffMember,
                    onUpdateStaffMember = rosterViewModel::updateStaffMember,
                    onUpdateStaffTrainingDue = rosterViewModel::updateStaffTrainingDue,
                    onDeleteStaffMember = rosterViewModel::deleteStaffMember
                  )
                  DepotTab.RAKES -> RakeManagerScreen(
                    rakes = rakes,
                    userRole = activeRole,
                    roleAccess = resolvedRoleAccess,
                    isBusy = isBusy,
                    onSaveRake = rosterViewModel::saveRakeFormation,
                    onReverseRake = rosterViewModel::reverseRakeFormation,
                    onDeleteRake = rosterViewModel::deleteRakeFormation,
                    onNotifyMessage = rosterViewModel::postStatusMessage
                  )
                  DepotTab.USERS -> UsersScreen(
                    userProfiles = allUserProfiles,
                    staffList = staffList,
                    currentActorUid = displayedActorUid,
                    isCloudMode = isCloudMode,
                    userRole = activeRole,
                    roleAccess = resolvedRoleAccess,
                    allowTechnicianShare = depotSettings.allowTechnicianShare,
                    isBusy = isBusy,
                    onToggleTechnicianShare = rosterViewModel::setTechnicianShareEnabled,
                    onCreateUser = rosterViewModel::createUserAccount,
                    onUpdateUser = rosterViewModel::updateUserAccount,
                    onDeleteUser = rosterViewModel::deleteUserAccount,
                    onAssignUserRole = rosterViewModel::assignUserRole
                  )
                  DepotTab.ROLES -> RolesManagerScreen(
                    userRole = activeRole,
                    depotSettings = depotSettings,
                    isBusy = isBusy,
                    onSaveRoleAccess = rosterViewModel::updateRoleMenuAccess,
                    onResetRoleAccess = rosterViewModel::resetRoleMenuAccess
                  )
                  DepotTab.DASHBOARD -> DashboardScreen(
                    selectedDate = selectedDate,
                    staffList = staffList,
                    assignments = assignments,
                    leaves = leaves,
                    overrides = overrides,
                    dutyDrafts = dutyDrafts,
                    dutyLocks = dutyLocks,
                    leaveAccounts = leaveAccounts,
                    dutyPermissions = dutyPermissions,
                    importantContacts = importantContacts,
                    allUserProfiles = allUserProfiles,
                    linkedStaffId = currentUserProfile?.staffId.orEmpty(),
                    userRole = activeRole,
                    roleAccess = resolvedRoleAccess,
                    isBusy = isBusy,
                    onSelectDate = rosterViewModel::selectDate,
                    onAutoAssignWeek = if (resolvedRoleAccess.canDirectEditDuties) {
                      { rosterViewModel.autoAssignCurrentWeek(overwriteExisting = false) }
                    } else null,
                    onUpdateLapBalance = rosterViewModel::updateLapBalance,
                    onUpdateStaffTrainingDue = rosterViewModel::updateStaffTrainingDue,
                    onRelieveForTraining = { staff ->
                      if (resolvedRoleAccess.canViewLeave) {
                        prefillLeaveStaffId = staff.id
                        prefillLeaveTypeName = LeaveType.TRAINING.code
                        prefillLeaveSignal += 1
                        currentTab = DepotTab.LEAVE
                      }
                    },
                    onAddImportantContact = rosterViewModel::addImportantContact,
                    onDeleteImportantContact = rosterViewModel::deleteImportantContact,
                    onOpenShiftsTab = { if (resolvedRoleAccess.canViewShifts) currentTab = DepotTab.SCHEDULE },
                    onOpenDutiesTab = { if (resolvedRoleAccess.canViewDuties) currentTab = DepotTab.DUTIES },
                    onOpenLeaveTab = { if (resolvedRoleAccess.canViewLeave) currentTab = DepotTab.LEAVE },
                    onOpenLeaveCalendar = {
                      if (resolvedRoleAccess.canViewLeave) {
                        openLeaveCalendarSignal += 1
                        currentTab = DepotTab.LEAVE
                      }
                    },
                    onOpenPermissionsTab = {
                      if (resolvedRoleAccess.canViewPermissions) {
                        currentTab = DepotTab.PERMISSIONS
                      }
                    },
                    onOpenStaffTab = { if (resolvedRoleAccess.canViewStaff) currentTab = DepotTab.STAFF },
                    onOpenUsersTab = {
                      if (resolvedRoleAccess.canViewUsers) {
                        currentTab = DepotTab.USERS
                      }
                    },
                    onOpenRolesTab = {
                      if (resolvedRoleAccess.canAccessRolesManager) {
                        currentTab = DepotTab.ROLES
                      }
                    },
                    onOpenAppUpdatesManager = {
                      if (resolvedRoleAccess.isAdmin) {
                        showAdminAppUpdatesScreen = true
                      }
                    },
                    publishedAppVersionLabel = if (appReleaseConfig.hasActiveRelease) {
                      appReleaseConfig.latestVersionName
                    } else {
                      ""
                    },
                    rakesCount = rakes.size,
                    onOpenRakesTab = { if (resolvedRoleAccess.canViewRakes) currentTab = DepotTab.RAKES },
                    dutyNotificationsEnabled = dutyNotificationsEnabled,
                    dutyReminderLeadMinutes = dutyReminderLeadMinutes,
                    nextScheduledReminder = nextScheduledReminder,
                    onToggleDutyNotifications = { enabled ->
                      dutyNotificationsEnabled = enabled
                      TechnicianDutyNotifier.setEnabled(shellContext, enabled)
                      notificationSyncTrigger += 1
                      rosterViewModel.postStatusMessage(
                        if (enabled) "Technician duty notifications enabled."
                        else "Technician duty notifications paused."
                      )
                    },
                    onSelectDutyReminderLeadMinutes = { minutes ->
                      dutyReminderLeadMinutes = minutes
                      TechnicianDutyNotifier.setLeadMinutes(shellContext, minutes)
                      notificationSyncTrigger += 1
                      rosterViewModel.postStatusMessage(
                        "Shift reminder set to ${TechnicianDutyNotifier.formatLeadLabel(minutes)} before duty."
                      )
                    },
                    onNotificationPermissionChanged = {
                      notificationSyncTrigger += 1
                    },
                    onSendTestDutyNotification = {
                      val posted = TechnicianDutyNotifier.sendTestDutyNotification(
                        context = shellContext,
                        nextDutyInfo = nextScheduledReminder
                      )
                      rosterViewModel.postStatusMessage(
                        if (posted) "Test duty notification sent."
                        else "Please allow notifications in Android settings to receive duty alerts."
                      )
                    }
                  )
                  DepotTab.AI -> AiTabContent(
                    aiViewModel = aiViewModel,
                    userRole = activeRole,
                    liveContextProvider = rosterViewModel::buildAiContextSummary,
                    onNotifyMessage = rosterViewModel::postStatusMessage
                  )
                }
              }
            }
          }
            }
          }
        }
      }
    }
  }

  val staffListForDialogs = (staffState as? UiState.Success)?.data ?: emptyList()

  if (showUserDetailsDialog) {
    UserDetailsDialog(
      displayName = displayedName,
      username = displayedUsername,
      email = displayedEmail,
      role = activeRole,
      authMethod = if (isCloudMode) {
        "Online Gmail Cloud Sync"
      } else {
        "Offline Test Login (Local Sandbox · No Gmail / No Server Sync)"
      },
      linkedStaff = currentUserProfile?.staffId?.takeIf { it.isNotBlank() }?.let { sid ->
        staffListForDialogs.firstOrNull { it.id == sid }
      },
      phoneNumber1 = currentUserProfile?.phoneNumber.orEmpty(),
      phoneNumber2 = currentUserProfile?.secondaryPhoneNumber.orEmpty(),
      onDismiss = { showUserDetailsDialog = false }
    )
  }

  if (isCloudMode && showChangePasswordDialog) {
    ChangePasswordDialog(
      isBusy = isBusy,
      onChangePassword = { currentPwd, newPwd, confirmPwd, callback ->
        rosterViewModel.changeCurrentUserPassword(currentPwd, newPwd, confirmPwd, callback)
      },
      onDismiss = { showChangePasswordDialog = false }
    )
  }

  if (showRolePermissionsDialog) {
    val (badgeTint, badgeInk, badgeBorder) = roleBadgeStyle(activeRole)
    RolePermissionsDialog(
      role = activeRole,
      badgeTint = badgeTint,
      badgeInk = badgeInk,
      badgeBorder = badgeBorder,
      roleAccess = resolvedRoleAccess,
      onDismiss = { showRolePermissionsDialog = false }
    )
  }

  if (showAppUpdateDialog) {
    AppUpdateStatusDialog(
      evaluation = appUpdateEvaluation,
      isAdmin = resolvedRoleAccess.isAdmin,
      onDownload = { url ->
        val opened = openVerifiedUpdateUrl(shellContext, url)
        if (!opened) {
          rosterViewModel.postStatusMessage("Unable to open update link. Please check the URL or browser app.")
        }
      },
      onOpenAdminManager = {
        if (resolvedRoleAccess.isAdmin) {
          showAdminAppUpdatesScreen = true
        }
      },
      onNotifyMessage = rosterViewModel::postStatusMessage,
      onDismiss = { showAppUpdateDialog = false }
    )
  }

  if (appUpdateEvaluation.isMandatory && !showAdminAppUpdatesScreen) {
    MandatoryUpdateGateScreen(
      evaluation = appUpdateEvaluation,
      isAdmin = resolvedRoleAccess.isAdmin,
      onDownload = { url ->
        val opened = openVerifiedUpdateUrl(shellContext, url)
        if (!opened) {
          rosterViewModel.postStatusMessage("Unable to open update link. Please check your browser app.")
        }
      },
      onRecheck = {
        rosterViewModel.retrySync()
        rosterViewModel.postStatusMessage("Rechecked latest release status.")
      },
      onOpenAdminManager = {
        if (resolvedRoleAccess.isAdmin) {
          showAdminAppUpdatesScreen = true
        }
      },
      onNotifyMessage = rosterViewModel::postStatusMessage
    )
  }

  if (showAdminAppUpdatesScreen && resolvedRoleAccess.isAdmin) {
    AdminAppUpdatesScreen(
      userRole = activeRole,
      currentRelease = appReleaseConfig,
      isBusy = isBusy,
      onPublishRelease = rosterViewModel::publishAppRelease,
      onClearRelease = rosterViewModel::clearAppRelease,
      onNotifyMessage = rosterViewModel::postStatusMessage,
      onClose = { showAdminAppUpdatesScreen = false }
    )
  }
}

@Composable
private fun StatusSnackbarEffect(
  rosterViewModel: RosterViewModel,
  snackbarHostState: SnackbarHostState
) {
  val actionStatus by rosterViewModel.actionStatus.collectAsStateWithLifecycle()
  LaunchedEffect(actionStatus) {
    val msg = actionStatus
    if (!msg.isNullOrBlank()) {
      snackbarHostState.showSnackbar(msg)
      rosterViewModel.clearStatusMessage()
    }
  }
}

@Composable
private fun AiTabContent(
  aiViewModel: AiChatViewModel,
  userRole: UserRole,
  liveContextProvider: () -> String,
  onNotifyMessage: (String) -> Unit
) {
  val aiMessages by aiViewModel.messages.collectAsStateWithLifecycle()
  val aiUiState by aiViewModel.uiState.collectAsStateWithLifecycle()
  AiAssistantScreen(
    messages = aiMessages,
    uiState = aiUiState,
    userRole = userRole,
    liveContextProvider = liveContextProvider,
    onSendMessage = aiViewModel::sendMessage,
    onCancelStreaming = aiViewModel::cancelStreaming,
    onRetryLastMessage = aiViewModel::retryLastMessage,
    onClearChat = aiViewModel::clearConversation,
    onNotifyMessage = onNotifyMessage
  )
}

private fun dashboardTabLabel(tab: DepotTab, role: UserRole): String = when {
  tab == DepotTab.DASHBOARD && role == UserRole.TECHNICIAN -> "Home"
  tab == DepotTab.DASHBOARD && (
    role == UserRole.ADMIN ||
      role == UserRole.SSE ||
      role == UserRole.SUPERVISOR ||
      role == UserRole.JE ||
      role == UserRole.SHIFT_INCHARGE
    ) -> "Dashboard"
  else -> tab.label
}

@Composable
private fun CompactLandscapeTopBar(
  activeRole: UserRole,
  subtitleLine: String,
  subtitleColor: Color,
  insets: WindowInsets,
  actions: @Composable () -> Unit
) {
  Box(
    modifier = Modifier
      .fillMaxWidth()
      .liquidGlassSurface(
        shape = RoundedCornerShape(bottomStart = 18.dp, bottomEnd = 18.dp),
        topColor = GlassSurfaceBright,
        bottomColor = GlassSurfaceMid,
        elevation = 6.dp
      )
      .windowInsetsPadding(insets)
      .heightIn(min = 48.dp)
      .padding(horizontal = 14.dp, vertical = 2.dp)
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
      Text(
        text = "PPIO ROSTER",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
      )
      val (badgeTint, badgeInk, badgeBorder) = roleBadgeStyle(activeRole)
      GlassPillBadge(
        text = activeRole.label.uppercase(),
        tintColor = badgeTint,
        textColor = badgeInk,
        borderColor = badgeBorder,
        modifier = Modifier.testTag("active_role_badge")
      )
      Text(
        text = subtitleLine,
        style = MaterialTheme.typography.labelSmall,
        color = subtitleColor,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.weight(1f)
      )
      actions()
    }
  }
}

@Composable
private fun UserAccountMenuAction(
  activeRole: UserRole,
  displayedName: String,
  displayedUsername: String?,
  displayedEmail: String,
  canChangePassword: Boolean = true,
  isAdmin: Boolean = false,
  installedVersionName: String = "",
  isUpdateAvailable: Boolean = false,
  showUserMenu: Boolean,
  onShowUserMenuChange: (Boolean) -> Unit,
  onOpenUserDetails: () -> Unit,
  onOpenChangePassword: () -> Unit,
  onOpenRolePermissions: () -> Unit,
  onOpenAppUpdates: () -> Unit,
  onOpenAdminAppUpdates: () -> Unit,
  onLogout: () -> Unit
) {
  val (_, _, roleAccent) = roleBadgeStyle(activeRole)
  Box {
    IconButton(
      onClick = { onShowUserMenuChange(true) },
      modifier = Modifier
        .size(48.dp)
        .testTag("user_menu_button")
    ) {
      GlassIconOrb(
        shape = RoundedCornerShape(14.dp),
        tintColor = roleAccent.copy(alpha = 0.16f),
        modifier = Modifier.size(36.dp)
      ) {
        Icon(
          imageVector = Icons.Default.AccountCircle,
          contentDescription = "User account menu",
          tint = roleAccent
        )
      }
    }

    DropdownMenu(
      expanded = showUserMenu,
      onDismissRequest = { onShowUserMenuChange(false) },
      containerColor = GlassPopupSurface,
      modifier = Modifier.testTag("user_account_menu")
    ) {
      Column(
        modifier = Modifier
          .padding(horizontal = 16.dp, vertical = 10.dp)
          .widthIn(min = 190.dp, max = 260.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
      ) {
        Text(
          text = displayedName,
          style = MaterialTheme.typography.titleSmall,
          fontWeight = FontWeight.Bold,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis
        )
        val subText = listOfNotNull(
          displayedUsername?.let { "@$it" },
          displayedEmail.takeIf { it.isNotBlank() }
        ).joinToString(" · ").ifBlank { activeRole.label }
        Text(
          text = subText,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis
        )
      }
      HorizontalDivider()

      DropdownMenuItem(
        text = { Text("User Details") },
        leadingIcon = {
          Icon(
            imageVector = Icons.Default.Badge,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary
          )
        },
        onClick = {
          onShowUserMenuChange(false)
          onOpenUserDetails()
        },
        modifier = Modifier
          .heightIn(min = 48.dp)
          .testTag("menu_user_details")
      )

      if (canChangePassword) {
        DropdownMenuItem(
          text = { Text("Change Password") },
          leadingIcon = {
            Icon(
              imageVector = Icons.Default.Lock,
              contentDescription = null,
              tint = DepotTeal
            )
          },
          onClick = {
            onShowUserMenuChange(false)
            onOpenChangePassword()
          },
          modifier = Modifier
            .heightIn(min = 48.dp)
            .testTag("menu_change_password")
        )
      }

      DropdownMenuItem(
        text = { Text("Role & Permissions") },
        leadingIcon = {
          Icon(
            imageVector = Icons.Default.Security,
            contentDescription = null,
            tint = roleAccent
          )
        },
        onClick = {
          onShowUserMenuChange(false)
          onOpenRolePermissions()
        },
        modifier = Modifier
          .heightIn(min = 48.dp)
          .testTag("menu_role_permissions")
      )

      DropdownMenuItem(
        text = {
          val verSuffix = installedVersionName.takeIf { it.isNotBlank() }?.let { " · v$it" }.orEmpty()
          Text(
            text = if (isUpdateAvailable) "App Updates (New!)" else "App Updates$verSuffix",
            fontWeight = if (isUpdateAvailable) FontWeight.Bold else FontWeight.Normal,
            color = if (isUpdateAvailable) RailwayCobalt else MaterialTheme.colorScheme.onSurface
          )
        },
        leadingIcon = {
          Icon(
            imageVector = Icons.Default.SystemUpdate,
            contentDescription = null,
            tint = if (isUpdateAvailable) RailwayCobalt else DepotTeal
          )
        },
        onClick = {
          onShowUserMenuChange(false)
          onOpenAppUpdates()
        },
        modifier = Modifier
          .heightIn(min = 48.dp)
          .testTag("menu_app_updates")
      )

      if (isAdmin) {
        DropdownMenuItem(
          text = { Text("Manage App Updates") },
          leadingIcon = {
            Icon(
              imageVector = Icons.Default.AdminPanelSettings,
              contentDescription = null,
              tint = SignalCrimson
            )
          },
          onClick = {
            onShowUserMenuChange(false)
            onOpenAdminAppUpdates()
          },
          modifier = Modifier
            .heightIn(min = 48.dp)
            .testTag("menu_admin_app_updates")
        )
      }

      HorizontalDivider()

      DropdownMenuItem(
        text = {
          Text(
            text = "Logout",
            color = SignalCrimson,
            fontWeight = FontWeight.SemiBold
          )
        },
        leadingIcon = {
          Icon(
            imageVector = Icons.AutoMirrored.Filled.ExitToApp,
            contentDescription = null,
            tint = SignalCrimson
          )
        },
        onClick = {
          onShowUserMenuChange(false)
          onLogout()
        },
        modifier = Modifier
          .heightIn(min = 48.dp)
          .testTag("menu_logout")
      )
    }
  }
}

@Composable
private fun FloatingGlassNavBar(
  tabs: List<DepotTab>,
  currentTab: DepotTab,
  activeRole: UserRole = UserRole.SUPERVISOR,
  isCompactHeight: Boolean = false,
  onSelectTab: (DepotTab) -> Unit
) {
  val navBarInsets = WindowInsets.systemBars
    .union(WindowInsets.displayCutout)
    .only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal)

  BoxWithConstraints(
    modifier = Modifier
      .fillMaxWidth()
      .windowInsetsPadding(navBarInsets)
      .padding(
        horizontal = if (isCompactHeight) 12.dp else 10.dp,
        vertical = if (isCompactHeight) 4.dp else 8.dp
      ),
    contentAlignment = Alignment.Center
  ) {
    val maxBarWidth = if (isCompactHeight) 860.dp else 640.dp
    val effectiveBarWidth = if (maxWidth < maxBarWidth) maxWidth else maxBarWidth
    val tabCount = tabs.size.coerceAtLeast(1)
    val perTabWidth = (effectiveBarWidth - 12.dp) / tabCount
    val needsScroll = perTabWidth < 68.dp
    val useInlineLandscapeItem = isCompactHeight && !needsScroll && perTabWidth >= 96.dp
    val scrollState = rememberScrollState()

    val rowModifier = Modifier
      .widthIn(max = maxBarWidth)
      .fillMaxWidth()
      .liquidGlassSurface(
        shape = RoundedCornerShape(if (isCompactHeight) 24.dp else 32.dp),
        topColor = GlassSurfaceBright,
        bottomColor = GlassSurfaceMid,
        elevation = if (isCompactHeight) 8.dp else 10.dp
      )
      .then(if (needsScroll) Modifier.horizontalScroll(scrollState) else Modifier)
      .padding(
        horizontal = 6.dp,
        vertical = if (isCompactHeight) 4.dp else 6.dp
      )

    Row(
      modifier = rowModifier,
      horizontalArrangement = if (needsScroll) Arrangement.spacedBy(4.dp) else Arrangement.SpaceEvenly,
      verticalAlignment = Alignment.CenterVertically
    ) {
      tabs.forEach { tab ->
        val selected = currentTab == tab
        val tabLabel = dashboardTabLabel(tab, activeRole)
        val interactionSource = remember { MutableInteractionSource() }
        val tint by animateColorAsState(
          targetValue = if (selected) {
            MaterialTheme.colorScheme.primary
          } else {
            MaterialTheme.colorScheme.onSurfaceVariant
          },
          animationSpec = tween(durationMillis = 180),
          label = "navTint"
        )

        val itemHeight = if (isCompactHeight) 48.dp else 52.dp
        val itemShape = RoundedCornerShape(if (isCompactHeight) 18.dp else 24.dp)
        val sizeMod = if (needsScroll) {
          Modifier
            .widthIn(min = 68.dp)
            .heightIn(min = itemHeight)
        } else {
          Modifier
            .weight(1f)
            .heightIn(min = itemHeight)
        }

        val itemModifier = if (selected) {
          sizeMod.liquidGlassSurface(
            shape = itemShape,
            topColor = GlassSurfaceBright,
            bottomColor = GlassSurfaceSoft,
            tintColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f),
            elevation = 0.dp,
            borderWidth = 1.dp
          )
        } else {
          sizeMod.clip(itemShape)
        }

        val clickableMod = itemModifier
          .clickable(
            interactionSource = interactionSource,
            indication = ripple(bounded = true),
            onClick = { onSelectTab(tab) }
          )
          .testTag(tab.testTag)

        if (useInlineLandscapeItem) {
          Row(
            modifier = clickableMod.padding(horizontal = 6.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Icon(
              imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
              contentDescription = tabLabel,
              tint = tint,
              modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(5.dp))
            Text(
              text = tabLabel,
              style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 11.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
              ),
              color = tint,
              maxLines = 1,
              softWrap = false,
              overflow = TextOverflow.Ellipsis
            )
          }
        } else {
          Column(
            modifier = clickableMod.padding(
              vertical = if (isCompactHeight) 4.dp else 6.dp,
              horizontal = 2.dp
            ),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
          ) {
            Icon(
              imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
              contentDescription = tabLabel,
              tint = tint,
              modifier = Modifier.size(if (isCompactHeight) 19.dp else 21.dp)
            )
            Spacer(modifier = Modifier.height(if (isCompactHeight) 1.dp else 2.dp))
            Text(
              text = tabLabel,
              style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 10.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
              ),
              color = tint,
              textAlign = TextAlign.Center,
              maxLines = 2,
              overflow = TextOverflow.Ellipsis
            )
          }
        }
      }
    }
  }
}

private fun roleBadgeStyle(role: UserRole): Triple<Color, Color, Color> = when (role) {
  UserRole.ADMIN -> Triple(SignalCrimsonContainer.copy(alpha = 0.80f), SignalCrimsonOnContainer, SignalCrimson)
  UserRole.SSE -> Triple(RailwayCobalt.copy(alpha = 0.16f), RailwayCobalt, RailwayCobalt)
  UserRole.SUPERVISOR -> Triple(DepotTealContainer.copy(alpha = 0.80f), DepotTealOnContainer, DepotTeal)
  UserRole.JE -> Triple(SafetyAmberContainer.copy(alpha = 0.72f), SafetyAmberOnContainer, SafetyAmber)
  UserRole.SHIFT_INCHARGE -> Triple(DepotTealContainer.copy(alpha = 0.60f), DepotTealOnContainer, DepotTeal)
  UserRole.STOREMATE -> Triple(SafetyAmberContainer.copy(alpha = 0.85f), SafetyAmberOnContainer, SafetyAmber)
  UserRole.TECHNICIAN -> Triple(SignalEmeraldContainer.copy(alpha = 0.80f), SignalEmeraldOnContainer, SignalEmerald)
}
