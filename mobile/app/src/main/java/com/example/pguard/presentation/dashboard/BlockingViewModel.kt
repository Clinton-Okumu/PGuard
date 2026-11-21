package com.example.pguard.presentation.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pguard.core.datastore.AuthDataStore
import com.example.pguard.core.network.model.AccountabilityPartnerDto
import com.example.pguard.core.network.model.UpdateSettingsRequest
import com.example.pguard.core.network.model.UserSettingsDto
import com.example.pguard.data.repository.AccountabilityRepository
import com.example.pguard.data.repository.SettingsRepository
import com.example.pguard.engine.accessibility.BlockingPreferences
import com.example.pguard.engine.admin.DeviceAdminManager
import kotlinx.coroutines.Job
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

data class BlockingUiState(
    val settings: UserSettingsDto = UserSettingsDto(),
    val partner: AccountabilityPartnerDto? = null,
    val isPartnerLinked: Boolean = false,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val editor: SettingsEditor? = null,
    val editorText: String = "",
    val infoDialog: InfoDialogState? = null,
    val isGuideVisible: Boolean = false,
    val isInviteDialogVisible: Boolean = false,
    val inviteEmail: String = "",
    val accountabilityMode: AccountabilityMode = AccountabilityMode.NONE,
    val isModeSelectionVisible: Boolean = false,
    val typingChallengeTarget: ProtectedAction? = null,
    val timeDelayConfirmTarget: ProtectedAction? = null,
    val friendApprovalTarget: ProtectedAction? = null,
    val timeDelayUnlockAt: Long = 0L,
    val timeDelayTarget: String = "",
    val inviteEmailError: String? = null,
    val isVerifyingUnlockCode: Boolean = false,
    val unlockCodeError: String? = null,
    val unlockRequestError: String? = null,
    val isDeviceAdminDialogVisible: Boolean = false
)

class BlockingViewModel(
    private val settingsRepository: SettingsRepository,
    private val accountabilityRepository: AccountabilityRepository,
    private val authDataStore: AuthDataStore? = null
) : ViewModel() {

    private var currentUserEmail: String? = null
    private var partnerPollingJob: Job? = null
    private var loadDataJob: Job? = null

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
                redirectUrl = BlockingPreferences.redirectUrl
            ),
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
                        if (user != null) {
                            loadData()
                        } else {
                            stopPartnerPolling()
                            loadDataJob?.cancel()
                            loadDataJob = null
                            _uiState.update { it.copy(isLoading = false, partner = null, isPartnerLinked = false) }
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

    fun refreshFromLocalPreferences(context: android.content.Context? = null) {
        if (context != null) {
            BlockingPreferences.loadSettings(context)
        }
        _uiState.update { current ->
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
                    redirectUrl = BlockingPreferences.redirectUrl
                ),
                accountabilityMode = AccountabilityMode.fromCode(BlockingPreferences.accountabilityMode),
                timeDelayUnlockAt = BlockingPreferences.timeDelayUnlockAt,
                timeDelayTarget = BlockingPreferences.timeDelayTarget
            )
        }
    }

    fun loadData() {
        loadDataJob?.cancel()
        loadDataJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }

            val settingsResult = settingsRepository.fetchSettings()
            val partnerResult = accountabilityRepository.fetchPartner()

            val partnerDto = partnerResult.getOrNull()?.partner
            val isLinked = partnerResult.getOrNull()?.isLinked ?: false || (partnerDto != null && partnerDto.status == "accepted")

            var shouldPushLocal = false
            var settingsToPush: UserSettingsDto? = null

            _uiState.update { current ->
                val serverSettings = settingsResult.getOrNull()
                val finalSettings = if (serverSettings != null) {
                    if (BlockingPreferences.isLocalDirty) {
                        // Local changes haven't synced to server yet; preserve local state and push to server
                        shouldPushLocal = true
                        settingsToPush = current.settings
                        current.settings
                    } else {
                        syncSettingsToAccessibilityService(serverSettings, markDirty = false)
                        serverSettings
                    }
                } else {
                    current.settings
                }

                current.copy(
                    isLoading = false,
                    settings = finalSettings,
                    partner = partnerDto,
                    isPartnerLinked = isLinked,
                    errorMessage = null
                )
            }

            if (shouldPushLocal && settingsToPush != null) {
                pushSettingsToServer(settingsToPush!!)
            }

            if (partnerDto != null && !isLinked && partnerDto.status == "pending") {
                startPartnerPolling()
            } else {
                stopPartnerPolling()
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

    fun updateSetting(block: (UserSettingsDto) -> UserSettingsDto) {
        val currentSettings = _uiState.value.settings
        val updatedLocal = block(currentSettings)
        syncSettingsToAccessibilityService(updatedLocal, markDirty = true)
        _uiState.update { it.copy(settings = updatedLocal) }

        val request = UpdateSettingsRequest(
            blockAdultContent = updatedLocal.blockAdultContent,
            blockShorts = updatedLocal.blockShorts,
            blockWhatsAppChannels = updatedLocal.blockWhatsAppChannels,
            blockInstagramReels = updatedLocal.blockInstagramReels,
            blockTikTok = updatedLocal.blockTikTok,
            uninstallProtection = updatedLocal.uninstallProtection,
            blockUnsupportedBrowsers = updatedLocal.blockUnsupportedBrowsers,
            blockNewlyInstalledApps = updatedLocal.blockNewlyInstalledApps,
            blockedScreenMessage = updatedLocal.blockedScreenMessage,
            blockedScreenCountdown = updatedLocal.blockedScreenCountdown,
            redirectUrl = updatedLocal.redirectUrl
        )

        viewModelScope.launch {
            settingsRepository.updateSettings(request).fold(
                onSuccess = { updatedServer ->
                    BlockingPreferences.markLocalDirty(null, false)
                    syncSettingsToAccessibilityService(updatedServer, markDirty = false)
                    _uiState.update { it.copy(settings = updatedServer) }
                },
                onFailure = { error ->
                    // DO NOT roll back the user's local protection setting!
                    // Device-level blocking remains active on the phone even when backend is offline.
                    android.util.Log.w("BlockingViewModel", "Backend sync failed, keeping local protection setting: ${error.message}")
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
                }
            )
        }
    }

    fun updateEditorText(text: String) {
        _uiState.update { it.copy(editorText = text) }
    }

    fun dismissEditor() {
        _uiState.update { it.copy(editor = null, editorText = "") }
    }

    fun saveEditor() {
        val state = _uiState.value
        when (state.editor) {
            SettingsEditor.MESSAGE ->
                if (state.editorText.isNotBlank()) {
                    updateSetting { it.copy(blockedScreenMessage = state.editorText.trim()) }
                }
            SettingsEditor.REDIRECT ->
                updateSetting { it.copy(redirectUrl = state.editorText.trim()) }
            SettingsEditor.COUNTDOWN, null -> Unit
        }
        dismissEditor()
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
        _uiState.update { it.copy(isInviteDialogVisible = true, inviteEmail = "", inviteEmailError = null) }
    }

    fun dismissInviteDialog() {
        _uiState.update { it.copy(isInviteDialogVisible = false, inviteEmailError = null) }
    }

    fun updateInviteEmail(email: String) {
        _uiState.update { it.copy(inviteEmail = email, inviteEmailError = null) }
    }

    fun submitInvite() {
        val email = _uiState.value.inviteEmail.trim()
        if (email.isEmpty()) return

        val userEmail = currentUserEmail
        if (!userEmail.isNullOrBlank() && isSameEmail(email, userEmail)) {
            _uiState.update {
                it.copy(inviteEmailError = "You cannot use your own email as an accountability partner.")
            }
            return
        }

        dismissInviteDialog()
        invitePartner(email)
    }

    fun disconnectPartner() {
        viewModelScope.launch {
            stopPartnerPolling()
            accountabilityRepository.disconnectPartner().onSuccess {
                _uiState.update { it.copy(partner = null, isPartnerLinked = false) }
            }
        }
    }

    fun invitePartner(email: String, name: String = "") {
        viewModelScope.launch {
            accountabilityRepository.invitePartner(email, name).onSuccess { partner ->
                _uiState.update { it.copy(partner = partner, isPartnerLinked = false) }
                startPartnerPolling()
            }.onFailure { err ->
                val friendlyMessage = if (err.message?.contains("invalid or expired token", ignoreCase = true) == true) {
                    "Your session has expired. Please log out and sign in again."
                } else {
                    err.message ?: "Could not send partner invite. Please try again."
                }
                _uiState.update {
                    it.copy(
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
        }
    }

    fun startPartnerPolling() {
        if (partnerPollingJob?.isActive == true) return
        partnerPollingJob = viewModelScope.launch {
            while (isActive) {
                delay(3000)
                accountabilityRepository.fetchPartner().onSuccess { res ->
                    val partner = res.partner
                    val linked = res.isLinked || (partner != null && partner.status == "accepted")
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

    fun requestChangeAccountabilityMode(context: android.content.Context) {
        val currentMode = _uiState.value.accountabilityMode
        if (currentMode == AccountabilityMode.NONE) {
            openModeSelection()
            return
        }
        guardAction(ProtectedAction.OpenChangeMode, context)
    }

    fun requestSetAccountabilityMode(newMode: AccountabilityMode, context: android.content.Context) {
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

    fun applyAccountabilityMode(mode: AccountabilityMode, context: android.content.Context) {
        BlockingPreferences.saveAccountabilityMode(context, mode.code)
        _uiState.update { it.copy(accountabilityMode = mode) }
        if (mode == AccountabilityMode.FRIEND && _uiState.value.partner == null) {
            showInviteDialog()
        }
    }

    fun setAccountabilityMode(mode: AccountabilityMode, context: android.content.Context) {
        requestSetAccountabilityMode(mode, context)
    }

    fun showDeviceAdminDialog() {
        _uiState.update { it.copy(isDeviceAdminDialogVisible = true) }
    }

    fun dismissDeviceAdminDialog(context: android.content.Context? = null) {
        _uiState.update { it.copy(isDeviceAdminDialogVisible = false) }
        context?.let { ctx ->
            if (!DeviceAdminManager.isDeviceAdminActive(ctx)) {
                executeToggle(BlockingToggle.UNINSTALL_PROTECTION, false, ctx)
            }
        }
    }

    fun onConfirmDeviceAdmin(context: android.content.Context) {
        _uiState.update { it.copy(isDeviceAdminDialogVisible = false) }
        executeToggle(BlockingToggle.UNINSTALL_PROTECTION, true, context)
        DeviceAdminManager.requestEnableDeviceAdmin(context)
    }

    fun checkDeviceAdminStatus(context: android.content.Context) {
        val isAdminActive = DeviceAdminManager.isDeviceAdminActive(context)
        val currentSetting = _uiState.value.settings.uninstallProtection

        if (isAdminActive && !currentSetting && _uiState.value.accountabilityMode != AccountabilityMode.NONE) {
            executeToggle(BlockingToggle.UNINSTALL_PROTECTION, true, context)
        }
    }

    fun requestDisconnectPartner(context: android.content.Context) {
        if (_uiState.value.partner == null) return

        // Always use the current friction method preset to remove partner
        guardAction(ProtectedAction.DisconnectPartner, context)
    }

    fun handleToggleRequested(toggle: BlockingToggle, checked: Boolean, context: android.content.Context) {
        if (toggle == BlockingToggle.UNINSTALL_PROTECTION) {
            if (checked) {
                val currentMode = _uiState.value.accountabilityMode
                // If mode is NONE, automatically upgrade to SELF (Typing Challenge) as default check
                if (currentMode == AccountabilityMode.NONE) {
                    applyAccountabilityMode(AccountabilityMode.SELF, context)
                }

                // If mode is FRIEND, ensure partner is linked
                if (_uiState.value.accountabilityMode == AccountabilityMode.FRIEND) {
                    val partner = _uiState.value.partner
                    val isLinked = _uiState.value.isPartnerLinked
                    if (partner == null || !isLinked) {
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

                // Turn ON the toggle in app and accessibility engine
                executeToggle(toggle, true, context)

                // If device admin is not active yet, show the modal prompting to enable it
                if (!DeviceAdminManager.isDeviceAdminActive(context)) {
                    showDeviceAdminDialog()
                }
                return
            } else {
                // Turning OFF uninstall protection: strictly uses the selected accountability check (Friend, Myself, or 24-Hour Delay)
                guardAction(ProtectedAction.ToggleFeature(toggle), context)
                return
            }
        }

        if (checked) {
            // Turning ON is always friction-free
            executeToggle(toggle, true, context)
            return
        }

        // Turning OFF requires guard checks
        guardAction(ProtectedAction.ToggleFeature(toggle), context)
    }

    fun guardAction(action: ProtectedAction, context: android.content.Context) {
        val currentMode = _uiState.value.accountabilityMode
        when (currentMode) {
            AccountabilityMode.NONE -> {
                executeProtectedAction(action, context)
            }
            AccountabilityMode.SELF -> {
                _uiState.update { it.copy(typingChallengeTarget = action) }
            }
            AccountabilityMode.TIME_DELAY -> {
                if (BlockingPreferences.isTimeDelayElapsed(context, action.identifier)) {
                    cancelTimeDelay(context)
                    executeProtectedAction(action, context)
                } else {
                    _uiState.update { it.copy(timeDelayConfirmTarget = action) }
                }
            }
            AccountabilityMode.FRIEND -> {
                val partner = _uiState.value.partner
                val isLinked = _uiState.value.isPartnerLinked
                if (partner != null && isLinked) {
                    _uiState.update {
                        it.copy(
                            friendApprovalTarget = action,
                            unlockCodeError = null,
                            unlockRequestError = null
                        )
                    }
                } else {
                    // No active partner linked yet -> allow changing mode or action directly without trapping user
                    executeProtectedAction(action, context)
                }
            }
        }
    }

    private fun executeProtectedAction(action: ProtectedAction, context: android.content.Context) {
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
        }
    }

    fun onTypingChallengeSuccess(context: android.content.Context) {
        val action = _uiState.value.typingChallengeTarget
        _uiState.update { it.copy(typingChallengeTarget = null) }
        if (action != null) {
            executeProtectedAction(action, context)
        }
    }

    fun dismissTypingChallenge() {
        _uiState.update { it.copy(typingChallengeTarget = null) }
    }

    fun onStartTimeDelay(context: android.content.Context) {
        val action = _uiState.value.timeDelayConfirmTarget ?: return
        startTimeDelayForTarget(action.identifier, context)
    }

    fun startTimeDelayForTarget(targetId: String, context: android.content.Context) {
        val durationMs = BlockingPreferences.DEFAULT_TIME_DELAY_MS
        val unlockAt = System.currentTimeMillis() + durationMs
        BlockingPreferences.setTimeDelay(context, unlockAt, targetId, durationMs)
        _uiState.update {
            it.copy(
                timeDelayConfirmTarget = null,
                timeDelayUnlockAt = unlockAt,
                timeDelayTarget = targetId
            )
        }
    }

    fun cancelTimeDelay(context: android.content.Context) {
        BlockingPreferences.clearTimeDelay(context)
        _uiState.update {
            it.copy(
                timeDelayUnlockAt = 0L,
                timeDelayTarget = ""
            )
        }
    }

    fun dismissTimeDelayConfirm() {
        _uiState.update { it.copy(timeDelayConfirmTarget = null) }
    }

    fun verifyFriendApprovalCode(enteredCode: String, context: android.content.Context) {
        val action = _uiState.value.friendApprovalTarget ?: return
        if (_uiState.value.isVerifyingUnlockCode) return

        val trimmed = enteredCode.trim()
        if (trimmed.length != 6) {
            _uiState.update { it.copy(unlockCodeError = "Please enter the 6-digit approval code.") }
            return
        }

        _uiState.update { it.copy(isVerifyingUnlockCode = true, unlockCodeError = null) }
        viewModelScope.launch {
            accountabilityRepository.verifyUnlockCode(trimmed).fold(
                onSuccess = {
                    _uiState.update {
                        it.copy(
                            friendApprovalTarget = null,
                            isVerifyingUnlockCode = false,
                            unlockCodeError = null,
                            unlockRequestError = null
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
            accountabilityRepository.verifyUnlockCode(trimmed).fold(
                onSuccess = {
                    _uiState.update {
                        it.copy(
                            isVerifyingUnlockCode = false,
                            unlockCodeError = null,
                            unlockRequestError = null
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
        _uiState.update { it.copy(unlockCodeError = null, unlockRequestError = null, isVerifyingUnlockCode = false) }
    }

    fun dismissFriendApproval() {
        _uiState.update {
            it.copy(
                friendApprovalTarget = null,
                isVerifyingUnlockCode = false,
                unlockCodeError = null,
                unlockRequestError = null
            )
        }
    }

    fun requestPartnerApprovalCode(featureName: String) {
        _uiState.update { it.copy(unlockRequestError = null, unlockCodeError = null) }
        viewModelScope.launch {
            accountabilityRepository.requestUnlockCode(featureName).onFailure { err ->
                android.util.Log.w("BlockingViewModel", "Failed to dispatch unlock request: ${err.message}")
                _uiState.update {
                    it.copy(unlockRequestError = err.message ?: "Could not reach server")
                }
            }
        }
    }

    fun executeToggle(toggle: BlockingToggle, enabled: Boolean, context: android.content.Context) {
        BlockingPreferences.ensureContext(context)
        when (toggle) {
            BlockingToggle.ADULT_CONTENT -> setAdultBlocking(enabled)
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
    }

    private fun syncSettingsToAccessibilityService(settings: UserSettingsDto, markDirty: Boolean = false) {
        BlockingPreferences.saveSettings(
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

    private fun pushSettingsToServer(settings: UserSettingsDto) {
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
            redirectUrl = settings.redirectUrl
        )
        viewModelScope.launch {
            settingsRepository.updateSettings(request).onSuccess {
                BlockingPreferences.markLocalDirty(null, false)
            }.onFailure { err ->
                android.util.Log.w("BlockingViewModel", "Failed to sync local protections to server: ${err.message}")
            }
        }
    }
}
