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
        val payload =
            TheFactoryHkaPayloadBuilder().build(
                context(
                    totalFactura = 65.0,
                    montoCancelar = 65.0,
                    vuelto = 35.0,
                    formasPago =
                        listOf(
                            FEFormaPagoData(
                                siglas = "EFECTIVO",
                                formaPagoFact = "02",
                                descripcion = "EFECTIVO",
                                monto = 65.0,
                                esCash = true,
                            ),
                        ),
                ),
            )

        val totales = payload.documento.totalesSubTotales
        assertEquals("65.00", totales.totalFactura)
        assertEquals("100.00", totales.totalValorRecibido)
        assertEquals("35.00", totales.vuelto)
        assertEquals("100.00", totales.listaFormaPago.single().valorCuotaPagada)
        assertEquals(
            "5411",
            payload.documento.listaItems
                .single()
                .codigoCPBS,
        )
        assertEquals(
            "54",
            payload.documento.listaItems
                .single()
                .codigoCPBSAbrev,
        )
    }

    @Test
    fun `100 percent credit sale sets tiempoPago 2, totalValorRecibido, and listaPagoPlazo without infoPagoCuota`() {
        val payload =
            TheFactoryHkaPayloadBuilder().build(
                context(
                    totalFactura = 23.0,
                    montoCancelar = 23.0,
                    vuelto = null,
                    formasPago =
                        listOf(
                            FEFormaPagoData(
                                siglas = "CXC",
                                formaPagoFact = "01",
                                descripcion = "CUENTAS POR COBRAR",
                                monto = 23.0,
                                esCash = false,
                            ),
                        ),
                ),
            )

        val totales = payload.documento.totalesSubTotales
        assertEquals("23.00", totales.totalFactura)
        assertEquals("23.00", totales.totalValorRecibido)
        assertEquals("2", totales.tiempoPago)
        assertEquals("01", totales.listaFormaPago.single().formaPagoFact)
        assertEquals("23.00", totales.listaFormaPago.single().valorCuotaPagada)
        assertEquals(null, totales.listaFormaPago.single().descFormaPago)
        assertEquals(1, totales.listaPagoPlazo?.size)
        assertEquals("23.00", totales.listaPagoPlazo?.single()?.valorCuota)
        assertEquals("CUOTA 1 DE 1 - CREDITO 30 DIAS", totales.listaPagoPlazo?.single()?.infoPagoCuota)
    }

    @Test
    fun `mixed sale sets tiempoPago 3, full totalValorRecibido, and listaPagoPlazo with credit portion only`() {
        val payload =
            TheFactoryHkaPayloadBuilder().build(
                context(
                    totalFactura = 100.0,
                    montoCancelar = 100.0,
                    vuelto = null,
                    formasPago =
                        listOf(
                            FEFormaPagoData(
                                siglas = "EFECTIVO",
                                formaPagoFact = "02",
                                descripcion = "EFECTIVO",
                                monto = 20.0,
                                esCash = true,
                            ),
                            FEFormaPagoData(
                                siglas = "CXC",
                                formaPagoFact = "01",
                                descripcion = "CUENTAS POR COBRAR",
                                monto = 80.0,
                                esCash = false,
                            ),
                        ),
                ),
            )

        val totales = payload.documento.totalesSubTotales
        assertEquals("100.00", totales.totalFactura)
        assertEquals("100.00", totales.totalValorRecibido)
        assertEquals("3", totales.tiempoPago)
        assertEquals(2, totales.listaFormaPago.size)
        assertEquals("02", totales.listaFormaPago[0].formaPagoFact)
        assertEquals("20.00", totales.listaFormaPago[0].valorCuotaPagada)
        assertEquals("01", totales.listaFormaPago[1].formaPagoFact)
        assertEquals("80.00", totales.listaFormaPago[1].valorCuotaPagada)
        assertEquals(1, totales.listaPagoPlazo?.size)
        assertEquals("80.00", totales.listaPagoPlazo?.single()?.valorCuota)
        assertEquals("CUOTA 1 DE 1 - CREDITO 30 DIAS", totales.listaPagoPlazo?.single()?.infoPagoCuota)
    }

    private fun context(
        totalFactura: Double,
        montoCancelar: Double?,
        vuelto: Double?,
        formasPago: List<FEFormaPagoData>,
    ) = InvoiceFEContext(
        config = buildFEConfig(),
        factura = buildFEFactura(totalFactura),
        cliente = buildFECliente(),
        detalles =
            listOf(
                buildFEDetalle(totalFactura),
            ),
        formasPago = formasPago,
        retencion = null,
        montoCancelar = montoCancelar,
        codigoSucursalEmisor = "0000",
        puntoFacturacionFiscal = "001",
        vuelto = vuelto,
    )

    private fun buildFEConfig() =
        FEConfigData(
            tokenEmpresa = "usuario",
            tokenPassword = "clave",
            apiTheFactoryHka = "https://example.com",
            tipoEmision = "01",
            destinoOperacion = "1",
            procesoGeneracion = "1",
            codigoSucursalEmisorFallback = "0000",
            puntoFacturacionFiscalFallback = "001",
            fechaInicioContingencia = null,
            motivoContingencia = null,
            tipoFacturacion = 3,
        )

    private fun buildFEFactura(totalFactura: Double) =
        FEFacturaData(
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
        )

    private fun buildFECliente() =
        FEClienteData(
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
        )

    private fun buildFEDetalle(totalFactura: Double) =
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
}
