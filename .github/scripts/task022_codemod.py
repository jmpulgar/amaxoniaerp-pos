from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[2]
TARGET = ROOT / "amaxoniaerp-backend/src/main/kotlin/com/amaxoniaerp/features/sales/data/SalesTables.kt"

text = TARGET.read_text(encoding="utf-8")
marker = "import org.jetbrains.exposed.sql.javatime.datetime\n"
if marker not in text:
    raise RuntimeError("SalesTables import marker not found")

constants = """
private const val UUID_LENGTH = 36
private const val LEGACY_IDENTIFIER_LENGTH = 32
private const val SHORT_CODE_LENGTH = 10
private const val TYPE_CODE_LENGTH = 20
private const val SHORT_TOKEN_LENGTH = 5
private const val VERY_SHORT_CODE_LENGTH = 4
private const val RECEIPT_TYPE_LENGTH = 3
private const val COMPACT_CODE_LENGTH = 15
private const val REFERENCE_LENGTH = 30
private const val USER_LENGTH = 40
private const val STANDARD_CODE_LENGTH = 50
private const val AUDIT_USER_LENGTH = 60
private const val CONTACT_REFERENCE_LENGTH = 80
private const val STANDARD_TEXT_LENGTH = 100
private const val DISPLAY_NAME_LENGTH = 200
private const val ADDRESS_LENGTH = 250
private const val LONG_TEXT_LENGTH = 300
private const val ITEM_DESCRIPTION_LENGTH = 500
private const val OBSERVATION_LENGTH = 600
private const val MONEY_PRECISION = 20
private const val LEGACY_AMOUNT_PRECISION = 10
private const val QUANTITY_PRECISION = 32
private const val STOCK_PRECISION = 18
private const val KARDEX_PRICE_PRECISION = 9
private const val QUANTITY_SCALE = 3
private const val STOCK_SCALE = 4
"""
text = text.replace(marker, marker + constants, 1)

varchar_lengths = {
    36: "UUID_LENGTH",
    32: "LEGACY_IDENTIFIER_LENGTH",
    10: "SHORT_CODE_LENGTH",
    20: "TYPE_CODE_LENGTH",
    5: "SHORT_TOKEN_LENGTH",
    4: "VERY_SHORT_CODE_LENGTH",
    3: "RECEIPT_TYPE_LENGTH",
    15: "COMPACT_CODE_LENGTH",
    30: "REFERENCE_LENGTH",
    40: "USER_LENGTH",
    50: "STANDARD_CODE_LENGTH",
    60: "AUDIT_USER_LENGTH",
    80: "CONTACT_REFERENCE_LENGTH",
    100: "STANDARD_TEXT_LENGTH",
    200: "DISPLAY_NAME_LENGTH",
    250: "ADDRESS_LENGTH",
    300: "LONG_TEXT_LENGTH",
    500: "ITEM_DESCRIPTION_LENGTH",
    600: "OBSERVATION_LENGTH",
}
for value, name in varchar_lengths.items():
    text = re.sub(rf'(varchar\([^\n,]+,\s*){value}(\))', rf'\1{name}\2', text)

text = text.replace(
    'enumerationByName("status", 10, CajaStatus::class)',
    'enumerationByName("status", SHORT_CODE_LENGTH, CajaStatus::class)',
)

precisions = {
    20: "MONEY_PRECISION",
    10: "LEGACY_AMOUNT_PRECISION",
    32: "QUANTITY_PRECISION",
    18: "STOCK_PRECISION",
    9: "KARDEX_PRICE_PRECISION",
}
for value, name in precisions.items():
    text = re.sub(rf'(decimal\([^\n,]+,\s*){value}(\s*,)', rf'\1{name}\2', text)
text = re.sub(r'(decimal\([^\n]+,\s*)3(\))', r'\1QUANTITY_SCALE\2', text)
text = re.sub(r'(decimal\([^\n]+,\s*)4(\))', r'\1STOCK_SCALE\2', text)

# Guard: every non-ignored numeric literal outside strings/comments after the constants must be gone.
body = text.split("private const val STOCK_SCALE = 4", 1)[1]
for line_number, line in enumerate(body.splitlines(), 1):
    code = re.sub(r'"(?:\\.|[^"\\])*"', '', line).split("//", 1)[0]
    remaining = [
        token
        for token in re.findall(r'(?<![\w.])-?\d+(?:\.\d+)?', code)
        if token not in {"-1", "0", "1", "2"}
    ]
    if remaining:
        raise RuntimeError(f"Unmapped SalesTables numeric literal(s) {remaining} near body line {line_number}")

TARGET.write_text(text, encoding="utf-8")
