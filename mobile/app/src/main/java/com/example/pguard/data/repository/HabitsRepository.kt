package com.example.pguard.data.repository

import com.example.pguard.core.common.runSuspendCatching
import com.example.pguard.core.network.api.HabitsApiService
import com.example.pguard.core.network.model.CreateHabitRequest
import com.example.pguard.core.network.model.HabitDto
import com.example.pguard.core.network.model.RelapseLogDto
import com.example.pguard.core.network.model.ResetStreakRequest
import com.example.pguard.core.network.model.StreakResponse
import com.example.pguard.core.network.model.ToggleHabitResponse

class HabitsRepository(
    private val habitsApiService: HabitsApiService
) {

    suspend fun fetchStreak(): Result<StreakResponse> {
        return runSuspendCatching {
            habitsApiService.getStreak()
        }
    }

    suspend fun resetStreak(trigger: String = "", notes: String = ""): Result<Unit> {
        return runSuspendCatching {
            habitsApiService.resetStreak(ResetStreakRequest(trigger = trigger, notes = notes))
            Unit
        }
    }

    suspend fun fetchRelapseHistory(limit: Int = 20): Result<List<RelapseLogDto>> {
        return runSuspendCatching {
            habitsApiService.getRelapseHistory(limit).relapses
        }
    }

    suspend fun fetchHabits(date: String? = null): Result<List<HabitDto>> {
        return runSuspendCatching {
            habitsApiService.getHabits(date).habits
        }
    }

    suspend fun createHabit(title: String, icon: String): Result<HabitDto> {
        return runSuspendCatching {
            val res = habitsApiService.createHabit(CreateHabitRequest(title, icon))
            res.habit ?: throw IllegalStateException(res.error ?: "Failed to create habit")
        }
    }

    suspend fun toggleHabit(habitId: Long, date: String? = null): Result<ToggleHabitResponse> {
        return runSuspendCatching {
            habitsApiService.toggleHabit(habitId, date)
        }
    }

    suspend fun deleteHabit(habitId: Long): Result<Unit> {
        return runSuspendCatching {
            habitsApiService.deleteHabit(habitId)
            Unit
        }
    }
}
