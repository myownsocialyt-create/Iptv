package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.CustomPlaylistEntity
import com.example.data.repository.PlaylistSource
import com.example.data.repository.PresetPlaylists
import com.example.ui.theme.LocalAppColors
import com.example.ui.theme.OttPrimary

@Composable
fun PlaylistsScreen(
    currentPlaylist: PlaylistSource,
    customPlaylists: List<CustomPlaylistEntity>,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    deletedPresetIds: Set<String>,
    onDeletePresetPlaylist: (String) -> Unit,
    hasSelectedFirstPlaylist: Boolean,
    onSelectPlaylist: (PlaylistSource) -> Unit,
    onDeleteCustomPlaylist: (Long) -> Unit,
    onNavigateToAddPlaylist: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val appColors = LocalAppColors.current

    LazyColumn(
        modifier = modifier.fillMaxSize().background(appColors.background),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Add Playlist Header Action
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Playlists",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = appColors.textPrimary
                )

                Button(
                    onClick = { onNavigateToAddPlaylist(0) },
                    colors = ButtonDefaults.buttonColors(containerColor = OttPrimary),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Add Playlist", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        }

        // Preset Playlists Section
        item {
            Text(
                text = "DEFAULT PLAYLISTS",
                fontSize = 11.sp,
                fontWeight = FontWeight.ExtraBold,
                color = OttPrimary,
                letterSpacing = 0.5.sp
            )
        }

        val presets = listOf(PresetPlaylists.ALL, PresetPlaylists.INDIA, PresetPlaylists.GLOBAL)
            .filter { it.id !in deletedPresetIds }

        items(presets) { preset ->
            val isSelected = currentPlaylist.id == preset.id
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = if (isSelected) OttPrimary.copy(alpha = 0.12f) else appColors.surface),
                border = BorderStroke(1.dp, if (isSelected) OttPrimary else appColors.border),
                modifier = Modifier.fillMaxWidth().clickable { onSelectPlaylist(preset) }
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Box(
                            modifier = Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)).background(OttPrimary.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.PlaylistPlay, contentDescription = null, tint = OttPrimary, modifier = Modifier.size(24.dp))
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(preset.title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = appColors.textPrimary)
                            Text("Official Curated Feed", fontSize = 11.sp, color = appColors.textSecondary)
                        }
                    }

                    if (isSelected) {
                        Surface(color = OttPrimary, shape = RoundedCornerShape(12.dp)) {
                            Text("ACTIVE", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
                        }
                    }
                }
            }
        }

        // Custom Playlists Section
        if (customPlaylists.isNotEmpty()) {
            item {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "CUSTOM PLAYLISTS (${customPlaylists.size})",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = OttPrimary,
                    letterSpacing = 0.5.sp
                )
            }

            items(customPlaylists) { cp ->
                val customSource = PlaylistSource(id = "custom_${cp.id}", title = cp.title, url = cp.url, isPreset = false)
                val isSelected = currentPlaylist.id == customSource.id

                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = if (isSelected) OttPrimary.copy(alpha = 0.12f) else appColors.surface),
                    border = BorderStroke(1.dp, if (isSelected) OttPrimary else appColors.border),
                    modifier = Modifier.fillMaxWidth().clickable { onSelectPlaylist(customSource) }
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Box(
                                modifier = Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)).background(appColors.surfaceVariant),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("📁", fontSize = 18.sp)
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f, fill = false)) {
                                Text(cp.title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = appColors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(cp.url, fontSize = 11.sp, color = appColors.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }

                        IconButton(onClick = { onDeleteCustomPlaylist(cp.id) }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.Red.copy(alpha = 0.7f), modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }
    }
}
