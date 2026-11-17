package com.example.ui.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.SonoraApplication
import com.example.data.local.UserPreferences
import com.example.data.model.PlaylistWithTracks
import com.example.data.model.TrackEntity
import com.example.player.PlayerUiState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
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

class MusicViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as SonoraApplication
    private val repository = app.musicRepository
    private val preferencesRepo = app.preferencesRepository
    private val networkMonitor = app.networkMonitor
    private val playerManager = app.playerManager

    private val selectedLibraryTab = MutableStateFlow(LibraryTab.OFFLINE)
    private val searchQuery = MutableStateFlow("")
    private val selectedSearchFilter = MutableStateFlow(SearchFilter.ALL)

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
            playerManager.connect()
        }
    }

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
            contextQueue
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

    fun cancelSleepTimer() {
        playerManager.cancelSleepTimer()
        _snackbarEvents.tryEmit("Sleep timer cancelled")
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
            val track = repository.addCustomStream(title, artist, url, genre, isLive)
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
