package com.example.ui.components

import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.R
import com.example.ui.viewmodel.EmbedCommand
import com.example.ui.viewmodel.EmbedPlaybackSource
import com.example.ui.viewmodel.EmbedPlayerUiState
import kotlinx.coroutines.flow.SharedFlow
import kotlin.math.roundToInt

private val SpotifyGreen = Color(0xFF1DB954)
private val YouTubeMusicRed = Color(0xFFFF4E45)

private class SonoraYouTubeJsBridge(
    private val onReadyCallback: () -> Unit,
    private val onStateCallback: (Int) -> Unit,
    private val onProgressCallback: (Long, Long) -> Unit,
    private val onErrorCallback: (Int) -> Unit,
) {
    private val mainHandler = Handler(Looper.getMainLooper())

    @JavascriptInterface
    fun onReady() {
        mainHandler.post { onReadyCallback() }
    }

    @JavascriptInterface
    fun onStateChanged(state: Int) {
        mainHandler.post { onStateCallback(state) }
    }

    @JavascriptInterface
    fun onProgress(positionMs: Long, durationMs: Long) {
        mainHandler.post { onProgressCallback(positionMs, durationMs) }
    }

    @JavascriptInterface
    fun onError(errorCode: Int) {
        mainHandler.post { onErrorCallback(errorCode) }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun PersistentEmbedWebView(
    embedState: EmbedPlayerUiState,
    embedCommands: SharedFlow<EmbedCommand>,
    targetBounds: Rect?,
    onYouTubeReady: () -> Unit,
    onYouTubeStateChanged: (Int) -> Unit,
    onYouTubeProgress: (Long, Long) -> Unit,
    onYouTubeError: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!embedState.isActive) return

    val context = LocalContext.current
    val density = LocalDensity.current

    var ytHtmlLoaded by remember { mutableStateOf(false) }
    var lastLoadedKey by remember { mutableStateOf<String?>(null) }

    val originUrl = remember(context.packageName) { "https://${context.packageName}" }

    val webView = remember {
        WebView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(android.graphics.Color.parseColor("#0E1015"))
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.mediaPlaybackRequiresUserGesture = false
            settings.loadsImagesAutomatically = true
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            settings.userAgentString = settings.userAgentString
                .replace("; wv)", ")")
                .replace("Version/4.0 ", "")
            CookieManager.getInstance().setAcceptCookie(true)
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
            webChromeClient = WebChromeClient()
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(
                    view: WebView?,
                    request: WebResourceRequest?
                ): Boolean {
                    val uri = request?.url ?: return false
                    val scheme = uri.scheme?.lowercase().orEmpty()
                    val host = uri.host?.lowercase().orEmpty()
                    // Block any intent://, spotify://, vnd.youtube:// or Play Store redirects
                    if (scheme != "http" && scheme != "https") return true
                    // Prevent main-frame navigation away from the embedded player to external YouTube/Spotify pages
                    if (request?.isForMainFrame == true && (
                            host.endsWith("spotify.link") ||
                            host == "youtu.be" ||
                            (host.contains("youtube.com") && uri.path?.startsWith("/watch") == true)
                        )
                    ) {
                        return true
                    }
                    return false
                }
            }
            addJavascriptInterface(
                SonoraYouTubeJsBridge(
                    onReadyCallback = onYouTubeReady,
                    onStateCallback = onYouTubeStateChanged,
                    onProgressCallback = onYouTubeProgress,
                    onErrorCallback = onYouTubeError
                ),
                "SonoraBridge"
            )
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            ytHtmlLoaded = false
            lastLoadedKey = null
            webView.removeJavascriptInterface("SonoraBridge")
            webView.stopLoading()
            webView.loadUrl("about:blank")
            webView.destroy()
        }
    }

    val currentKey = when (embedState.source) {
        EmbedPlaybackSource.YOUTUBE_MUSIC -> embedState.youtubeTrack?.let { "yt:${it.videoId}" }
        EmbedPlaybackSource.SPOTIFY_EMBED -> embedState.spotifyTrack?.let { "sp:${it.id}" }
        null -> null
    }

    LaunchedEffect(currentKey) {
        if (currentKey == null || currentKey == lastLoadedKey) return@LaunchedEffect
        when (embedState.source) {
            EmbedPlaybackSource.YOUTUBE_MUSIC -> {
                val track = embedState.youtubeTrack ?: return@LaunchedEffect
                if (ytHtmlLoaded && embedState.isReady) {
                    webView.evaluateJavascript("loadVideo('${track.videoId}')", null)
                } else {
                    ytHtmlLoaded = true
                    webView.loadDataWithBaseURL(
                        originUrl,
                        track.buildIframePlayerHtml(originUrl),
                        "text/html",
                        "UTF-8",
                        null
                    )
                }
            }
            EmbedPlaybackSource.SPOTIFY_EMBED -> {
                val track = embedState.spotifyTrack ?: return@LaunchedEffect
                ytHtmlLoaded = false
                webView.loadUrl(track.embedUrl)
            }
            null -> Unit
        }
        lastLoadedKey = currentKey
    }

    val currentEmbedState by rememberUpdatedState(embedState)

    LaunchedEffect(embedCommands) {
        embedCommands.collect { cmd ->
            if (currentEmbedState.source != EmbedPlaybackSource.YOUTUBE_MUSIC) return@collect
            when (cmd) {
                EmbedCommand.Play -> webView.evaluateJavascript("playVideo()", null)
                EmbedCommand.Pause -> webView.evaluateJavascript("pauseVideo()", null)
                is EmbedCommand.SeekToSec -> webView.evaluateJavascript("seekToSec(${cmd.seconds})", null)
            }
        }
    }

    val shouldParkHidden =
        (embedState.source == EmbedPlaybackSource.SPOTIFY_EMBED && !embedState.isExpanded) ||
            targetBounds == null ||
            targetBounds.width <= 1f ||
            targetBounds.height <= 1f

    val viewModifier = if (shouldParkHidden) {
        modifier
            .size(1.dp)
            .alpha(0.01f)
    } else {
        val bounds = targetBounds!!
        val widthDp = with(density) { bounds.width.toDp() }
        val heightDp = with(density) { bounds.height.toDp() }
        modifier
            .offset { IntOffset(bounds.left.roundToInt(), bounds.top.roundToInt()) }
            .size(width = widthDp, height = heightDp)
            .clip(RoundedCornerShape(if (embedState.isExpanded) 18.dp else 10.dp))
    }

    AndroidView(
        factory = { webView },
        update = { view ->
            if (!shouldParkHidden) {
                view.requestLayout()
            }
        },
        modifier = viewModifier
    )
}

@Composable
fun EmbedNowPlayingOverlay(
    embedState: EmbedPlayerUiState,
    onCollapse: () -> Unit,
    onClose: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onSkipPrevious: () -> Unit,
    onSkipNext: () -> Unit,
    onJumpToQueueIndex: (Int) -> Unit,
    onExpandedSlotPositioned: (Rect) -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(enabled = embedState.isActive && embedState.isExpanded) {
        onCollapse()
    }

    val isYouTube = embedState.source == EmbedPlaybackSource.YOUTUBE_MUSIC
    val accent = if (isYouTube) YouTubeMusicRed else SpotifyGreen
    val sourceLabel = if (isYouTube) "YOUTUBE MUSIC IN-APP" else "SPOTIFY IN-APP PLAYER"

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .widthIn(max = 620.dp)
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onCollapse,
                    modifier = Modifier.testTag("embed_player_collapse")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.KeyboardArrowDown,
                        contentDescription = "Minimize player to mini-bar"
                    )
                }

                SourcePill(label = sourceLabel, accentColor = accent)

                IconButton(
                    onClick = onClose,
                    modifier = Modifier.testTag("embed_player_close")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Stop and close player"
                    )
                }
            }

            // Placeholder bounds where PersistentEmbedWebView renders seamlessly
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (isYouTube) Modifier.aspectRatio(16f / 9f)
                        else Modifier.height(156.dp)
                    )
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color(0xFF0E1015))
                    .border(1.dp, accent.copy(alpha = 0.45f), RoundedCornerShape(18.dp))
                    .onGloballyPositioned { coords ->
                        onExpandedSlotPositioned(coords.boundsInRoot())
                    }
            )

            // Title & artist
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = embedState.title,
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = embedState.subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            embedState.errorMessage?.let { err ->
                Text(
                    text = err,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f))
                        .padding(10.dp)
                )
            }

            // Seek slider for YouTube Music
            if (isYouTube) {
                val dur = embedState.durationMs.coerceAtLeast(1L)
                val pos = embedState.currentPositionMs.coerceIn(0L, dur)
                var sliderPos by remember(pos) { mutableStateOf(pos.toFloat()) }
                Column {
                    Slider(
                        value = sliderPos,
                        onValueChange = { sliderPos = it },
                        onValueChangeFinished = { onSeekTo(sliderPos.toLong()) },
                        valueRange = 0f..dur.toFloat(),
                        colors = SliderDefaults.colors(
                            thumbColor = accent,
                            activeTrackColor = accent
                        )
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = formatDuration(pos),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = if (embedState.durationMs > 0L) formatDuration(embedState.durationMs) else "--:--",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                Text(
                    text = "Playing directly inside RinMusic via Spotify Web Player — minimize anytime to keep listening across tabs.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Transport Controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onSkipPrevious,
                    modifier = Modifier
                        .size(52.dp)
                        .testTag("embed_player_prev")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.SkipPrevious,
                        contentDescription = stringResource(R.string.cd_previous),
                        modifier = Modifier.size(34.dp)
                    )
                }

                if (isYouTube) {
                    if (embedState.isBuffering) {
                        Box(
                            modifier = Modifier
                                .size(66.dp)
                                .clip(CircleShape)
                                .background(accent),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(28.dp),
                                color = Color.White,
                                strokeWidth = 3.dp
                            )
                        }
                    } else {
                        IconButton(
                            onClick = onTogglePlayPause,
                            modifier = Modifier
                                .size(66.dp)
                                .clip(CircleShape)
                                .background(accent)
                                .testTag("embed_player_play_pause")
                        ) {
                            Icon(
                                imageVector = if (embedState.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                contentDescription = stringResource(
                                    if (embedState.isPlaying) R.string.cd_pause else R.string.cd_play
                                ),
                                tint = Color.White,
                                modifier = Modifier.size(36.dp)
                            )
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .size(66.dp)
                            .clip(CircleShape)
                            .background(accent.copy(alpha = 0.16f))
                            .border(1.dp, accent.copy(alpha = 0.5f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.GraphicEq,
                            contentDescription = "Spotify In-App Active",
                            tint = accent,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }

                IconButton(
                    onClick = onSkipNext,
                    modifier = Modifier
                        .size(52.dp)
                        .testTag("embed_player_next")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.SkipNext,
                        contentDescription = stringResource(R.string.cd_next),
                        modifier = Modifier.size(34.dp)
                    )
                }
            }

            // Queue section
            Text(
                text = "Up Next in RinMusic",
                style = MaterialTheme.typography.titleMedium
            )

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (isYouTube) {
                    itemsIndexed(embedState.youtubeQueue, key = { idx, t -> "ytq_${idx}_${t.videoId}" }) { idx, track ->
                        val active = idx == embedState.youtubeQueueIndex
                        EmbedQueueRow(
                            title = track.title,
                            subtitle = track.channelName,
                            artworkUrl = track.thumbnailUrl,
                            artworkFallbackKey = "studio",
                            isActive = active,
                            accent = accent,
                            onClick = { onJumpToQueueIndex(idx) }
                        )
                    }
                } else {
                    itemsIndexed(embedState.spotifyQueue, key = { idx, t -> "spq_${idx}_${t.id}" }) { idx, track ->
                        val active = idx == embedState.spotifyQueueIndex
                        EmbedQueueRow(
                            title = track.title,
                            subtitle = track.artist,
                            artworkUrl = track.artworkUrl,
                            artworkFallbackKey = "electronic",
                            isActive = active,
                            accent = accent,
                            onClick = { onJumpToQueueIndex(idx) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EmbedQueueRow(
    title: String,
    subtitle: String,
    artworkUrl: String?,
    artworkFallbackKey: String,
    isActive: Boolean,
    accent: Color,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(
                if (isActive) accent.copy(alpha = 0.14f)
                else MaterialTheme.colorScheme.surfaceContainerLow
            )
            .border(
                width = 1.dp,
                color = if (isActive) accent.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(14.dp)
            )
            .clickable(onClick = onClick)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ArtworkImage(
            artworkResKey = artworkFallbackKey,
            artworkUrl = artworkUrl,
            modifier = Modifier.size(44.dp),
            shape = RoundedCornerShape(8.dp)
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
