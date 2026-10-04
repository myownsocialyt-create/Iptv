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
            id = "test_movie_stream",
            name = "Cinema Classics HD",
            logoUrl = "https://images.unsplash.com/photo-1489599849927-2ee91cede3ba?w=200",
            category = "Movies",
            language = "English",
            country = "IN",
            streamUrl = "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8"
        ),
        Channel(
            id = "sports_arena_in",
            name = "Sports Arena Live HD",
            logoUrl = "https://images.unsplash.com/photo-1508098682722-e99c43a406b2?w=200",
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
        ),
        Channel(
            id = "aaj_tak_hd",
            name = "Aaj Tak Live HD",
            logoUrl = "https://upload.wikimedia.org/wikipedia/commons/thumb/c/cb/Aaj_tak_logo.png/512px-Aaj_tak_logo.png",
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
            streamUrl = "https://9xjio.wiseplayout.com/9XM/master.m3u8"
        ),
        Channel(
            id = "bunny_retro_in",
            name = "Big Buck Bunny HD",
            logoUrl = "https://images.unsplash.com/photo-1579783902614-a3fb3927b675?w=200",
            category = "Entertainment",
            language = "English",
            country = "IN",
            streamUrl = "https://test-streams.mux.dev/test_001/stream.m3u8"
        )
    )

    val FALLBACK_GLOBAL_CHANNELS = listOf(
        Channel(
            id = "sports_arena_global",
            name = "Sports Arena Live HD",
            logoUrl = "https://images.unsplash.com/photo-1508098682722-e99c43a406b2?w=200",
            category = "Sports",
            language = "English",
            country = "US",
            streamUrl = "https://cph-p2p-msl.akamaized.net/hls/live/2000341/test/master.m3u8"
        ),
        Channel(
            id = "rakuten_movies",
            name = "Cinema World HD",
            logoUrl = "https://images.unsplash.com/photo-1536440136628-849c177e76a1?w=200",
            category = "Movies",
            language = "English",
            country = "INT",
            streamUrl = "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8"
        ),
        Channel(
            id = "redbull_tv",
            name = "Red Bull Extreme Sports",
            logoUrl = "https://resources.redbull.com/logos/redbull-tv-logo.png",
            category = "Sports",
            language = "English",
            country = "US",
            streamUrl = "https://rbmn-live.akamaized.net/hls/live/590964/BoRB-AT/master.m3u8"
        ),
        Channel(
            id = "sintel_live",
            name = "Sintel HD Movie",
            logoUrl = "https://images.unsplash.com/photo-1489599849927-2ee91cede3ba?w=200",
            category = "Movies",
            language = "English",
            country = "INT",
            streamUrl = "https://test-streams.mux.dev/test_001/stream.m3u8"
        ),
        Channel(
            id = "tears_of_steel_global",
            name = "Tears of Steel Sci-Fi",
            logoUrl = "https://images.unsplash.com/photo-1451187580459-43490279c0fa?w=200",
            category = "Movies",
            language = "English",
            country = "INT",
            streamUrl = "https://test-streams.mux.dev/pts_shift/master.m3u8"
        ),
        Channel(
            id = "bunny_global",
            name = "Big Buck Bunny HD",
            logoUrl = "https://images.unsplash.com/photo-1579783902614-a3fb3927b675?w=200",
            category = "Animation",
            language = "English",
            country = "INT",
            streamUrl = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4"
        )
    )

    fun getCategoryFallbackChannels(filter: String): List<Channel> {
        val lower = filter.lowercase()
        return when {
            lower.contains("sport") -> listOf(
                Channel("sp_1", "Sports Arena Live", "https://images.unsplash.com/photo-1508098682722-e99c43a406b2?w=200", "Sports", "English", "INT", "https://cph-p2p-msl.akamaized.net/hls/live/2000341/test/master.m3u8"),
                Channel("sp_2", "Red Bull Action Sports", "https://resources.redbull.com/logos/redbull-tv-logo.png", "Sports", "English", "INT", "https://rbmn-live.akamaized.net/hls/live/590964/BoRB-AT/master.m3u8")
            )
            lower.contains("movie") || lower.contains("cinema") -> listOf(
                Channel("mov_1", "Cinema Classics HD", "https://images.unsplash.com/photo-1489599849927-2ee91cede3ba?w=200", "Movies", "English", "INT", "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8"),
                Channel("mov_2", "Tears of Steel Sci-Fi", "https://images.unsplash.com/photo-1451187580459-43490279c0fa?w=200", "Movies", "English", "INT", "https://test-streams.mux.dev/pts_shift/master.m3u8")
            )
            lower.contains("music") -> listOf(
                Channel("mus_1", "9XM Music Live", "https://xstreamcp-assets-msp.streamready.in/assets/LIVETV/LIVECHANNEL/LIVETV_LIVETVCHANNEL_9XM/images/LOGO_HD/image.png", "Music", "Hindi", "IN", "https://9xjio.wiseplayout.com/9XM/master.m3u8")
            )
            lower.contains("news") -> listOf(
                Channel("news_1", "Aaj Tak Live HD", "https://upload.wikimedia.org/wikipedia/commons/thumb/c/cb/Aaj_tak_logo.png/512px-Aaj_tak_logo.png", "News", "Hindi", "IN", "https://feeds.intoday.in/aajtak/api/aajtakhd/master.m3u8")
            )
            else -> FALLBACK_INDIA_CHANNELS
        }
    }
}
