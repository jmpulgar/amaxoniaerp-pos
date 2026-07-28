package com.amaxonia.pos.domain.repository

import com.amaxonia.pos.domain.model.mesas.CrearPedidoMesaRequest
import com.amaxonia.pos.domain.model.mesas.EnviarComandaRequest
import com.amaxonia.pos.domain.model.mesas.PedidoMesa

/**
 * Pedidos y comandas ligados a la sesión de mesa.
 *
 * Reglas contractuales:
 * - El `sesionId` ya está abierto: el repositorio deriva del backend la validación de
 *   pertenencia sesión ⇄ mesa y el bloqueo de operaciones si la sesión ya no está activa.
 * - `estado` opcional para listar solo pedidos en un estado concreto (p.e. solo PENDIENTE).
 * - Las funciones devuelven `Result.success` con el payload del backend o
 *   `Result.failure(IllegalStateException(msg))` con el mensaje exacto del backend.
 */
interface PedidosMesaRepository {
    suspend fun listar(
        cajaId: String,
        areaId: Int,
        mesaId: Int,
        sesionId: Int,
        estado: String? = null,
    ): Result<List<PedidoMesa>>

    suspend fun crear(
        cajaId: String,
        areaId: Int,
        mesaId: Int,
        sesionId: Int,
        request: CrearPedidoMesaRequest,
    ): Result<List<PedidoMesa>>

    suspend fun enviarComanda(
        cajaId: String,
        areaId: Int,
        mesaId: Int,
        sesionId: Int,
        request: EnviarComandaRequest = EnviarComandaRequest(),
    ): Result<List<PedidoMesa>>

    // Firma conserva contrato público usado por ambos sabores y adaptadores existentes.
    @Suppress("LongParameterList")
    suspend fun cambiarEstado(
        cajaId: String,
        areaId: Int,
        mesaId: Int,
        sesionId: Int,
        pedidoId: Int,
        estado: String,
    ): Result<PedidoMesa>
}
