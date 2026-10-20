package com.example.player

import android.app.Activity
import android.content.Context
import android.media.AudioManager
import android.net.Uri
import android.view.ViewGroup
import android.view.WindowManager
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.BrightnessMedium
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.example.ad.InStreamAdManager
import com.example.ad.YouTubeVideoAdOverlay
import com.example.data.model.Channel
import com.example.ui.i18n.LocalAppStrings
import com.example.ui.theme.OttGold
import com.example.ui.theme.OttGreen
import com.example.ui.theme.OttLiveRed
import com.example.ui.theme.OttPrimary
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@OptIn(UnstableApi::class)
@Composable
fun IptvVideoPlayer(
    channel: Channel,
    playerManager: IptvPlayerManager,
    allChannels: List<Channel> = emptyList(),
    isFullscreen: Boolean = false,
    isPipMode: Boolean = false,
    onToggleFullscreen: () -> Unit = {},
    onEnterPip: () -> Unit = {},
    onClosePlayer: (() -> Unit)? = null,
    onChannelSelect: (Channel) -> Unit = {},
    onToggleFavorite: (Channel) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val strings = LocalAppStrings.current
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val activity = context as? Activity
    val isActuallyInPip = isPipMode || (activity?.isInPictureInPictureMode == true)

    // Player state from PlayerManager
    val playbackState by playerManager.playbackState.collectAsState()
    val isPlaying by playerManager.isPlaying.collectAsState()
    val availableQualities by playerManager.availableQualities.collectAsState()
    val selectedQualityLabel by playerManager.selectedQualityLabel.collectAsState()
    val isDataSaverMode by playerManager.isDataSaverMode.collectAsState()

    var aspectRatioMode by remember { mutableStateOf(AspectRatioMode.FIT_16_9) }

    val currentPosition by playerManager.currentPosition.collectAsState()
    val totalDuration by playerManager.duration.collectAsState()
    val isMovieOrVod = channel.isMovieOrVod || totalDuration > 0L

    var isScrubbing by remember { mutableStateOf(false) }
    var scrubPosition by remember { mutableFloatStateOf(0f) }

    fun formatDuration(ms: Long): String {
        val totalSeconds = (ms / 1000).coerceAtLeast(0)
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return if (hours > 0) {
            String.format("%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format("%02d:%02d", minutes, seconds)
        }
    }

    // Controls visibility state
    var showControls by remember { mutableStateOf(true) }
    var showChannelOverlay by remember { mutableStateOf(false) }
    var showQualityMenu by remember { mutableStateOf(false) }
    var showAspectRatioMenu by remember { mutableStateOf(false) }

    // Gesture indicator overlays
    var gestureIndicatorText by remember { mutableStateOf<String?>(null) }
    var gestureIndicatorIcon by remember { mutableStateOf<androidx.compose.ui.graphics.vector.ImageVector?>(null) }
    var gestureIndicatorPercent by remember { mutableFloatStateOf(0f) }
    var showGestureIndicator by remember { mutableStateOf(false) }
    var accumulatedVolumeFraction by remember { mutableFloatStateOf(-1f) }

    // Trigger channel playback via the shared player manager
    DisposableEffect(channel.streamUrl) {
        playerManager.playChannel(channel)

        // Notify in-stream ad manager on channel change to track switch counter (1, 2, or 3 switches)
        InStreamAdManager.onChannelSwitched(
            context = context,
            streamUrl = channel.streamUrl,
            onStartAd = {
                try {
                    playerManager.exoPlayer?.pause()
                } catch (_: Exception) {}
            },
            onEndAd = {
                try {
                    playerManager.exoPlayer?.let { p ->
                        if (!channel.isMovieOrVod && p.isCurrentMediaItemLive) {
                            p.seekToDefaultPosition()
                        }
                        p.play()
                    }
                } catch (_: Exception) {}
            }
        )

        // Keep screen on during playback
        activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        onDispose {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    // Auto-hide controls after 4 seconds of inactivity
    LaunchedEffect(showControls, isPlaying) {
        if (showControls && isPlaying) {
            delay(4000)
            showControls = false
        }
    }

    // Auto-hide gesture indicator
    LaunchedEffect(showGestureIndicator) {
        if (showGestureIndicator) {
            delay(1200)
            showGestureIndicator = false
        }
    }

    // Find next and previous channels
    val currentIndex = remember(channel, allChannels) {
        allChannels.indexOfFirst { it.streamUrl == channel.streamUrl }
    }
    val hasPrev = currentIndex > 0
    val hasNext = currentIndex != -1 && currentIndex < allChannels.size - 1

    Box(
        modifier = modifier
            .background(Color.Black)
            .pointerInput(Unit) {
                detectTransformGestures { _, _, zoom, _ ->
                    if (zoom > 1.2f) {
                        aspectRatioMode = AspectRatioMode.FILL_CROP
                    } else if (zoom < 0.85f) {
                        aspectRatioMode = AspectRatioMode.FIT_16_9
                    }
                }
            }
    ) {
        // Player Surface
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    useController = false
                    keepScreenOn = true
                    val activeExo = playerManager.getPlayer()
                    player = activeExo
                    (videoSurfaceView as? android.view.SurfaceView)?.let {
                        activeExo.setVideoSurfaceView(it)
                    }
                }
            },
            update = { playerView ->
                val activeExo = playerManager.getPlayer()
                if (playerView.player != activeExo) {
                    playerView.player = activeExo
                }
                (playerView.videoSurfaceView as? android.view.SurfaceView)?.let {
                    activeExo.setVideoSurfaceView(it)
                }
                playerView.resizeMode = when (aspectRatioMode) {
                    AspectRatioMode.FIT_16_9, AspectRatioMode.ORIGINAL -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                    AspectRatioMode.FOUR_THREE -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                    AspectRatioMode.FILL -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                    AspectRatioMode.ZOOM, AspectRatioMode.FILL_CROP -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                }
            },
            onReset = {},
            onRelease = { playerView ->
                playerView.player = null
            },
            modifier = if (isActuallyInPip) {
                Modifier.fillMaxSize()
            } else when (aspectRatioMode) {
                AspectRatioMode.FIT_16_9 -> Modifier
                    .align(Alignment.Center)
                    .aspectRatio(16f / 9f)
                AspectRatioMode.FOUR_THREE -> Modifier
                    .align(Alignment.Center)
                    .aspectRatio(4f / 3f)
                AspectRatioMode.ZOOM, AspectRatioMode.FILL_CROP -> Modifier
                    .align(Alignment.Center)
                    .fillMaxSize()
                    .graphicsLayer(scaleX = 1.25f, scaleY = 1.25f)
                AspectRatioMode.FILL -> Modifier
                    .align(Alignment.Center)
                    .fillMaxSize()
                else -> Modifier.fillMaxSize()
            }
        )

        // Touch & Gesture Detection Layer
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(isActuallyInPip, isMovieOrVod) {
                    if (!isActuallyInPip) {
                        detectTapGestures(
                            onTap = {
                                showControls = !showControls
                                showChannelOverlay = false
                            },
                            onDoubleTap = { offset ->
                                if (isMovieOrVod) {
                                    val width = size.width
                                    if (offset.x < width * 0.4f) {
                                        // Left double tap: Rewind 10s (YouTube style)
                                        playerManager.seekBy(-10000L)
                                        gestureIndicatorText = "-10 sec"
                                        gestureIndicatorIcon = Icons.Default.Replay10
                                        gestureIndicatorPercent = 0.5f
                                        showGestureIndicator = true
                                    } else if (offset.x > width * 0.6f) {
                                        // Right double tap: Forward 10s (YouTube style)
                                        playerManager.seekBy(10000L)
                                        gestureIndicatorText = "+10 sec"
                                        gestureIndicatorIcon = Icons.Default.Forward10
                                        gestureIndicatorPercent = 0.5f
                                        showGestureIndicator = true
                                    } else {
                                        // Center double tap: cycle aspect ratio
                                        aspectRatioMode = when (aspectRatioMode) {
                                            AspectRatioMode.FIT_16_9 -> AspectRatioMode.FILL
                                            AspectRatioMode.FILL -> AspectRatioMode.ZOOM
                                            AspectRatioMode.ZOOM -> AspectRatioMode.FOUR_THREE
                                            AspectRatioMode.FOUR_THREE -> AspectRatioMode.ORIGINAL
                                            AspectRatioMode.ORIGINAL, AspectRatioMode.FILL_CROP -> AspectRatioMode.FIT_16_9
                                        }
                                    }
                                } else {
                                    // Live stream: Cycle aspect ratio
                                    aspectRatioMode = when (aspectRatioMode) {
                                        AspectRatioMode.FIT_16_9 -> AspectRatioMode.FILL
                                        AspectRatioMode.FILL -> AspectRatioMode.ZOOM
                                        AspectRatioMode.ZOOM -> AspectRatioMode.FOUR_THREE
                                        AspectRatioMode.FOUR_THREE -> AspectRatioMode.ORIGINAL
                                        AspectRatioMode.ORIGINAL, AspectRatioMode.FILL_CROP -> AspectRatioMode.FIT_16_9
                                    }
                                }
                            }
                        )
                    }
                }
                .pointerInput(isActuallyInPip) {
                    if (!isActuallyInPip) {
                        detectVerticalDragGestures(
                            onDragEnd = {
                                showGestureIndicator = false
                                accumulatedVolumeFraction = -1f
                            },
                            onDragCancel = {
                                showGestureIndicator = false
                                accumulatedVolumeFraction = -1f
                            },
                            onVerticalDrag = { change, dragAmount ->
                                change.consume()
                                val width = size.width
                                val x = change.position.x

                                if (x < width / 2) {
                                    // Left side: Brightness
                                    activity?.let { act ->
                                        val lp = act.window.attributes
                                        val currentBrightness = if (lp.screenBrightness < 0) 0.5f else lp.screenBrightness
                                        val newBrightness = (currentBrightness - (dragAmount / 500f)).coerceIn(0.01f, 1.0f)
                                        lp.screenBrightness = newBrightness
                                        act.window.attributes = lp

                                        gestureIndicatorText = "Brightness ${(newBrightness * 100).roundToInt()}%"
                                        gestureIndicatorIcon = Icons.Default.BrightnessMedium
                                        gestureIndicatorPercent = newBrightness
                                        showGestureIndicator = true
                                    }
                                } else {
                                    // Right side: Volume (Smooth continuous fraction matching brightness)
                                    val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
                                    if (accumulatedVolumeFraction < 0f) {
                                        val currentVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                                        accumulatedVolumeFraction = (currentVol.toFloat() / maxVol.toFloat()).coerceIn(0f, 1f)
                                    }

                                    // Smoothly adjust by exact drag amount matching brightness sensitivity (dragAmount / 500f)
                                    accumulatedVolumeFraction = (accumulatedVolumeFraction - (dragAmount / 500f)).coerceIn(0f, 1f)

                                    val targetVolInt = (accumulatedVolumeFraction * maxVol).roundToInt()
                                    val currentStreamVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                                    if (targetVolInt != currentStreamVol) {
                                        try {
                                            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, targetVolInt, 0)
                                        } catch (_: Exception) {}
                                    }

                                    gestureIndicatorText = "Volume ${(accumulatedVolumeFraction * 100).roundToInt()}%"
                                    gestureIndicatorIcon = Icons.Default.VolumeUp
                                    gestureIndicatorPercent = accumulatedVolumeFraction
                                    showGestureIndicator = true
                                }
                            }
                        )
                    }
            }
        )

        // Gesture HUD overlay (Volume / Brightness)
        if (showGestureIndicator && gestureIndicatorText != null) {
            Surface(
                color = Color.Black.copy(alpha = 0.85f),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(24.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
                ) {
                    gestureIndicatorIcon?.let { icon ->
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                    }
                    Text(
                        text = gestureIndicatorText ?: "",
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        // Buffer / Loading indicator with Slow Internet quick-optimize action
        if (!isActuallyInPip && (playbackState is PlayerState.Connecting || playbackState is PlayerState.Buffering)) {
            Surface(
                color = Color.Black.copy(alpha = 0.78f),
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.16f)),
                modifier = Modifier.align(Alignment.Center)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            strokeWidth = 2.5.dp,
                            color = OttPrimary
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = if (playbackState is PlayerState.Connecting) {
                                "Connecting (Fast Low-Bitrate Start)..."
                            } else {
                                "Optimizing Stream for Your Speed..."
                            },
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    if (!isDataSaverMode) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Surface(
                            onClick = { playerManager.setDataSaverMode(true) },
                            shape = RoundedCornerShape(12.dp),
                            color = OttPrimary.copy(alpha = 0.85f)
                        ) {
                            Text(
                                text = "⚡ Slow Internet? Tap for 360p Data Saver",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }
        }

        // Error state overlay with Next Channel and Retry actions
        if (!isActuallyInPip && playbackState is PlayerState.Error) {
            Surface(
                color = Color.Black.copy(alpha = 0.92f),
                shape = RoundedCornerShape(20.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(24.dp)
                    .widthIn(max = 380.dp)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 22.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = Color.Red.copy(alpha = 0.2f),
                        modifier = Modifier.size(54.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.WifiOff,
                                contentDescription = null,
                                tint = Color(0xFFFF5252),
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = strings.streamOffline,
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = (playbackState as PlayerState.Error).message,
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 12.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(18.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (hasNext) {
                            Button(
                                onClick = { onChannelSelect(allChannels[currentIndex + 1]) },
                                colors = ButtonDefaults.buttonColors(containerColor = OttPrimary),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.SkipNext,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Next Channel", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                        OutlinedButton(
                            onClick = { playerManager.retry() },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.4f)),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(strings.retry, fontSize = 13.sp)
                        }
                    }
                }
            }
        }

        // Custom Overlay Controls
        AnimatedVisibility(
            visible = showControls && !isActuallyInPip,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.8f),
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.85f)
                            )
                        )
                    )
            ) {
                // Top Action Bar (LIVE badge + polished controls, channel name & category removed as requested)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (onClosePlayer != null || isFullscreen) {
                        Surface(
                            onClick = {
                                if (isFullscreen) onToggleFullscreen() else onClosePlayer?.invoke()
                            },
                            shape = CircleShape,
                            color = Color.Black.copy(alpha = 0.5f),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
                            modifier = Modifier
                                .size(38.dp)
                                .testTag("player_back_button")
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                    }

                    // Badge placed where channel name was (MOVIE for VOD, LIVE for live channels)
                    Surface(
                        color = if (channel.isMovieOrVod) OttPrimary else OttLiveRed,
                        shape = RoundedCornerShape(5.dp),
                        shadowElevation = 3.dp
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.5.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(5.dp)
                                    .background(Color.White, CircleShape)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (channel.isMovieOrVod) "MOVIE" else "LIVE",
                                color = Color.White,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 0.5.sp
                            )
                        }
                    }

                    // Channel Logo with instant monogram fallback
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color.White.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        com.example.ui.components.ChannelLogo(
                            channel = channel,
                            modifier = Modifier.fillMaxSize(),
                            imagePadding = 2.dp,
                            fontSize = 10.sp,
                            showCategoryIcon = false,
                            cornerRadius = 6.dp
                        )
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    // Aspect Ratio Toggle (Modern glassmorphic pill with Dropdown Menu)
                    Box {
                        Surface(
                            onClick = { showAspectRatioMenu = !showAspectRatioMenu },
                            shape = RoundedCornerShape(19.dp),
                            color = Color.Black.copy(alpha = 0.5f),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
                            modifier = Modifier
                                .testTag("player_aspect_ratio_button")
                                .height(38.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AspectRatio,
                                    contentDescription = "Aspect Ratio",
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = aspectRatioMode.label,
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }

                        DropdownMenu(
                            expanded = showAspectRatioMenu,
                            onDismissRequest = { showAspectRatioMenu = false }
                        ) {
                            val modes = listOf(
                                AspectRatioMode.FIT_16_9 to "16:9 (Standard)",
                                AspectRatioMode.FILL to "Full (Stretch)",
                                AspectRatioMode.ZOOM to "Zoom (Fill Screen)",
                                AspectRatioMode.FOUR_THREE to "4:3 (Classic TV)",
                                AspectRatioMode.ORIGINAL to "Fit (Original Stream)"
                            )
                            for ((mode, label) in modes) {
                                DropdownMenuItem(
                                    text = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = label,
                                                fontWeight = if (aspectRatioMode == mode) FontWeight.Bold else FontWeight.Normal,
                                                color = if (aspectRatioMode == mode) Color(0xFFFFC107) else Color.Unspecified
                                            )
                                            if (aspectRatioMode == mode) {
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Icon(
                                                    imageVector = Icons.Default.Check,
                                                    contentDescription = null,
                                                    tint = Color(0xFFFFC107),
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }
                                    },
                                    onClick = {
                                        aspectRatioMode = mode
                                        showAspectRatioMenu = false
                                    }
                                )
                            }
                        }
                    }

                    // Quality & Slow-Internet Data Saver Selector Button (Always available)
                    Spacer(modifier = Modifier.width(6.dp))
                    Box {
                        Surface(
                            onClick = { showQualityMenu = !showQualityMenu },
                            shape = RoundedCornerShape(19.dp),
                            color = if (isDataSaverMode) OttPrimary.copy(alpha = 0.75f) else Color.Black.copy(alpha = 0.5f),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.25f)),
                            modifier = Modifier
                                .height(38.dp)
                                .testTag("player_quality_button")
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.HighQuality,
                                    contentDescription = "Quality",
                                    tint = Color.White,
                                    modifier = Modifier.size(17.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (isDataSaverMode) "360p Saver" else selectedQualityLabel.substringBefore(" "),
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }
                        DropdownMenu(
                            expanded = showQualityMenu,
                            onDismissRequest = { showQualityMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = "Auto (Smart Adaptive)",
                                            fontWeight = if (!isDataSaverMode && selectedQualityLabel == "Auto") FontWeight.Bold else FontWeight.Normal,
                                            color = if (!isDataSaverMode && selectedQualityLabel == "Auto") Color(0xFFFFC107) else Color.Unspecified
                                        )
                                        if (!isDataSaverMode && selectedQualityLabel == "Auto") {
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = null,
                                                tint = Color(0xFFFFC107),
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                },
                                onClick = {
                                    playerManager.selectQuality(null)
                                    showQualityMenu = false
                                }
                            )
                            DropdownMenuItem(
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = "⚡ Slow Internet Saver (≤360p)",
                                            fontWeight = if (isDataSaverMode) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isDataSaverMode) Color(0xFFFFC107) else Color.Unspecified
                                        )
                                        if (isDataSaverMode) {
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = null,
                                                tint = Color(0xFFFFC107),
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                },
                                onClick = {
                                    playerManager.setDataSaverMode(!isDataSaverMode)
                                    showQualityMenu = false
                                }
                            )
                            for (quality in availableQualities) {
                                val isSelected = !isDataSaverMode && selectedQualityLabel == quality.label
                                DropdownMenuItem(
                                    text = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = quality.label,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                color = if (isSelected) Color(0xFFFFC107) else Color.Unspecified
                                            )
                                            if (isSelected) {
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Icon(
                                                    imageVector = Icons.Default.Check,
                                                    contentDescription = null,
                                                    tint = Color(0xFFFFC107),
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }
                                    },
                                    onClick = {
                                        playerManager.selectQuality(quality)
                                        showQualityMenu = false
                                    }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    // Picture-in-Picture Button (Modern glassmorphic circle)
                    Surface(
                        onClick = {
                            InStreamAdManager.suppressAdsTemporarily(3000L)
                            (context as? com.example.MainActivity)?.enterPipMode() ?: onEnterPip()
                        },
                        shape = CircleShape,
                        color = Color.Black.copy(alpha = 0.5f),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
                        modifier = Modifier
                            .size(38.dp)
                            .testTag("player_pip_button")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.PictureInPictureAlt,
                                contentDescription = "Picture in Picture",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    // Landscape / Fullscreen Button on Top Control Bar
                    Surface(
                        onClick = {
                            InStreamAdManager.suppressAdsTemporarily(3000L)
                            onToggleFullscreen()
                        },
                        shape = CircleShape,
                        color = Color.Black.copy(alpha = 0.5f),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
                        modifier = Modifier
                            .size(38.dp)
                            .testTag("player_top_fullscreen_button")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (isFullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                                contentDescription = if (isFullscreen) "Exit Landscape" else "Landscape Mode",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                // Center Play/Pause & Channel Next/Previous or Seek 10s Rewind/Forward Controls
                Row(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(horizontal = 24.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(18.dp)
                ) {
                    if (isMovieOrVod) {
                        // Rewind 10 Seconds (-10s)
                        Surface(
                            onClick = {
                                playerManager.seekBy(-10000L)
                            },
                            shape = CircleShape,
                            color = Color.Black.copy(alpha = 0.55f),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.25f)),
                            modifier = Modifier
                                .size(44.dp)
                                .testTag("player_replay_10")
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Replay10,
                                    contentDescription = "Rewind 10 seconds",
                                    tint = Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    } else {
                        // Previous Channel (Live TV mode)
                        Surface(
                            onClick = {
                                if (hasPrev) {
                                    onChannelSelect(allChannels[currentIndex - 1])
                                }
                            },
                            enabled = hasPrev,
                            shape = CircleShape,
                            color = Color.Black.copy(alpha = if (hasPrev) 0.55f else 0.25f),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = if (hasPrev) 0.25f else 0.1f)),
                            modifier = Modifier
                                .size(42.dp)
                                .testTag("player_prev_channel")
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.SkipPrevious,
                                    contentDescription = "Previous Channel",
                                    tint = if (hasPrev) Color.White else Color.Gray,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    }

                    // Play/Pause Main Button
                    Surface(
                        onClick = {
                            playerManager.togglePlayPause()
                        },
                        shape = CircleShape,
                        color = OttPrimary,
                        border = BorderStroke(2.dp, Color.White.copy(alpha = 0.35f)),
                        shadowElevation = 6.dp,
                        modifier = Modifier
                            .size(54.dp)
                            .testTag("player_play_pause")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (isPlaying) "Pause" else "Play",
                                tint = Color.White,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }

                    if (isMovieOrVod) {
                        // Forward 10 Seconds (+10s)
                        Surface(
                            onClick = {
                                playerManager.seekBy(10000L)
                            },
                            shape = CircleShape,
                            color = Color.Black.copy(alpha = 0.55f),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.25f)),
                            modifier = Modifier
                                .size(44.dp)
                                .testTag("player_forward_10")
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Forward10,
                                    contentDescription = "Forward 10 seconds",
                                    tint = Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    } else {
                        // Next Channel (Live TV mode)
                        Surface(
                            onClick = {
                                if (hasNext) {
                                    onChannelSelect(allChannels[currentIndex + 1])
                                }
                            },
                            enabled = hasNext,
                            shape = CircleShape,
                            color = Color.Black.copy(alpha = if (hasNext) 0.55f else 0.25f),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = if (hasNext) 0.25f else 0.1f)),
                            modifier = Modifier
                                .size(42.dp)
                                .testTag("player_next_channel")
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.SkipNext,
                                    contentDescription = "Next Channel",
                                    tint = if (hasNext) Color.White else Color.Gray,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    }
                }

                // Bottom Control Bar
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    // YouTube-style Timeline Slider & Hour:Min:Sec Timestamp (for Movies & VOD)
                    if (isMovieOrVod) {
                        val activePos = if (isScrubbing) scrubPosition.toLong() else currentPosition
                        val effectiveDur = if (totalDuration > 0) totalDuration else 1L

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = formatDuration(activePos),
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )

                            Slider(
                                value = if (isScrubbing) scrubPosition else currentPosition.toFloat(),
                                onValueChange = { newValue ->
                                    isScrubbing = true
                                    scrubPosition = newValue
                                },
                                onValueChangeFinished = {
                                    isScrubbing = false
                                    playerManager.seekTo(scrubPosition.toLong())
                                },
                                valueRange = 0f..effectiveDur.toFloat(),
                                colors = SliderDefaults.colors(
                                    thumbColor = OttPrimary,
                                    activeTrackColor = OttPrimary,
                                    inactiveTrackColor = Color.White.copy(alpha = 0.28f)
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 8.dp)
                                    .height(28.dp)
                                    .testTag("movie_progress_slider")
                            )

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = if (totalDuration > 0) formatDuration(totalDuration) else "--:--",
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                if (totalDuration >= 3600000L) {
                                    val hrs = (totalDuration / 1000) / 3600
                                    val mins = ((totalDuration / 1000) % 3600) / 60
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = Color.White.copy(alpha = 0.22f)
                                    ) {
                                        Text(
                                            text = "${hrs}h ${mins}m",
                                            color = Color.White,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Surface(
                            color = Color.Black.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f))
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(5.dp)
                                        .background(OttGreen, CircleShape)
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = if (isMovieOrVod) "VOD Movie" else "HLS Live Stream",
                                    color = Color.White.copy(alpha = 0.9f),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }

                        // YouTube-style Fullscreen Button in Bottom-Right of Video
                        Surface(
                            onClick = {
                                InStreamAdManager.suppressAdsTemporarily(3000L)
                                onToggleFullscreen()
                            },
                            shape = CircleShape,
                            color = Color.Black.copy(alpha = 0.55f),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.25f)),
                            modifier = Modifier
                                .testTag("player_bottom_fullscreen_button")
                                .size(40.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = if (isFullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                                    contentDescription = if (isFullscreen) "Exit Fullscreen" else "Enter Fullscreen",
                                    tint = Color.White,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Quick Channel List Overlay Drawer Inside Video Player
        AnimatedVisibility(
            visible = showChannelOverlay && !isActuallyInPip,
            enter = slideInVertically(initialOffsetY = { it }),
            exit = slideOutVertically(targetOffsetY = { it }),
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.6f)
                .align(Alignment.BottomCenter)
        ) {
            Surface(
                color = Color(0xEE0F172A),
                shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Switch Channel",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        IconButton(onClick = { showChannelOverlay = false }) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = Color.White
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    LazyColumn(
                        contentPadding = PaddingValues(vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(allChannels, key = { it.streamUrl }) { ch ->
                            val isSelected = ch.streamUrl == channel.streamUrl
                            Surface(
                                onClick = {
                                    onChannelSelect(ch)
                                    showChannelOverlay = false
                                },
                                shape = RoundedCornerShape(10.dp),
                                color = if (isSelected) OttPrimary.copy(alpha = 0.35f) else Color.White.copy(alpha = 0.08f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(Color.White.copy(alpha = 0.15f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        com.example.ui.components.ChannelLogo(
                                            channel = ch,
                                            modifier = Modifier.fillMaxSize(),
                                            imagePadding = 3.dp,
                                            fontSize = 11.sp,
                                            showCategoryIcon = false,
                                            cornerRadius = 6.dp
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = ch.name,
                                            color = Color.White,
                                            fontSize = 14.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = ch.category,
                                            color = Color.LightGray,
                                            fontSize = 11.sp
                                        )
                                    }
                                    if (isSelected) {
                                        Surface(
                                            color = OttPrimary,
                                            shape = RoundedCornerShape(4.dp)
                                        ) {
                                            Text(
                                                text = "PLAYING",
                                                color = Color.White,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
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

        // YouTube-style In-Stream Video Ad Overlay (Portrait & Landscape & Fullscreen)
        val inStreamAdState by InStreamAdManager.adState.collectAsState()
        if (inStreamAdState.isAdActive && !isActuallyInPip) {
            YouTubeVideoAdOverlay(
                adState = inStreamAdState,
                onSkipAd = {
                    InStreamAdManager.dismissAd(
                        onResumePlayback = {
                            try {
                                playerManager.exoPlayer?.play()
                            } catch (_: Exception) {}
                        }
                    )
                },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
