package com.example.ad

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.VideoOptions
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdOptions
import kotlin.random.Random

/**
 * Creative information for YouTube-style in-stream video ads and poster ads.
 */
data class VideoAdCreative(
    val id: String,
    val title: String,
    val advertiser: String,
    val description: String,
    val ctaText: String,
    val posterUrl: String,
    val videoUrl: String? = null,
    val iconUrl: String,
    val rating: String = "4.8 ★",
    val destinationUrl: String = "https://play.google.com",
    val isSurveyType: Boolean = false
)

/**
 * InStreamVideoAdManager manages YouTube-style video & poster ads that appear over the video player.
 *
 * Requirements:
 * 1. Channel Switch Frequency:
 *    - Ads trigger randomly on 2nd or 3rd channel switch ("kbhi 2nd time channel switch kbhi 3rd time").
 *    - Does not trigger repeatedly on same channel or orientation/fullscreen recomposition.
 * 2. Never Black Screen:
 *    - Instant HD poster art / cinematic backdrop rendered immediately (0ms delay).
 *    - Fast reliable MP4 commercial trailer video clips that load quickly.
 *    - AdMob Test Native Ad integration for official ad compliance.
 *    - Full YouTube ad controls: "Sponsored · 0:XX (i)", "Skip in 5s" -> "Skip ad ▶|", yellow progress bar.
 */
object InStreamVideoAdManager {
    private const val TAG = "InStreamVideoAdManager"

    // Official Google AdMob Native Video / Test Ad Unit ID
    const val TEST_NATIVE_AD_UNIT_ID = "ca-app-pub-3940256099942544/2247696110"

    // Rotating rich commercial creatives (both video ads and poster ads)
    val CREATIVES = listOf(
        VideoAdCreative(
            id = "google_play_pass",
            title = "Play 1,000+ Games & Apps Without Ads",
            advertiser = "Google Play • Sponsored",
            description = "One monthly pass for top-rated games and apps. Try 1 month free.",
            ctaText = "Start Free Trial",
            posterUrl = "https://images.unsplash.com/photo-1511512578047-dfb367046420?w=900",
            videoUrl = "https://media.w3.org/2010/05/sintel/trailer.mp4",
            iconUrl = "https://images.unsplash.com/photo-1618005182384-a83a8bd57fbe?w=120",
            rating = "4.8 ★",
            destinationUrl = "https://play.google.com/store"
        ),
        VideoAdCreative(
            id = "cybershield_vpn",
            title = "Fast & Secure VPN for Ultra HD Streaming",
            advertiser = "CyberShield Security • 50M+ Downloads",
            description = "High-speed encrypted servers in 95+ countries. Zero logs policy.",
            ctaText = "Install App",
            posterUrl = "https://images.unsplash.com/photo-1563986768609-322da13575f3?w=900",
            videoUrl = "https://media.w3.org/2010/05/bunny/trailer.mp4",
            iconUrl = "https://images.unsplash.com/photo-1526374965328-7f61d4dc18c5?w=120",
            rating = "4.9 ★",
            destinationUrl = "https://play.google.com/store"
        ),
        VideoAdCreative(
            id = "cinema_pass_4k",
            title = "Unlimited Blockbusters & Premieres in 4K HDR",
            advertiser = "CinemaPass Studios • Verified Partner",
            description = "Stream thousands of blockbuster movies and live sporting events.",
            ctaText = "Watch Now",
            posterUrl = "https://images.unsplash.com/photo-1489599849927-2ee91cede3ba?w=900",
            videoUrl = "https://media.w3.org/2010/05/sintel/trailer.mp4",
            iconUrl = "https://images.unsplash.com/photo-1536440136628-849c177e76a1?w=120",
            rating = "4.7 ★",
            destinationUrl = "https://play.google.com/store"
        ),
        VideoAdCreative(
            id = "samsung_galaxy_s25",
            title = "Experience Next-Gen Mobile AI & 200MP Camera",
            advertiser = "Samsung Mobile • Sponsored",
            description = "Built-in S-Pen, titanium frame and ultra-fast processing power.",
            ctaText = "Learn More",
            posterUrl = "https://images.unsplash.com/photo-1518770660439-4636190af475?w=900",
            videoUrl = "https://media.w3.org/2010/05/bunny/trailer.mp4",
            iconUrl = "https://images.unsplash.com/photo-1574375927938-d5a98e8ffe85?w=120",
            rating = "4.9 ★",
            destinationUrl = "https://www.samsung.com"
        ),
        VideoAdCreative(
            id = "youtube_survey",
            title = "Help YouTube by answering one question",
            advertiser = "YouTube & Google • Survey Partner",
            description = "Your feedback helps improve video recommendations and sponsored content.",
            ctaText = "Answer One Question",
            posterUrl = "https://images.unsplash.com/photo-1574375927938-d5a98e8ffe85?w=900",
            videoUrl = null, // Authentic YouTube Doodle Poster Ad
            iconUrl = "https://images.unsplash.com/photo-1618005182384-a83a8bd57fbe?w=120",
            rating = "5.0 ★",
            destinationUrl = "https://www.google.com/ads",
            isSurveyType = true
        )
    )

    private val _isAdShowing = mutableStateOf(false)
    val isAdShowing: State<Boolean> = _isAdShowing

    private val _currentNativeAd = mutableStateOf<NativeAd?>(null)
    val currentNativeAd: State<NativeAd?> = _currentNativeAd

    private val _currentCreative = mutableStateOf(CREATIVES[0])
    val currentCreative: State<VideoAdCreative> = _currentCreative

    private val _adHeadline = mutableStateOf(CREATIVES[0].title)
    val adHeadline: State<String> = _adHeadline

    private val _adAdvertiser = mutableStateOf(CREATIVES[0].advertiser)
    val adAdvertiser: State<String> = _adAdvertiser

    private val _skipCountdown = mutableIntStateOf(5)
    val skipCountdown: State<Int> = _skipCountdown

    private val _canSkip = mutableStateOf(false)
    val canSkip: State<Boolean> = _canSkip

    private val _secondsRemaining = mutableIntStateOf(15)
    val secondsRemaining: State<Int> = _secondsRemaining

    private val _adProgress = mutableFloatStateOf(0f)
    val adProgress: State<Float> = _adProgress

    private var preloadedNativeAd: NativeAd? = null
    private var isLoadingNativeAd = false
    private val mainHandler = Handler(Looper.getMainLooper())

    private var onAdStartedCallback: (() -> Unit)? = null
    private var onAdEndedCallback: (() -> Unit)? = null

    private var tickerRunnable: Runnable? = null
    private var midrollAdRunnable: Runnable? = null
    private var totalAdDurationSeconds = 15
    private var currentElapsedSeconds = 0

    // Channel switch frequency tracker
    // Randomly triggers on the 2nd or 3rd switch (e.g. 2 or 3)
    private var channelSwitchCount = 0
    private var switchesUntilNextAd = Random.nextInt(2, 4) // 2 or 3 switches
    private var lastPlayedStreamUrl: String? = null
    private var creativeIndex = 0

    /**
     * Preloads an AdMob Native Ad in background.
     */
    fun preloadAd(context: Context) {
        if (preloadedNativeAd != null || isLoadingNativeAd) return
        isLoadingNativeAd = true

        try {
            val videoOptions = VideoOptions.Builder()
                .setStartMuted(false)
                .build()

            val adOptions = NativeAdOptions.Builder()
                .setVideoOptions(videoOptions)
                .setMediaAspectRatio(NativeAdOptions.NATIVE_MEDIA_ASPECT_RATIO_LANDSCAPE)
                .build()

            val adLoader = AdLoader.Builder(context.applicationContext, TEST_NATIVE_AD_UNIT_ID)
                .forNativeAd { nativeAd ->
                    isLoadingNativeAd = false
                    preloadedNativeAd = nativeAd
                    Log.d(TAG, "AdMob Native Ad preloaded successfully: ${nativeAd.headline}")
                }
                .withAdListener(object : AdListener() {
                    override fun onAdFailedToLoad(error: LoadAdError) {
                        isLoadingNativeAd = false
                        preloadedNativeAd = null
                        Log.d(TAG, "AdMob Native Ad notice (${error.code}): ${error.message}")
                    }
                })
                .withNativeAdOptions(adOptions)
                .build()

            adLoader.loadAd(AdRequest.Builder().build())
        } catch (e: Exception) {
            isLoadingNativeAd = false
            Log.w(TAG, "Exception preloading AdMob Native Ad", e)
        }
    }

    /**
     * Called whenever a channel is selected or switched to.
     * Accurately implements the user requirement:
     * "kbhi 2nd time channel switch kbhi 3rd time ads aaye"
     */
    fun onChannelSwitch(
        context: Context,
        channelUrl: String,
        onAdStart: () -> Unit,
        onAdEnd: () -> Unit
    ) {
        onAdStartedCallback = onAdStart
        onAdEndedCallback = onAdEnd

        // Preload next ad in background
        preloadAd(context)

        // Prevent duplicate increments if recomposed with same channel URL (e.g. orientation or fullscreen)
        if (channelUrl == lastPlayedStreamUrl) {
            return
        }
        lastPlayedStreamUrl = channelUrl

        // Increment channel switch count
        channelSwitchCount++
        Log.d(TAG, "Channel switched! Switch count: $channelSwitchCount, Target for ad: $switchesUntilNextAd")

        // Check if target switch threshold (2nd or 3rd switch) is reached
        if (channelSwitchCount >= switchesUntilNextAd) {
            channelSwitchCount = 0
            // Set next target randomly between 2 and 3 switches
            switchesUntilNextAd = Random.nextInt(2, 4)
            Log.d(TAG, "Target reached! Showing in-stream ad. Next ad in $switchesUntilNextAd switches.")

            // Show ad with a brief 350ms delay for smooth UI transition
            mainHandler.postDelayed({
                if (!_isAdShowing.value) {
                    showInStreamAd(context)
                }
            }, 350L)
            return
        }

        // Mid-roll timer if user stays on one channel for more than 4 minutes
        scheduleNextMidrollAd(context, 240_000L)
    }

    /**
     * Backward-compatible entrypoint.
     */
    fun onChannelPlaybackStarted(
        context: Context,
        onAdStart: () -> Unit,
        onAdEnd: () -> Unit
    ) {
        onAdStartedCallback = onAdStart
        onAdEndedCallback = onAdEnd
        preloadAd(context)
    }

    /**
     * Schedules a mid-roll ad if user stays on the same channel for a long duration.
     */
    private fun scheduleNextMidrollAd(context: Context, delayMillis: Long) {
        midrollAdRunnable?.let { mainHandler.removeCallbacks(it) }

        val runnable = Runnable {
            if (!_isAdShowing.value) {
                showInStreamAd(context)
            }
        }
        midrollAdRunnable = runnable
        mainHandler.postDelayed(runnable, delayMillis)
    }

    /**
     * Displays the in-stream video ad overlay over playback.
     */
    fun showInStreamAd(context: Context) {
        if (_isAdShowing.value) return

        // Rotate through high quality creatives
        creativeIndex = (creativeIndex + 1) % CREATIVES.size
        val creative = CREATIVES[creativeIndex]
        _currentCreative.value = creative

        val nativeAd = preloadedNativeAd
        _currentNativeAd.value = nativeAd

        val headline = nativeAd?.headline?.takeIf { it.isNotBlank() } ?: creative.title
        val advertiser = nativeAd?.advertiser?.takeIf { it.isNotBlank() }
            ?: nativeAd?.body?.takeIf { it.isNotBlank() }
            ?: creative.advertiser

        _adHeadline.value = headline
        _adAdvertiser.value = advertiser

        _isAdShowing.value = true
        _canSkip.value = false
        _skipCountdown.value = 5
        _adProgress.value = 0f

        totalAdDurationSeconds = if (creative.isSurveyType) 20 else 15
        currentElapsedSeconds = 0
        _secondsRemaining.value = totalAdDurationSeconds

        // Pause/mute underlying video stream
        try {
            onAdStartedCallback?.invoke()
        } catch (e: Exception) {
            Log.w(TAG, "Error in onAdStartedCallback", e)
        }

        // Start countdown and progress ticker
        startAdTimer(context)

        // Preload next native ad in background
        preloadedNativeAd = null
        preloadAd(context)
    }

    private fun startAdTimer(context: Context) {
        tickerRunnable?.let { mainHandler.removeCallbacks(it) }

        tickerRunnable = object : Runnable {
            override fun run() {
                if (!_isAdShowing.value) return

                currentElapsedSeconds++
                val remaining = (totalAdDurationSeconds - currentElapsedSeconds).coerceAtLeast(0)
                _secondsRemaining.value = remaining
                _adProgress.value = (currentElapsedSeconds.toFloat() / totalAdDurationSeconds.toFloat()).coerceIn(0f, 1f)

                if (currentElapsedSeconds < 5) {
                    _skipCountdown.value = 5 - currentElapsedSeconds
                    _canSkip.value = false
                } else {
                    _canSkip.value = true
                    _skipCountdown.value = 0
                }

                if (currentElapsedSeconds >= totalAdDurationSeconds) {
                    // Ad completed naturally
                    dismissAd(context)
                } else {
                    mainHandler.postDelayed(this, 1000L)
                }
            }
        }
        mainHandler.postDelayed(tickerRunnable!!, 1000L)
    }

    /**
     * User tapped the "Skip ad ▶|" button.
     */
    fun skipAd(context: Context) {
        if (_canSkip.value) {
            Log.d(TAG, "User tapped 'Skip ad'")
            dismissAd(context)
        }
    }

    /**
     * Dismisses the ad and resumes channel playback smoothly.
     */
    fun dismissAd(context: Context) {
        tickerRunnable?.let { mainHandler.removeCallbacks(it) }
        _isAdShowing.value = false

        _currentNativeAd.value?.destroy()
        _currentNativeAd.value = null

        // Resume underlying video stream
        try {
            onAdEndedCallback?.invoke()
        } catch (e: Exception) {
            Log.w(TAG, "Error in onAdEndedCallback", e)
        }

        // Preload next ad
        preloadAd(context)
    }

    /**
     * Clean release when player is destroyed.
     */
    fun release() {
        tickerRunnable?.let { mainHandler.removeCallbacks(it) }
        midrollAdRunnable?.let { mainHandler.removeCallbacks(it) }
        _isAdShowing.value = false
        _currentNativeAd.value?.destroy()
        _currentNativeAd.value = null
        preloadedNativeAd?.destroy()
        preloadedNativeAd = null
        lastPlayedStreamUrl = null
    }
}
