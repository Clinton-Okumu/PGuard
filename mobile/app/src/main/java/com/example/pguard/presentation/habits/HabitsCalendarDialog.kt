package com.example.pguard.presentation.habits

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.pguard.R
import com.example.pguard.presentation.theme.pguardColors
import java.util.Calendar

@Composable
internal fun MonthlyProgressCalendarDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val now = Calendar.getInstance()
    var selectedYear by remember { mutableIntStateOf(now.get(Calendar.YEAR)) }
    var selectedMonth by remember { mutableIntStateOf(now.get(Calendar.MONTH)) }
    var syncRevision by remember { mutableIntStateOf(0) }

    LaunchedEffect(selectedYear, selectedMonth) {
        HabitsManager.syncCalendarWithBackend(context)
        syncRevision++
    }

    val monthData = remember(selectedYear, selectedMonth, syncRevision) {
        HabitsManager.getMonthCalendar(context, selectedYear, selectedMonth)
    }

    val dayLetters = listOf("M", "T", "W", "T", "F", "S", "S")
    val primary = MaterialTheme.colorScheme.primary
    val extendedColors = MaterialTheme.pguardColors

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
                    }
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_arrow_back),
                        contentDescription = "Previous Month",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Text(
                    text = monthData.monthName,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )

                IconButton(
                    onClick = {
                        if (selectedMonth == 11) {
                            selectedMonth = 0
                            selectedYear++
                        } else {
                            selectedMonth++
                        }
                    }
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_chevron_right),
                        contentDescription = "Next Month",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
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
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                LazyVerticalGrid(
                    columns = GridCells.Fixed(7),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(230.dp),
                    userScrollEnabled = false,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(monthData.firstDayOfWeekOffset) {
                        Box(modifier = Modifier.size(36.dp))
                    }

                    items(monthData.days) { day ->
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(
                                    when {
                                        day.isFuture -> Color.Transparent
                                        !day.isWithinStreak -> MaterialTheme.colorScheme.surfaceContainerLow
                                        day.isRelapse -> primary.copy(alpha = 0.2f)
                                        day.isClean -> extendedColors.success.copy(alpha = 0.18f)
                                        else -> MaterialTheme.colorScheme.surfaceContainerHigh
                                    }
                                )
                                .border(
                                    width = if (day.isToday) 1.5.dp else 1.dp,
                                    color = when {
                                        day.isToday -> primary
                                        !day.isWithinStreak -> MaterialTheme.colorScheme.outlineVariant
                                        day.isRelapse -> primary
                                        day.isClean -> extendedColors.success
                                        else -> MaterialTheme.colorScheme.outlineVariant
                                    },
                                    shape = CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "${day.dayNumber}",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = if (day.isToday || day.isClean) FontWeight.Bold else FontWeight.Normal,
                                color = when {
                                    day.isToday -> primary
                                    !day.isWithinStreak -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                                    day.isClean -> extendedColors.success
                                    day.isRelapse -> primary
                                    day.isFuture -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Clean Days", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("${monthData.cleanCount}", style = MaterialTheme.typography.titleMedium, color = extendedColors.success)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Relapses", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                "${monthData.relapseCount}",
                                style = MaterialTheme.typography.titleMedium,
                                color = if (monthData.relapseCount == 0) extendedColors.success else primary
                            )
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("Consistency", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("${monthData.consistencyPercent}%", style = MaterialTheme.typography.titleMedium, color = extendedColors.gold)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "● Green = Clean  ● Red = Relapse  ○ Faint = Pre-streak",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
            }
        },
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.large
    )
}
