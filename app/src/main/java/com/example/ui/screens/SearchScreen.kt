package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.model.TrackEntity
import com.example.data.model.TrackSourceType
import com.example.player.PlayerUiState
import com.example.ui.components.TrackRow
import com.example.ui.viewmodel.MusicHomeUiState
import com.example.ui.viewmodel.SearchFilter

@Composable
fun SearchScreen(
    uiState: MusicHomeUiState,
    playerState: PlayerUiState,
    onQueryChange: (String) -> Unit,
    onSelectFilter: (SearchFilter) -> Unit,
    onPlayTrack: (TrackEntity, List<TrackEntity>) -> Unit,
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
    val results = uiState.filteredSearchResults

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            top = 12.dp,
            bottom = bottomContentPadding.calculateBottomPadding() + 16.dp
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Search",
                    style = MaterialTheme.typography.headlineMedium
                )
                OutlinedTextField(
                    value = uiState.searchQuery,
                    onValueChange = onQueryChange,
                    placeholder = { Text("Tracks, artists, genres, or radio...") },
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                    trailingIcon = {
                        if (uiState.searchQuery.isNotEmpty()) {
                            IconButton(onClick = { onQueryChange("") }) {
                                Icon(Icons.Rounded.Clear, contentDescription = "Clear search")
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("search_query_input")
                )
            }
        }

        item {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(SearchFilter.entries) { filter ->
                    FilterChip(
                        selected = uiState.selectedSearchFilter == filter,
                        onClick = { onSelectFilter(filter) },
                        label = { Text(filter.label) },
                        modifier = Modifier.testTag("search_filter_${filter.name.lowercase()}")
                    )
                }
            }
        }

        if (results.isEmpty()) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 40.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.SearchOff,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(44.dp)
                    )
                    Text(
                        text = "No matching tracks",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "Try a different keyword or switch filters.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            item {
                Text(
                    text = "${results.size} results",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp)
                )
            }

            items(results, key = { "search_${it.id}" }) { track ->
                TrackRow(
                    track = track,
                    isCurrentTrack = playerState.currentTrack?.id == track.id,
                    isPlaying = playerState.isPlaying,
                    isEffectivelyOffline = uiState.isEffectivelyOffline,
                    downloadProgress = uiState.downloadProgress[track.id],
                    onPlayClick = { onPlayTrack(track, results) },
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
