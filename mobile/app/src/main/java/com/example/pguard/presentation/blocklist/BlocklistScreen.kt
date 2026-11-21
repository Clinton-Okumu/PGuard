package com.example.pguard.presentation.blocklist

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.os.Build
import android.widget.Toast
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.pguard.R
import com.example.pguard.engine.accessibility.BlockingPreferences
import com.example.pguard.engine.accessibility.PGuardAccessibilityService
import com.example.pguard.presentation.dashboard.AccountabilityMode
import com.example.pguard.presentation.dashboard.BlockingViewModel
import com.example.pguard.presentation.dashboard.FriendApprovalDialog
import com.example.pguard.presentation.dashboard.TimeDelayActiveBanner
import com.example.pguard.presentation.dashboard.TimeDelayConfirmDialog
import com.example.pguard.presentation.dashboard.TypingChallengeDialog
import com.example.pguard.presentation.dashboard.targetDisplayName
import com.example.pguard.presentation.theme.PGuardTheme
import com.example.pguard.presentation.theme.pguardColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class ListType {
    BLOCKLIST,
    WHITELIST
}

enum class ContentCategory {
    APPS,
    KEYWORDS_WEBSITES
}

data class InstalledAppItem(
    val packageName: String,
    val name: String,
    val icon: Drawable? = null
)

@Composable
fun BlocklistScreen(
    blockingViewModel: BlockingViewModel? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    val blockingUiState = blockingViewModel?.uiState?.collectAsState()?.value
    val activeFrictionMode = blockingUiState?.accountabilityMode
        ?: AccountabilityMode.fromCode(PGuardAccessibilityService.accountabilityMode)

    var selectedListType by remember { mutableStateOf(ListType.BLOCKLIST) }
    var selectedCategory by remember { mutableStateOf(ContentCategory.APPS) }

    // Blocklist state collections
    val blockedApps = remember { mutableStateListOf<InstalledAppItem>() }
    val blockedKeywords = remember { mutableStateListOf<String>() }

    // Whitelist state collections
    val whitelistedApps = remember { mutableStateListOf<InstalledAppItem>() }
    val whitelistedKeywords = remember { mutableStateListOf<String>() }

    // Dialog state
    var showAddAppDialog by remember { mutableStateOf(false) }
    var showAddKeywordDialog by remember { mutableStateOf(false) }
    var keywordInput by remember { mutableStateOf("") }

    // Friction Guard States
    var showTypingChallenge by remember { mutableStateOf(false) }
    var showTimeDelayConfirm by remember { mutableStateOf(false) }
    var showFriendApproval by remember { mutableStateOf(false) }
    var pendingFrictionAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var pendingTimeDelayTargetId by remember { mutableStateOf("") }
    var frictionTargetTitle by remember { mutableStateOf("Blocklist Item") }
    var localUnlockError by remember { mutableStateOf<String?>(null) }

    val currentApps = if (selectedListType == ListType.BLOCKLIST) blockedApps else whitelistedApps
    val currentKeywords = if (selectedListType == ListType.BLOCKLIST) blockedKeywords else whitelistedKeywords

    LaunchedEffect(Unit) {
        val pm = context.packageManager

        // 1. Load Blocked Apps
        val savedBlocked = PGuardAccessibilityService.getBlockedApps(context)
        savedBlocked.forEach { pkg ->
            if (blockedApps.none { it.packageName == pkg }) {
                val label = try {
                    val info = pm.getApplicationInfo(pkg, 0)
                    pm.getApplicationLabel(info).toString()
                } catch (_: Exception) {
                    pkg
                }
                val icon = try {
                    val info = pm.getApplicationInfo(pkg, 0)
                    pm.getApplicationIcon(info)
                } catch (_: Exception) {
                    null
                }
                blockedApps.add(InstalledAppItem(pkg, label, icon))
            }
        }

        // 2. Load Blocked Keywords / Websites
        val savedKeywords = PGuardAccessibilityService.getCustomBlockedKeywords(context)
        savedKeywords.forEach { kw ->
            if (!blockedKeywords.contains(kw)) {
                blockedKeywords.add(kw)
            }
        }

        // 3. Load Whitelisted Apps
        val savedWhitelistedApps = PGuardAccessibilityService.getWhitelistedApps(context)
        savedWhitelistedApps.forEach { pkg ->
            if (whitelistedApps.none { it.packageName == pkg }) {
                val label = try {
                    val info = pm.getApplicationInfo(pkg, 0)
                    pm.getApplicationLabel(info).toString()
                } catch (_: Exception) {
                    pkg
                }
                val icon = try {
                    val info = pm.getApplicationInfo(pkg, 0)
                    pm.getApplicationIcon(info)
                } catch (_: Exception) {
                    null
                }
                whitelistedApps.add(InstalledAppItem(pkg, label, icon))
            }
        }

        // 4. Load Whitelisted Keywords / Websites
        val savedWhitelistedKeywords = PGuardAccessibilityService.getWhitelistedKeywords(context)
        savedWhitelistedKeywords.forEach { kw ->
            if (!whitelistedKeywords.contains(kw)) {
                whitelistedKeywords.add(kw)
            }
        }
    }

    fun triggerProtectedRemove(targetId: String, itemTitle: String, action: () -> Unit) {
        if (selectedListType == ListType.WHITELIST) {
            // Whitelisting doesn't require any use of friction
            action()
            return
        }

        // Removing from Blocklist is guarded by the active friction method
        frictionTargetTitle = itemTitle
        when (activeFrictionMode) {
            AccountabilityMode.NONE -> {
                action()
            }
            AccountabilityMode.SELF -> {
                pendingFrictionAction = action
                showTypingChallenge = true
            }
            AccountabilityMode.TIME_DELAY -> {
                if (BlockingPreferences.isTimeDelayElapsed(context, targetId)) {
                    blockingViewModel?.cancelTimeDelay(context) ?: BlockingPreferences.clearTimeDelay(context)
                    action()
                } else {
                    pendingTimeDelayTargetId = targetId
                    pendingFrictionAction = null
                    showTimeDelayConfirm = true
                }
            }
            AccountabilityMode.FRIEND -> {
                val partner = blockingUiState?.partner
                val isLinked = blockingUiState?.isPartnerLinked ?: false
                if (partner != null && isLinked) {
                    localUnlockError = null
                    blockingViewModel?.clearUnlockErrors()
                    pendingFrictionAction = action
                    showFriendApproval = true
                } else {
                    action()
                }
            }
        }
    }

    val primary = MaterialTheme.colorScheme.primary

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 96.dp)
        ) {
            // 1. Top Mode Selector (Blocklist vs Whitelist)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .height(48.dp)
                    .background(MaterialTheme.colorScheme.surfaceContainerLow, MaterialTheme.shapes.medium)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.medium)
                    .padding(4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(MaterialTheme.shapes.small)
                        .background(if (selectedListType == ListType.BLOCKLIST) primary else Color.Transparent)
                        .clickable { selectedListType = ListType.BLOCKLIST },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Blocklist",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (selectedListType == ListType.BLOCKLIST) FontWeight.Bold else FontWeight.Medium,
                        color = if (selectedListType == ListType.BLOCKLIST) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(MaterialTheme.shapes.small)
                        .background(if (selectedListType == ListType.WHITELIST) primary else Color.Transparent)
                        .clickable { selectedListType = ListType.WHITELIST },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Whitelist",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (selectedListType == ListType.WHITELIST) FontWeight.Bold else FontWeight.Medium,
                        color = if (selectedListType == ListType.WHITELIST) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }

            // 2. Sub-category Selector (Apps vs Keywords/Websites)
            Row(
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    onClick = { selectedCategory = ContentCategory.APPS },
                    shape = MaterialTheme.shapes.small,
                    color = if (selectedCategory == ContentCategory.APPS) primary else MaterialTheme.colorScheme.surfaceContainerLow,
                    border = BorderStroke(
                        1.dp,
                        if (selectedCategory == ContentCategory.APPS) primary else MaterialTheme.colorScheme.outlineVariant
                    ),
                    modifier = Modifier.height(36.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_nav_blocking),
                            contentDescription = null,
                            tint = if (selectedCategory == ContentCategory.APPS) {
                                MaterialTheme.colorScheme.onPrimary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier.size(15.dp)
                        )
                        Text(
                            text = "Apps (${currentApps.size})",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (selectedCategory == ContentCategory.APPS) {
                                MaterialTheme.colorScheme.onPrimary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                }

                Surface(
                    onClick = { selectedCategory = ContentCategory.KEYWORDS_WEBSITES },
                    shape = MaterialTheme.shapes.small,
                    color = if (selectedCategory == ContentCategory.KEYWORDS_WEBSITES) primary else MaterialTheme.colorScheme.surfaceContainerLow,
                    border = BorderStroke(
                        1.dp,
                        if (selectedCategory == ContentCategory.KEYWORDS_WEBSITES) primary else MaterialTheme.colorScheme.outlineVariant
                    ),
                    modifier = Modifier.height(36.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_globe),
                            contentDescription = null,
                            tint = if (selectedCategory == ContentCategory.KEYWORDS_WEBSITES) {
                                MaterialTheme.colorScheme.onPrimary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier.size(15.dp)
                        )
                        Text(
                            text = "Keywords / Websites (${currentKeywords.size})",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (selectedCategory == ContentCategory.KEYWORDS_WEBSITES) {
                                MaterialTheme.colorScheme.onPrimary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            val pendingTarget = blockingUiState?.timeDelayTarget ?: BlockingPreferences.timeDelayTarget
            val pendingUnlockAt = blockingUiState?.timeDelayUnlockAt ?: BlockingPreferences.timeDelayUnlockAt
            if (pendingUnlockAt > 0L && pendingTarget.startsWith("BLOCKLIST_")) {
                TimeDelayActiveBanner(
                    targetFeatureTitle = targetDisplayName(pendingTarget),
                    unlockAt = pendingUnlockAt,
                    onCancel = {
                        blockingViewModel?.cancelTimeDelay(context) ?: BlockingPreferences.clearTimeDelay(context)
                    },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }

            // 3. Main Content: Empty State or Active List
            val isEmpty = if (selectedCategory == ContentCategory.APPS) currentApps.isEmpty() else currentKeywords.isEmpty()

            if (isEmpty) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(32.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(MaterialTheme.shapes.medium)
                                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.medium),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter = painterResource(
                                    id = if (selectedCategory == ContentCategory.APPS) R.drawable.ic_nav_blocking else R.drawable.ic_globe
                                ),
                                contentDescription = null,
                                tint = primary.copy(alpha = 0.85f),
                                modifier = Modifier.size(28.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = if (selectedCategory == ContentCategory.APPS) {
                                if (selectedListType == ListType.BLOCKLIST) "No Blocked Apps" else "No Whitelisted Apps"
                            } else {
                                if (selectedListType == ListType.BLOCKLIST) "No Blocked Keywords or Websites" else "No Whitelisted Keywords or Websites"
                            },
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = if (selectedCategory == ContentCategory.APPS) {
                                "Tap below to choose apps to ${if (selectedListType == ListType.BLOCKLIST) "block" else "whitelist"}."
                            } else {
                                "Add domains or keywords to prevent access."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                        Button(
                            onClick = {
                                if (selectedCategory == ContentCategory.APPS) {
                                    showAddAppDialog = true
                                } else {
                                    keywordInput = ""
                                    showAddKeywordDialog = true
                                }
                            },
                            shape = MaterialTheme.shapes.small,
                            colors = ButtonDefaults.buttonColors(containerColor = primary),
                            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp)
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_add),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (selectedCategory == ContentCategory.APPS) "Add Apps" else "Add Keyword",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(horizontal = 16.dp)
                ) {
                    if (selectedCategory == ContentCategory.APPS) {
                        items(currentApps, key = { it.packageName }) { app ->
                            AppListItem(
                                app = app,
                                onRemove = {
                                    triggerProtectedRemove("BLOCKLIST_APP:${app.packageName}", app.name) {
                                        if (selectedListType == ListType.BLOCKLIST) {
                                            blockedApps.remove(app)
                                            PGuardAccessibilityService.removeBlockedApp(context, app.packageName)
                                        } else {
                                            whitelistedApps.remove(app)
                                            PGuardAccessibilityService.removeWhitelistedApp(context, app.packageName)
                                        }
                                    }
                                }
                            )
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        }
                    } else {
                        items(currentKeywords, key = { it }) { keyword ->
                            KeywordListItem(
                                keyword = keyword,
                                onRemove = {
                                    triggerProtectedRemove("BLOCKLIST_KW:$keyword", keyword) {
                                        if (selectedListType == ListType.BLOCKLIST) {
                                            blockedKeywords.remove(keyword)
                                            PGuardAccessibilityService.removeCustomBlockedKeyword(context, keyword)
                                        } else {
                                            whitelistedKeywords.remove(keyword)
                                            PGuardAccessibilityService.removeWhitelistedKeyword(context, keyword)
                                        }
                                    }
                                }
                            )
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        }
                    }
                }
            }
        }

        // 4. Floating Action Button (+)
        FloatingActionButton(
            onClick = {
                if (selectedCategory == ContentCategory.APPS) {
                    showAddAppDialog = true
                } else {
                    keywordInput = ""
                    showAddKeywordDialog = true
                }
            },
            shape = MaterialTheme.shapes.medium,
            containerColor = primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 20.dp, bottom = 20.dp)
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_add),
                contentDescription = "Add",
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(24.dp)
            )
        }
    }

    // Add Keyword / Website Dialog
    if (showAddKeywordDialog) {
        val typeTitle = if (selectedListType == ListType.BLOCKLIST) "Blocklist" else "Whitelist"
        AlertDialog(
            onDismissRequest = { showAddKeywordDialog = false },
            title = {
                Text(
                    text = "Add to $typeTitle",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            text = {
                Column {
                    Text(
                        text = "Enter a website domain or keyword (e.g. reddit.com, casino, adult):",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    OutlinedTextField(
                        value = keywordInput,
                        onValueChange = { keywordInput = it },
                        placeholder = { Text("domain.com or keyword") },
                        singleLine = true,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline
                        )
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val trimmed = keywordInput.trim().lowercase()
                        if (trimmed.isEmpty()) return@Button

                        if (selectedListType == ListType.WHITELIST) {
                            val isBlocked = blockedKeywords.any { it.equals(trimmed, ignoreCase = true) }
                            if (isBlocked) {
                                Toast.makeText(
                                    context,
                                    "Cannot whitelist \"$trimmed\": it is currently in your Blocklist. Remove it from Blocklist first using friction.",
                                    Toast.LENGTH_LONG
                                ).show()
                                return@Button
                            }
                            if (!whitelistedKeywords.contains(trimmed)) {
                                whitelistedKeywords.add(trimmed)
                                PGuardAccessibilityService.addWhitelistedKeyword(context, trimmed)
                            }
                        } else {
                            if (whitelistedKeywords.contains(trimmed)) {
                                whitelistedKeywords.remove(trimmed)
                                PGuardAccessibilityService.removeWhitelistedKeyword(context, trimmed)
                            }
                            if (!blockedKeywords.contains(trimmed)) {
                                blockedKeywords.add(trimmed)
                                PGuardAccessibilityService.addCustomBlockedKeyword(context, trimmed)
                            }
                        }
                        showAddKeywordDialog = false
                    },
                    shape = MaterialTheme.shapes.small,
                    colors = ButtonDefaults.buttonColors(containerColor = primary),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp)
                ) {
                    Text("Add", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showAddKeywordDialog = false },
                    shape = MaterialTheme.shapes.small
                ) {
                    Text("Cancel", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            shape = MaterialTheme.shapes.large
        )
    }

    // Add Apps Picker Dialog
    if (showAddAppDialog) {
        AddAppPickerDialog(
            context = context,
            alreadySelectedPackages = currentApps.map { it.packageName }.toSet(),
            blockedPackages = blockedApps.map { it.packageName }.toSet(),
            isWhitelistMode = selectedListType == ListType.WHITELIST,
            listTitle = if (selectedListType == ListType.BLOCKLIST) "Block Apps" else "Whitelist Apps",
            onDismiss = { showAddAppDialog = false },
            onAddApps = { selectedApps ->
                selectedApps.forEach { app ->
                    if (selectedListType == ListType.BLOCKLIST) {
                        // If it was in whitelist, remove it because blocklist takes precedence
                        val inWhitelist = whitelistedApps.find { it.packageName == app.packageName }
                        if (inWhitelist != null) {
                            whitelistedApps.remove(inWhitelist)
                            PGuardAccessibilityService.removeWhitelistedApp(context, app.packageName)
                        }
                        if (blockedApps.none { it.packageName == app.packageName }) {
                            blockedApps.add(app)
                            PGuardAccessibilityService.addBlockedApp(context, app.packageName, app.name)
                        }
                    } else {
                        // Whitelist mode: blocked apps are prohibited from being whitelisted
                        val isBlocked = blockedApps.any { it.packageName == app.packageName }
                        if (!isBlocked && whitelistedApps.none { it.packageName == app.packageName }) {
                            whitelistedApps.add(app)
                            PGuardAccessibilityService.addWhitelistedApp(context, app.packageName)
                        }
                    }
                }
                showAddAppDialog = false
            }
        )
    }

    // Friction Guard Dialog 1: Myself Typing Challenge
    if (showTypingChallenge) {
        TypingChallengeDialog(
            targetFeatureTitle = frictionTargetTitle,
            onSuccess = {
                showTypingChallenge = false
                pendingFrictionAction?.invoke()
                pendingFrictionAction = null
            },
            onDismiss = {
                showTypingChallenge = false
                pendingFrictionAction = null
            }
        )
    }

    // Friction Guard Dialog 2: 24-Hour Time Delay
    if (showTimeDelayConfirm) {
        TimeDelayConfirmDialog(
            targetFeatureTitle = frictionTargetTitle,
            onConfirm = {
                showTimeDelayConfirm = false
                if (pendingTimeDelayTargetId.isNotBlank()) {
                    if (blockingViewModel != null) {
                        blockingViewModel.startTimeDelayForTarget(pendingTimeDelayTargetId, context)
                    } else {
                        val duration = BlockingPreferences.DEFAULT_TIME_DELAY_MS
                        BlockingPreferences.setTimeDelay(context, System.currentTimeMillis() + duration, pendingTimeDelayTargetId, duration)
                    }
                }
                pendingTimeDelayTargetId = ""
                pendingFrictionAction = null
            },
            onDismiss = {
                showTimeDelayConfirm = false
                pendingTimeDelayTargetId = ""
                pendingFrictionAction = null
            }
        )
    }

    // Friction Guard Dialog 3: Accountability Partner Approval
    if (showFriendApproval) {
        FriendApprovalDialog(
            targetFeatureTitle = frictionTargetTitle,
            partnerEmail = blockingUiState?.partner?.partnerEmail ?: "",
            onRequestCode = {
                localUnlockError = null
                blockingViewModel?.requestPartnerApprovalCode(frictionTargetTitle)
            },
            onSubmitCode = { code ->
                if (blockingViewModel != null) {
                    blockingViewModel.verifyExternalUnlockCode(
                        enteredCode = code,
                        onSuccess = {
                            showFriendApproval = false
                            localUnlockError = null
                            pendingFrictionAction?.invoke()
                            pendingFrictionAction = null
                        },
                        onError = { err ->
                            localUnlockError = err
                        }
                    )
                } else {
                    localUnlockError = "Accountability verification service is unavailable."
                }
            },
            onDismiss = {
                showFriendApproval = false
                localUnlockError = null
                blockingViewModel?.clearUnlockErrors()
                pendingFrictionAction = null
            },
            isVerifying = blockingUiState?.isVerifyingUnlockCode ?: false,
            errorMessage = localUnlockError ?: blockingUiState?.unlockCodeError,
            requestError = blockingUiState?.unlockRequestError
        )
    }
}

@Composable
private fun AppListItem(
    app: InstalledAppItem,
    onRemove: () -> Unit
) {
    val primary = MaterialTheme.colorScheme.primary
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .weight(1f)
                .padding(end = 12.dp)
        ) {
            val bitmap = remember(app.packageName) { app.icon?.toComposeBitmap() }
            if (bitmap != null) {
                Image(
                    bitmap = bitmap,
                    contentDescription = app.name,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(MaterialTheme.shapes.small)
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(MaterialTheme.shapes.small)
                        .background(primary.copy(alpha = 0.14f))
                        .border(1.dp, primary.copy(alpha = 0.25f), MaterialTheme.shapes.small),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_nav_blocking),
                        contentDescription = null,
                        tint = primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column {
                Text(
                    text = app.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = app.packageName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        IconButton(onClick = onRemove) {
            Icon(
                painter = painterResource(id = R.drawable.ic_close),
                contentDescription = "Remove",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun KeywordListItem(
    keyword: String,
    onRemove: () -> Unit
) {
    val infoColor = MaterialTheme.pguardColors.info
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .weight(1f)
                .padding(end = 12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(MaterialTheme.shapes.small)
                    .background(infoColor.copy(alpha = 0.14f))
                    .border(1.dp, infoColor.copy(alpha = 0.25f), MaterialTheme.shapes.small),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_globe),
                    contentDescription = null,
                    tint = infoColor,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Text(
                text = keyword,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        IconButton(onClick = onRemove) {
            Icon(
                painter = painterResource(id = R.drawable.ic_close),
                contentDescription = "Remove",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun AddAppPickerDialog(
    context: Context,
    alreadySelectedPackages: Set<String>,
    blockedPackages: Set<String> = emptySet(),
    isWhitelistMode: Boolean = false,
    listTitle: String,
    onDismiss: () -> Unit,
    onAddApps: (List<InstalledAppItem>) -> Unit
) {
    var installedApps by remember { mutableStateOf<List<InstalledAppItem>>(emptyList()) }
    var searchQuery by remember { mutableStateOf("") }
    val selectedPackages = remember { mutableStateListOf<String>() }
    var isLoading by remember { mutableStateOf(true) }
    val primary = MaterialTheme.colorScheme.primary

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val apps = queryDeviceLauncherApps(context)
            installedApps = apps
            isLoading = false
        }
    }

    val filteredApps = remember(searchQuery, installedApps) {
        if (searchQuery.isBlank()) {
            installedApps
        } else {
            installedApps.filter {
                it.name.contains(searchQuery, ignoreCase = true) ||
                        it.packageName.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = listTitle,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        },
        text = {
            Column(modifier = Modifier.height(420.dp)) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search installed apps...") },
                    leadingIcon = {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_search),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    singleLine = true,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline
                    )
                )

                Spacer(modifier = Modifier.height(12.dp))

                if (isLoading) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = "Loading installed apps...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else if (filteredApps.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = "No apps found",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(filteredApps, key = { it.packageName }) { app ->
                            val isAlreadyAdded = alreadySelectedPackages.contains(app.packageName)
                            val isBlockedConflict = isWhitelistMode && blockedPackages.contains(app.packageName)
                            val isChecked = selectedPackages.contains(app.packageName) || isAlreadyAdded

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        if (isBlockedConflict) {
                                            Toast.makeText(
                                                context,
                                                "${app.name} is in your Blocklist. Remove it from Blocklist first using your friction method.",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        } else if (!isAlreadyAdded) {
                                            if (selectedPackages.contains(app.packageName)) {
                                                selectedPackages.remove(app.packageName)
                                            } else {
                                                selectedPackages.add(app.packageName)
                                            }
                                        }
                                    }
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val bitmap = remember(app.packageName) { app.icon?.toComposeBitmap() }
                                if (bitmap != null) {
                                    Image(
                                        bitmap = bitmap,
                                        contentDescription = app.name,
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(MaterialTheme.shapes.small)
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(MaterialTheme.shapes.small)
                                            .background(primary.copy(alpha = 0.14f))
                                            .border(1.dp, primary.copy(alpha = 0.25f), MaterialTheme.shapes.small),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            painter = painterResource(id = R.drawable.ic_nav_blocking),
                                            contentDescription = null,
                                            tint = primary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = app.name,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = if (isAlreadyAdded || isBlockedConflict) {
                                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
                                        } else {
                                            MaterialTheme.colorScheme.onSurface
                                        }
                                    )
                                    Text(
                                        text = when {
                                            isBlockedConflict -> "In Blocklist (cannot whitelist)"
                                            isAlreadyAdded -> "Already in list"
                                            else -> app.packageName
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = when {
                                            isBlockedConflict -> primary
                                            isAlreadyAdded -> primary.copy(alpha = 0.8f)
                                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                                        }
                                    )
                                }

                                Checkbox(
                                    checked = if (isBlockedConflict) false else isChecked,
                                    enabled = !isAlreadyAdded && !isBlockedConflict,
                                    onCheckedChange = { checked ->
                                        if (!isBlockedConflict) {
                                            if (checked) selectedPackages.add(app.packageName)
                                            else selectedPackages.remove(app.packageName)
                                        }
                                    },
                                    colors = CheckboxDefaults.colors(
                                        checkedColor = primary,
                                        checkmarkColor = MaterialTheme.colorScheme.onPrimary
                                    )
                                )
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val toAdd = installedApps.filter {
                        selectedPackages.contains(it.packageName) && (!isWhitelistMode || !blockedPackages.contains(it.packageName))
                    }
                    onAddApps(toAdd)
                },
                shape = MaterialTheme.shapes.small,
                colors = ButtonDefaults.buttonColors(containerColor = primary),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp)
            ) {
                Text(
                    text = "Add (${selectedPackages.size})",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                shape = MaterialTheme.shapes.small
            ) {
                Text("Cancel", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.large
    )
}

internal fun queryDeviceLauncherApps(context: Context): List<InstalledAppItem> {
    val pm = context.packageManager
    val intent = Intent(Intent.ACTION_MAIN, null).apply {
        addCategory(Intent.CATEGORY_LAUNCHER)
    }

    val resolveInfos = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0))
    } else {
        @Suppress("DEPRECATION")
        pm.queryIntentActivities(intent, 0)
    }

    val list = resolveInfos.mapNotNull { resolveInfo ->
        val pkg = resolveInfo.activityInfo.packageName
        if (pkg != context.packageName) {
            val label = resolveInfo.loadLabel(pm).toString()
            val icon = resolveInfo.loadIcon(pm)
            InstalledAppItem(pkg, label, icon)
        } else null
    }.distinctBy { it.packageName }.sortedBy { it.name.lowercase() }

    // Fallback if querying yields few/no apps in restricted environment
    return if (list.isNotEmpty()) {
        list
    } else {
        listOf(
            InstalledAppItem("com.google.android.youtube", "YouTube"),
            InstalledAppItem("com.instagram.android", "Instagram"),
            InstalledAppItem("com.zhiliaoapp.musically", "TikTok"),
            InstalledAppItem("com.android.chrome", "Chrome"),
            InstalledAppItem("com.whatsapp", "WhatsApp"),
            InstalledAppItem("com.netflix.mediaclient", "Netflix")
        )
    }
}

private fun Drawable.toComposeBitmap(width: Int = 96, height: Int = 96): ImageBitmap? {
    return try {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        setBounds(0, 0, canvas.width, canvas.height)
        draw(canvas)
        bitmap.asImageBitmap()
    } catch (e: Exception) {
        null
    }
}

@Preview(showBackground = true)
@Composable
private fun BlocklistScreenPreview() {
    PGuardTheme {
        BlocklistScreen()
    }
}
