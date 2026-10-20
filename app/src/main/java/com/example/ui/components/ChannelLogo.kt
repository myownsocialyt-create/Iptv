package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImagePainter
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.size.Precision
import com.example.data.model.Channel

/**
 * High-speed, resilient IPTV Channel Logo component.
 * - Immediately renders a vibrant, crisp channel monogram badge while loading (zero blank delay on slow networks)
 * - Downsamples remote logos to thumbnail size (160x160) with aggressive disk & memory caching
 * - Automatically retries with a verified fallback logo URL if the M3U logo URL is dead or blocked
 * - Guarantees 100% of channels display a polished visual logo even offline or with broken M3U links
 */
@Composable
fun ChannelLogo(
    channel: Channel,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit,
    imagePadding: Dp = 4.dp,
    fontSize: TextUnit = 13.sp,
    showCategoryIcon: Boolean = false,
    cornerRadius: Dp = 10.dp
) {
    val context = LocalContext.current

    val primaryUrl = remember(channel.name, channel.logoUrl, channel.category) {
        ChannelLogoResolver.resolveOptimalLogoUrl(channel.name, channel.logoUrl, channel.category)
    }
    val secondaryFallbackUrl = remember(channel.name, channel.category) {
        ChannelLogoResolver.getFallbackLogoUrl(channel.name, channel.category)
    }

    var useSecondaryFallback by remember(channel.id, channel.logoUrl) {
        mutableStateOf(false)
    }

    val activeUrl = if (useSecondaryFallback && secondaryFallbackUrl != null && secondaryFallbackUrl != primaryUrl) {
        secondaryFallbackUrl
    } else {
        primaryUrl
    }

    if (activeUrl.isNullOrBlank()) {
        ChannelMonogramBadge(
            channel = channel,
            fontSize = fontSize,
            showCategoryIcon = showCategoryIcon,
            cornerRadius = cornerRadius,
            modifier = modifier
        )
        return
    }

    val imageRequest = remember(activeUrl, context) {
        ImageRequest.Builder(context)
            .data(activeUrl)
            .size(160, 160)
            .precision(Precision.INEXACT)
            .crossfade(180)
            .memoryCacheKey(activeUrl)
            .diskCacheKey(activeUrl)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .networkCachePolicy(CachePolicy.ENABLED)
            .allowHardware(true)
            .build()
    }

    SubcomposeAsyncImage(
        model = imageRequest,
        contentDescription = channel.name,
        contentScale = contentScale,
        modifier = modifier.clip(RoundedCornerShape(cornerRadius))
    ) {
        when (painter.state) {
            is AsyncImagePainter.State.Success -> {
                SubcomposeAsyncImageContent(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(imagePadding)
                )
            }
            is AsyncImagePainter.State.Error -> {
                if (!useSecondaryFallback && secondaryFallbackUrl != null && secondaryFallbackUrl != primaryUrl) {
                    useSecondaryFallback = true
                }
                ChannelMonogramBadge(
                    channel = channel,
                    fontSize = fontSize,
                    showCategoryIcon = showCategoryIcon,
                    cornerRadius = cornerRadius,
                    modifier = Modifier.fillMaxSize()
                )
            }
            else -> {
                // Instant monogram badge during loading so slow internet never shows a blank box
                ChannelMonogramBadge(
                    channel = channel,
                    fontSize = fontSize,
                    showCategoryIcon = showCategoryIcon,
                    cornerRadius = cornerRadius,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

@Composable
fun ChannelMonogramBadge(
    channel: Channel,
    fontSize: TextUnit = 13.sp,
    showCategoryIcon: Boolean = false,
    cornerRadius: Dp = 10.dp,
    modifier: Modifier = Modifier
) {
    val monogram = remember(channel.name) {
        ChannelLogoResolver.getChannelMonogram(channel.name)
    }
    val gradientColors = remember(channel.name, channel.category) {
        ChannelLogoResolver.getBadgeGradientColors(channel)
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .clip(RoundedCornerShape(cornerRadius))
            .background(Brush.linearGradient(gradientColors))
            .padding(4.dp)
    ) {
        if (showCategoryIcon) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = if (channel.isMovieOrVod) Icons.Default.Movie else Icons.Default.LiveTv,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.85f),
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = monogram,
                    color = Color.White,
                    fontSize = fontSize,
                    fontWeight = FontWeight.Black,
                    textAlign = TextAlign.Center,
                    maxLines = 1
                )
            }
        } else {
            Text(
                text = monogram,
                color = Color.White,
                fontSize = fontSize,
                fontWeight = FontWeight.Black,
                textAlign = TextAlign.Center,
                maxLines = 1
            )
        }
    }
}
