package com.amaxoniaerp.features.mesas.data

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.datetime

/**
 * Cuenta de mesa (o división de cuenta). Cada fila representa un "ticket" pendiente de pago
 * dentro de una sesión: la cuenta completa o una división por producto/cantidad.
 *
 * El saldo de cada [PedidoMesaTable] se reparte entre las cuentas ACTIVAS de su sesión: la
 * suma de `cuenta_mesa_detalle.cantidad` para un `pedido_mesa_id` no puede superar
 * `pedido_mesa.item_cantidad - pedido_mesa.cantidad_facturada`.
 *
 * Modelo (ver migración `003_cuenta_mesa.sql`):
 * - `numero_cuenta` distingue divisiones sucesivas dentro de la misma sesión.
 * - `estado` refleja [com.amaxoniaerp.features.mesas.domain.EstadoCuentaMesa].
 * - `id_factura`/`cod_factura` se rellenan dentro de la transacción estándar de venta.
 * - `saldo_restante` es el total reservado mientras está ACTIVA y 0 cuando queda PAGADA o
 *   CANCELADA. Los pagos parciales de la sesión se representan con cuentas consecutivas.
 */
object CuentaMesaTable : Table("cuenta_mesa") {
    val id = integer("id").autoIncrement("seq_cuenta_mesa")
    val sesionMesaId = integer("sesion_mesa_id")
    val numeroCuenta = integer("numero_cuenta").default(1)
    val estado = varchar("estado", 20).default(EstadoCuentaMesaDefault.ACTIVA)
    val subtotal = decimal("subtotal", 20, 2).default(0.toBigDecimal())
    val descuento = decimal("descuento", 20, 2).default(0.toBigDecimal())
    val impuesto = decimal("impuesto", 20, 2).default(0.toBigDecimal())
    val total = decimal("total", 20, 2).default(0.toBigDecimal())
    val saldoRestante = decimal("saldo_restante", 20, 2).default(0.toBigDecimal())
    val idFactura = varchar("id_factura", 64).nullable()
    val codFactura = varchar("cod_factura", 64).nullable()
    val fechaFactura = datetime("fecha_factura").nullable()
    val fechaCreacion = datetime("fecha_creacion")
    val fechaCierre = datetime("fecha_cierre").nullable()
    val activo = integer("activo").default(1)

    override val primaryKey = PrimaryKey(id)

    init {
        // uniqueness del número de cuenta dentro de la sesión + estado (permite reciclar
        // número tras CANCELADA si fuera necesario, aunque la práctica recomendada es alta-
        // siempre).
        uniqueIndex("uq_cuenta_mesa_numero", sesionMesaId, numeroCuenta, estado)
        index("ix_cuenta_mesa_sesion", false, sesionMesaId, estado)
    }
}

/**
 * Líneas de una cuenta. Cada fila copia el snapshot del `pedido_mesa` original
 * (precio/iva/descuento freezados) y la cantidad cubierta por esta cuenta. La diferencia
 * con el `pedido_mesa` original permanece cobrable desde otra división.
 *
 * El campo `facturado = 1` se activa en la misma transacción que inserta la factura, junto con
 * `pedido_mesa.cantidad_facturada += cantidad` y la asociación de `cuenta_mesa.id_factura`.
 */
object CuentaMesaDetalleTable : Table("cuenta_mesa_detalle") {
    val id = integer("id").autoIncrement("seq_cuenta_mesa_detalle")
    val cuentaMesaId = integer("cuenta_mesa_id")
    val pedidoMesaId = integer("pedido_mesa_id")
    val productoId = integer("producto_id")
    val itemAlmacen = integer("item_almacen").default(1)
    val itemCodigo = varchar("item_codigo", 80).default("")
    val itemDescripcion = varchar("item_descripcion", 500)
    val cantidad = decimal("cantidad", 32, 3)
    val itemPrecioSinIva = decimal("item_preciosiniva", 20, 2)
    val itemDescuento = decimal("item_descuento", 10, 2).default(0.toBigDecimal())
    val itemMontoDescuento = decimal("item_montodescuento", 20, 2).default(0.toBigDecimal())
    val itemPIva = decimal("item_piva", 10, 2).default(0.toBigDecimal())
    val itemTotalSinIva = decimal("item_totalsiniva", 20, 2)
    val itemTotalConIva = decimal("item_totalconiva", 20, 2)
    val facturado = integer("facturado").default(0)
    val fechaCreacion = datetime("fecha_creacion")

    override val primaryKey = PrimaryKey(id)

    init {
        // Una línea de pedido solo puede aparecer una vez por cuenta (pero sí en varias
        // cuentas distintas para divisiones por cantidad).
        uniqueIndex("uq_cuenta_mesa_detalle_linea", cuentaMesaId, pedidoMesaId)
        index("ix_cuenta_detalle_pedido", false, pedidoMesaId)
        index("ix_cuenta_detalle_cuenta", false, cuentaMesaId)
    }
}

/**
 * Registro idempotente del intento de facturación de una cuenta. Garantiza que un reintento
 * accidental (timeout de red, doble tap del operario) NO provoque doble cobro ni doble
 * decremento de saldos.
 *
 * El POS usa `mesa-{sesionId}-cuenta-{cuentaId}` como `idFactura`. El pipeline estándar de
 * ventas crea SENDING y lo confirma junto con factura, cantidades y cierre. Un retry obtiene
 * 409 por factura duplicada y reconcilia el registro autoritativo sin crear otra venta.
 */
object CuentaMesaIdempotenciaTable : Table("cuenta_mesa_idempotencia") {
    val idempotencyKey = varchar("idempotency_key", 64)
    val cuentaMesaId = integer("cuenta_mesa_id")
    val sesionMesaId = integer("sesion_mesa_id")
    val estado = varchar("estado", 20).default(EstadoCuentaIdempotenciaDefault.SENDING)
    val idFacturaResultado = varchar("id_factura_resultado", 64).nullable()
    val codFacturaResultado = varchar("cod_factura_resultado", 64).nullable()
    val errorMensaje = varchar("error_mensaje", 500).nullable()
    val intentos = integer("intentos").default(0)
    val fechaPrimerIntento = datetime("fecha_primer_intento")
    val fechaUltimoIntento = datetime("fecha_ultimo_intento").nullable()

    override val primaryKey = PrimaryKey(idempotencyKey)

    init {
        index("ix_cuenta_idem_cuenta", false, cuentaMesaId)
        index("ix_cuenta_idem_sesion", false, sesionMesaId)
    }
}

/** Constantes almacenadas en DB. Reflejan los códigos de los enums del domain. */
object EstadoCuentaMesaDefault {
    const val ACTIVA = "ACTIVA"
    const val PAGADA = "PAGADA"
    const val CANCELADA = "CANCELADA"
}

object EstadoCuentaIdempotenciaDefault {
    const val SENDING = "SENDING"
    const val CONFIRMED = "CONFIRMED"
    const val FAILED = "FAILED"
}
