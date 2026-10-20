package com.example.ui.screens

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.theme.OttPrimary
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(
    onSplashFinished: () -> Unit,
    modifier: Modifier = Modifier
) {
    var startAnimation by remember { mutableStateOf(false) }

    val alphaAnim by animateFloatAsState(
        targetValue = if (startAnimation) 1f else 0f,
        animationSpec = tween(durationMillis = 900, easing = FastOutSlowInEasing),
        label = "splashAlpha"
    )

    val scaleAnim by animateFloatAsState(
        targetValue = if (startAnimation) 1f else 0.82f,
        animationSpec = tween(durationMillis = 900, easing = FastOutSlowInEasing),
        label = "splashScale"
    )

    // Infinite breathing glow on the center icon
    val infiniteTransition = rememberInfiniteTransition(label = "pulse_transition")
    val pulseGlow by infiniteTransition.animateFloat(
        initialValue = 0.88f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseGlow"
    )

    LaunchedEffect(Unit) {
        startAnimation = true
        delay(2200L)
        onSplashFinished()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF07090E))
            .statusBarsPadding()
            .navigationBarsPadding(),
        contentAlignment = Alignment.Center
    ) {
        // Subtle circular radial glow behind the logo
        Box(
            modifier = Modifier
                .size(340.dp)
                .scale(pulseGlow)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF4C0E14).copy(alpha = 0.55f),
                            Color(0xFF26070B).copy(alpha = 0.3f),
                            Color.Transparent
                        )
                    ),
                    shape = CircleShape
                )
        )

        // Floating decorative particles (Photo 1)
        Box(
            modifier = Modifier
                .size(6.dp)
                .offset(x = (-120).dp, y = (-180).dp)
                .background(Color(0xFFEF4444).copy(alpha = 0.45f), CircleShape)
        )
        Box(
            modifier = Modifier
                .size(8.dp)
                .offset(x = 130.dp, y = (-90).dp)
                .background(Color(0xFF3B82F6).copy(alpha = 0.35f), CircleShape)
        )
        Box(
            modifier = Modifier
                .size(5.dp)
                .offset(x = (-140).dp, y = 20.dp)
                .background(Color(0xFF38BDF8).copy(alpha = 0.3f), CircleShape)
        )
        Box(
            modifier = Modifier
                .size(7.dp)
                .offset(x = 150.dp, y = 110.dp)
                .background(Color(0xFFEF4444).copy(alpha = 0.5f), CircleShape)
        )
        Box(
            modifier = Modifier
                .size(5.dp)
                .offset(x = (-90).dp, y = 220.dp)
                .background(Color(0xFFEF4444).copy(alpha = 0.4f), CircleShape)
        )
        Box(
            modifier = Modifier
                .size(6.dp)
                .offset(x = 120.dp, y = 190.dp)
                .background(Color(0xFFEF4444).copy(alpha = 0.6f), CircleShape)
        )

        // Center Content Column (Photo 1)
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .alpha(alphaAnim)
                .scale(scaleAnim)
        ) {
            // App Logo in Rounded Dark Square with Neon Red Border (Photo 1)
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = Color.Black,
                border = BorderStroke(2.dp, Color(0xFFEF4444)),
                shadowElevation = 16.dp,
                modifier = Modifier
                    .size(124.dp)
                    .shadow(
                        elevation = 20.dp,
                        shape = RoundedCornerShape(28.dp),
                        ambientColor = Color(0xFFEF4444),
                        spotColor = Color(0xFFEF4444)
                    )
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_launcher_logo),
                        contentDescription = "Hypnotix Logo",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(26.dp))
                    )
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // Title: HYPNOTIX
            Text(
                text = "HYPNOTIX",
                color = Color.White,
                fontSize = 32.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 2.5.sp
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Pill Badge: IPTV PLAYER
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = Color(0xFF2C0A0E),
                border = BorderStroke(1.dp, Color(0xFF6B1822))
            ) {
                Text(
                    text = "IPTV PLAYER",
                    color = Color(0xFFEF4444),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Subtitle: Live TV • Movies • Series • Sports
            Text(
                text = "Live TV • Movies • Series • Sports",
                color = Color(0xFF94A3B8),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            )
        }

        // Bottom Footer (3 Loading Dots + Fast • Reliable • Multi-Source)
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 36.dp)
                .alpha(alphaAnim)
        ) {
            // 3 Dots
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .background(Color(0xFFEF4444).copy(alpha = 0.6f), CircleShape)
                )
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(Color(0xFFEF4444), CircleShape)
                )
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .background(Color(0xFFEF4444).copy(alpha = 0.6f), CircleShape)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = "Fast • Reliable • Multi-Source",
                color = Color(0xFF64748B),
                fontSize = 12.sp,
                letterSpacing = 1.sp
            )
        }
    }
}
