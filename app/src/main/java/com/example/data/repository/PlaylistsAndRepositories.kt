package com.example.data.repository

import com.example.data.model.Channel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

sealed class PlaylistSource(
    open val id: String,
    open val title: String,
    open val url: String,
    open val description: String
) {
    data class Preset(
        override val id: String,
        override val title: String,
        override val url: String,
        override val description: String
    ) : PlaylistSource(id, title, url, description)

    data class Custom(
        val customId: Long,
        override val title: String,
        override val url: String
    ) : PlaylistSource("custom_$customId", title, url, "Custom M3U Playlist")
}

object PresetPlaylists {
    val defaultPresets: List<PlaylistSource.Preset> = listOf(
        PlaylistSource.Preset(
            id = "preset_featured_live",
            title = "Featured Live & Cinema",
            url = "https://iptv-org.github.io/iptv/categories/movies.m3u",
            description = "Instant high-reliability HLS live streams & open cinema channels"
        ),
        PlaylistSource.Preset(
            id = "preset_news",
            title = "World News 24/7",
            url = "https://iptv-org.github.io/iptv/categories/news.m3u",
            description = "International live breaking news & financial networks"
        ),
        PlaylistSource.Preset(
            id = "preset_music",
            title = "Music & Concert TV",
            url = "https://iptv-org.github.io/iptv/categories/music.m3u",
            description = "Non-stop music videos, live concerts & chill beats"
        )
    )

    val builtInChannels: List<Channel> = listOf(
        Channel(
            id = "ch_big_buck_bunny_live",
            name = "Blender Open Cinema HD",
            streamUrl = "https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8",
            category = "Movies",
            country = "Global"
        ),
        Channel(
            id = "ch_tears_of_steel",
            name = "Sci-Fi Action Showcase",
            streamUrl = "https://demo.unified-streaming.com/k8s/features/stable/video/tears-of-steel/tears-of-steel.ism/.m3u8",
            category = "Movies",
            country = "Global"
        ),
        Channel(
            id = "ch_redbull_tv",
            name = "Red Bull TV Live",
            streamUrl = "https://rbmn-live.akamaized.net/hls/live/590964/BoRB-AT/master.m3u8",
            category = "Sports",
            country = "Austria"
        ),
        Channel(
            id = "ch_dw_english",
            name = "DW English Live News",
            streamUrl = "https://dwamdstream102.akamaized.net/hls/live/2015525/dwstream102/index.m3u8",
            category = "News",
            country = "Germany"
        ),
        Channel(
            id = "ch_france24_en",
            name = "France 24 English HD",
            streamUrl = "https://live.france24.com/hls/live/2037179-b/F24_EN_HI_HLS/master_5000.m3u8",
            category = "News",
            country = "France"
        ),
        Channel(
            id = "ch_nasa_tv",
            name = "NASA Public Space TV",
            streamUrl = "https://ntv1.akamaized.net/hls/live/2014075/NASA-NTV1-HLS/master.m3u8",
            category = "Science",
            country = "USA"
        ),
        Channel(
            id = "ch_sintel_vod",
            name = "Sintel Fantasy Feature (VOD)",
            streamUrl = "https://bitdash-a.akamaihd.net/content/sintel/hls/playlist.m3u8",
            category = "Movies",
            country = "Global"
        ),
        Channel(
            id = "ch_fmp4_bipbop",
            name = "Apple UHD Test Broadcast",
            streamUrl = "https://devstreaming-cdn.apple.com/videos/streaming/examples/img_bipbop_adv_example_fmp4/master.m3u8",
            category = "Tech",
            country = "USA"
        )
    )
}

class M3uRepository {

    suspend fun fetchChannels(source: PlaylistSource): List<Channel> = withContext(Dispatchers.IO) {
        val remoteChannels = runCatching {
            val connection = (URL(source.url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 6000
                readTimeout = 8000
                requestMethod = "GET"
                setRequestProperty("User-Agent", "HypnotixIPTV/1.1 (Android)")
            }
            if (connection.responseCode in 200..299) {
                val content = connection.inputStream.bufferedReader().use { it.readText() }
                parseM3u(content)
            } else {
                emptyList()
            }
        }.getOrDefault(emptyList())

        if (source.id == PresetPlaylists.defaultPresets.first().id) {
            (PresetPlaylists.builtInChannels + remoteChannels).distinctBy { it.streamUrl }.take(250)
        } else if (remoteChannels.isNotEmpty()) {
            remoteChannels.take(250)
        } else {
            PresetPlaylists.builtInChannels
        }
    }

    fun parseM3u(raw: String): List<Channel> {
        val lines = raw.lines()
        val result = ArrayList<Channel>()
        var currentName = ""
        var currentLogo = ""
        var currentGroup = "Live TV"
        var currentCountry = "Global"

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) continue
            if (trimmed.startsWith("#EXTINF:", ignoreCase = true)) {
                currentName = trimmed.substringAfterLast(",", "Live Channel").trim()
                currentLogo = extractAttribute(trimmed, "tvg-logo")
                currentGroup = extractAttribute(trimmed, "group-title").ifBlank { "Live TV" }
                currentCountry = extractAttribute(trimmed, "tvg-country").ifBlank { "Global" }
            } else if (!trimmed.startsWith("#") && (trimmed.startsWith("http://") || trimmed.startsWith("https://"))) {
                val name = currentName.ifBlank { "Channel ${result.size + 1}" }
                result.add(
                    Channel(
                        id = "m3u_${trimmed.hashCode()}_${result.size}",
                        name = name,
                        streamUrl = trimmed,
                        logoUrl = currentLogo,
                        category = currentGroup.split(";").firstOrNull()?.trim().orEmpty().ifBlank { "Live TV" },
                        country = currentCountry
                    )
                )
                currentName = ""
                currentLogo = ""
                currentGroup = "Live TV"
                currentCountry = "Global"
                if (result.size >= 250) break
            }
        }
        return result
    }

    private fun extractAttribute(extInfLine: String, attr: String): String {
        val marker = "$attr=\""
        val start = extInfLine.indexOf(marker, ignoreCase = true)
        if (start == -1) return ""
        val valueStart = start + marker.length
        val end = extInfLine.indexOf('"', valueStart)
        if (end == -1) return ""
        return extInfLine.substring(valueStart, end).trim()
    }
}
