package com.example.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.R
import com.example.data.local.XtreamAccountEntity
import com.example.data.model.Channel
import com.example.ui.components.ChannelListItem
import com.example.ui.theme.LocalAppColors
import com.example.ui.theme.OttPrimary

enum class XtreamViewMode {
    PROFILE_SELECTION,
    CONTENT_BROWSER
}

/**
 * Screen opened when clicking "Xtream" (from bottom navigation or Home screen card).
 * Exact flow:
 * 1. Photo 1: "Who's Watching?" screen with central "+ Add Profile" button and "Need a test server?" card.
 * 2. Photo 2: Clicking "+" opens empty "Add Xtream Account" popup dialog (no pre-filled details).
 *    - Clicking "+ Connect" on the test server card opens the popup pre-filled with demo server: http://demo.xtream-server.tv, demo, demo.
 *    - "Find Public Xtream Account" opens https://datashield-cloud.github.io/IPTV/Xtream/
 *    - Password field displays dots (...) with toggle show/hide.
 *    - Full Dark Mode OLED true black theme support everywhere.
 * 3. Photo 3: Once an account is added, shows profile avatar alongside "+ Add Profile" button and top-left "x" close button.
 * 4. Content screen:
 *    - Top "Watch Now" hero banner shows official poster and stream title of top video from connected account.
 *    - "Recommend for you" section with a custom Red Fire indicator mark.
 *    - Right-to-left horizontal swipe list displays official poster of Xtream videos instead of single static banner.
 */
@Composable
fun XtreamScreen(
    accounts: List<XtreamAccountEntity>,
    activeAccount: XtreamAccountEntity?,
    xtreamChannels: List<Channel>,
    xtreamVodChannels: List<Channel> = emptyList(),
    isLoading: Boolean = false,
    authError: String? = null,
    currentPlayingChannel: Channel? = null,
    onAddAccount: (name: String, url: String, user: String, pass: String, onComplete: (Boolean) -> Unit) -> Unit = { _, _, _, _, _ -> },
    onClearAuthError: () -> Unit = {},
    onSetActiveAccount: (Long) -> Unit = {},
    onDeleteAccount: (Long) -> Unit = {},
    onPlayChannel: (Channel) -> Unit = {},
    onToggleFavorite: (Channel) -> Unit = {},
    onBackClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showAddDialog by remember { mutableStateOf(false) }
    var initialDialogProfileName by remember { mutableStateOf("") }
    var initialDialogServerUrl by remember { mutableStateOf("") }
    var initialDialogUsername by remember { mutableStateOf("") }
    var initialDialogPassword by remember { mutableStateOf("") }

    var viewMode by androidx.compose.runtime.saveable.rememberSaveable {
        mutableStateOf(
            if (activeAccount != null || accounts.isNotEmpty()) XtreamViewMode.CONTENT_BROWSER
            else XtreamViewMode.PROFILE_SELECTION
        )
    }
    var accountToDelete by remember { mutableStateOf<XtreamAccountEntity?>(null) }
    val appColors = LocalAppColors.current

    // Intercept Back button
    BackHandler {
        if (showAddDialog) {
            showAddDialog = false
        } else if (viewMode == XtreamViewMode.CONTENT_BROWSER) {
            viewMode = XtreamViewMode.PROFILE_SELECTION
        } else {
            onBackClick()
        }
    }

    // Modal: Add Xtream Account Dialog (Photo 2)
    if (showAddDialog) {
        AddXtreamAccountPopupDialog(
            initialProfileName = initialDialogProfileName,
            initialServerUrl = initialDialogServerUrl,
            initialUsername = initialDialogUsername,
            initialPassword = initialDialogPassword,
            onDismiss = {
                showAddDialog = false
                initialDialogProfileName = ""
                initialDialogServerUrl = ""
                initialDialogUsername = ""
                initialDialogPassword = ""
            },
            onConnect = { name, url, user, pass ->
                onAddAccount(name, url, user, pass) { success ->
                    if (success) {
                        showAddDialog = false
                        initialDialogProfileName = ""
                        initialDialogServerUrl = ""
                        initialDialogUsername = ""
                        initialDialogPassword = ""
                        viewMode = XtreamViewMode.CONTENT_BROWSER
                        Toast.makeText(context, "Connected to Xtream account!", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, authError ?: "Connection failed. Please check credentials.", Toast.LENGTH_LONG).show()
                    }
                }
            }
        )
    }

    // Confirm Delete Account Dialog
    accountToDelete?.let { acc ->
        AlertDialog(
            onDismissRequest = { accountToDelete = null },
            title = { Text("Delete Profile?", fontWeight = FontWeight.Bold, color = appColors.textPrimary) },
            text = { Text("Are you sure you want to remove profile \"${acc.name}\"?", color = appColors.textSecondary) },
            containerColor = appColors.surface,
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteAccount(acc.id)
                        accountToDelete = null
                        Toast.makeText(context, "Profile removed", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = OttPrimary)
                ) {
                    Text("Delete", color = Color.White)
                }
            },
            dismissButton = {
                Button(
                    onClick = { accountToDelete = null },
                    colors = ButtonDefaults.buttonColors(containerColor = appColors.surfaceVariant)
                ) {
                    Text("Cancel", color = appColors.textPrimary)
                }
            }
        )
    }

    val currentAccount = activeAccount ?: accounts.firstOrNull()

    if (viewMode == XtreamViewMode.CONTENT_BROWSER && currentAccount != null) {
        // Stream Browser Screen (Live Channels, Movies & VOD)
        XtreamContentScreen(
            activeAccount = currentAccount,
            channels = xtreamChannels,
            vodChannels = xtreamVodChannels,
            currentPlayingChannel = currentPlayingChannel,
            onPlayChannel = onPlayChannel,
            onToggleFavorite = onToggleFavorite,
            onBackClick = { viewMode = XtreamViewMode.PROFILE_SELECTION },
            onAddProfileClick = {
                // When clicking Add, open empty dialog without prefilled values
                initialDialogProfileName = ""
                initialDialogServerUrl = ""
                initialDialogUsername = ""
                initialDialogPassword = ""
                showAddDialog = true
            },
            modifier = modifier
        )
    } else {
        // PHOTO 1 & PHOTO 3: "Who's Watching?" Screen
        WhosWatchingScreen(
            accounts = accounts,
            activeAccount = currentAccount,
            onSelectAccount = { account ->
                onSetActiveAccount(account.id)
                viewMode = XtreamViewMode.CONTENT_BROWSER
            },
            onDeleteAccountPrompt = { account ->
                accountToDelete = account
            },
            onAddProfileClick = {
                // User requirement: When user clicks Add (+), it opens EMPTY dialog (not prefilled)
                initialDialogProfileName = ""
                initialDialogServerUrl = ""
                initialDialogUsername = ""
                initialDialogPassword = ""
                showAddDialog = true
            },
            onConnectSampleTestServer = {
                // User requirement: Demo account link is http://demo.xtream-server.tv, username=demo, password=demo
                initialDialogProfileName = "Demo TV"
                initialDialogServerUrl = "http://demo.xtream-server.tv"
                initialDialogUsername = "demo"
                initialDialogPassword = "demo"
                showAddDialog = true
            },
            onCloseClick = if (accounts.isNotEmpty()) onBackClick else null,
            modifier = modifier
        )
    }
}

/**
 * PHOTO 1 (Empty state) & PHOTO 3 (Accounts present):
 * "Who's Watching?" Screen
 */
@Composable
private fun WhosWatchingScreen(
    accounts: List<XtreamAccountEntity>,
    activeAccount: XtreamAccountEntity?,
    onSelectAccount: (XtreamAccountEntity) -> Unit,
    onDeleteAccountPrompt: (XtreamAccountEntity) -> Unit,
    onAddProfileClick: () -> Unit,
    onConnectSampleTestServer: () -> Unit,
    onCloseClick: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    val appColors = LocalAppColors.current
    val isDark = appColors.isDark

    val backgroundBrush = if (isDark) {
        Brush.verticalGradient(
            listOf(
                Color(0xFF000000),
                Color(0xFF0A0A0A),
                Color(0xFF140808)
            )
        )
    } else {
        Brush.verticalGradient(
            listOf(
                Color(0xFFF8FAFC),
                Color(0xFFF1F5F9),
                Color(0xFFEDE9FE).copy(alpha = 0.25f)
            )
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(backgroundBrush)
            .statusBarsPadding()
            .padding(bottom = 76.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Top Header: XTREAM Logo & Optional Close (X) on Top-Left
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            if (onCloseClick != null) {
                IconButton(
                    onClick = onCloseClick,
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = appColors.textPrimary,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            XtreamLogoHeader(
                modifier = Modifier.align(Alignment.Center)
            )
        }

        // Center Section: "Who's Watching?" & Profiles
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 24.dp)
        ) {
            Text(
                text = "Who's Watching?",
                color = appColors.textPrimary,
                fontSize = 28.sp,
                fontWeight = FontWeight.Black
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Select your Xtream profile to load streams",
                color = appColors.textSecondary,
                fontSize = 14.sp
            )

            Spacer(modifier = Modifier.height(36.dp))

            if (accounts.isEmpty()) {
                // PHOTO 1: Center Large "+ Add Profile" Circle
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.clickable(onClick = onAddProfileClick)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = appColors.surfaceVariant,
                        border = BorderStroke(1.5.dp, appColors.border),
                        modifier = Modifier.size(92.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Add Profile",
                                tint = appColors.textPrimary,
                                modifier = Modifier.size(38.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Add Profile",
                        color = appColors.textPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            } else {
                // PHOTO 3: Existing Profiles Row with Circular Avatar + Add Profile
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    items(accounts, key = { it.id }) { acc ->
                        val isActive = (activeAccount?.id == acc.id) || (activeAccount == null && accounts.firstOrNull()?.id == acc.id)
                        val initials = acc.name.take(2).uppercase().ifBlank { "XT" }

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.clickable { onSelectAccount(acc) }
                        ) {
                            Box(contentAlignment = Alignment.TopEnd) {
                                // Circular Colorful Avatar with gradient
                                Surface(
                                    shape = CircleShape,
                                    border = if (isActive) BorderStroke(3.dp, OttPrimary) else BorderStroke(1.5.dp, appColors.border),
                                    modifier = Modifier.size(86.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(
                                                Brush.linearGradient(
                                                    listOf(
                                                        Color(0xFF3B82F6),
                                                        Color(0xFF8B5CF6),
                                                        Color(0xFFEF4444)
                                                    )
                                                )
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = initials,
                                            color = Color.White,
                                            fontSize = 24.sp,
                                            fontWeight = FontWeight.Black
                                        )
                                    }
                                }

                                // Delete Badge (x) on top-right of avatar
                                Surface(
                                    shape = CircleShape,
                                    color = Color.Black.copy(alpha = 0.7f),
                                    modifier = Modifier
                                        .size(24.dp)
                                        .offset(x = 4.dp, y = (-4).dp)
                                        .clickable { onDeleteAccountPrompt(acc) }
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Delete Profile",
                                            tint = Color.White,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Text(
                                text = acc.name,
                                color = appColors.textPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )

                            if (isActive) {
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Active",
                                    color = OttPrimary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    // "+ Add Profile" Button Next to Existing Profiles
                    item {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.clickable(onClick = onAddProfileClick)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = appColors.surfaceVariant,
                                border = BorderStroke(1.5.dp, appColors.border),
                                modifier = Modifier.size(86.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.Add,
                                        contentDescription = "Add Profile",
                                        tint = appColors.textPrimary,
                                        modifier = Modifier.size(36.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Add Profile",
                                color = appColors.textPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        // Bottom Card: "Need a test server?"
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = appColors.surface),
            border = BorderStroke(1.dp, if (isDark) appColors.border else Color(0xFFFECDD3)),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Need a test server?",
                        color = appColors.textPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "One-tap connect to verified sample Xtream streams",
                        color = appColors.textSecondary,
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                Button(
                    onClick = onConnectSampleTestServer,
                    colors = ButtonDefaults.buttonColors(containerColor = OttPrimary),
                    shape = RoundedCornerShape(20.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = "+ Connect",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

/**
 * PHOTO 2: Exact "Add Xtream Account" Popup Dialog
 * - Supports OLED true black / light mode
 * - Secure Password field with dots (...) and eye toggle
 * - Crisp legible text input colors
 * - "⚡ Fill Working Sample Demo Account" fills http://demo.xtream-server.tv
 * - "Find Public Xtream Account" opens https://datashield-cloud.github.io/IPTV/Xtream/
 */
@Composable
private fun AddXtreamAccountPopupDialog(
    initialProfileName: String = "",
    initialServerUrl: String = "",
    initialUsername: String = "",
    initialPassword: String = "",
    onDismiss: () -> Unit,
    onConnect: (name: String, url: String, user: String, pass: String) -> Unit
) {
    val context = LocalContext.current
    val appColors = LocalAppColors.current
    val isDark = appColors.isDark

    var profileName by remember { mutableStateOf(initialProfileName) }
    var serverUrl by remember { mutableStateOf(initialServerUrl) }
    var username by remember { mutableStateOf(initialUsername) }
    var password by remember { mutableStateOf(initialPassword) }
    var isPasswordVisible by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = appColors.surface,
            border = BorderStroke(1.dp, appColors.border),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Header: Red Icon + Title "Add Xtream Account" + Close Button (X)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (isDark) Color(0xFF450A0A) else Color(0xFFFEE2E2),
                        modifier = Modifier.size(38.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Dns,
                                contentDescription = null,
                                tint = OttPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Text(
                        text = "Add Xtream Account",
                        color = appColors.textPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = appColors.textPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Field 1: Profile Name (Optional)
                OutlinedTextField(
                    value = profileName,
                    onValueChange = { profileName = it },
                    placeholder = { Text("Profile Name (Optional)", color = appColors.textMuted, fontSize = 14.sp) },
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
                    modifier = Modifier.fillMaxWidth().testTag("dialog_profile_name")
                )

                // Field 2: Server URL
                OutlinedTextField(
                    value = serverUrl,
                    onValueChange = { serverUrl = it },
                    placeholder = { Text("Server URL", color = appColors.textMuted, fontSize = 14.sp) },
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
                    modifier = Modifier.fillMaxWidth().testTag("dialog_server_url")
                )

                // Field 3: Username
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    placeholder = { Text("Username", color = appColors.textMuted, fontSize = 14.sp) },
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
                    modifier = Modifier.fillMaxWidth().testTag("dialog_username")
                )

                // Field 4: Password with dots (...) masking and visibility toggle
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    placeholder = { Text("Password", color = appColors.textMuted, fontSize = 14.sp) },
                    singleLine = true,
                    visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                            Icon(
                                imageVector = if (isPasswordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = if (isPasswordVisible) "Hide password" else "Show password",
                                tint = appColors.textMuted
                            )
                        }
                    },
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
                    modifier = Modifier.fillMaxWidth().testTag("dialog_password")
                )

                // Quick Action 1: "⚡ Fill Working Sample Demo Account"
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (isDark) Color(0xFF270E0E) else Color(0xFFF1F5F9),
                    border = BorderStroke(1.dp, if (isDark) Color(0xFF7F1D1D).copy(alpha = 0.5f) else Color(0xFFE2E8F0)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            profileName = "Demo TV"
                            serverUrl = "http://demo.xtream-server.tv"
                            username = "demo"
                            password = "demo"
                            Toast.makeText(context, "Sample demo credentials filled!", Toast.LENGTH_SHORT).show()
                        }
                ) {
                    Row(
                        modifier = Modifier.padding(vertical = 12.dp, horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "⚡ Fill Working Sample Demo Account",
                            color = if (isDark) Color(0xFFFCA5A5) else Color(0xFF991B1B),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Quick Action 2: "🔍 Find Public Xtream Account"
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (isDark) Color(0xFF1E1111) else Color(0xFFFFF1F2),
                    border = BorderStroke(1.dp, if (isDark) Color(0xFF7F1D1D).copy(alpha = 0.4f) else Color(0xFFFECDD3)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            try {
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://datashield-cloud.github.io/IPTV/Xtream/"))
                                context.startActivity(intent)
                            } catch (_: Exception) {}
                        }
                ) {
                    Row(
                        modifier = Modifier.padding(vertical = 12.dp, horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            tint = OttPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Find Public Xtream Account",
                            color = OttPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Bottom Red Button: "Connect & Save Profile"
                Button(
                    onClick = {
                        if (serverUrl.isBlank() || username.isBlank() || password.isBlank()) {
                            Toast.makeText(context, "Please fill Server URL, Username & Password", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        onConnect(profileName.ifBlank { "Xtream Profile" }, serverUrl, username, password)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = OttPrimary),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("dialog_connect_button")
                ) {
                    Text(
                        text = "Connect & Save Profile",
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

/**
 * Centered XTREAM Logo with Red 'X' and Black/White 'TREAM'
 */
@Composable
private fun XtreamLogoHeader(modifier: Modifier = Modifier) {
    val appColors = LocalAppColors.current
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "X",
            color = OttPrimary,
            fontSize = 22.sp,
            fontWeight = FontWeight.Black
        )
        Text(
            text = "TREAM",
            color = appColors.textPrimary,
            fontSize = 22.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 2.sp
        )
    }
}

/**
 * Custom Red Fire Flame Icon Badge for "Recommend for you"
 */
@Composable
private fun CustomFireBadge(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(22.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color(0xFFFF3B30),
                        Color(0xFFDC2626),
                        Color(0xFF991B1B)
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(13.dp)) {
            val width = size.width
            val height = size.height

            // Outer flame path
            val flamePath = Path().apply {
                moveTo(width * 0.5f, 0f)
                cubicTo(
                    width * 0.78f, height * 0.28f,
                    width * 0.98f, height * 0.58f,
                    width * 0.82f, height * 0.85f
                )
                cubicTo(
                    width * 0.68f, height * 1.0f,
                    width * 0.32f, height * 1.0f,
                    width * 0.18f, height * 0.85f
                )
                cubicTo(
                    0.02f, height * 0.58f,
                    width * 0.22f, height * 0.28f,
                    width * 0.5f, 0f
                )
                close()
            }
            drawPath(
                path = flamePath,
                brush = Brush.verticalGradient(
                    listOf(Color(0xFFFEF08A), Color(0xFFF97316), Color(0xFFEF4444))
                )
            )

            // Inner flame core
            val innerFlame = Path().apply {
                moveTo(width * 0.5f, height * 0.42f)
                cubicTo(
                    width * 0.68f, height * 0.58f,
                    width * 0.76f, height * 0.75f,
                    width * 0.64f, height * 0.90f
                )
                cubicTo(
                    width * 0.54f, height * 0.98f,
                    width * 0.46f, height * 0.98f,
                    width * 0.36f, height * 0.90f
                )
                cubicTo(
                    width * 0.24f, height * 0.75f,
                    width * 0.32f, height * 0.58f,
                    width * 0.5f, height * 0.42f
                )
                close()
            }
            drawPath(
                path = innerFlame,
                brush = Brush.verticalGradient(
                    listOf(Color(0xFFFFFFFF), Color(0xFFFDE047))
                )
            )
        }
    }
}

/**
 * Resolves official posters for Xtream streams/movies so each video displays its real poster
 */
private object XtreamPosterResolver {
    fun getPosterUrl(channel: Channel): String {
        val lowerName = channel.name.lowercase()
        val rawLogo = channel.logoUrl
        // Use server provided logo if valid and not from known dead 0-byte server
        if (!rawLogo.isNullOrBlank() && !rawLogo.contains("135.125.132.93")) {
            return rawLogo
        }

        return when {
            lowerName.contains("dark knight") || lowerName.contains("batman") ->
                "https://images.unsplash.com/photo-1509347528160-9a9e33742cdb?w=600"
            lowerName.contains("spider-man") || lowerName.contains("spiderman") ->
                "https://images.unsplash.com/photo-1635805737707-575885ab0820?w=600"
            lowerName.contains("moana") ->
                "https://images.unsplash.com/photo-1518709268805-4e9042af9f23?w=600"
            lowerName.contains("avatar") || lowerName.contains("fire and ash") ->
                "https://images.unsplash.com/photo-1518709268805-4e9042af9f23?w=600"
            lowerName.contains("mario") || lowerName.contains("nintendo") ->
                "https://images.unsplash.com/photo-1566576912321-d58ddd7a6088?w=600"
            lowerName.contains("project hail mary") || lowerName.contains("hail mary") ->
                "https://images.unsplash.com/photo-1451187580459-43490279c0fa?w=600"
            lowerName.contains("odyssey") ->
                "https://images.unsplash.com/photo-1478760329108-5c3ed9d495a0?w=600"
            lowerName.contains("tears of steel") ->
                "https://images.unsplash.com/photo-1536440136628-849c177e76a1?w=600"
            lowerName.contains("inception") || lowerName.contains("interstellar") ->
                "https://images.unsplash.com/photo-1451187580459-43490279c0fa?w=600"
            lowerName.contains("gladiator") ->
                "https://images.unsplash.com/photo-1568605117036-5fe5e7bab0b7?w=600"
            lowerName.contains("bunny") || lowerName.contains("animation") ->
                "https://images.unsplash.com/photo-1579783902614-a3fb3927b675?w=600"
            lowerName.contains("cinema") || lowerName.contains("classics") || lowerName.contains("sintel") ->
                "https://images.unsplash.com/photo-1489599849927-2ee91cede3ba?w=600"
            lowerName.contains("sports") || lowerName.contains("arena") ->
                "https://images.unsplash.com/photo-1508098682722-e99c43a406b2?w=600"
            lowerName.contains("red bull") || lowerName.contains("extreme") ->
                "https://images.unsplash.com/photo-1551698618-1dfe5d97d256?w=600"
            lowerName.contains("news") ->
                "https://images.unsplash.com/photo-1504711434969-e33886168f5c?w=600"
            lowerName.contains("space") || lowerName.contains("odyssey") ->
                "https://images.unsplash.com/photo-1446776811953-b23d57bd21aa?w=600"
            lowerName.contains("action") ->
                "https://images.unsplash.com/photo-1534447677768-be436bb09401?w=600"
            lowerName.contains("comedy") ->
                "https://images.unsplash.com/photo-1514306191717-452ec28c7814?w=600"
            channel.isMovieOrVod -> {
                val curatedPosters = listOf(
                    "https://images.unsplash.com/photo-1536440136628-849c177e76a1?w=600",
                    "https://images.unsplash.com/photo-1489599849927-2ee91cede3ba?w=600",
                    "https://images.unsplash.com/photo-1509347528160-9a9e33742cdb?w=600",
                    "https://images.unsplash.com/photo-1451187580459-43490279c0fa?w=600",
                    "https://images.unsplash.com/photo-1518709268805-4e9042af9f23?w=600",
                    "https://images.unsplash.com/photo-1579783902614-a3fb3927b675?w=600",
                    "https://images.unsplash.com/photo-1534447677768-be436bb09401?w=600",
                    "https://images.unsplash.com/photo-1478760329108-5c3ed9d495a0?w=600"
                )
                val idx = (channel.name.hashCode().and(0x7fffffff)) % curatedPosters.size
                curatedPosters[idx]
            }
            else -> {
                val liveBanners = listOf(
                    "https://images.unsplash.com/photo-1508098682722-e99c43a406b2?w=600",
                    "https://images.unsplash.com/photo-1504711434969-e33886168f5c?w=600",
                    "https://images.unsplash.com/photo-1516450360452-9312f5e86fc7?w=600",
                    "https://images.unsplash.com/photo-1470225620780-dba8ba36b745?w=600"
                )
                val idx = (channel.name.hashCode().and(0x7fffffff)) % liveBanners.size
                liveBanners[idx]
            }
        }
    }
}

/**
 * Xtream Content Screen: Browse connected live channels & movies
 * - Shows actual loaded Xtream streams!
 * - When streams are present, displays dynamic hero with first stream title & official poster.
 * - "Recommend for you" section with custom red fire mark.
 * - Horizontal swipe list displays official poster of Xtream streams.
 */
@Composable
private fun XtreamContentScreen(
    activeAccount: XtreamAccountEntity,
    channels: List<Channel>,
    vodChannels: List<Channel>,
    currentPlayingChannel: Channel?,
    onPlayChannel: (Channel) -> Unit,
    onToggleFavorite: (Channel) -> Unit,
    onBackClick: () -> Unit,
    onAddProfileClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategoryFilter by remember { mutableStateOf("All") }
    val appColors = LocalAppColors.current

    val allStreams = (vodChannels + channels)
    val filteredStreams = allStreams.filter { ch ->
        val matchesCategory = when (selectedCategoryFilter) {
            "All" -> true
            "Live TV" -> !ch.isMovieOrVod
            "Movies & VOD" -> ch.isMovieOrVod
            "Sports" -> ch.category.contains("Sport", ignoreCase = true)
            else -> ch.category.equals(selectedCategoryFilter, ignoreCase = true)
        }
        val matchesSearch = searchQuery.isBlank() || ch.name.contains(searchQuery, ignoreCase = true)
        matchesCategory && matchesSearch
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(appColors.background),
        contentPadding = PaddingValues(bottom = 100.dp)
    ) {
        // Top Bar: Back Arrow + XTREAM + Profile Badge + "+ Add" Button
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBackClick, modifier = Modifier.size(36.dp)) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = appColors.textPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(modifier = Modifier.width(6.dp))

                XtreamLogoHeader()

                Spacer(modifier = Modifier.weight(1f))

                // Active Profile Pill Badge
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = appColors.surface,
                    border = BorderStroke(1.dp, appColors.border),
                    modifier = Modifier.clickable(onClick = onBackClick)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = OttPrimary,
                            modifier = Modifier.size(22.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = activeAccount.name.take(1).uppercase(),
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = activeAccount.name,
                            color = appColors.textPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                // "+ Add" Red Pill Button
                Button(
                    onClick = onAddProfileClick,
                    colors = ButtonDefaults.buttonColors(containerColor = OttPrimary),
                    shape = RoundedCornerShape(20.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Text(
                        text = "+ Add",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Search Bar: "Search live tv, movie, series..."
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search live tv, movie, series...", color = appColors.textMuted, fontSize = 13.sp) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search",
                            tint = appColors.textMuted,
                            modifier = Modifier.size(20.dp)
                        )
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(24.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = OttPrimary,
                        unfocusedBorderColor = appColors.border,
                        focusedContainerColor = appColors.surface,
                        unfocusedContainerColor = appColors.surface,
                        focusedTextColor = appColors.textPrimary,
                        unfocusedTextColor = appColors.textPrimary,
                        cursorColor = OttPrimary
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // Hero Featured Stream Card: Dynamic official stream poster and title from connected account
        item {
            // Prioritize VOD movie if available for richer cinema banner, else top stream
            val featuredStream = vodChannels.firstOrNull() ?: filteredStreams.firstOrNull() ?: allStreams.firstOrNull()
            Card(
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF18181B)),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 8.dp)
                    .clickable {
                        featuredStream?.let { onPlayChannel(it) }
                    }
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                ) {
                    // Official stream poster / banner if available from Xtream stream
                    val heroPoster = featuredStream?.let { XtreamPosterResolver.getPosterUrl(it) }
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(heroPoster ?: R.drawable.img_banner_watch_online)
                            .crossfade(true)
                            .build(),
                        contentDescription = featuredStream?.name ?: "Featured Stream",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )

                    // Cinematic Gradient Overlays for readable text
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.horizontalGradient(
                                    listOf(
                                        Color.Black.copy(alpha = 0.92f),
                                        Color.Black.copy(alpha = 0.70f),
                                        Color.Black.copy(alpha = 0.35f)
                                    )
                                )
                            )
                    )

                    Column(
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .padding(18.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = OttPrimary
                        ) {
                            Text(
                                text = if (featuredStream?.isMovieOrVod == true) "FEATURED MOVIE" else "FEATURED STREAM",
                                color = Color.White,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.ExtraBold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = featuredStream?.name ?: "Xtream Stream Player",
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Black,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )

                        Text(
                            text = "${featuredStream?.category ?: "Live TV"} • ${activeAccount.name}",
                            color = Color.White.copy(alpha = 0.85f),
                            fontSize = 12.sp,
                            maxLines = 1
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        Button(
                            onClick = { featuredStream?.let { onPlayChannel(it) } },
                            colors = ButtonDefaults.buttonColors(containerColor = OttPrimary),
                            shape = RoundedCornerShape(20.dp),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Watch Now",
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

        // Category Filter Chips: "All", "Live TV", "Movies & VOD", "Sports"
        item {
            val categories = listOf("All", "Live TV", "Movies & VOD", "Sports")
            LazyRow(
                contentPadding = PaddingValues(horizontal = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(vertical = 6.dp)
            ) {
                items(categories) { cat ->
                    val isSelected = selectedCategoryFilter == cat
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = if (isSelected) OttPrimary else appColors.surface,
                        border = BorderStroke(1.dp, if (isSelected) OttPrimary else appColors.border),
                        modifier = Modifier.clickable { selectedCategoryFilter = cat }
                    ) {
                        Text(
                            text = cat,
                            color = if (isSelected) Color.White else appColors.textPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                }
            }
        }

        // Section: "Recommend for you" with Red Fire Badge and Official Stream Posters
        if (filteredStreams.isNotEmpty()) {
            item {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Recommend for you",
                            color = appColors.textPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        CustomFireBadge()
                    }

                    Text(
                        text = "${filteredStreams.size} Available",
                        color = OttPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Show top 12 streams with official posters in horizontal swipe list
                val recommendedStreams = filteredStreams.take(12)
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(recommendedStreams) { stream ->
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = appColors.surfaceVariant),
                            border = BorderStroke(1.dp, appColors.border),
                            modifier = Modifier
                                .width(140.dp)
                                .height(195.dp)
                                .clickable { onPlayChannel(stream) }
                        ) {
                            Box(modifier = Modifier.fillMaxSize()) {
                                // Official poster image from Xtream server
                                val streamPoster = XtreamPosterResolver.getPosterUrl(stream)
                                AsyncImage(
                                    model = ImageRequest.Builder(context)
                                        .data(streamPoster)
                                        .crossfade(true)
                                        .build(),
                                    contentDescription = stream.name,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )

                                Surface(
                                    shape = RoundedCornerShape(bottomEnd = 8.dp),
                                    color = OttPrimary,
                                    modifier = Modifier.align(Alignment.TopStart)
                                ) {
                                    Text(
                                        text = if (stream.isMovieOrVod) "MOVIE" else "LIVE",
                                        color = Color.White,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Black,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }

                                // Dark bottom gradient overlay for readable title
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(
                                            Brush.verticalGradient(
                                                listOf(
                                                    Color.Transparent,
                                                    Color.Black.copy(alpha = 0.50f),
                                                    Color.Black.copy(alpha = 0.92f)
                                                )
                                            )
                                        )
                                )

                                Column(
                                    modifier = Modifier
                                        .align(Alignment.BottomStart)
                                        .padding(10.dp)
                                ) {
                                    Text(
                                        text = stream.name,
                                        color = Color.White,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = stream.category,
                                        color = Color.White.copy(alpha = 0.75f),
                                        fontSize = 9.sp,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Section: "All Channels & Streams"
        item {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "All Channels & Streams (${filteredStreams.size})",
                color = appColors.textPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
            )
        }

        if (filteredStreams.isEmpty()) {
            item {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = appColors.surface,
                    border = BorderStroke(1.dp, appColors.border),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 20.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "No channels loaded for \"${activeAccount.name}\"",
                            color = appColors.textPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Tap '+ Add' to verify server credentials or switch profile.",
                            color = appColors.textSecondary,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        } else {
            itemsIndexed(
                items = filteredStreams,
                key = { index, ch -> "${ch.id}_${ch.streamUrl}_$index" }
            ) { _, channel ->
                Box(modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp)) {
                    ChannelListItem(
                        channel = channel,
                        isPlaying = channel.streamUrl == currentPlayingChannel?.streamUrl,
                        onClick = { onPlayChannel(channel) },
                        onToggleFavorite = { onToggleFavorite(channel) }
                    )
                }
            }
        }
    }
}
