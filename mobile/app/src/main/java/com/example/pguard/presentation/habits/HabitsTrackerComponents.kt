package com.example.pguard.presentation.habits

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.pguard.R
import com.example.pguard.presentation.theme.CoralPrimary
import com.example.pguard.presentation.theme.PremiumGold
import com.example.pguard.presentation.theme.pguardColors

data class MilestoneBadge(
    val targetDays: Int,
    val title: String,
    val subtitle: String,
    val iconRes: Int,
    val badgeColor: Color = PremiumGold
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
    "trophy" -> PremiumGold
    "fire" -> CoralPrimary
    else -> CoralPrimary
}

@Composable
internal fun HabitsTrackerSection(
    habits: List<PersistentHabit>,
    onToggleHabit: (PersistentHabit) -> Unit,
    onDeleteHabit: (PersistentHabit) -> Unit,
    onAddHabitClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val completedCount = habits.count { it.isCompletedToday }
    val progress = if (habits.isNotEmpty()) completedCount.toFloat() / habits.size else 0f
    val primary = MaterialTheme.colorScheme.primary
    val successColor = MaterialTheme.pguardColors.success

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 6.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(MaterialTheme.shapes.extraSmall)
                            .background(primary.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_target),
                            contentDescription = null,
                            tint = primary,
                            modifier = Modifier.size(17.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Daily Habits",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                IconButton(onClick = onAddHabitClick) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_add),
                        contentDescription = "Add Habit",
                        tint = primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            if (habits.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_target),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(36.dp)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "No Daily Habits Yet",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Tap + to add positive daily routines that replace compulsive triggers.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
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
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "${(progress * 100).toInt()}%",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (progress == 1f) successColor else primary
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(CircleShape),
                        color = if (progress == 1f) successColor else primary,
                        trackColor = MaterialTheme.colorScheme.surfaceContainerHigh
                    )
                }

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
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
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
    val primary = MaterialTheme.colorScheme.primary
    val successColor = MaterialTheme.pguardColors.success

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .weight(1f)
                .padding(end = 8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(MaterialTheme.shapes.small)
                    .background(iconTint.copy(alpha = 0.14f))
                    .border(1.dp, iconTint.copy(alpha = 0.25f), MaterialTheme.shapes.small),
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
                    style = MaterialTheme.typography.titleSmall,
                    color = if (habit.isCompletedToday) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    textDecoration = if (habit.isCompletedToday) TextDecoration.LineThrough else TextDecoration.None
                )
                Spacer(modifier = Modifier.height(3.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_fire),
                        contentDescription = null,
                        tint = primary,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "${habit.currentStreak} day streak",
                        style = MaterialTheme.typography.bodySmall,
                        color = primary,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onDelete) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_delete),
                    contentDescription = "Delete Habit",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(17.dp)
                )
            }

            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(if (habit.isCompletedToday) successColor else Color.Transparent)
                    .border(
                        width = 1.5.dp,
                        color = if (habit.isCompletedToday) successColor else MaterialTheme.colorScheme.outline,
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (habit.isCompletedToday) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_check_circle),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
internal fun AddHabitDialog(
    onDismiss: () -> Unit,
    onConfirmAdd: (title: String, iconKey: String, targetDays: Int) -> Unit
) {
    var newTitle by remember { mutableStateOf("") }
    val primary = MaterialTheme.colorScheme.primary
    val gold = MaterialTheme.pguardColors.gold

    val iconOptions = remember(primary, gold) {
        listOf(
            SelectableHabitIcon(R.drawable.ic_habit_water, Color(0xFF4FC3F7), "Water", "water"),
            SelectableHabitIcon(R.drawable.ic_habit_fitness, primary, "Fitness", "fitness"),
            SelectableHabitIcon(R.drawable.ic_habit_book, Color(0xFFFFB74D), "Read", "book"),
            SelectableHabitIcon(R.drawable.ic_habit_meditation, Color(0xFF64B5F6), "Meditate", "meditation"),
            SelectableHabitIcon(R.drawable.ic_habit_journal, Color(0xFF81C784), "Journal", "journal"),
            SelectableHabitIcon(R.drawable.ic_habit_walking, Color(0xFFFFD54F), "Walk", "walking"),
            SelectableHabitIcon(R.drawable.ic_habit_noscreen, Color(0xFFBA68C8), "Detox", "noscreen"),
            SelectableHabitIcon(R.drawable.ic_target, Color(0xFFFF8A65), "Focus", "target"),
            SelectableHabitIcon(R.drawable.ic_feature_shield, Color(0xFF26A69A), "Shield", "shield"),
            SelectableHabitIcon(R.drawable.ic_feature_bolt, Color(0xFFFFCA28), "Energy", "bolt"),
            SelectableHabitIcon(R.drawable.ic_lightbulb, Color(0xFFFF7043), "Rest", "lightbulb"),
            SelectableHabitIcon(R.drawable.ic_trophy, gold, "Goal", "trophy"),
            SelectableHabitIcon(R.drawable.ic_fire, primary, "Discipline", "fire")
        )
    }
    var selectedIcon by remember { mutableStateOf(iconOptions[0]) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "New Habit",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        },
        text = {
            Column {
                Text(
                    text = "Choose category icon:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(10.dp))

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
                                .clip(MaterialTheme.shapes.small)
                                .background(
                                    if (isSelected) option.tint.copy(alpha = 0.25f)
                                    else MaterialTheme.colorScheme.surfaceContainerHigh
                                )
                                .border(
                                    width = if (isSelected) 1.5.dp else 1.dp,
                                    color = if (isSelected) option.tint else MaterialTheme.colorScheme.outlineVariant,
                                    shape = MaterialTheme.shapes.small
                                )
                                .clickable { selectedIcon = option },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter = painterResource(id = option.iconRes),
                                contentDescription = option.label,
                                tint = if (isSelected) option.tint else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = newTitle,
                    onValueChange = { newTitle = it },
                    placeholder = {
                        Text(
                            "e.g. Read 15 mins daily",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                    ),
                    shape = MaterialTheme.shapes.small
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
                colors = ButtonDefaults.textButtonColors(contentColor = primary)
            ) {
                Text("Add", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.medium
    )
}

@Composable
internal fun MilestonesSection(
    currentStreak: Int,
    milestones: List<MilestoneBadge>,
    onBadgeClick: (MilestoneBadge) -> Unit,
    modifier: Modifier = Modifier
) {
    val unlockedCount = milestones.count { currentStreak >= it.targetDays }
    val gold = MaterialTheme.pguardColors.gold

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
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
                            .clip(MaterialTheme.shapes.extraSmall)
                            .background(gold.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_trophy),
                            contentDescription = null,
                            tint = gold,
                            modifier = Modifier.size(17.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Milestones & Badges",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Row(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(gold.copy(alpha = 0.12f))
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "$unlockedCount of ${milestones.size} Unlocked",
                        style = MaterialTheme.typography.labelSmall,
                        color = gold
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
                    MilestoneCardItem(
                        badge = badge,
                        isUnlocked = isUnlocked,
                        onClick = { onBadgeClick(badge) }
                    )
                }
            }
        }
    }
}

@Composable
private fun MilestoneCardItem(
    badge: MilestoneBadge,
    isUnlocked: Boolean,
    onClick: () -> Unit
) {
    val successColor = MaterialTheme.pguardColors.success

    Column(
        modifier = Modifier
            .width(108.dp)
            .clip(MaterialTheme.shapes.small)
            .background(
                if (isUnlocked) MaterialTheme.colorScheme.surfaceContainerHigh
                else MaterialTheme.colorScheme.surfaceContainer
            )
            .border(
                width = 1.dp,
                color = if (isUnlocked) badge.badgeColor.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outlineVariant,
                shape = MaterialTheme.shapes.small
            )
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(
                    if (isUnlocked) badge.badgeColor.copy(alpha = 0.16f)
                    else MaterialTheme.colorScheme.surfaceContainerHighest
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(id = badge.iconRes),
                contentDescription = badge.title,
                tint = if (isUnlocked) badge.badgeColor else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(modifier = Modifier.height(7.dp))
        Text(
            text = badge.title,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = if (isUnlocked) badge.badgeColor else MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 1
        )
        Text(
            text = badge.subtitle,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 1,
            softWrap = false
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = if (isUnlocked) "UNLOCKED" else "LOCKED",
            style = MaterialTheme.typography.labelSmall,
            color = if (isUnlocked) successColor else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
internal fun MilestoneCelebrationDialog(
    milestone: MilestoneBadge,
    onClaimBadge: () -> Unit
) {
    val scaleAnim by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(durationMillis = 600),
        label = "celebrationScale"
    )
    val gold = MaterialTheme.pguardColors.gold
    val successColor = MaterialTheme.pguardColors.success

    AlertDialog(
        onDismissRequest = onClaimBadge,
        title = null,
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(110.dp)
                        .graphicsLayer {
                            scaleX = scaleAnim
                            scaleY = scaleAnim
                        }
                        .clip(CircleShape)
                        .background(milestone.badgeColor.copy(alpha = 0.18f))
                        .border(3.dp, milestone.badgeColor, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(id = milestone.iconRes),
                        contentDescription = null,
                        tint = milestone.badgeColor,
                        modifier = Modifier.size(54.dp)
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))

                Row(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(gold.copy(alpha = 0.15f))
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "🎉 NEW MILESTONE UNLOCKED!",
                        style = MaterialTheme.typography.labelSmall,
                        color = gold
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "${milestone.title} • ${milestone.subtitle}",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                val description = when (milestone.targetDays) {
                    1 -> "You broke the impulsive loop and proved to yourself you have what it takes. The hardest hurdle is behind you."
                    3 -> "72 hours of sheer discipline! The initial storm has passed, and your dopamine baseline is stabilizing."
                    7 -> "1 full week of freedom! Major neural pathways are beginning to rewire. Feel the clarity."
                    14 -> "2 weeks of unbroken resolve! You are building iron willpower and taking control of your mind."
                    30 -> "1 month clean! A new neurochemical baseline has formed. Compulsive urges have lost their grip."
                    60 -> "60 days of relentless consistency! Your self-discipline has become second nature."
                    90 -> "90 days of complete dopamine reboot! The brain's gray matter has structurally renewed. Pure mental clarity."
                    180 -> "6 months of mastery! Half a year of triumph over impulse. You have built a fortress of character."
                    270 -> "9 months of sustained freedom! Old habits are completely dissolved. You operate on purpose, not impulse."
                    365 -> "365 DAYS • 1 FULL YEAR OF FREEDOM! A monumental, life-defining achievement. You are a living legend."
                    else -> "You achieved a landmark of discipline. Keep forging your fortress of strength."
                }

                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(16.dp))

                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Target: ${milestone.targetDays} Days",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "UNLOCKED 🏆",
                            style = MaterialTheme.typography.labelMedium,
                            color = successColor
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onClaimBadge,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp),
                shape = MaterialTheme.shapes.small,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Text(
                    text = "Claim Badge 🏆",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.large
    )
}

@Composable
internal fun DailyStoicReminderDialog(
    stoicQuote: StoicQuote,
    onDismiss: () -> Unit
) {
    val gold = MaterialTheme.pguardColors.gold
    val primary = MaterialTheme.colorScheme.primary

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(MaterialTheme.shapes.extraSmall)
                        .background(gold.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_lightbulb),
                        contentDescription = null,
                        tint = gold,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "Daily Stoic Anchor",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Mindset of the Day",
                        style = MaterialTheme.typography.labelSmall,
                        color = primary
                    )
                }
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = stoicQuote.quote,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "— ${stoicQuote.author}",
                            style = MaterialTheme.typography.labelLarge,
                            color = gold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "TODAY'S INTENTION",
                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.6.sp),
                    color = primary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stoicQuote.reflection,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp),
                shape = MaterialTheme.shapes.small,
                colors = ButtonDefaults.buttonColors(
                    containerColor = primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Text(
                    text = "Embrace Mindset & Continue",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.large
    )
}
