from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]


def replace_exact(path: Path, old: str, new: str, expected: int = 1) -> None:
    text = path.read_text(encoding="utf-8")
    actual = text.count(old)
    if actual != expected:
        raise RuntimeError(f"{path}: expected {expected} occurrence(s), found {actual}: {old!r}")
    path.write_text(text.replace(old, new), encoding="utf-8")


pedido = ROOT / "amaxoniaerp-backend/src/main/kotlin/com/amaxoniaerp/features/mesas/PedidoMesaRouting.kt"
replace_exact(pedido, "val areaId = call.requireAreaId() ?: return@get", "call.requireAreaId() ?: return@get")
replace_exact(pedido, "val areaId = call.requireAreaId() ?: return@post", "call.requireAreaId() ?: return@post")

test = ROOT / "amaxoniaerp-backend/src/test/kotlin/com/amaxoniaerp/features/mesas/SesionMesaRepositoryTest.kt"
replace_exact(test, "import com.amaxoniaerp.features.mesas.data.MesasRepository\n", "")
replace_exact(test, "    private val mesasRepository = MesasRepository()\n", "")

cuenta = ROOT / "amaxoniaerp-backend/src/main/kotlin/com/amaxoniaerp/features/mesas/data/CuentaMesaRepository.kt"
unused_helper = """    private fun existePedidoPendienteEnSesion(sesionId: Int): Boolean =\n        PedidoMesaTable\n            .selectAll()\n            .where {\n                (PedidoMesaTable.sesionMesaId eq sesionId) and\n                    (PedidoMesaTable.activo eq ACTIVE) and\n                    (PedidoMesaTable.estado notInList ESTADOS_NO_IMPiden_CIERRE)\n            }.limit(1)\n            .singleOrNull() != null\n\n"""
replace_exact(cuenta, unused_helper, "")
