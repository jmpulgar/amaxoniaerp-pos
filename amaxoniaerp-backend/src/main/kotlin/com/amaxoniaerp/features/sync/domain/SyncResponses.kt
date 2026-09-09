package com.amaxoniaerp.features.sync.domain

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/** Estadísticas de una entidad del manifest (bajo el alcance del request). */
@Serializable
data class EntityStatDto(
    val type: String,
    val count: Long,
    val hash: Long,
)

@Serializable
data class SyncManifestResponse(
    val hashScheme: String,
    val schemaVersion: Int,
    val retentionDays: Int,
    val latestChangeId: Long,
    val entities: List<EntityStatDto>,
)

@Serializable
data class DeltaChangeDto(
    val changeId: Long,
    val entityType: String,
    val entityId: String,
    val op: String,
    val payload: JsonElement? = null,
)

@Serializable
data class SyncDeltaResponse(
    val changes: List<DeltaChangeDto>,
    val nextCursor: Long,
    val hasMore: Boolean,
)

@Serializable
data class SyncBootstrapResponse(
    val entityType: String,
    val items: List<JsonElement>,
    val nextAfterId: String? = null,
    val hasMore: Boolean = false,
)

@Serializable
data class ScopePreviewResponse(
    val entityType: String,
    val count: Long,
)

@Serializable
data class SimpleCatalogItemDto(
    val id: String,
    val nombre: String? = null,
    val conteo: Long? = null,
)
