package com.example.pguard.presentation.dashboard

import android.app.Activity
import android.content.Context
import android.net.VpnService
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.pguard.R
import com.example.pguard.engine.accessibility.PGuardAccessibilityService
import com.example.pguard.engine.admin.DeviceAdminManager
import com.example.pguard.engine.vpn.PGuardVpnService
import com.example.pguard.presentation.theme.PGuardTheme

internal data class PermissionAlerts(
    val pushNotificationsMissing: Boolean,
    val accessibilityMissing: Boolean,
    val dnsVpnInactive: Boolean = false,
    val deviceAdminMissing: Boolean = false
)

internal data class BlockingScreenCallbacks(
    val onPremiumClick: () -> Unit,
    val onGuideClick: () -> Unit,
    val onFocusModeClick: () -> Unit,
    val onToggle: (BlockingToggle, Boolean) -> Unit,
    val onEditSetting: (SettingsEditor) -> Unit,
    val onInfo: (String, String) -> Unit,
    val onPartnerAction: () -> Unit,
    val onResendInvite: () -> Unit,
    val onConfirmCancelInvite: () -> Unit,
    val onDismissCancelInvite: () -> Unit,
    val onOpenSecurityAlerts: () -> Unit,
    val onRefreshSecurityAlerts: () -> Unit,
    val onDismissSecurityAlerts: () -> Unit,
    val onFixPushNotifications: () -> Unit,
    val onFixAccessibility: () -> Unit,
    val onFixDnsVpn: () -> Unit,
    val onEditorTextChange: (String) -> Unit,
    val onEditorSave: () -> Unit,
    val onEditorDismiss: () -> Unit,
    val onCountdownSelected: (Int) -> Unit,
    val onInfoDismiss: () -> Unit,
    val onGuideDismiss: () -> Unit,
    val onInviteEmailChange: (String) -> Unit,
    val onInviteNameChange: (String) -> Unit,
    val onInviteSubmit: () -> Unit,
    val onInviteDismiss: () -> Unit,
    val onManageAccountabilityMode: () -> Unit,
    val onSelectAccountabilityMode: (AccountabilityMode) -> Unit,
    val onDismissModeSelection: () -> Unit,
    val onTypingChallengeSuccess: () -> Unit,
    val onDismissTypingChallenge: () -> Unit,
    val onConfirmTimeDelay: () -> Unit,
    val onCancelTimeDelay: () -> Unit,
    val onCompleteTimeDelay: () -> Unit,
    val onDismissTimeDelayConfirm: () -> Unit,
    val onVerifyFriendApprovalCode: (String) -> Unit,
    val onDismissFriendApproval: () -> Unit,
    val onRequestPartnerApproval: (String) -> Unit,
    val onConfirmDeviceAdmin: () -> Unit,
    val onDismissDeviceAdmin: () -> Unit,
    val onFixDeviceAdmin: () -> Unit,
    val onResetMessageDefault: () -> Unit,
    val onClearRedirectUrl: () -> Unit,
    val onPreviewBlockedScreen: () -> Unit,
    val onDismissBlockedScreenPreview: () -> Unit
)

@Composable
fun BlockingScreen(
    viewModel: BlockingViewModel,
    onPremiumClick: () -> Unit = {},
    onGuideClick: () -> Unit = {},
    onFocusModeClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsState()
    val isVpnRunning by PGuardVpnService.isRunning.collectAsState()
    val lifecycleOwner = LocalLifecycleOwner.current

    var showVpnDialog by remember { mutableStateOf(false) }

    var alerts by remember {
        mutableStateOf(
            readPermissionAlerts(
                context = context,
                dnsVpnEnabled = state.isDnsVpnEnabled,
                isVpnRunning = isVpnRunning,
                uninstallProtectionEnabled = state.settings.uninstallProtection
            )
        )
    }

    val vpnPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            viewModel.onDnsVpnPermissionGranted(context)
        }
    }

    val launchVpnConsent = {
        val prepareIntent = VpnService.prepare(context)
        if (prepareIntent == null) {
            viewModel.onDnsVpnPermissionGranted(context)
        } else {
            vpnPermissionLauncher.launch(prepareIntent)
        }
    }

    val deviceAdminLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { _ ->
        viewModel.onDeviceAdminActivityResult(context)
        val latest = viewModel.uiState.value
        alerts = readPermissionAlerts(
            context = context,
            dnsVpnEnabled = latest.isDnsVpnEnabled,
            isVpnRunning = PGuardVpnService.isRunning.value,
            uninstallProtectionEnabled = latest.settings.uninstallProtection
        )
    }

    val launchDeviceAdminConsent = {
        viewModel.markDeviceAdminRequestLaunched()
        try {
            deviceAdminLauncher.launch(DeviceAdminManager.buildEnableDeviceAdminIntent(context))
        } catch (_: Exception) {
            DeviceAdminManager.requestEnableDeviceAdmin(context)
        }
    }

    LaunchedEffect(Unit) {
        PGuardAccessibilityService.loadSettings(context)
        viewModel.refreshFromLocalPreferences(context)
        if (PGuardAccessibilityService.isDnsVpnEnabled &&
            !PGuardVpnService.isRunning.value &&
            VpnService.prepare(context) == null
        ) {
            viewModel.onDnsVpnPermissionGranted(context)
        }
    }

    LaunchedEffect(state.isDnsVpnEnabled, isVpnRunning, state.settings.uninstallProtection) {
        alerts = readPermissionAlerts(
            context = context,
            dnsVpnEnabled = state.isDnsVpnEnabled,
            isVpnRunning = isVpnRunning,
            uninstallProtectionEnabled = state.settings.uninstallProtection
        )
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    viewModel.checkDeviceAdminStatus(context)
                    val refreshedState = viewModel.uiState.value
                    val vpnActive = PGuardVpnService.isRunning.value
                    if (refreshedState.isDnsVpnEnabled && !vpnActive && VpnService.prepare(context) == null) {
                        viewModel.onDnsVpnPermissionGranted(context)
                    }
                    alerts = readPermissionAlerts(
                        context = context,
                        dnsVpnEnabled = refreshedState.isDnsVpnEnabled,
                        isVpnRunning = PGuardVpnService.isRunning.value,
                        uninstallProtectionEnabled = refreshedState.settings.uninstallProtection
                    )
                    viewModel.refreshPartnerStatus()
                }
                Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP -> {
                    viewModel.stopPartnerPolling()
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.stopPartnerPolling()
        }
    }

    val callbacks = BlockingScreenCallbacks(
        onPremiumClick = onPremiumClick,
        onGuideClick = {
            viewModel.showGuide()
            onGuideClick()
        },
        onFocusModeClick = onFocusModeClick,
        onToggle = { toggle, checked ->
            if (toggle == BlockingToggle.DNS_VPN_SHIELD && checked) {
                showVpnDialog = true
            } else {
                viewModel.handleToggleRequested(toggle, checked, context)
            }
        },
        onEditSetting = viewModel::openEditor,
        onInfo = viewModel::showInfo,
        onPartnerAction = {
            if (state.partner == null) viewModel.showInviteDialog() else viewModel.requestDisconnectPartner(context)
        },
        onResendInvite = viewModel::resendPartnerInvite,
        onConfirmCancelInvite = viewModel::confirmCancelPendingInvite,
        onDismissCancelInvite = viewModel::dismissCancelPendingInvite,
        onOpenSecurityAlerts = viewModel::openSecurityAlerts,
        onRefreshSecurityAlerts = viewModel::refreshSecurityAlerts,
        onDismissSecurityAlerts = viewModel::dismissSecurityAlerts,
        onFixPushNotifications = {
            openNotificationSettings(context)
            alerts = alerts.copy(pushNotificationsMissing = false)
        },
        onFixAccessibility = {
            openAccessibilitySettings(context)
            alerts = alerts.copy(accessibilityMissing = false)
        },
        onFixDnsVpn = {
            showVpnDialog = true
        },
        onEditorTextChange = viewModel::updateEditorText,
        onEditorSave = viewModel::saveEditor,
        onEditorDismiss = viewModel::dismissEditor,
        onCountdownSelected = viewModel::setCountdown,
        onInfoDismiss = viewModel::dismissInfo,
        onGuideDismiss = viewModel::dismissGuide,
        onInviteEmailChange = viewModel::updateInviteEmail,
        onInviteNameChange = viewModel::updateInviteName,
        onInviteSubmit = viewModel::submitInvite,
        onInviteDismiss = viewModel::dismissInviteDialog,
        onManageAccountabilityMode = { viewModel.requestChangeAccountabilityMode(context) },
        onSelectAccountabilityMode = { viewModel.requestSetAccountabilityMode(it, context) },
        onDismissModeSelection = viewModel::dismissModeSelection,
        onTypingChallengeSuccess = { viewModel.onTypingChallengeSuccess(context) },
        onDismissTypingChallenge = viewModel::dismissTypingChallenge,
        onConfirmTimeDelay = { viewModel.onStartTimeDelay(context) },
        onCancelTimeDelay = { viewModel.cancelTimeDelay(context) },
        onCompleteTimeDelay = { viewModel.completeReadyTimeDelay(context) },
        onDismissTimeDelayConfirm = viewModel::dismissTimeDelayConfirm,
        onVerifyFriendApprovalCode = { code -> viewModel.verifyFriendApprovalCode(code, context) },
        onDismissFriendApproval = viewModel::dismissFriendApproval,
        onRequestPartnerApproval = viewModel::requestPartnerApprovalCode,
        onConfirmDeviceAdmin = launchDeviceAdminConsent,
        onDismissDeviceAdmin = viewModel::dismissDeviceAdminDialog,
        onFixDeviceAdmin = viewModel::showDeviceAdminDialog,
        onResetMessageDefault = viewModel::resetMessageEditorToDefault,
        onClearRedirectUrl = viewModel::clearRedirectEditor,
        onPreviewBlockedScreen = viewModel::openBlockedScreenPreview,
        onDismissBlockedScreenPreview = viewModel::dismissBlockedScreenPreview
    )

    BlockingScreenContent(
        state = state,
        alerts = alerts,
        callbacks = callbacks,
        modifier = modifier
    )

    if (showVpnDialog) {
        VpnDnsExplanationDialog(
            onConfirm = {
                showVpnDialog = false
                launchVpnConsent()
            },
            onDismiss = { showVpnDialog = false }
        )
    }
}

@Composable
internal fun BlockingScreenContent(
    state: BlockingUiState,
    alerts: PermissionAlerts,
    callbacks: BlockingScreenCallbacks,
    modifier: Modifier = Modifier
) {
    val settings = state.settings
    val partnerLabel = state.partner?.let { p ->
        if (p.partnerName.isNotBlank()) "${p.partnerName} (${p.partnerEmail})" else p.partnerEmail
    }.orEmpty()
    val partnerStatus = when {
        state.isPartnerLinked -> "Active ($partnerLabel)"
        state.partner != null -> "Pending ($partnerLabel)"
        else -> "No partner linked"
    }

    val isPendingPartner = state.partner != null && !state.isPartnerLinked
    val partnerActionLabel = when {
        state.accountabilityMode == AccountabilityMode.FRIEND && state.partner == null -> "Invite"
        state.accountabilityMode == AccountabilityMode.FRIEND && state.isPartnerLinked -> "Remove"
        state.accountabilityMode == AccountabilityMode.FRIEND && isPendingPartner -> "Cancel"
        else -> null
    }

    var showAccessibilityDialog by remember { mutableStateOf(false) }
    var showNotificationDialog by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 96.dp)
        ) {
            BlockingHeader(
                onPremiumClick = callbacks.onPremiumClick,
                onGuideClick = callbacks.onGuideClick
            )

            Spacer(modifier = Modifier.height(6.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                AnimatedVisibility(visible = alerts.pushNotificationsMissing) {
                    AlertActionCard(
                        title = "Push Notifications",
                        subtitle = "If allowed, you will get notified of important updates and changes",
                        actionLabel = "Allow",
                        trailingIcon = R.drawable.ic_notification,
                        onAction = { showNotificationDialog = true }
                    )
                }

                AnimatedVisibility(visible = alerts.accessibilityMissing) {
                    AlertActionCard(
                        title = "Accessibility is OFF",
                        subtitle = "Some features may not work unless you turn on accessibility service.",
                        actionLabel = "Turn ON",
                        trailingIcon = R.drawable.ic_chevron_right,
                        onAction = { showAccessibilityDialog = true }
                    )
                }

                AnimatedVisibility(visible = alerts.dnsVpnInactive) {
                    AlertActionCard(
                        title = "DNS Shield is Paused",
                        subtitle = "Turn ON local DNS filtering to block adult and custom domains across browsers and WebViews.",
                        actionLabel = "Turn ON",
                        trailingIcon = R.drawable.ic_chevron_right,
                        onAction = callbacks.onFixDnsVpn
                    )
                }

                AnimatedVisibility(visible = alerts.deviceAdminMissing) {
                    AlertActionCard(
                        title = "Device Admin is OFF",
                        subtitle = "Uninstall Protection needs Device Administrator active to prevent unauthorized removal.",
                        actionLabel = "Activate",
                        trailingIcon = R.drawable.ic_chevron_right,
                        onAction = callbacks.onFixDeviceAdmin
                    )
                }

                AnimatedVisibility(visible = state.timeDelayUnlockAt > 0L) {
                    TimeDelayActiveBanner(
                        targetFeatureTitle = targetDisplayName(state.timeDelayTarget),
                        unlockAt = state.timeDelayUnlockAt,
                        onCancel = callbacks.onCancelTimeDelay,
                        onCompleteUnlock = callbacks.onCompleteTimeDelay,
                        isServerReady = state.isTimeDelayServerReady
                    )
                }

                AccountabilityCard(
                    mode = state.accountabilityMode,
                    partnerStatus = partnerStatus,
                    onManageMode = callbacks.onManageAccountabilityMode,
                    onPartnerAction = callbacks.onPartnerAction,
                    partnerActionLabel = partnerActionLabel,
                    isPendingPartner = isPendingPartner,
                    isLinkedPartner = state.isPartnerLinked,
                    onResendInvite = callbacks.onResendInvite,
                    onViewSecurityAlerts = callbacks.onOpenSecurityAlerts,
                    onInfoClick = {
                        callbacks.onInfo(
                            "Accountability Guard",
                            "Choose friction mode before turning off protection:\n\n• Friend: Partner must approve via email code and receives tamper/SOS security alerts.\n• Myself: 60-second randomized typing challenge (copy/paste disabled).\n• 24h Time Delay: Server-backed 24-hour cooling-off period."
                        )
                    }
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            ContentBlockingSection(
                settings = settings,
                isDnsVpnEnabled = state.isDnsVpnEnabled,
                onToggle = callbacks.onToggle,
                onInfo = callbacks.onInfo,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            Spacer(modifier = Modifier.height(18.dp))

            AdvancedBlockingSection(
                settings = settings,
                onToggle = callbacks.onToggle,
                onInfo = callbacks.onInfo,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            Spacer(modifier = Modifier.height(18.dp))

            CustomizeBlockedScreenSection(
                settings = settings,
                onEdit = callbacks.onEditSetting,
                onInfo = callbacks.onInfo,
                onPreview = callbacks.onPreviewBlockedScreen,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }

        ExtendedFloatingActionButton(
            onClick = callbacks.onFocusModeClick,
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 6.dp),
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = 16.dp)
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_focus_mode),
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Focus Mode",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold
            )
        }
    }

    BlockingScreenDialogs(state = state, callbacks = callbacks)

    if (showAccessibilityDialog) {
        AccessibilityExplanationDialog(
            onConfirm = {
                showAccessibilityDialog = false
                callbacks.onFixAccessibility()
            },
            onDismiss = { showAccessibilityDialog = false }
        )
    }

    if (showNotificationDialog) {
        NotificationExplanationDialog(
            onConfirm = {
                showNotificationDialog = false
                callbacks.onFixPushNotifications()
            },
            onDismiss = { showNotificationDialog = false }
        )
    }
}

@Composable
private fun BlockingScreenDialogs(
    state: BlockingUiState,
    callbacks: BlockingScreenCallbacks
) {
    when (state.editor) {
        SettingsEditor.MESSAGE -> BlockedScreenMessageDialog(
            text = state.editorText,
            errorMessage = state.editorError,
            onTextChange = callbacks.onEditorTextChange,
            onSave = callbacks.onEditorSave,
            onResetDefault = callbacks.onResetMessageDefault,
            onDismiss = callbacks.onEditorDismiss
        )
        SettingsEditor.COUNTDOWN -> BlockedScreenCountdownDialog(
            selectedSeconds = state.settings.blockedScreenCountdown,
            onSelect = callbacks.onCountdownSelected,
            onDismiss = callbacks.onEditorDismiss
        )
        SettingsEditor.REDIRECT -> RedirectUrlDialog(
            text = state.editorText,
            errorMessage = state.editorError,
            onTextChange = callbacks.onEditorTextChange,
            onSave = callbacks.onEditorSave,
            onClear = callbacks.onClearRedirectUrl,
            onDismiss = callbacks.onEditorDismiss
        )
        null -> Unit
    }

    if (state.isBlockedScreenPreviewVisible) {
        BlockedScreenPreviewDialog(
            message = state.settings.blockedScreenMessage,
            countdownSeconds = state.settings.blockedScreenCountdown,
            redirectUrl = state.settings.redirectUrl,
            onDismiss = callbacks.onDismissBlockedScreenPreview
        )
    }

    state.infoDialog?.let { info ->
        InfoDialog(
            title = info.title,
            message = info.message,
            onDismiss = callbacks.onInfoDismiss
        )
    }

    if (state.isGuideVisible) {
        GuideBottomSheet(onDismiss = callbacks.onGuideDismiss)
    }

    if (state.isInviteDialogVisible) {
        InvitePartnerDialog(
            email = state.inviteEmail,
            partnerName = state.inviteName,
            onEmailChange = callbacks.onInviteEmailChange,
            onPartnerNameChange = callbacks.onInviteNameChange,
            onSubmit = callbacks.onInviteSubmit,
            onDismiss = callbacks.onInviteDismiss,
            errorMessage = state.inviteEmailError
        )
    }

    if (state.isConfirmCancelInviteVisible) {
        ConfirmCancelInviteDialog(
            partnerEmail = state.partner?.partnerEmail.orEmpty(),
            onConfirm = callbacks.onConfirmCancelInvite,
            onDismiss = callbacks.onDismissCancelInvite
        )
    }

    if (state.isSecurityAlertsDialogVisible) {
        SecurityAlertsDialog(
            events = state.securityAlerts,
            isLoading = state.isLoadingSecurityAlerts,
            onRefresh = callbacks.onRefreshSecurityAlerts,
            onDismiss = callbacks.onDismissSecurityAlerts
        )
    }

    if (state.isModeSelectionVisible) {
        AccountabilityModeSelectionDialog(
            currentMode = state.accountabilityMode,
            onSelectMode = callbacks.onSelectAccountabilityMode,
            onDismiss = callbacks.onDismissModeSelection
        )
    }

    state.typingChallengeTarget?.let { target ->
        TypingChallengeDialog(
            targetFeatureTitle = target.title,
            onSuccess = callbacks.onTypingChallengeSuccess,
            onDismiss = callbacks.onDismissTypingChallenge
        )
    }

    state.timeDelayConfirmTarget?.let { target ->
        val activeTitle = state.timeDelayTarget
            .takeIf { it.isNotBlank() && it != target.identifier }
            ?.let { targetDisplayName(it) }
        TimeDelayConfirmDialog(
            targetFeatureTitle = target.title,
            activeTimerTitle = activeTitle,
            onConfirm = callbacks.onConfirmTimeDelay,
            onDismiss = callbacks.onDismissTimeDelayConfirm
        )
    }

    state.friendApprovalTarget?.let { target ->
        val featureTitle = target.title
        FriendApprovalDialog(
            targetFeatureTitle = featureTitle,
            partnerEmail = state.partner?.partnerEmail ?: "",
            onRequestCode = { callbacks.onRequestPartnerApproval(featureTitle) },
            onSubmitCode = callbacks.onVerifyFriendApprovalCode,
            onDismiss = callbacks.onDismissFriendApproval,
            isVerifying = state.isVerifyingUnlockCode,
            isSendingRequest = state.isSendingUnlockCode,
            emailSentSuccess = state.unlockEmailSent,
            errorMessage = state.unlockCodeError,
            requestError = state.unlockRequestError
        )
    }

    if (state.isDeviceAdminDialogVisible) {
        DeviceAdminPermissionDialog(
            onConfirm = callbacks.onConfirmDeviceAdmin,
            onDismiss = callbacks.onDismissDeviceAdmin
        )
    }
}

private fun readPermissionAlerts(
    context: Context,
    dnsVpnEnabled: Boolean,
    isVpnRunning: Boolean,
    uninstallProtectionEnabled: Boolean = false
): PermissionAlerts = PermissionAlerts(
    pushNotificationsMissing = !NotificationManagerCompat.from(context).areNotificationsEnabled(),
    accessibilityMissing = !PGuardAccessibilityService.isEnabled(context),
    dnsVpnInactive = dnsVpnEnabled && !isVpnRunning,
    deviceAdminMissing = uninstallProtectionEnabled && !DeviceAdminManager.isDeviceAdminActive(context)
)

@Preview(showBackground = true)
@Composable
private fun BlockingScreenPreview() {
    PGuardTheme {
        BlockingScreenContent(
            state = BlockingUiState(),
            alerts = PermissionAlerts(
                pushNotificationsMissing = true,
                accessibilityMissing = true,
                dnsVpnInactive = false,
                deviceAdminMissing = false
            ),
            callbacks = previewCallbacks()
        )
    }
}

private fun previewCallbacks() = BlockingScreenCallbacks(
    onPremiumClick = {},
    onGuideClick = {},
    onFocusModeClick = {},
    onToggle = { _, _ -> },
    onEditSetting = {},
    onInfo = { _, _ -> },
    onPartnerAction = {},
    onResendInvite = {},
    onConfirmCancelInvite = {},
    onDismissCancelInvite = {},
    onOpenSecurityAlerts = {},
    onRefreshSecurityAlerts = {},
    onDismissSecurityAlerts = {},
    onFixPushNotifications = {},
    onFixAccessibility = {},
    onFixDnsVpn = {},
    onEditorTextChange = {},
    onEditorSave = {},
    onEditorDismiss = {},
    onCountdownSelected = {},
    onInfoDismiss = {},
    onGuideDismiss = {},
    onInviteEmailChange = {},
    onInviteNameChange = {},
    onInviteSubmit = {},
    onInviteDismiss = {},
    onManageAccountabilityMode = {},
    onSelectAccountabilityMode = {},
    onDismissModeSelection = {},
    onTypingChallengeSuccess = {},
    onDismissTypingChallenge = {},
    onConfirmTimeDelay = {},
    onCancelTimeDelay = {},
    onCompleteTimeDelay = {},
    onDismissTimeDelayConfirm = {},
    onVerifyFriendApprovalCode = {},
    onDismissFriendApproval = {},
    onRequestPartnerApproval = {},
    onConfirmDeviceAdmin = {},
    onDismissDeviceAdmin = {},
    onFixDeviceAdmin = {},
    onResetMessageDefault = {},
    onClearRedirectUrl = {},
    onPreviewBlockedScreen = {},
    onDismissBlockedScreenPreview = {}
)
