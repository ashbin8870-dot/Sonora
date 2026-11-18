package com.example.ui.viewmodel

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.SonoraApplication
import com.example.data.local.UserPreferences
import com.example.data.model.PlaylistWithTracks
import com.example.data.model.TrackEntity
import com.example.data.model.TrackSourceType
import com.example.data.remote.CountryMarket
import com.example.data.remote.GlobalMusicMarkets
import com.example.data.remote.GlobalMusicTrack
import com.example.data.remote.SpotifyConnectState
import com.example.data.remote.SpotifyPlaylist
import com.example.data.remote.SpotifyTrack
import com.example.data.remote.SpotifyUserProfile
import com.example.data.remote.YouTubeMusicTrack
import com.example.player.PlayerUiState
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class LibraryTab(val label: String) {
    OFFLINE("Offline Ready"),
    ALL("All Tracks"),
    FAVORITES("Favorites")
}

enum class SearchFilter(val label: String) {
    ALL("All"),
    OFFLINE_READY("Offline Ready"),
    ONLINE_STREAMS("Online"),
    RADIO("Live Radio"),
    LOFI("Lo-Fi"),
    ELECTRONIC("Electronic"),
    ACOUSTIC("Acoustic"),
    AMBIENT("Ambient"),
}

enum class StreamingServiceTab(val label: String) {
    SPOTIFY("Spotify"),
    YOUTUBE_MUSIC("YouTube Music")
}

data class ServicesUiState(
    val selectedService: StreamingServiceTab = StreamingServiceTab.SPOTIFY,
    // Spotify
    val spotifyConfigured: Boolean = false,
    val spotifySignedIn: Boolean = false,
    val spotifyLoading: Boolean = false,
    val spotifyProfile: SpotifyUserProfile? = null,
    val spotifyPlaylists: List<SpotifyPlaylist> = emptyList(),
    val spotifySelectedPlaylist: SpotifyPlaylist? = null,
    val spotifySelectedPlaylistName: String? = null,
    val spotifyTracks: List<SpotifyTrack> = emptyList(),
    val spotifySearchQuery: String = "",
    val spotifyDirectUrl: String = "",
    val spotifyConnectState: SpotifyConnectState? = null,
    // YouTube Music
    val youtubeConfigured: Boolean = false,
    val youtubeLoading: Boolean = false,
    val youtubeSearchQuery: String = "",
    val youtubeDirectUrl: String = "",
    val youtubeTracks: List<YouTubeMusicTrack> = emptyList(),
)

data class GlobalSearchUiState(
    val selectedCountryCode: String = GlobalMusicMarkets.ALL_COUNTRIES.code,
    val isLoading: Boolean = false,
    val results: List<GlobalMusicTrack> = emptyList(),
    val errorMessage: String? = null,
    val lastSearchedQuery: String = "",
) {
    val selectedCountry: CountryMarket
        get() = GlobalMusicMarkets.findByCode(selectedCountryCode)
}

data class MusicHomeUiState(
    val allTracks: List<TrackEntity> = emptyList(),
    val offlineTracks: List<TrackEntity> = emptyList(),
    val onlineTracks: List<TrackEntity> = emptyList(),
    val liveRadioStations: List<TrackEntity> = emptyList(),
    val favoriteTracks: List<TrackEntity> = emptyList(),
    val recentlyPlayed: List<TrackEntity> = emptyList(),
    val playlists: List<PlaylistWithTracks> = emptyList(),
    val downloadProgress: Map<String, Float> = emptyMap(),
    val isDeviceOnline: Boolean = true,
    val preferences: UserPreferences = UserPreferences(),
    val selectedLibraryTab: LibraryTab = LibraryTab.OFFLINE,
    val searchQuery: String = "",
    val selectedSearchFilter: SearchFilter = SearchFilter.ALL,
) {
    val isEffectivelyOffline: Boolean
        get() = preferences.offlineOnlyMode || !isDeviceOnline

    val totalOfflineBytes: Long
        get() = offlineTracks.sumOf { it.fileSizeBytes }

    val filteredSearchResults: List<TrackEntity>
        get() {
            val q = searchQuery.trim().lowercase()
            return allTracks.filter { track ->
                val matchesQuery = q.isEmpty() ||
                    track.title.lowercase().contains(q) ||
                    track.artist.lowercase().contains(q) ||
                    track.album.lowercase().contains(q) ||
                    track.genre.lowercase().contains(q)

                val matchesFilter = when (selectedSearchFilter) {
                    SearchFilter.ALL -> true
                    SearchFilter.OFFLINE_READY -> track.isAvailableOffline
                    SearchFilter.ONLINE_STREAMS -> !track.isAvailableOffline && !track.isLiveStream
                    SearchFilter.RADIO -> track.isLiveStream
                    SearchFilter.LOFI -> track.genre.equals("Lo-Fi", ignoreCase = true)
                    SearchFilter.ELECTRONIC -> track.genre.equals("Electronic", ignoreCase = true)
                    SearchFilter.ACOUSTIC -> track.genre.equals("Acoustic", ignoreCase = true)
                    SearchFilter.AMBIENT -> track.genre.equals("Ambient", ignoreCase = true)
                }
                matchesQuery && matchesFilter
            }
        }
}

@OptIn(FlowPreview::class)
class MusicViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as SonoraApplication
    private val repository = app.musicRepository
    private val preferencesRepo = app.preferencesRepository
    private val networkMonitor = app.networkMonitor
    private val playerManager = app.playerManager
    private val spotifyClient = app.spotifyClient
    private val youTubeClient = app.youTubeMusicClient
    private val globalSearchClient = app.globalMusicSearchClient
    private val playlistImportClient = app.playlistImportClient
    private val trackStreamResolver = app.trackStreamResolver

    private val selectedLibraryTab = MutableStateFlow(LibraryTab.OFFLINE)
    private val searchQuery = MutableStateFlow("")
    private val selectedSearchFilter = MutableStateFlow(SearchFilter.ALL)

    private val _servicesState = MutableStateFlow(
        ServicesUiState(
            spotifyConfigured = spotifyClient.isConfigured,
            youtubeConfigured = youTubeClient.isConfigured
        )
    )
    val servicesState: StateFlow<ServicesUiState> = _servicesState.asStateFlow()

    private val _globalSearchState = MutableStateFlow(GlobalSearchUiState())
    val globalSearchState: StateFlow<GlobalSearchUiState> = _globalSearchState.asStateFlow()

    private val _isImportingPlaylist = MutableStateFlow(false)
    val isImportingPlaylist: StateFlow<Boolean> = _isImportingPlaylist.asStateFlow()

    private var globalSearchJob: Job? = null

    private val _snackbarEvents = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val snackbarEvents: SharedFlow<String> = _snackbarEvents.asSharedFlow()

    val playerState: StateFlow<PlayerUiState> = playerManager.uiState

    private val baseDataFlow = combine(
        repository.allTracks,
        repository.offlineReadyTracks,
        repository.favoriteTracks,
        repository.recentlyPlayed,
        repository.playlistsWithTracks,
    ) { all, offline, favs, recent, playlists ->
        DataBundle(all, offline, favs, recent, playlists)
    }

    private val envFlow = combine(
        repository.downloadProgress,
        networkMonitor.isOnline,
        preferencesRepo.preferences,
        selectedLibraryTab,
        searchQuery,
        selectedSearchFilter,
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        EnvBundle(
            downloadProgress = values[0] as Map<String, Float>,
            isOnline = values[1] as Boolean,
            prefs = values[2] as UserPreferences,
            libraryTab = values[3] as LibraryTab,
            query = values[4] as String,
            filter = values[5] as SearchFilter,
        )
    }

    val uiState: StateFlow<MusicHomeUiState> = combine(baseDataFlow, envFlow) { data, env ->
        playerManager.refreshTrackMetadata(data.all)
        MusicHomeUiState(
            allTracks = data.all,
            offlineTracks = data.offline,
            onlineTracks = data.all.filter { !it.isLiveStream && it.streamUrl != null },
            liveRadioStations = data.all.filter { it.isLiveStream },
            favoriteTracks = data.favs,
            recentlyPlayed = data.recent,
            playlists = data.playlists,
            downloadProgress = env.downloadProgress,
            isDeviceOnline = env.isOnline,
            preferences = env.prefs,
            selectedLibraryTab = env.libraryTab,
            searchQuery = env.query,
            selectedSearchFilter = env.filter,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = MusicHomeUiState()
    )

    init {
        viewModelScope.launch {
            val prefs = preferencesRepo.preferences.first()
            playerManager.setPlaybackSpeed(prefs.playbackSpeed)
            val hasSpotifySession = !prefs.spotifyRefreshToken.isNullOrBlank() ||
                !prefs.spotifyAccessToken.isNullOrBlank()
            if (hasSpotifySession && spotifyClient.isConfigured) {
                _servicesState.update { it.copy(spotifySignedIn = true) }
                refreshSpotifyData()
            }
        }
        viewModelScope.launch {
            searchQuery
                .debounce(450L)
                .distinctUntilChanged()
                .collectLatest { raw ->
                    val trimmed = raw.trim()
                    if (trimmed.isEmpty()) {
                        _globalSearchState.update {
                            it.copy(
                                isLoading = false,
                                results = emptyList(),
                                errorMessage = null,
                                lastSearchedQuery = ""
                            )
                        }
                    } else if (trimmed.length >= 2 && !uiState.value.isEffectivelyOffline) {
                        executeGlobalSearch(trimmed, _globalSearchState.value.selectedCountryCode)
                    }
                }
        }
    }

    fun connectMediaController() = playerManager.connect()

    fun disconnectMediaController() = playerManager.disconnect()

    fun observePlaylist(playlistId: Long): Flow<PlaylistWithTracks?> =
        repository.observePlaylist(playlistId)

    fun selectLibraryTab(tab: LibraryTab) {
        selectedLibraryTab.value = tab
    }

    fun updateSearchQuery(query: String) {
        searchQuery.value = query
    }

    fun selectSearchFilter(filter: SearchFilter) {
        selectedSearchFilter.value = filter
    }

    // --- Streaming Services (Spotify & YouTube Music) ---

    fun selectStreamingService(tab: StreamingServiceTab) {
        _servicesState.update { it.copy(selectedService = tab) }
    }

    fun startSpotifySignIn(context: Context) {
        if (!spotifyClient.isConfigured) {
            _snackbarEvents.tryEmit("Add SPOTIFY_CLIENT_ID in the AI Studio Secrets panel first.")
            return
        }
        viewModelScope.launch {
            runCatching {
                val intent: Intent = spotifyClient.createAuthorizationIntent()
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            }.onFailure {
                _snackbarEvents.tryEmit("Could not open browser for Spotify sign-in.")
            }
        }
    }

    fun handleSpotifyAuthCallback(uri: Uri) {
        if (uri.scheme != "sonora" || uri.host != "spotify-callback") return
        viewModelScope.launch {
            _servicesState.update { it.copy(spotifyLoading = true) }
            spotifyClient.handleAuthCallback(uri)
                .onSuccess {
                    _servicesState.update { it.copy(spotifySignedIn = true) }
                    _snackbarEvents.tryEmit("Connected to Spotify")
                    refreshSpotifyData()
                }
                .onFailure { err ->
                    _servicesState.update { it.copy(spotifyLoading = false) }
                    _snackbarEvents.tryEmit(err.message ?: "Spotify sign-in failed")
                }
        }
    }

    fun refreshSpotifyData() {
        if (!spotifyClient.isConfigured) return
        viewModelScope.launch {
            _servicesState.update { it.copy(spotifyLoading = true) }
            val profileRes = spotifyClient.fetchCurrentUser()
            val playlistsRes = spotifyClient.fetchUserPlaylists()
            val savedRes = spotifyClient.fetchSavedTracks()
            val connectRes = spotifyClient.fetchPlaybackState()

            profileRes.onFailure { err ->
                _snackbarEvents.tryEmit(err.message ?: "Failed to load Spotify profile")
            }

            _servicesState.update {
                it.copy(
                    spotifyLoading = false,
                    spotifySignedIn = profileRes.isSuccess,
                    spotifyProfile = profileRes.getOrNull(),
                    spotifyPlaylists = playlistsRes.getOrDefault(emptyList()),
                    spotifySelectedPlaylist = null,
                    spotifySelectedPlaylistName = "Your Liked Songs",
                    spotifyTracks = savedRes.getOrDefault(emptyList()),
                    spotifyConnectState = connectRes.getOrNull(),
                )
            }
        }
    }

    fun selectSpotifyPlaylist(playlist: SpotifyPlaylist) {
        viewModelScope.launch {
            _servicesState.update {
                it.copy(
                    spotifyLoading = true,
                    spotifySelectedPlaylist = playlist,
                    spotifySelectedPlaylistName = playlist.name
                )
            }
            spotifyClient.fetchPlaylistTracks(playlist.id)
                .onSuccess { tracks ->
                    _servicesState.update {
                        it.copy(spotifyLoading = false, spotifyTracks = tracks)
                    }
                }
                .onFailure { err ->
                    _servicesState.update { it.copy(spotifyLoading = false) }
                    _snackbarEvents.tryEmit(err.message ?: "Failed to load playlist tracks")
                }
        }
    }

    fun importSelectedSpotifyPlaylistToSonora() {
        val state = _servicesState.value
        val playlist = state.spotifySelectedPlaylist ?: return
        val tracks = state.spotifyTracks
        if (tracks.isEmpty()) {
            _snackbarEvents.tryEmit("Load tracks in this playlist first.")
            return
        }
        viewModelScope.launch {
            val payload = playlistImportClient.fromSpotifyPlaylist(playlist, tracks)
            repository.importPlaylistWithTracks(
                name = payload.name,
                description = payload.description,
                coverResKey = payload.coverResKey,
                tracks = payload.tracks
            )
            _snackbarEvents.tryEmit("Imported \"${payload.name}\" (${payload.tracks.size} tracks) to Playlists")
        }
    }

    fun updateSpotifySearchQuery(query: String) {
        _servicesState.update { it.copy(spotifySearchQuery = query) }
    }

    fun updateSpotifyDirectUrl(url: String) {
        _servicesState.update { it.copy(spotifyDirectUrl = url) }
    }

    fun playSpotifyDirectUrl() {
        val parsed = spotifyClient.parseSpotifyTrackUrl(_servicesState.value.spotifyDirectUrl)
        if (parsed == null) {
            _snackbarEvents.tryEmit("Enter a valid open.spotify.com/track/... or spotify:track:... link.")
            return
        }
        playSpotifyTrack(parsed)
    }

    fun searchSpotifyTracks() {
        val query = _servicesState.value.spotifySearchQuery.trim()
        if (query.isBlank()) return
        val market = _globalSearchState.value.selectedCountryCode
        viewModelScope.launch {
            _servicesState.update {
                it.copy(
                    spotifyLoading = true,
                    spotifySelectedPlaylist = null,
                    spotifySelectedPlaylistName = "Search: $query"
                )
            }
            spotifyClient.searchTracks(query, market = market)
                .onSuccess { tracks ->
                    _servicesState.update {
                        it.copy(spotifyLoading = false, spotifyTracks = tracks)
                    }
                }
                .onFailure { err ->
                    _servicesState.update { it.copy(spotifyLoading = false) }
                    _snackbarEvents.tryEmit(err.message ?: "Spotify search failed")
                }
        }
    }

    fun playSpotifyTrack(track: SpotifyTrack) {
        if (uiState.value.isEffectivelyOffline) {
            _snackbarEvents.tryEmit("Turn off Offline Mode to play Spotify tracks.")
            return
        }
        viewModelScope.launch {
            val enriched = trackStreamResolver.enrichSpotifyTrack(track)
            val entity = enriched.toAppPlayerEntity()
            repository.upsertTrack(entity)
            val queue = _servicesState.value.spotifyTracks
                .map { if (it.id == enriched.id) enriched else it }
                .map { it.toAppPlayerEntity() }
                .ifEmpty { listOf(entity) }
            playerManager.playTrack(entity, queue)
        }
    }

    fun playOnSpotifyConnect(track: SpotifyTrack) {
        viewModelScope.launch {
            spotifyClient.playOnSpotifyConnect(track.spotifyUri)
                .onSuccess {
                    _snackbarEvents.tryEmit("Playing \"${track.title}\" on Spotify Connect")
                    refreshSpotifyConnectState()
                }
                .onFailure { err ->
                    _snackbarEvents.tryEmit(err.message ?: "Spotify Connect failed")
                }
        }
    }

    fun toggleSpotifyConnectPlayPause() {
        val current = _servicesState.value.spotifyConnectState ?: return
        viewModelScope.launch {
            val res = if (current.isPlaying) spotifyClient.pauseSpotifyConnect()
            else spotifyClient.resumeSpotifyConnect()
            res.onSuccess { refreshSpotifyConnectState() }
                .onFailure { _snackbarEvents.tryEmit(it.message ?: "Command failed") }
        }
    }

    fun nextOnSpotifyConnect() {
        viewModelScope.launch {
            spotifyClient.skipNextSpotifyConnect()
                .onSuccess { refreshSpotifyConnectState() }
                .onFailure { _snackbarEvents.tryEmit(it.message ?: "Command failed") }
        }
    }

    private fun refreshSpotifyConnectState() {
        viewModelScope.launch {
            spotifyClient.fetchPlaybackState().onSuccess { state ->
                _servicesState.update { it.copy(spotifyConnectState = state) }
            }
        }
    }

    fun disconnectSpotify() {
        viewModelScope.launch {
            spotifyClient.disconnect()
            _servicesState.update {
                it.copy(
                    spotifySignedIn = false,
                    spotifyProfile = null,
                    spotifyPlaylists = emptyList(),
                    spotifySelectedPlaylist = null,
                    spotifyTracks = emptyList(),
                    spotifyConnectState = null,
                    spotifySelectedPlaylistName = null
                )
            }
            _snackbarEvents.tryEmit("Disconnected from Spotify")
        }
    }

    fun updateYouTubeSearchQuery(query: String) {
        _servicesState.update { it.copy(youtubeSearchQuery = query) }
    }

    fun updateYouTubeDirectUrl(url: String) {
        _servicesState.update { it.copy(youtubeDirectUrl = url) }
    }

    fun searchYouTubeMusic(overrideQuery: String? = null) {
        val query = (overrideQuery ?: _servicesState.value.youtubeSearchQuery).trim()
        if (query.isBlank()) return
        if (overrideQuery != null) {
            _servicesState.update { it.copy(youtubeSearchQuery = overrideQuery) }
        }
        if (uiState.value.isEffectivelyOffline) {
            _snackbarEvents.tryEmit("Turn off Offline Mode to search YouTube Music.")
            return
        }
        val regionCode = _globalSearchState.value.selectedCountryCode
        viewModelScope.launch {
            _servicesState.update { it.copy(youtubeLoading = true) }
            youTubeClient.searchMusic(query, regionCode = regionCode)
                .onSuccess { results ->
                    _servicesState.update {
                        it.copy(youtubeLoading = false, youtubeTracks = results)
                    }
                }
                .onFailure { err ->
                    _servicesState.update { it.copy(youtubeLoading = false) }
                    _snackbarEvents.tryEmit(err.message ?: "YouTube Music search failed")
                }
        }
    }

    fun playYouTubeDirectUrl() {
        val parsed = youTubeClient.parseYouTubeUrl(_servicesState.value.youtubeDirectUrl)
        if (parsed == null) {
            _snackbarEvents.tryEmit("Enter a valid YouTube or YouTube Music URL.")
            return
        }
        playYouTubeMusicTrack(parsed)
    }

    fun playYouTubeMusicTrack(track: YouTubeMusicTrack) {
        if (uiState.value.isEffectivelyOffline) {
            _snackbarEvents.tryEmit("Turn off Offline Mode to play YouTube Music.")
            return
        }
        viewModelScope.launch {
            val enriched = trackStreamResolver.enrichYouTubeTrack(track)
            val entity = enriched.toAppPlayerEntity()
            repository.upsertTrack(entity)
            val queue = _servicesState.value.youtubeTracks
                .map { if (it.videoId == enriched.videoId) enriched else it }
                .map { it.toAppPlayerEntity() }
                .ifEmpty { listOf(entity) }
            playerManager.playTrack(entity, queue)
        }
    }

    // --- Core Offline & Online Playback ---

    fun quickPlayDefault() {
        val state = uiState.value
        val candidates = if (state.isEffectivelyOffline) state.offlineTracks else state.allTracks
        val preferred = if (state.isEffectivelyOffline) {
            candidates.firstOrNull()
        } else {
            state.recentlyPlayed.firstOrNull() ?: candidates.firstOrNull()
        }
        if (preferred == null) {
            _snackbarEvents.tryEmit("Import an audio file or connect to stream.")
            return
        }
        playTrack(preferred, candidates)
    }

    fun playTrack(track: TrackEntity, contextQueue: List<TrackEntity> = uiState.value.allTracks) {
        val state = uiState.value
        if (!track.isAvailableOffline && state.isEffectivelyOffline) {
            val msg = if (state.preferences.offlineOnlyMode) {
                "Offline Mode is active. Turn it off or download \"${track.title}\" first."
            } else {
                "No internet connection. Play an offline or downloaded track instead."
            }
            _snackbarEvents.tryEmit(msg)
            return
        }

        val playableQueue = if (state.isEffectivelyOffline) {
            contextQueue.filter { it.isAvailableOffline }.ifEmpty { listOf(track) }
        } else {
            contextQueue.ifEmpty { listOf(track) }
        }
        playerManager.playTrack(track, playableQueue)
    }

    fun shuffleAndPlay(tracks: List<TrackEntity>) {
        val state = uiState.value
        val candidates = if (state.isEffectivelyOffline) {
            tracks.filter { it.isAvailableOffline }
        } else {
            tracks
        }
        if (candidates.isEmpty()) {
            _snackbarEvents.tryEmit("No playable tracks available in current mode.")
            return
        }
        val shuffled = candidates.shuffled()
        playerManager.setQueueAndPlay(shuffled, 0)
    }

    private fun YouTubeMusicTrack.toAppPlayerEntity(): TrackEntity = TrackEntity(
        id = "yt_$videoId",
        title = title,
        artist = channelName,
        album = "YouTube Music",
        genre = "YouTube Music",
        durationMs = 0L,
        sourceType = TrackSourceType.ONLINE,
        streamUrl = "yt://$videoId",
        artworkResKey = "studio",
        artworkUrl = thumbnailUrl,
        isLiveStream = false,
        isUserAdded = true,
        addedAt = System.currentTimeMillis()
    )

    private fun SpotifyTrack.toAppPlayerEntity(): TrackEntity = TrackEntity(
        id = "sp_$id",
        title = title,
        artist = artist,
        album = album.ifBlank { "Spotify" },
        genre = "Spotify",
        durationMs = durationMs,
        sourceType = TrackSourceType.ONLINE,
        streamUrl = "spotify://track/$id",
        artworkResKey = "electronic",
        artworkUrl = artworkUrl,
        isLiveStream = false,
        isUserAdded = true,
        addedAt = System.currentTimeMillis()
    )

    fun togglePlayPause() = playerManager.togglePlayPause()

    fun seekTo(positionMs: Long) = playerManager.seekTo(positionMs)

    fun skipNext() = playerManager.skipNext()

    fun skipPrevious() = playerManager.skipPrevious()

    fun toggleShuffle() {
        val enabled = playerManager.toggleShuffle()
        viewModelScope.launch { preferencesRepo.setShuffleEnabled(enabled) }
    }

    fun cycleRepeatMode() {
        val mode = playerManager.cycleRepeatMode()
        viewModelScope.launch { preferencesRepo.setRepeatMode(mode) }
    }

    fun setPlaybackSpeed(speed: Float) {
        playerManager.setPlaybackSpeed(speed)
        viewModelScope.launch { preferencesRepo.setPlaybackSpeed(speed) }
    }

    fun setSleepTimer(minutes: Int) {
        playerManager.setSleepTimer(minutes)
        if (minutes > 0) {
            _snackbarEvents.tryEmit("Sleep timer set for $minutes min")
        } else {
            _snackbarEvents.tryEmit("Sleep timer cancelled")
        }
    }

    fun addToQueue(track: TrackEntity) {
        if (!track.isAvailableOffline && uiState.value.isEffectivelyOffline) {
            _snackbarEvents.tryEmit("Cannot queue online stream while offline.")
            return
        }
        playerManager.addToQueue(track)
        _snackbarEvents.tryEmit("Added \"${track.title}\" to queue")
    }

    fun playNextInQueue(track: TrackEntity) {
        if (!track.isAvailableOffline && uiState.value.isEffectivelyOffline) {
            _snackbarEvents.tryEmit("Cannot queue online stream while offline.")
            return
        }
        playerManager.playNextInQueue(track)
        _snackbarEvents.tryEmit("\"${track.title}\" will play next")
    }

    fun removeFromQueue(index: Int) = playerManager.removeFromQueue(index)

    fun jumpToQueueIndex(index: Int) = playerManager.jumpToQueueIndex(index)

    fun clearPlayerError() = playerManager.clearError()

    fun toggleOfflineOnlyMode() {
        val next = !uiState.value.preferences.offlineOnlyMode
        viewModelScope.launch {
            preferencesRepo.setOfflineOnlyMode(next)
            _snackbarEvents.tryEmit(
                if (next) "Offline Mode enabled — playing saved & local audio only"
                else "Online streaming enabled"
            )
        }
    }

    fun toggleDarkTheme() {
        val next = !uiState.value.preferences.darkTheme
        viewModelScope.launch { preferencesRepo.setDarkTheme(next) }
    }

    fun toggleFavorite(track: TrackEntity) {
        viewModelScope.launch {
            repository.toggleFavorite(track.id, track.isFavorite)
        }
    }

    fun downloadTrack(track: TrackEntity) {
        if (!uiState.value.isDeviceOnline) {
            _snackbarEvents.tryEmit("Connect to the internet to download \"${track.title}\".")
            return
        }
        repository.downloadTrackForOffline(track)
        _snackbarEvents.tryEmit("Downloading \"${track.title}\" for offline playback...")
    }

    fun removeDownload(track: TrackEntity) {
        viewModelScope.launch {
            repository.removeOfflineDownload(track)
            _snackbarEvents.tryEmit("Removed offline copy of \"${track.title}\"")
        }
    }

    fun importLocalAudio(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            val count = repository.importLocalAudioUris(uris)
            if (count > 0) {
                selectedLibraryTab.value = LibraryTab.OFFLINE
                _snackbarEvents.tryEmit("Imported $count audio ${if (count == 1) "file" else "files"} for offline playback")
            } else {
                _snackbarEvents.tryEmit("Could not read metadata from selected audio file.")
            }
        }
    }

    fun addCustomStream(
        title: String,
        artist: String,
        url: String,
        genre: String,
        isLive: Boolean,
        playImmediately: Boolean,
    ) {
        viewModelScope.launch {
            val yt = youTubeClient.parseYouTubeUrl(url)
            val sp = spotifyClient.parseSpotifyTrackUrl(url)
            val track = when {
                yt != null -> {
                    val enriched = trackStreamResolver.enrichYouTubeTrack(yt)
                    enriched.copy(
                        title = title.trim().ifBlank { enriched.title },
                        channelName = artist.trim().ifBlank { enriched.channelName }
                    ).toAppPlayerEntity()
                }
                sp != null -> {
                    val enriched = trackStreamResolver.enrichSpotifyTrack(sp)
                    enriched.copy(
                        title = title.trim().ifBlank { enriched.title },
                        artist = artist.trim().ifBlank { enriched.artist }
                    ).toAppPlayerEntity()
                }
                else -> repository.addCustomStream(title, artist, url, genre, isLive)
            }
            if (yt != null || sp != null) repository.upsertTrack(track)
            _snackbarEvents.tryEmit("Added stream \"${track.title}\"")
            if (playImmediately && !uiState.value.isEffectivelyOffline) {
                playerManager.playTrack(track, listOf(track) + uiState.value.onlineTracks)
            }
        }
    }

    fun deleteTrack(track: TrackEntity) {
        viewModelScope.launch {
            repository.deleteUserTrack(track)
            _snackbarEvents.tryEmit("Removed \"${track.title}\"")
        }
    }

    fun createPlaylist(name: String, description: String, coverResKey: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            repository.createPlaylist(name, description, coverResKey)
            _snackbarEvents.tryEmit("Created playlist \"$name\"")
        }
    }

    fun deletePlaylist(playlistId: Long, name: String) {
        viewModelScope.launch {
            repository.deletePlaylist(playlistId)
            _snackbarEvents.tryEmit("Deleted playlist \"$name\"")
        }
    }

    fun addTrackToPlaylist(playlistId: Long, playlistName: String, trackId: String) {
        viewModelScope.launch {
            repository.addTrackToPlaylist(playlistId, trackId)
            _snackbarEvents.tryEmit("Added to \"$playlistName\"")
        }
    }

    fun removeTrackFromPlaylist(playlistId: Long, trackId: String) {
        viewModelScope.launch {
            repository.removeTrackFromPlaylist(playlistId, trackId)
            _snackbarEvents.tryEmit("Removed track from playlist")
        }
    }

    // --- Playlist Import ---

    fun importPlaylistFromUrl(
        url: String,
        customName: String = "",
        onSuccess: (Long) -> Unit = {},
    ) {
        if (uiState.value.isEffectivelyOffline) {
            _snackbarEvents.tryEmit("Turn off Offline Mode to import a playlist URL.")
            return
        }
        viewModelScope.launch {
            _isImportingPlaylist.value = true
            playlistImportClient.importFromUrl(url, customName)
                .onSuccess { payload ->
                    val id = repository.importPlaylistWithTracks(
                        name = payload.name,
                        description = payload.description,
                        coverResKey = payload.coverResKey,
                        tracks = payload.tracks
                    )
                    _isImportingPlaylist.value = false
                    _snackbarEvents.tryEmit("Imported \"${payload.name}\" (${payload.tracks.size} tracks)")
                    onSuccess(id)
                }
                .onFailure { err ->
                    _isImportingPlaylist.value = false
                    _snackbarEvents.tryEmit(err.message ?: "Could not import playlist URL")
                }
        }
    }

    fun importPlaylistFromUri(
        context: Context,
        uri: Uri,
        customName: String = "",
        onSuccess: (Long) -> Unit = {},
    ) {
        viewModelScope.launch {
            _isImportingPlaylist.value = true
            playlistImportClient.importFromUri(context, uri, customName)
                .onSuccess { payload ->
                    val id = repository.importPlaylistWithTracks(
                        name = payload.name,
                        description = payload.description,
                        coverResKey = payload.coverResKey,
                        tracks = payload.tracks
                    )
                    _isImportingPlaylist.value = false
                    _snackbarEvents.tryEmit("Imported \"${payload.name}\" (${payload.tracks.size} tracks)")
                    onSuccess(id)
                }
                .onFailure { err ->
                    _isImportingPlaylist.value = false
                    _snackbarEvents.tryEmit(err.message ?: "Could not read playlist file")
                }
        }
    }

    fun importAudioFilesAsPlaylist(
        playlistName: String,
        uris: List<Uri>,
        onSuccess: (Long) -> Unit = {},
    ) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            _isImportingPlaylist.value = true
            val (playlistId, count) = repository.importLocalAudioFilesAsPlaylist(playlistName, uris)
            _isImportingPlaylist.value = false
            if (playlistId > 0L && count > 0) {
                _snackbarEvents.tryEmit("Created playlist with $count local audio ${if (count == 1) "file" else "files"}")
                onSuccess(playlistId)
            } else {
                _snackbarEvents.tryEmit("Could not read metadata from selected audio files.")
            }
        }
    }

    fun importSpotifyPlaylistByAccount(
        playlist: SpotifyPlaylist,
        onSuccess: (Long) -> Unit = {},
    ) {
        if (uiState.value.isEffectivelyOffline) {
            _snackbarEvents.tryEmit("Turn off Offline Mode to import a Spotify playlist.")
            return
        }
        viewModelScope.launch {
            _isImportingPlaylist.value = true
            spotifyClient.fetchPlaylistTracks(playlist.id)
                .onSuccess { tracks ->
                    if (tracks.isEmpty()) {
                        _isImportingPlaylist.value = false
                        _snackbarEvents.tryEmit("No tracks found in \"${playlist.name}\".")
                        return@onSuccess
                    }
                    val payload = playlistImportClient.fromSpotifyPlaylist(playlist, tracks)
                    val id = repository.importPlaylistWithTracks(
                        name = payload.name,
                        description = payload.description,
                        coverResKey = payload.coverResKey,
                        tracks = payload.tracks
                    )
                    _isImportingPlaylist.value = false
                    _snackbarEvents.tryEmit("Imported \"${payload.name}\" (${payload.tracks.size} tracks)")
                    onSuccess(id)
                }
                .onFailure { err ->
                    _isImportingPlaylist.value = false
                    _snackbarEvents.tryEmit(err.message ?: "Failed to import Spotify playlist")
                }
        }
    }

    // --- All-Countries Global Music Search ---

    fun selectSearchCountry(countryCode: String) {
        _globalSearchState.update { it.copy(selectedCountryCode = countryCode.uppercase()) }
        val query = searchQuery.value.trim()
        if (query.isNotBlank() && !uiState.value.isEffectivelyOffline) {
            executeGlobalSearch(query, countryCode.uppercase())
        }
    }

    fun searchGlobalMusic(overrideQuery: String? = null) {
        val query = (overrideQuery ?: searchQuery.value).trim()
        if (overrideQuery != null) {
            searchQuery.value = overrideQuery
        }
        if (query.isBlank()) {
            _snackbarEvents.tryEmit("Enter a song, artist, or genre to search worldwide.")
            return
        }
        if (uiState.value.isEffectivelyOffline) {
            _snackbarEvents.tryEmit("Turn off Offline Mode to search worldwide music.")
            return
        }
        executeGlobalSearch(query, _globalSearchState.value.selectedCountryCode)
    }

    private fun executeGlobalSearch(query: String, countryCode: String) {
        globalSearchJob?.cancel()
        globalSearchJob = viewModelScope.launch {
            _globalSearchState.update {
                it.copy(isLoading = true, errorMessage = null, lastSearchedQuery = query)
            }
            globalSearchClient.searchMusic(query, countryCode)
                .onSuccess { tracks ->
                    _globalSearchState.update {
                        it.copy(isLoading = false, results = tracks, errorMessage = null)
                    }
                }
                .onFailure { err ->
                    _globalSearchState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = err.message ?: "Global music search failed"
                        )
                    }
                }
        }
    }

    fun playGlobalTrack(track: GlobalMusicTrack) {
        if (uiState.value.isEffectivelyOffline) {
            _snackbarEvents.tryEmit("Turn off Offline Mode to stream global search tracks.")
            return
        }
        val entity = track.toTrackEntity()
        val queueEntities = _globalSearchState.value.results
            .map { it.toTrackEntity() }
            .ifEmpty { listOf(entity) }
        viewModelScope.launch {
            repository.upsertTrack(entity)
        }
        playerManager.playTrack(entity, queueEntities)
    }

    fun playNextGlobalTrack(track: GlobalMusicTrack) {
        val entity = track.toTrackEntity()
        viewModelScope.launch { repository.upsertTrack(entity) }
        playNextInQueue(entity)
    }

    fun addGlobalTrackToQueue(track: GlobalMusicTrack) {
        val entity = track.toTrackEntity()
        viewModelScope.launch { repository.upsertTrack(entity) }
        addToQueue(entity)
    }

    fun downloadGlobalTrack(track: GlobalMusicTrack) {
        val entity = track.toTrackEntity()
        viewModelScope.launch {
            repository.upsertTrack(entity)
            downloadTrack(entity)
        }
    }

    fun saveGlobalTrackAndPickPlaylist(track: GlobalMusicTrack, onReady: (TrackEntity) -> Unit) {
        val entity = track.toTrackEntity()
        viewModelScope.launch {
            repository.upsertTrack(entity)
            onReady(entity)
        }
    }

    private data class DataBundle(
        val all: List<TrackEntity>,
        val offline: List<TrackEntity>,
        val favs: List<TrackEntity>,
        val recent: List<TrackEntity>,
        val playlists: List<PlaylistWithTracks>,
    )

    private data class EnvBundle(
        val downloadProgress: Map<String, Float>,
        val isOnline: Boolean,
        val prefs: UserPreferences,
        val libraryTab: LibraryTab,
        val query: String,
        val filter: SearchFilter,
    )
}
