package com.example.pguard.data.repository

import com.example.pguard.core.common.runSuspendCatching
import com.example.pguard.core.network.api.BlocklistApiService
import com.example.pguard.core.network.model.BlocklistItemDto
import com.example.pguard.core.network.model.CreateBlocklistRequest
import com.example.pguard.core.network.model.ToggleBlocklistRequest

class BlocklistRepository(
    private val blocklistApiService: BlocklistApiService
) {

    suspend fun fetchBlocklist(type: String? = null): Result<List<BlocklistItemDto>> {
        return runSuspendCatching {
            val response = blocklistApiService.getBlocklist(type)
            response.items
        }
    }

    suspend fun createItem(
        type: String,
        value: String,
        label: String? = null,
        isEnabled: Boolean = true
    ): Result<BlocklistItemDto> {
        return runSuspendCatching {
            val response = blocklistApiService.createItem(
                CreateBlocklistRequest(
                    type = type,
                    value = value,
                    label = label,
                    isEnabled = isEnabled
                )
            )
            response.item ?: throw IllegalStateException(response.error ?: "Failed to create item")
        }
    }

    suspend fun toggleItem(id: Long, isEnabled: Boolean): Result<BlocklistItemDto> {
        return runSuspendCatching {
            val response = blocklistApiService.toggleItem(id, ToggleBlocklistRequest(isEnabled))
            response.item ?: throw IllegalStateException(response.error ?: "Failed to update item")
        }
    }

    suspend fun deleteItem(id: Long): Result<Unit> {
        return runSuspendCatching {
            blocklistApiService.deleteItem(id)
            Unit
        }
    }
}
