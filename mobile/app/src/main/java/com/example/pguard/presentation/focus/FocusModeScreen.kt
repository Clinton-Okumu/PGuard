package com.example.pguard.presentation.focus

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.pguard.R
import com.example.pguard.engine.accessibility.BlockingPreferences
import com.example.pguard.engine.accessibility.PGuardAccessibilityService
import com.example.pguard.presentation.blocklist.InstalledAppItem
import com.example.pguard.presentation.blocklist.queryDeviceLauncherApps
import com.example.pguard.presentation.dashboard.AccountabilityMode
import com.example.pguard.presentation.dashboard.BlockingViewModel
import com.example.pguard.presentation.dashboard.FriendApprovalDialog
import com.example.pguard.presentation.dashboard.TimeDelayActiveBanner
import com.example.pguard.presentation.dashboard.TimeDelayConfirmDialog
import com.example.pguard.presentation.dashboard.TypingChallengeDialog
import com.example.pguard.presentation.dashboard.targetDisplayName
import com.example.pguard.presentation.theme.pguardColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

@Composable
fun FocusModeScreen(
    onBackClick: () -> Unit,
    onUpgradeClick: () -> Unit = {},
    blockingViewModel: BlockingViewModel? = null
) {
    BackHandler(onBack = onBackClick)

    val context = LocalContext.current
    val schedules = remember { mutableStateListOf<FocusSchedule>() }

    val blockingUiState = blockingViewModel?.uiState?.collectAsState()?.value
    val activeFrictionMode = blockingUiState?.accountabilityMode
        ?: AccountabilityMode.fromCode(PGuardAccessibilityService.accountabilityMode)

    var showHelpDialog by remember { mutableStateOf(false) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var showLimitToast by remember { mutableStateOf(false) }

    var showTypingChallenge by remember { mutableStateOf(false) }
    var showTimeDelayConfirm by remember { mutableStateOf(false) }
    var showFriendApproval by remember { mutableStateOf(false) }
    var pendingFrictionAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var pendingTimeDelayTargetId by remember { mutableStateOf("") }
    var localUnlockError by remember { mutableStateOf<String?>(null) }

    var showAppPickerDialog by remember { mutableStateOf(false) }
    var currentAllowedAppsTarget by remember { mutableStateOf<Set<String>>(emptySet()) }
    var onAllowedAppsSelectedCallback by remember { mutableStateOf<((Set<String>) -> Unit)?>(null) }

    fun refreshSchedules() {
        schedules.clear()
        schedules.addAll(FocusModeManager.getSchedules(context))
    }

    LaunchedEffect(Unit) {
        refreshSchedules()
    }

    LaunchedEffect(showLimitToast) {
        if (showLimitToast) {
            delay(3500L)
            showLimitToast = false
        }
    }

    fun triggerProtectedScheduleAction(targetId: String, action: () -> Unit) {
        when (activeFrictionMode) {
            AccountabilityMode.NONE -> {
                action()
            }
            AccountabilityMode.SELF -> {
                pendingFrictionAction = action
                showTypingChallenge = true
            }
            AccountabilityMode.TIME_DELAY -> {
                if (BlockingPreferences.isTimeDelayElapsed(context, targetId)) {
                    blockingViewModel?.cancelTimeDelay(context) ?: BlockingPreferences.clearTimeDelay(context)
                    action()
                } else {
                    pendingTimeDelayTargetId = targetId
                    pendingFrictionAction = null
                    showTimeDelayConfirm = true
                }
            }
            AccountabilityMode.FRIEND -> {
                val partner = blockingUiState?.partner
                val isLinked = blockingUiState?.isPartnerLinked ?: false
                if (partner != null && isLinked) {
                    localUnlockError = null
                    blockingViewModel?.clearUnlockErrors()
                    pendingFrictionAction = action
                    showFriendApproval = true
                } else {
                    action()
                }
            }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            FocusModeTopBar(
                onBackClick = onBackClick,
                onHelpClick = { showHelpDialog = true }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    currentAllowedAppsTarget = emptySet()
                    showCreateDialog = true
                },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = MaterialTheme.shapes.medium,
                elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 6.dp),
                modifier = Modifier
                    .navigationBarsPadding()
                    .padding(bottom = 12.dp, end = 4.dp)
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_add),
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "SCHEDULE",
                    style = MaterialTheme.typography.labelLarge.copy(letterSpacing = 0.5.sp),
                    fontWeight = FontWeight.Bold
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (schedules.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Empty List",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    val pendingTarget = blockingUiState?.timeDelayTarget ?: BlockingPreferences.timeDelayTarget
                    val pendingUnlockAt = blockingUiState?.timeDelayUnlockAt ?: BlockingPreferences.timeDelayUnlockAt
                    if (pendingUnlockAt > 0L && pendingTarget.startsWith("FOCUS_SCHEDULE:")) {
                        TimeDelayActiveBanner(
                            targetFeatureTitle = targetDisplayName(pendingTarget),
                            unlockAt = pendingUnlockAt,
                            onCancel = {
                                blockingViewModel?.cancelTimeDelay(context) ?: BlockingPreferences.clearTimeDelay(context)
                            }
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                    }

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        items(schedules, key = { it.id }) { schedule ->
                            FocusScheduleCard(
                                schedule = schedule,
                                onToggle = { isEnabled ->
                                    if (!isEnabled) {
                                        triggerProtectedScheduleAction("FOCUS_SCHEDULE:${schedule.id}") {
                                            FocusModeManager.toggleSchedule(context, schedule.id, false)
                                            refreshSchedules()
                                        }
                                    } else {
                                        FocusModeManager.toggleSchedule(context, schedule.id, true)
                                        refreshSchedules()
                                    }
                                },
                                onDelete = {
                                    triggerProtectedScheduleAction("FOCUS_SCHEDULE:${schedule.id}") {
                                        FocusModeManager.deleteSchedule(context, schedule.id)
                                        refreshSchedules()
                                    }
                                }
                            )
                        }
                    }
                }
            }

            AnimatedVisibility(
                visible = showLimitToast,
                enter = fadeIn() + slideInVertically(initialOffsetY = { it }),
                exit = fadeOut() + slideOutVertically(targetOffsetY = { it }),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 86.dp)
            ) {
                FreeLimitPillToast(
                    onDismiss = { showLimitToast = false },
                    onUpgradeClick = onUpgradeClick
                )
            }
        }
    }

    if (showHelpDialog) {
        FocusHelpDialog(onDismiss = { showHelpDialog = false })
    }

    if (showTypingChallenge) {
        TypingChallengeDialog(
            targetFeatureTitle = "Focus Schedule",
            onSuccess = {
                showTypingChallenge = false
                pendingFrictionAction?.invoke()
                pendingFrictionAction = null
            },
            onDismiss = {
                showTypingChallenge = false
                pendingFrictionAction = null
            }
        )
    }

    if (showTimeDelayConfirm) {
        TimeDelayConfirmDialog(
            targetFeatureTitle = "Focus Schedule",
            onConfirm = {
                showTimeDelayConfirm = false
                if (pendingTimeDelayTargetId.isNotBlank()) {
                    if (blockingViewModel != null) {
                        blockingViewModel.startTimeDelayForTarget(pendingTimeDelayTargetId, context)
                    } else {
                        val duration = BlockingPreferences.DEFAULT_TIME_DELAY_MS
                        BlockingPreferences.setTimeDelay(context, System.currentTimeMillis() + duration, pendingTimeDelayTargetId, duration)
                    }
                }
                pendingTimeDelayTargetId = ""
                pendingFrictionAction = null
            },
            onDismiss = {
                showTimeDelayConfirm = false
                pendingTimeDelayTargetId = ""
                pendingFrictionAction = null
            }
        )
    }

    if (showFriendApproval) {
        FriendApprovalDialog(
            targetFeatureTitle = "Focus Schedule",
            partnerEmail = blockingUiState?.partner?.partnerEmail ?: "",
            onRequestCode = {
                localUnlockError = null
                blockingViewModel?.requestPartnerApprovalCode("Focus Schedule")
            },
            onSubmitCode = { code ->
                if (blockingViewModel != null) {
                    blockingViewModel.verifyExternalUnlockCode(
                        enteredCode = code,
                        onSuccess = {
                            showFriendApproval = false
                            localUnlockError = null
                            pendingFrictionAction?.invoke()
                            pendingFrictionAction = null
                        },
                        onError = { err ->
                            localUnlockError = err
                        }
                    )
                } else {
                    localUnlockError = "Accountability verification service is unavailable."
                }
            },
            onDismiss = {
                showFriendApproval = false
                localUnlockError = null
                blockingViewModel?.clearUnlockErrors()
                pendingFrictionAction = null
            },
            isVerifying = blockingUiState?.isVerifyingUnlockCode ?: false,
            errorMessage = localUnlockError ?: blockingUiState?.unlockCodeError,
            requestError = blockingUiState?.unlockRequestError
        )
    }

    if (showCreateDialog) {
        CreateScheduleDialog(
            initialSchedule = null,
            selectedAllowedApps = currentAllowedAppsTarget,
            onDismiss = { showCreateDialog = false },
            onPickAllowedAppsClick = { currentSet ->
                currentAllowedAppsTarget = currentSet
                onAllowedAppsSelectedCallback = { updatedSet ->
                    currentAllowedAppsTarget = updatedSet
                }
                showAppPickerDialog = true
            },
            onSaveSchedule = { newSchedule ->
                FocusModeManager.saveSchedule(context, newSchedule)
                refreshSchedules()
                showCreateDialog = false
            }
        )
    }

    if (showAppPickerDialog) {
        AllowedAppsPickerDialog(
            context = context,
            alreadySelectedPackages = currentAllowedAppsTarget,
            onDismiss = { showAppPickerDialog = false },
            onSaveSelection = { updatedList ->
                onAllowedAppsSelectedCallback?.invoke(updatedList)
                showAppPickerDialog = false
            }
        )
    }
}

@Composable
private fun FocusModeTopBar(
    onBackClick: () -> Unit,
    onHelpClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBackClick) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_arrow_back),
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Focus Mode",
                    color = MaterialTheme.colorScheme.onBackground,
                    style = MaterialTheme.typography.titleLarge
                )
            }

            IconButton(onClick = onHelpClick) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_help_circle),
                    contentDescription = "Help",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

@Composable
private fun FocusScheduleCard(
    schedule: FocusSchedule,
    onToggle: (Boolean) -> Unit,
    onDelete: () -> Unit
) {
    var isAccordionExpanded by remember { mutableStateOf(false) }
    val primary = MaterialTheme.colorScheme.primary
    val extendedColors = MaterialTheme.pguardColors

    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = schedule.formattedTimeRange,
                    color = primary,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Switch(
                        checked = schedule.isEnabled,
                        onCheckedChange = onToggle,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                            checkedTrackColor = extendedColors.success,
                            uncheckedThumbColor = extendedColors.switchThumbOff,
                            uncheckedTrackColor = extendedColors.switchTrackOff,
                            uncheckedBorderColor = Color.Transparent
                        )
                    )

                    IconButton(onClick = onDelete) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_delete),
                            contentDescription = "Delete",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(19.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Surface(
                shape = MaterialTheme.shapes.extraSmall,
                color = MaterialTheme.colorScheme.surfaceContainerHigh
            ) {
                Text(
                    text = schedule.formattedDaysBadge,
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isAccordionExpanded = !isAccordionExpanded }
                    .padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Allowed Apps (${schedule.allowedApps.size})",
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.labelLarge
                )

                Icon(
                    painter = painterResource(id = R.drawable.ic_chevron_right),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
            }

            if (isAccordionExpanded) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Messages, Calls, Contacts & PGuard are default allowed.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )

                if (schedule.allowedApps.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Additional allowed: ${schedule.allowedApps.joinToString(", ")}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_feature_lock),
                        contentDescription = null,
                        tint = primary,
                        modifier = Modifier.size(13.dp)
                    )
                    Text(
                        text = "Allowed apps are locked once scheduled to prevent bypass.",
                        color = primary.copy(alpha = 0.9f),
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        }
    }
}

@Composable
private fun AllowedAppsPickerDialog(
    context: Context,
    alreadySelectedPackages: Set<String>,
    onDismiss: () -> Unit,
    onSaveSelection: (Set<String>) -> Unit
) {
    val selected = remember { mutableStateListOf<String>().apply { addAll(alreadySelectedPackages) } }
    var apps by remember { mutableStateOf<List<InstalledAppItem>>(emptyList()) }
    var searchQuery by remember { mutableStateOf("") }
    val primary = MaterialTheme.colorScheme.primary

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            apps = queryDeviceLauncherApps(context)
        }
    }

    val filteredApps = remember(searchQuery, apps) {
        if (searchQuery.isBlank()) apps
        else apps.filter { it.name.contains(searchQuery, ignoreCase = true) || it.packageName.contains(searchQuery, ignoreCase = true) }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainer,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier
                .fillMaxWidth()
                .height(480.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(18.dp)
            ) {
                Text(
                    text = "Allowed Apps (${selected.size})",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "These apps will bypass blocking during focus time.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(14.dp))

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredApps, key = { it.packageName }) { app ->
                        val isChecked = selected.contains(app.packageName)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (isChecked) selected.remove(app.packageName)
                                    else selected.add(app.packageName)
                                }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = app.name,
                                color = MaterialTheme.colorScheme.onSurface,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f)
                            )
                            Checkbox(
                                checked = isChecked,
                                onCheckedChange = { check ->
                                    if (check) selected.add(app.packageName)
                                    else selected.remove(app.packageName)
                                },
                                colors = CheckboxDefaults.colors(
                                    checkedColor = primary,
                                    uncheckedColor = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = { onSaveSelection(selected.toSet()) },
                        colors = ButtonDefaults.buttonColors(containerColor = primary),
                        shape = MaterialTheme.shapes.small
                    ) {
                        Text("Apply", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary)
                    }
                }
            }
        }
    }
}
