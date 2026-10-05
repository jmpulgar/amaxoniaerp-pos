package com.amaxonia.erp.data.remote

import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import kotlinx.serialization.Serializable

@Serializable
data class SimpleCatalogItemDto(
    val id: String,
    val nombre: String? = null,
)

@Serializable
data class SucursalDto(
    val id: Int,
    val codigo: String? = null,
    val serie: String? = null,
    val codigoSucursalEmisor: String? = null,
    val sucursal: String? = null,
    val descripcion: String? = null,
    val defaultWarehouseId: Int? = null,
)

@Serializable
data class SaveSucursalRequest(
    val codigo: String? = null,
    val serie: String? = null,
    val codigoSucursalEmisor: String? = null,
    val sucursal: String,
    val descripcion: String? = null,
    val defaultWarehouseId: Int? = null,
)

@Serializable
data class SucursalesListResponse(
    val success: Boolean = true,
    val data: List<SucursalDto>,
)

@Serializable
data class SucursalDetailResponse(
    val success: Boolean = true,
    val data: SucursalDto,
)

suspend fun ApiService.getSucursales(
    token: String,
): List<SimpleCatalogItemDto> =
    client.get("sync/sucursales") {
        header("Authorization", "Bearer $token")
    }.body()

suspend fun ApiService.getSucursalesList(
    token: String,
    companyDb: String,
): List<SucursalDto> {
    val response: SucursalesListResponse =
        client.get("api/sucursales") {
            header("Authorization", "Bearer $token")
            header("Company-DB", companyDb)
        }.body()
    return response.data
}

suspend fun ApiService.createSucursal(
    token: String,
    companyDb: String,
    request: SaveSucursalRequest,
): SucursalDto {
    val response: SucursalDetailResponse =
        client.post("api/sucursales") {
            header("Authorization", "Bearer $token")
            header("Company-DB", companyDb)
            setBody(request)
        }.body()
    return response.data
}

suspend fun ApiService.updateSucursal(
    token: String,
    companyDb: String,
    id: Int,
    request: SaveSucursalRequest,
): SucursalDto {
    val response: SucursalDetailResponse =
        client.put("api/sucursales/$id") {
            header("Authorization", "Bearer $token")
            header("Company-DB", companyDb)
            setBody(request)
        }.body()
    return response.data
}
