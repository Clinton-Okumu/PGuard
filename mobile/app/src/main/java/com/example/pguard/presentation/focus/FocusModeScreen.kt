package com.example.pguard.presentation.focus

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.pguard.R
import com.example.pguard.engine.accessibility.BlockingPreferences
import com.example.pguard.engine.accessibility.PGuardAccessibilityService
import com.example.pguard.presentation.accountability.AccountabilityGuardDialogsHost
import com.example.pguard.presentation.accountability.rememberAccountabilityGuard
import com.example.pguard.presentation.blocklist.InstalledAppItem
import com.example.pguard.presentation.blocklist.queryDeviceLauncherApps
import com.example.pguard.presentation.dashboard.AlertActionCard
import com.example.pguard.presentation.dashboard.BlockingViewModel
import com.example.pguard.presentation.dashboard.TimeDelayActiveBanner
import com.example.pguard.presentation.dashboard.openAccessibilitySettings
import com.example.pguard.presentation.dashboard.targetDisplayName
import com.example.pguard.presentation.theme.pguardColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.Locale

@Composable
fun FocusModeScreen(
    onBackClick: () -> Unit,
    onUpgradeClick: () -> Unit = {},
    blockingViewModel: BlockingViewModel? = null
) {
    BackHandler(onBack = onBackClick)

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val schedules = remember { mutableStateListOf<FocusSchedule>() }
    val focusRevision by FocusModeManager.focusRevision.collectAsState()

    val blockingUiState = blockingViewModel?.uiState?.collectAsState()?.value
    val accountabilityGuard = rememberAccountabilityGuard(blockingViewModel)

    var isAccessibilityEnabled by remember { mutableStateOf(PGuardAccessibilityService.isEnabled(context)) }
    var quickFocusRemainingSecs by remember { mutableIntStateOf(FocusModeManager.getQuickFocusRemainingSeconds(context)) }
    var currentCalendar by remember { mutableStateOf(Calendar.getInstance()) }

    var showHelpDialog by remember { mutableStateOf(false) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var editingSchedule by remember { mutableStateOf<FocusSchedule?>(null) }
    var showQuickFocusDialog by remember { mutableStateOf(false) }

    var showAppPickerDialog by remember { mutableStateOf(false) }
    var currentAllowedAppsTarget by remember { mutableStateOf<Set<String>>(emptySet()) }
    var onAllowedAppsSelectedCallback by remember { mutableStateOf<((Set<String>) -> Unit)?>(null) }

    fun refreshFocusState() {
        schedules.clear()
        schedules.addAll(FocusModeManager.getSchedules(context))
        quickFocusRemainingSecs = FocusModeManager.getQuickFocusRemainingSeconds(context)
        currentCalendar = Calendar.getInstance()
    }

    LaunchedEffect(focusRevision) {
        refreshFocusState()
    }

    // Keep live "ACTIVE NOW" badges and Quick Focus timer updating every second
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000L)
            quickFocusRemainingSecs = FocusModeManager.getQuickFocusRemainingSeconds(context)
            currentCalendar = Calendar.getInstance()
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isAccessibilityEnabled = PGuardAccessibilityService.isEnabled(context)
                refreshFocusState()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    fun triggerProtectedFocusAction(targetId: String, targetTitle: String, action: () -> Unit) {
        accountabilityGuard.guard(targetId = targetId, targetTitle = targetTitle, action = action)
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
                    editingSchedule = null
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
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(top = 12.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (!isAccessibilityEnabled) {
                item(key = "accessibility_warning") {
                    AlertActionCard(
                        title = "Accessibility is OFF",
                        subtitle = "Focus Mode cannot block apps until PGuard Accessibility Service is turned on.",
                        actionLabel = "Turn ON",
                        trailingIcon = R.drawable.ic_chevron_right,
                        onAction = { openAccessibilitySettings(context) }
                    )
                }
            }

            item(key = "quick_focus_card") {
                QuickFocusCard(
                    remainingSeconds = quickFocusRemainingSecs,
                    onStartPreset = { mins ->
                        FocusModeManager.startQuickFocus(context, mins)
                    },
                    onOpenCustom = {
                        currentAllowedAppsTarget = emptySet()
                        showQuickFocusDialog = true
                    },
                    onStop = {
                        triggerProtectedFocusAction(
                            targetId = "FOCUS_QUICK",
                            targetTitle = "Stop Quick Focus"
                        ) {
                            FocusModeManager.stopQuickFocus(context)
                        }
                    }
                )
            }

            val pendingTarget = blockingUiState?.timeDelayTarget ?: BlockingPreferences.timeDelayTarget
            val pendingUnlockAt = blockingUiState?.timeDelayUnlockAt ?: BlockingPreferences.timeDelayUnlockAt
            val serverReady = blockingUiState?.isTimeDelayServerReady ?: false
            if (pendingUnlockAt > 0L && pendingTarget.startsWith("FOCUS_")) {
                item(key = "focus_time_delay_banner") {
                    TimeDelayActiveBanner(
                        targetFeatureTitle = targetDisplayName(pendingTarget),
                        unlockAt = pendingUnlockAt,
                        isServerReady = serverReady,
                        onCancel = {
                            blockingViewModel?.cancelTimeDelay(context) ?: BlockingPreferences.clearTimeDelay(context)
                        },
                        onCompleteUnlock = {
                            if (blockingViewModel != null) {
                                blockingViewModel.completeReadyTimeDelay(context)
                            } else if (BlockingPreferences.isTimeDelayElapsed(context, pendingTarget)) {
                                BlockingPreferences.clearTimeDelay(context)
                                when {
                                    pendingTarget == "FOCUS_QUICK" -> FocusModeManager.stopQuickFocus(context)
                                    pendingTarget.startsWith("FOCUS_SCHEDULE_TOGGLE:") ->
                                        FocusModeManager.toggleSchedule(context, pendingTarget.removePrefix("FOCUS_SCHEDULE_TOGGLE:"), false)
                                    pendingTarget.startsWith("FOCUS_SCHEDULE_DELETE:") ->
                                        FocusModeManager.deleteSchedule(context, pendingTarget.removePrefix("FOCUS_SCHEDULE_DELETE:"))
                                    pendingTarget.startsWith("FOCUS_SCHEDULE:") ->
                                        FocusModeManager.toggleSchedule(context, pendingTarget.removePrefix("FOCUS_SCHEDULE:"), false)
                                }
                            }
                        }
                    )
                }
            }

            if (schedules.isEmpty()) {
                item(key = "empty_schedules") {
                    EmptySchedulesCard(
                        onCreateSchedule = {
                            editingSchedule = null
                            currentAllowedAppsTarget = emptySet()
                            showCreateDialog = true
                        }
                    )
                }
            } else {
                items(schedules, key = { it.id }) { schedule ->
                    FocusScheduleCard(
                        schedule = schedule,
                        isActiveNow = schedule.isCurrentlyActive(currentCalendar),
                        onToggle = { isEnabled ->
                            if (!isEnabled) {
                                triggerProtectedFocusAction(
                                    targetId = "FOCUS_SCHEDULE_TOGGLE:${schedule.id}",
                                    targetTitle = "Disable ${schedule.displayName}"
                                ) {
                                    FocusModeManager.toggleSchedule(context, schedule.id, false)
                                }
                            } else {
                                FocusModeManager.toggleSchedule(context, schedule.id, true)
                            }
                        },
                        onEdit = {
                            editingSchedule = schedule
                            currentAllowedAppsTarget = schedule.allowedApps
                            showCreateDialog = true
                        },
                        onDelete = {
                            if (schedule.isEnabled) {
                                triggerProtectedFocusAction(
                                    targetId = "FOCUS_SCHEDULE_DELETE:${schedule.id}",
                                    targetTitle = "Delete ${schedule.displayName}"
                                ) {
                                    FocusModeManager.deleteSchedule(context, schedule.id)
                                }
                            } else {
                                FocusModeManager.deleteSchedule(context, schedule.id)
                            }
                        }
                    )
                }
            }
        }
    }

    if (showHelpDialog) {
        FocusHelpDialog(onDismiss = { showHelpDialog = false })
    }

    AccountabilityGuardDialogsHost(
        controller = accountabilityGuard,
        blockingViewModel = blockingViewModel
    )

    if (showCreateDialog) {
        CreateScheduleDialog(
            initialSchedule = editingSchedule,
            selectedAllowedApps = currentAllowedAppsTarget,
            onDismiss = {
                showCreateDialog = false
                editingSchedule = null
            },
            onPickAllowedAppsClick = { currentSet ->
                currentAllowedAppsTarget = currentSet
                onAllowedAppsSelectedCallback = { updatedSet ->
                    currentAllowedAppsTarget = updatedSet
                }
                showAppPickerDialog = true
            },
            onSaveSchedule = { saved ->
                FocusModeManager.saveSchedule(context, saved)
                showCreateDialog = false
                editingSchedule = null
            }
        )
    }

    if (showQuickFocusDialog) {
        QuickFocusDialog(
            selectedAllowedApps = currentAllowedAppsTarget,
            onPickAllowedAppsClick = { currentSet ->
                currentAllowedAppsTarget = currentSet
                onAllowedAppsSelectedCallback = { updatedSet ->
                    currentAllowedAppsTarget = updatedSet
                }
                showAppPickerDialog = true
            },
            onStartQuickFocus = { durationMins, allowed ->
                FocusModeManager.startQuickFocus(context, durationMins, allowed)
                showQuickFocusDialog = false
            },
            onDismiss = { showQuickFocusDialog = false }
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
private fun QuickFocusCard(
    remainingSeconds: Int,
    onStartPreset: (Int) -> Unit,
    onOpenCustom: () -> Unit,
    onStop: () -> Unit
) {
    val primary = MaterialTheme.colorScheme.primary
    val success = MaterialTheme.pguardColors.success
    val isActive = remainingSeconds > 0

    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border = BorderStroke(1.dp, if (isActive) success.copy(alpha = 0.55f) else MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (isActive) "Quick Focus Active" else "Quick Focus",
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (isActive) {
                            "Blocking non-essential apps right now"
                        } else {
                            "Start an instant focus sprint without adding a schedule"
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                if (isActive) {
                    val mins = remainingSeconds / 60
                    val secs = remainingSeconds % 60
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = success.copy(alpha = 0.16f)
                    ) {
                        Text(
                            text = String.format(Locale.US, "%02d:%02d", mins, secs),
                            color = success,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (isActive) {
                OutlinedButton(
                    onClick = onStop,
                    shape = MaterialTheme.shapes.small,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Stop Early",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(15 to "15m", 30 to "30m", 60 to "1h").forEach { (mins, label) ->
                        OutlinedButton(
                            onClick = { onStartPreset(mins) },
                            shape = MaterialTheme.shapes.small,
                            border = BorderStroke(1.dp, primary.copy(alpha = 0.6f)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = primary),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Button(
                        onClick = onOpenCustom,
                        shape = MaterialTheme.shapes.small,
                        colors = ButtonDefaults.buttonColors(containerColor = primary),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                        modifier = Modifier.weight(1.1f)
                    ) {
                        Text(
                            text = "Custom",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptySchedulesCard(onCreateSchedule: () -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 28.dp)
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_focus_mode),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(36.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "No Recurring Focus Schedules",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Schedule daily deep-work or sleep windows where only essential tools stay accessible.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedButton(
                onClick = onCreateSchedule,
                shape = MaterialTheme.shapes.small,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary)
            ) {
                Text(
                    text = "Create First Schedule",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun FocusScheduleCard(
    schedule: FocusSchedule,
    isActiveNow: Boolean,
    onToggle: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    var isAccordionExpanded by remember { mutableStateOf(false) }
    val primary = MaterialTheme.colorScheme.primary
    val extendedColors = MaterialTheme.pguardColors

    val resolvedAppLabels = remember(schedule.allowedApps) {
        val pm = context.packageManager
        schedule.allowedApps.map { pkg ->
            try {
                val info = pm.getApplicationInfo(pkg, 0)
                pm.getApplicationLabel(info).toString()
            } catch (_: Exception) {
                pkg
            }
        }.sorted()
    }

    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        border = BorderStroke(
            1.dp,
            if (isActiveNow) extendedColors.success.copy(alpha = 0.55f) else MaterialTheme.colorScheme.outlineVariant
        ),
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
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = schedule.displayName,
                            color = MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (isActiveNow) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = extendedColors.success.copy(alpha = 0.16f)
                            ) {
                                Text(
                                    text = "ACTIVE NOW",
                                    color = extendedColors.success,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = schedule.formattedTimeRange,
                        color = primary,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
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

                    if (!schedule.isEnabled) {
                        IconButton(onClick = onEdit) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_edit),
                                contentDescription = "Edit Schedule",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

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
                    modifier = Modifier
                        .size(16.dp)
                        .rotate(if (isAccordionExpanded) 90f else 0f)
                )
            }

            if (isAccordionExpanded) {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Messages, Calls, Contacts, Keyboards & PGuard are allowed by default.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )

                if (resolvedAppLabels.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Additional allowed: ${resolvedAppLabels.joinToString(", ")}",
                        color = MaterialTheme.colorScheme.onSurface,
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
                        text = if (schedule.isEnabled) {
                            "Allowed apps are locked while enabled. Disable schedule to edit."
                        } else {
                            "Schedule is paused — tap the edit icon above to adjust apps or times."
                        },
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
    var isLoadingApps by remember { mutableStateOf(true) }
    var searchQuery by remember { mutableStateOf("") }
    val primary = MaterialTheme.colorScheme.primary

    LaunchedEffect(Unit) {
        isLoadingApps = true
        apps = withContext(Dispatchers.IO) {
            queryDeviceLauncherApps(context)
        }
        isLoadingApps = false
    }

    val filteredApps = remember(searchQuery, apps) {
        if (searchQuery.isBlank()) apps
        else apps.filter {
            it.name.contains(searchQuery, ignoreCase = true) ||
                it.packageName.contains(searchQuery, ignoreCase = true)
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainer,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier
                .fillMaxWidth()
                .height(520.dp)
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
                    text = "These apps stay accessible during your focus session.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search installed apps...") },
                    singleLine = true,
                    trailingIcon = if (searchQuery.isNotEmpty()) {
                        {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_close),
                                    contentDescription = "Clear search",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    } else null,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline
                    )
                )

                Spacer(modifier = Modifier.height(10.dp))

                if (isLoadingApps) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = primary)
                    }
                } else if (filteredApps.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No apps match \"$searchQuery\"",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(filteredApps, key = { it.packageName }) { app ->
                            val isChecked = selected.contains(app.packageName)
                            val iconBitmap = remember(app.packageName) { app.icon?.toFocusComposeBitmap() }
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
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    if (iconBitmap != null) {
                                        Image(
                                            bitmap = iconBitmap,
                                            contentDescription = app.name,
                                            modifier = Modifier
                                                .size(28.dp)
                                                .clip(RoundedCornerShape(6.dp))
                                        )
                                    } else {
                                        Box(
                                            modifier = Modifier
                                                .size(28.dp)
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                painter = painterResource(id = R.drawable.ic_focus_mode),
                                                contentDescription = null,
                                                tint = primary,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                    Text(
                                        text = app.name,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        style = MaterialTheme.typography.bodyMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
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

private fun Drawable.toFocusComposeBitmap(width: Int = 72, height: Int = 72): ImageBitmap? {
    return try {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        setBounds(0, 0, canvas.width, canvas.height)
        draw(canvas)
        bitmap.asImageBitmap()
    } catch (_: Exception) {
        null
    }
}
