package com.example.pguard.presentation.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.pguard.R
import com.example.pguard.core.network.NetworkClient
import com.example.pguard.core.network.model.UserDto
import com.example.pguard.data.repository.SettingsRepository
import com.example.pguard.engine.accessibility.BlockingPreferences
import com.example.pguard.engine.accessibility.PGuardAccessibilityService
import com.example.pguard.engine.admin.DeviceAdminManager
import com.example.pguard.engine.vpn.PGuardVpnService
import com.example.pguard.presentation.accountability.AccountabilityGuardDialogsHost
import com.example.pguard.presentation.accountability.rememberAccountabilityGuard
import com.example.pguard.presentation.blocklist.BlocklistViewModel
import com.example.pguard.presentation.dashboard.AccountabilityMode
import com.example.pguard.presentation.dashboard.BlockingViewModel
import com.example.pguard.presentation.dashboard.ProtectedAction
import com.example.pguard.presentation.dashboard.TimeDelayActiveBanner
import com.example.pguard.presentation.dashboard.openAccessibilitySettings
import com.example.pguard.presentation.dashboard.openAutoStartSettings
import com.example.pguard.presentation.dashboard.openBatteryOptimization
import com.example.pguard.presentation.dashboard.openNotificationSettings
import com.example.pguard.presentation.dashboard.targetDisplayName
import com.example.pguard.presentation.habits.HabitsManager
import com.example.pguard.presentation.theme.PGuardTheme
import com.example.pguard.presentation.theme.pguardColors
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    user: UserDto?,
    onLogout: () -> Unit,
    onSignInClick: () -> Unit = onLogout,
    onOpenBlockingTab: () -> Unit = {},
    onUpgradeClick: () -> Unit = {},
    blockingViewModel: BlockingViewModel? = null,
    blocklistViewModel: BlocklistViewModel? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current
    val accountabilityGuard = rememberAccountabilityGuard(blockingViewModel)

    val blockingUiState = blockingViewModel?.uiState?.collectAsState()?.value
    val isAccessibilityRunning by PGuardAccessibilityService.isRunning.collectAsState()
    val isVpnRunning by PGuardVpnService.isRunning.collectAsState()

    var isDeviceAdminActive by remember { mutableStateOf(DeviceAdminManager.isDeviceAdminActive(context)) }
    var isBatteryUnrestricted by remember { mutableStateOf(isBatteryOptimizationIgnored(context)) }
    var notificationsEnabled by remember { mutableStateOf(areAppNotificationsEnabled(context)) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isDeviceAdminActive = DeviceAdminManager.isDeviceAdminActive(context)
                isBatteryUnrestricted = isBatteryOptimizationIgnored(context)
                notificationsEnabled = areAppNotificationsEnabled(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val isUninstallProtectionOn = blockingUiState?.settings?.uninstallProtection
        ?: BlockingPreferences.isUninstallProtectionEnabled
    val pendingTarget = blockingUiState?.timeDelayTarget ?: BlockingPreferences.timeDelayTarget
    val pendingUnlockAt = blockingUiState?.timeDelayUnlockAt ?: BlockingPreferences.timeDelayUnlockAt
    val serverReady = blockingUiState?.isTimeDelayServerReady ?: false

    val currentAccountabilityMode = blockingUiState?.accountabilityMode
        ?: AccountabilityMode.fromCode(BlockingPreferences.accountabilityMode)

    var isSyncing by remember { mutableStateOf(false) }
    var showLogoutDialog by remember { mutableStateOf(false) }
    var showUninstallDialog by remember { mutableStateOf(false) }
    var showAccountDialog by remember { mutableStateOf(false) }
    var showPrivacyPolicyDialog by remember { mutableStateOf(false) }
    var showTermsDialog by remember { mutableStateOf(false) }

    val appVersion = remember(context) {
        getAppVersionSummary(context)
    }

    val primary = MaterialTheme.colorScheme.primary

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 96.dp)
        ) {
            Spacer(modifier = Modifier.height(12.dp))

            if (
                pendingUnlockAt > 0L &&
                (pendingTarget == ProtectedAction.Logout.identifier ||
                    pendingTarget == ProtectedAction.UninstallApp.identifier)
            ) {
                TimeDelayActiveBanner(
                    targetFeatureTitle = targetDisplayName(pendingTarget),
                    unlockAt = pendingUnlockAt,
                    isServerReady = serverReady,
                    onCancel = {
                        blockingViewModel?.cancelTimeDelay(context) ?: BlockingPreferences.clearTimeDelay(context)
                    },
                    onCompleteUnlock = {
                        if (isUninstallProtectionOn) {
                            Toast.makeText(
                                context,
                                "Disable Uninstall Protection under Blocking first.",
                                Toast.LENGTH_LONG
                            ).show()
                        } else {
                            blockingViewModel?.cancelTimeDelay(context) ?: BlockingPreferences.clearTimeDelay(context)
                            if (pendingTarget == ProtectedAction.UninstallApp.identifier) {
                                if (DeviceAdminManager.isDeviceAdminActive(context)) {
                                    DeviceAdminManager.deactivateDeviceAdmin(context)
                                }
                                if (!launchAppUninstall(context)) {
                                    Toast.makeText(
                                        context,
                                        "Unable to open Android uninstall screen.",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            } else {
                                onLogout()
                            }
                        }
                    },
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                Spacer(modifier = Modifier.height(14.dp))
            }

            ProtectionHealthCard(
                accessibilityRunning = isAccessibilityRunning,
                vpnRunning = isVpnRunning,
                deviceAdminActive = isDeviceAdminActive,
                batteryUnrestricted = isBatteryUnrestricted,
                notificationsEnabled = notificationsEnabled,
                onAccessibilityClick = { openAccessibilitySettings(context) },
                onBlockingSettingsClick = onOpenBlockingTab,
                onBatteryClick = { openBatteryOptimization(context) },
                onNotificationsClick = { openNotificationSettings(context) },
                onAutoStartClick = { openAutoStartSettings(context) },
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            Spacer(modifier = Modifier.height(12.dp))

            SettingItemRow(
                iconRes = R.drawable.ic_settings_account,
                title = "Account",
                subtitle = user?.email ?: "Guest Mode • Tap to view or sign in",
                onClick = { showAccountDialog = true }
            )
            SettingDivider()

            SettingItemRow(
                iconRes = R.drawable.ic_settings_sync,
                title = "Sync Data Now",
                subtitle = if (isSyncing) {
                    "Syncing settings, blocklist & streak..."
                } else {
                    "Sync settings, blocklist & streak"
                },
                trailingContent = if (isSyncing) {
                    {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = primary
                        )
                    }
                } else {
                    null
                },
                onClick = {
                    if (isSyncing) return@SettingItemRow
                    if (user == null) {
                        Toast.makeText(
                            context,
                            "Sign in to sync settings across devices.",
                            Toast.LENGTH_SHORT
                        ).show()
                        return@SettingItemRow
                    }
                    coroutineScope.launch {
                        isSyncing = true
                        try {
                            val settingsResult = if (blockingViewModel != null) {
                                blockingViewModel.syncSettingsNow()
                            } else {
                                val repo = SettingsRepository(NetworkClient.getSettingsApiService(context))
                                repo.fetchSettings().map { s ->
                                    if (!BlockingPreferences.isLocalDirty) {
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
                                        BlockingPreferences.saveAccountabilityMode(context, s.accountabilityMode)
                                    }
                                }
                            }
                            blocklistViewModel?.loadBlocklist()
                            HabitsManager.syncCalendarWithBackend(context, user.id)
                            settingsResult.fold(
                                onSuccess = {
                                    Toast.makeText(context, "Data synced successfully", Toast.LENGTH_SHORT).show()
                                },
                                onFailure = { error ->
                                    Toast.makeText(
                                        context,
                                        "Sync failed: ${error.localizedMessage ?: "Unable to reach server"}",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                            )
                        } finally {
                            isSyncing = false
                        }
                    }
                }
            )
            SettingDivider()

            SettingItemRow(
                iconRes = R.drawable.ic_settings_uninstall,
                title = "Uninstall App",
                subtitle = if (isUninstallProtectionOn) {
                    "Locked while Uninstall Protection is active"
                } else {
                    "Protected by your Accountability Guard"
                },
                onClick = {
                    if (isUninstallProtectionOn) {
                        Toast.makeText(
                            context,
                            "Uninstall Protection is active. Disable it under Blocking using your Accountability Guard first.",
                            Toast.LENGTH_LONG
                        ).show()
                    } else {
                        showUninstallDialog = true
                    }
                }
            )
            SettingDivider()

            if (user == null) {
                SettingItemRow(
                    iconRes = R.drawable.ic_settings_account,
                    title = "Sign In",
                    subtitle = "Back up settings, habits & accountability",
                    onClick = onSignInClick
                )
            } else {
                SettingItemRow(
                    iconRes = R.drawable.ic_settings_logout,
                    title = "Log Out",
                    subtitle = user.email,
                    onClick = {
                        if (isUninstallProtectionOn) {
                            Toast.makeText(
                                context,
                                "Uninstall Protection is active. Disable it under Blocking using your Accountability Guard first.",
                                Toast.LENGTH_LONG
                            ).show()
                        } else {
                            showLogoutDialog = true
                        }
                    }
                )
            }
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
                title = "App Version",
                subtitle = "$appVersion • Check Play Store",
                onClick = { openPlayStoreListing(context) }
            )
            SettingDivider()

            SettingItemRow(
                iconRes = R.drawable.ic_settings_star,
                title = "Rate App",
                onClick = { openPlayStoreListing(context) }
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "PGuard • On-Device Digital Wellness",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
            )
        }

        FloatingActionButton(
            onClick = {
                val body = buildSupportEmailBody(
                    appVersion = appVersion,
                    androidRelease = Build.VERSION.RELEASE ?: "Unknown",
                    sdkInt = Build.VERSION.SDK_INT,
                    isSignedIn = user != null,
                    accessibilityRunning = isAccessibilityRunning,
                    vpnRunning = isVpnRunning,
                    deviceAdminActive = isDeviceAdminActive,
                    batteryUnrestricted = isBatteryUnrestricted,
                    notificationsEnabled = notificationsEnabled,
                    accountabilityModeTitle = currentAccountabilityMode.title
                )
                val emailIntent = Intent(Intent.ACTION_SENDTO).apply {
                    data = Uri.parse("mailto:support@pguard.app")
                    putExtra(Intent.EXTRA_SUBJECT, "PGuard Support Request ($appVersion)")
                    putExtra(Intent.EXTRA_TEXT, body)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
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

    if (showUninstallDialog) {
        AlertDialog(
            onDismissRequest = { showUninstallDialog = false },
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
                            painter = painterResource(id = R.drawable.ic_settings_uninstall),
                            contentDescription = null,
                            tint = primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Uninstall App",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            },
            text = {
                Text(
                    text = "Removing PGuard disables on-device adult and distraction protection. Any active Device Administrator protection will be deactivated before opening Android's uninstall confirmation.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showUninstallDialog = false
                        if (isUninstallProtectionOn) {
                            Toast.makeText(
                                context,
                                "Uninstall Protection is active. Disable it under Blocking using your Accountability Guard first.",
                                Toast.LENGTH_LONG
                            ).show()
                        } else {
                            accountabilityGuard.guard(
                                targetId = ProtectedAction.UninstallApp.identifier,
                                targetTitle = ProtectedAction.UninstallApp.title
                            ) {
                                if (DeviceAdminManager.isDeviceAdminActive(context)) {
                                    DeviceAdminManager.deactivateDeviceAdmin(context)
                                }
                                if (!launchAppUninstall(context)) {
                                    Toast.makeText(
                                        context,
                                        "Unable to open Android uninstall screen.",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                        }
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = primary)
                ) {
                    Text(
                        text = "Continue",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showUninstallDialog = false }) {
                    Text(
                        text = "Stay Protected",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            shape = MaterialTheme.shapes.medium
        )
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
                        accountabilityGuard.guard(
                            targetId = ProtectedAction.Logout.identifier,
                            targetTitle = ProtectedAction.Logout.title
                        ) {
                            onLogout()
                        }
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
        val partnerEmail = blockingUiState?.partner?.partnerEmail?.takeIf { it.isNotBlank() }
            ?: BlockingPreferences.cachedPartnerEmail
        val isPartnerLinked = (blockingUiState?.isPartnerLinked == true) || BlockingPreferences.isPartnerLinkedCached

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
                        text = "Email: ${user?.email ?: "Guest Mode (Offline Only)"}",
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
                        text = "Accountability Mode: ${currentAccountabilityMode.title}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (isPartnerLinked && partnerEmail.isNotBlank()) {
                        Text(
                            text = "Partner: $partnerEmail",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.pguardColors.success
                        )
                    }
                    Text(
                        text = "Plan: Free Plan",
                        style = MaterialTheme.typography.labelLarge,
                        color = primary
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showAccountDialog = false
                        onUpgradeClick()
                    }
                ) {
                    Text(
                        text = "Upgrade",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.pguardColors.gold,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (user == null) {
                        TextButton(
                            onClick = {
                                showAccountDialog = false
                                onSignInClick()
                            },
                            colors = ButtonDefaults.textButtonColors(contentColor = primary)
                        ) {
                            Text(
                                text = "Sign In",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
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
                }
            },
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            shape = MaterialTheme.shapes.medium
        )
    }

    AccountabilityGuardDialogsHost(
        controller = accountabilityGuard,
        blockingViewModel = blockingViewModel
    )
}

@Composable
private fun ProtectionHealthCard(
    accessibilityRunning: Boolean,
    vpnRunning: Boolean,
    deviceAdminActive: Boolean,
    batteryUnrestricted: Boolean,
    notificationsEnabled: Boolean,
    onAccessibilityClick: () -> Unit,
    onBlockingSettingsClick: () -> Unit,
    onBatteryClick: () -> Unit,
    onNotificationsClick: () -> Unit,
    onAutoStartClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val primary = MaterialTheme.colorScheme.primary
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .clip(MaterialTheme.shapes.extraSmall)
                            .background(primary.copy(alpha = 0.14f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_feature_shield),
                            contentDescription = null,
                            tint = primary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Protection Health",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .clickable(onClick = onAutoStartClick)
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "OEM Auto-Start",
                        style = MaterialTheme.typography.labelSmall,
                        color = primary,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            HealthStatusRow(
                iconRes = R.drawable.ic_accessibility,
                label = "Accessibility Service",
                statusLabel = if (accessibilityRunning) "Active" else "Off",
                isHealthy = accessibilityRunning,
                onClick = onAccessibilityClick
            )
            HealthRowDivider()
            HealthStatusRow(
                iconRes = R.drawable.ic_pguard_globe_shield,
                label = "DNS VPN Shield",
                statusLabel = if (vpnRunning) "Active" else "Off",
                isHealthy = vpnRunning,
                onClick = onBlockingSettingsClick
            )
            HealthRowDivider()
            HealthStatusRow(
                iconRes = R.drawable.ic_feature_lock,
                label = "Device Administrator",
                statusLabel = if (deviceAdminActive) "Enabled" else "Off",
                isHealthy = deviceAdminActive,
                onClick = onBlockingSettingsClick
            )
            HealthRowDivider()
            HealthStatusRow(
                iconRes = R.drawable.ic_feature_bolt,
                label = "Battery Unrestricted",
                statusLabel = if (batteryUnrestricted) "Unrestricted" else "Optimized",
                isHealthy = batteryUnrestricted,
                onClick = onBatteryClick
            )
            HealthRowDivider()
            HealthStatusRow(
                iconRes = R.drawable.ic_notification,
                label = "Notifications",
                statusLabel = if (notificationsEnabled) "Enabled" else "Off",
                isHealthy = notificationsEnabled,
                onClick = onNotificationsClick
            )
        }
    }
}

@Composable
private fun HealthStatusRow(
    iconRes: Int,
    label: String,
    statusLabel: String,
    isHealthy: Boolean,
    onClick: () -> Unit
) {
    val accent = if (isHealthy) MaterialTheme.pguardColors.success else MaterialTheme.colorScheme.primary
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 11.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Icon(
                painter = painterResource(id = iconRes),
                contentDescription = label,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(accent.copy(alpha = 0.14f))
                    .padding(horizontal = 9.dp, vertical = 3.dp)
            ) {
                Text(
                    text = statusLabel,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = accent
                )
            }
            Spacer(modifier = Modifier.width(6.dp))
            Icon(
                painter = painterResource(id = R.drawable.ic_chevron_right),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(14.dp)
            )
        }
    }
}

@Composable
private fun HealthRowDivider() {
    HorizontalDivider(
        color = MaterialTheme.colorScheme.outlineVariant,
        thickness = 0.6.dp,
        modifier = Modifier.padding(horizontal = 16.dp)
    )
}

@Composable
private fun SettingItemRow(
    iconRes: Int,
    title: String,
    subtitle: String? = null,
    trailingContent: (@Composable () -> Unit)? = null,
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
        if (trailingContent != null) {
            Spacer(modifier = Modifier.width(12.dp))
            trailingContent()
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

private fun openPlayStoreListing(context: Context) {
    try {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=${context.packageName}")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        )
    } catch (_: Exception) {
        openBrowser(context, "https://play.google.com/store/apps/details?id=${context.packageName}")
    }
}

private fun openBrowser(context: Context, url: String) {
    try {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    } catch (_: Exception) {
        Toast.makeText(context, "Unable to open link", Toast.LENGTH_SHORT).show()
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
