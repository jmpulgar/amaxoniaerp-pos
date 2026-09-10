package com.amaxonia.pos.data.remote

import com.amaxonia.pos.data.sync.SyncBootstrapResponseDto
import com.amaxonia.pos.data.sync.SyncCatalogItemDto
import com.amaxonia.pos.data.sync.SyncDeltaResponseDto
import com.amaxonia.pos.data.sync.SyncManifestDto
import com.amaxonia.pos.data.sync.SyncScopePreviewDto
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.url
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.json.Json

/** El servidor podó cambios que el dispositivo aún necesita → resync completo. */
class SyncCursorExpiredApiException(
    val oldestRetainedChangeId: Long,
) : RuntimeException("CURSOR_EXPIRED (oldest=$oldestRetainedChangeId)")

/**
 * Contrato del sync incremental (/api/sync/v1). El alcance offline viaja en
 * `scope` en CADA llamada (ADR-008, servidor sin estado).
 */
data class SyncScopeQuery(
    val deptIds: List<Int>? = null,
    val branchIds: List<Int>? = null,
)

interface SyncApiClient {
    suspend fun manifest(
        token: String,
        scope: SyncScopeQuery,
    ): SyncManifestDto

    suspend fun bootstrap(
        token: String,
        entityType: String,
        afterId: String?,
        limit: Int,
        scope: SyncScopeQuery,
    ): SyncBootstrapResponseDto

    suspend fun changes(
        token: String,
        cursor: Long,
        limit: Int,
        scope: SyncScopeQuery,
    ): SyncDeltaResponseDto

    suspend fun scopePreview(
        token: String,
        entity: String,
        scope: SyncScopeQuery,
    ): SyncScopePreviewDto

    suspend fun sucursales(token: String): List<SyncCatalogItemDto>
}

private val syncErrorJson = Json { ignoreUnknownKeys = true }

private const val HTTP_GONE = 410

/** Implementación real sobre el [ApiService] compartido (client + auth). */
class SyncApi(
    private val apiService: ApiService,
) : SyncApiClient {
    override suspend fun manifest(
        token: String,
        scope: SyncScopeQuery,
    ): SyncManifestDto =
        apiService.client
            .get("api/sync/v1/manifest") {
                authHeaders(token)
                url {
                    appendCsv("deptIds", scope.deptIds)
                    appendCsv("branchIds", scope.branchIds)
                }
            }.scopedBody()

    override suspend fun bootstrap(
        token: String,
        entityType: String,
        afterId: String?,
        limit: Int,
        scope: SyncScopeQuery,
    ): SyncBootstrapResponseDto =
        apiService.client
            .get("api/sync/v1/bootstrap/$entityType") {
                authHeaders(token)
                url {
                    parameters.append("limit", limit.toString())
                    if (afterId != null) parameters.append("afterId", afterId)
                    appendCsv("deptIds", scope.deptIds)
                    appendCsv("branchIds", scope.branchIds)
                }
            }.scopedBody()

    override suspend fun changes(
        token: String,
        cursor: Long,
        limit: Int,
        scope: SyncScopeQuery,
    ): SyncDeltaResponseDto {
        val response =
            apiService.client
                .get("api/sync/v1/changes") {
                    authHeaders(token)
                    url {
                        parameters.append("cursor", cursor.toString())
                        parameters.append("limit", limit.toString())
                        appendCsv("deptIds", scope.deptIds)
                        appendCsv("branchIds", scope.branchIds)
                    }
                }
        if (response.status.value == HTTP_GONE) {
            val body = syncErrorJson.parseToJsonElement(response.bodyAsText()).toString()
            val decoded = syncErrorJson.decodeFromString(CursorExpiredErrorDto.serializer(), body)
            throw SyncCursorExpiredApiException(decoded.error.oldestRetainedChangeId)
        }
        return response.body()
    }

    override suspend fun scopePreview(
        token: String,
        entity: String,
        scope: SyncScopeQuery,
    ): SyncScopePreviewDto =
        apiService.client
            .get("api/sync/v1/scope-preview") {
                authHeaders(token)
                url {
                    parameters.append("entity", entity)
                    appendCsv("deptIds", scope.deptIds)
                    appendCsv("branchIds", scope.branchIds)
                }
            }.scopedBody()

    override suspend fun sucursales(token: String): List<SyncCatalogItemDto> =
        apiService.client
            .get("api/sync/v1/sucursales") {
                authHeaders(token)
            }.body()
}

private fun io.ktor.http.URLBuilder.appendCsv(
    name: String,
    values: List<Int>?,
) {
    if (!values.isNullOrEmpty()) {
        parameters.append(name, values.joinToString(","))
    }
}

/** `body<T>()` con ContentNegotiation del cliente compartido. */
private suspend inline fun <reified T> io.ktor.client.statement.HttpResponse.scopedBody(): T = body()

@kotlinx.serialization.Serializable
private data class CursorExpiredErrorDto(
    val error: CursorExpiredBody,
)

@kotlinx.serialization.Serializable
private data class CursorExpiredBody(
    val code: String = "",
    val oldestRetainedChangeId: Long = 0,
)
