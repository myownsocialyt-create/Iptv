package com.example.ad

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.nativead.MediaView
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdView

/**
 * YouTube-style In-Stream Video Ad Overlay that renders ONLY the REAL Google AdMob NativeAdView.
 * ZERO custom text, ZERO fake posters, ZERO "Loading test ID" placeholders.
 */
@Composable
fun YouTubeVideoAdOverlay(
    adState: InStreamAdState,
    onSkipAd: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (!adState.isAdActive) return

    val activeAd by InStreamAdManager.activeNativeAdFlow.collectAsState()
    val ad = activeAd ?: InStreamAdManager.activeNativeAd ?: return

    val yellowAdColor = Color(0xFFFFD600) // Signature YouTube yellow ad bar

    val animatedProgress by animateFloatAsState(
        targetValue = adState.progressFraction,
        animationSpec = tween(durationMillis = 1000, easing = LinearEasing),
        label = "youtube_ad_progress_anim"
    )

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .testTag("youtube_in_stream_ad_overlay")
    ) {
        // 1. REAL GOOGLE ADMOB NATIVE AD VIEW
        AndroidView(
            factory = { ctx ->
                createInStreamNativeAdView(ctx)
            },
            update = { adView ->
                populateInStreamNativeAdView(ad, adView)
            },
            modifier = Modifier.fillMaxSize()
        )

        // 2. YOUTUBE IN-STREAM TOP OVERLAY: "Sponsored • 0:16 ⓘ"
        Row(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                color = Color.Black.copy(alpha = 0.75f),
                shape = RoundedCornerShape(6.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Sponsored",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = " • 0:${if (adState.remainingSeconds < 10) "0" else ""}${adState.remainingSeconds}",
                        color = Color.White.copy(alpha = 0.9f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "Ad Info",
                        tint = Color.White.copy(alpha = 0.85f),
                        modifier = Modifier.size(13.dp)
                    )
                }
            }
        }

        // 3. YOUTUBE BOTTOM-RIGHT OVERLAY: "Skip in 5s" -> "Skip ad ▶|" Button
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(horizontal = 14.dp, vertical = 18.dp)
        ) {
            if (adState.canSkip) {
                Surface(
                    color = Color.Black.copy(alpha = 0.85f),
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.6f)),
                    modifier = Modifier
                        .clickable(onClick = onSkipAd)
                        .testTag("youtube_skip_ad_button")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Skip ad",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.Default.SkipNext,
                            contentDescription = "Skip",
                            tint = Color.White,
                            modifier = Modifier.size(17.dp)
                        )
                    }
                }
            } else {
                Surface(
                    color = Color.Black.copy(alpha = 0.75f),
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.25f))
                ) {
                    Text(
                        text = "Skip in ${adState.skipCountdownSeconds}s",
                        color = Color.White.copy(alpha = 0.95f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                    )
                }
            }
        }

        // 4. SIGNATURE YOUTUBE YELLOW PROGRESS BAR AT BOTTOM
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth(animatedProgress)
                .height(3.5.dp)
                .background(yellowAdColor)
        )
    }
}

private fun createInStreamNativeAdView(context: Context): NativeAdView {
    val nativeAdView = NativeAdView(context).apply {
        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
    }

    val rootFrame = FrameLayout(context).apply {
        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
    }

    // MediaView renders real Google video/poster creative
    val mediaView = MediaView(context).apply {
        id = View.generateViewId()
        layoutParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        setImageScaleType(ImageView.ScaleType.CENTER_CROP)
    }
    nativeAdView.mediaView = mediaView
    rootFrame.addView(mediaView)

    // Bottom Bar for Headline, Body, and Call to Action
    val bottomOverlay = android.widget.LinearLayout(context).apply {
        orientation = android.widget.LinearLayout.HORIZONTAL
        gravity = android.view.Gravity.CENTER_VERTICAL
        val pad = (12 * context.resources.displayMetrics.density).toInt()
        setPadding(pad, pad, pad, pad)
        setBackgroundColor(android.graphics.Color.parseColor("#D9000000"))
        layoutParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = android.view.Gravity.BOTTOM
            bottomMargin = (12 * context.resources.displayMetrics.density).toInt()
        }
    }

    // App Icon from AdMob
    val iconView = ImageView(context).apply {
        id = View.generateViewId()
        val sz = (42 * context.resources.displayMetrics.density).toInt()
        layoutParams = android.widget.LinearLayout.LayoutParams(sz, sz).apply {
            rightMargin = (10 * context.resources.displayMetrics.density).toInt()
        }
        scaleType = ImageView.ScaleType.FIT_CENTER
    }
    nativeAdView.iconView = iconView
    bottomOverlay.addView(iconView)

    // Text Details Column
    val textCol = android.widget.LinearLayout(context).apply {
        orientation = android.widget.LinearLayout.VERTICAL
        layoutParams = android.widget.LinearLayout.LayoutParams(
            0,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            1f
        )
    }

    val headlineView = TextView(context).apply {
        id = View.generateViewId()
        textSize = 14f
        typeface = android.graphics.Typeface.DEFAULT_BOLD
        setTextColor(android.graphics.Color.WHITE)
        maxLines = 1
        ellipsize = android.text.TextUtils.TruncateAt.END
    }
    nativeAdView.headlineView = headlineView
    textCol.addView(headlineView)

    val bodyView = TextView(context).apply {
        id = View.generateViewId()
        textSize = 11f
        setTextColor(android.graphics.Color.parseColor("#CBD5E1"))
        maxLines = 1
        ellipsize = android.text.TextUtils.TruncateAt.END
    }
    nativeAdView.bodyView = bodyView
    textCol.addView(bodyView)

    bottomOverlay.addView(textCol)

    // Call To Action Button
    val ctaButton = Button(context).apply {
        id = View.generateViewId()
        textSize = 11f
        typeface = android.graphics.Typeface.DEFAULT_BOLD
        setTextColor(android.graphics.Color.WHITE)
        setBackgroundColor(android.graphics.Color.parseColor("#E50914"))
        isAllCaps = false
        layoutParams = android.widget.LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            (36 * context.resources.displayMetrics.density).toInt()
        ).apply {
            leftMargin = (8 * context.resources.displayMetrics.density).toInt()
            rightMargin = (112 * context.resources.displayMetrics.density).toInt() // clearance for Skip button
        }
    }
    nativeAdView.callToActionView = ctaButton
    bottomOverlay.addView(ctaButton)

    rootFrame.addView(bottomOverlay)
    nativeAdView.addView(rootFrame)
    return nativeAdView
}

private fun populateInStreamNativeAdView(nativeAd: NativeAd, adView: NativeAdView) {
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
