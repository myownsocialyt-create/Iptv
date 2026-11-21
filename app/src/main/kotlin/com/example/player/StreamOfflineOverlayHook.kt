package com.example.player

import android.util.Log
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.premium.AppUiTranslator
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.util.concurrent.ConcurrentHashMap

/**
 * Responsive replacement for the "Stream Offline" overlay inside IptvVideoPlayer so the error
 * message and action buttons never clip in compact 16:9 portrait or mobile landscape layouts.
 */
object StreamOfflineOverlayHook {
    private const val TAG = "StreamOfflineOverlayHook"

    private val fieldCache = ConcurrentHashMap<String, Field>()
    private val methodCache = ConcurrentHashMap<String, Method>()

    @Suppress("UNCHECKED_CAST")
    @Composable
    fun RenderStreamOfflineCard(target: Any) {
        val strings = getField(target, "\$strings")
        val playbackStateDelegate = getField(target, "\$playbackState\$delegate") as? State<*>
        val hasNext = (getField(target, "\$hasNext") as? Boolean) ?: false
        val onChannelSelect = getField(target, "\$onChannelSelect") as? Function1<Any?, Any?>
        val allChannels = getField(target, "\$allChannels") as? List<*>
        val currentIndex = (getField(target, "\$currentIndex") as? Int) ?: -1
        val playerManager = getField(target, "\$playerManager")

        val rawTitle = (invokeNoArg(strings, "getStreamOffline") as? String)
            ?.takeIf { it.isNotBlank() } ?: "Stream Offline"
        val rawRetry = (invokeNoArg(strings, "getRetry") as? String)
            ?.takeIf { it.isNotBlank() } ?: "Retry"
        val rawNext = (invokeNoArg(strings, "getNextChannel") as? String)
            ?.takeIf { it.isNotBlank() } ?: "Next"

        val stateObj = playbackStateDelegate?.value
        val rawErrorMessage = (invokeNoArg(stateObj, "getMessage") as? String)
            ?.takeIf { it.isNotBlank() } ?: "Unable to load stream. Try another server or channel."

        val titleText = AppUiTranslator.t(rawTitle)
        val retryText = AppUiTranslator.t(rawRetry)
        val nextText = AppUiTranslator.t(rawNext)
        val errorMessage = AppUiTranslator.t(rawErrorMessage)

        val ottRed = Color(0xFFE50914)
        val canSelectNext = hasNext && allChannels != null && currentIndex + 1 in allChannels.indices

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("stream_offline_overlay")
        ) {
            val isCompact = maxHeight < 195.dp
            val scrollState = rememberScrollState()

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(scrollState)
                    .padding(
                        horizontal = if (isCompact) 14.dp else 22.dp,
                        vertical = if (isCompact) 10.dp else 18.dp
                    ),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                if (isCompact) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Surface(
                            modifier = Modifier.size(30.dp),
                            shape = CircleShape,
                            color = ottRed.copy(alpha = 0.22f)
                        ) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.WifiOff,
                                    contentDescription = titleText,
                                    tint = ottRed,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = titleText,
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                } else {
                    Surface(
                        modifier = Modifier.size(46.dp),
                        shape = CircleShape,
                        color = ottRed.copy(alpha = 0.22f)
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.WifiOff,
                                contentDescription = titleText,
                                tint = ottRed,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = titleText,
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                }

                Text(
                    text = errorMessage,
                    color = Color.White.copy(alpha = 0.78f),
                    fontSize = if (isCompact) 11.5.sp else 12.5.sp,
                    lineHeight = if (isCompact) 15.sp else 17.sp,
                    textAlign = TextAlign.Center,
                    maxLines = if (isCompact) 3 else 4,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(if (isCompact) 10.dp else 14.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (canSelectNext) {
                        Button(
                            onClick = {
                                runCatching {
                                    val nextItem = allChannels!![currentIndex + 1]
                                    onChannelSelect?.invoke(nextItem)
                                }
                            },
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = ottRed,
                                contentColor = Color.White
                            ),
                            contentPadding = PaddingValues(
                                horizontal = if (isCompact) 10.dp else 14.dp,
                                vertical = 0.dp
                            ),
                            modifier = Modifier
                                .height(if (isCompact) 32.dp else 36.dp)
                                .testTag("stream_offline_next_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.SkipNext,
                                contentDescription = null,
                                modifier = Modifier.size(if (isCompact) 14.dp else 16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = nextText,
                                fontSize = if (isCompact) 11.5.sp else 12.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }

                    OutlinedButton(
                        onClick = {
                            runCatching { invokeNoArg(playerManager, "retry") }
                        },
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.35f)),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = Color.White
                        ),
                        contentPadding = PaddingValues(
                            horizontal = if (isCompact) 12.dp else 16.dp,
                            vertical = 0.dp
                        ),
                        modifier = Modifier
                            .height(if (isCompact) 32.dp else 36.dp)
                            .testTag("stream_offline_retry_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(if (isCompact) 14.dp else 16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = retryText,
                            fontSize = if (isCompact) 11.5.sp else 12.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }
        }
    }

    private fun getField(target: Any?, name: String): Any? {
        if (target == null) return null
        val cls = target.javaClass
        val key = "${cls.name}#$name"
        return try {
            val field = fieldCache.getOrPut(key) {
                var c: Class<*>? = cls
                var found: Field? = null
                while (c != null && found == null) {
                    try {
                        found = c.getDeclaredField(name)
                    } catch (_: NoSuchFieldException) {
                        c = c.superclass
                    }
                }
                (found ?: throw NoSuchFieldException(key)).apply { isAccessible = true }
            }
            field.get(target)
        } catch (t: Throwable) {
            Log.w(TAG, "getField failed for $key", t)
            null
        }
    }

    private fun invokeNoArg(target: Any?, name: String): Any? {
        if (target == null) return null
        val cls = target.javaClass
        val key = "${cls.name}#$name"
        return try {
            val method = methodCache.getOrPut(key) {
                cls.getMethod(name).apply { isAccessible = true }
            }
            method.invoke(target)
        } catch (_: Throwable) {
            null
        }
    }
}
