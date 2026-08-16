from pathlib import Path
import re

ROOT = Path("amaxoniaerp-backend/src/main/kotlin")
SCHEMA_IMPORT = "import com.amaxoniaerp.core.database.SchemaDimensions"

VARCHAR_LENGTHS = (3, 4, 5, 9, 10, 15, 20, 25, 30, 32, 36, 40, 45, 50, 60, 64, 80, 100, 120, 150, 200, 250, 255, 300, 500, 600, 5000)
DECIMAL_PRECISIONS = (6, 9, 10, 14, 18, 20, 32)
DECIMAL_SCALES = (3, 4, 6, 8)

constants_path = ROOT / "com/amaxoniaerp/core/database/SchemaDimensions.kt"
if constants_path.exists():
    raise RuntimeError(f"Schema dimensions already exist: {constants_path}")
constants_path.write_text(
    "package com.amaxoniaerp.core.database\n\n"
    "internal object SchemaDimensions {\n"
    + "".join(f"    const val VARCHAR_LENGTH_{value} = {value}\n" for value in VARCHAR_LENGTHS)
    + "".join(f"    const val DECIMAL_PRECISION_{value} = {value}\n" for value in DECIMAL_PRECISIONS)
    + "".join(f"    const val DECIMAL_SCALE_{value} = {value}\n" for value in DECIMAL_SCALES)
    + "}\n"
)

replacement_count = 0
changed_files = 0

def replace_varchar(match: re.Match[str]) -> str:
    global replacement_count
    length = int(match.group(2))
    if length not in VARCHAR_LENGTHS:
        return match.group(0)
    replacement_count += 1
    return f"varchar({match.group(1)}, SchemaDimensions.VARCHAR_LENGTH_{length})"


def replace_decimal(match: re.Match[str]) -> str:
    global replacement_count
    first_arg = match.group(1)
    precision = int(match.group(2))
    scale = int(match.group(3))
    precision_expr = str(precision)
    scale_expr = str(scale)
    if precision in DECIMAL_PRECISIONS:
        precision_expr = f"SchemaDimensions.DECIMAL_PRECISION_{precision}"
        replacement_count += 1
    if scale in DECIMAL_SCALES:
        scale_expr = f"SchemaDimensions.DECIMAL_SCALE_{scale}"
        replacement_count += 1
    return f"decimal({first_arg}, {precision_expr}, {scale_expr})"


def replace_enum(match: re.Match[str]) -> str:
    global replacement_count
    length = int(match.group(2))
    if length not in VARCHAR_LENGTHS:
        return match.group(0)
    replacement_count += 1
    return f"enumerationByName({match.group(1)}, SchemaDimensions.VARCHAR_LENGTH_{length},"

paths = sorted(set(ROOT.rglob("*Table.kt")) | set(ROOT.rglob("*Tables.kt")))
for path in paths:
    text = path.read_text()
    updated = re.sub(r"varchar\(([^,\n]+),\s*(\d+)\)", replace_varchar, text)
    updated = re.sub(r"decimal\(([^,\n]+),\s*(\d+)\s*,\s*(\d+)\)", replace_decimal, updated)
    updated = re.sub(r"enumerationByName\(([^,\n]+),\s*(\d+)\s*,", replace_enum, updated)
    if updated == text:
        continue
    if SCHEMA_IMPORT not in updated:
        package_end = updated.index("\n", updated.index("package ")) + 1
        updated = updated[:package_end] + "\n" + SCHEMA_IMPORT + "\n" + updated[package_end:]
    path.write_text(updated)
    changed_files += 1

if replacement_count != 770:
    raise RuntimeError(f"Expected exactly 770 schema literal replacements, found {replacement_count}")
if changed_files != 19:
    raise RuntimeError(f"Expected exactly 19 changed schema files, found {changed_files}")

print(f"centralized {replacement_count} schema literals across {changed_files} table files")
