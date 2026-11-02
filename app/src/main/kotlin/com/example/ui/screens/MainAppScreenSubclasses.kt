package com.example.ui.screens

import android.app.Activity
import android.content.Context
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.runtime.Composer
import androidx.compose.runtime.MutableState
import com.example.ui.components.NavigationItem
import com.example.ui.i18n.AppStrings
import com.example.ui.theme.AppColors
import com.example.ui.viewmodel.IptvViewModel
import kotlin.jvm.functions.Function0
import kotlin.jvm.functions.Function3

@Suppress("ClassName")
class `HomeBannerCarouselKt$$ExternalSyntheticLambda12`(
    @JvmField val `f$0`: MutableState<Any?>
) : Function0<Any?> {
    override fun invoke(): Any? {
        GlobalSearchActivity.start()
        return kotlin.Unit
    }
}

@Suppress("ClassName")
class `MainAppScreenKt$MainAppScreen$25$1$1$$ExternalSyntheticLambda1`(
    @JvmField val `f$0`: MutableState<NavigationItem>
) : Function0<Any?> {
    override fun invoke(): Any? {
        val current = MainAppScreenKt.`access$MainAppScreen$lambda$12`(`f$0`)
        if (current == NavigationItem.PLAYLISTS) {
            PlaylistTopSearchHelper.showSearchDialog()
        } else if (current == NavigationItem.HOME) {
            GlobalSearchActivity.start()
        } else if (current != NavigationItem.CHANNELS) {
            MainAppScreenKt.`access$MainAppScreen$lambda$13`(`f$0`, NavigationItem.CHANNELS)
        }
        return kotlin.Unit
    }
}

@Suppress("ClassName")
class `MainAppScreenKt$$ExternalSyntheticLambda5`(
    @JvmField val `f$0`: IptvViewModel,
    @JvmField val `f$1`: Context,
    @JvmField val `f$2`: MutableState<Boolean>
) : Function0<Any?> {
    override fun invoke(): Any? {
        `f$2`.value = false
        try {
            `f$0`::class.java.getMethod("closePlayer").invoke(`f$0`)
        } catch (_: Throwable) {}

        val activity = (`f$1` as? Activity)
            ?: GlobalSearchActivity.getActiveActivity()
            ?: (try {
                val adManagerClass = Class.forName("com.example.ad.AdManager")
                val curActField = adManagerClass.getDeclaredField("currentActivity")
                curActField.isAccessible = true
                curActField.get(null) as? Activity
            } catch (_: Throwable) { null })

        activity?.finishAffinity()
        activity?.finish()
        return kotlin.Unit
    }
}

object PlaylistAdHelper {
    private var lastPlaylistAdTime = 0L
    private const val COOLDOWN_MS = 6 * 60 * 1000L // 6 minutes = 360,000 ms

    fun checkAndShowPlaylistAd(activity: Activity? = null) {
        val now = System.currentTimeMillis()
        if (now - lastPlaylistAdTime < COOLDOWN_MS) {
            return
        }

        val act = activity ?: GlobalSearchActivity.getActiveActivity() ?: (try {
            val adManagerClass = Class.forName("com.example.ad.AdManager")
            val curActField = adManagerClass.getDeclaredField("currentActivity")
            curActField.isAccessible = true
            curActField.get(null) as? Activity
        } catch (_: Throwable) { null }) ?: return

        lastPlaylistAdTime = now

        act.runOnUiThread {
            try {
                val adManagerClass = Class.forName("com.example.ad.AdManager")
                val instanceField = adManagerClass.getDeclaredField("INSTANCE")
                val adManager = instanceField.get(null)

                // Ensure finishAfterInterstitial is set to false so the app remains running
                try {
                    val finishField = adManagerClass.getDeclaredField("finishAfterInterstitial")
                    finishField.isAccessible = true
                    finishField.setBoolean(null, false)
                } catch (_: Throwable) {}

                // Sync lastPlaylistSwitchAdTime in AdManager
                try {
                    val timeField = adManagerClass.getDeclaredField("lastPlaylistSwitchAdTime")
                    timeField.isAccessible = true
                    timeField.setLong(null, now)
                } catch (_: Throwable) {}

                val showMethod = adManagerClass.getMethod(
                    "showExitInterstitial",
                    Activity::class.java,
                    Function0::class.java
                )
                showMethod.invoke(adManager, act, null)
            } catch (t: Throwable) {
                t.printStackTrace()
            }
        }
    }
}

@Suppress("ClassName")
class `PlaylistsScreenKt$PlaylistsScreen$3$1$1`(
    val appColors: AppColors,
    val searchQuery: String,
    val onSearchQueryChange: (String) -> Unit,
    val strings: AppStrings
) : Function3<LazyItemScope, Composer, Int, Unit> {
    init {
        PlaylistTopSearchHelper.bind(appColors, searchQuery, onSearchQueryChange, strings)
        PlaylistAdHelper.checkAndShowPlaylistAd()
    }

    override fun invoke(scope: LazyItemScope, composer: Composer, changed: Int) {
        // Lower search bar is removed from Playlists list.
    }
}
