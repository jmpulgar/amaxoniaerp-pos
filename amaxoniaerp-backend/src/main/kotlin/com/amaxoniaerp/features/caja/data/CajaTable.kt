package com.amaxoniaerp.features.caja.data

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.datetime

private const val SCHEMA_CAJA_MAX_LENGTH = 50
private const val SCHEMA_CODIGO_MAX_LENGTH_10 = 10
private const val SCHEMA_CODIGO_MAX_LENGTH_50 = 50
private const val SCHEMA_CODIGO_SUCURSAL_EMISOR_MAX_LENGTH = 20
private const val SCHEMA_COD_USUARIOS_MAX_LENGTH = 50
private const val SCHEMA_DENOMINACION_MAX_LENGTH = 80
private const val SCHEMA_DESCRIPCION_MAX_LENGTH_100 = 100
private const val SCHEMA_DESCRIPCION_MAX_LENGTH_150 = 150
private const val SCHEMA_FONDO_APERTURA_PRECISION = 10
private const val SCHEMA_GRUPO_MAX_LENGTH = 100
private const val SCHEMA_ID_CAJAS_MAX_LENGTH = 500
private const val SCHEMA_ID_CAJA_MAX_LENGTH = 36
private const val SCHEMA_ID_CAJA_SECUENCIA_MAX_LENGTH = 36
private const val SCHEMA_ID_MAX_LENGTH = 36
private const val SCHEMA_ID_SECUENCIA_MAX_LENGTH = 36
private const val SCHEMA_ID_TIENDAS_MAX_LENGTH = 50
private const val SCHEMA_IMPRESORA_MODELO_MAX_LENGTH = 50
private const val SCHEMA_MONTO_CIERRE_PRECISION = 10
private const val SCHEMA_MONTO_DIFERENCIA_PRECISION = 10
private const val SCHEMA_MONTO_EFECTIVO_APERTURA_PRECISION = 10
private const val SCHEMA_MONTO_EFECTIVO_CIERRE_PRECISION = 10
private const val SCHEMA_MONTO_EFECTIVO_DIFERENCIA_PRECISION = 10
private const val SCHEMA_MONTO_EFECTIVO_ENTRADA_PRECISION = 10
private const val SCHEMA_MONTO_EFECTIVO_SALIDA_PRECISION = 10
private const val SCHEMA_MONTO_EFECTIVO_TOTAL_PRECISION = 10
private const val SCHEMA_MONTO_EFECTIVO_VENTAS_PRECISION = 10
private const val SCHEMA_MONTO_OTROS_CIERRE_PRECISION = 10
private const val SCHEMA_MONTO_OTROS_DIFERENCIA_PRECISION = 10
private const val SCHEMA_MONTO_OTROS_TOTAL_PRECISION = 10
private const val SCHEMA_MONTO_PRECISION = 10
private const val SCHEMA_MONTO_TOTAL_PRECISION = 10
private const val SCHEMA_MONTO_VENTAS_PRECISION = 10
private const val SCHEMA_NOMBRE_MAX_LENGTH = 50
private const val SCHEMA_NUMERO_CIERRE_FISCAL_MAX_LENGTH = 50
private const val SCHEMA_OBSERVACION_APERTURA_MAX_LENGTH = 300
private const val SCHEMA_OBSERVACION_CIERRE_MAX_LENGTH = 300
private const val SCHEMA_SECUENCIA_MAX_LENGTH = 10
private const val SCHEMA_SERIAL_FISCAL_MAX_LENGTH = 50
private const val SCHEMA_SERIE_CAJA_MAX_LENGTH = 10
private const val SCHEMA_SERIE_MAX_LENGTH = 10
private const val SCHEMA_SERIE_SUCURSAL_MAX_LENGTH = 10
private const val SCHEMA_SUCURSAL_MAX_LENGTH = 100
private const val SCHEMA_TOTAL_PRECISION = 10
private const val SCHEMA_TOTAL_TOTAL_FACTURA_PRECISION = 10
private const val SCHEMA_USUARIO_CONTABILIZACION_MAX_LENGTH = 50
private const val SCHEMA_USUARIO_MAX_LENGTH = 10
private const val SCHEMA_VALOR_PRECISION = 10

object CajaTable : Table("caja") {
    val idCaja = varchar("id", SCHEMA_ID_MAX_LENGTH)
    val codCaja = varchar("codigo", SCHEMA_CODIGO_MAX_LENGTH_50).nullable()
    val descripcion = varchar("descripcion", SCHEMA_DESCRIPCION_MAX_LENGTH_100).nullable()
    val codEstatus = integer("activo").default(1)
    val idSucursal = integer("id_sucursal").nullable()
    val codAlmacen = integer("cod_almacen").nullable()
    val serieCaja = varchar("serie_caja", SCHEMA_SERIE_CAJA_MAX_LENGTH)
    val caja = varchar("caja", SCHEMA_CAJA_MAX_LENGTH).nullable()
    val fondoApertura = decimal("fondo_apertura", SCHEMA_FONDO_APERTURA_PRECISION, 2).nullable()
    val impresoraModelo = varchar("impresora_modelo", SCHEMA_IMPRESORA_MODELO_MAX_LENGTH).nullable()

    override val primaryKey = PrimaryKey(idCaja)
}

object SucursalTable : Table("sucursal") {
    val idSucursal = integer("id")
    val codigo = varchar("codigo", SCHEMA_CODIGO_MAX_LENGTH_10).nullable()
    val serie = varchar("serie", SCHEMA_SERIE_MAX_LENGTH).nullable()
    val codigoSucursalEmisor = varchar("codigo_sucursal_emisor", SCHEMA_CODIGO_SUCURSAL_EMISOR_MAX_LENGTH).nullable()
    val sucursal = varchar("sucursal", SCHEMA_SUCURSAL_MAX_LENGTH).nullable()
    val descripcion = varchar("descripcion", SCHEMA_DESCRIPCION_MAX_LENGTH_150).nullable()

    override val primaryKey = PrimaryKey(idSucursal)
}

object SucursalAlmacenTable : Table("sucursal_almacen") {
    val idSucursal = integer("id_sucursal")
    val idAlmacen = integer("id_almacen")
    val defaultVentas = integer("default_ventas").nullable()

    override val primaryKey = PrimaryKey(idSucursal, idAlmacen)
}

object VendedorTable : Table("vendedor") {
    val idVendedor = integer("id_vendedor")
    val codVendedor = integer("cod_vendedor")
    val nombre = varchar("nombre", SCHEMA_NOMBRE_MAX_LENGTH)
    val codUsuarios = varchar("cod_usuarios", SCHEMA_COD_USUARIOS_MAX_LENGTH)
    val idTiendas = varchar("id_tiendas", SCHEMA_ID_TIENDAS_MAX_LENGTH)
    val idCajas = varchar("id_cajas", SCHEMA_ID_CAJAS_MAX_LENGTH).nullable()
    val activo = integer("activo")

    override val primaryKey = PrimaryKey(idVendedor)
}

object CajaSecuenciaTable : Table("caja_secuencia") {
    // AQUÍ ESTABA EL ERROR: El esquema SQL dice que la llave primaria es `id`
    val idCajaSecuencia = varchar("id", SCHEMA_ID_MAX_LENGTH)
    val idCaja = varchar("id_caja", SCHEMA_ID_CAJA_MAX_LENGTH)

    // Y la columna estado no se llamaba estado sino "contabilizado" o simplemente no tiene "estado" pero sí tiene
    // "activo"
    // Según tu esquema SQL enviado la llave se llama id.
    val idVendedor = integer("id_vendedor").nullable()
    val secuencia = varchar("secuencia", SCHEMA_SECUENCIA_MAX_LENGTH).nullable()

    val fechaApertura = datetime("fecha_apertura").nullable()
    val fechaCierre = datetime("fecha_cierre").nullable()
    val fechaCreacion = datetime("fecha_creacion").nullable()

    // Montos base
    val montoEfectivoApertura =
        decimal(
            "monto_efectivo_apertura",
            SCHEMA_MONTO_EFECTIVO_APERTURA_PRECISION,
            2,
        ).default(java.math.BigDecimal.ZERO)
    val montoEfectivoVentas = decimal("monto_efectivo_ventas", SCHEMA_MONTO_EFECTIVO_VENTAS_PRECISION, 2).default(java.math.BigDecimal.ZERO)
    val montoEfectivoEntrada =
        decimal(
            "monto_efectivo_entrada",
            SCHEMA_MONTO_EFECTIVO_ENTRADA_PRECISION,
            2,
        ).default(java.math.BigDecimal.ZERO)
    val montoEfectivoSalida = decimal("monto_efectivo_salida", SCHEMA_MONTO_EFECTIVO_SALIDA_PRECISION, 2).default(java.math.BigDecimal.ZERO)
    val montoEfectivoTotal = decimal("monto_efectivo_total", SCHEMA_MONTO_EFECTIVO_TOTAL_PRECISION, 2).default(java.math.BigDecimal.ZERO)
    val montoEfectivoCierre =
        decimal(
            "monto_efectivo_cierre",
            SCHEMA_MONTO_EFECTIVO_CIERRE_PRECISION,
            2,
        ).nullable().default(java.math.BigDecimal.ZERO)
    val montoEfectivoDiferencia =
        decimal(
            "monto_efectivo_diferencia",
            SCHEMA_MONTO_EFECTIVO_DIFERENCIA_PRECISION,
            2,
        ).default(java.math.BigDecimal.ZERO)
    val montoOtrosTotal = decimal("monto_otros_total", SCHEMA_MONTO_OTROS_TOTAL_PRECISION, 2).default(java.math.BigDecimal.ZERO)
    val montoOtrosCierre = decimal("monto_otros_cierre", SCHEMA_MONTO_OTROS_CIERRE_PRECISION, 2).default(java.math.BigDecimal.ZERO)
    val montoOtrosDiferencia =
        decimal(
            "monto_otros_diferencia",
            SCHEMA_MONTO_OTROS_DIFERENCIA_PRECISION,
            2,
        ).default(java.math.BigDecimal.ZERO)
    val montoTotal = decimal("monto_total", SCHEMA_MONTO_TOTAL_PRECISION, 2).default(java.math.BigDecimal.ZERO)
    val montoCierre = decimal("monto_cierre", SCHEMA_MONTO_CIERRE_PRECISION, 2).default(java.math.BigDecimal.ZERO)
    val montoDiferencia = decimal("monto_diferencia", SCHEMA_MONTO_DIFERENCIA_PRECISION, 2).default(java.math.BigDecimal.ZERO)
    val numeroCierreFiscal = varchar("numero_cierre_fiscal", SCHEMA_NUMERO_CIERRE_FISCAL_MAX_LENGTH).nullable()

    // Resto
    val usuario = varchar("usuario", SCHEMA_USUARIO_MAX_LENGTH).nullable()
    val serieSucursal = varchar("serie_sucursal", SCHEMA_SERIE_SUCURSAL_MAX_LENGTH)
    val contabilizado = integer("contabilizado").default(0)

    // Los campos faltantes que hacían fallar la inserción (serial_fiscal, observacion_apertura, observacion_cierre)
    val serialFiscal = varchar("serial_fiscal", SCHEMA_SERIAL_FISCAL_MAX_LENGTH).default("")
    val observacionApertura = varchar("observacion_apertura", SCHEMA_OBSERVACION_APERTURA_MAX_LENGTH).default("")
    val observacionCierre = varchar("observacion_cierre", SCHEMA_OBSERVACION_CIERRE_MAX_LENGTH).default("")
    val usuarioContabilizacion = varchar("usuario_contabilizacion", SCHEMA_USUARIO_CONTABILIZACION_MAX_LENGTH).default("")
    val fechaContabilizacion = datetime("fecha_contabilizacion").defaultExpression(org.jetbrains.exposed.sql.javatime.CurrentDateTime)

    override val primaryKey = PrimaryKey(idCajaSecuencia)
}

object CajaDetalleCierreTable : Table("caja_detalle_cierre") {
    val id = varchar("id", SCHEMA_ID_MAX_LENGTH)
    val idSecuencia = varchar("id_secuencia", SCHEMA_ID_SECUENCIA_MAX_LENGTH)
    val idMonedaDenominacion = integer("id_moneda_denominacion").nullable()
    val cantidad = integer("cantidad").default(0)
    val valor = decimal("valor", SCHEMA_VALOR_PRECISION, 2).default(java.math.BigDecimal.ZERO)
    val monto = decimal("monto", SCHEMA_MONTO_PRECISION, 2).default(java.math.BigDecimal.ZERO)
    val serieSucursal = varchar("serie_sucursal", SCHEMA_SERIE_SUCURSAL_MAX_LENGTH)

    override val primaryKey = PrimaryKey(id)
}

object CajaDetalleCierreFormaPagoTable : Table("caja_detalle_cierre_formapago") {
    val id = varchar("id", SCHEMA_ID_MAX_LENGTH)
    val idSecuencia = varchar("id_secuencia", SCHEMA_ID_SECUENCIA_MAX_LENGTH)
    val idFormaPago = integer("id_forma_pago").nullable()
    val montoVentas = decimal("monto_ventas", SCHEMA_MONTO_VENTAS_PRECISION, 2).default(java.math.BigDecimal.ZERO)
    val montoCierre = decimal("monto_cierre", SCHEMA_MONTO_CIERRE_PRECISION, 2).default(java.math.BigDecimal.ZERO)
    val montoDiferencia = decimal("monto_diferencia", SCHEMA_MONTO_DIFERENCIA_PRECISION, 2).default(java.math.BigDecimal.ZERO)
    val serieSucursal = varchar("serie_sucursal", SCHEMA_SERIE_SUCURSAL_MAX_LENGTH)

    override val primaryKey = PrimaryKey(id)
}

object MonedaDenominacionTable : Table("moneda_denominacion") {
    val id = integer("id")
    val denominacion = varchar("denominacion", SCHEMA_DENOMINACION_MAX_LENGTH).nullable()

    override val primaryKey = PrimaryKey(id)
}

object CajaMovimientoTable : Table("caja_movimiento") {
    val id = varchar("id", SCHEMA_ID_MAX_LENGTH)
    val idSecuencia = varchar("id_secuencia", SCHEMA_ID_SECUENCIA_MAX_LENGTH)
    val tipo = varchar("tipo", 1)
    val total = decimal("total", SCHEMA_TOTAL_PRECISION, 2).default(java.math.BigDecimal.ZERO)

    override val primaryKey = PrimaryKey(id)
}

object FacturaDevolucionTable : Table("factura_devolucion") {
    val id = varchar("id", SCHEMA_ID_MAX_LENGTH)
    val idCajaSecuencia = varchar("id_caja_secuencia", SCHEMA_ID_CAJA_SECUENCIA_MAX_LENGTH)
    val idFormaPago = integer("id_forma_pago").nullable()
    val totalTotalFactura = decimal("TotalTotalFactura", SCHEMA_TOTAL_TOTAL_FACTURA_PRECISION, 2).nullable()

    override val primaryKey = PrimaryKey(id)
}

object CajaFormaPagoGrupoTable : Table("caja_forma_pago_grupo") {
    val id = integer("id")
    val grupo = varchar("grupo", SCHEMA_GRUPO_MAX_LENGTH).nullable()
    val imagen = text("imagen").nullable()
    val orden = integer("orden").nullable()
    val activo = integer("activo").nullable()

    override val primaryKey = PrimaryKey(id)
}

object CajaDetalleAperturaTable : Table("caja_detalle_apertura") {
    val idDetalleApertura = varchar("id", SCHEMA_ID_MAX_LENGTH)
    val idCajaSecuencia = varchar("id_secuencia", SCHEMA_ID_SECUENCIA_MAX_LENGTH)
    val idMonedaDenominacion = integer("id_moneda_denominacion").nullable()
    val cantidad = integer("cantidad").default(0)
    val valor = decimal("valor", SCHEMA_VALOR_PRECISION, 2).default(java.math.BigDecimal.ZERO)
    val monto = decimal("monto", SCHEMA_MONTO_PRECISION, 2).default(java.math.BigDecimal.ZERO)
    val serieSucursal = varchar("serie_sucursal", SCHEMA_SERIE_SUCURSAL_MAX_LENGTH)

    override val primaryKey = PrimaryKey(idDetalleApertura)
}
