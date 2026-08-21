package com.amaxonia.pos.data.local.db

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.amaxonia.pos.domain.model.sales.FiscalState
import com.amaxonia.pos.domain.usecase.payment.QueueFiscalConfirmationUseCase
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Semántica del ledger de transacciones usada por los workers: aislamiento por
 * tenant, lease anti-duplicado, backoff de reintentos y máquina de estados del
 * callback de gateway (TASK-084).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TransactionLogDaoTest {
    private lateinit var db: AppDatabase
    private lateinit var dao: TransactionLogDao

    private val now = 1_000_000L

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db =
            Room
                .inMemoryDatabaseBuilder(context, AppDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        dao = db.transactionLogDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private data class EntrySpec(
        val tenantId: String = TENANT_A,
        val fiscalStatus: String = "PENDING",
        val nextAttemptAt: Long = 0L,
        val leasedUntil: Long = 0L,
        val gatewayStatus: String = "IGNORED",
    )

    private fun entry(
        id: String,
        spec: EntrySpec = EntrySpec(),
    ): TransactionLogEntity =
        TransactionLogEntity(
            clientCorrelationId = id,
            idCaja = "caja",
            idCajaSecuencia = "seq",
            totalAmount = 10.0,
            currency = "USD",
            clientName = "Cliente",
            status = "CONFIRMED",
            fiscalConfirmationStatus = spec.fiscalStatus,
            fiscalConfirmationRetryCount = 0,
            fiscalConfirmationNextAttemptAt = spec.nextAttemptAt,
            fiscalConfirmationLeasedUntil = spec.leasedUntil,
            gatewayCallbackStatus = spec.gatewayStatus,
            tenantId = spec.tenantId,
            createdAt = now,
            updatedAt = now,
        )

    @Test
    fun `findFiscalConfirmableForTenant solo devuelve filas del tenant pendientes y sin lease`() =
        runTest {
            dao.upsert(entry("a"))
            dao.upsert(entry("b", EntrySpec(fiscalStatus = "CONFIRMED")))
            dao.upsert(entry("c", EntrySpec(nextAttemptAt = now + 5_000)))
            dao.upsert(entry("d", EntrySpec(leasedUntil = now + 30_000)))
            dao.upsert(entry("e", EntrySpec(tenantId = TENANT_B)))

            val rows = dao.findFiscalConfirmableForTenant(TENANT_A, now)

            assertEquals(listOf("a"), rows.map { it.clientCorrelationId })
        }

    @Test
    fun `un lease activo impide ejecucion duplicada y al expirar la fila vuelve a elegirse`() =
        runTest {
            dao.upsert(entry("a"))

            dao.leaseFiscal("a", now + 60_000, updatedAt = now)
            assertTrue(dao.findFiscalConfirmableForTenant(TENANT_A, now).isEmpty())

            val afterExpiry = now + 61_000
            assertEquals(
                listOf("a"),
                dao.findFiscalConfirmableForTenant(TENANT_A, afterExpiry).map { it.clientCorrelationId },
            )
        }

    @Test
    fun `tryClaimFiscal es atomico - solo un claim dentro de la ventana de lease`() =
        runTest {
            dao.upsert(entry("a"))

            assertEquals(1, dao.tryClaimFiscal("a", now, now + 60_000, updatedAt = now))
            assertEquals(0, dao.tryClaimFiscal("a", now, now + 120_000, updatedAt = now))
            assertEquals(1, dao.tryClaimFiscal("a", now + 60_000, now + 120_000, updatedAt = now))
        }

    @Test
    fun `markFiscalRetriable incrementa reintentos y agenda el backoff`() =
        runTest {
            dao.upsert(entry("a"))

            dao.markFiscalRetriable(
                id = "a",
                status = "RETRYABLE_PENDING",
                remoteInvoiceId = "remote-1",
                fiscalNumber = null,
                printerSerial = null,
                nextAttemptAt = now + 120_000,
                message = "timeout",
                updatedAt = now,
            )

            val row = dao.findById("a")
            assertNotNull(row)
            assertEquals("RETRYABLE_PENDING", row?.fiscalConfirmationStatus)
            assertEquals(1, row?.fiscalConfirmationRetryCount)
            assertEquals(now + 120_000, row?.fiscalConfirmationNextAttemptAt)
            assertEquals("timeout", row?.lastError)
            assertEquals(0L, row?.fiscalConfirmationLeasedUntil)
            assertTrue(dao.findFiscalConfirmableForTenant(TENANT_A, now).isEmpty())
            assertEquals(
                listOf("a"),
                dao.findFiscalConfirmableForTenant(TENANT_A, now + 120_000).map { it.clientCorrelationId },
            )
        }

    @Test
    fun `markFiscalTerminal limpia agenda y lease`() =
        runTest {
            dao.upsert(entry("a", EntrySpec(nextAttemptAt = now + 1, leasedUntil = now + 1)))

            dao.markFiscalTerminal(
                id = "a",
                status = QueueFiscalConfirmationUseCase.STATUS_TERMINAL_FAILED,
                message = "agotado",
                updatedAt = now,
            )

            val row = dao.findById("a")
            assertEquals(QueueFiscalConfirmationUseCase.STATUS_TERMINAL_FAILED, row?.fiscalConfirmationStatus)
            assertEquals(0L, row?.fiscalConfirmationNextAttemptAt)
            assertEquals(0L, row?.fiscalConfirmationLeasedUntil)
        }

    @Test
    fun `markFiscalConfirmed consolida el numero fiscal`() =
        runTest {
            dao.upsert(entry("a"))

            dao.markFiscalConfirmed(
                id = "a",
                status = QueueFiscalConfirmationUseCase.STATUS_CONFIRMED,
                fiscalNumber = "NC-1",
                printerSerial = "SER-1",
                updatedAt = now,
            )

            val row = dao.findById("a")
            assertEquals(QueueFiscalConfirmationUseCase.STATUS_CONFIRMED, row?.fiscalConfirmationStatus)
            assertEquals("NC-1", row?.fiscalNumber)
            assertEquals("SER-1", row?.printerSerial)
            assertNull(row?.lastError)
        }

    @Test
    fun `transitionFiscalState es CAS - 0 si el estado actual no coincide`() =
        runTest {
            dao.upsert(entry("a"))

            assertEquals(
                1,
                dao.transitionFiscalState(
                    "a",
                    FiscalState.PRINTED_PENDING_CONFIRM,
                    listOf(FiscalState.NOT_APPLICABLE),
                    updatedAt = now,
                ),
            )
            assertEquals(
                0,
                dao.transitionFiscalState(
                    "a",
                    FiscalState.CONFIRMED,
                    listOf(FiscalState.NOT_APPLICABLE),
                    updatedAt = now,
                ),
            )
        }

    @Test
    fun `findGatewayReconcilableForTenant respeta tenant, estados y lease`() =
        runTest {
            dao.upsert(entry("a", EntrySpec(gatewayStatus = "AWAITING")))
            dao.upsert(entry("b", EntrySpec(gatewayStatus = "RESOLVED")))
            dao.upsert(entry("c", EntrySpec(gatewayStatus = "AWAITING", tenantId = TENANT_B)))

            val rows = dao.findGatewayReconcilableForTenant(TENANT_A, now)

            assertEquals(listOf("a"), rows.map { it.clientCorrelationId })
        }

    @Test
    fun `markGatewayResolved resuelve filas AWAITING y siempre audita el callback`() =
        runTest {
            dao.upsert(entry("a", EntrySpec(gatewayStatus = "AWAITING")))

            dao.markGatewayResolved(
                id = "a",
                status = "RESOLVED",
                resultCode = "200",
                rawResponse = """{"code":"200"}""",
                message = "Aprobado",
                updatedAt = now,
            )

            val row = dao.findById("a")
            assertEquals("RESOLVED", row?.gatewayCallbackStatus)
            assertEquals("200", row?.gatewayResultCode)
            assertEquals("""{"code":"200"}""", row?.gatewayRawResponse)
            assertEquals("Aprobado", row?.gatewayResultMessage)
        }

    @Test
    fun `markGatewayResolved no resucita una fila marcada terminal pero conserva la auditoria`() =
        runTest {
            dao.upsert(entry("a", EntrySpec(gatewayStatus = "TERMINAL_AWAITING")))

            dao.markGatewayResolved(
                id = "a",
                status = "RESOLVED",
                resultCode = "200",
                rawResponse = "{}",
                message = "tardío",
                updatedAt = now,
            )

            val row = dao.findById("a")
            assertEquals("TERMINAL_AWAITING", row?.gatewayCallbackStatus)
            assertEquals("200", row?.gatewayResultCode)
            assertEquals("tardío", row?.gatewayResultMessage)
        }

    @Test
    fun `tenantIdOf expone el tenant canonico de la fila`() =
        runTest {
            dao.upsert(entry("a", EntrySpec(tenantId = TENANT_B)))

            assertEquals(TENANT_B, dao.tenantIdOf("a"))
            assertNull(dao.tenantIdOf("missing"))
        }

    private companion object {
        const val TENANT_A = "t1"
        const val TENANT_B = "t2"
    }
}
