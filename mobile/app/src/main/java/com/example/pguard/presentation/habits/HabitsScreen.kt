package com.example.pguard.presentation.habits

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.pguard.R
import com.example.pguard.core.network.NetworkClient
import com.example.pguard.core.network.model.CreateHabitRequest
import com.example.pguard.core.network.model.UserDto
import com.example.pguard.presentation.focus.FocusModeManager
import com.example.pguard.presentation.theme.CoralPrimary
import com.example.pguard.presentation.theme.PGuardTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Calendar

private val CardBackground = Color(0xFF18181C)
private val GoldColor = Color(0xFFFFB800)
private val EmeraldGreen = Color(0xFF4CAF50)

data class MilestoneBadge(
    val targetDays: Int,
    val title: String,
    val subtitle: String,
    val iconRes: Int,
    val badgeColor: Color = GoldColor
)

data class SelectableHabitIcon(
    val iconRes: Int,
    val tint: Color,
    val label: String,
    val key: String
)

fun getHabitIconRes(key: String): Int = when (key) {
    "water" -> R.drawable.ic_habit_water
    "fitness" -> R.drawable.ic_habit_fitness
    "book" -> R.drawable.ic_habit_book
    "meditation" -> R.drawable.ic_habit_meditation
    "journal" -> R.drawable.ic_habit_journal
    "walking" -> R.drawable.ic_habit_walking
    "noscreen" -> R.drawable.ic_habit_noscreen
    "target" -> R.drawable.ic_target
    "shield" -> R.drawable.ic_feature_shield
    "bolt" -> R.drawable.ic_feature_bolt
    "lightbulb" -> R.drawable.ic_lightbulb
    "trophy" -> R.drawable.ic_trophy
    "fire" -> R.drawable.ic_fire
    else -> R.drawable.ic_habit_book
}

fun getHabitIconTint(key: String): Color = when (key) {
    "water" -> Color(0xFF4FC3F7)
    "fitness" -> CoralPrimary
    "book" -> Color(0xFFFFB74D)
    "meditation" -> Color(0xFF64B5F6)
    "journal" -> Color(0xFF81C784)
    "walking" -> Color(0xFFFFD54F)
    "noscreen" -> Color(0xFFBA68C8)
    "target" -> Color(0xFFFF8A65)
    "shield" -> Color(0xFF26A69A)
    "bolt" -> Color(0xFFFFCA28)
    "lightbulb" -> Color(0xFFFF7043)
    "trophy" -> GoldColor
    "fire" -> CoralPrimary
    else -> CoralPrimary
}

@Composable
fun HabitsScreen(
    user: UserDto? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current

    // Trigger state to refresh UI when streak or habits change
    var refreshKey by remember { mutableIntStateOf(0) }

    // Streak & protection status (Streak is ON only if app is doing its job & uninstall protection is ON)
    var streakStatus by remember { mutableStateOf(HabitsManager.getStreakStatus(context)) }
    var urgesPrevented by remember { mutableIntStateOf(HabitsManager.getUrgesPrevented(context)) }
    var weeklyConsistency by remember { mutableStateOf(HabitsManager.getWeeklyConsistency(context)) }

    // Persistent habits list (supports removing hardcoded habits & adding new custom ones)
    val habits = remember { mutableStateListOf<PersistentHabit>() }

    fun refreshAll() {
        streakStatus = HabitsManager.getStreakStatus(context)
        urgesPrevented = HabitsManager.getUrgesPrevented(context)
        weeklyConsistency = HabitsManager.getWeeklyConsistency(context)
        habits.clear()
        habits.addAll(HabitsManager.getHabits(context))
        refreshKey++
    }

    LaunchedEffect(Unit) {
        refreshAll()
    }

    // Modal and dialog states
    var showSosIntervention by remember { mutableStateOf(false) }
    var showRelapseDialog by remember { mutableStateOf(false) }
    var showAddHabitDialog by remember { mutableStateOf(false) }
    var showMonthlyCalendarDialog by remember { mutableStateOf(false) }
    var habitToDelete by remember { mutableStateOf<PersistentHabit?>(null) }

    // Milestones definition
    val milestones = remember {
        listOf(
            MilestoneBadge(1, "24 Hours", "First Step", R.drawable.ic_milestone_seed, EmeraldGreen),
            MilestoneBadge(3, "3 Days", "Break Momentum", R.drawable.ic_feature_bolt, Color(0xFFFFD54F)),
            MilestoneBadge(7, "1 Week", "Neural Reset", R.drawable.ic_fire, CoralPrimary),
            MilestoneBadge(14, "2 Weeks", "Iron Will", R.drawable.ic_feature_shield, Color(0xFF64B5F6)),
            MilestoneBadge(30, "1 Month", "True Freedom", R.drawable.ic_crown, GoldColor),
            MilestoneBadge(90, "90 Days", "Mastery", R.drawable.ic_milestone_diamond, Color(0xFF00E5FF))
        )
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

            // 1. Hero Clean Streak Card (Streak is ON if app is doing its job; OFF if uninstall protection is off)
            CleanStreakHeroCard(
                streakStatus = streakStatus,
                urgesPrevented = urgesPrevented,
                onSosClick = {
                    // Start 2-Minute Urge Lockdown (like Focus Mode for 2 minutes)
                    FocusModeManager.startUrgeLockdown(context, durationSeconds = 120)
                    showSosIntervention = true
                },
                onResetClick = { showRelapseDialog = true },
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            Spacer(modifier = Modifier.height(18.dp))

            // 2. Weekly Consistency Row (Linked directly to streak, click opens Monthly Calendar View)
            WeeklyConsistencyCard(
                weeklyStatus = weeklyConsistency,
                onOpenMonthlyCalendar = { showMonthlyCalendarDialog = true },
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            Spacer(modifier = Modifier.height(18.dp))

            // 3. Daily Habits Tracker Card (Fully dynamic: remove hardcoded habits, add custom ones, backend synced)
            HabitsTrackerSection(
                habits = habits,
                onToggleHabit = { habit ->
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    val updatedList = HabitsManager.toggleHabit(context, habit.id)
                    habits.clear()
                    habits.addAll(updatedList)

                    // Asynchronously sync with backend if online
                    coroutineScope.launch {
                        runCatching {
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

            // 4. Milestone Badges Section
            MilestonesSection(
                currentStreak = streakStatus.streakDays,
                milestones = milestones,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            Spacer(modifier = Modifier.height(18.dp))

            // 5. Daily Stoic Mindset Card
            DailyMindsetCard(modifier = Modifier.padding(horizontal = 16.dp))
        }
    }

    // 2-MINUTE URGE INTERVENTION MODAL (Focus Mode for 2 mins + Box Breathing + Habit Redirect)
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

    // MONTHLY PROGRESS CALENDAR MODAL (Full monthly calendar view linked to streak & relapses)
    if (showMonthlyCalendarDialog) {
        MonthlyProgressCalendarDialog(
            onDismiss = { showMonthlyCalendarDialog = false }
        )
    }

    // DELETE HABIT CONFIRMATION DIALOG
    if (habitToDelete != null) {
        val habit = habitToDelete!!
        AlertDialog(
            onDismissRequest = { habitToDelete = null },
            title = {
                Text("Delete Habit?", fontWeight = FontWeight.Bold, color = Color.White)
            },
            text = {
                Text(
                    "Are you sure you want to remove \"${habit.title}\"? This action cannot be undone.",
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val updated = HabitsManager.deleteHabit(context, habit.id)
                        habits.clear()
                        habits.addAll(updated)
                        habitToDelete = null

                        // Sync with backend
                        coroutineScope.launch {
                            runCatching {
                                NetworkClient.getHabitsApiService(context).deleteHabit(habit.id.toLongOrNull() ?: 0)
                            }
                        }
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = CoralPrimary)
                ) {
                    Text("Delete", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { habitToDelete = null }) {
                    Text("Cancel", color = Color.White.copy(alpha = 0.6f))
                }
            },
            containerColor = Color(0xFF1E1E24),
            shape = RoundedCornerShape(16.dp)
        )
    }

    // 2-Step Reflective Relapse Modal
    if (showRelapseDialog) {
        ReflectiveRelapseDialog(
            onDismiss = { showRelapseDialog = false },
            onConfirmReset = { trigger, note ->
                HabitsManager.resetStreak(context, trigger, note)
                refreshAll()
                showRelapseDialog = false
            }
        )
    }

    // Add Habit Dialog with Extended Icon Palette
    if (showAddHabitDialog) {
        AddHabitDialog(
            onDismiss = { showAddHabitDialog = false },
            onConfirmAdd = { title, iconKey, targetDays ->
                val newHabit = HabitsManager.addHabit(context, title, iconKey, targetDays)
                habits.add(newHabit)
                showAddHabitDialog = false

                // Sync with backend
                coroutineScope.launch {
                    runCatching {
                        NetworkClient.getHabitsApiService(context).createHabit(
                            CreateHabitRequest(title = title, icon = iconKey)
                        )
                    }
                }
            }
        )
    }
}

@Composable
private fun CleanStreakHeroCard(
    streakStatus: StreakStatus,
    urgesPrevented: Int,
    onSosClick: () -> Unit,
    onResetClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val isStreakOn = streakStatus.isStreakActive
    val streakDays = streakStatus.streakDays
    val bestStreak = streakStatus.bestStreakDays

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = CardBackground,
        border = BorderStroke(
            width = 1.dp,
            color = if (isStreakOn) Color.White.copy(alpha = 0.08f) else CoralPrimary.copy(alpha = 0.4f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header Row: Status Badge + Overflow Menu
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                // Status Badge
                Row(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(if (isStreakOn) EmeraldGreen.copy(alpha = 0.15f) else CoralPrimary.copy(alpha = 0.15f))
                        .border(
                            1.dp,
                            if (isStreakOn) EmeraldGreen.copy(alpha = 0.6f) else CoralPrimary.copy(alpha = 0.6f),
                            CircleShape
                        )
                        .padding(horizontal = 14.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        painter = painterResource(id = if (isStreakOn) R.drawable.ic_fire else R.drawable.ic_feature_shield),
                        contentDescription = null,
                        tint = if (isStreakOn) EmeraldGreen else CoralPrimary,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isStreakOn) "STREAK ACTIVE • PROTECTED" else "STREAK OFF • UNPROTECTED",
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        color = if (isStreakOn) EmeraldGreen else CoralPrimary,
                        letterSpacing = 0.8.sp
                    )
                }

                // Top-Right Overflow Menu for Reset
                Box(
                    modifier = Modifier.align(Alignment.CenterEnd)
                ) {
                    IconButton(
                        onClick = { menuExpanded = true },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_more_vert),
                            contentDescription = "Options",
                            tint = Color.White.copy(alpha = 0.45f),
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false },
                        modifier = Modifier.background(Color(0xFF22222A))
                    ) {
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = "Reset Streak...",
                                    color = CoralPrimary,
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            },
                            onClick = {
                                menuExpanded = false
                                onResetClick()
                            }
                        )
                    }
                }
            }

            // Warning Banner if streak is off because uninstall protection is off
            if (!isStreakOn && streakStatus.inactiveReason != null) {
                Spacer(modifier = Modifier.height(14.dp))
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = CoralPrimary.copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, CoralPrimary.copy(alpha = 0.3f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_feature_shield),
                            contentDescription = null,
                            tint = CoralPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = streakStatus.inactiveReason,
                            fontSize = 11.5.sp,
                            color = Color.White.copy(alpha = 0.9f),
                            lineHeight = 15.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Circular Streak Gauge
            val nextMilestone = if (streakDays < 14) 14 else if (streakDays < 30) 30 else 90
            val progress = if (isStreakOn) (streakDays.toFloat() / nextMilestone.toFloat()).coerceIn(0.05f, 1f) else 0f

            Box(
                modifier = Modifier.size(160.dp),
                contentAlignment = Alignment.Center
            ) {
                // Background track
                CircularProgressIndicator(
                    progress = { 1f },
                    modifier = Modifier.fillMaxSize(),
                    color = Color.White.copy(alpha = 0.07f),
                    strokeWidth = 10.dp,
                    strokeCap = StrokeCap.Round
                )
                // Active gradient progress
                CircularProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxSize(),
                    color = if (isStreakOn) CoralPrimary else Color.White.copy(alpha = 0.2f),
                    strokeWidth = 10.dp,
                    strokeCap = StrokeCap.Round
                )

                // Inner content
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if (isStreakOn) {
                        Text(
                            text = "$streakDays",
                            fontSize = 44.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White
                        )
                        Text(
                            text = if (streakDays == 1) "DAY FREE" else "DAYS FREE",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = CoralPrimary,
                            letterSpacing = 1.sp
                        )
                    } else {
                        Text(
                            text = "PAUSED",
                            fontSize = 26.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = CoralPrimary
                        )
                        Text(
                            text = "STREAK OFF",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White.copy(alpha = 0.5f),
                            letterSpacing = 0.8.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = if (isStreakOn) "$streakDays of $nextMilestone days to next milestone" else "Enable protection to resume streak counting",
                fontSize = 12.5.sp,
                color = Color.White.copy(alpha = 0.6f)
            )

            Spacer(modifier = Modifier.height(20.dp))

            // 3-Metric Stats Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFF222228))
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                MetricColumn(title = "CURRENT", value = if (isStreakOn) "$streakDays d" else "0 d", color = if (isStreakOn) CoralPrimary else Color.White.copy(alpha = 0.4f))
                Box(modifier = Modifier.height(28.dp).width(1.dp).background(Color.White.copy(alpha = 0.1f)))
                MetricColumn(title = "BEST", value = "$bestStreak d", color = GoldColor)
                Box(modifier = Modifier.height(28.dp).width(1.dp).background(Color.White.copy(alpha = 0.1f)))
                MetricColumn(title = "URGES WON", value = "$urgesPrevented", color = EmeraldGreen)
            }

            Spacer(modifier = Modifier.height(18.dp))

            // 2-Minute Emergency Urge Focus Lockdown CTA
            Button(
                onClick = onSosClick,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = CoralPrimary,
                    contentColor = Color.White
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_sos),
                    contentDescription = "Urge SOS",
                    modifier = Modifier.size(19.dp),
                    tint = Color.White
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "I Feel an Urge (2m Lockdown)",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.5.sp,
                    letterSpacing = 0.3.sp
                )
            }
        }
    }
}

@Composable
private fun MetricColumn(title: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = title,
            fontSize = 10.5.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White.copy(alpha = 0.45f),
            letterSpacing = 0.6.sp
        )
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            text = value,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            color = color
        )
    }
}

@Composable
private fun WeeklyConsistencyCard(
    weeklyStatus: List<DayConsistencyStatus>,
    onOpenMonthlyCalendar: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cleanDaysCount = weeklyStatus.count { it.isPastOrToday && it.isClean }
    val totalPastDays = weeklyStatus.count { it.isPastOrToday }
    val percentage = if (totalPastDays > 0) ((cleanDaysCount.toFloat() / totalPastDays) * 100).toInt() else 100

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onOpenMonthlyCalendar),
        shape = RoundedCornerShape(16.dp),
        color = CardBackground,
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(EmeraldGreen.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_check_circle),
                            contentDescription = null,
                            tint = EmeraldGreen,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Weekly Consistency",
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.5.sp
                            ),
                            color = Color.White
                        )
                        Text(
                            text = "Tap to view monthly calendar 📅",
                            fontSize = 11.sp,
                            color = Color.White.copy(alpha = 0.45f)
                        )
                    }
                }

                // Subtle badge
                Row(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(EmeraldGreen.copy(alpha = 0.12f))
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (percentage == 100) "100% Clean" else "$percentage% Clean",
                        fontSize = 11.sp,
                        color = EmeraldGreen,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                weeklyStatus.forEach { status ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = status.dayLetter,
                            fontSize = 12.sp,
                            fontWeight = if (status.isToday) FontWeight.Bold else FontWeight.Medium,
                            color = if (status.isToday) CoralPrimary else Color.White.copy(alpha = 0.5f)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(
                                    when {
                                        !status.isPastOrToday -> Color(0xFF222228)
                                        status.isClean -> EmeraldGreen.copy(alpha = 0.15f)
                                        else -> CoralPrimary.copy(alpha = 0.15f)
                                    }
                                )
                                .border(
                                    width = if (status.isToday) 1.5.dp else 1.dp,
                                    color = when {
                                        status.isToday -> CoralPrimary
                                        !status.isPastOrToday -> Color.White.copy(alpha = 0.08f)
                                        status.isClean -> EmeraldGreen
                                        else -> CoralPrimary
                                    },
                                    shape = CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            if (status.isPastOrToday) {
                                if (status.isClean) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_check_circle),
                                        contentDescription = null,
                                        tint = EmeraldGreen,
                                        modifier = Modifier.size(18.dp)
                                    )
                                } else {
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_close),
                                        contentDescription = null,
                                        tint = CoralPrimary,
                                        modifier = Modifier.size(15.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MonthlyProgressCalendarDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val now = Calendar.getInstance()
    var selectedYear by remember { mutableIntStateOf(now.get(Calendar.YEAR)) }
    var selectedMonth by remember { mutableIntStateOf(now.get(Calendar.MONTH)) }

    val monthData = remember(selectedYear, selectedMonth) {
        HabitsManager.getMonthCalendar(context, selectedYear, selectedMonth)
    }

    val dayLetters = listOf("M", "T", "W", "T", "F", "S", "S")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = {
                        if (selectedMonth == 0) {
                            selectedMonth = 11
                            selectedYear--
                        } else {
                            selectedMonth--
                        }
                    },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_arrow_back),
                        contentDescription = "Previous Month",
                        tint = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.size(18.dp)
                    )
                }

                Text(
                    text = monthData.monthName,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = Color.White
                )

                IconButton(
                    onClick = {
                        if (selectedMonth == 11) {
                            selectedMonth = 0
                            selectedYear++
                        } else {
                            selectedMonth++
                        }
                    },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_chevron_right),
                        contentDescription = "Next Month",
                        tint = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Day of Week Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    dayLetters.forEach { letter ->
                        Box(
                            modifier = Modifier.width(36.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = letter,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White.copy(alpha = 0.45f)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Calendar Grid: Offset blanks + Days
                val totalCells = monthData.firstDayOfWeekOffset + monthData.days.size
                LazyVerticalGrid(
                    columns = GridCells.Fixed(7),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(230.dp),
                    userScrollEnabled = false,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Empty leading slots for offset
                    items(monthData.firstDayOfWeekOffset) {
                        Box(modifier = Modifier.size(36.dp))
                    }

                    // Actual month days
                    items(monthData.days) { day ->
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(
                                    when {
                                        day.isFuture -> Color.Transparent
                                        day.isRelapse -> CoralPrimary.copy(alpha = 0.2f)
                                        day.isClean -> EmeraldGreen.copy(alpha = 0.18f)
                                        else -> Color(0xFF24242C)
                                    }
                                )
                                .border(
                                    width = if (day.isToday) 1.5.dp else 1.dp,
                                    color = when {
                                        day.isToday -> CoralPrimary
                                        day.isRelapse -> CoralPrimary
                                        day.isClean -> EmeraldGreen
                                        else -> Color.White.copy(alpha = 0.06f)
                                    },
                                    shape = CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "${day.dayNumber}",
                                fontSize = 12.sp,
                                fontWeight = if (day.isToday || day.isClean) FontWeight.Bold else FontWeight.Normal,
                                color = when {
                                    day.isToday -> CoralPrimary
                                    day.isClean -> EmeraldGreen
                                    day.isRelapse -> CoralPrimary
                                    day.isFuture -> Color.White.copy(alpha = 0.2f)
                                    else -> Color.White.copy(alpha = 0.5f)
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Monthly Summary Card
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF22222A),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Clean Days", fontSize = 11.sp, color = Color.White.copy(alpha = 0.5f))
                            Text("${monthData.cleanCount}", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = EmeraldGreen)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Relapses", fontSize = 11.sp, color = Color.White.copy(alpha = 0.5f))
                            Text("${monthData.relapseCount}", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = if (monthData.relapseCount == 0) EmeraldGreen else CoralPrimary)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Consistency", fontSize = 11.sp, color = Color.White.copy(alpha = 0.5f))
                            Text("${monthData.consistencyPercent}%", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = GoldColor)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = Color.White.copy(alpha = 0.8f), fontWeight = FontWeight.Bold)
            }
        },
        containerColor = Color(0xFF1B1B22),
        shape = RoundedCornerShape(18.dp)
    )
}

@Composable
private fun HabitsTrackerSection(
    habits: List<PersistentHabit>,
    onToggleHabit: (PersistentHabit) -> Unit,
    onDeleteHabit: (PersistentHabit) -> Unit,
    onAddHabitClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val completedCount = habits.count { it.isCompletedToday }
    val progress = if (habits.isNotEmpty()) completedCount.toFloat() / habits.size else 0f

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = CardBackground,
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 6.dp)
        ) {
            // Header Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(CoralPrimary.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_target),
                            contentDescription = null,
                            tint = CoralPrimary,
                            modifier = Modifier.size(17.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Daily Habits",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.5.sp
                        ),
                        color = Color.White
                    )
                }

                IconButton(
                    onClick = onAddHabitClick,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_add),
                        contentDescription = "Add Habit",
                        tint = CoralPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            if (habits.isEmpty()) {
                // Empty state when user removes all habits
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_target),
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.3f),
                        modifier = Modifier.size(36.dp)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "No Daily Habits Yet",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Tap + to add positive daily routines that replace compulsive triggers.",
                        fontSize = 12.sp,
                        color = Color.White.copy(alpha = 0.5f),
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                // Progress bar
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "$completedCount of ${habits.size} completed",
                            fontSize = 12.sp,
                            color = Color.White.copy(alpha = 0.6f)
                        )
                        Text(
                            text = "${(progress * 100).toInt()}%",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (progress == 1f) EmeraldGreen else CoralPrimary
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(CircleShape),
                        color = if (progress == 1f) EmeraldGreen else CoralPrimary,
                        trackColor = Color.White.copy(alpha = 0.08f)
                    )
                }

                // Habits list
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                ) {
                    habits.forEachIndexed { index, habit ->
                        HabitRowItem(
                            habit = habit,
                            onToggle = { onToggleHabit(habit) },
                            onDelete = { onDeleteHabit(habit) }
                        )
                        if (index < habits.lastIndex) {
                            HorizontalDivider(color = Color.White.copy(alpha = 0.06f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HabitRowItem(
    habit: PersistentHabit,
    onToggle: () -> Unit,
    onDelete: () -> Unit
) {
    val iconRes = getHabitIconRes(habit.iconKey)
    val iconTint = getHabitIconTint(habit.iconKey)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f).padding(end = 8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(iconTint.copy(alpha = 0.14f))
                    .border(1.dp, iconTint.copy(alpha = 0.25f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(id = iconRes),
                    contentDescription = habit.title,
                    tint = iconTint,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column {
                Text(
                    text = habit.title,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.5.sp,
                    color = if (habit.isCompletedToday) Color.White.copy(alpha = 0.5f) else Color.White,
                    textDecoration = if (habit.isCompletedToday) TextDecoration.LineThrough else TextDecoration.None
                )
                Spacer(modifier = Modifier.height(3.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_fire),
                        contentDescription = null,
                        tint = CoralPrimary,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "${habit.currentStreak} day streak",
                        fontSize = 12.sp,
                        color = CoralPrimary,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            // Delete Action Button
            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_delete),
                    contentDescription = "Delete Habit",
                    tint = Color.White.copy(alpha = 0.3f),
                    modifier = Modifier.size(17.dp)
                )
            }

            Spacer(modifier = Modifier.width(6.dp))

            // Completion Checkmark
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(if (habit.isCompletedToday) EmeraldGreen else Color.Transparent)
                    .border(
                        width = 1.5.dp,
                        color = if (habit.isCompletedToday) EmeraldGreen else Color.White.copy(alpha = 0.3f),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (habit.isCompletedToday) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_check_circle),
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun AddHabitDialog(
    onDismiss: () -> Unit,
    onConfirmAdd: (title: String, iconKey: String, targetDays: Int) -> Unit
) {
    var newTitle by remember { mutableStateOf("") }

    val iconOptions = remember {
        listOf(
            SelectableHabitIcon(R.drawable.ic_habit_water, Color(0xFF4FC3F7), "Water", "water"),
            SelectableHabitIcon(R.drawable.ic_habit_fitness, CoralPrimary, "Fitness", "fitness"),
            SelectableHabitIcon(R.drawable.ic_habit_book, Color(0xFFFFB74D), "Read", "book"),
            SelectableHabitIcon(R.drawable.ic_habit_meditation, Color(0xFF64B5F6), "Meditate", "meditation"),
            SelectableHabitIcon(R.drawable.ic_habit_journal, Color(0xFF81C784), "Journal", "journal"),
            SelectableHabitIcon(R.drawable.ic_habit_walking, Color(0xFFFFD54F), "Walk", "walking"),
            SelectableHabitIcon(R.drawable.ic_habit_noscreen, Color(0xFFBA68C8), "Detox", "noscreen"),
            SelectableHabitIcon(R.drawable.ic_target, Color(0xFFFF8A65), "Focus", "target"),
            SelectableHabitIcon(R.drawable.ic_feature_shield, Color(0xFF26A69A), "Shield", "shield"),
            SelectableHabitIcon(R.drawable.ic_feature_bolt, Color(0xFFFFCA28), "Energy", "bolt"),
            SelectableHabitIcon(R.drawable.ic_lightbulb, Color(0xFFFF7043), "Rest", "lightbulb"),
            SelectableHabitIcon(R.drawable.ic_trophy, GoldColor, "Goal", "trophy"),
            SelectableHabitIcon(R.drawable.ic_fire, CoralPrimary, "Discipline", "fire")
        )
    }
    var selectedIcon by remember { mutableStateOf(iconOptions[0]) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "New Habit",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = Color.White
            )
        },
        text = {
            Column {
                Text(
                    text = "Choose category icon:",
                    fontSize = 13.sp,
                    color = Color.White.copy(alpha = 0.7f)
                )
                Spacer(modifier = Modifier.height(10.dp))

                // Scrollable/wrap grid of icons
                LazyVerticalGrid(
                    columns = GridCells.Fixed(5),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(110.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(iconOptions) { option ->
                        val isSelected = (selectedIcon.key == option.key)
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isSelected) option.tint.copy(alpha = 0.25f) else Color(0xFF26262E))
                                .border(
                                    width = if (isSelected) 1.5.dp else 1.dp,
                                    color = if (isSelected) option.tint else Color.White.copy(alpha = 0.08f),
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .clickable { selectedIcon = option },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter = painterResource(id = option.iconRes),
                                contentDescription = option.label,
                                tint = if (isSelected) option.tint else Color.White.copy(alpha = 0.5f),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = newTitle,
                    onValueChange = { newTitle = it },
                    placeholder = { Text("e.g. Read 15 mins daily", color = Color.White.copy(alpha = 0.35f)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CoralPrimary,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.25f),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    shape = RoundedCornerShape(10.dp)
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (newTitle.isNotBlank()) {
                        onConfirmAdd(newTitle.trim(), selectedIcon.key, 30)
                    }
                },
                colors = ButtonDefaults.textButtonColors(contentColor = CoralPrimary)
            ) {
                Text("Add", fontWeight = FontWeight.Bold)
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
private fun MilestonesSection(
    currentStreak: Int,
    milestones: List<MilestoneBadge>,
    modifier: Modifier = Modifier
) {
    val unlockedCount = milestones.count { currentStreak >= it.targetDays }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = CardBackground,
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 6.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(GoldColor.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_trophy),
                            contentDescription = null,
                            tint = GoldColor,
                            modifier = Modifier.size(17.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Milestones & Badges",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.5.sp
                        ),
                        color = Color.White
                    )
                }

                Row(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(GoldColor.copy(alpha = 0.12f))
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "$unlockedCount of ${milestones.size} Unlocked",
                        fontSize = 11.5.sp,
                        color = GoldColor,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(milestones) { badge ->
                    val isUnlocked = currentStreak >= badge.targetDays
                    MilestoneCardItem(badge = badge, isUnlocked = isUnlocked)
                }
            }
        }
    }
}

@Composable
private fun MilestoneCardItem(badge: MilestoneBadge, isUnlocked: Boolean) {
    Column(
        modifier = Modifier
            .width(108.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (isUnlocked) Color(0xFF24242C) else Color(0xFF1D1D22))
            .border(
                width = 1.dp,
                color = if (isUnlocked) badge.badgeColor.copy(alpha = 0.5f) else Color.White.copy(alpha = 0.06f),
                shape = RoundedCornerShape(14.dp)
            )
            .padding(vertical = 12.dp, horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(if (isUnlocked) badge.badgeColor.copy(alpha = 0.16f) else Color(0xFF26262E)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(id = badge.iconRes),
                contentDescription = badge.title,
                tint = if (isUnlocked) badge.badgeColor else Color.White.copy(alpha = 0.3f),
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(modifier = Modifier.height(7.dp))
        Text(
            text = badge.title,
            fontWeight = FontWeight.Bold,
            fontSize = 12.5.sp,
            color = if (isUnlocked) badge.badgeColor else Color.White.copy(alpha = 0.45f),
            textAlign = TextAlign.Center,
            maxLines = 1
        )
        Text(
            text = badge.subtitle,
            fontSize = 10.sp,
            color = Color.White.copy(alpha = 0.45f),
            textAlign = TextAlign.Center,
            maxLines = 1,
            softWrap = false
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = if (isUnlocked) "UNLOCKED" else "LOCKED",
            fontSize = 9.sp,
            fontWeight = FontWeight.ExtraBold,
            color = if (isUnlocked) EmeraldGreen else Color.White.copy(alpha = 0.3f),
            letterSpacing = 0.5.sp
        )
    }
}

@Composable
private fun DailyMindsetCard(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = CardBackground,
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_lightbulb),
                    contentDescription = null,
                    tint = GoldColor,
                    modifier = Modifier.size(17.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "DAILY STOIC REMINDER",
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    color = CoralPrimary,
                    letterSpacing = 0.8.sp
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "“He who conquers himself is mightier than he who conquers a city.”",
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    fontWeight = FontWeight.Medium
                ),
                color = Color.White.copy(alpha = 0.9f)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "— Marcus Aurelius",
                fontSize = 12.sp,
                color = Color.White.copy(alpha = 0.45f)
            )
        }
    }
}

@Composable
private fun UrgeInterventionModal(
    incompleteHabit: PersistentHabit?,
    onDismiss: () -> Unit,
    onUrgeOvercome: () -> Unit
) {
    // 2-minute (120s) urge focus lockdown timer
    var timerSeconds by remember { mutableIntStateOf(120) }
    var isRunning by remember { mutableStateOf(true) }
    val haptic = LocalHapticFeedback.current

    LaunchedEffect(isRunning) {
        while (isRunning && timerSeconds > 0) {
            delay(1000L)
            timerSeconds--
        }
    }

    // 16s Box Breathing cycle
    val cycleTime = (120 - timerSeconds) % 16
    val (phaseText, targetScale) = when (cycleTime) {
        in 0..3 -> "Inhale deeply (4s)" to 1.25f
        in 4..7 -> "Hold your breath (4s)" to 1.25f
        in 8..11 -> "Exhale slowly (4s)" to 0.85f
        else -> "Rest & center (4s)" to 0.85f
    }

    val animatedScale by animateFloatAsState(
        targetValue = targetScale,
        animationSpec = tween(durationMillis = 3500),
        label = "breathingScale"
    )

    val minutes = timerSeconds / 60
    val seconds = timerSeconds % 60
    val formattedTime = String.format("%d:%02d", minutes, seconds)

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
                            painter = painterResource(id = R.drawable.ic_feature_shield),
                            contentDescription = null,
                            tint = CoralPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "2-Min Urge Lockdown",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = Color.White
                        )
                        Text(
                            text = "Focus Mode Active",
                            fontSize = 11.5.sp,
                            color = CoralPrimary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
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
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Distracting apps are locked down just like Focus Mode for the next 2 minutes. Ride the urge wave until it breaks.",
                    fontSize = 12.5.sp,
                    color = Color.White.copy(alpha = 0.75f),
                    textAlign = TextAlign.Center,
                    lineHeight = 17.sp
                )

                Spacer(modifier = Modifier.height(18.dp))

                // Pulsating breathing circle
                Box(
                    modifier = Modifier.size(130.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(110.dp)
                            .graphicsLayer {
                                scaleX = animatedScale
                                scaleY = animatedScale
                            }
                            .clip(CircleShape)
                            .background(CoralPrimary.copy(alpha = 0.12f))
                            .border(2.dp, CoralPrimary, CircleShape)
                    )
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = formattedTime,
                            fontSize = 28.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White
                        )
                        Text(
                            text = phaseText,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = CoralPrimary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Instant Habit Redirect
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF26262E),
                    border = BorderStroke(1.dp, CoralPrimary.copy(alpha = 0.35f))
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(CoralPrimary.copy(alpha = 0.18f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_feature_bolt),
                                contentDescription = null,
                                tint = CoralPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "INSTANT HABIT REDIRECT",
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.5.sp,
                                color = CoralPrimary,
                                letterSpacing = 0.6.sp
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            val habitMsg = if (incompleteHabit != null) {
                                "You haven't done ${incompleteHabit.title} yet today. Go do that right now!"
                            } else {
                                "Drink a cold glass of water or do 10 pushups right now to shift focus."
                            }
                            Text(
                                text = habitMsg,
                                fontSize = 12.sp,
                                color = Color.White.copy(alpha = 0.9f),
                                lineHeight = 16.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Stoic reminder snippet
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF1B1B20),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.06f))
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(
                            text = "“He who conquers himself is mightier than he who conquers a city.”",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color.White.copy(alpha = 0.8f)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "— Marcus Aurelius",
                            fontSize = 10.5.sp,
                            color = Color.White.copy(alpha = 0.45f)
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onUrgeOvercome()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = EmeraldGreen,
                    contentColor = Color.White
                )
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_check_circle),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "I Survived It (+1 Urge Won)",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.5.sp
                )
            }
        },
        containerColor = Color(0xFF1E1E24),
        shape = RoundedCornerShape(18.dp)
    )
}

@Composable
private fun ReflectiveRelapseDialog(
    onDismiss: () -> Unit,
    onConfirmReset: (trigger: String, note: String) -> Unit
) {
    val triggers = listOf("Stress", "Late Night", "Boredom", "Social Media", "Loneliness", "Exhaustion", "Other")
    var selectedTrigger by remember { mutableStateOf(triggers[0]) }
    var reflectionNote by remember { mutableStateOf("") }

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
                        painter = painterResource(id = R.drawable.ic_fire),
                        contentDescription = null,
                        tint = CoralPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "Reset & Reflect",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = Color.White
                    )
                    Text(
                        text = "Turn a stumble into wisdom",
                        fontSize = 12.sp,
                        color = Color.White.copy(alpha = 0.5f)
                    )
                }
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "What triggered this lapse?",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.5.sp,
                    color = Color.White.copy(alpha = 0.9f)
                )
                Spacer(modifier = Modifier.height(8.dp))

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    triggers.chunked(3).forEach { rowTriggers ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            rowTriggers.forEach { trigger ->
                                val isSelected = (selectedTrigger == trigger)
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(
                                            if (isSelected) CoralPrimary.copy(alpha = 0.2f)
                                            else Color(0xFF26262E)
                                        )
                                        .border(
                                            1.dp,
                                            if (isSelected) CoralPrimary else Color.White.copy(alpha = 0.1f),
                                            RoundedCornerShape(8.dp)
                                        )
                                        .clickable { selectedTrigger = trigger }
                                        .padding(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        text = trigger,
                                        fontSize = 12.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) CoralPrimary else Color.White.copy(alpha = 0.7f)
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "What will you do next time? (optional)",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.5.sp,
                    color = Color.White.copy(alpha = 0.9f)
                )
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedTextField(
                    value = reflectionNote,
                    onValueChange = { reflectionNote = it },
                    placeholder = {
                        Text(
                            "e.g. Keep phone outside the bedroom",
                            fontSize = 12.5.sp,
                            color = Color.White.copy(alpha = 0.35f)
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 3,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CoralPrimary,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.2f),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    shape = RoundedCornerShape(10.dp)
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirmReset(selectedTrigger, reflectionNote) },
                colors = ButtonDefaults.textButtonColors(contentColor = CoralPrimary)
            ) {
                Text("Log Lapse & Restart", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Stay Strong (Cancel)", color = Color.White.copy(alpha = 0.7f))
            }
        },
        containerColor = Color(0xFF1E1E24),
        shape = RoundedCornerShape(16.dp)
    )
}

@Preview(showBackground = true, backgroundColor = 0xFF121214)
@Composable
private fun HabitsScreenPreview() {
    PGuardTheme(darkTheme = true) {
        HabitsScreen()
    }
}
