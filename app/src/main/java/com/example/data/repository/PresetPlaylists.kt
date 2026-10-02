package com.example.data.repository

import com.example.data.model.Channel

data class PlaylistSource(
    val id: String,
    val title: String,
    val url: String,
    val isPreset: Boolean = true
)

object PresetPlaylists {
    val ALL = PlaylistSource(
        id = "preset_all",
        title = "All Playlists",
        url = "all://combined"
    )

    val INDIA = PlaylistSource(
        id = "preset_india",
        title = "India Channels",
        url = "https://iptv-org.github.io/iptv/countries/in.m3u"
    )

    val GLOBAL = PlaylistSource(
        id = "preset_global",
        title = "Global Channels",
        url = "https://iptv-org.github.io/iptv/index.m3u"
    )

    val DEFAULT_PLAYLISTS = listOf(ALL, INDIA, GLOBAL)

    // Curated high-availability live channels for instant preview and offline fallback
    val FALLBACK_INDIA_CHANNELS = listOf(
        Channel(
            id = "in_aajtak",
            name = "Aaj Tak Live HD",
            logoUrl = "https://upload.wikimedia.org/wikipedia/commons/thumb/c/cb/Aaj_tak_logo.png/512px-Aaj_tak_logo.png",
            category = "News",
            language = "Hindi",
            country = "IN",
            streamUrl = "https://feeds.intoday.in/aajtak/api/aajtakhd/master.m3u8"
        ),
        Channel(
            id = "in_9xm",
            name = "9XM Music Live",
            logoUrl = "https://xstreamcp-assets-msp.streamready.in/assets/LIVETV/LIVECHANNEL/LIVETV_LIVETVCHANNEL_9XM/images/LOGO_HD/image.png",
            category = "Music",
            language = "Hindi",
            country = "IN",
            streamUrl = "https://9xjio.wiseplayout.com/9XM/master.m3u8"
        ),
        Channel(
            id = "in_indiatoday",
            name = "India Today News",
            logoUrl = "https://upload.wikimedia.org/wikipedia/commons/thumb/6/69/India_Today_logo.svg/512px-India_Today_logo.svg.png",
            category = "News",
            language = "English",
            country = "IN",
            streamUrl = "https://feeds.intoday.in/indiatoday/api/itlive/master.m3u8"
        ),
        Channel(
            id = "in_cinema_classics",
            name = "Cinema Classics HD",
            logoUrl = "https://upload.wikimedia.org/wikipedia/commons/thumb/5/52/Free_Cinema_Logo.png/512px-Free_Cinema_Logo.png",
            category = "Movies",
            language = "Hindi",
            country = "IN",
            streamUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4"
        ),
        Channel(
            id = "in_sports_arena",
            name = "Sports Arena Live HD",
            logoUrl = "https://upload.wikimedia.org/wikipedia/commons/thumb/3/36/Stadium_icon.svg/512px-Stadium_icon.svg.png",
            category = "Sports",
            language = "English",
            country = "IN",
            streamUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ElephantsDream.mp4"
        )
    )

    val FALLBACK_GLOBAL_CHANNELS = listOf(
        Channel(
            id = "global_bloomberg",
            name = "Bloomberg Television",
            logoUrl = "https://upload.wikimedia.org/wikipedia/commons/thumb/4/4e/Bloomberg_Television_logo.svg/512px-Bloomberg_Television_logo.svg.png",
            category = "News",
            language = "English",
            country = "US",
            streamUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/TearsOfSteel.mp4"
        ),
        Channel(
            id = "global_redbull",
            name = "Red Bull TV",
            logoUrl = "https://upload.wikimedia.org/wikipedia/commons/thumb/9/91/Red_Bull_TV_logo.svg/512px-Red_Bull_TV_logo.svg.png",
            category = "Sports",
            language = "English",
            country = "AT",
            streamUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerBlazes.mp4"
        )
    )
}
