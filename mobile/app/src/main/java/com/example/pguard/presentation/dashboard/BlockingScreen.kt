package com.example.pguard.presentation.dashboard

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.pguard.R
import com.example.pguard.presentation.theme.CoralPrimary
import com.example.pguard.presentation.theme.PGuardTheme

private val GoldColor = Color(0xFFFFB800)
private val CardBackground = Color(0xFF18181C)
private val AlertBorderColor = Color(0x33FF5252)

private val TabHeaderShape = GenericShape { size, _ ->
    val cut = 18f
    val radius = 32f
    moveTo(0f, size.height)
    lineTo(0f, radius)
    arcTo(
        rect = Rect(0f, 0f, radius * 2, radius * 2),
        startAngleDegrees = 180f,
        sweepAngleDegrees = 90f,
        forceMoveTo = false
    )
    lineTo(size.width - cut - 8f, 0f)
    quadraticTo(
        size.width - cut, 0f,
        size.width - cut + 4f, 4f
    )
    lineTo(size.width, size.height)
    close()
}

@Composable
fun BlockingScreen(
    onPremiumClick: () -> Unit = {},
    onGuideClick: () -> Unit = {},
    onFocusModeClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    // Content Blocking switch states
    var isAdultContentBlocked by remember { mutableStateOf(true) }
    var isMediaSearchBlocked by remember { mutableStateOf(false) }
    var isShortsBlocked by remember { mutableStateOf(false) }
    var isWhatsAppChannelsBlocked by remember { mutableStateOf(false) }

    // Advanced Blocking switch states
    var isUninstallProtectionEnabled by remember { mutableStateOf(false) }
    var isUnsupportedBrowsersBlocked by remember { mutableStateOf(false) }
    var isNewlyInstalledAppsBlocked by remember { mutableStateOf(false) }

    // Customize Blocked screen states
    var blockedScreenMessage by remember { mutableStateOf("This page is blocked.") }
    var blockedScreenCountdown by remember { mutableStateOf("3 seconds") }
    var redirectUrl by remember { mutableStateOf("Not set") }

    // Dialog edit & info states
    var editingItem by remember { mutableStateOf<String?>(null) }
    var tempEditText by remember { mutableStateOf("") }
    var infoDialogTitle by remember { mutableStateOf<String?>(null) }
    var infoDialogMessage by remember { mutableStateOf<String?>(null) }

    // Alert banner dismissal states
    var showPushNotificationCard by remember { mutableStateOf(true) }
    var showAccessibilityCard by remember { mutableStateOf(true) }
    var showOverlayWindowCard by remember { mutableStateOf(true) }
    var showGuideSheet by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 96.dp) // Clearance for floating button & bottom bar
        ) {
            // 1. Top Header Row: Premium & Guide Pills
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Premium Button (Gold Outline)
                OutlinedButton(
                    onClick = onPremiumClick,
                    shape = CircleShape,
                    border = BorderStroke(1.2.dp, GoldColor),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = GoldColor.copy(alpha = 0.08f),
                        contentColor = GoldColor
                    ),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                    modifier = Modifier.height(38.dp)
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_crown),
                        contentDescription = null,
                        tint = GoldColor,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Premium",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = GoldColor
                    )
                }

                // Guide Button (Coral Outline)
                OutlinedButton(
                    onClick = {
                        showGuideSheet = true
                        onGuideClick()
                    },
                    shape = CircleShape,
                    border = BorderStroke(1.2.dp, CoralPrimary),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = CoralPrimary.copy(alpha = 0.08f),
                        contentColor = CoralPrimary
                    ),
                    contentPadding = PaddingValues(horizontal = 18.dp, vertical = 6.dp),
                    modifier = Modifier.height(38.dp)
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_guide),
                        contentDescription = null,
                        tint = CoralPrimary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Guide",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = CoralPrimary
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // 2. Permission / Setup Alert Cards
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Push Notifications Alert
                AnimatedVisibility(visible = showPushNotificationCard) {
                    AlertActionCard(
                        title = "Push Notifications",
                        subtitle = "If allowed, you will get notified of important updates and changes",
                        actionLabel = "Allow",
                        trailingIcon = R.drawable.ic_notification,
                        onAction = {
                            openNotificationSettings(context)
                            showPushNotificationCard = false
                        }
                    )
                }

                // Accessibility Service Alert
                AnimatedVisibility(visible = showAccessibilityCard) {
                    AlertActionCard(
                        title = "Accessibility is OFF",
                        subtitle = "Some features may not work unless you turn on accessibility service.",
                        actionLabel = "Turn ON",
                        trailingIcon = R.drawable.ic_chevron_right,
                        onAction = {
                            openAccessibilitySettings(context)
                            showAccessibilityCard = false
                        }
                    )
                }

                // Display pop-up window Alert
                AnimatedVisibility(visible = showOverlayWindowCard) {
                    AlertActionCard(
                        title = "Display pop-up window",
                        subtitle = "Allows pop-ups to appear over other apps and enables safe search.",
                        actionLabel = "Turn ON",
                        trailingIcon = R.drawable.ic_chevron_right,
                        onAction = {
                            openOverlayPermission(context)
                            showOverlayWindowCard = false
                        }
                    )
                }

                // 3. Accountability Partner Card
                AccountabilityCard(
                    partnerStatus = "Not found",
                    onRemove = {}
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            // 4. Content Blocking Card
            SectionCard(
                title = "Content Blocking",
                iconRes = R.drawable.ic_pguard_globe_shield,
                modifier = Modifier.padding(horizontal = 16.dp)
            ) {
                ToggleSettingRow(
                    title = "Block adult content",
                    checked = isAdultContentBlocked,
                    onCheckedChange = { isAdultContentBlocked = it }
                )
                HorizontalDivider(color = Color.White.copy(alpha = 0.08f))

                ToggleSettingRow(
                    title = "Block image & video search",
                    checked = isMediaSearchBlocked,
                    onCheckedChange = { isMediaSearchBlocked = it }
                )
                HorizontalDivider(color = Color.White.copy(alpha = 0.08f))

                ToggleSettingRow(
                    title = "Block Youtube Shorts",
                    checked = isShortsBlocked,
                    onCheckedChange = { isShortsBlocked = it }
                )
                HorizontalDivider(color = Color.White.copy(alpha = 0.08f))

                ToggleSettingRow(
                    title = "Block WhatsApp channels",
                    isPremium = true,
                    checked = isWhatsAppChannelsBlocked,
                    onCheckedChange = { isWhatsAppChannelsBlocked = it }
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            // 5. Advanced Blocking Card
            SectionCard(
                title = "Advanced Blocking",
                iconRes = R.drawable.ic_nav_blocking,
                modifier = Modifier.padding(horizontal = 16.dp)
            ) {
                ToggleSettingRow(
                    title = "Uninstall protection",
                    checked = isUninstallProtectionEnabled,
                    onCheckedChange = { isUninstallProtectionEnabled = it },
                    onInfoClick = {
                        infoDialogTitle = "Uninstall protection"
                        infoDialogMessage = "Prevents PGuard from being removed or tampered with without authorization."
                    }
                )
                HorizontalDivider(color = Color.White.copy(alpha = 0.08f))

                ToggleSettingRow(
                    title = "Block unsupported browsers",
                    checked = isUnsupportedBrowsersBlocked,
                    onCheckedChange = { isUnsupportedBrowsersBlocked = it },
                    onInfoClick = {
                        infoDialogTitle = "Block unsupported browsers"
                        infoDialogMessage = "Blocks web browsers that do not support PGuard content filtering."
                    }
                )
                HorizontalDivider(color = Color.White.copy(alpha = 0.08f))

                ToggleSettingRow(
                    title = "Block newly installed apps",
                    isPremium = true,
                    checked = isNewlyInstalledAppsBlocked,
                    onCheckedChange = { isNewlyInstalledAppsBlocked = it },
                    onInfoClick = {
                        infoDialogTitle = "Block newly installed apps"
                        infoDialogMessage = "Automatically blocks any newly installed apps until you review and allow them."
                    }
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            // 6. Customize Blocked screen Card
            SectionCard(
                title = "Customize Blocked screen",
                iconRes = R.drawable.ic_blocked_screen,
                modifier = Modifier.padding(horizontal = 16.dp)
            ) {
                EditableSettingRow(
                    title = "Blocked screen message",
                    subtitle = blockedScreenMessage,
                    isPremium = true,
                    onEditClick = {
                        tempEditText = blockedScreenMessage
                        editingItem = "message"
                    },
                    onInfoClick = {
                        infoDialogTitle = "Blocked screen message"
                        infoDialogMessage = "The message displayed on screen whenever a blocked website or app is accessed."
                    }
                )
                HorizontalDivider(color = Color.White.copy(alpha = 0.08f))

                EditableSettingRow(
                    title = "Blocked screen countdown",
                    subtitle = blockedScreenCountdown,
                    isPremium = true,
                    onEditClick = { editingItem = "countdown" },
                    onInfoClick = {
                        infoDialogTitle = "Blocked screen countdown"
                        infoDialogMessage = "The countdown timer duration before the blocked screen automatically dismisses."
                    }
                )
                HorizontalDivider(color = Color.White.copy(alpha = 0.08f))

                EditableSettingRow(
                    title = "Redirect after closing (any URL)",
                    subtitle = redirectUrl,
                    isPremium = true,
                    onEditClick = {
                        tempEditText = if (redirectUrl == "Not set") "" else redirectUrl
                        editingItem = "redirect"
                    },
                    onInfoClick = {
                        infoDialogTitle = "Redirect after closing"
                        infoDialogMessage = "Optionally redirect to a chosen safe URL or homepage when closing the blocked screen."
                    }
                )
            }
        }

        // 7. Floating Action Button: Focus Mode
        ExtendedFloatingActionButton(
            onClick = onFocusModeClick,
            containerColor = CoralPrimary,
            contentColor = Color.White,
            elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 6.dp),
            shape = CircleShape,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = 16.dp)
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_focus_mode),
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Focus Mode",
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
        }
    }

    // Edit Dialog for Blocked Screen Message
    if (editingItem == "message") {
        AlertDialog(
            onDismissRequest = { editingItem = null },
            title = {
                Text(
                    text = "Blocked screen message",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            },
            text = {
                Column {
                    Text(
                        text = "Enter the message to display when content is blocked:",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = tempEditText,
                        onValueChange = { tempEditText = it },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CoralPrimary,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.3f)
                        )
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (tempEditText.isNotBlank()) {
                            blockedScreenMessage = tempEditText
                        }
                        editingItem = null
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = CoralPrimary)
                ) {
                    Text("Save", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { editingItem = null }) {
                    Text("Cancel", color = Color.White.copy(alpha = 0.7f))
                }
            },
            containerColor = Color(0xFF1E1E24),
            shape = RoundedCornerShape(16.dp)
        )
    }

    // Edit Dialog for Countdown
    if (editingItem == "countdown") {
        val options = listOf("Immediately", "3 seconds", "5 seconds", "10 seconds")
        AlertDialog(
            onDismissRequest = { editingItem = null },
            title = {
                Text(
                    text = "Blocked screen countdown",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    options.forEach { opt ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    blockedScreenCountdown = opt
                                    editingItem = null
                                }
                                .padding(vertical = 8.dp)
                        ) {
                            RadioButton(
                                selected = (blockedScreenCountdown == opt),
                                onClick = {
                                    blockedScreenCountdown = opt
                                    editingItem = null
                                },
                                colors = RadioButtonDefaults.colors(selectedColor = CoralPrimary)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(text = opt, fontSize = 15.sp, color = Color.White)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { editingItem = null }) {
                    Text("Cancel", color = Color.White.copy(alpha = 0.7f))
                }
            },
            containerColor = Color(0xFF1E1E24),
            shape = RoundedCornerShape(16.dp)
        )
    }

    // Edit Dialog for Redirect URL
    if (editingItem == "redirect") {
        AlertDialog(
            onDismissRequest = { editingItem = null },
            title = {
                Text(
                    text = "Redirect after closing",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            },
            text = {
                Column {
                    Text(
                        text = "Enter a URL to open after the blocked screen closes (leave empty for Not set):",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = tempEditText,
                        onValueChange = { tempEditText = it },
                        placeholder = { Text("https://example.com") },
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CoralPrimary,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.3f)
                        )
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        redirectUrl = if (tempEditText.isBlank()) "Not set" else tempEditText
                        editingItem = null
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = CoralPrimary)
                ) {
                    Text("Save", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { editingItem = null }) {
                    Text("Cancel", color = Color.White.copy(alpha = 0.7f))
                }
            },
            containerColor = Color(0xFF1E1E24),
            shape = RoundedCornerShape(16.dp)
        )
    }

    // Generic Info Dialog
    if (infoDialogTitle != null && infoDialogMessage != null) {
        AlertDialog(
            onDismissRequest = {
                infoDialogTitle = null
                infoDialogMessage = null
            },
            title = {
                Text(
                    text = infoDialogTitle ?: "",
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp
                )
            },
            text = {
                Text(
                    text = infoDialogMessage ?: "",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        infoDialogTitle = null
                        infoDialogMessage = null
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = CoralPrimary)
                ) {
                    Text("Got it", fontWeight = FontWeight.Bold)
                }
            },
            containerColor = Color(0xFF1E1E24),
            shape = RoundedCornerShape(16.dp)
        )
    }

    // Guide Bottom Sheet ("App not working properly?")
    if (showGuideSheet) {
        GuideBottomSheet(
            onDismiss = { showGuideSheet = false }
        )
    }
}

@Composable
private fun AlertActionCard(
    title: String,
    subtitle: String,
    actionLabel: String,
    trailingIcon: Int? = null,
    onAction: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = CardBackground,
        border = BorderStroke(1.dp, AlertBorderColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                modifier = Modifier.weight(1f).padding(end = 12.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    ),
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    ),
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.65f)
                )
            }

            OutlinedButton(
                onClick = onAction,
                shape = CircleShape,
                border = BorderStroke(1.dp, CoralPrimary),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = CoralPrimary
                ),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                modifier = Modifier.height(34.dp)
            ) {
                Text(
                    text = actionLabel,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
                if (trailingIcon != null) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        painter = painterResource(id = trailingIcon),
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = CoralPrimary
                    )
                }
            }
        }
    }
}

@Composable
private fun AccountabilityCard(
    partnerStatus: String,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = CardBackground,
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_partner),
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Accountability Partner",
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.5.sp
                        ),
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }

                Icon(
                    painter = painterResource(id = R.drawable.ic_info),
                    contentDescription = "Info",
                    tint = Color.White.copy(alpha = 0.5f),
                    modifier = Modifier.size(18.dp)
                )
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 12.dp),
                color = Color.White.copy(alpha = 0.08f)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = partnerStatus,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp
                    ),
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.85f)
                )

                OutlinedButton(
                    onClick = onRemove,
                    shape = CircleShape,
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.25f)),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = Color.White.copy(alpha = 0.7f)
                    ),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 2.dp),
                    modifier = Modifier.height(30.dp)
                ) {
                    Text(
                        text = "Remove",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionCard(
    title: String,
    iconRes: Int,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = CardBackground,
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Trapezoid Header Tab matching reference screenshot
            Box(
                modifier = Modifier
                    .clip(TabHeaderShape)
                    .background(Color(0xFF383842))
                    .padding(start = 16.dp, end = 26.dp, top = 8.dp, bottom = 8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        painter = painterResource(id = iconRes),
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.5.sp
                        ),
                        color = Color.White
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            ) {
                content()
            }
        }
    }
}

@Composable
private fun ToggleSettingRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    isPremium: Boolean = false,
    onInfoClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            ),
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f).padding(end = 8.dp)
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (isPremium) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_crown),
                    contentDescription = "Premium",
                    tint = GoldColor,
                    modifier = Modifier.size(18.dp)
                )
            }

            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = Color(0xFF4CAF50), // Emerald Green
                    uncheckedThumbColor = Color(0xFF8E8E93),
                    uncheckedTrackColor = Color(0xFF2C2C30),
                    uncheckedBorderColor = Color.Transparent
                )
            )

            IconButton(
                onClick = onInfoClick,
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_info),
                    contentDescription = "Info",
                    tint = Color.White.copy(alpha = 0.5f),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun EditableSettingRow(
    title: String,
    subtitle: String,
    isPremium: Boolean = false,
    onEditClick: () -> Unit,
    onInfoClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onEditClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 8.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                ),
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 12.sp
                ),
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (isPremium) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_crown),
                    contentDescription = "Premium",
                    tint = GoldColor,
                    modifier = Modifier.size(18.dp)
                )
            }

            IconButton(
                onClick = onEditClick,
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_edit),
                    contentDescription = "Edit",
                    tint = Color.White.copy(alpha = 0.65f),
                    modifier = Modifier.size(16.dp)
                )
            }

            IconButton(
                onClick = onInfoClick,
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_info),
                    contentDescription = "Info",
                    tint = Color.White.copy(alpha = 0.5f),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF121212)
@Composable
private fun BlockingScreenPreview() {
    PGuardTheme(darkTheme = true) {
        BlockingScreen()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GuideBottomSheet(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val manufacturer = Build.MANUFACTURER.lowercase().replace(" ", "")
    val dontKillUrl = if (manufacturer.isNotBlank()) {
        "https://dontkillmyapp.com/$manufacturer"
    } else {
        "https://dontkillmyapp.com"
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF1E1E24),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        dragHandle = {
            BottomSheetDefaults.DragHandle(
                color = Color.White.copy(alpha = 0.35f),
                width = 38.dp,
                height = 4.dp
            )
        },
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .padding(bottom = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "App not working properly?",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp
                ),
                color = Color.White,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Check below solutions",
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 14.sp
                ),
                color = Color.White.copy(alpha = 0.65f),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(20.dp))

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                GuideSolutionCard(
                    title = "Auto Start",
                    description = "Make sure PGuard is allowed to start up when device is restarted or required.",
                    onClick = {
                        openAutoStartSettings(context)
                    }
                )

                GuideSolutionCard(
                    title = "Ignore Battery Optimization",
                    description = "Battery optimization may cause PGuard to stop blocking after some time. Choose 'Don't optimize' option to keep the app running in the background.",
                    onClick = {
                        openBatteryOptimization(context)
                    }
                )

                GuideSolutionCard(
                    title = "Other Issues",
                    description = "If the app is still not working, please visit \"$dontKillUrl\" and follow the steps there.",
                    onClick = {
                        openDontKillMyApp(context, dontKillUrl)
                    }
                )
            }
        }
    }
}

@Composable
private fun GuideSolutionCard(
    title: String,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = CardBackground,
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                ),
                color = Color.White
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 12.5.sp,
                    lineHeight = 17.sp
                ),
                color = Color.White.copy(alpha = 0.7f)
            )
        }
    }
}

private fun openAutoStartSettings(context: Context) {
    val intents = listOf(
        Intent().setComponent(
            android.content.ComponentName(
                "com.miui.securitycenter",
                "com.miui.permcenter.autostart.AutoStartManagementActivity"
            )
        ),
        Intent().setComponent(
            android.content.ComponentName(
                "com.huawei.systemmanager",
                "com.huawei.systemmanager.optimize.process.ProtectActivity"
            )
        ),
        Intent().setComponent(
            android.content.ComponentName(
                "com.huawei.systemmanager",
                "com.huawei.systemmanager.appcontrol.activity.StartupAppControlActivity"
            )
        ),
        Intent().setComponent(
            android.content.ComponentName(
                "com.coloros.safecenter",
                "com.coloros.safecenter.permission.startup.StartupAppListActivity"
            )
        ),
        Intent().setComponent(
            android.content.ComponentName(
                "com.oppo.safe",
                "com.oppo.safe.permission.startup.StartupAppListActivity"
            )
        ),
        Intent().setComponent(
            android.content.ComponentName(
                "com.iqoo.secure",
                "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity"
            )
        ),
        Intent().setComponent(
            android.content.ComponentName(
                "com.vivo.permissionmanager",
                "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"
            )
        ),
        Intent().setComponent(
            android.content.ComponentName(
                "com.samsung.android.lool",
                "com.samsung.android.sm.ui.battery.BatteryActivity"
            )
        )
    )

    for (intent in intents) {
        try {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            return
        } catch (_: Exception) {}
    }

    try {
        val fallbackIntent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(fallbackIntent)
    } catch (_: Exception) {}
}

private fun openBatteryOptimization(context: Context) {
    try {
        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:${context.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    } catch (_: Exception) {
        try {
            val fallbackIntent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(fallbackIntent)
        } catch (_: Exception) {}
    }
}

private fun openDontKillMyApp(context: Context, url: String) {
    try {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    } catch (_: Exception) {}
}

private fun openOverlayPermission(context: Context) {
    try {
        val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
            data = Uri.parse("package:${context.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    } catch (_: Exception) {
        try {
            val fallbackIntent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(fallbackIntent)
        } catch (_: Exception) {}
    }
}

private fun openAccessibilitySettings(context: Context) {
    try {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    } catch (_: Exception) {}
}

private fun openNotificationSettings(context: Context) {
    try {
        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    } catch (_: Exception) {}
}
