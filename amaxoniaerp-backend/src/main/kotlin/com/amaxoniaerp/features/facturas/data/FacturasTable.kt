package com.amaxoniaerp.features.facturas.data

import org.jetbrains.exposed.sql.Table

private const val SCHEMA_ABR_MONEDA_BASE_MAX_LENGTH = 10
private const val SCHEMA_ABR_MONEDA_SECUNDARIA_MAX_LENGTH = 10
private const val SCHEMA_APELLIDO_MAX_LENGTH = 100
private const val SCHEMA_COD_CLIENTE_MAX_LENGTH = 80
private const val SCHEMA_COD_FACTURA_FISCAL_MAX_LENGTH = 10
private const val SCHEMA_COD_FACTURA_MAX_LENGTH = 32
private const val SCHEMA_DESCRIPCION_MAX_LENGTH = 50
private const val SCHEMA_FECHA_CREACION_MAX_LENGTH = 25
private const val SCHEMA_FECHA_FACTURA_MAX_LENGTH = 20
private const val SCHEMA_FECHA_LIMITE_MAX_LENGTH = 25
private const val SCHEMA_FECHA_RECEPCION_DGI_MAX_LENGTH = 25
private const val SCHEMA_FORMAPAGO_MAX_LENGTH = 20
private const val SCHEMA_ID_CAJA_MAX_LENGTH = 36
private const val SCHEMA_ID_CLIENTE_MAX_LENGTH = 36
private const val SCHEMA_ID_FACTURA_MAX_LENGTH = 36
private const val SCHEMA_IMPRESORA_SERIAL_MAX_LENGTH = 50
private const val SCHEMA_NOMBRE_MAX_LENGTH = 100
private const val SCHEMA_NRO_PROTOCOLO_AUTORIZACION_MAX_LENGTH = 100
private const val SCHEMA_NUMERO_DOCUMENTO_FISCAL_MAX_LENGTH = 20
private const val SCHEMA_PUNTO_FACTURACION_FISCAL_MAX_LENGTH = 10
private const val SCHEMA_RIF_MAX_LENGTH = 20
private const val SCHEMA_TIPO_FACTURA_MAX_LENGTH = 50
private const val SCHEMA_TOTALIZAR_TOTAL_GENERAL_PRECISION = 10
private const val SCHEMA_TOTAL_TOTAL_FACTURA_PRECISION = 10
private const val SCHEMA_USUARIO_CREACION_MAX_LENGTH = 40

abstract class BaseFacturasTable(
    name: String = "factura",
) : Table(name) {
    val idFactura = varchar("id_factura", SCHEMA_ID_FACTURA_MAX_LENGTH)
    val codFactura = varchar("cod_factura", SCHEMA_COD_FACTURA_MAX_LENGTH)
    val codFacturaFiscal = varchar("cod_factura_fiscal", SCHEMA_COD_FACTURA_FISCAL_MAX_LENGTH)
    val numeroDocumentoFiscal = varchar("numeroDocumentoFiscal", SCHEMA_NUMERO_DOCUMENTO_FISCAL_MAX_LENGTH).nullable()
    val idCliente = varchar("id_cliente", SCHEMA_ID_CLIENTE_MAX_LENGTH)
    val codVendedor = integer("cod_vendedor")
    val codEstatus = integer("cod_estatus").nullable()
    val idSucursal = integer("id_sucursal")
    val idCaja = varchar("id_caja", SCHEMA_ID_CAJA_MAX_LENGTH)
    val fechaFactura = varchar("fechaFactura", SCHEMA_FECHA_FACTURA_MAX_LENGTH).nullable()
    val fechaCreacion = varchar("fecha_creacion", SCHEMA_FECHA_CREACION_MAX_LENGTH).nullable()
    val totalTotalFactura = decimal("TotalTotalFactura", SCHEMA_TOTAL_TOTAL_FACTURA_PRECISION, 2).default(0.0.toBigDecimal())
    val totalizarTotalGeneral = decimal("totalizar_total_general", SCHEMA_TOTALIZAR_TOTAL_GENERAL_PRECISION, 2).default(0.0.toBigDecimal())
    val formaPago = varchar("formapago", SCHEMA_FORMAPAGO_MAX_LENGTH)
    val tipoFactura = varchar("tipo_factura", SCHEMA_TIPO_FACTURA_MAX_LENGTH)
    val usuarioCreacion = varchar("usuario_creacion", SCHEMA_USUARIO_CREACION_MAX_LENGTH)

    override val primaryKey = PrimaryKey(idFactura)
}

object FacturasTableVE : BaseFacturasTable() {
    val abrMonedaBase = varchar("abr_moneda_base", SCHEMA_ABR_MONEDA_BASE_MAX_LENGTH).nullable()
    val abrMonedaSecundaria = varchar("abr_moneda_secundaria", SCHEMA_ABR_MONEDA_SECUNDARIA_MAX_LENGTH).nullable()
    val tasa = float("tasa").nullable()
    val totalRef = float("total_ref").nullable()
    val impresoraSerial = varchar("impresora_serial", SCHEMA_IMPRESORA_SERIAL_MAX_LENGTH).nullable()
}

object FacturasTablePA : BaseFacturasTable() {
    val puntoFacturacionFiscal = varchar("puntoFacturacionFiscal", SCHEMA_PUNTO_FACTURACION_FISCAL_MAX_LENGTH).nullable()
    val fechaRecepcionDGI = varchar("fechaRecepcionDGI", SCHEMA_FECHA_RECEPCION_DGI_MAX_LENGTH).nullable()
    val cufe = text("cufe").nullable()
    val qr = text("qr").nullable()
    val nroProtocoloAutorizacion = varchar("nroProtocoloAutorizacion", SCHEMA_NRO_PROTOCOLO_AUTORIZACION_MAX_LENGTH).nullable()
    val fechaLimite = varchar("fechaLimite", SCHEMA_FECHA_LIMITE_MAX_LENGTH).nullable()
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
    val idCliente = varchar("id_cliente", SCHEMA_ID_CLIENTE_MAX_LENGTH)
    val nombre = varchar("nombre", SCHEMA_NOMBRE_MAX_LENGTH)
    val apellido = varchar("apellido", SCHEMA_APELLIDO_MAX_LENGTH).nullable()
    val rif = varchar("rif", SCHEMA_RIF_MAX_LENGTH)
    val codCliente = varchar("cod_cliente", SCHEMA_COD_CLIENTE_MAX_LENGTH)

    override val primaryKey = PrimaryKey(idCliente)
}

object EstatusTable : Table("estatus") {
    val codEstatus = integer("cod_estatus")
    val descripcion = varchar("descripcion", SCHEMA_DESCRIPCION_MAX_LENGTH)

    override val primaryKey = PrimaryKey(codEstatus)
}
