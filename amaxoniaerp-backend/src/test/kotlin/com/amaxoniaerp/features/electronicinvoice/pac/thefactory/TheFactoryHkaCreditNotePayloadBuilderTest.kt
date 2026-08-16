package com.amaxoniaerp.features.electronicinvoice.pac.thefactory

import com.amaxoniaerp.features.electronicinvoice.domain.FEClienteData
import com.amaxoniaerp.features.electronicinvoice.domain.FEConfigData
import com.amaxoniaerp.features.electronicinvoice.domain.FEDetalleData
import com.amaxoniaerp.features.electronicinvoice.domain.FEFacturaData
import com.amaxoniaerp.features.electronicinvoice.domain.InvoiceFEContext
import com.amaxoniaerp.features.electronicinvoice.domain.PanamaCreditNotePayloadContext
import kotlinx.serialization.encodeToString
import kotlin.test.Test
import kotlin.test.assertEquals

class TheFactoryHkaCreditNotePayloadBuilderTest {
    @Test
    fun `builds type 04 with original electronic invoice reference and task 11 totals`() {
        val payload = TheFactoryHkaCreditNotePayloadBuilder().build(context())
        val transaction = payload.documento.datosTransaccion
        val totals = payload.documento.totalesSubTotales

        val referencedDocument = transaction.listaDocsFiscalReferenciados?.single()
        assertEquals("04", transaction.tipoDocumento)
        assertEquals("2026-08-01T00:00:00-05:00", referencedDocument?.fechaEmisionDocFiscalReferenciado)
        assertEquals(ORIGINAL_CUFE, referencedDocument?.cufeFEReferenciada)
        assertEquals("9.35", totals.totalPrecioNeto)
        assertEquals("0.65", totals.totalITBMS)
        assertEquals("10.00", totals.totalFactura)
        assertEquals("0.65", totals.totalDescuento)

        val goldenJson = feJson.encodeToString(payload)
        assertEquals(
            """{"documento":{"codigoSucursalEmisor":"0000","datosTransaccion":{"tipoEmision":"01",""" +
                """"tipoDocumento":"04","numeroDocumentoFiscal":"0000009001","puntoFacturacionFiscal":"001",""" +
                """"fechaEmision":"2026-08-12T00:00:00-05:00","naturalezaOperacion":"01",""" +
                """"tipoOperacion":"1","destinoOperacion":"1","formatoCAFE":"1","entregaCAFE":"1",""" +
                """"envioContenedor":"1","procesoGeneracion":"1","tipoVenta":"1",""" +
                """"informacionInteres":"Devolución parcial","cliente":{"tipoClienteFE":"02",""" +
                """"tipoContribuyente":"1","numeroRUC":"155-001-001","digitoVerificadorRUC":"1",""" +
                """"razonSocial":"CLIENTE PRUEBA","direccion":"Calle 1","telefono1":"6000-0000",""" +
                """"correoElectronico1":"cliente@example.com","pais":"PA"},""" +
                """"listaDocsFiscalReferenciados":[{""" +
                """"fechaEmisionDocFiscalReferenciado":"2026-08-01T00:00:00-05:00",""" +
                """"cufeFEReferenciada":"$ORIGINAL_CUFE"}]},"listaItems":[{"descripcion":"Producto devuelto",""" +
                """"codigo":"P-001","cantidad":"1.000","precioUnitario":"9.35","precioItem":"9.35",""" +
                """"valorTotal":"10.00","tasaITBMS":"01","valorITBMS":"0.65","codigoCPBSAbrev":"54",""" +
                """"codigoCPBS":"5411"}],"totalesSubTotales":{"totalPrecioNeto":"9.35","totalITBMS":"0.65",""" +
                """"totalMontoGravado":"0.65","totalDescuento":"0.65","totalFactura":"10.00",""" +
                """"totalValorRecibido":"10.00","tiempoPago":"1","nroItems":"1","totalTodosItems":"10.65",""" +
                """"listaDescBonificacion":[{"descDescuento":"Descuento Global","montoDescuento":"0.65"}],""" +
                """"listaFormaPago":[{"formaPagoFact":"99","descFormaPago":"Otro medio de pago",""" +
                """"valorCuotaPagada":"0.00"}]}}}""",
            goldenJson,
        )
    }

    private fun context() =
        PanamaCreditNotePayloadContext(
            invoice =
                InvoiceFEContext(
                    config =
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
                        ),
                    factura =
                        FEFacturaData(
                            idFactura = "nc-001",
                            codFactura = "NC-000001",
                            numeroDocumentoFiscal = "9001",
                            fechaFactura = "2026-08-12",
                            tipoDocumento = "01",
                            naturalezaOperacion = "01",
                            tipoOperacion = "1",
                            formatoCAFE = "1",
                            entregaCAFE = "1",
                            envioContenedor = "1",
                            tipoVenta = "1",
                            tipoFactura = "nota_credito",
                            observacion = "Devolución parcial",
                            montoItemsFactura = 9.35,
                            ivaTotalFactura = 0.65,
                            totalTotalFactura = 10.0,
                            totalizarDescuentoGlobal = 0.65,
                            cajaId = "caja-001",
                        ),
                    cliente =
                        FEClienteData(
                            tipoClienteFE = "02",
                            tipoContribuyente = "1",
                            identificacion = "155-001-001",
                            dv = "1",
                            nombre = "CLIENTE PRUEBA",
                            codigoUbicacion = null,
                            telefono = "6000-0000",
                            correo = "cliente@example.com",
                            direccion = "Calle 1",
                            paisIso = "PA",
                            paisExtranjeroIso = null,
                        ),
                    detalles =
                        listOf(
                            FEDetalleData(
                                descripcion = "Producto devuelto",
                                codigo = "P-001",
                                unidadMedida = "und",
                                codigoCPBS = null,
                                codigoCPBSAbrev = null,
                                cantidad = 1.0,
                                precioSinIva = 9.35,
                                montoDescuento = 0.0,
                                piva = 7.0,
                                totalSinIva = 9.35,
                                totalConIva = 10.0,
                                porcentajeIsc = null,
                                importeIsc = null,
                                idOti = null,
                                importeOti = null,
                            ),
                        ),
                    formasPago = emptyList(),
                    retencion = null,
                    montoCancelar = null,
                    codigoSucursalEmisor = "0000",
                    puntoFacturacionFiscal = "001",
                    vuelto = null,
                ),
            originalInvoiceCufe = ORIGINAL_CUFE,
            originalInvoiceDate = "2026-08-01",
            originalInvoiceFiscalNumber = "7001",
        )

    private companion object {
        val ORIGINAL_CUFE = "A".repeat(66)
    }
}
