package com.example.pguard.core.network.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class GoogleAuthRequest(
    @SerialName("id_token") val idToken: String,
    @SerialName("device_info") val deviceInfo: String
)

@Serializable
data class RefreshTokenRequest(
    @SerialName("refresh_token") val refreshToken: String,
    @SerialName("device_info") val deviceInfo: String = ""
)

@Serializable
data class LogoutRequest(
    @SerialName("refresh_token") val refreshToken: String
)

@Serializable
data class UserDto(
    @SerialName("id") val id: Long,
    @SerialName("email") val email: String,
    @SerialName("display_name") val displayName: String? = null,
    @SerialName("avatar_url") val avatarUrl: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
    @SerialName("last_login_at") val lastLoginAt: String? = null
)

@Serializable
data class AuthResponse(
    @SerialName("access_token") val accessToken: String,
    @SerialName("refresh_token") val refreshToken: String,
    @SerialName("expires_in") val expiresIn: Long,
    @SerialName("user") val user: UserDto
)

@Serializable
data class MeResponse(
    @SerialName("user") val user: UserDto
)

@Serializable
data class StatusResponse(
    @SerialName("status") val status: String? = null,
    @SerialName("error") val error: String? = null
)
