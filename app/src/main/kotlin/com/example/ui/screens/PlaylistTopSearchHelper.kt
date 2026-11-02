package com.example.ui.screens

import android.app.Activity
import android.app.Dialog
import android.graphics.Color as AndroidColor
import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import android.view.ViewGroup
import android.view.WindowManager
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
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.i18n.AppStrings
import com.example.ui.theme.AppColors

/**
 * Helper to manage upper Search Bar on the Playlists screen.
 * Replaces the lower in-list search bar with a clean, top-bar search experience.
 */
object PlaylistTopSearchHelper {
    var isSearchOpen by mutableStateOf(false)
    var currentQuery by mutableStateOf("")
    private var boundQueryChange: ((String) -> Unit)? = null
    var boundAppColors: AppColors? = null
    var boundStrings: AppStrings? = null
    private var activeDialog: Dialog? = null

    fun bind(
        appColors: AppColors,
        searchQuery: String,
        onSearchQueryChange: (String) -> Unit,
        strings: AppStrings
    ) {
        boundAppColors = appColors
        boundStrings = strings
        boundQueryChange = onSearchQueryChange
        if (currentQuery.isEmpty() && searchQuery.isNotEmpty()) {
            currentQuery = searchQuery
        }
    }

    fun toggleSearch(activity: Activity? = null) {
        showSearchDialog(activity)
    }

    fun showSearchDialog(activity: Activity? = null) {
        val act = activity ?: GlobalSearchActivity.getActiveActivity() ?: return
        act.runOnUiThread {
            activeDialog?.dismiss()

            val dialog = Dialog(act, android.R.style.Theme_Translucent_NoTitleBar)
            activeDialog = dialog
            dialog.window?.let { window ->
                window.setGravity(Gravity.TOP)
                window.setLayout(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                window.setBackgroundDrawable(ColorDrawable(AndroidColor.TRANSPARENT))
                window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
            }

            val composeView = ComposeView(act).apply {
                setContent {
                    val colors = boundAppColors ?: GlobalSearchActivity.getCachedAppColors()
                    val strs = boundStrings ?: AppStrings()
                    RenderTopSearchBar(
                        appColors = colors,
                        strings = strs,
                        onDismiss = {
                            dialog.dismiss()
                            activeDialog = null
                        }
                    )
                }
            }

            dialog.setOnDismissListener {
                activeDialog = null
                isSearchOpen = false
            }

            dialog.setContentView(composeView)
            isSearchOpen = true
            dialog.show()
        }
    }

    fun updateQuery(newQuery: String) {
        currentQuery = newQuery
        boundQueryChange?.invoke(newQuery)
    }

    @Composable
    fun RenderTopSearchBar(
        appColors: AppColors,
        strings: AppStrings,
        onDismiss: () -> Unit = {}
    ) {
        val focusRequester = remember { FocusRequester() }
        val keyboardController = LocalSoftwareKeyboardController.current

        LaunchedEffect(Unit) {
            focusRequester.requestFocus()
            keyboardController?.show()
        }

        Surface(
            color = appColors.surface,
            tonalElevation = 4.dp,
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
                    onClick = onDismiss,
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
                                    text = "Search playlists...",
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
