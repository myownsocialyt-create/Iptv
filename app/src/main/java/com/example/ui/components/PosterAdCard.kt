package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.delay

data class PosterAdItem(
    val title: String,
    val description: String,
    val sponsor: String,
    val cta: String,
    val rating: String,
    val imageUrl: String
)

/**
 * Poster-style Ad Card placed BELOW the video player
 * (Exact match to the red-marked area in User's 1st Reference Photo).
 * Features:
 * - Does not obstruct or overlay on the video player.
 * - Fits cleanly with the Light Theme (white card, subtle border).
 * - Randomly reloads / rotates smoothly every 50 seconds.
 * - Shows clear "Ad" / "Sponsored" badge.
 * - Can be dismissed with close (X) button.
 */
@Composable
fun PosterAdCard(
    modifier: Modifier = Modifier
) {
    var isVisible by remember { mutableStateOf(true) }
    var adIndex by remember { mutableIntStateOf(0) }

    val adList = remember {
        listOf(
            PosterAdItem(
                title = "Build & Deploy Android Apps Fast",
                description = "Learn Jetpack Compose & Kotlin modern architecture with live interactive tools.",
                sponsor = "Google Cloud Developers",
                cta = "INSTALL",
                rating = "4.9 ★",
                imageUrl = "https://images.unsplash.com/photo-1551650975-87deedd944c3?w=500&q=80"
            ),
            PosterAdItem(
                title = "Create Your First Website Today",
                description = "Build fast, scalable websites and mobile backends with zero configuration.",
                sponsor = "Cloud App Suite",
                cta = "OPEN",
                rating = "4.8 ★",
                imageUrl = "https://images.unsplash.com/photo-1460925895917-afdab827c52f?w=500&q=80"
            ),
            PosterAdItem(
                title = "Stream Live Sports in 4K HDR",
                description = "Watch global cricket, football, racing & cinema without buffering.",
                sponsor = "Ultra Sports Stream",
                cta = "LEARN MORE",
                rating = "4.7 ★",
                imageUrl = "https://images.unsplash.com/photo-1508098682722-e99c43a406b2?w=500&q=80"
            )
        )
    }

    // Auto rotate randomly every 50 seconds
    LaunchedEffect(Unit) {
        while (true) {
            delay(50_000L)
            adIndex = (adIndex + 1) % adList.size
        }
    }

    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        val currentAd = adList[adIndex % adList.size]

        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 6.dp)
                .testTag("poster_ad_card")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp)
            ) {
                // Header Row: Sponsored Tag + Advertiser + Dismiss (X)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            color = Color(0xFFF59E0B),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = "Ad",
                                color = Color.Black,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.ExtraBold,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Sponsored • ${currentAd.sponsor}",
                            color = Color(0xFF64748B),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    IconButton(
                        onClick = { isVisible = false },
                        modifier = Modifier.size(22.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Dismiss Ad",
                            tint = Color(0xFF94A3B8),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Content Row: Poster Thumbnail + Description + CTA Button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Poster Thumbnail Image
                    Box(
                        modifier = Modifier
                            .size(70.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFFF1F5F9))
                    ) {
                        AsyncImage(
                            model = currentAd.imageUrl,
                            contentDescription = currentAd.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = currentAd.title,
                            color = Color(0xFF0F172A),
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = currentAd.description,
                            color = Color(0xFF64748B),
                            fontSize = 11.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            lineHeight = 15.sp
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = currentAd.rating,
                                color = Color(0xFFD97706),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Surface(
                                color = Color(0xFF2563EB),
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier
                                    .clickable { /* Simulate CTA interaction */ }
                            ) {
                                Text(
                                    text = currentAd.cta,
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
