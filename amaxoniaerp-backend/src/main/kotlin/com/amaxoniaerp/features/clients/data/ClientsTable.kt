package com.amaxoniaerp.features.clients.data

import org.jetbrains.exposed.sql.Table
import com.amaxoniaerp.core.database.SchemaDimensions as S

object ClientsTable : Table("clientes") {
    val idCliente = varchar("id_cliente", S.VARCHAR_LENGTH_36)
    val codCliente = varchar("cod_cliente", S.VARCHAR_LENGTH_80)
    val rif = varchar("rif", S.VARCHAR_LENGTH_50)
    val dv = varchar("dv", S.VARCHAR_LENGTH_255).nullable().default("0")
    val nombre = varchar("nombre", S.VARCHAR_LENGTH_100)
    val apellido = varchar("apellido", S.VARCHAR_LENGTH_20).nullable()
    val direccion = varchar("direccion", S.VARCHAR_LENGTH_200).default("")
    val direccionNivel1 = varchar("direccion_nivel1", S.VARCHAR_LENGTH_100).nullable()
    val direccionNivel2 = varchar("direccion_nivel2", S.VARCHAR_LENGTH_100).nullable()
    val direccionNivel3 = varchar("direccion_nivel3", S.VARCHAR_LENGTH_100).nullable()
    val tipoIdentificacionExtranjera = varchar("tipo_identificacion_extranjera", S.VARCHAR_LENGTH_10).nullable()
    val telefonos = varchar("telefonos", S.VARCHAR_LENGTH_50).default("")
    val email = varchar("email", S.VARCHAR_LENGTH_50).default("")
    val estado = varchar("estado", 1).default("1")
    val pais = integer("pais").default(1)
    val paisExtranjero = integer("paisExtranjero").nullable()
    val codTipoCliente = integer("cod_tipo_cliente").default(1)
    val codTipoPrecio = integer("cod_tipo_precio").default(2)
    val tipoContribuyente = integer("tipo_contribuyente").default(1)
    val fecha = varchar("fecha", S.VARCHAR_LENGTH_64).nullable()
    val permiteCredito = bool("permitecredito").default(false)
    val limite = double("limite").default(0.0)
    val dias = integer("dias").default(0)
    val foto = varchar("foto", S.VARCHAR_LENGTH_120).nullable()

    /**
     * Sucursal de Amaxonia propietaria del cliente (`sucursal.id`), confirmada
     * por negocio (2026-09-04). Solo lectura para el filtro de alcance de
     * sincronización de clientes (ADR-008); nullable porque columnas legacy
     * pueden venir sin asignar.
     */
    val idSucursal = registerColumn<Int>("id_sucursal", LenientIntegerColumnType()).nullable()

    override val primaryKey = PrimaryKey(idCliente)
}

class LenientIntegerColumnType : org.jetbrains.exposed.sql.ColumnType<Int>() {
    override fun sqlType(): String = "VARCHAR(36)"
    override fun valueFromDB(value: Any): Int = when (value) {
        is Number -> value.toInt()
        is String -> value.trim().toIntOrNull() ?: 0
        else -> 0
    }
    override fun notNullValueToDB(value: Int): Any = value.toString()
    override fun nonNullValueToString(value: Int): String = "'$value'"
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
