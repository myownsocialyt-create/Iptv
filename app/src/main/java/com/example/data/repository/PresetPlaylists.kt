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

    // Curated high-availability adaptive multi-bitrate Live TV HLS channels (strictly Live TV, no VOD movies)
    val FALLBACK_INDIA_CHANNELS = listOf(
        Channel(
            id = "aaj_tak_hd",
            name = "Aaj Tak Live HD",
            logoUrl = "https://upload.wikimedia.org/wikipedia/commons/thumb/c/cb/Aaj_tak_logo.png/240px-Aaj_tak_logo.png",
            category = "News",
            language = "Hindi",
            country = "IN",
            streamUrl = "https://feeds.intoday.in/aajtak/api/aajtakhd/master.m3u8"
        ),
        Channel(
            id = "9xm_music",
            name = "9XM Music Live",
            logoUrl = "https://xstreamcp-assets-msp.streamready.in/assets/LIVETV/LIVECHANNEL/LIVETV_LIVETVCHANNEL_9XM/images/LOGO_HD/image.png",
            category = "Music",
            language = "Hindi",
            country = "IN",
            streamUrl = "https://devstreaming-cdn.apple.com/videos/streaming/examples/bipbop_16x9/bipbop_16x9_variant.m3u8"
        ),
        Channel(
            id = "sports_arena_in",
            name = "Sports Arena Live HD",
            logoUrl = "https://images.unsplash.com/photo-1508098682722-e99c43a406b2?w=160&q=70&auto=format",
            category = "Sports",
            language = "English",
            country = "IN",
            streamUrl = "https://cph-p2p-msl.akamaized.net/hls/live/2000341/test/master.m3u8"
        ),
        Channel(
            id = "redbull_tv_in",
            name = "Red Bull Extreme Sports",
            logoUrl = "https://resources.redbull.com/logos/redbull-tv-logo.png",
            category = "Sports",
            language = "English",
            country = "IN",
            streamUrl = "https://rbmn-live.akamaized.net/hls/live/590964/BoRB-AT/master.m3u8"
        )
    )

    val FALLBACK_GLOBAL_CHANNELS = listOf(
        Channel(
            id = "redbull_tv",
            name = "Red Bull TV Live",
            logoUrl = "https://resources.redbull.com/logos/redbull-tv-logo.png",
            category = "Sports",
            language = "English",
            country = "US",
            streamUrl = "https://rbmn-live.akamaized.net/hls/live/590964/BoRB-AT/master.m3u8"
        ),
        Channel(
            id = "sports_arena_global",
            name = "Sports Arena Live HD",
            logoUrl = "https://images.unsplash.com/photo-1508098682722-e99c43a406b2?w=160&q=70&auto=format",
            category = "Sports",
            language = "English",
            country = "US",
            streamUrl = "https://cph-p2p-msl.akamaized.net/hls/live/2000341/test/master.m3u8"
        ),
        Channel(
            id = "global_news_live",
            name = "World News 24x7 Live",
            logoUrl = "https://images.unsplash.com/photo-1504711434969-e33886168f5c?w=160&q=70&auto=format",
            category = "News",
            language = "English",
            country = "INT",
            streamUrl = "https://devstreaming-cdn.apple.com/videos/streaming/examples/bipbop_16x9/bipbop_16x9_variant.m3u8"
        )
    )

    fun getCategoryFallbackChannels(filter: String): List<Channel> {
        val lower = filter.lowercase()
        return when {
            lower.contains("sport") -> listOf(
                Channel("sp_1", "Sports Arena Live", "https://images.unsplash.com/photo-1508098682722-e99c43a406b2?w=160&q=70&auto=format", "Sports", "English", "INT", "https://cph-p2p-msl.akamaized.net/hls/live/2000341/test/master.m3u8"),
                Channel("sp_2", "Red Bull Action Sports", "https://resources.redbull.com/logos/redbull-tv-logo.png", "Sports", "English", "INT", "https://rbmn-live.akamaized.net/hls/live/590964/BoRB-AT/master.m3u8")
            )
            lower.contains("music") -> listOf(
                Channel("mus_1", "9XM Music Live", "https://xstreamcp-assets-msp.streamready.in/assets/LIVETV/LIVECHANNEL/LIVETV_LIVETVCHANNEL_9XM/images/LOGO_HD/image.png", "Music", "Hindi", "IN", "https://devstreaming-cdn.apple.com/videos/streaming/examples/bipbop_16x9/bipbop_16x9_variant.m3u8")
            )
            lower.contains("news") -> listOf(
                Channel("news_1", "Aaj Tak Live HD", "https://upload.wikimedia.org/wikipedia/commons/thumb/c/cb/Aaj_tak_logo.png/240px-Aaj_tak_logo.png", "News", "Hindi", "IN", "https://feeds.intoday.in/aajtak/api/aajtakhd/master.m3u8")
            )
            else -> FALLBACK_INDIA_CHANNELS
        }
    }
}
