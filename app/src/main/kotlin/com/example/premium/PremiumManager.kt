package com.example.premium

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.provider.Settings
import android.util.Base64
import android.util.Log
import androidx.compose.runtime.mutableStateOf
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

// Note: Local HMAC signature verification provides tamper resistance against casual local
// SharedPreferences editing. Real security requires server-side purchase verification.
object PremiumManager {
    private const val TAG = "PremiumManager"
    private const val PREFS_NAME = "hypnotix_premium_prefs"
    private const val KEY_IS_PREMIUM = "is_premium"
    private const val KEY_ACTIVE_PLAN = "active_plan"
    private const val KEY_PURCHASE_TIME = "purchase_time"
    private const val KEY_LAST_DAILY_PROMPT = "last_daily_prompt_date"
    private const val KEY_PREMIUM_SIG = "premium_sig"

    private const val HMAC_ALGORITHM = "HmacSHA256"
    private const val APP_SECRET_CONSTANT = "Hypnotix_IPTV_2026_Premium_Integrity_Constant"

    val PLAN_MONTHLY = "monthly_premium"
    val PLAN_YEARLY = "yearly_premium"
    val PLAN_LIFETIME = "lifetime_premium"

    val isPremiumState = mutableStateOf(false)
    val activePlanState = mutableStateOf("")

    private var appContext: Context? = null

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private fun computeHmacSignature(context: Context, isPremium: Boolean, plan: String): String? {
        return try {
            val androidId = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ANDROID_ID
            ) ?: "default_android_id"
            val keyMaterial = "${context.packageName}|$androidId|$APP_SECRET_CONSTANT"
            val mac = Mac.getInstance(HMAC_ALGORITHM)
            val secretKey = SecretKeySpec(keyMaterial.toByteArray(Charsets.UTF_8), HMAC_ALGORITHM)
            mac.init(secretKey)
            val data = "$isPremium|$plan".toByteArray(Charsets.UTF_8)
            val rawHmac = mac.doFinal(data)
            Base64.encodeToString(rawHmac, Base64.NO_WRAP)
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to compute premium signature", t)
            null
        }
    }

    private fun verifyStoredPremium(context: Context, prefs: SharedPreferences): Pair<Boolean, String> {
        return try {
            val storedPrem = prefs.getBoolean(KEY_IS_PREMIUM, false)
            if (!storedPrem) {
                return Pair(false, "")
            }
            val storedPlan = prefs.getString(KEY_ACTIVE_PLAN, "") ?: ""
            val storedSig = prefs.getString(KEY_PREMIUM_SIG, null)
            if (storedSig.isNullOrEmpty()) {
                Log.w(TAG, "Missing premium signature; treating as non-premium until verified by Google Play")
                return Pair(false, "")
            }
            val expectedSig = computeHmacSignature(context, storedPrem, storedPlan)
            if (expectedSig != null && storedSig == expectedSig) {
                Pair(true, storedPlan)
            } else {
                Log.w(TAG, "Invalid premium signature; treating as non-premium until verified by Google Play")
                Pair(false, "")
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Error verifying premium signature", t)
            Pair(false, "")
        }
    }

    @JvmStatic
    fun init(context: Context) {
        val app = context.applicationContext
        appContext = app
        val prefs = getPrefs(app)
        val (verifiedPrem, verifiedPlan) = verifyStoredPremium(app, prefs)
        isPremiumState.value = verifiedPrem
        activePlanState.value = verifiedPlan
        if (!verifiedPrem) {
            PremiumUiHooks.enforceFreeTierLimitsIfNeeded(app)
        }
    }

    @JvmStatic
    fun isPremium(context: Context? = null): Boolean {
        val ctx = (context ?: appContext)?.applicationContext ?: (context ?: appContext)
        return if (ctx != null) {
            val (verifiedPrem, verifiedPlan) = verifyStoredPremium(ctx, getPrefs(ctx))
            if (isPremiumState.value != verifiedPrem) {
                isPremiumState.value = verifiedPrem
            }
            if (activePlanState.value != verifiedPlan) {
                activePlanState.value = verifiedPlan
            }
            verifiedPrem
        } else {
            isPremiumState.value
        }
    }

    @JvmStatic
    fun setPremium(context: Context, isPremium: Boolean, plan: String = "") {
        val app = context.applicationContext
        appContext = app
        val normalizedPlan = if (isPremium) plan else ""
        val sig = computeHmacSignature(app, isPremium, normalizedPlan) ?: ""
        getPrefs(app).edit()
            .putBoolean(KEY_IS_PREMIUM, isPremium)
            .putString(KEY_ACTIVE_PLAN, normalizedPlan)
            .putLong(KEY_PURCHASE_TIME, if (isPremium) System.currentTimeMillis() else 0L)
            .putString(KEY_PREMIUM_SIG, sig)
            .apply()
        isPremiumState.value = isPremium
        activePlanState.value = normalizedPlan
        if (!isPremium) {
            PremiumUiHooks.enforceFreeTierLimitsIfNeeded(app)
        }
    }

    @JvmStatic
    fun getActivePlan(context: Context? = null): String {
        val ctx = (context ?: appContext)?.applicationContext ?: (context ?: appContext)
        return if (ctx != null) {
            val (verifiedPrem, verifiedPlan) = verifyStoredPremium(ctx, getPrefs(ctx))
            if (verifiedPrem) verifiedPlan else ""
        } else {
            if (isPremiumState.value) activePlanState.value else ""
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
