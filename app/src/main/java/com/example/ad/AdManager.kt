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
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback

/**
 * AdManager handles Google AdMob Ads for:
 * 1. App Open: Shows standard AdMob App Open Ad with a 4-minute cooldown.
 * 2. App Close / Exit: Shows interstitial ad every time the user exits the app.
 * 3. Back-button Protection: Intercepts and consumes back key events & gestures on AdMob's AdActivity
 *    so the ad is not prematurely dismissed by pressing back; the user must wait until the ad finishes
 *    and tap the ad's native cut/close (X) button.
 */
object AdManager {
    private const val TAG = "AdManager"

    // Official Google AdMob App Open Test Ad Unit ID
    const val TEST_APP_OPEN_AD_UNIT_ID = "ca-app-pub-3940256099942544/9257395921"

    // Official Google AdMob Interstitial Test Ad Unit ID
    const val TEST_INTERSTITIAL_AD_UNIT_ID = "ca-app-pub-3940256099942544/1033173712"

    // 4 minutes cooldown in milliseconds (240,000 ms)
    private const val OPEN_AD_COOLDOWN_MS = 4 * 60 * 1000L
    private const val PREFS_NAME = "hypnotix_ad_prefs"
    private const val KEY_LAST_OPEN_AD_TIME = "key_last_open_ad_time"

    private var appOpenAd: AppOpenAd? = null
    private var exitInterstitialAd: InterstitialAd? = null

    private var isOpenAdLoading = false
    private var isExitAdLoading = false
    private var isInitialized = false

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
                preloadOpenAd(application)
                preloadExitAd(application)
            }

            // Register lifecycle callbacks to attach back-button block to AdActivity
            application.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
                override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
                    attachBackButtonBlockerIfNeeded(activity)
                }

                override fun onActivityStarted(activity: Activity) {}

                override fun onActivityResumed(activity: Activity) {
                    attachBackButtonBlockerIfNeeded(activity)
                }

                override fun onActivityPaused(activity: Activity) {}
                override fun onActivityStopped(activity: Activity) {}
                override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
                override fun onActivityDestroyed(activity: Activity) {}
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
     * Preloads an App Open Ad for App Launch.
     */
    fun preloadOpenAd(context: Context) {
        if (appOpenAd != null || isOpenAdLoading) return
        isOpenAdLoading = true

        val adRequest = AdRequest.Builder().build()
        AppOpenAd.load(
            context.applicationContext,
            TEST_APP_OPEN_AD_UNIT_ID,
            adRequest,
            object : AppOpenAd.AppOpenAdLoadCallback() {
                override fun onAdLoaded(ad: AppOpenAd) {
                    isOpenAdLoading = false
                    appOpenAd = ad
                    Log.d(TAG, "App Open Ad Loaded successfully")
                }

                override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                    isOpenAdLoading = false
                    appOpenAd = null
                    Log.w(TAG, "App Open Ad failed to load: ${loadAdError.message}")
                }
            }
        )
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
     * Shows a standard AdMob App Open Ad on App Launch IF 4 minutes have passed since the last open ad was shown.
     * If less than 4 minutes have elapsed, onFinished() is called immediately.
     */
    fun showAppOpenAdIfEligible(activity: Activity, onFinished: () -> Unit) {
        val prefs = activity.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val lastShownTime = prefs.getLong(KEY_LAST_OPEN_AD_TIME, 0L)
        val currentTime = System.currentTimeMillis()
        val elapsed = currentTime - lastShownTime

        // If cooldown has not elapsed, skip ad
        if (lastShownTime > 0L && elapsed < OPEN_AD_COOLDOWN_MS) {
            val minutesLeft = (OPEN_AD_COOLDOWN_MS - elapsed) / 1000 / 60
            Log.d(TAG, "Open ad cooldown active (~$minutesLeft min left). Skipping ad.")
            onFinished()
            return
        }

        // If eligible, show ad
        val ad = appOpenAd
        if (ad != null) {
            ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    Log.d(TAG, "App Open Ad dismissed by user")
                    appOpenAd = null
                    // Save timestamp of successful display
                    prefs.edit().putLong(KEY_LAST_OPEN_AD_TIME, System.currentTimeMillis()).apply()
                    preloadOpenAd(activity.applicationContext)
                    onFinished()
                }

                override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                    Log.w(TAG, "App Open Ad failed to show: ${adError.message}")
                    appOpenAd = null
                    preloadOpenAd(activity.applicationContext)
                    onFinished()
                }

                override fun onAdShowedFullScreenContent() {
                    Log.d(TAG, "App Open Ad displayed on screen")
                }
            }
            ad.show(activity)
        } else {
            // Ad was not ready yet; try a fast load with 1.5 second timeout
            Log.d(TAG, "App Open Ad not preloaded, attempting quick load...")
            var hasCallbackFired = false
            val timeoutRunnable = Runnable {
                if (!hasCallbackFired) {
                    hasCallbackFired = true
                    Log.d(TAG, "Quick load timed out, proceeding to app")
                    onFinished()
                }
            }
            mainHandler.postDelayed(timeoutRunnable, 1500)

            val adRequest = AdRequest.Builder().build()
            AppOpenAd.load(
                activity.applicationContext,
                TEST_APP_OPEN_AD_UNIT_ID,
                adRequest,
                object : AppOpenAd.AppOpenAdLoadCallback() {
                    override fun onAdLoaded(loadedAd: AppOpenAd) {
                        mainHandler.removeCallbacks(timeoutRunnable)
                        if (!hasCallbackFired) {
                            hasCallbackFired = true
                            loadedAd.fullScreenContentCallback = object : FullScreenContentCallback() {
                                override fun onAdDismissedFullScreenContent() {
                                    prefs.edit().putLong(KEY_LAST_OPEN_AD_TIME, System.currentTimeMillis()).apply()
                                    preloadOpenAd(activity.applicationContext)
                                    onFinished()
                                }

                                override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                                    preloadOpenAd(activity.applicationContext)
                                    onFinished()
                                }
                            }
                            loadedAd.show(activity)
                        } else {
                            appOpenAd = loadedAd
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
     * Shows launch ad (backward-compatible delegate to App Open Ad).
     */
    fun showOpenInterstitialIfEligible(activity: Activity, onFinished: () -> Unit) {
        showAppOpenAdIfEligible(activity, onFinished)
    }

    /**
     * Safe callback invoked when user navigates between sections or channels.
     */
    fun onUserNavigated(activity: Activity? = null) {
        // Can be used to track navigation events or manage interstitial frequency if configured
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
