package com.amaxoniaerp.features.facturas.data

import org.jetbrains.exposed.sql.Table

object FacturasTable : Table("factura") {
    val idFactura = varchar("id_factura", 36)
    val codFactura = varchar("cod_factura", 32).default("S/I")
    val codFacturaFiscal = varchar("cod_factura_fiscal", 10)
    val numeroDocumentoFiscal = varchar("numeroDocumentoFiscal", 20).nullable()
    val idCliente = varchar("id_cliente", 36)
    val codVendedor = integer("cod_vendedor")
    val codEstatus = integer("cod_estatus").nullable()
    val idSucursal = integer("id_sucursal")
    val idCaja = varchar("id_caja", 36)
    val fechaFactura = varchar("fechaFactura", 20).nullable()
    val fechaRecepcionDGI = varchar("fechaRecepcionDGI", 25).nullable()
    val fechaCreacion = varchar("fecha_creacion", 25).nullable()
    val totalTotalFactura = decimal("TotalTotalFactura", 10, 2).default(0.0.toBigDecimal())
    val totalizarTotalGeneral = decimal("totalizar_total_general", 10, 2).default(0.0.toBigDecimal())
    val formaPago = varchar("formapago", 20)
    val cufe = text("cufe").nullable()
    val tipoFactura = varchar("tipo_factura", 50)
    val usuarioCreacion = varchar("usuario_creacion", 40)
    val abrMonedaBase = varchar("abr_moneda_base", 10).nullable()
    val impresoraSerial = varchar("impresora_serial", 50).nullable()

    override val primaryKey = PrimaryKey(idFactura)
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
