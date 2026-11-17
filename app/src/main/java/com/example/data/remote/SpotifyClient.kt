package com.example.data.remote

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Base64
import com.example.BuildConfig
import com.example.data.local.PreferencesRepository
import com.example.data.model.TrackEntity
import com.example.data.model.TrackSourceType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.TimeUnit

@Serializable
data class SpotifyImageDto(
    val url: String = "",
)

@Serializable
data class SpotifyUserDto(
    val id: String = "",
    @SerialName("display_name") val displayName: String? = null,
    val product: String? = null,
    val images: List<SpotifyImageDto> = emptyList(),
)

@Serializable
data class SpotifyArtistDto(
    val name: String = "",
)

@Serializable
data class SpotifyAlbumDto(
    val name: String = "",
    val images: List<SpotifyImageDto> = emptyList(),
)

@Serializable
data class SpotifyExternalUrlsDto(
    val spotify: String? = null,
)

@Serializable
data class SpotifyTrackDto(
    val id: String = "",
    val name: String = "",
    val uri: String = "",
    @SerialName("duration_ms") val durationMs: Long = 0L,
    @SerialName("preview_url") val previewUrl: String? = null,
    val artists: List<SpotifyArtistDto> = emptyList(),
    val album: SpotifyAlbumDto = SpotifyAlbumDto(),
    @SerialName("external_urls") val externalUrls: SpotifyExternalUrlsDto = SpotifyExternalUrlsDto(),
)

@Serializable
data class SpotifyPlaylistTracksRefDto(
    val total: Int = 0,
)

@Serializable
data class SpotifyPlaylistDto(
    val id: String = "",
    val name: String = "",
    val description: String? = null,
    val uri: String = "",
    val images: List<SpotifyImageDto> = emptyList(),
    val tracks: SpotifyPlaylistTracksRefDto = SpotifyPlaylistTracksRefDto(),
    @SerialName("external_urls") val externalUrls: SpotifyExternalUrlsDto = SpotifyExternalUrlsDto(),
)

@Serializable
private data class SpotifyTokenResponseDto(
    @SerialName("access_token") val accessToken: String = "",
    @SerialName("refresh_token") val refreshToken: String? = null,
    @SerialName("expires_in") val expiresIn: Long = 3600L,
)

@Serializable
private data class SpotifyPlaylistsPageDto(
    val items: List<SpotifyPlaylistDto> = emptyList(),
)

@Serializable
private data class SpotifySavedTrackWrapperDto(
    val track: SpotifyTrackDto? = null,
)

@Serializable
private data class SpotifySavedTracksPageDto(
    val items: List<SpotifySavedTrackWrapperDto> = emptyList(),
)

@Serializable
private data class SpotifySearchResponseDto(
    val tracks: SpotifyTrackItemsWrapperDto = SpotifyTrackItemsWrapperDto(),
)

@Serializable
private data class SpotifyTrackItemsWrapperDto(
    val items: List<SpotifyTrackDto> = emptyList(),
)

@Serializable
private data class SpotifyDeviceDto(
    val name: String = "",
    val type: String = "",
)

@Serializable
private data class SpotifyPlaybackStateDto(
    @SerialName("is_playing") val isPlaying: Boolean = false,
    val device: SpotifyDeviceDto? = null,
    val item: SpotifyTrackDto? = null,
)

data class SpotifyUserProfile(
    val id: String,
    val displayName: String,
    val productTier: String,
    val avatarUrl: String?,
)

data class SpotifyPlaylist(
    val id: String,
    val name: String,
    val description: String,
    val trackCount: Int,
    val imageUrl: String?,
    val uri: String,
    val externalUrl: String?,
)

data class SpotifyTrack(
    val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val artworkUrl: String?,
    val previewUrl: String?,
    val spotifyUri: String,
    val externalUrl: String?,
) {
    val embedUrl: String
        get() = "https://open.spotify.com/embed/track/$id?utm_source=generator&theme=0"

    fun toPreviewTrackEntity(): TrackEntity? {
        val url = previewUrl?.takeIf { it.isNotBlank() } ?: return null
        return TrackEntity(
            id = "spotify_preview_$id",
            title = "$title (Preview)",
            artist = artist,
            album = album,
            genre = "Spotify",
            durationMs = 30_000L.coerceAtMost(if (durationMs > 0L) durationMs else 30_000L),
            sourceType = TrackSourceType.ONLINE,
            streamUrl = url,
            artworkResKey = "electronic",
            artworkUrl = artworkUrl,
            isLiveStream = false,
            isUserAdded = false,
        )
    }
}

data class SpotifyConnectState(
    val isPlaying: Boolean,
    val deviceName: String,
    val trackTitle: String?,
    val artistName: String?,
)

class SpotifyClient(
    private val preferencesRepository: PreferencesRepository,
) {
    companion object {
        const val REDIRECT_URI = "sonora://spotify-callback"
        private const val AUTH_URL = "https://accounts.spotify.com/authorize"
        private const val TOKEN_URL = "https://accounts.spotify.com/api/token"
        private const val API_BASE = "https://api.spotify.com/v1"
        private const val SCOPES =
            "user-read-private user-library-read playlist-read-private playlist-read-collaborative " +
                "user-read-playback-state user-modify-playback-state"
    }

    private val json = Json { ignoreUnknownKeys = true }
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    val clientId: String
        get() = BuildConfig.SPOTIFY_CLIENT_ID

    val isConfigured: Boolean
        get() = clientId.isNotBlank() && clientId != "YOUR_SPOTIFY_CLIENT_ID"

    suspend fun createAuthorizationIntent(): Intent {
        val verifier = generateCodeVerifier()
        preferencesRepository.saveSpotifyCodeVerifier(verifier)
        val challenge = generateCodeChallenge(verifier)

        val uri = Uri.parse(AUTH_URL).buildUpon()
            .appendQueryParameter("client_id", clientId)
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("redirect_uri", REDIRECT_URI)
            .appendQueryParameter("code_challenge_method", "S256")
            .appendQueryParameter("code_challenge", challenge)
            .appendQueryParameter("scope", SCOPES)
            .build()

        return Intent(Intent.ACTION_VIEW, uri)
    }

    suspend fun handleAuthCallback(callbackUri: Uri): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val error = callbackUri.getQueryParameter("error")
            if (!error.isNullOrBlank()) {
                error("Spotify authorization declined: $error")
            }
            val code = callbackUri.getQueryParameter("code")
                ?: error("Missing Spotify authorization code")
            val verifier = preferencesRepository.preferences.first().spotifyCodeVerifier
                ?: error("Missing PKCE verifier; please start sign-in again.")

            val body = FormBody.Builder()
                .add("client_id", clientId)
                .add("grant_type", "authorization_code")
                .add("code", code)
                .add("redirect_uri", REDIRECT_URI)
                .add("code_verifier", verifier)
                .build()

            val request = Request.Builder()
                .url(TOKEN_URL)
                .post(body)
                .build()

            client.newCall(request).execute().use { response ->
                val raw = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    error("Spotify token exchange failed (${response.code})")
                }
                val token = json.decodeFromString<SpotifyTokenResponseDto>(raw)
                preferencesRepository.saveSpotifyTokens(
                    accessToken = token.accessToken,
                    refreshToken = token.refreshToken,
                    expiresInSeconds = token.expiresIn
                )
            }
        }
    }

    private suspend fun getValidAccessToken(): String = withContext(Dispatchers.IO) {
        val prefs = preferencesRepository.preferences.first()
        val currentToken = prefs.spotifyAccessToken
        val refreshToken = prefs.spotifyRefreshToken
        if (!currentToken.isNullOrBlank() && System.currentTimeMillis() < prefs.spotifyTokenExpiryMs) {
            return@withContext currentToken
        }
        if (refreshToken.isNullOrBlank()) {
            error("Not signed in to Spotify")
        }
        val body = FormBody.Builder()
            .add("client_id", clientId)
            .add("grant_type", "refresh_token")
            .add("refresh_token", refreshToken)
            .build()
        val request = Request.Builder().url(TOKEN_URL).post(body).build()
        client.newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                preferencesRepository.clearSpotifySession()
                error("Spotify session expired. Please reconnect.")
            }
            val refreshed = json.decodeFromString<SpotifyTokenResponseDto>(raw)
            preferencesRepository.saveSpotifyTokens(
                accessToken = refreshed.accessToken,
                refreshToken = refreshed.refreshToken ?: refreshToken,
                expiresInSeconds = refreshed.expiresIn
            )
            refreshed.accessToken
        }
    }

    suspend fun fetchCurrentUser(): Result<SpotifyUserProfile> = withContext(Dispatchers.IO) {
        runCatching {
            val raw = authorizedGet("$API_BASE/me")
            val dto = json.decodeFromString<SpotifyUserDto>(raw)
            SpotifyUserProfile(
                id = dto.id,
                displayName = dto.displayName?.takeIf { it.isNotBlank() } ?: dto.id,
                productTier = dto.product?.uppercase() ?: "FREE",
                avatarUrl = dto.images.firstOrNull()?.url
            )
        }
    }

    suspend fun fetchUserPlaylists(): Result<List<SpotifyPlaylist>> = withContext(Dispatchers.IO) {
        runCatching {
            val raw = authorizedGet("$API_BASE/me/playlists?limit=20")
            val page = json.decodeFromString<SpotifyPlaylistsPageDto>(raw)
            page.items.map { dto ->
                SpotifyPlaylist(
                    id = dto.id,
                    name = dto.name,
                    description = dto.description.orEmpty(),
                    trackCount = dto.tracks.total,
                    imageUrl = dto.images.firstOrNull()?.url,
                    uri = dto.uri,
                    externalUrl = dto.externalUrls.spotify
                )
            }
        }
    }

    suspend fun fetchSavedTracks(): Result<List<SpotifyTrack>> = withContext(Dispatchers.IO) {
        runCatching {
            val raw = authorizedGet("$API_BASE/me/tracks?limit=25")
            val page = json.decodeFromString<SpotifySavedTracksPageDto>(raw)
            page.items.mapNotNull { it.track?.toModel() }
        }
    }

    suspend fun fetchPlaylistTracks(playlistId: String): Result<List<SpotifyTrack>> = withContext(Dispatchers.IO) {
        runCatching {
            val raw = authorizedGet("$API_BASE/playlists/$playlistId/tracks?limit=30")
            val page = json.decodeFromString<SpotifySavedTracksPageDto>(raw)
            page.items.mapNotNull { it.track?.toModel() }
        }
    }

    suspend fun searchTracks(query: String): Result<List<SpotifyTrack>> = withContext(Dispatchers.IO) {
        runCatching {
            val encoded = Uri.encode(query.trim())
            val raw = authorizedGet("$API_BASE/search?q=$encoded&type=track&limit=20")
            val resp = json.decodeFromString<SpotifySearchResponseDto>(raw)
            resp.tracks.items.map { it.toModel() }
        }
    }

    suspend fun fetchPlaybackState(): Result<SpotifyConnectState?> = withContext(Dispatchers.IO) {
        runCatching {
            val token = getValidAccessToken()
            val request = Request.Builder()
                .url("$API_BASE/me/player")
                .header("Authorization", "Bearer $token")
                .build()
            client.newCall(request).execute().use { response ->
                if (response.code == 204) return@runCatching null
                val raw = response.body?.string().orEmpty()
                if (!response.isSuccessful) error("Spotify Connect status unavailable (${response.code})")
                val dto = json.decodeFromString<SpotifyPlaybackStateDto>(raw)
                SpotifyConnectState(
                    isPlaying = dto.isPlaying,
                    deviceName = dto.device?.name ?: "Spotify Device",
                    trackTitle = dto.item?.name,
                    artistName = dto.item?.artists?.joinToString(", ") { it.name }
                )
            }
        }
    }

    suspend fun playOnSpotifyConnect(trackUri: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val payload = """{"uris":["$trackUri"]}"""
                .toRequestBody("application/json; charset=utf-8".toMediaType())
            authorizedCommand("PUT", "$API_BASE/me/player/play", payload)
        }
    }

    suspend fun pauseSpotifyConnect(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            authorizedCommand("PUT", "$API_BASE/me/player/pause", ByteArray(0).toRequestBody(null))
        }
    }

    suspend fun resumeSpotifyConnect(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            authorizedCommand("PUT", "$API_BASE/me/player/play", ByteArray(0).toRequestBody(null))
        }
    }

    suspend fun skipNextSpotifyConnect(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            authorizedCommand("POST", "$API_BASE/me/player/next", ByteArray(0).toRequestBody(null))
        }
    }

    suspend fun disconnect() {
        preferencesRepository.clearSpotifySession()
    }

    fun parseSpotifyTrackUrl(input: String): SpotifyTrack? {
        val trimmed = input.trim()
        if (trimmed.isBlank()) return null
        val trackId = when {
            trimmed.startsWith("spotify:track:") -> trimmed.removePrefix("spotify:track:").substringBefore('?')
            trimmed.contains("open.spotify.com/track/") ->
                trimmed.substringAfter("open.spotify.com/track/").substringBefore('?').substringBefore('/')
            else -> null
        }?.takeIf { it.isNotBlank() } ?: return null

        return SpotifyTrack(
            id = trackId,
            title = "Spotify Track",
            artist = "Spotify Stream",
            album = "In-App Player",
            durationMs = 0L,
            artworkUrl = null,
            previewUrl = null,
            spotifyUri = "spotify:track:$trackId",
            externalUrl = "https://open.spotify.com/track/$trackId"
        )
    }

    private suspend fun authorizedGet(url: String): String {
        val token = getValidAccessToken()
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $token")
            .build()
        client.newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                error("Spotify API error (${response.code})")
            }
            return raw
        }
    }

    private suspend fun authorizedCommand(
        method: String,
        url: String,
        body: okhttp3.RequestBody,
    ) {
        val token = getValidAccessToken()
        val request = Request.Builder()
            .url(url)
            .method(method, body)
            .header("Authorization", "Bearer $token")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful && response.code != 204) {
                val msg = when (response.code) {
                    403 -> "Spotify Connect playback control requires Spotify Premium."
                    404 -> "No active Spotify device found. Open Spotify on a device first."
                    else -> "Spotify Connect command failed (${response.code})"
                }
                error(msg)
            }
        }
    }

    private fun SpotifyTrackDto.toModel(): SpotifyTrack = SpotifyTrack(
        id = id,
        title = name,
        artist = artists.joinToString(", ") { it.name }.ifBlank { "Spotify Artist" },
        album = album.name,
        durationMs = durationMs,
        artworkUrl = album.images.firstOrNull()?.url,
        previewUrl = previewUrl,
        spotifyUri = uri,
        externalUrl = externalUrls.spotify
    )

    private fun generateCodeVerifier(): String {
        val bytes = ByteArray(64)
        SecureRandom().nextBytes(bytes)
        return Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }

    private fun generateCodeChallenge(verifier: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII))
        return Base64.encodeToString(digest, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }
}
