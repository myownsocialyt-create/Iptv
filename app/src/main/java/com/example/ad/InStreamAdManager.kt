package com.example.ad

import android.content.Context
import android.util.Log
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.nativead.NativeAd
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.random.Random

/**
 * Creative information for YouTube-style in-stream video ads.
 */
data class InStreamAdCreative(
    val id: String,
    val title: String,
    val subtitle: String,
    val advertiserName: String,
    val ctaText: String,
    val targetUrl: String,
    val iconEmoji: String = "▶️",
    val accentColorHex: String = "#FF0000"
)

/**
 * Live state of an in-stream ad currently playing over the video.
 */
data class InStreamAdState(
    val isAdActive: Boolean = false,
    val creative: InStreamAdCreative? = null,
    val totalDurationSeconds: Int = 22,
    val remainingSeconds: Int = 22,
    val skipCountdownSeconds: Int = 5,
    val canSkip: Boolean = false
) {
    val progressFraction: Float
        get() = if (totalDurationSeconds > 0) {
            ((totalDurationSeconds - remainingSeconds).toFloat() / totalDurationSeconds.toFloat()).coerceIn(0f, 1f)
        } else 0f
}

/**
 * InStreamAdManager coordinates YouTube-style video ads:
 * - Triggers randomly on channel start (pre-roll, ~50% chance)
 * - Triggers randomly during playback:
 *     - Short intervals: 25-45s (e.g. 2 ads in ~1 minute)
 *     - Medium intervals: 60-120s (~1-2 min)
 *     - Long intervals: 240-330s (~4-5 min)
 * - Emits real-time state for skip countdown ("Skip in 5s" -> "Skip ad ▶|")
 * - Drives the signature yellow progress bar along the bottom of the video player
 * - Preloads official Google AdMob test Native Ads (ca-app-pub-3940256099942544/2247696110)
 */
object InStreamAdManager {
    private const val TAG = "InStreamAdManager"

    // Official Google AdMob Test Ad Unit IDs
    const val TEST_BANNER_AD_UNIT_ID = "ca-app-pub-3940256099942544/6300978111"
    const val TEST_NATIVE_AD_UNIT_ID = "ca-app-pub-3940256099942544/2247696110"
    const val TEST_INTERSTITIAL_AD_UNIT_ID = "ca-app-pub-3940256099942544/1033173712"

    private val scope = CoroutineScope(Dispatchers.Main + Job())

    private val _adState = MutableStateFlow(InStreamAdState())
    val adState: StateFlow<InStreamAdState> = _adState.asStateFlow()

    // Default YouTube Survey Creative modeled after Photos 1 & 2
    val YOUTUBE_SURVEY_CREATIVE = InStreamAdCreative(
        id = "yt_survey_1",
        title = "Help YouTube by answering one question",
        subtitle = "YouTube advertiser survey",
        advertiserName = "Google & YouTube",
        ctaText = "Answer one question",
        targetUrl = "https://www.google.com/ads",
        iconEmoji = "▶️",
        accentColorHex = "#FF0000"
    )

    // Sample Ad Creatives modeled after YouTube Ads
    val SAMPLE_CREATIVES = listOf(
        YOUTUBE_SURVEY_CREATIVE,
        InStreamAdCreative(
            id = "gcloud_1",
            title = "Build & Deploy with Google Cloud",
            subtitle = "Get $300 in free credits for new cloud projects",
            advertiserName = "Google Cloud",
            ctaText = "Start Free Trial",
            targetUrl = "https://cloud.google.com",
            iconEmoji = "☁️",
            accentColorHex = "#4285F4"
        ),
        InStreamAdCreative(
            id = "android_dev_1",
            title = "Create Beautiful Modern Apps with Compose",
            subtitle = "Android Jetpack - Faster development, native performance",
            advertiserName = "Android Developers",
            ctaText = "Learn Compose",
            targetUrl = "https://developer.android.com",
            iconEmoji = "🤖",
            accentColorHex = "#3DDC84"
        ),
        InStreamAdCreative(
            id = "hypnotix_pro_1",
            title = "Hypnotix Ultra HD Live Player",
            subtitle = "Ad-free experience, multi-screen streaming and cloud favorites",
            advertiserName = "Hypnotix Media",
            ctaText = "Upgrade to Pro",
            targetUrl = "https://github.com",
            iconEmoji = "⚡",
            accentColorHex = "#E50914"
        ),
        InStreamAdCreative(
            id = "yt_premium_1",
            title = "YouTube Premium • Ad-free & Background Play",
            subtitle = "Download videos and listen with screen off",
            advertiserName = "YouTube",
            ctaText = "Try 1 Month Free",
            targetUrl = "https://youtube.com/premium",
            iconEmoji = "🎬",
            accentColorHex = "#FF0033"
        )
    )

    private var countdownJob: Job? = null
    private var randomAdScheduleJob: Job? = null
    private var channelStartJob: Job? = null
    private var lastAdDismissedTime = 0L
    private var isPlayerActive = false
    private var currentChannelUrl: String? = null
    private var appContext: Context? = null

    private var adMobCreative: InStreamAdCreative? = null
    private var isAdMobLoading = false

    /**
     * Preloads an AdMob Native Ad using the official Google AdMob test native ad unit ID.
     */
    fun preloadAdMobNativeAd(context: Context) {
        appContext = context.applicationContext
        if (isAdMobLoading) return
        isAdMobLoading = true

        try {
            val adLoader = AdLoader.Builder(context.applicationContext, TEST_NATIVE_AD_UNIT_ID)
                .forNativeAd { nativeAd: NativeAd ->
                    isAdMobLoading = false
                    val headline = nativeAd.headline?.takeIf { it.isNotBlank() }
                        ?: YOUTUBE_SURVEY_CREATIVE.title
                    val body = nativeAd.body?.takeIf { it.isNotBlank() }
                        ?: YOUTUBE_SURVEY_CREATIVE.subtitle
                    val cta = nativeAd.callToAction?.takeIf { it.isNotBlank() }
                        ?: YOUTUBE_SURVEY_CREATIVE.ctaText
                    val advertiser = nativeAd.advertiser?.takeIf { it.isNotBlank() }
                        ?: YOUTUBE_SURVEY_CREATIVE.advertiserName

                    adMobCreative = InStreamAdCreative(
                        id = "admob_native_${System.currentTimeMillis()}",
                        title = headline,
                        subtitle = body,
                        advertiserName = advertiser,
                        ctaText = cta,
                        targetUrl = "https://www.google.com/ads",
                        iconEmoji = "▶️",
                        accentColorHex = "#FF0000"
                    )
                    Log.d(TAG, "AdMob Native Test Ad loaded: $headline")
                }
                .withAdListener(object : AdListener() {
                    override fun onAdFailedToLoad(error: LoadAdError) {
                        isAdMobLoading = false
                        Log.w(TAG, "AdMob Native Ad failed to load (${error.code}): ${error.message}")
                    }
                })
                .build()

            adLoader.loadAd(AdRequest.Builder().build())
        } catch (e: Exception) {
            isAdMobLoading = false
            Log.w(TAG, "Exception while loading AdMob Native Ad", e)
        }
    }

    /**
     * Called when channel playback is initiated.
     * "kbhi channel start hote he aa jaye":
     * ~50% chance to show a preroll ad immediately after a brief buffer.
     */
    fun onChannelStarted(channelStreamUrl: String, context: Context? = null) {
        if (context != null) {
            appContext = context.applicationContext
            preloadAdMobNativeAd(context)
        }

        // Avoid re-triggering preroll on orientation change or fullscreen toggle if same channel is already active
        if (channelStreamUrl == currentChannelUrl && isPlayerActive) {
            return
        }
        currentChannelUrl = channelStreamUrl

        channelStartJob?.cancel()
        channelStartJob = scope.launch {
            // Brief 600ms buffer so ExoPlayer initializes smoothly
            delay(600L)

            val now = System.currentTimeMillis()
            val timeSinceLastAd = now - lastAdDismissedTime

            // Allow preroll if no ad is currently playing and at least 15s since last ad
            if (!_adState.value.isAdActive && (lastAdDismissedTime == 0L || timeSinceLastAd >= 15_000L)) {
                val shouldShowStartAd = Random.nextFloat() < 0.50f
                if (shouldShowStartAd) {
                    Log.d(TAG, "Preroll YouTube ad triggered on channel start")
                    triggerInStreamAd()
                }
            }
        }
    }

    /**
     * Starts or syncs the periodic random ad schedule while video is playing.
     * Matches the user requirement:
     * "random kbhi 2 ads 1 he min. m aa rhe h kbhi 5 min. pr 1 ads random mtlb ads kbhi bhi show ho skta h"
     */
    fun updatePlaybackStatus(isPlaying: Boolean, context: Context? = null) {
        isPlayerActive = isPlaying
        if (context != null) {
            appContext = context.applicationContext
            if (adMobCreative == null) {
                preloadAdMobNativeAd(context)
            }
        }

        if (isPlaying) {
            if (randomAdScheduleJob == null || randomAdScheduleJob?.isActive == false) {
                startRandomAdLoop()
            }
        }
    }

    private fun startRandomAdLoop() {
        randomAdScheduleJob?.cancel()
        randomAdScheduleJob = scope.launch {
            while (true) {
                // Randomized intervals matching user description:
                // Tier 1 (35%): Short interval (25 to 45s) -> allows 2 ads in ~1 minute
                // Tier 2 (35%): Medium interval (60 to 120s) -> ~1 to 2 min
                // Tier 3 (30%): Long interval (240 to 330s) -> ~4 to 5.5 minutes!
                val roll = Random.nextInt(100)
                val randomSeconds = when {
                    roll < 35 -> Random.nextInt(25, 46)
                    roll < 70 -> Random.nextInt(60, 121)
                    else -> Random.nextInt(240, 331)
                }
                Log.d(TAG, "Next random in-stream ad scheduled in $randomSeconds seconds")

                // Tick second by second so pauses or brief buffering don't reset the schedule
                var elapsedActiveSeconds = 0
                while (elapsedActiveSeconds < randomSeconds) {
                    delay(1000L)
                    if (isPlayerActive && !_adState.value.isAdActive) {
                        elapsedActiveSeconds++
                    }
                }

                if (isPlayerActive && !_adState.value.isAdActive) {
                    val timeSinceLastAd = System.currentTimeMillis() - lastAdDismissedTime
                    // Minimum safety gap of 15 seconds after dismissing previous ad
                    if (lastAdDismissedTime == 0L || timeSinceLastAd >= 15_000L) {
                        Log.d(TAG, "Triggering scheduled in-stream ad during live stream")
                        triggerInStreamAd()
                    }
                }
            }
        }
    }

    /**
     * Manually or automatically trigger an in-stream video ad.
     * Priority:
     * 1. 50% chance to show the exact YouTube survey ad from Photos 1 & 2 ("Help YouTube by answering one question")
     * 2. AdMob loaded native test ad or other YouTube creatives.
     */
    fun triggerInStreamAd(customCreative: InStreamAdCreative? = null) {
        if (_adState.value.isAdActive) return

        val creative = customCreative ?: run {
            if (Random.nextBoolean()) {
                YOUTUBE_SURVEY_CREATIVE
            } else {
                adMobCreative ?: SAMPLE_CREATIVES.random()
            }
        }

        // 22 seconds matches the exact "0:22" displayed in user Reference Photos 1 & 2
        val totalDuration = if (creative.id == YOUTUBE_SURVEY_CREATIVE.id) 22 else 15
        val skipCountdown = 5 // 5 seconds before skip ad button activates

        _adState.value = InStreamAdState(
            isAdActive = true,
            creative = creative,
            totalDurationSeconds = totalDuration,
            remainingSeconds = totalDuration,
            skipCountdownSeconds = skipCountdown,
            canSkip = false
        )

        countdownJob?.cancel()
        countdownJob = scope.launch {
            for (sec in 0 until totalDuration) {
                delay(1000L)
                val current = _adState.value
                if (!current.isAdActive) break

                val newRemaining = (current.remainingSeconds - 1).coerceAtLeast(0)
                val newSkipCount = (current.skipCountdownSeconds - 1).coerceAtLeast(0)
                val canSkipNow = newSkipCount <= 0

                _adState.value = current.copy(
                    remainingSeconds = newRemaining,
                    skipCountdownSeconds = newSkipCount,
                    canSkip = canSkipNow
                )

                if (newRemaining <= 0) {
                    dismissAd()
                    break
                }
            }
        }
    }

    /**
     * User tapped the "Skip ad ▶|" button or ad finished.
     */
    fun dismissAd() {
        if (!_adState.value.isAdActive) return
        countdownJob?.cancel()
        countdownJob = null
        lastAdDismissedTime = System.currentTimeMillis()
        _adState.value = InStreamAdState(isAdActive = false)
        Log.d(TAG, "In-stream ad dismissed, resuming live channel stream")

        // Preload next AdMob native test ad for next round
        appContext?.let { ctx ->
            preloadAdMobNativeAd(ctx)
        }
    }

    /**
     * Called when the video player is completely closed by the user.
     */
    fun onPlayerClosed() {
        isPlayerActive = false
        randomAdScheduleJob?.cancel()
        randomAdScheduleJob = null
        channelStartJob?.cancel()
        channelStartJob = null
        dismissAd()
        currentChannelUrl = null
    }
}
