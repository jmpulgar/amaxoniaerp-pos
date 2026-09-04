package com.amaxoniaerp.features.pos.data

import org.jetbrains.exposed.sql.Table
import com.amaxoniaerp.core.database.SchemaDimensions as S

object CajaFormaPagoTable : Table("caja_forma_pago") {
    val idFormaPago = integer("id_forma_pago")
    val siglas = varchar("siglas", S.VARCHAR_LENGTH_80).nullable()
    val codigo = integer("codigo").nullable()
    val descripcion = varchar("descripcion", S.VARCHAR_LENGTH_80).nullable()
    val idCajaTpConcepto = integer("id_caja_tp_concepto").nullable()
    val cuentaContable = varchar("cuenta_contable", S.VARCHAR_LENGTH_80).nullable()
    val idCajaTpRegistro = integer("id_caja_tp_registro").nullable()
    val formaPagoFact = varchar("FormaPagoFact", 2).nullable()
    val activo = integer("activo").default(1)
    val pos = integer("pos").default(1)
    val imagen = text("imagen").default("")
    val grupo = integer("grupo").default(1)
    val orden = integer("orden").default(1)
    val idBancoCuenta = integer("id_banco_cuenta").default(1)
    val idBancoOperacion = integer("id_banco_operacion").default(1)
    val tipoMoneda = varchar("tipo_moneda", 1).default("1")

    override val primaryKey = PrimaryKey(idFormaPago)
}

object CajaFormaTable : Table("caja_forma") {
    val idCaja = varchar("id_caja", S.VARCHAR_LENGTH_36)
    val idFormaPago = integer("id_forma_pago")
    val activo = integer("activo").nullable()

    override val primaryKey = PrimaryKey(idCaja, idFormaPago)
}
