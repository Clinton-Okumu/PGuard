package com.example.pguard.presentation.habits

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.pguard.R
import com.example.pguard.presentation.theme.pguardColors

@Composable
internal fun CleanStreakHeroCard(
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

    val primary = MaterialTheme.colorScheme.primary
    val extendedColors = MaterialTheme.pguardColors
    val badgeAccent = if (isStreakOn) extendedColors.success else primary

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(
            width = 1.dp,
            color = if (isStreakOn) MaterialTheme.colorScheme.outlineVariant else primary.copy(alpha = 0.4f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(badgeAccent.copy(alpha = 0.15f))
                        .border(1.dp, badgeAccent.copy(alpha = 0.6f), CircleShape)
                        .padding(horizontal = 14.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        painter = painterResource(id = if (isStreakOn) R.drawable.ic_fire else R.drawable.ic_feature_shield),
                        contentDescription = null,
                        tint = badgeAccent,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isStreakOn) "STREAK ACTIVE • PROTECTED" else "STREAK OFF • UNPROTECTED",
                        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.8.sp),
                        color = badgeAccent
                    )
                }

                Box(modifier = Modifier.align(Alignment.CenterEnd)) {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_more_vert),
                            contentDescription = "Options",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false },
                        modifier = Modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    ) {
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = "Reset Streak...",
                                    color = primary,
                                    style = MaterialTheme.typography.labelLarge
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

            if (!isStreakOn && streakStatus.inactiveReason != null) {
                Spacer(modifier = Modifier.height(14.dp))
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = primary.copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, primary.copy(alpha = 0.3f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_feature_shield),
                            contentDescription = null,
                            tint = primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = streakStatus.inactiveReason,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            val milestoneTargets = listOf(1, 3, 7, 14, 30, 60, 90, 180, 270, 365)
            val nextMilestone = milestoneTargets.firstOrNull { it > streakDays } ?: 365
            val progress = if (isStreakOn) (streakDays.toFloat() / nextMilestone.toFloat()).coerceIn(0.05f, 1f) else 0f

            Box(
                modifier = Modifier.size(160.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    progress = { 1f },
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.outlineVariant,
                    strokeWidth = 10.dp,
                    strokeCap = StrokeCap.Round
                )
                CircularProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxSize(),
                    color = if (isStreakOn) primary else MaterialTheme.colorScheme.outline,
                    strokeWidth = 10.dp,
                    strokeCap = StrokeCap.Round
                )

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if (isStreakOn) {
                        Text(
                            text = "$streakDays",
                            style = MaterialTheme.typography.displayMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (streakDays == 1) "DAY FREE" else "DAYS FREE",
                            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.sp),
                            color = primary
                        )
                    } else {
                        Text(
                            text = "PAUSED",
                            style = MaterialTheme.typography.headlineMedium,
                            color = primary
                        )
                        Text(
                            text = "STREAK OFF",
                            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.8.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = if (isStreakOn) "$streakDays of $nextMilestone days to next milestone" else "Enable protection to resume streak counting",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(20.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.small)
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                MetricColumn(
                    title = "CURRENT",
                    value = if (isStreakOn) "$streakDays d" else "0 d",
                    color = if (isStreakOn) primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Box(
                    modifier = Modifier
                        .height(28.dp)
                        .width(1.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant)
                )
                MetricColumn(title = "BEST", value = "$bestStreak d", color = extendedColors.gold)
                Box(
                    modifier = Modifier
                        .height(28.dp)
                        .width(1.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant)
                )
                MetricColumn(title = "URGES WON", value = "$urgesPrevented", color = extendedColors.success)
            }

            Spacer(modifier = Modifier.height(18.dp))

            Button(
                onClick = onSosClick,
                shape = MaterialTheme.shapes.small,
                colors = ButtonDefaults.buttonColors(
                    containerColor = primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_feature_lock),
                    contentDescription = "2-Min Urge Lockdown",
                    modifier = Modifier.size(19.dp),
                    tint = MaterialTheme.colorScheme.onPrimary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "I Feel an Urge (2m Lockdown)",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
internal fun MetricColumn(title: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            color = color
        )
    }
}

@Composable
internal fun WeeklyConsistencyCard(
    weeklyStatus: List<DayConsistencyStatus>,
    onOpenMonthlyCalendar: () -> Unit,
    modifier: Modifier = Modifier
) {
    val trackedPastDays = weeklyStatus.filter { it.isTracked && it.isPastOrToday }
    val cleanDaysCount = trackedPastDays.count { it.isClean }
    val percentage = if (trackedPastDays.isNotEmpty()) ((cleanDaysCount.toFloat() / trackedPastDays.size) * 100).toInt() else 100

    val primary = MaterialTheme.colorScheme.primary
    val successColor = MaterialTheme.pguardColors.success

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onOpenMonthlyCalendar),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
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
                            .clip(MaterialTheme.shapes.extraSmall)
                            .background(successColor.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_check_circle),
                            contentDescription = null,
                            tint = successColor,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Weekly Consistency",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Linked to streak • Tap for Calendar 📅",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(successColor.copy(alpha = 0.12f))
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (trackedPastDays.isEmpty()) "Tracking Starts Today" else if (percentage == 100) "100% Clean" else "$percentage% Clean",
                        style = MaterialTheme.typography.labelSmall,
                        color = successColor
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
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (status.isToday) FontWeight.Bold else FontWeight.Medium,
                            color = if (status.isToday) {
                                primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (status.isTracked) 1f else 0.45f)
                            }
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(
                                    when {
                                        !status.isTracked || !status.isPastOrToday -> MaterialTheme.colorScheme.surfaceContainer
                                        status.isClean -> successColor.copy(alpha = 0.15f)
                                        else -> primary.copy(alpha = 0.15f)
                                    }
                                )
                                .border(
                                    width = if (status.isToday) 1.5.dp else 1.dp,
                                    color = when {
                                        status.isToday -> primary
                                        !status.isTracked || !status.isPastOrToday -> MaterialTheme.colorScheme.outlineVariant
                                        status.isClean -> successColor
                                        else -> primary
                                    },
                                    shape = CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            if (status.isTracked && status.isPastOrToday) {
                                if (status.isClean) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_check_circle),
                                        contentDescription = null,
                                        tint = successColor,
                                        modifier = Modifier.size(18.dp)
                                    )
                                } else {
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_close),
                                        contentDescription = null,
                                        tint = primary,
                                        modifier = Modifier.size(15.dp)
                                    )
                                }
                            } else if (!status.isTracked && status.isPastOrToday) {
                                Box(
                                    modifier = Modifier
                                        .size(4.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.outline)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun DailyMindsetCard(
    onCardClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val stoic = remember { HabitsManager.getDailyStoicQuote() }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onCardClick),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_lightbulb),
                        contentDescription = null,
                        tint = MaterialTheme.pguardColors.gold,
                        modifier = Modifier.size(17.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "DAILY STOIC REMINDER",
                        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.8.sp),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Text(
                    text = "Tap to read 📖",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stoic.quote,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "— ${stoic.author}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
