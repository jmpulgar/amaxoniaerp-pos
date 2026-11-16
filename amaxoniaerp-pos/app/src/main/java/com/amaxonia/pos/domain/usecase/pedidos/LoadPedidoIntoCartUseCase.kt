package com.amaxonia.pos.domain.usecase.pedidos

import com.amaxonia.pos.domain.model.Client
import com.amaxonia.pos.domain.model.PriceLevel
import com.amaxonia.pos.domain.model.Product
import com.amaxonia.pos.domain.model.pedidos.LinkedPedido
import com.amaxonia.pos.domain.model.pedidos.PedidoDetailDto
import com.amaxonia.pos.domain.model.pedidos.PedidoEstatus
import com.amaxonia.pos.domain.model.pedidos.PedidoLineDto
import com.amaxonia.pos.domain.repository.CartRepository
import com.amaxonia.pos.domain.repository.ClientRepository
import com.amaxonia.pos.domain.repository.PedidoRepository
import com.amaxonia.pos.domain.repository.ProductRepository
import com.amaxonia.pos.domain.repository.RestoredCartLine
import com.amaxonia.pos.domain.repository.restorePedidoLine
import com.amaxonia.pos.domain.repository.setClient
import com.amaxonia.pos.domain.repository.setCurrentSeller
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Carga un pedido pendiente en el carrito para facturarlo por el flujo normal de cobro
 * (formas de pago, crédito, factura electrónica e impresión). El carrito queda enlazado al
 * pedido ([LinkedPedido]); al procesar la venta el backend lo marca facturado.
 *
 * Como en el web, solo se cargan las líneas con cantidad pendiente (`_cantidad_restante`).
 */
class LoadPedidoIntoCartUseCase(
    private val pedidoRepository: PedidoRepository,
    private val productRepository: ProductRepository,
    private val clientRepository: ClientRepository,
    private val cart: CartRepository,
) {
    suspend operator fun invoke(idPedido: String): Result<LinkedPedido> =
        pedidoRepository.detail(idPedido).mapCatching { detail ->
            val lines = detail.items.filter { it.cantidadPendiente > 0.0 }
            check(detail.pedido.codEstatus == PedidoEstatus.PENDIENTE) {
                "El pedido ${detail.pedido.codPedido} ya no está pendiente"
            }
            check(lines.isNotEmpty()) { "El pedido ${detail.pedido.codPedido} no tiene productos pendientes por facturar" }
            restore(detail, lines)
        }

    private suspend fun restore(
        detail: PedidoDetailDto,
        lines: List<PedidoLineDto>,
    ): LinkedPedido {
        val client = resolveClient(detail)
        cart.clearCart()
        cart.setClient(client)
        cart.availableSellers.value
            .firstOrNull { it.id == detail.pedido.codVendedor }
            ?.let(cart::setCurrentSeller)
        lines.forEach { line ->
            cart.restorePedidoLine(
                RestoredCartLine(
                    product = resolveProduct(line),
                    quantity = line.cantidadPendiente,
                    unit = if (line.itemUnidadEmpaque.equals("EMPAQUE", ignoreCase = true)) "EMPAQUE" else "UNIDAD",
                    unitPriceWithTax = unitPriceWithTax(line),
                    discountPercent = line.descuentoPorcentaje,
                    sellerId = detail.pedido.codVendedor,
                ),
            )
        }
        cart.setObservacion(detail.pedido.observacion)
        val linked = LinkedPedido(detail.pedido.idPedido, detail.pedido.codPedido)
        cart.setLinkedPedido(linked)
        return linked
    }

    private suspend fun resolveClient(detail: PedidoDetailDto): Client =
        clientRepository.getClientById(detail.pedido.idCliente).getOrNull()
            ?: Client(
                id = detail.pedido.idCliente,
                code = detail.pedido.codCliente,
                firstName = detail.pedido.cliente,
            )

    /** Producto del catálogo local; si no está sincronizado se reconstruye desde la línea. */
    private suspend fun resolveProduct(line: PedidoLineDto): Product =
        productRepository.getProductById(line.idItem.toString()).getOrNull()
            ?: Product(
                id = line.idItem.toString(),
                code = line.codItem,
                description = line.descripcion,
                isExempt = line.porcentajeIva <= 0.0,
                taxRate = line.porcentajeIva,
                unitPackage = line.unidadEmpaque,
                bulkQuantity = line.cantidadBulto.toDouble().coerceAtLeast(1.0),
                prices = listOf(PriceLevel(label = "A", pricePlusTax = unitPriceWithTax(line))),
            )

    private fun unitPriceWithTax(line: PedidoLineDto): Double =
        BigDecimal
            .valueOf(line.precioSinIva)
            .multiply(BigDecimal.ONE + BigDecimal.valueOf(line.porcentajeIva).movePointLeft(2))
            .setScale(2, RoundingMode.HALF_UP)
            .toDouble()
}
