package com.amaxonia.pos.data.repository

import com.amaxonia.pos.core.result.catchingResult
import com.amaxonia.pos.data.local.LocalStore
import com.amaxonia.pos.data.remote.api.SalesApi
import com.amaxonia.pos.domain.model.sales.ConfirmFacturaFiscalRequestDto
import com.amaxonia.pos.domain.model.sales.ConfirmFacturaFiscalResponseDto
import com.amaxonia.pos.domain.model.sales.EnviarCorreoFacturaResponseDto
import com.amaxonia.pos.domain.model.sales.FacturaPrintPayloadDto
import com.amaxonia.pos.domain.model.sales.ProcessSaleRequestDto
import com.amaxonia.pos.domain.model.sales.ProcessSaleResponseDto
import com.amaxonia.pos.domain.repository.SalesRepository

class SalesRepositoryImpl(
    private val salesApi: SalesApi,
    private val localStore: LocalStore,
) : SalesRepository {
    override suspend fun processSale(payload: ProcessSaleRequestDto): Result<ProcessSaleResponseDto> =
        catchingResult {
            val token =
                localStore.readCompanySession()?.token
                    ?: error("No autorizado: primero selecciona una empresa")
            salesApi.processSale(authHeader = "Bearer $token", payload = payload)
        }

    override suspend fun confirmFacturaFiscal(
        facturaId: String,
        payload: ConfirmFacturaFiscalRequestDto,
    ): Result<ConfirmFacturaFiscalResponseDto> =
        catchingResult {
            val token =
                localStore.readCompanySession()?.token
                    ?: error("No autorizado: primero selecciona una empresa")
            salesApi.confirmFacturaFiscal(authHeader = "Bearer $token", facturaId = facturaId, payload = payload)
        }

    override suspend fun getPrintPayload(facturaId: String): Result<FacturaPrintPayloadDto> =
        catchingResult {
            val token =
                localStore.readCompanySession()?.token
                    ?: error("No autorizado: primero selecciona una empresa")
            salesApi.getPrintPayload(authHeader = "Bearer $token", facturaId = facturaId)
        }

    override suspend fun sendReceiptEmail(facturaId: String): Result<EnviarCorreoFacturaResponseDto> =
        catchingResult {
            val token =
                localStore.readCompanySession()?.token
                    ?: error("No autorizado: primero selecciona una empresa")
            salesApi.sendReceiptEmail(authHeader = "Bearer $token", facturaId = facturaId)
        }
}
