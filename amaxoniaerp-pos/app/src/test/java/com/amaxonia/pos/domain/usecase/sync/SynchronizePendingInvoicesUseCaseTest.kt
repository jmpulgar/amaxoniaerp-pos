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
            val sut =
                useCase(queue) { request ->
                    submitted = request
                    Result.success(SynchronizedInvoice("remote-1", "F001"))
                }

            assertEquals(PendingInvoiceSyncResult.Success, sut("t$1"))
            assertEquals("local-1", submitted?.idFactura)
            assertEquals("OFF-001", submitted?.codFactura)
            assertEquals(listOf("recover", "sending:local-1", "sent:local-1:remote-1"), queue.events)
        }

    @Test
    fun alreadySynchronizedRecordIsNotSubmittedTwice() =
        runTest {
            val queue = FakeQueue(record())
            var submissions = 0
            val sut =
                useCase(queue) {
                    submissions += 1
                    Result.success(SynchronizedInvoice("remote-1", "F001"))
                }

            assertEquals(PendingInvoiceSyncResult.Success, sut("t$1"))
            assertEquals(PendingInvoiceSyncResult.Success, sut("t$1"))
            assertEquals(1, submissions)
        }

    @Test
    fun networkFailureIsRecoverableAndRequestsWorkManagerRetry() =
        runTest {
            val queue = FakeQueue(record())
            val sut = useCase(queue) { Result.failure(IllegalStateException("timeout")) }

            assertEquals(PendingInvoiceSyncResult.Retry, sut("t$1"))
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

            assertEquals(PendingInvoiceSyncResult.Success, useCase("t$1"))
            assertEquals(0, submissions)
            assertTrue(queue.events.any { it == "permanent:local-1" })
        }

    @Test
    fun partialSynchronizationKeepsOnlyRecoverableFailuresPending() =
        runTest {
            val queue = FakeQueue(record("first", "OFF-001"), record("second", "OFF-002"))
            val submissions = mutableListOf<String?>()
            val sut =
                useCase(queue) { request ->
                    submissions += request.idFactura
                    if (request.idFactura == "second") {
                        Result.failure(IllegalStateException("timeout"))
                    } else {
                        Result.success(SynchronizedInvoice("remote-first", "F001"))
                    }
                }

            assertEquals(PendingInvoiceSyncResult.Retry, sut("t$1"))
            assertEquals(listOf("first", "second"), submissions)
            assertEquals(listOf("second"), queue.pending("t$1").map(PendingInvoiceRecord::id))
        }

    @Test
    fun backendIdentifiersAlreadyPresentAreNeverOverwritten() =
        runTest {
            val queue = FakeQueue(record())
            var submitted: ProcessSaleRequestDto? = null
            val useCaseObject =
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

            assertEquals(PendingInvoiceSyncResult.Success, useCaseObject("t$1"))
            assertEquals("existing-id", submitted?.idFactura)
            assertEquals("existing-number", submitted?.codFactura)
        }

    @Test
    fun nullTenantSkipsAllRowsSoNoSessionLeaksAcrossTenants() =
        runTest {
            val queue = FakeQueue(record("first", "OFF-001"), record("second", "OFF-002"))
            var submissions = 0
            val sut =
                useCase(queue) {
                    submissions += 1
                    Result.success(SynchronizedInvoice("remote", "number"))
                }

            // null tenant = no active company session = workers must NOT process rows.
            assertEquals(PendingInvoiceSyncResult.Success, sut(null))
            assertEquals(0, submissions)
            assertEquals(emptyList<String>(), queue.events.filter { it.startsWith("sending:") })
        }

    @Test
    fun rowAlreadyLeasedByAnotherWorkerIsSkippedSoOnlyOneSubmissionHappens() =
        runTest {
            val queue = FakeQueue(record("local-1", "OFF-001"))
            // Simula que otra instancia del worker ya tomó el lease.
            queue.claimExternally("local-1")

            var submissions = 0
            val sut =
                useCase(queue) {
                    submissions += 1
                    Result.success(SynchronizedInvoice("remote", "number"))
                }

            assertEquals(PendingInvoiceSyncResult.Success, sut("t$1"))
            assertEquals(0, submissions)
            assertEquals(emptyList<String>(), queue.events.filter { it.startsWith("sending:") })
        }

    @Test
    fun tenantARowStaysPendingWhenWorkerRunsUnderTenantBSession() =
        runTest {
            // SUT del Item 3 / TEN-001: dos facturas pendientes de tenants
            // distintos encoladas offline. El worker ejecuta bajo sesión t$1.
            // La factura del t$2 DEBE quedar intacta; nunca se envía con
            // credenciales ajenas.
            val firstTenant1 = record("t1-1", "OFF-A")
            val firstTenant2 = record("t2-1", "OFF-B")
            val queue =
                FakeQueue(
                    firstTenant1,
                    firstTenant2,
                    tenantOf = { record -> if (record.id.startsWith("t1")) "t$1" else "t$2" },
                )
            val submittedIds = mutableListOf<String?>()
            val sut =
                useCase(queue) { request ->
                    submittedIds += request.idFactura
                    Result.success(SynchronizedInvoice("remote", "number"))
                }

            assertEquals(PendingInvoiceSyncResult.Success, sut("t$1"))

            // Solo facturas del t$1 enviadas. La factura del t$2 sigue pendiente.
            assertEquals(listOf("t1-1"), submittedIds)
            assertEquals(listOf("t2-1"), queue.pending("t$2").map(PendingInvoiceRecord::id))
            assertEquals(emptyList<String>(), queue.events.filter { it.startsWith("sending:t2-") })
        }

    @Test
    fun interruptedLeaseIsRecoveredBeforeReadingTheQueue() =
        runTest {
            val queue = FakeQueue()

            assertEquals(PendingInvoiceSyncResult.Success, useCase(queue) { error("must not submit") }("t$1"))
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
        private val tenantOf: (PendingInvoiceRecord) -> String = { "t$1" },
    ) : PendingInvoiceQueue {
        private val pendingRecords = initial.toMutableList()
        val events = mutableListOf<String>()
        var recoveredBefore: Long? = null
        var recoveredAt: Long? = null
        private val claimedAlready = mutableSetOf<String>()

        fun claimExternally(id: String) {
            claimedAlready += id
        }

        override suspend fun recoverInterrupted(
            staleBeforeEpochMillis: Long,
            nowEpochMillis: Long,
        ) {
            recoveredBefore = staleBeforeEpochMillis
            recoveredAt = nowEpochMillis
            events += "recover"
        }

        override suspend fun pending(tenantId: String?): List<PendingInvoiceRecord> {
            if (tenantId == null) return emptyList()
            return pendingRecords.filter { tenantOf(it) == tenantId }
        }

        override suspend fun tryClaim(
            id: String,
            now: Long,
            leasedUntil: Long,
        ): Int = if (id in claimedAlready) 0 else { claimedAlready += id; 1 }

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
