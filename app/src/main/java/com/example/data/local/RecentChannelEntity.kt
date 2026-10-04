package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "recent_channels")
data class RecentChannelEntity(
    @PrimaryKey val streamUrl: String,
    val channelId: String,
    val name: String,
    val logoUrl: String?,
    val category: String,
    val language: String?,
    val country: String?,
    val watchedAt: Long = System.currentTimeMillis()
)
