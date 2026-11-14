package com.example.pguard.core.network

import com.example.pguard.core.datastore.AuthDataStore
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response

class AuthInterceptor(
    private val authDataStore: AuthDataStore
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()

        // Skip adding auth header for public authentication endpoints
        val path = originalRequest.url.encodedPath
        if (path.contains("/auth/google") || path.contains("/auth/refresh")) {
            return chain.proceed(originalRequest)
        }

        // Retrieve current access token from DataStore
        val accessToken = runBlocking {
            authDataStore.accessTokenFlow.firstOrNull()
        }

        val authenticatedRequest = if (!accessToken.isNullOrBlank()) {
            originalRequest.newBuilder()
                .header("Authorization", "Bearer $accessToken")
                .build()
        } else {
            originalRequest
        }

        return chain.proceed(authenticatedRequest)
    }
}
