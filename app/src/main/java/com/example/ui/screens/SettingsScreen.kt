package com.example.ui.screens

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Policy
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.player.IptvPlayerManager
import com.example.ui.components.PosterAdCard
import com.example.ui.theme.LocalAppColors
import com.example.ui.theme.OttPrimary

@Composable
fun SettingsScreen(
    playerManager: IptvPlayerManager,
    isDarkTheme: Boolean,
    onOpenThemeDialog: () -> Unit,
    onOpenLanguageDialog: () -> Unit,
    onOpenNoticeDialog: () -> Unit,
    onClearRecents: () -> Unit,
    modifier: Modifier = Modifier
) {
    val appColors = LocalAppColors.current
    val isAutoReconnect by playerManager.isAutoReconnectEnabled.collectAsState()

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
    ) {
        item {
            Text(
                text = "Settings",
                color = appColors.textPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 12.dp)
            )
        }

        // Poster Ad Card in Settings
        item {
            PosterAdCard(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp))
        }

        item {
            SettingsCategoryHeader("Playback & Streaming")
            SettingsCard {
                SettingsSwitchRow(
                    title = "Auto Reconnect Streams",
                    subtitle = "Automatically resume playback when internet reconnects",
                    checked = isAutoReconnect,
                    onCheckedChange = { playerManager.setAutoReconnectEnabled(it) }
                )
            }
        }

        item {
            Spacer(modifier = Modifier.height(16.dp))
            SettingsCategoryHeader("Appearance & Language")
            SettingsCard {
                SettingsClickableRow(
                    icon = Icons.Default.Palette,
                    title = "Theme",
                    subtitle = if (isDarkTheme) "Dark Theme (Default Cinema)" else "Light Theme",
                    onClick = onOpenThemeDialog
                )
                SettingsClickableRow(
                    icon = Icons.Default.Language,
                    title = "App Language",
                    subtitle = "Change language of the application",
                    onClick = onOpenLanguageDialog
                )
            }
        }

        item {
            Spacer(modifier = Modifier.height(16.dp))
            SettingsCategoryHeader("Data & Legal")
            SettingsCard {
                SettingsClickableRow(
                    icon = Icons.Default.Refresh,
                    title = "Clear Recently Watched",
                    subtitle = "Remove history of watched channels",
                    onClick = onClearRecents
                )
                SettingsClickableRow(
                    icon = Icons.Default.Policy,
                    title = "Content & Media Notice",
                    subtitle = "View disclaimer regarding public M3U sources",
                    onClick = onOpenNoticeDialog
                )
            }
        }

        item {
            Spacer(modifier = Modifier.height(24.dp))
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Hypnotix IPTV v1.0",
                    color = appColors.textSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "High-performance Media3 Player with Google Mobile Ads",
                    color = appColors.textSecondary.copy(alpha = 0.7f),
                    fontSize = 11.sp
                )
            }
        }
    }
}

@Composable
private fun SettingsCategoryHeader(text: String) {
    Text(
        text = text,
        color = OttPrimary,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(vertical = 6.dp, horizontal = 4.dp)
    )
}

@Composable
private fun SettingsCard(content: @Composable () -> Unit) {
    val appColors = LocalAppColors.current
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = appColors.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(vertical = 4.dp)) {
            content()
        }
    }
}

@Composable
private fun SettingsClickableRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    val appColors = LocalAppColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = OttPrimary, modifier = Modifier.size(22.dp))
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, color = appColors.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Text(text = subtitle, color = appColors.textSecondary, fontSize = 12.sp)
        }
    }
}

@Composable
private fun SettingsSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val appColors = LocalAppColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, color = appColors.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Text(text = subtitle, color = appColors.textSecondary, fontSize = 12.sp)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = OttPrimary)
        )
    }
}
