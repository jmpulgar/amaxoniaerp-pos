package com.amaxonia.pos.data.repository

import com.amaxonia.pos.data.local.LocalStore
import com.amaxonia.pos.data.remote.api.CajaApi
import com.amaxonia.pos.domain.model.caja.AperturaRequest
import com.amaxonia.pos.domain.model.caja.Caja
import com.amaxonia.pos.domain.model.caja.CajaStatusResponse
import com.amaxonia.pos.domain.model.caja.CierreCajaRequest
import com.amaxonia.pos.domain.model.caja.CierreCajaResponse
import com.amaxonia.pos.domain.model.caja.CierreCajaSummary
import com.amaxonia.pos.domain.repository.CajaRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class CajaRepositoryImpl(
    private val cajaApi: CajaApi,
    private val localStore: LocalStore
) : CajaRepository {

    private val _activeCajaName = MutableStateFlow("Caja no seleccionada")
    override val activeCajaName: StateFlow<String> = _activeCajaName.asStateFlow()
    private val _activeCaja = MutableStateFlow<Caja?>(null)
    override val activeCaja: StateFlow<Caja?> = _activeCaja.asStateFlow()

    /** Stores the active session so we can build close-register summaries. */
    private var activeSecuencia: com.amaxonia.pos.domain.model.caja.CajaSecuencia? = null

    override suspend fun getCajas(): Result<List<Caja>> {
        return try {
            val authHeader = getAuthHeader()
            val companyDb = getCompanyDb()
            cajaApi.getCajas(authHeader, companyDb)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun checkCajaStatus(cajaId: String): Result<CajaStatusResponse> {
        return try {
            val authHeader = getAuthHeader()
            val companyDb = getCompanyDb()
            val result = cajaApi.checkCajaStatus(cajaId, authHeader, companyDb)
            result.onSuccess { response ->
                if (response.isOpen) {
                    activeSecuencia = response.cajaSecuencia
                }
            }
            result
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun openCaja(request: AperturaRequest): Result<CajaStatusResponse> {
        return try {
            val authHeader = getAuthHeader()
            val companyDb = getCompanyDb()
            val result = cajaApi.openCaja(request, authHeader, companyDb)
            result.onSuccess { response ->
                activeSecuencia = response.cajaSecuencia
            }
            result
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun closeCaja(request: CierreCajaRequest): Result<CierreCajaResponse> {
        return try {
            val authHeader = getAuthHeader()
            val companyDb = getCompanyDb()
            val result = cajaApi.closeCaja(request, authHeader, companyDb)
            result.onSuccess {
                activeSecuencia = null
            }
            result
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getCierreSummary(): Result<CierreCajaSummary> {
        val caja = _activeCaja.value
            ?: return Result.failure(IllegalStateException("No hay caja activa"))
        val secuencia = activeSecuencia

        return Result.success(
            CierreCajaSummary(
                cajaName = caja.descripcion ?: "Caja",
                openedAt = secuencia?.fechaApertura ?: "—",
                openAmount = secuencia?.montoApertura ?: 0.0,
                totalSales = 0.0,
                totalCash = 0.0,
                totalCard = 0.0,
                totalOther = 0.0,
                transactionCount = 0,
                expectedClose = secuencia?.montoApertura ?: 0.0
            )
        )
    }

    override fun setActiveCaja(caja: Caja) {
        _activeCajaName.update { caja.descripcion ?: "Caja Principal" }
        _activeCaja.update { caja }
    }

    override fun clearActiveCaja() {
        _activeCajaName.update { "Caja no seleccionada" }
        _activeCaja.update { null }
        activeSecuencia = null
    }

    private suspend fun getAuthHeader(): String {
        val token = localStore.readCompanySession()?.token
            ?: throw Exception("No autorizado: primero selecciona una empresa")
        return "Bearer $token"
    }

    private suspend fun getCompanyDb(): String {
        return localStore.readCompanySession()?.company?.adminDb
            ?: throw Exception("Base de datos de empresa no configurada")
    }
}
