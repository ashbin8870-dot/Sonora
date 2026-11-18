package com.example.data.remote

import android.net.Uri
import android.util.Xml
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.xmlpull.v1.XmlPullParser
import java.io.StringReader
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

@Serializable
private data class YtResourceIdDto(
    val videoId: String? = null,
)

@Serializable
private data class YtPlaylistSnippetDto(
    val title: String = "",
    val channelTitle: String = "",
    val videoOwnerChannelTitle: String? = null,
    val thumbnails: YtThumbnailsDto = YtThumbnailsDto(),
    val resourceId: YtResourceIdDto = YtResourceIdDto(),
)

@Serializable
private data class YtPlaylistItemDto(
    val snippet: YtPlaylistSnippetDto = YtPlaylistSnippetDto(),
)

@Serializable
private data class YtPlaylistItemsResponseDto(
    val items: List<YtPlaylistItemDto> = emptyList(),
)

@Serializable
private data class YtPlaylistsInfoResponseDto(
    val items: List<YtPlaylistInfoItemDto> = emptyList(),
)

@Serializable
private data class YtPlaylistInfoItemDto(
    val snippet: YtSnippetDto = YtSnippetDto(),
)

data class YouTubeMusicTrack(
    val videoId: String,
    val title: String,
    val channelName: String,
    val thumbnailUrl: String?,
    val durationMs: Long = 0L,
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
              src="https://www.youtube.com/embed/$videoId?enablejsapi=1&autoplay=1&playsinline=1&rel=0&modestbranding=1&controls=0&iv_load_policy=3&disablekb=1&fs=0&origin=$encodedOrigin"
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

    suspend fun searchMusic(
        query: String,
        regionCode: String? = null,
    ): Result<List<YouTubeMusicTrack>> = withContext(Dispatchers.IO) {
        runCatching {
            if (!isConfigured) {
                error("YOUTUBE_API_KEY is not configured. Add it in the AI Studio Secrets panel.")
            }
            val encoded = Uri.encode(query.trim())
            val regionParam = regionCode
                ?.takeIf { it.isNotBlank() && !it.equals("ALL", ignoreCase = true) }
                ?.let { "&regionCode=${it.uppercase()}" }
                .orEmpty()
            val url = "https://www.googleapis.com/youtube/v3/search" +
                "?part=snippet&type=video&videoCategoryId=10&maxResults=25" +
                "$regionParam&q=$encoded&key=$apiKey"
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

    suspend fun fetchPlaylist(playlistId: String): Result<Pair<String, List<YouTubeMusicTrack>>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val cleanId = playlistId.trim()
                if (cleanId.isBlank()) error("Invalid YouTube playlist ID")

                if (isConfigured) {
                    val apiResult = runCatching { fetchPlaylistViaDataApi(cleanId) }.getOrNull()
                    if (apiResult != null && apiResult.second.isNotEmpty()) {
                        return@runCatching apiResult
                    }
                }

                // Public YouTube playlist page ytInitialData (supports up to 100 tracks + durations + OLAK5uy_/Mixes)
                val webResult = runCatching { fetchPlaylistFromWebPage(cleanId) }.getOrNull()
                if (webResult != null && webResult.second.isNotEmpty()) {
                    return@runCatching webResult
                }

                // Fallback: public Atom XML feed (works for PL... playlists)
                val feedUrl = "https://www.youtube.com/feeds/videos.xml?playlist_id=${Uri.encode(cleanId)}"
                val xml = client.newCall(Request.Builder().url(feedUrl).build()).execute().use { resp ->
                    if (!resp.isSuccessful) error("Could not load YouTube playlist (${resp.code})")
                    resp.body?.string().orEmpty()
                }
                val parsedFeed = parseAtomPlaylistFeed(xml)
                if (parsedFeed.second.isEmpty()) {
                    error("No playable tracks found in that YouTube playlist.")
                }
                parsedFeed
            }
        }

    private fun fetchPlaylistViaDataApi(cleanId: String): Pair<String, List<YouTubeMusicTrack>> {
        val infoUrl = "https://www.googleapis.com/youtube/v3/playlists" +
            "?part=snippet&id=${Uri.encode(cleanId)}&key=$apiKey"
        val playlistTitle = client.newCall(Request.Builder().url(infoUrl).build()).execute().use { resp ->
            if (resp.isSuccessful) {
                val raw = resp.body?.string().orEmpty()
                json.decodeFromString<YtPlaylistsInfoResponseDto>(raw)
                    .items.firstOrNull()?.snippet?.title?.takeIf { it.isNotBlank() }
            } else null
        } ?: "YouTube Music Playlist"

        val itemsUrl = "https://www.googleapis.com/youtube/v3/playlistItems" +
            "?part=snippet&maxResults=50&playlistId=${Uri.encode(cleanId)}&key=$apiKey"
        val tracks = client.newCall(Request.Builder().url(itemsUrl).build()).execute().use { resp ->
            val raw = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) error("Could not fetch YouTube playlist (${resp.code})")
            val parsed = json.decodeFromString<YtPlaylistItemsResponseDto>(raw)
            parsed.items.mapNotNull { item ->
                val videoId = item.snippet.resourceId.videoId?.takeIf { it.isNotBlank() }
                    ?: return@mapNotNull null
                val thumb = item.snippet.thumbnails.high?.url
                    ?: item.snippet.thumbnails.medium?.url
                    ?: item.snippet.thumbnails.default?.url
                    ?: "https://img.youtube.com/vi/$videoId/hqdefault.jpg"
                val channel = item.snippet.videoOwnerChannelTitle
                    ?: item.snippet.channelTitle.ifBlank { "YouTube Music" }
                YouTubeMusicTrack(
                    videoId = videoId,
                    title = decodeHtmlEntities(item.snippet.title),
                    channelName = decodeHtmlEntities(channel),
                    thumbnailUrl = thumb
                )
            }
        }
        return decodeHtmlEntities(playlistTitle) to tracks
    }

    private fun fetchPlaylistFromWebPage(cleanId: String): Pair<String, List<YouTubeMusicTrack>> {
        val url = "https://www.youtube.com/playlist?list=${Uri.encode(cleanId)}&hl=en"
        val request = Request.Builder()
            .url(url)
            .header(
                "User-Agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
            )
            .header("Accept-Language", "en-US,en;q=0.9")
            .build()

        val html = client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) error("Could not load YouTube playlist (${resp.code})")
            resp.body?.string().orEmpty()
        }

        val jsonText = extractYtInitialDataJson(html)
            ?: error("Could not parse YouTube playlist page. Make sure the playlist is public or unlisted.")
        val root = json.parseToJsonElement(jsonText).jsonObject

        val metaTitle = root["metadata"]?.jsonObject
            ?.get("playlistMetadataRenderer")?.jsonObject
            ?.get("title")?.jsonPrimitive?.contentOrNull
            ?.takeIf { it.isNotBlank() }

        val tracks = mutableListOf<YouTubeMusicTrack>()
        val seenIds = HashSet<String>()
        collectPlaylistVideoRenderers(root, tracks, seenIds)

        if (tracks.isEmpty()) {
            error("No playable tracks found in that YouTube playlist.")
        }
        return decodeHtmlEntities(metaTitle ?: "YouTube Music Playlist") to tracks
    }

    private fun extractYtInitialDataJson(html: String): String? {
        val markerIdx = listOf("var ytInitialData = ", "window[\"ytInitialData\"] = ", "ytInitialData = ")
            .firstNotNullOfOrNull { marker ->
                html.indexOf(marker).takeIf { it >= 0 }?.let { it + marker.length }
            } ?: return null

        val startBrace = html.indexOf('{', markerIdx)
        if (startBrace < 0) return null

        var depth = 0
        var inString = false
        var escaped = false
        for (i in startBrace until html.length) {
            val ch = html[i]
            if (escaped) {
                escaped = false
                continue
            }
            if (ch == '\\' && inString) {
                escaped = true
                continue
            }
            if (ch == '"') {
                inString = !inString
                continue
            }
            if (!inString) {
                if (ch == '{') depth++
                else if (ch == '}') {
                    depth--
                    if (depth == 0) {
                        return html.substring(startBrace, i + 1)
                    }
                }
            }
        }
        return null
    }

    private fun collectPlaylistVideoRenderers(
        element: JsonElement,
        out: MutableList<YouTubeMusicTrack>,
        seenIds: MutableSet<String>,
    ) {
        when (element) {
            is JsonObject -> {
                val renderer = (element["playlistVideoRenderer"] as? JsonObject)
                    ?: (element["playlistPanelVideoRenderer"] as? JsonObject)
                if (renderer != null) {
                    val videoId = renderer["videoId"]?.jsonPrimitive?.contentOrNull
                        ?.takeIf { it.isNotBlank() }
                    val title = extractYtText(renderer["title"])
                    val isUnavailable = title.isNullOrBlank() ||
                        title.equals("[Deleted video]", ignoreCase = true) ||
                        title.equals("[Private video]", ignoreCase = true) ||
                        title.equals("Deleted video", ignoreCase = true) ||
                        title.equals("Private video", ignoreCase = true)
                    if (videoId != null && !isUnavailable && seenIds.add(videoId)) {
                        val channel = extractYtText(renderer["shortBylineText"])
                            ?: extractYtText(renderer["longBylineText"])
                            ?: "YouTube Music"
                        val thumb = renderer["thumbnail"]?.jsonObject
                            ?.get("thumbnails")?.jsonArray
                            ?.lastOrNull()?.jsonObject
                            ?.get("url")?.jsonPrimitive?.contentOrNull
                            ?: "https://img.youtube.com/vi/$videoId/hqdefault.jpg"
                        val lengthSeconds = renderer["lengthSeconds"]?.jsonPrimitive?.contentOrNull
                            ?.toLongOrNull()
                            ?: renderer["lengthSeconds"]?.jsonPrimitive?.longOrNull
                        val durationMs = if (lengthSeconds != null && lengthSeconds > 0L) {
                            lengthSeconds * 1000L
                        } else {
                            parseDurationTextMs(extractYtText(renderer["lengthText"]))
                        }
                        out.add(
                            YouTubeMusicTrack(
                                videoId = videoId,
                                title = decodeHtmlEntities(title!!),
                                channelName = decodeHtmlEntities(channel),
                                thumbnailUrl = thumb,
                                durationMs = durationMs
                            )
                        )
                    }
                } else {
                    for (value in element.values) {
                        collectPlaylistVideoRenderers(value, out, seenIds)
                    }
                }
            }
            is JsonArray -> {
                for (item in element) {
                    collectPlaylistVideoRenderers(item, out, seenIds)
                }
            }
            else -> Unit
        }
    }

    private fun parseDurationTextMs(text: String?): Long {
        if (text.isNullOrBlank()) return 0L
        val parts = text.trim().split(':').mapNotNull { it.toLongOrNull() }
        return when (parts.size) {
            2 -> (parts[0] * 60 + parts[1]) * 1000L
            3 -> (parts[0] * 3600 + parts[1] * 60 + parts[2]) * 1000L
            else -> 0L
        }
    }

    private fun extractYtText(el: JsonElement?): String? {
        val obj = el as? JsonObject ?: return null
        obj["simpleText"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }?.let { return it }
        val runs = obj["runs"] as? JsonArray ?: return null
        val joined = runs.mapNotNull { (it as? JsonObject)?.get("text")?.jsonPrimitive?.contentOrNull }
            .joinToString("")
            .trim()
        return joined.takeIf { it.isNotBlank() }
    }

    private fun parseAtomPlaylistFeed(xml: String): Pair<String, List<YouTubeMusicTrack>> {
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(StringReader(xml))

        var feedTitle = "YouTube Playlist"
        val tracks = mutableListOf<YouTubeMusicTrack>()
        var inEntry = false
        var inAuthor = false
        var currentVideoId: String? = null
        var currentTitle: String? = null
        var currentAuthor: String? = null
        var currentThumb: String? = null
        var currentTag: String? = null

        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    currentTag = parser.name
                    when (currentTag) {
                        "entry" -> {
                            inEntry = true
                            currentVideoId = null
                            currentTitle = null
                            currentAuthor = null
                            currentThumb = null
                        }
                        "author" -> if (inEntry) inAuthor = true
                        "media:thumbnail" -> if (inEntry) {
                            currentThumb = parser.getAttributeValue(null, "url")
                        }
                    }
                }
                XmlPullParser.TEXT -> {
                    val text = parser.text?.trim().orEmpty()
                    if (text.isNotEmpty()) {
                        if (!inEntry && currentTag == "title") {
                            feedTitle = text
                        } else if (inEntry) {
                            when (currentTag) {
                                "yt:videoId" -> currentVideoId = text
                                "title" -> if (!inAuthor) currentTitle = text
                                "name" -> if (inAuthor) currentAuthor = text
                            }
                        }
                    }
                }
                XmlPullParser.END_TAG -> {
                    when (parser.name) {
                        "author" -> inAuthor = false
                        "entry" -> {
                            val vid = currentVideoId
                            val title = currentTitle
                            if (!vid.isNullOrBlank() && !title.isNullOrBlank()) {
                                tracks.add(
                                    YouTubeMusicTrack(
                                        videoId = vid,
                                        title = decodeHtmlEntities(title),
                                        channelName = decodeHtmlEntities(currentAuthor ?: "YouTube Music"),
                                        thumbnailUrl = currentThumb ?: "https://img.youtube.com/vi/$vid/hqdefault.jpg"
                                    )
                                )
                            }
                            inEntry = false
                        }
                    }
                    currentTag = null
                }
            }
            eventType = parser.next()
        }
        return decodeHtmlEntities(feedTitle) to tracks
    }

    fun parseYouTubePlaylistId(input: String): String? {
        val trimmed = input.trim()
        if (trimmed.isBlank()) return null
        val uri = runCatching { Uri.parse(trimmed) }.getOrNull()
        val listParam = uri?.getQueryParameter("list")?.takeIf { it.isNotBlank() }
        if (listParam != null) return listParam
        if (trimmed.matches(Regex("^(PL|OLAK5uy_|UU|RD|LL)[a-zA-Z0-9_-]{8,}$"))) return trimmed
        return null
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
