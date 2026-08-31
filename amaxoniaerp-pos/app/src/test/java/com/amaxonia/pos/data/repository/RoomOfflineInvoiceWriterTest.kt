package com.amaxonia.pos.data.repository

import com.amaxonia.pos.data.local.db.PendingInvoiceDao
import com.amaxonia.pos.data.local.db.PendingInvoiceEntity
import com.amaxonia.pos.domain.model.sales.ProcessSaleRequestDto
import com.amaxonia.pos.domain.model.sales.SaleInvoiceDto
import com.amaxonia.pos.domain.model.sales.SalePaymentSummaryDto
import com.amaxonia.pos.domain.model.tenant.SaleTenant
import com.amaxonia.pos.domain.usecase.payment.OfflineInvoice
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RoomOfflineInvoiceWriterTest {

    private val insertedEntities = mutableListOf<PendingInvoiceEntity>()

    private val fakeDao = object : PendingInvoiceDao {
        override suspend fun insert(invoice: PendingInvoiceEntity) {
            insertedEntities.add(invoice)
        }

        override suspend fun getPending(limit: Int): List<PendingInvoiceEntity> = emptyList()
        override suspend fun getPendingForTenant(tenantId: String, limit: Int): List<PendingInvoiceEntity> = emptyList()
        override suspend fun getCreatedBetween(fromMillis: Long, toMillis: Long): List<PendingInvoiceEntity> = emptyList()
        override suspend fun countPending(): Int = 0
        override suspend fun markSending(id: String, updatedAt: Long) {}
        override suspend fun markSent(id: String, remoteInvoiceId: String, remoteInvoiceNumber: String, updatedAt: Long) {}
        override suspend fun markFailed(id: String, message: String, updatedAt: Long) {}
        override suspend fun markInvalid(id: String, message: String, updatedAt: Long) {}
        override suspend fun recoverInterrupted(staleBefore: Long, updatedAt: Long) {}
        override suspend fun tryClaim(id: String, now: Long, leasedUntil: Long, updatedAt: Long): Int = 1
    }

    @Test
    fun writesEntityAndTriggersOnQueuedCallback() = runTest {
        var onQueuedCalled = false
        val writer = RoomOfflineInvoiceWriter(fakeDao, onQueued = { onQueuedCalled = true })

        val invoice = OfflineInvoice(
            id = "OFF-123",
            localInvoiceNumber = "OFF-001",
            countryCode = "PA",
            request = ProcessSaleRequestDto(
                idFactura = "OFF-123",
                codFactura = "OFF-001",
                factura = SaleInvoiceDto(
                    idCliente = "1",
                    codCliente = "C1",
                    codVendedor = 1,
                    idShop = 1,
                    idSucursal = 1,
                    idCaja = "1",
                    codigoCaja = "01",
                    idCajaSecuencia = "SEC-01",
                    serieSucursal = "SUC-01",
                    formaPago = "CONTADO",
                    codEstatus = 2,
                    subtotal = 10.00,
                    descuentosItemFactura = 0.0,
                    ivaTotalFactura = 0.70,
                    totalTotalFactura = 10.70,
                    montoItemsFactura = 10.00,
                    totalizarBaseImponible = 10.00,
                    totalizarMontoIva = 0.70,
                    totalizarTotalGeneral = 10.70,
                    usuarioCreacion = "admin",
                    facturarA = "CONSUMIDOR FINAL",
                    facturarARuc = "CF",
                    facturarADireccion = "",
                    facturarATelefono = "",
                ),
                items = emptyList(),
                pagoResumen = SalePaymentSummaryDto(10.70, 10.70, 0.0, 0.0, emptyMap()),
                pagos = emptyList(),
            ),
            total = 10.70,
            clientName = "CONSUMIDOR FINAL",
            createdAt = 1700000000L,
            tenant = SaleTenant(
                tenantId = "t$1",
                companyId = 1,
                label = "Empresa 1",
                adminDb = "admin_db",
                contableDb = "acc_db",
                nominaDb = "pay_db",
            ),
        )

        writer.write(invoice)

        assertEquals(1, insertedEntities.size)
        val entity = insertedEntities.first()
        assertEquals("OFF-123", entity.id)
        assertEquals("PA", entity.countryCode)
        assertEquals("OFF-001", entity.localInvoiceNumber)
        assertEquals("t$1", entity.tenantId)
        assertEquals(1070L, entity.totalMinor)
        assertTrue(onQueuedCalled)
    }
}
