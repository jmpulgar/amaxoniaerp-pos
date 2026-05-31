package com.amaxonia.pos.domain.repository

import com.amaxonia.pos.domain.model.caja.AperturaRequest
import com.amaxonia.pos.domain.model.caja.Caja
import com.amaxonia.pos.domain.model.caja.CajaSecuencia
import com.amaxonia.pos.domain.model.caja.CajaStatusResponse
import com.amaxonia.pos.domain.model.caja.CierreCajaRequest
import com.amaxonia.pos.domain.model.caja.CierreCajaResponse
import com.amaxonia.pos.domain.model.caja.CierreCajaSummary
import kotlinx.coroutines.flow.StateFlow

interface CajaRepository {
    val activeCajaName: StateFlow<String>
    val activeCaja: StateFlow<Caja?>
    val activeCajaSecuencia: StateFlow<CajaSecuencia?>

    suspend fun getCajas(): Result<List<Caja>>
    suspend fun getNextSecuenciaCodigo(idCaja: String): Result<String>
    suspend fun restoreActiveCajaIfValid()
    suspend fun checkCajaStatus(cajaId: String): Result<CajaStatusResponse>
    suspend fun openCaja(request: AperturaRequest): Result<CajaStatusResponse>
    suspend fun closeCaja(request: CierreCajaRequest): Result<CierreCajaResponse>
    suspend fun getCierreSummary(): Result<CierreCajaSummary>
    suspend fun setActiveCaja(caja: Caja)
    suspend fun clearActiveCaja()
}
