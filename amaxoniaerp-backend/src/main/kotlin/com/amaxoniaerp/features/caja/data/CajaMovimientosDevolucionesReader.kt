package com.amaxoniaerp.features.caja.data

import com.amaxoniaerp.features.caja.domain.CajaFormaPagoDevolucionItem
import com.amaxoniaerp.features.caja.domain.CajaFormaPagoItem
import com.amaxoniaerp.features.pos.data.CajaFormaPagoTable
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.selectAll

internal fun loadMovimientoTotal(
    idSecuencia: String,
    tipo: String,
): Double =
    runCatching {
        CajaMovimientoTable
            .select(CajaMovimientoTable.total)
            .where {
                (CajaMovimientoTable.idSecuencia eq idSecuencia) and
                    (CajaMovimientoTable.tipo eq tipo)
            }.sumOf { it[CajaMovimientoTable.total].toDouble() }
    }.getOrDefault(0.0)

internal fun appendEntradasSalidasItems(
    formaPagoItems: MutableList<CajaFormaPagoItem>,
    montoEntrada: Double,
    montoSalida: Double,
) {
    if (montoEntrada != 0.0) {
        formaPagoItems +=
            CajaFormaPagoItem(
                id = -100,
                formaPago = "ENTRADAS",
                siglas = "E",
                monto = montoEntrada,
                estatus = 1,
            )
    }
    if (montoSalida != 0.0) {
        formaPagoItems +=
            CajaFormaPagoItem(
                id = -101,
                formaPago = "SALIDAS",
                siglas = "S",
                monto = montoSalida,
                estatus = 1,
            )
    }
}

internal fun loadFormaPagoDevolucion(idSecuencia: String): List<CajaFormaPagoDevolucionItem> =
    runCatching {
        val devolucionRows =
            FacturaDevolucionTable
                .selectAll()
                .where { FacturaDevolucionTable.idCajaSecuencia eq idSecuencia }
                .toList()

        val devolucionesPorForma =
            devolucionRows
                .groupBy { it[FacturaDevolucionTable.idFormaPago] ?: RETURN_PAYMENT_FORM_FALLBACK }
                .mapValues { (_, rows) ->
                    rows.sumOf { row ->
                        row[FacturaDevolucionTable.totalTotalFactura]?.toDouble() ?: 0.0
                    }
                }

        if (devolucionesPorForma.isEmpty()) {
            emptyList()
        } else {
            val ids = devolucionesPorForma.keys.toList()
            val meta =
                CajaFormaPagoTable
                    .select(
                        CajaFormaPagoTable.idFormaPago,
                        CajaFormaPagoTable.siglas,
                        CajaFormaPagoTable.descripcion,
                    ).where { CajaFormaPagoTable.idFormaPago inList ids }
                    .associateBy { it[CajaFormaPagoTable.idFormaPago] }

            devolucionesPorForma.map { (idForma, monto) ->
                val row = meta[idForma]
                CajaFormaPagoDevolucionItem(
                    idFormaPago = idForma,
                    siglas = row?.get(CajaFormaPagoTable.siglas) ?: if (idForma == 30) "NC" else null,
                    descripcion =
                        row?.get(CajaFormaPagoTable.descripcion) ?: if (idForma ==
                            30
                        ) {
                            "NOTA DE CREDITO"
                        } else {
                            null
                        },
                    monto = monto,
                )
            }
        }
    }.getOrDefault(emptyList())

internal fun applyDevolucionesToFormaPago(
    formaPagoItems: MutableList<CajaFormaPagoItem>,
    formaPagoDevolucion: List<CajaFormaPagoDevolucionItem>,
) {
    formaPagoDevolucion.forEach { devolucion ->
        val index = formaPagoItems.indexOfFirst { it.id == devolucion.idFormaPago }
        if (index >= 0) {
            val current = formaPagoItems[index]
            formaPagoItems[index] = current.copy(monto = current.monto + devolucion.monto)
        } else {
            formaPagoItems +=
                CajaFormaPagoItem(
                    id = devolucion.idFormaPago,
                    formaPago = devolucion.descripcion ?: "NOTA DE CREDITO",
                    siglas = devolucion.siglas ?: "NC",
                    estatus = 1,
                    monto = devolucion.monto,
                )
        }
    }
}
