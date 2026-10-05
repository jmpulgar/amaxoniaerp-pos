package com.amaxonia.erp.data.remote

import com.amaxonia.erp.data.remote.dto.CreateProductRequest
import com.amaxonia.erp.data.remote.dto.DepartmentDto
import com.amaxonia.erp.data.remote.dto.PagedResponse
import com.amaxonia.erp.data.remote.dto.ProductDto
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody

suspend fun ApiService.getProducts(
    token: String,
    limit: Int = 50,
    offset: Int = 0,
    search: String? = null,
    departmentId: Int? = null,
): PagedResponse<ProductDto> =
    client.get("items") {
        header("Authorization", "Bearer $token")
        url {
            parameters.append("limit", limit.toString())
            parameters.append("offset", offset.toString())
            if (!search.isNullOrBlank()) {
                parameters.append("search", search)
            }
            if (departmentId != null && departmentId > 0) {
                parameters.append("departmentId", departmentId.toString())
            }
        }
    }.body()

suspend fun ApiService.createProduct(
    token: String,
    request: CreateProductRequest,
): ProductDto =
    client.post("items") {
        header("Authorization", "Bearer $token")
        setBody(request)
    }.body()

suspend fun ApiService.updateProduct(
    token: String,
    id: String,
    request: CreateProductRequest,
): ProductDto =
    client.put("items/$id") {
        header("Authorization", "Bearer $token")
        setBody(request)
    }.body()

suspend fun ApiService.getDepartments(
    token: String,
): List<DepartmentDto> =
    runCatching {
        client.get("sync/departamentos") {
            header("Authorization", "Bearer $token")
        }.body<List<DepartmentDto>>()
    }.getOrElse { emptyList() }
