package com.example.ad

import android.content.Intent
import android.net.Uri
import android.view.ViewGroup
import android.widget.ImageView
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.example.data.model.Channel
import com.google.android.gms.ads.nativead.MediaView
import com.google.android.gms.ads.nativead.NativeAdView

/**
 * YouTube-style In-Stream Video & Poster Ad Overlay that renders directly over the video player.
 *
 * Solves:
 * 1. Zero Black Screen:
 *    - Instant HD poster art & rich cinematic backdrop is rendered immediately (0ms).
 *    - Video trailers smoothly play on top with transparent shutter, avoiding any black screen.
 * 2. Guaranteed channel-switch ad triggers:
 *    - Keyed on [channel.streamUrl] so every 2nd or 3rd switch triggers the ad.
 * 3. Authentic YouTube UI:
 *    - Top [Ad] badge & advertiser
 *    - Center headline & CTA
 *    - Bottom-left: "Sponsored · 0:XX ⓘ"
 *    - Bottom-right: "Skip in 5s" -> "Skip ad ▶|"
 *    - Bottom: Signature YouTube Yellow Progress Bar (#FFCC00)
 */
@OptIn(UnstableApi::class)
@Composable
fun InStreamVideoAdOverlay(
    channel: Channel,
    exoPlayer: ExoPlayer?,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isAdShowing by InStreamVideoAdManager.isAdShowing
    val currentCreative by InStreamVideoAdManager.currentCreative
    val nativeAd by InStreamVideoAdManager.currentNativeAd
    val headline by InStreamVideoAdManager.adHeadline
    val advertiser by InStreamVideoAdManager.adAdvertiser
    val canSkip by InStreamVideoAdManager.canSkip
    val skipCountdown by InStreamVideoAdManager.skipCountdown
    val secondsRemaining by InStreamVideoAdManager.secondsRemaining
    val progress by InStreamVideoAdManager.adProgress

    // Secondary dedicated ExoPlayer for video ad playback
    val adExoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            repeatMode = ExoPlayer.REPEAT_MODE_ONE
        }
    }

    // Channel switch listener: triggered whenever channel.streamUrl changes!
    LaunchedEffect(channel.streamUrl) {
        InStreamVideoAdManager.onChannelSwitch(
            context = context,
            channelUrl = channel.streamUrl,
            onAdStart = {
                try {
                    exoPlayer?.pause()
                    exoPlayer?.volume = 0f
                } catch (_: Exception) {}
            },
            onAdEnd = {
                try {
                    exoPlayer?.volume = 1f
                    exoPlayer?.play()
                } catch (_: Exception) {}
            }
        )
    }

    // Sync ad player state when an ad appears or disappears
    LaunchedEffect(isAdShowing, currentCreative) {
        if (isAdShowing) {
            try {
                exoPlayer?.pause()
                exoPlayer?.volume = 0f
            } catch (_: Exception) {}

            val videoUrl = currentCreative.videoUrl
            if (!videoUrl.isNullOrBlank()) {
                try {
                    adExoPlayer.stop()
                    adExoPlayer.setMediaItem(MediaItem.fromUri(Uri.parse(videoUrl)))
                    adExoPlayer.prepare()
                    adExoPlayer.play()
                } catch (_: Exception) {}
            } else {
                try {
                    adExoPlayer.pause()
                } catch (_: Exception) {}
            }
        } else {
            try {
                adExoPlayer.pause()
            } catch (_: Exception) {}
            try {
                exoPlayer?.volume = 1f
                exoPlayer?.play()
            } catch (_: Exception) {}
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            try {
                adExoPlayer.release()
            } catch (_: Exception) {}
            InStreamVideoAdManager.release()
        }
    }

    AnimatedVisibility(
        visible = isAdShowing,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF0F0F0F))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    try {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(currentCreative.destinationUrl))
                        context.startActivity(intent)
                    } catch (_: Exception) {}
                }
                .testTag("instream_video_ad_overlay")
        ) {
            // LAYER 1: Instant High-Definition Poster Art & Backdrop (NEVER BLACK SCREEN)
            AsyncImage(
                model = currentCreative.posterUrl,
                contentDescription = "Ad Poster Backdrop",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )

            // Cinematic dark gradient overlay
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color(0xCC000000),
                                Color(0x66000000),
                                Color(0xE6000000)
                            )
                        )
                    )
            )

            // YouTube botanical chalk doodle art overlay (for authentic YouTube aesthetic)
            AdLeafDoodleCanvas(
                modifier = Modifier.fillMaxSize()
            )

            // LAYER 2: Video Player (If creative has videoUrl, plays smoothly over poster)
            if (!currentCreative.videoUrl.isNullOrBlank()) {
                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                            useController = false
                            setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)
                            player = adExoPlayer
                        }
                    },
                    update = { playerView ->
                        if (playerView.player != adExoPlayer) {
                            playerView.player = adExoPlayer
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }

            // LAYER 3: AdMob Native MediaView (if loaded NativeAd has mediaContent)
            if (nativeAd?.mediaContent != null) {
                AndroidView(
                    factory = { ctx ->
                        NativeAdView(ctx).apply {
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                            val mediaView = MediaView(ctx).apply {
                                layoutParams = ViewGroup.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT
                                )
                                setImageScaleType(ImageView.ScaleType.CENTER_CROP)
                            }
                            addView(mediaView)
                            this.mediaView = mediaView
                            mediaView.mediaContent = nativeAd!!.mediaContent
                            setNativeAd(nativeAd!!)
                        }
                    },
                    update = { adView ->
                        nativeAd?.let {
                            adView.mediaView?.mediaContent = it.mediaContent
                            adView.setNativeAd(it)
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }

            // LAYER 4: Top Action Bar (Ad Tag, Advertiser, Visit CTA)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color(0xB3000000), Color.Transparent)
                        )
                    )
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        color = Color(0xFFFFCC00),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = "Ad · 1 of 1",
                            color = Color.Black,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Text(
                        text = advertiser,
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Surface(
                    color = Color.White.copy(alpha = 0.2f),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.4f)),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.clickable {
                        try {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(currentCreative.destinationUrl))
                            context.startActivity(intent)
                        } catch (_: Exception) {}
                    }
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = currentCreative.ctaText,
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Default.OpenInNew,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }
            }

            // LAYER 5: Center Creative Hero Card (for Survey or Poster types)
            if (currentCreative.videoUrl.isNullOrBlank() || currentCreative.isSurveyType) {
                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(horizontal = 24.dp)
                        .fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    // YouTube Red Play Button Box
                    Surface(
                        color = Color(0xFFFF0000),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.size(width = 48.dp, height = 32.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "YouTube Ad",
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = headline,
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        lineHeight = 22.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = currentCreative.description,
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color.Black.copy(alpha = 0.6f))
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = null,
                            tint = Color(0xFFFFCC00),
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = currentCreative.rating,
                            color = Color(0xFFFFCC00),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = currentCreative.ctaText,
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            // LAYER 6: Bottom Controls & Badges (YouTube Style)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color(0xCC000000))
                        )
                    )
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left Badge: "Sponsored · 0:XX ⓘ"
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0x99000000))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "Sponsored",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )

                        Text(
                            text = " · 0:${if (secondsRemaining < 10) "0$secondsRemaining" else "$secondsRemaining"}",
                            color = Color(0xFFDDDDDD),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Normal
                        )

                        Spacer(modifier = Modifier.width(4.dp))

                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Ad Info",
                            tint = Color(0xFFCCCCCC),
                            modifier = Modifier.size(13.dp)
                        )
                    }

                    // Right Button: YouTube "Skip ad ▶|" or countdown
                    if (canSkip) {
                        Surface(
                            onClick = {
                                InStreamVideoAdManager.skipAd(context)
                            },
                            color = Color.Black.copy(alpha = 0.85f),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.8f)),
                            shape = RoundedCornerShape(18.dp),
                            modifier = Modifier.testTag("youtube_skip_ad_button")
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = "Skip ad",
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(
                                    imageVector = Icons.Default.SkipNext,
                                    contentDescription = "Skip",
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    } else {
                        Surface(
                            color = Color.Black.copy(alpha = 0.75f),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.3f)),
                            shape = RoundedCornerShape(18.dp)
                        ) {
                            Text(
                                text = "Skip in ${skipCountdown}s",
                                color = Color(0xFFDDDDDD),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
            }

            // LAYER 7: YouTube Signature Yellow Progress Bar (#FFCC00) along bottom-most edge
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .align(Alignment.BottomStart)
                    .background(Color(0x33FFFFFF))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(fraction = progress.coerceIn(0f, 1f))
                        .height(3.dp)
                        .background(Color(0xFFFFCC00))
                )
            }
        }
    }
}

/**
 * Botanical chalk line-art doodles matching authentic YouTube survey ads.
 */
@Composable
private fun AdLeafDoodleCanvas(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        val strokeColor = Color.White.copy(alpha = 0.15f)
        val leafStroke = Stroke(
            width = 2.dp.toPx(),
            cap = StrokeCap.Round,
            join = StrokeJoin.Round
        )

        // Left Oak Leaf
        val leftOakPath = Path().apply {
            moveTo(0f, h * 0.70f)
            cubicTo(w * 0.06f, h * 0.73f, w * 0.12f, h * 0.82f, w * 0.18f, h * 0.75f)
            cubicTo(w * 0.24f, h * 0.68f, w * 0.16f, h * 0.56f, w * 0.24f, h * 0.46f)
            cubicTo(w * 0.30f, h * 0.38f, w * 0.20f, h * 0.26f, w * 0.26f, h * 0.18f)
            cubicTo(w * 0.30f, h * 0.10f, w * 0.22f, h * 0.02f, w * 0.14f, h * 0.05f)
            cubicTo(w * 0.06f, h * 0.08f, w * 0.10f, h * 0.22f, w * 0.04f, h * 0.30f)
            cubicTo(0f, h * 0.35f, w * 0.03f, h * 0.48f, 0f, h * 0.58f)
        }
        drawPath(leftOakPath, strokeColor, style = leafStroke)

        // Right Tropical Leaf
        val rightLeafPath = Path().apply {
            moveTo(w * 0.90f, 0f)
            cubicTo(w * 0.80f, h * 0.08f, w * 0.92f, h * 0.18f, w * 0.82f, h * 0.28f)
            cubicTo(w * 0.74f, h * 0.36f, w * 0.88f, h * 0.46f, w * 0.78f, h * 0.56f)
            cubicTo(w * 0.70f, h * 0.66f, w * 0.86f, h * 0.76f, w * 0.80f, h * 0.88f)
            cubicTo(w * 0.86f, h * 0.96f, w * 0.95f, h * 0.82f, w, h * 0.72f)
        }
        drawPath(rightLeafPath, strokeColor, style = leafStroke)
    }
}
