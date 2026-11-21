package com.example.pguard.data.repository

import com.example.pguard.core.common.runSuspendCatching
import com.example.pguard.core.network.api.SettingsApiService
import com.example.pguard.core.network.model.UpdateSettingsRequest
import com.example.pguard.core.network.model.UserSettingsDto

class SettingsRepository(
    private val settingsApiService: SettingsApiService
) {

    suspend fun fetchSettings(): Result<UserSettingsDto> {
        return runSuspendCatching {
            val response = settingsApiService.getSettings()
            response.settings ?: throw IllegalStateException(response.error ?: "Failed to fetch settings")
        }
    }

    suspend fun updateSettings(request: UpdateSettingsRequest): Result<UserSettingsDto> {
        return runSuspendCatching {
            val response = settingsApiService.updateSettings(request)
            response.settings ?: throw IllegalStateException(response.error ?: "Failed to update settings")
        }
    }
}
