package com.amaxoniaerp.features.kiosk.route

import com.amaxoniaerp.features.caja.application.CajaSessionWorkflow
import com.amaxoniaerp.features.caja.data.CajaDetalleAperturaTable
import com.amaxoniaerp.features.caja.data.CajaDetalleCierreFormaPagoTable
import com.amaxoniaerp.features.caja.data.CajaDetalleCierreTable
import com.amaxoniaerp.features.caja.data.CajaSecuenciaTable
import com.amaxoniaerp.features.caja.data.CajaTablePA
import com.amaxoniaerp.features.caja.data.ExposedCajaSessionStore
import com.amaxoniaerp.features.caja.data.SucursalAlmacenTable
import com.amaxoniaerp.features.caja.data.SucursalTable
import com.amaxoniaerp.features.caja.data.VendedorTable
import com.amaxoniaerp.features.clients.data.ClientsTable
import com.amaxoniaerp.features.electronicinvoice.application.ElectronicInvoiceStrategy
import com.amaxoniaerp.features.electronicinvoice.application.ProcessorFactory
import com.amaxoniaerp.features.electronicinvoice.domain.ElectronicInvoiceResult
import com.amaxoniaerp.features.items.data.ItemsTablePA
import com.amaxoniaerp.features.kiosk.application.KioskService
import com.amaxoniaerp.features.kiosk.application.KioskYappyService
import com.amaxoniaerp.features.kiosk.application.PlaceKioskOrderService
import com.amaxoniaerp.features.kiosk.application.UnlockRateLimiter
import com.amaxoniaerp.features.kiosk.application.YappySessionManager
import com.amaxoniaerp.features.kiosk.data.KioskConfigRepository
import com.amaxoniaerp.features.kiosk.data.KioskOrderItemModifierTable
import com.amaxoniaerp.features.kiosk.data.KioskOrderItemTable
import com.amaxoniaerp.features.kiosk.data.KioskOrderRepository
import com.amaxoniaerp.features.kiosk.data.KioskOrderTable
import com.amaxoniaerp.features.kiosk.data.KioskYappyConfigRepository
import com.amaxoniaerp.features.kiosk.domain.KioskConfigResponse
import com.amaxoniaerp.features.kiosk.domain.KioskPayResponse
import com.amaxoniaerp.features.kiosk.domain.KioskPaymentRequest
import com.amaxoniaerp.features.kiosk.domain.KioskYappyQrResponse
import com.amaxoniaerp.features.kiosk.domain.KioskYappyStatusResponse
import com.amaxoniaerp.features.kiosk.domain.yappy.YappyCharge
import com.amaxoniaerp.features.kiosk.domain.yappy.YappyCredentials
import com.amaxoniaerp.features.kiosk.domain.yappy.YappyDevice
import com.amaxoniaerp.features.kiosk.domain.yappy.YappyGateway
import com.amaxoniaerp.features.kiosk.domain.yappy.YappyQr
import com.amaxoniaerp.features.kiosk.domain.yappy.YappyQrType
import com.amaxoniaerp.features.kiosk.domain.yappy.YappyTransactionStatus
import com.amaxoniaerp.features.kiosk.domain.yappy.YappyUpstreamException
import com.amaxoniaerp.features.kiosk.route.KioskTestSupport.kioskHeaders
import com.amaxoniaerp.features.pos.data.CajaFormaPagoTable
import com.amaxoniaerp.features.sales.application.ProcessSaleUseCase
import com.amaxoniaerp.features.sales.data.ProcessSaleTransactionalRepository
import com.amaxoniaerp.features.sales.data.SalesFacturaTablePA
import com.amaxoniaerp.features.sales.domain.ProcessSaleRequest
import com.amaxoniaerp.features.sales.domain.ProcessSaleResponse
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
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
import java.io.File
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Endpoints Yappy del kiosco y confirmación de pago con method = YAPPY.
 * Yappy se sustituye por un [YappyGateway] en memoria; el resto (H2, venta, caja) es real.
 */
class KioskYappyRoutesTest {
    private lateinit var dataSource: HikariDataSource
    private lateinit var database: Database
    private lateinit var kioskService: KioskService

    private val jwtConfig = KioskTestSupport.jwtConfig

    private val json =
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = false
            explicitNulls = false
        }

    private val testCajaId = "CAJA-01"
    private val gateway = FakeYappyGateway()
    private var lastSaleRequest: ProcessSaleRequest? = null

    @BeforeTest
    fun setUp() {
        dataSource =
            HikariDataSource(
                HikariConfig().apply {
                    jdbcUrl = "jdbc:h2:mem:kiosk_yappy_${System.nanoTime()};MODE=MySQL;DB_CLOSE_DELAY=-1"
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
                    lastSaleRequest = request
                    return ProcessSaleResponse(
                        success = true,
                        idFactura = request.idFactura ?: "INV-YAPPY-001",
                        codFactura = "FAC-PA-2001",
                        codEstatus = 2,
                    )
                }
            }
        val strategy =
            object : ElectronicInvoiceStrategy {
                override val countryCode: String = "PA"

                override suspend fun processElectronicInvoice(
                    database: Database,
                    invoiceId: String,
                ): ElectronicInvoiceResult = ElectronicInvoiceResult.Success(cufe = "CUFE-YAPPY-1", qr = "https://dgi.pa/qr/yappy")
            }
        val feFactory =
            object : ProcessorFactory {
                override fun forCountry(countryCode: String): ElectronicInvoiceStrategy = strategy
            }

        val kioskOrderRepository = KioskOrderRepository()
        val yappyService =
            KioskYappyService(
                kioskOrderRepository = kioskOrderRepository,
                yappyConfigRepository = KioskYappyConfigRepository(),
                yappyGateway = gateway,
                sessionManager = YappySessionManager(gateway),
                // Simula YAPPY_QR_TYPE=HYB para distinguir el respaldo del valor por defecto DYN.
                defaultQrType = YappyQrType.HYB,
            )
        val placeKioskOrderService =
            PlaceKioskOrderService(
                kioskOrderRepository = kioskOrderRepository,
                cajaSessionWorkflow = CajaSessionWorkflow(ExposedCajaSessionStore()),
                processSaleUseCase = ProcessSaleUseCase(saleRepo, feFactory),
                yappyPaymentVerifier = yappyService,
            )
        kioskService =
            KioskService(
                unlockRateLimiter = UnlockRateLimiter(),
                databaseResolver = { _, _ -> database },
                kioskConfigRepository = KioskConfigRepository(),
                kioskOrderRepository = kioskOrderRepository,
                placeKioskOrderService = placeKioskOrderService,
                kioskYappyService = yappyService,
            )

        seedSchema()
    }

    @AfterTest
    fun tearDown() {
        dataSource.close()
    }

    private fun seedSchema() {
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
                    clave_kiosko VARCHAR(255) NULL,
                    yappy_api_key VARCHAR(255) NULL,
                    yappy_secret_key VARCHAR(255) NULL,
                    yappy_modo_produccion TINYINT(1) NOT NULL DEFAULT 0,
                    yappy_endpoint_prod VARCHAR(255) NULL DEFAULT 'https://api-checkout.yappy.cloud/v1',
                    yappy_endpoint_sandbox VARCHAR(255) NULL DEFAULT 'https://api-integrationcheckout-uat.yappycloud.com/v1',
                    yappy_id_unidad VARCHAR(100) NULL,
                    yappy_id_grupo VARCHAR(100) NULL
                );
                """.trimIndent(),
            )
            exec(
                "INSERT INTO parametros_generales (cod_empresa, rif, yappy_api_key, yappy_secret_key) " +
                    "VALUES (1, '155688-1-554433', 'api-key-test', 'secret-key-test');",
            )

            SchemaUtils.create(
                SucursalTable,
                SucursalAlmacenTable,
                VendedorTable,
                ClientsTable,
                CajaTablePA,
                CajaSecuenciaTable,
                CajaDetalleAperturaTable,
                CajaDetalleCierreTable,
                CajaDetalleCierreFormaPagoTable,
                CajaFormaPagoTable,
                ItemsTablePA,
                KioskOrderTable,
                KioskOrderItemTable,
                KioskOrderItemModifierTable,
                SalesFacturaTablePA,
            )

            SucursalTable.insert {
                it[idSucursal] = 1
                it[codigo] = "SUC-01"
                it[serie] = "01"
                it[sucursal] = "Multiplaza Mall"
                it[descripcion] = "Vía Israel, Ciudad de Panamá"
            }
            CajaTablePA.insert {
                it[idCaja] = testCajaId
                it[codCaja] = "C01"
                it[serieCaja] = "01"
                it[descripcion] = "Multiplaza"
                it[idSucursal] = 1
                it[yappyDeviceId] = "UNIDAD-K1"
                it[yappyGroupId] = "GRUPO-K1"
            }
            ClientsTable.insert {
                it[idCliente] = "CF"
                it[codCliente] = "CF"
                it[rif] = "CF"
                it[dv] = "0"
                it[nombre] = "CONSUMIDOR FINAL"
                it[direccion] = "Panamá"
            }
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
            }
            CajaFormaPagoTable.insert {
                it[idFormaPago] = 55
                it[siglas] = "YAPPY"
                it[codigo] = 55
                it[descripcion] = "YAPPY"
            }
        }
    }

    private fun insertOrder(
        orderId: String,
        estado: String = "COTIZADO",
        expiresAt: LocalDateTime = LocalDateTime.now().plusMinutes(10),
        yappyTransactionId: String? = null,
    ) {
        transaction(database) {
            KioskOrderTable.insert {
                it[id] = orderId
                it[idDispositivo] = testCajaId
                it[numeroPedidoDiario] = 7
                it[codigoPedido] = "K1-007"
                it[fecha] = LocalDate.now()
                it[KioskOrderTable.estado] = estado
                it[modalidad] = "PARA_LLEVAR"
                it[idCliente] = "CF"
                it[total] = BigDecimal("5.89").setScale(4, RoundingMode.HALF_UP)
                it[quoteExpiraEn] = expiresAt
                it[creadoEn] = LocalDateTime.now()
                it[actualizadoEn] = LocalDateTime.now()
                // Sin columnas Yappy propias: la transacción vive en pago_marca/pago_referencia.
                if (yappyTransactionId != null) {
                    it[pagoMarca] = "YAPPY"
                    it[pagoReferencia] = yappyTransactionId
                }
            }
            KioskOrderItemTable.insert {
                it[idPedido] = orderId
                it[linea] = 1
                it[idItem] = 101
                it[cantidad] = BigDecimal.ONE.setScale(4)
                it[precioUnitario] = BigDecimal("5.5000")
            }
        }
    }

    private fun storedOrder(orderId: String) =
        transaction(database) { KioskOrderTable.selectAll().where { KioskOrderTable.id eq orderId }.single() }

    /** Transacción Yappy registrada: pago_referencia cuando pago_marca = 'YAPPY'. */
    private fun storedYappyTransaction(orderId: String): String? {
        val row = storedOrder(orderId)
        return if (row[KioskOrderTable.pagoMarca] == "YAPPY") row[KioskOrderTable.pagoReferencia] else null
    }

    private fun addQrTypeColumn(value: String?) {
        transaction(database) {
            exec("ALTER TABLE parametros_generales ADD COLUMN IF NOT EXISTS yappy_tipo_qr VARCHAR(3) NOT NULL DEFAULT 'DYN'")
            if (value != null) exec("UPDATE parametros_generales SET yappy_tipo_qr = '$value'")
        }
    }

    private fun disableYappyCredentials() {
        transaction(database) { exec("UPDATE parametros_generales SET yappy_api_key = NULL") }
    }

    /** Token de empresa del POS (mismo login) para la empresa momi_pa. */
    private fun token(): String = KioskTestSupport.companyToken()

    private fun ApplicationTestBuilder.kioskClient(): HttpClient {
        install(io.ktor.server.plugins.contentnegotiation.ContentNegotiation) { json(json) }
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
                validate { credential -> JWTPrincipal(credential.payload) }
            }
        }
        routing { kioskRoutes(kioskService) }
        return createClient { install(ContentNegotiation) { json(json) } }
    }

    private suspend fun HttpClient.createQr(orderId: String): HttpResponse =
        post("/api/v1/kiosk/orders/$orderId/yappy") { kioskHeaders(token = token()) }

    private suspend fun HttpClient.pay(
        orderId: String,
        request: KioskPaymentRequest,
    ): HttpResponse =
        post("/api/v1/kiosk/orders/$orderId/pay") {
            kioskHeaders(token = token())
            contentType(ContentType.Application.Json)
            setBody(request)
        }

    private fun yappyPayment(transactionId: String) = KioskPaymentRequest(transactionId = transactionId, amount = "5.89", method = "YAPPY")

    // ─── POST /orders/{id}/yappy ───────────────────────────────────────────

    @Test
    fun `POST yappy generates QR for the quoted total and stores the transaction`() =
        testApplication {
            val client = kioskClient()
            val orderId = UUID.randomUUID().toString()
            insertOrder(orderId)

            val response = client.createQr(orderId)

            assertEquals(HttpStatusCode.OK, response.status)
            val qr = json.decodeFromString<KioskYappyQrResponse>(response.bodyAsText())
            assertEquals("YAPPY-TX-1", qr.transactionId)
            assertEquals("QR-HASH-YAPPY-TX-1", qr.qrHash)
            assertEquals("5.89", qr.amount)
            assertEquals(180, qr.expiresInSec)

            val charge = gateway.charges.single()
            assertEquals(BigDecimal("5.50"), charge.subTotal)
            assertEquals(BigDecimal("0.39"), charge.tax)
            assertEquals(BigDecimal("5.89"), charge.total)
            assertEquals("K1-007", charge.orderId)
            assertEquals("Kiosco Multiplaza", charge.description)
            assertEquals(YappyDevice("UNIDAD-K1", "GRUPO-K1"), gateway.openedDevices.single())
            assertEquals("https://api-integrationcheckout-uat.yappycloud.com/v1", gateway.lastCredentials?.baseUrl)
            assertEquals("YAPPY-TX-1", storedYappyTransaction(orderId))
        }

    @Test
    fun `POST yappy cancels the previous pending transaction before generating a new one`() =
        testApplication {
            val client = kioskClient()
            val orderId = UUID.randomUUID().toString()
            insertOrder(orderId, yappyTransactionId = "OLD-TX")
            gateway.statuses["OLD-TX"] = YappyTransactionStatus.PENDING

            val response = client.createQr(orderId)

            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals(listOf("OLD-TX"), gateway.cancelled)
            assertEquals("YAPPY-TX-1", storedYappyTransaction(orderId))
        }

    @Test
    fun `POST yappy refuses a new QR when the previous one was already paid`() =
        testApplication {
            val client = kioskClient()
            val orderId = UUID.randomUUID().toString()
            insertOrder(orderId, yappyTransactionId = "PAID-TX")
            gateway.statuses["PAID-TX"] = YappyTransactionStatus.COMPLETED

            val response = client.createQr(orderId)

            assertEquals(HttpStatusCode.Conflict, response.status)
            assertTrue(gateway.charges.isEmpty())
            assertTrue(gateway.cancelled.isEmpty())
            assertEquals("PAID-TX", storedYappyTransaction(orderId))
        }

    @Test
    fun `POST yappy returns 503 when Yappy is not configured`() =
        testApplication {
            val client = kioskClient()
            val orderId = UUID.randomUUID().toString()
            insertOrder(orderId)
            disableYappyCredentials()

            val response = client.createQr(orderId)

            assertEquals(HttpStatusCode.ServiceUnavailable, response.status)
            assertTrue(response.bodyAsText().contains("Yappy no está configurado para este kiosco"))
        }

    @Test
    fun `POST yappy validates order ownership, state and quote expiry`() =
        testApplication {
            val client = kioskClient()
            assertEquals(HttpStatusCode.BadRequest, client.createQr("missing-order").status)

            val paidOrder = UUID.randomUUID().toString()
            insertOrder(paidOrder, estado = "FACTURADO")
            assertEquals(HttpStatusCode.Conflict, client.createQr(paidOrder).status)

            val expiredOrder = UUID.randomUUID().toString()
            transaction(database) { exec("DELETE FROM kiosco_pedido_item") }
            transaction(database) { exec("DELETE FROM kiosco_pedido") }
            insertOrder(expiredOrder, expiresAt = LocalDateTime.now().minusMinutes(1))
            val expired = client.createQr(expiredOrder)
            assertEquals(HttpStatusCode.Conflict, expired.status)
            assertTrue(expired.bodyAsText().contains("ha expirado"))
        }

    @Test
    fun `POST yappy maps upstream failures to 502`() =
        testApplication {
            val client = kioskClient()
            val orderId = UUID.randomUUID().toString()
            insertOrder(orderId)
            gateway.failure = YappyUpstreamException("No se pudo contactar a Yappy")

            val response = client.createQr(orderId)

            assertEquals(HttpStatusCode.BadGateway, response.status)
            assertTrue(response.bodyAsText().contains("No se pudo contactar a Yappy"))
            assertNull(storedYappyTransaction(orderId))
        }

    @Test
    fun `POST yappy stores the transaction in pago_marca and pago_referencia without extra columns`() =
        testApplication {
            val client = kioskClient()
            val orderId = UUID.randomUUID().toString()
            insertOrder(orderId)

            assertEquals(HttpStatusCode.OK, client.createQr(orderId).status)

            val row = storedOrder(orderId)
            assertEquals("YAPPY", row[KioskOrderTable.pagoMarca])
            assertEquals("YAPPY-TX-1", row[KioskOrderTable.pagoReferencia])
            assertEquals("COTIZADO", row[KioskOrderTable.estado])
            val columns =
                transaction(database) {
                    exec("SHOW COLUMNS FROM kiosco_pedido") { rs ->
                        buildList { while (rs.next()) add(rs.getString(1).lowercase()) }
                    }.orEmpty()
                }
            assertTrue("pago_metodo" !in columns && "yappy_transaction_id" !in columns)
        }

    @Test
    fun `POST yappy falls back to YAPPY_QR_TYPE when parametros_generales has no yappy_tipo_qr column`() =
        testApplication {
            val client = kioskClient()
            val orderId = UUID.randomUUID().toString()
            insertOrder(orderId)

            assertEquals(HttpStatusCode.OK, client.createQr(orderId).status)
            assertEquals(listOf(YappyQrType.HYB), gateway.qrTypes)
        }

    @Test
    fun `POST yappy uses parametros_generales yappy_tipo_qr when the optional column exists`() =
        testApplication {
            addQrTypeColumn("DYN")
            val client = kioskClient()
            val orderId = UUID.randomUUID().toString()
            insertOrder(orderId)

            assertEquals(HttpStatusCode.OK, client.createQr(orderId).status)
            assertEquals(listOf(YappyQrType.DYN), gateway.qrTypes)

            // Valor inválido en la columna: se usa el respaldo YAPPY_QR_TYPE.
            transaction(database) { exec("UPDATE parametros_generales SET yappy_tipo_qr = 'ZZZ'") }
            assertEquals(HttpStatusCode.OK, client.createQr(orderId).status)
            assertEquals(listOf(YappyQrType.DYN, YappyQrType.HYB), gateway.qrTypes)
        }

    // ─── GET / DELETE /orders/{id}/yappy/{transactionId} ──────────────────

    @Test
    fun `GET yappy status passes through the normalized Yappy status`() =
        testApplication {
            val client = kioskClient()
            val orderId = UUID.randomUUID().toString()
            insertOrder(orderId, yappyTransactionId = "TX-55")
            gateway.statuses["TX-55"] = YappyTransactionStatus.COMPLETED

            val response =
                client.get("/api/v1/kiosk/orders/$orderId/yappy/TX-55") {
                    kioskHeaders(token = token())
                }

            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals(
                KioskYappyStatusResponse(transactionId = "TX-55", status = "COMPLETED"),
                json.decodeFromString<KioskYappyStatusResponse>(response.bodyAsText()),
            )
        }

    @Test
    fun `GET yappy status returns 409 for a transaction not stored on the order and 502 on upstream errors`() =
        testApplication {
            val client = kioskClient()
            val orderId = UUID.randomUUID().toString()
            insertOrder(orderId, yappyTransactionId = "TX-55")

            val mismatch =
                client.get("/api/v1/kiosk/orders/$orderId/yappy/OTHER-TX") {
                    kioskHeaders(token = token())
                }
            assertEquals(HttpStatusCode.Conflict, mismatch.status)

            gateway.failure = YappyUpstreamException("Yappy respondió con error (HTTP 500)")
            val upstream =
                client.get("/api/v1/kiosk/orders/$orderId/yappy/TX-55") {
                    kioskHeaders(token = token())
                }
            assertEquals(HttpStatusCode.BadGateway, upstream.status)
        }

    @Test
    fun `DELETE yappy cancels a pending transaction and always answers 204`() =
        testApplication {
            val client = kioskClient()
            val orderId = UUID.randomUUID().toString()
            insertOrder(orderId, yappyTransactionId = "TX-77")
            gateway.statuses["TX-77"] = YappyTransactionStatus.PENDING

            val response =
                client.delete("/api/v1/kiosk/orders/$orderId/yappy/TX-77") {
                    kioskHeaders(token = token())
                }
            assertEquals(HttpStatusCode.NoContent, response.status)
            assertEquals(listOf("TX-77"), gateway.cancelled)
            assertNull(storedYappyTransaction(orderId))
            assertNull(storedOrder(orderId)[KioskOrderTable.pagoMarca])
            assertNull(storedOrder(orderId)[KioskOrderTable.pagoReferencia])

            gateway.failure = YappyUpstreamException("timeout")
            val failing =
                client.delete("/api/v1/kiosk/orders/missing/yappy/TX-0") {
                    kioskHeaders(token = token())
                }
            assertEquals(HttpStatusCode.NoContent, failing.status)
        }

    @Test
    fun `DELETE yappy never cancels a completed payment`() =
        testApplication {
            val client = kioskClient()
            val orderId = UUID.randomUUID().toString()
            insertOrder(orderId, yappyTransactionId = "TX-PAID")
            gateway.statuses["TX-PAID"] = YappyTransactionStatus.COMPLETED

            val response =
                client.delete("/api/v1/kiosk/orders/$orderId/yappy/TX-PAID") {
                    kioskHeaders(token = token())
                }

            assertEquals(HttpStatusCode.NoContent, response.status)
            assertTrue(gateway.cancelled.isEmpty())
            assertEquals("TX-PAID", storedYappyTransaction(orderId))
        }

    // ─── POST /orders/{id}/pay con method = YAPPY ──────────────────────────

    @Test
    fun `POST pay with YAPPY and COMPLETED transaction invoices with the YAPPY forma de pago`() =
        testApplication {
            val client = kioskClient()
            val orderId = UUID.randomUUID().toString()
            insertOrder(orderId, yappyTransactionId = "TX-OK")
            gateway.statuses["TX-OK"] = YappyTransactionStatus.COMPLETED

            val response = client.pay(orderId, yappyPayment("TX-OK"))

            assertEquals(HttpStatusCode.OK, response.status)
            val body = json.decodeFromString<KioskPayResponse>(response.bodyAsText())
            assertEquals("FACTURADO", body.status)
            assertEquals("CUFE-YAPPY-1", body.invoice?.cufe)
            assertEquals("YAPPY", body.receipt.paymentBrand)
            assertEquals("", body.receipt.paymentLast4)
            assertEquals("TX-OK", body.receipt.paymentAuthCode)
            assertEquals("TX-OK", body.receipt.paymentReference)

            val sale = lastSaleRequest!!
            val pago = sale.pagos.single()
            assertEquals(55, pago.idFormaPago)
            assertEquals("YAPPY", pago.tipoMovimiento)
            assertNull(pago.tdcNumero)
            assertNull(pago.codigoVerificacion)
            assertEquals(mapOf("YAPPY" to 5.89), sale.pagoResumen.montosPorTipo)

            val row = storedOrder(orderId)
            assertEquals("FACTURADO", row[KioskOrderTable.estado])
            assertEquals("YAPPY", row[KioskOrderTable.pagoMarca])
            assertEquals("TX-OK", row[KioskOrderTable.pagoReferencia])
            assertEquals("TX-OK", row[KioskOrderTable.pagoAutorizacion])
            assertNull(row[KioskOrderTable.pagoUltimos4])

            // Reintento idempotente: recibo Yappy, nunca "VISA".
            val again = json.decodeFromString<KioskPayResponse>(client.pay(orderId, yappyPayment("TX-OK")).bodyAsText())
            assertEquals("YAPPY", again.receipt.paymentBrand)
            assertEquals("TX-OK", again.receipt.paymentAuthCode)
        }

    @Test
    fun `POST pay with YAPPY still confirms a paid transaction after the quote expired`() =
        testApplication {
            val client = kioskClient()
            val orderId = UUID.randomUUID().toString()
            insertOrder(orderId, expiresAt = LocalDateTime.now().minusMinutes(2), yappyTransactionId = "TX-LATE")
            gateway.statuses["TX-LATE"] = YappyTransactionStatus.COMPLETED

            assertEquals(HttpStatusCode.OK, client.pay(orderId, yappyPayment("TX-LATE")).status)
        }

    @Test
    fun `POST pay with YAPPY and PENDING transaction returns 409 without invoicing`() =
        testApplication {
            val client = kioskClient()
            val orderId = UUID.randomUUID().toString()
            insertOrder(orderId, yappyTransactionId = "TX-PEND")
            gateway.statuses["TX-PEND"] = YappyTransactionStatus.PENDING

            val response = client.pay(orderId, yappyPayment("TX-PEND"))

            assertEquals(HttpStatusCode.Conflict, response.status)
            assertTrue(response.bodyAsText().contains("Pago Yappy no confirmado"))
            assertNull(lastSaleRequest)
            assertEquals("COTIZADO", storedOrder(orderId)[KioskOrderTable.estado])
        }

    @Test
    fun `POST pay with YAPPY rejects a transaction id different from the stored one`() =
        testApplication {
            val client = kioskClient()
            val orderId = UUID.randomUUID().toString()
            insertOrder(orderId, yappyTransactionId = "TX-REAL")
            gateway.statuses["TX-FORGED"] = YappyTransactionStatus.COMPLETED

            val response = client.pay(orderId, yappyPayment("TX-FORGED"))

            assertEquals(HttpStatusCode.Conflict, response.status)
            assertTrue(gateway.statusQueries.isEmpty())
        }

    @Test
    fun `POST pay with YAPPY without YAPPY forma de pago keeps the order PAGADO_SIN_FACTURA`() =
        testApplication {
            val client = kioskClient()
            val orderId = UUID.randomUUID().toString()
            insertOrder(orderId, yappyTransactionId = "TX-NOFP")
            gateway.statuses["TX-NOFP"] = YappyTransactionStatus.COMPLETED
            transaction(database) { exec("DELETE FROM caja_forma_pago WHERE siglas = 'YAPPY'") }

            val response = client.pay(orderId, yappyPayment("TX-NOFP"))

            assertEquals(HttpStatusCode.Accepted, response.status)
            val body = json.decodeFromString<KioskPayResponse>(response.bodyAsText())
            assertEquals("PAID_PENDING_INVOICE", body.status)
            assertNull(body.invoice)
            assertEquals("YAPPY", body.receipt.paymentBrand)
            assertNull(lastSaleRequest)
            val row = storedOrder(orderId)
            assertEquals("PAGADO_SIN_FACTURA", row[KioskOrderTable.estado])
            assertTrue(row[KioskOrderTable.motivoRechazo]!!.contains("forma de pago YAPPY"))
        }

    @Test
    fun `POST pay with CARD keeps the card flow and records method CARD`() =
        testApplication {
            val client = kioskClient()
            val orderId = UUID.randomUUID().toString()
            insertOrder(orderId)

            val response =
                client.pay(
                    orderId,
                    KioskPaymentRequest(
                        transactionId = "tx-card",
                        authCode = "AUTH1",
                        reference = "REF1",
                        last4 = "4242",
                        brand = "VISA",
                        amount = "5.89",
                    ),
                )

            assertEquals(HttpStatusCode.OK, response.status)
            val body = json.decodeFromString<KioskPayResponse>(response.bodyAsText())
            assertEquals("VISA", body.receipt.paymentBrand)
            assertEquals("4242", body.receipt.paymentLast4)
            val pago = lastSaleRequest!!.pagos.single()
            assertEquals(2, pago.idFormaPago)
            assertEquals("ING", pago.tipoMovimiento)
            assertEquals(mapOf("TDC" to 5.89), lastSaleRequest!!.pagoResumen.montosPorTipo)
            val row = storedOrder(orderId)
            assertEquals("VISA", row[KioskOrderTable.pagoMarca])
            assertEquals("REF1", row[KioskOrderTable.pagoReferencia])
            assertEquals("AUTH1", row[KioskOrderTable.pagoAutorizacion])
            assertTrue(gateway.statusQueries.isEmpty())
        }

    @Test
    fun `POST pay with CARD after an abandoned Yappy QR overwrites pago_marca and stays CARD`() =
        testApplication {
            val client = kioskClient()
            val orderId = UUID.randomUUID().toString()
            insertOrder(orderId, yappyTransactionId = "TX-ABANDONED")
            val card =
                KioskPaymentRequest(
                    transactionId = "tx-card",
                    authCode = "AUTH9",
                    reference = "REF9",
                    last4 = "1111",
                    brand = "MASTERCARD",
                    amount = "5.89",
                )

            assertEquals(HttpStatusCode.OK, client.pay(orderId, card).status)
            val row = storedOrder(orderId)
            assertEquals("MASTERCARD", row[KioskOrderTable.pagoMarca])
            assertEquals("REF9", row[KioskOrderTable.pagoReferencia])
            assertNull(storedYappyTransaction(orderId))
            assertEquals(
                "TDC",
                lastSaleRequest!!
                    .pagoResumen.montosPorTipo.keys
                    .single(),
            )

            val again = json.decodeFromString<KioskPayResponse>(client.pay(orderId, card).bodyAsText())
            assertEquals("MASTERCARD", again.receipt.paymentBrand)
            assertEquals("1111", again.receipt.paymentLast4)
        }

    @Test
    fun `POST pay with CARD rejects the reserved YAPPY brand`() =
        testApplication {
            val client = kioskClient()
            val orderId = UUID.randomUUID().toString()
            insertOrder(orderId)

            val response =
                client.pay(orderId, KioskPaymentRequest(transactionId = "x", brand = "YAPPY", amount = "5.89", method = "CARD"))

            assertEquals(HttpStatusCode.BadRequest, response.status)
            assertNull(lastSaleRequest)
        }

    @Test
    fun `POST pay rejects unknown payment methods with 400`() =
        testApplication {
            val client = kioskClient()
            val orderId = UUID.randomUUID().toString()
            insertOrder(orderId)

            val response = client.pay(orderId, KioskPaymentRequest(transactionId = "x", amount = "5.89", method = "CASH"))

            assertEquals(HttpStatusCode.BadRequest, response.status)
        }

    // ─── GET /config ───────────────────────────────────────────────────────

    @Test
    fun `GET config advertises YAPPY only when credentials and device are configured`() =
        testApplication {
            val client = kioskClient()

            val configured = client.get("/api/v1/kiosk/config") { kioskHeaders(token = token()) }
            assertEquals(HttpStatusCode.OK, configured.status)
            val configuredEtag = configured.headers[HttpHeaders.ETag]
            assertEquals(listOf("CARD", "YAPPY"), json.decodeFromString<KioskConfigResponse>(configured.bodyAsText()).paymentMethods)

            disableYappyCredentials()
            val notConfigured = client.get("/api/v1/kiosk/config") { kioskHeaders(token = token()) }
            assertEquals(listOf("CARD"), json.decodeFromString<KioskConfigResponse>(notConfigured.bodyAsText()).paymentMethods)
            assertTrue(configuredEtag != notConfigured.headers[HttpHeaders.ETag], "paymentMethods debe formar parte del ETag")
        }

    @Test
    fun `GET config falls back to the parametros_generales Yappy unit when the caja has none`() =
        testApplication {
            val client = kioskClient()
            transaction(database) {
                exec("UPDATE caja SET yappy_device_id = NULL, yappy_group_id = NULL")
            }

            val withoutUnit = client.get("/api/v1/kiosk/config") { kioskHeaders(token = token()) }
            assertEquals(listOf("CARD"), json.decodeFromString<KioskConfigResponse>(withoutUnit.bodyAsText()).paymentMethods)

            transaction(database) {
                exec("UPDATE parametros_generales SET yappy_id_unidad = 'UNIDAD-EXPRESS', yappy_id_grupo = 'GRUPO-EXPRESS'")
            }
            val withFallback = client.get("/api/v1/kiosk/config") { kioskHeaders(token = token()) }
            assertEquals(listOf("CARD", "YAPPY"), json.decodeFromString<KioskConfigResponse>(withFallback.bodyAsText()).paymentMethods)
        }

    @Test
    fun `yappy-qr-response fixture matches the QR response contract`() {
        val fixture =
            listOf(File("../contracts/kiosk/yappy-qr-response.json"), File("contracts/kiosk/yappy-qr-response.json"))
                .first { it.exists() }

        val qr = json.decodeFromString<KioskYappyQrResponse>(fixture.readText())

        assertTrue(qr.transactionId.isNotBlank())
        assertTrue(qr.qrHash.isNotBlank())
        assertEquals("12.50", qr.amount)
        assertEquals(180, qr.expiresInSec)
    }
}

/** Yappy en memoria: estados por transacción, QR secuenciales y registro de llamadas. */
private class FakeYappyGateway : YappyGateway {
    val statuses = mutableMapOf<String, YappyTransactionStatus>()
    val charges = mutableListOf<YappyCharge>()
    val qrTypes = mutableListOf<YappyQrType>()
    val cancelled = mutableListOf<String>()
    val statusQueries = mutableListOf<String>()
    val openedDevices = mutableListOf<YappyDevice>()
    var lastCredentials: YappyCredentials? = null
    var failure: YappyUpstreamException? = null
    private var nextTransaction = 1

    override suspend fun openSession(
        credentials: YappyCredentials,
        device: YappyDevice,
    ): String {
        lastCredentials = credentials
        openedDevices += device
        return "session-${openedDevices.size}"
    }

    override suspend fun generateQr(
        credentials: YappyCredentials,
        sessionToken: String,
        qrType: YappyQrType,
        charge: YappyCharge,
    ): YappyQr {
        failure?.let { throw it }
        charges += charge
        qrTypes += qrType
        val transactionId = "YAPPY-TX-${nextTransaction++}"
        statuses[transactionId] = YappyTransactionStatus.PENDING
        return YappyQr(transactionId = transactionId, hash = "QR-HASH-$transactionId")
    }

    override suspend fun getTransactionStatus(
        credentials: YappyCredentials,
        sessionToken: String,
        transactionId: String,
    ): YappyTransactionStatus {
        failure?.let { throw it }
        statusQueries += transactionId
        return statuses[transactionId] ?: YappyTransactionStatus.PENDING
    }

    override suspend fun cancelTransaction(
        credentials: YappyCredentials,
        sessionToken: String,
        transactionId: String,
    ) {
        failure?.let { throw it }
        cancelled += transactionId
        statuses[transactionId] = YappyTransactionStatus.CANCELLED
    }
}
