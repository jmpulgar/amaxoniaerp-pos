from pathlib import Path

ROOT = Path("amaxoniaerp-backend/src/main/kotlin/com/amaxoniaerp/features/electronicinvoice/pac/thefactory")


def replace_exact(path: Path, old: str, new: str, expected: int) -> None:
    text = path.read_text()
    count = text.count(old)
    if count != expected:
        raise RuntimeError(f"Expected {expected} matches in {path}, found {count}: {old!r}")
    path.write_text(text.replace(old, new))


invoice = ROOT / "TheFactoryHkaPayloadBuilder.kt"
replace_exact(invoice, "        private const val MIN_FORMA_PAGO_DESC_LENGTH = 10\n", "        private const val MIN_FORMA_PAGO_DESC_LENGTH = 10\n        private const val MIN_IDENTIFICATION_LENGTH = 5\n        private const val EMAIL_MIN_LENGTH = 7\n        private const val ADDRESS_MIN_LENGTH = 4\n        private const val DISCOUNT_SCALE = 4\n        private const val QUANTITY_SCALE = 3\n        private const val ITBMS_RATE_7 = 7.0\n        private const val ITBMS_RATE_10 = 10.0\n        private const val ITBMS_RATE_15 = 15.0\n        private const val ISO_DATE_LENGTH = 10\n", 1)
replace_exact(invoice, "cliente.identificacion.length < 5", "cliente.identificacion.length < MIN_IDENTIFICATION_LENGTH", 1)
replace_exact(invoice, "validated.padStart(7, '0')", "validated.padStart(EMAIL_MIN_LENGTH, '0')", 1)
replace_exact(invoice, ").padStart(4, '-')", ").padStart(ADDRESS_MIN_LENGTH, '-')", 1)
replace_exact(invoice, ").formatDecimals(4)", ").formatDecimals(DISCOUNT_SCALE)", 1)
replace_exact(invoice, "det.cantidad.formatDecimals(3)", "det.cantidad.formatDecimals(QUANTITY_SCALE)", 1)
replace_exact(invoice, "piva == 7.0 || isApprox(piva, 7.0)", "piva == ITBMS_RATE_7 || isApprox(piva, ITBMS_RATE_7)", 1)
replace_exact(invoice, "piva == 10.0 || isApprox(piva, 10.0)", "piva == ITBMS_RATE_10 || isApprox(piva, ITBMS_RATE_10)", 1)
replace_exact(invoice, "piva == 15.0 || isApprox(piva, 15.0)", "piva == ITBMS_RATE_15 || isApprox(piva, ITBMS_RATE_15)", 1)
replace_exact(invoice, "fecha.trim().take(10)", "fecha.trim().take(ISO_DATE_LENGTH)", 1)

credit = ROOT / "TheFactoryHkaCreditNotePayloadBuilder.kt"
replace_exact(credit, ") {\n    fun build", ") {\n    companion object {\n        private const val CUFE_LENGTH = 66\n        private const val FISCAL_NUMBER_LENGTH = 10\n        private const val BILLING_POINT_LENGTH = 3\n    }\n\n    fun build", 1)
replace_exact(credit, "context.originalInvoiceCufe.length == 66", "context.originalInvoiceCufe.length == CUFE_LENGTH", 1)
replace_exact(credit, "normalized.length <= 10", "normalized.length <= FISCAL_NUMBER_LENGTH", 1)
replace_exact(credit, "normalized.padStart(10, '0')", "normalized.padStart(FISCAL_NUMBER_LENGTH, '0')", 1)
replace_exact(credit, "normalized.length <= 3", "normalized.length <= BILLING_POINT_LENGTH", 1)
replace_exact(credit, "normalized.padStart(3, '0')", "normalized.padStart(BILLING_POINT_LENGTH, '0')", 1)

venezuela = ROOT / "venezuela/VenezuelaHkaPayloadBuilder.kt"
replace_exact(venezuela, "        private const val QTY_SCALE = 3\n", "        private const val QTY_SCALE = 3\n        private const val PERCENT_CALCULATION_SCALE = 6\n        private const val ISO_DATE_LENGTH = 10\n        private const val TRANSACTION_ID_HASH_BYTES = 16\n        private const val HUNDRED = 100\n        private const val TWENTY = 20\n        private const val TWENTY_ONE = 21\n        private const val TWENTY_NINE = 29\n        private const val DECIMAL_BASE = 10\n", 1)
replace_exact(venezuela, "BigDecimal(\"100\"), 6, RoundingMode.HALF_UP", "BigDecimal(\"100\"), PERCENT_CALCULATION_SCALE, RoundingMode.HALF_UP", 1)
replace_exact(venezuela, "fecha.trim().take(10)", "fecha.trim().take(ISO_DATE_LENGTH)", 1)
replace_exact(venezuela, "sha.copyOfRange(0, 16)", "sha.copyOfRange(0, TRANSACTION_ID_HASH_BYTES)", 1)
replace_exact(venezuela, "if (n == 100) return \"CIEN\"", "if (n == HUNDRED) return \"CIEN\"", 1)
replace_exact(venezuela, "if (resto >= 100)", "if (resto >= HUNDRED)", 1)
replace_exact(venezuela, "centenas[resto / 100]", "centenas[resto / HUNDRED]", 1)
replace_exact(venezuela, "resto %= 100", "resto %= HUNDRED", 1)
replace_exact(venezuela, "resto < 20", "resto < TWENTY", 1)
replace_exact(venezuela, "resto == 20", "resto == TWENTY", 1)
replace_exact(venezuela, "resto in 21..29", "resto in TWENTY_ONE..TWENTY_NINE", 1)
replace_exact(venezuela, "unidades[resto - 20]", "unidades[resto - TWENTY]", 1)
replace_exact(venezuela, "resto / 10", "resto / DECIMAL_BASE", 1)
replace_exact(venezuela, "resto % 10", "resto % DECIMAL_BASE", 1)

print("named all remaining PAC payload magic numbers without changing values or payload contracts")
