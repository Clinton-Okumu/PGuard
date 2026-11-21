package com.example.pguard.presentation.premium

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.pguard.R
import com.example.pguard.presentation.theme.PGuardTheme
import com.example.pguard.presentation.theme.pguardColors

enum class PremiumPlan(
    val id: String,
    val price: String,
    val period: String,
    val isRecommended: Boolean = false,
    val discountBadge: String? = null
) {
    MONTHLY("monthly", "Ksh 200.00", "monthly"),
    QUARTERLY("quarterly", "Ksh 600.00", "quarterly"),
    YEARLY("yearly", "Ksh 2,000.00", "yearly", isRecommended = true, discountBadge = "Save 16%"),
    LIFETIME("one_time", "Ksh 9,500.00", "one-time")
}

@Composable
fun PremiumScreen(
    onBackClick: () -> Unit,
    onCheckoutClick: (PremiumPlan) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    BackHandler(onBack = onBackClick)

    var selectedPlan by remember { mutableStateOf(PremiumPlan.YEARLY) }
    var showHelpDialog by remember { mutableStateOf(false) }

    val benefits = remember {
        listOf(
            "Uninstall Protection (full access)",
            "Focus Mode (full access)",
            "Block Unlimited apps & websites",
            "Use on up to two devices",
            "No ads"
        )
    }

    val primary = MaterialTheme.colorScheme.primary

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 32.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBackClick) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_arrow_back),
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Text(
                    text = "Premium",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground
                )

                IconButton(onClick = { showHelpDialog = true }) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_help_circle),
                        contentDescription = "Help",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 22.dp)
                ) {
                    Text(
                        text = "What you get",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Spacer(modifier = Modifier.height(18.dp))

                    benefits.forEachIndexed { index, benefit ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_check_circle_green),
                                contentDescription = null,
                                tint = Color.Unspecified,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(14.dp))
                            Text(
                                text = benefit,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        if (index < benefits.lastIndex) {
                            Spacer(modifier = Modifier.height(14.dp))
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 18.dp)
                ) {
                    Text(
                        text = "Choose your plan",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    PremiumPlan.entries.forEachIndexed { index, plan ->
                        val isSelected = (plan == selectedPlan)
                        PlanRowItem(
                            plan = plan,
                            isSelected = isSelected,
                            onSelect = { selectedPlan = plan }
                        )

                        if (index < PremiumPlan.entries.lastIndex) {
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant,
                                thickness = 0.8.dp,
                                modifier = Modifier.padding(horizontal = 20.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "*3-Day Money-Back Guarantee",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(18.dp))

            Button(
                onClick = {
                    onCheckoutClick(selectedPlan)
                    Toast.makeText(
                        context,
                        "Proceeding with ${selectedPlan.price} / ${selectedPlan.period}",
                        Toast.LENGTH_SHORT
                    ).show()
                },
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .height(54.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_ribbon_badge),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "CONTINUE TO CHECKOUT",
                        style = MaterialTheme.typography.labelLarge.copy(letterSpacing = 0.8.sp),
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }

            Spacer(modifier = Modifier.height(26.dp))

            Text(
                text = "You can cancel or manage your subscription anytime on Google Play Store.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp)
            )
        }
    }

    if (showHelpDialog) {
        AlertDialog(
            onDismissRequest = { showHelpDialog = false },
            title = {
                Text(
                    text = "PGuard Premium Help",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "With PGuard Premium, you unlock full content blocking without limits, tamper-proof protection, and cross-device sync.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.88f)
                    )
                    Text(
                        text = "• Subscriptions renew automatically unless canceled at least 24 hours before the end of the period.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "• Manage or cancel your subscriptions anytime in Google Play Store > Subscriptions.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { showHelpDialog = false },
                    colors = ButtonDefaults.textButtonColors(contentColor = primary)
                ) {
                    Text("Got it", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                }
            },
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            shape = MaterialTheme.shapes.medium
        )
    }
}

@Composable
private fun PlanRowItem(
    plan: PremiumPlan,
    isSelected: Boolean,
    onSelect: () -> Unit
) {
    val rowBackground by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.surfaceContainerHigh else Color.Transparent,
        animationSpec = tween(durationMillis = 150),
        label = "planRowBackground"
    )
    val primary = MaterialTheme.colorScheme.primary
    val successColor = MaterialTheme.pguardColors.success

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .background(rowBackground)
            .clickable(onClick = onSelect)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(CircleShape)
                .border(
                    width = if (isSelected) 2.2.dp else 1.8.dp,
                    color = if (isSelected) primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            if (isSelected) {
                Box(
                    modifier = Modifier
                        .size(11.dp)
                        .clip(CircleShape)
                        .background(primary)
                )
            }
        }

        Spacer(modifier = Modifier.width(16.dp))

        Text(
            text = "${plan.price} / ${plan.period}",
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
            color = MaterialTheme.colorScheme.onSurface
        )

        if (plan.isRecommended) {
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "(Recommended)",
                color = successColor,
                style = MaterialTheme.typography.labelMedium
            )
        }

        if (plan.discountBadge != null) {
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = plan.discountBadge,
                color = successColor,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun PremiumScreenPreview() {
    PGuardTheme {
        PremiumScreen(onBackClick = {})
    }
}
