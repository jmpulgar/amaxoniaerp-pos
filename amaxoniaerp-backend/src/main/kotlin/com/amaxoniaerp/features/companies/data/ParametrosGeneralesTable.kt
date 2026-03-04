package com.amaxoniaerp.features.companies.data

import org.jetbrains.exposed.sql.Table

object ParametrosGeneralesTable : Table("parametros_generales") {
    val codEmpresa = integer("cod_empresa")
    val defaultCodClienteFactura = varchar("default_cod_cliente_factura", 80)
    val defaultIdFormaPagoFactura = integer("default_id_formapago_factura")
    val porcentajeImpuestoPrincipal = decimal("porcentaje_impuesto_principal", 10, 2)
    val validarStock = varchar("validar_stock", 2)
    val multiMoneda = varchar("multi_moneda", 2)
    val monedaBase = integer("moneda_base").nullable()
    val abrMonedaBase = varchar("moneda", 50)
    val monedaSecundaria = integer("moneda_secundaria")
    val abrMonedaSecundaria = varchar("moneda_secundaria_abr", 50)
    val diasVencimiento = integer("dias_vencimiento")
    val codAlmacen = integer("cod_almacen")
    val rif = varchar("rif", 50).nullable()
}

object TasasCambioTable : Table("tasas_cambio") {
    val id = long("id")
    val divisa = integer("divisa").nullable()
    val tasaInversa = decimal("tasa_inversa", 20, 8)
    val monedabase = integer("monedabase").nullable()
    val facturado = varchar("facturado", 1)

    override val primaryKey = PrimaryKey(id)
}
