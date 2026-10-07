package com.amaxonia.kiosk.hardware.printer

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

private const val TAG = "PrinterConnection"
const val PRINTER_INITIAL_BACKOFF_MS = 1_000L
const val PRINTER_MAX_BACKOFF_MS = 30_000L
const val PRINTER_CONNECT_TIMEOUT_MS = 10_000L

/** Abstraction over an Android bound service (the Sunmi AIDL printer) so the retry logic is testable. */
interface PrinterServiceBinder<S : Any> {
    interface Listener<S> {
        fun onConnected(service: S)

        fun onDisconnected()
    }

    /** Requests the bind. Returns false (or throws) when the service cannot be bound right now. */
    fun bind(listener: Listener<S>): Boolean

    fun unbind()
}

/**
 * Keeps a printer service bound. The Sunmi printer service can take a long time to start on cold
 * boot, so binding is retried with exponential backoff (1 s, 2 s, 4 s … capped at 30 s), and the
 * service is rebound whenever it disconnects or its binder dies.
 */
class PrinterConnection<S : Any>(
    private val binder: PrinterServiceBinder<S>,
    private val scope: CoroutineScope,
    private val initialBackoffMs: Long = PRINTER_INITIAL_BACKOFF_MS,
    private val maxBackoffMs: Long = PRINTER_MAX_BACKOFF_MS,
    private val connectTimeoutMs: Long = PRINTER_CONNECT_TIMEOUT_MS,
) {
    private val service = MutableStateFlow<S?>(null)
    private var job: Job? = null

    /** Number of bind attempts made so far (diagnostics/tests). */
    @Volatile
    var bindAttempts: Int = 0
        private set

    val isConnected: Boolean
        get() = service.value != null

    private val listener =
        object : PrinterServiceBinder.Listener<S> {
            override fun onConnected(service: S) {
                this@PrinterConnection.service.value = service
            }

            override fun onDisconnected() {
                Log.w(TAG, "Printer service disconnected, rebinding")
                service.value = null
            }
        }

    fun start() {
        if (job?.isActive == true) return
        job = scope.launch { connectLoop() }
    }

    fun stop() {
        job?.cancel()
        job = null
        service.value = null
        safeUnbind()
    }

    /** The connected service, waiting up to [timeoutMs] for a (re)connection; null on timeout. */
    suspend fun awaitService(timeoutMs: Long): S? = service.value ?: withTimeoutOrNull(timeoutMs) { service.filterNotNull().first() }

    private suspend fun CoroutineScope.connectLoop() {
        var backoff = initialBackoffMs
        while (isActive) {
            bindAttempts++
            if (tryBind() && withTimeoutOrNull(connectTimeoutMs) { service.filterNotNull().first() } != null) {
                Log.i(TAG, "Printer service connected after $bindAttempts attempt(s)")
                backoff = initialBackoffMs
                service.first { it == null }
                safeUnbind()
            } else {
                safeUnbind()
                Log.w(TAG, "Printer service not available, retrying in ${backoff}ms")
                delay(backoff)
                backoff = (backoff * 2).coerceAtMost(maxBackoffMs)
            }
        }
    }

    @Suppress("TooGenericExceptionCaught")
    private fun tryBind(): Boolean =
        try {
            binder.bind(listener)
        } catch (e: Exception) {
            Log.w(TAG, "Printer bind threw: ${e.message}")
            false
        }

    @Suppress("TooGenericExceptionCaught")
    private fun safeUnbind() {
        try {
            binder.unbind()
        } catch (e: Exception) {
            Log.w(TAG, "Printer unbind threw: ${e.message}")
        }
    }
}
