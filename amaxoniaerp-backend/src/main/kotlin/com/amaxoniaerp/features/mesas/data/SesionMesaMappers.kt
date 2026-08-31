package com.amaxoniaerp.features.mesas.data

import com.amaxoniaerp.features.auth.data.UsersTable
import com.amaxoniaerp.features.mesas.domain.SesionMesaResponse
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.selectAll
import java.time.LocalDateTime

internal fun usuarioNombre(usuarioId: Int): String? =
    try {
        UsersTable
            .select(UsersTable.usuario)
            .where { UsersTable.codUsuario eq usuarioId }
            .singleOrNull()
            ?.get(UsersTable.usuario)
    } catch (_: Exception) {
        null
    }

/**
 * Construye una respuesta sintética cuando acabamos de insertar/actualizar y tenemos los
 * datos frescos en memoria. [usuarioNombre] se resuelve fuera cuando interesa (listados
 * por lote) o puntualmente tras abrir/cerrar; nunca se hace N+1.
 */
@Suppress("LongParameterList")
internal fun sessionRowToResponse(
    id: Int,
    sucursalId: Int,
    cajaId: String,
    areaId: Int,
    mesaId: Int,
    usuarioId: Int,
    usuarioNombre: String?,
    cantidadPersonas: Int,
    estado: String,
    fechaApertura: LocalDateTime,
    fechaCierre: LocalDateTime?,
    activo: Boolean,
): SesionMesaResponse =
    SesionMesaResponse(
        id = id,
        sucursalId = sucursalId,
        cajaId = cajaId,
        areaId = areaId,
        mesaId = mesaId,
        usuarioId = usuarioId,
        usuario = usuarioNombre,
        cantidadPersonas = cantidadPersonas,
        estado = estado,
        fechaApertura = fechaApertura.formatSesionIso(),
        fechaCierre = fechaCierre?.formatSesionIso(),
        activo = activo,
    )

internal fun ResultRow.toSesionMesaResponse(usuarioByCod: Map<Int, String> = emptyMap()): SesionMesaResponse =
    SesionMesaResponse(
        id = this[SesionMesaTable.id],
        sucursalId = this[SesionMesaTable.sucursalId],
        cajaId = this[SesionMesaTable.cajaId],
        areaId = this[SesionMesaTable.areaId],
        mesaId = this[SesionMesaTable.mesaId],
        usuarioId = this[SesionMesaTable.usuarioId],
        // Primero el mapa caché (más eficiente en listados); si no está, no resolvemos
        // en este path para evitar N+1.
        usuario = usuarioByCod[this[SesionMesaTable.usuarioId]],
        cantidadPersonas = this[SesionMesaTable.cantidadPersonas],
        estado = this[SesionMesaTable.estado],
        fechaApertura = this[SesionMesaTable.fechaApertura].formatSesionIso(),
        fechaCierre = this[SesionMesaTable.fechaCierre]?.formatSesionIso(),
        activo = this[SesionMesaTable.activo],
    )

internal fun LocalDateTime.formatSesionIso(): String = SESION_ISO_FORMATTER.format(this)
