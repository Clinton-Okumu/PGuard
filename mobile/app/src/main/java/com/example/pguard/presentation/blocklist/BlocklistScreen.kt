package com.example.pguard.presentation.blocklist

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.pguard.R
import com.example.pguard.presentation.theme.CoralPrimary
import com.example.pguard.presentation.theme.PGuardTheme
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
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

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

    val currentApps = if (selectedListType == ListType.BLOCKLIST) blockedApps else whitelistedApps
    val currentKeywords = if (selectedListType == ListType.BLOCKLIST) blockedKeywords else whitelistedKeywords

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
            // 1. Top Mode Selector Pill (Blocklist vs Whitelist)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .height(48.dp)
                    .border(1.2.dp, CoralPrimary, CircleShape)
                    .clip(CircleShape)
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .background(if (selectedListType == ListType.BLOCKLIST) CoralPrimary else Color.Transparent)
                        .clickable { selectedListType = ListType.BLOCKLIST },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Blocklist",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = if (selectedListType == ListType.BLOCKLIST) Color.White else CoralPrimary
                    )
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .background(if (selectedListType == ListType.WHITELIST) CoralPrimary else Color.Transparent)
                        .clickable { selectedListType = ListType.WHITELIST },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Whitelist",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = if (selectedListType == ListType.WHITELIST) Color.White else CoralPrimary
                    )
                }
            }

            // 2. Sub-category Selector Pill (Apps vs Keywords/websites)
            Row(
                modifier = Modifier
                    .padding(start = 16.dp, top = 2.dp, bottom = 12.dp)
                    .height(34.dp)
                    .border(1.dp, CoralPrimary, CircleShape)
                    .clip(CircleShape)
            ) {
                Box(
                    modifier = Modifier
                        .background(if (selectedCategory == ContentCategory.APPS) CoralPrimary else Color.Transparent)
                        .clickable { selectedCategory = ContentCategory.APPS }
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Apps (${currentApps.size})",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.5.sp,
                        color = if (selectedCategory == ContentCategory.APPS) Color.White else CoralPrimary
                    )
                }

                Box(
                    modifier = Modifier
                        .background(if (selectedCategory == ContentCategory.KEYWORDS_WEBSITES) CoralPrimary else Color.Transparent)
                        .clickable { selectedCategory = ContentCategory.KEYWORDS_WEBSITES }
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Keywords/websites (${currentKeywords.size})",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.5.sp,
                        color = if (selectedCategory == ContentCategory.KEYWORDS_WEBSITES) Color.White else CoralPrimary
                    )
                }
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
                    Text(
                        text = "Empty List",
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontSize = 15.5.sp,
                            fontWeight = FontWeight.Medium
                        ),
                        color = Color.White.copy(alpha = 0.8f)
                    )
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
                                onRemove = { currentApps.remove(app) }
                            )
                            HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
                        }
                    } else {
                        items(currentKeywords, key = { it }) { keyword ->
                            KeywordListItem(
                                keyword = keyword,
                                onRemove = { currentKeywords.remove(keyword) }
                            )
                            HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
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
            shape = RoundedCornerShape(18.dp),
            containerColor = CoralPrimary,
            contentColor = Color.White,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 20.dp, bottom = 20.dp)
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_add),
                contentDescription = "Add",
                tint = Color.White,
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
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            },
            text = {
                Column {
                    Text(
                        text = "Enter a website domain or keyword (e.g. reddit.com, casino, adult):",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = keywordInput,
                        onValueChange = { keywordInput = it },
                        placeholder = { Text("domain.com or keyword") },
                        singleLine = true,
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
                        val trimmed = keywordInput.trim().lowercase()
                        if (trimmed.isNotEmpty() && !currentKeywords.contains(trimmed)) {
                            currentKeywords.add(trimmed)
                        }
                        showAddKeywordDialog = false
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = CoralPrimary)
                ) {
                    Text("Add", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddKeywordDialog = false }) {
                    Text("Cancel", color = Color.White.copy(alpha = 0.7f))
                }
            },
            containerColor = Color(0xFF1E1E24),
            shape = RoundedCornerShape(16.dp)
        )
    }

    // Add Apps Picker Dialog
    if (showAddAppDialog) {
        AddAppPickerDialog(
            context = context,
            alreadySelectedPackages = currentApps.map { it.packageName }.toSet(),
            listTitle = if (selectedListType == ListType.BLOCKLIST) "Block Apps" else "Whitelist Apps",
            onDismiss = { showAddAppDialog = false },
            onAddApps = { selectedApps ->
                selectedApps.forEach { app ->
                    if (currentApps.none { it.packageName == app.packageName }) {
                        currentApps.add(app)
                    }
                }
                showAddAppDialog = false
            }
        )
    }
}

@Composable
private fun AppListItem(
    app: InstalledAppItem,
    onRemove: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f).padding(end = 12.dp)
        ) {
            val bitmap = remember(app.packageName) { app.icon?.toComposeBitmap() }
            if (bitmap != null) {
                Image(
                    bitmap = bitmap,
                    contentDescription = app.name,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(CoralPrimary.copy(alpha = 0.14f))
                        .border(1.dp, CoralPrimary.copy(alpha = 0.25f), RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_nav_blocking),
                        contentDescription = null,
                        tint = CoralPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column {
                Text(
                    text = app.name,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = app.packageName,
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.5f)
                )
            }
        }

        IconButton(
            onClick = onRemove,
            modifier = Modifier.size(32.dp)
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_close),
                contentDescription = "Remove",
                tint = Color.White.copy(alpha = 0.55f),
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
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f).padding(end = 12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF64B5F6).copy(alpha = 0.14f))
                    .border(1.dp, Color(0xFF64B5F6).copy(alpha = 0.25f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_globe),
                    contentDescription = null,
                    tint = Color(0xFF64B5F6),
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Text(
                text = keyword,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
                color = Color.White
            )
        }

        IconButton(
            onClick = onRemove,
            modifier = Modifier.size(32.dp)
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_close),
                contentDescription = "Remove",
                tint = Color.White.copy(alpha = 0.55f),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun AddAppPickerDialog(
    context: Context,
    alreadySelectedPackages: Set<String>,
    listTitle: String,
    onDismiss: () -> Unit,
    onAddApps: (List<InstalledAppItem>) -> Unit
) {
    var installedApps by remember { mutableStateOf<List<InstalledAppItem>>(emptyList()) }
    var searchQuery by remember { mutableStateOf("") }
    val selectedPackages = remember { mutableStateListOf<String>() }
    var isLoading by remember { mutableStateOf(true) }

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
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
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
                            tint = Color.White.copy(alpha = 0.5f),
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CoralPrimary,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.25f)
                    )
                )

                Spacer(modifier = Modifier.height(12.dp))

                if (isLoading) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(text = "Loading installed apps...", color = Color.White.copy(alpha = 0.6f))
                    }
                } else if (filteredApps.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(text = "No apps found", color = Color.White.copy(alpha = 0.6f))
                    }
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(filteredApps, key = { it.packageName }) { app ->
                            val isAlreadyAdded = alreadySelectedPackages.contains(app.packageName)
                            val isChecked = selectedPackages.contains(app.packageName) || isAlreadyAdded

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(enabled = !isAlreadyAdded) {
                                        if (selectedPackages.contains(app.packageName)) {
                                            selectedPackages.remove(app.packageName)
                                        } else {
                                            selectedPackages.add(app.packageName)
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
                                            .clip(RoundedCornerShape(10.dp))
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(CoralPrimary.copy(alpha = 0.14f))
                                            .border(1.dp, CoralPrimary.copy(alpha = 0.25f), RoundedCornerShape(10.dp)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            painter = painterResource(id = R.drawable.ic_nav_blocking),
                                            contentDescription = null,
                                            tint = CoralPrimary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = app.name,
                                        fontWeight = FontWeight.Medium,
                                        fontSize = 14.sp,
                                        color = if (isAlreadyAdded) Color.White.copy(alpha = 0.4f) else Color.White
                                    )
                                    Text(
                                        text = if (isAlreadyAdded) "Already in list" else app.packageName,
                                        fontSize = 11.5.sp,
                                        color = if (isAlreadyAdded) CoralPrimary.copy(alpha = 0.8f) else Color.White.copy(alpha = 0.45f)
                                    )
                                }

                                Checkbox(
                                    checked = isChecked,
                                    enabled = !isAlreadyAdded,
                                    onCheckedChange = { checked ->
                                        if (checked) selectedPackages.add(app.packageName)
                                        else selectedPackages.remove(app.packageName)
                                    },
                                    colors = CheckboxDefaults.colors(
                                        checkedColor = CoralPrimary,
                                        checkmarkColor = Color.White
                                    )
                                )
                            }
                            HorizontalDivider(color = Color.White.copy(alpha = 0.05f))
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val toAdd = installedApps.filter { selectedPackages.contains(it.packageName) }
                    onAddApps(toAdd)
                },
                colors = ButtonDefaults.textButtonColors(contentColor = CoralPrimary)
            ) {
                Text(
                    text = "Add (${selectedPackages.size})",
                    fontWeight = FontWeight.Bold
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = Color.White.copy(alpha = 0.7f))
            }
        },
        containerColor = Color(0xFF1E1E24),
        shape = RoundedCornerShape(16.dp)
    )
}

private fun queryDeviceLauncherApps(context: Context): List<InstalledAppItem> {
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

@Preview(showBackground = true, backgroundColor = 0xFF121214)
@Composable
private fun BlocklistScreenPreview() {
    PGuardTheme(darkTheme = true) {
        BlocklistScreen()
    }
}
