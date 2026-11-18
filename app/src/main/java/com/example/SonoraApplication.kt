package com.example

import android.app.Application
import com.example.data.local.AppDatabase
import com.example.data.local.PreferencesRepository
import com.example.data.remote.GlobalMusicSearchClient
import com.example.data.remote.PlaylistImportClient
import com.example.data.remote.SpotifyClient
import com.example.data.remote.YouTubeMusicClient
import com.example.data.repository.MusicRepository
import com.example.data.repository.NetworkMonitor
import com.example.player.PlayerManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class SonoraApplication : Application() {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val database: AppDatabase by lazy { AppDatabase.getInstance(this) }
    val preferencesRepository: PreferencesRepository by lazy { PreferencesRepository(this) }
    val networkMonitor: NetworkMonitor by lazy { NetworkMonitor(this) }
    val spotifyClient: SpotifyClient by lazy { SpotifyClient(preferencesRepository) }
    val youTubeMusicClient: YouTubeMusicClient by lazy { YouTubeMusicClient() }
    val globalMusicSearchClient: GlobalMusicSearchClient by lazy { GlobalMusicSearchClient() }
    val playlistImportClient: PlaylistImportClient by lazy {
        PlaylistImportClient(
            youTubeMusicClient = youTubeMusicClient,
            spotifyClient = spotifyClient
        )
    }
    val musicRepository: MusicRepository by lazy {
        MusicRepository(
            context = this,
            trackDao = database.trackDao(),
            playlistDao = database.playlistDao()
        )
    }

    val playerManager: PlayerManager by lazy {
        PlayerManager(
            context = this,
            onTrackPlayed = { trackId ->
                appScope.launch {
                    musicRepository.markPlayed(trackId)
                    preferencesRepository.setLastPlayedTrackId(trackId)
                }
            }
        )
    }

    override fun onCreate() {
        super.onCreate()
        appScope.launch {
            musicRepository.ensureSeeded()
        }
    }
}
