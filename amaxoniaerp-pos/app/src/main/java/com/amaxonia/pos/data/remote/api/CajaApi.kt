package com.amaxonia.pos.data.remote.api

import com.amaxonia.pos.domain.model.caja.AperturaRequest
import com.amaxonia.pos.domain.model.caja.Caja
import com.amaxonia.pos.domain.model.caja.CajaStatusResponse

interface CajaApi {
    suspend fun getCajas(
        authHeader: String,
        companyDb: String
    ): Result<List<Caja>>

    suspend fun checkCajaStatus(
        cajaId: String,
        authHeader: String,
        companyDb: String
    ): Result<CajaStatusResponse>

    suspend fun openCaja(
        request: AperturaRequest,
        authHeader: String,
        companyDb: String
    ): Result<CajaStatusResponse>
}
