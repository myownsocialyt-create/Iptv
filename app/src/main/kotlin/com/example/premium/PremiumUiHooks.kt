package com.example.premium

import android.app.Activity
import android.app.Application
import android.app.Dialog
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.res.Configuration
import android.database.sqlite.SQLiteDatabase
import android.graphics.Color as AndroidColor
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
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
import com.google.firebase.remoteconfig.ConfigUpdate
import com.google.firebase.remoteconfig.ConfigUpdateListener
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigException
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings
import java.lang.ref.WeakReference
import java.util.Collections
import java.util.WeakHashMap
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

// Do not enable minify without keep rules for reflected ViewModel/Channel getters.
object PremiumUiHooks {

    private const val TAG = "PremiumUiHooks"

    private val mainHandler = Handler(Looper.getMainLooper())
    private val hookScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var startupBillingHelper: BillingHelper? = null

    @Volatile
    private var appContext: Context? = null

    @Volatile
    private var currentActivityRef: WeakReference<Activity>? = null

    @Volatile
    private var lastKnownViewModelRef: WeakReference<Any>? = null

    @Volatile
    private var cachedRoomDbRef: WeakReference<RoomDatabase>? = null

    @Volatile
    private var lifecycleRegistered: Boolean = false

    private var activeLimitDialogRef: WeakReference<Dialog>? = null

    private var activeForceUpdateDialogRef: WeakReference<Dialog>? = null

    private var activeForceUpdateActivityRef: WeakReference<Activity>? = null

    @Volatile
    private var isForceUpdateRequired: Boolean = false

    @Volatile
    private var remoteVersionListenerRegistered: Boolean = false

    private val observedViewModels = Collections.newSetFromMap(WeakHashMap<Any, Boolean>())

    private val customChannelsCache = ConcurrentHashMap<Long, List<Any>>()
    private val customPlaylistUrls = ConcurrentHashMap<Long, String>()
    private val customPlaylistJobs = ConcurrentHashMap<Long, Job>()

    @Volatile
    private var latestCustomEntities: List<Any> = emptyList()

    @Volatile
    private var channelCopyDefaultMethod: java.lang.reflect.Method? = null

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

    private fun safeGetField(target: Any?, fieldName: String): Any? {
        if (target == null) return null
        var currentClass: Class<*>? = target.javaClass
        while (currentClass != null) {
            try {
                val field = currentClass.getDeclaredField(fieldName)
                field.isAccessible = true
                return field.get(target)
            } catch (_: NoSuchFieldException) {
                currentClass = currentClass.superclass
            } catch (t: Throwable) {
                return null
            }
        }
        return null
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
                        checkRemoteMinimumVersion(act)
                        startupBillingHelper?.queryActivePurchases { isActive ->
                            if (!isActive) {
                                enforceFreeTierLimitsIfNeeded(act)
                            }
                        }
                        if (!PremiumManager.isPremium(act)) {
                            enforceFreeTierLimitsIfNeeded(act)
                        }
                    } else if (isForceUpdateRequired) {
                        try {
                            act.finish()
                        } catch (_: Throwable) {
                        }
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
                    val forceAct = activeForceUpdateActivityRef?.get()
                    if (forceAct === act) {
                        activeForceUpdateActivityRef = null
                        activeForceUpdateDialogRef = null
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
            checkRemoteMinimumVersion(activity)
            PremiumManager.init(activity)
            if (startupBillingHelper == null) {
                startupBillingHelper = BillingHelper(activity.applicationContext)
            } else {
                startupBillingHelper?.queryActivePurchases { isActive ->
                    if (!isActive) {
                        enforceFreeTierLimitsIfNeeded(activity)
                    }
                }
            }
            if (!PremiumManager.isPremium(activity)) {
                enforceFreeTierLimitsIfNeeded(activity)
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
            lastKnownViewModelRef = WeakReference(viewModel)
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

    private fun discoverViewModelIfNeeded(): Any? {
        lastKnownViewModelRef?.get()?.let { return it }
        try {
            val helperClass = Class.forName("com.example.ui.screens.PlaylistTopSearchHelper")
            val instance = helperClass.getDeclaredField("INSTANCE").apply { isAccessible = true }.get(null)
            val vm = safeCall(instance, "getBoundViewModel")
            if (vm != null) {
                ensureViewModelFlowsSubscribed(vm)
                return vm
            }
        } catch (_: Throwable) {
        }
        return null
    }

    private fun getRepositoryInstance(viewModel: Any? = null): Any? {
        val vm = viewModel ?: lastKnownViewModelRef?.get()
        if (vm != null) {
            val repo = safeGetField(vm, "repository")
            if (repo != null) return repo
        }
        return try {
            val repoClass = Class.forName("com.example.data.repository.PlaylistRepository")
            val instanceField = repoClass.getDeclaredField("INSTANCE").apply { isAccessible = true }
            instanceField.get(null)
        } catch (_: Throwable) {
            null
        }
    }

    private fun createPlaylistSource(id: String, title: String, url: String, isPreset: Boolean): Any? {
        return try {
            val cls = Class.forName("com.example.data.repository.PlaylistSource")
            val ctor = cls.getConstructor(
                String::class.java,
                String::class.java,
                String::class.java,
                Boolean::class.javaPrimitiveType
            )
            ctor.newInstance(id, title, url, isPreset)
        } catch (t: Throwable) {
            Log.w(TAG, "createPlaylistSource failed", t)
            null
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun pushCustomChannelsToViewModel(viewModel: Any?) {
        val vm = viewModel ?: lastKnownViewModelRef?.get() ?: return
        try {
            val customMapFlow = safeGetField(vm, "_customPlaylistsChannels") as? MutableStateFlow<Map<Long, List<Any>>>
            if (customMapFlow != null) {
                customMapFlow.value = HashMap(customChannelsCache)
            }
        } catch (t: Throwable) {
            Log.w(TAG, "pushCustomChannelsToViewModel failed", t)
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun syncCustomPlaylistsChannels(viewModel: Any?, entities: Collection<*>) {
        val repo = getRepositoryInstance(viewModel) ?: return
        val activeIds = HashSet<Long>()
        val nonNullEntities = ArrayList<Any>()

        for (item in entities) {
            if (item == null) continue
            nonNullEntities.add(item)
            val id = (safeCall(item, "getId") as? Number)?.toLong() ?: continue
            val title = (safeCall(item, "getTitle") as? String)?.takeIf { it.isNotBlank() } ?: "Custom Playlist"
            val url = (safeCall(item, "getUrl") as? String)?.trim() ?: ""
            if (url.isEmpty()) continue

            activeIds.add(id)

            // Check if already cached in PlaylistRepository.playlistCache synchronously
            try {
                val repoCache = safeGetField(repo, "playlistCache") as? Map<*, *>
                val cachedList = repoCache?.get(url) as? List<Any>
                if (!cachedList.isNullOrEmpty()) {
                    customChannelsCache[id] = cachedList
                }
            } catch (_: Throwable) {
            }

            val previousUrl = customPlaylistUrls[id]
            val existingJob = customPlaylistJobs[id]
            if (previousUrl == url && (customChannelsCache[id]?.isNotEmpty() == true || existingJob?.isActive == true)) {
                continue
            }

            customPlaylistUrls[id] = url
            existingJob?.cancel()

            val job = ioScope.launch {
                try {
                    val source = createPlaylistSource("custom_$id", title, url, false) ?: return@launch
                    val loadMethod = repo.javaClass.getMethod("loadChannels", source.javaClass)
                    val flow = loadMethod.invoke(repo, source) as? Flow<*> ?: return@launch
                    flow.collect { emitted ->
                        val chList = (emitted as? List<*>)?.filterNotNull()
                        if (!chList.isNullOrEmpty()) {
                            customChannelsCache[id] = chList
                            pushCustomChannelsToViewModel(viewModel)
                        }
                    }
                } catch (t: Throwable) {
                    Log.w(TAG, "Failed loading custom playlist id=$id", t)
                }
            }
            customPlaylistJobs[id] = job
        }

        latestCustomEntities = nonNullEntities

        var removedAny = false
        val iter = customChannelsCache.keys.iterator()
        while (iter.hasNext()) {
            val key = iter.next()
            if (!activeIds.contains(key)) {
                iter.remove()
                customPlaylistUrls.remove(key)
                customPlaylistJobs.remove(key)?.cancel()
                removedAny = true
            }
        }
        if (removedAny || customChannelsCache.isNotEmpty()) {
            pushCustomChannelsToViewModel(viewModel)
        }
    }

    private val trimLock = Any()

    private fun ensureViewModelFlowsSubscribed(viewModel: Any) {
        lastKnownViewModelRef = WeakReference(viewModel)
        val shouldSubscribe = synchronized(observedViewModels) {
            observedViewModels.add(viewModel)
        }
        if (!shouldSubscribe) return

        // Subscribe directly to repository.observeCustomPlaylists() so custom playlists load immediately on startup
        try {
            val repo = getRepositoryInstance(viewModel)
            if (repo != null) {
                val daoFlow = safeCall(repo, "observeCustomPlaylists") as? Flow<*>
                if (daoFlow != null) {
                    ioScope.launch {
                        daoFlow.collect { value ->
                            val list = value as? Collection<*>
                            if (list != null) {
                                cachedPlaylistCount = list.size
                                syncCustomPlaylistsChannels(viewModel, list)
                                val ctx = resolveContext(viewModel)
                                if (!PremiumManager.isPremium(ctx)) {
                                    val activePresets = getActivePresetPlaylistCount(ctx, viewModel)
                                    if (activePresets + list.size > 3) {
                                        enforceFreeTierLimitsIfNeeded(ctx, viewModel)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Failed subscribing to observeCustomPlaylists", t)
        }

        // Keep customPlaylists, uiState, and favorites StateFlows subscribed so WhileSubscribed(5000) stays hot
        val customFlow = safeCall(viewModel, "getCustomPlaylists") as? StateFlow<*>
        if (customFlow != null) {
            hookScope.launch {
                customFlow.collect { value ->
                    val list = value as? Collection<*>
                    if (list != null) {
                        cachedPlaylistCount = list.size
                        syncCustomPlaylistsChannels(viewModel, list)
                        val ctx = resolveContext(viewModel)
                        if (!PremiumManager.isPremium(ctx)) {
                            val activePresets = getActivePresetPlaylistCount(ctx, viewModel)
                            if (activePresets + list.size > 3) {
                                enforceFreeTierLimitsIfNeeded(ctx, viewModel)
                            }
                        }
                    }
                }
            }
        }

        val uiStateFlow = safeCall(viewModel, "getUiState") as? StateFlow<*>
        if (uiStateFlow != null) {
            hookScope.launch {
                uiStateFlow.collect { state ->
                    val list = safeCall(state, "getCustomPlaylists") as? Collection<*>
                    if (list != null && list.isNotEmpty()) {
                        cachedPlaylistCount = list.size
                        syncCustomPlaylistsChannels(viewModel, list)
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
                        val ctx = resolveContext(viewModel)
                        if (!PremiumManager.isPremium(ctx) && list.size > 5) {
                            enforceFreeTierLimitsIfNeeded(ctx, viewModel)
                        }
                    }
                }
            }
        }

        val deletedPresetsFlow = safeCall(viewModel, "getDeletedPresetIds") as? StateFlow<*>
        if (deletedPresetsFlow != null) {
            hookScope.launch {
                deletedPresetsFlow.collect {
                    val ctx = resolveContext(viewModel)
                    if (!PremiumManager.isPremium(ctx)) {
                        enforceFreeTierLimitsIfNeeded(ctx, viewModel)
                    }
                }
            }
        }

        val ctx = resolveContext(viewModel)
        if (!PremiumManager.isPremium(ctx)) {
            enforceFreeTierLimitsIfNeeded(ctx, viewModel)
        }
    }

    @JvmStatic
    fun enforceFreeTierLimitsIfNeeded(context: Context? = null, viewModel: Any? = null) {
        val vm = viewModel ?: discoverViewModelIfNeeded()
        val ctx = resolveContext(vm) ?: context?.applicationContext ?: context
        if (PremiumManager.isPremium(ctx)) {
            return
        }

        ioScope.launch {
            if (PremiumManager.isPremium(ctx)) return@launch

            synchronized(trimLock) {
                try {
                    val activePresets = getActivePresetPlaylistCount(ctx, vm)
                    val maxAllowedCustom = (3 - activePresets).coerceAtLeast(0)
                    val maxAllowedFavorites = 5

                    val roomDb = getRoomDatabaseInstance(ctx, vm)
                    var trimmedAny = false
                    val deletedCustomIds = ArrayList<Long>()
                    val keptFavUrls = LinkedHashSet<String>()

                    if (roomDb != null) {
                        val supportDb = try {
                            roomDb.openHelper.writableDatabase
                        } catch (_: Throwable) {
                            null
                        }
                        if (supportDb != null) {
                            // 1. Keep only the first maxAllowedCustom custom playlists (ordered oldest first)
                            val playlistRows = ArrayList<Pair<Long, String>>()
                            supportDb.query("SELECT id, url FROM custom_playlists ORDER BY createdAt ASC, id ASC").use { cursor ->
                                while (cursor.moveToNext()) {
                                    val id = cursor.getLong(0)
                                    val url = cursor.getString(1) ?: ""
                                    playlistRows.add(id to url)
                                }
                            }
                            if (playlistRows.size > maxAllowedCustom) {
                                val repo = getRepositoryInstance(vm)
                                val repoCache = safeGetField(repo, "playlistCache") as? MutableMap<*, *>
                                for (i in maxAllowedCustom until playlistRows.size) {
                                    val (delId, delUrl) = playlistRows[i]
                                    supportDb.execSQL("DELETE FROM custom_playlists WHERE id = ?", arrayOf<Any>(delId))
                                    deletedCustomIds.add(delId)
                                    customChannelsCache.remove(delId)
                                    customPlaylistUrls.remove(delId)
                                    customPlaylistJobs.remove(delId)?.cancel()
                                    try {
                                        repoCache?.remove(delUrl)
                                    } catch (_: Throwable) {
                                    }
                                    trimmedAny = true
                                }
                                synchronized(stateLock) {
                                    cachedPlaylistCount = maxAllowedCustom
                                    optimisticPlaylistCount = maxAllowedCustom
                                }
                                pushCustomChannelsToViewModel(vm)
                            }

                            // 2. Keep only the first 5 favorites (ordered oldest first by addedAt ASC, rowid ASC)
                            val favUrls = ArrayList<String>()
                            supportDb.query("SELECT streamUrl FROM favorite_channels ORDER BY addedAt ASC, rowid ASC").use { cursor ->
                                while (cursor.moveToNext()) {
                                    val url = cursor.getString(0)?.trim()
                                    if (!url.isNullOrEmpty()) {
                                        favUrls.add(url)
                                    }
                                }
                            }
                            for (i in 0 until minOf(maxAllowedFavorites, favUrls.size)) {
                                keptFavUrls.add(favUrls[i])
                            }
                            if (favUrls.size > maxAllowedFavorites) {
                                for (i in maxAllowedFavorites until favUrls.size) {
                                    val delUrl = favUrls[i]
                                    supportDb.execSQL("DELETE FROM favorite_channels WHERE streamUrl = ?", arrayOf<Any>(delUrl))
                                    trimmedAny = true
                                }
                                synchronized(stateLock) {
                                    optimisticFavoriteUrls.clear()
                                    optimisticFavoriteUrls.addAll(keptFavUrls)
                                    hasInitializedFavoritesSet = true
                                    cachedFavoriteCount = keptFavUrls.size
                                }
                            }

                            if (trimmedAny) {
                                try {
                                    roomDb.invalidationTracker.refreshVersionsAsync()
                                } catch (t: Throwable) {
                                    Log.w(TAG, "refreshVersionsAsync failed", t)
                                }
                            }
                        }
                    } else if (ctx != null) {
                        // Direct SQLite fallback if Room instance is not yet initialized
                        val dbFile = ctx.getDatabasePath("hypnotix_iptv_db")
                        if (dbFile != null && dbFile.exists()) {
                            val flags = SQLiteDatabase.OPEN_READWRITE or SQLiteDatabase.ENABLE_WRITE_AHEAD_LOGGING
                            SQLiteDatabase.openDatabase(dbFile.absolutePath, null, flags).use { sqlite ->
                                val playlistIds = ArrayList<Long>()
                                sqlite.rawQuery("SELECT id FROM custom_playlists ORDER BY createdAt ASC, id ASC", null).use { cursor ->
                                    while (cursor.moveToNext()) {
                                        playlistIds.add(cursor.getLong(0))
                                    }
                                }
                                if (playlistIds.size > maxAllowedCustom) {
                                    for (i in maxAllowedCustom until playlistIds.size) {
                                        val delId = playlistIds[i]
                                        sqlite.execSQL("DELETE FROM custom_playlists WHERE id = ?", arrayOf<Any>(delId))
                                        deletedCustomIds.add(delId)
                                        customChannelsCache.remove(delId)
                                        customPlaylistUrls.remove(delId)
                                        customPlaylistJobs.remove(delId)?.cancel()
                                    }
                                    synchronized(stateLock) {
                                        cachedPlaylistCount = maxAllowedCustom
                                        optimisticPlaylistCount = maxAllowedCustom
                                    }
                                }

                                val favUrls = ArrayList<String>()
                                sqlite.rawQuery("SELECT streamUrl FROM favorite_channels ORDER BY addedAt ASC, rowid ASC", null).use { cursor ->
                                    while (cursor.moveToNext()) {
                                        val url = cursor.getString(0)?.trim()
                                        if (!url.isNullOrEmpty()) {
                                            favUrls.add(url)
                                        }
                                    }
                                }
                                for (i in 0 until minOf(maxAllowedFavorites, favUrls.size)) {
                                    keptFavUrls.add(favUrls[i])
                                }
                                if (favUrls.size > maxAllowedFavorites) {
                                    for (i in maxAllowedFavorites until favUrls.size) {
                                        val delUrl = favUrls[i]
                                        sqlite.execSQL("DELETE FROM favorite_channels WHERE streamUrl = ?", arrayOf<Any>(delUrl))
                                    }
                                    synchronized(stateLock) {
                                        optimisticFavoriteUrls.clear()
                                        optimisticFavoriteUrls.addAll(keptFavUrls)
                                        hasInitializedFavoritesSet = true
                                        cachedFavoriteCount = keptFavUrls.size
                                    }
                                }
                            }
                        }
                    }

                    // If a deleted custom playlist was currently active, switch to All Playlists or active preset
                    if (deletedCustomIds.isNotEmpty() && vm != null) {
                        val deletedSourceIds = deletedCustomIds.map { "custom_$it" }.toSet()
                        mainHandler.post {
                            try {
                                val selFlow = safeGetField(vm, "_selectedPlaylist") as? StateFlow<*>
                                val curId = safeCall(selFlow?.value, "getId") as? String
                                val chSelFlow = safeGetField(vm, "_channelSelectedPlaylist") as? StateFlow<*>
                                val chCurId = safeCall(chSelFlow?.value, "getId") as? String
                                if ((curId != null && deletedSourceIds.contains(curId)) ||
                                    (chCurId != null && deletedSourceIds.contains(chCurId))
                                ) {
                                    val presetCls = Class.forName("com.example.data.repository.PresetPlaylists")
                                    val presetInstance = presetCls.getDeclaredField("INSTANCE").apply { isAccessible = true }.get(null)
                                    val allSource = safeCall(presetInstance, "getALL")
                                    if (allSource != null) {
                                        val selectMethod = vm.javaClass.getMethod("selectPlaylist", allSource.javaClass)
                                        selectMethod.invoke(vm, allSource)
                                    }
                                }
                            } catch (t: Throwable) {
                                Log.w(TAG, "Failed switching away from trimmed custom playlist", t)
                            }
                        }
                    }
                } catch (t: Throwable) {
                    Log.e(TAG, "Error enforcing free tier limits", t)
                }
            }
        }
    }

    private fun getRoomDatabaseInstance(context: Context?, viewModel: Any? = null): RoomDatabase? {
        cachedRoomDbRef?.get()?.let { cached ->
            if (cached.isOpen) return cached
        }

        val vm = viewModel ?: lastKnownViewModelRef?.get()
        if (vm != null) {
            try {
                val repo = safeGetField(vm, "repository")
                val dao = safeGetField(repo, "channelDao")
                val db = safeGetField(dao, "__db") as? RoomDatabase
                if (db != null) {
                    cachedRoomDbRef = WeakReference(db)
                    return db
                }
            } catch (t: Throwable) {
                Log.w(TAG, "Unable to extract RoomDatabase from ViewModel repository", t)
            }
        }

        if (context == null) return null

        return try {
            val dbClass = Class.forName("com.example.data.local.IptvDatabase")
            val instanceField = try {
                dbClass.getDeclaredField("INSTANCE").apply { isAccessible = true }
            } catch (_: Throwable) {
                null
            }
            val existing = instanceField?.get(null) as? RoomDatabase
            if (existing != null) {
                cachedRoomDbRef = WeakReference(existing)
                return existing
            }
            val companionField = dbClass.getDeclaredField("Companion").apply { isAccessible = true }
            val companion = companionField.get(null)
            val getDbMethod = try {
                companion.javaClass.getMethod("getInstance", Context::class.java)
            } catch (_: NoSuchMethodException) {
                companion.javaClass.getMethod("getDatabase", Context::class.java)
            }
            val created = getDbMethod.invoke(companion, context.applicationContext) as? RoomDatabase
            if (created != null) {
                cachedRoomDbRef = WeakReference(created)
            }
            created
        } catch (t: Throwable) {
            Log.w(TAG, "Unable to obtain IptvDatabase via reflection", t)
            null
        }
    }

    private fun queryDbPlaylistCount(context: Context?, viewModel: Any? = null): Int {
        try {
            val roomDb = getRoomDatabaseInstance(context, viewModel)
            if (roomDb != null) {
                val supportDb = try {
                    roomDb.openHelper.writableDatabase
                } catch (_: Throwable) {
                    roomDb.openHelper.readableDatabase
                }
                supportDb.query("SELECT COUNT(*) FROM custom_playlists").use { cursor ->
                    if (cursor.moveToFirst()) {
                        return cursor.getInt(0)
                    }
                }
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Room queryDbPlaylistCount fallback triggered", t)
        }

        if (context == null) return -1

        return try {
            val dbFile = context.getDatabasePath("hypnotix_iptv_db")
            if (dbFile != null && dbFile.exists()) {
                val flags = SQLiteDatabase.OPEN_READWRITE or SQLiteDatabase.ENABLE_WRITE_AHEAD_LOGGING
                val db = try {
                    SQLiteDatabase.openDatabase(dbFile.absolutePath, null, flags)
                } catch (_: Throwable) {
                    SQLiteDatabase.openDatabase(dbFile.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
                }
                db.use { sqlite ->
                    sqlite.rawQuery("SELECT COUNT(*) FROM custom_playlists", null).use { cursor ->
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

    private fun queryDbFavoriteUrls(context: Context?, viewModel: Any? = null): Set<String>? {
        try {
            val roomDb = getRoomDatabaseInstance(context, viewModel)
            if (roomDb != null) {
                val supportDb = try {
                    roomDb.openHelper.writableDatabase
                } catch (_: Throwable) {
                    roomDb.openHelper.readableDatabase
                }
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

        if (context == null) return null

        return try {
            val dbFile = context.getDatabasePath("hypnotix_iptv_db")
            if (dbFile != null && dbFile.exists()) {
                val flags = SQLiteDatabase.OPEN_READWRITE or SQLiteDatabase.ENABLE_WRITE_AHEAD_LOGGING
                val db = try {
                    SQLiteDatabase.openDatabase(dbFile.absolutePath, null, flags)
                } catch (_: Throwable) {
                    SQLiteDatabase.openDatabase(dbFile.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
                }
                db.use { sqlite ->
                    sqlite.rawQuery("SELECT streamUrl FROM favorite_channels", null).use { cursor ->
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

    private fun getViewModelPlaylistCount(viewModel: Any?): Int {
        if (viewModel == null) return -1
        var bestCount = -1
        try {
            val customFlow = safeCall(viewModel, "getCustomPlaylists") as? StateFlow<*>
            val flowList = customFlow?.value as? Collection<*>
            if (flowList != null && flowList.isNotEmpty()) {
                bestCount = maxOf(bestCount, flowList.size)
            }
        } catch (_: Throwable) {
        }
        try {
            val uiStateFlow = safeCall(viewModel, "getUiState") as? StateFlow<*>
            val uiState = uiStateFlow?.value
            val stateList = safeCall(uiState, "getCustomPlaylists") as? Collection<*>
            if (stateList != null && stateList.isNotEmpty()) {
                bestCount = maxOf(bestCount, stateList.size)
            }
        } catch (_: Throwable) {
        }
        return bestCount
    }

    private fun getActivePresetPlaylistCount(context: Context?, viewModel: Any? = null): Int {
        val deletedIds = HashSet<String>()
        var resolvedFromVm = false
        val vm = viewModel ?: lastKnownViewModelRef?.get()
        if (vm != null) {
            try {
                val deletedFlow = safeCall(vm, "getDeletedPresetIds") as? StateFlow<*>
                val flowSet = deletedFlow?.value as? Collection<*>
                if (flowSet != null) {
                    resolvedFromVm = true
                    for (item in flowSet) {
                        val id = item?.toString()?.trim()
                        if (!id.isNullOrEmpty()) {
                            deletedIds.add(id)
                        }
                    }
                }
            } catch (_: Throwable) {
            }
        }
        if (!resolvedFromVm && context != null) {
            try {
                val prefs = context.getSharedPreferences("hypnotix_prefs", Context.MODE_PRIVATE)
                val savedSet = prefs.getStringSet("deleted_preset_ids", emptySet())
                if (savedSet != null) {
                    for (id in savedSet) {
                        if (!id.isNullOrBlank()) {
                            deletedIds.add(id.trim())
                        }
                    }
                }
            } catch (_: Throwable) {
            }
        }
        var activePresets = 0
        if (!deletedIds.contains("preset_india")) activePresets++
        if (!deletedIds.contains("preset_global")) activePresets++
        return activePresets
    }

    private fun isAppInDarkMode(context: Context, viewModel: Any? = null): Boolean {
        val vm = viewModel ?: lastKnownViewModelRef?.get()
        if (vm != null) {
            try {
                val themeFlow = safeCall(vm, "getCurrentThemeMode") as? StateFlow<*>
                val modeName = themeFlow?.value?.toString()?.uppercase()
                if (modeName == "DARK") return true
                if (modeName == "LIGHT") return false
            } catch (_: Throwable) {
            }
        }
        try {
            val prefs = context.getSharedPreferences("hypnotix_prefs", Context.MODE_PRIVATE)
            val savedMode = prefs.getString("app_theme_mode", "system")?.lowercase()
            if (savedMode == "dark") return true
            if (savedMode == "light") return false
        } catch (_: Throwable) {
        }
        val nightMask = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        return nightMask == Configuration.UI_MODE_NIGHT_YES
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
        contextFallback: Context? = null,
        viewModel: Any? = null
    ) {
        val showAction = Runnable {
            val activity = findActiveActivity() ?: unwrapActivity(contextFallback)
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
                val isDark = isAppInDarkMode(activity, viewModel)

                // Red, Black (Dark Mode) & White (Light Mode) theme palette matching AppColors
                val cardBgColor = if (isDark) {
                    AndroidColor.parseColor("#0D0D0D") // Deep Black in Dark Mode
                } else {
                    AndroidColor.parseColor("#FFFFFF") // Pure White in Light Mode
                }
                val redAccentColor = AndroidColor.parseColor("#E50914") // Hypnotix Primary Red
                val redDarkAccentColor = AndroidColor.parseColor("#B91C1C")
                val badgeFillColor = if (isDark) {
                    AndroidColor.parseColor("#26E50914")
                } else {
                    AndroidColor.parseColor("#1AE50914")
                }
                val titleTextColor = if (isDark) {
                    AndroidColor.parseColor("#FFFFFF") // White text on Black card
                } else {
                    AndroidColor.parseColor("#0D0D0D") // Black text on White card
                }
                val messageTextColor = if (isDark) {
                    AndroidColor.parseColor("#D4D4D8") // Soft White/Silver on Black card
                } else {
                    AndroidColor.parseColor("#3F3F46") // Charcoal/Black on White card
                }
                val cancelBtnBgColor = if (isDark) {
                    AndroidColor.parseColor("#1A1A1A")
                } else {
                    AndroidColor.parseColor("#F4F4F5")
                }
                val cancelBtnBorderColor = if (isDark) {
                    AndroidColor.parseColor("#2E2E2E")
                } else {
                    AndroidColor.parseColor("#E4E4E7")
                }
                val cancelBtnTextColor = if (isDark) {
                    AndroidColor.parseColor("#FFFFFF")
                } else {
                    AndroidColor.parseColor("#0D0D0D")
                }

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
                        setColor(cardBgColor)
                        setStroke(dpToPx(activity, 1.5f), redAccentColor)
                    }
                }

                // Crown Icon Circle Badge (Red accent ring + soft red fill)
                val iconBadge = TextView(activity).apply {
                    text = "👑"
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 26f)
                    gravity = Gravity.CENTER
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setColor(badgeFillColor)
                        setStroke(dpToPx(activity, 1.4f), redAccentColor)
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
                    setTextColor(titleTextColor)
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
                    setTextColor(messageTextColor)
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

                // Primary Button: Buy Subscription (Hypnotix Red Gradient + White Bold Text)
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
                            redAccentColor,
                            redDarkAccentColor
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

                // Secondary Button: Not Now (Black/White surface pill matching theme)
                val cancelButton = TextView(activity).apply {
                    text = "Not Now"
                    setTextColor(cancelBtnTextColor)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f)
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    gravity = Gravity.CENTER
                    val cancelPadV = dpToPx(activity, 11f)
                    setPadding(0, cancelPadV, 0, cancelPadV)
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.RECTANGLE
                        cornerRadius = dpToPx(activity, 25f).toFloat()
                        setColor(cancelBtnBgColor)
                        setStroke(dpToPx(activity, 1f), cancelBtnBorderColor)
                    }
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

        // Always post to mainHandler so if called right before a screen state transition
        // (such as AddPlaylistScreen closing back to MainAppScreen), the Dialog displays cleanly on top.
        mainHandler.post(showAction)
    }

    private fun cleanRemoteConfigValue(raw: String?): String {
        if (raw == null) return ""
        var s = raw.trim().trim('"', '\'')
        val colonIdx = s.indexOf(':')
        if (colonIdx >= 0) {
            s = s.substring(colonIdx + 1).trim().trim('"', '\'')
        }
        return s
    }

    private fun getCurrentAppBuildNumber(context: Context): Long {
        return try {
            val pkgInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                pkgInfo.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                pkgInfo.versionCode.toLong()
            }
        } catch (_: Throwable) {
            5L
        }
    }

    private fun getCurrentAppVersionName(context: Context): String {
        return try {
            val pkgInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            pkgInfo.versionName ?: "1.0.4"
        } catch (_: Throwable) {
            "1.0.4"
        }
    }

    private fun isSemanticVersionOlder(currentVersion: String, requiredVersion: String): Boolean {
        val curParts = currentVersion.split('.').map { it.trim().toIntOrNull() ?: 0 }
        val reqParts = requiredVersion.split('.').map { it.trim().toIntOrNull() ?: 0 }
        val maxLen = maxOf(curParts.size, reqParts.size)
        for (i in 0 until maxLen) {
            val c = curParts.getOrElse(i) { 0 }
            val r = reqParts.getOrElse(i) { 0 }
            if (c < r) return true
            if (c > r) return false
        }
        return false
    }

    private fun openPlayStoreForUpdate(context: Context) {
        val pkgName = context.packageName.ifBlank { "com.hypnotix.iptv" }
        try {
            val playStoreIntent = Intent(
                Intent.ACTION_VIEW,
                Uri.parse("market://details?id=$pkgName")
            ).apply {
                setPackage("com.android.vending")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(playStoreIntent)
            return
        } catch (_: Throwable) {
        }
        try {
            val marketIntent = Intent(
                Intent.ACTION_VIEW,
                Uri.parse("market://details?id=$pkgName")
            ).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(marketIntent)
            return
        } catch (_: Throwable) {
        }
        try {
            val webIntent = Intent(
                Intent.ACTION_VIEW,
                Uri.parse("https://play.google.com/store/apps/details?id=$pkgName")
            ).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(webIntent)
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to open Play Store for update", t)
        }
    }

    private fun showForceUpdatePopup(contextFallback: Context? = null) {
        mainHandler.post {
            val activity = findActiveActivity() ?: unwrapActivity(contextFallback)
            if (activity == null || activity.isFinishing || activity.isDestroyed) {
                return@post
            }

            try {
                val existingForce = activeForceUpdateDialogRef?.get()
                val existingAct = activeForceUpdateActivityRef?.get()
                if (existingForce != null && existingForce.isShowing && existingAct === activity) {
                    return@post
                }
                if (existingForce != null && existingForce.isShowing) {
                    existingForce.dismiss()
                }
            } catch (_: Throwable) {
            }

            try {
                activeLimitDialogRef?.get()?.let { limitDlg ->
                    if (limitDlg.isShowing) {
                        limitDlg.dismiss()
                    }
                }
            } catch (_: Throwable) {
            }

            try {
                val isDark = isAppInDarkMode(activity)

                val cardBgColor = if (isDark) {
                    AndroidColor.parseColor("#0D0D0D")
                } else {
                    AndroidColor.parseColor("#FFFFFF")
                }
                val redAccentColor = AndroidColor.parseColor("#E50914")
                val redDarkAccentColor = AndroidColor.parseColor("#B91C1C")
                val badgeFillColor = if (isDark) {
                    AndroidColor.parseColor("#26E50914")
                } else {
                    AndroidColor.parseColor("#1AE50914")
                }
                val titleTextColor = if (isDark) {
                    AndroidColor.parseColor("#FFFFFF")
                } else {
                    AndroidColor.parseColor("#0D0D0D")
                }
                val messageTextColor = if (isDark) {
                    AndroidColor.parseColor("#D4D4D8")
                } else {
                    AndroidColor.parseColor("#3F3F46")
                }

                val dialog = Dialog(activity)
                dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
                dialog.setCancelable(false)
                dialog.setCanceledOnTouchOutside(false)
                dialog.setOnKeyListener { _, keyCode, _ ->
                    keyCode == KeyEvent.KEYCODE_BACK
                }

                val rootCard = LinearLayout(activity).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER_HORIZONTAL
                    val padH = dpToPx(activity, 24f)
                    val padV = dpToPx(activity, 26f)
                    setPadding(padH, padV, padH, padV)
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.RECTANGLE
                        cornerRadius = dpToPx(activity, 24f).toFloat()
                        setColor(cardBgColor)
                        setStroke(dpToPx(activity, 1.6f), redAccentColor)
                    }
                }

                val iconBadge = TextView(activity).apply {
                    text = "🚀"
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 28f)
                    gravity = Gravity.CENTER
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setColor(badgeFillColor)
                        setStroke(dpToPx(activity, 1.5f), redAccentColor)
                    }
                }
                val badgeSize = dpToPx(activity, 64f)
                rootCard.addView(
                    iconBadge,
                    LinearLayout.LayoutParams(badgeSize, badgeSize).apply {
                        bottomMargin = dpToPx(activity, 14f)
                    }
                )

                val tagPill = TextView(activity).apply {
                    text = "UPDATE REQUIRED"
                    setTextColor(redAccentColor)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f)
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    gravity = Gravity.CENTER
                    val pillH = dpToPx(activity, 10f)
                    val pillV = dpToPx(activity, 4f)
                    setPadding(pillH, pillV, pillH, pillV)
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.RECTANGLE
                        cornerRadius = dpToPx(activity, 12f).toFloat()
                        setColor(badgeFillColor)
                        setStroke(dpToPx(activity, 1f), redAccentColor)
                    }
                }
                rootCard.addView(
                    tagPill,
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    ).apply {
                        bottomMargin = dpToPx(activity, 12f)
                    }
                )

                val titleView = TextView(activity).apply {
                    text = "App Version Outdated"
                    setTextColor(titleTextColor)
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

                val messageView = TextView(activity).apply {
                    text = "This version of the app is outdated. Please update the app from Google Play Store to continue watching seamlessly."
                    setTextColor(messageTextColor)
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
                        bottomMargin = dpToPx(activity, 24f)
                    }
                )

                val updateButton = TextView(activity).apply {
                    text = "Update Now"
                    setTextColor(AndroidColor.WHITE)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 15.5f)
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    gravity = Gravity.CENTER
                    val btnPadV = dpToPx(activity, 13.5f)
                    setPadding(0, btnPadV, 0, btnPadV)
                    background = GradientDrawable(
                        GradientDrawable.Orientation.LEFT_RIGHT,
                        intArrayOf(
                            redAccentColor,
                            redDarkAccentColor
                        )
                    ).apply {
                        shape = GradientDrawable.RECTANGLE
                        cornerRadius = dpToPx(activity, 25f).toFloat()
                    }
                    setOnClickListener {
                        openPlayStoreForUpdate(activity)
                    }
                }
                rootCard.addView(
                    updateButton,
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

                activeForceUpdateDialogRef = WeakReference(dialog)
                activeForceUpdateActivityRef = WeakReference(activity)
                dialog.show()
            } catch (t: Throwable) {
                Log.e(TAG, "Failed to show force update dialog", t)
            }
        }
    }

    private fun evaluateRemoteMinimumVersion(contextFallback: Context? = null) {
        try {
            val ctx = resolveContext() ?: contextFallback?.applicationContext ?: contextFallback ?: return
            val frc = FirebaseRemoteConfig.getInstance()
            val candidateKeys = listOf("Version", "version", "Minimum_version", "Min_version", "min_version")
            var rawValue = ""
            for (key in candidateKeys) {
                val v = cleanRemoteConfigValue(frc.getString(key))
                if (v.isNotEmpty()) {
                    rawValue = v
                    break
                }
            }

            val prefs = ctx.getSharedPreferences("hypnotix_prefs", Context.MODE_PRIVATE)
            if (rawValue.isNotEmpty()) {
                prefs.edit().putString("cached_remote_min_version", rawValue).apply()
            } else {
                rawValue = prefs.getString("cached_remote_min_version", "") ?: ""
            }

            if (rawValue.isEmpty()) {
                isForceUpdateRequired = false
                return
            }

            val currentBuild = getCurrentAppBuildNumber(ctx)
            val parsedBuild = rawValue.toLongOrNull()
                ?: if (rawValue.contains('.') && rawValue.substringAfter('.').all { it == '0' }) {
                    rawValue.substringBefore('.').toLongOrNull()
                } else {
                    null
                }

            val outdated = if (parsedBuild != null) {
                parsedBuild > 0L && currentBuild < parsedBuild
            } else if (rawValue.contains('.')) {
                isSemanticVersionOlder(getCurrentAppVersionName(ctx), rawValue)
            } else {
                false
            }

            isForceUpdateRequired = outdated
            if (outdated) {
                showForceUpdatePopup(contextFallback ?: ctx)
            } else {
                mainHandler.post {
                    try {
                        activeForceUpdateDialogRef?.get()?.let { dlg ->
                            if (dlg.isShowing) {
                                dlg.dismiss()
                            }
                        }
                        activeForceUpdateDialogRef = null
                        activeForceUpdateActivityRef = null
                    } catch (_: Throwable) {
                    }
                }
            }
        } catch (t: Throwable) {
            Log.w(TAG, "evaluateRemoteMinimumVersion failed", t)
        }
    }

    @JvmStatic
    fun checkRemoteMinimumVersion(context: Context? = null) {
        try {
            updateContextAndActivity(context)
            evaluateRemoteMinimumVersion(context)

            val frc = FirebaseRemoteConfig.getInstance()
            val settings = FirebaseRemoteConfigSettings.Builder()
                .setMinimumFetchIntervalInSeconds(0L)
                .build()
            frc.setConfigSettingsAsync(settings)

            frc.fetchAndActivate().addOnCompleteListener {
                evaluateRemoteMinimumVersion(findActiveActivity() ?: context)
            }

            if (!remoteVersionListenerRegistered) {
                synchronized(this) {
                    if (!remoteVersionListenerRegistered) {
                        remoteVersionListenerRegistered = true
                        frc.addOnConfigUpdateListener(object : ConfigUpdateListener {
                            override fun onUpdate(configUpdate: ConfigUpdate) {
                                frc.activate().addOnCompleteListener {
                                    evaluateRemoteMinimumVersion(findActiveActivity() ?: context)
                                }
                            }

                            override fun onError(error: FirebaseRemoteConfigException) {
                                Log.w(TAG, "Remote config update listener error", error)
                            }
                        })
                    }
                }
            }
        } catch (t: Throwable) {
            Log.w(TAG, "checkRemoteMinimumVersion failed", t)
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

    private fun copyChannelWithPlaylistTitle(channel: Any, playlistTitle: String): Any {
        try {
            var method = channelCopyDefaultMethod
            if (method == null) {
                val cls = channel.javaClass
                method = cls.declaredMethods.firstOrNull {
                    it.name == "copy\$default" && it.parameterTypes.size == 15
                }?.apply { isAccessible = true }
                channelCopyDefaultMethod = method
            }
            if (method != null) {
                return method.invoke(
                    null,
                    channel,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    false,
                    playlistTitle,
                    null,
                    null,
                    false,
                    3839,
                    null
                ) ?: channel
            }
        } catch (t: Throwable) {
            Log.w(TAG, "copyChannelWithPlaylistTitle failed", t)
        }
        return channel
    }

    private fun interleaveByCategory(channels: List<Any>): List<Any> {
        if (channels.size <= 2) return channels
        val byCategory = LinkedHashMap<String, ArrayList<Any>>()
        for (ch in channels) {
            val cat = (safeCall(ch, "getCategory") as? String)?.trim()?.takeIf { it.isNotEmpty() } ?: "General"
            byCategory.getOrPut(cat) { ArrayList() }.add(ch)
        }
        if (byCategory.size <= 1) return channels

        val categoryLists = ArrayList(byCategory.values)
        val maxCatSize = categoryLists.maxOfOrNull { it.size } ?: 0
        val result = ArrayList<Any>(channels.size)
        for (i in 0 until maxCatSize) {
            for (catList in categoryLists) {
                if (i < catList.size) {
                    result.add(catList[i])
                }
            }
        }
        return result
    }

    private fun getPresetFallbackChannels(fieldName: String): List<Any> {
        return try {
            val cls = Class.forName("com.example.data.repository.PresetPlaylists")
            val field = cls.getDeclaredField(fieldName).apply { isAccessible = true }
            (field.get(null) as? List<*>)?.filterNotNull() ?: emptyList()
        } catch (_: Throwable) {
            emptyList()
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun buildMixedAllPlaylistsChannels(args: Array<*>, outList: MutableList<Any>) {
        try {
            val vm = discoverViewModelIfNeeded()
            val repo = getRepositoryInstance(vm)
            val repoCache = safeGetField(repo, "playlistCache") as? Map<*, *>

            val indiaRaw = (args.getOrNull(0) as? List<*>)?.filterNotNull() ?: emptyList()
            val globalRaw = (args.getOrNull(1) as? List<*>)?.filterNotNull() ?: emptyList()
            val customMap = args.getOrNull(2) as? Map<*, *> ?: emptyMap<Any, Any>()
            val cListsArg = (args.getOrNull(3) as? List<*>)?.filterNotNull() ?: emptyList()
            val deletedPresetsArg = args.getOrNull(4) as? Collection<*> ?: emptySet<String>()

            val deletedSet = HashSet<String>()
            for (item in deletedPresetsArg) {
                val id = item?.toString()?.trim()
                if (!id.isNullOrEmpty()) {
                    deletedSet.add(id)
                }
            }

            val effectiveCustomEntities = if (cListsArg.isNotEmpty()) {
                cListsArg
            } else {
                latestCustomEntities
            }

            if (effectiveCustomEntities.isNotEmpty()) {
                syncCustomPlaylistsChannels(vm, effectiveCustomEntities)
            }

            val activePlaylistBuckets = ArrayList<List<Any>>()

            // 1. Collect all currently added Custom Playlists' channels
            for (entity in effectiveCustomEntities) {
                val id = (safeCall(entity, "getId") as? Number)?.toLong() ?: continue
                val title = (safeCall(entity, "getTitle") as? String)?.takeIf { it.isNotBlank() } ?: "Custom Playlist"
                val url = (safeCall(entity, "getUrl") as? String)?.trim() ?: ""

                val rawChannels: List<Any> = ((customMap[id] as? List<*>)?.filterNotNull()?.takeIf { it.isNotEmpty() })
                    ?: (customChannelsCache[id]?.takeIf { it.isNotEmpty() })
                    ?: ((repoCache?.get(url) as? List<*>)?.filterNotNull()?.takeIf { it.isNotEmpty() }?.also {
                        customChannelsCache[id] = it
                    })
                    ?: emptyList()

                if (rawChannels.isNotEmpty()) {
                    val tagged = ArrayList<Any>(rawChannels.size)
                    for (ch in rawChannels) {
                        tagged.add(copyChannelWithPlaylistTitle(ch, title))
                    }
                    activePlaylistBuckets.add(interleaveByCategory(tagged))
                }
            }

            // 2. Collect India Channels if not deleted
            if (!deletedSet.contains("preset_india")) {
                val effectiveIndia = indiaRaw.ifEmpty {
                    (repoCache?.get("https://iptv-org.github.io/iptv/countries/in.m3u") as? List<*>)?.filterNotNull()
                        ?.takeIf { it.isNotEmpty() }
                        ?: getPresetFallbackChannels("FALLBACK_INDIA_CHANNELS")
                }
                if (effectiveIndia.isNotEmpty()) {
                    val tagged = ArrayList<Any>(effectiveIndia.size)
                    for (ch in effectiveIndia) {
                        tagged.add(copyChannelWithPlaylistTitle(ch, "India Channels"))
                    }
                    activePlaylistBuckets.add(interleaveByCategory(tagged))
                }
            }

            // 3. Collect Global Channels if not deleted
            if (!deletedSet.contains("preset_global")) {
                val effectiveGlobal = globalRaw.ifEmpty {
                    (repoCache?.get("https://iptv-org.github.io/iptv/index.m3u") as? List<*>)?.filterNotNull()
                        ?.takeIf { it.isNotEmpty() }
                        ?: getPresetFallbackChannels("FALLBACK_GLOBAL_CHANNELS")
                }
                if (effectiveGlobal.isNotEmpty()) {
                    val tagged = ArrayList<Any>(effectiveGlobal.size)
                    for (ch in effectiveGlobal) {
                        tagged.add(copyChannelWithPlaylistTitle(ch, "Global Channels"))
                    }
                    activePlaylistBuckets.add(interleaveByCategory(tagged))
                }
            }

            if (activePlaylistBuckets.isEmpty()) {
                return
            }

            // 4. Round-robin mix across all active playlists and deduplicate by streamUrl
            outList.clear()
            val seenUrls = HashSet<String>()
            val maxBucketSize = activePlaylistBuckets.maxOfOrNull { it.size } ?: 0

            for (i in 0 until maxBucketSize) {
                for (bucket in activePlaylistBuckets) {
                    if (i < bucket.size) {
                        val ch = bucket[i]
                        val streamUrl = (safeCall(ch, "getStreamUrl") as? String)?.trim() ?: ""
                        val key = if (streamUrl.isNotEmpty()) streamUrl else ch.hashCode().toString()
                        if (seenUrls.add(key)) {
                            outList.add(ch)
                        }
                    }
                }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "buildMixedAllPlaylistsChannels failed; keeping existing list", t)
        }
    }

    @JvmStatic
    fun isPresetDeleted(viewModel: Any?, presetId: String): Boolean {
        try {
            val vm = viewModel ?: discoverViewModelIfNeeded()
            val ctx = resolveContext(vm)
            if (vm != null) {
                val deletedFlow = safeCall(vm, "getDeletedPresetIds") as? StateFlow<*>
                val flowSet = deletedFlow?.value as? Collection<*>
                if (flowSet != null) {
                    for (item in flowSet) {
                        if (item?.toString()?.trim() == presetId) {
                            return true
                        }
                    }
                    return false
                }
            }
            if (ctx != null) {
                val prefs = ctx.getSharedPreferences("hypnotix_prefs", Context.MODE_PRIVATE)
                val savedSet = prefs.getStringSet("deleted_preset_ids", emptySet())
                if (savedSet != null && savedSet.contains(presetId)) {
                    return true
                }
            }
        } catch (t: Throwable) {
            Log.w(TAG, "isPresetDeleted check failed", t)
        }
        return false
    }

    @JvmStatic
    fun sanitizeRestoredPlaylist(viewModel: Any?, restored: Any?): Any? {
        if (restored == null) return null
        try {
            val id = (safeCall(restored, "getId") as? String)?.trim() ?: ""
            if (id.isEmpty()) return restored

            val vm = viewModel ?: discoverViewModelIfNeeded()
            val ctx = resolveContext(vm)

            // If restored playlist is a deleted preset, fallback to All Playlists or available preset
            if (isPresetDeleted(vm, id)) {
                val presetCls = Class.forName("com.example.data.repository.PresetPlaylists")
                val presetInstance = presetCls.getDeclaredField("INSTANCE").apply { isAccessible = true }.get(null)
                // Prefer ALL
                val allSource = safeCall(presetInstance, "getALL")
                if (allSource != null) {
                    return allSource
                }
                // Fallback to non-deleted India or Global
                if (!isPresetDeleted(vm, "preset_india")) {
                    return safeCall(presetInstance, "getINDIA") ?: restored
                }
                if (!isPresetDeleted(vm, "preset_global")) {
                    return safeCall(presetInstance, "getGLOBAL") ?: restored
                }
            }
        } catch (t: Throwable) {
            Log.w(TAG, "sanitizeRestoredPlaylist failed", t)
        }
        return restored
    }

    @Suppress("UNCHECKED_CAST")
    private fun onPlaylistSelectedHook(viewModel: Any, source: Any) {
        try {
            ensureViewModelFlowsSubscribed(viewModel)
            // Critical fix: Reset filters to null so ChannelListScreen does not filter channels by name "All"
            (safeGetField(viewModel, "_selectedCategory") as? MutableStateFlow<String?>)?.value = null
            (safeGetField(viewModel, "_channelSelectedCategory") as? MutableStateFlow<String?>)?.value = null
            (safeGetField(viewModel, "_selectedCountry") as? MutableStateFlow<String?>)?.value = null
            (safeGetField(viewModel, "_selectedLanguage") as? MutableStateFlow<String?>)?.value = null
            (safeGetField(viewModel, "_searchQuery") as? MutableStateFlow<String>)?.value = ""

            val sourceId = safeCall(source, "getId") as? String
            val sourceTitle = (safeCall(source, "getTitle") as? String) ?: ""
            val sourceUrl = (safeCall(source, "getUrl") as? String) ?: ""
            val isPreset = (safeCall(source, "isPreset") as? Boolean) ?: false

            // Save persistent selection in hypnotix_prefs
            val ctx = resolveContext(viewModel)
            if (ctx != null && !sourceId.isNullOrEmpty()) {
                val prefs = ctx.getSharedPreferences("hypnotix_prefs", Context.MODE_PRIVATE)
                prefs.edit()
                    .putBoolean("has_selected_first_playlist", true)
                    .putString("selected_playlist_id", sourceId)
                    .putString("selected_playlist_title", sourceTitle)
                    .putString("selected_playlist_url", sourceUrl)
                    .putBoolean("selected_playlist_is_preset", isPreset)
                    .putString("channel_selected_playlist_id", sourceId)
                    .putString("channel_selected_playlist_title", sourceTitle)
                    .putString("channel_selected_playlist_url", sourceUrl)
                    .putBoolean("channel_selected_playlist_is_preset", isPreset)
                    .apply()
            }

            // Sync custom playlists channels if ALL or custom playlist is selected
            if (sourceId == "preset_all" || sourceId?.startsWith("custom_") == true) {
                if (latestCustomEntities.isNotEmpty()) {
                    syncCustomPlaylistsChannels(viewModel, latestCustomEntities)
                }
                pushCustomChannelsToViewModel(viewModel)
            }
        } catch (t: Throwable) {
            Log.w(TAG, "onPlaylistSelectedHook failed", t)
        }
    }

    @JvmStatic
    fun shouldBlockPlayChannel(viewModel: Any, channel: Any): Boolean {
        // Hook C: Called from IptvViewModel$special$$inlined$combine$1$3.invokeSuspend with (Object[] args, ArrayList outList)
        if (viewModel is Array<*> && channel is MutableList<*>) {
            @Suppress("UNCHECKED_CAST")
            buildMixedAllPlaylistsChannels(viewModel, channel as MutableList<Any>)
            return false
        }

        // Hook A: Called from IptvViewModel.<init> with (IptvViewModel, Flow[] flows)
        if (channel is Array<*>) {
            resolveContext(viewModel)
            ensureViewModelFlowsSubscribed(viewModel)
            return false
        }

        // Hook B: Called from IptvViewModel.selectPlaylist with (IptvViewModel, PlaylistSource)
        if (channel.javaClass.name.endsWith("PlaylistSource")) {
            resolveContext(viewModel)
            onPlaylistSelectedHook(viewModel, channel)
            return false
        }

        val ctx = resolveContext(viewModel)
        ensureViewModelFlowsSubscribed(viewModel)
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
            val dbCount = queryDbPlaylistCount(ctx, viewModel)
            val vmCount = getViewModelPlaylistCount(viewModel)

            // Sync optimistic custom count with live DB/ViewModel state after rapid insert window settles
            if (now - lastPlaylistAddMs > 1800L) {
                if (dbCount >= 0 && vmCount >= 0) {
                    optimisticPlaylistCount = maxOf(dbCount, vmCount)
                } else if (dbCount >= 0) {
                    optimisticPlaylistCount = dbCount
                } else if (vmCount >= 0) {
                    optimisticPlaylistCount = vmCount
                }
            }

            val currentCustomCount = maxOf(
                dbCount,
                vmCount,
                optimisticPlaylistCount,
                if (dbCount < 0 && vmCount < 0) cachedPlaylistCount else 0
            )

            cachedPlaylistCount = currentCustomCount

            val activePresetCount = getActivePresetPlaylistCount(ctx, viewModel)
            val totalPlaylistCount = activePresetCount + currentCustomCount

            if (!PremiumManager.canAddPlaylist(totalPlaylistCount)) {
                showLimitReachedPopup(
                    title = "Playlist Limit Reached",
                    message = "You already have 3 playlists (including preset playlists). To remove this limit and add unlimited playlists, please buy a subscription.",
                    contextFallback = ctx,
                    viewModel = viewModel
                )
                return true
            }

            optimisticPlaylistCount = currentCustomCount + 1
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

            val dbFavUrls = queryDbFavoriteUrls(ctx, viewModel)

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
                    contextFallback = ctx,
                    viewModel = viewModel
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
            checkRemoteMinimumVersion(context)
            delay(3500L)
            if (!isForceUpdateRequired && PremiumManager.shouldShowDailyPrompt(context)) {
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
