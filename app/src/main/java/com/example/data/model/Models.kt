package com.example.data.model

enum class AppLanguage(val code: String, val displayName: String, val nativeName: String) {
    ENGLISH("en", "English", "English"),
    HINDI("hi", "Hindi", "हिन्दी"),
    SPANISH("es", "Spanish", "Español"),
    ARABIC("ar", "Arabic", "العربية")
}

data class Channel(
    val id: String,
    val name: String,
    val streamUrl: String,
    val logoUrl: String = "",
    val category: String = "General",
    val country: String = "Global",
    val language: String = "English",
    val isFavorite: Boolean = false
) {
    fun isMovieOrVod(): Boolean {
        val lowerUrl = streamUrl.lowercase()
        val lowerCat = category.lowercase()
        return lowerUrl.endsWith(".mp4") ||
            lowerUrl.endsWith(".mkv") ||
            lowerCat.contains("movie") ||
            lowerCat.contains("cinema") ||
            lowerCat.contains("vod")
    }
}

data class CommunityPlaylistPack(
    val id: String,
    val name: String,
    val description: String,
    val url: String,
    val channelCountHint: String
)

object CommunityPlaylists {
    val featured: List<CommunityPlaylistPack> = listOf(
        CommunityPlaylistPack(
            id = "iptv_org_global",
            name = "IPTV-Org Global Live TV",
            description = "Curated international FTA live television streams",
            url = "https://iptv-org.github.io/iptv/index.m3u",
            channelCountHint = "8000+ Live"
        ),
        CommunityPlaylistPack(
            id = "iptv_org_india",
            name = "India Regional & News Pack",
            description = "Hindi, English & regional Indian FTA live channels",
            url = "https://iptv-org.github.io/iptv/countries/in.m3u",
            channelCountHint = "350+ Live"
        ),
        CommunityPlaylistPack(
            id = "iptv_org_movies",
            name = "Movies & Classic Cinema",
            description = "24/7 movie channels, public domain cinema & trailers",
            url = "https://iptv-org.github.io/iptv/categories/movies.m3u",
            channelCountHint = "200+ Channels"
        ),
        CommunityPlaylistPack(
            id = "iptv_org_sports",
            name = "Global Sports & Action",
            description = "Live sports networks, motorsports & highlights",
            url = "https://iptv-org.github.io/iptv/categories/sports.m3u",
            channelCountHint = "180+ Channels"
        )
    )
}
