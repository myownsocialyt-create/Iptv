package com.example.premium

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.R

class SubscriptionActivity : ComponentActivity() {

    private lateinit var billingHelper: BillingHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        billingHelper = BillingHelper(this)

        setContent {
            SubscriptionContent(
                billingHelper = billingHelper,
                onClose = { finish() }
            )
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        billingHelper.destroy()
    }

    companion object {
        fun start(context: Context) {
            val intent = Intent(context, SubscriptionActivity::class.java)
            if (context !is Activity) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }
}

@Composable
fun SubscriptionDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val billingHelper = remember { BillingHelper(context) }

    DisposableEffect(Unit) {
        onDispose {
            billingHelper.destroy()
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false
        )
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color(0xFF0B0E14)
        ) {
            SubscriptionContent(
                billingHelper = billingHelper,
                onClose = onDismiss
            )
        }
    }
}

@Composable
fun SubscriptionContent(
    billingHelper: BillingHelper,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity

    var selectedPlan by remember { mutableStateOf(PremiumManager.PLAN_LIFETIME) }
    var isPurchasing by remember { mutableStateOf(false) }

    val monthlyPrice = billingHelper.priceDisplayMap[PremiumManager.PLAN_MONTHLY] ?: "₹690.00/Month"
    val yearlyPrice = billingHelper.priceDisplayMap[PremiumManager.PLAN_YEARLY] ?: "₹2,400.00/Year"
    val lifetimePrice = billingHelper.priceDisplayMap[PremiumManager.PLAN_LIFETIME] ?: "₹2,600.00"

    val scrollState = rememberScrollState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0B0E14))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Hero Top Section with Banner Image
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(240.dp)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.img_banner_sports_live),
                    contentDescription = "Premium Banner",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )

                // Gradient fade to dark background at the bottom
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color(0x66000000),
                                    Color(0x00000000),
                                    Color(0xCC0B0E14),
                                    Color(0xFF0B0E14)
                                )
                            )
                        )
                )

                // Close Button Top Right
                IconButton(
                    onClick = onClose,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .statusBarsPadding()
                        .padding(16.dp)
                        .size(36.dp)
                        .background(Color(0x80000000), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Title and Subtitle positioned over bottom of hero
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Upgrade Premium",
                        fontSize = 30.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        style = androidx.compose.ui.text.TextStyle(
                            brush = Brush.horizontalGradient(
                                colors = listOf(
                                    Color(0xFF5B93FF),
                                    Color(0xFF8AB4F8),
                                    Color(0xFF67D5FF)
                                )
                            )
                        )
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "Activate more opportunities to explore all treasures",
                        color = Color(0xFFA0A5B5),
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Feature Comparison Table
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
            ) {
                // Table Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        text = "Premium",
                        color = Color(0xFF38B6FF),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.width(68.dp),
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = "Basic",
                        color = Color(0xFF8E95A5),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.width(52.dp),
                        textAlign = TextAlign.Center
                    )
                }

                FeatureComparisonRow(
                    featureName = "Unlock All Access Features",
                    isPremiumChecked = true,
                    isBasicChecked = false
                )
                FeatureComparisonRow(
                    featureName = "No Ads - Remove All Ads",
                    isPremiumChecked = true,
                    isBasicChecked = false
                )
                FeatureComparisonRow(
                    featureName = "Unlimited Playlist & Channel",
                    isPremiumChecked = true,
                    isBasicChecked = false
                )
                FeatureComparisonRow(
                    featureName = "Picture-in-Picture & Xtream",
                    isPremiumChecked = true,
                    isBasicChecked = false
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 3 Subscription Plan Cards
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // 1. Lifetime Plan (Highlighted with "Most Popular" badge)
                PlanCard(
                    title = "Lifetime",
                    subtitle = "One-time payment",
                    price = lifetimePrice,
                    isSelected = selectedPlan == PremiumManager.PLAN_LIFETIME,
                    badgeText = "Most Popular",
                    onClick = { selectedPlan = PremiumManager.PLAN_LIFETIME }
                )

                // 2. Yearly Plan
                PlanCard(
                    title = "Yearly",
                    subtitle = "Auto-renewable",
                    price = yearlyPrice,
                    isSelected = selectedPlan == PremiumManager.PLAN_YEARLY,
                    badgeText = null,
                    onClick = { selectedPlan = PremiumManager.PLAN_YEARLY }
                )

                // 3. Monthly Plan
                PlanCard(
                    title = "Monthly",
                    subtitle = "Auto-renewable",
                    price = monthlyPrice,
                    isSelected = selectedPlan == PremiumManager.PLAN_MONTHLY,
                    badgeText = null,
                    onClick = { selectedPlan = PremiumManager.PLAN_MONTHLY }
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Continue Button
            Button(
                onClick = {
                    if (activity != null) {
                        isPurchasing = true
                        billingHelper.launchPurchaseFlow(activity, selectedPlan) { success, errorMsg ->
                            isPurchasing = false
                            if (success) {
                                Toast.makeText(activity, "🎉 Premium Activated!", Toast.LENGTH_LONG).show()
                                onClose()
                            } else if (errorMsg != null && errorMsg != "User canceled") {
                                Toast.makeText(activity, "Notice: $errorMsg", Toast.LENGTH_SHORT).show()
                            }
                        }
                    } else {
                        Toast.makeText(context, "Activating purchase flow...", Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .height(54.dp),
                shape = RoundedCornerShape(27.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF1A86F0)
                ),
                enabled = !isPurchasing
            ) {
                Text(
                    text = if (isPurchasing) "Connecting..." else "Continue",
                    color = Color.White,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Restore Purchases Button
            TextButton(
                onClick = {
                    if (activity != null) {
                        billingHelper.restorePurchases(activity) { success, msg ->
                            Toast.makeText(activity, msg, Toast.LENGTH_SHORT).show()
                            if (success) {
                                onClose()
                            }
                        }
                    }
                }
            ) {
                Text(
                    text = "Restore Purchases",
                    color = Color(0xFF64B5F6),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Privacy Policy & Terms of Service Links
            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Privacy Policy",
                    color = Color(0xFF38B6FF),
                    fontSize = 12.sp,
                    modifier = Modifier.clickable {
                        try {
                            val intent = Intent(
                                Intent.ACTION_VIEW,
                                Uri.parse("https://sites.google.com/view/hypnotix-iptv-privacy")
                            )
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            Toast.makeText(context, "Privacy Policy: Contact support", Toast.LENGTH_SHORT).show()
                        }
                    }
                )
                Text(
                    text = " & ",
                    color = Color(0xFF788195),
                    fontSize = 12.sp
                )
                Text(
                    text = "Terms of Service",
                    color = Color(0xFF38B6FF),
                    fontSize = 12.sp,
                    modifier = Modifier.clickable {
                        try {
                            val intent = Intent(
                                Intent.ACTION_VIEW,
                                Uri.parse("https://sites.google.com/view/hypnotix-iptv-terms")
                            )
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            Toast.makeText(context, "Terms: Contact support", Toast.LENGTH_SHORT).show()
                        }
                    }
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Legal Disclaimer
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 6.dp)
            ) {
                Text(
                    text = "• Payment will be charged to your Google account.",
                    color = Color(0xFF70778A),
                    fontSize = 11.sp,
                    lineHeight = 15.sp
                )
                Text(
                    text = "• Subscription renews automatically unless auto-renew is turned off.",
                    color = Color(0xFF70778A),
                    fontSize = 11.sp,
                    lineHeight = 15.sp
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun FeatureComparisonRow(
    featureName: String,
    isPremiumChecked: Boolean,
    isBasicChecked: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "• ",
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = featureName,
                color = Color(0xFFE2E4E9),
                fontSize = 13.5.sp,
                fontWeight = FontWeight.Normal
            )
        }

        Box(
            modifier = Modifier.width(68.dp),
            contentAlignment = Alignment.Center
        ) {
            if (isPremiumChecked) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Included in Premium",
                    tint = Color(0xFF38B6FF),
                    modifier = Modifier.size(19.dp)
                )
            }
        }

        Box(
            modifier = Modifier.width(52.dp),
            contentAlignment = Alignment.Center
        ) {
            if (isBasicChecked) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Included in Basic",
                    tint = Color(0xFF8E95A5),
                    modifier = Modifier.size(18.dp)
                )
            } else {
                Text(
                    text = "—",
                    color = Color(0xFF5A6275),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun PlanCard(
    title: String,
    subtitle: String,
    price: String,
    isSelected: Boolean,
    badgeText: String?,
    onClick: () -> Unit
) {
    val borderColor by animateColorAsState(
        targetValue = if (isSelected) Color(0xFF2CA8FE) else Color(0xFF232834),
        label = "borderColor"
    )

    val bgColor by animateColorAsState(
        targetValue = if (isSelected) Color(0xFF141A26) else Color(0xFF0F131C),
        label = "bgColor"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(32.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(bgColor, RoundedCornerShape(32.dp))
                .border(
                    border = BorderStroke(if (isSelected) 1.8.dp else 1.dp, borderColor),
                    shape = RoundedCornerShape(32.dp)
                )
                .padding(horizontal = 22.dp, vertical = 18.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Title and Subtitle
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        color = Color.White,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = subtitle,
                        color = Color(0xFF9098A9),
                        fontSize = 12.sp
                    )
                }

                // Price
                Text(
                    text = price,
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.width(14.dp))

                // Custom Radio Button
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .background(Color.Transparent, CircleShape)
                        .border(
                            BorderStroke(
                                2.dp,
                                if (isSelected) Color(0xFF2CA8FE) else Color(0xFF5A6275)
                            ),
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (isSelected) {
                        Box(
                            modifier = Modifier
                                .size(11.dp)
                                .background(Color(0xFF2CA8FE), CircleShape)
                        )
                    }
                }
            }
        }

        // "Most Popular" Badge pinned to top-right
        if (badgeText != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = (-26).dp, y = (-1).dp)
                    .background(
                        color = Color(0xFF2CA8FE),
                        shape = RoundedCornerShape(bottomStart = 10.dp, bottomEnd = 10.dp, topStart = 6.dp, topEnd = 6.dp)
                    )
                    .padding(horizontal = 12.dp, vertical = 3.dp)
            ) {
                Text(
                    text = badgeText,
                    color = Color.White,
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
