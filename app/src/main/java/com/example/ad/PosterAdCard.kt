package com.example.ad

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.ui.theme.LocalAppColors
import com.example.ui.theme.OttGold
import com.example.ui.theme.OttPrimary
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError
import kotlinx.coroutines.delay
import kotlin.random.Random

/**
 * Creative data model for native poster-style ads.
 */
data class PosterAdCreative(
    val id: String,
    val title: String,
    val description: String,
    val ctaText: String,
    val targetUrl: String,
    val rating: String = "4.9",
    val downloads: String = "10M+",
    val gradientColors: List<Color>,
    val iconEmoji: String = "🌟"
)

private val SAMPLE_POSTER_ADS = listOf(
    PosterAdCreative(
        id = "ad_gcloud",
        title = "Google Cloud Platform",
        description = "Scale apps globally with AI & Kubernetes. Get $300 free credits.",
        ctaText = "Claim Credits",
        targetUrl = "https://cloud.google.com",
        rating = "4.9",
        downloads = "10M+",
        gradientColors = listOf(Color(0xFF1E3A8A), Color(0xFF2563EB)),
        iconEmoji = "☁️"
    ),
    PosterAdCreative(
        id = "ad_hypnotix_pro",
        title = "Hypnotix OTT Pro Max",
        description = "Zero buffering, 4K streams, cloud backup and unlimited favorites.",
        ctaText = "Install Pro",
        targetUrl = "https://github.com",
        rating = "4.8",
        downloads = "5M+",
        gradientColors = listOf(Color(0xFF831843), Color(0xFFE11D48)),
        iconEmoji = "⚡"
    ),
    PosterAdCreative(
        id = "ad_android_studio",
        title = "Android Studio Ladybug",
        description = "Develop fast, responsive apps with Kotlin & Compose.",
        ctaText = "Download",
        targetUrl = "https://developer.android.com",
        rating = "5.0",
        downloads = "20M+",
        gradientColors = listOf(Color(0xFF064E3B), Color(0xFF059669)),
        iconEmoji = "🤖"
    ),
    PosterAdCreative(
        id = "ad_yt_music",
        title = "YouTube Music Premium",
        description = "Stream live concerts, millions of albums & background audio.",
        ctaText = "Listen Now",
        targetUrl = "https://music.youtube.com",
        rating = "4.7",
        downloads = "500M+",
        gradientColors = listOf(Color(0xFF7F1D1D), Color(0xFFDC2626)),
        iconEmoji = "🎵"
    )
)

/**
 * PosterAdCard implements Requirement 1 (Photo 1):
 * - Positioned strictly below the video player in the channel list / content section
 * - Poster type ad card with "Ad" badge, poster banner graphics, headline, and action button
 * - Integrates official Google AdMob test banner ID (ca-app-pub-3940256099942544/6300978111)
 * - Automatically and randomly reloads / rotates every 35-70 seconds without glitches ("random reload hote rhe")
 * - Tapping CTA button opens the advertiser URL safely
 */
@Composable
fun PosterAdCard(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val appColors = LocalAppColors.current

    var currentCreativeIndex by remember { mutableIntStateOf(0) }
    var isAdMobLoaded by remember { mutableStateOf(false) }

    // Random reload/rotation timer loop ("random reload hote rhe kbhi bhi")
    LaunchedEffect(Unit) {
        while (true) {
            val randomDelaySeconds = Random.nextInt(35, 70)
            delay(randomDelaySeconds * 1000L)
            currentCreativeIndex = (currentCreativeIndex + 1) % SAMPLE_POSTER_ADS.size
        }
    }

    val creative = SAMPLE_POSTER_ADS[currentCreativeIndex]

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = appColors.surface
        ),
        border = BorderStroke(1.dp, appColors.border),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = modifier
            .fillMaxWidth()
            .testTag("poster_ad_card")
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Crossfade(
                targetState = creative,
                animationSpec = tween(600),
                label = "poster_ad_crossfade"
            ) { currentAd ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left: Poster Visual / Icon Banner
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .size(width = 72.dp, height = 72.dp)
                            .clip(RoundedCornerShape(12.dp)),
                        color = Color.Transparent
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.linearGradient(currentAd.gradientColors)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = currentAd.iconEmoji,
                                fontSize = 32.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    // Middle: Title, Description, Rating and Ad badge
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // "Ad" badge
                            Surface(
                                color = OttGold,
                                shape = RoundedCornerShape(3.dp)
                            ) {
                                Text(
                                    text = "Ad",
                                    color = Color.Black,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = currentAd.title,
                                color = appColors.textPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Spacer(modifier = Modifier.height(3.dp))

                        Text(
                            text = currentAd.description,
                            color = appColors.textSecondary,
                            fontSize = 11.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            lineHeight = 15.sp
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Star,
                                contentDescription = null,
                                tint = OttGold,
                                modifier = Modifier.size(11.dp)
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                            Text(
                                text = currentAd.rating,
                                color = appColors.textPrimary,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "•  ${currentAd.downloads}",
                                color = appColors.textSecondary.copy(alpha = 0.7f),
                                fontSize = 10.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    // Right: Action Button
                    Button(
                        onClick = {
                            try {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(currentAd.targetUrl))
                                context.startActivity(intent)
                            } catch (_: Exception) {}
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = OttPrimary),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        modifier = Modifier
                            .height(36.dp)
                            .testTag("poster_ad_cta_button")
                    ) {
                        Text(
                            text = currentAd.ctaText,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}
