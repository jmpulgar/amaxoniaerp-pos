package com.amaxonia.pos.data.remote.api

import com.amaxonia.pos.domain.model.mesas.CambiarEstadoPedidoRequest
import com.amaxonia.pos.domain.model.mesas.CrearPedidoMesaRequest
import com.amaxonia.pos.domain.model.mesas.EnviarComandaRequest
import com.amaxonia.pos.domain.model.mesas.EnviarComandaResponse
import com.amaxonia.pos.domain.model.mesas.PedidoMesa
import com.amaxonia.pos.domain.model.mesas.PedidoMesaActualizadoResponse
import com.amaxonia.pos.domain.model.mesas.PedidoMesaCreadoResponse
import com.amaxonia.pos.domain.model.mesas.PedidosMesaListResponse

/**
 * Pedidos y comandas ligados a la sesión de mesa. Endpoints:
 *
 * - `GET   .../sesiones/{sesionId}/pedidos?estado=` listar
 * - `POST  .../sesiones/{sesionId}/pedidos` crear
 * - `POST  .../sesiones/{sesionId}/pedidos/enviar` enviar comanda
 * - `PATCH .../sesiones/{sesionId}/pedidos/{pedidoId}` cambiar estado
 *
 * Sigue el mismo patrón de [SesionMesaApi]: el `authHeader` viaja por cada llamada, y los
 * errores del backend (`{"error": "..."}`) se traducen a `Result.failure` con ese mensaje.
 */
interface PedidosMesaApi {
    // Firma refleja parámetros HTTP separados; agruparlos cambiaría contrato del adaptador.
    @Suppress("LongParameterList")
    suspend fun listar(
        cajaId: String,
        areaId: Int,
        mesaId: Int,
        sesionId: Int,
        estado: String?,
        authHeader: String,
    ): Result<PedidosMesaListResponse>

    // Firma refleja parámetros HTTP separados; agruparlos cambiaría contrato del adaptador.
    @Suppress("LongParameterList")
    suspend fun crear(
        cajaId: String,
        areaId: Int,
        mesaId: Int,
        sesionId: Int,
        request: CrearPedidoMesaRequest,
        authHeader: String,
    ): Result<PedidoMesaCreadoResponse>

    // Firma refleja parámetros HTTP separados; agruparlos cambiaría contrato del adaptador.
    @Suppress("LongParameterList")
    suspend fun enviarComanda(
        cajaId: String,
        areaId: Int,
        mesaId: Int,
        sesionId: Int,
        request: EnviarComandaRequest,
        authHeader: String,
    ): Result<EnviarComandaResponse>

    // Firma refleja parámetros HTTP separados; agruparlos cambiaría contrato del adaptador.
    @Suppress("LongParameterList")
    suspend fun cambiarEstado(
        cajaId: String,
        areaId: Int,
        mesaId: Int,
        sesionId: Int,
        pedidoId: Int,
        request: CambiarEstadoPedidoRequest,
        authHeader: String,
    ): Result<PedidoMesaActualizadoResponse>
}

/** Tipo de retorno canónico para evitar imports repetidos en Repository/Impl. */
typealias PedidosMesaList = List<PedidoMesa>
