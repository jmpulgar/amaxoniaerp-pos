package com.amaxonia.erp.data.repository

import com.amaxonia.erp.data.local.LocalStore
import com.amaxonia.erp.data.local.db.PaymentMethodDao
import com.amaxonia.erp.data.local.db.PaymentMethodEntity
import com.amaxonia.erp.data.remote.ApiService
import com.amaxonia.erp.data.remote.NetworkMonitor
import com.amaxonia.erp.data.remote.dto.FacturaPrintPayloadDto
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
    private val paymentMethodDao: PaymentMethodDao? = null,
    private val networkMonitor: NetworkMonitor? = null,
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
            val isOnline = networkMonitor?.isOnline() ?: true
            if (!isOnline) {
                return@runCatching readFromRoom(cajaId)
            }
            val (token, adminDb) = getContext()
            val response = apiService.getFormasPago(token, adminDb, cajaId)
            response.data
        }.recoverCatching { error ->
            val fromRoom = readFromRoom(cajaId)
            if (fromRoom.isNotEmpty()) {
                fromRoom
            } else {
                throw error
            }
        }

    private suspend fun readFromRoom(cajaId: String?): List<FormaPagoDto> {
        val dao = paymentMethodDao ?: return emptyList()
        val entities = if (!cajaId.isNullOrBlank()) {
            val byCaja = dao.getByCaja(cajaId.trim())
            if (byCaja.isNotEmpty()) byCaja else dao.getAll()
        } else {
            dao.getAll()
        }
        return entities.map { it.toDto() }
    }

    private fun PaymentMethodEntity.toDto(): FormaPagoDto =
        FormaPagoDto(
            idFormaPago = idFormaPago,
            siglas = siglas,
            codigo = codigo?.toString(),
            descripcion = descripcion,
            activo = activo,
            pos = pos,
            tipoMoneda = tipoMoneda.orEmpty(),
        )

    override suspend fun getPrintPayload(facturaId: String): Result<FacturaPrintPayloadDto> =
        runCatching {
            val (token, adminDb) = getContext()
            apiService.getFacturaPrintPayload(token, adminDb, facturaId)
        }
}

