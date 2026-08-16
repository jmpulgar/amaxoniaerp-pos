package com.amaxoniaerp.features.clients.data

import com.amaxoniaerp.core.database.SchemaDimensions
import org.jetbrains.exposed.sql.Table

object ClientsTable : Table("clientes") {
    val idCliente = varchar("id_cliente", SchemaDimensions.VARCHAR_LENGTH_36)
    val codCliente = varchar("cod_cliente", SchemaDimensions.VARCHAR_LENGTH_80)
    val rif = varchar("rif", SchemaDimensions.VARCHAR_LENGTH_50)
    val dv = varchar("dv", SchemaDimensions.VARCHAR_LENGTH_255)
    val nombre = varchar("nombre", SchemaDimensions.VARCHAR_LENGTH_100)
    val apellido = varchar("apellido", SchemaDimensions.VARCHAR_LENGTH_20)
    val direccion = varchar("direccion", SchemaDimensions.VARCHAR_LENGTH_200)
    val direccionNivel1 = varchar("direccion_nivel1", SchemaDimensions.VARCHAR_LENGTH_100).nullable()
    val direccionNivel2 = varchar("direccion_nivel2", SchemaDimensions.VARCHAR_LENGTH_100).nullable()
    val direccionNivel3 = varchar("direccion_nivel3", SchemaDimensions.VARCHAR_LENGTH_100).nullable()
    val tipoIdentificacionExtranjera = varchar("tipo_identificacion_extranjera", SchemaDimensions.VARCHAR_LENGTH_10).nullable()
    val telefonos = varchar("telefonos", SchemaDimensions.VARCHAR_LENGTH_50)
    val email = varchar("email", SchemaDimensions.VARCHAR_LENGTH_50)
    val estado = varchar("estado", 1)
    val pais = integer("pais")
    val codTipoCliente = integer("cod_tipo_cliente")
    val tipoContribuyente = integer("tipo_contribuyente")
    val fecha = varchar("fecha", SchemaDimensions.VARCHAR_LENGTH_64).nullable()
    val permiteCredito = bool("permitecredito")
    val limite = double("limite")
    val dias = integer("dias")
    val foto = varchar("foto", SchemaDimensions.VARCHAR_LENGTH_120).nullable()

    override val primaryKey = PrimaryKey(idCliente)
}

object ClientSucursalTable : Table("cliente_sucursal") {
    val sucursalId = integer("sucursal_id").autoIncrement()
    val clienteCodigo = varchar("cliente_codigo", SchemaDimensions.VARCHAR_LENGTH_9)
    val nombreSucursal = varchar("nombre_sucursal", SchemaDimensions.VARCHAR_LENGTH_255)
    val nombreContacto = varchar("nombre_contacto", SchemaDimensions.VARCHAR_LENGTH_255).nullable()
    val telefonoContacto = varchar("telefono_contacto", SchemaDimensions.VARCHAR_LENGTH_50).nullable()
    val correoContacto = varchar("correo_contacto", SchemaDimensions.VARCHAR_LENGTH_255).nullable()
    val direccion = text("direccion").nullable()
    val observaciones = text("observaciones").nullable()

    override val primaryKey = PrimaryKey(sucursalId)
}
