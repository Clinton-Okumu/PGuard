package com.example.pguard.core.network.api

import com.example.pguard.core.network.model.AcceptInviteRequest
import com.example.pguard.core.network.model.AcceptInviteResponse
import com.example.pguard.core.network.model.InvitePartnerRequest
import com.example.pguard.core.network.model.PartnerInviteResponse
import com.example.pguard.core.network.model.PartnerStatusResponse
import com.example.pguard.core.network.model.RecordEventRequest
import com.example.pguard.core.network.model.RecordEventResponse
import com.example.pguard.core.network.model.SecurityEventsResponse
import com.example.pguard.core.network.model.StatusResponse
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

interface AccountabilityApiService {

    @GET("api/v1/accountability/partner")
    suspend fun getPartner(): PartnerStatusResponse

    @POST("api/v1/accountability/invite")
    suspend fun invitePartner(
        @Body request: InvitePartnerRequest
    ): PartnerInviteResponse

    @POST("api/v1/accountability/accept")
    suspend fun acceptInvite(
        @Body request: AcceptInviteRequest
    ): AcceptInviteResponse

    @DELETE("api/v1/accountability/partner")
    suspend fun disconnectPartner(): StatusResponse

    @POST("api/v1/accountability/event")
    suspend fun recordEvent(
        @Body request: RecordEventRequest
    ): RecordEventResponse

    @GET("api/v1/accountability/events")
    suspend fun getEvents(
        @Query("limit") limit: Int = 20
    ): SecurityEventsResponse
}
