package com.amaxonia.erp.data.repository

import com.amaxonia.erp.data.local.LocalStore
import com.amaxonia.erp.data.remote.ApiService
import com.amaxonia.erp.data.remote.dto.FormaPagoDto
import com.amaxonia.erp.data.remote.dto.ProcessSaleRequestDto
import com.amaxonia.erp.data.remote.dto.ProcessSaleResponseDto
import com.amaxonia.erp.data.remote.getFacturaPrintPayload
import com.amaxonia.erp.data.remote.getFormasPago
import com.amaxonia.erp.data.remote.processSale
import com.amaxonia.erp.domain.repository.SalesRepository

class SalesRepositoryImpl(
    private val apiService: ApiService,
    private val localStore: LocalStore,
) : SalesRepository {

    private suspend fun getContext(): Pair<String, String> {
        val session = localStore.readCompanySession()
            ?: error("No hay sesión de empresa activa")
        val adminDb = session.company.adminDb.ifBlank { "default" }
        return Pair(session.token, adminDb)
    }

    override suspend fun processSale(request: ProcessSaleRequestDto): Result<ProcessSaleResponseDto> =
        runCatching {
            val (token, adminDb) = getContext()
            apiService.processSale(token, adminDb, request)
        }

    override suspend fun getFormasPago(cajaId: String?): Result<List<FormaPagoDto>> =
        runCatching {
            val (token, adminDb) = getContext()
            val response = apiService.getFormasPago(token, adminDb, cajaId)
            response.data
        }

    override suspend fun getPrintPayload(facturaId: String): Result<com.amaxonia.erp.data.remote.dto.FacturaPrintPayloadDto> =
        runCatching {
            val (token, adminDb) = getContext()
            apiService.getFacturaPrintPayload(token, adminDb, facturaId)
        }
}

