package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "favorite_channels")
data class FavoriteChannelEntity(
    @PrimaryKey val streamUrl: String,
    val channelId: String,
    val name: String,
    val logoUrl: String?,
    val category: String,
    val language: String?,
    val country: String?,
    val addedAt: Long = System.currentTimeMillis()
)
