package com.example.pguard.core.network.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class AccountabilityPartnerDto(
    @SerialName("id") val id: Long = 0,
    @SerialName("user_id") val userId: Long = 0,
    @SerialName("partner_email") val partnerEmail: String = "",
    @SerialName("partner_name") val partnerName: String = "",
    @SerialName("invite_code") val inviteCode: String = "",
    @SerialName("status") val status: String = "pending", // "pending", "accepted", "disconnected"
    @SerialName("created_at") val createdAt: String? = null
)

@Serializable
data class PartnerStatusResponse(
    @SerialName("partner") val partner: AccountabilityPartnerDto? = null,
    @SerialName("is_linked") val isLinked: Boolean = false,
    @SerialName("error") val error: String? = null
)

@Serializable
data class InvitePartnerRequest(
    @SerialName("partner_email") val partnerEmail: String,
    @SerialName("partner_name") val partnerName: String = ""
)

@Serializable
data class PartnerInviteResponse(
    @SerialName("message") val message: String? = null,
    @SerialName("partner") val partner: AccountabilityPartnerDto? = null,
    @SerialName("error") val error: String? = null
)

@Serializable
data class AcceptInviteRequest(
    @SerialName("invite_code") val inviteCode: String
)

@Serializable
data class AcceptInviteResponse(
    @SerialName("message") val message: String? = null,
    @SerialName("partner") val partner: AccountabilityPartnerDto? = null,
    @SerialName("error") val error: String? = null
)

@Serializable
data class RecordEventRequest(
    @SerialName("event_type") val eventType: String, // "sos_triggered", "protection_disabled", "permission_revoked", "uninstall_attempt"
    @SerialName("message") val message: String
)

@Serializable
data class SecurityAlertDto(
    @SerialName("id") val id: Long = 0,
    @SerialName("user_id") val userId: Long = 0,
    @SerialName("event_type") val eventType: String = "",
    @SerialName("message") val message: String = "",
    @SerialName("created_at") val createdAt: String? = null
)

@Serializable
data class RecordEventResponse(
    @SerialName("message") val message: String? = null,
    @SerialName("alert") val alert: SecurityAlertDto? = null,
    @SerialName("error") val error: String? = null
)

@Serializable
data class SecurityEventsResponse(
    @SerialName("events") val events: List<SecurityAlertDto> = emptyList(),
    @SerialName("count") val count: Int = 0,
    @SerialName("error") val error: String? = null
)

@Serializable
data class RequestUnlockCodeRequest(
    @SerialName("feature_name") val featureName: String
)

@Serializable
data class RequestUnlockCodeResponse(
    @SerialName("message") val message: String? = null,
    @SerialName("partner_email") val partnerEmail: String? = null,
    @SerialName("feature_name") val featureName: String? = null,
    @SerialName("error") val error: String? = null
)

@Serializable
data class VerifyUnlockCodeRequest(
    @SerialName("code") val code: String,
    @SerialName("feature_name") val featureName: String? = null
)

@Serializable
data class VerifyUnlockCodeResponse(
    @SerialName("verified") val verified: Boolean = false,
    @SerialName("feature_name") val featureName: String? = null,
    @SerialName("error") val error: String? = null
)

@Serializable
data class TimeDelayUnlockDto(
    @SerialName("id") val id: Long = 0,
    @SerialName("user_id") val userId: Long = 0,
    @SerialName("target_key") val targetKey: String = "",
    @SerialName("target_title") val targetTitle: String = "",
    @SerialName("unlock_at") val unlockAt: String = ""
)

@Serializable
data class StartTimeDelayRequest(
    @SerialName("target_key") val targetKey: String,
    @SerialName("target_title") val targetTitle: String,
    @SerialName("duration_seconds") val durationSeconds: Long = 86_400L
)

@Serializable
data class TimeDelayResponse(
    @SerialName("time_delay") val timeDelay: TimeDelayUnlockDto? = null,
    @SerialName("server_now_ms") val serverNowMs: Long = 0L,
    @SerialName("remaining_ms") val remainingMs: Long = 0L,
    @SerialName("is_ready") val isReady: Boolean = false,
    @SerialName("error") val error: String? = null
)
