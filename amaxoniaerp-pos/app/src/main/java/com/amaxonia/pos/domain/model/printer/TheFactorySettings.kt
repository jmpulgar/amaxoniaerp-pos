package com.amaxonia.pos.domain.model.printer

data class TheFactorySettings(
    val ipAddress: String = "",
    val port: String = "",
    val openMode: String = "",
    val gatewayKey: String = "",
    val gatewayLabel: String = "",
    val printerSerial: String = "",
) {
    fun isConfigured(): Boolean = ipAddress.isNotBlank() && port.isNotBlank()
}
