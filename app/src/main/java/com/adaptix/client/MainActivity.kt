package com.adaptix.client

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat as CoreContextCompat
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.fragment.app.FragmentActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import com.adaptix.client.ui.navigation.Screen
import com.adaptix.client.ui.screens.*
import com.adaptix.client.ui.theme.*
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.adaptix.client.viewmodel.MainViewModel

class MainActivity : FragmentActivity() {

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* granted or not, we handle gracefully */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestNotificationPermission()
        setContent {
            AdaptixApp()
        }
    }

    private fun requestNotificationPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdaptixApp(viewModel: MainViewModel = viewModel()) {
    val state by viewModel.state.collectAsState()
    var currentScreen by remember { mutableStateOf(Screen.Dashboard) }

    // Theme state — persisted in prefs
    var currentTheme by remember {
        val saved = viewModel.prefs.themeName
        mutableStateOf(AppTheme.entries.find { it.name == saved } ?: AppTheme.ADAPTIX_DARK)
    }
    var showThemeMenu by remember { mutableStateOf(false) }

    val isConsoleScreen = currentScreen == Screen.Console && state.selectedAgentId != null

    // Observable state for prefs toggles (SharedPreferences aren't Compose-reactive)
    var biometricEnabled by remember { mutableStateOf(viewModel.prefs.biometricEnabled) }
    var autoLogin by remember { mutableStateOf(viewModel.prefs.autoLogin) }

    // Biometric lock gate
    var biometricPassed by remember { mutableStateOf(!viewModel.prefs.biometricEnabled) }
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        if (biometricEnabled && !biometricPassed) {
            val biometricManager = BiometricManager.from(context)
            if (biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.BIOMETRIC_WEAK) != BiometricManager.BIOMETRIC_SUCCESS) {
                biometricPassed = true // No biometric available, skip
            }
        }
    }

    // Reconnect WebSocket when app resumes from background
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.ensureConnected()
        // Re-prompt biometric when resuming if enabled
        if (biometricEnabled && !biometricPassed) {
            val activity = context as? FragmentActivity ?: return@LifecycleEventEffect
            val executor = CoreContextCompat.getMainExecutor(context)
            val prompt = BiometricPrompt(activity, executor, object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    biometricPassed = true
                }
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    if (errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON ||
                        errorCode == BiometricPrompt.ERROR_USER_CANCELED) {
                        // User cancelled — stay locked
                    }
                }
            })
            val promptInfo = BiometricPrompt.PromptInfo.Builder()
                .setTitle("Adaptix C2")
                .setSubtitle("Authenticate to unlock")
                .setNegativeButtonText("Cancel")
                .build()
            prompt.authenticate(promptInfo)
        }
    }

    // Auto-login after biometric passes
    LaunchedEffect(biometricPassed) {
        if (biometricPassed && viewModel.prefs.autoLogin) {
            viewModel.prefs.lastProfile?.let { profile ->
                if (!state.isLoggedIn && !state.isConnecting) {
                    viewModel.login(profile)
                }
            }
        }
    }

    // Track whether auto-login is in progress (biometric passed, connecting)
    val autoLoginInProgress = biometricPassed && autoLogin && state.isConnecting && !state.isLoggedIn

    AdaptixTheme(appTheme = currentTheme) {
        // Root screen key for animated transitions
        val rootScreen = when {
            !biometricPassed -> "lock"
            autoLoginInProgress -> "connecting"
            !state.isLoggedIn -> "login"
            isConsoleScreen -> "console"
            else -> "main"
        }

        AnimatedContent(
            targetState = rootScreen,
            transitionSpec = {
                when {
                    // Lock → Connecting/Login: scale+fade
                    initialState == "lock" ->
                        (fadeIn(tween(400)) + scaleIn(tween(400), initialScale = 0.95f)) togetherWith
                            fadeOut(tween(300))
                    // Connecting → Main: crossfade
                    initialState == "connecting" ->
                        fadeIn(tween(400)) togetherWith fadeOut(tween(300))
                    // Login → Main: slide right
                    initialState == "login" ->
                        (slideInHorizontally(tween(350)) { it / 3 } + fadeIn(tween(350))) togetherWith
                            (slideOutHorizontally(tween(300)) { -it / 3 } + fadeOut(tween(300)))
                    // Main → Login (logout): slide left
                    targetState == "login" ->
                        (slideInHorizontally(tween(350)) { -it / 3 } + fadeIn(tween(350))) togetherWith
                            (slideOutHorizontally(tween(300)) { it / 3 } + fadeOut(tween(300)))
                    else -> fadeIn(tween(250)) togetherWith fadeOut(tween(250))
                }
            },
            modifier = Modifier.fillMaxSize(),
            label = "root"
        ) { screen ->
            when (screen) {
                "lock" -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(SurfaceBlack),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.Fingerprint,
                                null,
                                tint = Crimson,
                                modifier = Modifier.size(64.dp)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("Locked", color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Authenticate to continue", color = TextMuted, fontSize = 13.sp)
                            Spacer(modifier = Modifier.height(24.dp))
                            Button(
                                onClick = {
                                    val activity = context as? FragmentActivity ?: return@Button
                                    val executor = CoreContextCompat.getMainExecutor(context)
                                    val prompt = BiometricPrompt(activity, executor, object : BiometricPrompt.AuthenticationCallback() {
                                        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                                            biometricPassed = true
                                        }
                                    })
                                    val promptInfo = BiometricPrompt.PromptInfo.Builder()
                                        .setTitle("Adaptix C2")
                                        .setSubtitle("Authenticate to unlock")
                                        .setNegativeButtonText("Cancel")
                                        .build()
                                    prompt.authenticate(promptInfo)
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Crimson)
                            ) {
                                Text("Unlock", color = TextPrimary)
                            }
                        }
                    }
                }
                "connecting" -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(SurfaceBlack),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(
                                color = Crimson,
                                modifier = Modifier.size(48.dp),
                                strokeWidth = 3.dp
                            )
                            Spacer(modifier = Modifier.height(20.dp))
                            Text("Connecting...", color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                viewModel.prefs.lastProfile?.let { "${it.host}:${it.port}" } ?: "",
                                color = TextMuted,
                                fontSize = 12.sp
                            )
                            Spacer(modifier = Modifier.height(24.dp))
                            TextButton(onClick = {
                                viewModel.logout()
                                autoLogin = false
                                viewModel.prefs.autoLogin = false
                            }) {
                                Text("Cancel", color = TextMuted, fontSize = 13.sp)
                            }
                        }
                    }
                }
                "login" -> {
                    LoginScreen(
                        state = state,
                        savedProfiles = viewModel.prefs.savedProfiles,
                        autoLogin = autoLogin,
                        onAutoLoginChange = { autoLogin = it; viewModel.prefs.autoLogin = it },
                        onLogin = { viewModel.login(it) },
                        onDeleteProfile = { viewModel.prefs.removeProfile(it) }
                    )
                }
                "console" -> {
                    val agent = state.agents.find { it.id == state.selectedAgentId }
                    ConsoleScreen(
                        agent = agent,
                        tasks = state.agentTasks,
                        onExecute = { cmd ->
                            state.selectedAgentId?.let { viewModel.executeCommand(it, cmd) }
                        },
                        onBack = {
                            viewModel.selectAgent(null)
                            currentScreen = Screen.Agents
                        },
                        onClear = { viewModel.clearConsole() },
                        registeredCommands = state.registeredCommands[agent?.name] ?: emptyList(),
                        favoriteCommands = state.favoriteCommands,
                        onAddFavorite = { viewModel.addFavoriteCommand(it) },
                        onRemoveFavorite = { viewModel.removeFavoriteCommand(it) }
                    )
                }
                else -> {
                    Scaffold(
                        topBar = {
                            Column {
                                CenterAlignedTopAppBar(
                                    title = {
                                        Text(
                                            currentScreen.title,
                                            color = TextPrimary,
                                            fontSize = 16.sp
                                        )
                                    },
                                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                                        containerColor = SurfaceDark
                                    ),
                                    navigationIcon = {
                                        if (currentScreen != Screen.Dashboard) {
                                            IconButton(onClick = { currentScreen = Screen.Dashboard }) {
                                                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = TextPrimary)
                                            }
                                        }
                                    },
                                    actions = {
                                        // Theme picker
                                        Box {
                                            IconButton(onClick = { showThemeMenu = true }) {
                                                Icon(Icons.Default.Palette, "Theme", tint = TextMuted)
                                            }
                                            DropdownMenu(
                                                expanded = showThemeMenu,
                                                onDismissRequest = { showThemeMenu = false }
                                            ) {
                                                AppTheme.entries.forEach { theme ->
                                                    DropdownMenuItem(
                                                        text = {
                                                            Text(
                                                                theme.displayName,
                                                                color = if (theme == currentTheme) theme.palette.primary else TextPrimary,
                                                                fontSize = 13.sp
                                                            )
                                                        },
                                                        onClick = {
                                                            currentTheme = theme
                                                            viewModel.prefs.themeName = theme.name
                                                            showThemeMenu = false
                                                        },
                                                        leadingIcon = {
                                                            Box(
                                                                modifier = Modifier
                                                                    .size(12.dp)
                                                                    .background(
                                                                        theme.palette.primary,
                                                                        shape = CircleShape
                                                                    )
                                                            )
                                                        }
                                                    )
                                                }
                                            }
                                        }
                                        // Biometric lock toggle
                                        IconButton(onClick = {
                                            biometricEnabled = !biometricEnabled
                                            viewModel.prefs.biometricEnabled = biometricEnabled
                                        }) {
                                            Icon(
                                                if (biometricEnabled) Icons.Default.Lock else Icons.Default.LockOpen,
                                                "Biometric",
                                                tint = if (biometricEnabled) Crimson else TextMuted,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                        // Logout
                                        IconButton(onClick = { viewModel.logout() }) {
                                            Text("Exit", color = TextMuted, fontSize = 11.sp)
                                        }
                                    }
                                )
                                HorizontalDivider(thickness = 1.dp, color = DividerColor)
                            }
                        },
                        containerColor = SurfaceBlack
                    ) { padding ->
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(padding)
                                .imePadding()
                                .background(SurfaceBlack)
                        ) {
                            // Offline banner
                            if (!state.wsConnected) {
                                Surface(
                                    color = RedError.copy(alpha = 0.15f),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            Icons.Default.CloudOff,
                                            null,
                                            tint = RedError,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            "Disconnected — reconnecting...",
                                            color = RedError,
                                            fontSize = 11.sp
                                        )
                                        Spacer(modifier = Modifier.weight(1f))
                                        TextButton(
                                            onClick = { viewModel.ensureConnected() },
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                                            modifier = Modifier.height(28.dp)
                                        ) {
                                            Text("Retry", color = RedError, fontSize = 11.sp)
                                        }
                                    }
                                }
                            }
                            AnimatedContent(
                                targetState = currentScreen,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(SurfaceBlack),
                                transitionSpec = {
                                    val forward = targetState.ordinal > initialState.ordinal
                                    if (forward) {
                                        (slideInHorizontally { it / 4 } + fadeIn()) togetherWith
                                            (slideOutHorizontally { -it / 4 } + fadeOut())
                                    } else {
                                        (slideInHorizontally { -it / 4 } + fadeIn()) togetherWith
                                            (slideOutHorizontally { it / 4 } + fadeOut())
                                    }
                                },
                                label = "screen"
                            ) { navScreen ->
                                when (navScreen) {
                                    Screen.Dashboard -> DashboardScreen(
                                        state = state,
                                        onNavigate = { currentScreen = it },
                                        onAgentClick = { agentId ->
                                            viewModel.selectAgent(agentId)
                                            currentScreen = Screen.Console
                                        }
                                    )
                                    Screen.Agents -> AgentsScreen(
                                        agents = state.agents,
                                        onAgentClick = { agentId ->
                                            viewModel.selectAgent(agentId)
                                            currentScreen = Screen.Console
                                        },
                                        onRefresh = { viewModel.refreshAgents() },
                                        onRemove = { viewModel.removeAgents(it) },
                                        sortBy = viewModel.prefs.agentSortBy,
                                        onSortChange = { viewModel.prefs.agentSortBy = it }
                                    )
                                    Screen.Listeners -> ListenersScreen(
                                        listeners = state.listeners,
                                        listenerTypes = state.registeredListenerTypes,
                                        listenerProfiles = viewModel.prefs.listenerProfiles,
                                        onCreateListener = { name, type, config -> viewModel.createListener(name, type, config) },
                                        onStopListener = { name, type -> viewModel.stopListener(name, type) },
                                        onGenerateFromListener = { listenerName ->
                                            viewModel.setGenerateListener(listenerName)
                                            currentScreen = Screen.Generate
                                        },
                                        onSaveListenerProfile = { viewModel.prefs.addListenerProfile(it) },
                                        onDeleteListenerProfile = { viewModel.prefs.removeListenerProfile(it) },
                                        onRefresh = { viewModel.refreshListeners() }
                                    )
                                    Screen.Downloads -> DownloadsScreen(
                                        downloads = state.downloads,
                                        onRefresh = { viewModel.refreshDownloads() }
                                    )
                                    Screen.Screenshots -> ScreenshotsScreen(
                                        screenshots = state.screenshots,
                                        onFetchImage = { screenId, cb -> viewModel.fetchScreenshotImage(screenId, cb) },
                                        onRemove = { viewModel.removeScreenshots(it) },
                                        onRefresh = { viewModel.refreshScreenshots() }
                                    )
                                    Screen.Credentials -> CredentialsScreen(
                                        credentials = state.credentials,
                                        onRefresh = { viewModel.refreshCredentials() }
                                    )
                                    Screen.Tunnels -> TunnelsScreen(
                                        tunnels = state.tunnels,
                                        onStopTunnel = { viewModel.stopTunnel(it) },
                                        onRefresh = { viewModel.refreshTunnels() }
                                    )
                                    Screen.Chat -> ChatScreen(
                                        messages = state.chatMessages,
                                        currentUser = state.serverProfile?.username ?: "",
                                        onSend = { viewModel.sendChat(it) },
                                        onClear = { viewModel.clearChat() }
                                    )
                                    Screen.Generate -> GenerateScreen(
                                        agentTypes = state.registeredAgentTypes,
                                        listeners = state.listeners,
                                        isGenerating = state.isGenerating,
                                        generateError = state.generateError,
                                        generateSuccess = state.generateSuccess,
                                        buildLogs = state.buildLogs,
                                        buildProfiles = viewModel.prefs.buildProfiles,
                                        preSelectedListener = state.generatePreSelectedListener,
                                        onGenerate = { agent, listeners, config, profileName -> viewModel.generateAgent(agent, listeners, config, profileName) },
                                        onDeleteProfile = { viewModel.prefs.removeBuildProfile(it) },
                                        onClearStatus = { viewModel.clearGenerateStatus() },
                                        onBack = {
                                            viewModel.setGenerateListener(null)
                                            currentScreen = Screen.Listeners
                                        }
                                    )
                                    Screen.Console -> {} // Handled above
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
