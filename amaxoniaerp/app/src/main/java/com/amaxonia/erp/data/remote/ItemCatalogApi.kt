package com.amaxonia.erp.data.remote

import com.amaxonia.erp.data.remote.dto.CreateProductRequest
import com.amaxonia.erp.data.remote.dto.DepartmentDto
import com.amaxonia.erp.data.remote.dto.DepartmentsResponse
import com.amaxonia.erp.data.remote.dto.PagedResponse
import com.amaxonia.erp.data.remote.dto.ProductDto
import com.amaxonia.erp.data.remote.dto.ProductOptionsResponseDto
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject

suspend fun ApiService.getProducts(
    token: String,
    limit: Int = 50,
    offset: Int = 0,
    search: String? = null,
    departmentId: Int? = null,
    departmentIds: List<Int>? = null,
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
            } else if (!departmentIds.isNullOrEmpty()) {
                parameters.append("departmentIds", departmentIds.joinToString(","))
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
    onlyVisiblePos: Boolean = true,
): List<DepartmentDto> =
    runCatching {
        // Endpoint principal coincidente con amaxoniaerp-pos: items/departments
        val response = client.get("items/departments") {
            header("Authorization", "Bearer $token")
            header("Accept-Charset", "utf-8")
            if (onlyVisiblePos) {
                url {
                    parameters.append("visible_pos", "true")
                }
            }
        }.body<DepartmentsResponse>()
        response.data
    }.recoverCatching {
        // Fallback: intentar parsear respuesta flexible desde items/departments
        val responseText = client.get("items/departments") {
            header("Authorization", "Bearer $token")
            header("Accept-Charset", "utf-8")
            if (onlyVisiblePos) {
                url {
                    parameters.append("visible_pos", "true")
                }
            }
        }.bodyAsText()
        parseDepartmentsFlexible(responseText)
    }.recoverCatching {
        // Fallback secundario: sync/departamentos
        val responseText = client.get("sync/departamentos") {
            header("Authorization", "Bearer $token")
            header("Accept-Charset", "utf-8")
            if (onlyVisiblePos) {
                url {
                    parameters.append("visible_pos", "true")
                }
            }
        }.bodyAsText()
        parseDepartmentsFlexible(responseText)
    }.getOrElse { emptyList() }

private fun parseDepartmentsFlexible(responseText: String): List<DepartmentDto> {
    val element = AppJson.decodeFromString(JsonElement.serializer(), responseText)
    val array = when (element) {
        is JsonArray -> element
        is JsonObject -> element.jsonObject["data"]?.jsonArray
            ?: element.jsonObject["departments"]?.jsonArray
            ?: JsonArray(emptyList())
        else -> JsonArray(emptyList())
    }
    return AppJson.decodeFromJsonElement(ListSerializer(DepartmentDto.serializer()), array)
}

suspend fun ApiService.getPromotions(
    token: String,
): List<com.amaxonia.erp.data.remote.dto.PromocionDto> =
    runCatching {
        val responseText = client.get("promociones") {
            header("Authorization", "Bearer $token")
        }.body<String>()
        val element = AppJson.decodeFromString(kotlinx.serialization.json.JsonElement.serializer(), responseText)
        val array = when (element) {
            is kotlinx.serialization.json.JsonArray -> element
            is kotlinx.serialization.json.JsonObject -> element.jsonObject["data"]?.jsonArray ?: kotlinx.serialization.json.JsonArray(emptyList())
            else -> kotlinx.serialization.json.JsonArray(emptyList())
        }
        AppJson.decodeFromJsonElement(kotlinx.serialization.builtins.ListSerializer(com.amaxonia.erp.data.remote.dto.PromocionDto.serializer()), array)
    }.getOrElse { emptyList() }

suspend fun ApiService.getProductOptions(
    token: String,
    companyDb: String? = null,
    productId: String,
    almacenId: Int? = null,
): ProductOptionsResponseDto =
    runCatching {
        val responseText = client.get("items/$productId/options") {
            header("Authorization", "Bearer $token")
            if (!companyDb.isNullOrBlank()) {
                header("Company-DB", companyDb)
            }
            if (almacenId != null && almacenId > 0) {
                url.parameters.append("almacen", almacenId.toString())
            }
        }.body<String>()
        AppJson.decodeFromString(ProductOptionsResponseDto.serializer(), responseText)
    }.recoverCatching {
        val responseText = client.get("composicion/opciones") {
            header("Authorization", "Bearer $token")
            if (!companyDb.isNullOrBlank()) {
                header("Company-DB", companyDb)
            }
            url.parameters.append("id_item", productId)
            if (almacenId != null && almacenId > 0) {
                url.parameters.append("almacen", almacenId.toString())
            }
        }.body<String>()
        AppJson.decodeFromString(ProductOptionsResponseDto.serializer(), responseText)
    }.recoverCatching {
        val responseText = client.get("") {
            header("Authorization", "Bearer $token")
            if (!companyDb.isNullOrBlank()) {
                header("Company-DB", companyDb)
            }
            url.parameters.append("action", "composicion/opciones")
            url.parameters.append("id_item", productId)
            if (almacenId != null && almacenId > 0) {
                url.parameters.append("almacen", almacenId.toString())
            }
        }.body<String>()
        AppJson.decodeFromString(ProductOptionsResponseDto.serializer(), responseText)
    }.recoverCatching {
        val responseText = client.get("") {
            header("Authorization", "Bearer $token")
            if (!companyDb.isNullOrBlank()) {
                header("Company-DB", companyDb)
            }
            url.parameters.append("action", "combos/getByid_item")
            url.parameters.append("id_item", productId)
        }.body<String>()
        AppJson.decodeFromString(ProductOptionsResponseDto.serializer(), responseText)
    }.getOrElse { error ->
        ProductOptionsResponseDto(success = false, message = error.message)
    }


