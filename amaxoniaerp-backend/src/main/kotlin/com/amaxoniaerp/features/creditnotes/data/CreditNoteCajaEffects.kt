package com.amaxoniaerp.features.creditnotes.data

import com.amaxoniaerp.features.items.data.FacturaDetalleProductoLoteTable
import com.amaxoniaerp.features.items.data.ItemExistenciaAlmacenTable
import com.amaxoniaerp.features.items.data.ItemLoteTable
import com.amaxoniaerp.features.sales.data.CajaStatus
import com.amaxoniaerp.features.sales.data.SalesCajaNuevaDetalleTableFactory
import com.amaxoniaerp.features.sales.data.SalesCajaNuevaDetalleTableVE
import com.amaxoniaerp.features.sales.data.SalesCajaNuevaReciboTableFactory
import com.amaxoniaerp.features.sales.data.SalesCajaNuevaTableFactory
import com.amaxoniaerp.features.sales.data.SalesKardexDetalleTableFactory
import com.amaxoniaerp.features.sales.data.SalesKardexDetalleTablePA
import com.amaxoniaerp.features.sales.data.SalesKardexTableFactory
import com.amaxoniaerp.features.sales.data.SalesKardexTablePA
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.insertIgnore
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

/** Comando de registro de NC parcial sobre la caja de la factura original. */
internal data class PartialCashRegistration(
    val countryCode: String,
    val invoice: InvoiceHeader,
    val creditNoteId: String,
    val creditNoteCode: String,
    val total: BigDecimal,
    val paymentFormId: Int,
    val username: String,
    val now: LocalDateTime,
)

/** Comando de restauraciÃ³n de inventario por devoluciÃ³n. */
internal data class InventoryRestore(
    val countryCode: String,
    val invoice: InvoiceHeader,
    val creditNoteId: String,
    val creditNoteCode: String,
    val lines: List<ProcessedLine>,
    val username: String,
    val date: LocalDate,
    val now: LocalDateTime,
    val idSucursal: Int,
)

/** Comando de egreso de caja por reintegro. */
internal data class RefundEgress(
    val countryCode: String,
    val invoice: InvoiceHeader,
    val creditNoteId: String,
    val creditNoteCode: String,
    val total: BigDecimal,
    val idFormaPago: Int,
    val username: String,
    val now: LocalDateTime,
    val date: LocalDate,
    val cajaContext: CajaContext,
)

/** Comando de abono a favor del cliente. */
internal data class AbonoRegistration(
    val creditNoteId: String,
    val total: BigDecimal,
    val invoice: InvoiceHeader,
    val client: ClientContext,
    val username: String,
    val now: LocalDateTime,
    val cajaContext: CajaContext,
)

/** Comando de certificado de regalo. */
internal data class GiftCertificateRegistration(
    val creditNoteId: String,
    val total: BigDecimal,
    val client: ClientContext,
    val username: String,
    val now: LocalDateTime,
    val cajaContext: CajaContext,
)

internal fun cancelInvoiceAndOriginalCash(
    countryCode: String,
    invoiceId: String,
    username: String,
    date: LocalDate,
    now: LocalDateTime,
) {
    CreditNoteFacturaTable.update({ CreditNoteFacturaTable.idFactura eq invoiceId }) {
        it[codEstatus] = ANNULLED_INVOICE_STATUS
    }

    val cajaNuevaTable = SalesCajaNuevaTableFactory.forCountry(countryCode)
    val cajaNuevaDetalleTable = SalesCajaNuevaDetalleTableFactory.forCountry(countryCode)
    val originalCajas =
        cajaNuevaTable
            .selectAll()
            .where { cajaNuevaTable.idFactura eq invoiceId }
            .toList()

    originalCajas.forEach { cajaRow ->
        val cajaId = cajaRow[cajaNuevaTable.cajaId]
        cajaNuevaTable.update({ cajaNuevaTable.cajaId eq cajaId }) {
            it[status] = CajaStatus.Anulada
        }

        val reciboIds =
            cajaNuevaDetalleTable
                .select(cajaNuevaDetalleTable.cajaReciboId)
                .where { cajaNuevaDetalleTable.cajaId eq cajaId }
                .map { it[cajaNuevaDetalleTable.cajaReciboId] }
                .filter { it.isNotBlank() }

        if (reciboIds.isNotEmpty()) {
            val reciboTable = SalesCajaNuevaReciboTableFactory.forCountry(countryCode)
            reciboTable.update({ reciboTable.cajaReciboId inList reciboIds }) {
                it[reciboTable.status] = "AN"
                it[reciboTable.usuarioCreacion] = username.take(MAX_USERNAME_LENGTH)
                it[reciboTable.fechaCreacion] = LocalDateTime.of(date, now.toLocalTime())
            }
        }
    }
}

internal fun registerPartialCreditNoteOnOriginalCash(cmd: PartialCashRegistration) {
    val cajaNuevaTable = SalesCajaNuevaTableFactory.forCountry(cmd.countryCode)
    val cajaNuevaDetalleTable = SalesCajaNuevaDetalleTableFactory.forCountry(cmd.countryCode)
    val originalCaja =
        cajaNuevaTable
            .selectAll()
            .where { cajaNuevaTable.idFactura eq cmd.invoice.idFactura }
            .orderBy(cajaNuevaTable.fechaCreacion to SortOrder.DESC)
            .limit(1)
            .firstOrNull()
            ?: return

    val cajaId = originalCaja[cajaNuevaTable.cajaId]
    val cajaReciboId =
        cajaNuevaDetalleTable
            .select(cajaNuevaDetalleTable.cajaReciboId)
            .where { cajaNuevaDetalleTable.cajaId eq cajaId }
            .limit(1)
            .firstOrNull()
            ?.get(cajaNuevaDetalleTable.cajaReciboId)
            .orEmpty()

    cajaNuevaDetalleTable.insert {
        it[cajaDetalleId] = UUID.randomUUID().toString()
        it[this.cajaId] = cajaId
        it[idFormaPago] = cmd.paymentFormId
        it[idTransaccion] = originalCaja[cajaNuevaTable.idTransaccion]
        it[this.cajaReciboId] = cajaReciboId
        val reversalAmount = if (cmd.countryCode.equals("PA", ignoreCase = true)) -cmd.total else cmd.total
        it[monto] = reversalAmount
        it[montoOriginal] = reversalAmount
        it[concepto] = "Nota de credito ${cmd.creditNoteCode}"
        it[usuarioCreacion] = cmd.username.take(MAX_USERNAME_LENGTH)
        it[fechaCreacion] = cmd.now
        it[retencionTipo] = ""
        it[retencionPorcentaje] = ""
        it[numero] = ""
        it[observacion] = "Nota de credito aplicada sobre factura ${cmd.invoice.codFactura}"
        it[retencionBaseCalculo] = ""
        it[serieSucursal] = cmd.invoice.serieSucursal
        it[cajaSecuencia] = cmd.invoice.idCajaSecuencia
        it[numeroControl] = ""
        it[numeroComprobante] = cmd.creditNoteCode
        it[retencionMonto] = ""
        it[retencionDetalleJson] = ""
        if (cajaNuevaDetalleTable is SalesCajaNuevaDetalleTableVE) {
            it[cajaNuevaDetalleTable.montoRecibido] = cmd.total
            it[cajaNuevaDetalleTable.montoMonedaPrincipal] = cmd.total
        }
    }

    cajaNuevaTable.update({ cajaNuevaTable.cajaId eq cajaId }) {
        val current = originalCaja[cajaNuevaTable.monto] ?: BigDecimal.ZERO.setScale(2)
        it[monto] = current.subtract(cmd.total).coerceAtLeast(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP)
        it[idNotaCredito] = cmd.creditNoteId
    }
}

internal fun restoreInventory(cmd: InventoryRestore) {
    if (cmd.lines.isEmpty()) return

    val kardexId = insertCreditNoteKardex(cmd)
    cmd.lines.forEach { line ->
        insertCreditNoteKardexDetalle(cmd.countryCode, kardexId, line)
        upsertStockForLine(line)
        restoreLotAvailability(line)
    }
}

private fun insertCreditNoteKardex(cmd: InventoryRestore): String {
    val kardexId = UUID.randomUUID().toString()
    val kardexTable = SalesKardexTableFactory.forCountry(cmd.countryCode)
    kardexTable.insert {
        it[kardexTable.idTransaccion] = kardexId
        it[kardexTable.tipoMovimientoAlmacen] = CREDIT_NOTE_KARDEX_MOVEMENT_TYPE
        it[kardexTable.autorizadoPor] = cmd.username.take(MAX_USERNAME_LENGTH)
        it[kardexTable.observacion] = "Entrada por nota de crÃ©dito ${cmd.creditNoteCode}"
        it[kardexTable.fecha] = cmd.date
        it[kardexTable.usuarioCreacion] = cmd.username.take(MAX_USERNAME_LENGTH)
        it[kardexTable.fechaCreacion] = cmd.now
        it[kardexTable.estado] = "Procesado"
        it[kardexTable.idDocumento] = cmd.creditNoteId
        it[kardexTable.codProveedor] = 0
        it[kardexTable.comprobante] = cmd.creditNoteCode
        it[kardexTable.anio] = cmd.date.year
        it[kardexTable.tipoCosto] = "PEPS"
        it[kardexTable.estatus] = 1
        it[kardexTable.entregadoACodigo] = cmd.invoice.facturarARuc.take(KARDEX_RECIPIENT_CODE_LENGTH)
        it[kardexTable.entregadoANombre] = cmd.invoice.facturarA.take(KARDEX_RECIPIENT_NAME_LENGTH)
        it[kardexTable.codDocumento] = cmd.creditNoteCode
        it[kardexTable.subtipoMovimientoAlmacen] = 0
        it[kardexTable.contabilizado] = 0
        it[kardexTable.fechaContabilizacion] = cmd.date
        it[kardexTable.usuarioContabilizacion] = cmd.username.take(MAX_USERNAME_LENGTH)
        it[kardexTable.idAlmacenSalida] =
            cmd.lines
                .first()
                .sourceLine.almacen
        it[kardexTable.idSucursal] = cmd.idSucursal
        it[kardexTable.validadoFecha] = cmd.date
        it[kardexTable.validadoUsuario] = cmd.username.take(MAX_USERNAME_LENGTH)
        it[kardexTable.validadoObservacion] = "Entrada por devolucion"
        if (kardexTable is SalesKardexTablePA) {
            it[kardexTable.controlaStock] = 0
        }
    }
    return kardexId
}

private fun insertCreditNoteKardexDetalle(
    countryCode: String,
    kardexId: String,
    line: ProcessedLine,
) {
    val kardexDetalleTable = SalesKardexDetalleTableFactory.forCountry(countryCode)

    val quantity = line.quantity.setScale(2, RoundingMode.HALF_UP)
    kardexDetalleTable.insert {
        it[kardexDetalleTable.idTransaccionDetalle] = UUID.randomUUID().toString()
        it[kardexDetalleTable.idTransaccion] = kardexId
        it[kardexDetalleTable.idAlmacenEntrada] = line.sourceLine.almacen
        it[kardexDetalleTable.idAlmacenSalida] = 0
        it[kardexDetalleTable.idItem] = line.sourceLine.idItem
        it[kardexDetalleTable.cantidad] = quantity.toFloat()
        it[kardexDetalleTable.cantidadDistribuida] = 0
        it[kardexDetalleTable.precio] = line.sourceLine.precioSinIva
        it[kardexDetalleTable.cantidadMuestra] = 0
        it[kardexDetalleTable.unidadBulto] = "UNIDAD"
        it[kardexDetalleTable.cantidadBulto] = BigDecimal.ONE.setScale(2)
        it[kardexDetalleTable.unidadEmpaque] = "UNIDAD"
        it[kardexDetalleTable.cantidadTotal] = quantity
        it[kardexDetalleTable.costo] = BigDecimal.ZERO.setScale(2)
        if (kardexDetalleTable is SalesKardexDetalleTablePA) {
            it[kardexDetalleTable.idCentroCosto] = 0
            it[kardexDetalleTable.idLoteItem] = 0
        }
    }
}

private fun upsertStockForLine(line: ProcessedLine) {
    val quantity = line.quantity.setScale(2, RoundingMode.HALF_UP)
    val stockRow =
        ItemExistenciaAlmacenTable
            .selectAll()
            .where {
                (ItemExistenciaAlmacenTable.idItem eq line.sourceLine.idItem) and
                    (ItemExistenciaAlmacenTable.codAlmacen eq line.sourceLine.almacen)
            }.limit(1)
            .firstOrNull()
    if (stockRow == null) {
        ItemExistenciaAlmacenTable.insertIgnore {
            it[idItem] = line.sourceLine.idItem
            it[codAlmacen] = line.sourceLine.almacen
            it[cantidad] = quantity
            it[cantidadMuestra] = BigDecimal.ZERO.setScale(INVENTORY_QUANTITY_SCALE)
            it[minimo] = BigDecimal.ZERO.setScale(INVENTORY_QUANTITY_SCALE)
            it[maximo] = BigDecimal.ZERO.setScale(INVENTORY_QUANTITY_SCALE)
        }
    } else {
        val currentQuantity =
            stockRow[ItemExistenciaAlmacenTable.cantidad]
                ?: BigDecimal.ZERO.setScale(INVENTORY_QUANTITY_SCALE)
        ItemExistenciaAlmacenTable.update({
            (ItemExistenciaAlmacenTable.idItem eq line.sourceLine.idItem) and
                (ItemExistenciaAlmacenTable.codAlmacen eq line.sourceLine.almacen)
        }) {
            it[cantidad] =
                currentQuantity.add(quantity).setScale(INVENTORY_QUANTITY_SCALE, RoundingMode.HALF_UP)
        }
    }
}

internal fun restoreLotAvailability(line: ProcessedLine) {
    var remaining = line.quantity.setScale(0, RoundingMode.DOWN).toInt()
    if (remaining <= 0) return

    val lotRows =
        FacturaDetalleProductoLoteTable
            .selectAll()
            .where { FacturaDetalleProductoLoteTable.idDetalleFactura eq line.sourceLine.idDetalleFactura }
            .orderBy(FacturaDetalleProductoLoteTable.id)
            .toList()

    lotRows.forEach { row ->
        if (remaining <= 0) return@forEach
        val restoreQty = minOf(remaining, row[FacturaDetalleProductoLoteTable.cantidad])
        if (restoreQty <= 0) return@forEach

        val lotId = row[FacturaDetalleProductoLoteTable.idLoteItem]
        val lotRow =
            ItemLoteTable
                .selectAll()
                .where { ItemLoteTable.idLoteItem eq lotId }
                .limit(1)
                .firstOrNull()
        if (lotRow != null) {
            val currentAvailability = lotRow[ItemLoteTable.disponibilidad]
            ItemLoteTable.update({ ItemLoteTable.idLoteItem eq lotId }) {
                it[disponibilidad] = currentAvailability.add(BigDecimal.valueOf(restoreQty.toLong()).setScale(2))
            }
        }
        remaining -= restoreQty
    }
}

internal fun registerAbono(cmd: AbonoRegistration) {
    val current =
        CreditNoteCajaTable
            .select(CreditNoteCajaTable.abonoCorrelativo)
            .where { CreditNoteCajaTable.idCaja eq cmd.cajaContext.idCaja }
            .limit(1)
            .firstOrNull()
            ?.get(CreditNoteCajaTable.abonoCorrelativo)
            ?: 0
    val next = current + 1
    CreditNoteCajaTable.update({ CreditNoteCajaTable.idCaja eq cmd.cajaContext.idCaja }) {
        it[abonoCorrelativo] = next
    }

    CreditNoteAbonoTable.insert {
        it[idAbono] = UUID.randomUUID().toString()
        it[codAbono] =
            "AB-${cmd.cajaContext.codigoCaja}-${next.toString().padStart(CORRELATIVE_CODE_LENGTH, '0')}"
        it[fecha] = cmd.now
        it[vencimiento] = 0
        it[fechaVencimiento] = cmd.now
        it[idVendedor] = cmd.invoice.codVendedor
        it[idCajero] = cmd.invoice.codVendedor
        it[idCliente] = cmd.client.idCliente
        it[idCajaSecuencia] = cmd.invoice.idCajaSecuencia
        it[monto] = cmd.total
        it[saldo] = cmd.total
        it[estatus] = 1
        it[descripcion] = "Abono generado por nota de crÃ©dito ${cmd.creditNoteId}"
        it[observacion] = ""
        it[tipo] = "nota_credito"
        it[idOperacion] = cmd.creditNoteId
        it[codigoReparacion] = ""
        it[fechaCreacion] = cmd.now
        it[usuarioCreacion] = cmd.username.take(MAX_USERNAME_LENGTH)
        it[fechaModificacion] = cmd.now
        it[usuarioModificacion] = cmd.username.take(MAX_USERNAME_LENGTH)
        it[fechaAnulacion] = cmd.now
        it[usuarioAnulacion] = cmd.username.take(MAX_USERNAME_LENGTH)
        it[idTransaccion] = cmd.creditNoteId
        it[serieSucursal] = cmd.cajaContext.serieSucursal
        it[idSucursal] = cmd.cajaContext.idSucursal
        it[idCaja] = cmd.cajaContext.idCaja
    }
}

internal fun registerGiftCertificate(cmd: GiftCertificateRegistration) {
    val current =
        CreditNoteCajaTable
            .select(CreditNoteCajaTable.certificadoCorrelativo)
            .where { CreditNoteCajaTable.idCaja eq cmd.cajaContext.idCaja }
            .limit(1)
            .firstOrNull()
            ?.get(CreditNoteCajaTable.certificadoCorrelativo)
            ?: 0
    val next = current + 1
    CreditNoteCajaTable.update({ CreditNoteCajaTable.idCaja eq cmd.cajaContext.idCaja }) {
        it[certificadoCorrelativo] = next
    }

    CreditNoteGiftCertificateTable.insert {
        it[idCertificado] = UUID.randomUUID().toString()
        it[codigo] =
            "CG-${cmd.cajaContext.codigoCaja}-${next.toString().padStart(CORRELATIVE_CODE_LENGTH, '0')}"
        it[monto] = cmd.total
        it[saldo] = cmd.total
        it[idCliente] = cmd.client.idCliente
        it[idCaja] = cmd.cajaContext.idCaja
        it[estatus] = 1
        it[usuarioCreacion] = cmd.username.take(MAX_USERNAME_LENGTH)
        it[fechaCreacion] = cmd.now
        it[idTransaccion] = cmd.creditNoteId
    }
}
