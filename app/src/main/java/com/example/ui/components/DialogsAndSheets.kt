package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AudioFile
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.FileOpen
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Login
import androidx.compose.material.icons.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SmartDisplay
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.PlaylistWithTracks
import com.example.data.model.TrackEntity
import com.example.data.remote.CountryMarket
import com.example.data.remote.GlobalMusicMarkets
import com.example.data.remote.SpotifyPlaylist

@Composable
fun AddStreamDialog(
    onDismiss: () -> Unit,
    onConfirm: (title: String, artist: String, url: String, genre: String, isLive: Boolean, playNow: Boolean) -> Unit,
) {
    var title by remember { mutableStateOf("") }
    var artist by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var genre by remember { mutableStateOf("Electronic") }
    var isLive by remember { mutableStateOf(false) }

    val isUrlValid = url.trim().startsWith("http://") || url.trim().startsWith("https://")
    val canSubmit = title.isNotBlank() && isUrlValid

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Add Online Stream",
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Paste any direct MP3, AAC, or HLS (.m3u8) audio URL to stream online or save offline.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Track or Station Name") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("stream_title_input")
                )
                OutlinedTextField(
                    value = artist,
                    onValueChange = { artist = it },
                    label = { Text("Artist / Broadcaster") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("stream_artist_input")
                )
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("Stream URL (https://...)") },
                    singleLine = true,
                    isError = url.isNotBlank() && !isUrlValid,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("stream_url_input")
                )
                OutlinedTextField(
                    value = genre,
                    onValueChange = { genre = it },
                    label = { Text("Genre") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Live Radio Stream", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "Continuous broadcast (not downloadable)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = isLive,
                        onCheckedChange = { isLive = it },
                        modifier = Modifier.testTag("stream_live_switch")
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirm(title, artist, url, genre, isLive, true)
                    onDismiss()
                },
                enabled = canSubmit,
                modifier = Modifier.testTag("stream_submit_button")
            ) {
                Text("Stream Now")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun CreatePlaylistDialog(
    onDismiss: () -> Unit,
    onCreate: (name: String, description: String, coverKey: String) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var selectedCover by remember { mutableStateOf("lofi") }
    val covers = listOf(
        "lofi" to "Twilight",
        "electronic" to "Prism",
        "acoustic" to "Warm Wood",
        "studio" to "Studio"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create Playlist", style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Playlist Name") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("playlist_name_input")
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = "Cover Style",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    covers.forEach { (key, label) ->
                        FilterChip(
                            selected = selectedCover == key,
                            onClick = { selectedCover = key },
                            label = { Text(label) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onCreate(name, description, selectedCover)
                    onDismiss()
                },
                enabled = name.isNotBlank(),
                modifier = Modifier.testTag("create_playlist_confirm_button")
            ) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddToPlaylistSheet(
    track: TrackEntity,
    playlists: List<PlaylistWithTracks>,
    onDismiss: () -> Unit,
    onSelectPlaylist: (PlaylistWithTracks) -> Unit,
    onCreateNewPlaylist: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            Text(
                text = "Add to Playlist",
                style = MaterialTheme.typography.titleLarge
            )
            Text(
                text = track.title,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(14.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .border(
                        1.dp,
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                        RoundedCornerShape(14.dp)
                    )
                    .clickable {
                        onDismiss()
                        onCreateNewPlaylist()
                    }
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Rounded.Add,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = "Create New Playlist",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(Modifier.height(12.dp))

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(bottom = 24.dp)
            ) {
                items(playlists, key = { it.playlist.id }) { item ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .clickable {
                                onSelectPlaylist(item)
                                onDismiss()
                            }
                            .padding(horizontal = 8.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ArtworkImage(
                            artworkResKey = item.playlist.coverResKey,
                            modifier = Modifier.size(46.dp),
                            shape = RoundedCornerShape(10.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = item.playlist.name,
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(
                                text = "${item.tracks.size} tracks",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(
                            imageVector = Icons.Rounded.PlaylistPlay,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

enum class ImportPlaylistSourceTab(val label: String) {
    YOUTUBE_MUSIC("YouTube Music"),
    SPOTIFY("Spotify"),
    FILE_AND_LOCAL("File / Audio")
}

@Composable
fun ImportPlaylistDialog(
    initialTab: ImportPlaylistSourceTab = ImportPlaylistSourceTab.YOUTUBE_MUSIC,
    spotifyConfigured: Boolean = false,
    spotifySignedIn: Boolean = false,
    spotifyPlaylists: List<SpotifyPlaylist> = emptyList(),
    onConnectSpotify: () -> Unit = {},
    onImportSpotifyAccountPlaylist: (SpotifyPlaylist) -> Unit = {},
    onDismiss: () -> Unit,
    onImportFromUrl: (url: String, customName: String) -> Unit,
    onPickPlaylistFile: (customName: String) -> Unit,
    onPickAudioFilesForPlaylist: (customName: String) -> Unit,
) {
    var selectedTab by remember(initialTab) { mutableStateOf(initialTab) }
    var playlistUrl by remember { mutableStateOf("") }
    var customName by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Import Playlist",
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    ImportPlaylistSourceTab.entries.forEach { tab ->
                        FilterChip(
                            selected = selectedTab == tab,
                            onClick = { selectedTab = tab },
                            label = {
                                Text(
                                    text = when (tab) {
                                        ImportPlaylistSourceTab.YOUTUBE_MUSIC -> "YT Music"
                                        ImportPlaylistSourceTab.SPOTIFY -> "Spotify"
                                        ImportPlaylistSourceTab.FILE_AND_LOCAL -> "Files"
                                    },
                                    maxLines = 1
                                )
                            },
                            modifier = Modifier.testTag("import_tab_${tab.name.lowercase()}")
                        )
                    }
                }

                OutlinedTextField(
                    value = customName,
                    onValueChange = { customName = it },
                    label = { Text("Playlist Name (optional)") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("import_playlist_name_input")
                )

                when (selectedTab) {
                    ImportPlaylistSourceTab.YOUTUBE_MUSIC -> {
                        Text(
                            text = "Paste any public YouTube Music or YouTube playlist link (music.youtube.com/playlist?list=... or youtube.com/playlist?list=...).",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedTextField(
                            value = playlistUrl,
                            onValueChange = { playlistUrl = it },
                            label = { Text("YouTube Music Playlist URL") },
                            placeholder = { Text("https://music.youtube.com/playlist?list=...") },
                            leadingIcon = { Icon(Icons.Rounded.SmartDisplay, contentDescription = null) },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("import_playlist_url_input")
                        )
                        Button(
                            onClick = {
                                onImportFromUrl(playlistUrl, customName)
                                onDismiss()
                            },
                            enabled = playlistUrl.isNotBlank(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("import_playlist_url_button")
                        ) {
                            Icon(Icons.Rounded.SmartDisplay, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Import YouTube Music Playlist")
                        }
                    }

                    ImportPlaylistSourceTab.SPOTIFY -> {
                        Text(
                            text = "Paste any public Spotify playlist URL (open.spotify.com/playlist/... or spotify:playlist:...) to import into RinMusic.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedTextField(
                            value = playlistUrl,
                            onValueChange = { playlistUrl = it },
                            label = { Text("Spotify Playlist URL") },
                            placeholder = { Text("https://open.spotify.com/playlist/...") },
                            leadingIcon = { Icon(Icons.Rounded.GraphicEq, contentDescription = null) },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("import_playlist_url_input")
                        )
                        Button(
                            onClick = {
                                onImportFromUrl(playlistUrl, customName)
                                onDismiss()
                            },
                            enabled = playlistUrl.isNotBlank(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("import_playlist_url_button")
                        ) {
                            Icon(Icons.Rounded.GraphicEq, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Import Spotify Playlist")
                        }

                        if (spotifyConfigured) {
                            HorizontalDivider()
                            if (!spotifySignedIn) {
                                OutlinedButton(
                                    onClick = {
                                        onDismiss()
                                        onConnectSpotify()
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("import_connect_spotify_button")
                                ) {
                                    Icon(Icons.Rounded.Login, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text("Connect Spotify for Private Playlists")
                                }
                            } else if (spotifyPlaylists.isNotEmpty()) {
                                Text(
                                    text = "Your Spotify Playlists",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                LazyColumn(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(max = 180.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    items(spotifyPlaylists, key = { "sp_imp_${it.id}" }) { sp ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                                                .clickable {
                                                    onImportSpotifyAccountPlaylist(sp)
                                                    onDismiss()
                                                }
                                                .padding(horizontal = 10.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = sp.name,
                                                    style = MaterialTheme.typography.titleSmall,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    text = "${sp.trackCount} tracks",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            Icon(
                                                imageVector = Icons.Rounded.Add,
                                                contentDescription = "Import Spotify playlist",
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    ImportPlaylistSourceTab.FILE_AND_LOCAL -> {
                        OutlinedTextField(
                            value = playlistUrl,
                            onValueChange = { playlistUrl = it },
                            label = { Text("Remote .m3u / .m3u8 / .pls URL") },
                            placeholder = { Text("https://example.com/playlist.m3u8") },
                            leadingIcon = { Icon(Icons.Rounded.Link, contentDescription = null) },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("import_playlist_url_input")
                        )
                        Button(
                            onClick = {
                                onImportFromUrl(playlistUrl, customName)
                                onDismiss()
                            },
                            enabled = playlistUrl.isNotBlank(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("import_playlist_url_button")
                        ) {
                            Icon(Icons.Rounded.Link, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Import from M3U / PLS URL")
                        }

                        HorizontalDivider()

                        FilledTonalButton(
                            onClick = {
                                onDismiss()
                                onPickPlaylistFile(customName)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("import_playlist_file_button")
                        ) {
                            Icon(Icons.Rounded.FileOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Pick .m3u / .m3u8 / .pls File")
                        }

                        OutlinedButton(
                            onClick = {
                                onDismiss()
                                onPickAudioFilesForPlaylist(customName)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("import_playlist_audio_files_button")
                        ) {
                            Icon(Icons.Rounded.AudioFile, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Select Audio Files as Playlist")
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CountryPickerSheet(
    selectedCountryCode: String,
    onDismiss: () -> Unit,
    onSelectCountry: (CountryMarket) -> Unit,
) {
    var filterText by remember { mutableStateOf("") }
    val markets = remember(filterText) {
        val q = filterText.trim().lowercase()
        if (q.isEmpty()) {
            GlobalMusicMarkets.ALL_MARKETS
        } else {
            GlobalMusicMarkets.ALL_MARKETS.filter {
                it.name.lowercase().contains(q) ||
                    it.code.lowercase().contains(q) ||
                    it.region.lowercase().contains(q)
            }
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Public,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Column {
                    Text(
                        text = "Music Search Region",
                        style = MaterialTheme.typography.titleLarge
                    )
                    Text(
                        text = "Search All Countries worldwide or pick any storefront",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            OutlinedTextField(
                value = filterText,
                onValueChange = { filterText = it },
                placeholder = { Text("Search country or continent...") },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                trailingIcon = {
                    if (filterText.isNotEmpty()) {
                        IconButton(onClick = { filterText = "" }) {
                            Icon(Icons.Rounded.Clear, contentDescription = "Clear country filter")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("country_picker_search_input")
            )

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .padding(bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(markets, key = { it.code }) { market ->
                    val isSelected = market.code.equals(selectedCountryCode, ignoreCase = true)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(
                                if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                                else MaterialTheme.colorScheme.surfaceContainerLow
                            )
                            .border(
                                width = 1.dp,
                                color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                                else MaterialTheme.colorScheme.outlineVariant,
                                shape = RoundedCornerShape(14.dp)
                            )
                            .clickable {
                                onSelectCountry(market)
                                onDismiss()
                            }
                            .testTag("country_option_${market.code.lowercase()}")
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (market.code == "ALL") {
                                    "All Countries (Global Mix)"
                                } else {
                                    "${market.name} (${market.code})"
                                },
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                ),
                                color = if (isSelected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (market.code == "ALL") {
                                    "Searches across 12+ global regions simultaneously"
                                } else {
                                    market.region
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Rounded.Check,
                                contentDescription = "Selected",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }
    }
}

