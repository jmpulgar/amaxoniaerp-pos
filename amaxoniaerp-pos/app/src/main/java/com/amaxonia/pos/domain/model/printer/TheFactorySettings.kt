package com.amaxonia.pos.domain.model.printer

data class TheFactorySettings(
    val ipAddress: String = "",
    val port: String = "",
    val openMode: String = ""
) {
    fun isConfigured(): Boolean = ipAddress.isNotBlank() && port.isNotBlank()
}
