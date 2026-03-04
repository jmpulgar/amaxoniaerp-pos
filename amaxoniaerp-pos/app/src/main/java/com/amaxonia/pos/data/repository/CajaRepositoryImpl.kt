package com.amaxonia.pos.data.repository

import com.amaxonia.pos.data.local.LocalStore
import com.amaxonia.pos.data.remote.api.CajaApi
import com.amaxonia.pos.domain.model.caja.AperturaRequest
import com.amaxonia.pos.domain.model.caja.Caja
import com.amaxonia.pos.domain.model.caja.CajaStatusResponse
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
            cajaApi.checkCajaStatus(cajaId, authHeader, companyDb)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun openCaja(request: AperturaRequest): Result<CajaStatusResponse> {
        return try {
            val authHeader = getAuthHeader()
            val companyDb = getCompanyDb()
            cajaApi.openCaja(request, authHeader, companyDb)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun setActiveCaja(caja: Caja) {
        _activeCajaName.update { caja.descripcion ?: "Caja Principal" }
        _activeCaja.update { caja }
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
