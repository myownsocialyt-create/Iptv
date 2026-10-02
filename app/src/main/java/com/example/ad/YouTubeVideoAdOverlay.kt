package com.example.ad

import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * YouTube-style In-Stream Video Ad Overlay that renders directly over the video player
 * in both Portrait and Landscape (and Fullscreen) modes, exactly matching Photos 1 & 2.
 *
 * Implements:
 * - Authentic botanical leaf line-art / chalk doodle background (Photo 1 & 2)
 * - YouTube red rounded-rectangle play icon
 * - "Help YouTube by answering one question" headline
 * - "Sponsored • 0:22 ⓘ" badge
 * - "Skip in 5s" -> "Skip ad ▶|" pill button
 * - Signature YouTube yellow animated ad progress bar
 */
@Composable
fun YouTubeVideoAdOverlay(
    adState: InStreamAdState,
    onSkipAd: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (!adState.isAdActive || adState.creative == null) return

    val context = LocalContext.current
    val creative = adState.creative
    val yellowAdColor = Color(0xFFFFD600) // YouTube signature ad progress yellow

    val configuration = LocalConfiguration.current
    val isLandscapeOrientation = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    val animatedProgress by animateFloatAsState(
        targetValue = adState.progressFraction,
        animationSpec = tween(durationMillis = 1000, easing = LinearEasing),
        label = "youtube_ad_progress_anim"
    )

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF141414))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                // Tapping ad can open advertiser survey
                try {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(creative.targetUrl))
                    context.startActivity(intent)
                } catch (_: Exception) {}
            }
            .testTag("youtube_in_stream_ad_overlay")
    ) {
        val isWide = maxWidth >= 500.dp || isLandscapeOrientation

        // 1. Botanical Leaf Chalk Doodle Line-Art Background (matching Photos 1 & 2)
        YouTubeLeafDoodleBackground(modifier = Modifier.fillMaxSize())

        // 2. Center Brand & Headline: YouTube Logo + "Help YouTube by answering one question"
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = if (isWide) 48.dp else 20.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // YouTube Red Play Button Box
            Surface(
                color = Color(0xFFFF0000),
                shape = RoundedCornerShape(if (isWide) 12.dp else 9.dp),
                modifier = Modifier.size(
                    width = if (isWide) 56.dp else 44.dp,
                    height = if (isWide) 38.dp else 30.dp
                )
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "YouTube Ad",
                        tint = Color.White,
                        modifier = Modifier.size(if (isWide) 26.dp else 20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(if (isWide) 16.dp else 10.dp))

            // Main Prompt Title (e.g. "Help YouTube by answering one question")
            Text(
                text = creative.title,
                color = Color.White,
                fontSize = if (isWide) 24.sp else 16.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                lineHeight = if (isWide) 30.sp else 21.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            if (isWide && creative.subtitle.isNotBlank() && creative.subtitle != "YouTube advertiser survey") {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = creative.subtitle,
                    color = Color.White.copy(alpha = 0.75f),
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // 3. Bottom Bar:
        // Left: "Sponsored • 0:15 ⓘ"
        // Right: "Skip in 5s" OR "Skip ad ▶|" button
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(
                    horizontal = if (isWide) 20.dp else 12.dp,
                    vertical = if (isWide) 16.dp else 10.dp
                ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Left: Sponsored Badge & Timer
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .background(Color.Black.copy(alpha = 0.65f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 7.dp, vertical = 3.5.dp)
            ) {
                Text(
                    text = "Sponsored",
                    color = Color.White.copy(alpha = 0.95f),
                    fontSize = if (isWide) 13.sp else 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = " • 0:${if (adState.remainingSeconds < 10) "0" else ""}${adState.remainingSeconds}",
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = if (isWide) 13.sp else 11.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = "Ad Info",
                    tint = Color.White.copy(alpha = 0.8f),
                    modifier = Modifier.size(if (isWide) 14.dp else 12.dp)
                )
            }

            // Right: YouTube Skip Ad Pill Button
            if (adState.canSkip) {
                Surface(
                    onClick = onSkipAd,
                    color = Color.Black.copy(alpha = 0.85f),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.75f)),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.testTag("youtube_skip_ad_button")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(
                            horizontal = if (isWide) 16.dp else 12.dp,
                            vertical = if (isWide) 8.dp else 5.dp
                        )
                    ) {
                        Text(
                            text = "Skip ad",
                            color = Color.White,
                            fontSize = if (isWide) 14.sp else 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Default.SkipNext,
                            contentDescription = "Skip",
                            tint = Color.White,
                            modifier = Modifier.size(if (isWide) 18.dp else 15.dp)
                        )
                    }
                }
            } else {
                Surface(
                    color = Color.Black.copy(alpha = 0.75f),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.35f)),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(
                            horizontal = if (isWide) 14.dp else 10.dp,
                            vertical = if (isWide) 8.dp else 5.dp
                        )
                    ) {
                        Text(
                            text = "Skip in ${adState.skipCountdownSeconds}s",
                            color = Color.White.copy(alpha = 0.9f),
                            fontSize = if (isWide) 13.sp else 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }

        // 4. Yellow Animated Ad Progress Bar along bottom edge
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .height(3.5.dp)
                .background(Color.White.copy(alpha = 0.2f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(animatedProgress)
                    .height(3.5.dp)
                    .background(yellowAdColor)
            )
        }
    }
}

/**
 * Botanical chalk line-art leaf doodles faithfully matching the YouTube survey ad background
 * in user's Reference Photos 1 & 2.
 */
@Composable
private fun YouTubeLeafDoodleBackground(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        val strokeColor = Color.White.copy(alpha = 0.22f)
        val leafStroke = Stroke(
            width = 2.dp.toPx(),
            cap = StrokeCap.Round,
            join = StrokeJoin.Round
        )
        val veinStroke = Stroke(
            width = 1.3.dp.toPx(),
            cap = StrokeCap.Round
        )

        // --- 1. Left Oak Leaf with round lobes (Photo 1 & 2 left side) ---
        val leftOakPath = Path().apply {
            moveTo(0f, h * 0.72f)
            cubicTo(w * 0.06f, h * 0.75f, w * 0.12f, h * 0.85f, w * 0.18f, h * 0.78f)
            cubicTo(w * 0.24f, h * 0.70f, w * 0.16f, h * 0.58f, w * 0.24f, h * 0.48f)
            cubicTo(w * 0.30f, h * 0.40f, w * 0.20f, h * 0.28f, w * 0.26f, h * 0.20f)
            cubicTo(w * 0.30f, h * 0.12f, w * 0.22f, h * 0.02f, w * 0.14f, h * 0.05f)
            cubicTo(w * 0.06f, h * 0.08f, w * 0.10f, h * 0.22f, w * 0.04f, h * 0.30f)
            cubicTo(0f, h * 0.35f, w * 0.03f, h * 0.48f, 0f, h * 0.58f)
        }
        drawPath(leftOakPath, strokeColor, style = leafStroke)

        // Left Oak Central Vein
        val leftVein = Path().apply {
            moveTo(0f, h * 0.72f)
            quadraticBezierTo(w * 0.12f, h * 0.42f, w * 0.14f, h * 0.05f)
        }
        drawPath(leftVein, strokeColor.copy(alpha = 0.16f), style = veinStroke)

        // Side veins for left leaf
        drawLine(strokeColor.copy(alpha = 0.16f), Offset(w * 0.07f, h * 0.52f), Offset(w * 0.20f, h * 0.48f), strokeWidth = 1.3.dp.toPx())
        drawLine(strokeColor.copy(alpha = 0.16f), Offset(w * 0.11f, h * 0.38f), Offset(w * 0.24f, h * 0.34f), strokeWidth = 1.3.dp.toPx())
        drawLine(strokeColor.copy(alpha = 0.16f), Offset(w * 0.12f, h * 0.24f), Offset(w * 0.24f, h * 0.20f), strokeWidth = 1.3.dp.toPx())

        // --- 2. Right Undulating Tropical / Monstera Leaf (Photo 1 & 2 right side) ---
        val rightLeafPath = Path().apply {
            moveTo(w * 0.88f, 0f)
            cubicTo(w * 0.78f, h * 0.08f, w * 0.90f, h * 0.18f, w * 0.80f, h * 0.28f)
            cubicTo(w * 0.72f, h * 0.36f, w * 0.86f, h * 0.46f, w * 0.76f, h * 0.56f)
            cubicTo(w * 0.68f, h * 0.66f, w * 0.84f, h * 0.76f, w * 0.78f, h * 0.88f)
            cubicTo(w * 0.84f, h * 0.96f, w * 0.95f, h * 0.82f, w, h * 0.72f)
        }
        drawPath(rightLeafPath, strokeColor, style = leafStroke)

        // Right Leaf Central Vein
        val rightVein = Path().apply {
            moveTo(w * 0.88f, 0f)
            quadraticBezierTo(w * 0.80f, h * 0.46f, w * 0.78f, h * 0.88f)
        }
        drawPath(rightVein, strokeColor.copy(alpha = 0.16f), style = veinStroke)

        // Side veins for right leaf
        drawLine(strokeColor.copy(alpha = 0.16f), Offset(w * 0.84f, h * 0.20f), Offset(w * 0.74f, h * 0.26f), strokeWidth = 1.3.dp.toPx())
        drawLine(strokeColor.copy(alpha = 0.16f), Offset(w * 0.80f, h * 0.38f), Offset(w * 0.68f, h * 0.46f), strokeWidth = 1.3.dp.toPx())
        drawLine(strokeColor.copy(alpha = 0.16f), Offset(w * 0.78f, h * 0.58f), Offset(w * 0.66f, h * 0.66f), strokeWidth = 1.3.dp.toPx())

        // --- 3. Bottom Left Corner Vine & Leaf ---
        val btmLeftPath = Path().apply {
            moveTo(0f, h * 0.86f)
            cubicTo(w * 0.07f, h * 0.84f, w * 0.12f, h * 0.94f, w * 0.19f, h)
        }
        drawPath(btmLeftPath, strokeColor, style = leafStroke)

        // --- 4. Top Center Subtle Vine ---
        val topVinePath = Path().apply {
            moveTo(w * 0.44f, 0f)
            cubicTo(w * 0.48f, h * 0.08f, w * 0.54f, h * 0.05f, w * 0.58f, 0f)
        }
        drawPath(topVinePath, strokeColor.copy(alpha = 0.14f), style = leafStroke)
    }
}
