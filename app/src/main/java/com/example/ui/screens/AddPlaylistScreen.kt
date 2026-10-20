package com.example.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.CommunityPlaylists
import com.example.ui.theme.LocalAppColors
import com.example.ui.theme.OttPrimary

@Composable
fun AddPlaylistScreen(
    initialTab: Int = 0,
    onBackClick: () -> Unit,
    onAddPlaylist: (title: String, url: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val appColors = LocalAppColors.current
    val isDark = appColors.isDark

    var selectedTab by remember(initialTab) { mutableIntStateOf(initialTab) }

    var playlistName by remember { mutableStateOf("") }
    var playlistUrl by remember { mutableStateOf("") }
    var selectedFileName by remember { mutableStateOf<String?>(null) }
    var showHelpDialog by remember { mutableStateOf(false) }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            val fileName = uri.lastPathSegment?.substringAfterLast('/') ?: "custom_playlist.m3u"
            selectedFileName = fileName
            if (playlistName.isBlank()) {
                playlistName = fileName.removeSuffix(".m3u").removeSuffix(".m3u8")
            }
            playlistUrl = uri.toString()
        }
    }

    if (showHelpDialog) {
        AlertDialog(
            onDismissRequest = { showHelpDialog = false },
            title = { Text("M3U Playlist Help", fontWeight = FontWeight.Bold, color = appColors.textPrimary) },
            text = {
                Text(
                    "You can add any public IPTV M3U or M3U8 link or upload local .m3u files from your device. " +
                            "Choose from the verified community playlists below to get started immediately.",
                    color = appColors.textSecondary
                )
            },
            containerColor = appColors.surface,
            confirmButton = {
                Button(
                    onClick = { showHelpDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = OttPrimary)
                ) {
                    Text("Got it", color = Color.White)
                }
            }
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(if (isDark) Color(0xFF000000) else Color(0xFF0F172A))
    ) {
        // Dark Top Header with Circular Back & Help Buttons
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = CircleShape,
                color = Color.White.copy(alpha = 0.15f),
                modifier = Modifier.size(40.dp)
            ) {
                IconButton(onClick = onBackClick) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            Text(
                text = "Add Your Playlist",
                color = Color.White,
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )

            Surface(
                shape = CircleShape,
                color = Color.White.copy(alpha = 0.15f),
                modifier = Modifier.size(40.dp)
            ) {
                IconButton(onClick = { showHelpDialog = true }) {
                    Icon(
                        imageVector = Icons.Default.HelpOutline,
                        contentDescription = "Help",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Body Sheet supporting OLED Black / Light
        Surface(
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            color = appColors.surface,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Two Tabs: [ Import link ] [ Upload file ]
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = appColors.surface,
                    contentColor = OttPrimary,
                    indicator = { tabPositions ->
                        TabRowDefaults.SecondaryIndicator(
                            modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                            color = OttPrimary,
                            height = 3.dp
                        )
                    },
                    divider = {
                        HorizontalDivider(color = appColors.border)
                    }
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = {
                            Text(
                                text = "Import link",
                                fontSize = 15.sp,
                                fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Medium,
                                color = if (selectedTab == 0) appColors.textPrimary else appColors.textSecondary
                            )
                        }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = {
                            Text(
                                text = "Upload file",
                                fontSize = 15.sp,
                                fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Medium,
                                color = if (selectedTab == 1) appColors.textPrimary else appColors.textSecondary
                            )
                        }
                    )
                }

                // Tab Content List
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 16.dp),
                    contentPadding = PaddingValues(top = 16.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // TAB 0: IMPORT LINK
                    if (selectedTab == 0) {
                        // Field 1: Playlist Name
                        item {
                            Column {
                                Text(
                                    text = "Playlist Name",
                                    color = appColors.textPrimary,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                OutlinedTextField(
                                    value = playlistName,
                                    onValueChange = { playlistName = it },
                                    placeholder = { Text("Enter playlist name", color = appColors.textMuted, fontSize = 14.sp) },
                                    singleLine = true,
                                    shape = RoundedCornerShape(12.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = OttPrimary,
                                        unfocusedBorderColor = appColors.border,
                                        focusedContainerColor = appColors.surfaceVariant,
                                        unfocusedContainerColor = appColors.surfaceVariant,
                                        focusedTextColor = appColors.textPrimary,
                                        unfocusedTextColor = appColors.textPrimary,
                                        cursorColor = OttPrimary
                                    ),
                                    modifier = Modifier.fillMaxWidth().testTag("input_playlist_name")
                                )
                            }
                        }

                        // Field 2: Playlist URL + "Find Public IPTV Playlists" link
                        item {
                            Column {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "Playlist URL",
                                        color = appColors.textPrimary,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold
                                    )

                                    Text(
                                        text = "Find Public IPTV Playlists",
                                        color = OttPrimary,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        textDecoration = TextDecoration.Underline,
                                        modifier = Modifier.clickable {
                                            openPublicIptvUrl(context)
                                        }
                                    )
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                OutlinedTextField(
                                    value = playlistUrl,
                                    onValueChange = { playlistUrl = it },
                                    placeholder = { Text("https://example.com/playlist.m3u", color = appColors.textMuted, fontSize = 14.sp) },
                                    singleLine = true,
                                    shape = RoundedCornerShape(12.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = OttPrimary,
                                        unfocusedBorderColor = appColors.border,
                                        focusedContainerColor = appColors.surfaceVariant,
                                        unfocusedContainerColor = appColors.surfaceVariant,
                                        focusedTextColor = appColors.textPrimary,
                                        unfocusedTextColor = appColors.textPrimary,
                                        cursorColor = OttPrimary
                                    ),
                                    modifier = Modifier.fillMaxWidth().testTag("input_playlist_url")
                                )
                            }
                        }

                        // Community Playlists Header
                        item {
                            Text(
                                text = "Choose one from community playlist",
                                color = appColors.textPrimary,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }

                        // Community Playlists Cards
                        items(CommunityPlaylists.LIST) { item ->
                            Card(
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(containerColor = appColors.surfaceVariant),
                                border = BorderStroke(1.dp, appColors.border),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        playlistName = item.name
                                        playlistUrl = item.url
                                    }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(42.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(OttPrimary),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.PlayArrow,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(12.dp))

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = item.name,
                                            color = appColors.textPrimary,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = item.url,
                                            color = appColors.textSecondary,
                                            fontSize = 11.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }

                        // Find Public IPTV Playlists Red Action Card Button
                        item {
                            Button(
                                onClick = { openPublicIptvUrl(context) },
                                colors = ButtonDefaults.buttonColors(containerColor = OttPrimary),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(46.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "Find Public IPTV Playlists",
                                        color = Color.White,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    } else {
                        // TAB 1: UPLOAD FILE
                        item {
                            Column {
                                Text(
                                    text = "Playlist Name",
                                    color = appColors.textPrimary,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                OutlinedTextField(
                                    value = playlistName,
                                    onValueChange = { playlistName = it },
                                    placeholder = { Text("Enter your playlist name", color = appColors.textMuted, fontSize = 14.sp) },
                                    singleLine = true,
                                    shape = RoundedCornerShape(12.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = OttPrimary,
                                        unfocusedBorderColor = appColors.border,
                                        focusedContainerColor = appColors.surfaceVariant,
                                        unfocusedContainerColor = appColors.surfaceVariant,
                                        focusedTextColor = appColors.textPrimary,
                                        unfocusedTextColor = appColors.textPrimary,
                                        cursorColor = OttPrimary
                                    ),
                                    modifier = Modifier.fillMaxWidth().testTag("input_upload_playlist_name")
                                )
                            }
                        }

                        item {
                            Column {
                                Text(
                                    text = "Upload M3U File",
                                    color = appColors.textPrimary,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Accepted file formats: .m3u, .m3u8",
                                    color = appColors.textSecondary,
                                    fontSize = 12.sp
                                )
                                Spacer(modifier = Modifier.height(10.dp))

                                // Select file upload box
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = appColors.surfaceVariant,
                                    border = BorderStroke(1.dp, appColors.border),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            filePickerLauncher.launch(arrayOf("text/*", "application/*", "*/*"))
                                        }
                                        .padding(vertical = 4.dp)
                                        .testTag("file_picker_container")
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 32.dp, horizontal = 16.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Description,
                                            contentDescription = null,
                                            tint = Color(0xFF10B981),
                                            modifier = Modifier.size(36.dp)
                                        )
                                        Spacer(modifier = Modifier.height(12.dp))
                                        Text(
                                            text = selectedFileName ?: "Select file to upload",
                                            color = appColors.textPrimary,
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = if (selectedFileName != null) "File selected ready to import" else "Only .m3u or .m3u8 playlist files are accepted",
                                            color = appColors.textSecondary,
                                            fontSize = 12.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Fixed Bottom "Add" Red Button
                Surface(
                    color = appColors.surface,
                    border = BorderStroke(1.dp, appColors.border.copy(alpha = 0.5f)),
                    shadowElevation = 8.dp,
                    modifier = Modifier.fillMaxWidth().navigationBarsPadding()
                ) {
                    Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                        Button(
                            onClick = {
                                if (playlistUrl.isBlank()) {
                                    Toast.makeText(context, "Please enter a playlist URL or select a file", Toast.LENGTH_SHORT).show()
                                    return@Button
                                }
                                val finalTitle = playlistName.trim().ifBlank { "My Playlist" }
                                onAddPlaylist(finalTitle, playlistUrl.trim())
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = OttPrimary),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("add_playlist_submit_button")
                        ) {
                            Text(
                                text = "Add",
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun openPublicIptvUrl(context: Context) {
    try {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/iptv-org/iptv")).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    } catch (_: Exception) {
        Toast.makeText(context, "Visit: https://github.com/iptv-org/iptv", Toast.LENGTH_LONG).show()
    }
}
