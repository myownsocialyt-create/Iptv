package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.CustomPlaylistEntity
import com.example.data.model.Channel
import com.example.data.repository.PlaylistSource
import com.example.data.repository.PresetPlaylists
import com.example.player.IptvPlayerManager
import com.example.ui.theme.AppColors

/**
 * Top-level function using simple for loops (no inline lambdas) to filter
 * custom playlists that are not Xtream API accounts, preventing bytecode
 * verifier register conflicts.
 */
fun filterNonXtreamCustomPlaylists(customPlaylists: List<CustomPlaylistEntity>): List<CustomPlaylistEntity> {
    val result = ArrayList<CustomPlaylistEntity>()
    val size = customPlaylists.size
    for (i in 0 until size) {
        val cp = customPlaylists[i]
        val url = cp.url
        val title = cp.title
        var isXtream = false

        if (url != null && url.contains("player_api.php")) {
            isXtream = true
        }
        if (!isXtream && title != null) {
            val lower = title.lowercase()
            if (lower.contains("xtream")) {
                isXtream = true
            }
        }
        if (!isXtream) {
            result.add(cp)
        }
    }
    return result
}

/**
 * Modular sub-composable for Preset Playlists dropdown section.
 */
@Composable
fun PresetPlaylistsDropdownSection(
    currentPlaylist: PlaylistSource,
    deletedPresetIds: Set<String>,
    appColors: AppColors,
    onSelectPlaylist: (PlaylistSource) -> Unit,
    onDismiss: () -> Unit
) {
    val showIndia = !deletedPresetIds.contains(PresetPlaylists.INDIA.id)
    val showGlobal = !deletedPresetIds.contains(PresetPlaylists.GLOBAL.id)
    if (!showIndia && !showGlobal) return

    Text(
        text = "PRESET PLAYLISTS",
        fontSize = 10.sp,
        fontWeight = FontWeight.ExtraBold,
        color = Color(0xFF6C63FF),
        letterSpacing = 1.sp,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
    )

    if (showIndia) {
        val isIndiaSelected = currentPlaylist.id == PresetPlaylists.INDIA.id
        DropdownMenuItem(
            text = {
                Text(
                    text = "India (Free)",
                    fontWeight = if (isIndiaSelected) FontWeight.Bold else FontWeight.Normal,
                    color = if (isIndiaSelected) appColors.textPrimary else appColors.textSecondary
                )
            },
            onClick = {
                onSelectPlaylist(PresetPlaylists.INDIA)
                onDismiss()
            },
            leadingIcon = {
                Text(if (isIndiaSelected) "🇮🇳 ✓" else "🇮🇳")
            }
        )
    }

    if (showGlobal) {
        val isGlobalSelected = currentPlaylist.id == PresetPlaylists.GLOBAL.id
        DropdownMenuItem(
            text = {
                Text(
                    text = "Global (Free)",
                    fontWeight = if (isGlobalSelected) FontWeight.Bold else FontWeight.Normal,
                    color = if (isGlobalSelected) appColors.textPrimary else appColors.textSecondary
                )
            },
            onClick = {
                onSelectPlaylist(PresetPlaylists.GLOBAL)
                onDismiss()
            },
            leadingIcon = {
                Text(if (isGlobalSelected) "🌍 ✓" else "🌍")
            }
        )
    }
}

/**
 * Modular sub-composable for Added / Custom Playlists dropdown section.
 */
@Composable
fun AddedPlaylistsDropdownSection(
    filteredPlaylists: List<CustomPlaylistEntity>,
    currentPlaylist: PlaylistSource,
    appColors: AppColors,
    onSelectPlaylist: (PlaylistSource) -> Unit,
    onDismiss: () -> Unit
) {
    if (filteredPlaylists.isEmpty()) return

    HorizontalDivider(
        color = appColors.border,
        modifier = Modifier.padding(vertical = 4.dp)
    )
    Text(
        text = "ADDED PLAYLISTS (${filteredPlaylists.size})",
        fontSize = 10.sp,
        fontWeight = FontWeight.ExtraBold,
        color = Color(0xFF6C63FF),
        letterSpacing = 1.sp,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
    )

    val size = filteredPlaylists.size
    for (i in 0 until size) {
        val cp = filteredPlaylists[i]
        val isSelected = currentPlaylist.id == "custom_${cp.id}" || currentPlaylist.url == cp.url
        val iconPrefix = if (cp.url.startsWith("file://")) {
            "📄"
        } else if (cp.url.startsWith("xtream_account://")) {
            "⚡"
        } else {
            "📁"
        }
        DropdownMenuItem(
            text = {
                Text(
                    text = cp.title,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    color = if (isSelected) appColors.textPrimary else appColors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            },
            onClick = {
                onSelectPlaylist(
                    PlaylistSource(
                        id = "custom_${cp.id}",
                        name = cp.title,
                        url = cp.url,
                        isPreset = false
                    )
                )
                onDismiss()
            },
            leadingIcon = {
                Text(if (isSelected) "$iconPrefix ✓" else iconPrefix)
            }
        )
    }
}

/**
 * Modular Split Screen Dropdown Menu Content that avoids giant single-method inlining.
 */
@Composable
fun SplitScreenPlaylistMenuContent(
    currentPlaylist: PlaylistSource,
    customPlaylists: List<CustomPlaylistEntity>,
    deletedPresetIds: Set<String>,
    appColors: AppColors,
    onSelectPlaylist: (PlaylistSource) -> Unit,
    onDismiss: () -> Unit
) {
    // 1. Filter using plain top-level function once with remember(customPlaylists)
    val nonXtreamPlaylists = remember(customPlaylists) {
        filterNonXtreamCustomPlaylists(customPlaylists)
    }

    val isXtreamAccountActive = currentPlaylist.url.startsWith("xtream_account://")
    val filteredPlaylists = if (isXtreamAccountActive) {
        customPlaylists
    } else {
        nonXtreamPlaylists
    }

    // Header label
    Text(
        text = if (isXtreamAccountActive) "SWITCH XTREAM ACCOUNT" else "SWITCH PLAYLIST",
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        color = appColors.textMuted,
        letterSpacing = 1.sp,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
    )

    // 2. Separate preset section
    if (!isXtreamAccountActive) {
        PresetPlaylistsDropdownSection(
            currentPlaylist = currentPlaylist,
            deletedPresetIds = deletedPresetIds,
            appColors = appColors,
            onSelectPlaylist = onSelectPlaylist,
            onDismiss = onDismiss
        )
    }

    // 3. Separate added playlists section
    AddedPlaylistsDropdownSection(
        filteredPlaylists = filteredPlaylists,
        currentPlaylist = currentPlaylist,
        appColors = appColors,
        onSelectPlaylist = onSelectPlaylist,
        onDismiss = onDismiss
    )
}
