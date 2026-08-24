package com.amaxoniaerp.features.caja.data

import com.amaxoniaerp.features.caja.domain.CajaCierreSummary
import com.amaxoniaerp.features.caja.domain.CajaFormaPagoTotal
import com.amaxoniaerp.features.caja.domain.CajaSecuencia
import com.amaxoniaerp.features.facturas.data.EstatusTable
import com.amaxoniaerp.features.pos.data.CajaFormaPagoTable
import com.amaxoniaerp.features.sales.data.CajaIngresoEgreso
import com.amaxoniaerp.features.sales.data.CajaStatus
import com.amaxoniaerp.features.sales.data.SalesCajaNuevaDetalleFormaPagoTable
import com.amaxoniaerp.features.sales.data.SalesCajaNuevaTableFactory
import com.amaxoniaerp.features.sales.data.SalesFacturaTableFactory
import org.jetbrains.exposed.sql.JoinType
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.leftJoin
import org.jetbrains.exposed.sql.select
import java.math.RoundingMode

internal class CajaHeaderNames(
    val cajaName: String,
    val vendedorName: String?,
)

internal fun loadCajaHeaderNames(
    idCaja: String,
    idCajaSecuencia: String,
): CajaHeaderNames {
    val cajaName =
        CajaTable
            .select(CajaTable.descripcion)
            .where { CajaTable.idCaja eq idCaja }
            .limit(1)
            .firstOrNull()
            ?.get(CajaTable.descripcion)
            ?.takeIf { it.isNotBlank() }
            ?: "Caja"

    val vendedorName =
        CajaSecuenciaTable
            .leftJoin(VendedorTable, { idVendedor }, { VendedorTable.idVendedor })
            .select(VendedorTable.nombre)
            .where { CajaSecuenciaTable.idCajaSecuencia eq idCajaSecuencia }
            .limit(1)
            .firstOrNull()
            ?.get(VendedorTable.nombre)
            ?.takeIf { it.isNotBlank() }

    return CajaHeaderNames(cajaName, vendedorName)
}

/** (totalVentas, cantidadTransacciones) excluyendo facturas anuladas. */
internal fun loadSalesTotals(
    countryCode: String,
    idCajaSecuencia: String,
): Pair<Double, Int> {
    val facturaTable = SalesFacturaTableFactory.forCountry(countryCode)
    val facturaRows =
        facturaTable
            .leftJoin(EstatusTable, { codEstatus }, { EstatusTable.codEstatus })
            .select(
                facturaTable.idFactura,
                facturaTable.totalTotalFactura,
                EstatusTable.descripcion,
            ).where { facturaTable.idCajaSecuencia eq idCajaSecuencia }
            .toList()

    var totalSales = 0.0
    var transactionCount = 0
    facturaRows.forEach { row ->
        val statusDesc = row[EstatusTable.descripcion]
        if (!isCancelledStatus(statusDesc)) {
            totalSales += row[facturaTable.totalTotalFactura].toDouble()
            transactionCount += 1
        }
    }
    return totalSales to transactionCount
}

internal class MovimientoTotals(
    val totalIncome: Double,
    val totalExpense: Double,
    val totalCancelled: Double,
)

internal fun loadMovimientoTotals(
    countryCode: String,
    idCajaSecuencia: String,
): MovimientoTotals {
    val cajaNuevaTable = SalesCajaNuevaTableFactory.forCountry(countryCode)
    val movimientos =
        cajaNuevaTable
            .select(
                cajaNuevaTable.ingEg,
                cajaNuevaTable.monto,
                cajaNuevaTable.status,
            ).where { cajaNuevaTable.idCajaSecuencia eq idCajaSecuencia }
            .toList()

    var totalIncome = 0.0
    var totalExpense = 0.0
    var totalCancelled = 0.0
    movimientos.forEach { row ->
        val amount = row[cajaNuevaTable.monto]?.toDouble() ?: 0.0
        when (row[cajaNuevaTable.status]) {
            CajaStatus.Anulada -> totalCancelled += amount
            else ->
                when (row[cajaNuevaTable.ingEg]) {
                    CajaIngresoEgreso.I -> totalIncome += amount
                    CajaIngresoEgreso.E -> totalExpense += amount
                    null -> Unit
                }
        }
    }
    return MovimientoTotals(totalIncome, totalExpense, totalCancelled)
}

internal class FormaPagoBreakdown(
    val totalCash: Double,
    val totalCard: Double,
    val totalOther: Double,
    val formasPago: List<CajaFormaPagoTotal>,
)

private class FormaAccum(
    val idFormaPago: Int?,
    val siglas: String?,
    val descripcion: String?,
    var total: Double,
)

private data class FormaItemMetadata(
    val idFormaPago: Int?,
    val siglas: String?,
    val descripcion: String?,
    val tipoMovimiento: String?,
)

internal fun loadFormaPagoBreakdown(
    countryCode: String,
    idCajaSecuencia: String,
): FormaPagoBreakdown {
    val cajaNuevaTable = SalesCajaNuevaTableFactory.forCountry(countryCode)
    val formasCatalogo = loadFormasCatalogo()

    val formaRows =
        SalesCajaNuevaDetalleFormaPagoTable
            .join(
                cajaNuevaTable,
                JoinType.INNER,
                onColumn = SalesCajaNuevaDetalleFormaPagoTable.cajaId,
                otherColumn = cajaNuevaTable.cajaId,
            ).select(
                SalesCajaNuevaDetalleFormaPagoTable.idFormaPago,
                SalesCajaNuevaDetalleFormaPagoTable.tipoMovimiento,
                SalesCajaNuevaDetalleFormaPagoTable.monto,
            ).where {
                (cajaNuevaTable.idCajaSecuencia eq idCajaSecuencia) and
                    (cajaNuevaTable.status neq CajaStatus.Anulada)
            }.toList()

    val formasMap = linkedMapOf<String, FormaAccum>()
    var totalCash = 0.0
    var totalCard = 0.0
    var totalOther = 0.0

    formaRows.forEach { row ->
        val amount = row[SalesCajaNuevaDetalleFormaPagoTable.monto]?.toDouble() ?: 0.0
        if (amount == 0.0) return@forEach

        val tipoMovimiento = row[SalesCajaNuevaDetalleFormaPagoTable.tipoMovimiento]
        val idFormaPago = row[SalesCajaNuevaDetalleFormaPagoTable.idFormaPago]
        val metadata = idFormaPago?.let { formasCatalogo[it] }
        val siglas = metadata?.first
        val descripcion = metadata?.second

        when (classifyPaymentCategory(tipoMovimiento, siglas, descripcion)) {
            PaymentCategory.CASH -> totalCash += amount
            PaymentCategory.CARD -> totalCard += amount
            PaymentCategory.OTHER -> totalOther += amount
        }

        accumulateFormaRow(
            formasMap,
            FormaItemMetadata(idFormaPago, siglas, descripcion, tipoMovimiento),
            amount,
        )
    }

    return FormaPagoBreakdown(
        totalCash = totalCash,
        totalCard = totalCard,
        totalOther = totalOther,
        formasPago =
            formasMap.values.map {
                CajaFormaPagoTotal(
                    idFormaPago = it.idFormaPago,
                    siglas = it.siglas,
                    descripcion = it.descripcion,
                    total = it.total,
                )
            },
    )
}

private fun loadFormasCatalogo(): Map<Int, Pair<String?, String?>> =
    CajaFormaPagoTable
        .select(
            CajaFormaPagoTable.idFormaPago,
            CajaFormaPagoTable.siglas,
            CajaFormaPagoTable.descripcion,
        ).toList()
        .associate { row ->
            row[CajaFormaPagoTable.idFormaPago] to
                (row[CajaFormaPagoTable.siglas] to row[CajaFormaPagoTable.descripcion])
        }

private fun accumulateFormaRow(
    formasMap: MutableMap<String, FormaAccum>,
    meta: FormaItemMetadata,
    amount: Double,
) {
    val formKey =
        meta.idFormaPago?.toString()
            ?: listOf(meta.siglas.orEmpty(), meta.descripcion.orEmpty(), meta.tipoMovimiento.orEmpty())
                .joinToString("|")

    val current = formasMap[formKey]
    if (current == null) {
        formasMap[formKey] =
            FormaAccum(
                idFormaPago = meta.idFormaPago,
                siglas = meta.siglas ?: meta.tipoMovimiento,
                descripcion = meta.descripcion,
                total = amount,
            )
    } else {
        current.total += amount
    }
}

internal fun buildCajaCierreSummary(
    secuencia: CajaSecuencia,
    names: CajaHeaderNames,
    sales: Pair<Double, Int>,
    movimientos: MovimientoTotals,
    formas: FormaPagoBreakdown,
): CajaCierreSummary {
    val open = secuencia.montoApertura.toMoney()
    val income = movimientos.totalIncome.toMoney()
    val expense = movimientos.totalExpense.toMoney()
    val cancelled = movimientos.totalCancelled.toMoney()
    val expectedClose = (open + income - expense - cancelled).setScale(2, RoundingMode.HALF_UP).toDouble()

    return CajaCierreSummary(
        idCajaSecuencia = secuencia.idCajaSecuencia,
        idCaja = secuencia.idCaja,
        cajaName = names.cajaName,
        vendedorName = names.vendedorName,
        openedAt = secuencia.fechaApertura,
        openAmount = secuencia.montoApertura,
        totalSales = sales.first,
        transactionCount = sales.second,
        totalCash = formas.totalCash,
        totalCard = formas.totalCard,
        totalOther = formas.totalOther,
        totalIncome = movimientos.totalIncome,
        totalExpense = movimientos.totalExpense,
        totalCancelled = movimientos.totalCancelled,
        expectedClose = expectedClose,
        formasPago = formas.formasPago,
    )
}
