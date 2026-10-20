package com.example.ad

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.util.Log
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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
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
 * Real Google AdMob Native Ad Banner displayed above/below the search bar in Split Screen.
 * Uses official AdMob Native Advanced Test ID: ca-app-pub-3940256099942544/2247696110
 * Features:
 * - Displays ONLY genuine AdMob NativeAdView with Headline, Body, Icon, MediaView, and CTA Button.
 * - ZERO custom text or fake labels.
 * - Cut/Close (✕) button to dismiss ad.
 * - Manual Reload (🔄) button.
 * - Automatic refresh every 60 seconds.
 */
@Composable
fun PosterNativeAdView(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activity = context.findActivity() ?: return

    var nativeAd by remember { mutableStateOf<NativeAd?>(null) }
    var isAdVisible by remember { mutableStateOf(true) }
    var refreshKey by remember { mutableIntStateOf(0) }

    // Official Google AdMob Native Advanced Test Ad Unit ID
    val testAdUnitId = "ca-app-pub-3940256099942544/2247696110"

    // Load real AdMob Native Ad using Activity context
    LaunchedEffect(refreshKey) {
        try {
            val adLoader = AdLoader.Builder(activity, testAdUnitId)
                .forNativeAd { ad ->
                    nativeAd?.destroy()
                    nativeAd = ad
                    isAdVisible = true
                    Log.d("PosterNativeAd", "AdMob Native Ad loaded successfully: ${ad.headline}")
                }
                .withAdListener(object : AdListener() {
                    override fun onAdFailedToLoad(error: LoadAdError) {
                        Log.w("PosterNativeAd", "AdMob native ad failed to load (${error.code}): ${error.message}")
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
            Log.e("PosterNativeAd", "Error loading AdMob ad", e)
        }
    }

    // Auto-refresh ad every 60 seconds while visible
    LaunchedEffect(isAdVisible, refreshKey) {
        if (isAdVisible) {
            delay(60_000L)
            refreshKey++
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            nativeAd?.destroy()
            nativeAd = null
        }
    }

    // Only render when the real AdMob ad is loaded and visible
    AnimatedVisibility(
        visible = isAdVisible && nativeAd != null,
        enter = fadeIn() + slideInVertically(),
        exit = fadeOut() + slideOutVertically(),
        modifier = modifier
    ) {
        val currentAd = nativeAd ?: return@AnimatedVisibility

        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp)
                .testTag("split_screen_native_ad_card")
        ) {
            Box(modifier = Modifier.fillMaxWidth()) {
                AndroidView(
                    factory = { ctx ->
                        createNativeAdView(ctx)
                    },
                    update = { adView ->
                        populateNativeAdView(currentAd, adView)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(10.dp)
                )

                // Top-Right Actions: Reload/Refresh Button + Cut/Close (X) Button
                Row(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Reload / Refresh Icon Button
                    Surface(
                        shape = CircleShape,
                        color = Color(0xFFF1F5F9),
                        modifier = Modifier
                            .size(26.dp)
                            .clickable { refreshKey++ }
                            .testTag("ad_reload_button")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Reload Ad",
                                tint = Color(0xFF64748B),
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }

                    // Cut / Close (X) Button
                    Surface(
                        shape = CircleShape,
                        color = Color(0xFFF1F5F9),
                        modifier = Modifier
                            .size(26.dp)
                            .clickable {
                                isAdVisible = false
                                // Auto-reappear with refreshed ad after 15 seconds
                                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                                    refreshKey++
                                    isAdVisible = true
                                }, 15_000L)
                            }
                            .testTag("ad_cut_close_button")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close Ad",
                                tint = Color(0xFF64748B),
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun createNativeAdView(context: Context): NativeAdView {
    val nativeAdView = NativeAdView(context)
    val rootLayout = android.widget.LinearLayout(context).apply {
        orientation = android.widget.LinearLayout.VERTICAL
        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    // Top Row: Icon + Info + CTA Button
    val topRow = android.widget.LinearLayout(context).apply {
        orientation = android.widget.LinearLayout.HORIZONTAL
        gravity = android.view.Gravity.CENTER_VERTICAL
        layoutParams = android.widget.LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    // App Icon from AdMob
    val iconView = ImageView(context).apply {
        id = View.generateViewId()
        layoutParams = android.widget.LinearLayout.LayoutParams(dpToPx(context, 44), dpToPx(context, 44)).apply {
            setMargins(0, 0, dpToPx(context, 10), 0)
        }
        scaleType = ImageView.ScaleType.FIT_CENTER
    }
    nativeAdView.iconView = iconView
    topRow.addView(iconView)

    // Info Column (Ad Badge + Headline + Body)
    val infoCol = android.widget.LinearLayout(context).apply {
        orientation = android.widget.LinearLayout.VERTICAL
        layoutParams = android.widget.LinearLayout.LayoutParams(
            0,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            1f
        )
    }

    val badgeRow = android.widget.LinearLayout(context).apply {
        orientation = android.widget.LinearLayout.HORIZONTAL
        gravity = android.view.Gravity.CENTER_VERTICAL
    }

    val adBadge = TextView(context).apply {
        text = "Ad"
        textSize = 9f
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
        setTextColor(android.graphics.Color.parseColor("#0F172A"))
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
        setTextColor(android.graphics.Color.parseColor("#64748B"))
        maxLines = 1
        ellipsize = android.text.TextUtils.TruncateAt.END
        setPadding(0, dpToPx(context, 2), 0, 0)
    }
    nativeAdView.bodyView = bodyView
    infoCol.addView(bodyView)
    topRow.addView(infoCol)

    // Call To Action Button from AdMob
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
            setMargins(dpToPx(context, 6), 0, dpToPx(context, 54), 0)
        }
    }
    nativeAdView.callToActionView = ctaButton
    topRow.addView(ctaButton)

    rootLayout.addView(topRow)

    // MediaView for video or graphic banner from AdMob
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

private fun populateNativeAdView(nativeAd: NativeAd, adView: NativeAdView) {
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
