package com.example.data.model

object MusicCatalog {

    fun defaultOnlineCatalog(): List<TrackEntity> = listOf(
        // Downloadable & Streamable Public Tracks (SoundHelix high-bitrate MP3s)
        TrackEntity(
            id = "online_sh_1",
            title = "Midnight Signal",
            artist = "SoundHelix Collective",
            album = "Nocturne Sessions",
            genre = "Electronic",
            durationMs = 372_000L,
            sourceType = TrackSourceType.ONLINE,
            streamUrl = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-1.mp3",
            artworkResKey = "electronic",
            isLiveStream = false,
            addedAt = 1000L
        ),
        TrackEntity(
            id = "online_sh_2",
            title = "Amber Skyline",
            artist = "T. Schürger",
            album = "Horizon Drift",
            genre = "Lo-Fi",
            durationMs = 425_000L,
            sourceType = TrackSourceType.ONLINE,
            streamUrl = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-2.mp3",
            artworkResKey = "lofi",
            isLiveStream = false,
            addedAt = 2000L
        ),
        TrackEntity(
            id = "online_sh_3",
            title = "Velvet Frequency",
            artist = "SoundHelix Lab",
            album = "Warm Analog",
            genre = "Ambient",
            durationMs = 344_000L,
            sourceType = TrackSourceType.ONLINE,
            streamUrl = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-3.mp3",
            artworkResKey = "studio",
            isLiveStream = false,
            addedAt = 3000L
        ),
        TrackEntity(
            id = "online_sh_4",
            title = "Golden Hour Strings",
            artist = "Acoustic Ensemble",
            album = "Wood & Copper",
            genre = "Acoustic",
            durationMs = 302_000L,
            sourceType = TrackSourceType.ONLINE,
            streamUrl = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-4.mp3",
            artworkResKey = "acoustic",
            isLiveStream = false,
            addedAt = 4000L
        ),
        TrackEntity(
            id = "online_sh_6",
            title = "Neon Rainstorm",
            artist = "Pulse Architecture",
            album = "Glass Prism",
            genre = "Electronic",
            durationMs = 281_000L,
            sourceType = TrackSourceType.ONLINE,
            streamUrl = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-6.mp3",
            artworkResKey = "electronic",
            isLiveStream = false,
            addedAt = 5000L
        ),
        TrackEntity(
            id = "online_sh_8",
            title = "Rooftop Polaroid",
            artist = "Late Night Tapes",
            album = "City Lights Vol. 2",
            genre = "Lo-Fi",
            durationMs = 325_000L,
            sourceType = TrackSourceType.ONLINE,
            streamUrl = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-8.mp3",
            artworkResKey = "lofi",
            isLiveStream = false,
            addedAt = 6000L
        ),
        TrackEntity(
            id = "online_sh_10",
            title = "Timber & Dawn",
            artist = "Clara Vance",
            album = "Studio B Unplugged",
            genre = "Acoustic",
            durationMs = 368_000L,
            sourceType = TrackSourceType.ONLINE,
            streamUrl = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-10.mp3",
            artworkResKey = "acoustic",
            isLiveStream = false,
            addedAt = 7000L
        ),
        TrackEntity(
            id = "online_sh_13",
            title = "Orbital Resonance",
            artist = "Kinetic Waves",
            album = "Deep Telemetry",
            genre = "Ambient",
            durationMs = 319_000L,
            sourceType = TrackSourceType.ONLINE,
            streamUrl = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-13.mp3",
            artworkResKey = "studio",
            isLiveStream = false,
            addedAt = 8000L
        ),

        // Live Online Internet Radio Stations (SomaFM HTTPS MP3 streams)
        TrackEntity(
            id = "radio_somafm_groove",
            title = "Groove Salad",
            artist = "SomaFM Live Radio",
            album = "Ambient / Downtempo Beats",
            genre = "Radio",
            durationMs = 0L,
            sourceType = TrackSourceType.ONLINE,
            streamUrl = "https://ice1.somafm.com/groovesalad-128-mp3",
            artworkResKey = "lofi",
            isLiveStream = true,
            addedAt = 9000L
        ),
        TrackEntity(
            id = "radio_somafm_drone",
            title = "Drone Zone",
            artist = "SomaFM Live Radio",
            album = "Atmospheric Space Textures",
            genre = "Radio",
            durationMs = 0L,
            sourceType = TrackSourceType.ONLINE,
            streamUrl = "https://ice1.somafm.com/dronezone-128-mp3",
            artworkResKey = "studio",
            isLiveStream = true,
            addedAt = 10000L
        ),
        TrackEntity(
            id = "radio_somafm_indie",
            title = "Indie Pop Rocks!",
            artist = "SomaFM Live Radio",
            album = "Independent Alternative",
            genre = "Radio",
            durationMs = 0L,
            sourceType = TrackSourceType.ONLINE,
            streamUrl = "https://ice1.somafm.com/indiepop-128-mp3",
            artworkResKey = "acoustic",
            isLiveStream = true,
            addedAt = 11000L
        ),
        TrackEntity(
            id = "radio_somafm_spacestation",
            title = "Space Station Soma",
            artist = "SomaFM Live Radio",
            album = "Spaced-Out Mid-Tempo Electronica",
            genre = "Radio",
            durationMs = 0L,
            sourceType = TrackSourceType.ONLINE,
            streamUrl = "https://ice1.somafm.com/spacestation-128-mp3",
            artworkResKey = "electronic",
            isLiveStream = true,
            addedAt = 12000L
        )
    )
}
