package com.example.data.model

import kotlinx.serialization.Serializable

@Serializable
data class Channel(
    val id: String,
    val name: String,
    val streamUrl: String,
    val logoUrl: String? = null,
    val category: String = "General",
    val language: String? = null,
    val country: String? = null,
    val tvgId: String? = null,
    val isFavorite: Boolean = false,
    val playlistName: String? = null
) {
    val isMovieOrVod: Boolean
        get() {
            val urlLower = streamUrl.lowercase()
            val catLower = category.lowercase()
            val nameLower = name.lowercase()
            return urlLower.contains("/movie/") ||
                    urlLower.contains("/series/") ||
                    urlLower.endsWith(".mp4") ||
                    urlLower.endsWith(".mkv") ||
                    urlLower.endsWith(".avi") ||
                    urlLower.endsWith(".mov") ||
                    catLower.contains("movie") ||
                    catLower.contains("vod") ||
                    catLower.contains("cinema") ||
                    nameLower.contains("[vod]") ||
                    nameLower.contains("(vod)")
        }
}
