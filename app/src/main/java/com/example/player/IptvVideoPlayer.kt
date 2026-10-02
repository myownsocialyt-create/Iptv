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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
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
import com.example.data.model.Channel
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
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val activity = context as? Activity

    // Player state from PlayerManager
    val playbackState by playerManager.playbackState.collectAsState()
    val isPlaying by playerManager.isPlaying.collectAsState()
    val availableQualities by playerManager.availableQualities.collectAsState()
    val selectedQualityLabel by playerManager.selectedQualityLabel.collectAsState()
    val currentPosition by playerManager.currentPosition.collectAsState()
    val duration by playerManager.duration.collectAsState()
    val bufferedPosition by playerManager.bufferedPosition.collectAsState()
    val isSeekable by playerManager.isSeekable.collectAsState()

    val isMovie = channel.isMovieOrVod

    var aspectRatioMode by remember { mutableStateOf(AspectRatioMode.FIT_16_9) }

    // Controls visibility state
    var showControls by remember { mutableStateOf(true) }
    var showChannelOverlay by remember { mutableStateOf(false) }
    var showQualityMenu by remember { mutableStateOf(false) }

    // YouTube-style In-Stream Ad State
    val inStreamAdState by com.example.ad.InStreamAdManager.adState.collectAsState()

    // Random Preroll Ad check on channel change
    LaunchedEffect(channel.streamUrl) {
        com.example.ad.InStreamAdManager.onChannelStarted(channel.streamUrl, context)
    }

    // Sync random ad loop with playback state
    LaunchedEffect(isPlaying) {
        com.example.ad.InStreamAdManager.updatePlaybackStatus(isPlaying, context)
    }

    // Mute video stream audio and hide controls while in-stream ad is playing
    LaunchedEffect(inStreamAdState.isAdActive) {
        if (inStreamAdState.isAdActive) {
            playerManager.exoPlayer?.volume = 0f
            showControls = false
        } else {
            playerManager.exoPlayer?.volume = 1f
        }
    }

    // Gesture indicator overlays
    var gestureIndicatorText by remember { mutableStateOf<String?>(null) }
    var gestureIndicatorIcon by remember { mutableStateOf<androidx.compose.ui.graphics.vector.ImageVector?>(null) }
    var gestureIndicatorPercent by remember { mutableFloatStateOf(0f) }
    var showGestureIndicator by remember { mutableStateOf(false) }
    var accumulatedVolumeFraction by remember { mutableFloatStateOf(-1f) }
    var cachedMaxVol by remember { mutableIntStateOf(15) }
    var cachedCurrentVolInt by remember { mutableIntStateOf(-1) }

    LaunchedEffect(gestureIndicatorText) {
        if (gestureIndicatorText != null) {
            delay(1200)
            showGestureIndicator = false
            gestureIndicatorText = null
        }
    }

    // Trigger channel playback via the shared player manager
    DisposableEffect(channel.streamUrl) {
        playerManager.playChannel(channel)

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
                    player = playerManager.exoPlayer
                }
            },
            update = { playerView ->
                if (playerView.player != playerManager.exoPlayer) {
                    playerView.player = playerManager.exoPlayer
                }
                playerView.resizeMode = when (aspectRatioMode) {
                    AspectRatioMode.FIT_16_9 -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                    AspectRatioMode.FOUR_THREE -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                    AspectRatioMode.FILL_CROP, AspectRatioMode.ZOOM, AspectRatioMode.FILL -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                    else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                }
            },
            onReset = { playerView ->
                playerView.player = null
            },
            onRelease = { playerView ->
                playerView.player = null
            },
            modifier = when (aspectRatioMode) {
                AspectRatioMode.FOUR_THREE -> Modifier
                    .align(Alignment.Center)
                    .fillMaxHeight()
                    .aspectRatio(4f / 3f, matchHeightConstraintsFirst = true)
                else -> Modifier.fillMaxSize()
            }
        )

        // Touch & Gesture Detection Layer
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(isPipMode) {
                    if (!isPipMode) {
                        detectTapGestures(
                            onTap = {
                                showControls = !showControls
                                showChannelOverlay = false
                            },
                            onDoubleTap = { offset ->
                                if (isMovie) {
                                    val width = size.width
                                    if (offset.x < width * 0.4f) {
                                        playerManager.seekBackward(10_000L)
                                        gestureIndicatorText = "-10s"
                                        showGestureIndicator = true
                                    } else if (offset.x > width * 0.6f) {
                                        playerManager.seekForward(10_000L)
                                        gestureIndicatorText = "+10s"
                                        showGestureIndicator = true
                                    } else {
                                        aspectRatioMode = when (aspectRatioMode) {
                                            AspectRatioMode.FIT_16_9 -> AspectRatioMode.FILL_CROP
                                            AspectRatioMode.FILL_CROP -> AspectRatioMode.FOUR_THREE
                                            else -> AspectRatioMode.FIT_16_9
                                        }
                                    }
                                } else {
                                    // Cycle aspect ratio
                                    aspectRatioMode = when (aspectRatioMode) {
                                        AspectRatioMode.FIT_16_9 -> AspectRatioMode.FILL_CROP
                                        AspectRatioMode.FILL_CROP -> AspectRatioMode.FOUR_THREE
                                        else -> AspectRatioMode.FIT_16_9
                                    }
                                }
                            }
                        )
                    }
                }
                .pointerInput(isPipMode) {
                    if (!isPipMode) {
                        detectVerticalDragGestures(
                            onDragStart = { offset ->
                                val width = size.width
                                if (offset.x >= width / 2) {
                                    val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
                                    val currentVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                                    cachedMaxVol = maxVol
                                    cachedCurrentVolInt = currentVol
                                    accumulatedVolumeFraction = (currentVol.toFloat() / maxVol.toFloat()).coerceIn(0f, 1f)
                                }
                            },
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
                                    // Right side: Volume (Smooth continuous drag matching brightness)
                                    if (accumulatedVolumeFraction < 0f) {
                                        val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
                                        val currentVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                                        cachedMaxVol = maxVol
                                        cachedCurrentVolInt = currentVol
                                        accumulatedVolumeFraction = (currentVol.toFloat() / maxVol.toFloat()).coerceIn(0f, 1f)
                                    }

                                    // Smoothly adjust by exact drag amount matching brightness sensitivity
                                    accumulatedVolumeFraction = (accumulatedVolumeFraction - (dragAmount / 500f)).coerceIn(0f, 1f)

                                    val targetVolInt = (accumulatedVolumeFraction * cachedMaxVol).roundToInt()
                                    if (targetVolInt != cachedCurrentVolInt) {
                                        cachedCurrentVolInt = targetVolInt
                                        try {
                                            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, targetVolInt, 0)
                                        } catch (_: Exception) {}
                                    }

                                    // Smooth real-time software audio volume for continuous sound level without stepping
                                    playerManager.exoPlayer?.volume = accumulatedVolumeFraction

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

        // Buffer / Loading indicator
        if (!isPipMode && (playbackState is PlaybackState.Connecting || playbackState is PlaybackState.Buffering)) {
            Surface(
                color = Color.Black.copy(alpha = 0.65f),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.align(Alignment.Center)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        strokeWidth = 2.5.dp,
                        color = OttPrimary
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = if (playbackState is PlaybackState.Connecting) "Connecting..." else "Buffering...",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        // Error state overlay with Next Channel and Retry actions
        if (!isPipMode && playbackState is PlaybackState.Error) {
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
                        text = "Stream Offline",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = (playbackState as PlaybackState.Error).message,
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
                            Text("Retry", fontSize = 13.sp)
                        }
                    }
                }
            }
        }

        // Custom Overlay Controls
        AnimatedVisibility(
            visible = showControls && !isPipMode && !inStreamAdState.isAdActive,
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

                    // Badge: MOVIE with Hours & Minutes for Movies, LIVE for Live TV
                    if (isMovie) {
                        Surface(
                            color = OttGold,
                            shape = RoundedCornerShape(5.dp),
                            shadowElevation = 3.dp
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.5.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Movie,
                                    contentDescription = null,
                                    tint = Color.Black,
                                    modifier = Modifier.size(11.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                val runtimeStr = if (duration > 0) " • ${formatHoursMinutes(duration)}" else ""
                                Text(
                                    text = "MOVIE$runtimeStr",
                                    color = Color.Black,
                                    fontSize = 9.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.5.sp
                                )
                            }
                        }
                    } else {
                        Surface(
                            color = OttLiveRed,
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
                                    text = "LIVE",
                                    color = Color.White,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    letterSpacing = 0.5.sp
                                )
                            }
                        }
                    }

                    // Channel Logo if available
                    if (!channel.logoUrl.isNullOrBlank()) {
                        Spacer(modifier = Modifier.width(8.dp))
                        AsyncImage(
                            model = channel.logoUrl,
                            contentDescription = null,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .size(28.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color.White.copy(alpha = 0.15f))
                        )
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    // Aspect Ratio Toggle (Modern glassmorphic pill)
                    Surface(
                        onClick = {
                            aspectRatioMode = when (aspectRatioMode) {
                                AspectRatioMode.FIT_16_9 -> AspectRatioMode.FILL_CROP
                                AspectRatioMode.FILL_CROP -> AspectRatioMode.FOUR_THREE
                                else -> AspectRatioMode.FIT_16_9
                            }
                        },
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

                    // Quality Selector Button
                    if (availableQualities.isNotEmpty()) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Box {
                            Surface(
                                onClick = { showQualityMenu = !showQualityMenu },
                                shape = CircleShape,
                                color = Color.Black.copy(alpha = 0.5f),
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
                                modifier = Modifier
                                    .size(38.dp)
                                    .testTag("player_quality_button")
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.HighQuality,
                                        contentDescription = "Quality",
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                            DropdownMenu(
                                expanded = showQualityMenu,
                                onDismissRequest = { showQualityMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Auto") },
                                    onClick = {
                                        playerManager.selectQuality(null)
                                        showQualityMenu = false
                                    }
                                )
                                availableQualities.forEach { quality ->
                                    DropdownMenuItem(
                                        text = { Text(quality.label) },
                                        onClick = {
                                            playerManager.selectQuality(quality)
                                            showQualityMenu = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    // Picture-in-Picture Button (Modern glassmorphic circle)
                    Surface(
                        onClick = onEnterPip,
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
                }

                // Center Play/Pause & Channel Next/Previous Controls (or -10s / +10s for Movies)
                Row(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(horizontal = 24.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(18.dp)
                ) {
                    if (isMovie) {
                        // Rewind 10s button
                        Surface(
                            onClick = { playerManager.seekBackward(10_000L) },
                            shape = CircleShape,
                            color = Color.Black.copy(alpha = 0.65f),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.25f)),
                            modifier = Modifier
                                .size(46.dp)
                                .testTag("player_center_rewind_10s")
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = "-10s",
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
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
                                .size(56.dp)
                                .testTag("player_play_pause")
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = if (isPlaying) "Pause" else "Play",
                                    tint = Color.White,
                                    modifier = Modifier.size(34.dp)
                                )
                            }
                        }

                        // Forward 10s button
                        Surface(
                            onClick = { playerManager.seekForward(10_000L) },
                            shape = CircleShape,
                            color = Color.Black.copy(alpha = 0.65f),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.25f)),
                            modifier = Modifier
                                .size(46.dp)
                                .testTag("player_center_forward_10s")
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = "+10s",
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }
                        }
                    } else {
                        // Previous Channel
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

                        // Next Channel
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

                // Bottom Control Bar (YouTube Scrubber & Hours/Minutes for Movies, Live Bar for Live TV)
                if (isMovie) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter)
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        var isDraggingSlider by remember { mutableStateOf(false) }
                        var dragProgressMs by remember { mutableFloatStateOf(0f) }

                        val currentDisplayPos = if (isDraggingSlider) dragProgressMs.toLong() else currentPosition
                        val safeDuration = maxOf(1L, duration)
                        val sliderValue = (currentDisplayPos.toFloat()).coerceIn(0f, safeDuration.toFloat())

                        // 1. YouTube Scrubber Slider
                        Slider(
                            value = sliderValue,
                            onValueChange = { newVal ->
                                isDraggingSlider = true
                                dragProgressMs = newVal
                            },
                            onValueChangeFinished = {
                                isDraggingSlider = false
                                playerManager.seekTo(dragProgressMs.toLong())
                            },
                            valueRange = 0f..safeDuration.toFloat(),
                            colors = SliderDefaults.colors(
                                thumbColor = OttPrimary,
                                activeTrackColor = OttPrimary,
                                inactiveTrackColor = Color.White.copy(alpha = 0.25f)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(26.dp)
                                .testTag("player_movie_slider")
                        )

                        // 2. Info Row: Timestamp (hours & mins like YouTube) + Seek buttons + Fullscreen
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            // YouTube Style Time Display (shows hours & minutes)
                            Surface(
                                color = Color.Black.copy(alpha = 0.6f),
                                shape = RoundedCornerShape(6.dp),
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f))
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                ) {
                                    Text(
                                        text = "${formatYouTubeTime(currentDisplayPos)} / ${formatYouTubeTime(duration)}",
                                        color = Color.White,
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    if (duration > 0) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "(${formatHoursMinutes(duration)})",
                                            color = OttGold,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Surface(
                                    onClick = { playerManager.seekBackward(10_000L) },
                                    shape = CircleShape,
                                    color = Color.Black.copy(alpha = 0.55f),
                                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.25f)),
                                    modifier = Modifier
                                        .testTag("player_bottom_rewind_10s")
                                        .size(34.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = "-10s",
                                            color = Color.White,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }

                                Surface(
                                    onClick = { playerManager.seekForward(10_000L) },
                                    shape = CircleShape,
                                    color = Color.Black.copy(alpha = 0.55f),
                                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.25f)),
                                    modifier = Modifier
                                        .testTag("player_bottom_forward_10s")
                                        .size(34.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = "+10s",
                                            color = Color.White,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }

                                Surface(
                                    onClick = onToggleFullscreen,
                                    shape = CircleShape,
                                    color = Color.Black.copy(alpha = 0.55f),
                                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.25f)),
                                    modifier = Modifier
                                        .testTag("player_bottom_fullscreen_button")
                                        .size(38.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = if (isFullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                                            contentDescription = if (isFullscreen) "Exit Fullscreen" else "Enter Fullscreen",
                                            tint = Color.White,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter)
                            .padding(horizontal = 16.dp, vertical = 12.dp),
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
                                    text = "HLS Live Stream",
                                    color = Color.White.copy(alpha = 0.9f),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }

                        // YouTube-style Fullscreen Button in Bottom-Right of Video
                        Surface(
                            onClick = onToggleFullscreen,
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
            visible = showChannelOverlay,
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
                                    if (!ch.logoUrl.isNullOrBlank()) {
                                        AsyncImage(
                                            model = ch.logoUrl,
                                            contentDescription = ch.name,
                                            contentScale = ContentScale.Fit,
                                            modifier = Modifier
                                                .size(36.dp)
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(Color.White.copy(alpha = 0.2f))
                                        )
                                    } else {
                                        Surface(
                                            modifier = Modifier.size(36.dp),
                                            shape = RoundedCornerShape(6.dp),
                                            color = OttPrimary.copy(alpha = 0.25f)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Text(
                                                    text = ch.name.take(2).uppercase(),
                                                    color = Color.White,
                                                    fontSize = 13.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
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

        // YouTube-style In-Stream Video Ad Overlay (Photos 2 & 3: Landscape & Portrait)
        if (inStreamAdState.isAdActive && !isPipMode) {
            com.example.ad.YouTubeVideoAdOverlay(
                adState = inStreamAdState,
                onSkipAd = { com.example.ad.InStreamAdManager.dismissAd() },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

private fun formatYouTubeTime(millis: Long): String {
    if (millis <= 0L) return "0:00"
    val totalSeconds = millis / 1000
    val seconds = totalSeconds % 60
    val minutes = (totalSeconds / 60) % 60
    val hours = totalSeconds / 3600
    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%d:%02d", minutes, seconds)
    }
}

private fun formatHoursMinutes(millis: Long): String {
    if (millis <= 0L) return ""
    val totalMinutes = millis / (1000 * 60)
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return when {
        hours > 0 && minutes > 0 -> "${hours} hr ${minutes} min"
        hours > 0 -> "${hours} hr"
        else -> "${minutes} min"
    }
}

