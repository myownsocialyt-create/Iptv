package com.example.ad

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import android.util.Log
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.VideoOptions
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.random.Random

fun Context.findActivity(): Activity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}

/**
 * Creative details for in-stream ads.
 */
data class InStreamAdCreative(
    val id: String = "admob_real",
    val title: String = "",
    val subtitle: String = "",
    val advertiserName: String = "",
    val ctaText: String = "Open",
    val targetUrl: String = ""
)

/**
 * Live state of in-stream ad overlay.
 */
data class InStreamAdState(
    val isAdActive: Boolean = false,
    val creative: InStreamAdCreative? = null,
    val totalDurationSeconds: Int = 16,
    val remainingSeconds: Int = 16,
    val skipCountdownSeconds: Int = 5,
    val canSkip: Boolean = false
) {
    val progressFraction: Float
        get() = if (totalDurationSeconds > 0) {
            ((totalDurationSeconds - remainingSeconds).toFloat() / totalDurationSeconds.toFloat()).coerceIn(0f, 1f)
        } else 0f
}

/**
 * InStreamAdManager coordinates 100% REAL Google AdMob Ads for video playback:
 * 1. AdMob Interstitial Video Ads (ca-app-pub-3940256099942544/1033173712) for authentic full-screen Google test video ads.
 * 2. AdMob Native Video Ads (ca-app-pub-3940256099942544/2247696110) with MediaView for in-player native ad playback.
 * 3. Zero fake canvas doodles, zero fake static posters, zero "Loading test id" text.
 */
object InStreamAdManager {
    private const val TAG = "InStreamAdManager"

    // Official Google AdMob Interstitial Video Test Ad Unit ID
    const val TEST_INTERSTITIAL_AD_UNIT_ID = "ca-app-pub-3940256099942544/1033173712"

    // Official Google AdMob Native Advanced Test Ad Unit ID
    const val TEST_NATIVE_AD_UNIT_ID = "ca-app-pub-3940256099942544/2247696110"

    private val scope = CoroutineScope(Dispatchers.Main + Job())

    private val _adState = MutableStateFlow(InStreamAdState())
    val adState: StateFlow<InStreamAdState> = _adState.asStateFlow()

    // Real NativeAd instance rendered via MediaView in the in-stream overlay
    var activeNativeAd: NativeAd? = null
    private val _activeNativeAdFlow = MutableStateFlow<NativeAd?>(null)
    val activeNativeAdFlow: StateFlow<NativeAd?> = _activeNativeAdFlow.asStateFlow()

    private var preloadedInterstitialAd: InterstitialAd? = null
    private var isInterstitialLoading = false

    private var preloadedNativeAd: NativeAd? = null
    private var isNativeLoading = false

    private var countdownJob: Job? = null
    private var midrollScheduleJob: Job? = null
    private var lastAdDismissedTime = 0L

    private var onAdStartedCallback: (() -> Unit)? = null
    private var onAdEndedCallback: (() -> Unit)? = null

    // Channel switch counting: triggers reliably after 2 to 3 channel switches with cooldown
    private var channelSwitchCount = 0
    private var switchesUntilNextAd = 2
    private var hasPlayedFirstChannel = false
    private var currentPlayingStreamUrl: String? = null
    private var suppressedUntilTime = 0L
    private const val MIN_CHANNEL_SWITCH_AD_COOLDOWN_MS = 60_000L

    /**
     * Temporarily suppresses ads for layout, orientation, or PiP transitions.
     */
    fun suppressAdsTemporarily(durationMs: Long = 3000L) {
        suppressedUntilTime = System.currentTimeMillis() + durationMs
        Log.d(TAG, "Ads temporarily suppressed for ${durationMs}ms (orientation / PiP transition)")
    }

    /**
     * Checks if the activity is currently in Picture-in-Picture mode.
     */
    fun isPipActive(activity: Activity?): Boolean {
        if (activity == null) return false
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            activity.isInPictureInPictureMode
        } else {
            false
        }
    }

    /**
     * Preloads real AdMob ads in the background.
     */
    fun preloadAdMobNativeAd(context: Context) {
        preloadInterstitial(context)
        preloadNative(context)
    }

    private fun preloadInterstitial(context: Context) {
        if (preloadedInterstitialAd != null || isInterstitialLoading) return
        val activity = context.findActivity() ?: return
        isInterstitialLoading = true

        try {
            val adRequest = AdRequest.Builder().build()
            InterstitialAd.load(
                activity,
                TEST_INTERSTITIAL_AD_UNIT_ID,
                adRequest,
                object : InterstitialAdLoadCallback() {
                    override fun onAdLoaded(ad: InterstitialAd) {
                        isInterstitialLoading = false
                        preloadedInterstitialAd = ad
                        Log.d(TAG, "AdMob Real Interstitial Video Ad preloaded successfully")
                    }

                    override fun onAdFailedToLoad(error: LoadAdError) {
                        isInterstitialLoading = false
                        preloadedInterstitialAd = null
                        Log.w(TAG, "AdMob Interstitial failed to load (${error.code}): ${error.message}")
                    }
                }
            )
        } catch (e: Exception) {
            isInterstitialLoading = false
            Log.e(TAG, "Error preloading interstitial ad", e)
        }
    }

    private fun preloadNative(context: Context) {
        if (preloadedNativeAd != null || isNativeLoading) return
        val activity = context.findActivity() ?: return
        isNativeLoading = true

        try {
            val videoOptions = VideoOptions.Builder()
                .setStartMuted(false)
                .build()

            val adOptions = NativeAdOptions.Builder()
                .setVideoOptions(videoOptions)
                .setMediaAspectRatio(NativeAdOptions.NATIVE_MEDIA_ASPECT_RATIO_LANDSCAPE)
                .setAdChoicesPlacement(NativeAdOptions.ADCHOICES_TOP_RIGHT)
                .build()

            val adLoader = AdLoader.Builder(activity, TEST_NATIVE_AD_UNIT_ID)
                .forNativeAd { nativeAd ->
                    isNativeLoading = false
                    preloadedNativeAd = nativeAd
                    Log.d(TAG, "AdMob Real Native Video Ad preloaded successfully: ${nativeAd.headline}")
                }
                .withAdListener(object : AdListener() {
                    override fun onAdFailedToLoad(error: LoadAdError) {
                        isNativeLoading = false
                        preloadedNativeAd = null
                        Log.w(TAG, "AdMob Native failed to load (${error.code}): ${error.message}")
                    }
                })
                .withNativeAdOptions(adOptions)
                .build()

            adLoader.loadAd(AdRequest.Builder().build())
        } catch (e: Exception) {
            isNativeLoading = false
            Log.e(TAG, "Error preloading native ad", e)
        }
    }

    /**
     * Called whenever a channel is played or switched.
     * Checks stream URL to prevent spurious ad triggers on orientation or PiP changes.
     */
    fun onChannelSwitched(
        context: Context,
        streamUrl: String = "",
        onStartAd: () -> Unit = {},
        onEndAd: () -> Unit = {}
    ) {
        val now = System.currentTimeMillis()

        // 1. If currently suppressed due to landscape/portrait or PiP transition, ignore
        if (now < suppressedUntilTime) {
            Log.d(TAG, "Channel switch ignored because ads are currently suppressed for layout transition")
            return
        }

        // 2. If in Picture-in-Picture mode, strictly do not show ads
        val activity = context.findActivity()
        if (activity != null && isPipActive(activity)) {
            Log.d(TAG, "Channel switch ignored because app is in PiP mode")
            return
        }

        // 3. If stream is the SAME as already playing, it's NOT a channel switch (e.g. recomposition, rotation, pip)
        if (streamUrl.isNotEmpty() && currentPlayingStreamUrl != null && currentPlayingStreamUrl == streamUrl) {
            Log.d(TAG, "Same stream already playing, skipping ad logic ($streamUrl)")
            return
        }

        if (streamUrl.isNotEmpty()) {
            currentPlayingStreamUrl = streamUrl
        }
        onAdStartedCallback = onStartAd
        onAdEndedCallback = onEndAd

        preloadAdMobNativeAd(context)

        // 4. Initial channel play vs subsequent channel switches:
        if (!hasPlayedFirstChannel) {
            hasPlayedFirstChannel = true
            channelSwitchCount = 0
            // Pick target for upcoming channel switches: 2 or 3 switches
            switchesUntilNextAd = Random.nextInt(2, 4)
            lastAdDismissedTime = System.currentTimeMillis() // Start cooldown timer from initial play
            Log.d(TAG, "First channel started: Next ad will trigger on switch #$switchesUntilNextAd")
            // Do NOT interrupt the initial stream play with an ad
            schedulePeriodicVideoAd(context)
            return
        }

        // 5. Subsequent channel switches (from channel list or category folder):
        channelSwitchCount++
        Log.d(TAG, "Channel switch registered (#$channelSwitchCount / target: $switchesUntilNextAd)")

        val timeSinceLastAd = if (lastAdDismissedTime > 0L) (now - lastAdDismissedTime) else Long.MAX_VALUE
        val isCooldownPassed = timeSinceLastAd >= MIN_CHANNEL_SWITCH_AD_COOLDOWN_MS

        // Trigger ad ONLY when both switch target count AND cooldown timing have passed
        if (channelSwitchCount >= switchesUntilNextAd && isCooldownPassed) {
            val triggeredTarget = switchesUntilNextAd
            channelSwitchCount = 0
            // Next ad target randomly chosen between 2 or 3 switches
            switchesUntilNextAd = Random.nextInt(2, 4)
            lastAdDismissedTime = System.currentTimeMillis()
            Log.d(TAG, "Target reached ($triggeredTarget switches) & cooldown satisfied. Showing ad! Next target: switch #$switchesUntilNextAd")

            scope.launch {
                delay(300L)
                val act = context.findActivity()
                if (act == null || isPipActive(act) || System.currentTimeMillis() < suppressedUntilTime) {
                    return@launch
                }

                if (preloadedInterstitialAd != null) {
                    showRealInterstitialAd(act, onStartAd, onEndAd)
                } else {
                    fetchAndShowRealInterstitial(act, onStartAd, onEndAd)
                }
            }
        }

        // Reschedule periodic mid-roll full screen ad for 5 to 10 minutes
        schedulePeriodicVideoAd(context)
    }

    private fun showRealInterstitialAd(
        activity: Activity,
        onStartAd: () -> Unit,
        onEndAd: () -> Unit
    ) {
        if (isPipActive(activity) || System.currentTimeMillis() < suppressedUntilTime) {
            Log.d(TAG, "Suppressed showing interstitial: PiP active or in orientation transition")
            return
        }
        val ad = preloadedInterstitialAd ?: return
        preloadedInterstitialAd = null

        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdShowedFullScreenContent() {
                Log.d(TAG, "AdMob Real Interstitial Ad is showing on screen")
            }

            override fun onAdDismissedFullScreenContent() {
                Log.d(TAG, "AdMob Real Interstitial Ad dismissed by user")
                lastAdDismissedTime = System.currentTimeMillis()
                preloadInterstitial(activity)
                onEndAd()
            }

            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                Log.e(TAG, "AdMob Real Interstitial failed to show: ${error.message}")
                preloadInterstitial(activity)
                onEndAd()
            }
        }

        try {
            onStartAd()
            ad.show(activity)
        } catch (e: Exception) {
            Log.e(TAG, "Error showing AdMob Interstitial Ad", e)
            onEndAd()
        }
    }

    private fun fetchAndShowRealInterstitial(
        activity: Activity,
        onStartAd: () -> Unit,
        onEndAd: () -> Unit
    ) {
        if (isPipActive(activity) || System.currentTimeMillis() < suppressedUntilTime) {
            return
        }
        isInterstitialLoading = true
        InterstitialAd.load(
            activity,
            TEST_INTERSTITIAL_AD_UNIT_ID,
            AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    isInterstitialLoading = false
                    preloadedInterstitialAd = ad
                    if (!isPipActive(activity) && System.currentTimeMillis() >= suppressedUntilTime) {
                        showRealInterstitialAd(activity, onStartAd, onEndAd)
                    }
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    isInterstitialLoading = false
                    Log.w(TAG, "Immediate interstitial load failed (${error.code}): ${error.message}")
                }
            }
        )
    }

    private fun showRealNativeAdOverlay(context: Context) {
        if (_adState.value.isAdActive) return
        val ad = preloadedNativeAd ?: return
        preloadedNativeAd = null

        activeNativeAd = ad
        _activeNativeAdFlow.value = ad

        val totalDuration = 16
        val skipCountdown = 5

        _adState.value = InStreamAdState(
            isAdActive = true,
            creative = InStreamAdCreative(
                title = ad.headline ?: "",
                subtitle = ad.body ?: "",
                advertiserName = ad.advertiser ?: "",
                ctaText = ad.callToAction ?: "Open"
            ),
            totalDurationSeconds = totalDuration,
            remainingSeconds = totalDuration,
            skipCountdownSeconds = skipCountdown,
            canSkip = false
        )

        try {
            onAdStartedCallback?.invoke()
        } catch (_: Exception) {}

        countdownJob?.cancel()
        countdownJob = scope.launch {
            for (sec in 0 until totalDuration) {
                delay(1000L)
                val current = _adState.value
                if (!current.isAdActive) break

                val newRemaining = (current.remainingSeconds - 1).coerceAtLeast(0)
                val newSkip = (current.skipCountdownSeconds - 1).coerceAtLeast(0)
                val canSkipNow = newSkip <= 0

                _adState.value = current.copy(
                    remainingSeconds = newRemaining,
                    skipCountdownSeconds = newSkip,
                    canSkip = canSkipNow
                )

                if (newRemaining <= 0) {
                    dismissAd()
                    break
                }
            }
        }

        preloadNative(context)
    }

    /**
     * Schedules periodic full-screen interstitial ad while video is playing (random 5 to 10 minutes).
     */
    fun schedulePeriodicVideoAd(context: Context) {
        midrollScheduleJob?.cancel()
        midrollScheduleJob = scope.launch {
            // Random delay between 5 and 10 minutes (300 to 600 seconds)
            val delaySeconds = Random.nextInt(300, 601)
            Log.d(TAG, "Periodic full-screen ad scheduled in $delaySeconds seconds (~${delaySeconds / 60} mins)")
            delay(delaySeconds * 1000L)

            // If player was closed in the meantime, abort
            if (currentPlayingStreamUrl == null) {
                Log.d(TAG, "Periodic ad cancelled because video playback ended")
                return@launch
            }

            val activity = context.findActivity()
            if (activity == null || isPipActive(activity) || System.currentTimeMillis() < suppressedUntilTime) {
                Log.d(TAG, "Periodic ad postponed due to PiP, transition, or inactive activity")
                delay(60_000L)
                schedulePeriodicVideoAd(context)
                return@launch
            }

            // Cooldown check (don't show if another ad was shown within last 60 seconds)
            val now = System.currentTimeMillis()
            if (lastAdDismissedTime > 0L && (now - lastAdDismissedTime) < 60_000L) {
                schedulePeriodicVideoAd(context)
                return@launch
            }

            Log.d(TAG, "Triggering periodic full-screen ad after 5-10 min playback")
            if (preloadedInterstitialAd != null) {
                showRealInterstitialAd(
                    activity,
                    onStartAd = { onAdStartedCallback?.invoke() },
                    onEndAd = {
                        onAdEndedCallback?.invoke()
                        schedulePeriodicVideoAd(context)
                    }
                )
            } else {
                fetchAndShowRealInterstitial(
                    activity,
                    onStartAd = { onAdStartedCallback?.invoke() },
                    onEndAd = {
                        onAdEndedCallback?.invoke()
                        schedulePeriodicVideoAd(context)
                    }
                )
            }
        }
    }

    fun scheduleMidRollAd(context: Context) {
        schedulePeriodicVideoAd(context)
    }

    /**
     * User tapped the "Skip ad ▶|" button or ad completed.
     */
    fun dismissAd(onResumePlayback: () -> Unit = {}) {
        if (!_adState.value.isAdActive) return
        countdownJob?.cancel()
        countdownJob = null
        lastAdDismissedTime = System.currentTimeMillis()

        activeNativeAd?.destroy()
        activeNativeAd = null
        _activeNativeAdFlow.value = null

        _adState.value = InStreamAdState(isAdActive = false)
        Log.d(TAG, "In-stream native ad dismissed, resuming live playback")

        try {
            onAdEndedCallback?.invoke()
        } catch (_: Exception) {}

        try {
            onResumePlayback()
        } catch (_: Exception) {}
    }

    fun onPlayerClosed() {
        currentPlayingStreamUrl = null
        hasPlayedFirstChannel = false
        countdownJob?.cancel()
        countdownJob = null
        midrollScheduleJob?.cancel()
        midrollScheduleJob = null
        _adState.value = InStreamAdState(isAdActive = false)
        activeNativeAd?.destroy()
        activeNativeAd = null
        _activeNativeAdFlow.value = null
        preloadedNativeAd?.destroy()
        preloadedNativeAd = null
        preloadedInterstitialAd = null
    }
}
