package com.anuj.obsidian.data.remote

import com.anuj.obsidian.data.model.SearchResultItem
import com.anuj.obsidian.data.model.TrendingChart

object TrendingChartsProvider {

    val featuredThisWeek = listOf(
        SearchResultItem(
            id = "feat_week_1",
            title = "Espresso",
            artist = "Sabrina Carpenter",
            album = "Short n' Sweet",
            durationSeconds = 175,
            coverUrl = "https://images.unsplash.com/photo-1514525253161-7a46d19cd819?w=600&auto=format&fit=crop&q=80",
            bitDepth = "24-bit",
            sampleRate = "96.0 kHz",
            provider = "Qobuz Studio Master",
            isrc = "USUM72403305",
            releaseYear = "2024",
            isExplicit = false
        ),
        SearchResultItem(
            id = "feat_week_2",
            title = "Birds of a Feather",
            artist = "Billie Eilish",
            album = "HIT ME HARD AND SOFT",
            durationSeconds = 196,
            coverUrl = "https://images.unsplash.com/photo-1470225620780-dba8ba36b745?w=600&auto=format&fit=crop&q=80",
            bitDepth = "24-bit",
            sampleRate = "96.0 kHz",
            provider = "Qobuz Studio Master",
            isrc = "USUM72404122",
            releaseYear = "2024",
            isExplicit = false
        ),
        SearchResultItem(
            id = "feat_week_3",
            title = "Blinding Lights",
            artist = "The Weeknd",
            album = "After Hours",
            durationSeconds = 200,
            coverUrl = "https://images.unsplash.com/photo-1614613535308-eb5fbd3d2c17?w=600&auto=format&fit=crop&q=80",
            bitDepth = "24-bit",
            sampleRate = "192.0 kHz",
            provider = "Tidal HiFi Lossless",
            isrc = "USUM71922336",
            releaseYear = "2020",
            isExplicit = false
        ),
        SearchResultItem(
            id = "feat_week_4",
            title = "Good Luck, Babe!",
            artist = "Chappell Roan",
            album = "Good Luck, Babe! - Single",
            durationSeconds = 218,
            coverUrl = "https://images.unsplash.com/photo-1501386761578-eac5c94b800a?w=600&auto=format&fit=crop&q=80",
            bitDepth = "24-bit",
            sampleRate = "96.0 kHz",
            provider = "Qobuz Studio Master",
            isrc = "USUG12401662",
            releaseYear = "2024",
            isExplicit = false
        ),
        SearchResultItem(
            id = "feat_week_5",
            title = "Get Lucky",
            artist = "Daft Punk ft. Pharrell",
            album = "Random Access Memories",
            durationSeconds = 369,
            coverUrl = "https://images.unsplash.com/photo-1487180144351-b8472da7d491?w=600&auto=format&fit=crop&q=80",
            bitDepth = "24-bit",
            sampleRate = "88.2 kHz",
            provider = "Qobuz Studio Master",
            isrc = "USQX91300105",
            releaseYear = "2013",
            isExplicit = false
        )
    )

    val stayIndie = listOf(
        SearchResultItem(
            id = "indie_1",
            title = "Motion Sickness",
            artist = "Phoebe Bridgers",
            album = "Stranger in the Alps",
            durationSeconds = 229,
            coverUrl = "https://images.unsplash.com/photo-1518609878373-06d740f60d8b?w=600&auto=format&fit=crop&q=80",
            bitDepth = "24-bit",
            sampleRate = "96.0 kHz",
            provider = "Tidal HiFi Lossless",
            isrc = "US38Y1725002",
            releaseYear = "2017",
            isExplicit = false
        ),
        SearchResultItem(
            id = "indie_2",
            title = "Not Strong Enough",
            artist = "boygenius",
            album = "the record",
            durationSeconds = 234,
            coverUrl = "https://images.unsplash.com/photo-1465847899084-d164df4dedc6?w=600&auto=format&fit=crop&q=80",
            bitDepth = "24-bit",
            sampleRate = "96.0 kHz",
            provider = "Qobuz Studio Master",
            isrc = "USUG12300438",
            releaseYear = "2023",
            isExplicit = false
        ),
        SearchResultItem(
            id = "indie_3",
            title = "Space Song",
            artist = "Beach House",
            album = "Depression Cherry",
            durationSeconds = 320,
            coverUrl = "https://images.unsplash.com/photo-1493225457124-a3eb161ffa5f?w=600&auto=format&fit=crop&q=80",
            bitDepth = "24-bit",
            sampleRate = "96.0 kHz",
            provider = "Apple Lossless Master",
            isrc = "USSUB1501104",
            releaseYear = "2015",
            isExplicit = false
        ),
        SearchResultItem(
            id = "indie_4",
            title = "The Less I Know The Better",
            artist = "Tame Impala",
            album = "Currents",
            durationSeconds = 216,
            coverUrl = "https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4?w=600&auto=format&fit=crop&q=80",
            bitDepth = "24-bit",
            sampleRate = "96.0 kHz",
            provider = "Qobuz Studio Master",
            isrc = "AUUM71500303",
            releaseYear = "2015",
            isExplicit = false
        ),
        SearchResultItem(
            id = "indie_5",
            title = "Chamber of Reflection",
            artist = "Mac DeMarco",
            album = "Salad Days",
            durationSeconds = 231,
            coverUrl = "https://images.unsplash.com/photo-1508700115892-45ecd05ae2ad?w=600&auto=format&fit=crop&q=80",
            bitDepth = "24-bit",
            sampleRate = "96.0 kHz",
            provider = "Tidal HiFi Lossless",
            isrc = "US2S71415008",
            releaseYear = "2014",
            isExplicit = false
        )
    )

    val globalTopHits = listOf(
        SearchResultItem(
            id = "trend_global_1",
            title = "Espresso",
            artist = "Sabrina Carpenter",
            album = "Short n' Sweet",
            durationSeconds = 175,
            coverUrl = "https://images.unsplash.com/photo-1514525253161-7a46d19cd819?w=600&auto=format&fit=crop&q=80",
            bitDepth = "24-bit",
            sampleRate = "96.0 kHz",
            provider = "Qobuz Studio Master",
            isrc = "USUM72403305",
            releaseYear = "2024",
            isExplicit = false
        ),
        SearchResultItem(
            id = "trend_global_2",
            title = "Birds of a Feather",
            artist = "Billie Eilish",
            album = "HIT ME HARD AND SOFT",
            durationSeconds = 196,
            coverUrl = "https://images.unsplash.com/photo-1470225620780-dba8ba36b745?w=600&auto=format&fit=crop&q=80",
            bitDepth = "24-bit",
            sampleRate = "96.0 kHz",
            provider = "Qobuz Studio Master",
            isrc = "USUM72404122",
            releaseYear = "2024",
            isExplicit = false
        ),
        SearchResultItem(
            id = "trend_global_3",
            title = "Blinding Lights",
            artist = "The Weeknd",
            album = "After Hours",
            durationSeconds = 200,
            coverUrl = "https://images.unsplash.com/photo-1614613535308-eb5fbd3d2c17?w=600&auto=format&fit=crop&q=80",
            bitDepth = "24-bit",
            sampleRate = "192.0 kHz",
            provider = "Tidal HiFi Lossless",
            isrc = "USUM71922336",
            releaseYear = "2020",
            isExplicit = false
        ),
        SearchResultItem(
            id = "trend_global_4",
            title = "I Had Some Help",
            artist = "Post Malone ft. Morgan Wallen",
            album = "F-1 Trillion",
            durationSeconds = 178,
            coverUrl = "https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4?w=600&auto=format&fit=crop&q=80",
            bitDepth = "24-bit",
            sampleRate = "96.0 kHz",
            provider = "Apple Lossless Master",
            isrc = "USUM72403681",
            releaseYear = "2024",
            isExplicit = false
        ),
        SearchResultItem(
            id = "trend_global_5",
            title = "Good Luck, Babe!",
            artist = "Chappell Roan",
            album = "Good Luck, Babe! - Single",
            durationSeconds = 218,
            coverUrl = "https://images.unsplash.com/photo-1501386761578-eac5c94b800a?w=600&auto=format&fit=crop&q=80",
            bitDepth = "24-bit",
            sampleRate = "96.0 kHz",
            provider = "Qobuz Studio Master",
            isrc = "USUG12401662",
            releaseYear = "2024",
            isExplicit = false
        ),
        SearchResultItem(
            id = "trend_global_6",
            title = "Cruel Summer",
            artist = "Taylor Swift",
            album = "Lover",
            durationSeconds = 178,
            coverUrl = "https://images.unsplash.com/photo-1518609878373-06d740f60d8b?w=600&auto=format&fit=crop&q=80",
            bitDepth = "24-bit",
            sampleRate = "96.0 kHz",
            provider = "Apple Lossless Master",
            isrc = "USUG11901472",
            releaseYear = "2019",
            isExplicit = false
        ),
        SearchResultItem(
            id = "trend_global_7",
            title = "Not Like Us",
            artist = "Kendrick Lamar",
            album = "Not Like Us - Single",
            durationSeconds = 274,
            coverUrl = "https://images.unsplash.com/photo-1493225457124-a3eb161ffa5f?w=600&auto=format&fit=crop&q=80",
            bitDepth = "24-bit",
            sampleRate = "96.0 kHz",
            provider = "Qobuz Studio Master",
            isrc = "USUM72404768",
            releaseYear = "2024",
            isExplicit = true
        ),
        SearchResultItem(
            id = "trend_global_8",
            title = "Too Sweet",
            artist = "Hozier",
            album = "Unheard - EP",
            durationSeconds = 251,
            coverUrl = "https://images.unsplash.com/photo-1465847899084-d164df4dedc6?w=600&auto=format&fit=crop&q=80",
            bitDepth = "24-bit",
            sampleRate = "96.0 kHz",
            provider = "Tidal HiFi Lossless",
            isrc = "USSM12401019",
            releaseYear = "2024",
            isExplicit = false
        ),
        SearchResultItem(
            id = "trend_global_9",
            title = "Beautiful Things",
            artist = "Benson Boone",
            album = "Fireworks & Rollerblades",
            durationSeconds = 180,
            coverUrl = "https://images.unsplash.com/photo-1487180144351-b8472da7d491?w=600&auto=format&fit=crop&q=80",
            bitDepth = "24-bit",
            sampleRate = "96.0 kHz",
            provider = "Qobuz Studio Master",
            isrc = "USWB12307022",
            releaseYear = "2024",
            isExplicit = false
        ),
        SearchResultItem(
            id = "trend_global_10",
            title = "Lose Yourself",
            artist = "Eminem",
            album = "8 Mile Soundtrack",
            durationSeconds = 326,
            coverUrl = "https://images.unsplash.com/photo-1508700115892-45ecd05ae2ad?w=600&auto=format&fit=crop&q=80",
            bitDepth = "24-bit",
            sampleRate = "96.0 kHz",
            provider = "Qobuz Studio Master",
            isrc = "USIR10211559",
            releaseYear = "2002",
            isExplicit = true
        )
    )

    val audiophileHiResMasters = listOf(
        SearchResultItem(
            id = "trend_audio_1",
            title = "Hotel California",
            artist = "Eagles",
            album = "Hotel California (2013 Remaster)",
            durationSeconds = 391,
            coverUrl = "https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4?w=600&auto=format&fit=crop&q=80",
            bitDepth = "24-bit",
            sampleRate = "192.0 kHz",
            provider = "Qobuz Studio Master",
            isrc = "USEE10101736",
            releaseYear = "1976",
            isExplicit = false
        ),
        SearchResultItem(
            id = "trend_audio_2",
            title = "Time",
            artist = "Pink Floyd",
            album = "The Dark Side of the Moon (50th Ann.)",
            durationSeconds = 413,
            coverUrl = "https://images.unsplash.com/photo-1614613535308-eb5fbd3d2c17?w=600&auto=format&fit=crop&q=80",
            bitDepth = "24-bit",
            sampleRate = "96.0 kHz",
            provider = "Tidal MQA / HiFi",
            isrc = "GBARL9300135",
            releaseYear = "1973",
            isExplicit = false
        ),
        SearchResultItem(
            id = "trend_audio_3",
            title = "Bohemian Rhapsody",
            artist = "Queen",
            album = "A Night at the Opera (Remastered)",
            durationSeconds = 354,
            coverUrl = "https://images.unsplash.com/photo-1514525253161-7a46d19cd819?w=600&auto=format&fit=crop&q=80",
            bitDepth = "24-bit",
            sampleRate = "96.0 kHz",
            provider = "Qobuz Studio Master",
            isrc = "GBCEE0400001",
            releaseYear = "1975",
            isExplicit = false
        ),
        SearchResultItem(
            id = "trend_audio_4",
            title = "Get Lucky",
            artist = "Daft Punk ft. Pharrell Williams",
            album = "Random Access Memories (10th Ann.)",
            durationSeconds = 369,
            coverUrl = "https://images.unsplash.com/photo-1470225620780-dba8ba36b745?w=600&auto=format&fit=crop&q=80",
            bitDepth = "24-bit",
            sampleRate = "88.2 kHz",
            provider = "Qobuz Studio Master",
            isrc = "USQX91300105",
            releaseYear = "2013",
            isExplicit = false
        ),
        SearchResultItem(
            id = "trend_audio_5",
            title = "Dreams",
            artist = "Fleetwood Mac",
            album = "Rumours (Super Deluxe Edition)",
            durationSeconds = 257,
            coverUrl = "https://images.unsplash.com/photo-1487180144351-b8472da7d491?w=600&auto=format&fit=crop&q=80",
            bitDepth = "24-bit",
            sampleRate = "96.0 kHz",
            provider = "Tidal HiFi Lossless",
            isrc = "USWB10100412",
            releaseYear = "1977",
            isExplicit = false
        ),
        SearchResultItem(
            id = "trend_audio_6",
            title = "Time (Inception OST)",
            artist = "Hans Zimmer",
            album = "Inception (Original Soundtrack)",
            durationSeconds = 275,
            coverUrl = "https://images.unsplash.com/photo-1508700115892-45ecd05ae2ad?w=600&auto=format&fit=crop&q=80",
            bitDepth = "24-bit",
            sampleRate = "96.0 kHz",
            provider = "Apple Lossless Master",
            isrc = "USWB11002237",
            releaseYear = "2010",
            isExplicit = false
        ),
        SearchResultItem(
            id = "trend_audio_7",
            title = "Billie Jean",
            artist = "Michael Jackson",
            album = "Thriller (25th Anniversary)",
            durationSeconds = 294,
            coverUrl = "https://images.unsplash.com/photo-1493225457124-a3eb161ffa5f?w=600&auto=format&fit=crop&q=80",
            bitDepth = "24-bit",
            sampleRate = "96.0 kHz",
            provider = "Qobuz Studio Master",
            isrc = "USSM18200171",
            releaseYear = "1982",
            isExplicit = false
        )
    )

    val hipHopRapHits = listOf(
        SearchResultItem(
            id = "trend_hiphop_1",
            title = "Starboy",
            artist = "The Weeknd ft. Daft Punk",
            album = "Starboy",
            durationSeconds = 230,
            coverUrl = "https://images.unsplash.com/photo-1508700115892-45ecd05ae2ad?w=600&auto=format&fit=crop&q=80",
            bitDepth = "24-bit",
            sampleRate = "96.0 kHz",
            provider = "Tidal HiFi Lossless",
            isrc = "USUM71607312",
            releaseYear = "2016",
            isExplicit = true
        ),
        SearchResultItem(
            id = "trend_hiphop_2",
            title = "FE!N",
            artist = "Travis Scott ft. Playboi Carti",
            album = "UTOPIA",
            durationSeconds = 191,
            coverUrl = "https://images.unsplash.com/photo-1514525253161-7a46d19cd819?w=600&auto=format&fit=crop&q=80",
            bitDepth = "24-bit",
            sampleRate = "96.0 kHz",
            provider = "Qobuz Studio Master",
            isrc = "USSM12305844",
            releaseYear = "2023",
            isExplicit = true
        ),
        SearchResultItem(
            id = "trend_hiphop_3",
            title = "God's Plan",
            artist = "Drake",
            album = "Scorpion",
            durationSeconds = 198,
            coverUrl = "https://images.unsplash.com/photo-1614613535308-eb5fbd3d2c17?w=600&auto=format&fit=crop&q=80",
            bitDepth = "24-bit",
            sampleRate = "96.0 kHz",
            provider = "Apple Lossless Master",
            isrc = "USCM51800054",
            releaseYear = "2018",
            isExplicit = true
        ),
        SearchResultItem(
            id = "trend_hiphop_4",
            title = "redrum",
            artist = "21 Savage",
            album = "american dream",
            durationSeconds = 270,
            coverUrl = "https://images.unsplash.com/photo-1493225457124-a3eb161ffa5f?w=600&auto=format&fit=crop&q=80",
            bitDepth = "24-bit",
            sampleRate = "96.0 kHz",
            provider = "Qobuz Studio Master",
            isrc = "USSM12400045",
            releaseYear = "2024",
            isExplicit = true
        ),
        SearchResultItem(
            id = "trend_hiphop_5",
            title = "No Role Modelz",
            artist = "J. Cole",
            album = "2014 Forest Hills Drive",
            durationSeconds = 292,
            coverUrl = "https://images.unsplash.com/photo-1470225620780-dba8ba36b745?w=600&auto=format&fit=crop&q=80",
            bitDepth = "24-bit",
            sampleRate = "96.0 kHz",
            provider = "Tidal HiFi Lossless",
            isrc = "USSM11406880",
            releaseYear = "2014",
            isExplicit = true
        )
    )

    val edmBassMaster = listOf(
        SearchResultItem(
            id = "trend_edm_1",
            title = "Strobe",
            artist = "deadmau5",
            album = "For Lack of a Better Name",
            durationSeconds = 637,
            coverUrl = "https://images.unsplash.com/photo-1518609878373-06d740f60d8b?w=600&auto=format&fit=crop&q=80",
            bitDepth = "24-bit",
            sampleRate = "96.0 kHz",
            provider = "Qobuz Studio Master",
            isrc = "CAUM70900012",
            releaseYear = "2009",
            isExplicit = false
        ),
        SearchResultItem(
            id = "trend_edm_2",
            title = "Midnight City",
            artist = "M83",
            album = "Hurry Up, We're Dreaming",
            durationSeconds = 243,
            coverUrl = "https://images.unsplash.com/photo-1501386761578-eac5c94b800a?w=600&auto=format&fit=crop&q=80",
            bitDepth = "24-bit",
            sampleRate = "96.0 kHz",
            provider = "Qobuz Studio Master",
            isrc = "FR9W11100340",
            releaseYear = "2011",
            isExplicit = false
        ),
        SearchResultItem(
            id = "trend_edm_3",
            title = "STAY",
            artist = "The Kid LAROI & Justin Bieber",
            album = "F*CK LOVE 3+: OVER YOU",
            durationSeconds = 141,
            coverUrl = "https://images.unsplash.com/photo-1465847899084-d164df4dedc6?w=600&auto=format&fit=crop&q=80",
            bitDepth = "24-bit",
            sampleRate = "96.0 kHz",
            provider = "Apple Lossless Master",
            isrc = "USSM12104276",
            releaseYear = "2021",
            isExplicit = true
        ),
        SearchResultItem(
            id = "trend_edm_4",
            title = "Wake Me Up",
            artist = "Avicii",
            album = "True",
            durationSeconds = 247,
            coverUrl = "https://images.unsplash.com/photo-1487180144351-b8472da7d491?w=600&auto=format&fit=crop&q=80",
            bitDepth = "24-bit",
            sampleRate = "96.0 kHz",
            provider = "Qobuz Studio Master",
            isrc = "SEUM71300060",
            releaseYear = "2013",
            isExplicit = false
        ),
        SearchResultItem(
            id = "trend_edm_5",
            title = "Animals",
            artist = "Martin Garrix",
            album = "Gold Skies - EP",
            durationSeconds = 304,
            coverUrl = "https://images.unsplash.com/photo-1614613535308-eb5fbd3d2c17?w=600&auto=format&fit=crop&q=80",
            bitDepth = "24-bit",
            sampleRate = "96.0 kHz",
            provider = "Tidal HiFi Lossless",
            isrc = "NLE341300010",
            releaseYear = "2013",
            isExplicit = false
        )
    )

    val desiBollywoodMaster = listOf(
        SearchResultItem(
            id = "trend_desi_1",
            title = "Khaab",
            artist = "Akhil",
            album = "Khaab - Single",
            durationSeconds = 202,
            coverUrl = "https://images.unsplash.com/photo-1514525253161-7a46d19cd819?w=600&auto=format&fit=crop&q=80",
            bitDepth = "24-bit",
            sampleRate = "96.0 kHz",
            provider = "JioSaavn Studio Master",
            isrc = "INC101600101",
            releaseYear = "2016",
            isExplicit = false
        ),
        SearchResultItem(
            id = "trend_desi_2",
            title = "Husn",
            artist = "Anuv Jain",
            album = "Husn - Single",
            durationSeconds = 219,
            coverUrl = "https://images.unsplash.com/photo-1470225620780-dba8ba36b745?w=600&auto=format&fit=crop&q=80",
            bitDepth = "24-bit",
            sampleRate = "96.0 kHz",
            provider = "Qobuz Studio Master",
            isrc = "INM202300021",
            releaseYear = "2023",
            isExplicit = false
        ),
        SearchResultItem(
            id = "trend_desi_3",
            title = "Tu Hai Kahan",
            artist = "AUR",
            album = "Tu Hai Kahan - Single",
            durationSeconds = 263,
            coverUrl = "https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4?w=600&auto=format&fit=crop&q=80",
            bitDepth = "24-bit",
            sampleRate = "96.0 kHz",
            provider = "Tidal HiFi Lossless",
            isrc = "PKUM12300014",
            releaseYear = "2023",
            isExplicit = false
        ),
        SearchResultItem(
            id = "trend_desi_4",
            title = "O Maahi",
            artist = "Arijit Singh, Pritam",
            album = "Dunki",
            durationSeconds = 233,
            coverUrl = "https://images.unsplash.com/photo-1508700115892-45ecd05ae2ad?w=600&auto=format&fit=crop&q=80",
            bitDepth = "24-bit",
            sampleRate = "96.0 kHz",
            provider = "JioSaavn 320kbps Master",
            isrc = "INT102301824",
            releaseYear = "2023",
            isExplicit = false
        ),
        SearchResultItem(
            id = "trend_desi_5",
            title = "Pehle Bhi Main",
            artist = "Vishal Mishra, Raj Shekhar",
            album = "Animal",
            durationSeconds = 250,
            coverUrl = "https://images.unsplash.com/photo-1493225457124-a3eb161ffa5f?w=600&auto=format&fit=crop&q=80",
            bitDepth = "24-bit",
            sampleRate = "96.0 kHz",
            provider = "JioSaavn Studio Master",
            isrc = "INT102301683",
            releaseYear = "2023",
            isExplicit = false
        ),
        SearchResultItem(
            id = "trend_desi_6",
            title = "Chaleya",
            artist = "Anirudh Ravichander, Arijit Singh",
            album = "Jawan",
            durationSeconds = 200,
            coverUrl = "https://images.unsplash.com/photo-1465847899084-d164df4dedc6?w=600&auto=format&fit=crop&q=80",
            bitDepth = "24-bit",
            sampleRate = "96.0 kHz",
            provider = "Apple Lossless Master",
            isrc = "INT102300892",
            releaseYear = "2023",
            isExplicit = false
        )
    )


    val allCharts: List<TrendingChart> = listOf(
        TrendingChart(
            id = "chart_global",
            title = "Global Top Hits",
            subtitle = "Top Trending Worldwide Charts",
            iconEmoji = "🔥",
            badgeColorHex = 0xFF1DB954,
            tracks = globalTopHits
        ),
        TrendingChart(
            id = "chart_audiophile",
            title = "Hi-Res Studio Masters",
            subtitle = "24-Bit / 192kHz Audiophile Classics",
            iconEmoji = "💎",
            badgeColorHex = 0xFFF5A623,
            tracks = audiophileHiResMasters
        ),
        TrendingChart(
            id = "chart_hiphop",
            title = "Hot Hip-Hop & Rap",
            subtitle = "Top Charting Beats & Heavy Bass",
            iconEmoji = "🎧",
            badgeColorHex = 0xFFE91E63,
            tracks = hipHopRapHits
        ),
        TrendingChart(
            id = "chart_edm",
            title = "Bass & Electronic",
            subtitle = "High Dynamic Range EDM & Synth",
            iconEmoji = "⚡",
            badgeColorHex = 0xFF00E5FF,
            tracks = edmBassMaster
        ),
        TrendingChart(
            id = "chart_desi",
            title = "Desi & Bollywood Hits",
            subtitle = "Trending Indian Lossless Masters",
            iconEmoji = "🇮🇳",
            badgeColorHex = 0xFFFF9800,
            tracks = desiBollywoodMaster
        )
    )

    fun getDefaultTrendingTracks(): List<SearchResultItem> = globalTopHits
}
