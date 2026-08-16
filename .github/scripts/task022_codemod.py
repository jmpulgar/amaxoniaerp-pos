from pathlib import Path

ROOT = Path("amaxoniaerp-backend/src/test/kotlin/com/amaxoniaerp")

replacements = {
    ROOT / "features/electronicinvoice/pac/thefactory/TheFactoryHkaCreditNotePayloadBuilderTest.kt": [
        (
            '                """"listaDocsFiscalReferenciados":[{"fechaEmisionDocFiscalReferenciado":"2026-08-01T00:00:00-05:00",""" +\n',
            '                """"listaDocsFiscalReferenciados":[{""" +\n'
            '                """"fechaEmisionDocFiscalReferenciado":"2026-08-01T00:00:00-05:00",""" +\n',
        ),
    ],
    ROOT / "features/electronicinvoice/pac/thefactory/venezuela/VenezuelaHkaPayloadBuilderTest.kt": [
        (
            '    private fun VenezuelaHkaFormaPago.esDivisaMoneda(): Boolean = descripcion?.contains("DIVISA", ignoreCase = true) == true\n',
            '    private fun VenezuelaHkaFormaPago.esDivisaMoneda(): Boolean {\n'
            '        val paymentDescription = descripcion\n'
            '        return paymentDescription?.contains("DIVISA", ignoreCase = true) == true\n'
            '    }\n',
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
