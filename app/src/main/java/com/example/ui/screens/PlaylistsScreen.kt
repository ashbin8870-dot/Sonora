package com.example.ui.screens

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SmartDisplay
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.PlaylistWithTracks
import com.example.data.model.TrackEntity
import com.example.player.PlayerUiState
import com.example.ui.components.ArtworkImage
import com.example.ui.components.ImportPlaylistSourceTab
import com.example.ui.components.SourcePill
import com.example.ui.components.TrackRow
import com.example.ui.theme.EmeraldOffline
import com.example.ui.viewmodel.MusicHomeUiState

@Composable
fun PlaylistsScreen(
    playlists: List<PlaylistWithTracks>,
    isImportingPlaylist: Boolean,
    onOpenPlaylist: (Long) -> Unit,
    onCreatePlaylistClick: () -> Unit,
    onImportWithSourceTab: (ImportPlaylistSourceTab) -> Unit,
    onDeletePlaylist: (Long, String) -> Unit,
    onPlayPlaylist: (PlaylistWithTracks) -> Unit,
    bottomContentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            top = 12.dp,
            bottom = bottomContentPadding.calculateBottomPadding() + 16.dp
        ),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Playlists",
                            style = MaterialTheme.typography.headlineMedium
                        )
                        Text(
                            text = "Import YouTube Music, Spotify, M3U/PLS, or local files",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Button(
                        onClick = onCreatePlaylistClick,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.testTag("new_playlist_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Add,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("New")
                    }
                }
            }
        }

        item {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    FilledTonalButton(
                        onClick = { onImportWithSourceTab(ImportPlaylistSourceTab.YOUTUBE_MUSIC) },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.testTag("import_youtube_playlist_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.SmartDisplay,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("YouTube Music")
                    }
                }
                item {
                    FilledTonalButton(
                        onClick = { onImportWithSourceTab(ImportPlaylistSourceTab.SPOTIFY) },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.testTag("import_spotify_playlist_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.GraphicEq,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("Spotify")
                    }
                }
                item {
                    OutlinedButton(
                        onClick = { onImportWithSourceTab(ImportPlaylistSourceTab.FILE_AND_LOCAL) },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.testTag("import_playlist_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.FileDownload,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("M3U / Files")
                    }
                }
            }
        }

        if (isImportingPlaylist) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "Importing playlist tracks...",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }
        }

        if (playlists.isEmpty()) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 40.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.QueueMusic,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(48.dp)
                    )
                    Text(
                        text = "No Playlists Yet",
                        style = MaterialTheme.typography.titleLarge
                    )
                    Text(
                        text = "Create custom playlists or import from YouTube, Spotify, M3U/PLS files, and device audio.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        FilledTonalButton(onClick = { onImportWithSourceTab(ImportPlaylistSourceTab.YOUTUBE_MUSIC) }) {
                            Icon(Icons.Rounded.SmartDisplay, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("YT Music")
                        }
                        FilledTonalButton(onClick = { onImportWithSourceTab(ImportPlaylistSourceTab.SPOTIFY) }) {
                            Icon(Icons.Rounded.GraphicEq, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("Spotify")
                        }
                    }
                }
            }
        } else {
            items(playlists, key = { it.playlist.id }) { item ->
                val offlineCount = item.tracks.count { it.isAvailableOffline }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerLow)
                        .border(
                            1.dp,
                            MaterialTheme.colorScheme.outlineVariant,
                            RoundedCornerShape(18.dp)
                        )
                        .clickable { onOpenPlaylist(item.playlist.id) }
                        .testTag("playlist_card_${item.playlist.id}")
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ArtworkImage(
                        artworkResKey = item.playlist.coverResKey,
                        modifier = Modifier.size(68.dp),
                        shape = RoundedCornerShape(14.dp)
                    )
                    Spacer(Modifier.width(14.dp))
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = item.playlist.name,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (item.playlist.description.isNotBlank()) {
                            Text(
                                text = item.playlist.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "${item.tracks.size} tracks",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (offlineCount > 0) {
                                SourcePill(
                                    label = "$offlineCount OFFLINE",
                                    accentColor = EmeraldOffline
                                )
                            }
                        }
                    }

                    if (item.tracks.isNotEmpty()) {
                        IconButton(
                            onClick = { onPlayPlaylist(item) },
                            modifier = Modifier.testTag("play_playlist_${item.playlist.id}")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.PlayArrow,
                                contentDescription = stringResource(R.string.cd_play),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    IconButton(
                        onClick = { onDeletePlaylist(item.playlist.id, item.playlist.name) },
                        modifier = Modifier.testTag("delete_playlist_${item.playlist.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.DeleteOutline,
                            contentDescription = "Delete playlist",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistDetailScreen(
    playlistWithTracks: PlaylistWithTracks?,
    uiState: MusicHomeUiState,
    playerState: PlayerUiState,
    onBack: () -> Unit,
    onPlayTrack: (TrackEntity, List<TrackEntity>) -> Unit,
    onShufflePlay: (List<TrackEntity>) -> Unit,
    onToggleFavorite: (TrackEntity) -> Unit,
    onDownloadTrack: (TrackEntity) -> Unit,
    onRemoveDownload: (TrackEntity) -> Unit,
    onPlayNext: (TrackEntity) -> Unit,
    onAddToQueue: (TrackEntity) -> Unit,
    onAddTrackToThisPlaylist: (Long, String, String) -> Unit,
    onRemoveTrackFromThisPlaylist: (Long, String) -> Unit,
    onOpenPlaylistPicker: (TrackEntity) -> Unit,
    bottomContentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    var showAddTracksSheet by remember { mutableStateOf(false) }

    if (playlistWithTracks == null) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Playlist not found", style = MaterialTheme.typography.titleMedium)
        }
        return
    }

    val playlist = playlistWithTracks.playlist
    val tracks = playlistWithTracks.tracks

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            top = 8.dp,
            bottom = bottomContentPadding.calculateBottomPadding() + 16.dp
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.testTag("playlist_detail_back")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = stringResource(R.string.cd_back)
                    )
                }
                Text(
                    text = playlist.name,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                FilledTonalButton(
                    onClick = { showAddTracksSheet = true },
                    modifier = Modifier
                        .padding(end = 8.dp)
                        .testTag("playlist_add_tracks_button")
                ) {
                    Icon(Icons.Rounded.PlaylistAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Add Tracks")
                }
            }
        }

        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ArtworkImage(
                    artworkResKey = playlist.coverResKey,
                    modifier = Modifier.size(148.dp),
                    shape = RoundedCornerShape(22.dp)
                )
                if (playlist.description.isNotBlank()) {
                    Text(
                        text = playlist.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Button(
                        onClick = {
                            if (tracks.isNotEmpty()) onPlayTrack(tracks.first(), tracks)
                        },
                        enabled = tracks.isNotEmpty(),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Play All")
                    }
                    FilledTonalButton(
                        onClick = { onShufflePlay(tracks) },
                        enabled = tracks.isNotEmpty(),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Rounded.Shuffle, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text("Shuffle")
                    }
                }
            }
        }

        if (tracks.isEmpty()) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("No tracks in this playlist yet", style = MaterialTheme.typography.titleMedium)
                    Button(onClick = { showAddTracksSheet = true }) {
                        Text("Add Tracks from Library")
                    }
                }
            }
        } else {
            items(tracks, key = { "pd_${it.id}" }) { track ->
                TrackRow(
                    track = track,
                    isCurrentTrack = playerState.currentTrack?.id == track.id,
                    isPlaying = playerState.isPlaying,
                    isEffectivelyOffline = uiState.isEffectivelyOffline,
                    downloadProgress = uiState.downloadProgress[track.id],
                    onPlayClick = { onPlayTrack(track, tracks) },
                    onToggleFavorite = { onToggleFavorite(track) },
                    onDownloadClick = { onDownloadTrack(track) },
                    onRemoveDownloadClick = { onRemoveDownload(track) },
                    onPlayNextClick = { onPlayNext(track) },
                    onAddToQueueClick = { onAddToQueue(track) },
                    onAddToPlaylistClick = { onOpenPlaylistPicker(track) },
                    onDeleteTrackClick = { onRemoveTrackFromThisPlaylist(playlist.id, track.id) },
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
        }
    }

    if (showAddTracksSheet) {
        val existingIds = tracks.map { it.id }.toSet()
        val availableTracks = uiState.allTracks.filter { it.id !in existingIds }
        ModalBottomSheet(onDismissRequest = { showAddTracksSheet = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp)
            ) {
                Text(
                    text = "Add Tracks to ${playlist.name}",
                    style = MaterialTheme.typography.titleLarge
                )
                Spacer(Modifier.height(12.dp))
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(bottom = 24.dp)
                ) {
                    items(availableTracks, key = { "add_${it.id}" }) { track ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    onAddTrackToThisPlaylist(playlist.id, playlist.name, track.id)
                                }
                                .padding(horizontal = 8.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            ArtworkImage(
                                artworkResKey = track.artworkResKey,
                                artworkFilePath = track.artworkFilePath,
                                modifier = Modifier.size(44.dp),
                                shape = RoundedCornerShape(10.dp)
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(track.title, style = MaterialTheme.typography.titleSmall, maxLines = 1)
                                Text(
                                    track.artist,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1
                                )
                            }
                            Icon(
                                imageVector = Icons.Rounded.Add,
                                contentDescription = "Add to playlist",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }
    }
}
