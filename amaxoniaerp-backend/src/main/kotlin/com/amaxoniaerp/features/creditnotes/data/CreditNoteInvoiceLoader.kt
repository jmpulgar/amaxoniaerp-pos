package com.amaxoniaerp.features.creditnotes.data

import com.amaxoniaerp.features.clients.data.ClientsTable
import com.amaxoniaerp.features.creditnotes.domain.CreditNoteValidationException
import org.jetbrains.exposed.sql.JoinType
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import java.math.BigDecimal

/**
 * Serializes credit-note creation for one source invoice. The lock must be
 * acquired before loading returned quantities so concurrent transactions
 * cannot both validate against the same available balance.
 */
internal fun lockInvoiceForCreditNote(invoiceId: String): ResultRow? =
    CreditNoteFacturaTable
        .select(CreditNoteFacturaTable.idFactura)
        .where { CreditNoteFacturaTable.idFactura eq invoiceId }
        .forUpdate()
        .singleOrNull()

internal fun loadInvoiceHeader(invoiceId: String): InvoiceHeader? {
    val row =
        CreditNoteFacturaTable
            .selectAll()
            .where { CreditNoteFacturaTable.idFactura eq invoiceId }
            .limit(1)
            .firstOrNull()
            ?: return null

    return InvoiceHeader(
        idFactura = row[CreditNoteFacturaTable.idFactura],
        codFactura = row[CreditNoteFacturaTable.codFactura],
        codFacturaFiscal = row[CreditNoteFacturaTable.codFacturaFiscal].orEmpty(),
        numeroDocumentoFiscal = row[CreditNoteFacturaTable.numeroDocumentoFiscal].orEmpty(),
        idCliente = row[CreditNoteFacturaTable.idCliente],
        codVendedor = row[CreditNoteFacturaTable.codVendedor],
        codEstatus = row[CreditNoteFacturaTable.codEstatus] ?: 0,
        fechaFactura = row[CreditNoteFacturaTable.fechaFactura],
        subtotal = row[CreditNoteFacturaTable.subtotal],
        totalizarSubTotal = row[CreditNoteFacturaTable.totalizarSubTotal],
        totalizarTotalOperacion = row[CreditNoteFacturaTable.totalizarTotalOperacion],
        totalizarPDescuentoGlobal = row[CreditNoteFacturaTable.totalizarPDescuentoGlobal],
        totalizarDescuentoGlobal = row[CreditNoteFacturaTable.totalizarDescuentoGlobal],
        totalizarBaseImponible = row[CreditNoteFacturaTable.totalizarBaseImponible],
        totalizarMontoIva = row[CreditNoteFacturaTable.totalizarMontoIva],
        totalizarTotalGeneral = row[CreditNoteFacturaTable.totalizarTotalGeneral],
        totalTotalFactura = row[CreditNoteFacturaTable.totalTotalFactura],
        formaPago = row[CreditNoteFacturaTable.formaPago],
        idCajaSecuencia = row[CreditNoteFacturaTable.idCajaSecuencia],
        idCaja = row[CreditNoteFacturaTable.idCaja],
        idSucursal = row[CreditNoteFacturaTable.idSucursal],
        serieSucursal = row[CreditNoteFacturaTable.serieSucursal],
        codigoCaja = row[CreditNoteFacturaTable.codigoCaja],
        facturarA = row[CreditNoteFacturaTable.facturarA],
        facturarARuc = row[CreditNoteFacturaTable.facturarARuc],
        facturarADireccion = row[CreditNoteFacturaTable.facturarADireccion],
        facturarATelefono = row[CreditNoteFacturaTable.facturarATelefono],
        moneda = row[CreditNoteFacturaTable.abrMonedaBase].orEmpty().ifBlank { "USD" },
        tasa = row[CreditNoteFacturaTable.tasa],
        totalRef = row[CreditNoteFacturaTable.totalRef],
    )
}

internal fun loadClient(idCliente: String): ClientContext {
    val row =
        ClientsTable
            .select(
                ClientsTable.codCliente,
                ClientsTable.nombre,
                ClientsTable.apellido,
                ClientsTable.rif,
                ClientsTable.direccion,
                ClientsTable.telefonos,
            ).where { ClientsTable.idCliente eq idCliente }
            .limit(1)
            .firstOrNull()

    val nombre = row?.get(ClientsTable.nombre).orEmpty()
    val apellido = row?.get(ClientsTable.apellido).orEmpty()
    return ClientContext(
        idCliente = idCliente,
        codigoCliente = row?.get(ClientsTable.codCliente).orEmpty().ifBlank { idCliente },
        nombreCompleto = "$nombre $apellido".trim().ifBlank { "CONSUMIDOR FINAL" },
        identificacion = row?.get(ClientsTable.rif).orEmpty().ifBlank { "CF" },
        direccion = row?.get(ClientsTable.direccion).orEmpty(),
        telefono = row?.get(ClientsTable.telefonos).orEmpty(),
    )
}

internal fun loadPreviousCreditNoteTotals(
    countryCode: String,
    invoiceId: String,
): PreviousCreditNoteTotals {
    val headerTable = CreditNoteHeaderTableFactory.forCountry(countryCode)
    return headerTable
        .selectAll()
        .where { headerTable.codFactura eq invoiceId }
        .toList()
        .filter { row ->
            !countryCode.equals("PA", ignoreCase = true) ||
                !row[headerTable.codDevolucionFiscal]
                    .orEmpty()
                    .trim()
                    .equals(REJECTED_FISCAL_CODE, ignoreCase = true)
        }.fold(PreviousCreditNoteTotals()) { totals, row ->
            totals.copy(
                subtotal = totals.subtotal + row[headerTable.subtotal],
                tax = totals.tax + row[headerTable.impuesto],
                total = totals.total + row[headerTable.total],
                globalDiscount = totals.globalDiscount + (row[headerTable.descuentoGlobal] ?: BigDecimal.ZERO),
            )
        }
}

internal fun resolveCajaContext(idCajaSecuencia: String): CajaContext {
    val row =
        CreditNoteCajaSecuenciaTable
            .join(
                CreditNoteCajaTable,
                JoinType.INNER,
                CreditNoteCajaSecuenciaTable.idCaja,
                CreditNoteCajaTable.idCaja,
            ).select(
                CreditNoteCajaTable.idCaja,
                CreditNoteCajaTable.idSucursal,
                CreditNoteCajaTable.codigo,
                CreditNoteCajaTable.serieCaja,
                CreditNoteCajaTable.impresoraModelo,
                CreditNoteCajaSecuenciaTable.serieSucursal,
                CreditNoteCajaSecuenciaTable.secuencia,
            ).where { CreditNoteCajaSecuenciaTable.idCajaSecuencia eq idCajaSecuencia }
            .limit(1)
            .firstOrNull()
            ?: throw CreditNoteValidationException("No se encontró la caja secuencia indicada")

    return CajaContext(
        idCaja = row[CreditNoteCajaTable.idCaja],
        idSucursal = row[CreditNoteCajaTable.idSucursal] ?: 1,
        codigoCaja = row[CreditNoteCajaTable.codigo].orEmpty().ifBlank { "NC" },
        serieSucursal =
            row[CreditNoteCajaSecuenciaTable.serieSucursal]
                ?: row[CreditNoteCajaTable.serieCaja]
                ?: "00001",
        cajaSecuencia = row[CreditNoteCajaSecuenciaTable.secuencia].orEmpty().ifBlank { "000001" },
        impresoraModelo = row[CreditNoteCajaTable.impresoraModelo].orEmpty(),
    )
}

internal fun advanceCreditNoteCorrelative(idCaja: String): Int {
    val current =
        CreditNoteCajaTable
            .select(CreditNoteCajaTable.notacreditoCorrelativo)
            .where { CreditNoteCajaTable.idCaja eq idCaja }
            .forUpdate()
            .limit(1)
            .firstOrNull()
            ?.get(CreditNoteCajaTable.notacreditoCorrelativo)
            ?: 0

    val next = current + 1
    val updated =
        CreditNoteCajaTable.update({ CreditNoteCajaTable.idCaja eq idCaja }) {
            it[notacreditoCorrelativo] = next
        }
    if (updated != 1) {
        throw CreditNoteValidationException("No se pudo avanzar el correlativo de nota de crédito")
    }
    return next
}
