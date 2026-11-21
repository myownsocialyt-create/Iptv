package com.example.premium

import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup
import android.view.Window
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.pm.PackageInfoCompat
import com.example.BuildConfig
import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.install.model.UpdateAvailability
import java.lang.ref.WeakReference
import java.util.concurrent.atomic.AtomicBoolean

object PlayStoreUpdatePrompt {
    private const val TAG = "PlayStoreUpdatePrompt"
    private const val PREFS_NAME = "hypnotix_play_update_prefs"
    private const val KEY_LAST_SHOWN_AT_MS = "last_shown_at_ms"
    private const val KEY_LAST_SHOWN_VERSION_CODE = "last_shown_version_code"

    internal const val REMINDER_INTERVAL_MS = 15L * 24L * 60L * 60L * 1000L
    private const val CHECK_THROTTLE_MS = 30_000L
    private const val INITIAL_SHOW_DELAY_MS = 1_200L

    private val mainHandler by lazy { Handler(Looper.getMainLooper()) }
    private val checkInFlight = AtomicBoolean(false)

    @Volatile
    private var lastCheckAttemptAtMs: Long = 0L

    @Volatile
    private var activeDialogRef: WeakReference<Dialog>? = null

    @Volatile
    private var activeActivityRef: WeakReference<Activity>? = null

    @Volatile
    private var resumedActivityRef: WeakReference<Activity>? = null

    fun onActivityResumed(activity: Activity) {
        if (activity is SubscriptionActivity) return
        resumedActivityRef = WeakReference(activity)
        scheduleCheck(activity)
    }

    fun onActivityPaused(activity: Activity) {
        if (resumedActivityRef?.get() === activity) {
            resumedActivityRef = null
        }
    }

    fun scheduleCheck(activity: Activity) {
        if (!isActivityEligible(activity)) return
        val activityRef = WeakReference(activity)
        mainHandler.postDelayed(
            {
                val act = activityRef.get()
                if (act != null && isActivityInForeground(act) && isActivityEligible(act)) {
                    checkForPlayStoreUpdate(act)
                }
            },
            INITIAL_SHOW_DELAY_MS
        )
    }

    fun onActivityDestroyed(activity: Activity) {
        if (resumedActivityRef?.get() === activity) {
            resumedActivityRef = null
        }
        val currentAct = activeActivityRef?.get()
        if (currentAct === activity) {
            dismissActiveDialogQuietly()
        }
    }

    private fun isActivityInForeground(activity: Activity): Boolean {
        val resumed = resumedActivityRef?.get()
        return resumed === activity
    }

    private fun isActivityEligible(activity: Activity): Boolean {
        if (activity is SubscriptionActivity) return false
        if (activity.isFinishing || activity.isDestroyed) return false
        if (activity.isInPictureInPictureMode) return false
        if (PremiumUiHooks.isForceUpdateActive()) return false
        return true
    }

    private fun checkForPlayStoreUpdate(activity: Activity) {
        val nowMs = System.currentTimeMillis()
        if (nowMs - lastCheckAttemptAtMs < CHECK_THROTTLE_MS) return

        val appCtx = activity.applicationContext
        val prefs = appCtx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val currentVersionCode = getInstalledVersionCode(appCtx)
        val lastShownAtMs = readSanitizedLastShownAtMs(prefs, nowMs)
        val lastShownVersionCode = prefs.getInt(KEY_LAST_SHOWN_VERSION_CODE, 0)

        val userUpdatedPastLastPrompt =
            lastShownVersionCode > 0 && currentVersionCode >= lastShownVersionCode.toLong()
        if (userUpdatedPastLastPrompt && lastShownAtMs > 0L) {
            clearPromptState(prefs)
        } else if (lastShownAtMs > 0L && (nowMs - lastShownAtMs) < REMINDER_INTERVAL_MS) {
            return
        }

        if (!checkInFlight.compareAndSet(false, true)) return
        lastCheckAttemptAtMs = nowMs

        val activityRef = WeakReference(activity)
        try {
            val appUpdateManager = AppUpdateManagerFactory.create(appCtx)
            appUpdateManager.appUpdateInfo
                .addOnSuccessListener { appUpdateInfo ->
                    checkInFlight.set(false)
                    handleAppUpdateInfo(activityRef.get(), appCtx, appUpdateInfo)
                }
                .addOnFailureListener { error ->
                    checkInFlight.set(false)
                    Log.d(TAG, "Play Store update check skipped/unavailable: ${error.message}")
                }
        } catch (t: Throwable) {
            checkInFlight.set(false)
            Log.w(TAG, "Failed to query Play Store AppUpdateManager", t)
        }
    }

    private fun handleAppUpdateInfo(
        activity: Activity?,
        appCtx: Context,
        info: AppUpdateInfo
    ) {
        val prefs = appCtx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val currentVersionCode = getInstalledVersionCode(appCtx)
        val availableVersionCode = info.availableVersionCode()
        val availability = info.updateAvailability()

        if (availability == UpdateAvailability.UPDATE_NOT_AVAILABLE ||
            availableVersionCode <= 0 ||
            availableVersionCode.toLong() <= currentVersionCode
        ) {
            if (prefs.getLong(KEY_LAST_SHOWN_AT_MS, 0L) > 0L) {
                clearPromptState(prefs)
            }
            return
        }

        if (availability != UpdateAvailability.UPDATE_AVAILABLE) {
            return
        }

        val nowMs = System.currentTimeMillis()
        val lastShownAtMs = readSanitizedLastShownAtMs(prefs, nowMs)
        val lastShownVersionCode = prefs.getInt(KEY_LAST_SHOWN_VERSION_CODE, 0)

        if (!shouldShowPromptNow(
                currentVersionCode = currentVersionCode,
                availableVersionCode = availableVersionCode,
                lastShownAtMs = lastShownAtMs,
                lastShownVersionCode = lastShownVersionCode,
                nowMs = nowMs
            )
        ) {
            return
        }

        val targetActivity = activity ?: return
        showMinimalUpdateDialog(targetActivity, prefs, availableVersionCode)
    }

    internal fun shouldShowPromptNow(
        currentVersionCode: Long,
        availableVersionCode: Int,
        lastShownAtMs: Long,
        lastShownVersionCode: Int,
        nowMs: Long
    ): Boolean {
        if (availableVersionCode <= 0 || availableVersionCode.toLong() <= currentVersionCode) {
            return false
        }
        if (lastShownAtMs <= 0L) {
            return true
        }
        if (lastShownVersionCode > 0 && currentVersionCode >= lastShownVersionCode.toLong()) {
            return true
        }
        if (nowMs < lastShownAtMs) {
            return false
        }
        return (nowMs - lastShownAtMs) >= REMINDER_INTERVAL_MS
    }

    private fun readSanitizedLastShownAtMs(prefs: SharedPreferences, nowMs: Long): Long {
        val saved = prefs.getLong(KEY_LAST_SHOWN_AT_MS, 0L)
        if (saved > nowMs && nowMs > 0L) {
            prefs.edit().putLong(KEY_LAST_SHOWN_AT_MS, nowMs).apply()
            return nowMs
        }
        return saved
    }

    private fun recordPromptShown(
        prefs: SharedPreferences,
        nowMs: Long,
        availableVersionCode: Int
    ) {
        prefs.edit()
            .putLong(KEY_LAST_SHOWN_AT_MS, nowMs)
            .putInt(KEY_LAST_SHOWN_VERSION_CODE, availableVersionCode)
            .apply()
    }

    private fun clearPromptState(prefs: SharedPreferences) {
        prefs.edit()
            .remove(KEY_LAST_SHOWN_AT_MS)
            .remove(KEY_LAST_SHOWN_VERSION_CODE)
            .apply()
    }

    private fun getInstalledVersionCode(context: Context): Long {
        return try {
            val pkgInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            val code = PackageInfoCompat.getLongVersionCode(pkgInfo)
            if (code > 0L) code else BuildConfig.VERSION_CODE.toLong()
        } catch (_: Throwable) {
            BuildConfig.VERSION_CODE.toLong()
        }
    }

    private fun showMinimalUpdateDialog(
        activity: Activity,
        prefs: SharedPreferences,
        availableVersionCode: Int
    ) {
        mainHandler.post {
            if (!isActivityEligible(activity)) return@post
            if (activity.window?.decorView?.windowToken == null) return@post

            val existing = activeDialogRef?.get()
            if (existing != null && existing.isShowing) {
                return@post
            }

            try {
                val isDark = PremiumUiHooks.isAppInDarkMode(activity)
                val dialog = buildMinimalDialog(activity, isDark)
                activeDialogRef = WeakReference(dialog)
                activeActivityRef = WeakReference(activity)
                dialog.show()
                recordPromptShown(prefs, System.currentTimeMillis(), availableVersionCode)
            } catch (t: Throwable) {
                Log.w(TAG, "Unable to show Play Store update dialog", t)
            }
        }
    }

    private fun buildMinimalDialog(activity: Activity, isDark: Boolean): Dialog {
        val cardBg = if (isDark) Color.parseColor("#18181B") else Color.parseColor("#FFFFFF")
        val cardBorder = if (isDark) Color.parseColor("#2E2E33") else Color.parseColor("#E4E4E7")
        val titleColor = if (isDark) Color.parseColor("#FFFFFF") else Color.parseColor("#111113")
        val bodyColor = if (isDark) Color.parseColor("#B4B4BC") else Color.parseColor("#52525B")
        val primaryBtnBg = if (isDark) Color.parseColor("#FFFFFF") else Color.parseColor("#111113")
        val primaryBtnText = if (isDark) Color.parseColor("#111113") else Color.parseColor("#FFFFFF")
        val secondaryBtnBg = if (isDark) Color.parseColor("#242429") else Color.parseColor("#F4F4F5")
        val secondaryBtnBorder = if (isDark) Color.parseColor("#32323A") else Color.parseColor("#E4E4E7")
        val secondaryBtnText = if (isDark) Color.parseColor("#E4E4E7") else Color.parseColor("#27272A")

        val dialog = Dialog(activity)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setCancelable(true)
        dialog.setCanceledOnTouchOutside(true)
        dialog.setOnDismissListener {
            if (activeDialogRef?.get() === dialog) {
                activeDialogRef = null
                activeActivityRef = null
            }
        }

        val rootCard = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            val padH = dpToPx(activity, 22f)
            val padV = dpToPx(activity, 22f)
            setPadding(padH, padV, padH, padV)
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dpToPx(activity, 20f).toFloat()
                setColor(cardBg)
                setStroke(dpToPx(activity, 1f), cardBorder)
            }
        }

        val titleView = TextView(activity).apply {
            text = AppUiTranslator.tx("New Version Available", activity)
            setTextColor(titleColor)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            gravity = Gravity.START
        }
        rootCard.addView(
            titleView,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dpToPx(activity, 8f)
            }
        )

        val messageView = TextView(activity).apply {
            text = AppUiTranslator.tx(
                "A newer version of this app is available on Google Play Store. Download the latest update for improvements and fixes.",
                activity
            )
            setTextColor(bodyColor)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            gravity = Gravity.START
            setLineSpacing(dpToPx(activity, 3f).toFloat(), 1.0f)
        }
        rootCard.addView(
            messageView,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dpToPx(activity, 20f)
            }
        )

        val buttonRow = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val minTouchHeight = dpToPx(activity, 48f)

        val remindLaterBtn = TextView(activity).apply {
            text = AppUiTranslator.tx("Remind Later", activity)
            contentDescription = AppUiTranslator.tx("Remind later about app update", activity)
            setTextColor(secondaryBtnText)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            gravity = Gravity.CENTER
            minHeight = minTouchHeight
            val padH = dpToPx(activity, 12f)
            setPadding(padH, 0, padH, 0)
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dpToPx(activity, 12f).toFloat()
                setColor(secondaryBtnBg)
                setStroke(dpToPx(activity, 1f), secondaryBtnBorder)
            }
            setOnClickListener {
                dismissDialogQuietly(dialog)
            }
        }
        buttonRow.addView(
            remindLaterBtn,
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = dpToPx(activity, 10f)
            }
        )

        val updateBtn = TextView(activity).apply {
            text = AppUiTranslator.tx("Update", activity)
            contentDescription = AppUiTranslator.tx("Download new version from Google Play Store", activity)
            setTextColor(primaryBtnText)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            gravity = Gravity.CENTER
            minHeight = minTouchHeight
            val padH = dpToPx(activity, 12f)
            setPadding(padH, 0, padH, 0)
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dpToPx(activity, 12f).toFloat()
                setColor(primaryBtnBg)
            }
            setOnClickListener {
                dismissDialogQuietly(dialog)
                PremiumUiHooks.openPlayStoreForUpdate(activity)
            }
        }
        buttonRow.addView(
            updateBtn,
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        )

        rootCard.addView(
            buttonRow,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        dialog.setContentView(rootCard)
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            val dm = activity.resources.displayMetrics
            val shortEdge = minOf(dm.widthPixels, dm.heightPixels)
            val dialogWidth = (shortEdge * 0.86f).toInt().coerceAtMost(dpToPx(activity, 340f))
            setLayout(dialogWidth, ViewGroup.LayoutParams.WRAP_CONTENT)
            setGravity(Gravity.CENTER)
        }

        return dialog
    }

    private fun dismissActiveDialogQuietly() {
        val dlg = activeDialogRef?.get()
        activeDialogRef = null
        activeActivityRef = null
        if (dlg != null) {
            dismissDialogQuietly(dlg)
        }
    }

    private fun dismissDialogQuietly(dialog: Dialog) {
        try {
            if (dialog.isShowing) {
                dialog.dismiss()
            }
        } catch (_: Throwable) {
        }
    }

    private fun dpToPx(context: Context, dp: Float): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp,
            context.resources.displayMetrics
        ).toInt()
    }
}
