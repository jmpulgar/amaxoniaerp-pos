package com.amaxoniaerp.features.mesas.data

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.datetime

private const val SCHEMA_CANTIDAD_FACTURADA_PRECISION = 32
private const val SCHEMA_CANTIDAD_FACTURADA_SCALE = 3
private const val SCHEMA_ESTADO_MAX_LENGTH = 30
private const val SCHEMA_ITEM_CANTIDAD_PRECISION = 32
private const val SCHEMA_ITEM_CANTIDAD_SCALE = 3
private const val SCHEMA_ITEM_CODIGO_MAX_LENGTH = 80
private const val SCHEMA_ITEM_DESCRIPCION_MAX_LENGTH = 500
private const val SCHEMA_ITEM_DESCUENTO_PRECISION = 10
private const val SCHEMA_ITEM_MONTODESCUENTO_PRECISION = 20
private const val SCHEMA_ITEM_PIVA_PRECISION = 10
private const val SCHEMA_ITEM_PRECIOSINIVA_PRECISION = 20
private const val SCHEMA_ITEM_TOTALCONIVA_PRECISION = 20
private const val SCHEMA_ITEM_TOTALSINIVA_PRECISION = 20
private const val SCHEMA_NOTAS_MAX_LENGTH = 300
private const val SCHEMA_PROMOCION_DETALLE_ID_MAX_LENGTH = 40
private const val SCHEMA_PROMOCION_ID_MAX_LENGTH = 40
private const val SCHEMA_PROMOCION_TIPO_MAX_LENGTH = 40
private const val SCHEMA_UNIDAD_EMPAQUE_MAX_LENGTH = 40

/**
 * Pedidos y comandas ligados a una sesión operativa de mesa.
 *
 * Modelo (ver migración `002_pedido_mesa.sql` para el DDL completo):
 *
 * - Cada fila es una **línea de pedido** sobre la sesión.
 * - La *comanda* es un concepto **derivado**: el conjunto de líneas que comparten el mismo
 *   `comandaSecuencia` (no nulo) fueron enviadas juntas a cocina/bar en el mismo instante.
 *   Las líneas con `comandaSecuencia = null` están `PENDIENTE` de enviar: viven en el buffer
 *   del POS hasta que el operario presiona "Enviar comanda".
 * - El `estado` vive por línea, no por comanda: permite anular un item sin tocar la comanda
 *   completa ni reenviarla. La transición de estados la decide cocina/bar (`EN_PREPARACION`,
 *   `LISTA`, `ENTREGADA`) o el cajero (`CANCELADA`).
 *
 * Estados (`EstadoPedidoMesa`):
 * - `PENDIENTE` : línea recién agregada, todavía no enviada a preparación.
 * - `ENVIADA`   : enviada a cocina/bar; ninguna estación la tomó todavía.
 * - `EN_PREPARACION`, `LISTA`, `ENTREGADA` : avance del flujo de cocina.
 * - `CANCELADA` : la línea se anuló y no cuenta para facturación.
 *
 * El campo `activo = 0` se reserva para líneas físicamente suprimidas en el futuro; en esta
 * fase una línea anulada queda con `estado = CANCELADA` y `activo = 1` para auditoría.
 */
object PedidoMesaTable : Table("pedido_mesa") {
    val id = integer("id").autoIncrement("seq_pedido_mesa")
    val sesionMesaId = integer("sesion_mesa_id")
    val comandaSecuencia = integer("comanda_secuencia").nullable()
    val productoId = integer("producto_id")
    val itemAlmacen = integer("item_almacen").default(1)
    val itemCodigo = varchar("item_codigo", SCHEMA_ITEM_CODIGO_MAX_LENGTH).default("")
    val itemDescripcion = varchar("item_descripcion", SCHEMA_ITEM_DESCRIPCION_MAX_LENGTH)
    val itemCantidad = decimal("item_cantidad", SCHEMA_ITEM_CANTIDAD_PRECISION, SCHEMA_ITEM_CANTIDAD_SCALE)
    val itemPrecioSinIva = decimal("item_preciosiniva", SCHEMA_ITEM_PRECIOSINIVA_PRECISION, 2)
    val itemDescuento = decimal("item_descuento", SCHEMA_ITEM_DESCUENTO_PRECISION, 2).default(0.toBigDecimal())
    val itemMontoDescuento = decimal("item_montodescuento", SCHEMA_ITEM_MONTODESCUENTO_PRECISION, 2).default(0.toBigDecimal())
    val itemPIva = decimal("item_piva", SCHEMA_ITEM_PIVA_PRECISION, 2).default(0.toBigDecimal())
    val itemTotalSinIva = decimal("item_totalsiniva", SCHEMA_ITEM_TOTALSINIVA_PRECISION, 2)
    val itemTotalConIva = decimal("item_totalconiva", SCHEMA_ITEM_TOTALCONIVA_PRECISION, 2)
    val cantidadBulto = integer("cantidad_bulto").default(1)
    val unidadEmpaque = varchar("unidad_empaque", SCHEMA_UNIDAD_EMPAQUE_MAX_LENGTH).default("UNIDAD")
    val notas = varchar("notas", SCHEMA_NOTAS_MAX_LENGTH).nullable()
    val promocionId = varchar("promocion_id", SCHEMA_PROMOCION_ID_MAX_LENGTH).nullable()
    val promocionTipo = varchar("promocion_tipo", SCHEMA_PROMOCION_TIPO_MAX_LENGTH).nullable()
    val promocionDetalleId = varchar("promocion_detalle_id", SCHEMA_PROMOCION_DETALLE_ID_MAX_LENGTH).nullable()
    val estado = varchar("estado", SCHEMA_ESTADO_MAX_LENGTH).default(EstadoPedidoMesaDefault.PENDIENTE)
    val fechaCreacion = datetime("fecha_creacion")
    val fechaEnvio = datetime("fecha_envio").nullable()
    val fechaEntrega = datetime("fecha_entrega").nullable()

    /** Cantidad acumulada ya asociada a cuentas facturadas. Evita cobrar 2x y permite
     *  divisiones parciales: `saldoPendiente = itemCantidad - cantidadFacturada`. */
    val cantidadFacturada =
        decimal(
            "cantidad_facturada",
            SCHEMA_CANTIDAD_FACTURADA_PRECISION,
            SCHEMA_CANTIDAD_FACTURADA_SCALE,
        ).default(0.toBigDecimal())
    val activo = integer("activo").default(1)

    override val primaryKey = PrimaryKey(id)

    init {
        /**
         * Índice de agregación de comanda: dado un `(sesion, secuencia)` se agrupan las
         * líneas enviadas en el mismo instante. `comanda_secuencia` puede ser NULL para las
         * líneas pendientes y por eso NO entra en una UNIQUE KEY: dos líneas pendientes tienen
         * ambas `NULL`, y `NULL != NULL` en SQL, pero varias líneas enviadas comparten el
         * mismo número (no es único). El índice solo optimiza el lookup por comanda.
         */
        index("ix_pedido_mesa_comanda", false, sesionMesaId, comandaSecuencia)
    }
}

/**
 * Constantes de estado almacenadas en `pedido_mesa.estado`. Reflejan
 * [com.amaxoniaerp.features.mesas.domain.EstadoPedidoMesa.codigo].
 */
object EstadoPedidoMesaDefault {
    const val PENDIENTE = "PENDIENTE"
    const val ENVIADA = "ENVIADA"
    const val EN_PREPARACION = "EN_PREPARACION"
    const val LISTA = "LISTA"
    const val ENTREGADA = "ENTREGADA"
    const val CANCELADA = "CANCELADA"
}
