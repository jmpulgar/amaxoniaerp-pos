package com.amaxoniaerp.features.kiosk.data

import com.amaxoniaerp.core.database.SchemaDimensions as S
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.date
import org.jetbrains.exposed.sql.javatime.datetime

/**
 * Dispositivos Kiosco registrados y emparejados.
 */
object KioskDeviceTable : Table("kiosco_dispositivo") {
    val id = char("id", S.VARCHAR_LENGTH_36)
    val nombre = varchar("nombre", S.VARCHAR_LENGTH_80)
    val prefijoPedido = varchar("prefijo_pedido", S.VARCHAR_LENGTH_5).default("K1")
    val idCaja = varchar("id_caja", S.VARCHAR_LENGTH_36)
    val idSucursal = integer("id_sucursal")
    val idAlmacen = integer("id_almacen")
    val codVendedor = integer("cod_vendedor")
    val idClienteGenerico = varchar("id_cliente_generico", S.VARCHAR_LENGTH_36)
    val tokenHash = char("token_hash", S.VARCHAR_LENGTH_64).nullable()
    val codigoEmparejamientoHash = char("codigo_emparejamiento_hash", S.VARCHAR_LENGTH_64).nullable()
    val codigoExpiraEn = datetime("codigo_expira_en").nullable()
    val activo = bool("activo").default(true)
    val ultimoContacto = datetime("ultimo_contacto").nullable()
    val creadoEn = datetime("creado_en")

    override val primaryKey = PrimaryKey(id)

    init {
        index("ix_kiosco_dispositivo_caja", false, idCaja)
        index("ix_kiosco_dispositivo_token", false, tokenHash)
    }
}

/**
 * Medios (imágenes o videos) para el Attract Loop del kiosco.
 */
object KioskMediaTable : Table("kiosco_media") {
    val id = integer("id").autoIncrement()
    val tipo = varchar("tipo", S.VARCHAR_LENGTH_10) // 'IMAGE' o 'VIDEO'
    val archivo = varchar("archivo", S.VARCHAR_LENGTH_255)
    val orden = integer("orden").default(0)
    val duracionSeg = integer("duracion_seg").default(6)
    val activo = bool("activo").default(true)
    val updatedAt = datetime("updated_at")

    override val primaryKey = PrimaryKey(id)

    init {
        index("ix_kiosco_media_activo_orden", false, activo, orden)
    }
}

/**
 * Pedidos generados desde el kiosco (cotizados, pagados, facturados, etc.).
 */
object KioskOrderTable : Table("kiosco_pedido") {
    val id = char("id", S.VARCHAR_LENGTH_36) // = Idempotency-Key
    val idDispositivo = char("id_dispositivo", S.VARCHAR_LENGTH_36)
    val numeroPedidoDiario = integer("numero_pedido_diario")
    val codigoPedido = varchar("codigo_pedido", S.VARCHAR_LENGTH_20)
    val fecha = date("fecha")
    val estado = varchar("estado", S.VARCHAR_LENGTH_30) // COTIZADO, PAGADO, FACTURADO, PAGADO_SIN_FACTURA, ANULADO, RECHAZADO
    val modalidad = varchar("modalidad", S.VARCHAR_LENGTH_20) // COMER_AQUI, PARA_LLEVAR
    val portamesa = varchar("portamesa", S.VARCHAR_LENGTH_10).nullable()
    val idCliente = varchar("id_cliente", S.VARCHAR_LENGTH_36)
    val total = decimal("total", S.DECIMAL_PRECISION_18, S.DECIMAL_SCALE_4)
    val quoteExpiraEn = datetime("quote_expira_en")
    val pagoReferencia = varchar("pago_referencia", S.VARCHAR_LENGTH_64).nullable()
    val pagoAutorizacion = varchar("pago_autorizacion", S.VARCHAR_LENGTH_32).nullable()
    val pagoUltimos4 = char("pago_ultimos4", S.VARCHAR_LENGTH_4).nullable()
    val pagoMarca = varchar("pago_marca", S.VARCHAR_LENGTH_20).nullable()
    val idFactura = varchar("id_factura", S.VARCHAR_LENGTH_36).nullable()
    val motivoRechazo = varchar("motivo_rechazo", S.VARCHAR_LENGTH_255).nullable()
    val creadoEn = datetime("creado_en")
    val actualizadoEn = datetime("actualizado_en")

    override val primaryKey = PrimaryKey(id)

    init {
        uniqueIndex("uq_kiosco_pedido_dia", idDispositivo, fecha, numeroPedidoDiario)
        index("ix_kiosco_pedido_fecha", false, fecha)
        index("ix_kiosco_pedido_estado", false, estado)
    }
}

/**
 * Líneas de un pedido de kiosco.
 */
object KioskOrderItemTable : Table("kiosco_pedido_item") {
    val idPedido = char("id_pedido", S.VARCHAR_LENGTH_36).references(KioskOrderTable.id, onDelete = ReferenceOption.CASCADE)
    val linea = integer("linea")
    val idItem = integer("id_item")
    val cantidad = decimal("cantidad", S.DECIMAL_PRECISION_18, S.DECIMAL_SCALE_4)
    val precioUnitario = decimal("precio_unitario", S.DECIMAL_PRECISION_18, S.DECIMAL_SCALE_4)
    val nota = varchar("nota", S.VARCHAR_LENGTH_80).nullable()

    override val primaryKey = PrimaryKey(idPedido, linea)
}

/**
 * Modificadores aplicados a una línea de un pedido de kiosco.
 */
object KioskOrderItemModifierTable : Table("kiosco_pedido_item_modificador") {
    val idPedido = char("id_pedido", S.VARCHAR_LENGTH_36)
    val linea = integer("linea")
    val idModificador = integer("id_modificador")
    val nombre = varchar("nombre", S.VARCHAR_LENGTH_80)
    val precioAdicional = decimal("precio_adicional", S.DECIMAL_PRECISION_18, S.DECIMAL_SCALE_4)

    override val primaryKey = PrimaryKey(idPedido, linea, idModificador)

    init {
        index("ix_kiosco_pedido_item_mod", false, idPedido, linea)
    }
}

/**
 * Vista de lectura/escritura de configuración del Kiosco sobre parametros_generales.
 */
object KioskParametrosTable : Table("parametros_generales") {
    val codEmpresa = integer("cod_empresa")
    val defaultCodClienteFactura = varchar("default_cod_cliente_factura", S.VARCHAR_LENGTH_80)
    val kioscoDestinoPedido = varchar("kiosco_destino_pedido", S.VARCHAR_LENGTH_30).default("RETIRO_MOSTRADOR")
    val kioscoImpresoraCocinaIp = varchar("kiosco_impresora_cocina_ip", S.VARCHAR_LENGTH_45).nullable()
    val kioscoModalidades = varchar("kiosco_modalidades", S.VARCHAR_LENGTH_30).default("COMER_AQUI,PARA_LLEVAR")
    val kioscoColorMarca = varchar("kiosco_color_marca", 7).nullable()
    val kioscoConfigVersion = integer("kiosco_config_version").default(1)
    val claveKiosko = varchar("clave_kiosko", S.VARCHAR_LENGTH_255).nullable()
}
