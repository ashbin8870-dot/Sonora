package com.example.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.sonoraDataStore: DataStore<Preferences> by preferencesDataStore(name = "sonora_prefs")

data class UserPreferences(
    val offlineOnlyMode: Boolean = false,
    val darkTheme: Boolean = true,
    val shuffleEnabled: Boolean = false,
    val repeatMode: Int = 0, // Player.REPEAT_MODE_OFF = 0, ONE = 1, ALL = 2
    val playbackSpeed: Float = 1.0f,
    val lastPlayedTrackId: String? = null,
)

class PreferencesRepository(private val context: Context) {

    private object Keys {
        val OFFLINE_ONLY_MODE = booleanPreferencesKey("offline_only_mode")
        val DARK_THEME = booleanPreferencesKey("dark_theme")
        val SHUFFLE_ENABLED = booleanPreferencesKey("shuffle_enabled")
        val REPEAT_MODE = intPreferencesKey("repeat_mode")
        val PLAYBACK_SPEED = floatPreferencesKey("playback_speed")
        val LAST_PLAYED_TRACK_ID = stringPreferencesKey("last_played_track_id")
    }

    val preferences: Flow<UserPreferences> = context.sonoraDataStore.data.map { prefs ->
        UserPreferences(
            offlineOnlyMode = prefs[Keys.OFFLINE_ONLY_MODE] ?: false,
            darkTheme = prefs[Keys.DARK_THEME] ?: true,
            shuffleEnabled = prefs[Keys.SHUFFLE_ENABLED] ?: false,
            repeatMode = prefs[Keys.REPEAT_MODE] ?: 0,
            playbackSpeed = prefs[Keys.PLAYBACK_SPEED] ?: 1.0f,
            lastPlayedTrackId = prefs[Keys.LAST_PLAYED_TRACK_ID],
        )
    }

    suspend fun setOfflineOnlyMode(enabled: Boolean) {
        context.sonoraDataStore.edit { it[Keys.OFFLINE_ONLY_MODE] = enabled }
    }

    suspend fun setDarkTheme(enabled: Boolean) {
        context.sonoraDataStore.edit { it[Keys.DARK_THEME] = enabled }
    }

    suspend fun setShuffleEnabled(enabled: Boolean) {
        context.sonoraDataStore.edit { it[Keys.SHUFFLE_ENABLED] = enabled }
    }

    suspend fun setRepeatMode(mode: Int) {
        context.sonoraDataStore.edit { it[Keys.REPEAT_MODE] = mode }
    }

    suspend fun setPlaybackSpeed(speed: Float) {
        context.sonoraDataStore.edit { it[Keys.PLAYBACK_SPEED] = speed }
    }

    suspend fun setLastPlayedTrackId(trackId: String?) {
        context.sonoraDataStore.edit {
            if (trackId == null) it.remove(Keys.LAST_PLAYED_TRACK_ID)
            else it[Keys.LAST_PLAYED_TRACK_ID] = trackId
        }
    }
}
