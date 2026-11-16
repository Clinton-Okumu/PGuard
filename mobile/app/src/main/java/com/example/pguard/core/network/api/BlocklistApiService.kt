package com.example.pguard.core.network.api

import com.example.pguard.core.network.model.BlocklistResponse
import com.example.pguard.core.network.model.CreateBlocklistRequest
import com.example.pguard.core.network.model.SingleBlocklistItemResponse
import com.example.pguard.core.network.model.StatusResponse
import com.example.pguard.core.network.model.ToggleBlocklistRequest
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface BlocklistApiService {

    @GET("api/v1/blocklist")
    suspend fun getBlocklist(
        @Query("type") type: String? = null
    ): BlocklistResponse

    @POST("api/v1/blocklist")
    suspend fun createItem(
        @Body request: CreateBlocklistRequest
    ): SingleBlocklistItemResponse

    @PATCH("api/v1/blocklist/{id}")
    suspend fun toggleItem(
        @Path("id") id: Long,
        @Body request: ToggleBlocklistRequest
    ): SingleBlocklistItemResponse

    @DELETE("api/v1/blocklist/{id}")
    suspend fun deleteItem(
        @Path("id") id: Long
    ): StatusResponse
}
