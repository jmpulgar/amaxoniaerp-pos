package com.amaxonia.pos.domain.repository

import com.amaxonia.pos.domain.model.caja.AperturaRequest
import com.amaxonia.pos.domain.model.caja.Caja
import com.amaxonia.pos.domain.model.caja.CajaStatusResponse
import kotlinx.coroutines.flow.StateFlow

interface CajaRepository {
    val activeCajaName: StateFlow<String>
    val activeCaja: StateFlow<Caja?>
    
    suspend fun getCajas(): Result<List<Caja>>
    suspend fun checkCajaStatus(cajaId: String): Result<CajaStatusResponse>
    suspend fun openCaja(request: AperturaRequest): Result<CajaStatusResponse>
    fun setActiveCaja(caja: Caja)
}
