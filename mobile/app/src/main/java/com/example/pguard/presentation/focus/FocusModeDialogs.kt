package com.example.pguard.presentation.focus

import android.app.TimePickerDialog
import android.content.Context
import java.util.Calendar
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.pguard.R
import com.example.pguard.presentation.theme.CoralPrimary

private val DialogBackground = Color(0xFF1E1E24)

/**
 * Help modal matching Image 1: Explains Schedule and Allowed Apps.
 */
@Composable
fun FocusHelpDialog(onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = DialogBackground,
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(22.dp)
            ) {
                Text(
                    text = "What is Schedule?",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "If you want to block all apps except few selected apps for a particular time period, then you can schedule a focus time.\nExample: 4:00 PM - 5:00 PM.",
                    fontSize = 13.sp,
                    color = Color.White.copy(alpha = 0.75f),
                    lineHeight = 18.sp
                )

                Spacer(modifier = Modifier.height(18.dp))

                Text(
                    text = "What is Allowed Apps?",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "By default only 'Call/SMS' apps are allowed during active focus time, but this option used to allow apps you want to access during active focus time.",
                    fontSize = 13.sp,
                    color = Color.White.copy(alpha = 0.75f),
                    lineHeight = 18.sp
                )

                Spacer(modifier = Modifier.height(24.dp))

                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.CenterEnd
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        border = BorderStroke(1.2.dp, CoralPrimary),
                        shape = RoundedCornerShape(20.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                    ) {
                        Text(
                            text = "Got it",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }
    }
}

/**
 * Accountability Guard Dialog matching Image 3:
 * Appears when user attempts to disable or delete an active schedule under accountability partner lock.
 */
@Composable
fun FocusAccountabilityGuardDialog(
    partnerModeTitle: String = "Team",
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = DialogBackground,
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
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
                    fontSize = 14.5.sp,
                    color = Color.White.copy(alpha = 0.85f),
                    lineHeight = 21.sp,
                    textAlign = TextAlign.Start
                )

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = CoralPrimary),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    Text(
                        text = "Okay",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                }
            }
        }
    }
}

/**
 * Free Tier Limit Toast Pill matching Image 2:
 * "Only one focus time allowed in the free version."
 */
@Composable
fun FreeLimitPillToast(
    onDismiss: () -> Unit,
    onUpgradeClick: () -> Unit = {}
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = DialogBackground,
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
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
                    .clip(RoundedCornerShape(8.dp))
                    .background(CoralPrimary),
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
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/**
 * Dialog to configure and save a new Focus Schedule.
 */
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
    var repeatDaily by remember { mutableStateOf(initialSchedule?.repeatDaily ?: true) }
    var selectedDays by remember {
        mutableStateOf(
            initialSchedule?.repeatDays ?: setOf(
                Calendar.MONDAY,
                Calendar.TUESDAY,
                Calendar.WEDNESDAY,
                Calendar.THURSDAY,
                Calendar.FRIDAY,
                Calendar.SATURDAY,
                Calendar.SUNDAY
            )
        )
    }

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

    fun showTimePicker(initialH: Int, initialM: Int, onTimeSet: (Int, Int) -> Unit) {
        TimePickerDialog(context, { _, h, m -> onTimeSet(h, m) }, initialH, initialM, true).show()
    }

    val tempSchedule = remember(startHour, startMinute, endHour, endMinute, repeatDaily, selectedDays, selectedAllowedApps) {
        FocusSchedule(
            id = initialSchedule?.id ?: java.util.UUID.randomUUID().toString(),
            startHour = startHour,
            startMinute = startMinute,
            endHour = endHour,
            endMinute = endMinute,
            repeatDaily = repeatDaily,
            repeatDays = if (repeatDaily) allDays else selectedDays,
            allowedApps = selectedAllowedApps
        )
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = DialogBackground,
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = if (initialSchedule == null) "Create Focus Schedule" else "Edit Focus Schedule",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Set daily focus windows to silence app distractions.",
                    fontSize = 12.5.sp,
                    color = Color.White.copy(alpha = 0.6f)
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Time Pickers Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    TimePickerChip(
                        label = "START TIME",
                        time = String.format("%02d:%02d", startHour, startMinute),
                        modifier = Modifier.weight(1f),
                        onClick = {
                            showTimePicker(startHour, startMinute) { h, m ->
                                startHour = h
                                startMinute = m
                            }
                        }
                    )

                    TimePickerChip(
                        label = "END TIME",
                        time = String.format("%02d:%02d", endHour, endMinute),
                        modifier = Modifier.weight(1f),
                        onClick = {
                            showTimePicker(endHour, endMinute) { h, m ->
                                endHour = h
                                endMinute = m
                            }
                        }
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Duration indicator Pill
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = CoralPrimary.copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, CoralPrimary.copy(alpha = 0.25f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_focus_mode),
                            contentDescription = null,
                            tint = CoralPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Total Focus Time: ${tempSchedule.formattedDuration}",
                            color = CoralPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))
                HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
                Spacer(modifier = Modifier.height(16.dp))

                // Repeat Daily Switch
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Repeat Everyday",
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (repeatDaily) "Applies every day automatically" else "Pick custom scheduled days below",
                            color = Color.White.copy(alpha = 0.6f),
                            fontSize = 12.sp
                        )
                    }

                    Switch(
                        checked = repeatDaily,
                        onCheckedChange = { isDaily ->
                            repeatDaily = isDaily
                            if (isDaily) {
                                selectedDays = allDays
                            }
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = Color(0xFF4CAF50),
                            uncheckedThumbColor = Color.White,
                            uncheckedTrackColor = Color.White.copy(alpha = 0.2f)
                        )
                    )
                }

                // Custom Scheduled Days (Calendar Weekday Selector)
                if (!repeatDaily) {
                    Spacer(modifier = Modifier.height(14.dp))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color.White.copy(alpha = 0.04f))
                            .padding(14.dp)
                    ) {
                        Text(
                            text = "Scheduled Days",
                            color = Color.White.copy(alpha = 0.85f),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(12.dp))

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
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(if (isSelected) CoralPrimary else Color.White.copy(alpha = 0.08f))
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
                                        color = if (isSelected) Color.White else Color.White.copy(alpha = 0.5f),
                                        fontSize = 13.5.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Quick Presets: Weekdays / Weekends
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (selectedDays == weekdays) CoralPrimary.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.06f))
                                    .clickable { selectedDays = weekdays }
                                    .padding(horizontal = 12.dp, vertical = 7.dp)
                            ) {
                                Text(
                                    text = "Weekdays (Mon-Fri)",
                                    fontSize = 12.sp,
                                    color = if (selectedDays == weekdays) CoralPrimary else Color.White.copy(alpha = 0.7f),
                                    fontWeight = FontWeight.Medium
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (selectedDays == weekends) CoralPrimary.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.06f))
                                    .clickable { selectedDays = weekends }
                                    .padding(horizontal = 12.dp, vertical = 7.dp)
                            ) {
                                Text(
                                    text = "Weekends (Sat-Sun)",
                                    fontSize = 12.sp,
                                    color = if (selectedDays == weekends) CoralPrimary else Color.White.copy(alpha = 0.7f),
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Allowed Apps Selection
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.05f)),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onPickAllowedAppsClick(selectedAllowedApps) }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Allowed Apps (${selectedAllowedApps.size})",
                                color = Color.White,
                                fontSize = 14.5.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Messages, Calls, Contacts & PGuard are default allowed.",
                                color = Color.White.copy(alpha = 0.6f),
                                fontSize = 11.5.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_feature_lock),
                                    contentDescription = null,
                                    tint = CoralPrimary,
                                    modifier = Modifier.size(13.dp)
                                )
                                Text(
                                    text = "Allowed apps are locked once schedule is saved.",
                                    color = CoralPrimary.copy(alpha = 0.9f),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }

                        Icon(
                            painter = painterResource(id = R.drawable.ic_chevron_right),
                            contentDescription = null,
                            tint = CoralPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(26.dp))

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Cancel", color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp)
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Button(
                        onClick = {
                            onSaveSchedule(tempSchedule)
                            onDismiss()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = CoralPrimary),
                        shape = RoundedCornerShape(14.dp),
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp)
                    ) {
                        Text(
                            text = "Save Schedule",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TimePickerChip(
    label: String,
    time: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.05f)),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)),
        modifier = modifier.clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Text(
                text = label,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White.copy(alpha = 0.5f),
                letterSpacing = 0.5.sp
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = time,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }
    }
}
