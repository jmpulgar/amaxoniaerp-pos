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

    val nombreComercial = varchar("nombre_comercial", S.VARCHAR_LENGTH_200).nullable()
    val representante = varchar("representante", S.VARCHAR_LENGTH_100).nullable()
    val telefono2 = varchar("telefono2", S.VARCHAR_LENGTH_50).nullable()
    val web = varchar("web", S.VARCHAR_LENGTH_100).nullable()
    val fax = varchar("fax", S.VARCHAR_LENGTH_50).nullable()
    val codigoCrm = varchar("codigo_crm", S.VARCHAR_LENGTH_50).nullable()
    val actividad = varchar("actividad", S.VARCHAR_LENGTH_100).nullable()
    val empresaZonaLibre = varchar("empresa_zona_libre", 10).nullable().default("0")
    val pedidosOnline = varchar("pedidos", 10).nullable().default("0")
    val fnacimiento = varchar("fnacimiento", S.VARCHAR_LENGTH_64).nullable()
    val genero = varchar("genero", 10).nullable()
    val tolerancia = integer("tolerancia").nullable().default(0)
    val porcParcial = double("porc_parcial").nullable().default(0.0)
    val porcDescuentoGlobal = double("porc_descuento_global").nullable().default(0.0)
    val cuentaContable = varchar("cuenta_contable", S.VARCHAR_LENGTH_50).nullable()
    val codVendedor = varchar("cod_vendedor", S.VARCHAR_LENGTH_50).nullable()
    val codZona = varchar("cod_zona", S.VARCHAR_LENGTH_50).nullable()
    val lugarEntrega = varchar("lugar_entrega", S.VARCHAR_LENGTH_200).nullable()
    val contribuyenteEspecial = varchar("contribuyente_especial", 10).nullable().default("0")
    val retenidoPorCliente = varchar("retenido_por_cliente", 50).nullable()
    val codEntidad = varchar("cod_entidad", 50).nullable()
    val aplicaItbms = varchar("aplica_itbms", 10).nullable().default("1")
    val clienteFidelizacion = integer("cliente_fidelizacion").nullable().default(0)
    val clienteFidelizacionSucursal = integer("cliente_fidelizacion_sucursal").nullable().default(0)
    val clienteFidelizacionNivel = integer("cliente_fidelizacion_nivel").nullable().default(0)
    val clienteFidelizacionPuntos = double("cliente_fidelizacion_puntos").nullable().default(0.0)
    val clienteFidelizacionFecha = varchar("cliente_fidelizacion_fecha", S.VARCHAR_LENGTH_64).nullable()
    val direccionCiudad = varchar("direccion_ciudad", S.VARCHAR_LENGTH_100).nullable()
    val localidad = varchar("localidad", S.VARCHAR_LENGTH_100).nullable()
    val direccionSector = varchar("direccion_sector", S.VARCHAR_LENGTH_100).nullable()
    val direccionUrbanizacion = varchar("direccion_urbanizacion", S.VARCHAR_LENGTH_100).nullable()
    val direccionCalleAv = varchar("direccion_calle_av", S.VARCHAR_LENGTH_100).nullable()
    val direccionEdificioCasa = varchar("direccion_edificio_casa", S.VARCHAR_LENGTH_100).nullable()
    val direccionPiso = varchar("direccion_piso", S.VARCHAR_LENGTH_50).nullable()
    val direccionOficina = varchar("direccion_oficina", S.VARCHAR_LENGTH_50).nullable()
    val direccionReferencia = varchar("direccion_referencia", S.VARCHAR_LENGTH_200).nullable()
    val alterna = varchar("altena", S.VARCHAR_LENGTH_200).nullable()
    val alterna2 = varchar("alterna2", S.VARCHAR_LENGTH_200).nullable()

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

    override fun valueFromDB(value: Any): Int =
        when (value) {
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
