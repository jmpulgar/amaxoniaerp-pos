package com.amaxonia.pos.data.printer.senraise

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import recieptservice.com.recieptservice.PrinterInterface
import kotlin.coroutines.resume

/** Plazo máximo para completar el bind del servicio de impresión Senraise. */
private const val BIND_TIMEOUT_MS = 3_000L

open class SenraisePrinterManager(
    context: Context,
) {
    private val appContext = context.applicationContext

    @Volatile
    private var printerInterface: PrinterInterface? = null
    private var serviceConnection: ServiceConnection? = null

    open suspend fun bind(): Boolean {
        printerInterface?.let { return true }

        return withTimeoutOrNull(BIND_TIMEOUT_MS) {
            suspendCancellableCoroutine { continuation ->
                val connection =
                    object : ServiceConnection {
                        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                            printerInterface = PrinterInterface.Stub.asInterface(service)
                            if (continuation.isActive) continuation.resume(true)
                        }

                        override fun onServiceDisconnected(name: ComponentName?) {
                            printerInterface = null
                        }
                    }
                serviceConnection = connection

                val intent =
                    Intent().apply {
                        component =
                            ComponentName(
                                "recieptservice.com.recieptservice",
                                "recieptservice.com.recieptservice.service.PrinterService",
                            )
                    }

                val bound =
                    runCatching {
                        appContext.bindService(intent, connection, Context.BIND_AUTO_CREATE)
                    }.getOrDefault(false)

                if (!bound && continuation.isActive) {
                    continuation.resume(false)
                }
                continuation.invokeOnCancellation { unbind() }
            }
        } ?: false
    }

    open fun getInterface(): PrinterInterface? = printerInterface

    open fun unbind() {
        val connection = serviceConnection ?: return
        runCatching {
            appContext.unbindService(connection)
        }
        serviceConnection = null
        printerInterface = null
    }
}
