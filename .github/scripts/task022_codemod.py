from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[2]

MAPPING = {
    "caja_codigo": "cajaCodigo",
    "cantidad_transacciones": "cantidadTransacciones",
    "cantidad_vendida": "cantidadVendida",
    "detalle_apertura": "detalleApertura",
    "detalle_formapago": "detalleFormaPago",
    "existencia_disponible": "existenciaDisponible",
    "existencia_inicial": "existenciaInicial",
    "fecha_apertura": "fechaApertura",
    "fecha_cierre": "fechaCierre",
    "fecha_creacion": "fechaCreacion",
    "ffecha_apertura": "ffechaApertura",
    "ffecha_cierre": "ffechaCierre",
    "fondo_apertura": "fondoApertura",
    "forma_pago": "formaPago",
    "forma_pago_devolucion": "formaPagoDevolucion",
    "grupo_activo": "grupoActivo",
    "grupo_imagen": "grupoImagen",
    "grupo_nombre": "grupoNombre",
    "grupo_orden": "grupoOrden",
    "id_caja": "idCaja",
    "id_caja_tp_concepto": "idCajaTpConcepto",
    "id_forma_pago": "idFormaPago",
    "id_moneda_denominacion": "idMonedaDenominacion",
    "id_secuencia": "idSecuencia",
    "id_vendedor": "idVendedor",
    "monto_cierre": "montoCierre",
    "monto_diferencia": "montoDiferencia",
    "monto_efectivo_apertura": "montoEfectivoApertura",
    "monto_efectivo_cierre": "montoEfectivoCierre",
    "monto_efectivo_diferencia": "montoEfectivoDiferencia",
    "monto_efectivo_entrada": "montoEfectivoEntrada",
    "monto_efectivo_salida": "montoEfectivoSalida",
    "monto_efectivo_total": "montoEfectivoTotal",
    "monto_efectivo_ventas": "montoEfectivoVentas",
    "monto_otros_cierre": "montoOtrosCierre",
    "monto_otros_diferencia": "montoOtrosDiferencia",
    "monto_otros_total": "montoOtrosTotal",
    "monto_total": "montoTotal",
    "nombre_modelo": "nombreModelo",
    "numero_cierre_fiscal": "numeroCierreFiscal",
    "observacion_apertura": "observacionApertura",
    "observacion_cierre": "observacionCierre",
    "serial_fiscal": "serialFiscal",
    "serie_sucursal": "serieSucursal",
    "tipo_moneda": "tipoMoneda",
    "total_anulado": "totalAnulado",
    "total_ventas": "totalVentas",
    "verificar_facturas_temporales": "verificarFacturasTemporales",
}

EXPECTED_FIELDS = {
    "CajaSecuenciaData": [
        "id", "id_caja", "id_vendedor", "secuencia", "fecha_apertura", "fecha_cierre",
        "fecha_creacion", "usuario", "observacion_apertura", "observacion_cierre",
        "monto_efectivo_apertura", "monto_efectivo_ventas", "monto_efectivo_entrada",
        "monto_efectivo_salida", "monto_efectivo_total", "monto_efectivo_cierre",
        "monto_efectivo_diferencia", "monto_otros_total", "monto_otros_cierre",
        "monto_otros_diferencia", "monto_total", "monto_cierre", "monto_diferencia",
        "total_ventas", "cantidad_transacciones", "numero_cierre_fiscal", "serie_sucursal",
        "serial_fiscal", "contabilizado", "ffecha_apertura", "ffecha_cierre", "caja_codigo",
        "caja", "fondo_apertura", "nombre_modelo", "vendedor", "detalle_apertura", "forma_pago",
        "forma_pago_devolucion", "total_anulado", "verificar_facturas_temporales", "inventario",
    ],
    "CajaInventarioItem": ["codigo", "descripcion", "existencia_inicial", "cantidad_vendida", "existencia_disponible"],
    "CajaDetalleAperturaItem": ["id", "id_secuencia", "id_moneda_denominacion", "cantidad", "valor", "monto", "denominacion"],
    "CajaFormaPagoItem": [
        "id", "forma_pago", "siglas", "grupo", "imagen", "id_caja_tp_concepto", "tipo_moneda",
        "estatus", "grupo_nombre", "grupo_imagen", "grupo_orden", "grupo_activo", "monto",
    ],
    "CajaFormaPagoDevolucionItem": ["id_forma_pago", "siglas", "descripcion", "monto"],
    "CajaCierreSaveRequest": [
        "id", "monto_efectivo_ventas", "monto_efectivo_entrada", "monto_efectivo_salida",
        "monto_efectivo_total", "monto_efectivo_cierre", "monto_efectivo_diferencia",
        "monto_otros_total", "monto_otros_cierre", "monto_otros_diferencia", "monto_total",
        "monto_cierre", "monto_diferencia", "detalle", "detalle_formapago", "observacion_cierre",
        "numero_cierre_fiscal",
    ],
    "CajaCierreDetalleRequest": ["id_moneda_denominacion", "cantidad", "valor", "monto"],
    "CajaCierreFormaPagoRequest": ["id_forma_pago", "monto", "monto_cierre", "monto_diferencia"],
}


def replace_identifiers_in_code(text: str, mapping: dict[str, str]) -> str:
    out = []
    i = 0
    state = "code"
    block_depth = 0
    while i < len(text):
        if state == "code":
            if text.startswith("//", i):
                out.append("//")
                i += 2
                state = "line"
            elif text.startswith("/*", i):
                out.append("/*")
                i += 2
                state = "block"
                block_depth = 1
            elif text.startswith('"""', i):
                out.append('"""')
                i += 3
                state = "triple"
            elif text[i] == '"':
                out.append('"')
                i += 1
                state = "string"
            elif text[i] == "'":
                out.append("'")
                i += 1
                state = "char"
            elif text[i].isalpha() or text[i] == "_":
                j = i + 1
                while j < len(text) and (text[j].isalnum() or text[j] == "_"):
                    j += 1
                token = text[i:j]
                out.append(mapping.get(token, token))
                i = j
            else:
                out.append(text[i])
                i += 1
        elif state == "line":
            out.append(text[i])
            if text[i] == "\n":
                state = "code"
            i += 1
        elif state == "block":
            if text.startswith("/*", i):
                out.append("/*")
                i += 2
                block_depth += 1
            elif text.startswith("*/", i):
                out.append("*/")
                i += 2
                block_depth -= 1
                if block_depth == 0:
                    state = "code"
            else:
                out.append(text[i])
                i += 1
        elif state == "triple":
            if text.startswith('"""', i):
                out.append('"""')
                i += 3
                state = "code"
            else:
                out.append(text[i])
                i += 1
        elif state in {"string", "char"}:
            quote = '"' if state == "string" else "'"
            if text[i] == "\\" and i + 1 < len(text):
                out.append(text[i:i + 2])
                i += 2
            else:
                out.append(text[i])
                if text[i] == quote:
                    state = "code"
                i += 1
    return "".join(out)


models = ROOT / "amaxoniaerp-backend/src/main/kotlin/com/amaxoniaerp/features/caja/domain/CajaModels.kt"
model_text = models.read_text(encoding="utf-8")
for old in MAPPING:
    pattern = rf'(?m)^(\s*)val {re.escape(old)}:'
    if len(re.findall(pattern, model_text)) == 0:
        raise RuntimeError(f"No CajaModels declaration found for {old}")
    model_text = re.sub(pattern, rf'\1@SerialName("{old}")\n\1val {old}:', model_text)
model_text = replace_identifiers_in_code(model_text, MAPPING)
models.write_text(model_text, encoding="utf-8")

repository = ROOT / "amaxoniaerp-backend/src/main/kotlin/com/amaxoniaerp/features/caja/data/CajaRepository.kt"
repo_text = repository.read_text(encoding="utf-8")
repo_text = replace_identifiers_in_code(repo_text, MAPPING)
repository.write_text(repo_text, encoding="utf-8")

test_path = ROOT / "amaxoniaerp-backend/src/test/kotlin/com/amaxoniaerp/features/caja/domain/CajaSerializationContractTest.kt"
test_path.parent.mkdir(parents=True, exist_ok=True)
lines = [
    "package com.amaxoniaerp.features.caja.domain",
    "",
    "import kotlinx.serialization.ExperimentalSerializationApi",
    "import kotlinx.serialization.KSerializer",
    "import kotlin.test.Test",
    "import kotlin.test.assertEquals",
    "",
    "@OptIn(ExperimentalSerializationApi::class)",
    "class CajaSerializationContractTest {",
]
for class_name, fields in EXPECTED_FIELDS.items():
    lines.extend([
        "    @Test",
        f"    fun `{class_name} keeps its JSON field contract`() {{",
        "        assertFieldNames(",
        f"            {class_name}.serializer(),",
    ])
    lines.extend(f'            "{field}",' for field in fields)
    lines.extend(["        )", "    }", ""])
lines.extend([
    "    private fun <T> assertFieldNames(",
    "        serializer: KSerializer<T>,",
    "        vararg expected: String,",
    "    ) {",
    "        val descriptor = serializer.descriptor",
    "        val actual =",
    "            (0 until descriptor.elementsCount)",
    "                .map(descriptor::getElementName)",
    "        assertEquals(expected.toList(), actual)",
    "    }",
    "}",
    "",
])
test_path.write_text("\n".join(lines), encoding="utf-8")
