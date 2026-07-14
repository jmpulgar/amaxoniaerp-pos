package com.amaxonia.pos.data.printer.sunmi

import android.content.Context
import android.os.RemoteException
import com.amaxonia.pos.domain.model.printer.PrintResult
import com.amaxonia.pos.domain.model.printer.TicketAlign
import com.amaxonia.pos.domain.model.printer.TicketDocument
import com.amaxonia.pos.domain.model.printer.TicketElement
import com.amaxonia.pos.domain.model.printer.TicketPrinter

class SunmiV2Printer(
    context: Context,
    private val manager: SunmiPrinterManager = SunmiPrinterManager(context),
) : TicketPrinter {
    override suspend fun connect(): PrintResult =
        if (manager.bind()) PrintResult.Success else PrintResult.Error("No se pudo conectar con la impresora SUNMI")

    override suspend fun disconnect() {
        manager.unbind()
    }

    override suspend fun isAvailable(): Boolean {
        if (manager.getService() != null) return true
        return manager.bind()
    }

    override suspend fun printText(text: String): PrintResult =
        withService { service ->
            service.printerInit(null)
            service.printText(text + "\n", null)
            service.lineWrap(2, null)
        }

    override suspend fun printTicket(ticket: TicketDocument): PrintResult =
        withService { service ->
            service.printerInit(null)
            ticket.elements.forEach { element ->
                when (element) {
                    is TicketElement.Text -> {
                        service.setAlignment(element.align.toSunmiAlign(), null)
                        if (element.bold) service.setFontSize(28f, null) else service.setFontSize(24f, null)
                        service.printText(element.value + "\n", null)
                        service.setFontSize(24f, null)
                    }

                    is TicketElement.Columns -> {
                        service.setAlignment(TicketAlign.LEFT.toSunmiAlign(), null)
                        service.printColumnsString(
                            element.values.toTypedArray(),
                            element.widths.toIntArray(),
                            element.aligns.map { it.toSunmiAlign() }.toIntArray(),
                            null,
                        )
                    }

                    is TicketElement.Qr -> {
                        service.setAlignment(TicketAlign.CENTER.toSunmiAlign(), null)
                        service.printQRCode(element.value, element.size, 2, null)
                    }

                    is TicketElement.Feed -> service.lineWrap(element.lines.coerceAtLeast(0), null)
                    TicketElement.Divider -> {
                        service.setAlignment(TicketAlign.LEFT.toSunmiAlign(), null)
                        service.printText("--------------------------------\n", null)
                    }
                }
            }
            service.lineWrap(4, null)
        }

    private suspend fun withService(block: (com.sunmi.peripheral.printer.SunmiPrinterService) -> Unit): PrintResult {
        val connected = manager.bind()
        if (!connected) return PrintResult.Error("Servicio de impresión SUNMI no disponible")
        val service = manager.getService() ?: return PrintResult.Error("Impresora SUNMI no conectada")
        return try {
            block(service)
            PrintResult.Success
        } catch (e: RemoteException) {
            PrintResult.Error("Error comunicando con la impresora SUNMI", e)
        } catch (e: Exception) {
            PrintResult.Error(e.message ?: "No se pudo imprimir en SUNMI", e)
        }
    }

    private fun TicketAlign.toSunmiAlign(): Int =
        when (this) {
            TicketAlign.LEFT -> 0
            TicketAlign.CENTER -> 1
            TicketAlign.RIGHT -> 2
        }
}
