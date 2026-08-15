package com.amaxoniaerp.features.pos.data

import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class FormasPagoRepositoryTest {
    private lateinit var database: Database
    private val repository = FormasPagoRepository()

    @BeforeTest
    fun setUp() {
        database =
            Database.connect(
                "jdbc:h2:mem:formas_pago_${System.nanoTime()};MODE=MySQL;DB_CLOSE_DELAY=-1",
                "org.h2.Driver",
            )
        transaction(database) {
            SchemaUtils.create(CajaFormaPagoTable)
            CajaFormaPagoTable.insert {
                it[idFormaPago] = 1
                it[siglas] = "EF"
                it[codigo] = 1
                it[descripcion] = "Efectivo"
                it[idCajaTpRegistro] = null
                it[activo] = 1
                it[pos] = 1
                it[imagen] = "cash"
                it[grupo] = 1
                it[orden] = 1
                it[idBancoCuenta] = 0
                it[idBancoOperacion] = 0
                it[tipoMoneda] = "D"
            }
        }
    }

    @AfterTest
    fun tearDown() {
        transaction(database) {
            SchemaUtils.drop(CajaFormaPagoTable)
        }
    }

    @Test
    fun `mapping preserves a valid tipo moneda`() =
        runBlocking {
            val formasPago = repository.listFormasPago(database, cajaId = null, tipoRegistro = emptyList())

            assertEquals("D", formasPago.single().tipoMoneda)
        }

    @Test
    fun `mapping normalizes legacy null tipo moneda to empty`() =
        runBlocking {
            transaction(database) {
                exec("ALTER TABLE caja_forma_pago ALTER COLUMN tipo_moneda DROP NOT NULL")
                exec("UPDATE caja_forma_pago SET tipo_moneda = NULL WHERE id_forma_pago = 1")
            }

            val formasPago = repository.listFormasPago(database, cajaId = null, tipoRegistro = emptyList())

            assertEquals("", formasPago.single().tipoMoneda)
        }
}
