from pathlib import Path

ROOT = Path("amaxoniaerp-backend/src/test/kotlin/com/amaxoniaerp")

replacements = {
    ROOT / "features/electronicinvoice/pac/thefactory/venezuela/VenezuelaHkaRestClientTest.kt": [
        (
            'import kotlin.test.assertTrue\n\n/**\n',
            'import kotlin.test.assertTrue\n\n'
            'private typealias MockRequestHandler = suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData\n\n'
            '/**\n',
        ),
        (
            '    private fun cliente(handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData): VenezuelaHkaRestClient =\n',
            '    private fun cliente(handler: MockRequestHandler): VenezuelaHkaRestClient =\n',
        ),
    ],
    ROOT / "features/facturas/data/FacturasPrintPayloadDiscountTest.kt": [
        (
            '                "CREATE TABLE IF NOT EXISTS sucursal (id INT, sucursal VARCHAR(100), descripcion VARCHAR(255), codigo_sucursal_emisor VARCHAR(40))",\n',
            '                "CREATE TABLE IF NOT EXISTS sucursal (id INT, sucursal VARCHAR(100), " +\n'
            '                    "descripcion VARCHAR(255), codigo_sucursal_emisor VARCHAR(40))",\n',
        ),
        (
            '                "CREATE TABLE IF NOT EXISTS cliente_sucursal (sucursal_id INT, direccion VARCHAR(255), nombre_sucursal VARCHAR(255))",\n',
            '                "CREATE TABLE IF NOT EXISTS cliente_sucursal (sucursal_id INT, " +\n'
            '                    "direccion VARCHAR(255), nombre_sucursal VARCHAR(255))",\n',
        ),
        (
            '                "CREATE TABLE IF NOT EXISTS caja_nueva_detalle (caja_detalle_id INT, caja_id VARCHAR(36), id_forma_pago INT, monto DECIMAL(20,2))",\n',
            '                "CREATE TABLE IF NOT EXISTS caja_nueva_detalle (caja_detalle_id INT, " +\n'
            '                    "caja_id VARCHAR(36), id_forma_pago INT, monto DECIMAL(20,2))",\n',
        ),
        (
            '            exec("CREATE TABLE IF NOT EXISTS factura_detalle_formapago (id_factura VARCHAR(36), totalizar_cambio DECIMAL(20,2))")\n',
            '            exec(\n'
            '                "CREATE TABLE IF NOT EXISTS factura_detalle_formapago (id_factura VARCHAR(36), " +\n'
            '                    "totalizar_cambio DECIMAL(20,2))",\n'
            '            )\n',
        ),
    ],
    ROOT / "features/sales/application/ProcessSaleUseCaseSelectionTest.kt": [
        (
            '        Database.connect("jdbc:h2:mem:process_sale_sel_${System.nanoTime()};MODE=MySQL;DB_CLOSE_DELAY=-1", "org.h2.Driver")\n',
            '        Database.connect(\n'
            '            "jdbc:h2:mem:process_sale_sel_${System.nanoTime()};MODE=MySQL;DB_CLOSE_DELAY=-1",\n'
            '            "org.h2.Driver",\n'
            '        )\n',
        ),
        (
            '            val digital = RecordingStrategy("VE", result = ElectronicInvoiceResult.Success(numeroDocumentoFiscal = "DOC-LEAK"))\n',
            '            val digital =\n'
            '                RecordingStrategy(\n'
            '                    "VE",\n'
            '                    result = ElectronicInvoiceResult.Success(numeroDocumentoFiscal = "DOC-LEAK"),\n'
            '                )\n',
        ),
        (
            '            val digital = RecordingStrategy("VE", result = ElectronicInvoiceResult.Success(numeroDocumentoFiscal = "DOC-EMIT"))\n',
            '            val digital =\n'
            '                RecordingStrategy(\n'
            '                    "VE",\n'
            '                    result = ElectronicInvoiceResult.Success(numeroDocumentoFiscal = "DOC-EMIT"),\n'
            '                )\n',
        ),
        (
            '                    result = ElectronicInvoiceResult.Success(numeroDocumentoFiscal = "00001234", numeroControlThka = "001-00001"),\n',
            '                    result =\n'
            '                        ElectronicInvoiceResult.Success(\n'
            '                            numeroDocumentoFiscal = "00001234",\n'
            '                            numeroControlThka = "001-00001",\n'
            '                        ),\n',
        ),
        (
            '                    result = ElectronicInvoiceResult.Success(numeroDocumentoFiscal = "00001234", numeroControlThka = "001-00001"),\n',
            '                    result =\n'
            '                        ElectronicInvoiceResult.Success(\n'
            '                            numeroDocumentoFiscal = "00001234",\n'
            '                            numeroControlThka = "001-00001",\n'
            '                        ),\n',
        ),
        (
            '                    result = ElectronicInvoiceResult.Success(numeroDocumentoFiscal = "DOC-ONLY", numeroControlThka = null),\n',
            '                    result =\n'
            '                        ElectronicInvoiceResult.Success(\n'
            '                            numeroDocumentoFiscal = "DOC-ONLY",\n'
            '                            numeroControlThka = null,\n'
            '                        ),\n',
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
