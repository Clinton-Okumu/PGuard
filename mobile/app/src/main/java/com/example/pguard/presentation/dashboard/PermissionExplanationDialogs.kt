package com.example.pguard.presentation.dashboard

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.pguard.R
import com.example.pguard.presentation.theme.CoralPrimary

@Composable
fun PermissionGuideDialog(
    title: String,
    category: String,
    iconRes: Int,
    description: String,
    steps: List<String>,
    confirmLabel: String = "Open Settings",
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = CardBackground,
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .background(CoralPrimary.copy(alpha = 0.15f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(id = iconRes),
                            contentDescription = null,
                            tint = CoralPrimary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Column {
                        Text(
                            text = title,
                            fontSize = 17.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = category,
                            fontSize = 12.sp,
                            color = CoralPrimary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = description,
                    fontSize = 13.5.sp,
                    color = Color.White.copy(alpha = 0.85f),
                    lineHeight = 20.sp
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "HOW TO ACTIVATE IN SETTINGS:",
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White.copy(alpha = 0.5f),
                    letterSpacing = 0.5.sp
                )

                Spacer(modifier = Modifier.height(10.dp))

                steps.forEachIndexed { index, step ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(22.dp)
                                .background(Color.White.copy(alpha = 0.08f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "${index + 1}",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = CoralPrimary
                            )
                        }
                        Text(
                            text = step,
                            fontSize = 13.sp,
                            color = Color.White.copy(alpha = 0.9f),
                            lineHeight = 18.sp,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel", color = Color.White.copy(alpha = 0.7f))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = onConfirm,
                        colors = ButtonDefaults.buttonColors(containerColor = CoralPrimary),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = confirmLabel,
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AccessibilityExplanationDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = CardBackground,
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(22.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // Header
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .background(CoralPrimary.copy(alpha = 0.15f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_accessibility),
                            contentDescription = null,
                            tint = CoralPrimary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "Accessibility Disclosure",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "Prominent In-App Consent",
                            fontSize = 12.sp,
                            color = CoralPrimary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
                Spacer(modifier = Modifier.height(14.dp))

                // Core explanation
                Text(
                    text = "PGuard uses Android's AccessibilityService API solely for real-time digital wellbeing protection and content filtering.",
                    fontSize = 13.sp,
                    color = Color.White.copy(alpha = 0.85f),
                    lineHeight = 19.sp
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Section 1: What Data Is Monitored
                DisclosureSection(
                    title = "WHAT DATA IS INSPECTED",
                    items = listOf(
                        "Web Browser URLs: Scanned in real time to detect and block explicit or restricted websites.",
                        "On-Screen Text & Metadata: Inspected to detect banned keywords in feeds and short-form video tabs (Shorts, Reels, TikTok)."
                    )
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Section 2: Non-Destructive Protection
                DisclosureSection(
                    title = "HOW IT PROTECTS YOU",
                    items = listOf(
                        "Two-Tier State Changes: Applies a targeted feed blur or non-destructive overlay over flagged content without terminating your host apps.",
                        "Clear Dismissal: Displays a prominent 'Go Back' escape button and back navigation at all times."
                    )
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Section 3: Strict Privacy Guarantee
                DisclosureSection(
                    title = "PRIVACY & SECURITY GUARANTEE",
                    items = listOf(
                        "100% On-Device: All content evaluation runs locally on your phone.",
                        "Zero Data Collection: No personal data, passwords, keystrokes, messages, or financial information is ever logged, stored, or transmitted off your device."
                    )
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Activation steps
                Text(
                    text = "ACTIVATION STEPS:",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White.copy(alpha = 0.5f),
                    letterSpacing = 0.5.sp
                )
                Spacer(modifier = Modifier.height(8.dp))

                listOf(
                    "Tap \"Agree & Open Settings\" below.",
                    "Select \"Installed apps\" (or \"Downloaded services\").",
                    "Tap \"PGuard Protection Service\" and toggle it ON."
                ).forEachIndexed { index, step ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(20.dp)
                                .background(Color.White.copy(alpha = 0.08f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "${index + 1}",
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = CoralPrimary
                            )
                        }
                        Text(
                            text = step,
                            fontSize = 12.5.sp,
                            color = Color.White.copy(alpha = 0.9f),
                            lineHeight = 17.sp,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Action buttons (Decline vs Affirmative Consent)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Decline", color = Color.White.copy(alpha = 0.7f), fontSize = 13.5.sp)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = onConfirm,
                        colors = ButtonDefaults.buttonColors(containerColor = CoralPrimary),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = "Agree & Open Settings",
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.5.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DisclosureSection(
    title: String,
    items: List<String>
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White.copy(alpha = 0.04f), RoundedCornerShape(12.dp))
            .padding(10.dp)
    ) {
        Text(
            text = title,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = CoralPrimary,
            letterSpacing = 0.5.sp
        )
        Spacer(modifier = Modifier.height(6.dp))
        items.forEach { item ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "•",
                    color = CoralPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = item,
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.85f),
                    lineHeight = 16.5.sp
                )
            }
        }
    }
}

@Composable
fun OverlayExplanationDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    PermissionGuideDialog(
        title = "Display Pop-up Window",
        category = "Shield & Blocked Screen",
        iconRes = R.drawable.ic_blocked_screen,
        description = "Android requires this permission so PGuard can display the blocked warning screen, timer, and safe redirect immediately over restricted websites and apps.",
        steps = listOf(
            "Tap \"Open Settings\" below to open Appear on Top settings.",
            "Locate and select \"PGuard\" in the list of applications.",
            "Turn the switch ON for \"Allow display over other apps\" (or \"Appear on top\")."
        ),
        confirmLabel = "Open Settings",
        onConfirm = onConfirm,
        onDismiss = onDismiss
    )
}

@Composable
fun NotificationExplanationDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    PermissionGuideDialog(
        title = "Push Notifications",
        category = "Accountability & Security Alerts",
        iconRes = R.drawable.ic_notification,
        description = "Allows PGuard to notify you when an accountability partner joins, changes are requested, time delay unlock timers complete, or security milestones are hit.",
        steps = listOf(
            "Tap \"Open Settings\" below to open PGuard notification settings.",
            "Ensure the toggle for \"Allow notifications\" (or \"All PGuard notifications\") is turned ON."
        ),
        confirmLabel = "Open Settings",
        onConfirm = onConfirm,
        onDismiss = onDismiss
    )
}
