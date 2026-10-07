package com.amaxoniaerp.features.kiosk.route

import com.amaxoniaerp.features.caja.application.CajaSessionWorkflow
import com.amaxoniaerp.features.caja.data.CajaDetalleAperturaTable
import com.amaxoniaerp.features.caja.data.CajaDetalleCierreFormaPagoTable
import com.amaxoniaerp.features.caja.data.CajaDetalleCierreTable
import com.amaxoniaerp.features.caja.data.CajaSecuenciaTable
import com.amaxoniaerp.features.caja.data.ExposedCajaSessionStore
import com.amaxoniaerp.features.electronicinvoice.application.ElectronicInvoiceStrategy
import com.amaxoniaerp.features.electronicinvoice.application.ProcessorFactory
import com.amaxoniaerp.features.electronicinvoice.domain.ElectronicInvoiceResult
import com.amaxoniaerp.features.items.data.ItemsTablePA
import com.amaxoniaerp.features.kiosk.application.KioskService
import com.amaxoniaerp.features.kiosk.application.PlaceKioskOrderService
import com.amaxoniaerp.features.kiosk.application.UnlockRateLimiter
import com.amaxoniaerp.features.kiosk.data.KioskOrderItemModifierTable
import com.amaxoniaerp.features.kiosk.data.KioskOrderItemTable
import com.amaxoniaerp.features.kiosk.data.KioskOrderRepository
import com.amaxoniaerp.features.kiosk.data.KioskOrderTable
import com.amaxoniaerp.features.kiosk.domain.KioskPayResponse
import com.amaxoniaerp.features.kiosk.domain.KioskPaymentRequest
import com.amaxoniaerp.features.kiosk.route.KioskTestSupport.kioskClient
import com.amaxoniaerp.features.kiosk.route.KioskTestSupport.kioskHeaders
import com.amaxoniaerp.features.pos.data.CajaFormaPagoTable
import com.amaxoniaerp.features.sales.application.ProcessSaleUseCase
import com.amaxoniaerp.features.sales.data.ProcessSaleTransactionalRepository
import com.amaxoniaerp.features.sales.data.SalesFacturaTablePA
import com.amaxoniaerp.features.sales.domain.ProcessSaleRequest
import com.amaxoniaerp.features.sales.domain.ProcessSaleResponse
import com.zaxxer.hikari.HikariDataSource
import io.ktor.client.HttpClient
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Pago con tarjeta de un pedido de kiosco: apertura de caja, venta/facturación y despacho. */
class KioskPaymentRoutesTest {
    private lateinit var dataSource: HikariDataSource
    private lateinit var database: Database
    private lateinit var kioskService: KioskService
    private val json = KioskTestSupport.json
    private var lastSaleRequest: ProcessSaleRequest? = null

    private var feResultToReturn: ElectronicInvoiceResult =
        ElectronicInvoiceResult.Success(
            cufe = "CUFE-PANAMA-998877",
            qr = "https://dgi-fe.mef.gob.pa/consultas/facturas?cufe=CUFE-PANAMA-998877",
            fechaRecepcionDGI = "2026-10-05 14:00:00",
        )

    @BeforeTest
    fun setUp() {
        dataSource = KioskTestSupport.newDataSource("kiosk_pay")
        database = Database.connect(dataSource)

        val saleRepo =
            object : ProcessSaleTransactionalRepository() {
                override fun process(
                    countryCode: String,
                    request: ProcessSaleRequest,
                ): ProcessSaleResponse {
                    lastSaleRequest = request
                    return ProcessSaleResponse(
                        success = true,
                        idFactura = request.idFactura ?: "INV-TEST-001",
                        codFactura = "FAC-PA-1001",
                        codEstatus = 2,
                    )
                }
            }
        val strategy =
            object : ElectronicInvoiceStrategy {
                override val countryCode: String = KioskTestSupport.COUNTRY

                override suspend fun processElectronicInvoice(
                    database: Database,
                    invoiceId: String,
                ): ElectronicInvoiceResult = feResultToReturn
            }
        val feFactory =
            object : ProcessorFactory {
                override fun forCountry(countryCode: String): ElectronicInvoiceStrategy = strategy
            }

        val kioskOrderRepository = KioskOrderRepository()
        kioskService =
            KioskService(
                unlockRateLimiter = UnlockRateLimiter(),
                databaseResolver = { _, _ -> database },
                kioskOrderRepository = kioskOrderRepository,
                placeKioskOrderService =
                    PlaceKioskOrderService(
                        kioskOrderRepository = kioskOrderRepository,
                        cajaSessionWorkflow = CajaSessionWorkflow(ExposedCajaSessionStore()),
                        processSaleUseCase = ProcessSaleUseCase(saleRepo, feFactory),
                    ),
            )

        KioskTestSupport.createParametrosGenerales(database)
        KioskTestSupport.createCajaSchema(database)
        KioskTestSupport.createKioskOrderTables(database)
        transaction(database) {
            exec(
                """
                CREATE TABLE IF NOT EXISTS caja_nueva_detalle (
                    caja_detalle_id VARCHAR(36) PRIMARY KEY,
                    caja_id VARCHAR(36) NOT NULL,
                    id_forma_pago INT NULL,
                    monto DECIMAL(10,2) NULL,
                    monto_original DECIMAL(10,2) NULL
                );
                """.trimIndent(),
            )
            exec("CREATE TABLE IF NOT EXISTS caja_nueva (caja_id VARCHAR(36) PRIMARY KEY, id_factura VARCHAR(36) NOT NULL);")
            exec(
                """
                CREATE TABLE IF NOT EXISTS factura_detalle_formapago (
                    cod_factura_detalle_formapago VARCHAR(36) PRIMARY KEY,
                    id_factura VARCHAR(36) NOT NULL,
                    codigo_retencion INT NULL,
                    totalizar_monto_retencion DECIMAL(10,2) NULL,
                    totalizar_monto_cancelar DECIMAL(10,2) NULL,
                    totalizar_cambio DECIMAL(10,2) NOT NULL DEFAULT 0.00
                );
                """.trimIndent(),
            )
            SchemaUtils.create(
                CajaSecuenciaTable,
                CajaDetalleAperturaTable,
                CajaDetalleCierreTable,
                CajaDetalleCierreFormaPagoTable,
                CajaFormaPagoTable,
                ItemsTablePA,
                SalesFacturaTablePA,
            )
            ItemsTablePA.insert {
                it[idItem] = 101
                it[codItem] = "HAM-01"
                it[descripcion1] = "Hamburguesa Clásica"
                it[descripcion2] = "Carne y queso"
                it[codDepartamento] = 1
                it[departamentoId] = 1
                it[precio1] = BigDecimal("5.50")
                it[iva] = BigDecimal("7.00")
                it[montoExento] = false
                it[estatus] = "A"
            }
            CajaFormaPagoTable.insert {
                it[idFormaPago] = 2
                it[siglas] = "TDC"
                it[codigo] = 2
                it[descripcion] = "TARJETA DE CREDITO"
                it[pos] = 1
                it[activo] = 1
            }
            CajaFormaPagoTable.insert {
                it[idFormaPago] = 50
                it[siglas] = "TDC"
                it[codigo] = 35
                it[descripcion] = "MASTERCARD"
                it[formaPagoFact] = "03"
                it[pos] = 1
                it[activo] = 1
            }
            CajaFormaPagoTable.insert {
                it[idFormaPago] = 15
                it[siglas] = "CASH"
                it[codigo] = 1
                it[descripcion] = "EFECTIVO"
                it[pos] = 1
                it[activo] = 1
            }
        }
    }

    @AfterTest
    fun tearDown() {
        dataSource.close()
    }

    private fun insertTestOrder(
        orderId: String,
        estado: String = "COTIZADO",
        total: BigDecimal = BigDecimal("5.89"),
        expiresAt: LocalDateTime = LocalDateTime.now().plusMinutes(10),
        idDispositivo: String = KioskTestSupport.CAJA_ID,
    ) {
        transaction(database) {
            KioskOrderTable.insert {
                it[id] = orderId
                it[KioskOrderTable.idDispositivo] = idDispositivo
                it[numeroPedidoDiario] = 1
                it[codigoPedido] = "K1-001"
                it[fecha] = LocalDate.now()
                it[KioskOrderTable.estado] = estado
                it[modalidad] = "COMER_AQUI"
                it[portamesa] = "15"
                it[idCliente] = "CF"
                it[KioskOrderTable.total] = total.setScale(4, RoundingMode.HALF_UP)
                it[quoteExpiraEn] = expiresAt
                it[creadoEn] = LocalDateTime.now()
                it[actualizadoEn] = LocalDateTime.now()
            }
            KioskOrderItemTable.insert {
                it[idPedido] = orderId
                it[linea] = 1
                it[idItem] = 101
                it[cantidad] = BigDecimal.ONE.setScale(4, RoundingMode.HALF_UP)
                it[precioUnitario] = BigDecimal("5.50").setScale(4, RoundingMode.HALF_UP)
                it[nota] = "Sin cebolla"
            }
            KioskOrderItemModifierTable.insert {
                it[idPedido] = orderId
                it[linea] = 1
                it[idModificador] = 122
                it[nombre] = "SIN CEBOLLA"
                it[precioAdicional] = BigDecimal("0.0000")
            }
        }
    }

    private fun card(
        amount: String = "5.89",
        transactionId: String = "tx-123",
        paymentMethodId: Int? = null,
    ) = KioskPaymentRequest(
        transactionId = transactionId,
        authCode = "AUTH7788",
        reference = "REF9900",
        last4 = "4242",
        brand = "VISA",
        amount = amount,
        paymentMethodId = paymentMethodId,
    )

    private suspend fun HttpClient.pay(
        orderId: String,
        request: KioskPaymentRequest = card(),
    ): HttpResponse =
        post("/api/v1/kiosk/orders/$orderId/pay") {
            kioskHeaders()
            contentType(ContentType.Application.Json)
            setBody(request)
        }

    @Test
    fun `POST pay returns 200 OK with CUFE and QR when fiscal invoice succeeds`() =
        testApplication {
            val client = kioskClient(kioskService)
            val orderId = UUID.randomUUID().toString()
            insertTestOrder(orderId)

            val response = client.pay(orderId)

            assertEquals(HttpStatusCode.OK, response.status, response.bodyAsText())
            val payResp = json.decodeFromString<KioskPayResponse>(response.bodyAsText())
            assertEquals("K1-001", payResp.orderNumber)
            assertEquals("FACTURADO", payResp.status)
            assertEquals("RETIRO_MOSTRADOR", payResp.dispatch)
            assertEquals("FAC-PA-1001", payResp.invoice?.codFactura)
            assertEquals("CUFE-PANAMA-998877", payResp.invoice?.cufe)

            val receipt = payResp.receipt
            assertEquals("Multiplaza Mall", receipt.companyName)
            assertEquals("155688-1-554433", receipt.ruc)
            assertEquals("15", receipt.tableTent)
            assertEquals("5.89", receipt.total)
            assertEquals("VISA", receipt.paymentBrand)
            assertEquals("4242", receipt.paymentLast4)
            assertEquals(listOf("SIN CEBOLLA"), receipt.lines.single().modifiers)

            // La venta usa la caja, sucursal, almacén y vendedor resueltos desde la caja del header.
            val sale = assertNotNull(lastSaleRequest)
            assertEquals(KioskTestSupport.CAJA_ID, sale.factura.idCaja)
            assertEquals(1, sale.factura.idSucursal)
            assertEquals(KioskTestSupport.SELLER_ID, sale.factura.codVendedor)
            assertEquals("C01", sale.factura.codigoCaja)
            val saleItem = sale.items.single()
            assertEquals(3, saleItem.itemAlmacen)
            // Sin combos en la venta Kotlin: las opciones van en la descripción de la línea.
            assertEquals("Hamburguesa Clásica (SIN CEBOLLA)", saleItem.itemDescripcion)

            transaction(database) {
                val orderRow = KioskOrderTable.selectAll().where { KioskOrderTable.id eq orderId }.single()
                assertEquals("FACTURADO", orderRow[KioskOrderTable.estado])
                assertEquals("REF9900", orderRow[KioskOrderTable.pagoReferencia])
                assertEquals("AUTH7788", orderRow[KioskOrderTable.pagoAutorizacion])
                val secuencias = CajaSecuenciaTable.selectAll().where { CajaSecuenciaTable.idCaja eq KioskTestSupport.CAJA_ID }.toList()
                assertTrue(secuencias.isNotEmpty(), "Debe haber una secuencia de caja creada")
            }
        }

    @Test
    fun `POST pay registers the card payment method chosen on the kiosk`() =
        testApplication {
            val client = kioskClient(kioskService)
            val orderId = UUID.randomUUID().toString()
            insertTestOrder(orderId)

            val response = client.pay(orderId, card(paymentMethodId = 50))

            assertEquals(HttpStatusCode.OK, response.status, response.bodyAsText())
            val pago = assertNotNull(lastSaleRequest).pagos.single()
            assertEquals(50, pago.idFormaPago)
            assertEquals("ING", pago.tipoMovimiento)
        }

    @Test
    fun `POST pay without a chosen card keeps the default TDC payment method`() =
        testApplication {
            val client = kioskClient(kioskService)
            val orderId = UUID.randomUUID().toString()
            insertTestOrder(orderId)

            assertEquals(HttpStatusCode.OK, client.pay(orderId).status)
            assertEquals(2, assertNotNull(lastSaleRequest).pagos.single().idFormaPago)
        }

    @Test
    fun `POST pay rejects a payment method that is not a card option`() =
        testApplication {
            val client = kioskClient(kioskService)
            val orderId = UUID.randomUUID().toString()
            insertTestOrder(orderId)

            val response = client.pay(orderId, card(paymentMethodId = 15))

            assertEquals(HttpStatusCode.BadRequest, response.status)
            assertTrue(response.bodyAsText().contains("no válida"))
            assertNull(lastSaleRequest)
        }

    @Test
    fun `POST pay returns 202 Accepted with PAID_PENDING_INVOICE when fiscal invoice fails`() =
        testApplication {
            val client = kioskClient(kioskService)
            val orderId = UUID.randomUUID().toString()
            insertTestOrder(orderId)
            feResultToReturn = ElectronicInvoiceResult.Failure(codigo = "PAC_TIMEOUT", mensaje = "PAC no disponible tras 3 reintentos")

            val response = client.pay(orderId, card(transactionId = "tx-failure-1"))

            assertEquals(HttpStatusCode.Accepted, response.status)
            val payResp = json.decodeFromString<KioskPayResponse>(response.bodyAsText())
            assertEquals("PAID_PENDING_INVOICE", payResp.status)
            assertNull(payResp.invoice?.cufe)
            transaction(database) {
                val orderRow = KioskOrderTable.selectAll().where { KioskOrderTable.id eq orderId }.single()
                assertEquals("PAGADO_SIN_FACTURA", orderRow[KioskOrderTable.estado])
                assertTrue(orderRow[KioskOrderTable.motivoRechazo]!!.contains("PAC_TIMEOUT"))
            }
        }

    @Test
    fun `POST pay is idempotent when re-paying an already FACTURADO order`() =
        testApplication {
            val client = kioskClient(kioskService)
            val orderId = UUID.randomUUID().toString()
            insertTestOrder(orderId)

            assertEquals(HttpStatusCode.OK, client.pay(orderId).status)
            val second = client.pay(orderId)

            assertEquals(HttpStatusCode.OK, second.status)
            val body = json.decodeFromString<KioskPayResponse>(second.bodyAsText())
            assertEquals("FACTURADO", body.status)
            assertEquals("K1-001", body.orderNumber)
        }

    @Test
    fun `POST pay returns 400 Bad Request when payment amount does not match order total`() =
        testApplication {
            val client = kioskClient(kioskService)
            val orderId = UUID.randomUUID().toString()
            insertTestOrder(orderId)

            val response = client.pay(orderId, card(amount = "10.00"))

            assertEquals(HttpStatusCode.BadRequest, response.status)
            assertTrue(response.bodyAsText().contains("no coincide con el total del pedido"))
        }

    @Test
    fun `POST pay rejects an order quoted on another caja`() =
        testApplication {
            val client = kioskClient(kioskService)
            val orderId = UUID.randomUUID().toString()
            insertTestOrder(orderId, idDispositivo = "OTRA-CAJA")

            val response = client.pay(orderId)

            assertEquals(HttpStatusCode.BadRequest, response.status)
            assertTrue(response.bodyAsText().contains("no pertenece"))
        }

    @Test
    fun `POST pay returns 409 Conflict when order quote has expired`() =
        testApplication {
            val client = kioskClient(kioskService)
            val orderId = UUID.randomUUID().toString()
            insertTestOrder(orderId, expiresAt = LocalDateTime.now().minusMinutes(5))

            val response = client.pay(orderId, card(transactionId = "tx-expired"))

            assertEquals(HttpStatusCode.Conflict, response.status)
            assertTrue(response.bodyAsText().contains("ha expirado"))
        }

    @Test
    fun `POST pay answers 503 when the tenant has no kiosk order tables`() =
        testApplication {
            transaction(database) { SchemaUtils.drop(KioskOrderItemModifierTable, KioskOrderItemTable, KioskOrderTable) }
            val response = kioskClient(kioskService).pay(UUID.randomUUID().toString())

            assertEquals(HttpStatusCode.ServiceUnavailable, response.status)
            assertTrue(response.bodyAsText().contains("falta migración"))
        }
}
