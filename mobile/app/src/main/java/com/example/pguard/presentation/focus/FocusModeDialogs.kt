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
        mutableStateOf(
            initialSchedule?.repeatDays ?: allDays
        )
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
                    .padding(22.dp)
            ) {
                // Header
                Text(
                    text = if (initialSchedule == null) "New Focus Schedule" else "Edit Focus Schedule",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                Spacer(modifier = Modifier.height(18.dp))

                // Unified Time Selector Card
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = Color.White.copy(alpha = 0.05f),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Start Time
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
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White.copy(alpha = 0.5f),
                                    letterSpacing = 0.5.sp
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = String.format("%02d:%02d", startHour, startMinute),
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }

                            // Direction Arrow
                            Icon(
                                painter = painterResource(id = R.drawable.ic_chevron_right),
                                contentDescription = null,
                                tint = Color.White.copy(alpha = 0.35f),
                                modifier = Modifier
                                    .size(24.dp)
                                    .padding(horizontal = 4.dp)
                            )

                            // End Time
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
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White.copy(alpha = 0.5f),
                                    letterSpacing = 0.5.sp
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = String.format("%02d:%02d", endHour, endMinute),
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        HorizontalDivider(color = Color.White.copy(alpha = 0.06f))
                        Spacer(modifier = Modifier.height(8.dp))

                        // Duration text
                        Text(
                            text = "${tempSchedule.formattedDuration} focus window",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = CoralPrimary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Repeat Days Section
                Text(
                    text = "REPEAT",
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White.copy(alpha = 0.5f),
                    letterSpacing = 0.5.sp
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
                                color = if (isSelected) Color.White else Color.White.copy(alpha = 0.55f),
                                fontSize = 13.5.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Quick Presets: Everyday / Weekdays / Weekends
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Everyday",
                        fontSize = 11.5.sp,
                        color = if (selectedDays.size >= 7) CoralPrimary else Color.White.copy(alpha = 0.5f),
                        fontWeight = if (selectedDays.size >= 7) FontWeight.Bold else FontWeight.Normal,
                        modifier = Modifier
                            .clickable { selectedDays = allDays }
                            .padding(vertical = 4.dp, horizontal = 2.dp)
                    )
                    Text("•", color = Color.White.copy(alpha = 0.25f), fontSize = 11.5.sp, modifier = Modifier.align(Alignment.CenterVertically))
                    Text(
                        text = "Weekdays",
                        fontSize = 11.5.sp,
                        color = if (selectedDays == weekdays) CoralPrimary else Color.White.copy(alpha = 0.5f),
                        fontWeight = if (selectedDays == weekdays) FontWeight.Bold else FontWeight.Normal,
                        modifier = Modifier
                            .clickable { selectedDays = weekdays }
                            .padding(vertical = 4.dp, horizontal = 2.dp)
                    )
                    Text("•", color = Color.White.copy(alpha = 0.25f), fontSize = 11.5.sp, modifier = Modifier.align(Alignment.CenterVertically))
                    Text(
                        text = "Weekends",
                        fontSize = 11.5.sp,
                        color = if (selectedDays == weekends) CoralPrimary else Color.White.copy(alpha = 0.5f),
                        fontWeight = if (selectedDays == weekends) FontWeight.Bold else FontWeight.Normal,
                        modifier = Modifier
                            .clickable { selectedDays = weekends }
                            .padding(vertical = 4.dp, horizontal = 2.dp)
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Allowed Apps Row
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color.White.copy(alpha = 0.05f),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
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
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(CoralPrimary.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_feature_lock),
                                    contentDescription = null,
                                    tint = CoralPrimary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }

                            Column {
                                Text(
                                    text = if (selectedAllowedApps.isEmpty()) "Allowed Apps (0)" else "Allowed Apps (${selectedAllowedApps.size})",
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "Calls, Messages & Contacts allowed by default",
                                    color = Color.White.copy(alpha = 0.55f),
                                    fontSize = 11.5.sp
                                )
                            }
                        }

                        Icon(
                            painter = painterResource(id = R.drawable.ic_chevron_right),
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.45f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

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
                    Spacer(modifier = Modifier.width(10.dp))
                    Button(
                        onClick = {
                            onSaveSchedule(tempSchedule)
                            onDismiss()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = CoralPrimary),
                        shape = RoundedCornerShape(14.dp),
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp)
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
