package com.example.data.local

import android.content.Context
import com.example.data.model.AppLanguage
import com.example.data.model.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

data class CustomPlaylistEntity(
    val id: Long,
    val name: String,
    val url: String
)

class OnboardingPreferences(context: Context) {
    private val prefs = context.getSharedPreferences("hypnotix_prefs", Context.MODE_PRIVATE)

    fun isDarkTheme(): Boolean = prefs.getBoolean("dark_theme", true)

    fun setDarkTheme(isDark: Boolean) {
        prefs.edit().putBoolean("dark_theme", isDark).apply()
    }

    fun getLanguage(): AppLanguage {
        val code = prefs.getString("app_language", AppLanguage.ENGLISH.code) ?: AppLanguage.ENGLISH.code
        return AppLanguage.entries.firstOrNull { it.code == code } ?: AppLanguage.ENGLISH
    }

    fun setLanguage(language: AppLanguage) {
        prefs.edit().putString("app_language", language.code).apply()
    }
}

class LocalChannelStore(context: Context) {
    private val prefs = context.getSharedPreferences("hypnotix_local_store", Context.MODE_PRIVATE)

    private val _favoriteIds = MutableStateFlow(loadStringSet("favorite_ids"))
    val favoriteIds: StateFlow<Set<String>> = _favoriteIds.asStateFlow()

    private val _recents = MutableStateFlow(loadRecents())
    val recents: StateFlow<List<Channel>> = _recents.asStateFlow()

    private val _customPlaylists = MutableStateFlow(loadCustomPlaylists())
    val customPlaylists: StateFlow<List<CustomPlaylistEntity>> = _customPlaylists.asStateFlow()

    fun toggleFavorite(channelId: String) {
        val updated = _favoriteIds.value.toMutableSet()
        if (!updated.add(channelId)) {
            updated.remove(channelId)
        }
        prefs.edit().putStringSet("favorite_ids", updated).apply()
        _favoriteIds.value = updated
    }

    fun recordRecent(channel: Channel) {
        val current = _recents.value.filterNot { it.id == channel.id }.toMutableList()
        current.add(0, channel)
        val trimmed = current.take(15)
        saveRecents(trimmed)
        _recents.value = trimmed
    }

    fun addCustomPlaylist(name: String, url: String): CustomPlaylistEntity {
        val entity = CustomPlaylistEntity(
            id = System.currentTimeMillis(),
            name = name.trim(),
            url = url.trim()
        )
        val updated = _customPlaylists.value + entity
        saveCustomPlaylists(updated)
        _customPlaylists.value = updated
        return entity
    }

    fun deleteCustomPlaylist(id: Long) {
        val updated = _customPlaylists.value.filterNot { it.id == id }
        saveCustomPlaylists(updated)
        _customPlaylists.value = updated
    }

    private fun loadStringSet(key: String): Set<String> {
        return prefs.getStringSet(key, emptySet())?.toSet() ?: emptySet()
    }

    private fun loadRecents(): List<Channel> {
        val raw = prefs.getString("recent_channels_json", null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    add(
                        Channel(
                            id = obj.optString("id"),
                            name = obj.optString("name"),
                            streamUrl = obj.optString("streamUrl"),
                            logoUrl = obj.optString("logoUrl"),
                            category = obj.optString("category", "General"),
                            country = obj.optString("country", "Global")
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun saveRecents(channels: List<Channel>) {
        val array = JSONArray()
        channels.forEach { ch ->
            val obj = JSONObject().apply {
                put("id", ch.id)
                put("name", ch.name)
                put("streamUrl", ch.streamUrl)
                put("logoUrl", ch.logoUrl)
                put("category", ch.category)
                put("country", ch.country)
            }
            array.put(obj)
        }
        prefs.edit().putString("recent_channels_json", array.toString()).apply()
    }

    private fun loadCustomPlaylists(): List<CustomPlaylistEntity> {
        val raw = prefs.getString("custom_playlists_json", null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    add(
                        CustomPlaylistEntity(
                            id = obj.optLong("id"),
                            name = obj.optString("name"),
                            url = obj.optString("url")
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun saveCustomPlaylists(items: List<CustomPlaylistEntity>) {
        val array = JSONArray()
        items.forEach { item ->
            val obj = JSONObject().apply {
                put("id", item.id)
                put("name", item.name)
                put("url", item.url)
            }
            array.put(obj)
        }
        prefs.edit().putString("custom_playlists_json", array.toString()).apply()
    }
}
