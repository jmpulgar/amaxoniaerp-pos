package com.amaxonia.erp.data.printer.imin

import android.content.Context
import com.imin.printer.InitPrinterCallback
import com.imin.printer.PrinterHelper
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

private const val BIND_TIMEOUT_MS = 3_000L

class IminPrinterManager(
    context: Context,
) {
    private val appContext = context.applicationContext

    @Volatile
    private var isConnected = false
    private var callback: InitPrinterCallback? = null

    suspend fun bind(): Boolean {
        if (isConnected) return true

        return withTimeoutOrNull(BIND_TIMEOUT_MS) {
            suspendCancellableCoroutine { continuation ->
                val bindCallback =
                    object : InitPrinterCallback {
                        override fun onConnected() {
                            isConnected = true
                            if (continuation.isActive) continuation.resume(true)
                        }

                        override fun onDisconnected() {
                            isConnected = false
                        }
                    }
                callback = bindCallback

                val bound =
                    runCatching {
                        PrinterHelper.getInstance().initPrinterService(appContext, bindCallback)
                    }.getOrDefault(false)

                if (!bound && continuation.isActive) {
                    continuation.resume(false)
                }
                continuation.invokeOnCancellation { unbind() }
            }
        } ?: false
    }

    fun isBound(): Boolean = isConnected

    fun unbind() {
        runCatching {
            PrinterHelper.getInstance().deInitPrinterService(appContext)
        }
        callback = null
        isConnected = false
    }
}
