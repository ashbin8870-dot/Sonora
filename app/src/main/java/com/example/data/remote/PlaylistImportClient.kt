package com.example.data.remote

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.example.data.model.TrackEntity
import com.example.data.model.TrackSourceType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

data class ImportedPlaylistPayload(
    val name: String,
    val description: String,
    val coverResKey: String,
    val tracks: List<TrackEntity>,
)

class PlaylistImportClient(
    private val youTubeMusicClient: YouTubeMusicClient,
    private val spotifyClient: SpotifyClient,
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    suspend fun importFromUrl(
        rawInput: String,
        customName: String? = null,
    ): Result<ImportedPlaylistPayload> = withContext(Dispatchers.IO) {
        runCatching {
            val trimmed = rawInput.trim()
            require(trimmed.isNotBlank()) { "Enter a playlist URL first." }

            // 1. YouTube / YouTube Music playlist URL or playlist ID
            youTubeMusicClient.parseYouTubePlaylistId(trimmed)?.let { ytPlaylistId ->
                val (ytTitle, ytTracks) = youTubeMusicClient.fetchPlaylist(ytPlaylistId).getOrThrow()
                if (ytTracks.isEmpty()) error("No playable tracks found in that YouTube playlist.")
                val finalName = customName?.trim()?.takeIf { it.isNotBlank() } ?: ytTitle
                return@runCatching ImportedPlaylistPayload(
                    name = finalName,
                    description = "Imported from YouTube Music (${ytTracks.size} tracks)",
                    coverResKey = "studio",
                    tracks = ytTracks.map { it.toPlaylistTrackEntity() }
                )
            }

            // 2. Spotify playlist URL or spotify:playlist: URI
            spotifyClient.parseSpotifyPlaylistId(trimmed)?.let { spPlaylistId ->
                if (!spotifyClient.isConfigured) {
                    error("Add SPOTIFY_CLIENT_ID in AI Studio Secrets and sign in to import Spotify playlists.")
                }
                val meta = spotifyClient.fetchPlaylistMeta(spPlaylistId).getOrElse {
                    "Spotify Playlist" to ""
                }
                val tracks = spotifyClient.fetchPlaylistTracks(spPlaylistId).getOrThrow()
                if (tracks.isEmpty()) error("No tracks found in that Spotify playlist.")
                val finalName = customName?.trim()?.takeIf { it.isNotBlank() } ?: meta.first
                return@runCatching ImportedPlaylistPayload(
                    name = finalName,
                    description = meta.second.ifBlank { "Imported from Spotify (${tracks.size} tracks)" },
                    coverResKey = "electronic",
                    tracks = tracks.map { it.toPlaylistTrackEntity() }
                )
            }

            // 3. Remote .m3u / .m3u8 / .pls / plain URL list
            if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
                error("Unsupported playlist link. Paste a YouTube, Spotify, or M3U/PLS URL.")
            }

            val request = Request.Builder().url(trimmed).build()
            val bodyText = client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) error("Failed to download playlist (${response.code})")
                response.body?.string().orEmpty()
            }
            val urlFileName = Uri.parse(trimmed).lastPathSegment
                ?.substringBeforeLast('.')
                ?.replace('-', ' ')
                ?.replace('_', ' ')
                ?.takeIf { it.isNotBlank() }
                ?: "Imported Web Playlist"

            val finalName = customName?.trim()?.takeIf { it.isNotBlank() } ?: urlFileName
            parsePlaylistText(
                rawText = bodyText,
                defaultName = finalName,
                description = "Imported from playlist URL",
                isPlsHint = trimmed.lowercase().contains(".pls")
            )
        }
    }

    suspend fun importFromUri(
        context: Context,
        uri: Uri,
        customName: String? = null,
    ): Result<ImportedPlaylistPayload> = withContext(Dispatchers.IO) {
        runCatching {
            val resolver = context.contentResolver
            var displayName = "Imported Playlist"
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) {
                        displayName = cursor.getString(idx)?.takeIf { it.isNotBlank() } ?: displayName
                    }
                }
            }

            val rawText = resolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                ?: error("Could not read selected playlist file.")

            val cleanName = customName?.trim()?.takeIf { it.isNotBlank() }
                ?: displayName.substringBeforeLast('.').ifBlank { "Imported Playlist" }

            parsePlaylistText(
                rawText = rawText,
                defaultName = cleanName,
                description = "Imported from $displayName",
                isPlsHint = displayName.lowercase().endsWith(".pls")
            )
        }
    }

    fun fromSpotifyPlaylist(
        playlist: SpotifyPlaylist,
        tracks: List<SpotifyTrack>,
    ): ImportedPlaylistPayload = ImportedPlaylistPayload(
        name = playlist.name.ifBlank { "Spotify Playlist" },
        description = playlist.description.ifBlank { "Imported from Spotify (${tracks.size} tracks)" },
        coverResKey = "electronic",
        tracks = tracks.map { it.toPlaylistTrackEntity() }
    )

    private fun parsePlaylistText(
        rawText: String,
        defaultName: String,
        description: String,
        isPlsHint: Boolean,
    ): ImportedPlaylistPayload {
        val lines = rawText.lines().map { it.trim() }.filter { it.isNotEmpty() }
        if (lines.isEmpty()) error("Playlist file is empty.")

        val looksLikePls = isPlsHint || lines.any { it.equals("[playlist]", ignoreCase = true) }
        val tracks = if (looksLikePls) {
            parsePlsLines(lines)
        } else {
            parseM3uOrUrlLines(lines).second
        }

        val resolvedName = if (!looksLikePls) {
            parseM3uOrUrlLines(lines).first ?: defaultName
        } else {
            defaultName
        }

        if (tracks.isEmpty()) {
            error("No playable HTTP, YouTube, or Spotify track entries found in playlist.")
        }

        return ImportedPlaylistPayload(
            name = resolvedName,
            description = "$description (${tracks.size} tracks)",
            coverResKey = "lofi",
            tracks = tracks
        )
    }

    private fun parsePlsLines(lines: List<String>): List<TrackEntity> {
        val files = mutableMapOf<Int, String>()
        val titles = mutableMapOf<Int, String>()
        val lengths = mutableMapOf<Int, Long>()

        for (line in lines) {
            val eqIdx = line.indexOf('=')
            if (eqIdx <= 0) continue
            val key = line.substring(0, eqIdx).trim()
            val value = line.substring(eqIdx + 1).trim()
            when {
                key.startsWith("File", ignoreCase = true) -> {
                    key.drop(4).toIntOrNull()?.let { idx -> files[idx] = value }
                }
                key.startsWith("Title", ignoreCase = true) -> {
                    key.drop(5).toIntOrNull()?.let { idx -> titles[idx] = value }
                }
                key.startsWith("Length", ignoreCase = true) -> {
                    key.drop(6).toIntOrNull()?.let { idx ->
                        val sec = value.toLongOrNull() ?: -1L
                        if (sec > 0L) lengths[idx] = sec * 1000L
                    }
                }
            }
        }

        return files.entries.sortedBy { it.key }.mapNotNull { (idx, target) ->
            buildEntityFromEntry(
                rawTarget = target,
                rawTitle = titles[idx],
                durationMs = lengths[idx] ?: 0L
            )
        }
    }

    private fun parseM3uOrUrlLines(lines: List<String>): Pair<String?, List<TrackEntity>> {
        var playlistTitle: String? = null
        var pendingTitle: String? = null
        var pendingDurationMs = 0L
        val tracks = mutableListOf<TrackEntity>()

        for (line in lines) {
            when {
                line.startsWith("#PLAYLIST:", ignoreCase = true) -> {
                    playlistTitle = line.substringAfter(':').trim().takeIf { it.isNotBlank() }
                }
                line.startsWith("#EXTINF:", ignoreCase = true) -> {
                    val info = line.substringAfter(':')
                    val commaIdx = info.indexOf(',')
                    val secPart = if (commaIdx >= 0) info.substring(0, commaIdx) else info
                    val sec = secPart.trim().toLongOrNull() ?: -1L
                    pendingDurationMs = if (sec > 0L) sec * 1000L else 0L
                    pendingTitle = if (commaIdx >= 0) info.substring(commaIdx + 1).trim() else null
                }
                line.startsWith("#") -> Unit
                else -> {
                    buildEntityFromEntry(
                        rawTarget = line,
                        rawTitle = pendingTitle,
                        durationMs = pendingDurationMs
                    )?.let { tracks.add(it) }
                    pendingTitle = null
                    pendingDurationMs = 0L
                }
            }
        }
        return playlistTitle to tracks
    }

    private fun buildEntityFromEntry(
        rawTarget: String,
        rawTitle: String?,
        durationMs: Long,
    ): TrackEntity? {
        val target = rawTarget.trim()
        if (target.isBlank()) return null

        // Check if line points to YouTube video
        youTubeMusicClient.parseYouTubeUrl(target)?.let { yt ->
            val title = rawTitle?.takeIf { it.isNotBlank() } ?: yt.title
            return yt.copy(title = title).toPlaylistTrackEntity()
        }

        // Check if line points to Spotify track
        spotifyClient.parseSpotifyTrackUrl(target)?.let { sp ->
            val title = rawTitle?.takeIf { it.isNotBlank() } ?: sp.title
            return sp.copy(title = title).toPlaylistTrackEntity()
        }

        if (!target.startsWith("http://") && !target.startsWith("https://")) {
            return null
        }

        val (artist, title) = splitArtistAndTitle(rawTitle, target)
        val isLive = durationMs <= 0L && (
            target.contains("radio", ignoreCase = true) ||
                target.contains("live", ignoreCase = true) ||
                target.endsWith(".m3u8", ignoreCase = true)
            )
        val stableId = "imp_${target.hashCode().toUInt()}"
        return TrackEntity(
            id = stableId,
            title = title,
            artist = artist,
            album = "Imported Playlist",
            genre = if (isLive) "Live Radio" else "Imported",
            durationMs = durationMs,
            sourceType = TrackSourceType.ONLINE,
            streamUrl = target,
            artworkResKey = if (isLive) "lofi" else "electronic",
            isLiveStream = isLive,
            isUserAdded = true,
            addedAt = System.currentTimeMillis()
        )
    }

    private fun splitArtistAndTitle(rawTitle: String?, fallbackUrl: String): Pair<String, String> {
        val cleaned = rawTitle?.trim()?.takeIf { it.isNotBlank() }
        if (cleaned != null) {
            val sep = cleaned.indexOf(" - ")
            if (sep > 0 && sep + 3 < cleaned.length) {
                return cleaned.substring(0, sep).trim() to cleaned.substring(sep + 3).trim()
            }
            return "Imported Stream" to cleaned
        }
        val filename = Uri.parse(fallbackUrl).lastPathSegment
            ?.substringBeforeLast('.')
            ?.replace('-', ' ')
            ?.replace('_', ' ')
            ?.takeIf { it.isNotBlank() }
            ?: "Imported Track"
        return "Imported Stream" to filename
    }

    private fun YouTubeMusicTrack.toPlaylistTrackEntity(): TrackEntity = TrackEntity(
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

    private fun SpotifyTrack.toPlaylistTrackEntity(): TrackEntity = TrackEntity(
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
}
