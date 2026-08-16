from pathlib import Path
import re

ROOT = Path("amaxoniaerp-backend")

mapping = {
    "id_caja": "idCaja",
    "id_vendedor": "idVendedor",
    "fecha_apertura": "fechaApertura",
    "fecha_cierre": "fechaCierre",
    "fecha_creacion": "fechaCreacion",
    "observacion_apertura": "observacionApertura",
    "observacion_cierre": "observacionCierre",
    "monto_efectivo_apertura": "montoEfectivoApertura",
    "monto_efectivo_ventas": "montoEfectivoVentas",
    "monto_efectivo_entrada": "montoEfectivoEntrada",
    "monto_efectivo_salida": "montoEfectivoSalida",
    "monto_efectivo_total": "montoEfectivoTotal",
    "monto_efectivo_cierre": "montoEfectivoCierre",
    "monto_efectivo_diferencia": "montoEfectivoDiferencia",
    "monto_otros_total": "montoOtrosTotal",
    "monto_otros_cierre": "montoOtrosCierre",
    "monto_otros_diferencia": "montoOtrosDiferencia",
    "monto_total": "montoTotal",
    "monto_cierre": "montoCierre",
    "monto_diferencia": "montoDiferencia",
    "total_ventas": "totalVentas",
    "cantidad_transacciones": "cantidadTransacciones",
    "numero_cierre_fiscal": "numeroCierreFiscal",
    "serie_sucursal": "serieSucursal",
    "serial_fiscal": "serialFiscal",
    "ffecha_apertura": "ffechaApertura",
    "ffecha_cierre": "ffechaCierre",
    "caja_codigo": "cajaCodigo",
    "fondo_apertura": "fondoApertura",
    "nombre_modelo": "nombreModelo",
    "detalle_apertura": "detalleApertura",
    "forma_pago": "formaPago",
    "forma_pago_devolucion": "formaPagoDevolucion",
    "total_anulado": "totalAnulado",
    "verificar_facturas_temporales": "verificarFacturasTemporales",
    "existencia_inicial": "existenciaInicial",
    "cantidad_vendida": "cantidadVendida",
    "existencia_disponible": "existenciaDisponible",
    "id_secuencia": "idSecuencia",
    "id_moneda_denominacion": "idMonedaDenominacion",
    "id_caja_tp_concepto": "idCajaTpConcepto",
    "tipo_moneda": "tipoMoneda",
    "grupo_nombre": "grupoNombre",
    "grupo_imagen": "grupoImagen",
    "grupo_orden": "grupoOrden",
    "grupo_activo": "grupoActivo",
    "id_forma_pago": "idFormaPago",
    "detalle_formapago": "detalleFormaPago",
}

models = ROOT / "src/main/kotlin/com/amaxoniaerp/features/caja/domain/CajaModels.kt"
text = models.read_text()
model_replacements = 0
for serialized_name, property_name in mapping.items():
    pattern = re.compile(rf"(?m)^(\s*)val {re.escape(serialized_name)}(\s*:)")
    text, count = pattern.subn(
        rf'\1@SerialName("{serialized_name}") val {property_name}\2',
        text,
    )
    model_replacements += count
if model_replacements != 67:
    raise RuntimeError(f"Expected 67 Caja model naming replacements, found {model_replacements}")
models.write_text(text)

repository = ROOT / "src/main/kotlin/com/amaxoniaerp/features/caja/data/CajaRepository.kt"
text = repository.read_text()
repo_replacements = 0
for serialized_name, property_name in mapping.items():
    text, property_count = re.subn(
        rf"\.{re.escape(serialized_name)}\b",
        f".{property_name}",
        text,
    )
    text, named_arg_count = re.subn(
        rf"\b{re.escape(serialized_name)}(?=\s*=)",
        property_name,
        text,
    )
    repo_replacements += property_count + named_arg_count
if repo_replacements != 99:
    raise RuntimeError(f"Expected 99 Caja repository references, found {repo_replacements}")
repository.write_text(text)

test_path = ROOT / "src/test/kotlin/com/amaxoniaerp/features/caja/domain/CajaSerializationContractTest.kt"
test_path.parent.mkdir(parents=True, exist_ok=True)
if test_path.exists():
    raise RuntimeError(f"Contract test already exists: {test_path}")
test_path.write_text(
    '''package com.amaxoniaerp.features.caja.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class CajaSerializationContractTest {
    private val json = Json { encodeDefaults = true }

    @Test
    fun `secuencia mantiene claves snake case`() {
        val encoded = json.encodeToString(CajaSecuenciaData(id = "seq-1", idCaja = "caja-1"))

        assertTrue("\\\"id_caja\\\":\\\"caja-1\\\"" in encoded)
        assertTrue("\\\"monto_efectivo_apertura\\\":0.0" in encoded)
        assertTrue("\\\"forma_pago\\\":[]" in encoded)
        assertFalse("\\\"idCaja\\\"" in encoded)
    }

    @Test
    fun `detalle de cierre mantiene claves snake case`() {
        val encoded =
            json.encodeToString(
                CajaCierreFormaPagoRequest(
                    idFormaPago = 1,
                    monto = 0.0,
                    montoCierre = 0.0,
                    montoDiferencia = 0.0,
                ),
            )

        assertEquals(
            "{\\\"id_forma_pago\\\":1,\\\"monto\\\":0.0,\\\"monto_cierre\\\":0.0,\\\"monto_diferencia\\\":0.0}",
            encoded,
        )
    }
}
'''
)

print(f"renamed {model_replacements} serialized Caja properties and {repo_replacements} references")
