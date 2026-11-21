package com.example.premium

import android.content.Context
import android.content.res.Configuration
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.size.Precision
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

object HomeChannelCardHook {
    private const val TAG = "HomeChannelCardHook"
    private val OTT_PRIMARY = Color(0xFFE50914)
    private val OTT_GOLD = Color(0xFFFFB800)

    private val fieldCache = ConcurrentHashMap<String, Field>()
    private val methodCache = ConcurrentHashMap<String, Method>()

    @Volatile
    private var pendingCardTarget: Any? = null

    @Volatile
    private var pendingMiniHighlightTarget: Any? = null

    fun setPendingCardTarget(target: Any) {
        pendingMiniHighlightTarget = null
        pendingCardTarget = target
    }

    fun setPendingMiniHighlightTarget(target: Any) {
        pendingCardTarget = null
        pendingMiniHighlightTarget = target
    }

    @Composable
    fun tryRenderPendingHomeCard(): Boolean {
        val miniTarget = pendingMiniHighlightTarget
        if (miniTarget != null) {
            pendingMiniHighlightTarget = null
            key("home_mini_highlight_card") {
                RenderMiniHighlightFromLambda(miniTarget)
            }
            return true
        }
        val target = pendingCardTarget ?: return false
        pendingCardTarget = null
        key("home_channel_card_compact") {
            RenderCardFromLambda(target)
        }
        return true
    }

    internal fun isMovieOrVodChannel(
        isMovieOrVodFlag: Boolean,
        id: String,
        streamUrl: String,
        category: String
    ): Boolean {
        if (isMovieOrVodFlag) return true

        val lowerId = id.trim().lowercase(Locale.ROOT)
        if (lowerId.startsWith("vod_") ||
            lowerId.startsWith("movie_") ||
            lowerId.startsWith("demo_vod") ||
            lowerId.startsWith("series_")
        ) {
            return true
        }

        val lowerUrl = streamUrl.trim().lowercase(Locale.ROOT)
        if (lowerUrl.contains("/movie/") ||
            lowerUrl.contains("/vod/") ||
            lowerUrl.contains("/series/")
        ) {
            return true
        }

        val urlPath = lowerUrl.substringBefore('?').substringBefore('#')
        if (urlPath.endsWith(".mp4") ||
            urlPath.endsWith(".mkv") ||
            urlPath.endsWith(".avi") ||
            urlPath.endsWith(".mov") ||
            urlPath.endsWith(".webm")
        ) {
            return true
        }

        val lowerCat = category.trim().lowercase(Locale.ROOT)
        if (lowerCat.contains("vod")) {
            return true
        }

        return false
    }

    internal fun resolveBadgeLabel(isMovieOrVod: Boolean): String {
        return if (isMovieOrVod) "MOVIE" else "• LIVE"
    }

    private data class CardPalette(
        val isDark: Boolean,
        val surfaceVariant: Color,
        val textPrimary: Color,
        val textSecondary: Color,
        val textMuted: Color,
        val playBg: Color
    )

    private fun extractCardPalette(appColors: Any?, context: Context): CardPalette {
        val isDark = (invokeNoArg(appColors, "isDark") as? Boolean)
            ?: ((context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES)

        return if (isDark) {
            CardPalette(
                isDark = true,
                surfaceVariant = extractColorField(appColors, "surfaceVariant", Color(0xFF171717)),
                textPrimary = extractColorField(appColors, "textPrimary", Color(0xFFFFFFFF)),
                textSecondary = extractColorField(appColors, "textSecondary", Color(0xFFA1A1AA)),
                textMuted = extractColorField(appColors, "textMuted", Color(0xFF71717A)),
                playBg = Color(0xFF2E1218)
            )
        } else {
            CardPalette(
                isDark = false,
                surfaceVariant = extractColorField(appColors, "surfaceVariant", Color(0xFFF1F5F9)),
                textPrimary = extractColorField(appColors, "textPrimary", Color(0xFF0F172A)),
                textSecondary = extractColorField(appColors, "textSecondary", Color(0xFF64748B)),
                textMuted = extractColorField(appColors, "textMuted", Color(0xFF94A3B8)),
                playBg = Color(0xFFFFEEEE)
            )
        }
    }

    @Composable
    private fun RenderMiniHighlightFromLambda(target: Any) {
        val tag = (getField(target, "\$tag") as? String).orEmpty()
        val title = (getField(target, "\$title") as? String).orEmpty()
        val rawColor = getField(target, "\$tagColor") as? Long
        val tagColor = if (rawColor != null) Color(rawColor.toULong()) else OTT_PRIMARY

        val tightTextStyle = remember {
            TextStyle(
                platformStyle = PlatformTextStyle(includeFontPadding = false)
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp, vertical = 7.dp)
                .testTag("home_mini_highlight_content"),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Surface(
                color = tagColor,
                shape = RoundedCornerShape(4.dp)
            ) {
                Text(
                    text = AppUiTranslator.t(tag),
                    color = Color.White,
                    fontSize = 9.sp,
                    lineHeight = 11.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    softWrap = false,
                    style = tightTextStyle,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.5.dp)
                )
            }

            Text(
                text = title,
                color = Color.White,
                fontSize = 10.5.sp,
                lineHeight = 13.sp,
                letterSpacing = (-0.1).sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                style = tightTextStyle
            )
        }
    }

    @Suppress("UNCHECKED_CAST")
    @Composable
    private fun RenderCardFromLambda(target: Any) {
        val context = LocalContext.current
        val channel = getField(target, "\$channel") ?: return
        val appColors = getField(target, "\$appColors")
        val onToggleFavorite = getField(target, "\$onToggleFavorite") as? Function0<Any?>
        val onPlay = getField(target, "\$onPlay") as? Function0<Any?>

        val id = (invokeNoArg(channel, "getId") as? String).orEmpty()
        val name = (invokeNoArg(channel, "getName") as? String).orEmpty()
        val logoUrl = (invokeNoArg(channel, "getLogoUrl") as? String).orEmpty()
        val category = (invokeNoArg(channel, "getCategory") as? String).orEmpty()
        val streamUrl = (invokeNoArg(channel, "getStreamUrl") as? String).orEmpty()
        val isFavorite = (invokeNoArg(channel, "isFavorite") as? Boolean) ?: false
        val isMovieOrVodFlag = (invokeNoArg(channel, "isMovieOrVod") as? Boolean) ?: false

        val isVod = remember(isMovieOrVodFlag, id, streamUrl, category) {
            isMovieOrVodChannel(
                isMovieOrVodFlag = isMovieOrVodFlag,
                id = id,
                streamUrl = streamUrl,
                category = category
            )
        }

        val resolvedLogoUrl = remember(channel, name, logoUrl, category, isVod) {
            resolveBestImageUrl(
                channel = channel,
                name = name,
                rawLogoUrl = logoUrl,
                category = category,
                isVod = isVod
            )
        }

        val monogram = remember(name) {
            resolveMonogram(name)
        }

        val palette = extractCardPalette(appColors, context)

        HomeChannelCardContent(
            channelName = name,
            category = category,
            logoUrl = resolvedLogoUrl,
            monogram = monogram,
            isFavorite = isFavorite,
            isVod = isVod,
            palette = palette,
            context = context,
            onPlay = {
                try {
                    onPlay?.invoke()
                } catch (t: Throwable) {
                    Log.w(TAG, "onPlay failed", t)
                }
            },
            onToggleFavorite = {
                try {
                    onToggleFavorite?.invoke()
                } catch (t: Throwable) {
                    Log.w(TAG, "onToggleFavorite failed", t)
                }
            }
        )
    }

    @Composable
    private fun HomeChannelCardContent(
        channelName: String,
        category: String,
        logoUrl: String,
        monogram: String,
        isFavorite: Boolean,
        isVod: Boolean,
        palette: CardPalette,
        context: Context,
        onPlay: () -> Unit,
        onToggleFavorite: () -> Unit
    ) {
        val badgeText = AppUiTranslator.t(resolveBadgeLabel(isVod))
        val displayCategory = AppUiTranslator.t(category.ifBlank { if (isVod) "VOD" else "Live" })
        val tightTextStyle = remember {
            TextStyle(
                platformStyle = PlatformTextStyle(includeFontPadding = false)
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp)
                .testTag("home_channel_card_content")
        ) {
            // Row 1: Compact status badge (LIVE / MOVIE) + Favorite Heart
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = OTT_PRIMARY,
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier.testTag("home_card_status_badge")
                ) {
                    Box(
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = badgeText,
                            color = Color.White,
                            fontSize = 8.sp,
                            lineHeight = 10.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 0.25.sp,
                            maxLines = 1,
                            softWrap = false,
                            style = tightTextStyle
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onToggleFavorite)
                        .testTag("home_card_favorite_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                        contentDescription = AppUiTranslator.t(if (isFavorite) "Remove from favorites" else "Add to favorites"),
                        tint = if (isFavorite) OTT_GOLD else palette.textMuted,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(5.dp))

            // Row 2: Slightly shorter Logo / Poster preview box
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(palette.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                if (logoUrl.isNotBlank()) {
                    val request = remember(logoUrl, context) {
                        ImageRequest.Builder(context)
                            .data(logoUrl)
                            .size(256, 256)
                            .precision(Precision.INEXACT)
                            .crossfade(150)
                            .memoryCacheKey(logoUrl)
                            .diskCacheKey(logoUrl)
                            .memoryCachePolicy(CachePolicy.ENABLED)
                            .diskCachePolicy(CachePolicy.ENABLED)
                            .networkCachePolicy(CachePolicy.ENABLED)
                            .allowHardware(true)
                            .build()
                    }
                    SubcomposeAsyncImage(
                        model = request,
                        contentDescription = channelName,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                        contentScale = ContentScale.Fit,
                        error = {
                            CompactMonogramFallback(
                                monogram = monogram,
                                seed = channelName
                            )
                        },
                        loading = {
                            CompactMonogramFallback(
                                monogram = monogram,
                                seed = channelName
                            )
                        }
                    )
                } else {
                    CompactMonogramFallback(
                        monogram = monogram,
                        seed = channelName
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Row 3: Channel / Movie Title
            Text(
                text = channelName,
                color = palette.textPrimary,
                fontSize = 13.sp,
                lineHeight = 16.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = tightTextStyle
            )

            Spacer(modifier = Modifier.height(5.dp))

            // Row 4: Tighter Category grey box (same 10.sp text) + Play circle button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = palette.surfaceVariant,
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .padding(end = 6.dp)
                        .testTag("home_card_category_chip")
                ) {
                    Text(
                        text = displayCategory,
                        color = palette.textSecondary,
                        fontSize = 10.sp,
                        lineHeight = 12.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = tightTextStyle,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }

                Surface(
                    shape = CircleShape,
                    color = palette.playBg,
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onPlay)
                        .testTag("home_card_play_button")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Filled.PlayArrow,
                            contentDescription = "${AppUiTranslator.t("Play")} $channelName",
                            tint = OTT_PRIMARY,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
        }
    }

    @Composable
    private fun CompactMonogramFallback(
        monogram: String,
        seed: String
    ) {
        val gradient = remember(seed) {
            val hash = seed.hashCode().let { if (it < 0) -it else it }
            val pairs = listOf(
                Color(0xFFE50914) to Color(0xFF991B1B),
                Color(0xFF2563EB) to Color(0xFF1E3A8A),
                Color(0xFF7C3AED) to Color(0xFF4C1D95),
                Color(0xFF059669) to Color(0xFF065F46),
                Color(0xFFD97706) to Color(0xFF92400E),
                Color(0xFF0284C7) to Color(0xFF075985)
            )
            val (c1, c2) = pairs[hash % pairs.size]
            Brush.linearGradient(listOf(c1, c2))
        }
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(gradient),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = monogram,
                color = Color.White,
                fontSize = 12.sp,
                lineHeight = 14.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
                style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))
            )
        }
    }

    private fun resolveBestImageUrl(
        channel: Any,
        name: String,
        rawLogoUrl: String,
        category: String,
        isVod: Boolean
    ): String {
        if (isVod) {
            try {
                val posterCls = Class.forName("com.example.ui.screens.XtreamPosterResolver")
                val instance = posterCls.getField("INSTANCE").get(null)
                val method = posterCls.methods.firstOrNull {
                    it.name == "getPosterUrl" && it.parameterTypes.size == 1
                }
                val resolved = method?.invoke(instance, channel) as? String
                if (!resolved.isNullOrBlank()) return resolved.trim()
            } catch (_: Throwable) {
            }
        }

        try {
            val resolverCls = Class.forName("com.example.ui.components.ChannelLogoResolver")
            val instance = resolverCls.getField("INSTANCE").get(null)
            val method = resolverCls.getMethod(
                "resolvePrimaryLogo",
                String::class.java,
                String::class.java,
                String::class.java
            )
            val resolved = method.invoke(instance, name, rawLogoUrl, category) as? String
            if (!resolved.isNullOrBlank()) return resolved.trim()
        } catch (_: Throwable) {
        }

        return rawLogoUrl.trim()
    }

    private fun resolveMonogram(name: String): String {
        try {
            val resolverCls = Class.forName("com.example.ui.components.ChannelLogoResolver")
            val instance = resolverCls.getField("INSTANCE").get(null)
            val method = resolverCls.getMethod("getChannelMonogram", String::class.java)
            val m = method.invoke(instance, name) as? String
            if (!m.isNullOrBlank()) return m
        } catch (_: Throwable) {
        }
        val words = name.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        return when {
            words.size >= 2 -> "${words[0].first().uppercaseChar()}${words[1].first().uppercaseChar()}"
            words.size == 1 && words[0].length >= 2 -> words[0].take(2).uppercase(Locale.ROOT)
            words.size == 1 -> words[0].uppercase(Locale.ROOT)
            else -> "TV"
        }
    }

    private fun extractColorField(appColors: Any?, fieldName: String, fallback: Color): Color {
        if (appColors == null) return fallback
        return try {
            val raw = getField(appColors, fieldName) as? Long ?: return fallback
            Color(raw.toULong())
        } catch (_: Throwable) {
            fallback
        }
    }

    private fun getField(target: Any?, fieldName: String): Any? {
        if (target == null) return null
        val key = "${target.javaClass.name}#$fieldName"
        val cached = fieldCache[key]
        if (cached != null) {
            return try {
                cached.get(target)
            } catch (_: Throwable) {
                null
            }
        }
        var cls: Class<*>? = target.javaClass
        while (cls != null) {
            try {
                val f = cls.getDeclaredField(fieldName)
                f.isAccessible = true
                fieldCache[key] = f
                return f.get(target)
            } catch (_: NoSuchFieldException) {
                cls = cls.superclass
            } catch (_: Throwable) {
                return null
            }
        }
        return null
    }

    private fun invokeNoArg(target: Any?, methodName: String): Any? {
        if (target == null) return null
        val key = "${target.javaClass.name}#$methodName"
        val cached = methodCache[key]
        if (cached != null) {
            return try {
                cached.invoke(target)
            } catch (_: Throwable) {
                null
            }
        }
        return try {
            val m = target.javaClass.getMethod(methodName)
            m.isAccessible = true
            methodCache[key] = m
            m.invoke(target)
        } catch (_: Throwable) {
            null
        }
    }
}
