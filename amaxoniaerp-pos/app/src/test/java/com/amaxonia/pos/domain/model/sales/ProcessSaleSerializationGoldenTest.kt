package com.amaxonia.pos.domain.model.sales

import com.amaxonia.pos.data.local.AppJson
import kotlinx.serialization.encodeToString
import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Paths

class ProcessSaleSerializationGoldenTest {
    @Test
    fun saleRequestMatchesBackendContractGolden() {
        val request = sampleRequest()
        val actual = AppJson.parseToJsonElement(AppJson.encodeToString(request))
        val expected = AppJson.parseToJsonElement(readGolden("process-sale-request.json"))

        assertEquals(expected, actual)
        assertEquals(request, AppJson.decodeFromString(ProcessSaleRequestDto.serializer(), actual.toString()))
    }

    private fun sampleRequest(): ProcessSaleRequestDto =
        ProcessSaleRequestDto(
            idFactura = "offline-id-001",
            factura =
                SaleInvoiceDto(
                    idCliente = "client-1",
                    codCliente = "C0001",
                    codVendedor = 7,
                    idShop = 2,
                    idSucursal = 2,
                    idCaja = "caja-1",
                    codigoCaja = "CJ01",
                    idCajaSecuencia = "seq-1",
                    serieSucursal = "A01",
                    formaPago = "contado",
                    codEstatus = 2,
                    subtotal = 10.0,
                    descuentosItemFactura = 1.0,
                    ivaTotalFactura = 1.44,
                    totalTotalFactura = 10.44,
                    montoItemsFactura = 9.0,
                    totalizarSubTotal = 10.0,
                    totalizarDescuentoParcial = 1.0,
                    totalizarTotalOperacion = 9.0,
                    totalizarBaseImponible = 9.0,
                    totalizarMontoIva = 1.44,
                    totalizarTotalGeneral = 10.44,
                    usuarioCreacion = "POS",
                    facturarA = "CLIENTE PRUEBA",
                    facturarARuc = "TEST-ID",
                    facturarADireccion = "DIRECCION",
                    facturarATelefono = "0000",
                ),
            items =
                listOf(
                    SaleItemDto(
                        idItem = 42,
                        codVendedor = 7,
                        itemAlmacen = 3,
                        itemDescripcion = "PRODUCTO",
                        itemCantidad = 2.0,
                        itemPrecioSinIva = 5.0,
                        itemDescuento = 10.0,
                        itemMontoDescuento = 1.0,
                        itemPIva = 16.0,
                        itemTotalSinIva = 9.0,
                        itemTotalConIva = 10.44,
                        itemCantidadTotal = 2.0,
                        itemCodigo = "P42",
                        itemReferencia = "REF42",
                    ),
                ),
            impuestos = listOf(SaleTaxDto(9.0, 1, 1.44)),
            pagoResumen = SalePaymentSummaryDto(10.44, 20.0, 9.56, 0.0, mapOf("CASH" to 10.44)),
            pagos = listOf(SalePaymentDto(1, "CASH", 10.44, 20.0, 9.56)),
            moneda = SaleCurrencyDto("NO", 1.0, 0, 1, "USD", 1, "USD", 10.44),
        )

    private fun readGolden(name: String): String {
        val uri = checkNotNull(javaClass.classLoader?.getResource("golden/$name")).toURI()
        return String(Files.readAllBytes(Paths.get(uri)), Charsets.UTF_8)
    }
}
