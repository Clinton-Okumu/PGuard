package com.example.pguard.presentation.habits

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.pguard.R
import com.example.pguard.core.common.runSuspendCatching
import com.example.pguard.core.network.NetworkClient
import com.example.pguard.core.network.model.CreateHabitRequest
import com.example.pguard.core.network.model.ResetStreakRequest
import com.example.pguard.core.network.model.UserDto
import com.example.pguard.engine.accessibility.PGuardAccessibilityService
import com.example.pguard.presentation.dashboard.BlockingViewModel
import com.example.pguard.presentation.focus.FocusModeManager
import com.example.pguard.presentation.theme.CoralPrimary
import com.example.pguard.presentation.theme.PGuardTheme
import com.example.pguard.presentation.theme.PremiumGold
import com.example.pguard.presentation.theme.SuccessGreen
import kotlinx.coroutines.launch

@Composable
fun HabitsScreen(
    user: UserDto? = null,
    blockingViewModel: BlockingViewModel? = null,
    isActiveTab: Boolean = true,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val blockingState = blockingViewModel?.uiState?.collectAsState()?.value
    val uninstallProtectionEnabled = blockingState?.settings?.uninstallProtection
        ?: HabitsManager.isUninstallProtectionEnabled(context)
    val isBlockingLoading = blockingState?.isLoading ?: false
    val isAccessibilityRunning by PGuardAccessibilityService.isRunning.collectAsState()

    var refreshKey by remember { mutableIntStateOf(0) }

    var streakStatus by remember { mutableStateOf(HabitsManager.getStreakStatus(context)) }
    var urgesPrevented by remember { mutableIntStateOf(HabitsManager.getUrgesPrevented(context)) }
    var weeklyConsistency by remember { mutableStateOf(HabitsManager.getWeeklyConsistency(context)) }

    val habits = remember { mutableStateListOf<PersistentHabit>() }

    var uncelebratedMilestone by remember { mutableStateOf<MilestoneBadge?>(null) }
    var showDailyStoicDialog by remember { mutableStateOf(false) }
    var showSosIntervention by remember { mutableStateOf(false) }
    var showRelapseDialog by remember { mutableStateOf(false) }
    var showAddHabitDialog by remember { mutableStateOf(false) }
    var showMonthlyCalendarDialog by remember { mutableStateOf(false) }
    var habitToDelete by remember { mutableStateOf<PersistentHabit?>(null) }

    val milestones = remember {
        listOf(
            MilestoneBadge(1, "24 Hours", "First Step", R.drawable.ic_milestone_seed, SuccessGreen),
            MilestoneBadge(3, "3 Days", "Momentum", R.drawable.ic_feature_bolt, Color(0xFFFFD54F)),
            MilestoneBadge(7, "1 Week", "Neural Reset", R.drawable.ic_fire, CoralPrimary),
            MilestoneBadge(14, "2 Weeks", "Iron Will", R.drawable.ic_feature_shield, Color(0xFF64B5F6)),
            MilestoneBadge(30, "1 Month", "New Baseline", R.drawable.ic_target, Color(0xFF81C784)),
            MilestoneBadge(60, "60 Days", "Unshakeable", R.drawable.ic_feature_lock, Color(0xFFFFB74D)),
            MilestoneBadge(90, "90 Days", "Dopamine Reboot", R.drawable.ic_milestone_diamond, Color(0xFF00E5FF)),
            MilestoneBadge(180, "6 Months", "True Master", R.drawable.ic_ribbon_badge, Color(0xFFBA68C8)),
            MilestoneBadge(270, "9 Months", "Transcendence", R.drawable.ic_trophy, Color(0xFFFF8A65)),
            MilestoneBadge(365, "1 Year", "Living Legend", R.drawable.ic_crown, PremiumGold)
        )
    }

    fun refreshAll() {
        streakStatus = HabitsManager.getStreakStatus(context)
        urgesPrevented = HabitsManager.getUrgesPrevented(context)
        weeklyConsistency = HabitsManager.getWeeklyConsistency(context)
        habits.clear()
        habits.addAll(HabitsManager.getHabits(context))
        refreshKey++
    }

    DisposableEffect(lifecycleOwner, user?.id) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refreshAll()
                val activeUser = user
                if (activeUser != null) {
                    coroutineScope.launch {
                        HabitsManager.syncCalendarWithBackend(context, activeUser.id)
                        refreshAll()
                    }
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(uninstallProtectionEnabled, isAccessibilityRunning, isBlockingLoading, isActiveTab) {
        refreshAll()
        if (isActiveTab && user != null) {
            HabitsManager.syncCalendarWithBackend(context, user.id)
            refreshAll()
        }
    }

    LaunchedEffect(user?.id) {
        refreshAll()
        if (user != null) {
            HabitsManager.syncCalendarWithBackend(context, user.id)
            refreshAll()
        }
    }

    LaunchedEffect(streakStatus.isStreakActive, streakStatus.streakDays) {
        if (streakStatus.isStreakActive && streakStatus.streakDays > 0 && uncelebratedMilestone == null) {
            val uncelebratedDays = HabitsManager.getUncelebratedMilestone(
                context,
                streakStatus.streakDays,
                milestones.map { it.targetDays }
            )
            if (uncelebratedDays != null) {
                uncelebratedMilestone = milestones.firstOrNull { it.targetDays == uncelebratedDays }
            }
        }
    }

    LaunchedEffect(Unit) {
        if (uncelebratedMilestone == null && HabitsManager.shouldShowDailyStoic(context)) {
            showDailyStoicDialog = true
        }
    }

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
            Spacer(modifier = Modifier.height(14.dp))

            CleanStreakHeroCard(
                streakStatus = streakStatus,
                urgesPrevented = urgesPrevented,
                onSosClick = {
                    FocusModeManager.startUrgeLockdown(context, durationSeconds = 120)
                    showSosIntervention = true
                },
                onResetClick = { showRelapseDialog = true },
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            Spacer(modifier = Modifier.height(18.dp))

            WeeklyConsistencyCard(
                weeklyStatus = weeklyConsistency,
                onOpenMonthlyCalendar = { showMonthlyCalendarDialog = true },
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            Spacer(modifier = Modifier.height(18.dp))

            HabitsTrackerSection(
                habits = habits,
                onToggleHabit = { habit ->
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    val updatedList = HabitsManager.toggleHabit(context, habit.id)
                    habits.clear()
                    habits.addAll(updatedList)

                    coroutineScope.launch {
                        runSuspendCatching {
                            NetworkClient.getHabitsApiService(context).toggleHabit(habit.id.toLongOrNull() ?: 0)
                        }
                    }
                },
                onDeleteHabit = { habit ->
                    habitToDelete = habit
                },
                onAddHabitClick = { showAddHabitDialog = true },
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            Spacer(modifier = Modifier.height(18.dp))

            MilestonesSection(
                currentStreak = streakStatus.streakDays,
                milestones = milestones,
                onBadgeClick = { badge ->
                    if (streakStatus.streakDays >= badge.targetDays) {
                        uncelebratedMilestone = badge
                    }
                },
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            Spacer(modifier = Modifier.height(18.dp))

            DailyMindsetCard(
                onCardClick = { showDailyStoicDialog = true },
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }
    }

    if (uncelebratedMilestone != null) {
        val badge = uncelebratedMilestone!!
        MilestoneCelebrationDialog(
            milestone = badge,
            onClaimBadge = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                HabitsManager.markMilestoneCelebrated(context, badge.targetDays)
                uncelebratedMilestone = null

                if (HabitsManager.shouldShowDailyStoic(context)) {
                    showDailyStoicDialog = true
                }
            }
        )
    }

    if (showDailyStoicDialog) {
        val stoicQuote = remember { HabitsManager.getDailyStoicQuote() }
        DailyStoicReminderDialog(
            stoicQuote = stoicQuote,
            onDismiss = {
                HabitsManager.markDailyStoicShown(context)
                showDailyStoicDialog = false
            }
        )
    }

    if (showSosIntervention) {
        val incompleteHabit = habits.firstOrNull { !it.isCompletedToday }
        UrgeInterventionModal(
            incompleteHabit = incompleteHabit,
            onDismiss = {
                FocusModeManager.stopUrgeLockdown(context)
                showSosIntervention = false
            },
            onUrgeOvercome = {
                FocusModeManager.stopUrgeLockdown(context)
                HabitsManager.recordUrgeSurvived(context)
                refreshAll()
                showSosIntervention = false
            }
        )
    }

    if (showMonthlyCalendarDialog) {
        MonthlyProgressCalendarDialog(
            onDismiss = { showMonthlyCalendarDialog = false }
        )
    }

    if (habitToDelete != null) {
        val habit = habitToDelete!!
        AlertDialog(
            onDismissRequest = { habitToDelete = null },
            title = {
                Text(
                    text = "Delete Habit?",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            text = {
                Text(
                    text = "Are you sure you want to remove \"${habit.title}\"? This action cannot be undone.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val updated = HabitsManager.deleteHabit(context, habit.id)
                        habits.clear()
                        habits.addAll(updated)
                        habitToDelete = null

                        coroutineScope.launch {
                            runSuspendCatching {
                                NetworkClient.getHabitsApiService(context).deleteHabit(habit.id.toLongOrNull() ?: 0)
                            }
                        }
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text("Delete", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { habitToDelete = null }) {
                    Text("Cancel", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            shape = MaterialTheme.shapes.medium
        )
    }

    if (showRelapseDialog) {
        ReflectiveRelapseDialog(
            onDismiss = { showRelapseDialog = false },
            onConfirmReset = { trigger, note ->
                HabitsManager.resetStreak(context, trigger, note)
                refreshAll()
                showRelapseDialog = false
                coroutineScope.launch {
                    runSuspendCatching {
                        NetworkClient.getHabitsApiService(context).resetStreak(
                            ResetStreakRequest(trigger = trigger, notes = note)
                        )
                    }
                }
            }
        )
    }

    if (showAddHabitDialog) {
        AddHabitDialog(
            onDismiss = { showAddHabitDialog = false },
            onConfirmAdd = { title, iconKey, targetDays ->
                val newHabit = HabitsManager.addHabit(context, title, iconKey, targetDays)
                habits.add(newHabit)
                showAddHabitDialog = false

                coroutineScope.launch {
                    runSuspendCatching {
                        NetworkClient.getHabitsApiService(context).createHabit(
                            CreateHabitRequest(title = title, icon = iconKey)
                        )
                    }
                }
            }
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun HabitsScreenPreview() {
    PGuardTheme {
        HabitsScreen()
    }
}
