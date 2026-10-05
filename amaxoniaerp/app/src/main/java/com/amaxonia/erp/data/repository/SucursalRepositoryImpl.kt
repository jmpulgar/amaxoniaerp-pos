package com.amaxonia.erp.data.repository

import com.amaxonia.erp.data.local.LocalStore
import com.amaxonia.erp.data.remote.ApiService
import com.amaxonia.erp.data.remote.SaveSucursalRequest
import com.amaxonia.erp.data.remote.createSucursal
import com.amaxonia.erp.data.remote.getSucursales
import com.amaxonia.erp.data.remote.getSucursalesList
import com.amaxonia.erp.data.remote.updateSucursal
import com.amaxonia.erp.domain.model.Sucursal
import com.amaxonia.erp.domain.repository.SucursalRepository

class SucursalRepositoryImpl(
    private val apiService: ApiService,
    private val localStore: LocalStore,
) : SucursalRepository {

    private suspend fun getContext(): Pair<String, String> {
        val session = localStore.readCompanySession()
            ?: error("No hay sesión de empresa activa")
        val adminDb = session.company.adminDb.ifBlank { "default" }
        return Pair(session.token, adminDb)
    }

    override suspend fun getSucursales(): Result<List<Sucursal>> =
        runCatching {
            val (token, adminDb) = getContext()
            val active = localStore.readActiveSucursal()

            runCatching {
                val fullList = apiService.getSucursalesList(token, adminDb)
                fullList.map { dto ->
                    Sucursal(
                        id = dto.id.toString(),
                        nombre = dto.sucursal ?: "Sucursal ${dto.id}",
                        codigo = dto.codigo,
                        serie = dto.serie,
                        codigoSucursalEmisor = dto.codigoSucursalEmisor,
                        descripcion = dto.descripcion,
                        defaultWarehouseId = dto.defaultWarehouseId,
                        isActive = active?.first == dto.id.toString(),
                    )
                }
            }.getOrElse {
                // Fallback a sync catálogo si /api/sucursales no responde
                val fallbackList = apiService.getSucursales(token)
                fallbackList.map { item ->
                    Sucursal(
                        id = item.id,
                        nombre = item.nombre ?: "Sucursal ${item.id}",
                        codigo = item.id,
                        isActive = active?.first == item.id,
                    )
                }
            }
        }

    override suspend fun createSucursal(request: SaveSucursalRequest): Result<Sucursal> =
        runCatching {
            val (token, adminDb) = getContext()
            val created = apiService.createSucursal(token, adminDb, request)
            val active = localStore.readActiveSucursal()
            Sucursal(
                id = created.id.toString(),
                nombre = created.sucursal ?: "Sucursal ${created.id}",
                codigo = created.codigo,
                serie = created.serie,
                codigoSucursalEmisor = created.codigoSucursalEmisor,
                descripcion = created.descripcion,
                defaultWarehouseId = created.defaultWarehouseId,
                isActive = active?.first == created.id.toString(),
            )
        }

    override suspend fun updateSucursal(id: Int, request: SaveSucursalRequest): Result<Sucursal> =
        runCatching {
            val (token, adminDb) = getContext()
            val updated = apiService.updateSucursal(token, adminDb, id, request)
            val active = localStore.readActiveSucursal()
            Sucursal(
                id = updated.id.toString(),
                nombre = updated.sucursal ?: "Sucursal ${updated.id}",
                codigo = updated.codigo,
                serie = updated.serie,
                codigoSucursalEmisor = updated.codigoSucursalEmisor,
                descripcion = updated.descripcion,
                defaultWarehouseId = updated.defaultWarehouseId,
                isActive = active?.first == updated.id.toString(),
            )
        }

    override suspend fun getActiveSucursal(): Pair<String, String>? =
        localStore.readActiveSucursal()

    override suspend fun setActiveSucursal(id: String, name: String) {
        localStore.saveActiveSucursal(id, name)
    }
}
