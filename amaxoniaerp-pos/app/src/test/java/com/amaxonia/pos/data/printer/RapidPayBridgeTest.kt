package com.amaxonia.pos.data.printer

import android.content.Context
import android.content.Intent
import com.amaxonia.pos.data.AppPrefs
import com.amaxonia.pos.domain.model.Transaction
import com.amaxonia.pos.domain.model.TransactionDetail
import com.amaxonia.pos.domain.printer.PrintResult
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Date

class RapidPayBridgeTest {

    private lateinit var context: Context
    private lateinit var appPrefs: AppPrefs
    private lateinit var bridge: RapidPayBridge

    @Before
    fun setUp() {
        context = mockk(relaxed = true)
        appPrefs = mockk(relaxed = true)
        every { appPrefs.paymentGatewayType.value } returns "RAPIDPAY"
        bridge = RapidPayBridge(context, appPrefs)
    }

    @Test
    fun `isRapidPaySupported returns true when gateway is RapidPay`() {
        assertTrue(bridge.isRapidPaySupported())
    }

    @Test
    fun `sendReprintIntent sends valid broadcast and completes print`() = runTest {
        val intentSlot = slot<Intent>()
        every { context.sendBroadcast(capture(intentSlot)) } returns Unit

        val transaction = Transaction(
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
            details = listOf(
                TransactionDetail(
                    code = "P1",
                    description = "Product 1",
                    quantity = 1.0,
                    price = 15.0,
                    discount = 0.0,
                    vatPercentage = 0.0,
                    categoryCode = null,
                    itbmsPercentage = 0.0,
                    lineItbmsTotal = 0.0,
                    lineItbmsSubtotal = 0.0,
                    sellerId = null
                )
            ),
            paymentMethods = emptyList()
        )

        // Launch in background
        val resultDeferred = kotlinx.coroutines.async { bridge.sendReprintIntent(transaction) }

        // Give it time to register receiver
        kotlinx.coroutines.delay(100)

        // Simulate broadcast from RapidPay
        bridge.handleRapidPayResponse(
            status = "00",
            message = "Approved",
            responseCode = "00",
            responseMessage = "Success",
            hostDate = "260721",
            hostTime = "120000",
            approvalCode = "APP123",
            referenceNum = "REF123"
        )
        
        val result = resultDeferred.await()
        assertTrue(result is PrintResult.Success)
        
        val intent = intentSlot.captured
        assertEquals("vz.com.pos.APP_PRINT", intent.action)
    }
}
