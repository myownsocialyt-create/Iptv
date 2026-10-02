package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "xtream_accounts")
data class XtreamAccountEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    val serverUrl: String,
    val username: String,
    val password: String,
    val isActive: Boolean = true,
    val liveChannelsCount: Int = 0,
    val vodCount: Int = 0,
    val addedAt: Long = System.currentTimeMillis()
)
