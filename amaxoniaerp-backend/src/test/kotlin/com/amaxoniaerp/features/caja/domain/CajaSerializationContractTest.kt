package com.amaxoniaerp.features.caja.domain

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalSerializationApi::class)
class CajaSerializationContractTest {
    @Test
    fun `CajaSecuenciaData keeps its JSON field contract`() {
        assertFieldNames(
            CajaSecuenciaData.serializer(),
            "id",
            "id_caja",
            "id_vendedor",
            "secuencia",
            "fecha_apertura",
            "fecha_cierre",
            "fecha_creacion",
            "usuario",
            "observacion_apertura",
            "observacion_cierre",
            "monto_efectivo_apertura",
            "monto_efectivo_ventas",
            "monto_efectivo_entrada",
            "monto_efectivo_salida",
            "monto_efectivo_total",
            "monto_efectivo_cierre",
            "monto_efectivo_diferencia",
            "monto_otros_total",
            "monto_otros_cierre",
            "monto_otros_diferencia",
            "monto_total",
            "monto_cierre",
            "monto_diferencia",
            "total_ventas",
            "cantidad_transacciones",
            "numero_cierre_fiscal",
            "serie_sucursal",
            "serial_fiscal",
            "contabilizado",
            "ffecha_apertura",
            "ffecha_cierre",
            "caja_codigo",
            "caja",
            "fondo_apertura",
            "nombre_modelo",
            "vendedor",
            "detalle_apertura",
            "forma_pago",
            "forma_pago_devolucion",
            "total_anulado",
            "verificar_facturas_temporales",
            "inventario",
        )
    }

    @Test
    fun `CajaInventarioItem keeps its JSON field contract`() {
        assertFieldNames(
            CajaInventarioItem.serializer(),
            "codigo",
            "descripcion",
            "existencia_inicial",
            "cantidad_vendida",
            "existencia_disponible",
        )
    }

    @Test
    fun `CajaDetalleAperturaItem keeps its JSON field contract`() {
        assertFieldNames(
            CajaDetalleAperturaItem.serializer(),
            "id",
            "id_secuencia",
            "id_moneda_denominacion",
            "cantidad",
            "valor",
            "monto",
            "denominacion",
        )
    }

    @Test
    fun `CajaFormaPagoItem keeps its JSON field contract`() {
        assertFieldNames(
            CajaFormaPagoItem.serializer(),
            "id",
            "forma_pago",
            "siglas",
            "grupo",
            "imagen",
            "id_caja_tp_concepto",
            "tipo_moneda",
            "estatus",
            "grupo_nombre",
            "grupo_imagen",
            "grupo_orden",
            "grupo_activo",
            "monto",
        )
    }

    @Test
    fun `CajaFormaPagoDevolucionItem keeps its JSON field contract`() {
        assertFieldNames(
            CajaFormaPagoDevolucionItem.serializer(),
            "id_forma_pago",
            "siglas",
            "descripcion",
            "monto",
        )
    }

    @Test
    fun `CajaCierreSaveRequest keeps its JSON field contract`() {
        assertFieldNames(
            CajaCierreSaveRequest.serializer(),
            "id",
            "monto_efectivo_ventas",
            "monto_efectivo_entrada",
            "monto_efectivo_salida",
            "monto_efectivo_total",
            "monto_efectivo_cierre",
            "monto_efectivo_diferencia",
            "monto_otros_total",
            "monto_otros_cierre",
            "monto_otros_diferencia",
            "monto_total",
            "monto_cierre",
            "monto_diferencia",
            "detalle",
            "detalle_formapago",
            "observacion_cierre",
            "numero_cierre_fiscal",
        )
    }

    @Test
    fun `CajaCierreDetalleRequest keeps its JSON field contract`() {
        assertFieldNames(
            CajaCierreDetalleRequest.serializer(),
            "id_moneda_denominacion",
            "cantidad",
            "valor",
            "monto",
        )
    }

    @Test
    fun `CajaCierreFormaPagoRequest keeps its JSON field contract`() {
        assertFieldNames(
            CajaCierreFormaPagoRequest.serializer(),
            "id_forma_pago",
            "monto",
            "monto_cierre",
            "monto_diferencia",
        )
    }

    private fun <T> assertFieldNames(
        serializer: KSerializer<T>,
        vararg expected: String,
    ) {
        val descriptor = serializer.descriptor
        val actual =
            (0 until descriptor.elementsCount)
                .map(descriptor::getElementName)
        assertEquals(expected.toList(), actual)
    }
}
