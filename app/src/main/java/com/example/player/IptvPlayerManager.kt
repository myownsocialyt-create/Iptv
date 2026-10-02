package com.example.player

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory
import androidx.media3.extractor.ts.TsExtractor
import com.example.data.model.Channel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import java.security.cert.X509Certificate

private const val TAG = "IptvPlayerManager"

@OptIn(UnstableApi::class)
class IptvPlayerManager(private val context: Context) {

    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private var retryJob: Job? = null
    private var bufferingWatchdogJob: Job? = null
    private var currentRetryCount = 0

    private val _isAutoReconnectEnabled = MutableStateFlow(true)
    val isAutoReconnectEnabled: StateFlow<Boolean> = _isAutoReconnectEnabled.asStateFlow()

    fun setAutoReconnectEnabled(enabled: Boolean) {
        _isAutoReconnectEnabled.value = enabled
        if (!enabled) {
            retryJob?.cancel()
            retryJob = null
            bufferingWatchdogJob?.cancel()
            bufferingWatchdogJob = null
        }
    }

    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    init {
        try {
            val trustAllCerts = arrayOf<TrustManager>(
                object : X509TrustManager {
                    override fun checkClientTrusted(chain: Array<X509Certificate>?, authType: String?) {}
                    override fun checkServerTrusted(chain: Array<X509Certificate>?, authType: String?) {}
                    override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
                }
            )
            val sslContext = SSLContext.getInstance("TLS")
            sslContext.init(null, trustAllCerts, java.security.SecureRandom())
            HttpsURLConnection.setDefaultSSLSocketFactory(sslContext.socketFactory)
            HttpsURLConnection.setDefaultHostnameVerifier { _, _ -> true }
        } catch (e: Exception) {
            Log.w(TAG, "Unable to configure lenient HttpsURLConnection SSL", e)
        }

        startProgressTracking()
        try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            val callback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: android.net.Network) {
                    if (_isAutoReconnectEnabled.value && currentChannel != null && _playbackState.value is PlaybackState.Error) {
                        Log.i(TAG, "Network restored: auto-reconnecting stream")
                        scope.launch {
                            delay(1200)
                            if (_isAutoReconnectEnabled.value && currentChannel != null) {
                                retry()
                            }
                        }
                    }
                }
            }
            networkCallback = callback
            cm?.registerDefaultNetworkCallback(callback)
        } catch (e: Exception) {
            Log.w(TAG, "Unable to register network callback for auto-reconnect", e)
        }
    }

    private val _playbackState = MutableStateFlow<PlaybackState>(PlaybackState.Idle)
    val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _currentPosition = MutableStateFlow(0L)
    val currentPosition: StateFlow<Long> = _currentPosition.asStateFlow()

    private val _duration = MutableStateFlow(0L)
    val duration: StateFlow<Long> = _duration.asStateFlow()

    private val _bufferedPosition = MutableStateFlow(0L)
    val bufferedPosition: StateFlow<Long> = _bufferedPosition.asStateFlow()

    private val _isSeekable = MutableStateFlow(false)
    val isSeekable: StateFlow<Boolean> = _isSeekable.asStateFlow()

    private var progressTrackingJob: Job? = null

    private val _availableQualities = MutableStateFlow<List<VideoQualityOption>>(emptyList())
    val availableQualities: StateFlow<List<VideoQualityOption>> = _availableQualities.asStateFlow()

    private val _selectedQualityLabel = MutableStateFlow("Auto")
    val selectedQualityLabel: StateFlow<String> = _selectedQualityLabel.asStateFlow()

    var currentChannel: Channel? = null
        private set

    val exoPlayer: ExoPlayer by lazy {
        val renderersFactory = DefaultRenderersFactory(context.applicationContext)
            .setEnableDecoderFallback(true)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF)
            .setMediaCodecSelector { mimeType, requiresSecureDecoder, requiresTunnelingDecoder ->
                try {
                    val clearDecoders = MediaCodecSelector.DEFAULT.getDecoderInfos(mimeType, false, false)
                    if (clearDecoders.isNotEmpty()) {
                        clearDecoders
                    } else {
                        MediaCodecSelector.DEFAULT.getDecoderInfos(mimeType, requiresSecureDecoder, requiresTunnelingDecoder)
                    }
                } catch (t: Throwable) {
                    Log.w(TAG, "MediaCodec query exception for $mimeType: ${t.message}")
                    emptyList()
                }
            }

        val userAgent = "IPTVSmartersPro/3.1.5 (Linux; Android 12) ExoPlayerLib/2.18.7"
        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent(userAgent)
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(20_000)
            .setReadTimeoutMs(30_000)
            .setKeepPostFor302Redirects(true)
            .setDefaultRequestProperties(
                mapOf(
                    "User-Agent" to userAgent,
                    "Accept" to "*/*",
                    "Connection" to "keep-alive"
                )
            )

        val dataSourceFactory = DefaultDataSource.Factory(context.applicationContext, httpDataSourceFactory)

        val extractorsFactory = DefaultExtractorsFactory()
            .setConstantBitrateSeekingEnabled(true)
            .setTsExtractorFlags(
                DefaultTsPayloadReaderFactory.FLAG_ALLOW_NON_IDR_KEYFRAMES or
                DefaultTsPayloadReaderFactory.FLAG_DETECT_ACCESS_UNITS
            )
            .setTsExtractorTimestampSearchBytes(1500 * TsExtractor.TS_PACKET_SIZE)

        val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory, extractorsFactory)

        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                /* minBufferMs = */ 15_000,
                /* maxBufferMs = */ 30_000,
                /* bufferForPlaybackMs = */ 1_000,
                /* bufferForPlaybackAfterRebufferMs = */ 2_500
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .setBackBuffer(10_000, true)
            .build()

        ExoPlayer.Builder(context.applicationContext, renderersFactory)
            .setMediaSourceFactory(mediaSourceFactory)
            .setLoadControl(loadControl)
            .build()
            .apply {
                addListener(playerListener)
            }
    }

    private val playerListener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            when (playbackState) {
                Player.STATE_IDLE -> {
                    if (_playbackState.value !is PlaybackState.Error) {
                        _playbackState.value = PlaybackState.Idle
                    }
                }
                Player.STATE_BUFFERING -> {
                    _playbackState.value = PlaybackState.Buffering
                    startBufferingWatchdog()
                }
                Player.STATE_READY -> {
                    bufferingWatchdogJob?.cancel()
                    _playbackState.value = PlaybackState.Playing
                    _isPlaying.value = exoPlayer.playWhenReady
                    updateDurationAndPosition()
                }
                Player.STATE_ENDED -> {
                    bufferingWatchdogJob?.cancel()
                    _playbackState.value = PlaybackState.Idle
                    _isPlaying.value = false
                }
            }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _isPlaying.value = isPlaying
            if (isPlaying) {
                _playbackState.value = PlaybackState.Playing
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            Log.e(TAG, "ExoPlayer Error [code=${error.errorCode}]: ${error.message}", error)
            bufferingWatchdogJob?.cancel()
            val userFriendlyMsg = when (error.errorCode) {
                PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED -> "Network connection failed. Check your internet."
                PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT -> "Connection to stream timed out."
                PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS -> "Server returned an error status."
                PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED -> "Unsupported stream format."
                else -> error.localizedMessage ?: "Playback error"
            }
            _playbackState.value = PlaybackState.Error(userFriendlyMsg)

            // Auto recover format for Xtream streams
            val curUrl = currentChannel?.streamUrl ?: ""
            if (curUrl.contains("/live/") && curUrl.endsWith(".ts")) {
                val m3u8Url = curUrl.replace(".ts", ".m3u8")
                Log.i(TAG, "Swapping Xtream stream format from .ts to .m3u8: $m3u8Url")
                currentChannel = currentChannel!!.copy(streamUrl = m3u8Url)
                playChannel(currentChannel!!)
            } else if (curUrl.contains("/live/") && curUrl.endsWith(".m3u8")) {
                val tsUrl = curUrl.replace(".m3u8", ".ts")
                Log.i(TAG, "Swapping Xtream stream format from .m3u8 to .ts: $tsUrl")
                currentChannel = currentChannel!!.copy(streamUrl = tsUrl)
                playChannel(currentChannel!!)
            }
        }

        override fun onTracksChanged(tracks: Tracks) {
            extractQualityOptions(tracks)
        }
    }

    private fun extractQualityOptions(tracks: Tracks) {
        val qualityList = mutableListOf<VideoQualityOption>()
        qualityList.add(VideoQualityOption(0, 0, "Auto"))

        for (group in tracks.groups) {
            if (group.type == C.TRACK_TYPE_VIDEO) {
                for (i in 0 until group.length) {
                    val format = group.getTrackFormat(i)
                    if (format.height > 0) {
                        val label = "${format.height}p"
                        qualityList.add(
                            VideoQualityOption(
                                height = format.height,
                                bitrate = format.bitrate.toLong(),
                                label = label
                            )
                        )
                    }
                }
            }
        }
        _availableQualities.value = qualityList.distinctBy { it.height }.sortedByDescending { it.height }
    }

    private fun startBufferingWatchdog() {
        bufferingWatchdogJob?.cancel()
        bufferingWatchdogJob = scope.launch {
            val curUrl = currentChannel?.streamUrl ?: ""
            val isXtream = curUrl.contains("/live/") || curUrl.contains("/movie/")
            val watchdogDelay = if (isXtream) 18_000L else 14_000L
            delay(watchdogDelay)
            if (_playbackState.value is PlaybackState.Buffering && currentChannel != null) {
                val buffered = exoPlayer.bufferedPosition
                if (buffered > 0 && exoPlayer.playbackState != Player.STATE_IDLE) {
                    Log.i(TAG, "Stream has $buffered ms buffered. Continuing to buffer without interrupting.")
                    return@launch
                }

                Log.w(TAG, "Stream buffered for > ${watchdogDelay}ms without progress. Recovering...")
                if (curUrl.contains("/live/")) {
                    val alternateUrl = when {
                        curUrl.endsWith(".m3u8") -> curUrl.replace(".m3u8", ".ts")
                        curUrl.endsWith(".ts") -> curUrl.replace(".ts", ".m3u8")
                        else -> null
                    }
                    if (alternateUrl != null && alternateUrl != curUrl) {
                        Log.i(TAG, "Watchdog attempting alternate format: $alternateUrl")
                        currentChannel = currentChannel!!.copy(streamUrl = alternateUrl)
                        playChannel(currentChannel!!)
                        return@launch
                    }
                }
                try {
                    exoPlayer.prepare()
                    exoPlayer.play()
                } catch (e: Exception) {
                    Log.w(TAG, "Watchdog re-prepare error", e)
                }
            }
        }
    }

    private fun startProgressTracking() {
        progressTrackingJob?.cancel()
        progressTrackingJob = scope.launch {
            while (true) {
                try {
                    if (exoPlayer.playbackState != Player.STATE_IDLE) {
                        val pos = exoPlayer.currentPosition
                        val dur = exoPlayer.duration
                        val buf = exoPlayer.bufferedPosition
                        _currentPosition.value = if (pos > 0) pos else 0L
                        if (dur > 0 && dur != C.TIME_UNSET) {
                            _duration.value = dur
                        }
                        _bufferedPosition.value = if (buf > 0) buf else 0L
                        val isMovie = currentChannel?.isMovieOrVod == true
                        _isSeekable.value = isMovie || (exoPlayer.isCurrentMediaItemSeekable && !exoPlayer.isCurrentMediaItemLive)
                    }
                } catch (_: Exception) {}
                delay(300)
            }
        }
    }

    private fun updateDurationAndPosition() {
        val dur = exoPlayer.duration
        val pos = exoPlayer.currentPosition
        if (dur > 0 && dur != C.TIME_UNSET) {
            _duration.value = dur
        }
        if (pos > 0) {
            _currentPosition.value = pos
        }
    }

    fun playChannel(channel: Channel) {
        if (!isNetworkAvailable()) {
            _playbackState.value = PlaybackState.Error(
                message = "No internet connection. Please check your network and tap retry."
            )
            return
        }

        if (currentChannel?.streamUrl == channel.streamUrl && exoPlayer.playbackState != Player.STATE_IDLE) {
            if (!exoPlayer.isPlaying) {
                exoPlayer.play()
            }
            return
        }

        currentChannel = channel
        currentRetryCount = 0
        retryJob?.cancel()
        bufferingWatchdogJob?.cancel()
        _playbackState.value = PlaybackState.Connecting

        try {
            val isMovie = channel.isMovieOrVod
            val streamUrl = channel.streamUrl.trim()
            val uri = Uri.parse(streamUrl)
            val builder = MediaItem.Builder()
                .setUri(uri)
                .setMediaId(channel.id)

            val urlLower = streamUrl.lowercase()

            if (!isMovie && (urlLower.contains(".m3u8") || urlLower.contains(".mpd"))) {
                builder.setLiveConfiguration(
                    MediaItem.LiveConfiguration.Builder()
                        .setTargetOffsetMs(4_000L)
                        .setMinPlaybackSpeed(0.97f)
                        .setMaxPlaybackSpeed(1.03f)
                        .build()
                )
            }

            when {
                urlLower.contains(".m3u8") -> builder.setMimeType(MimeTypes.APPLICATION_M3U8)
                urlLower.contains(".mpd") -> builder.setMimeType(MimeTypes.APPLICATION_MPD)
            }

            val mediaItem = builder.build()
            exoPlayer.stop()
            exoPlayer.clearMediaItems()
            exoPlayer.setMediaItem(mediaItem)
            exoPlayer.playWhenReady = true
            exoPlayer.prepare()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to prepare channel: ${channel.name}", e)
            _playbackState.value = PlaybackState.Error("Error preparing stream: ${e.localizedMessage ?: "Unknown"}")
        }
    }

    fun togglePlayPause() {
        if (exoPlayer.isPlaying) {
            exoPlayer.pause()
        } else {
            val isMovie = currentChannel?.isMovieOrVod == true || _isSeekable.value
            if (!isMovie) {
                try {
                    exoPlayer.seekToDefaultPosition()
                } catch (_: Exception) {}
            }
            exoPlayer.play()
        }
    }

    fun seekTo(positionMs: Long) {
        val safePos = positionMs.coerceIn(0L, maxOf(1L, _duration.value))
        _currentPosition.value = safePos
        exoPlayer.seekTo(safePos)
    }

    fun seekForward(offsetMs: Long = 10_000L) {
        val target = (_currentPosition.value + offsetMs).coerceAtMost(_duration.value)
        seekTo(target)
    }

    fun seekBackward(offsetMs: Long = 10_000L) {
        val target = (_currentPosition.value - offsetMs).coerceAtLeast(0L)
        seekTo(target)
    }

    fun retry() {
        currentChannel?.let { playChannel(it) }
    }

    fun selectQuality(option: VideoQualityOption?) {
        if (option == null) {
            _selectedQualityLabel.value = "Auto"
            val parametersBuilder = exoPlayer.trackSelectionParameters.buildUpon()
            parametersBuilder.clearVideoSizeConstraints()
            exoPlayer.trackSelectionParameters = parametersBuilder.build()
            return
        }
        _selectedQualityLabel.value = option.label
        val parametersBuilder = exoPlayer.trackSelectionParameters.buildUpon()
        if (option.height == 0) {
            parametersBuilder.clearVideoSizeConstraints()
        } else {
            parametersBuilder.setMaxVideoSize(Int.MAX_VALUE, option.height)
            parametersBuilder.setMinVideoSize(0, option.height)
        }
        exoPlayer.trackSelectionParameters = parametersBuilder.build()
    }

    private fun isNetworkAvailable(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val activeNetwork = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    fun release() {
        try {
            networkCallback?.let {
                val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                cm?.unregisterNetworkCallback(it)
            }
        } catch (_: Exception) {}
        retryJob?.cancel()
        bufferingWatchdogJob?.cancel()
        progressTrackingJob?.cancel()
        exoPlayer.removeListener(playerListener)
        exoPlayer.release()
    }
}
