package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ChannelDao {
    @Query("SELECT * FROM favorites ORDER BY addedAt DESC")
    fun getAllFavorites(): Flow<List<FavoriteChannelEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFavorite(channel: FavoriteChannelEntity)

    @Query("DELETE FROM favorites WHERE streamUrl = :streamUrl")
    suspend fun deleteFavorite(streamUrl: String)

    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE streamUrl = :streamUrl)")
    fun isFavorite(streamUrl: String): Flow<Boolean>

    @Query("SELECT * FROM recents ORDER BY watchedAt DESC LIMIT 20")
    fun getRecentChannels(): Flow<List<RecentChannelEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecent(channel: RecentChannelEntity)

    @Query("DELETE FROM recents")
    suspend fun clearRecents()

    @Query("SELECT * FROM custom_playlists ORDER BY addedAt DESC")
    fun getAllCustomPlaylists(): Flow<List<CustomPlaylistEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCustomPlaylist(playlist: CustomPlaylistEntity)

    @Query("DELETE FROM custom_playlists WHERE id = :id")
    suspend fun deleteCustomPlaylist(id: String)

    @Query("SELECT * FROM xtream_accounts ORDER BY addedAt DESC")
    fun getAllXtreamAccounts(): Flow<List<XtreamAccountEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertXtreamAccount(account: XtreamAccountEntity)

    @Query("DELETE FROM xtream_accounts WHERE id = :id")
    suspend fun deleteXtreamAccount(id: String)
}
