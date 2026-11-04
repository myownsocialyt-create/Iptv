package com.example.premium

import android.app.Activity
import android.content.Context
import android.util.Log
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

// Do not enable minify without keep rules for reflected ViewModel/Channel getters.
object PremiumUiHooks {

    private const val TAG = "PremiumUiHooks"

    private var startupBillingHelper: BillingHelper? = null

    @Volatile
    private var cachedPlaylistCount: Int = -1

    @Volatile
    private var cachedFavoriteCount: Int = -1

    private fun safeCall(target: Any?, methodName: String): Any? {
        if (target == null) return null
        val clazz = target.javaClass
        return try {
            val pubMethod = try {
                clazz.getMethod(methodName)
            } catch (_: NoSuchMethodException) {
                null
            }
            if (pubMethod != null) {
                pubMethod.invoke(target)
            } else {
                var currentClass: Class<*>? = clazz
                var foundMethod: java.lang.reflect.Method? = null
                while (currentClass != null && foundMethod == null) {
                    foundMethod = currentClass.declaredMethods.firstOrNull {
                        it.name == methodName && it.parameterTypes.isEmpty()
                    }
                    currentClass = currentClass.superclass
                }
                if (foundMethod != null) {
                    foundMethod.isAccessible = true
                    foundMethod.invoke(target)
                } else {
                    Log.e(TAG, "Method $methodName not found on ${clazz.name}")
                    null
                }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "safeCall failed for ${clazz.name}.$methodName", t)
            null
        }
    }

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
            Log.e(TAG, "Error in onMainActivityCreate", t)
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
            val currentSectionFlow = safeCall(viewModel, "getCurrentSection") as? StateFlow<*>
            val sectionName = currentSectionFlow?.value?.toString() ?: ""

            val streamUrlObj = safeCall(channel, "getStreamUrl")
            val channelIdObj = safeCall(channel, "getId")

            if (currentSectionFlow == null && streamUrlObj == null && channelIdObj == null) {
                Log.e(TAG, "shouldBlockPlayChannel: could not determine channel type; allowing playback")
                return false
            }

            val streamUrl = streamUrlObj as? String ?: ""
            val channelId = channelIdObj as? String ?: ""

            var isXtream = sectionName.equals("XTREAM", ignoreCase = true) ||
                channelId.startsWith("xtream_", ignoreCase = true)

            if (!isXtream) {
                val xtreamList = (safeCall(viewModel, "getXtreamChannels") as? StateFlow<*>)?.value as? Iterable<*>
                if (xtreamList != null) {
                    for (item in xtreamList) {
                        if (item != null) {
                            val itemUrl = safeCall(item, "getStreamUrl") as? String
                            if (itemUrl == streamUrl && streamUrl.isNotEmpty()) {
                                isXtream = true
                                break
                            }
                        }
                    }
                }
            }

            if (!isXtream) {
                val vodList = (safeCall(viewModel, "getXtreamVodChannels") as? StateFlow<*>)?.value as? Iterable<*>
                if (vodList != null) {
                    for (item in vodList) {
                        if (item != null) {
                            val itemUrl = safeCall(item, "getStreamUrl") as? String
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
            Log.e(TAG, "Error in shouldBlockPlayChannel; allowing playback", t)
        }
        return false
    }

    @JvmStatic
    fun shouldBlockAddPlaylist(viewModel: Any): Boolean {
        if (PremiumManager.isPremium()) {
            return false
        }
        try {
            val customFlow = safeCall(viewModel, "getCustomPlaylists") as? StateFlow<*>
            val list = customFlow?.value as? Collection<*>
            if (list != null) {
                val currentCount = list.size
                cachedPlaylistCount = currentCount
                if (!PremiumManager.canAddPlaylist(currentCount)) {
                    launchPaywall()
                    return true
                }
                return false
            } else {
                Log.e(TAG, "shouldBlockAddPlaylist: failed to read customPlaylists via reflection")
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Error in shouldBlockAddPlaylist", t)
        }

        val fallbackCount = cachedPlaylistCount
        if (fallbackCount >= 0) {
            if (!PremiumManager.canAddPlaylist(fallbackCount)) {
                launchPaywall()
                return true
            }
        } else {
            Log.e(TAG, "shouldBlockAddPlaylist: no cached playlist count available; allowing action")
        }
        return false
    }

    @JvmStatic
    fun shouldBlockToggleFavorite(viewModel: Any, channel: Any): Boolean {
        if (PremiumManager.isPremium()) {
            return false
        }
        try {
            val favFlow = safeCall(viewModel, "getFavorites") as? StateFlow<*>
            val favList = favFlow?.value as? Collection<*>
            if (favList != null) {
                cachedFavoriteCount = favList.size

                val targetUrl = safeCall(channel, "getStreamUrl") as? String ?: ""
                val targetId = safeCall(channel, "getId") as? String ?: ""

                // Check if already favorite (removing is always allowed)
                for (item in favList) {
                    if (item != null) {
                        val itemUrl = safeCall(item, "getStreamUrl") as? String ?: ""
                        val itemId = safeCall(item, "getId") as? String ?: ""
                        if ((targetUrl.isNotEmpty() && targetUrl == itemUrl) ||
                            (targetId.isNotEmpty() && targetId == itemId)
                        ) {
                            return false
                        }
                    }
                }

                if (!PremiumManager.canAddFavorite(favList.size)) {
                    launchPaywall()
                    return true
                }
                return false
            } else {
                Log.e(TAG, "shouldBlockToggleFavorite: failed to read favorites via reflection")
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Error in shouldBlockToggleFavorite", t)
        }

        val fallbackCount = cachedFavoriteCount
        if (fallbackCount >= 0) {
            if (!PremiumManager.canAddFavorite(fallbackCount)) {
                launchPaywall()
                return true
            }
        } else {
            Log.e(TAG, "shouldBlockToggleFavorite: no cached favorite count available; allowing action")
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
