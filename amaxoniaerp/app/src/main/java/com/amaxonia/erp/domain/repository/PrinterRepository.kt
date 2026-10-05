package com.amaxonia.erp.domain.repository

import com.amaxonia.erp.data.remote.dto.FacturaPrintPayloadDto
import com.amaxonia.erp.domain.model.printer.ReceiptPrintResult

interface PrinterRepository {
    suspend fun printReceipt(payload: FacturaPrintPayloadDto): Result<ReceiptPrintResult>

    suspend fun printReportX(): Result<Unit>

    suspend fun printReportZ(): Result<Unit>
}
