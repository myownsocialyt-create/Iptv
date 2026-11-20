package com.example.epg

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import org.xml.sax.Attributes
import org.xml.sax.helpers.DefaultHandler
import java.io.BufferedInputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Base64
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.GZIPInputStream
import javax.xml.parsers.SAXParserFactory

data class EpgProgram(
    val title: String,
    val description: String,
    val startTimeMs: Long,
    val endTimeMs: Long
) {
    fun isCurrentlyAiring(nowMs: Long): Boolean = nowMs in startTimeMs until endTimeMs

    fun isUpcoming(nowMs: Long): Boolean = startTimeMs > nowMs

    fun progressFraction(nowMs: Long): Float {
        val duration = (endTimeMs - startTimeMs).coerceAtLeast(1L)
        return ((nowMs - startTimeMs).toFloat() / duration.toFloat()).coerceIn(0f, 1f)
    }
}

sealed class EpgUiState {
    data object Loading : EpgUiState()
    data class Available(
        val current: EpgProgram?,
        val upcoming: List<EpgProgram>,
        val sourceLabel: String
    ) : EpgUiState()

    data class Unavailable(
        val message: String = "No upcoming details available",
        val canConfigureEpgUrl: Boolean = false
    ) : EpgUiState()
}

data class XtreamEpgTarget(
    val serverBaseUrl: String,
    val username: String,
    val password: String,
    val streamId: String
)

object LiveEpgManager {
    private const val TAG = "LiveEpgManager"
    private const val PREFS_NAME = "hypnotix_live_epg_prefs"
    private const val KEY_CUSTOM_EPG_URL = "custom_epg_url"

    private const val CHANNEL_CACHE_TTL_MS = 10L * 60L * 1000L // 10 minutes
    private const val M3U_HEADER_CACHE_TTL_MS = 2L * 60L * 60L * 1000L // 2 hours
    private const val XMLTV_CACHE_TTL_MS = 30L * 60L * 1000L // 30 minutes
    private const val MAX_XMLTV_BYTES = 18 * 1024 * 1024 // 18 MB uncompressed safety limit
    private const val MAX_UPCOMING_ITEMS = 12

    private data class CachedChannelEpg(
        val fetchedAtMs: Long,
        val state: EpgUiState
    )

    private data class CachedHeaderEpgUrl(
        val fetchedAtMs: Long,
        val epgUrl: String?
    )

    internal data class XmltvIndex(
        val fetchedAtMs: Long,
        val programsByChannelId: Map<String, List<EpgProgram>>,
        val normalizedNameToChannelIds: Map<String, Set<String>>
    )

    private val channelCache = ConcurrentHashMap<String, CachedChannelEpg>()
    private val m3uHeaderCache = ConcurrentHashMap<String, CachedHeaderEpgUrl>()
    private val xmltvIndexCache = ConcurrentHashMap<String, XmltvIndex>()

    fun getCustomEpgUrl(context: Context): String {
        return try {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(KEY_CUSTOM_EPG_URL, "")
                ?.trim()
                .orEmpty()
        } catch (_: Throwable) {
            ""
        }
    }

    fun saveCustomEpgUrl(context: Context, url: String) {
        val cleaned = url.trim()
        try {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_CUSTOM_EPG_URL, cleaned)
                .apply()
        } catch (_: Throwable) {
        }
        channelCache.clear()
        xmltvIndexCache.clear()
    }

    fun clearCacheForChannel(channelKey: String) {
        channelCache.remove(channelKey)
    }

    suspend fun loadEpgForLiveChannel(
        context: Context,
        channelId: String,
        channelName: String,
        streamUrl: String,
        isXtreamMode: Boolean,
        playlistUrl: String?,
        viewModel: Any?,
        forceRefresh: Boolean = false
    ): EpgUiState {
        val cacheKey = "${isXtreamMode}|${channelId}|${streamUrl}|${channelName}"
        val nowMs = System.currentTimeMillis()

        if (!forceRefresh) {
            val cached = channelCache[cacheKey]
            if (cached != null && (nowMs - cached.fetchedAtMs) < CHANNEL_CACHE_TTL_MS) {
                return cached.state
            }
        } else {
            channelCache.remove(cacheKey)
        }

        val result = withContext(Dispatchers.IO) {
            try {
                // 1. Try Xtream EPG API whenever credentials can be resolved (Xtream mode OR Xtream stream URL)
                val xtreamTarget = resolveXtreamTarget(
                    streamUrl = streamUrl,
                    channelId = channelId,
                    isXtreamMode = isXtreamMode,
                    viewModel = viewModel
                )
                if (xtreamTarget != null) {
                    val xtreamState = fetchXtreamShortEpg(xtreamTarget, nowMs)
                    if (xtreamState is EpgUiState.Available) {
                        return@withContext xtreamState
                    }
                    if (isXtreamMode) {
                        return@withContext EpgUiState.Unavailable(
                            message = "No upcoming details available",
                            canConfigureEpgUrl = false
                        )
                    }
                }

                // 2. For M3U playlist live channels: try custom EPG URL or playlist's url-tvg XMLTV feed
                val customEpgUrl = getCustomEpgUrl(context)
                val candidateEpgUrls = linkedSetOf<String>()
                if (customEpgUrl.startsWith("http://", ignoreCase = true) ||
                    customEpgUrl.startsWith("https://", ignoreCase = true)
                ) {
                    candidateEpgUrls.add(customEpgUrl)
                }

                val cleanPlaylistUrl = playlistUrl?.trim().orEmpty()
                if (cleanPlaylistUrl.startsWith("http://", ignoreCase = true) ||
                    cleanPlaylistUrl.startsWith("https://", ignoreCase = true)
                ) {
                    val headerEpgUrl = resolveM3uHeaderEpgUrl(cleanPlaylistUrl, nowMs, forceRefresh)
                    if (!headerEpgUrl.isNullOrBlank()) {
                        headerEpgUrl.split(',').forEach { part ->
                            val u = part.trim()
                            if (u.startsWith("http://", ignoreCase = true) ||
                                u.startsWith("https://", ignoreCase = true)
                            ) {
                                candidateEpgUrls.add(u)
                            }
                        }
                    }
                }

                for (epgUrl in candidateEpgUrls) {
                    val xmltvIndex = fetchOrGetXmltvIndex(epgUrl, nowMs, forceRefresh) ?: continue
                    val matched = lookupXmltvChannel(
                        index = xmltvIndex,
                        channelId = channelId,
                        channelName = channelName,
                        nowMs = nowMs
                    )
                    if (matched != null) {
                        return@withContext matched
                    }
                }

                EpgUiState.Unavailable(
                    message = "No upcoming details available",
                    canConfigureEpgUrl = !isXtreamMode
                )
            } catch (t: Throwable) {
                Log.w(TAG, "EPG load failed for $channelName", t)
                EpgUiState.Unavailable(
                    message = "No upcoming details available",
                    canConfigureEpgUrl = !isXtreamMode
                )
            }
        }

        channelCache[cacheKey] = CachedChannelEpg(System.currentTimeMillis(), result)
        return result
    }

    private fun resolveXtreamTarget(
        streamUrl: String,
        channelId: String,
        isXtreamMode: Boolean,
        viewModel: Any?
    ): XtreamEpgTarget? {
        parseXtreamCredentialsFromUrl(streamUrl, channelId)?.let { return it }
        if (!isXtreamMode || viewModel == null) return null

        val account = extractActiveXtreamAccount(viewModel) ?: return null
        val server = invokeStringGetter(account, "getServerUrl").trim().trimEnd('/')
        val user = invokeStringGetter(account, "getUsername").trim()
        val pass = invokeStringGetter(account, "getPassword").trim()
        val streamId = extractNumericStreamId(channelId, streamUrl)
        if (server.isEmpty() || user.isEmpty() || pass.isEmpty() || streamId.isEmpty()) {
            return null
        }
        return XtreamEpgTarget(server, user, pass, streamId)
    }

    internal fun parseXtreamCredentialsFromUrl(streamUrl: String, channelId: String = ""): XtreamEpgTarget? {
        val raw = streamUrl.trim()
        if (!raw.startsWith("http://", ignoreCase = true) && !raw.startsWith("https://", ignoreCase = true)) {
            return null
        }
        return try {
            val parsed = URL(raw)
            val portPart = if (parsed.port != -1) ":${parsed.port}" else ""
            val base = "${parsed.protocol}://${parsed.host}$portPart"
            val segments = parsed.path.split('/').filter { it.isNotBlank() }

            // Pattern 1: /live/{user}/{pass}/{streamId}(.ts|.m3u8)
            if (segments.size >= 4 && segments[0].equals("live", ignoreCase = true)) {
                val user = segments[1]
                val pass = segments[2]
                val idPart = segments[3].substringBefore('.').trim()
                if (idPart.all { it.isDigit() } && idPart.isNotEmpty()) {
                    return XtreamEpgTarget(base, user, pass, idPart)
                }
            }

            // Pattern 2: /{user}/{pass}/{streamId}(.ts|.m3u8) (excluding /movie/ or /series/)
            if (segments.size == 3 &&
                !segments[0].equals("movie", ignoreCase = true) &&
                !segments[0].equals("series", ignoreCase = true) &&
                !segments[0].equals("vod", ignoreCase = true)
            ) {
                val user = segments[0]
                val pass = segments[1]
                val idPart = segments[2].substringBefore('.').trim()
                if (idPart.all { it.isDigit() } && idPart.isNotEmpty()) {
                    return XtreamEpgTarget(base, user, pass, idPart)
                }
            }

            // Fallback if channelId carries numeric stream id and path has live/user/pass
            val fallbackId = extractNumericStreamId(channelId, raw)
            if (segments.size >= 3 && segments[0].equals("live", ignoreCase = true) && fallbackId.isNotEmpty()) {
                return XtreamEpgTarget(base, segments[1], segments[2], fallbackId)
            }

            null
        } catch (_: Throwable) {
            null
        }
    }

    internal fun extractNumericStreamId(channelId: String, streamUrl: String): String {
        val cleanId = channelId.trim()
        val idMatch = Regex("(?:^|_)(\\d+)$").find(cleanId)
        if (idMatch != null) return idMatch.groupValues[1]

        val pathPart = streamUrl.substringBefore('?').substringAfterLast('/').substringBefore('.')
        if (pathPart.isNotEmpty() && pathPart.all { it.isDigit() }) {
            return pathPart
        }
        return ""
    }

    private fun fetchXtreamShortEpg(target: XtreamEpgTarget, nowMs: Long): EpgUiState {
        val encUser = URLEncoder.encode(target.username, "UTF-8")
        val encPass = URLEncoder.encode(target.password, "UTF-8")
        val encId = URLEncoder.encode(target.streamId, "UTF-8")

        val shortUrl = "${target.serverBaseUrl}/player_api.php?username=$encUser&password=$encPass&action=get_short_epg&stream_id=$encId&limit=$MAX_UPCOMING_ITEMS"
        val shortBody = httpGetText(shortUrl, timeoutMs = 8000)
        if (!shortBody.isNullOrBlank()) {
            val parsed = parseXtreamEpgJson(shortBody, nowMs)
            if (parsed != null) return parsed
        }

        val tableUrl = "${target.serverBaseUrl}/player_api.php?username=$encUser&password=$encPass&action=get_simple_data_table&stream_id=$encId"
        val tableBody = httpGetText(tableUrl, timeoutMs = 8000)
        if (!tableBody.isNullOrBlank()) {
            val parsed = parseXtreamEpgJson(tableBody, nowMs)
            if (parsed != null) return parsed
        }

        return EpgUiState.Unavailable(
            message = "No upcoming details available",
            canConfigureEpgUrl = false
        )
    }

    internal fun parseXtreamEpgJson(jsonText: String, nowMs: Long): EpgUiState.Available? {
        return try {
            val trimmed = jsonText.trim()
            if (trimmed.isEmpty()) return null

            val arraySlice = extractListingsArraySlice(trimmed) ?: return null
            val objects = splitTopLevelJsonObjects(arraySlice)
            if (objects.isEmpty()) return null

            val parsedPrograms = mutableListOf<EpgProgram>()
            for (objStr in objects) {
                val fields = parseFlatJsonObject(objStr)
                val rawTitle = fields["title"].orEmpty().trim()
                val title = decodeXtreamText(rawTitle).trim()
                if (title.isEmpty()) continue

                val rawDesc = fields["description"].orEmpty().trim()
                val desc = decodeXtreamText(rawDesc).trim()

                val startMs = parseXtreamTimestampMs(
                    epochStr = fields["start_timestamp"].orEmpty(),
                    dateStr = fields["start"].orEmpty()
                )
                val endMs = parseXtreamTimestampMs(
                    epochStr = fields["stop_timestamp"].orEmpty(),
                    dateStr = fields["end"].orEmpty()
                )

                if (startMs <= 0L || endMs <= startMs) continue
                if (endMs <= nowMs) continue

                parsedPrograms.add(
                    EpgProgram(
                        title = title,
                        description = desc,
                        startTimeMs = startMs,
                        endTimeMs = endMs
                    )
                )
            }

            buildAvailableState(parsedPrograms, nowMs, "Xtream EPG")
        } catch (_: Throwable) {
            null
        }
    }

    private fun extractListingsArraySlice(json: String): String? {
        if (json.startsWith("[")) return json
        val keyIdx = json.indexOf("\"epg_listings\"")
        if (keyIdx == -1) return null
        val bracketIdx = json.indexOf('[', keyIdx)
        if (bracketIdx == -1) return null

        var depth = 0
        var inString = false
        var escaped = false
        for (i in bracketIdx until json.length) {
            val c = json[i]
            if (inString) {
                if (escaped) {
                    escaped = false
                } else if (c == '\\') {
                    escaped = true
                } else if (c == '"') {
                    inString = false
                }
            } else {
                when (c) {
                    '"' -> inString = true
                    '[' -> depth++
                    ']' -> {
                        depth--
                        if (depth == 0) return json.substring(bracketIdx, i + 1)
                    }
                }
            }
        }
        return null
    }

    private fun splitTopLevelJsonObjects(arrayJson: String): List<String> {
        val result = mutableListOf<String>()
        var objStart = -1
        var depth = 0
        var inString = false
        var escaped = false
        for (i in arrayJson.indices) {
            val c = arrayJson[i]
            if (inString) {
                if (escaped) {
                    escaped = false
                } else if (c == '\\') {
                    escaped = true
                } else if (c == '"') {
                    inString = false
                }
            } else {
                when (c) {
                    '"' -> inString = true
                    '{' -> {
                        if (depth == 0) objStart = i
                        depth++
                    }
                    '}' -> {
                        depth--
                        if (depth == 0 && objStart != -1) {
                            result.add(arrayJson.substring(objStart, i + 1))
                            objStart = -1
                        }
                    }
                }
            }
        }
        return result
    }

    private fun parseFlatJsonObject(obj: String): Map<String, String> {
        val map = HashMap<String, String>()
        var i = 0
        val n = obj.length
        while (i < n) {
            val keyStart = obj.indexOf('"', i)
            if (keyStart == -1) break
            val (key, afterKey) = readJsonString(obj, keyStart) ?: break
            var j = afterKey
            while (j < n && obj[j].isWhitespace()) j++
            if (j >= n || obj[j] != ':') {
                i = j + 1
                continue
            }
            j++
            while (j < n && obj[j].isWhitespace()) j++
            if (j >= n) break

            when (obj[j]) {
                '"' -> {
                    val (value, afterVal) = readJsonString(obj, j) ?: break
                    map[key] = value
                    i = afterVal
                }
                '{', '[' -> {
                    // Skip nested structure
                    val open = obj[j]
                    val close = if (open == '{') '}' else ']'
                    var d = 0
                    var inStr = false
                    var esc = false
                    while (j < n) {
                        val c = obj[j]
                        if (inStr) {
                            if (esc) esc = false
                            else if (c == '\\') esc = true
                            else if (c == '"') inStr = false
                        } else {
                            if (c == '"') inStr = true
                            else if (c == open) d++
                            else if (c == close) {
                                d--
                                if (d == 0) {
                                    j++
                                    break
                                }
                            }
                        }
                        j++
                    }
                    i = j
                }
                else -> {
                    val valStart = j
                    while (j < n && obj[j] != ',' && obj[j] != '}' && !obj[j].isWhitespace()) j++
                    val rawVal = obj.substring(valStart, j)
                    if (rawVal != "null") map[key] = rawVal
                    i = j
                }
            }
        }
        return map
    }

    private fun readJsonString(src: String, startQuoteIdx: Int): Pair<String, Int>? {
        val sb = StringBuilder()
        var i = startQuoteIdx + 1
        val n = src.length
        while (i < n) {
            val c = src[i]
            if (c == '\\' && i + 1 < n) {
                val next = src[i + 1]
                when (next) {
                    '"' -> { sb.append('"'); i += 2 }
                    '\\' -> { sb.append('\\'); i += 2 }
                    '/' -> { sb.append('/'); i += 2 }
                    'n' -> { sb.append('\n'); i += 2 }
                    'r' -> { sb.append('\r'); i += 2 }
                    't' -> { sb.append('\t'); i += 2 }
                    'u' -> {
                        if (i + 5 < n) {
                            val hex = src.substring(i + 2, i + 6)
                            val code = hex.toIntOrNull(16)
                            if (code != null) sb.append(code.toChar())
                            i += 6
                        } else {
                            i += 2
                        }
                    }
                    else -> { sb.append(next); i += 2 }
                }
            } else if (c == '"') {
                return sb.toString() to (i + 1)
            } else {
                sb.append(c)
                i++
            }
        }
        return null
    }

    internal fun decodeXtreamText(raw: String): String {
        if (raw.isBlank()) return ""
        val trimmed = raw.trim()
        // Xtream API normally sends Base64-encoded UTF-8 titles/descriptions.
        val looksLikeBase64 = trimmed.length >= 4 &&
            trimmed.length % 4 == 0 &&
            Regex("^[A-Za-z0-9+/=\\r\\n]+$").matches(trimmed) &&
            !trimmed.contains(' ')
        if (!looksLikeBase64) return trimmed

        return try {
            val bytes = Base64.getMimeDecoder().decode(trimmed)
            val decoded = String(bytes, Charsets.UTF_8).trim()
            if (decoded.isEmpty()) return trimmed
            // Reject binary garbage if string wasn't actually Base64 (e.g. a 4-letter English word like "News")
            val controlCount = decoded.count { ch ->
                ch.code < 32 && ch != '\n' && ch != '\r' && ch != '\t'
            }
            val replacementCount = decoded.count { it == '\uFFFD' }
            if (controlCount == 0 && replacementCount == 0) decoded else trimmed
        } catch (_: Throwable) {
            trimmed
        }
    }

    private fun parseXtreamTimestampMs(epochStr: String, dateStr: String): Long {
        val cleanEpoch = epochStr.trim()
        if (cleanEpoch.isNotEmpty()) {
            val epochNum = cleanEpoch.toLongOrNull()
            if (epochNum != null && epochNum > 0L) {
                return if (epochNum < 10_000_000_000L) epochNum * 1000L else epochNum
            }
        }
        val cleanDate = dateStr.trim()
        if (cleanDate.isNotEmpty()) {
            val patterns = arrayOf("yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd'T'HH:mm:ss")
            for (pattern in patterns) {
                try {
                    val sdf = SimpleDateFormat(pattern, Locale.US).apply {
                        timeZone = TimeZone.getDefault()
                    }
                    val parsed = sdf.parse(cleanDate)
                    if (parsed != null) return parsed.time
                } catch (_: Throwable) {
                }
            }
        }
        return 0L
    }

    private fun resolveM3uHeaderEpgUrl(
        playlistUrl: String,
        nowMs: Long,
        forceRefresh: Boolean
    ): String? {
        if (!forceRefresh) {
            val cached = m3uHeaderCache[playlistUrl]
            if (cached != null && (nowMs - cached.fetchedAtMs) < M3U_HEADER_CACHE_TTL_MS) {
                return cached.epgUrl
            }
        }
        val header = httpGetPrefix(playlistUrl, maxBytes = 4096, timeoutMs = 5000)
        val epgUrl = header?.let { extractEpgUrlFromM3uHeader(it) }
        m3uHeaderCache[playlistUrl] = CachedHeaderEpgUrl(nowMs, epgUrl)
        return epgUrl
    }

    internal fun extractEpgUrlFromM3uHeader(headerText: String): String? {
        val firstLines = headerText.lineSequence().take(5).joinToString(" ")
        if (!firstLines.contains("#EXTM3U", ignoreCase = true)) return null
        val regex = Regex("""(?:x-tvg-url|url-tvg)\s*=\s*"([^"]+)"""", RegexOption.IGNORE_CASE)
        val quoted = regex.find(firstLines)?.groupValues?.getOrNull(1)?.trim()
        if (!quoted.isNullOrBlank()) return quoted

        val unquotedRegex = Regex("""(?:x-tvg-url|url-tvg)\s*=\s*(\S+)""", RegexOption.IGNORE_CASE)
        return unquotedRegex.find(firstLines)?.groupValues?.getOrNull(1)?.trim()?.trim('"', '\'')
    }

    private fun fetchOrGetXmltvIndex(
        epgUrl: String,
        nowMs: Long,
        forceRefresh: Boolean
    ): XmltvIndex? {
        if (!forceRefresh) {
            val cached = xmltvIndexCache[epgUrl]
            if (cached != null && (nowMs - cached.fetchedAtMs) < XMLTV_CACHE_TTL_MS) {
                return cached
            }
        }

        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(epgUrl).openConnection() as HttpURLConnection).apply {
                connectTimeout = 9000
                readTimeout = 12000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "Mozilla/5.0 (Android; IPTV)")
                setRequestProperty("Accept-Encoding", "gzip")
            }
            if (conn.responseCode !in 200..299) return null

            val rawStream = BufferedInputStream(conn.inputStream)
            rawStream.mark(4)
            val b0 = rawStream.read()
            val b1 = rawStream.read()
            rawStream.reset()

            val isGzip = (b0 == 0x1f && b1 == 0x8b) ||
                epgUrl.lowercase(Locale.ROOT).endsWith(".gz") ||
                conn.contentEncoding.equals("gzip", ignoreCase = true)

            val stream: InputStream = if (isGzip) GZIPInputStream(rawStream) else rawStream
            val limitedStream = ByteLimitedInputStream(stream, MAX_XMLTV_BYTES)
            val parsed = limitedStream.use { parseXmltvStream(it, nowMs) }
            xmltvIndexCache[epgUrl] = parsed
            parsed
        } catch (t: Throwable) {
            Log.w(TAG, "XMLTV fetch/parse failed for $epgUrl", t)
            null
        } finally {
            conn?.disconnect()
        }
    }

    internal fun parseXmltvStream(input: InputStream, nowMs: Long): XmltvIndex {
        val windowEndMs = nowMs + 24L * 60L * 60L * 1000L
        val programsByChannelId = HashMap<String, MutableList<EpgProgram>>()
        val normalizedNameToIds = HashMap<String, MutableSet<String>>()

        val factory = SAXParserFactory.newInstance().apply {
            isNamespaceAware = false
        }
        val saxParser = factory.newSAXParser()

        val handler = object : DefaultHandler() {
            private var currentChannelTagId: String? = null
            private var progChannelId: String? = null
            private var progStartMs = 0L
            private var progEndMs = 0L
            private var progTitle: String? = null
            private var progDesc: String? = null
            private var activeTextTag: String? = null
            private val textBuffer = StringBuilder()

            override fun startElement(
                uri: String?,
                localName: String?,
                qName: String?,
                attributes: Attributes?
            ) {
                val tag = (qName ?: localName).orEmpty().lowercase(Locale.ROOT)
                when (tag) {
                    "channel" -> {
                        currentChannelTagId = attributes?.getValue("id")?.trim()
                        val chId = currentChannelTagId
                        if (!chId.isNullOrEmpty()) {
                            val normId = normalizeChannelKey(chId)
                            if (normId.isNotEmpty()) {
                                normalizedNameToIds.getOrPut(normId) { linkedSetOf() }
                                    .add(chId.lowercase(Locale.ROOT))
                            }
                        }
                    }
                    "display-name" -> {
                        if (currentChannelTagId != null) {
                            activeTextTag = "display-name"
                            textBuffer.setLength(0)
                        }
                    }
                    "programme" -> {
                        val chId = attributes?.getValue("channel")?.trim()
                        val startRaw = attributes?.getValue("start")?.trim().orEmpty()
                        val stopRaw = attributes?.getValue("stop")?.trim().orEmpty()
                        val startMs = parseXmltvTimestamp(startRaw)
                        val stopMs = parseXmltvTimestamp(stopRaw)
                        if (!chId.isNullOrEmpty() && startMs > 0L && stopMs > startMs &&
                            stopMs > nowMs && startMs <= windowEndMs
                        ) {
                            progChannelId = chId.lowercase(Locale.ROOT)
                            progStartMs = startMs
                            progEndMs = stopMs
                            progTitle = null
                            progDesc = null
                        } else {
                            progChannelId = null
                        }
                    }
                    "title" -> {
                        if (progChannelId != null) {
                            activeTextTag = "title"
                            textBuffer.setLength(0)
                        }
                    }
                    "desc" -> {
                        if (progChannelId != null) {
                            activeTextTag = "desc"
                            textBuffer.setLength(0)
                        }
                    }
                }
            }

            override fun characters(ch: CharArray?, start: Int, length: Int) {
                if (activeTextTag != null && ch != null && length > 0) {
                    textBuffer.append(ch, start, length)
                }
            }

            override fun endElement(uri: String?, localName: String?, qName: String?) {
                val tag = (qName ?: localName).orEmpty().lowercase(Locale.ROOT)
                when (tag) {
                    "display-name" -> {
                        val text = textBuffer.toString().trim()
                        val chId = currentChannelTagId?.lowercase(Locale.ROOT)
                        if (chId != null && text.isNotEmpty()) {
                            val norm = normalizeChannelKey(text)
                            if (norm.isNotEmpty()) {
                                normalizedNameToIds.getOrPut(norm) { linkedSetOf() }.add(chId)
                            }
                        }
                        activeTextTag = null
                        textBuffer.setLength(0)
                    }
                    "title" -> {
                        val text = textBuffer.toString().trim()
                        if (progTitle == null && text.isNotEmpty()) {
                            progTitle = text
                        }
                        activeTextTag = null
                        textBuffer.setLength(0)
                    }
                    "desc" -> {
                        val text = textBuffer.toString().trim()
                        if (progDesc == null && text.isNotEmpty()) {
                            progDesc = text
                        }
                        activeTextTag = null
                        textBuffer.setLength(0)
                    }
                    "channel" -> {
                        currentChannelTagId = null
                    }
                    "programme" -> {
                        val chId = progChannelId
                        val title = progTitle
                        if (chId != null && !title.isNullOrBlank()) {
                            programsByChannelId.getOrPut(chId) { mutableListOf() }.add(
                                EpgProgram(
                                    title = title,
                                    description = progDesc.orEmpty(),
                                    startTimeMs = progStartMs,
                                    endTimeMs = progEndMs
                                )
                            )
                        }
                        progChannelId = null
                        progTitle = null
                        progDesc = null
                    }
                }
            }
        }

        saxParser.parse(input, handler)

        return XmltvIndex(
            fetchedAtMs = nowMs,
            programsByChannelId = programsByChannelId,
            normalizedNameToChannelIds = normalizedNameToIds
        )
    }

    internal fun lookupXmltvChannel(
        index: XmltvIndex,
        channelId: String,
        channelName: String,
        nowMs: Long
    ): EpgUiState.Available? {
        val candidateIds = linkedSetOf<String>()
        val lowerId = channelId.trim().lowercase(Locale.ROOT)
        if (lowerId.isNotEmpty()) {
            candidateIds.add(lowerId)
            val normId = normalizeChannelKey(lowerId)
            index.normalizedNameToChannelIds[normId]?.let { candidateIds.addAll(it) }
        }

        val normName = normalizeChannelKey(channelName)
        if (normName.isNotEmpty()) {
            index.normalizedNameToChannelIds[normName]?.let { candidateIds.addAll(it) }
            // Also check direct key match if channel id in XMLTV equals channel name
            if (index.programsByChannelId.containsKey(normName)) {
                candidateIds.add(normName)
            }
        }

        for (id in candidateIds) {
            val programs = index.programsByChannelId[id] ?: continue
            val state = buildAvailableState(programs, nowMs, "Playlist EPG")
            if (state != null) return state
        }
        return null
    }

    private fun buildAvailableState(
        rawPrograms: List<EpgProgram>,
        nowMs: Long,
        sourceLabel: String
    ): EpgUiState.Available? {
        val sorted = rawPrograms
            .filter { it.endTimeMs > nowMs && it.title.isNotBlank() }
            .distinctBy { "${it.startTimeMs}|${it.title}" }
            .sortedBy { it.startTimeMs }

        if (sorted.isEmpty()) return null

        val current = sorted.firstOrNull { it.isCurrentlyAiring(nowMs) }
        val upcoming = sorted.filter { it.isUpcoming(nowMs) }.take(MAX_UPCOMING_ITEMS)

        if (current == null && upcoming.isEmpty()) return null
        return EpgUiState.Available(
            current = current,
            upcoming = upcoming,
            sourceLabel = sourceLabel
        )
    }

    internal fun parseXmltvTimestamp(raw: String): Long {
        val clean = raw.trim()
        if (clean.length < 14) return 0L
        val digits = clean.substring(0, 14)
        if (!digits.all { it.isDigit() }) return 0L

        val tzPart = clean.substring(14).trim()
        return try {
            val sdf = SimpleDateFormat("yyyyMMddHHmmss", Locale.US)
            if (tzPart.matches(Regex("^[+-]\\d{4}$"))) {
                val gmt = "GMT${tzPart.substring(0, 3)}:${tzPart.substring(3, 5)}"
                sdf.timeZone = TimeZone.getTimeZone(gmt)
            } else {
                sdf.timeZone = TimeZone.getTimeZone("UTC")
            }
            sdf.parse(digits)?.time ?: 0L
        } catch (_: Throwable) {
            0L
        }
    }

    internal fun normalizeChannelKey(raw: String): String {
        return raw
            .lowercase(Locale.ROOT)
            .replace(Regex("\\([^)]*\\)"), " ")
            .replace(Regex("\\[[^]]*]"), " ")
            .replace(Regex("\\b(hd|fhd|uhd|sd|4k|1080p|720p|480p|270p|live|tv)\\b"), " ")
            .replace(Regex("[^a-z0-9]+"), "")
            .trim()
    }

    fun formatTime(epochMs: Long): String {
        val sdf = SimpleDateFormat("h:mm a", Locale.getDefault())
        return sdf.format(Date(epochMs))
    }

    fun formatTimeUntil(startMs: Long, nowMs: Long): String {
        val diffMin = ((startMs - nowMs) / 60_000L).coerceAtLeast(0L)
        return when {
            diffMin <= 0L -> "starting soon"
            diffMin < 60L -> "in ${diffMin}m"
            else -> {
                val hrs = diffMin / 60L
                val mins = diffMin % 60L
                if (mins == 0L) "in ${hrs}h" else "in ${hrs}h ${mins}m"
            }
        }
    }

    private fun httpGetText(urlStr: String, timeoutMs: Int): String? {
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
                connectTimeout = timeoutMs
                readTimeout = timeoutMs
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "Mozilla/5.0 (Android; IPTV)")
            }
            if (conn.responseCode !in 200..299) return null
            conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } catch (_: Throwable) {
            null
        } finally {
            conn?.disconnect()
        }
    }

    private fun httpGetPrefix(urlStr: String, maxBytes: Int, timeoutMs: Int): String? {
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
                connectTimeout = timeoutMs
                readTimeout = timeoutMs
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "Mozilla/5.0 (Android; IPTV)")
                setRequestProperty("Range", "bytes=0-${maxBytes - 1}")
            }
            if (conn.responseCode !in 200..299) return null
            val buf = ByteArray(maxBytes)
            var total = 0
            conn.inputStream.use { input ->
                while (total < maxBytes) {
                    val read = input.read(buf, total, maxBytes - total)
                    if (read <= 0) break
                    total += read
                }
            }
            if (total <= 0) null else String(buf, 0, total, Charsets.UTF_8)
        } catch (_: Throwable) {
            null
        } finally {
            conn?.disconnect()
        }
    }

    private fun extractActiveXtreamAccount(viewModel: Any): Any? {
        return try {
            var cur: Class<*>? = viewModel.javaClass
            while (cur != null) {
                try {
                    val f = cur.getDeclaredField("activeXtreamAccount").apply { isAccessible = true }
                    val flow = f.get(viewModel) as? StateFlow<*>
                    return flow?.value
                } catch (_: NoSuchFieldException) {
                    cur = cur.superclass
                }
            }
            null
        } catch (_: Throwable) {
            null
        }
    }

    private fun invokeStringGetter(target: Any?, methodName: String): String {
        if (target == null) return ""
        return try {
            (target.javaClass.getMethod(methodName).invoke(target) as? String).orEmpty()
        } catch (_: Throwable) {
            ""
        }
    }

    private class ByteLimitedInputStream(
        private val delegate: InputStream,
        private val maxBytes: Int
    ) : InputStream() {
        private var readSoFar = 0

        override fun read(): Int {
            if (readSoFar >= maxBytes) return -1
            val b = delegate.read()
            if (b != -1) readSoFar++
            return b
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            val remaining = maxBytes - readSoFar
            if (remaining <= 0) return -1
            val n = delegate.read(b, off, minOf(len, remaining))
            if (n > 0) readSoFar += n
            return n
        }

        override fun close() {
            delegate.close()
        }
    }
}
