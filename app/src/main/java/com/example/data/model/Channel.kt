package com.example.data.model

data class Channel(
    val id: String,
    val name: String,
    val logoUrl: String? = null,
    val category: String = "General",
    val language: String? = null,
    val country: String? = null,
    val streamUrl: String,
    val isFavorite: Boolean = false,
    val playlistName: String? = null,
    val userAgent: String? = null,
    val referer: String? = null,
    val isMovieOrVod: Boolean = id.startsWith("vod_", ignoreCase = true) ||
            id.startsWith("movie_", ignoreCase = true) ||
            id.startsWith("demo_vod", ignoreCase = true) ||
            category.contains("vod", ignoreCase = true) ||
            streamUrl.contains("/movie/", ignoreCase = true) ||
            streamUrl.contains("/vod/", ignoreCase = true) ||
            streamUrl.contains("/series/", ignoreCase = true) ||
            streamUrl.endsWith(".mp4", ignoreCase = true) ||
            streamUrl.endsWith(".mkv", ignoreCase = true) ||
            playlistName?.contains("VOD", ignoreCase = true) == true
) {
    val isXtreamChannel: Boolean
        get() = id.startsWith("xtream_", ignoreCase = true) ||
                id.startsWith("vod_", ignoreCase = true) ||
                id.startsWith("demo_live_", ignoreCase = true) ||
                id.startsWith("demo_vod_", ignoreCase = true) ||
                playlistName?.startsWith("Xtream", ignoreCase = true) == true ||
                streamUrl.contains("player_api.php", ignoreCase = true)
}
