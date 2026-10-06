package com.amaxonia.erp.data.remote

import com.amaxonia.erp.data.remote.dto.ClientDto
import com.amaxonia.erp.data.remote.dto.ClientSucursalDto
import com.amaxonia.erp.data.remote.dto.CreateClientRequest
import com.amaxonia.erp.data.remote.dto.PagedResponse
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody

suspend fun ApiService.getClients(
    token: String,
    limit: Int = 50,
    offset: Int = 0,
    search: String? = null,
): PagedResponse<ClientDto> =
    client.get("clients") {
        header("Authorization", "Bearer $token")
        url {
            parameters.append("limit", limit.toString())
            parameters.append("offset", offset.toString())
            if (!search.isNullOrBlank()) {
                parameters.append("search", search)
            }
        }
    }.body()

suspend fun ApiService.createClient(
    token: String,
    request: CreateClientRequest,
): ClientDto =
    client.post("clients") {
        header("Authorization", "Bearer $token")
        setBody(request)
    }.body()

suspend fun ApiService.updateClient(
    token: String,
    id: String,
    request: CreateClientRequest,
): ClientDto =
    client.put("clients/$id") {
        header("Authorization", "Bearer $token")
        setBody(request)
    }.body()

suspend fun ApiService.getDefaultClient(token: String): ClientDto =
    client.get("clients/default") {
        header("Authorization", "Bearer $token")
    }.body()

suspend fun ApiService.getClientSucursales(
    token: String,
    clientId: String,
): List<ClientSucursalDto> =
    client.get("clients/$clientId/sucursales") {
        header("Authorization", "Bearer $token")
    }.body()

