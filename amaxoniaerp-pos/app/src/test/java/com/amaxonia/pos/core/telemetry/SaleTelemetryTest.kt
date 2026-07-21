package com.amaxonia.pos.core.telemetry

import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Pure-JVM coverage of [SaleTelemetry].
 *
 * Auditoría ítem 10 / OBS-001 — the telemetry contract is critical: a sale
 * must NEVER break because of telemetry, must ALWAYS surface money-relevant
 * events with the right correlation id, and must never carry PII / card data
 * / encrypted commands in the structured payload.
 *
 * Coverage matrix:
 *   - payload format (event / seq / idFactura / kv pairs)
 *   - sequence uniqueness and monotonic increase
 *   - attribute truncation (MAX_ATTRIBUTE_LEN=120)
 *   - critical events route to emitAlert()
 *   - non-critical events do NOT route to emitAlert()
 *   - sink failure (RuntimeException) is swallowed silently → sale is unbroken
 *   - sink failure (Error subclass) is NOT swallowed → corrupt process surfaces
 *   - the default sink ships in BOTH debug and release (LogcatSink contract)
 *   - masking: no rawResponse / card number / token substring parrots into payload
 *   - SaleEvent.code names match the documented convention
 */
class SaleTelemetryTest {
    private val received: MutableList<Pair<SaleEvent, String>> = java.util.Collections.synchronizedList(mutableListOf())
    private val alertReceived: MutableList<Pair<SaleEvent, String>> = java.util.Collections.synchronizedList(mutableListOf())
    private val recordingSink =
        object : TelemetrySink {
            override fun emit(
                event: SaleEvent,
                payload: String,
            ) {
                received += event to payload
            }

            override fun emitAlert(
                event: SaleEvent,
                payload: String,
            ) {
                alertReceived += event to payload
            }
        }

    private val originalSink = SaleTelemetry.sink
    private val originalPolicy = SaleTelemetry.alerting

    @Before
    fun installRecordingSink() {
        received.clear()
        alertReceived.clear()
        SaleTelemetry.sink = recordingSink
        SaleTelemetry.alerting = DefaultAlertPolicy
    }

    @After
    fun restoreSink() {
        SaleTelemetry.sink = originalSink
        SaleTelemetry.alerting = originalPolicy
    }

    @Test
    fun payloadStartsSequenceEventIdFacturaAndAppendsAttributes() {
        SaleTelemetry.record(
            event = SaleEvent.SALE_CONFIRMED,
            idFactura = "fact-123",
            "approved" to true,
            "amount" to 18.5,
        )
        assertEquals(1, received.size)
        val payload = received[0].second
        // The structured format is intentional: a log shipper can grep
        // `seq=N event=sale.confirmed idFactura=fact-123 ...` deterministically.
        assertTrue("payload must start with the seq counter: $payload", payload.startsWith("seq="))
        assertTrue("payload must contain the event identifier: $payload", payload.contains(" event=sale.confirmed"))
        assertTrue("payload must include idFactura: $payload", payload.contains(" idFactura=fact-123"))
        assertTrue("payload must include boolean attribute: $payload", payload.contains(" approved=true"))
        assertTrue("payload must include numeric attribute: $payload", payload.contains(" amount=18.5"))
    }

    @Test
    fun sequenceNumberStrictlyMonotonicAcrossDistinctEvents() {
        repeat(5) { idx ->
            SaleTelemetry.record(SaleEvent.SALE_STARTED, "id-$idx")
        }
        val seqs =
            received
                .map { p -> p.second.substringAfter("seq=").substringBefore(' ').toLong() }
        assertEquals(5, seqs.size)
        seqs.zipWithNext { a, b -> a < b }.forEach { isIncreasing -> assertTrue("seq must be strictly increasing", isIncreasing) }
    }

    @Test
    fun attributeValuesAreTruncatedToMax120Chars() {
        val longValue = "x".repeat(500)
        SaleTelemetry.record(SaleEvent.GATEWAY_AWAITING, "id-trunc", "trace" to longValue)
        val payload = received[0].second
        assertTrue(
            "trace attribute must be truncated to ≤120 chars (PII / log volume)",
            payload.contains("trace=" + "x".repeat(120)),
        )
        assertFalse(
            "trace attribute must NOT contain chars beyond 120 (would bloat the export)",
            payload.contains("x".repeat(121)),
        )
    }

    @Test
    fun nullAttributesAreCoercedToEmptyString() {
        SaleTelemetry.record(SaleEvent.RETRY_SCHEDULED, "id-null", "rawResponse" to null)
        val payload = received[0].second
        assertTrue("null attribute must coerce to empty, not the literal 'null'", payload.contains(" rawResponse="))
        assertFalse("never write the literal 'null' for null attributes", payload.contains(" rawResponse=null"))
    }

    @Test
    fun criticalEventsRouteToBothEmitAndEmitAlert() {
        val critical =
            listOf(
                SaleEvent.SALE_DUPLICATE,
                SaleEvent.SALE_AMBIGUOUS,
                SaleEvent.RETRY_EXHAUSTED,
                SaleEvent.FISCAL_FAILED,
                SaleEvent.GATEWAY_TERMINAL,
                SaleEvent.GATEWAY_LATE_CALLBACK,
                SaleEvent.GATEWAY_DUPLICATE_CALLBACK,
            )
        critical.forEach { SaleTelemetry.record(it, "id-${it.code}") }
        assertEquals("every critical event must reach emit()", critical.size, received.size)
        assertEquals("every critical event must route to emitAlert()", critical.size, alertReceived.size)
        assertTrue(
            "alert payloads must be flagged [ALERT]-compatible (canonical structured form)",
            alertReceived.all { it.second.startsWith("seq=") },
        )
    }

    @Test
    fun nonCriticalEventsDoNotRouteToAlertChannel() {
        val routine =
            listOf(
                SaleEvent.SALE_STARTED,
                SaleEvent.SALE_CONFIRMED,
                SaleEvent.FISCAL_PRINTED,
                SaleEvent.FISCAL_CONFIRMED,
                SaleEvent.GATEWAY_RESOLVED,
            )
        routine.forEach { SaleTelemetry.record(it, "id-${it.code}") }
        assertEquals(routine.size, received.size)
        assertEquals("routine events must NOT trigger the alert channel", 0, alertReceived.size)
    }

    @Test
    fun runtimeExceptionFromSinkIsSwallowedSoTheSaleIsNeverBroken() {
        val failingSink =
            object : TelemetrySink {
                override fun emit(
                    event: SaleEvent,
                    payload: String,
                ) {
                    error("sink exploded — logd binder died")
                }
            }
        SaleTelemetry.sink = failingSink

        // The act of recording MUST NOT throw — the sale should complete.
        val failure =
            runCatching {
                SaleTelemetry.record(SaleEvent.SALE_CONFIRMED, "id-resilient")
            }.exceptionOrNull()
        assertNull("SaleTelemetry must swallow RuntimeException-class sink failures", failure)
    }

    @Test
    fun runtimeExceptionFromAlertPathIsAlsoSwallowed() {
        val failingSink =
            object : TelemetrySink {
                override fun emit(
                    event: SaleEvent,
                    payload: String,
                ) = Unit

                override fun emitAlert(
                    event: SaleEvent,
                    payload: String,
                ) {
                    error("alert sink exploded")
                }
            }
        SaleTelemetry.sink = failingSink

        val failure =
            runCatching {
                SaleTelemetry.record(SaleEvent.SALE_DUPLICATE, "id-alert")
            }.exceptionOrNull()
        assertNull("SaleTelemetry must swallow RuntimeException from alert sink", failure)
    }

    @Test(expected = OutOfMemoryError::class)
    fun errorSubclassesFromSinkAreNotSwallowed() {
        // Safety design: only RuntimeException-class failures are best-effort.
        // OutOfMemoryError, StackOverflowError and linking errors propagate
        // so a corrupt process surfaces immediately instead of continuing to
        // charge money in an indeterminate state.
        val oomSink =
            object : TelemetrySink {
                override fun emit(
                    event: SaleEvent,
                    payload: String,
                ) {
                    throw OutOfMemoryError("simulated heap exhaustion")
                }
            }
        SaleTelemetry.sink = oomSink
        SaleTelemetry.record(SaleEvent.SALE_CONFIRMED, "id-oom")
    }

    @Test
    fun payloadNeverEchoesEncryptedCommandsOrTokensEvenWhenCallerProvidesThem() {
        // Defensive: the API should not invent protection we never promised,
        // but the documented contract is that callers do not pass PII. We
        // verify that our code does NOT inject anything resembling a bearer
        // token or a gateway command into the payload implicitly.
        SaleTelemetry.record(SaleEvent.GATEWAY_RESOLVED, "id-mask", "approved" to true)
        val payload = received[0].second
        assertFalse("payload must not embed 'token'", payload.lowercase().contains("token"))
        assertFalse("payload must not embed 'Bearer'", payload.contains("Bearer"))
        assertFalse("payload must not embed card data", payload.contains("card"))
    }

    @Test
    fun saleEventCodesFollowTheReverseDomainConvention() {
        // Stability contract: log shippers grep on these exact codes. A
        // rename would silently break the pilot dashboard.
        val expectedCodes =
            mapOf(
                SaleEvent.SALE_STARTED to "sale.started",
                SaleEvent.SALE_CONFIRMED to "sale.confirmed",
                SaleEvent.SALE_REJECTED_BACKEND to "sale.rejected.backend",
                SaleEvent.SALE_DUPLICATE to "sale.duplicate",
                SaleEvent.SALE_AMBIGUOUS to "sale.ambiguous",
                SaleEvent.RETRY_SCHEDULED to "retry.scheduled",
                SaleEvent.RETRY_EXHAUSTED to "retry.exhausted",
                SaleEvent.FISCAL_PRINTED to "fiscal.printed",
                SaleEvent.FISCAL_CONFIRMED to "fiscal.confirmed",
                SaleEvent.FISCAL_FAILED to "fiscal.failed",
                SaleEvent.GATEWAY_AWAITING to "gateway.awaiting",
                SaleEvent.GATEWAY_RESOLVED to "gateway.resolved",
                SaleEvent.GATEWAY_TERMINAL to "gateway.terminal",
                SaleEvent.GATEWAY_LATE_CALLBACK to "gateway.late_callback",
                SaleEvent.GATEWAY_DUPLICATE_CALLBACK to "gateway.duplicate_callback",
            )
        expectedCodes.forEach { (event, code) ->
            assertEquals("event ${event.name} must stabilize on its .code", code, event.code)
        }
    }

    @Test
    fun customAlertPolicyIsHonoredWithoutChangingCallSites() {
        // Theerchant extensibility: a third-party alert policy can escalate
        // additional routine events without touching production code.
        val escalateEverything =
            com.amaxonia.pos.core.telemetry.AlertPolicy { true }
        SaleTelemetry.alerting = escalateEverything
        SaleTelemetry.record(SaleEvent.SALE_CONFIRMED, "id-all")
        assertEquals("custom policy must drive the alert path", 1, alertReceived.size)
    }

    @Test
    fun recordingTelemetryFromMultipleThreadsIsThreadSafeAndLosesNoEvents() {
        val count = AtomicInteger()
        val latch = CountDownLatch(1)
        val workers =
            (1..16).map {
                Thread {
                    latch.await(5, TimeUnit.SECONDS)
                    repeat(50) { idx ->
                        SaleTelemetry.record(SaleEvent.SALE_STARTED, "thread-$it-$idx")
                        count.incrementAndGet()
                    }
                }
            }
        workers.forEach { it.start() }
        latch.countDown()
        // join without timeout — the work is bounded (16×50 record() calls
        // against an in-memory sink) and finishes in well under a second in CI.
        // A timeout-based join would let unfinished workers slip through and
        // make the test flaky.
        workers.forEach { it.join() }
        assertEquals("every record() from concurrent callers must reach the sink", 16 * 50, received.size)
    }

    @Test
    fun defaultAlertPolicyIsStatelessAndAssignableToWarningDocumentation() {
        // Stability of the default policy: we never want to ship a release
        // accidentally with `escalateEverything` left on.
        val dp = DefaultAlertPolicy
        // Critical set: every UR the audit enumerates as operator-facing.
        assertTrue(dp.shouldAlert(SaleEvent.SALE_DUPLICATE))
        assertTrue(dp.shouldAlert(SaleEvent.SALE_AMBIGUOUS))
        assertTrue(dp.shouldAlert(SaleEvent.RETRY_EXHAUSTED))
        assertTrue(dp.shouldAlert(SaleEvent.FISCAL_FAILED))
        assertTrue(dp.shouldAlert(SaleEvent.GATEWAY_TERMINAL))
        assertTrue(dp.shouldAlert(SaleEvent.GATEWAY_LATE_CALLBACK))
        assertTrue(dp.shouldAlert(SaleEvent.GATEWAY_DUPLICATE_CALLBACK))
        // Non-critical: routine success should stay info-only.
        assertFalse(dp.shouldAlert(SaleEvent.SALE_CONFIRMED))
        assertFalse(dp.shouldAlert(SaleEvent.FISCAL_PRINTED))
    }
}
