package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class TrackSourceType {
    LOCAL,
    ONLINE,
    DOWNLOADED
}

enum class DownloadStatus {
    NONE,
    DOWNLOADING,
    DOWNLOADED,
    FAILED
}

@Entity(tableName = "tracks")
data class TrackEntity(
    @PrimaryKey val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val genre: String,
    val durationMs: Long,
    val sourceType: TrackSourceType,
    val streamUrl: String? = null,
    val contentUri: String? = null,
    val localFilePath: String? = null,
    val artworkResKey: String? = null,
    val artworkFilePath: String? = null,
    val artworkUrl: String? = null,
    val isLiveStream: Boolean = false,
    val isFavorite: Boolean = false,
    val downloadStatus: DownloadStatus = DownloadStatus.NONE,
    val fileSizeBytes: Long = 0L,
    val isUserAdded: Boolean = false,
    val lastPlayedAt: Long = 0L,
    val addedAt: Long = System.currentTimeMillis(),
) {
    val isAvailableOffline: Boolean
        get() = sourceType == TrackSourceType.LOCAL ||
            (downloadStatus == DownloadStatus.DOWNLOADED && !localFilePath.isNullOrBlank())

    val playableUri: String
        get() = when {
            !localFilePath.isNullOrBlank() && downloadStatus == DownloadStatus.DOWNLOADED -> "file://$localFilePath"
            !contentUri.isNullOrBlank() -> contentUri
            else -> streamUrl.orEmpty()
        }
}
