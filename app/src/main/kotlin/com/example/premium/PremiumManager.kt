package com.example.premium

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import androidx.compose.runtime.mutableStateOf
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object PremiumManager {
    private const val PREFS_NAME = "hypnotix_premium_prefs"
    private const val KEY_IS_PREMIUM = "is_premium"
    private const val KEY_ACTIVE_PLAN = "active_plan"
    private const val KEY_PURCHASE_TIME = "purchase_time"
    private const val KEY_LAST_DAILY_PROMPT = "last_daily_prompt_date"

    val PLAN_MONTHLY = "monthly_premium"
    val PLAN_YEARLY = "yearly_premium"
    val PLAN_LIFETIME = "lifetime_premium"

    val isPremiumState = mutableStateOf(false)
    val activePlanState = mutableStateOf("")

    private var appContext: Context? = null

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    @JvmStatic
    fun init(context: Context) {
        val app = context.applicationContext
        appContext = app
        val prefs = getPrefs(app)
        val prem = prefs.getBoolean(KEY_IS_PREMIUM, false)
        val plan = prefs.getString(KEY_ACTIVE_PLAN, "") ?: ""
        isPremiumState.value = prem
        activePlanState.value = plan
    }

    @JvmStatic
    fun isPremium(context: Context? = null): Boolean {
        val ctx = context ?: appContext
        return if (ctx != null) {
            val prem = getPrefs(ctx).getBoolean(KEY_IS_PREMIUM, false)
            if (isPremiumState.value != prem) {
                isPremiumState.value = prem
            }
            prem
        } else {
            isPremiumState.value
        }
    }

    @JvmStatic
    fun setPremium(context: Context, isPremium: Boolean, plan: String = "") {
        val app = context.applicationContext
        appContext = app
        getPrefs(app).edit()
            .putBoolean(KEY_IS_PREMIUM, isPremium)
            .putString(KEY_ACTIVE_PLAN, plan)
            .putLong(KEY_PURCHASE_TIME, if (isPremium) System.currentTimeMillis() else 0L)
            .apply()
        isPremiumState.value = isPremium
        activePlanState.value = plan
    }

    @JvmStatic
    fun getActivePlan(context: Context? = null): String {
        val ctx = context ?: appContext
        return if (ctx != null) {
            getPrefs(ctx).getString(KEY_ACTIVE_PLAN, "") ?: ""
        } else {
            activePlanState.value
        }
    }

    @JvmStatic
    fun isAdsAllowed(): Boolean {
        return !isPremium()
    }

    @JvmStatic
    fun canUsePip(): Boolean {
        return isPremium()
    }

    @JvmStatic
    fun canPlayXtream(): Boolean {
        return isPremium()
    }

    @JvmStatic
    fun canAddPlaylist(currentCount: Int): Boolean {
        return isPremium() || currentCount < 3
    }

    @JvmStatic
    fun canAddFavorite(currentCount: Int): Boolean {
        return isPremium() || currentCount < 5
    }

    @JvmStatic
    fun shouldShowDailyPrompt(context: Context): Boolean {
        if (isPremium(context)) return false
        val prefs = getPrefs(context)
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        val lastDate = prefs.getString(KEY_LAST_DAILY_PROMPT, "")
        return today != lastDate
    }

    @JvmStatic
    fun markDailyPromptShown(context: Context) {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        getPrefs(context).edit().putString(KEY_LAST_DAILY_PROMPT, today).apply()
    }

    @JvmStatic
    fun openSubscription(activity: Activity) {
        try {
            val intent = Intent(activity, SubscriptionActivity::class.java)
            activity.startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
