package com.example.pguard.data.repository

import com.example.pguard.core.network.api.AccountabilityApiService
import com.example.pguard.core.network.model.AcceptInviteRequest
import com.example.pguard.core.network.model.AccountabilityPartnerDto
import com.example.pguard.core.network.model.InvitePartnerRequest
import com.example.pguard.core.network.model.PartnerStatusResponse
import com.example.pguard.core.network.model.RecordEventRequest
import com.example.pguard.core.network.model.SecurityAlertDto

class AccountabilityRepository(
    private val accountabilityApiService: AccountabilityApiService
) {

    suspend fun fetchPartner(): Result<PartnerStatusResponse> {
        return runCatching {
            accountabilityApiService.getPartner()
        }
    }

    suspend fun invitePartner(email: String, name: String = ""): Result<AccountabilityPartnerDto> {
        return runCatching {
            val response = accountabilityApiService.invitePartner(
                InvitePartnerRequest(partnerEmail = email, partnerName = name)
            )
            response.partner ?: throw IllegalStateException(response.error ?: "Failed to create partner invite")
        }
    }

    suspend fun acceptInvite(code: String): Result<AccountabilityPartnerDto> {
        return runCatching {
            val response = accountabilityApiService.acceptInvite(AcceptInviteRequest(inviteCode = code))
            response.partner ?: throw IllegalStateException(response.error ?: "Failed to accept invite")
        }
    }

    suspend fun disconnectPartner(): Result<Unit> {
        return runCatching {
            accountabilityApiService.disconnectPartner()
            Unit
        }
    }

    suspend fun recordSecurityEvent(eventType: String, message: String): Result<SecurityAlertDto> {
        return runCatching {
            val response = accountabilityApiService.recordEvent(
                RecordEventRequest(eventType = eventType, message = message)
            )
            response.alert ?: throw IllegalStateException(response.error ?: "Failed to record security alert")
        }
    }

    suspend fun fetchSecurityEvents(limit: Int = 20): Result<List<SecurityAlertDto>> {
        return runCatching {
            accountabilityApiService.getEvents(limit).events
        }
    }
}
