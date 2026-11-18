package com.example.data.repository

import android.content.Context
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import com.example.data.local.PlaylistDao
import com.example.data.local.TrackDao
import com.example.data.model.DownloadStatus
import com.example.data.model.MusicCatalog
import com.example.data.model.PlaylistEntity
import com.example.data.model.PlaylistTrackCrossRef
import com.example.data.model.PlaylistWithTracks
import com.example.data.model.TrackEntity
import com.example.data.model.TrackSourceType
import com.example.data.remote.ResolvedAudioStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import java.util.concurrent.TimeUnit

class MusicRepository(
    private val context: Context,
    private val trackDao: TrackDao,
    private val playlistDao: PlaylistDao,
    private val streamResolver: ((Uri) -> ResolvedAudioStream)? = null,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private val offlineAudioDir = File(context.filesDir, "offline_audio").apply { mkdirs() }
    private val artworkDir = File(context.filesDir, "artwork").apply { mkdirs() }

    private val _downloadProgress = MutableStateFlow<Map<String, Float>>(emptyMap())
    val downloadProgress: StateFlow<Map<String, Float>> = _downloadProgress.asStateFlow()

    val allTracks: Flow<List<TrackEntity>> = trackDao.observeAllTracks()
    val offlineReadyTracks: Flow<List<TrackEntity>> = trackDao.observeOfflineReadyTracks()
    val favoriteTracks: Flow<List<TrackEntity>> = trackDao.observeFavoriteTracks()
    val recentlyPlayed: Flow<List<TrackEntity>> = trackDao.observeRecentlyPlayed()
    val playlistsWithTracks: Flow<List<PlaylistWithTracks>> = playlistDao.observePlaylistsWithTracks()

    fun observePlaylist(playlistId: Long): Flow<PlaylistWithTracks?> =
        playlistDao.observePlaylistWithTracks(playlistId)

    suspend fun ensureSeeded() = withContext(Dispatchers.IO) {
        trackDao.insertIgnore(MusicCatalog.defaultOnlineCatalog())
        if (playlistDao.getPlaylistCount() == 0) {
            val focusId = playlistDao.insertPlaylist(
                PlaylistEntity(
                    name = "Late Night Focus",
                    description = "Ambient & lo-fi sessions for deep work",
                    coverResKey = "lofi"
                )
            )
            val pulseId = playlistDao.insertPlaylist(
                PlaylistEntity(
                    name = "Neon Drive",
                    description = "Electronic & synthwave energy",
                    coverResKey = "electronic"
                )
            )
            listOf("online_sh_2", "online_sh_3", "online_sh_8").forEach { trackId ->
                playlistDao.addTrackToPlaylist(PlaylistTrackCrossRef(focusId, trackId))
            }
            listOf("online_sh_1", "online_sh_6", "online_sh_13").forEach { trackId ->
                playlistDao.addTrackToPlaylist(PlaylistTrackCrossRef(pulseId, trackId))
            }
        }
    }

    suspend fun importLocalAudioUris(uris: List<Uri>): Int = withContext(Dispatchers.IO) {
        var importedCount = 0
        for (uri in uris) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            val track = extractLocalTrackMetadata(uri)
            if (track != null) {
                trackDao.upsert(track)
                importedCount++
            }
        }
        importedCount
    }

    private fun extractLocalTrackMetadata(uri: Uri): TrackEntity? {
        val resolver = context.contentResolver
        var fileName = "Audio Track"
        var fileSize = 0L

        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (nameIdx >= 0) fileName = cursor.getString(nameIdx) ?: fileName
                    if (sizeIdx >= 0) fileSize = cursor.getLong(sizeIdx)
                }
            }

        val cleanName = fileName.substringBeforeLast('.').ifBlank { "Local Track" }
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            val title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                ?.takeIf { it.isNotBlank() } ?: cleanName
            val artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                ?.takeIf { it.isNotBlank() } ?: "Local Artist"
            val album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
                ?.takeIf { it.isNotBlank() } ?: "Device Storage"
            val genre = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_GENRE)
                ?.takeIf { it.isNotBlank() } ?: "Local Audio"
            val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: 0L

            val trackId = "local_${uri.toString().hashCode().toUInt()}"
            var artworkPath: String? = null
            val artBytes = retriever.embeddedPicture
            if (artBytes != null && artBytes.isNotEmpty()) {
                val artFile = File(artworkDir, "$trackId.jpg")
                FileOutputStream(artFile).use { it.write(artBytes) }
                artworkPath = artFile.absolutePath
            }

            TrackEntity(
                id = trackId,
                title = title,
                artist = artist,
                album = album,
                genre = genre,
                durationMs = duration,
                sourceType = TrackSourceType.LOCAL,
                contentUri = uri.toString(),
                artworkResKey = "studio",
                artworkFilePath = artworkPath,
                isLiveStream = false,
                downloadStatus = DownloadStatus.NONE,
                fileSizeBytes = fileSize,
                isUserAdded = true,
                addedAt = System.currentTimeMillis()
            )
        } catch (_: Exception) {
            null
        } finally {
            runCatching { retriever.release() }
        }
    }

    suspend fun addCustomStream(
        title: String,
        artist: String,
        streamUrl: String,
        genre: String,
        isLiveStream: Boolean,
    ): TrackEntity = withContext(Dispatchers.IO) {
        val artworkKey = when (genre.lowercase()) {
            "lo-fi", "chill" -> "lofi"
            "electronic", "synth" -> "electronic"
            "acoustic", "indie" -> "acoustic"
            else -> "studio"
        }
        val track = TrackEntity(
            id = "custom_${UUID.randomUUID().toString().take(8)}",
            title = title.trim(),
            artist = artist.trim().ifBlank { "Online Stream" },
            album = if (isLiveStream) "Live Broadcast" else "Custom Stream",
            genre = genre.trim().ifBlank { "Stream" },
            durationMs = 0L,
            sourceType = TrackSourceType.ONLINE,
            streamUrl = streamUrl.trim(),
            artworkResKey = artworkKey,
            isLiveStream = isLiveStream,
            isUserAdded = true,
            addedAt = System.currentTimeMillis()
        )
        trackDao.upsert(track)
        track
    }

    fun downloadTrackForOffline(track: TrackEntity) {
        val url = track.streamUrl ?: return
        if (track.isLiveStream) return
        if (_downloadProgress.value.containsKey(track.id)) return

        scope.launch {
            _downloadProgress.update { it + (track.id to 0f) }
            trackDao.updateDownloadState(
                id = track.id,
                status = DownloadStatus.DOWNLOADING,
                localFilePath = track.localFilePath,
                fileSizeBytes = track.fileSizeBytes,
                sourceType = track.sourceType
            )

            val destFile = File(offlineAudioDir, "${track.id}.mp3")
            val tempFile = File(offlineAudioDir, "${track.id}.tmp")

            val result = runCatching {
                val resolved = if (url.startsWith("yt://") || url.startsWith("spotify://")) {
                    streamResolver?.invoke(Uri.parse(url))
                        ?: error("Stream resolver unavailable")
                } else {
                    ResolvedAudioStream(url)
                }
                val requestBuilder = Request.Builder().url(resolved.url)
                resolved.headers.forEach { (k, v) -> requestBuilder.header(k, v) }
                okHttpClient.newCall(requestBuilder.build()).execute().use { response ->
                    if (!response.isSuccessful) error("HTTP ${response.code}")
                    val body = response.body ?: error("Empty response body")
                    val totalBytes = body.contentLength()
                    var downloadedBytes = 0L

                    body.byteStream().use { input ->
                        FileOutputStream(tempFile).use { output ->
                            val buffer = ByteArray(16 * 1024)
                            var bytesRead: Int
                            while (input.read(buffer).also { bytesRead = it } != -1) {
                                output.write(buffer, 0, bytesRead)
                                downloadedBytes += bytesRead
                                if (totalBytes > 0) {
                                    val progress = (downloadedBytes.toFloat() / totalBytes).coerceIn(0f, 1f)
                                    _downloadProgress.update { it + (track.id to progress) }
                                }
                            }
                            output.flush()
                        }
                    }
                    if (destFile.exists()) destFile.delete()
                    tempFile.renameTo(destFile)
                    destFile.length()
                }
            }

            _downloadProgress.update { it - track.id }

            result.onSuccess { bytes ->
                trackDao.updateDownloadState(
                    id = track.id,
                    status = DownloadStatus.DOWNLOADED,
                    localFilePath = destFile.absolutePath,
                    fileSizeBytes = bytes,
                    sourceType = TrackSourceType.DOWNLOADED
                )
            }.onFailure {
                tempFile.delete()
                trackDao.updateDownloadState(
                    id = track.id,
                    status = DownloadStatus.FAILED,
                    localFilePath = null,
                    fileSizeBytes = 0L,
                    sourceType = TrackSourceType.ONLINE
                )
            }
        }
    }

    suspend fun removeOfflineDownload(track: TrackEntity) = withContext(Dispatchers.IO) {
        track.localFilePath?.let { path ->
            runCatching { File(path).delete() }
        }
        val restoredSource = if (track.streamUrl != null) TrackSourceType.ONLINE else TrackSourceType.LOCAL
        trackDao.updateDownloadState(
            id = track.id,
            status = DownloadStatus.NONE,
            localFilePath = null,
            fileSizeBytes = 0L,
            sourceType = restoredSource
        )
    }

    suspend fun deleteUserTrack(track: TrackEntity) = withContext(Dispatchers.IO) {
        track.localFilePath?.let { runCatching { File(it).delete() } }
        track.artworkFilePath?.let { runCatching { File(it).delete() } }
        trackDao.deleteById(track.id)
    }

    suspend fun toggleFavorite(trackId: String, currentFavorite: Boolean) {
        trackDao.setFavorite(trackId, !currentFavorite)
    }

    suspend fun markPlayed(trackId: String) {
        trackDao.markPlayed(trackId, System.currentTimeMillis())
    }

    suspend fun createPlaylist(name: String, description: String, coverResKey: String): Long {
        return playlistDao.insertPlaylist(
            PlaylistEntity(
                name = name.trim(),
                description = description.trim(),
                coverResKey = coverResKey
            )
        )
    }

    suspend fun deletePlaylist(playlistId: Long) {
        playlistDao.deletePlaylist(playlistId)
    }

    suspend fun addTrackToPlaylist(playlistId: Long, trackId: String) {
        playlistDao.addTrackToPlaylist(PlaylistTrackCrossRef(playlistId, trackId))
    }

    suspend fun removeTrackFromPlaylist(playlistId: Long, trackId: String) {
        playlistDao.removeTrackFromPlaylist(playlistId, trackId)
    }

    suspend fun upsertTrack(track: TrackEntity) = withContext(Dispatchers.IO) {
        trackDao.upsert(track)
    }

    suspend fun importPlaylistWithTracks(
        name: String,
        description: String,
        coverResKey: String,
        tracks: List<TrackEntity>,
    ): Long = withContext(Dispatchers.IO) {
        val playlistId = playlistDao.insertPlaylist(
            PlaylistEntity(
                name = name.trim().ifBlank { "Imported Playlist" },
                description = description.trim(),
                coverResKey = coverResKey.ifBlank { "lofi" }
            )
        )
        for (track in tracks) {
            trackDao.insertIgnore(listOf(track))
            playlistDao.addTrackToPlaylist(PlaylistTrackCrossRef(playlistId, track.id))
        }
        playlistId
    }

    suspend fun importLocalAudioFilesAsPlaylist(
        playlistName: String,
        uris: List<Uri>,
    ): Pair<Long, Int> = withContext(Dispatchers.IO) {
        val importedTracks = mutableListOf<TrackEntity>()
        for (uri in uris) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            extractLocalTrackMetadata(uri)?.let { track ->
                trackDao.upsert(track)
                importedTracks.add(track)
            }
        }
        if (importedTracks.isEmpty()) return@withContext (-1L to 0)

        val resolvedName = playlistName.trim().ifBlank {
            "Imported Local Mix (${importedTracks.size})"
        }
        val playlistId = playlistDao.insertPlaylist(
            PlaylistEntity(
                name = resolvedName,
                description = "Imported ${importedTracks.size} local audio files",
                coverResKey = "acoustic"
            )
        )
        for (track in importedTracks) {
            playlistDao.addTrackToPlaylist(PlaylistTrackCrossRef(playlistId, track.id))
        }
        playlistId to importedTracks.size
    }
}
