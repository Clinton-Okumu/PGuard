package com.example.pguard.core.network.api

import com.example.pguard.core.network.model.SettingsResponse
import com.example.pguard.core.network.model.UpdateSettingsRequest
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PUT

interface SettingsApiService {

    @GET("api/v1/settings")
    suspend fun getSettings(): SettingsResponse

    @PUT("api/v1/settings")
    suspend fun updateSettings(
        @Body request: UpdateSettingsRequest
    ): SettingsResponse
}
