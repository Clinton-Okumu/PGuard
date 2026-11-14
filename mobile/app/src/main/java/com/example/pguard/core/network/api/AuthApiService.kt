package com.example.pguard.core.network.api

import com.example.pguard.core.network.model.AuthResponse
import com.example.pguard.core.network.model.GoogleAuthRequest
import com.example.pguard.core.network.model.LogoutRequest
import com.example.pguard.core.network.model.MeResponse
import com.example.pguard.core.network.model.RefreshTokenRequest
import com.example.pguard.core.network.model.StatusResponse
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST

interface AuthApiService {

    @POST("api/v1/auth/google")
    suspend fun authenticateWithGoogle(
        @Body request: GoogleAuthRequest
    ): AuthResponse

    @POST("api/v1/auth/refresh")
    suspend fun refreshToken(
        @Body request: RefreshTokenRequest
    ): AuthResponse

    @POST("api/v1/auth/logout")
    suspend fun logout(
        @Body request: LogoutRequest
    ): StatusResponse

    @GET("api/v1/me")
    suspend fun getMe(
        @Header("Authorization") bearerToken: String
    ): MeResponse
}
