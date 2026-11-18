package com.example.player

import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.example.MainActivity
import com.example.R
import com.example.SonoraApplication
import com.example.data.remote.TrackStreamResolver
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class PlaybackService : MediaSessionService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var mediaSession: MediaSession? = null

    private val sessionCallback = object : MediaSession.Callback {
        @OptIn(UnstableApi::class)
        override fun onPlaybackResumption(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            isForPlayback: Boolean,
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
            val future = SettableFuture.create<MediaSession.MediaItemsWithStartPosition>()
            serviceScope.launch {
                try {
                    val app = applicationContext as SonoraApplication
                    val prefs = app.preferencesRepository.preferences.first()
                    val lastId = prefs.lastPlayedTrackId
                    val track = lastId?.let { app.database.trackDao().getTrackById(it) }
                    if (track != null) {
                        val item = MediaItemMapper.toMediaItem(applicationContext, track)
                        future.set(MediaSession.MediaItemsWithStartPosition(listOf(item), 0, 0L))
                    } else {
                        future.setException(IllegalStateException("No recent track to resume"))
                    }
                } catch (e: Exception) {
                    future.setException(e)
                }
            }
            return future
        }
    }

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()

        val notificationProvider = DefaultMediaNotificationProvider.Builder(this)
            .setChannelName(R.string.notification_channel_name)
            .build()
            .apply {
                setSmallIcon(R.drawable.ic_notification_music)
            }
        setMediaNotificationProvider(notificationProvider)

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()

        val app = applicationContext as SonoraApplication
        val okHttpDataSourceFactory = OkHttpStreamDataSource.Factory(
            client = app.trackStreamResolver.streamHttpClient,
            defaultUserAgent = TrackStreamResolver.EXOPLAYER_USER_AGENT
        )
        val upstreamFactory = DefaultDataSource.Factory(this, okHttpDataSourceFactory)
        val resolvingFactory = ResolvingDataSource.Factory(upstreamFactory) { dataSpec ->
            val uri = dataSpec.uri
            if (uri.scheme == "yt" || uri.scheme == "spotify") {
                val resolved = app.trackStreamResolver.resolvePlayableStreamBlocking(uri)
                val builder = dataSpec.buildUpon().setUri(Uri.parse(resolved.url))
                if (resolved.headers.isNotEmpty()) {
                    builder.setHttpRequestHeaders(resolved.headers)
                }
                builder.build()
            } else {
                dataSpec
            }
        }

        val player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(resolvingFactory))
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()

        // Apply saved playback preferences on service creation
        serviceScope.launch(Dispatchers.Main) {
            val app = applicationContext as? SonoraApplication ?: return@launch
            val prefs = app.preferencesRepository.preferences.first()
            player.repeatMode = prefs.repeatMode
            player.shuffleModeEnabled = prefs.shuffleEnabled
            player.playbackParameters = PlaybackParameters(prefs.playbackSpeed.coerceIn(0.5f, 2.0f))
        }

        val launchIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val sessionActivityPendingIntent = PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(sessionActivityPendingIntent)
            .setCallback(sessionCallback)
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        mediaSession?.player?.let { player ->
            player.playWhenReady = false
            player.pause()
            player.stop()
            player.clearMediaItems()
        }
        @Suppress("DEPRECATION")
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        serviceScope.cancel()
        @Suppress("DEPRECATION")
        stopForeground(STOP_FOREGROUND_REMOVE)
        mediaSession?.run {
            player.stop()
            player.release()
            release()
            mediaSession = null
        }
        super.onDestroy()
    }
}
