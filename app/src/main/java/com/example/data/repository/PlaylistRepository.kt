package com.example.data.repository

import android.content.Context
import com.example.data.local.ChannelDao
import com.example.data.local.CustomPlaylistEntity
import com.example.data.local.FavoriteChannelEntity
import com.example.data.local.IptvDatabase
import com.example.data.local.RecentChannelEntity
import com.example.data.model.Channel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

class PlaylistRepository(
    private val channelDao: ChannelDao,
    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()
) {
    private val playlistCache = ConcurrentHashMap<String, List<Channel>>().apply {
        put(PresetPlaylists.INDIA.url, PresetPlaylists.FALLBACK_INDIA_CHANNELS)
        put(PresetPlaylists.GLOBAL.url, PresetPlaylists.FALLBACK_GLOBAL_CHANNELS)
    }

    fun clearCache(url: String? = null) {
        if (url != null) {
            playlistCache.remove(url)
        } else {
            playlistCache.clear()
        }
    }

    companion object {
        @Volatile
        private var INSTANCE: PlaylistRepository? = null

        fun getInstance(context: Context): PlaylistRepository {
            return INSTANCE ?: synchronized(this) {
                val db = IptvDatabase.getInstance(context)
                val instance = PlaylistRepository(db.channelDao())
                INSTANCE = instance
                instance
            }
        }
    }

    /**
     * Fetches channels for the given playlist source.
     * Emits fallback/cached first for instant rendering, then updates from remote network.
     */
    fun loadChannels(source: PlaylistSource): Flow<List<Channel>> = flow {
        if (source.id == PresetPlaylists.ALL.id) {
            val combinedList = mutableListOf<Channel>()
            combinedList.addAll(PresetPlaylists.FALLBACK_INDIA_CHANNELS)
            combinedList.addAll(PresetPlaylists.FALLBACK_GLOBAL_CHANNELS)
            playlistCache.values.forEach { list ->
                combinedList.addAll(list)
            }
            val distinct = combinedList.distinctBy { it.streamUrl }
            emit(distinct)
            return@flow
        }

        // 1. Emit cached or fallback first for instantaneous UI feedback
        val cached = playlistCache[source.url]
        if (cached != null && cached.isNotEmpty()) {
            emit(cached)
        } else {
            val fallback = when (source.id) {
                PresetPlaylists.INDIA.id -> PresetPlaylists.FALLBACK_INDIA_CHANNELS
                PresetPlaylists.GLOBAL.id -> PresetPlaylists.FALLBACK_GLOBAL_CHANNELS
                else -> PresetPlaylists.getCategoryFallbackChannels(source.url + " " + source.title)
            }
            if (fallback.isNotEmpty()) {
                emit(fallback)
            }
        }

        // 2. Fetch from network
        try {
            val parsedChannels = withContext(Dispatchers.IO) {
                if (source.url.startsWith("file://") || source.url.startsWith("/")) {
                    val filePath = source.url.removePrefix("file://")
                    val file = java.io.File(filePath)
                    if (file.exists()) {
                        file.inputStream().use { inputStream ->
                            M3uParser.parse(inputStream)
                        }
                    } else {
                        emptyList()
                    }
                } else if (source.url.startsWith("http", ignoreCase = true)) {
                    val parsed = kotlinx.coroutines.withTimeoutOrNull(10000L) {
                        val request = Request.Builder()
                            .url(source.url)
                            .header("User-Agent", "Mozilla/5.0 (Android; Mobile; IPTVPlayer)")
                            .build()

                        val response = okHttpClient.newCall(request).execute()
                        if (!response.isSuccessful) {
                            throw IllegalStateException("Failed to load playlist: HTTP ${response.code}")
                        }
                        val body = response.body ?: throw IllegalStateException("Empty playlist response")
                        body.byteStream().use { inputStream ->
                            M3uParser.parse(inputStream)
                        }
                    }
                    parsed ?: emptyList()
                } else {
                    emptyList()
                }
            }

            if (parsedChannels.isNotEmpty()) {
                val verifiedChannels = when (source.id) {
                    PresetPlaylists.INDIA.id -> PresetPlaylists.FALLBACK_INDIA_CHANNELS
                    PresetPlaylists.GLOBAL.id -> PresetPlaylists.FALLBACK_GLOBAL_CHANNELS
                    else -> emptyList()
                }
                val prioritized = (verifiedChannels + parsedChannels).distinctBy { it.streamUrl }
                playlistCache[source.url] = prioritized
                emit(prioritized)
            }
        } catch (e: Exception) {
            // Keep previous cached/fallback if network fails
            if (!playlistCache.containsKey(source.url)) {
                val fallback = when (source.id) {
                    PresetPlaylists.INDIA.id -> PresetPlaylists.FALLBACK_INDIA_CHANNELS
                    PresetPlaylists.GLOBAL.id -> PresetPlaylists.FALLBACK_GLOBAL_CHANNELS
                    else -> emptyList()
                }
                if (fallback.isNotEmpty()) {
                    emit(fallback)
                }
            }
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Combines channels with local Room favorites to reflect isFavorite state
     */
    fun observeChannelsWithFavorites(source: PlaylistSource): Flow<List<Channel>> {
        return combine(
            loadChannels(source),
            channelDao.getAllFavorites()
        ) { channels, favorites ->
            val favUrls = favorites.map { it.streamUrl }.toSet()
            channels.map { ch ->
                ch.copy(isFavorite = favUrls.contains(ch.streamUrl))
            }
        }.flowOn(Dispatchers.Default)
    }

    /**
     * Local Favorites Flow
     */
    fun observeFavorites(): Flow<List<Channel>> {
        return channelDao.getAllFavorites().map { entities ->
            entities.map { entity ->
                Channel(
                    id = entity.channelId,
                    name = entity.name,
                    logoUrl = entity.logoUrl,
                    category = entity.category,
                    language = entity.language,
                    country = entity.country,
                    streamUrl = entity.streamUrl,
                    isFavorite = true
                )
            }
        }.flowOn(Dispatchers.Default)
    }

    /**
     * Local Recents Flow
     */
    fun observeRecents(): Flow<List<Channel>> {
        return combine(
            channelDao.getRecentChannels(),
            channelDao.getAllFavorites()
        ) { recents, favorites ->
            val favUrls = favorites.map { it.streamUrl }.toSet()
            recents.map { entity ->
                Channel(
                    id = entity.channelId,
                    name = entity.name,
                    logoUrl = entity.logoUrl,
                    category = entity.category,
                    language = entity.language,
                    country = entity.country,
                    streamUrl = entity.streamUrl,
                    isFavorite = favUrls.contains(entity.streamUrl)
                )
            }
        }.flowOn(Dispatchers.Default)
    }

    suspend fun toggleFavorite(channel: Channel): Boolean = withContext(Dispatchers.IO) {
        val currentlyFav = channelDao.isFavoriteDirect(channel.streamUrl)
        if (currentlyFav) {
            channelDao.deleteFavorite(channel.streamUrl)
            false
        } else {
            channelDao.insertFavorite(
                FavoriteChannelEntity(
                    streamUrl = channel.streamUrl,
                    channelId = channel.id,
                    name = channel.name,
                    logoUrl = channel.logoUrl,
                    category = channel.category,
                    language = channel.language,
                    country = channel.country
                )
            )
            true
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

    fun observeCustomPlaylists(): Flow<List<CustomPlaylistEntity>> {
        return channelDao.getAllCustomPlaylists()
    }

    suspend fun addCustomPlaylist(title: String, url: String): Long = withContext(Dispatchers.IO) {
        val cleanTitle = title.trim().ifEmpty { "My Playlist" }
        channelDao.insertCustomPlaylist(
            CustomPlaylistEntity(title = cleanTitle, url = url.trim())
        )
    }

    suspend fun deleteCustomPlaylist(id: Long) = withContext(Dispatchers.IO) {
        channelDao.deleteCustomPlaylist(id)
    }
}
