package com.example.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.OttPrimary

@Composable
fun MediaNoticeLetterDialog(
    onAccept: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onAccept,
        containerColor = Color(0xFF1E293B),
        title = {
            Text(
                text = "Public Content & Media Notice",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(280.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = "Hypnotix is an open IPTV player interface. It does not provide, host, or broadcast any audio/video content itself.\n\n" +
                            "• Any presets loaded by default reference publicly accessible internet playlists from iptv-org and open community broadcasts.\n" +
                            "• Users are responsible for adding their own legal M3U playlists and authorized Xtream Codes subscriptions.\n" +
                            "• Content copyright belongs to the respective broadcaster. If you are a copyright holder wishing to remove a stream, contact the source host directly.\n\n" +
                            "By continuing, you acknowledge and agree to these terms.",
                    color = Color(0xFFCBD5E1),
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onAccept,
                colors = ButtonDefaults.buttonColors(containerColor = OttPrimary),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(text = "I Understand & Accept", color = Color.White)
            }
        }
    )
}
