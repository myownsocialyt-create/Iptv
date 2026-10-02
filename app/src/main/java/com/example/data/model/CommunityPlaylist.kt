package com.example.data.model

data class CommunityPlaylist(
    val id: String,
    val name: String,
    val description: String,
    val url: String,
    val channelCountEstimate: Int,
    val category: String,
    val country: String? = null,
    val flagEmoji: String = "📺"
) {
    val title: String get() = name
}

object CommunityPlaylists {
    const val PUBLIC_PLAYLISTS_URL = "https://github.com/iptv-org/iptv"

    val presets = listOf(
        CommunityPlaylist(
            id = "iptv_org_all",
            name = "IPTV-org Global",
            description = "Over 8,000 free, public broadcast channels worldwide",
            url = "https://iptv-org.github.io/iptv/index.m3u",
            channelCountEstimate = 8000,
            category = "Global",
            flagEmoji = "🌍"
        ),
        CommunityPlaylist(
            id = "iptv_org_in",
            name = "India TV (National & Regional)",
            description = "News, entertainment, movies, and sports across India",
            url = "https://iptv-org.github.io/iptv/countries/in.m3u",
            channelCountEstimate = 744,
            category = "Regional",
            country = "IN",
            flagEmoji = "🇮🇳"
        ),
        CommunityPlaylist(
            id = "iptv_org_us",
            name = "United States TV",
            description = "US national news, PBS, weather, and free local channels",
            url = "https://iptv-org.github.io/iptv/countries/us.m3u",
            channelCountEstimate = 650,
            category = "Regional",
            country = "US",
            flagEmoji = "🇺🇸"
        ),
        CommunityPlaylist(
            id = "iptv_org_uk",
            name = "United Kingdom TV",
            description = "UK public service broadcasts, news, and entertainment",
            url = "https://iptv-org.github.io/iptv/countries/uk.m3u",
            channelCountEstimate = 320,
            category = "Regional",
            country = "UK",
            flagEmoji = "🇬🇧"
        ),
        CommunityPlaylist(
            id = "iptv_org_news",
            name = "24/7 Global News Network",
            description = "Live English & multilingual international news channels",
            url = "https://iptv-org.github.io/iptv/categories/news.m3u",
            channelCountEstimate = 450,
            category = "News",
            flagEmoji = "📰"
        ),
        CommunityPlaylist(
            id = "iptv_org_movies",
            name = "Cinema & Classic Movies",
            description = "Free retro, classic, and independent movie channels",
            url = "https://iptv-org.github.io/iptv/categories/movies.m3u",
            channelCountEstimate = 280,
            category = "Movies",
            flagEmoji = "🎬"
        )
    )

    val ALL: List<CommunityPlaylist> get() = presets
}
