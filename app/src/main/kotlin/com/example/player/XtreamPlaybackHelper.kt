package com.example.player

import android.net.Uri
import android.os.Handler
import android.util.Log
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.flow.MutableStateFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.InputStream
import java.lang.reflect.Constructor
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

object XtreamPlaybackHelper {
    private const val TAG = "XtreamPlaybackHelper"

    const val XTREAM_CONNECT_TIMEOUT_MS = 10_000
    const val XTREAM_READ_TIMEOUT_MS = 15_000
    const val DEFAULT_CONNECT_TIMEOUT_MS = 7_000
    const val DEFAULT_READ_TIMEOUT_MS = 9_000
    const val XTREAM_READY_TIMEOUT_MS = 40_000L
    const val DIAGNOSTIC_TIMEOUT_SECONDS = 10L
    const val DIAGNOSTIC_MAX_BYTES = 2048
    const val DIAGNOSTIC_TEXT_LIMIT = 80
    const val OKHTTP_USER_AGENT = "okhttp/4.12.0"
    const val VLC_USER_AGENT = "VLC/3.0.20 LibVLC/3.0.20"
    const val DEFAULT_BROWSER_USER_AGENT =
        "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"

    private val workingLiveExtensions = ConcurrentHashMap<String, String>()
    private val allowedOutputFormatsByServer = ConcurrentHashMap<String, Set<String>>()
    private val pendingRetriesByManager = ConcurrentHashMap<Int, Boolean>()
    private val activeReadyTimeouts = ConcurrentHashMap<Int, XtreamReadyTimeoutTask>()
    private val http403RetriedByManager = ConcurrentHashMap<Int, MutableSet<String>>()
    private val extRetriedByManager = ConcurrentHashMap<Int, MutableSet<String>>()
    private val diagnosticRunKeysByManager = ConcurrentHashMap<Int, String>()
    private val knownSensitiveTokens = ConcurrentHashMap.newKeySet<String>()

    private val diagnosticExecutor = Executors.newCachedThreadPool()

    @Volatile
    private var diagnosticHttpClient: OkHttpClient? = null

    @Volatile
    private var cachedErrorStateConstructor: Constructor<*>? = null

    @Volatile
    private var cachedChannelCopyMethod: Method? = null

    private fun managerKey(manager: Any?): Int {
        return if (manager != null) System.identityHashCode(manager) else 0
    }

    @JvmStatic
    fun normalizeServerUrl(serverUrl: String?): String {
        if (serverUrl == null) return ""
        var cleaned = serverUrl.trim()
        while (cleaned.endsWith("/")) {
            cleaned = cleaned.substring(0, cleaned.length - 1)
        }
        if (cleaned.isEmpty()) return ""
        val lower = cleaned.lowercase(Locale.ROOT)
        if (!lower.startsWith("http://") && !lower.startsWith("https://")) {
            cleaned = "http://$cleaned"
        }
        return cleaned
    }

    @JvmStatic
    fun isXtreamChannelId(channelId: String?): Boolean {
        if (channelId.isNullOrEmpty()) return false
        return channelId.startsWith("xtream_") || channelId.startsWith("vod_")
    }

    @JvmStatic
    fun isXtreamChannel(channel: Any?): Boolean {
        return isXtreamChannelId(extractChannelId(channel))
    }

    @JvmStatic
    fun getConnectTimeoutMs(channelId: String?): Int {
        return if (isXtreamChannelId(channelId)) {
            XTREAM_CONNECT_TIMEOUT_MS
        } else {
            DEFAULT_CONNECT_TIMEOUT_MS
        }
    }

    @JvmStatic
    fun getReadTimeoutMs(channelId: String?): Int {
        return if (isXtreamChannelId(channelId)) {
            XTREAM_READ_TIMEOUT_MS
        } else {
            DEFAULT_READ_TIMEOUT_MS
        }
    }

    @JvmStatic
    fun applyHttpTimeouts(factory: DefaultHttpDataSource.Factory?, channelId: String?) {
        if (factory == null) return
        factory.setConnectTimeoutMs(getConnectTimeoutMs(channelId))
        factory.setReadTimeoutMs(getReadTimeoutMs(channelId))
    }

    @JvmStatic
    fun setRetryPending(manager: Any?, pending: Boolean) {
        val key = managerKey(manager)
        if (key == 0) return
        if (pending) {
            pendingRetriesByManager[key] = true
        } else {
            pendingRetriesByManager.remove(key)
        }
    }

    @JvmStatic
    fun isRetryPending(manager: Any?): Boolean {
        val key = managerKey(manager)
        if (key == 0) return false
        return pendingRetriesByManager[key] == true
    }

    @JvmStatic
    fun isPlayerStateError(state: Any?): Boolean {
        if (state == null) return false
        return state.javaClass.name.endsWith("PlayerState\$Error")
    }

    @JvmStatic
    fun isPlayerStateReady(state: Any?): Boolean {
        if (state == null) return false
        return state.javaClass.name.endsWith("PlayerState\$Ready")
    }

    @JvmStatic
    fun isPlayerStateIdle(state: Any?): Boolean {
        if (state == null) return false
        return state.javaClass.name.endsWith("PlayerState\$Idle")
    }

    @JvmStatic
    fun shouldSkipIdleState(manager: Any?, exoPlayer: ExoPlayer?, currentState: Any?): Boolean {
        if (exoPlayer?.playerError != null) {
            return true
        }
        if (isPlayerStateError(currentState)) {
            return true
        }
        if (isRetryPending(manager)) {
            return true
        }
        return false
    }

    @JvmStatic
    fun scheduleSlowNetWatchdogOnly(manager: Any?) {
        if (manager == null) return
        val handler = extractManagerHandler(manager) ?: return
        val runnable = extractManagerSlowNetRunnable(manager) ?: return
        handler.removeCallbacks(runnable)
        handler.postDelayed(runnable, 6500L)
    }

    @JvmStatic
    fun onScheduleWatchdogsFromManager(manager: Any?) {
        if (manager == null) return
        scheduleSlowNetWatchdogOnly(manager)
        val handler = extractManagerHandler(manager)
        val factory = extractManagerHttpFactory(manager)
        val channel = extractManagerCurrentChannel(manager)
        val isRetry = extractManagerRetryCount(manager) > 0
        onPlayChannel(manager, handler, factory, channel, isRetry)
    }

    @JvmStatic
    fun onPlayChannel(
        manager: Any?,
        handler: Handler?,
        factory: DefaultHttpDataSource.Factory?,
        channel: Any?,
        isRetry: Boolean
    ) {
        val channelId = extractChannelId(channel)
        val streamUrl = extractChannelStreamUrl(channel)
        rememberCredentialsFromUrl(streamUrl)
        applyHttpTimeouts(factory, channelId)
        setRetryPending(manager, false)

        val key = managerKey(manager)
        if (!isRetry && key != 0) {
            http403RetriedByManager.remove(key)
            extRetriedByManager.remove(key)
            diagnosticRunKeysByManager.remove(key)
            cancelReadyTimeout(manager, handler)
            VodPlaybackSpeedController.resetSpeedForNewChannel(manager)
        } else if (!VodPlaybackSpeedController.isVodChannel(channel)) {
            VodPlaybackSpeedController.resetSpeedForNewChannel(manager)
        }

        if (isXtreamChannelId(channelId)) {
            scheduleReadyTimeoutIfNeeded(manager, handler, channelId, isRetry)
        } else {
            cancelReadyTimeout(manager, handler)
        }
    }

    private fun scheduleReadyTimeoutIfNeeded(
        manager: Any?,
        handler: Handler?,
        channelId: String?,
        isRetry: Boolean
    ) {
        if (manager == null || handler == null || channelId.isNullOrEmpty()) return
        val key = managerKey(manager)
        val existing = activeReadyTimeouts[key]
        if (isRetry && existing != null && existing.channelId == channelId) {
            return
        }
        if (existing != null) {
            handler.removeCallbacks(existing)
            activeReadyTimeouts.remove(key)
        }
        val task = XtreamReadyTimeoutTask(manager, handler, channelId)
        activeReadyTimeouts[key] = task
        handler.postDelayed(task, XTREAM_READY_TIMEOUT_MS)
    }

    @JvmStatic
    fun cancelReadyTimeout(manager: Any?, handler: Handler?) {
        val key = managerKey(manager)
        if (key == 0) return
        val existing = activeReadyTimeouts.remove(key) ?: return
        val targetHandler = handler ?: extractManagerHandler(manager)
        targetHandler?.removeCallbacks(existing)
    }

    @JvmStatic
    fun onPlayerReady(manager: Any?, handler: Handler?) {
        setRetryPending(manager, false)
        cancelReadyTimeout(manager, handler)
        val key = managerKey(manager)
        if (key != 0) {
            http403RetriedByManager.remove(key)
            extRetriedByManager.remove(key)
            diagnosticRunKeysByManager.remove(key)
        }
        VodPlaybackSpeedController.syncSpeedOnPlayerReady(manager, extractManagerCurrentChannel(manager))
    }

    @JvmStatic
    fun onPlayerStopped(manager: Any?, handler: Handler?) {
        setRetryPending(manager, false)
        cancelReadyTimeout(manager, handler)
        val key = managerKey(manager)
        if (key != 0) {
            http403RetriedByManager.remove(key)
            extRetriedByManager.remove(key)
            diagnosticRunKeysByManager.remove(key)
        }
        VodPlaybackSpeedController.resetSpeedForNewChannel(manager)
    }

    @JvmStatic
    fun handleXtreamReadyTimeout(manager: Any?, expectedChannelId: String) {
        if (manager == null) return
        val key = managerKey(manager)
        activeReadyTimeouts.remove(key)

        val currentChannel = extractManagerCurrentChannel(manager) ?: return
        val currentChannelId = extractChannelId(currentChannel) ?: return
        if (currentChannelId != expectedChannelId) return

        val exoPlayer = extractManagerExoPlayer(manager) ?: return
        val stateFlow = extractManagerPlayerStateFlow(manager)
        val currentState = stateFlow?.value
        if (exoPlayer.playbackState == Player.STATE_READY ||
            isPlayerStateReady(currentState) ||
            isPlayerStateIdle(currentState) ||
            isPlayerStateError(currentState)
        ) {
            return
        }

        setRetryPending(manager, false)
        invokeCancelBufferingWatchdogs(manager)

        val streamUrl = extractChannelStreamUrl(currentChannel)
        val ext = extractExtension(streamUrl)
        val timeoutMessage = "Xtream stream error (.$ext) [Timeout after 40s]"
        if (stateFlow != null) {
            onFinalPlayerError(manager, extractManagerHandler(manager), currentChannel, timeoutMessage, stateFlow)
        }
        extractManagerIsPlayingFlow(manager)?.value = false
        try {
            exoPlayer.stop()
        } catch (t: Throwable) {
            Log.w(TAG, "Error stopping player on 40s timeout", t)
        }
    }

    @JvmStatic
    fun recordAllowedOutputFormats(serverUrl: String?, userInfo: JSONObject?) {
        if (serverUrl.isNullOrBlank() || userInfo == null) return
        val normalized = normalizeServerUrl(serverUrl)
        if (normalized.isEmpty()) return
        val formats = parseAllowedFormatsFromJson(userInfo)
        allowedOutputFormatsByServer[normalized] = formats
    }

    private fun parseAllowedFormatsFromJson(userInfo: JSONObject): Set<String> {
        val result = LinkedHashSet<String>()
        val array: JSONArray? = userInfo.optJSONArray("allowed_output_formats")
        if (array != null) {
            for (i in 0 until array.length()) {
                val item = array.optString(i, "").trim().removePrefix(".").lowercase(Locale.ROOT)
                if (item.isNotEmpty()) {
                    result.add(item)
                }
            }
            return result
        }
        val rawString = userInfo.optString("allowed_output_formats", "").trim()
        if (rawString.isNotEmpty()) {
            val parts = rawString.split(",")
            for (part in parts) {
                val item = part.trim().removePrefix(".").lowercase(Locale.ROOT)
                if (item.isNotEmpty()) {
                    result.add(item)
                }
            }
        }
        return result
    }

    @JvmStatic
    fun recordAllowedOutputFormatsList(serverUrl: String?, formats: Collection<String>?) {
        if (serverUrl.isNullOrBlank() || formats == null) return
        val normalized = normalizeServerUrl(serverUrl)
        if (normalized.isEmpty()) return
        val cleanSet = LinkedHashSet<String>()
        for (fmt in formats) {
            val item = fmt.trim().removePrefix(".").lowercase(Locale.ROOT)
            if (item.isNotEmpty()) {
                cleanSet.add(item)
            }
        }
        allowedOutputFormatsByServer[normalized] = cleanSet
    }

    @JvmStatic
    fun getAllowedOutputFormats(serverUrl: String?): Set<String> {
        val normalized = normalizeServerUrl(serverUrl)
        if (normalized.isEmpty()) return emptySet()
        return allowedOutputFormatsByServer[normalized] ?: emptySet()
    }

    @JvmStatic
    fun isTsAllowedForServer(serverUrl: String?): Boolean {
        val formats = getAllowedOutputFormats(serverUrl)
        return formats.contains("ts") || formats.contains("mpegts")
    }

    @JvmStatic
    fun recordWorkingLiveExtension(serverUrl: String?, extension: String?) {
        if (serverUrl.isNullOrBlank() || extension.isNullOrBlank()) return
        val normalized = normalizeServerUrl(serverUrl)
        if (normalized.isEmpty()) return
        val trimmed = extension.trim().lowercase(Locale.ROOT)
        val dotExt = if (trimmed.startsWith(".")) trimmed else ".$trimmed"
        workingLiveExtensions[normalized] = dotExt
        Log.i("XtreamStreamResolver", "Remembered working live extension for $normalized: $dotExt")
    }

    @JvmStatic
    fun getRememberedWorkingLiveExtension(serverUrl: String?): String? {
        val normalized = normalizeServerUrl(serverUrl)
        if (normalized.isEmpty()) return null
        return workingLiveExtensions[normalized]?.takeIf { it.isNotEmpty() }
    }

    @JvmStatic
    fun recordWorkingLiveExtensionFromUrl(url: String?) {
        if (url.isNullOrBlank()) return
        val liveIdx = url.indexOf("/live/")
        if (liveIdx == -1) return
        val serverPart = url.substring(0, liveIdx)
        val ext = extractExtension(url)
        if (ext.equals("ts", ignoreCase = true) || ext.equals("m3u8", ignoreCase = true)) {
            recordWorkingLiveExtension(serverPart, "." + ext.lowercase(Locale.ROOT))
        }
    }

    @JvmStatic
    fun selectLiveExtension(serverUrl: String?): String {
        val remembered = getRememberedWorkingLiveExtension(serverUrl)
        if (!remembered.isNullOrEmpty()) {
            return if (remembered.startsWith(".")) remembered else ".$remembered"
        }
        if (isTsAllowedForServer(serverUrl)) {
            return ".ts"
        }
        return ".m3u8"
    }

    @JvmStatic
    fun buildLiveStreamUrl(
        serverUrl: String?,
        username: String?,
        password: String?,
        streamId: Int,
        extension: String?
    ): String {
        registerKnownCredentials(username, password)
        val normalizedServer = normalizeServerUrl(serverUrl)
        val encodedUser = Uri.encode(username ?: "")
        val encodedPass = Uri.encode(password ?: "")
        val resolvedExt = selectLiveExtension(normalizedServer)
        return "$normalizedServer/live/$encodedUser/$encodedPass/$streamId$resolvedExt"
    }

    @JvmStatic
    fun buildVodStreamUrl(
        serverUrl: String?,
        username: String?,
        password: String?,
        streamId: Int,
        extension: String?
    ): String {
        registerKnownCredentials(username, password)
        val normalizedServer = normalizeServerUrl(serverUrl)
        val encodedUser = Uri.encode(username ?: "")
        val encodedPass = Uri.encode(password ?: "")
        val rawExt = if (!extension.isNullOrBlank()) extension.trim() else "mp4"
        val cleanExt = rawExt.removePrefix(".")
        return "$normalizedServer/movie/$encodedUser/$encodedPass/$streamId.$cleanExt"
    }

    private val stashedDirectSource = ThreadLocal<String>()

    @JvmStatic
    fun optCategoryAndStashDirectSource(jsonObject: JSONObject?, key: String?): String {
        if (jsonObject == null) {
            stashedDirectSource.set("")
            return ""
        }
        val rawDirectSource = jsonObject.optString("direct_source", "").trim()
        stashedDirectSource.set(rawDirectSource)
        return jsonObject.optString(key ?: "category_name")
    }

    @JvmStatic
    fun consumeStashedDirectSource(): String {
        val value = stashedDirectSource.get().orEmpty()
        stashedDirectSource.remove()
        return value
    }

    @JvmStatic
    fun isHttpDirectSource(directSource: String?): Boolean {
        if (directSource.isNullOrBlank()) return false
        val trimmed = directSource.trim()
        return trimmed.startsWith("http://", ignoreCase = true) ||
            trimmed.startsWith("https://", ignoreCase = true)
    }

    @JvmStatic
    fun extractDirectSource(jsonObject: JSONObject?): String {
        if (jsonObject == null) return ""
        val raw = jsonObject.optString("direct_source", "").trim()
        return if (isHttpDirectSource(raw)) raw else ""
    }

    @JvmStatic
    fun resolveEffectiveStreamUrl(
        serverUrl: String?,
        username: String?,
        password: String?,
        streamId: Int,
        directSource: String?,
        isVod: Boolean,
        containerExtension: String?
    ): String {
        if (serverUrl == null) throw NullPointerException("serverUrl is null")
        if (username == null) throw NullPointerException("username is null")
        if (password == null) throw NullPointerException("password is null")
        registerKnownCredentials(username, password)

        if (isHttpDirectSource(directSource)) {
            val trimmedDirect = directSource!!.trim()
            rememberCredentialsFromUrl(trimmedDirect)
            return trimmedDirect
        }

        val normalizedServer = normalizeServerUrl(serverUrl)
        return if (isVod) {
            buildVodStreamUrl(normalizedServer, username, password, streamId, containerExtension)
        } else {
            val ext = selectLiveExtension(normalizedServer)
            buildLiveStreamUrl(normalizedServer, username, password, streamId, ext)
        }
    }

    @JvmStatic
    fun extractExtension(url: String?): String {
        if (url.isNullOrEmpty()) return "stream"
        val qIdx = url.indexOf('?')
        val cleanUrl = if (qIdx != -1) url.substring(0, qIdx) else url
        val dotIdx = cleanUrl.lastIndexOf('.')
        if (dotIdx != -1 && dotIdx + 1 < cleanUrl.length) {
            val candidate = cleanUrl.substring(dotIdx + 1)
            if (candidate.length <= 6 && !candidate.contains("/")) {
                return candidate
            }
        }
        return "stream"
    }

    @JvmStatic
    fun createAlternateExtensionUrl(streamUrl: String?): String? {
        if (streamUrl.isNullOrBlank() || !streamUrl.contains("/live/")) {
            return null
        }
        val qIdx = streamUrl.indexOf('?')
        val basePart = if (qIdx != -1) streamUrl.substring(0, qIdx) else streamUrl
        val queryPart = if (qIdx != -1) streamUrl.substring(qIdx) else ""
        return when {
            basePart.endsWith(".m3u8", ignoreCase = true) ->
                basePart.substring(0, basePart.length - 5) + ".ts" + queryPart
            basePart.endsWith(".ts", ignoreCase = true) ->
                basePart.substring(0, basePart.length - 3) + ".m3u8" + queryPart
            else -> null
        }
    }

    @JvmStatic
    fun createAlternateExtensionChannel(channel: Any?): Any? {
        if (channel == null) return null
        val streamUrl = extractChannelStreamUrl(channel) ?: return null
        val alternateUrl = createAlternateExtensionUrl(streamUrl) ?: return null
        return copyChannelWithUrlAndUserAgent(channel, alternateUrl, null)
    }

    @JvmStatic
    fun isCandidateFallbackError(httpCode: Int, errorCode: Int): Boolean {
        if (httpCode == 400 || httpCode == 401 || httpCode == 403 || httpCode == 404 || httpCode == 410) {
            return true
        }
        if (errorCode in 2001..2004) {
            return true
        }
        if (errorCode in 3001..3004) {
            return true
        }
        return false
    }

    @JvmStatic
    fun getTsFallbackChannel(
        channel: Any?,
        httpCode: Int,
        errorCode: Int,
        retryCount: Int
    ): Any? {
        if (channel == null) return null
        if (retryCount > 1) return null
        val channelId = extractChannelId(channel) ?: return null
        if (!channelId.startsWith("xtream_")) return null
        if (extractChannelIsVod(channel)) return null
        val streamUrl = extractChannelStreamUrl(channel) ?: return null
        if (!streamUrl.contains("/live/")) return null
        if (!isCandidateFallbackError(httpCode, errorCode)) return null
        return createAlternateExtensionChannel(channel)
    }

    @JvmStatic
    fun createHttp403RetryChannel(manager: Any?, channel: Any?, httpCode: Int): Any? {
        if (channel == null || httpCode != 403) return null
        val channelId = extractChannelId(channel) ?: return null
        if (!isXtreamChannelId(channelId)) return null

        val currentUa = extractChannelUserAgent(channel)
        if (OKHTTP_USER_AGENT.equals(currentUa, ignoreCase = true)) {
            return null
        }

        val key = managerKey(manager)
        if (key != 0) {
            val retriedSet = http403RetriedByManager.getOrPut(key) { ConcurrentHashMap.newKeySet() }
            val retryToken = "$channelId|403_okhttp"
            if (!retriedSet.add(retryToken)) {
                return null
            }
        }
        val streamUrl = extractChannelStreamUrl(channel) ?: return null
        return copyChannelWithUrlAndUserAgent(channel, streamUrl, OKHTTP_USER_AGENT)
    }

    @JvmStatic
    fun resolveFallbackChannelForError(
        manager: Any?,
        channel: Any?,
        httpCode: Int,
        errorCode: Int,
        retryCount: Int,
        triedUrls: Set<String>?
    ): Any? {
        if (channel == null) return null
        if (httpCode == 403 && isXtreamChannel(channel)) {
            val uaFallback = createHttp403RetryChannel(manager, channel, httpCode)
            if (uaFallback != null) {
                return uaFallback
            }
        }
        val channelId = extractChannelId(channel) ?: return null
        val key = managerKey(manager)
        if (key != 0) {
            val retriedSet = extRetriedByManager.getOrPut(key) { ConcurrentHashMap.newKeySet() }
            if (retriedSet.contains("$channelId|ext_swap")) {
                return null
            }
        }
        val extFallback = getTsFallbackChannel(channel, httpCode, errorCode, retryCount) ?: return null
        val fallbackUrl = extractChannelStreamUrl(extFallback) ?: return null
        if (triedUrls != null && triedUrls.contains(fallbackUrl)) {
            return null
        }
        if (key != 0) {
            val retriedSet = extRetriedByManager.getOrPut(key) { ConcurrentHashMap.newKeySet() }
            retriedSet.add("$channelId|ext_swap")
        }
        return extFallback
    }

    @JvmStatic
    fun extractHttpStatusCode(error: PlaybackException?): Int {
        var cause: Throwable? = error?.cause
        while (cause != null) {
            if (cause is HttpDataSource.InvalidResponseCodeException) {
                return cause.responseCode
            }
            cause = cause.cause
        }
        return -1
    }

    @JvmStatic
    fun resolveUserAgent(channelUserAgent: String?, channelId: String?, defaultUserAgent: String?): String {
        if (!channelUserAgent.isNullOrBlank()) {
            return channelUserAgent
        }
        if (isXtreamChannelId(channelId)) {
            return VLC_USER_AGENT
        }
        if (!defaultUserAgent.isNullOrBlank()) {
            return defaultUserAgent
        }
        return DEFAULT_BROWSER_USER_AGENT
    }

    @JvmStatic
    fun formatXtreamErrorMessage(url: String?, httpCode: Int, errorCode: Int): String {
        val ext = extractExtension(url)
        val sb = StringBuilder("Xtream stream error (.$ext)")
        if (httpCode > 0) {
            sb.append(" [HTTP ").append(httpCode).append("]")
        } else if (errorCode != 0) {
            sb.append(" [code ").append(errorCode).append("]")
        }
        return sanitizeCredentials(sb.toString(), url)
    }

    @JvmStatic
    fun formatPlayerErrorMessage(channel: Any?, httpCode: Int, error: PlaybackException?): String? {
        if (channel == null) return null
        val channelId = extractChannelId(channel) ?: return null
        if (!isXtreamChannelId(channelId)) return null
        val streamUrl = extractChannelStreamUrl(channel)
        val errorCode = error?.errorCode ?: 0
        return formatXtreamErrorMessage(streamUrl, httpCode, errorCode)
    }

    @JvmStatic
    fun onFinalPlayerError(
        manager: Any?,
        handler: Handler?,
        channel: Any?,
        baseMessage: String?,
        stateFlow: MutableStateFlow<Any?>
    ) {
        setRetryPending(manager, false)
        cancelReadyTimeout(manager, handler)

        val streamUrl = extractChannelStreamUrl(channel)
        rememberCredentialsFromUrl(streamUrl)
        val safeBaseMessage = sanitizeCredentials(baseMessage ?: "Playback error", streamUrl)
        val initialErrorState = createPlayerErrorState(safeBaseMessage)
        if (initialErrorState != null) {
            stateFlow.value = initialErrorState
        }

        val channelId = extractChannelId(channel)
        if (!isXtreamChannelId(channelId) || streamUrl.isNullOrBlank()) {
            return
        }

        val key = managerKey(manager)
        val diagKey = "$channelId|$streamUrl|$safeBaseMessage"
        if (key != 0) {
            val previousKey = diagnosticRunKeysByManager.put(key, diagKey)
            if (previousKey == diagKey) {
                return
            }
        }

        val effectiveUa = resolveUserAgent(
            extractChannelUserAgent(channel),
            channelId,
            DEFAULT_BROWSER_USER_AGENT
        )
        val targetHandler = handler ?: extractManagerHandler(manager)
        val task = DiagnosticFetchTask(
            manager = manager,
            handler = targetHandler,
            expectedChannelId = channelId!!,
            streamUrl = streamUrl,
            userAgent = effectiveUa,
            baseMessage = safeBaseMessage,
            stateFlow = stateFlow
        )
        diagnosticExecutor.execute(task)
    }

    private fun getOrCreateDiagnosticHttpClient(): OkHttpClient {
        val existing = diagnosticHttpClient
        if (existing != null) return existing
        val created = OkHttpClient.Builder()
            .connectTimeout(DIAGNOSTIC_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(DIAGNOSTIC_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .callTimeout(DIAGNOSTIC_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
        diagnosticHttpClient = created
        return created
    }

    private fun buildDiagnosticRequest(streamUrl: String, userAgent: String): Request {
        return Request.Builder()
            .url(streamUrl)
            .get()
            .header("User-Agent", userAgent)
            .header("Range", "bytes=0-2047")
            .build()
    }

    @JvmStatic
    fun executeDiagnosticGet(streamUrl: String, userAgent: String): String {
        rememberCredentialsFromUrl(streamUrl)
        val startNs = System.nanoTime()
        return try {
            val client = getOrCreateDiagnosticHttpClient()
            val request = buildDiagnosticRequest(streamUrl, userAgent)
            client.newCall(request).execute().use { response ->
                val ttfbMs = ((System.nanoTime() - startNs) / 1_000_000L).coerceAtLeast(0L)
                val statusCode = response.code
                val contentType = sanitizeCredentials(
                    response.header("Content-Type")?.trim().takeUnless { it.isNullOrEmpty() } ?: "unknown",
                    streamUrl
                )
                val bodyBytes = readUpTo2048Bytes(response)
                val bodySnippet = if (isTextContent(contentType, bodyBytes)) {
                    formatBodyPreview(bodyBytes, streamUrl)
                } else {
                    null
                }
                formatDiagnosticSuffix(statusCode, contentType, ttfbMs, bodySnippet)
            }
        } catch (t: Throwable) {
            val elapsedMs = ((System.nanoTime() - startNs) / 1_000_000L).coerceAtLeast(0L)
            val safeErr = sanitizeCredentials(t.message ?: t.javaClass.simpleName, streamUrl)
            "[Diag: ${t.javaClass.simpleName} ($safeErr), TTFB=${elapsedMs}ms]"
        }
    }

    private fun readUpTo2048Bytes(response: Response): ByteArray {
        val body = response.body ?: return ByteArray(0)
        val input: InputStream = body.byteStream()
        val buffer = ByteArray(DIAGNOSTIC_MAX_BYTES)
        var total = 0
        while (total < DIAGNOSTIC_MAX_BYTES) {
            val read = input.read(buffer, total, DIAGNOSTIC_MAX_BYTES - total)
            if (read <= 0) break
            total += read
        }
        return if (total == buffer.size) buffer else buffer.copyOf(total)
    }

    @JvmStatic
    fun isTextContent(contentType: String?, bodyBytes: ByteArray): Boolean {
        if (bodyBytes.isEmpty()) return false
        val lowerType = contentType?.lowercase(Locale.ROOT).orEmpty()
        if (lowerType.contains("video/") ||
            lowerType.contains("audio/") ||
            lowerType.contains("mp2t") ||
            lowerType.contains("octet-stream")
        ) {
            if (bodyBytes[0] == 0x47.toByte()) {
                return false
            }
        }
        if (lowerType.contains("text/") ||
            lowerType.contains("json") ||
            lowerType.contains("xml") ||
            lowerType.contains("html") ||
            lowerType.contains("mpegurl") ||
            lowerType.contains("javascript") ||
            lowerType.contains("urlencoded")
        ) {
            return true
        }
        return looksLikePrintableText(bodyBytes)
    }

    private fun looksLikePrintableText(bytes: ByteArray): Boolean {
        if (bytes.isEmpty()) return false
        if (bytes[0] == 0x47.toByte() && (bytes.size < 189 || bytes[188] == 0x47.toByte())) {
            return false
        }
        val sampleSize = minOf(bytes.size, 160)
        var printable = 0
        for (i in 0 until sampleSize) {
            val b = bytes[i].toInt() and 0xFF
            if (b == 0) return false
            if (b in 32..126 || b == 9 || b == 10 || b == 13 || b >= 128) {
                printable++
            }
        }
        return printable * 10 >= sampleSize * 9
    }

    @JvmStatic
    fun formatBodyPreview(bodyBytes: ByteArray, streamUrl: String?): String {
        val rawText = String(bodyBytes, StandardCharsets.UTF_8)
            .replace('\r', ' ')
            .replace('\n', ' ')
            .replace('\t', ' ')
            .trim()
        val sanitized = sanitizeCredentials(rawText, streamUrl).trim()
        return if (sanitized.length <= DIAGNOSTIC_TEXT_LIMIT) {
            sanitized
        } else {
            sanitized.substring(0, DIAGNOSTIC_TEXT_LIMIT)
        }
    }

    private fun formatDiagnosticSuffix(
        httpStatus: Int,
        contentType: String,
        ttfbMs: Long,
        bodyPreview: String?
    ): String {
        val sb = StringBuilder()
        sb.append("[HTTP ").append(httpStatus)
        sb.append(" | Content-Type: ").append(contentType)
        sb.append(" | TTFB: ").append(ttfbMs).append("ms")
        if (!bodyPreview.isNullOrEmpty()) {
            sb.append(" | Body: ").append(bodyPreview)
        }
        sb.append("]")
        return sb.toString()
    }

    @JvmStatic
    fun registerKnownCredentials(username: String?, password: String?) {
        addSensitiveToken(username)
        addSensitiveToken(password)
        if (!username.isNullOrBlank()) addSensitiveToken(Uri.encode(username))
        if (!password.isNullOrBlank()) addSensitiveToken(Uri.encode(password))
    }

    private fun addSensitiveToken(token: String?) {
        val clean = token?.trim().orEmpty()
        if (clean.length >= 3 && !clean.equals("live", ignoreCase = true) &&
            !clean.equals("movie", ignoreCase = true) &&
            !clean.equals("http", ignoreCase = true) &&
            !clean.equals("https", ignoreCase = true)
        ) {
            knownSensitiveTokens.add(clean)
        }
    }

    @JvmStatic
    fun rememberCredentialsFromUrl(url: String?) {
        if (url.isNullOrBlank()) return
        val pathRegex = Regex("""/(?:live|movie|series)/([^/?#]+)/([^/?#]+)/""", RegexOption.IGNORE_CASE)
        val match = pathRegex.find(url)
        if (match != null) {
            val user = match.groupValues.getOrNull(1)
            val pass = match.groupValues.getOrNull(2)
            registerKnownCredentials(user, pass)
            if (!user.isNullOrEmpty()) addSensitiveToken(Uri.decode(user))
            if (!pass.isNullOrEmpty()) addSensitiveToken(Uri.decode(pass))
        }
        val queryUser = Regex("""[?&]username=([^&#\s]+)""", RegexOption.IGNORE_CASE).find(url)?.groupValues?.getOrNull(1)
        val queryPass = Regex("""[?&]password=([^&#\s]+)""", RegexOption.IGNORE_CASE).find(url)?.groupValues?.getOrNull(1)
        registerKnownCredentials(queryUser, queryPass)
    }

    @JvmStatic
    fun sanitizeCredentials(text: String?, streamUrl: String?): String {
        if (text.isNullOrEmpty()) return ""
        rememberCredentialsFromUrl(streamUrl)
        rememberCredentialsFromUrl(text)

        var sanitized = text!!
        sanitized = sanitized.replace(
            Regex("""/(live|movie|series)/([^/\s?#]+)/([^/\s?#]+)/""", RegexOption.IGNORE_CASE),
            "/$1/***/***/"
        )
        sanitized = sanitized.replace(
            Regex("""([?&](?:username|user|password|pass)=)[^&#\s"']+""", RegexOption.IGNORE_CASE),
            "$1***"
        )
        sanitized = sanitized.replace(
            Regex("""(https?://)[^/\s:@]+:[^/\s@]+@""", RegexOption.IGNORE_CASE),
            "$1***:***@"
        )
        for (token in knownSensitiveTokens) {
            if (token.isNotEmpty() && sanitized.contains(token)) {
                sanitized = sanitized.replace(token, "***")
            }
        }
        return sanitized
    }

    @JvmStatic
    fun createPlayerErrorState(message: String): Any? {
        return try {
            var ctor = cachedErrorStateConstructor
            if (ctor == null) {
                val cls = Class.forName("com.example.player.PlayerState\$Error")
                ctor = cls.getDeclaredConstructor(
                    String::class.java,
                    Boolean::class.javaPrimitiveType
                ).apply {
                    isAccessible = true
                }
                cachedErrorStateConstructor = ctor
            }
            ctor?.newInstance(message, true)
        } catch (t: Throwable) {
            Log.w(TAG, "Failed creating PlayerState.Error", t)
            null
        }
    }

    private fun copyChannelWithUrlAndUserAgent(
        channel: Any,
        newStreamUrl: String,
        newUserAgent: String?
    ): Any? {
        return try {
            val cls = channel.javaClass
            val c1 = cls.getMethod("component1").invoke(channel) as? String ?: ""
            val c2 = cls.getMethod("component2").invoke(channel) as? String ?: ""
            val c3 = cls.getMethod("component3").invoke(channel) as? String
            val c4 = cls.getMethod("component4").invoke(channel) as? String ?: ""
            val c5 = cls.getMethod("component5").invoke(channel) as? String
            val c6 = cls.getMethod("component6").invoke(channel) as? String
            val c8 = (cls.getMethod("component8").invoke(channel) as? Boolean) ?: false
            val c9 = cls.getMethod("component9").invoke(channel) as? String ?: ""
            val c10 = newUserAgent ?: (cls.getMethod("component10").invoke(channel) as? String)
            val c11 = cls.getMethod("component11").invoke(channel) as? String
            val c12 = (cls.getMethod("component12").invoke(channel) as? Boolean) ?: false

            var copyMethod = cachedChannelCopyMethod
            if (copyMethod == null) {
                copyMethod = cls.getMethod(
                    "copy",
                    String::class.java,
                    String::class.java,
                    String::class.java,
                    String::class.java,
                    String::class.java,
                    String::class.java,
                    String::class.java,
                    Boolean::class.javaPrimitiveType,
                    String::class.java,
                    String::class.java,
                    String::class.java,
                    Boolean::class.javaPrimitiveType
                )
                cachedChannelCopyMethod = copyMethod
            }
            copyMethod?.invoke(
                channel,
                c1,
                c2,
                c3,
                c4,
                c5,
                c6,
                newStreamUrl,
                c8,
                c9,
                c10,
                c11,
                c12
            )
        } catch (t: Throwable) {
            Log.w(TAG, "Failed copying Channel", t)
            null
        }
    }

    @JvmStatic
    fun extractChannelId(channel: Any?): String? {
        if (channel == null) return null
        return try {
            channel.javaClass.getMethod("getId").invoke(channel) as? String
        } catch (_: Throwable) {
            null
        }
    }

    @JvmStatic
    fun extractChannelStreamUrl(channel: Any?): String? {
        if (channel == null) return null
        return try {
            channel.javaClass.getMethod("getStreamUrl").invoke(channel) as? String
        } catch (_: Throwable) {
            null
        }
    }

    @JvmStatic
    fun extractChannelUserAgent(channel: Any?): String? {
        if (channel == null) return null
        return try {
            channel.javaClass.getMethod("getUserAgent").invoke(channel) as? String
        } catch (_: Throwable) {
            null
        }
    }

    @JvmStatic
    fun extractChannelIsVod(channel: Any?): Boolean {
        if (channel == null) return false
        return try {
            (channel.javaClass.getMethod("isMovieOrVod").invoke(channel) as? Boolean) == true
        } catch (_: Throwable) {
            false
        }
    }

    private fun getFieldValue(target: Any?, fieldName: String): Any? {
        if (target == null) return null
        return try {
            val field: Field = target.javaClass.getDeclaredField(fieldName)
            field.isAccessible = true
            field.get(target)
        } catch (_: Throwable) {
            null
        }
    }

    private fun extractManagerHandler(manager: Any?): Handler? {
        return getFieldValue(manager, "handler") as? Handler
    }

    private fun extractManagerSlowNetRunnable(manager: Any?): Runnable? {
        return getFieldValue(manager, "slowNetStepDownRunnable") as? Runnable
    }

    private fun extractManagerHttpFactory(manager: Any?): DefaultHttpDataSource.Factory? {
        return getFieldValue(manager, "httpDataSourceFactory") as? DefaultHttpDataSource.Factory
    }

    private fun extractManagerRetryCount(manager: Any?): Int {
        return (getFieldValue(manager, "retryCount") as? Int) ?: 0
    }

    private fun extractManagerCurrentChannel(manager: Any?): Any? {
        return getFieldValue(manager, "currentChannel")
    }

    private fun extractManagerExoPlayer(manager: Any?): ExoPlayer? {
        return getFieldValue(manager, "exoPlayer") as? ExoPlayer
    }

    @Suppress("UNCHECKED_CAST")
    private fun extractManagerPlayerStateFlow(manager: Any?): MutableStateFlow<Any?>? {
        return getFieldValue(manager, "_playerState") as? MutableStateFlow<Any?>
    }

    @Suppress("UNCHECKED_CAST")
    private fun extractManagerIsPlayingFlow(manager: Any?): MutableStateFlow<Boolean>? {
        return getFieldValue(manager, "_isPlaying") as? MutableStateFlow<Boolean>
    }

    private fun invokeCancelBufferingWatchdogs(manager: Any?) {
        if (manager == null) return
        try {
            val m = manager.javaClass.getDeclaredMethod("cancelBufferingWatchdogs")
            m.isAccessible = true
            m.invoke(manager)
        } catch (_: Throwable) {
        }
    }

    private class XtreamReadyTimeoutTask(
        private val manager: Any,
        private val handler: Handler,
        val channelId: String
    ) : Runnable {
        override fun run() {
            handler.removeCallbacks(this)
            handleXtreamReadyTimeout(manager, channelId)
        }
    }

    private class DiagnosticFetchTask(
        private val manager: Any?,
        private val handler: Handler?,
        private val expectedChannelId: String,
        private val streamUrl: String,
        private val userAgent: String,
        private val baseMessage: String,
        private val stateFlow: MutableStateFlow<Any?>
    ) : Runnable {
        override fun run() {
            val diagSuffix = executeDiagnosticGet(streamUrl, userAgent)
            val combinedMessage = sanitizeCredentials("$baseMessage $diagSuffix", streamUrl)
            val updateTask = UpdateErrorStateTask(
                manager = manager,
                expectedChannelId = expectedChannelId,
                updatedMessage = combinedMessage,
                stateFlow = stateFlow
            )
            if (handler != null) {
                handler.post(updateTask)
            } else {
                updateTask.run()
            }
        }
    }

    private class UpdateErrorStateTask(
        private val manager: Any?,
        private val expectedChannelId: String,
        private val updatedMessage: String,
        private val stateFlow: MutableStateFlow<Any?>
    ) : Runnable {
        override fun run() {
            if (manager != null) {
                val activeChannel = extractManagerCurrentChannel(manager)
                val activeId = extractChannelId(activeChannel)
                if (activeId != null && activeId != expectedChannelId) {
                    return
                }
            }
            if (!isPlayerStateError(stateFlow.value)) {
                return
            }
            val newErrorState = createPlayerErrorState(updatedMessage)
            if (newErrorState != null) {
                stateFlow.value = newErrorState
            }
        }
    }
}
