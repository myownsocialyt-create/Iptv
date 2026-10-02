package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.data.local.XtreamAccountEntity
import com.example.data.model.Channel
import com.example.ui.components.ChannelCard
import com.example.ui.components.ChannelListItem
import com.example.ui.theme.LocalAppColors
import com.example.ui.theme.OttGold
import com.example.ui.theme.OttLiveRed
import com.example.ui.theme.OttPrimary
import com.example.ui.theme.Slate100
import com.example.ui.theme.Slate200
import com.example.ui.theme.Slate400
import com.example.ui.theme.Slate500
import com.example.ui.theme.Slate700
import com.example.ui.theme.Slate900

@Composable
fun XtreamScreen(
    accounts: List<XtreamAccountEntity>,
    activeAccount: XtreamAccountEntity?,
    xtreamChannels: List<Channel>,
    xtreamVodChannels: List<Channel> = emptyList(),
    isLoading: Boolean,
    authError: String? = null,
    currentPlayingChannel: Channel?,
    onAddAccount: (name: String, url: String, user: String, pass: String, onComplete: (Boolean, String) -> Unit) -> Unit,
    onClearAuthError: () -> Unit = {},
    onSetActiveAccount: (String) -> Unit,
    onDeleteAccount: (String) -> Unit,
    onPlayChannel: (Channel) -> Unit,
    onToggleFavorite: (Channel) -> Unit,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val appColors = LocalAppColors.current

    // Animated ambient gradient background
    val infiniteTransition = rememberInfiniteTransition(label = "gradientAnim")
    val gradientShift by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 8000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "gradientShift"
    )

    val animatedGradient = Brush.linearGradient(
        colors = if (appColors.isDark) {
            listOf(
                Color(0xFF090814),
                Color(0xFF131735),
                Color(0xFF0E1728),
                Color(0xFF080B13)
            )
        } else {
            listOf(
                Color(0xFFF1F5F9),
                Color(0xFFE2E8F0),
                Color(0xFFEDE9FE),
                Color(0xFFF8FAFC)
            )
        },
        start = Offset(0f, gradientShift * 600f),
        end = Offset(1000f, (1f - gradientShift) * 1000f)
    )

    // State
    var showAddDialog by remember { mutableStateOf(false) }
    var isProfileSelectorOpen by remember { mutableStateOf(accounts.isEmpty() || activeAccount == null) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategoryTab by remember { mutableStateOf("All") }

    // If no accounts exist, open profile selector by default
    val isInProfileView = isProfileSelectorOpen || accounts.isEmpty()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(animatedGradient)
            .testTag("xtream_screen")
    ) {
        if (isInProfileView) {
            // View 1: "Who's Watching?" Profile Selector (Inspired by Photo 1)
            XtreamProfileSelectorView(
                accounts = accounts,
                activeAccount = activeAccount,
                onSelectProfile = { profile ->
                    onSetActiveAccount(profile.id)
                    isProfileSelectorOpen = false
                },
                onAddProfileClick = { showAddDialog = true },
                onDeleteProfile = onDeleteAccount,
                onBackClick = { isProfileSelectorOpen = false },
                isDark = appColors.isDark
            )
        } else {
            // View 2: Content Hub View (Inspired by Photo 2)
            XtreamContentHubView(
                activeAccount = activeAccount,
                channels = xtreamChannels,
                vodChannels = xtreamVodChannels,
                isLoading = isLoading,
                currentPlayingChannel = currentPlayingChannel,
                searchQuery = searchQuery,
                onSearchQueryChange = { searchQuery = it },
                selectedCategory = selectedCategoryTab,
                onCategorySelect = { selectedCategoryTab = it },
                onPlayChannel = onPlayChannel,
                onToggleFavorite = onToggleFavorite,
                onOpenProfiles = { isProfileSelectorOpen = true },
                onAddNewAccount = { showAddDialog = true }
            )
        }

        // Add Account / Profile Dialog
        if (showAddDialog) {
            AddXtreamAccountDialog(
                isLoading = isLoading,
                authError = authError,
                onDismiss = {
                    showAddDialog = false
                    onClearAuthError()
                },
                onConnect = { name, url, user, pass ->
                    onAddAccount(name, url, user, pass) { success, _ ->
                        if (success) {
                            showAddDialog = false
                            isProfileSelectorOpen = false
                        }
                    }
                }
            )
        }
    }
}

/**
 * "Who's Watching?" Profile Selector View (Matches User Reference Image 1)
 */
@Composable
private fun XtreamProfileSelectorView(
    accounts: List<XtreamAccountEntity>,
    activeAccount: XtreamAccountEntity?,
    onSelectProfile: (XtreamAccountEntity) -> Unit,
    onAddProfileClick: () -> Unit,
    onDeleteProfile: (String) -> Unit,
    onBackClick: () -> Unit,
    isDark: Boolean
) {
    val appColors = LocalAppColors.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Top Navigation Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            if (activeAccount != null) {
                IconButton(
                    onClick = onBackClick,
                    modifier = Modifier
                        .size(40.dp)
                        .testTag("btn_profile_back")
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = appColors.textPrimary
                    )
                }
            } else {
                Spacer(modifier = Modifier.size(40.dp))
            }

            XtreamLogoHeader()

            Spacer(modifier = Modifier.size(40.dp))
        }

        Spacer(modifier = Modifier.height(36.dp))

        // Headline: "Who's Watching?"
        Text(
            text = "Who's Watching?",
            fontSize = 28.sp,
            fontWeight = FontWeight.Black,
            color = appColors.textPrimary,
            letterSpacing = 0.5.sp,
            modifier = Modifier.testTag("text_whos_watching")
        )

        Text(
            text = "Select your Xtream profile to load streams",
            fontSize = 14.sp,
            color = appColors.textSecondary,
            modifier = Modifier.padding(top = 6.dp)
        )

        Spacer(modifier = Modifier.height(48.dp))

        // Profiles Grid / Row
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            contentPadding = PaddingValues(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            items(accounts) { account ->
                val isCurrent = account.id == activeAccount?.id
                ProfileAvatarItem(
                    account = account,
                    isSelected = isCurrent,
                    onClick = { onSelectProfile(account) },
                    onDelete = { onDeleteProfile(account.id) },
                    isDark = isDark
                )
            }

            // Circular "+ Add Profile" Button
            item {
                AddProfileAvatarButton(onClick = onAddProfileClick, isDark = isDark)
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        // Quick Working Demo Profile helper (so user never gets stuck!)
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (isDark) Color(0xFF1B2236) else Color(0xFFE2E8F0)
            ),
            border = BorderStroke(1.dp, OttPrimary.copy(alpha = 0.3f)),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Need a test server?",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = appColors.textPrimary
                    )
                    Text(
                        text = "One-tap connect to verified sample Xtream streams",
                        fontSize = 12.sp,
                        color = appColors.textSecondary
                    )
                }

                Button(
                    onClick = onAddProfileClick,
                    colors = ButtonDefaults.buttonColors(containerColor = OttPrimary),
                    shape = RoundedCornerShape(20.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                    modifier = Modifier.testTag("btn_connect_demo_helper")
                ) {
                    Text(
                        text = "+ Connect",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }
    }
}

/**
 * Single Profile Avatar Item (Matches Reference Image 1)
 */
@Composable
private fun ProfileAvatarItem(
    account: XtreamAccountEntity,
    isSelected: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    isDark: Boolean
) {
    val appColors = LocalAppColors.current

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(96.dp)
    ) {
        Box(contentAlignment = Alignment.TopEnd) {
            Surface(
                onClick = onClick,
                shape = CircleShape,
                color = Color.Transparent,
                border = BorderStroke(
                    width = if (isSelected) 3.dp else 1.5.dp,
                    color = if (isSelected) OttPrimary else if (isDark) Color(0xFF3B4866) else Color(0xFFCBD5E1)
                ),
                modifier = Modifier
                    .size(80.dp)
                    .testTag("profile_avatar_${account.id}")
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.linearGradient(
                                colors = listOf(Color(0xFF38BDF8), Color(0xFF818CF8), Color(0xFFE50914))
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    val initials = account.name.trim().take(2).uppercase()
                    Text(
                        text = if (initials.isNotBlank()) initials else "XT",
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Black,
                        color = Color.White
                    )
                }
            }

            // Delete badge on avatar
            IconButton(
                onClick = onDelete,
                modifier = Modifier
                    .size(26.dp)
                    .background(Color.Black.copy(alpha = 0.7f), CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Delete Profile",
                    tint = Color.White,
                    modifier = Modifier.size(14.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Profile Name (e.g. "ok")
        Text(
            text = account.name,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = appColors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )

        // Status badge
        Text(
            text = if (isSelected) "Active" else "Ready",
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (isSelected) OttPrimary else appColors.textSecondary
        )
    }
}

/**
 * Circular "+ Add Profile" Button (Matches Reference Image 1)
 */
@Composable
private fun AddProfileAvatarButton(
    onClick: () -> Unit,
    isDark: Boolean
) {
    val appColors = LocalAppColors.current

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(96.dp)
    ) {
        Surface(
            onClick = onClick,
            shape = CircleShape,
            color = if (isDark) Color(0xFF1E2638) else Color(0xFFE2E8F0),
            border = BorderStroke(
                1.5.dp,
                if (isDark) Color(0xFF475569) else Color(0xFF94A3B8)
            ),
            modifier = Modifier
                .size(80.dp)
                .testTag("btn_add_profile_circle")
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

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = "Add Profile",
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = appColors.textPrimary,
            maxLines = 1
        )
    }
}

/**
 * Content Hub View (Matches User Reference Image 2)
 */
@Composable
private fun XtreamContentHubView(
    activeAccount: XtreamAccountEntity?,
    channels: List<Channel>,
    vodChannels: List<Channel>,
    isLoading: Boolean,
    currentPlayingChannel: Channel?,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    selectedCategory: String,
    onCategorySelect: (String) -> Unit,
    onPlayChannel: (Channel) -> Unit,
    onToggleFavorite: (Channel) -> Unit,
    onOpenProfiles: () -> Unit,
    onAddNewAccount: () -> Unit
) {
    val appColors = LocalAppColors.current

    // Combine all streams for filtering
    val allItems = remember(channels, vodChannels) {
        channels + vodChannels
    }

    val filteredItems = remember(allItems, searchQuery, selectedCategory) {
        var list = allItems

        if (selectedCategory != "All") {
            list = when (selectedCategory) {
                "Live TV" -> list.filter { !it.id.startsWith("vod_") }
                "Movies & VOD" -> list.filter { it.id.startsWith("vod_") || it.category.contains("Movie", ignoreCase = true) }
                "Sports" -> list.filter { it.category.contains("Sport", ignoreCase = true) }
                "News" -> list.filter { it.category.contains("News", ignoreCase = true) }
                "Entertainment" -> list.filter { it.category.contains("Entertainment", ignoreCase = true) }
                else -> list.filter { it.category.equals(selectedCategory, ignoreCase = true) }
            }
        }

        if (searchQuery.isNotBlank()) {
            list = list.filter {
                it.name.contains(searchQuery, ignoreCase = true) ||
                    it.category.contains(searchQuery, ignoreCase = true)
            }
        }

        list
    }

    // Featured hero item (first available item or a fallback showcase)
    val featuredItem = remember(channels, vodChannels) {
        vodChannels.firstOrNull() ?: channels.firstOrNull()
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 96.dp)
    ) {
        // 1. Top Header Bar (Back button, XTREAM brand, Profile Avatar pill, and Add Account Plus button)
        item {
            XtreamHubTopHeader(
                activeAccount = activeAccount,
                onBackClick = onOpenProfiles,
                onProfileClick = onOpenProfiles,
                onAddAccountClick = onAddNewAccount
            )
        }

        // 2. Search Bar
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChange,
                    placeholder = {
                        Text(
                            text = "Search live tv, movie, series...",
                            fontSize = 14.sp,
                            color = appColors.textSecondary
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            tint = appColors.textSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotBlank()) {
                            IconButton(onClick = { onSearchQueryChange("") }) {
                                Icon(
                                    imageVector = Icons.Default.Clear,
                                    contentDescription = "Clear",
                                    tint = appColors.textSecondary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(24.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = appColors.surfaceVariant,
                        unfocusedContainerColor = appColors.surfaceVariant,
                        focusedBorderColor = OttPrimary,
                        unfocusedBorderColor = appColors.border,
                        focusedTextColor = appColors.textPrimary,
                        unfocusedTextColor = appColors.textPrimary
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("xtream_search_input")
                )
            }
        }

        // 3. Featured Hero Showcase Banner (Matches Reference Image 2)
        if (featuredItem != null && searchQuery.isBlank()) {
            item {
                FeaturedHeroBanner(
                    channel = featuredItem,
                    onPlay = { onPlayChannel(featuredItem) }
                )
            }
        }

        // 4. Category Filter Tabs
        item {
            val categories = listOf("All", "Live TV", "Movies & VOD", "Sports", "News", "Entertainment")
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp),
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(categories) { cat ->
                    val isCatSelected = selectedCategory == cat
                    Surface(
                        onClick = { onCategorySelect(cat) },
                        shape = RoundedCornerShape(20.dp),
                        color = if (isCatSelected) OttPrimary else appColors.surfaceVariant,
                        border = BorderStroke(
                            1.dp,
                            if (isCatSelected) OttPrimary else appColors.border
                        )
                    ) {
                        Text(
                            text = cat,
                            fontSize = 13.sp,
                            fontWeight = if (isCatSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isCatSelected) Color.White else appColors.textPrimary,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                }
            }
        }

        // 5. "Recommended For You" Horizontal Carousel (Matches Reference Image 2)
        if (allItems.isNotEmpty() && searchQuery.isBlank() && selectedCategory == "All") {
            item {
                Column(modifier = Modifier.padding(top = 10.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Recommended For You",
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                color = appColors.textPrimary
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(
                                imageVector = Icons.Default.Whatshot,
                                contentDescription = null,
                                tint = OttPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        Text(
                            text = "More >>",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = OttPrimary,
                            modifier = Modifier.clickable { onCategorySelect("Movies & VOD") }
                        )
                    }

                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(allItems.take(10)) { item ->
                            RecommendedPosterCard(
                                channel = item,
                                onClick = { onPlayChannel(item) }
                            )
                        }
                    }
                }
            }
        }

        // 6. Section Header
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (selectedCategory == "All") "All Channels & Streams (${filteredItems.size})" else "$selectedCategory (${filteredItems.size})",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = appColors.textPrimary
                )

                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = OttPrimary,
                        strokeWidth = 2.dp
                    )
                }
            }
        }

        // 7. Stream Items List / Empty State
        if (filteredItems.isEmpty()) {
            item {
                XtreamEmptyStreamsCard(
                    isLoading = isLoading,
                    activeAccount = activeAccount,
                    onOpenProfiles = onOpenProfiles,
                    onAddNewAccount = onAddNewAccount
                )
            }
        } else {
            items(filteredItems) { channel ->
                val isPlaying = currentPlayingChannel?.id == channel.id
                ChannelListItem(
                    channel = channel,
                    isPlaying = isPlaying,
                    onClick = { onPlayChannel(channel) },
                    onToggleFavorite = { onToggleFavorite(channel) },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }
        }
    }
}

/**
 * Top Header for Content Hub
 */
@Composable
private fun XtreamHubTopHeader(
    activeAccount: XtreamAccountEntity?,
    onBackClick: () -> Unit,
    onProfileClick: () -> Unit,
    onAddAccountClick: () -> Unit = {}
) {
    val appColors = LocalAppColors.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        // Back button
        IconButton(
            onClick = onBackClick,
            modifier = Modifier.size(36.dp).testTag("btn_hub_back")
        ) {
            Icon(
                imageVector = Icons.Default.ArrowBack,
                contentDescription = "Back to Profiles",
                tint = appColors.textPrimary
            )
        }

        // Branded Logo
        XtreamLogoHeader()

        // Profile Avatar Badge Pill & Add Account (+) Button
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Profile Avatar Badge Pill (e.g. [D] Demo TV)
            Surface(
                onClick = onProfileClick,
                shape = RoundedCornerShape(20.dp),
                color = appColors.surfaceVariant,
                border = BorderStroke(1.dp, OttPrimary.copy(alpha = 0.5f)),
                modifier = Modifier.testTag("btn_hub_profile_pill")
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
                                text = (activeAccount?.name ?: "P").take(1).uppercase(),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = activeAccount?.name ?: "Profile",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = appColors.textPrimary,
                        maxLines = 1
                    )
                }
            }

            // Top Plus (+) Button to Add Account directly
            Surface(
                onClick = onAddAccountClick,
                shape = RoundedCornerShape(16.dp),
                color = OttPrimary,
                shadowElevation = 3.dp,
                modifier = Modifier.testTag("btn_hub_add_account_plus")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Add Xtream Account",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = "Add",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }
    }
}

/**
 * Branded XTREAM Logo Header
 */
@Composable
private fun XtreamLogoHeader() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = "X",
            fontSize = 24.sp,
            fontWeight = FontWeight.Black,
            color = OttPrimary,
            letterSpacing = 1.sp
        )
        Text(
            text = "TREAM",
            fontSize = 20.sp,
            fontWeight = FontWeight.Black,
            color = LocalAppColors.current.textPrimary,
            letterSpacing = 2.sp
        )
    }
}

/**
 * Large Featured Showcase Banner (Matches Reference Image 2)
 */
@Composable
private fun FeaturedHeroBanner(
    channel: Channel,
    onPlay: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp)
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .testTag("hero_featured_banner")
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Background Artwork
            if (!channel.logoUrl.isNullOrBlank()) {
                AsyncImage(
                    model = channel.logoUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }

            // Dark Scrim Gradient
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.90f),
                                Color.Black.copy(alpha = 0.60f),
                                Color.Transparent
                            )
                        )
                    )
            )

            // Content Overlay
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.Center
            ) {
                // "FEATURED" pill
                Surface(
                    color = OttPrimary,
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier.wrapContentHeight()
                ) {
                    Text(
                        text = "FEATURED",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = channel.name,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Text(
                    text = "${channel.category} • 4K HD",
                    fontSize = 11.sp,
                    color = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.padding(top = 2.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = onPlay,
                    colors = ButtonDefaults.buttonColors(containerColor = OttPrimary),
                    shape = RoundedCornerShape(20.dp),
                    contentPadding = PaddingValues(horizontal = 18.dp, vertical = 6.dp),
                    modifier = Modifier.testTag("btn_hero_watch_now")
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Watch Now",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }
    }
}

/**
 * Recommended Poster Card (Matches Reference Image 2)
 */
@Composable
private fun RecommendedPosterCard(
    channel: Channel,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
        modifier = Modifier
            .width(130.dp)
            .height(190.dp)
            .testTag("recommended_card_${channel.id}")
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Artwork
            if (!channel.logoUrl.isNullOrBlank()) {
                AsyncImage(
                    model = channel.logoUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color(0xFF1E293B), Color(0xFF0F172A))
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    androidx.compose.foundation.Image(
                        painter = androidx.compose.ui.res.painterResource(id = com.example.R.drawable.ic_iptv_logo),
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.size(36.dp)
                    )
                }
            }

            // Top-left "HOT" badge
            Surface(
                color = OttLiveRed,
                shape = RoundedCornerShape(topStart = 14.dp, bottomEnd = 8.dp),
                modifier = Modifier.align(Alignment.TopStart)
            ) {
                Text(
                    text = "HOT",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }

            // Bottom title scrim
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.95f))
                        )
                    )
                    .padding(8.dp)
            ) {
                Column {
                    Text(
                        text = channel.name,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = channel.category,
                        fontSize = 10.sp,
                        color = Color.White.copy(alpha = 0.65f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

/**
 * Empty state when a connected Xtream server contains no channels.
 * Prevents showing fake content on invalid URLs!
 */
@Composable
private fun XtreamEmptyStreamsCard(
    isLoading: Boolean,
    activeAccount: XtreamAccountEntity?,
    onOpenProfiles: () -> Unit,
    onAddNewAccount: () -> Unit
) {
    val appColors = LocalAppColors.current

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = appColors.surfaceVariant),
        border = BorderStroke(1.dp, appColors.border),
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .testTag("card_xtream_empty_streams")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.ErrorOutline,
                contentDescription = null,
                tint = OttPrimary,
                modifier = Modifier.size(44.dp)
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = if (isLoading) "Loading streams from Xtream server..." else "No Streams Found On This Server",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = appColors.textPrimary,
                textAlign = TextAlign.Center
            )

            Text(
                text = if (isLoading) "Please wait while we connect and fetch live channels and VODs..." else "The server responded, but no active streams were found. Check your subscription package or server URL.",
                fontSize = 13.sp,
                color = appColors.textSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 6.dp)
            )

            Spacer(modifier = Modifier.height(18.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = onOpenProfiles,
                    colors = ButtonDefaults.buttonColors(containerColor = appColors.surface),
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(1.dp, appColors.border)
                ) {
                    Text("Switch Profile", color = appColors.textPrimary, fontSize = 12.sp)
                }

                Button(
                    onClick = onAddNewAccount,
                    colors = ButtonDefaults.buttonColors(containerColor = OttPrimary),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Text("+ Add Account", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

/**
 * Add Xtream Account Modal Dialog
 */
@Composable
private fun AddXtreamAccountDialog(
    isLoading: Boolean,
    authError: String?,
    onDismiss: () -> Unit,
    onConnect: (name: String, url: String, user: String, pass: String) -> Unit
) {
    val appColors = LocalAppColors.current

    var name by remember { mutableStateOf("") }
    var serverUrl by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var localValidationErr by remember { mutableStateOf<String?>(null) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
                .padding(horizontal = 20.dp, vertical = 24.dp),
            contentAlignment = Alignment.Center
        ) {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = appColors.surface),
                border = BorderStroke(1.dp, appColors.border),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("dialog_add_xtream")
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(20.dp)
                ) {
                // Title
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = CircleShape,
                            color = OttPrimary.copy(alpha = 0.15f),
                            modifier = Modifier.size(36.dp)
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
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Add Xtream Account",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = appColors.textPrimary
                        )
                    }

                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Close", tint = appColors.textPrimary)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Error Banner
                val displayedError = localValidationErr ?: authError
                if (displayedError != null) {
                    Surface(
                        color = Color(0xFFFEF2F2),
                        border = BorderStroke(1.dp, Color(0xFFEF4444)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.ErrorOutline,
                                contentDescription = null,
                                tint = Color(0xFFEF4444),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = displayedError,
                                fontSize = 12.sp,
                                color = Color(0xFF991B1B)
                            )
                        }
                    }
                }

                // Inputs
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Profile Name (Optional)") },
                    placeholder = { Text("e.g. Living Room, ok") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = appColors.textPrimary,
                        unfocusedTextColor = appColors.textPrimary,
                        focusedBorderColor = OttPrimary,
                        unfocusedBorderColor = appColors.border,
                        focusedLabelColor = OttPrimary,
                        unfocusedLabelColor = appColors.textSecondary,
                        focusedContainerColor = appColors.surfaceVariant,
                        unfocusedContainerColor = appColors.surfaceVariant,
                        cursorColor = OttPrimary
                    ),
                    modifier = Modifier.fillMaxWidth().testTag("input_xtream_name")
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = serverUrl,
                    onValueChange = { serverUrl = it },
                    label = { Text("Server URL") },
                    placeholder = { Text("http://example.com:8080") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = appColors.textPrimary,
                        unfocusedTextColor = appColors.textPrimary,
                        focusedBorderColor = OttPrimary,
                        unfocusedBorderColor = appColors.border,
                        focusedLabelColor = OttPrimary,
                        unfocusedLabelColor = appColors.textSecondary,
                        focusedContainerColor = appColors.surfaceVariant,
                        unfocusedContainerColor = appColors.surfaceVariant,
                        cursorColor = OttPrimary
                    ),
                    modifier = Modifier.fillMaxWidth().testTag("input_xtream_server")
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("Username") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = appColors.textPrimary,
                        unfocusedTextColor = appColors.textPrimary,
                        focusedBorderColor = OttPrimary,
                        unfocusedBorderColor = appColors.border,
                        focusedLabelColor = OttPrimary,
                        unfocusedLabelColor = appColors.textSecondary,
                        focusedContainerColor = appColors.surfaceVariant,
                        unfocusedContainerColor = appColors.surfaceVariant,
                        cursorColor = OttPrimary
                    ),
                    modifier = Modifier.fillMaxWidth().testTag("input_xtream_user")
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = appColors.textPrimary,
                        unfocusedTextColor = appColors.textPrimary,
                        focusedBorderColor = OttPrimary,
                        unfocusedBorderColor = appColors.border,
                        focusedLabelColor = OttPrimary,
                        unfocusedLabelColor = appColors.textSecondary,
                        focusedContainerColor = appColors.surfaceVariant,
                        unfocusedContainerColor = appColors.surfaceVariant,
                        cursorColor = OttPrimary
                    ),
                    modifier = Modifier.fillMaxWidth().testTag("input_xtream_pass")
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Working Demo Account Fill Button
                Surface(
                    onClick = {
                        name = "Demo TV"
                        serverUrl = "http://demo.xtream-server.tv"
                        username = "demo"
                        password = "demo"
                        localValidationErr = null
                    },
                    shape = RoundedCornerShape(10.dp),
                    color = appColors.surfaceVariant,
                    border = BorderStroke(1.dp, appColors.border),
                    modifier = Modifier.fillMaxWidth().testTag("btn_fill_demo_account")
                ) {
                    Text(
                        text = "⚡ Fill Working Sample Demo Account",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = OttPrimary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Find Public Xtream Account Link Button
                val context = androidx.compose.ui.platform.LocalContext.current
                Surface(
                    onClick = {
                        try {
                            val intent = android.content.Intent(
                                android.content.Intent.ACTION_VIEW,
                                android.net.Uri.parse("https://datashield-cloud.github.io/IPTV/Xtream/")
                            ).apply {
                                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    },
                    shape = RoundedCornerShape(10.dp),
                    color = OttPrimary.copy(alpha = 0.08f),
                    border = BorderStroke(1.dp, OttPrimary.copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth().testTag("btn_find_public_xtream")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp, horizontal = 12.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
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
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = OttPrimary,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Connect Button
                Button(
                    onClick = {
                        if (serverUrl.isBlank() || username.isBlank() || password.isBlank()) {
                            localValidationErr = "Please enter Server URL, Username, and Password."
                            return@Button
                        }
                        localValidationErr = null
                        onConnect(name, serverUrl, username, password)
                    },
                    enabled = !isLoading,
                    colors = ButtonDefaults.buttonColors(containerColor = OttPrimary),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("btn_submit_xtream")
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            color = Color.White,
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Verifying & Connecting...", color = Color.White)
                    } else {
                        Text("Connect & Save Profile", fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }
            }
        }
    }
}
}
