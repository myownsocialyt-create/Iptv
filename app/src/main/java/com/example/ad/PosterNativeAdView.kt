package com.example.ad

import android.content.Context
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.ui.theme.LocalAppColors
import com.example.ui.theme.OttLiveRed
import com.example.ui.theme.OttPrimary
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.nativead.MediaView
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdOptions
import com.google.android.gms.ads.nativead.NativeAdView
import kotlinx.coroutines.delay

/**
 * Poster/Native banner ad displayed directly below or above the Search Bar in portrait mode.
 * Adapts to Dark and Light modes.
 * Includes a close [X] button. When closed, it disappears and refreshes after a cooldown.
 */
@Composable
fun PosterNativeAdView(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val appColors = LocalAppColors.current
    val isDark = isSystemInDarkTheme()

    var nativeAd by remember { mutableStateOf<NativeAd?>(null) }
    var isAdVisible by remember { mutableStateOf(true) }
    var refreshKey by remember { mutableIntStateOf(0) }

    // Official Google AdMob Sample Native Ad Unit ID
    val testAdUnitId = "ca-app-pub-3940256099942544/2247696110"

    // Load AdMob Native Ad whenever refreshKey changes
    LaunchedEffect(refreshKey) {
        try {
            val adLoader = AdLoader.Builder(context, testAdUnitId)
                .forNativeAd { ad ->
                    nativeAd?.destroy()
                    nativeAd = ad
                    isAdVisible = true
                }
                .withAdListener(object : AdListener() {
                    override fun onAdFailedToLoad(error: LoadAdError) {
                        Log.d("PosterNativeAd", "Ad load error: ${error.message}")
                    }
                })
                .withNativeAdOptions(
                    NativeAdOptions.Builder()
                        .setAdChoicesPlacement(NativeAdOptions.ADCHOICES_TOP_RIGHT)
                        .build()
                )
                .build()

            adLoader.loadAd(AdRequest.Builder().build())
        } catch (e: Exception) {
            Log.e("PosterNativeAd", "Error loading ad", e)
        }
    }

    // Auto-refresh ad every 75 seconds if visible
    LaunchedEffect(isAdVisible) {
        if (isAdVisible) {
            delay(75_000L)
            refreshKey++
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            nativeAd?.destroy()
        }
    }

    AnimatedVisibility(
        visible = isAdVisible,
        enter = fadeIn() + slideInVertically(),
        exit = fadeOut() + slideOutVertically(),
        modifier = modifier
    ) {
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (isDark) Color(0xFF1E2638) else Color(0xFFF1F5F9)
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Box(modifier = Modifier.fillMaxWidth()) {
                if (nativeAd != null) {
                    AndroidView(
                        factory = { ctx ->
                            createNativeAdView(ctx, isDark)
                        },
                        update = { adView ->
                            nativeAd?.let { populateNativeAdView(it, adView, isDark) }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp)
                    )
                } else {
                    // Clean placeholder / test poster preview while ad loads
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(OttPrimary.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "AD",
                                color = OttPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .background(OttLiveRed, RoundedCornerShape(3.dp))
                                        .padding(horizontal = 4.dp, vertical = 1.dp)
                                ) {
                                    Text(
                                        text = "Ad",
                                        color = Color.White,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Google Play Sponsored Apps",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = appColors.textPrimary
                                )
                            }
                            Text(
                                text = "Discover trending games & streaming tools",
                                fontSize = 11.sp,
                                color = appColors.textSecondary,
                                maxLines = 1
                            )
                        }
                    }
                }

                // Close [X] Button on top-right corner
                IconButton(
                    onClick = {
                        isAdVisible = false
                        // Cooldown: reappears with fresh ad after 20 seconds
                        (context as? android.app.Activity)?.runOnUiThread {
                            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                                refreshKey++
                                isAdVisible = true
                            }, 20_000L)
                        }
                    },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .size(24.dp)
                        .background(
                            color = if (isDark) Color.Black.copy(alpha = 0.5f) else Color.White.copy(alpha = 0.8f),
                            shape = CircleShape
                        )
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close Ad",
                        tint = if (isDark) Color.LightGray else Color.DarkGray,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }
}

private fun createNativeAdView(context: Context, isDark: Boolean): NativeAdView {
    val nativeAdView = NativeAdView(context)
    val rootLayout = android.widget.LinearLayout(context).apply {
        orientation = android.widget.LinearLayout.VERTICAL
        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    // Top Row: Icon + Headline + Advertiser + CTA
    val topRow = android.widget.LinearLayout(context).apply {
        orientation = android.widget.LinearLayout.HORIZONTAL
        gravity = android.view.Gravity.CENTER_VERTICAL
        layoutParams = android.widget.LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    // Icon
    val iconView = ImageView(context).apply {
        id = View.generateViewId()
        layoutParams = android.widget.LinearLayout.LayoutParams(dpToPx(context, 42), dpToPx(context, 42)).apply {
            setMargins(0, 0, dpToPx(context, 10), 0)
        }
        scaleType = ImageView.ScaleType.FIT_CENTER
    }
    nativeAdView.iconView = iconView
    topRow.addView(iconView)

    // Info Column (Badge + Headline + Body)
    val infoCol = android.widget.LinearLayout(context).apply {
        orientation = android.widget.LinearLayout.VERTICAL
        layoutParams = android.widget.LinearLayout.LayoutParams(
            0,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            1f
        )
    }

    // Badge Row
    val badgeRow = android.widget.LinearLayout(context).apply {
        orientation = android.widget.LinearLayout.HORIZONTAL
        gravity = android.view.Gravity.CENTER_VERTICAL
    }

    val adBadge = TextView(context).apply {
        text = "Ad"
        textSize = 10f
        typeface = android.graphics.Typeface.DEFAULT_BOLD
        setTextColor(android.graphics.Color.WHITE)
        setBackgroundColor(android.graphics.Color.parseColor("#E50914"))
        setPadding(dpToPx(context, 4), dpToPx(context, 1), dpToPx(context, 4), dpToPx(context, 1))
    }
    badgeRow.addView(adBadge)

    val headlineView = TextView(context).apply {
        id = View.generateViewId()
        textSize = 13f
        typeface = android.graphics.Typeface.DEFAULT_BOLD
        setTextColor(if (isDark) android.graphics.Color.WHITE else android.graphics.Color.parseColor("#0F172A"))
        maxLines = 1
        ellipsize = android.text.TextUtils.TruncateAt.END
        setPadding(dpToPx(context, 6), 0, 0, 0)
    }
    nativeAdView.headlineView = headlineView
    badgeRow.addView(headlineView)
    infoCol.addView(badgeRow)

    val bodyView = TextView(context).apply {
        id = View.generateViewId()
        textSize = 11f
        setTextColor(if (isDark) android.graphics.Color.parseColor("#94A3B8") else android.graphics.Color.parseColor("#64748B"))
        maxLines = 1
        ellipsize = android.text.TextUtils.TruncateAt.END
        setPadding(0, dpToPx(context, 2), 0, 0)
    }
    nativeAdView.bodyView = bodyView
    infoCol.addView(bodyView)
    topRow.addView(infoCol)

    // CTA Button
    val ctaButton = Button(context).apply {
        id = View.generateViewId()
        textSize = 11f
        typeface = android.graphics.Typeface.DEFAULT_BOLD
        setTextColor(android.graphics.Color.WHITE)
        setBackgroundColor(android.graphics.Color.parseColor("#E50914"))
        isAllCaps = false
        layoutParams = android.widget.LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            dpToPx(context, 34)
        ).apply {
            setMargins(dpToPx(context, 6), 0, dpToPx(context, 26), 0)
        }
    }
    nativeAdView.callToActionView = ctaButton
    topRow.addView(ctaButton)

    rootLayout.addView(topRow)

    // Optional Poster MediaView
    val mediaView = MediaView(context).apply {
        id = View.generateViewId()
        layoutParams = android.widget.LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            dpToPx(context, 120)
        ).apply {
            setMargins(0, dpToPx(context, 8), 0, 0)
        }
    }
    nativeAdView.mediaView = mediaView
    rootLayout.addView(mediaView)

    nativeAdView.addView(rootLayout)
    return nativeAdView
}

private fun populateNativeAdView(nativeAd: NativeAd, adView: NativeAdView, isDark: Boolean) {
    (adView.headlineView as? TextView)?.text = nativeAd.headline

    val bodyView = adView.bodyView as? TextView
    val bodyText = nativeAd.body ?: nativeAd.advertiser
    if (!bodyText.isNullOrBlank()) {
        bodyView?.text = bodyText
        bodyView?.visibility = View.VISIBLE
    } else {
        bodyView?.visibility = View.GONE
    }

    val ctaView = adView.callToActionView as? Button
    if (!nativeAd.callToAction.isNullOrBlank()) {
        ctaView?.text = nativeAd.callToAction
        ctaView?.visibility = View.VISIBLE
    } else {
        ctaView?.text = "Open"
        ctaView?.visibility = View.VISIBLE
    }

    val iconView = adView.iconView as? ImageView
    if (nativeAd.icon?.drawable != null) {
        iconView?.setImageDrawable(nativeAd.icon?.drawable)
        iconView?.visibility = View.VISIBLE
    } else {
        iconView?.visibility = View.GONE
    }

    val mediaView = adView.mediaView as? MediaView
    if (nativeAd.mediaContent != null) {
        mediaView?.mediaContent = nativeAd.mediaContent
        mediaView?.visibility = View.VISIBLE
    } else {
        mediaView?.visibility = View.GONE
    }

    adView.setNativeAd(nativeAd)
}

private fun dpToPx(context: Context, dp: Int): Int {
    return (dp * context.resources.displayMetrics.density).toInt()
}
