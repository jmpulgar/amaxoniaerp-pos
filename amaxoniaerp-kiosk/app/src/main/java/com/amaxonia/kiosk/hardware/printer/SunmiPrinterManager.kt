package com.amaxonia.kiosk.hardware.printer

import android.content.Context
import android.os.IBinder
import android.util.Log
import com.amaxonia.kiosk.core.network.KioskPaymentResponse
import com.sunmi.peripheral.printer.InnerPrinterCallback
import com.sunmi.peripheral.printer.InnerPrinterManager
import com.sunmi.peripheral.printer.InnerResultCallback
import com.sunmi.peripheral.printer.SunmiPrinterService
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

private const val TAG = "SunmiPrinterManager"
private const val QR_MODULE_SIZE = 6
private const val QR_ERROR_LEVEL = 3
private const val LINES_BEFORE_CUT = 4
private const val ALIGN_CENTER = 1
private const val PRINT_RESULT_OK = 0
private const val PRINTER_WARMUP_POLL_MS = 500L
const val PRINT_CONNECT_TIMEOUT_MS = 15_000L
const val PRINT_JOB_TIMEOUT_MS = 20_000L
const val PRINTER_WARMUP_TIMEOUT_MS = 8_000L

class PrinterUnavailableException(
    message: String,
) : Exception(message)

/**
 * Printer states reported by `SunmiPrinterService.updatePrinterState()` on SUNMI devices with a
 * built-in printer (the K2 Seiko 80 mm included). Only the codes the kiosk reacts to are mapped.
 */
enum class SunmiPrinterState(
    val code: Int,
    val reason: String?,
) {
    READY(1, null),
    WARMING_UP(2, null),
    COMMUNICATION_ERROR(3, "Error de comunicación con la impresora"),
    OUT_OF_PAPER(4, "Impresora sin papel"),
    OVERHEATED(5, "Impresora sobrecalentada"),
    COVER_OPEN(6, "Tapa de la impresora abierta"),
    CUTTER_ERROR(7, "Error en el cortador de papel"),
    CUTTER_RECOVERED(8, null),
    NO_PRINTER(505, "Impresora no detectada"),
    UNKNOWN(-1, null),
    ;

    val isReady: Boolean
        get() = this == READY || this == CUTTER_RECOVERED

    /** Unknown codes are optimistic: the transaction result decides. */
    val canPrint: Boolean
        get() = isReady || this == UNKNOWN

    companion object {
        fun fromCode(code: Int): SunmiPrinterState = entries.firstOrNull { it.code == code } ?: UNKNOWN
    }
}

interface KioskPrinter {
    val isConnected: Boolean

    /** Prints the customer receipt; fails if the printer is not reachable within the connect timeout. */
    suspend fun printReceipt(paymentResponse: KioskPaymentResponse): Result<Unit>

    suspend fun printKitchenTicket(paymentResponse: KioskPaymentResponse): Result<Unit>

    suspend fun cutPaper(): Result<Unit>
}

/** Binds the Sunmi inner printer AIDL service and reports binder death as a disconnect. */
class SunmiPrinterBinder(
    private val context: Context,
) : PrinterServiceBinder<SunmiPrinterService> {
    private var callback: InnerPrinterCallback? = null

    override fun bind(listener: PrinterServiceBinder.Listener<SunmiPrinterService>): Boolean {
        val newCallback =
            object : InnerPrinterCallback() {
                override fun onConnected(service: SunmiPrinterService) {
                    val deathRecipient = IBinder.DeathRecipient { listener.onDisconnected() }
                    runCatching { service.asBinder().linkToDeath(deathRecipient, 0) }
                    listener.onConnected(service)
                }

                override fun onDisconnected() {
                    listener.onDisconnected()
                }
            }
        val bound = InnerPrinterManager.getInstance().bindService(context.applicationContext, newCallback)
        if (bound) callback = newCallback
        return bound
    }

    override fun unbind() {
        val current = callback ?: return
        callback = null
        InnerPrinterManager.getInstance().unBindService(context.applicationContext, current)
    }
}

@Suppress("TooGenericExceptionCaught")
class SunmiPrinterManager(
    private val connection: PrinterConnection<SunmiPrinterService>,
    private val connectTimeoutMs: Long = PRINT_CONNECT_TIMEOUT_MS,
    private val jobTimeoutMs: Long = PRINT_JOB_TIMEOUT_MS,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : KioskPrinter {
    constructor(context: Context, scope: CoroutineScope) : this(PrinterConnection(SunmiPrinterBinder(context), scope))

    init {
        connection.start()
    }

    override val isConnected: Boolean
        get() = connection.isConnected

    override suspend fun printReceipt(paymentResponse: KioskPaymentResponse): Result<Unit> =
        withPrintJob("receipt") { service ->
            service.printerInit(null)
            service.printText(ReceiptFormatter.formatCustomerReceipt(paymentResponse), null)

            val qrData = paymentResponse.invoice?.qr ?: paymentResponse.receipt.qr
            if (!qrData.isNullOrBlank()) {
                service.setAlignment(ALIGN_CENTER, null)
                service.printQRCode(qrData, QR_MODULE_SIZE, QR_ERROR_LEVEL, null)
                service.lineWrap(1, null)
            }

            service.lineWrap(LINES_BEFORE_CUT, null)
            service.cutPaper(null)
        }

    override suspend fun printKitchenTicket(paymentResponse: KioskPaymentResponse): Result<Unit> =
        withPrintJob("kitchen ticket") { service ->
            service.printerInit(null)
            service.printText(ReceiptFormatter.formatKitchenTicket(paymentResponse), null)
            service.lineWrap(LINES_BEFORE_CUT, null)
            service.cutPaper(null)
        }

    override suspend fun cutPaper(): Result<Unit> = withPrintJob("cut") { service -> service.cutPaper(null) }

    /**
     * Runs [block] as a Sunmi transaction (enterPrinterBuffer → commands → exitPrinterBufferWithCallback)
     * so the real outcome is known: paper out, cover open or a cutter jam on the K2 surface as a failure
     * instead of a silently lost receipt. After a cold boot the printer is given time to warm up.
     */
    private suspend fun withPrintJob(
        label: String,
        block: (SunmiPrinterService) -> Unit,
    ): Result<Unit> {
        val service =
            connection.awaitService(connectTimeoutMs)
                ?: return Result.failure(PrinterUnavailableException("Impresora no disponible ($label)"))
        return withContext(ioDispatcher) {
            try {
                val state = awaitWarmUp(service)
                if (!state.canPrint) {
                    val reason = state.reason ?: "Impresora no lista (${state.name})"
                    Log.w(TAG, "Skipping $label: $reason")
                    Result.failure(PrinterUnavailableException(reason))
                } else {
                    service.enterPrinterBuffer(true)
                    block(service)
                    awaitJobResult(service, label)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to print $label via Sunmi service: ${e.message}", e)
                Result.failure(e)
            }
        }
    }

    private suspend fun awaitWarmUp(service: SunmiPrinterService): SunmiPrinterState {
        var state = SunmiPrinterState.fromCode(service.updatePrinterState())
        withTimeoutOrNull(PRINTER_WARMUP_TIMEOUT_MS) {
            while (state == SunmiPrinterState.WARMING_UP) {
                delay(PRINTER_WARMUP_POLL_MS)
                state = SunmiPrinterState.fromCode(service.updatePrinterState())
            }
        }
        return state
    }

    private suspend fun awaitJobResult(
        service: SunmiPrinterService,
        label: String,
    ): Result<Unit> {
        val outcome = CompletableDeferred<Result<Unit>>()
        val callback =
            object : InnerResultCallback() {
                override fun onRunResult(isSuccess: Boolean) = Unit

                override fun onReturnString(result: String?) = Unit

                override fun onRaiseException(
                    code: Int,
                    msg: String?,
                ) {
                    outcome.complete(Result.failure(PrinterUnavailableException("Error de impresora ($code): ${msg.orEmpty()}")))
                }

                override fun onPrintResult(
                    code: Int,
                    msg: String?,
                ) {
                    val result =
                        if (code == PRINT_RESULT_OK) {
                            Result.success(Unit)
                        } else {
                            Result.failure(PrinterUnavailableException("La impresión falló ($code): ${msg.orEmpty()}"))
                        }
                    outcome.complete(result)
                }
            }
        service.exitPrinterBufferWithCallback(true, callback)
        // Some firmware builds never call onPrintResult; then the printer state after the job decides.
        return withTimeoutOrNull(jobTimeoutMs) { outcome.await() } ?: resultFromState(service, label)
    }

    private fun resultFromState(
        service: SunmiPrinterService,
        label: String,
    ): Result<Unit> {
        val state = SunmiPrinterState.fromCode(service.updatePrinterState())
        Log.w(TAG, "No print result for $label within ${jobTimeoutMs}ms, printer state $state")
        return if (state.isReady) {
            Result.success(Unit)
        } else {
            Result.failure(PrinterUnavailableException(state.reason ?: "Impresora sin respuesta"))
        }
    }
}
