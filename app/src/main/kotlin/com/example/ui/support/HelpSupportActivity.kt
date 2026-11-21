package com.example.ui.support

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color as AndroidColor
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.SupportAgent
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.premium.AppUiTranslator

class HelpSupportActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val isDark = resolveAppDarkMode(this)
        enableEdgeToEdge(
            statusBarStyle = if (isDark) {
                SystemBarStyle.dark(AndroidColor.TRANSPARENT)
            } else {
                SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT)
            },
            navigationBarStyle = if (isDark) {
                SystemBarStyle.dark(AndroidColor.TRANSPARENT)
            } else {
                SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT)
            }
        )
        setContent {
            MaterialTheme(
                colorScheme = if (isDark) {
                    darkColorScheme(
                        primary = Color(0xFFE50914),
                        background = Color(0xFF000000),
                        surface = Color(0xFF121216)
                    )
                } else {
                    lightColorScheme(
                        primary = Color(0xFFE50914),
                        background = Color(0xFFFFFFFF),
                        surface = Color(0xFFFFFFFF)
                    )
                }
            ) {
                HelpSupportScreen(
                    isDark = isDark,
                    onBack = { finish() }
                )
            }
        }
    }

    companion object {
        const val SUPPORT_EMAIL = "Datashield.studio@gmail.com"
        private const val GMAIL_PACKAGE = "com.google.android.gm"

        @JvmStatic
        fun launch(context: Context) {
            try {
                val intent = Intent(context, HelpSupportActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            } catch (_: Throwable) {
                Toast.makeText(
                    context,
                    AppUiTranslator.tx("Unable to open Help & Support", context),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        internal fun resolveAppDarkMode(context: Context): Boolean {
            try {
                val prefs = context.getSharedPreferences("hypnotix_prefs", Context.MODE_PRIVATE)
                val savedMode = prefs.getString("app_theme_mode", "system")?.lowercase()
                if (savedMode == "dark") return true
                if (savedMode == "light") return false
            } catch (_: Throwable) {
            }
            val nightMask = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
            return nightMask == Configuration.UI_MODE_NIGHT_YES
        }

        internal fun buildEmailSubject(selectedCategory: String, customTopic: String): String {
            val topic = customTopic.trim()
            val category = selectedCategory.trim()
            return when {
                topic.isNotEmpty() && category.isNotEmpty() && !topic.equals(category, ignoreCase = true) ->
                    "[$category] $topic"
                topic.isNotEmpty() -> topic
                category.isNotEmpty() -> "Hypnotix Help: $category"
                else -> "Hypnotix Help & Support"
            }
        }

        internal fun openGmailCompose(context: Context, subject: String, body: String): Boolean {
            val encodedSubject = Uri.encode(subject)
            val encodedBody = Uri.encode(body)
            val mailtoUri = Uri.parse("mailto:$SUPPORT_EMAIL?subject=$encodedSubject&body=$encodedBody")

            // 1. Direct Gmail app redirect (pre-filled To, Subject, Body — user only taps Send)
            val gmailIntent = Intent(Intent.ACTION_SENDTO).apply {
                data = mailtoUri
                putExtra(Intent.EXTRA_EMAIL, arrayOf(SUPPORT_EMAIL))
                putExtra(Intent.EXTRA_SUBJECT, subject)
                putExtra(Intent.EXTRA_TEXT, body)
                setPackage(GMAIL_PACKAGE)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            try {
                context.startActivity(gmailIntent)
                return true
            } catch (_: ActivityNotFoundException) {
                // Gmail not installed or disabled — fall back to default mail client
            } catch (_: Throwable) {
                // Fall back to default mail client
            }

            // 2. Fallback: system default email app with exact same pre-filled fields
            val genericMailto = Intent(Intent.ACTION_SENDTO).apply {
                data = mailtoUri
                putExtra(Intent.EXTRA_EMAIL, arrayOf(SUPPORT_EMAIL))
                putExtra(Intent.EXTRA_SUBJECT, subject)
                putExtra(Intent.EXTRA_TEXT, body)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            return try {
                context.startActivity(genericMailto)
                true
            } catch (_: Throwable) {
                Toast.makeText(
                    context,
                    AppUiTranslator.tx("No email app found on this device.", context),
                    Toast.LENGTH_LONG
                ).show()
                false
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HelpSupportScreen(
    isDark: Boolean,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    AppUiTranslator.rememberLanguageCode()
    val topics = remember {
        listOf(
            "Playback Issue",
            "Playlist / Xtream",
            "App Crash / Bug",
            "Feature Request",
            "Account / Premium",
            "Other"
        )
    }

    var selectedCategory by rememberSaveable { mutableStateOf("") }
    var topicText by rememberSaveable { mutableStateOf("") }
    var messageText by rememberSaveable { mutableStateOf("") }
    var messageError by rememberSaveable { mutableStateOf<String?>(null) }

    val primary = Color(0xFFE50914)
    val bgColor = if (isDark) Color(0xFF000000) else Color(0xFFFFFFFF)
    val cardColor = if (isDark) Color(0xFF131317) else Color(0xFFF1F5F9)
    val inputBg = if (isDark) Color(0xFF19191F) else Color(0xFFF8FAFC)
    val borderColor = if (isDark) Color(0xFF282830) else Color(0xFFCBD5E1)
    val textPrimary = if (isDark) Color(0xFFFFFFFF) else Color(0xFF0F172A)
    val textSecondary = if (isDark) Color(0xFFB0B0BA) else Color(0xFF475569)
    val textMuted = if (isDark) Color(0xFF7B7B86) else Color(0xFF64748B)

    Scaffold(
        containerColor = bgColor,
        contentWindowInsets = WindowInsets.systemBars,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = AppUiTranslator.t("Help & Support"),
                        color = textPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("support_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = AppUiTranslator.t("Back"),
                            tint = textPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = bgColor
                )
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
                .imePadding(),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 600.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header card
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = cardColor,
                    border = BorderStroke(1.dp, borderColor)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(primary.copy(alpha = 0.14f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.SupportAgent,
                                contentDescription = AppUiTranslator.t("Support"),
                                tint = primary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = AppUiTranslator.t("Need Help?"),
                                color = textPrimary,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = AppUiTranslator.t("Select a category or write your topic & message below, then tap Send."),
                                color = textSecondary,
                                fontSize = 12.sp,
                                lineHeight = 16.sp
                            )
                        }
                    }
                }

                // Quick topic selector chips (none pre-selected; user chooses)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = AppUiTranslator.t("Select Category"),
                        color = textPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        topics.forEach { category ->
                            val isSelected = selectedCategory == category
                            Surface(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(20.dp))
                                    .clickable {
                                        selectedCategory = if (isSelected) "" else category
                                    }
                                    .testTag("support_chip_${category.lowercase().replace(" ", "_")}"),
                                shape = RoundedCornerShape(20.dp),
                                color = if (isSelected) primary else cardColor,
                                border = BorderStroke(
                                    1.dp,
                                    if (isSelected) primary else borderColor
                                )
                            ) {
                                Text(
                                    text = AppUiTranslator.t(category),
                                    color = if (isSelected) Color.White else textSecondary,
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                                )
                            }
                        }
                    }
                }

                // Topic / Subject input
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = AppUiTranslator.t("Topic / Subject"),
                        color = textPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    OutlinedTextField(
                        value = topicText,
                        onValueChange = { topicText = it },
                        singleLine = true,
                        placeholder = {
                            Text(
                                text = AppUiTranslator.t("e.g. Channel not playing or Xtream login issue"),
                                color = textMuted,
                                fontSize = 13.sp
                            )
                        },
                        shape = RoundedCornerShape(12.dp),
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Sentences,
                            imeAction = ImeAction.Next
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = inputBg,
                            unfocusedContainerColor = inputBg,
                            focusedTextColor = textPrimary,
                            unfocusedTextColor = textPrimary,
                            focusedBorderColor = primary,
                            unfocusedBorderColor = borderColor,
                            cursorColor = primary
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("support_topic_input")
                    )
                }

                // Message input
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = AppUiTranslator.t("Your Message"),
                        color = textPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    OutlinedTextField(
                        value = messageText,
                        onValueChange = {
                            messageText = it
                            if (messageError != null && it.isNotBlank()) {
                                messageError = null
                            }
                        },
                        minLines = 5,
                        maxLines = 9,
                        isError = messageError != null,
                        placeholder = {
                            Text(
                                text = AppUiTranslator.t("Describe your issue, question, or feedback here..."),
                                color = textMuted,
                                fontSize = 13.sp
                            )
                        },
                        supportingText = messageError?.let { err ->
                            {
                                Text(
                                    text = err,
                                    color = primary,
                                    fontSize = 11.5.sp
                                )
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Sentences
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = inputBg,
                            unfocusedContainerColor = inputBg,
                            focusedTextColor = textPrimary,
                            unfocusedTextColor = textPrimary,
                            focusedBorderColor = primary,
                            unfocusedBorderColor = borderColor,
                            errorBorderColor = primary,
                            cursorColor = primary
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("support_message_input")
                    )
                }

                // Send button -> redirects to Gmail with pre-filled To, Subject, Message
                Button(
                    onClick = {
                        val trimmedMsg = messageText.trim()
                        if (trimmedMsg.isEmpty()) {
                            messageError = AppUiTranslator.tx("Please enter your message before sending.", context)
                            return@Button
                        }
                        val finalSubject = HelpSupportActivity.buildEmailSubject(
                            selectedCategory = selectedCategory,
                            customTopic = topicText
                        )
                        HelpSupportActivity.openGmailCompose(
                            context = context,
                            subject = finalSubject,
                            body = trimmedMsg
                        )
                    },
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = primary,
                        contentColor = Color.White
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("support_send_gmail_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.Send,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = AppUiTranslator.t("Send"),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }
}
