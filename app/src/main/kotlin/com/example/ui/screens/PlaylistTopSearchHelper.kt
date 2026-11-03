package com.example.ui.screens

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.NavigationItem
import com.example.ui.i18n.AppStrings
import com.example.ui.theme.AppColors
import com.example.ui.viewmodel.IptvViewModel
import kotlinx.coroutines.CoroutineScope

/**
 * Helper to manage upper Search Bar on the Channel, Favorites, and Playlists screens.
 * Replaces the lower in-list search bar with a clean, top-bar search experience.
 */
object PlaylistTopSearchHelper {
    @JvmField
    val `isSearchOpen$delegate`: MutableState<Boolean> = mutableStateOf(false)

    var isSearchOpen: Boolean
        get() = `isSearchOpen$delegate`.value
        set(value) {
            `isSearchOpen$delegate`.value = value
        }

    var isTopSearchVisible by mutableStateOf(false)
    var currentQuery by mutableStateOf("")
    private var searchOpenedForNav: NavigationItem? = null
    private var boundQueryChange: ((String) -> Unit)? = null
    var boundAppColors: AppColors? = null
    var boundStrings: AppStrings? = null
    var boundViewModel: IptvViewModel? = null

    fun bind(
        appColors: AppColors,
        searchQuery: String,
        onSearchQueryChange: (String) -> Unit,
        strings: AppStrings
    ) {
        boundAppColors = appColors
        boundStrings = strings
        boundQueryChange = onSearchQueryChange
        if (!isTopSearchVisible) {
            currentQuery = searchQuery
        }
    }

    fun toggleSearch(activity: Activity? = null) {
        if (isTopSearchVisible) {
            closeSearch()
        } else {
            showSearchDialog(activity)
        }
    }

    fun showSearchDialog(activity: Activity? = null) {
        isTopSearchVisible = true
    }

    fun openSearchForNav(navItem: NavigationItem) {
        searchOpenedForNav = navItem
        isTopSearchVisible = true
    }

    fun closeSearch() {
        isTopSearchVisible = false
        searchOpenedForNav = null
        updateQuery("")
    }

    fun updateQuery(newQuery: String) {
        currentQuery = newQuery
        boundQueryChange?.invoke(newQuery)
        if (searchOpenedForNav != NavigationItem.PLAYLISTS) {
            try {
                boundViewModel?.setSearchQuery(newQuery)
            } catch (_: Throwable) {}
        }
    }

    fun renderMainTopBarBridge(
        appColors: AppColors,
        selectedNavDelegate: MutableState<NavigationItem>,
        coroutineScope: CoroutineScope,
        drawerState: DrawerState,
        strings: AppStrings,
        uiStateDelegate: State<Any?>,
        viewModel: IptvViewModel,
        customPlaylistsDelegate: State<Any?>,
        composer: Composer,
        changed: Int
    ) {
        try {
            val method = PlaylistTopSearchHelper::class.java.declaredMethods.firstOrNull {
                it.name == "RenderMainTopBar" && it.parameterCount == 10
            }
            method?.invoke(
                this,
                appColors,
                selectedNavDelegate,
                coroutineScope,
                drawerState,
                strings,
                uiStateDelegate,
                viewModel,
                customPlaylistsDelegate,
                composer,
                changed
            )
        } catch (t: Throwable) {
            t.printStackTrace()
        }
    }

    @Composable
    fun RenderMainTopBar(
        appColors: AppColors,
        selectedNavDelegate: MutableState<NavigationItem>,
        coroutineScope: CoroutineScope,
        drawerState: DrawerState,
        strings: AppStrings,
        uiStateDelegate: State<Any?>,
        viewModel: IptvViewModel,
        customPlaylistsDelegate: State<Any?>
    ) {
        boundViewModel = viewModel
        boundAppColors = appColors
        boundStrings = strings

        val selectedNav = MainAppScreenKt.`access$MainAppScreen$lambda$12`(selectedNavDelegate)

        LaunchedEffect(selectedNav) {
            val openedNav = searchOpenedForNav
            if (isTopSearchVisible && openedNav != null && selectedNav != openedNav) {
                closeSearch()
            }
        }

        if (selectedNav != NavigationItem.XTREAM && selectedNav != NavigationItem.SETTINGS) {
            if (isTopSearchVisible && selectedNav != NavigationItem.HOME) {
                RenderTopSearchBar(
                    appColors = appColors,
                    strings = strings,
                    onDismiss = { closeSearch() }
                )
            } else {
                Surface(
                    modifier = Modifier.fillMaxWidth().statusBarsPadding(),
                    color = appColors.surface,
                    shadowElevation = 0.5.dp
                ) {
                    `MainAppScreenKt$MainAppScreen$25$1$1`(
                        coroutineScope,
                        drawerState,
                        appColors,
                        strings,
                        selectedNavDelegate,
                        uiStateDelegate,
                        viewModel,
                        customPlaylistsDelegate
                    ).invoke(currentComposer, 0)
                }
            }
        }
    }

    @Composable
    fun RenderTopSearchBar(
        appColors: AppColors,
        strings: AppStrings,
        onDismiss: () -> Unit = {}
    ) {
        val focusRequester = remember { FocusRequester() }
        val keyboardController = LocalSoftwareKeyboardController.current

        BackHandler(enabled = isTopSearchVisible) {
            keyboardController?.hide()
            onDismiss()
        }

        LaunchedEffect(Unit) {
            try {
                focusRequester.requestFocus()
                keyboardController?.show()
            } catch (_: Throwable) {}
        }

        Surface(
            color = appColors.surface,
            tonalElevation = 4.dp,
            shadowElevation = 2.dp,
            modifier = Modifier.fillMaxWidth().statusBarsPadding()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = {
                        keyboardController?.hide()
                        onDismiss()
                    },
                    modifier = Modifier.size(44.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Close Search",
                        tint = appColors.textPrimary
                    )
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(40.dp)
                        .background(
                            color = appColors.surfaceVariant,
                            shape = RoundedCornerShape(20.dp)
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
                            tint = appColors.textSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))

                        Box(modifier = Modifier.weight(1f)) {
                            if (currentQuery.isEmpty()) {
                                Text(
                                    text = "Search channels...",
                                    color = appColors.textMuted,
                                    fontSize = 14.sp
                                )
                            }
                            BasicTextField(
                                value = currentQuery,
                                onValueChange = { updateQuery(it) },
                                singleLine = true,
                                textStyle = TextStyle(
                                    color = appColors.textPrimary,
                                    fontSize = 14.sp
                                ),
                                cursorBrush = SolidColor(appColors.primary),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                keyboardActions = KeyboardActions(onSearch = {
                                    keyboardController?.hide()
                                }),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .focusRequester(focusRequester)
                            )
                        }

                        if (currentQuery.isNotEmpty()) {
                            IconButton(
                                onClick = { updateQuery("") },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Clear",
                                    tint = appColors.textSecondary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
