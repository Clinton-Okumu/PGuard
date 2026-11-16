package com.example.pguard.core.network.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class BlocklistItemDto(
    @SerialName("id") val id: Long = 0,
    @SerialName("user_id") val userId: Long = 0,
    @SerialName("type") val type: String = "app", // "app", "website", "keyword"
    @SerialName("value") val value: String = "",
    @SerialName("label") val label: String = "",
    @SerialName("is_enabled") val isEnabled: Boolean = true,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null
)

@Serializable
data class BlocklistPayload(
    @SerialName("type") val type: String,
    @SerialName("value") val value: String,
    @SerialName("label") val label: String? = null,
    @SerialName("is_enabled") val isEnabled: Boolean? = true
)

@Serializable
data class CreateBlocklistRequest(
    @SerialName("type") val type: String? = null,
    @SerialName("value") val value: String? = null,
    @SerialName("label") val label: String? = null,
    @SerialName("is_enabled") val isEnabled: Boolean? = true,
    @SerialName("items") val items: List<BlocklistPayload>? = null
)

@Serializable
data class ToggleBlocklistRequest(
    @SerialName("is_enabled") val isEnabled: Boolean
)

@Serializable
data class BlocklistResponse(
    @SerialName("items") val items: List<BlocklistItemDto> = emptyList(),
    @SerialName("count") val count: Int = 0,
    @SerialName("error") val error: String? = null
)

@Serializable
data class SingleBlocklistItemResponse(
    @SerialName("item") val item: BlocklistItemDto? = null,
    @SerialName("message") val message: String? = null,
    @SerialName("error") val error: String? = null
)
