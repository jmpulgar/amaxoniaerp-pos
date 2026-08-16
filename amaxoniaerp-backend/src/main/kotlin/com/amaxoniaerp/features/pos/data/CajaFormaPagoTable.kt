package com.amaxoniaerp.features.pos.data

import com.amaxoniaerp.core.database.SchemaDimensions
import org.jetbrains.exposed.sql.Table

object CajaFormaPagoTable : Table("caja_forma_pago") {
    val idFormaPago = integer("id_forma_pago")
    val siglas = varchar("siglas", SchemaDimensions.VARCHAR_LENGTH_80).nullable()
    val codigo = integer("codigo").nullable()
    val descripcion = varchar("descripcion", SchemaDimensions.VARCHAR_LENGTH_80).nullable()
    val idCajaTpConcepto = integer("id_caja_tp_concepto").nullable()
    val cuentaContable = varchar("cuenta_contable", SchemaDimensions.VARCHAR_LENGTH_80).nullable()
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
    val idCaja = varchar("id_caja", SchemaDimensions.VARCHAR_LENGTH_36)
    val idFormaPago = integer("id_forma_pago")
    val activo = integer("activo").nullable()

    override val primaryKey = PrimaryKey(idCaja, idFormaPago)
}
