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
    lines[insert_at:insert_at] = ["", *constants.rstrip().splitlines()]
    path.write_text("\n".join(lines) + "\n")


# Credit-note repository: name persisted field widths, numeric scales and fixed status/correlative values.
path = ROOT / "com/amaxoniaerp/features/creditnotes/data/CreditNoteRepository.kt"
replace_exact(path, "CreditNoteFacturaTable.codEstatus neq 3", "CreditNoteFacturaTable.codEstatus neq ANNULLED_INVOICE_STATUS", 1)
replace_exact(path, "it[codEstatus] = 3", "it[codEstatus] = ANNULLED_INVOICE_STATUS", 3)
replace_exact(path, "setScale(3, RoundingMode.HALF_UP)", "setScale(QUANTITY_SCALE, RoundingMode.HALF_UP)", 8)
replace_exact(path, "coerceAtLeastZero(3)", "coerceAtLeastZero(QUANTITY_SCALE)", 1)
replace_exact(path, "row[CreditNoteFacturaDetalleTable.itemTotalSinIva], quantityOriginal, 6", "row[CreditNoteFacturaDetalleTable.itemTotalSinIva], quantityOriginal, UNIT_CALCULATION_SCALE", 1)
replace_exact(path, "row[CreditNoteFacturaDetalleTable.itemTotalConIva], quantityOriginal, 6", "row[CreditNoteFacturaDetalleTable.itemTotalConIva], quantityOriginal, UNIT_CALCULATION_SCALE", 1)
replace_exact(path, "row[CreditNoteFacturaDetalleTable.itemMontoDescuento], quantityOriginal, 6", "row[CreditNoteFacturaDetalleTable.itemMontoDescuento], quantityOriginal, UNIT_CALCULATION_SCALE", 1)
replace_exact(path, "sourceLine.totalSinIvaOriginal, sourceLine.quantityOriginal, 6", "sourceLine.totalSinIvaOriginal, sourceLine.quantityOriginal, UNIT_CALCULATION_SCALE", 1)
replace_exact(path, "sourceLine.totalConIvaOriginal, sourceLine.quantityOriginal, 6", "sourceLine.totalConIvaOriginal, sourceLine.quantityOriginal, UNIT_CALCULATION_SCALE", 1)
replace_exact(path, "12, RoundingMode.HALF_UP", "FINANCIAL_DIVISION_SCALE, RoundingMode.HALF_UP", 3)
replace_exact(path, ".padStart(5, '0')", ".padStart(CORRELATIVE_CODE_LENGTH, '0')", 3)
replace_exact(path, "next > 9_999_999_999L", "next > MAX_FISCAL_DOCUMENT_NUMBER", 1)
replace_exact(path, ".padStart(10, '0')", ".padStart(FISCAL_DOCUMENT_LENGTH, '0')", 2)
replace_exact(path, "normalized.length > 10", "normalized.length > FISCAL_DOCUMENT_LENGTH", 1)
replace_exact(path, "tipoMovimientoAlmacen] = 14", "tipoMovimientoAlmacen] = CREDIT_NOTE_KARDEX_MOVEMENT_TYPE", 1)
replace_exact(path, "invoice.facturarARuc.take(10)", "invoice.facturarARuc.take(KARDEX_RECIPIENT_CODE_LENGTH)", 1)
replace_exact(path, "invoice.facturarA.take(30)", "invoice.facturarA.take(KARDEX_RECIPIENT_NAME_LENGTH)", 1)
replace_exact(path, "setScale(4", "setScale(INVENTORY_QUANTITY_SCALE", 5)
add_constants(
    path,
    """private const val ANNULLED_INVOICE_STATUS = 3
private const val QUANTITY_SCALE = 3
private const val UNIT_CALCULATION_SCALE = 6
private const val FINANCIAL_DIVISION_SCALE = 12
private const val CORRELATIVE_CODE_LENGTH = 5
private const val MAX_FISCAL_DOCUMENT_NUMBER = 9_999_999_999L
private const val FISCAL_DOCUMENT_LENGTH = 10
private const val CREDIT_NOTE_KARDEX_MOVEMENT_TYPE = 14
private const val KARDEX_RECIPIENT_CODE_LENGTH = 10
private const val KARDEX_RECIPIENT_NAME_LENGTH = 30
private const val INVENTORY_QUANTITY_SCALE = 4""",
)

# Sales repository: name storage widths, scales and bounded retry/padding values only.
path = ROOT / "com/amaxoniaerp/features/sales/data/ProcessSaleTransactionalRepository.kt"
replace_exact(path, ".take(10)", ".take(SHORT_CODE_LENGTH)", 5)
replace_exact(path, "setScale(8, RoundingMode.HALF_UP)", "setScale(EXCHANGE_RATE_SCALE, RoundingMode.HALF_UP)", 1)
replace_exact(path, "request.factura.codCliente.take(9)", "request.factura.codCliente.take(CLIENT_CODE_LENGTH)", 1)
replace_exact(path, "repeat(10) {", "repeat(CORRELATIVE_RETRY_ATTEMPTS) {", 2)
replace_exact(path, ".padStart(5, '0')", ".padStart(INVOICE_SEQUENCE_LENGTH, '0')", 1)
replace_exact(path, "?: 3", "?: DEFAULT_TERM_PAYMENT_ID", 1)
replace_exact(path, "request.factura.usuarioCreacion.take(32)", "request.factura.usuarioCreacion.take(INVOICE_USER_LENGTH)", 1)
replace_exact(path, "toScaledBigDecimal(3)", "toScaledBigDecimal(QUANTITY_SCALE)", 2)
replace_exact(path, "BigDecimal.valueOf(100.0)", "BigDecimal.valueOf(PERCENT_BASE)", 1)
replace_exact(path, "item.itemUnidadEmpaque.take(15)", "item.itemUnidadEmpaque.take(PACKAGING_UNIT_LENGTH)", 1)
replace_exact(path, "item.promocionCodigo.take(15)", "item.promocionCodigo.take(PROMOTION_CODE_LENGTH)", 1)
replace_exact(path, ".take(36)", ".take(PROMOTION_IDENTIFIER_LENGTH)", 3)
replace_exact(path, "item.promocionTipo.take(20)", "item.promocionTipo.take(PROMOTION_TYPE_LENGTH)", 1)
replace_exact(path, "item.promocionNombre.take(200)", "item.promocionNombre.take(PROMOTION_NAME_LENGTH)", 1)
replace_exact(path, "request.factura.usuarioCreacion.take(60)", "request.factura.usuarioCreacion.take(PAYMENT_USER_LENGTH)", 1)
replace_exact(path, "BigDecimal.ZERO.setScale(4)", "BigDecimal.ZERO.setScale(INVENTORY_QUANTITY_SCALE)", 1)
replace_exact(path, ".value % 100", ".value % TWO_DIGIT_YEAR_MODULUS", 1)
replace_exact(path, "request.factura.usuarioCreacion.take(20)", "request.factura.usuarioCreacion.take(STANDARD_USER_LENGTH)", 4)
add_constants(
    path,
    """private const val SHORT_CODE_LENGTH = 10
private const val EXCHANGE_RATE_SCALE = 8
private const val CLIENT_CODE_LENGTH = 9
private const val CORRELATIVE_RETRY_ATTEMPTS = 10
private const val INVOICE_SEQUENCE_LENGTH = 5
private const val DEFAULT_TERM_PAYMENT_ID = 3
private const val INVOICE_USER_LENGTH = 32
private const val QUANTITY_SCALE = 3
private const val PERCENT_BASE = 100.0
private const val PACKAGING_UNIT_LENGTH = 15
private const val PROMOTION_CODE_LENGTH = 15
private const val PROMOTION_IDENTIFIER_LENGTH = 36
private const val PROMOTION_TYPE_LENGTH = 20
private const val PROMOTION_NAME_LENGTH = 200
private const val PAYMENT_USER_LENGTH = 60
private const val INVENTORY_QUANTITY_SCALE = 4
private const val TWO_DIGIT_YEAR_MODULUS = 100
private const val STANDARD_USER_LENGTH = 20""",
)

path = ROOT / "com/amaxoniaerp/features/caja/data/CajaRepository.kt"
replace_exact(path, "FacturaDevolucionTable.idFormaPago] ?: 30", "FacturaDevolucionTable.idFormaPago] ?: RETURN_PAYMENT_FORM_FALLBACK", 1)
replace_exact(path, "facturaTable.codEstatus eq 3", "facturaTable.codEstatus eq ANNULLED_INVOICE_STATUS", 1)
replace_exact(path, "row[facturaTable.codEstatus] ?: 0) != 3", "row[facturaTable.codEstatus] ?: 0) != ANNULLED_INVOICE_STATUS", 1)
replace_exact(path, ".padStart(6, '0')", ".padStart(CASH_SEQUENCE_LENGTH, '0')", 1)
add_constants(path, "private const val RETURN_PAYMENT_FORM_FALLBACK = 30\nprivate const val ANNULLED_INVOICE_STATUS = 3\nprivate const val CASH_SEQUENCE_LENGTH = 6")

path = ROOT / "com/amaxoniaerp/features/electronicinvoice/data/ElectronicInvoiceRepository.kt"
replace_exact(path, "config.tokenEmpresa.take(\n                    8,\n                )", "config.tokenEmpresa.take(\n                    LOG_TOKEN_PREFIX_LENGTH,\n                )", 1)
replace_exact(path, ".take(19)", ".take(SQL_DATETIME_TEXT_LENGTH)", 2)
add_constants(path, "private const val LOG_TOKEN_PREFIX_LENGTH = 8\nprivate const val SQL_DATETIME_TEXT_LENGTH = 19")

path = ROOT / "com/amaxoniaerp/features/mesas/data/CuentaMesaRepository.kt"
replace_exact(path, "cantidad.divide(row[PedidoMesaTable.itemCantidad], 6, RoundingMode.HALF_EVEN)", "cantidad.divide(row[PedidoMesaTable.itemCantidad], UNIT_CALCULATION_SCALE, RoundingMode.HALF_EVEN)", 1)
replace_exact(path, "BigDecimal.valueOf(value).setScale(3, RoundingMode.HALF_EVEN)", "BigDecimal.valueOf(value).setScale(QUANTITY_SCALE, RoundingMode.HALF_EVEN)", 1)
add_constants(path, "private const val UNIT_CALCULATION_SCALE = 6\nprivate const val QUANTITY_SCALE = 3")

path = ROOT / "com/amaxoniaerp/features/items/data/ItemsRepository.kt"
replace_exact(path, "storedTaxRate / 100.0", "storedTaxRate / PERCENT_BASE", 1)
add_constants(path, "private const val PERCENT_BASE = 100.0")

path = ROOT / "com/amaxoniaerp/features/electronicinvoice/domain/VenezuelaInvoiceStrategy.kt"
replace_exact(path, "emission.httpStatus >= 500", "emission.httpStatus >= HTTP_SERVER_ERROR_MIN", 1)
add_constants(path, "private const val HTTP_SERVER_ERROR_MIN = 500")

path = ROOT / "com/amaxoniaerp/features/electronicinvoice/data/VenezuelaElectronicInvoiceRepository.kt"
replace_exact(path, "VECorrelativoReservado(0, 8)", "VECorrelativoReservado(0, PLACEHOLDER_FISCAL_NUMBER_LENGTH)", 1)
add_constants(path, "private const val PLACEHOLDER_FISCAL_NUMBER_LENGTH = 8")

path = ROOT / "com/amaxoniaerp/Routing.kt"
replace_exact(path, "requestTimeout = 30_000", "requestTimeout = HTTP_REQUEST_TIMEOUT_MS", 1)
add_constants(path, "private const val HTTP_REQUEST_TIMEOUT_MS = 30_000L")

path = ROOT / "com/amaxoniaerp/features/pos/PosRouting.kt"
replace_exact(path, "listOf(1, 3)", "listOf(1, SECOND_DEFAULT_REGISTRATION_TYPE)", 1)
add_constants(path, "private const val SECOND_DEFAULT_REGISTRATION_TYPE = 3")

path = ROOT / "com/amaxoniaerp/features/electronicinvoice/pac/thefactory/TheFactoryHkaRestClient.kt"
replace_exact(path, "cufe.take(20)", "cufe.take(CUFE_LOG_PREFIX_LENGTH)", 1)
add_constants(path, "private const val CUFE_LOG_PREFIX_LENGTH = 20")

path = ROOT / "com/amaxoniaerp/features/electronicinvoice/pac/thefactory/venezuela/VenezuelaHkaRestClient.kt"
replace_exact(path, "credentials.usuario.take(8)", "credentials.usuario.take(LOG_CREDENTIAL_PREFIX_LENGTH)", 1)
replace_exact(path, "status in 200..299", "status in HTTP_SUCCESS_MIN..HTTP_SUCCESS_MAX", 1)
add_constants(path, "private const val LOG_CREDENTIAL_PREFIX_LENGTH = 8\nprivate const val HTTP_SUCCESS_MIN = 200\nprivate const val HTTP_SUCCESS_MAX = 299")

path = ROOT / "com/amaxoniaerp/features/electronicinvoice/pac/thefactory/venezuela/VenezuelaHkaClient.kt"
replace_exact(path, "httpStatus in 200..299", "httpStatus in HTTP_SUCCESS_MIN..HTTP_SUCCESS_MAX", 1)
add_constants(path, "private const val HTTP_SUCCESS_MIN = 200\nprivate const val HTTP_SUCCESS_MAX = 299")

print("named remaining non-payload MagicNumber values without altering numeric behavior")
