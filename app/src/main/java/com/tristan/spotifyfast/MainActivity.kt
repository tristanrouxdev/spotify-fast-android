package com.tristan.spotifyfast

import android.app.Activity
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.tristan.spotifyfast.auth.PkceAuthManager
import com.tristan.spotifyfast.player.AppRemoteBridge
import com.tristan.spotifyfast.player.RepeatMode
import com.tristan.spotifyfast.ui.components.MiniPlayerBar
import com.tristan.spotifyfast.ui.components.ProfileAndAudioStatusSheet
import com.tristan.spotifyfast.ui.home.HomeScreen
import com.tristan.spotifyfast.ui.home.HomeViewModel
import com.tristan.spotifyfast.ui.icons.SpotifyIcons
import com.tristan.spotifyfast.ui.library.AudiobookDetailScreen
import com.tristan.spotifyfast.ui.library.CollectionDetailScreen
import com.tristan.spotifyfast.ui.library.LibraryScreen
import com.tristan.spotifyfast.ui.library.LibraryViewModel
import com.tristan.spotifyfast.ui.library.ShowDetailScreen
import com.tristan.spotifyfast.ui.nowplaying.ConnectDevicesBottomSheet
import com.tristan.spotifyfast.ui.nowplaying.NowPlayingSheet
import com.tristan.spotifyfast.ui.onboarding.OnboardingAuthScreen
import com.tristan.spotifyfast.ui.search.SearchScreen
import com.tristan.spotifyfast.ui.search.SearchViewModel
import com.tristan.spotifyfast.ui.theme.SpotifyFastTheme
import com.tristan.spotifyfast.ui.theme.SpotifyTokens
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val container = (application as SpotifyFastApplication).container

        setContent {
            SpotifyFastTheme {
                SpotifyFastRoot(
                    container = container,
                    onFirstFrameDrawn = { reportFullyDrawn() },
                )
            }
        }
    }
}

private enum class BottomTab(val route: String, val label: String, val icon: ImageVector) {
    HOME("home", "Accueil", SpotifyIcons.Home),
    SEARCH("search", "Rechercher", SpotifyIcons.Search),
    LIBRARY("library", "Bibliothèque", SpotifyIcons.Library),
}

@Composable
private fun SpotifyFastRoot(
    container: AppContainer,
    onFirstFrameDrawn: () -> Unit,
) {
    val isAuthenticated by container.tokenStore.isAuthenticatedFlow
        .collectAsStateWithLifecycle(initialValue = null)
    val isDemoMode by container.tokenStore.isDemoModeFlow
        .collectAsStateWithLifecycle(initialValue = false)
    val savedClientId by container.tokenStore.clientIdFlow
        .collectAsStateWithLifecycle(initialValue = "")
    val profileName by container.tokenStore.userProfileNameFlow
        .collectAsStateWithLifecycle(initialValue = "")
    val productTier by container.tokenStore.userProductFlow
        .collectAsStateWithLifecycle(initialValue = "")
    val githubRepo by container.tokenStore.githubRepoFlow
        .collectAsStateWithLifecycle(initialValue = com.tristan.spotifyfast.auth.EncryptedTokenStore.DEFAULT_GITHUB_REPO)

    val scope = rememberCoroutineScope()

    LaunchedEffect(isAuthenticated) {
        withFrameNanos { }
        onFirstFrameDrawn()
        if (isAuthenticated == true) {
            val bundle = container.tokenStore.loadTokenBundle()
            if (bundle != null && !bundle.isDemoMode) {
                container.playbackRouter.startPostFrameWarmup(bundle.clientId)
            }
            container.syncEngine.syncAll()
        }
    }

    if (isAuthenticated == false) {
        OnboardingAuthScreen(
            initialClientId = savedClientId,
            pkceAuthManager = container.pkceAuthManager,
            tokenStore = container.tokenStore,
            syncEngine = container.syncEngine,
            onAuthenticated = { },
        )
        return
    }

    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    val homeViewModel = remember { HomeViewModel(container.dao) }
    val searchViewModel = remember { SearchViewModel(container.dao, container.syncEngine) }
    val libraryViewModel = remember { LibraryViewModel(container.dao, container.syncEngine) }

    val playbackState by container.playbackRouter.playbackState.collectAsStateWithLifecycle()
    val devices by container.playbackRouter.connectedDevices.collectAsStateWithLifecycle()
    val queue by container.playbackRouter.queue.collectAsStateWithLifecycle()
    val isAppRemoteConnected by container.playbackRouter.isAppRemoteConnected.collectAsStateWithLifecycle()
    val lastAudioError by container.playbackRouter.lastErrorMessage.collectAsStateWithLifecycle()
    val needsSsoAuthorization by container.playbackRouter.needsSsoAuthorization.collectAsStateWithLifecycle()
    val updateStatus by container.appUpdateManager.status.collectAsStateWithLifecycle()

    val context = LocalContext.current
    var ssoFeedback by remember { mutableStateOf<String?>(null) }

    val ssoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val extraKeys = result.data?.extras?.keySet()?.joinToString(",").orEmpty()
        Log.i("SpotifyFast", "Native SSO resultCode=${result.resultCode}, extraKeys=[$extraKeys]")
        when (result.resultCode) {
            Activity.RESULT_OK -> {
                ssoFeedback = null
                Log.i("SpotifyFast", "Native SSO authorized, reconnecting App Remote")
                scope.launch { container.playbackRouter.reconnectSpotifyApp(savedClientId) }
            }
            -2 -> { // Spotify LoginActivity.RESULT_ERROR
                val rawErr = result.data?.getStringExtra("ERROR") ?: "Erreur autorisation Spotify"
                Log.e("SpotifyFast", "Native SSO error (-2): $rawErr")
                val sha1 = PkceAuthManager.readAppSigningSha1(context)
                ssoFeedback = if (
                    rawErr.contains("INVALID", ignoreCase = true) ||
                    rawErr.contains("APP_ID", ignoreCase = true) ||
                    rawErr.contains("AUTHENTICATION", ignoreCase = true)
                ) {
                    "Spotify App Remote a retourné : $rawErr.\n" +
                        "Pour App Remote, ajoutez dans Spotify Developer Dashboard :\n" +
                        "• Package Android : ${context.packageName}\n" +
                        "• Empreinte SHA-1 : $sha1\n" +
                        "Le mode Spotify Connect Web API prend automatiquement le relais sans SHA-1 si l'app Spotify a une session active."
                } else {
                    "Erreur autorisation Spotify ($rawErr)"
                }
            }
            Activity.RESULT_CANCELED -> {
                Log.w("SpotifyFast", "Native SSO cancelled (resultCode=0)")
                ssoFeedback = "Autorisation Spotify annulée ou bloquée (vérifiez Package=${context.packageName} / SHA-1 dans votre Dashboard Spotify)."
            }
        }
    }

    val launchNativeSsoOrReconnect: () -> Unit = {
        val currentErr = lastAudioError.orEmpty()
        val shouldLaunchSso = needsSsoAuthorization ||
            currentErr.contains("Explicit user authorization", ignoreCase = true) ||
            currentErr.contains("UserNotAuthorizedException", ignoreCase = true)

        if (shouldLaunchSso) {
            val ssoIntent = AppRemoteBridge.buildNativeSsoAuthIntent(
                context = context,
                clientId = savedClientId,
                redirectUri = container.pkceAuthManager.redirectUri(),
            )
            if (ssoIntent != null) {
                Log.i("SpotifyFast", "Launching foreground Spotify SSO intent (START_AUTH_FLOW)")
                ssoFeedback = null
                runCatching {
                    ssoLauncher.launch(ssoIntent)
                }.onFailure { err ->
                    Log.e("SpotifyFast", "Unable to start foreground SSO intent", err)
                    ssoFeedback = "Impossible d'ouvrir l'écran d'autorisation Spotify : ${err.message}"
                    scope.launch { container.playbackRouter.reconnectSpotifyApp(savedClientId) }
                }
            } else {
                Log.w("SpotifyFast", "Native START_AUTH_FLOW intent did not resolve, falling back to reconnectSpotifyApp")
                scope.launch { container.playbackRouter.reconnectSpotifyApp(savedClientId) }
            }
        } else {
            scope.launch { container.playbackRouter.reconnectSpotifyApp(savedClientId) }
        }
    }

    var showNowPlaying by remember { mutableStateOf(false) }
    var showDevicesSheet by remember { mutableStateOf(false) }
    var showProfileSheet by remember { mutableStateOf(false) }

    val userInitial = profileName.trim().firstOrNull()?.uppercase() ?: "S"

    val openUriRoute: (String, String) -> Unit = { uri, title ->
        val encodedUri = Uri.encode(uri)
        val encodedTitle = Uri.encode(title)
        when {
            uri.startsWith("spotify:show:") -> navController.navigate("show/$encodedUri/$encodedTitle")
            uri.startsWith("spotify:audiobook:") -> navController.navigate("audiobook/$encodedUri/$encodedTitle")
            else -> navController.navigate("collection/$encodedUri/$encodedTitle")
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = SpotifyTokens.Background,
            bottomBar = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, SpotifyTokens.Background.copy(alpha = 0.96f), SpotifyTokens.Background),
                            ),
                        )
                        .navigationBarsPadding(),
                ) {
                    if (!lastAudioError.isNullOrBlank()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(SpotifyTokens.ErrorRed.copy(alpha = 0.92f))
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = lastAudioError.orEmpty(),
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                color = Color.White,
                                modifier = Modifier.weight(1f),
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            if (needsSsoAuthorization) {
                                TextButton(onClick = launchNativeSsoOrReconnect) {
                                    Text("Autoriser", color = Color.White, fontWeight = FontWeight.Bold)
                                }
                            }
                            TextButton(
                                onClick = {
                                    container.playbackRouter.clearError()
                                    showProfileSheet = true
                                },
                            ) {
                                Text("Diagnostic", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                            TextButton(onClick = { container.playbackRouter.clearError() }) {
                                Text("OK", color = Color.White)
                            }
                        }
                    }

                    MiniPlayerBar(
                        state = playbackState,
                        onExpandNowPlaying = { showNowPlaying = true },
                        onTogglePlayPause = { scope.launch { container.playbackRouter.togglePlayPause() } },
                        onToggleLike = { scope.launch { container.playbackRouter.toggleLikeCurrentTrack() } },
                        onOpenDevices = {
                            scope.launch { container.playbackRouter.refreshDevices() }
                            showDevicesSheet = true
                        },
                        onSkipNext = { scope.launch { container.playbackRouter.skipNext() } },
                        onSkipPrevious = { scope.launch { container.playbackRouter.skipPrevious() } },
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(SpotifyTokens.BottomNavHeight)
                            .padding(top = 4.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        BottomTab.entries.forEach { tab ->
                            val selected = currentRoute == tab.route
                            val tint = if (selected) SpotifyTokens.TextPrimary else SpotifyTokens.TextSecondary
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable {
                                        navController.navigate(tab.route) {
                                            popUpTo(BottomTab.HOME.route) { saveState = true }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    }
                                    .padding(vertical = 6.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Icon(
                                    imageVector = tab.icon,
                                    contentDescription = tab.label,
                                    tint = tint,
                                    modifier = Modifier.size(22.dp),
                                )
                                Text(
                                    text = tab.label,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = tint,
                                )
                            }
                        }
                    }
                }
            },
        ) { innerPadding ->
            NavHost(
                navController = navController,
                startDestination = BottomTab.HOME.route,
                modifier = Modifier.fillMaxSize(),
            ) {
                composable(BottomTab.HOME.route) {
                    HomeScreen(
                        viewModel = homeViewModel,
                        playbackState = playbackState,
                        userInitial = userInitial,
                        onOpenUri = openUriRoute,
                        onPlayTrack = { uri, ctx ->
                            scope.launch { container.playbackRouter.playUri(uri, ctx) }
                        },
                        onOpenProfile = { showProfileSheet = true },
                        bottomPadding = innerPadding,
                    )
                }

                composable(BottomTab.SEARCH.route) {
                    SearchScreen(
                        viewModel = searchViewModel,
                        onPlayUri = { uri ->
                            scope.launch { container.playbackRouter.playUri(uri) }
                        },
                        onOpenUri = openUriRoute,
                        bottomPadding = innerPadding,
                    )
                }

                composable(BottomTab.LIBRARY.route) {
                    LibraryScreen(
                        viewModel = libraryViewModel,
                        onOpenItem = { item -> openUriRoute(item.uri, item.title) },
                        bottomPadding = innerPadding,
                    )
                }

                composable(
                    route = "collection/{uri}/{title}",
                    arguments = listOf(
                        navArgument("uri") { type = NavType.StringType },
                        navArgument("title") { type = NavType.StringType },
                    ),
                ) { entry ->
                    val uri = Uri.decode(entry.arguments?.getString("uri").orEmpty())
                    val title = Uri.decode(entry.arguments?.getString("title").orEmpty())
                    CollectionDetailScreen(
                        contextUri = uri,
                        title = title,
                        viewModel = libraryViewModel,
                        playbackState = playbackState,
                        onBack = { navController.popBackStack() },
                        onPlayTrack = { trackUri, ctxUri, index ->
                            scope.launch { container.playbackRouter.playUri(trackUri, ctxUri, index) }
                        },
                        onToggleContextPlayPause = {
                            scope.launch { container.playbackRouter.toggleContextPlayback(uri) }
                        },
                        onShufflePlay = {
                            scope.launch { container.playbackRouter.shufflePlayContext(uri) }
                        },
                        onToggleSaveTrack = { trackUri, currentlyLiked ->
                            scope.launch { container.syncEngine.toggleLibraryItem(trackUri, currentlyLiked) }
                        },
                        onEnqueueTrack = { trackUri ->
                            scope.launch { container.playbackRouter.enqueue(trackUri) }
                        },
                        bottomPadding = innerPadding,
                    )
                }

                composable(
                    route = "show/{uri}/{title}",
                    arguments = listOf(
                        navArgument("uri") { type = NavType.StringType },
                        navArgument("title") { type = NavType.StringType },
                    ),
                ) { entry ->
                    val uri = Uri.decode(entry.arguments?.getString("uri").orEmpty())
                    val title = Uri.decode(entry.arguments?.getString("title").orEmpty())
                    ShowDetailScreen(
                        showUri = uri,
                        title = title,
                        viewModel = libraryViewModel,
                        playbackState = playbackState,
                        onBack = { navController.popBackStack() },
                        onPlayEpisode = { epUri, sUri ->
                            scope.launch { container.playbackRouter.playUri(epUri, sUri) }
                        },
                        bottomPadding = innerPadding,
                    )
                }

                composable(
                    route = "audiobook/{uri}/{title}",
                    arguments = listOf(
                        navArgument("uri") { type = NavType.StringType },
                        navArgument("title") { type = NavType.StringType },
                    ),
                ) { entry ->
                    val uri = Uri.decode(entry.arguments?.getString("uri").orEmpty())
                    val title = Uri.decode(entry.arguments?.getString("title").orEmpty())
                    AudiobookDetailScreen(
                        audiobookUri = uri,
                        title = title,
                        viewModel = libraryViewModel,
                        playbackState = playbackState,
                        onBack = { navController.popBackStack() },
                        onPlayChapter = { chUri, abUri ->
                            scope.launch { container.playbackRouter.playUri(chUri, abUri) }
                        },
                        bottomPadding = innerPadding,
                    )
                }
            }
        }

        if (showNowPlaying) {
            NowPlayingSheet(
                state = playbackState,
                queue = queue,
                lyricsRepository = container.lyricsRepository,
                onDismiss = { showNowPlaying = false },
                onTogglePlayPause = { scope.launch { container.playbackRouter.togglePlayPause() } },
                onSkipNext = { scope.launch { container.playbackRouter.skipNext() } },
                onSkipPrevious = { scope.launch { container.playbackRouter.skipPrevious() } },
                onSeekTo = { pos -> scope.launch { container.playbackRouter.seekTo(pos) } },
                onToggleShuffle = {
                    scope.launch { container.playbackRouter.setShuffle(!playbackState.shuffleEnabled) }
                },
                onCycleRepeat = {
                    val nextRepeat = when (playbackState.repeatMode) {
                        RepeatMode.OFF -> RepeatMode.CONTEXT
                        RepeatMode.CONTEXT -> RepeatMode.TRACK
                        RepeatMode.TRACK -> RepeatMode.OFF
                    }
                    scope.launch { container.playbackRouter.setRepeatMode(nextRepeat) }
                },
                onSetPodcastSpeed = { speed ->
                    scope.launch { container.playbackRouter.setPodcastSpeed(speed) }
                },
                onToggleLike = { scope.launch { container.playbackRouter.toggleLikeCurrentTrack() } },
                onOpenDevices = {
                    scope.launch { container.playbackRouter.refreshDevices() }
                    showDevicesSheet = true
                },
                onPlayQueuedUri = { uri ->
                    scope.launch { container.playbackRouter.playUri(uri) }
                },
                onRefreshQueue = {
                    scope.launch { container.playbackRouter.refreshQueue() }
                },
            )
        }

        if (showDevicesSheet) {
            ConnectDevicesBottomSheet(
                devices = devices,
                activeDevice = playbackState.activeDevice,
                isPlaying = playbackState.isPlaying,
                onSelectDevice = { id ->
                    scope.launch { container.playbackRouter.transferPlayback(id, true) }
                    showDevicesSheet = false
                },
                onVolumeChange = { vol, devId ->
                    scope.launch { container.playbackRouter.setRemoteVolume(vol, devId) }
                },
                onDismiss = { showDevicesSheet = false },
            )
        }

        if (showProfileSheet) {
            ProfileAndAudioStatusSheet(
                profileName = profileName,
                productTier = productTier,
                clientId = savedClientId,
                isDemoMode = isDemoMode,
                isAppRemoteConnected = isAppRemoteConnected,
                needsSsoAuthorization = needsSsoAuthorization,
                lastAudioError = lastAudioError,
                ssoFeedback = ssoFeedback,
                githubRepo = githubRepo,
                updateStatus = updateStatus,
                onCheckAndInstallUpdate = { repoSlug, force ->
                    scope.launch {
                        container.tokenStore.saveGithubRepo(repoSlug)
                        container.appUpdateManager.checkAndDownloadAndInstall(repoSlug, force)
                    }
                },
                onInstallReadyApk = { apkFile ->
                    container.appUpdateManager.launchApkInstaller(apkFile)
                },
                onReconnectSpotify = launchNativeSsoOrReconnect,
                onSyncNow = {
                    scope.launch { container.syncEngine.syncAll() }
                },
                onConfirmLogout = {
                    scope.launch { container.tokenStore.clear() }
                },
                onDismiss = { showProfileSheet = false },
            )
        }
    }
}
