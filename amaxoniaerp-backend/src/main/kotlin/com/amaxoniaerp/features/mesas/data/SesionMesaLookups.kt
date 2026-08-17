package com.amaxoniaerp.features.mesas.data

import com.amaxoniaerp.features.auth.data.UsersTable
import com.amaxoniaerp.features.caja.data.CajaTable
import com.amaxoniaerp.features.mesas.domain.SesionMesaResponse
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.selectAll
import java.time.format.DateTimeFormatter

internal val SESION_ISO_FORMATTER: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME

internal val DUPLICATE_KEYS =
    listOf("uq_sesion_mesa_activa", "duplicate key", "unique constraint", "duplicate entry")

internal fun esViolacionUniqueSesion(e: org.jetbrains.exposed.exceptions.ExposedSQLException): Boolean {
    val msg = e.message?.lowercase().orEmpty()
    return DUPLICATE_KEYS.any { msg.contains(it) }
}

internal fun areaActivaPerteneceASucursal(
    areaId: Int,
    sucursalId: Int,
): Boolean =
    PlantasTable
        .selectAll()
        .where {
            (PlantasTable.id eq areaId) and
                (PlantasTable.sucursalId eq sucursalId) and
                (PlantasTable.activo eq ACTIVE)
        }.limit(1)
        .singleOrNull() != null

internal fun mesaActivaPerteneceAArea(
    mesaId: Int,
    areaId: Int,
): Pair<Boolean, Boolean> {
    val row =
        MesasTable
            .selectAll()
            .where { (MesasTable.id eq mesaId) and (MesasTable.plantaId eq areaId) }
            .limit(1)
            .singleOrNull()
            ?: return false to false
    return true to (row[MesasTable.activo] == ACTIVE)
}

internal fun existeSesionActiva(mesaId: Int): Boolean =
    SesionMesaTable
        .selectAll()
        .where { (SesionMesaTable.mesaId eq mesaId) and (SesionMesaTable.activo eq ACTIVE) }
        .limit(1)
        .singleOrNull() != null

internal fun sesionActivaDeMesa(mesaId: Int): SesionMesaResponse? {
    val row =
        SesionMesaTable
            .selectAll()
            .where { (SesionMesaTable.mesaId eq mesaId) and (SesionMesaTable.activo eq ACTIVE) }
            .orderBy(SesionMesaTable.fechaApertura to SortOrder.DESC)
            .limit(1)
            .singleOrNull()
            ?: return null
    return row.toSesionMesaResponse()
}

internal fun sesionesActivasByMesa(mesas: Set<Int>): Map<Int, SesionMesaResponse> {
    if (mesas.isEmpty()) return emptyMap()
    val usuarioById = usuariosById()
    return SesionMesaTable
        .selectAll()
        .where {
            (SesionMesaTable.mesaId inList mesas) and (SesionMesaTable.activo eq ACTIVE)
        }.map { it.toSesionMesaResponse(usuarioByCod = usuarioById) }
        .associateBy { it.mesaId }
}

internal fun usuariosById(): Map<Int, String> =
    UsersTable
        .selectAll()
        .map { it[UsersTable.codUsuario] to it[UsersTable.usuario] }
        .toMap()

internal fun sucursalDeCaja(cajaId: String): Int? =
    CajaTable
        .select(CajaTable.idSucursal, CajaTable.codEstatus)
        .where { CajaTable.idCaja eq cajaId }
        .limit(1)
        .singleOrNull()
        ?.let { row ->
            val activo = row[CajaTable.codEstatus] == ACTIVE
            if (activo) row[CajaTable.idSucursal] else null
        }
