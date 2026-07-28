package com.amaxonia.pos.data.remote.api

import com.amaxonia.pos.domain.model.mesas.AbrirSesionRequest
import com.amaxonia.pos.domain.model.mesas.AbrirSesionResponse
import com.amaxonia.pos.domain.model.mesas.EstadosMesasResponse
import com.amaxonia.pos.domain.model.mesas.SesionActivaResponse
import com.amaxonia.pos.domain.model.mesas.SesionMutacionResponse

/**
 * Sesiones operativas de mesa para el POS. Endpoints asociados al área `{areaId}` y a la mesa
 * `{mesaId}`. La sucursal nunca viaja: la deriva el backend desde `cajaId`.
 *
 * Resultados:
 * - `estados` solo falla si no hay conexión o el backend responde con `{"error": ...}`;
 *   cualquier estado 409/404/etc del backend se traduce a `Result.failure(IllegalStateException(msg))`
 *   con el mensaje exacto del backend.
 */
interface SesionMesaApi {
    suspend fun getEstados(
        cajaId: String,
        areaId: Int,
        authHeader: String,
    ): Result<EstadosMesasResponse>

    suspend fun abrir(
        cajaId: String,
        areaId: Int,
        mesaId: Int,
        request: AbrirSesionRequest,
        authHeader: String,
    ): Result<AbrirSesionResponse>

    suspend fun getSesionActiva(
        cajaId: String,
        areaId: Int,
        mesaId: Int,
        authHeader: String,
    ): Result<SesionActivaResponse>

    suspend fun cerrar(
        cajaId: String,
        areaId: Int,
        mesaId: Int,
        sesionId: Int,
        authHeader: String,
    ): Result<SesionMutacionResponse>

    suspend fun cancelar(
        cajaId: String,
        areaId: Int,
        mesaId: Int,
        sesionId: Int,
        authHeader: String,
    ): Result<SesionMutacionResponse>
}
