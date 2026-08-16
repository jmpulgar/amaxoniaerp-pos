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
    val fechaFactura = varchar("fechaFactura", S.VARCHAR_LENGTH_20).nullable()
    val fechaCreacion = varchar("fecha_creacion", S.VARCHAR_LENGTH_25).nullable()
    val totalTotalFactura = decimal("TotalTotalFactura", S.DECIMAL_PRECISION_10, 2).default(0.0.toBigDecimal())
    val totalizarTotalGeneral = decimal("totalizar_total_general", S.DECIMAL_PRECISION_10, 2).default(0.0.toBigDecimal())
    val formaPago = varchar("formapago", S.VARCHAR_LENGTH_20)
    val tipoFactura = varchar("tipo_factura", S.VARCHAR_LENGTH_50)
    val usuarioCreacion = varchar("usuario_creacion", S.VARCHAR_LENGTH_40)

    override val primaryKey = PrimaryKey(idFactura)
}

object FacturasTableVE : BaseFacturasTable() {
    val abrMonedaBase = varchar("abr_moneda_base", S.VARCHAR_LENGTH_10).nullable()
    val abrMonedaSecundaria = varchar("abr_moneda_secundaria", S.VARCHAR_LENGTH_10).nullable()
    val tasa = float("tasa").nullable()
    val totalRef = float("total_ref").nullable()
    val impresoraSerial = varchar("impresora_serial", S.VARCHAR_LENGTH_50).nullable()
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
    val codCliente = varchar("cod_cliente", S.VARCHAR_LENGTH_80)

    override val primaryKey = PrimaryKey(idCliente)
}

object EstatusTable : Table("estatus") {
    val codEstatus = integer("cod_estatus")
    val descripcion = varchar("descripcion", S.VARCHAR_LENGTH_50)

    override val primaryKey = PrimaryKey(codEstatus)
}
