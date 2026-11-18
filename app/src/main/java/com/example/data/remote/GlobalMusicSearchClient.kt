package com.example.data.remote

import android.net.Uri
import com.example.data.model.TrackEntity
import com.example.data.model.TrackSourceType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

data class CountryMarket(
    val code: String,
    val name: String,
    val region: String,
)

data class GlobalMusicTrack(
    val id: String,
    val trackId: Long,
    val title: String,
    val artist: String,
    val album: String,
    val genre: String,
    val durationMs: Long,
    val previewUrl: String,
    val artworkUrl: String?,
    val countryCode: String,
    val countryName: String,
) {
    fun toTrackEntity(): TrackEntity = TrackEntity(
        id = id,
        title = title,
        artist = artist,
        album = album,
        genre = genre,
        durationMs = durationMs,
        sourceType = TrackSourceType.ONLINE,
        streamUrl = previewUrl,
        artworkResKey = when {
            genre.contains("rock", ignoreCase = true) ||
                genre.contains("acoustic", ignoreCase = true) ||
                genre.contains("folk", ignoreCase = true) -> "acoustic"
            genre.contains("electronic", ignoreCase = true) ||
                genre.contains("dance", ignoreCase = true) ||
                genre.contains("pop", ignoreCase = true) -> "electronic"
            genre.contains("hip-hop", ignoreCase = true) ||
                genre.contains("r&b", ignoreCase = true) ||
                genre.contains("jazz", ignoreCase = true) -> "lofi"
            else -> "studio"
        },
        artworkUrl = artworkUrl,
        isLiveStream = false,
        isUserAdded = false,
    )
}

@Serializable
private data class ItunesSearchResponseDto(
    val resultCount: Int = 0,
    val results: List<ItunesTrackDto> = emptyList(),
)

@Serializable
private data class ItunesTrackDto(
    val trackId: Long = 0L,
    val trackName: String? = null,
    val artistName: String? = null,
    val collectionName: String? = null,
    val primaryGenreName: String? = null,
    val previewUrl: String? = null,
    val artworkUrl100: String? = null,
    val trackTimeMillis: Long = 0L,
)

object GlobalMusicMarkets {
    val ALL_COUNTRIES = CountryMarket("ALL", "All Countries", "Worldwide")

    // Storefronts sampled in parallel when "All Countries" is selected so every continent appears
    val GLOBAL_SAMPLE_CODES = listOf(
        "US", "GB", "IN", "JP", "KR", "BR", "DE", "FR", "NG", "MX", "AU", "TR"
    )

    val ALL_MARKETS: List<CountryMarket> = listOf(
        ALL_COUNTRIES,
        CountryMarket("AF", "Afghanistan", "Asia"),
        CountryMarket("AL", "Albania", "Europe"),
        CountryMarket("DZ", "Algeria", "Africa"),
        CountryMarket("AR", "Argentina", "South America"),
        CountryMarket("AM", "Armenia", "Asia"),
        CountryMarket("AU", "Australia", "Oceania"),
        CountryMarket("AT", "Austria", "Europe"),
        CountryMarket("AZ", "Azerbaijan", "Asia"),
        CountryMarket("BH", "Bahrain", "Middle East"),
        CountryMarket("BD", "Bangladesh", "Asia"),
        CountryMarket("BY", "Belarus", "Europe"),
        CountryMarket("BE", "Belgium", "Europe"),
        CountryMarket("BO", "Bolivia", "South America"),
        CountryMarket("BA", "Bosnia and Herzegovina", "Europe"),
        CountryMarket("BR", "Brazil", "South America"),
        CountryMarket("BG", "Bulgaria", "Europe"),
        CountryMarket("KH", "Cambodia", "Asia"),
        CountryMarket("CM", "Cameroon", "Africa"),
        CountryMarket("CA", "Canada", "North America"),
        CountryMarket("CL", "Chile", "South America"),
        CountryMarket("CN", "China", "Asia"),
        CountryMarket("CO", "Colombia", "South America"),
        CountryMarket("CR", "Costa Rica", "North America"),
        CountryMarket("HR", "Croatia", "Europe"),
        CountryMarket("CY", "Cyprus", "Europe"),
        CountryMarket("CZ", "Czechia", "Europe"),
        CountryMarket("DK", "Denmark", "Europe"),
        CountryMarket("DO", "Dominican Republic", "North America"),
        CountryMarket("EC", "Ecuador", "South America"),
        CountryMarket("EG", "Egypt", "Africa"),
        CountryMarket("SV", "El Salvador", "North America"),
        CountryMarket("EE", "Estonia", "Europe"),
        CountryMarket("FI", "Finland", "Europe"),
        CountryMarket("FR", "France", "Europe"),
        CountryMarket("GE", "Georgia", "Asia"),
        CountryMarket("DE", "Germany", "Europe"),
        CountryMarket("GH", "Ghana", "Africa"),
        CountryMarket("GR", "Greece", "Europe"),
        CountryMarket("GT", "Guatemala", "North America"),
        CountryMarket("HN", "Honduras", "North America"),
        CountryMarket("HK", "Hong Kong", "Asia"),
        CountryMarket("HU", "Hungary", "Europe"),
        CountryMarket("IS", "Iceland", "Europe"),
        CountryMarket("IN", "India", "Asia"),
        CountryMarket("ID", "Indonesia", "Asia"),
        CountryMarket("IQ", "Iraq", "Middle East"),
        CountryMarket("IE", "Ireland", "Europe"),
        CountryMarket("IL", "Israel", "Middle East"),
        CountryMarket("IT", "Italy", "Europe"),
        CountryMarket("JM", "Jamaica", "North America"),
        CountryMarket("JP", "Japan", "Asia"),
        CountryMarket("JO", "Jordan", "Middle East"),
        CountryMarket("KZ", "Kazakhstan", "Asia"),
        CountryMarket("KE", "Kenya", "Africa"),
        CountryMarket("KR", "South Korea", "Asia"),
        CountryMarket("KW", "Kuwait", "Middle East"),
        CountryMarket("LV", "Latvia", "Europe"),
        CountryMarket("LB", "Lebanon", "Middle East"),
        CountryMarket("LT", "Lithuania", "Europe"),
        CountryMarket("LU", "Luxembourg", "Europe"),
        CountryMarket("MY", "Malaysia", "Asia"),
        CountryMarket("MX", "Mexico", "North America"),
        CountryMarket("MA", "Morocco", "Africa"),
        CountryMarket("MM", "Myanmar", "Asia"),
        CountryMarket("NP", "Nepal", "Asia"),
        CountryMarket("NL", "Netherlands", "Europe"),
        CountryMarket("NZ", "New Zealand", "Oceania"),
        CountryMarket("NI", "Nicaragua", "North America"),
        CountryMarket("NG", "Nigeria", "Africa"),
        CountryMarket("NO", "Norway", "Europe"),
        CountryMarket("OM", "Oman", "Middle East"),
        CountryMarket("PK", "Pakistan", "Asia"),
        CountryMarket("PA", "Panama", "North America"),
        CountryMarket("PY", "Paraguay", "South America"),
        CountryMarket("PE", "Peru", "South America"),
        CountryMarket("PH", "Philippines", "Asia"),
        CountryMarket("PL", "Poland", "Europe"),
        CountryMarket("PT", "Portugal", "Europe"),
        CountryMarket("QA", "Qatar", "Middle East"),
        CountryMarket("RO", "Romania", "Europe"),
        CountryMarket("SA", "Saudi Arabia", "Middle East"),
        CountryMarket("SN", "Senegal", "Africa"),
        CountryMarket("RS", "Serbia", "Europe"),
        CountryMarket("SG", "Singapore", "Asia"),
        CountryMarket("SK", "Slovakia", "Europe"),
        CountryMarket("SI", "Slovenia", "Europe"),
        CountryMarket("ZA", "South Africa", "Africa"),
        CountryMarket("ES", "Spain", "Europe"),
        CountryMarket("LK", "Sri Lanka", "Asia"),
        CountryMarket("SE", "Sweden", "Europe"),
        CountryMarket("CH", "Switzerland", "Europe"),
        CountryMarket("TW", "Taiwan", "Asia"),
        CountryMarket("TZ", "Tanzania", "Africa"),
        CountryMarket("TH", "Thailand", "Asia"),
        CountryMarket("TN", "Tunisia", "Africa"),
        CountryMarket("TR", "Turkey", "Europe/Asia"),
        CountryMarket("UG", "Uganda", "Africa"),
        CountryMarket("UA", "Ukraine", "Europe"),
        CountryMarket("AE", "United Arab Emirates", "Middle East"),
        CountryMarket("GB", "United Kingdom", "Europe"),
        CountryMarket("US", "United States", "North America"),
        CountryMarket("UY", "Uruguay", "South America"),
        CountryMarket("UZ", "Uzbekistan", "Asia"),
        CountryMarket("VE", "Venezuela", "South America"),
        CountryMarket("VN", "Vietnam", "Asia"),
        CountryMarket("ZM", "Zambia", "Africa"),
        CountryMarket("ZW", "Zimbabwe", "Africa"),
    )

    private val byCode = ALL_MARKETS.associateBy { it.code }

    fun findByCode(code: String): CountryMarket = byCode[code.uppercase()] ?: ALL_COUNTRIES
}

class GlobalMusicSearchClient {

    private val json = Json { ignoreUnknownKeys = true }
    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    suspend fun searchMusic(
        query: String,
        countryCode: String = GlobalMusicMarkets.ALL_COUNTRIES.code,
    ): Result<List<GlobalMusicTrack>> = withContext(Dispatchers.IO) {
        runCatching {
            val trimmed = query.trim()
            if (trimmed.isBlank()) return@runCatching emptyList()

            if (countryCode.equals("ALL", ignoreCase = true)) {
                val perCountryLists = coroutineScope {
                    GlobalMusicMarkets.GLOBAL_SAMPLE_CODES.map { code ->
                        async {
                            runCatching { queryStorefront(trimmed, code, limit = 6) }
                                .getOrDefault(emptyList())
                        }
                    }.awaitAll()
                }
                interleaveAndDedupe(perCountryLists)
            } else {
                queryStorefront(trimmed, countryCode.uppercase(), limit = 30)
            }
        }
    }

    private fun queryStorefront(
        query: String,
        countryCode: String,
        limit: Int,
    ): List<GlobalMusicTrack> {
        val encoded = Uri.encode(query)
        val url = "https://itunes.apple.com/search" +
            "?term=$encoded&media=music&entity=song&country=$countryCode&limit=$limit"
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .build()

        client.newCall(request).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            if (!response.isSuccessful || raw.isBlank()) return emptyList()
            val parsed = json.decodeFromString<ItunesSearchResponseDto>(raw)
            val market = GlobalMusicMarkets.findByCode(countryCode)

            return parsed.results.mapNotNull { dto ->
                val title = dto.trackName?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                val preview = dto.previewUrl?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                val artwork = dto.artworkUrl100?.replace("100x100bb", "400x400bb")
                GlobalMusicTrack(
                    id = "global_${dto.trackId}",
                    trackId = dto.trackId,
                    title = title,
                    artist = dto.artistName?.takeIf { it.isNotBlank() } ?: "Unknown Artist",
                    album = dto.collectionName?.takeIf { it.isNotBlank() } ?: market.name,
                    genre = dto.primaryGenreName?.takeIf { it.isNotBlank() } ?: "Global",
                    durationMs = dto.trackTimeMillis.coerceAtLeast(30_000L),
                    previewUrl = preview,
                    artworkUrl = artwork,
                    countryCode = market.code,
                    countryName = market.name,
                )
            }
        }
    }

    private fun interleaveAndDedupe(lists: List<List<GlobalMusicTrack>>): List<GlobalMusicTrack> {
        val merged = mutableListOf<GlobalMusicTrack>()
        val seenIds = HashSet<Long>()
        val seenTitleArtist = HashSet<String>()
        val maxLen = lists.maxOfOrNull { it.size } ?: 0
        for (i in 0 until maxLen) {
            for (list in lists) {
                val track = list.getOrNull(i) ?: continue
                val key = "${track.title.trim().lowercase()}|${track.artist.trim().lowercase()}"
                if (seenIds.add(track.trackId) && seenTitleArtist.add(key)) {
                    merged.add(track)
                }
            }
        }
        return merged
    }
}
