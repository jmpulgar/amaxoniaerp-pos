package com.amaxonia.pos.domain.repository

import com.amaxonia.pos.domain.model.mesas.EstadoMesaResponse
import com.amaxonia.pos.domain.model.mesas.SesionMesa

/**
 * Sesiones operativas de mesa. Solo vías de escritura/habilitación para esta fase:
 * - apertura (al confirmar mesa disponible),
 * - recuperación de la sesión activa de una mesa (al tocar mesa ocupada),
 * - cierre/cancelación de una sesión **vacía** (sin pedidos/comandas).
 *
 * La sucursal nunca es parámetro: la resuelve el backend con `cajaId`.
 *
 * Resultados:
 * - En éxito, `Result.success(...)` con el DTO del backend.
 * - En fallo `Result.failure(IllegalStateException(msg))` donde `msg` es el mensaje del backend
 *   ("La mesa ya tiene una sesión abierta", "La mesa no pertenece al área", etc.).
 */
interface SesionMesaRepository {
    /** Estados de todas las mesas activas de un área. */
    suspend fun getEstados(
        cajaId: String,
        areaId: Int,
    ): Result<List<EstadoMesaResponse>>

    /** Abre una sesión. Devuelve la sesión creada o error. */
    suspend fun abrir(
        cajaId: String,
        areaId: Int,
        mesaId: Int,
        cantidadPersonas: Int,
    ): Result<SesionMesa>

    /** Recupera la sesión activa de una mesa o `null` si no la hay. */
    suspend fun getSesionActiva(
        cajaId: String,
        areaId: Int,
        mesaId: Int,
    ): Result<SesionMesa?>

    /** Cierra una sesión abierta (deja histórico: `estado=CERRADA, activo=false`). */
    suspend fun cerrar(
        cajaId: String,
        areaId: Int,
        mesaId: Int,
        sesionId: Int,
    ): Result<SesionMesa>

    /** Cancela una sesión abierta y vacía (elimina el registro). */
    suspend fun cancelar(
        cajaId: String,
        areaId: Int,
        mesaId: Int,
        sesionId: Int,
    ): Result<SesionMesa>
}
