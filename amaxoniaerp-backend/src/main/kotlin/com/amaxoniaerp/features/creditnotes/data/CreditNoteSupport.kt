package com.amaxoniaerp.features.creditnotes.data

import com.amaxoniaerp.features.creditnotes.domain.CreditNoteFiscalStatus
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

internal const val ANNULLED_INVOICE_STATUS = 3
internal const val QUANTITY_SCALE = 3
internal const val UNIT_CALCULATION_SCALE = 6
internal const val FINANCIAL_DIVISION_SCALE = 12
internal const val CORRELATIVE_CODE_LENGTH = 5
internal const val MAX_FISCAL_DOCUMENT_NUMBER = 9_999_999_999L
internal const val FISCAL_DOCUMENT_LENGTH = 10
internal const val CREDIT_NOTE_KARDEX_MOVEMENT_TYPE = 14
internal const val KARDEX_RECIPIENT_CODE_LENGTH = 10
internal const val KARDEX_RECIPIENT_NAME_LENGTH = 30
internal const val INVENTORY_QUANTITY_SCALE = 4

internal const val MAX_OBSERVATION_LENGTH = 300
internal const val MAX_PERIOD_LENGTH = 20
internal const val MAX_USERNAME_LENGTH = 50
internal const val PENDING_FISCAL_CODE = "00000000"
internal const val UNCERTAIN_FISCAL_CODE = "INCIERTA"
internal const val REJECTED_FISCAL_CODE = "RECHAZADA"
internal const val CONFIRMED_FISCAL_CODE = "CONFIRMADA"
internal const val MAX_PAC_DIAGNOSTIC_LENGTH = 5000

internal val DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ISO_DATE
internal val DATE_TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

internal fun BigDecimal.coerceAtLeastZero(scale: Int): BigDecimal =
    if (this < BigDecimal.ZERO) {
        BigDecimal.ZERO.setScale(scale, RoundingMode.HALF_UP)
    } else {
        setScale(scale, RoundingMode.HALF_UP)
    }

internal fun BigDecimal.isEffectivelyZero(): Boolean =
    setScale(QUANTITY_SCALE, RoundingMode.HALF_UP)
        .compareTo(BigDecimal.ZERO.setScale(QUANTITY_SCALE, RoundingMode.HALF_UP)) == 0

internal fun resolveFiscalStatus(
    codDevolucionFiscal: String,
    numeroDocumentoFiscal: String,
): CreditNoteFiscalStatus =
    when (codDevolucionFiscal.trim().uppercase()) {
        PENDING_FISCAL_CODE -> CreditNoteFiscalStatus.PENDIENTE
        UNCERTAIN_FISCAL_CODE -> CreditNoteFiscalStatus.INCIERTA
        REJECTED_FISCAL_CODE -> CreditNoteFiscalStatus.RECHAZADA
        CONFIRMED_FISCAL_CODE -> CreditNoteFiscalStatus.CONFIRMADA
        else -> {
            val hasFiscalCode = isValidFiscalValue(codDevolucionFiscal)
            val hasDocumentNumber = isValidFiscalValue(numeroDocumentoFiscal)
            if (hasFiscalCode || hasDocumentNumber) {
                CreditNoteFiscalStatus.CONFIRMADA
            } else {
                CreditNoteFiscalStatus.PENDIENTE
            }
        }
    }

internal fun resolvePanamaFiscalStatus(
    cufe: String,
    estadoDevolucion: String?,
): CreditNoteFiscalStatus =
    when {
        cufe.trim().isNotBlank() ||
            estadoDevolucion.equals("PROCESADA", ignoreCase = true) ||
            estadoDevolucion.equals(CONFIRMED_FISCAL_CODE, ignoreCase = true) ->
            CreditNoteFiscalStatus.CONFIRMADA
        estadoDevolucion.equals(REJECTED_FISCAL_CODE, ignoreCase = true) ->
            CreditNoteFiscalStatus.RECHAZADA
        estadoDevolucion.equals(UNCERTAIN_FISCAL_CODE, ignoreCase = true) ->
            CreditNoteFiscalStatus.INCIERTA
        else ->
            CreditNoteFiscalStatus.PENDIENTE
    }

internal fun fiscalStatusCode(status: CreditNoteFiscalStatus): String =
    when (status) {
        CreditNoteFiscalStatus.PENDIENTE -> PENDING_FISCAL_CODE
        CreditNoteFiscalStatus.INCIERTA -> UNCERTAIN_FISCAL_CODE
        CreditNoteFiscalStatus.RECHAZADA -> REJECTED_FISCAL_CODE
        CreditNoteFiscalStatus.CONFIRMADA -> CONFIRMED_FISCAL_CODE
    }

internal fun resolveDisplayFiscalNumber(
    codDevolucionFiscal: String,
    numeroDocumentoFiscal: String,
): String =
    listOf(codDevolucionFiscal, numeroDocumentoFiscal)
        .map(String::trim)
        .firstOrNull(::isValidFiscalValue)
        .orEmpty()

internal fun isValidFiscalValue(value: String): Boolean =
    run {
        val normalized = value.trim()
        if (normalized.isBlank()) return false
        if (normalized == PENDING_FISCAL_CODE) return false
        val digits = normalized.filter(Char::isDigit)
        return digits.isNotEmpty() && digits.any { it != '0' }
    }

internal fun parsePacDate(value: String?): LocalDateTime? {
    val normalized = value?.trim().orEmpty()
    if (normalized.isBlank()) return null
    return runCatching { OffsetDateTime.parse(normalized).toLocalDateTime() }
        .recoverCatching { LocalDateTime.parse(normalized) }
        .recoverCatching { LocalDateTime.parse(normalized.replace(" ", "T")) }
        .recoverCatching { LocalDate.parse(normalized).atStartOfDay() }
        .getOrNull()
}

internal fun parseDate(value: String): LocalDate =
    runCatching { LocalDate.parse(value) }
        .getOrElse {
            throw com.amaxoniaerp.features.creditnotes.domain.CreditNoteValidationException(
                "Fecha inválida, usa formato yyyy-MM-dd",
            )
        }

internal fun divideSafe(
    value: BigDecimal,
    divisor: BigDecimal,
    scale: Int,
): BigDecimal {
    if (divisor.compareTo(BigDecimal.ZERO) == 0) return BigDecimal.ZERO.setScale(scale, RoundingMode.HALF_UP)
    return value.divide(divisor, scale, RoundingMode.HALF_UP)
}

internal fun minBigDecimal(
    first: BigDecimal,
    second: BigDecimal,
): BigDecimal = if (first <= second) first else second

internal data class RequestedLine(
    val idDetalleFactura: String,
    val cantidad: Double,
)

internal data class CreditNoteTotals(
    val subtotal: BigDecimal,
    val tax: BigDecimal,
    val total: BigDecimal,
)

internal data class ClientContext(
    val idCliente: String,
    val codigoCliente: String,
    val nombreCompleto: String,
    val identificacion: String,
    val direccion: String,
    val telefono: String,
)

internal data class InvoiceHeader(
    val idFactura: String,
    val codFactura: String,
    val codFacturaFiscal: String,
    val numeroDocumentoFiscal: String,
    val idCliente: String,
    val codVendedor: Int,
    val codEstatus: Int,
    val fechaFactura: LocalDate?,
    val subtotal: BigDecimal,
    val totalizarSubTotal: BigDecimal,
    val totalizarTotalOperacion: BigDecimal,
    val totalizarPDescuentoGlobal: BigDecimal,
    val totalizarDescuentoGlobal: BigDecimal,
    val totalizarBaseImponible: BigDecimal,
    val totalizarMontoIva: BigDecimal,
    val totalizarTotalGeneral: BigDecimal,
    val totalTotalFactura: BigDecimal,
    val formaPago: String,
    val idCajaSecuencia: String,
    val idCaja: String,
    val idSucursal: Int,
    val serieSucursal: String,
    val codigoCaja: String,
    val facturarA: String,
    val facturarARuc: String,
    val facturarADireccion: String,
    val facturarATelefono: String,
    val moneda: String,
    val tasa: BigDecimal?,
    val totalRef: BigDecimal?,
)

internal data class SourceInvoiceLine(
    val idDetalleFactura: String,
    val idItem: Int,
    val descripcion: String,
    val codigo: String,
    val referencia: String,
    val quantityOriginal: BigDecimal,
    val returnedQuantity: BigDecimal,
    val availableQuantity: BigDecimal,
    val precioSinIva: BigDecimal,
    val descuentoPorcentaje: BigDecimal,
    val descuentoMontoTotal: BigDecimal,
    val pIva: BigDecimal,
    val totalSinIvaOriginal: BigDecimal,
    val totalConIvaOriginal: BigDecimal,
    val availableTotalSinIva: BigDecimal,
    val availableTotalConIva: BigDecimal,
    val unitDiscountAmount: BigDecimal,
    val almacen: Int,
    val codVendedor: Int,
)

internal data class ProcessedLine(
    val sourceLine: SourceInvoiceLine,
    val quantity: BigDecimal,
    val discountAmount: BigDecimal,
    val totalSinIva: BigDecimal,
    val totalConIva: BigDecimal,
    val globalDiscountAmount: BigDecimal,
)

internal data class PreviousCreditNoteTotals(
    val subtotal: BigDecimal = BigDecimal.ZERO.setScale(2),
    val tax: BigDecimal = BigDecimal.ZERO.setScale(2),
    val total: BigDecimal = BigDecimal.ZERO.setScale(2),
    val globalDiscount: BigDecimal = BigDecimal.ZERO.setScale(2),
)

internal data class CreditNoteFinancials(
    val lines: List<ProcessedLine>,
    val totals: CreditNoteTotals,
    val globalDiscount: BigDecimal,
)

internal data class CajaContext(
    val idCaja: String,
    val idSucursal: Int,
    val codigoCaja: String,
    val serieSucursal: String,
    val cajaSecuencia: String,
    val impresoraModelo: String,
)

internal data class CreditNoteHeaderContext(
    val id: String,
    val codigo: String,
    val facturaId: String,
    val facturaCodigo: String,
    val fecha: LocalDate?,
    val fechaCreacion: LocalDateTime,
    val periodo: String,
    val observacion: String,
    val clienteNombre: String,
    val clienteIdentificacion: String,
    val clienteDireccion: String,
    val clienteTelefono: String,
    val subtotal: BigDecimal,
    val impuesto: BigDecimal,
    val total: BigDecimal,
    val fiscalStatus: com.amaxoniaerp.features.creditnotes.domain.CreditNoteFiscalStatus,
    val fiscalNumber: String,
    val printerSerial: String,
    val originalFiscalNumber: String,
    val originalInvoiceDate: LocalDate?,
    val anulaFacturaCompleta: Boolean,
)

/** Filtros de listado de notas de crédito (paginación + búsqueda + rango de fechas). */
data class CreditNoteListQuery(
    val limit: Int,
    val offset: Long,
    val search: String?,
    val fechaInicio: LocalDate?,
    val fechaFin: LocalDate?,
)
