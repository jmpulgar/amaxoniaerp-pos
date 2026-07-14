package com.amaxonia.pos.domain.usecase.sync

import com.amaxonia.pos.domain.model.sales.ProcessSaleRequestDto
import com.amaxonia.pos.domain.model.sales.SaleInvoiceDto
import com.amaxonia.pos.domain.model.sales.SalePaymentSummaryDto
import com.amaxonia.pos.domain.system.AppClock
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class SynchronizePendingInvoicesUseCaseTest {
    private val clock = AppClock { Instant.parse("2026-07-12T12:00:00Z") }

    @Test
    fun successfulSubmissionUsesStableLocalIdAsIdempotencyKey() =
        runTest {
            val queue = FakeQueue(record())
            var submitted: ProcessSaleRequestDto? = null
            val useCase =
                useCase(queue) { request ->
                    submitted = request
                    Result.success(SynchronizedInvoice("remote-1", "F001"))
                }

            assertEquals(PendingInvoiceSyncResult.Success, useCase())
            assertEquals("local-1", submitted?.idFactura)
            assertEquals("OFF-001", submitted?.codFactura)
            assertEquals(listOf("recover", "sending:local-1", "sent:local-1:remote-1"), queue.events)
        }

    @Test
    fun alreadySynchronizedRecordIsNotSubmittedTwice() =
        runTest {
            val queue = FakeQueue(record())
            var submissions = 0
            val useCase =
                useCase(queue) {
                    submissions += 1
                    Result.success(SynchronizedInvoice("remote-1", "F001"))
                }

            assertEquals(PendingInvoiceSyncResult.Success, useCase())
            assertEquals(PendingInvoiceSyncResult.Success, useCase())
            assertEquals(1, submissions)
        }

    @Test
    fun networkFailureIsRecoverableAndRequestsWorkManagerRetry() =
        runTest {
            val queue = FakeQueue(record())
            val useCase = useCase(queue) { Result.failure(IllegalStateException("timeout")) }

            assertEquals(PendingInvoiceSyncResult.Retry, useCase())
            assertTrue(queue.events.any { it == "recoverable:local-1" })
        }

    @Test
    fun corruptPayloadIsPermanentAndDoesNotLoopForever() =
        runTest {
            val queue = FakeQueue(record(payloadJson = "corrupt"))
            var submissions = 0
            val useCase =
                SynchronizePendingInvoicesUseCase(
                    queue = queue,
                    decoder = PendingSaleDecoder { Result.failure(IllegalArgumentException("invalid json")) },
                    gateway =
                        object : PendingSaleGateway {
                            override suspend fun submit(request: ProcessSaleRequestDto): Result<SynchronizedInvoice> {
                                submissions += 1
                                return Result.success(SynchronizedInvoice("remote", "number"))
                            }
                        },
                    clock = clock,
                )

            assertEquals(PendingInvoiceSyncResult.Success, useCase())
            assertEquals(0, submissions)
            assertTrue(queue.events.any { it == "permanent:local-1" })
        }

    @Test
    fun partialSynchronizationKeepsOnlyRecoverableFailuresPending() =
        runTest {
            val queue = FakeQueue(record("first", "OFF-001"), record("second", "OFF-002"))
            val submissions = mutableListOf<String?>()
            val useCase =
                useCase(queue) { request ->
                    submissions += request.idFactura
                    if (request.idFactura == "second") {
                        Result.failure(IllegalStateException("timeout"))
                    } else {
                        Result.success(SynchronizedInvoice("remote-first", "F001"))
                    }
                }

            assertEquals(PendingInvoiceSyncResult.Retry, useCase())
            assertEquals(listOf("first", "second"), submissions)
            assertEquals(listOf("second"), queue.pending().map(PendingInvoiceRecord::id))
        }

    @Test
    fun backendIdentifiersAlreadyPresentAreNeverOverwritten() =
        runTest {
            val queue = FakeQueue(record())
            var submitted: ProcessSaleRequestDto? = null
            val useCase =
                SynchronizePendingInvoicesUseCase(
                    queue = queue,
                    decoder = PendingSaleDecoder { Result.success(request("existing-id", "existing-number")) },
                    gateway =
                        object : PendingSaleGateway {
                            override suspend fun submit(request: ProcessSaleRequestDto): Result<SynchronizedInvoice> {
                                submitted = request
                                return Result.success(SynchronizedInvoice("remote", "number"))
                            }
                        },
                    clock = clock,
                )

            assertEquals(PendingInvoiceSyncResult.Success, useCase())
            assertEquals("existing-id", submitted?.idFactura)
            assertEquals("existing-number", submitted?.codFactura)
        }

    @Test
    fun interruptedLeaseIsRecoveredBeforeReadingTheQueue() =
        runTest {
            val queue = FakeQueue()

            assertEquals(PendingInvoiceSyncResult.Success, useCase(queue) { error("must not submit") }())
            assertEquals(Instant.parse("2026-07-12T11:45:00Z").toEpochMilli(), queue.recoveredBefore)
            assertEquals(clock.now().toEpochMilli(), queue.recoveredAt)
        }

    private fun useCase(
        queue: FakeQueue,
        submit: suspend (ProcessSaleRequestDto) -> Result<SynchronizedInvoice>,
    ): SynchronizePendingInvoicesUseCase =
        SynchronizePendingInvoicesUseCase(
            queue = queue,
            decoder = PendingSaleDecoder { Result.success(request()) },
            gateway =
                object : PendingSaleGateway {
                    override suspend fun submit(request: ProcessSaleRequestDto): Result<SynchronizedInvoice> = submit(request)
                },
            clock = clock,
        )

    private fun record(
        id: String = "local-1",
        invoiceNumber: String = "OFF-001",
        payloadJson: String = "valid",
    ): PendingInvoiceRecord = PendingInvoiceRecord(id, invoiceNumber, payloadJson)

    private fun request(
        id: String? = null,
        invoiceNumber: String? = null,
    ): ProcessSaleRequestDto =
        ProcessSaleRequestDto(
            factura =
                SaleInvoiceDto(
                    idCliente = "client",
                    codCliente = "client",
                    codVendedor = 1,
                    idShop = 1,
                    idSucursal = 1,
                    idCaja = "caja",
                    codigoCaja = "CJ",
                    idCajaSecuencia = "seq",
                    serieSucursal = "A",
                    formaPago = "contado",
                    codEstatus = 2,
                    subtotal = 1.0,
                    ivaTotalFactura = 0.0,
                    totalTotalFactura = 1.0,
                    montoItemsFactura = 1.0,
                    totalizarBaseImponible = 1.0,
                    totalizarMontoIva = 0.0,
                    totalizarTotalGeneral = 1.0,
                    usuarioCreacion = "POS",
                    facturarA = "CLIENT",
                    facturarARuc = "CF",
                    facturarADireccion = "",
                    facturarATelefono = "",
                ),
            items = emptyList(),
            pagoResumen = SalePaymentSummaryDto(1.0, 1.0, 0.0, 0.0, emptyMap()),
            pagos = emptyList(),
            idFactura = id,
            codFactura = invoiceNumber,
        )

    private class FakeQueue(
        vararg initial: PendingInvoiceRecord,
    ) : PendingInvoiceQueue {
        private val pendingRecords = initial.toMutableList()
        val events = mutableListOf<String>()
        var recoveredBefore: Long? = null
        var recoveredAt: Long? = null

        override suspend fun recoverInterrupted(
            staleBeforeEpochMillis: Long,
            nowEpochMillis: Long,
        ) {
            recoveredBefore = staleBeforeEpochMillis
            recoveredAt = nowEpochMillis
            events += "recover"
        }

        override suspend fun pending(): List<PendingInvoiceRecord> = pendingRecords.toList()

        override suspend fun markSending(
            id: String,
            nowEpochMillis: Long,
        ) {
            events += "sending:$id"
        }

        override suspend fun markSent(
            id: String,
            result: SynchronizedInvoice,
            nowEpochMillis: Long,
        ) {
            events += "sent:$id:${result.remoteId}"
            pendingRecords.removeAll { it.id == id }
        }

        override suspend fun markRecoverableFailure(
            id: String,
            message: String,
            nowEpochMillis: Long,
        ) {
            events += "recoverable:$id"
        }

        override suspend fun markPermanentFailure(
            id: String,
            message: String,
            nowEpochMillis: Long,
        ) {
            events += "permanent:$id"
            pendingRecords.removeAll { it.id == id }
        }
    }
}
