package com.example.pguard.presentation.dashboard

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.pguard.R
import com.example.pguard.presentation.theme.CoralPrimary
import kotlinx.coroutines.delay

/**
 * Dialog allowing the user to select their active protection guard mode:
 * 1. Friend (Accountability Partner)
 * 2. Myself (Typing Challenge)
 * 3. 24-Hour Time Delay
 */
@Composable
fun AccountabilityModeSelectionDialog(
    currentMode: AccountabilityMode,
    onSelectMode: (AccountabilityMode) -> Unit,
    onDismiss: () -> Unit
) {
    var selected by remember { mutableStateOf(currentMode) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = CardBackground,
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(22.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .background(CoralPrimary.copy(alpha = 0.15f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_partner),
                            contentDescription = null,
                            tint = CoralPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "Accountability Guard",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "Choose friction before turning off blocks",
                            fontSize = 12.sp,
                            color = Color.White.copy(alpha = 0.6f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
                Spacer(modifier = Modifier.height(12.dp))

                val modes = listOf(
                    Triple(
                        AccountabilityMode.NONE,
                        "None (No Restriction)",
                        "No friction. Toggles can be turned off anytime without an accountability check."
                    ),
                    Triple(
                        AccountabilityMode.FRIEND,
                        "Friend (Accountability Partner)",
                        "Invited friend must authorize disabling protection via email code."
                    ),
                    Triple(
                        AccountabilityMode.SELF,
                        "Myself (Typing Challenge)",
                        "Type a randomly generated commitment phrase within 60 seconds."
                    ),
                    Triple(
                        AccountabilityMode.TIME_DELAY,
                        "24-Hour Time Delay",
                        "Disabling features enforces a 24-hour waiting buffer before unlock."
                    )
                )

                modes.forEach { (mode, title, desc) ->
                    val isSelected = selected == mode
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (isSelected) CoralPrimary.copy(alpha = 0.08f) else Color.Transparent)
                            .border(
                                width = 1.dp,
                                color = if (isSelected) CoralPrimary.copy(alpha = 0.5f) else Color.White.copy(alpha = 0.06f),
                                shape = RoundedCornerShape(14.dp)
                            )
                            .clickable { selected = mode }
                            .padding(12.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        RadioButton(
                            selected = isSelected,
                            onClick = { selected = mode },
                            colors = RadioButtonDefaults.colors(
                                selectedColor = CoralPrimary,
                                unselectedColor = Color.White.copy(alpha = 0.4f)
                            ),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = title,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isSelected) Color.White else Color.White.copy(alpha = 0.9f)
                            )
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = desc,
                                fontSize = 11.5.sp,
                                color = Color.White.copy(alpha = 0.55f),
                                lineHeight = 16.sp
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel", color = Color.White.copy(alpha = 0.7f))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            onSelectMode(selected)
                            onDismiss()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = CoralPrimary),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Save Mode", color = Color.White, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

/**
 * Interactive Typing Challenge Dialog for "Myself" mode.
 * Presents an original focus commitment, a 60-second timer, and live validation.
 */
@Composable
fun TypingChallengeDialog(
    targetFeatureTitle: String,
    onSuccess: () -> Unit,
    onDismiss: () -> Unit
) {
    val samplePledges = remember {
        listOf(
            "I choose my long term goals over temporary impulse and take full ownership of this action.",
            "I understand that turning off protection puts my progress at risk and I proceed with awareness.",
            "I am calm, deliberate, and taking full responsibility for changing my digital boundaries today."
        )
    }
    val targetText = remember { samplePledges.random() }
    var inputText by remember { mutableStateOf("") }
    var secondsLeft by remember { mutableIntStateOf(60) }
    var isExpired by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        while (secondsLeft > 0 && !isExpired) {
            delay(1000L)
            secondsLeft -= 1
        }
        if (secondsLeft <= 0) {
            isExpired = true
        }
    }

    val isMatch = inputText.trim() == targetText.trim()

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = CardBackground,
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(22.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Typing Challenge",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = if (isExpired) "Expired" else "${secondsLeft}s",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (secondsLeft <= 10) CoralPrimary else Color.White.copy(alpha = 0.8f)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { secondsLeft / 60f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(CircleShape),
                    color = CoralPrimary,
                    trackColor = Color.White.copy(alpha = 0.1f)
                )

                Spacer(modifier = Modifier.height(14.dp))
                val challengePrompt = when {
                    targetFeatureTitle.startsWith("Change", ignoreCase = true) ->
                        "To change your accountability mode, accurately type the phrase below within 60 seconds:"
                    targetFeatureTitle.startsWith("Remove", ignoreCase = true) ->
                        "To remove your accountability partner, accurately type the phrase below within 60 seconds:"
                    else ->
                        "To turn off $targetFeatureTitle, accurately type the phrase below within 60 seconds:"
                }
                Text(
                    text = challengePrompt,
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.7f)
                )

                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color.White.copy(alpha = 0.05f),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = targetText,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Medium,
                        color = CoralPrimary,
                        modifier = Modifier.padding(12.dp),
                        lineHeight = 19.sp
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = inputText,
                    onValueChange = { if (!isExpired) inputText = it },
                    placeholder = { Text("Type phrase here...", fontSize = 13.sp, color = Color.White.copy(alpha = 0.3f)) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = if (isMatch) Color(0xFF4CAF50) else CoralPrimary,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.15f),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    singleLine = false,
                    maxLines = 4,
                    enabled = !isExpired
                )

                if (isExpired) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Time expired! Protection remains active.",
                        color = CoralPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel", color = Color.White.copy(alpha = 0.7f))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (isMatch && !isExpired) {
                                onSuccess()
                            }
                        },
                        enabled = isMatch && !isExpired,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = CoralPrimary,
                            disabledContainerColor = Color.White.copy(alpha = 0.1f)
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Verify & Unlock", color = Color.White, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

/**
 * Confirmation dialog before initiating a 24-Hour Cooling-Off delay.
 */
@Composable
fun TimeDelayConfirmDialog(
    targetFeatureTitle: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = CardBackground,
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(22.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .background(CoralPrimary.copy(alpha = 0.15f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_focus_mode),
                        contentDescription = null,
                        tint = CoralPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = "24-Hour Cooling-Off Period",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                Spacer(modifier = Modifier.height(8.dp))
                val timeDelayPrompt = when {
                    targetFeatureTitle.startsWith("Change", ignoreCase = true) ->
                        "Changing your accountability mode requires a 24-hour waiting delay to prevent impulsive decisions.\n\nYour current protection settings remain active until the timer expires."
                    targetFeatureTitle.startsWith("Remove", ignoreCase = true) ->
                        "Removing your accountability partner requires a 24-hour waiting delay to prevent impulsive decisions.\n\nYour current partner protection remains active until the timer expires."
                    else ->
                        "Turning off $targetFeatureTitle requires a 24-hour waiting delay to prevent impulsive decisions.\n\nThe feature will remain 100% blocked until the timer expires. You can cancel this unlock request at any time."
                }
                Text(
                    text = timeDelayPrompt,
                    fontSize = 13.sp,
                    color = Color.White.copy(alpha = 0.7f),
                    lineHeight = 18.sp
                )

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Keep Protected", color = Color.White.copy(alpha = 0.7f))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = onConfirm,
                        colors = ButtonDefaults.buttonColors(containerColor = CoralPrimary),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Start 24h Timer", color = Color.White, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

/**
 * Prominent banner showing an active 24-Hour Delay countdown.
 */
@Composable
fun TimeDelayActiveBanner(
    targetFeatureTitle: String,
    unlockAt: Long,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    val now = System.currentTimeMillis()
    val diffMillis = (unlockAt - now).coerceAtLeast(0L)
    val hours = diffMillis / (1000 * 60 * 60)
    val minutes = (diffMillis / (1000 * 60)) % 60
    val isComplete = diffMillis <= 0L

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CoralPrimary.copy(alpha = 0.15f)),
        border = BorderStroke(1.dp, CoralPrimary.copy(alpha = 0.4f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_focus_mode),
                    contentDescription = null,
                    tint = CoralPrimary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = if (isComplete) "Unlock Ready: $targetFeatureTitle" else "Unlock Pending: $targetFeatureTitle",
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (isComplete) "Ready to toggle off" else "Available in ${hours}h ${minutes}m",
                        fontSize = 12.sp,
                        color = Color.White.copy(alpha = 0.75f)
                    )
                }
            }

            OutlinedButton(
                onClick = onCancel,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.3f)),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 2.dp),
                modifier = Modifier.height(30.dp)
            ) {
                Text("Cancel", fontSize = 11.5.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}

/**
 * Partner Approval Dialog for "Friend" mode.
 */
@Composable
fun FriendApprovalDialog(
    targetFeatureTitle: String,
    partnerEmail: String,
    onRequestCode: () -> Unit,
    onSubmitCode: (String) -> Unit,
    onDismiss: () -> Unit,
    devCode: String? = null
) {
    var code by remember { mutableStateOf("") }
    var codeRequested by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        onRequestCode()
        codeRequested = true
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = CardBackground,
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(22.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .background(CoralPrimary.copy(alpha = 0.15f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_partner),
                            contentDescription = null,
                            tint = CoralPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "Partner Approval",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = partnerEmail.ifBlank { "Accountability Friend" },
                            fontSize = 12.sp,
                            color = Color.White.copy(alpha = 0.6f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                val friendPrompt = when {
                    targetFeatureTitle.startsWith("Change", ignoreCase = true) ->
                        "To change your accountability mode, ask your partner for their 6-digit approval code:"
                    targetFeatureTitle.startsWith("Remove", ignoreCase = true) ->
                        "To remove your accountability partner, ask your partner for their 6-digit approval code:"
                    else ->
                        "To disable $targetFeatureTitle, ask your partner for their 6-digit approval code:"
                }
                Text(
                    text = friendPrompt,
                    fontSize = 12.5.sp,
                    color = Color.White.copy(alpha = 0.75f),
                    lineHeight = 17.sp
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = code,
                    onValueChange = { if (it.length <= 6) code = it },
                    placeholder = { Text("6-digit code", fontSize = 14.sp, color = Color.White.copy(alpha = 0.3f)) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CoralPrimary,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.15f),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    singleLine = true
                )

                if (!devCode.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    TextButton(
                        onClick = { code = devCode },
                        modifier = Modifier.align(Alignment.Start),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Text(
                            text = "💡 Auto-fill Code for Testing ($devCode)",
                            fontSize = 11.5.sp,
                            color = CoralPrimary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                TextButton(
                    onClick = {
                        onRequestCode()
                        codeRequested = true
                    },
                    modifier = Modifier.align(Alignment.Start)
                ) {
                    Text(
                        text = if (codeRequested) "Email sent to partner!" else "Resend approval email to partner",
                        fontSize = 12.sp,
                        color = if (codeRequested) Color(0xFF4CAF50) else CoralPrimary
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel", color = Color.White.copy(alpha = 0.7f))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = { onSubmitCode(code.trim()) },
                        enabled = code.trim().length >= 4,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = CoralPrimary,
                            disabledContainerColor = Color.White.copy(alpha = 0.1f)
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Verify & Unlock", color = Color.White, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

/**
 * Modal dialog prompting the user to allow Android Device Administrator access
 * to activate Uninstall Protection.
 */
@Composable
fun DeviceAdminPermissionDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = CardBackground,
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .background(CoralPrimary.copy(alpha = 0.15f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_feature_shield),
                            contentDescription = null,
                            tint = CoralPrimary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "Admin Access Required",
                            fontSize = 17.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "Uninstall Protection",
                            fontSize = 12.sp,
                            color = CoralPrimary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "To prevent PGuard from being uninstalled or tampered with, Android requires Device Administrator permission.\n\nOnce activated, the Android operating system prevents PGuard from being uninstalled until you pass your accountability check inside the app.\n\nOn the next screen, tap \"Activate this device admin app\".",
                    fontSize = 13.5.sp,
                    color = Color.White.copy(alpha = 0.85f),
                    lineHeight = 20.sp
                )

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel", color = Color.White.copy(alpha = 0.7f))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = onConfirm,
                        colors = ButtonDefaults.buttonColors(containerColor = CoralPrimary),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = "Allow Admin Access",
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}
