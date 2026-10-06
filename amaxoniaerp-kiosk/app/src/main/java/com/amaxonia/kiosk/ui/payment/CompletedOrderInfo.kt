package com.amaxonia.kiosk.ui.payment

import com.amaxonia.kiosk.core.money.Money
import com.amaxonia.kiosk.core.network.KioskPaymentResponse
import com.amaxonia.kiosk.core.network.KioskReceipt
import com.amaxonia.kiosk.core.network.KioskReceiptLine
import com.amaxonia.kiosk.domain.cart.OrderGraph
import com.amaxonia.kiosk.domain.checkout.CheckoutResult

const val STATUS_PAID_PENDING_INVOICE = "PAID_PENDING_INVOICE"

data class CompletedOrderInfo(
    val orderNumber: String,
    val diningMode: String,
    val tableTent: String?,
    val total: Money,
    val paymentResponse: KioskPaymentResponse,
)

/**
 * Builds what the order-number screen and the receipt printer need from a paid checkout. For
 * [CheckoutResult.PaidPendingSync] (paid, backend not reachable) a receipt is assembled from the
 * server quote so the customer still gets a number and the payment reference for reconciliation.
 * Returns null for results that are not a completed payment.
 */
fun CheckoutResult.toCompletedOrderInfo(
    orderGraph: OrderGraph,
    dispatch: String,
): CompletedOrderInfo? =
    when (this) {
        is CheckoutResult.Success ->
            CompletedOrderInfo(
                orderNumber = paymentResponse.orderNumber,
                diningMode = orderGraph.diningMode.value,
                tableTent = orderGraph.tableTent.value,
                total = Money.fromString(quote.total),
                paymentResponse = paymentResponse,
            )
        is CheckoutResult.PaidPendingSync ->
            CompletedOrderInfo(
                orderNumber = quote.formattedOrderNumber,
                diningMode = orderGraph.diningMode.value,
                tableTent = orderGraph.tableTent.value,
                total = Money.fromString(quote.total),
                paymentResponse =
                    KioskPaymentResponse(
                        orderNumber = quote.formattedOrderNumber,
                        status = STATUS_PAID_PENDING_INVOICE,
                        dispatch = dispatch,
                        receipt =
                            KioskReceipt(
                                companyName = "Amaxonia Kiosk",
                                orderNumber = quote.formattedOrderNumber,
                                diningMode = orderGraph.diningMode.value,
                                tableTent = orderGraph.tableTent.value,
                                customerName = orderGraph.customerName.value,
                                customerId = orderGraph.customerId.value,
                                date = "",
                                lines =
                                    quote.lines.map { line ->
                                        KioskReceiptLine(
                                            qty = line.qty,
                                            description = line.name,
                                            price = line.unitPrice,
                                            total = line.total,
                                            modifiers = line.modifiers.map { it.name },
                                        )
                                    },
                                subtotal = quote.subtotal,
                                tax = quote.tax,
                                total = quote.total,
                                paymentBrand = payment.brand,
                                paymentLast4 = payment.last4,
                                paymentAuthCode = payment.authCode,
                                paymentReference = payment.reference,
                            ),
                    ),
            )
        else -> null
    }
