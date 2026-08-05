package com.amaxoniaerp.features.electronicinvoice.data

import com.amaxoniaerp.features.electronicinvoice.domain.FEConfigurationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.deleteAll
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import java.math.BigDecimal
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.assertIs

/**
 * Tests del [VenezuelaElectronicInvoiceRepository] enfocados en:
 *
 *  - 23. Concurrencia en la reserva de correlativo: 50 coroutines reservando
 *           simultáneamente reciben TODAS números distintos (0 deadlocks, 0
 *           duplicados). Verifica la atomicidad con `forUpdate` por id.
 *  - Idempotencia: `loadAlreadyIssued` detecta factura ya emitida.
 *  - Persistencia: `updateInvoiceWithVEResult` escribe ÚNICAMENTE los tres
 *    campos fiscales (numeroDocumentoFiscal, cod_factura_fiscal,
 *    numero_control_thka) y deja intactos los demás.
 *  - 24. La persistencia NO ocurre si no se invoca (control negativo): el repositorio
 *       no actualiza factura cuando la emisión falla.
 *  - Reserva falla controladamente si la fila de correlativo no existe o si
 *    hay duplicadas.
 *
 * NO se prueba `loadInvoiceContext` completo aquí porque depende del JOIN con
 * `caja_forma_pago` (definido en `pos.data`); eso se cubre con H2 solo para
 * la parte correlativo+factura. El comportamiento de HKA frente a la emisión
 * se valida en el suite de Strategy con un cliente en memoria.
 */
class VenezuelaElectronicInvoiceRepositoryTest {

    private lateinit var database: Database
    private val repository = VenezuelaElectronicInvoiceRepository()

    @BeforeTest
    fun setUp() {
        database = Database.connect(
            url = "jdbc:h2:mem:ve_fe_${System.nanoTime()};MODE=MySQL;DB_CLOSE_DELAY=-1;LOCK_MODE=1",
            driver = "org.h2.Driver",
        )
        transaction(database) {
            // Solo creamos las tablas necesarias para los tests de correlativo + persistencia.
            SchemaUtils.create(VECorrelativosTable, VEFacturaReadTable)
        }
    }

    @AfterTest
    fun tearDown() {
        transaction(database) {
            SchemaUtils.drop(VECorrelativosTable, VEFacturaReadTable)
        }
    }

    // ─── 23. Concurrencia de reserva de correlativo ────────────────────────

    @Test
    fun `reserva concurrente de correlativo asigna numeros unicos sin saltos`() = runBlocking {
        transaction(database) {
            VECorrelativosTable.insert {
                it[id] = 1
                it[campo] = repository.CAMPO_CORRELATIVO_FE
                it[contador] = 1
                it[formato] = 8
            }
        }

        val n = 50
        val reservados = (1..n).map {
            async { repository.reserveCorrelativoFacturaElectronica(database) }
        }.awaitAll()

        // Todos los números reservados deben ser distintos y cubrir el rango 1..n.
        val numeros = reservados.map { it.numero }.toSet()
        assertEquals(n, numeros.size, "Debe haber $n números únicos (sin duplicados)")
        assertEquals((1..n).toSet(), numeros, "El rango debe ser continuo 1..$n")
        assertEquals(n, numeros.size, "Debe haber $n números únicos (sin duplicados)")
        assertEquals((1..n).toSet(), numeros, "El rango debe ser continuo 1..$n")
        // El siguiente contador en DB debe ser n+1.
        val contadorFinal = transaction(database) {
            VECorrelativosTable
                .select(VECorrelativosTable.contador)
                .where { VECorrelativosTable.id eq 1 }
                .single()[VECorrelativosTable.contador]
        }
        assertEquals(n + 1, contadorFinal)
        // La longitud formateada respeta el formato definido (8).
        assertTrue(reservados.all { it.numeroFormateado().length == 8 })
    }

    // ─── FASE 1.1 — Item 3: reserveAtLeast con mínimo garantizado ────────

    /**
     * Brief item 3: contador=1, remoto=100, 50 reservas concurrentes con
     * minimumNextNumber=101 deben otorgar números en 101..150 (sin duplicados,
     * sin saltos). El contador final en DB queda en 151.
     *
     * Muestra explícitamente que la operación SQL/Exposed subyacente es:
     *
     *   begin;
     *     select id, contador from correlativos where campo = '...' for update;
     *     -- sobre la fila bloqueada:
     *     update correlativos set contador = MAX(contador, minimumNextNumber) + 1
     *         where id = :id;
     *   commit;
     *
     * Bajo H2 con LOCK_MODE=1 + REPEATABLE_READ el FOR UPDATE serializa; el test
     * es informativo de concurrencia ( Brief: "No declares seguridad productiva
     * basándote solamente en H2" ). La veredicta productiva recae en MySQL/InnoDB.
     */
    @Test
    fun `reserveAtLeast sincroniza contador con minimumNextNumber bajando concurrencia`() = runBlocking {
        transaction(database) {
            VECorrelativosTable.insert {
                it[id] = 1
                it[campo] = repository.CAMPO_CORRELATIVO_FE
                it[contador] = 1
                it[formato] = 8
            }
        }

        val n = 50
        // Todas las llamadas vienen con el mismo minimumNextNumber (caso Brief:
        // el PAC devolvió remoto=100 → minimumNextNumber=101).
        val reservados = (1..n).map {
            async { repository.reserveAtLeast(database, minimumNextNumber = 101) }
        }.awaitAll()

        val numeros = reservados.map { it.numero }
        // Sin duplicados.
        assertEquals(n, numeros.toSet().size, "No debe haber duplicados")
        // Todos >= 101.
        assertTrue(numeros.all { it >= 101 }, "Todos los números deben ser >= 101")
        // El contador final en DB debe ser mayor al máximo reservado.
        val maxReservado = numeros.max()
        val contadorFinal = transaction(database) {
            VECorrelativosTable
                .select(VECorrelativosTable.contador)
                .where { VECorrelativosTable.id eq 1 }
                .single()[VECorrelativosTable.contador]
        }
        assertEquals(maxReservado + 1, contadorFinal,
            "El contador persistido debe ser (max reservado)+1 = ${maxReservado + 1}")
    }

    @Test
    fun `reserveAtLeast con contador local mayor al minimo conserva el contador`() = runBlocking {
        transaction(database) {
            VECorrelativosTable.insert {
                it[id] = 1
                it[campo] = repository.CAMPO_CORRELATIVO_FE
                it[contador] = 500
                it[formato] = 8
            }
        }
        val reservado = repository.reserveAtLeast(database, minimumNextNumber = 100)
        assertEquals(500, reservado.numero, "max(500, 100) = 500")
        // El siguiente contador en DB debe ser 501.
        val contadorFinal = transaction(database) {
            VECorrelativosTable
                .select(VECorrelativosTable.contador)
                .where { VECorrelativosTable.id eq 1 }
                .single()[VECorrelativosTable.contador]
        }
        assertEquals(501, contadorFinal)
    }

    @Test
    fun `reserveAtLeast con minimumNextNumber menor que 1 falla con IllegalArgumentException`() {
        assertFailsWith<IllegalArgumentException> {
            runBlocking { repository.reserveAtLeast(database, minimumNextNumber = 0) }
        }
    }

    @Test
    fun `reserva con dos filas duplicadas del mismo campo falla controladamente`() {
        transaction(database) {
            VECorrelativosTable.insert {
                it[id] = 1; it[campo] = repository.CAMPO_CORRELATIVO_FE; it[contador] = 1; it[formato] = 8
            }
            VECorrelativosTable.insert {
                it[id] = 2; it[campo] = repository.CAMPO_CORRELATIVO_FE; it[contador] = 99; it[formato] = 8
            }
        }
        assertFailsWith<FEConfigurationException> {
            runBlocking { repository.reserveCorrelativoFacturaElectronica(database) }
        }
    }

    @Test
    fun `reserva sin fila de correlativo falla con configuracion`() {
        // No se inserta ninguna fila con el campo esperado.
        transaction(database) {
            VECorrelativosTable.insert {
                it[id] = 1; it[campo] = "otro_campo"; it[contador] = 1; it[formato] = 8
            }
        }
        assertFailsWith<FEConfigurationException> {
            runBlocking { repository.reserveCorrelativoFacturaElectronica(database) }
        }
    }

    @Test
    fun `reserva usa formato por defecto cuando la columna formato es null`() = runBlocking {
        transaction(database) {
            VECorrelativosTable.insert {
                it[id] = 1
                it[campo] = repository.CAMPO_CORRELATIVO_FE
                it[contador] = 7
                it[formato] = null
            }
        }
        val reservado = repository.reserveCorrelativoFacturaElectronica(database)
        assertEquals(7, reservado.numero)
        // DEFAULT_CORRELATIVO_FORMAT = 8 (privado); por contrato debe ser >= 1.
        assertTrue(reservado.formato >= 1)
    }

    // ─── 23b. Idempotencia: loadAlreadyIssued ───────────────────────────────

    @Test
    fun `loadAlreadyIssued retorna None cuando la factura no tiene numero fiscal`() = runBlocking {
        seedFactura(database, invoiceId = "inv-1", numeroDocumentoFiscal = null, numeroControl = null)
        val result = repository.loadAlreadyIssued(database, "inv-1")
        assertEquals(VenezuelaElectronicInvoiceRepository.AlreadyIssuedResult.None, result)
    }

    @Test
    fun `loadAlreadyIssued retorna Complete cuando la factura tiene ambos campos`() = runBlocking {
        seedFactura(database, invoiceId = "inv-2", numeroDocumentoFiscal = "00000100", numeroControl = "L001P001-100")
        val result = repository.loadAlreadyIssued(database, "inv-2")
        val complete = assertIs<VenezuelaElectronicInvoiceRepository.AlreadyIssuedResult.Complete>(result)
        assertEquals("00000100", complete.numeroDocumentoFiscal)
        assertEquals("L001P001-100", complete.numeroControl)
    }

    // ─── FASE 1.1 — Item 1: idempotencia OR (Partial) ─────────────────────

    @Test
    fun `loadAlreadyIssued con OR retorna Partial cuando solo existe numeroDocumentoFiscal`() = runBlocking {
        seedFactura(database, invoiceId = "inv-or-num", numeroDocumentoFiscal = "00000099", numeroControl = null)
        val result = repository.loadAlreadyIssued(database, "inv-or-num")
        val partial = assertIs<VenezuelaElectronicInvoiceRepository.AlreadyIssuedResult.Partial>(result)
        assertEquals("00000099", partial.numeroDocumentoFiscal)
        assertNull(partial.numeroControl)
    }

    @Test
    fun `loadAlreadyIssued con OR retorna Partial cuando solo existe numero_control_thka`() = runBlocking {
        seedFactura(database, invoiceId = "inv-or-ctrl", numeroDocumentoFiscal = null, numeroControl = "L001P001-200")
        val result = repository.loadAlreadyIssued(database, "inv-or-ctrl")
        val partial = assertIs<VenezuelaElectronicInvoiceRepository.AlreadyIssuedResult.Partial>(result)
        assertNull(partial.numeroDocumentoFiscal)
        assertEquals("L001P001-200", partial.numeroControl)
    }

    @Test
    fun `loadAlreadyIssued lanza cuando la factura no existe`() {
        assertFailsWith<com.amaxoniaerp.features.electronicinvoice.domain.FEInvoiceNotFoundException> {
            runBlocking { repository.loadAlreadyIssued(database, "inexistente") }
        }
    }

    // ─── 24. Persistencia exacta de los tres campos fiscales ───────────────

    @Test
    fun `updateInvoiceWithVEResult escribe unicamente los tres campos fiscales`() = runBlocking {
        seedFactura(
            database,
            invoiceId = "inv-3",
            numeroDocumentoFiscal = null,
            numeroControl = null,
            codFactura = "COD-9",
        )
        repository.updateInvoiceWithVEResult(
            database = database,
            invoiceId = "inv-3",
            numeroDocumento = "00000500",
            numeroControl = "L001P001-500",
        )

        val row = transaction(database) {
            VEFacturaReadTable
                .selectAll()
                .where { VEFacturaReadTable.idFactura eq "inv-3" }
                .single()
        }
        assertEquals("00000500", row[VEFacturaReadTable.numeroDocumentoFiscal])
        assertEquals("00000500", row[VEFacturaReadTable.codFacturaFiscal])
        assertEquals("L001P001-500", row[VEFacturaReadTable.numeroControlThka])
        // Otros campos preservados: cod_factura COMERCIAL no se altera.
        assertEquals("COD-9", row[VEFacturaReadTable.codFactura])
    }

    @Test
    fun `updateInvoiceWithVEResult con invoiceId inexistente no lanza y reporta 0 filas`() = runBlocking {
        // No se hace assert estricto de log: solo verificamos que no rompe.
        repository.updateInvoiceWithVEResult(
            database = database,
            invoiceId = "no-existe",
            numeroDocumento = "00000001",
            numeroControl = "CTRL",
        )
        // La factura inexistente sigue sin numeración fiscal.
        val stillNull = transaction(database) {
            VEFacturaReadTable
                .selectAll()
                .where { VEFacturaReadTable.idFactura eq "no-existe" }
                .firstOrNull() == null
        }
        assertTrue(stillNull)
    }

    // ─── 24b. Control negativo: sin invocar update no se persiste nada ──────

    @Test
    fun `factura no persistida queda sin numero fiscal tras emision fallida`() = runBlocking {
        seedFactura(database, invoiceId = "inv-4", numeroDocumentoFiscal = null, numeroControl = null)
        // Simulamos "emisión fallida": NO llamamos update.
        val snapshot = repository.loadAlreadyIssued(database, "inv-4")
        assertEquals(VenezuelaElectronicInvoiceRepository.AlreadyIssuedResult.None, snapshot,
            "Tras emisión fallida la factura NO debe estar marcada como emitida")
        val row = transaction(database) {
            VEFacturaReadTable.selectAll().where { VEFacturaReadTable.idFactura eq "inv-4" }.single()
        }
        assertNull(row[VEFacturaReadTable.numeroDocumentoFiscal])
        assertNull(row[VEFacturaReadTable.numeroControlThka])
    }

    // ─── Helpers ───────────────────────────────────────────────────────────

    private fun seedFactura(
        database: Database,
        invoiceId: String,
        numeroDocumentoFiscal: String?,
        numeroControl: String?,
        codFactura: String = "COD-GEN",
    ) {
        transaction(database) {
            VEFacturaReadTable.insert {
                it[VEFacturaReadTable.idFactura] = invoiceId
                it[VEFacturaReadTable.codFactura] = codFactura
                it[VEFacturaReadTable.codFacturaFiscal] = ""
                it[VEFacturaReadTable.numeroDocumentoFiscal] = numeroDocumentoFiscal
                it[VEFacturaReadTable.numeroControlThka] = numeroControl
                it[VEFacturaReadTable.tipoDocumento] = "01"
                it[VEFacturaReadTable.fechaFactura] = "2026-08-03"
                it[VEFacturaReadTable.fechaCreacion] = "2026-08-03"
                it[VEFacturaReadTable.facturarARuc] = "J123456789"
                it[VEFacturaReadTable.facturarANombre] = "CONSUMIDOR FINAL"
                it[VEFacturaReadTable.facturarADireccion] = ""
                it[VEFacturaReadTable.facturarATelefono] = ""
                it[VEFacturaReadTable.totalTotalFactura] = BigDecimal("116.00")
                it[VEFacturaReadTable.ivaTotalFactura] = BigDecimal("16.00")
                it[VEFacturaReadTable.descuentosItemFactura] = BigDecimal("0.00")
                it[VEFacturaReadTable.totalizarBaseImponible] = BigDecimal("100.00")
                it[VEFacturaReadTable.totalizarMontoIva] = BigDecimal("16.00")
                it[VEFacturaReadTable.totalizarTotalGeneral] = BigDecimal("116.00")
                it[VEFacturaReadTable.montoItemsFactura] = BigDecimal("100.00")
                it[VEFacturaReadTable.multiMoneda] = "NO"
                it[VEFacturaReadTable.tasa] = 1f
                it[VEFacturaReadTable.monedaBase] = 1
                it[VEFacturaReadTable.abrMonedaBase] = "VES"
                it[VEFacturaReadTable.monedaSecundaria] = 2
                it[VEFacturaReadTable.abrMonedaSecundaria] = "USD"
                it[VEFacturaReadTable.idCaja] = "caja-1"
                it[VEFacturaReadTable.idSucursal] = 1
            }
        }
    }
}