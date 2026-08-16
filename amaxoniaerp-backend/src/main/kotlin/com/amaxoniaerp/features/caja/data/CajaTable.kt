package com.amaxoniaerp.features.caja.data

import com.amaxoniaerp.core.database.SchemaDimensions
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.datetime

object CajaTable : Table("caja") {
    val idCaja = varchar("id", SchemaDimensions.VARCHAR_LENGTH_36)
    val codCaja = varchar("codigo", SchemaDimensions.VARCHAR_LENGTH_50).nullable()
    val descripcion = varchar("descripcion", SchemaDimensions.VARCHAR_LENGTH_100).nullable()
    val codEstatus = integer("activo").default(1)
    val idSucursal = integer("id_sucursal").nullable()
    val codAlmacen = integer("cod_almacen").nullable()
    val serieCaja = varchar("serie_caja", SchemaDimensions.VARCHAR_LENGTH_10)
    val caja = varchar("caja", SchemaDimensions.VARCHAR_LENGTH_50).nullable()
    val fondoApertura = decimal("fondo_apertura", SchemaDimensions.DECIMAL_PRECISION_10, 2).nullable()
    val impresoraModelo = varchar("impresora_modelo", SchemaDimensions.VARCHAR_LENGTH_50).nullable()

    override val primaryKey = PrimaryKey(idCaja)
}

object SucursalTable : Table("sucursal") {
    val idSucursal = integer("id")
    val codigo = varchar("codigo", SchemaDimensions.VARCHAR_LENGTH_10).nullable()
    val serie = varchar("serie", SchemaDimensions.VARCHAR_LENGTH_10).nullable()
    val codigoSucursalEmisor = varchar("codigo_sucursal_emisor", SchemaDimensions.VARCHAR_LENGTH_20).nullable()
    val sucursal = varchar("sucursal", SchemaDimensions.VARCHAR_LENGTH_100).nullable()
    val descripcion = varchar("descripcion", SchemaDimensions.VARCHAR_LENGTH_150).nullable()

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
    val nombre = varchar("nombre", SchemaDimensions.VARCHAR_LENGTH_50)
    val codUsuarios = varchar("cod_usuarios", SchemaDimensions.VARCHAR_LENGTH_50)
    val idTiendas = varchar("id_tiendas", SchemaDimensions.VARCHAR_LENGTH_50)
    val idCajas = varchar("id_cajas", SchemaDimensions.VARCHAR_LENGTH_500).nullable()
    val activo = integer("activo")

    override val primaryKey = PrimaryKey(idVendedor)
}

object CajaSecuenciaTable : Table("caja_secuencia") {
    // AQUÍ ESTABA EL ERROR: El esquema SQL dice que la llave primaria es `id`
    val idCajaSecuencia = varchar("id", SchemaDimensions.VARCHAR_LENGTH_36)
    val idCaja = varchar("id_caja", SchemaDimensions.VARCHAR_LENGTH_36)

    // Y la columna estado no se llamaba estado sino "contabilizado".
    // También puede no tener "estado", pero sí "activo".
    // Según tu esquema SQL enviado la llave se llama id.
    val idVendedor = integer("id_vendedor").nullable()
    val secuencia = varchar("secuencia", SchemaDimensions.VARCHAR_LENGTH_10).nullable()

    val fechaApertura = datetime("fecha_apertura").nullable()
    val fechaCierre = datetime("fecha_cierre").nullable()
    val fechaCreacion = datetime("fecha_creacion").nullable()

    // Montos base
    val montoEfectivoApertura =
        decimal(
            "monto_efectivo_apertura",
            SchemaDimensions.DECIMAL_PRECISION_10,
            2,
        ).default(java.math.BigDecimal.ZERO)
    val montoEfectivoVentas = decimal("monto_efectivo_ventas", SchemaDimensions.DECIMAL_PRECISION_10, 2).default(java.math.BigDecimal.ZERO)
    val montoEfectivoEntrada =
        decimal(
            "monto_efectivo_entrada",
            SchemaDimensions.DECIMAL_PRECISION_10,
            2,
        ).default(java.math.BigDecimal.ZERO)
    val montoEfectivoSalida = decimal("monto_efectivo_salida", SchemaDimensions.DECIMAL_PRECISION_10, 2).default(java.math.BigDecimal.ZERO)
    val montoEfectivoTotal = decimal("monto_efectivo_total", SchemaDimensions.DECIMAL_PRECISION_10, 2).default(java.math.BigDecimal.ZERO)
    val montoEfectivoCierre =
        decimal(
            "monto_efectivo_cierre",
            SchemaDimensions.DECIMAL_PRECISION_10,
            2,
        ).nullable().default(java.math.BigDecimal.ZERO)
    val montoEfectivoDiferencia =
        decimal(
            "monto_efectivo_diferencia",
            SchemaDimensions.DECIMAL_PRECISION_10,
            2,
        ).default(java.math.BigDecimal.ZERO)
    val montoOtrosTotal = decimal("monto_otros_total", SchemaDimensions.DECIMAL_PRECISION_10, 2).default(java.math.BigDecimal.ZERO)
    val montoOtrosCierre = decimal("monto_otros_cierre", SchemaDimensions.DECIMAL_PRECISION_10, 2).default(java.math.BigDecimal.ZERO)
    val montoOtrosDiferencia =
        decimal(
            "monto_otros_diferencia",
            SchemaDimensions.DECIMAL_PRECISION_10,
            2,
        ).default(java.math.BigDecimal.ZERO)
    val montoTotal = decimal("monto_total", SchemaDimensions.DECIMAL_PRECISION_10, 2).default(java.math.BigDecimal.ZERO)
    val montoCierre = decimal("monto_cierre", SchemaDimensions.DECIMAL_PRECISION_10, 2).default(java.math.BigDecimal.ZERO)
    val montoDiferencia = decimal("monto_diferencia", SchemaDimensions.DECIMAL_PRECISION_10, 2).default(java.math.BigDecimal.ZERO)
    val numeroCierreFiscal = varchar("numero_cierre_fiscal", SchemaDimensions.VARCHAR_LENGTH_50).nullable()

    // Resto
    val usuario = varchar("usuario", SchemaDimensions.VARCHAR_LENGTH_10).nullable()
    val serieSucursal = varchar("serie_sucursal", SchemaDimensions.VARCHAR_LENGTH_10)
    val contabilizado = integer("contabilizado").default(0)

    // Los campos faltantes que hacían fallar la inserción (serial_fiscal, observacion_apertura, observacion_cierre)
    val serialFiscal = varchar("serial_fiscal", SchemaDimensions.VARCHAR_LENGTH_50).default("")
    val observacionApertura = varchar("observacion_apertura", SchemaDimensions.VARCHAR_LENGTH_300).default("")
    val observacionCierre = varchar("observacion_cierre", SchemaDimensions.VARCHAR_LENGTH_300).default("")
    val usuarioContabilizacion = varchar("usuario_contabilizacion", SchemaDimensions.VARCHAR_LENGTH_50).default("")
    val fechaContabilizacion = datetime("fecha_contabilizacion").defaultExpression(org.jetbrains.exposed.sql.javatime.CurrentDateTime)

    override val primaryKey = PrimaryKey(idCajaSecuencia)
}

object CajaDetalleCierreTable : Table("caja_detalle_cierre") {
    val id = varchar("id", SchemaDimensions.VARCHAR_LENGTH_36)
    val idSecuencia = varchar("id_secuencia", SchemaDimensions.VARCHAR_LENGTH_36)
    val idMonedaDenominacion = integer("id_moneda_denominacion").nullable()
    val cantidad = integer("cantidad").default(0)
    val valor = decimal("valor", SchemaDimensions.DECIMAL_PRECISION_10, 2).default(java.math.BigDecimal.ZERO)
    val monto = decimal("monto", SchemaDimensions.DECIMAL_PRECISION_10, 2).default(java.math.BigDecimal.ZERO)
    val serieSucursal = varchar("serie_sucursal", SchemaDimensions.VARCHAR_LENGTH_10)

    override val primaryKey = PrimaryKey(id)
}

object CajaDetalleCierreFormaPagoTable : Table("caja_detalle_cierre_formapago") {
    val id = varchar("id", SchemaDimensions.VARCHAR_LENGTH_36)
    val idSecuencia = varchar("id_secuencia", SchemaDimensions.VARCHAR_LENGTH_36)
    val idFormaPago = integer("id_forma_pago").nullable()
    val montoVentas = decimal("monto_ventas", SchemaDimensions.DECIMAL_PRECISION_10, 2).default(java.math.BigDecimal.ZERO)
    val montoCierre = decimal("monto_cierre", SchemaDimensions.DECIMAL_PRECISION_10, 2).default(java.math.BigDecimal.ZERO)
    val montoDiferencia = decimal("monto_diferencia", SchemaDimensions.DECIMAL_PRECISION_10, 2).default(java.math.BigDecimal.ZERO)
    val serieSucursal = varchar("serie_sucursal", SchemaDimensions.VARCHAR_LENGTH_10)

    override val primaryKey = PrimaryKey(id)
}

object MonedaDenominacionTable : Table("moneda_denominacion") {
    val id = integer("id")
    val denominacion = varchar("denominacion", SchemaDimensions.VARCHAR_LENGTH_80).nullable()

    override val primaryKey = PrimaryKey(id)
}

object CajaMovimientoTable : Table("caja_movimiento") {
    val id = varchar("id", SchemaDimensions.VARCHAR_LENGTH_36)
    val idSecuencia = varchar("id_secuencia", SchemaDimensions.VARCHAR_LENGTH_36)
    val tipo = varchar("tipo", 1)
    val total = decimal("total", SchemaDimensions.DECIMAL_PRECISION_10, 2).default(java.math.BigDecimal.ZERO)

    override val primaryKey = PrimaryKey(id)
}

object FacturaDevolucionTable : Table("factura_devolucion") {
    val id = varchar("id", SchemaDimensions.VARCHAR_LENGTH_36)
    val idCajaSecuencia = varchar("id_caja_secuencia", SchemaDimensions.VARCHAR_LENGTH_36)
    val idFormaPago = integer("id_forma_pago").nullable()
    val totalTotalFactura = decimal("TotalTotalFactura", SchemaDimensions.DECIMAL_PRECISION_10, 2).nullable()

    override val primaryKey = PrimaryKey(id)
}

object CajaFormaPagoGrupoTable : Table("caja_forma_pago_grupo") {
    val id = integer("id")
    val grupo = varchar("grupo", SchemaDimensions.VARCHAR_LENGTH_100).nullable()
    val imagen = text("imagen").nullable()
    val orden = integer("orden").nullable()
    val activo = integer("activo").nullable()

    override val primaryKey = PrimaryKey(id)
}

object CajaDetalleAperturaTable : Table("caja_detalle_apertura") {
    val idDetalleApertura = varchar("id", SchemaDimensions.VARCHAR_LENGTH_36)
    val idCajaSecuencia = varchar("id_secuencia", SchemaDimensions.VARCHAR_LENGTH_36)
    val idMonedaDenominacion = integer("id_moneda_denominacion").nullable()
    val cantidad = integer("cantidad").default(0)
    val valor = decimal("valor", SchemaDimensions.DECIMAL_PRECISION_10, 2).default(java.math.BigDecimal.ZERO)
    val monto = decimal("monto", SchemaDimensions.DECIMAL_PRECISION_10, 2).default(java.math.BigDecimal.ZERO)
    val serieSucursal = varchar("serie_sucursal", SchemaDimensions.VARCHAR_LENGTH_10)

    override val primaryKey = PrimaryKey(idDetalleApertura)
}
