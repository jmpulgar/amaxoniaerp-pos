package com.amaxoniaerp.features.pedidos.data

import com.amaxoniaerp.core.database.LegacySql.columnsOf
import com.amaxoniaerp.core.database.LegacySql.execQuery
import com.amaxoniaerp.core.database.LegacySql.execUpdate
import com.amaxoniaerp.core.database.LegacySql.insertAdaptive
import com.amaxoniaerp.core.database.LegacySql.reserveCorrelativo
import com.amaxoniaerp.core.database.dbQuery
import com.amaxoniaerp.core.database.formatCorrelativo
import com.amaxoniaerp.core.time.BusinessClock
import com.amaxoniaerp.features.pedidos.domain.CreatePedidoRequest
import com.amaxoniaerp.features.pedidos.domain.CreatePedidoResponse
import com.amaxoniaerp.features.pedidos.domain.PedidoDetail
import com.amaxoniaerp.features.pedidos.domain.PedidoEstatus
import com.amaxoniaerp.features.pedidos.domain.PedidoLine
import com.amaxoniaerp.features.pedidos.domain.PedidoNotFoundException
import com.amaxoniaerp.features.pedidos.domain.PedidoStateException
import com.amaxoniaerp.features.pedidos.domain.PedidoSummary
import com.amaxoniaerp.features.pedidos.domain.PedidoValidationException
import com.amaxoniaerp.features.sales.domain.SaleItemInput
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.transactions.TransactionManager
import java.math.BigDecimal
import java.math.RoundingMode
import java.sql.ResultSet
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

private const val UUID_LENGTH = 36
private const val MAX_LIST_LIMIT = 200
private const val CORRELATIVO_PEDIDO = "cod_pedido"

/** Parámetros del listado de pedidos. */
data class PedidoListQuery(
    val estatus: List<Int> = listOf(PedidoEstatus.PENDIENTE),
    val search: String? = null,
    val idSucursal: Int? = null,
    val limit: Int = 50,
)

/**
 * Pedidos de venta (`pedido` / `pedido_detalle`) con las mismas reglas que el módulo web
 * `pedido/save`: `cod_estatus = 1`, correlativo `correlativos.cod_pedido`,
 * `_cantidad_restante = _item_cantidad` y sin movimiento de inventario al crear.
 *
 * Diferencias deliberadas con el web: no se registra `item_precompromiso` (equivale a
 * `facturar_caja = 0` en el web) ni eventos de calendario.
 */
class PedidoRepository {
    suspend fun create(
        database: Database,
        countryCode: String,
        request: CreatePedidoRequest,
        username: String,
    ): CreatePedidoResponse =
        dbQuery(database) {
            validate(request)
            val tx = TransactionManager.current()
            tx.findPedidoHeader(request.idPedido)?.let { existing ->
                return@dbQuery CreatePedidoResponse(
                    success = true,
                    idPedido = request.idPedido,
                    codPedido = existing.codPedido,
                    codEstatus = existing.codEstatus,
                    duplicado = true,
                )
            }
            val now = BusinessClock.nowForCountry(countryCode)
            val codPedido = tx.nextCodPedido()
            tx.insertAdaptive("pedido", pedidoValues(request, codPedido, now, username))
            val detalleColumns = tx.columnsOf("pedido_detalle")
            request.items.forEach { item ->
                tx.insertAdaptive("pedido_detalle", detalleValues(request.idPedido, item, now, username), detalleColumns)
            }
            CreatePedidoResponse(
                success = true,
                idPedido = request.idPedido,
                codPedido = codPedido,
                codEstatus = PedidoEstatus.PENDIENTE,
            )
        }

    suspend fun list(
        database: Database,
        query: PedidoListQuery,
    ): List<PedidoSummary> =
        dbQuery(database) {
            val params = mutableListOf<Any?>()
            val where = StringBuilder("WHERE 1 = 1")
            if (query.estatus.isNotEmpty()) {
                where.append(" AND P.cod_estatus IN (${query.estatus.joinToString(",") { "?" }})")
                params.addAll(query.estatus)
            }
            query.idSucursal?.let {
                where.append(" AND P.id_sucursal = ?")
                params += it
            }
            query.search?.trim()?.takeIf { it.isNotEmpty() }?.let { term ->
                where.append(
                    " AND (P.cod_pedido LIKE ? OR C.cod_cliente LIKE ? OR C.nombre LIKE ? " +
                        "OR C.apellido LIKE ? OR P.facturar_a LIKE ? OR P.facturar_a_ruc LIKE ?)",
                )
                repeat(SEARCH_COLUMNS) { params += "%$term%" }
            }
            params += query.limit.coerceIn(1, MAX_LIST_LIMIT)
            TransactionManager.current().execQuery(
                "$SUMMARY_SELECT $where ORDER BY P.fecha_creacion DESC, P.cod_pedido DESC LIMIT ?",
                params,
                ::mapSummary,
            )
        }

    suspend fun detail(
        database: Database,
        idPedido: String,
    ): PedidoDetail =
        dbQuery(database) {
            val tx = TransactionManager.current()
            val summary =
                tx.execQuery("$SUMMARY_SELECT WHERE P.id_pedido = ? LIMIT 1", listOf(idPedido), ::mapSummary).firstOrNull()
                    ?: throw PedidoNotFoundException("Pedido no encontrado")
            val totals =
                tx
                    .execQuery(
                        "SELECT subtotal, descuentosItemPedido, totalizar_base_imponible, ivaTotalPedido " +
                            "FROM pedido WHERE id_pedido = ?",
                        listOf(idPedido),
                    ) { rs ->
                        listOf(
                            rs.money("subtotal"),
                            rs.money("descuentosItemPedido"),
                            rs.money("totalizar_base_imponible"),
                            rs.money("ivaTotalPedido"),
                        )
                    }.first()
            PedidoDetail(
                pedido = summary,
                subtotal = totals[0],
                descuentos = totals[1],
                baseImponible = totals[2],
                impuesto = totals[3],
                items = tx.execQuery(DETAIL_SELECT, listOf(idPedido), ::mapLine),
            )
        }

    private fun validate(request: CreatePedidoRequest) {
        val id = request.idPedido.trim()
        val error =
            when {
                id.isEmpty() || id.length > UUID_LENGTH -> "idPedido inválido"
                request.items.isEmpty() -> "El pedido debe contener al menos un producto"
                request.pedido.idCliente.isBlank() -> "El pedido requiere un cliente"
                request.items.any { it.itemCantidad <= 0.0 } -> "Las cantidades del pedido deben ser mayores a cero"
                request.pedido.totalizarTotalGeneral < 0.0 -> "Total del pedido inválido"
                else -> null
            }
        error?.let { throw PedidoValidationException(it) }
    }

    private fun Transaction.nextCodPedido(): String {
        val (value, formato) =
            reserveCorrelativo(CORRELATIVO_PEDIDO, returnPrevious = true)
                ?: throw PedidoStateException("No está configurado el correlativo 'cod_pedido' en la empresa")
        return formatCorrelativo(value, formato)
    }

    private fun pedidoValues(
        request: CreatePedidoRequest,
        codPedido: String,
        now: LocalDateTime,
        username: String,
    ): Map<String, Any?> {
        val h = request.pedido
        val moneda = request.moneda
        val cantidadItems = request.items.sumOf { it.itemCantidad.toBigDecimal() }
        return mapOf(
            "id_pedido" to request.idPedido,
            "cod_pedido" to codPedido,
            "id_cliente" to h.idCliente,
            "cod_vendedor" to h.codVendedor,
            "id_factura" to "",
            "fechaPedido" to now.toLocalDate(),
            "subtotal" to h.subtotal,
            "descuentosItemPedido" to h.descuentosItemFactura,
            "montoItemsPedido" to h.montoItemsFactura,
            "ivaTotalPedido" to h.ivaTotalFactura,
            "TotalTotalPedido" to h.totalTotalFactura,
            "cantidad_items" to cantidadItems,
            "totalizar_sub_total" to h.totalizarSubTotal,
            "totalizar_descuento_parcial" to h.totalizarDescuentoParcial,
            "totalizar_total_operacion" to h.totalizarTotalOperacion,
            "totalizar_pdescuento_global" to h.totalizarPDescuentoGlobal,
            "totalizar_descuento_global" to h.totalizarDescuentoGlobal,
            "totalizar_base_imponible" to h.totalizarBaseImponible,
            "totalizar_monto_iva" to h.totalizarMontoIva,
            "totalizar_total_general" to h.totalizarTotalGeneral,
            "totalizar_total_retencion" to BigDecimal.ZERO,
            "formapago" to h.formaPago.ifBlank { "contado" }.take(FORMAPAGO_LENGTH),
            "cod_estatus" to PedidoEstatus.PENDIENTE,
            "total_bultos" to BigDecimal.ZERO,
            "peso_total_item" to BigDecimal.ZERO,
            "total_m3" to BigDecimal.ZERO,
            "total_ft3" to BigDecimal.ZERO,
            "total_porcentaje_ganancia" to BigDecimal.ZERO,
            "total_monto_ganancia_total" to BigDecimal.ZERO,
            "validar_stock" to "NO",
            "fecha_creacion" to now,
            "usuario_creacion" to username.take(USER_LENGTH),
            "observacion" to h.observacion.take(OBSERVACION_LENGTH),
            "observacion_pedido" to h.observacion.take(OBSERVACION_LENGTH),
            "facturar_a" to h.facturarA.take(FACTURAR_A_LENGTH),
            "facturar_a_ruc" to h.facturarARuc.take(RUC_LENGTH),
            "facturar_a_direccion" to h.facturarADireccion.take(DIRECCION_LENGTH),
            "facturar_a_telefono" to h.facturarATelefono.take(RUC_LENGTH),
            "facturar_a_tipo_transporte" to "",
            "id_shop" to h.idShop,
            "estatus_pedido" to 1,
            "serie_sucursal" to h.serieSucursal.take(SERIE_LENGTH),
            "id_sucursal" to h.idSucursal,
            "tipo_pedido" to "",
            "id_caja_secuencia" to h.idCajaSecuencia.take(UUID_LENGTH),
            "caja_secuencia" to "",
            "id_caja" to h.idCaja.take(UUID_LENGTH),
            "codigo_caja" to h.codigoCaja.take(CODIGO_CAJA_LENGTH),
            "fecha_limite" to 0,
            "fecha_vencimiento" to now,
            "fecha_cierre" to now,
            "plazo" to 0,
            "costo_envio" to BigDecimal.ZERO,
            "multi_moneda" to (moneda?.multiMoneda ?: "NO"),
            "tasa" to (moneda?.tasa ?: 1.0),
            "moneda_base" to (moneda?.monedaBase ?: 1),
            "abr_moneda_base" to (moneda?.abrMonedaBase ?: ""),
            "moneda_secundaria" to (moneda?.monedaSecundaria ?: 1),
            "abr_moneda_secundaria" to (moneda?.abrMonedaSecundaria ?: ""),
            "total_ref" to (moneda?.totalRef ?: h.totalizarTotalGeneral),
            "facturar_caja" to 0,
            "forma_entrega" to 0,
            "cod_estatus_comanda" to 1,
        )
    }

    private fun detalleValues(
        idPedido: String,
        item: SaleItemInput,
        now: LocalDateTime,
        username: String,
    ): Map<String, Any?> =
        mapOf(
            "id_detalle_pedido" to UUID.randomUUID().toString(),
            "id_pedido" to idPedido,
            "id_item" to item.idItem,
            "cod_item" to item.itemCodigo.ifBlank { item.idItem.toString() }.take(COD_ITEM_LENGTH),
            "_item_almacen" to item.itemAlmacen,
            "_item_descripcion" to item.itemDescripcion.take(DESCRIPCION_LENGTH),
            "_item_cantidad" to item.itemCantidad,
            "_item_preciosiniva" to item.itemPrecioSinIva,
            "_item_descuento" to item.itemDescuento,
            "_item_montodescuento" to item.itemMontoDescuento,
            "_item_piva" to item.itemPIva,
            "_item_totalsiniva" to item.itemTotalSinIva,
            "_item_totalconiva" to item.itemTotalConIva,
            "_cantidad_bulto" to item.cantidadBulto,
            "_unidad_empaque" to item.unidadEmpaque,
            "_posee_talla_color" to "NO",
            "_posee_serial" to "NO",
            "usuario_creacion" to username.take(DETAIL_USER_LENGTH),
            "fecha_creacion" to now,
            "_item_lista_precio" to "A",
            "_item_unidad_empaque" to item.itemUnidadEmpaque.take(UNIDAD_LENGTH),
            "_item_cantidad_total" to item.itemCantidadTotal,
            "descuento_autorizacion" to "",
            "promocion_id" to item.promocionId.ifBlank { null },
            "promocion_codigo" to item.promocionCodigo.take(PROMO_CODE_LENGTH),
            "promocion_nombre" to item.promocionNombre.take(PROMO_NAME_LENGTH),
            "promocion_grupo" to item.promocionGrupo.take(PROMO_CODE_LENGTH),
            "promocion_detalle_id" to (item.promocionDetalleId.toIntOrNull() ?: 0),
            "_item_codigo" to item.itemCodigo.take(CODE_LENGTH),
            "_item_referencia" to item.itemReferencia.take(CODE_LENGTH),
            "_cantidad_restante" to item.itemCantidad,
        )

    private companion object {
        const val SEARCH_COLUMNS = 6
        const val FORMAPAGO_LENGTH = 20
        const val USER_LENGTH = 40
        const val DETAIL_USER_LENGTH = 32
        const val OBSERVACION_LENGTH = 500
        const val FACTURAR_A_LENGTH = 60
        const val RUC_LENGTH = 50
        const val DIRECCION_LENGTH = 250
        const val SERIE_LENGTH = 10
        const val CODIGO_CAJA_LENGTH = 50
        const val COD_ITEM_LENGTH = 20
        const val DESCRIPCION_LENGTH = 100
        const val UNIDAD_LENGTH = 15
        const val PROMO_CODE_LENGTH = 15
        const val PROMO_NAME_LENGTH = 200
        const val CODE_LENGTH = 50

        val SUMMARY_SELECT =
            """
            SELECT P.id_pedido, P.cod_pedido, P.fechaPedido, P.id_cliente, P.cod_vendedor,
                   P.TotalTotalPedido, P.cantidad_items, P.cod_estatus, P.observacion, P.usuario_creacion,
                   P.facturar_a, C.cod_cliente, C.nombre, C.apellido, E.descripcion AS estatus_descripcion
            FROM pedido P
            LEFT JOIN clientes C ON C.id_cliente = P.id_cliente
            LEFT JOIN estatus E ON E.cod_estatus = P.cod_estatus
            """.trimIndent()

        val DETAIL_SELECT =
            """
            SELECT id_detalle_pedido, id_item, cod_item, _item_descripcion, _item_almacen, _item_cantidad,
                   COALESCE(_cantidad_restante, _item_cantidad) AS pendiente, _item_preciosiniva, _item_descuento,
                   _item_montodescuento, _item_piva, _item_totalsiniva, _item_totalconiva, _unidad_empaque,
                   _item_unidad_empaque, _cantidad_bulto
            FROM pedido_detalle
            WHERE id_pedido = ?
            ORDER BY fecha_creacion, id_detalle_pedido
            """.trimIndent()
    }
}

private data class PedidoHeader(
    val codPedido: String,
    val codEstatus: Int,
    val idFactura: String,
)

private fun Transaction.findPedidoHeader(
    idPedido: String,
    forUpdate: Boolean = false,
): PedidoHeader? =
    execQuery(
        "SELECT cod_pedido, cod_estatus, id_factura FROM pedido WHERE id_pedido = ?" +
            if (forUpdate) " FOR UPDATE" else "",
        listOf(idPedido),
    ) { rs ->
        PedidoHeader(
            codPedido = rs.getString("cod_pedido").orEmpty(),
            codEstatus = rs.getInt("cod_estatus"),
            idFactura = rs.getString("id_factura").orEmpty(),
        )
    }.firstOrNull()

/**
 * Enlace pedido → factura dentro de la transacción de la venta POS (equivale al bloque
 * "Pedido link" de `factura/save` del web, en su modalidad de facturación total).
 */
object PedidoInvoicing {
    /**
     * Bloquea el pedido (`FOR UPDATE`) y verifica que pueda facturarse. Un reintento de la misma
     * venta (mismo [idFactura]) sobre un pedido ya enlazado a ella se acepta (idempotencia).
     */
    fun lockForInvoicing(
        idPedido: String,
        idFactura: String,
    ) {
        val header =
            TransactionManager.current().findPedidoHeader(idPedido, forUpdate = true)
                ?: throw PedidoNotFoundException("El pedido a facturar no existe")
        val error =
            when {
                header.codEstatus == PedidoEstatus.PENDIENTE -> null
                header.codEstatus == PedidoEstatus.FACTURADO && header.idFactura == idFactura -> null
                header.codEstatus == PedidoEstatus.FACTURADO -> "El pedido ${header.codPedido} ya fue facturado"
                else -> "El pedido ${header.codPedido} está anulado"
            }
        error?.let { throw PedidoStateException(it) }
    }

    /** Marca el pedido como facturado y enlaza `factura.id_pedido` / `pedido.id_factura`. */
    fun markInvoiced(
        idPedido: String,
        idFactura: String,
        today: LocalDate,
    ) {
        val tx = TransactionManager.current()
        tx.execUpdate(
            "UPDATE pedido SET cod_estatus = ?, id_factura = ?, fecha_pago = ? WHERE id_pedido = ?",
            listOf(PedidoEstatus.FACTURADO, idFactura, today, idPedido),
        )
        tx.execUpdate("UPDATE pedido_detalle SET _cantidad_restante = 0 WHERE id_pedido = ?", listOf(idPedido))
        tx.execUpdate("UPDATE factura SET id_pedido = ? WHERE id_factura = ?", listOf(idPedido, idFactura))
        // Las reservas de stock del pedido (si el web las creó con facturar_caja=1) quedan
        // consumidas por la factura; se liberan para no bloquear existencia.
        if ("id_movimiento" in tx.columnsOf("item_precompromiso")) {
            tx.execUpdate("DELETE FROM item_precompromiso WHERE id_movimiento = ?", listOf(idPedido))
        }
    }
}

private fun ResultSet.money(column: String): Double =
    (getBigDecimal(column) ?: BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP).toDouble()

private fun mapSummary(rs: ResultSet): PedidoSummary {
    val nombre = listOfNotNull(rs.getString("nombre"), rs.getString("apellido")).joinToString(" ").trim()
    return PedidoSummary(
        idPedido = rs.getString("id_pedido"),
        codPedido = rs.getString("cod_pedido").orEmpty(),
        fecha =
            rs
                .getDate("fechaPedido")
                ?.toLocalDate()
                ?.toString()
                .orEmpty(),
        idCliente = rs.getString("id_cliente").orEmpty(),
        codCliente = rs.getString("cod_cliente").orEmpty(),
        cliente = nombre.ifBlank { rs.getString("facturar_a").orEmpty() },
        codVendedor = rs.getInt("cod_vendedor"),
        total = rs.money("TotalTotalPedido"),
        cantidadItems = rs.getBigDecimal("cantidad_items")?.toDouble() ?: 0.0,
        codEstatus = rs.getInt("cod_estatus"),
        estatus = rs.getString("estatus_descripcion").orEmpty(),
        observacion = rs.getString("observacion").orEmpty(),
        usuarioCreacion = rs.getString("usuario_creacion").orEmpty(),
    )
}

private fun mapLine(rs: ResultSet): PedidoLine =
    PedidoLine(
        idDetallePedido = rs.getString("id_detalle_pedido"),
        idItem = rs.getInt("id_item"),
        codItem = rs.getString("cod_item").orEmpty(),
        descripcion = rs.getString("_item_descripcion").orEmpty(),
        idAlmacen = rs.getInt("_item_almacen"),
        cantidad = rs.getBigDecimal("_item_cantidad")?.toDouble() ?: 0.0,
        cantidadPendiente = rs.getBigDecimal("pendiente")?.toDouble() ?: 0.0,
        precioSinIva = rs.money("_item_preciosiniva"),
        descuentoPorcentaje = rs.getBigDecimal("_item_descuento")?.toDouble() ?: 0.0,
        montoDescuento = rs.money("_item_montodescuento"),
        porcentajeIva = rs.getBigDecimal("_item_piva")?.toDouble() ?: 0.0,
        totalSinIva = rs.money("_item_totalsiniva"),
        totalConIva = rs.money("_item_totalconiva"),
        unidadEmpaque = rs.getString("_unidad_empaque").orEmpty(),
        itemUnidadEmpaque = rs.getString("_item_unidad_empaque").orEmpty().ifBlank { "UNIDAD" },
        cantidadBulto = rs.getInt("_cantidad_bulto").coerceAtLeast(1),
    )
