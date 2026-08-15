package com.amaxoniaerp.features.pos.data

import com.amaxoniaerp.core.database.dbQuery
import com.amaxoniaerp.features.pos.domain.FormaPagoItem
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList

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
}
