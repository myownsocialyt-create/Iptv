package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.AppLanguage
import com.example.ui.i18n.LocalAppStrings
import com.example.ui.theme.LocalAppColors
import com.example.ui.theme.OttPrimary

@Composable
fun LanguageSelectionDialog(
    currentLanguageCode: String,
    onLanguageSelected: (AppLanguage) -> Unit,
    onDismiss: () -> Unit
) {
    val appColors = LocalAppColors.current
    val strings = LocalAppStrings.current
    val dialogContainerColor = if (appColors.isDark) Color(0xFF171717) else Color.White

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = dialogContainerColor,
        titleContentColor = appColors.textPrimary,
        textContentColor = appColors.textPrimary,
        title = {
            Text(
                text = strings.chooseAppLanguage,
                color = appColors.textPrimary,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            LazyColumn(modifier = Modifier.fillMaxWidth().height(300.dp)) {
                items(AppLanguage.entries) { lang ->
                    val isSelected = lang.code.equals(currentLanguageCode, ignoreCase = true)
                    Surface(
                        color = if (isSelected) {
                            OttPrimary.copy(alpha = 0.14f)
                        } else if (appColors.isDark) {
                            Color(0xFF222226)
                        } else {
                            Color(0xFFF8FAFC)
                        },
                        border = BorderStroke(
                            width = 1.dp,
                            color = if (isSelected) OttPrimary.copy(alpha = 0.5f) else appColors.border
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clickable {
                                onLanguageSelected(lang)
                                onDismiss()
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "${lang.flagEmoji}  ${lang.nativeName}",
                                    color = if (isSelected) OttPrimary else appColors.textPrimary,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = lang.displayName,
                                    color = appColors.textSecondary,
                                    fontSize = 12.sp
                                )
                            }
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = OttPrimary
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, appColors.border)
            ) {
                Text(text = strings.close, color = appColors.textPrimary)
            }
        }
    )
}
