package com.example.ui.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

private const val PRIVACY_POLICY_URL = "https://datashield-cloud.github.io/privacy-policy/Hypnotix/"

@Composable
fun MediaNoticeLetterDialog(
    onAccept: () -> Unit = {},
    onDismiss: () -> Unit = {}
) {
    val context = LocalContext.current
    val isDark = isSystemInDarkTheme()

    var isAgreed by remember { mutableStateOf(true) }

    val dialogBg = if (isDark) Color(0xFF18181B) else Color.White
    val titleColor = if (isDark) Color(0xFFF8FAFC) else Color(0xFF0F172A)
    val boxBg = if (isDark) Color(0xFF1E293B) else Color(0xFFF1F5F9)
    val bodyTextColor = if (isDark) Color(0xFFCBD5E1) else Color(0xFF334155)
    val brandRed = Color(0xFFE50914)
    val iconBg = if (isDark) Color(0xFF451A1A) else Color(0xFFFEE2E2)
    val cancelColor = if (isDark) Color(0xFF94A3B8) else Color(0xFF64748B)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .widthIn(max = 420.dp)
                .wrapContentHeight(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = dialogBg),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                // Header with Shield Icon & Title
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                            .background(iconBg),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = "Security Notice",
                            tint = brandRed,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column(
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = "LEGAL NOTICE & DISCLAIMER",
                            color = brandRed,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Important Player Notice",
                            color = titleColor,
                            fontSize = 19.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Scrollable Disclaimer Notice Container
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 160.dp, max = 260.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(boxBg)
                        .padding(14.dp)
                ) {
                    val scrollState = rememberScrollState()
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(scrollState)
                    ) {
                        Text(
                            text = "Hypnotix is purely a media player application. It does not contain, host, stream, provide, or distribute any media content, channels, or playlists.",
                            color = bodyTextColor,
                            fontSize = 13.5.sp,
                            lineHeight = 19.sp
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        Text(
                            text = buildAnnotatedString {
                                withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = bodyTextColor)) {
                                    append("• User Content Only: ")
                                }
                                append("Users are solely responsible for providing their own content or adding legally acquired M3U playlists / Xtream Codes API.")
                            },
                            color = bodyTextColor,
                            fontSize = 13.5.sp,
                            lineHeight = 19.sp
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = buildAnnotatedString {
                                withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = bodyTextColor)) {
                                    append("• Public/Preloaded Lists: ")
                                }
                                append("Any default, public, or sample playlists loaded into the app consist strictly of freely available, non-copyrighted, or open-source streams.")
                            },
                            color = bodyTextColor,
                            fontSize = 13.5.sp,
                            lineHeight = 19.sp
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = buildAnnotatedString {
                                withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = bodyTextColor)) {
                                    append("• No Third-Party Affiliation: ")
                                }
                                append("Hypnotix is not affiliated with, endorsed by, or connected to any third-party IPTV providers or content owners.")
                            },
                            color = bodyTextColor,
                            fontSize = 13.5.sp,
                            lineHeight = 19.sp
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = buildAnnotatedString {
                                withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = bodyTextColor)) {
                                    append("• Copyright Compliance: ")
                                }
                                append("We strictly do not condone or support the unauthorized streaming of copyrighted material. You must ensure you have appropriate rights to access any stream added or viewed within this app.")
                            },
                            color = bodyTextColor,
                            fontSize = 13.5.sp,
                            lineHeight = 19.sp
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            text = "By clicking \"Agree & Continue\", you acknowledge that you have read, understood, and agreed to these terms and conditions.",
                            color = bodyTextColor,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            lineHeight = 18.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Checkbox & Privacy Policy Link
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { isAgreed = !isAgreed },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = isAgreed,
                        onCheckedChange = { isAgreed = it },
                        colors = CheckboxDefaults.colors(
                            checkedColor = brandRed,
                            uncheckedColor = Color(0xFF94A3B8),
                            checkmarkColor = Color.White
                        )
                    )

                    Spacer(modifier = Modifier.width(6.dp))

                    Text(
                        text = "I agree to these terms & ",
                        color = titleColor,
                        fontSize = 13.5.sp
                    )

                    Text(
                        text = "Privacy Policy",
                        color = brandRed,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        textDecoration = TextDecoration.Underline,
                        modifier = Modifier.clickable {
                            openPrivacyPolicy(context)
                        }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Agree & Continue Button
                Button(
                    onClick = onAccept,
                    enabled = isAgreed,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = brandRed,
                        disabledContainerColor = brandRed.copy(alpha = 0.4f),
                        contentColor = Color.White,
                        disabledContentColor = Color.White.copy(alpha = 0.7f)
                    )
                ) {
                    Text(
                        text = "Agree & Continue",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Cancel Button
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(42.dp)
                ) {
                    Text(
                        text = "Cancel",
                        color = cancelColor,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

fun openPrivacyPolicy(context: Context? = null) {
    val resolvedContext = context ?: try {
        Class.forName("android.app.ActivityThread")
            .getMethod("currentApplication")
            .invoke(null) as? Context
    } catch (_: Throwable) {
        null
    }
    if (resolvedContext == null) return
    try {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(PRIVACY_POLICY_URL)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        resolvedContext.startActivity(intent)
    } catch (_: Exception) {
        Toast.makeText(resolvedContext, "Could not open browser", Toast.LENGTH_SHORT).show()
    }
}
