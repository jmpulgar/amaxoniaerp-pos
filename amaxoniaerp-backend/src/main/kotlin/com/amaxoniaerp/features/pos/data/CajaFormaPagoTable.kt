package com.amaxoniaerp.features.pos.data

import org.jetbrains.exposed.sql.Table

private const val SCHEMA_CUENTA_CONTABLE_MAX_LENGTH = 80
private const val SCHEMA_DESCRIPCION_MAX_LENGTH = 80
private const val SCHEMA_ID_CAJA_MAX_LENGTH = 36
private const val SCHEMA_SIGLAS_MAX_LENGTH = 80

object CajaFormaPagoTable : Table("caja_forma_pago") {
    val idFormaPago = integer("id_forma_pago")
    val siglas = varchar("siglas", SCHEMA_SIGLAS_MAX_LENGTH).nullable()
    val codigo = integer("codigo").nullable()
    val descripcion = varchar("descripcion", SCHEMA_DESCRIPCION_MAX_LENGTH).nullable()
    val idCajaTpConcepto = integer("id_caja_tp_concepto").nullable()
    val cuentaContable = varchar("cuenta_contable", SCHEMA_CUENTA_CONTABLE_MAX_LENGTH).nullable()
    val idCajaTpRegistro = integer("id_caja_tp_registro").nullable()
    val formaPagoFact = varchar("FormaPagoFact", 2).nullable()
    val activo = integer("activo")
    val pos = integer("pos")
    val imagen = text("imagen")
    val grupo = integer("grupo")
    val orden = integer("orden")
    val idBancoCuenta = integer("id_banco_cuenta")
    val idBancoOperacion = integer("id_banco_operacion")
    val tipoMoneda = varchar("tipo_moneda", 1)

    override val primaryKey = PrimaryKey(idFormaPago)
}

object CajaFormaTable : Table("caja_forma") {
    val idCaja = varchar("id_caja", SCHEMA_ID_CAJA_MAX_LENGTH)
    val idFormaPago = integer("id_forma_pago")
    val activo = integer("activo").nullable()

    override val primaryKey = PrimaryKey(idCaja, idFormaPago)
}
