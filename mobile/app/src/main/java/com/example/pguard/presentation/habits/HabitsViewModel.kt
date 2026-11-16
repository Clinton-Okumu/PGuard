package com.example.pguard.presentation.habits

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pguard.core.network.model.HabitDto
import com.example.pguard.core.network.model.MilestoneDto
import com.example.pguard.core.network.model.RelapseLogDto
import com.example.pguard.data.repository.HabitsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HabitsUiState(
    val currentStreakDays: Int = 0,
    val longestStreakDays: Int = 0,
    val totalRelapses: Int = 0,
    val streakStart: String? = null,
    val milestones: List<MilestoneDto> = emptyList(),
    val nextMilestone: MilestoneDto? = null,
    val habits: List<HabitDto> = emptyList(),
    val relapseHistory: List<RelapseLogDto> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

class HabitsViewModel(
    private val habitsRepository: HabitsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HabitsUiState())
    val uiState: StateFlow<HabitsUiState> = _uiState.asStateFlow()

    init {
        loadData()
    }

    fun loadData() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }

            val streakRes = habitsRepository.fetchStreak()
            val habitsRes = habitsRepository.fetchHabits()
            val relapseRes = habitsRepository.fetchRelapseHistory(limit = 10)

            _uiState.update { current ->
                val streakData = streakRes.getOrNull()
                current.copy(
                    isLoading = false,
                    currentStreakDays = streakData?.streak?.currentDays ?: current.currentStreakDays,
                    longestStreakDays = streakData?.streak?.longestStreakDays ?: current.longestStreakDays,
                    totalRelapses = streakData?.streak?.totalRelapses ?: current.totalRelapses,
                    streakStart = streakData?.streak?.currentStreakStart ?: current.streakStart,
                    milestones = streakData?.milestones ?: current.milestones,
                    nextMilestone = streakData?.nextMilestone ?: current.nextMilestone,
                    habits = habitsRes.getOrDefault(current.habits),
                    relapseHistory = relapseRes.getOrDefault(current.relapseHistory),
                    errorMessage = streakRes.exceptionOrNull()?.localizedMessage
                )
            }
        }
    }

    fun toggleHabit(habitId: Long) {
        // Optimistic update
        _uiState.update { current ->
            current.copy(
                habits = current.habits.map {
                    if (it.id == habitId) it.copy(isCompleted = !it.isCompleted) else it
                }
            )
        }

        viewModelScope.launch {
            habitsRepository.toggleHabit(habitId).fold(
                onSuccess = { res ->
                    _uiState.update { current ->
                        current.copy(
                            habits = current.habits.map {
                                if (it.id == habitId) it.copy(isCompleted = res.completed) else it
                            }
                        )
                    }
                },
                onFailure = { error ->
                    // Revert
                    _uiState.update { current ->
                        current.copy(
                            habits = current.habits.map {
                                if (it.id == habitId) it.copy(isCompleted = !it.isCompleted) else it
                            },
                            errorMessage = error.localizedMessage ?: "Failed to update habit"
                        )
                    }
                }
            )
        }
    }

    fun createHabit(title: String, icon: String = "book") {
        viewModelScope.launch {
            habitsRepository.createHabit(title, icon).fold(
                onSuccess = { newHabit ->
                    _uiState.update { current ->
                        current.copy(habits = current.habits + newHabit)
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(errorMessage = error.localizedMessage ?: "Failed to create habit")
                    }
                }
            )
        }
    }

    fun resetStreak(trigger: String, notes: String) {
        viewModelScope.launch {
            habitsRepository.resetStreak(trigger, notes).fold(
                onSuccess = {
                    loadData()
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(errorMessage = error.localizedMessage ?: "Failed to reset streak")
                    }
                }
            )
        }
    }

    fun dismissError() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}
