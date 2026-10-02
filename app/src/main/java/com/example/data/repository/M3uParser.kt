package com.example.data.repository

import com.example.data.model.Channel
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader

object M3uParser {
    fun parse(inputStream: InputStream, defaultPlaylistName: String? = null): List<Channel> {
        val channels = mutableListOf<Channel>()
        val reader = BufferedReader(InputStreamReader(inputStream))
        var line: String?

        var currentName = ""
        var currentLogo: String? = null
        var currentCategory = "General"
        var currentLanguage: String? = null
        var currentCountry: String? = null
        var currentTvgId: String? = null
        var index = 0

        while (reader.readLine().also { line = it } != null) {
            val trimmed = line!!.trim()
            if (trimmed.isEmpty()) continue

            if (trimmed.startsWith("#EXTINF:", ignoreCase = true)) {
                // Parse attributes
                currentTvgId = extractAttribute(trimmed, "tvg-id")
                currentLogo = extractAttribute(trimmed, "tvg-logo")
                val groupTitle = extractAttribute(trimmed, "group-title")
                val tvgCountry = extractAttribute(trimmed, "tvg-country")
                val tvgLanguage = extractAttribute(trimmed, "tvg-language")

                if (!groupTitle.isNullOrBlank()) {
                    currentCategory = groupTitle.trim()
                } else {
                    currentCategory = "General"
                }

                currentCountry = tvgCountry
                currentLanguage = tvgLanguage

                val commaIndex = trimmed.lastIndexOf(',')
                if (commaIndex != -1 && commaIndex < trimmed.length - 1) {
                    currentName = trimmed.substring(commaIndex + 1).trim()
                } else {
                    currentName = extractAttribute(trimmed, "tvg-name") ?: "Channel ${index + 1}"
                }
            } else if (!trimmed.startsWith("#") && (trimmed.startsWith("http://") || trimmed.startsWith("https://") || trimmed.startsWith("rtmp://"))) {
                if (currentName.isBlank()) {
                    currentName = "Channel ${index + 1}"
                }

                val channel = Channel(
                    id = "ch_${index}_${trimmed.hashCode()}",
                    name = currentName,
                    streamUrl = trimmed,
                    logoUrl = currentLogo,
                    category = currentCategory,
                    language = currentLanguage,
                    country = currentCountry,
                    tvgId = currentTvgId,
                    playlistName = defaultPlaylistName
                )
                channels.add(channel)
                index++

                // Reset temporary fields
                currentName = ""
                currentLogo = null
                currentCategory = "General"
                currentLanguage = null
                currentCountry = null
                currentTvgId = null
            }
        }
        return channels
    }

    private fun extractAttribute(line: String, attributeName: String): String? {
        val pattern = """$attributeName="([^"]*)"""".toRegex(RegexOption.IGNORE_CASE)
        val match = pattern.find(line)
        return match?.groups?.get(1)?.value?.takeIf { it.isNotBlank() }
    }
}
