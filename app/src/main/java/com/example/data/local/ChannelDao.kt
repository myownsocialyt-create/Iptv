package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ChannelDao {
    @Query("SELECT * FROM favorite_channels ORDER BY addedAt DESC")
    fun getAllFavorites(): Flow<List<FavoriteChannelEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFavorite(favorite: FavoriteChannelEntity)

    @Query("DELETE FROM favorite_channels WHERE streamUrl = :streamUrl")
    suspend fun deleteFavorite(streamUrl: String)

    @Query("SELECT EXISTS(SELECT 1 FROM favorite_channels WHERE streamUrl = :streamUrl)")
    suspend fun isFavoriteDirect(streamUrl: String): Boolean

    @Query("SELECT * FROM recent_channels ORDER BY watchedAt DESC LIMIT 30")
    fun getRecentChannels(): Flow<List<RecentChannelEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecent(recent: RecentChannelEntity)

    @Query("SELECT * FROM custom_playlists ORDER BY createdAt DESC")
    fun getAllCustomPlaylists(): Flow<List<CustomPlaylistEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCustomPlaylist(playlist: CustomPlaylistEntity): Long

    @Query("DELETE FROM custom_playlists WHERE id = :id")
    suspend fun deleteCustomPlaylist(id: Long)

    @Query("SELECT * FROM xtream_accounts ORDER BY createdAt DESC")
    fun getAllXtreamAccounts(): Flow<List<XtreamAccountEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertXtreamAccount(account: XtreamAccountEntity): Long

    @Query("UPDATE xtream_accounts SET isActive = (id = :activeId)")
    suspend fun setActiveXtreamAccount(activeId: Long)

    @Query("DELETE FROM xtream_accounts WHERE id = :id")
    suspend fun deleteXtreamAccount(id: Long)

    @Query("SELECT * FROM xtream_accounts WHERE isActive = 1 LIMIT 1")
    fun getActiveXtreamAccount(): Flow<XtreamAccountEntity?>
}
