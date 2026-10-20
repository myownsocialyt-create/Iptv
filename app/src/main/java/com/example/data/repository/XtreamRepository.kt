package com.example.data.repository

import com.example.data.local.ChannelDao
import com.example.data.local.XtreamAccountEntity
import com.example.data.model.Channel
import com.example.player.XtreamStreamResolver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class XtreamRepository(
    private val channelDao: ChannelDao,
    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()
) {
    fun observeAccounts(): Flow<List<XtreamAccountEntity>> = channelDao.getAllXtreamAccounts()

    fun observeActiveAccount(): Flow<XtreamAccountEntity?> = channelDao.getActiveXtreamAccount()

    suspend fun addAccount(
        name: String,
        serverUrl: String,
        username: String,
        password: String
    ): Result<Long> = withContext(Dispatchers.IO) {
        val rawServer = serverUrl.trim().removeSuffix("/")
        val cleanUser = username.trim()
        val cleanPass = password.trim()
        val cleanName = name.trim().ifEmpty { "Xtream Account" }

        val cleanServer = if (!rawServer.startsWith("http://", ignoreCase = true) && !rawServer.startsWith("https://", ignoreCase = true)) {
            "http://$rawServer"
        } else {
            rawServer
        }

        // Special handling for demo account requested by user:
        // demo.xtream-server.tv or user=demo, pass=demo or server contains demo
        val isDemoHost = cleanServer.contains("demo.xtream-server.tv", ignoreCase = true) ||
                cleanServer.contains("xtream-server.tv", ignoreCase = true) ||
                cleanServer.contains("demo", ignoreCase = true) ||
                (cleanUser.equals("demo", ignoreCase = true) && cleanPass.equals("demo", ignoreCase = true))

        if (isDemoHost) {
            val id = channelDao.insertXtreamAccount(
                XtreamAccountEntity(
                    name = cleanName.ifEmpty { "Demo TV" },
                    serverUrl = cleanServer,
                    username = cleanUser.ifEmpty { "demo" },
                    password = cleanPass.ifEmpty { "demo" },
                    isActive = true
                )
            )
            channelDao.setActiveXtreamAccount(id)
            return@withContext Result.success(id)
        }

        val testUrl = "$cleanServer/player_api.php?username=$cleanUser&password=$cleanPass"

        try {
            val request = Request.Builder().url(testUrl).build()
            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("HTTP error ${response.code} connecting to Xtream server"))
            }

            val body = response.body?.string() ?: ""
            val json = JSONObject(body)
            val userInfo = json.optJSONObject("user_info")
            val auth = userInfo?.optInt("auth", 0) ?: 0

            if (auth != 1) {
                val status = userInfo?.optString("status", "Invalid credentials") ?: "Invalid credentials"
                return@withContext Result.failure(Exception("Xtream login failed: $status"))
            }

            val id = channelDao.insertXtreamAccount(
                XtreamAccountEntity(
                    name = cleanName,
                    serverUrl = cleanServer,
                    username = cleanUser,
                    password = cleanPass,
                    isActive = true
                )
            )
            channelDao.setActiveXtreamAccount(id)
            Result.success(id)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun setActiveAccount(id: Long) = withContext(Dispatchers.IO) {
        channelDao.setActiveXtreamAccount(id)
    }

    suspend fun deleteAccount(id: Long) = withContext(Dispatchers.IO) {
        channelDao.deleteXtreamAccount(id)
    }

    suspend fun fetchLiveStreams(account: XtreamAccountEntity): List<Channel> = withContext(Dispatchers.IO) {
        val isDemo = account.serverUrl.contains("demo.xtream-server.tv", ignoreCase = true) ||
                account.serverUrl.contains("demo", ignoreCase = true) ||
                (account.username.equals("demo", ignoreCase = true) && account.password.equals("demo", ignoreCase = true))

        if (isDemo) {
            return@withContext getDemoLiveStreams(account)
        }

        val url = "${account.serverUrl}/player_api.php?username=${account.username}&password=${account.password}&action=get_live_streams"
        try {
            val request = Request.Builder().url(url).build()
            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) return@withContext emptyList()

            val body = response.body?.string() ?: return@withContext emptyList()
            val array = JSONArray(body)
            val result = mutableListOf<Channel>()

            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val streamId = obj.optInt("stream_id", 0)
                val name = obj.optString("name", "Channel $streamId")
                val rawIcon = obj.optString("stream_icon").takeIf { it.isNotBlank() }
                val categoryName = obj.optString("category_name").takeIf { it.isNotBlank() } ?: "Live TV"
                val streamIcon = com.example.ui.components.ChannelLogoResolver.resolveOptimalLogoUrl(name, rawIcon, categoryName)
                val streamUrl = XtreamStreamResolver.getEffectiveStreamUrl(
                    serverUrl = account.serverUrl,
                    username = account.username,
                    password = account.password,
                    streamId = streamId,
                    title = name,
                    isVod = false
                )

                result.add(
                    Channel(
                        id = "xtream_$streamId",
                        name = name,
                        logoUrl = streamIcon,
                        category = categoryName,
                        streamUrl = streamUrl,
                        playlistName = "Xtream: ${account.name}"
                    )
                )
            }
            result
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun fetchVodStreams(account: XtreamAccountEntity): List<Channel> = withContext(Dispatchers.IO) {
        val isDemo = account.serverUrl.contains("demo.xtream-server.tv", ignoreCase = true) ||
                account.serverUrl.contains("demo", ignoreCase = true) ||
                (account.username.equals("demo", ignoreCase = true) && account.password.equals("demo", ignoreCase = true))

        if (isDemo) {
            return@withContext getDemoVodStreams(account)
        }

        val url = "${account.serverUrl}/player_api.php?username=${account.username}&password=${account.password}&action=get_vod_streams"
        try {
            val request = Request.Builder().url(url).build()
            val response = okHttpClient.newCall(request).execute()
            if (!response.isSuccessful) return@withContext emptyList()

            val body = response.body?.string() ?: return@withContext emptyList()
            val array = JSONArray(body)
            val result = mutableListOf<Channel>()

            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val streamId = obj.optInt("stream_id", 0)
                val name = obj.optString("name", "VOD $streamId")
                val rawIcon = obj.optString("stream_icon").takeIf { it.isNotBlank() }
                val categoryName = obj.optString("category_name").takeIf { it.isNotBlank() } ?: "VOD Movies"
                val streamIcon = com.example.ui.components.ChannelLogoResolver.resolveOptimalLogoUrl(name, rawIcon, categoryName)
                val containerExtension = obj.optString("container_extension", "mp4")
                
                val streamUrl = XtreamStreamResolver.getEffectiveStreamUrl(
                    serverUrl = account.serverUrl,
                    username = account.username,
                    password = account.password,
                    streamId = streamId,
                    title = name,
                    isVod = true,
                    containerExtension = containerExtension
                )

                result.add(
                    Channel(
                        id = "vod_$streamId",
                        name = name,
                        logoUrl = streamIcon,
                        category = categoryName,
                        streamUrl = streamUrl,
                        playlistName = "Xtream VOD: ${account.name}"
                    )
                )
            }
            result
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun getDemoLiveStreams(account: XtreamAccountEntity): List<Channel> {
        return listOf(
            Channel(
                id = "demo_live_1",
                name = "Sports Arena Live HD",
                logoUrl = "https://images.unsplash.com/photo-1508098682722-e99c43a406b2?w=180&q=70&auto=format",
                category = "Sports",
                streamUrl = "https://rbmn-live.akamaized.net/hls/live/590964/BoRB-AT/master.m3u8",
                playlistName = "Xtream: ${account.name}"
            ),
            Channel(
                id = "demo_live_2",
                name = "Red Bull Extreme Sports",
                logoUrl = "https://resources.redbull.com/logos/redbull-tv-logo.png",
                category = "Sports",
                streamUrl = "https://rbmn-live.akamaized.net/hls/live/590964/BoRB-AT/master.m3u8",
                playlistName = "Xtream: ${account.name}"
            ),
            Channel(
                id = "demo_live_3",
                name = "24 News World Live HD",
                logoUrl = "https://images.unsplash.com/photo-1504711434969-e33886168f5c?w=180&q=70&auto=format",
                category = "News",
                streamUrl = "https://cph-p2p-msl.akamaized.net/hls/live/2000341/test/master.m3u8",
                playlistName = "Xtream: ${account.name}"
            ),
            Channel(
                id = "demo_live_4",
                name = "Action Sports TV 4K",
                logoUrl = "https://images.unsplash.com/photo-1516450360452-9312f5e86fc7?w=180&q=70&auto=format",
                category = "Sports",
                streamUrl = "https://devstreaming-cdn.apple.com/videos/streaming/examples/bipbop_16x9/bipbop_16x9_variant.m3u8",
                playlistName = "Xtream: ${account.name}"
            )
        )
    }

    private fun getDemoVodStreams(account: XtreamAccountEntity): List<Channel> {
        return listOf(
            Channel(
                id = "demo_vod_1",
                name = "The Dark Knight (2008)",
                logoUrl = "https://images.unsplash.com/photo-1509347528160-9a9e33742cdb?w=240&q=75&auto=format",
                category = "Movies & VOD",
                streamUrl = "https://test-streams.mux.dev/test_001/stream.m3u8",
                playlistName = "Xtream VOD: ${account.name}"
            ),
            Channel(
                id = "demo_vod_2",
                name = "Spider-Man: No Way Home (2021)",
                logoUrl = "https://images.unsplash.com/photo-1635805737707-575885ab0820?w=240&q=75&auto=format",
                category = "Movies & VOD",
                streamUrl = "https://devstreaming-cdn.apple.com/videos/streaming/examples/bipbop_16x9/bipbop_16x9_variant.m3u8",
                playlistName = "Xtream VOD: ${account.name}"
            ),
            Channel(
                id = "demo_vod_3",
                name = "Moana 2",
                logoUrl = "https://images.unsplash.com/photo-1518709268805-4e9042af9f23?w=240&q=75&auto=format",
                category = "Movies & VOD",
                streamUrl = "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8",
                playlistName = "Xtream VOD: ${account.name}"
            ),
            Channel(
                id = "demo_vod_4",
                name = "Avatar: Fire and Ash (2025)",
                logoUrl = "https://images.unsplash.com/photo-1518709268805-4e9042af9f23?w=240&q=75&auto=format",
                category = "Movies & VOD",
                streamUrl = "https://test-streams.mux.dev/pts_shift/master.m3u8",
                playlistName = "Xtream VOD: ${account.name}"
            ),
            Channel(
                id = "demo_vod_5",
                name = "Project Hail Mary (2026)",
                logoUrl = "https://images.unsplash.com/photo-1451187580459-43490279c0fa?w=240&q=75&auto=format",
                category = "Movies & VOD",
                streamUrl = "https://devstreaming-cdn.apple.com/videos/streaming/examples/bipbop_16x9/bipbop_16x9_variant.m3u8",
                playlistName = "Xtream VOD: ${account.name}"
            ),
            Channel(
                id = "demo_vod_6",
                name = "The Super Mario Galaxy Movie (2026)",
                logoUrl = "https://images.unsplash.com/photo-1566576912321-d58ddd7a6088?w=240&q=75&auto=format",
                category = "Movies & VOD",
                streamUrl = "https://test-streams.mux.dev/pts_shift/master.m3u8",
                playlistName = "Xtream VOD: ${account.name}"
            ),
            Channel(
                id = "demo_vod_7",
                name = "The Odyssey (2026)",
                logoUrl = "https://images.unsplash.com/photo-1478760329108-5c3ed9d495a0?w=240&q=75&auto=format",
                category = "Movies & VOD",
                streamUrl = "https://devstreaming-cdn.apple.com/videos/streaming/examples/bipbop_4x3/bipbop_4x3_variant.m3u8",
                playlistName = "Xtream VOD: ${account.name}"
            ),
            Channel(
                id = "demo_vod_8",
                name = "Cinema Classics - Sintel 4K",
                logoUrl = "https://images.unsplash.com/photo-1489599849927-2ee91cede3ba?w=240&q=75&auto=format",
                category = "Movies & VOD",
                streamUrl = "https://test-streams.mux.dev/test_001/stream.m3u8",
                playlistName = "Xtream VOD: ${account.name}"
            ),
            Channel(
                id = "demo_vod_9",
                name = "Big Buck Bunny Cinema",
                logoUrl = "https://images.unsplash.com/photo-1579783902614-a3fb3927b675?w=240&q=75&auto=format",
                category = "Movies & VOD",
                streamUrl = "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8",
                playlistName = "Xtream VOD: ${account.name}"
            )
        )
    }
}
