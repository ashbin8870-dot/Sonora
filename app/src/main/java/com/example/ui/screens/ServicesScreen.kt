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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CastConnected
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Login
import androidx.compose.material.icons.rounded.Logout
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SmartDisplay
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.remote.SpotifyPlaylist
import com.example.data.remote.SpotifyTrack
import com.example.data.remote.YouTubeMusicTrack
import com.example.ui.components.ArtworkImage
import com.example.ui.components.ConnectivityModeChip
import com.example.ui.components.SourcePill
import com.example.ui.components.formatDuration
import com.example.ui.theme.EmeraldOffline
import com.example.ui.theme.SoftRose
import com.example.ui.viewmodel.EmbedPlayerUiState
import com.example.ui.viewmodel.MusicHomeUiState
import com.example.ui.viewmodel.ServicesUiState
import com.example.ui.viewmodel.StreamingServiceTab

private val SpotifyGreen = Color(0xFF1DB954)
private val YouTubeMusicRed = Color(0xFFFF4E45)

@Composable
fun ServicesScreen(
    homeState: MusicHomeUiState,
    servicesState: ServicesUiState,
    embedState: EmbedPlayerUiState,
    activeMedia3TrackId: String?,
    onToggleOfflineMode: () -> Unit,
    onSelectService: (StreamingServiceTab) -> Unit,
    // Spotify callbacks (all strictly in-app or remote Spotify Connect)
    onConnectSpotify: () -> Unit,
    onDisconnectSpotify: () -> Unit,
    onRefreshSpotify: () -> Unit,
    onSelectSpotifyPlaylist: (SpotifyPlaylist) -> Unit,
    onSpotifyQueryChange: (String) -> Unit,
    onSearchSpotify: () -> Unit,
    onSpotifyDirectUrlChange: (String) -> Unit,
    onPlaySpotifyDirectUrl: () -> Unit,
    onPlaySpotifyTrack: (SpotifyTrack) -> Unit,
    onPlaySpotifyInAppEmbed: (SpotifyTrack) -> Unit,
    onPlaySpotifyConnect: (SpotifyTrack) -> Unit,
    onToggleSpotifyConnectPlayPause: () -> Unit,
    onNextSpotifyConnect: () -> Unit,
    // YouTube Music callbacks (all strictly in-app)
    onYouTubeQueryChange: (String) -> Unit,
    onSearchYouTube: (String?) -> Unit,
    onYouTubeDirectUrlChange: (String) -> Unit,
    onPlayYouTubeDirectUrl: () -> Unit,
    onPlayYouTubeTrack: (YouTubeMusicTrack) -> Unit,
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
                        text = "Music Clients",
                        style = MaterialTheme.typography.headlineMedium
                    )
                    Text(
                        text = "Play Spotify & YouTube Music inside Sonora",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                ConnectivityModeChip(
                    isDeviceOnline = homeState.isDeviceOnline,
                    offlineOnlyMode = homeState.preferences.offlineOnlyMode,
                    onToggleOfflineMode = onToggleOfflineMode
                )
            }
        }

        // Service Selector Chips
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                StreamingServiceTab.entries.forEach { tab ->
                    val selected = servicesState.selectedService == tab
                    val icon = if (tab == StreamingServiceTab.SPOTIFY) {
                        Icons.Rounded.GraphicEq
                    } else {
                        Icons.Rounded.SmartDisplay
                    }
                    FilterChip(
                        selected = selected,
                        onClick = { onSelectService(tab) },
                        leadingIcon = {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = when {
                                    selected && tab == StreamingServiceTab.SPOTIFY -> SpotifyGreen
                                    selected && tab == StreamingServiceTab.YOUTUBE_MUSIC -> YouTubeMusicRed
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        },
                        label = { Text(tab.label) },
                        modifier = Modifier.testTag("service_tab_${tab.name.lowercase()}")
                    )
                }
            }
        }

        if (servicesState.selectedService == StreamingServiceTab.SPOTIFY) {
            // --- SPOTIFY CLIENT ---
            item {
                SpotifyConnectionCard(
                    state = servicesState,
                    isEffectivelyOffline = homeState.isEffectivelyOffline,
                    onConnect = onConnectSpotify,
                    onDisconnect = onDisconnectSpotify,
                    onRefresh = onRefreshSpotify,
                    onDirectUrlChange = onSpotifyDirectUrlChange,
                    onPlayDirectUrl = onPlaySpotifyDirectUrl,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }

            if (servicesState.spotifySignedIn) {
                servicesState.spotifyConnectState?.let { connect ->
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(SpotifyGreen.copy(alpha = 0.14f))
                                .border(1.dp, SpotifyGreen.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.CastConnected,
                                contentDescription = null,
                                tint = SpotifyGreen,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Spotify Connect • ${connect.deviceName}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = SpotifyGreen
                                )
                                Text(
                                    text = connect.trackTitle ?: "Active Spotify Session",
                                    style = MaterialTheme.typography.titleSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (!connect.artistName.isNullOrBlank()) {
                                    Text(
                                        text = connect.artistName,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1
                                    )
                                }
                            }
                            IconButton(onClick = onToggleSpotifyConnectPlayPause) {
                                Icon(
                                    imageVector = if (connect.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                    contentDescription = "Toggle Spotify playback",
                                    tint = SpotifyGreen
                                )
                            }
                            IconButton(onClick = onNextSpotifyConnect) {
                                Icon(
                                    imageVector = Icons.Rounded.SkipNext,
                                    contentDescription = "Next on Spotify",
                                    tint = SpotifyGreen
                                )
                            }
                        }
                    }
                }

                // Spotify Search Bar
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = servicesState.spotifySearchQuery,
                            onValueChange = onSpotifyQueryChange,
                            placeholder = { Text("Search Spotify tracks...") },
                            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("spotify_search_input")
                        )
                        Button(
                            onClick = onSearchSpotify,
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = SpotifyGreen,
                                contentColor = Color.Black
                            ),
                            modifier = Modifier
                                .height(54.dp)
                                .testTag("spotify_search_button")
                        ) {
                            Text("Go")
                        }
                    }
                }

                // User's Spotify Playlists Carousel
                if (servicesState.spotifyPlaylists.isNotEmpty()) {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "Your Spotify Playlists",
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.padding(horizontal = 20.dp)
                            )
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                items(servicesState.spotifyPlaylists, key = { it.id }) { pl ->
                                    Column(
                                        modifier = Modifier
                                            .width(134.dp)
                                            .clip(RoundedCornerShape(14.dp))
                                            .background(MaterialTheme.colorScheme.surfaceContainerLow)
                                            .border(
                                                1.dp,
                                                MaterialTheme.colorScheme.outlineVariant,
                                                RoundedCornerShape(14.dp)
                                            )
                                            .clickable { onSelectSpotifyPlaylist(pl) }
                                            .padding(10.dp)
                                    ) {
                                        ArtworkImage(
                                            artworkResKey = "electronic",
                                            artworkUrl = pl.imageUrl,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(106.dp),
                                            shape = RoundedCornerShape(10.dp)
                                        )
                                        Spacer(Modifier.height(6.dp))
                                        Text(
                                            text = pl.name,
                                            style = MaterialTheme.typography.titleSmall,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "${pl.trackCount} tracks",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                if (servicesState.spotifyLoading) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = SpotifyGreen)
                        }
                    }
                }

                if (servicesState.spotifyTracks.isNotEmpty()) {
                    item {
                        Text(
                            text = servicesState.spotifySelectedPlaylistName ?: "Spotify Tracks",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(horizontal = 20.dp)
                        )
                    }

                    items(servicesState.spotifyTracks, key = { "sp_${it.id}" }) { track ->
                        val isCurrentlyActive =
                            embedState.spotifyTrack?.id == track.id ||
                                activeMedia3TrackId == "spotify_preview_${track.id}"
                        SpotifyTrackRow(
                            track = track,
                            isActive = isCurrentlyActive,
                            onPlayInSonora = { onPlaySpotifyTrack(track) },
                            onPlayEmbed = { onPlaySpotifyInAppEmbed(track) },
                            onPlayConnect = { onPlaySpotifyConnect(track) },
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    }
                }
            }
        } else {
            // --- YOUTUBE MUSIC CLIENT ---
            item {
                YouTubeMusicSetupAndLinkCard(
                    state = servicesState,
                    onDirectUrlChange = onYouTubeDirectUrlChange,
                    onPlayDirectUrl = onPlayYouTubeDirectUrl,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }

            // YouTube Music Search Bar & Quick Genre Chips
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = servicesState.youtubeSearchQuery,
                            onValueChange = onYouTubeQueryChange,
                            placeholder = { Text("Search YouTube Music...") },
                            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("yt_search_input")
                        )
                        Button(
                            onClick = { onSearchYouTube(null) },
                            enabled = servicesState.youtubeConfigured && servicesState.youtubeSearchQuery.isNotBlank(),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = YouTubeMusicRed,
                                contentColor = Color.White
                            ),
                            modifier = Modifier
                                .height(54.dp)
                                .testTag("yt_search_button")
                        ) {
                            Text("Search")
                        }
                    }

                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(
                            listOf("Lo-Fi Beats", "Acoustic Live", "Synthwave", "Jazz Hop", "Indie Rock")
                        ) { chip ->
                            FilterChip(
                                selected = servicesState.youtubeSearchQuery.equals(chip, ignoreCase = true),
                                onClick = { onSearchYouTube(chip) },
                                enabled = servicesState.youtubeConfigured,
                                label = { Text(chip) }
                            )
                        }
                    }
                }
            }

            if (servicesState.youtubeLoading) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = YouTubeMusicRed)
                    }
                }
            }

            if (servicesState.youtubeTracks.isNotEmpty()) {
                item {
                    Text(
                        text = "YouTube Music Results",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(horizontal = 20.dp)
                    )
                }

                items(servicesState.youtubeTracks, key = { "yt_${it.videoId}" }) { track ->
                    YouTubeTrackRow(
                        track = track,
                        isActive = embedState.youtubeTrack?.videoId == track.videoId,
                        onPlayInSonora = { onPlayYouTubeTrack(track) },
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun SpotifyConnectionCard(
    state: ServicesUiState,
    isEffectivelyOffline: Boolean,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onRefresh: () -> Unit,
    onDirectUrlChange: (String) -> Unit,
    onPlayDirectUrl: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .border(1.dp, SpotifyGreen.copy(alpha = 0.35f), RoundedCornerShape(20.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.GraphicEq,
                    contentDescription = null,
                    tint = SpotifyGreen
                )
                Text(
                    text = "Spotify Client",
                    style = MaterialTheme.typography.titleLarge
                )
            }
            SourcePill(
                label = when {
                    !state.spotifyConfigured -> "IN-APP LINK"
                    state.spotifySignedIn -> state.spotifyProfile?.productTier ?: "CONNECTED"
                    else -> "OAUTH PKCE"
                },
                accentColor = if (state.spotifySignedIn) SpotifyGreen else MaterialTheme.colorScheme.tertiary
            )
        }

        when {
            !state.spotifyConfigured -> {
                Text(
                    text = "Add SPOTIFY_CLIENT_ID in the Google AI Studio Secrets panel (redirect URI sonora://spotify-callback) to browse your Spotify library. You can also paste any Spotify track link below to play it inside Sonora.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            !state.spotifySignedIn -> {
                Text(
                    text = "Sign in with Spotify to load your playlists, liked songs, and search, then play tracks directly inside Sonora without switching apps.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Button(
                    onClick = onConnect,
                    enabled = !isEffectivelyOffline,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = SpotifyGreen,
                        contentColor = Color.Black
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("spotify_connect_button")
                ) {
                    Icon(Icons.Rounded.Login, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Connect with Spotify")
                }
            }
            else -> {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ArtworkImage(
                        artworkResKey = "electronic",
                        artworkUrl = state.spotifyProfile?.avatarUrl,
                        modifier = Modifier.size(44.dp),
                        shape = CircleShape
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = state.spotifyProfile?.displayName ?: "Spotify User",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "${state.spotifyPlaylists.size} playlists loaded • Plays in Sonora",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = onRefresh) {
                        Icon(Icons.Rounded.Refresh, contentDescription = "Refresh Spotify")
                    }
                    IconButton(onClick = onDisconnect) {
                        Icon(
                            Icons.Rounded.Logout,
                            contentDescription = "Disconnect Spotify",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }

        // Direct Spotify Track URL input (always available to play in-app)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = state.spotifyDirectUrl,
                onValueChange = onDirectUrlChange,
                placeholder = { Text("Paste open.spotify.com/track/... link") },
                leadingIcon = { Icon(Icons.Rounded.Link, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .weight(1f)
                    .testTag("spotify_direct_url_input")
            )
            FilledTonalButton(
                onClick = onPlayDirectUrl,
                enabled = state.spotifyDirectUrl.isNotBlank() && !isEffectivelyOffline,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .height(54.dp)
                    .testTag("spotify_direct_play_button")
            ) {
                Text("Play")
            }
        }
    }
}

@Composable
private fun SpotifyTrackRow(
    track: SpotifyTrack,
    isActive: Boolean,
    onPlayInSonora: () -> Unit,
    onPlayEmbed: () -> Unit,
    onPlayConnect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(
                if (isActive) SpotifyGreen.copy(alpha = 0.12f)
                else MaterialTheme.colorScheme.surfaceContainerLow
            )
            .border(
                width = 1.dp,
                color = if (isActive) SpotifyGreen.copy(alpha = 0.55f) else MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(16.dp)
            )
            .clickable(onClick = onPlayInSonora)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ArtworkImage(
            artworkResKey = "electronic",
            artworkUrl = track.artworkUrl,
            isPlaying = isActive,
            modifier = Modifier.size(54.dp),
            shape = RoundedCornerShape(10.dp)
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = track.title,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${track.artist} • ${track.album}",
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
                    label = if (track.previewUrl != null) "IN-APP 30S" else "SPOTIFY IN-APP",
                    accentColor = SpotifyGreen
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
        IconButton(onClick = onPlayInSonora) {
            Icon(
                imageVector = Icons.Rounded.PlayCircle,
                contentDescription = "Play in Sonora",
                tint = SpotifyGreen
            )
        }
        if (track.previewUrl != null) {
            IconButton(onClick = onPlayEmbed) {
                Icon(
                    imageVector = Icons.Rounded.GraphicEq,
                    contentDescription = "Open Spotify in-app widget",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        IconButton(onClick = onPlayConnect) {
            Icon(
                imageVector = Icons.Rounded.CastConnected,
                contentDescription = "Play on Spotify Connect",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun YouTubeMusicSetupAndLinkCard(
    state: ServicesUiState,
    onDirectUrlChange: (String) -> Unit,
    onPlayDirectUrl: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .border(1.dp, YouTubeMusicRed.copy(alpha = 0.35f), RoundedCornerShape(20.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.SmartDisplay,
                    contentDescription = null,
                    tint = YouTubeMusicRed
                )
                Text(
                    text = "YouTube Music Client",
                    style = MaterialTheme.typography.titleLarge
                )
            }
            SourcePill(
                label = if (state.youtubeConfigured) "IN-APP READY" else "DIRECT LINK",
                accentColor = if (state.youtubeConfigured) EmeraldOffline else YouTubeMusicRed
            )
        }

        if (!state.youtubeConfigured) {
            Text(
                text = "Add YOUTUBE_API_KEY in the Google AI Studio Secrets panel to search YouTube Music directly. You can already paste any YouTube or YouTube Music link below to play it inside Sonora.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = state.youtubeDirectUrl,
                onValueChange = onDirectUrlChange,
                placeholder = { Text("Paste music.youtube.com or youtu.be URL") },
                leadingIcon = { Icon(Icons.Rounded.Link, contentDescription = null) },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .weight(1f)
                    .testTag("yt_direct_url_input")
            )
            FilledTonalButton(
                onClick = onPlayDirectUrl,
                enabled = state.youtubeDirectUrl.isNotBlank(),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .height(54.dp)
                    .testTag("yt_direct_play_button")
            ) {
                Text("Play")
            }
        }
    }
}

@Composable
private fun YouTubeTrackRow(
    track: YouTubeMusicTrack,
    isActive: Boolean,
    onPlayInSonora: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(
                if (isActive) YouTubeMusicRed.copy(alpha = 0.12f)
                else MaterialTheme.colorScheme.surfaceContainerLow
            )
            .border(
                width = 1.dp,
                color = if (isActive) YouTubeMusicRed.copy(alpha = 0.55f) else MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(16.dp)
            )
            .clickable(onClick = onPlayInSonora)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ArtworkImage(
            artworkResKey = "studio",
            artworkUrl = track.thumbnailUrl,
            isPlaying = isActive,
            modifier = Modifier.size(60.dp),
            shape = RoundedCornerShape(10.dp)
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                text = track.title,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = track.channelName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            SourcePill(label = "YOUTUBE MUSIC", accentColor = SoftRose)
        }
        IconButton(onClick = onPlayInSonora) {
            Icon(
                imageVector = Icons.Rounded.PlayCircle,
                contentDescription = "Play in Sonora",
                tint = YouTubeMusicRed
            )
        }
    }
}
