package com.amaxoniaerp.features.sales.data

import com.amaxoniaerp.features.sales.domain.InvalidSaleRequestException
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.plus
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.update

private const val CORRELATIVE_RETRY_ATTEMPTS = 10
private const val INVOICE_SEQUENCE_LENGTH = 5
private const val SHORT_CODE_LENGTH = 10

internal fun resolveInvoiceCode(
    countryCode: String,
    request: com.amaxoniaerp.features.sales.domain.ProcessSaleRequest,
): String {
    val idCaja = request.factura.idCaja.trim()
    if (idCaja.isBlank()) {
        throw InvalidSaleRequestException("idCaja es obligatorio para generar cod_factura desde caja")
    }

    var invoiceCode = getNextCodePreviewFromCaja(idCaja, request.factura.codigoCaja)
    var jumpedDuplicate = false

    while (invoiceCodeExists(countryCode, invoiceCode)) {
        jumpedDuplicate = true
        invoiceCode = consumeAndGetNextCodeFromCaja(idCaja, request.factura.codigoCaja)
    }

    if (!jumpedDuplicate) {
        consumeCorrelativoCaja(idCaja)
    }

    return invoiceCode
}

internal fun invoiceCodeExists(
    countryCode: String,
    code: String,
): Boolean {
    val t = SalesFacturaTableFactory.forCountry(countryCode)
    return t
        .select(t.idFactura)
        .where { t.codFactura eq code }
        .limit(1)
        .any()
}

internal fun getNextCodePreviewFromCaja(
    idCaja: String,
    fallbackCodigoCaja: String,
): String {
    val row =
        SalesCajaTable
            .select(SalesCajaTable.codigo, SalesCajaTable.facturaCorrelativo)
            .where { SalesCajaTable.id eq idCaja }
            .limit(1)
            .firstOrNull()
            ?: throw InvalidSaleRequestException("No se encontró caja para id_caja=$idCaja")

    val codigoCaja = row[SalesCajaTable.codigo]?.takeIf { it.isNotBlank() } ?: fallbackCodigoCaja
    val correlativo = row[SalesCajaTable.facturaCorrelativo] + 1
    return formatInvoiceCode(codigoCaja, correlativo)
}

internal fun consumeAndGetNextCodeFromCaja(
    idCaja: String,
    fallbackCodigoCaja: String,
): String {
    repeat(CORRELATIVE_RETRY_ATTEMPTS) {
        val row =
            SalesCajaTable
                .select(SalesCajaTable.codigo, SalesCajaTable.facturaCorrelativo)
                .where { SalesCajaTable.id eq idCaja }
                .limit(1)
                .firstOrNull()
                ?: throw InvalidSaleRequestException("No se encontró caja para id_caja=$idCaja")

        val current = row[SalesCajaTable.facturaCorrelativo]
        val next = current + 1
        val updated =
            SalesCajaTable.update({
                (SalesCajaTable.id eq idCaja) and (SalesCajaTable.facturaCorrelativo eq current)
            }) {
                it[facturaCorrelativo] = facturaCorrelativo.plus(1)
            }

        if (updated == 1) {
            val codigoCaja = row[SalesCajaTable.codigo]?.takeIf { it.isNotBlank() } ?: fallbackCodigoCaja
            return formatInvoiceCode(codigoCaja, next)
        }
    }

    throw InvalidSaleRequestException("No se pudo avanzar factura_correlativo para caja=$idCaja")
}

internal fun consumeCorrelativoCaja(idCaja: String) {
    repeat(CORRELATIVE_RETRY_ATTEMPTS) {
        val current =
            SalesCajaTable
                .select(SalesCajaTable.facturaCorrelativo)
                .where { SalesCajaTable.id eq idCaja }
                .limit(1)
                .firstOrNull()
                ?.get(SalesCajaTable.facturaCorrelativo)
                ?: throw InvalidSaleRequestException("No se encontró caja para id_caja=$idCaja")

        val updated =
            SalesCajaTable.update({
                (SalesCajaTable.id eq idCaja) and (SalesCajaTable.facturaCorrelativo eq current)
            }) {
                it[facturaCorrelativo] = facturaCorrelativo.plus(1)
            }

        if (updated == 1) return
    }

    throw InvalidSaleRequestException("No se pudo consumir correlativo de caja para id_caja=$idCaja")
}

internal fun formatInvoiceCode(
    codigoCaja: String,
    correlativo: Int,
): String {
    if (codigoCaja.isBlank()) {
        throw InvalidSaleRequestException("codigo de caja inválido para construir cod_factura")
    }
    return "${codigoCaja.trim()}-${correlativo.toString().padStart(INVOICE_SEQUENCE_LENGTH, '0')}"
}

internal fun resolveCajaSecuenciaCodigo(idCajaSecuencia: String): String =
    SalesCajaSecuenciaTable
        .select(SalesCajaSecuenciaTable.secuencia)
        .where { SalesCajaSecuenciaTable.id eq idCajaSecuencia }
        .limit(1)
        .firstOrNull()
        ?.get(SalesCajaSecuenciaTable.secuencia)
        ?.takeIf { it.isNotBlank() }
        ?.take(SHORT_CODE_LENGTH)
        ?: "000001"

internal fun normalizeTipoMovimiento(value: String): String {
    val normalized = value.trim().uppercase()
    val allowed = setOf("DE", "TR", "CH", "MB", "OT", "TDC", "NEQ", "ABONO", "CASH", "CXC")
    if (normalized in allowed) return normalized

    return when (normalized) {
        "EF", "EFE", "EFECTIVO" -> "CASH"
        "CK", "CK2", "CHEQUE" -> "CH"
        "DP", "DEP", "DEPOSITO" -> "DE"
        "TRANSFERENCIA", "TRANSF", "PM" -> "TR"
        "TARJETA", "POS", "PV", "DB", "DEBITO", "DEBIT", "CR", "CRED", "CREDITO", "PT", "PUNTO" -> "TDC"
        "NEQUI" -> "NEQ"
        "GC", "OTRO", "OTROS" -> "OT"
        else -> "OT"
    }
}
