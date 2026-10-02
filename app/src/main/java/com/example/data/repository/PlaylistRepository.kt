package com.example.data.repository

import android.content.Context
import android.util.Log
import com.example.data.local.ChannelDao
import com.example.data.local.CustomPlaylistEntity
import com.example.data.local.FavoriteChannelEntity
import com.example.data.local.RecentChannelEntity
import com.example.data.model.Channel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

class PlaylistRepository(
    private val context: Context,
    private val channelDao: ChannelDao
) {
    private val memoryCache = mutableMapOf<String, List<Channel>>()

    suspend fun getChannelsForSource(source: PlaylistSource): List<Channel> = withContext(Dispatchers.IO) {
        if (source.id == PresetPlaylists.ALL.id) {
            val india = getChannelsForSource(PresetPlaylists.INDIA)
            val global = getChannelsForSource(PresetPlaylists.GLOBAL)
            return@withContext (india + global).distinctBy { it.streamUrl }
        }

        memoryCache[source.url]?.let { return@withContext it }

        try {
            val url = URL(source.url)
            val connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 12000
            connection.readTimeout = 15000
            connection.setRequestProperty("User-Agent", "Hypnotix-IPTV/1.0 (Android)")
            connection.connect()

            if (connection.responseCode in 200..299) {
                val channels = connection.inputStream.use { stream ->
                    M3uParser.parse(stream, defaultPlaylistName = source.title)
                }
                if (channels.isNotEmpty()) {
                    memoryCache[source.url] = channels
                    return@withContext channels
                }
            }
        } catch (e: Exception) {
            Log.w("PlaylistRepository", "Failed to fetch playlist ${source.title}: ${e.message}")
        }

        // Offline / Network Failure Fallback
        val fallback = when (source.id) {
            PresetPlaylists.INDIA.id -> PresetPlaylists.FALLBACK_INDIA_CHANNELS
            PresetPlaylists.GLOBAL.id -> PresetPlaylists.FALLBACK_GLOBAL_CHANNELS
            else -> emptyList()
        }
        fallback
    }

    suspend fun parseCustomM3u(name: String, urlString: String): List<Channel> = withContext(Dispatchers.IO) {
        val url = URL(urlString)
        val connection = url.openConnection() as HttpURLConnection
        connection.connectTimeout = 12000
        connection.readTimeout = 15000
        connection.setRequestProperty("User-Agent", "Hypnotix-IPTV/1.0 (Android)")
        connection.connect()

        connection.inputStream.use { stream ->
            M3uParser.parse(stream, defaultPlaylistName = name)
        }
    }

    // Room Favorites
    fun getFavorites(): Flow<List<Channel>> = channelDao.getAllFavorites().map { list ->
        list.map {
            Channel(
                id = it.channelId,
                name = it.name,
                streamUrl = it.streamUrl,
                logoUrl = it.logoUrl,
                category = it.category,
                language = it.language,
                country = it.country,
                isFavorite = true,
                playlistName = it.playlistName
            )
        }
    }

    suspend fun toggleFavorite(channel: Channel) = withContext(Dispatchers.IO) {
        val entity = FavoriteChannelEntity(
            streamUrl = channel.streamUrl,
            channelId = channel.id,
            name = channel.name,
            logoUrl = channel.logoUrl,
            category = channel.category,
            language = channel.language,
            country = channel.country,
            playlistName = channel.playlistName
        )
        if (channel.isFavorite) {
            channelDao.deleteFavorite(channel.streamUrl)
        } else {
            channelDao.insertFavorite(entity)
        }
    }

    fun isFavorite(streamUrl: String): Flow<Boolean> = channelDao.isFavorite(streamUrl)

    // Recents
    fun getRecentChannels(): Flow<List<Channel>> = channelDao.getRecentChannels().map { list ->
        list.map {
            Channel(
                id = it.channelId,
                name = it.name,
                streamUrl = it.streamUrl,
                logoUrl = it.logoUrl,
                category = it.category,
                language = it.language,
                country = it.country
            )
        }
    }

    suspend fun recordRecent(channel: Channel) = withContext(Dispatchers.IO) {
        channelDao.insertRecent(
            RecentChannelEntity(
                streamUrl = channel.streamUrl,
                channelId = channel.id,
                name = channel.name,
                logoUrl = channel.logoUrl,
                category = channel.category,
                language = channel.language,
                country = channel.country
            )
        )
    }

    suspend fun clearRecents() = withContext(Dispatchers.IO) {
        channelDao.clearRecents()
    }

    // Custom Playlists
    fun getCustomPlaylists(): Flow<List<CustomPlaylistEntity>> = channelDao.getAllCustomPlaylists()

    suspend fun addCustomPlaylist(entity: CustomPlaylistEntity) = withContext(Dispatchers.IO) {
        channelDao.insertCustomPlaylist(entity)
    }

    suspend fun deleteCustomPlaylist(id: String) = withContext(Dispatchers.IO) {
        channelDao.deleteCustomPlaylist(id)
    }
}
