package com.amaxonia.pos.domain.usecase.payment

import com.amaxonia.pos.domain.model.sales.ProcessSaleRequestDto
import com.amaxonia.pos.domain.model.sales.SaleCurrencyDto
import com.amaxonia.pos.domain.model.sales.SaleInvoiceDto
import com.amaxonia.pos.domain.model.sales.SaleItemDto
import com.amaxonia.pos.domain.model.sales.SalePaymentDto
import com.amaxonia.pos.domain.model.sales.SalePaymentSummaryDto
import com.amaxonia.pos.domain.model.sales.SaleTaxDto

class BuildSaleRequestUseCase {
    operator fun invoke(input: BuildSaleRequestInput): ProcessSaleRequestDto =
        ProcessSaleRequestDto(
            procesar = input.procesar,
            esCobroCreditoPrevio = input.isPriorCreditCollection,
            factura = input.invoice,
            items = input.items,
            impuestos = input.taxes,
            pagoResumen = input.paymentSummary,
            pagos = input.payments,
            moneda = input.currency,
        )
}

data class BuildSaleRequestInput(
    val procesar: Int = 1,
    val isPriorCreditCollection: Boolean = false,
    val invoice: SaleInvoiceDto,
    val items: List<SaleItemDto>,
    val taxes: List<SaleTaxDto>,
    val paymentSummary: SalePaymentSummaryDto,
    val payments: List<SalePaymentDto>,
    val currency: SaleCurrencyDto,
)
