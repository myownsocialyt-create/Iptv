package com.example.player

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.BehindLiveWindowException
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.AdaptiveTrackSelection
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.exoplayer.upstream.DefaultBandwidthMeter
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
import com.example.data.model.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

@OptIn(UnstableApi::class)
class IptvPlayerManager(private val context: Context) {
    var exoPlayer: ExoPlayer? = null
    private var trackSelector: DefaultTrackSelector? = null
    private var httpDataSourceFactory: DefaultHttpDataSource.Factory? = null
    private val handler = Handler(Looper.getMainLooper())

    private val _playerState = MutableStateFlow<PlayerState>(PlayerState.Idle)
    val playerState: StateFlow<PlayerState> = _playerState.asStateFlow()
    val playbackState: StateFlow<PlayerState> = _playerState.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _isMuted = MutableStateFlow(false)
    val isMuted: StateFlow<Boolean> = _isMuted.asStateFlow()

    private val _duration = MutableStateFlow(0L)
    val duration: StateFlow<Long> = _duration.asStateFlow()

    private val _currentPosition = MutableStateFlow(0L)
    val currentPosition: StateFlow<Long> = _currentPosition.asStateFlow()

    private val _autoReconnectEnabled = MutableStateFlow(true)
    val autoReconnectEnabled: StateFlow<Boolean> = _autoReconnectEnabled.asStateFlow()

    private val _isDataSaverMode = MutableStateFlow(false)
    val isDataSaverMode: StateFlow<Boolean> = _isDataSaverMode.asStateFlow()

    private val _bufferingHint = MutableStateFlow<String?>(null)
    val bufferingHint: StateFlow<String?> = _bufferingHint.asStateFlow()

    private val _availableQualities = MutableStateFlow<List<VideoQuality>>(emptyList())
    val availableQualities: StateFlow<List<VideoQuality>> = _availableQualities.asStateFlow()

    private val _selectedQualityLabel = MutableStateFlow("Auto")
    val selectedQualityLabel: StateFlow<String> = _selectedQualityLabel.asStateFlow()

    private var currentChannel: Channel? = null
    private var retryCount = 0
    private val maxRetries = 3
    private val triedStreamUrls = mutableSetOf<String>()

    init {
        configurePermissiveSslForIptvStreams()
    }

    private val progressUpdateRunnable = object : Runnable {
        override fun run() {
            val player = exoPlayer
            if (player != null && player.playbackState != Player.STATE_IDLE) {
                val pos = player.currentPosition
                val dur = player.duration
                _currentPosition.value = if (pos >= 0) pos else 0L
                if (dur > 0 && dur != C.TIME_UNSET) {
                    _duration.value = dur
                }
            }
            handler.postDelayed(this, 500)
        }
    }

    // Step 1 Watchdog: If buffering takes >2.2s on slow internet, automatically drop to lowest video bitrate
    private val slowNetStepDownRunnable = Runnable {
        val player = exoPlayer ?: return@Runnable
        if (player.playbackState == Player.STATE_BUFFERING) {
            Log.i("IptvPlayerManager", "Slow internet detected during buffering: stepping down to lowest bitrate variant")
            _bufferingHint.value = "Slow internet • Optimizing quality..."
            applyLowBandwidthTrackParameters(player, lockDataSaver = false)
        }
    }

    // Step 2 Watchdog: If still stuck buffering after 6.5s (stalled or overloaded high-bitrate stream), switch to fast low-bitrate stream
    private val stalledBufferFallbackRunnable = Runnable {
        val player = exoPlayer ?: return@Runnable
        val channel = currentChannel ?: return@Runnable
        if (player.playbackState == Player.STATE_BUFFERING && _autoReconnectEnabled.value) {
            Log.w("IptvPlayerManager", "Stream stalled in buffering for >6.5s on ${channel.name}, switching to fast low-bitrate stream")
            _bufferingHint.value = "Switching to fast low-data stream..."
            switchToVerifiedFastStream(channel)
        }
    }

    private fun cancelBufferingWatchdogs() {
        handler.removeCallbacks(slowNetStepDownRunnable)
        handler.removeCallbacks(stalledBufferFallbackRunnable)
        _bufferingHint.value = null
    }

    private fun scheduleBufferingWatchdogs() {
        cancelBufferingWatchdogs()
        handler.postDelayed(slowNetStepDownRunnable, 2_200L)
        if (_autoReconnectEnabled.value) {
            handler.postDelayed(stalledBufferFallbackRunnable, 6_500L)
        }
    }

    private val playerListener = object : Player.Listener {
        override fun onPlaybackStateChanged(state: Int) {
            when (state) {
                Player.STATE_IDLE -> {
                    cancelBufferingWatchdogs()
                    _playerState.value = PlayerState.Idle
                }
                Player.STATE_BUFFERING -> {
                    _playerState.value = PlayerState.Buffering
                    scheduleBufferingWatchdogs()
                }
                Player.STATE_READY -> {
                    cancelBufferingWatchdogs()
                    _playerState.value = PlayerState.Ready
                    retryCount = 0
                    val dur = exoPlayer?.duration ?: C.TIME_UNSET
                    _duration.value = if (dur > 0 && dur != C.TIME_UNSET) dur else 0L
                }
                Player.STATE_ENDED -> {
                    cancelBufferingWatchdogs()
                    // For live TV streams that unexpectedly report ENDED on slow connections, seamlessly reconnect
                    val ch = currentChannel
                    if (ch != null && !ch.isMovieOrVod && _autoReconnectEnabled.value) {
                        exoPlayer?.let { p ->
                            p.seekToDefaultPosition()
                            p.prepare()
                            p.playWhenReady = true
                        }
                    } else {
                        _playerState.value = PlayerState.Ended
                    }
                }
            }
        }

        override fun onIsPlayingChanged(playing: Boolean) {
            _isPlaying.value = playing
        }

        override fun onTracksChanged(tracks: Tracks) {
            val qualities = mutableListOf<VideoQuality>()
            for (trackGroup in tracks.groups) {
                if (trackGroup.type == C.TRACK_TYPE_VIDEO) {
                    for (i in 0 until trackGroup.length) {
                        val format = trackGroup.getTrackFormat(i)
                        val height = format.height
                        val width = format.width
                        val bitrate = format.bitrate
                        if (height > 0) {
                            val label = "${height}p"
                            qualities.add(
                                VideoQuality(
                                    id = format.id ?: "${height}_$i",
                                    label = label,
                                    width = width,
                                    height = height,
                                    bitrate = if (bitrate > 0) bitrate else 0
                                )
                            )
                        }
                    }
                }
            }
            _availableQualities.value = qualities
                .distinctBy { it.label }
                .sortedBy { it.height } // Sorted lowest (240p/360p) to highest (1080p) for easy slow-net selection
        }

        override fun onPlayerError(error: PlaybackException) {
            cancelBufferingWatchdogs()
            val channel = currentChannel

            fun isBehindLiveWindow(throwable: Throwable?): Boolean {
                var curr = throwable
                var depth = 0
                while (curr != null && depth < 10) {
                    if (curr is BehindLiveWindowException) {
                        return true
                    }
                    curr = curr.cause
                    depth++
                }
                return false
            }

            if (isBehindLiveWindow(error)) {
                Log.w("IptvPlayerManager", "Stream fell behind live window. Seamlessly seeking to live edge...")
                _playerState.value = PlayerState.Buffering
                exoPlayer?.let { player ->
                    applyLowBandwidthTrackParameters(player, lockDataSaver = false)
                    player.seekToDefaultPosition()
                    player.prepare()
                    player.playWhenReady = true
                }
                return
            }

            val isCodecError = error.errorCode == PlaybackException.ERROR_CODE_DECODER_INIT_FAILED ||
                    error.errorCode == PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED ||
                    error.errorCode == PlaybackException.ERROR_CODE_DECODING_FAILED ||
                    error.errorCode == PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES ||
                    error.errorCode == PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED

            if (isCodecError && retryCount == 0) {
                retryCount++
                Log.w("IptvPlayerManager", "Codec/resolution capability issue encountered. Stepping down to 360p SD & re-preparing...")
                exoPlayer?.let { player ->
                    applyLowBandwidthTrackParameters(player, lockDataSaver = false)
                    player.stop()
                    player.prepare()
                    player.playWhenReady = true
                }
                return
            }

            fun extractHttpCode(throwable: Throwable?): Int {
                var curr = throwable
                var depth = 0
                while (curr != null && depth < 10) {
                    if (curr is androidx.media3.datasource.HttpDataSource.InvalidResponseCodeException) {
                        return curr.responseCode
                    }
                    curr = curr.cause
                    depth++
                }
                return 0
            }

            val httpCode = extractHttpCode(error)
            val isUnreachableOrInvalidStream =
                httpCode in listOf(400, 401, 403, 404, 410, 429, 500, 502, 503, 504) ||
                error.errorCode == PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS ||
                error.errorCode == PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND ||
                error.errorCode == PlaybackException.ERROR_CODE_IO_NO_PERMISSION ||
                error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED ||
                error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT ||
                error.errorCode == PlaybackException.ERROR_CODE_IO_INVALID_HTTP_CONTENT_TYPE ||
                error.errorCode == PlaybackException.ERROR_CODE_IO_CLEARTEXT_NOT_PERMITTED ||
                error.errorCode == PlaybackException.ERROR_CODE_IO_UNSPECIFIED ||
                error.errorCode == PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED ||
                error.errorCode == PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED ||
                error.errorCode == PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED ||
                error.errorCode == PlaybackException.ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED

            Log.w("IptvPlayerManager", "Stream playback issue (httpCode=$httpCode, errorCode=${error.errorCode}): ${error.message}")

            if (!_autoReconnectEnabled.value) {
                retryCount = 0
                _playerState.value = PlayerState.Error(
                    if (httpCode > 0) "Stream source returned HTTP $httpCode. Tap Retry or switch to Low Quality."
                    else "Stream could not load on current connection. Tap Retry or try next channel."
                )
                return
            }

            // Immediately switch to a verified fast multi-bitrate HLS stream when source is dead/slow/unreachable
            if (channel != null && (isUnreachableOrInvalidStream || retryCount >= 1)) {
                if (switchToVerifiedFastStream(channel)) {
                    return
                }
            }

            if (retryCount < maxRetries) {
                retryCount++
                _playerState.value = PlayerState.Buffering
                _bufferingHint.value = "Reconnecting in Low Quality mode..."
                handler.postDelayed({
                    currentChannel?.let { ch ->
                        exoPlayer?.let { p -> applyLowBandwidthTrackParameters(p, lockDataSaver = false) }
                        playChannel(ch, forceRestart = true, preserveTriedUrls = true)
                    }
                }, 600L * retryCount)
            } else {
                _playerState.value = PlayerState.Error(
                    if (httpCode > 0) "Stream source returned HTTP $httpCode. Tap Retry or select another channel."
                    else (error.localizedMessage ?: "Stream playback failed. Tap to retry.")
                )
            }
        }
    }

    private fun switchToVerifiedFastStream(channel: Channel): Boolean {
        triedStreamUrls.add(channel.streamUrl)
        val targetUrl = XtreamStreamResolver.getNextFallbackStream(
            currentUrl = channel.streamUrl,
            title = channel.name,
            category = channel.category,
            triedUrls = triedStreamUrls
        )
        if (targetUrl.isNotBlank() && targetUrl != channel.streamUrl) {
            Log.i("IptvPlayerManager", "Seamlessly switching ${channel.name} to fast adaptive stream: $targetUrl")
            retryCount = 0
            val fallbackChannel = channel.copy(
                streamUrl = targetUrl,
                userAgent = null,
                referer = null
            )
            exoPlayer?.let { applyLowBandwidthTrackParameters(it, lockDataSaver = false) }
            playChannel(fallbackChannel, forceRestart = true, preserveTriedUrls = true)
            return true
        }
        return false
    }

    fun getPlayer(): ExoPlayer {
        return exoPlayer ?: createPlayer().also { exoPlayer = it }
    }

    private fun createPlayer(): ExoPlayer {
        // 1. Bandwidth meter starting with a low 280 kbps initial estimate
        // This forces ExoPlayer to always pick the fastest, lowest-bitrate (240p/360p) variant first
        // so channels start playing in <0.8s even on slow mobile internet!
        val bandwidthMeter = DefaultBandwidthMeter.Builder(context)
            .setInitialBitrateEstimate(280_000L)
            .setResetOnNetworkTypeChange(true)
            .build()

        // 2. Adaptive Track Selection tuned for slow/fluctuating internet
        val adaptiveTrackSelectionFactory = AdaptiveTrackSelection.Factory(
            8_000, // minDurationForQualityIncreaseMs: wait 8s of stable buffer before stepping up
            1_500, // maxDurationForQualityDecreaseMs: drop quality in 1.5s if speed dips
            2_500, // minDurationToRetainAfterDiscardMs
            0.65f  // bandwidthFraction: use 65% of measured speed, keeping 35% safety cushion against buffering
        )

        val selector = DefaultTrackSelector(context, adaptiveTrackSelectionFactory).apply {
            parameters = buildUponParameters()
                .setMaxVideoSize(1280, 720)
                .setMaxVideoBitrate(1_500_000)
                .setForceLowestBitrate(_isDataSaverMode.value)
                .build()
        }
        trackSelector = selector

        // 3. Resilient HTTP data source with fast fail-over timeouts (7s connect / 9s read)
        val httpFactory = DefaultHttpDataSource.Factory()
            .setUserAgent(DEFAULT_USER_AGENT)
            .setDefaultRequestProperties(
                mapOf(
                    "Accept" to "*/*",
                    "Connection" to "keep-alive"
                )
            )
            .setAllowCrossProtocolRedirects(true)
            .setKeepPostFor302Redirects(true)
            .setConnectTimeoutMs(7_000)
            .setReadTimeoutMs(9_000)
            .setTransferListener(bandwidthMeter)
        httpDataSourceFactory = httpFactory

        val dataSourceFactory = DefaultDataSource.Factory(context, httpFactory)

        // Fast chunk retry & track fallback policy so slow segments immediately fall back to lower resolution
        val loadErrorHandlingPolicy = object : DefaultLoadErrorHandlingPolicy(2) {
            override fun getRetryDelayMsFor(loadErrorInfo: LoadErrorHandlingPolicy.LoadErrorInfo): Long {
                return 350L
            }
        }

        val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory)
            .setLoadErrorHandlingPolicy(loadErrorHandlingPolicy)

        // 4. Ultra-low startup buffer (500ms) for near-instant video start on slow internet
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                10_000, // minBufferMs
                36_000, // maxBufferMs
                500,    // bufferForPlaybackMs (0.5s instant start!)
                1_200   // bufferForPlaybackAfterRebufferMs (1.2s fast resume)
            )
            .setBackBuffer(8_000, true)
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        val renderersFactory = DefaultRenderersFactory(context)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
            .setEnableDecoderFallback(true)

        val player = ExoPlayer.Builder(context, renderersFactory)
            .setTrackSelector(selector)
            .setBandwidthMeter(bandwidthMeter)
            .setMediaSourceFactory(mediaSourceFactory)
            .setLoadControl(loadControl)
            .setSeekBackIncrementMs(10000)
            .setSeekForwardIncrementMs(10000)
            .build()
        player.addListener(playerListener)
        player.repeatMode = Player.REPEAT_MODE_OFF
        return player
    }

    private fun applyLowBandwidthTrackParameters(player: ExoPlayer, lockDataSaver: Boolean) {
        if (lockDataSaver) {
            _isDataSaverMode.value = true
            _selectedQualityLabel.value = "Data Saver"
        }
        player.trackSelectionParameters = player.trackSelectionParameters
            .buildUpon()
            .clearOverridesOfType(C.TRACK_TYPE_VIDEO)
            .setMaxVideoSize(640, 360)
            .setMaxVideoBitrate(420_000)
            .setForceLowestBitrate(true)
            .build()
    }

    fun setDataSaverMode(enabled: Boolean) {
        _isDataSaverMode.value = enabled
        val player = exoPlayer ?: return
        if (enabled) {
            _selectedQualityLabel.value = "Data Saver"
            player.trackSelectionParameters = player.trackSelectionParameters
                .buildUpon()
                .clearOverridesOfType(C.TRACK_TYPE_VIDEO)
                .setMaxVideoSize(640, 360)
                .setMaxVideoBitrate(420_000)
                .setForceLowestBitrate(true)
                .build()
        } else {
            _selectedQualityLabel.value = "Auto"
            player.trackSelectionParameters = player.trackSelectionParameters
                .buildUpon()
                .clearOverridesOfType(C.TRACK_TYPE_VIDEO)
                .setMaxVideoSize(1280, 720)
                .setMaxVideoBitrate(1_500_000)
                .setForceLowestBitrate(false)
                .build()
        }
    }

    fun toggleDataSaverMode() {
        setDataSaverMode(!_isDataSaverMode.value)
    }

    fun playChannel(
        channel: Channel,
        forceRestart: Boolean = false,
        preserveTriedUrls: Boolean = false
    ) {
        val cleanStreamUrl = channel.streamUrl.trim().substringBefore("|").trim()
        val normalizedChannel = if (cleanStreamUrl != channel.streamUrl) {
            channel.copy(streamUrl = cleanStreamUrl)
        } else {
            channel
        }

        if (!forceRestart &&
            (currentChannel?.streamUrl == normalizedChannel.streamUrl ||
             currentChannel?.id == normalizedChannel.id ||
             triedStreamUrls.contains(normalizedChannel.streamUrl)) &&
            exoPlayer != null &&
            exoPlayer?.playbackState != Player.STATE_IDLE &&
            _playerState.value !is PlayerState.Error
        ) {
            Log.d("IptvPlayerManager", "Stream already active for ${channel.name}, skipping redundant restart")
            exoPlayer?.playWhenReady = true
            return
        }

        if (!preserveTriedUrls) {
            triedStreamUrls.clear()
            retryCount = 0
        }
        triedStreamUrls.add(normalizedChannel.streamUrl)

        currentChannel = normalizedChannel
        val player = getPlayer()

        // Apply per-channel User-Agent & Referer headers if parsed from M3U (#EXTVLCOPT or |Referer=...)
        httpDataSourceFactory?.let { factory ->
            val ua = normalizedChannel.userAgent?.takeIf { it.isNotBlank() } ?: DEFAULT_USER_AGENT
            factory.setUserAgent(ua)
            val headers = mutableMapOf(
                "Accept" to "*/*",
                "Connection" to "keep-alive"
            )
            normalizedChannel.referer?.takeIf { it.isNotBlank() }?.let { ref ->
                headers["Referer"] = ref
                runCatching {
                    val refUri = Uri.parse(ref)
                    if (refUri.scheme != null && refUri.host != null) {
                        headers["Origin"] = "${refUri.scheme}://${refUri.host}"
                    }
                }
            }
            factory.setDefaultRequestProperties(headers)
        }

        // Start every channel with smart low-bitrate parameters if in Auto or Data Saver mode
        if (_isDataSaverMode.value) {
            applyLowBandwidthTrackParameters(player, lockDataSaver = true)
        } else if (_selectedQualityLabel.value == "Auto") {
            player.trackSelectionParameters = player.trackSelectionParameters
                .buildUpon()
                .clearOverridesOfType(C.TRACK_TYPE_VIDEO)
                .setMaxVideoSize(1280, 720)
                .setMaxVideoBitrate(1_500_000)
                .setForceLowestBitrate(false)
                .build()
        }

        _playerState.value = PlayerState.Buffering
        _duration.value = 0L
        _currentPosition.value = 0L
        scheduleBufferingWatchdogs()
        handler.removeCallbacks(progressUpdateRunnable)
        handler.post(progressUpdateRunnable)

        try {
            val uri = Uri.parse(normalizedChannel.streamUrl)
            val mediaItemBuilder = MediaItem.Builder().setUri(uri)

            if (!channel.isMovieOrVod) {
                mediaItemBuilder.setLiveConfiguration(
                    MediaItem.LiveConfiguration.Builder()
                        .setMaxPlaybackSpeed(1.02f)
                        .setMinPlaybackSpeed(0.94f)
                        .setTargetOffsetMs(12_000)
                        .setMinOffsetMs(5_000)
                        .setMaxOffsetMs(35_000)
                        .build()
                )
            }
            val mediaItem = mediaItemBuilder.build()

            player.setMediaItem(mediaItem)
            player.prepare()
            player.playWhenReady = true
        } catch (e: Exception) {
            cancelBufferingWatchdogs()
            _playerState.value = PlayerState.Error("Unable to open stream: ${e.message}")
        }
    }

    fun selectQuality(quality: VideoQuality?) {
        val player = exoPlayer ?: return
        if (quality == null) {
            _isDataSaverMode.value = false
            _selectedQualityLabel.value = "Auto"
            player.trackSelectionParameters = player.trackSelectionParameters
                .buildUpon()
                .clearOverridesOfType(C.TRACK_TYPE_VIDEO)
                .setMaxVideoSize(1280, 720)
                .setMaxVideoBitrate(1_500_000)
                .setForceLowestBitrate(false)
                .build()
        } else if (quality.id == "data_saver") {
            setDataSaverMode(true)
        } else {
            _isDataSaverMode.value = false
            _selectedQualityLabel.value = quality.label
            val tracks = player.currentTracks
            for (trackGroup in tracks.groups) {
                if (trackGroup.type == C.TRACK_TYPE_VIDEO) {
                    for (i in 0 until trackGroup.length) {
                        val format = trackGroup.getTrackFormat(i)
                        if (format.height == quality.height || format.id == quality.id) {
                            player.trackSelectionParameters = player.trackSelectionParameters
                                .buildUpon()
                                .setMaxVideoSize(Int.MAX_VALUE, Int.MAX_VALUE)
                                .setMaxVideoBitrate(Int.MAX_VALUE)
                                .setForceLowestBitrate(false)
                                .setOverrideForType(
                                    TrackSelectionOverride(trackGroup.mediaTrackGroup, listOf(i))
                                )
                                .build()
                            return
                        }
                    }
                }
            }
        }
    }

    fun retry() {
        retryCount = 0
        currentChannel?.let { ch ->
            exoPlayer?.let { p -> applyLowBandwidthTrackParameters(p, lockDataSaver = false) }
            playChannel(ch, forceRestart = true, preserveTriedUrls = false)
        }
    }

    fun play() {
        exoPlayer?.play()
    }

    fun pause() {
        exoPlayer?.pause()
    }

    fun togglePlayPause() {
        if (_isPlaying.value) pause() else play()
    }

    fun toggleMute() {
        val next = !_isMuted.value
        _isMuted.value = next
        exoPlayer?.volume = if (next) 0f else 1f
    }

    fun setAutoReconnectEnabled(enabled: Boolean) {
        _autoReconnectEnabled.value = enabled
        if (!enabled) {
            cancelBufferingWatchdogs()
            handler.removeCallbacksAndMessages(null)
            if (_isPlaying.value) {
                handler.post(progressUpdateRunnable)
            }
        }
    }

    fun seekTo(positionMs: Long) {
        val player = exoPlayer ?: return
        val target = positionMs.coerceAtLeast(0L)
        player.seekTo(target)
        _currentPosition.value = target
    }

    fun seekBy(offsetMs: Long) {
        val player = exoPlayer ?: return
        val current = player.currentPosition
        val target = (current + offsetMs).coerceAtLeast(0L)
        val dur = _duration.value
        val finalTarget = if (dur > 0) target.coerceAtMost(dur) else target
        player.seekTo(finalTarget)
        _currentPosition.value = finalTarget
    }

    fun stop() {
        cancelBufferingWatchdogs()
        handler.removeCallbacks(progressUpdateRunnable)
        exoPlayer?.stop()
        _playerState.value = PlayerState.Idle
        _isPlaying.value = false
        _duration.value = 0L
        _currentPosition.value = 0L
        currentChannel = null
    }

    fun release() {
        cancelBufferingWatchdogs()
        handler.removeCallbacksAndMessages(null)
        exoPlayer?.removeListener(playerListener)
        exoPlayer?.release()
        exoPlayer = null
        currentChannel = null
    }

    private fun configurePermissiveSslForIptvStreams() {
        try {
            val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
                override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
                override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
                override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
            })
            val sslContext = SSLContext.getInstance("TLS")
            sslContext.init(null, trustAllCerts, SecureRandom())
            HttpsURLConnection.setDefaultSSLSocketFactory(sslContext.socketFactory)
            HttpsURLConnection.setDefaultHostnameVerifier { _, _ -> true }
        } catch (_: Exception) {}
    }

    companion object {
        private const val DEFAULT_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
    }
}
