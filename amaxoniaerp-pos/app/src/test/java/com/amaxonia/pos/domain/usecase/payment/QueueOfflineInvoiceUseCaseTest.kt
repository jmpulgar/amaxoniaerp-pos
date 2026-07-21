package com.amaxonia.pos.domain.usecase.payment

import com.amaxonia.pos.domain.model.sales.ProcessSaleRequestDto
import com.amaxonia.pos.domain.model.sales.SaleInvoiceDto
import com.amaxonia.pos.domain.model.sales.SalePaymentSummaryDto
import com.amaxonia.pos.domain.model.tenant.SaleTenant
import com.amaxonia.pos.domain.system.AppClock
import com.amaxonia.pos.domain.system.IdGenerator
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class QueueOfflineInvoiceUseCaseTest {
    @Test
    fun persistsStableIdempotencyIdentifiersAndTenantBeforeReturning() =
        runTest {
            var written: OfflineInvoice? = null
            val useCase =
                QueueOfflineInvoiceUseCase(
                    writer = OfflineInvoiceWriter { written = it },
                    idGenerator = IdGenerator { "generated-id" },
                    clock = AppClock { Instant.ofEpochMilli(123456789L) },
                )

            val tenant =
                SaleTenant(
                    tenantId = SaleTenant.idFor(7),
                    companyId = 7,
                    label = "Empresa 7",
                    adminDb = "admin7",
                    contableDb = "contable7",
                    nominaDb = "nomina7",
                )
            val result = useCase("VE", request(), 10.0, "CLIENT", tenant)

            assertEquals("generated-id", result.id)
            assertEquals("OFF-123456789", result.localInvoiceNumber)
            assertEquals(result.id, result.request.idFactura)
            assertEquals(result.localInvoiceNumber, result.request.codFactura)
            assertEquals("t$7", result.tenant.tenantId)
            assertEquals(result, written)
            assertEquals(tenant, written?.tenant)
        }

    private fun request(): ProcessSaleRequestDto =
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
                    subtotal = 10.0,
                    ivaTotalFactura = 0.0,
                    totalTotalFactura = 10.0,
                    montoItemsFactura = 10.0,
                    totalizarBaseImponible = 10.0,
                    totalizarMontoIva = 0.0,
                    totalizarTotalGeneral = 10.0,
                    usuarioCreacion = "POS",
                    facturarA = "CLIENT",
                    facturarARuc = "CF",
                    facturarADireccion = "",
                    facturarATelefono = "",
                ),
            items = emptyList(),
            pagoResumen = SalePaymentSummaryDto(10.0, 10.0, 0.0, 0.0, emptyMap()),
            pagos = emptyList(),
        )
}
