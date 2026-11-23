package com.example.pguard.presentation.blocklist

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.os.Build
import android.provider.Telephony
import android.telecom.TelecomManager
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
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.pguard.R
import com.example.pguard.engine.accessibility.BlockingPreferences
import com.example.pguard.engine.accessibility.BlocklistRuleValidator
import com.example.pguard.engine.accessibility.PGuardAccessibilityService
import com.example.pguard.presentation.accountability.AccountabilityGuardDialogsHost
import com.example.pguard.presentation.accountability.rememberAccountabilityGuard
import com.example.pguard.presentation.dashboard.AccountabilityMode
import com.example.pguard.presentation.dashboard.AlertActionCard
import com.example.pguard.presentation.dashboard.BlockingViewModel
import com.example.pguard.presentation.dashboard.TimeDelayActiveBanner
import com.example.pguard.presentation.dashboard.openAccessibilitySettings
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
    blocklistViewModel: BlocklistViewModel? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val blockingUiState = blockingViewModel?.uiState?.collectAsState()?.value
    val blocklistUiState = blocklistViewModel?.uiState?.collectAsState()?.value
    val blocklistRevision by BlockingPreferences.blocklistRevision.collectAsState()
    val accountabilityGuard = rememberAccountabilityGuard(blockingViewModel)

    var selectedListType by remember { mutableStateOf(ListType.BLOCKLIST) }
    var selectedCategory by remember { mutableStateOf(ContentCategory.APPS) }
    var listSearchQuery by remember { mutableStateOf("") }
    var isAccessibilityEnabled by remember { mutableStateOf(PGuardAccessibilityService.isEnabled(context)) }

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
    var keywordError by remember { mutableStateOf<String?>(null) }

    val currentApps = if (selectedListType == ListType.BLOCKLIST) blockedApps else whitelistedApps
    val currentKeywords = if (selectedListType == ListType.BLOCKLIST) blockedKeywords else whitelistedKeywords

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isAccessibilityEnabled = PGuardAccessibilityService.isEnabled(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(blocklistUiState?.items, blocklistRevision) {
        val loadedBlockedApps = withContext(Dispatchers.IO) {
            val pm = context.packageManager
            PGuardAccessibilityService.getBlockedApps(context).map { pkg ->
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
                InstalledAppItem(pkg, label, icon)
            }.sortedBy { it.name.lowercase() }
        }
        blockedApps.clear()
        blockedApps.addAll(loadedBlockedApps)

        val savedKeywords = PGuardAccessibilityService.getCustomBlockedKeywords(context).sorted()
        blockedKeywords.clear()
        blockedKeywords.addAll(savedKeywords)

        val loadedWhitelistedApps = withContext(Dispatchers.IO) {
            val pm = context.packageManager
            PGuardAccessibilityService.getWhitelistedApps(context).map { pkg ->
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
                InstalledAppItem(pkg, label, icon)
            }.sortedBy { it.name.lowercase() }
        }
        whitelistedApps.clear()
        whitelistedApps.addAll(loadedWhitelistedApps)

        val savedWhitelistedKeywords = PGuardAccessibilityService.getWhitelistedKeywords(context).sorted()
        whitelistedKeywords.clear()
        whitelistedKeywords.addAll(savedWhitelistedKeywords)
    }

    fun triggerProtectedRemove(targetId: String, itemTitle: String, action: () -> Unit) {
        if (selectedListType == ListType.WHITELIST) {
            // Removing trust exceptions is always frictionless
            action()
            return
        }
        accountabilityGuard.guard(targetId = targetId, targetTitle = itemTitle, action = action)
    }

    val primary = MaterialTheme.colorScheme.primary
    val blockUnsupportedBrowsers = blockingUiState?.settings?.blockUnsupportedBrowsers
        ?: BlockingPreferences.isUnsupportedBrowsersBlockingEnabled
    val blockAdultContent = blockingUiState?.settings?.blockAdultContent
        ?: BlockingPreferences.isAdultBlockingEnabled

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
                        .clickable {
                            selectedListType = ListType.BLOCKLIST
                            listSearchQuery = ""
                        },
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
                        .clickable {
                            selectedListType = ListType.WHITELIST
                            listSearchQuery = ""
                        },
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
                    onClick = {
                        selectedCategory = ContentCategory.APPS
                        listSearchQuery = ""
                    },
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
                    onClick = {
                        selectedCategory = ContentCategory.KEYWORDS_WEBSITES
                        listSearchQuery = ""
                    },
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

            if (!isAccessibilityEnabled) {
                AlertActionCard(
                    title = "Accessibility is OFF",
                    subtitle = "App and browser blocklists require PGuard Accessibility Service.",
                    actionLabel = "Turn ON",
                    trailingIcon = R.drawable.ic_chevron_right,
                    onAction = { openAccessibilitySettings(context) },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }

            val pendingTarget = blockingUiState?.timeDelayTarget ?: BlockingPreferences.timeDelayTarget
            val pendingUnlockAt = blockingUiState?.timeDelayUnlockAt ?: BlockingPreferences.timeDelayUnlockAt
            val serverReady = blockingUiState?.isTimeDelayServerReady ?: false
            if (pendingUnlockAt > 0L && (pendingTarget.startsWith("BLOCKLIST_") || pendingTarget.startsWith("WHITELIST_"))) {
                TimeDelayActiveBanner(
                    targetFeatureTitle = targetDisplayName(pendingTarget),
                    unlockAt = pendingUnlockAt,
                    isServerReady = serverReady,
                    onCancel = {
                        blockingViewModel?.cancelTimeDelay(context) ?: BlockingPreferences.clearTimeDelay(context)
                    },
                    onCompleteUnlock = {
                        if (blockingViewModel != null) {
                            blockingViewModel.completeReadyTimeDelay(context)
                        } else if (BlockingPreferences.isTimeDelayElapsed(context, pendingTarget)) {
                            BlockingPreferences.clearTimeDelay(context)
                            when {
                                pendingTarget.startsWith("BLOCKLIST_APP:") -> {
                                    val pkg = pendingTarget.removePrefix("BLOCKLIST_APP:")
                                    blockedApps.removeAll { it.packageName == pkg }
                                    if (blocklistViewModel != null) {
                                        blocklistViewModel.removeBlockedApp(context, pkg)
                                    } else {
                                        PGuardAccessibilityService.removeBlockedApp(context, pkg)
                                    }
                                }
                                pendingTarget.startsWith("BLOCKLIST_KW:") -> {
                                    val kw = pendingTarget.removePrefix("BLOCKLIST_KW:")
                                    blockedKeywords.removeAll { it.equals(kw, ignoreCase = true) }
                                    if (blocklistViewModel != null) {
                                        blocklistViewModel.removeBlockedKeywordOrWebsite(context, kw)
                                    } else {
                                        PGuardAccessibilityService.removeCustomBlockedKeyword(context, kw)
                                    }
                                }
                                pendingTarget.startsWith("WHITELIST_ADD_APP:") -> {
                                    pendingTarget.removePrefix("WHITELIST_ADD_APP:")
                                        .split(',')
                                        .filter { it.isNotBlank() }
                                        .forEach { pkg -> PGuardAccessibilityService.addWhitelistedApp(context, pkg.trim()) }
                                }
                                pendingTarget.startsWith("WHITELIST_ADD_KW:") -> {
                                    PGuardAccessibilityService.addWhitelistedKeyword(
                                        context,
                                        pendingTarget.removePrefix("WHITELIST_ADD_KW:")
                                    )
                                }
                            }
                        }
                    },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }

            // 3. Main Content: Empty State or Searchable Active List
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
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = when {
                                selectedCategory == ContentCategory.APPS && selectedListType == ListType.BLOCKLIST ->
                                    "Tap below to choose apps to block."
                                selectedCategory == ContentCategory.APPS && selectedListType == ListType.WHITELIST ->
                                    "Tap below to choose trusted apps to always allow."
                                selectedListType == ListType.BLOCKLIST ->
                                    "Add domains or keywords to block access."
                                else ->
                                    "Add trusted domains or keywords to always allow."
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
                                    keywordError = null
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
                                text = if (selectedCategory == ContentCategory.APPS) "Add Apps" else "Add Website / Keyword",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    }
                }
            } else {
                OutlinedTextField(
                    value = listSearchQuery,
                    onValueChange = { listSearchQuery = it },
                    placeholder = {
                        Text(
                            if (selectedCategory == ContentCategory.APPS) "Filter apps..." else "Filter keywords or websites..."
                        )
                    },
                    leadingIcon = {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_search),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    trailingIcon = if (listSearchQuery.isNotEmpty()) {
                        {
                            IconButton(onClick = { listSearchQuery = "" }) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_close),
                                    contentDescription = "Clear filter",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    } else null,
                    singleLine = true,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline
                    )
                )

                val visibleApps = remember(listSearchQuery, currentApps.toList()) {
                    if (listSearchQuery.isBlank()) currentApps.toList()
                    else currentApps.filter {
                        it.name.contains(listSearchQuery, ignoreCase = true) ||
                            it.packageName.contains(listSearchQuery, ignoreCase = true)
                    }
                }
                val visibleKeywords = remember(listSearchQuery, currentKeywords.toList()) {
                    if (listSearchQuery.isBlank()) currentKeywords.toList()
                    else currentKeywords.filter { it.contains(listSearchQuery, ignoreCase = true) }
                }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(horizontal = 16.dp)
                ) {
                    if (selectedCategory == ContentCategory.APPS) {
                        items(visibleApps, key = { it.packageName }) { app ->
                            AppListItem(
                                app = app,
                                onRemove = {
                                    triggerProtectedRemove("BLOCKLIST_APP:${app.packageName}", app.name) {
                                        if (selectedListType == ListType.BLOCKLIST) {
                                            blockedApps.remove(app)
                                            if (blocklistViewModel != null) {
                                                blocklistViewModel.removeBlockedApp(context, app.packageName)
                                            } else {
                                                PGuardAccessibilityService.removeBlockedApp(context, app.packageName)
                                            }
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
                        items(visibleKeywords, key = { it }) { keyword ->
                            KeywordListItem(
                                keyword = keyword,
                                onRemove = {
                                    triggerProtectedRemove("BLOCKLIST_KW:$keyword", keyword) {
                                        if (selectedListType == ListType.BLOCKLIST) {
                                            blockedKeywords.remove(keyword)
                                            if (blocklistViewModel != null) {
                                                blocklistViewModel.removeBlockedKeywordOrWebsite(context, keyword)
                                            } else {
                                                PGuardAccessibilityService.removeCustomBlockedKeyword(context, keyword)
                                            }
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
                    keywordError = null
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
        val isWhitelist = selectedListType == ListType.WHITELIST
        val typeTitle = if (isWhitelist) "Whitelist" else "Blocklist"
        val previewNormalized = remember(keywordInput) {
            val trimmed = keywordInput.trim()
            if (trimmed.isEmpty()) "" else BlocklistRuleValidator.normalizeWebsiteOrKeyword(trimmed).first
        }

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
                        text = if (isWhitelist) {
                            "Enter a safe website domain or keyword to allow (minimum 3 characters):"
                        } else {
                            "Enter a website domain or keyword to block (e.g. reddit.com, gambling):"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    OutlinedTextField(
                        value = keywordInput,
                        onValueChange = {
                            keywordInput = it
                            keywordError = null
                        },
                        placeholder = { Text("domain.com or keyword") },
                        singleLine = true,
                        isError = !keywordError.isNullOrEmpty(),
                        supportingText = when {
                            !keywordError.isNullOrEmpty() -> {
                                { Text(text = keywordError!!, color = MaterialTheme.colorScheme.error) }
                            }
                            previewNormalized.isNotEmpty() &&
                                previewNormalized != keywordInput.trim().lowercase() -> {
                                {
                                    Text(
                                        text = "Will save as: $previewNormalized",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            else -> null
                        },
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                            errorBorderColor = MaterialTheme.colorScheme.error
                        )
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val result = BlocklistRuleValidator.validateRule(
                            raw = keywordInput,
                            isWhitelist = isWhitelist,
                            existingBlockedRules = blockedKeywords,
                            existingWhitelistedRules = whitelistedKeywords,
                            blockAdultContent = blockAdultContent
                        )
                        when (result) {
                            is BlocklistRuleValidator.RuleValidationResult.Invalid -> {
                                keywordError = result.error
                            }
                            is BlocklistRuleValidator.RuleValidationResult.Valid -> {
                                val normalized = result.normalizedValue
                                showAddKeywordDialog = false
                                if (isWhitelist) {
                                    accountabilityGuard.guard(
                                        targetId = "WHITELIST_ADD_KW:$normalized",
                                        targetTitle = "Whitelist $normalized"
                                    ) {
                                        if (whitelistedKeywords.none { it.equals(normalized, ignoreCase = true) }) {
                                            whitelistedKeywords.add(normalized)
                                        }
                                        PGuardAccessibilityService.addWhitelistedKeyword(context, normalized)
                                    }
                                } else {
                                    val matchingWhitelisted = whitelistedKeywords.filter {
                                        BlocklistRuleValidator.normalizeWebsiteOrKeyword(it).first
                                            .equals(normalized, ignoreCase = true)
                                    }
                                    matchingWhitelisted.forEach { w ->
                                        whitelistedKeywords.remove(w)
                                        PGuardAccessibilityService.removeWhitelistedKeyword(context, w)
                                    }
                                    if (blockedKeywords.none { it.equals(normalized, ignoreCase = true) }) {
                                        blockedKeywords.add(normalized)
                                    }
                                    if (blocklistViewModel != null) {
                                        blocklistViewModel.addBlockedKeywordOrWebsite(context, normalized)
                                    } else {
                                        PGuardAccessibilityService.addCustomBlockedKeyword(context, normalized)
                                    }
                                }
                            }
                        }
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
        val isWhitelistMode = selectedListType == ListType.WHITELIST
        AddAppPickerDialog(
            context = context,
            alreadySelectedPackages = currentApps.map { it.packageName }.toSet(),
            blockedPackages = blockedApps.map { it.packageName }.toSet(),
            isWhitelistMode = isWhitelistMode,
            blockUnsupportedBrowsersEnabled = blockUnsupportedBrowsers,
            listTitle = if (!isWhitelistMode) "Block Apps" else "Whitelist Apps",
            onDismiss = { showAddAppDialog = false },
            onAddApps = { selectedApps ->
                if (selectedApps.isEmpty()) {
                    showAddAppDialog = false
                    return@AddAppPickerDialog
                }

                if (!isWhitelistMode) {
                    selectedApps.forEach { app ->
                        val inWhitelist = whitelistedApps.find { it.packageName == app.packageName }
                        if (inWhitelist != null) {
                            whitelistedApps.remove(inWhitelist)
                            PGuardAccessibilityService.removeWhitelistedApp(context, app.packageName)
                        }
                        if (blockedApps.none { it.packageName == app.packageName }) {
                            blockedApps.add(app)
                            if (blocklistViewModel != null) {
                                blocklistViewModel.addBlockedApp(context, app.packageName, app.name)
                            } else {
                                PGuardAccessibilityService.addBlockedApp(context, app.packageName, app.name)
                            }
                        }
                    }
                    showAddAppDialog = false
                } else {
                    val encodedPkgs = selectedApps.joinToString(",") { it.packageName }
                    val timeDelayActive = blockingUiState?.accountabilityMode == AccountabilityMode.TIME_DELAY
                    if (timeDelayActive && encodedPkgs.length > 140) {
                        Toast.makeText(
                            context,
                            "In 24-Hour Time Delay mode, select fewer apps per request.",
                            Toast.LENGTH_LONG
                        ).show()
                        return@AddAppPickerDialog
                    }
                    showAddAppDialog = false
                    val actionTitle = if (selectedApps.size == 1) {
                        "Whitelist ${selectedApps.first().name}"
                    } else {
                        "Whitelist ${selectedApps.size} Apps"
                    }
                    accountabilityGuard.guard(
                        targetId = "WHITELIST_ADD_APP:$encodedPkgs",
                        targetTitle = actionTitle
                    ) {
                        selectedApps.forEach { app ->
                            val isBlocked = blockedApps.any { it.packageName == app.packageName }
                            val isUnsupported = BlocklistRuleValidator.isUnsupportedBrowserBlockedFromWhitelist(
                                app.packageName,
                                blockUnsupportedBrowsers
                            )
                            if (!isBlocked && !isUnsupported && whitelistedApps.none { it.packageName == app.packageName }) {
                                whitelistedApps.add(app)
                                PGuardAccessibilityService.addWhitelistedApp(context, app.packageName)
                            }
                        }
                    }
                }
            }
        )
    }

    AccountabilityGuardDialogsHost(
        controller = accountabilityGuard,
        blockingViewModel = blockingViewModel
    )
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
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = app.packageName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
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
    val kind = remember(keyword) { BlocklistRuleValidator.normalizeWebsiteOrKeyword(keyword).second }
    val kindLabel = if (kind == BlocklistRuleValidator.EntryKind.WEBSITE) "Website" else "Keyword"

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

            Column {
                Text(
                    text = keyword,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = infoColor.copy(alpha = 0.14f)
                ) {
                    Text(
                        text = kindLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = infoColor,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
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
private fun AddAppPickerDialog(
    context: Context,
    alreadySelectedPackages: Set<String>,
    blockedPackages: Set<String> = emptySet(),
    isWhitelistMode: Boolean = false,
    blockUnsupportedBrowsersEnabled: Boolean = false,
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
            val allApps = queryDeviceLauncherApps(context)
            installedApps = if (isWhitelistMode) {
                allApps
            } else {
                val critical = getCriticalCommunicationPackages(context)
                allApps.filterNot { it.packageName.lowercase() in critical }
            }
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
                    trailingIcon = if (searchQuery.isNotEmpty()) {
                        {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(
                                    painter = painterResource(id = R.drawable.ic_close),
                                    contentDescription = "Clear search",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    } else null,
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
                        CircularProgressIndicator(color = primary)
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
                            val isUnsupportedBrowserBlocked = isWhitelistMode &&
                                BlocklistRuleValidator.isUnsupportedBrowserBlockedFromWhitelist(
                                    app.packageName,
                                    blockUnsupportedBrowsersEnabled
                                )
                            val isRestricted = isAlreadyAdded || isBlockedConflict || isUnsupportedBrowserBlocked
                            val isChecked = selectedPackages.contains(app.packageName) || isAlreadyAdded

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        when {
                                            isBlockedConflict -> Toast.makeText(
                                                context,
                                                "${app.name} is in your Blocklist. Remove it from Blocklist first using your accountability check.",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                            isUnsupportedBrowserBlocked -> Toast.makeText(
                                                context,
                                                "${app.name} cannot be whitelisted while Block Unsupported Browsers is ON.",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                            !isAlreadyAdded -> {
                                                if (selectedPackages.contains(app.packageName)) {
                                                    selectedPackages.remove(app.packageName)
                                                } else {
                                                    selectedPackages.add(app.packageName)
                                                }
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
                                        color = if (isRestricted) {
                                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
                                        } else {
                                            MaterialTheme.colorScheme.onSurface
                                        },
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = when {
                                            isBlockedConflict -> "In Blocklist (cannot whitelist)"
                                            isUnsupportedBrowserBlocked -> "Unsupported browser (blocked)"
                                            isAlreadyAdded -> "Already in list"
                                            else -> app.packageName
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = when {
                                            isBlockedConflict || isUnsupportedBrowserBlocked -> primary
                                            isAlreadyAdded -> primary.copy(alpha = 0.8f)
                                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                Checkbox(
                                    checked = if (isBlockedConflict || isUnsupportedBrowserBlocked) false else isChecked,
                                    enabled = !isRestricted,
                                    onCheckedChange = { checked ->
                                        if (!isRestricted) {
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
                        selectedPackages.contains(it.packageName) &&
                            (!isWhitelistMode || (
                                !blockedPackages.contains(it.packageName) &&
                                    !BlocklistRuleValidator.isUnsupportedBrowserBlockedFromWhitelist(
                                        it.packageName,
                                        blockUnsupportedBrowsersEnabled
                                    )
                                ))
                    }
                    onAddApps(toAdd)
                },
                enabled = selectedPackages.isNotEmpty(),
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

private fun getCriticalCommunicationPackages(context: Context): Set<String> {
    val critical = mutableSetOf(
        "com.google.android.dialer",
        "com.android.dialer",
        "com.samsung.android.dialer",
        "com.google.android.apps.messaging",
        "com.samsung.android.messaging",
        "com.android.mms"
    )
    try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val tm = context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
            tm?.defaultDialerPackage?.lowercase()?.let { critical.add(it) }
        }
    } catch (_: Exception) {}
    try {
        Telephony.Sms.getDefaultSmsPackage(context)?.lowercase()?.let { critical.add(it) }
    } catch (_: Exception) {}
    return critical
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
