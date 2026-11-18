package com.example.data.remote

import android.net.Uri
import com.example.data.local.TrackDao
import com.example.data.model.TrackEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

class TrackStreamResolver(
    private val trackDao: TrackDao,
    private val spotifyClient: SpotifyClient,
    private val youTubeMusicClient: YouTubeMusicClient,
) {
    companion object {
        // Shared between stream probe and ExoPlayer DefaultHttpDataSource so verified URLs play reliably
        const val EXOPLAYER_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"

        private const val CACHE_TTL_MS = 90 * 60 * 1000L

        private val PIPED_INSTANCES = listOf(
            "https://pipedapi.kavin.rocks",
            "https://api.piped.private.coffee",
            "https://pipedapi.adminforge.de",
        )
    }

    private data class CachedStream(
        val url: String,
        val cachedAtMs: Long = System.currentTimeMillis(),
    )

    private data class CachedSpotifyMeta(
        val title: String,
        val artist: String,
        val durationMs: Long,
        val previewUrl: String?,
        val artworkUrl: String?,
    )

    private val json = Json { ignoreUnknownKeys = true }
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private val streamCache = ConcurrentHashMap<String, CachedStream>()
    private val spotifyMetaCache = ConcurrentHashMap<String, CachedSpotifyMeta>()

    fun rememberSpotifyTracks(tracks: List<SpotifyTrack>) {
        tracks.forEach { t ->
            spotifyMetaCache[t.id] = CachedSpotifyMeta(
                title = t.title,
                artist = t.artist,
                durationMs = t.durationMs,
                previewUrl = t.previewUrl,
                artworkUrl = t.artworkUrl
            )
        }
    }

    suspend fun enrichYouTubeTrack(track: YouTubeMusicTrack): YouTubeMusicTrack =
        withContext(Dispatchers.IO) {
            val isPlaceholder = track.title.startsWith("YouTube Music (") ||
                track.channelName == "In-App Stream" ||
                track.channelName == "RinMusic Stream"
            if (!isPlaceholder) return@withContext track
            val meta = fetchYouTubeOEmbed(track.videoId)
            if (meta != null) {
                track.copy(
                    title = meta.first,
                    channelName = meta.second,
                    thumbnailUrl = track.thumbnailUrl ?: "https://img.youtube.com/vi/${track.videoId}/hqdefault.jpg"
                )
            } else {
                track
            }
        }

    suspend fun enrichSpotifyTrack(track: SpotifyTrack): SpotifyTrack =
        withContext(Dispatchers.IO) {
            rememberSpotifyTracks(listOf(track))
            val isPlaceholder = track.title == "Spotify Track" || track.artist == "Spotify Stream"
            if (!isPlaceholder && !track.artworkUrl.isNullOrBlank()) return@withContext track
            val fetched = fetchSpotifyEmbedTrackMeta(track.id) ?: return@withContext track
            spotifyMetaCache[track.id] = fetched
            track.copy(
                title = fetched.title,
                artist = fetched.artist,
                durationMs = if (fetched.durationMs > 0L) fetched.durationMs else track.durationMs,
                previewUrl = fetched.previewUrl ?: track.previewUrl,
                artworkUrl = fetched.artworkUrl ?: track.artworkUrl
            )
        }

    /**
     * Synchronous resolver invoked on ExoPlayer's background loader thread via ResolvingDataSource.
     */
    fun resolvePlayableUrlBlocking(uri: Uri): String {
        val key = uri.toString()
        val cached = streamCache[key]
        if (cached != null && (System.currentTimeMillis() - cached.cachedAtMs) < CACHE_TTL_MS) {
            return cached.url
        }

        val resolved = when (uri.scheme?.lowercase()) {
            "yt" -> {
                val videoId = uri.host?.takeIf { it.isNotBlank() }
                    ?: uri.schemeSpecificPart.removePrefix("//").takeIf { it.isNotBlank() }
                    ?: throw IOException("Invalid YouTube track URI")
                resolveYouTubeStreamBlocking(videoId)
            }
            "spotify" -> {
                val trackId = uri.lastPathSegment?.takeIf { it.isNotBlank() }
                    ?: uri.schemeSpecificPart.substringAfterLast('/').takeIf { it.isNotBlank() }
                    ?: throw IOException("Invalid Spotify track URI")
                resolveSpotifyStreamBlocking(trackId)
            }
            else -> uri.toString()
        }

        streamCache[key] = CachedStream(resolved)
        return resolved
    }

    private fun resolveYouTubeStreamBlocking(videoId: String): String {
        // 1. Direct YouTube extraction on primary videoId
        extractYouTubeAudioUrl(videoId)?.let { return it }

        // 2. Fallback: look up title & artist from DB or YouTube oEmbed, then search alternate YouTube audio or Deezer/iTunes
        val dbTrack = kotlinx.coroutines.runBlocking { trackDao.getTrackById("yt_$videoId") }
        val oembed = if (dbTrack == null || dbTrack.title.startsWith("YouTube Music (")) {
            fetchYouTubeOEmbed(videoId)
        } else null

        val title = oembed?.first ?: dbTrack?.title.orEmpty()
        val artist = oembed?.second ?: dbTrack?.artist.orEmpty()
        val cleanQuery = buildSearchQuery(title, artist)

        if (cleanQuery.isNotBlank()) {
            val altIds = searchYouTubeVideoIds(cleanQuery).filter { it != videoId }.take(3)
            for (altId in altIds) {
                extractYouTubeAudioUrl(altId)?.let { return it }
            }
            findFallbackAudioPreview("$title $artist")?.let { return it }
        }

        throw IOException("Unable to load playable audio stream for YouTube track")
    }

    private fun resolveSpotifyStreamBlocking(trackId: String): String {
        val dbTrack = kotlinx.coroutines.runBlocking { trackDao.getTrackById("sp_$trackId") }
        val cachedMeta = spotifyMetaCache[trackId]
        val fetchedMeta = if (dbTrack == null || cachedMeta == null) {
            fetchSpotifyEmbedTrackMeta(trackId)?.also { spotifyMetaCache[trackId] = it }
        } else null

        val title = dbTrack?.title?.takeIf { it != "Spotify Track" }
            ?: cachedMeta?.title
            ?: fetchedMeta?.title
            ?: ""
        val artist = dbTrack?.artist?.takeIf { it != "Spotify Stream" }
            ?: cachedMeta?.artist
            ?: fetchedMeta?.artist
            ?: ""
        val durationMs = dbTrack?.durationMs?.takeIf { it > 0L }
            ?: cachedMeta?.durationMs
            ?: fetchedMeta?.durationMs
            ?: 0L
        val previewUrl = cachedMeta?.previewUrl ?: fetchedMeta?.previewUrl

        val query = buildSearchQuery(title, artist)
        if (query.isNotBlank()) {
            // 1. Full-song audio via YouTube search + InnerTube/Piped audio extraction
            val ytQuery = "$query official audio"
            val candidateIds = searchYouTubeVideoIds(ytQuery, targetDurationMs = durationMs).take(3)
            for (vid in candidateIds) {
                extractYouTubeAudioUrl(vid)?.let { return it }
            }
        }

        // 2. Spotify's own direct MP3 preview URL (if available)
        if (!previewUrl.isNullOrBlank() && probeStreamUrl(previewUrl)) {
            return previewUrl
        }

        // 3. Deezer / iTunes direct audio fallback
        if (query.isNotBlank()) {
            findFallbackAudioPreview("$title $artist")?.let { return it }
        }

        throw IOException("Unable to find a playable audio stream for \"$title\"")
    }

    private fun extractYouTubeAudioUrl(videoId: String): String? {
        // Tier 1a: YouTube InnerTube ANDROID_VR
        fetchInnerTubeAudioUrl(
            videoId = videoId,
            clientName = "ANDROID_VR",
            clientVersion = "1.60.19",
            clientIdHeader = "28",
            clientUa = "com.google.android.apps.youtube.vr.oculus/1.60.19 (Linux; U; Android 12L; eureka-user Build/SQ3A.220605.009.A1) gzip",
            extraClientJson = "\"androidSdkVersion\":32,\"osName\":\"Android\",\"osVersion\":\"12L\","
        )?.let { return it }

        // Tier 1b: YouTube InnerTube IOS
        fetchInnerTubeAudioUrl(
            videoId = videoId,
            clientName = "IOS",
            clientVersion = "19.29.1",
            clientIdHeader = "5",
            clientUa = "com.google.ios.youtube/19.29.1 (iPhone16,2; U; CPU iOS 17_5_1 like Mac OS X;)",
            extraClientJson = "\"deviceMake\":\"Apple\",\"deviceModel\":\"iPhone16,2\",\"osName\":\"iPhone\",\"osVersion\":\"17.5.1.21F90\","
        )?.let { return it }

        // Tier 2: Public Piped API audioStreams
        for (base in PIPED_INSTANCES) {
            fetchPipedAudioUrl(base, videoId)?.let { return it }
        }

        return null
    }

    private fun fetchInnerTubeAudioUrl(
        videoId: String,
        clientName: String,
        clientVersion: String,
        clientIdHeader: String,
        clientUa: String,
        extraClientJson: String,
    ): String? {
        return runCatching {
            val payload = """
                {
                  "videoId": "$videoId",
                  "context": {
                    "client": {
                      "clientName": "$clientName",
                      "clientVersion": "$clientVersion",
                      $extraClientJson
                      "hl": "en",
                      "gl": "US"
                    }
                  },
                  "contentCheckOk": true,
                  "racyCheckOk": true
                }
            """.trimIndent()

            val request = Request.Builder()
                .url("https://www.youtube.com/youtubei/v1/player?prettyPrint=false")
                .post(payload.toRequestBody("application/json; charset=utf-8".toMediaType()))
                .header("User-Agent", clientUa)
                .header("X-YouTube-Client-Name", clientIdHeader)
                .header("X-YouTube-Client-Version", clientVersion)
                .build()

            val raw = httpClient.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) return null
                resp.body?.string().orEmpty()
            }
            if (raw.isBlank()) return null

            val root = json.parseToJsonElement(raw).jsonObject
            val status = root["playabilityStatus"]?.jsonObject
                ?.get("status")?.jsonPrimitive?.contentOrNull
            if (status != "OK") return null

            val streamingData = root["streamingData"]?.jsonObject ?: return null
            val adaptive = streamingData["adaptiveFormats"]?.jsonArray.orEmpty()
            val formats = streamingData["formats"]?.jsonArray.orEmpty()

            val candidates = (adaptive + formats)
                .mapNotNull { it as? JsonObject }
                .mapNotNull { obj ->
                    val url = obj["url"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
                        ?: return@mapNotNull null
                    val mime = obj["mimeType"]?.jsonPrimitive?.contentOrNull.orEmpty()
                    val bitrate = obj["bitrate"]?.jsonPrimitive?.longOrNull ?: 0L
                    val priority = when {
                        mime.startsWith("audio/mp4", ignoreCase = true) -> 3
                        mime.startsWith("audio/", ignoreCase = true) -> 2
                        mime.startsWith("video/mp4", ignoreCase = true) -> 1
                        else -> 0
                    }
                    Triple(priority, bitrate, url)
                }
                .filter { it.first > 0 }
                .sortedWith(compareByDescending<Triple<Int, Long, String>> { it.first }.thenByDescending { it.second })

            candidates.firstOrNull { (_, _, url) -> probeStreamUrl(url) }?.third
        }.getOrNull()
    }

    private fun fetchPipedAudioUrl(baseUrl: String, videoId: String): String? {
        return runCatching {
            val request = Request.Builder()
                .url("$baseUrl/streams/${Uri.encode(videoId)}")
                .header("User-Agent", EXOPLAYER_USER_AGENT)
                .build()
            val raw = httpClient.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) return null
                resp.body?.string().orEmpty()
            }
            if (raw.isBlank()) return null
            val root = json.parseToJsonElement(raw).jsonObject
            val streams = root["audioStreams"]?.jsonArray.orEmpty()
            val sorted = streams.mapNotNull { it as? JsonObject }
                .mapNotNull { obj ->
                    val url = obj["url"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
                        ?: return@mapNotNull null
                    val mime = obj["mimeType"]?.jsonPrimitive?.contentOrNull.orEmpty()
                    val bitrate = obj["bitrate"]?.jsonPrimitive?.longOrNull ?: 0L
                    val pref = if (mime.contains("mp4", ignoreCase = true) || mime.contains("m4a", ignoreCase = true)) 2 else 1
                    Triple(pref, bitrate, url)
                }
                .sortedWith(compareByDescending<Triple<Int, Long, String>> { it.first }.thenByDescending { it.second })

            sorted.firstOrNull { (_, _, url) -> probeStreamUrl(url) }?.third
        }.getOrNull()
    }

    private fun searchYouTubeVideoIds(
        query: String,
        targetDurationMs: Long = 0L,
    ): List<String> {
        return runCatching {
            val escapedQuery = query.replace("\\", "\\\\").replace("\"", "\\\"")
            val payload = """
                {
                  "query": "$escapedQuery",
                  "context": {
                    "client": {
                      "clientName": "WEB",
                      "clientVersion": "2.20250101.00.00",
                      "hl": "en",
                      "gl": "US"
                    }
                  }
                }
            """.trimIndent()
            val request = Request.Builder()
                .url("https://www.youtube.com/youtubei/v1/search?prettyPrint=false")
                .post(payload.toRequestBody("application/json; charset=utf-8".toMediaType()))
                .header("User-Agent", EXOPLAYER_USER_AGENT)
                .build()

            val raw = httpClient.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) return emptyList()
                resp.body?.string().orEmpty()
            }
            if (raw.isBlank()) return emptyList()
            val root = json.parseToJsonElement(raw).jsonObject
            val results = mutableListOf<Pair<String, Long>>()
            collectVideoRenderers(root, results)

            if (targetDurationMs > 0L) {
                results.sortedBy { (_, durMs) ->
                    if (durMs <= 0L) 60_000L else kotlin.math.abs(durMs - targetDurationMs)
                }.map { it.first }
            } else {
                results.map { it.first }
            }
        }.getOrDefault(emptyList())
    }

    private fun collectVideoRenderers(
        el: JsonElement,
        out: MutableList<Pair<String, Long>>,
    ) {
        if (out.size >= 10) return
        when (el) {
            is JsonObject -> {
                val vr = el["videoRenderer"] as? JsonObject
                if (vr != null) {
                    val vid = vr["videoId"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
                    val durText = vr["lengthText"]?.jsonObject?.get("simpleText")?.jsonPrimitive?.contentOrNull
                    if (vid != null) {
                        out.add(vid to parseDurationTextMs(durText))
                    }
                } else {
                    for (v in el.values) collectVideoRenderers(v, out)
                }
            }
            is JsonArray -> {
                for (item in el) collectVideoRenderers(item, out)
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

    private fun fetchYouTubeOEmbed(videoId: String): Pair<String, String>? {
        return runCatching {
            val targetUrl = "https://www.youtube.com/watch?v=${Uri.encode(videoId)}"
            val request = Request.Builder()
                .url("https://www.youtube.com/oembed?url=${Uri.encode(targetUrl)}&format=json")
                .build()
            val raw = httpClient.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) return null
                resp.body?.string().orEmpty()
            }
            val obj = json.parseToJsonElement(raw).jsonObject
            val title = obj["title"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() } ?: return null
            val author = obj["author_name"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
                ?.removeSuffix(" - Topic")
                ?: "YouTube Music"
            title to author
        }.getOrNull()
    }

    private fun fetchSpotifyEmbedTrackMeta(trackId: String): CachedSpotifyMeta? {
        return runCatching {
            val request = Request.Builder()
                .url("https://open.spotify.com/embed/track/${Uri.encode(trackId)}")
                .header("User-Agent", EXOPLAYER_USER_AGENT)
                .build()
            val html = httpClient.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) return null
                resp.body?.string().orEmpty()
            }
            val marker = "<script id=\"__NEXT_DATA__\" type=\"application/json\">"
            val start = html.indexOf(marker)
            if (start < 0) return null
            val jsonStart = start + marker.length
            val jsonEnd = html.indexOf("</script>", jsonStart)
            if (jsonEnd < 0) return null

            val root = json.parseToJsonElement(html.substring(jsonStart, jsonEnd)).jsonObject
            val entity = root["props"]?.jsonObject
                ?.get("pageProps")?.jsonObject
                ?.get("state")?.jsonObject
                ?.get("data")?.jsonObject
                ?.get("entity")?.jsonObject
                ?: return null

            val title = entity["title"]?.jsonPrimitive?.contentOrNull
                ?: entity["name"]?.jsonPrimitive?.contentOrNull
                ?: return null
            val artist = entity["subtitle"]?.jsonPrimitive?.contentOrNull
                ?: entity["artists"]?.jsonArray?.firstOrNull()?.jsonObject?.get("name")?.jsonPrimitive?.contentOrNull
                ?: "Spotify Artist"
            val durationMs = entity["duration"]?.jsonPrimitive?.longOrNull ?: 0L
            val previewUrl = entity["audioPreview"]?.jsonObject?.get("url")?.jsonPrimitive?.contentOrNull
            val artworkUrl = entity["coverArt"]?.jsonObject
                ?.get("sources")?.jsonArray?.firstOrNull()?.jsonObject
                ?.get("url")?.jsonPrimitive?.contentOrNull
                ?: entity["visualIdentity"]?.jsonObject
                    ?.get("image")?.jsonArray?.firstOrNull()?.jsonObject
                    ?.get("url")?.jsonPrimitive?.contentOrNull

            CachedSpotifyMeta(
                title = title,
                artist = artist,
                durationMs = durationMs,
                previewUrl = previewUrl,
                artworkUrl = artworkUrl
            )
        }.getOrNull()
    }

    private fun findFallbackAudioPreview(query: String): String? {
        val cleaned = query.trim()
        if (cleaned.isBlank()) return null

        // Deezer public track search audio fallback
        runCatching {
            val req = Request.Builder()
                .url("https://api.deezer.com/search?q=${Uri.encode(cleaned)}&limit=3")
                .build()
            httpClient.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    val root = json.parseToJsonElement(resp.body?.string().orEmpty()).jsonObject
                    val data = root["data"]?.jsonArray.orEmpty()
                    for (item in data) {
                        val preview = (item as? JsonObject)?.get("preview")?.jsonPrimitive?.contentOrNull
                        if (!preview.isNullOrBlank() && probeStreamUrl(preview)) {
                            return preview
                        }
                    }
                }
            }
        }

        // iTunes Search public audio fallback
        runCatching {
            val req = Request.Builder()
                .url("https://itunes.apple.com/search?term=${Uri.encode(cleaned)}&media=music&entity=song&limit=3")
                .build()
            httpClient.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    val root = json.parseToJsonElement(resp.body?.string().orEmpty()).jsonObject
                    val results = root["results"]?.jsonArray.orEmpty()
                    for (item in results) {
                        val preview = (item as? JsonObject)?.get("previewUrl")?.jsonPrimitive?.contentOrNull
                        if (!preview.isNullOrBlank() && probeStreamUrl(preview)) {
                            return preview
                        }
                    }
                }
            }
        }

        return null
    }

    private fun probeStreamUrl(url: String): Boolean {
        return runCatching {
            val req = Request.Builder()
                .url(url)
                .header("User-Agent", EXOPLAYER_USER_AGENT)
                .header("Range", "bytes=0-1023")
                .build()
            httpClient.newCall(req).execute().use { resp ->
                resp.isSuccessful || resp.code == 206
            }
        }.getOrDefault(false)
    }

    private fun buildSearchQuery(title: String, artist: String): String {
        val cleanTitle = title
            .replace(Regex("\\s*\\(feat\\..*?\\)", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\s*\\[.*?\\]"), "")
            .trim()
        val cleanArtist = artist
            .removeSuffix(" - Topic")
            .takeIf {
                it.isNotBlank() &&
                    !it.equals("YouTube Music", ignoreCase = true) &&
                    !it.equals("Spotify", ignoreCase = true) &&
                    !it.equals("Spotify Artist", ignoreCase = true) &&
                    !it.equals("Spotify Stream", ignoreCase = true) &&
                    !it.equals("In-App Stream", ignoreCase = true)
            }.orEmpty()
        return listOf(cleanTitle, cleanArtist).filter { it.isNotBlank() }.joinToString(" ").trim()
    }
}
