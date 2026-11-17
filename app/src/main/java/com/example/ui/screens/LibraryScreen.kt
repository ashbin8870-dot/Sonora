package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.data.model.TrackEntity
import com.example.data.model.TrackSourceType
import com.example.player.PlayerUiState
import com.example.ui.components.ConnectivityModeChip
import com.example.ui.components.TrackRow
import com.example.ui.components.formatFileSize
import com.example.ui.theme.EmeraldOffline
import com.example.ui.viewmodel.LibraryTab
import com.example.ui.viewmodel.MusicHomeUiState

@Composable
fun LibraryScreen(
    uiState: MusicHomeUiState,
    playerState: PlayerUiState,
    onSelectTab: (LibraryTab) -> Unit,
    onToggleOfflineMode: () -> Unit,
    onImportLocalAudio: () -> Unit,
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
    val displayedTracks = when (uiState.selectedLibraryTab) {
        LibraryTab.OFFLINE -> uiState.offlineTracks
        LibraryTab.ALL -> uiState.allTracks
        LibraryTab.FAVORITES -> uiState.favoriteTracks
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            top = 12.dp,
            bottom = bottomContentPadding.calculateBottomPadding() + 16.dp
        ),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Header
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
                        text = "Your Library",
                        style = MaterialTheme.typography.headlineMedium
                    )
                    Text(
                        text = "Local files, offline downloads & favorites",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                ConnectivityModeChip(
                    isDeviceOnline = uiState.isDeviceOnline,
                    offlineOnlyMode = uiState.preferences.offlineOnlyMode,
                    onToggleOfflineMode = onToggleOfflineMode
                )
            }
        }

        // Filter tabs
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                LibraryTab.entries.forEach { tab ->
                    val count = when (tab) {
                        LibraryTab.OFFLINE -> uiState.offlineTracks.size
                        LibraryTab.ALL -> uiState.allTracks.size
                        LibraryTab.FAVORITES -> uiState.favoriteTracks.size
                    }
                    FilterChip(
                        selected = uiState.selectedLibraryTab == tab,
                        onClick = { onSelectTab(tab) },
                        label = { Text("${tab.label} ($count)") },
                        modifier = Modifier.testTag("library_tab_${tab.name.lowercase()}")
                    )
                }
            }
        }

        // Offline Storage & Import Card
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerLow)
                    .border(
                        1.dp,
                        EmeraldOffline.copy(alpha = 0.3f),
                        RoundedCornerShape(20.dp)
                    )
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.CloudDone,
                            contentDescription = null,
                            tint = EmeraldOffline,
                            modifier = Modifier.size(22.dp)
                        )
                        Column {
                            Text(
                                text = "${uiState.offlineTracks.size} Tracks Ready Offline",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = "Local & cached audio • ${formatFileSize(uiState.totalOfflineBytes)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = onImportLocalAudio,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("library_import_audio_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.FolderOpen,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("Import Audio")
                    }

                    FilledTonalButton(
                        onClick = {
                            if (displayedTracks.isNotEmpty()) {
                                onShufflePlay(displayedTracks)
                            }
                        },
                        enabled = displayedTracks.isNotEmpty(),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("library_shuffle_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Shuffle,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("Shuffle")
                    }
                }
            }
        }

        if (displayedTracks.isEmpty()) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.LibraryMusic,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(48.dp)
                    )
                    Text(
                        text = when (uiState.selectedLibraryTab) {
                            LibraryTab.OFFLINE -> "No Offline Audio Yet"
                            LibraryTab.FAVORITES -> "No Favorite Tracks Yet"
                            LibraryTab.ALL -> "Library is Empty"
                        },
                        style = MaterialTheme.typography.titleLarge,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = when (uiState.selectedLibraryTab) {
                            LibraryTab.OFFLINE ->
                                "Import MP3/M4A/FLAC files from your device or tap the download icon on any Discover track to listen offline."
                            LibraryTab.FAVORITES ->
                                "Tap the heart icon on any track or radio station to keep it close."
                            LibraryTab.ALL ->
                                "Import local files or add custom streams to build your library."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    if (uiState.selectedLibraryTab == LibraryTab.OFFLINE) {
                        Spacer(Modifier.height(4.dp))
                        Button(onClick = onImportLocalAudio) {
                            Icon(Icons.Rounded.FolderOpen, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Import Device Music")
                        }
                    }
                }
            }
        } else {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${displayedTracks.size} ${if (displayedTracks.size == 1) "track" else "tracks"}",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    FilledTonalButton(
                        onClick = { onPlayTrack(displayedTracks.first(), displayedTracks) },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.testTag("library_play_all_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("Play All")
                    }
                }
            }

            items(displayedTracks, key = { "lib_${it.id}" }) { track ->
                TrackRow(
                    track = track,
                    isCurrentTrack = playerState.currentTrack?.id == track.id,
                    isPlaying = playerState.isPlaying,
                    isEffectivelyOffline = uiState.isEffectivelyOffline,
                    downloadProgress = uiState.downloadProgress[track.id],
                    onPlayClick = { onPlayTrack(track, displayedTracks) },
                    onToggleFavorite = { onToggleFavorite(track) },
                    onDownloadClick = { onDownloadTrack(track) },
                    onRemoveDownloadClick = { onRemoveDownload(track) },
                    onPlayNextClick = { onPlayNext(track) },
                    onAddToQueueClick = { onAddToQueue(track) },
                    onAddToPlaylistClick = { onAddToPlaylist(track) },
                    onDeleteTrackClick = if (track.isUserAdded || track.sourceType == TrackSourceType.LOCAL) {
                        { onDeleteTrack(track) }
                    } else null,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
        }
    }
}
