package com.amaxonia.pos.domain.model.printer

enum class PrinterType(
    val displayName: String,
) {
    NONE("Ninguna"),
    THE_FACTORY_HKA("The Factory HKA (Fiscal)"),
    GENERIC_BLUETOOTH("Genérica (Bluetooth)"),
    SUNMI_V2("Terminal Sunmi V2"),
}
