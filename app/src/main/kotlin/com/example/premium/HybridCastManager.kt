package com.example.premium

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.media.MediaRouter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Cast
import androidx.compose.material.icons.rounded.CastConnected
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Router
import androidx.compose.material.icons.rounded.ScreenShare
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import java.io.BufferedOutputStream
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.lang.ref.WeakReference
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.HttpURLConnection
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.URI
import java.net.URL
import java.net.URLDecoder
import java.net.URLEncoder
import java.security.SecureRandom
import java.util.Collections
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

object HybridCastManager {
    private const val TAG = "HybridCastManager"
    private val mainHandler by lazy { Handler(Looper.getMainLooper()) }
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val serverExecutor = Executors.newCachedThreadPool()

    enum class DeviceProtocol(val badgeLabel: String) {
        DLNA_UPNP("DLNA / UPnP TV"),
        DIAL_CAST("Smart TV / Cast"),
        SYSTEM_ROUTE("Wireless Display"),
        WEB_RECEIVER("Web TV Receiver")
    }

    data class CastDevice(
        val id: String,
        val name: String,
        val modelOrManufacturer: String,
        val host: String,
        val locationUrl: String,
        val avTransportControlUrl: String?,
        val renderingControlUrl: String?,
        val dialApplicationUrl: String?,
        val protocol: DeviceProtocol
    )

    data class CastChannelSnapshot(
        val name: String = "",
        val category: String = "",
        val language: String = "",
        val logoUrl: String = "",
        val streamUrl: String = ""
    )

    enum class ActiveCastMode {
        NONE,
        DLNA_DEVICE,
        WEB_TV_SERVER,
        SYSTEM_MIRROR
    }

    // Observable Compose States
    var isScanning by mutableStateOf(false)
        private set

    val discoveredDevices = mutableStateListOf<CastDevice>()

    var activeMode by mutableStateOf(ActiveCastMode.NONE)
        private set

    var connectedDevice by mutableStateOf<CastDevice?>(null)
        private set

    var isWebServerRunning by mutableStateOf(false)
        private set

    var webServerUrl by mutableStateOf("")
        private set

    var webServerPort by mutableIntStateOf(8989)
        private set

    var useProxyForDlna by mutableStateOf(false)
    var useProxyForWebTv by mutableStateOf(true)

    var isRemotePaused by mutableStateOf(false)
        private set

    var isRemoteMuted by mutableStateOf(false)
        private set

    var statusMessage by mutableStateOf("Ready to cast live stream")
        private set

    var connectedWebClientCount by mutableIntStateOf(0)
        private set

    var currentChannel by mutableStateOf(CastChannelSnapshot())
        private set

    private var streamRevision by mutableLongStateOf(1L)
    private var remoteCommandSeq by mutableLongStateOf(0L)
    private var lastRemoteCommand by mutableStateOf(" NONE")

    private val scanningGuard = AtomicBoolean(false)
    private val webServerSocketRef = java.util.concurrent.atomic.AtomicReference<ServerSocket?>(null)
    private val webClientLastSeen = ConcurrentHashMap<String, Long>()
    private val seenDeviceLocations = ConcurrentHashMap<String, CastDevice>()
    private val allowedProxyHosts = ConcurrentHashMap.newKeySet<String>()
    private val tokenRandom = SecureRandom()

    @Volatile
    private var webAuthToken: String = ""

    @Volatile
    private var webServerBaseUrl: String = ""

    @Volatile
    private var localPlayerManagerRef: WeakReference<Any>? = null

    @Volatile
    private var guardedExoPlayerRef: WeakReference<ExoPlayer>? = null

    @Volatile
    private var pausedLocalPlayerForCast: Boolean = false

    @Volatile
    private var webClientPruneJob: Job? = null

    private val localPlayerCastGuardListener = object : Player.Listener {
        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            if (playWhenReady && isRemotePlaybackActive()) {
                pauseLocalPlayerForCast()
            }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (isPlaying && isRemotePlaybackActive()) {
                pauseLocalPlayerForCast()
            }
        }
    }

    val isCastingActive: Boolean
        get() = activeMode != ActiveCastMode.NONE || isWebServerRunning

    // =========================================================================
    // Focused Cast Helpers (URL sanitization, masking, token, proxy, player sync)
    // =========================================================================

    internal fun stripPipeSuffix(url: String): String =
        url.trim().substringBefore('|').trim()

    internal fun maskXtreamCredentials(url: String): String {
        if (url.isBlank()) return url
        return XTREAM_CREDENTIAL_REGEX.replace(url, "$1***/***$4")
    }

    internal fun generateWebAuthToken(): String {
        val sb = StringBuilder(WEB_TOKEN_LENGTH)
        repeat(WEB_TOKEN_LENGTH) {
            sb.append(WEB_TOKEN_CHARS[tokenRandom.nextInt(WEB_TOKEN_CHARS.length)])
        }
        return sb.toString()
    }

    internal fun buildWebServerUrl(lanIp: String, port: Int, token: String): String {
        val base = "http://$lanIp:$port"
        return if (token.isNotBlank()) "$base/?t=$token" else base
    }

    internal fun buildRelativeProxyPath(targetUrl: String, token: String = webAuthToken): String {
        val encodedUrl = URLEncoder.encode(targetUrl, "UTF-8")
        return "/proxy?t=${URLEncoder.encode(token, "UTF-8")}&url=$encodedUrl"
    }

    private fun buildAbsoluteProxyUrl(targetUrl: String): String {
        val base = webServerBaseUrl.trimEnd('/')
        if (base.isEmpty()) return targetUrl
        return "$base${buildRelativeProxyPath(targetUrl, webAuthToken)}"
    }

    internal fun extractQueryParam(fullPath: String, key: String): String? {
        val query = fullPath.substringAfter('?', missingDelimiterValue = "")
        if (query.isEmpty()) return null
        for (pair in query.split('&')) {
            val eq = pair.indexOf('=')
            val paramKey = if (eq >= 0) pair.substring(0, eq) else pair
            if (paramKey == key) {
                val rawValue = if (eq >= 0) pair.substring(eq + 1) else ""
                return runCatching { URLDecoder.decode(rawValue, "UTF-8") }.getOrDefault(rawValue)
            }
        }
        return null
    }

    internal fun isValidWebRequestToken(fullPath: String, expectedToken: String = webAuthToken): Boolean {
        if (expectedToken.isBlank()) return false
        return extractQueryParam(fullPath, "t") == expectedToken
    }

    internal fun extractNormalizedHttpHost(url: String): String? {
        val trimmed = url.trim()
        if (trimmed.isEmpty()) return null
        return try {
            val uri = URI(trimmed)
            val scheme = uri.scheme?.lowercase(Locale.US) ?: return null
            if (scheme != "http" && scheme != "https") return null
            uri.host?.lowercase(Locale.US)?.takeIf { it.isNotBlank() }
        } catch (_: Throwable) {
            null
        }
    }

    internal fun resetAllowedProxyHosts(streamUrl: String) {
        allowedProxyHosts.clear()
        allowProxyHostFromUrl(stripPipeSuffix(streamUrl))
    }

    internal fun allowProxyHostFromUrl(url: String) {
        val host = extractNormalizedHttpHost(url) ?: return
        allowedProxyHosts.add(host)
    }

    internal fun isAllowedProxyUrl(targetUrl: String): Boolean {
        val host = extractNormalizedHttpHost(targetUrl) ?: return false
        return allowedProxyHosts.contains(host)
    }

    internal fun isRemotePlaybackActive(): Boolean {
        return activeMode == ActiveCastMode.DLNA_DEVICE ||
            (isWebServerRunning && connectedWebClientCount > 0)
    }

    fun bindLocalPlayerManager(playerManager: Any?) {
        if (playerManager == null) return
        localPlayerManagerRef = WeakReference(playerManager)
        attachLocalPlayerCastGuard(playerManager)
        syncLocalPlayerForCastState()
    }

    private fun extractLocalExoPlayer(playerManager: Any? = localPlayerManagerRef?.get()): ExoPlayer? {
        if (playerManager == null) return null
        return try {
            val method = playerManager.javaClass.getMethod("getExoPlayer")
            method.invoke(playerManager) as? ExoPlayer
        } catch (_: Throwable) {
            null
        }
    }

    private fun attachLocalPlayerCastGuard(playerManager: Any) {
        runOnMainThread {
            val exoPlayer = extractLocalExoPlayer(playerManager) ?: return@runOnMainThread
            val previous = guardedExoPlayerRef?.get()
            if (previous === exoPlayer) return@runOnMainThread
            try {
                previous?.removeListener(localPlayerCastGuardListener)
            } catch (_: Throwable) {
            }
            try {
                exoPlayer.addListener(localPlayerCastGuardListener)
                guardedExoPlayerRef = WeakReference(exoPlayer)
            } catch (_: Throwable) {
            }
        }
    }

    private fun pauseLocalPlayerForCast() {
        runOnMainThread {
            val exoPlayer = extractLocalExoPlayer() ?: return@runOnMainThread
            pausedLocalPlayerForCast = true
            try {
                if (exoPlayer.playWhenReady || exoPlayer.isPlaying) {
                    exoPlayer.playWhenReady = false
                    exoPlayer.pause()
                }
            } catch (_: Throwable) {
            }
        }
    }

    private fun resumeLocalPlayerAfterCast() {
        runOnMainThread {
            if (!pausedLocalPlayerForCast) return@runOnMainThread
            pausedLocalPlayerForCast = false
            val exoPlayer = extractLocalExoPlayer() ?: return@runOnMainThread
            try {
                exoPlayer.playWhenReady = true
                exoPlayer.play()
            } catch (_: Throwable) {
            }
        }
    }

    private fun syncLocalPlayerForCastState() {
        if (isRemotePlaybackActive()) {
            pauseLocalPlayerForCast()
        } else if (pausedLocalPlayerForCast) {
            resumeLocalPlayerAfterCast()
        }
    }

    private fun runOnMainThread(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            block()
        } else {
            mainHandler.post(block)
        }
    }

    private fun startWebClientPruneLoop() {
        webClientPruneJob?.cancel()
        webClientPruneJob = ioScope.launch {
            while (isActive && isWebServerRunning) {
                delay(4_000L)
                pruneStaleWebClients()
            }
        }
    }

    private fun pruneStaleWebClients() {
        val now = System.currentTimeMillis()
        webClientLastSeen.entries.removeIf { now - it.value > WEB_CLIENT_TIMEOUT_MS }
        val activeCount = webClientLastSeen.size
        if (connectedWebClientCount != activeCount) {
            runOnMainThread {
                connectedWebClientCount = activeCount
                syncLocalPlayerForCastState()
            }
        }
    }

    private fun sendHttp403(client: Socket, reason: String = "Forbidden") {
        try {
            val body = reason.toByteArray(Charsets.UTF_8)
            val out = BufferedOutputStream(client.getOutputStream())
            val headers = buildString {
                append("HTTP/1.1 403 Forbidden\r\n")
                append("Content-Type: text/plain; charset=utf-8\r\n")
                append("Content-Length: ${body.size}\r\n")
                append("Cache-Control: no-store\r\n")
                append("Connection: close\r\n\r\n")
            }
            out.write(headers.toByteArray(Charsets.UTF_8))
            out.write(body)
            out.flush()
        } catch (_: Throwable) {
        }
    }

    private val XTREAM_CREDENTIAL_REGEX = Regex("(/(?:live|movie)/)([^/?#]+)/([^/?#]+)(/)", RegexOption.IGNORE_CASE)
    private const val WEB_TOKEN_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
    private const val WEB_TOKEN_LENGTH = 6
    private const val WEB_CLIENT_TIMEOUT_MS = 12_000L

    fun updateCurrentChannel(
        context: Context,
        channelName: String,
        category: String,
        language: String,
        logoUrl: String,
        streamUrl: String
    ) {
        val cleanedUrl = stripPipeSuffix(streamUrl)
        if (cleanedUrl.isEmpty()) return

        val prev = currentChannel
        val changed = prev.streamUrl != cleanedUrl || prev.name != channelName
        currentChannel = CastChannelSnapshot(
            name = channelName.ifBlank { "Live TV Channel" },
            category = category.ifBlank { "Live TV" },
            language = language,
            logoUrl = logoUrl,
            streamUrl = cleanedUrl
        )

        if (changed || allowedProxyHosts.isEmpty()) {
            resetAllowedProxyHosts(cleanedUrl)
        }

        if (changed) {
            streamRevision++
            isRemotePaused = false
            // Auto-sync new channel to connected DLNA TV if active
            val targetDevice = connectedDevice
            if (activeMode == ActiveCastMode.DLNA_DEVICE && targetDevice != null) {
                castToDevice(context, targetDevice, showToast = false)
            } else if (isWebServerRunning) {
                statusMessage = "Web TV synced: ${currentChannel.name}"
            }
            if (isRemotePlaybackActive()) {
                pauseLocalPlayerForCast()
            }
        }
    }

    /**
     * Starts hybrid discovery across:
     * 1. Android System MediaRouter (Wireless Display / Chromecast / Miracast routes)
     * 2. UDP SSDP Multicast (239.255.255.250:1900) for DLNA MediaRenderer, AVTransport & DIAL Smart TVs
     */
    fun startDiscovery(context: Context) {
        if (!scanningGuard.compareAndSet(false, true)) return
        isScanning = true
        statusMessage = "Scanning Wi-Fi for Smart TVs, DLNA & Cast devices..."

        // 1. Check System MediaRouter on Main Thread
        mainHandler.post {
            try {
                val router = context.getSystemService(Context.MEDIA_ROUTER_SERVICE) as? MediaRouter
                if (router != null) {
                    val count = router.routeCount
                    for (i in 0 until count) {
                        val route = router.getRouteAt(i) ?: continue
                        val name = route.name?.toString()?.trim().orEmpty()
                        val desc = route.description?.toString()?.trim().orEmpty()
                        if (name.isEmpty()) continue
                        val lower = name.lowercase(Locale.US)
                        val isPhoneOrSpeaker = lower.contains("phone") ||
                            lower.contains("speaker") ||
                            lower.contains("earpiece") ||
                            lower.contains("headphone") ||
                            lower.contains("bluetooth")
                        if (!isPhoneOrSpeaker && (route.supportedTypes and MediaRouter.ROUTE_TYPE_LIVE_VIDEO) != 0) {
                            val id = "sys_route_${name.lowercase(Locale.US)}"
                            val dev = CastDevice(
                                id = id,
                                name = name,
                                modelOrManufacturer = desc.ifEmpty { "System Wireless Display" },
                                host = "System Cast",
                                locationUrl = id,
                                avTransportControlUrl = null,
                                renderingControlUrl = null,
                                dialApplicationUrl = null,
                                protocol = DeviceProtocol.SYSTEM_ROUTE
                            )
                            addOrUpdateDevice(dev)
                        }
                    }
                }
            } catch (t: Throwable) {
                Log.w(TAG, "MediaRouter scan warning", t)
            }
        }

        // 2. SSDP Multicast Discovery on IO Thread
        ioScope.launch {
            var multicastLock: WifiManager.MulticastLock? = null
            try {
                val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
                multicastLock = wifiManager?.createMulticastLock("hypnotix_hybrid_ssdp_lock")?.apply {
                    setReferenceCounted(false)
                    acquire()
                }
            } catch (t: Throwable) {
                Log.w(TAG, "MulticastLock acquire skipped", t)
            }

            try {
                val searchTargets = listOf(
                    "urn:schemas-upnp-org:device:MediaRenderer:1",
                    "urn:schemas-upnp-org:service:AVTransport:1",
                    "urn:dial-multiscreen-org:service:dial:1",
                    "ssdp:all"
                )

                val multicastAddr = InetAddress.getByName("239.255.255.250")
                val socket = DatagramSocket(null).apply {
                    reuseAddress = true
                    soTimeout = 700
                    bind(InetSocketAddress(0))
                }

                socket.use { udp ->
                    for (st in searchTargets) {
                        val query = buildString {
                            append("M-SEARCH * HTTP/1.1\r\n")
                            append("HOST: 239.255.255.250:1900\r\n")
                            append("MAN: \"ssdp:discover\"\r\n")
                            append("MX: 2\r\n")
                            append("ST: $st\r\n")
                            append("USER-AGENT: Android/14 UPnP/1.1 HypnotixIPTV/1.1\r\n")
                            append("\r\n")
                        }
                        val bytes = query.toByteArray(Charsets.UTF_8)
                        val packet = DatagramPacket(bytes, bytes.size, multicastAddr, 1900)
                        try {
                            udp.send(packet)
                        } catch (e: Throwable) {
                            Log.w(TAG, "SSDP send warning for $st", e)
                        }
                    }

                    val deadline = System.currentTimeMillis() + 3400L
                    val buf = ByteArray(4096)
                    while (System.currentTimeMillis() < deadline) {
                        try {
                            val recv = DatagramPacket(buf, buf.size)
                            udp.receive(recv)
                            val raw = String(recv.data, 0, recv.length, Charsets.UTF_8)
                            val senderIp = recv.address?.hostAddress ?: ""
                            ioScope.launch {
                                parseSsdpResponse(raw, senderIp)
                            }
                        } catch (_: java.net.SocketTimeoutException) {
                            // Continue listening until deadline
                        } catch (_: Throwable) {
                            break
                        }
                    }
                }
            } catch (t: Throwable) {
                Log.w(TAG, "SSDP discovery error", t)
            } finally {
                try {
                    if (multicastLock?.isHeld == true) {
                        multicastLock.release()
                    }
                } catch (_: Throwable) {
                }
                mainHandler.post {
                    isScanning = false
                    scanningGuard.set(false)
                    statusMessage = if (discoveredDevices.isNotEmpty()) {
                        "Found ${discoveredDevices.size} device(s) on network"
                    } else if (isWebServerRunning) {
                        "Web TV Active at $webServerUrl"
                    } else {
                        "No DLNA TV auto-detected. Use Web TV Cast or System Cast below."
                    }
                }
            }
        }
    }

    /**
     * Probes a manually entered Smart TV IP address across standard DLNA / UPnP / Cast ports.
     */
    fun probeManualTvIp(context: Context, rawInput: String) {
        val cleaned = rawInput.trim()
            .removePrefix("http://")
            .removePrefix("https://")
            .substringBefore("/")
            .trim()
        if (cleaned.isEmpty()) {
            Toast.makeText(context, "Enter your Smart TV IP (e.g. 192.168.1.50)", Toast.LENGTH_SHORT).show()
            return
        }

        isScanning = true
        statusMessage = "Probing Smart TV at $cleaned..."
        ioScope.launch {
            var foundAny = false
            val candidateUrls = if (cleaned.contains(":")) {
                listOf(
                    "http://$cleaned/description.xml",
                    "http://$cleaned/dmr.xml",
                    "http://$cleaned/ssdp/device-desc.xml",
                    "http://$cleaned/xml/device_description.xml",
                    "http://$cleaned/"
                )
            } else {
                listOf(
                    "http://$cleaned:8008/ssdp/device-desc.xml",
                    "http://$cleaned:9197/dmr",
                    "http://$cleaned:7676/smp_10_",
                    "http://$cleaned:52235/dmr/SamsungMRDesc.xml",
                    "http://$cleaned:49152/description.xml",
                    "http://$cleaned:49153/description.xml",
                    "http://$cleaned:1400/xml/device_description.xml",
                    "http://$cleaned:8060/",
                    "http://$cleaned:8200/rootDesc.xml"
                )
            }

            for (url in candidateUrls) {
                val dev = fetchAndParseDeviceDescriptor(url, cleaned, null)
                if (dev != null) {
                    addOrUpdateDevice(dev)
                    foundAny = true
                    break
                }
            }

            mainHandler.post {
                isScanning = false
                if (foundAny) {
                    statusMessage = "Found Smart TV at $cleaned!"
                    Toast.makeText(context, "Smart TV connected from $cleaned", Toast.LENGTH_SHORT).show()
                } else {
                    statusMessage = "No DLNA descriptor on $cleaned — use Web TV Cast link on TV browser!"
                    Toast.makeText(
                        context,
                        "DLNA port not open on $cleaned. Turn on Web TV Server below & open link in TV browser!",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    private fun parseSsdpResponse(raw: String, senderIp: String) {
        val headers = HashMap<String, String>()
        for (line in raw.split("\r\n", "\n")) {
            val idx = line.indexOf(':')
            if (idx > 0) {
                val k = line.substring(0, idx).trim().uppercase(Locale.US)
                val v = line.substring(idx + 1).trim()
                headers[k] = v
            }
        }
        val location = headers["LOCATION"] ?: return
        val dialUrl = headers["APPLICATION-URL"]
        val st = headers["ST"].orEmpty()
        val server = headers["SERVER"].orEmpty()

        // Skip common routers/gateways that have no media renderer or DIAL capability
        if (st.contains("InternetGatewayDevice", ignoreCase = true) ||
            st.contains("WANDevice", ignoreCase = true) ||
            st.contains("WANConnectionDevice", ignoreCase = true)
        ) {
            return
        }

        val dev = fetchAndParseDeviceDescriptor(location, senderIp, dialUrl, server)
        if (dev != null) {
            addOrUpdateDevice(dev)
        }
    }

    private fun fetchAndParseDeviceDescriptor(
        locationUrl: String,
        fallbackHost: String,
        dialHeaderUrl: String?,
        serverHeader: String = ""
    ): CastDevice? {
        seenDeviceLocations[locationUrl]?.let { return it }
        return try {
            val url = URL(locationUrl)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 1800
                readTimeout = 1800
                requestMethod = "GET"
                setRequestProperty("User-Agent", "Android/14 UPnP/1.1 HypnotixIPTV/1.1")
            }
            val code = conn.responseCode
            val dialAppHeader = conn.getHeaderField("Application-URL") ?: dialHeaderUrl
            if (code !in 200..299) {
                conn.disconnect()
                return null
            }
            val xml = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            conn.disconnect()

            val friendlyName = extractXmlTag(xml, "friendlyName")
                .ifBlank { extractXmlTag(xml, "modelName") }
                .ifBlank { "Smart TV ($fallbackHost)" }
            val manufacturer = extractXmlTag(xml, "manufacturer")
            val modelName = extractXmlTag(xml, "modelName")

            // Ignore purely router/gateway descriptors unless they expose AVTransport or DIAL
            val isGatewayOnly = xml.contains("InternetGatewayDevice", ignoreCase = true) &&
                !xml.contains("AVTransport", ignoreCase = true) &&
                dialAppHeader.isNullOrBlank()
            if (isGatewayOnly) return null

            val avTransportControl = extractServiceControlUrl(xml, "AVTransport", locationUrl)
            val renderingControl = extractServiceControlUrl(xml, "RenderingControl", locationUrl)

            val hasMediaCapability = avTransportControl != null ||
                !dialAppHeader.isNullOrBlank() ||
                xml.contains("MediaRenderer", ignoreCase = true) ||
                serverHeader.contains("Chromecast", ignoreCase = true) ||
                serverHeader.contains("DLNA", ignoreCase = true) ||
                serverHeader.contains("UPnP", ignoreCase = true)

            if (!hasMediaCapability) return null

            val protocol = when {
                avTransportControl != null -> DeviceProtocol.DLNA_UPNP
                !dialAppHeader.isNullOrBlank() -> DeviceProtocol.DIAL_CAST
                else -> DeviceProtocol.DLNA_UPNP
            }

            val modelSummary = listOf(manufacturer, modelName)
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .distinct()
                .joinToString(" • ")
                .ifEmpty { protocol.badgeLabel }

            val host = url.host?.takeIf { it.isNotBlank() } ?: fallbackHost
            val device = CastDevice(
                id = locationUrl,
                name = friendlyName,
                modelOrManufacturer = modelSummary,
                host = host,
                locationUrl = locationUrl,
                avTransportControlUrl = avTransportControl,
                renderingControlUrl = renderingControl,
                dialApplicationUrl = dialAppHeader,
                protocol = protocol
            )
            seenDeviceLocations[locationUrl] = device
            device
        } catch (_: Throwable) {
            null
        }
    }

    private fun addOrUpdateDevice(device: CastDevice) {
        mainHandler.post {
            val existingIdx = discoveredDevices.indexOfFirst {
                it.id == device.id || (it.host == device.host && it.name.equals(device.name, ignoreCase = true))
            }
            if (existingIdx >= 0) {
                val existing = discoveredDevices[existingIdx]
                // Prefer the entry that has avTransportControlUrl populated
                if (existing.avTransportControlUrl == null && device.avTransportControlUrl != null) {
                    discoveredDevices[existingIdx] = device
                }
            } else {
                discoveredDevices.add(device)
            }
        }
    }

    /**
     * Casts the current live channel to a discovered Smart TV / DLNA / System route device.
     */
    fun castToDevice(context: Context, device: CastDevice, showToast: Boolean = true) {
        val snap = currentChannel
        val cleanStreamUrl = stripPipeSuffix(snap.streamUrl)
        if (cleanStreamUrl.isBlank()) {
            if (showToast) {
                Toast.makeText(context, "Play a live channel first to cast", Toast.LENGTH_SHORT).show()
            }
            return
        }

        if (device.protocol == DeviceProtocol.SYSTEM_ROUTE) {
            openSystemCastSettings(context)
            return
        }

        resetAllowedProxyHosts(cleanStreamUrl)

        val controlUrl = device.avTransportControlUrl
        if (controlUrl == null) {
            // Device supports DIAL/Cast but not direct DLNA AVTransport -> start Web TV Server + offer Cast Settings
            ensureWebServerRunning(context)
            connectedDevice = device
            activeMode = ActiveCastMode.WEB_TV_SERVER
            statusMessage = "Web TV Ready for ${device.name}: $webServerUrl"
            if (showToast) {
                Toast.makeText(
                    context,
                    "${device.name} uses Cast/Web receiver. Open $webServerUrl on TV or use System Cast!",
                    Toast.LENGTH_LONG
                ).show()
            }
            return
        }

        // If proxy mode for DLNA is enabled, ensure local server is running so we can pass the proxied URL
        if (useProxyForDlna && !isWebServerRunning) {
            ensureWebServerRunning(context)
        }

        val effectiveStreamUrl = if (useProxyForDlna && webServerBaseUrl.isNotEmpty()) {
            buildAbsoluteProxyUrl(cleanStreamUrl)
        } else {
            cleanStreamUrl
        }

        statusMessage = "Sending '${snap.name}' to ${device.name}..."
        ioScope.launch {
            val success = sendDlnaSetAvTransportAndPlay(
                controlUrl = controlUrl,
                streamUrl = effectiveStreamUrl,
                channelName = snap.name,
                category = snap.category,
                logoUrl = snap.logoUrl
            )
            mainHandler.post {
                if (success) {
                    connectedDevice = device
                    activeMode = ActiveCastMode.DLNA_DEVICE
                    isRemotePaused = false
                    statusMessage = "Casting '${snap.name}' on ${device.name}"
                    syncLocalPlayerForCastState()
                    if (showToast) {
                        Toast.makeText(context, "Casting '${snap.name}' to ${device.name}", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    // Fallback: auto-enable Web TV Server so user always has an instant working fallback
                    ensureWebServerRunning(context)
                    statusMessage = "DLNA rejected stream format — Web TV Server enabled ($webServerUrl)"
                    if (showToast) {
                        Toast.makeText(
                            context,
                            "TV rejected direct DLNA stream. Try toggling 'Proxy Mode' or open $webServerUrl on TV!",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
        }
    }

    /**
     * Sends remote playback commands (PLAY, PAUSE, RELOAD, MUTE_TOGGLE, STOP) to active DLNA TV
     * and/or connected Web TV Receiver browsers.
     */
    fun sendRemoteCommand(context: Context, command: String) {
        remoteCommandSeq++
        lastRemoteCommand = command

        when (command) {
            "PAUSE" -> isRemotePaused = true
            "PLAY" -> isRemotePaused = false
            "MUTE_TOGGLE" -> isRemoteMuted = !isRemoteMuted
            "RELOAD" -> {
                isRemotePaused = false
                streamRevision++
            }
            "STOP" -> {
                isRemotePaused = false
            }
        }

        val dev = connectedDevice
        val controlUrl = dev?.avTransportControlUrl
        val renderUrl = dev?.renderingControlUrl

        if (activeMode == ActiveCastMode.DLNA_DEVICE && dev != null && controlUrl != null) {
            if (command == "RELOAD") {
                castToDevice(context, dev, showToast = true)
                return
            }
            ioScope.launch {
                when (command) {
                    "PAUSE" -> sendSoapAction(
                        controlUrl = controlUrl,
                        serviceType = "urn:schemas-upnp-org:service:AVTransport:1",
                        action = "Pause",
                        bodyXml = "<InstanceID>0</InstanceID>"
                    )
                    "PLAY" -> sendSoapAction(
                        controlUrl = controlUrl,
                        serviceType = "urn:schemas-upnp-org:service:AVTransport:1",
                        action = "Play",
                        bodyXml = "<InstanceID>0</InstanceID><Speed>1</Speed>"
                    )
                    "STOP" -> sendSoapAction(
                        controlUrl = controlUrl,
                        serviceType = "urn:schemas-upnp-org:service:AVTransport:1",
                        action = "Stop",
                        bodyXml = "<InstanceID>0</InstanceID>"
                    )
                    "MUTE_TOGGLE" -> {
                        if (renderUrl != null) {
                            val muteVal = if (isRemoteMuted) "1" else "0"
                            sendSoapAction(
                                controlUrl = renderUrl,
                                serviceType = "urn:schemas-upnp-org:service:RenderingControl:1",
                                action = "SetMute",
                                bodyXml = "<InstanceID>0</InstanceID><Channel>Master</Channel><DesiredMute>$muteVal</DesiredMute>"
                            )
                        }
                    }
                }
            }
        }

        if (command == "STOP") {
            connectedDevice = null
            if (activeMode == ActiveCastMode.DLNA_DEVICE || activeMode == ActiveCastMode.SYSTEM_MIRROR) {
                activeMode = if (isWebServerRunning) ActiveCastMode.WEB_TV_SERVER else ActiveCastMode.NONE
            }
            statusMessage = if (isWebServerRunning) {
                "DLNA stopped • Web TV active at $webServerUrl"
            } else {
                "Cast session stopped"
            }
            syncLocalPlayerForCastState()
        } else {
            statusMessage = "Sent $command to Cast receiver"
        }
    }

    fun disconnectAll(context: Context) {
        val dev = connectedDevice
        val controlUrl = dev?.avTransportControlUrl
        if (controlUrl != null) {
            ioScope.launch {
                sendSoapAction(
                    controlUrl = controlUrl,
                    serviceType = "urn:schemas-upnp-org:service:AVTransport:1",
                    action = "Stop",
                    bodyXml = "<InstanceID>0</InstanceID>"
                )
            }
        }
        stopWebCastServer()
        connectedDevice = null
        activeMode = ActiveCastMode.NONE
        isRemotePaused = false
        statusMessage = "Cast disconnected"
        syncLocalPlayerForCastState()
        Toast.makeText(context, "Disconnected from Cast", Toast.LENGTH_SHORT).show()
    }

    private fun sendDlnaSetAvTransportAndPlay(
        controlUrl: String,
        streamUrl: String,
        channelName: String,
        category: String,
        logoUrl: String
    ): Boolean {
        val isHls = streamUrl.contains(".m3u8", ignoreCase = true) ||
            streamUrl.contains("m3u8", ignoreCase = true)
        val mime = when {
            isHls -> "application/vnd.apple.mpegurl"
            streamUrl.endsWith(".ts", ignoreCase = true) -> "video/mp2t"
            streamUrl.endsWith(".mp4", ignoreCase = true) -> "video/mp4"
            else -> "application/vnd.apple.mpegurl"
        }
        val protocolInfo = "http-get:*:$mime:DLNA.ORG_OP=01;DLNA.ORG_CI=0;DLNA.ORG_FLAGS=01700000000000000000000000000000"

        val didlRaw = buildString {
            append("""<DIDL-Lite xmlns="urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/" """)
            append("""xmlns:dc="http://purl.org/dc/elements/1.1/" """)
            append("""xmlns:upnp="urn:schemas-upnp-org:metadata-1-0/upnp/" """)
            append("""xmlns:sec="http://www.sec.co.kr/">""")
            append("""<item id="0" parentID="-1" restricted="1">""")
            append("""<dc:title>${escapeXml(channelName)}</dc:title>""")
            append("""<upnp:artist>${escapeXml(category.ifBlank { "Live TV" })}</upnp:artist>""")
            if (logoUrl.isNotBlank()) {
                append("""<upnp:albumArtURI>${escapeXml(logoUrl)}</upnp:albumArtURI>""")
            }
            append("""<upnp:class>object.item.videoItem.videoBroadcast</upnp:class>""")
            append("""<res protocolInfo="${escapeXml(protocolInfo)}">${escapeXml(streamUrl)}</res>""")
            append("""</item></DIDL-Lite>""")
        }

        // 1. Optional Stop to clear previous transport state
        sendSoapAction(
            controlUrl = controlUrl,
            serviceType = "urn:schemas-upnp-org:service:AVTransport:1",
            action = "Stop",
            bodyXml = "<InstanceID>0</InstanceID>"
        )

        // 2. SetAVTransportURI
        val setUriBody = buildString {
            append("<InstanceID>0</InstanceID>")
            append("<CurrentURI>${escapeXml(streamUrl)}</CurrentURI>")
            append("<CurrentURIMetaData>${escapeXml(didlRaw)}</CurrentURIMetaData>")
        }
        val setOk = sendSoapAction(
            controlUrl = controlUrl,
            serviceType = "urn:schemas-upnp-org:service:AVTransport:1",
            action = "SetAVTransportURI",
            bodyXml = setUriBody
        )
        if (!setOk) return false

        // 3. Play
        return sendSoapAction(
            controlUrl = controlUrl,
            serviceType = "urn:schemas-upnp-org:service:AVTransport:1",
            action = "Play",
            bodyXml = "<InstanceID>0</InstanceID><Speed>1</Speed>"
        )
    }

    private fun sendSoapAction(
        controlUrl: String,
        serviceType: String,
        action: String,
        bodyXml: String
    ): Boolean {
        return try {
            val envelope = buildString {
                append("""<?xml version="1.0" encoding="utf-8"?>""")
                append("""<s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" """)
                append("""s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">""")
                append("""<s:Body>""")
                append("""<u:$action xmlns:u="$serviceType">""")
                append(bodyXml)
                append("""</u:$action>""")
                append("""</s:Body></s:Envelope>""")
            }
            val payload = envelope.toByteArray(Charsets.UTF_8)
            val conn = (URL(controlUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 3500
                readTimeout = 3500
                doOutput = true
                setRequestProperty("Content-Type", "text/xml; charset=\"utf-8\"")
                setRequestProperty("SOAPAction", "\"$serviceType#$action\"")
                setRequestProperty("Content-Length", payload.size.toString())
                setRequestProperty("Connection", "close")
            }
            conn.outputStream.use { it.write(payload) }
            val code = conn.responseCode
            conn.disconnect()
            code in 200..299
        } catch (t: Throwable) {
            Log.w(TAG, "SOAP $action failed on $controlUrl", t)
            false
        }
    }

    // =========================================================================
    // Embedded Wi-Fi Web TV Cast Server (Universal Smart TV Browser Receiver)
    // =========================================================================

    fun ensureWebServerRunning(context: Context): String {
        if (isWebServerRunning && webServerSocketRef.get()?.isClosed == false) {
            val ip = detectLocalLanIp(context)
            webServerBaseUrl = "http://$ip:$webServerPort"
            webServerUrl = buildWebServerUrl(ip, webServerPort, webAuthToken)
            return webServerUrl
        }
        startWebCastServer(context)
        return webServerUrl
    }

    fun startWebCastServer(context: Context) {
        if (isWebServerRunning && webServerSocketRef.get()?.isClosed == false) return

        val lanIp = detectLocalLanIp(context)
        var boundSocket: ServerSocket? = null
        var chosenPort = 8989
        for (candidatePort in 8989..8998) {
            try {
                val ss = ServerSocket()
                ss.reuseAddress = true
                ss.bind(InetSocketAddress("0.0.0.0", candidatePort), 32)
                boundSocket = ss
                chosenPort = candidatePort
                break
            } catch (_: Throwable) {
            }
        }

        if (boundSocket == null) {
            statusMessage = "Unable to bind local Web TV port"
            return
        }

        val token = generateWebAuthToken()
        webAuthToken = token
        webServerSocketRef.set(boundSocket)
        webServerPort = chosenPort
        webServerBaseUrl = "http://$lanIp:$chosenPort"
        webServerUrl = buildWebServerUrl(lanIp, chosenPort, token)
        isWebServerRunning = true
        if (activeMode == ActiveCastMode.NONE) {
            activeMode = ActiveCastMode.WEB_TV_SERVER
        }
        statusMessage = "Web TV Ready: Open $webServerUrl on Smart TV Browser"
        startWebClientPruneLoop()

        serverExecutor.execute {
            while (!boundSocket.isClosed) {
                try {
                    val client = boundSocket.accept()
                    serverExecutor.execute {
                        handleHttpClient(client)
                    }
                } catch (_: Throwable) {
                    if (boundSocket.isClosed) break
                }
            }
        }
    }

    fun stopWebCastServer() {
        webClientPruneJob?.cancel()
        webClientPruneJob = null
        try {
            webServerSocketRef.getAndSet(null)?.close()
        } catch (_: Throwable) {
        }
        webClientLastSeen.clear()
        connectedWebClientCount = 0
        isWebServerRunning = false
        webAuthToken = ""
        webServerBaseUrl = ""
        webServerUrl = ""
        if (activeMode == ActiveCastMode.WEB_TV_SERVER) {
            activeMode = if (connectedDevice != null) ActiveCastMode.DLNA_DEVICE else ActiveCastMode.NONE
        }
        syncLocalPlayerForCastState()
    }

    private fun handleHttpClient(socket: Socket) {
        socket.use { client ->
            try {
                client.soTimeout = 10000
                val reader = BufferedReader(InputStreamReader(client.getInputStream(), Charsets.UTF_8))
                val requestLine = reader.readLine() ?: return
                val parts = requestLine.split(" ")
                if (parts.size < 2) return
                val fullPath = parts[1]

                // Consume headers
                while (true) {
                    val line = reader.readLine()
                    if (line.isNullOrEmpty()) break
                }

                if (!isValidWebRequestToken(fullPath)) {
                    sendHttp403(client, "Forbidden: missing or invalid token")
                    return
                }

                val pathOnly = fullPath.substringBefore('?')
                val remoteIp = client.inetAddress?.hostAddress ?: "tv-client"
                val now = System.currentTimeMillis()
                webClientLastSeen[remoteIp] = now
                // Prune clients inactive > 12s
                webClientLastSeen.entries.removeIf { now - it.value > WEB_CLIENT_TIMEOUT_MS }
                val activeCount = webClientLastSeen.size
                if (connectedWebClientCount != activeCount) {
                    runOnMainThread {
                        connectedWebClientCount = activeCount
                        syncLocalPlayerForCastState()
                    }
                }

                when {
                    pathOnly == "/" || pathOnly.startsWith("/index") -> {
                        val htmlBytes = buildWebTvReceiverHtml(webAuthToken).toByteArray(Charsets.UTF_8)
                        val out = BufferedOutputStream(client.getOutputStream())
                        val headers = buildString {
                            append("HTTP/1.1 200 OK\r\n")
                            append("Content-Type: text/html; charset=utf-8\r\n")
                            append("Content-Length: ${htmlBytes.size}\r\n")
                            append("Cache-Control: no-store\r\n")
                            append("Access-Control-Allow-Origin: *\r\n")
                            append("Connection: close\r\n\r\n")
                        }
                        out.write(headers.toByteArray(Charsets.UTF_8))
                        out.write(htmlBytes)
                        out.flush()
                    }

                    pathOnly == "/api/state" -> {
                        val snap = currentChannel
                        val cleanStreamUrl = stripPipeSuffix(snap.streamUrl)
                        val proxiedPath = if (cleanStreamUrl.isNotBlank()) {
                            buildRelativeProxyPath(cleanStreamUrl, webAuthToken)
                        } else {
                            ""
                        }
                        val json = JSONObject().apply {
                            put("channelName", snap.name.ifBlank { "Waiting for channel..." })
                            put("category", snap.category.ifBlank { "Live TV" })
                            put("language", snap.language)
                            put("logoUrl", snap.logoUrl)
                            put("directStreamUrl", cleanStreamUrl)
                            put("proxiedStreamUrl", proxiedPath)
                            put("preferProxy", useProxyForWebTv)
                            put("revision", streamRevision)
                            put("commandSeq", remoteCommandSeq)
                            put("command", lastRemoteCommand.trim())
                            put("paused", isRemotePaused)
                            put("muted", isRemoteMuted)
                        }.toString()
                        val bytes = json.toByteArray(Charsets.UTF_8)
                        val out = BufferedOutputStream(client.getOutputStream())
                        val headers = buildString {
                            append("HTTP/1.1 200 OK\r\n")
                            append("Content-Type: application/json; charset=utf-8\r\n")
                            append("Content-Length: ${bytes.size}\r\n")
                            append("Cache-Control: no-store\r\n")
                            append("Access-Control-Allow-Origin: *\r\n")
                            append("Connection: close\r\n\r\n")
                        }
                        out.write(headers.toByteArray(Charsets.UTF_8))
                        out.write(bytes)
                        out.flush()
                    }

                    pathOnly == "/proxy" -> {
                        val targetUrl = extractQueryParam(fullPath, "url").orEmpty()
                        if (!isAllowedProxyUrl(targetUrl)) {
                            sendHttp403(client, "Forbidden: proxy host not allowed")
                            return
                        }
                        handleStreamProxyRequest(client, targetUrl)
                    }

                    else -> {
                        val out = OutputStreamWriter(client.getOutputStream(), Charsets.UTF_8)
                        out.write("HTTP/1.1 404 Not Found\r\nConnection: close\r\n\r\n")
                        out.flush()
                    }
                }
            } catch (t: Throwable) {
                Log.d(TAG, "Web TV client closed: ${t.message}")
            }
        }
    }

    /**
     * Built-in CORS-free M3U8 & media segment proxy so Smart TV browsers can play any IPTV stream
     * even when the upstream server blocks browser CORS or requires an IPTV User-Agent.
     */
    private fun handleStreamProxyRequest(client: Socket, targetUrl: String) {
        if (!isAllowedProxyUrl(targetUrl)) {
            sendHttp403(client, "Forbidden: proxy host not allowed")
            return
        }
        var conn: HttpURLConnection? = null
        try {
            var currentUrl = targetUrl
            var redirects = 0
            while (redirects < 5) {
                conn = (URL(currentUrl).openConnection() as HttpURLConnection).apply {
                    instanceFollowRedirects = false
                    connectTimeout = 6000
                    readTimeout = 10000
                    requestMethod = "GET"
                    setRequestProperty("User-Agent", "Lavf/60.3.100")
                    setRequestProperty("Accept", "*/*")
                }
                val code = conn.responseCode
                if (code in 300..399) {
                    val loc = conn.getHeaderField("Location") ?: break
                    val resolved = URI(currentUrl).resolve(loc).toString()
                    conn.disconnect()
                    if (extractNormalizedHttpHost(resolved) == null) {
                        sendHttp403(client, "Forbidden: invalid redirect target")
                        return
                    }
                    allowProxyHostFromUrl(resolved)
                    currentUrl = resolved
                    redirects++
                } else {
                    break
                }
            }

            val finalConn = conn ?: return
            val contentType = finalConn.contentType ?: ""
            val isM3u8 = currentUrl.contains(".m3u8", ignoreCase = true) ||
                contentType.contains("mpegurl", ignoreCase = true)

            val out = BufferedOutputStream(client.getOutputStream())
            if (isM3u8) {
                val rawText = finalConn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                val baseUri = URI(currentUrl)
                val rewritten = buildString {
                    for (line in rawText.lines()) {
                        val trimmed = line.trim()
                        if (trimmed.isEmpty()) {
                            append("\n")
                        } else if (trimmed.startsWith("#")) {
                            // Also rewrite URI="..." inside #EXT-X-KEY or #EXT-X-MAP
                            if (trimmed.contains("URI=\"")) {
                                val updatedTag = Regex("""URI="([^"]+)"""").replace(trimmed) { match ->
                                    val rawUri = match.groupValues[1]
                                    val absUri = runCatching { baseUri.resolve(rawUri).toString() }.getOrDefault(rawUri)
                                    val proxied = buildRelativeProxyPath(absUri, webAuthToken)
                                    """URI="$proxied""""
                                }
                                append(updatedTag).append("\n")
                            } else {
                                append(trimmed).append("\n")
                            }
                        } else {
                            val absUrl = runCatching { baseUri.resolve(trimmed).toString() }.getOrDefault(trimmed)
                            val proxiedLine = buildRelativeProxyPath(absUrl, webAuthToken)
                            append(proxiedLine).append("\n")
                        }
                    }
                }
                val body = rewritten.toByteArray(Charsets.UTF_8)
                val header = buildString {
                    append("HTTP/1.1 200 OK\r\n")
                    append("Content-Type: application/vnd.apple.mpegurl\r\n")
                    append("Content-Length: ${body.size}\r\n")
                    append("Access-Control-Allow-Origin: *\r\n")
                    append("Cache-Control: no-store\r\n")
                    append("Connection: close\r\n\r\n")
                }
                out.write(header.toByteArray(Charsets.UTF_8))
                out.write(body)
                out.flush()
            } else {
                val effectiveType = contentType.ifBlank { "video/mp2t" }
                val header = buildString {
                    append("HTTP/1.1 200 OK\r\n")
                    append("Content-Type: $effectiveType\r\n")
                    append("Access-Control-Allow-Origin: *\r\n")
                    append("Cache-Control: no-store\r\n")
                    append("Connection: close\r\n\r\n")
                }
                out.write(header.toByteArray(Charsets.UTF_8))
                val buf = ByteArray(16384)
                finalConn.inputStream.use { input ->
                    while (true) {
                        val read = input.read(buf)
                        if (read <= 0) break
                        out.write(buf, 0, read)
                    }
                }
                out.flush()
            }
        } catch (_: Throwable) {
        } finally {
            try {
                conn?.disconnect()
            } catch (_: Throwable) {
            }
        }
    }

    private fun buildWebTvReceiverHtml(authToken: String): String {
        return """
<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="UTF-8" />
<meta name="viewport" content="width=device-width,initial-scale=1.0,maximum-scale=1.0,user-scalable=no" />
<title>Hypnotix Smart TV Cast Receiver</title>
<script src="https://cdn.jsdelivr.net/npm/hls.js@1.5.15/dist/hls.min.js"></script>
<style>
  * { box-sizing: border-box; margin: 0; padding: 0; }
  html, body { width: 100%; height: 100%; background: #000; color: #fff; font-family: system-ui, -apple-system, sans-serif; overflow: hidden; }
  #player-wrap { position: relative; width: 100vw; height: 100vh; display: flex; align-items: center; justify-content: center; background: #000; }
  video { width: 100%; height: 100%; object-fit: contain; background: #000; }
  #osd {
    position: absolute; top: 24px; left: 28px; right: 28px;
    display: flex; align-items: center; justify-content: space-between;
    background: rgba(14, 14, 18, 0.86); border: 1px solid rgba(255,255,255,0.14);
    border-radius: 16px; padding: 14px 20px; backdrop-filter: blur(12px);
    transition: opacity 0.4s ease;
  }
  .ch-left { display: flex; align-items: center; gap: 14px; }
  #ch-logo { width: 48px; height: 48px; border-radius: 10px; object-fit: contain; background: #1c1c22; padding: 4px; }
  .ch-title { font-size: 20px; font-weight: 700; color: #fff; }
  .ch-sub { display: flex; align-items: center; gap: 8px; margin-top: 4px; font-size: 13px; color: #b0b0b8; }
  .live-pill { background: #e5253b; color: #fff; font-weight: 800; font-size: 11px; padding: 2px 7px; border-radius: 5px; letter-spacing: 0.5px; }
  .controls { display: flex; align-items: center; gap: 10px; }
  .btn {
    background: #1f1f26; color: #fff; border: 1px solid rgba(255,255,255,0.16);
    padding: 8px 14px; border-radius: 10px; font-size: 13px; font-weight: 600; cursor: pointer;
  }
  .btn:hover, .btn:focus { background: #e5253b; border-color: #e5253b; outline: none; }
</style>
</head>
<body>
<div id="player-wrap">
  <video id="tv-video" autoplay playsinline controls></video>
  <div id="osd">
    <div class="ch-left">
      <img id="ch-logo" src="" alt="TV" style="display:none" />
      <div>
        <div class="ch-title" id="ch-name">Hypnotix Smart TV Receiver</div>
        <div class="ch-sub">
          <span class="live-pill">LIVE CAST</span>
          <span id="ch-cat">Connected to Phone • Auto-Sync Active</span>
        </div>
      </div>
    </div>
    <div class="controls">
      <button class="btn" id="mode-btn" onclick="toggleProxyMode()">Mode: Proxy</button>
      <button class="btn" onclick="reloadStream()">Reload</button>
      <button class="btn" onclick="goFullscreen()">Fullscreen</button>
    </div>
  </div>
</div>
<script>
  const video = document.getElementById('tv-video');
  const nameEl = document.getElementById('ch-name');
  const catEl = document.getElementById('ch-cat');
  const logoEl = document.getElementById('ch-logo');
  const modeBtn = document.getElementById('mode-btn');
  const osd = document.getElementById('osd');

  let hls = null;
  let currentRev = -1;
  let lastCmdSeq = -1;
  let useProxy = true;
  let latestState = null;
  let osdTimer = null;

  function showOsdTemporarily() {
    osd.style.opacity = '1';
    if (osdTimer) clearTimeout(osdTimer);
    osdTimer = setTimeout(() => { osd.style.opacity = '0.18'; }, 6000);
  }
  document.body.addEventListener('mousemove', showOsdTemporarily);
  document.body.addEventListener('keydown', showOsdTemporarily);
  document.body.addEventListener('click', showOsdTemporarily);

  function toggleProxyMode() {
    useProxy = !useProxy;
    modeBtn.textContent = useProxy ? 'Mode: Proxy' : 'Mode: Direct';
    if (latestState) loadChannelStream(latestState, true);
  }

  function goFullscreen() {
    const el = document.documentElement;
    if (el.requestFullscreen) el.requestFullscreen();
  }

  function reloadStream() {
    if (latestState) loadChannelStream(latestState, true);
  }

  function loadChannelStream(state, force) {
    if (!force && state.revision === currentRev) return;
    currentRev = state.revision;
    nameEl.textContent = state.channelName || 'Live Channel';
    catEl.textContent = (state.category || 'Live TV') + (state.language ? ' • ' + state.language : '');
    if (state.logoUrl) {
      logoEl.src = state.logoUrl;
      logoEl.style.display = 'block';
    } else {
      logoEl.style.display = 'none';
    }
    showOsdTemporarily();

    const targetUrl = useProxy ? (state.proxiedStreamUrl || state.directStreamUrl) : state.directStreamUrl;
    if (!targetUrl) return;

    if (hls) {
      hls.destroy();
      hls = null;
    }

    if (window.Hls && Hls.isSupported()) {
      hls = new Hls({ enableWorker: true, lowLatencyMode: true });
      hls.loadSource(targetUrl);
      hls.attachMedia(video);
      hls.on(Hls.Events.MANIFEST_PARSED, function() {
        video.play().catch(() => {});
      });
      hls.on(Hls.Events.ERROR, function(event, data) {
        if (data.fatal && !useProxy && state.proxiedStreamUrl) {
          useProxy = true;
          modeBtn.textContent = 'Mode: Proxy';
          loadChannelStream(state, true);
        }
      });
    } else {
      video.src = targetUrl;
      video.play().catch(() => {});
    }
  }

  const AUTH_TOKEN = '$authToken';

  async function pollState() {
    try {
      const res = await fetch('/api/state?t=' + encodeURIComponent(AUTH_TOKEN) + '&_=' + Date.now(), { cache: 'no-store' });
      if (!res.ok) return;
      const st = await res.json();
      if (latestState === null && typeof st.preferProxy === 'boolean') {
        useProxy = st.preferProxy;
        modeBtn.textContent = useProxy ? 'Mode: Proxy' : 'Mode: Direct';
      }
      latestState = st;
      if (st.revision !== currentRev) {
        loadChannelStream(st, true);
      }
      if (st.commandSeq !== lastCmdSeq) {
        lastCmdSeq = st.commandSeq;
        if (st.command === 'PAUSE') video.pause();
        else if (st.command === 'PLAY') video.play().catch(() => {});
        else if (st.command === 'RELOAD') loadChannelStream(st, true);
        else if (st.command === 'MUTE_TOGGLE') video.muted = !!st.muted;
      }
    } catch (e) {}
  }

  setInterval(pollState, 1200);
  pollState();
  showOsdTemporarily();
</script>
</body>
</html>
        """.trimIndent()
    }

    // =========================================================================
    // System Cast / Wireless Display & External Cast App Bridge
    // =========================================================================

    fun openSystemCastSettings(context: Context) {
        val actions = listOf(
            Settings.ACTION_CAST_SETTINGS,
            "android.settings.WIFI_DISPLAY_SETTINGS",
            "com.samsung.wfd.LAUNCH_WFD_PICKER_DLG",
            "miui.intent.action.MIPLAY",
            Settings.ACTION_DISPLAY_SETTINGS
        )
        for (act in actions) {
            try {
                val intent = Intent(act).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
                statusMessage = "System Cast opened"
                return
            } catch (_: Throwable) {
            }
        }
        Toast.makeText(context, "Could not open System Cast settings on this device", Toast.LENGTH_SHORT).show()
    }

    fun castViaExternalApp(context: Context, specificPackage: String? = null) {
        val snap = currentChannel
        val cleanStreamUrl = stripPipeSuffix(snap.streamUrl)
        if (cleanStreamUrl.isBlank()) {
            Toast.makeText(context, "Select and play a live channel first", Toast.LENGTH_SHORT).show()
            return
        }

        val mime = if (cleanStreamUrl.contains(".m3u8", ignoreCase = true)) {
            "application/x-mpegURL"
        } else {
            "video/*"
        }

        try {
            val viewIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(Uri.parse(cleanStreamUrl), mime)
                putExtra("title", snap.name)
                putExtra(Intent.EXTRA_TITLE, snap.name)
                putExtra("secure_uri", true)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                if (!specificPackage.isNullOrBlank()) {
                    setPackage(specificPackage)
                }
            }

            if (!specificPackage.isNullOrBlank()) {
                context.startActivity(viewIntent)
            } else {
                val chooser = Intent.createChooser(viewIntent, "Cast '${snap.name}' via App").apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(chooser)
            }
        } catch (_: Throwable) {
            // Fallback with generic video/* MIME
            try {
                val fallbackIntent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(Uri.parse(cleanStreamUrl), "video/*")
                    putExtra("title", snap.name)
                    putExtra(Intent.EXTRA_TITLE, snap.name)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                val chooser = Intent.createChooser(fallbackIntent, "Cast '${snap.name}' via App").apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(chooser)
            } catch (_: Throwable) {
                Toast.makeText(context, "No external video/cast player app installed", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun copyToClipboard(context: Context, label: String, value: String, toastText: String) {
        val safeValue = maskXtreamCredentials(value)
        if (safeValue.isBlank()) return
        try {
            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            cm?.setPrimaryClip(ClipData.newPlainText(label, safeValue))
            Toast.makeText(context, toastText, Toast.LENGTH_SHORT).show()
        } catch (_: Throwable) {
        }
    }

    fun shareStreamLink(context: Context) {
        val snap = currentChannel
        val maskedStreamUrl = maskXtreamCredentials(stripPipeSuffix(snap.streamUrl))
        val linkToShare = if (isWebServerRunning && webServerUrl.isNotBlank()) {
            "Watch ${snap.name} on Smart TV Browser: $webServerUrl\nDirect Stream: $maskedStreamUrl"
        } else {
            "${snap.name} Live Stream:\n$maskedStreamUrl"
        }
        try {
            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, "Cast ${snap.name}")
                putExtra(Intent.EXTRA_TEXT, linkToShare)
            }
            val chooser = Intent.createChooser(sendIntent, "Share Live Stream / TV Link").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
        } catch (_: Throwable) {
        }
    }

    internal data class NetworkIpCandidate(
        val interfaceName: String,
        val ipAddress: String
    )

    internal fun isCellularInterfaceName(interfaceName: String): Boolean {
        val lower = interfaceName.lowercase(Locale.US)
        return lower.startsWith("rmnet") ||
            lower.contains("rmnet") ||
            lower.startsWith("ccmni") ||
            lower.startsWith("pdp") ||
            lower.startsWith("wwan")
    }

    internal fun isPrivateIpv4(ip: String): Boolean {
        return ip.startsWith("192.168.") || ip.startsWith("10.") || ip.startsWith("172.")
    }

    internal fun selectPreferredLanIp(candidates: List<NetworkIpCandidate>): String? {
        val valid = candidates.filter { candidate ->
            !isCellularInterfaceName(candidate.interfaceName) && isPrivateIpv4(candidate.ipAddress)
        }
        if (valid.isEmpty()) return null

        fun interfacePriority(iface: String): Int {
            val lower = iface.lowercase(Locale.US)
            return when {
                lower == "wlan0" -> 0
                lower == "ap0" -> 1
                lower.startsWith("wlan") -> 2
                lower.startsWith("ap") || lower.startsWith("softap") || lower.startsWith("swlan") -> 3
                lower.startsWith("eth") -> 4
                else -> 5
            }
        }

        return valid.minByOrNull { interfacePriority(it.interfaceName) }?.ipAddress
    }

    fun detectLocalLanIp(context: Context): String {
        val candidates = mutableListOf<NetworkIpCandidate>()

        try {
            val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
            for (nif in interfaces) {
                if (!nif.isUp || nif.isLoopback) continue
                val ifaceName = nif.name.orEmpty()
                if (isCellularInterfaceName(ifaceName)) continue
                val addrs = Collections.list(nif.inetAddresses)
                for (addr in addrs) {
                    if (addr is Inet4Address && !addr.isLoopbackAddress) {
                        val ip = addr.hostAddress ?: continue
                        if (isPrivateIpv4(ip)) {
                            candidates.add(NetworkIpCandidate(ifaceName, ip))
                        }
                    }
                }
            }
        } catch (_: Throwable) {
        }

        try {
            val cm = context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            val activeNet = cm?.activeNetwork
            val linkProps = if (activeNet != null) cm.getLinkProperties(activeNet) else null
            val ifaceName = linkProps?.interfaceName.orEmpty()
            if (linkProps != null && !isCellularInterfaceName(ifaceName)) {
                for (la in linkProps.linkAddresses) {
                    val addr = la.address
                    if (addr is Inet4Address && !addr.isLoopbackAddress) {
                        val host = addr.hostAddress ?: continue
                        if (isPrivateIpv4(host)) {
                            candidates.add(NetworkIpCandidate(ifaceName, host))
                        }
                    }
                }
            }
        } catch (_: Throwable) {
        }

        return selectPreferredLanIp(candidates) ?: "127.0.0.1"
    }

    private fun extractXmlTag(xml: String, tag: String): String {
        val regex = Regex("<(?:\\w+:)?$tag[^>]*>(.*?)</(?:\\w+:)?$tag>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
        val match = regex.find(xml) ?: return ""
        return match.groupValues[1].trim()
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
    }

    private fun extractServiceControlUrl(xml: String, serviceKeyword: String, baseLocationUrl: String): String? {
        val serviceRegex = Regex("<(?:\\w+:)?service>(.*?)</(?:\\w+:)?service>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
        for (match in serviceRegex.findAll(xml)) {
            val block = match.groupValues[1]
            val serviceType = extractXmlTag(block, "serviceType")
            if (serviceType.contains(serviceKeyword, ignoreCase = true)) {
                val rawControl = extractXmlTag(block, "controlURL")
                if (rawControl.isNotBlank()) {
                    return resolveRelativeUrl(baseLocationUrl, rawControl)
                }
            }
        }
        return null
    }

    private fun resolveRelativeUrl(baseUrl: String, relativeOrAbs: String): String {
        return try {
            URI(baseUrl).resolve(relativeOrAbs.trim()).toString()
        } catch (_: Throwable) {
            relativeOrAbs
        }
    }

    private fun escapeXml(input: String): String {
        val sb = StringBuilder(input.length + 16)
        for (ch in input) {
            when (ch) {
                '&' -> sb.append("&amp;")
                '<' -> sb.append("&lt;")
                '>' -> sb.append("&gt;")
                '"' -> sb.append("&quot;")
                '\'' -> sb.append("&apos;")
                else -> sb.append(ch)
            }
        }
        return sb.toString()
    }

    // =========================================================================
    // Compose UI: Compact Active Cast Remote Bar + Hybrid Cast Center Modal
    // =========================================================================

    @Composable
    fun ActiveCastRemoteBar(
        context: Context,
        isDark: Boolean,
        onOpenCastSheet: () -> Unit
    ) {
        val primaryRed = Color(0xFFE5253B)
        val surfaceBg = if (isDark) Color(0xFF17171C) else Color(0xFFFFF1F2)
        val borderCol = primaryRed.copy(alpha = 0.45f)
        val textPri = if (isDark) Color.White else Color(0xFF0F172A)
        val textSec = if (isDark) Color(0xFFB4B4BE) else Color(0xFF475569)

        val targetTitle = when (activeMode) {
            ActiveCastMode.DLNA_DEVICE -> connectedDevice?.name ?: "Smart TV"
            ActiveCastMode.WEB_TV_SERVER -> if (connectedWebClientCount > 0) {
                "Web TV ($connectedWebClientCount TV Connected)"
            } else {
                "Web TV: $webServerUrl"
            }
            ActiveCastMode.SYSTEM_MIRROR -> connectedDevice?.name ?: "Wireless Display"
            ActiveCastMode.NONE -> if (isWebServerRunning) "Web TV: $webServerUrl" else "Connected"
        }

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable { onOpenCastSheet() }
                .testTag("active_cast_remote_bar"),
            shape = RoundedCornerShape(12.dp),
            color = surfaceBg,
            border = BorderStroke(1.dp, borderCol)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(primaryRed),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.CastConnected,
                        contentDescription = "Active cast",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = targetTitle,
                        color = textPri,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "Auto-syncing: ${currentChannel.name}",
                        color = textSec,
                        fontSize = 10.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Play / Pause Remote Button
                Surface(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .clickable {
                            val cmd = if (isRemotePaused) "PLAY" else "PAUSE"
                            sendRemoteCommand(context, cmd)
                        },
                    shape = CircleShape,
                    color = primaryRed.copy(alpha = 0.16f)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (isRemotePaused) Icons.Rounded.PlayArrow else Icons.Rounded.Pause,
                            contentDescription = if (isRemotePaused) "Resume TV" else "Pause TV",
                            tint = primaryRed,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                // Re-sync / Reload on TV Button
                Surface(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .clickable {
                            sendRemoteCommand(context, "RELOAD")
                            Toast.makeText(context, "Re-syncing stream to TV...", Toast.LENGTH_SHORT).show()
                        },
                    shape = CircleShape,
                    color = primaryRed.copy(alpha = 0.16f)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = "Re-sync channel on TV",
                            tint = primaryRed,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }

                // Stop Cast Button
                Surface(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .clickable {
                            disconnectAll(context)
                        },
                    shape = CircleShape,
                    color = primaryRed
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.Stop,
                            contentDescription = "Stop casting",
                            tint = Color.White,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }
            }
        }
    }

    @Composable
    fun HybridCastDialog(
        context: Context,
        isDark: Boolean,
        onDismiss: () -> Unit
    ) {
        val bgSurface = if (isDark) Color(0xFF101014) else Color(0xFFFFFFFF)
        val cardSurface = if (isDark) Color(0xFF1A1A20) else Color(0xFFF1F5F9)
        val elevatedSurface = if (isDark) Color(0xFF22222A) else Color(0xFFE2E8F0)
        val borderColor = if (isDark) Color(0xFF2C2C35) else Color(0xFFCBD5E1)
        val primaryRed = Color(0xFFE5253B)
        val accentGreen = Color(0xFF10B981)
        val textPri = if (isDark) Color(0xFFFFFFFF) else Color(0xFF0F172A)
        val textSec = if (isDark) Color(0xFFB0B0BC) else Color(0xFF475569)
        val textMuted = if (isDark) Color(0xFF7D7D8A) else Color(0xFF64748B)

        var selectedTab by remember { mutableIntStateOf(0) } // 0: Smart TV / DLNA, 1: Web TV Server, 2: Mirror & Apps
        var manualIpText by remember { mutableStateOf("") }
        var showQrCode by remember { mutableStateOf(false) }

        val lanIp = remember { detectLocalLanIp(context) }

        LaunchedEffect(Unit) {
            startDiscovery(context)
        }

        val spinTransition = rememberInfiniteTransition(label = "cast_scan_spin")
        val spinAngle by spinTransition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "scan_angle"
        )

        Dialog(
            onDismissRequest = onDismiss,
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth(0.95f)
                    .widthIn(max = 520.dp)
                    .heightIn(max = 680.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .testTag("hybrid_cast_dialog"),
                shape = RoundedCornerShape(22.dp),
                color = bgSurface,
                border = BorderStroke(1.dp, borderColor)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp)
                ) {
                    // Top Modal Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(11.dp))
                                .background(
                                    Brush.linearGradient(
                                        colors = listOf(Color(0xFFE5253B), Color(0xFFB91C1C))
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isCastingActive) Icons.Rounded.CastConnected else Icons.Rounded.Cast,
                                contentDescription = "Hybrid Cast",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(11.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "Hybrid TV Cast",
                                    color = textPri,
                                    fontSize = 16.5.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(5.dp))
                                        .background(
                                            if (isCastingActive) accentGreen.copy(alpha = 0.18f)
                                            else primaryRed.copy(alpha = 0.16f)
                                        )
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = if (isCastingActive) "CONNECTED" else "4-IN-1",
                                        color = if (isCastingActive) accentGreen else primaryRed,
                                        fontSize = 9.5.sp,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                }
                            }
                            Text(
                                text = "DLNA / UPnP • Web TV Browser • Miracast • External Cast",
                                color = textSec,
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = "Close Cast dialog",
                                tint = textSec,
                                modifier = Modifier.size(19.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Current Playing Channel Banner + Remote Controls when active
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(13.dp),
                        color = cardSurface,
                        border = BorderStroke(0.9.dp, borderColor)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(5.dp))
                                        .background(primaryRed)
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "LIVE",
                                        color = Color.White,
                                        fontSize = 9.5.sp,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = currentChannel.name.ifBlank { "No Active Channel" },
                                    color = textPri,
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = if (currentChannel.streamUrl.contains(".m3u8", true)) "HLS/M3U8" else "STREAM",
                                    color = primaryRed,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Text(
                                text = statusMessage,
                                color = if (isCastingActive) accentGreen else textSec,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )

                            // Live TV Remote Control Bar when Casting is active
                            AnimatedVisibility(visible = isCastingActive) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 2.dp),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RemoteChipButton(
                                        modifier = Modifier.weight(1f),
                                        icon = if (isRemotePaused) Icons.Rounded.PlayArrow else Icons.Rounded.Pause,
                                        label = if (isRemotePaused) "Play TV" else "Pause TV",
                                        containerColor = elevatedSurface,
                                        contentColor = textPri,
                                        onClick = {
                                            sendRemoteCommand(context, if (isRemotePaused) "PLAY" else "PAUSE")
                                        }
                                    )
                                    RemoteChipButton(
                                        modifier = Modifier.weight(1f),
                                        icon = if (isRemoteMuted) Icons.AutoMirrored.Rounded.VolumeOff else Icons.AutoMirrored.Rounded.VolumeUp,
                                        label = if (isRemoteMuted) "Unmute" else "Mute TV",
                                        containerColor = elevatedSurface,
                                        contentColor = textPri,
                                        onClick = {
                                            sendRemoteCommand(context, "MUTE_TOGGLE")
                                        }
                                    )
                                    RemoteChipButton(
                                        modifier = Modifier.weight(1f),
                                        icon = Icons.Rounded.Refresh,
                                        label = "Re-Sync",
                                        containerColor = elevatedSurface,
                                        contentColor = textPri,
                                        onClick = {
                                            sendRemoteCommand(context, "RELOAD")
                                        }
                                    )
                                    RemoteChipButton(
                                        modifier = Modifier.weight(1f),
                                        icon = Icons.Rounded.Stop,
                                        label = "Stop",
                                        containerColor = primaryRed,
                                        contentColor = Color.White,
                                        onClick = {
                                            disconnectAll(context)
                                        }
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // 3 Mode Selector Tabs
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(cardSurface)
                            .padding(3.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        CastModeTabButton(
                            modifier = Modifier.weight(1f),
                            title = "Smart TV / DLNA",
                            icon = Icons.Rounded.Tv,
                            selected = selectedTab == 0,
                            primaryRed = primaryRed,
                            textPri = textPri,
                            textSec = textSec,
                            onClick = { selectedTab = 0 }
                        )
                        CastModeTabButton(
                            modifier = Modifier.weight(1f),
                            title = "Web TV Link",
                            icon = Icons.Rounded.Public,
                            selected = selectedTab == 1,
                            primaryRed = primaryRed,
                            textPri = textPri,
                            textSec = textSec,
                            onClick = {
                                selectedTab = 1
                                if (!isWebServerRunning) {
                                    ensureWebServerRunning(context)
                                }
                            }
                        )
                        CastModeTabButton(
                            modifier = Modifier.weight(1f),
                            title = "Mirror & Apps",
                            icon = Icons.Rounded.ScreenShare,
                            selected = selectedTab == 2,
                            primaryRed = primaryRed,
                            textPri = textPri,
                            textSec = textSec,
                            onClick = { selectedTab = 2 }
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Scrollable Tab Content
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = false)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        when (selectedTab) {
                            0 -> {
                                // TAB 0: Auto Wi-Fi Smart TV / DLNA / UPnP Discovery + Manual IP Probe
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Wifi,
                                            contentDescription = null,
                                            tint = primaryRed,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Text(
                                            text = "Same Wi-Fi TVs (IP: $lanIp)",
                                            color = textPri,
                                            fontSize = 12.5.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }

                                    Surface(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable { startDiscovery(context) }
                                            .testTag("cast_rescan_button"),
                                        shape = RoundedCornerShape(8.dp),
                                        color = primaryRed.copy(alpha = 0.15f),
                                        border = BorderStroke(0.8.dp, primaryRed.copy(alpha = 0.45f))
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.Refresh,
                                                contentDescription = "Scan TVs",
                                                tint = primaryRed,
                                                modifier = Modifier
                                                    .size(14.dp)
                                                    .rotate(if (isScanning) spinAngle else 0f)
                                            )
                                            Text(
                                                text = if (isScanning) "Scanning..." else "Rescan",
                                                color = primaryRed,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }

                                if (discoveredDevices.isEmpty()) {
                                    Surface(
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(13.dp),
                                        color = cardSurface,
                                        border = BorderStroke(0.8.dp, borderColor)
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(14.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.spacedBy(7.dp)
                                        ) {
                                            if (isScanning) {
                                                CircularProgressIndicator(
                                                    modifier = Modifier.size(26.dp),
                                                    color = primaryRed,
                                                    strokeWidth = 2.5.dp
                                                )
                                                Text(
                                                    text = "Searching for Smart TVs, DLNA Renderers & Cast devices...",
                                                    color = textSec,
                                                    fontSize = 12.sp,
                                                    textAlign = TextAlign.Center
                                                )
                                            } else {
                                                Icon(
                                                    imageVector = Icons.Rounded.Tv,
                                                    contentDescription = null,
                                                    tint = textMuted,
                                                    modifier = Modifier.size(28.dp)
                                                )
                                                Text(
                                                    text = "No DLNA Smart TV detected yet",
                                                    color = textPri,
                                                    fontSize = 13.sp,
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                                Text(
                                                    text = "Make sure your phone & TV are on the same Wi-Fi, or tap 'Web TV Link' tab to play on ANY Smart TV browser immediately!",
                                                    color = textSec,
                                                    fontSize = 11.sp,
                                                    textAlign = TextAlign.Center
                                                )
                                                Row(
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                ) {
                                                    Button(
                                                        onClick = {
                                                            ensureWebServerRunning(context)
                                                            selectedTab = 1
                                                        },
                                                        colors = ButtonDefaults.buttonColors(containerColor = primaryRed),
                                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                                        shape = RoundedCornerShape(9.dp)
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Rounded.Public,
                                                            contentDescription = null,
                                                            modifier = Modifier.size(15.dp)
                                                        )
                                                        Spacer(modifier = Modifier.width(5.dp))
                                                        Text(
                                                            text = "Start Universal Web TV",
                                                            fontSize = 11.5.sp,
                                                            fontWeight = FontWeight.Bold
                                                        )
                                                    }
                                                    Button(
                                                        onClick = { openSystemCastSettings(context) },
                                                        colors = ButtonDefaults.buttonColors(
                                                            containerColor = elevatedSurface,
                                                            contentColor = textPri
                                                        ),
                                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                                        shape = RoundedCornerShape(9.dp)
                                                    ) {
                                                        Text(
                                                            text = "System Cast",
                                                            fontSize = 11.5.sp,
                                                            fontWeight = FontWeight.SemiBold
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                } else {
                                    for (dev in discoveredDevices) {
                                        val isThisConnected = connectedDevice?.id == dev.id &&
                                            activeMode != ActiveCastMode.NONE
                                        Surface(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(12.dp))
                                                .clickable {
                                                    castToDevice(context, dev, showToast = true)
                                                },
                                            shape = RoundedCornerShape(12.dp),
                                            color = if (isThisConnected) primaryRed.copy(alpha = 0.14f) else cardSurface,
                                            border = BorderStroke(
                                                1.dp,
                                                if (isThisConnected) primaryRed else borderColor
                                            )
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(38.dp)
                                                        .clip(RoundedCornerShape(10.dp))
                                                        .background(
                                                            if (isThisConnected) primaryRed else elevatedSurface
                                                        ),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(
                                                        imageVector = if (isThisConnected) Icons.Rounded.CastConnected else Icons.Rounded.Tv,
                                                        contentDescription = dev.name,
                                                        tint = if (isThisConnected) Color.White else textPri,
                                                        modifier = Modifier.size(19.dp)
                                                    )
                                                }

                                                Spacer(modifier = Modifier.width(10.dp))

                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = dev.name,
                                                        color = textPri,
                                                        fontSize = 13.5.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                    Text(
                                                        text = "${dev.modelOrManufacturer} • ${dev.host}",
                                                        color = textSec,
                                                        fontSize = 11.sp,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }

                                                Box(
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(8.dp))
                                                        .background(if (isThisConnected) accentGreen else primaryRed)
                                                        .padding(horizontal = 10.dp, vertical = 5.dp)
                                                ) {
                                                    Text(
                                                        text = if (isThisConnected) "Casting" else "Connect",
                                                        color = Color.White,
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }

                                // DLNA Stream Proxy Toggle + Manual Smart TV IP Input
                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                    color = cardSurface,
                                    border = BorderStroke(0.8.dp, borderColor)
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = "DLNA Stream Proxy Mode",
                                                    color = textPri,
                                                    fontSize = 12.5.sp,
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                                Text(
                                                    text = "Routes IPTV stream via phone so TVs bypass header/CORS blocks",
                                                    color = textSec,
                                                    fontSize = 10.5.sp
                                                )
                                            }
                                            Switch(
                                                checked = useProxyForDlna,
                                                onCheckedChange = { enabled ->
                                                    useProxyForDlna = enabled
                                                    if (enabled) ensureWebServerRunning(context)
                                                    connectedDevice?.let { dev ->
                                                        if (activeMode == ActiveCastMode.DLNA_DEVICE) {
                                                            castToDevice(context, dev, showToast = true)
                                                        }
                                                    }
                                                },
                                                colors = SwitchDefaults.colors(
                                                    checkedThumbColor = Color.White,
                                                    checkedTrackColor = primaryRed
                                                )
                                            )
                                        }

                                        HorizontalDivider(color = borderColor, thickness = 0.7.dp)

                                        Text(
                                            text = "Connect Smart TV by IP Address (if Wi-Fi router blocks auto-scan)",
                                            color = textSec,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Medium
                                        )

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Surface(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .height(38.dp),
                                                shape = RoundedCornerShape(9.dp),
                                                color = elevatedSurface,
                                                border = BorderStroke(0.8.dp, borderColor)
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxSize()
                                                        .padding(horizontal = 10.dp),
                                                    contentAlignment = Alignment.CenterStart
                                                ) {
                                                    if (manualIpText.isEmpty()) {
                                                        Text(
                                                            text = "e.g. 192.168.1.50",
                                                            color = textMuted,
                                                            fontSize = 12.sp
                                                        )
                                                    }
                                                    BasicTextField(
                                                        value = manualIpText,
                                                        onValueChange = { manualIpText = it },
                                                        singleLine = true,
                                                        textStyle = TextStyle(
                                                            color = textPri,
                                                            fontSize = 12.5.sp,
                                                            fontFamily = FontFamily.Monospace
                                                        ),
                                                        cursorBrush = SolidColor(primaryRed),
                                                        keyboardOptions = KeyboardOptions(
                                                            keyboardType = KeyboardType.Uri,
                                                            imeAction = ImeAction.Done
                                                        ),
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .testTag("cast_manual_ip_input")
                                                    )
                                                }
                                            }

                                            Button(
                                                onClick = { probeManualTvIp(context, manualIpText) },
                                                colors = ButtonDefaults.buttonColors(containerColor = primaryRed),
                                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                                                shape = RoundedCornerShape(9.dp),
                                                modifier = Modifier.height(38.dp)
                                            ) {
                                                Text(
                                                    text = "Connect IP",
                                                    fontSize = 11.5.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            1 -> {
                                // TAB 1: Universal Smart TV Browser Cast Server (Works on ALL TVs & Laptops)
                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(14.dp),
                                    color = cardSurface,
                                    border = BorderStroke(
                                        1.dp,
                                        if (isWebServerRunning) accentGreen.copy(alpha = 0.6f) else borderColor
                                    )
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(14.dp),
                                        verticalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(9.dp),
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(36.dp)
                                                        .clip(RoundedCornerShape(10.dp))
                                                        .background(
                                                            if (isWebServerRunning) accentGreen.copy(alpha = 0.2f)
                                                            else primaryRed.copy(alpha = 0.16f)
                                                        ),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Rounded.Router,
                                                        contentDescription = null,
                                                        tint = if (isWebServerRunning) accentGreen else primaryRed,
                                                        modifier = Modifier.size(20.dp)
                                                    )
                                                }
                                                Column {
                                                    Text(
                                                        text = "Wi-Fi Web TV Cast Server",
                                                        color = textPri,
                                                        fontSize = 13.5.sp,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                    Text(
                                                        text = if (isWebServerRunning) {
                                                            "Active • $connectedWebClientCount TV browser(s) connected"
                                                        } else {
                                                            "Works on Samsung, LG, Sony, Fire TV, Mi TV & PC"
                                                        },
                                                        color = if (isWebServerRunning) accentGreen else textSec,
                                                        fontSize = 11.sp
                                                    )
                                                }
                                            }

                                            Switch(
                                                checked = isWebServerRunning,
                                                onCheckedChange = { enabled ->
                                                    if (enabled) {
                                                        ensureWebServerRunning(context)
                                                    } else {
                                                        stopWebCastServer()
                                                    }
                                                },
                                                colors = SwitchDefaults.colors(
                                                    checkedThumbColor = Color.White,
                                                    checkedTrackColor = accentGreen
                                                )
                                            )
                                        }

                                        if (isWebServerRunning) {
                                            Surface(
                                                modifier = Modifier.fillMaxWidth(),
                                                shape = RoundedCornerShape(11.dp),
                                                color = elevatedSurface,
                                                border = BorderStroke(1.dp, primaryRed.copy(alpha = 0.45f))
                                            ) {
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Column(modifier = Modifier.weight(1f)) {
                                                        Text(
                                                            text = "OPEN THIS URL ON YOUR SMART TV BROWSER:",
                                                            color = textSec,
                                                            fontSize = 9.5.sp,
                                                            fontWeight = FontWeight.Bold
                                                        )
                                                        Spacer(modifier = Modifier.height(2.dp))
                                                        Text(
                                                            text = webServerUrl,
                                                            color = primaryRed,
                                                            fontSize = 16.sp,
                                                            fontWeight = FontWeight.ExtraBold,
                                                            fontFamily = FontFamily.Monospace
                                                        )
                                                    }

                                                    IconButton(
                                                        onClick = {
                                                            copyToClipboard(
                                                                context = context,
                                                                label = "Web TV URL",
                                                                value = webServerUrl,
                                                                toastText = "Copied Web TV URL: $webServerUrl"
                                                            )
                                                        }
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Rounded.ContentCopy,
                                                            contentDescription = "Copy Web TV URL",
                                                            tint = textPri,
                                                            modifier = Modifier.size(19.dp)
                                                        )
                                                    }

                                                    IconButton(
                                                        onClick = { showQrCode = !showQrCode }
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Rounded.QrCode2,
                                                            contentDescription = "Show QR Badge",
                                                            tint = primaryRed,
                                                            modifier = Modifier.size(21.dp)
                                                        )
                                                    }
                                                }
                                            }

                                            AnimatedVisibility(visible = showQrCode) {
                                                WebUrlVisualCard(
                                                    url = webServerUrl,
                                                    channelName = currentChannel.name
                                                )
                                            }

                                            Text(
                                                text = "• Type $webServerUrl once in your TV's web browser.\n• Whenever you switch channels in Hypnotix on your phone, the TV automatically switches channels live!",
                                                color = textSec,
                                                fontSize = 11.sp,
                                                lineHeight = 15.sp
                                            )
                                        }
                                    }
                                }
                            }

                            2 -> {
                                // TAB 2: System Wireless Display (Miracast / Google Cast) & External Cast Apps
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(13.dp))
                                        .clickable { openSystemCastSettings(context) },
                                    shape = RoundedCornerShape(13.dp),
                                    color = cardSurface,
                                    border = BorderStroke(0.9.dp, borderColor)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(13.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(40.dp)
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(primaryRed.copy(alpha = 0.16f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.ScreenShare,
                                                contentDescription = "System Wireless Display",
                                                tint = primaryRed,
                                                modifier = Modifier.size(21.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(11.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "System Cast / Screen Mirroring",
                                                color = textPri,
                                                fontSize = 13.5.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = "Chromecast, Miracast, Samsung Smart View, Mi Cast",
                                                color = textSec,
                                                fontSize = 11.sp
                                            )
                                        }
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                                            contentDescription = null,
                                            tint = primaryRed,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }

                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(13.dp))
                                        .clickable { castViaExternalApp(context, null) },
                                    shape = RoundedCornerShape(13.dp),
                                    color = cardSurface,
                                    border = BorderStroke(0.9.dp, borderColor)
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(13.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(40.dp)
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(primaryRed.copy(alpha = 0.16f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.Devices,
                                                contentDescription = "External Cast Player",
                                                tint = primaryRed,
                                                modifier = Modifier.size(21.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(11.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "Beam Stream to External Cast App",
                                                color = textPri,
                                                fontSize = 13.5.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = "Send live M3U8 to Web Video Caster, VLC, BubbleUPnP or LocalCast",
                                                color = textSec,
                                                fontSize = 11.sp
                                            )
                                        }
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                                            contentDescription = null,
                                            tint = primaryRed,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }

                                // Direct Stream URL Copy & Share Row
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Surface(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(11.dp))
                                            .clickable {
                                                copyToClipboard(
                                                    context = context,
                                                    label = "Stream URL",
                                                    value = maskXtreamCredentials(stripPipeSuffix(currentChannel.streamUrl)),
                                                    toastText = "Live stream URL copied!"
                                                )
                                            },
                                        shape = RoundedCornerShape(11.dp),
                                        color = cardSurface,
                                        border = BorderStroke(0.8.dp, borderColor)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.ContentCopy,
                                                contentDescription = null,
                                                tint = textPri,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = "Copy Stream URL",
                                                color = textPri,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                    }

                                    Surface(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(11.dp))
                                            .clickable { shareStreamLink(context) },
                                        shape = RoundedCornerShape(11.dp),
                                        color = cardSurface,
                                        border = BorderStroke(0.8.dp, borderColor)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.Share,
                                                contentDescription = null,
                                                tint = textPri,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = "Share TV Link",
                                                color = textPri,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.SemiBold
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun CastModeTabButton(
        modifier: Modifier,
        title: String,
        icon: ImageVector,
        selected: Boolean,
        primaryRed: Color,
        textPri: Color,
        textSec: Color,
        onClick: () -> Unit
    ) {
        Surface(
            modifier = modifier
                .height(36.dp)
                .clip(RoundedCornerShape(9.dp))
                .clickable(onClick = onClick),
            shape = RoundedCornerShape(9.dp),
            color = if (selected) primaryRed else Color.Transparent
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = if (selected) Color.White else textSec,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = title,
                    color = if (selected) Color.White else textPri,
                    fontSize = 11.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }

    @Composable
    private fun RemoteChipButton(
        modifier: Modifier,
        icon: ImageVector,
        label: String,
        containerColor: Color,
        contentColor: Color,
        onClick: () -> Unit
    ) {
        Surface(
            modifier = modifier
                .height(34.dp)
                .clip(RoundedCornerShape(9.dp))
                .clickable(onClick = onClick),
            shape = RoundedCornerShape(9.dp),
            color = containerColor
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = contentColor,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(3.dp))
                Text(
                    text = label,
                    color = contentColor,
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }

    @Composable
    private fun WebUrlVisualCard(url: String, channelName: String) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            color = Color(0xFF0A0A0E),
            border = BorderStroke(1.dp, Color(0xFF2E2E38))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Visual Finder Matrix Badge
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.White)
                        .padding(6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val cells = 9
                        val cellW = size.width / cells
                        val cellH = size.height / cells
                        val hash = url.hashCode()
                        for (r in 0 until cells) {
                            for (c in 0 until cells) {
                                val isFinder = (r in 0..2 && c in 0..2) ||
                                    (r in 0..2 && c in (cells - 3) until cells) ||
                                    (r in (cells - 3) until cells && c in 0..2)
                                val bit = ((hash ushr ((r * cells + c) % 28)) and 1) == 1
                                if (isFinder || bit) {
                                    drawRoundRect(
                                        color = if (isFinder) Color(0xFFE5253B) else Color(0xFF111827),
                                        topLeft = Offset(c * cellW + 0.5f, r * cellH + 0.5f),
                                        size = Size(cellW - 1f, cellH - 1f),
                                        cornerRadius = CornerRadius(2f, 2f)
                                    )
                                }
                            }
                        }
                    }
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Text(
                        text = "TV Browser Quick Connect",
                        color = Color.White,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = url,
                        color = Color(0xFFE5253B),
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.ExtraBold,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = "Now streaming: ${channelName.ifBlank { "Live TV" }}",
                        color = Color(0xFFA1A1AA),
                        fontSize = 10.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
