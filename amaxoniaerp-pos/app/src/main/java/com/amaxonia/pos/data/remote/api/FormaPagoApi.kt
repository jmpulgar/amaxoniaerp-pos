package com.amaxonia.pos.data.remote.api

import com.amaxonia.pos.domain.model.payment.FormasPagoResponse

interface FormaPagoApi {
    suspend fun getFormasPago(
        cajaId: String?,
        authHeader: String
    ): Result<FormasPagoResponse>
}
