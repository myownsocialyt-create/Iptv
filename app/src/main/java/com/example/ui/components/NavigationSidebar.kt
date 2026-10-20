package com.example.ui.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.i18n.LocalAppStrings
import com.example.ui.i18n.getLocalizedLabel
import com.example.ui.theme.LocalAppColors
import com.example.ui.theme.OttPrimary

enum class NavigationItem(val label: String, val icon: ImageVector) {
    HOME("Home", Icons.Default.Home),
    CHANNELS("Channel", Icons.Default.Tv),
    CATEGORIES("Categories", Icons.Default.Category),
    FAVORITES("Favorites", Icons.Default.Favorite),
    PLAYLISTS("Playlists", Icons.Default.PlaylistPlay),
    XTREAM("Xtream", Icons.Default.Movie),
    SETTINGS("Settings", Icons.Default.Settings)
}

@Composable
fun NavigationSidebar(
    selectedItem: NavigationItem,
    onItemSelected: (NavigationItem) -> Unit,
    onOpenDisclaimerDialog: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val appColors = LocalAppColors.current
    val strings = LocalAppStrings.current
    val scrollState = rememberScrollState()

    Surface(
        color = appColors.surface,
        modifier = modifier
            .fillMaxHeight()
            .width(280.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp, vertical = 20.dp)
        ) {
            // App Branding Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_iptv_logo),
                    contentDescription = "Hypnotix Logo",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(10.dp))
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Hypnotix",
                            color = appColors.textPrimary,
                            fontSize = 19.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            color = if (appColors.isDark) OttPrimary.copy(alpha = 0.22f) else Color(0xFFFEE2E2),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = "IPTV",
                                color = OttPrimary,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.ExtraBold,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Text(
                        text = strings.liveTvStreams,
                        color = appColors.textSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Normal
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            HorizontalDivider(color = appColors.border, thickness = 1.dp)
            Spacer(modifier = Modifier.height(10.dp))

            // Primary Navigation Items
            val navItems = listOf(
                NavigationItem.HOME,
                NavigationItem.CATEGORIES,
                NavigationItem.FAVORITES,
                NavigationItem.SETTINGS
            )

            navItems.forEach { item ->
                val isSelected = item == selectedItem
                val selectedBg = if (appColors.isDark) OttPrimary.copy(alpha = 0.18f) else Color(0xFFFFECEC)
                val localizedLabel = item.getLocalizedLabel(strings)
                Surface(
                    color = if (isSelected) selectedBg else Color.Transparent,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp)
                        .clickable { onItemSelected(item) }
                        .testTag("nav_drawer_${item.name.lowercase()}")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = item.icon,
                            contentDescription = localizedLabel,
                            tint = if (isSelected) OttPrimary else if (appColors.isDark) Color(0xFFCBD5E1) else Color(0xFF334155),
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(
                            text = localizedLabel,
                            color = if (isSelected) OttPrimary else appColors.textPrimary,
                            fontSize = 15.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = appColors.border, thickness = 1.dp)
            Spacer(modifier = Modifier.height(16.dp))

            // Section: SHARE & SUPPORT
            Text(
                text = strings.shareAndSupport,
                color = appColors.textMuted,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.8.sp,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
            )

            // Rate Us
            DrawerSupportItem(
                icon = Icons.Default.Star,
                title = strings.rateUs,
                subtitle = strings.rateUsDesc,
                trailingIcon = Icons.AutoMirrored.Filled.OpenInNew,
                onClick = {
                    openPlayStore(context)
                }
            )

            // Share App
            DrawerSupportItem(
                icon = Icons.Default.Share,
                title = strings.shareApp,
                subtitle = strings.shareAppDesc,
                trailingIcon = Icons.Default.Share,
                onClick = {
                    shareApp(context)
                }
            )

            // Help & Support
            DrawerSupportItem(
                icon = Icons.Default.HelpOutline,
                title = strings.helpAndSupport,
                subtitle = strings.helpAndSupportDesc,
                onClick = {
                    sendFeedback(context)
                }
            )

            // Privacy Policy
            DrawerSupportItem(
                icon = Icons.Default.Security,
                title = strings.privacyPolicy,
                subtitle = strings.privacyPolicyDesc,
                trailingIcon = Icons.AutoMirrored.Filled.OpenInNew,
                onClick = onOpenDisclaimerDialog
            )

            // App Version
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = if (appColors.isDark) Color(0xFFCBD5E1) else Color(0xFF0F172A),
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = strings.appVersion,
                            color = appColors.textPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            color = appColors.surfaceVariant,
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = "v1.0",
                                color = appColors.textSecondary,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Text(
                        text = "v1.0 • Hypnotix by DataShield",
                        color = appColors.textSecondary,
                        fontSize = 11.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun DrawerSupportItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    trailingIcon: ImageVector? = null,
    onClick: () -> Unit
) {
    val appColors = LocalAppColors.current
    Surface(
        color = Color.Transparent,
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 2.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (appColors.isDark) Color(0xFFCBD5E1) else Color(0xFF0F172A),
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = appColors.textPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = subtitle,
                    color = appColors.textSecondary,
                    fontSize = 11.sp
                )
            }
            if (trailingIcon != null) {
                Icon(
                    imageVector = trailingIcon,
                    contentDescription = null,
                    tint = if (appColors.isDark) Color(0xFF64748B) else Color(0xFF94A3B8),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

private fun openPlayStore(context: Context) {
    try {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=com.hypnotix.iptv")).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    } catch (_: Exception) {
        try {
            val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=com.hypnotix.iptv")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(webIntent)
        } catch (e: Exception) {
            Toast.makeText(context, "Cannot open Play Store", Toast.LENGTH_SHORT).show()
        }
    }
}

private fun shareApp(context: Context) {
    try {
        val sendIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, "Watch live IPTV channels on Android with Hypnotix! Download now: https://play.google.com/store/apps/details?id=com.hypnotix.iptv")
            type = "text/plain"
        }
        val shareIntent = Intent.createChooser(sendIntent, "Share Hypnotix")
        shareIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(shareIntent)
    } catch (e: Exception) {
        Toast.makeText(context, "Cannot share app", Toast.LENGTH_SHORT).show()
    }
}

private fun sendFeedback(context: Context) {
    try {
        val emailIntent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("mailto:techeditz8@gmail.com")
            putExtra(Intent.EXTRA_SUBJECT, "Hypnotix Feedback & Support")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(emailIntent)
    } catch (_: Exception) {
        Toast.makeText(context, "Contact: techeditz8@gmail.com", Toast.LENGTH_LONG).show()
    }
}
