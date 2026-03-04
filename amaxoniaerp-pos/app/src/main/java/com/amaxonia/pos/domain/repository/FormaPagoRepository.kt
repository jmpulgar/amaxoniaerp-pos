package com.amaxonia.pos.domain.repository

import com.amaxonia.pos.domain.model.payment.FormaPago

interface FormaPagoRepository {
    suspend fun getFormasPago(cajaId: String?): Result<List<FormaPago>>
}
