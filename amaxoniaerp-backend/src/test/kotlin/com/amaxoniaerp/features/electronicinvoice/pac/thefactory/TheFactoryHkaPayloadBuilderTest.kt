package com.amaxoniaerp.features.electronicinvoice.pac.thefactory

import com.amaxoniaerp.features.electronicinvoice.domain.FEClienteData
import com.amaxoniaerp.features.electronicinvoice.domain.FEConfigData
import com.amaxoniaerp.features.electronicinvoice.domain.FEDetalleData
import com.amaxoniaerp.features.electronicinvoice.domain.FEFacturaData
import com.amaxoniaerp.features.electronicinvoice.domain.FEFormaPagoData
import com.amaxoniaerp.features.electronicinvoice.domain.InvoiceFEContext
import kotlin.test.Test
import kotlin.test.assertEquals

class TheFactoryHkaPayloadBuilderTest {

    @Test
    fun `cash overpayment adds change to paid installment value`() {
        val payload = TheFactoryHkaPayloadBuilder().build(
            context(
                totalFactura = 65.0,
                montoCancelar = 65.0,
                vuelto = 35.0,
                formasPago = listOf(
                    FEFormaPagoData(
                        siglas = "EFECTIVO",
                        formaPagoFact = "02",
                        descripcion = "EFECTIVO",
                        monto = 65.0,
                        esCash = true,
                    )
                ),
            )
        )

        val totales = payload.documento.totalesSubTotales
        assertEquals("65.00", totales.totalFactura)
        assertEquals("100.00", totales.totalValorRecibido)
        assertEquals("35.00", totales.vuelto)
        assertEquals("100.00", totales.listaFormaPago.single().valorCuotaPagada)
        assertEquals("5411", payload.documento.listaItems.single().codigoCPBS)
        assertEquals("54", payload.documento.listaItems.single().codigoCPBSAbrev)
    }

    private fun context(
        totalFactura: Double,
        montoCancelar: Double?,
        vuelto: Double?,
        formasPago: List<FEFormaPagoData>,
    ) = InvoiceFEContext(
        config = FEConfigData(
            tokenEmpresa = "usuario",
            tokenPassword = "clave",
            api_thefactoryhka = "https://example.com",
            tipoEmision = "01",
            destinoOperacion = "1",
            procesoGeneracion = "1",
            codigoSucursalEmisorFallback = "0000",
            puntoFacturacionFiscalFallback = "001",
            fechaInicioContingencia = null,
            motivoContingencia = null,
            tipoFacturacion = 3,
        ),
        factura = FEFacturaData(
            idFactura = "factura-1",
            codFactura = "001-00001",
            numeroDocumentoFiscal = "1",
            fechaFactura = "2026-06-10",
            tipoDocumento = "1",
            naturalezaOperacion = "01",
            tipoOperacion = "1",
            formatoCAFE = "1",
            entregaCAFE = "1",
            envioContenedor = "1",
            tipoVenta = "1",
            tipoFactura = "1",
            observacion = null,
            montoItemsFactura = totalFactura,
            ivaTotalFactura = 0.0,
            totalTotalFactura = totalFactura,
            totalizarDescuentoGlobal = 0.0,
            cajaId = "caja-1",
        ),
        cliente = FEClienteData(
            tipoClienteFE = "02",
            tipoContribuyente = "1",
            identificacion = "00000",
            dv = "",
            nombre = "CONSUMIDOR FINAL",
            codigoUbicacion = null,
            telefono = null,
            correo = null,
            direccion = null,
            paisIso = "PA",
            paisExtranjeroIso = null,
        ),
        detalles = listOf(
            FEDetalleData(
                descripcion = "Producto de prueba",
                codigo = "P001",
                unidadMedida = null,
                codigoCPBS = null,
                codigoCPBSAbrev = null,
                cantidad = 1.0,
                precioSinIva = totalFactura,
                montoDescuento = 0.0,
                piva = 0.0,
                totalSinIva = totalFactura,
                totalConIva = totalFactura,
                porcentajeIsc = null,
                importeIsc = null,
                idOti = null,
                importeOti = null,
            )
        ),
        formasPago = formasPago,
        retencion = null,
        montoCancelar = montoCancelar,
        codigoSucursalEmisor = "0000",
        puntoFacturacionFiscal = "001",
        vuelto = vuelto,
    )
}
