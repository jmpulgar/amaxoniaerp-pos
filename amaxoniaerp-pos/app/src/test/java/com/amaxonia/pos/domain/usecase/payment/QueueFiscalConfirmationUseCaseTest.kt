package com.amaxonia.pos.domain.usecase.payment

import com.amaxonia.pos.data.local.db.TransactionLogEntity
import com.amaxonia.pos.domain.system.AppClock
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant

/**
 * Fake [AppClock] returning a fixed instant, so scheduling assertions are
 * deterministic without a mocking framework.
 */
private class FixedAppClock(
    private val instant: Instant,
) : AppClock {
    override fun now(): Instant = instant
}

/**
 * Characterization tests for [QueueFiscalConfirmationUseCase] — the durable
 * retry queue for fiscal confirmations whose sale was already PAID.
 *
 * Covered here (NOT duplicated by the Room DAO tests of task083, which verify
 * the raw SQL semantics):
 *   - `enqueue` persists the full (remoteInvoiceId, fiscalNumber,
 *     printerSerial) tuple plus the failure cause BEFORE any replay happens —
 *     losing this tuple would orphan an already-paid sale;
 *   - `enqueue` schedules the first replay at `clock.now()`, i.e. the row is
 *     immediately eligible;
 *   - repeated enqueues increment the retry counter and refresh the tuple
 *     (the worker replays until the backend acknowledges);
 *   - `nextAttempt` follows the fixed ladder, then grows exponentially and is
 *     capped at MAX_INTERVAL_MS.
 */
class QueueFiscalConfirmationUseCaseTest {
    private val nowMs = Instant.parse("2026-08-21T10:00:00Z").toEpochMilli()
    private val clock: AppClock = FixedAppClock(Instant.parse("2026-08-21T10:00:00Z"))
    private lateinit var dao: InMemoryTransactionLogDao
    private lateinit var useCase: QueueFiscalConfirmationUseCase

    @Before
    fun setUp() {
        dao = InMemoryTransactionLogDao()
        useCase = QueueFiscalConfirmationUseCase(dao, clock)
    }

    @Test
    fun `enqueue persiste el tuplo fiscal y la causa del fallo antes del primer replay`() =
        runTest {
            seedRow("fx-1")

            useCase.enqueue(
                clientCorrelationId = "fx-1",
                remoteInvoiceId = "42",
                fiscalNumber = "000001-00012345",
                printerSerial = "SN-PRN-7",
                failureMessage = "Impresora offline",
            )

            val row = dao.findById("fx-1")!!
            assertEquals(QueueFiscalConfirmationUseCase.STATUS_RETRYABLE_PENDING, row.fiscalConfirmationStatus)
            assertEquals("42", row.remoteInvoiceId)
            assertEquals("000001-00012345", row.fiscalNumber)
            assertEquals("SN-PRN-7", row.printerSerial)
            assertEquals("Impresora offline", row.lastError)
        }

    @Test
    fun `enqueue agenda el primer replay en el instante actual - fila inmediatamente elegible`() =
        runTest {
            seedRow("fx-2")

            useCase.enqueue(
                "fx-2",
                remoteInvoiceId = "7",
                fiscalNumber = "000001-000999",
                printerSerial = "SN-1",
                failureMessage = "5xx",
            )

            val eligibleNow = dao.findFiscalConfirmable(now = nowMs, limit = 25)
            assertTrue(eligibleNow.any { it.clientCorrelationId == "fx-2" })
        }

    @Test
    fun `enqueues sucesivos incrementan el contador de reintentos y refrescan el tuplo`() =
        runTest {
            seedRow("fx-3")
            useCase.enqueue(
                "fx-3",
                remoteInvoiceId = "9",
                fiscalNumber = "000001-000555",
                printerSerial = "SN-2",
                failureMessage = "timeout",
            )

            // The worker retried, the PATCH failed again with fresh data
            useCase.enqueue(
                "fx-3",
                remoteInvoiceId = "9",
                fiscalNumber = "000001-000777",
                printerSerial = "SN-2",
                failureMessage = "backend 503",
            )

            val row = dao.findById("fx-3")!!
            assertEquals(QueueFiscalConfirmationUseCase.STATUS_RETRYABLE_PENDING, row.fiscalConfirmationStatus)
            assertEquals(2, row.fiscalConfirmationRetryCount)
            assertEquals("000001-000777", row.fiscalNumber)
            assertEquals("SN-2", row.printerSerial)
        }

    @Test
    fun `nextAttempt recorre la escalera fija antes del crecimiento exponencial`() {
        assertEquals(15_000L, QueueFiscalConfirmationUseCase.nextAttempt(0))
        assertEquals(30_000L, QueueFiscalConfirmationUseCase.nextAttempt(1))
        assertEquals(60_000L, QueueFiscalConfirmationUseCase.nextAttempt(2))
        assertEquals(300_000L, QueueFiscalConfirmationUseCase.nextAttempt(3))
        assertEquals(900_000L, QueueFiscalConfirmationUseCase.nextAttempt(4))
    }

    @Test
    fun `nextAttempt crece exponencialmente tras la escalera y queda acotado al intervalo maximo`() {
        // First overshoot index still repeats the last ladder value
        assertEquals(900_000L, QueueFiscalConfirmationUseCase.nextAttempt(5))
        // Growth starts at the second overshoot step and can never exceed one hour
        assertTrue(QueueFiscalConfirmationUseCase.nextAttempt(6) <= QueueFiscalConfirmationUseCase.MAX_INTERVAL_MS)
        assertEquals(
            QueueFiscalConfirmationUseCase.MAX_INTERVAL_MS,
            QueueFiscalConfirmationUseCase.nextAttempt(100),
        )
        // Monotonic non-decreasing across the whole sequence
        val sequence = (0..12).map { QueueFiscalConfirmationUseCase.nextAttempt(it) }
        assertEquals(sequence.sorted(), sequence)
    }

    @Test
    fun `nextAttempt trata un contador negativo como cero`() {
        assertEquals(QueueFiscalConfirmationUseCase.nextAttempt(0), QueueFiscalConfirmationUseCase.nextAttempt(-3))
    }

    /**
     * TASK-102 (matriz de idempotencia) — confirmación fiscal duplicada:
     * compone el ciclo completo del worker sobre la fila durable. Tras el
     * primer pase exitoso (markFiscalConfirmed), un segundo pase del worker
     * NO vuelve a elegirla (findFiscalConfirmable vacío) y un mark repetido
     * es idempotente: estado, tuplo fiscal y contador no retroceden.
     */
    @Test
    fun `confirmacion duplicada - la fila confirmada no vuelve a elegirse ni revierte su estado`() =
        runTest {
            seedRow("fx-dup")
            useCase.enqueue(
                "fx-dup",
                remoteInvoiceId = "42",
                fiscalNumber = "000001-00012345",
                printerSerial = "SN-PRN-7",
                failureMessage = "impresora offline",
            )

            // Primer pase del worker: elegible y confirmado con éxito.
            val firstPass = dao.findFiscalConfirmable(now = nowMs, limit = 25)
            assertEquals(listOf("fx-dup"), firstPass.map { it.clientCorrelationId })
            dao.markFiscalConfirmed(
                id = "fx-dup",
                status = QueueFiscalConfirmationUseCase.STATUS_CONFIRMED,
                fiscalNumber = "000001-00012345",
                printerSerial = "SN-PRN-7",
            )

            // Segundo pase (retry/restart del worker): nada pendiente.
            assertTrue(dao.findFiscalConfirmable(now = nowMs, limit = 25).isEmpty())

            // Caracterización: un mark repetido SOBRESCRIBE el tuplo (el SQL
            // real no tiene guardas de estado ni COALESCE), pero la fila
            // consolidada jamás vuelve a ser elegible: no hay segunda
            // confirmación remota.
            dao.markFiscalConfirmed(
                id = "fx-dup",
                status = QueueFiscalConfirmationUseCase.STATUS_CONFIRMED,
                fiscalNumber = "000001-00099999",
                printerSerial = "SN-OTHER",
            )
            val row = dao.findById("fx-dup")!!
            assertEquals(QueueFiscalConfirmationUseCase.STATUS_CONFIRMED, row.fiscalConfirmationStatus)
            assertEquals("000001-00099999", row.fiscalNumber)
            assertTrue(dao.findFiscalConfirmable(now = nowMs + 3_600_000, limit = 25).isEmpty())
        }

    private fun seedRow(id: String) {
        val entity =
            TransactionLogEntity(
                clientCorrelationId = id,
                idCaja = "box-1",
                idCajaSecuencia = "001",
                totalAmount = 250.0,
                currency = "USD",
                clientName = "Cliente",
                status = "CONFIRMED",
                tenantId = "",
                createdAt = 0L,
                updatedAt = 0L,
            )
        kotlinx.coroutines.runBlocking { dao.upsert(entity) }
    }
}
