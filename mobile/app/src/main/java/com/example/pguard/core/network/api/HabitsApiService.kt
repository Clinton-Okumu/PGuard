package com.example.pguard.core.network.api

import com.example.pguard.core.network.model.CreateHabitRequest
import com.example.pguard.core.network.model.CreateHabitResponse
import com.example.pguard.core.network.model.HabitsListResponse
import com.example.pguard.core.network.model.RelapseHistoryResponse
import com.example.pguard.core.network.model.ResetStreakRequest
import com.example.pguard.core.network.model.ResetStreakResponse
import com.example.pguard.core.network.model.StatusResponse
import com.example.pguard.core.network.model.StreakResponse
import com.example.pguard.core.network.model.ToggleHabitResponse
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface HabitsApiService {

    @GET("api/v1/streak")
    suspend fun getStreak(): StreakResponse

    @POST("api/v1/streak/reset")
    suspend fun resetStreak(
        @Body request: ResetStreakRequest
    ): ResetStreakResponse

    @GET("api/v1/streak/history")
    suspend fun getRelapseHistory(
        @Query("limit") limit: Int = 20
    ): RelapseHistoryResponse

    @GET("api/v1/habits")
    suspend fun getHabits(
        @Query("date") date: String? = null
    ): HabitsListResponse

    @POST("api/v1/habits")
    suspend fun createHabit(
        @Body request: CreateHabitRequest
    ): CreateHabitResponse

    @POST("api/v1/habits/{id}/toggle")
    suspend fun toggleHabit(
        @Path("id") id: Long,
        @Query("date") date: String? = null
    ): ToggleHabitResponse

    @DELETE("api/v1/habits/{id}")
    suspend fun deleteHabit(
        @Path("id") id: Long
    ): StatusResponse
}
