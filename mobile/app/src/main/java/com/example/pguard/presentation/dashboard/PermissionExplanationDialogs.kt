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
    PermissionGuideDialog(
        title = "Enable Accessibility",
        category = "Core Content Filtering",
        iconRes = R.drawable.ic_accessibility,
        description = "PGuard requires Accessibility service to inspect browser URLs, social feeds (Shorts/Reels/TikTok), and newly launched apps in real time to keep you protected.",
        steps = listOf(
            "Tap \"Open Settings\" below to go to Android Accessibility settings.",
            "Tap \"Installed apps\" (or \"Downloaded services\").",
            "Find and tap \"PGuard\".",
            "Turn the toggle ON and tap \"Allow\" on the confirmation prompt."
        ),
        confirmLabel = "Open Settings",
        onConfirm = onConfirm,
        onDismiss = onDismiss
    )
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
