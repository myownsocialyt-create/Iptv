package com.example

import android.app.Application
import com.example.ad.AdManager

class HypnotixApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AdManager.initialize(this)
    }
}
