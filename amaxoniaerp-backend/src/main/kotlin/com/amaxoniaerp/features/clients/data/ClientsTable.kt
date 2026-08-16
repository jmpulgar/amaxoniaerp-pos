package com.amaxoniaerp.features.clients.data

import org.jetbrains.exposed.sql.Table
import com.amaxoniaerp.core.database.SchemaDimensions as S

object ClientsTable : Table("clientes") {
    val idCliente = varchar("id_cliente", S.VARCHAR_LENGTH_36)
    val codCliente = varchar("cod_cliente", S.VARCHAR_LENGTH_80)
    val rif = varchar("rif", S.VARCHAR_LENGTH_50)
    val dv = varchar("dv", S.VARCHAR_LENGTH_255)
    val nombre = varchar("nombre", S.VARCHAR_LENGTH_100)
    val apellido = varchar("apellido", S.VARCHAR_LENGTH_20)
    val direccion = varchar("direccion", S.VARCHAR_LENGTH_200)
    val direccionNivel1 = varchar("direccion_nivel1", S.VARCHAR_LENGTH_100).nullable()
    val direccionNivel2 = varchar("direccion_nivel2", S.VARCHAR_LENGTH_100).nullable()
    val direccionNivel3 = varchar("direccion_nivel3", S.VARCHAR_LENGTH_100).nullable()
    val tipoIdentificacionExtranjera = varchar("tipo_identificacion_extranjera", S.VARCHAR_LENGTH_10).nullable()
    val telefonos = varchar("telefonos", S.VARCHAR_LENGTH_50)
    val email = varchar("email", S.VARCHAR_LENGTH_50)
    val estado = varchar("estado", 1)
    val pais = integer("pais")
    val codTipoCliente = integer("cod_tipo_cliente")
    val tipoContribuyente = integer("tipo_contribuyente")
    val fecha = varchar("fecha", S.VARCHAR_LENGTH_64).nullable()
    val permiteCredito = bool("permitecredito")
    val limite = double("limite")
    val dias = integer("dias")
    val foto = varchar("foto", S.VARCHAR_LENGTH_120).nullable()

    override val primaryKey = PrimaryKey(idCliente)
}

object ClientSucursalTable : Table("cliente_sucursal") {
    val sucursalId = integer("sucursal_id").autoIncrement()
    val clienteCodigo = varchar("cliente_codigo", S.VARCHAR_LENGTH_9)
    val nombreSucursal = varchar("nombre_sucursal", S.VARCHAR_LENGTH_255)
    val nombreContacto = varchar("nombre_contacto", S.VARCHAR_LENGTH_255).nullable()
    val telefonoContacto = varchar("telefono_contacto", S.VARCHAR_LENGTH_50).nullable()
    val correoContacto = varchar("correo_contacto", S.VARCHAR_LENGTH_255).nullable()
    val direccion = text("direccion").nullable()
    val observaciones = text("observaciones").nullable()

    override val primaryKey = PrimaryKey(sucursalId)
}
