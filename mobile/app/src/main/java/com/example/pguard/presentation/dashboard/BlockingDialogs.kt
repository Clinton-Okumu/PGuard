package com.example.pguard.presentation.dashboard

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.pguard.R
import com.example.pguard.engine.accessibility.BlockedScreenConfig

@Composable
internal fun BlockedScreenMessageDialog(
    text: String,
    errorMessage: String? = null,
    onTextChange: (String) -> Unit,
    onSave: () -> Unit,
    onResetDefault: () -> Unit = {},
    onDismiss: () -> Unit
) {
    val charCount = BlockedScreenConfig.messageLength(text)
    val overLimit = charCount > BlockedScreenConfig.MAX_MESSAGE_LENGTH
    val isCustomized = text.trim() != BlockedScreenConfig.DEFAULT_MESSAGE

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Blocked screen message",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        },
        text = {
            Column {
                Text(
                    text = "Enter the message displayed across blocked sites, feeds, and apps:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = onTextChange,
                    maxLines = 4,
                    isError = !errorMessage.isNullOrEmpty() || overLimit,
                    supportingText = {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = errorMessage.orEmpty(),
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = "$charCount/${BlockedScreenConfig.MAX_MESSAGE_LENGTH}",
                                color = if (overLimit) {
                                    MaterialTheme.colorScheme.error
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                        errorBorderColor = MaterialTheme.colorScheme.error
                    )
                )
                if (isCustomized) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = onResetDefault) {
                            Text(
                                text = "Reset to default",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onSave,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.primary)
            ) {
                Text("Save", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
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
internal fun BlockedScreenCountdownDialog(
    selectedSeconds: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val options = listOf(
        "Manual (stay until tapped)" to 0,
        "3 seconds" to 3,
        "5 seconds" to 5,
        "10 seconds" to 10,
        "15 seconds" to 15
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Blocked screen countdown",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                options.forEach { (label, seconds) ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(seconds) }
                            .padding(vertical = 8.dp)
                    ) {
                        RadioButton(
                            selected = selectedSeconds == seconds,
                            onClick = { onSelect(seconds) },
                            colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.primary)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = label,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        },
        confirmButton = {},
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
internal fun RedirectUrlDialog(
    text: String,
    errorMessage: String? = null,
    onTextChange: (String) -> Unit,
    onSave: () -> Unit,
    onClear: () -> Unit = {},
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Redirect after closing",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        },
        text = {
            Column {
                Text(
                    text = "Enter a safe http:// or https:// website to open when the blocked screen closes (leave empty for Not set):",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = onTextChange,
                    placeholder = { Text("https://example.com") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Uri,
                        imeAction = ImeAction.Done
                    ),
                    isError = !errorMessage.isNullOrEmpty(),
                    supportingText = if (!errorMessage.isNullOrEmpty()) {
                        { Text(text = errorMessage, color = MaterialTheme.colorScheme.error) }
                    } else null,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                        errorBorderColor = MaterialTheme.colorScheme.error
                    )
                )
                if (text.isNotBlank()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = onClear) {
                            Text(
                                text = "Clear URL",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onSave,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.primary)
            ) {
                Text("Save", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
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
internal fun BlockedScreenPreviewDialog(
    message: String,
    countdownSeconds: Int,
    redirectUrl: String,
    onDismiss: () -> Unit
) {
    val coral = Color(0xFFFF5252)
    val darkSurface = Color(0xFF0D0F14)
    val resolvedMessage = BlockedScreenConfig.resolveOverlayMessage(message, BlockedScreenConfig.DEFAULT_MESSAGE)
    val displayHost = BlockedScreenConfig.extractDisplayHost(redirectUrl)
    val hasRedirect = redirectUrl.isNotBlank() && displayHost.isNotBlank()

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = darkSurface,
            border = BorderStroke(1.dp, Color(0x33FFFFFF)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 28.dp)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(72.dp)
                        .background(coral, RoundedCornerShape(18.dp))
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_pguard_globe_shield),
                        contentDescription = null,
                        tint = Color.Unspecified,
                        modifier = Modifier.size(46.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Access Blocked",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = resolvedMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xB3FFFFFF),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(14.dp))

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0x26FFFFFF),
                    border = BorderStroke(1.dp, Color(0x4DFF5252))
                ) {
                    Text(
                        text = "blocked-site.example.com",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = coral,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                    )
                }

                Spacer(modifier = Modifier.height(22.dp))

                Button(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = coral, contentColor = Color.White),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    val goBackText = if (countdownSeconds > 0 && !hasRedirect) {
                        "Go Back ($countdownSeconds s)"
                    } else {
                        "Go Back to Safety"
                    }
                    Text(
                        text = goBackText,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                }

                if (hasRedirect) {
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, Color(0x33FFFFFF)),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = Color(0x26FFFFFF),
                            contentColor = Color(0xB3FFFFFF)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                    ) {
                        val redirectLabel = if (countdownSeconds > 0) {
                            "Redirecting to $displayHost ($countdownSeconds s)"
                        } else {
                            "Open $displayHost"
                        }
                        Text(
                            text = redirectLabel,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                TextButton(onClick = onDismiss) {
                    Text(
                        text = "Close Preview",
                        style = MaterialTheme.typography.labelLarge,
                        color = Color(0x99FFFFFF)
                    )
                }
            }
        }
    }
}

@Composable
internal fun InfoDialog(
    title: String,
    message: String,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.88f)
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = onDismiss,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.primary)
            ) {
                Text("Got it", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            }
        },
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.medium
    )
}

@Composable
internal fun InvitePartnerDialog(
    email: String,
    partnerName: String = "",
    onEmailChange: (String) -> Unit,
    onPartnerNameChange: (String) -> Unit = {},
    onSubmit: () -> Unit,
    onDismiss: () -> Unit,
    errorMessage: String? = null
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Invite accountability partner",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        },
        text = {
            Column {
                Text(
                    text = "Enter the name and email address of the trusted person who will support you.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = partnerName,
                    onValueChange = onPartnerNameChange,
                    placeholder = { Text("Partner name (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline
                    )
                )
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = email,
                    onValueChange = onEmailChange,
                    placeholder = { Text("partner@example.com") },
                    singleLine = true,
                    isError = !errorMessage.isNullOrEmpty(),
                    supportingText = if (!errorMessage.isNullOrEmpty()) {
                        { Text(text = errorMessage, color = MaterialTheme.colorScheme.error) }
                    } else null,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                        errorBorderColor = MaterialTheme.colorScheme.error
                    )
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = onSubmit,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.primary)
            ) {
                Text("Invite", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
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
internal fun ConfirmCancelInviteDialog(
    partnerEmail: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Cancel pending invite?",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        },
        text = {
            Text(
                text = "Cancel the pending accountability invitation to $partnerEmail?",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) {
                Text("Cancel Invite", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Keep Waiting", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.medium
    )
}
