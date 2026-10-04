package com.example.data.repository

import com.example.data.local.ChannelDao
import com.example.data.local.XtreamAccountEntity
import com.example.data.model.Channel
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
        val cleanServer = serverUrl.trim().removeSuffix("/")
        val testUrl = "$cleanServer/player_api.php?username=${username.trim()}&password=${password.trim()}"

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
                    name = name.trim().ifEmpty { "Xtream Account" },
                    serverUrl = cleanServer,
                    username = username.trim(),
                    password = password.trim(),
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
                val streamIcon = obj.optString("stream_icon").takeIf { it.isNotBlank() }
                val categoryName = obj.optString("category_name").takeIf { it.isNotBlank() } ?: "Live TV"
                val streamUrl = "${account.serverUrl}/live/${account.username}/${account.password}/$streamId.m3u8"

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
                val streamIcon = obj.optString("stream_icon").takeIf { it.isNotBlank() }
                val categoryName = obj.optString("category_name").takeIf { it.isNotBlank() } ?: "VOD Movies"
                val containerExtension = obj.optString("container_extension", "mp4")
                val streamUrl = "${account.serverUrl}/movie/${account.username}/${account.password}/$streamId.$containerExtension"

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
}
