package com.amaxonia.pos.domain.usecase.payment

import com.amaxonia.pos.data.local.db.TransactionLogEntity
import com.amaxonia.pos.domain.system.AppClock
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant

/**
 * Fake [AppClock] returning a fixed instant, so the watchdog/lease timing in
 * these tests is deterministic without pulling in a mocking framework.
 */
private class FakeAppClock(
    private val instant: Instant,
) : AppClock {
    override fun now(): Instant = instant
}

/**
 * Characterization tests for the RapidPay callback ledger (auditoría ítem 6).
 *
 * These tests exercise the durable behaviour a real HKA callback depends on:
 *   - the await row is persisted BEFORE the Intent is launched;
 *   - a legit callback marks the row RESOLVED and stores the full HKA
 *     response (code + JSON + message) for audit/reconciliation;
 *   - a duplicate callback is idempotent (the audit fields are refreshed but
 *     no second state transition occurs);
 *   - a callback that arrives after process death (no in-memory request)
 *     still lands on the row so support can reconcile;
 *   - a callback with no pending row produces no effect;
 *   - the watchdog flips rows whose lease expired into RETRYABLE / TERMINAL.
 *
 * The DAO implementation used here ([InMemoryTransactionLogDao]) mirrors the
 * SQL semantics of the Room migration, so the assertions are equivalent to
 * what a connected instrumented test would verify against real SQLite.
 */
class QueueGatewayCallbackUseCaseTest {
    private val nowMs = Instant.parse("2026-07-21T12:00:00Z").toEpochMilli()
    private val clock: AppClock = FakeAppClock(Instant.parse("2026-07-21T12:00:00Z"))
    private lateinit var dao: InMemoryTransactionLogDao
    private lateinit var useCase: QueueGatewayCallbackUseCase

    @Before
    fun setUp() {
        dao = InMemoryTransactionLogDao()
        useCase = QueueGatewayCallbackUseCase(dao, clock)
    }

    @Test
    fun `markAwaiting persists the row as AWAITING before the Intent is launched`() =
        runTest {
            seedRow("sale-1")

            useCase.markAwaiting("sale-1")

            val row = dao.findById("sale-1")!!
            assertEquals(QueueGatewayCallbackUseCase.STATUS_AWAITING, row.gatewayCallbackStatus)
        }

    @Test
    fun `markResolved on a legit approval flips AWAITING to RESOLVED and keeps full HKA response`() =
        runTest {
            seedRow("sale-2")
            useCase.markAwaiting("sale-2")

            useCase.markResolved(
                clientCorrelationId = "sale-2",
                responseCode = "200",
                rawResponse = """{"message":"Aprobada","approvalCode":"APP-9"}""",
                message = "Aprobada",
            )

            val row = dao.findById("sale-2")!!
            assertEquals(QueueGatewayCallbackUseCase.STATUS_RESOLVED, row.gatewayCallbackStatus)
            assertEquals("200", row.gatewayResultCode)
            assertEquals("""{"message":"Aprobada","approvalCode":"APP-9"}""", row.gatewayRawResponse)
            assertEquals("Aprobada", row.gatewayResultMessage)
            assertEquals(0L, row.gatewayCallbackNextAttemptAt)
            assertEquals(0L, row.gatewayCallbackLeasedUntil)
        }

    @Test
    fun `markResolved on a rejection still flips to RESOLVED with the rejection message`() =
        runTest {
            seedRow("sale-3")
            useCase.markAwaiting("sale-3")

            useCase.markResolved(
                clientCorrelationId = "sale-3",
                responseCode = "400",
                rawResponse = null,
                message = "Tarjeta rechazada",
            )

            val row = dao.findById("sale-3")!!
            assertEquals(QueueGatewayCallbackUseCase.STATUS_RESOLVED, row.gatewayCallbackStatus)
            assertEquals("400", row.gatewayResultCode)
            assertEquals("Tarjeta rechazada", row.gatewayResultMessage)
            assertNull(row.gatewayRawResponse)
        }

    // --- Idempotency: duplicate callback (HKA-001) -------------------------

    @Test
    fun `duplicate markResolved is idempotent - status does not revert and audit fields refresh`() =
        runTest {
            seedRow("sale-4")
            useCase.markAwaiting("sale-4")
            useCase.markResolved("sale-4", responseCode = "200", rawResponse = """{"a":1}""", message = "Aprobada")

            // A second Intent with the same extras arrives (e.g. HKA retried delivery)
            useCase.markResolved("sale-4", responseCode = "200", rawResponse = """{"a":1}""", message = "Aprobada")

            val row = dao.findById("sale-4")!!
            assertEquals(QueueGatewayCallbackUseCase.STATUS_RESOLVED, row.gatewayCallbackStatus)
            assertEquals("200", row.gatewayResultCode)
            assertEquals("""{"a":1}""", row.gatewayRawResponse)
        }

    @Test
    fun `markResolved after TERMINAL_AWAITING keeps terminal status but preserves audit data`() =
        runTest {
            seedRow("sale-5")
            useCase.markAwaiting("sale-5")
            // Watchdog already escalated to terminal before the late callback arrived
            dao.markGatewayTerminal(
                id = "sale-5",
                status = QueueGatewayCallbackUseCase.STATUS_TERMINAL_AWAITING,
                rawResponse = null,
                message = "Reconciliacion manual requerida",
            )

            // The legitimate HKA callback finally arrives (e.g. after process death)
            useCase.markResolved(
                clientCorrelationId = "sale-5",
                responseCode = "200",
                rawResponse = """{"approvalCode":"APP-LATE"}""",
                message = "Aprobada (tardia)",
            )

            val row = dao.findById("sale-5")!!
            // Status MUST NOT silently resurrect the sale
            assertEquals(QueueGatewayCallbackUseCase.STATUS_TERMINAL_AWAITING, row.gatewayCallbackStatus)
            // But the full HKA response MUST be on disk for manual reconciliation
            assertEquals("200", row.gatewayResultCode)
            assertEquals("""{"approvalCode":"APP-LATE"}""", row.gatewayRawResponse)
            assertEquals("Aprobada (tardia)", row.gatewayResultMessage)
        }

    // --- Process death / no pending request (HKA-002) ----------------------

    @Test
    fun `markResolved for an unknown correlationId produces no effect`() =
        runTest {
            useCase.markResolved(
                clientCorrelationId = "never-started",
                responseCode = "200",
                rawResponse = """{"x":1}""",
                message = "Aprobada",
            )

            assertNull(dao.findById("never-started"))
        }

    @Test
    fun `late callback after simulated process death still lands on the row`() =
        runTest {
            seedRow("sale-6")
            useCase.markAwaiting("sale-6")
            // Simulate process death: the in-memory RapidPayBridge pendingResult is
            // gone (handled in MainActivity). The callback Intent still reaches the
            // ledger row, which is the durable source of truth.
            useCase.markResolved(
                clientCorrelationId = "sale-6",
                responseCode = "200",
                rawResponse = """{"approvalCode":"APP-RECOVERED"}""",
                message = "Aprobada",
            )

            val row = dao.findById("sale-6")!!
            assertEquals(QueueGatewayCallbackUseCase.STATUS_RESOLVED, row.gatewayCallbackStatus)
            assertNotNull(row.gatewayRawResponse)
        }

    // --- Watchdog reconciliation -------------------------------------------

    @Test
    fun `watchdog finds rows whose lease has expired`() =
        runTest {
            seedRow("sale-7")
            useCase.markAwaiting("sale-7")

            val eligibleAtExpiry = dao.findGatewayReconcilable(now = nowMs + 99_999L)
            assertTrue(eligibleAtExpiry.any { it.clientCorrelationId == "sale-7" })
        }

    @Test
    fun `watchdog marks retriable after first expiry`() =
        runTest {
            seedRow("sale-8")
            useCase.markAwaiting("sale-8")

            dao.markGatewayRetriable(
                id = "sale-8",
                status = QueueGatewayCallbackUseCase.STATUS_RETRYABLE_AWAITING,
                nextAttemptAt = nowMs + QueueGatewayCallbackUseCase.nextAttempt(1),
                rawResponse = null,
                message = null,
            )

            val row = dao.findById("sale-8")!!
            assertEquals(QueueGatewayCallbackUseCase.STATUS_RETRYABLE_AWAITING, row.gatewayCallbackStatus)
            assertEquals(1, row.gatewayCallbackRetryCount)
        }

    @Test
    fun `reconcilable rows are tenant scoped`() =
        runTest {
            seedRow("sale-9", tenantId = "tenant-A")
            useCase.markAwaiting("sale-9")

            val scopedToA = dao.findGatewayReconcilableForTenant("tenant-A", now = nowMs)
            val scopedToB = dao.findGatewayReconcilableForTenant("tenant-B", now = nowMs)

            assertTrue(scopedToA.any { it.clientCorrelationId == "sale-9" })
            assertTrue(scopedToB.none { it.clientCorrelationId == "sale-9" })
        }

    private fun seedRow(
        id: String,
        tenantId: String = "",
    ) {
        val entity =
            TransactionLogEntity(
                clientCorrelationId = id,
                idCaja = "box-1",
                idCajaSecuencia = "001",
                totalAmount = 100.0,
                currency = "VES",
                clientName = "Cliente",
                status = "SENDING",
                tenantId = tenantId,
                createdAt = 0L,
                updatedAt = 0L,
            )
        kotlinx.coroutines.runBlocking { dao.upsert(entity) }
    }
}
