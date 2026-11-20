package com.example.data.repository

import android.content.Context
import android.util.Log
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.lang.reflect.Constructor
import java.lang.reflect.Field
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.lang.ref.WeakReference
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.Continuation
import kotlin.coroutines.intrinsics.suspendCoroutineUninterceptedOrReturn
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Persistent on-disk binary cache for M3U playlists and Xtream Live/VOD channel lists.
 *
 * Designed for near-instant cold-start reopening (sub-50ms deserialization for thousands of
 * channels) while preserving stale-while-revalidate background freshness and explicit manual
 * refresh semantics.
 */
object ChannelDiskCache {

    private const val TAG = "ChannelDiskCache"
    private const val CACHE_DIR_NAME = "channel_disk_cache"
    private const val FILE_MAGIC = 0x48504348 // "HPCH"
    private const val FILE_VERSION = 1
    private const val MAX_CHANNEL_COUNT = 500_000
    private const val MAX_STRING_BYTES = 1_048_576

    internal const val FRESH_TTL_MS = 30L * 60L * 1000L // 30 minutes
    private const val PLAYLIST_PREFIX = "pl_"
    private const val XTREAM_PREFIX = "xt_"
    private const val FILE_EXT = ".bin"

    internal data class ChannelRecord(
        val id: String,
        val name: String,
        val logoUrl: String?,
        val category: String,
        val language: String,
        val country: String,
        val streamUrl: String,
        val isFavorite: Boolean,
        val playlistName: String,
        val userAgent: String?,
        val referer: String?,
        val isMovieOrVod: Boolean
    )

    internal data class RawCacheEntry(
        val sourceKey: String,
        val savedAtMs: Long,
        val records: List<ChannelRecord>
    )

    private data class HydratedCacheEntry(
        val channels: List<Any>,
        val savedAtMs: Long
    )

    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var appContext: Context? = null

    @Volatile
    private var overrideCacheDir: File? = null

    private val prewarmedPlaylists = ConcurrentHashMap<String, HydratedCacheEntry>()
    private val lastSavedAtByKey = ConcurrentHashMap<String, Long>()
    private val forceRefreshByUrl = ConcurrentHashMap<String, Long>()
    private val forceRefreshAllAtMs = AtomicLong(0L)
    private val inFlightBgRefresh = ConcurrentHashMap.newKeySet<String>()
    private val allPlaylistsHydrated = AtomicBoolean(false)
    private val xtreamJobsByVm = ConcurrentHashMap<Int, Job>()
    private val activeManualRefreshCount = AtomicInteger(0)
    private val allPlaylistsRefreshing = AtomicBoolean(false)

    @Volatile
    private var lastViewModelRef: WeakReference<Any>? = null

    // Cached reflection handles for com.example.data.model.Channel
    @Volatile
    private var channelClass: Class<*>? = null
    @Volatile
    private var channelCtor: Constructor<*>? = null
    @Volatile
    private var getterId: Method? = null
    @Volatile
    private var getterName: Method? = null
    @Volatile
    private var getterLogoUrl: Method? = null
    @Volatile
    private var getterCategory: Method? = null
    @Volatile
    private var getterLanguage: Method? = null
    @Volatile
    private var getterCountry: Method? = null
    @Volatile
    private var getterStreamUrl: Method? = null
    @Volatile
    private var getterIsFavorite: Method? = null
    @Volatile
    private var getterPlaylistName: Method? = null
    @Volatile
    private var getterUserAgent: Method? = null
    @Volatile
    private var getterReferer: Method? = null
    @Volatile
    private var getterIsMovieOrVod: Method? = null

    @Volatile
    private var playlistCacheField: Field? = null

    @JvmStatic
    fun init(context: Context?) {
        val app = context?.applicationContext ?: return
        if (appContext == null) {
            appContext = app
        }
    }

    @JvmStatic
    fun initAndPrewarm(context: Context?) {
        val app = context?.applicationContext ?: return
        init(app)
        ioScope.launch {
            try {
                val prefs = app.getSharedPreferences("hypnotix_prefs", Context.MODE_PRIVATE)
                val urls = LinkedHashSet<String>()
                prefs.getString("selected_playlist_url", null)?.trim()?.takeIf { it.isNotEmpty() }?.let { urls.add(it) }
                prefs.getString("channel_selected_playlist_url", null)?.trim()?.takeIf { it.isNotEmpty() }?.let { urls.add(it) }
                for (url in urls) {
                    if (!prewarmedPlaylists.containsKey(url)) {
                        readPlaylistHydrated(url)?.let { entry ->
                            prewarmedPlaylists[url] = entry
                            lastSavedAtByKey[url] = entry.savedAtMs
                        }
                    }
                }
            } catch (t: Throwable) {
                safeLogWarn("Prewarm failed", t)
            }
        }
    }

    @JvmStatic
    fun registerViewModel(viewModel: Any?) {
        if (viewModel != null) {
            lastViewModelRef = WeakReference(viewModel)
            ensureContextFromViewModel(viewModel)
        }
    }

    @JvmStatic
    fun clearPlaylistCache(repo: Any, url: String?) {
        val cacheMap = getPlaylistCacheMap(repo)
        if (url != null) {
            cacheMap?.remove(url)
            val trimmed = url.trim()
            if (trimmed != url) {
                cacheMap?.remove(trimmed)
            }
        } else {
            cacheMap?.clear()
        }
        markPlaylistForceRefresh(url)
    }

    @JvmStatic
    fun markPlaylistForceRefresh(url: String?) {
        val now = System.currentTimeMillis()
        if (url.isNullOrBlank()) {
            forceRefreshAllAtMs.set(now)
            prewarmedPlaylists.clear()
        } else {
            val trimmed = url.trim()
            forceRefreshByUrl[trimmed] = now
            prewarmedPlaylists.remove(trimmed)
        }
    }

    @JvmStatic
    fun setAllPlaylistsRefreshing(active: Boolean) {
        allPlaylistsRefreshing.set(active)
        if (!active) {
            forceRefreshAllAtMs.set(0L)
        }
    }

    @JvmStatic
    fun isPlaylistForceRefreshPending(url: String?): Boolean {
        val trimmed = url?.trim().orEmpty()
        return if (trimmed.isEmpty()) {
            forceRefreshAllAtMs.get() > 0L || allPlaylistsRefreshing.get()
        } else {
            isPlaylistForceRefresh(trimmed)
        }
    }

    @JvmStatic
    fun removePlaylist(url: String?) {
        val trimmed = url?.trim().orEmpty()
        if (trimmed.isEmpty()) return
        prewarmedPlaylists.remove(trimmed)
        lastSavedAtByKey.remove(trimmed)
        forceRefreshByUrl.remove(trimmed)
        ioScope.launch {
            deleteFileForKey(PLAYLIST_PREFIX, trimmed)
        }
    }

    @JvmStatic
    fun wrapLoadChannelsFlow(
        repo: Any,
        source: Any,
        upstream: Flow<List<Any>>
    ): Flow<List<Any>> {
        val sourceId = invokeStringGetter(source, "getId")
        val rawUrl = invokeStringGetter(source, "getUrl")
        val url = rawUrl.trim()

        if (sourceId == "preset_all" || url.isEmpty()) {
            return flow {
                if (allPlaylistsRefreshing.get()) {
                    emit(emptyList())
                    return@flow
                }
                val forceAll = forceRefreshAllAtMs.get() > 0L
                if (!forceAll) {
                    hydrateAllCachedPlaylistsIntoMemory(repo)
                }
                emitAll(upstream)
            }.flowOn(Dispatchers.IO)
        }

        return flow {
            val cacheMap = getPlaylistCacheMap(repo)
            val forceRefresh = isPlaylistForceRefresh(url)

            var cachedList: List<Any>? = null
            var cachedSavedAtMs = 0L

            if (!forceRefresh) {
                val mem = getNonEmptyList(cacheMap?.get(rawUrl) ?: cacheMap?.get(url))
                if (mem != null) {
                    cachedList = mem
                    cachedSavedAtMs = lastSavedAtByKey[url] ?: System.currentTimeMillis()
                } else {
                    val prewarmed = prewarmedPlaylists[url]
                    val hydrated = prewarmed ?: withContext(Dispatchers.IO) { readPlaylistHydrated(url) }
                    if (hydrated != null && hydrated.channels.isNotEmpty()) {
                        cachedList = hydrated.channels
                        cachedSavedAtMs = hydrated.savedAtMs
                        cacheMap?.put(rawUrl, hydrated.channels)
                        if (rawUrl != url) {
                            cacheMap?.put(url, hydrated.channels)
                        }
                        prewarmedPlaylists[url] = hydrated
                        lastSavedAtByKey[url] = hydrated.savedAtMs
                    }
                }
            }

            if (cachedList != null) {
                emit(cachedList)

                val now = System.currentTimeMillis()
                val localModified = isLocalFileModifiedSince(url, cachedSavedAtMs)
                val isFresh = !localModified && (now - cachedSavedAtMs) < FRESH_TTL_MS
                if (isFresh) {
                    return@flow
                }

                // Stale-while-revalidate: UI already has channels; refresh once silently.
                if (!inFlightBgRefresh.add(url)) {
                    return@flow
                }
                try {
                    upstream.collect { emitted ->
                        if (emitted === cachedList || emitted.isEmpty()) {
                            return@collect
                        }
                        if (getNonEmptyList(cacheMap?.get(rawUrl) ?: cacheMap?.get(url)) != null) {
                            persistPlaylistAsync(url, emitted)
                            emit(emitted)
                        }
                    }
                } finally {
                    inFlightBgRefresh.remove(url)
                }
                return@flow
            }

            // Cold first load (no disk cache yet) or explicit manual refresh.
            if (forceRefresh) {
                activeManualRefreshCount.incrementAndGet()
                setPlaylistLoadingOnViewModel(true)
                emit(emptyList())
            }
            var persistedFresh = false
            var lastFallbackEmitted: List<Any>? = null
            try {
                upstream.collect { emitted ->
                    val cachePopulated = getNonEmptyList(cacheMap?.get(rawUrl) ?: cacheMap?.get(url)) != null
                    if (cachePopulated && emitted.isNotEmpty()) {
                        persistedFresh = true
                        persistPlaylistAsync(url, emitted)
                        clearPlaylistForceRefresh(url)
                        emit(emitted)
                    } else if (!forceRefresh) {
                        emit(emitted)
                    } else if (emitted.isNotEmpty()) {
                        lastFallbackEmitted = emitted
                    }
                }

                // If user tapped manual refresh while offline and network failed, restore disk cache
                // (or preset fallback) instead of leaving an empty channel list.
                if (!persistedFresh && forceRefresh) {
                    val alreadyPopulated = getNonEmptyList(cacheMap?.get(rawUrl) ?: cacheMap?.get(url))
                    if (alreadyPopulated != null) {
                        emit(alreadyPopulated)
                    } else {
                        val fallbackDisk = withContext(Dispatchers.IO) { readPlaylistHydrated(url) }
                        if (fallbackDisk != null && fallbackDisk.channels.isNotEmpty()) {
                            cacheMap?.put(rawUrl, fallbackDisk.channels)
                            prewarmedPlaylists[url] = fallbackDisk
                            emit(fallbackDisk.channels)
                        } else if (!lastFallbackEmitted.isNullOrEmpty()) {
                            emit(lastFallbackEmitted!!)
                        }
                    }
                }
            } finally {
                if (forceRefresh && activeManualRefreshCount.decrementAndGet() <= 0) {
                    activeManualRefreshCount.set(0)
                    if (!allPlaylistsRefreshing.get()) {
                        setPlaylistLoadingOnViewModel(false)
                    }
                }
            }
        }.flowOn(Dispatchers.IO)
    }

    @JvmStatic
    fun loadXtreamChannels(viewModel: Any, account: Any) {
        runXtreamLoad(viewModel, account, forceRefresh = false, fetchLive = true, fetchVod = true)
    }

    @JvmStatic
    fun refreshXtreamChannels(viewModel: Any) {
        val activeFlow = safeGetField(viewModel, "activeXtreamAccount") as? StateFlow<*>
        val account = activeFlow?.value ?: return
        runXtreamLoad(viewModel, account, forceRefresh = true, fetchLive = true, fetchVod = true)
    }

    @JvmStatic
    fun refreshSelectedXtreamOnly(viewModel: Any, isVodOnly: Boolean?) {
        val activeFlow = safeGetField(viewModel, "activeXtreamAccount") as? StateFlow<*>
        val account = activeFlow?.value ?: return
        val fetchLive = isVodOnly != true
        val fetchVod = isVodOnly != false
        runXtreamLoad(viewModel, account, forceRefresh = true, fetchLive = fetchLive, fetchVod = fetchVod)
    }

    @Suppress("UNCHECKED_CAST")
    private fun runXtreamLoad(
        viewModel: Any,
        account: Any,
        forceRefresh: Boolean,
        fetchLive: Boolean = true,
        fetchVod: Boolean = true
    ) {
        registerViewModel(viewModel)
        val vmKey = System.identityHashCode(viewModel)
        xtreamJobsByVm.remove(vmKey)?.cancel()

        val liveFlow = safeGetField(viewModel, "_xtreamChannels") as? MutableStateFlow<List<Any>>
        val vodFlow = safeGetField(viewModel, "_xtreamVodChannels") as? MutableStateFlow<List<Any>>
        val loadingFlow = safeGetField(viewModel, "_isXtreamLoading") as? MutableStateFlow<Boolean>
        val xtreamRepo = safeGetField(viewModel, "xtreamRepository")

        if (liveFlow == null || vodFlow == null || loadingFlow == null || xtreamRepo == null) {
            return
        }

        val liveKey = buildXtreamKey("live", account)
        val vodKey = buildXtreamKey("vod", account)
        val scope = resolveViewModelScope(viewModel)

        val previousLive = liveFlow.value
        val previousVod = vodFlow.value

        val job = scope.launch {
            var hadCachedData = false
            var cachedLive: HydratedCacheEntry? = null
            var cachedVod: HydratedCacheEntry? = null

            if (!forceRefresh) {
                val (diskLive, diskVod) = withContext(Dispatchers.IO) {
                    val l = readEntryHydratedAllowEmpty(XTREAM_PREFIX, liveKey)
                    val v = readEntryHydratedAllowEmpty(XTREAM_PREFIX, vodKey)
                    Pair(l, v)
                }
                cachedLive = diskLive
                cachedVod = diskVod
                val liveList = diskLive?.channels.orEmpty()
                val vodList = diskVod?.channels.orEmpty()
                if (liveList.isNotEmpty() || vodList.isNotEmpty()) {
                    hadCachedData = true
                    liveFlow.value = liveList
                    vodFlow.value = vodList
                    loadingFlow.value = false

                    val now = System.currentTimeMillis()
                    val liveFresh = diskLive != null && (now - diskLive.savedAtMs) < FRESH_TTL_MS
                    val vodFresh = diskVod != null && (now - diskVod.savedAtMs) < FRESH_TTL_MS
                    if (liveFresh && vodFresh) {
                        return@launch
                    }
                }
            } else {
                // Explicit manual refresh: clear only the selected section while loading.
                loadingFlow.value = true
                if (fetchLive) liveFlow.value = emptyList()
                if (fetchVod) vodFlow.value = emptyList()
            }

            if (!hadCachedData || forceRefresh) {
                loadingFlow.value = true
            }

            try {
                val (freshLive, freshVod) = coroutineScope {
                    val liveDeferred = if (fetchLive) {
                        async(Dispatchers.IO) {
                            fetchXtreamListSafe(xtreamRepo, "fetchLiveStreams", account)
                        }
                    } else null
                    val vodDeferred = if (fetchVod) {
                        async(Dispatchers.IO) {
                            fetchXtreamListSafe(xtreamRepo, "fetchVodStreams", account)
                        }
                    } else null
                    Pair(liveDeferred?.await(), vodDeferred?.await())
                }

                val anyFreshNonEmpty = !freshLive.isNullOrEmpty() || !freshVod.isNullOrEmpty()

                if (fetchLive) {
                    if (freshLive != null && (freshLive.isNotEmpty() || !hadCachedData)) {
                        liveFlow.value = freshLive
                        if (freshLive.isNotEmpty() || anyFreshNonEmpty) {
                            writeEntryAsync(XTREAM_PREFIX, liveKey, freshLive, allowEmpty = anyFreshNonEmpty)
                        }
                    } else if (forceRefresh && previousLive.isNotEmpty()) {
                        liveFlow.value = previousLive
                    } else if (freshLive == null && !hadCachedData && cachedLive != null) {
                        liveFlow.value = cachedLive.channels
                    }
                }

                if (fetchVod) {
                    if (freshVod != null && (freshVod.isNotEmpty() || !hadCachedData)) {
                        vodFlow.value = freshVod
                        if (freshVod.isNotEmpty() || anyFreshNonEmpty) {
                            writeEntryAsync(XTREAM_PREFIX, vodKey, freshVod, allowEmpty = anyFreshNonEmpty)
                        }
                    } else if (forceRefresh && previousVod.isNotEmpty()) {
                        vodFlow.value = previousVod
                    } else if (freshVod == null && !hadCachedData && cachedVod != null) {
                        vodFlow.value = cachedVod.channels
                    }
                }
            } catch (ce: CancellationException) {
                if (forceRefresh) {
                    if (fetchLive && liveFlow.value.isEmpty() && previousLive.isNotEmpty()) {
                        liveFlow.value = previousLive
                    }
                    if (fetchVod && vodFlow.value.isEmpty() && previousVod.isNotEmpty()) {
                        vodFlow.value = previousVod
                    }
                }
                throw ce
            } catch (t: Throwable) {
                safeLogWarn("Xtream load failed", t)
                if (forceRefresh) {
                    if (fetchLive && liveFlow.value.isEmpty() && previousLive.isNotEmpty()) {
                        liveFlow.value = previousLive
                    }
                    if (fetchVod && vodFlow.value.isEmpty() && previousVod.isNotEmpty()) {
                        vodFlow.value = previousVod
                    }
                }
            } finally {
                loadingFlow.value = false
            }
        }
        xtreamJobsByVm[vmKey] = job
    }

    private suspend fun fetchXtreamListSafe(
        xtreamRepo: Any,
        methodName: String,
        account: Any
    ): List<Any>? {
        return try {
            val result = invokeSuspendMethod1(xtreamRepo, methodName, account)
            (result as? List<*>)?.filterNotNull()
        } catch (ce: CancellationException) {
            throw ce
        } catch (t: Throwable) {
            safeLogWarn("Xtream $methodName failed", t)
            null
        }
    }

    private suspend fun invokeSuspendMethod1(target: Any, methodName: String, arg: Any): Any? {
        return suspendCoroutineUninterceptedOrReturn { cont ->
            val method = target.javaClass.methods.firstOrNull { m ->
                m.name == methodName &&
                    m.parameterTypes.size == 2 &&
                    Continuation::class.java.isAssignableFrom(m.parameterTypes[1])
            } ?: throw NoSuchMethodException("${target.javaClass.name}.$methodName")
            try {
                method.invoke(target, arg, cont)
            } catch (ite: InvocationTargetException) {
                throw ite.targetException ?: ite
            }
        }
    }

    private fun resolveViewModelScope(viewModel: Any): CoroutineScope {
        return try {
            val vmKt = Class.forName("androidx.lifecycle.ViewModelKt")
            val vmClass = Class.forName("androidx.lifecycle.ViewModel")
            val method = vmKt.getMethod("getViewModelScope", vmClass)
            (method.invoke(null, viewModel) as? CoroutineScope) ?: ioScope
        } catch (_: Throwable) {
            ioScope
        }
    }

    private fun buildXtreamKey(kind: String, account: Any): String {
        val id = (invokeNoArg(account, "getId") as? Number)?.toLong() ?: 0L
        val server = invokeStringGetter(account, "getServerUrl").trim().lowercase().trimEnd('/')
        val user = invokeStringGetter(account, "getUsername").trim()
        return "$kind|$id|$server|$user"
    }

    private fun isPlaylistForceRefresh(url: String): Boolean {
        val lastSaved = lastSavedAtByKey[url] ?: 0L
        val urlForceAt = forceRefreshByUrl[url] ?: 0L
        val allForceAt = forceRefreshAllAtMs.get()
        val forceAt = maxOf(urlForceAt, allForceAt)
        return forceAt > 0L && forceAt >= lastSaved
    }

    private fun clearPlaylistForceRefresh(url: String) {
        forceRefreshByUrl.remove(url)
    }

    private fun persistPlaylistAsync(url: String, channels: List<Any>) {
        val now = System.currentTimeMillis()
        lastSavedAtByKey[url] = now
        prewarmedPlaylists[url] = HydratedCacheEntry(channels, now)
        writeEntryAsync(PLAYLIST_PREFIX, url, channels, now)
    }

    private fun writeEntryAsync(
        prefix: String,
        key: String,
        channels: List<Any>,
        savedAtMs: Long = System.currentTimeMillis(),
        allowEmpty: Boolean = false
    ) {
        if (channels.isEmpty() && !allowEmpty) return
        val records = channels.mapNotNull { toChannelRecord(it) }
        if (records.isEmpty() && !allowEmpty) return
        ioScope.launch {
            writeRawEntry(prefix, key, RawCacheEntry(key, savedAtMs, records))
        }
    }

    private suspend fun hydrateAllCachedPlaylistsIntoMemory(repo: Any) {
        if (!allPlaylistsHydrated.compareAndSet(false, true)) return
        val cacheMap = getPlaylistCacheMap(repo) ?: return
        withContext(Dispatchers.IO) {
            val dir = resolveCacheDir() ?: return@withContext
            val files = dir.listFiles { f ->
                f.isFile && f.name.startsWith(PLAYLIST_PREFIX) && f.name.endsWith(FILE_EXT)
            } ?: return@withContext
            for (file in files) {
                try {
                    val raw = readRawFile(file) ?: continue
                    if (raw.sourceKey.isEmpty() || raw.records.isEmpty()) continue
                    if (getNonEmptyList(cacheMap[raw.sourceKey]) != null) continue
                    val hydrated = raw.records.mapNotNull { fromChannelRecord(it) }
                    if (hydrated.isNotEmpty()) {
                        cacheMap[raw.sourceKey] = hydrated
                        prewarmedPlaylists[raw.sourceKey] = HydratedCacheEntry(hydrated, raw.savedAtMs)
                        lastSavedAtByKey[raw.sourceKey] = raw.savedAtMs
                    }
                } catch (t: Throwable) {
                    safeLogWarn("Hydrate failed for ${file.name}", t)
                }
            }
        }
    }

    private fun isLocalFileModifiedSince(url: String, savedAtMs: Long): Boolean {
        val path = when {
            url.startsWith("file://") -> url.removePrefix("file://")
            url.startsWith("/") -> url
            else -> return false
        }
        return try {
            val file = File(path)
            file.exists() && file.lastModified() > savedAtMs
        } catch (_: Throwable) {
            false
        }
    }

    private fun readPlaylistHydrated(url: String): HydratedCacheEntry? {
        return readEntryHydrated(PLAYLIST_PREFIX, url)
    }

    private fun readEntryHydrated(prefix: String, key: String): HydratedCacheEntry? {
        val raw = readRawEntry(prefix, key) ?: return null
        if (raw.records.isEmpty()) return null
        val hydrated = raw.records.mapNotNull { fromChannelRecord(it) }
        if (hydrated.isEmpty()) return null
        return HydratedCacheEntry(hydrated, raw.savedAtMs)
    }

    private fun readEntryHydratedAllowEmpty(prefix: String, key: String): HydratedCacheEntry? {
        val raw = readRawEntry(prefix, key) ?: return null
        if (raw.records.isEmpty()) return HydratedCacheEntry(emptyList(), raw.savedAtMs)
        val hydrated = raw.records.mapNotNull { fromChannelRecord(it) }
        if (hydrated.isEmpty()) return null
        return HydratedCacheEntry(hydrated, raw.savedAtMs)
    }

    internal fun writeRawEntry(prefix: String, key: String, entry: RawCacheEntry): Boolean {
        val dir = resolveCacheDir() ?: return false
        val target = File(dir, buildFileName(prefix, key))
        val tmp = File(dir, target.name + ".tmp")
        return try {
            DataOutputStream(BufferedOutputStream(FileOutputStream(tmp), 32 * 1024)).use { out ->
                out.writeInt(FILE_MAGIC)
                out.writeInt(FILE_VERSION)
                out.writeLong(entry.savedAtMs)
                writeNullableString(out, entry.sourceKey)
                out.writeInt(entry.records.size)
                for (r in entry.records) {
                    writeNullableString(out, r.id)
                    writeNullableString(out, r.name)
                    writeNullableString(out, r.logoUrl)
                    writeNullableString(out, r.category)
                    writeNullableString(out, r.language)
                    writeNullableString(out, r.country)
                    writeNullableString(out, r.streamUrl)
                    out.writeBoolean(r.isFavorite)
                    writeNullableString(out, r.playlistName)
                    writeNullableString(out, r.userAgent)
                    writeNullableString(out, r.referer)
                    out.writeBoolean(r.isMovieOrVod)
                }
                out.flush()
            }
            if (!tmp.renameTo(target)) {
                if (target.exists()) target.delete()
                if (!tmp.renameTo(target)) {
                    tmp.delete()
                    return false
                }
            }
            true
        } catch (t: Throwable) {
            try {
                tmp.delete()
            } catch (_: Throwable) {
            }
            safeLogWarn("writeRawEntry failed", t)
            false
        }
    }

    internal fun readRawEntry(prefix: String, key: String): RawCacheEntry? {
        val dir = resolveCacheDir() ?: return null
        val file = File(dir, buildFileName(prefix, key))
        return readRawFile(file)
    }

    internal fun readRawFile(file: File): RawCacheEntry? {
        if (!file.isFile || file.length() < 16L) return null
        return try {
            DataInputStream(BufferedInputStream(FileInputStream(file), 32 * 1024)).use { input ->
                val magic = input.readInt()
                if (magic != FILE_MAGIC) return null
                val version = input.readInt()
                if (version != FILE_VERSION) return null
                val savedAtMs = input.readLong()
                val sourceKey = readNullableString(input) ?: ""
                val count = input.readInt()
                if (count < 0 || count > MAX_CHANNEL_COUNT) return null
                val records = ArrayList<ChannelRecord>(count)
                for (i in 0 until count) {
                    val id = readNullableString(input) ?: ""
                    val name = readNullableString(input) ?: ""
                    val logoUrl = readNullableString(input)
                    val category = readNullableString(input) ?: ""
                    val language = readNullableString(input) ?: ""
                    val country = readNullableString(input) ?: ""
                    val streamUrl = readNullableString(input) ?: ""
                    val isFavorite = input.readBoolean()
                    val playlistName = readNullableString(input) ?: ""
                    val userAgent = readNullableString(input)
                    val referer = readNullableString(input)
                    val isMovieOrVod = input.readBoolean()
                    records.add(
                        ChannelRecord(
                            id = id,
                            name = name,
                            logoUrl = logoUrl,
                            category = category,
                            language = language,
                            country = country,
                            streamUrl = streamUrl,
                            isFavorite = isFavorite,
                            playlistName = playlistName,
                            userAgent = userAgent,
                            referer = referer,
                            isMovieOrVod = isMovieOrVod
                        )
                    )
                }
                RawCacheEntry(sourceKey, savedAtMs, records)
            }
        } catch (t: Throwable) {
            safeLogWarn("readRawFile failed (corrupt or truncated): ${file.name}", t)
            null
        }
    }

    internal fun deleteFileForKey(prefix: String, key: String): Boolean {
        val dir = resolveCacheDir() ?: return false
        val file = File(dir, buildFileName(prefix, key))
        return try {
            if (file.exists()) file.delete() else false
        } catch (_: Throwable) {
            false
        }
    }

    internal fun buildFileName(prefix: String, key: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(key.toByteArray(Charsets.UTF_8))
        val sb = StringBuilder(prefix.length + 40 + FILE_EXT.length)
        sb.append(prefix)
        for (i in 0 until 20) {
            val b = digest[i].toInt() and 0xFF
            sb.append(HEX_CHARS[b ushr 4])
            sb.append(HEX_CHARS[b and 0x0F])
        }
        sb.append(FILE_EXT)
        return sb.toString()
    }

    private fun writeNullableString(out: DataOutputStream, value: String?) {
        if (value == null) {
            out.writeInt(-1)
            return
        }
        val bytes = value.toByteArray(Charsets.UTF_8)
        out.writeInt(bytes.size)
        if (bytes.isNotEmpty()) {
            out.write(bytes)
        }
    }

    private fun readNullableString(input: DataInputStream): String? {
        val len = input.readInt()
        if (len == -1) return null
        if (len < 0 || len > MAX_STRING_BYTES) {
            throw IllegalStateException("Invalid cached string length: $len")
        }
        if (len == 0) return ""
        val bytes = ByteArray(len)
        input.readFully(bytes)
        return String(bytes, Charsets.UTF_8)
    }

    private fun resolveCacheDir(): File? {
        overrideCacheDir?.let { custom ->
            if (!custom.exists()) custom.mkdirs()
            return custom
        }
        val ctx = appContext ?: resolveApplicationContextReflective()?.also { appContext = it } ?: return null
        val dir = File(ctx.filesDir, CACHE_DIR_NAME)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    private fun ensureContextFromViewModel(viewModel: Any) {
        if (appContext != null) return
        val app = invokeNoArg(viewModel, "getApplication") as? Context
        if (app != null) {
            appContext = app.applicationContext
        }
    }

    private fun resolveApplicationContextReflective(): Context? {
        return try {
            val atClass = Class.forName("android.app.ActivityThread")
            val currentApp = atClass.getMethod("currentApplication").invoke(null) as? Context
            currentApp?.applicationContext
        } catch (_: Throwable) {
            null
        }
    }

    private fun ensureChannelReflection(): Boolean {
        if (channelCtor != null && getterId != null) return true
        return try {
            val cls = Class.forName("com.example.data.model.Channel")
            val ctor = cls.getConstructor(
                String::class.java,
                String::class.java,
                String::class.java,
                String::class.java,
                String::class.java,
                String::class.java,
                String::class.java,
                Boolean::class.javaPrimitiveType,
                String::class.java,
                String::class.java,
                String::class.java,
                Boolean::class.javaPrimitiveType
            )
            getterId = cls.getMethod("getId")
            getterName = cls.getMethod("getName")
            getterLogoUrl = cls.getMethod("getLogoUrl")
            getterCategory = cls.getMethod("getCategory")
            getterLanguage = cls.getMethod("getLanguage")
            getterCountry = cls.getMethod("getCountry")
            getterStreamUrl = cls.getMethod("getStreamUrl")
            getterIsFavorite = cls.getMethod("isFavorite")
            getterPlaylistName = cls.getMethod("getPlaylistName")
            getterUserAgent = cls.getMethod("getUserAgent")
            getterReferer = cls.getMethod("getReferer")
            getterIsMovieOrVod = cls.getMethod("isMovieOrVod")
            channelClass = cls
            channelCtor = ctor
            true
        } catch (t: Throwable) {
            safeLogWarn("Channel reflection init failed", t)
            false
        }
    }

    private fun toChannelRecord(channel: Any): ChannelRecord? {
        if (!ensureChannelReflection()) return null
        return try {
            ChannelRecord(
                id = (getterId?.invoke(channel) as? String).orEmpty(),
                name = (getterName?.invoke(channel) as? String).orEmpty(),
                logoUrl = getterLogoUrl?.invoke(channel) as? String,
                category = (getterCategory?.invoke(channel) as? String).orEmpty(),
                language = (getterLanguage?.invoke(channel) as? String).orEmpty(),
                country = (getterCountry?.invoke(channel) as? String).orEmpty(),
                streamUrl = (getterStreamUrl?.invoke(channel) as? String).orEmpty(),
                isFavorite = (getterIsFavorite?.invoke(channel) as? Boolean) == true,
                playlistName = (getterPlaylistName?.invoke(channel) as? String).orEmpty(),
                userAgent = getterUserAgent?.invoke(channel) as? String,
                referer = getterReferer?.invoke(channel) as? String,
                isMovieOrVod = (getterIsMovieOrVod?.invoke(channel) as? Boolean) == true
            )
        } catch (t: Throwable) {
            safeLogWarn("toChannelRecord failed", t)
            null
        }
    }

    private fun fromChannelRecord(record: ChannelRecord): Any? {
        if (!ensureChannelReflection()) return null
        return try {
            channelCtor?.newInstance(
                record.id,
                record.name,
                record.logoUrl,
                record.category,
                record.language,
                record.country,
                record.streamUrl,
                record.isFavorite,
                record.playlistName,
                record.userAgent,
                record.referer,
                record.isMovieOrVod
            )
        } catch (t: Throwable) {
            safeLogWarn("fromChannelRecord failed", t)
            null
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun getPlaylistCacheMap(repo: Any): MutableMap<Any, Any>? {
        return try {
            var field = playlistCacheField
            if (field == null) {
                field = repo.javaClass.getDeclaredField("playlistCache").apply {
                    isAccessible = true
                }
                playlistCacheField = field
            }
            field?.get(repo) as? MutableMap<Any, Any>
        } catch (_: Throwable) {
            null
        }
    }

    private fun getNonEmptyList(value: Any?): List<Any>? {
        val list = (value as? List<*>)?.filterNotNull()
        return if (!list.isNullOrEmpty()) list else null
    }

    private fun safeGetField(target: Any, name: String): Any? {
        return try {
            var cls: Class<*>? = target.javaClass
            while (cls != null) {
                try {
                    val f = cls.getDeclaredField(name)
                    f.isAccessible = true
                    return f.get(target)
                } catch (_: NoSuchFieldException) {
                    cls = cls.superclass
                }
            }
            null
        } catch (_: Throwable) {
            null
        }
    }

    private fun invokeNoArg(target: Any, methodName: String): Any? {
        return try {
            target.javaClass.getMethod(methodName).invoke(target)
        } catch (_: Throwable) {
            null
        }
    }

    private fun invokeStringGetter(target: Any, methodName: String): String {
        return (invokeNoArg(target, methodName) as? String).orEmpty()
    }

    private fun safeLogWarn(message: String, t: Throwable) {
        try {
            Log.w(TAG, message, t)
        } catch (_: Throwable) {
            // Plain JVM unit test environment where android.util.Log is unmocked.
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun setPlaylistLoadingOnViewModel(loading: Boolean) {
        val vm = lastViewModelRef?.get() ?: return
        try {
            val loadingFlow = safeGetField(vm, "_isPlaylistLoading") as? MutableStateFlow<Boolean>
            loadingFlow?.value = loading
        } catch (_: Throwable) {
        }
    }

    internal fun setCacheDirForTesting(dir: File?) {
        overrideCacheDir = dir
    }

    internal fun resetForTesting() {
        overrideCacheDir = null
        prewarmedPlaylists.clear()
        lastSavedAtByKey.clear()
        forceRefreshByUrl.clear()
        forceRefreshAllAtMs.set(0L)
        inFlightBgRefresh.clear()
        allPlaylistsHydrated.set(false)
        allPlaylistsRefreshing.set(false)
        activeManualRefreshCount.set(0)
        lastViewModelRef = null
        xtreamJobsByVm.values.forEach { it.cancel() }
        xtreamJobsByVm.clear()
    }

    private val HEX_CHARS = charArrayOf(
        '0', '1', '2', '3', '4', '5', '6', '7',
        '8', '9', 'a', 'b', 'c', 'd', 'e', 'f'
    )
}
