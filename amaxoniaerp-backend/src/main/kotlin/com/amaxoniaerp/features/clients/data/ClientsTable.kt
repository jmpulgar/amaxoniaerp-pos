package com.amaxoniaerp.features.clients.data

import org.jetbrains.exposed.sql.Table

private const val SCHEMA_APELLIDO_MAX_LENGTH = 20
private const val SCHEMA_CLIENTE_CODIGO_MAX_LENGTH = 9
private const val SCHEMA_COD_CLIENTE_MAX_LENGTH = 80
private const val SCHEMA_CORREO_CONTACTO_MAX_LENGTH = 255
private const val SCHEMA_DIRECCION_MAX_LENGTH = 200
private const val SCHEMA_DIRECCION_NIVEL1_MAX_LENGTH = 100
private const val SCHEMA_DIRECCION_NIVEL2_MAX_LENGTH = 100
private const val SCHEMA_DIRECCION_NIVEL3_MAX_LENGTH = 100
private const val SCHEMA_DV_MAX_LENGTH = 255
private const val SCHEMA_EMAIL_MAX_LENGTH = 50
private const val SCHEMA_FECHA_MAX_LENGTH = 64
private const val SCHEMA_FOTO_MAX_LENGTH = 120
private const val SCHEMA_ID_CLIENTE_MAX_LENGTH = 36
private const val SCHEMA_NOMBRE_CONTACTO_MAX_LENGTH = 255
private const val SCHEMA_NOMBRE_MAX_LENGTH = 100
private const val SCHEMA_NOMBRE_SUCURSAL_MAX_LENGTH = 255
private const val SCHEMA_RIF_MAX_LENGTH = 50
private const val SCHEMA_TELEFONOS_MAX_LENGTH = 50
private const val SCHEMA_TELEFONO_CONTACTO_MAX_LENGTH = 50
private const val SCHEMA_TIPO_IDENTIFICACION_EXTRANJERA_MAX_LENGTH = 10

object ClientsTable : Table("clientes") {
    val idCliente = varchar("id_cliente", SCHEMA_ID_CLIENTE_MAX_LENGTH)
    val codCliente = varchar("cod_cliente", SCHEMA_COD_CLIENTE_MAX_LENGTH)
    val rif = varchar("rif", SCHEMA_RIF_MAX_LENGTH)
    val dv = varchar("dv", SCHEMA_DV_MAX_LENGTH)
    val nombre = varchar("nombre", SCHEMA_NOMBRE_MAX_LENGTH)
    val apellido = varchar("apellido", SCHEMA_APELLIDO_MAX_LENGTH)
    val direccion = varchar("direccion", SCHEMA_DIRECCION_MAX_LENGTH)
    val direccionNivel1 = varchar("direccion_nivel1", SCHEMA_DIRECCION_NIVEL1_MAX_LENGTH).nullable()
    val direccionNivel2 = varchar("direccion_nivel2", SCHEMA_DIRECCION_NIVEL2_MAX_LENGTH).nullable()
    val direccionNivel3 = varchar("direccion_nivel3", SCHEMA_DIRECCION_NIVEL3_MAX_LENGTH).nullable()
    val tipoIdentificacionExtranjera =
        varchar(
            "tipo_identificacion_extranjera",
            SCHEMA_TIPO_IDENTIFICACION_EXTRANJERA_MAX_LENGTH,
        ).nullable()
    val telefonos = varchar("telefonos", SCHEMA_TELEFONOS_MAX_LENGTH)
    val email = varchar("email", SCHEMA_EMAIL_MAX_LENGTH)
    val estado = varchar("estado", 1)
    val pais = integer("pais")
    val codTipoCliente = integer("cod_tipo_cliente")
    val tipoContribuyente = integer("tipo_contribuyente")
    val fecha = varchar("fecha", SCHEMA_FECHA_MAX_LENGTH).nullable()
    val permiteCredito = bool("permitecredito")
    val limite = double("limite")
    val dias = integer("dias")
    val foto = varchar("foto", SCHEMA_FOTO_MAX_LENGTH).nullable()

    override val primaryKey = PrimaryKey(idCliente)
}

object ClientSucursalTable : Table("cliente_sucursal") {
    val sucursalId = integer("sucursal_id").autoIncrement()
    val clienteCodigo = varchar("cliente_codigo", SCHEMA_CLIENTE_CODIGO_MAX_LENGTH)
    val nombreSucursal = varchar("nombre_sucursal", SCHEMA_NOMBRE_SUCURSAL_MAX_LENGTH)
    val nombreContacto = varchar("nombre_contacto", SCHEMA_NOMBRE_CONTACTO_MAX_LENGTH).nullable()
    val telefonoContacto = varchar("telefono_contacto", SCHEMA_TELEFONO_CONTACTO_MAX_LENGTH).nullable()
    val correoContacto = varchar("correo_contacto", SCHEMA_CORREO_CONTACTO_MAX_LENGTH).nullable()
    val direccion = text("direccion").nullable()
    val observaciones = text("observaciones").nullable()

    override val primaryKey = PrimaryKey(sucursalId)
}
