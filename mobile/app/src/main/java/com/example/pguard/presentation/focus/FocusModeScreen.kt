package com.example.pguard.presentation.focus

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.pguard.R
import com.example.pguard.engine.accessibility.PGuardAccessibilityService
import com.example.pguard.presentation.blocklist.InstalledAppItem
import com.example.pguard.presentation.blocklist.queryDeviceLauncherApps
import com.example.pguard.presentation.theme.CoralPrimary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private val ScreenBackground = Color(0xFF101014)
private val CardBackground = Color(0xFF1E1E24)

@Composable
fun FocusModeScreen(
    onBackClick: () -> Unit,
    onUpgradeClick: () -> Unit = {}
) {
    val context = LocalContext.current
    val schedules = remember { mutableStateListOf<FocusSchedule>() }

    var showHelpDialog by remember { mutableStateOf(false) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var showLimitToast by remember { mutableStateOf(false) }
    var showAccountabilityGuardDialog by remember { mutableStateOf(false) }

    var editingSchedule by remember { mutableStateOf<FocusSchedule?>(null) }
    var showAppPickerDialog by remember { mutableStateOf(false) }
    var currentAllowedAppsTarget by remember { mutableStateOf<Set<String>>(emptySet()) }
    var onAllowedAppsSelectedCallback by remember { mutableStateOf<((Set<String>) -> Unit)?>(null) }

    fun refreshSchedules() {
        schedules.clear()
        schedules.addAll(FocusModeManager.getSchedules(context))
    }

    LaunchedEffect(Unit) {
        refreshSchedules()
    }

    // Auto-dismiss limit toast after 3.5s
    LaunchedEffect(showLimitToast) {
        if (showLimitToast) {
            delay(3500L)
            showLimitToast = false
        }
    }

    val isAccountabilityLocked = remember {
        val mode = PGuardAccessibilityService.accountabilityMode
        mode.isNotBlank() && !mode.equals("none", ignoreCase = true)
    }

    Scaffold(
        containerColor = ScreenBackground,
        topBar = {
            FocusModeTopBar(
                onBackClick = onBackClick,
                onHelpClick = { showHelpDialog = true }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    if (schedules.size >= FocusModeManager.MAX_FREE_SCHEDULES) {
                        showLimitToast = true
                    } else {
                        editingSchedule = null
                        currentAllowedAppsTarget = emptySet()
                        showCreateDialog = true
                    }
                },
                containerColor = CoralPrimary,
                contentColor = Color.White,
                shape = RoundedCornerShape(16.dp),
                elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 6.dp),
                modifier = Modifier.padding(bottom = 12.dp, end = 4.dp)
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_add),
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "SCHEDULE",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    letterSpacing = 0.5.sp
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (schedules.isEmpty()) {
                // Empty state matching Image 4
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Empty List",
                        color = Color.White.copy(alpha = 0.55f),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Normal
                    )
                }
            } else {
                // Schedules List matching Image 1
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    items(schedules, key = { it.id }) { schedule ->
                        FocusScheduleCard(
                            schedule = schedule,
                            onToggle = { isEnabled ->
                                if (!isEnabled && isAccountabilityLocked) {
                                    showAccountabilityGuardDialog = true
                                } else {
                                    FocusModeManager.toggleSchedule(context, schedule.id, isEnabled)
                                    refreshSchedules()
                                }
                            },
                            onDelete = {
                                if (isAccountabilityLocked) {
                                    showAccountabilityGuardDialog = true
                                } else {
                                    FocusModeManager.deleteSchedule(context, schedule.id)
                                    refreshSchedules()
                                }
                            },
                            onEditAllowedApps = {
                                editingSchedule = schedule
                                currentAllowedAppsTarget = schedule.allowedApps
                                onAllowedAppsSelectedCallback = { updatedApps ->
                                    val updated = schedule.copy(allowedApps = updatedApps)
                                    FocusModeManager.saveSchedule(context, updated)
                                    refreshSchedules()
                                }
                                showAppPickerDialog = true
                            }
                        )
                    }
                }
            }

            // Bottom Toast Pill matching Image 2
            AnimatedVisibility(
                visible = showLimitToast,
                enter = fadeIn() + slideInVertically(initialOffsetY = { it }),
                exit = fadeOut() + slideOutVertically(targetOffsetY = { it }),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 76.dp)
            ) {
                FreeLimitPillToast(
                    onDismiss = { showLimitToast = false },
                    onUpgradeClick = onUpgradeClick
                )
            }
        }
    }

    // Help Dialog matching Image 1
    if (showHelpDialog) {
        FocusHelpDialog(onDismiss = { showHelpDialog = false })
    }

    // Accountability Guard Dialog matching Image 3
    if (showAccountabilityGuardDialog) {
        val partnerTitle = when (PGuardAccessibilityService.accountabilityMode.lowercase()) {
            "friend" -> "Accountability Partner"
            "time_delay" -> "24-Hour Time Delay"
            "self" -> "Focus Commitment"
            else -> "Team"
        }
        FocusAccountabilityGuardDialog(
            partnerModeTitle = partnerTitle,
            onDismiss = { showAccountabilityGuardDialog = false }
        )
    }

    // Create Schedule Dialog
    if (showCreateDialog) {
        CreateScheduleDialog(
            initialSchedule = editingSchedule,
            selectedAllowedApps = currentAllowedAppsTarget,
            onDismiss = { showCreateDialog = false },
            onPickAllowedAppsClick = { currentSet ->
                currentAllowedAppsTarget = currentSet
                onAllowedAppsSelectedCallback = { updatedSet ->
                    currentAllowedAppsTarget = updatedSet
                }
                showAppPickerDialog = true
            },
            onSaveSchedule = { newSchedule ->
                FocusModeManager.saveSchedule(context, newSchedule)
                refreshSchedules()
                showCreateDialog = false
            }
        )
    }

    // Allowed Apps Picker Dialog
    if (showAppPickerDialog) {
        AllowedAppsPickerDialog(
            context = context,
            alreadySelectedPackages = currentAllowedAppsTarget,
            onDismiss = { showAppPickerDialog = false },
            onSaveSelection = { updatedList ->
                onAllowedAppsSelectedCallback?.invoke(updatedList)
                showAppPickerDialog = false
            }
        )
    }
}

@Composable
private fun FocusModeTopBar(
    onBackClick: () -> Unit,
    onHelpClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBackClick) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_arrow_back),
                        contentDescription = "Back",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Focus Mode",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            IconButton(onClick = onHelpClick) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_help_circle),
                    contentDescription = "Help",
                    tint = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

/**
 * Schedule Card matching Image 1:
 * - Time Range with duration: 00:00 - 05:05 (5 hr 5 min)
 * - Switch & Delete Trash icon
 * - Daily pill badge
 * - Allowed Apps (0) accordion
 */
@Composable
private fun FocusScheduleCard(
    schedule: FocusSchedule,
    onToggle: (Boolean) -> Unit,
    onDelete: () -> Unit,
    onEditAllowedApps: () -> Unit
) {
    var isAccordionExpanded by remember { mutableStateOf(false) }

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = CardBackground),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp)
        ) {
            // Row 1: Time Range + Toggle + Delete
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = schedule.formattedTimeRange,
                    color = CoralPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Switch(
                        checked = schedule.isEnabled,
                        onCheckedChange = onToggle,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = Color(0xFF4CAF50),
                            uncheckedThumbColor = Color.White,
                            uncheckedTrackColor = Color.White.copy(alpha = 0.2f)
                        )
                    )

                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_delete),
                            contentDescription = "Delete",
                            tint = Color.White.copy(alpha = 0.45f),
                            modifier = Modifier.size(19.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Row 2: "Daily" pill badge
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = Color(0xFF2A2A32)
            ) {
                Text(
                    text = if (schedule.repeatDaily) "Daily" else "Custom Days",
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))
            HorizontalDivider(color = Color.White.copy(alpha = 0.06f))
            Spacer(modifier = Modifier.height(12.dp))

            // Row 3: Allowed Apps accordion dropdown
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isAccordionExpanded = !isAccordionExpanded },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Allowed Apps (${schedule.allowedApps.size})",
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )

                Icon(
                    painter = painterResource(id = R.drawable.ic_chevron_right),
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.45f),
                    modifier = Modifier.size(16.dp)
                )
            }

            // Expanded Accordion details
            if (isAccordionExpanded) {
                Spacer(modifier = Modifier.height(10.dp))
                if (schedule.allowedApps.isEmpty()) {
                    Text(
                        text = "Only standard Call/SMS apps are allowed during this focus session.",
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    )
                } else {
                    Text(
                        text = "${schedule.allowedApps.size} apps will bypass blocking during focus time.",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 12.sp
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Edit Allowed Apps →",
                    color = CoralPrimary,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable(onClick = onEditAllowedApps)
                )
            }
        }
    }
}

/**
 * Picker dialog allowing user to search and check allowed apps for the schedule.
 */
@Composable
private fun AllowedAppsPickerDialog(
    context: Context,
    alreadySelectedPackages: Set<String>,
    onDismiss: () -> Unit,
    onSaveSelection: (Set<String>) -> Unit
) {
    val selected = remember { mutableStateListOf<String>().apply { addAll(alreadySelectedPackages) } }
    var apps by remember { mutableStateOf<List<InstalledAppItem>>(emptyList()) }
    var searchQuery by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            apps = queryDeviceLauncherApps(context)
        }
    }

    val filteredApps = remember(searchQuery, apps) {
        if (searchQuery.isBlank()) apps
        else apps.filter { it.name.contains(searchQuery, ignoreCase = true) || it.packageName.contains(searchQuery, ignoreCase = true) }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = CardBackground,
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
            modifier = Modifier
                .fillMaxWidth()
                .height(480.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(18.dp)
            ) {
                Text(
                    text = "Allowed Apps (${selected.size})",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = "These apps will bypass blocking during focus time.",
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.6f)
                )

                Spacer(modifier = Modifier.height(14.dp))

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredApps, key = { it.packageName }) { app ->
                        val isChecked = selected.contains(app.packageName)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (isChecked) selected.remove(app.packageName)
                                    else selected.add(app.packageName)
                                }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = app.name,
                                color = Color.White,
                                fontSize = 14.sp,
                                modifier = Modifier.weight(1f)
                            )
                            androidx.compose.material3.Checkbox(
                                checked = isChecked,
                                onCheckedChange = { check ->
                                    if (check) selected.add(app.packageName)
                                    else selected.remove(app.packageName)
                                },
                                colors = androidx.compose.material3.CheckboxDefaults.colors(
                                    checkedColor = CoralPrimary,
                                    uncheckedColor = Color.White.copy(alpha = 0.3f)
                                )
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    androidx.compose.material3.TextButton(onClick = onDismiss) {
                        Text("Cancel", color = Color.White.copy(alpha = 0.7f))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    androidx.compose.material3.Button(
                        onClick = { onSaveSelection(selected.toSet()) },
                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = CoralPrimary),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Apply", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
