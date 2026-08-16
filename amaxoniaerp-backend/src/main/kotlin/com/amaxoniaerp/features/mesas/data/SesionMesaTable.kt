package com.amaxoniaerp.features.mesas.data

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.datetime

private const val SCHEMA_CAJA_ID_MAX_LENGTH = 36
private const val SCHEMA_ESTADO_MAX_LENGTH = 30

/**
 * Sesión operativa de una mesa: el lapso durante el cual una mesa está siendo atendida.
 *
 * Diseño:
 * - Producción usa la columna generada nullable `mesa_activa_id` de la migración 001 para
 *   imponer una sola sesión activa por mesa sin limitar el número de sesiones históricas.
 *   El modelo Exposed omite esa columna generada para seguir siendo portable en pruebas H2.
 * - `mesas.activo` NUNCA se usa como estado de ocupación: ese flag indica únicamente si la
 *   mesa está disponible administrativamente. La ocupación operativa vive aquí.
 * - El descubrimiento de la sucursal, área y mesa válidas lo hace el repositorio
 *   consultando `caja`, `plantas` y `mesas` antes de insertar.
 */
object SesionMesaTable : Table("sesion_mesa") {
    val id = integer("id").autoIncrement("seq_sesion_mesa")
    val sucursalId = integer("sucursal_id")
    val cajaId = varchar("caja_id", SCHEMA_CAJA_ID_MAX_LENGTH)
    val areaId = integer("area_id")
    val mesaId = integer("mesa_id")
    val usuarioId = integer("usuario_id")
    val cantidadPersonas = integer("cantidad_personas").default(1)
    val estado = varchar("estado", SCHEMA_ESTADO_MAX_LENGTH)
    val fechaApertura = datetime("fecha_apertura")
    val fechaCierre = datetime("fecha_cierre").nullable()
    val activo = integer("activo").default(1)

    override val primaryKey = PrimaryKey(id)
}
