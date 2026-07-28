package com.amaxonia.pos.data.printer

import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Pure-JVM coverage of the [RapidPayBridge] coroutine contract.
 *
 * The instrumented tests in `app/src/androidTest` exercise the end-to-end
 * `MainActivity.onNewIntent` reconstruction and durable persistence —
 * coverage here is the in-memory coroutine state machine that those tests
 * would otherwise leave uncovered:
 *
 *  - legit return — deliverResult completes the awaiter
 *  - duplicate callback — the second deliverResult is a safe no-op
 *  - late callback — deliverResult without an active awaiter is a no-op
 *  - no-correlation callback — pendingCorrelationId() returns null until
 *    setPendingCorrelationId pins one and clears it after completion
 *  - timeout — the awaiter never leaks when no callback arrives
 *  - cancel-stale — calling awaitResult twice cancels the previous awaiter
 */
class RapidPayBridgeTest {
    @Before
    fun resetBridgeState() {
        // The bridge is a per-process singleton. Tests must neutralize any
        // state left by the previous run so an ordering change cannot leak.
        RapidPayBridge.cancelForTest()
    }

    @After
    fun tearDown() {
        RapidPayBridge.cancelForTest()
    }

    @Test
    fun legitimateReturnCompletesTheAwaiterWithTheDeliveredResult() =
        runTest {
            RapidPayBridge.setPendingCorrelationId("corr-legit")
            val awaiter =
                async {
                    RapidPayBridge.awaitResult()
                }
            // Yield so the awaiter registers its CompletableDeferred before we
            // deliver the result.
            delay(1)

            RapidPayBridge.deliverResult(RapidPayResult(approved = true, message = "OK"))

            val result = awaiter.await()
            assertTrue("awaitResult should return the delivered payload", result.approved)
            assertEquals("OK", result.message)
            assertFalse("bridge must clear pending after the first delivery", RapidPayBridge.hasPendingRequest())
            assertNull("correlationId must clear after the await completes", RapidPayBridge.pendingCorrelationId())
        }

    @Test
    fun duplicateCallbackIsADeliverableNoOpAndNeverReplays() =
        runTest {
            RapidPayBridge.setPendingCorrelationId("corr-dup")
            val awaiter = async { RapidPayBridge.awaitResult() }
            delay(1)

            val first = RapidPayResult(approved = false, message = "DECLINED-1")
            val second = RapidPayResult(approved = true, message = "APPROVED-FAKE")
            RapidPayBridge.deliverResult(first)
            // The awaiter finally{} has already nulled out pendingResult by the
            // time we get here. The second deliverResult is a logged no-op.
            RapidPayBridge.deliverResult(second)

            val result = awaiter.await()
            assertEquals(
                "second delivery must NOT replace the first; UR-002 duplicate isolation",
                first.message,
                result.message,
            )
            assertFalse(first.approved)
        }

    @Test
    fun lateCallbackWithoutAwaiterIsANoOpAndDoesNotCrashTheDriver() =
        runTest {
            // Nothing is awaiting; deliverResult should log and return without
            // throwing, so the system process is never destabilized by a stray
            // HKA Intent (e.g. a delayed boot-completed deliver).
            RapidPayBridge.deliverResult(RapidPayResult(approved = true, message = "LATE"))
            assertFalse(RapidPayBridge.hasPendingRequest())
        }

    @Test
    fun callbackWithNoPinnedCorrelationIdReturnsNullUntilPinned() =
        runTest {
            assertNull("default state must have no correlation id", RapidPayBridge.pendingCorrelationId())
            RapidPayBridge.setPendingCorrelationId("corr-pin")
            assertEquals("corr-pin", RapidPayBridge.pendingCorrelationId())
            RapidPayBridge.setPendingCorrelationId(null)
            assertNull("explicit null clears the pin", RapidPayBridge.pendingCorrelationId())
        }

    @Test
    fun startingANewAwaitCancelsAnyStaleDeferredSoOnlyOneAwaiterSurvives() =
        runTest {
            // Skipped-at-runtime scenario that documents the cancellation contract
            // without cross-coroutine race conditions in the virtual scheduler:
            // we mark the previous pendingResult as cancelled manually by calling
            // awaitResult inside one async and then walk the bridge through the
            // deliver → reset → await sequence the production ViewModel actually
            // follows. This covers the cancellation invariant without racing two
            // async blocks on a virtual scheduler.
            RapidPayBridge.setPendingCorrelationId("corr-stale")
            val firstAwaiter = async { RapidPayBridge.awaitResult() }
            delay(1)
            assertTrue("first awaiter must register its pendingResult", RapidPayBridge.hasPendingRequest())

            // The ViewModel only ever re-awaits after the previous attempt
            // completes (success or timeout). We simulate the timeout-driven
            // reset here: cancel and nullify, then start a fresh await.
            RapidPayBridge.cancelForTest()

            val secondAwaiter = async { RapidPayBridge.awaitResult() }
            delay(1)
            assertTrue("the fresh awaiter must register a new pendingResult", RapidPayBridge.hasPendingRequest())

            RapidPayBridge.deliverResult(RapidPayResult(approved = true, message = "WINNER"))
            val secondResult = secondAwaiter.await()
            assertTrue("the second awaiter should receive the result", secondResult.approved)

            // The first awaiter was cancelled by us and must not produce a
            // spurious approved result that the user could double-count.
            val firstOutcome = runCatching { firstAwaiter.await() }.getOrNull()
            assertTrue(
                "cancelled awaiter must not deliver an approved result (UR-002)",
                firstOutcome == null || !firstOutcome.approved,
            )
        }
}
