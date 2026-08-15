package com.amaxonia.pos.domain.usecase.payment

import com.amaxonia.pos.domain.model.printer.PrinterType
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

// Caracterización de docs/auditoria-produccion-pos-2026-07-20.md — ítems 1 y 2 —
// más comportamiento HKA-20 del flujo de pago. Fixtures compartidos en
// ExecutePaymentFlowTestFixtures.kt.

class ExecutePaymentFlowAuditTest {
    @Test
    fun `timeout then retry with carry-over id reuses the same idFactura and ledger row`() =
        runTest {
            // First attempt: backend times out (simulated as a thrown failure
            // after the row has been opened). The ViewModel would surface the
            // failure and remember the correlation id to carry into the retry.
            val firstFixture =
                fixture(
                    FixtureOptions(
                        isOnline = true,
                        withLedger = true,
                        processSaleFailure = IllegalStateException("timeout"),
                    ),
                )
            val firstResult = firstFixture.useCase(input(countryCode = "VE")) {}
            assertTrue(firstResult is PaymentFlowResult.Failure)
            assertEquals("timeout", (firstResult as PaymentFlowResult.Failure).message)
            val firstId =
                firstFixture.ledger!!
                    .rows.keys
                    .single()

            // Retry: the ViewModel re-presents the same payment details, but
            // NOW it carries the correlation id of the timed-out attempt.
            val retryFixture =
                fixture(
                    FixtureOptions(
                        isOnline = true,
                        withLedger = true,
                    ),
                )
            retryFixture.ledger!!.rows[firstId] =
                firstFixture.ledger!!.rows[firstId]!!.copy(
                    // Simulate process death: a fresh DAO instance has the
                    // same row but the use case does NOT share the IdGenerator.
                )

            val retryResult =
                retryFixture.useCase(input(countryCode = "VE", correlationCarryOver = firstId)) {}

            // KEY ASSERTION of ítem 1: the SAME idFactura reached the backend.
            assertTrue(retryResult is PaymentFlowResult.Success)
            assertEquals(firstId, retryFixture.sales.request?.idFactura)
            // And only ONE ledger row exists in the retry ledger (no second one opened).
            assertEquals(1, retryFixture.ledger!!.rows.size)
        }

    @Test
    fun `HTTP 409 with reconcilable invoice converges to a single confirmed sale`() =
        runTest {
            // The idGenerator mints idFactura = "flow-id" deterministically.
            val fixture =
                fixture(
                    FixtureOptions(
                        isOnline = true,
                        withLedger = true,
                        processSaleDuplicateOn = "flow-id",
                        reconciledCorrelationId = "flow-id",
                        reconciledTableSessionClosed = true,
                    ),
                )

            val result = fixture.useCase(input(countryCode = "VE")) {}

            // KEY ASSERTION of ítem 2: the conflict was reconciled with the
            // backend's authoritative record instead of becoming a dead-end.
            assertTrue("Expected Success, was $result", result is PaymentFlowResult.Success)
            val success = result as PaymentFlowResult.Success
            assertEquals("RECONCILED-1", success.payload.codFactura)
            assertTrue(success.payload.tableSessionClosed)
            // And processSale was called exactly once (no second submission).
            assertEquals(1, fixture.sales.processSaleCalls)
        }

    @Test
    fun `HTTP 409 without backend exposure surfaces DuplicateInvoice instead of auto-approving`() =
        runTest {
            val fixture =
                fixture(
                    FixtureOptions(
                        isOnline = true,
                        withLedger = true,
                        processSaleDuplicateOn = "flow-id",
                        // reconciledCorrelationId unset -> findByCorrelationId returns null
                    ),
                )

            val result = fixture.useCase(input(countryCode = "VE")) {}

            // KEY ASSERTION of ítem 2: ambiguous conflicts never silently
            // become approvals. The user is asked to retry or escalate.
            assertTrue(result is PaymentFlowResult.DuplicateInvoice)
            assertEquals("flow-id", (result as PaymentFlowResult.DuplicateInvoice).clientCorrelationId)
        }

    @Test
    fun `HTTP 409 with unreachable reconciliation backend stays Duplicate and never auto-approves`() =
        runTest {
            val fixture =
                fixture(
                    FixtureOptions(
                        isOnline = true,
                        withLedger = true,
                        processSaleDuplicateOn = "flow-id",
                        reconciliationFailureId = "flow-id",
                    ),
                )

            val result = fixture.useCase(input(countryCode = "VE")) {}

            assertTrue(result is PaymentFlowResult.DuplicateInvoice)
            val dup = result as PaymentFlowResult.DuplicateInvoice
            assertEquals("flow-id", dup.clientCorrelationId)
            assertEquals(1, fixture.sales.processSaleCalls)
        }

    @Test
    fun `Venezuela HKA-20 printerType attributes useHka20 to backend and uses fiscal print`() =
        runTest {
            val fixture =
                fixture(
                    FixtureOptions(
                        isOnline = true,
                        printFeedback = InvoicePrintFeedback("Impreso HKA20", "FISCAL-9", "SERIAL-2"),
                    ),
                )
            val events = mutableListOf<PaymentFlowEvent>()

            val result =
                fixture.useCase(input(countryCode = "VE", printerType = PrinterType.THE_FACTORY_HKA)) { events += it }

            // 1) El request SÍ viaja al backend (procesa la venta comercial).
            // 2) Pero el backend CUANDO recibe useHka20=true debe omitir digital:
            //    aquí sólo verificamos que el POS transportó la bandera correcta.
            val request = fixture.sales.request
            assertNotNull(request)
            assertEquals("El POS debe enviar useHka20=true cuando printerType=THE_FACTORY_HKA", true, request?.useHka20)

            // 3) El flujo continúa con impresión fiscal HKA-20 y confirmación.
            assertTrue(result is PaymentFlowResult.Success)
            assertEquals("remote-invoice", fixture.sales.confirmedInvoiceId)
            assertEquals("FISCAL-9", fixture.sales.confirmation?.numeroDocumentoFiscal)
            assertEquals("SERIAL-2", fixture.sales.confirmation?.impresoraSerial)
        }

    @Test
    fun `Venezuela HKA-20 print failure stays Success but never retries with useHka20=false`() =
        runTest {
            // Simula que la impresora fiscal HKA-20 física NO logró imprimir ni devolvió
            // número fiscal. El resultado de la venta sigue siendo SUCCESS (la venta
            // comercial ya está confirmada) pero no se reintenta procesar la venta con
            // useHka20=false.
            val fixture =
                fixture(
                    FixtureOptions(
                        isOnline = true,
                        printFeedback = null, // gateway fiscal no retorna número
                    ),
                )

            val firstResult =
                fixture.useCase(input(countryCode = "VE", printerType = PrinterType.THE_FACTORY_HKA)) {}

            assertTrue(firstResult is PaymentFlowResult.Success)
            // Sólo se invoca al backend UNA vez: el fallo fiscal NO dispara reintento digital.
            assertEquals("Reintento fiscal falla no debe disparar reintento digital", 1, fixture.sales.processSaleCalls)
            // No se invoca confirmación porque no hay número fiscal.
            assertEquals(null, fixture.sales.confirmedInvoiceId)
            assertEquals(null, fixture.sales.confirmation)
            // Y la bandera que viajó fue useHka20=true.
            assertEquals("Bandera transportada fue useHka20=true", true, fixture.sales.request?.useHka20)
        }
}
