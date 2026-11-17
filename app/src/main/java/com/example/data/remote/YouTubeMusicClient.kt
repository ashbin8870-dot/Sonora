package com.example.data.remote

import android.content.Context
import android.content.Intent
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
    val youtubeMusicUrl: String
        get() = "https://music.youtube.com/watch?v=$videoId"

    val embedHtml: String
        get() = """
            <!DOCTYPE html>
            <html>
              <head>
                <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no"/>
                <style>
                  html, body { margin: 0; padding: 0; width: 100%; height: 100%; background: #0E1015; overflow: hidden; }
                  iframe { width: 100%; height: 100%; border: 0; }
                </style>
              </head>
              <body>
                <iframe
                  src="https://www.youtube.com/embed/$videoId?autoplay=1&playsinline=1&rel=0&modestbranding=1"
                  allow="autoplay; encrypted-media; picture-in-picture"
                  allowfullscreen>
                </iframe>
              </body>
            </html>
        """.trimIndent()
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
            channelName = "Direct Link",
            thumbnailUrl = "https://img.youtube.com/vi/$videoId/hqdefault.jpg"
        )
    }

    fun openInYouTubeMusicApp(context: Context, videoId: String) {
        val ytMusicIntent = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("https://music.youtube.com/watch?v=$videoId")
        ).apply {
            setPackage("com.google.android.apps.youtube.music")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val launched = runCatching {
            context.startActivity(ytMusicIntent)
            true
        }.getOrDefault(false)

        if (!launched) {
            val fallbackIntent = Intent(
                Intent.ACTION_VIEW,
                Uri.parse("https://music.youtube.com/watch?v=$videoId")
            ).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            runCatching { context.startActivity(fallbackIntent) }
        }
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
