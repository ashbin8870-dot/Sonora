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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.DownloadForOffline
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.TrackEntity
import com.example.data.model.TrackSourceType
import com.example.data.remote.GlobalMusicMarkets
import com.example.data.remote.GlobalMusicTrack
import com.example.player.PlayerUiState
import com.example.ui.components.ArtworkImage
import com.example.ui.components.SourcePill
import com.example.ui.components.TrackRow
import com.example.ui.components.formatDuration
import com.example.ui.theme.ElectricCyan
import com.example.ui.viewmodel.GlobalSearchUiState
import com.example.ui.viewmodel.MusicHomeUiState
import com.example.ui.viewmodel.SearchFilter

private val QuickCountryCodes = listOf(
    "ALL", "US", "IN", "JP", "KR", "GB", "BR", "NG", "DE", "FR", "MX", "TR", "AU", "ID"
)

private val GlobalDiscoveryPrompts = listOf(
    "Top Hits", "Bollywood", "K-Pop", "Afrobeats", "J-Pop", "Latin Pop", "Lo-Fi"
)

@Composable
fun SearchScreen(
    uiState: MusicHomeUiState,
    globalSearchState: GlobalSearchUiState,
    playerState: PlayerUiState,
    onQueryChange: (String) -> Unit,
    onSearchSubmit: (String?) -> Unit,
    onSelectCountry: (String) -> Unit,
    onOpenCountryPicker: () -> Unit,
    onSelectFilter: (SearchFilter) -> Unit,
    onPlayTrack: (TrackEntity, List<TrackEntity>) -> Unit,
    onToggleFavorite: (TrackEntity) -> Unit,
    onDownloadTrack: (TrackEntity) -> Unit,
    onRemoveDownload: (TrackEntity) -> Unit,
    onPlayNext: (TrackEntity) -> Unit,
    onAddToQueue: (TrackEntity) -> Unit,
    onAddToPlaylist: (TrackEntity) -> Unit,
    onDeleteTrack: (TrackEntity) -> Unit,
    onPlayGlobalTrack: (GlobalMusicTrack) -> Unit,
    onPlayNextGlobalTrack: (GlobalMusicTrack) -> Unit,
    onAddGlobalTrackToQueue: (GlobalMusicTrack) -> Unit,
    onDownloadGlobalTrack: (GlobalMusicTrack) -> Unit,
    onAddGlobalTrackToPlaylist: (GlobalMusicTrack) -> Unit,
    bottomContentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    val libraryResults = uiState.filteredSearchResults
    val keyboardController = LocalSoftwareKeyboardController.current
    val selectedCountry = globalSearchState.selectedCountry

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            top = 12.dp,
            bottom = bottomContentPadding.calculateBottomPadding() + 16.dp
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Header + Search Input + All-Countries Picker
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
                            text = "Search",
                            style = MaterialTheme.typography.headlineMedium
                        )
                        Text(
                            text = "Worldwide music across 110+ countries & your library",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    FilledTonalButton(
                        onClick = onOpenCountryPicker,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.testTag("country_picker_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Public,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = if (selectedCountry.code == "ALL") "All Countries" else selectedCountry.code,
                            maxLines = 1
                        )
                        Icon(
                            imageVector = Icons.Rounded.ArrowDropDown,
                            contentDescription = "Choose country",
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = uiState.searchQuery,
                        onValueChange = onQueryChange,
                        placeholder = { Text("Search songs, artists, countries...") },
                        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                        trailingIcon = {
                            if (uiState.searchQuery.isNotEmpty()) {
                                IconButton(onClick = { onQueryChange("") }) {
                                    Icon(Icons.Rounded.Clear, contentDescription = "Clear search")
                                }
                            }
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(
                            onSearch = {
                                keyboardController?.hide()
                                onSearchSubmit(null)
                            }
                        ),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("search_query_input")
                    )
                    Button(
                        onClick = {
                            keyboardController?.hide()
                            onSearchSubmit(null)
                        },
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .height(54.dp)
                            .testTag("search_submit_button")
                    ) {
                        Icon(Icons.Rounded.Search, contentDescription = "Search Worldwide")
                    }
                }
            }
        }

        // Quick Country Selector Row
        item {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(QuickCountryCodes) { code ->
                    val market = GlobalMusicMarkets.findByCode(code)
                    val isSelected = globalSearchState.selectedCountryCode.equals(code, ignoreCase = true)
                    FilterChip(
                        selected = isSelected,
                        onClick = { onSelectCountry(code) },
                        leadingIcon = if (code == "ALL") {
                            { Icon(Icons.Rounded.Public, contentDescription = null, modifier = Modifier.size(16.dp)) }
                        } else null,
                        label = {
                            Text(if (code == "ALL") "All Countries" else "${market.name} (${market.code})")
                        },
                        modifier = Modifier.testTag("country_chip_${code.lowercase()}")
                    )
                }
                item {
                    FilterChip(
                        selected = selectedCountry.code !in QuickCountryCodes,
                        onClick = onOpenCountryPicker,
                        label = {
                            Text(
                                if (selectedCountry.code !in QuickCountryCodes) {
                                    "${selectedCountry.name} (${selectedCountry.code})"
                                } else {
                                    "110+ Countries..."
                                }
                            )
                        },
                        modifier = Modifier.testTag("country_chip_more")
                    )
                }
            }
        }

        // Quick Worldwide Prompts when query is empty
        if (uiState.searchQuery.isBlank()) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Explore Global Music",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 20.dp)
                    )
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(GlobalDiscoveryPrompts) { prompt ->
                            FilterChip(
                                selected = false,
                                onClick = { onSearchSubmit(prompt) },
                                label = { Text(prompt) },
                                modifier = Modifier.testTag("global_prompt_${prompt.lowercase().replace(" ", "_")}")
                            )
                        }
                    }
                }
            }
        }

        // Worldwide Music Search Results Section
        if (uiState.searchQuery.isNotBlank() || globalSearchState.isLoading || globalSearchState.results.isNotEmpty()) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (selectedCountry.code == "ALL") {
                            "Worldwide Results (${globalSearchState.results.size})"
                        } else {
                            "${selectedCountry.name} Results (${globalSearchState.results.size})"
                        },
                        style = MaterialTheme.typography.titleMedium
                    )
                    if (selectedCountry.code != "ALL") {
                        SourcePill(label = selectedCountry.code, accentColor = ElectricCyan)
                    } else {
                        SourcePill(label = "ALL COUNTRIES", accentColor = ElectricCyan)
                    }
                }
            }

            when {
                globalSearchState.isLoading -> {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                }
                uiState.isEffectivelyOffline -> {
                    item {
                        Text(
                            text = "Offline Mode is active. Showing saved library matches below.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 20.dp)
                        )
                    }
                }
                globalSearchState.errorMessage != null -> {
                    item {
                        Text(
                            text = globalSearchState.errorMessage,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(horizontal = 20.dp)
                        )
                    }
                }
                globalSearchState.results.isEmpty() && globalSearchState.lastSearchedQuery.isNotBlank() -> {
                    item {
                        Text(
                            text = "No global results found for \"${globalSearchState.lastSearchedQuery}\" in ${selectedCountry.name}.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 20.dp)
                        )
                    }
                }
                else -> {
                    items(globalSearchState.results, key = { "global_${it.countryCode}_${it.id}" }) { track ->
                        val isPlayingThis = playerState.currentTrack?.id == track.id
                        GlobalTrackRow(
                            track = track,
                            isActive = isPlayingThis,
                            isPlaying = playerState.isPlaying && isPlayingThis,
                            onPlayClick = { onPlayGlobalTrack(track) },
                            onAddToPlaylistClick = { onAddGlobalTrackToPlaylist(track) },
                            onPlayNextClick = { onPlayNextGlobalTrack(track) },
                            onAddToQueueClick = { onAddGlobalTrackToQueue(track) },
                            onDownloadClick = { onDownloadGlobalTrack(track) },
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    }
                }
            }
        }

        // Local & Saved Library Section
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "In Your Library (${libraryResults.size})",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp)
                )
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
        }

        if (libraryResults.isEmpty()) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.SearchOff,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(38.dp)
                    )
                    Text(
                        text = "No matching library tracks",
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
            items(libraryResults, key = { "search_${it.id}" }) { track ->
                TrackRow(
                    track = track,
                    isCurrentTrack = playerState.currentTrack?.id == track.id,
                    isPlaying = playerState.isPlaying,
                    isEffectivelyOffline = uiState.isEffectivelyOffline,
                    downloadProgress = uiState.downloadProgress[track.id],
                    onPlayClick = { onPlayTrack(track, libraryResults) },
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

@Composable
private fun GlobalTrackRow(
    track: GlobalMusicTrack,
    isActive: Boolean,
    isPlaying: Boolean,
    onPlayClick: () -> Unit,
    onAddToPlaylistClick: () -> Unit,
    onPlayNextClick: () -> Unit,
    onAddToQueueClick: () -> Unit,
    onDownloadClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(
                if (isActive) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.42f)
                else MaterialTheme.colorScheme.surfaceContainerLow
            )
            .border(
                width = 1.dp,
                color = if (isActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
                else MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(16.dp)
            )
            .clickable(onClick = onPlayClick)
            .testTag("global_track_row_${track.trackId}")
            .padding(start = 10.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ArtworkImage(
            artworkResKey = "electronic",
            artworkUrl = track.artworkUrl,
            isPlaying = isPlaying,
            modifier = Modifier.size(54.dp),
            shape = RoundedCornerShape(12.dp)
        )
        Spacer(Modifier.width(12.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(
                text = track.title,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.SemiBold
                ),
                color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${track.artist} • ${track.genre}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                SourcePill(
                    label = "${track.countryCode} • ${track.countryName}",
                    accentColor = ElectricCyan,
                    icon = Icons.Rounded.Public
                )
                if (track.durationMs > 0L) {
                    Text(
                        text = formatDuration(track.durationMs),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        IconButton(onClick = onPlayClick) {
            Icon(
                imageVector = Icons.Rounded.PlayCircle,
                contentDescription = "Play in Sonora",
                tint = MaterialTheme.colorScheme.primary
            )
        }

        IconButton(onClick = onAddToPlaylistClick) {
            Icon(
                imageVector = Icons.Rounded.PlaylistAdd,
                contentDescription = "Add to playlist",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Box {
            IconButton(onClick = { menuExpanded = true }) {
                Icon(
                    imageVector = Icons.Rounded.MoreVert,
                    contentDescription = "More track options",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false }
            ) {
                DropdownMenuItem(
                    text = { Text("Play Next") },
                    leadingIcon = { Icon(Icons.Rounded.PlaylistPlay, contentDescription = null) },
                    onClick = {
                        menuExpanded = false
                        onPlayNextClick()
                    }
                )
                DropdownMenuItem(
                    text = { Text("Add to Queue") },
                    leadingIcon = { Icon(Icons.Rounded.QueueMusic, contentDescription = null) },
                    onClick = {
                        menuExpanded = false
                        onAddToQueueClick()
                    }
                )
                DropdownMenuItem(
                    text = { Text("Download Offline Copy") },
                    leadingIcon = { Icon(Icons.Rounded.DownloadForOffline, contentDescription = null) },
                    onClick = {
                        menuExpanded = false
                        onDownloadClick()
                    }
                )
            }
        }
    }
}
