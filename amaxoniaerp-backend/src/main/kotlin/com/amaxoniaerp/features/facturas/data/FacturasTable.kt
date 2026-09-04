package com.amaxoniaerp.features.facturas.data

import org.jetbrains.exposed.sql.Table
import com.amaxoniaerp.core.database.SchemaDimensions as S

abstract class BaseFacturasTable(
    name: String = "factura",
) : Table(name) {
    val idFactura = varchar("id_factura", S.VARCHAR_LENGTH_36)
    val codFactura = varchar("cod_factura", S.VARCHAR_LENGTH_32)
    val codFacturaFiscal = varchar("cod_factura_fiscal", S.VARCHAR_LENGTH_10)
    val numeroDocumentoFiscal = varchar("numeroDocumentoFiscal", S.VARCHAR_LENGTH_20).nullable()
    val idCliente = varchar("id_cliente", S.VARCHAR_LENGTH_36)
    val codVendedor = integer("cod_vendedor")
    val codEstatus = integer("cod_estatus").nullable()
    val idSucursal = integer("id_sucursal")
    val idCaja = varchar("id_caja", S.VARCHAR_LENGTH_36)
    val idCajaSecuencia = varchar("id_caja_secuencia", S.VARCHAR_LENGTH_36).nullable()
    val serieSucursal = varchar("serie_sucursal", S.VARCHAR_LENGTH_10).nullable()
    val codigoCaja = varchar("codigo_caja", S.VARCHAR_LENGTH_50).nullable()
    val cantidadItems = integer("cantidad_items").default(1)
    val clienteSucursalId = integer("cliente_sucursal_id").nullable()
    val fechaFactura = varchar("fechaFactura", S.VARCHAR_LENGTH_20).nullable()
    val fechaCreacion = varchar("fecha_creacion", S.VARCHAR_LENGTH_25).nullable()
    val subtotal = decimal("subtotal", S.DECIMAL_PRECISION_20, 2).default(0.0.toBigDecimal())
    val totalizarSubTotal = decimal("totalizar_sub_total", S.DECIMAL_PRECISION_20, 2).default(0.0.toBigDecimal())
    val totalizarTotalOperacion =
        decimal(
            "totalizar_total_operacion",
            S.DECIMAL_PRECISION_20,
            2,
        ).default(0.0.toBigDecimal())
    val totalizarPDescuentoGlobal =
        decimal(
            "totalizar_pdescuento_global",
            S.DECIMAL_PRECISION_20,
            2,
        ).default(0.0.toBigDecimal())
    val totalizarDescuentoGlobal =
        decimal(
            "totalizar_descuento_global",
            S.DECIMAL_PRECISION_20,
            2,
        ).default(0.0.toBigDecimal())
    val totalizarBaseImponible =
        decimal(
            "totalizar_base_imponible",
            S.DECIMAL_PRECISION_20,
            2,
        ).default(0.0.toBigDecimal())
    val totalizarMontoIva = decimal("totalizar_monto_iva", S.DECIMAL_PRECISION_20, 2).default(0.0.toBigDecimal())
    val totalizarTotalGeneral =
        decimal("totalizar_total_general", S.DECIMAL_PRECISION_10, 2).default(0.0.toBigDecimal())
    val totalTotalFactura = decimal("TotalTotalFactura", S.DECIMAL_PRECISION_10, 2).default(0.0.toBigDecimal())
    val formaPago = varchar("formapago", S.VARCHAR_LENGTH_20)
    val tipoFactura = varchar("tipo_factura", S.VARCHAR_LENGTH_50).default("VENTA")
    val usuarioCreacion = varchar("usuario_creacion", S.VARCHAR_LENGTH_40).default("admin")
    val montoItemsFactura = decimal("montoItemsFactura", S.DECIMAL_PRECISION_20, 2).default(0.0.toBigDecimal())
    val ivaTotalFactura = decimal("ivaTotalFactura", S.DECIMAL_PRECISION_20, 2).default(0.0.toBigDecimal())
    val tipoDocumento = varchar("tipo_documento", S.VARCHAR_LENGTH_5).default("01")
    val naturalezaOperacion = varchar("NaturalezaOperacion", S.VARCHAR_LENGTH_5).nullable()
    val tipoOperacion = varchar("tipoOperacion", S.VARCHAR_LENGTH_5).nullable()
    val formatoCAFE = varchar("formatoCAFE", S.VARCHAR_LENGTH_5).nullable()
    val entregaCAFE = varchar("entregaCAFE", S.VARCHAR_LENGTH_5).nullable()
    val envioContenedor = varchar("envioContenedor", S.VARCHAR_LENGTH_5).nullable()
    val tipoVenta = varchar("tipoVenta", S.VARCHAR_LENGTH_5).nullable()
    val observacion = varchar("observacion", S.VARCHAR_LENGTH_300).nullable()
    val facturarA = varchar("facturar_a", S.VARCHAR_LENGTH_80).nullable()
    val facturarARuc = varchar("facturar_a_ruc", S.VARCHAR_LENGTH_50).nullable()
    val facturarADireccion = varchar("facturar_a_direccion", S.VARCHAR_LENGTH_250).nullable()
    val facturarATelefono = varchar("facturar_a_telefono", S.VARCHAR_LENGTH_50).nullable()
    val abrMonedaBase = varchar("abr_moneda_base", S.VARCHAR_LENGTH_10).nullable()
    val abrMonedaSecundaria = varchar("abr_moneda_secundaria", S.VARCHAR_LENGTH_10).nullable()
    val tasa = float("tasa").nullable()
    val totalRef = float("total_ref").nullable()
    val impresoraSerial = varchar("impresora_serial", S.VARCHAR_LENGTH_50).nullable()

    override val primaryKey = PrimaryKey(idFactura)
}

object FacturasTableVE : BaseFacturasTable() {
    val numeroControlThka = varchar("numero_control_thka", S.VARCHAR_LENGTH_50).nullable()
}

object FacturasTablePA : BaseFacturasTable() {
    val puntoFacturacionFiscal = varchar("puntoFacturacionFiscal", S.VARCHAR_LENGTH_10).nullable()
    val fechaRecepcionDGI = varchar("fechaRecepcionDGI", S.VARCHAR_LENGTH_25).nullable()
    val cufe = text("cufe").nullable()
    val qr = text("qr").nullable()
    val nroProtocoloAutorizacion = varchar("nroProtocoloAutorizacion", S.VARCHAR_LENGTH_100).nullable()
    val fechaLimite = varchar("fechaLimite", S.VARCHAR_LENGTH_25).nullable()
}

object FacturasTableFactory {
    fun forCountry(countryCode: String): BaseFacturasTable =
        when (countryCode.uppercase()) {
            "VE" -> FacturasTableVE
            "PA" -> FacturasTablePA
            else -> FacturasTableVE
        }
}

object FacturasClientesTable : Table("clientes") {
    val idCliente = varchar("id_cliente", S.VARCHAR_LENGTH_36)
    val nombre = varchar("nombre", S.VARCHAR_LENGTH_100)
    val apellido = varchar("apellido", S.VARCHAR_LENGTH_100).nullable()
    val rif = varchar("rif", S.VARCHAR_LENGTH_20)
    val dv = varchar("dv", 5).nullable()
    val codCliente = varchar("cod_cliente", S.VARCHAR_LENGTH_80)
    val direccion = varchar("direccion", S.VARCHAR_LENGTH_250).nullable()
    val telefonos = varchar("telefonos", S.VARCHAR_LENGTH_50).nullable()
    val email = varchar("email", S.VARCHAR_LENGTH_100).nullable()
    val codTipoCliente = integer("cod_tipo_cliente").default(1)

    override val primaryKey = PrimaryKey(idCliente)
}

object EstatusTable : Table("estatus") {
    val codEstatus = integer("cod_estatus")
    val descripcion = varchar("descripcion", S.VARCHAR_LENGTH_50)

    override val primaryKey = PrimaryKey(codEstatus)
}
