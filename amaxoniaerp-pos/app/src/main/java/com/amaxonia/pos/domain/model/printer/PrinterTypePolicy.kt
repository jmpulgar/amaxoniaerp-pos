package com.amaxonia.pos.domain.model.printer

import com.amaxonia.pos.domain.model.ServerCountry

object PrinterTypePolicy {
    private const val PANAMA_CODE = "PA"

    fun availablePrinterTypes(country: ServerCountry?): List<PrinterType> {
        val base = listOf(
            PrinterType.NONE,
            PrinterType.GENERIC_BLUETOOTH
        )
        val countryCode = country?.code?.uppercase()
        return when (countryCode) {
            "VE" -> base + PrinterType.THE_FACTORY_HKA
            PANAMA_CODE -> base + PrinterType.SUNMI_V2
            else -> base
        }
    }

    fun isAllowed(country: ServerCountry?, printerType: PrinterType): Boolean {
        return printerType in availablePrinterTypes(country)
    }

    fun validate(country: ServerCountry?, printerType: PrinterType) {
        if (!isAllowed(country, printerType)) {
            throw InvalidPosConfigurationException(
                when (printerType) {
                    PrinterType.SUNMI_V2 -> "La impresora SUNMI solo está disponible para Panamá."
                    PrinterType.THE_FACTORY_HKA -> "The Factory HKA solo está disponible para Venezuela."
                    else -> "La impresora seleccionada no está disponible para el país configurado."
                }
            )
        }
    }

    fun coerce(country: ServerCountry?, printerType: PrinterType): PrinterType {
        return if (isAllowed(country, printerType)) printerType else PrinterType.NONE
    }
}

class InvalidPosConfigurationException(message: String) : IllegalArgumentException(message)
