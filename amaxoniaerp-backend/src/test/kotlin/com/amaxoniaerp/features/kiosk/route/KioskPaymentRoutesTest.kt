package com.amaxoniaerp.features.kiosk.route

import com.amaxoniaerp.JwtConfig
import com.amaxoniaerp.features.caja.application.CajaSessionWorkflow
import com.amaxoniaerp.features.caja.data.CajaDetalleAperturaTable
import com.amaxoniaerp.features.caja.data.CajaDetalleCierreFormaPagoTable
import com.amaxoniaerp.features.caja.data.CajaDetalleCierreTable
import com.amaxoniaerp.features.caja.data.CajaSecuenciaTable
import com.amaxoniaerp.features.caja.data.CajaTablePA
import com.amaxoniaerp.features.caja.data.ExposedCajaSessionStore
import com.amaxoniaerp.features.caja.data.SucursalTable
import com.amaxoniaerp.features.clients.data.ClientsTable
import com.amaxoniaerp.features.electronicinvoice.application.ElectronicInvoiceStrategy
import com.amaxoniaerp.features.electronicinvoice.application.ProcessorFactory
import com.amaxoniaerp.features.electronicinvoice.domain.ElectronicInvoiceResult
import com.amaxoniaerp.features.items.data.ItemsTablePA
import com.amaxoniaerp.features.kiosk.application.KioskService
import com.amaxoniaerp.features.kiosk.application.PlaceKioskOrderService
import com.amaxoniaerp.features.kiosk.application.UnlockRateLimiter
import com.amaxoniaerp.features.kiosk.data.ItemModifierGroupTable
import com.amaxoniaerp.features.kiosk.data.ItemModifierRelationTable
import com.amaxoniaerp.features.kiosk.data.ItemModifierTable
import com.amaxoniaerp.features.kiosk.data.KioskCatalogRepository
import com.amaxoniaerp.features.kiosk.data.KioskDeviceRepository
import com.amaxoniaerp.features.kiosk.data.KioskDeviceTable
import com.amaxoniaerp.features.kiosk.data.KioskOrderItemModifierTable
import com.amaxoniaerp.features.kiosk.data.KioskOrderItemTable
import com.amaxoniaerp.features.kiosk.data.KioskOrderRepository
import com.amaxoniaerp.features.kiosk.data.KioskOrderTable
import com.amaxoniaerp.features.kiosk.domain.KioskPayResponse
import com.amaxoniaerp.features.kiosk.domain.KioskPaymentRequest
import com.amaxoniaerp.features.sales.application.ProcessSaleUseCase
import com.amaxoniaerp.features.sales.data.ProcessSaleTransactionalRepository
import com.amaxoniaerp.features.sales.data.SalesFacturaTablePA
import com.amaxoniaerp.features.sales.domain.ProcessSaleRequest
import com.amaxoniaerp.features.sales.domain.ProcessSaleResponse
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
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

class KioskPaymentRoutesTest {
    private lateinit var dataSource: HikariDataSource
    private lateinit var database: Database

    private val jwtConfig =
        JwtConfig(
            secret = "test-secret-kiosk-test-must-be-very-long-32-chars",
            domain = "http://localhost:8080",
            audience = "http://localhost:8080/kiosk",
            realm = "Amaxonia Kiosk Test",
        )

    private val json =
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = false
            explicitNulls = false
        }

    private val kioskDeviceRepository = KioskDeviceRepository()
    private val kioskCatalogRepository = KioskCatalogRepository()
    private val kioskOrderRepository = KioskOrderRepository()
    private val unlockRateLimiter = UnlockRateLimiter()

    private val testCountry = "PA"
    private val testCompanyDb = "momi_pa"
    private val testDeviceId = UUID.randomUUID().toString()
    private val testCajaId = "CAJA-01"

    private var feResultToReturn: ElectronicInvoiceResult =
        ElectronicInvoiceResult.Success(
            cufe = "CUFE-PANAMA-998877",
            qr = "https://dgi-fe.mef.gob.pa/consultas/facturas?cufe=CUFE-PANAMA-998877",
            fechaRecepcionDGI = "2026-10-05 14:00:00",
        )

    private lateinit var processSaleUseCase: ProcessSaleUseCase
    private lateinit var placeKioskOrderService: PlaceKioskOrderService
    private lateinit var kioskService: KioskService

    @BeforeTest
    fun setUp() {
        dataSource =
            HikariDataSource(
                HikariConfig().apply {
                    jdbcUrl = "jdbc:h2:mem:kiosk_pay_${System.nanoTime()};MODE=MySQL;DB_CLOSE_DELAY=-1"
                    driverClassName = "org.h2.Driver"
                    maximumPoolSize = 2
                    isAutoCommit = false
                },
            )
        database = Database.connect(dataSource)

        val saleRepo =
            object : ProcessSaleTransactionalRepository() {
                override fun process(
                    countryCode: String,
                    request: ProcessSaleRequest,
                ): ProcessSaleResponse {
                    val invId = request.idFactura ?: "INV-TEST-001"
                    return ProcessSaleResponse(
                        success = true,
                        idFactura = invId,
                        codFactura = "FAC-PA-1001",
                        codEstatus = 2,
                    )
                }
            }

        val strategy =
            object : ElectronicInvoiceStrategy {
                override val countryCode: String = testCountry

                override suspend fun processElectronicInvoice(
                    database: Database,
                    invoiceId: String,
                ): ElectronicInvoiceResult = feResultToReturn
            }

        val feFactory =
            object : ProcessorFactory {
                override fun forCountry(countryCode: String): ElectronicInvoiceStrategy = strategy
            }

        processSaleUseCase = ProcessSaleUseCase(saleRepo, feFactory)
        val cajaWorkflow = CajaSessionWorkflow(ExposedCajaSessionStore())
        placeKioskOrderService =
            PlaceKioskOrderService(
                kioskOrderRepository = kioskOrderRepository,
                cajaSessionWorkflow = cajaWorkflow,
                processSaleUseCase = processSaleUseCase,
            )

        kioskService =
            KioskService(
                kioskDeviceRepository = kioskDeviceRepository,
                unlockRateLimiter = unlockRateLimiter,
                jwtConfig = jwtConfig,
                databaseResolver = { _, _ -> database },
                kioskCatalogRepository = kioskCatalogRepository,
                kioskOrderRepository = kioskOrderRepository,
                placeKioskOrderService = placeKioskOrderService,
            )

        transaction(database) {
            exec(
                """
                CREATE TABLE IF NOT EXISTS parametros_generales (
                    cod_empresa INT PRIMARY KEY,
                    default_cod_cliente_factura VARCHAR(80) NOT NULL DEFAULT 'CF',
                    default_id_formapago_factura INT NOT NULL DEFAULT 1,
                    porcentaje_impuesto_principal DECIMAL(10,2) NOT NULL DEFAULT 7.00,
                    validar_stock VARCHAR(2) NOT NULL DEFAULT 'SI',
                    dias_vencimiento INT NOT NULL DEFAULT 30,
                    cod_almacen INT NOT NULL DEFAULT 1,
                    rif VARCHAR(50) NULL,
                    moneda VARCHAR(50) NOT NULL DEFAULT 'USD',
                    moneda_base INT NULL,
                    bloquear_itbms VARCHAR(2) NOT NULL DEFAULT 'NO',
                    facturar_cero TINYINT(1) NOT NULL DEFAULT 0,
                    impresion_directa TINYINT(1) NOT NULL DEFAULT 0,
                    tipo_facturacion INT NOT NULL DEFAULT 0,
                    kiosco_destino_pedido VARCHAR(30) NOT NULL DEFAULT 'RETIRO_MOSTRADOR',
                    kiosco_impresora_cocina_ip VARCHAR(45) NULL,
                    kiosco_modalidades VARCHAR(30) NOT NULL DEFAULT 'COMER_AQUI,PARA_LLEVAR',
                    kiosco_color_marca CHAR(7) NULL,
                    kiosco_config_version INT NOT NULL DEFAULT 1,
                    clave_kiosko VARCHAR(255) NULL
                );
                """.trimIndent(),
            )
            exec(
                "INSERT INTO parametros_generales (cod_empresa, validar_stock, porcentaje_impuesto_principal, rif) " +
                    "VALUES (1, 'SI', 7.00, '155688-1-554433');",
            )

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
            exec(
                """
                CREATE TABLE IF NOT EXISTS caja_nueva (
                    caja_id VARCHAR(36) PRIMARY KEY,
                    id_factura VARCHAR(36) NOT NULL
                );
                """.trimIndent(),
            )
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
                KioskDeviceTable,
                SucursalTable,
                ClientsTable,
                CajaTablePA,
                CajaSecuenciaTable,
                CajaDetalleAperturaTable,
                CajaDetalleCierreTable,
                CajaDetalleCierreFormaPagoTable,
                com.amaxoniaerp.features.pos.data.CajaFormaPagoTable,
                ItemsTablePA,
                ItemModifierGroupTable,
                ItemModifierTable,
                ItemModifierRelationTable,
                KioskOrderTable,
                KioskOrderItemTable,
                KioskOrderItemModifierTable,
                SalesFacturaTablePA,
            )

            // Kiosk Device
            KioskDeviceTable.insert {
                it[id] = testDeviceId
                it[nombre] = "Kiosco Multiplaza"
                it[prefijoPedido] = "K1"
                it[idCaja] = testCajaId
                it[idSucursal] = 1
                it[idAlmacen] = 1
                it[codVendedor] = 10
                it[idClienteGenerico] = "CF"
                it[activo] = true
                it[creadoEn] = LocalDateTime.now()
            }

            // Sucursal
            SucursalTable.insert {
                it[idSucursal] = 1
                it[codigo] = "SUC-01"
                it[serie] = "01"
                it[sucursal] = "Multiplaza Mall"
                it[descripcion] = "Vía Israel, Ciudad de Panamá"
            }

            // Caja
            CajaTablePA.insert {
                it[idCaja] = testCajaId
                it[codCaja] = "C01"
                it[serieCaja] = "01"
                it[descripcion] = "Caja Kiosco 1"
                it[idSucursal] = 1
            }

            // Cliente CF
            ClientsTable.insert {
                it[idCliente] = "CF"
                it[codCliente] = "CF"
                it[rif] = "CF"
                it[dv] = "0"
                it[nombre] = "CONSUMIDOR FINAL"
                it[direccion] = "Panamá"
            }

            // Item 101: Hamburguesa ($5.50 + 7% = $5.885 -> total con tax)
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

            com.amaxoniaerp.features.pos.data.CajaFormaPagoTable.insert {
                it[idFormaPago] = 2
                it[siglas] = "TDC"
                it[codigo] = 2
                it[descripcion] = "TARJETA DE CREDITO"
                it[pos] = 1
                it[activo] = 1
            }
        }
    }

    @AfterTest
    fun tearDown() {
        dataSource.close()
    }

    private fun generateKioskToken(deviceId: String = testDeviceId): String =
        JWT
            .create()
            .withIssuer(jwtConfig.domain)
            .withAudience(jwtConfig.audience)
            .withClaim("token_type", "kiosk")
            .withClaim("role", "KIOSK")
            .withClaim("device_id", deviceId)
            .withClaim("device_name", "Kiosco Multiplaza")
            .withClaim("country_code", testCountry)
            .withClaim("admin_db", testCompanyDb)
            .withClaim("company_db", testCompanyDb)
            .withClaim("prefix", "K1")
            .withClaim("box_id", testCajaId)
            .withClaim("branch_id", 1)
            .withClaim("warehouse_id", 1)
            .withClaim("seller_code", 10)
            .withClaim("customer_id", "CF")
            .sign(Algorithm.HMAC256(jwtConfig.secret))

    private fun ApplicationTestBuilder.configureKioskApp() {
        install(io.ktor.server.plugins.contentnegotiation.ContentNegotiation) {
            json(json)
        }
        install(io.ktor.server.auth.Authentication) {
            jwt {
                realm = jwtConfig.realm ?: "Amaxonia Kiosk"
                verifier(
                    JWT
                        .require(Algorithm.HMAC256(jwtConfig.secret))
                        .withAudience(jwtConfig.audience)
                        .withIssuer(jwtConfig.domain)
                        .build(),
                )
                validate { credential ->
                    if (credential.payload.getClaim("token_type").asString() == "kiosk") {
                        JWTPrincipal(credential.payload)
                    } else {
                        null
                    }
                }
            }
        }
        routing {
            kioskRoutes(kioskService)
        }
    }

    private fun insertTestOrder(
        orderId: String,
        estado: String = "COTIZADO",
        total: BigDecimal = BigDecimal("5.89"),
        expiresAt: LocalDateTime = LocalDateTime.now().plusMinutes(10),
    ) {
        transaction(database) {
            KioskOrderTable.insert {
                it[id] = orderId
                it[idDispositivo] = testDeviceId
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
        }
    }

    @Test
    fun `POST pay returns 200 OK with CUFE and QR when fiscal invoice succeeds`() =
        testApplication {
            configureKioskApp()
            val client =
                createClient {
                    install(ContentNegotiation) { json(json) }
                }

            val orderId = UUID.randomUUID().toString()
            insertTestOrder(orderId, estado = "COTIZADO", total = BigDecimal("5.89"))

            feResultToReturn =
                ElectronicInvoiceResult.Success(
                    cufe = "CUFE-PANAMA-123456",
                    qr = "https://dgi-fe.mef.gob.pa/consultas/facturas?cufe=CUFE-PANAMA-123456",
                    fechaRecepcionDGI = "2026-10-05 14:30:00",
                )

            val paymentRequest =
                KioskPaymentRequest(
                    transactionId = "tx-123",
                    authCode = "AUTH7788",
                    reference = "REF9900",
                    last4 = "4242",
                    brand = "VISA",
                    amount = "5.89",
                )

            val response =
                client.post("/api/v1/kiosk/orders/$orderId/pay") {
                    header(HttpHeaders.Authorization, "Bearer ${generateKioskToken()}")
                    contentType(ContentType.Application.Json)
                    setBody(paymentRequest)
                }

            assertEquals(HttpStatusCode.OK, response.status)

            val payResp = json.decodeFromString<KioskPayResponse>(response.bodyAsText())
            assertEquals("K1-001", payResp.orderNumber)
            assertEquals("FACTURADO", payResp.status)
            assertEquals("RETIRO_MOSTRADOR", payResp.dispatch)
            assertNotNull(payResp.invoice)
            assertEquals("FAC-PA-1001", payResp.invoice?.codFactura)
            assertEquals("CUFE-PANAMA-123456", payResp.invoice?.cufe)
            assertEquals("https://dgi-fe.mef.gob.pa/consultas/facturas?cufe=CUFE-PANAMA-123456", payResp.invoice?.qr)

            // Verificación de recibo
            val receipt = payResp.receipt
            assertEquals("Multiplaza Mall", receipt.companyName)
            assertEquals("155688-1-554433", receipt.ruc)
            assertEquals("K1-001", receipt.orderNumber)
            assertEquals("COMER_AQUI", receipt.diningMode)
            assertEquals("15", receipt.tableTent)
            assertEquals("5.89", receipt.total)
            assertEquals("VISA", receipt.paymentBrand)
            assertEquals("4242", receipt.paymentLast4)
            assertEquals("AUTH7788", receipt.paymentAuthCode)

            // Verificar estado persistido en DB
            transaction(database) {
                val orderRow = KioskOrderTable.selectAll().where { KioskOrderTable.id eq orderId }.single()
                assertEquals("FACTURADO", orderRow[KioskOrderTable.estado])
                assertEquals("REF9900", orderRow[KioskOrderTable.pagoReferencia])
                assertEquals("AUTH7788", orderRow[KioskOrderTable.pagoAutorizacion])
                assertEquals("4242", orderRow[KioskOrderTable.pagoUltimos4])
                assertEquals("VISA", orderRow[KioskOrderTable.pagoMarca])

                // Verificar que se abrió una secuencia de caja para hoy
                val secuencias = CajaSecuenciaTable.selectAll().where { CajaSecuenciaTable.idCaja eq testCajaId }.toList()
                assertTrue(secuencias.isNotEmpty(), "Debe haber una secuencia de caja creada")
            }
        }

    @Test
    fun `POST pay returns 202 Accepted with PAID_PENDING_INVOICE when fiscal invoice fails`() =
        testApplication {
            configureKioskApp()
            val client =
                createClient {
                    install(ContentNegotiation) { json(json) }
                }

            val orderId = UUID.randomUUID().toString()
            insertTestOrder(orderId, estado = "COTIZADO", total = BigDecimal("5.89"))

            feResultToReturn =
                ElectronicInvoiceResult.Failure(
                    codigo = "PAC_TIMEOUT",
                    mensaje = "PAC no disponible tras 3 reintentos",
                )

            val paymentRequest =
                KioskPaymentRequest(
                    transactionId = "tx-failure-1",
                    authCode = "AUTH-FAIL",
                    reference = "REF-FAIL",
                    last4 = "1111",
                    brand = "MASTERCARD",
                    amount = "5.89",
                )

            val response =
                client.post("/api/v1/kiosk/orders/$orderId/pay") {
                    header(HttpHeaders.Authorization, "Bearer ${generateKioskToken()}")
                    contentType(ContentType.Application.Json)
                    setBody(paymentRequest)
                }

            assertEquals(HttpStatusCode.Accepted, response.status)

            val payResp = json.decodeFromString<KioskPayResponse>(response.bodyAsText())
            assertEquals("K1-001", payResp.orderNumber)
            assertEquals("PAID_PENDING_INVOICE", payResp.status)
            assertNull(payResp.invoice?.cufe)

            // Verificar estado persistido en DB: PAGADO_SIN_FACTURA
            transaction(database) {
                val orderRow = KioskOrderTable.selectAll().where { KioskOrderTable.id eq orderId }.single()
                assertEquals("PAGADO_SIN_FACTURA", orderRow[KioskOrderTable.estado])
                assertTrue(orderRow[KioskOrderTable.motivoRechazo]!!.contains("PAC_TIMEOUT"))
            }
        }

    @Test
    fun `POST pay is idempotent when re-paying an already FACTURADO order`() =
        testApplication {
            configureKioskApp()
            val client =
                createClient {
                    install(ContentNegotiation) { json(json) }
                }

            val orderId = UUID.randomUUID().toString()
            insertTestOrder(orderId, estado = "COTIZADO", total = BigDecimal("5.89"))

            feResultToReturn =
                ElectronicInvoiceResult.Success(
                    cufe = "CUFE-IDEM-001",
                    qr = "https://dgi.pa/qr/001",
                )

            val paymentRequest =
                KioskPaymentRequest(
                    transactionId = "tx-idem",
                    authCode = "AUTH-IDEM",
                    reference = "REF-IDEM",
                    last4 = "4242",
                    brand = "VISA",
                    amount = "5.89",
                )

            // Primer pago exitoso
            val firstResp =
                client.post("/api/v1/kiosk/orders/$orderId/pay") {
                    header(HttpHeaders.Authorization, "Bearer ${generateKioskToken()}")
                    contentType(ContentType.Application.Json)
                    setBody(paymentRequest)
                }
            assertEquals(HttpStatusCode.OK, firstResp.status)

            // Reintento de pago sobre la misma orden
            val secondResp =
                client.post("/api/v1/kiosk/orders/$orderId/pay") {
                    header(HttpHeaders.Authorization, "Bearer ${generateKioskToken()}")
                    contentType(ContentType.Application.Json)
                    setBody(paymentRequest)
                }
            assertEquals(HttpStatusCode.OK, secondResp.status)

            val body = json.decodeFromString<KioskPayResponse>(secondResp.bodyAsText())
            assertEquals("FACTURADO", body.status)
            assertEquals("K1-001", body.orderNumber)
        }

    @Test
    fun `POST pay returns 400 Bad Request when payment amount does not match order total`() =
        testApplication {
            configureKioskApp()
            val client =
                createClient {
                    install(ContentNegotiation) { json(json) }
                }

            val orderId = UUID.randomUUID().toString()
            insertTestOrder(orderId, estado = "COTIZADO", total = BigDecimal("5.89"))

            val paymentRequest =
                KioskPaymentRequest(
                    transactionId = "tx-bad-amount",
                    authCode = "AUTH123",
                    reference = "REF123",
                    last4 = "4242",
                    brand = "VISA",
                    amount = "10.00", // Mismatch!
                )

            val response =
                client.post("/api/v1/kiosk/orders/$orderId/pay") {
                    header(HttpHeaders.Authorization, "Bearer ${generateKioskToken()}")
                    contentType(ContentType.Application.Json)
                    setBody(paymentRequest)
                }

            assertEquals(HttpStatusCode.BadRequest, response.status)
            assertTrue(response.bodyAsText().contains("no coincide con el total del pedido"))
        }

    @Test
    fun `POST pay returns 409 Conflict when order quote has expired`() =
        testApplication {
            configureKioskApp()
            val client =
                createClient {
                    install(ContentNegotiation) { json(json) }
                }

            val orderId = UUID.randomUUID().toString()
            // Cotización expiró hace 5 minutos
            insertTestOrder(
                orderId = orderId,
                estado = "COTIZADO",
                total = BigDecimal("5.89"),
                expiresAt = LocalDateTime.now().minusMinutes(5),
            )

            val paymentRequest =
                KioskPaymentRequest(
                    transactionId = "tx-expired",
                    authCode = "AUTH123",
                    reference = "REF123",
                    last4 = "4242",
                    brand = "VISA",
                    amount = "5.89",
                )

            val response =
                client.post("/api/v1/kiosk/orders/$orderId/pay") {
                    header(HttpHeaders.Authorization, "Bearer ${generateKioskToken()}")
                    contentType(ContentType.Application.Json)
                    setBody(paymentRequest)
                }

            assertEquals(HttpStatusCode.Conflict, response.status)
            assertTrue(response.bodyAsText().contains("ha expirado"))
        }
}
