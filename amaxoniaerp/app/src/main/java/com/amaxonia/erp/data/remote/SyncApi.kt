package com.amaxonia.erp.data.remote

import com.amaxonia.erp.data.sync.SyncBootstrapResponseDto
import com.amaxonia.erp.data.sync.SyncCatalogItemDto
import com.amaxonia.erp.data.sync.SyncDeltaResponseDto
import com.amaxonia.erp.data.sync.SyncManifestDto
import com.amaxonia.erp.data.sync.SyncScopePreviewDto
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.url
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

class SyncCursorExpiredApiException(
    val oldestRetainedChangeId: Long,
) : RuntimeException("CURSOR_EXPIRED (oldest=$oldestRetainedChangeId)")

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

class SyncApi(
    private val apiService: ApiService,
) : SyncApiClient {
    override suspend fun manifest(
        token: String,
        scope: SyncScopeQuery,
    ): SyncManifestDto =
        apiService.client
            .get("api/sync/v1/manifest") {
                header("Authorization", "Bearer $token")
                header("Accept-Charset", "utf-8")
                url {
                    appendCsv("deptIds", scope.deptIds)
                    appendCsv("branchIds", scope.branchIds)
                }
            }.body()

    override suspend fun bootstrap(
        token: String,
        entityType: String,
        afterId: String?,
        limit: Int,
        scope: SyncScopeQuery,
    ): SyncBootstrapResponseDto =
        apiService.client
            .get("api/sync/v1/bootstrap/$entityType") {
                header("Authorization", "Bearer $token")
                header("Accept-Charset", "utf-8")
                url {
                    parameters.append("limit", limit.toString())
                    if (afterId != null) parameters.append("afterId", afterId)
                    appendCsv("deptIds", scope.deptIds)
                    appendCsv("branchIds", scope.branchIds)
                }
            }.body()

    override suspend fun changes(
        token: String,
        cursor: Long,
        limit: Int,
        scope: SyncScopeQuery,
    ): SyncDeltaResponseDto {
        val response =
            apiService.client
                .get("api/sync/v1/changes") {
                    header("Authorization", "Bearer $token")
                    header("Accept-Charset", "utf-8")
                    url {
                        parameters.append("cursor", cursor.toString())
                        parameters.append("limit", limit.toString())
                        appendCsv("deptIds", scope.deptIds)
                        appendCsv("branchIds", scope.branchIds)
                    }
                }
        if (response.status.value == HTTP_GONE) {
            val body = response.bodyAsText()
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
                header("Authorization", "Bearer $token")
                header("Accept-Charset", "utf-8")
                url {
                    parameters.append("entity", entity)
                    appendCsv("deptIds", scope.deptIds)
                    appendCsv("branchIds", scope.branchIds)
                }
            }.body()

    override suspend fun sucursales(token: String): List<SyncCatalogItemDto> =
        apiService.client
            .get("api/sync/v1/sucursales") {
                header("Authorization", "Bearer $token")
                header("Accept-Charset", "utf-8")
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

@Serializable
private data class CursorExpiredErrorDto(
    val error: CursorExpiredBody = CursorExpiredBody(),
)

@Serializable
private data class CursorExpiredBody(
    val code: String = "",
    val oldestRetainedChangeId: Long = 0,
)
