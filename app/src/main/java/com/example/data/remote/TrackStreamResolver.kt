package com.example.data.remote

import android.net.Uri
import com.example.data.local.TrackDao
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

data class ResolvedAudioStream(
    val url: String,
    val headers: Map<String, String> = emptyMap(),
)

class TrackStreamResolver(
    private val trackDao: TrackDao,
    private val spotifyClient: SpotifyClient,
    private val youTubeMusicClient: YouTubeMusicClient,
) {
    companion object {
        const val EXOPLAYER_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"

        private const val CACHE_TTL_MS = 90 * 60 * 1000L
        private const val VISITOR_DATA_TTL_MS = 30 * 60 * 1000L

        private val INVIDIOUS_INSTANCES = listOf(
            "https://inv.nadeko.net",
            "https://invidious.nerdvpn.de",
            "https://invidious.f5.si",
            "https://yt.artemislena.eu",
        )

        private val PIPED_INSTANCES = listOf(
            "https://pipedapi.kavin.rocks",
            "https://api.piped.private.coffee",
            "https://pipedapi.adminforge.de",
            "https://pipedapi.leptons.xyz",
        )
    }

    private data class CachedStream(
        val stream: ResolvedAudioStream,
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
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    val streamHttpClient: OkHttpClient
        get() = httpClient

    private val streamCache = ConcurrentHashMap<String, CachedStream>()
    private val spotifyMetaCache = ConcurrentHashMap<String, CachedSpotifyMeta>()

    private val visitorLock = Any()
    @Volatile
    private var cachedVisitorData: String? = null
    @Volatile
    private var visitorDataFetchedAtMs: Long = 0L

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
            var working = if (!isPlaceholder && !track.artworkUrl.isNullOrBlank()) {
                track
            } else {
                val fetched = fetchSpotifyEmbedTrackMeta(track.id)
                if (fetched != null) {
                    spotifyMetaCache[track.id] = fetched
                    track.copy(
                        title = fetched.title,
                        artist = fetched.artist,
                        durationMs = if (fetched.durationMs > 0L) fetched.durationMs else track.durationMs,
                        previewUrl = fetched.previewUrl ?: track.previewUrl,
                        artworkUrl = fetched.artworkUrl ?: track.artworkUrl
                    )
                } else {
                    track
                }
            }

            if (working.durationMs <= 120_000L && working.title != "Spotify Track") {
                val realDuration = spotifyClient.resolveFullDurationMs(
                    trackId = working.id,
                    title = working.title,
                    artist = working.artist,
                    currentMs = working.durationMs
                )
                if (realDuration > working.durationMs) {
                    working = working.copy(durationMs = realDuration)
                }
            }
            rememberSpotifyTracks(listOf(working))
            working
        }

    fun resolvePlayableUrlBlocking(uri: Uri): String = resolvePlayableStreamBlocking(uri).url

    /**
     * Synchronous resolver invoked on ExoPlayer's background loader thread via ResolvingDataSource.
     * Returns both the target URL and client-matched HTTP headers (critical so YouTube CDNs don't 403).
     */
    fun resolvePlayableStreamBlocking(uri: Uri): ResolvedAudioStream {
        val key = uri.toString()
        val cached = streamCache[key]
        if (cached != null && (System.currentTimeMillis() - cached.cachedAtMs) < CACHE_TTL_MS) {
            return cached.stream
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
            else -> ResolvedAudioStream(uri.toString())
        }

        streamCache[key] = CachedStream(resolved)
        return resolved
    }

    private fun resolveYouTubeStreamBlocking(videoId: String): ResolvedAudioStream {
        // 1. Direct YouTube extraction on primary videoId
        extractYouTubeAudioStream(videoId)?.let { return it }

        // 2. Fallback: look up title & artist from DB or YouTube oEmbed, then search alternate uploads
        val dbTrack = kotlinx.coroutines.runBlocking { trackDao.getTrackById("yt_$videoId") }
        val oembed = if (dbTrack == null || dbTrack.title.startsWith("YouTube Music (")) {
            fetchYouTubeOEmbed(videoId)
        } else null

        val title = oembed?.first ?: dbTrack?.title.orEmpty()
        val artist = oembed?.second ?: dbTrack?.artist.orEmpty()
        val cleanQuery = buildSearchQuery(title, artist)
        val knownDuration = dbTrack?.durationMs?.takeIf { it > 45_000L } ?: 0L

        if (cleanQuery.isNotBlank()) {
            val queries = listOf(cleanQuery, "$cleanQuery audio").distinct()
            val triedIds = mutableSetOf(videoId)
            for (q in queries) {
                val altIds = searchYouTubeVideoIds(q, targetDurationMs = knownDuration)
                    .filter { triedIds.add(it) }
                    .take(4)
                for (altId in altIds) {
                    extractYouTubeAudioStream(altId)?.let { return it }
                }
            }
            findFallbackAudioPreview(cleanQuery)?.let { return it }
        }

        throw IOException("Unable to load playable audio stream for YouTube track")
    }

    private fun resolveSpotifyStreamBlocking(trackId: String): ResolvedAudioStream {
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
        var durationMs = dbTrack?.durationMs?.takeIf { it > 45_000L }
            ?: cachedMeta?.durationMs?.takeIf { it > 45_000L }
            ?: fetchedMeta?.durationMs?.takeIf { it > 45_000L }
            ?: 0L
        val previewUrl = cachedMeta?.previewUrl ?: fetchedMeta?.previewUrl

        val query = buildSearchQuery(title, artist)
        if (durationMs <= 45_000L && title.isNotBlank()) {
            durationMs = spotifyClient.resolveFullDurationMs(
                trackId = trackId,
                title = title,
                artist = artist,
                currentMs = durationMs
            )
            if (durationMs > 45_000L && dbTrack != null) {
                kotlinx.coroutines.runBlocking {
                    runCatching { trackDao.updateDuration(dbTrack.id, durationMs) }
                }
            }
        }

        if (query.isNotBlank()) {
            // 1. Full-song audio via YouTube search + multi-tier extraction
            val triedIds = mutableSetOf<String>()
            val ytQueries = listOf("$query official audio", query).distinct()
            for (ytQuery in ytQueries) {
                val candidateIds = searchYouTubeVideoIds(ytQuery, targetDurationMs = durationMs)
                    .filter { triedIds.add(it) }
                    .take(4)
                for (vid in candidateIds) {
                    extractYouTubeAudioStream(vid)?.let { return it }
                }
            }
        }

        // 2. Spotify's own direct MP3 preview URL (last resort)
        val previewHeaders = mapOf("User-Agent" to EXOPLAYER_USER_AGENT)
        if (!previewUrl.isNullOrBlank() && probeStreamUrl(previewUrl, previewHeaders)) {
            return ResolvedAudioStream(previewUrl, previewHeaders)
        }

        // 3. Deezer / iTunes direct audio fallback
        if (query.isNotBlank()) {
            findFallbackAudioPreview(query)?.let { return it }
        }

        throw IOException("Unable to find a playable audio stream for \"$title\"")
    }

    private fun extractYouTubeAudioStream(videoId: String): ResolvedAudioStream? {
        val visitorData = getVisitorData()

        // Tier 1a: InnerTube ANDROID_VR (direct googlevideo URLs without signature cipher)
        fetchInnerTubeAudioStream(
            videoId = videoId,
            clientName = "ANDROID_VR",
            clientVersion = "1.60.19",
            clientIdHeader = "28",
            clientUa = "com.google.android.apps.youtube.vr.oculus/1.60.19 (Linux; U; Android 12L; eureka-user Build/SQ3A.220605.009.A1) gzip",
            extraClientJson = "\"androidSdkVersion\":32,\"osName\":\"Android\",\"osVersion\":\"12L\",",
            visitorData = visitorData,
        )?.let { return it }

        // Tier 1b: InnerTube ANDROID_MUSIC (audio-focused client)
        fetchInnerTubeAudioStream(
            videoId = videoId,
            clientName = "ANDROID_MUSIC",
            clientVersion = "7.02.52",
            clientIdHeader = "21",
            clientUa = "com.google.android.apps.youtube.music/7.02.52 (Linux; U; Android 14; en_US; Pixel 8 Build/UQ1A.240205.002) gzip",
            extraClientJson = "\"androidSdkVersion\":34,\"osName\":\"Android\",\"osVersion\":\"14\",",
            visitorData = visitorData,
        )?.let { return it }

        // Tier 1c: InnerTube IOS (supports both direct adaptiveFormats and HLS manifest)
        fetchInnerTubeAudioStream(
            videoId = videoId,
            clientName = "IOS",
            clientVersion = "19.45.4",
            clientIdHeader = "5",
            clientUa = "com.google.ios.youtube/19.45.4 (iPhone16,2; U; CPU iOS 18_1_0 like Mac OS X;)",
            extraClientJson = "\"deviceMake\":\"Apple\",\"deviceModel\":\"iPhone16,2\",\"osName\":\"iPhone\",\"osVersion\":\"18.1.0.22B83\",",
            visitorData = visitorData,
        )?.let { return it }

        // Tier 1d: InnerTube ANDROID_TESTSUITE
        fetchInnerTubeAudioStream(
            videoId = videoId,
            clientName = "ANDROID_TESTSUITE",
            clientVersion = "1.9",
            clientIdHeader = "30",
            clientUa = "com.google.android.youtube/1.9 (Linux; U; Android 11) gzip",
            extraClientJson = "\"androidSdkVersion\":30,\"osName\":\"Android\",\"osVersion\":\"11\",",
            visitorData = visitorData,
        )?.let { return it }

        // Tier 2: Public Invidious API (handles server-side signature/proxy fallback)
        for (base in INVIDIOUS_INSTANCES) {
            fetchInvidiousAudioStream(base, videoId)?.let { return it }
        }

        // Tier 3: Public Piped API audioStreams
        for (base in PIPED_INSTANCES) {
            fetchPipedAudioStream(base, videoId)?.let { return it }
        }

        return null
    }

    private fun getVisitorData(): String? {
        val cached = cachedVisitorData
        if (!cached.isNullOrBlank() && (System.currentTimeMillis() - visitorDataFetchedAtMs) < VISITOR_DATA_TTL_MS) {
            return cached
        }
        synchronized(visitorLock) {
            val recheck = cachedVisitorData
            if (!recheck.isNullOrBlank() && (System.currentTimeMillis() - visitorDataFetchedAtMs) < VISITOR_DATA_TTL_MS) {
                return recheck
            }

            val fetched = runCatching {
                val payload = """
                    {
                      "context": {
                        "client": {
                          "clientName": "WEB",
                          "clientVersion": "2.20250401.00.00",
                          "hl": "en",
                          "gl": "US"
                        }
                      }
                    }
                """.trimIndent()
                val req = Request.Builder()
                    .url("https://www.youtube.com/youtubei/v1/visitor_id?prettyPrint=false")
                    .post(payload.toRequestBody("application/json; charset=utf-8".toMediaType()))
                    .header("User-Agent", EXOPLAYER_USER_AGENT)
                    .build()
                httpClient.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return@runCatching null
                    val raw = resp.body?.string().orEmpty()
                    val root = json.parseToJsonElement(raw).jsonObject
                    root["responseContext"]?.jsonObject
                        ?.get("visitorData")?.jsonPrimitive?.contentOrNull
                        ?.takeIf { it.isNotBlank() }
                }
            }.getOrNull() ?: runCatching {
                val req = Request.Builder()
                    .url("https://www.youtube.com/sw.js_data")
                    .header("User-Agent", EXOPLAYER_USER_AGENT)
                    .build()
                httpClient.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) return@runCatching null
                    val body = resp.body?.string().orEmpty()
                    Regex("\"(Cg[a-zA-Z0-9%_-]{14,})\"").find(body)?.groupValues?.getOrNull(1)
                }
            }.getOrNull()

            if (!fetched.isNullOrBlank()) {
                cachedVisitorData = fetched
                visitorDataFetchedAtMs = System.currentTimeMillis()
            }
            return fetched ?: cachedVisitorData
        }
    }

    private fun fetchInnerTubeAudioStream(
        videoId: String,
        clientName: String,
        clientVersion: String,
        clientIdHeader: String,
        clientUa: String,
        extraClientJson: String,
        visitorData: String?,
    ): ResolvedAudioStream? {
        return runCatching {
            val visitorJson = if (!visitorData.isNullOrBlank()) {
                "\"visitorData\":\"$visitorData\","
            } else ""
            val payload = """
                {
                  "videoId": "$videoId",
                  "context": {
                    "client": {
                      "clientName": "$clientName",
                      "clientVersion": "$clientVersion",
                      $visitorJson
                      $extraClientJson
                      "hl": "en",
                      "gl": "US"
                    }
                  },
                  "contentCheckOk": true,
                  "racyCheckOk": true
                }
            """.trimIndent()

            val requestBuilder = Request.Builder()
                .url("https://www.youtube.com/youtubei/v1/player?prettyPrint=false")
                .post(payload.toRequestBody("application/json; charset=utf-8".toMediaType()))
                .header("User-Agent", clientUa)
                .header("X-YouTube-Client-Name", clientIdHeader)
                .header("X-YouTube-Client-Version", clientVersion)
            if (!visitorData.isNullOrBlank()) {
                requestBuilder.header("X-Goog-Visitor-Id", visitorData)
            }

            val raw = httpClient.newCall(requestBuilder.build()).execute().use { resp ->
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
            val streamHeaders = mapOf("User-Agent" to clientUa)

            val candidates = (adaptive + formats)
                .mapNotNull { it as? JsonObject }
                .mapNotNull { obj ->
                    val url = obj["url"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
                        ?: return@mapNotNull null
                    val mime = obj["mimeType"]?.jsonPrimitive?.contentOrNull.orEmpty()
                    val bitrate = obj["bitrate"]?.jsonPrimitive?.longOrNull ?: 0L
                    val priority = when {
                        mime.startsWith("audio/mp4", ignoreCase = true) -> 4
                        mime.startsWith("audio/", ignoreCase = true) -> 3
                        mime.startsWith("video/mp4", ignoreCase = true) -> 2
                        else -> 0
                    }
                    Triple(priority, bitrate, url)
                }
                .filter { it.first > 0 }
                .sortedWith(compareByDescending<Triple<Int, Long, String>> { it.first }.thenByDescending { it.second })
                .toMutableList()

            streamingData["hlsManifestUrl"]?.jsonPrimitive?.contentOrNull
                ?.takeIf { it.isNotBlank() }
                ?.let { hlsUrl ->
                    candidates.add(Triple(1, 0L, hlsUrl))
                }

            val matched = candidates.firstOrNull { (_, _, url) ->
                probeStreamUrl(url, streamHeaders)
            } ?: return null

            ResolvedAudioStream(matched.third, streamHeaders)
        }.getOrNull()
    }

    private fun fetchInvidiousAudioStream(baseUrl: String, videoId: String): ResolvedAudioStream? {
        return runCatching {
            val streamHeaders = mapOf("User-Agent" to EXOPLAYER_USER_AGENT)
            val request = Request.Builder()
                .url("$baseUrl/api/v1/videos/${Uri.encode(videoId)}?fields=adaptiveFormats,formatStreams")
                .header("User-Agent", EXOPLAYER_USER_AGENT)
                .build()
            val raw = httpClient.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) return null
                resp.body?.string().orEmpty()
            }
            if (raw.isBlank()) return null

            val root = json.parseToJsonElement(raw).jsonObject
            val adaptive = root["adaptiveFormats"]?.jsonArray.orEmpty()
            val formatStreams = root["formatStreams"]?.jsonArray.orEmpty()

            val candidates = (adaptive + formatStreams)
                .mapNotNull { it as? JsonObject }
                .mapNotNull { obj ->
                    val rawUrl = obj["url"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
                        ?: return@mapNotNull null
                    val resolvedUrl = if (rawUrl.startsWith("/")) "$baseUrl$rawUrl" else rawUrl
                    val type = obj["type"]?.jsonPrimitive?.contentOrNull.orEmpty()
                    val bitrate = obj["bitrate"]?.jsonPrimitive?.contentOrNull?.toLongOrNull()
                        ?: obj["bitrate"]?.jsonPrimitive?.longOrNull
                        ?: 0L
                    val priority = when {
                        type.startsWith("audio/mp4", ignoreCase = true) -> 4
                        type.startsWith("audio/", ignoreCase = true) -> 3
                        type.startsWith("video/mp4", ignoreCase = true) -> 2
                        else -> 0
                    }
                    Triple(priority, bitrate, resolvedUrl)
                }
                .filter { it.first > 0 }
                .sortedWith(compareByDescending<Triple<Int, Long, String>> { it.first }.thenByDescending { it.second })
                .toMutableList()

            // Local proxy fallback on the Invidious instance (bypasses IP-locked googlevideo URLs)
            candidates.add(
                Triple(1, 0L, "$baseUrl/latest_version?id=${Uri.encode(videoId)}&itag=18&local=true")
            )

            val matched = candidates.firstOrNull { (_, _, url) ->
                probeStreamUrl(url, streamHeaders)
            } ?: return null

            ResolvedAudioStream(matched.third, streamHeaders)
        }.getOrNull()
    }

    private fun fetchPipedAudioStream(baseUrl: String, videoId: String): ResolvedAudioStream? {
        return runCatching {
            val streamHeaders = mapOf("User-Agent" to EXOPLAYER_USER_AGENT)
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
                    val rawUrl = obj["url"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
                        ?: return@mapNotNull null
                    val url = if (rawUrl.startsWith("/")) "$baseUrl$rawUrl" else rawUrl
                    val mime = obj["mimeType"]?.jsonPrimitive?.contentOrNull.orEmpty()
                    val bitrate = obj["bitrate"]?.jsonPrimitive?.longOrNull ?: 0L
                    val pref = if (mime.contains("mp4", ignoreCase = true) || mime.contains("m4a", ignoreCase = true)) 2 else 1
                    Triple(pref, bitrate, url)
                }
                .sortedWith(compareByDescending<Triple<Int, Long, String>> { it.first }.thenByDescending { it.second })

            val matched = sorted.firstOrNull { (_, _, url) ->
                probeStreamUrl(url, streamHeaders)
            } ?: return null

            ResolvedAudioStream(matched.third, streamHeaders)
        }.getOrNull()
    }

    private fun searchYouTubeVideoIds(
        query: String,
        targetDurationMs: Long = 0L,
    ): List<String> {
        val results = mutableListOf<Pair<String, Long>>()

        // Primary: InnerTube WEB search
        runCatching {
            val visitorData = getVisitorData()
            val visitorJson = if (!visitorData.isNullOrBlank()) "\"visitorData\":\"$visitorData\"," else ""
            val escapedQuery = query.replace("\\", "\\\\").replace("\"", "\\\"")
            val payload = """
                {
                  "query": "$escapedQuery",
                  "context": {
                    "client": {
                      "clientName": "WEB",
                      "clientVersion": "2.20250401.00.00",
                      $visitorJson
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
                if (!resp.isSuccessful) "" else resp.body?.string().orEmpty()
            }
            if (raw.isNotBlank()) {
                val root = json.parseToJsonElement(raw).jsonObject
                collectVideoRenderers(root, results)
            }
        }

        // Secondary: YouTube web search results ytInitialData fallback
        if (results.isEmpty()) {
            runCatching {
                val req = Request.Builder()
                    .url("https://www.youtube.com/results?search_query=${Uri.encode(query)}&hl=en")
                    .header(
                        "User-Agent",
                        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
                    )
                    .build()
                val html = httpClient.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) "" else resp.body?.string().orEmpty()
                }
                val ytJson = extractYtInitialDataJson(html)
                if (!ytJson.isNullOrBlank()) {
                    val root = json.parseToJsonElement(ytJson).jsonObject
                    collectVideoRenderers(root, results)
                }
            }
        }

        if (results.isEmpty()) return emptyList()

        // Never bias YouTube search toward preview-capped (<= 45s) Spotify embed durations
        val usableTargetMs = targetDurationMs.takeIf { it > 45_000L } ?: 0L
        return if (usableTargetMs > 0L) {
            results.sortedBy { (_, durMs) ->
                when {
                    durMs <= 0L -> 120_000L
                    usableTargetMs >= 90_000L && durMs < 60_000L -> 800_000L + (60_000L - durMs)
                    durMs > 900_000L -> 700_000L + kotlin.math.abs(durMs - usableTargetMs)
                    else -> kotlin.math.abs(durMs - usableTargetMs)
                }
            }.map { it.first }
        } else {
            results.sortedBy { (_, durMs) ->
                when {
                    durMs <= 0L -> 120_000L
                    durMs < 90_000L -> 600_000L + (90_000L - durMs)
                    durMs > 600_000L -> 600_000L + (durMs - 600_000L)
                    else -> kotlin.math.abs(durMs - 210_000L) / 4L
                }
            }.map { it.first }
        }
    }

    private fun extractYtInitialDataJson(html: String): String? {
        if (html.isBlank()) return null
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
                    if (depth == 0) return html.substring(startBrace, i + 1)
                }
            }
        }
        return null
    }

    private fun collectVideoRenderers(
        el: JsonElement,
        out: MutableList<Pair<String, Long>>,
    ) {
        if (out.size >= 12) return
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
            val rawDur = entity["duration"]?.jsonPrimitive?.longOrNull ?: 0L
            val durationMs = if (rawDur in 1L..9_999L) rawDur * 1000L else rawDur
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

    private fun findFallbackAudioPreview(query: String): ResolvedAudioStream? {
        val cleaned = query.trim()
        if (cleaned.isBlank()) return null
        val defaultHeaders = mapOf("User-Agent" to EXOPLAYER_USER_AGENT)

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
                        if (!preview.isNullOrBlank() && probeStreamUrl(preview, defaultHeaders)) {
                            return ResolvedAudioStream(preview, defaultHeaders)
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
                        if (!preview.isNullOrBlank() && probeStreamUrl(preview, defaultHeaders)) {
                            return ResolvedAudioStream(preview, defaultHeaders)
                        }
                    }
                }
            }
        }

        return null
    }

    private fun probeStreamUrl(
        url: String,
        headers: Map<String, String> = mapOf("User-Agent" to EXOPLAYER_USER_AGENT),
    ): Boolean {
        val rangedOk = runCatching {
            val builder = Request.Builder().url(url).header("Range", "bytes=0-1023")
            var hasUa = false
            headers.forEach { (k, v) ->
                if (k.equals("User-Agent", ignoreCase = true)) hasUa = true
                builder.header(k, v)
            }
            if (!hasUa) builder.header("User-Agent", EXOPLAYER_USER_AGENT)
            httpClient.newCall(builder.build()).execute().use { resp ->
                val contentType = resp.header("Content-Type").orEmpty()
                (resp.isSuccessful || resp.code == 206) &&
                    !contentType.contains("text/html", ignoreCase = true)
            }
        }.getOrDefault(false)
        if (rangedOk) return true

        // Fallback probe without Range header (e.g. HLS .m3u8 playlists or proxies rejecting Range)
        return runCatching {
            val builder = Request.Builder().url(url)
            var hasUa = false
            headers.forEach { (k, v) ->
                if (k.equals("User-Agent", ignoreCase = true)) hasUa = true
                builder.header(k, v)
            }
            if (!hasUa) builder.header("User-Agent", EXOPLAYER_USER_AGENT)
            httpClient.newCall(builder.build()).execute().use { resp ->
                val contentType = resp.header("Content-Type").orEmpty()
                resp.isSuccessful && !contentType.contains("text/html", ignoreCase = true)
            }
        }.getOrDefault(false)
    }

    private fun buildSearchQuery(title: String, artist: String): String {
        val cleanTitle = title
            .replace(Regex("\\s*\\((?:feat|ft|official|lyric|lyrics|audio|video|visualizer|full|hd|4k).*?\\)", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\s*\\[.*?\\]"), "")
            .substringBefore(" | ")
            .trim()
        val cleanArtist = artist
            .removeSuffix(" - Topic")
            .takeIf {
                it.isNotBlank() &&
                    !it.equals("YouTube Music", ignoreCase = true) &&
                    !it.equals("Spotify", ignoreCase = true) &&
                    !it.equals("Spotify Artist", ignoreCase = true) &&
                    !it.equals("Spotify Stream", ignoreCase = true) &&
                    !it.equals("In-App Stream", ignoreCase = true) &&
                    !it.equals("RinMusic Stream", ignoreCase = true)
            }.orEmpty()
        if (cleanArtist.isNotBlank() && cleanTitle.contains(cleanArtist, ignoreCase = true)) {
            return cleanTitle
        }
        return listOf(cleanTitle, cleanArtist).filter { it.isNotBlank() }.joinToString(" ").trim()
    }
}
