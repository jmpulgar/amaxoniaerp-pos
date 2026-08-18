package com.amaxoniaerp.features.creditnotes.data

import com.amaxoniaerp.features.creditnotes.domain.CreditNoteNotFoundException
import com.amaxoniaerp.features.creditnotes.domain.CreditNoteSourceInvoiceDetailResponse
import com.amaxoniaerp.features.creditnotes.domain.CreditNoteSourceInvoiceLine
import com.amaxoniaerp.features.creditnotes.domain.CreditNoteValidationException
import com.amaxoniaerp.features.electronicinvoice.data.FECorrelativosTable
import com.amaxoniaerp.features.pos.data.CajaFormaPagoTable
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import java.math.BigDecimal

internal fun buildCreditNoteCode(
    codigoCaja: String,
    nextCorrelative: Int,
): String =
    "${codigoCaja.takeIf { it.isNotBlank() } ?: "NC"}" +
        "-${nextCorrelative.toString().padStart(CORRELATIVE_CODE_LENGTH, '0')}"

internal fun validateCreateRequest(request: com.amaxoniaerp.features.creditnotes.domain.CreateCreditNoteRequest) {
    if (request.idCajaSecuencia.isBlank()) {
        throw CreditNoteValidationException("La nota de crédito requiere una caja secuencia activa")
    }
    if (
        request.settlementType ==
        com.amaxoniaerp.features.creditnotes.domain.CreditNoteSettlementType.REINTEGRO &&
        request.idFormaPagoReintegro == null
    ) {
        throw CreditNoteValidationException("Debes indicar la forma de pago de reintegro")
    }
}

internal fun reserveFiscalDocumentNumber(): String {
    val row =
        FECorrelativosTable
            .selectAll()
            .where { FECorrelativosTable.campo eq "numeroDocumentoFiscal" }
            .forUpdate()
            .singleOrNull()
            ?: throw CreditNoteValidationException("No existe correlativo fiscal para numeroDocumentoFiscal")

    val next = row[FECorrelativosTable.contador].toLong() + 1L
    val updated =
        if (next <= 0L || next > MAX_FISCAL_DOCUMENT_NUMBER) {
            -1
        } else {
            FECorrelativosTable.update({ FECorrelativosTable.id eq row[FECorrelativosTable.id] }) {
                it[contador] = next.toInt()
            }
        }

    val error =
        when {
            next <= 0L || next > MAX_FISCAL_DOCUMENT_NUMBER -> "El correlativo fiscal excede el rango permitido"
            updated != 1 -> "No se pudo reservar el correlativo fiscal"
            else -> null
        }
    if (error != null) throw CreditNoteValidationException(error)

    return next.toString().padStart(FISCAL_DOCUMENT_LENGTH, '0')
}

internal fun normalizeFiscalDocumentNumber(value: String): String {
    val normalized = value.trim()
    if (normalized.isBlank() || !normalized.all(Char::isDigit) || normalized.length > FISCAL_DOCUMENT_LENGTH) {
        throw CreditNoteValidationException(
            "El número fiscal de la nota de crédito debe ser numérico de hasta 10 dígitos",
        )
    }
    if (normalized.all { it == '0' }) {
        throw CreditNoteValidationException("El número fiscal de la nota de crédito no puede ser cero")
    }
    return normalized.padStart(FISCAL_DOCUMENT_LENGTH, '0')
}

internal fun resolveCreditNotePaymentFormId(): Int =
    com.amaxoniaerp.features.pos.data.CajaFormaPagoTable
        .selectAll()
        .where { com.amaxoniaerp.features.pos.data.CajaFormaPagoTable.activo eq 1 }
        .mapNotNull { row ->
            row[com.amaxoniaerp.features.pos.data.CajaFormaPagoTable.siglas]
                ?.trim()
                ?.takeIf { it.equals("NC", ignoreCase = true) }
                ?.let { row[com.amaxoniaerp.features.pos.data.CajaFormaPagoTable.idFormaPago] }
        }.firstOrNull()
        ?: throw CreditNoteValidationException("No existe forma de pago activa para Nota de Crédito (NC)")

internal fun buildSourceInvoiceDetail(
    invoiceId: String,
    countryCode: String,
): CreditNoteSourceInvoiceDetailResponse? =
    run {
        val invoice = loadInvoiceHeader(invoiceId) ?: return null
        val client = loadClient(invoice.idCliente)
        val lines = loadInvoiceLines(countryCode, invoiceId)
        if (lines.isEmpty()) return null

        val remainingAmount = lines.fold(BigDecimal.ZERO) { acc, line -> acc + line.availableTotalConIva }

        val totalOriginal = invoice.totalTotalFactura.toDouble()
        val totalRef = invoice.totalRef?.toDouble() ?: 0.0
        val isBs = invoice.moneda.equals("BS", ignoreCase = true) || invoice.moneda.equals("Bs.", ignoreCase = true)

        val totalBs = if (isBs) totalOriginal else totalRef
        val totalUsd = if (!isBs) totalOriginal else totalRef

        return CreditNoteSourceInvoiceDetailResponse(
            id = invoice.idFactura,
            codigo = invoice.codFactura,
            codigoFiscal = invoice.codFacturaFiscal,
            numeroDocumentoFiscal = invoice.numeroDocumentoFiscal,
            fecha = formatDate(invoice.fechaFactura),
            clienteId = invoice.idCliente,
            clienteNombre = client.nombreCompleto,
            clienteIdentificacion = client.identificacion,
            clienteDireccion = client.direccion,
            clienteTelefono = client.telefono,
            codVendedor = invoice.codVendedor,
            totalOriginal = totalOriginal,
            subtotalOriginal = invoice.totalizarSubTotal.toDouble(),
            impuestoOriginal = invoice.totalizarMontoIva.toDouble(),
            remainingAmount = remainingAmount.toDouble(),
            moneda = invoice.moneda,
            tasa = invoice.tasa?.toDouble(),
            totalBs = totalBs,
            totalUsd = totalUsd,
            lines = lines.map { it.toResponseLine() },
        )
    }

internal fun SourceInvoiceLine.toResponseLine(): CreditNoteSourceInvoiceLine =
    CreditNoteSourceInvoiceLine(
        idDetalleFactura = idDetalleFactura,
        idItem = idItem,
        descripcion = descripcion,
        codigo = codigo,
        referencia = referencia,
        cantidadOriginal = quantityOriginal.toDouble(),
        cantidadDevuelta = returnedQuantity.toDouble(),
        cantidadDisponible = availableQuantity.toDouble(),
        precioSinIva = precioSinIva.toDouble(),
        descuentoPorcentaje = descuentoPorcentaje.toDouble(),
        descuentoMontoTotal = descuentoMontoTotal.toDouble(),
        pIva = pIva.toDouble(),
        totalSinIvaOriginal = totalSinIvaOriginal.toDouble(),
        totalConIvaOriginal = totalConIvaOriginal.toDouble(),
        totalSinIvaDisponible = availableTotalSinIva.toDouble(),
        totalConIvaDisponible = availableTotalConIva.toDouble(),
        almacen = almacen,
    )

internal fun lockInvoiceOrThrow(invoiceId: String) {
    lockInvoiceForCreditNote(invoiceId)
        ?: throw CreditNoteNotFoundException("Factura origen no encontrada")
}

internal fun requireInvoiceHeader(invoiceId: String): InvoiceHeader =
    loadInvoiceHeader(invoiceId)
        ?: throw CreditNoteNotFoundException("Factura origen no encontrada")
