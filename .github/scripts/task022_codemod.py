from pathlib import Path

ROOT = Path("amaxoniaerp-backend/src/main/kotlin/com/amaxoniaerp")


def replace_once(path: Path, old: str, new: str) -> None:
    text = path.read_text()
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"Expected exactly one match in {path}, found {count}")
    path.write_text(text.replace(old, new, 1))


cuenta = ROOT / "features/mesas/CuentaMesaRouting.kt"
replace_once(
    cuenta,
    " * - `POST   .../sesiones/{sesionId}/cuenta?cajaId=`                                     crear cuenta (completa o división).",
    " * - `POST   .../sesiones/{sesionId}/cuenta?cajaId=` crear cuenta\n *   (completa o división).",
)

caja_table = ROOT / "features/caja/data/CajaTable.kt"
replace_once(
    caja_table,
    '    // Y la columna estado no se llamaba estado sino "contabilizado" o simplemente no tiene "estado" pero sí tiene "activo"',
    '    // Y la columna estado no se llamaba estado sino "contabilizado".\n'
    '    // También puede no tener "estado", pero sí "activo".',
)

print("wrapped two comment-only MaxLineLength findings")
