package com.amaxoniaerp.features.caja.data

import com.amaxoniaerp.features.caja.domain.CajaSecuencia
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.selectAll
import java.time.format.DateTimeFormatter

/**
 * Primitivas de sesión de caja con alcance de transacción: cada función
 * asume que ya existe una transacción activa y no abre ninguna propia.
 * Los métodos públicos de [CajaRepository] las ejecutan dentro de su fase
 * dbQuery; el workflow de sesión de caja las compondrá bajo fases propias
 * en la capa application.
 *
 * Filas de secuencias abiertas para una caja, más reciente primero (máx. 2).
 */
internal fun findOpenSecuenciaRows(idCaja: String): List<ResultRow> =
    CajaSecuenciaTable
        .selectAll()
        .where { (CajaSecuenciaTable.idCaja eq idCaja) and (CajaSecuenciaTable.fechaCierre.isNull()) }
        .orderBy(CajaSecuenciaTable.fechaApertura to SortOrder.DESC)
        .limit(2)
        .toList()

/** Mapeo verbatim de fila de secuencia abierta a dominio (formato y defaults vigentes). */
internal fun mapOpenSecuenciaRow(row: ResultRow): CajaSecuencia =
    CajaSecuencia(
        idCajaSecuencia = row[CajaSecuenciaTable.idCajaSecuencia],
        idCaja = row[CajaSecuenciaTable.idCaja],
        fechaApertura =
            row[CajaSecuenciaTable.fechaApertura]?.format(
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
            ) ?: "",
        montoApertura = row[CajaSecuenciaTable.montoEfectivoApertura].toDouble(),
        fechaCierre =
            row[CajaSecuenciaTable.fechaCierre]?.format(
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
            ),
        montoCierre = row[CajaSecuenciaTable.montoEfectivoCierre]?.toDouble(),
        estatus = if (row[CajaSecuenciaTable.fechaCierre] == null) 1 else 0,
        usuarioApertura = row[CajaSecuenciaTable.usuario] ?: "",
        usuarioCierre = null,
        serieSucursal = row[CajaSecuenciaTable.serieSucursal],
        idSucursal = 1, // default since it was removed
    )

/** Datos mínimos de guarda para cerrar una secuencia: estado y serie sucursal. */
internal data class CajaSecuenciaGuard(
    val cerrada: Boolean,
    val serieSucursal: String,
)

/** Lectura de guarda de la secuencia a cerrar; null si no existe. */
internal fun findSecuenciaGuard(idSecuencia: String): CajaSecuenciaGuard? {
    val row =
        CajaSecuenciaTable
            .selectAll()
            .where { CajaSecuenciaTable.idCajaSecuencia eq idSecuencia }
            .limit(1)
            .firstOrNull()
            ?: return null

    return CajaSecuenciaGuard(
        cerrada = row[CajaSecuenciaTable.fechaCierre] != null,
        serieSucursal = row[CajaSecuenciaTable.serieSucursal],
    )
}
