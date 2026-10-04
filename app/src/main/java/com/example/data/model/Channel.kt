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
    val playlistName: String? = null
)
