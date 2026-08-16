package com.amaxoniaerp.features.mesas.data

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.datetime

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
    val itemCodigo = varchar("item_codigo", 80).default("")
    val itemDescripcion = varchar("item_descripcion", 500)
    val itemCantidad = decimal("item_cantidad", 32, 3)
    val itemPrecioSinIva = decimal("item_preciosiniva", 20, 2)
    val itemDescuento = decimal("item_descuento", 10, 2).default(0.toBigDecimal())
    val itemMontoDescuento = decimal("item_montodescuento", 20, 2).default(0.toBigDecimal())
    val itemPIva = decimal("item_piva", 10, 2).default(0.toBigDecimal())
    val itemTotalSinIva = decimal("item_totalsiniva", 20, 2)
    val itemTotalConIva = decimal("item_totalconiva", 20, 2)
    val cantidadBulto = integer("cantidad_bulto").default(1)
    val unidadEmpaque = varchar("unidad_empaque", 40).default("UNIDAD")
    val notas = varchar("notas", 300).nullable()
    val promocionId = varchar("promocion_id", 40).nullable()
    val promocionTipo = varchar("promocion_tipo", 40).nullable()
    val promocionDetalleId = varchar("promocion_detalle_id", 40).nullable()
    val estado = varchar("estado", 30).default(EstadoPedidoMesaDefault.PENDIENTE)
    val fechaCreacion = datetime("fecha_creacion")
    val fechaEnvio = datetime("fecha_envio").nullable()
    val fechaEntrega = datetime("fecha_entrega").nullable()

    /** Cantidad acumulada ya asociada a cuentas facturadas. Evita cobrar 2x y permite
     *  divisiones parciales: `saldoPendiente = itemCantidad - cantidadFacturada`. */
    val cantidadFacturada = decimal("cantidad_facturada", 32, 3).default(0.toBigDecimal())
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
