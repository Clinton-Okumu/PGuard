package com.example.pguard.presentation.habits

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.example.pguard.R
import com.example.pguard.presentation.focus.FocusModeManager
import com.example.pguard.presentation.theme.pguardColors
import kotlinx.coroutines.delay

@Composable
internal fun UrgeInterventionModal(
    incompleteHabit: PersistentHabit?,
    isAccessibilityRunning: Boolean = true,
    onOpenAccessibilitySettings: () -> Unit = {},
    onDismiss: () -> Unit,
    onUrgeOvercome: () -> Unit
) {
    val context = LocalContext.current
    val hadLockdown = remember { FocusModeManager.isUrgeLockdownActive(context) }
    var timerSeconds by remember {
        mutableIntStateOf(
            FocusModeManager.getUrgeLockdownRemainingSeconds(context).let { if (it > 0) it else 120 }
        )
    }
    var confirmUnlockCountdown by remember { mutableIntStateOf(-1) }
    val isRunning = true
    val haptic = LocalHapticFeedback.current

    BackHandler(enabled = true) {
        // Enforce phone lockdown during 2-minute urge protocol
    }

    LaunchedEffect(isRunning) {
        while (timerSeconds > 0) {
            delay(1000L)
            timerSeconds = if (hadLockdown) {
                FocusModeManager.getUrgeLockdownRemainingSeconds(context)
            } else {
                (timerSeconds - 1).coerceAtLeast(0)
            }
        }
    }

    LaunchedEffect(confirmUnlockCountdown) {
        if (confirmUnlockCountdown > 0) {
            delay(1000L)
            confirmUnlockCountdown--
        }
    }

    val cycleTime = (120 - timerSeconds).coerceAtLeast(0) % 16
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

    val primary = MaterialTheme.colorScheme.primary
    val successColor = MaterialTheme.pguardColors.success
    val activeAccent = if (timerSeconds > 0) primary else successColor

    AlertDialog(
        onDismissRequest = {
            if (timerSeconds <= 0) {
                onDismiss()
            }
        },
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false
        ),
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
                            .background(primary.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_feature_lock),
                            contentDescription = "2-Min Lockdown",
                            tint = primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Phone Locked (2 Min)",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (isAccessibilityRunning) "Urge Protocol • Focus Active" else "Breathing Protocol • App Lock OFF",
                            style = MaterialTheme.typography.labelSmall,
                            color = primary
                        )
                    }
                }
                if (timerSeconds <= 0) {
                    IconButton(onClick = onDismiss) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_close),
                            contentDescription = "Close",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (!isAccessibilityRunning && timerSeconds > 0) {
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = primary.copy(alpha = 0.12f),
                        border = BorderStroke(1.dp, primary.copy(alpha = 0.35f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.small)
                            .clickable(onClick = onOpenAccessibilitySettings)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
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
                                text = "Accessibility Service is OFF — other apps won't be locked until enabled.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Enable",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = primary
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                }

                Text(
                    text = when {
                        timerSeconds <= 0 -> "You survived the peak urge wave! Your dopamine receptors are resting. Claim your victory below."
                        isAccessibilityRunning -> "Phone is locked down from use for 2 minutes just like Focus Mode. Non-essential apps and notifications are blocked."
                        else -> "Breathe through this 2-minute urge wave. Enable Accessibility Service to lock other apps automatically."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(18.dp))

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
                            .background(activeAccent.copy(alpha = 0.14f))
                            .border(2.dp, activeAccent, CircleShape)
                    )
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            painter = painterResource(id = if (timerSeconds > 0) R.drawable.ic_feature_lock else R.drawable.ic_check_circle),
                            contentDescription = "Lockdown State",
                            tint = activeAccent,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = formattedTime,
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (timerSeconds > 0) phaseText else "Wave Defeated!",
                            style = MaterialTheme.typography.labelSmall,
                            color = activeAccent
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    border = BorderStroke(1.dp, primary.copy(alpha = 0.35f))
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(MaterialTheme.shapes.extraSmall)
                                .background(primary.copy(alpha = 0.18f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_feature_bolt),
                                contentDescription = null,
                                tint = primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "INSTANT HABIT REDIRECT",
                                style = MaterialTheme.typography.labelSmall,
                                color = primary
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            val habitMsg = if (incompleteHabit != null) {
                                "You haven't done ${incompleteHabit.title} yet today. Go do that right now!"
                            } else {
                                "Drink a cold glass of water or do 10 pushups right now to shift focus."
                            }
                            Text(
                                text = habitMsg,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(
                            text = "“He who conquers himself is mightier than he who conquers a city.”",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "— Marcus Aurelius",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        },
        confirmButton = {
            if (timerSeconds <= 0) {
                Button(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onUrgeOvercome()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp),
                    shape = MaterialTheme.shapes.small,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = successColor,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_check_circle),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "I Survived It (+1 Urge Won) 🏆",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            } else {
                Button(
                    onClick = {},
                    enabled = false,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp),
                    shape = MaterialTheme.shapes.small,
                    colors = ButtonDefaults.buttonColors(
                        disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_feature_shield),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isAccessibilityRunning) "Phone Locked ($formattedTime)" else "Hold Steady ($formattedTime)",
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        },
        dismissButton = {
            if (timerSeconds > 0) {
                if (confirmUnlockCountdown < 0) {
                    TextButton(
                        onClick = { confirmUnlockCountdown = 5 },
                        modifier = Modifier.padding(top = 2.dp)
                    ) {
                        Text(
                            text = "Emergency Unlock",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = { confirmUnlockCountdown = -1 }) {
                            Text(
                                text = "Stay Strong",
                                style = MaterialTheme.typography.labelMedium,
                                color = successColor,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        TextButton(
                            onClick = onDismiss,
                            enabled = confirmUnlockCountdown == 0
                        ) {
                            Text(
                                text = if (confirmUnlockCountdown > 0) "Wait ${confirmUnlockCountdown}s..." else "Unlock Now",
                                style = MaterialTheme.typography.labelMedium,
                                color = if (confirmUnlockCountdown == 0) primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.large
    )
}

@Composable
internal fun ReflectiveRelapseDialog(
    onDismiss: () -> Unit,
    onConfirmReset: (trigger: String, note: String) -> Unit
) {
    val triggers = listOf("Stress", "Late Night", "Boredom", "Social Media", "Loneliness", "Exhaustion", "Other")
    var selectedTrigger by remember { mutableStateOf(triggers[0]) }
    var reflectionNote by remember { mutableStateOf("") }
    val primary = MaterialTheme.colorScheme.primary

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(primary.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_fire),
                        contentDescription = null,
                        tint = primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "Reset & Reflect",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Turn a stumble into wisdom",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "What triggered this lapse?",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
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
                                        .clip(MaterialTheme.shapes.extraSmall)
                                        .background(
                                            if (isSelected) primary.copy(alpha = 0.2f)
                                            else MaterialTheme.colorScheme.surfaceContainerHigh
                                        )
                                        .border(
                                            1.dp,
                                            if (isSelected) primary else MaterialTheme.colorScheme.outlineVariant,
                                            MaterialTheme.shapes.extraSmall
                                        )
                                        .clickable { selectedTrigger = trigger }
                                        .padding(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        text = trigger,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) primary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "What will you do next time? (optional)",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedTextField(
                    value = reflectionNote,
                    onValueChange = {
                        if (it.length <= HabitsManager.MAX_RELAPSE_NOTE_LENGTH) {
                            reflectionNote = it
                        }
                    },
                    placeholder = {
                        Text(
                            "e.g. Keep phone outside the bedroom",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    supportingText = {
                        Text(
                            text = "${reflectionNote.length}/${HabitsManager.MAX_RELAPSE_NOTE_LENGTH}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 3,
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
                onClick = { onConfirmReset(selectedTrigger, reflectionNote) },
                colors = ButtonDefaults.textButtonColors(contentColor = primary)
            ) {
                Text("Log Lapse & Restart", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Stay Strong (Cancel)", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.medium
    )
}
