package com.amaxonia.pos.domain.usecase.cobranza

import com.amaxonia.pos.domain.model.cobranza.CobranzaFacturaInputDto
import com.amaxonia.pos.domain.model.cobranza.CobranzaPagoInputDto
import com.amaxonia.pos.domain.model.cobranza.CobranzaPendingInvoiceDto
import com.amaxonia.pos.domain.model.cobranza.CreateCobranzaRequestDto
import com.amaxonia.pos.domain.model.cobranza.CreateCobranzaResponseDto
import com.amaxonia.pos.domain.model.money.Money
import com.amaxonia.pos.domain.model.payment.FormaPago
import com.amaxonia.pos.domain.repository.ActiveCajaContext
import com.amaxonia.pos.domain.repository.CobranzaRepository
import java.util.UUID

/** Factura elegida para cobrar con el monto que se abonará (≤ saldo). */
data class CobranzaInvoiceSelection(
    val invoice: CobranzaPendingInvoiceDto,
    val amount: Money,
)

/** Forma de pago capturada; en efectivo [amount] es lo recibido (puede generar vuelto). */
data class CobranzaPaymentLine(
    val id: String = UUID.randomUUID().toString(),
    val formaPago: FormaPago,
    val amount: Money,
    val referencia: String = "",
) {
    val isCash: Boolean get() = isCashForma(formaPago)
}

/** Resumen calculado del cobro en curso. */
data class CobranzaTotals(
    val totalACobrar: Money,
    val totalRecibido: Money,
    val cambio: Money,
    val faltante: Money,
    val error: String?,
) {
    val isReady: Boolean get() = error == null
}

private val CASH_SIGLAS = setOf("CASH", "EF", "EFE", "EFECTIVO")
private val CREDIT_SIGLAS = setOf("CXC", "CRED", "CREDITO", "CR")

fun isCashForma(forma: FormaPago): Boolean = forma.siglas?.trim()?.uppercase() in CASH_SIGLAS

/** Formas de pago válidas para cobrar una deuda: nunca crédito (no se paga crédito con crédito). */
fun cobranzaFormasPago(formas: List<FormaPago>): List<FormaPago> = formas.filterNot { it.siglas?.trim()?.uppercase() in CREDIT_SIGLAS }

/**
 * Reglas del cobro: el total a cobrar es la suma de los montos por factura; lo recibido debe
 * cubrirlo exactamente, salvo excedente en efectivo, que se devuelve como vuelto.
 */
object CobranzaCalculator {
    fun totals(
        selections: List<CobranzaInvoiceSelection>,
        payments: List<CobranzaPaymentLine>,
    ): CobranzaTotals {
        val total = selections.fold(Money.ZERO) { acc, selection -> acc + selection.amount }
        val received = payments.fold(Money.ZERO) { acc, payment -> acc + payment.amount }
        val cashReceived = payments.filter { it.isCash }.fold(Money.ZERO) { acc, payment -> acc + payment.amount }
        val excess = (received - total).coerceAtLeastZero()
        val faltante = (total - received).coerceAtLeastZero()
        val error =
            when {
                selections.isEmpty() -> "Selecciona al menos una factura"
                selections.any { it.amount <= Money.ZERO } -> "Los montos a cobrar deben ser mayores a cero"
                selections.any { it.amount > Money.fromDouble(it.invoice.saldo) } -> "Un monto supera el saldo de su factura"
                payments.isEmpty() -> "Agrega al menos una forma de pago"
                payments.any { it.amount <= Money.ZERO } -> "Los montos de pago deben ser mayores a cero"
                faltante > Money.ZERO -> "Falta cubrir ${faltante.toPlainString()}"
                excess > cashReceived -> "El excedente solo puede devolverse en efectivo"
                else -> null
            }
        return CobranzaTotals(total, received, if (excess <= cashReceived) excess else Money.ZERO, faltante, error)
    }

    /**
     * Pagos aplicados a la deuda: el vuelto se descuenta de las líneas en efectivo (de la última
     * a la primera) y queda registrado como `efectivoCambio`.
     */
    fun appliedPayments(
        payments: List<CobranzaPaymentLine>,
        cambio: Money,
    ): List<CobranzaPagoInputDto> {
        var pendingChange = cambio
        val changeByLine = mutableMapOf<String, Money>()
        payments.asReversed().filter { it.isCash }.forEach { line ->
            val portion = if (pendingChange > line.amount) line.amount else pendingChange
            if (portion > Money.ZERO) {
                changeByLine[line.id] = portion
                pendingChange -= portion
            }
        }
        return payments.mapNotNull { line ->
            val change = changeByLine[line.id] ?: Money.ZERO
            val applied = line.amount - change
            if (applied <= Money.ZERO) {
                null
            } else {
                CobranzaPagoInputDto(
                    idFormaPago = line.formaPago.idFormaPago,
                    monto = applied.toDouble(),
                    montoRecibido = line.amount.toDouble(),
                    efectivoCambio = change.toDouble(),
                    referencia = line.referencia.trim(),
                )
            }
        }
    }
}

/** Datos del cobro que arma la pantalla. [idCobranza] se conserva entre reintentos. */
data class CobranzaSubmission(
    val idCliente: String,
    val selections: List<CobranzaInvoiceSelection>,
    val payments: List<CobranzaPaymentLine>,
    val codVendedor: Int = 0,
    val observacion: String = "",
    val idCobranza: String = UUID.randomUUID().toString(),
)

/**
 * Registra el cobro de facturas pendientes en el ERP con una o varias formas de pago.
 * Requiere una caja activa con secuencia abierta (el ingreso entra al cierre de esa caja).
 */
class SubmitCobranzaUseCase(
    private val repository: CobranzaRepository,
    private val cajaContext: ActiveCajaContext,
) {
    suspend operator fun invoke(submission: CobranzaSubmission): Result<CreateCobranzaResponseDto> {
        val totals = CobranzaCalculator.totals(submission.selections, submission.payments)
        val caja = cajaContext.activeCaja()
        val secuencia = cajaContext.activeSecuencia()
        val error =
            when {
                totals.error != null -> totals.error
                caja == null -> "Selecciona una caja para cobrar"
                secuencia?.idCajaSecuencia.isNullOrBlank() -> "La caja no está abierta. Aperturala para cobrar"
                else -> null
            }
        if (error != null || caja == null) return Result.failure(IllegalStateException(error))
        val request =
            CreateCobranzaRequestDto(
                idCobranza = submission.idCobranza,
                idCliente = submission.idCliente,
                idCaja = caja.idCaja,
                idCajaSecuencia = secuencia?.idCajaSecuencia.orEmpty(),
                idSucursal = caja.idSucursal ?: 1,
                serieSucursal = secuencia?.serieSucursal ?: caja.serieSucursal ?: caja.serieCaja,
                codVendedor = submission.codVendedor.takeIf { it > 0 } ?: caja.defaultSellerId ?: 0,
                observacion = submission.observacion.trim(),
                facturas =
                    submission.selections.map {
                        CobranzaFacturaInputDto(idFactura = it.invoice.idFactura, monto = it.amount.toDouble())
                    },
                pagos = CobranzaCalculator.appliedPayments(submission.payments, totals.cambio),
            )
        return repository.cobrar(request)
    }
}
