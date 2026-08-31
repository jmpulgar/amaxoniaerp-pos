package com.amaxoniaerp.features.electronicinvoice.data

import com.amaxoniaerp.features.electronicinvoice.domain.FEFormaPagoData
import com.amaxoniaerp.features.electronicinvoice.domain.FERetencionData
import com.amaxoniaerp.features.pos.data.CajaFormaPagoTable
import org.jetbrains.exposed.sql.JoinType
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.slf4j.LoggerFactory

private val paymentReadersLog = LoggerFactory.getLogger("ElectronicInvoicePaymentReaders")

internal fun loadRetencion(invoiceId: String): FERetencionData? =
    run {
        val safeInvoiceId = invoiceId.replace("'", "''")
        val result =
            TransactionManager.current().exec(
                """
                SELECT codigo_retencion, totalizar_monto_retencion
                FROM factura_detalle_formapago
                WHERE id_factura = '$safeInvoiceId'
                LIMIT 1
                """.trimIndent(),
            ) { rs ->
                if (!rs.next()) return@exec null
                val codigo = rs.getString("codigo_retencion").safeIntOrZero()
                val monto = rs.getString("totalizar_monto_retencion").safeDoubleOrZero()
                codigo to monto
            } ?: return null

        val (codigo, monto) = result
        if (codigo == 0 || monto <= 0.0) return null

        return FERetencionData(
            codigoRetencion = codigo.toString(),
            montoRetencion = monto,
        )
    }

internal fun loadFormasPago(invoiceId: String): List<FEFormaPagoData> {
    // Buscar el caja_id asociado a esta factura en caja_nueva
    val cajaRow =
        FECajaNuevaReadTable
            .selectAll()
            .where { FECajaNuevaReadTable.idFactura eq invoiceId }
            .limit(1)
            .firstOrNull()

    if (cajaRow == null) {
        paymentReadersLog.warn("No se encontró registro en caja_nueva para factura {}", invoiceId)
        return emptyList()
    }

    val cajaId = cajaRow[FECajaNuevaReadTable.cajaId]

    // JOIN caja_nueva_detalle con caja_forma_pago para obtener siglas y formaPagoFact
    return FECajaNuevaDetalleReadTable
        .join(
            CajaFormaPagoTable,
            JoinType.LEFT,
            onColumn = FECajaNuevaDetalleReadTable.idFormaPago,
            otherColumn = CajaFormaPagoTable.idFormaPago,
        ).selectAll()
        .where { FECajaNuevaDetalleReadTable.cajaId eq cajaId }
        .mapNotNull { row ->
            val montoDetalle = row[FECajaNuevaDetalleReadTable.monto]?.toDouble() ?: 0.0
            val montoOriginal = row[FECajaNuevaDetalleReadTable.montoOriginal]?.toDouble() ?: 0.0
            val monto = if (montoDetalle > 0.0) montoDetalle else montoOriginal
            if (monto <= 0.0) return@mapNotNull null

            val siglas = row.getOrNull(CajaFormaPagoTable.siglas)
            val formaPagoFact = row.getOrNull(CajaFormaPagoTable.formaPagoFact)
            val descripcion = row.getOrNull(CajaFormaPagoTable.descripcion) ?: "Pago"

            FEFormaPagoData(
                siglas = siglas,
                formaPagoFact = formaPagoFact,
                descripcion = descripcion,
                monto = monto,
                esCash = siglas?.uppercase()?.trim() in setOf("EF", "CASH", "EFECTIVO"),
            )
        }
}

internal fun loadVuelto(invoiceId: String): Double? =
    FEFacturaDetalleFormaPagoReadTable
        .select(FEFacturaDetalleFormaPagoReadTable.totalizarCambio)
        .where { FEFacturaDetalleFormaPagoReadTable.idFactura eq invoiceId }
        .limit(1)
        .firstOrNull()
        ?.get(FEFacturaDetalleFormaPagoReadTable.totalizarCambio)
        ?.toDouble()
        ?.takeIf { it > 0 }

internal fun loadMontoCancelar(invoiceId: String): Double? =
    FEFacturaDetalleFormaPagoReadTable
        .select(FEFacturaDetalleFormaPagoReadTable.totalizarMontoCancelar)
        .where { FEFacturaDetalleFormaPagoReadTable.idFactura eq invoiceId }
        .limit(1)
        .firstOrNull()
        ?.get(FEFacturaDetalleFormaPagoReadTable.totalizarMontoCancelar)
        ?.toDouble()
        ?.takeIf { it > 0 }

internal fun formatFechaRecepcion(isoDate: String): String =
    try {
        isoDate
            .substringBefore("-05:00")
            .substringBefore("-04:00")
            .replace("T", " ")
            .take(SQL_DATETIME_TEXT_LENGTH)
    } catch (_: Exception) {
        isoDate.take(SQL_DATETIME_TEXT_LENGTH).replace("T", " ")
    }
