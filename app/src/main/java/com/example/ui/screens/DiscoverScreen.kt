package com.example.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddLink
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Podcasts
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.TrackEntity
import com.example.player.PlayerUiState
import com.example.ui.components.AnimatedEqualizerBars
import com.example.ui.components.ArtworkImage
import com.example.ui.components.ConnectivityModeChip
import com.example.ui.components.SourcePill
import com.example.ui.components.TrackRow
import com.example.ui.theme.SoftRose
import com.example.ui.viewmodel.MusicHomeUiState

@Composable
fun DiscoverScreen(
    uiState: MusicHomeUiState,
    playerState: PlayerUiState,
    onToggleOfflineMode: () -> Unit,
    onToggleTheme: () -> Unit,
    onImportLocalAudio: () -> Unit,
    onOpenAddStreamDialog: () -> Unit,
    onPlayTrack: (TrackEntity, List<TrackEntity>) -> Unit,
    onShufflePlay: (List<TrackEntity>) -> Unit,
    onToggleFavorite: (TrackEntity) -> Unit,
    onDownloadTrack: (TrackEntity) -> Unit,
    onRemoveDownload: (TrackEntity) -> Unit,
    onPlayNext: (TrackEntity) -> Unit,
    onAddToQueue: (TrackEntity) -> Unit,
    onAddToPlaylist: (TrackEntity) -> Unit,
    onDeleteTrack: (TrackEntity) -> Unit,
    bottomContentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            top = 12.dp,
            bottom = bottomContentPadding.calculateBottomPadding() + 16.dp
        ),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // Top Header Bar
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "RINMUSIC",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "${uiState.offlineTracks.size} offline • ${uiState.onlineTracks.size + uiState.liveRadioStations.size} online",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    ConnectivityModeChip(
                        isDeviceOnline = uiState.isDeviceOnline,
                        offlineOnlyMode = uiState.preferences.offlineOnlyMode,
                        onToggleOfflineMode = onToggleOfflineMode
                    )
                    IconButton(
                        onClick = onToggleTheme,
                        modifier = Modifier.testTag("theme_toggle_button")
                    ) {
                        Icon(
                            imageVector = if (uiState.preferences.darkTheme) {
                                Icons.Rounded.LightMode
                            } else {
                                Icons.Rounded.DarkMode
                            },
                            contentDescription = "Toggle theme",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // Hero Studio Banner Card
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .height(216.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .border(
                        1.dp,
                        MaterialTheme.colorScheme.outline.copy(alpha = 0.6f),
                        RoundedCornerShape(24.dp)
                    )
            ) {
                Image(
                    painter = painterResource(id = R.drawable.hero_studio_1794941431881),
                    contentDescription = "RinMusic Studio Vinyl Banner",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    Color.Black.copy(alpha = 0.2f),
                                    Color.Black.copy(alpha = 0.72f),
                                    Color(0xFF0E1015).copy(alpha = 0.94f)
                                )
                            )
                        )
                )

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(18.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SourcePill(
                            label = if (uiState.isEffectivelyOffline) "OFFLINE READY" else "HYBRID AUDIO",
                            accentColor = MaterialTheme.colorScheme.primary
                        )
                        IconButton(
                            onClick = {
                                val pool = if (uiState.isEffectivelyOffline) {
                                    uiState.offlineTracks
                                } else {
                                    uiState.allTracks
                                }
                                onShufflePlay(pool)
                            },
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary)
                                .testTag("hero_shuffle_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Shuffle,
                                contentDescription = "Shuffle play",
                                tint = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "Play Local Files & Live Streams",
                            style = MaterialTheme.typography.titleLarge,
                            color = Color.White
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Button(
                                onClick = onImportLocalAudio,
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                ),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("hero_import_audio_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.FolderOpen,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text("Import Audio", maxLines = 1)
                            }

                            OutlinedButton(
                                onClick = onOpenAddStreamDialog,
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = Color.White,
                                    containerColor = Color.White.copy(alpha = 0.08f)
                                ),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("hero_add_stream_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.AddLink,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text("Add Stream", maxLines = 1)
                            }
                        }
                    }
                }
            }
        }

        // Recently Played Shelf
        if (uiState.recentlyPlayed.isNotEmpty()) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Jump Back In",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(horizontal = 20.dp)
                    )
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(uiState.recentlyPlayed, key = { "recent_${it.id}" }) { track ->
                            val isCurrent = playerState.currentTrack?.id == track.id
                            Column(
                                modifier = Modifier
                                    .width(136.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(MaterialTheme.colorScheme.surfaceContainerLow)
                                    .border(
                                        1.dp,
                                        if (isCurrent) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.outlineVariant,
                                        RoundedCornerShape(16.dp)
                                    )
                                    .clickable { onPlayTrack(track, uiState.recentlyPlayed) }
                                    .padding(10.dp)
                            ) {
                                ArtworkImage(
                                    artworkResKey = track.artworkResKey,
                                    artworkFilePath = track.artworkFilePath,
                                    isPlaying = isCurrent && playerState.isPlaying,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(112.dp),
                                    shape = RoundedCornerShape(12.dp)
                                )
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    text = track.title,
                                    style = MaterialTheme.typography.titleSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = track.artist,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }

        // Live Online Radio Carousel
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Podcasts,
                            contentDescription = null,
                            tint = SoftRose,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "Live Online Radio",
                            style = MaterialTheme.typography.titleLarge
                        )
                    }
                    if (uiState.isEffectivelyOffline) {
                        SourcePill(label = "REQUIRES ONLINE", accentColor = MaterialTheme.colorScheme.tertiary)
                    }
                }

                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(uiState.liveRadioStations, key = { it.id }) { station ->
                        val isCurrent = playerState.currentTrack?.id == station.id
                        Box(
                            modifier = Modifier
                                .width(220.dp)
                                .clip(RoundedCornerShape(18.dp))
                                .background(
                                    if (isCurrent) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                                    else MaterialTheme.colorScheme.surfaceContainerLow
                                )
                                .border(
                                    1.dp,
                                    if (isCurrent) SoftRose else MaterialTheme.colorScheme.outlineVariant,
                                    RoundedCornerShape(18.dp)
                                )
                                .clickable { onPlayTrack(station, uiState.liveRadioStations) }
                                .testTag("radio_card_${station.id}")
                                .padding(12.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                ArtworkImage(
                                    artworkResKey = station.artworkResKey,
                                    isPlaying = isCurrent && playerState.isPlaying,
                                    modifier = Modifier.size(56.dp),
                                    shape = RoundedCornerShape(12.dp)
                                )
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    SourcePill(label = "LIVE", accentColor = SoftRose)
                                    Text(
                                        text = station.title,
                                        style = MaterialTheme.typography.titleSmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = station.album,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                if (isCurrent && playerState.isPlaying) {
                                    AnimatedEqualizerBars(color = SoftRose, maxHeight = 16.dp)
                                } else {
                                    Icon(
                                        imageVector = Icons.Rounded.PlayArrow,
                                        contentDescription = "Play station",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Featured Online & Downloadable Tracks Header
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Online & Offline-Ready Tracks",
                        style = MaterialTheme.typography.titleLarge
                    )
                    Text(
                        text = "Stream instantly or tap download to save offline",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        items(uiState.onlineTracks, key = { it.id }) { track ->
            TrackRow(
                track = track,
                isCurrentTrack = playerState.currentTrack?.id == track.id,
                isPlaying = playerState.isPlaying,
                isEffectivelyOffline = uiState.isEffectivelyOffline,
                downloadProgress = uiState.downloadProgress[track.id],
                onPlayClick = { onPlayTrack(track, uiState.onlineTracks) },
                onToggleFavorite = { onToggleFavorite(track) },
                onDownloadClick = { onDownloadTrack(track) },
                onRemoveDownloadClick = { onRemoveDownload(track) },
                onPlayNextClick = { onPlayNext(track) },
                onAddToQueueClick = { onAddToQueue(track) },
                onAddToPlaylistClick = { onAddToPlaylist(track) },
                onDeleteTrackClick = if (track.isUserAdded) { { onDeleteTrack(track) } } else null,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }
    }
}
