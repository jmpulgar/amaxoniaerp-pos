package com.amaxonia.kiosk.hardware.printer

import com.amaxonia.kiosk.core.network.KioskPaymentResponse
import com.amaxonia.kiosk.testutil.payResponseJson
import com.sunmi.peripheral.printer.InnerResultCallback
import com.sunmi.peripheral.printer.SunmiPrinterService
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PrinterConnectionTest {
    /** Fails [failures] binds, then connects (immediately when [connectOnBind]). */
    private class FakeBinder<S : Any>(
        private val service: S,
        private val failures: Int = 0,
        private val connectOnBind: Boolean = true,
        private val clock: () -> Long,
    ) : PrinterServiceBinder<S> {
        val bindTimes = mutableListOf<Long>()
        var unbinds = 0
        var listener: PrinterServiceBinder.Listener<S>? = null

        override fun bind(listener: PrinterServiceBinder.Listener<S>): Boolean {
            bindTimes += clock()
            this.listener = listener
            if (bindTimes.size <= failures) return false
            if (connectOnBind) listener.onConnected(service)
            return true
        }

        override fun unbind() {
            unbinds++
        }
    }

    private fun TestScope.binder(
        failures: Int = 0,
        connectOnBind: Boolean = true,
    ) = FakeBinder("printer", failures, connectOnBind) { currentTime }

    @Test
    fun `cold boot - retries with exponential backoff until the service appears`() =
        runTest {
            val binder = binder(failures = 4)
            val connection = PrinterConnection(binder, backgroundScope)

            connection.start()
            advanceTimeBy(20_000)

            assertEquals(listOf(0L, 1_000L, 3_000L, 7_000L, 15_000L), binder.bindTimes)
            assertTrue(connection.isConnected)
        }

    @Test
    fun `backoff is capped at 30 s`() =
        runTest {
            val binder = binder(failures = Int.MAX_VALUE)
            PrinterConnection(binder, backgroundScope).start()

            advanceTimeBy(200_000)

            val gaps = binder.bindTimes.zipWithNext { a, b -> b - a }
            assertEquals(listOf(1_000L, 2_000L, 4_000L, 8_000L, 16_000L, 30_000L, 30_000L), gaps.take(7))
            assertTrue(gaps.all { it <= PRINTER_MAX_BACKOFF_MS })
        }

    @Test
    fun `disconnect or binder death triggers an immediate rebind`() =
        runTest {
            val binder = binder()
            val connection = PrinterConnection(binder, backgroundScope)
            connection.start()
            runCurrent()
            assertEquals(1, binder.bindTimes.size)

            binder.listener?.onDisconnected()
            runCurrent()

            assertEquals(2, binder.bindTimes.size)
            assertEquals(1, binder.unbinds)
            assertTrue(connection.isConnected)
        }

    @Test
    fun `bind accepted but never connected is retried after the connect timeout`() =
        runTest {
            val binder = binder(connectOnBind = false)
            PrinterConnection(binder, backgroundScope).start()

            advanceTimeBy(PRINTER_CONNECT_TIMEOUT_MS + PRINTER_INITIAL_BACKOFF_MS + 1)

            assertEquals(2, binder.bindTimes.size)
        }

    @Test
    fun `awaitService waits for a late connection but gives up after the timeout`() =
        runTest {
            val late = binder(failures = 2)
            val lateConnection = PrinterConnection(late, backgroundScope).apply { start() }
            val service = async { lateConnection.awaitService(15_000) }
            advanceTimeBy(15_001)
            assertEquals("printer", service.await())

            val never = binder(failures = Int.MAX_VALUE)
            val neverConnection = PrinterConnection(never, backgroundScope).apply { start() }
            val missing = async { neverConnection.awaitService(15_000) }
            advanceTimeBy(15_001)
            assertNull(missing.await())
            assertFalse(neverConnection.isConnected)
        }

    @Test
    fun `printReceipt fails (instead of silently dropping) when the printer never connects`() =
        runTest {
            val printerService = mockk<SunmiPrinterService>(relaxed = true)
            val binder = FakeBinder(printerService, failures = Int.MAX_VALUE) { currentTime }
            val printer = SunmiPrinterManager(PrinterConnection(binder, backgroundScope))
            val response = Json { ignoreUnknownKeys = true }.decodeFromString<KioskPaymentResponse>(payResponseJson)

            val result = async { printer.printReceipt(response) }
            advanceTimeBy(PRINT_CONNECT_TIMEOUT_MS + 1)

            assertTrue(result.await().exceptionOrNull() is PrinterUnavailableException)
        }

    @Test
    fun `printReceipt prints and cuts once connected`() =
        runTest {
            val printerService = printerReporting(printResultCode = 0)
            val printer = connectedPrinter(printerService)

            val result = printer.printReceipt(response())

            assertTrue(result.isSuccess)
            verify { printerService.enterPrinterBuffer(true) }
            verify { printerService.printText(any(), null) }
            verify { printerService.cutPaper(null) }
            verify { printerService.exitPrinterBufferWithCallback(true, any()) }
        }

    @Test
    fun `out of paper fails without sending the job`() =
        runTest {
            val printerService = printerReporting(state = SunmiPrinterState.OUT_OF_PAPER.code)
            val printer = connectedPrinter(printerService)

            val result = printer.printReceipt(response())

            assertEquals("Impresora sin papel", result.exceptionOrNull()?.message)
            verify(exactly = 0) { printerService.printText(any(), any()) }
        }

    @Test
    fun `a failed transaction result is reported`() =
        runTest {
            val printerService = printerReporting(printResultCode = 1)
            val printer = connectedPrinter(printerService)

            val result = printer.printReceipt(response())

            assertTrue(result.exceptionOrNull() is PrinterUnavailableException)
        }

    @Test
    fun `waits for the printer to warm up after a cold boot`() =
        runTest {
            val printerService = printerReporting(printResultCode = 0)
            every { printerService.updatePrinterState() } returnsMany
                listOf(SunmiPrinterState.WARMING_UP.code, SunmiPrinterState.WARMING_UP.code, SunmiPrinterState.READY.code)
            val printer = connectedPrinter(printerService)

            val result = printer.printReceipt(response())

            assertTrue(result.isSuccess)
            verify(exactly = 3) { printerService.updatePrinterState() }
        }

    @Test
    fun `missing print callback falls back to the printer state`() =
        runTest {
            val printerService = printerReporting(printResultCode = null)
            val printer = connectedPrinter(printerService)

            val ok = async { printer.printReceipt(response()) }
            advanceTimeBy(PRINT_JOB_TIMEOUT_MS + 1)
            assertTrue(ok.await().isSuccess)

            every { printerService.updatePrinterState() } returnsMany
                listOf(SunmiPrinterState.READY.code, SunmiPrinterState.COVER_OPEN.code)
            val failed = async { printer.printReceipt(response()) }
            advanceTimeBy(PRINT_JOB_TIMEOUT_MS + 1)
            assertEquals("Tapa de la impresora abierta", failed.await().exceptionOrNull()?.message)
        }

    @Test
    fun `printer state codes map to known states`() {
        assertEquals(SunmiPrinterState.READY, SunmiPrinterState.fromCode(1))
        assertEquals(SunmiPrinterState.NO_PRINTER, SunmiPrinterState.fromCode(505))
        assertEquals(SunmiPrinterState.UNKNOWN, SunmiPrinterState.fromCode(42))
        assertTrue(SunmiPrinterState.UNKNOWN.canPrint)
        assertFalse(SunmiPrinterState.CUTTER_ERROR.canPrint)
    }

    /** Relaxed Sunmi service mock; [printResultCode] null means the firmware never calls onPrintResult. */
    private fun printerReporting(
        state: Int = SunmiPrinterState.READY.code,
        printResultCode: Int? = 0,
    ): SunmiPrinterService {
        val service = mockk<SunmiPrinterService>(relaxed = true)
        every { service.updatePrinterState() } returns state
        every { service.exitPrinterBufferWithCallback(true, any()) } answers {
            printResultCode?.let { secondArg<InnerResultCallback>().onPrintResult(it, "") }
        }
        return service
    }

    private fun TestScope.connectedPrinter(service: SunmiPrinterService): SunmiPrinterManager {
        val printer =
            SunmiPrinterManager(
                connection = PrinterConnection(FakeBinder(service) { currentTime }, backgroundScope),
                ioDispatcher = StandardTestDispatcher(testScheduler),
            )
        runCurrent()
        return printer
    }

    private fun response() = Json { ignoreUnknownKeys = true }.decodeFromString<KioskPaymentResponse>(payResponseJson)
}
