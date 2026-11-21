package com.example.epg

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.premium.AppUiTranslator
import kotlinx.coroutines.delay

@Composable
fun LiveUpcomingHeaderBadge(
    context: Context,
    channelId: String,
    channelName: String,
    streamUrl: String,
    isXtreamMode: Boolean,
    playlistUrl: String?,
    viewModel: Any?,
    isDark: Boolean,
    surfaceColor: Color,
    tileSurfaceColor: Color,
    borderColor: Color,
    primaryColor: Color,
    textPrimary: Color,
    textSecondary: Color,
    textMuted: Color,
    modifier: Modifier = Modifier
) {
    var epgState by remember(channelId, streamUrl) { mutableStateOf<EpgUiState>(EpgUiState.Loading) }
    var refreshNonce by remember { mutableIntStateOf(0) }
    var showScheduleDialog by remember { mutableStateOf(false) }
    var showEpgUrlDialog by remember { mutableStateOf(false) }
    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }

    val tightStyle = remember {
        TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))
    }

    // Periodic 30s ticker so current/upcoming show stays accurate while watching
    LaunchedEffect(channelId, streamUrl) {
        while (true) {
            delay(30_000L)
            nowMs = System.currentTimeMillis()
        }
    }

    LaunchedEffect(channelId, streamUrl, isXtreamMode, playlistUrl, refreshNonce) {
        nowMs = System.currentTimeMillis()
        epgState = EpgUiState.Loading
        epgState = LiveEpgManager.loadEpgForLiveChannel(
            context = context,
            channelId = channelId,
            channelName = channelName,
            streamUrl = streamUrl,
            isXtreamMode = isXtreamMode,
            playlistUrl = playlistUrl,
            viewModel = viewModel,
            forceRefresh = refreshNonce > 0
        )
        nowMs = System.currentTimeMillis()
    }

    val nextShowTitle = (epgState as? EpgUiState.Available)
        ?.upcoming
        ?.firstOrNull()
        ?.title
        ?.trim()
        ?.takeIf { it.isNotEmpty() }

    val detailText = when (epgState) {
        is EpgUiState.Loading -> AppUiTranslator.t("Loading...")
        is EpgUiState.Available -> nextShowTitle ?: AppUiTranslator.t("Not available")
        is EpgUiState.Unavailable -> AppUiTranslator.t("Not available")
    }

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .clickable {
                when (epgState) {
                    is EpgUiState.Available -> showScheduleDialog = true
                    is EpgUiState.Unavailable -> refreshNonce++
                    is EpgUiState.Loading -> Unit
                }
            }
            .padding(horizontal = 4.dp, vertical = 2.dp)
            .testTag("live_upcoming_header_badge"),
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(4.dp))
                .background(tileSurfaceColor)
                .border(0.6.dp, borderColor, RoundedCornerShape(4.dp))
                .padding(horizontal = 5.dp, vertical = 1.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = AppUiTranslator.t("UPCOMING"),
                color = if (nextShowTitle != null) textPrimary else textSecondary,
                fontSize = 7.5.sp,
                lineHeight = 9.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.35.sp,
                maxLines = 1,
                softWrap = false,
                style = tightStyle
            )
        }

        Text(
            text = detailText,
            color = if (nextShowTitle != null) textPrimary else textMuted,
            fontSize = 10.5.sp,
            lineHeight = 13.sp,
            fontWeight = if (nextShowTitle != null) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = tightStyle
        )
    }

    if (showScheduleDialog && epgState is EpgUiState.Available) {
        val avail = epgState as EpgUiState.Available
        FullUpcomingScheduleDialog(
            channelName = channelName,
            state = avail,
            nowMs = nowMs,
            isXtreamMode = isXtreamMode,
            surfaceColor = surfaceColor,
            tileSurfaceColor = tileSurfaceColor,
            borderColor = borderColor,
            primaryColor = primaryColor,
            textPrimary = textPrimary,
            textSecondary = textSecondary,
            textMuted = textMuted,
            onRefresh = {
                refreshNonce++
                showScheduleDialog = false
            },
            onConfigureEpg = {
                showScheduleDialog = false
                showEpgUrlDialog = true
            },
            onDismiss = { showScheduleDialog = false }
        )
    }

    if (showEpgUrlDialog) {
        CustomEpgUrlDialog(
            context = context,
            isDark = isDark,
            surfaceColor = surfaceColor,
            borderColor = borderColor,
            primaryColor = primaryColor,
            textPrimary = textPrimary,
            textSecondary = textSecondary,
            textMuted = textMuted,
            onSaved = {
                showEpgUrlDialog = false
                refreshNonce++
            },
            onDismiss = { showEpgUrlDialog = false }
        )
    }
}

@Composable
private fun FullUpcomingScheduleDialog(
    channelName: String,
    state: EpgUiState.Available,
    nowMs: Long,
    isXtreamMode: Boolean,
    surfaceColor: Color,
    tileSurfaceColor: Color,
    borderColor: Color,
    primaryColor: Color,
    textPrimary: Color,
    textSecondary: Color,
    textMuted: Color,
    onRefresh: () -> Unit,
    onConfigureEpg: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("live_upcoming_schedule_dialog"),
            shape = RoundedCornerShape(16.dp),
            color = surfaceColor,
            border = BorderStroke(1.dp, borderColor)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = AppUiTranslator.t("Upcoming Schedule"),
                            color = textPrimary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = channelName,
                            color = textSecondary,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .clickable(onClick = onRefresh),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = AppUiTranslator.t("Refresh schedule"),
                            tint = textSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .clickable(onClick = onDismiss),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = AppUiTranslator.t("Close"),
                            tint = textSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider(color = borderColor, thickness = 0.8.dp)
                Spacer(modifier = Modifier.height(10.dp))

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 360.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (state.current != null) {
                        item(key = "now_playing") {
                            val cur = state.current
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = primaryColor.copy(alpha = 0.11f),
                                border = BorderStroke(0.9.dp, primaryColor.copy(alpha = 0.4f))
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(10.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = AppUiTranslator.t("• LIVE NOW"),
                                            color = primaryColor,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.ExtraBold
                                        )
                                        Text(
                                            text = "${LiveEpgManager.formatTime(cur.startTimeMs)} – ${LiveEpgManager.formatTime(cur.endTimeMs)}",
                                            color = textSecondary,
                                            fontSize = 10.5.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                    Text(
                                        text = cur.title,
                                        color = textPrimary,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    if (cur.description.isNotBlank()) {
                                        Text(
                                            text = cur.description,
                                            color = textSecondary,
                                            fontSize = 11.sp,
                                            maxLines = 3,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(3.dp)
                                            .clip(RoundedCornerShape(2.dp))
                                            .background(borderColor)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth(cur.progressFraction(nowMs))
                                                .height(3.dp)
                                                .clip(RoundedCornerShape(2.dp))
                                                .background(primaryColor)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    if (state.upcoming.isEmpty()) {
                        item(key = "no_more_upcoming") {
                            Text(
                                text = AppUiTranslator.t("No upcoming details available"),
                                color = textMuted,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        }
                    } else {
                        item(key = "upcoming_header") {
                            Text(
                                text = AppUiTranslator.t("UPCOMING"),
                                color = textMuted,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.6.sp,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                        items(state.upcoming, key = { "${it.startTimeMs}_${it.title}" }) { prog ->
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = tileSurfaceColor,
                                border = BorderStroke(0.7.dp, borderColor)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(10.dp),
                                    verticalArrangement = Arrangement.spacedBy(3.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "${LiveEpgManager.formatTime(prog.startTimeMs)} – ${LiveEpgManager.formatTime(prog.endTimeMs)}",
                                            color = primaryColor,
                                            fontSize = 10.5.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            text = AppUiTranslator.t(LiveEpgManager.formatTimeUntil(prog.startTimeMs, nowMs)),
                                            color = textMuted,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                    Text(
                                        text = prog.title,
                                        color = textPrimary,
                                        fontSize = 12.5.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (prog.description.isNotBlank()) {
                                        Text(
                                            text = prog.description,
                                            color = textSecondary,
                                            fontSize = 11.sp,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                if (!isXtreamMode) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(onClick = onConfigureEpg)
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Link,
                            contentDescription = AppUiTranslator.t("Configure EPG URL"),
                            tint = primaryColor,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = AppUiTranslator.t("Set Custom XMLTV EPG URL"),
                            color = primaryColor,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CustomEpgUrlDialog(
    context: Context,
    isDark: Boolean,
    surfaceColor: Color,
    borderColor: Color,
    primaryColor: Color,
    textPrimary: Color,
    textSecondary: Color,
    textMuted: Color,
    onSaved: () -> Unit,
    onDismiss: () -> Unit
) {
    var urlInput by remember { mutableStateOf(LiveEpgManager.getCustomEpgUrl(context)) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("custom_epg_url_dialog"),
            shape = RoundedCornerShape(16.dp),
            color = surfaceColor,
            border = BorderStroke(1.dp, borderColor)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = AppUiTranslator.t("Playlist EPG URL (XMLTV)"),
                    color = textPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = AppUiTranslator.t("Optional: Paste an XMLTV (.xml or .xml.gz) guide URL for M3U playlists that don't include built-in EPG data."),
                    color = textSecondary,
                    fontSize = 11.5.sp
                )
                OutlinedTextField(
                    value = urlInput,
                    onValueChange = { urlInput = it },
                    singleLine = true,
                    placeholder = {
                        Text(
                            text = "https://example.com/epg.xml.gz",
                            color = textMuted,
                            fontSize = 12.sp
                        )
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = textPrimary,
                        unfocusedTextColor = textPrimary,
                        focusedBorderColor = primaryColor,
                        unfocusedBorderColor = borderColor,
                        cursorColor = primaryColor
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("custom_epg_url_input")
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            LiveEpgManager.saveCustomEpgUrl(context, "")
                            Toast.makeText(context, AppUiTranslator.tx("EPG URL cleared", context), Toast.LENGTH_SHORT).show()
                            onSaved()
                        },
                        border = BorderStroke(0.9.dp, borderColor),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(AppUiTranslator.t("Clear"), color = textSecondary, fontSize = 12.sp)
                    }
                    Button(
                        onClick = {
                            val trimmed = urlInput.trim()
                            if (trimmed.isNotEmpty() &&
                                !trimmed.startsWith("http://", ignoreCase = true) &&
                                !trimmed.startsWith("https://", ignoreCase = true)
                            ) {
                                Toast.makeText(
                                    context,
                                    AppUiTranslator.tx("Enter a valid http/https EPG URL", context),
                                    Toast.LENGTH_SHORT
                                ).show()
                                return@Button
                            }
                            LiveEpgManager.saveCustomEpgUrl(context, trimmed)
                            Toast.makeText(
                                context,
                                AppUiTranslator.tx(if (trimmed.isEmpty()) "EPG URL cleared" else "EPG URL saved", context),
                                Toast.LENGTH_SHORT
                            ).show()
                            onSaved()
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = primaryColor,
                            contentColor = Color.White
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("custom_epg_url_save_button")
                    ) {
                        Text(AppUiTranslator.t("Save"), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}
