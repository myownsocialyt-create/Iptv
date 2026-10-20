package com.example.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.ui.theme.LocalAppColors
import com.example.ui.theme.OttPrimary

@Composable
fun MediaNoticeLetterDialog(
    onAccept: () -> Unit,
    onDismiss: () -> Unit
) {
    val appColors = LocalAppColors.current
    val dialogContainerColor = if (appColors.isDark) Color(0xFF171717) else Color.White

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = dialogContainerColor,
        titleContentColor = appColors.textPrimary,
        textContentColor = appColors.textSecondary,
        title = {
            Text(text = "Media Disclaimer", color = appColors.textPrimary, fontWeight = FontWeight.Bold)
        },
        text = {
            Text(
                text = "Hypnotix IPTV Player is a media player for publicly available M3U/HLS streams. It does not host, broadcast, or provide copyrighted media streams directly. Users are responsible for their own custom playlist links.",
                color = appColors.textSecondary,
                fontSize = 14.sp
            )
        },
        confirmButton = {
            Button(
                onClick = onAccept,
                colors = ButtonDefaults.buttonColors(containerColor = OttPrimary)
            ) {
                Text(text = "I Understand", color = Color.White)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = "Dismiss", color = appColors.textSecondary)
            }
        }
    )
}
