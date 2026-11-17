package com.example.player

import android.content.ComponentName
import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.example.data.model.TrackEntity
import com.example.data.model.TrackSourceType
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class PlayerUiState(
    val currentTrack: TrackEntity? = null,
    val queue: List<TrackEntity> = emptyList(),
    val currentQueueIndex: Int = -1,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val currentPositionMs: Long = 0L,
    val durationMs: Long = 0L,
    val shuffleEnabled: Boolean = false,
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
    val playbackSpeed: Float = 1.0f,
    val sleepTimerRemainingMs: Long? = null,
    val isConnected: Boolean = false,
    val errorMessage: String? = null,
)

class PlayerManager(
    private val context: Context,
    private val onTrackPlayed: (String) -> Unit,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var mediaController: MediaController? = null

    private var positionPollingJob: Job? = null
    private var sleepTimerJob: Job? = null
    private var pendingTracks: Pair<List<TrackEntity>, Int>? = null

    private val queueLookup = mutableMapOf<String, TrackEntity>()

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _uiState.update { it.copy(isPlaying = isPlaying) }
            if (isPlaying) startPositionPolling() else stopPositionPolling()
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            val buffering = playbackState == Player.STATE_BUFFERING
            val controller = mediaController
            val duration = controller?.duration?.takeIf { it > 0L }
                ?: _uiState.value.currentTrack?.durationMs
                ?: 0L
            _uiState.update {
                it.copy(
                    isBuffering = buffering,
                    durationMs = duration,
                    currentPositionMs = controller?.currentPosition?.coerceAtLeast(0L) ?: it.currentPositionMs
                )
            }
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            val controller = mediaController ?: return
            val index = controller.currentMediaItemIndex
            val track = resolveTrackFromMediaItem(mediaItem, index)
            if (track != null) {
                onTrackPlayed(track.id)
            }
            val duration = controller.duration.takeIf { it > 0L } ?: track?.durationMs ?: 0L
            _uiState.update {
                it.copy(
                    currentTrack = track,
                    currentQueueIndex = index,
                    currentPositionMs = 0L,
                    durationMs = duration,
                    errorMessage = null
                )
            }
        }

        override fun onPositionDiscontinuity(
            oldPosition: Player.PositionInfo,
            newPosition: Player.PositionInfo,
            reason: Int,
        ) {
            _uiState.update {
                it.copy(currentPositionMs = newPosition.positionMs.coerceAtLeast(0L))
            }
        }

        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
            _uiState.update { it.copy(shuffleEnabled = shuffleModeEnabled) }
        }

        override fun onRepeatModeChanged(repeatMode: Int) {
            _uiState.update { it.copy(repeatMode = repeatMode) }
        }

        override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) {
            _uiState.update { it.copy(playbackSpeed = playbackParameters.speed) }
        }

        override fun onPlayerError(error: PlaybackException) {
            val track = _uiState.value.currentTrack
            val msg = when {
                track != null && !track.isAvailableOffline ->
                    "Unable to stream \"${track.title}\". Check your connection or try an offline track."
                else ->
                    "Playback error: ${error.localizedMessage ?: "Unsupported audio format"}"
            }
            _uiState.update {
                it.copy(
                    isPlaying = false,
                    isBuffering = false,
                    errorMessage = msg
                )
            }
        }
    }

    fun connect() {
        if (controllerFuture != null) return
        val sessionToken = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, sessionToken).buildAsync()
        controllerFuture = future
        future.addListener(
            {
                runCatching {
                    val controller = future.get()
                    mediaController = controller
                    controller.addListener(playerListener)
                    _uiState.update { it.copy(isConnected = true) }

                    pendingTracks?.let { (tracks, startIndex) ->
                        pendingTracks = null
                        setQueueAndPlay(tracks, startIndex)
                    } ?: syncFromController(controller)
                }.onFailure {
                    controllerFuture = null
                    _uiState.update { it.copy(isConnected = false) }
                }
            },
            MoreExecutors.directExecutor()
        )
    }

    fun disconnect() {
        stopPositionPolling()
        mediaController?.removeListener(playerListener)
        mediaController = null
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controllerFuture = null
        _uiState.update { it.copy(isConnected = false) }
    }

    private fun syncFromController(controller: MediaController) {
        val restoredQueue = if (controller.mediaItemCount > 0) {
            (0 until controller.mediaItemCount).mapNotNull { idx ->
                resolveTrackFromMediaItem(controller.getMediaItemAt(idx), idx)
            }
        } else {
            _uiState.value.queue
        }

        val currentIndex = controller.currentMediaItemIndex
        val currentTrack = resolveTrackFromMediaItem(controller.currentMediaItem, currentIndex)
            ?: restoredQueue.getOrNull(currentIndex)
            ?: _uiState.value.currentTrack

        _uiState.update {
            it.copy(
                currentTrack = currentTrack,
                queue = restoredQueue,
                currentQueueIndex = currentIndex,
                isPlaying = controller.isPlaying,
                isBuffering = controller.playbackState == Player.STATE_BUFFERING,
                currentPositionMs = controller.currentPosition.coerceAtLeast(0L),
                durationMs = controller.duration.takeIf { d -> d > 0L } ?: currentTrack?.durationMs ?: 0L,
                shuffleEnabled = controller.shuffleModeEnabled,
                repeatMode = controller.repeatMode,
                playbackSpeed = controller.playbackParameters.speed,
                isConnected = true,
            )
        }
        if (controller.isPlaying) startPositionPolling()
    }

    private fun resolveTrackFromMediaItem(mediaItem: MediaItem?, index: Int): TrackEntity? {
        if (mediaItem == null) return _uiState.value.queue.getOrNull(index)
        queueLookup[mediaItem.mediaId]?.let { return it }
        _uiState.value.queue.getOrNull(index)?.let { if (it.id == mediaItem.mediaId) return it }
        val meta = mediaItem.mediaMetadata
        val title = meta.title?.toString() ?: return null
        return TrackEntity(
            id = mediaItem.mediaId,
            title = title,
            artist = meta.artist?.toString().orEmpty().ifBlank { "Unknown Artist" },
            album = meta.albumTitle?.toString().orEmpty(),
            genre = meta.genre?.toString().orEmpty().ifBlank { "Audio" },
            durationMs = 0L,
            sourceType = TrackSourceType.ONLINE,
        )
    }

    fun playTrack(track: TrackEntity, queue: List<TrackEntity> = listOf(track)) {
        val effectiveQueue = if (queue.any { it.id == track.id }) queue else listOf(track) + queue
        val startIndex = effectiveQueue.indexOfFirst { it.id == track.id }.coerceAtLeast(0)
        setQueueAndPlay(effectiveQueue, startIndex)
    }

    fun setQueueAndPlay(tracks: List<TrackEntity>, startIndex: Int = 0) {
        if (tracks.isEmpty()) return
        val safeIndex = startIndex.coerceIn(0, tracks.lastIndex)
        tracks.forEach { queueLookup[it.id] = it }
        val targetTrack = tracks[safeIndex]

        val controller = mediaController
        if (controller == null) {
            pendingTracks = tracks to safeIndex
            _uiState.update {
                it.copy(
                    currentTrack = targetTrack,
                    queue = tracks,
                    currentQueueIndex = safeIndex,
                    isBuffering = true,
                    errorMessage = null
                )
            }
            connect()
            return
        }

        val mediaItems = tracks.map { MediaItemMapper.toMediaItem(context, it) }
        _uiState.update {
            it.copy(
                currentTrack = targetTrack,
                queue = tracks,
                currentQueueIndex = safeIndex,
                currentPositionMs = 0L,
                durationMs = targetTrack.durationMs,
                errorMessage = null
            )
        }
        onTrackPlayed(targetTrack.id)
        controller.setMediaItems(mediaItems, safeIndex, 0L)
        controller.prepare()
        controller.play()
    }

    fun addToQueue(track: TrackEntity) {
        queueLookup[track.id] = track
        val currentQueue = _uiState.value.queue
        if (currentQueue.isEmpty()) {
            setQueueAndPlay(listOf(track), 0)
            return
        }
        val updatedQueue = currentQueue + track
        _uiState.update { it.copy(queue = updatedQueue) }
        mediaController?.addMediaItem(MediaItemMapper.toMediaItem(context, track))
    }

    fun playNextInQueue(track: TrackEntity) {
        queueLookup[track.id] = track
        val currentQueue = _uiState.value.queue
        if (currentQueue.isEmpty()) {
            setQueueAndPlay(listOf(track), 0)
            return
        }
        val insertIndex = (_uiState.value.currentQueueIndex + 1).coerceIn(0, currentQueue.size)
        val updatedQueue = currentQueue.toMutableList().apply { add(insertIndex, track) }
        _uiState.update { it.copy(queue = updatedQueue) }
        mediaController?.addMediaItem(insertIndex, MediaItemMapper.toMediaItem(context, track))
    }

    fun removeFromQueue(index: Int) {
        val currentQueue = _uiState.value.queue
        if (index !in currentQueue.indices) return
        if (index == _uiState.value.currentQueueIndex && currentQueue.size == 1) {
            mediaController?.stop()
            mediaController?.clearMediaItems()
            _uiState.update {
                it.copy(
                    currentTrack = null,
                    queue = emptyList(),
                    currentQueueIndex = -1,
                    isPlaying = false,
                    currentPositionMs = 0L,
                    durationMs = 0L
                )
            }
            return
        }
        val updated = currentQueue.toMutableList().apply { removeAt(index) }
        val newCurrentIdx = when {
            index < _uiState.value.currentQueueIndex -> _uiState.value.currentQueueIndex - 1
            else -> _uiState.value.currentQueueIndex.coerceAtMost(updated.lastIndex)
        }
        _uiState.update {
            it.copy(
                queue = updated,
                currentQueueIndex = newCurrentIdx,
                currentTrack = updated.getOrNull(newCurrentIdx)
            )
        }
        mediaController?.removeMediaItem(index)
    }

    fun jumpToQueueIndex(index: Int) {
        val controller = mediaController ?: return
        val target = _uiState.value.queue.getOrNull(index) ?: return
        controller.seekToDefaultPosition(index)
        controller.play()
        _uiState.update {
            it.copy(
                currentTrack = target,
                currentQueueIndex = index,
                currentPositionMs = 0L,
                durationMs = target.durationMs
            )
        }
    }

    fun togglePlayPause() {
        val controller = mediaController ?: return
        if (controller.isPlaying) {
            controller.pause()
        } else {
            if (controller.playbackState == Player.STATE_IDLE && _uiState.value.currentTrack != null) {
                controller.prepare()
            }
            controller.play()
        }
    }

    fun seekTo(positionMs: Long) {
        mediaController?.seekTo(positionMs.coerceAtLeast(0L))
        _uiState.update { it.copy(currentPositionMs = positionMs.coerceAtLeast(0L)) }
    }

    fun skipNext() {
        val controller = mediaController ?: return
        if (controller.hasNextMediaItem()) {
            controller.seekToNextMediaItem()
            controller.play()
        } else if (_uiState.value.queue.isNotEmpty()) {
            controller.seekToDefaultPosition(0)
            controller.play()
        }
    }

    fun skipPrevious() {
        val controller = mediaController ?: return
        if (controller.currentPosition > 3_500L && _uiState.value.currentTrack?.isLiveStream != true) {
            controller.seekTo(0L)
        } else if (controller.hasPreviousMediaItem()) {
            controller.seekToPreviousMediaItem()
            controller.play()
        } else {
            controller.seekTo(0L)
        }
    }

    fun toggleShuffle(): Boolean {
        val next = !_uiState.value.shuffleEnabled
        mediaController?.shuffleModeEnabled = next
        _uiState.update { it.copy(shuffleEnabled = next) }
        return next
    }

    fun cycleRepeatMode(): Int {
        val next = when (_uiState.value.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
        mediaController?.repeatMode = next
        _uiState.update { it.copy(repeatMode = next) }
        return next
    }

    fun setPlaybackSpeed(speed: Float) {
        val clamped = speed.coerceIn(0.5f, 2.0f)
        mediaController?.playbackParameters = PlaybackParameters(clamped)
        _uiState.update { it.copy(playbackSpeed = clamped) }
    }

    fun setSleepTimer(minutes: Int) {
        sleepTimerJob?.cancel()
        if (minutes <= 0) {
            _uiState.update { it.copy(sleepTimerRemainingMs = null) }
            return
        }
        val totalMs = minutes * 60_000L
        _uiState.update { it.copy(sleepTimerRemainingMs = totalMs) }
        sleepTimerJob = scope.launch {
            var remaining = totalMs
            while (remaining > 0 && isActive) {
                delay(1_000L)
                remaining -= 1_000L
                _uiState.update { it.copy(sleepTimerRemainingMs = remaining.coerceAtLeast(0L)) }
            }
            if (isActive) {
                mediaController?.pause()
                _uiState.update { it.copy(sleepTimerRemainingMs = null) }
            }
        }
    }

    fun cancelSleepTimer() {
        sleepTimerJob?.cancel()
        _uiState.update { it.copy(sleepTimerRemainingMs = null) }
    }

    fun refreshTrackMetadata(updatedTracks: List<TrackEntity>) {
        updatedTracks.forEach { queueLookup[it.id] = it }
        _uiState.update { state ->
            val updatedCurrent = state.currentTrack?.let { cur ->
                updatedTracks.find { it.id == cur.id } ?: cur
            }
            val updatedQueue = state.queue.map { q ->
                updatedTracks.find { it.id == q.id } ?: q
            }
            state.copy(currentTrack = updatedCurrent, queue = updatedQueue)
        }
    }

    fun showError(message: String) {
        _uiState.update { it.copy(errorMessage = message) }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun release() {
        sleepTimerJob?.cancel()
        disconnect()
    }

    private fun startPositionPolling() {
        if (positionPollingJob?.isActive == true) return
        positionPollingJob = scope.launch {
            while (isActive) {
                val controller = mediaController
                if (controller != null && controller.isPlaying) {
                    val pos = controller.currentPosition.coerceAtLeast(0L)
                    val dur = controller.duration.takeIf { it > 0L }
                        ?: _uiState.value.currentTrack?.durationMs
                        ?: _uiState.value.durationMs
                    _uiState.update {
                        it.copy(currentPositionMs = pos, durationMs = dur)
                    }
                }
                delay(250L)
            }
        }
    }

    private fun stopPositionPolling() {
        positionPollingJob?.cancel()
        positionPollingJob = null
    }
}
