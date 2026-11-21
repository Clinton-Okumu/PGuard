package com.example.pguard.presentation.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.pguard.R

@Composable
fun PrivacyPolicyDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            LegalDialogHeader(
                iconRes = R.drawable.ic_settings_privacy,
                title = "Privacy Policy"
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(380.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = "Last updated: November 2026",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "1. Zero-Log Local Protection\n" +
                        "PGuard operates entirely on-device. Your browsing activities, URLs visited, and habit tracking remain strictly on your personal phone. We do not sell personal data, do not monetize ads, and do not route web traffic through remote proxies.\n\n" +
                        "2. Accessibility Service (BIND_ACCESSIBILITY_SERVICE)\n" +
                        "PGuard uses Android's Accessibility Service exclusively to:\n" +
                        "• Detect adult or blocked website URLs in active browsers to prevent unauthorized access.\n" +
                        "• Enforce Uninstall Protection to prevent tampering during weak moments.\n" +
                        "• Zero Remote Transmission: The service works purely in local memory. PGuard does NOT log keystrokes, does NOT read personal chats, and NEVER sends browsing history to external servers.\n\n" +
                        "3. Account & Cloud Sync\n" +
                        "If you create an account, we securely store your email, hashed session tokens, settings, and recovery streak metrics via encrypted transport solely for cross-device restoration and optional accountability alerts.\n\n" +
                        "4. Data Rights & Contact\n" +
                        "You can request account deletion at any time by contacting privacy@pguard.app.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.88f)
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDismiss,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.primary)
            ) {
                Text(
                    text = "Close",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.medium
    )
}

@Composable
fun TermsOfServiceDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            LegalDialogHeader(
                iconRes = R.drawable.ic_guide,
                title = "Terms of Service"
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(340.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = "1. Purpose\n" +
                        "PGuard is a self-discipline and digital wellness application designed to help users overcome compulsive phone usage and adult content addiction.\n\n" +
                        "2. User Responsibility\n" +
                        "You are responsible for your own device configuration and maintaining the protection settings you choose to enable.\n\n" +
                        "3. Disclaimer\n" +
                        "PGuard is provided 'as is' without warranty. While the filtering engine continually improves, no automated filter is guaranteed to block 100% of newly created web domains.\n\n" +
                        "4. Support\n" +
                        "For assistance, contact support@pguard.app.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.88f)
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDismiss,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.primary)
            ) {
                Text(
                    text = "Close",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.medium
    )
}

@Composable
private fun LegalDialogHeader(
    iconRes: Int,
    title: String
) {
    val primary = MaterialTheme.colorScheme.primary
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(primary.copy(alpha = 0.15f))
                .border(BorderStroke(1.dp, primary.copy(alpha = 0.3f)), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(id = iconRes),
                contentDescription = null,
                tint = primary,
                modifier = Modifier.size(18.dp)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
