package com.amaxonia.pos.domain.usecase.payment

import com.amaxonia.pos.domain.model.tenant.SaleTenant
import com.amaxonia.pos.domain.system.AppClock
import com.amaxonia.pos.domain.system.IdGenerator
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/**
 * Auditoría docs/auditoria-produccion-pos-2026-07-20.md — ítem 1 (PAY-001, OFF-001).
 *
 * Criterio binario de aceptación: "Un timeout posterior al commit y un reinicio
 * nunca crean otra venta ni otro identificador".
 *
 * El contrato que lo garantiza: [StartTransactionUseCase.recoverOrStart] con
 * un [StartTransactionCommand.carryOverId] que ya tiene una fila SENDING en el
 * ledger reutiliza el mismo [TransactionLogEntity.clientCorrelationId]. Por
 * tanto cualquier retry que transporta ese carry-over id produce
 * [StartedTransaction.resumed] = true y envía al backend el MISMO idFactura,
 * de modo que el backend dedup (HTTP 409) lo reconoce y la venta converge.
 *
 * Este test no depende de Room: usa [InMemoryTransactionLogDao].
 */
class StartTransactionIdempotencyTest {
    @Test
    fun `fresh operation mints a new correlation id and persists a SENDING row`() =
        runTest {
            val dao = InMemoryTransactionLogDao()
            val useCase = StartTransactionUseCase(dao, ids("first-id"), fixedClock())

            val started = requireNotNull(useCase.recoverOrStart(command(carryOverId = null)))

            assertEquals("first-id", started.clientCorrelationId)
            assertFalse(started.resumed)
            assertEquals(1, dao.rows.size)
            assertEquals(StartTransactionUseCase.STATUS_SENDING, dao.rows.row("first-id").status)
        }

    @Test
    fun `retry with carry over id of a SENDING row reuses the same id`() =
        runTest {
            val dao = InMemoryTransactionLogDao()
            val first = StartTransactionUseCase(dao, ids("first-id"), fixedClock())
            val second = StartTransactionUseCase(dao, ids("SHOULD-NOT-BE-USED"), fixedClock())

            first.recoverOrStart(command(carryOverId = null))
            val resumed = requireNotNull(second.recoverOrStart(command(carryOverId = "first-id")))

            // KEY ASSERTION of ítem 1: the SAME idFactura is reused on retry.
            assertEquals("first-id", resumed.clientCorrelationId)
            assertTrue("resumed must be true so callers know no new ledger row was opened", resumed.resumed)
            // And no second row was created.
            assertEquals(1, dao.rows.size)
            assertEquals("first-id", dao.rows.keys.single())
        }

    @Test
    fun `carry over id with no ledger row mints a fresh id and never re-derives from carry-over`() =
        runTest {
            val dao = InMemoryTransactionLogDao()
            val useCase = StartTransactionUseCase(dao, ids("fresh-id"), fixedClock())

            val started = requireNotNull(useCase.recoverOrStart(command(carryOverId = "ghost-id-with-no-row")))

            assertEquals("fresh-id", started.clientCorrelationId)
            assertFalse(started.resumed)
            assertEquals(1, dao.rows.size)
        }

    @Test
    fun `carry over id with terminal status CONFIRMED does NOT resume it`() =
        runTest {
            val dao = InMemoryTransactionLogDao()
            val first = StartTransactionUseCase(dao, ids("first-id"), fixedClock())
            first.recoverOrStart(command(carryOverId = null))
            dao.rows["first-id"] = dao.rows.row("first-id").copy(status = StartTransactionUseCase.STATUS_CONFIRMED)
            val second = StartTransactionUseCase(dao, ids("fresh-id"), fixedClock())

            val started = requireNotNull(second.recoverOrStart(command(carryOverId = "first-id")))

            // KEY ASSERTION of ítem 1: an id that already completed is NOT
            // blindly reused, because that would silently concatenate two
            // distinct sales under one id. A fresh id is minted instead.
            assertEquals("fresh-id", started.clientCorrelationId)
            assertFalse(started.resumed)
            assertEquals(2, dao.rows.size)
        }

    @Test
    fun `preferred table account id already confirmed is preserved without reopening ledger row`() =
        runTest {
            val dao = InMemoryTransactionLogDao()
            val canonical = "mesa-7-cuenta-3"
            val first = StartTransactionUseCase(dao, ids("unused"), fixedClock())
            first.recoverOrStart(command(carryOverId = canonical, preferredId = canonical))
            dao.rows[canonical] =
                dao.rows.row(canonical).copy(status = StartTransactionUseCase.STATUS_CONFIRMED)
            val retry = StartTransactionUseCase(dao, ids("must-not-be-used"), fixedClock())

            val started =
                requireNotNull(
                    retry.recoverOrStart(command(carryOverId = canonical, preferredId = canonical)),
                )

            assertEquals(canonical, started.clientCorrelationId)
            assertTrue(started.resumed)
            assertEquals(StartTransactionUseCase.STATUS_CONFIRMED, dao.rows.row(canonical).status)
            assertEquals(1, dao.rows.size)
        }

    private fun ids(value: String): IdGenerator = IdGenerator { value }

    private fun fixedClock(): AppClock = AppClock { Instant.ofEpochMilli(1_000L) }

    private fun tenant(companyId: Int = 1) =
        SaleTenant(
            tenantId = SaleTenant.idFor(companyId),
            companyId = companyId,
            label = "Empresa $companyId",
            adminDb = "admin$companyId",
            contableDb = "contable$companyId",
            nominaDb = "nomina$companyId",
        )

    private fun command(
        carryOverId: String?,
        tenantValue: SaleTenant? = tenant(),
        preferredId: String? = null,
    ) = StartTransactionCommand(
        carryOverId = carryOverId,
        preferredId = preferredId,
        idCaja = "caja",
        idCajaSecuencia = "OFFLINE-caja",
        totalAmount = 10.0,
        currency = "USD",
        clientName = "Cliente",
        tenant = tenantValue,
    )
}

class StartTransactionTenantIsolationTest {
    @Test
    fun `command without tenant returns null and never opens a row`() =
        runTest {
            val dao = InMemoryTransactionLogDao()
            val useCase = StartTransactionUseCase(dao, IdGenerator { "should-not-be-used" }, AppClock { Instant.ofEpochMilli(1L) })

            val started = useCase.recoverOrStart(command(carryOverId = null, tenantValue = null))

            assertNull(started)
            assertTrue(dao.rows.isEmpty())
        }

    @Test
    fun `carry over id owned by a different tenant mints a fresh id and never resumes the borrowed row`() =
        runTest {
            val dao = InMemoryTransactionLogDao()
            val tenantA = StartTransactionUseCase(dao, IdGenerator { "tenant-a-id" }, AppClock { Instant.ofEpochMilli(1L) })
            tenantA.recoverOrStart(command(carryOverId = null, tenantValue = tenant(1)))
            // Sanity: el row quedó etiquetado con t$1.
            assertEquals(SaleTenant.idFor(1), dao.rows.row("tenant-a-id").tenantId)

            val tenantB = StartTransactionUseCase(dao, IdGenerator { "tenant-b-id" }, AppClock { Instant.ofEpochMilli(2L) })

            // Ataque simulado: someone hands the t$1 id to a session under t$2.
            val started = requireNotNull(tenantB.recoverOrStart(command(carryOverId = "tenant-a-id", tenantValue = tenant(2))))

            // No reanuda: genera un id propio y deja el original intacto.
            assertEquals("tenant-b-id", started.clientCorrelationId)
            assertFalse(started.resumed)
            assertEquals(2, dao.rows.size)
            // El row del tenant A sigue en SENDING con su id original.
            assertEquals(StartTransactionUseCase.STATUS_SENDING, dao.rows.row("tenant-a-id").status)
        }

    private fun tenant(companyId: Int) =
        SaleTenant(
            tenantId = SaleTenant.idFor(companyId),
            companyId = companyId,
            label = "Empresa $companyId",
            adminDb = "admin$companyId",
            contableDb = "contable$companyId",
            nominaDb = "nomina$companyId",
        )

    private fun command(
        carryOverId: String?,
        tenantValue: SaleTenant?,
    ) = StartTransactionCommand(
        carryOverId = carryOverId,
        idCaja = "caja",
        idCajaSecuencia = "OFFLINE-caja",
        totalAmount = 10.0,
        currency = "USD",
        clientName = "Cliente",
        tenant = tenantValue,
    )
}
