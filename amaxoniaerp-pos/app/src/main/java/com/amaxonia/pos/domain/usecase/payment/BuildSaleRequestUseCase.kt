package com.amaxonia.pos.domain.usecase.payment

import com.amaxonia.pos.domain.model.printer.PrinterType
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
            idFactura = input.idFactura,
            procesar = input.procesar,
            esCobroCreditoPrevio = input.isPriorCreditCollection,
            factura = input.invoice,
            items = input.items,
            impuestos = input.taxes,
            pagoResumen = input.paymentSummary,
            pagos = input.payments,
            moneda = input.currency,
            // FASE 1.1: la selección HKA20 es la única fuente de verdad para que el
            // backend omita la facturación digital Venezuela. Se calcula aquí a
            // partir de la configuración de impresora persistida en Settings.
            useHka20 = input.printerType == PrinterType.THE_FACTORY_HKA,
        )
}

data class BuildSaleRequestInput(
    val procesar: Int = 1,
    val isPriorCreditCollection: Boolean = false,
    val idFactura: String? = null,
    val invoice: SaleInvoiceDto,
    val items: List<SaleItemDto>,
    val taxes: List<SaleTaxDto>,
    val paymentSummary: SalePaymentSummaryDto,
    val payments: List<SalePaymentDto>,
    val currency: SaleCurrencyDto,
    /**
     * Impresora fiscal seleccionada en Settings por el usuario. Única fuente de verdad
     * para que el backend decida entre HKA20 físico (Venezuela) o facturación digital.
     */
    val printerType: PrinterType = PrinterType.NONE,
)

