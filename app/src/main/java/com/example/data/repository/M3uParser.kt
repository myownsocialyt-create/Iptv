package com.example.data.repository

import com.example.data.model.Channel
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.util.UUID

object M3uParser {
    fun parse(inputStream: InputStream): List<Channel> {
        val channels = mutableListOf<Channel>()
        val reader = BufferedReader(InputStreamReader(inputStream))

        var currentName = ""
        var currentLogo: String? = null
        var currentCategory = "General"
        var currentLanguage: String? = null
        var currentCountry: String? = null

        var line: String? = reader.readLine()
        while (line != null) {
            val trimmed = line.trim()
            if (trimmed.startsWith("#EXTINF:", ignoreCase = true)) {
                currentName = parseTagValue(trimmed, "tvg-name").ifBlank {
                    parseNameFromExtinf(trimmed)
                }
                currentLogo = parseTagValue(trimmed, "tvg-logo").takeIf { it.isNotBlank() }
                currentCategory = parseTagValue(trimmed, "group-title").ifBlank { "General" }
                currentLanguage = parseTagValue(trimmed, "tvg-language").takeIf { it.isNotBlank() }
                currentCountry = parseTagValue(trimmed, "tvg-country").takeIf { it.isNotBlank() }
            } else if (trimmed.isNotBlank() && !trimmed.startsWith("#")) {
                if (currentName.isNotBlank() || trimmed.startsWith("http", ignoreCase = true)) {
                    val finalName = currentName.ifBlank { "Channel ${channels.size + 1}" }
                    channels.add(
                        Channel(
                            id = UUID.randomUUID().toString(),
                            name = finalName,
                            logoUrl = currentLogo,
                            category = currentCategory,
                            language = currentLanguage,
                            country = currentCountry,
                            streamUrl = trimmed
                        )
                    )
                    currentName = ""
                    currentLogo = null
                    currentCategory = "General"
                    currentLanguage = null
                    currentCountry = null
                }
            }
            line = reader.readLine()
        }
        return channels
    }

    private fun parseTagValue(line: String, key: String): String {
        val pattern = """$key="([^"]*)"""".toRegex(RegexOption.IGNORE_CASE)
        val match = pattern.find(line)
        return match?.groupValues?.get(1)?.trim() ?: ""
    }

    private fun parseNameFromExtinf(line: String): String {
        val commaIndex = line.lastIndexOf(',')
        return if (commaIndex != -1 && commaIndex < line.length - 1) {
            line.substring(commaIndex + 1).trim()
        } else {
            ""
        }
    }
}
