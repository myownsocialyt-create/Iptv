package com.example.data.repository

import com.example.data.model.Channel
import com.example.ui.components.ChannelLogoResolver
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader

object M3uParser {
    fun parse(inputStream: InputStream): List<Channel> {
        val normalChannels = mutableListOf<Channel>()
        val lowPriorityChannels = mutableListOf<Channel>()
        val reader = BufferedReader(InputStreamReader(inputStream))

        var currentName = ""
        var currentLogo: String? = null
        var currentCategory = "General"
        var currentLanguage: String? = null
        var currentCountry: String? = null
        var currentUserAgent: String? = null
        var currentReferer: String? = null
        var isGeoBlocked = false

        var line: String? = reader.readLine()
        while (line != null) {
            val trimmed = line.trim()
            if (trimmed.startsWith("#EXTINF:", ignoreCase = true)) {
                val rawName = parseTagValue(trimmed, "tvg-name").ifBlank {
                    parseNameFromExtinf(trimmed)
                }
                isGeoBlocked = rawName.contains("[Geo-blocked]", ignoreCase = true) ||
                        rawName.contains("[Offline]", ignoreCase = true)
                currentName = cleanChannelName(rawName)

                val rawLogo = parseTagValue(trimmed, "tvg-logo")
                    .ifBlank { parseTagValue(trimmed, "logo") }
                    .ifBlank { parseTagValue(trimmed, "tvg-icon") }
                    .takeIf { it.isNotBlank() }

                currentCategory = parseTagValue(trimmed, "group-title")
                    .ifBlank { parseTagValue(trimmed, "tvg-group") }
                    .ifBlank { "General" }
                    .split(";")
                    .firstOrNull()
                    ?.trim()
                    ?.ifBlank { "General" } ?: "General"

                currentLogo = ChannelLogoResolver.resolveOptimalLogoUrl(
                    channelName = currentName,
                    rawLogoUrl = rawLogo,
                    category = currentCategory
                )

                currentLanguage = parseTagValue(trimmed, "tvg-language").takeIf { it.isNotBlank() }
                currentCountry = parseTagValue(trimmed, "tvg-country").takeIf { it.isNotBlank() }

                // Check inline header attributes if present
                val inlineUa = parseTagValue(trimmed, "user-agent").ifBlank { parseTagValue(trimmed, "http-user-agent") }
                if (inlineUa.isNotBlank()) currentUserAgent = inlineUa
                val inlineRef = parseTagValue(trimmed, "referer").ifBlank { parseTagValue(trimmed, "http-referrer") }
                if (inlineRef.isNotBlank()) currentReferer = inlineRef
            } else if (trimmed.startsWith("#EXTGRP:", ignoreCase = true)) {
                val grp = trimmed.substringAfter(":").trim()
                if (grp.isNotBlank() && currentCategory == "General") {
                    currentCategory = grp
                }
            } else if (trimmed.startsWith("#EXTVLCOPT:", ignoreCase = true)) {
                val opt = trimmed.substringAfter(":").trim()
                when {
                    opt.startsWith("http-user-agent=", ignoreCase = true) -> {
                        currentUserAgent = opt.substringAfter("=").trim().trim('"', '\'').takeIf { it.isNotBlank() }
                    }
                    opt.startsWith("http-referrer=", ignoreCase = true) ||
                    opt.startsWith("http-referer=", ignoreCase = true) ||
                    opt.startsWith("http-origin=", ignoreCase = true) -> {
                        currentReferer = opt.substringAfter("=").trim().trim('"', '\'').takeIf { it.isNotBlank() }
                    }
                }
            } else if (trimmed.isNotBlank() && !trimmed.startsWith("#")) {
                if (currentName.isNotBlank() || trimmed.startsWith("http", ignoreCase = true)) {
                    val (cleanUrl, pipeUa, pipeRef) = extractStreamUrlAndHeaders(trimmed)
                    if (cleanUrl.startsWith("http://", ignoreCase = true) || cleanUrl.startsWith("https://", ignoreCase = true)) {
                        val totalCount = normalChannels.size + lowPriorityChannels.size + 1
                        val finalName = currentName.ifBlank { "Channel $totalCount" }
                        val resolvedLogo = currentLogo ?: ChannelLogoResolver.resolveOptimalLogoUrl(
                            channelName = finalName,
                            rawLogoUrl = null,
                            category = currentCategory
                        )
                        val deterministicId = "ch_${kotlin.math.abs((finalName + "_" + cleanUrl).hashCode())}"
                        val channel = Channel(
                            id = deterministicId,
                            name = finalName,
                            logoUrl = resolvedLogo,
                            category = currentCategory,
                            language = currentLanguage,
                            country = currentCountry,
                            streamUrl = cleanUrl,
                            userAgent = pipeUa ?: currentUserAgent,
                            referer = pipeRef ?: currentReferer
                        )
                        if (isGeoBlocked) {
                            lowPriorityChannels.add(channel)
                        } else {
                            normalChannels.add(channel)
                        }
                    }
                    currentName = ""
                    currentLogo = null
                    currentCategory = "General"
                    currentLanguage = null
                    currentCountry = null
                    currentUserAgent = null
                    currentReferer = null
                    isGeoBlocked = false
                }
            }
            line = reader.readLine()
        }
        return normalChannels + lowPriorityChannels
    }

    private fun cleanChannelName(raw: String): String {
        val cleaned = raw
            .replace(Regex("\\[Geo-blocked\\]", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\[Not 24/7\\]", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\s+"), " ")
            .trim()
        return cleaned.ifBlank { raw.trim() }
    }

    private fun extractStreamUrlAndHeaders(rawLine: String): Triple<String, String?, String?> {
        val pipeIndex = rawLine.indexOf('|')
        if (pipeIndex <= 0) {
            return Triple(rawLine.trim(), null, null)
        }
        val cleanUrl = rawLine.substring(0, pipeIndex).trim()
        val headerPart = rawLine.substring(pipeIndex + 1).trim()
        var ua: String? = null
        var referer: String? = null

        val pairs = headerPart.split('&', '|')
        for (pair in pairs) {
            val eqIdx = pair.indexOf('=')
            if (eqIdx > 0) {
                val key = pair.substring(0, eqIdx).trim().lowercase()
                val value = pair.substring(eqIdx + 1).trim().trim('"', '\'')
                if (value.isNotBlank()) {
                    when (key) {
                        "user-agent", "http-user-agent" -> ua = value
                        "referer", "referrer", "http-referer", "http-referrer", "origin" -> referer = value
                    }
                }
            }
        }
        return Triple(cleanUrl, ua, referer)
    }

    private fun parseTagValue(line: String, key: String): String {
        val doubleQuotePattern = """$key\s*=\s*"([^"]*)"""".toRegex(RegexOption.IGNORE_CASE)
        doubleQuotePattern.find(line)?.groupValues?.get(1)?.trim()?.let {
            if (it.isNotEmpty()) return it
        }
        val singleQuotePattern = """$key\s*=\s*'([^']*)'""".toRegex(RegexOption.IGNORE_CASE)
        singleQuotePattern.find(line)?.groupValues?.get(1)?.trim()?.let {
            if (it.isNotEmpty()) return it
        }
        return ""
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
