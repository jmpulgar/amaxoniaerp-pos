package com.amaxonia.pos.data.printer

import com.amaxonia.pos.core.logging.SafeLog
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeout

/**
 * Singleton bridge that passes Rapid Pay results from MainActivity.onNewIntent()
 * to the PaymentViewModel which is suspending while waiting for the gateway response.
 *
 * Flow:
 * 1. ViewModel calls [awaitResult] — creates a CompletableDeferred and suspends
 * 2. UI launches the HKA POS Intent via startActivity
 * 3. HKA POS processes payment and re-launches our MainActivity with result extras
 * 4. onNewIntent() calls [deliverResult] — completes the Deferred
 * 5. ViewModel resumes with the result
 */
object RapidPayBridge {
    private const val TAG = "RapidPayBridge"

    /** Timeout for waiting on HKA POS response (2 minutes — card payments can be slow). */
    private const val RESULT_TIMEOUT_MS = 120_000L

    @Volatile
    private var pendingResult: CompletableDeferred<RapidPayResult>? = null

    @Volatile
    private var pendingCorrelationId: String? = null

    /**
     * Pins the [correlationId] (UUID minted on-device, sent to the backend as
     * idFactura) so [deliverResult] / MainActivity can flip the matching
     * transaction_log row to RESOLVED when the HKA callback arrives.
     */
    fun setPendingCorrelationId(correlationId: String?) {
        pendingCorrelationId = correlationId
    }

    /**
     * Called by the ViewModel. Creates a new CompletableDeferred and suspends
     * until [deliverResult] is called or the timeout expires.
     */
    suspend fun awaitResult(): RapidPayResult {
        // Cancel any stale pending result
        pendingResult?.cancel()

        val deferred = CompletableDeferred<RapidPayResult>()
        pendingResult = deferred

        SafeLog.d(TAG, "Waiting for payment gateway result (correlationId=$pendingCorrelationId)")

        return try {
            withTimeout(RESULT_TIMEOUT_MS) {
                deferred.await()
            }
        } catch (_: kotlinx.coroutines.TimeoutCancellationException) {
            SafeLog.w(TAG, "Payment gateway result timed out")
            RapidPayResult(
                approved = false,
                message = "Tiempo de espera agotado esperando respuesta de la pasarela de pago",
            )
        } finally {
            pendingResult = null
            pendingCorrelationId = null
        }
    }

    /**
     * Called by MainActivity.onNewIntent() when the HKA POS app returns.
     */
    fun deliverResult(result: RapidPayResult) {
        SafeLog.d(TAG, "Payment gateway result delivered; approved=${result.approved}")
        val completed = pendingResult?.complete(result) ?: false
        if (!completed) {
            SafeLog.w(TAG, "Payment gateway result ignored because no request is pending")
        }
    }

    /**
     * The correlationId (UUID/idFactura) pinned via [setPendingCorrelationId].
     * MainActivity reads this on callback arrival so it can flip the
     * corresponding transaction_log row to RESOLVED via the
     * gatewayCallbackLedger. Returns null when no await is in flight or the
     * bridge was reset by process death.
     */
    fun pendingCorrelationId(): String? = pendingCorrelationId

    /**
     * Returns true if there's a pending gateway request waiting for a result.
     */
    fun hasPendingRequest(): Boolean = pendingResult?.isActive == true

    /**
     * Test-only reset that clears the in-memory singleton. We expose it as
     * `internal` so unit tests can neutralize ordering-dependent state
     * without reflecting into the singleton. Never called from production
     * code — every awaits cleans up its own state in `finally`.
     */
    @androidx.annotation.VisibleForTesting
    internal fun cancelForTest() {
        pendingResult?.cancel()
        pendingResult = null
        pendingCorrelationId = null
    }
}
