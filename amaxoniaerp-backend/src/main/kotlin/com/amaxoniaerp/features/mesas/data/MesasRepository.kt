package com.amaxoniaerp.features.mesas.data

import com.amaxoniaerp.core.database.dbQuery
import com.amaxoniaerp.features.caja.data.CajaTable
import com.amaxoniaerp.features.caja.data.VendedorTable
import com.amaxoniaerp.features.mesas.domain.AreaResponse
import com.amaxoniaerp.features.mesas.domain.CajaScopeResult
import com.amaxoniaerp.features.mesas.domain.CajaSucursalScope
import com.amaxoniaerp.features.mesas.domain.Lienzo
import com.amaxoniaerp.features.mesas.domain.LienzoDefaults
import com.amaxoniaerp.features.mesas.domain.MesaResponse
import com.amaxoniaerp.features.mesas.domain.MesasPlan
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.count
import org.jetbrains.exposed.sql.selectAll

/**
 * Lectura de áreas (`plantas`) y mesas (`mesas`) para el POS.
 *
 * Regla de aislamiento: el cliente nunca envía `sucursal_id`. Envía la caja activa y el
 * servidor deriva la sucursal desde `caja.id_sucursal` tras comprobar que el usuario tiene
 * acceso a esa caja, con la misma regla que
 * [com.amaxoniaerp.features.caja.data.CajaRepository.getCajas].
 */
class MesasRepository {
    /**
     * Valida el acceso del usuario a [cajaId] y deriva su sucursal.
     *
     * El orden importa: primero se comprueba la existencia de la caja y después el acceso,
     * de modo que un id inventado y un id ajeno se distinguen en el log pero ambos terminan
     * sin datos para el llamante.
     */
    suspend fun resolveCajaScope(
        database: Database,
        userId: Int,
        cajaId: String,
    ): CajaScopeResult =
        dbQuery(database) {
            val cajaRow =
                CajaTable
                    .select(CajaTable.idCaja, CajaTable.idSucursal)
                    .where { CajaTable.idCaja eq cajaId }
                    .limit(1)
                    .singleOrNull()
                    ?: return@dbQuery CajaScopeResult.CajaNotFound

            if (!userCanAccessCaja(userId, cajaId)) {
                return@dbQuery CajaScopeResult.AccessDenied
            }

            val sucursalId =
                cajaRow[CajaTable.idSucursal]
                    ?: return@dbQuery CajaScopeResult.SucursalNotAssigned

            CajaScopeResult.Allowed(CajaSucursalScope(idCaja = cajaId, sucursalId = sucursalId))
        }

    /** Áreas activas de [sucursalId], ordenadas por `orden` y luego por `nombre`. */
    suspend fun listAreas(
        database: Database,
        sucursalId: Int,
    ): List<AreaResponse> =
        dbQuery(database) {
            val areas =
                PlantasTable
                    .selectAll()
                    .where { (PlantasTable.sucursalId eq sucursalId) and (PlantasTable.activo eq ACTIVE) }
                    .orderBy(
                        PlantasTable.orden to SortOrder.ASC_NULLS_LAST,
                        PlantasTable.nombre to SortOrder.ASC,
                        PlantasTable.id to SortOrder.ASC,
                    ).map { it.toAreaResponse() }

            if (areas.isEmpty()) {
                return@dbQuery emptyList()
            }

            val activeMesas = countActiveMesasByArea(areas.map { it.id })
            areas.map { area -> area.copy(cantidadMesasActivas = activeMesas[area.id] ?: 0) }
        }

    /**
     * Plan completo (lienzo + imagen + mesas activas) de [areaId], o `null` si el área no
     * existe, está inactiva o **no pertenece a [sucursalId]**. El llamante traduce ese `null`
     * a un 404 con el mismo mensaje en los tres casos, para no revelar por sondeo qué ids
     * existen en otras sucursales.
     *
     * La imagen de fondo del plano se toma de `plantas.imagen`: es lo que el administrativo
     * dibuja del salón. Llega al POS como `imagen_url` y, si es blank o nula, se omite y el
     * cliente pinta solo las mesas sobre un fondo neutro.
     */
    suspend fun listMesas(
        database: Database,
        sucursalId: Int,
        areaId: Int,
    ): MesasPlan? =
        dbQuery(database) {
            val areaRow =
                PlantasTable
                    .select(PlantasTable.id, PlantasTable.imagen)
                    .where {
                        (PlantasTable.id eq areaId) and
                            (PlantasTable.sucursalId eq sucursalId) and
                            (PlantasTable.activo eq ACTIVE)
                    }.limit(1)
                    .singleOrNull()
                    ?: return@dbQuery null

            val imagenUrl =
                areaRow[PlantasTable.imagen]
                    ?.trim()
                    ?.takeIf { it.isNotBlank() }

            val mesas =
                MesasTable
                    .selectAll()
                    .where { (MesasTable.plantaId eq areaId) and (MesasTable.activo eq ACTIVE) }
                    .orderBy(
                        MesasTable.codigo to SortOrder.ASC_NULLS_LAST,
                        MesasTable.nombre to SortOrder.ASC_NULLS_LAST,
                        MesasTable.id to SortOrder.ASC,
                    ).map { it.toMesaResponse() }

            MesasPlan(
                lienzo = Lienzo(LienzoDefaults.ANCHO_LIENZO, LienzoDefaults.ALTO_LIENZO),
                imagenUrl = imagenUrl,
                mesas = mesas,
            )
        }

    private fun countActiveMesasByArea(areaIds: List<Int>): Map<Int, Int> {
        val total = MesasTable.id.count()
        return MesasTable
            .select(MesasTable.plantaId, total)
            .where { (MesasTable.plantaId inList areaIds) and (MesasTable.activo eq ACTIVE) }
            .groupBy(MesasTable.plantaId)
            .associate { row -> row[MesasTable.plantaId] to row[total].toInt() }
    }

    /**
     * Misma regla permisiva que el listado de cajas: si el usuario no tiene ninguna caja
     * asignada vía `vendedor`, se le permiten todas. Endurecerlo cambiaría accesos existentes.
     */
    private fun userCanAccessCaja(
        userId: Int,
        cajaId: String,
    ): Boolean {
        val userToken = userId.toString()
        val assignedCajaIds =
            VendedorTable
                .select(VendedorTable.codUsuarios, VendedorTable.idCajas)
                .where { VendedorTable.activo eq ACTIVE }
                .filter { csvContains(it[VendedorTable.codUsuarios], userToken) }
                .flatMap { csvTokens(it[VendedorTable.idCajas]) }
                .toSet()

        return assignedCajaIds.isEmpty() || cajaId in assignedCajaIds
    }

    private fun csvContains(
        csv: String?,
        token: String,
    ): Boolean {
        if (csv.isNullOrBlank()) return false
        return csv.split(',').any { it.trim() == token }
    }

    private fun csvTokens(csv: String?): List<String> {
        if (csv.isNullOrBlank()) return emptyList()
        return csv.split(',').mapNotNull { it.trim().takeIf(String::isNotBlank) }
    }

    private fun ResultRow.toAreaResponse(): AreaResponse {
        val id = this[PlantasTable.id]
        return AreaResponse(
            id = id,
            nombre = this[PlantasTable.nombre].trim().ifBlank { "Área $id" },
            descripcion = this[PlantasTable.descripcion]?.trim()?.takeIf(String::isNotBlank),
            imagen = this[PlantasTable.imagen]?.trim()?.takeIf(String::isNotBlank),
            orden = this[PlantasTable.orden] ?: 0,
            activo = this[PlantasTable.activo] == ACTIVE,
            // Se rellena en listAreas con un único conteo agrupado, no por área.
            cantidadMesasActivas = 0,
        )
    }

    private fun ResultRow.toMesaResponse(): MesaResponse {
        val id = this[MesasTable.id]
        val codigo = this[MesasTable.codigo]?.trim()?.takeIf(String::isNotBlank)
        val nombre = this[MesasTable.nombre]?.trim()?.takeIf(String::isNotBlank)
        return MesaResponse(
            id = id,
            areaId = this[MesasTable.plantaId],
            codigo = codigo,
            nombre = nombre ?: codigo ?: "Mesa $id",
            capacidad = this[MesasTable.capacidad] ?: 0,
            forma = this[MesasTable.forma]?.trim()?.takeIf(String::isNotBlank),
            posicionX = this[MesasTable.posicionX]?.toDouble() ?: 0.0,
            posicionY = this[MesasTable.posicionY]?.toDouble() ?: 0.0,
            ancho = this[MesasTable.ancho]?.toDouble() ?: 0.0,
            alto = this[MesasTable.alto]?.toDouble() ?: 0.0,
            rotacion = this[MesasTable.rotacion]?.toDouble() ?: 0.0,
            activo = this[MesasTable.activo] == ACTIVE,
        )
    }

    private companion object {
        const val ACTIVE = 1
    }
}
