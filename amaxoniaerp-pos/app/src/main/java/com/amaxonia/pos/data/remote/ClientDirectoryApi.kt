package com.amaxonia.pos.data.remote

import com.amaxonia.pos.data.remote.dto.AddressLevelDto
import com.amaxonia.pos.data.remote.dto.ClientDto
import com.amaxonia.pos.data.remote.dto.ClientSucursalDto
import com.amaxonia.pos.data.remote.dto.ClientTypeDto
import com.amaxonia.pos.data.remote.dto.CountryDto
import com.amaxonia.pos.data.remote.dto.CreateClientRequest
import com.amaxonia.pos.data.remote.dto.PagedResponse
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.request.url

/**
 * Endpoints del directorio de clientes y catálogos de dirección/tipo.
 * Extensiones sobre [ApiService]: mismos endpoints y bodies que cuando eran miembros.
 */
suspend fun ApiService.getClients(
    token: String,
    limit: Int,
    offset: Int,
    search: String?,
    includeTotal: Boolean? = null,
): PagedResponse<ClientDto> =
    client
        .get("clients") {
            authHeaders(token)
            url {
                parameters.append("limit", limit.toString())
                parameters.append("offset", offset.toString())
                if (!search.isNullOrBlank()) {
                    parameters.append("search", search)
                }
                if (includeTotal != null) {
                    parameters.append("includeTotal", includeTotal.toString())
                }
            }
        }.body()

suspend fun ApiService.createClient(
    token: String,
    request: CreateClientRequest,
): ClientDto =
    client
        .post("clients") {
            authHeaders(token)
            setBody(request)
        }.body()

suspend fun ApiService.getDefaultClient(token: String): ClientDto =
    client
        .get("clients/default") {
            authHeaders(token)
        }.body()

suspend fun ApiService.getClientSucursales(
    token: String,
    clientId: String,
): List<ClientSucursalDto> =
    client
        .get("clients/$clientId/sucursales") {
            authHeaders(token)
        }.body()

suspend fun ApiService.updateClient(
    token: String,
    id: String,
    request: CreateClientRequest,
): ClientDto =
    client
        .put("clients/$id") {
            authHeaders(token)
            setBody(request)
        }.body()

suspend fun ApiService.getCountries(
    token: String,
    limit: Int,
    offset: Int,
    includeTotal: Boolean? = null,
): List<CountryDto> {
    val response =
        client
            .get("countries") {
                authHeaders(token)
                url {
                    parameters.append("limit", limit.toString())
                    parameters.append("offset", offset.toString())
                    if (includeTotal != null) {
                        parameters.append("includeTotal", includeTotal.toString())
                    }
                }
            }.body<PagedResponse<CountryDto>>()
    return response.data
}

suspend fun ApiService.getAddressLevels(
    token: String,
    level: Int,
    limit: Int,
    offset: Int,
    includeTotal: Boolean? = null,
): List<AddressLevelDto> {
    val response =
        client
            .get("address-levels/$level") {
                authHeaders(token)
                url {
                    parameters.append("limit", limit.toString())
                    parameters.append("offset", offset.toString())
                    if (includeTotal != null) {
                        parameters.append("includeTotal", includeTotal.toString())
                    }
                }
            }.body<PagedResponse<AddressLevelDto>>()
    return response.data
}

suspend fun ApiService.getClientTypes(
    token: String,
    limit: Int,
    offset: Int,
    includeTotal: Boolean? = null,
): List<ClientTypeDto> {
    val response =
        client
            .get("client-types") {
                authHeaders(token)
                url {
                    parameters.append("limit", limit.toString())
                    parameters.append("offset", offset.toString())
                    if (includeTotal != null) {
                        parameters.append("includeTotal", includeTotal.toString())
                    }
                }
            }.body<PagedResponse<ClientTypeDto>>()
    return response.data
}
