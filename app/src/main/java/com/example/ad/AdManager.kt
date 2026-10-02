package com.example.ad

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.KeyEvent
import android.view.Window
import android.window.OnBackInvokedDispatcher
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.appopen.AppOpenAd
import com.google.android.gms.ads.appopen.AppOpenAd.AppOpenAdLoadCallback
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback

data class YouTubeAdData(
    val title: String = "Help YouTube by answering one question",
    val advertiser: String = "YouTube advertiser survey",
    val callToAction: String = "Answer one question",
    val durationSeconds: Int = 15,
    val skipAfterSeconds: Int = 5
)

/**
 * AdManager handles Google AdMob Ads:
 * 1. App Open: Shows Google AdMob App Open Ad (using official test ID ca-app-pub-3940256099942544/9257395921)
 *    with cooldown.
 * 2. App Close / Exit: Shows interstitial ad every time the user exits the app.
 * 3. Navigation / Tab Switch: Random full-screen interstitial ad when switching playlists, channels, settings.
 * 4. Back-button Protection: Intercepts and consumes back key events & gestures on AdMob's AdActivity
 *    so the ad is not prematurely dismissed by pressing back; the user must wait until the ad finishes
 *    and tap the ad's native cut/close (X) button.
 */
object AdManager {
    private const val TAG = "AdManager"

    // Official Google AdMob Test Ad Unit IDs
    const val TEST_APP_OPEN_AD_UNIT_ID = "ca-app-pub-3940256099942544/9257395921"
    const val TEST_INTERSTITIAL_AD_UNIT_ID = "ca-app-pub-3940256099942544/1033173712"
    const val TEST_BANNER_AD_UNIT_ID = "ca-app-pub-3940256099942544/6300978111"
    const val TEST_NATIVE_AD_UNIT_ID = "ca-app-pub-3940256099942544/2247696110"

    // Cooldown in ms between consecutive app open ads to avoid overlapping launches
    private const val OPEN_AD_COOLDOWN_MS = 3 * 1000L
    private const val PREFS_NAME = "hypnotix_ad_prefs"
    private const val KEY_LAST_OPEN_AD_TIME = "key_last_open_ad_time"

    // 25 seconds cooldown between navigation full-screen ads
    private const val NAVIGATION_AD_COOLDOWN_MS = 25 * 1000L

    private var appOpenAd: AppOpenAd? = null
    private var isAppOpenAdLoading = false
    private var appOpenAdLoadTime = 0L
    var isShowingAppOpenAd = false
        private set

    private var exitInterstitialAd: InterstitialAd? = null
    private var navigationInterstitialAd: InterstitialAd? = null

    private var isExitAdLoading = false
    private var isNavigationAdLoading = false
    private var isInitialized = false

    private var currentActivity: Activity? = null

    private var navigationCounter = 0
    private var lastNavigationAdTime = 0L

    // In-stream YouTube ad flows for UI components
    private val _isYouTubeAdActive = kotlinx.coroutines.flow.MutableStateFlow(false)
    val isYouTubeAdActive: kotlinx.coroutines.flow.StateFlow<Boolean> = _isYouTubeAdActive

    private val _currentAdData = kotlinx.coroutines.flow.MutableStateFlow(YouTubeAdData())
    val currentAdData: kotlinx.coroutines.flow.StateFlow<YouTubeAdData> = _currentAdData

    private val _adRemainingSeconds = kotlinx.coroutines.flow.MutableStateFlow(15)
    val adRemainingSeconds: kotlinx.coroutines.flow.StateFlow<Int> = _adRemainingSeconds

    private val _canSkipAd = kotlinx.coroutines.flow.MutableStateFlow(false)
    val canSkipAd: kotlinx.coroutines.flow.StateFlow<Boolean> = _canSkipAd

    private val _adProgress = kotlinx.coroutines.flow.MutableStateFlow(0f)
    val adProgress: kotlinx.coroutines.flow.StateFlow<Float> = _adProgress

    fun dismissYouTubeAd() {
        _isYouTubeAdActive.value = false
    }

    fun skipInStreamAd() {
        _isYouTubeAdActive.value = false
    }

    fun onPlaybackStopped() {
        _isYouTubeAdActive.value = false
    }

    private val mainHandler = Handler(Looper.getMainLooper())

    /**
     * Initializes Google Mobile Ads SDK and registers an ActivityLifecycleCallback to intercept
     * back presses and gestures inside AdActivity, preventing premature ad dismissal.
     */
    fun initialize(application: Application) {
        if (isInitialized) return
        isInitialized = true

        try {
            MobileAds.initialize(application) {
                Log.d(TAG, "Google Mobile Ads SDK Initialized")
                preloadAppOpenAd(application)
                preloadExitAd(application)
                preloadNavigationAd(application)
            }

            // Register lifecycle callbacks to attach back-button block to AdActivity
            application.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
                override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
                    attachBackButtonBlockerIfNeeded(activity)
                }

                override fun onActivityStarted(activity: Activity) {
                    if (!activity.javaClass.name.contains("AdActivity", ignoreCase = true)) {
                        currentActivity = activity
                    }
                }

                override fun onActivityResumed(activity: Activity) {
                    if (!activity.javaClass.name.contains("AdActivity", ignoreCase = true)) {
                        currentActivity = activity
                    }
                    attachBackButtonBlockerIfNeeded(activity)
                }

                override fun onActivityPaused(activity: Activity) {}
                override fun onActivityStopped(activity: Activity) {
                    if (currentActivity === activity) {
                        currentActivity = null
                    }
                }
                override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
                override fun onActivityDestroyed(activity: Activity) {
                    if (currentActivity === activity) {
                        currentActivity = null
                    }
                }
            })
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing MobileAds", e)
        }
    }

    /**
     * Blocks back key & back gesture on AdMob AdActivity so the ad is only dismissible
     * via the ad's own close/cut button.
     */
    private fun attachBackButtonBlockerIfNeeded(activity: Activity) {
        val className = activity.javaClass.name
        if (className.contains("AdActivity", ignoreCase = true) || className.contains("com.google.android.gms.ads")) {
            try {
                // 1. Intercept key events on the window callback to consume KEYCODE_BACK
                val originalCallback = activity.window.callback
                if (originalCallback != null && originalCallback !is BackBlockerWindowCallback) {
                    activity.window.callback = BackBlockerWindowCallback(originalCallback)
                    Log.d(TAG, "Attached BackBlockerWindowCallback to ${activity.javaClass.simpleName}")
                }

                // 2. On Android 13+ (API 33+), register high-priority back invoked callback to consume back gestures
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    runCatching {
                        activity.onBackInvokedDispatcher.registerOnBackInvokedCallback(
                            OnBackInvokedDispatcher.PRIORITY_OVERLAY
                        ) {
                            Log.d(TAG, "Back gesture consumed on AdActivity. User must use ad close button.")
                        }
                    }
                }
            } catch (t: Throwable) {
                Log.w(TAG, "Failed to attach back blocker to AdActivity", t)
            }
        }
    }

    /**
     * Checks if a valid preloaded App Open Ad is available.
     * AdMob App Open ads expire after 4 hours.
     */
    fun isAppOpenAdAvailable(): Boolean {
        return appOpenAd != null && (System.currentTimeMillis() - appOpenAdLoadTime < 4 * 3600 * 1000L)
    }

    /**
     * Preloads an App Open Ad using the official test ID (ca-app-pub-3940256099942544/9257395921).
     */
    fun preloadAppOpenAd(context: Context) {
        if (isAppOpenAdAvailable() || isAppOpenAdLoading) return
        isAppOpenAdLoading = true

        val adRequest = AdRequest.Builder().build()
        AppOpenAd.load(
            context.applicationContext,
            TEST_APP_OPEN_AD_UNIT_ID,
            adRequest,
            object : AppOpenAdLoadCallback() {
                override fun onAdLoaded(ad: AppOpenAd) {
                    isAppOpenAdLoading = false
                    appOpenAd = ad
                    appOpenAdLoadTime = System.currentTimeMillis()
                    Log.d(TAG, "App Open Test Ad Loaded successfully (Unit ID: $TEST_APP_OPEN_AD_UNIT_ID)")
                }

                override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                    isAppOpenAdLoading = false
                    appOpenAd = null
                    Log.w(TAG, "App Open Test Ad failed to load: ${loadAdError.message}")
                }
            }
        )
    }

    /**
     * Preloads an interstitial ad for App Open (compatibility alias).
     */
    fun preloadOpenAd(context: Context) {
        preloadAppOpenAd(context)
    }

    /**
     * Preloads an interstitial ad for App Close / Exit.
     */
    fun preloadExitAd(context: Context) {
        if (exitInterstitialAd != null || isExitAdLoading) return
        isExitAdLoading = true

        val adRequest = AdRequest.Builder().build()
        InterstitialAd.load(
            context.applicationContext,
            TEST_INTERSTITIAL_AD_UNIT_ID,
            adRequest,
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(interstitialAd: InterstitialAd) {
                    isExitAdLoading = false
                    exitInterstitialAd = interstitialAd
                    Log.d(TAG, "Exit Interstitial Ad Loaded successfully")
                }

                override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                    isExitAdLoading = false
                    exitInterstitialAd = null
                    Log.w(TAG, "Exit Interstitial Ad failed to load: ${loadAdError.message}")
                }
            }
        )
    }

    /**
     * Preloads an interstitial ad for in-app navigation (playlist / channel / settings switching).
     */
    fun preloadNavigationAd(context: Context) {
        if (navigationInterstitialAd != null || isNavigationAdLoading) return
        isNavigationAdLoading = true

        val adRequest = AdRequest.Builder().build()
        InterstitialAd.load(
            context.applicationContext,
            TEST_INTERSTITIAL_AD_UNIT_ID,
            adRequest,
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(interstitialAd: InterstitialAd) {
                    isNavigationAdLoading = false
                    navigationInterstitialAd = interstitialAd
                    Log.d(TAG, "Navigation Interstitial Ad Loaded successfully")
                }

                override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                    isNavigationAdLoading = false
                    navigationInterstitialAd = null
                    Log.w(TAG, "Navigation Interstitial Ad failed to load: ${loadAdError.message}")
                    mainHandler.postDelayed({ preloadNavigationAd(context) }, 5000)
                }
            }
        )
    }

    /**
     * Checks if a full-screen interstitial ad should be shown when the user switches tabs
     * or navigates between playlists, channels, settings, categories, etc.
     * Matches: "home screen pr agr vo baar baar playlist channel ya setting pr jata h to
     * randomly vha bhi kuch kuch time pr full screen ads show honge"
     */
    fun onUserNavigated(activity: Activity?, onFinished: () -> Unit = {}) {
        if (activity == null || activity.isFinishing || activity.isDestroyed) {
            onFinished()
            return
        }

        navigationCounter++
        val now = System.currentTimeMillis()
        val timeSinceLast = now - lastNavigationAdTime

        // Trigger randomly when navigating (counter >= 2 or random 50% chance, with cooldown)
        val isCooldownPassed = (lastNavigationAdTime == 0L || timeSinceLast >= NAVIGATION_AD_COOLDOWN_MS)
        val shouldShow = (navigationCounter >= 2 || (navigationCounter >= 1 && kotlin.random.Random.nextFloat() < 0.5f)) && isCooldownPassed

        if (!shouldShow) {
            onFinished()
            return
        }

        val ad = navigationInterstitialAd ?: exitInterstitialAd
        if (ad != null) {
            navigationCounter = 0
            lastNavigationAdTime = now
            ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    Log.d(TAG, "Navigation Interstitial Ad dismissed by user")
                    if (ad === navigationInterstitialAd) navigationInterstitialAd = null
                    if (ad === exitInterstitialAd) exitInterstitialAd = null
                    preloadNavigationAd(activity.applicationContext)
                    preloadAppOpenAd(activity.applicationContext)
                    onFinished()
                }

                override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                    Log.w(TAG, "Navigation Interstitial Ad failed to show: ${adError.message}")
                    if (ad === navigationInterstitialAd) navigationInterstitialAd = null
                    preloadNavigationAd(activity.applicationContext)
                    onFinished()
                }

                override fun onAdShowedFullScreenContent() {
                    Log.d(TAG, "Navigation Interstitial Ad displayed on screen")
                }
            }
            ad.show(activity)
        } else {
            preloadNavigationAd(activity.applicationContext)
            onFinished()
        }
    }

    /**
     * Shows an App Open Ad when the user launches the app.
     * Uses official Google AdMob App Open Test Ad Unit ID (ca-app-pub-3940256099942544/9257395921).
     * If the ad is ready, displays it immediately; otherwise fetches and displays with timeout fallback.
     */
    fun showAppOpenAd(activity: Activity, onFinished: () -> Unit) {
        if (activity.isFinishing || activity.isDestroyed) {
            onFinished()
            return
        }

        if (isShowingAppOpenAd) {
            Log.d(TAG, "App Open Ad is already showing.")
            onFinished()
            return
        }

        val prefs = activity.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val lastShownTime = prefs.getLong(KEY_LAST_OPEN_AD_TIME, 0L)
        val currentTime = System.currentTimeMillis()
        val elapsed = currentTime - lastShownTime

        // Short guard to avoid duplicate rapid calls in the same second
        if (lastShownTime > 0L && elapsed < OPEN_AD_COOLDOWN_MS) {
            Log.d(TAG, "App Open ad rapid trigger guard active. Skipping ad.")
            onFinished()
            return
        }

        // If preloaded App Open test ad is available, display it
        if (isAppOpenAdAvailable()) {
            val ad = appOpenAd ?: run {
                onFinished()
                return
            }
            ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    Log.d(TAG, "App Open Test Ad dismissed by user (cut button tapped)")
                    appOpenAd = null
                    isShowingAppOpenAd = false
                    prefs.edit().putLong(KEY_LAST_OPEN_AD_TIME, System.currentTimeMillis()).apply()
                    preloadAppOpenAd(activity.applicationContext)
                    onFinished()
                }

                override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                    Log.w(TAG, "App Open Test Ad failed to show: ${adError.message}")
                    appOpenAd = null
                    isShowingAppOpenAd = false
                    preloadAppOpenAd(activity.applicationContext)
                    onFinished()
                }

                override fun onAdShowedFullScreenContent() {
                    isShowingAppOpenAd = true
                    Log.d(TAG, "App Open Test Ad displayed on screen (Unit ID: $TEST_APP_OPEN_AD_UNIT_ID)")
                }
            }
            ad.show(activity)
        } else {
            // Ad was not ready yet; try a fast load with 2.5 second timeout so user is not kept waiting
            Log.d(TAG, "App Open Ad not preloaded, fetching with Test ID: $TEST_APP_OPEN_AD_UNIT_ID...")
            var hasCallbackFired = false
            val timeoutRunnable = Runnable {
                if (!hasCallbackFired) {
                    hasCallbackFired = true
                    Log.d(TAG, "App Open quick load timed out, proceeding to app")
                    onFinished()
                }
            }
            mainHandler.postDelayed(timeoutRunnable, 2500)

            val adRequest = AdRequest.Builder().build()
            AppOpenAd.load(
                activity.applicationContext,
                TEST_APP_OPEN_AD_UNIT_ID,
                adRequest,
                object : AppOpenAdLoadCallback() {
                    override fun onAdLoaded(loadedAd: AppOpenAd) {
                        mainHandler.removeCallbacks(timeoutRunnable)
                        if (!hasCallbackFired && !activity.isFinishing && !activity.isDestroyed) {
                            hasCallbackFired = true
                            loadedAd.fullScreenContentCallback = object : FullScreenContentCallback() {
                                override fun onAdDismissedFullScreenContent() {
                                    prefs.edit().putLong(KEY_LAST_OPEN_AD_TIME, System.currentTimeMillis()).apply()
                                    appOpenAd = null
                                    isShowingAppOpenAd = false
                                    preloadAppOpenAd(activity.applicationContext)
                                    onFinished()
                                }

                                override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                                    appOpenAd = null
                                    isShowingAppOpenAd = false
                                    preloadAppOpenAd(activity.applicationContext)
                                    onFinished()
                                }

                                override fun onAdShowedFullScreenContent() {
                                    isShowingAppOpenAd = true
                                    Log.d(TAG, "App Open Test Ad displayed on screen (Unit ID: $TEST_APP_OPEN_AD_UNIT_ID)")
                                }
                            }
                            loadedAd.show(activity)
                        } else {
                            appOpenAd = loadedAd
                            appOpenAdLoadTime = System.currentTimeMillis()
                        }
                    }

                    override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                        mainHandler.removeCallbacks(timeoutRunnable)
                        if (!hasCallbackFired) {
                            hasCallbackFired = true
                            Log.w(TAG, "App Open Test Ad quick load failed: ${loadAdError.message}")
                            onFinished()
                        }
                    }
                }
            )
        }
    }

    /**
     * Shows an App Open Ad on App Open (compatibility alias).
     */
    fun showAppOpenAdIfEligible(activity: Activity, onFinished: () -> Unit) {
        showAppOpenAd(activity, onFinished)
    }

    /**
     * Compatibility alias: delegating to showAppOpenAd.
     */
    fun showOpenInterstitialIfEligible(activity: Activity, onFinished: () -> Unit) {
        showAppOpenAd(activity, onFinished)
    }

    /**
     * Shows an Interstitial Ad on App Close EVERY TIME.
     * When user dismisses the ad via the ad's close/cut button, onFinished() is invoked so the activity can finish.
     */
    fun showExitInterstitial(activity: Activity?, onFinished: () -> Unit) {
        if (activity == null || activity.isFinishing || activity.isDestroyed) {
            onFinished()
            return
        }

        val ad = exitInterstitialAd
        if (ad != null) {
            ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    Log.d(TAG, "Exit Interstitial Ad dismissed by user (cut button tapped)")
                    exitInterstitialAd = null
                    preloadExitAd(activity.applicationContext)
                    onFinished()
                }

                override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                    Log.w(TAG, "Exit Interstitial Ad failed to show: ${adError.message}")
                    exitInterstitialAd = null
                    preloadExitAd(activity.applicationContext)
                    onFinished()
                }

                override fun onAdShowedFullScreenContent() {
                    Log.d(TAG, "Exit Interstitial Ad displayed on screen")
                }
            }
            ad.show(activity)
        } else {
            // If ad is not ready, try quick load with 1.2s timeout so exit is not stuck
            Log.d(TAG, "Exit Interstitial Ad not preloaded, attempting quick load before exit...")
            var hasCallbackFired = false
            val timeoutRunnable = Runnable {
                if (!hasCallbackFired) {
                    hasCallbackFired = true
                    Log.d(TAG, "Exit ad load timed out, closing app")
                    onFinished()
                }
            }
            mainHandler.postDelayed(timeoutRunnable, 1200)

            val adRequest = AdRequest.Builder().build()
            InterstitialAd.load(
                activity.applicationContext,
                TEST_INTERSTITIAL_AD_UNIT_ID,
                adRequest,
                object : InterstitialAdLoadCallback() {
                    override fun onAdLoaded(interstitialAd: InterstitialAd) {
                        mainHandler.removeCallbacks(timeoutRunnable)
                        if (!hasCallbackFired) {
                            hasCallbackFired = true
                            interstitialAd.fullScreenContentCallback = object : FullScreenContentCallback() {
                                override fun onAdDismissedFullScreenContent() {
                                    preloadExitAd(activity.applicationContext)
                                    onFinished()
                                }

                                override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                                    preloadExitAd(activity.applicationContext)
                                    onFinished()
                                }
                            }
                            interstitialAd.show(activity)
                        } else {
                            exitInterstitialAd = interstitialAd
                        }
                    }

                    override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                        mainHandler.removeCallbacks(timeoutRunnable)
                        if (!hasCallbackFired) {
                            hasCallbackFired = true
                            onFinished()
                        }
                    }
                }
            )
        }
    }

    /**
     * Window.Callback wrapper that consumes KeyEvent.KEYCODE_BACK so that pressing the mobile's
     * back button does not prematurely close the AdActivity.
     */
    private class BackBlockerWindowCallback(
        private val delegate: Window.Callback
    ) : Window.Callback by delegate {
        override fun dispatchKeyEvent(event: KeyEvent): Boolean {
            if (event.keyCode == KeyEvent.KEYCODE_BACK) {
                // Consume back press event!
                Log.d(TAG, "Back button press intercepted and blocked during interstitial ad. User must tap ad close button.")
                return true
            }
            return delegate.dispatchKeyEvent(event)
        }
    }
}
