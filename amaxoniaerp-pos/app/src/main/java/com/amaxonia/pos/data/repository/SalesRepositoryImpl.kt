package com.amaxonia.pos.data.repository

import com.amaxonia.pos.data.local.LocalStore
import com.amaxonia.pos.data.remote.api.SalesApi
import com.amaxonia.pos.domain.model.sales.ProcessSaleRequestDto
import com.amaxonia.pos.domain.model.sales.ProcessSaleResponseDto
import com.amaxonia.pos.domain.repository.SalesRepository

class SalesRepositoryImpl(
    private val salesApi: SalesApi,
    private val localStore: LocalStore
) : SalesRepository {

    override suspend fun processSale(payload: ProcessSaleRequestDto): Result<ProcessSaleResponseDto> {
        return try {
            val token = localStore.readCompanySession()?.token
                ?: throw IllegalStateException("No autorizado: primero selecciona una empresa")
            salesApi.processSale(authHeader = "Bearer $token", payload = payload)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
