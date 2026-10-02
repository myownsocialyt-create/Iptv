package com.example.data.local

import android.content.Context
import android.content.SharedPreferences

class OnboardingPreferences(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("hypnotix_onboarding_prefs", Context.MODE_PRIVATE)

    var hasCompletedOnboarding: Boolean
        get() = prefs.getBoolean("has_completed_onboarding", false)
        set(value) = prefs.edit().putBoolean("has_completed_onboarding", value).apply()

    var selectedAppLanguage: String
        get() = prefs.getString("selected_app_language", "en") ?: "en"
        set(value) = prefs.edit().putString("selected_app_language", value).apply()

    var hasAcceptedMediaNotice: Boolean
        get() = prefs.getBoolean("has_accepted_media_notice", false)
        set(value) = prefs.edit().putBoolean("has_accepted_media_notice", value).apply()
}
