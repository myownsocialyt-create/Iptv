package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.AppLanguage
import com.example.data.model.AppThemeMode
import com.example.ui.theme.LocalAppColors
import com.example.ui.theme.OttPrimary

@Composable
fun SettingsScreen(
    currentLanguage: AppLanguage,
    currentThemeMode: AppThemeMode,
    autoReconnectEnabled: Boolean,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onToggleAutoReconnect: (Boolean) -> Unit,
    onSelectLanguage: (AppLanguage) -> Unit,
    onSelectThemeMode: (AppThemeMode) -> Unit,
    modifier: Modifier = Modifier
) {
    val appColors = LocalAppColors.current

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(appColors.background)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text("Settings", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = appColors.textPrimary)
        }

        // Appearance / Theme Section
        item {
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = appColors.surface),
                border = BorderStroke(1.dp, appColors.border),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.DarkMode, contentDescription = null, tint = OttPrimary)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("Appearance", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = appColors.textPrimary)
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    AppThemeMode.entries.forEach { mode ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelectThemeMode(mode) }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(mode.label, color = if (currentThemeMode == mode) OttPrimary else appColors.textPrimary, fontWeight = if (currentThemeMode == mode) FontWeight.Bold else FontWeight.Normal)
                            if (currentThemeMode == mode) {
                                Text("✓", color = OttPrimary, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        // Language Section
        item {
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = appColors.surface),
                border = BorderStroke(1.dp, appColors.border),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Language, contentDescription = null, tint = OttPrimary)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("App Language", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = appColors.textPrimary)
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    AppLanguage.entries.forEach { lang ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelectLanguage(lang) }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("${lang.flagEmoji}  ${lang.displayName} (${lang.nativeName})", color = if (currentLanguage == lang) OttPrimary else appColors.textPrimary, fontWeight = if (currentLanguage == lang) FontWeight.Bold else FontWeight.Normal)
                            if (currentLanguage == lang) {
                                Text("✓", color = OttPrimary, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        // Player Options
        item {
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = appColors.surface),
                border = BorderStroke(1.dp, appColors.border),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Auto Reconnect", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = appColors.textPrimary)
                            Text("Automatically reconnect when a live stream connection drops", fontSize = 12.sp, color = appColors.textSecondary)
                        }

                        Switch(
                            checked = autoReconnectEnabled,
                            onCheckedChange = onToggleAutoReconnect,
                            colors = SwitchDefaults.colors(checkedThumbColor = OttPrimary)
                        )
                    }
                }
            }
        }

        // About Hypnotix
        item {
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = appColors.surface),
                border = BorderStroke(1.dp, appColors.border),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Info, contentDescription = null, tint = OttPrimary)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("About Hypnotix Android", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = appColors.textPrimary)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Hypnotix is an IPTV streaming application with support for live TV, movies, and series from M3U playlists and Xtream servers.",
                        fontSize = 12.sp,
                        color = appColors.textSecondary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Version 1.0.0 • Modern Android & Media3 ExoPlayer",
                        fontSize = 11.sp,
                        color = appColors.textMuted
                    )
                }
            }
        }
    }
}
