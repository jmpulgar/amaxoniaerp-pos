package com.amaxonia.pos.data.printer

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.amaxonia.pos.data.local.db.AppDatabase
import com.amaxonia.pos.domain.model.AppConfig
import com.amaxonia.pos.domain.model.Transaction
import com.amaxonia.pos.domain.model.TransactionDetail
import com.amaxonia.pos.domain.printer.PrintResult
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import java.util.Date
import kotlinx.coroutines.delay
import org.junit.Assert.assertTrue

class FiscalWatchdogTest {

    private lateinit var watchdog: FiscalWatchdog
    private lateinit var mockDb: AppDatabase
    private lateinit var mockPrinterService: PrinterServiceWrapper
    private lateinit var mockRapidPayBridge: RapidPayBridge

    @Before
    fun setUp() {
        mockDb = mockk(relaxed = true)
        mockPrinterService = mockk(relaxed = true)
        mockRapidPayBridge = mockk(relaxed = true)
        
        watchdog = FiscalWatchdog(mockDb, mockPrinterService, mockRapidPayBridge)
    }

    @Test
    fun `resumePendingTransactions tries to print unsynced pending transactions`() = runTest {
        val pendingTx = createTransaction("PENDING")
        coEvery { mockDb.transactionDao().getUnsyncedPendingPrinting() } returns listOf(pendingTx)
        coEvery { mockPrinterService.isEnabled() } returns true
        coEvery { mockPrinterService.isBusy() } returns false
        coEvery { mockPrinterService.print(pendingTx) } returns PrintResult.Success("Ok")

        watchdog.resumePendingTransactions()

        coVerify(exactly = 1) { mockPrinterService.print(pendingTx) }
        coVerify(exactly = 1) { mockDb.transactionDao().markAsPrinted(pendingTx.id) }
    }

    private fun createTransaction(state: String): Transaction {
        return Transaction(
            code = "T-123",
            created = Date(),
            transactionTotal = 15.0,
            transactionDiscountTotal = 0.0,
            transactionRate1VatSubtotal = 15.0,
            transactionRate1VatTotal = 0.0,
            transactionRate2VatSubtotal = 0.0,
            transactionRate2VatTotal = 0.0,
            transactionRate3VatSubtotal = 0.0,
            transactionRate3VatTotal = 0.0,
            transactionExemptTotal = 0.0,
            transactionItbms1VatSubtotal = 0.0,
            transactionItbms1VatTotal = 0.0,
            transactionItbms2VatSubtotal = 0.0,
            transactionItbms2VatTotal = 0.0,
            transactionItbms3VatSubtotal = 0.0,
            transactionItbms3VatTotal = 0.0,
            user = null,
            correlative = "001",
            printerSerial = "SN123",
            tableCode = null,
            qrCodeUrl = null,
            details = emptyList(),
            paymentMethods = emptyList(),
            printState = state
        ).apply { id = 1L }
    }
}