package com.amaxonia.pos.domain.usecase.pedidos

import com.amaxonia.pos.domain.model.Client
import com.amaxonia.pos.domain.model.PriceLevel
import com.amaxonia.pos.domain.model.Product
import com.amaxonia.pos.domain.model.caja.Caja
import com.amaxonia.pos.domain.model.caja.CajaSecuencia
import com.amaxonia.pos.domain.model.pedidos.CreatePedidoRequestDto
import com.amaxonia.pos.domain.model.pedidos.CreatePedidoResponseDto
import com.amaxonia.pos.domain.model.pedidos.LinkedPedido
import com.amaxonia.pos.domain.model.pedidos.PedidoDetailDto
import com.amaxonia.pos.domain.model.pedidos.PedidoSummaryDto
import com.amaxonia.pos.domain.repository.ActiveCajaContext
import com.amaxonia.pos.domain.repository.CartRepository
import com.amaxonia.pos.domain.repository.PedidoRepository
import com.amaxonia.pos.domain.repository.RestoredCartLine
import com.amaxonia.pos.domain.repository.restorePedidoLine
import com.amaxonia.pos.domain.repository.setClient
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PedidoCartUseCasesTest {
    private lateinit var cart: CartRepository
    private val product =
        Product(
            id = "42",
            code = "P42",
            description = "PRODUCTO",
            taxRate = 7.0,
            prices = listOf(PriceLevel(label = "A", price = 10.0, pricePlusTax = 10.70)),
        )
    private val caja =
        Caja(
            idCaja = "caja-1",
            codCaja = "CJ01",
            descripcion = "Caja 1",
            estatus = 1,
            idSucursal = 2,
            defaultWarehouseId = 3,
            serieCaja = "A01",
        )

    private class RecordingPedidoRepository : PedidoRepository {
        val created = mutableListOf<CreatePedidoRequestDto>()

        override suspend fun create(request: CreatePedidoRequestDto): Result<CreatePedidoResponseDto> {
            created += request
            return Result.success(CreatePedidoResponseDto(success = true, idPedido = request.idPedido, codPedido = "000025"))
        }

        override suspend fun pendientes(search: String?): Result<List<PedidoSummaryDto>> = Result.success(emptyList())

        override suspend fun detail(idPedido: String): Result<PedidoDetailDto> = Result.failure(UnsupportedOperationException())
    }

    private fun useCase(
        repository: PedidoRepository,
        activeCaja: Caja? = caja,
    ) = CreatePedidoFromCartUseCase(
        cart = cart,
        cajaContext =
            object : ActiveCajaContext {
                override fun activeCaja() = activeCaja

                override fun activeSecuencia(): CajaSecuencia? = null
            },
        pedidoRepository = repository,
    )

    @Before
    fun setUp() {
        cart = CartRepository()
    }

    @Test
    fun `guarda el carrito como pedido con la misma matematica de la venta`() =
        runBlocking {
            cart.setClient(Client(id = "client-1", code = "C0001", firstName = "Ana", lastName = "Pérez"))
            cart.addToCart(product, 2)
            val repository = RecordingPedidoRepository()

            val result = useCase(repository)(CreatePedidoAttempt("pedido-1"))

            assertTrue(result.isSuccess)
            val request = repository.created.single()
            assertEquals("pedido-1", request.idPedido)
            assertEquals("client-1", request.pedido.idCliente)
            assertEquals(3, request.items.single().itemAlmacen)
            assertEquals(2.0, request.items.single().itemCantidad, 0.0)
            assertEquals(cart.financialSnapshot.value!!.total, request.pedido.totalizarTotalGeneral, 0.0)
            assertEquals(21.40, request.pedido.totalizarTotalGeneral, 0.001)
        }

    @Test
    fun `sin cliente o sin caja no se guarda el pedido`() =
        runBlocking {
            cart.addToCart(product, 1)
            val repository = RecordingPedidoRepository()
            assertEquals("Selecciona un cliente para el pedido", useCase(repository)().exceptionOrNull()?.message)

            cart.setClient(Client(id = "client-1"))
            assertTrue(useCase(repository, activeCaja = null)().isFailure)
            assertTrue(repository.created.isEmpty())
        }

    @Test
    fun `un carrito que ya factura un pedido no se puede guardar como otro pedido`() =
        runBlocking {
            cart.setClient(Client(id = "client-1"))
            cart.addToCart(product, 1)
            cart.setLinkedPedido(LinkedPedido("p-1", "000010"))
            assertTrue(useCase(RecordingPedidoRepository())().isFailure)
        }

    @Test
    fun `restaurar lineas de pedido conserva precio, cantidad decimal y fusiona iguales`() {
        val line =
            RestoredCartLine(
                product = product,
                quantity = 1.5,
                unit = "UNIDAD",
                unitPriceWithTax = 9.63,
                discountPercent = 10.0,
                sellerId = 7,
            )
        cart.restorePedidoLine(line)
        cart.restorePedidoLine(line.copy(quantity = 0.5))

        val item = cart.cartItems.value.single()
        assertEquals(2.0, item.quantityDecimal, 0.0)
        assertEquals(9.63, item.unitPriceWithTax, 0.0)
        assertTrue("el precio del pedido no se recalcula por lista", item.isManualPrice)
        assertEquals(10.0, item.discountPercent, 0.0)
    }

    @Test
    fun `vaciar el carrito desvincula el pedido`() {
        cart.setLinkedPedido(LinkedPedido("p-1", "000010"))
        cart.clearCart()
        assertNull(cart.linkedPedido.value)
    }
}
