package com.amaxonia.pos.data.repository

import com.amaxonia.pos.core.result.catchingResult
import com.amaxonia.pos.data.remote.api.SesionMesaApi
import com.amaxonia.pos.domain.model.mesas.AbrirSesionRequest
import com.amaxonia.pos.domain.model.mesas.EstadoMesaResponse
import com.amaxonia.pos.domain.model.mesas.SesionMesa
import com.amaxonia.pos.domain.repository.CompanyTokenReader
import com.amaxonia.pos.domain.repository.SesionMesaRepository
import com.amaxonia.pos.domain.repository.SessionConfigurationException

/**
 * Puerta de enlace al SesionMesa API. No cachea en disco: los estados de mesa son relativos
 * "a ahora mismo" (disponible/ocupada cambia a cada minuto) y snapshotearlos induciría a una
 * UI falsa al volver a entrar. La UI decide si hidrata el mapa de estados desde red o lo deja
 * vacío indicando "no disponible" mientras carga.
 */
class SesionMesaRepositoryImpl(
    private val api: SesionMesaApi,
    private val session: CompanyTokenReader,
) : SesionMesaRepository {
    override suspend fun getEstados(
        cajaId: String,
        areaId: Int,
    ): Result<List<EstadoMesaResponse>> =
        catchingResult {
            val authHeader = getAuthHeader()
            api.getEstados(cajaId, areaId, authHeader).mapCatching { response ->
                if (!response.success) error(response.error ?: "No se pudieron consultar los estados")
                response.data
            }
        }

    override suspend fun abrir(
        cajaId: String,
        areaId: Int,
        mesaId: Int,
        cantidadPersonas: Int,
    ): Result<SesionMesa> =
        catchingResult {
            val authHeader = getAuthHeader()
            api
                .abrir(
                    cajaId = cajaId,
                    areaId = areaId,
                    mesaId = mesaId,
                    request = AbrirSesionRequest(cantidadPersonas = cantidadPersonas),
                    authHeader = authHeader,
                ).mapCatching { response ->
                    if (!response.success) error(response.error ?: "No se pudo abrir la sesión")
                    response.sesion
                }
        }

    override suspend fun getSesionActiva(
        cajaId: String,
        areaId: Int,
        mesaId: Int,
    ): Result<SesionMesa?> =
        catchingResult {
            val authHeader = getAuthHeader()
            api.getSesionActiva(cajaId, areaId, mesaId, authHeader).mapCatching { response ->
                if (!response.success) error(response.error ?: "No se pudo recuperar la sesión")
                response.sesion
            }
        }

    override suspend fun cerrar(
        cajaId: String,
        areaId: Int,
        mesaId: Int,
        sesionId: Int,
    ): Result<SesionMesa> =
        catchingResult {
            val authHeader = getAuthHeader()
            api.cerrar(cajaId, areaId, mesaId, sesionId, authHeader).mapCatching { response ->
                if (!response.success) error(response.error ?: "No se pudo cerrar la sesión")
                response.sesion
            }
        }

    override suspend fun cancelar(
        cajaId: String,
        areaId: Int,
        mesaId: Int,
        sesionId: Int,
    ): Result<SesionMesa> =
        catchingResult {
            val authHeader = getAuthHeader()
            api.cancelar(cajaId, areaId, mesaId, sesionId, authHeader).mapCatching { response ->
                if (!response.success) error(response.error ?: "No se pudo cancelar la sesión")
                response.sesion
            }
        }

    private suspend fun getAuthHeader(): String {
        val token =
            session.companyToken()
                ?: throw SessionConfigurationException("No autorizado: primero selecciona una empresa")
        return "Bearer $token"
    }
}
