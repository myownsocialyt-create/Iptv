package com.example.ui.screens

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import coil.compose.AsyncImage
import com.example.data.local.CustomPlaylistEntity
import com.example.data.model.Channel
import com.example.data.model.CommunityPlaylists
import com.example.data.repository.PlaylistRepository
import com.example.data.repository.PlaylistSource
import com.example.data.repository.PresetPlaylists
import com.example.ui.theme.AppColors
import com.example.ui.viewmodel.IptvUiState
import com.example.ui.viewmodel.IptvViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.lang.ref.WeakReference

class GlobalSearchActivity : ComponentActivity() {

    companion object {
        private var activeActivityRef: WeakReference<ComponentActivity>? = null

        fun getActiveActivity(): ComponentActivity? = activeActivityRef?.get()

        fun registerActivity(act: ComponentActivity) {
            activeActivityRef = WeakReference(act)
        }

        fun start(context: Context? = null) {
            val ctx = context ?: getActiveActivity() ?: getApplicationContext() ?: return
            val intent = Intent(ctx, GlobalSearchActivity::class.java).apply {
                if (ctx !is Activity) {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }
            ctx.startActivity(intent)
        }

        private fun getApplicationContext(): Context? {
            return try {
                val atClass = Class.forName("android.app.ActivityThread")
                val method = atClass.getMethod("currentApplication")
                method.invoke(null) as? Context
            } catch (_: Throwable) {
                null
            }
        }

        fun getCachedViewModel(): IptvViewModel? {
            return try {
                val clazz = Class.forName("com.example.ui.screens.HomeBannerCarouselKt")
                val field = clazz.getDeclaredField("cachedViewModel")
                field.isAccessible = true
                field.get(null) as? IptvViewModel
            } catch (_: Throwable) {
                null
            }
        }

        @Suppress("UNCHECKED_CAST")
        fun getCachedUiState(): IptvUiState? {
            return try {
                val clazz = Class.forName("com.example.ui.screens.HomeBannerCarouselKt")
                val field = clazz.getDeclaredField("cachedUiState")
                field.isAccessible = true
                (field.get(null) as? State<IptvUiState>)?.value
            } catch (_: Throwable) {
                null
            }
        }

        @Suppress("UNCHECKED_CAST")
        fun getCachedCustomPlaylists(): List<CustomPlaylistEntity>? {
            return try {
                val clazz = Class.forName("com.example.ui.screens.HomeBannerCarouselKt")
                val field = clazz.getDeclaredField("cachedCustomPlaylists")
                field.isAccessible = true
                (field.get(null) as? State<List<CustomPlaylistEntity>>)?.value
            } catch (_: Throwable) {
                null
            }
        }

        fun getCachedAppColors(): AppColors {
            return try {
                val clazz = Class.forName("com.example.ui.screens.HomeBannerCarouselKt")
                val field = clazz.getDeclaredField("cachedAppColors")
                field.isAccessible = true
                (field.get(null) as? AppColors) ?: AppColors()
            } catch (_: Throwable) {
                AppColors()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        registerActivity(this)
        enableEdgeToEdge()

        setContent {
            val appColors = remember { getCachedAppColors() }
            GlobalSearchScreen(
                appColors = appColors,
                onClose = { finish() },
                onPlayChannel = { channel ->
                    lifecycleScope.launch {
                        val vm = getCachedViewModel()
                        vm?.playChannel(channel)
                        finish()
                    }
                }
            )
        }
    }

    override fun onResume() {
        super.onResume()
        registerActivity(this)
    }
}

@Composable
fun GlobalSearchScreen(
    appColors: AppColors,
    onClose: () -> Unit,
    onPlayChannel: (Channel) -> Unit
) {
    val context = LocalContext.current
    BackHandler { onClose() }

    val prefs = remember {
        context.getSharedPreferences("global_search_history_prefs", Context.MODE_PRIVATE)
    }

    var searchHistory by remember {
        val saved = prefs.getStringSet("history", emptySet()) ?: emptySet()
        mutableStateOf(saved.toList().sorted())
    }

    fun saveHistory(query: String) {
        val clean = query.trim()
        if (clean.length < 2) return
        val currentSet = prefs.getStringSet("history", emptySet())?.toMutableSet() ?: mutableSetOf()
        currentSet.add(clean)
        prefs.edit().putStringSet("history", currentSet).apply()
        searchHistory = currentSet.toList().sorted()
    }

    fun removeHistoryItem(item: String) {
        val currentSet = prefs.getStringSet("history", emptySet())?.toMutableSet() ?: mutableSetOf()
        currentSet.remove(item)
        prefs.edit().putStringSet("history", currentSet).apply()
        searchHistory = currentSet.toList().sorted()
    }

    fun clearAllHistory() {
        prefs.edit().remove("history").apply()
        searchHistory = emptyList()
    }

    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf("All") }
    var allChannels by remember { mutableStateOf<List<Channel>>(emptyList()) }
    var isIndexing by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val list = mutableListOf<Channel>()
            val seenUrls = mutableSetOf<String>()

            fun addChannels(source: List<Channel>?, playlistTag: String) {
                if (source == null) return
                for (ch in source) {
                    if (ch.streamUrl.isNotBlank() && seenUrls.add(ch.streamUrl)) {
                        list.add(if (ch.playlistName.isNullOrBlank()) ch.copy(playlistName = playlistTag) else ch)
                    }
                }
            }

            // 1. From UiState
            val uiState = GlobalSearchActivity.getCachedUiState()
            uiState?.let {
                addChannels(it.allChannels, "Live TV")
                addChannels(it.channels, "Live TV")
            }

            // 2. From Preset Fallbacks via PlaylistRepository
            val repo = PlaylistRepository()
            try {
                val indiaChannels = repo.loadChannels(PresetPlaylists.INDIA).firstOrNull()
                addChannels(indiaChannels, "India")
            } catch (_: Throwable) {}

            try {
                val globalChannels = repo.loadChannels(PresetPlaylists.GLOBAL).firstOrNull()
                addChannels(globalChannels, "Global")
            } catch (_: Throwable) {}

            // 3. From Community Playlists
            for (comm in CommunityPlaylists.LIST.take(6)) {
                try {
                    val channels = repo.loadChannels(
                        PlaylistSource(id = comm.name, title = comm.name, url = comm.url, isPreset = false)
                    ).firstOrNull()
                    addChannels(channels, comm.name)
                } catch (_: Throwable) {}
            }

            // 4. From Custom Playlists
            try {
                val custom = GlobalSearchActivity.getCachedCustomPlaylists() ?: emptyList()
                for (cp in custom) {
                    try {
                        val channels = repo.loadChannels(
                            PlaylistSource(id = cp.title, title = cp.title, url = cp.url, isPreset = false)
                        ).firstOrNull()
                        addChannels(channels, cp.title)
                    } catch (_: Throwable) {}
                }
            } catch (_: Throwable) {}

            withContext(Dispatchers.Main) {
                allChannels = list
                isIndexing = false
            }
        }
    }

    val filterChips = listOf("All", "India", "Global", "Community", "Custom", "Live TV", "Movies/VOD")

    val filteredChannels by remember {
        derivedStateOf {
            val query = searchQuery.trim().lowercase()
            if (query.isEmpty()) {
                emptyList()
            } else {
                allChannels.filter { ch ->
                    val matchesQuery = ch.name.lowercase().contains(query) ||
                            (ch.category?.lowercase()?.contains(query) == true) ||
                            (ch.playlistName?.lowercase()?.contains(query) == true)

                    val matchesFilter = when (selectedFilter) {
                        "All" -> true
                        "India" -> ch.playlistName?.contains("India", ignoreCase = true) == true ||
                                ch.category?.contains("India", ignoreCase = true) == true
                        "Global" -> ch.playlistName?.contains("Global", ignoreCase = true) == true ||
                                ch.category?.contains("Global", ignoreCase = true) == true
                        "Community" -> CommunityPlaylists.LIST.any { cp -> ch.playlistName?.contains(cp.name, ignoreCase = true) == true }
                        "Custom" -> !ch.playlistName.isNullOrBlank() &&
                                !ch.playlistName!!.contains("India", ignoreCase = true) &&
                                !ch.playlistName!!.contains("Global", ignoreCase = true)
                        "Live TV" -> !ch.category.equals("VOD", ignoreCase = true) && !ch.category.equals("Movie", ignoreCase = true)
                        "Movies/VOD" -> ch.category?.contains("VOD", ignoreCase = true) == true ||
                                ch.category?.contains("Movie", ignoreCase = true) == true
                        else -> true
                    }
                    matchesQuery && matchesFilter
                }
            }
        }
    }

    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        keyboardController?.show()
    }

    Scaffold(
        contentWindowInsets = WindowInsets.statusBars,
        containerColor = appColors.background,
        topBar = {
            Surface(
                color = appColors.surface,
                tonalElevation = 2.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = onClose,
                            modifier = Modifier.size(44.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = appColors.textPrimary
                            )
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                                .background(
                                    color = appColors.surfaceVariant,
                                    shape = RoundedCornerShape(22.dp)
                                )
                                .padding(horizontal = 12.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = null,
                                    tint = appColors.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))

                                Box(modifier = Modifier.weight(1f)) {
                                    if (searchQuery.isEmpty()) {
                                        Text(
                                            text = "Search all channels & playlists...",
                                            color = appColors.textMuted,
                                            fontSize = 14.sp
                                        )
                                    }
                                    BasicTextField(
                                        value = searchQuery,
                                        onValueChange = { searchQuery = it },
                                        singleLine = true,
                                        textStyle = TextStyle(
                                            color = appColors.textPrimary,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Medium
                                        ),
                                        cursorBrush = SolidColor(appColors.primary),
                                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                        keyboardActions = KeyboardActions(onSearch = {
                                            keyboardController?.hide()
                                            if (searchQuery.isNotBlank()) {
                                                saveHistory(searchQuery)
                                            }
                                        }),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .focusRequester(focusRequester)
                                    )
                                }

                                if (searchQuery.isNotEmpty()) {
                                    IconButton(
                                        onClick = { searchQuery = "" },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Clear",
                                            tint = appColors.textSecondary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Filter chips row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        for (chip in filterChips) {
                            val isSelected = (chip == selectedFilter)
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = if (isSelected) appColors.primary else appColors.surfaceVariant,
                                modifier = Modifier
                                    .height(32.dp)
                                    .clickable { selectedFilter = chip }
                            ) {
                                Box(
                                    modifier = Modifier.padding(horizontal = 14.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = chip,
                                        fontSize = 12.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) Color.White else appColors.textSecondary
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (searchQuery.isEmpty()) {
                // Search History View
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    if (searchHistory.isNotEmpty()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Search History",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = appColors.textPrimary
                            )
                            TextButton(onClick = { clearAllHistory() }) {
                                Text(
                                    text = "Clear All",
                                    color = appColors.primary,
                                    fontSize = 13.sp
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(searchHistory) { historyItem ->
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = appColors.surface,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            searchQuery = historyItem
                                        }
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 14.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.History,
                                            contentDescription = null,
                                            tint = appColors.textSecondary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Text(
                                            text = historyItem,
                                            fontSize = 14.sp,
                                            color = appColors.textPrimary,
                                            modifier = Modifier.weight(1f)
                                        )
                                        IconButton(
                                            onClick = { removeHistoryItem(historyItem) },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = "Delete",
                                                tint = appColors.textMuted,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        // Empty State when history is empty
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = null,
                                    tint = appColors.textMuted,
                                    modifier = Modifier.size(64.dp)
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = "Global Search",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = appColors.textPrimary
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = if (isIndexing) "Indexing playlists & channels..." else "Search across preset, community & custom playlists",
                                    fontSize = 13.sp,
                                    color = appColors.textSecondary
                                )
                            }
                        }
                    }
                }
            } else {
                // Search Results View
                if (filteredChannels.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No channels found for \"$searchQuery\"",
                            color = appColors.textSecondary,
                            fontSize = 14.sp
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        item {
                            Text(
                                text = "Found ${filteredChannels.size} channels",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = appColors.textSecondary,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
                            )
                        }

                        items(filteredChannels, key = { it.streamUrl }) { channel ->
                            ChannelSearchCard(
                                channel = channel,
                                appColors = appColors,
                                onClick = {
                                    saveHistory(searchQuery)
                                    onPlayChannel(channel)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ChannelSearchCard(
    channel: Channel,
    appColors: AppColors,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = appColors.surface,
        tonalElevation = 1.dp,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Channel Logo / Badge
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                appColors.primary.copy(alpha = 0.3f),
                                appColors.surfaceVariant
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (!channel.logoUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = channel.logoUrl,
                        contentDescription = channel.name,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(6.dp))
                    )
                } else {
                    val initials = channel.name.take(2).uppercase()
                    Text(
                        text = initials,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = appColors.textPrimary
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = channel.name,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = appColors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (!channel.category.isNullOrBlank()) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = appColors.surfaceVariant
                        ) {
                            Text(
                                text = channel.category!!,
                                fontSize = 10.sp,
                                color = appColors.textSecondary,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    if (!channel.playlistName.isNullOrBlank()) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = appColors.primary.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = channel.playlistName!!,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium,
                                color = appColors.primary,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Play Icon button
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(appColors.primary.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Play",
                    tint = appColors.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
