package com.amaxonia.pos.domain.usecase.pedidos

import com.amaxonia.pos.domain.model.Client
import com.amaxonia.pos.domain.model.SaleFinancialSnapshot
import com.amaxonia.pos.domain.model.caja.Caja
import com.amaxonia.pos.domain.model.computeFinancialSnapshot
import com.amaxonia.pos.domain.model.pedidos.CreatePedidoRequestDto
import com.amaxonia.pos.domain.model.pedidos.CreatePedidoResponseDto
import com.amaxonia.pos.domain.model.sales.SaleCurrencyDto
import com.amaxonia.pos.domain.model.sales.SaleInvoiceDto
import com.amaxonia.pos.domain.repository.ActiveCajaContext
import com.amaxonia.pos.domain.repository.CartRepository
import com.amaxonia.pos.domain.repository.PedidoRepository
import com.amaxonia.pos.domain.usecase.payment.BuildSaleItemsInput
import com.amaxonia.pos.domain.usecase.payment.BuildSaleItemsUseCase
import java.util.UUID

/**
 * Guarda el carrito actual como pedido en el ERP (equivalente a `pedido/save` del web).
 *
 * Las líneas y totales se calculan con [BuildSaleItemsUseCase] y el snapshot financiero del
 * carrito, la misma matemática que usa la factura, para que facturar el pedido después dé el
 * mismo total. La clave de idempotencia se genera por intento: reintentar tras un timeout con
 * el mismo [CreatePedidoAttempt] no duplica el pedido.
 */
class CreatePedidoFromCartUseCase(
    private val cart: CartRepository,
    private val cajaContext: ActiveCajaContext,
    private val pedidoRepository: PedidoRepository,
    private val buildSaleItems: BuildSaleItemsUseCase = BuildSaleItemsUseCase(),
) {
    suspend operator fun invoke(attempt: CreatePedidoAttempt = CreatePedidoAttempt()): Result<CreatePedidoResponseDto> {
        val request =
            runCatching { buildRequest(attempt.idPedido) }
                .getOrElse { return Result.failure(it) }
        return pedidoRepository.create(request)
    }

    internal fun buildRequest(idPedido: String): CreatePedidoRequestDto {
        val items = cart.cartItems.value
        require(items.isNotEmpty()) { "Agrega productos antes de guardar el pedido" }
        require(cart.linkedPedido.value == null) { "Este carrito ya pertenece a un pedido; cóbralo o vacía el carrito" }
        val client = cart.selectedClient.value
        require(client != null && client.id.isNotBlank()) { "Selecciona un cliente para el pedido" }
        val caja = requireNotNull(cajaContext.activeCaja()) { "Selecciona una caja antes de guardar el pedido" }
        require(items.all { it.product.id.toIntOrNull() != null }) { "Hay productos sin código válido en el carrito" }

        val sellerId =
            cart.currentSeller.value
                ?.id
                ?.takeIf { it > 0 }
                ?: cart.availableSellers.value
                    .firstOrNull()
                    ?.id
                ?: caja.defaultSellerId
                ?: DEFAULT_ID
        val warehouseId = caja.defaultWarehouseId ?: caja.codAlmacen?.takeIf { it > 0 } ?: DEFAULT_ID
        val saleItems =
            buildSaleItems(
                BuildSaleItemsInput(
                    cartItems = items,
                    warehouseId = warehouseId,
                    sellerId = sellerId,
                    defaultTaxRate = caja.defaultTaxRate?.takeIf { it > 0.0 } ?: 0.0,
                ),
            )
        val totals = cart.financialSnapshot.value ?: items.computeFinancialSnapshot()
        return CreatePedidoRequestDto(
            idPedido = idPedido,
            pedido = buildHeader(client, caja, sellerId, totals),
            items = saleItems,
            moneda = caja.currencyDto(totals.total),
        )
    }

    private fun buildHeader(
        client: Client,
        caja: Caja,
        sellerId: Int,
        totals: SaleFinancialSnapshot,
    ): SaleInvoiceDto {
        val branch = cart.selectedClientSucursal.value
        val secuencia = cajaContext.activeSecuencia()
        val sucursal = caja.idSucursal ?: DEFAULT_ID
        return SaleInvoiceDto(
            idCliente = client.id,
            codCliente = client.code.ifBlank { client.id },
            codVendedor = sellerId,
            idShop = sucursal,
            idSucursal = sucursal,
            idCaja = caja.idCaja,
            codigoCaja = caja.codCaja.orEmpty(),
            idCajaSecuencia = secuencia?.idCajaSecuencia.orEmpty().take(ID_MAX_LENGTH),
            serieSucursal = secuencia?.serieSucursal ?: caja.serieSucursal ?: caja.serieCaja,
            formaPago = "contado",
            codEstatus = 1,
            subtotal = totals.subtotalGross,
            descuentosItemFactura = totals.itemDiscounts,
            ivaTotalFactura = totals.tax,
            totalTotalFactura = totals.total,
            montoItemsFactura = totals.subtotalNet,
            totalizarSubTotal = totals.subtotalGross,
            totalizarDescuentoParcial = totals.itemDiscounts,
            totalizarTotalOperacion = totals.subtotalNet,
            totalizarBaseImponible = totals.subtotalNet,
            totalizarMontoIva = totals.tax,
            totalizarTotalGeneral = totals.total,
            usuarioCreacion = "",
            facturarA = "${client.firstName} ${client.lastName}".trim().ifBlank { "CONSUMIDOR FINAL" },
            facturarARuc = client.ruc.ifBlank { client.cedula.ifBlank { "CF" } },
            facturarADireccion = branch?.direccion?.takeIf(String::isNotBlank) ?: client.addressDetail,
            facturarATelefono = branch?.telefonoContacto?.takeIf(String::isNotBlank) ?: client.phone,
            clienteSucursalId = branch?.sucursalId,
            observacion = cart.observacion.value,
        )
    }

    private fun Caja.currencyDto(total: Double): SaleCurrencyDto? =
        currency?.let { config ->
            val multi = config.multiMoneda.equals("SI", ignoreCase = true)
            SaleCurrencyDto(
                multiMoneda = if (multi) "SI" else "NO",
                tasa = if (multi) config.tasa.takeIf { it > 0.0 } ?: 1.0 else 1.0,
                idTasa = if (multi) config.idTasa else 0,
                monedaBase = config.monedaBase,
                abrMonedaBase = config.abrMonedaBase,
                monedaSecundaria = config.monedaSecundaria,
                abrMonedaSecundaria = config.abrMonedaSecundaria,
                totalRef = total,
            )
        }

    private companion object {
        const val DEFAULT_ID = 1
        const val ID_MAX_LENGTH = 36
    }
}

/** Intento de alta de pedido; conservarlo entre reintentos mantiene la idempotencia. */
data class CreatePedidoAttempt(
    val idPedido: String = UUID.randomUUID().toString(),
)
