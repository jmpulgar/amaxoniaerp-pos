package com.amaxoniaerp.features.kiosk.application.dispatch

import com.amaxoniaerp.features.kiosk.domain.KioskOrderItemRecord
import com.amaxoniaerp.features.kiosk.domain.KioskOrderModifierRecord
import com.amaxoniaerp.features.kiosk.domain.KioskOrderRecord
import kotlinx.coroutines.runBlocking
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class OrderDispatchPolicyTest {

    private fun createSampleOrder(
        orderNumber: String = "K1-042",
        modalidad: String = "COMER_AQUI",
        portamesa: String? = null,
    ): KioskOrderRecord {
        return KioskOrderRecord(
            id = "order-test-uuid-1",
            idDispositivo = "device-test-uuid-1",
            numeroPedidoDiario = 42,
            codigoPedido = orderNumber,
            fecha = LocalDate.of(2026, 10, 5),
            estado = "COTIZADO",
            modalidad = modalidad,
            portamesa = portamesa,
            idCliente = "CF",
            total = BigDecimal("12.50"),
            quoteExpiraEn = LocalDateTime.now().plusMinutes(10),
            pagoReferencia = null,
            pagoAutorizacion = null,
            pagoUltimos4 = null,
            pagoMarca = null,
            idFactura = null,
            motivoRechazo = null,
            creadoEn = LocalDateTime.now(),
            actualizadoEn = LocalDateTime.now(),
            items = listOf(
                KioskOrderItemRecord(
                    idPedido = "order-test-uuid-1",
                    linea = 1,
                    idItem = 101,
                    cantidad = BigDecimal("2.0000"),
                    precioUnitario = BigDecimal("5.5000"),
                    nota = "Sin pepinillos",
                    modifiers = listOf(
                        KioskOrderModifierRecord(
                            idPedido = "order-test-uuid-1",
                            linea = 1,
                            idModificador = 1,
                            nombre = "Extra Queso",
                            precioAdicional = BigDecimal("0.7500"),
                        ),
                        KioskOrderModifierRecord(
                            idPedido = "order-test-uuid-1",
                            linea = 1,
                            idModificador = 2,
                            nombre = "Bacon",
                            precioAdicional = BigDecimal("1.0000"),
                        ),
                    ),
                ),
                KioskOrderItemRecord(
                    idPedido = "order-test-uuid-1",
                    linea = 2,
                    idItem = 202,
                    cantidad = BigDecimal("1.0000"),
                    precioUnitario = BigDecimal("2.0000"),
                    nota = null,
                    modifiers = emptyList(),
                ),
            ),
        )
    }

    // ─── 1. Retiro en Mostrador ───────────────────────────────────────────────

    @Test
    fun `CounterPickupDispatchPolicy produces CounterPickup result with order number`() = runBlocking {
        val policy = CounterPickupDispatchPolicy()
        val order = createSampleOrder(orderNumber = "K1-005")

        val result = policy.dispatch(order)

        assertTrue(result.success)
        assertEquals(KioskDispatchDestination.RETIRO_MOSTRADOR, result.destination)
        assertEquals("K1-005", result.orderNumber)
        assertIs<DispatchResult.CounterPickup>(result)
        assertTrue(result.message.contains("K1-005"))
    }

    // ─── 2. Servicio a Mesas ──────────────────────────────────────────────────

    @Test
    fun `TableDeliveryDispatchPolicy succeeds when portamesa is provided`() = runBlocking {
        val policy = TableDeliveryDispatchPolicy()
        val order = createSampleOrder(orderNumber = "K1-010", portamesa = "Mesa 14")

        val result = policy.dispatch(order)

        assertTrue(result.success)
        assertEquals(KioskDispatchDestination.MESAS, result.destination)
        assertIs<DispatchResult.TableDelivery>(result)
        assertEquals("Mesa 14", result.tableTent)
        assertTrue(result.message.contains("Mesa 14"))
    }

    @Test
    fun `TableDeliveryDispatchPolicy fails when portamesa is null or blank`() = runBlocking {
        val policy = TableDeliveryDispatchPolicy()
        val order = createSampleOrder(orderNumber = "K1-011", portamesa = null)

        val result = policy.dispatch(order)

        assertFalse(result.success)
        assertEquals(KioskDispatchDestination.MESAS, result.destination)
        assertIs<DispatchResult.Failure>(result)
        assertTrue(result.errorMessage.contains("número de mesa o portamesa"))
    }

    // ─── 3. Impresora de Cocina LAN ESC/POS ────────────────────────────────────

    private class RecordingKitchenPrinterClient : KitchenPrinterClient {
        var lastIp: String? = null
        var lastPort: Int? = null
        var lastBytes: ByteArray? = null
        var shouldSucceed: Boolean = true
        var failureMessage: String = "Printer unreachable"

        override suspend fun sendBytes(ip: String, port: Int, bytes: ByteArray): Result<Unit> {
            lastIp = ip
            lastPort = port
            lastBytes = bytes
            return if (shouldSucceed) {
                Result.success(Unit)
            } else {
                Result.failure(RuntimeException(failureMessage))
            }
        }
    }

    @Test
    fun `KitchenPrinterDispatchPolicy generates ESC-POS ticket and sends to client`() = runBlocking {
        val fakeClient = RecordingKitchenPrinterClient().apply { shouldSucceed = true }
        val policy = KitchenPrinterDispatchPolicy(printerClient = fakeClient)
        val order = createSampleOrder(orderNumber = "K1-042", portamesa = "8")
        val config = KioskDispatchConfig(kitchenPrinterIp = "192.168.1.200", kitchenPrinterPort = 9100)
        val itemNames = mapOf(101 to "Hamburguesa Doble", 202 to "Papas Fritas")

        val result = policy.dispatch(order, config, itemNames)

        assertTrue(result.success)
        assertEquals(KioskDispatchDestination.IMPRESORA_COCINA, result.destination)
        assertIs<DispatchResult.KitchenPrinter>(result)
        assertTrue(result.printedSuccessfully)
        assertEquals("192.168.1.200", result.printerIp)
        assertEquals(9100, result.port)
        assertTrue(result.bytesCount > 0)

        // Verificar datos enviados por socket
        assertEquals("192.168.1.200", fakeClient.lastIp)
        assertEquals(9100, fakeClient.lastPort)
        val sentBytes = fakeClient.lastBytes
        assertTrue(sentBytes != null && sentBytes.isNotEmpty())

        val ticketText = String(sentBytes, Charsets.ISO_8859_1)
        assertTrue(ticketText.contains("K1-042"))
        assertTrue(ticketText.contains("Hamburguesa Doble"))
        assertTrue(ticketText.contains("Extra Queso"))
        assertTrue(ticketText.contains("Sin pepinillos"))
        assertTrue(ticketText.contains("Papas Fritas"))
        assertTrue(ticketText.contains("PORTAMESA / MESA: 8"))
    }

    @Test
    fun `KitchenPrinterDispatchPolicy handles printer connection failure gracefully`() = runBlocking {
        val fakeClient = RecordingKitchenPrinterClient().apply {
            shouldSucceed = false
            failureMessage = "Connection refused to 192.168.1.200:9100"
        }
        val policy = KitchenPrinterDispatchPolicy(printerClient = fakeClient)
        val order = createSampleOrder(orderNumber = "K1-043")
        val config = KioskDispatchConfig(kitchenPrinterIp = "192.168.1.200")

        val result = policy.dispatch(order, config)

        assertFalse(result.success)
        assertIs<DispatchResult.KitchenPrinter>(result)
        assertFalse(result.printedSuccessfully)
        assertEquals("192.168.1.200", result.printerIp)
        assertTrue(result.errorMessage!!.contains("Connection refused"))
    }

    @Test
    fun `KitchenPrinterDispatchPolicy reports error when printer IP is unconfigured`() = runBlocking {
        val fakeClient = RecordingKitchenPrinterClient()
        val policy = KitchenPrinterDispatchPolicy(printerClient = fakeClient)
        val order = createSampleOrder(orderNumber = "K1-044")
        val config = KioskDispatchConfig(kitchenPrinterIp = null)

        val result = policy.dispatch(order, config)

        assertFalse(result.success)
        assertIs<DispatchResult.KitchenPrinter>(result)
        assertFalse(result.printedSuccessfully)
        assertEquals(null, result.printerIp)
        assertTrue(result.errorMessage!!.contains("IP de impresora de cocina no configurada"))
    }

    // ─── 4. EscPosKitchenTicketBuilder ────────────────────────────────────────

    @Test
    fun `EscPosKitchenTicketBuilder builds proper commands and line structure`() {
        val order = createSampleOrder(orderNumber = "K1-099", modalidad = "PARA_LLEVAR")
        val itemNames = mapOf(101 to "Hamburguesa Clásica", 202 to "Refresco")

        val bytes = EscPosKitchenTicketBuilder.buildTicket(order, itemNames)

        assertTrue(bytes.isNotEmpty())

        // Check ESC @ init (0x1B, 0x40)
        assertEquals(0x1B.toByte(), bytes[0])
        assertEquals(0x40.toByte(), bytes[1])

        val text = String(bytes, Charsets.ISO_8859_1)
        assertTrue(text.contains("*** COMANDA DE COCINA ***"))
        assertTrue(text.contains("K1-099"))
        assertTrue(text.contains("PARA LLEVAR"))
        assertTrue(text.contains("Hamburguesa Clásica"))
        assertTrue(text.contains("+ Extra Queso"))
        assertTrue(text.contains("+ Bacon"))
        assertTrue(text.contains("* NOTA: Sin pepinillos"))
        assertTrue(text.contains("Refresco"))
    }

    // ─── 5. OrderDispatchPolicyFactory ────────────────────────────────────────

    @Test
    fun `OrderDispatchPolicyFactory resolves correct policy for each destination`() {
        val factory = OrderDispatchPolicyFactory()

        val p1 = factory.getPolicy("RETIRO_MOSTRADOR")
        assertEquals(KioskDispatchDestination.RETIRO_MOSTRADOR, p1.destination)
        assertIs<CounterPickupDispatchPolicy>(p1)

        val p2 = factory.getPolicy("IMPRESORA_COCINA")
        assertEquals(KioskDispatchDestination.IMPRESORA_COCINA, p2.destination)
        assertIs<KitchenPrinterDispatchPolicy>(p2)

        val p3 = factory.getPolicy("MESAS")
        assertEquals(KioskDispatchDestination.MESAS, p3.destination)
        assertIs<TableDeliveryDispatchPolicy>(p3)

        val pDefault = factory.getPolicy(null)
        assertEquals(KioskDispatchDestination.RETIRO_MOSTRADOR, pDefault.destination)
        assertIs<CounterPickupDispatchPolicy>(pDefault)

        val pUnknown = factory.getPolicy("DESCONOCIDO")
        assertEquals(KioskDispatchDestination.RETIRO_MOSTRADOR, pUnknown.destination)
        assertIs<CounterPickupDispatchPolicy>(pUnknown)
    }
}
