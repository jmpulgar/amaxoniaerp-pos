package com.amaxoniaerp.features.electronicinvoice.pac.thefactory

import com.amaxoniaerp.features.electronicinvoice.domain.FEClienteData
import com.amaxoniaerp.features.electronicinvoice.domain.FEConfigData
import com.amaxoniaerp.features.electronicinvoice.domain.FEDetalleData
import com.amaxoniaerp.features.electronicinvoice.domain.FEFacturaData
import com.amaxoniaerp.features.electronicinvoice.domain.FEFormaPagoData
import com.amaxoniaerp.features.electronicinvoice.domain.FEValidacionException
import com.amaxoniaerp.features.electronicinvoice.domain.InvoiceFEContext
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

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

    @Test
    fun `contingencia 02 usa la hora actual como inicio de contingencia y no la fecha de la factura`() {
        val payload = builderFijo().build(context(tipoEmision = "02"))
        val datos = payload.documento.datosTransaccion

        assertEquals("02", datos.tipoEmision)
        assertEquals("2026-09-03T14:00:00-05:00", datos.fechaInicioContingencia)
        assertEquals("Problemas de comunicación interna.", datos.motivoContingencia)
        assertEquals("2026-06-10T00:00:00-05:00", datos.fechaEmision)
    }

    @Test
    fun `emision 04 conserva la fecha de contingencia configurada`() {
        val payload =
            builderFijo().build(
                context(
                    tipoEmision = "04",
                    fechaInicioContingencia = "2026-09-01T08:00:00-05:00",
                    motivoContingencia = "Caida del enlace",
                ),
            )
        val datos = payload.documento.datosTransaccion

        assertEquals("04", datos.tipoEmision)
        assertEquals("2026-09-01T08:00:00-05:00", datos.fechaInicioContingencia)
        assertEquals("Caida del enlace", datos.motivoContingencia)
    }

    @Test
    fun `emision normal 01 no informa contingencia`() {
        val payload = builderFijo().build(context(tipoEmision = "01"))

        assertEquals("01", payload.documento.datosTransaccion.tipoEmision)
        assertEquals(null, payload.documento.datosTransaccion.fechaInicioContingencia)
        assertEquals(null, payload.documento.datosTransaccion.motivoContingencia)
    }

    @Test
    fun `cliente gobierno sin CPBS rechaza el payload antes de enviarlo al PAC`() {
        val ex =
            runCatching {
                builderFijo().build(
                    context(
                        tipoClienteFE = "03",
                        detalles = listOf(detalleConCpbs(null, null)),
                    ),
                )
            }.exceptionOrNull()

        assertIs<FEValidacionException>(ex)
        assertTrue(ex.message?.contains("CPBS") == true, "el mensaje debe identificar el CPBS faltante: ${ex.message}")
    }

    @Test
    fun `cliente gobierno con CPBS en los detalles construye el payload`() {
        val payload =
            builderFijo().build(
                context(
                    tipoClienteFE = "03",
                    detalles = listOf(detalleConCpbs("541100", "54")),
                ),
            )

        val item = payload.documento.listaItems.single()
        assertEquals("541100", item.codigoCPBS)
        assertEquals("54", item.codigoCPBSAbrev)
        assertEquals("und", item.unidadMedidaCPBS)
    }

    private fun builderFijo(): TheFactoryHkaPayloadBuilder =
        TheFactoryHkaPayloadBuilder(
            Clock.fixed(Instant.parse("2026-09-03T19:00:00Z"), ZoneId.of("America/Panama")),
        )

    private fun context(
        totalFactura: Double = 100.0,
        montoCancelar: Double? = null,
        vuelto: Double? = null,
        formasPago: List<FEFormaPagoData>? = null,
        tipoEmision: String = "01",
        fechaInicioContingencia: String? = null,
        motivoContingencia: String? = null,
        tipoClienteFE: String = "02",
        detalles: List<FEDetalleData>? = null,
    ) = InvoiceFEContext(
        config =
            buildFEConfig().copy(
                tipoEmision = tipoEmision,
                fechaInicioContingencia = fechaInicioContingencia,
                motivoContingencia = motivoContingencia,
            ),
        factura = buildFEFactura(totalFactura),
        cliente = buildFECliente(tipoClienteFE),
        detalles = detalles ?: listOf(buildFEDetalle(totalFactura)),
        formasPago =
            formasPago
                ?: listOf(
                    FEFormaPagoData(
                        siglas = "EFECTIVO",
                        formaPagoFact = "02",
                        descripcion = "EFECTIVO",
                        monto = totalFactura,
                        esCash = true,
                    ),
                ),
        retencion = null,
        montoCancelar = montoCancelar ?: totalFactura,
        codigoSucursalEmisor = "0000",
        puntoFacturacionFiscal = "001",
        vuelto = vuelto,
    )

    private fun detalleConCpbs(
        codigoCPBS: String?,
        codigoCPBSAbrev: String?,
    ): FEDetalleData = buildFEDetalle(100.0).copy(codigoCPBS = codigoCPBS, codigoCPBSAbrev = codigoCPBSAbrev)

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

    private fun buildFECliente(tipoClienteFE: String = "02") =
        FEClienteData(
            tipoClienteFE = tipoClienteFE,
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
