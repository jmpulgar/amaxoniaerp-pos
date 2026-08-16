from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]


def replace_exact(path: Path, old: str, new: str, expected: int = 1) -> None:
    text = path.read_text(encoding="utf-8")
    actual = text.count(old)
    if actual != expected:
        raise RuntimeError(f"{path}: expected {expected} occurrence(s), found {actual}: {old!r}")
    path.write_text(text.replace(old, new), encoding="utf-8")


ve_repo = ROOT / "amaxoniaerp-backend/src/main/kotlin/com/amaxoniaerp/features/electronicinvoice/data/VenezuelaElectronicInvoiceRepository.kt"
replace_exact(ve_repo, "loadFactura(database, invoiceId)", "loadFactura(invoiceId)")
replace_exact(ve_repo, "loadConfig(database)", "loadConfig()")
replace_exact(
    ve_repo,
    "loadComprador(database, factura.idClienteComprador, factura.facturaData)",
    "loadComprador(factura.idClienteComprador, factura.facturaData)",
)
replace_exact(ve_repo, "loadFormasPago(database, factura.idCaja, invoiceId)", "loadFormasPago(factura.idCaja, invoiceId)")
replace_exact(
    ve_repo,
    "loadCaja(database, factura.idCaja, factura.idSucursal, config)",
    "loadCaja(factura.idCaja, factura.idSucursal, config)",
)
replace_exact(
    ve_repo,
    """    private fun loadFactura(\n        database: Database,\n        invoiceId: String,\n""",
    """    private fun loadFactura(\n        invoiceId: String,\n""",
)
replace_exact(
    ve_repo,
    """    private fun loadFactura(\n        invoiceId: String,\n    ): FacturaCargada {\n""",
    "    private fun loadFactura(invoiceId: String): FacturaCargada {\n",
)
replace_exact(
    ve_repo,
    "    private fun loadConfig(database: Database): VEConfigData {",
    "    private fun loadConfig(): VEConfigData {",
)
replace_exact(
    ve_repo,
    """    private fun loadComprador(\n        database: Database,\n        idClienteComprador: String?,\n""",
    """    private fun loadComprador(\n        idClienteComprador: String?,\n""",
)
replace_exact(
    ve_repo,
    """    private fun loadFormasPago(\n        database: Database,\n        cajaId: String,\n""",
    """    private fun loadFormasPago(\n        cajaId: String,\n""",
)
replace_exact(
    ve_repo,
    """    private fun loadCaja(\n        database: Database,\n        cajaId: String,\n""",
    """    private fun loadCaja(\n        cajaId: String,\n""",
)

facturas = ROOT / "amaxoniaerp-backend/src/main/kotlin/com/amaxoniaerp/features/facturas/data/FacturasRepository.kt"
replace_exact(facturas, "mapRowToFacturaSummary(row, tabla, countryCode)", "mapRowToFacturaSummary(row, tabla)")
replace_exact(
    facturas,
    """    private fun mapRowToFacturaSummary(\n        row: ResultRow,\n        tabla: BaseFacturasTable,\n        countryCode: String,\n""",
    """    private fun mapRowToFacturaSummary(\n        row: ResultRow,\n        tabla: BaseFacturasTable,\n""",
)

items = ROOT / "amaxoniaerp-backend/src/main/kotlin/com/amaxoniaerp/features/items/data/ItemsRepository.kt"
replace_exact(
    items,
    """    suspend fun listDepartments(\n        database: Database,\n        countryCode: String,\n""",
    """    suspend fun listDepartments(\n        database: Database,\n""",
)
replace_exact(
    items,
    """    suspend fun listDepartments(\n        database: Database,\n    ): List<Pair<Int, String>> =\n""",
    "    suspend fun listDepartments(database: Database): List<Pair<Int, String>> =\n",
)
items_routes = ROOT / "amaxoniaerp-backend/src/main/kotlin/com/amaxoniaerp/features/items/route/ItemsRoutes.kt"
replace_exact(
    items_routes,
    "itemsRepository.listDepartments(database = companyDb, countryCode = countryCode)",
    "itemsRepository.listDepartments(database = companyDb)",
)

cuenta = ROOT / "amaxoniaerp-backend/src/main/kotlin/com/amaxoniaerp/features/mesas/data/CuentaMesaRepository.kt"
replace_exact(
    cuenta,
    """class CuentaMesaRepository(\n    private val sesiones: SesionMesaRepository,\n    private val pedidos: PedidoMesaRepository,\n) {\n""",
    "class CuentaMesaRepository {\n",
)
routing = ROOT / "amaxoniaerp-backend/src/main/kotlin/com/amaxoniaerp/Routing.kt"
replace_exact(
    routing,
    "val cuentaMesaRepository = CuentaMesaRepository(sesionMesaRepository, pedidoMesaRepository)",
    "val cuentaMesaRepository = CuentaMesaRepository()",
)
cuenta_test = ROOT / "amaxoniaerp-backend/src/test/kotlin/com/amaxoniaerp/features/mesas/CuentaMesaRepositoryTest.kt"
replace_exact(
    cuenta_test,
    "private val cuentaRepository = CuentaMesaRepository(sesionRepository, pedidoRepository)",
    "private val cuentaRepository = CuentaMesaRepository()",
)
