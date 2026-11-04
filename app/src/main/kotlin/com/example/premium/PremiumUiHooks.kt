package com.example.premium

import android.app.Activity
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow

object PremiumUiHooks {

    private var startupBillingHelper: BillingHelper? = null

    @JvmStatic
    fun onMainActivityCreate(activity: Activity) {
        try {
            PremiumManager.init(activity)
            if (startupBillingHelper == null) {
                startupBillingHelper = BillingHelper(activity.applicationContext)
            } else {
                startupBillingHelper?.queryActivePurchases()
            }
        } catch (t: Throwable) {
            t.printStackTrace()
        }
    }

    private fun findActiveActivity(): Activity? {
        return try {
            val helperClass = Class.forName("com.example.player.IptvAudioHelper")
            val method = helperClass.getMethod("getActiveActivity")
            method.invoke(null) as? Activity
        } catch (t: Throwable) {
            null
        }
    }

    private fun launchPaywall(context: Context? = null) {
        val act = (context as? Activity) ?: findActiveActivity()
        if (act != null) {
            SubscriptionActivity.start(act)
        } else if (context != null) {
            SubscriptionActivity.start(context)
        }
    }

    @JvmStatic
    fun shouldBlockPip(activity: Activity): Boolean {
        if (PremiumManager.canUsePip()) {
            return false
        }
        SubscriptionActivity.start(activity)
        return true
    }

    @JvmStatic
    fun shouldBlockPlayChannel(viewModel: Any, channel: Any): Boolean {
        if (PremiumManager.canPlayXtream()) {
            return false
        }
        try {
            val vmClass = viewModel.javaClass
            val currentSectionFlow = vmClass.getMethod("getCurrentSection").invoke(viewModel) as? StateFlow<*>
            val sectionName = currentSectionFlow?.value?.toString() ?: ""

            val chClass = channel.javaClass
            val streamUrl = chClass.getMethod("getStreamUrl").invoke(channel) as? String ?: ""
            val channelId = chClass.getMethod("getId").invoke(channel) as? String ?: ""

            var isXtream = sectionName.equals("XTREAM", ignoreCase = true) ||
                channelId.startsWith("xtream_", ignoreCase = true)

            if (!isXtream) {
                val xtreamList = (vmClass.getMethod("getXtreamChannels").invoke(viewModel) as? StateFlow<*>)?.value as? Iterable<*>
                if (xtreamList != null) {
                    for (item in xtreamList) {
                        if (item != null) {
                            val itemUrl = item.javaClass.getMethod("getStreamUrl").invoke(item) as? String
                            if (itemUrl == streamUrl && streamUrl.isNotEmpty()) {
                                isXtream = true
                                break
                            }
                        }
                    }
                }
            }

            if (!isXtream) {
                val vodList = (vmClass.getMethod("getXtreamVodChannels").invoke(viewModel) as? StateFlow<*>)?.value as? Iterable<*>
                if (vodList != null) {
                    for (item in vodList) {
                        if (item != null) {
                            val itemUrl = item.javaClass.getMethod("getStreamUrl").invoke(item) as? String
                            if (itemUrl == streamUrl && streamUrl.isNotEmpty()) {
                                isXtream = true
                                break
                            }
                        }
                    }
                }
            }

            if (isXtream) {
                launchPaywall()
                return true
            }
        } catch (t: Throwable) {
            t.printStackTrace()
        }
        return false
    }

    @JvmStatic
    fun shouldBlockAddPlaylist(viewModel: Any): Boolean {
        if (PremiumManager.isPremium()) {
            return false
        }
        try {
            val vmClass = viewModel.javaClass
            val customFlow = vmClass.getMethod("getCustomPlaylists").invoke(viewModel) as? StateFlow<*>
            val list = customFlow?.value as? Collection<*>
            val currentCount = list?.size ?: 0
            if (!PremiumManager.canAddPlaylist(currentCount)) {
                launchPaywall()
                return true
            }
        } catch (t: Throwable) {
            t.printStackTrace()
        }
        return false
    }

    @JvmStatic
    fun shouldBlockToggleFavorite(viewModel: Any, channel: Any): Boolean {
        if (PremiumManager.isPremium()) {
            return false
        }
        try {
            val vmClass = viewModel.javaClass
            val favFlow = vmClass.getMethod("getFavorites").invoke(viewModel) as? StateFlow<*>
            val favList = favFlow?.value as? Collection<*> ?: emptyList<Any>()

            val chClass = channel.javaClass
            val targetUrl = chClass.getMethod("getStreamUrl").invoke(channel) as? String ?: ""
            val targetId = chClass.getMethod("getId").invoke(channel) as? String ?: ""

            // Check if already favorite (removing is always allowed)
            for (item in favList) {
                if (item != null) {
                    val itemUrl = item.javaClass.getMethod("getStreamUrl").invoke(item) as? String ?: ""
                    val itemId = item.javaClass.getMethod("getId").invoke(item) as? String ?: ""
                    if ((targetUrl.isNotEmpty() && targetUrl == itemUrl) || (targetId.isNotEmpty() && targetId == itemId)) {
                        return false
                    }
                }
            }

            if (!PremiumManager.canAddFavorite(favList.size)) {
                launchPaywall()
                return true
            }
        } catch (t: Throwable) {
            t.printStackTrace()
        }
        return false
    }

    @Composable
    @JvmStatic
    fun RenderGoldPremiumBadge() {
        val context = LocalContext.current
        val isPremium by PremiumManager.isPremiumState
        val activePlan by PremiumManager.activePlanState

        val goldBrush = Brush.horizontalGradient(
            colors = listOf(
                Color(0xFFFFD700),
                Color(0xFFF59E0B),
                Color(0xFFFFEA79)
            )
        )

        val bgBrush = Brush.horizontalGradient(
            colors = listOf(
                Color(0xFF2A210B),
                Color(0xFF1F1706),
                Color(0xFF2D220A)
            )
        )

        Surface(
            modifier = Modifier
                .height(32.dp)
                .clip(RoundedCornerShape(16.dp))
                .clickable {
                    if (!isPremium) {
                        SubscriptionActivity.start(context)
                    } else if (activePlan != PremiumManager.PLAN_LIFETIME) {
                        // Allow plan upgrade for monthly/yearly users
                        SubscriptionActivity.start(context)
                    } else {
                        Toast.makeText(context, "👑 Lifetime Premium Active!", Toast.LENGTH_SHORT).show()
                    }
                },
            shape = RoundedCornerShape(16.dp),
            color = Color.Transparent,
            border = BorderStroke(1.2.dp, goldBrush)
        ) {
            Row(
                modifier = Modifier
                    .background(bgBrush)
                    .padding(horizontal = 12.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "👑",
                    fontSize = 13.sp
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (isPremium) "Premium" else "Go Premium",
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.ExtraBold,
                    style = androidx.compose.ui.text.TextStyle(
                        brush = goldBrush
                    )
                )
                if (isPremium && activePlan != PremiumManager.PLAN_LIFETIME) {
                    Spacer(modifier = Modifier.width(5.dp))
                    Box(
                        modifier = Modifier
                            .background(Color(0x33FFD700), RoundedCornerShape(6.dp))
                            .padding(horizontal = 5.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = "UPGRADE",
                            color = Color(0xFFFFD700),
                            fontSize = 8.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }

    @Composable
    @JvmStatic
    fun RenderHomeTopBarCrownAndDailyCheck() {
        val context = LocalContext.current
        val isPremium by PremiumManager.isPremiumState

        LaunchedEffect(Unit) {
            PremiumManager.init(context)
            delay(3500L)
            if (PremiumManager.shouldShowDailyPrompt(context)) {
                PremiumManager.markDailyPromptShown(context)
                SubscriptionActivity.start(context)
            }
        }

        if (isPremium) {
            Text(
                text = "👑",
                fontSize = 14.sp,
                modifier = Modifier
                    .offset(x = 2.dp, y = (-6).dp)
                    .rotate(-18f)
            )
            Spacer(modifier = Modifier.width(2.dp))
        }
    }

    @Composable
    @JvmStatic
    fun RenderDrawerPremiumBanner() {
        val context = LocalContext.current
        val isPremium by PremiumManager.isPremiumState
        val activePlan by PremiumManager.activePlanState

        val goldBrush = Brush.horizontalGradient(
            colors = listOf(
                Color(0xFFFFD700),
                Color(0xFFF59E0B),
                Color(0xFFFFE066)
            )
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            Color(0xFF281F0A),
                            Color(0xFF1A1405),
                            Color(0xFF2C2109)
                        )
                    )
                )
                .border(1.2.dp, goldBrush, RoundedCornerShape(16.dp))
                .clickable {
                    if (!isPremium || activePlan != PremiumManager.PLAN_LIFETIME) {
                        SubscriptionActivity.start(context)
                    } else {
                        Toast.makeText(context, "👑 Lifetime Premium Active!", Toast.LENGTH_SHORT).show()
                    }
                }
                .padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .background(Color(0x33FFD700), CircleShape)
                        .border(1.dp, Color(0xFFFFD700), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "👑",
                        fontSize = 18.sp
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (isPremium) "Premium Member" else "Go Pro / Premium",
                        fontSize = 14.5.sp,
                        fontWeight = FontWeight.ExtraBold,
                        style = androidx.compose.ui.text.TextStyle(brush = goldBrush)
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (isPremium && activePlan != PremiumManager.PLAN_LIFETIME)
                            "Tap to upgrade your plan"
                        else if (isPremium)
                            "All VIP features unlocked"
                        else
                            "No Ads • PiP • Xtream • Unlimited",
                        color = Color(0xFFD4AF37),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                Box(
                    modifier = Modifier
                        .background(goldBrush, RoundedCornerShape(10.dp))
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = if (isPremium) "PRO" else "BUY",
                        color = Color(0xFF141004),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black
                    )
                }
            }
        }
    }
}
