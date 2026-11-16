package com.example.pguard.presentation.blocklist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.pguard.core.network.model.BlocklistItemDto
import com.example.pguard.data.repository.BlocklistRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class BlocklistUiState(
    val items: List<BlocklistItemDto> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

class BlocklistViewModel(
    private val blocklistRepository: BlocklistRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(BlocklistUiState())
    val uiState: StateFlow<BlocklistUiState> = _uiState.asStateFlow()

    init {
        loadBlocklist()
    }

    fun loadBlocklist() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            blocklistRepository.fetchBlocklist().fold(
                onSuccess = { items ->
                    _uiState.update { it.copy(items = items, isLoading = false) }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = error.localizedMessage ?: "Failed to load blocklist"
                        )
                    }
                }
            )
        }
    }

    fun addItem(type: String, value: String, label: String? = null) {
        viewModelScope.launch {
            blocklistRepository.createItem(type = type, value = value, label = label).fold(
                onSuccess = { created ->
                    _uiState.update { current ->
                        val updated = current.items.filterNot { it.type == created.type && it.value == created.value } + created
                        current.copy(items = updated)
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(errorMessage = error.localizedMessage ?: "Failed to add item")
                    }
                }
            )
        }
    }

    fun toggleItem(id: Long, isEnabled: Boolean) {
        // Optimistic update
        _uiState.update { current ->
            current.copy(
                items = current.items.map {
                    if (it.id == id) it.copy(isEnabled = isEnabled) else it
                }
            )
        }

        viewModelScope.launch {
            blocklistRepository.toggleItem(id, isEnabled).onFailure { error ->
                // Revert on failure
                _uiState.update { current ->
                    current.copy(
                        items = current.items.map {
                            if (it.id == id) it.copy(isEnabled = !isEnabled) else it
                        },
                        errorMessage = error.localizedMessage ?: "Failed to update item"
                    )
                }
            }
        }
    }

    fun deleteItem(id: Long) {
        val previousItems = _uiState.value.items
        _uiState.update { current ->
            current.copy(items = current.items.filterNot { it.id == id })
        }

        viewModelScope.launch {
            blocklistRepository.deleteItem(id).onFailure { error ->
                _uiState.update {
                    it.copy(
                        items = previousItems,
                        errorMessage = error.localizedMessage ?: "Failed to delete item"
                    )
                }
            }
        }
    }

    fun dismissError() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}
