package com.amaxonia.pos.domain.model.printer

data class GatewayOption(
    val key: String,
    val label: String,
)

data class FiscalConnectionResult(
    val success: Boolean,
    val latencyMs: Long = 0,
    val errorMessage: String? = null,
)

data class FiscalStatusResult(
    val success: Boolean,
    val statusDescription: String = "",
    val errorDescription: String = "",
    val errorMessage: String? = null,
)

interface FiscalDeviceDiagnostics {
    suspend fun gateways(): Result<List<GatewayOption>>

    suspend fun testConnection(
        ip: String,
        port: Int,
    ): FiscalConnectionResult

    suspend fun printerStatus(
        ip: String,
        port: Int,
    ): FiscalStatusResult
}
