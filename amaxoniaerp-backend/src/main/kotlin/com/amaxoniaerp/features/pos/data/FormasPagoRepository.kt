package com.amaxoniaerp.features.pos.data

import com.amaxoniaerp.core.database.dbQuery
import com.amaxoniaerp.features.caja.data.MonedaDenominacionTable
import com.amaxoniaerp.features.pos.domain.FormaPagoItem
import com.amaxoniaerp.features.pos.domain.MonedaDenominacionDto
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.andWhere
import org.jetbrains.exposed.sql.leftJoin
import org.jetbrains.exposed.sql.selectAll

class FormasPagoRepository {
    suspend fun listFormasPago(
        database: Database,
        cajaId: String?,
        tipoRegistro: List<Int>,
    ): List<FormaPagoItem> =
        dbQuery(database) {
            if (cajaId.isNullOrBlank()) {
                val query =
                    CajaFormaPagoTable
                        .selectAll()
                        .where {
                            (CajaFormaPagoTable.pos eq 1) and
                                (CajaFormaPagoTable.activo eq 1)
                        }

                if (tipoRegistro.isNotEmpty()) {
                    query.andWhere { CajaFormaPagoTable.idCajaTpRegistro inList tipoRegistro }
                }

                query.orderBy(
                    CajaFormaPagoTable.orden to SortOrder.ASC,
                    CajaFormaPagoTable.codigo to SortOrder.ASC,
                )

                query.map { row -> mapRow(row, null) }
            } else {
                val query =
                    CajaFormaTable
                        .leftJoin(
                            otherTable = CajaFormaPagoTable,
                            onColumn = { idFormaPago },
                            otherColumn = { CajaFormaPagoTable.idFormaPago },
                        ).selectAll()
                        .where {
                            (CajaFormaTable.idCaja eq cajaId) and
                                (CajaFormaTable.activo eq 1) and
                                (CajaFormaPagoTable.pos eq 1) and
                                (CajaFormaPagoTable.activo eq 1)
                        }

                if (tipoRegistro.isNotEmpty()) {
                    query.andWhere { CajaFormaPagoTable.idCajaTpRegistro inList tipoRegistro }
                }

                query.orderBy(
                    CajaFormaPagoTable.orden to SortOrder.ASC,
                    CajaFormaPagoTable.codigo to SortOrder.ASC,
                )

                query.map { row -> mapRow(row, row[CajaFormaTable.idCaja]) }
            }
        }

    private fun mapRow(
        row: ResultRow,
        idCaja: String?,
    ): FormaPagoItem =
        FormaPagoItem(
            idFormaPago = row[CajaFormaPagoTable.idFormaPago],
            siglas = row[CajaFormaPagoTable.siglas],
            codigo = row[CajaFormaPagoTable.codigo]?.toString(),
            descripcion = row[CajaFormaPagoTable.descripcion],
            idCajaTpConcepto = row[CajaFormaPagoTable.idCajaTpConcepto],
            idCajaTpRegistro = row[CajaFormaPagoTable.idCajaTpRegistro],
            cuentaContable = row[CajaFormaPagoTable.cuentaContable],
            formaPagoFact = row[CajaFormaPagoTable.formaPagoFact],
            activo = row[CajaFormaPagoTable.activo],
            pos = row[CajaFormaPagoTable.pos],
            imagen = row[CajaFormaPagoTable.imagen].takeIf { it.isNotBlank() },
            grupo = row[CajaFormaPagoTable.grupo],
            orden = row[CajaFormaPagoTable.orden],
            idBancoCuenta = row[CajaFormaPagoTable.idBancoCuenta].takeIf { it > 0 },
            idBancoOperacion = row[CajaFormaPagoTable.idBancoOperacion].takeIf { it > 0 },
            tipoMoneda = row.getOrNull(CajaFormaPagoTable.tipoMoneda).orEmpty(),
            idCaja = idCaja,
        )

    suspend fun listDenominaciones(database: Database): List<MonedaDenominacionDto> =
        dbQuery(database) {
            MonedaDenominacionTable
                .selectAll()
                .where { MonedaDenominacionTable.activo eq 1 }
                .orderBy(MonedaDenominacionTable.valor to SortOrder.DESC, MonedaDenominacionTable.id to SortOrder.ASC)
                .map { row ->
                    val valor = row[MonedaDenominacionTable.valor].toDouble()
                    val rollo = row[MonedaDenominacionTable.rollo]
                    MonedaDenominacionDto(
                        id = row[MonedaDenominacionTable.id],
                        idDivisa = row[MonedaDenominacionTable.idDivisa],
                        denominacion = row[MonedaDenominacionTable.denominacion].orEmpty(),
                        valor = valor,
                        rollo = rollo,
                        esMoneda = rollo > 0 || valor < 1.0,
                        imagen = row[MonedaDenominacionTable.imagen]?.takeIf { it.isNotBlank() },
                    )
                }
        }
}
