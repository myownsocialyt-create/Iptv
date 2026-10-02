package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "recents")
data class RecentChannelEntity(
    @PrimaryKey
    val streamUrl: String,
    val channelId: String,
    val name: String,
    val logoUrl: String? = null,
    val category: String = "General",
    val language: String? = null,
    val country: String? = null,
    val watchedAt: Long = System.currentTimeMillis()
)
