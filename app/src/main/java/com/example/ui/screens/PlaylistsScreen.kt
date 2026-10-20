package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Notes
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.CustomPlaylistEntity
import com.example.data.repository.PlaylistSource
import com.example.data.repository.PresetPlaylists
import com.example.ui.i18n.LocalAppStrings
import com.example.ui.theme.LocalAppColors
import com.example.ui.theme.OttPrimary

@Composable
fun PlaylistsScreen(
    currentPlaylist: PlaylistSource,
    customPlaylists: List<CustomPlaylistEntity>,
    searchQuery: String = "",
    onSearchQueryChange: (String) -> Unit = {},
    deletedPresetIds: Set<String> = emptySet(),
    onDeletePresetPlaylist: (String) -> Unit = {},
    hasSelectedFirstPlaylist: Boolean = true,
    onSelectPlaylist: (PlaylistSource) -> Unit,
    onDeleteCustomPlaylist: (Long) -> Unit,
    onNavigateToAddPlaylist: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val appColors = LocalAppColors.current
    val strings = LocalAppStrings.current
    val isDark = appColors.isDark

    val allPresets = remember(deletedPresetIds) {
        listOf(
            PresetPlaylists.INDIA,
            PresetPlaylists.GLOBAL,
            PresetPlaylists.ALL
        ).filter { !deletedPresetIds.contains(it.id) }
    }

    val trimmedQuery = searchQuery.trim().lowercase()

    // Filter presets and custom playlists based on search query
    val filteredCustom = remember(customPlaylists, trimmedQuery) {
        if (trimmedQuery.isEmpty()) customPlaylists
        else customPlaylists.filter { it.title.lowercase().contains(trimmedQuery) || it.url.lowercase().contains(trimmedQuery) }
    }

    val filteredPresets = remember(allPresets, trimmedQuery) {
        if (trimmedQuery.isEmpty()) allPresets
        else allPresets.filter { it.title.lowercase().contains(trimmedQuery) }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(appColors.background),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Search Bar: Searches playlist by name
        item(key = "playlist_search_bar") {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = appColors.surface,
                border = BorderStroke(1.dp, appColors.border),
                shadowElevation = 0.5.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("playlist_search_bar")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search Playlists",
                        tint = appColors.textMuted,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    BasicTextField(
                        value = searchQuery,
                        onValueChange = onSearchQueryChange,
                        singleLine = true,
                        textStyle = TextStyle(
                            color = appColors.textPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("playlist_search_input"),
                        decorationBox = { innerTextField ->
                            if (searchQuery.isEmpty()) {
                                Text(
                                    text = strings.searchPlaylistsHint,
                                    color = appColors.textMuted,
                                    fontSize = 14.sp
                                )
                            }
                            innerTextField()
                        }
                    )
                    if (searchQuery.isNotEmpty()) {
                        IconButton(
                            onClick = { onSearchQueryChange("") },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Clear search",
                                tint = appColors.textMuted,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }

        // 2. Manage Playlists Hero Dark Navy Card (Screenshot 1)
        item(key = "manage_playlists_hero") {
            Card(
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = if (isDark) Color(0xFF111111) else Color(0xFF0F172A)),
                border = BorderStroke(1.dp, if (isDark) Color(0xFF262626) else Color.Transparent),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("manage_playlists_hero_card")
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(
                        text = strings.managePlaylists,
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = strings.managePlaylistsSubtitle,
                        color = Color(0xFF94A3B8),
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Buttons Row: [ Import Link ] [ Upload M3U ]
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Import Link Red Button
                        Button(
                            onClick = { onNavigateToAddPlaylist(0) },
                            colors = ButtonDefaults.buttonColors(containerColor = OttPrimary),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                                .testTag("import_link_button"),
                            contentPadding = PaddingValues(horizontal = 12.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Link,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(17.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = strings.importLink,
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        // Upload M3U Green Button
                        Button(
                            onClick = { onNavigateToAddPlaylist(1) },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF059669)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                                .testTag("upload_m3u_button"),
                            contentPadding = PaddingValues(horizontal = 12.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.UploadFile,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(17.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = strings.uploadM3u,
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }

        // 3. ACTIVE PLAYLIST Section (Shown only once a playlist is selected)
        if (hasSelectedFirstPlaylist && currentPlaylist.id != "none") {
            item(key = "header_active_playlist") {
                Text(
                    text = strings.activePlaylist,
                    color = appColors.textSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp,
                    modifier = Modifier.padding(start = 2.dp, top = 2.dp)
                )
            }

        item(key = "card_active_playlist") {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isDark) Color(0xFF2E1218) else Color(0xFFFEF2F2)
                ),
                border = BorderStroke(2.dp, Color(0xFFDC2626)),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Red squircle with playlist lines icon
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFFDC2626)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Notes,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = currentPlaylist.title,
                                color = appColors.textPrimary,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = if (isDark) Color(0xFF0F3820) else Color(0xFFDCFCE7)
                            ) {
                                Text(
                                    text = strings.active,
                                    color = if (isDark) Color(0xFF4ADE80) else Color(0xFF16A34A),
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = currentPlaylist.url,
                            color = appColors.textSecondary,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    // Prominent Red Check Mark indicating selected (Photo 3)
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFDC2626)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Selected",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
        }

        // 4. MY ADDED PLAYLISTS Section (Screenshot 1 & 3)
        item(key = "header_my_added_playlists") {
            Text(
                text = "${strings.customPlaylists} (${filteredCustom.size})",
                color = appColors.textSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.8.sp,
                modifier = Modifier.padding(start = 2.dp, top = 6.dp)
            )
        }

        if (filteredCustom.isEmpty()) {
            item(key = "empty_custom_playlists") {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = appColors.surface),
                    border = BorderStroke(1.dp, appColors.border),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = if (trimmedQuery.isNotEmpty()) "No custom playlists matching \"$trimmedQuery\""
                        else "No custom playlists added yet. Tap 'Import Link' or 'Upload M3U' above to add yours.",
                        color = appColors.textSecondary,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        } else {
            items(filteredCustom, key = { it.id }) { cp ->
                val isCurrent = currentPlaylist.id == "custom_${cp.id}" || currentPlaylist.url == cp.url
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isCurrent) {
                            if (isDark) Color(0xFF2E1218) else Color(0xFFFEF2F2)
                        } else appColors.surface
                    ),
                    border = BorderStroke(
                        width = if (isCurrent) 2.dp else 1.dp,
                        color = if (isCurrent) Color(0xFFDC2626) else appColors.border
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            onSelectPlaylist(
                                PlaylistSource(
                                    id = "custom_${cp.id}",
                                    title = cp.title,
                                    url = cp.url,
                                    isPreset = false
                                )
                            )
                        }
                        .testTag("custom_playlist_${cp.id}")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Squircle with Link icon (Red if current, slate if not)
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isCurrent) Color(0xFFDC2626) else Color(0xFF475569)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Link,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(14.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = cp.title,
                                color = appColors.textPrimary,
                                fontSize = 15.sp,
                                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = cp.url,
                                color = appColors.textSecondary,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        // Clear Red Mark for Selection (Photo 3)
                        if (isCurrent) {
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFDC2626)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Selected",
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        } else {
                            // Unselected indicator
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .border(BorderStroke(1.5.dp, if (isDark) Color(0xFF3F3F46) else Color(0xFFCBD5E1)), CircleShape)
                            )
                        }

                        Spacer(modifier = Modifier.width(6.dp))

                        // Delete custom playlist button
                        IconButton(
                            onClick = { onDeleteCustomPlaylist(cp.id) },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeleteOutline,
                                contentDescription = "Delete playlist",
                                tint = Color(0xFFEF4444),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }

        // 5. DEFAULT PRESET PLAYLISTS Section (Matching user screenshot)
        item(key = "header_presets") {
            Text(
                text = "DEFAULT PRESET PLAYLISTS",
                color = appColors.textSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.8.sp,
                modifier = Modifier.padding(start = 2.dp, top = 6.dp)
            )
        }

        // Red Guidance Banner (Shown only when user has not yet selected a playlist)
        if (!hasSelectedFirstPlaylist) {
            item(key = "banner_select_first_playlist") {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFE50914)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("select_playlist_guidance_banner")
                ) {
                    Text(
                        text = "👇 Click here to select a playlist and start watching!",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)
                    )
                }
            }
        }

        if (filteredPresets.isEmpty()) {
            item(key = "empty_preset_playlists") {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = appColors.surface),
                    border = BorderStroke(1.dp, appColors.border),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "No preset playlists matching \"$trimmedQuery\"",
                        color = appColors.textSecondary,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        } else {
            itemsIndexed(filteredPresets, key = { _, it -> it.id }) { index, preset ->
                val isCurrent = hasSelectedFirstPlaylist && currentPlaylist.id == preset.id
                val isFirstWhenNotSelected = !hasSelectedFirstPlaylist && index == 0
                val showRedBorder = isCurrent || isFirstWhenNotSelected

                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (showRedBorder) {
                            if (isDark) Color(0xFF2E1218) else Color(0xFFFEF2F2)
                        } else appColors.surface
                    ),
                    border = BorderStroke(
                        width = if (showRedBorder) 2.dp else 1.dp,
                        color = if (showRedBorder) Color(0xFFDC2626) else appColors.border
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelectPlaylist(preset) }
                        .testTag("preset_playlist_${preset.id}")
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp)
                    ) {
                        // "👉 CLICK HERE TO SELECT" badge on top of first preset card when not selected yet
                        if (isFirstWhenNotSelected) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = Color(0xFFEF4444),
                                modifier = Modifier
                                    .padding(bottom = 8.dp)
                                    .testTag("click_here_to_select_badge")
                            ) {
                                Text(
                                    text = "👉 CLICK HERE TO SELECT",
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Black,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Play Arrow Squircle (Red if selected or first item when unselected, slate if not)
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (showRedBorder) Color(0xFFDC2626) else Color(0xFF475569)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(22.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(14.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = preset.title,
                                    color = appColors.textPrimary,
                                    fontSize = 15.sp,
                                    fontWeight = if (showRedBorder) FontWeight.Bold else FontWeight.SemiBold
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                val desc = when (preset.id) {
                                    PresetPlaylists.INDIA.id -> "Curated Indian news, entertainment, music & movies"
                                    PresetPlaylists.GLOBAL.id -> "Global international news, sports, & entertainment"
                                    else -> "All aggregated world live streams"
                                }
                                Text(
                                    text = desc,
                                    color = appColors.textSecondary,
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            if (hasSelectedFirstPlaylist) {
                                // Clear Red Mark for Selection once a playlist has been chosen
                                if (isCurrent) {
                                    Box(
                                        modifier = Modifier
                                            .size(28.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFFDC2626)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = "Selected",
                                            tint = Color.White,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                } else {
                                    // Unselected circular indicator
                                    Box(
                                        modifier = Modifier
                                            .size(24.dp)
                                            .clip(CircleShape)
                                            .border(BorderStroke(1.5.dp, if (isDark) Color(0xFF3F3F46) else Color(0xFFCBD5E1)), CircleShape)
                                    )
                                }
                                Spacer(modifier = Modifier.width(6.dp))
                            }

                            // Delete / Hide preset
                            IconButton(
                                onClick = { onDeletePresetPlaylist(preset.id) },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DeleteOutline,
                                    contentDescription = "Delete preset",
                                    tint = Color(0xFFEF4444),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Bottom space to ensure content is never hidden behind bottom bar
        item(key = "bottom_spacer") {
            Spacer(modifier = Modifier.height(100.dp))
        }
    }
}
