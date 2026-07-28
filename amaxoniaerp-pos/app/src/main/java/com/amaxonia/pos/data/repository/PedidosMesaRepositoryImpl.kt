package com.amaxonia.pos.data.repository

import com.amaxonia.pos.core.result.catchingResult
import com.amaxonia.pos.data.remote.api.PedidosMesaApi
import com.amaxonia.pos.domain.model.mesas.CambiarEstadoPedidoRequest
import com.amaxonia.pos.domain.model.mesas.CrearPedidoMesaRequest
import com.amaxonia.pos.domain.model.mesas.EnviarComandaRequest
import com.amaxonia.pos.domain.model.mesas.PedidoMesa
import com.amaxonia.pos.domain.repository.CompanyTokenReader
import com.amaxonia.pos.domain.repository.PedidosMesaRepository
import com.amaxonia.pos.domain.repository.SessionConfigurationException

/**
 * Puerta de enlace al [PedidosMesaApi]. No cachea: cada llamado consulta al backend porque los
 * estados de comanda mutan rápidamente (varias cajas pueden enviar o cancelar simultáneamente
 * sobre la misma sesión).
 */
class PedidosMesaRepositoryImpl(
    private val api: PedidosMesaApi,
    private val session: CompanyTokenReader,
) : PedidosMesaRepository {
    override suspend fun listar(
        cajaId: String,
        areaId: Int,
        mesaId: Int,
        sesionId: Int,
        estado: String?,
    ): Result<List<PedidoMesa>> =
        catchingResult {
            val authHeader = getAuthHeader()
            api.listar(cajaId, areaId, mesaId, sesionId, estado, authHeader).mapCatching { response ->
                if (!response.success) error(response.error ?: "No se pudieron consultar los pedidos")
                response.data
            }
        }

    override suspend fun crear(
        cajaId: String,
        areaId: Int,
        mesaId: Int,
        sesionId: Int,
        request: CrearPedidoMesaRequest,
    ): Result<List<PedidoMesa>> =
        catchingResult {
            val authHeader = getAuthHeader()
            api.crear(cajaId, areaId, mesaId, sesionId, request, authHeader).mapCatching { response ->
                if (!response.success) error(response.error ?: "No se pudieron crear los pedidos")
                response.data
            }
        }

    override suspend fun enviarComanda(
        cajaId: String,
        areaId: Int,
        mesaId: Int,
        sesionId: Int,
        request: EnviarComandaRequest,
    ): Result<List<PedidoMesa>> =
        catchingResult {
            val authHeader = getAuthHeader()
            api.enviarComanda(cajaId, areaId, mesaId, sesionId, request, authHeader).mapCatching { response ->
                if (!response.success) error(response.error ?: "No se pudo enviar la comanda")
                response.data
            }
        }

    override suspend fun cambiarEstado(
        cajaId: String,
        areaId: Int,
        mesaId: Int,
        sesionId: Int,
        pedidoId: Int,
        estado: String,
    ): Result<PedidoMesa> =
        catchingResult {
            val authHeader = getAuthHeader()
            api
                .cambiarEstado(
                    cajaId = cajaId,
                    areaId = areaId,
                    mesaId = mesaId,
                    sesionId = sesionId,
                    pedidoId = pedidoId,
                    request = CambiarEstadoPedidoRequest(estado = estado),
                    authHeader = authHeader,
                ).mapCatching { response ->
                    if (!response.success) error(response.error ?: "No se pudo cambiar el estado del pedido")
                    response.data
                }
        }

    private suspend fun getAuthHeader(): String {
        val token =
            session.companyToken()
                ?: throw SessionConfigurationException("No autorizado: primero selecciona una empresa")
        return "Bearer $token"
    }
}
