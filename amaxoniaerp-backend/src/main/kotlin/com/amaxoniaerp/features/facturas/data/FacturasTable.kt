package com.amaxoniaerp.features.facturas.data

import org.jetbrains.exposed.sql.Table

abstract class BaseFacturasTable(name: String = "factura") : Table(name) {
    val idFactura = varchar("id_factura", 36)
    val codFactura = varchar("cod_factura", 32)
    val codFacturaFiscal = varchar("cod_factura_fiscal", 10)
    val numeroDocumentoFiscal = varchar("numeroDocumentoFiscal", 20).nullable()
    val idCliente = varchar("id_cliente", 36)
    val codVendedor = integer("cod_vendedor")
    val codEstatus = integer("cod_estatus").nullable()
    val idSucursal = integer("id_sucursal")
    val idCaja = varchar("id_caja", 36)
    val fechaFactura = varchar("fechaFactura", 20).nullable()
    val fechaCreacion = varchar("fecha_creacion", 25).nullable()
    val totalTotalFactura = decimal("TotalTotalFactura", 10, 2).default(0.0.toBigDecimal())
    val totalizarTotalGeneral = decimal("totalizar_total_general", 10, 2).default(0.0.toBigDecimal())
    val formaPago = varchar("formapago", 20)
    val tipoFactura = varchar("tipo_factura", 50)
    val usuarioCreacion = varchar("usuario_creacion", 40)

    override val primaryKey = PrimaryKey(idFactura)
}

object FacturasTableVE : BaseFacturasTable() {
    val abrMonedaBase = varchar("abr_moneda_base", 10).nullable()
    val abrMonedaSecundaria = varchar("abr_moneda_secundaria", 10).nullable()
    val tasa = float("tasa").nullable()
    val totalRef = float("total_ref").nullable()
    val impresoraSerial = varchar("impresora_serial", 50).nullable()
}

object FacturasTablePA : BaseFacturasTable() {
    val puntoFacturacionFiscal = varchar("puntoFacturacionFiscal", 10).nullable()
    val fechaRecepcionDGI = varchar("fechaRecepcionDGI", 25).nullable()
    val cufe = text("cufe").nullable()
    val qr = text("qr").nullable()
    val nroProtocoloAutorizacion = varchar("nroProtocoloAutorizacion", 100).nullable()
    val fechaLimite = varchar("fechaLimite", 25).nullable()
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
    val idCliente = varchar("id_cliente", 36)
    val nombre = varchar("nombre", 100)
    val apellido = varchar("apellido", 100).nullable()
    val rif = varchar("rif", 20)
    val codCliente = varchar("cod_cliente", 80)

    override val primaryKey = PrimaryKey(idCliente)
}

object EstatusTable : Table("estatus") {
    val codEstatus = integer("cod_estatus")
    val descripcion = varchar("descripcion", 50)

    override val primaryKey = PrimaryKey(codEstatus)
}
