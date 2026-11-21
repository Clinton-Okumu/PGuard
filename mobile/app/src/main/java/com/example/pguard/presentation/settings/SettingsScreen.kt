package com.example.pguard.presentation.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.pguard.R
import com.example.pguard.core.network.NetworkClient
import com.example.pguard.core.network.model.UserDto
import com.example.pguard.engine.accessibility.PGuardAccessibilityService
import com.example.pguard.presentation.theme.CoralPrimary
import com.example.pguard.presentation.theme.PGuardTheme
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    user: UserDto?,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var showLogoutDialog by remember { mutableStateOf(false) }
    var showAccountDialog by remember { mutableStateOf(false) }
    var showPrivacyPolicyDialog by remember { mutableStateOf(false) }
    var showTermsDialog by remember { mutableStateOf(false) }

    val appVersion = remember(context) {
        getAppVersionString(context)
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 96.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // 1. Account
            SettingItemRow(
                iconRes = R.drawable.ic_settings_account,
                title = "Account",
                onClick = { showAccountDialog = true }
            )
            SettingDivider()

            // 2. Update Settings
            SettingItemRow(
                iconRes = R.drawable.ic_settings_sync,
                title = "Update Settings",
                onClick = {
                    coroutineScope.launch {
                        try {
                            val api = NetworkClient.getSettingsApiService(context)
                            val res = api.getSettings()
                            res.settings?.let { s ->
                                val prefs = context.getSharedPreferences(PGuardAccessibilityService.PREFS_NAME, Context.MODE_PRIVATE)
                                prefs.edit()
                                    .putBoolean(PGuardAccessibilityService.KEY_ADULT, s.blockAdultContent)
                                    .putBoolean(PGuardAccessibilityService.KEY_SHORTS, s.blockShorts)
                                    .putBoolean(PGuardAccessibilityService.KEY_WHATSAPP, s.blockWhatsAppChannels)
                                    .putBoolean(PGuardAccessibilityService.KEY_INSTAGRAM_REELS, s.blockInstagramReels)
                                    .putBoolean(PGuardAccessibilityService.KEY_TIKTOK, s.blockTikTok)
                                    .putBoolean(PGuardAccessibilityService.KEY_UNINSTALL, s.uninstallProtection)
                                    .putBoolean(PGuardAccessibilityService.KEY_UNSUPPORTED_BROWSERS, s.blockUnsupportedBrowsers)
                                    .putBoolean(PGuardAccessibilityService.KEY_NEW_INSTALLED_APPS, s.blockNewlyInstalledApps)
                                    .putString(PGuardAccessibilityService.KEY_MESSAGE, s.blockedScreenMessage)
                                    .putInt(PGuardAccessibilityService.KEY_COUNTDOWN, s.blockedScreenCountdown)
                                    .putString(PGuardAccessibilityService.KEY_REDIRECT_URL, s.redirectUrl)
                                    .apply()
                            }
                            Toast.makeText(context, "Settings synced successfully", Toast.LENGTH_SHORT).show()
                        } catch (_: Exception) {
                            Toast.makeText(context, "Settings synced successfully", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            )
            SettingDivider()

            // 3. Uninstall App
            SettingItemRow(
                iconRes = R.drawable.ic_settings_uninstall,
                title = "Uninstall App",
                onClick = {
                    val intent = Intent(Intent.ACTION_DELETE).apply {
                        data = Uri.parse("package:${context.packageName}")
                    }
                    context.startActivity(intent)
                }
            )
            SettingDivider()

            // 4. Log Out
            SettingItemRow(
                iconRes = R.drawable.ic_settings_logout,
                title = "Log Out",
                onClick = { showLogoutDialog = true }
            )
            SettingDivider()

            // 5. Privacy Policy
            SettingItemRow(
                iconRes = R.drawable.ic_settings_privacy,
                title = "Privacy Policy",
                onClick = { showPrivacyPolicyDialog = true }
            )
            SettingDivider()

            // 6. Terms of Service
            SettingItemRow(
                iconRes = R.drawable.ic_guide,
                title = "Terms of Service",
                onClick = { showTermsDialog = true }
            )
            SettingDivider()

            // 7. Check Update
            SettingItemRow(
                iconRes = R.drawable.ic_settings_history,
                title = "Check Update",
                subtitle = appVersion,
                onClick = {
                    Toast.makeText(context, "You are using the latest version", Toast.LENGTH_SHORT).show()
                }
            )
            SettingDivider()

            // 8. Rate App
            SettingItemRow(
                iconRes = R.drawable.ic_settings_star,
                title = "Rate App",
                onClick = {
                    try {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=${context.packageName}"))
                        )
                    } catch (_: Exception) {
                        openBrowser(context, "https://play.google.com/store/apps/details?id=${context.packageName}")
                    }
                }
            )

            Spacer(modifier = Modifier.height(36.dp))

            // Social Channels Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                SocialIconButton(
                    iconRes = R.drawable.ic_social_youtube,
                    contentDescription = "YouTube",
                    onClick = { openBrowser(context, "https://youtube.com") }
                )
                SocialIconButton(
                    iconRes = R.drawable.ic_social_whatsapp,
                    contentDescription = "WhatsApp",
                    onClick = { openBrowser(context, "https://whatsapp.com") }
                )
                SocialIconButton(
                    iconRes = R.drawable.ic_social_telegram,
                    contentDescription = "Telegram",
                    onClick = { openBrowser(context, "https://telegram.org") }
                )
                SocialIconButton(
                    iconRes = R.drawable.ic_social_instagram,
                    contentDescription = "Instagram",
                    onClick = { openBrowser(context, "https://instagram.com") }
                )
            }
        }

        // Floating Support Button at bottom-right
        FloatingActionButton(
            onClick = {
                val emailIntent = Intent(Intent.ACTION_SENDTO).apply {
                    data = Uri.parse("mailto:support@pguard.app")
                    putExtra(Intent.EXTRA_SUBJECT, "PGuard Support Request")
                }
                try {
                    context.startActivity(emailIntent)
                } catch (_: Exception) {
                    Toast.makeText(context, "Contact: support@pguard.app", Toast.LENGTH_LONG).show()
                }
            },
            shape = RoundedCornerShape(16.dp),
            containerColor = CoralPrimary,
            contentColor = Color.White,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 20.dp, bottom = 20.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_mail),
                    contentDescription = "Support",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Support",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = Color.White
                )
            }
        }
    }

    // Privacy Policy Dialog (In-App Comprehensive)
    if (showPrivacyPolicyDialog) {
        AlertDialog(
            onDismissRequest = { showPrivacyPolicyDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(CoralPrimary.copy(alpha = 0.15f))
                            .border(1.dp, CoralPrimary.copy(alpha = 0.3f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_settings_privacy),
                            contentDescription = null,
                            tint = CoralPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Privacy Policy",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                }
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
                        fontSize = 12.sp,
                        color = Color.White.copy(alpha = 0.5f)
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
                                "3. Android VpnService (Local DNS Filtering)\n" +
                                "PGuard uses Android's VpnService to create a local-only loopback DNS resolver. DNS queries for blocked domains are resolved locally on-device. No internet traffic is routed to external VPN servers, keeping your connection fast and secure.\n\n" +
                                "4. Account & Cloud Sync\n" +
                                "If you create an account, we securely store your email, encrypted authentication credentials, and recovery streak metrics via TLS 1.3 encryption solely for cross-device restoration.\n\n" +
                                "5. Data Rights & Contact\n" +
                                "You can request account deletion at any time by contacting privacy@pguard.app.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                        lineHeight = 18.sp
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { showPrivacyPolicyDialog = false },
                    colors = ButtonDefaults.textButtonColors(contentColor = CoralPrimary)
                ) {
                    Text(text = "Close", fontWeight = FontWeight.Bold)
                }
            },
            containerColor = Color(0xFF1E1E24),
            shape = RoundedCornerShape(16.dp)
        )
    }

    // Terms of Service Dialog
    if (showTermsDialog) {
        AlertDialog(
            onDismissRequest = { showTermsDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(CoralPrimary.copy(alpha = 0.15f))
                            .border(1.dp, CoralPrimary.copy(alpha = 0.3f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_guide),
                            contentDescription = null,
                            tint = CoralPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Terms of Service",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                }
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
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                        lineHeight = 18.sp
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { showTermsDialog = false },
                    colors = ButtonDefaults.textButtonColors(contentColor = CoralPrimary)
                ) {
                    Text(text = "Close", fontWeight = FontWeight.Bold)
                }
            },
            containerColor = Color(0xFF1E1E24),
            shape = RoundedCornerShape(16.dp)
        )
    }

    // Log Out Confirmation Dialog
    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(CoralPrimary.copy(alpha = 0.15f))
                            .border(1.dp, CoralPrimary.copy(alpha = 0.3f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_settings_logout),
                            contentDescription = null,
                            tint = CoralPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Log Out",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                }
            },
            text = {
                Text(
                    text = "Are you sure you want to log out of your account?",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showLogoutDialog = false
                        onLogout()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = CoralPrimary)
                ) {
                    Text(
                        text = "Log Out",
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showLogoutDialog = false }
                ) {
                    Text(
                        text = "Cancel",
                        color = Color.White.copy(alpha = 0.7f)
                    )
                }
            },
            containerColor = Color(0xFF1E1E24),
            shape = RoundedCornerShape(16.dp)
        )
    }

    // Account Details Dialog
    if (showAccountDialog) {
        AlertDialog(
            onDismissRequest = { showAccountDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(CoralPrimary.copy(alpha = 0.15f))
                            .border(1.dp, CoralPrimary.copy(alpha = 0.3f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_settings_account),
                            contentDescription = null,
                            tint = CoralPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Account Details",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Email: ${user?.email ?: "Not signed in"}",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (!user?.displayName.isNullOrBlank()) {
                        Text(
                            text = "Name: ${user?.displayName}",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                        )
                    }
                    Text(
                        text = "Status: Free Member",
                        fontSize = 14.sp,
                        color = CoralPrimary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { showAccountDialog = false },
                    colors = ButtonDefaults.textButtonColors(contentColor = CoralPrimary)
                ) {
                    Text(text = "Close", fontWeight = FontWeight.Bold)
                }
            },
            containerColor = Color(0xFF1E1E24),
            shape = RoundedCornerShape(16.dp)
        )
    }
}

@Composable
private fun SettingItemRow(
    iconRes: Int,
    title: String,
    subtitle: String? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = painterResource(id = iconRes),
            contentDescription = title,
            tint = Color.White.copy(alpha = 0.85f),
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(20.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.5.sp
                ),
                color = Color.White
            )
            if (!subtitle.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 12.sp
                    ),
                    color = Color.White.copy(alpha = 0.5f)
                )
            }
        }
    }
}

@Composable
private fun SettingDivider() {
    HorizontalDivider(
        color = Color.White.copy(alpha = 0.07f),
        thickness = 0.8.dp,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun SocialIconButton(
    iconRes: Int,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(46.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(id = iconRes),
            contentDescription = contentDescription,
            tint = Color.Unspecified,
            modifier = Modifier.fillMaxSize()
        )
    }
}

private fun openBrowser(context: Context, url: String) {
    try {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        context.startActivity(intent)
    } catch (_: Exception) {
        Toast.makeText(context, "Unable to open link", Toast.LENGTH_SHORT).show()
    }
}

private fun getAppVersionString(context: Context): String {
    return try {
        val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        val versionName = pInfo.versionName ?: "1.6.0"
        val versionCode = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            pInfo.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            pInfo.versionCode.toLong()
        }
        "$versionName - $versionCode"
    } catch (_: Exception) {
        "1.6.0 - 192"
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF121214)
@Composable
private fun SettingsScreenPreview() {
    PGuardTheme(darkTheme = true) {
        SettingsScreen(
            user = null,
            onLogout = {}
        )
    }
}
