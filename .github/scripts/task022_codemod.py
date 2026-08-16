from pathlib import Path

ROOT = Path("amaxoniaerp-backend/src/test/kotlin/com/amaxoniaerp")

replacements = {
    ROOT / "features/creditnotes/CreditNoteRepositoryEligibilityTest.kt": [
        (
            '                url = "jdbc:h2:mem:credit_note_eligibility_${UUID.randomUUID().toString().replace("-", "")};MODE=MySQL;DB_CLOSE_DELAY=-1",\n',
            '                url =\n'
            '                    "jdbc:h2:mem:credit_note_eligibility_${UUID.randomUUID().toString().replace("-", "")};" +\n'
            '                        "MODE=MySQL;DB_CLOSE_DELAY=-1",\n',
        ),
    ],
    ROOT / "features/creditnotes/CreditNotePanamaStagedFlowTest.kt": [
        (
            '                url = "jdbc:h2:mem:credit_note_staged_${UUID.randomUUID().toString().replace("-", "")};MODE=MySQL;DB_CLOSE_DELAY=-1",\n',
            '                url =\n'
            '                    "jdbc:h2:mem:credit_note_staged_${UUID.randomUUID().toString().replace("-", "")};" +\n'
            '                        "MODE=MySQL;DB_CLOSE_DELAY=-1",\n',
        ),
    ],
    ROOT / "features/sales/data/ProcessSaleCreditTest.kt": [
        (
            '        assertEquals(100.0, paymentDetail()[SalesFacturaDetalleFormaPagoTablePA.totalizarSaldoPendiente].toDouble(), 0.0)\n',
            '        assertEquals(\n'
            '            100.0,\n'
            '            paymentDetail()[SalesFacturaDetalleFormaPagoTablePA.totalizarSaldoPendiente].toDouble(),\n'
            '            0.0,\n'
            '        )\n',
        ),
    ],
    ROOT / "features/mesas/PedidoMesaRepositoryTest.kt": [
        (
            '        database = Database.connect("jdbc:h2:mem:pedidos_${System.nanoTime()};MODE=MySQL;DB_CLOSE_DELAY=-1", "org.h2.Driver")\n',
            '        database =\n'
            '            Database.connect(\n'
            '                "jdbc:h2:mem:pedidos_${System.nanoTime()};MODE=MySQL;DB_CLOSE_DELAY=-1",\n'
            '                "org.h2.Driver",\n'
            '            )\n',
        ),
        (
            '            listOf(EstadoPedidoMesa.EN_PREPARACION, EstadoPedidoMesa.LISTA, EstadoPedidoMesa.ENTREGADA).forEach { destino ->\n',
            '            listOf(\n'
            '                EstadoPedidoMesa.EN_PREPARACION,\n'
            '                EstadoPedidoMesa.LISTA,\n'
            '                EstadoPedidoMesa.ENTREGADA,\n'
            '            ).forEach { destino ->\n',
        ),
    ],
    ROOT / "features/mesas/SesionMesaRepositoryTest.kt": [
        (
            '        database = Database.connect("jdbc:h2:mem:sesion_${System.nanoTime()};MODE=MySQL;DB_CLOSE_DELAY=-1", "org.h2.Driver")\n',
            '        database =\n'
            '            Database.connect(\n'
            '                "jdbc:h2:mem:sesion_${System.nanoTime()};MODE=MySQL;DB_CLOSE_DELAY=-1",\n'
            '                "org.h2.Driver",\n'
            '            )\n',
        ),
        (
            '    ): SesionMesaResult = repository.abrir(database, abrirScope(cajaId = cajaId, mesaId = mesaId, cantidadPersonas = cantidadPersonas))\n',
            '    ): SesionMesaResult =\n'
            '        repository.abrir(\n'
            '            database,\n'
            '            abrirScope(cajaId = cajaId, mesaId = mesaId, cantidadPersonas = cantidadPersonas),\n'
            '        )\n',
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
