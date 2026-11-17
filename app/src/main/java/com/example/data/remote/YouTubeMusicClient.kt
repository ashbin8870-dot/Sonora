package com.example.data.remote

import android.net.Uri
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

@Serializable
private data class YtThumbnailDto(
    val url: String = "",
)

@Serializable
private data class YtThumbnailsDto(
    val medium: YtThumbnailDto? = null,
    val high: YtThumbnailDto? = null,
    val default: YtThumbnailDto? = null,
)

@Serializable
private data class YtSnippetDto(
    val title: String = "",
    val channelTitle: String = "",
    val thumbnails: YtThumbnailsDto = YtThumbnailsDto(),
)

@Serializable
private data class YtIdDto(
    val videoId: String? = null,
)

@Serializable
private data class YtSearchItemDto(
    val id: YtIdDto = YtIdDto(),
    val snippet: YtSnippetDto = YtSnippetDto(),
)

@Serializable
private data class YtSearchResponseDto(
    val items: List<YtSearchItemDto> = emptyList(),
)

data class YouTubeMusicTrack(
    val videoId: String,
    val title: String,
    val channelName: String,
    val thumbnailUrl: String?,
) {
    fun buildIframePlayerHtml(originUrl: String): String {
        val encodedOrigin = Uri.encode(originUrl)
        return """
        <!DOCTYPE html>
        <html>
          <head>
            <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no"/>
            <meta name="referrer" content="strict-origin-when-cross-origin"/>
            <style>
              html, body { margin: 0; padding: 0; width: 100%; height: 100%; background: #0E1015; overflow: hidden; }
              #player { width: 100%; height: 100%; border: 0; }
            </style>
          </head>
          <body>
            <iframe
              id="player"
              src="https://www.youtube.com/embed/$videoId?enablejsapi=1&autoplay=1&playsinline=1&rel=0&modestbranding=1&controls=1&fs=0&origin=$encodedOrigin"
              referrerpolicy="strict-origin-when-cross-origin"
              allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture; web-share"
              allowfullscreen>
            </iframe>
            <script src="https://www.youtube.com/iframe_api"></script>
            <script>
              var ytPlayer = null;
              function onYouTubeIframeAPIReady() {
                ytPlayer = new YT.Player('player', {
                  events: {
                    onReady: function(e) {
                      e.target.playVideo();
                      if (window.SonoraBridge) SonoraBridge.onReady();
                    },
                    onStateChange: function(e) {
                      if (window.SonoraBridge) SonoraBridge.onStateChanged(e.data);
                    },
                    onError: function(e) {
                      if (window.SonoraBridge) SonoraBridge.onError(e.data);
                    }
                  }
                });
              }
              function loadVideo(id) {
                if (ytPlayer && ytPlayer.loadVideoById) {
                  ytPlayer.loadVideoById(id);
                }
              }
              function playVideo() { if (ytPlayer && ytPlayer.playVideo) ytPlayer.playVideo(); }
              function pauseVideo() { if (ytPlayer && ytPlayer.pauseVideo) ytPlayer.pauseVideo(); }
              function seekToSec(sec) { if (ytPlayer && ytPlayer.seekTo) ytPlayer.seekTo(sec, true); }
              setInterval(function() {
                if (ytPlayer && ytPlayer.getCurrentTime && ytPlayer.getDuration && window.SonoraBridge) {
                  var pos = Math.floor((ytPlayer.getCurrentTime() || 0) * 1000);
                  var dur = Math.floor((ytPlayer.getDuration() || 0) * 1000);
                  SonoraBridge.onProgress(pos, dur);
                }
              }, 500);
            </script>
          </body>
        </html>
        """.trimIndent()
    }
}

class YouTubeMusicClient {

    private val json = Json { ignoreUnknownKeys = true }
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    val apiKey: String
        get() = BuildConfig.YOUTUBE_API_KEY

    val isConfigured: Boolean
        get() = apiKey.isNotBlank() && apiKey != "YOUR_YOUTUBE_API_KEY"

    suspend fun searchMusic(query: String): Result<List<YouTubeMusicTrack>> = withContext(Dispatchers.IO) {
        runCatching {
            if (!isConfigured) {
                error("YOUTUBE_API_KEY is not configured. Add it in the AI Studio Secrets panel.")
            }
            val encoded = Uri.encode(query.trim())
            val url = "https://www.googleapis.com/youtube/v3/search" +
                "?part=snippet&type=video&videoCategoryId=10&maxResults=20" +
                "&q=$encoded&key=$apiKey"
            val request = Request.Builder().url(url).build()
            client.newCall(request).execute().use { response ->
                val raw = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    error("YouTube Data API error (${response.code})")
                }
                val parsed = json.decodeFromString<YtSearchResponseDto>(raw)
                parsed.items.mapNotNull { item ->
                    val videoId = item.id.videoId?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                    val thumb = item.snippet.thumbnails.high?.url
                        ?: item.snippet.thumbnails.medium?.url
                        ?: item.snippet.thumbnails.default?.url
                    YouTubeMusicTrack(
                        videoId = videoId,
                        title = decodeHtmlEntities(item.snippet.title),
                        channelName = decodeHtmlEntities(item.snippet.channelTitle),
                        thumbnailUrl = thumb
                    )
                }
            }
        }
    }

    fun parseYouTubeUrl(input: String): YouTubeMusicTrack? {
        val trimmed = input.trim()
        if (trimmed.isBlank()) return null
        val uri = runCatching { Uri.parse(trimmed) }.getOrNull() ?: return null
        val host = uri.host?.lowercase().orEmpty()
        val videoId = when {
            host.contains("youtu.be") -> uri.lastPathSegment
            host.contains("youtube.com") -> {
                uri.getQueryParameter("v")
                    ?: uri.pathSegments.takeIf { it.size >= 2 && it[0] in listOf("embed", "shorts", "live") }?.get(1)
            }
            trimmed.matches(Regex("^[a-zA-Z0-9_-]{11}$")) -> trimmed
            else -> null
        }?.takeIf { it.isNotBlank() } ?: return null

        return YouTubeMusicTrack(
            videoId = videoId,
            title = "YouTube Music ($videoId)",
            channelName = "In-App Stream",
            thumbnailUrl = "https://img.youtube.com/vi/$videoId/hqdefault.jpg"
        )
    }

    private fun decodeHtmlEntities(text: String): String {
        return text
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
    }
}
