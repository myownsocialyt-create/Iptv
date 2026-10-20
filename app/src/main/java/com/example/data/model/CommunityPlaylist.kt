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
            name = "India Channels",
            description = "News, entertainment, movies, and regional television from India",
            url = "https://iptv-org.github.io/iptv/countries/in.m3u",
            channelCount = "250+ Channels",
            category = "Regional / India"
        ),
        CommunityPlaylist(
            name = "Global Channels",
            description = "Worldwide public broadcast international channels index",
            url = "https://iptv-org.github.io/iptv/index.m3u",
            channelCount = "8,000+ Channels",
            category = "Global / General"
        ),
        CommunityPlaylist(
            name = "iptv-demo | Sports",
            description = "Live sports leagues, auto racing, and extreme sports broadcasts",
            url = "https://iptv-org.github.io/iptv/categories/sports.m3u",
            channelCount = "300+ Channels",
            category = "Sports"
        ),
        CommunityPlaylist(
            name = "iptv-demo | Family",
            description = "Family-friendly entertainment and kids television",
            url = "https://iptv-org.github.io/iptv/categories/family.m3u",
            channelCount = "150+ Channels",
            category = "Family"
        ),
        CommunityPlaylist(
            name = "iptv-demo | Lifestyle",
            description = "Cooking, travel, home design, and lifestyle shows",
            url = "https://iptv-org.github.io/iptv/categories/lifestyle.m3u",
            channelCount = "200+ Channels",
            category = "Lifestyle"
        ),
        CommunityPlaylist(
            name = "iptv-demo | News",
            description = "24/7 breaking global news and financial reports",
            url = "https://iptv-org.github.io/iptv/categories/news.m3u",
            channelCount = "450+ Channels",
            category = "News"
        ),
        CommunityPlaylist(
            name = "iptv-demo | Music",
            description = "Non-stop music videos, live concerts, and charts",
            url = "https://iptv-org.github.io/iptv/categories/music.m3u",
            channelCount = "320+ Channels",
            category = "Music"
        ),
        CommunityPlaylist(
            name = "iptv-demo | Movies",
            description = "Classic cinema, indie films, and blockbuster movie streams",
            url = "https://iptv-org.github.io/iptv/categories/movies.m3u",
            channelCount = "280+ Channels",
            category = "Movies"
        ),
        CommunityPlaylist(
            name = "iptv-demo | Animation",
            description = "Cartoons, anime, and animated series streams",
            url = "https://iptv-org.github.io/iptv/categories/animation.m3u",
            channelCount = "180+ Channels",
            category = "Animation"
        ),
        CommunityPlaylist(
            name = "iptv-demo | Kids",
            description = "Educational programs, nursery rhymes, and kids series",
            url = "https://iptv-org.github.io/iptv/categories/kids.m3u",
            channelCount = "160+ Channels",
            category = "Kids"
        ),
        CommunityPlaylist(
            name = "iptv-demo | Documentary",
            description = "Science, nature, historical and documentary channels",
            url = "https://iptv-org.github.io/iptv/categories/documentary.m3u",
            channelCount = "220+ Channels",
            category = "Documentary"
        ),
        CommunityPlaylist(
            name = "iptv-demo | Entertainment",
            description = "Reality television, drama series, and comedy shows",
            url = "https://iptv-org.github.io/iptv/categories/entertainment.m3u",
            channelCount = "380+ Channels",
            category = "Entertainment"
        ),
        CommunityPlaylist(
            name = "iptv-demo | Science",
            description = "Space exploration, tech innovations, and nature",
            url = "https://iptv-org.github.io/iptv/categories/science.m3u",
            channelCount = "110+ Channels",
            category = "Science"
        )
    )
}
