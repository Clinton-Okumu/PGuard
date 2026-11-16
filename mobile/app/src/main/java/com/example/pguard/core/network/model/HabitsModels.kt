package com.example.pguard.core.network.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class StreakDto(
    @SerialName("current_days") val currentDays: Int = 0,
    @SerialName("longest_streak_days") val longestStreakDays: Int = 0,
    @SerialName("total_relapses") val totalRelapses: Int = 0,
    @SerialName("current_streak_start") val currentStreakStart: String? = null
)

@Serializable
data class MilestoneDto(
    @SerialName("days") val days: Int,
    @SerialName("title") val title: String,
    @SerialName("is_unlocked") val isUnlocked: Boolean = false
)

@Serializable
data class StreakResponse(
    @SerialName("streak") val streak: StreakDto? = null,
    @SerialName("milestones") val milestones: List<MilestoneDto> = emptyList(),
    @SerialName("next_milestone") val nextMilestone: MilestoneDto? = null,
    @SerialName("error") val error: String? = null
)

@Serializable
data class ResetStreakRequest(
    @SerialName("trigger") val trigger: String = "",
    @SerialName("notes") val notes: String = ""
)

@Serializable
data class RelapseLogDto(
    @SerialName("id") val id: Long = 0,
    @SerialName("trigger") val trigger: String = "",
    @SerialName("notes") val notes: String = "",
    @SerialName("streak_days_lost") val streakDaysLost: Int = 0,
    @SerialName("created_at") val createdAt: String? = null
)

@Serializable
data class ResetStreakResponse(
    @SerialName("message") val message: String? = null,
    @SerialName("streak") val streak: StreakDto? = null,
    @SerialName("relapse_log") val relapseLog: RelapseLogDto? = null,
    @SerialName("error") val error: String? = null
)

@Serializable
data class RelapseHistoryResponse(
    @SerialName("relapses") val relapses: List<RelapseLogDto> = emptyList(),
    @SerialName("count") val count: Int = 0,
    @SerialName("error") val error: String? = null
)

@Serializable
data class HabitDto(
    @SerialName("id") val id: Long = 0,
    @SerialName("user_id") val userId: Long = 0,
    @SerialName("title") val title: String = "",
    @SerialName("icon") val icon: String = "book",
    @SerialName("is_completed") val isCompleted: Boolean = false,
    @SerialName("created_at") val createdAt: String? = null
)

@Serializable
data class HabitsListResponse(
    @SerialName("date") val date: String = "",
    @SerialName("habits") val habits: List<HabitDto> = emptyList(),
    @SerialName("count") val count: Int = 0,
    @SerialName("error") val error: String? = null
)

@Serializable
data class CreateHabitRequest(
    @SerialName("title") val title: String,
    @SerialName("icon") val icon: String = "book"
)

@Serializable
data class CreateHabitResponse(
    @SerialName("message") val message: String? = null,
    @SerialName("habit") val habit: HabitDto? = null,
    @SerialName("error") val error: String? = null
)

@Serializable
data class ToggleHabitResponse(
    @SerialName("habit_id") val habitId: Long = 0,
    @SerialName("date") val date: String = "",
    @SerialName("completed") val completed: Boolean = false,
    @SerialName("error") val error: String? = null
)
