package com.example.pguard.presentation.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pguard.core.network.model.AccountabilityPartnerDto
import com.example.pguard.core.network.model.UpdateSettingsRequest
import com.example.pguard.core.network.model.UserSettingsDto
import com.example.pguard.data.repository.AccountabilityRepository
import com.example.pguard.data.repository.SettingsRepository
import com.example.pguard.engine.accessibility.PGuardAccessibilityService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
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
    val inviteEmail: String = ""
)

class BlockingViewModel(
    private val settingsRepository: SettingsRepository,
    private val accountabilityRepository: AccountabilityRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        BlockingUiState(
            settings = UserSettingsDto(
                blockAdultContent = PGuardAccessibilityService.isAdultBlockingEnabled,
                blockShorts = PGuardAccessibilityService.isShortsBlockingEnabled,
                blockWhatsAppChannels = PGuardAccessibilityService.isWhatsAppChannelsBlockingEnabled,
                blockInstagramReels = PGuardAccessibilityService.isInstagramReelsBlockingEnabled,
                blockTikTok = PGuardAccessibilityService.isTikTokBlockingEnabled,
                uninstallProtection = PGuardAccessibilityService.isUninstallProtectionEnabled,
                blockedScreenMessage = PGuardAccessibilityService.blockedScreenMessage,
                blockedScreenCountdown = PGuardAccessibilityService.blockedScreenCountdown
            )
        )
    )
    val uiState: StateFlow<BlockingUiState> = _uiState.asStateFlow()

    init {
        loadData()
    }

    fun refreshFromLocalPreferences() {
        _uiState.update { current ->
            current.copy(
                settings = current.settings.copy(
                    blockAdultContent = PGuardAccessibilityService.isAdultBlockingEnabled,
                    blockShorts = PGuardAccessibilityService.isShortsBlockingEnabled,
                    blockWhatsAppChannels = PGuardAccessibilityService.isWhatsAppChannelsBlockingEnabled,
                    blockInstagramReels = PGuardAccessibilityService.isInstagramReelsBlockingEnabled,
                    blockTikTok = PGuardAccessibilityService.isTikTokBlockingEnabled,
                    uninstallProtection = PGuardAccessibilityService.isUninstallProtectionEnabled,
                    blockedScreenMessage = PGuardAccessibilityService.blockedScreenMessage,
                    blockedScreenCountdown = PGuardAccessibilityService.blockedScreenCountdown
                )
            )
        }
    }

    fun loadData() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }

            val settingsResult = settingsRepository.fetchSettings()
            val partnerResult = accountabilityRepository.fetchPartner()

            _uiState.update { current ->
                val serverSettings = settingsResult.getOrNull()
                val finalSettings = if (serverSettings != null) {
                    syncSettingsToAccessibilityService(serverSettings)
                    serverSettings
                } else {
                    // Keep current local settings if offline/server unreachable
                    current.settings
                }

                current.copy(
                    isLoading = false,
                    settings = finalSettings,
                    partner = partnerResult.getOrNull()?.partner,
                    isPartnerLinked = partnerResult.getOrNull()?.isLinked ?: false,
                    errorMessage = null
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

    fun updateSetting(block: (UserSettingsDto) -> UserSettingsDto) {
        val currentSettings = _uiState.value.settings
        val updatedLocal = block(currentSettings)
        syncSettingsToAccessibilityService(updatedLocal)
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
                    syncSettingsToAccessibilityService(updatedServer)
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
        _uiState.update { it.copy(isInviteDialogVisible = true, inviteEmail = "") }
    }

    fun dismissInviteDialog() {
        _uiState.update { it.copy(isInviteDialogVisible = false) }
    }

    fun updateInviteEmail(email: String) {
        _uiState.update { it.copy(inviteEmail = email) }
    }

    fun submitInvite() {
        val email = _uiState.value.inviteEmail.trim()
        if (email.isEmpty()) return
        dismissInviteDialog()
        invitePartner(email)
    }

    fun disconnectPartner() {
        viewModelScope.launch {
            accountabilityRepository.disconnectPartner().onSuccess {
                _uiState.update { it.copy(partner = null, isPartnerLinked = false) }
            }
        }
    }

    fun invitePartner(email: String, name: String = "") {
        viewModelScope.launch {
            accountabilityRepository.invitePartner(email, name).onSuccess { partner ->
                _uiState.update { it.copy(partner = partner, isPartnerLinked = false) }
            }
        }
    }

    fun dismissError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    private fun syncSettingsToAccessibilityService(settings: UserSettingsDto) {
        PGuardAccessibilityService.isAdultBlockingEnabled = settings.blockAdultContent
        PGuardAccessibilityService.isShortsBlockingEnabled = settings.blockShorts
        PGuardAccessibilityService.isWhatsAppChannelsBlockingEnabled = settings.blockWhatsAppChannels
        PGuardAccessibilityService.isInstagramReelsBlockingEnabled = settings.blockInstagramReels
        PGuardAccessibilityService.isTikTokBlockingEnabled = settings.blockTikTok
        PGuardAccessibilityService.isUninstallProtectionEnabled = settings.uninstallProtection
        PGuardAccessibilityService.blockedScreenMessage = settings.blockedScreenMessage
        PGuardAccessibilityService.blockedScreenCountdown = settings.blockedScreenCountdown
    }
}
