package com.example.pguard.core.network.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class UserSettingsDto(
    @SerialName("id") val id: Long = 0,
    @SerialName("user_id") val userId: Long = 0,
    @SerialName("block_adult_content") val blockAdultContent: Boolean = true,
    @SerialName("block_shorts") val blockShorts: Boolean = false,
    @SerialName("block_whatsapp_channels") val blockWhatsAppChannels: Boolean = false,
    @SerialName("block_instagram_reels") val blockInstagramReels: Boolean = false,
    @SerialName("block_tiktok") val blockTikTok: Boolean = false,
    @SerialName("uninstall_protection") val uninstallProtection: Boolean = false,
    @SerialName("block_unsupported_browsers") val blockUnsupportedBrowsers: Boolean = false,
    @SerialName("block_newly_installed_apps") val blockNewlyInstalledApps: Boolean = false,
    @SerialName("blocked_screen_message") val blockedScreenMessage: String = "This page is blocked.",
    @SerialName("blocked_screen_countdown") val blockedScreenCountdown: Int = 3,
    @SerialName("redirect_url") val redirectUrl: String = "",
    @SerialName("accountability_mode") val accountabilityMode: String = "none",
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null
)

@Serializable
data class UpdateSettingsRequest(
    @SerialName("block_adult_content") val blockAdultContent: Boolean? = null,
    @SerialName("block_shorts") val blockShorts: Boolean? = null,
    @SerialName("block_whatsapp_channels") val blockWhatsAppChannels: Boolean? = null,
    @SerialName("block_instagram_reels") val blockInstagramReels: Boolean? = null,
    @SerialName("block_tiktok") val blockTikTok: Boolean? = null,
    @SerialName("uninstall_protection") val uninstallProtection: Boolean? = null,
    @SerialName("block_unsupported_browsers") val blockUnsupportedBrowsers: Boolean? = null,
    @SerialName("block_newly_installed_apps") val blockNewlyInstalledApps: Boolean? = null,
    @SerialName("blocked_screen_message") val blockedScreenMessage: String? = null,
    @SerialName("blocked_screen_countdown") val blockedScreenCountdown: Int? = null,
    @SerialName("redirect_url") val redirectUrl: String? = null,
    @SerialName("accountability_mode") val accountabilityMode: String? = null
)

@Serializable
data class SettingsResponse(
    @SerialName("settings") val settings: UserSettingsDto? = null,
    @SerialName("message") val message: String? = null,
    @SerialName("error") val error: String? = null
)
