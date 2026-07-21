package com.amaxonia.pos.domain.usecase.payment

import com.amaxonia.pos.data.local.db.TransactionLogEntity
import com.amaxonia.pos.domain.model.sales.FiscalState
import com.amaxonia.pos.domain.system.AppClock
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class AdvanceFiscalStateUseCaseTest {
    @Test
    fun `PENDING_PRINT advances to PRINTED_PENDING_CONFIRM after printer OK`() = runTest {
        val dao = InMemoryTransactionLogDao()
        seed(dao, "id-1", FiscalState.PENDING_PRINT)
        val useCase = AdvanceFiscalStateUseCase(dao, fixedClock())

        val affected = useCase.markPrinted("id-1")

        assertEquals(1, affected)
        assertEquals(FiscalState.PRINTED_PENDING_CONFIRM, dao.rows.row("id-1").fiscalState)
    }

    @Test
    fun `markPrinted is idempotent when already printed and stays put`() = runTest {
        val dao = InMemoryTransactionLogDao()
        seed(dao, "id-1", FiscalState.PRINTED_PENDING_CONFIRM)
        val useCase = AdvanceFiscalStateUseCase(dao, fixedClock())

        val affected = useCase.markPrinted("id-1")

        // 0 affected: the row was already past PENDING_PRINT.
        assertEquals(0, affected)
        assertEquals(FiscalState.PRINTED_PENDING_CONFIRM, dao.rows.row("id-1").fiscalState)
    }

    @Test
    fun `markConfirmed from PRINTED_PENDING_CONFIRM is the happy path`() = runTest {
        val dao = InMemoryTransactionLogDao()
        seed(dao, "id-1", FiscalState.PRINTED_PENDING_CONFIRM)
        val useCase = AdvanceFiscalStateUseCase(dao, fixedClock())

        val affected = useCase.markConfirmed("id-1")

        assertEquals(1, affected)
        assertEquals(FiscalState.CONFIRMED, dao.rows.row("id-1").fiscalState)
    }

    @Test
    fun `markConfirmed skips the printer step when backend confirms without printer feedback`() = runTest {
        val dao = InMemoryTransactionLogDao()
        seed(dao, "id-1", FiscalState.PENDING_PRINT)
        val useCase = AdvanceFiscalStateUseCase(dao, fixedClock())

        val affected = useCase.markConfirmed("id-1")

        assertEquals(1, affected)
        assertEquals(FiscalState.CONFIRMED, dao.rows.row("id-1").fiscalState)
    }

    @Test
    fun `markConfirmed on an already CONFIRMED row is a no-op`() = runTest {
        val dao = InMemoryTransactionLogDao()
        seed(dao, "id-1", FiscalState.CONFIRMED)
        val useCase = AdvanceFiscalStateUseCase(dao, fixedClock())

        val affected = useCase.markConfirmed("id-1")

        assertEquals(0, affected)
        assertEquals(FiscalState.CONFIRMED, dao.rows.row("id-1").fiscalState)
    }

    @Test
    fun `markFailed cannot overwrite CONFIRMED`() = runTest {
        val dao = InMemoryTransactionLogDao()
        seed(dao, "id-1", FiscalState.CONFIRMED)
        val useCase = AdvanceFiscalStateUseCase(dao, fixedClock())

        val affected = useCase.markFailed("id-1")

        assertEquals(0, affected)
        assertEquals(FiscalState.CONFIRMED, dao.rows.row("id-1").fiscalState)
    }

    @Test
    fun `markNotApplicable only works from PENDING_PRINT`() = runTest {
        val dao = InMemoryTransactionLogDao()
        seed(dao, "id-1", FiscalState.PENDING_PRINT)
        seed(dao, "id-2", FiscalState.PRINTED_PENDING_CONFIRM)
        val useCase = AdvanceFiscalStateUseCase(dao, fixedClock())

        assertEquals(1, useCase.markNotApplicable("id-1"))
        assertEquals(0, useCase.markNotApplicable("id-2"))

        assertEquals(FiscalState.NOT_APPLICABLE, dao.rows.row("id-1").fiscalState)
        assertEquals(FiscalState.PRINTED_PENDING_CONFIRM, dao.rows.row("id-2").fiscalState)
    }

    private fun fixedClock(): AppClock = AppClock { Instant.ofEpochMilli(1_000L) }

    private suspend fun seed(
        dao: InMemoryTransactionLogDao,
        id: String,
        fiscalState: FiscalState,
    ) {
        dao.upsert(
            TransactionLogEntity(
                clientCorrelationId = id,
                idCaja = "caja",
                idCajaSecuencia = "seq",
                totalAmount = 1.0,
                currency = "USD",
                clientName = "Cliente",
                status = StartTransactionUseCase.STATUS_CONFIRMED,
                tenantId = "t$1",
                tenantCompanyId = 1,
                fiscalState = fiscalState,
                createdAt = 1L,
                updatedAt = 1L,
            ),
        )
    }
}
