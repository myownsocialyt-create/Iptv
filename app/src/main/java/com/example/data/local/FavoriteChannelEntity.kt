package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "favorites")
data class FavoriteChannelEntity(
    @PrimaryKey
    val streamUrl: String,
    val channelId: String,
    val name: String,
    val logoUrl: String? = null,
    val category: String = "General",
    val language: String? = null,
    val country: String? = null,
    val playlistName: String? = null,
    val addedAt: Long = System.currentTimeMillis()
)
