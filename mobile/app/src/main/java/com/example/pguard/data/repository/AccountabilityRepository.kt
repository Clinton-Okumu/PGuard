package com.example.pguard.data.repository

import com.example.pguard.core.common.runSuspendCatching
import com.example.pguard.core.network.api.AccountabilityApiService
import com.example.pguard.core.network.model.AcceptInviteRequest
import com.example.pguard.core.network.model.AccountabilityPartnerDto
import com.example.pguard.core.network.model.InvitePartnerRequest
import com.example.pguard.core.network.model.PartnerStatusResponse
import com.example.pguard.core.network.model.RecordEventRequest
import com.example.pguard.core.network.model.RequestUnlockCodeRequest
import com.example.pguard.core.network.model.RequestUnlockCodeResponse
import com.example.pguard.core.network.model.SecurityAlertDto
import com.example.pguard.core.network.model.VerifyUnlockCodeRequest
import com.example.pguard.core.network.model.VerifyUnlockCodeResponse
import kotlinx.coroutines.CancellationException

class AccountabilityRepository(
    private val accountabilityApiService: AccountabilityApiService
) {

    private fun extractErrorMessage(e: Throwable): String {
        if (e is retrofit2.HttpException) {
            val errorBody = e.response()?.errorBody()?.string()
            if (!errorBody.isNullOrBlank()) {
                try {
                    val json = org.json.JSONObject(errorBody)
                    if (json.has("error")) {
                        return json.getString("error")
                    }
                } catch (_: Exception) {}
            }
        }
        return e.message ?: "An unexpected error occurred"
    }

    suspend fun fetchPartner(): Result<PartnerStatusResponse> {
        return runSuspendCatching {
            accountabilityApiService.getPartner()
        }
    }

    suspend fun invitePartner(email: String, name: String = ""): Result<AccountabilityPartnerDto> {
        return try {
            val response = accountabilityApiService.invitePartner(
                InvitePartnerRequest(partnerEmail = email, partnerName = name)
            )
            val partner = response.partner ?: throw IllegalStateException(response.error ?: "Failed to create partner invite")
            Result.success(partner)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(Exception(extractErrorMessage(e), e))
        }
    }

    suspend fun acceptInvite(code: String): Result<AccountabilityPartnerDto> {
        return try {
            val response = accountabilityApiService.acceptInvite(AcceptInviteRequest(inviteCode = code))
            val partner = response.partner ?: throw IllegalStateException(response.error ?: "Failed to accept invite")
            Result.success(partner)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(Exception(extractErrorMessage(e), e))
        }
    }

    suspend fun disconnectPartner(): Result<Unit> {
        return runSuspendCatching {
            accountabilityApiService.disconnectPartner()
            Unit
        }
    }

    suspend fun recordSecurityEvent(eventType: String, message: String): Result<SecurityAlertDto> {
        return runSuspendCatching {
            val response = accountabilityApiService.recordEvent(
                RecordEventRequest(eventType = eventType, message = message)
            )
            response.alert ?: throw IllegalStateException(response.error ?: "Failed to record security alert")
        }
    }

    suspend fun fetchSecurityEvents(limit: Int = 20): Result<List<SecurityAlertDto>> {
        return runSuspendCatching {
            accountabilityApiService.getEvents(limit).events
        }
    }

    suspend fun requestUnlockCode(featureName: String): Result<RequestUnlockCodeResponse> {
        return try {
            val response = accountabilityApiService.requestUnlockCode(
                RequestUnlockCodeRequest(featureName = featureName)
            )
            if (response.error != null) {
                throw IllegalStateException(response.error)
            }
            Result.success(response)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(Exception(extractErrorMessage(e), e))
        }
    }

    suspend fun verifyUnlockCode(code: String): Result<VerifyUnlockCodeResponse> {
        return try {
            val response = accountabilityApiService.verifyUnlockCode(
                VerifyUnlockCodeRequest(code = code.trim())
            )
            if (!response.verified) {
                throw IllegalStateException(response.error ?: "Invalid or expired approval code")
            }
            Result.success(response)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(Exception(extractErrorMessage(e), e))
        }
    }
}
