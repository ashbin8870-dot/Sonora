package com.example.player

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.example.R
import com.example.data.model.TrackEntity

object MediaItemMapper {

    fun toMediaItem(context: Context, track: TrackEntity): MediaItem {
        val artworkUri = when {
            !track.artworkFilePath.isNullOrBlank() -> Uri.parse("file://${track.artworkFilePath}")
            !track.artworkUrl.isNullOrBlank() -> Uri.parse(track.artworkUrl)
            else -> {
                val resId = when (track.artworkResKey) {
                    "lofi" -> R.drawable.cover_lofi_1794941431952
                    "electronic" -> R.drawable.cover_electronic_1794941431840
                    "acoustic" -> R.drawable.cover_acoustic_1794941433359
                    else -> R.drawable.hero_studio_1794941431881
                }
                Uri.parse("android.resource://${context.packageName}/$resId")
            }
        }

        val metadata = MediaMetadata.Builder()
            .setTitle(track.title)
            .setArtist(track.artist)
            .setAlbumTitle(track.album)
            .setGenre(track.genre)
            .setArtworkUri(artworkUri)
            .build()

        return MediaItem.Builder()
            .setMediaId(track.id)
            .setUri(track.playableUri)
            .setMediaMetadata(metadata)
            .build()
    }
}
