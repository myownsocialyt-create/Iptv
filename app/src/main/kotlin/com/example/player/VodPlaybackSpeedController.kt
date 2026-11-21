package com.example.player

import android.util.Log
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.PlaybackParameters
import androidx.media3.exoplayer.ExoPlayer
import com.example.premium.AppUiTranslator
import java.lang.reflect.Method
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object VodPlaybackSpeedController {
    private const val TAG = "VodPlaybackSpeed"

    val SPEED_OPTIONS: List<Float> = listOf(0.5f, 1.0f, 1.25f, 1.5f, 2.0f)
    const val DEFAULT_SPEED: Float = 1.0f
    internal const val DEFAULT_LIVE_WINDOW_MS: Long = 30_000L
    internal const val LIVE_WINDOW_MIN_DURATION_MS: Long = 3_000L
    internal const val LIVE_EDGE_TOLERANCE_MS: Long = 2_500L
    internal const val LIVE_EDGE_SNAP_FRACTION: Float = 0.96f

    internal data class LiveTimelineState(
        val progress: Float,
        val behindSeconds: Int,
        val label: String
    )

    private val _currentSpeed = MutableStateFlow(DEFAULT_SPEED)
    val currentSpeed: StateFlow<Float> = _currentSpeed.asStateFlow()

    @Volatile
    private var cachedGetExoPlayerMethod: Method? = null

    @Volatile
    private var cachedSeekToMethod: Method? = null

    @Volatile
    private var activeLiveFractionRef: MutableFloatState? = null

    @JvmStatic
    fun formatSpeedLabel(speed: Float): String {
        return when {
            speed == 0.5f -> "0.5x"
            speed == 1.0f -> "1x"
            speed == 1.25f -> "1.25x"
            speed == 1.5f -> "1.5x"
            speed == 2.0f -> "2x"
            else -> String.format(Locale.US, "%.2gx", speed)
        }
    }

    @JvmStatic
    fun sanitizeSpeed(speed: Float): Float {
        return SPEED_OPTIONS.minByOrNull { kotlin.math.abs(it - speed) } ?: DEFAULT_SPEED
    }

    @JvmStatic
    fun applyPlaybackSpeed(playerManager: Any?, requestedSpeed: Float): Float {
        val safeSpeed = sanitizeSpeed(requestedSpeed)
        _currentSpeed.value = safeSpeed
        val exoPlayer = extractExoPlayer(playerManager)
        applySpeedToExoPlayer(exoPlayer, safeSpeed)
        return safeSpeed
    }

    @JvmStatic
    fun resetSpeedForNewChannel(playerManager: Any?) {
        _currentSpeed.value = DEFAULT_SPEED
        activeLiveFractionRef?.floatValue = 1f
        val exoPlayer = extractExoPlayer(playerManager)
        applySpeedToExoPlayer(exoPlayer, DEFAULT_SPEED)
    }

    @JvmStatic
    fun syncSpeedOnPlayerReady(playerManager: Any?, channel: Any?) {
        val exoPlayer = extractExoPlayer(playerManager) ?: return
        if (isVodChannel(channel)) {
            val activeSpeed = _currentSpeed.value
            if (activeSpeed != DEFAULT_SPEED) {
                applySpeedToExoPlayer(exoPlayer, activeSpeed)
            }
        } else {
            if (_currentSpeed.value != DEFAULT_SPEED) {
                _currentSpeed.value = DEFAULT_SPEED
            }
            applySpeedToExoPlayer(exoPlayer, DEFAULT_SPEED)
        }
    }

    private fun applySpeedToExoPlayer(exoPlayer: ExoPlayer?, speed: Float) {
        if (exoPlayer == null) return
        try {
            exoPlayer.playbackParameters = PlaybackParameters(speed)
        } catch (t: Throwable) {
            Log.w(TAG, "Failed setting ExoPlayer playback speed to $speed", t)
        }
    }

    private fun extractExoPlayer(playerManager: Any?): ExoPlayer? {
        if (playerManager == null) return null
        return try {
            var method = cachedGetExoPlayerMethod
            if (method == null || method.declaringClass != playerManager.javaClass) {
                method = playerManager.javaClass.getMethod("getExoPlayer")
                cachedGetExoPlayerMethod = method
            }
            method?.invoke(playerManager) as? ExoPlayer
        } catch (_: Throwable) {
            null
        }
    }

    private fun seekPlayer(playerManager: Any?, positionMs: Long) {
        if (playerManager == null) return
        try {
            var method = cachedSeekToMethod
            if (method == null || method.declaringClass != playerManager.javaClass) {
                method = playerManager.javaClass.getMethod("seekTo", Long::class.javaPrimitiveType)
                cachedSeekToMethod = method
            }
            method?.invoke(playerManager, positionMs)
        } catch (t: Throwable) {
            Log.w(TAG, "seekTo failed", t)
        }
    }

    private fun seekToLiveEdge(playerManager: Any?) {
        if (playerManager == null) return
        try {
            val exoPlayer = extractExoPlayer(playerManager)
            if (exoPlayer != null) {
                exoPlayer.seekToDefaultPosition()
                exoPlayer.play()
                return
            }
            val method = playerManager.javaClass.getMethod("seekToLive")
            method.invoke(playerManager)
        } catch (t: Throwable) {
            Log.w(TAG, "seekToLiveEdge failed", t)
        }
    }

    private fun registerLiveFractionWithHelper(state: MutableFloatState) {
        activeLiveFractionRef = state
        try {
            val cls = Class.forName("com.example.player.LiveTimerHelper")
            val method = cls.getMethod("registerLiveFractionState", MutableFloatState::class.java)
            method.invoke(null, state)
        } catch (_: Throwable) {
            // Plain unit test environment without precompiled LiveTimerHelper class.
        }
    }

    @JvmStatic
    fun formatLiveBehindLabel(behindSeconds: Int): String {
        val safeSec = behindSeconds.coerceAtLeast(0)
        if (safeSec == 0) return "0:00 (LIVE)"
        val hours = safeSec / 3600
        val minutes = (safeSec % 3600) / 60
        val seconds = safeSec % 60
        return when {
            hours > 0 -> String.format(Locale.US, "-%d:%02d:%02d", hours, minutes, seconds)
            minutes > 0 -> String.format(Locale.US, "-%d:%02d", minutes, seconds)
            else -> String.format(Locale.US, "-0:%02d (-%ds)", seconds, seconds)
        }
    }

    internal fun computeLiveTimelineState(
        isScrubbing: Boolean,
        currentPositionMs: Long,
        durationMs: Long,
        manualFraction: Float
    ): LiveTimelineState {
        val clampedManual = manualFraction.coerceIn(0f, 1f)
        val hasRealWindow = durationMs > LIVE_WINDOW_MIN_DURATION_MS &&
            currentPositionMs in 0L..durationMs

        if (isScrubbing) {
            val windowMs = if (hasRealWindow) durationMs else DEFAULT_LIVE_WINDOW_MS
            val behindMs = ((1f - clampedManual) * windowMs.toFloat()).toLong().coerceAtLeast(0L)
            val behindSeconds = if (clampedManual >= LIVE_EDGE_SNAP_FRACTION || behindMs < 500L) {
                0
            } else {
                kotlin.math.round(behindMs / 1000f).toInt().coerceAtLeast(1)
            }
            val syncedProgress = if (behindSeconds == 0) 1f else clampedManual
            return LiveTimelineState(
                progress = syncedProgress,
                behindSeconds = behindSeconds,
                label = formatLiveBehindLabel(behindSeconds)
            )
        }

        if (hasRealWindow) {
            val behindMs = (durationMs - currentPositionMs).coerceAtLeast(0L)
            if (behindMs <= LIVE_EDGE_TOLERANCE_MS) {
                return LiveTimelineState(
                    progress = 1f,
                    behindSeconds = 0,
                    label = formatLiveBehindLabel(0)
                )
            }
            val behindSeconds = kotlin.math.round(behindMs / 1000f).toInt().coerceAtLeast(1)
            val progress = (currentPositionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
            return LiveTimelineState(
                progress = progress,
                behindSeconds = behindSeconds,
                label = formatLiveBehindLabel(behindSeconds)
            )
        }

        val behindSeconds = kotlin.math.round((1f - clampedManual) * (DEFAULT_LIVE_WINDOW_MS / 1000f))
            .toInt()
            .coerceIn(0, (DEFAULT_LIVE_WINDOW_MS / 1000L).toInt())
        val syncedProgress = if (behindSeconds == 0) 1f else clampedManual
        return LiveTimelineState(
            progress = syncedProgress,
            behindSeconds = behindSeconds,
            label = formatLiveBehindLabel(behindSeconds)
        )
    }

    internal fun isVodChannel(channel: Any?): Boolean {
        if (channel == null) return false
        if (XtreamPlaybackHelper.extractChannelIsVod(channel)) return true
        val id = XtreamPlaybackHelper.extractChannelId(channel).orEmpty()
        if (id.startsWith("vod_", ignoreCase = true)) return true
        val url = XtreamPlaybackHelper.extractChannelStreamUrl(channel).orEmpty().lowercase(Locale.ROOT)
        return url.contains("/movie/") || url.contains("/series/")
    }

    private fun isManagerPlayingVod(playerManager: Any?): Boolean {
        if (playerManager == null) return false
        return try {
            val field = playerManager.javaClass.getDeclaredField("currentChannel")
            field.isAccessible = true
            isVodChannel(field.get(playerManager))
        } catch (_: Throwable) {
            false
        }
    }

    @JvmStatic
    fun formatPlayerDuration(ms: Long): String {
        if (ms <= 0L) return "00:00"
        val totalSeconds = ms / 1000L
        val hours = totalSeconds / 3600L
        val minutes = (totalSeconds % 3600L) / 60L
        val seconds = totalSeconds % 60L
        return if (hours > 0L) {
            String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.US, "%02d:%02d", minutes, seconds)
        }
    }

    @Composable
    fun RenderBottomPlayerControls(
        boxScope: BoxScope,
        isMovieOrVod: Boolean,
        isFullscreen: Boolean,
        playerManager: Any?,
        currentPositionState: State<Long>,
        durationState: State<Long>,
        isScrubbingState: MutableState<Boolean>,
        scrubPositionState: MutableFloatState,
        onToggleFullscreen: () -> Unit
    ) {
        val effectiveIsVod = isMovieOrVod || isManagerPlayingVod(playerManager)
        val currentPos = currentPositionState.value.coerceAtLeast(0L)
        val totalDuration = durationState.value.coerceAtLeast(0L)
        val isScrubbing = isScrubbingState.value
        val activeSpeed by currentSpeed.collectAsState()
        val liveFractionState = remember { mutableFloatStateOf(1f) }

        DisposableEffect(liveFractionState) {
            registerLiveFractionWithHelper(liveFractionState)
            onDispose {
                if (activeLiveFractionRef === liveFractionState) {
                    activeLiveFractionRef = null
                }
            }
        }

        val liveTimeline = computeLiveTimelineState(
            isScrubbing = isScrubbing,
            currentPositionMs = currentPos,
            durationMs = totalDuration,
            manualFraction = liveFractionState.floatValue
        )

        LaunchedEffect(effectiveIsVod, isScrubbing, totalDuration, liveTimeline.progress) {
            if (!effectiveIsVod && !isScrubbing && totalDuration > LIVE_WINDOW_MIN_DURATION_MS) {
                liveFractionState.floatValue = liveTimeline.progress
            }
        }

        val displayProgress = computeProgressFraction(
            isVod = effectiveIsVod,
            isScrubbing = isScrubbing,
            scrubPosition = scrubPositionState.floatValue,
            currentPositionMs = currentPos,
            durationMs = totalDuration,
            liveFraction = liveTimeline.progress
        )

        val displayTimeMs = if (isScrubbing && totalDuration > 0L) {
            scrubPositionState.floatValue.toLong().coerceIn(0L, totalDuration)
        } else {
            currentPos
        }

        val timerText = if (effectiveIsVod) {
            if (totalDuration > 0L) {
                "${formatPlayerDuration(displayTimeMs)} / ${formatPlayerDuration(totalDuration)}"
            } else {
                ""
            }
        } else {
            liveTimeline.label
        }

        val horizontalPad = if (isFullscreen) 18.dp else 10.dp
        val bottomPad = if (isFullscreen) 14.dp else 6.dp

        with(boxScope) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(start = horizontalPad, end = horizontalPad, bottom = bottomPad),
                verticalArrangement = Arrangement.spacedBy(if (isFullscreen) 6.dp else 4.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(if (isFullscreen) 8.dp else 6.dp)
                    ) {
                        StreamTypeBadge(
                            isVod = effectiveIsVod,
                            isFullscreen = isFullscreen,
                            isBehindLive = !effectiveIsVod && liveTimeline.behindSeconds > 0,
                            onJumpToLive = {
                                liveFractionState.floatValue = 1f
                                seekToLiveEdge(playerManager)
                            }
                        )
                        if (timerText.isNotEmpty()) {
                            Text(
                                text = AppUiTranslator.t(timerText),
                                color = Color.White.copy(alpha = 0.92f),
                                fontSize = if (isFullscreen) 12.sp else 10.5.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                modifier = Modifier.testTag("player_live_or_vod_timer")
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(if (isFullscreen) 8.dp else 6.dp)
                    ) {
                        if (effectiveIsVod) {
                            PlaybackSpeedSelectorButton(
                                activeSpeed = activeSpeed,
                                isFullscreen = isFullscreen,
                                onSelectSpeed = { selected ->
                                    applyPlaybackSpeed(playerManager, selected)
                                }
                            )
                        }
                        FullscreenToggleButton(
                            isFullscreen = isFullscreen,
                            onClick = onToggleFullscreen
                        )
                    }
                }

                SeekProgressBar(
                    isVod = effectiveIsVod,
                    isScrubbing = isScrubbing,
                    progress = displayProgress,
                    durationMs = totalDuration,
                    onStartScrub = { fraction ->
                        isScrubbingState.value = true
                        if (effectiveIsVod && totalDuration > 0L) {
                            scrubPositionState.floatValue = fraction * totalDuration.toFloat()
                        } else {
                            liveFractionState.floatValue = fraction
                        }
                    },
                    onUpdateScrub = { fraction ->
                        if (effectiveIsVod && totalDuration > 0L) {
                            scrubPositionState.floatValue = fraction * totalDuration.toFloat()
                        } else {
                            liveFractionState.floatValue = fraction
                        }
                    },
                    onFinishScrub = { fraction ->
                        if (effectiveIsVod && totalDuration > 0L) {
                            val targetMs = (fraction * totalDuration.toFloat()).toLong().coerceIn(0L, totalDuration)
                            scrubPositionState.floatValue = targetMs.toFloat()
                            seekPlayer(playerManager, targetMs)
                            isScrubbingState.value = false
                        } else {
                            isScrubbingState.value = false
                            if (fraction >= LIVE_EDGE_SNAP_FRACTION) {
                                liveFractionState.floatValue = 1f
                                seekToLiveEdge(playerManager)
                            } else {
                                liveFractionState.floatValue = fraction
                                if (totalDuration > LIVE_WINDOW_MIN_DURATION_MS) {
                                    val targetMs = (fraction * totalDuration.toFloat()).toLong()
                                        .coerceIn(0L, totalDuration)
                                    seekPlayer(playerManager, targetMs)
                                }
                            }
                        }
                    },
                    onCancelScrub = {
                        isScrubbingState.value = false
                    }
                )
            }
        }
    }

    private fun computeProgressFraction(
        isVod: Boolean,
        isScrubbing: Boolean,
        scrubPosition: Float,
        currentPositionMs: Long,
        durationMs: Long,
        liveFraction: Float
    ): Float {
        if (!isVod) return liveFraction.coerceIn(0f, 1f)
        if (durationMs <= 0L) return 0f
        val pos = if (isScrubbing) scrubPosition else currentPositionMs.toFloat()
        return (pos / durationMs.toFloat()).coerceIn(0f, 1f)
    }

    @Composable
    private fun StreamTypeBadge(
        isVod: Boolean,
        isFullscreen: Boolean,
        isBehindLive: Boolean,
        onJumpToLive: () -> Unit
    ) {
        val badgeContent: @Composable () -> Unit = {
            Row(
                modifier = Modifier.padding(
                    horizontal = if (isFullscreen) 8.dp else 6.dp,
                    vertical = if (isFullscreen) 4.dp else 2.5.dp
                ),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(if (isFullscreen) 6.dp else 5.dp)
                        .background(
                            color = if (isVod) Color(0xFF38BDF8) else Color(0xFFE50914),
                            shape = CircleShape
                        )
                )
                Text(
                    text = AppUiTranslator.t(if (isVod) "VOD Movie" else "Live Stream"),
                    color = Color.White,
                    fontSize = if (isFullscreen) 11.sp else 9.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )
            }
        }

        if (isBehindLive) {
            Surface(
                onClick = onJumpToLive,
                color = Color.Black.copy(alpha = 0.58f),
                shape = RoundedCornerShape(6.dp),
                border = BorderStroke(0.75.dp, Color.White.copy(alpha = 0.28f)),
                content = badgeContent
            )
        } else {
            Surface(
                color = Color.Black.copy(alpha = 0.58f),
                shape = RoundedCornerShape(6.dp),
                border = BorderStroke(0.75.dp, Color.White.copy(alpha = 0.22f)),
                content = badgeContent
            )
        }
    }

    @Composable
    private fun PlaybackSpeedSelectorButton(
        activeSpeed: Float,
        isFullscreen: Boolean,
        onSelectSpeed: (Float) -> Unit
    ) {
        var expanded by remember { mutableStateOf(false) }
        val isCustomSpeed = activeSpeed != DEFAULT_SPEED
        val containerColor = if (isCustomSpeed) {
            Color(0xFFE50914).copy(alpha = 0.88f)
        } else {
            Color.Black.copy(alpha = 0.58f)
        }
        val borderColor = if (isCustomSpeed) {
            Color.White.copy(alpha = 0.45f)
        } else {
            Color.White.copy(alpha = 0.25f)
        }
        val pillHeight = if (isFullscreen) 32.dp else 28.dp

        Box {
            val speedContentDesc = AppUiTranslator.t("Playback speed ${formatSpeedLabel(activeSpeed)}")
            Surface(
                onClick = { expanded = true },
                modifier = Modifier
                    .height(pillHeight)
                    .testTag("vod_playback_speed_button")
                    .semantics {
                        contentDescription = speedContentDesc
                    },
                color = containerColor,
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(0.85.dp, borderColor)
            ) {
                Box(
                    modifier = Modifier.padding(
                        horizontal = if (isFullscreen) 10.dp else 8.dp
                    ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = formatSpeedLabel(activeSpeed),
                        color = Color.White,
                        fontSize = if (isFullscreen) 12.sp else 10.5.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                }
            }

            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier
                    .background(Color(0xF218181B))
                    .testTag("vod_playback_speed_menu")
            ) {
                for (speedOption in SPEED_OPTIONS) {
                    val isSelected = speedOption == activeSpeed
                    DropdownMenuItem(
                        modifier = Modifier.testTag("vod_speed_option_${formatSpeedLabel(speedOption)}"),
                        text = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = if (speedOption == DEFAULT_SPEED) {
                                        AppUiTranslator.t("1x (Normal)")
                                    } else {
                                        formatSpeedLabel(speedOption)
                                    },
                                    color = if (isSelected) Color.White else Color(0xFFD4D4D8),
                                    fontSize = 13.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                )
                                if (isSelected) {
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Icon(
                                        imageVector = Icons.Filled.Check,
                                        contentDescription = AppUiTranslator.t("Selected speed"),
                                        tint = Color(0xFFE50914),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        },
                        onClick = {
                            expanded = false
                            onSelectSpeed(speedOption)
                        }
                    )
                }
            }
        }
    }

    @Composable
    private fun FullscreenToggleButton(
        isFullscreen: Boolean,
        onClick: () -> Unit
    ) {
        val buttonSize = if (isFullscreen) 32.dp else 28.dp
        val iconSize = if (isFullscreen) 19.dp else 17.dp
        Surface(
            onClick = onClick,
            modifier = Modifier
                .size(buttonSize)
                .testTag("player_bottom_fullscreen_button"),
            color = Color.Black.copy(alpha = 0.58f),
            shape = RoundedCornerShape(8.dp),
            border = BorderStroke(0.85.dp, Color.White.copy(alpha = 0.25f))
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = if (isFullscreen) Icons.Filled.FullscreenExit else Icons.Filled.Fullscreen,
                    contentDescription = AppUiTranslator.t(if (isFullscreen) "Exit Fullscreen" else "Enter Fullscreen"),
                    tint = Color.White,
                    modifier = Modifier.size(iconSize)
                )
            }
        }
    }

    @Composable
    private fun SeekProgressBar(
        isVod: Boolean,
        isScrubbing: Boolean,
        progress: Float,
        durationMs: Long,
        onStartScrub: (Float) -> Unit,
        onUpdateScrub: (Float) -> Unit,
        onFinishScrub: (Float) -> Unit,
        onCancelScrub: () -> Unit
    ) {
        var dragFraction by remember { mutableFloatStateOf(progress) }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(16.dp)
                .testTag("movie_progress_slider")
                .pointerInput(isVod, durationMs) {
                    detectTapGestures { offset ->
                        val width = size.width.toFloat().coerceAtLeast(1f)
                        val fraction = (offset.x / width).coerceIn(0f, 1f)
                        onFinishScrub(fraction)
                    }
                }
                .pointerInput(isVod, durationMs) {
                    detectHorizontalDragGestures(
                        onDragStart = { offset ->
                            val width = size.width.toFloat().coerceAtLeast(1f)
                            dragFraction = (offset.x / width).coerceIn(0f, 1f)
                            onStartScrub(dragFraction)
                        },
                        onHorizontalDrag = { change, _ ->
                            val width = size.width.toFloat().coerceAtLeast(1f)
                            dragFraction = (change.position.x / width).coerceIn(0f, 1f)
                            onUpdateScrub(dragFraction)
                        },
                        onDragEnd = {
                            onFinishScrub(dragFraction)
                        },
                        onDragCancel = {
                            onCancelScrub()
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(13.dp)
            ) {
                val trackHeight = if (isScrubbing) 4.dp.toPx() else 3.dp.toPx()
                val centerY = size.height / 2f
                val corner = CornerRadius(trackHeight / 2f, trackHeight / 2f)

                drawRoundRect(
                    color = Color.White.copy(alpha = 0.28f),
                    topLeft = Offset(0f, centerY - trackHeight / 2f),
                    size = Size(size.width, trackHeight),
                    cornerRadius = corner
                )

                val filledWidth = (size.width * progress.coerceIn(0f, 1f)).coerceIn(0f, size.width)
                drawRoundRect(
                    color = Color(0xFFE50914),
                    topLeft = Offset(0f, centerY - trackHeight / 2f),
                    size = Size(filledWidth, trackHeight),
                    cornerRadius = corner
                )

                val thumbRadius = if (isScrubbing) 6.dp.toPx() else 4.5.dp.toPx()
                drawCircle(
                    color = Color(0xFFE50914),
                    radius = thumbRadius,
                    center = Offset(filledWidth.coerceIn(thumbRadius, (size.width - thumbRadius).coerceAtLeast(thumbRadius)), centerY)
                )
            }
        }
    }
}
