package com.amaxonia.pos.data.remote

import com.amaxonia.pos.data.local.AppJson
import com.amaxonia.pos.data.remote.dto.BestSellerDto
import com.amaxonia.pos.data.remote.dto.BestSellersResponse
import com.amaxonia.pos.data.remote.dto.CreateProductRequest
import com.amaxonia.pos.data.remote.dto.DepartmentDto
import com.amaxonia.pos.data.remote.dto.DepartmentsResponse
import com.amaxonia.pos.data.remote.dto.ItemLotsResponseDto
import com.amaxonia.pos.data.remote.dto.PagedResponse
import com.amaxonia.pos.data.remote.dto.ProductDto
import com.amaxonia.pos.data.remote.dto.ProductStockResponseDto
import com.amaxonia.pos.data.remote.dto.PromocionDto
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject

/**
 * Endpoints de catálogo de ítems (productos, jerarquías, promociones, stock y lotes).
 * Extensiones sobre [ApiService]: mismos endpoints y bodies que cuando eran miembros.
 */
suspend fun ApiService.getProducts(
    token: String,
    page: CatalogPage,
    departmentId: Int? = null,
): PagedResponse<ProductDto> =
    client
        .get("items") {
            authHeaders(token)
            url {
                parameters.append("limit", page.limit.toString())
                parameters.append("offset", page.offset.toString())
                if (!page.search.isNullOrBlank()) {
                    parameters.append("search", page.search)
                }
                if (page.includeTotal != null) {
                    parameters.append("includeTotal", page.includeTotal.toString())
                }
                if (departmentId != null && departmentId > 0) {
                    parameters.append("departmentId", departmentId.toString())
                }
            }
        }.body()

suspend fun ApiService.getDepartments(token: String): List<DepartmentDto> {
    val response =
        client
            .get("items/departments") {
                authHeaders(token)
            }.body<DepartmentsResponse>()
    return response.data
}

suspend fun ApiService.getSections(
    token: String,
    departmentId: Int,
): List<DepartmentDto> {
    val response =
        client
            .get("items/sections") {
                authHeaders(token)
                url { parameters.append("departmentId", departmentId.toString()) }
            }.body<DepartmentsResponse>()
    return response.data
}

suspend fun ApiService.getFamilies(
    token: String,
    sectionId: Int,
): List<DepartmentDto> {
    val response =
        client
            .get("items/families") {
                authHeaders(token)
                url { parameters.append("sectionId", sectionId.toString()) }
            }.body<DepartmentsResponse>()
    return response.data
}

suspend fun ApiService.getSubFamilies(
    token: String,
    familyId: Int,
): List<DepartmentDto> {
    val response =
        client
            .get("items/subfamilies") {
                authHeaders(token)
                url { parameters.append("familyId", familyId.toString()) }
            }.body<DepartmentsResponse>()
    return response.data
}

suspend fun ApiService.getBrands(token: String): List<DepartmentDto> {
    val response =
        client
            .get("items/brands") {
                authHeaders(token)
            }.body<DepartmentsResponse>()
    return response.data
}

suspend fun ApiService.getLines(
    token: String,
    brandId: Int,
): List<DepartmentDto> {
    val response =
        client
            .get("items/lines") {
                authHeaders(token)
                url { parameters.append("brandId", brandId.toString()) }
            }.body<DepartmentsResponse>()
    return response.data
}

suspend fun ApiService.getBestSellers(
    token: String,
    limit: Int = 20,
): List<BestSellerDto> {
    val response =
        client
            .get("items/best-sellers") {
                authHeaders(token)
                url { parameters.append("limit", limit.toString()) }
            }.body<BestSellersResponse>()
    return response.data
}

suspend fun ApiService.getPromotions(token: String): List<PromocionDto> {
    val responseText =
        client
            .get("promociones") {
                authHeaders(token)
            }.bodyAsText()
    val element = AppJson.decodeFromString(JsonElement.serializer(), responseText)
    val array =
        when (element) {
            is JsonArray -> element
            is JsonObject -> element.jsonObject["data"]?.jsonArray ?: JsonArray(emptyList())
            else -> JsonArray(emptyList())
        }
    return AppJson.decodeFromJsonElement(ListSerializer(PromocionDto.serializer()), array)
}

suspend fun ApiService.createProduct(
    token: String,
    request: CreateProductRequest,
): ProductDto =
    client
        .post("items") {
            authHeaders(token)
            setBody(request)
        }.body()

suspend fun ApiService.updateProduct(
    token: String,
    id: Int,
    request: CreateProductRequest,
): ProductDto =
    client
        .put("items/$id") {
            authHeaders(token)
            setBody(request)
        }.body()

suspend fun ApiService.getProductById(
    token: String,
    id: String,
): ProductDto =
    client
        .get("items/$id") {
            authHeaders(token)
        }.body()

suspend fun ApiService.getItemStock(
    token: String,
    id: String,
): ProductStockResponseDto =
    client
        .get("items/$id/stock") {
            authHeaders(token)
        }.body()

suspend fun ApiService.getItemLots(
    token: String,
    id: String,
): ItemLotsResponseDto =
    client
        .get("items/$id/lots") {
            authHeaders(token)
        }.body()
