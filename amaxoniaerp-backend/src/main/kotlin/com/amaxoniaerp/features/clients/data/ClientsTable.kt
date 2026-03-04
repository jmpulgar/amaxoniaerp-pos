package com.amaxoniaerp.features.clients.data

import org.jetbrains.exposed.sql.Table

object ClientsTable : Table("clientes") {
    val idCliente = varchar("id_cliente", 36)
    val codCliente = varchar("cod_cliente", 80)
    val rif = varchar("rif", 50)
    val dv = varchar("dv", 255)
    val nombre = varchar("nombre", 100)
    val apellido = varchar("apellido", 20)
    val direccion = varchar("direccion", 200)
    val direccionNivel1 = varchar("direccion_nivel1", 100).nullable()
    val direccionNivel2 = varchar("direccion_nivel2", 100).nullable()
    val direccionNivel3 = varchar("direccion_nivel3", 100).nullable()
    val tipoIdentificacionExtranjera = varchar("tipo_identificacion_extranjera", 10).nullable()
    val telefonos = varchar("telefonos", 50)
    val email = varchar("email", 50)
    val estado = varchar("estado", 1)
    val pais = integer("pais")
    val codTipoCliente = integer("cod_tipo_cliente")
    val tipoContribuyente = integer("tipo_contribuyente")
    val fecha = varchar("fecha", 64).nullable()
    val permiteCredito = bool("permitecredito")
    val limite = double("limite")
    val dias = integer("dias")
    val foto = varchar("foto", 120).nullable()

    override val primaryKey = PrimaryKey(idCliente)
}
