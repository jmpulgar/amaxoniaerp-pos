package com.amaxoniaerp.features.kiosk.route

import com.amaxoniaerp.JwtConfig
import com.amaxoniaerp.features.caja.data.CajaTablePA
import com.amaxoniaerp.features.caja.data.SucursalAlmacenTable
import com.amaxoniaerp.features.caja.data.SucursalTable
import com.amaxoniaerp.features.caja.data.VendedorTable
import com.amaxoniaerp.features.clients.data.ClientsTable
import com.amaxoniaerp.features.kiosk.application.KioskService
import com.amaxoniaerp.features.kiosk.data.ComboGroupItemTable
import com.amaxoniaerp.features.kiosk.data.ComboGroupTable
import com.amaxoniaerp.features.kiosk.data.ComboItemTable
import com.amaxoniaerp.features.kiosk.data.KioskOrderItemModifierTable
import com.amaxoniaerp.features.kiosk.data.KioskOrderItemTable
import com.amaxoniaerp.features.kiosk.data.KioskOrderTable
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.jwt.jwt
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation as ServerContentNegotiation

/**
 * Infraestructura compartida de los tests del kiosco: H2 en modo MySQL con las tablas del ERP
 * que reutiliza el kiosco (parametros_generales, caja, sucursal, vendedor, clientes, combos y
 * kiosco_pedido*), token de empresa del POS y headers X-Kiosk-Caja / X-Kiosk-Prefix.
 */
internal object KioskTestSupport {
    const val COUNTRY = "PA"
    const val COMPANY_DB = "momi_pa"
    const val CAJA_ID = "CAJA-01"
    const val PREFIX = "K1"
    const val USER_ID = 7
    const val SELLER_ID = 10

    val jwtConfig =
        JwtConfig(
            secret = "test-secret-kiosk-test-must-be-very-long-32-chars",
            domain = "http://localhost:8080",
            audience = "http://localhost:8080/kiosk",
            realm = "Amaxonia Kiosk Test",
        )

    val json =
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = false
            explicitNulls = false
        }

    /** Columnas opcionales del kiosco en parametros_generales (migración del administrativo). */
    const val KIOSK_PARAMETROS_COLUMNS =
        """
        kiosco_destino_pedido VARCHAR(30) NOT NULL DEFAULT 'RETIRO_MOSTRADOR',
        kiosco_impresora_cocina_ip VARCHAR(45) NULL,
        kiosco_modalidades VARCHAR(30) NOT NULL DEFAULT 'COMER_AQUI,PARA_LLEVAR',
        clave_kiosko VARCHAR(255) NULL,
        banner_1 VARCHAR(255) NULL,
        banner_2 VARCHAR(255) NULL,
        banner_3 VARCHAR(255) NULL,
        menu_1 VARCHAR(255) NULL,
        """

    fun newDataSource(prefix: String): HikariDataSource =
        HikariDataSource(
            HikariConfig().apply {
                jdbcUrl = "jdbc:h2:mem:${prefix}_${System.nanoTime()};MODE=MySQL;DB_CLOSE_DELAY=-1"
                driverClassName = "org.h2.Driver"
                maximumPoolSize = 2
                isAutoCommit = false
            },
        )

    /** Token de empresa del POS (`POST /auth/company`), sin expiración. */
    fun companyToken(
        tokenType: String = "company",
        userId: Int? = USER_ID,
        countryCode: String = COUNTRY,
        adminDb: String = COMPANY_DB,
    ): String =
        JWT
            .create()
            .withIssuer(jwtConfig.domain)
            .withAudience(jwtConfig.audience)
            .withClaim("token_type", tokenType)
            .withClaim("country_code", countryCode)
            .withClaim("admin_db", adminDb)
            .apply { if (userId != null) withClaim("user_id", userId) }
            .sign(Algorithm.HMAC256(jwtConfig.secret))

    /**
     * parametros_generales PA. [optionalColumns] agrega columnas opcionales (por defecto las del
     * kiosco); pasar "" simula un tenant sin la migración del kiosco.
     */
    fun createParametrosGenerales(
        database: Database,
        optionalColumns: String = KIOSK_PARAMETROS_COLUMNS,
        validarStock: String = "SI",
        rif: String? = "155688-1-554433",
    ) {
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
                    $optionalColumns
                    tipo_facturacion INT NOT NULL DEFAULT 0
                );
                """.trimIndent(),
            )
            val rifValue = rif?.let { "'$it'" } ?: "NULL"
            exec(
                "INSERT INTO parametros_generales (cod_empresa, validar_stock, porcentaje_impuesto_principal, rif) " +
                    "VALUES (1, '$validarStock', 7.00, $rifValue);",
            )
        }
    }

    /**
     * Caja activa [CAJA_ID] en la sucursal 1 (almacén por defecto 3), vendedor [SELLER_ID]
     * asignado al usuario [USER_ID] y cliente genérico "CF".
     */
    fun createCajaSchema(
        database: Database,
        cajaDescripcion: String = "Caja Kiosco 1",
    ) {
        transaction(database) {
            SchemaUtils.create(SucursalTable, SucursalAlmacenTable, VendedorTable, CajaTablePA, ClientsTable)
            SucursalTable.insert {
                it[idSucursal] = 1
                it[codigo] = "SUC-01"
                it[serie] = "01"
                it[sucursal] = "Multiplaza Mall"
                it[descripcion] = "Vía Israel, Ciudad de Panamá"
            }
            SucursalAlmacenTable.insert {
                it[idSucursal] = 1
                it[idAlmacen] = 3
                it[defaultVentas] = 1
            }
            VendedorTable.insert {
                it[idVendedor] = SELLER_ID
                it[codVendedor] = SELLER_ID
                it[nombre] = "KIOSCO"
                it[codUsuarios] = USER_ID.toString()
                it[idTiendas] = "1"
                it[idCajas] = CAJA_ID
                it[activo] = 1
            }
            CajaTablePA.insert {
                it[idCaja] = CAJA_ID
                it[codCaja] = "C01"
                it[serieCaja] = "01"
                it[descripcion] = cajaDescripcion
                it[idSucursal] = 1
                it[codEstatus] = 1
            }
            ClientsTable.insert {
                it[idCliente] = "CF"
                it[codCliente] = "CF"
                it[rif] = "CF"
                it[dv] = "0"
                it[nombre] = "CONSUMIDOR FINAL"
                it[direccion] = "Panamá"
            }
        }
    }

    /** kiosco_pedido, kiosco_pedido_item y kiosco_pedido_item_modificador. */
    fun createKioskOrderTables(database: Database) {
        transaction(database) { SchemaUtils.create(KioskOrderTable, KioskOrderItemTable, KioskOrderItemModifierTable) }
    }

    /** item_combos, grupos y grupo_items con las columnas de 2026-10-01-RECETA-COMBO-VARIANTES. */
    fun createComboTables(database: Database) {
        transaction(database) { SchemaUtils.create(ComboItemTable, ComboGroupTable, ComboGroupItemTable) }
    }

    fun ApplicationTestBuilder.kioskClient(kioskService: KioskService): HttpClient {
        install(ServerContentNegotiation) { json(json) }
        install(Authentication) {
            jwt {
                realm = jwtConfig.realm ?: "Amaxonia Kiosk Test"
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

    /** Token de empresa + caja + prefijo del kiosco. */
    fun HttpRequestBuilder.kioskHeaders(
        token: String = companyToken(),
        caja: String? = CAJA_ID,
        prefix: String? = PREFIX,
    ) {
        header(HttpHeaders.Authorization, "Bearer $token")
        if (caja != null) header(KIOSK_CAJA_HEADER, caja)
        if (prefix != null) header(KIOSK_PREFIX_HEADER, prefix)
    }
}
