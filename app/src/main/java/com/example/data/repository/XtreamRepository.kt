package com.example.data.repository

import android.util.Log
import com.example.data.local.ChannelDao
import com.example.data.local.XtreamAccountEntity
import com.example.data.model.Channel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import java.security.cert.X509Certificate

class XtreamRepository(private val channelDao: ChannelDao) {

    init {
        // Ensure lenient SSL globally
        try {
            val trustAll = arrayOf<TrustManager>(object : X509TrustManager {
                override fun checkClientTrusted(chain: Array<X509Certificate>?, authType: String?) {}
                override fun checkServerTrusted(chain: Array<X509Certificate>?, authType: String?) {}
                override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
            })
            val sslContext = SSLContext.getInstance("TLS")
            sslContext.init(null, trustAll, java.security.SecureRandom())
            HttpsURLConnection.setDefaultSSLSocketFactory(sslContext.socketFactory)
            HttpsURLConnection.setDefaultHostnameVerifier { _, _ -> true }
        } catch (_: Exception) {}
    }

    fun getAllAccounts(): Flow<List<XtreamAccountEntity>> = channelDao.getAllXtreamAccounts()

    suspend fun saveAccount(account: XtreamAccountEntity) = withContext(Dispatchers.IO) {
        channelDao.insertXtreamAccount(account)
    }

    suspend fun loginAndSaveAccount(serverUrl: String, user: String, pass: String, name: String) = withContext(Dispatchers.IO) {
        val success = authenticate(serverUrl, user, pass)
        if (!success) {
            throw Exception("Authentication failed. Please check credentials or server URL.")
        }
        val account = XtreamAccountEntity(
            id = "xtream_${System.currentTimeMillis()}",
            name = if (name.isNotBlank()) name else user,
            serverUrl = normalizeServerUrl(serverUrl),
            username = user,
            password = pass
        )
        channelDao.insertXtreamAccount(account)
    }

    suspend fun deleteAccount(id: String) = withContext(Dispatchers.IO) {
        channelDao.deleteXtreamAccount(id)
    }

    suspend fun authenticate(serverUrl: String, user: String, pass: String): Boolean = withContext(Dispatchers.IO) {
        val server = normalizeServerUrl(serverUrl)
        val testUrl = "$server/player_api.php?username=$user&password=$pass"
        try {
            val response = executeGet(testUrl)
            if (response.isBlank()) return@withContext false
            val json = JSONObject(response)
            val userInfo = json.optJSONObject("user_info")
            val status = userInfo?.optString("status") ?: ""
            val auth = userInfo?.optInt("auth", 0) ?: 0
            return@withContext status.equals("Active", ignoreCase = true) || auth == 1
        } catch (e: Exception) {
            Log.w("XtreamRepository", "Auth failed for $serverUrl: ${e.message}")
            return@withContext false
        }
    }

    suspend fun loadXtreamChannels(account: XtreamAccountEntity): List<Channel> = withContext(Dispatchers.IO) {
        val server = normalizeServerUrl(account.serverUrl)
        val user = account.username
        val pass = account.password

        val channels = mutableListOf<Channel>()
        try {
            // First load categories
            val catUrl = "$server/player_api.php?username=$user&password=$pass&action=get_live_categories"
            val catResponse = executeGet(catUrl)
            val categoriesMap = mutableMapOf<String, String>()
            if (catResponse.isNotBlank()) {
                val catArray = JSONArray(catResponse)
                for (i in 0 until catArray.length()) {
                    val catObj = catArray.getJSONObject(i)
                    categoriesMap[catObj.optString("category_id")] = catObj.optString("category_name")
                }
            }

            // Load live streams
            val streamsUrl = "$server/player_api.php?username=$user&password=$pass&action=get_live_streams"
            val streamsResponse = executeGet(streamsUrl)
            if (streamsResponse.isNotBlank()) {
                val streamsArray = JSONArray(streamsResponse)
                for (i in 0 until streamsArray.length()) {
                    val obj = streamsArray.getJSONObject(i)
                    val streamId = obj.opt("stream_id")?.toString()?.takeIf { it.isNotBlank() && it != "null" }
                        ?: obj.optString("stream_id")
                    if (streamId.isBlank()) continue
                    val name = obj.optString("name").ifBlank { "Channel $streamId" }
                    val icon = obj.optString("stream_icon").takeIf { it.isNotBlank() && it != "null" }
                    val catId = obj.optString("category_id")
                    val catName = categoriesMap[catId] ?: "Live TV"
                    val streamUrl = "$server/live/$user/$pass/$streamId.ts"

                    channels.add(
                        Channel(
                            id = "xtream_${account.id}_$streamId",
                            name = name,
                            streamUrl = streamUrl,
                            logoUrl = icon,
                            category = catName,
                            language = "Live TV",
                            country = "Live"
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.w("XtreamRepository", "Failed to load live streams: ${e.message}")
        }
        channels
    }

    suspend fun loadXtreamVod(account: XtreamAccountEntity): List<Channel> = withContext(Dispatchers.IO) {
        val server = normalizeServerUrl(account.serverUrl)
        val user = account.username
        val pass = account.password

        val movies = mutableListOf<Channel>()
        try {
            val vodCatUrl = "$server/player_api.php?username=$user&password=$pass&action=get_vod_categories"
            val catResponse = executeGet(vodCatUrl)
            val categoriesMap = mutableMapOf<String, String>()
            if (catResponse.isNotBlank()) {
                val catArray = JSONArray(catResponse)
                for (i in 0 until catArray.length()) {
                    val catObj = catArray.getJSONObject(i)
                    categoriesMap[catObj.optString("category_id")] = catObj.optString("category_name")
                }
            }

            val vodStreamsUrl = "$server/player_api.php?username=$user&password=$pass&action=get_vod_streams"
            val streamsResponse = executeGet(vodStreamsUrl)
            if (streamsResponse.isNotBlank()) {
                val streamsArray = JSONArray(streamsResponse)
                for (i in 0 until streamsArray.length()) {
                    val obj = streamsArray.getJSONObject(i)
                    val streamId = obj.opt("stream_id")?.toString()?.takeIf { it.isNotBlank() && it != "null" }
                        ?: obj.optString("stream_id")
                    if (streamId.isBlank()) continue
                    val name = obj.optString("name").ifBlank { "Movie $streamId" }
                    val icon = obj.optString("stream_icon").takeIf { it.isNotBlank() && it != "null" }
                    val catId = obj.optString("category_id")
                    val catName = categoriesMap[catId] ?: "Movies"
                    val rawExt = obj.optString("container_extension")
                    val ext = if (rawExt.isBlank() || rawExt == "null") "mp4" else rawExt.trim().lowercase().removePrefix(".")
                    val streamUrl = "$server/movie/$user/$pass/$streamId.$ext"

                    movies.add(
                        Channel(
                            id = "vod_${account.id}_$streamId",
                            name = name,
                            streamUrl = streamUrl,
                            logoUrl = icon,
                            category = catName,
                            language = "Movie",
                            country = "VOD"
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.w("XtreamRepository", "Failed to load VOD streams: ${e.message}")
        }
        movies
    }

    private fun normalizeServerUrl(rawUrl: String): String {
        val trimmed = rawUrl.trim()
        val withScheme = if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) "http://$trimmed" else trimmed
        return withScheme
            .removeSuffix("/player_api.php")
            .removeSuffix("/get.php")
            .removeSuffix("/c")
            .removeSuffix("/")
    }

    private fun executeGet(urlString: String): String {
        val url = URL(urlString)
        val conn = url.openConnection() as HttpURLConnection
        conn.connectTimeout = 12000
        conn.readTimeout = 18000
        conn.setRequestProperty("User-Agent", "IPTVSmartersPro/3.1.5 (Linux; Android 12)")
        conn.setRequestProperty("Accept", "*/*")
        conn.connect()

        if (conn.responseCode in 200..299) {
            val reader = BufferedReader(InputStreamReader(conn.inputStream))
            val sb = java.lang.StringBuilder()
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                sb.append(line)
            }
            return sb.toString()
        }
        return ""
    }
}
