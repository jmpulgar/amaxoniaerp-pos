package com.amaxonia.pos.data.repository

import com.amaxonia.pos.data.local.LocalStore
import com.amaxonia.pos.data.remote.NetworkMonitor
import com.amaxonia.pos.data.remote.api.FormaPagoApi
import com.amaxonia.pos.domain.model.payment.FormaPago
import com.amaxonia.pos.domain.repository.FormaPagoRepository

class FormaPagoRepositoryImpl(
    private val formaPagoApi: FormaPagoApi,
    private val localStore: LocalStore,
    private val networkMonitor: NetworkMonitor
) : FormaPagoRepository {

    override suspend fun getFormasPago(cajaId: String?): Result<List<FormaPago>> {
        val cached = localStore.readFormasPago(cajaId)
        if (!networkMonitor.isOnline()) {
            return if (cached.isNotEmpty()) {
                Result.success(cached)
            } else {
                Result.failure(IllegalStateException("No hay formas de pago sincronizadas para trabajar offline"))
            }
        }

        return try {
            val authHeader = getAuthHeader()
            formaPagoApi.getFormasPago(cajaId = cajaId, authHeader = authHeader)
                .map { response -> response.data }
                .onSuccess { formasPago -> localStore.saveFormasPago(cajaId, formasPago) }
                .recoverCatching { error ->
                    if (cached.isNotEmpty()) cached else throw error
                }
        } catch (e: Exception) {
            if (cached.isNotEmpty()) Result.success(cached) else Result.failure(e)
        }
    }

    private suspend fun getAuthHeader(): String {
        val token = localStore.readCompanySession()?.token
            ?: throw Exception("No autorizado: primero selecciona una empresa")
        return "Bearer $token"
    }
}
