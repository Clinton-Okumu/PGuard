package com.example.pguard.presentation.focus

import android.app.TimePickerDialog
import java.util.Calendar
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.pguard.R

@Composable
fun FocusHelpDialog(onDismiss: () -> Unit) {
    val primary = MaterialTheme.colorScheme.primary

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainer,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(22.dp)
            ) {
                Text(
                    text = "What is Schedule?",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "If you want to block all apps except few selected apps for a particular time period, then you can schedule a focus time.\nExample: 4:00 PM - 5:00 PM.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(18.dp))

                Text(
                    text = "What is Allowed Apps?",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "By default only 'Call/SMS' apps are allowed during active focus time, but this option used to allow apps you want to access during active focus time.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(24.dp))

                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.CenterEnd
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        border = BorderStroke(1.2.dp, primary),
                        shape = CircleShape,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)
                    ) {
                        Text(
                            text = "Got it",
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun FocusAccountabilityGuardDialog(
    partnerModeTitle: String = "Team",
    onDismiss: () -> Unit
) {
    val primary = MaterialTheme.colorScheme.primary

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainer,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "To disable or reset this option, please contact our support team, as you have chosen '$partnerModeTitle' as your accountability partner.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.88f),
                    textAlign = TextAlign.Start
                )

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = primary),
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    Text(
                        text = "Okay",
                        color = MaterialTheme.colorScheme.onPrimary,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun FreeLimitPillToast(
    onDismiss: () -> Unit,
    onUpgradeClick: () -> Unit = {}
) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shadowElevation = 8.dp,
        modifier = Modifier
            .fillMaxWidth(0.92f)
            .clickable {
                onUpgradeClick()
                onDismiss()
            }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(MaterialTheme.shapes.extraSmall)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_pguard_globe_shield),
                    contentDescription = null,
                    modifier = Modifier.size(22.dp)
                )
            }

            Text(
                text = "Only one focus time allowed in the free version.",
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
fun CreateScheduleDialog(
    initialSchedule: FocusSchedule? = null,
    onDismiss: () -> Unit,
    onPickAllowedAppsClick: (Set<String>) -> Unit,
    selectedAllowedApps: Set<String>,
    onSaveSchedule: (FocusSchedule) -> Unit
) {
    val context = LocalContext.current
    val nowCal = remember { Calendar.getInstance() }
    val currentHour = nowCal.get(Calendar.HOUR_OF_DAY)
    val currentMinute = nowCal.get(Calendar.MINUTE)
    val defaultEndHour = (currentHour + 1) % 24

    var startHour by remember { mutableIntStateOf(initialSchedule?.startHour ?: currentHour) }
    var startMinute by remember { mutableIntStateOf(initialSchedule?.startMinute ?: currentMinute) }
    var endHour by remember { mutableIntStateOf(initialSchedule?.endHour ?: defaultEndHour) }
    var endMinute by remember { mutableIntStateOf(initialSchedule?.endMinute ?: currentMinute) }

    val allDays = remember {
        setOf(
            Calendar.MONDAY,
            Calendar.TUESDAY,
            Calendar.WEDNESDAY,
            Calendar.THURSDAY,
            Calendar.FRIDAY,
            Calendar.SATURDAY,
            Calendar.SUNDAY
        )
    }
    val weekdays = remember {
        setOf(
            Calendar.MONDAY,
            Calendar.TUESDAY,
            Calendar.WEDNESDAY,
            Calendar.THURSDAY,
            Calendar.FRIDAY
        )
    }
    val weekends = remember {
        setOf(
            Calendar.SATURDAY,
            Calendar.SUNDAY
        )
    }

    var selectedDays by remember {
        mutableStateOf(initialSchedule?.repeatDays ?: allDays)
    }

    fun showTimePicker(initialH: Int, initialM: Int, onTimeSet: (Int, Int) -> Unit) {
        TimePickerDialog(context, { _, h, m -> onTimeSet(h, m) }, initialH, initialM, true).show()
    }

    val tempSchedule = remember(startHour, startMinute, endHour, endMinute, selectedDays, selectedAllowedApps) {
        FocusSchedule(
            id = initialSchedule?.id ?: java.util.UUID.randomUUID().toString(),
            startHour = startHour,
            startMinute = startMinute,
            endHour = endHour,
            endMinute = endMinute,
            repeatDaily = selectedDays.size >= 7,
            repeatDays = selectedDays,
            allowedApps = selectedAllowedApps
        )
    }

    val primary = MaterialTheme.colorScheme.primary

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainer,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(22.dp)
            ) {
                Text(
                    text = if (initialSchedule == null) "New Focus Schedule" else "Edit Focus Schedule",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(18.dp))

                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable {
                                        showTimePicker(startHour, startMinute) { h, m ->
                                            startHour = h
                                            startMinute = m
                                        }
                                    }
                            ) {
                                Text(
                                    text = "START",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = String.format("%02d:%02d", startHour, startMinute),
                                    style = MaterialTheme.typography.headlineSmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }

                            Icon(
                                painter = painterResource(id = R.drawable.ic_chevron_right),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .size(24.dp)
                                    .padding(horizontal = 4.dp)
                            )

                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable {
                                        showTimePicker(endHour, endMinute) { h, m ->
                                            endHour = h
                                            endMinute = m
                                        }
                                    },
                                horizontalAlignment = Alignment.End
                            ) {
                                Text(
                                    text = "END",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = String.format("%02d:%02d", endHour, endMinute),
                                    style = MaterialTheme.typography.headlineSmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "${tempSchedule.formattedDuration} focus window",
                            style = MaterialTheme.typography.labelMedium,
                            color = primary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                Text(
                    text = "REPEAT",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(10.dp))

                val daysList = remember {
                    listOf(
                        Triple(Calendar.MONDAY, "M", "Mon"),
                        Triple(Calendar.TUESDAY, "T", "Tue"),
                        Triple(Calendar.WEDNESDAY, "W", "Wed"),
                        Triple(Calendar.THURSDAY, "T", "Thu"),
                        Triple(Calendar.FRIDAY, "F", "Fri"),
                        Triple(Calendar.SATURDAY, "S", "Sat"),
                        Triple(Calendar.SUNDAY, "S", "Sun")
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    for (dayItem in daysList) {
                        val dayInt = dayItem.first
                        val letter = dayItem.second
                        val isSelected = dayInt in selectedDays
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isSelected) primary else MaterialTheme.colorScheme.surfaceContainerHigh
                                )
                                .clickable {
                                    selectedDays = if (isSelected) {
                                        if (selectedDays.size > 1) selectedDays - dayInt else selectedDays
                                    } else {
                                        selectedDays + dayInt
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = letter,
                                color = if (isSelected) {
                                    MaterialTheme.colorScheme.onPrimary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Everyday",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (selectedDays.size >= 7) primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = if (selectedDays.size >= 7) FontWeight.Bold else FontWeight.Normal,
                        modifier = Modifier
                            .clickable { selectedDays = allDays }
                            .padding(vertical = 4.dp, horizontal = 2.dp)
                    )
                    Text(
                        text = "•",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.align(Alignment.CenterVertically)
                    )
                    Text(
                        text = "Weekdays",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (selectedDays == weekdays) primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = if (selectedDays == weekdays) FontWeight.Bold else FontWeight.Normal,
                        modifier = Modifier
                            .clickable { selectedDays = weekdays }
                            .padding(vertical = 4.dp, horizontal = 2.dp)
                    )
                    Text(
                        text = "•",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.align(Alignment.CenterVertically)
                    )
                    Text(
                        text = "Weekends",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (selectedDays == weekends) primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = if (selectedDays == weekends) FontWeight.Bold else FontWeight.Normal,
                        modifier = Modifier
                            .clickable { selectedDays = weekends }
                            .padding(vertical = 4.dp, horizontal = 2.dp)
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))

                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onPickAllowedAppsClick(selectedAllowedApps) }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(MaterialTheme.shapes.extraSmall)
                                    .background(primary.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_feature_lock),
                                    contentDescription = null,
                                    tint = primary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }

                            Column {
                                Text(
                                    text = if (selectedAllowedApps.isEmpty()) "Allowed Apps (0)" else "Allowed Apps (${selectedAllowedApps.size})",
                                    color = MaterialTheme.colorScheme.onSurface,
                                    style = MaterialTheme.typography.titleSmall
                                )
                                Text(
                                    text = "Calls, Messages & Contacts allowed by default",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }

                        Icon(
                            painter = painterResource(id = R.drawable.ic_chevron_right),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = onDismiss,
                        shape = MaterialTheme.shapes.small
                    ) {
                        Text("Cancel", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Button(
                        onClick = {
                            onSaveSchedule(tempSchedule)
                            onDismiss()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = primary),
                        shape = MaterialTheme.shapes.small,
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp)
                    ) {
                        Text(
                            text = "Save Schedule",
                            color = MaterialTheme.colorScheme.onPrimary,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
