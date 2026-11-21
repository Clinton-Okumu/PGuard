package com.example.pguard.core.network

import android.os.Build
import com.example.pguard.core.datastore.AuthDataStore
import com.example.pguard.core.network.model.AuthResponse
import com.example.pguard.core.network.model.RefreshTokenRequest
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.Authenticator
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.Route
import java.util.concurrent.TimeUnit

class TokenAuthenticator(
    private val authDataStore: AuthDataStore,
    private val baseUrlProvider: () -> String,
    private val json: Json
) : Authenticator {

    private sealed interface RefreshResult {
        data class Success(val auth: AuthResponse) : RefreshResult
        data object InvalidToken : RefreshResult
        data object TransientError : RefreshResult
    }

    private val refreshHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    override fun authenticate(route: Route?, response: Response): Request? {
        val path = response.request.url.encodedPath
        // Do not attempt to refresh if the endpoint itself is an auth endpoint
        if (path.contains("/auth/google") || path.contains("/auth/refresh") || path.contains("/auth/logout")) {
            return null
        }

        // Prevent infinite loop if already retried
        if (responseCount(response) >= 3) {
            return null
        }

        synchronized(this) {
            val currentToken = runBlocking { authDataStore.accessTokenFlow.firstOrNull() }
            val requestHeaderToken = response.request.header("Authorization")?.removePrefix("Bearer ")?.trim()

            // If another concurrent request already refreshed the token, retry with that new token
            if (!currentToken.isNullOrBlank() && currentToken != requestHeaderToken) {
                return response.request.newBuilder()
                    .header("Authorization", "Bearer $currentToken")
                    .build()
            }

            val refreshToken = runBlocking { authDataStore.refreshTokenFlow.firstOrNull() }
            if (refreshToken.isNullOrBlank()) {
                return null
            }

            return when (val result = performRefreshToken(refreshToken)) {
                is RefreshResult.Success -> {
                    runBlocking {
                        authDataStore.updateTokens(result.auth.accessToken, result.auth.refreshToken)
                    }
                    response.request.newBuilder()
                        .header("Authorization", "Bearer ${result.auth.accessToken}")
                        .build()
                }
                RefreshResult.InvalidToken -> {
                    // Refresh token definitively rejected (401/403); clear session
                    runBlocking { authDataStore.clearSession() }
                    null
                }
                RefreshResult.TransientError -> {
                    // Offline or transient server error; preserve session
                    null
                }
            }
        }
    }

    private fun performRefreshToken(refreshToken: String): RefreshResult {
        return try {
            val base = baseUrlProvider().let { if (it.endsWith("/")) it else "$it/" }
            val url = "${base}api/v1/auth/refresh"
            val deviceInfo = "${Build.MANUFACTURER} ${Build.MODEL} (Android ${Build.VERSION.RELEASE})"
            val payload = json.encodeToString(
                RefreshTokenRequest.serializer(),
                RefreshTokenRequest(refreshToken = refreshToken, deviceInfo = deviceInfo)
            )
            val body = payload.toRequestBody("application/json".toMediaType())
            val request = Request.Builder()
                .url(url)
                .post(body)
                .build()

            refreshHttpClient.newCall(request).execute().use { resp ->
                if (resp.code == 401 || resp.code == 403) {
                    return RefreshResult.InvalidToken
                }
                if (!resp.isSuccessful) {
                    return RefreshResult.TransientError
                }
                val respBody = resp.body?.string() ?: return RefreshResult.TransientError
                val authResp = json.decodeFromString(AuthResponse.serializer(), respBody)
                RefreshResult.Success(authResp)
            }
        } catch (_: Exception) {
            RefreshResult.TransientError
        }
    }

    private fun responseCount(response: Response): Int {
        var count = 1
        var prior = response.priorResponse
        while (prior != null) {
            count++
            prior = prior.priorResponse
        }
        return count
    }
}
