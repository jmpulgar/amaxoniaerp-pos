package com.amaxoniaerp.features.kiosk.route

import com.amaxoniaerp.features.caja.data.CajaTableVE
import com.amaxoniaerp.features.caja.data.SucursalAlmacenTable
import com.amaxoniaerp.features.caja.data.SucursalTable
import com.amaxoniaerp.features.caja.data.VendedorTable
import com.amaxoniaerp.features.clients.data.ClientsTable
import com.amaxoniaerp.features.kiosk.application.KioskCajaSelection
import com.amaxoniaerp.features.kiosk.application.KioskService
import com.amaxoniaerp.features.kiosk.application.UnlockRateLimiter
import com.amaxoniaerp.features.kiosk.domain.KioskCardOptionDto
import com.amaxoniaerp.features.kiosk.domain.KioskConfigResponse
import com.amaxoniaerp.features.kiosk.domain.KioskMediaItem
import com.amaxoniaerp.features.kiosk.route.KioskTestSupport.kioskClient
import com.amaxoniaerp.features.kiosk.route.KioskTestSupport.kioskHeaders
import com.amaxoniaerp.features.pos.data.CajaFormaPagoTable
import com.zaxxer.hikari.HikariDataSource
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/** Configuración del kiosco: banners del ERP, columnas opcionales de parametros_generales y ETag. */
class KioskConfigRoutesTest {
    private lateinit var dataSourcePA: HikariDataSource
    private lateinit var databasePA: Database
    private lateinit var dataSourceVE: HikariDataSource
    private lateinit var databaseVE: Database
    private val json = KioskTestSupport.json

    @BeforeTest
    fun setUp() {
        dataSourcePA = KioskTestSupport.newDataSource("kiosk_cfg_pa")
        databasePA = Database.connect(dataSourcePA)
        dataSourceVE = KioskTestSupport.newDataSource("kiosk_cfg_ve")
        databaseVE = Database.connect(dataSourceVE)
        KioskTestSupport.createCajaSchema(databasePA)
        seedVenezuela()
    }

    @AfterTest
    fun tearDown() {
        dataSourcePA.close()
        dataSourceVE.close()
    }

    private fun newService() =
        KioskService(
            unlockRateLimiter = UnlockRateLimiter(),
            databaseResolver = { countryCode, _ -> if (countryCode.equals("VE", ignoreCase = true)) databaseVE else databasePA },
        )

    private suspend fun HttpClient.config(token: String = KioskTestSupport.companyToken()): KioskConfigResponse {
        val response = get("/api/v1/kiosk/config") { kioskHeaders(token = token) }
        assertEquals(HttpStatusCode.OK, response.status)
        assertNotNull(response.headers[HttpHeaders.ETag])
        return json.decodeFromString<KioskConfigResponse>(response.bodyAsText())
    }

    @Test
    fun `GET config builds media from the ERP banners and reads kiosk settings`() =
        testApplication {
            KioskTestSupport.createParametrosGenerales(databasePA)
            transaction(databasePA) {
                exec(
                    "UPDATE parametros_generales SET banner_1 = 'promo1.jpg', banner_2 = '', banner_3 = 'banners/video promo.MP4', " +
                        "menu_1 = 'menu.png', kiosco_destino_pedido = 'impresora_cocina', kiosco_modalidades = 'PARA_LLEVAR', " +
                        "default_cod_cliente_factura = '0001'",
                )
            }

            val config = kioskClient(newService()).config()

            assertEquals(
                listOf(
                    KioskMediaItem("IMAGE", "/api/data/PA/momi_pa/banners/promo1.jpg", 8),
                    KioskMediaItem("VIDEO", "/api/data/PA/momi_pa/banners/video%20promo.MP4", 0),
                    KioskMediaItem("IMAGE", "/api/data/PA/momi_pa/banners/menu.png", 8),
                ),
                config.media,
            )
            assertEquals("IMPRESORA_COCINA", config.dispatch)
            assertEquals(listOf("PARA_LLEVAR"), config.diningModes)
            assertEquals("0001", config.defaultCustomerId)
            assertNull(config.brandColor)
            assertNull(config.logoUrl)
            assertEquals("PA", config.country)
            assertEquals("USD", config.currency.base)
            assertEquals(listOf("CARD"), config.paymentMethods)
        }

    @Test
    fun `GET config lists the active card payment methods of caja_forma_pago`() =
        testApplication {
            KioskTestSupport.createParametrosGenerales(databasePA)
            transaction(databasePA) {
                SchemaUtils.create(CajaFormaPagoTable)

                fun formaPago(
                    id: Int,
                    siglas: String,
                    descripcion: String,
                    orden: Int,
                    dgi: String? = null,
                    activo: Int = 1,
                    pos: Int = 1,
                    imagen: String = "",
                ) = CajaFormaPagoTable.insert {
                    it[idFormaPago] = id
                    it[CajaFormaPagoTable.siglas] = siglas
                    it[CajaFormaPagoTable.descripcion] = descripcion
                    it[formaPagoFact] = dgi
                    it[CajaFormaPagoTable.activo] = activo
                    it[CajaFormaPagoTable.pos] = pos
                    it[CajaFormaPagoTable.orden] = orden
                    it[CajaFormaPagoTable.imagen] = imagen
                    it[grupo] = 0
                }
                formaPago(15, "CASH", "EFECTIVO", 1, dgi = "02")
                formaPago(50, "TDC", "MASTERCARD", 4, dgi = "03")
                formaPago(49, "TDC", "VISA", 3, dgi = "03", imagen = "data:image/png;base64,AAAA")
                formaPago(44, "TDD", "TARJETA DE DEBITO", 5)
                formaPago(34, "AMEX", "AMERICAN EXPRESS", 10, activo = 0)
                formaPago(60, "TDC", "TARJETA OCULTA", 11, pos = 0)
                formaPago(55, "YAPPY", "YAPPY", 3)
            }

            val config = kioskClient(newService()).config()

            assertEquals(
                listOf(
                    KioskCardOptionDto(49, "VISA", "TDC", "data:image/png;base64,AAAA"),
                    KioskCardOptionDto(50, "MASTERCARD", "TDC", null),
                    KioskCardOptionDto(44, "TARJETA DE DEBITO", "TDD", null),
                ),
                config.cardOptions,
            )
        }

    @Test
    fun `GET config has no card options when the tenant lacks caja_forma_pago`() =
        testApplication {
            KioskTestSupport.createParametrosGenerales(databasePA)

            assertEquals(emptyList(), kioskClient(newService()).config().cardOptions)
        }

    @Test
    fun `GET config uses defaults when the tenant has no kiosk columns`() =
        testApplication {
            KioskTestSupport.createParametrosGenerales(databasePA, optionalColumns = "")

            val config = kioskClient(newService()).config()

            assertEquals(emptyList(), config.media)
            assertEquals("RETIRO_MOSTRADOR", config.dispatch)
            assertEquals(listOf("COMER_AQUI", "PARA_LLEVAR"), config.diningModes)
            assertEquals("CF", config.defaultCustomerId)
        }

    @Test
    fun `GET config ignores an unknown dispatch value`() =
        testApplication {
            KioskTestSupport.createParametrosGenerales(databasePA)
            transaction(databasePA) { exec("UPDATE parametros_generales SET kiosco_destino_pedido = 'DRONE'") }

            assertEquals("RETIRO_MOSTRADOR", kioskClient(newService()).config().dispatch)
        }

    @Test
    fun `GET config version and ETag follow the content`() =
        testApplication {
            KioskTestSupport.createParametrosGenerales(databasePA)
            val client = kioskClient(newService())

            val first = client.get("/api/v1/kiosk/config") { kioskHeaders() }
            val etag = first.headers[HttpHeaders.ETag]
            assertNotNull(etag)
            val firstConfig = json.decodeFromString<KioskConfigResponse>(first.bodyAsText())

            val notModified =
                client.get("/api/v1/kiosk/config") {
                    kioskHeaders()
                    header(HttpHeaders.IfNoneMatch, etag)
                }
            assertEquals(HttpStatusCode.NotModified, notModified.status)
            assertEquals("", notModified.bodyAsText())
            assertEquals(firstConfig.version, client.config().version)

            transaction(databasePA) { exec("UPDATE parametros_generales SET banner_1 = 'nuevo.jpg'") }
            val changed = client.get("/api/v1/kiosk/config") { kioskHeaders() }
            assertNotEquals(etag, changed.headers[HttpHeaders.ETag])
            assertNotEquals(firstConfig.version, json.decodeFromString<KioskConfigResponse>(changed.bodyAsText()).version)
        }

    @Test
    fun `GET config returns multicurrency and exchange rate for VE`() =
        testApplication {
            val token = KioskTestSupport.companyToken(countryCode = "VE", adminDb = "momi_ve")
            val config = kioskClient(newService()).config(token)

            assertEquals("USD", config.currency.base)
            assertEquals("VES", config.currency.secondary)
            assertEquals("40.5", config.currency.rate)
            assertEquals("VE", config.country)
            assertEquals("IMPRESORA_COCINA", config.dispatch)
            assertEquals(listOf("COMER_AQUI"), config.diningModes)
        }

    @Test
    fun `VE context takes the warehouse from the caja`() =
        runBlocking {
            val context =
                newService().resolveContext(
                    KioskCajaSelection("VE", "momi_ve", KioskTestSupport.USER_ID, KioskTestSupport.CAJA_ID, "K2"),
                )
            assertNotNull(context)
            assertEquals(5, context.idAlmacen)
            assertEquals(0, context.codVendedor)
        }

    private fun seedVenezuela() {
        transaction(databaseVE) {
            exec(
                """
                CREATE TABLE IF NOT EXISTS parametros_generales (
                    cod_empresa INT PRIMARY KEY,
                    default_cod_cliente_factura VARCHAR(80) NOT NULL DEFAULT 'CF',
                    default_id_formapago_factura INT NOT NULL DEFAULT 1,
                    porcentaje_impuesto_principal DECIMAL(10,2) NOT NULL DEFAULT 16.00,
                    validar_stock VARCHAR(2) NOT NULL DEFAULT 'SI',
                    dias_vencimiento INT NOT NULL DEFAULT 30,
                    cod_almacen INT NOT NULL DEFAULT 1,
                    rif VARCHAR(50) NULL,
                    moneda VARCHAR(50) NOT NULL DEFAULT 'USD',
                    moneda_base INT NOT NULL DEFAULT 1,
                    multi_moneda VARCHAR(2) NOT NULL DEFAULT 'SI',
                    moneda_secundaria INT NOT NULL DEFAULT 2,
                    moneda_secundaria_abr VARCHAR(50) NOT NULL DEFAULT 'VES',
                    igtf DECIMAL(10,6) NULL,
                    impresion_directa VARCHAR(2) NOT NULL DEFAULT 'No',
                    kiosco_destino_pedido VARCHAR(30) NOT NULL DEFAULT 'IMPRESORA_COCINA',
                    kiosco_impresora_cocina_ip VARCHAR(45) NULL,
                    kiosco_modalidades VARCHAR(30) NOT NULL DEFAULT 'COMER_AQUI'
                );
                """.trimIndent(),
            )
            exec("INSERT INTO parametros_generales (cod_empresa) VALUES (1);")
            exec(
                """
                CREATE TABLE IF NOT EXISTS tasas_cambio (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    divisa INT NOT NULL,
                    monedabase INT NOT NULL,
                    tasa_inversa DECIMAL(20,8) NOT NULL,
                    facturado VARCHAR(1) NOT NULL DEFAULT '0'
                );
                """.trimIndent(),
            )
            exec("INSERT INTO tasas_cambio (divisa, monedabase, tasa_inversa, facturado) VALUES (2, 1, 40.50000000, '0');")
            SchemaUtils.create(SucursalTable, SucursalAlmacenTable, VendedorTable, CajaTableVE, ClientsTable)
            SucursalTable.insert {
                it[idSucursal] = 1
                it[serie] = "01"
                it[sucursal] = "Caracas"
            }
            CajaTableVE.insert {
                it[idCaja] = KioskTestSupport.CAJA_ID
                it[codCaja] = "C01"
                it[serieCaja] = "01"
                it[descripcion] = "Kiosco Caracas"
                it[idSucursal] = 1
                it[codEstatus] = 1
                it[codAlmacen] = 5
            }
        }
    }
}
