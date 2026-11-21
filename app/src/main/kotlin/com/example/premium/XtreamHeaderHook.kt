package com.example.premium

import android.util.Log
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * Renders the Xtream Content Screen top header row with a bounded, ellipsizing
 * active-account pill so long Xtream account names never push the "+ Add" button
 * off-screen.
 */
object XtreamHeaderHook {
    private const val TAG = "XtreamHeaderHook"
    private val fieldCache = ConcurrentHashMap<String, Field>()
    private val methodCache = ConcurrentHashMap<String, Method>()

    @Volatile
    private var pendingHeaderTarget: Any? = null

    fun setPendingHeaderTarget(target: Any) {
        pendingHeaderTarget = target
    }

    @Composable
    fun tryRenderPendingXtreamHeader(): Boolean {
        val target = pendingHeaderTarget ?: return false
        pendingHeaderTarget = null
        RenderXtreamHeader(target)
        return true
    }

    @Suppress("UNCHECKED_CAST")
    @Composable
    private fun RenderXtreamHeader(target: Any) {
        val onBackClick = getField(target, "\$onBackClick") as? Function0<Any?>
        val onAddProfileClick = getField(target, "\$onAddProfileClick") as? Function0<Any?>
        val activeAccount = getField(target, "\$activeAccount")
        val appColors = getField(target, "\$appColors")

        val accountName = (invokeNoArg(activeAccount, "getName") as? String)
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: "Xtream"

        val isDark = (invokeNoArg(appColors, "isDark") as? Boolean) ?: isSystemInDarkTheme()
        val ottPrimary = Color(0xFFE50914)
        val surfaceColor = readColorField(appColors, "getSurface-0d7_KjU")
            ?: if (isDark) Color(0xFF161619) else Color(0xFFFFFFFF)
        val borderColor = readColorField(appColors, "getBorder-0d7_KjU")
            ?: if (isDark) Color(0xFF2B2B30) else Color(0xFFE2E8F0)
        val textPrimary = readColorField(appColors, "getTextPrimary-0d7_KjU")
            ?: if (isDark) Color(0xFFFFFFFF) else Color(0xFF0F172A)

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = {
                    runCatching { onBackClick?.invoke() }
                },
                modifier = Modifier
                    .size(36.dp)
                    .testTag("xtream_header_back_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = AppUiTranslator.t("Back"),
                    tint = textPrimary,
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.width(6.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "X",
                    color = ottPrimary,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                    softWrap = false
                )
                Text(
                    text = "TREAM",
                    color = textPrimary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 2.sp,
                    maxLines = 1,
                    softWrap = false
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Bounded right-aligned profile pill: shrinks and ellipsizes cleanly for long names
            // so the "+ Add" button is always visible on the right.
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.CenterEnd
            ) {
                Surface(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .clickable {
                            runCatching { onBackClick?.invoke() }
                        }
                        .testTag("xtream_active_profile_chip"),
                    shape = RoundedCornerShape(20.dp),
                    color = surfaceColor,
                    border = BorderStroke(1.dp, borderColor)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            modifier = Modifier.size(22.dp),
                            shape = CircleShape,
                            color = ottPrimary
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = accountName.take(1).uppercase(Locale.ROOT),
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(6.dp))

                        Text(
                            text = accountName,
                            color = textPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            Button(
                onClick = {
                    runCatching { onAddProfileClick?.invoke() }
                },
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = ottPrimary,
                    contentColor = Color.White
                ),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                modifier = Modifier
                    .height(32.dp)
                    .testTag("xtream_add_profile_button")
            ) {
                Text(
                    text = AppUiTranslator.t("+ Add"),
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    softWrap = false
                )
            }
        }
    }

    private fun readColorField(obj: Any?, methodName: String): Color? {
        val raw = invokeNoArg(obj, methodName) as? Long ?: return null
        return try {
            Color(raw.toULong())
        } catch (_: Throwable) {
            null
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
