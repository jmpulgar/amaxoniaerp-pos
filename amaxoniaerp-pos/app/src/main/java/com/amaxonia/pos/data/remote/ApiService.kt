package com.amaxonia.pos.data.remote

import com.amaxonia.pos.data.local.AppJson
import com.amaxonia.pos.data.remote.dto.AddressLevelDto
import com.amaxonia.pos.data.remote.dto.BestSellerDto
import com.amaxonia.pos.data.remote.dto.BestSellersResponse
import com.amaxonia.pos.data.remote.dto.ClientDto
import com.amaxonia.pos.data.remote.dto.ClientSucursalDto
import com.amaxonia.pos.data.remote.dto.ClientTypeDto
import com.amaxonia.pos.data.remote.dto.CountryDto
import com.amaxonia.pos.data.remote.dto.CreateClientRequest
import com.amaxonia.pos.data.remote.dto.CreateProductRequest
import com.amaxonia.pos.data.remote.dto.DepartmentDto
import com.amaxonia.pos.data.remote.dto.DepartmentsResponse
import com.amaxonia.pos.data.remote.dto.ErrorResponse
import com.amaxonia.pos.data.remote.dto.FacturasResumenDto
import com.amaxonia.pos.data.remote.dto.ItemLotsResponseDto
import com.amaxonia.pos.data.remote.dto.LoginRequest
import com.amaxonia.pos.data.remote.dto.LoginResponse
import com.amaxonia.pos.data.remote.dto.PagedResponse
import com.amaxonia.pos.data.remote.dto.ProductDto
import com.amaxonia.pos.data.remote.dto.ProductStockResponseDto
import com.amaxonia.pos.data.remote.dto.PromocionDto
import com.amaxonia.pos.data.remote.dto.SelectCompanyRequest
import com.amaxonia.pos.data.remote.dto.SelectCompanyResponse
import com.amaxonia.pos.domain.error.UnauthorizedException
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject

class ApiService(
    private val apiClient: ApiClient,
) {
    // Acceso al cliente HTTP actual (se recrea automáticamente si cambia la URL)
    private val client: HttpClient
        get() = apiClient.httpClient

    // Helper para headers comunes
    private fun io.ktor.client.request.HttpRequestBuilder.authHeaders(token: String) {
        header(HttpHeaders.Authorization, "Bearer $token")
        // IMPORTANTE: Forzamos UTF-8 para evitar los "?"
        header(HttpHeaders.AcceptCharset, "utf-8")
    }

    suspend fun login(
        request: LoginRequest,
        countryCode: String,
    ): LoginResponse {
        val response =
            client.post("auth/login") {
                // Enviar el código de país al backend para que sepa a qué BD de config conectarse
                header("X-Country-Code", countryCode)
                setBody(request)
            }
        if (!response.status.isSuccess()) {
            if (response.status.value == 401) {
                throw UnauthorizedException("Usuario o contrasena incorrectos")
            }
            val message =
                runCatching {
                    AppJson
                        .decodeFromString(
                            ErrorResponse.serializer(),
                            response.bodyAsText(),
                        ).error
                }.getOrNull()
            error(message ?: "No se pudo iniciar sesion")
        }
        return response.body()
    }

    suspend fun selectCompany(
        token: String,
        request: SelectCompanyRequest,
    ): SelectCompanyResponse =
        client
            .post("auth/company") {
                authHeaders(token)
                setBody(request)
            }.body()

    suspend fun getProducts(
        token: String,
        limit: Int,
        offset: Int,
        search: String?,
        includeTotal: Boolean? = null,
        departmentId: Int? = null,
    ): PagedResponse<ProductDto> =
        client
            .get("items") {
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
                    if (departmentId != null && departmentId > 0) {
                        parameters.append("departmentId", departmentId.toString())
                    }
                }
            }.body()

    suspend fun getDepartments(token: String): List<DepartmentDto> {
        val response =
            client
                .get("items/departments") {
                    authHeaders(token)
                }.body<DepartmentsResponse>()
        return response.data
    }

    suspend fun getSections(
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

    suspend fun getFamilies(
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

    suspend fun getSubFamilies(
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

    suspend fun getBrands(token: String): List<DepartmentDto> {
        val response =
            client
                .get("items/brands") {
                    authHeaders(token)
                }.body<DepartmentsResponse>()
        return response.data
    }

    suspend fun getLines(
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

    suspend fun getBestSellers(
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

    suspend fun getPromotions(token: String): List<PromocionDto> {
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

    suspend fun getFacturasResumen(token: String): FacturasResumenDto =
        client
            .get("facturas/resumen") {
                authHeaders(token)
            }.body()

    suspend fun createProduct(
        token: String,
        request: CreateProductRequest,
    ): ProductDto =
        client
            .post("items") {
                authHeaders(token)
                setBody(request)
            }.body()

    suspend fun updateProduct(
        token: String,
        id: Int,
        request: CreateProductRequest,
    ): ProductDto =
        client
            .put("items/$id") {
                authHeaders(token)
                setBody(request)
            }.body()

    suspend fun getProductById(
        token: String,
        id: String,
    ): ProductDto =
        client
            .get("items/$id") {
                authHeaders(token)
            }.body()

    suspend fun getItemStock(
        token: String,
        id: String,
    ): ProductStockResponseDto =
        client
            .get("items/$id/stock") {
                authHeaders(token)
            }.body()

    suspend fun getItemLots(
        token: String,
        id: String,
    ): ItemLotsResponseDto =
        client
            .get("items/$id/lots") {
                authHeaders(token)
            }.body()

    suspend fun getClients(
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

    suspend fun createClient(
        token: String,
        request: CreateClientRequest,
    ): ClientDto =
        client
            .post("clients") {
                authHeaders(token)
                setBody(request)
            }.body()

    suspend fun getDefaultClient(token: String): ClientDto =
        client
            .get("clients/default") {
                authHeaders(token)
            }.body()

    suspend fun getClientSucursales(
        token: String,
        clientId: String,
    ): List<ClientSucursalDto> =
        client
            .get("clients/$clientId/sucursales") {
                authHeaders(token)
            }.body()

    suspend fun updateClient(
        token: String,
        id: String,
        request: CreateClientRequest,
    ): ClientDto =
        client
            .put("clients/$id") {
                authHeaders(token)
                setBody(request)
            }.body()

    suspend fun getCountries(
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

    suspend fun getAddressLevels(
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

    suspend fun getClientTypes(
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
}
