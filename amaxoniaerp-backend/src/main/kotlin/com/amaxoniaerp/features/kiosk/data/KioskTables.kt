package com.amaxoniaerp.features.kiosk.data

import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.date
import org.jetbrains.exposed.sql.javatime.datetime
import com.amaxoniaerp.core.database.SchemaDimensions as S

/**
 * Pedidos generados desde el kiosco (cotizados, pagados, facturados, etc.).
 *
 * Las tablas `kiosco_pedido*` las crea la migración del administrativo; si un tenant aún no
 * las tiene, el kiosco responde 503 (ver [KioskSchemaInspector.hasKioskOrderTables]).
 * `id_dispositivo` guarda el `idCaja` del kiosco: la numeración diaria es por caja y fecha.
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

    // Yappy reutiliza las columnas de pago (sin cambio de esquema): pago_marca = 'YAPPY' y
    // pago_referencia = transactionId desde que se genera el QR (ver KioskOrderRecord.pagoMetodo).
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
 * Columnas de `parametros_generales` que usa el kiosco. Todas salvo `cod_empresa` y
 * `default_cod_cliente_factura` son opcionales en el tenant: solo se leen con `select`
 * explícito de las columnas detectadas por [KioskSchemaInspector] (ver [KioskSettingsRepository]),
 * nunca con `selectAll()`.
 */
object KioskParametrosTable : Table("parametros_generales") {
    val codEmpresa = integer("cod_empresa")
    val defaultCodClienteFactura = varchar("default_cod_cliente_factura", S.VARCHAR_LENGTH_80).nullable()
    val kioscoDestinoPedido = varchar("kiosco_destino_pedido", S.VARCHAR_LENGTH_30).nullable()
    val kioscoImpresoraCocinaIp = varchar("kiosco_impresora_cocina_ip", S.VARCHAR_LENGTH_45).nullable()
    val kioscoModalidades = varchar("kiosco_modalidades", S.VARCHAR_LENGTH_30).nullable()
    val claveKiosko = varchar("clave_kiosko", S.VARCHAR_LENGTH_255).nullable()
    val banner1 = varchar("banner_1", S.VARCHAR_LENGTH_255).nullable()
    val banner2 = varchar("banner_2", S.VARCHAR_LENGTH_255).nullable()
    val banner3 = varchar("banner_3", S.VARCHAR_LENGTH_255).nullable()
    val menu1 = varchar("menu_1", S.VARCHAR_LENGTH_255).nullable()
}

/**
 * Proyección de solo lectura de las credenciales Yappy que el administrativo PHP guarda en
 * parametros_generales (2025-08-27-ALTER-TABLE-YAPPY.sql y 2025-09-13-...-YAPPY.sql).
 *
 * Se mapea aparte de `ParametrosGeneralesTablePA` porque esa tabla se lee con `selectAll()`
 * en la venta y en compañías: sumarle estas columnas rompería esas lecturas en tenants que
 * aún no tengan las columnas Yappy.
 */
object KioskYappyParametrosTable : Table("parametros_generales") {
    val codEmpresa = integer("cod_empresa")
    val yappyApiKey = varchar("yappy_api_key", S.VARCHAR_LENGTH_255).nullable()
    val yappySecretKey = varchar("yappy_secret_key", S.VARCHAR_LENGTH_255).nullable()
    val yappyModoProduccion = integer("yappy_modo_produccion").default(0)
    val yappyEndpointProd = varchar("yappy_endpoint_prod", S.VARCHAR_LENGTH_255).nullable()
    val yappyEndpointSandbox = varchar("yappy_endpoint_sandbox", S.VARCHAR_LENGTH_255).nullable()
    val yappyIdUnidad = varchar("yappy_id_unidad", S.VARCHAR_LENGTH_100).nullable()
    val yappyIdGrupo = varchar("yappy_id_grupo", S.VARCHAR_LENGTH_100).nullable()
}

/**
 * Proyección opcional de `parametros_generales.yappy_tipo_qr` (DYN|HYB). La columna NO es
 * obligatoria (la agrega la migración del kiosco del administrativo): solo se consulta con un `select`
 * explícito después de comprobar que existe en el tenant, nunca con `selectAll()`.
 */
object KioskYappyQrTypeParametrosTable : Table("parametros_generales") {
    val codEmpresa = integer("cod_empresa")
    val yappyTipoQr = varchar("yappy_tipo_qr", 3).nullable()
}
