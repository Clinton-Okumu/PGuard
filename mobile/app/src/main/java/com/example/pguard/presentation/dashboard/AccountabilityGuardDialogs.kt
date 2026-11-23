package com.example.pguard.presentation.dashboard

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.pguard.R
import com.example.pguard.core.network.model.SecurityAlertDto
import com.example.pguard.engine.accessibility.BlockingPreferences
import com.example.pguard.presentation.accountability.TypingChallengeEngine
import com.example.pguard.presentation.theme.pguardColors
import kotlinx.coroutines.delay

@Composable
fun AccountabilityModeSelectionDialog(
    currentMode: AccountabilityMode,
    onSelectMode: (AccountabilityMode) -> Unit,
    onDismiss: () -> Unit
) {
    var selected by remember { mutableStateOf(currentMode) }
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
                            .background(primary.copy(alpha = 0.15f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_partner),
                            contentDescription = null,
                            tint = primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "Accountability Guard",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Choose friction before turning off blocks",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
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
                        "Type a uniquely randomized commitment phrase within 60 seconds."
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
                            .clip(MaterialTheme.shapes.small)
                            .background(if (isSelected) primary.copy(alpha = 0.08f) else Color.Transparent)
                            .border(
                                width = 1.dp,
                                color = if (isSelected) primary.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outlineVariant,
                                shape = MaterialTheme.shapes.small
                            )
                            .clickable { selected = mode }
                            .padding(12.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        RadioButton(
                            selected = isSelected,
                            onClick = { selected = mode },
                            colors = RadioButtonDefaults.colors(
                                selectedColor = primary,
                                unselectedColor = MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = title,
                                style = MaterialTheme.typography.titleSmall,
                                color = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f)
                            )
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = desc,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
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
                        Text("Cancel", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = { onSelectMode(selected) },
                        colors = ButtonDefaults.buttonColors(containerColor = primary),
                        shape = MaterialTheme.shapes.small
                    ) {
                        Text("Save Mode", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimary)
                    }
                }
            }
        }
    }
}

@Composable
fun TypingChallengeDialog(
    targetFeatureTitle: String,
    onSuccess: () -> Unit,
    onDismiss: () -> Unit
) {
    var targetText by remember { mutableStateOf(TypingChallengeEngine.generateChallenge()) }
    var inputText by remember { mutableStateOf("") }
    var pasteBlockedWarning by remember { mutableStateOf(false) }
    var secondsLeft by remember { mutableIntStateOf(60) }
    var isExpired by remember { mutableStateOf(false) }
    var attemptNonce by remember { mutableIntStateOf(0) }

    LaunchedEffect(attemptNonce) {
        secondsLeft = 60
        isExpired = false
        while (secondsLeft > 0) {
            delay(1000L)
            secondsLeft -= 1
        }
        isExpired = true
    }

    val matchState = TypingChallengeEngine.evaluatePrefix(targetText, inputText)
    val isMatch = matchState.isComplete
    val primary = MaterialTheme.colorScheme.primary
    val successColor = MaterialTheme.pguardColors.success
    val errorColor = MaterialTheme.colorScheme.error

    val annotatedPrompt = buildAnnotatedString {
        if (matchState.matchedCount > 0) {
            withStyle(SpanStyle(color = successColor, fontWeight = FontWeight.SemiBold)) {
                append(targetText.substring(0, matchState.matchedCount))
            }
        }
        val mismatchIdx = matchState.firstMismatchIndex
        if (mismatchIdx != null && mismatchIdx < targetText.length) {
            withStyle(
                SpanStyle(
                    color = errorColor,
                    background = errorColor.copy(alpha = 0.18f),
                    textDecoration = TextDecoration.Underline,
                    fontWeight = FontWeight.Bold
                )
            ) {
                append(targetText[mismatchIdx])
            }
            if (mismatchIdx + 1 < targetText.length) {
                withStyle(SpanStyle(color = primary.copy(alpha = 0.9f))) {
                    append(targetText.substring(mismatchIdx + 1))
                }
            }
        } else if (matchState.matchedCount < targetText.length) {
            withStyle(SpanStyle(color = primary.copy(alpha = 0.9f))) {
                append(targetText.substring(matchState.matchedCount))
            }
        }
    }

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
                    .verticalScroll(rememberScrollState())
                    .padding(22.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Typing Challenge",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = if (isExpired) "Expired" else "${secondsLeft}s",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = if (secondsLeft <= 10) primary else MaterialTheme.colorScheme.onSurface
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { secondsLeft / 60f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(CircleShape),
                    color = primary,
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHigh
                )

                Spacer(modifier = Modifier.height(14.dp))
                val challengePrompt = when {
                    targetFeatureTitle.startsWith("Change", ignoreCase = true) ->
                        "To change your accountability mode, accurately type the phrase and code below within 60 seconds:"
                    targetFeatureTitle.startsWith("Remove", ignoreCase = true) ->
                        "To remove your accountability partner, accurately type the phrase and code below within 60 seconds:"
                    targetFeatureTitle.equals("Log Out", ignoreCase = true) ->
                        "To log out of your account, accurately type the phrase and code below within 60 seconds:"
                    else ->
                        "To turn off $targetFeatureTitle, accurately type the phrase and code below within 60 seconds:"
                }
                Text(
                    text = challengePrompt,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = annotatedPrompt,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(12.dp)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = inputText,
                    onValueChange = { candidate ->
                        if (!isExpired) {
                            val result = TypingChallengeEngine.sanitizeTypedInput(inputText, candidate)
                            inputText = result.acceptedText
                            pasteBlockedWarning = result.pasteBlocked
                        }
                    },
                    placeholder = {
                        Text(
                            "Type phrase + [CODE] manually...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    keyboardOptions = KeyboardOptions(
                        autoCorrectEnabled = false,
                        keyboardType = KeyboardType.Ascii
                    ),
                    isError = matchState.firstMismatchIndex != null || pasteBlockedWarning,
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.small,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = if (isMatch) successColor else primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                        errorBorderColor = errorColor,
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                    ),
                    singleLine = false,
                    maxLines = 4,
                    enabled = !isExpired
                )

                if (pasteBlockedWarning) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Copy/paste is disabled. Please type each character manually.",
                        color = errorColor,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium
                    )
                } else if (matchState.firstMismatchIndex != null && !isExpired) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Typo at character #${matchState.firstMismatchIndex + 1} (highlighted above).",
                        color = errorColor,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                if (isExpired) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Time expired! Protection remains active.",
                            color = primary,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = {
                            targetText = TypingChallengeEngine.generateChallenge()
                            inputText = ""
                            pasteBlockedWarning = false
                            attemptNonce++
                        }) {
                            Text("Try New Phrase", style = MaterialTheme.typography.labelMedium, color = primary)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                            containerColor = primary,
                            disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                        ),
                        shape = MaterialTheme.shapes.small
                    ) {
                        Text("Verify & Unlock", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimary)
                    }
                }
            }
        }
    }
}

@Composable
fun TimeDelayConfirmDialog(
    targetFeatureTitle: String,
    activeTimerTitle: String? = null,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
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
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .background(primary.copy(alpha = 0.15f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_focus_mode),
                        contentDescription = null,
                        tint = primary,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = "24-Hour Cooling-Off Period",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(8.dp))
                val timeDelayPrompt = when {
                    targetFeatureTitle.startsWith("Change", ignoreCase = true) ->
                        "Changing your accountability mode requires a 24-hour waiting delay to prevent impulsive decisions.\n\nYour current protection settings remain active until the timer expires."
                    targetFeatureTitle.startsWith("Remove", ignoreCase = true) ->
                        "Removing your accountability partner requires a 24-hour waiting delay to prevent impulsive decisions.\n\nYour current partner protection remains active until the timer expires."
                    targetFeatureTitle.equals("Log Out", ignoreCase = true) ->
                        "Logging out requires a 24-hour waiting delay to prevent impulsive bypasses.\n\nProtection remains active until the timer expires."
                    else ->
                        "Turning off $targetFeatureTitle requires a 24-hour waiting delay to prevent impulsive decisions.\n\nThe feature will remain 100% blocked until the timer expires. You can cancel this unlock request at any time."
                }
                Text(
                    text = timeDelayPrompt,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (!activeTimerTitle.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = primary.copy(alpha = 0.10f),
                        border = BorderStroke(1.dp, primary.copy(alpha = 0.35f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Note: Starting this timer will replace your active 24h unlock timer for \"$activeTimerTitle\".",
                            style = MaterialTheme.typography.bodySmall,
                            color = primary,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Keep Protected", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = onConfirm,
                        colors = ButtonDefaults.buttonColors(containerColor = primary),
                        shape = MaterialTheme.shapes.small
                    ) {
                        Text("Start 24h Timer", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimary)
                    }
                }
            }
        }
    }
}

@Composable
fun TimeDelayActiveBanner(
    targetFeatureTitle: String,
    unlockAt: Long,
    onCancel: () -> Unit,
    onCompleteUnlock: (() -> Unit)? = null,
    isServerReady: Boolean = false,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var diffMillis by remember(unlockAt, targetFeatureTitle, isServerReady) {
        mutableStateOf(
            BlockingPreferences.getTimeDelayRemainingMs(context, serverConfirmedReady = isServerReady).let {
                if (it > 0L) it else (unlockAt - System.currentTimeMillis()).coerceAtLeast(0L)
            }
        )
    }

    LaunchedEffect(unlockAt, targetFeatureTitle, isServerReady) {
        diffMillis = BlockingPreferences.getTimeDelayRemainingMs(context, serverConfirmedReady = isServerReady)
        while (diffMillis > 0L) {
            val interval = if (diffMillis <= 120_000L) 1_000L else 10_000L
            delay(interval)
            diffMillis = BlockingPreferences.getTimeDelayRemainingMs(context, serverConfirmedReady = isServerReady)
        }
    }

    val awaitingServerVerify = BlockingPreferences.isTimeDelayServerBacked &&
        !isServerReady &&
        unlockAt <= System.currentTimeMillis() &&
        diffMillis > 0L
    val hours = diffMillis / (1000 * 60 * 60)
    val minutes = (diffMillis / (1000 * 60)) % 60
    val seconds = (diffMillis / 1000) % 60
    val isComplete = diffMillis <= 0L
    val primary = MaterialTheme.colorScheme.primary

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = primary.copy(alpha = 0.15f)),
        border = BorderStroke(1.dp, primary.copy(alpha = 0.4f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_focus_mode),
                    contentDescription = null,
                    tint = primary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = if (isComplete) "Unlock Ready: $targetFeatureTitle" else "Unlock Pending: $targetFeatureTitle",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = when {
                            isComplete -> "Cooling-off complete. Ready to unlock."
                            awaitingServerVerify -> "Awaiting server time verification..."
                            hours == 0L && minutes < 2L -> "Available in ${minutes}m ${seconds}s"
                            else -> "Available in ${hours}h ${minutes}m"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (isComplete && onCompleteUnlock != null) {
                    Button(
                        onClick = onCompleteUnlock,
                        shape = MaterialTheme.shapes.small,
                        colors = ButtonDefaults.buttonColors(containerColor = primary),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Text(
                            text = "Unlock",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }

                OutlinedButton(
                    onClick = onCancel,
                    shape = MaterialTheme.shapes.small,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Text("Cancel", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

@Composable
fun FriendApprovalDialog(
    targetFeatureTitle: String,
    partnerEmail: String,
    onRequestCode: () -> Unit,
    onSubmitCode: (String) -> Unit,
    onDismiss: () -> Unit,
    isVerifying: Boolean = false,
    isSendingRequest: Boolean = false,
    emailSentSuccess: Boolean = false,
    errorMessage: String? = null,
    requestError: String? = null
) {
    var code by remember { mutableStateOf("") }
    var resendCooldownSeconds by remember { mutableIntStateOf(0) }
    val primary = MaterialTheme.colorScheme.primary

    LaunchedEffect(Unit) {
        onRequestCode()
        resendCooldownSeconds = 30
    }

    LaunchedEffect(resendCooldownSeconds) {
        if (resendCooldownSeconds > 0) {
            delay(1000L)
            resendCooldownSeconds -= 1
        }
    }

    val canResend = !isVerifying && !isSendingRequest && (resendCooldownSeconds == 0 || !requestError.isNullOrBlank())

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
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .background(primary.copy(alpha = 0.15f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_partner),
                            contentDescription = null,
                            tint = primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "Partner Approval",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = partnerEmail.ifBlank { "Accountability Friend" },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                val friendPrompt = when {
                    targetFeatureTitle.startsWith("Change", ignoreCase = true) ->
                        "To change your accountability mode, ask your partner for their 6-digit approval code:"
                    targetFeatureTitle.startsWith("Remove", ignoreCase = true) ->
                        "To remove your accountability partner, ask your partner for their 6-digit approval code:"
                    targetFeatureTitle.equals("Log Out", ignoreCase = true) ->
                        "To log out of your account, ask your partner for their 6-digit approval code:"
                    else ->
                        "To disable $targetFeatureTitle, ask your partner for their 6-digit approval code:"
                }
                Text(
                    text = friendPrompt,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = code,
                    onValueChange = { raw ->
                        if (!isVerifying) {
                            code = raw.filter { it.isDigit() }.take(6)
                        }
                    },
                    placeholder = {
                        Text(
                            "6-digit code",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.small,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                    ),
                    singleLine = true,
                    enabled = !isVerifying
                )

                if (!errorMessage.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = errorMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Medium
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                TextButton(
                    onClick = {
                        onRequestCode()
                        resendCooldownSeconds = 30
                    },
                    enabled = canResend,
                    modifier = Modifier.align(Alignment.Start)
                ) {
                    val statusText = when {
                        isSendingRequest -> "Sending approval email..."
                        !requestError.isNullOrBlank() -> "Failed to send ($requestError) — tap to retry"
                        emailSentSuccess && resendCooldownSeconds > 0 ->
                            "Email sent to partner! Resend in ${resendCooldownSeconds}s"
                        emailSentSuccess -> "Email sent to partner! Tap to resend"
                        resendCooldownSeconds > 0 -> "Wait ${resendCooldownSeconds}s to resend"
                        else -> "Send approval email to partner"
                    }
                    val statusColor = when {
                        !requestError.isNullOrBlank() -> MaterialTheme.colorScheme.error
                        emailSentSuccess -> MaterialTheme.pguardColors.success
                        else -> primary
                    }
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.bodySmall,
                        color = statusColor
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss, enabled = !isVerifying) {
                        Text("Cancel", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = { onSubmitCode(code.trim()) },
                        enabled = code.trim().length == 6 && !isVerifying,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = primary,
                            disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                        ),
                        shape = MaterialTheme.shapes.small
                    ) {
                        Text(
                            text = if (isVerifying) "Verifying..." else "Verify & Unlock",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SecurityAlertsDialog(
    events: List<SecurityAlertDto>,
    isLoading: Boolean,
    onRefresh: () -> Unit,
    onDismiss: () -> Unit
) {
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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Partner Security Alerts",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    TextButton(onClick = onRefresh, enabled = !isLoading) {
                        Text("Refresh", style = MaterialTheme.typography.labelMedium, color = primary)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(modifier = Modifier.height(12.dp))

                if (isLoading) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = primary, modifier = Modifier.size(28.dp))
                    }
                } else if (events.isEmpty()) {
                    Text(
                        text = "No security or tamper alerts recorded yet. Alerts appear here whenever SOS Urge Lockdown, tamper attempts, or shield disables occur while your partner is linked.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 12.dp)
                    )
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 320.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        events.forEach { alert ->
                            Surface(
                                shape = MaterialTheme.shapes.small,
                                color = MaterialTheme.colorScheme.surfaceContainerLow,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = formatSecurityEventTitle(alert.eventType),
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = FontWeight.Bold,
                                            color = primary
                                        )
                                        val dateText = alert.createdAt?.take(16)?.replace("T", " ").orEmpty()
                                        if (dateText.isNotEmpty()) {
                                            Text(
                                                text = dateText,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = alert.message,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.88f)
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Close", style = MaterialTheme.typography.labelLarge, color = primary)
                    }
                }
            }
        }
    }
}

private fun formatSecurityEventTitle(eventType: String): String = when (eventType.lowercase()) {
    "sos_triggered" -> "SOS Urge Triggered"
    "protection_disabled" -> "Protection Disabled"
    "permission_revoked" -> "Permission Revoked"
    "uninstall_attempt" -> "Tamper / Uninstall Attempt"
    else -> eventType
}

@Composable
fun DeviceAdminPermissionDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
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
                    .padding(24.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .background(primary.copy(alpha = 0.15f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_feature_shield),
                            contentDescription = null,
                            tint = primary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "Admin Access Required",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Uninstall Protection",
                            style = MaterialTheme.typography.labelMedium,
                            color = primary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "To prevent PGuard from being uninstalled or tampered with, Android requires Device Administrator permission.\n\nOnce activated, the Android operating system prevents PGuard from being uninstalled until you pass your accountability check inside the app.\n\nOn the next screen, tap \"Activate this device admin app\".",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.88f)
                )

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = onConfirm,
                        colors = ButtonDefaults.buttonColors(containerColor = primary),
                        shape = MaterialTheme.shapes.small
                    ) {
                        Text(
                            text = "Allow Admin Access",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
            }
        }
    }
}
