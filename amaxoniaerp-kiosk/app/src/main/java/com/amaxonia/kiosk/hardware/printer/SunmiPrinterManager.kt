package com.amaxonia.kiosk.hardware.printer

import android.content.Context
import android.util.Log
import com.amaxonia.kiosk.core.network.KioskPaymentResponse
import com.sunmi.peripheral.printer.InnerPrinterCallback
import com.sunmi.peripheral.printer.InnerPrinterManager
import com.sunmi.peripheral.printer.SunmiPrinterService

private const val TAG = "SunmiPrinterManager"
private const val QR_MODULE_SIZE = 6
private const val QR_ERROR_LEVEL = 3
private const val LINES_BEFORE_CUT = 4

interface KioskPrinter {
    val isConnected: Boolean

    suspend fun printReceipt(paymentResponse: KioskPaymentResponse): Boolean

    suspend fun printKitchenTicket(paymentResponse: KioskPaymentResponse): Boolean

    suspend fun cutPaper(): Boolean
}

@Suppress("TooGenericExceptionCaught")
class SunmiPrinterManager(
    private val context: Context,
) : KioskPrinter {
    private var printerService: SunmiPrinterService? = null
    override var isConnected: Boolean = false
        private set

    init {
        bindService()
    }

    private fun bindService() {
        try {
            val bound =
                InnerPrinterManager.getInstance().bindService(
                    context.applicationContext,
                    object : InnerPrinterCallback() {
                        override fun onConnected(service: SunmiPrinterService) {
                            printerService = service
                            isConnected = true
                            Log.i(TAG, "SunmiPrinterService connected successfully")
                        }

                        override fun onDisconnected() {
                            printerService = null
                            isConnected = false
                            Log.w(TAG, "SunmiPrinterService disconnected")
                        }
                    },
                )
            if (!bound) {
                Log.w(TAG, "Sunmi printer service could not be bound (device may not be Sunmi hardware)")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Exception during Sunmi printer binding: ${e.message}")
        }
    }

    override suspend fun printReceipt(paymentResponse: KioskPaymentResponse): Boolean {
        val service = printerService
        val receiptText = ReceiptFormatter.formatCustomerReceipt(paymentResponse)

        if (service == null) {
            Log.i(TAG, "[SIMULATED PRINT RECEIPT]\n$receiptText")
            return true
        }

        return try {
            service.printerInit(null)
            service.printText(receiptText, null)

            val qrData = paymentResponse.invoice?.qr ?: paymentResponse.receipt.qr
            if (!qrData.isNullOrBlank()) {
                service.setAlignment(1, null)
                service.printQRCode(qrData, QR_MODULE_SIZE, QR_ERROR_LEVEL, null)
                service.lineWrap(1, null)
            }

            service.lineWrap(LINES_BEFORE_CUT, null)
            service.cutPaper(null)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to print receipt via Sunmi service: ${e.message}", e)
            false
        }
    }

    override suspend fun printKitchenTicket(paymentResponse: KioskPaymentResponse): Boolean {
        val service = printerService
        val ticketText = ReceiptFormatter.formatKitchenTicket(paymentResponse)

        if (service == null) {
            Log.i(TAG, "[SIMULATED PRINT KITCHEN TICKET]\n$ticketText")
            return true
        }

        return try {
            service.printerInit(null)
            service.printText(ticketText, null)
            service.lineWrap(LINES_BEFORE_CUT, null)
            service.cutPaper(null)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to print kitchen ticket via Sunmi service: ${e.message}", e)
            false
        }
    }

    override suspend fun cutPaper(): Boolean {
        val service = printerService ?: return false
        return try {
            service.cutPaper(null)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to cut paper: ${e.message}", e)
            false
        }
    }
}
