package com.example.ad

import android.app.Application
import android.util.Log
import com.google.android.gms.ads.MobileAds

object AdManager {
    fun initialize(application: Application) {
        try {
            MobileAds.initialize(application) { initializationStatus ->
                Log.d("AdManager", "Google Mobile Ads initialized: $initializationStatus")
            }
            InStreamVideoAdManager.preloadAd(application)
        } catch (e: Exception) {
            Log.w("AdManager", "Failed to initialize MobileAds", e)
        }
    }
}
