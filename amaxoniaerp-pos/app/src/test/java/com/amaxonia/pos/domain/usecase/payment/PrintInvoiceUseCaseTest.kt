package com.amaxonia.pos.domain.usecase.payment

import com.amaxonia.pos.domain.model.Transaction
import com.amaxonia.pos.domain.model.TransactionStatus
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class PrintInvoiceUseCaseTest {
    @Test
    fun `forwards the exact print context and returns printer feedback`() =
        runTest {
            val transaction = transaction()
            val feedback = InvoicePrintFeedback("printed", "fiscal-1", "serial-1")
            var captured: Triple<String, Transaction, String>? = null
            val useCase =
                PrintInvoiceUseCase { country, sale, remoteId ->
                    captured = Triple(country, sale, remoteId)
                    feedback
                }

            val result = useCase("VE", transaction, "remote-1")

            assertSame(feedback, result)
            assertEquals(Triple("VE", transaction, "remote-1"), captured)
        }

    @Test
    fun `preserves no-print result for unsupported printer environments`() =
        runTest {
            val result = PrintInvoiceUseCase { _, _, _ -> null }("PA", transaction(), "remote")

            assertNull(result)
        }

    private fun transaction() =
        Transaction(
            id = "transaction",
            invoiceNumber = "INV-1",
            time = "10:00 AM",
            amount = 10.0,
            status = TransactionStatus.PAID,
            dateHeader = "Monday, 01 January 2026",
        )
}
