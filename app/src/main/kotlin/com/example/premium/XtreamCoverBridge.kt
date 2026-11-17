package com.example.premium

import android.util.Log
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.util.concurrent.ConcurrentHashMap

object XtreamCoverBridge {
    private const val TAG = "XtreamCoverBridge"

    private val knownXtreamChannelIds: MutableSet<String> = ConcurrentHashMap.newKeySet()

    @Volatile
    private var cachedPosterResolverInstance: Any? = null

    @Volatile
    private var cachedGetPosterUrlMethod: Method? = null

    @Volatile
    private var cachedLogoUrlField: Field? = null

    private fun safeGetString(target: Any, getterName: String): String {
        return try {
            (target.javaClass.getMethod(getterName).invoke(target) as? String)?.trim().orEmpty()
        } catch (_: Throwable) {
            ""
        }
    }

    private fun safeGetBoolean(target: Any, getterName: String): Boolean {
        return try {
            (target.javaClass.getMethod(getterName).invoke(target) as? Boolean) == true
        } catch (_: Throwable) {
            false
        }
    }

    private fun invokePosterResolver(channel: Any): String? {
        return try {
            var instance = cachedPosterResolverInstance
            var method = cachedGetPosterUrlMethod
            if (instance == null || method == null) {
                val cls = Class.forName("com.example.ui.screens.XtreamPosterResolver")
                instance = cls.getField("INSTANCE").get(null)
                method = cls.getMethod("getPosterUrl", channel.javaClass).apply {
                    isAccessible = true
                }
                cachedPosterResolverInstance = instance
                cachedGetPosterUrlMethod = method
            }
            (method?.invoke(instance, channel) as? String)?.trim()?.takeIf { it.isNotEmpty() }
        } catch (t: Throwable) {
            Log.w(TAG, "invokePosterResolver failed", t)
            null
        }
    }

    private fun setChannelLogoUrlReflective(channel: Any, newLogoUrl: String?) {
        try {
            var field = cachedLogoUrlField
            if (field == null) {
                field = channel.javaClass.getDeclaredField("logoUrl").apply {
                    isAccessible = true
                }
                cachedLogoUrlField = field
            }
            field?.set(channel, newLogoUrl)
        } catch (t: Throwable) {
            Log.w(TAG, "setChannelLogoUrlReflective failed", t)
        }
    }

    @JvmStatic
    fun ensureXtreamChannelCoverUrl(channel: Any) {
        try {
            val id = safeGetString(channel, "getId")
            if (id.isNotEmpty()) {
                knownXtreamChannelIds.add(id)
            }
            val posterUrl = invokePosterResolver(channel) ?: return
            val currentLogo = safeGetString(channel, "getLogoUrl")
            if (currentLogo.isEmpty() || currentLogo.contains("135.125.132.93")) {
                setChannelLogoUrlReflective(channel, posterUrl)
            }
        } catch (t: Throwable) {
            Log.w(TAG, "ensureXtreamChannelCoverUrl failed", t)
        }
    }

    private fun isXtreamChannel(channel: Any): Boolean {
        val id = safeGetString(channel, "getId")
        if (id.startsWith("xtream_") || id.startsWith("vod_") || knownXtreamChannelIds.contains(id)) {
            return true
        }
        val playlistName = safeGetString(channel, "getPlaylistName")
        if (playlistName.startsWith("Xtream", ignoreCase = true)) {
            return true
        }
        return safeGetBoolean(channel, "isMovieOrVod")
    }

    @JvmStatic
    fun resolveCoverForChannelLogo(channel: Any, currentResolved: String?): String? {
        if (!currentResolved.isNullOrBlank()) {
            return currentResolved
        }
        if (!isXtreamChannel(channel)) {
            return null
        }
        val currentLogo = safeGetString(channel, "getLogoUrl")
        val posterUrl = invokePosterResolver(channel)
        if (!posterUrl.isNullOrBlank() && posterUrl != currentLogo) {
            return posterUrl
        }
        // If currentLogo was non-empty (meaning primaryUrl already tried currentLogo and this call
        // is for secondaryFallbackUrl), compute the fallback cover URL with logoUrl temporarily cleared.
        if (currentLogo.isNotEmpty()) {
            return try {
                setChannelLogoUrlReflective(channel, null)
                val fallbackCover = invokePosterResolver(channel)
                setChannelLogoUrlReflective(channel, currentLogo)
                fallbackCover
            } catch (_: Throwable) {
                setChannelLogoUrlReflective(channel, currentLogo)
                posterUrl
            }
        }
        return posterUrl
    }
}
