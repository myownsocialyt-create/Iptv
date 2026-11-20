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
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStoreOwner
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.room.RoomDatabase
import com.google.firebase.remoteconfig.ConfigUpdate
import com.google.firebase.remoteconfig.ConfigUpdateListener
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigException
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings
import java.lang.ref.WeakReference
import java.net.HttpURLConnection
import java.net.URL
import java.util.Collections
import java.util.Locale
import java.util.UUID
import java.util.WeakHashMap
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import org.json.JSONObject

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

    private val remoteRestFetchInFlight = AtomicBoolean(false)

    @Volatile
    private var periodicRemoteCheckStarted: Boolean = false

    @Volatile
    private var hasLiveRestVersionInSession: Boolean = false

    @Volatile
    private var lastLiveRestVersion: String = ""

    @Volatile
    private var lastAppliedAdConfigSignature: String? = null

    private val observedViewModels = Collections.newSetFromMap(WeakHashMap<Any, Boolean>())
    private val observedDerivedFlowViewModels = Collections.newSetFromMap(WeakHashMap<Any, Boolean>())
    private val attachedPlayWhenReadyPlayers = Collections.newSetFromMap(WeakHashMap<ExoPlayer, Boolean>())

    @Volatile
    private var pipObserverJob: Job? = null

    @Volatile
    private var pausedWhileBufferingByStop: Boolean = false

    @Volatile
    private var cachedIptvViewModelClass: Class<*>? = null

    @Volatile
    private var cachedUpdatePipParamsMethod: java.lang.reflect.Method? = null

    @Volatile
    private var cachedWasPlayingField: java.lang.reflect.Field? = null

    @Volatile
    private var cachedIsPipModeStateField: java.lang.reflect.Field? = null

    private val customChannelsCache = ConcurrentHashMap<Long, List<Any>>()
    private val customPlaylistUrls = ConcurrentHashMap<Long, String>()
    private val customPlaylistJobs = ConcurrentHashMap<Long, Job>()
    private val notifiedCustomKeys = ConcurrentHashMap.newKeySet<String>()
    private val recentlyDeletedCustomIds = ConcurrentHashMap<Long, Long>()
    private val splitAllRefreshToken = AtomicLong(0L)

    @Volatile
    private var splitAllRefreshJob: Job? = null

    @Volatile
    private var splitCustomSyncJob: Job? = null

    @Volatile
    private var latestCustomEntities: List<Any> = emptyList()

    @Volatile
    private var recentlySelectedCustomId: Long = -1L

    @Volatile
    private var recentlySelectedCustomAtMs: Long = 0L

    @Volatile
    private var recentlySelectedCustomSource: Any? = null

    @Volatile
    private var lastAutoOpenAddPopupMs: Long = 0L

    @Volatile
    private var pendingHomeDropdownTarget: Any? = null

    @Volatile
    private var pendingBannerDropdownVm: Any? = null

    @Volatile
    private var pendingBannerDropdownCustomList: List<*>? = null

    @Volatile
    private var pendingBannerDropdownMenuState: MutableState<Boolean>? = null

    @Volatile
    private var pendingAppearanceTarget: Any? = null

    @Volatile
    private var mainScreenScaffoldTargetRef: WeakReference<Any>? = null

    @Volatile
    private var isReconcilingSelection: Boolean = false

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
                        NotificationSyncBridge.onActivityCreatedOrStarted(act)
                    }
                }

                override fun onActivityStarted(act: Activity) {
                    if (act !is SubscriptionActivity) {
                        currentActivityRef = WeakReference(act)
                        NotificationSyncBridge.onActivityCreatedOrStarted(act)
                        onMainActivityStartedHook(act)
                    }
                }

                override fun onActivityResumed(act: Activity) {
                    if (act !is SubscriptionActivity) {
                        currentActivityRef = WeakReference(act)
                        NotificationSyncBridge.onActivityResumed(act)
                        if (isMainActivity(act) && !act.isInPictureInPictureMode) {
                            resetStuckPipState(act)
                        }
                        checkRemoteMinimumVersion(act)
                        PlayStoreUpdatePrompt.onActivityResumed(act)
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

                override fun onActivityPaused(act: Activity) {
                    PlayStoreUpdatePrompt.onActivityPaused(act)
                }
                override fun onActivityStopped(act: Activity) {
                    if (isMainActivity(act)) {
                        onMainActivityStoppedHook(act)
                    }
                }
                override fun onActivitySaveInstanceState(act: Activity, outState: Bundle) {}
                override fun onActivityDestroyed(act: Activity) {
                    if (isMainActivity(act)) {
                        pipObserverJob?.cancel()
                        pipObserverJob = null
                    }
                    val current = currentActivityRef?.get()
                    if (current === act) {
                        currentActivityRef = null
                    }
                    val forceAct = activeForceUpdateActivityRef?.get()
                    if (forceAct === act) {
                        activeForceUpdateActivityRef = null
                        activeForceUpdateDialogRef = null
                    }
                    PlayStoreUpdatePrompt.onActivityDestroyed(act)
                }
            })
        }
    }

    @JvmStatic
    fun onMainActivityCreate(activity: Activity) {
        try {
            appContext = activity.applicationContext
            com.example.data.repository.ChannelDiskCache.initAndPrewarm(activity.applicationContext)
            currentActivityRef = WeakReference(activity)
            ensureLifecycleCallbacks(activity)
            NotificationSyncBridge.onActivityCreatedOrStarted(activity)
            NotificationSyncBridge.syncRemoteSourcesNow(activity)
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

    private fun getPresetSourceByName(getterName: String): Any? {
        return try {
            val presetCls = Class.forName("com.example.data.repository.PresetPlaylists")
            val presetInstance = presetCls.getDeclaredField("INSTANCE").apply { isAccessible = true }.get(null)
            safeCall(presetInstance, getterName)
        } catch (_: Throwable) {
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

    private fun isCustomIdRecentlyDeleted(id: Long, now: Long = System.currentTimeMillis()): Boolean {
        val expiry = recentlyDeletedCustomIds[id] ?: return false
        if (now < expiry) return true
        recentlyDeletedCustomIds.remove(id, expiry)
        return false
    }

    @Suppress("UNCHECKED_CAST")
    private fun bumpRefreshTriggers(viewModel: Any) {
        val refreshFlow = safeGetField(viewModel, "_refreshTrigger") as? MutableStateFlow<Long>
        val chRefreshFlow = safeGetField(viewModel, "_channelRefreshTrigger") as? MutableStateFlow<Long>
        val now = System.currentTimeMillis()
        if (refreshFlow != null) {
            refreshFlow.value = maxOf(now, refreshFlow.value + 1L)
        }
        if (chRefreshFlow != null) {
            chRefreshFlow.value = maxOf(now + 1L, chRefreshFlow.value + 1L)
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun setPlaylistLoadingState(viewModel: Any, loading: Boolean) {
        try {
            val loadingFlow = safeGetField(viewModel, "_isPlaylistLoading") as? MutableStateFlow<Boolean>
            loadingFlow?.value = loading
        } catch (_: Throwable) {
        }
    }

    private fun clearRepoCacheSafe(repo: Any?, url: String?) {
        if (repo == null) return
        try {
            val clearMethod = repo.javaClass.getMethod("clearCache", String::class.java)
            clearMethod.invoke(repo, url)
        } catch (t: Throwable) {
            Log.w(TAG, "clearRepoCacheSafe failed", t)
        }
    }

    private fun collectActiveCustomEntities(viewModel: Any): List<Any> {
        val now = System.currentTimeMillis()
        val rawEntities: List<Any> = latestCustomEntities.ifEmpty {
            ((safeCall(viewModel, "getCustomPlaylists") as? StateFlow<*>)?.value as? Collection<*>)
                ?.filterNotNull()
                ?: emptyList()
        }
        return rawEntities.filter { entity ->
            val id = (safeCall(entity, "getId") as? Number)?.toLong() ?: return@filter false
            !isCustomIdRecentlyDeleted(id, now)
        }
    }

    private suspend fun fetchPlaylistSourceNow(repo: Any, source: Any): List<Any>? {
        return try {
            val loadMethod = repo.javaClass.getMethod("loadChannels", source.javaClass)
            val flow = loadMethod.invoke(repo, source) as? Flow<*> ?: return null
            var latestNonEmpty: List<Any>? = null
            flow.collect { emitted ->
                val list = (emitted as? List<*>)?.filterNotNull()
                if (!list.isNullOrEmpty()) {
                    latestNonEmpty = list
                }
            }
            latestNonEmpty
        } catch (ce: CancellationException) {
            throw ce
        } catch (t: Throwable) {
            Log.w(TAG, "fetchPlaylistSourceNow failed", t)
            null
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun bumpMatchingRefreshTriggers(viewModel: Any, targetId: String, targetUrl: String) {
        val refreshFlow = safeGetField(viewModel, "_refreshTrigger") as? MutableStateFlow<Long>
        val chRefreshFlow = safeGetField(viewModel, "_channelRefreshTrigger") as? MutableStateFlow<Long>
        val selPlaylist = (safeGetField(viewModel, "_selectedPlaylist") as? StateFlow<*>)?.value
        val chSelPlaylist = (safeGetField(viewModel, "_channelSelectedPlaylist") as? StateFlow<*>)?.value

        val matchesHome = matchesPlaylistTarget(selPlaylist, targetId, targetUrl)
        val matchesChannel = matchesPlaylistTarget(chSelPlaylist, targetId, targetUrl)
        val now = System.currentTimeMillis()

        if (matchesHome && refreshFlow != null) {
            refreshFlow.value = maxOf(now, refreshFlow.value + 1L)
        }
        if (matchesChannel && chRefreshFlow != null) {
            chRefreshFlow.value = maxOf(now + 1L, chRefreshFlow.value + 1L)
        }
        if (!matchesHome && !matchesChannel) {
            if (chRefreshFlow != null) {
                chRefreshFlow.value = maxOf(now + 1L, chRefreshFlow.value + 1L)
            } else if (refreshFlow != null) {
                refreshFlow.value = maxOf(now, refreshFlow.value + 1L)
            }
        }
    }

    private fun matchesPlaylistTarget(playlist: Any?, targetId: String, targetUrl: String): Boolean {
        if (playlist == null) return false
        val id = (safeCall(playlist, "getId") as? String)?.trim().orEmpty()
        val url = (safeCall(playlist, "getUrl") as? String)?.trim().orEmpty()
        if (targetId.isNotEmpty() && id == targetId) return true
        if (targetUrl.isNotEmpty() && url == targetUrl) return true
        return false
    }

    private fun isUrlCachedInRepo(repo: Any, url: String): Boolean {
        if (url.isEmpty()) return false
        return try {
            val cacheMap = safeGetField(repo, "playlistCache") as? Map<*, *>
            val cachedList = cacheMap?.get(url) as? Collection<*>
            cachedList != null && cachedList.isNotEmpty()
        } catch (_: Throwable) {
            false
        }
    }

    @JvmStatic
    fun forceRefreshSplitChannels(viewModel: Any?, currentPlaylist: Any?) {
        forceRefreshSplitChannels(viewModel, currentPlaylist, null)
    }

    @JvmStatic
    fun forceRefreshSplitChannels(viewModel: Any?, currentPlaylist: Any?, activeChannel: Any?) {
        val vm = viewModel ?: discoverViewModelIfNeeded() ?: return
        com.example.data.repository.ChannelDiskCache.registerViewModel(vm)
        val repo = getRepositoryInstance(vm)

        val resolvedPlaylist = currentPlaylist
            ?: (safeGetField(vm, "_channelSelectedPlaylist") as? StateFlow<*>)?.value
            ?: (safeGetField(vm, "_selectedPlaylist") as? StateFlow<*>)?.value

        val currentId = (safeCall(resolvedPlaylist, "getId") as? String)?.trim().orEmpty()
        val currentUrl = (safeCall(resolvedPlaylist, "getUrl") as? String)?.trim().orEmpty()
        val currentTitle = (safeCall(resolvedPlaylist, "getTitle") as? String)?.trim().orEmpty()

        when {
            currentId == "none" -> return
            currentId == "preset_all" -> {
                refreshMissingOrActiveInAllPlaylists(vm, repo, activeChannel)
            }
            currentId.startsWith("custom_") -> {
                refreshCustomPlaylistFromNetwork(vm, repo, currentId, currentTitle, currentUrl)
            }
            currentUrl.isNotEmpty() -> {
                cancelPendingAllPlaylistsRefresh()
                clearRepoCacheSafe(repo, currentUrl)
                setPlaylistLoadingState(vm, true)
                bumpMatchingRefreshTriggers(vm, currentId, currentUrl)
            }
        }
    }

    private fun cancelPendingAllPlaylistsRefresh() {
        splitAllRefreshToken.incrementAndGet()
        splitAllRefreshJob?.cancel()
        splitAllRefreshJob = null
        com.example.data.repository.ChannelDiskCache.setAllPlaylistsRefreshing(false)
    }

    private fun resolveChannelSourceInAll(
        vm: Any,
        activeChannel: Any?,
        presetSources: List<Any>,
        customEntities: List<Any>
    ): Pair<Any?, Long?> {
        val playlistName = (safeCall(activeChannel, "getPlaylistName") as? String)?.trim().orEmpty()
        if (playlistName.isEmpty()) return Pair(null, null)

        for (entity in customEntities) {
            val id = (safeCall(entity, "getId") as? Number)?.toLong() ?: continue
            val title = (safeCall(entity, "getTitle") as? String)?.trim().orEmpty()
            val url = (safeCall(entity, "getUrl") as? String)?.trim().orEmpty()
            if (url.isNotEmpty() && title.equals(playlistName, ignoreCase = true)) {
                return Pair(createPlaylistSource("custom_$id", title, url, false), id)
            }
        }
        for (source in presetSources) {
            val id = (safeCall(source, "getId") as? String)?.trim().orEmpty()
            val title = (safeCall(source, "getTitle") as? String)?.trim().orEmpty()
            val matchesIndia = id == "preset_india" && playlistName.contains("india", ignoreCase = true)
            val matchesGlobal = id == "preset_global" && playlistName.contains("global", ignoreCase = true)
            if (title.equals(playlistName, ignoreCase = true) || matchesIndia || matchesGlobal) {
                return Pair(source, null)
            }
        }
        return Pair(null, null)
    }

    @Suppress("UNCHECKED_CAST")
    private fun refreshMissingOrActiveInAllPlaylists(vm: Any, repo: Any?, activeChannel: Any?) {
        if (repo == null) return
        val token = splitAllRefreshToken.incrementAndGet()
        splitAllRefreshJob?.cancel()

        val presetSources = ArrayList<Any>()
        if (!isPresetDeleted(vm, "preset_india")) {
            getPresetSourceByName("getINDIA")?.let { presetSources.add(it) }
        }
        if (!isPresetDeleted(vm, "preset_global")) {
            getPresetSourceByName("getGLOBAL")?.let { presetSources.add(it) }
        }
        val customEntities = collectActiveCustomEntities(vm)

        // Only fetch playlists that have NOT loaded yet (never wipe already-loaded playlists).
        val missingPresets = presetSources.filter { src ->
            val u = (safeCall(src, "getUrl") as? String)?.trim().orEmpty()
            u.isNotEmpty() && !isUrlCachedInRepo(repo, u)
        }
        val missingCustoms = customEntities.filter { entity ->
            val u = (safeCall(entity, "getUrl") as? String)?.trim().orEmpty()
            u.isNotEmpty() && !isUrlCachedInRepo(repo, u)
        }

        if (missingPresets.isEmpty() && missingCustoms.isEmpty()) {
            // Everything is already loaded: refresh only the active channel's single playlist if known.
            val (activeSource, activeCustomId) = resolveChannelSourceInAll(
                vm, activeChannel, presetSources, customEntities
            )
            if (activeSource == null) {
                bumpMatchingRefreshTriggers(vm, "preset_all", "")
                return
            }
            val activeUrl = (safeCall(activeSource, "getUrl") as? String)?.trim().orEmpty()
            if (activeUrl.isEmpty()) {
                bumpMatchingRefreshTriggers(vm, "preset_all", "")
                return
            }
            setPlaylistLoadingState(vm, true)
            clearRepoCacheSafe(repo, activeUrl)
            splitAllRefreshJob = ioScope.launch {
                try {
                    val fresh = fetchPlaylistSourceNow(repo, activeSource)
                    if (activeCustomId != null && !fresh.isNullOrEmpty() && !isCustomIdRecentlyDeleted(activeCustomId)) {
                        try {
                            val repoCache = safeGetField(repo, "playlistCache") as? MutableMap<Any, Any>
                            repoCache?.put(activeUrl, fresh)
                        } catch (_: Throwable) {
                        }
                        customChannelsCache[activeCustomId] = fresh
                        customPlaylistUrls[activeCustomId] = activeUrl
                        notifiedCustomKeys.add("$activeCustomId|$activeUrl")
                        pushCustomChannelsToViewModel(vm)
                    }
                } catch (ce: CancellationException) {
                    throw ce
                } catch (t: Throwable) {
                    Log.w(TAG, "Active playlist refresh inside All failed", t)
                } finally {
                    if (splitAllRefreshToken.get() == token) {
                        bumpMatchingRefreshTriggers(vm, "preset_all", "")
                        setPlaylistLoadingState(vm, false)
                    }
                }
            }
            return
        }

        // Fetch ONLY the missing/unloaded playlists without clearing already-loaded ones.
        setPlaylistLoadingState(vm, true)
        splitAllRefreshJob = ioScope.launch {
            try {
                coroutineScope {
                    val tasks = ArrayList<kotlinx.coroutines.Deferred<Unit>>()
                    for (source in missingPresets) {
                        val u = (safeCall(source, "getUrl") as? String)?.trim().orEmpty()
                        clearRepoCacheSafe(repo, u)
                        tasks.add(async {
                            fetchPlaylistSourceNow(repo, source)
                            Unit
                        })
                    }
                    for (entity in missingCustoms) {
                        val id = (safeCall(entity, "getId") as? Number)?.toLong() ?: continue
                        val title = (safeCall(entity, "getTitle") as? String)?.takeIf { it.isNotBlank() } ?: "Custom Playlist"
                        val url = (safeCall(entity, "getUrl") as? String)?.trim().orEmpty()
                        if (url.isEmpty()) continue
                        val source = createPlaylistSource("custom_$id", title, url, false) ?: continue
                        clearRepoCacheSafe(repo, url)
                        tasks.add(async {
                            val list = fetchPlaylistSourceNow(repo, source)
                            if (!list.isNullOrEmpty() && !isCustomIdRecentlyDeleted(id)) {
                                try {
                                    val repoCache = safeGetField(repo, "playlistCache") as? MutableMap<Any, Any>
                                    repoCache?.put(url, list)
                                } catch (_: Throwable) {
                                }
                                customChannelsCache[id] = list
                                customPlaylistUrls[id] = url
                                notifiedCustomKeys.add("$id|$url")
                                pushCustomChannelsToViewModel(vm)
                            }
                        })
                    }
                    tasks.awaitAll()
                }
            } catch (ce: CancellationException) {
                throw ce
            } catch (t: Throwable) {
                Log.w(TAG, "refreshMissingOrActiveInAllPlaylists failed", t)
            } finally {
                if (splitAllRefreshToken.get() == token) {
                    bumpMatchingRefreshTriggers(vm, "preset_all", "")
                    setPlaylistLoadingState(vm, false)
                }
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun refreshCustomPlaylistFromNetwork(
        vm: Any,
        repo: Any?,
        customIdStr: String,
        title: String,
        url: String
    ) {
        cancelPendingAllPlaylistsRefresh()
        if (url.isEmpty()) return
        val customId = customIdStr.removePrefix("custom_").toLongOrNull()
        if (customId != null) {
            customPlaylistJobs.remove(customId)?.cancel()
            customChannelsCache.remove(customId)
            val nkIter = notifiedCustomKeys.iterator()
            while (nkIter.hasNext()) {
                if (nkIter.next().startsWith("${customId}|")) {
                    nkIter.remove()
                }
            }
            pushCustomChannelsToViewModel(vm)
        }

        clearRepoCacheSafe(repo, url)
        setPlaylistLoadingState(vm, true)
        bumpMatchingRefreshTriggers(vm, customIdStr, url)

        if (customId == null || repo == null) return

        val selUrl = (safeCall((safeGetField(vm, "_selectedPlaylist") as? StateFlow<*>)?.value, "getUrl") as? String)?.trim().orEmpty()
        val chSelUrl = (safeCall((safeGetField(vm, "_channelSelectedPlaylist") as? StateFlow<*>)?.value, "getUrl") as? String)?.trim().orEmpty()
        val isObservedByVmFlow = (selUrl == url || chSelUrl == url)

        if (isObservedByVmFlow) {
            syncCustomCacheAfterTriggerRefresh(vm, repo, customId, title.ifBlank { "Custom Playlist" }, url)
        } else {
            ensureCustomPlaylistLoaded(vm, customId, title.ifBlank { "Custom Playlist" }, url)
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun syncCustomCacheAfterTriggerRefresh(
        vm: Any,
        repo: Any,
        customId: Long,
        title: String,
        url: String
    ) {
        splitCustomSyncJob?.cancel()
        splitCustomSyncJob = ioScope.launch {
            try {
                for (attempt in 0 until 150) {
                    delay(200L)
                    if (isCustomIdRecentlyDeleted(customId)) return@launch
                    val repoCache = safeGetField(repo, "playlistCache") as? MutableMap<Any, Any>
                    val fresh = (repoCache?.get(url) as? List<*>)?.filterNotNull()
                    if (!fresh.isNullOrEmpty()) {
                        customChannelsCache[customId] = fresh
                        customPlaylistUrls[customId] = url
                        notifiedCustomKeys.add("$customId|$url")
                        pushCustomChannelsToViewModel(vm)
                        return@launch
                    }
                }
                ensureCustomPlaylistLoaded(vm, customId, title, url)
            } catch (ce: CancellationException) {
                throw ce
            } catch (t: Throwable) {
                Log.w(TAG, "syncCustomCacheAfterTriggerRefresh failed", t)
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun ensureCustomPlaylistLoaded(
        viewModel: Any?,
        id: Long,
        title: String,
        rawUrl: String
    ) {
        val url = rawUrl.trim()
        if (url.isEmpty() || isCustomIdRecentlyDeleted(id)) return
        val vm = viewModel ?: discoverViewModelIfNeeded()
        val repo = getRepositoryInstance(vm) ?: return

        try {
            val repoCache = safeGetField(repo, "playlistCache") as? MutableMap<Any, Any>
            val cachedList = (repoCache?.get(url) as? List<*>)?.filterNotNull()
                ?: (repoCache?.get(rawUrl) as? List<*>)?.filterNotNull()
            if (!cachedList.isNullOrEmpty()) {
                repoCache?.put(url, cachedList)
                customChannelsCache[id] = cachedList
                pushCustomChannelsToViewModel(vm)
                if (notifiedCustomKeys.add("$id|$url")) {
                    notifyViewModelCustomChannelsReady(vm, id, title, url)
                }
            }
        } catch (_: Throwable) {
        }

        val previousUrl = customPlaylistUrls[id]
        val existingJob = customPlaylistJobs[id]
        if (previousUrl == url && (customChannelsCache[id]?.isNotEmpty() == true || existingJob?.isActive == true)) {
            if (customChannelsCache[id]?.isNotEmpty() == true && notifiedCustomKeys.add("$id|$url")) {
                notifyViewModelCustomChannelsReady(vm, id, title, url)
            }
            return
        }

        customPlaylistUrls[id] = url
        existingJob?.cancel()

        val job = ioScope.launch {
            try {
                val source = createPlaylistSource("custom_$id", title, url, false) ?: return@launch
                val loadMethod = repo.javaClass.getMethod("loadChannels", source.javaClass)
                val flow = loadMethod.invoke(repo, source) as? Flow<*> ?: return@launch
                flow.collect { emitted ->
                    if (isCustomIdRecentlyDeleted(id) || customPlaylistUrls[id] != url) {
                        return@collect
                    }
                    val chList = (emitted as? List<*>)?.filterNotNull()
                    if (!chList.isNullOrEmpty()) {
                        try {
                            val repoCache = safeGetField(repo, "playlistCache") as? MutableMap<Any, Any>
                            repoCache?.put(url, chList)
                            if (rawUrl != url) {
                                repoCache?.put(rawUrl, chList)
                            }
                        } catch (_: Throwable) {
                        }
                        if (!isCustomIdRecentlyDeleted(id)) {
                            customChannelsCache[id] = chList
                            pushCustomChannelsToViewModel(vm)
                            if (notifiedCustomKeys.add("$id|$url")) {
                                notifyViewModelCustomChannelsReady(vm, id, title, url)
                            }
                        }
                    }
                }
            } catch (t: Throwable) {
                Log.w(TAG, "Failed loading custom playlist id=$id", t)
            }
        }
        customPlaylistJobs[id] = job
    }

    @Suppress("UNCHECKED_CAST")
    private fun notifyViewModelCustomChannelsReady(
        viewModel: Any?,
        id: Long,
        title: String,
        url: String
    ) {
        if (isCustomIdRecentlyDeleted(id)) return
        val vm = viewModel ?: discoverViewModelIfNeeded() ?: return
        val expectedId = "custom_$id"
        mainHandler.post {
            try {
                if (isCustomIdRecentlyDeleted(id)) return@post
                val knownEntities = latestCustomEntities
                val isStillKnown = knownEntities.any { (safeCall(it, "getId") as? Number)?.toLong() == id } ||
                    (recentlySelectedCustomId == id && System.currentTimeMillis() - recentlySelectedCustomAtMs < 3000L)
                if (knownEntities.isNotEmpty() && !isStillKnown) {
                    return@post
                }

                val selFlow = safeGetField(vm, "_selectedPlaylist") as? MutableStateFlow<Any?>
                val chSelFlow = safeGetField(vm, "_channelSelectedPlaylist") as? MutableStateFlow<Any?>
                val hasSelectedFlow = safeGetField(vm, "_hasSelectedFirstPlaylist") as? MutableStateFlow<Boolean>

                val curId = (safeCall(selFlow?.value, "getId") as? String).orEmpty()
                val chCurId = (safeCall(chSelFlow?.value, "getId") as? String).orEmpty()
                val curUrl = (safeCall(selFlow?.value, "getUrl") as? String)?.trim().orEmpty()

                val noPresetsLeft = isPresetDeleted(vm, "preset_india") && isPresetDeleted(vm, "preset_global")
                val cleanSource = createPlaylistSource(expectedId, title.ifBlank { "My Playlist" }, url, false)

                val shouldSelectHome = curId == expectedId || curUrl == url || (curId == "none" && noPresetsLeft && isStillKnown)
                if (shouldSelectHome) {
                    hasSelectedFlow?.value = true
                    if (cleanSource != null && curId != expectedId) {
                        selFlow?.value = cleanSource
                    }
                }
                if (chCurId == expectedId || (chCurId == "none" && noPresetsLeft && isStillKnown) || (shouldSelectHome && chCurId == curId)) {
                    if (cleanSource != null && chCurId != expectedId) {
                        chSelFlow?.value = cleanSource
                    }
                }

                val updatedCurId = (safeCall(selFlow?.value, "getId") as? String).orEmpty()
                val updatedChCurId = (safeCall(chSelFlow?.value, "getId") as? String).orEmpty()
                val homeChannels = (safeCall(vm, "getPlaylistChannels") as? StateFlow<*>)?.value as? Collection<*>
                val chChannels = (safeCall(vm, "getChannelScreenChannels") as? StateFlow<*>)?.value as? Collection<*>

                val now = System.currentTimeMillis()
                if (updatedCurId == expectedId && homeChannels.isNullOrEmpty()) {
                    (safeGetField(vm, "_refreshTrigger") as? MutableStateFlow<Long>)?.let {
                        it.value = maxOf(now, it.value + 1L)
                    }
                }
                if (updatedChCurId == expectedId && chChannels.isNullOrEmpty()) {
                    (safeGetField(vm, "_channelRefreshTrigger") as? MutableStateFlow<Long>)?.let {
                        it.value = maxOf(now + 1L, it.value + 1L)
                    }
                }
            } catch (t: Throwable) {
                Log.w(TAG, "notifyViewModelCustomChannelsReady failed", t)
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun syncCustomPlaylistsChannels(
        viewModel: Any?,
        entities: Collection<*>,
        reconcileSelection: Boolean = true
    ) {
        val repo = getRepositoryInstance(viewModel) ?: return
        val repoCache = safeGetField(repo, "playlistCache") as? MutableMap<*, *>
        val activeIds = HashSet<Long>()
        val nonNullEntities = ArrayList<Any>()
        val now = System.currentTimeMillis()

        for (item in entities) {
            if (item == null) continue
            val id = (safeCall(item, "getId") as? Number)?.toLong() ?: continue
            if (isCustomIdRecentlyDeleted(id, now)) continue
            nonNullEntities.add(item)
            val title = (safeCall(item, "getTitle") as? String)?.takeIf { it.isNotBlank() } ?: "Custom Playlist"
            val url = (safeCall(item, "getUrl") as? String)?.trim() ?: ""
            if (url.isEmpty()) continue

            activeIds.add(id)
            ensureCustomPlaylistLoaded(viewModel, id, title, url)
        }

        latestCustomEntities = nonNullEntities

        val recentId = if (
            recentlySelectedCustomId > 0L &&
            !isCustomIdRecentlyDeleted(recentlySelectedCustomId, now) &&
            now - recentlySelectedCustomAtMs < 3000L
        ) {
            recentlySelectedCustomId
        } else {
            -1L
        }

        var removedAny = false
        val iter = customChannelsCache.keys.iterator()
        while (iter.hasNext()) {
            val key = iter.next()
            if (!activeIds.contains(key) && key != recentId) {
                iter.remove()
                val removedUrl = customPlaylistUrls.remove(key)
                if (!removedUrl.isNullOrEmpty()) {
                    try {
                        repoCache?.remove(removedUrl)
                    } catch (_: Throwable) {
                    }
                }
                val nkIter = notifiedCustomKeys.iterator()
                while (nkIter.hasNext()) {
                    if (nkIter.next().startsWith("${key}|")) {
                        nkIter.remove()
                    }
                }
                customPlaylistJobs.remove(key)?.cancel()
                removedAny = true
            }
        }
        val urlIter = customPlaylistUrls.keys.iterator()
        while (urlIter.hasNext()) {
            val key = urlIter.next()
            if (!activeIds.contains(key) && key != recentId) {
                val removedUrl = customPlaylistUrls.remove(key)
                if (!removedUrl.isNullOrEmpty()) {
                    try {
                        repoCache?.remove(removedUrl)
                    } catch (_: Throwable) {
                    }
                }
                val nkIter = notifiedCustomKeys.iterator()
                while (nkIter.hasNext()) {
                    if (nkIter.next().startsWith("${key}|")) {
                        nkIter.remove()
                    }
                }
                customPlaylistJobs.remove(key)?.cancel()
                removedAny = true
            }
        }

        if (removedAny || customChannelsCache.isNotEmpty()) {
            pushCustomChannelsToViewModel(viewModel)
        }

        if (reconcileSelection) {
            reconcileActivePlaylistSelection(viewModel, nonNullEntities)
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun onCustomPlaylistDeleted(viewModel: Any, customId: Long) {
        try {
            ensureViewModelFlowsSubscribed(viewModel)
            val now = System.currentTimeMillis()
            recentlyDeletedCustomIds[customId] = now + 4500L
            if (recentlySelectedCustomId == customId) {
                recentlySelectedCustomId = -1L
                recentlySelectedCustomAtMs = 0L
                recentlySelectedCustomSource = null
            }

            customPlaylistJobs.remove(customId)?.cancel()
            customChannelsCache.remove(customId)
            var deletedUrl = customPlaylistUrls.remove(customId)
            val nkIter = notifiedCustomKeys.iterator()
            while (nkIter.hasNext()) {
                if (nkIter.next().startsWith("${customId}|")) {
                    nkIter.remove()
                }
            }

            val repo = getRepositoryInstance(viewModel)
            val repoCache = safeGetField(repo, "playlistCache") as? MutableMap<*, *>
            if (!deletedUrl.isNullOrEmpty()) {
                try {
                    repoCache?.remove(deletedUrl)
                    com.example.data.repository.ChannelDiskCache.removePlaylist(deletedUrl)
                } catch (_: Throwable) {
                }
            }

            val currentEntities: List<Any> = latestCustomEntities.ifEmpty {
                ((safeCall(viewModel, "getCustomPlaylists") as? StateFlow<*>)?.value as? Collection<*>)
                    ?.filterNotNull()
                    ?: emptyList()
            }
            val filtered = ArrayList<Any>(currentEntities.size)
            for (entity in currentEntities) {
                val eid = (safeCall(entity, "getId") as? Number)?.toLong()
                if (eid == customId) {
                    if (deletedUrl.isNullOrEmpty()) {
                        deletedUrl = (safeCall(entity, "getUrl") as? String)?.trim()
                        if (!deletedUrl.isNullOrEmpty()) {
                            try {
                                repoCache?.remove(deletedUrl)
                            } catch (_: Throwable) {
                            }
                        }
                    }
                    continue
                }
                if (eid != null && isCustomIdRecentlyDeleted(eid, now)) continue
                filtered.add(entity)
            }

            latestCustomEntities = filtered
            synchronized(stateLock) {
                cachedPlaylistCount = filtered.size
                optimisticPlaylistCount = filtered.size
            }
            pushCustomChannelsToViewModel(viewModel)
            reconcileActivePlaylistSelection(
                viewModel = viewModel,
                customEntitiesOverride = filtered,
                openAddPopupIfEmpty = true,
                forceDeletedId = "custom_$customId",
                forceDeletedUrl = deletedUrl
            )
        } catch (t: Throwable) {
            Log.w(TAG, "onCustomPlaylistDeleted failed", t)
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun onPresetPlaylistDeleted(viewModel: Any, presetId: String) {
        try {
            ensureViewModelFlowsSubscribed(viewModel)
            val repo = getRepositoryInstance(viewModel)
            val repoCache = safeGetField(repo, "playlistCache") as? MutableMap<*, *>
            val presetUrl = when (presetId) {
                "preset_india" -> "https://iptv-org.github.io/iptv/countries/in.m3u"
                "preset_global" -> "https://iptv-org.github.io/iptv/index.m3u"
                else -> null
            }
            if (!presetUrl.isNullOrEmpty()) {
                try {
                    repoCache?.remove(presetUrl)
                } catch (_: Throwable) {
                }
            }
            val currentEntities: List<Any> = latestCustomEntities.ifEmpty {
                ((safeCall(viewModel, "getCustomPlaylists") as? StateFlow<*>)?.value as? Collection<*>)
                    ?.filterNotNull()
                    ?: emptyList()
            }
            reconcileActivePlaylistSelection(
                viewModel = viewModel,
                customEntitiesOverride = currentEntities,
                openAddPopupIfEmpty = true,
                forceDeletedId = presetId,
                forceDeletedUrl = presetUrl
            )
        } catch (t: Throwable) {
            Log.w(TAG, "onPresetPlaylistDeleted failed", t)
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun reconcileActivePlaylistSelection(
        viewModel: Any?,
        customEntitiesOverride: List<Any>? = null,
        openAddPopupIfEmpty: Boolean = false,
        forceDeletedId: String? = null,
        forceDeletedUrl: String? = null
    ) {
        val vm = viewModel ?: discoverViewModelIfNeeded() ?: return
        val task = Runnable {
            if (isReconcilingSelection) return@Runnable
            isReconcilingSelection = true
            try {
                val now = System.currentTimeMillis()
                val ctx = resolveContext(vm)
                val repo = getRepositoryInstance(vm)
                val repoCache = safeGetField(repo, "playlistCache") as? MutableMap<*, *>

                val deletedSet = HashSet<String>()
                val deletedFlow = safeCall(vm, "getDeletedPresetIds") as? StateFlow<*>
                val flowSet = deletedFlow?.value as? Collection<*>
                if (flowSet != null) {
                    for (item in flowSet) {
                        val s = item?.toString()?.trim()
                        if (!s.isNullOrEmpty()) deletedSet.add(s)
                    }
                } else if (ctx != null) {
                    val prefs = ctx.getSharedPreferences("hypnotix_prefs", Context.MODE_PRIVATE)
                    val saved = prefs.getStringSet("deleted_preset_ids", emptySet())
                    if (saved != null) deletedSet.addAll(saved)
                }
                if (forceDeletedId != null && forceDeletedId.startsWith("preset_")) {
                    deletedSet.add(forceDeletedId)
                }

                // Purge deleted preset caches from PlaylistRepository.playlistCache
                if (deletedSet.contains("preset_india")) {
                    try {
                        repoCache?.remove("https://iptv-org.github.io/iptv/countries/in.m3u")
                        com.example.data.repository.ChannelDiskCache.removePlaylist("https://iptv-org.github.io/iptv/countries/in.m3u")
                    } catch (_: Throwable) {
                    }
                }
                if (deletedSet.contains("preset_global")) {
                    try {
                        repoCache?.remove("https://iptv-org.github.io/iptv/index.m3u")
                        com.example.data.repository.ChannelDiskCache.removePlaylist("https://iptv-org.github.io/iptv/index.m3u")
                    } catch (_: Throwable) {
                    }
                }

                val indiaAvailable = !deletedSet.contains("preset_india")
                val globalAvailable = !deletedSet.contains("preset_global")

                val rawEntities: List<Any> = customEntitiesOverride ?: latestCustomEntities.ifEmpty {
                    ((safeCall(vm, "getCustomPlaylists") as? StateFlow<*>)?.value as? Collection<*>)
                        ?.filterNotNull()
                        ?: emptyList()
                }
                val customSources = ArrayList<Any>()
                val seenIds = HashSet<String>()
                for (entity in rawEntities) {
                    val id = (safeCall(entity, "getId") as? Number)?.toLong() ?: continue
                    val srcId = "custom_$id"
                    if (isCustomIdRecentlyDeleted(id, now) || srcId == forceDeletedId) continue
                    val title = (safeCall(entity, "getTitle") as? String)?.trim()?.ifEmpty { "My Playlist" } ?: "My Playlist"
                    val url = (safeCall(entity, "getUrl") as? String)?.trim() ?: ""
                    if (url.isNotEmpty()) {
                        val src = createPlaylistSource(srcId, title, url, false)
                        if (src != null && seenIds.add(srcId)) {
                            customSources.add(src)
                        }
                    }
                }

                // Preserve newly added custom playlist within 3s window before Room Flow emits (unless deleted)
                val recentSrc = recentlySelectedCustomSource
                if (
                    recentSrc != null &&
                    recentlySelectedCustomId > 0L &&
                    !isCustomIdRecentlyDeleted(recentlySelectedCustomId, now) &&
                    (now - recentlySelectedCustomAtMs < 3000L)
                ) {
                    val rId = (safeCall(recentSrc, "getId") as? String).orEmpty()
                    if (rId.startsWith("custom_") && rId != forceDeletedId && seenIds.add(rId)) {
                        customSources.add(recentSrc)
                    }
                }

                val anyAvailable = indiaAvailable || globalAvailable || customSources.isNotEmpty()

                val hasSelectedFlow = safeGetField(vm, "_hasSelectedFirstPlaylist") as? MutableStateFlow<Boolean>
                val selFlow = safeGetField(vm, "_selectedPlaylist") as? MutableStateFlow<Any?>
                val chSelFlow = safeGetField(vm, "_channelSelectedPlaylist") as? MutableStateFlow<Any?>

                val curSource = selFlow?.value
                val chCurSource = chSelFlow?.value
                val curId = (safeCall(curSource, "getId") as? String)?.trim().orEmpty()
                val curUrl = (safeCall(curSource, "getUrl") as? String)?.trim().orEmpty()
                val chCurId = (safeCall(chCurSource, "getId") as? String)?.trim().orEmpty()
                val chCurUrl = (safeCall(chCurSource, "getUrl") as? String)?.trim().orEmpty()

                if (!anyAvailable) {
                    val homeChannels = (safeCall(vm, "getPlaylistChannels") as? StateFlow<*>)?.value as? Collection<*>
                    val chChannels = (safeCall(vm, "getChannelScreenChannels") as? StateFlow<*>)?.value as? Collection<*>
                    val wasActiveOrHasChannels =
                        (curId.isNotEmpty() && curId != "none") ||
                        (chCurId.isNotEmpty() && chCurId != "none") ||
                        (hasSelectedFlow?.value == true) ||
                        !homeChannels.isNullOrEmpty() ||
                        !chChannels.isNullOrEmpty()

                    // ALL playlists deleted -> Clear active playlist & channels and show "Add Playlist"
                    val noneSource = createPlaylistSource("none", "Add Playlist", "", false)
                    hasSelectedFlow?.value = false
                    if (noneSource != null) {
                        selFlow?.value = noneSource
                        chSelFlow?.value = noneSource
                    }
                    (safeGetField(vm, "_selectedCategory") as? MutableStateFlow<String?>)?.value = null
                    (safeGetField(vm, "_channelSelectedCategory") as? MutableStateFlow<String?>)?.value = null
                    (safeGetField(vm, "_selectedCountry") as? MutableStateFlow<String?>)?.value = null
                    (safeGetField(vm, "_selectedLanguage") as? MutableStateFlow<String?>)?.value = null
                    (safeGetField(vm, "_searchQuery") as? MutableStateFlow<String>)?.value = ""

                    customChannelsCache.clear()
                    customPlaylistUrls.clear()
                    notifiedCustomKeys.clear()
                    recentlySelectedCustomId = -1L
                    recentlySelectedCustomAtMs = 0L
                    recentlySelectedCustomSource = null
                    for (job in customPlaylistJobs.values) {
                        job.cancel()
                    }
                    customPlaylistJobs.clear()
                    pushCustomChannelsToViewModel(vm)

                    try {
                        repoCache?.clear()
                    } catch (_: Throwable) {
                    }

                    if (ctx != null) {
                        ctx.getSharedPreferences("hypnotix_prefs", Context.MODE_PRIVATE).edit()
                            .putBoolean("has_selected_first_playlist", false)
                            .putString("selected_playlist_id", "none")
                            .putString("selected_playlist_title", "Add Playlist")
                            .putString("selected_playlist_url", "")
                            .putBoolean("selected_playlist_is_preset", false)
                            .putString("channel_selected_playlist_id", "none")
                            .putString("channel_selected_playlist_title", "Add Playlist")
                            .putString("channel_selected_playlist_url", "")
                            .putBoolean("channel_selected_playlist_is_preset", false)
                            .apply()
                    }

                    if (wasActiveOrHasChannels) {
                        bumpRefreshTriggers(vm)
                    }

                    if (openAddPopupIfEmpty && now - lastAutoOpenAddPopupMs > 600L) {
                        lastAutoOpenAddPopupMs = now
                        openAddPlaylistUi(vm)
                    }
                    return@Runnable
                }

                fun isDeletedSource(sourceId: String, sourceUrl: String): Boolean {
                    if (forceDeletedId != null && sourceId == forceDeletedId) return true
                    if (!forceDeletedUrl.isNullOrEmpty() && sourceUrl.isNotEmpty() && sourceUrl == forceDeletedUrl) return true
                    if (sourceId == "preset_india" && !indiaAvailable) return true
                    if (sourceId == "preset_global" && !globalAvailable) return true
                    if (sourceId.startsWith("custom_")) {
                        val cid = sourceId.removePrefix("custom_").toLongOrNull()
                        if (cid != null && isCustomIdRecentlyDeleted(cid, now)) return true
                        if (!seenIds.contains(sourceId)) return true
                    }
                    return false
                }

                val hasSelected = hasSelectedFlow?.value == true
                val hasPresets = indiaAvailable || globalAvailable

                // Case 1: First launch or no playlist selected yet -> preserve unselected state when presets exist
                if (!hasSelected || curId.isEmpty() || curId == "none") {
                    if (hasPresets) {
                        if (isDeletedSource(curId, curUrl)) {
                            createPlaylistSource("none", "No Playlist", "", false)?.let { selFlow?.value = it }
                        }
                        if (isDeletedSource(chCurId, chCurUrl)) {
                            createPlaylistSource("none", "No Playlist", "", false)?.let { chSelFlow?.value = it }
                        }
                        return@Runnable
                    }
                    if (customSources.isNotEmpty()) {
                        val target = customSources.last()
                        hasSelectedFlow?.value = true
                        selFlow?.value = target
                        chSelFlow?.value = target
                        val selectMethod = vm.javaClass.getMethod("selectPlaylist", target.javaClass)
                        selectMethod.invoke(vm, target)
                        bumpRefreshTriggers(vm)
                    }
                    return@Runnable
                }

                // Case 2: Active playlist exists; auto-switch immediately if the active playlist itself was deleted
                val curDeleted = isDeletedSource(curId, curUrl)
                val chDeleted = chCurId.isNotEmpty() && chCurId != "none" && isDeletedSource(chCurId, chCurUrl)
                if (curDeleted || chDeleted) {
                    val replacement = customSources.lastOrNull()
                        ?: (if (indiaAvailable) getPresetSourceByName("getINDIA") else null)
                        ?: (if (globalAvailable) getPresetSourceByName("getGLOBAL") else null)
                        ?: getPresetSourceByName("getALL")

                    if (replacement != null) {
                        if (curDeleted) {
                            hasSelectedFlow?.value = true
                            selFlow?.value = replacement
                            val selectMethod = vm.javaClass.getMethod("selectPlaylist", replacement.javaClass)
                            selectMethod.invoke(vm, replacement)
                        } else {
                            chSelFlow?.value = replacement
                            val selectChMethod = vm.javaClass.getMethod("selectChannelPlaylist", replacement.javaClass)
                            selectChMethod.invoke(vm, replacement)
                        }
                        bumpRefreshTriggers(vm)
                    }
                }
            } catch (t: Throwable) {
                Log.w(TAG, "reconcileActivePlaylistSelection failed", t)
            } finally {
                isReconcilingSelection = false
            }
        }
        if (Looper.myLooper() == Looper.getMainLooper()) {
            task.run()
        } else {
            mainHandler.post(task)
        }
    }

    private val trimLock = Any()

    private fun ensureViewModelFlowsSubscribed(viewModel: Any) {
        lastKnownViewModelRef = WeakReference(viewModel)

        val playlistChannelsFlow = safeCall(viewModel, "getPlaylistChannels") as? StateFlow<*>
        val channelScreenChannelsFlow = safeCall(viewModel, "getChannelScreenChannels") as? StateFlow<*>
        if (playlistChannelsFlow != null && channelScreenChannelsFlow != null) {
            val shouldSubscribeDerived = synchronized(observedDerivedFlowViewModels) {
                observedDerivedFlowViewModels.add(viewModel)
            }
            if (shouldSubscribeDerived) {
                hookScope.launch {
                    playlistChannelsFlow.collect { }
                }
                hookScope.launch {
                    channelScreenChannelsFlow.collect { }
                }
            }
        }

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

        // Keep customPlaylists and favorites StateFlows hot
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
                var seenInitial = false
                var prevHadAny = true
                deletedPresetsFlow.collect {
                    val anyNow = hasAnyAvailablePlaylist(viewModel)
                    val openIfEmpty = seenInitial && prevHadAny && !anyNow
                    seenInitial = true
                    prevHadAny = anyNow
                    reconcileActivePlaylistSelection(viewModel, openAddPopupIfEmpty = openIfEmpty)
                    val ctx = resolveContext(viewModel)
                    if (!PremiumManager.isPremium(ctx)) {
                        enforceFreeTierLimitsIfNeeded(ctx, viewModel)
                    }
                }
            }
        }

        reconcileActivePlaylistSelection(viewModel)
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
                    roomDb.openHelper.readableDatabase
                } catch (_: Throwable) {
                    if (Looper.myLooper() == Looper.getMainLooper()) null else roomDb.openHelper.writableDatabase
                }
                if (supportDb != null) {
                    supportDb.query("SELECT COUNT(*) FROM custom_playlists").use { cursor ->
                        if (cursor.moveToFirst()) {
                            return cursor.getInt(0)
                        }
                    }
                }
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Room queryDbPlaylistCount fallback triggered", t)
        }

        if (context == null || Looper.myLooper() == Looper.getMainLooper()) return -1

        return try {
            val dbFile = context.getDatabasePath("hypnotix_iptv_db")
            if (dbFile != null && dbFile.exists()) {
                SQLiteDatabase.openDatabase(dbFile.absolutePath, null, SQLiteDatabase.OPEN_READONLY).use { sqlite ->
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

    internal fun isAppInDarkMode(context: Context, viewModel: Any? = null): Boolean {
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
        val sepIdx = maxOf(s.indexOf(':'), s.indexOf('='))
        if (sepIdx >= 0) {
            s = s.substring(sepIdx + 1).trim().trim('"', '\'')
        }
        if ((s.startsWith("v", ignoreCase = true)) && s.length > 1 && s[1].isDigit()) {
            s = s.substring(1).trim()
        }
        return s
    }

    private fun getCurrentAppBuildNumber(context: Context): Long {
        return try {
            val pkgInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            val pkgCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                pkgInfo.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                pkgInfo.versionCode.toLong()
            }
            if (pkgCode > 0L) pkgCode else maxOf(com.example.BuildConfig.VERSION_CODE.toLong(), 6L)
        } catch (_: Throwable) {
            maxOf(com.example.BuildConfig.VERSION_CODE.toLong(), 6L)
        }
    }

    private fun getCurrentAppVersionName(context: Context): String {
        return try {
            val pkgInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            pkgInfo.versionName ?: "1.1.0"
        } catch (_: Throwable) {
            "1.1.0"
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

    internal fun isForceUpdateActive(): Boolean {
        return isForceUpdateRequired || (activeForceUpdateDialogRef?.get()?.isShowing == true)
    }

    internal fun openPlayStoreForUpdate(context: Context) {
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

    private fun applyRemoteVersionString(ctx: Context, rawValue: String, contextFallback: Context? = null) {
        if (rawValue.isEmpty()) {
            isForceUpdateRequired = false
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
            return
        }

        val currentBuild = getCurrentAppBuildNumber(ctx)
        val parsedBuild = rawValue.toLongOrNull()
            ?: if (rawValue.contains('.') && rawValue.substringAfter('.').all { it == '0' }) {
                rawValue.substringBefore('.').toLongOrNull()
            } else {
                null
            }

        // Trigger force update ONLY when the installed app build/version is strictly lower than
        // Firebase Remote Config Version (e.g. Version=4 -> <4 blocked, 4/5/6+ allowed).
        val outdated = if (parsedBuild != null) {
            parsedBuild > 0L && currentBuild < parsedBuild
        } else if (rawValue.contains('.')) {
            val currentVerName = getCurrentAppVersionName(ctx).trim()
            isSemanticVersionOlder(currentVerName, rawValue)
        } else {
            false
        }

        Log.i(
            TAG,
            "Version check: currentBuild=$currentBuild, currentVer=${getCurrentAppVersionName(ctx)}, remoteRaw='$rawValue', parsedBuild=$parsedBuild, outdated=$outdated"
        )

        isForceUpdateRequired = outdated
        if (outdated) {
            val targetCtx = contextFallback ?: ctx
            showForceUpdatePopup(targetCtx)
            mainHandler.postDelayed({
                if (isForceUpdateRequired) {
                    showForceUpdatePopup(findActiveActivity() ?: targetCtx)
                }
            }, 600L)
            mainHandler.postDelayed({
                if (isForceUpdateRequired) {
                    showForceUpdatePopup(findActiveActivity() ?: targetCtx)
                }
            }, 1600L)
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
    }

    private fun evaluateRemoteMinimumVersion(
        contextFallback: Context? = null,
        fromSdkServerFetch: Boolean = false
    ) {
        try {
            val ctx = resolveContext() ?: contextFallback?.applicationContext ?: contextFallback ?: return
            val prefs = ctx.getSharedPreferences("hypnotix_prefs", Context.MODE_PRIVATE)
            var rawValue = ""

            if (hasLiveRestVersionInSession && !fromSdkServerFetch) {
                rawValue = lastLiveRestVersion
                applyRemoteVersionString(ctx, rawValue, contextFallback)
                return
            }

            if (fromSdkServerFetch) {
                try {
                    val frc = FirebaseRemoteConfig.getInstance()
                    val candidateKeys = listOf("Version", "version", "Minimum_version", "Min_version", "min_version")
                    for (key in candidateKeys) {
                        val v = cleanRemoteConfigValue(frc.getString(key))
                        if (v.isNotEmpty()) {
                            rawValue = v
                            break
                        }
                    }
                    NotificationSyncBridge.onRemoteConfigSdkReceived(ctx, frc)
                } catch (_: Throwable) {
                }

                if (rawValue.isNotEmpty()) {
                    prefs.edit().putString("cached_remote_min_version", rawValue).apply()
                    lastLiveRestVersion = rawValue
                    hasLiveRestVersionInSession = true
                } else {
                    prefs.edit().remove("cached_remote_min_version").apply()
                    lastLiveRestVersion = ""
                    hasLiveRestVersionInSession = true
                }
            } else {
                rawValue = prefs.getString("cached_remote_min_version", "") ?: ""
            }

            applyRemoteVersionString(ctx, rawValue, contextFallback)
        } catch (t: Throwable) {
            Log.w(TAG, "evaluateRemoteMinimumVersion failed", t)
        }
    }

    private fun fetchRemoteConfigDirectlyViaRest(contextFallback: Context? = null) {
        val ctx = resolveContext() ?: contextFallback?.applicationContext ?: contextFallback ?: return
        if (!remoteRestFetchInFlight.compareAndSet(false, true)) return

        ioScope.launch {
            try {
                val prefs = ctx.getSharedPreferences("hypnotix_prefs", Context.MODE_PRIVATE)
                var appInstanceId = prefs.getString("rc_app_instance_id", null)
                if (appInstanceId.isNullOrBlank()) {
                    appInstanceId = UUID.randomUUID().toString().replace("-", "")
                    prefs.edit().putString("rc_app_instance_id", appInstanceId).apply()
                }

                val endpoint = "https://firebaseremoteconfig.googleapis.com/v1/projects/80080000257/namespaces/firebase:fetch?key=AIzaSyDGNKZ3KG5ANDVTolipmoSpNqXxH5RBM1M"
                val conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 8000
                    readTimeout = 8000
                    useCaches = false
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                    setRequestProperty("Accept", "application/json")
                    setRequestProperty("Cache-Control", "no-cache")
                    setRequestProperty("Pragma", "no-cache")
                }

                val payload = JSONObject().apply {
                    put("appId", "1:80080000257:android:4adeafddb941d467ba7700")
                    put("appInstanceId", appInstanceId)
                    put("packageName", ctx.packageName.ifBlank { "com.hypnotix.iptv" })
                }.toString()

                conn.outputStream.use { os ->
                    os.write(payload.toByteArray(Charsets.UTF_8))
                }

                val code = conn.responseCode
                if (code in 200..299) {
                    val body = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                    val root = JSONObject(body)
                    val entries = root.optJSONObject("entries")
                    if (entries != null) {
                        val versionKeys = listOf("Version", "version", "Minimum_version", "Min_version", "min_version")
                        var remoteVersionVal = ""
                        for (k in versionKeys) {
                            if (entries.has(k)) {
                                val cleaned = cleanRemoteConfigValue(entries.optString(k, ""))
                                if (cleaned.isNotEmpty()) {
                                    remoteVersionVal = cleaned
                                    break
                                }
                            }
                        }

                        lastLiveRestVersion = remoteVersionVal
                        hasLiveRestVersionInSession = true
                        if (remoteVersionVal.isNotEmpty()) {
                            prefs.edit().putString("cached_remote_min_version", remoteVersionVal).apply()
                        } else {
                            prefs.edit().remove("cached_remote_min_version").apply()
                        }

                        applyRemoteVersionString(ctx, remoteVersionVal, findActiveActivity() ?: contextFallback)

                        // Also sync Show_ads and Ad Unit IDs from live Remote Config entries
                        try {
                            val showAdsRaw = cleanRemoteConfigValue(
                                entries.optString("Show_ads", entries.optString("show_ads", "true"))
                            ).lowercase(Locale.US)
                            val showAds = showAdsRaw in listOf("true", "1", "yes", "on")
                            val pubIdRaw = cleanRemoteConfigValue(
                                entries.optString("Pub_id", entries.optString("pub_id", ""))
                            ).ifEmpty { "ca-app-pub-3940256099942544~3347511713" }
                            val appOpenRaw = cleanRemoteConfigValue(
                                entries.optString("App_open", entries.optString("app_open", ""))
                            ).ifEmpty { "ca-app-pub-3940256099942544/9257395921" }
                            val interRaw = cleanRemoteConfigValue(
                                entries.optString("Interstitial", entries.optString("interstitial", ""))
                            ).ifEmpty { "ca-app-pub-3940256099942544/1033173712" }
                            val nativeRaw = cleanRemoteConfigValue(
                                entries.optString("Native", entries.optString("native", ""))
                            ).ifEmpty { "ca-app-pub-3940256099942544/2247696110" }

                            val configSignature = "$showAds|$pubIdRaw|$appOpenRaw|$interRaw|$nativeRaw"
                            if (configSignature != lastAppliedAdConfigSignature) {
                                lastAppliedAdConfigSignature = configSignature
                                val rcClass = Class.forName("com.example.fcm.HypnotixRemoteAdConfig")
                                val applyMethod = rcClass.getMethod(
                                    "applyToManagers",
                                    Context::class.java,
                                    Boolean::class.javaPrimitiveType,
                                    String::class.java,
                                    String::class.java,
                                    String::class.java,
                                    String::class.java
                                )
                                applyMethod.invoke(null, ctx, showAds, pubIdRaw, appOpenRaw, interRaw, nativeRaw)
                            }
                        } catch (t: Throwable) {
                            Log.w(TAG, "Failed applying ad config from direct REST fetch", t)
                        }
                        NotificationSyncBridge.onRemoteConfigJsonReceived(ctx, entries)
                    }
                }
                conn.disconnect()
            } catch (t: Throwable) {
                Log.w(TAG, "fetchRemoteConfigDirectlyViaRest error", t)
            } finally {
                remoteRestFetchInFlight.set(false)
            }
        }
    }

    private fun ensurePeriodicRemoteConfigCheck(context: Context?) {
        if (periodicRemoteCheckStarted) return
        synchronized(this) {
            if (periodicRemoteCheckStarted) return
            periodicRemoteCheckStarted = true
            ioScope.launch {
                while (true) {
                    delay(10000L)
                    try {
                        val act = findActiveActivity()
                        if (act != null && !act.isFinishing && !act.isDestroyed) {
                            fetchRemoteConfigDirectlyViaRest(act)
                            NotificationSyncBridge.syncRemoteSourcesNow(act)
                        }
                    } catch (_: Throwable) {
                    }
                }
            }
        }
    }

    @JvmStatic
    fun checkRemoteMinimumVersion(context: Context? = null) {
        try {
            updateContextAndActivity(context)
            evaluateRemoteMinimumVersion(context)
            fetchRemoteConfigDirectlyViaRest(context)
            ensurePeriodicRemoteConfigCheck(context)

            try {
                val frc = FirebaseRemoteConfig.getInstance()
                val settings = FirebaseRemoteConfigSettings.Builder()
                    .setMinimumFetchIntervalInSeconds(0L)
                    .build()
                frc.setConfigSettingsAsync(settings).addOnCompleteListener {
                    frc.fetch(0L).addOnCompleteListener { fetchTask ->
                        if (fetchTask.isSuccessful) {
                            frc.activate().addOnCompleteListener {
                                evaluateRemoteMinimumVersion(
                                    contextFallback = findActiveActivity() ?: context,
                                    fromSdkServerFetch = true
                                )
                            }
                        }
                    }
                }

                if (!remoteVersionListenerRegistered) {
                    synchronized(this) {
                        if (!remoteVersionListenerRegistered) {
                            remoteVersionListenerRegistered = true
                            frc.addOnConfigUpdateListener(object : ConfigUpdateListener {
                                override fun onUpdate(configUpdate: ConfigUpdate) {
                                    fetchRemoteConfigDirectlyViaRest(findActiveActivity() ?: context)
                                    frc.activate().addOnCompleteListener {
                                        evaluateRemoteMinimumVersion(
                                            contextFallback = findActiveActivity() ?: context,
                                            fromSdkServerFetch = true
                                        )
                                    }
                                }

                                override fun onError(error: FirebaseRemoteConfigException) {
                                    Log.w(TAG, "Remote config update listener error", error)
                                }
                            })
                        }
                    }
                }
            } catch (_: Throwable) {
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
                outList.clear()
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

    private fun hasAnyAvailablePlaylist(viewModel: Any? = null): Boolean {
        val vm = viewModel ?: discoverViewModelIfNeeded()
        if (!isPresetDeleted(vm, "preset_india") || !isPresetDeleted(vm, "preset_global")) {
            return true
        }
        if (latestCustomEntities.isNotEmpty() || customChannelsCache.isNotEmpty()) {
            return true
        }
        if (vm != null) {
            val customFlow = safeCall(vm, "getCustomPlaylists") as? StateFlow<*>
            val list = customFlow?.value as? Collection<*>
            if (!list.isNullOrEmpty()) return true
        }
        val ctx = resolveContext(vm)
        return queryDbPlaylistCount(ctx, vm) > 0
    }

    private fun openAddPlaylistUi(viewModel: Any? = null) {
        val runnable = Runnable {
            var opened = false
            try {
                val target = mainScreenScaffoldTargetRef?.get()
                if (target != null) {
                    @Suppress("UNCHECKED_CAST")
                    val tabState = safeGetField(target, "\$addPlaylistInitialTab\$delegate") as? MutableState<Int>
                    tabState?.value = 0
                    @Suppress("UNCHECKED_CAST")
                    val bottomSheetState = safeGetField(target, "\$showAddPlaylistBottomSheet\$delegate") as? MutableState<Boolean>
                    if (bottomSheetState != null) {
                        bottomSheetState.value = true
                        opened = true
                    } else {
                        @Suppress("UNCHECKED_CAST")
                        val screenState = safeGetField(target, "\$showAddPlaylistScreen\$delegate") as? MutableState<Boolean>
                        if (screenState != null) {
                            screenState.value = true
                            opened = true
                        }
                    }
                }
            } catch (t: Throwable) {
                Log.w(TAG, "openAddPlaylistUi via scaffold target failed", t)
            }
            if (!opened) {
                val vm = viewModel ?: discoverViewModelIfNeeded()
                if (vm != null) {
                    try {
                        val openMethod = vm.javaClass.getMethod("openAddPlaylist", Int::class.javaPrimitiveType)
                        openMethod.invoke(vm, 0)
                    } catch (_: Throwable) {
                    }
                }
            }
        }
        if (Looper.myLooper() == Looper.getMainLooper()) {
            runnable.run()
        } else {
            mainHandler.post(runnable)
        }
    }

    @JvmStatic
    fun isPresetDeleted(viewModel: Any?, presetId: String): Boolean {
        if (viewModel != null) {
            val clsName = viewModel.javaClass.name
            if (clsName == "com.example.data.model.Channel") {
                XtreamCoverBridge.ensureXtreamChannelCoverUrl(viewModel)
                return false
            }
            if (presetId == "\$this\$Card" && clsName.endsWith("HomeChannelCard\$1")) {
                HomeChannelCardHook.setPendingCardTarget(viewModel)
                return false
            }
            if (presetId == "\$this\$Card" && clsName.endsWith("MiniHighlightCard\$1")) {
                HomeChannelCardHook.setPendingMiniHighlightTarget(viewModel)
                return false
            }
            if (presetId == "split_refresh_playlist_button" &&
                (clsName.endsWith("HypnotixSplitScreen\$7\$2\$2") || clsName.endsWith("HypnotixSplitScreen\$7\$3\$2\$1\$2"))
            ) {
                SplitPortraitHooks.setPendingActionPanelTarget(viewModel)
                return false
            }
            if (presetId == "channel_filter_input" && clsName.endsWith("HypnotixSplitScreen\$7")) {
                SplitPortraitHooks.setPendingSearchPanelTarget(viewModel)
                return false
            }
            if (presetId == "top_bar_playlist_pill") {
                pendingBannerDropdownCustomList = viewModel as? List<*>
                return false
            }
            if (presetId == "SWITCH PLAYLIST") {
                @Suppress("UNCHECKED_CAST")
                pendingBannerDropdownMenuState = viewModel as? MutableState<Boolean>
                return false
            }
            if (presetId == "\$this\$DropdownMenu" && clsName.endsWith("IptvViewModel")) {
                pendingBannerDropdownVm = viewModel
                return false
            }
            if (presetId == "\$this\$DropdownMenu" && clsName.endsWith("MainAppScreen\$25\$1\$1\$1\$6\$4")) {
                pendingHomeDropdownTarget = viewModel
                return false
            }
            if (presetId == "dark_mode_switch" && clsName.endsWith("SettingsScreen\$9\$2\$1\$2\$1\$2")) {
                pendingAppearanceTarget = viewModel
                return false
            }
            if (presetId == "empty_preset_playlists" && clsName.endsWith("MainAppScreen\$25\$3")) {
                mainScreenScaffoldTargetRef = WeakReference(viewModel)
                return false
            }
            if (presetId == "start_watching_live_tv_home") {
                val vm = discoverViewModelIfNeeded()
                if (!hasAnyAvailablePlaylist(vm)) {
                    openAddPlaylistUi(vm)
                    return true
                }
                try {
                    @Suppress("UNCHECKED_CAST")
                    val navState = viewModel as? MutableState<Any?>
                    if (navState != null) {
                        val navCls = Class.forName("com.example.ui.components.NavigationItem")
                        val playlistsConst = navCls.enumConstants?.firstOrNull {
                            (it as? Enum<*>)?.name == "PLAYLISTS"
                        }
                        if (playlistsConst != null) {
                            navState.value = playlistsConst
                        }
                    }
                } catch (t: Throwable) {
                    Log.w(TAG, "Failed navigating to PLAYLISTS from hook", t)
                }
                return false
            }
        }
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
        if (viewModel != null && viewModel.javaClass.name == "com.example.data.model.Channel") {
            return XtreamCoverBridge.resolveCoverForChannelLogo(viewModel, restored as? String)
        }
        if (restored == null) return null
        try {
            val id = (safeCall(restored, "getId") as? String)?.trim() ?: ""
            if (id.isEmpty() || id == "none") return restored

            val vm = viewModel ?: discoverViewModelIfNeeded()
            val indiaDeleted = isPresetDeleted(vm, "preset_india")
            val globalDeleted = isPresetDeleted(vm, "preset_global")
            val ctx = resolveContext(vm)

            if (indiaDeleted && globalDeleted && id.startsWith("preset_")) {
                val hasCustom = latestCustomEntities.isNotEmpty() || queryDbPlaylistCount(ctx, vm) > 0
                if (!hasCustom) {
                    return createPlaylistSource("none", "Add Playlist", "", false) ?: restored
                }
            }

            // If restored playlist is a deleted preset, fallback to available preset or All Playlists
            if (isPresetDeleted(vm, id)) {
                if (!indiaDeleted) {
                    return getPresetSourceByName("getINDIA") ?: restored
                }
                if (!globalDeleted) {
                    return getPresetSourceByName("getGLOBAL") ?: restored
                }
                if (latestCustomEntities.isEmpty() && queryDbPlaylistCount(ctx, vm) <= 0) {
                    return createPlaylistSource("none", "Add Playlist", "", false) ?: restored
                }
                val allSource = getPresetSourceByName("getALL")
                if (allSource != null) {
                    return allSource
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

            val sourceId = (safeCall(source, "getId") as? String)?.trim()
            val rawTitle = (safeCall(source, "getTitle") as? String) ?: ""
            val rawUrl = (safeCall(source, "getUrl") as? String) ?: ""
            val sourceTitle = rawTitle.trim().ifEmpty { "My Playlist" }
            val sourceUrl = rawUrl.trim()
            val isPreset = (safeCall(source, "isPreset") as? Boolean) ?: false

            // Synchronously mirror active selection BEFORE any sync/load callbacks run,
            // preventing re-entrant reconcile loops when selectPlaylist() triggers hooks
            if (!sourceId.isNullOrEmpty() && sourceId != "none") {
                (safeGetField(viewModel, "_hasSelectedFirstPlaylist") as? MutableStateFlow<Boolean>)?.value = true
                (safeGetField(viewModel, "_selectedPlaylist") as? MutableStateFlow<Any?>)?.value = source
                (safeGetField(viewModel, "_channelSelectedPlaylist") as? MutableStateFlow<Any?>)?.value = source
            }

            // Save persistent selection in hypnotix_prefs
            val ctx = resolveContext(viewModel)
            if (ctx != null && !sourceId.isNullOrEmpty() && sourceId != "none") {
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

            if (sourceId?.startsWith("custom_") == true && sourceUrl.isNotEmpty()) {
                val customId = sourceId.removePrefix("custom_").toLongOrNull()
                val cleanSource = createPlaylistSource(sourceId, sourceTitle, sourceUrl, false) ?: source
                if (customId != null) {
                    recentlyDeletedCustomIds.remove(customId)
                    recentlySelectedCustomId = customId
                    recentlySelectedCustomAtMs = System.currentTimeMillis()
                    recentlySelectedCustomSource = cleanSource
                    ensureCustomPlaylistLoaded(viewModel, customId, sourceTitle, rawUrl)
                }
            }

            // Sync custom playlists channels if ALL or custom playlist is selected
            if (sourceId == "preset_all" || sourceId?.startsWith("custom_") == true) {
                if (latestCustomEntities.isNotEmpty()) {
                    syncCustomPlaylistsChannels(viewModel, latestCustomEntities, reconcileSelection = false)
                }
                pushCustomChannelsToViewModel(viewModel)
            }
        } catch (t: Throwable) {
            Log.w(TAG, "onPlaylistSelectedHook failed", t)
        }
    }

    private fun isMainActivity(act: Activity?): Boolean {
        return act != null && act.javaClass.name == "com.example.MainActivity"
    }

    private fun resolveIptvViewModelForActivity(act: Activity): Any? {
        return try {
            if (!isMainActivity(act) || act !is ViewModelStoreOwner) {
                return lastKnownViewModelRef?.get()
            }
            val vmClass = cachedIptvViewModelClass ?: Class.forName("com.example.ui.viewmodel.IptvViewModel").also {
                cachedIptvViewModelClass = it
            }
            @Suppress("UNCHECKED_CAST")
            ViewModelProvider(act)[vmClass as Class<androidx.lifecycle.ViewModel>]
        } catch (_: Throwable) {
            lastKnownViewModelRef?.get()
        }
    }

    private fun isFullScreenOrOpenAdActive(): Boolean {
        return try {
            val inStreamCls = Class.forName("com.example.ad.InStreamAdManager")
            val inStreamInst = inStreamCls.getField("INSTANCE").get(null)
            val inStreamActive = (inStreamCls.getMethod("isFullScreenAdActive").invoke(inStreamInst) as? Boolean) == true
            if (inStreamActive) return true
            val adMgrCls = Class.forName("com.example.ad.AdManager")
            val adMgrInst = adMgrCls.getField("INSTANCE").get(null)
            (adMgrCls.getMethod("isAppOpenAdPendingOrShowing").invoke(adMgrInst) as? Boolean) == true
        } catch (_: Throwable) {
            false
        }
    }

    private fun getWasPlayingBeforeStop(act: Activity): Boolean {
        return try {
            val field = cachedWasPlayingField ?: act.javaClass.getDeclaredField("wasPlayingBeforeStop").apply {
                isAccessible = true
                cachedWasPlayingField = this
            }
            field.getBoolean(act)
        } catch (_: Throwable) {
            false
        }
    }

    private fun setWasPlayingBeforeStop(act: Activity, value: Boolean) {
        try {
            val field = cachedWasPlayingField ?: act.javaClass.getDeclaredField("wasPlayingBeforeStop").apply {
                isAccessible = true
                cachedWasPlayingField = this
            }
            field.setBoolean(act, value)
        } catch (_: Throwable) {}
    }

    private fun resetStuckPipState(act: Activity) {
        try {
            val field = cachedIsPipModeStateField ?: act.javaClass.getDeclaredField("isPipModeState").apply {
                isAccessible = true
                cachedIsPipModeStateField = this
            }
            @Suppress("UNCHECKED_CAST")
            val state = field.get(act) as? MutableState<Boolean>
            if (state != null && state.value) {
                state.value = false
            }
        } catch (_: Throwable) {}
    }

    private fun invokeUpdatePipParams(act: Activity, canEnterPip: Boolean) {
        try {
            val method = cachedUpdatePipParamsMethod ?: act.javaClass.getMethod("updatePipParams", Boolean::class.javaPrimitiveType).also {
                cachedUpdatePipParamsMethod = it
            }
            method.invoke(act, canEnterPip)
        } catch (_: Throwable) {}
    }

    private fun computePipEligibility(act: Activity, uiState: Any?): Boolean {
        if (uiState == null) return false
        val isSplit = (safeCall(uiState, "isSplitViewMode") as? Boolean) == true
        val activeCh = safeCall(uiState, "getActiveChannel")
        return isSplit && activeCh != null && isStreamActiveOrBuffering(act)
    }

    private fun attachPlayWhenReadyListener(act: Activity, exo: ExoPlayer?) {
        if (exo == null) return
        synchronized(attachedPlayWhenReadyPlayers) {
            if (attachedPlayWhenReadyPlayers.contains(exo)) return
            attachedPlayWhenReadyPlayers.add(exo)
        }
        val actRef = WeakReference(act)
        exo.addListener(object : Player.Listener {
            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                val mainAct = actRef.get() ?: return
                if (mainAct.isFinishing || mainAct.isDestroyed) return
                mainHandler.post {
                    try {
                        if (!mainAct.isFinishing && !mainAct.isDestroyed && !mainAct.isInPictureInPictureMode) {
                            val vm = resolveIptvViewModelForActivity(mainAct)
                            val uiState = (safeCall(vm, "getUiState") as? StateFlow<*>)?.value
                            invokeUpdatePipParams(mainAct, computePipEligibility(mainAct, uiState))
                        }
                    } catch (_: Throwable) {}
                }
            }
        })
    }

    private fun startOrSyncPipObserver(act: Activity) {
        if (!isMainActivity(act)) return
        pipObserverJob?.cancel()
        pipObserverJob = hookScope.launch {
            try {
                val vm = resolveIptvViewModelForActivity(act) ?: return@launch
                val pm = safeCall(vm, "getPlayerManager") ?: return@launch
                val uiStateFlow = safeCall(vm, "getUiState") as? StateFlow<*> ?: return@launch
                val isPlayingFlow = safeCall(pm, "isPlaying") as? StateFlow<*> ?: return@launch
                val playerStateFlow = safeCall(pm, "getPlayerState") as? StateFlow<*> ?: return@launch
                val exo = safeCall(pm, "getExoPlayer") as? ExoPlayer
                attachPlayWhenReadyListener(act, exo)

                combine(uiStateFlow, isPlayingFlow, playerStateFlow) { uiState, _, _ ->
                    computePipEligibility(act, uiState)
                }.collect { eligible ->
                    mainHandler.post {
                        try {
                            if (!act.isFinishing && !act.isDestroyed && !act.isInPictureInPictureMode) {
                                val latestPm = safeCall(vm, "getPlayerManager")
                                attachPlayWhenReadyListener(act, safeCall(latestPm, "getExoPlayer") as? ExoPlayer)
                                invokeUpdatePipParams(act, eligible)
                            }
                        } catch (_: Throwable) {}
                    }
                }
            } catch (_: Throwable) {}
        }
    }

    private fun onMainActivityStartedHook(act: Activity) {
        if (!isMainActivity(act)) return
        try {
            if (!act.isInPictureInPictureMode) {
                resetStuckPipState(act)
            }
            val vm = resolveIptvViewModelForActivity(act)
            val pm = if (vm != null) safeCall(vm, "getPlayerManager") else null
            val exo = if (pm != null) safeCall(pm, "getExoPlayer") as? ExoPlayer else null
            val currentCh = if (pm != null) safeGetField(pm, "currentChannel") else null

            if (pausedWhileBufferingByStop) {
                pausedWhileBufferingByStop = false
                if (!isFullScreenOrOpenAdActive() && pm != null && exo != null && currentCh != null) {
                    if (exo.playbackState == Player.STATE_IDLE) {
                        safeCall(pm, "retry")
                    }
                }
            }
            if (pm != null && exo != null && currentCh != null) {
                val prog = safeGetField(pm, "progressUpdateRunnable") as? Runnable
                val h = safeGetField(pm, "handler") as? Handler
                if (prog != null && h != null) {
                    h.removeCallbacks(prog)
                    h.post(prog)
                }
            }
        } catch (_: Throwable) {}
        startOrSyncPipObserver(act)
    }

    private fun onMainActivityStoppedHook(act: Activity) {
        pipObserverJob?.cancel()
        pipObserverJob = null
        if (act.isChangingConfigurations || act.isInPictureInPictureMode) return
        try {
            resetStuckPipState(act)
            val vm = resolveIptvViewModelForActivity(act) ?: return
            val pm = safeCall(vm, "getPlayerManager") ?: return
            val exo = safeCall(pm, "getExoPlayer") as? ExoPlayer
            val currentCh = safeGetField(pm, "currentChannel")
            val isPlayingNow = ((safeCall(pm, "isPlaying") as? StateFlow<*>)?.value as? Boolean) == true
            val stateName = ((safeCall(pm, "getPlayerState") as? StateFlow<*>)?.value)?.javaClass?.simpleName ?: ""
            val markedWasPlaying = getWasPlayingBeforeStop(act)

            val hadActiveIntent = isPlayingNow || (
                exo?.playWhenReady == true &&
                currentCh != null &&
                stateName != "Error" &&
                stateName != "Ended" &&
                stateName != "Idle"
            )

            // Cancel any pending error-retry or slow-network step-down callbacks so stream cannot start in background
            try {
                safeCall(pm, "cancelBufferingWatchdogs")
            } catch (_: Throwable) {}
            try {
                (safeGetField(pm, "handler") as? Handler)?.removeCallbacksAndMessages(null)
            } catch (_: Throwable) {}

            if (hadActiveIntent || exo?.playWhenReady == true || markedWasPlaying) {
                if ((hadActiveIntent || markedWasPlaying) && !isFullScreenOrOpenAdActive()) {
                    if (!markedWasPlaying) {
                        setWasPlayingBeforeStop(act, true)
                    }
                    if (!isPlayingNow) {
                        pausedWhileBufferingByStop = true
                    }
                }
                try {
                    safeCall(pm, "pause")
                } catch (_: Throwable) {}
                try {
                    exo?.playWhenReady = false
                } catch (_: Throwable) {}
            }
        } catch (t: Throwable) {
            Log.w(TAG, "onMainActivityStoppedHook failed", t)
        }
    }

    @JvmStatic
    fun isStreamActiveOrBuffering(activity: Activity): Boolean {
        return try {
            val vm = resolveIptvViewModelForActivity(activity) ?: return false
            val pm = safeCall(vm, "getPlayerManager") ?: return false
            val isPlayingNow = ((safeCall(pm, "isPlaying") as? StateFlow<*>)?.value as? Boolean) == true
            if (isPlayingNow) return true
            val currentCh = safeGetField(pm, "currentChannel") ?: return false
            val stateName = ((safeCall(pm, "getPlayerState") as? StateFlow<*>)?.value)?.javaClass?.simpleName ?: ""
            if (stateName == "Error" || stateName == "Idle" || stateName == "Ended") return false
            val exo = safeCall(pm, "getExoPlayer") as? ExoPlayer
            if (exo != null) {
                exo.playWhenReady && (
                    stateName == "Buffering" ||
                    stateName == "Connecting" ||
                    exo.playbackState == Player.STATE_BUFFERING ||
                    exo.playbackState == Player.STATE_READY
                )
            } else {
                stateName == "Buffering" || stateName == "Connecting"
            }
        } catch (_: Throwable) {
            false
        }
    }

    @JvmStatic
    fun shouldBlockPlayChannel(viewModel: Any, channel: Any): Boolean {
        // Hook F: Called from MainActivity PiP & lifecycle checks with (MainActivity, MainActivity)
        if (viewModel is Activity && channel === viewModel) {
            return isStreamActiveOrBuffering(viewModel)
        }

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

        // Hook D: Called from IptvViewModel.deleteCustomPlaylist with (IptvViewModel, Long customId)
        if (channel is Long) {
            resolveContext(viewModel)
            onCustomPlaylistDeleted(viewModel, channel)
            return false
        }

        // Hook E: Called from IptvViewModel.deletePresetPlaylist with (IptvViewModel, String presetId)
        if (channel is String && channel.startsWith("preset_")) {
            resolveContext(viewModel)
            onPresetPlaylistDeleted(viewModel, channel)
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

            recentlyDeletedCustomIds.clear()
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
    @androidx.compose.runtime.NonRestartableComposable
    @JvmStatic
    fun RenderHomeTopBarCrownAndDailyCheck() {
        if (HomeChannelCardHook.tryRenderPendingHomeCard()) {
            return
        }
        val appearanceTarget = pendingAppearanceTarget
        if (appearanceTarget != null) {
            pendingAppearanceTarget = null
            RenderAppearanceThemeSelector(appearanceTarget)
            return
        }
        val bannerVm = pendingBannerDropdownVm
        if (bannerVm != null) {
            val bannerCustom = pendingBannerDropdownCustomList
            val bannerMenu = pendingBannerDropdownMenuState
            pendingBannerDropdownVm = null
            pendingBannerDropdownCustomList = null
            pendingBannerDropdownMenuState = null
            RenderPlaylistSwitcherDropdownItems(bannerVm, bannerCustom, bannerMenu)
            return
        }
        val dropdownTarget = pendingHomeDropdownTarget
        if (dropdownTarget != null) {
            pendingHomeDropdownTarget = null
            RenderHomePlaylistDropdownItems(dropdownTarget)
            return
        }
        if (SplitPortraitHooks.tryRenderPendingSplitUi()) {
            return
        }
        RenderHomeTopBarCrownAndDailyCheckImpl()
    }

    private fun extractAppColor(appColorsObj: Any?, fieldName: String, fallback: Color): Color {
        if (appColorsObj == null) return fallback
        return try {
            val raw = safeGetField(appColorsObj, fieldName) as? Long ?: return fallback
            Color(raw.toULong())
        } catch (_: Throwable) {
            fallback
        }
    }

    private fun syncCachedAppColors(isDark: Boolean) {
        try {
            val themeKtCls = Class.forName("com.example.ui.theme.ThemeKt")
            val getterName = if (isDark) "getDarkAppColors" else "getLightAppColors"
            val colorsObj = themeKtCls.getMethod(getterName).invoke(null) ?: return
            val bannerCls = Class.forName("com.example.ui.screens.HomeBannerCarouselKt")
            val cachedField = bannerCls.getDeclaredField("cachedAppColors")
            cachedField.isAccessible = true
            cachedField.set(null, colorsObj)
        } catch (_: Throwable) {
        }
    }

    private fun applyThemeModeSelection(
        context: Context,
        viewModel: Any?,
        targetKey: String,
        systemInDark: Boolean
    ) {
        val normalizedKey = when (targetKey.lowercase()) {
            "light" -> "light"
            "dark" -> "dark"
            else -> "system"
        }
        try {
            context.getSharedPreferences("hypnotix_prefs", Context.MODE_PRIVATE)
                .edit()
                .putString("app_theme_mode", normalizedKey)
                .apply()
        } catch (_: Throwable) {
        }

        val vm = viewModel ?: discoverViewModelIfNeeded()
        if (vm != null) {
            try {
                val enumCls = Class.forName("com.example.data.model.AppThemeMode")
                val enumName = when (normalizedKey) {
                    "light" -> "LIGHT"
                    "dark" -> "DARK"
                    else -> "SYSTEM"
                }
                val enumConst = enumCls.enumConstants?.firstOrNull {
                    (it as? Enum<*>)?.name == enumName
                }
                if (enumConst != null) {
                    val setMethod = vm.javaClass.getMethod("setThemeMode", enumCls)
                    setMethod.invoke(vm, enumConst)
                }
            } catch (t: Throwable) {
                Log.w(TAG, "applyThemeModeSelection failed", t)
            }
        }

        val effectiveDark = when (normalizedKey) {
            "dark" -> true
            "light" -> false
            else -> systemInDark
        }
        syncCachedAppColors(effectiveDark)
    }

    @Composable
    private fun RenderAppearanceThemeSelector(target: Any) {
        val context = LocalContext.current
        updateContextAndActivity(context)
        val viewModel = remember { discoverViewModelIfNeeded() }
        val systemInDark = isSystemInDarkTheme()

        val initialKey = remember {
            try {
                context.getSharedPreferences("hypnotix_prefs", Context.MODE_PRIVATE)
                    .getString("app_theme_mode", "system")?.lowercase() ?: "system"
            } catch (_: Throwable) {
                "system"
            }
        }
        var localSelectedKey by remember { mutableStateOf(initialKey) }

        val themeFlow = remember(viewModel) {
            safeCall(viewModel, "getCurrentThemeMode") as? StateFlow<*>
        }
        val vmThemeState = themeFlow?.collectAsState()
        val vmKey = vmThemeState?.value?.let { modeObj ->
            (safeCall(modeObj, "getKey") as? String)?.lowercase()
                ?: modeObj.toString().lowercase()
        }
        val selectedKey = when (vmKey ?: localSelectedKey) {
            "light" -> "light"
            "dark" -> "dark"
            else -> "system"
        }

        val appColorsObj = safeGetField(target, "\$appColors")
        val fallbackIsDark = (safeGetField(target, "\$isDark") as? Boolean) ?: false
        val isEffectiveDark = when (selectedKey) {
            "dark" -> true
            "light" -> false
            else -> systemInDark
        }
        val isDarkUi = (safeGetField(appColorsObj, "isDark") as? Boolean)
            ?: (if (appColorsObj == null) fallbackIsDark else isEffectiveDark)

        val textPrimary = extractAppColor(
            appColorsObj,
            "textPrimary",
            if (isDarkUi) Color.White else Color(0xFF111827)
        )
        val textSecondary = extractAppColor(
            appColorsObj,
            "textSecondary",
            if (isDarkUi) Color(0xFFA1A1AA) else Color(0xFF52525B)
        )
        val accentRed = Color(0xFFE50914)
        val selectedBg = if (isDarkUi) Color(0x2AE50914) else Color(0x14E50914)
        val unselectedBg = if (isDarkUi) Color(0xFF161618) else Color(0xFFF3F4F6)
        val unselectedBorder = if (isDarkUi) Color(0xFF2A2A2E) else Color(0xFFE5E7EB)

        val headerIcon = when (selectedKey) {
            "light" -> Icons.Filled.LightMode
            "dark" -> Icons.Filled.DarkMode
            else -> Icons.Filled.BrightnessAuto
        }
        val activeBadgeLabel = when (selectedKey) {
            "light" -> "Light"
            "dark" -> "Dark"
            else -> "Auto"
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(accentRed.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = headerIcon,
                        contentDescription = "Theme Mode",
                        tint = accentRed,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Text(
                    text = "Theme Mode",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = textPrimary,
                    modifier = Modifier.weight(1f)
                )

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(selectedBg)
                        .border(1.dp, accentRed.copy(alpha = 0.45f), RoundedCornerShape(50))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = activeBadgeLabel,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = accentRed
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ThemeModeOptionTile(
                    label = "System Default",
                    icon = Icons.Filled.BrightnessAuto,
                    isSelected = selectedKey == "system",
                    accentRed = accentRed,
                    selectedBg = selectedBg,
                    unselectedBg = unselectedBg,
                    unselectedBorder = unselectedBorder,
                    textPrimary = textPrimary,
                    textSecondary = textSecondary,
                    testTag = "theme_option_system",
                    modifier = Modifier.weight(1f),
                    onClick = {
                        localSelectedKey = "system"
                        applyThemeModeSelection(context, viewModel, "system", systemInDark)
                    }
                )

                ThemeModeOptionTile(
                    label = "Light",
                    icon = Icons.Filled.LightMode,
                    isSelected = selectedKey == "light",
                    accentRed = accentRed,
                    selectedBg = selectedBg,
                    unselectedBg = unselectedBg,
                    unselectedBorder = unselectedBorder,
                    textPrimary = textPrimary,
                    textSecondary = textSecondary,
                    testTag = "theme_option_light",
                    modifier = Modifier.weight(1f),
                    onClick = {
                        localSelectedKey = "light"
                        applyThemeModeSelection(context, viewModel, "light", systemInDark)
                    }
                )

                ThemeModeOptionTile(
                    label = "Dark",
                    icon = Icons.Filled.DarkMode,
                    isSelected = selectedKey == "dark",
                    accentRed = accentRed,
                    selectedBg = selectedBg,
                    unselectedBg = unselectedBg,
                    unselectedBorder = unselectedBorder,
                    textPrimary = textPrimary,
                    textSecondary = textSecondary,
                    testTag = "theme_option_dark",
                    modifier = Modifier.weight(1f),
                    onClick = {
                        localSelectedKey = "dark"
                        applyThemeModeSelection(context, viewModel, "dark", systemInDark)
                    }
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            val statusDescription = when (selectedKey) {
                "light" -> "Clean White theme active: crisp bright styling."
                "dark" -> "OLED True Black theme active: rich dark styling."
                else -> "Following device theme (Currently ${if (systemInDark) "Dark" else "Light"})."
            }
            Text(
                text = statusDescription,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                color = textSecondary
            )
        }
    }

    @Composable
    private fun ThemeModeOptionTile(
        label: String,
        icon: ImageVector,
        isSelected: Boolean,
        accentRed: Color,
        selectedBg: Color,
        unselectedBg: Color,
        unselectedBorder: Color,
        textPrimary: Color,
        textSecondary: Color,
        testTag: String,
        modifier: Modifier = Modifier,
        onClick: () -> Unit
    ) {
        val shape = RoundedCornerShape(14.dp)
        Column(
            modifier = modifier
                .heightIn(min = 84.dp)
                .clip(shape)
                .background(if (isSelected) selectedBg else unselectedBg)
                .border(
                    width = if (isSelected) 1.5.dp else 1.dp,
                    color = if (isSelected) accentRed else unselectedBorder,
                    shape = shape
                )
                .clickable(onClick = onClick)
                .testTag(testTag)
                .padding(horizontal = 6.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (isSelected) accentRed else textSecondary,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.height(7.dp))
            Text(
                text = label,
                fontSize = 12.sp,
                lineHeight = 15.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = if (isSelected) accentRed else textPrimary,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }

    @Suppress("UNCHECKED_CAST")
    @Composable
    private fun RenderHomePlaylistDropdownItems(target: Any) {
        val viewModel = safeGetField(target, "\$viewModel") ?: discoverViewModelIfNeeded()
        val showMenuState = safeGetField(target, "\$showPlaylistMenu\$delegate") as? MutableState<Boolean>
        val customPlaylistsDelegate = safeGetField(target, "\$customPlaylists\$delegate") as? State<*>
        val initialList = (customPlaylistsDelegate?.value as? List<*>)
        RenderPlaylistSwitcherDropdownItems(viewModel, initialList, showMenuState)
    }

    private fun resolveAddedPlaylistIcon(url: String, isDark: Boolean): Pair<ImageVector, Color> {
        val normalized = url.trim().lowercase(Locale.ROOT)
        return when {
            normalized.startsWith("file://") ||
                normalized.startsWith("content://") ||
                normalized.startsWith("/") ||
                (!normalized.startsWith("http://") &&
                    !normalized.startsWith("https://") &&
                    !normalized.startsWith("xtream_account://") &&
                    !normalized.contains("player_api.php")) -> {
                Icons.Filled.Description to (if (isDark) Color(0xFFFBBF24) else Color(0xFFD97706))
            }
            normalized.startsWith("xtream_account://") || normalized.contains("player_api.php") -> {
                Icons.Filled.Dns to (if (isDark) Color(0xFFA78BFA) else Color(0xFF7C3AED))
            }
            else -> {
                Icons.Filled.Link to (if (isDark) Color(0xFF60A5FA) else Color(0xFF2563EB))
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    @Composable
    private fun RenderPlaylistSwitcherDropdownItems(
        rawViewModel: Any?,
        initialCustomList: List<*>?,
        showMenuState: MutableState<Boolean>?
    ) {
        val context = LocalContext.current
        val viewModel = rawViewModel ?: discoverViewModelIfNeeded()
        val isDark = isAppInDarkMode(context, viewModel)

        val deletedFlow = safeCall(viewModel, "getDeletedPresetIds") as? StateFlow<Set<String>>
        val deletedSetState = deletedFlow?.collectAsState()
        val deletedSet = deletedSetState?.value ?: emptySet()

        val customFlow = safeCall(viewModel, "getCustomPlaylists") as? StateFlow<List<Any>>
        val customFlowState = customFlow?.collectAsState()
        val customList: List<Any> = customFlowState?.value
            ?: initialCustomList?.filterNotNull()
            ?: latestCustomEntities

        val uiStateFlow = safeCall(viewModel, "getUiState") as? StateFlow<*>
        val uiStateVal = uiStateFlow?.collectAsState()?.value
        val selectedPlaylistObj = safeCall(uiStateVal, "getSelectedPlaylist")
        val selectedPlaylistId = safeCall(selectedPlaylistObj, "getId") as? String ?: ""

        val indiaAvailable = !deletedSet.contains("preset_india")
        val globalAvailable = !deletedSet.contains("preset_global")
        val anyAvailable = indiaAvailable || globalAvailable || customList.isNotEmpty()

        val allSource = getPresetSourceByName("getALL")
        val globalSource = getPresetSourceByName("getGLOBAL")
        val indiaSource = getPresetSourceByName("getINDIA")

        val allId = safeCall(allSource, "getId") as? String ?: "preset_all"
        val globalId = safeCall(globalSource, "getId") as? String ?: "preset_global"
        val indiaId = safeCall(indiaSource, "getId") as? String ?: "preset_india"

        val textPrimary = if (isDark) Color(0xFFF1F5F9) else Color(0xFF0F172A)
        val accentRed = if (isDark) Color(0xFFF87171) else Color(0xFFDC2626)

        fun selectPlaylistOnVm(source: Any?) {
            showMenuState?.value = false
            if (viewModel == null || source == null) return
            try {
                val selectMethod = viewModel.javaClass.getMethod("selectPlaylist", source.javaClass)
                selectMethod.invoke(viewModel, source)
            } catch (_: Throwable) {
                try {
                    val homeMethod = viewModel.javaClass.getMethod("selectHomePlaylist", source.javaClass)
                    homeMethod.invoke(viewModel, source)
                } catch (_: Throwable) {
                }
            }
        }

        @Composable
        fun PlaylistMenuRow(
            title: String,
            icon: ImageVector,
            iconTint: Color,
            isSelected: Boolean,
            tag: String,
            onClick: () -> Unit
        ) {
            val activeTint = if (isSelected) accentRed else iconTint
            val labelColor = if (isSelected) accentRed else textPrimary
            DropdownMenuItem(
                modifier = Modifier.testTag(tag),
                leadingIcon = {
                    Icon(
                        imageVector = icon,
                        contentDescription = title,
                        tint = activeTint,
                        modifier = Modifier.size(20.dp)
                    )
                },
                trailingIcon = if (isSelected) {
                    {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = "Selected",
                            tint = accentRed,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                } else {
                    null
                },
                text = {
                    Text(
                        text = title,
                        color = labelColor,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                onClick = onClick
            )
        }

        if (globalAvailable && globalSource != null) {
            PlaylistMenuRow(
                title = "Global Channels",
                icon = Icons.Filled.Public,
                iconTint = if (isDark) Color(0xFF38BDF8) else Color(0xFF0284C7),
                isSelected = selectedPlaylistId == globalId,
                tag = "home_dropdown_global",
                onClick = { selectPlaylistOnVm(globalSource) }
            )
        }

        if (indiaAvailable && indiaSource != null) {
            PlaylistMenuRow(
                title = "India Channels",
                icon = Icons.Filled.LiveTv,
                iconTint = if (isDark) Color(0xFF34D399) else Color(0xFF059669),
                isSelected = selectedPlaylistId == indiaId,
                tag = "home_dropdown_india",
                onClick = { selectPlaylistOnVm(indiaSource) }
            )
        }

        if (anyAvailable && allSource != null) {
            PlaylistMenuRow(
                title = "All Channels",
                icon = Icons.Filled.PlaylistPlay,
                iconTint = if (isDark) Color(0xFF818CF8) else Color(0xFF4F46E5),
                isSelected = selectedPlaylistId == allId,
                tag = "home_dropdown_all",
                onClick = { selectPlaylistOnVm(allSource) }
            )
        }

        for (entity in customList) {
            val id = (safeCall(entity, "getId") as? Number)?.toLong() ?: continue
            val title = (safeCall(entity, "getTitle") as? String)?.trim()?.ifEmpty { "My Playlist" } ?: "My Playlist"
            val url = (safeCall(entity, "getUrl") as? String)?.trim() ?: ""
            val customId = "custom_$id"
            val (itemIcon, itemTint) = resolveAddedPlaylistIcon(url, isDark)
            PlaylistMenuRow(
                title = title,
                icon = itemIcon,
                iconTint = itemTint,
                isSelected = selectedPlaylistId == customId,
                tag = "home_dropdown_custom_$id",
                onClick = {
                    val src = createPlaylistSource(customId, title, url, false)
                    selectPlaylistOnVm(src)
                }
            )
        }

        if (!anyAvailable) {
            PlaylistMenuRow(
                title = "Add Playlist",
                icon = Icons.Filled.PlaylistAdd,
                iconTint = accentRed,
                isSelected = false,
                tag = "home_dropdown_add",
                onClick = {
                    showMenuState?.value = false
                    openAddPlaylistUi(viewModel)
                }
            )
        }
    }

    @Composable
    private fun RenderHomeTopBarCrownAndDailyCheckImpl() {
        val context = LocalContext.current
        updateContextAndActivity(context)
        val isPremium by PremiumManager.isPremiumState
        val isCenterOpen = NotificationSyncBridge.getCenterOpenState()?.value ?: false
        val isRefreshing = NotificationSyncBridge.getRefreshingState()?.value ?: false

        LaunchedEffect(isCenterOpen) {
            if (isCenterOpen) {
                NotificationSyncBridge.onNotificationCenterOpened(context)
            }
        }

        LaunchedEffect(isRefreshing) {
            NotificationSyncBridge.onNotificationRefreshStateChanged(context, isRefreshing)
        }

        LaunchedEffect(Unit) {
            NotificationSyncBridge.ensureInitialized(context)
            NotificationSyncBridge.pollLocalTrayAndPrefs(context)
            PremiumManager.init(context)
            syncCachedAppColors(isAppInDarkMode(context, discoverViewModelIfNeeded()))
            checkRemoteMinimumVersion(context)
            delay(3500L)
            if (!isForceUpdateRequired && PremiumManager.shouldShowDailyPrompt(context)) {
                PremiumManager.markDailyPromptShown(context)
                SubscriptionActivity.start(context)
            }
            while (true) {
                delay(2000L)
                NotificationSyncBridge.pollLocalTrayAndPrefs(context)
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
