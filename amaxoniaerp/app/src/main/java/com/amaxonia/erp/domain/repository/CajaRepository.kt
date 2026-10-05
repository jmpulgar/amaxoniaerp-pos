package com.amaxonia.erp.domain.repository

import com.amaxonia.erp.domain.model.AperturaRequest
import com.amaxonia.erp.domain.model.Caja
import com.amaxonia.erp.domain.model.CajaSecuencia
import com.amaxonia.erp.domain.model.CajaSessionStatus
import com.amaxonia.erp.domain.model.CajaStatusResponse
import com.amaxonia.erp.domain.model.CierreCajaRequest
import com.amaxonia.erp.domain.model.CierreCajaResponse
import com.amaxonia.erp.domain.model.CierreCajaSummary
import com.amaxonia.erp.domain.model.SaveCajaRequest
import kotlinx.coroutines.flow.StateFlow

interface CajaRepository {
    val activeCaja: StateFlow<Caja?>
    val activeCajaName: StateFlow<String>
    val activeCajaSecuencia: StateFlow<CajaSecuencia?>
    val sessionStatus: StateFlow<CajaSessionStatus>

    suspend fun getCajas(): Result<List<Caja>>
    suspend fun createCaja(request: SaveCajaRequest): Result<Caja>
    suspend fun updateCaja(id: String, request: SaveCajaRequest): Result<Caja>
    suspend fun getNextSecuenciaCodigo(idCaja: String): Result<String>
    suspend fun restoreActiveCajaIfValid()
    suspend fun checkCajaStatus(cajaId: String): Result<CajaStatusResponse>
    suspend fun openCaja(request: AperturaRequest): Result<CajaStatusResponse>
    suspend fun closeCaja(request: CierreCajaRequest): Result<CierreCajaResponse>
    suspend fun getCierreSummary(): Result<CierreCajaSummary>
    suspend fun getCierreSummaryForSequence(caja: Caja, sequenceId: String): Result<CierreCajaSummary>
    suspend fun setActiveCaja(caja: Caja)
    suspend fun setActiveCaja(id: String, name: String)
    suspend fun clearActiveCaja()
    suspend fun markSequenceClosed()
    suspend fun getActiveCaja(): Pair<String, String>?
}
