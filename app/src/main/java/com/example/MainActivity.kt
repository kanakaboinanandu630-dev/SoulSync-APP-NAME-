package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.crypto.AppLockManager
import com.example.crypto.CryptoVault
import com.example.crypto.LocalSecurityPreferences
import com.example.data.ForeverUsRepository
import com.example.data.UiState
import com.example.data.authStateFlow
import com.example.data.signOutUser
import com.example.ui.ForeverUsViewModel
import com.example.ui.screens.AppLockScreen
import com.example.ui.screens.AuthScreen
import com.example.ui.screens.CallsScreen
import com.example.ui.screens.HomeSanctuaryScreen
import com.example.ui.screens.MemoriesAndNotesScreen
import com.example.ui.screens.PrivateChatScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.AppThemePalette
import com.example.ui.theme.ForeverUsTheme
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.PersistentCacheSettings
import kotlinx.coroutines.launch

private object ForeverUsRoutes {
    const val HOME = "sanctuary_home"
    const val CHAT = "private_chat"
    const val CALLS = "private_calls"
    const val MEMORIES = "memories_notes"
    const val SETTINGS = "settings_security"
}

private data class NavDestination(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
)

private val TopLevelDestinations = listOf(
    NavDestination(ForeverUsRoutes.HOME, "Home", Icons.Filled.Home, Icons.Outlined.Home, "nav_sanctuary"),
    NavDestination(ForeverUsRoutes.CHAT, "Chat", Icons.Filled.ChatBubble, Icons.Outlined.ChatBubbleOutline, "nav_chat"),
    NavDestination(ForeverUsRoutes.CALLS, "Calls", Icons.Filled.Call, Icons.Outlined.Call, "nav_calls"),
    NavDestination(ForeverUsRoutes.MEMORIES, "Memories", Icons.Filled.Favorite, Icons.Outlined.FavoriteBorder, "nav_memories"),
    NavDestination(ForeverUsRoutes.SETTINGS, "Settings", Icons.Filled.Settings, Icons.Outlined.Settings, "nav_settings")
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ForeverUsAppRoot()
        }
    }
}

@Composable
fun ForeverUsAppRoot() {
    val context = LocalContext.current
    val localPrefs by remember(context) {
        AppLockManager.observePreferences(context.applicationContext)
    }.collectAsStateWithLifecycle(initialValue = LocalSecurityPreferences())

    val currentUser by remember { Firebase.auth.authStateFlow() }
        .collectAsStateWithLifecycle(initialValue = Firebase.auth.currentUser)

    var isVaultUnlocked by rememberSaveable { mutableStateOf(false) }

    ForeverUsTheme(
        palette = AppThemePalette.fromKey(localPrefs.themePalette),
        darkTheme = localPrefs.darkMode
    ) {
        val user = currentUser
        if (user == null) {
            isVaultUnlocked = false
            AuthScreen(onAuthSuccess = { })
        } else if (localPrefs.pinEnabled && !isVaultUnlocked) {
            AppLockScreen(
                biometricEnabled = localPrefs.biometricEnabled,
                onVerifyPin = { candidate -> AppLockManager.verifyPin(context.applicationContext, candidate) },
                onUnlocked = { isVaultUnlocked = true }
            )
        } else {
            val uid = user.uid
            key(uid) {
                AuthenticatedForeverUsShell(
                    currentUserId = uid,
                    displayName = user.displayName ?: "Forever Partner",
                    email = user.email,
                    localPrefs = localPrefs
                )
            }
        }
    }
}

@Composable
private fun AuthenticatedForeverUsShell(
    currentUserId: String,
    displayName: String,
    email: String?,
    localPrefs: LocalSecurityPreferences
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }
    val snackbarHostState = remember { SnackbarHostState() }
    val navController = rememberNavController()

    val viewModel: ForeverUsViewModel = viewModel(
        key = currentUserId,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY]) {
                    "APPLICATION_KEY missing from CreationExtras"
                }
                val databaseId = app.getString(R.string.firestore_database_id)
                val db = FirebaseFirestore.getInstance(databaseId)
                runCatching {
                    db.firestoreSettings = FirebaseFirestoreSettings.Builder()
                        .setLocalCacheSettings(PersistentCacheSettings.newBuilder().build())
                        .build()
                }
                ForeverUsViewModel(
                    repository = ForeverUsRepository(db),
                    cryptoVault = CryptoVault(app.applicationContext),
                    currentUserId = currentUserId,
                    initialDisplayName = displayName,
                    initialEmail = email
                )
            }
        }
    )

    val session by viewModel.sessionState.collectAsStateWithLifecycle()
    val messagesUi by viewModel.messagesState.collectAsStateWithLifecycle()
    val mediaUi by viewModel.mediaState.collectAsStateWithLifecycle()
    val notesUi by viewModel.loveNotesState.collectAsStateWithLifecycle()
    val callsUi by viewModel.callsState.collectAsStateWithLifecycle()
    val notificationsUi by viewModel.notificationsState.collectAsStateWithLifecycle()
    val bannerMessage by viewModel.bannerMessage.collectAsStateWithLifecycle()
    val isBusy by viewModel.isActionBusy.collectAsStateWithLifecycle()
    val exportedJson by viewModel.exportedJson.collectAsStateWithLifecycle()
    val savedChatPhotoIds by viewModel.savedChatPhotoIds.collectAsStateWithLifecycle()

    val messages = (messagesUi as? UiState.Success)?.data ?: emptyList()
    val mediaItems = (mediaUi as? UiState.Success)?.data ?: emptyList()
    val loveNotes = (notesUi as? UiState.Success)?.data ?: emptyList()
    val calls = (callsUi as? UiState.Success)?.data ?: emptyList()
    val notifications = (notificationsUi as? UiState.Success)?.data ?: emptyList()

    val unreadNotifications by remember(notifications) {
        derivedStateOf { notifications.count { !it.read } }
    }
    val hasIncomingCall by remember(calls, currentUserId) {
        derivedStateOf { calls.any { it.status == "ringing" && it.calleeId == currentUserId } }
    }

    LaunchedEffect(bannerMessage) {
        val msg = bannerMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(msg)
        viewModel.clearBanner()
    }

    LaunchedEffect(exportedJson) {
        val json = exportedJson ?: return@LaunchedEffect
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_SUBJECT, "SoulSync Personal Data Export")
            putExtra(Intent.EXTRA_TEXT, json)
        }
        context.startActivity(Intent.createChooser(shareIntent, "Export SoulSync Data"))
        viewModel.clearExportedJson()
    }

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: ForeverUsRoutes.HOME

    fun navigateTopLevel(route: String) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useNavRail = maxWidth >= 600.dp

        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            bottomBar = {
                if (!useNavRail) {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                        tonalElevation = 0.dp
                    ) {
                        TopLevelDestinations.forEach { dest ->
                            val selected = currentRoute == dest.route
                            NavigationBarItem(
                                selected = selected,
                                onClick = { navigateTopLevel(dest.route) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = MaterialTheme.colorScheme.primary,
                                    selectedTextColor = MaterialTheme.colorScheme.primary,
                                    indicatorColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                                icon = {
                                    BadgedBox(
                                        badge = {
                                            if (dest.route == ForeverUsRoutes.HOME && unreadNotifications > 0) {
                                                Badge { Text(unreadNotifications.coerceAtMost(9).toString()) }
                                            } else if (dest.route == ForeverUsRoutes.CALLS && hasIncomingCall) {
                                                Badge()
                                            }
                                        }
                                    ) {
                                        Icon(
                                            imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                            contentDescription = dest.label
                                        )
                                    }
                                },
                                label = { Text(dest.label) },
                                modifier = Modifier.testTag(dest.testTag)
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useNavRail) {
                    NavigationRail {
                        TopLevelDestinations.forEach { dest ->
                            val selected = currentRoute == dest.route
                            NavigationRailItem(
                                selected = selected,
                                onClick = { navigateTopLevel(dest.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                        contentDescription = dest.label
                                    )
                                },
                                label = { Text(dest.label) },
                                modifier = Modifier.testTag(dest.testTag)
                            )
                        }
                    }
                }

                Box(modifier = Modifier.weight(1f).fillMaxSize()) {
                    NavHost(
                        navController = navController,
                        startDestination = ForeverUsRoutes.HOME,
                        enterTransition = { fadeIn(animationSpec = tween(150)) },
                        exitTransition = { fadeOut(animationSpec = tween(120)) },
                        popEnterTransition = { fadeIn(animationSpec = tween(150)) },
                        popExitTransition = { fadeOut(animationSpec = tween(120)) }
                    ) {
                        composable(ForeverUsRoutes.HOME) {
                            HomeSanctuaryScreen(
                                session = session,
                                loveNotes = loveNotes,
                                mediaItems = mediaItems,
                                notifications = notifications,
                                isBusy = isBusy,
                                onCreateInvite = viewModel::createInviteCode,
                                onJoinInvite = viewModel::joinWithInviteCode,
                                onApproveConnection = viewModel::approvePartnerConnection,
                                onDisconnect = viewModel::disconnectPartner,
                                onSaveMilestones = viewModel::saveAnniversaryAndBirthday,
                                onOpenChat = { navigateTopLevel(ForeverUsRoutes.CHAT) },
                                onOpenCalls = { navigateTopLevel(ForeverUsRoutes.CALLS) },
                                onStartAudioCall = {
                                    viewModel.startCall("audio")
                                    navigateTopLevel(ForeverUsRoutes.CALLS)
                                },
                                onStartVideoCall = {
                                    viewModel.startCall("video")
                                    navigateTopLevel(ForeverUsRoutes.CALLS)
                                },
                                onOpenMemories = { navigateTopLevel(ForeverUsRoutes.MEMORIES) },
                                onMarkNotificationRead = viewModel::markNotificationRead
                            )
                        }

                        composable(ForeverUsRoutes.CHAT) {
                            PrivateChatScreen(
                                session = session,
                                messages = messages,
                                mediaItems = mediaItems,
                                savedChatPhotoIds = savedChatPhotoIds,
                                onSendText = viewModel::sendTextMessage,
                                onSendMedia = viewModel::sendEncryptedAttachmentMessage,
                                onSavePhotoToMemories = viewModel::saveChatPhotoToMemories,
                                onMarkRead = viewModel::markPartnerMessagesRead,
                                onDeleteMessage = viewModel::deleteMessage,
                                onSetTyping = viewModel::setTyping,
                                onSetDisappearingTimer = viewModel::setDisappearingTimer,
                                onStartAudioCall = {
                                    viewModel.startCall("audio")
                                    navigateTopLevel(ForeverUsRoutes.CALLS)
                                },
                                onStartVideoCall = {
                                    viewModel.startCall("video")
                                    navigateTopLevel(ForeverUsRoutes.CALLS)
                                },
                                onGoToPairing = { navigateTopLevel(ForeverUsRoutes.HOME) },
                                onShowError = { msg -> scope.launch { snackbarHostState.showSnackbar(msg) } }
                            )
                        }

                        composable(ForeverUsRoutes.CALLS) {
                            CallsScreen(
                                session = session,
                                calls = calls,
                                onStartCall = viewModel::startCall,
                                onAnswerCall = viewModel::answerCall,
                                onEndOrDeclineCall = viewModel::endOrDeclineCall,
                                onToggleMute = viewModel::toggleCallMute,
                                onGoToPairing = { navigateTopLevel(ForeverUsRoutes.HOME) },
                                onShowMessage = { msg -> scope.launch { snackbarHostState.showSnackbar(msg) } }
                            )
                        }

                        composable(ForeverUsRoutes.MEMORIES) {
                            MemoriesAndNotesScreen(
                                session = session,
                                loveNotes = loveNotes,
                                mediaItems = mediaItems,
                                savedChatPhotoIds = savedChatPhotoIds,
                                onAddLoveNote = viewModel::addLoveNote,
                                onTogglePinNote = viewModel::togglePinNote,
                                onDeleteNote = viewModel::deleteLoveNote,
                                onUploadMemoryPhoto = viewModel::uploadMemoryPhoto,
                                onSaveChatPhotoToMemories = viewModel::saveChatPhotoToMemories,
                                onDeleteMedia = viewModel::deleteMediaItem,
                                onOpenChat = { navigateTopLevel(ForeverUsRoutes.CHAT) },
                                onGoToPairing = { navigateTopLevel(ForeverUsRoutes.HOME) },
                                onShowError = { msg -> scope.launch { snackbarHostState.showSnackbar(msg) } }
                            )
                        }

                        composable(ForeverUsRoutes.SETTINGS) {
                            SettingsScreen(
                                session = session,
                                localPrefs = localPrefs,
                                biometricAvailable = viewModel.isBiometricHardwareAvailable(),
                                isBusy = isBusy,
                                onUpdateProfile = viewModel::updateProfile,
                                onUpdateTheme = viewModel::updateTheme,
                                onUpdatePrivacyToggle = viewModel::updatePrivacyToggle,
                                onEnablePin = viewModel::enablePinLock,
                                onDisablePin = viewModel::disablePinLock,
                                onToggleBiometric = viewModel::toggleBiometricLock,
                                onDisconnectPartner = viewModel::disconnectPartner,
                                onSubmitReport = viewModel::submitAbuseReport,
                                onExportPersonalInfo = viewModel::generateDataExport,
                                onDeleteAccount = {
                                    signOutUser(
                                        credentialManager = credentialManager,
                                        scope = scope,
                                        beforeSignOut = { viewModel.deleteAccountCompletely() }
                                    )
                                },
                                onSignOut = {
                                    signOutUser(
                                        credentialManager = credentialManager,
                                        scope = scope
                                    )
                                },
                                onShowError = { msg -> scope.launch { snackbarHostState.showSnackbar(msg) } }
                            )
                        }
                    }
                }
            }
        }
    }
}
