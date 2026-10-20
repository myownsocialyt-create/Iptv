package com.example.player

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.data.model.Channel
import com.example.ui.theme.OttGold
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class PlayerState {
    IDLE,
    BUFFERING,
    READY,
    ERROR
}

class IptvPlayerManager(private val appContext: Context) {

    private var mediaPlayer: MediaPlayer? = null
    private var currentSurfaceHolder: SurfaceHolder? = null
    private var currentUrl: String? = null

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _playbackState = MutableStateFlow(PlayerState.IDLE)
    val playbackState: StateFlow<PlayerState> = _playbackState.asStateFlow()

    private val _currentPosition = MutableStateFlow(0L)
    val currentPosition: StateFlow<Long> = _currentPosition.asStateFlow()

    private val _duration = MutableStateFlow(0L)
    val duration: StateFlow<Long> = _duration.asStateFlow()

    fun playStream(url: String) {
        if (currentUrl == url && mediaPlayer != null && _playbackState.value != PlayerState.ERROR) {
            if (!_isPlaying.value) {
                runCatching {
                    mediaPlayer?.start()
                    _isPlaying.value = true
                }
            }
            return
        }

        currentUrl = url
        _playbackState.value = PlayerState.BUFFERING
        _isPlaying.value = true

        releaseInternal()

        runCatching {
            val mp = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .build()
                )
                currentSurfaceHolder?.let { setDisplay(it) }
                setDataSource(appContext, Uri.parse(url))
                setOnPreparedListener { player ->
                    _duration.value = player.duration.toLong().coerceAtLeast(0L)
                    _playbackState.value = PlayerState.READY
                    player.start()
                    _isPlaying.value = true
                }
                setOnInfoListener { _, what, _ ->
                    when (what) {
                        MediaPlayer.MEDIA_INFO_BUFFERING_START -> _playbackState.value = PlayerState.BUFFERING
                        MediaPlayer.MEDIA_INFO_BUFFERING_END,
                        MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START -> _playbackState.value = PlayerState.READY
                    }
                    false
                }
                setOnErrorListener { _, _, _ ->
                    _playbackState.value = PlayerState.ERROR
                    _isPlaying.value = false
                    true
                }
                prepareAsync()
            }
            mediaPlayer = mp
        }.onFailure {
            _playbackState.value = PlayerState.ERROR
            _isPlaying.value = false
        }
    }

    fun attachSurfaceView(surfaceView: SurfaceView) {
        val holder = surfaceView.holder
        holder.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceCreated(h: SurfaceHolder) {
                currentSurfaceHolder = h
                runCatching { mediaPlayer?.setDisplay(h) }
            }

            override fun surfaceChanged(h: SurfaceHolder, format: Int, width: Int, height: Int) {
                currentSurfaceHolder = h
                runCatching { mediaPlayer?.setDisplay(h) }
            }

            override fun surfaceDestroyed(h: SurfaceHolder) {
                if (currentSurfaceHolder === h) {
                    currentSurfaceHolder = null
                    runCatching { mediaPlayer?.setDisplay(null) }
                }
            }
        })
        if (holder.surface != null && holder.surface.isValid) {
            currentSurfaceHolder = holder
            runCatching { mediaPlayer?.setDisplay(holder) }
        }
    }

    fun togglePlayPause() {
        val mp = mediaPlayer
        if (mp == null) {
            currentUrl?.let { playStream(it) }
            return
        }
        runCatching {
            if (mp.isPlaying) {
                mp.pause()
                _isPlaying.value = false
            } else {
                mp.start()
                _isPlaying.value = true
            }
        }.onFailure {
            _isPlaying.value = !_isPlaying.value
        }
    }

    fun retry() {
        currentUrl?.let { url ->
            currentUrl = null
            playStream(url)
        }
    }

    fun stop() {
        currentUrl = null
        releaseInternal()
        _isPlaying.value = false
        _playbackState.value = PlayerState.IDLE
        _currentPosition.value = 0L
        _duration.value = 0L
    }

    fun release() {
        stop()
    }

    private fun releaseInternal() {
        runCatching {
            mediaPlayer?.setDisplay(null)
            mediaPlayer?.reset()
            mediaPlayer?.release()
        }
        mediaPlayer = null
    }
}

@Composable
fun IptvVideoPlayer(
    channel: Channel,
    playerManager: IptvPlayerManager,
    isFullscreen: Boolean,
    isPipMode: Boolean,
    onToggleFullscreen: () -> Unit,
    onEnterPip: () -> Unit,
    onMinimizeToMiniPlayer: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isPlaying by playerManager.isPlaying.collectAsState()
    val playbackState by playerManager.playbackState.collectAsState()
    var controlsVisible by remember { mutableStateOf(true) }

    Box(
        modifier = modifier
            .background(Color.Black)
            .clickable {
                if (!isPipMode) {
                    controlsVisible = !controlsVisible
                }
            }
            .testTag("iptv_video_player")
    ) {
        AndroidView(
            factory = { ctx ->
                SurfaceView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    playerManager.attachSurfaceView(this)
                }
            },
            update = { surfaceView ->
                playerManager.attachSurfaceView(surfaceView)
            },
            modifier = Modifier.fillMaxSize()
        )

        if (playbackState == PlayerState.BUFFERING) {
            CircularProgressIndicator(
                color = OttGold,
                modifier = Modifier
                    .size(40.dp)
                    .align(Alignment.Center)
            )
        }

        if (playbackState == PlayerState.ERROR && !isPipMode) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .align(Alignment.Center)
                    .background(Color.Black.copy(alpha = 0.75f), RoundedCornerShape(12.dp))
                    .padding(16.dp)
            ) {
                Text(
                    text = "Stream connection interrupted",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
                IconButton(
                    onClick = { playerManager.retry() },
                    modifier = Modifier
                        .background(OttGold, CircleShape)
                        .size(40.dp)
                        .testTag("player_retry_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Retry",
                        tint = Color.Black
                    )
                }
            }
        }

        // Hide all overlay controls when in Picture-in-Picture mode
        if (!isPipMode && controlsVisible) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.65f),
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.7f)
                            )
                        )
                    )
            ) {
                // Top bar: Minimize (down arrow), Channel title, and PiP button
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    IconButton(
                        onClick = onMinimizeToMiniPlayer,
                        modifier = Modifier.testTag("player_minimize_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = "Minimize to Mini Player",
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                    Text(
                        text = channel.name,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = onEnterPip,
                        modifier = Modifier.testTag("player_pip_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.PictureInPictureAlt,
                            contentDescription = "Picture in Picture",
                            tint = Color.White
                        )
                    }
                    IconButton(
                        onClick = onToggleFullscreen,
                        modifier = Modifier.testTag("player_fullscreen_button")
                    ) {
                        Icon(
                            imageVector = if (isFullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                            contentDescription = "Toggle Fullscreen",
                            tint = Color.White
                        )
                    }
                }

                // Center Play/Pause button
                IconButton(
                    onClick = { playerManager.togglePlayPause() },
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(56.dp)
                        .background(Color.Black.copy(alpha = 0.55f), CircleShape)
                        .testTag("player_center_play_pause")
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        tint = OttGold,
                        modifier = Modifier.size(32.dp)
                    )
                }

                // Bottom LIVE badge
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(Color(0xFFFF3B30), CircleShape)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (channel.isMovieOrVod()) "VOD STREAM" else "LIVE STREAM",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
