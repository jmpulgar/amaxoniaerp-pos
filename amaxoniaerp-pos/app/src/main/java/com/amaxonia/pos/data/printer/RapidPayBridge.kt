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

    /**
     * Called by the ViewModel. Creates a new CompletableDeferred and suspends
     * until [deliverResult] is called or the timeout expires.
     */
    suspend fun awaitResult(): RapidPayResult {
        // Cancel any stale pending result
        pendingResult?.cancel()

        val deferred = CompletableDeferred<RapidPayResult>()
        pendingResult = deferred

        SafeLog.d(TAG, "Waiting for payment gateway result")

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
     * Returns true if there's a pending gateway request waiting for a result.
     */
    fun hasPendingRequest(): Boolean = pendingResult?.isActive == true
}
