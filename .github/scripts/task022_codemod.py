from pathlib import Path

ROOT = Path("amaxoniaerp-backend/src/main/kotlin")
IMPORT = "import com.amaxoniaerp.core.database.SchemaDimensions"
ALIASED_IMPORT = "import com.amaxoniaerp.core.database.SchemaDimensions as S"

changed = 0
for path in sorted(set(ROOT.rglob("*Table.kt")) | set(ROOT.rglob("*Tables.kt"))):
    text = path.read_text()
    if IMPORT not in text:
        continue
    updated = text.replace(IMPORT, ALIASED_IMPORT).replace("SchemaDimensions.", "S.")
    if updated != text:
        path.write_text(updated)
        changed += 1

if changed != 19:
    raise RuntimeError(f"Expected 19 aliased schema files, found {changed}")

print(f"aliased SchemaDimensions in {changed} table files")
