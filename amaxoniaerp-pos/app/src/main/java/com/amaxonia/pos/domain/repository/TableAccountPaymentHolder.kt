package com.amaxonia.pos.domain.repository

import com.amaxonia.pos.domain.model.SaleFinancialSnapshot
import com.amaxonia.pos.domain.model.mesas.CuentaMesaResponse
import com.amaxonia.pos.domain.model.money.Money
import com.amaxonia.pos.domain.model.sales.CuentaMesaVentaDto
import com.amaxonia.pos.domain.model.sales.SaleItemDto
import com.amaxonia.pos.domain.model.sales.SaleTaxDto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Contexto efímero de la cuenta que está atravesando el pipeline estándar de pago.
 *
 * La clave de correlación es determinista por cuenta. Un timeout o reconstrucción de pantalla
 * vuelve a usar el mismo `idFactura`, mientras el backend confirma cuenta y factura en una sola
 * transacción.
 */
data class TableAccountPayment(
    val areaId: Int,
    val mesaId: Int,
    val sesionId: Int,
    val cuenta: CuentaMesaResponse,
) {
    val correlationId: String = "mesa-$sesionId-cuenta-${cuenta.id}"

    val saleContext =
        CuentaMesaVentaDto(
            areaId = areaId,
            mesaId = mesaId,
            sesionMesaId = sesionId,
            cuentaMesaId = cuenta.id,
        )

    val financialSnapshot: SaleFinancialSnapshot = cuenta.toFinancialSnapshot()

    val saleItems: List<SaleItemDto> =
        cuenta.detalle.map { detalle ->
            SaleItemDto(
                idItem = detalle.productoId,
                itemAlmacen = detalle.itemAlmacen,
                itemDescripcion = detalle.itemDescripcion,
                itemCantidad = detalle.cantidad,
                itemPrecioSinIva = detalle.itemPrecioSinIva,
                itemDescuento = detalle.itemDescuento,
                itemMontoDescuento = detalle.itemMontoDescuento,
                itemPIva = detalle.itemPIva,
                itemTotalSinIva = detalle.itemTotalSinIva,
                itemTotalConIva = detalle.itemTotalConIva,
                itemCantidadTotal = detalle.cantidad,
                itemCodigo = detalle.itemCodigo,
            )
        }
}

private fun CuentaMesaResponse.toFinancialSnapshot(): SaleFinancialSnapshot {
    val taxLines =
        detalle
            .groupBy { it.itemPIva }
            .filterKeys { it > 0.0 }
            .map { (_, lines) ->
                SaleTaxDto(
                    totalizarBaseRetencion = Money.fromDouble(lines.sumOf { it.itemTotalSinIva }).toDouble(),
                    codImpuestoIva = 1,
                    totalizarMontoIva2 =
                        Money
                            .fromDouble(lines.sumOf { it.itemTotalConIva - it.itemTotalSinIva })
                            .toDouble(),
                )
            }
    return SaleFinancialSnapshot(
        subtotalGross = Money.fromDouble(subtotal).toDouble(),
        itemDiscounts = Money.fromDouble(descuento).toDouble(),
        subtotalNet = Money.fromDouble((subtotal - descuento).coerceAtLeast(0.0)).toDouble(),
        tax = Money.fromDouble(impuesto).toDouble(),
        total = Money.fromDouble(total).toDouble(),
        taxLines = taxLines,
    )
}

interface TableAccountPaymentReader {
    val current: StateFlow<TableAccountPayment?>
}

interface TableAccountPaymentHolder : TableAccountPaymentReader {
    fun select(payment: TableAccountPayment)

    fun clear()
}

class InMemoryTableAccountPaymentHolder : TableAccountPaymentHolder {
    private val mutableCurrent = MutableStateFlow<TableAccountPayment?>(null)
    override val current: StateFlow<TableAccountPayment?> = mutableCurrent.asStateFlow()

    override fun select(payment: TableAccountPayment) {
        mutableCurrent.value = payment
    }

    override fun clear() {
        mutableCurrent.value = null
    }
}
