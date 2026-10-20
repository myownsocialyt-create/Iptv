package com.example.ad

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.LocalAppColors

/**
 * YouTube Companion Ad Card that appears directly below the video player in Portrait mode
 * while an in-stream ad is playing, exactly matching the bottom card in user reference photo (IMG_20261004_230821.jpg).
 */
@Composable
fun YouTubeCompanionAdCard(
    creative: InStreamAdCreative,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val appColors = LocalAppColors.current

    // Card background: crisp clean white matching the photo (or deep card in dark theme)
    val cardBg = if (appColors.isDark) Color(0xFF1E1E1E) else Color.White
    val headlineColor = if (appColors.isDark) Color.White else Color(0xFF0F0F0F)
    val subtitleColor = if (appColors.isDark) Color(0xFFAAAAAA) else Color(0xFF606060)
    val arrowBg = if (appColors.isDark) Color(0xFF2C2C2C) else Color(0xFFF2F2F2)
    val arrowTint = if (appColors.isDark) Color.White else Color(0xFF0F0F0F)

    Surface(
        onClick = {
            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(creative.targetUrl))
                context.startActivity(intent)
            } catch (_: Exception) {}
        },
        color = cardBg,
        border = BorderStroke(1.dp, if (appColors.isDark) Color(0xFF2E2E2E) else Color(0xFFE5E5E5)),
        modifier = modifier
            .fillMaxWidth()
            .testTag("youtube_companion_ad_card")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                // Red circular icon with white play triangle (matching Photo)
                Surface(
                    color = Color(0xFFFF0000),
                    shape = CircleShape,
                    modifier = Modifier.size(42.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Ad Icon",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column {
                    Text(
                        text = creative.ctaText.ifBlank { creative.title },
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = headlineColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = creative.subtitle,
                        fontSize = 12.sp,
                        color = subtitleColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Circular up-arrow icon button on the right (matching Photo)
            Surface(
                shape = CircleShape,
                color = arrowBg,
                modifier = Modifier.size(36.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowUp,
                        contentDescription = "Open Ad Link",
                        tint = arrowTint,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}
