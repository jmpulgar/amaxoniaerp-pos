package com.amaxoniaerp.features.sales.route

import com.amaxoniaerp.JwtConfig
import com.amaxoniaerp.core.database.DatabaseManager
import com.amaxoniaerp.features.companies.data.ParametrosGeneralesTablePA
import com.amaxoniaerp.features.sales.data.SalesCajaTable
import com.amaxoniaerp.features.sales.data.SalesFacturaTablePA
import com.amaxoniaerp.features.sales.data.SalesSucursalAlmacenTable
import com.amaxoniaerp.features.sales.data.SalesSucursalTable
import com.amaxoniaerp.loadJwtConfig
import com.amaxoniaerp.module
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction
import java.math.BigDecimal
import java.time.LocalDate
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Tests de integración de la capa de acceso a `POST /api/pos/ventas/procesar`
 * sobre el módulo real. Cubren la barrera de autenticación y la traducción del
 * body malformado según el comportamiento vigente (characterization); la ruta
 * resuelve la empresa directamente desde `admin_db` del JWT (sin header).
 *
 * TASK-102 (matriz de idempotencia): el caso HTTP 409 por factura duplicada
 * se verifica E2E — un request cuyo `idFactura` ya existe en `factura`
 * responde 409 sin re-procesar la venta.
 */
class SalesRoutesIntegrationTest {
    private lateinit var jwt: JwtConfig
    private var dataSource: HikariDataSource? = null

    private fun app(block: suspend ApplicationTestBuilder.() -> Unit) =
        testApplication {
            application {
                module()
                jwt = loadJwtConfig()
            }
            client.get("/health")
            block()
        }

    private fun companyToken(adminDb: String): String =
        JWT
            .create()
            .withIssuer(jwt.domain)
            .withAudience(jwt.audience)
            .withClaim("token_type", "company")
            .withClaim("user_id", 7)
            .withClaim("country_code", "PA")
            .withClaim("admin_db", adminDb)
            .sign(Algorithm.HMAC256(jwt.secret))

    /** Siembra un DataSource H2 en la caché privada de [DatabaseManager]. */
    private fun seedH2CompanyDb(companyDb: String): Database {
        val ds =
            HikariDataSource(
                HikariConfig().apply {
                    jdbcUrl = "jdbc:h2:mem:${companyDb}_${System.nanoTime()};MODE=MySQL;DB_CLOSE_DELAY=-1"
                    driverClassName = "org.h2.Driver"
                    maximumPoolSize = 2
                    isAutoCommit = false
                },
            )
        dataSource = ds
        val field = DatabaseManager::class.java.getDeclaredField("companyDataSources")
        field.isAccessible = true
        val cache = field.get(DatabaseManager) as MutableMap<Any?, Any?>
        synchronized(cache) {
            cache["PA:$companyDb"] = ds
        }
        return Database.connect(ds)
    }

    private fun createSchemaAndSeed(database: Database) {
        transaction(database) {
            SchemaUtils.create(
                SalesCajaTable,
                SalesSucursalTable,
                SalesSucursalAlmacenTable,
                ParametrosGeneralesTablePA,
                SalesFacturaTablePA,
            )
            seedCajaConAlmacen()
            seedParametrosGenerales()
            seedFacturaDuplicada()
        }
    }

    private fun seedCajaConAlmacen() {
        SalesCajaTable.insert {
            it[id] = "1"
            it[idSucursal] = 1
            it[facturaCorrelativo] = 100
        }
        SalesSucursalTable.insert {
            it[id] = 1
            it[serie] = "001"
        }
        SalesSucursalAlmacenTable.insert {
            it[idSucursal] = 1
            it[idAlmacen] = 1
            it[defaultVentas] = 1
        }
    }

    private fun seedParametrosGenerales() {
        ParametrosGeneralesTablePA.insert {
            it[codEmpresa] = 1
            it[defaultCodClienteFactura] = "CF"
            it[defaultIdFormaPagoFactura] = 1
            it[porcentajeImpuestoPrincipal] = BigDecimal("7.00")
            it[validarStock] = "NO"
            it[diasVencimiento] = 8
            it[codAlmacen] = 1
            it[abrMonedaBase] = "USD"
        }
    }

    /** La factura YA procesada que convierte el reintento en conflicto. */
    private fun seedFacturaDuplicada() {
        SalesFacturaTablePA.insert {
            it[idFactura] = DUPLICATE_ID_FACTURA
            it[codFactura] = "FAC-DUP-1"
            it[codFacturaFiscal] = ""
            it[idCliente] = "cli-1"
            it[codVendedor] = 5
            it[subtotal] = BigDecimal("10.00")
            it[descuentosItemFactura] = BigDecimal("0.00")
            it[montoItemsFactura] = BigDecimal("10.00")
            it[ivaTotalFactura] = BigDecimal("0.70")
            it[totalTotalFactura] = BigDecimal("10.70")
            it[cantidadItems] = 1
            it[totalizarSubTotal] = BigDecimal("10.00")
            it[totalizarDescuentoParcial] = BigDecimal("0.00")
            it[totalizarTotalOperacion] = BigDecimal("10.00")
            it[totalizarPDescuentoGlobal] = BigDecimal("0.00")
            it[totalizarDescuentoGlobal] = BigDecimal("0.00")
            it[totalizarBaseImponible] = BigDecimal("10.00")
            it[totalizarMontoIva] = BigDecimal("0.70")
            it[totalizarTotalGeneral] = BigDecimal("10.70")
            it[totalizarTotalRetencion] = BigDecimal("0.00")
            it[formaPago] = "contado"
            it[codEstatus] = 2
            it[usuarioCreacion] = "cajero1"
            it[tipoFactura] = "POS"
            it[facturarA] = "CONSUMIDOR FINAL"
            it[facturarARuc] = "CF"
            it[facturarADireccion] = ""
            it[facturarATelefono] = ""
            it[validarStock] = "NO"
            it[idShop] = 1
            it[servicioPeriodo] = ""
            it[servicioOrden] = ""
            it[observacion] = ""
            it[servicioAnio] = 2026
            it[servicioMes] = "08"
            it[idCajaSecuencia] = "seq-1"
            it[numcomContabilizado] = 0
            it[fechaContabilizado] = LocalDate.of(2026, 8, 22)
            it[serieSucursal] = "001"
            it[cajaSecuencia] = "0001"
            it[idSucursal] = 1
            it[idCaja] = "1"
            it[codigoCaja] = "CAJA1"
            it[codCliente] = "CLI-1"
            it[nroz] = "0000"
            it[impresoraSerial] = ""
            it[multiMoneda] = "NO"
            it[tasa] = 1.0f
            it[idTasa] = 0
            it[monedaBase] = 1
            it[abrMonedaBase] = "USD"
            it[monedaSecundaria] = 1
            it[abrMonedaSecundaria] = "USD"
            it[totalRef] = 10.70f
        }
    }

    @AfterTest
    fun tearDown() {
        dataSource?.close()
        dataSource = null
    }

    @Test
    fun `procesar venta sin token responde 401`() =
        app {
            val response =
                client.post("/api/pos/ventas/procesar") {
                    header(HttpHeaders.ContentType, "application/json")
                    setBody("""{"factura":{}}""")
                }
            assertEquals(HttpStatusCode.Unauthorized, response.status)
        }

    @Test
    fun `procesar venta con body malformado cae al fallback interno vigente`() =
        app {
            val response =
                client.post("/api/pos/ventas/procesar") {
                    header(HttpHeaders.Authorization, "Bearer ${companyToken(adminDb = "ventas_seam")}")
                    header("Company-DB", "ventas_seam")
                    header(HttpHeaders.ContentType, "application/json")
                    setBody("""{venta rota""")
                }
            // Characterization: igual que en /auth/login, el receive malformado
            // lanza BadRequestException que NO es ApiException y el handler
            // genérico de StatusPages responde 500 estable. Contraste:
            // POST /api/cajas/close sí traduce payload inválido a 400.
            assertEquals(HttpStatusCode.InternalServerError, response.status)
            assertTrue(response.bodyAsText().contains("Error interno del servidor"))
        }

    @Test
    fun `reintento de venta con factura ya procesada responde 409 sin reprocesar`() =
        app {
            val db = "ventas_dup_${System.nanoTime()}"
            val database = seedH2CompanyDb(db)
            createSchemaAndSeed(database)

            val response =
                client.post("/api/pos/ventas/procesar") {
                    header(HttpHeaders.Authorization, "Bearer ${companyToken(adminDb = db)}")
                    // El header viaja VERBATIM al cache key case-sensitive.
                    header("Company-DB", db)
                    header(HttpHeaders.ContentType, "application/json")
                    setBody(DUPLICATE_SALE_BODY)
                }

            assertEquals(HttpStatusCode.Conflict, response.status)
            assertTrue(response.bodyAsText().contains("ya existe"), response.bodyAsText())
        }

    private companion object {
        const val DUPLICATE_ID_FACTURA = "dup-e2e-001"

        val DUPLICATE_SALE_BODY =
            """
            {
              "idFactura": "$DUPLICATE_ID_FACTURA",
              "factura": {
                "idCliente": "cli-1",
                "codCliente": "CLI-1",
                "codVendedor": 5,
                "idShop": 1,
                "idSucursal": 1,
                "idCaja": "1",
                "codigoCaja": "CAJA1",
                "idCajaSecuencia": "seq-1",
                "serieSucursal": "001",
                "formaPago": "contado",
                "subtotal": 10.0,
                "ivaTotalFactura": 0.7,
                "totalTotalFactura": 10.7,
                "montoItemsFactura": 10.0,
                "totalizarBaseImponible": 10.0,
                "totalizarMontoIva": 0.7,
                "totalizarTotalGeneral": 10.7,
                "usuarioCreacion": "cajero1"
              },
              "items": [
                {
                  "idItem": 1,
                  "_item_almacen": 1,
                  "_item_descripcion": "ITEM",
                  "_item_cantidad": 1.0,
                  "_item_preciosiniva": 10.0,
                  "_item_piva": 7.0,
                  "_item_totalsiniva": 10.0,
                  "_item_totalconiva": 10.7,
                  "_item_cantidad_total": 1.0
                }
              ],
              "pagoResumen": {
                "totalizarMontoCancelar": 10.7,
                "totalizarMontoEfectivo": 10.7,
                "totalizarCambio": 0.0,
                "totalizarSaldoPendiente": 0.0
              }
            }
            """.trimIndent()
    }
}
