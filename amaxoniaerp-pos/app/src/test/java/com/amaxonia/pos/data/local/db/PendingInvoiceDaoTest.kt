package com.amaxonia.pos.data.local.db

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Cola offline de ventas pendientes: aislamiento por tenant, ciclo
 * PENDING/SENDING/SENT/FAILED/INVALID, recuperación de sincronizaciones
 * interrumpidas y claim atómico anti-envío-duplicado (TASK-084).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PendingInvoiceDaoTest {
    private lateinit var db: AppDatabase
    private lateinit var dao: PendingInvoiceDao

    private val now = 1_000_000L

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db =
            Room
                .inMemoryDatabaseBuilder(context, AppDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        dao = db.pendingInvoiceDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun invoice(
        id: String,
        tenantId: String = TENANT_A,
        status: String = "PENDING",
        createdAt: Long = now,
        updatedAt: Long = now,
    ): PendingInvoiceEntity =
        PendingInvoiceEntity(
            id = id,
            countryCode = "PA",
            payloadJson = "{}",
            localInvoiceNumber = "LOC-$id",
            totalMinor = 1_000L,
            clientName = "Cliente",
            status = status,
            tenantId = tenantId,
            createdAt = createdAt,
            updatedAt = updatedAt,
        )

    @Test
    fun `getPendingForTenant solo devuelve PENDING y FAILED del tenant en orden de creacion`() =
        runTest {
            dao.insert(invoice("old", createdAt = now - 10))
            dao.insert(invoice("sent", status = "SENT"))
            dao.insert(invoice("invalid", status = "INVALID"))
            dao.insert(invoice("other", tenantId = TENANT_B))
            dao.insert(invoice("failed", status = "FAILED", createdAt = now + 10))

            val rows = dao.getPendingForTenant(TENANT_A)

            assertEquals(listOf("old", "failed"), rows.map { it.id })
        }

    @Test
    fun `markSent sella la fila con el id remoto y la saca de la cola`() =
        runTest {
            dao.insert(invoice("a"))

            dao.markSent("a", remoteInvoiceId = "r-1", remoteInvoiceNumber = "F-1", updatedAt = now)

            val row = dao.getCreatedBetween(0, now + 1).single()
            assertEquals("SENT", row.status)
            assertEquals("r-1", row.remoteInvoiceId)
            assertEquals("F-1", row.remoteInvoiceNumber)
            assertEquals(0, dao.countPending())
        }

    @Test
    fun `markFailed incrementa reintentos y mantiene la fila reintentable`() =
        runTest {
            dao.insert(invoice("a"))

            dao.markFailed("a", message = "5xx", updatedAt = now)
            dao.markFailed("a", message = "timeout", updatedAt = now + 1)

            val row = dao.getPendingForTenant(TENANT_A).single()
            assertEquals("FAILED", row.status)
            assertEquals(2, row.retryCount)
            assertEquals("timeout", row.lastError)
        }

    @Test
    fun `markInvalid retira la fila del ciclo de reintentos`() =
        runTest {
            dao.insert(invoice("a"))

            dao.markInvalid("a", message = "contrato inválido", updatedAt = now)

            assertTrue(dao.getPendingForTenant(TENANT_A).isEmpty())
            assertEquals("INVALID", dao.getCreatedBetween(0, now + 1).single().status)
        }

    @Test
    fun `recoverInterrupted devuelve a FAILED las SENDING abandonadas dentro de la ventana`() =
        runTest {
            dao.insert(invoice("stale", status = "SENDING", updatedAt = now - 60_000))
            dao.insert(invoice("fresh", status = "SENDING", updatedAt = now))
            dao.recoverInterrupted(staleBefore = now - 30_000, updatedAt = now)

            // "fresh" sigue SENDING (sigue en vuelo): no se recupera ni aparece en la cola.
            val rows = dao.getPendingForTenant(TENANT_A)
            assertEquals(listOf("stale"), rows.map { it.id })
            val staleRow = rows.single()
            assertEquals("FAILED", staleRow.status)
            assertEquals("Interrupted synchronization recovered", staleRow.lastError)
        }

    @Test
    fun `tryClaim es atomico - un solo worker claim la fila hasta que expire el lease`() =
        runTest {
            dao.insert(invoice("a"))

            assertEquals(1, dao.tryClaim("a", now, now + 60_000, updatedAt = now))
            assertEquals(0, dao.tryClaim("a", now, now + 120_000, updatedAt = now))
            assertEquals(1, dao.tryClaim("a", now + 60_000, now + 120_000, updatedAt = now))
        }

    @Test
    fun `countPending ignora filas SENT e INVALID`() =
        runTest {
            dao.insert(invoice("a"))
            dao.insert(invoice("b", status = "SENT"))
            dao.insert(invoice("c", status = "INVALID"))
            dao.insert(invoice("d", status = "FAILED"))

            assertEquals(2, dao.countPending())
        }

    private companion object {
        const val TENANT_A = "t1"
        const val TENANT_B = "t2"
    }
}
