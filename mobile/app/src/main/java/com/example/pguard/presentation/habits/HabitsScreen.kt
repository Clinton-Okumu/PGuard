package com.example.pguard.presentation.habits

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.pguard.R
import com.example.pguard.presentation.theme.CoralPrimary
import com.example.pguard.presentation.theme.PGuardTheme
import kotlinx.coroutines.delay

private val CardBackground = Color(0xFF18181C)
private val GoldColor = Color(0xFFFFB800)
private val EmeraldGreen = Color(0xFF4CAF50)

data class HabitItem(
    val id: String,
    val title: String,
    val iconRes: Int,
    val iconTint: Color = CoralPrimary,
    val targetDays: Int = 21,
    var currentStreak: Int = 5,
    var isCompletedToday: Boolean = false
)

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
    val label: String
)

@Composable
fun HabitsScreen(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    // Blocker-connected streak and habit state from HabitsManager
    var streakDays by remember { mutableIntStateOf(HabitsManager.getStreakDays(context)) }
    var bestStreak by remember { mutableIntStateOf(HabitsManager.getBestStreakDays(context)) }
    var urgesPrevented by remember { mutableIntStateOf(HabitsManager.getUrgesPrevented(context)) }
    var weeklyConsistency by remember { mutableStateOf(HabitsManager.getWeeklyConsistency(context)) }

    // Synchronize on recomposition/entry
    LaunchedEffect(Unit) {
        streakDays = HabitsManager.getStreakDays(context)
        bestStreak = HabitsManager.getBestStreakDays(context)
        urgesPrevented = HabitsManager.getUrgesPrevented(context)
        weeklyConsistency = HabitsManager.getWeeklyConsistency(context)
    }

    // Modal and dialog states
    var showSosIntervention by remember { mutableStateOf(false) }
    var showRelapseDialog by remember { mutableStateOf(false) }
    var showAddHabitDialog by remember { mutableStateOf(false) }

    // Habits collection with Material vector icons & tailored theme colors
    val habits = remember {
        mutableStateListOf(
            HabitItem("1", "Morning Meditation", R.drawable.ic_habit_meditation, Color(0xFF64B5F6), 30, 7, true),
            HabitItem("2", "Read Self-Development", R.drawable.ic_habit_book, Color(0xFFFFB74D), 30, 6, true),
            HabitItem("3", "Workout / Exercise", R.drawable.ic_habit_fitness, CoralPrimary, 21, 4, false),
            HabitItem("4", "Drink 2.5L Water", R.drawable.ic_habit_water, Color(0xFF4FC3F7), 21, 5, false),
            HabitItem("5", "No Screens 1h Before Bed", R.drawable.ic_habit_noscreen, Color(0xFFBA68C8), 30, 3, false)
        )
    }

    // Milestones with Material vector icons
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

            // 1. Hero Clean Streak Card (Reset moved to overflow menu, SOS button gets full prominent width)
            CleanStreakHeroCard(
                streakDays = streakDays,
                bestStreak = bestStreak,
                urgesPrevented = urgesPrevented,
                onSosClick = { showSosIntervention = true },
                onResetClick = { showRelapseDialog = true },
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            Spacer(modifier = Modifier.height(18.dp))

            // 2. Weekly Consistency Row (Connected to real blockers and relapse tracking)
            WeeklyConsistencyCard(
                weeklyStatus = weeklyConsistency,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            Spacer(modifier = Modifier.height(18.dp))

            // 3. Daily Habits Tracker Card (Unified card header)
            HabitsTrackerSection(
                habits = habits,
                onToggleHabit = { habit ->
                    val index = habits.indexOfFirst { it.id == habit.id }
                    if (index != -1) {
                        val item = habits[index]
                        val newState = !item.isCompletedToday
                        val newStreak = if (newState) item.currentStreak + 1 else maxOf(0, item.currentStreak - 1)
                        habits[index] = item.copy(isCompletedToday = newState, currentStreak = newStreak)
                    }
                },
                onAddHabitClick = { showAddHabitDialog = true },
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            Spacer(modifier = Modifier.height(18.dp))

            // 4. Milestone Badges Section (Unified card header & fixed label truncation)
            MilestonesSection(
                currentStreak = streakDays,
                milestones = milestones,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            Spacer(modifier = Modifier.height(18.dp))

            // 5. Daily Stoic Mindset Card
            DailyMindsetCard(modifier = Modifier.padding(horizontal = 16.dp))
        }
    }

    // Interactive Urge Intervention Modal (Box breathing timer, instant habit redirect, stoic quote & haptic feedback)
    if (showSosIntervention) {
        val incompleteHabit = habits.firstOrNull { !it.isCompletedToday }
        UrgeInterventionModal(
            incompleteHabit = incompleteHabit,
            onDismiss = { showSosIntervention = false },
            onUrgeOvercome = {
                HabitsManager.recordUrgeSurvived(context)
                urgesPrevented = HabitsManager.getUrgesPrevented(context)
                showSosIntervention = false
            }
        )
    }

    // 2-Step Reflective Relapse Modal (Friction + Trigger Identification + Optional Reflection)
    if (showRelapseDialog) {
        ReflectiveRelapseDialog(
            onDismiss = { showRelapseDialog = false },
            onConfirmReset = { trigger, note ->
                HabitsManager.resetStreak(context, trigger, note)
                streakDays = HabitsManager.getStreakDays(context)
                bestStreak = HabitsManager.getBestStreakDays(context)
                weeklyConsistency = HabitsManager.getWeeklyConsistency(context)
                showRelapseDialog = false
            }
        )
    }

    // Add Habit Dialog with Material Vector Icon Picker
    if (showAddHabitDialog) {
        var newTitle by remember { mutableStateOf("") }

        val iconOptions = remember {
            listOf(
                SelectableHabitIcon(R.drawable.ic_habit_meditation, Color(0xFF64B5F6), "Meditate"),
                SelectableHabitIcon(R.drawable.ic_habit_book, Color(0xFFFFB74D), "Read"),
                SelectableHabitIcon(R.drawable.ic_habit_fitness, CoralPrimary, "Fitness"),
                SelectableHabitIcon(R.drawable.ic_habit_water, Color(0xFF4FC3F7), "Water"),
                SelectableHabitIcon(R.drawable.ic_habit_journal, Color(0xFF81C784), "Journal"),
                SelectableHabitIcon(R.drawable.ic_habit_walking, Color(0xFFFFD54F), "Walk"),
                SelectableHabitIcon(R.drawable.ic_habit_noscreen, Color(0xFFBA68C8), "Detox"),
                SelectableHabitIcon(R.drawable.ic_target, Color(0xFFFF8A65), "Focus")
            )
        }
        var selectedIcon by remember { mutableStateOf(iconOptions[0]) }

        AlertDialog(
            onDismissRequest = { showAddHabitDialog = false },
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
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        iconOptions.take(4).forEach { option ->
                            HabitIconChoice(
                                iconOption = option,
                                isSelected = (selectedIcon == option),
                                onSelect = { selectedIcon = option }
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        iconOptions.drop(4).take(4).forEach { option ->
                            HabitIconChoice(
                                iconOption = option,
                                isSelected = (selectedIcon == option),
                                onSelect = { selectedIcon = option }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedTextField(
                        value = newTitle,
                        onValueChange = { newTitle = it },
                        placeholder = { Text("e.g. Morning stretch", color = Color.White.copy(alpha = 0.4f)) },
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
                            habits.add(
                                HabitItem(
                                    id = System.currentTimeMillis().toString(),
                                    title = newTitle.trim(),
                                    iconRes = selectedIcon.iconRes,
                                    iconTint = selectedIcon.tint,
                                    currentStreak = 0,
                                    isCompletedToday = false
                                )
                            )
                        }
                        showAddHabitDialog = false
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = CoralPrimary)
                ) {
                    Text("Add", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddHabitDialog = false }) {
                    Text("Cancel", color = Color.White.copy(alpha = 0.7f))
                }
            },
            containerColor = Color(0xFF1E1E24),
            shape = RoundedCornerShape(16.dp)
        )
    }
}

@Composable
private fun HabitIconChoice(
    iconOption: SelectableHabitIcon,
    isSelected: Boolean,
    onSelect: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(42.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (isSelected) iconOption.tint.copy(alpha = 0.2f) else Color(0xFF26262E))
            .border(
                width = if (isSelected) 1.5.dp else 1.dp,
                color = if (isSelected) iconOption.tint else Color.White.copy(alpha = 0.08f),
                shape = RoundedCornerShape(12.dp)
            )
            .clickable(onClick = onSelect),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(id = iconOption.iconRes),
            contentDescription = iconOption.label,
            tint = if (isSelected) iconOption.tint else Color.White.copy(alpha = 0.6f),
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun CleanStreakHeroCard(
    streakDays: Int,
    bestStreak: Int,
    urgesPrevented: Int,
    onSosClick: () -> Unit,
    onResetClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = CardBackground,
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header Row: Centered Tag + Top-Right Overflow Menu for Reset Friction
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                // Centered Tag
                Row(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(Color(0xFF26262E))
                        .border(1.dp, CoralPrimary.copy(alpha = 0.5f), CircleShape)
                        .padding(horizontal = 14.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_fire),
                        contentDescription = null,
                        tint = CoralPrimary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "CLEAN & FREE STREAK",
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.5.sp,
                        color = CoralPrimary,
                        letterSpacing = 0.8.sp
                    )
                }

                // Top-Right Overflow Menu (Places Reset behind friction)
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

            Spacer(modifier = Modifier.height(20.dp))

            // Large Circular Streak Gauge
            val nextMilestone = if (streakDays < 14) 14 else if (streakDays < 30) 30 else 90
            val progress = (streakDays.toFloat() / nextMilestone.toFloat()).coerceIn(0.05f, 1f)

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
                    color = CoralPrimary,
                    strokeWidth = 10.dp,
                    strokeCap = StrokeCap.Round
                )

                // Inner content
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
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
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "$streakDays of $nextMilestone days to next milestone",
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
                MetricColumn(title = "CURRENT", value = "$streakDays d", color = CoralPrimary)
                Box(modifier = Modifier.height(28.dp).width(1.dp).background(Color.White.copy(alpha = 0.1f)))
                MetricColumn(title = "BEST", value = "$bestStreak d", color = GoldColor)
                Box(modifier = Modifier.height(28.dp).width(1.dp).background(Color.White.copy(alpha = 0.1f)))
                MetricColumn(title = "URGES WON", value = "$urgesPrevented", color = EmeraldGreen)
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Full-Width Urgent SOS Button (Safe from accidental reset taps)
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
                    text = "I Feel an Urge (SOS)",
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
    modifier: Modifier = Modifier
) {
    val cleanDaysCount = weeklyStatus.count { it.isPastOrToday && it.isClean }
    val totalPastDays = weeklyStatus.count { it.isPastOrToday }
    val percentage = if (totalPastDays > 0) ((cleanDaysCount.toFloat() / totalPastDays) * 100).toInt() else 100

    Surface(
        modifier = modifier.fillMaxWidth(),
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
                    Text(
                        text = "Weekly Consistency",
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.5.sp
                        ),
                        color = Color.White
                    )
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
                        text = if (percentage == 100) "0 bypass attempts • $percentage%" else "$percentage% Clean",
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
private fun HabitsTrackerSection(
    habits: List<HabitItem>,
    onToggleHabit: (HabitItem) -> Unit,
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
            // Sleek Unified Header matching WeeklyConsistencyCard
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
                        onToggle = { onToggleHabit(habit) }
                    )
                    if (index < habits.lastIndex) {
                        HorizontalDivider(color = Color.White.copy(alpha = 0.06f))
                    }
                }
            }
        }
    }
}

@Composable
private fun HabitRowItem(
    habit: HabitItem,
    onToggle: () -> Unit
) {
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
            modifier = Modifier
                .weight(1f)
                .padding(end = 10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(habit.iconTint.copy(alpha = 0.14f))
                    .border(1.dp, habit.iconTint.copy(alpha = 0.25f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(id = habit.iconRes),
                    contentDescription = habit.title,
                    tint = habit.iconTint,
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

        // Circular completion check
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
            // Sleek Unified Header matching WeeklyConsistencyCard
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
    incompleteHabit: HabitItem?,
    onDismiss: () -> Unit,
    onUrgeOvercome: () -> Unit
) {
    var timerSeconds by remember { mutableIntStateOf(60) }
    var isRunning by remember { mutableStateOf(true) }
    val haptic = LocalHapticFeedback.current

    LaunchedEffect(isRunning) {
        while (isRunning && timerSeconds > 0) {
            delay(1000L)
            timerSeconds--
        }
    }

    // 16s Box Breathing cycle:
    // 0..3: Inhale (scale up to 1.25)
    // 4..7: Hold (hold scale 1.25)
    // 8..11: Exhale (scale down to 0.85)
    // 12..15: Rest (hold scale 0.85)
    val cycleTime = (60 - timerSeconds) % 16
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
                            painter = painterResource(id = R.drawable.ic_sos),
                            contentDescription = null,
                            tint = CoralPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Urge Protocol (60s)",
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
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Urges peak like an ocean wave for ~60 seconds before receding. Breathe through the crest.",
                    fontSize = 12.5.sp,
                    color = Color.White.copy(alpha = 0.7f),
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
                            text = "${timerSeconds}s",
                            fontSize = 32.sp,
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
                                "Drink a tall glass of cold water or do 10 pushups right now to shift focus."
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
