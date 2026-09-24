package com.amaxoniaerp.features.electronicinvoice.data

import com.amaxoniaerp.features.facturas.data.FacturasTablePA
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Q1 (opción b): en un reintento, la factura DEBE reutilizar el
 * numeroDocumentoFiscal ya persistido; el correlativo sólo se consume cuando
 * la factura nunca recibió número. Esto elimina el desfase que produce el
 * rechazo DGI 1513 (número duplicado).
 */
class NumeroDocumentoFiscalTest {
    private lateinit var database: Database
    private val repository = ElectronicInvoiceRepository()

    @BeforeTest
    fun setUp() {
        database =
            Database.connect(
                url = "jdbc:h2:mem:pa_fe_${System.nanoTime()};MODE=MySQL;DB_CLOSE_DELAY=-1;LOCK_MODE=1",
                driver = "org.h2.Driver",
            )
        transaction(database) {
            SchemaUtils.create(FECorrelativosTable, FacturasTablePA)
        }
    }

    @AfterTest
    fun tearDown() {
        transaction(database) {
            SchemaUtils.drop(FECorrelativosTable, FacturasTablePA)
        }
    }

    @Test
    fun `reintento reutiliza el numero persistido sin leer correlativos`() {
        var lecturasCorrelativo = 0

        val numero =
            resolverNumeroDocumentoFiscal(
                persistido = "0000000012345",
                siguienteCorrelativo = {
                    lecturasCorrelativo++
                    "16"
                },
            )

        assertEquals("0000000012345", numero)
        assertEquals(0, lecturasCorrelativo)
    }

    @Test
    fun `primer envio consume el siguiente correlativo`() {
        for (persistido in listOf(null, "", "   ")) {
            val numero = resolverNumeroDocumentoFiscal(persistido) { "18" }
            assertEquals("18", numero)
        }
    }

    @Test
    fun `resolveNumeroDocumentoFiscal auto-inicializa fila cuando no existe y retorna 1`() {
        transaction(database) {
            assertEquals(0, FECorrelativosTable.selectAll().count())
            val resolved = resolveNumeroDocumentoFiscal()
            assertEquals("1", resolved)
            assertEquals(1, FECorrelativosTable.selectAll().count())
        }
    }

    @Test
    fun `resolveNumeroDocumentoFiscal se alinea con el maximo de facturas existentes para evitar duplicados`() {
        transaction(database) {
            FacturasTablePA.insert {
                it[idFactura] = "F-OLD-1"
                it[codFactura] = "FAC-1"
                it[codFacturaFiscal] = "CF-1"
                it[idCliente] = "CLI-1"
                it[codVendedor] = 1
                it[idSucursal] = 1
                it[idCaja] = "CAJA-1"
                it[formaPago] = "contado"
                it[numeroDocumentoFiscal] = "10"
            }
            FacturasTablePA.insert {
                it[idFactura] = "F-OLD-2"
                it[codFactura] = "FAC-2"
                it[codFacturaFiscal] = "CF-2"
                it[idCliente] = "CLI-1"
                it[codVendedor] = 1
                it[idSucursal] = 1
                it[idCaja] = "CAJA-1"
                it[formaPago] = "contado"
                it[numeroDocumentoFiscal] = "25"
            }

            // Aunque correlativos esté vacío o en 5, debe alinearse y generar 26
            val nextNumber = resolveNumeroDocumentoFiscal()
            assertEquals("26", nextNumber)
        }
    }

    @Test
    fun `incrementNumeroDocumentoFiscal incrementa correlativo y auto-crea fila si no existe`() =
        runBlocking {
            assertEquals(0L, transaction(database) { FECorrelativosTable.selectAll().count() })
            repository.incrementNumeroDocumentoFiscal(database)
            val contador =
                transaction(database) {
                    FECorrelativosTable.selectAll().single()[FECorrelativosTable.contador]
                }
            assertEquals(2, contador)

            repository.incrementNumeroDocumentoFiscal(database)
            val updatedContador =
                transaction(database) {
                    FECorrelativosTable.selectAll().single()[FECorrelativosTable.contador]
                }
            assertEquals(3, updatedContador)
        }

    @Test
    fun `emision secuencial consecutiva produce secuencia estricta sin saltos ni duplicados`() =
        runBlocking {
            transaction(database) {
                // Factura 1
                val num1 = resolveNumeroDocumentoFiscal()
                assertEquals("1", num1)
                FacturasTablePA.insert {
                    it[idFactura] = "F-1"
                    it[codFactura] = "FAC-1"
                    it[codFacturaFiscal] = "CF-1"
                    it[idCliente] = "CLI-1"
                    it[codVendedor] = 1
                    it[idSucursal] = 1
                    it[idCaja] = "CAJA-1"
                    it[formaPago] = "contado"
                    it[numeroDocumentoFiscal] = num1
                }
            }
            repository.incrementNumeroDocumentoFiscal(database)

            transaction(database) {
                // Factura 2 debe ser exactamente "2", no "3"
                val num2 = resolveNumeroDocumentoFiscal()
                assertEquals("2", num2)
                FacturasTablePA.insert {
                    it[idFactura] = "F-2"
                    it[codFactura] = "FAC-2"
                    it[codFacturaFiscal] = "CF-2"
                    it[idCliente] = "CLI-1"
                    it[codVendedor] = 1
                    it[idSucursal] = 1
                    it[idCaja] = "CAJA-1"
                    it[formaPago] = "contado"
                    it[numeroDocumentoFiscal] = num2
                }
            }
            repository.incrementNumeroDocumentoFiscal(database)

            transaction(database) {
                // Factura 3 debe ser exactamente "3", no "4" ni "5"
                val num3 = resolveNumeroDocumentoFiscal()
                assertEquals("3", num3)
            }
        }
}
