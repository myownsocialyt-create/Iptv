package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.CommunityPlaylists
import com.example.ui.theme.LocalAppColors
import com.example.ui.theme.OttPrimary
import com.example.ui.theme.Slate100
import com.example.ui.theme.Slate200
import com.example.ui.theme.Slate400
import com.example.ui.theme.Slate800
import com.example.ui.theme.Slate900

@Composable
fun AddPlaylistScreen(
    initialTab: Int = 0,
    onBackClick: () -> Unit,
    onAddPlaylist: (title: String, url: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val appColors = LocalAppColors.current
    val context = LocalContext.current
    var selectedTab by remember { mutableIntStateOf(initialTab) }

    var playlistName by remember { mutableStateOf("") }
    var playlistUrl by remember { mutableStateOf("") }
    var selectedFileUri by remember { mutableStateOf<Uri?>(null) }
    var selectedFileName by remember { mutableStateOf<String?>(null) }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            selectedFileUri = uri
            val fileName = uri.lastPathSegment?.substringAfterLast('/') ?: "imported_playlist.m3u"
            selectedFileName = fileName
            if (playlistName.isBlank()) {
                playlistName = fileName.removeSuffix(".m3u").removeSuffix(".m3u8")
            }
            playlistUrl = uri.toString()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(appColors.background)
    ) {
        // Top App Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBackClick) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = appColors.textPrimary)
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Add Playlist",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = appColors.textPrimary
            )
        }

        // Tab Row (Import Link vs Upload File)
        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = appColors.surface,
            contentColor = OttPrimary,
            indicator = { tabPositions ->
                TabRowDefaults.SecondaryIndicator(
                    modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                    color = OttPrimary
                )
            }
        ) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { Text("Import Link", fontWeight = FontWeight.Bold) },
                icon = { Icon(Icons.Default.Link, contentDescription = null, modifier = Modifier.size(18.dp)) }
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = { Text("Upload File", fontWeight = FontWeight.Bold) },
                icon = { Icon(Icons.Default.FileOpen, contentDescription = null, modifier = Modifier.size(18.dp)) }
            )
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (selectedTab == 0) {
                item {
                    Text("Playlist Name", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Slate800)
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(
                        value = playlistName,
                        onValueChange = { playlistName = it },
                        placeholder = { Text("Enter playlist name", color = Slate400) },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Slate900,
                            unfocusedTextColor = Slate900,
                            cursorColor = OttPrimary,
                            focusedBorderColor = OttPrimary,
                            unfocusedBorderColor = Slate200,
                            focusedContainerColor = Slate100.copy(alpha = 0.5f),
                            unfocusedContainerColor = Slate100.copy(alpha = 0.5f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                item {
                    Text("Playlist URL", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Slate800)
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(
                        value = playlistUrl,
                        onValueChange = { playlistUrl = it },
                        placeholder = { Text("https://example.com/playlist.m3u", color = Slate400) },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Slate900,
                            unfocusedTextColor = Slate900,
                            cursorColor = OttPrimary,
                            focusedBorderColor = OttPrimary,
                            unfocusedBorderColor = Slate200,
                            focusedContainerColor = Slate100.copy(alpha = 0.5f),
                            unfocusedContainerColor = Slate100.copy(alpha = 0.5f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                item {
                    Button(
                        onClick = {
                            if (playlistUrl.isNotBlank()) {
                                onAddPlaylist(playlistName.ifBlank { "My Playlist" }, playlistUrl)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = OttPrimary),
                        shape = RoundedCornerShape(12.dp),
                        enabled = playlistUrl.isNotBlank(),
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    ) {
                        Text("Add Playlist", fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }

                // Community Playlists Section
                item {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Or Choose from Community Playlists",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = appColors.textPrimary
                    )
                }

                items(CommunityPlaylists.LIST) { cp ->
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = appColors.surface),
                        border = BorderStroke(1.dp, appColors.border),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                playlistName = cp.name
                                playlistUrl = cp.url
                            }
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(cp.name, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = appColors.textPrimary)
                            Text(cp.description, fontSize = 12.sp, color = appColors.textSecondary, modifier = Modifier.padding(vertical = 2.dp))
                            Text(cp.channelCount, fontSize = 11.sp, color = OttPrimary, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            } else {
                // Upload File Tab
                item {
                    Text("Playlist Name", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Slate800)
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(
                        value = playlistName,
                        onValueChange = { playlistName = it },
                        placeholder = { Text("Enter your playlist name", color = Slate400) },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Slate900,
                            unfocusedTextColor = Slate900,
                            cursorColor = OttPrimary,
                            focusedBorderColor = OttPrimary,
                            unfocusedBorderColor = Slate200,
                            focusedContainerColor = Slate100.copy(alpha = 0.5f),
                            unfocusedContainerColor = Slate100.copy(alpha = 0.5f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                item {
                    Surface(
                        onClick = { filePickerLauncher.launch(arrayOf("*/*")) },
                        shape = RoundedCornerShape(14.dp),
                        color = appColors.surfaceVariant,
                        border = BorderStroke(1.dp, appColors.border),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(Icons.Default.FileOpen, contentDescription = null, tint = OttPrimary, modifier = Modifier.size(36.dp))
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = selectedFileName ?: "Tap to choose .M3U or .M3U8 file",
                                fontWeight = FontWeight.Bold,
                                color = appColors.textPrimary,
                                fontSize = 14.sp
                            )
                        }
                    }
                }

                item {
                    Button(
                        onClick = {
                            if (playlistUrl.isNotBlank()) {
                                onAddPlaylist(playlistName.ifBlank { "My File Playlist" }, playlistUrl)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = OttPrimary),
                        shape = RoundedCornerShape(12.dp),
                        enabled = playlistUrl.isNotBlank(),
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    ) {
                        Text("Add Local Playlist", fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            }
        }
    }
}
