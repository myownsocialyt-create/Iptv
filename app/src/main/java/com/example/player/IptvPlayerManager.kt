package com.example.player

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import com.example.data.model.Channel
import okhttp3.OkHttpClient

class IptvPlayerManager(private val context: Context) {
    private var exoPlayer: ExoPlayer? = null
    private val handler = Handler(Looper.getMainLooper())

    private val _playerState = mutableStateOf<PlayerState>(PlayerState.Idle)
    val playerState: State<PlayerState> = _playerState

    private val _isPlaying = mutableStateOf(false)
    val isPlaying: State<Boolean> = _isPlaying

    private val _isMuted = mutableStateOf(false)
    val isMuted: State<Boolean> = _isMuted

    private val _autoReconnectEnabled = mutableStateOf(true)
    val autoReconnectEnabled: State<Boolean> = _autoReconnectEnabled

    private var currentChannel: Channel? = null
    private var retryCount = 0
    private val maxRetries = 4

    private val playerListener = object : Player.Listener {
        override fun onPlaybackStateChanged(state: Int) {
            when (state) {
                Player.STATE_IDLE -> _playerState.value = PlayerState.Idle
                Player.STATE_BUFFERING -> _playerState.value = PlayerState.Buffering
                Player.STATE_READY -> {
                    _playerState.value = PlayerState.Ready
                    retryCount = 0
                }
                Player.STATE_ENDED -> _playerState.value = PlayerState.Ended
            }
        }

        override fun onIsPlayingChanged(playing: Boolean) {
            _isPlaying.value = playing
        }

        override fun onPlayerError(error: PlaybackException) {
            Log.e("IptvPlayerManager", "Player error: ${error.message}", error)
            if (_autoReconnectEnabled.value && retryCount < maxRetries) {
                retryCount++
                _playerState.value = PlayerState.Buffering
                handler.postDelayed({
                    currentChannel?.let { playChannel(it) }
                }, 2000L * retryCount)
            } else {
                _playerState.value = PlayerState.Error(
                    error.localizedMessage ?: "Stream playback failed. Tap to retry."
                )
            }
        }
    }

    fun getPlayer(): ExoPlayer {
        return exoPlayer ?: createPlayer().also { exoPlayer = it }
    }

    private fun createPlayer(): ExoPlayer {
        val player = ExoPlayer.Builder(context)
            .setSeekBackIncrementMs(10000)
            .setSeekForwardIncrementMs(10000)
            .build()
        player.addListener(playerListener)
        player.repeatMode = Player.REPEAT_MODE_OFF
        return player
    }

    fun playChannel(channel: Channel) {
        currentChannel = channel
        val player = getPlayer()
        _playerState.value = PlayerState.Buffering

        try {
            val uri = Uri.parse(channel.streamUrl)
            val mediaItem = MediaItem.Builder()
                .setUri(uri)
                .setLiveConfiguration(
                    MediaItem.LiveConfiguration.Builder()
                        .setMaxPlaybackSpeed(1.02f)
                        .setMinPlaybackSpeed(0.98f)
                        .build()
                )
                .build()

            player.setMediaItem(mediaItem)
            player.prepare()
            player.playWhenReady = true
        } catch (e: Exception) {
            _playerState.value = PlayerState.Error("Unable to open stream: ${e.message}")
        }
    }

    fun retry() {
        retryCount = 0
        currentChannel?.let { playChannel(it) }
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
    }

    fun stop() {
        exoPlayer?.stop()
        _playerState.value = PlayerState.Idle
        _isPlaying.value = false
        currentChannel = null
    }

    fun release() {
        handler.removeCallbacksAndMessages(null)
        exoPlayer?.removeListener(playerListener)
        exoPlayer?.release()
        exoPlayer = null
        currentChannel = null
    }
}
