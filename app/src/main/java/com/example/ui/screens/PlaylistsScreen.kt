package com.example.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.CustomPlaylistEntity
import com.example.data.model.CommunityPlaylists
import com.example.data.repository.PlaylistSource
import com.example.data.repository.PresetPlaylists
import com.example.ui.components.PosterAdCard
import com.example.ui.theme.LocalAppColors
import com.example.ui.theme.OttPrimary

@Composable
fun PlaylistsScreen(
    currentSource: PlaylistSource,
    customPlaylists: List<CustomPlaylistEntity>,
    onSelectSource: (PlaylistSource) -> Unit,
    onNavigateToAddPlaylist: () -> Unit,
    onDeleteCustomPlaylist: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val appColors = LocalAppColors.current

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Playlists & Sources",
                    color = appColors.textPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Button(
                    onClick = onNavigateToAddPlaylist,
                    colors = ButtonDefaults.buttonColors(containerColor = OttPrimary),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Add Playlist", fontSize = 12.sp)
                }
            }
        }

        // Poster Ad Card
        item {
            PosterAdCard(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp))
        }

        item {
            Text(
                text = "Preset Sources",
                color = appColors.textSecondary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(vertical = 6.dp)
            )
        }

        items(PresetPlaylists.DEFAULT_PLAYLISTS) { source ->
            val isSelected = source.id == currentSource.id
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = appColors.surface),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .clickable { onSelectSource(source) }
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.PlaylistPlay,
                        contentDescription = null,
                        tint = if (isSelected) OttPrimary else appColors.textSecondary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = source.title,
                            color = if (isSelected) OttPrimary else appColors.textPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = source.url,
                            color = appColors.textSecondary,
                            fontSize = 11.sp,
                            maxLines = 1
                        )
                    }
                    if (isSelected) {
                        Icon(Icons.Default.Check, contentDescription = "Active", tint = OttPrimary)
                    }
                }
            }
        }

        if (customPlaylists.isNotEmpty()) {
            item {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Custom Playlists (${customPlaylists.size})",
                    color = appColors.textSecondary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(vertical = 6.dp)
                )
            }

            items(customPlaylists, key = { it.id }) { custom ->
                val source = PlaylistSource(id = custom.id, title = custom.name, url = custom.url, isPreset = false)
                val isSelected = custom.id == currentSource.id
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = appColors.surface),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clickable { onSelectSource(source) }
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlaylistPlay,
                            contentDescription = null,
                            tint = if (isSelected) OttPrimary else appColors.textSecondary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = custom.name,
                                color = if (isSelected) OttPrimary else appColors.textPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = custom.url,
                                color = appColors.textSecondary,
                                fontSize = 11.sp,
                                maxLines = 1
                            )
                        }
                        IconButton(onClick = { onDeleteCustomPlaylist(custom.id) }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.Red.copy(alpha = 0.7f))
                        }
                    }
                }
            }
        }

        // Community Recommended Playlists
        item {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Community Recommended Playlists",
                color = appColors.textSecondary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(vertical = 6.dp)
            )
        }

        items(CommunityPlaylists.presets) { community ->
            val source = PlaylistSource(id = community.id, title = community.name, url = community.url, isPreset = false)
            val isSelected = community.id == currentSource.id
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = appColors.surface),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .clickable { onSelectSource(source) }
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = community.flagEmoji, fontSize = 22.sp)
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = community.name,
                            color = if (isSelected) OttPrimary else appColors.textPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = community.description,
                            color = appColors.textSecondary,
                            fontSize = 11.sp
                        )
                    }
                    if (isSelected) {
                        Icon(Icons.Default.Check, contentDescription = "Active", tint = OttPrimary)
                    }
                }
            }
        }
    }
}
