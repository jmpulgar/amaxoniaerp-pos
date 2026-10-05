package com.amaxonia.erp.domain.repository

import com.amaxonia.erp.data.remote.SaveSucursalRequest
import com.amaxonia.erp.domain.model.Sucursal

interface SucursalRepository {
    suspend fun getSucursales(): Result<List<Sucursal>>
    suspend fun createSucursal(request: SaveSucursalRequest): Result<Sucursal>
    suspend fun updateSucursal(id: Int, request: SaveSucursalRequest): Result<Sucursal>
    suspend fun getActiveSucursal(): Pair<String, String>?
    suspend fun setActiveSucursal(id: String, name: String)
}
