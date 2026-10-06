package com.amaxoniaerp.features.sucursales.data

import com.amaxoniaerp.core.database.dbQuery
import com.amaxoniaerp.features.caja.data.SucursalAlmacenTable
import com.amaxoniaerp.features.caja.data.SucursalTable
import com.amaxoniaerp.features.sucursales.domain.SaveSucursalRequest
import com.amaxoniaerp.features.sucursales.domain.SucursalDto
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import org.slf4j.LoggerFactory

class SucursalRepository {
    private val log = LoggerFactory.getLogger(SucursalRepository::class.java)

    suspend fun listSucursales(database: Database): List<SucursalDto> =
        dbQuery(database) {
            val defaultWarehouseMap =
                SucursalAlmacenTable
                    .select(SucursalAlmacenTable.idSucursal, SucursalAlmacenTable.idAlmacen)
                    .where { SucursalAlmacenTable.defaultVentas eq 1 }
                    .associate { it[SucursalAlmacenTable.idSucursal] to it[SucursalAlmacenTable.idAlmacen] }

            SucursalTable.selectAll().map { row ->
                val id = row[SucursalTable.idSucursal]
                SucursalDto(
                    id = id,
                    codigo = row[SucursalTable.codigo],
                    serie = row[SucursalTable.serie],
                    codigoSucursalEmisor = row[SucursalTable.codigoSucursalEmisor],
                    sucursal = row[SucursalTable.sucursal],
                    descripcion = row[SucursalTable.descripcion],
                    defaultWarehouseId = defaultWarehouseMap[id],
                )
            }
        }

    suspend fun getSucursalById(
        database: Database,
        id: Int,
    ): SucursalDto? =
        dbQuery(database) {
            val row =
                SucursalTable
                    .selectAll()
                    .where { SucursalTable.idSucursal eq id }
                    .singleOrNull() ?: return@dbQuery null

            val defaultWarehouse =
                SucursalAlmacenTable
                    .select(SucursalAlmacenTable.idAlmacen)
                    .where { (SucursalAlmacenTable.idSucursal eq id) and (SucursalAlmacenTable.defaultVentas eq 1) }
                    .singleOrNull()
                    ?.get(SucursalAlmacenTable.idAlmacen)

            SucursalDto(
                id = row[SucursalTable.idSucursal],
                codigo = row[SucursalTable.codigo],
                serie = row[SucursalTable.serie],
                codigoSucursalEmisor = row[SucursalTable.codigoSucursalEmisor],
                sucursal = row[SucursalTable.sucursal],
                descripcion = row[SucursalTable.descripcion],
                defaultWarehouseId = defaultWarehouse,
            )
        }

    suspend fun createSucursal(
        database: Database,
        request: SaveSucursalRequest,
    ): SucursalDto =
        dbQuery(database) {
            val insertedId =
                SucursalTable.insert {
                    it[codigo] = request.codigo
                    it[serie] = request.serie
                    it[codigoSucursalEmisor] = request.codigoSucursalEmisor
                    it[sucursal] = request.sucursal
                    it[descripcion] = request.descripcion
                }[SucursalTable.idSucursal]

            if (request.defaultWarehouseId != null) {
                SucursalAlmacenTable.deleteWhere { SucursalAlmacenTable.idSucursal eq insertedId }
                SucursalAlmacenTable.insert {
                    it[idSucursal] = insertedId
                    it[idAlmacen] = request.defaultWarehouseId
                    it[defaultVentas] = 1
                }
            }

            log.info("Sucursal creada con éxito: id={} nombre={}", insertedId, request.sucursal)
            SucursalDto(
                id = insertedId,
                codigo = request.codigo,
                serie = request.serie,
                codigoSucursalEmisor = request.codigoSucursalEmisor,
                sucursal = request.sucursal,
                descripcion = request.descripcion,
                defaultWarehouseId = request.defaultWarehouseId,
            )
        }

    suspend fun updateSucursal(
        database: Database,
        id: Int,
        request: SaveSucursalRequest,
    ): SucursalDto? =
        dbQuery(database) {
            val updatedRows =
                SucursalTable.update({ SucursalTable.idSucursal eq id }) {
                    it[codigo] = request.codigo
                    it[serie] = request.serie
                    it[codigoSucursalEmisor] = request.codigoSucursalEmisor
                    it[sucursal] = request.sucursal
                    it[descripcion] = request.descripcion
                }

            if (updatedRows == 0) {
                return@dbQuery null
            }

            if (request.defaultWarehouseId != null) {
                SucursalAlmacenTable.deleteWhere { SucursalAlmacenTable.idSucursal eq id }
                SucursalAlmacenTable.insert {
                    it[idSucursal] = id
                    it[idAlmacen] = request.defaultWarehouseId
                    it[defaultVentas] = 1
                }
            }

            log.info("Sucursal actualizada con éxito: id={} nombre={}", id, request.sucursal)
            SucursalDto(
                id = id,
                codigo = request.codigo,
                serie = request.serie,
                codigoSucursalEmisor = request.codigoSucursalEmisor,
                sucursal = request.sucursal,
                descripcion = request.descripcion,
                defaultWarehouseId = request.defaultWarehouseId,
            )
        }
}
