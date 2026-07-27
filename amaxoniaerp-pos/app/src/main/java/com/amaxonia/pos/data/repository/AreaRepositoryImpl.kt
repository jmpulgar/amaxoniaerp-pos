package com.amaxonia.pos.data.repository

import com.amaxonia.pos.core.result.catchingResult
import com.amaxonia.pos.data.remote.api.AreasApi
import com.amaxonia.pos.domain.model.mesas.AreasResult
import com.amaxonia.pos.domain.model.mesas.MesasResult
import com.amaxonia.pos.domain.repository.AreaRepository
import com.amaxonia.pos.domain.repository.CompanyTokenReader
import com.amaxonia.pos.domain.repository.ConnectivityStatus
import com.amaxonia.pos.domain.repository.SalonConfigCache
import com.amaxonia.pos.domain.repository.SessionConfigurationException

/**
 * Áreas y mesas con la misma estrategia que las formas de pago: red primero y, si falla o no hay
 * conexión, la última configuración válida descargada para **esa misma empresa y caja**.
 *
 * No se usa Room a propósito: el sincronizador de catálogos corre al seleccionar empresa, antes de
 * que exista una caja, así que todavía no se conoce la sucursal. El snapshot en `LocalStore` evita
 * además subir la versión de la base local (y su migración) en esta fase.
 */
class AreaRepositoryImpl(
    private val areasApi: AreasApi,
    private val cache: SalonConfigCache,
    private val session: CompanyTokenReader,
    private val connectivity: ConnectivityStatus,
) : AreaRepository {
    override suspend fun getAreas(cajaId: String): Result<AreasResult> {
        val cached = cache.readCachedAreas(cajaId)

        if (!connectivity.isOnline()) {
            return cached?.let { Result.success(it) }
                ?: Result.failure(IllegalStateException(NO_OFFLINE_AREAS))
        }

        return catchingResult {
            val authHeader = getAuthHeader()
            areasApi
                .getAreas(cajaId = cajaId, authHeader = authHeader)
                .mapCatching { response ->
                    if (!response.success) {
                        error(response.error ?: "No se pudieron consultar las áreas")
                    }
                    cache.cacheAreas(cajaId, response.sucursalId, response.data)
                    AreasResult(sucursalId = response.sucursalId, areas = response.data)
                }
        }.recoverCatching { error -> cached ?: throw error }
    }

    override suspend fun getMesas(
        cajaId: String,
        areaId: Int,
    ): Result<MesasResult> {
        val cached = cache.readCachedMesas(cajaId, areaId)

        if (!connectivity.isOnline()) {
            return cached?.let { Result.success(it) }
                ?: Result.failure(IllegalStateException(NO_OFFLINE_MESAS))
        }

        return catchingResult {
            val authHeader = getAuthHeader()
            areasApi
                .getMesas(cajaId = cajaId, areaId = areaId, authHeader = authHeader)
                .mapCatching { response ->
                    if (!response.success) {
                        error(response.error ?: "No se pudieron consultar las mesas")
                    }
                    cache.cacheMesas(cajaId, areaId, response.lienzo, response.imagenUrl, response.data)
                    MesasResult(
                        areaId = areaId,
                        lienzo = response.lienzo,
                        imagenUrl = response.imagenUrl,
                        mesas = response.data,
                    )
                }
        }.recoverCatching { error -> cached ?: throw error }
    }

    private suspend fun getAuthHeader(): String {
        val token =
            session.companyToken()
                ?: throw SessionConfigurationException("No autorizado: primero selecciona una empresa")
        return "Bearer $token"
    }

    private companion object {
        const val NO_OFFLINE_AREAS = "Sin conexión y sin áreas descargadas para esta caja"
        const val NO_OFFLINE_MESAS = "Sin conexión y sin mesas descargadas para esta área"
    }
}
