package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.LocalAppColors
import com.example.ui.theme.OttPrimary

@Composable
fun ThemeSelectionDialog(
    isDarkTheme: Boolean,
    onThemeSelected: (Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    val appColors = LocalAppColors.current
    val dialogContainerColor = if (appColors.isDark) Color(0xFF171717) else Color.White

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = dialogContainerColor,
        titleContentColor = appColors.textPrimary,
        textContentColor = appColors.textPrimary,
        title = {
            Text(text = "Choose App Theme", color = appColors.textPrimary, fontWeight = FontWeight.Bold)
        },
        text = {
            Column {
                // Dark Theme Option
                Surface(
                    color = if (isDarkTheme) {
                        OttPrimary.copy(alpha = 0.14f)
                    } else if (appColors.isDark) {
                        Color(0xFF222226)
                    } else {
                        Color(0xFFF8FAFC)
                    },
                    border = BorderStroke(
                        width = 1.dp,
                        color = if (isDarkTheme) OttPrimary.copy(alpha = 0.5f) else appColors.border
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clickable { onThemeSelected(true) }
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(imageVector = Icons.Default.DarkMode, contentDescription = null, tint = OttPrimary)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Dark Mode",
                            color = if (isDarkTheme) OttPrimary else appColors.textPrimary,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp,
                            modifier = Modifier.weight(1f)
                        )
                        if (isDarkTheme) {
                            Icon(imageVector = Icons.Default.Check, contentDescription = null, tint = OttPrimary)
                        }
                    }
                }

                // Light Theme Option
                Surface(
                    color = if (!isDarkTheme) {
                        OttPrimary.copy(alpha = 0.14f)
                    } else if (appColors.isDark) {
                        Color(0xFF222226)
                    } else {
                        Color(0xFFF8FAFC)
                    },
                    border = BorderStroke(
                        width = 1.dp,
                        color = if (!isDarkTheme) OttPrimary.copy(alpha = 0.5f) else appColors.border
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clickable { onThemeSelected(false) }
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(imageVector = Icons.Default.LightMode, contentDescription = null, tint = OttPrimary)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Light Mode",
                            color = if (!isDarkTheme) OttPrimary else appColors.textPrimary,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp,
                            modifier = Modifier.weight(1f)
                        )
                        if (!isDarkTheme) {
                            Icon(imageVector = Icons.Default.Check, contentDescription = null, tint = OttPrimary)
                        }
                    }
                }
            }
        },
        confirmButton = {}
    )
}
