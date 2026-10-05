package com.amaxonia.erp.data.printer

import android.content.Context
import android.util.Log
import com.amaxonia.erp.data.local.LocalStore
import com.amaxonia.erp.data.printer.imin.IminDeviceDetector
import com.amaxonia.erp.data.printer.imin.IminSwiftPrinter
import com.amaxonia.erp.data.printer.sunmi.SunmiDeviceDetector
import com.amaxonia.erp.data.printer.sunmi.SunmiV2Printer
import com.amaxonia.erp.domain.model.printer.PrinterType
import com.amaxonia.erp.domain.model.printer.TicketPrinter
import com.amaxonia.erp.domain.repository.PrinterProvider
import com.amaxonia.erp.domain.repository.PrinterRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class PrinterFactory(
    context: Context,
    private val localStore: LocalStore,
    ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : PrinterProvider {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + ioDispatcher)
    private val printerTypeState = MutableStateFlow(PrinterType.NONE)

    @Volatile
    private var isHydrated = false

    @Volatile
    private var theFactoryPrinterInstance: PrinterRepository? = null
    private var sunmiPrinterInstance: TicketPrinter? = null
    private var iminPrinterInstance: TicketPrinter? = null

    private fun getTheFactoryPrinterOrNull(): PrinterRepository? {
        theFactoryPrinterInstance?.let { return it }
        synchronized(this) {
            return when {
                theFactoryPrinterInstance != null -> theFactoryPrinterInstance
                else -> {
                    theFactoryPrinterInstance =
                        runCatching {
                            TheFactoryPrinterImpl(
                                context = appContext,
                                localStore = localStore,
                            )
                        }.fold(
                            onSuccess = { it },
                            onFailure = { t ->
                                Log.e(TAG, "Fiscal printer implementation is unavailable: ${t.message}", t)
                                null
                            },
                        )
                    theFactoryPrinterInstance
                }
            }
        }
    }

    private fun getSunmiPrinterOrNull(): TicketPrinter? {
        sunmiPrinterInstance?.let { return it }
        synchronized(this) {
            return when {
                sunmiPrinterInstance != null -> sunmiPrinterInstance
                else -> {
                    sunmiPrinterInstance =
                        runCatching {
                            SunmiV2Printer(appContext)
                        }.fold(
                            onSuccess = { it },
                            onFailure = { t ->
                                Log.e(TAG, "SUNMI printer implementation is unavailable: ${t.message}", t)
                                null
                            },
                        )
                    sunmiPrinterInstance
                }
            }
        }
    }

    private fun getIminPrinterOrNull(): TicketPrinter? {
        iminPrinterInstance?.let { return it }
        synchronized(this) {
            return when {
                iminPrinterInstance != null -> iminPrinterInstance
                else -> {
                    iminPrinterInstance =
                        runCatching {
                            IminSwiftPrinter(appContext)
                        }.fold(
                            onSuccess = { it },
                            onFailure = { t ->
                                Log.e(TAG, "iMin printer implementation is unavailable: ${t.message}", t)
                                null
                            },
                        )
                    iminPrinterInstance
                }
            }
        }
    }

    init {
        scope.launch {
            localStore.selectedPrinterTypeFlow().collectLatest { printerType ->
                printerTypeState.value = printerType
                isHydrated = true
            }
        }
    }

    override fun getActivePrinter(): PrinterRepository? {
        val printerType =
            if (isHydrated) {
                printerTypeState.value
            } else {
                runBlocking {
                    localStore.readSelectedPrinterType().also {
                        printerTypeState.value = it
                        isHydrated = true
                    }
                }
            }

        return when (printerType) {
            PrinterType.THE_FACTORY_HKA -> getTheFactoryPrinterOrNull()
            PrinterType.NONE,
            PrinterType.GENERIC_BLUETOOTH,
            PrinterType.SUNMI_V2,
            PrinterType.IMIN_SWIFT,
            -> null
        }
    }

    override fun getActiveTicketPrinter(): TicketPrinter? {
        val printerType =
            if (isHydrated) {
                printerTypeState.value
            } else {
                runBlocking {
                    localStore.readSelectedPrinterType().also {
                        printerTypeState.value = it
                        isHydrated = true
                    }
                }
            }

        return when (printerType) {
            PrinterType.SUNMI_V2 -> getSunmiPrinterOrNull()
            PrinterType.IMIN_SWIFT -> getIminPrinterOrNull()
            PrinterType.NONE,
            PrinterType.THE_FACTORY_HKA,
            PrinterType.GENERIC_BLUETOOTH,
            ->
                when {
                    SunmiDeviceDetector.isSunmiDevice() -> getSunmiPrinterOrNull()
                    IminDeviceDetector.isIminDevice() -> getIminPrinterOrNull()
                    else -> null
                }
        }
    }

    private companion object {
        const val TAG = "PrinterFactory"
    }
}
