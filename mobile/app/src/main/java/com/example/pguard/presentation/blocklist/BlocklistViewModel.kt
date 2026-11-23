package com.example.pguard.presentation.blocklist

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pguard.core.datastore.AuthDataStore
import com.example.pguard.core.network.model.BlocklistItemDto
import com.example.pguard.data.repository.BlocklistRepository
import com.example.pguard.engine.accessibility.BlockingPreferences
import com.example.pguard.engine.accessibility.BlocklistRuleValidator
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Locale

data class BlocklistUiState(
    val items: List<BlocklistItemDto> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

class BlocklistViewModel(
    private val blocklistRepository: BlocklistRepository,
    private val appContext: Context? = null,
    private val authDataStore: AuthDataStore? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(BlocklistUiState())
    val uiState: StateFlow<BlocklistUiState> = _uiState.asStateFlow()
    private var loadJob: Job? = null
    private var flushDeletesJob: Job? = null
    private var isUserSignedIn: Boolean = authDataStore == null

    init {
        if (authDataStore != null) {
            viewModelScope.launch {
                authDataStore.userFlow
                    .distinctUntilChangedBy { it?.id }
                    .collect { user ->
                        isUserSignedIn = user != null
                        if (user != null) {
                            loadBlocklist()
                        } else {
                            loadJob?.cancel()
                            flushDeletesJob?.cancel()
                            _uiState.update { it.copy(isLoading = false, items = emptyList()) }
                        }
                    }
            }
        } else {
            loadBlocklist()
        }

        val ctx = appContext
        if (ctx != null) {
            viewModelScope.launch {
                BlockingPreferences.blocklistRevision.drop(1).collect {
                    if (isUserSignedIn) {
                        syncLocalChangesIntoUi(ctx)
                    }
                }
            }
        }
    }

    private fun normalizeRuleKey(raw: String): String =
        BlocklistRuleValidator.normalizeWebsiteOrKeyword(raw).first

    private fun syncLocalChangesIntoUi(context: Context) {
        val localBlockedApps = BlockingPreferences.blockedAppPackages.toSet()
        val localBlockedKeywords = BlockingPreferences.customBlocklist.map { normalizeRuleKey(it) }
            .filter { it.isNotBlank() }
            .toSet()

        _uiState.update { current ->
            val retained = current.items.filter { item ->
                if (!item.isEnabled) {
                    true
                } else if (item.type == "app") {
                    item.value in localBlockedApps
                } else {
                    normalizeRuleKey(item.value) in localBlockedKeywords
                }
            }

            val existingAppPackages = retained.filter { it.type == "app" }.map { it.value }.toSet()
            val addedPackages = localBlockedApps - existingAppPackages
            val pm = context.packageManager
            val newAppItems = addedPackages.map { pkg ->
                val label = try {
                    val info = pm.getApplicationInfo(pkg, 0)
                    pm.getApplicationLabel(info).toString()
                } catch (_: Exception) {
                    pkg
                }
                BlocklistItemDto(
                    id = -System.nanoTime(),
                    type = "app",
                    value = pkg,
                    label = label,
                    isEnabled = true
                )
            }

            val existingKeywords = retained
                .filter { it.type == "website" || it.type == "keyword" }
                .map { normalizeRuleKey(it.value) }
                .toSet()
            val addedKeywords = localBlockedKeywords - existingKeywords
            val newKeywordItems = addedKeywords.map { kw ->
                val (normalized, kind) = BlocklistRuleValidator.normalizeWebsiteOrKeyword(kw)
                BlocklistItemDto(
                    id = -System.nanoTime(),
                    type = if (kind == BlocklistRuleValidator.EntryKind.WEBSITE) "website" else "keyword",
                    value = normalized,
                    label = normalized,
                    isEnabled = true
                )
            }

            current.copy(items = retained + newAppItems + newKeywordItems)
        }

        if (BlockingPreferences.pendingDeletedBlocklistApps.isNotEmpty() ||
            BlockingPreferences.pendingDeletedBlocklistKeywords.isNotEmpty()
        ) {
            flushPendingDeletes(context)
        }
    }

    private fun flushPendingDeletes(context: Context) {
        if (!isUserSignedIn) return
        flushDeletesJob?.cancel()
        flushDeletesJob = viewModelScope.launch {
            val pendingApps = BlockingPreferences.pendingDeletedBlocklistApps.toSet()
            val pendingKws = BlockingPreferences.pendingDeletedBlocklistKeywords.map { normalizeRuleKey(it) }.toSet()
            if (pendingApps.isEmpty() && pendingKws.isEmpty()) return@launch

            blocklistRepository.fetchBlocklist().onSuccess { serverItems ->
                for (item in serverItems) {
                    when (item.type) {
                        "app" -> if (item.value in pendingApps) {
                            blocklistRepository.deleteItem(item.id).onSuccess {
                                BlockingPreferences.clearPendingDeletedApp(context, item.value)
                            }
                        }
                        "website", "keyword" -> {
                            val norm = normalizeRuleKey(item.value)
                            if (norm in pendingKws) {
                                blocklistRepository.deleteItem(item.id).onSuccess {
                                    BlockingPreferences.clearPendingDeletedKeyword(context, norm)
                                }
                            }
                        }
                    }
                }

                val serverApps = serverItems.filter { it.type == "app" }.map { it.value }.toSet()
                for (pkg in pendingApps) {
                    if (pkg !in serverApps) {
                        BlockingPreferences.clearPendingDeletedApp(context, pkg)
                    }
                }
                val serverKws = serverItems
                    .filter { it.type == "website" || it.type == "keyword" }
                    .map { normalizeRuleKey(it.value) }
                    .toSet()
                for (kw in pendingKws) {
                    if (kw !in serverKws) {
                        BlockingPreferences.clearPendingDeletedKeyword(context, kw)
                    }
                }
            }
        }
    }

    fun loadBlocklist() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val ctx = appContext
            val localApps = ctx?.let { BlockingPreferences.getBlockedApps(it) } ?: emptySet()
            val localKeywords = ctx?.let { BlockingPreferences.getCustomBlockedKeywords(it) } ?: emptySet()
            val pendingDeletedApps = BlockingPreferences.pendingDeletedBlocklistApps.toSet()
            val pendingDeletedKeywords = BlockingPreferences.pendingDeletedBlocklistKeywords
                .map { normalizeRuleKey(it) }
                .toSet()

            blocklistRepository.fetchBlocklist().fold(
                onSuccess = { serverItems ->
                    val merged = mutableListOf<BlocklistItemDto>()
                    if (ctx != null) {
                        val seenServerApps = mutableSetOf<String>()
                        val seenServerKeywords = mutableSetOf<String>()

                        for (item in serverItems) {
                            when (item.type) {
                                "app" -> {
                                    seenServerApps.add(item.value.lowercase(Locale.ROOT))
                                    if (item.value in pendingDeletedApps) {
                                        blocklistRepository.deleteItem(item.id).onSuccess {
                                            BlockingPreferences.clearPendingDeletedApp(ctx, item.value)
                                        }
                                        continue
                                    }
                                    merged.add(item)
                                    if (item.isEnabled) {
                                        BlockingPreferences.addBlockedApp(
                                            ctx,
                                            item.value,
                                            item.label.ifBlank { item.value }
                                        )
                                    } else {
                                        BlockingPreferences.removeBlockedApp(ctx, item.value, recordTombstone = false)
                                    }
                                }
                                "website", "keyword" -> {
                                    val normalized = normalizeRuleKey(item.value)
                                    seenServerKeywords.add(normalized)
                                    if (normalized in pendingDeletedKeywords) {
                                        blocklistRepository.deleteItem(item.id).onSuccess {
                                            BlockingPreferences.clearPendingDeletedKeyword(ctx, normalized)
                                        }
                                        continue
                                    }
                                    merged.add(item.copy(value = normalized))
                                    if (item.isEnabled) {
                                        BlockingPreferences.addCustomBlockedKeyword(ctx, normalized)
                                    } else {
                                        BlockingPreferences.removeCustomBlockedKeyword(ctx, normalized, recordTombstone = false)
                                    }
                                }
                                else -> merged.add(item)
                            }
                        }

                        // Clear stale tombstones no longer present on the server
                        for (pkg in pendingDeletedApps) {
                            if (pkg.lowercase(Locale.ROOT) !in seenServerApps) {
                                BlockingPreferences.clearPendingDeletedApp(ctx, pkg)
                            }
                        }
                        for (kw in pendingDeletedKeywords) {
                            if (kw !in seenServerKeywords) {
                                BlockingPreferences.clearPendingDeletedKeyword(ctx, kw)
                            }
                        }

                        // Push any locally added offline items not yet present on the server
                        for (pkg in localApps) {
                            if (pkg !in pendingDeletedApps && pkg.lowercase(Locale.ROOT) !in seenServerApps) {
                                blocklistRepository.createItem(type = "app", value = pkg, label = pkg)
                                    .onSuccess { created -> merged.add(created) }
                            }
                        }

                        for (kw in localKeywords) {
                            val normalized = normalizeRuleKey(kw)
                            if (normalized.isNotBlank() &&
                                normalized !in pendingDeletedKeywords &&
                                normalized !in seenServerKeywords
                            ) {
                                val itemType = classifyKeywordOrWebsite(normalized)
                                blocklistRepository.createItem(type = itemType, value = normalized, label = normalized)
                                    .onSuccess { created -> merged.add(created) }
                            }
                        }
                    } else {
                        merged.addAll(serverItems)
                    }

                    _uiState.update { it.copy(items = merged, isLoading = false, errorMessage = null) }
                },
                onFailure = { error ->
                    Log.w(TAG, "Failed to sync blocklist from server: ${error.message}")
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = error.localizedMessage ?: "Failed to load blocklist"
                        )
                    }
                }
            )
        }
    }

    fun addBlockedApp(context: Context, packageName: String, label: String) {
        BlockingPreferences.addBlockedApp(context, packageName, label)
        if (_uiState.value.items.none { it.type == "app" && it.value == packageName }) {
            val optimistic = BlocklistItemDto(
                id = -System.nanoTime(),
                type = "app",
                value = packageName,
                label = label,
                isEnabled = true
            )
            _uiState.update { it.copy(items = it.items + optimistic) }
        }

        viewModelScope.launch {
            blocklistRepository.createItem(type = "app", value = packageName, label = label).fold(
                onSuccess = { created ->
                    _uiState.update { current ->
                        val updated = current.items.filterNot { it.type == "app" && it.value == packageName } + created
                        current.copy(items = updated)
                    }
                },
                onFailure = { err ->
                    Log.w(TAG, "Offline/sync deferred adding app $packageName: ${err.message}")
                }
            )
        }
    }

    fun removeBlockedApp(context: Context, packageName: String) {
        BlockingPreferences.removeBlockedApp(context, packageName, recordTombstone = true)
        val existing = _uiState.value.items.firstOrNull { it.type == "app" && it.value == packageName }
        _uiState.update { current ->
            current.copy(items = current.items.filterNot { it.type == "app" && it.value == packageName })
        }

        if (existing != null && existing.id > 0L) {
            viewModelScope.launch {
                blocklistRepository.deleteItem(existing.id).onSuccess {
                    BlockingPreferences.clearPendingDeletedApp(context, packageName)
                }.onFailure { err ->
                    Log.w(TAG, "Offline/sync deferred removing app $packageName: ${err.message}")
                }
            }
        } else {
            flushPendingDeletes(context)
        }
    }

    fun addBlockedKeywordOrWebsite(context: Context, rawValue: String) {
        val (normalized, kind) = BlocklistRuleValidator.normalizeWebsiteOrKeyword(rawValue)
        if (normalized.isBlank()) return
        BlockingPreferences.addCustomBlockedKeyword(context, normalized)

        val itemType = if (kind == BlocklistRuleValidator.EntryKind.WEBSITE) "website" else "keyword"
        if (_uiState.value.items.none {
                (it.type == "website" || it.type == "keyword") &&
                    normalizeRuleKey(it.value).equals(normalized, ignoreCase = true)
            }
        ) {
            val optimistic = BlocklistItemDto(
                id = -System.nanoTime(),
                type = itemType,
                value = normalized,
                label = normalized,
                isEnabled = true
            )
            _uiState.update { it.copy(items = it.items + optimistic) }
        }

        viewModelScope.launch {
            blocklistRepository.createItem(type = itemType, value = normalized, label = normalized).fold(
                onSuccess = { created ->
                    _uiState.update { current ->
                        val updated = current.items.filterNot {
                            (it.type == "website" || it.type == "keyword") &&
                                normalizeRuleKey(it.value).equals(normalized, ignoreCase = true)
                        } + created
                        current.copy(items = updated)
                    }
                },
                onFailure = { err ->
                    Log.w(TAG, "Offline/sync deferred adding keyword '$normalized': ${err.message}")
                }
            )
        }
    }

    fun removeBlockedKeywordOrWebsite(context: Context, rawValue: String) {
        val normalized = normalizeRuleKey(rawValue)
        BlockingPreferences.removeCustomBlockedKeyword(context, normalized, recordTombstone = true)
        val existing = _uiState.value.items.firstOrNull {
            (it.type == "website" || it.type == "keyword") &&
                normalizeRuleKey(it.value).equals(normalized, ignoreCase = true)
        }
        _uiState.update { current ->
            current.copy(
                items = current.items.filterNot {
                    (it.type == "website" || it.type == "keyword") &&
                        normalizeRuleKey(it.value).equals(normalized, ignoreCase = true)
                }
            )
        }

        if (existing != null && existing.id > 0L) {
            viewModelScope.launch {
                blocklistRepository.deleteItem(existing.id).onSuccess {
                    BlockingPreferences.clearPendingDeletedKeyword(context, normalized)
                }.onFailure { err ->
                    Log.w(TAG, "Offline/sync deferred removing keyword '$normalized': ${err.message}")
                }
            }
        } else {
            flushPendingDeletes(context)
        }
    }

    fun addItem(type: String, value: String, label: String? = null) {
        viewModelScope.launch {
            blocklistRepository.createItem(type = type, value = value, label = label).fold(
                onSuccess = { created ->
                    _uiState.update { current ->
                        val updated = current.items.filterNot { it.type == created.type && it.value == created.value } + created
                        current.copy(items = updated)
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(errorMessage = error.localizedMessage ?: "Failed to add item")
                    }
                }
            )
        }
    }

    fun toggleItem(id: Long, isEnabled: Boolean) {
        _uiState.update { current ->
            current.copy(
                items = current.items.map {
                    if (it.id == id) it.copy(isEnabled = isEnabled) else it
                }
            )
        }

        viewModelScope.launch {
            blocklistRepository.toggleItem(id, isEnabled).onFailure { error ->
                _uiState.update { current ->
                    current.copy(
                        items = current.items.map {
                            if (it.id == id) it.copy(isEnabled = !isEnabled) else it
                        },
                        errorMessage = error.localizedMessage ?: "Failed to update item"
                    )
                }
            }
        }
    }

    fun deleteItem(id: Long) {
        val previousItems = _uiState.value.items
        _uiState.update { current ->
            current.copy(items = current.items.filterNot { it.id == id })
        }

        viewModelScope.launch {
            blocklistRepository.deleteItem(id).onFailure { error ->
                _uiState.update {
                    it.copy(
                        items = previousItems,
                        errorMessage = error.localizedMessage ?: "Failed to delete item"
                    )
                }
            }
        }
    }

    fun dismissError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    private fun classifyKeywordOrWebsite(normalized: String): String =
        if (BlocklistRuleValidator.normalizeWebsiteOrKeyword(normalized).second == BlocklistRuleValidator.EntryKind.WEBSITE) {
            "website"
        } else {
            "keyword"
        }

    companion object {
        private const val TAG = "BlocklistViewModel"
    }
}
