package com.example.pguard.presentation.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import com.example.pguard.R
import com.example.pguard.core.network.NetworkClient
import com.example.pguard.core.network.model.UserDto
import com.example.pguard.data.repository.SettingsRepository
import com.example.pguard.engine.accessibility.BlockingPreferences
import com.example.pguard.presentation.dashboard.BlockingViewModel
import com.example.pguard.presentation.habits.HabitsManager
import com.example.pguard.presentation.theme.PGuardTheme
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    user: UserDto?,
    onLogout: () -> Unit,
    blockingViewModel: BlockingViewModel? = null,
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

    val primary = MaterialTheme.colorScheme.primary

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 96.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            SettingItemRow(
                iconRes = R.drawable.ic_settings_account,
                title = "Account",
                onClick = { showAccountDialog = true }
            )
            SettingDivider()

            SettingItemRow(
                iconRes = R.drawable.ic_settings_sync,
                title = "Update Settings",
                onClick = {
                    coroutineScope.launch {
                        val settingsRepository = SettingsRepository(NetworkClient.getSettingsApiService(context))
                        settingsRepository.fetchSettings().fold(
                            onSuccess = { s ->
                                BlockingPreferences.saveSettings(
                                    context = context,
                                    adult = s.blockAdultContent,
                                    shorts = s.blockShorts,
                                    whatsapp = s.blockWhatsAppChannels,
                                    instagramReels = s.blockInstagramReels,
                                    tiktok = s.blockTikTok,
                                    uninstall = s.uninstallProtection,
                                    unsupportedBrowsers = s.blockUnsupportedBrowsers,
                                    newInstalledApps = s.blockNewlyInstalledApps,
                                    message = s.blockedScreenMessage,
                                    countdown = s.blockedScreenCountdown,
                                    redirect = s.redirectUrl,
                                    markDirty = false
                                )
                                BlockingPreferences.markLocalDirty(context, false)
                                HabitsManager.syncCalendarWithBackend(context, user?.id)
                                blockingViewModel?.refreshFromLocalPreferences(context)
                                Toast.makeText(context, "Settings synced successfully", Toast.LENGTH_SHORT).show()
                            },
                            onFailure = { error ->
                                Toast.makeText(
                                    context,
                                    "Sync failed: ${error.localizedMessage ?: "Unable to reach server"}",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        )
                    }
                }
            )
            SettingDivider()

            SettingItemRow(
                iconRes = R.drawable.ic_settings_uninstall,
                title = "Uninstall App",
                onClick = {
                    if (BlockingPreferences.isUninstallProtectionEnabled) {
                        Toast.makeText(
                            context,
                            "Uninstall Protection is active. Disable it under Blocking using your Accountability Guard first.",
                            Toast.LENGTH_LONG
                        ).show()
                    } else {
                        val intent = Intent(Intent.ACTION_DELETE).apply {
                            data = Uri.parse("package:${context.packageName}")
                        }
                        context.startActivity(intent)
                    }
                }
            )
            SettingDivider()

            SettingItemRow(
                iconRes = R.drawable.ic_settings_logout,
                title = "Log Out",
                onClick = { showLogoutDialog = true }
            )
            SettingDivider()

            SettingItemRow(
                iconRes = R.drawable.ic_settings_privacy,
                title = "Privacy Policy",
                onClick = { showPrivacyPolicyDialog = true }
            )
            SettingDivider()

            SettingItemRow(
                iconRes = R.drawable.ic_guide,
                title = "Terms of Service",
                onClick = { showTermsDialog = true }
            )
            SettingDivider()

            SettingItemRow(
                iconRes = R.drawable.ic_settings_history,
                title = "Check Update",
                subtitle = appVersion,
                onClick = {
                    Toast.makeText(context, "You are using the latest version", Toast.LENGTH_SHORT).show()
                }
            )
            SettingDivider()

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
            shape = MaterialTheme.shapes.medium,
            containerColor = primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
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
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Support",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            }
        }
    }

    if (showPrivacyPolicyDialog) {
        PrivacyPolicyDialog(onDismiss = { showPrivacyPolicyDialog = false })
    }

    if (showTermsDialog) {
        TermsOfServiceDialog(onDismiss = { showTermsDialog = false })
    }

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = {
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
                            painter = painterResource(id = R.drawable.ic_settings_logout),
                            contentDescription = null,
                            tint = primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Log Out",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            },
            text = {
                Text(
                    text = "Are you sure you want to log out of your account?",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showLogoutDialog = false
                        onLogout()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = primary)
                ) {
                    Text(
                        text = "Log Out",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) {
                    Text(
                        text = "Cancel",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            shape = MaterialTheme.shapes.medium
        )
    }

    if (showAccountDialog) {
        AlertDialog(
            onDismissRequest = { showAccountDialog = false },
            title = {
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
                            painter = painterResource(id = R.drawable.ic_settings_account),
                            contentDescription = null,
                            tint = primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Account Details",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Email: ${user?.email ?: "Not signed in"}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (!user?.displayName.isNullOrBlank()) {
                        Text(
                            text = "Name: ${user?.displayName}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = "Status: Free Member",
                        style = MaterialTheme.typography.labelLarge,
                        color = primary
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { showAccountDialog = false },
                    colors = ButtonDefaults.textButtonColors(contentColor = primary)
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
            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(20.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (!subtitle.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun SettingDivider() {
    HorizontalDivider(
        color = MaterialTheme.colorScheme.outlineVariant,
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

@Preview(showBackground = true)
@Composable
private fun SettingsScreenPreview() {
    PGuardTheme {
        SettingsScreen(
            user = null,
            onLogout = {}
        )
    }
}
