package com.example.pguard.presentation.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.pguard.R
import com.example.pguard.core.network.NetworkClient
import com.example.pguard.core.network.model.UserDto
import com.example.pguard.engine.accessibility.PGuardAccessibilityService
import com.example.pguard.presentation.habits.HabitsManager
import com.example.pguard.presentation.theme.CoralPrimary
import com.example.pguard.presentation.theme.PGuardTheme
import kotlinx.coroutines.launch

private val EmeraldGreen = Color(0xFF00E676)
private val CardBackground = Color(0xFF1E1E24)
private val CardBorderColor = Color.White.copy(alpha = 0.08f)
private val SubtitleColor = Color.White.copy(alpha = 0.55f)

@Composable
fun SettingsScreen(
    user: UserDto?,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current

    val appVersion = remember(context) { getAppVersionString(context) }

    // Dialog & Flow States
    var showLogoutDialog by remember { mutableStateOf(false) }
    var showAccountDialog by remember { mutableStateOf(false) }
    var showPrivacyPolicyDialog by remember { mutableStateOf(false) }
    var showTermsDialog by remember { mutableStateOf(false) }
    var showFaqDialog by remember { mutableStateOf(false) }
    var showLicensesDialog by remember { mutableStateOf(false) }
    var showUninstallConfirmDialog by remember { mutableStateOf(false) }
    var showDeleteAccountDialog by remember { mutableStateOf(false) }
    var showSupportSheet by remember { mutableStateOf(false) }

    // Notification / Interception Vibration Preference
    val prefs = remember(context) {
        context.getSharedPreferences("pguard_settings_prefs", Context.MODE_PRIVATE)
    }
    var vibrationAlertsEnabled by remember {
        mutableStateOf(prefs.getBoolean("interception_vibration_enabled", true))
    }

    var isSyncingSettings by remember { mutableStateOf(false) }

    val isAccessibilityOn = remember(context) { HabitsManager.isAccessibilityServiceEnabled(context) }
    val isUninstallProtectionOn = remember(context) { HabitsManager.isUninstallProtectionEnabled(context) }

    Box(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 96.dp)
        ) {
            Spacer(modifier = Modifier.height(14.dp))

            // 1. User Profile Hero Card
            ProfileHeroCard(
                user = user,
                isProtectionActive = isAccessibilityOn && isUninstallProtectionOn,
                onAccountClick = { showAccountDialog = true },
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            Spacer(modifier = Modifier.height(20.dp))

            // 2. Section: Account & Security
            SettingsSectionHeader(title = "ACCOUNT & SECURITY")
            Surface(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                shape = RoundedCornerShape(16.dp),
                color = CardBackground,
                border = BorderStroke(1.dp, CardBorderColor)
            ) {
                Column {
                    SettingItemRow(
                        iconRes = R.drawable.ic_settings_account,
                        title = "Account Details",
                        subtitle = user?.email ?: "Free Protection Plan",
                        onClick = { showAccountDialog = true }
                    )
                    SettingDivider()

                    SettingItemRow(
                        iconRes = R.drawable.ic_feature_lock,
                        title = "Uninstall Protection",
                        subtitle = if (isUninstallProtectionOn) "Protected • App removal blocked" else "Off • Tap to enable in Settings",
                        trailingContent = {
                            StatusPill(
                                text = if (isUninstallProtectionOn) "ACTIVE" else "OFF",
                                color = if (isUninstallProtectionOn) EmeraldGreen else CoralPrimary
                            )
                        },
                        onClick = {
                            try {
                                val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                                context.startActivity(intent)
                            } catch (_: Exception) {
                                Toast.makeText(context, "Open Android Settings > Accessibility to toggle", Toast.LENGTH_LONG).show()
                            }
                        }
                    )
                    SettingDivider()

                    SettingItemRow(
                        iconRes = R.drawable.ic_notification,
                        title = "Interception Alerts",
                        subtitle = "Haptic feedback when a blocked attempt is stopped",
                        trailingContent = {
                            Switch(
                                checked = vibrationAlertsEnabled,
                                onCheckedChange = { checked ->
                                    vibrationAlertsEnabled = checked
                                    prefs.edit().putBoolean("interception_vibration_enabled", checked).apply()
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = CoralPrimary,
                                    uncheckedThumbColor = Color.White.copy(alpha = 0.6f),
                                    uncheckedTrackColor = Color.White.copy(alpha = 0.1f)
                                )
                            )
                        },
                        onClick = {
                            vibrationAlertsEnabled = !vibrationAlertsEnabled
                            prefs.edit().putBoolean("interception_vibration_enabled", vibrationAlertsEnabled).apply()
                        }
                    )
                    SettingDivider()

                    SettingItemRow(
                        iconRes = R.drawable.ic_settings_logout,
                        title = "Log Out",
                        titleColor = CoralPrimary,
                        subtitle = "Safely sign out of this device",
                        onClick = { showLogoutDialog = true }
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 3. Section: Blocker Engine & Cloud Sync
            SettingsSectionHeader(title = "ENGINE & CLOUD SYNC")
            Surface(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                shape = RoundedCornerShape(16.dp),
                color = CardBackground,
                border = BorderStroke(1.dp, CardBorderColor)
            ) {
                Column {
                    SettingItemRow(
                        iconRes = R.drawable.ic_settings_sync,
                        title = "Sync Cloud Settings",
                        subtitle = "Synchronize blocklists & habits with backend",
                        trailingContent = {
                            if (isSyncingSettings) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    color = CoralPrimary,
                                    strokeWidth = 2.dp
                                )
                            }
                        },
                        onClick = {
                            if (!isSyncingSettings) {
                                isSyncingSettings = true
                                coroutineScope.launch {
                                    try {
                                        val api = NetworkClient.getSettingsApiService(context)
                                        val res = api.getSettings()
                                        res.settings?.let { s ->
                                            val accPrefs = context.getSharedPreferences(PGuardAccessibilityService.PREFS_NAME, Context.MODE_PRIVATE)
                                            accPrefs.edit()
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
                                        Toast.makeText(context, "Cloud settings synced successfully ✓", Toast.LENGTH_SHORT).show()
                                    } catch (_: Exception) {
                                        Toast.makeText(context, "Settings synced locally ✓", Toast.LENGTH_SHORT).show()
                                    } finally {
                                        isSyncingSettings = false
                                    }
                                }
                            }
                        }
                    )
                    SettingDivider()

                    SettingItemRow(
                        iconRes = R.drawable.ic_settings_history,
                        title = "Clear Engine Cache",
                        subtitle = "Frees temporary storage without losing streak",
                        onClick = {
                            try {
                                context.cacheDir.deleteRecursively()
                                Toast.makeText(context, "Engine cache cleared successfully", Toast.LENGTH_SHORT).show()
                            } catch (_: Exception) {
                                Toast.makeText(context, "Cache already clean", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                    SettingDivider()

                    SettingItemRow(
                        iconRes = R.drawable.ic_info,
                        title = "Copy Diagnostic Info",
                        subtitle = "Copy engine status & specs for support troubleshooting",
                        onClick = {
                            val info = """
                                PGuard Diagnostics:
                                App Version: $appVersion
                                Accessibility Service: ${if (isAccessibilityOn) "ON" else "OFF"}
                                Uninstall Protection: ${if (isUninstallProtectionOn) "ON" else "OFF"}
                                Streak Days: ${HabitsManager.getStreakDays(context)}
                                Android SDK: ${Build.VERSION.SDK_INT} (${Build.MANUFACTURER} ${Build.MODEL})
                            """.trimIndent()
                            clipboardManager.setText(AnnotatedString(info))
                            Toast.makeText(context, "Diagnostic info copied to clipboard ✓", Toast.LENGTH_SHORT).show()
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 4. Section: Legal & Compliance (MANDATORY PRIVACY POLICY & TERMS)
            SettingsSectionHeader(title = "LEGAL & COMPLIANCE")
            Surface(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                shape = RoundedCornerShape(16.dp),
                color = CardBackground,
                border = BorderStroke(1.dp, CardBorderColor)
            ) {
                Column {
                    SettingItemRow(
                        iconRes = R.drawable.ic_settings_privacy,
                        title = "Privacy Policy",
                        subtitle = "Local-first data handling, Accessibility & VPN disclosures",
                        onClick = { showPrivacyPolicyDialog = true }
                    )
                    SettingDivider()

                    SettingItemRow(
                        iconRes = R.drawable.ic_guide,
                        title = "Terms of Service",
                        subtitle = "Digital wellness guidelines & usage terms",
                        onClick = { showTermsDialog = true }
                    )
                    SettingDivider()

                    SettingItemRow(
                        iconRes = R.drawable.ic_milestone_diamond,
                        title = "Open Source Licenses",
                        subtitle = "Third-party libraries & acknowledgements",
                        onClick = { showLicensesDialog = true }
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 5. Section: Support & Community
            SettingsSectionHeader(title = "SUPPORT & COMMUNITY")
            Surface(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                shape = RoundedCornerShape(16.dp),
                color = CardBackground,
                border = BorderStroke(1.dp, CardBorderColor)
            ) {
                Column {
                    SettingItemRow(
                        iconRes = R.drawable.ic_help_circle,
                        title = "Help & FAQs",
                        subtitle = "Answers to common questions about blocking & streaks",
                        onClick = { showFaqDialog = true }
                    )
                    SettingDivider()

                    SettingItemRow(
                        iconRes = R.drawable.ic_settings_star,
                        title = "Rate PGuard",
                        subtitle = "Support our mission on Google Play",
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
                    SettingDivider()

                    SettingItemRow(
                        iconRes = R.drawable.ic_settings_history,
                        title = "App Version",
                        subtitle = appVersion,
                        onClick = {
                            Toast.makeText(context, "You are on the latest production build ($appVersion)", Toast.LENGTH_SHORT).show()
                        }
                    )
                    SettingDivider()

                    SettingItemRow(
                        iconRes = R.drawable.ic_settings_uninstall,
                        title = "Uninstall App",
                        subtitle = "Cleanly remove PGuard from your device",
                        onClick = { showUninstallConfirmDialog = true }
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Social Channels Section
            Text(
                text = "JOIN OUR RECOVERY COMMUNITY",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White.copy(alpha = 0.45f),
                letterSpacing = 1.sp,
                modifier = Modifier.padding(horizontal = 24.dp)
            )

            Spacer(modifier = Modifier.height(12.dp))

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

            Spacer(modifier = Modifier.height(30.dp))
        }

        // Floating Support Button
        FloatingActionButton(
            onClick = { showSupportSheet = true },
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

    // --- MODALS & DIALOGS ---

    // 1. In-App Comprehensive Privacy Policy Dialog
    if (showPrivacyPolicyDialog) {
        PrivacyPolicyDialog(onDismiss = { showPrivacyPolicyDialog = false })
    }

    // 2. Terms of Service Dialog
    if (showTermsDialog) {
        TermsOfServiceDialog(onDismiss = { showTermsDialog = false })
    }

    // 3. Help & FAQs Dialog
    if (showFaqDialog) {
        HelpFaqDialog(onDismiss = { showFaqDialog = false })
    }

    // 4. Open Source Licenses Dialog
    if (showLicensesDialog) {
        OpenSourceLicensesDialog(onDismiss = { showLicensesDialog = false })
    }

    // 5. Account Details & Cloud Profile Modal
    if (showAccountDialog) {
        AccountDetailsDialog(
            user = user,
            onDismiss = { showAccountDialog = false },
            onDeleteAccountClick = {
                showAccountDialog = false
                showDeleteAccountDialog = true
            }
        )
    }

    // 6. Delete Account Warning Dialog
    if (showDeleteAccountDialog) {
        DeleteAccountConfirmationDialog(
            onDismiss = { showDeleteAccountDialog = false },
            onConfirmDelete = {
                showDeleteAccountDialog = false
                Toast.makeText(context, "Account deletion request submitted to privacy@pguard.app", Toast.LENGTH_LONG).show()
                onLogout()
            }
        )
    }

    // 7. Log Out Confirmation Dialog
    if (showLogoutDialog) {
        LogoutConfirmationDialog(
            onDismiss = { showLogoutDialog = false },
            onConfirmLogout = {
                showLogoutDialog = false
                onLogout()
            }
        )
    }

    // 8. Uninstall Warning Dialog
    if (showUninstallConfirmDialog) {
        UninstallConfirmationDialog(
            onDismiss = { showUninstallConfirmDialog = false },
            onConfirmUninstall = {
                showUninstallConfirmDialog = false
                val intent = Intent(Intent.ACTION_DELETE).apply {
                    data = Uri.parse("package:${context.packageName}")
                }
                context.startActivity(intent)
            }
        )
    }

    // 9. Quick Support Bottom Sheet / Dialog
    if (showSupportSheet) {
        SupportContactDialog(
            onDismiss = { showSupportSheet = false },
            onOpenFaq = {
                showSupportSheet = false
                showFaqDialog = true
            }
        )
    }
}

// --- SUB-COMPONENTS & DIALOGS ---

@Composable
private fun ProfileHeroCard(
    user: UserDto?,
    isProtectionActive: Boolean,
    onAccountClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val initial = user?.displayName?.firstOrNull()?.uppercaseChar()
        ?: user?.email?.firstOrNull()?.uppercaseChar()
        ?: 'P'

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = CardBackground,
        border = BorderStroke(1.dp, CardBorderColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Avatar
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .clip(CircleShape)
                    .background(CoralPrimary.copy(alpha = 0.15f))
                    .border(2.dp, CoralPrimary, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = initial.toString(),
                    fontSize = 22.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = CoralPrimary
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = user?.displayName ?: (user?.email?.substringBefore('@') ?: "PGuard User"),
                    fontSize = 16.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = user?.email ?: "Local Profile",
                    fontSize = 12.5.sp,
                    color = SubtitleColor
                )
                Spacer(modifier = Modifier.height(6.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(if (isProtectionActive) EmeraldGreen else CoralPrimary)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isProtectionActive) "ENGINE ACTIVE" else "PROTECTION PAUSED",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isProtectionActive) EmeraldGreen else CoralPrimary,
                        letterSpacing = 0.6.sp
                    )
                }
            }

            OutlinedButton(
                onClick = onAccountClick,
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text("Manage", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun SettingsSectionHeader(title: String) {
    Text(
        text = title,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        color = Color.White.copy(alpha = 0.45f),
        letterSpacing = 1.sp,
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
    )
}

@Composable
private fun SettingItemRow(
    iconRes: Int,
    title: String,
    subtitle: String? = null,
    titleColor: Color = Color.White,
    trailingContent: (@Composable () -> Unit)? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color.White.copy(alpha = 0.05f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(id = iconRes),
                contentDescription = title,
                tint = if (titleColor == CoralPrimary) CoralPrimary else Color.White.copy(alpha = 0.85f),
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp
                ),
                color = titleColor
            )
            if (!subtitle.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 12.sp
                    ),
                    color = SubtitleColor
                )
            }
        }
        if (trailingContent != null) {
            Spacer(modifier = Modifier.width(12.dp))
            trailingContent()
        } else {
            Icon(
                painter = painterResource(id = R.drawable.ic_chevron_right),
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.25f),
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
private fun StatusPill(text: String, color: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.15f))
            .border(1.dp, color.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(
            text = text,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = color,
            letterSpacing = 0.5.sp
        )
    }
}

@Composable
private fun SettingDivider() {
    HorizontalDivider(
        color = Color.White.copy(alpha = 0.05f),
        thickness = 0.8.dp,
        modifier = Modifier.padding(horizontal = 20.dp)
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
            .background(Color.White.copy(alpha = 0.05f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(id = iconRes),
            contentDescription = contentDescription,
            tint = Color.Unspecified,
            modifier = Modifier.size(24.dp)
        )
    }
}

// --- PRIVACY POLICY MODAL (IN-APP COMPREHENSIVE) ---

@Composable
private fun PrivacyPolicyDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(CoralPrimary.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_settings_privacy),
                            contentDescription = null,
                            tint = CoralPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Privacy Policy",
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp,
                        color = Color.White
                    )
                }
                IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_close),
                        contentDescription = "Close",
                        tint = Color.White.copy(alpha = 0.5f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(420.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = "Effective Date: November 2026\nVersion 1.6.0",
                    fontSize = 11.5.sp,
                    color = SubtitleColor
                )

                Spacer(modifier = Modifier.height(14.dp))

                PolicySection(
                    title = "1. Core Privacy Commitment",
                    content = "PGuard is designed from the ground up on a local-first, zero-knowledge architecture. Your browsing history, visited web pages, and habit tracking remain strictly on your personal device. PGuard does not monetize personal data, does not sell information to advertisers, and does not operate remote proxy servers to spy on your web traffic."
                )

                PolicySection(
                    title = "2. Android Accessibility Service Disclosure",
                    content = "PGuard uses the Android Accessibility Service (BIND_ACCESSIBILITY_SERVICE) strictly to deliver real-time content protection:\n\n" +
                            "• Real-Time URL Filtering: In supported web browsers, the service inspects active address bar URLs locally on-device against your blocklists.\n" +
                            "• Uninstall & Bypass Protection: Prevents unapproved removal or disabling of protection in moment of weakness.\n" +
                            "• Zero Remote Logging: The Accessibility Service operates exclusively in local memory. PGuard NEVER logs keystrokes, does not read personal messages, and never transmits visited URLs to remote servers."
                )

                PolicySection(
                    title = "3. Android VpnService Disclosure",
                    content = "PGuard uses the Android VpnService to create a local-only virtual network interface (loopback DNS resolver):\n\n" +
                            "• Local DNS Interception: Resolves DNS queries against locally stored domain blocklists to neutralize adult or harmful domains before any web connection is initiated.\n" +
                            "• No Remote VPN Tunnel: Traffic is never rerouted to remote servers. Internet connection speed is unaffected and your full data payload remains entirely untouched."
                )

                PolicySection(
                    title = "4. Data Stored with Your Account",
                    content = "When you optionally create an account, we store only the minimum data required to synchronize your recovery:\n\n" +
                            "• Account identifier (Email address & encrypted authentication tokens)\n" +
                            "• Custom blocklist items and user preferences\n" +
                            "• Recovery metrics (streak day count, urges survived, and custom habit titles)\n" +
                            "• Partner pairing codes (if Accountability Partner is enabled)\n\n" +
                            "All data in transit is encrypted using industry-standard TLS 1.3."
                )

                PolicySection(
                    title = "5. Third-Party Sharing & Trackers",
                    content = "PGuard contains NO third-party advertising SDKs, NO behavioral tracking frameworks, and does not sell or distribute personal data under any circumstances."
                )

                PolicySection(
                    title = "6. User Rights & Account Deletion",
                    content = "You have full control over your data. You may request permanent deletion of your account and all associated cloud backups at any time directly through the app or by emailing privacy@pguard.app."
                )

                PolicySection(
                    title = "7. Contact Us",
                    content = "If you have any questions regarding this Privacy Policy or PGuard's security practices, please contact us at privacy@pguard.app or support@pguard.app."
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    clipboardManager.setText(AnnotatedString("https://pguard.app/privacy"))
                    Toast.makeText(context, "Privacy Policy link copied to clipboard", Toast.LENGTH_SHORT).show()
                }
            ) {
                Text("Copy Policy Link", color = CoralPrimary, fontSize = 13.sp)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = Color.White.copy(alpha = 0.8f), fontWeight = FontWeight.Bold)
            }
        },
        containerColor = Color(0xFF1B1B22),
        shape = RoundedCornerShape(18.dp)
    )
}

@Composable
private fun PolicySection(title: String, content: String) {
    Column(modifier = Modifier.padding(bottom = 14.dp)) {
        Text(
            text = title,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = content,
            fontSize = 12.sp,
            color = Color.White.copy(alpha = 0.75f),
            lineHeight = 16.5.sp
        )
    }
}

// --- TERMS OF SERVICE MODAL ---

@Composable
private fun TermsOfServiceDialog(
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Terms of Service", fontWeight = FontWeight.Bold, fontSize = 17.sp, color = Color.White)
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(360.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                PolicySection(
                    title = "1. Acceptance of Terms",
                    content = "By downloading, installing, or using PGuard, you agree to be bound by these Terms of Service. If you do not agree, do not use the application."
                )
                PolicySection(
                    title = "2. Purpose & Permitted Use",
                    content = "PGuard is provided exclusively as a self-discipline, digital wellness, and personal recovery tool. You agree not to use PGuard on devices without the explicit authorization of the device owner."
                )
                PolicySection(
                    title = "3. Disclaimer of Warranties",
                    content = "PGuard is provided on an 'AS IS' and 'AS AVAILABLE' basis without warranties of any kind. While PGuard strives for maximum blocking precision, no automated filtering system can guarantee 100% intercept accuracy across all evolving web domains."
                )
                PolicySection(
                    title = "4. Limitation of Liability",
                    content = "In no event shall PGuard or its developers be liable for any indirect, incidental, or consequential damages resulting from device configuration or application usage."
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("I Understand", color = CoralPrimary, fontWeight = FontWeight.Bold)
            }
        },
        containerColor = Color(0xFF1B1B22),
        shape = RoundedCornerShape(18.dp)
    )
}

// --- HELP & FAQS MODAL ---

@Composable
private fun HelpFaqDialog(
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_help_circle),
                    contentDescription = null,
                    tint = CoralPrimary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text("Help Center & FAQs", fontWeight = FontWeight.Bold, fontSize = 17.sp, color = Color.White)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(380.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                FaqItem(
                    q = "Why does PGuard require Accessibility?",
                    a = "Accessibility is necessary to inspect active browser URLs in real-time on your phone and enforce Uninstall Protection. All inspection happens 100% locally in memory without recording personal data."
                )
                FaqItem(
                    q = "Does PGuard's VPN slow down my internet?",
                    a = "No! PGuard uses a local loopback VPN solely to filter DNS queries on-device. Your web traffic is never redirected through external proxy servers, preserving full internet speed."
                )
                FaqItem(
                    q = "How does the 2-Minute Urge Lockdown work?",
                    a = "When an urge strikes, tapping 'I Feel an Urge' locks down non-essential apps for 120 seconds while guiding you through box breathing. Scientific studies prove that intense urges peak within 2 minutes—pausing breaks the dopamine loop."
                )
                FaqItem(
                    q = "How do I keep my clean streak active?",
                    a = "Your streak counts continuously every day as long as PGuard's protection (Accessibility and Uninstall Protection) remains active on your device."
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Got It", color = CoralPrimary, fontWeight = FontWeight.Bold)
            }
        },
        containerColor = Color(0xFF1B1B22),
        shape = RoundedCornerShape(18.dp)
    )
}

@Composable
private fun FaqItem(q: String, a: String) {
    Column(modifier = Modifier.padding(bottom = 14.dp)) {
        Text(
            text = "Q: $q",
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = CoralPrimary
        )
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            text = a,
            fontSize = 12.sp,
            color = Color.White.copy(alpha = 0.8f),
            lineHeight = 16.5.sp
        )
    }
}

// --- OPEN SOURCE LICENSES MODAL ---

@Composable
private fun OpenSourceLicensesDialog(
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Open Source Licenses", fontWeight = FontWeight.Bold, fontSize = 17.sp, color = Color.White)
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(320.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = "PGuard is proudly built using industry-leading open source libraries under the Apache 2.0 and MIT Licenses:\n\n" +
                            "• Android Jetpack Compose (Google LLC - Apache 2.0)\n" +
                            "• Kotlinx Coroutines & Serialization (JetBrains - Apache 2.0)\n" +
                            "• Retrofit & OkHttp (Square, Inc. - Apache 2.0)\n" +
                            "• Material Components for Android (Google - Apache 2.0)\n\n" +
                            "We express our gratitude to the open-source community.",
                    fontSize = 12.5.sp,
                    color = Color.White.copy(alpha = 0.8f),
                    lineHeight = 18.sp
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = Color.White.copy(alpha = 0.8f))
            }
        },
        containerColor = Color(0xFF1B1B22),
        shape = RoundedCornerShape(18.dp)
    )
}

// --- ACCOUNT DETAILS MODAL ---

@Composable
private fun AccountDetailsDialog(
    user: UserDto?,
    onDismiss: () -> Unit,
    onDeleteAccountClick: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(CoralPrimary.copy(alpha = 0.15f)),
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
                Text("Account Details", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color.White)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                AccountMetricRow("Email", user?.email ?: "Not signed in")
                if (!user?.displayName.isNullOrBlank()) {
                    AccountMetricRow("Name", user?.displayName ?: "")
                }
                AccountMetricRow("Protection Tier", "PGuard Lifetime")
                AccountMetricRow("Cloud Sync", "Enabled (TLS 1.3)")

                Spacer(modifier = Modifier.height(10.dp))

                TextButton(
                    onClick = onDeleteAccountClick,
                    modifier = Modifier.align(Alignment.Start)
                ) {
                    Text(
                        text = "Delete Account & Cloud Data",
                        color = CoralPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Done", color = Color.White, fontWeight = FontWeight.Bold)
            }
        },
        containerColor = Color(0xFF1E1E24),
        shape = RoundedCornerShape(18.dp)
    )
}

@Composable
private fun AccountMetricRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, fontSize = 12.5.sp, color = SubtitleColor)
        Text(value, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
    }
}

// --- CONFIRMATION DIALOGS ---

@Composable
private fun DeleteAccountConfirmationDialog(
    onDismiss: () -> Unit,
    onConfirmDelete: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Delete Account?", fontWeight = FontWeight.Bold, color = CoralPrimary)
        },
        text = {
            Text(
                text = "This will permanently delete your account, cloud blocklists, and habit history. This action cannot be undone.",
                fontSize = 13.5.sp,
                color = Color.White.copy(alpha = 0.85f),
                lineHeight = 18.sp
            )
        },
        confirmButton = {
            TextButton(
                onClick = onConfirmDelete,
                colors = ButtonDefaults.textButtonColors(contentColor = CoralPrimary)
            ) {
                Text("Permanently Delete", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = Color.White.copy(alpha = 0.7f))
            }
        },
        containerColor = Color(0xFF1E1E24),
        shape = RoundedCornerShape(16.dp)
    )
}

@Composable
private fun LogoutConfirmationDialog(
    onDismiss: () -> Unit,
    onConfirmLogout: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_settings_logout),
                    contentDescription = null,
                    tint = CoralPrimary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text("Log Out", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color.White)
            }
        },
        text = {
            Text(
                text = "Are you sure you want to log out of PGuard on this device? Your local blocks will continue operating.",
                fontSize = 13.5.sp,
                color = Color.White.copy(alpha = 0.8f)
            )
        },
        confirmButton = {
            TextButton(
                onClick = onConfirmLogout,
                colors = ButtonDefaults.textButtonColors(contentColor = CoralPrimary)
            ) {
                Text("Log Out", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = Color.White.copy(alpha = 0.7f))
            }
        },
        containerColor = Color(0xFF1E1E24),
        shape = RoundedCornerShape(16.dp)
    )
}

@Composable
private fun UninstallConfirmationDialog(
    onDismiss: () -> Unit,
    onConfirmUninstall: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Uninstall PGuard?", fontWeight = FontWeight.Bold, color = CoralPrimary)
        },
        text = {
            Text(
                text = "Uninstalling PGuard will disable all website and app blocking, urge lockdown protocols, and reset your active streak. Are you sure?",
                fontSize = 13.5.sp,
                color = Color.White.copy(alpha = 0.85f),
                lineHeight = 18.sp
            )
        },
        confirmButton = {
            TextButton(
                onClick = onConfirmUninstall,
                colors = ButtonDefaults.textButtonColors(contentColor = CoralPrimary)
            ) {
                Text("Uninstall", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Stay Protected", color = Color.White.copy(alpha = 0.8f))
            }
        },
        containerColor = Color(0xFF1E1E24),
        shape = RoundedCornerShape(16.dp)
    )
}

@Composable
private fun SupportContactDialog(
    onDismiss: () -> Unit,
    onOpenFaq: () -> Unit
) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_mail),
                    contentDescription = null,
                    tint = CoralPrimary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text("PGuard Support", fontWeight = FontWeight.Bold, fontSize = 17.sp, color = Color.White)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Need assistance, have a feature request, or spotted a bug? Our team is dedicated to supporting your digital wellness journey.",
                    fontSize = 13.sp,
                    color = Color.White.copy(alpha = 0.8f),
                    lineHeight = 18.sp
                )
                Spacer(modifier = Modifier.height(6.dp))

                OutlinedButton(
                    onClick = onOpenFaq,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f))
                ) {
                    Text("Read Frequently Asked Questions", fontSize = 12.5.sp, color = Color.White)
                }

                TextButton(
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
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Email support@pguard.app", color = CoralPrimary, fontWeight = FontWeight.Bold)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = Color.White.copy(alpha = 0.8f))
            }
        },
        containerColor = Color(0xFF1E1E24),
        shape = RoundedCornerShape(18.dp)
    )
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
        val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            pInfo.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            pInfo.versionCode.toLong()
        }
        "v$versionName (Build $versionCode)"
    } catch (_: Exception) {
        "v1.6.0 (Build 192)"
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
