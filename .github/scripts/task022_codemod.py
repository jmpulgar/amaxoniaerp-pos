from pathlib import Path

ROOT = Path("amaxoniaerp-backend/src/test/kotlin/com/amaxoniaerp")

replacements = {
    ROOT / "features/electronicinvoice/pac/thefactory/TheFactoryHkaCreditNotePayloadBuilderTest.kt": [
        (
            '        assertEquals("04", transaction.tipoDocumento)\n'
            '        assertEquals("2026-08-01T00:00:00-05:00", transaction.listaDocsFiscalReferenciados?.single()?.fechaEmisionDocFiscalReferenciado)\n'
            '        assertEquals(ORIGINAL_CUFE, transaction.listaDocsFiscalReferenciados?.single()?.cufeFEReferenciada)\n',
            '        val referencedDocument = transaction.listaDocsFiscalReferenciados?.single()\n'
            '        assertEquals("04", transaction.tipoDocumento)\n'
            '        assertEquals("2026-08-01T00:00:00-05:00", referencedDocument?.fechaEmisionDocFiscalReferenciado)\n'
            '        assertEquals(ORIGINAL_CUFE, referencedDocument?.cufeFEReferenciada)\n',
        ),
        (
            '            """{"documento":{"codigoSucursalEmisor":"0000","datosTransaccion":{"tipoEmision":"01","tipoDocumento":"04","numeroDocumentoFiscal":"0000009001","puntoFacturacionFiscal":"001","fechaEmision":"2026-08-12T00:00:00-05:00","naturalezaOperacion":"01","tipoOperacion":"1","destinoOperacion":"1","formatoCAFE":"1","entregaCAFE":"1","envioContenedor":"1","procesoGeneracion":"1","tipoVenta":"1","informacionInteres":"Devolución parcial","cliente":{"tipoClienteFE":"02","tipoContribuyente":"1","numeroRUC":"155-001-001","digitoVerificadorRUC":"1","razonSocial":"CLIENTE PRUEBA","direccion":"Calle 1","telefono1":"6000-0000","correoElectronico1":"cliente@example.com","pais":"PA"},"listaDocsFiscalReferenciados":[{"fechaEmisionDocFiscalReferenciado":"2026-08-01T00:00:00-05:00","cufeFEReferenciada":"$ORIGINAL_CUFE"}]},"listaItems":[{"descripcion":"Producto devuelto","codigo":"P-001","cantidad":"1.000","precioUnitario":"9.35","precioItem":"9.35","valorTotal":"10.00","tasaITBMS":"01","valorITBMS":"0.65","codigoCPBSAbrev":"54","codigoCPBS":"5411"}],"totalesSubTotales":{"totalPrecioNeto":"9.35","totalITBMS":"0.65","totalMontoGravado":"0.65","totalDescuento":"0.65","totalFactura":"10.00","totalValorRecibido":"10.00","tiempoPago":"1","nroItems":"1","totalTodosItems":"10.65","listaDescBonificacion":[{"descDescuento":"Descuento Global","montoDescuento":"0.65"}],"listaFormaPago":[{"formaPagoFact":"99","descFormaPago":"Otro medio de pago","valorCuotaPagada":"0.00"}]}}}""",\n',
            '            """{"documento":{"codigoSucursalEmisor":"0000","datosTransaccion":{"tipoEmision":"01",""" +\n'
            '                """"tipoDocumento":"04","numeroDocumentoFiscal":"0000009001","puntoFacturacionFiscal":"001",""" +\n'
            '                """"fechaEmision":"2026-08-12T00:00:00-05:00","naturalezaOperacion":"01",""" +\n'
            '                """"tipoOperacion":"1","destinoOperacion":"1","formatoCAFE":"1","entregaCAFE":"1",""" +\n'
            '                """"envioContenedor":"1","procesoGeneracion":"1","tipoVenta":"1",""" +\n'
            '                """"informacionInteres":"Devolución parcial","cliente":{"tipoClienteFE":"02",""" +\n'
            '                """"tipoContribuyente":"1","numeroRUC":"155-001-001","digitoVerificadorRUC":"1",""" +\n'
            '                """"razonSocial":"CLIENTE PRUEBA","direccion":"Calle 1","telefono1":"6000-0000",""" +\n'
            '                """"correoElectronico1":"cliente@example.com","pais":"PA"},""" +\n'
            '                """"listaDocsFiscalReferenciados":[{"fechaEmisionDocFiscalReferenciado":"2026-08-01T00:00:00-05:00",""" +\n'
            '                """"cufeFEReferenciada":"$ORIGINAL_CUFE"}]},"listaItems":[{"descripcion":"Producto devuelto",""" +\n'
            '                """"codigo":"P-001","cantidad":"1.000","precioUnitario":"9.35","precioItem":"9.35",""" +\n'
            '                """"valorTotal":"10.00","tasaITBMS":"01","valorITBMS":"0.65","codigoCPBSAbrev":"54",""" +\n'
            '                """"codigoCPBS":"5411"}],"totalesSubTotales":{"totalPrecioNeto":"9.35","totalITBMS":"0.65",""" +\n'
            '                """"totalMontoGravado":"0.65","totalDescuento":"0.65","totalFactura":"10.00",""" +\n'
            '                """"totalValorRecibido":"10.00","tiempoPago":"1","nroItems":"1","totalTodosItems":"10.65",""" +\n'
            '                """"listaDescBonificacion":[{"descDescuento":"Descuento Global","montoDescuento":"0.65"}],""" +\n'
            '                """"listaFormaPago":[{"formaPagoFact":"99","descFormaPago":"Otro medio de pago",""" +\n'
            '                """"valorCuotaPagada":"0.00"}]}}}""",\n',
        ),
    ],
    ROOT / "features/electronicinvoice/pac/thefactory/venezuela/VenezuelaHkaPayloadBuilderTest.kt": [
        (
            '                        detalle(precioSinIva = "100.00", totalSinIva = "100.00", totalConIva = "116.00", piva = "16.00"),\n',
            '                        detalle(\n'
            '                            precioSinIva = "100.00",\n'
            '                            totalSinIva = "100.00",\n'
            '                            totalConIva = "116.00",\n'
            '                            piva = "16.00",\n'
            '                        ),\n',
        ),
        (
            '                        detalle(precioSinIva = "100.00", totalSinIva = "100.00", totalConIva = "116.00", piva = "16.00"),\n',
            '                        detalle(\n'
            '                            precioSinIva = "100.00",\n'
            '                            totalSinIva = "100.00",\n'
            '                            totalConIva = "116.00",\n'
            '                            piva = "16.00",\n'
            '                        ),\n',
        ),
        (
            '        val ctx = context(comprador = VECompradorData(nombreRazonSocial = "", rif = "", direccion = null, telefono = null, email = null))\n',
            '        val ctx =\n'
            '            context(\n'
            '                comprador =\n'
            '                    VECompradorData(\n'
            '                        nombreRazonSocial = "",\n'
            '                        rif = "",\n'
            '                        direccion = null,\n'
            '                        telefono = null,\n'
            '                        email = null,\n'
            '                    ),\n'
            '            )\n',
        ),
        (
            '                        detalle(precioSinIva = "100.005", totalSinIva = "100.005", totalConIva = "116.006", piva = "16.00"),\n',
            '                        detalle(\n'
            '                            precioSinIva = "100.005",\n'
            '                            totalSinIva = "100.005",\n'
            '                            totalConIva = "116.006",\n'
            '                            piva = "16.00",\n'
            '                        ),\n',
        ),
        (
            '    private fun VenezuelaHkaFormaPago.esDivisaMoneda(): Boolean = descripcion?.contains("DIVISA", ignoreCase = true) == true\n',
            '    private fun VenezuelaHkaFormaPago.esDivisaMoneda(): Boolean {\n'
            '        return descripcion?.contains("DIVISA", ignoreCase = true) == true\n'
            '    }\n',
        ),
    ],
    ROOT / "features/electronicinvoice/domain/VenezuelaInvoiceStrategyTest.kt": [
        (
            '                    alreadyIssued = AlreadyIssuedResult.Partial(numeroDocumentoFiscal = "00000099", numeroControl = null),\n',
            '                    alreadyIssued =\n'
            '                        AlreadyIssuedResult.Partial(\n'
            '                            numeroDocumentoFiscal = "00000099",\n'
            '                            numeroControl = null,\n'
            '                        ),\n',
        ),
        (
            '                    alreadyIssued = AlreadyIssuedResult.Partial(numeroDocumentoFiscal = null, numeroControl = "L001P001-200"),\n',
            '                    alreadyIssued =\n'
            '                        AlreadyIssuedResult.Partial(\n'
            '                            numeroDocumentoFiscal = null,\n'
            '                            numeroControl = "L001P001-200",\n'
            '                        ),\n',
        ),
        (
            '                            rawBody = """{"codigo":"200","resultado":{"numeroDocumento":"00000060","numeroControl":"L001P001-60"}}""",\n',
            '                            rawBody =\n'
            '                                """{"codigo":"200","resultado":{"numeroDocumento":"00000060",""" +\n'
            '                                    """"numeroControl":"L001P001-60"}}""",\n',
        ),
        (
            '                            resultado = VenezuelaHkaEmisionResponse(codigo = "422", mensaje = "RIF invalido", resultado = null),\n',
            '                            resultado =\n'
            '                                VenezuelaHkaEmisionResponse(\n'
            '                                    codigo = "422",\n'
            '                                    mensaje = "RIF invalido",\n'
            '                                    resultado = null,\n'
            '                                ),\n',
        ),
        (
            '            rawBody = """{"codigo":"200","resultado":{"numeroDocumento":"$numDoc","numeroControl":"L001P001-$numDoc"}}""",\n',
            '            rawBody =\n'
            '                """{"codigo":"200","resultado":{"numeroDocumento":"$numDoc",""" +\n'
            '                    """"numeroControl":"L001P001-$numDoc"}}""",\n',
        ),
    ],
}

for path, edits in replacements.items():
    text = path.read_text()
    for old, new in edits:
        count = text.count(old)
        if count < 1:
            raise RuntimeError(f"Expected a match in {path} for {old!r}, found {count}")
        text = text.replace(old, new, 1)
    path.write_text(text)
    print(f"updated {path}")
