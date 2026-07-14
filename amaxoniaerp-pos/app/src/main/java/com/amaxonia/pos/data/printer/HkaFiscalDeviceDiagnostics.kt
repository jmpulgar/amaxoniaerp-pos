package com.amaxonia.pos.data.printer

import com.amaxonia.pos.domain.model.printer.FiscalConnectionResult
import com.amaxonia.pos.domain.model.printer.FiscalDeviceDiagnostics
import com.amaxonia.pos.domain.model.printer.FiscalStatusResult
import com.amaxonia.pos.domain.model.printer.GatewayOption

class HkaFiscalDeviceDiagnostics(
    private val connectionHelper: HkaConnectionHelper,
    private val rapidPayClient: TheFactoryRapidPayClient,
) : FiscalDeviceDiagnostics {
    override suspend fun gateways(): Result<List<GatewayOption>> = rapidPayClient.listGateways()

    override suspend fun testConnection(
        ip: String,
        port: Int,
    ): FiscalConnectionResult {
        val result = connectionHelper.testConnection(ip, port)
        return FiscalConnectionResult(result.success, result.latencyMs, result.errorMessage)
    }

    override suspend fun printerStatus(
        ip: String,
        port: Int,
    ): FiscalStatusResult {
        val result = connectionHelper.checkPrinterStatus(ip, port)
        return FiscalStatusResult(result.success, result.statusDescription, result.errorDescription, result.errorMessage)
    }
}
