package com.example.premium

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.rounded.Cast
import androidx.compose.material.icons.rounded.CastConnected
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.PictureInPictureAlt
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Splitscreen
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

object SplitPortraitHooks {
    private const val TAG = "SplitPortraitHooks"
    private val mainHandler = Handler(Looper.getMainLooper())
    private val fieldCache = ConcurrentHashMap<String, Field>()
    private val methodCache = ConcurrentHashMap<String, Method>()

    @Volatile
    private var pendingActionTarget: Any? = null

    @Volatile
    private var pendingSearchTarget: Any? = null

    // Optimistic reload spin trigger
    private var reloadPulseUntilMs by mutableLongStateOf(0L)

    fun setPendingActionPanelTarget(target: Any) {
        pendingSearchTarget = null
        pendingActionTarget = target
    }

    fun setPendingSearchPanelTarget(target: Any) {
        pendingActionTarget = null
        pendingSearchTarget = target
    }

    @Composable
    fun tryRenderPendingSplitUi(): Boolean {
        val actionTarget = pendingActionTarget
        if (actionTarget != null) {
            pendingActionTarget = null
            key("split_action_panel") {
                RenderActionPanelFromLambda(actionTarget)
            }
            return true
        }
        val searchTarget = pendingSearchTarget
        if (searchTarget != null) {
            pendingSearchTarget = null
            key("split_search_panel") {
                RenderSearchPlaylistReloadFromLambda(searchTarget)
            }
            return true
        }
        return false
    }

    private data class ThemePalette(
        val isDark: Boolean,
        val background: Color,
        val surface: Color,
        val tileSurface: Color,
        val searchSurface: Color,
        val border: Color,
        val primary: Color,
        val textPrimary: Color,
        val textSecondary: Color,
        val textMuted: Color
    )

    private fun extractThemePalette(appColors: Any?, context: Context): ThemePalette {
        val isDark = (invokeNoArg(appColors, "isDark") as? Boolean)
            ?: ((context.resources.configuration.uiMode and
                android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                android.content.res.Configuration.UI_MODE_NIGHT_YES)

        val primary = Color(0xFFE50914)
        return if (isDark) {
            ThemePalette(
                isDark = true,
                background = Color(0xFF000000),
                surface = Color(0xFF0D0D0D),
                tileSurface = Color(0xFF1A1A1D),
                searchSurface = Color(0xFF161619),
                border = Color(0xFF28282D),
                primary = primary,
                textPrimary = Color(0xFFFFFFFF),
                textSecondary = Color(0xFFB0B0B8),
                textMuted = Color(0xFF80808A)
            )
        } else {
            ThemePalette(
                isDark = false,
                background = Color(0xFFF8FAFC),
                surface = Color(0xFFFFFFFF),
                tileSurface = Color(0xFFF1F5F9),
                searchSurface = Color(0xFFF1F5F9),
                border = Color(0xFFE2E8F0),
                primary = primary,
                textPrimary = Color(0xFF0F172A),
                textSecondary = Color(0xFF475569),
                textMuted = Color(0xFF64748B)
            )
        }
    }

    @Suppress("UNCHECKED_CAST")
    @Composable
    private fun RenderActionPanelFromLambda(target: Any) {
        val activeChannel = getField(target, "\$activeChannel") ?: return
        val appColors = getField(target, "\$appColors")
        val context = getField(target, "\$context") as? Context ?: return
        val onToggleFavorite = getField(target, "\$onToggleFavorite") as? Function1<Any, Any?>
        val onEnterPip = getField(target, "\$onEnterPip") as? Function0<Any?>
        val onToggleFullscreen = getField(target, "\$onToggleFullscreen") as? Function0<Any?>
        val isXtreamMode = (getField(target, "\$isXtreamMode") as? Boolean) ?: false
        val xtreamAccountName = getField(target, "\$xtreamAccountName") as? String

        val channelName = (invokeNoArg(activeChannel, "getName") as? String)?.trim().orEmpty()
        val logoUrl = (invokeNoArg(activeChannel, "getLogoUrl") as? String)?.trim().orEmpty()
        val category = (invokeNoArg(activeChannel, "getCategory") as? String)?.trim().orEmpty()
        val language = (invokeNoArg(activeChannel, "getLanguage") as? String)?.trim().orEmpty()
        val streamUrl = (invokeNoArg(activeChannel, "getStreamUrl") as? String)?.trim().orEmpty()
        val isFavFromChannel = (invokeNoArg(activeChannel, "isFavorite") as? Boolean) ?: false

        ActionPanelContent(
            channelRef = activeChannel,
            channelName = channelName,
            logoUrl = logoUrl,
            category = category,
            language = language,
            streamUrl = streamUrl,
            isFavorite = isFavFromChannel,
            isXtreamMode = isXtreamMode,
            xtreamAccountName = xtreamAccountName,
            palette = extractThemePalette(appColors, context),
            onFavoriteClick = {
                try {
                    onToggleFavorite?.invoke(activeChannel)
                } catch (t: Throwable) {
                    Log.w(TAG, "onToggleFavorite failed", t)
                }
            },
            onPipClick = {
                try {
                    val act = findMainActivity(context)
                    if (act != null) {
                        val method = act.javaClass.getMethod("enterPipMode")
                        method.invoke(act)
                    } else {
                        onEnterPip?.invoke()
                    }
                } catch (t: Throwable) {
                    try {
                        onEnterPip?.invoke()
                    } catch (_: Throwable) {
                    }
                }
            },
            onFullscreenClick = {
                try {
                    onToggleFullscreen?.invoke()
                } catch (t: Throwable) {
                    Log.w(TAG, "onToggleFullscreen failed", t)
                }
            },
            context = context
        )
    }

    @Composable
    private fun ActionPanelContent(
        channelRef: Any,
        channelName: String,
        logoUrl: String,
        category: String,
        language: String,
        streamUrl: String,
        isFavorite: Boolean,
        isXtreamMode: Boolean,
        xtreamAccountName: String?,
        palette: ThemePalette,
        onFavoriteClick: () -> Unit,
        onPipClick: () -> Unit,
        onFullscreenClick: () -> Unit,
        context: Context
    ) {
        var showCastDialog by remember { mutableStateOf(false) }
        var dualHighlighted by remember(streamUrl) { mutableStateOf(false) }
        val isCasting = HybridCastManager.isCastingActive

        LaunchedEffect(streamUrl, channelName, category, language, logoUrl) {
            HybridCastManager.updateCurrentChannel(
                context = context,
                channelName = channelName,
                category = category,
                language = language,
                logoUrl = logoUrl,
                streamUrl = streamUrl
            )
        }

        val initials = remember(channelName) {
            channelName
                .split(" ")
                .filter { it.isNotBlank() }
                .take(2)
                .joinToString("") { it.first().uppercaseChar().toString() }
                .ifEmpty { "TV" }
        }

        val subtitleText = remember(category, language) {
            buildString {
                val cat = category.ifEmpty { "Live TV" }
                append(cat)
                if (language.isNotEmpty() && !cat.equals(language, ignoreCase = true)) {
                    append(" • ")
                    append(language)
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            // Compact Channel Header Row (slightly smaller & sleeker)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .background(palette.tileSurface)
                        .border(0.8.dp, palette.border, RoundedCornerShape(9.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    if (logoUrl.isNotEmpty()) {
                        SubcomposeAsyncImage(
                            model = logoUrl,
                            contentDescription = channelName,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(3.dp),
                            contentScale = ContentScale.Fit,
                            error = {
                                Text(
                                    text = initials,
                                    color = palette.textPrimary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1
                                )
                            }
                        )
                    } else {
                        Text(
                            text = initials,
                            color = palette.textPrimary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                    }
                }

                Spacer(modifier = Modifier.width(10.dp))

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = channelName.ifEmpty { "Live Channel" },
                        color = palette.textPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(3.dp))
                                .background(Color(0xFFE5253B))
                                .padding(horizontal = 4.dp, vertical = 0.5.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "LIVE",
                                color = Color.White,
                                fontSize = 7.5.sp,
                                lineHeight = 9.sp,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 0.3.sp,
                                maxLines = 1
                            )
                        }

                        Text(
                            text = subtitleText,
                            color = palette.textSecondary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                if (isXtreamMode && !xtreamAccountName.isNullOrBlank()) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .widthIn(max = 88.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(palette.primary.copy(alpha = 0.14f))
                            .border(0.8.dp, palette.primary.copy(alpha = 0.45f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 7.dp, vertical = 3.dp)
                            .testTag("split_xtream_account_pill")
                    ) {
                        Text(
                            text = "⚡ $xtreamAccountName",
                            color = palette.primary,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // 5 Rounded Action Tile Cards Row: Favorite | Cast | PiP | Dual | Full
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ActionTileButton(
                    modifier = Modifier
                        .weight(1f)
                        .testTag("split_favorite_button"),
                    icon = if (isFavorite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                    label = "Favorite",
                    contentDescription = if (isFavorite) "Remove from favorites" else "Add to favorites",
                    isActive = isFavorite,
                    activeContainer = Color(0x28EF4444),
                    activeContent = Color(0xFFEF4444),
                    palette = palette,
                    onClick = onFavoriteClick
                )

                ActionTileButton(
                    modifier = Modifier
                        .weight(1f)
                        .testTag("split_cast_button"),
                    icon = if (isCasting) Icons.Rounded.CastConnected else Icons.Rounded.Cast,
                    label = if (isCasting) "Casting" else "Cast",
                    contentDescription = "Cast to TV",
                    isActive = isCasting || showCastDialog,
                    activeContainer = Color(0xFFE5253B),
                    activeContent = Color.White,
                    palette = palette,
                    onClick = {
                        HybridCastManager.updateCurrentChannel(
                            context = context,
                            channelName = channelName,
                            category = category,
                            language = language,
                            logoUrl = logoUrl,
                            streamUrl = streamUrl
                        )
                        showCastDialog = true
                    }
                )

                ActionTileButton(
                    modifier = Modifier
                        .weight(1f)
                        .testTag("split_pip_button"),
                    icon = Icons.Rounded.PictureInPictureAlt,
                    label = "PiP",
                    contentDescription = "Picture in Picture",
                    isActive = false,
                    activeContainer = palette.primary.copy(alpha = 0.18f),
                    activeContent = palette.primary,
                    palette = palette,
                    onClick = onPipClick
                )

                ActionTileButton(
                    modifier = Modifier
                        .weight(1f)
                        .testTag("split_dual_button"),
                    icon = Icons.Rounded.Splitscreen,
                    label = "Dual",
                    contentDescription = "Dual screen",
                    isActive = dualHighlighted,
                    activeContainer = palette.primary.copy(alpha = 0.18f),
                    activeContent = palette.primary,
                    palette = palette,
                    onClick = {
                        dualHighlighted = !dualHighlighted
                        Toast.makeText(context, "Dual screen coming soon", Toast.LENGTH_SHORT).show()
                    }
                )

                ActionTileButton(
                    modifier = Modifier
                        .weight(1f)
                        .testTag("split_fullscreen_button"),
                    icon = Icons.Rounded.Fullscreen,
                    label = "Full",
                    contentDescription = "Fullscreen landscape mode",
                    isActive = false,
                    activeContainer = palette.primary.copy(alpha = 0.18f),
                    activeContent = palette.primary,
                    palette = palette,
                    onClick = onFullscreenClick
                )
            }

            if (isCasting) {
                HybridCastManager.ActiveCastRemoteBar(
                    context = context,
                    isDark = palette.isDark,
                    onOpenCastSheet = { showCastDialog = true }
                )
            }

            if (showCastDialog) {
                HybridCastManager.HybridCastDialog(
                    context = context,
                    isDark = palette.isDark,
                    onDismiss = { showCastDialog = false }
                )
            }
        }
    }

    @Composable
    private fun ActionTileButton(
        modifier: Modifier,
        icon: ImageVector,
        label: String,
        contentDescription: String,
        isActive: Boolean,
        activeContainer: Color,
        activeContent: Color,
        palette: ThemePalette,
        onClick: () -> Unit
    ) {
        val bg = if (isActive) activeContainer else palette.tileSurface
        val fg = if (isActive) activeContent else palette.textPrimary
        val borderColor = if (isActive) activeContent.copy(alpha = 0.45f) else palette.border

        Surface(
            modifier = modifier
                .height(54.dp)
                .clip(RoundedCornerShape(12.dp))
                .clickable(onClick = onClick),
            shape = RoundedCornerShape(12.dp),
            color = bg,
            border = BorderStroke(0.8.dp, borderColor)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 3.dp, vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = contentDescription,
                    tint = fg,
                    modifier = Modifier.size(19.dp)
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = label,
                    color = if (isActive) fg else palette.textSecondary,
                    fontSize = 10.sp,
                    fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Medium,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    @Composable
    private fun RenderSearchPlaylistReloadFromLambda(target: Any) {
        val appColors = getField(target, "\$appColors")
        val context = getField(target, "\$context") as? Context ?: return
        val searchState = getField(target, "\$searchQuery\$delegate") as? MutableState<String>
        val currentPlaylist = getField(target, "\$currentPlaylist")
        val customPlaylists = (getField(target, "\$customPlaylists") as? List<*>)?.filterNotNull() ?: emptyList()
        val deletedPresetIds = (getField(target, "\$deletedPresetIds") as? Set<*>)
            ?.mapNotNull { it?.toString() }?.toSet() ?: emptySet()
        val onSelectPlaylist = getField(target, "\$onSelectPlaylist") as? Function1<Any, Any?>
        val onRefresh = getField(target, "\$onRefresh") as? Function0<Any?>
        val isXtreamMode = (getField(target, "\$isXtreamMode") as? Boolean) ?: false
        val xtreamAccountName = getField(target, "\$xtreamAccountName") as? String
        val isLoading = (getField(target, "\$isLoading") as? Boolean) ?: false
        val activeChannel = getField(target, "\$activeChannel")
        val playerManager = getField(target, "\$playerManager")

        SearchPlaylistReloadContent(
            searchQueryState = searchState,
            currentPlaylist = currentPlaylist,
            customPlaylists = customPlaylists,
            deletedPresetIds = deletedPresetIds,
            onSelectPlaylist = onSelectPlaylist,
            onRefresh = onRefresh,
            isXtreamMode = isXtreamMode,
            xtreamAccountName = xtreamAccountName,
            isLoading = isLoading,
            activeChannel = activeChannel,
            playerManager = playerManager,
            palette = extractThemePalette(appColors, context),
            context = context
        )
    }

    @Composable
    private fun SearchPlaylistReloadContent(
        searchQueryState: MutableState<String>?,
        currentPlaylist: Any?,
        customPlaylists: List<Any>,
        deletedPresetIds: Set<String>,
        onSelectPlaylist: Function1<Any, Any?>?,
        onRefresh: Function0<Any?>?,
        isXtreamMode: Boolean,
        xtreamAccountName: String?,
        isLoading: Boolean,
        activeChannel: Any?,
        playerManager: Any?,
        palette: ThemePalette,
        context: Context
    ) {
        val scope = rememberCoroutineScope()
        var localQuery by remember { mutableStateOf("") }
        val queryText = searchQueryState?.value ?: localQuery
        var showPlaylistMenu by remember { mutableStateOf(false) }

        val nowMs = System.currentTimeMillis()
        val isReloading = isLoading || reloadPulseUntilMs > nowMs

        val spinTransition = rememberInfiniteTransition(label = "reload_spin")
        val spinAngle by spinTransition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 850, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "reload_angle"
        )

        val currentPlaylistId = (invokeNoArg(currentPlaylist, "getId") as? String) ?: ""
        val currentPlaylistUrl = (invokeNoArg(currentPlaylist, "getUrl") as? String) ?: ""
        val currentPlaylistTitleRaw = (invokeNoArg(currentPlaylist, "getTitle") as? String) ?: ""

        val indiaAvailable = !deletedPresetIds.contains("preset_india")
        val globalAvailable = !deletedPresetIds.contains("preset_global")
        val noPlaylistsAvailable = !isXtreamMode && !indiaAvailable && !globalAvailable && customPlaylists.isEmpty()

        val shortSwitchLabel = remember(
            currentPlaylistId,
            currentPlaylistTitleRaw,
            isXtreamMode,
            xtreamAccountName,
            noPlaylistsAvailable
        ) {
            if (noPlaylistsAvailable || currentPlaylistId == "none") {
                "Add Playlist"
            } else {
                formatShortPlaylistLabel(
                    playlistId = currentPlaylistId,
                    title = currentPlaylistTitleRaw,
                    isXtreamMode = isXtreamMode,
                    xtreamAccountName = xtreamAccountName
                )
            }
        }

        fun openAddPlaylistDialog() {
            val vm = discoverViewModel() ?: return
            try {
                val openMethod = vm.javaClass.getMethod("openAddPlaylist", Int::class.javaPrimitiveType)
                openMethod.invoke(vm, 0)
            } catch (t: Throwable) {
                Log.w(TAG, "openAddPlaylist invocation failed", t)
            }
        }

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            val isVeryNarrow = maxWidth < 310.dp
            val switchMaxWidth = when {
                shortSwitchLabel == "Add Playlist" -> if (isVeryNarrow) 108.dp else 124.dp
                isVeryNarrow -> 86.dp
                else -> 104.dp
            }
            val controlHeight = 40.dp
            val reloadBtnSize = if (isVeryNarrow) 38.dp else 40.dp

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // 1. Compact slim Search Bar
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .height(controlHeight),
                    shape = RoundedCornerShape(11.dp),
                    color = palette.searchSurface,
                    border = BorderStroke(0.9.dp, palette.border)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Search,
                            contentDescription = "Filter channels",
                            tint = palette.textMuted,
                            modifier = Modifier.size(17.dp)
                        )
                        Spacer(modifier = Modifier.width(7.dp))
                        Box(
                            modifier = Modifier.weight(1f),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            if (queryText.isEmpty()) {
                                Text(
                                    text = "Filter channels",
                                    color = palette.textMuted,
                                    fontSize = 13.sp,
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            BasicTextField(
                                value = queryText,
                                onValueChange = { updated ->
                                    localQuery = updated
                                    searchQueryState?.value = updated
                                },
                                singleLine = true,
                                textStyle = TextStyle(
                                    color = palette.textPrimary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Normal
                                ),
                                cursorBrush = SolidColor(palette.primary),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("channel_filter_input")
                            )
                        }
                        if (queryText.isNotEmpty()) {
                            Spacer(modifier = Modifier.width(4.dp))
                            IconButton(
                                onClick = {
                                    localQuery = ""
                                    searchQueryState?.value = ""
                                },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = "Clear search",
                                    tint = palette.textSecondary,
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                        }
                    }
                }

                // 2. Shortened Playlist Switcher Pill + Dropdown Menu
                Box {
                    Surface(
                        modifier = Modifier
                            .height(controlHeight)
                            .widthIn(min = 66.dp, max = switchMaxWidth)
                            .clip(RoundedCornerShape(11.dp))
                            .clickable {
                                if (noPlaylistsAvailable) {
                                    openAddPlaylistDialog()
                                } else {
                                    showPlaylistMenu = true
                                }
                            }
                            .testTag("split_playlist_dropdown_trigger"),
                        shape = RoundedCornerShape(11.dp),
                        color = palette.tileSurface,
                        border = BorderStroke(0.9.dp, palette.border)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = shortSwitchLabel,
                                color = if (noPlaylistsAvailable) palette.primary else palette.textPrimary,
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Icon(
                                imageVector = Icons.Rounded.KeyboardArrowDown,
                                contentDescription = "Switch playlist",
                                tint = palette.textSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    DropdownMenu(
                        expanded = showPlaylistMenu,
                        onDismissRequest = { showPlaylistMenu = false },
                        containerColor = palette.surface
                    ) {
                        val allSource = getPresetSource("getALL")
                        val indiaSource = getPresetSource("getINDIA")
                        val globalSource = getPresetSource("getGLOBAL")

                        if (!noPlaylistsAvailable && allSource != null) {
                            val isSelected = currentPlaylistId == "preset_all"
                            PlaylistDropdownOption(
                                label = "🌐  All Playlists",
                                isSelected = isSelected,
                                palette = palette,
                                onClick = {
                                    showPlaylistMenu = false
                                    onSelectPlaylist?.invoke(allSource)
                                }
                            )
                        }

                        if (indiaSource != null && indiaAvailable) {
                            val isSelected = currentPlaylistId == "preset_india"
                            PlaylistDropdownOption(
                                label = "🇮🇳  India Channels",
                                isSelected = isSelected,
                                palette = palette,
                                onClick = {
                                    showPlaylistMenu = false
                                    onSelectPlaylist?.invoke(indiaSource)
                                }
                            )
                        }

                        if (globalSource != null && globalAvailable) {
                            val isSelected = currentPlaylistId == "preset_global"
                            PlaylistDropdownOption(
                                label = "🌍  Global Channels",
                                isSelected = isSelected,
                                palette = palette,
                                onClick = {
                                    showPlaylistMenu = false
                                    onSelectPlaylist?.invoke(globalSource)
                                }
                            )
                        }

                        for (entity in customPlaylists) {
                            val id = (invokeNoArg(entity, "getId") as? Number)?.toLong() ?: continue
                            val title = (invokeNoArg(entity, "getTitle") as? String)?.takeIf { it.isNotBlank() } ?: "Playlist"
                            val url = (invokeNoArg(entity, "getUrl") as? String)?.trim() ?: ""
                            val sourceId = "custom_$id"
                            val isSelected = (currentPlaylistId == sourceId) ||
                                (url.isNotEmpty() && currentPlaylistUrl == url)
                            val prefix = when {
                                url.startsWith("file://") -> "📁"
                                url.startsWith("xtream_account://") -> "⚡"
                                else -> "📺"
                            }
                            PlaylistDropdownOption(
                                label = "$prefix  $title",
                                isSelected = isSelected,
                                palette = palette,
                                onClick = {
                                    showPlaylistMenu = false
                                    val src = createPlaylistSource(sourceId, title, url, false)
                                    if (src != null) {
                                        onSelectPlaylist?.invoke(src)
                                    }
                                }
                            )
                        }

                        if (noPlaylistsAvailable) {
                            PlaylistDropdownOption(
                                label = "➕  Add Playlist",
                                isSelected = false,
                                palette = palette,
                                onClick = {
                                    showPlaylistMenu = false
                                    openAddPlaylistDialog()
                                }
                            )
                        }
                    }
                }

                // 3. Working Reload Button right next to Playlist Switcher
                Surface(
                    modifier = Modifier
                        .size(reloadBtnSize)
                        .clip(RoundedCornerShape(11.dp))
                        .clickable {
                            reloadPulseUntilMs = System.currentTimeMillis() + 1400L
                            scope.launch {
                                performWorkingReload(
                                    context = context,
                                    activeChannel = activeChannel,
                                    playerManager = playerManager,
                                    currentPlaylist = currentPlaylist,
                                    isXtreamMode = isXtreamMode,
                                    onRefresh = onRefresh
                                )
                                delay(1450L)
                                if (reloadPulseUntilMs <= System.currentTimeMillis()) {
                                    reloadPulseUntilMs = 0L
                                }
                            }
                        }
                        .testTag("split_refresh_playlist_button"),
                    shape = RoundedCornerShape(11.dp),
                    color = if (isReloading) palette.primary.copy(alpha = 0.16f) else palette.tileSurface,
                    border = BorderStroke(
                        0.9.dp,
                        if (isReloading) palette.primary.copy(alpha = 0.55f) else palette.border
                    )
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = "Reload playlist and stream",
                            tint = if (isReloading) palette.primary else palette.textPrimary,
                            modifier = Modifier
                                .size(19.dp)
                                .rotate(if (isReloading) spinAngle else 0f)
                        )
                    }
                }
            }
        }
    }

    @Composable
    private fun PlaylistDropdownOption(
        label: String,
        isSelected: Boolean,
        palette: ThemePalette,
        onClick: () -> Unit
    ) {
        DropdownMenuItem(
            text = {
                Text(
                    text = label,
                    color = if (isSelected) palette.primary else palette.textPrimary,
                    fontSize = 13.5.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            },
            trailingIcon = if (isSelected) {
                {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = "Selected",
                        tint = palette.primary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            } else {
                null
            },
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
            onClick = onClick
        )
    }

    private fun performWorkingReload(
        context: Context,
        activeChannel: Any?,
        playerManager: Any?,
        currentPlaylist: Any?,
        isXtreamMode: Boolean,
        onRefresh: Function0<Any?>?
    ) {
        try {
            // 1. Retry/reload current playing video stream if active
            if (playerManager != null && activeChannel != null) {
                mainHandler.post {
                    try {
                        val currentCh = getField(playerManager, "currentChannel")
                        if (currentCh != null) {
                            val retryMethod = playerManager.javaClass.getMethod("retry")
                            retryMethod.invoke(playerManager)
                        } else {
                            val playMethod = playerManager.javaClass.getMethod(
                                "playChannel",
                                activeChannel.javaClass,
                                Boolean::class.javaPrimitiveType,
                                Boolean::class.javaPrimitiveType
                            )
                            playMethod.invoke(playerManager, activeChannel, true, false)
                        }
                    } catch (t: Throwable) {
                        Log.w(TAG, "Player reload failed", t)
                    }
                }
            }

            // 2. Purge stale PlaylistRepository cache so playlist actually re-fetches
            val vm = discoverViewModel()
            val repo = getRepository(vm)
            val currentUrl = (invokeNoArg(currentPlaylist, "getUrl") as? String)?.trim().orEmpty()
            val currentId = (invokeNoArg(currentPlaylist, "getId") as? String)?.trim().orEmpty()

            if (repo != null) {
                try {
                    val clearMethod = repo.javaClass.getMethod("clearCache", String::class.java)
                    if (currentId == "preset_all" || currentUrl.isEmpty()) {
                        // All Playlists aggregates individual URLs -> clear full cache!
                        clearMethod.invoke(repo, null)
                    } else {
                        clearMethod.invoke(repo, currentUrl)
                    }
                } catch (t: Throwable) {
                    Log.w(TAG, "Repository clearCache failed", t)
                }
            }

            // 3. Invoke callback + force ViewModel refresh (fixes M3U All Playlists & Xtream mode!)
            try {
                onRefresh?.invoke()
            } catch (t: Throwable) {
                Log.w(TAG, "onRefresh callback threw", t)
            }

            if (vm != null) {
                try {
                    if (isXtreamMode) {
                        vm.javaClass.getMethod("refreshXtreamChannels").invoke(vm)
                    } else if (onRefresh == null || currentId == "preset_all") {
                        vm.javaClass.getMethod("refreshPlaylistChannels").invoke(vm)
                    }
                } catch (t: Throwable) {
                    Log.w(TAG, "ViewModel refresh invocation failed", t)
                }
            }

            val toastMsg = if (activeChannel != null) {
                "Reloading playlist & stream..."
            } else {
                "Refreshing playlist..."
            }
            Toast.makeText(context, toastMsg, Toast.LENGTH_SHORT).show()
        } catch (t: Throwable) {
            Log.e(TAG, "performWorkingReload failed", t)
        }
    }

    internal fun formatShortPlaylistLabel(
        playlistId: String,
        title: String,
        isXtreamMode: Boolean,
        xtreamAccountName: String?
    ): String {
        if (playlistId == "none" || title.equals("Add Playlist", ignoreCase = true)) {
            return "Add Playlist"
        }
        val raw = if (isXtreamMode && !xtreamAccountName.isNullOrBlank()) {
            xtreamAccountName.trim()
        } else {
            title.trim()
        }

        if (playlistId == "preset_india" || raw.contains("india", ignoreCase = true)) {
            return if (raw.contains("indian", ignoreCase = true)) "Indian" else "India"
        }
        if (playlistId == "preset_global" || raw.contains("global", ignoreCase = true)) {
            return "Global"
        }
        if (playlistId == "preset_all" || raw.equals("all playlists", ignoreCase = true) || raw.equals("all", ignoreCase = true)) {
            return "All"
        }

        // Strip leading emojis / punctuation symbols
        val cleaned = raw
            .replaceFirst(Regex("^[\\p{So}\\p{Cs}\\p{Cn}\\s\\-:•⚡📁📺🌐🇮🇳🌍]+"), "")
            .replaceFirst(Regex("^Xtream:\\s*", RegexOption.IGNORE_CASE), "")
            .trim()
            .replace(Regex("\\s+(Channels?|Playlists?|IPTV|Streams?|Live|TV)$", RegexOption.IGNORE_CASE), "")
            .trim()

        if (cleaned.isEmpty()) {
            return if (isXtreamMode) "Xtream" else "All"
        }

        val firstToken = cleaned.split(Regex("\\s+")).firstOrNull()?.trim().orEmpty()
        val chosen = if (firstToken.length >= 2) firstToken else cleaned
        return if (chosen.length > 10) chosen.take(9) + "…" else chosen
    }

    private fun discoverViewModel(): Any? {
        try {
            val helperClass = Class.forName("com.example.ui.screens.PlaylistTopSearchHelper")
            val instance = helperClass.getDeclaredField("INSTANCE").apply { isAccessible = true }.get(null)
            val method = helperClass.getMethod("getBoundViewModel")
            method.invoke(instance)?.let { return it }
        } catch (_: Throwable) {
        }
        return getField(PremiumUiHooks, "lastKnownViewModelRef")?.let { ref ->
            (ref as? java.lang.ref.WeakReference<*>)?.get()
        }
    }

    private fun getRepository(viewModel: Any?): Any? {
        if (viewModel != null) {
            getField(viewModel, "repository")?.let { return it }
        }
        return try {
            val repoCls = Class.forName("com.example.data.repository.PlaylistRepository")
            repoCls.getDeclaredField("INSTANCE").apply { isAccessible = true }.get(null)
        } catch (_: Throwable) {
            null
        }
    }

    private fun getPresetSource(getterName: String): Any? {
        return try {
            val presetCls = Class.forName("com.example.data.repository.PresetPlaylists")
            val instance = presetCls.getDeclaredField("INSTANCE").apply { isAccessible = true }.get(null)
            invokeNoArg(instance, getterName)
        } catch (_: Throwable) {
            null
        }
    }

    private fun createPlaylistSource(id: String, title: String, url: String, isPreset: Boolean): Any? {
        return try {
            val cls = Class.forName("com.example.data.repository.PlaylistSource")
            val ctor = cls.getConstructor(
                String::class.java,
                String::class.java,
                String::class.java,
                Boolean::class.javaPrimitiveType
            )
            ctor.newInstance(id, title, url, isPreset)
        } catch (t: Throwable) {
            Log.w(TAG, "createPlaylistSource failed", t)
            null
        }
    }

    private fun findMainActivity(context: Context): Activity? {
        var ctx: Context? = context
        while (ctx is ContextWrapper) {
            if (ctx is Activity && ctx.javaClass.name == "com.example.MainActivity") return ctx
            ctx = ctx.baseContext
        }
        return null
    }

    private fun getField(target: Any?, name: String): Any? {
        if (target == null) return null
        val cls = target.javaClass
        val key = "${cls.name}#$name"
        val cached = fieldCache[key]
        if (cached != null) {
            return try {
                cached.get(target)
            } catch (_: Throwable) {
                null
            }
        }
        var cur: Class<*>? = cls
        while (cur != null) {
            try {
                val f = cur.getDeclaredField(name).apply { isAccessible = true }
                fieldCache[key] = f
                return f.get(target)
            } catch (_: NoSuchFieldException) {
                cur = cur.superclass
            } catch (_: Throwable) {
                return null
            }
        }
        return null
    }

    private fun invokeNoArg(target: Any?, methodName: String): Any? {
        if (target == null) return null
        val cls = target.javaClass
        val key = "${cls.name}#$methodName"
        val cached = methodCache[key]
        if (cached != null) {
            return try {
                cached.invoke(target)
            } catch (_: Throwable) {
                null
            }
        }
        return try {
            val m = cls.getMethod(methodName).apply { isAccessible = true }
            methodCache[key] = m
            m.invoke(target)
        } catch (_: Throwable) {
            var cur: Class<*>? = cls
            while (cur != null) {
                val declared = cur.declaredMethods.firstOrNull {
                    it.name == methodName && it.parameterTypes.isEmpty()
                }
                if (declared != null) {
                    declared.isAccessible = true
                    methodCache[key] = declared
                    return try {
                        declared.invoke(target)
                    } catch (_: Throwable) {
                        null
                    }
                }
                cur = cur.superclass
            }
            null
        }
    }
}
