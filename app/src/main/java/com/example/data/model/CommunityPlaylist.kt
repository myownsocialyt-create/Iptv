package com.example.data.model

data class CommunityPlaylist(
    val name: String,
    val description: String,
    val url: String,
    val channelCount: String,
    val category: String
)

object CommunityPlaylists {
    val LIST = listOf(
        CommunityPlaylist(
            name = "Free IPTV Global Index",
            description = "Over 8,000 publicly broadcast international channels organized by category",
            url = "https://iptv-org.github.io/iptv/index.m3u",
            channelCount = "8,000+ Channels",
            category = "Global / General"
        ),
        CommunityPlaylist(
            name = "India TV Broadcasts",
            description = "News, entertainment, movies, and regional television from India",
            url = "https://iptv-org.github.io/iptv/countries/in.m3u",
            channelCount = "250+ Channels",
            category = "Regional / India"
        ),
        CommunityPlaylist(
            name = "International News 24/7",
            description = "Worldwide breaking news networks broadcasting live",
            url = "https://iptv-org.github.io/iptv/categories/news.m3u",
            channelCount = "400+ Channels",
            category = "News"
        ),
        CommunityPlaylist(
            name = "Global Sports Live",
            description = "Sports leagues, extreme outdoor sports, and racing broadcasts",
            url = "https://iptv-org.github.io/iptv/categories/sports.m3u",
            channelCount = "200+ Channels",
            category = "Sports"
        ),
        CommunityPlaylist(
            name = "Music & Concert Feeds",
            description = "Continuous live music video streams and live concerts",
            url = "https://iptv-org.github.io/iptv/categories/music.m3u",
            channelCount = "300+ Channels",
            category = "Music"
        )
    )
}
