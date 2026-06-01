package com.amaxonia.pos.data.printer

import android.content.Context
import com.amaxonia.pos.data.local.LocalStore
import com.amaxonia.pos.domain.model.printer.PrinterType
import com.amaxonia.pos.domain.repository.PrinterRepository
import com.amaxonia.pos.data.printer.sunmi.SunmiV2Printer
import com.amaxonia.pos.domain.model.printer.TicketPrinter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class PrinterFactory(
    context: Context,
    private val localStore: LocalStore
) {

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val printerTypeState = MutableStateFlow(PrinterType.NONE)

    @Volatile
    private var isHydrated = false

    /** Carga bajo demanda; si la librería fiscal falla (p. ej. en Android 10), no se cierra la app. */
    @Volatile
    private var theFactoryPrinterInstance: PrinterRepository? = null
    private var sunmiPrinterInstance: TicketPrinter? = null

    private fun getTheFactoryPrinterOrNull(): PrinterRepository? {
        if (theFactoryPrinterInstance != null) return theFactoryPrinterInstance
        synchronized(this) {
            if (theFactoryPrinterInstance != null) return theFactoryPrinterInstance
            // Atrapar Throwable para evitar crashes de bajo nivel como NoClassDefFoundError en Android 10
            theFactoryPrinterInstance = try {
                TheFactoryPrinterImpl(
                    context = appContext,
                    localStore = localStore
                )
            } catch (t: Throwable) {
                null
            }
            return theFactoryPrinterInstance
        }
    }

    private fun getSunmiPrinterOrNull(): TicketPrinter? {
        if (sunmiPrinterInstance != null) return sunmiPrinterInstance
        synchronized(this) {
            if (sunmiPrinterInstance != null) return sunmiPrinterInstance
            sunmiPrinterInstance = try {
                SunmiV2Printer(appContext)
            } catch (t: Throwable) {
                null
            }
            return sunmiPrinterInstance
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

    fun getActivePrinter(): PrinterRepository? {
        val printerType = if (isHydrated) {
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
            PrinterType.SUNMI_V2 -> null
        }
    }

    fun getActiveTicketPrinter(): TicketPrinter? {
        val printerType = if (isHydrated) {
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
            PrinterType.NONE,
            PrinterType.THE_FACTORY_HKA,
            PrinterType.GENERIC_BLUETOOTH -> null
        }
    }
}
