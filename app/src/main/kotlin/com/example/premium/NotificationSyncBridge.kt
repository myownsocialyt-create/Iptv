package com.example.premium

import android.app.Activity
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.compose.runtime.MutableState
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import java.lang.reflect.Constructor
import java.lang.reflect.Method
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs

internal object NotificationSyncBridge {

    private const val TAG = "NotificationSyncBridge"
    private const val PREFS_NAME = "hypnotix_notifications_prefs"

    private const val KEY_CACHED_JSON = "cached_notifications_json"
    private const val KEY_READ_IDS = "read_notification_ids"
    private const val KEY_SYSTEM_NOTIFIED_IDS = "system_notified_ids"
    private const val KEY_NOTIFIED_IDS = "notified_ids"

    private const val KEY_SYNCED_FS_IDS = "sync_bridge_firestore_ids"
    private const val KEY_SYNCED_RC_IDS = "sync_bridge_rc_ids"
    private const val KEY_SYNCED_RTDB_IDS = "sync_bridge_rtdb_ids"
    private const val KEY_REMOTE_DELETED_IDS = "sync_bridge_remote_deleted_ids"
    private const val KEY_REMOTE_DELETED_SIGNATURES = "sync_bridge_remote_deleted_sigs"
    private const val KEY_SYNCED_SIGNATURES = "sync_bridge_synced_sigs"
    private const val KEY_CAPTURED_SBN_KEYS = "sync_bridge_captured_sbn_keys"
    private const val KEY_PROCESSED_INTENT_KEYS = "sync_bridge_processed_intent_keys"

    private const val FIRESTORE_URL =
        "https://firestore.googleapis.com/v1/projects/hypnotix-iptv/databases/(default)/documents/notifications?pageSize=100"
    private const val RTDB_URL =
        "https://hypnotix-iptv-default-rtdb.firebaseio.com/notifications.json"

    private const val MAX_NOTIFICATIONS = 100
    private const val CONTENT_DEDUP_WINDOW_MS = 15L * 60L * 1000L

    private val mainHandler = Handler(Looper.getMainLooper())
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val prefsLock = Any()

    private val remoteSyncInFlight = AtomicBoolean(false)
    private val rtdbProbeCooldownUntilMs = java.util.concurrent.atomic.AtomicLong(0L)
    private val isReconcilingWrite = AtomicBoolean(false)
    private val initialized = AtomicBoolean(false)

    @Volatile
    private var appContext: Context? = null

    @Volatile
    private var prefListener: SharedPreferences.OnSharedPreferenceChangeListener? = null

    // Cached reflection handles into NotificationCenterHelper & AppNotificationItem (classes6.dex)
    @Volatile
    private var reflectionResolved = false

    @Volatile
    private var helperInstance: Any? = null

    @Volatile
    private var ensureInitMethod: Method? = null

    @Volatile
    private var getNotificationsMethod: Method? = null

    @Volatile
    private var getReadIdsMethod: Method? = null

    @Volatile
    private var isCenterOpenMethod: Method? = null

    @Volatile
    private var isRefreshingMethod: Method? = null

    @Volatile
    private var inspectIntentMethod: Method? = null

    @Volatile
    private var itemCtor5: Constructor<*>? = null

    @Volatile
    private var itemGetIdMethod: Method? = null

    @Volatile
    private var itemGetTitleMethod: Method? = null

    @Volatile
    private var itemGetMessageMethod: Method? = null

    @Volatile
    private var itemGetImageUrlMethod: Method? = null

    @Volatile
    private var itemGetTimestampMethod: Method? = null

    private data class NotifRecord(
        val id: String,
        val title: String,
        val message: String,
        val imageUrl: String,
        val timestampMillis: Long,
        val isRead: Boolean = false
    )

    private fun resolveReflection() {
        if (reflectionResolved) return
        synchronized(this) {
            if (reflectionResolved) return
            try {
                val helperCls = Class.forName("com.example.ui.screens.NotificationCenterHelper")
                val instField = helperCls.getDeclaredField("INSTANCE").apply { isAccessible = true }
                helperInstance = instField.get(null)
                ensureInitMethod = helperCls.getMethod("ensureInitialized", Context::class.java)
                getNotificationsMethod = helperCls.getMethod("getNotifications")
                getReadIdsMethod = helperCls.getMethod("getReadIds")
                isCenterOpenMethod = helperCls.getMethod("isCenterOpen")
                isRefreshingMethod = helperCls.getMethod("isRefreshing")
                inspectIntentMethod = helperCls.getMethod(
                    "inspectActivityIntent",
                    Context::class.java,
                    Intent::class.java
                )

                val itemCls = Class.forName("com.example.ui.screens.AppNotificationItem")
                itemCtor5 = itemCls.getConstructor(
                    String::class.java,
                    String::class.java,
                    String::class.java,
                    String::class.java,
                    Long::class.javaPrimitiveType
                )
                itemGetIdMethod = itemCls.getMethod("getId")
                itemGetTitleMethod = itemCls.getMethod("getTitle")
                itemGetMessageMethod = itemCls.getMethod("getMessage")
                itemGetImageUrlMethod = itemCls.getMethod("getImageUrl")
                itemGetTimestampMethod = itemCls.getMethod("getTimestampMillis")
            } catch (t: Throwable) {
                Log.w(TAG, "Failed resolving NotificationCenterHelper reflection", t)
            } finally {
                reflectionResolved = true
            }
        }
    }

    fun ensureInitialized(context: Context?) {
        val ctx = context?.applicationContext ?: return
        appContext = ctx
        resolveReflection()

        try {
            helperInstance?.let { helper ->
                ensureInitMethod?.invoke(helper, ctx)
            }
        } catch (_: Throwable) {
        }

        if (initialized.compareAndSet(false, true)) {
            registerPrefsListener(ctx)
        }
    }

    private fun registerPrefsListener(ctx: Context) {
        try {
            val prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
                if (key == KEY_CACHED_JSON && !isReconcilingWrite.get()) {
                    mainHandler.post {
                        reconcileLocalSourcesOnMain(ctx, null)
                    }
                }
            }
            prefListener = listener
            prefs.registerOnSharedPreferenceChangeListener(listener)
        } catch (t: Throwable) {
            Log.w(TAG, "registerPrefsListener failed", t)
        }
    }

    @Suppress("UNCHECKED_CAST")
    fun getCenterOpenState(): MutableState<Boolean>? {
        resolveReflection()
        return try {
            val helper = helperInstance ?: return null
            isCenterOpenMethod?.invoke(helper) as? MutableState<Boolean>
        } catch (_: Throwable) {
            null
        }
    }

    @Suppress("UNCHECKED_CAST")
    fun getRefreshingState(): MutableState<Boolean>? {
        resolveReflection()
        return try {
            val helper = helperInstance ?: return null
            isRefreshingMethod?.invoke(helper) as? MutableState<Boolean>
        } catch (_: Throwable) {
            null
        }
    }

    fun onActivityCreatedOrStarted(activity: Activity) {
        val ctx = activity.applicationContext ?: return
        ensureInitialized(ctx)
        mainHandler.post {
            reconcileLocalSourcesOnMain(ctx, activity.intent)
        }
    }

    fun onActivityResumed(activity: Activity) {
        val ctx = activity.applicationContext ?: return
        ensureInitialized(ctx)
        mainHandler.post {
            reconcileLocalSourcesOnMain(ctx, activity.intent)
        }
        syncRemoteSourcesNow(ctx)
    }

    fun onNotificationCenterOpened(context: Context?) {
        val ctx = context?.applicationContext ?: appContext ?: return
        ensureInitialized(ctx)
        mainHandler.post {
            reconcileLocalSourcesOnMain(ctx, null)
        }
        syncRemoteSourcesNow(ctx)
    }

    fun onNotificationRefreshStateChanged(context: Context?, refreshing: Boolean) {
        val ctx = context?.applicationContext ?: appContext ?: return
        ensureInitialized(ctx)
        if (refreshing) {
            syncRemoteSourcesNow(ctx)
        } else {
            mainHandler.post {
                reconcileLocalSourcesOnMain(ctx, null)
            }
        }
    }

    fun pollLocalTrayAndPrefs(context: Context?) {
        val ctx = context?.applicationContext ?: appContext ?: return
        ensureInitialized(ctx)
        mainHandler.post {
            reconcileLocalSourcesOnMain(ctx, null)
        }
    }

    fun syncRemoteSourcesNow(context: Context?) {
        val ctx = context?.applicationContext ?: appContext ?: return
        ensureInitialized(ctx)
        if (!remoteSyncInFlight.compareAndSet(false, true)) return

        ioScope.launch {
            try {
                syncFirestoreNotifications(ctx)
                syncRtdbNotificationsIfAvailable(ctx)
            } catch (t: Throwable) {
                Log.w(TAG, "syncRemoteSourcesNow error", t)
            } finally {
                remoteSyncInFlight.set(false)
            }
        }
    }

    fun onRemoteConfigJsonReceived(context: Context?, entries: JSONObject?) {
        val ctx = context?.applicationContext ?: appContext ?: return
        if (entries == null) return
        ensureInitialized(ctx)

        ioScope.launch {
            try {
                processRemoteConfigEntries(ctx, entries)
            } catch (t: Throwable) {
                Log.w(TAG, "onRemoteConfigJsonReceived failed", t)
            }
        }
    }

    fun onRemoteConfigSdkReceived(context: Context?, frc: FirebaseRemoteConfig?) {
        val ctx = context?.applicationContext ?: appContext ?: return
        if (frc == null) return
        ensureInitialized(ctx)

        ioScope.launch {
            try {
                val keys = listOf(
                    "notifications",
                    "Notifications",
                    "notification_list",
                    "Notification_list",
                    "announcements",
                    "notification",
                    "Notification",
                    "latest_notification",
                    "notification_id",
                    "notification_title",
                    "notification_message",
                    "notification_body",
                    "notification_image",
                    "deleted_notification_ids",
                    "Deleted_notification_ids",
                    "deleted_notifications"
                )
                val obj = JSONObject()
                var hasAnyNotificationKey = false
                for (k in keys) {
                    val v = frc.getString(k).trim()
                    if (v.isNotEmpty()) {
                        obj.put(k, v)
                        hasAnyNotificationKey = true
                    }
                }
                if (hasAnyNotificationKey || hasPreviouslySyncedRemoteConfig(ctx)) {
                    processRemoteConfigEntries(ctx, obj)
                }
            } catch (t: Throwable) {
                Log.w(TAG, "onRemoteConfigSdkReceived failed", t)
            }
        }
    }

    private fun hasPreviouslySyncedRemoteConfig(ctx: Context): Boolean {
        val prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return (prefs.getStringSet(KEY_SYNCED_RC_IDS, emptySet())?.isNotEmpty() == true)
    }

    // ---------------------------------------------------------------------------------------------
    // Local Reconciliation (Prefs <-> Memory, StatusBar Tray, Activity Intent)
    // ---------------------------------------------------------------------------------------------

    private fun reconcileLocalSourcesOnMain(ctx: Context, activityIntent: Intent?) {
        try {
            val prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            var changedPrefs = false

            synchronized(prefsLock) {
                val currentRecords = readCachedJsonLocked(prefs).toMutableList()
                val remoteDeletedIds = prefs.getStringSet(KEY_REMOTE_DELETED_IDS, emptySet())
                    ?.toMutableSet() ?: HashSet()
                val remoteDeletedSigs = prefs.getStringSet(KEY_REMOTE_DELETED_SIGNATURES, emptySet())
                    ?.toMutableSet() ?: HashSet()

                // 1. Strip anything that was authoritatively deleted in Firebase
                if (remoteDeletedIds.isNotEmpty() || remoteDeletedSigs.isNotEmpty()) {
                    val before = currentRecords.size
                    currentRecords.removeAll { item ->
                        remoteDeletedIds.contains(item.id) ||
                            isDeletedBySignature(item, remoteDeletedSigs)
                    }
                    if (currentRecords.size != before) {
                        changedPrefs = true
                    }
                }

                // 2. Inspect incoming Activity intent extras (if push was tapped)
                if (activityIntent != null) {
                    val intentHandled = processIncomingIntentLocked(
                        ctx = ctx,
                        prefs = prefs,
                        intent = activityIntent,
                        records = currentRecords,
                        remoteDeletedIds = remoteDeletedIds,
                        remoteDeletedSigs = remoteDeletedSigs
                    )
                    if (intentHandled) {
                        changedPrefs = true
                    }
                }

                // 3. Harvest active StatusBarNotification tray items (handles background/killed pushes
                //    AND single-field / bigText / summary notifications that NotificationCenterHelper skips)
                val trayAdded = harvestStatusBarNotificationsLocked(
                    ctx = ctx,
                    prefs = prefs,
                    records = currentRecords,
                    remoteDeletedIds = remoteDeletedIds,
                    remoteDeletedSigs = remoteDeletedSigs
                )
                if (trayAdded) {
                    changedPrefs = true
                }

                // 4. Clean deduplication & sorting
                val normalized = deduplicateAndSort(currentRecords)
                if (changedPrefs || normalized.size != currentRecords.size) {
                    writeCachedJsonLocked(prefs, normalized)
                }

                // 5. Sync SnapshotStateLists inside NotificationCenterHelper on Main thread
                applyRecordsToComposeMemoryOnMain(ctx, prefs, normalized)
            }
        } catch (t: Throwable) {
            Log.w(TAG, "reconcileLocalSourcesOnMain error", t)
        }
    }

    private fun processIncomingIntentLocked(
        ctx: Context,
        prefs: SharedPreferences,
        intent: Intent,
        records: MutableList<NotifRecord>,
        remoteDeletedIds: MutableSet<String>,
        remoteDeletedSigs: MutableSet<String>
    ): Boolean {
        val extras = intent.extras ?: return false
        var modified = false

        // Explicit delete command via FCM intent
        val action = extrasString(extras, "action", "notif_action", "command")?.lowercase(Locale.US) ?: ""
        val deleteId = extrasString(extras, "delete_id", "deleted_id", "remove_id")
        val deletedIdsRaw = extrasString(extras, "deleted_ids", "deleted_notification_ids")
        val clearAll = extrasBoolean(extras, "clear_notifications", "clear_all_notifications")

        if (clearAll) {
            val idsToDelete = records.map { it.id }.toSet()
            if (idsToDelete.isNotEmpty()) {
                applyDeletionLocked(ctx, prefs, records, idsToDelete, emptySet())
                modified = true
            }
        } else if (action == "delete" || !deleteId.isNullOrEmpty() || !deletedIdsRaw.isNullOrEmpty()) {
            val targets = LinkedHashSet<String>()
            if (!deleteId.isNullOrEmpty()) targets.add(deleteId)
            val fallbackId = extrasString(extras, "id", "notification_id", "msg_id")
            if (action == "delete" && !fallbackId.isNullOrEmpty()) targets.add(fallbackId)
            if (!deletedIdsRaw.isNullOrEmpty()) {
                targets.addAll(parseIdList(deletedIdsRaw))
            }
            if (targets.isNotEmpty()) {
                applyDeletionLocked(ctx, prefs, records, targets, emptySet())
                modified = true
            }
            if (action == "delete") {
                return modified
            }
        }

        val rawTitle = extrasString(
            extras,
            "gcm.notification.title",
            "google.c.a.c_t",
            "notification_title",
            "title",
            "heading",
            "gcm.notification.title_loc_key"
        ) ?: ""
        val rawBody = extrasString(
            extras,
            "gcm.notification.body",
            "notification_body",
            "message",
            "body",
            "alert",
            "text",
            "description"
        ) ?: ""
        val rawImage = extrasString(
            extras,
            "gcm.notification.image",
            "imageUrl",
            "image_url",
            "image"
        ) ?: ""
        val msgId = extrasString(
            extras,
            "google.message_id",
            "gcm.message_id",
            "message_id",
            "notification_id",
            "id"
        ) ?: ""

        if (rawTitle.isBlank() && rawBody.isBlank()) {
            return modified
        }

        val sigKey = "${msgId}|${rawTitle.trim()}|${rawBody.trim()}"
        val processedSet = prefs.getStringSet(KEY_PROCESSED_INTENT_KEYS, emptySet())?.toMutableSet()
            ?: HashSet()
        if (processedSet.contains(sigKey)) {
            return modified
        }

        val title = rawTitle.trim().ifBlank { "Hypnotix" }
        val message = rawBody.trim()
        val id = msgId.takeIf { it.isNotBlank() }
            ?: "fcm_${abs((title + "|" + message).hashCode())}_${System.currentTimeMillis()}"

        if (remoteDeletedIds.contains(id) || remoteDeletedSigs.contains(contentSignature(title, message))) {
            return modified
        }

        val now = System.currentTimeMillis()
        if (!containsDuplicateRecord(records, id, title, message, now)) {
            records.add(
                0,
                NotifRecord(
                    id = id,
                    title = title,
                    message = message,
                    imageUrl = rawImage.trim(),
                    timestampMillis = now,
                    isRead = false
                )
            )
            modified = true
        }

        if (processedSet.size > 150) {
            processedSet.clear()
        }
        processedSet.add(sigKey)
        prefs.edit().putStringSet(KEY_PROCESSED_INTENT_KEYS, processedSet).apply()

        // Also let original helper inspect if it wants to open center
        try {
            helperInstance?.let { helper ->
                inspectIntentMethod?.invoke(helper, ctx, intent)
            }
        } catch (_: Throwable) {
        }

        return modified
    }

    private fun harvestStatusBarNotificationsLocked(
        ctx: Context,
        prefs: SharedPreferences,
        records: MutableList<NotifRecord>,
        remoteDeletedIds: Set<String>,
        remoteDeletedSigs: Set<String>
    ): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return false
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            ?: return false

        val active = try {
            nm.activeNotifications
        } catch (_: Throwable) {
            null
        } ?: return false

        val capturedKeys = prefs.getStringSet(KEY_CAPTURED_SBN_KEYS, emptySet())?.toMutableSet()
            ?: HashSet()
        var addedAny = false
        var capturedChanged = false

        for (sbn in active) {
            val notif = sbn.notification ?: continue
            if (shouldIgnoreNotification(sbn, notif)) continue

            val extras = notif.extras ?: Bundle.EMPTY
            val rawTitle = firstNonBlank(
                charSeqToString(extras.getCharSequence(Notification.EXTRA_TITLE)),
                charSeqToString(extras.getCharSequence(Notification.EXTRA_TITLE_BIG))
            )
            val linesText = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)
                ?.joinToString("\n") { it?.toString()?.trim().orEmpty() }
                ?.trim()
                .orEmpty()
            val rawMessage = firstNonBlank(
                charSeqToString(extras.getCharSequence(Notification.EXTRA_BIG_TEXT)),
                charSeqToString(extras.getCharSequence(Notification.EXTRA_TEXT)),
                linesText,
                charSeqToString(extras.getCharSequence(Notification.EXTRA_SUB_TEXT)),
                charSeqToString(extras.getCharSequence(Notification.EXTRA_SUMMARY_TEXT)),
                charSeqToString(extras.getCharSequence(Notification.EXTRA_INFO_TEXT)),
                charSeqToString(notif.tickerText)
            )

            if (rawTitle.isBlank() && rawMessage.isBlank()) continue

            val title = rawTitle.ifBlank { "Hypnotix" }
            val message = rawMessage
            val postTime = if (sbn.postTime > 0L) sbn.postTime else System.currentTimeMillis()
            val sbnKey = "${sbn.key}|$postTime"
            val sbnId = "sbn_${abs((title + "|" + message + "|" + postTime).hashCode())}"
            val sig = contentSignature(title, message)

            // If this notification was deleted from Firebase, cancel it from tray too!
            if (remoteDeletedIds.contains(sbnId) ||
                (!sbn.tag.isNullOrBlank() && remoteDeletedIds.contains(sbn.tag)) ||
                remoteDeletedSigs.contains(sig)
            ) {
                try {
                    nm.cancel(sbn.tag, sbn.id)
                } catch (_: Throwable) {
                }
                continue
            }

            // Don't re-add if already captured (respects manual user deletes inside the app!)
            if (capturedKeys.contains(sbnKey)) {
                // Only skip if we already captured this exact SBN instance before
                continue
            }

            val isDup = containsDuplicateRecord(records, sbnId, title, message, postTime)
            if (!isDup) {
                records.add(
                    0,
                    NotifRecord(
                        id = sbnId,
                        title = title,
                        message = message,
                        imageUrl = "",
                        timestampMillis = postTime,
                        isRead = false
                    )
                )
                addedAny = true
            }

            if (capturedKeys.size > 200) {
                capturedKeys.clear()
            }
            capturedKeys.add(sbnKey)
            capturedChanged = true
        }

        if (capturedChanged) {
            prefs.edit().putStringSet(KEY_CAPTURED_SBN_KEYS, capturedKeys).apply()
        }
        return addedAny
    }

    private fun shouldIgnoreNotification(sbn: StatusBarNotification, notif: Notification): Boolean {
        if ((notif.flags and Notification.FLAG_ONGOING_EVENT) != 0) return true
        if ((notif.flags and Notification.FLAG_FOREGROUND_SERVICE) != 0) return true
        if ((notif.flags and Notification.FLAG_GROUP_SUMMARY) != 0) return true

        val cat = notif.category ?: ""
        if (cat == Notification.CATEGORY_TRANSPORT ||
            cat == Notification.CATEGORY_SERVICE ||
            cat == Notification.CATEGORY_PROGRESS
        ) {
            return true
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = (notif.channelId ?: "").lowercase(Locale.US)
            if (ch.contains("media") || ch.contains("playback") || ch.contains("player") || ch.contains("exo")) {
                return true
            }
        }
        return false
    }

    // ---------------------------------------------------------------------------------------------
    // Remote Sync & Authoritative Delete Diffing (Firestore, Remote Config, RTDB)
    // ---------------------------------------------------------------------------------------------

    private fun syncFirestoreNotifications(ctx: Context) {
        var conn: HttpURLConnection? = null
        try {
            conn = (URL(FIRESTORE_URL).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 8000
                readTimeout = 8000
                setRequestProperty("Accept", "application/json")
            }
            val code = conn.responseCode
            if (code !in 200..299) {
                return
            }

            val body = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            val root = JSONObject(body)
            val docs = root.optJSONArray("documents")

            val activeList = ArrayList<NotifRecord>()
            val activeIds = LinkedHashSet<String>()
            val explicitDeletedIds = LinkedHashSet<String>()

            if (docs != null) {
                for (i in 0 until docs.length()) {
                    val doc = docs.optJSONObject(i) ?: continue
                    val name = doc.optString("name", "")
                    val docId = name.substringAfterLast("/").trim()
                    if (docId.isEmpty()) continue

                    val fields = doc.optJSONObject("fields") ?: continue
                    val isDeleted = fsBool(fields, "deleted", "isDeleted", "is_deleted", "archived")
                    val activeFlag = fsBoolNullable(fields, "active", "enabled")
                    if (isDeleted || activeFlag == false) {
                        explicitDeletedIds.add(docId)
                        continue
                    }

                    val title = fsString(fields, "title", "Title", "heading", "name", "subject")
                    val message = fsString(fields, "message", "Message", "body", "Body", "text", "description", "content")
                    if (title.isBlank() && message.isBlank()) continue

                    val imageUrl = fsString(fields, "imageUrl", "image_url", "image", "icon", "banner")
                    val ts = fsTimestamp(fields, doc, "timestampMillis", "createdAt", "timestamp", "time")

                    activeIds.add(docId)
                    activeList.add(
                        NotifRecord(
                            id = docId,
                            title = title.ifBlank { "Hypnotix" },
                            message = message,
                            imageUrl = imageUrl,
                            timestampMillis = ts,
                            isRead = false
                        )
                    )
                }
            }

            applyAuthoritativeSourceState(
                ctx = ctx,
                sourceTrackerPrefKey = KEY_SYNCED_FS_IDS,
                activeRecords = activeList,
                activeIds = activeIds,
                explicitDeletedIds = explicitDeletedIds,
                alsoSweepUntaggedFirestoreIds = true
            )
        } catch (t: Throwable) {
            Log.w(TAG, "syncFirestoreNotifications failed", t)
        } finally {
            try {
                conn?.disconnect()
            } catch (_: Throwable) {
            }
        }
    }

    private fun syncRtdbNotificationsIfAvailable(ctx: Context) {
        val now = System.currentTimeMillis()
        if (now < rtdbProbeCooldownUntilMs.get()) return

        var conn: HttpURLConnection? = null
        try {
            conn = (URL(RTDB_URL).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 6000
                readTimeout = 6000
                setRequestProperty("Accept", "application/json")
            }
            val code = conn.responseCode
            if (code == 404 || code == 401 || code == 403) {
                rtdbProbeCooldownUntilMs.set(now + 5L * 60L * 1000L)
                return
            }
            if (code !in 200..299) {
                return
            }

            val raw = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }.trim()
            val activeList = ArrayList<NotifRecord>()
            val activeIds = LinkedHashSet<String>()
            val explicitDeletedIds = LinkedHashSet<String>()

            if (raw.isNotEmpty() && raw != "null") {
                if (raw.startsWith("{")) {
                    val obj = JSONObject(raw)
                    val keys = obj.keys()
                    while (keys.hasNext()) {
                        val key = keys.next()
                        val itemObj = obj.optJSONObject(key) ?: continue
                        val id = "rtdb_$key"
                        if (itemObj.optBoolean("deleted", false) ||
                            itemObj.optBoolean("isDeleted", false) ||
                            (itemObj.has("active") && !itemObj.optBoolean("active", true))
                        ) {
                            explicitDeletedIds.add(id)
                            continue
                        }
                        val parsed = parseGenericJsonNotification(id, itemObj) ?: continue
                        activeIds.add(parsed.id)
                        activeList.add(parsed)
                    }
                } else if (raw.startsWith("[")) {
                    val arr = JSONArray(raw)
                    for (i in 0 until arr.length()) {
                        val itemObj = arr.optJSONObject(i) ?: continue
                        val rawId = itemObj.optString("id", "").trim().ifEmpty { i.toString() }
                        val id = if (rawId.startsWith("rtdb_")) rawId else "rtdb_$rawId"
                        if (itemObj.optBoolean("deleted", false) ||
                            itemObj.optBoolean("isDeleted", false)
                        ) {
                            explicitDeletedIds.add(id)
                            continue
                        }
                        val parsed = parseGenericJsonNotification(id, itemObj) ?: continue
                        activeIds.add(parsed.id)
                        activeList.add(parsed)
                    }
                }
            }

            applyAuthoritativeSourceState(
                ctx = ctx,
                sourceTrackerPrefKey = KEY_SYNCED_RTDB_IDS,
                activeRecords = activeList,
                activeIds = activeIds,
                explicitDeletedIds = explicitDeletedIds,
                alsoSweepUntaggedFirestoreIds = false
            )
        } catch (_: Throwable) {
            rtdbProbeCooldownUntilMs.set(now + 60_000L)
        } finally {
            try {
                conn?.disconnect()
            } catch (_: Throwable) {
            }
        }
    }

    private fun processRemoteConfigEntries(ctx: Context, entries: JSONObject) {
        val activeList = ArrayList<NotifRecord>()
        val activeIds = LinkedHashSet<String>()
        val explicitDeletedIds = LinkedHashSet<String>()

        // 1. Check explicit deletion keys in Remote Config
        for (delKey in listOf("deleted_notification_ids", "Deleted_notification_ids", "deleted_notifications")) {
            val rawDel = entries.optString(delKey, "").trim()
            if (rawDel.isNotEmpty()) {
                explicitDeletedIds.addAll(parseIdList(rawDel))
            }
        }

        // 2. Check list parameters ("notifications", "Notifications", "notification_list", "announcements")
        val listKeys = listOf("notifications", "Notifications", "notification_list", "Notification_list", "announcements")
        for (k in listKeys) {
            val raw = entries.optString(k, "").trim()
            if (raw.isEmpty() || raw == "[]" || raw == "null") continue

            try {
                if (raw.startsWith("[")) {
                    val arr = JSONArray(raw)
                    for (i in 0 until arr.length()) {
                        val el = arr.opt(i)
                        if (el is JSONObject) {
                            val rawId = el.optString("id", "").trim()
                            val title = el.optString("title", "").trim()
                            val msg = firstNonBlank(
                                el.optString("message", ""),
                                el.optString("body", ""),
                                el.optString("text", "")
                            )
                            val fallbackId = "rc_${k}_${abs((title + "|" + msg).hashCode())}"
                            val id = if (rawId.isNotEmpty()) {
                                if (rawId.startsWith("rc_")) rawId else "rc_$rawId"
                            } else {
                                fallbackId
                            }
                            if (el.optBoolean("deleted", false) || el.optBoolean("isDeleted", false)) {
                                explicitDeletedIds.add(id)
                                if (rawId.isNotEmpty()) explicitDeletedIds.add(rawId)
                                continue
                            }
                            val parsed = parseGenericJsonNotification(id, el) ?: continue
                            if (activeIds.add(parsed.id)) {
                                activeList.add(parsed)
                            }
                        } else if (el is String && el.trim().isNotEmpty()) {
                            val txt = el.trim()
                            val id = "rc_${k}_${abs(txt.hashCode())}"
                            if (activeIds.add(id)) {
                                activeList.add(
                                    NotifRecord(
                                        id = id,
                                        title = "Hypnotix",
                                        message = txt,
                                        imageUrl = "",
                                        timestampMillis = stableRcTimestamp(id),
                                        isRead = false
                                    )
                                )
                            }
                        }
                    }
                } else if (raw.startsWith("{")) {
                    val obj = JSONObject(raw)
                    val rawId = obj.optString("id", "").trim()
                    val id = if (rawId.isNotEmpty()) "rc_$rawId" else "rc_${k}_${abs(raw.hashCode())}"
                    if (obj.optBoolean("deleted", false) || obj.optBoolean("isDeleted", false)) {
                        explicitDeletedIds.add(id)
                    } else {
                        val parsed = parseGenericJsonNotification(id, obj)
                        if (parsed != null && activeIds.add(parsed.id)) {
                            activeList.add(parsed)
                        }
                    }
                }
            } catch (_: Throwable) {
            }
        }

        // 3. Check single notification JSON or plain string ("notification", "Notification", "latest_notification")
        for (k in listOf("notification", "Notification", "latest_notification")) {
            val raw = entries.optString(k, "").trim()
            if (raw.isEmpty() || raw == "{}" || raw == "null") continue

            if (raw.startsWith("{")) {
                try {
                    val obj = JSONObject(raw)
                    val rawId = obj.optString("id", "").trim()
                    val id = if (rawId.isNotEmpty()) "rc_$rawId" else "rc_${k}_${abs(raw.hashCode())}"
                    if (obj.optBoolean("deleted", false) || obj.optBoolean("isDeleted", false)) {
                        explicitDeletedIds.add(id)
                    } else {
                        val parsed = parseGenericJsonNotification(id, obj)
                        if (parsed != null && activeIds.add(parsed.id)) {
                            activeList.add(parsed)
                        }
                    }
                } catch (_: Throwable) {
                }
            } else {
                val id = "rc_${k}_${abs(raw.hashCode())}"
                if (activeIds.add(id)) {
                    activeList.add(
                        NotifRecord(
                            id = id,
                            title = "Hypnotix",
                            message = raw,
                            imageUrl = "",
                            timestampMillis = stableRcTimestamp(id),
                            isRead = false
                        )
                    )
                }
            }
        }

        // 4. Check paired Remote Config keys (notification_title / notification_message)
        val pairedTitle = firstNonBlank(
            entries.optString("notification_title", ""),
            entries.optString("Notification_title", "")
        )
        val pairedMsg = firstNonBlank(
            entries.optString("notification_message", ""),
            entries.optString("notification_body", ""),
            entries.optString("Notification_message", ""),
            entries.optString("Notification_body", "")
        )
        if (pairedTitle.isNotBlank() || pairedMsg.isNotBlank()) {
            val pairedIdRaw = entries.optString("notification_id", "").trim()
            val pairedImg = entries.optString("notification_image", "").trim()
            val id = if (pairedIdRaw.isNotEmpty()) {
                "rc_$pairedIdRaw"
            } else {
                "rc_paired_${abs((pairedTitle + "|" + pairedMsg).hashCode())}"
            }
            if (activeIds.add(id)) {
                activeList.add(
                    NotifRecord(
                        id = id,
                        title = pairedTitle.ifBlank { "Hypnotix" },
                        message = pairedMsg,
                        imageUrl = pairedImg,
                        timestampMillis = stableRcTimestamp(id),
                        isRead = false
                    )
                )
            }
        }

        applyAuthoritativeSourceState(
            ctx = ctx,
            sourceTrackerPrefKey = KEY_SYNCED_RC_IDS,
            activeRecords = activeList,
            activeIds = activeIds,
            explicitDeletedIds = explicitDeletedIds,
            alsoSweepUntaggedFirestoreIds = false
        )
    }

    private val rcFirstSeenTimes = java.util.concurrent.ConcurrentHashMap<String, Long>()

    private fun stableRcTimestamp(id: String): Long {
        return rcFirstSeenTimes.getOrPut(id) { System.currentTimeMillis() }
    }

    private fun applyAuthoritativeSourceState(
        ctx: Context,
        sourceTrackerPrefKey: String,
        activeRecords: List<NotifRecord>,
        activeIds: Set<String>,
        explicitDeletedIds: Set<String>,
        alsoSweepUntaggedFirestoreIds: Boolean
    ) {
        val finalRecordsSnapshot: List<NotifRecord>
        val needsMemoryUpdate: Boolean

        synchronized(prefsLock) {
            val prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val records = readCachedJsonLocked(prefs).toMutableList()
            val prevTrackedIds = prefs.getStringSet(sourceTrackerPrefKey, emptySet())
                ?.toSet() ?: emptySet()
            val remoteDeletedIds = prefs.getStringSet(KEY_REMOTE_DELETED_IDS, emptySet())
                ?.toMutableSet() ?: HashSet()
            val remoteDeletedSigs = prefs.getStringSet(KEY_REMOTE_DELETED_SIGNATURES, emptySet())
                ?.toMutableSet() ?: HashSet()
            val syncedSigsMap = readSyncedSigsLocked(prefs)

            val deletedNow = LinkedHashSet<String>()
            // 1) Any ID previously synced from this Firebase source that disappeared remotely
            for (oldId in prevTrackedIds) {
                if (!activeIds.contains(oldId)) {
                    deletedNow.add(oldId)
                }
            }
            // 2) Explicitly flagged deleted IDs
            deletedNow.addAll(explicitDeletedIds)

            // 3) If this is authoritative Firestore sync, also catch legacy docIds synced before bridge existed
            if (alsoSweepUntaggedFirestoreIds) {
                for (existing in records) {
                    if (isLikelyFirestoreLegacyId(existing.id) && !activeIds.contains(existing.id)) {
                        deletedNow.add(existing.id)
                    }
                }
            }

            var modified = false

            // Re-adding an ID in Firebase un-deletes it cleanly
            for (activeId in activeIds) {
                remoteDeletedIds.remove(activeId)
            }
            for (active in activeRecords) {
                val sig = contentSignature(active.title, active.message)
                remoteDeletedSigs.remove(sig)
                syncedSigsMap[active.id] = sig
            }

            // Collect signatures of notifications being deleted so matching FCM/SBN mirrors vanish too
            val sigsToDelete = LinkedHashSet<String>()
            for (delId in deletedNow) {
                remoteDeletedIds.add(delId)
                syncedSigsMap.remove(delId)?.let { sigsToDelete.add(it) }
                records.firstOrNull { it.id == delId }?.let {
                    sigsToDelete.add(contentSignature(it.title, it.message))
                }
            }
            remoteDeletedSigs.addAll(sigsToDelete)

            if (deletedNow.isNotEmpty() || sigsToDelete.isNotEmpty()) {
                val changedByDelete = applyDeletionLocked(
                    ctx = ctx,
                    prefs = prefs,
                    records = records,
                    idsToDelete = deletedNow,
                    sigsToDelete = sigsToDelete
                )
                if (changedByDelete) modified = true
            }

            // Upsert active items from Firebase
            for (active in activeRecords) {
                val existingIdx = records.indexOfFirst { it.id == active.id }
                if (existingIdx >= 0) {
                    val cur = records[existingIdx]
                    if (cur.title != active.title ||
                        cur.message != active.message ||
                        cur.imageUrl != active.imageUrl
                    ) {
                        records[existingIdx] = active.copy(
                            timestampMillis = maxOf(cur.timestampMillis, active.timestampMillis),
                            isRead = cur.isRead
                        )
                        modified = true
                    }
                } else {
                    // Collapse identical FCM/SBN entry into canonical cloud ID to prevent duplicates
                    val sig = contentSignature(active.title, active.message)
                    val dupIdx = records.indexOfFirst {
                        contentSignature(it.title, it.message) == sig &&
                            (it.id.startsWith("sbn_") || it.id.startsWith("fcm_")) &&
                            abs(it.timestampMillis - active.timestampMillis) <= CONTENT_DEDUP_WINDOW_MS
                    }
                    if (dupIdx >= 0) {
                        val old = records[dupIdx]
                        records[dupIdx] = active.copy(
                            timestampMillis = maxOf(old.timestampMillis, active.timestampMillis),
                            isRead = old.isRead
                        )
                    } else {
                        records.add(0, active)
                    }
                    modified = true
                }
            }

            // Cap remoteDeleted sets to avoid unbounded growth
            while (remoteDeletedIds.size > 200) {
                val first = remoteDeletedIds.firstOrNull() ?: break
                remoteDeletedIds.remove(first)
            }
            while (remoteDeletedSigs.size > 200) {
                val first = remoteDeletedSigs.firstOrNull() ?: break
                remoteDeletedSigs.remove(first)
            }

            val normalized = deduplicateAndSort(records)
            if (modified || normalized.size != records.size) {
                writeCachedJsonLocked(prefs, normalized)
            }

            prefs.edit()
                .putStringSet(sourceTrackerPrefKey, activeIds.toSet())
                .putStringSet(KEY_REMOTE_DELETED_IDS, remoteDeletedIds)
                .putStringSet(KEY_REMOTE_DELETED_SIGNATURES, remoteDeletedSigs)
                .putString(KEY_SYNCED_SIGNATURES, encodeSyncedSigs(syncedSigsMap))
                .apply()

            finalRecordsSnapshot = normalized
            needsMemoryUpdate = modified || deletedNow.isNotEmpty() || activeRecords.isNotEmpty()
        }

        if (needsMemoryUpdate) {
            mainHandler.post {
                val prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                applyRecordsToComposeMemoryOnMain(ctx, prefs, finalRecordsSnapshot)
            }
        }
    }

    private fun isLikelyFirestoreLegacyId(id: String): Boolean {
        if (id.isBlank()) return false
        if (id.startsWith("fcm_") ||
            id.startsWith("sbn_") ||
            id.startsWith("rc_") ||
            id.startsWith("rtdb_") ||
            id.startsWith("intent_") ||
            id.startsWith("welcome_") ||
            id.startsWith("local_") ||
            id.startsWith("0:")
        ) {
            return false
        }
        return true
    }

    private fun applyDeletionLocked(
        ctx: Context,
        prefs: SharedPreferences,
        records: MutableList<NotifRecord>,
        idsToDelete: Set<String>,
        sigsToDelete: Set<String>
    ): Boolean {
        if (idsToDelete.isEmpty() && sigsToDelete.isEmpty()) return false

        val removedIds = LinkedHashSet<String>(idsToDelete)
        val iterator = records.iterator()
        var removedAny = false

        while (iterator.hasNext()) {
            val item = iterator.next()
            val sig = contentSignature(item.title, item.message)
            val matchesId = idsToDelete.contains(item.id) ||
                (item.id.startsWith("fs_") && idsToDelete.contains(item.id.removePrefix("fs_")))
            val matchesSig = sigsToDelete.isNotEmpty() && sigsToDelete.contains(sig)
            if (matchesId || matchesSig) {
                removedIds.add(item.id)
                iterator.remove()
                removedAny = true
            }
        }

        // Remove from read_notification_ids, system_notified_ids, notified_ids
        val readIds = prefs.getStringSet(KEY_READ_IDS, emptySet())?.toMutableSet()
        val sysIds = prefs.getStringSet(KEY_SYSTEM_NOTIFIED_IDS, emptySet())?.toMutableSet()
        val notifiedIds = prefs.getStringSet(KEY_NOTIFIED_IDS, emptySet())?.toMutableSet()
        val editor = prefs.edit()
        var prefEdited = false

        if (readIds != null && readIds.removeAll(removedIds)) {
            editor.putStringSet(KEY_READ_IDS, readIds)
            prefEdited = true
        }
        if (sysIds != null && sysIds.removeAll(removedIds)) {
            editor.putStringSet(KEY_SYSTEM_NOTIFIED_IDS, sysIds)
            prefEdited = true
        }
        if (notifiedIds != null && notifiedIds.removeAll(removedIds)) {
            editor.putStringSet(KEY_NOTIFIED_IDS, notifiedIds)
            prefEdited = true
        }
        if (prefEdited) {
            editor.apply()
        }

        cancelSystemTrayNotifications(ctx, removedIds, sigsToDelete)
        return removedAny
    }

    private fun cancelSystemTrayNotifications(
        ctx: Context,
        ids: Set<String>,
        sigs: Set<String>
    ) {
        try {
            val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                ?: return
            for (id in ids) {
                try {
                    nm.cancel(id.hashCode())
                    nm.cancel(id, 0)
                } catch (_: Throwable) {
                }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && sigs.isNotEmpty()) {
                val active = try {
                    nm.activeNotifications
                } catch (_: Throwable) {
                    null
                }
                if (active != null) {
                    for (sbn in active) {
                        val notif = sbn.notification ?: continue
                        if (shouldIgnoreNotification(sbn, notif)) continue
                        val extras = notif.extras ?: Bundle.EMPTY
                        val t = firstNonBlank(
                            charSeqToString(extras.getCharSequence(Notification.EXTRA_TITLE)),
                            charSeqToString(extras.getCharSequence(Notification.EXTRA_TITLE_BIG))
                        ).ifBlank { "Hypnotix" }
                        val m = firstNonBlank(
                            charSeqToString(extras.getCharSequence(Notification.EXTRA_BIG_TEXT)),
                            charSeqToString(extras.getCharSequence(Notification.EXTRA_TEXT)),
                            charSeqToString(extras.getCharSequence(Notification.EXTRA_SUB_TEXT)),
                            charSeqToString(notif.tickerText)
                        )
                        if (sigs.contains(contentSignature(t, m))) {
                            try {
                                nm.cancel(sbn.tag, sbn.id)
                            } catch (_: Throwable) {
                            }
                        }
                    }
                }
            }
        } catch (_: Throwable) {
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Compose SnapshotStateList Sync (Main Thread ONLY, uses 5-arg AppNotificationItem ctor)
    // ---------------------------------------------------------------------------------------------

    @Suppress("UNCHECKED_CAST")
    private fun applyRecordsToComposeMemoryOnMain(
        ctx: Context,
        prefs: SharedPreferences,
        desired: List<NotifRecord>
    ) {
        resolveReflection()
        val helper = helperInstance ?: return
        val notifList = try {
            getNotificationsMethod?.invoke(helper) as? MutableList<Any>
        } catch (_: Throwable) {
            null
        } ?: return

        val readIdsList = try {
            getReadIdsMethod?.invoke(helper) as? MutableList<String>
        } catch (_: Throwable) {
            null
        }

        // Keep readIds synchronized with prefs + item.isRead flags
        val prefReadIds = prefs.getStringSet(KEY_READ_IDS, emptySet())?.toMutableSet() ?: HashSet()
        var addedReadPref = false
        for (item in desired) {
            if (item.isRead && prefReadIds.add(item.id)) {
                addedReadPref = true
            }
        }
        val validIds = desired.map { it.id }.toSet()
        val removedStaleRead = prefReadIds.retainAll(validIds)
        if (addedReadPref || removedStaleRead) {
            prefs.edit().putStringSet(KEY_READ_IDS, prefReadIds).apply()
        }

        if (readIdsList != null) {
            val currentReadSet = readIdsList.toSet()
            if (currentReadSet != prefReadIds) {
                readIdsList.clear()
                readIdsList.addAll(prefReadIds)
            }
        }

        // Diff SnapshotStateList<AppNotificationItem> to avoid needless recomposition
        if (isSameNotificationList(notifList, desired)) {
            return
        }

        val ctor = itemCtor5 ?: return
        val newItems = ArrayList<Any>(desired.size)
        for (rec in desired) {
            try {
                val instance = ctor.newInstance(
                    rec.id,
                    rec.title,
                    rec.message,
                    rec.imageUrl,
                    rec.timestampMillis
                )
                newItems.add(instance)
            } catch (t: Throwable) {
                Log.w(TAG, "Failed constructing AppNotificationItem for id=${rec.id}", t)
            }
        }

        notifList.clear()
        notifList.addAll(newItems)
    }

    private fun isSameNotificationList(existing: List<Any>, desired: List<NotifRecord>): Boolean {
        if (existing.size != desired.size) return false
        for (i in existing.indices) {
            val item = existing[i]
            val target = desired[i]
            val id = (try { itemGetIdMethod?.invoke(item) } catch (_: Throwable) { null }) as? String ?: ""
            val title = (try { itemGetTitleMethod?.invoke(item) } catch (_: Throwable) { null }) as? String ?: ""
            val msg = (try { itemGetMessageMethod?.invoke(item) } catch (_: Throwable) { null }) as? String ?: ""
            val img = (try { itemGetImageUrlMethod?.invoke(item) } catch (_: Throwable) { null }) as? String ?: ""
            val ts = (try { itemGetTimestampMethod?.invoke(item) } catch (_: Throwable) { null }) as? Long ?: 0L
            if (id != target.id ||
                title != target.title ||
                msg != target.message ||
                img != target.imageUrl ||
                ts != target.timestampMillis
            ) {
                return false
            }
        }
        return true
    }

    // ---------------------------------------------------------------------------------------------
    // JSON Serialization & Utility Helpers
    // ---------------------------------------------------------------------------------------------

    private fun readCachedJsonLocked(prefs: SharedPreferences): List<NotifRecord> {
        val raw = prefs.getString(KEY_CACHED_JSON, null)
        if (raw.isNullOrBlank()) return emptyList()

        val result = ArrayList<NotifRecord>()
        try {
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val id = obj.optString("id", "").trim()
                if (id.isEmpty() || id.startsWith("welcome_broadcast_")) continue

                val rawTitle = obj.optString("title", "").trim()
                val rawMessage = firstNonBlank(
                    obj.optString("message", ""),
                    obj.optString("body", ""),
                    obj.optString("text", "")
                )
                if (rawTitle.isBlank() && rawMessage.isBlank()) continue

                val title = rawTitle.ifBlank { "Hypnotix" }
                val imageUrl = firstNonBlank(obj.optString("imageUrl", ""), obj.optString("image", ""))
                val ts = obj.optLong("timestampMillis", System.currentTimeMillis())
                val isRead = obj.optBoolean("isRead", false)
                result.add(
                    NotifRecord(
                        id = id,
                        title = title,
                        message = rawMessage,
                        imageUrl = imageUrl,
                        timestampMillis = ts,
                        isRead = isRead
                    )
                )
            }
        } catch (t: Throwable) {
            Log.w(TAG, "readCachedJsonLocked error", t)
        }
        return result
    }

    private fun writeCachedJsonLocked(prefs: SharedPreferences, list: List<NotifRecord>) {
        val arr = JSONArray()
        for (item in list.take(MAX_NOTIFICATIONS)) {
            val obj = JSONObject().apply {
                put("id", item.id)
                put("title", item.title)
                put("message", item.message)
                put("imageUrl", item.imageUrl)
                put("timestampMillis", item.timestampMillis)
                if (item.isRead) put("isRead", true)
            }
            arr.put(obj)
        }
        isReconcilingWrite.set(true)
        try {
            prefs.edit().putString(KEY_CACHED_JSON, arr.toString()).commit()
        } finally {
            isReconcilingWrite.set(false)
        }
    }

    private fun deduplicateAndSort(input: List<NotifRecord>): List<NotifRecord> {
        val sorted = input.sortedByDescending { it.timestampMillis }
        val byId = LinkedHashMap<String, NotifRecord>()
        for (item in sorted) {
            if (!byId.containsKey(item.id)) {
                byId[item.id] = item
            }
        }

        // Also collapse near-simultaneous duplicate (title, message) between SBN and FCM/cloud records
        val final = ArrayList<NotifRecord>(byId.size)
        for (candidate in byId.values) {
            val sig = contentSignature(candidate.title, candidate.message)
            val existingIdx = final.indexOfFirst { existing ->
                contentSignature(existing.title, existing.message) == sig &&
                    abs(existing.timestampMillis - candidate.timestampMillis) <= CONTENT_DEDUP_WINDOW_MS
            }
            if (existingIdx < 0) {
                final.add(candidate)
            } else {
                val existing = final[existingIdx]
                // Prefer non-SBN canonical ID (Firestore / RC / FCM) over local sbn_ mirror
                if (existing.id.startsWith("sbn_") && !candidate.id.startsWith("sbn_")) {
                    final[existingIdx] = candidate.copy(
                        timestampMillis = maxOf(existing.timestampMillis, candidate.timestampMillis),
                        isRead = existing.isRead || candidate.isRead
                    )
                }
            }
        }
        return final.take(MAX_NOTIFICATIONS)
    }

    private fun containsDuplicateRecord(
        records: List<NotifRecord>,
        id: String,
        title: String,
        message: String,
        timestampMillis: Long
    ): Boolean {
        val sig = contentSignature(title, message)
        for (item in records) {
            if (item.id == id) return true
            if (contentSignature(item.title, item.message) == sig &&
                abs(item.timestampMillis - timestampMillis) <= CONTENT_DEDUP_WINDOW_MS
            ) {
                return true
            }
        }
        return false
    }

    private fun isDeletedBySignature(item: NotifRecord, deletedSigs: Set<String>): Boolean {
        if (deletedSigs.isEmpty()) return false
        return deletedSigs.contains(contentSignature(item.title, item.message))
    }

    private fun contentSignature(title: String, message: String): String {
        val t = title.trim().lowercase(Locale.US)
        val m = message.trim().lowercase(Locale.US)
        return "$t|$m"
    }

    private fun readSyncedSigsLocked(prefs: SharedPreferences): MutableMap<String, String> {
        val raw = prefs.getString(KEY_SYNCED_SIGNATURES, null) ?: return HashMap()
        val map = HashMap<String, String>()
        try {
            val obj = JSONObject(raw)
            val keys = obj.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                map[k] = obj.optString(k, "")
            }
        } catch (_: Throwable) {
        }
        return map
    }

    private fun encodeSyncedSigs(map: Map<String, String>): String {
        val obj = JSONObject()
        for ((k, v) in map) {
            obj.put(k, v)
        }
        return obj.toString()
    }

    private fun parseGenericJsonNotification(id: String, obj: JSONObject): NotifRecord? {
        val title = firstNonBlank(
            obj.optString("title", ""),
            obj.optString("Title", ""),
            obj.optString("heading", ""),
            obj.optString("name", "")
        )
        val message = firstNonBlank(
            obj.optString("message", ""),
            obj.optString("Message", ""),
            obj.optString("body", ""),
            obj.optString("Body", ""),
            obj.optString("text", ""),
            obj.optString("description", "")
        )
        if (title.isBlank() && message.isBlank()) return null

        val imageUrl = firstNonBlank(
            obj.optString("imageUrl", ""),
            obj.optString("image_url", ""),
            obj.optString("image", "")
        )
        val tsRaw = obj.optLong("timestampMillis", 0L)
            .takeIf { it > 0L }
            ?: obj.optLong("timestamp", 0L).takeIf { it > 0L }
            ?: stableRcTimestamp(id)
        return NotifRecord(
            id = id,
            title = title.ifBlank { "Hypnotix" },
            message = message,
            imageUrl = imageUrl,
            timestampMillis = tsRaw,
            isRead = false
        )
    }

    private fun parseIdList(raw: String): Set<String> {
        val trimmed = raw.trim()
        val out = LinkedHashSet<String>()
        if (trimmed.isEmpty()) return out
        if (trimmed.startsWith("[")) {
            try {
                val arr = JSONArray(trimmed)
                for (i in 0 until arr.length()) {
                    val s = arr.optString(i, "").trim()
                    if (s.isNotEmpty()) {
                        out.add(s)
                        if (!s.startsWith("rc_")) out.add("rc_$s")
                    }
                }
                return out
            } catch (_: Throwable) {
            }
        }
        for (part in trimmed.split(',', ';', '\n')) {
            val s = part.trim()
            if (s.isNotEmpty()) {
                out.add(s)
                if (!s.startsWith("rc_")) out.add("rc_$s")
            }
        }
        return out
    }

    private fun fsString(fields: JSONObject, vararg keys: String): String {
        for (k in keys) {
            val fieldObj = fields.optJSONObject(k) ?: continue
            val v = firstNonBlank(
                fieldObj.optString("stringValue", ""),
                fieldObj.optString("integerValue", ""),
                fieldObj.optString("doubleValue", "")
            )
            if (v.isNotEmpty()) return v
        }
        return ""
    }

    private fun fsBool(fields: JSONObject, vararg keys: String): Boolean {
        for (k in keys) {
            val fieldObj = fields.optJSONObject(k) ?: continue
            if (fieldObj.has("booleanValue")) {
                return fieldObj.optBoolean("booleanValue", false)
            }
            val str = fieldObj.optString("stringValue", "").trim().lowercase(Locale.US)
            if (str == "true" || str == "1" || str == "yes") return true
        }
        return false
    }

    private fun fsBoolNullable(fields: JSONObject, vararg keys: String): Boolean? {
        for (k in keys) {
            val fieldObj = fields.optJSONObject(k) ?: continue
            if (fieldObj.has("booleanValue")) {
                return fieldObj.optBoolean("booleanValue", true)
            }
            val str = fieldObj.optString("stringValue", "").trim().lowercase(Locale.US)
            if (str == "false" || str == "0" || str == "no") return false
            if (str == "true" || str == "1" || str == "yes") return true
        }
        return null
    }

    private fun fsTimestamp(fields: JSONObject, doc: JSONObject, vararg keys: String): Long {
        for (k in keys) {
            val fieldObj = fields.optJSONObject(k) ?: continue
            val iso = fieldObj.optString("timestampValue", "").trim()
            if (iso.isNotEmpty()) {
                val parsed = parseIso8601(iso)
                if (parsed > 0L) return parsed
            }
            val intStr = fieldObj.optString("integerValue", "").trim()
            intStr.toLongOrNull()?.takeIf { it > 0L }?.let { return it }
        }
        val createTime = doc.optString("createTime", "").trim()
        if (createTime.isNotEmpty()) {
            val parsed = parseIso8601(createTime)
            if (parsed > 0L) return parsed
        }
        val updateTime = doc.optString("updateTime", "").trim()
        if (updateTime.isNotEmpty()) {
            val parsed = parseIso8601(updateTime)
            if (parsed > 0L) return parsed
        }
        return System.currentTimeMillis()
    }

    private fun parseIso8601(raw: String): Long {
        val cleaned = raw.trim()
        if (cleaned.isEmpty()) return 0L
        val patterns = listOf(
            "yyyy-MM-dd'T'HH:mm:ss.SSSSSS'Z'",
            "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
            "yyyy-MM-dd'T'HH:mm:ss'Z'"
        )
        // Normalize variable fractional seconds (e.g. .123456789Z -> .123Z)
        val dotIdx = cleaned.indexOf('.')
        val zIdx = cleaned.indexOf('Z')
        val normalized = if (dotIdx > 0 && zIdx > dotIdx) {
            val frac = cleaned.substring(dotIdx + 1, zIdx).padEnd(3, '0').take(3)
            cleaned.substring(0, dotIdx + 1) + frac + "Z"
        } else {
            cleaned
        }
        for (pattern in patterns) {
            try {
                val sdf = SimpleDateFormat(pattern, Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                }
                val date = sdf.parse(normalized)
                if (date != null) return date.time
            } catch (_: Throwable) {
            }
        }
        return 0L
    }

    private fun extrasString(bundle: Bundle, vararg keys: String): String? {
        for (k in keys) {
            val v = try {
                bundle.getString(k) ?: bundle.getCharSequence(k)?.toString()
            } catch (_: Throwable) {
                null
            }
            if (!v.isNullOrBlank()) return v.trim()
        }
        return null
    }

    private fun extrasBoolean(bundle: Bundle, vararg keys: String): Boolean {
        for (k in keys) {
            if (!bundle.containsKey(k)) continue
            val v = try {
                val raw = bundle.get(k)
                when (raw) {
                    is Boolean -> raw
                    is String -> raw.trim().equals("true", ignoreCase = true) || raw.trim() == "1"
                    is Number -> raw.toInt() == 1
                    else -> false
                }
            } catch (_: Throwable) {
                false
            }
            if (v) return true
        }
        return false
    }

    private fun charSeqToString(cs: CharSequence?): String {
        return cs?.toString()?.trim().orEmpty()
    }

    private fun firstNonBlank(vararg values: String?): String {
        for (v in values) {
            val trimmed = v?.trim()
            if (!trimmed.isNullOrEmpty()) return trimmed
        }
        return ""
    }
}
