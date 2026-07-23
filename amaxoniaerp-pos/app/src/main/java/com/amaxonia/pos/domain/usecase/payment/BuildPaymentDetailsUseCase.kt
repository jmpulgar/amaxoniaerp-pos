package com.amaxonia.pos.domain.usecase.payment

import com.amaxonia.pos.domain.model.TransactionPaymentMethod
import com.amaxonia.pos.domain.model.money.Money
import com.amaxonia.pos.domain.model.payment.EXTERNAL_GATEWAY_MARKER
import com.amaxonia.pos.domain.model.payment.FormaPago
import com.amaxonia.pos.domain.model.payment.FormaPagoDetalle
import com.amaxonia.pos.domain.model.payment.FormapagoDetallePayload

data class BuildPaymentDetailsInput(
    val isCash: Boolean,
    val totalAmount: Money,
    val cashTenderedAmount: Money = Money.ZERO,
    val cashMethods: List<FormaPago>,
    val nonCashMethods: List<FormaPago>,
    val allMethods: List<FormaPago>,
    val nonCashAmountsInput: Map<Int, String>,
)

data class PaymentDetails(
    val payload: FormapagoDetallePayload,
    val transactionMethods: List<TransactionPaymentMethod>,
)

class BuildPaymentDetailsUseCase {
    operator fun invoke(input: BuildPaymentDetailsInput): PaymentDetails {
        val nonCashDetail = buildNonCashDetail(input)
        val nonCashTotal =
            nonCashDetail.fold(Money.ZERO) { accumulated, detail ->
                accumulated + Money.fromDouble(detail.monto)
            }
        val detail = buildCashDetail(input, nonCashTotal) + nonCashDetail
        return PaymentDetails(
            payload = buildPayload(detail),
            transactionMethods = detail.mapNotNull { it.toTransactionMethod(input.allMethods) },
        )
    }

    private fun buildCashDetail(
        input: BuildPaymentDetailsInput,
        nonCashTotal: Money,
    ): List<FormaPagoDetalle> {
        val method = input.cashMethods.firstOrNull() ?: return emptyList()
        val tendered =
            input.cashTenderedAmount.takeIf { it > Money.ZERO }
                ?: input.totalAmount.takeIf { input.isCash }
                ?: Money.ZERO
        val remaining = (input.totalAmount - nonCashTotal).coerceAtLeastZero()
        val applied = if (tendered > remaining) remaining else tendered
        return if (applied > Money.ZERO) listOf(method.toDetail(applied.toDouble())) else emptyList()
    }

    private fun buildNonCashDetail(input: BuildPaymentDetailsInput): List<FormaPagoDetalle> =
        input.nonCashMethods.mapNotNull { method ->
            val amount = Money.parse(input.nonCashAmountsInput[method.idFormaPago])
            if (amount <= Money.ZERO) null else method.toDetail(amount.toDouble())
        }

    private fun FormaPago.toDetail(amount: Double): FormaPagoDetalle =
        FormaPagoDetalle(
            idFormaPago = idFormaPago,
            sigla = siglas.orEmpty(),
            monto = amount,
            idCajaTpConcepto = idCajaTpConcepto,
            idBancoCuenta = idBancoCuenta,
            idBancoOperacion = idBancoOperacion,
        )

    private fun buildPayload(detail: List<FormaPagoDetalle>): FormapagoDetallePayload {
        val cash = detail.sumMoneyWhere { it.sigla.equals(CASH_SIGLA, ignoreCase = true) }
        val credit = detail.sumMoneyWhere { it.sigla.equals(CREDIT_SIGLA, true) || it.sigla.equals(CXC_SIGLA, true) }
        val total = detail.fold(Money.ZERO) { accumulated, item -> accumulated + Money.fromDouble(item.monto) }
        val other = (total - cash - credit).coerceAtLeastZero()
        return FormapagoDetallePayload(
            totalizarMontoEfectivo = cash.toDouble(),
            totalizarMontoCredito = credit.toDouble(),
            totalizarMontoOtros = other.toDouble(),
            detalle = detail,
        )
    }

    private fun List<FormaPagoDetalle>.sumMoneyWhere(predicate: (FormaPagoDetalle) -> Boolean): Money =
        filter(predicate).fold(Money.ZERO) { accumulated, item -> accumulated + Money.fromDouble(item.monto) }

    private fun FormaPagoDetalle.toTransactionMethod(allMethods: List<FormaPago>): TransactionPaymentMethod? {
        val method = allMethods.firstOrNull { it.idFormaPago == idFormaPago } ?: return null
        return TransactionPaymentMethod(
            description = method.descripcion.orEmpty(),
            sigla = method.siglas.orEmpty(),
            amount = monto,
            fiscalCode = resolveFiscalPaymentCode(method),
            gatewayCommandPrefix = if (method.requiresRapidPay()) EXTERNAL_GATEWAY_MARKER else "",
        )
    }

    private fun FormaPago.requiresRapidPay(): Boolean = descripcion.orEmpty().trim().equals(RAPID_PAY_DESCRIPTION, ignoreCase = true)

    private fun resolveFiscalPaymentCode(method: FormaPago): String {
        method.formaPagoFact
            ?.trim()
            ?.takeIf(VALID_FISCAL_CODES::contains)
            ?.let { return it }
        val normalized =
            listOf(
                method.descripcion.orEmpty(),
                method.siglas.orEmpty(),
                method.codigo.orEmpty(),
            ).joinToString(" ").lowercase()
        return when {
            normalized.contains("punto de venta") -> DEBIT_CARD_CODE
            normalized.contains(
                "debito",
            ) ||
                normalized == "pv" ||
                normalized.contains(" tdc") ||
                normalized.startsWith("tdc") -> DEBIT_CARD_CODE
            normalized.contains("credito") -> CREDIT_CARD_CODE
            normalized.contains("efectivo") || normalized.contains("cash") || normalized.contains("divisa") -> CASH_CODE
            OTHER_PAYMENT_ALIASES.any(normalized::contains) -> OTHER_CODE
            else -> UNKNOWN_CODE
        }
    }

    private companion object {
        const val CASH_SIGLA = "CASH"
        const val CREDIT_SIGLA = "CRED"
        const val CXC_SIGLA = "CXC"
        const val RAPID_PAY_DESCRIPTION = "PUNTO DE VENTA"
        const val CASH_CODE = "101"
        const val DEBIT_CARD_CODE = "102"
        const val CREDIT_CARD_CODE = "103"
        const val OTHER_CODE = "104"
        const val UNKNOWN_CODE = "199"
        val VALID_FISCAL_CODES = setOf(CASH_CODE, DEBIT_CARD_CODE, CREDIT_CARD_CODE, OTHER_CODE, UNKNOWN_CODE)
        val OTHER_PAYMENT_ALIASES =
            listOf(
                "transfer",
                "deposit",
                "cheque",
                "zelle",
                "pago movil",
                "yappy",
                "nequi",
                "solutech",
                "sunmi",
                "retencion",
                "puntos",
                "anticipo",
            )
    }
}
