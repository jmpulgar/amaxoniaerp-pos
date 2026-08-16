from pathlib import Path

ROOT = Path("amaxoniaerp-backend/src/main/kotlin")


def replace_exact(path: Path, old: str, new: str, expected: int) -> None:
    text = path.read_text()
    count = text.count(old)
    if count != expected:
        raise RuntimeError(f"Expected {expected} matches in {path}, found {count}: {old!r}")
    path.write_text(text.replace(old, new))


def add_constants(path: Path, constants: str) -> None:
    text = path.read_text()
    lines = text.splitlines()
    import_indexes = [i for i, line in enumerate(lines) if line.startswith("import ")]
    if not import_indexes:
        raise RuntimeError(f"No imports found in {path}")
    insert_at = max(import_indexes) + 1
    block = ["", *constants.rstrip().splitlines()]
    lines[insert_at:insert_at] = block
    path.write_text("\n".join(lines) + "\n")


path = ROOT / "com/amaxoniaerp/ConfigLoader.kt"
replace_exact(path, "repeat(4) {", "repeat(DOTENV_SEARCH_PARENT_LEVELS) {", 1)
add_constants(path, "private const val DOTENV_SEARCH_PARENT_LEVELS = 4")

path = ROOT / "com/amaxoniaerp/core/database/DatabaseManager.kt"
replace_exact(path, "maximumPoolSize = 10", "maximumPoolSize = DATABASE_POOL_MAX_SIZE", 1)
replace_exact(path, "idleTimeout = 300000", "idleTimeout = DATABASE_POOL_IDLE_TIMEOUT_MS", 1)
replace_exact(path, "connectionTimeout = 20000", "connectionTimeout = DATABASE_CONNECTION_TIMEOUT_MS", 1)
replace_exact(path, "maxLifetime = 1200000", "maxLifetime = DATABASE_CONNECTION_MAX_LIFETIME_MS", 1)
add_constants(
    path,
    """private const val DATABASE_POOL_MAX_SIZE = 10
private const val DATABASE_POOL_IDLE_TIMEOUT_MS = 300_000L
private const val DATABASE_CONNECTION_TIMEOUT_MS = 20_000L
private const val DATABASE_CONNECTION_MAX_LIFETIME_MS = 1_200_000L""",
)

path = ROOT / "com/amaxoniaerp/core/time/BusinessClock.kt"
replace_exact(path, ".value % 100", ".value % TWO_DIGIT_YEAR_MODULUS", 1)
add_constants(path, "private const val TWO_DIGIT_YEAR_MODULUS = 100")

for rel in (
    "com/amaxoniaerp/features/clients/route/ClientsRoute.kt",
    "com/amaxoniaerp/features/clients/route/ClientTypesRoute.kt",
    "com/amaxoniaerp/features/facturas/route/FacturasRoutes.kt",
):
    path = ROOT / rel
    replace_exact(path, "limitParam ?: 100", "limitParam ?: DEFAULT_PAGE_LIMIT", 1) if "facturas" not in rel else None
    if "facturas" in rel:
        replace_exact(path, 'call.request.queryParameters["limit"]?.toIntOrNull() ?: 100', 'call.request.queryParameters["limit"]?.toIntOrNull() ?: DEFAULT_PAGE_LIMIT', 1)
    replace_exact(path, "limit > 1000", "limit > MAX_PAGE_LIMIT", 1)
    add_constants(path, "private const val DEFAULT_PAGE_LIMIT = 100\nprivate const val MAX_PAGE_LIMIT = 1_000")

path = ROOT / "com/amaxoniaerp/features/geography/route/GeographyRoutes.kt"
replace_exact(path, "limitParam ?: 100", "limitParam ?: DEFAULT_PAGE_LIMIT", 2)
replace_exact(path, "limit > 1000", "limit > MAX_PAGE_LIMIT", 2)
replace_exact(path, '3 -> "direccion_nivel3"', 'ADDRESS_LEVEL_THREE -> "direccion_nivel3"', 1)
add_constants(
    path,
    "private const val DEFAULT_PAGE_LIMIT = 100\nprivate const val MAX_PAGE_LIMIT = 1_000\nprivate const val ADDRESS_LEVEL_THREE = 3",
)

path = ROOT / "com/amaxoniaerp/features/items/route/ItemsRoutes.kt"
replace_exact(path, "limitParam ?: 100", "limitParam ?: DEFAULT_PAGE_LIMIT", 1)
replace_exact(path, "limit > 1000", "limit > MAX_PAGE_LIMIT", 1)
replace_exact(path, ".coerceIn(1, 50) ?: 20", ".coerceIn(MIN_BEST_SELLERS_LIMIT, MAX_BEST_SELLERS_LIMIT) ?: DEFAULT_BEST_SELLERS_LIMIT", 1)
add_constants(
    path,
    """private const val DEFAULT_PAGE_LIMIT = 100
private const val MAX_PAGE_LIMIT = 1_000
private const val MIN_BEST_SELLERS_LIMIT = 1
private const val MAX_BEST_SELLERS_LIMIT = 50
private const val DEFAULT_BEST_SELLERS_LIMIT = 20""",
)

path = ROOT / "com/amaxoniaerp/features/facturas/data/FacturaDetalleRepository.kt"
replace_exact(path, "limit.coerceIn(1, 100)", "limit.coerceIn(1, MAX_BEST_SELLER_LIMIT)", 1)
add_constants(path, "private const val MAX_BEST_SELLER_LIMIT = 100")

path = ROOT / "com/amaxoniaerp/features/creditnotes/route/CreditNoteRoutes.kt"
replace_exact(path, 'call.request.queryParameters["limit"]?.toIntOrNull() ?: 50', 'call.request.queryParameters["limit"]?.toIntOrNull() ?: DEFAULT_CREDIT_NOTE_PAGE_LIMIT', 2)
replace_exact(path, "limit > 200", "limit > MAX_CREDIT_NOTE_PAGE_LIMIT", 2)
add_constants(path, "private const val DEFAULT_CREDIT_NOTE_PAGE_LIMIT = 50\nprivate const val MAX_CREDIT_NOTE_PAGE_LIMIT = 200")

path = ROOT / "com/amaxoniaerp/features/electronicinvoice/application/PanamaInvoiceProcessor.kt"
replace_exact(path, "tipoFact < 3", "tipoFact < PAC_FISCAL_TYPE_THRESHOLD", 1)
replace_exact(path, "context.config.tipoFacturacion < 3", "context.config.tipoFacturacion < PAC_FISCAL_TYPE_THRESHOLD", 1)
replace_exact(path, "context.config.tokenEmpresa.take(8)", "context.config.tokenEmpresa.take(LOG_CREDENTIAL_PREFIX_LENGTH)", 1)
replace_exact(path, "pacResponse.cufe?.take(20)", "pacResponse.cufe?.take(CUFE_LOG_PREFIX_LENGTH)", 1)
replace_exact(path, "item.descripcion.take(80)", "item.descripcion.take(ITEM_DESCRIPTION_LOG_LENGTH)", 1)
add_constants(
    path,
    """private const val PAC_FISCAL_TYPE_THRESHOLD = 3
private const val LOG_CREDENTIAL_PREFIX_LENGTH = 8
private const val CUFE_LOG_PREFIX_LENGTH = 20
private const val ITEM_DESCRIPTION_LOG_LENGTH = 80""",
)

path = ROOT / "com/amaxoniaerp/features/clients/data/ClientsRepository.kt"
replace_exact(path, "data.take(3)", "data.take(CLIENT_PHOTO_LOG_SAMPLE_SIZE)", 1)
replace_exact(path, "photoFilename?.take(80)", "photoFilename?.take(PHOTO_FILENAME_LOG_LENGTH)", 1)
replace_exact(path, "codCliente].take(9)", "codCliente].take(CLIENT_CODE_LENGTH)", 1)
replace_exact(path, ".padStart(9, '0')", ".padStart(CLIENT_CODE_LENGTH, '0')", 1)
add_constants(
    path,
    """private const val CLIENT_PHOTO_LOG_SAMPLE_SIZE = 3
private const val PHOTO_FILENAME_LOG_LENGTH = 80
private const val CLIENT_CODE_LENGTH = 9""",
)

print("named low-risk backend numeric limits without changing values")
