package com.example.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.example.ui.theme.LocalAppColors
import com.example.ui.theme.OttLiveRed
import com.example.ui.theme.OttPrimary

@Composable
fun ExitConfirmationDialog(
    onConfirmExit: () -> Unit,
    onDismiss: () -> Unit,
    onKeepWatching: () -> Unit = onDismiss
) {
    val appColors = LocalAppColors.current
    val dialogContainerColor = if (appColors.isDark) Color(0xFF171717) else Color.White

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = dialogContainerColor,
        titleContentColor = appColors.textPrimary,
        textContentColor = appColors.textSecondary,
        title = {
            Text(text = "Exit App?", color = appColors.textPrimary)
        },
        text = {
            Text(
                text = "Are you sure you want to exit Hypnotix IPTV?",
                color = appColors.textSecondary
            )
        },
        confirmButton = {
            TextButton(
                onClick = onConfirmExit,
                colors = ButtonDefaults.textButtonColors(contentColor = OttLiveRed)
            ) {
                Text(text = "Exit")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onKeepWatching,
                colors = ButtonDefaults.textButtonColors(contentColor = OttPrimary)
            ) {
                Text(text = "Keep Watching")
            }
        }
    )
}
