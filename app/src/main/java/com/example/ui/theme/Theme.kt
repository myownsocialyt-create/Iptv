package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

val OttGold = Color(0xFFF5B301)
val OttCyan = Color(0xFF00E5FF)

@Immutable
data class AppThemeColors(
    val isDark: Boolean,
    val background: Color,
    val surface: Color,
    val cardBackground: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val border: Color
)

val DarkAppColors = AppThemeColors(
    isDark = true,
    background = Color(0xFF0B0E17),
    surface = Color(0xFF121624),
    cardBackground = Color(0xFF191F30),
    textPrimary = Color(0xFFF8FAFC),
    textSecondary = Color(0xFF94A3B8),
    border = Color(0xFF262F45)
)

val LightAppColors = AppThemeColors(
    isDark = false,
    background = Color(0xFFF4F6FB),
    surface = Color(0xFFFFFFFF),
    cardBackground = Color(0xFFFFFFFF),
    textPrimary = Color(0xFF0F172A),
    textSecondary = Color(0xFF475569),
    border = Color(0xFFE2E8F0)
)

val LocalAppColors = staticCompositionLocalOf { DarkAppColors }

private val DarkColors = darkColorScheme(
    primary = OttGold,
    secondary = OttCyan,
    background = DarkAppColors.background,
    surface = DarkAppColors.surface,
    onPrimary = Color.Black,
    onBackground = DarkAppColors.textPrimary,
    onSurface = DarkAppColors.textPrimary
)

private val LightColors = lightColorScheme(
    primary = OttGold,
    secondary = OttCyan,
    background = LightAppColors.background,
    surface = LightAppColors.surface,
    onPrimary = Color.Black,
    onBackground = LightAppColors.textPrimary,
    onSurface = LightAppColors.textPrimary
)

@Composable
fun HypnotixTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val appColors = if (darkTheme) DarkAppColors else LightAppColors
    val colorScheme = if (darkTheme) DarkColors else LightColors

    CompositionLocalProvider(LocalAppColors provides appColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            content = content
        )
    }
}
