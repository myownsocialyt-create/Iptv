package com.example.premium

import android.util.Log
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.flow.StateFlow

/**
 * Manages Xtream Demo Account UX and lightweight streamed demo videos:
 * 1. Replaces the "Connect Demo" card with an "Add Account" card once the Demo Account is already connected.
 * 2. Provides 15-second streamed demo videos (aerial/flight & animated movie intro) with zero APK size increase.
 */
object XtreamDemoHook {
    private const val TAG = "XtreamDemoHook"
    private const val DEMO_HOST_TOKEN = "demo.xtream-server.tv"

    private val fieldCache = ConcurrentHashMap<String, Field>()
    private val methodCache = ConcurrentHashMap<String, Method>()

    @Volatile
    private var pendingBottomCardTarget: Any? = null

    @Volatile
    private var lastOnAddProfileClick: Function0<Any?>? = null

    data class DemoChannelSpec(
        val id: String,
        val name: String,
        val logoUrl: String,
        val category: String,
        val language: String,
        val country: String,
        val streamUrl: String,
        val playlistName: String,
        val isMovieOrVod: Boolean
    )

    // Lightweight streamed public sample videos (0 bytes bundled in APK, verified HTTP 200)
    fun getDemoLiveSpecs(): List<DemoChannelSpec> = listOf(
        DemoChannelSpec(
            id = "demo_live_skyline_intro",
            name = "Skyline Flight Intro (Demo)",
            logoUrl = "https://assets.mixkit.co/videos/44498/44498-thumb-720-0.jpg",
            category = "Demo Showcase",
            language = "English",
            country = "Global",
            streamUrl = "https://assets.mixkit.co/videos/44498/44498-720.mp4",
            playlistName = "Demo TV",
            isMovieOrVod = false
        )
    )

    fun getDemoVodSpecs(): List<DemoChannelSpec> = listOf(
        DemoChannelSpec(
            id = "demo_vod_movie_intro",
            name = "Cinematic Animation Intro (Demo)",
            logoUrl = "https://media.w3.org/2010/05/bunny/poster.png",
            category = "Demo Intro",
            language = "English",
            country = "Global",
            streamUrl = "https://www.w3schools.com/html/mov_bbb.mp4",
            playlistName = "Demo TV",
            isMovieOrVod = true
        )
    )

    fun isDemoXtreamAccount(account: Any?): Boolean {
        if (account == null) return false
        val server = (invokeNoArg(account, "getServerUrl") as? String)?.trim()?.lowercase(Locale.ROOT).orEmpty()
        val username = (invokeNoArg(account, "getUsername") as? String)?.trim()?.lowercase(Locale.ROOT).orEmpty()
        val password = (invokeNoArg(account, "getPassword") as? String)?.trim()?.lowercase(Locale.ROOT).orEmpty()
        return server.contains(DEMO_HOST_TOKEN) || (username == "demo" && password == "demo")
    }

    fun isDemoChannel(channel: Any?, activeAccount: Any?): Boolean {
        if (channel == null) return false
        val id = (invokeNoArg(channel, "getId") as? String)?.trim()?.lowercase(Locale.ROOT).orEmpty()
        if (id.startsWith("demo_live_") || id.startsWith("demo_vod_")) {
            return true
        }
        if (isDemoXtreamAccount(activeAccount)) {
            val playlistName = (invokeNoArg(channel, "getPlaylistName") as? String)?.trim().orEmpty()
            if (playlistName.equals("Demo TV", ignoreCase = true)) {
                return true
            }
        }
        return false
    }

    fun hasConnectedDemoAccount(accounts: Collection<*>?): Boolean {
        if (accounts.isNullOrEmpty()) return false
        return accounts.any { isDemoXtreamAccount(it) }
    }

    fun isDemoAccountConnectedNow(): Boolean {
        return hasConnectedDemoAccount(readCurrentXtreamAccountsSnapshot())
    }

    @Suppress("UNCHECKED_CAST")
    fun captureAddProfileCallback(target: Any?) {
        if (target == null) return
        val cb = getField(target, "\$onAddProfileClick") as? Function0<Any?>
        if (cb != null) {
            lastOnAddProfileClick = cb
        }
    }

    fun setPendingBottomCardTarget(target: Any) {
        pendingBottomCardTarget = target
    }

    @Composable
    fun tryRenderPendingBottomCard(): Boolean {
        val target = pendingBottomCardTarget ?: return false
        pendingBottomCardTarget = null
        RenderWhosWatchingBottomCard(target)
        return true
    }

    @Suppress("UNCHECKED_CAST")
    @Composable
    private fun RenderWhosWatchingBottomCard(target: Any) {
        val onConnectDemo = getField(target, "\$onConnectSampleTestServer") as? Function0<Any?>
        val appColors = getField(target, "\$appColors")

        val isDark = (invokeNoArg(appColors, "isDark") as? Boolean) ?: isSystemInDarkTheme()
        val ottPrimary = Color(0xFFE50914)
        val textPrimary = readColorField(appColors, "getTextPrimary-0d7_KjU")
            ?: if (isDark) Color(0xFFFFFFFF) else Color(0xFF0F172A)
        val textSecondary = readColorField(appColors, "getTextSecondary-0d7_KjU")
            ?: if (isDark) Color(0xFFA1A1AA) else Color(0xFF475569)

        val liveAccounts = observeXtreamAccounts()
        val demoConnected = hasConnectedDemoAccount(liveAccounts)
        val onAddAccount = lastOnAddProfileClick

        val showAddAccountMode = demoConnected

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp)
                .testTag(if (showAddAccountMode) "xtream_add_account_card" else "xtream_demo_connect_card"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(ottPrimary.copy(alpha = if (isDark) 0.18f else 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (showAddAccountMode) Icons.Rounded.PersonAdd else Icons.Rounded.Bolt,
                    contentDescription = if (showAddAccountMode) "Add Account" else "Demo Account",
                    tint = ottPrimary,
                    modifier = Modifier.size(22.dp)
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = if (showAddAccountMode) "Add Your Xtream Account" else "Need a test server?",
                    color = textPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = if (showAddAccountMode) {
                        "Demo connected • Add your own Xtream server"
                    } else {
                        "One-tap connect to verified sample Xtream streams"
                    },
                    color = textSecondary,
                    fontSize = 11.5.sp,
                    lineHeight = 15.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Button(
                onClick = {
                    if (showAddAccountMode) {
                        runCatching { onAddAccount?.invoke() }
                    } else {
                        runCatching { onConnectDemo?.invoke() }
                    }
                },
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = ottPrimary,
                    contentColor = Color.White
                ),
                border = BorderStroke(0.dp, Color.Transparent),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                modifier = Modifier
                    .height(36.dp)
                    .testTag(if (showAddAccountMode) "xtream_add_account_action_btn" else "xtream_connect_demo_btn")
            ) {
                if (showAddAccountMode) {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Add Account",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false
                    )
                } else {
                    Text(
                        text = "Connect",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    @Composable
    private fun observeXtreamAccounts(): List<Any> {
        val stateFromMain = readMainAppXtreamAccountsState()
        if (stateFromMain != null) {
            return (stateFromMain.value as? List<*>)?.filterNotNull().orEmpty()
        }
        val flowFromVm = readViewModelXtreamAccountsFlow()
        if (flowFromVm != null) {
            val collected = flowFromVm.collectAsState()
            return (collected.value as? List<*>)?.filterNotNull().orEmpty()
        }
        return emptyList()
    }

    private fun readCurrentXtreamAccountsSnapshot(): List<Any> {
        val stateFromMain = readMainAppXtreamAccountsState()
        if (stateFromMain != null) {
            return (stateFromMain.value as? List<*>)?.filterNotNull().orEmpty()
        }
        val flowFromVm = readViewModelXtreamAccountsFlow()
        if (flowFromVm != null) {
            return (flowFromVm.value as? List<*>)?.filterNotNull().orEmpty()
        }
        return emptyList()
    }

    private fun readMainAppXtreamAccountsState(): State<*>? {
        return try {
            val cls = Class.forName("com.example.ui.screens.MainAppScreenKt")
            val field = cls.getDeclaredField("sXtreamAccountsState").apply { isAccessible = true }
            field.get(null) as? State<*>
        } catch (_: Throwable) {
            null
        }
    }

    private fun readViewModelXtreamAccountsFlow(): StateFlow<*>? {
        val vm = discoverViewModelSafe() ?: return null
        return try {
            val field = vm.javaClass.getDeclaredField("xtreamAccounts").apply { isAccessible = true }
            field.get(vm) as? StateFlow<*>
        } catch (_: Throwable) {
            null
        }
    }

    private fun discoverViewModelSafe(): Any? {
        return try {
            val field = PremiumUiHooks::class.java.getDeclaredField("lastKnownViewModelRef")
                .apply { isAccessible = true }
            val ref = field.get(PremiumUiHooks) as? java.lang.ref.WeakReference<*>
            ref?.get()
        } catch (_: Throwable) {
            null
        }
    }

    private fun readColorField(obj: Any?, methodName: String): Color? {
        val raw = invokeNoArg(obj, methodName) as? Long ?: return null
        return try {
            Color(raw.toULong())
        } catch (_: Throwable) {
            null
        }
    }

    private fun getField(target: Any?, name: String): Any? {
        if (target == null) return null
        val cls = target.javaClass
        val key = "${cls.name}#$name"
        return try {
            val field = fieldCache.getOrPut(key) {
                var c: Class<*>? = cls
                var found: Field? = null
                while (c != null && found == null) {
                    try {
                        found = c.getDeclaredField(name)
                    } catch (_: NoSuchFieldException) {
                        c = c.superclass
                    }
                }
                (found ?: throw NoSuchFieldException(key)).apply { isAccessible = true }
            }
            field.get(target)
        } catch (t: Throwable) {
            Log.w(TAG, "getField failed for $key", t)
            null
        }
    }

    private fun invokeNoArg(target: Any?, name: String): Any? {
        if (target == null) return null
        val cls = target.javaClass
        val key = "${cls.name}#$name"
        return try {
            val method = methodCache.getOrPut(key) {
                cls.getMethod(name).apply { isAccessible = true }
            }
            method.invoke(target)
        } catch (_: Throwable) {
            null
        }
    }
}
