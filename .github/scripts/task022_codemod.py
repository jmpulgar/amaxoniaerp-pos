from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[2]
SOURCE_ROOT = ROOT / "amaxoniaerp-backend/src/main/kotlin"
IGNORED = {"0", "1", "2"}

VARCHAR = re.compile(r'varchar\("([^"]+)",\s*(\d+)\)')
DECIMAL = re.compile(r'decimal\("([^"]+)",\s*(\d+)\s*,\s*(\d+)\)')
ENUM = re.compile(r'enumerationByName\("([^"]+)",\s*(\d+)\s*,')


def identifier(value: str) -> str:
    value = re.sub(r'([a-z0-9])([A-Z])', r'\1_\2', value)
    value = re.sub(r'[^A-Za-z0-9]+', '_', value).strip('_').upper()
    return value or "FIELD"


def add_requirement(requirements: dict[str, set[int]], base: str, numeric: str) -> None:
    if numeric not in IGNORED:
        requirements.setdefault(base, set()).add(int(numeric))


def constant_name(requirements: dict[str, set[int]], base: str, numeric: str) -> str:
    values = requirements[base]
    return f"{base}_{numeric}" if len(values) > 1 else base


def transform(path: Path) -> int:
    text = path.read_text(encoding="utf-8")
    requirements: dict[str, set[int]] = {}

    for match in VARCHAR.finditer(text):
        add_requirement(requirements, f"SCHEMA_{identifier(match.group(1))}_MAX_LENGTH", match.group(2))
    for match in DECIMAL.finditer(text):
        column = identifier(match.group(1))
        add_requirement(requirements, f"SCHEMA_{column}_PRECISION", match.group(2))
        add_requirement(requirements, f"SCHEMA_{column}_SCALE", match.group(3))
    for match in ENUM.finditer(text):
        add_requirement(requirements, f"SCHEMA_{identifier(match.group(1))}_ENUM_LENGTH", match.group(2))

    if not requirements:
        return 0

    replacements = 0

    def replace_varchar(match: re.Match[str]) -> str:
        nonlocal replacements
        column, length = match.groups()
        if length in IGNORED:
            return match.group(0)
        replacements += 1
        name = constant_name(requirements, f"SCHEMA_{identifier(column)}_MAX_LENGTH", length)
        return f'varchar("{column}", {name})'

    def replace_decimal(match: re.Match[str]) -> str:
        nonlocal replacements
        column, precision, scale = match.groups()
        column_id = identifier(column)
        precision_expr = precision
        scale_expr = scale
        if precision not in IGNORED:
            replacements += 1
            precision_expr = constant_name(requirements, f"SCHEMA_{column_id}_PRECISION", precision)
        if scale not in IGNORED:
            replacements += 1
            scale_expr = constant_name(requirements, f"SCHEMA_{column_id}_SCALE", scale)
        return f'decimal("{column}", {precision_expr}, {scale_expr})'

    def replace_enum(match: re.Match[str]) -> str:
        nonlocal replacements
        column, length = match.groups()
        if length in IGNORED:
            return match.group(0)
        replacements += 1
        name = constant_name(requirements, f"SCHEMA_{identifier(column)}_ENUM_LENGTH", length)
        return f'enumerationByName("{column}", {name},'

    updated = VARCHAR.sub(replace_varchar, text)
    updated = DECIMAL.sub(replace_decimal, updated)
    updated = ENUM.sub(replace_enum, updated)

    constants: list[str] = []
    for base in sorted(requirements):
        for value in sorted(requirements[base]):
            name = f"{base}_{value}" if len(requirements[base]) > 1 else base
            constants.append(f"private const val {name} = {value}")

    lines = updated.splitlines()
    last_import = max((index for index, line in enumerate(lines) if line.startswith("import ")), default=-1)
    if last_import < 0:
        raise RuntimeError(f"Cannot place schema constants in {path}")
    insertion = [""] + constants + [""]
    lines[last_import + 1:last_import + 1] = insertion
    path.write_text("\n".join(lines) + "\n", encoding="utf-8")
    return replacements


total = 0
changed_files = 0
for path in SOURCE_ROOT.rglob("*.kt"):
    count = transform(path)
    if count:
        total += count
        changed_files += 1

# SalesTables was normalized in the immediately preceding slice; the remaining schema debt is still large.
if total < 500 or changed_files < 10:
    raise RuntimeError(f"Unexpected schema cleanup scope: {total} replacements across {changed_files} files")

print(f"Named {total} schema numeric constraints across {changed_files} files")
