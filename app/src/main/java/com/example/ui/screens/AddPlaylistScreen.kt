package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.CommunityPlaylist
import com.example.data.model.CommunityPlaylists
import com.example.data.repository.M3uParser
import com.example.ui.theme.LocalAppColors
import com.example.ui.theme.OttLiveRed
import com.example.ui.theme.OttPrimary
import com.example.ui.theme.Slate100
import com.example.ui.theme.Slate200
import com.example.ui.theme.Slate400
import com.example.ui.theme.Slate500
import com.example.ui.theme.Slate700
import com.example.ui.theme.Slate800
import com.example.ui.theme.Slate900
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Screen to Add / Import IPTV Playlists via Link or File Upload.
 * Matches user screenshots with modern app theme styling and full functionality.
 */
@Composable
fun AddPlaylistScreen(
    initialTab: Int = 0,
    onBackClick: () -> Unit,
    onAddPlaylist: (title: String, url: String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val appColors = LocalAppColors.current

    var selectedTab by remember { mutableIntStateOf(initialTab.coerceIn(0, 1)) }

    // Form states for Tab 0: Import Link
    var playlistName by remember { mutableStateOf("") }
    var playlistUrl by remember { mutableStateOf("") }
    var selectedCommunityUrl by remember { mutableStateOf<String?>(null) }

    // Form states for Tab 1: Upload File
    var uploadedFileName by remember { mutableStateOf<String?>(null) }
    var uploadedFileUrl by remember { mutableStateOf<String?>(null) }
    var uploadedFileSize by remember { mutableStateOf<String?>(null) }
    var uploadedChannelCount by remember { mutableIntStateOf(0) }
    var uploadErrorMessage by remember { mutableStateOf<String?>(null) }
    var isReadingFile by remember { mutableStateOf(false) }

    // File Picker Launcher
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            isReadingFile = true
            uploadErrorMessage = null
            scope.launch(Dispatchers.IO) {
                try {
                    val contentResolver = context.contentResolver
                    var displayName = "playlist.m3u"

                    contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                            if (nameIndex != -1) {
                                displayName = cursor.getString(nameIndex) ?: displayName
                            }
                        }
                    }

                    // Strict Extension Validation: Only .m3u and .m3u8 are accepted
                    val lowerName = displayName.lowercase().trim()
                    val hasValidExtension = lowerName.endsWith(".m3u") || lowerName.endsWith(".m3u8")

                    if (!hasValidExtension) {
                        withContext(Dispatchers.Main) {
                            isReadingFile = false
                            uploadedFileName = null
                            uploadedFileUrl = null
                            uploadedFileSize = null
                            uploadedChannelCount = 0
                            uploadErrorMessage = "Invalid file type! Only .m3u and .m3u8 files are accepted (You selected: $displayName)"
                            Toast.makeText(context, "Only .m3u and .m3u8 files are accepted!", Toast.LENGTH_LONG).show()
                        }
                        return@launch
                    }

                    // Save file to app private filesDir
                    val playlistsDir = File(context.filesDir, "playlists").apply { mkdirs() }
                    val cleanFileName = displayName.replace("[^a-zA-Z0-9._-]".toRegex(), "_")
                    val destFile = File(playlistsDir, "m3u_${System.currentTimeMillis()}_$cleanFileName")

                    contentResolver.openInputStream(uri)?.use { input ->
                        destFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }

                    // Content validation: Parse channels to ensure file actually works
                    val channels = try {
                        destFile.inputStream().use { input ->
                            M3uParser.parse(input)
                        }
                    } catch (e: Exception) {
                        emptyList()
                    }

                    if (channels.isEmpty()) {
                        destFile.delete()
                        withContext(Dispatchers.Main) {
                            isReadingFile = false
                            uploadedFileName = null
                            uploadedFileUrl = null
                            uploadedFileSize = null
                            uploadedChannelCount = 0
                            uploadErrorMessage = "No playable channels found in this file. Please verify it is a valid IPTV .m3u or .m3u8 playlist."
                            Toast.makeText(context, "Invalid playlist: No channels found in file!", Toast.LENGTH_LONG).show()
                        }
                        return@launch
                    }

                    withContext(Dispatchers.Main) {
                        uploadedFileName = displayName
                        uploadedFileUrl = "file://${destFile.absolutePath}"
                        val sizeKb = (destFile.length() / 1024).coerceAtLeast(1)
                        uploadedFileSize = "$sizeKb KB"
                        uploadedChannelCount = channels.size
                        uploadErrorMessage = null

                        // If user hasn't typed a name yet, auto-fill with file base name
                        if (playlistName.isBlank()) {
                            val baseName = displayName.substringBeforeLast(".")
                            playlistName = baseName.replace("_", " ").replace("-", " ")
                                .split(" ")
                                .filter { it.isNotBlank() }
                                .joinToString(" ") { it.replaceFirstChar { char -> char.uppercase() } }
                        }
                        isReadingFile = false
                        Toast.makeText(context, "✓ $displayName (${channels.size} channels) loaded", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        isReadingFile = false
                        uploadedFileName = null
                        uploadedFileUrl = null
                        uploadedFileSize = null
                        uploadedChannelCount = 0
                        uploadErrorMessage = "Failed to process file: ${e.localizedMessage}"
                        Toast.makeText(context, "Failed to read file: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    val openFindPlaylistsUrl = {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(CommunityPlaylists.PUBLIC_PLAYLISTS_URL))
        try {
            context.startActivity(intent)
        } catch (_: Exception) {
            Toast.makeText(context, "Cannot open link: browser not found", Toast.LENGTH_SHORT).show()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF180808),
                        Color(0xFF220A0B),
                        Color(0xFF0F172A)
                    )
                )
            )
    ) {
        // Top Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                color = Color.White.copy(alpha = 0.15f),
                shape = CircleShape,
                modifier = Modifier
                    .size(42.dp)
                    .clickable { onBackClick() }
                    .testTag("add_playlist_back_button")
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            Text(
                text = "Add Your Playlist",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )

            // Help Question Icon
            IconButton(
                onClick = openFindPlaylistsUrl,
                modifier = Modifier.size(38.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.HelpOutline,
                    contentDescription = "Find Public IPTV Playlists",
                    tint = Color.White.copy(alpha = 0.85f),
                    modifier = Modifier.size(22.dp)
                )
            }
        }

        // Form Card Container
        Card(
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (appColors.isDark) appColors.surface else Color.White
            ),
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Top Tab Selector: "Import link" vs "Upload file"
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = if (appColors.isDark) appColors.surface else Color.White,
                    contentColor = OttPrimary,
                    indicator = { tabPositions ->
                        TabRowDefaults.SecondaryIndicator(
                            Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                            color = OttPrimary,
                            height = 3.dp
                        )
                    },
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = {
                            Text(
                                text = "Import link",
                                fontSize = 15.sp,
                                fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Medium,
                                color = if (selectedTab == 0) (if (appColors.isDark) appColors.textPrimary else Slate900) else appColors.textMuted
                            )
                        },
                        modifier = Modifier.testTag("tab_import_link")
                    )

                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = {
                            Text(
                                text = "Upload file",
                                fontSize = 15.sp,
                                fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Medium,
                                color = if (selectedTab == 1) (if (appColors.isDark) appColors.textPrimary else Slate900) else appColors.textMuted
                            )
                        },
                        modifier = Modifier.testTag("tab_upload_file")
                    )
                }

                // Tab Contents
                Box(modifier = Modifier.weight(1f)) {
                    if (selectedTab == 0) {
                        // TAB 0: IMPORT LINK
                        LazyColumn(
                            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            // Playlist Name Field
                            item {
                                Column {
                                    Text(
                                        text = "Playlist Name",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (appColors.isDark) appColors.textPrimary else Slate800
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    OutlinedTextField(
                                        value = playlistName,
                                        onValueChange = { playlistName = it },
                                        placeholder = {
                                            Text(
                                                "Enter playlist name",
                                                color = if (appColors.isDark) appColors.textMuted else Slate400
                                            )
                                        },
                                        singleLine = true,
                                        shape = RoundedCornerShape(14.dp),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedTextColor = if (appColors.isDark) appColors.textPrimary else Slate900,
                                            unfocusedTextColor = if (appColors.isDark) appColors.textPrimary else Slate900,
                                            focusedBorderColor = OttPrimary,
                                            unfocusedBorderColor = if (appColors.isDark) appColors.border else Slate200,
                                            focusedContainerColor = if (appColors.isDark) appColors.surfaceVariant else Slate100.copy(alpha = 0.5f),
                                            unfocusedContainerColor = if (appColors.isDark) appColors.surfaceVariant else Slate100.copy(alpha = 0.5f),
                                            cursorColor = OttPrimary
                                        ),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("input_playlist_name")
                                    )
                                }
                            }

                            // Playlist URL Field with "Find Public IPTV Playlists" link
                            item {
                                Column {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Playlist URL",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (appColors.isDark) appColors.textPrimary else Slate800
                                        )

                                        TextButton(
                                            onClick = openFindPlaylistsUrl,
                                            contentPadding = PaddingValues(0.dp)
                                        ) {
                                            Text(
                                                text = "Find Public IPTV Playlists",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                textDecoration = TextDecoration.Underline,
                                                color = OttPrimary
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(4.dp))

                                    OutlinedTextField(
                                        value = playlistUrl,
                                        onValueChange = {
                                            playlistUrl = it
                                            selectedCommunityUrl = null
                                        },
                                        placeholder = {
                                            Text(
                                                "https://example.com/playlist.m3u",
                                                color = if (appColors.isDark) appColors.textMuted else Slate400
                                            )
                                        },
                                        singleLine = true,
                                        shape = RoundedCornerShape(14.dp),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedTextColor = if (appColors.isDark) appColors.textPrimary else Slate900,
                                            unfocusedTextColor = if (appColors.isDark) appColors.textPrimary else Slate900,
                                            focusedBorderColor = OttPrimary,
                                            unfocusedBorderColor = if (appColors.isDark) appColors.border else Slate200,
                                            focusedContainerColor = if (appColors.isDark) appColors.surfaceVariant else Slate100.copy(alpha = 0.5f),
                                            unfocusedContainerColor = if (appColors.isDark) appColors.surfaceVariant else Slate100.copy(alpha = 0.5f),
                                            cursorColor = OttPrimary
                                        ),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("input_playlist_url")
                                    )
                                }
                            }

                            // Community Playlists Section
                            item {
                                Text(
                                    text = "Choose one from community playlist",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (appColors.isDark) appColors.textPrimary else Slate900,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }

                            // Community Playlist Cards (Auto-fills on click)
                            items(CommunityPlaylists.ALL) { item ->
                                val isSelected = selectedCommunityUrl == item.url || playlistUrl == item.url
                                CommunityPlaylistCard(
                                    playlist = item,
                                    isSelected = isSelected,
                                    appColors = appColors,
                                    onClick = {
                                        playlistName = item.title
                                        playlistUrl = item.url
                                        selectedCommunityUrl = item.url
                                    }
                                )
                            }

                            // Big Gradient Button: "Find Public IPTV Playlists" (Screenshot 4)
                            item {
                                Spacer(modifier = Modifier.height(4.dp))
                                Button(
                                    onClick = openFindPlaylistsUrl,
                                    shape = RoundedCornerShape(14.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                                    contentPadding = PaddingValues(0.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(50.dp)
                                        .background(
                                            Brush.horizontalGradient(
                                                colors = listOf(
                                                    Color(0xFFE50914),
                                                    Color(0xFFDC2626),
                                                    Color(0xFFB91C1C)
                                                )
                                            ),
                                            shape = RoundedCornerShape(14.dp)
                                        )
                                        .testTag("btn_find_public_playlists")
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        Text(
                                            text = "Find Public IPTV Playlists",
                                            color = Color.White,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Icon(
                                            imageVector = Icons.Default.OpenInNew,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }

                            item { Spacer(modifier = Modifier.height(10.dp)) }
                        }
                    } else {
                        // TAB 1: UPLOAD FILE
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 20.dp, vertical = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            // Playlist Name
                            Column {
                                Text(
                                    text = "Playlist Name",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (appColors.isDark) appColors.textPrimary else Slate800
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                OutlinedTextField(
                                    value = playlistName,
                                    onValueChange = { playlistName = it },
                                    placeholder = {
                                        Text(
                                            "Enter your playlist name",
                                            color = if (appColors.isDark) appColors.textMuted else Slate400
                                        )
                                    },
                                    singleLine = true,
                                    shape = RoundedCornerShape(14.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedTextColor = if (appColors.isDark) appColors.textPrimary else Slate900,
                                        unfocusedTextColor = if (appColors.isDark) appColors.textPrimary else Slate900,
                                        focusedBorderColor = OttPrimary,
                                        unfocusedBorderColor = if (appColors.isDark) appColors.border else Slate200,
                                        focusedContainerColor = if (appColors.isDark) appColors.surfaceVariant else Slate100.copy(alpha = 0.5f),
                                        unfocusedContainerColor = if (appColors.isDark) appColors.surfaceVariant else Slate100.copy(alpha = 0.5f),
                                        cursorColor = OttPrimary
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("input_upload_playlist_name")
                                )
                            }

                            // Upload M3U File Section
                            Column {
                                Text(
                                    text = "Upload M3U File",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (appColors.isDark) appColors.textPrimary else Slate900
                                )
                                Text(
                                    text = "Accepted file formats: .m3u, .m3u8",
                                    fontSize = 12.sp,
                                    color = appColors.textSecondary
                                )

                                Spacer(modifier = Modifier.height(12.dp))

                                // Dashed Border Card for Selecting File (Screenshot 5)
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = when {
                                        uploadErrorMessage != null -> if (appColors.isDark) Color(0xFF2A1010) else Color(0xFFFEF2F2)
                                        uploadedFileName != null -> if (appColors.isDark) Color(0xFF0E2816) else Color(0xFFF0FDF4)
                                        else -> if (appColors.isDark) appColors.surfaceVariant else Color(0xFFFAFAFA)
                                    },
                                    border = BorderStroke(
                                        width = 1.5.dp,
                                        color = when {
                                            uploadErrorMessage != null -> Color(0xFFEF4444)
                                            uploadedFileName != null -> Color(0xFF22C55E)
                                            else -> if (appColors.isDark) appColors.border else Color(0xFFCBD5E1)
                                        }
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            if (!isReadingFile) {
                                                filePickerLauncher.launch("*/*")
                                            }
                                        }
                                        .testTag("select_file_box")
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 24.dp, horizontal = 16.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        if (isReadingFile) {
                                            CircularProgressIndicator(
                                                color = OttPrimary,
                                                modifier = Modifier.size(36.dp)
                                            )
                                            Spacer(modifier = Modifier.height(10.dp))
                                            Text(
                                                text = "Validating .m3u / .m3u8 playlist...",
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = if (appColors.isDark) appColors.textPrimary else Slate700
                                            )
                                        } else if (uploadedFileName != null) {
                                            Icon(
                                                imageVector = Icons.Default.CheckCircle,
                                                contentDescription = "File Ready",
                                                tint = Color(0xFF16A34A),
                                                modifier = Modifier.size(42.dp)
                                            )
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Text(
                                                text = uploadedFileName ?: "",
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (appColors.isDark) appColors.textPrimary else Slate900,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = "✓ $uploadedChannelCount channels detected • $uploadedFileSize",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = Color(0xFF15803D)
                                            )
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = "Tap to choose a different .m3u / .m3u8 file",
                                                fontSize = 11.sp,
                                                color = appColors.textMuted
                                            )
                                        } else {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.Center
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Description,
                                                    contentDescription = "Document",
                                                    tint = if (uploadErrorMessage != null) Color(0xFFDC2626) else Color(0xFF10B981),
                                                    modifier = Modifier.size(24.dp)
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(
                                                    text = if (uploadErrorMessage != null) "Select a valid .m3u / .m3u8 file" else "Select file to upload",
                                                    fontSize = 14.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = if (uploadErrorMessage != null) Color(0xFFDC2626) else (if (appColors.isDark) appColors.textPrimary else Slate800)
                                                )
                                            }
                                            Spacer(modifier = Modifier.height(6.dp))
                                            Text(
                                                text = "Only .m3u or .m3u8 playlist files are accepted",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = if (uploadErrorMessage != null) Color(0xFFB91C1C) else appColors.textMuted
                                            )
                                        }
                                    }
                                }

                                if (uploadErrorMessage != null) {
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Surface(
                                        color = if (appColors.isDark) Color(0xFF2A1010) else Color(0xFFFEF2F2),
                                        shape = RoundedCornerShape(12.dp),
                                        border = BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.4f)),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(12.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.HelpOutline,
                                                contentDescription = "Error",
                                                tint = Color(0xFFDC2626),
                                                modifier = Modifier.size(20.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = uploadErrorMessage ?: "",
                                                color = if (appColors.isDark) Color(0xFFFCA5A5) else Color(0xFFB91C1C),
                                                fontSize = 12.sp,
                                                lineHeight = 16.sp
                                            )
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }

                // Bottom Action Bar with Big "Add" Button
                Surface(
                    color = if (appColors.isDark) appColors.surface else Color.White,
                    shadowElevation = 8.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 14.dp)
                    ) {
                        Button(
                            onClick = {
                                if (selectedTab == 0) {
                                    // Add via URL
                                    if (playlistUrl.isBlank()) {
                                        Toast.makeText(context, "Please enter or select a Playlist URL", Toast.LENGTH_SHORT).show()
                                        return@Button
                                    }
                                    val finalName = playlistName.trim().ifEmpty {
                                        selectedCommunityUrl?.let { url ->
                                            CommunityPlaylists.ALL.firstOrNull { it.url == url }?.title
                                        } ?: "Custom Playlist"
                                    }
                                    onAddPlaylist(finalName, playlistUrl.trim())
                                } else {
                                    // Add via Uploaded File
                                    val fileUrl = uploadedFileUrl
                                    if (fileUrl == null) {
                                        Toast.makeText(context, "Please select a valid .m3u or .m3u8 file to upload", Toast.LENGTH_SHORT).show()
                                        return@Button
                                    }
                                    val finalName = playlistName.trim().ifEmpty {
                                        uploadedFileName?.substringBeforeLast(".") ?: "Uploaded Playlist"
                                    }
                                    onAddPlaylist(finalName, fileUrl)
                                }
                            },
                            shape = RoundedCornerShape(50),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = OttPrimary
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .testTag("btn_add_playlist_submit")
                        ) {
                            Text(
                                text = "Add",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Community Playlist Item Card with selectable highlight and auto-fill action.
 */
@Composable
private fun CommunityPlaylistCard(
    playlist: CommunityPlaylist,
    isSelected: Boolean,
    appColors: com.example.ui.theme.AppColors,
    onClick: () -> Unit
) {
    val borderColor by animateColorAsState(
        targetValue = if (isSelected) OttPrimary else if (appColors.isDark) appColors.border else Slate200,
        animationSpec = tween(durationMillis = 200),
        label = "border_color"
    )

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                if (appColors.isDark) Color(0xFF330C0E) else Color(0xFFFEF2F2)
            } else {
                if (appColors.isDark) appColors.surfaceVariant else Color(0xFFF8FAFC)
            }
        ),
        border = BorderStroke(if (isSelected) 2.dp else 1.dp, borderColor),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .testTag("community_card_${playlist.title.replace("[^a-zA-Z0-9]".toRegex(), "_")}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Play Icon Square
            Surface(
                color = if (isSelected) OttPrimary else Color(0xFFDC2626),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = playlist.title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (appColors.isDark) appColors.textPrimary else Slate900,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = playlist.url,
                    fontSize = 11.sp,
                    color = appColors.textMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (isSelected) {
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Selected",
                    tint = OttPrimary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
