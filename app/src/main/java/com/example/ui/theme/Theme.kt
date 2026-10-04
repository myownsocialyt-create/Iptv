package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.example.data.model.AppThemeMode

data class AppColors(
    val background: Color,
    val surface: Color,
    val surfaceVariant: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val border: Color,
    val bottomBarBg: Color,
    val isDark: Boolean
)

val LocalAppColors = staticCompositionLocalOf {
    AppColors(
        background = Color.White,
        surface = Color.White,
        surfaceVariant = Slate100,
        textPrimary = Slate900,
        textSecondary = Slate600,
        textMuted = Slate400,
        border = Slate200,
        bottomBarBg = Color.White,
        isDark = false
    )
}

private val DarkColorScheme = darkColorScheme(
    primary = OttPrimary,
    secondary = OttGold,
    background = Color(0xFF0F172A),
    surface = Color(0xFF1E293B),
    onPrimary = Color.White,
    onSecondary = Color.Black,
    onBackground = Color.White,
    onSurface = Color.White
)

private val LightColorScheme = lightColorScheme(
    primary = OttPrimary,
    secondary = OttGold,
    background = Color(0xFFF8FAFC),
    surface = Color.White,
    onPrimary = Color.White,
    onSecondary = Color.Black,
    onBackground = Slate900,
    onSurface = Slate900
)

@Composable
fun HypnotixTheme(
    themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    content: @Composable () -> Unit
) {
    val isDark = when (themeMode) {
        AppThemeMode.SYSTEM -> isSystemInDarkTheme()
        AppThemeMode.DARK -> true
        AppThemeMode.LIGHT -> false
    }

    val appColors = if (isDark) {
        AppColors(
            background = Color(0xFF0B1120),
            surface = Color(0xFF151F32),
            surfaceVariant = Color(0xFF1E293B),
            textPrimary = Color.White,
            textSecondary = Slate400,
            textMuted = Slate500,
            border = Color(0xFF334155),
            bottomBarBg = Color(0xFF0F172A),
            isDark = true
        )
    } else {
        AppColors(
            background = Color(0xFFF8FAFC),
            surface = Color.White,
            surfaceVariant = Slate100,
            textPrimary = Slate900,
            textSecondary = Slate600,
            textMuted = Slate400,
            border = Slate200,
            bottomBarBg = Color.White,
            isDark = false
        )
    }

    val colorScheme = if (isDark) DarkColorScheme else LightColorScheme

    CompositionLocalProvider(LocalAppColors provides appColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}
