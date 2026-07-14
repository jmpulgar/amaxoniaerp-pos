package com.amaxonia.pos.data.printer.sunmi

import android.content.Context
import com.sunmi.peripheral.printer.InnerPrinterCallback
import com.sunmi.peripheral.printer.InnerPrinterManager
import com.sunmi.peripheral.printer.SunmiPrinterService
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

class SunmiPrinterManager(
    context: Context,
) {
    private val appContext = context.applicationContext

    @Volatile
    private var printerService: SunmiPrinterService? = null
    private var callback: InnerPrinterCallback? = null

    suspend fun bind(): Boolean {
        printerService?.let { return true }

        return withTimeoutOrNull(3_000) {
            suspendCancellableCoroutine { continuation ->
                val bindCallback =
                    object : InnerPrinterCallback() {
                        override fun onConnected(service: SunmiPrinterService) {
                            printerService = service
                            if (continuation.isActive) continuation.resume(true)
                        }

                        override fun onDisconnected() {
                            printerService = null
                        }
                    }
                callback = bindCallback

                val bound =
                    runCatching {
                        InnerPrinterManager.getInstance().bindService(appContext, bindCallback)
                    }.getOrDefault(false)

                if (!bound && continuation.isActive) continuation.resume(false)
                continuation.invokeOnCancellation { unbind() }
            }
        } ?: false
    }

    fun getService(): SunmiPrinterService? = printerService

    fun unbind() {
        val currentCallback = callback ?: return
        runCatching {
            InnerPrinterManager.getInstance().unBindService(appContext, currentCallback)
        }
        callback = null
        printerService = null
    }
}
