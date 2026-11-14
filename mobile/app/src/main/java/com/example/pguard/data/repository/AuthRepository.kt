package com.example.pguard.data.repository

import android.os.Build
import com.example.pguard.core.datastore.AuthDataStore
import com.example.pguard.core.network.api.AuthApiService
import com.example.pguard.core.network.model.GoogleAuthRequest
import com.example.pguard.core.network.model.LogoutRequest
import com.example.pguard.core.network.model.RefreshTokenRequest
import com.example.pguard.core.network.model.UserDto
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull

class AuthRepository(
    private val authApiService: AuthApiService,
    private val authDataStore: AuthDataStore
) {

    val currentUser: Flow<UserDto?> = authDataStore.userFlow
    val isLoggedIn: Flow<Boolean> = authDataStore.isLoggedInFlow
    val accessToken: Flow<String?> = authDataStore.accessTokenFlow

    suspend fun authenticateWithGoogle(
        idToken: String,
        deviceInfo: String = "${Build.MANUFACTURER} ${Build.MODEL} (Android ${Build.VERSION.RELEASE})"
    ): Result<UserDto> {
        return runCatching {
            val response = authApiService.authenticateWithGoogle(
                GoogleAuthRequest(
                    idToken = idToken,
                    deviceInfo = deviceInfo
                )
            )
            authDataStore.saveSession(
                accessToken = response.accessToken,
                refreshToken = response.refreshToken,
                user = response.user
            )
            response.user
        }
    }

    suspend fun refreshToken(): Result<Unit> {
        return runCatching {
            val currentRefreshToken = authDataStore.refreshTokenFlow.firstOrNull()
                ?: throw IllegalStateException("No refresh token available")

            val response = authApiService.refreshToken(
                RefreshTokenRequest(
                    refreshToken = currentRefreshToken,
                    deviceInfo = "${Build.MANUFACTURER} ${Build.MODEL}"
                )
            )
            authDataStore.updateTokens(
                accessToken = response.accessToken,
                refreshToken = response.refreshToken
            )
        }
    }

    suspend fun logout(): Result<Unit> {
        return runCatching {
            val currentRefreshToken = authDataStore.refreshTokenFlow.firstOrNull()
            if (!currentRefreshToken.isNullOrBlank()) {
                runCatching {
                    authApiService.logout(LogoutRequest(currentRefreshToken))
                }
            }
            authDataStore.clearSession()
        }
    }
}
