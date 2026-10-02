package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "custom_playlists")
data class CustomPlaylistEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val url: String,
    val isFile: Boolean = false,
    val channelCount: Int = 0,
    val addedAt: Long = System.currentTimeMillis()
) {
    val title: String get() = name
}
