package com.example.pguard.presentation.dashboard

import android.content.Context
import android.util.Patterns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pguard.core.datastore.AuthDataStore
import com.example.pguard.core.network.model.AccountabilityPartnerDto
import com.example.pguard.core.network.model.SecurityAlertDto
import com.example.pguard.core.network.model.UpdateSettingsRequest
import com.example.pguard.core.network.model.UserSettingsDto
import com.example.pguard.data.repository.AccountabilityRepository
import com.example.pguard.data.repository.SettingsRepository
import com.example.pguard.engine.accessibility.BlockedScreenConfig
import com.example.pguard.engine.accessibility.BlockingPreferences
import com.example.pguard.engine.accessibility.PGuardAccessibilityService
import com.example.pguard.engine.accountability.SecurityAlertReporter
import com.example.pguard.engine.accountability.TimeDelayNotificationScheduler
import com.example.pguard.engine.admin.DeviceAdminManager
import com.example.pguard.engine.vpn.PGuardVpnService
import com.example.pguard.presentation.focus.FocusModeManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class SettingsEditor {
    MESSAGE,
    COUNTDOWN,
    REDIRECT
}

data class InfoDialogState(
    val title: String,
    val message: String
)

enum class SyncState {
    IDLE,
    SYNCING,
    SYNCED,
    PENDING_RETRY
}

data class BlockingUiState(
    val settings: UserSettingsDto = UserSettingsDto(),
    val partner: AccountabilityPartnerDto? = null,
    val isPartnerLinked: Boolean = false,
    val isLoading: Boolean = false,
    val syncState: SyncState = SyncState.IDLE,
    val lastSyncedAtMs: Long = 0L,
    val errorMessage: String? = null,
    val editor: SettingsEditor? = null,
    val editorText: String = "",
    val editorError: String? = null,
    val isBlockedScreenPreviewVisible: Boolean = false,
    val infoDialog: InfoDialogState? = null,
    val isGuideVisible: Boolean = false,
    val isInviteDialogVisible: Boolean = false,
    val inviteEmail: String = "",
    val inviteName: String = "",
    val inviteEmailError: String? = null,
    val isSendingInvite: Boolean = false,
    val isConfirmCancelInviteVisible: Boolean = false,
    val accountabilityMode: AccountabilityMode = AccountabilityMode.NONE,
    val isModeSelectionVisible: Boolean = false,
    val typingChallengeTarget: ProtectedAction? = null,
    val timeDelayConfirmTarget: ProtectedAction? = null,
    val friendApprovalTarget: ProtectedAction? = null,
    val timeDelayUnlockAt: Long = 0L,
    val timeDelayTarget: String = "",
    val isTimeDelayServerReady: Boolean = false,
    val isVerifyingUnlockCode: Boolean = false,
    val isSendingUnlockCode: Boolean = false,
    val unlockEmailSent: Boolean = false,
    val unlockCodeError: String? = null,
    val unlockRequestError: String? = null,
    val isDeviceAdminDialogVisible: Boolean = false,
    val isDnsVpnEnabled: Boolean = BlockingPreferences.isDnsVpnEnabled,
    val isSecurityAlertsDialogVisible: Boolean = false,
    val securityAlerts: List<SecurityAlertDto> = emptyList(),
    val isLoadingSecurityAlerts: Boolean = false
)

class BlockingViewModel(
    private val settingsRepository: SettingsRepository,
    private val accountabilityRepository: AccountabilityRepository,
    private val authDataStore: AuthDataStore? = null,
    private val appContext: Context? = null
) : ViewModel() {

    private var currentUserEmail: String? = null
    private var isUserSignedIn: Boolean = authDataStore == null
    private var partnerPollingJob: Job? = null
    private var loadDataJob: Job? = null
    private var updateSettingJob: Job? = null
    private var autoSyncRetryJob: Job? = null
    private var autoSyncRetryAttempt: Int = 0
    private var pendingDeviceAdminActivation: Boolean = false

    private val resolvedContext: Context?
        get() = appContext ?: BlockingPreferences.appContext

    private val _uiState = MutableStateFlow(
        BlockingUiState(
            settings = UserSettingsDto(
                blockAdultContent = BlockingPreferences.isAdultBlockingEnabled,
                blockShorts = BlockingPreferences.isShortsBlockingEnabled,
                blockWhatsAppChannels = BlockingPreferences.isWhatsAppChannelsBlockingEnabled,
                blockInstagramReels = BlockingPreferences.isInstagramReelsBlockingEnabled,
                blockTikTok = BlockingPreferences.isTikTokBlockingEnabled,
                uninstallProtection = BlockingPreferences.isUninstallProtectionEnabled,
                blockUnsupportedBrowsers = BlockingPreferences.isUnsupportedBrowsersBlockingEnabled,
                blockNewlyInstalledApps = BlockingPreferences.isNewlyInstalledAppsBlockingEnabled,
                blockedScreenMessage = BlockingPreferences.blockedScreenMessage,
                blockedScreenCountdown = BlockingPreferences.blockedScreenCountdown,
                redirectUrl = BlockingPreferences.redirectUrl,
                accountabilityMode = BlockingPreferences.accountabilityMode
            ),
            isPartnerLinked = BlockingPreferences.isPartnerLinkedCached,
            partner = if (BlockingPreferences.isPartnerLinkedCached && BlockingPreferences.cachedPartnerEmail.isNotBlank()) {
                AccountabilityPartnerDto(
                    partnerEmail = BlockingPreferences.cachedPartnerEmail,
                    partnerName = BlockingPreferences.cachedPartnerName,
                    status = "accepted"
                )
            } else {
                null
            },
            accountabilityMode = AccountabilityMode.fromCode(BlockingPreferences.accountabilityMode),
            timeDelayUnlockAt = BlockingPreferences.timeDelayUnlockAt,
            timeDelayTarget = BlockingPreferences.timeDelayTarget
        )
    )
    val uiState: StateFlow<BlockingUiState> = _uiState.asStateFlow()

    init {
        if (authDataStore != null) {
            viewModelScope.launch {
                authDataStore.userFlow
                    .distinctUntilChangedBy { it?.id }
                    .collect { user ->
                        currentUserEmail = user?.email
                        isUserSignedIn = user != null
                        if (user != null) {
                            autoSyncRetryAttempt = 0
                            loadData()
                        } else {
                            stopPartnerPolling()
                            loadDataJob?.cancel()
                            loadDataJob = null
                            updateSettingJob?.cancel()
                            updateSettingJob = null
                            autoSyncRetryJob?.cancel()
                            autoSyncRetryJob = null
                            autoSyncRetryAttempt = 0
                            // Only clear partner UI state if local fail-closed cache is already cleared
                            if (!BlockingPreferences.isPartnerLinkedCached) {
                                _uiState.update {
                                    it.copy(
                                        isLoading = false,
                                        syncState = SyncState.IDLE,
                                        partner = null,
                                        isPartnerLinked = false
                                    )
                                }
                            } else {
                                _uiState.update { it.copy(isLoading = false, syncState = SyncState.IDLE) }
                            }
                        }
                    }
            }
        } else {
            loadData()
        }
    }

    fun setCurrentUserEmail(email: String?) {
        currentUserEmail = email
    }

    private fun normalizeEmail(email: String): String {
        val clean = email.trim().lowercase()
        val parts = clean.split("@")
        if (parts.size != 2) return clean
        var local = parts[0]
        val domain = parts[1]
        val plusIdx = local.indexOf('+')
        if (plusIdx != -1) {
            local = local.substring(0, plusIdx)
        }
        if (domain == "gmail.com" || domain == "googlemail.com") {
            local = local.replace(".", "")
        }
        return "$local@$domain"
    }

    private fun isSameEmail(email1: String, email2: String): Boolean {
        if (email1.trim().equals(email2.trim(), ignoreCase = true)) return true
        return normalizeEmail(email1) == normalizeEmail(email2)
    }

    private fun isValidEmailFormat(email: String): Boolean {
        val trimmed = email.trim()
        if (trimmed.isEmpty() || !trimmed.contains("@")) return false
        return try {
            Patterns.EMAIL_ADDRESS.matcher(trimmed).matches()
        } catch (_: Throwable) {
            val parts = trimmed.split("@")
            parts.size == 2 && parts[0].isNotBlank() && parts[1].contains(".")
        }
    }

    fun refreshFromLocalPreferences(context: Context? = null) {
        if (context != null) {
            BlockingPreferences.loadSettings(context)
        }
        _uiState.update { current ->
            val cachedLinked = BlockingPreferences.isPartnerLinkedCached
            val fallbackPartner = if (current.partner == null && cachedLinked && BlockingPreferences.cachedPartnerEmail.isNotBlank()) {
                AccountabilityPartnerDto(
                    partnerEmail = BlockingPreferences.cachedPartnerEmail,
                    partnerName = BlockingPreferences.cachedPartnerName,
                    status = "accepted"
                )
            } else {
                current.partner
            }
            current.copy(
                settings = current.settings.copy(
                    blockAdultContent = BlockingPreferences.isAdultBlockingEnabled,
                    blockShorts = BlockingPreferences.isShortsBlockingEnabled,
                    blockWhatsAppChannels = BlockingPreferences.isWhatsAppChannelsBlockingEnabled,
                    blockInstagramReels = BlockingPreferences.isInstagramReelsBlockingEnabled,
                    blockTikTok = BlockingPreferences.isTikTokBlockingEnabled,
                    uninstallProtection = BlockingPreferences.isUninstallProtectionEnabled,
                    blockUnsupportedBrowsers = BlockingPreferences.isUnsupportedBrowsersBlockingEnabled,
                    blockNewlyInstalledApps = BlockingPreferences.isNewlyInstalledAppsBlockingEnabled,
                    blockedScreenMessage = BlockingPreferences.blockedScreenMessage,
                    blockedScreenCountdown = BlockingPreferences.blockedScreenCountdown,
                    redirectUrl = BlockingPreferences.redirectUrl,
                    accountabilityMode = BlockingPreferences.accountabilityMode
                ),
                partner = fallbackPartner,
                isPartnerLinked = current.isPartnerLinked || cachedLinked,
                accountabilityMode = AccountabilityMode.fromCode(BlockingPreferences.accountabilityMode),
                timeDelayUnlockAt = BlockingPreferences.timeDelayUnlockAt,
                timeDelayTarget = BlockingPreferences.timeDelayTarget,
                isDnsVpnEnabled = BlockingPreferences.isDnsVpnEnabled
            )
        }
    }

    fun loadData() {
        if (!isUserSignedIn) return
        loadDataJob?.cancel()
        loadDataJob = viewModelScope.launch {
            loadDataInternal()
        }
    }

    /**
     * Automatically syncs settings in the background on app resume or when local changes
     * are pending, without requiring the user to tap a manual sync button.
     */
    fun autoSyncIfNeeded(minIntervalMs: Long = 30_000L) {
        if (!isUserSignedIn) return
        if (loadDataJob?.isActive == true) return
        val state = _uiState.value
        val now = System.currentTimeMillis()
        val needsSync = BlockingPreferences.isLocalDirty ||
            state.syncState == SyncState.PENDING_RETRY ||
            state.lastSyncedAtMs == 0L ||
            (now - state.lastSyncedAtMs) >= minIntervalMs
        if (needsSync) {
            loadData()
        }
    }

    suspend fun syncSettingsNow(): Result<Unit> {
        if (!isUserSignedIn) return Result.success(Unit)
        loadDataJob?.cancel()
        loadDataJob = null
        return loadDataInternal()
    }

    private fun scheduleAutoSyncRetry() {
        if (!isUserSignedIn) return
        if (autoSyncRetryJob?.isActive == true) return
        val backoffMs = longArrayOf(5_000L, 15_000L, 30_000L, 60_000L)
        if (autoSyncRetryAttempt >= backoffMs.size) return
        val delayMs = backoffMs[autoSyncRetryAttempt]
        autoSyncRetryAttempt++
        autoSyncRetryJob = viewModelScope.launch {
            delay(delayMs)
            autoSyncRetryJob = null
            if (isUserSignedIn) {
                loadData()
            }
        }
    }

    private suspend fun loadDataInternal(): Result<Unit> {
        _uiState.update { it.copy(isLoading = true, syncState = SyncState.SYNCING, errorMessage = null) }

        return coroutineScope {
            val settingsDeferred = async { settingsRepository.fetchSettings() }
            val partnerDeferred = async { accountabilityRepository.fetchPartner() }
            val timeDelayDeferred = async { accountabilityRepository.getTimeDelay() }

            val settingsResult = settingsDeferred.await()
            val partnerResult = partnerDeferred.await()
            val timeDelayResult = timeDelayDeferred.await()

            val partnerSuccess = partnerResult.isSuccess
            val partnerDto = partnerResult.getOrNull()?.partner
            val isLinked = if (partnerSuccess) {
                (partnerResult.getOrNull()?.isLinked == true) || (partnerDto != null && partnerDto.status == "accepted")
            } else {
                _uiState.value.isPartnerLinked || BlockingPreferences.isPartnerLinkedCached
            }

            if (partnerSuccess) {
                resolvedContext?.let { ctx ->
                    BlockingPreferences.savePartnerCache(
                        context = ctx,
                        isLinked = isLinked,
                        email = if (isLinked) partnerDto?.partnerEmail.orEmpty() else "",
                        name = if (isLinked) partnerDto?.partnerName.orEmpty() else ""
                    )
                }
            }

            // Reconcile server-backed time delay
            var syncedUnlockAt = _uiState.value.timeDelayUnlockAt
            var syncedTarget = _uiState.value.timeDelayTarget
            var syncedServerReady = false
            val tdResp = timeDelayResult.getOrNull()
            if (timeDelayResult.isSuccess && tdResp != null) {
                val td = tdResp.timeDelay
                if (td != null) {
                    val nowWall = System.currentTimeMillis()
                    val serverUnlockWallMs = if (tdResp.isReady) nowWall - 1_000L else nowWall + tdResp.remainingMs
                    syncedUnlockAt = serverUnlockWallMs
                    syncedTarget = td.targetKey
                    syncedServerReady = tdResp.isReady
                    resolvedContext?.let { ctx ->
                        BlockingPreferences.setTimeDelay(
                            context = ctx,
                            unlockAtWallMs = serverUnlockWallMs,
                            target = td.targetKey,
                            durationMs = tdResp.remainingMs.coerceAtLeast(0L),
                            serverBacked = true
                        )
                        if (!tdResp.isReady) {
                            TimeDelayNotificationScheduler.schedule(
                                ctx,
                                serverUnlockWallMs,
                                td.targetTitle.ifBlank { targetDisplayName(td.targetKey) }
                            )
                        } else {
                            TimeDelayNotificationScheduler.cancel(ctx)
                        }
                    }
                } else if (BlockingPreferences.isTimeDelayServerBacked) {
                    syncedUnlockAt = 0L
                    syncedTarget = ""
                    syncedServerReady = false
                    resolvedContext?.let { ctx ->
                        BlockingPreferences.clearTimeDelay(ctx)
                        TimeDelayNotificationScheduler.cancel(ctx)
                    }
                }
            }

            var shouldPushLocal = false
            var settingsToPush: UserSettingsDto? = null

            _uiState.update { current ->
                val serverSettings = settingsResult.getOrNull()
                val finalSettings: UserSettingsDto
                val finalMode: AccountabilityMode

                if (serverSettings != null) {
                    if (BlockingPreferences.isLocalDirty) {
                        shouldPushLocal = true
                        val dirtySettings = current.settings.copy(accountabilityMode = current.accountabilityMode.code)
                        settingsToPush = dirtySettings
                        finalSettings = dirtySettings
                        finalMode = current.accountabilityMode
                    } else {
                        val serverMode = AccountabilityMode.fromCode(serverSettings.accountabilityMode)
                        val resolvedMode = if (isLinked && serverMode == AccountabilityMode.NONE) {
                            shouldPushLocal = true
                            AccountabilityMode.FRIEND
                        } else {
                            serverMode
                        }
                        val merged = serverSettings.copy(accountabilityMode = resolvedMode.code)
                        syncSettingsToAccessibilityService(merged, markDirty = false)
                        resolvedContext?.let { BlockingPreferences.saveAccountabilityMode(it, resolvedMode.code) }
                        if (shouldPushLocal) {
                            settingsToPush = merged
                        }
                        finalSettings = merged
                        finalMode = resolvedMode
                    }
                } else {
                    finalSettings = current.settings
                    finalMode = current.accountabilityMode
                }

                current.copy(
                    isLoading = false,
                    settings = finalSettings,
                    accountabilityMode = finalMode,
                    partner = if (partnerSuccess) partnerDto else current.partner,
                    isPartnerLinked = isLinked,
                    timeDelayUnlockAt = syncedUnlockAt,
                    timeDelayTarget = syncedTarget,
                    isTimeDelayServerReady = syncedServerReady,
                    errorMessage = null
                )
            }

            val pushResult = if (shouldPushLocal && settingsToPush != null) {
                pushSettingsToServerSuspend(settingsToPush!!)
            } else {
                Result.success(Unit)
            }

            if (partnerSuccess && partnerDto != null && !isLinked && partnerDto.status == "pending") {
                startPartnerPolling()
            } else if (partnerSuccess) {
                stopPartnerPolling()
            }

            val finalSuccess = settingsResult.isSuccess && pushResult.isSuccess
            if (finalSuccess) {
                autoSyncRetryAttempt = 0
                autoSyncRetryJob?.cancel()
                autoSyncRetryJob = null
                _uiState.update {
                    it.copy(
                        syncState = SyncState.SYNCED,
                        lastSyncedAtMs = System.currentTimeMillis()
                    )
                }
                Result.success(Unit)
            } else {
                _uiState.update { it.copy(syncState = SyncState.PENDING_RETRY) }
                scheduleAutoSyncRetry()
                Result.failure(
                    settingsResult.exceptionOrNull()
                        ?: pushResult.exceptionOrNull()
                        ?: IllegalStateException("Unable to reach server")
                )
            }
        }
    }

    fun setAdultBlocking(enabled: Boolean) = updateSetting { it.copy(blockAdultContent = enabled) }

    fun setShortsBlocking(enabled: Boolean) = updateSetting { it.copy(blockShorts = enabled) }

    fun setWhatsAppChannelsBlocking(enabled: Boolean) = updateSetting { it.copy(blockWhatsAppChannels = enabled) }

    fun setInstagramReelsBlocking(enabled: Boolean) = updateSetting { it.copy(blockInstagramReels = enabled) }

    fun setTikTokBlocking(enabled: Boolean) = updateSetting { it.copy(blockTikTok = enabled) }

    fun setUninstallProtection(enabled: Boolean) = updateSetting { it.copy(uninstallProtection = enabled) }

    fun setUnsupportedBrowsersBlocking(enabled: Boolean) = updateSetting { it.copy(blockUnsupportedBrowsers = enabled) }

    fun setNewlyInstalledAppsBlocking(enabled: Boolean) = updateSetting { it.copy(blockNewlyInstalledApps = enabled) }

    fun onDnsVpnPermissionGranted(context: Context) {
        BlockingPreferences.saveDnsVpnEnabled(context, true)
        _uiState.update { it.copy(isDnsVpnEnabled = true) }
        try {
            PGuardVpnService.start(context)
        } catch (e: Exception) {
            android.util.Log.w("BlockingViewModel", "Could not start PGuardVpnService: ${e.message}")
        }
    }

    fun setDnsVpnEnabled(enabled: Boolean, context: Context) {
        if (enabled) {
            onDnsVpnPermissionGranted(context)
        } else {
            BlockingPreferences.saveDnsVpnEnabled(context, false)
            _uiState.update { it.copy(isDnsVpnEnabled = false) }
            try {
                PGuardVpnService.stop(context)
            } catch (e: Exception) {
                android.util.Log.w("BlockingViewModel", "Could not stop PGuardVpnService: ${e.message}")
            }
        }
    }

    fun updateSetting(block: (UserSettingsDto) -> UserSettingsDto) {
        val currentState = _uiState.value
        val updatedLocal = block(currentState.settings).let {
            if (it.accountabilityMode.isBlank()) it.copy(accountabilityMode = currentState.accountabilityMode.code) else it
        }
        syncSettingsToAccessibilityService(updatedLocal, markDirty = true)
        _uiState.update {
            it.copy(
                settings = updatedLocal,
                syncState = if (isUserSignedIn) SyncState.SYNCING else SyncState.IDLE
            )
        }

        if (!isUserSignedIn) return

        // Cancel any superseded in-flight push so rapid toggles never overwrite newer local state
        updateSettingJob?.cancel()
        updateSettingJob = viewModelScope.launch {
            delay(250L) // Brief debounce coalesces rapid multi-toggle edits into a single PUT
            val latestState = _uiState.value
            val latest = latestState.settings.let {
                if (it.accountabilityMode.isBlank()) it.copy(accountabilityMode = latestState.accountabilityMode.code) else it
            }
            val request = UpdateSettingsRequest(
                blockAdultContent = latest.blockAdultContent,
                blockShorts = latest.blockShorts,
                blockWhatsAppChannels = latest.blockWhatsAppChannels,
                blockInstagramReels = latest.blockInstagramReels,
                blockTikTok = latest.blockTikTok,
                uninstallProtection = latest.uninstallProtection,
                blockUnsupportedBrowsers = latest.blockUnsupportedBrowsers,
                blockNewlyInstalledApps = latest.blockNewlyInstalledApps,
                blockedScreenMessage = latest.blockedScreenMessage,
                blockedScreenCountdown = latest.blockedScreenCountdown,
                redirectUrl = latest.redirectUrl,
                accountabilityMode = latest.accountabilityMode
            )
            settingsRepository.updateSettings(request).fold(
                onSuccess = { updatedServer ->
                    BlockingPreferences.markLocalDirty(resolvedContext, false)
                    syncSettingsToAccessibilityService(updatedServer, markDirty = false)
                    autoSyncRetryAttempt = 0
                    autoSyncRetryJob?.cancel()
                    autoSyncRetryJob = null
                    _uiState.update {
                        it.copy(
                            settings = updatedServer,
                            accountabilityMode = AccountabilityMode.fromCode(updatedServer.accountabilityMode),
                            syncState = SyncState.SYNCED,
                            lastSyncedAtMs = System.currentTimeMillis()
                        )
                    }
                },
                onFailure = { error ->
                    android.util.Log.w("BlockingViewModel", "Backend sync failed, will retry automatically: ${error.message}")
                    _uiState.update { it.copy(syncState = SyncState.PENDING_RETRY) }
                    scheduleAutoSyncRetry()
                }
            )
        }
    }

    fun openEditor(editor: SettingsEditor) {
        _uiState.update { current ->
            current.copy(
                editor = editor,
                editorText = when (editor) {
                    SettingsEditor.MESSAGE -> current.settings.blockedScreenMessage
                    SettingsEditor.REDIRECT -> current.settings.redirectUrl
                    SettingsEditor.COUNTDOWN -> current.settings.blockedScreenCountdown.toString()
                },
                editorError = null
            )
        }
    }

    fun updateEditorText(text: String) {
        _uiState.update { it.copy(editorText = text, editorError = null) }
    }

    fun dismissEditor() {
        _uiState.update { it.copy(editor = null, editorText = "", editorError = null) }
    }

    fun resetMessageEditorToDefault() {
        _uiState.update {
            it.copy(
                editorText = BlockedScreenConfig.DEFAULT_MESSAGE,
                editorError = null
            )
        }
    }

    fun clearRedirectEditor() {
        _uiState.update { it.copy(editorText = "", editorError = null) }
    }

    fun saveEditor() {
        val state = _uiState.value
        when (state.editor) {
            SettingsEditor.MESSAGE -> {
                when (val result = BlockedScreenConfig.validateMessage(state.editorText)) {
                    is BlockedScreenConfig.ValidationResult.Invalid -> {
                        _uiState.update { it.copy(editorError = result.error) }
                        return
                    }
                    is BlockedScreenConfig.ValidationResult.Valid -> {
                        updateSetting { it.copy(blockedScreenMessage = result.normalized) }
                    }
                }
            }
            SettingsEditor.REDIRECT -> {
                val result = BlockedScreenConfig.validateRedirectUrl(
                    raw = state.editorText,
                    customBlocklist = BlockingPreferences.customBlocklist,
                    blockAdultContent = state.settings.blockAdultContent,
                    blockShorts = state.settings.blockShorts,
                    blockWhatsAppChannels = state.settings.blockWhatsAppChannels,
                    blockInstagramReels = state.settings.blockInstagramReels,
                    blockTikTok = state.settings.blockTikTok
                )
                when (result) {
                    is BlockedScreenConfig.ValidationResult.Invalid -> {
                        _uiState.update { it.copy(editorError = result.error) }
                        return
                    }
                    is BlockedScreenConfig.ValidationResult.Valid -> {
                        updateSetting { it.copy(redirectUrl = result.normalized) }
                    }
                }
            }
            SettingsEditor.COUNTDOWN, null -> Unit
        }
        dismissEditor()
    }

    fun openBlockedScreenPreview() {
        _uiState.update { it.copy(isBlockedScreenPreviewVisible = true) }
    }

    fun dismissBlockedScreenPreview() {
        _uiState.update { it.copy(isBlockedScreenPreviewVisible = false) }
    }

    fun setCountdown(seconds: Int) {
        dismissEditor()
        updateSetting { it.copy(blockedScreenCountdown = seconds) }
    }

    fun showInfo(title: String, message: String) {
        _uiState.update { it.copy(infoDialog = InfoDialogState(title, message)) }
    }

    fun dismissInfo() {
        _uiState.update { it.copy(infoDialog = null) }
    }

    fun showGuide() {
        _uiState.update { it.copy(isGuideVisible = true) }
    }

    fun dismissGuide() {
        _uiState.update { it.copy(isGuideVisible = false) }
    }

    fun showInviteDialog() {
        _uiState.update {
            it.copy(
                isInviteDialogVisible = true,
                inviteEmail = "",
                inviteName = "",
                inviteEmailError = null
            )
        }
    }

    fun dismissInviteDialog() {
        _uiState.update {
            it.copy(
                isInviteDialogVisible = false,
                inviteEmailError = null
            )
        }
    }

    fun updateInviteEmail(email: String) {
        _uiState.update { it.copy(inviteEmail = email, inviteEmailError = null) }
    }

    fun updateInviteName(name: String) {
        _uiState.update { it.copy(inviteName = name) }
    }

    fun submitInvite() {
        val state = _uiState.value
        val email = state.inviteEmail.trim()
        if (!isValidEmailFormat(email)) {
            _uiState.update {
                it.copy(inviteEmailError = "Please enter a valid email address.")
            }
            return
        }

        val userEmail = currentUserEmail
        if (!userEmail.isNullOrBlank() && isSameEmail(email, userEmail)) {
            _uiState.update {
                it.copy(inviteEmailError = "You cannot use your own email as an accountability partner.")
            }
            return
        }

        val name = state.inviteName.trim()
        dismissInviteDialog()
        invitePartner(email, name)
    }

    fun resendPartnerInvite() {
        val partner = _uiState.value.partner ?: return
        if (_uiState.value.isSendingInvite || partner.status != "pending") return
        invitePartner(partner.partnerEmail, partner.partnerName)
    }

    fun confirmCancelPendingInvite() {
        _uiState.update { it.copy(isConfirmCancelInviteVisible = false) }
        disconnectPartner()
    }

    fun dismissCancelPendingInvite() {
        _uiState.update { it.copy(isConfirmCancelInviteVisible = false) }
    }

    fun disconnectPartner() {
        viewModelScope.launch {
            stopPartnerPolling()
            accountabilityRepository.disconnectPartner().onSuccess {
                resolvedContext?.let { BlockingPreferences.clearPartnerCache(it) }
                _uiState.update { it.copy(partner = null, isPartnerLinked = false) }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        infoDialog = InfoDialogState(
                            title = "Disconnect Failed",
                            message = err.message ?: "Could not remove accountability partner."
                        )
                    )
                }
            }
        }
    }

    fun invitePartner(email: String, name: String = "") {
        if (_uiState.value.isSendingInvite) return
        _uiState.update { it.copy(isSendingInvite = true) }
        viewModelScope.launch {
            accountabilityRepository.invitePartner(email, name).onSuccess { partner ->
                _uiState.update {
                    it.copy(
                        partner = partner,
                        isPartnerLinked = partner.status == "accepted",
                        isSendingInvite = false,
                        infoDialog = InfoDialogState(
                            title = "Invite Sent",
                            message = "An invitation email was sent to ${partner.partnerEmail}. Once they confirm, your partner shield becomes active."
                        )
                    )
                }
                startPartnerPolling()
            }.onFailure { err ->
                val friendlyMessage = if (err.message?.contains("invalid or expired token", ignoreCase = true) == true) {
                    "Your session has expired. Please log out and sign in again."
                } else {
                    err.message ?: "Could not send partner invite. Please try again."
                }
                _uiState.update {
                    it.copy(
                        isSendingInvite = false,
                        infoDialog = InfoDialogState(
                            title = "Invite Failed",
                            message = friendlyMessage
                        )
                    )
                }
            }
        }
    }

    fun refreshPartnerStatus() {
        viewModelScope.launch {
            accountabilityRepository.fetchPartner().onSuccess { res ->
                val partner = res.partner
                val linked = res.isLinked || (partner != null && partner.status == "accepted")
                resolvedContext?.let { ctx ->
                    BlockingPreferences.savePartnerCache(
                        context = ctx,
                        isLinked = linked,
                        email = if (linked) partner?.partnerEmail.orEmpty() else "",
                        name = if (linked) partner?.partnerName.orEmpty() else ""
                    )
                }
                _uiState.update { current ->
                    current.copy(
                        partner = partner,
                        isPartnerLinked = linked
                    )
                }
                if (partner != null && !linked && partner.status == "pending") {
                    startPartnerPolling()
                } else {
                    stopPartnerPolling()
                }
            }

            if (_uiState.value.timeDelayTarget.isNotBlank() || BlockingPreferences.timeDelayTarget.isNotBlank()) {
                refreshTimeDelayFromServer()
            }
        }
    }

    private suspend fun refreshTimeDelayFromServer() {
        accountabilityRepository.getTimeDelay().onSuccess { tdResp ->
            val td = tdResp.timeDelay
            val ctx = resolvedContext
            if (td != null) {
                val nowWall = System.currentTimeMillis()
                val unlockAtWall = if (tdResp.isReady) nowWall - 1_000L else nowWall + tdResp.remainingMs
                if (ctx != null) {
                    BlockingPreferences.setTimeDelay(
                        context = ctx,
                        unlockAtWallMs = unlockAtWall,
                        target = td.targetKey,
                        durationMs = tdResp.remainingMs.coerceAtLeast(0L),
                        serverBacked = true
                    )
                }
                _uiState.update {
                    it.copy(
                        timeDelayUnlockAt = unlockAtWall,
                        timeDelayTarget = td.targetKey,
                        isTimeDelayServerReady = tdResp.isReady
                    )
                }
            } else if (BlockingPreferences.isTimeDelayServerBacked) {
                if (ctx != null) {
                    BlockingPreferences.clearTimeDelay(ctx)
                    TimeDelayNotificationScheduler.cancel(ctx)
                }
                _uiState.update {
                    it.copy(
                        timeDelayUnlockAt = 0L,
                        timeDelayTarget = "",
                        isTimeDelayServerReady = false
                    )
                }
            }
        }
    }

    fun startPartnerPolling() {
        if (partnerPollingJob?.isActive == true) return
        partnerPollingJob = viewModelScope.launch {
            val backoffIntervals = longArrayOf(3_000L, 5_000L, 10_000L, 15_000L, 30_000L)
            var attempt = 0
            var consecutiveFailures = 0

            while (isActive) {
                val waitMs = backoffIntervals[minOf(attempt, backoffIntervals.lastIndex)]
                delay(waitMs)
                attempt++

                val res = accountabilityRepository.fetchPartner()
                if (res.isSuccess) {
                    consecutiveFailures = 0
                    val body = res.getOrNull()
                    val partner = body?.partner
                    val linked = (body?.isLinked == true) || (partner != null && partner.status == "accepted")
                    resolvedContext?.let { ctx ->
                        BlockingPreferences.savePartnerCache(
                            context = ctx,
                            isLinked = linked,
                            email = if (linked) partner?.partnerEmail.orEmpty() else "",
                            name = if (linked) partner?.partnerName.orEmpty() else ""
                        )
                    }
                    _uiState.update { current ->
                        current.copy(
                            partner = partner,
                            isPartnerLinked = linked
                        )
                    }
                    if (linked || partner == null || partner.status != "pending") {
                        stopPartnerPolling()
                        return@launch
                    }
                } else {
                    consecutiveFailures++
                    if (consecutiveFailures >= 5) {
                        stopPartnerPolling()
                        return@launch
                    }
                }
            }
        }
    }

    fun stopPartnerPolling() {
        partnerPollingJob?.cancel()
        partnerPollingJob = null
    }

    override fun onCleared() {
        super.onCleared()
        stopPartnerPolling()
    }

    fun dismissError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun openModeSelection() {
        _uiState.update { it.copy(isModeSelectionVisible = true) }
    }

    fun dismissModeSelection() {
        _uiState.update { it.copy(isModeSelectionVisible = false) }
    }

    fun requestChangeAccountabilityMode(context: Context) {
        val currentMode = _uiState.value.accountabilityMode
        if (currentMode == AccountabilityMode.NONE) {
            openModeSelection()
            return
        }
        guardAction(ProtectedAction.OpenChangeMode, context)
    }

    fun requestSetAccountabilityMode(newMode: AccountabilityMode, context: Context) {
        dismissModeSelection()
        val currentMode = _uiState.value.accountabilityMode
        if (newMode == currentMode) return

        if (newMode == AccountabilityMode.NONE && _uiState.value.settings.uninstallProtection) {
            _uiState.update {
                it.copy(
                    infoDialog = InfoDialogState(
                        title = "Uninstall Protection Active",
                        message = "Uninstall Protection requires an active accountability check (Friend, Myself, or 24-Hour Delay). Please turn off Uninstall Protection before switching to None."
                    )
                )
            }
            return
        }

        applyAccountabilityMode(newMode, context)
    }

    fun applyAccountabilityMode(mode: AccountabilityMode, context: Context) {
        BlockingPreferences.ensureContext(context)
        BlockingPreferences.saveAccountabilityMode(context, mode.code)
        _uiState.update { current ->
            current.copy(
                accountabilityMode = mode,
                settings = current.settings.copy(accountabilityMode = mode.code)
            )
        }
        updateSetting { it.copy(accountabilityMode = mode.code) }

        val hasPartner = _uiState.value.partner != null || BlockingPreferences.isPartnerLinkedCached
        if (mode == AccountabilityMode.FRIEND && !hasPartner) {
            showInviteDialog()
        }
    }

    fun setAccountabilityMode(mode: AccountabilityMode, context: Context) {
        requestSetAccountabilityMode(mode, context)
    }

    fun showDeviceAdminDialog() {
        _uiState.update { it.copy(isDeviceAdminDialogVisible = true) }
    }

    fun dismissDeviceAdminDialog() {
        pendingDeviceAdminActivation = false
        _uiState.update { it.copy(isDeviceAdminDialogVisible = false) }
    }

    fun markDeviceAdminRequestLaunched() {
        pendingDeviceAdminActivation = true
        _uiState.update { it.copy(isDeviceAdminDialogVisible = false) }
    }

    fun onConfirmDeviceAdmin(context: Context) {
        markDeviceAdminRequestLaunched()
        DeviceAdminManager.requestEnableDeviceAdmin(context)
    }

    fun onDeviceAdminActivityResult(context: Context) {
        pendingDeviceAdminActivation = false
        if (DeviceAdminManager.isDeviceAdminActive(context)) {
            if (_uiState.value.accountabilityMode == AccountabilityMode.NONE) {
                applyAccountabilityMode(AccountabilityMode.SELF, context)
            }
            if (!_uiState.value.settings.uninstallProtection) {
                executeToggle(BlockingToggle.UNINSTALL_PROTECTION, true, context)
            }
        }
    }

    fun checkDeviceAdminStatus(context: Context) {
        if (pendingDeviceAdminActivation) {
            onDeviceAdminActivityResult(context)
            return
        }
        val isAdminActive = DeviceAdminManager.isDeviceAdminActive(context)
        val currentSetting = _uiState.value.settings.uninstallProtection

        if (isAdminActive && !currentSetting && _uiState.value.accountabilityMode != AccountabilityMode.NONE) {
            executeToggle(BlockingToggle.UNINSTALL_PROTECTION, true, context)
        }
    }

    fun requestDisconnectPartner(context: Context) {
        val partner = _uiState.value.partner ?: return
        val isLinked = _uiState.value.isPartnerLinked || BlockingPreferences.isPartnerLinkedCached
        if (!isLinked || partner.status == "pending") {
            _uiState.update { it.copy(isConfirmCancelInviteVisible = true) }
            return
        }

        guardAction(ProtectedAction.DisconnectPartner, context)
    }

    fun openSecurityAlerts() {
        _uiState.update { it.copy(isSecurityAlertsDialogVisible = true) }
        refreshSecurityAlerts()
    }

    fun dismissSecurityAlerts() {
        _uiState.update { it.copy(isSecurityAlertsDialogVisible = false) }
    }

    fun refreshSecurityAlerts() {
        _uiState.update { it.copy(isLoadingSecurityAlerts = true) }
        viewModelScope.launch {
            accountabilityRepository.fetchSecurityEvents(limit = 30).fold(
                onSuccess = { events ->
                    _uiState.update { it.copy(securityAlerts = events, isLoadingSecurityAlerts = false) }
                },
                onFailure = {
                    _uiState.update { it.copy(isLoadingSecurityAlerts = false) }
                }
            )
        }
    }

    fun handleToggleRequested(toggle: BlockingToggle, checked: Boolean, context: Context) {
        if (toggle == BlockingToggle.UNINSTALL_PROTECTION) {
            if (checked) {
                if (!PGuardAccessibilityService.isEnabled(context)) {
                    _uiState.update {
                        it.copy(
                            infoDialog = InfoDialogState(
                                title = "Accessibility Service Required",
                                message = "Please turn on PGuard's Accessibility Service first so Uninstall Protection can stop tampering in system settings."
                            )
                        )
                    }
                    return
                }

                if (_uiState.value.accountabilityMode == AccountabilityMode.FRIEND) {
                    val isLinked = _uiState.value.isPartnerLinked || BlockingPreferences.isPartnerLinkedCached
                    if (!isLinked) {
                        _uiState.update {
                            it.copy(
                                infoDialog = InfoDialogState(
                                    title = "Accountability Partner Required",
                                    message = "You selected Friend accountability. Please link an active partner before activating Uninstall Protection, or switch to Myself in the Accountability card."
                                )
                            )
                        }
                        return
                    }
                }

                if (DeviceAdminManager.isDeviceAdminActive(context)) {
                    if (_uiState.value.accountabilityMode == AccountabilityMode.NONE) {
                        applyAccountabilityMode(AccountabilityMode.SELF, context)
                    }
                    executeToggle(toggle, true, context)
                } else {
                    showDeviceAdminDialog()
                }
                return
            } else {
                guardAction(ProtectedAction.ToggleFeature(toggle), context)
                return
            }
        }

        if (checked) {
            executeToggle(toggle, true, context)
            return
        }

        guardAction(ProtectedAction.ToggleFeature(toggle), context)
    }

    fun guardAction(action: ProtectedAction, context: Context) {
        val currentMode = _uiState.value.accountabilityMode
        when (currentMode) {
            AccountabilityMode.NONE -> {
                executeProtectedAction(action, context)
            }
            AccountabilityMode.SELF -> {
                _uiState.update { it.copy(typingChallengeTarget = action) }
            }
            AccountabilityMode.TIME_DELAY -> {
                val serverReady = _uiState.value.isTimeDelayServerReady
                if (BlockingPreferences.isTimeDelayElapsed(context, action.identifier, serverConfirmedReady = serverReady)) {
                    cancelTimeDelay(context)
                    executeProtectedAction(action, context)
                } else {
                    _uiState.update { it.copy(timeDelayConfirmTarget = action) }
                }
            }
            AccountabilityMode.FRIEND -> {
                val isLinked = _uiState.value.isPartnerLinked || BlockingPreferences.isPartnerLinkedCached
                if (isLinked) {
                    _uiState.update {
                        it.copy(
                            friendApprovalTarget = action,
                            unlockCodeError = null,
                            unlockRequestError = null,
                            unlockEmailSent = false,
                            isSendingUnlockCode = false
                        )
                    }
                } else {
                    executeProtectedAction(action, context)
                }
            }
        }
    }

    private fun executeProtectedAction(action: ProtectedAction, context: Context) {
        when (action) {
            is ProtectedAction.ToggleFeature -> {
                executeToggle(action.toggle, false, context)
            }
            is ProtectedAction.OpenChangeMode -> {
                openModeSelection()
            }
            is ProtectedAction.ChangeMode -> {
                applyAccountabilityMode(action.targetMode, context)
            }
            is ProtectedAction.DisconnectPartner -> {
                disconnectPartner()
            }
            is ProtectedAction.Logout, is ProtectedAction.UninstallApp -> Unit
        }
    }

    fun onTypingChallengeSuccess(context: Context) {
        val action = _uiState.value.typingChallengeTarget
        _uiState.update { it.copy(typingChallengeTarget = null) }
        if (action != null) {
            executeProtectedAction(action, context)
        }
    }

    fun dismissTypingChallenge() {
        _uiState.update { it.copy(typingChallengeTarget = null) }
    }

    fun onStartTimeDelay(context: Context) {
        val action = _uiState.value.timeDelayConfirmTarget ?: return
        startTimeDelayForTarget(action.identifier, context, action.title)
    }

    fun startTimeDelayForTarget(
        targetId: String,
        context: Context,
        targetTitle: String = targetDisplayName(targetId)
    ) {
        val durationMs = BlockingPreferences.DEFAULT_TIME_DELAY_MS
        val unlockAt = System.currentTimeMillis() + durationMs
        BlockingPreferences.setTimeDelay(
            context = context,
            unlockAtWallMs = unlockAt,
            target = targetId,
            durationMs = durationMs,
            serverBacked = false
        )
        TimeDelayNotificationScheduler.schedule(context, unlockAt, targetTitle)
        _uiState.update {
            it.copy(
                timeDelayConfirmTarget = null,
                timeDelayUnlockAt = unlockAt,
                timeDelayTarget = targetId,
                isTimeDelayServerReady = false
            )
        }

        viewModelScope.launch {
            accountabilityRepository.startTimeDelay(targetId, targetTitle, durationMs / 1000L).onSuccess { tdResp ->
                val serverUnlockWall = System.currentTimeMillis() + tdResp.remainingMs
                BlockingPreferences.setTimeDelay(
                    context = context,
                    unlockAtWallMs = serverUnlockWall,
                    target = targetId,
                    durationMs = tdResp.remainingMs,
                    serverBacked = true
                )
                TimeDelayNotificationScheduler.schedule(context, serverUnlockWall, targetTitle)
                _uiState.update {
                    it.copy(
                        timeDelayUnlockAt = serverUnlockWall,
                        timeDelayTarget = targetId,
                        isTimeDelayServerReady = tdResp.isReady
                    )
                }
            }
        }
    }

    fun cancelTimeDelay(context: Context) {
        BlockingPreferences.clearTimeDelay(context)
        TimeDelayNotificationScheduler.cancel(context)
        _uiState.update {
            it.copy(
                timeDelayUnlockAt = 0L,
                timeDelayTarget = "",
                isTimeDelayServerReady = false
            )
        }
        viewModelScope.launch {
            accountabilityRepository.clearTimeDelay()
        }
    }

    fun completeReadyTimeDelay(context: Context) {
        val targetId = _uiState.value.timeDelayTarget.ifBlank { BlockingPreferences.timeDelayTarget }
        if (targetId.isBlank()) {
            cancelTimeDelay(context)
            return
        }

        if (BlockingPreferences.isTimeDelayElapsed(
                context,
                targetId,
                serverConfirmedReady = _uiState.value.isTimeDelayServerReady
            )
        ) {
            performTimeDelayUnlockForTarget(targetId, context)
            return
        }

        if (BlockingPreferences.isTimeDelayServerBacked) {
            viewModelScope.launch {
                accountabilityRepository.getTimeDelay().fold(
                    onSuccess = { resp ->
                        if (resp.isReady || resp.timeDelay == null) {
                            _uiState.update { it.copy(isTimeDelayServerReady = true) }
                            performTimeDelayUnlockForTarget(targetId, context)
                        } else {
                            _uiState.update {
                                it.copy(
                                    infoDialog = InfoDialogState(
                                        title = "Unlock Pending",
                                        message = "Your 24-hour cooling-off timer is still active on the server."
                                    )
                                )
                            }
                        }
                    },
                    onFailure = {
                        _uiState.update {
                            it.copy(
                                infoDialog = InfoDialogState(
                                    title = "Verification Required",
                                    message = "Connect to the internet to verify that the 24-hour cooling-off timer has completed after rebooting."
                                )
                            )
                        }
                    }
                )
            }
        }
    }

    private fun performTimeDelayUnlockForTarget(targetId: String, context: Context) {
        cancelTimeDelay(context)
        when {
            targetId == ProtectedAction.OpenChangeMode.identifier -> openModeSelection()
            targetId == ProtectedAction.DisconnectPartner.identifier -> disconnectPartner()
            targetId == "FOCUS_QUICK" -> FocusModeManager.stopQuickFocus(context)
            targetId.startsWith("FOCUS_SCHEDULE_TOGGLE:") -> {
                val scheduleId = targetId.removePrefix("FOCUS_SCHEDULE_TOGGLE:")
                FocusModeManager.toggleSchedule(context, scheduleId, false)
            }
            targetId.startsWith("FOCUS_SCHEDULE_DELETE:") -> {
                val scheduleId = targetId.removePrefix("FOCUS_SCHEDULE_DELETE:")
                FocusModeManager.deleteSchedule(context, scheduleId)
            }
            targetId.startsWith("FOCUS_SCHEDULE:") -> {
                val scheduleId = targetId.removePrefix("FOCUS_SCHEDULE:")
                FocusModeManager.toggleSchedule(context, scheduleId, false)
            }
            targetId.startsWith("BLOCKLIST_APP:") -> {
                val pkg = targetId.removePrefix("BLOCKLIST_APP:")
                BlockingPreferences.removeBlockedApp(context, pkg)
            }
            targetId.startsWith("BLOCKLIST_KW:") -> {
                val kw = targetId.removePrefix("BLOCKLIST_KW:")
                BlockingPreferences.removeCustomBlockedKeyword(context, kw)
            }
            targetId.startsWith("WHITELIST_ADD_APP:") -> {
                val rawPkgs = targetId.removePrefix("WHITELIST_ADD_APP:")
                rawPkgs.split(',').filter { it.isNotBlank() }.forEach { pkg ->
                    BlockingPreferences.addWhitelistedApp(context, pkg.trim())
                }
            }
            targetId.startsWith("WHITELIST_ADD_KW:") -> {
                val kw = targetId.removePrefix("WHITELIST_ADD_KW:")
                BlockingPreferences.addWhitelistedKeyword(context, kw)
            }
            else -> {
                val toggle = BlockingToggle.entries.firstOrNull { it.name == targetId }
                if (toggle != null) {
                    executeToggle(toggle, false, context)
                }
            }
        }
    }

    fun dismissTimeDelayConfirm() {
        _uiState.update { it.copy(timeDelayConfirmTarget = null) }
    }

    fun verifyFriendApprovalCode(enteredCode: String, context: Context) {
        val action = _uiState.value.friendApprovalTarget ?: return
        if (_uiState.value.isVerifyingUnlockCode) return

        val trimmed = enteredCode.trim()
        if (trimmed.length != 6) {
            _uiState.update { it.copy(unlockCodeError = "Please enter the 6-digit approval code.") }
            return
        }

        _uiState.update { it.copy(isVerifyingUnlockCode = true, unlockCodeError = null) }
        viewModelScope.launch {
            accountabilityRepository.verifyUnlockCode(trimmed, action.title).fold(
                onSuccess = {
                    _uiState.update {
                        it.copy(
                            friendApprovalTarget = null,
                            isVerifyingUnlockCode = false,
                            unlockCodeError = null,
                            unlockRequestError = null,
                            unlockEmailSent = false
                        )
                    }
                    executeProtectedAction(action, context)
                },
                onFailure = { err ->
                    _uiState.update {
                        it.copy(
                            isVerifyingUnlockCode = false,
                            unlockCodeError = err.message ?: "Verification failed. Please check the code and connection."
                        )
                    }
                }
            )
        }
    }

    fun verifyExternalUnlockCode(
        enteredCode: String,
        featureName: String? = null,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        if (_uiState.value.isVerifyingUnlockCode) return

        val trimmed = enteredCode.trim()
        if (trimmed.length != 6) {
            val msg = "Please enter the 6-digit approval code."
            _uiState.update { it.copy(unlockCodeError = msg) }
            onError(msg)
            return
        }

        _uiState.update { it.copy(isVerifyingUnlockCode = true, unlockCodeError = null) }
        viewModelScope.launch {
            accountabilityRepository.verifyUnlockCode(trimmed, featureName).fold(
                onSuccess = {
                    _uiState.update {
                        it.copy(
                            isVerifyingUnlockCode = false,
                            unlockCodeError = null,
                            unlockRequestError = null,
                            unlockEmailSent = false
                        )
                    }
                    onSuccess()
                },
                onFailure = { err ->
                    val msg = err.message ?: "Verification failed. Please check the code and connection."
                    _uiState.update {
                        it.copy(
                            isVerifyingUnlockCode = false,
                            unlockCodeError = msg
                        )
                    }
                    onError(msg)
                }
            )
        }
    }

    fun clearUnlockErrors() {
        _uiState.update {
            it.copy(
                unlockCodeError = null,
                unlockRequestError = null,
                isVerifyingUnlockCode = false,
                isSendingUnlockCode = false,
                unlockEmailSent = false
            )
        }
    }

    fun dismissFriendApproval() {
        _uiState.update {
            it.copy(
                friendApprovalTarget = null,
                isVerifyingUnlockCode = false,
                isSendingUnlockCode = false,
                unlockEmailSent = false,
                unlockCodeError = null,
                unlockRequestError = null
            )
        }
    }

    fun requestPartnerApprovalCode(featureName: String) {
        _uiState.update {
            it.copy(
                isSendingUnlockCode = true,
                unlockEmailSent = false,
                unlockRequestError = null,
                unlockCodeError = null
            )
        }
        viewModelScope.launch {
            accountabilityRepository.requestUnlockCode(featureName).fold(
                onSuccess = {
                    _uiState.update {
                        it.copy(
                            isSendingUnlockCode = false,
                            unlockEmailSent = true,
                            unlockRequestError = null
                        )
                    }
                },
                onFailure = { err ->
                    android.util.Log.w("BlockingViewModel", "Failed to dispatch unlock request: ${err.message}")
                    _uiState.update {
                        it.copy(
                            isSendingUnlockCode = false,
                            unlockEmailSent = false,
                            unlockRequestError = err.message ?: "Could not reach server"
                        )
                    }
                }
            )
        }
    }

    fun executeToggle(toggle: BlockingToggle, enabled: Boolean, context: Context) {
        BlockingPreferences.ensureContext(context)
        when (toggle) {
            BlockingToggle.ADULT_CONTENT -> setAdultBlocking(enabled)
            BlockingToggle.DNS_VPN_SHIELD -> setDnsVpnEnabled(enabled, context)
            BlockingToggle.SHORTS -> setShortsBlocking(enabled)
            BlockingToggle.WHATSAPP_CHANNELS -> setWhatsAppChannelsBlocking(enabled)
            BlockingToggle.INSTAGRAM_REELS -> setInstagramReelsBlocking(enabled)
            BlockingToggle.TIKTOK -> setTikTokBlocking(enabled)
            BlockingToggle.UNINSTALL_PROTECTION -> {
                setUninstallProtection(enabled)
                if (!enabled) {
                    DeviceAdminManager.deactivateDeviceAdmin(context)
                }
            }
            BlockingToggle.UNSUPPORTED_BROWSERS -> setUnsupportedBrowsersBlocking(enabled)
            BlockingToggle.NEW_INSTALLED_APPS -> setNewlyInstalledAppsBlocking(enabled)
        }

        if (!enabled) {
            val featureLabel = featureDisplayName(toggle.name)
            SecurityAlertReporter.reportIfPartnerLinked(
                context = context,
                eventType = "protection_disabled",
                message = "$featureLabel protection was turned off."
            )
        }
    }

    private fun syncSettingsToAccessibilityService(settings: UserSettingsDto, markDirty: Boolean = false) {
        BlockingPreferences.saveSettings(
            context = resolvedContext,
            adult = settings.blockAdultContent,
            shorts = settings.blockShorts,
            whatsapp = settings.blockWhatsAppChannels,
            instagramReels = settings.blockInstagramReels,
            tiktok = settings.blockTikTok,
            uninstall = settings.uninstallProtection,
            unsupportedBrowsers = settings.blockUnsupportedBrowsers,
            newInstalledApps = settings.blockNewlyInstalledApps,
            message = settings.blockedScreenMessage,
            countdown = settings.blockedScreenCountdown,
            redirect = settings.redirectUrl,
            markDirty = markDirty
        )
    }

    private suspend fun pushSettingsToServerSuspend(settings: UserSettingsDto): Result<Unit> {
        val request = UpdateSettingsRequest(
            blockAdultContent = settings.blockAdultContent,
            blockShorts = settings.blockShorts,
            blockWhatsAppChannels = settings.blockWhatsAppChannels,
            blockInstagramReels = settings.blockInstagramReels,
            blockTikTok = settings.blockTikTok,
            uninstallProtection = settings.uninstallProtection,
            blockUnsupportedBrowsers = settings.blockUnsupportedBrowsers,
            blockNewlyInstalledApps = settings.blockNewlyInstalledApps,
            blockedScreenMessage = settings.blockedScreenMessage,
            blockedScreenCountdown = settings.blockedScreenCountdown,
            redirectUrl = settings.redirectUrl,
            accountabilityMode = settings.accountabilityMode.ifBlank { _uiState.value.accountabilityMode.code }
        )
        return settingsRepository.updateSettings(request).map {
            BlockingPreferences.markLocalDirty(resolvedContext, false)
        }.onFailure { err ->
            android.util.Log.w("BlockingViewModel", "Failed to sync local protections to server: ${err.message}")
        }
    }
}
