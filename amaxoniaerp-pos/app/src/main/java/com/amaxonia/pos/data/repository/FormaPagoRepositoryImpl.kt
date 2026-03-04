package com.amaxonia.pos.data.repository

import com.amaxonia.pos.data.local.LocalStore
import com.amaxonia.pos.data.remote.api.FormaPagoApi
import com.amaxonia.pos.domain.model.payment.FormaPago
import com.amaxonia.pos.domain.repository.FormaPagoRepository

class FormaPagoRepositoryImpl(
    private val formaPagoApi: FormaPagoApi,
    private val localStore: LocalStore
) : FormaPagoRepository {

    override suspend fun getFormasPago(cajaId: String?): Result<List<FormaPago>> {
        return try {
            val authHeader = getAuthHeader()
            formaPagoApi.getFormasPago(cajaId = cajaId, authHeader = authHeader)
                .map { response -> response.data }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun getAuthHeader(): String {
        val token = localStore.readCompanySession()?.token
            ?: throw Exception("No autorizado: primero selecciona una empresa")
        return "Bearer $token"
    }
}
