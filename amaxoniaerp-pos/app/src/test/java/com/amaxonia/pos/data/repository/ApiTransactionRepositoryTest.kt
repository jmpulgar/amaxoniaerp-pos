package com.amaxonia.pos.data.repository

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.amaxonia.pos.data.local.AppJson
import com.amaxonia.pos.data.local.CompanyDetailsSnapshot
import com.amaxonia.pos.data.local.CompanySessionSnapshot
import com.amaxonia.pos.data.local.LocalStore
import com.amaxonia.pos.data.local.db.PendingInvoiceDao
import com.amaxonia.pos.data.local.db.PendingInvoiceEntity
import com.amaxonia.pos.data.local.saveCompanySession
import com.amaxonia.pos.data.remote.api.SalesApi
import com.amaxonia.pos.domain.model.TransactionStatus
import com.amaxonia.pos.domain.model.sales.ConfirmFacturaFiscalRequestDto
import com.amaxonia.pos.domain.model.sales.ConfirmFacturaFiscalResponseDto
import com.amaxonia.pos.domain.model.sales.EnviarCorreoFacturaResponseDto
import com.amaxonia.pos.domain.model.sales.FacturaDetalleResponseDto
import com.amaxonia.pos.domain.model.sales.FacturaPrintPayloadDto
import com.amaxonia.pos.domain.model.sales.FacturaSummaryDto
import com.amaxonia.pos.domain.model.sales.FacturasListResponseDto
import com.amaxonia.pos.domain.model.sales.FacturasResumenDto
import com.amaxonia.pos.domain.model.sales.ProcessSaleRequestDto
import com.amaxonia.pos.domain.model.sales.ProcessSaleResponseDto
import com.amaxonia.pos.domain.model.sales.ReconciledInvoice
import com.amaxonia.pos.domain.model.sales.SaleInvoiceDto
import com.amaxonia.pos.domain.model.sales.SaleItemDto
import com.amaxonia.pos.domain.model.sales.SalePaymentSummaryDto
import com.amaxonia.pos.domain.repository.InvoiceHistoryFilter
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ApiTransactionRepositoryTest {

    private lateinit var localStore: LocalStore
    private val pendingEntities = mutableListOf<PendingInvoiceEntity>()

    private val fakePendingDao = object : PendingInvoiceDao {
        override suspend fun insert(invoice: PendingInvoiceEntity) {
            pendingEntities.add(invoice)
        }

        override suspend fun getPending(limit: Int): List<PendingInvoiceEntity> = pendingEntities
        override suspend fun getPendingForTenant(tenantId: String, limit: Int): List<PendingInvoiceEntity> =
            pendingEntities.filter { it.tenantId == tenantId }

        override suspend fun getCreatedBetween(fromMillis: Long, toMillis: Long): List<PendingInvoiceEntity> = emptyList()
        override suspend fun countPending(): Int = pendingEntities.size
        override suspend fun markSending(id: String, updatedAt: Long) {}
        override suspend fun markSent(id: String, remoteInvoiceId: String, remoteInvoiceNumber: String, updatedAt: Long) {}
        override suspend fun markFailed(id: String, message: String, updatedAt: Long) {}
        override suspend fun markInvalid(id: String, message: String, updatedAt: Long) {}
        override suspend fun recoverInterrupted(staleBefore: Long, updatedAt: Long) {}
        override suspend fun tryClaim(id: String, now: Long, leasedUntil: Long, updatedAt: Long): Int = 1
    }

    private val fakeSalesApi = object : SalesApi {
        override suspend fun processSale(authHeader: String, payload: ProcessSaleRequestDto): Result<ProcessSaleResponseDto> =
            error("Not used")

        override suspend fun getFacturas(
            authHeader: String,
            limit: Int,
            offset: Long,
            filter: InvoiceHistoryFilter,
        ): Result<FacturasListResponseDto> = Result.success(
            FacturasListResponseDto(
                data = listOf(
                    FacturaSummaryDto(
                        id = "REMOTE-1",
                        codigo = "F001-0001",
                        fecha = "26/08/2026",
                        fechaCreacion = "26/08/2026 10:00:00",
                        fechaDgi = "",
                        total = 50.0,
                        moneda = "USD",
                        estatus = "Pagada",
                        clienteNombre = "Cliente Remoto",
                        clienteIdentificacion = "R-111",
                        formaPago = "Efectivo",
                        totalRef = null,
                        abrMonedaSecundaria = null,
                    ),
                ),
                total = 1,
            ),
        )

        override suspend fun getFacturasResumen(
            authHeader: String,
            filter: InvoiceHistoryFilter,
        ): Result<FacturasResumenDto> = Result.success(
            FacturasResumenDto(
                ventasNetas = 50.0,
                totalFacturas = 1,
                moneda = "USD",
            ),
        )

        override suspend fun getFacturaDetalle(authHeader: String, facturaId: String): Result<FacturaDetalleResponseDto> =
            Result.failure(IllegalStateException("Not found remotely"))

        override suspend fun findByCorrelationId(authHeader: String, clientCorrelationId: String): Result<ReconciledInvoice?> =
            error("Not used")

        override suspend fun confirmFacturaFiscal(
            authHeader: String,
            facturaId: String,
            payload: ConfirmFacturaFiscalRequestDto,
        ): Result<ConfirmFacturaFiscalResponseDto> = error("Not used")

        override suspend fun getPrintPayload(authHeader: String, facturaId: String): Result<FacturaPrintPayloadDto> =
            error("Not used")

        override suspend fun sendReceiptEmail(authHeader: String, facturaId: String): Result<EnviarCorreoFacturaResponseDto> =
            error("Not used")
    }

    private val testCompany = CompanySessionSnapshot(
        token = "test-token",
        company = CompanyDetailsSnapshot(
            id = 1,
            name = "TEST RESTAURANT",
            adminDb = "admin",
            accountingDb = "acc",
            payrollDb = "pay",
            rif = "J-12345678-0",
        ),
    )

    @Before
    fun setUp() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        localStore = LocalStore(context, FakeSecureKeyValueStore())
        localStore.saveCompanySession(testCompany)
        pendingEntities.clear()
    }

    @Test
    fun getTransactionsMergesPendingOfflineInvoicesWithRemoteInvoices() = runTest {
        val pendingSale = ProcessSaleRequestDto(
            idFactura = "OFF-100",
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
                subtotal = 20.00,
                descuentosItemFactura = 0.0,
                ivaTotalFactura = 1.40,
                totalTotalFactura = 21.40,
                montoItemsFactura = 20.00,
                totalizarBaseImponible = 20.00,
                totalizarMontoIva = 1.40,
                totalizarTotalGeneral = 21.40,
                usuarioCreacion = "admin",
                facturarA = "Cliente Local",
                facturarARuc = "8-999",
                facturarADireccion = "",
                facturarATelefono = "",
            ),
            items = listOf(
                SaleItemDto(
                    idItem = 1,
                    itemAlmacen = 1,
                    itemDescripcion = "Item Offline",
                    itemCantidad = 1.0,
                    itemCantidadTotal = 1.0,
                    itemPrecioSinIva = 20.00,
                    itemDescuento = 0.0,
                    itemMontoDescuento = 0.0,
                    itemPIva = 7.0,
                    itemTotalSinIva = 20.00,
                    itemTotalConIva = 21.40,
                ),
            ),
            pagoResumen = SalePaymentSummaryDto(21.40, 21.40, 0.0, 0.0, emptyMap()),
            pagos = emptyList(),
        )

        fakePendingDao.insert(
            PendingInvoiceEntity(
                id = "OFF-100",
                countryCode = "PA",
                payloadJson = AppJson.encodeToString(ProcessSaleRequestDto.serializer(), pendingSale),
                localInvoiceNumber = "OFF-001",
                clientName = "Cliente Local",
                createdAt = 1700000000L,
                updatedAt = 1700000000L,
                tenantId = "t$1",
                tenantCompanyId = 1,
                tenantLabel = "TEST RESTAURANT",
                totalMinor = 2140L,
            ),
        )

        val repository = ApiTransactionRepository(fakeSalesApi, localStore, fakePendingDao)

        val page = repository.getTransactions().getOrThrow()
        assertEquals(2, page.transactions.size)
        assertEquals(2, page.total)

        val offlineTrx = page.transactions.first { it.id == "OFF-100" }
        assertEquals("OFF-001", offlineTrx.invoiceNumber)
        assertEquals(TransactionStatus.PENDING, offlineTrx.status)
        assertEquals("Cliente Local", offlineTrx.clienteNombre)
        assertEquals(21.40, offlineTrx.amount, 0.001)

        val remoteTrx = page.transactions.first { it.id == "REMOTE-1" }
        assertEquals("F001-0001", remoteTrx.invoiceNumber)
        assertEquals(TransactionStatus.PAID, remoteTrx.status)
    }

    @Test
    fun getInvoiceDetailReturnsLocalItemDetailForPendingInvoice() = runTest {
        val pendingSale = ProcessSaleRequestDto(
            idFactura = "OFF-200",
            codFactura = "OFF-002",
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
                ivaTotalFactura = 0.0,
                totalTotalFactura = 10.00,
                montoItemsFactura = 10.00,
                totalizarBaseImponible = 10.00,
                totalizarMontoIva = 0.0,
                totalizarTotalGeneral = 10.00,
                usuarioCreacion = "admin",
                facturarA = "Cliente Detalle",
                facturarARuc = "8-111",
                facturarADireccion = "",
                facturarATelefono = "",
            ),
            items = listOf(
                SaleItemDto(
                    idItem = 99,
                    itemAlmacen = 1,
                    itemDescripcion = "Producto Detalle Local",
                    itemCantidad = 2.0,
                    itemCantidadTotal = 2.0,
                    itemPrecioSinIva = 5.00,
                    itemDescuento = 0.0,
                    itemMontoDescuento = 0.0,
                    itemPIva = 0.0,
                    itemTotalSinIva = 10.00,
                    itemTotalConIva = 10.00,
                    itemCodigo = "COD-99",
                ),
            ),
            pagoResumen = SalePaymentSummaryDto(10.00, 10.00, 0.0, 0.0, emptyMap()),
            pagos = emptyList(),
        )

        fakePendingDao.insert(
            PendingInvoiceEntity(
                id = "OFF-200",
                countryCode = "PA",
                payloadJson = AppJson.encodeToString(ProcessSaleRequestDto.serializer(), pendingSale),
                localInvoiceNumber = "OFF-002",
                clientName = "Cliente Detalle",
                createdAt = 1700000000L,
                updatedAt = 1700000000L,
                tenantId = "t$1",
                tenantCompanyId = 1,
                tenantLabel = "TEST RESTAURANT",
                totalMinor = 1000L,
            ),
        )

        val repository = ApiTransactionRepository(fakeSalesApi, localStore, fakePendingDao)

        val detail = repository.getInvoiceDetail("OFF-200").getOrThrow()
        assertEquals("OFF-200", detail.idFactura)
        assertEquals("OFF-002", detail.codFactura)
        assertEquals(1, detail.items.size)
        assertEquals("Producto Detalle Local", detail.items.first().descripcion)
        assertEquals(5.00, detail.items.first().precioUnitario, 0.001)
        assertEquals(10.00, detail.items.first().totalConIva, 0.001)
    }
}
