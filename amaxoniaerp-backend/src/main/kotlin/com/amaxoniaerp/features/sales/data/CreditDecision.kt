package com.amaxoniaerp.features.sales.data

import com.amaxoniaerp.features.clients.data.ClientsTable
import com.amaxoniaerp.features.sales.domain.InvalidSaleRequestException
import com.amaxoniaerp.features.sales.domain.ProcessSaleRequest
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.select
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

internal data class CreditDecision(
    val formaPago: String,
    val totalGeneral: BigDecimal,
    val totalPagos: BigDecimal,
    val totalCxc: BigDecimal,
    val totalPagadoReal: BigDecimal,
    val saldoEsperado: BigDecimal,
    val isCredit: Boolean,
    val fechaVencimiento: LocalDate?,
)

/** Totales derivados de los pagos para decidir el carácter contado/crédito. */
private class CreditAmounts(
    val totalGeneral: BigDecimal,
    val totalPagos: BigDecimal,
    val totalCxc: BigDecimal,
    val saldoEsperado: BigDecimal,
    val saldoDeclarado: BigDecimal,
    formaPagoSolicitada: String,
    val hasNegativePayment: Boolean,
) {
    val totalPagadoReal: BigDecimal
        get() = totalPagos - totalCxc

    val isCredit: Boolean =
        formaPagoSolicitada == "credito" ||
            totalCxc > BigDecimal.ZERO ||
            saldoDeclarado > BigDecimal.ZERO
}

internal fun resolveCreditDecision(
    request: ProcessSaleRequest,
    today: LocalDate,
): CreditDecision {
    val amounts = computeCreditAmounts(request)
    validateSaleAmounts(amounts)
    validatePaymentCoherence(request, amounts)
    if (amounts.isCredit) validateCreditRules(amounts) else validateCashRules(amounts)

    val diasCredito = if (amounts.isCredit) resolveDiasCredito(request) else null

    return CreditDecision(
        formaPago = if (amounts.isCredit) "credito" else "contado",
        totalGeneral = amounts.totalGeneral,
        totalPagos = amounts.totalPagos,
        totalCxc = amounts.totalCxc,
        totalPagadoReal = amounts.totalPagadoReal,
        saldoEsperado = amounts.saldoEsperado,
        isCredit = amounts.isCredit,
        fechaVencimiento = diasCredito?.let { today.plusDays(it.toLong()) },
    )
}

private fun computeCreditAmounts(request: ProcessSaleRequest): CreditAmounts {
    val totalGeneral = request.factura.totalTotalFactura.toScaledBigDecimal(2)
    val pagos =
        request.pagos.map { payment ->
            payment.tipoMovimiento.trim().uppercase() to payment.monto.toScaledBigDecimal(2)
        }
    val totalPagos = pagos.fold(BigDecimal.ZERO.setScale(2)) { total, (_, amount) -> total + amount }
    val totalCxc =
        pagos
            .filter { (tipoMovimiento, _) -> tipoMovimiento == "CXC" }
            .fold(BigDecimal.ZERO.setScale(2)) { total, (_, amount) -> total + amount }
    val saldoEsperado =
        totalGeneral
            .subtract(totalPagos - totalCxc)
            .max(BigDecimal.ZERO)
            .setScale(2, RoundingMode.HALF_UP)
    val saldoDeclarado = request.pagoResumen.totalizarSaldoPendiente.toScaledBigDecimal(2)
    val formaPagoSolicitada =
        request.factura.formaPago
            .trim()
            .lowercase()
    val isCredit =
        formaPagoSolicitada == "credito" ||
            totalCxc > BigDecimal.ZERO ||
            saldoDeclarado > BigDecimal.ZERO

    return CreditAmounts(
        totalGeneral,
        totalPagos,
        totalCxc,
        saldoEsperado,
        saldoDeclarado,
        formaPagoSolicitada,
        pagos.any { (_, amount) -> amount < BigDecimal.ZERO },
    )
}

private fun validateSaleAmounts(amounts: CreditAmounts) {
    val error =
        when {
            amounts.totalGeneral < BigDecimal.ZERO || amounts.hasNegativePayment ->
                "Los montos de la venta no pueden ser negativos"
            amounts.saldoDeclarado < BigDecimal.ZERO ->
                "El saldo pendiente no puede ser negativo"
            amounts.totalCxc > amounts.totalGeneral ->
                "El monto CXC no puede exceder el total de la venta"
            else -> null
        }
    if (error != null) throw InvalidSaleRequestException(error)
}

private fun validatePaymentCoherence(
    request: ProcessSaleRequest,
    amounts: CreditAmounts,
) {
    val excesoPago = amounts.totalPagadoReal - amounts.totalGeneral
    if (excesoPago > BigDecimal.ZERO) {
        val cambioDeclarado = request.pagoResumen.totalizarCambio.toScaledBigDecimal(2)
        if (cambioDeclarado != excesoPago.setScale(2, RoundingMode.HALF_UP)) {
            throw InvalidSaleRequestException("Los pagos exceden el total sin un cambio de efectivo coherente")
        }
    }
}

private fun validateCashRules(amounts: CreditAmounts) {
    if (amounts.totalCxc > BigDecimal.ZERO ||
        amounts.saldoEsperado > BigDecimal.ZERO ||
        amounts.saldoDeclarado > BigDecimal.ZERO
    ) {
        throw InvalidSaleRequestException("Una venta de contado debe quedar totalmente pagada y sin CXC")
    }
}

private fun validateCreditRules(amounts: CreditAmounts) {
    val error =
        when {
            amounts.totalCxc > BigDecimal.ZERO && amounts.totalCxc != amounts.saldoEsperado ->
                "El monto CXC no coincide con el saldo esperado"
            amounts.saldoDeclarado > BigDecimal.ZERO && amounts.saldoDeclarado != amounts.saldoEsperado ->
                "El saldo pendiente no coincide con los pagos recibidos"
            amounts.totalCxc == BigDecimal.ZERO &&
                amounts.saldoDeclarado == BigDecimal.ZERO &&
                amounts.saldoEsperado > BigDecimal.ZERO ->
                "Debe indicar el saldo pendiente de la venta a crédito"
            else -> null
        }
    if (error != null) throw InvalidSaleRequestException(error)
}

private fun resolveDiasCredito(request: ProcessSaleRequest): Int? {
    val client =
        ClientsTable
            .select(ClientsTable.permiteCredito, ClientsTable.dias)
            .where { ClientsTable.idCliente eq request.factura.idCliente }
            .limit(1)
            .firstOrNull()

    val error =
        when {
            client == null -> "No se encontró el cliente para la venta a crédito"
            !client[ClientsTable.permiteCredito] -> "El cliente no permite ventas a crédito"
            client[ClientsTable.dias] < 0 -> "La configuración de días de crédito del cliente es inválida"
            else -> null
        }
    if (error != null) throw InvalidSaleRequestException(error)

    return client!![ClientsTable.dias]
}
