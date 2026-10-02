package com.amaxonia.pos.domain.model.printer

import com.amaxonia.pos.domain.model.ServerCountry

object PrinterTypePolicy {
    private const val PANAMA_CODE = "PA"

    fun availablePrinterTypes(country: ServerCountry?): List<PrinterType> {
        val base =
            listOf(
                PrinterType.NONE,
                PrinterType.GENERIC_BLUETOOTH,
            )
        val countryCode = country?.code?.uppercase()
        return when (countryCode) {
            "VE" -> base + listOf(PrinterType.THE_FACTORY_HKA, PrinterType.SUNMI_V2, PrinterType.IMIN_SWIFT)
            PANAMA_CODE -> base + listOf(PrinterType.SUNMI_V2, PrinterType.IMIN_SWIFT)
            else -> base
        }
    }

    fun isAllowed(
        country: ServerCountry?,
        printerType: PrinterType,
    ): Boolean = printerType in availablePrinterTypes(country)

    fun validate(
        country: ServerCountry?,
        printerType: PrinterType,
    ) {
        if (!isAllowed(country, printerType)) {
            throw InvalidPosConfigurationException(
                when (printerType) {
                    PrinterType.SUNMI_V2,
                    PrinterType.IMIN_SWIFT,
                    -> "Esta impresora de tickets solo está disponible para Panamá y Venezuela."
                    PrinterType.THE_FACTORY_HKA -> "The Factory HKA solo está disponible para Venezuela."
                    else -> "La impresora seleccionada no está disponible para el país configurado."
                },
            )
        }
    }

    fun coerce(
        country: ServerCountry?,
        printerType: PrinterType,
    ): PrinterType = if (isAllowed(country, printerType)) printerType else PrinterType.NONE
}

class InvalidPosConfigurationException(
    message: String,
) : IllegalArgumentException(message)
