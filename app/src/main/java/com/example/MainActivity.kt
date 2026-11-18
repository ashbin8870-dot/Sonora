package com.example

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.QueueMusic
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.example.data.model.TrackEntity
import com.example.ui.components.AddStreamDialog
import com.example.ui.components.AddToPlaylistSheet
import com.example.ui.components.CountryPickerSheet
import com.example.ui.components.CreatePlaylistDialog
import com.example.ui.components.ImportPlaylistDialog
import com.example.ui.components.ImportPlaylistSourceTab
import com.example.ui.components.MiniPlayerBar
import com.example.ui.components.NowPlayingSheet
import com.example.ui.navigation.DiscoverRoute
import com.example.ui.navigation.LibraryRoute
import com.example.ui.navigation.PlaylistDetailRoute
import com.example.ui.navigation.PlaylistsRoute
import com.example.ui.navigation.SearchRoute
import com.example.ui.screens.DiscoverScreen
import com.example.ui.screens.LibraryScreen
import com.example.ui.screens.PlaylistDetailScreen
import com.example.ui.screens.PlaylistsScreen
import com.example.ui.screens.SearchScreen
import com.example.ui.theme.SonoraTheme
import com.example.ui.viewmodel.MusicViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class MainActivity : ComponentActivity() {

    private val pendingAuthUri = MutableStateFlow<Uri?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIncomingIntent(intent)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.Transparent.toArgb()),
            navigationBarStyle = SystemBarStyle.dark(Color.Transparent.toArgb())
        )
        setContent {
            val authUri by pendingAuthUri.asStateFlow().collectAsStateWithLifecycle()
            SonoraRoot(
                pendingAuthUri = authUri,
                onAuthUriConsumed = { pendingAuthUri.value = null }
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: Intent?) {
        val data = intent?.data ?: return
        if (data.scheme == "sonora" && data.host == "spotify-callback") {
            pendingAuthUri.value = data
        }
    }
}

private data class TopLevelDestination(
    val route: Any,
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val tag: String,
)

@Composable
fun SonoraRoot(
    pendingAuthUri: Uri? = null,
    onAuthUriConsumed: () -> Unit = {},
    viewModel: MusicViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val servicesState by viewModel.servicesState.collectAsStateWithLifecycle()
    val globalSearchState by viewModel.globalSearchState.collectAsStateWithLifecycle()
    val isImportingPlaylist by viewModel.isImportingPlaylist.collectAsStateWithLifecycle()
    val playerState by viewModel.playerState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(pendingAuthUri) {
        pendingAuthUri?.let { uri ->
            viewModel.handleSpotifyAuthCallback(uri)
            onAuthUriConsumed()
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> viewModel.connectMediaController()
                Lifecycle.Event.ON_STOP -> viewModel.disconnectMediaController()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    val snackbarHostState = remember { SnackbarHostState() }

    var showNowPlaying by remember { mutableStateOf(false) }
    var showAddStreamDialog by remember { mutableStateOf(false) }
    var showCreatePlaylistDialog by remember { mutableStateOf(false) }
    var showImportPlaylistDialog by remember { mutableStateOf(false) }
    var importDialogSourceTab by remember { mutableStateOf(ImportPlaylistSourceTab.YOUTUBE_MUSIC) }
    var showCountryPickerSheet by remember { mutableStateOf(false) }
    var pendingPlaylistCustomName by remember { mutableStateOf("") }
    var trackForPlaylistSheet by remember { mutableStateOf<TrackEntity?>(null) }

    // SAF OpenMultipleDocuments launcher (zero storage permissions needed)
    val audioPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.importLocalAudio(uris)
        }
    }

    // SAF OpenDocument launcher for .m3u / .m3u8 / .pls / .txt playlists
    val playlistFilePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.importPlaylistFromUri(
                context = context,
                uri = uri,
                customName = pendingPlaylistCustomName
            ) { importedId ->
                navController.navigate(PlaylistDetailRoute(importedId))
            }
        }
        pendingPlaylistCustomName = ""
    }

    // SAF OpenMultipleDocuments launcher to import audio files directly as a new playlist
    val playlistAudioFilesLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.importAudioFilesAsPlaylist(
                playlistName = pendingPlaylistCustomName,
                uris = uris
            ) { importedId ->
                navController.navigate(PlaylistDetailRoute(importedId))
            }
        }
        pendingPlaylistCustomName = ""
    }

    // Android 13+ notification permission for media playback controls
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ -> }

    LaunchedEffect(playerState.isPlaying) {
        if (playerState.isPlaying && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    LaunchedEffect(Unit) {
        viewModel.snackbarEvents.collect { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    LaunchedEffect(playerState.errorMessage) {
        playerState.errorMessage?.let { err ->
            snackbarHostState.showSnackbar(err)
            viewModel.clearPlayerError()
        }
    }

    val topDestinations = remember {
        listOf(
            TopLevelDestination(
                route = DiscoverRoute,
                labelRes = R.string.nav_discover,
                selectedIcon = Icons.Rounded.Explore,
                unselectedIcon = Icons.Outlined.Explore,
                tag = "nav_discover"
            ),
            TopLevelDestination(
                route = LibraryRoute,
                labelRes = R.string.nav_library,
                selectedIcon = Icons.Rounded.LibraryMusic,
                unselectedIcon = Icons.Outlined.LibraryMusic,
                tag = "nav_library"
            ),
            TopLevelDestination(
                route = PlaylistsRoute,
                labelRes = R.string.nav_playlists,
                selectedIcon = Icons.Rounded.QueueMusic,
                unselectedIcon = Icons.Outlined.QueueMusic,
                tag = "nav_playlists"
            ),
            TopLevelDestination(
                route = SearchRoute,
                labelRes = R.string.nav_search,
                selectedIcon = Icons.Rounded.Search,
                unselectedIcon = Icons.Outlined.Search,
                tag = "nav_search"
            )
        )
    }

    val navigateToTopLevel: (Any) -> Unit = { route ->
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    SonoraTheme(darkTheme = uiState.preferences.darkTheme) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val useRail = maxWidth >= 600.dp

            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.TopStart
            ) {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    contentWindowInsets = WindowInsets.safeDrawing,
                    snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
                    bottomBar = {
                        if (!useRail) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                MiniPlayerBar(
                                    playerState = playerState,
                                    onExpandClick = { showNowPlaying = true },
                                    onTogglePlayPause = { viewModel.togglePlayPause() },
                                    onNextClick = { viewModel.skipNext() },
                                    onQuickPlayClick = { viewModel.quickPlayDefault() }
                                )

                                NavigationBar(
                                    containerColor = MaterialTheme.colorScheme.surface,
                                    tonalElevation = 4.dp
                                ) {
                                    topDestinations.forEach { dest ->
                                        val selected = when (dest.route) {
                                            DiscoverRoute -> currentDestination?.hasRoute<DiscoverRoute>() == true
                                            LibraryRoute -> currentDestination?.hasRoute<LibraryRoute>() == true
                                            PlaylistsRoute -> currentDestination?.hasRoute<PlaylistsRoute>() == true ||
                                                currentDestination?.hasRoute<PlaylistDetailRoute>() == true
                                            SearchRoute -> currentDestination?.hasRoute<SearchRoute>() == true
                                            else -> false
                                        }
                                        NavigationBarItem(
                                            selected = selected,
                                            onClick = { navigateToTopLevel(dest.route) },
                                            icon = {
                                                Icon(
                                                    imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                                    contentDescription = stringResource(dest.labelRes)
                                                )
                                            },
                                            label = { Text(stringResource(dest.labelRes)) },
                                            modifier = Modifier.testTag(dest.tag)
                                        )
                                    }
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
                        if (useRail) {
                            NavigationRail(
                                containerColor = MaterialTheme.colorScheme.surface,
                                modifier = Modifier.fillMaxHeight()
                            ) {
                                topDestinations.forEach { dest ->
                                    val selected = when (dest.route) {
                                        DiscoverRoute -> currentDestination?.hasRoute<DiscoverRoute>() == true
                                        LibraryRoute -> currentDestination?.hasRoute<LibraryRoute>() == true
                                        PlaylistsRoute -> currentDestination?.hasRoute<PlaylistsRoute>() == true ||
                                            currentDestination?.hasRoute<PlaylistDetailRoute>() == true
                                        SearchRoute -> currentDestination?.hasRoute<SearchRoute>() == true
                                        else -> false
                                    }
                                    NavigationRailItem(
                                        selected = selected,
                                        onClick = { navigateToTopLevel(dest.route) },
                                        icon = {
                                            Icon(
                                                imageVector = if (selected) dest.selectedIcon else dest.unselectedIcon,
                                                contentDescription = stringResource(dest.labelRes)
                                            )
                                        },
                                        label = { Text(stringResource(dest.labelRes)) },
                                        modifier = Modifier.testTag(dest.tag)
                                    )
                                }
                            }
                        }

                        Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                            val contentBottomPad = if (useRail) {
                                PaddingValues(bottom = 86.dp)
                            } else {
                                PaddingValues(bottom = 0.dp)
                            }

                            NavHost(
                                navController = navController,
                                startDestination = DiscoverRoute,
                                modifier = Modifier.fillMaxSize()
                            ) {
                                composable<DiscoverRoute> {
                                    DiscoverScreen(
                                        uiState = uiState,
                                        playerState = playerState,
                                        onToggleOfflineMode = { viewModel.toggleOfflineOnlyMode() },
                                        onToggleTheme = { viewModel.toggleDarkTheme() },
                                        onImportLocalAudio = { audioPickerLauncher.launch(arrayOf("audio/*")) },
                                        onOpenAddStreamDialog = { showAddStreamDialog = true },
                                        onPlayTrack = { track, queue -> viewModel.playTrack(track, queue) },
                                        onShufflePlay = { tracks -> viewModel.shuffleAndPlay(tracks) },
                                        onToggleFavorite = { viewModel.toggleFavorite(it) },
                                        onDownloadTrack = { viewModel.downloadTrack(it) },
                                        onRemoveDownload = { viewModel.removeDownload(it) },
                                        onPlayNext = { viewModel.playNextInQueue(it) },
                                        onAddToQueue = { viewModel.addToQueue(it) },
                                        onAddToPlaylist = { trackForPlaylistSheet = it },
                                        onDeleteTrack = { viewModel.deleteTrack(it) },
                                        bottomContentPadding = contentBottomPad
                                    )
                                }

                                composable<LibraryRoute> {
                                    LibraryScreen(
                                        uiState = uiState,
                                        playerState = playerState,
                                        onSelectTab = { viewModel.selectLibraryTab(it) },
                                        onToggleOfflineMode = { viewModel.toggleOfflineOnlyMode() },
                                        onImportLocalAudio = { audioPickerLauncher.launch(arrayOf("audio/*")) },
                                        onPlayTrack = { track, queue -> viewModel.playTrack(track, queue) },
                                        onShufflePlay = { tracks -> viewModel.shuffleAndPlay(tracks) },
                                        onToggleFavorite = { viewModel.toggleFavorite(it) },
                                        onDownloadTrack = { viewModel.downloadTrack(it) },
                                        onRemoveDownload = { viewModel.removeDownload(it) },
                                        onPlayNext = { viewModel.playNextInQueue(it) },
                                        onAddToQueue = { viewModel.addToQueue(it) },
                                        onAddToPlaylist = { trackForPlaylistSheet = it },
                                        onDeleteTrack = { viewModel.deleteTrack(it) },
                                        bottomContentPadding = contentBottomPad
                                    )
                                }

                                composable<PlaylistsRoute> {
                                    PlaylistsScreen(
                                        playlists = uiState.playlists,
                                        isImportingPlaylist = isImportingPlaylist,
                                        onOpenPlaylist = { id -> navController.navigate(PlaylistDetailRoute(id)) },
                                        onCreatePlaylistClick = { showCreatePlaylistDialog = true },
                                        onImportWithSourceTab = { sourceTab ->
                                            importDialogSourceTab = sourceTab
                                            showImportPlaylistDialog = true
                                        },
                                        onDeletePlaylist = { id, name -> viewModel.deletePlaylist(id, name) },
                                        onPlayPlaylist = { pl ->
                                            pl.tracks.firstOrNull()?.let { first ->
                                                viewModel.playTrack(first, pl.tracks)
                                            }
                                        },
                                        bottomContentPadding = contentBottomPad
                                    )
                                }

                                composable<PlaylistDetailRoute> { entry ->
                                    val args = entry.toRoute<PlaylistDetailRoute>()
                                    val playlistState by viewModel.observePlaylist(args.playlistId)
                                        .collectAsStateWithLifecycle(initialValue = null)
                                    PlaylistDetailScreen(
                                        playlistWithTracks = playlistState,
                                        uiState = uiState,
                                        playerState = playerState,
                                        onBack = { navController.popBackStack() },
                                        onPlayTrack = { track, queue -> viewModel.playTrack(track, queue) },
                                        onShufflePlay = { tracks -> viewModel.shuffleAndPlay(tracks) },
                                        onToggleFavorite = { viewModel.toggleFavorite(it) },
                                        onDownloadTrack = { viewModel.downloadTrack(it) },
                                        onRemoveDownload = { viewModel.removeDownload(it) },
                                        onPlayNext = { viewModel.playNextInQueue(it) },
                                        onAddToQueue = { viewModel.addToQueue(it) },
                                        onAddTrackToThisPlaylist = { plId, plName, tId ->
                                            viewModel.addTrackToPlaylist(plId, plName, tId)
                                        },
                                        onRemoveTrackFromThisPlaylist = { plId, tId ->
                                            viewModel.removeTrackFromPlaylist(plId, tId)
                                        },
                                        onOpenPlaylistPicker = { trackForPlaylistSheet = it },
                                        bottomContentPadding = contentBottomPad
                                    )
                                }

                                composable<SearchRoute> {
                                    SearchScreen(
                                        uiState = uiState,
                                        globalSearchState = globalSearchState,
                                        playerState = playerState,
                                        onQueryChange = { viewModel.updateSearchQuery(it) },
                                        onSearchSubmit = { viewModel.searchGlobalMusic(it) },
                                        onSelectCountry = { viewModel.selectSearchCountry(it) },
                                        onOpenCountryPicker = { showCountryPickerSheet = true },
                                        onSelectFilter = { viewModel.selectSearchFilter(it) },
                                        onPlayTrack = { track, queue -> viewModel.playTrack(track, queue) },
                                        onToggleFavorite = { viewModel.toggleFavorite(it) },
                                        onDownloadTrack = { viewModel.downloadTrack(it) },
                                        onRemoveDownload = { viewModel.removeDownload(it) },
                                        onPlayNext = { viewModel.playNextInQueue(it) },
                                        onAddToQueue = { viewModel.addToQueue(it) },
                                        onAddToPlaylist = { trackForPlaylistSheet = it },
                                        onDeleteTrack = { viewModel.deleteTrack(it) },
                                        onPlayGlobalTrack = { viewModel.playGlobalTrack(it) },
                                        onPlayNextGlobalTrack = { viewModel.playNextGlobalTrack(it) },
                                        onAddGlobalTrackToQueue = { viewModel.addGlobalTrackToQueue(it) },
                                        onDownloadGlobalTrack = { viewModel.downloadGlobalTrack(it) },
                                        onAddGlobalTrackToPlaylist = { gTrack ->
                                            viewModel.saveGlobalTrackAndPickPlaylist(gTrack) { saved ->
                                                trackForPlaylistSheet = saved
                                            }
                                        },
                                        bottomContentPadding = contentBottomPad
                                    )
                                }
                            }

                            if (useRail) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.BottomCenter)
                                        .navigationBarsPadding()
                                ) {
                                    MiniPlayerBar(
                                        playerState = playerState,
                                        onExpandClick = { showNowPlaying = true },
                                        onTogglePlayPause = { viewModel.togglePlayPause() },
                                        onNextClick = { viewModel.skipNext() },
                                        onQuickPlayClick = { viewModel.quickPlayDefault() }
                                    )
                                }
                            }
                        }
                    }
                }

                if (showNowPlaying && playerState.currentTrack != null) {
                    val curId = playerState.currentTrack?.id
                    NowPlayingSheet(
                        playerState = playerState,
                        downloadProgress = curId?.let { uiState.downloadProgress[it] },
                        onDismiss = { showNowPlaying = false },
                        onTogglePlayPause = { viewModel.togglePlayPause() },
                        onSeekTo = { viewModel.seekTo(it) },
                        onSkipPrevious = { viewModel.skipPrevious() },
                        onSkipNext = { viewModel.skipNext() },
                        onToggleShuffle = { viewModel.toggleShuffle() },
                        onCycleRepeat = { viewModel.cycleRepeatMode() },
                        onToggleFavorite = { viewModel.toggleFavorite(it) },
                        onDownloadTrack = { viewModel.downloadTrack(it) },
                        onRemoveDownload = { viewModel.removeDownload(it) },
                        onAddToPlaylist = { trackForPlaylistSheet = it },
                        onSetSpeed = { viewModel.setPlaybackSpeed(it) },
                        onSetSleepTimer = { viewModel.setSleepTimer(it) },
                        onJumpToQueueIndex = { viewModel.jumpToQueueIndex(it) },
                        onRemoveFromQueue = { viewModel.removeFromQueue(it) }
                    )
                }

                if (showAddStreamDialog) {
                    AddStreamDialog(
                        onDismiss = { showAddStreamDialog = false },
                        onConfirm = { title, artist, url, genre, isLive, playNow ->
                            viewModel.addCustomStream(title, artist, url, genre, isLive, playNow)
                        }
                    )
                }

                if (showCreatePlaylistDialog) {
                    CreatePlaylistDialog(
                        onDismiss = { showCreatePlaylistDialog = false },
                        onCreate = { name, desc, coverKey ->
                            viewModel.createPlaylist(name, desc, coverKey)
                        }
                    )
                }

                if (showImportPlaylistDialog) {
                    ImportPlaylistDialog(
                        initialTab = importDialogSourceTab,
                        spotifyConfigured = servicesState.spotifyConfigured,
                        spotifySignedIn = servicesState.spotifySignedIn,
                        spotifyPlaylists = servicesState.spotifyPlaylists,
                        onConnectSpotify = { viewModel.startSpotifySignIn(context) },
                        onImportSpotifyAccountPlaylist = { spPlaylist ->
                            viewModel.importSpotifyPlaylistByAccount(spPlaylist) { importedId ->
                                navController.navigate(PlaylistDetailRoute(importedId))
                            }
                        },
                        onDismiss = { showImportPlaylistDialog = false },
                        onImportFromUrl = { url, customName ->
                            viewModel.importPlaylistFromUrl(url, customName) { importedId ->
                                navController.navigate(PlaylistDetailRoute(importedId))
                            }
                        },
                        onPickPlaylistFile = { customName ->
                            pendingPlaylistCustomName = customName
                            playlistFilePickerLauncher.launch(
                                arrayOf(
                                    "audio/x-mpegurl",
                                    "audio/mpegurl",
                                    "application/vnd.apple.mpegurl",
                                    "application/x-mpegurl",
                                    "audio/x-scpls",
                                    "text/plain",
                                    "*/*"
                                )
                            )
                        },
                        onPickAudioFilesForPlaylist = { customName ->
                            pendingPlaylistCustomName = customName
                            playlistAudioFilesLauncher.launch(arrayOf("audio/*"))
                        }
                    )
                }

                if (showCountryPickerSheet) {
                    CountryPickerSheet(
                        selectedCountryCode = globalSearchState.selectedCountryCode,
                        onDismiss = { showCountryPickerSheet = false },
                        onSelectCountry = { market ->
                            viewModel.selectSearchCountry(market.code)
                        }
                    )
                }

                trackForPlaylistSheet?.let { selectedTrack ->
                    AddToPlaylistSheet(
                        track = selectedTrack,
                        playlists = uiState.playlists,
                        onDismiss = { trackForPlaylistSheet = null },
                        onSelectPlaylist = { pl ->
                            viewModel.addTrackToPlaylist(pl.playlist.id, pl.playlist.name, selectedTrack.id)
                        },
                        onCreateNewPlaylist = { showCreatePlaylistDialog = true }
                    )
                }
            }
        }
    }
}
