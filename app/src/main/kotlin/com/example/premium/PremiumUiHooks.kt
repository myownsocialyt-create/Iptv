package com.example.premium

import android.app.Activity
import android.app.Application
import android.app.Dialog
import android.content.Context
import android.content.ContextWrapper
import android.database.sqlite.SQLiteDatabase
import android.graphics.Color as AndroidColor
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup
import android.view.Window
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.room.RoomDatabase
import java.lang.ref.WeakReference
import java.util.Collections
import java.util.WeakHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

// Do not enable minify without keep rules for reflected ViewModel/Channel getters.
object PremiumUiHooks {

    private const val TAG = "PremiumUiHooks"

    private val mainHandler = Handler(Looper.getMainLooper())
    private val hookScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var startupBillingHelper: BillingHelper? = null

    @Volatile
    private var appContext: Context? = null

    @Volatile
    private var currentActivityRef: WeakReference<Activity>? = null

    @Volatile
    private var lifecycleRegistered: Boolean = false

    private var activeLimitDialogRef: WeakReference<Dialog>? = null

    private val observedViewModels = Collections.newSetFromMap(WeakHashMap<Any, Boolean>())

    private val stateLock = Any()

    @Volatile
    private var cachedPlaylistCount: Int = -1

    @Volatile
    private var optimisticPlaylistCount: Int = -1

    @Volatile
    private var lastPlaylistAddMs: Long = 0L

    @Volatile
    private var cachedFavoriteCount: Int = -1

    private val optimisticFavoriteUrls = LinkedHashSet<String>()

    @Volatile
    private var hasInitializedFavoritesSet: Boolean = false

    @Volatile
    private var lastFavoriteToggleMs: Long = 0L

    private fun safeCall(target: Any?, methodName: String): Any? {
        if (target == null) return null
        val clazz = target.javaClass
        return try {
            val pubMethod = try {
                clazz.getMethod(methodName)
            } catch (_: NoSuchMethodException) {
                null
            }
            if (pubMethod != null) {
                pubMethod.invoke(target)
            } else {
                var currentClass: Class<*>? = clazz
                var foundMethod: java.lang.reflect.Method? = null
                while (currentClass != null && foundMethod == null) {
                    foundMethod = currentClass.declaredMethods.firstOrNull {
                        it.name == methodName && it.parameterTypes.isEmpty()
                    }
                    currentClass = currentClass.superclass
                }
                if (foundMethod != null) {
                    foundMethod.isAccessible = true
                    foundMethod.invoke(target)
                } else {
                    Log.e(TAG, "Method $methodName not found on ${clazz.name}")
                    null
                }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "safeCall failed for ${clazz.name}.$methodName", t)
            null
        }
    }

    private fun unwrapActivity(context: Context?): Activity? {
        var ctx = context
        while (ctx is ContextWrapper) {
            if (ctx is Activity) return ctx
            ctx = ctx.baseContext
        }
        return null
    }

    private fun updateContextAndActivity(context: Context?) {
        if (context == null) return
        if (appContext == null) {
            appContext = context.applicationContext
        }
        val act = unwrapActivity(context)
        if (act != null && act !is SubscriptionActivity && !act.isFinishing && !act.isDestroyed) {
            currentActivityRef = WeakReference(act)
        }
    }

    private fun ensureLifecycleCallbacks(activity: Activity) {
        if (lifecycleRegistered) return
        synchronized(this) {
            if (lifecycleRegistered) return
            lifecycleRegistered = true
            activity.application.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
                override fun onActivityCreated(act: Activity, savedInstanceState: Bundle?) {
                    if (act !is SubscriptionActivity) {
                        currentActivityRef = WeakReference(act)
                    }
                }

                override fun onActivityStarted(act: Activity) {
                    if (act !is SubscriptionActivity) {
                        currentActivityRef = WeakReference(act)
                    }
                }

                override fun onActivityResumed(act: Activity) {
                    if (act !is SubscriptionActivity) {
                        currentActivityRef = WeakReference(act)
                    }
                }

                override fun onActivityPaused(act: Activity) {}
                override fun onActivityStopped(act: Activity) {}
                override fun onActivitySaveInstanceState(act: Activity, outState: Bundle) {}
                override fun onActivityDestroyed(act: Activity) {
                    val current = currentActivityRef?.get()
                    if (current === act) {
                        currentActivityRef = null
                    }
                }
            })
        }
    }

    @JvmStatic
    fun onMainActivityCreate(activity: Activity) {
        try {
            appContext = activity.applicationContext
            currentActivityRef = WeakReference(activity)
            ensureLifecycleCallbacks(activity)
            PremiumManager.init(activity)
            if (startupBillingHelper == null) {
                startupBillingHelper = BillingHelper(activity.applicationContext)
            } else {
                startupBillingHelper?.queryActivePurchases()
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Error in onMainActivityCreate", t)
        }
    }

    private fun findActiveActivity(): Activity? {
        val tracked = currentActivityRef?.get()
        if (tracked != null && !tracked.isFinishing && !tracked.isDestroyed) {
            return tracked
        }
        return try {
            val helperClass = Class.forName("com.example.player.IptvAudioHelper")
            val method = helperClass.getMethod("getActiveActivity")
            val act = method.invoke(null) as? Activity
            if (act != null && !act.isFinishing && !act.isDestroyed) {
                currentActivityRef = WeakReference(act)
                act
            } else {
                null
            }
        } catch (t: Throwable) {
            null
        }
    }

    private fun resolveContext(viewModel: Any? = null): Context? {
        if (viewModel != null) {
            val vmApp = safeCall(viewModel, "getApplication") as? Context
            if (vmApp != null) {
                if (appContext == null) {
                    appContext = vmApp.applicationContext
                }
                return vmApp.applicationContext
            }
        }
        return appContext ?: findActiveActivity()?.applicationContext
    }

    private fun ensureViewModelFlowsSubscribed(viewModel: Any) {
        val shouldSubscribe = synchronized(observedViewModels) {
            observedViewModels.add(viewModel)
        }
        if (!shouldSubscribe) return

        // Keep customPlaylists and favorites StateFlows subscribed so WhileSubscribed(5000) stays hot
        val customFlow = safeCall(viewModel, "getCustomPlaylists") as? StateFlow<*>
        if (customFlow != null) {
            hookScope.launch {
                customFlow.collect { value ->
                    val list = value as? Collection<*>
                    if (list != null) {
                        cachedPlaylistCount = list.size
                    }
                }
            }
        }

        val favFlow = safeCall(viewModel, "getFavorites") as? StateFlow<*>
        if (favFlow != null) {
            hookScope.launch {
                favFlow.collect { value ->
                    val list = value as? Collection<*>
                    if (list != null) {
                        cachedFavoriteCount = list.size
                    }
                }
            }
        }
    }

    private fun getRoomDatabaseInstance(context: Context): RoomDatabase? {
        return try {
            val dbClass = Class.forName("com.example.data.local.IptvDatabase")
            val instanceField = try {
                dbClass.getDeclaredField("INSTANCE").apply { isAccessible = true }
            } catch (_: Throwable) {
                null
            }
            val existing = instanceField?.get(null) as? RoomDatabase
            if (existing != null) {
                return existing
            }
            val companionField = dbClass.getDeclaredField("Companion").apply { isAccessible = true }
            val companion = companionField.get(null)
            val getDbMethod = companion.javaClass.getMethod("getDatabase", Context::class.java)
            getDbMethod.invoke(companion, context.applicationContext) as? RoomDatabase
        } catch (t: Throwable) {
            Log.w(TAG, "Unable to obtain IptvDatabase via reflection", t)
            null
        }
    }

    private fun queryDbPlaylistCount(context: Context?): Int {
        if (context == null) return -1
        try {
            val roomDb = getRoomDatabaseInstance(context)
            if (roomDb != null) {
                val supportDb = roomDb.openHelper.readableDatabase
                supportDb.query("SELECT COUNT(*) FROM custom_playlists").use { cursor ->
                    if (cursor.moveToFirst()) {
                        return cursor.getInt(0)
                    }
                }
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Room queryDbPlaylistCount fallback triggered", t)
        }

        return try {
            val dbFile = context.getDatabasePath("hypnotix_iptv_db")
            if (dbFile != null && dbFile.exists()) {
                SQLiteDatabase.openDatabase(
                    dbFile.absolutePath,
                    null,
                    SQLiteDatabase.OPEN_READONLY
                ).use { db ->
                    db.rawQuery("SELECT COUNT(*) FROM custom_playlists", null).use { cursor ->
                        if (cursor.moveToFirst()) cursor.getInt(0) else -1
                    }
                }
            } else {
                -1
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Direct SQLite queryDbPlaylistCount failed", t)
            -1
        }
    }

    private fun queryDbFavoriteUrls(context: Context?): Set<String>? {
        if (context == null) return null
        try {
            val roomDb = getRoomDatabaseInstance(context)
            if (roomDb != null) {
                val supportDb = roomDb.openHelper.readableDatabase
                supportDb.query("SELECT streamUrl FROM favorite_channels").use { cursor ->
                    val urls = LinkedHashSet<String>()
                    while (cursor.moveToNext()) {
                        val url = cursor.getString(0)?.trim()
                        if (!url.isNullOrEmpty()) {
                            urls.add(url)
                        }
                    }
                    return urls
                }
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Room queryDbFavoriteUrls fallback triggered", t)
        }

        return try {
            val dbFile = context.getDatabasePath("hypnotix_iptv_db")
            if (dbFile != null && dbFile.exists()) {
                SQLiteDatabase.openDatabase(
                    dbFile.absolutePath,
                    null,
                    SQLiteDatabase.OPEN_READONLY
                ).use { db ->
                    db.rawQuery("SELECT streamUrl FROM favorite_channels", null).use { cursor ->
                        val urls = LinkedHashSet<String>()
                        while (cursor.moveToNext()) {
                            val url = cursor.getString(0)?.trim()
                            if (!url.isNullOrEmpty()) {
                                urls.add(url)
                            }
                        }
                        urls
                    }
                }
            } else {
                null
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Direct SQLite queryDbFavoriteUrls failed", t)
            null
        }
    }

    private fun launchPaywall(context: Context? = null) {
        val act = (context as? Activity) ?: findActiveActivity()
        if (act != null) {
            SubscriptionActivity.start(act)
        } else {
            val ctx = context ?: appContext
            if (ctx != null) {
                SubscriptionActivity.start(ctx)
            }
        }
    }

    private fun dpToPx(context: Context, dp: Float): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp,
            context.resources.displayMetrics
        ).toInt()
    }

    private fun showLimitReachedPopup(
        title: String,
        message: String,
        contextFallback: Context? = null
    ) {
        val showAction = Runnable {
            val activity = findActiveActivity()
            if (activity == null || activity.isFinishing || activity.isDestroyed) {
                launchPaywall(contextFallback)
                return@Runnable
            }

            try {
                activeLimitDialogRef?.get()?.let { existing ->
                    if (existing.isShowing) {
                        existing.dismiss()
                    }
                }
            } catch (_: Throwable) {
            }

            try {
                val dialog = Dialog(activity)
                dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
                dialog.setCancelable(true)
                dialog.setCanceledOnTouchOutside(true)

                val rootCard = LinearLayout(activity).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER_HORIZONTAL
                    val padH = dpToPx(activity, 24f)
                    val padV = dpToPx(activity, 24f)
                    setPadding(padH, padV, padH, padV)
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.RECTANGLE
                        cornerRadius = dpToPx(activity, 24f).toFloat()
                        setColor(AndroidColor.parseColor("#141A26"))
                        setStroke(dpToPx(activity, 1.5f), AndroidColor.parseColor("#2CA8FE"))
                    }
                }

                // Crown Icon Circle Badge
                val iconBadge = TextView(activity).apply {
                    text = "👑"
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 26f)
                    gravity = Gravity.CENTER
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setColor(AndroidColor.parseColor("#26FFD700"))
                        setStroke(dpToPx(activity, 1.2f), AndroidColor.parseColor("#FFD700"))
                    }
                }
                val badgeSize = dpToPx(activity, 60f)
                rootCard.addView(
                    iconBadge,
                    LinearLayout.LayoutParams(badgeSize, badgeSize).apply {
                        bottomMargin = dpToPx(activity, 16f)
                    }
                )

                // Title
                val titleView = TextView(activity).apply {
                    text = title
                    setTextColor(AndroidColor.WHITE)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    gravity = Gravity.CENTER
                }
                rootCard.addView(
                    titleView,
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    ).apply {
                        bottomMargin = dpToPx(activity, 10f)
                    }
                )

                // Description Message
                val messageView = TextView(activity).apply {
                    text = message
                    setTextColor(AndroidColor.parseColor("#B0B8C8"))
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                    gravity = Gravity.CENTER
                    setLineSpacing(dpToPx(activity, 3f).toFloat(), 1.0f)
                }
                rootCard.addView(
                    messageView,
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    ).apply {
                        bottomMargin = dpToPx(activity, 22f)
                    }
                )

                // Primary Button: Buy Subscription
                val buyButton = TextView(activity).apply {
                    text = "Buy Subscription"
                    setTextColor(AndroidColor.WHITE)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 15.5f)
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    gravity = Gravity.CENTER
                    val btnPadV = dpToPx(activity, 13f)
                    setPadding(0, btnPadV, 0, btnPadV)
                    background = GradientDrawable(
                        GradientDrawable.Orientation.LEFT_RIGHT,
                        intArrayOf(
                            AndroidColor.parseColor("#1A86F0"),
                            AndroidColor.parseColor("#2CA8FE")
                        )
                    ).apply {
                        shape = GradientDrawable.RECTANGLE
                        cornerRadius = dpToPx(activity, 25f).toFloat()
                    }
                    setOnClickListener {
                        try {
                            dialog.dismiss()
                        } catch (_: Throwable) {
                        }
                        SubscriptionActivity.start(activity)
                    }
                }
                rootCard.addView(
                    buyButton,
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    ).apply {
                        bottomMargin = dpToPx(activity, 10f)
                    }
                )

                // Secondary Button: Not Now
                val cancelButton = TextView(activity).apply {
                    text = "Not Now"
                    setTextColor(AndroidColor.parseColor("#8E95A5"))
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f)
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    gravity = Gravity.CENTER
                    val cancelPadV = dpToPx(activity, 8f)
                    setPadding(0, cancelPadV, 0, cancelPadV)
                    setOnClickListener {
                        try {
                            dialog.dismiss()
                        } catch (_: Throwable) {
                        }
                    }
                }
                rootCard.addView(
                    cancelButton,
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                )

                dialog.setContentView(rootCard)
                dialog.window?.apply {
                    setBackgroundDrawable(ColorDrawable(AndroidColor.TRANSPARENT))
                    val screenWidth = activity.resources.displayMetrics.widthPixels
                    val dialogWidth = (screenWidth * 0.88f).toInt().coerceAtMost(dpToPx(activity, 380f))
                    setLayout(dialogWidth, ViewGroup.LayoutParams.WRAP_CONTENT)
                    setGravity(Gravity.CENTER)
                }

                activeLimitDialogRef = WeakReference(dialog)
                dialog.show()
            } catch (t: Throwable) {
                Log.e(TAG, "Failed to show limit dialog, falling back to SubscriptionActivity", t)
                SubscriptionActivity.start(activity)
            }
        }

        if (Looper.myLooper() == Looper.getMainLooper()) {
            showAction.run()
        } else {
            mainHandler.post(showAction)
        }
    }

    @JvmStatic
    fun shouldBlockPip(activity: Activity): Boolean {
        updateContextAndActivity(activity)
        if (PremiumManager.canUsePip()) {
            return false
        }
        SubscriptionActivity.start(activity)
        return true
    }

    @JvmStatic
    fun shouldBlockPlayChannel(viewModel: Any, channel: Any): Boolean {
        val ctx = resolveContext(viewModel)
        if (PremiumManager.isPremium(ctx)) {
            return false
        }
        try {
            val currentSectionFlow = safeCall(viewModel, "getCurrentSection") as? StateFlow<*>
            val sectionName = currentSectionFlow?.value?.toString() ?: ""

            val streamUrlObj = safeCall(channel, "getStreamUrl")
            val channelIdObj = safeCall(channel, "getId")

            if (currentSectionFlow == null && streamUrlObj == null && channelIdObj == null) {
                Log.e(TAG, "shouldBlockPlayChannel: could not determine channel type; allowing playback")
                return false
            }

            val streamUrl = streamUrlObj as? String ?: ""
            val channelId = channelIdObj as? String ?: ""

            var isXtream = sectionName.equals("XTREAM", ignoreCase = true) ||
                channelId.startsWith("xtream_", ignoreCase = true)

            if (!isXtream) {
                val xtreamList = (safeCall(viewModel, "getXtreamChannels") as? StateFlow<*>)?.value as? Iterable<*>
                if (xtreamList != null) {
                    for (item in xtreamList) {
                        if (item != null) {
                            val itemUrl = safeCall(item, "getStreamUrl") as? String
                            if (itemUrl == streamUrl && streamUrl.isNotEmpty()) {
                                isXtream = true
                                break
                            }
                        }
                    }
                }
            }

            if (!isXtream) {
                val vodList = (safeCall(viewModel, "getXtreamVodChannels") as? StateFlow<*>)?.value as? Iterable<*>
                if (vodList != null) {
                    for (item in vodList) {
                        if (item != null) {
                            val itemUrl = safeCall(item, "getStreamUrl") as? String
                            if (itemUrl == streamUrl && streamUrl.isNotEmpty()) {
                                isXtream = true
                                break
                            }
                        }
                    }
                }
            }

            if (isXtream) {
                launchPaywall(ctx)
                return true
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Error in shouldBlockPlayChannel; allowing playback", t)
        }
        return false
    }

    @JvmStatic
    fun shouldBlockAddPlaylist(viewModel: Any): Boolean {
        val ctx = resolveContext(viewModel)
        ensureViewModelFlowsSubscribed(viewModel)

        if (PremiumManager.isPremium(ctx)) {
            return false
        }

        synchronized(stateLock) {
            val now = System.currentTimeMillis()
            val dbCount = queryDbPlaylistCount(ctx)

            val customFlow = safeCall(viewModel, "getCustomPlaylists") as? StateFlow<*>
            val flowList = customFlow?.value as? Collection<*>
            val flowCount = if (flowList != null && flowList.isNotEmpty()) flowList.size else -1

            // If enough time has passed for Room's IO insert/delete to settle, sync optimistic count with DB
            if (now - lastPlaylistAddMs > 3000L && dbCount >= 0) {
                optimisticPlaylistCount = dbCount
            }

            val currentCount = maxOf(
                dbCount,
                flowCount,
                optimisticPlaylistCount,
                if (dbCount < 0 && flowCount < 0) cachedPlaylistCount else 0
            )

            cachedPlaylistCount = currentCount

            if (!PremiumManager.canAddPlaylist(currentCount)) {
                showLimitReachedPopup(
                    title = "Playlist Limit Reached",
                    message = "You have already added 3 playlists. To remove this limit and add unlimited playlists, please buy a subscription.",
                    contextFallback = ctx
                )
                return true
            }

            optimisticPlaylistCount = currentCount + 1
            lastPlaylistAddMs = now
            cachedPlaylistCount = optimisticPlaylistCount
            return false
        }
    }

    @JvmStatic
    fun shouldBlockToggleFavorite(viewModel: Any, channel: Any): Boolean {
        val ctx = resolveContext(viewModel)
        ensureViewModelFlowsSubscribed(viewModel)

        if (PremiumManager.isPremium(ctx)) {
            return false
        }

        synchronized(stateLock) {
            val now = System.currentTimeMillis()
            val targetUrl = (safeCall(channel, "getStreamUrl") as? String)?.trim() ?: ""

            val dbFavUrls = queryDbFavoriteUrls(ctx)

            val favFlow = safeCall(viewModel, "getFavorites") as? StateFlow<*>
            val favList = favFlow?.value as? Collection<*>
            val flowFavUrls: Set<String>? = if (favList != null && favList.isNotEmpty()) {
                val set = LinkedHashSet<String>()
                for (item in favList) {
                    if (item != null) {
                        val url = (safeCall(item, "getStreamUrl") as? String)?.trim()
                        if (!url.isNullOrEmpty()) {
                            set.add(url)
                        }
                    }
                }
                set
            } else {
                null
            }

            // Synchronize optimistic set with live SQLite DB if initial or if 3s window after rapid taps has settled
            if (!hasInitializedFavoritesSet || (now - lastFavoriteToggleMs > 3000L)) {
                if (dbFavUrls != null) {
                    optimisticFavoriteUrls.clear()
                    optimisticFavoriteUrls.addAll(dbFavUrls)
                    hasInitializedFavoritesSet = true
                } else if (flowFavUrls != null) {
                    optimisticFavoriteUrls.clear()
                    optimisticFavoriteUrls.addAll(flowFavUrls)
                    hasInitializedFavoritesSet = true
                }
            }

            // Check if channel is already a favorite (by streamUrl primary key) -> removing is always allowed
            if (targetUrl.isNotEmpty() && optimisticFavoriteUrls.contains(targetUrl)) {
                optimisticFavoriteUrls.remove(targetUrl)
                lastFavoriteToggleMs = now
                cachedFavoriteCount = optimisticFavoriteUrls.size
                return false
            }

            val currentCount = if (hasInitializedFavoritesSet) {
                optimisticFavoriteUrls.size
            } else {
                maxOf(cachedFavoriteCount, 0)
            }

            if (!PremiumManager.canAddFavorite(currentCount)) {
                showLimitReachedPopup(
                    title = "Favorites Limit Reached",
                    message = "You have already added 5 favorites. To remove this limit and add unlimited favorite channels, please buy a subscription.",
                    contextFallback = ctx
                )
                return true
            }

            if (targetUrl.isNotEmpty()) {
                optimisticFavoriteUrls.add(targetUrl)
                hasInitializedFavoritesSet = true
            }
            lastFavoriteToggleMs = now
            cachedFavoriteCount = optimisticFavoriteUrls.size
            return false
        }
    }

    @Composable
    @JvmStatic
    fun RenderGoldPremiumBadge() {
        val context = LocalContext.current
        updateContextAndActivity(context)
        val isPremium by PremiumManager.isPremiumState
        val activePlan by PremiumManager.activePlanState

        val goldBrush = Brush.horizontalGradient(
            colors = listOf(
                Color(0xFFFFD700),
                Color(0xFFF59E0B),
                Color(0xFFFFEA79)
            )
        )

        val bgBrush = Brush.horizontalGradient(
            colors = listOf(
                Color(0xFF2A210B),
                Color(0xFF1F1706),
                Color(0xFF2D220A)
            )
        )

        Surface(
            modifier = Modifier
                .height(32.dp)
                .clip(RoundedCornerShape(16.dp))
                .clickable {
                    if (!isPremium) {
                        SubscriptionActivity.start(context)
                    } else if (activePlan != PremiumManager.PLAN_LIFETIME) {
                        SubscriptionActivity.start(context)
                    } else {
                        Toast.makeText(context, "👑 Lifetime Premium Active!", Toast.LENGTH_SHORT).show()
                    }
                },
            shape = RoundedCornerShape(16.dp),
            color = Color.Transparent,
            border = BorderStroke(1.2.dp, goldBrush)
        ) {
            Row(
                modifier = Modifier
                    .background(bgBrush)
                    .padding(horizontal = 12.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "👑",
                    fontSize = 13.sp
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (isPremium) "Premium" else "Go Premium",
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.ExtraBold,
                    style = androidx.compose.ui.text.TextStyle(
                        brush = goldBrush
                    )
                )
                if (isPremium && activePlan != PremiumManager.PLAN_LIFETIME) {
                    Spacer(modifier = Modifier.width(5.dp))
                    Box(
                        modifier = Modifier
                            .background(Color(0x33FFD700), RoundedCornerShape(6.dp))
                            .padding(horizontal = 5.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = "UPGRADE",
                            color = Color(0xFFFFD700),
                            fontSize = 8.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }

    @Composable
    @JvmStatic
    fun RenderHomeTopBarCrownAndDailyCheck() {
        val context = LocalContext.current
        updateContextAndActivity(context)
        val isPremium by PremiumManager.isPremiumState

        LaunchedEffect(Unit) {
            PremiumManager.init(context)
            delay(3500L)
            if (PremiumManager.shouldShowDailyPrompt(context)) {
                PremiumManager.markDailyPromptShown(context)
                SubscriptionActivity.start(context)
            }
        }

        if (isPremium) {
            Text(
                text = "👑",
                fontSize = 14.sp,
                modifier = Modifier
                    .offset(x = 2.dp, y = (-6).dp)
                    .rotate(-18f)
            )
            Spacer(modifier = Modifier.width(2.dp))
        }
    }

    @Composable
    @JvmStatic
    fun RenderDrawerPremiumBanner() {
        val context = LocalContext.current
        updateContextAndActivity(context)
        val isPremium by PremiumManager.isPremiumState
        val activePlan by PremiumManager.activePlanState

        val goldBrush = Brush.horizontalGradient(
            colors = listOf(
                Color(0xFFFFD700),
                Color(0xFFF59E0B),
                Color(0xFFFFE066)
            )
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            Color(0xFF281F0A),
                            Color(0xFF1A1405),
                            Color(0xFF2C2109)
                        )
                    )
                )
                .border(1.2.dp, goldBrush, RoundedCornerShape(16.dp))
                .clickable {
                    if (!isPremium || activePlan != PremiumManager.PLAN_LIFETIME) {
                        SubscriptionActivity.start(context)
                    } else {
                        Toast.makeText(context, "👑 Lifetime Premium Active!", Toast.LENGTH_SHORT).show()
                    }
                }
                .padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .background(Color(0x33FFD700), CircleShape)
                        .border(1.dp, Color(0xFFFFD700), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "👑",
                        fontSize = 18.sp
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (isPremium) "Premium Member" else "Go Pro / Premium",
                        fontSize = 14.5.sp,
                        fontWeight = FontWeight.ExtraBold,
                        style = androidx.compose.ui.text.TextStyle(brush = goldBrush)
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (isPremium && activePlan != PremiumManager.PLAN_LIFETIME)
                            "Tap to upgrade your plan"
                        else if (isPremium)
                            "All VIP features unlocked"
                        else
                            "No Ads • PiP • Xtream • Unlimited",
                        color = Color(0xFFD4AF37),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                Box(
                    modifier = Modifier
                        .background(goldBrush, RoundedCornerShape(10.dp))
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = if (isPremium) "PRO" else "BUY",
                        color = Color(0xFF141004),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black
                    )
                }
            }
        }
    }
}
