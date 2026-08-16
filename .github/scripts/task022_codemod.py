from pathlib import Path

ROOT = Path("amaxoniaerp-backend/src/test/kotlin/com/amaxoniaerp")

replacements = {
    ROOT / "features/mesas/CuentaMesaRepositoryTest.kt": [
        (
            '        database = Database.connect("jdbc:h2:mem:cuenta_${System.nanoTime()};MODE=MySQL;DB_CLOSE_DELAY=-1", "org.h2.Driver")\n',
            '        database =\n'
            '            Database.connect(\n'
            '                "jdbc:h2:mem:cuenta_${System.nanoTime()};MODE=MySQL;DB_CLOSE_DELAY=-1",\n'
            '                "org.h2.Driver",\n'
            '            )\n',
        ),
    ],
    ROOT / "features/creditnotes/data/CreditNoteFinancialsTest.kt": [
        (
            '                url = "jdbc:h2:mem:credit_note_financials_${UUID.randomUUID().toString().replace("-", "")};MODE=MySQL;DB_CLOSE_DELAY=-1",\n',
            '                url =\n'
            '                    "jdbc:h2:mem:credit_note_financials_${UUID.randomUUID().toString().replace("-", "")};" +\n'
            '                        "MODE=MySQL;DB_CLOSE_DELAY=-1",\n',
        ),
        (
            '                it[itemPrecioSinIva] = line.base.toBigDecimal().divide(line.quantity.toBigDecimal(), 2, RoundingMode.HALF_UP)\n',
            '                it[itemPrecioSinIva] =\n'
            '                    line.base.toBigDecimal().divide(\n'
            '                        line.quantity.toBigDecimal(),\n'
            '                        2,\n'
            '                        RoundingMode.HALF_UP,\n'
            '                    )\n',
        ),
    ],
    ROOT / "features/creditnotes/data/CreditNoteCajaBehaviorTest.kt": [
        (
            '            assertEquals(BigDecimal("-0.01"), reversal[SalesCajaNuevaDetalleTableFactory.forCountry("PA").montoOriginal])\n',
            '            assertEquals(\n'
            '                BigDecimal("-0.01"),\n'
            '                reversal[SalesCajaNuevaDetalleTableFactory.forCountry("PA").montoOriginal],\n'
            '            )\n',
        ),
        (
            '                url = "jdbc:h2:mem:credit_note_caja_${UUID.randomUUID().toString().replace("-", "")};MODE=MySQL;DB_CLOSE_DELAY=-1",\n',
            '                url =\n'
            '                    "jdbc:h2:mem:credit_note_caja_${UUID.randomUUID().toString().replace("-", "")};" +\n'
            '                        "MODE=MySQL;DB_CLOSE_DELAY=-1",\n',
        ),
    ],
    ROOT / "features/electronicinvoice/pac/thefactory/venezuela/VenezuelaHkaRestClientTest.kt": [
        (
            '    private fun cliente(handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData): VenezuelaHkaRestClient =\n',
            '    private fun cliente(\n'
            '        handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,\n'
            '    ): VenezuelaHkaRestClient =\n',
        ),
        (
            '                        content = """{"codigo":"422","mensaje":"Serie no configurada","resultado":null,"validaciones":["serie"]}""",\n',
            '                        content =\n'
            '                            "{\\"codigo\\":\\"422\\",\\"mensaje\\":\\"Serie no configurada\\",\\"resultado\\":null," +\n'
            '                                "\\"validaciones\\":[\\"serie\\"]}",\n',
        ),
        (
            '                            listaFormaPago = listOf(VenezuelaHkaFormaPago(formaPagoFact = "01", montoPagado = "116.00")),\n',
            '                            listaFormaPago =\n'
            '                                listOf(\n'
            '                                    VenezuelaHkaFormaPago(formaPagoFact = "01", montoPagado = "116.00"),\n'
            '                                ),\n',
        ),
    ],
    ROOT / "features/electronicinvoice/data/VenezuelaElectronicInvoiceRepositoryTest.kt": [
        (
            '            seedFactura(database, invoiceId = "inv-2", numeroDocumentoFiscal = "00000100", numeroControl = "L001P001-100")\n',
            '            seedFactura(\n'
            '                database,\n'
            '                invoiceId = "inv-2",\n'
            '                numeroDocumentoFiscal = "00000100",\n'
            '                numeroControl = "L001P001-100",\n'
            '            )\n',
        ),
        (
            '            seedFactura(database, invoiceId = "inv-or-ctrl", numeroDocumentoFiscal = null, numeroControl = "L001P001-200")\n',
            '            seedFactura(\n'
            '                database,\n'
            '                invoiceId = "inv-or-ctrl",\n'
            '                numeroDocumentoFiscal = null,\n'
            '                numeroControl = "L001P001-200",\n'
            '            )\n',
        ),
    ],
}

for path, edits in replacements.items():
    text = path.read_text()
    for old, new in edits:
        count = text.count(old)
        if count != 1:
            raise RuntimeError(f"Expected exactly one match in {path} for {old!r}, found {count}")
        text = text.replace(old, new)
    path.write_text(text)
    print(f"updated {path}")
