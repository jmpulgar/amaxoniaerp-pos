package com.amaxoniaerp.features.items.data

import org.jetbrains.exposed.sql.Table

private const val SCHEMA_CANTIDAD_BULTO_PRECISION = 9
private const val SCHEMA_CODIGO_BARRAS2_MAX_LENGTH = 50
private const val SCHEMA_CODIGO_BARRAS3_MAX_LENGTH = 50
private const val SCHEMA_CODIGO_BARRAS_MAX_LENGTH = 50
private const val SCHEMA_CODIGO_FABRICANTE_MAX_LENGTH = 50
private const val SCHEMA_COD_ITEM_MAX_LENGTH = 20
private const val SCHEMA_CONIVA1_PRECISION = 10
private const val SCHEMA_CONIVA2_PRECISION = 10
private const val SCHEMA_CONIVA3_PRECISION = 10
private const val SCHEMA_CONIVA4_PRECISION = 10
private const val SCHEMA_CONIVA5_PRECISION = 10
private const val SCHEMA_COSTO_ACTUAL_PRECISION = 10
private const val SCHEMA_COSTO_ANTERIOR_PRECISION = 10
private const val SCHEMA_COSTO_PROMEDIO_PRECISION = 10
private const val SCHEMA_DESCRIPCION1_MAX_LENGTH = 150
private const val SCHEMA_DESCRIPCION3_MAX_LENGTH = 500
private const val SCHEMA_DESCUENTO1_PRECISION = 10
private const val SCHEMA_DESCUENTO2_PRECISION = 10
private const val SCHEMA_DESCUENTO3_PRECISION = 10
private const val SCHEMA_DESCUENTO4_PRECISION = 10
private const val SCHEMA_DESCUENTO5_PRECISION = 10
private const val SCHEMA_FECHA_CREACION_MAX_LENGTH = 20
private const val SCHEMA_FOTO1_MAX_LENGTH = 60
private const val SCHEMA_FOTO2_MAX_LENGTH = 60
private const val SCHEMA_FOTO3_MAX_LENGTH = 60
private const val SCHEMA_FOTO4_MAX_LENGTH = 60
private const val SCHEMA_FOTO_MAX_LENGTH = 60
private const val SCHEMA_IVA_PRECISION = 10
private const val SCHEMA_PRECIO1_EXTRA_PRECISION = 10
private const val SCHEMA_PRECIO1_PRECISION = 10
private const val SCHEMA_PRECIO2_EXTRA_PRECISION = 10
private const val SCHEMA_PRECIO2_PRECISION = 10
private const val SCHEMA_PRECIO3_EXTRA_PRECISION = 10
private const val SCHEMA_PRECIO3_PRECISION = 10
private const val SCHEMA_PRECIO4_EXTRA_PRECISION = 10
private const val SCHEMA_PRECIO4_PRECISION = 10
private const val SCHEMA_PRECIO5_EXTRA_PRECISION = 10
private const val SCHEMA_PRECIO5_PRECISION = 10
private const val SCHEMA_REFERENCIA_MAX_LENGTH = 50
private const val SCHEMA_TIPO_ITEM_MAX_LENGTH = 50
private const val SCHEMA_UBICACION1_MAX_LENGTH = 50
private const val SCHEMA_UBICACION2_MAX_LENGTH = 50
private const val SCHEMA_UBICACION3_MAX_LENGTH = 50
private const val SCHEMA_UNIDAD_EMPAQUE_MAX_LENGTH = 40
private const val SCHEMA_UNIDAD_O_EMPAQUE_MAX_LENGTH = 40
private const val SCHEMA_UNIDAD_PORCION_MAX_LENGTH = 15
private const val SCHEMA_USUARIO_CREACION_MAX_LENGTH = 60
private const val SCHEMA_UTILIDAD1_PRECISION = 10
private const val SCHEMA_UTILIDAD2_PRECISION = 10
private const val SCHEMA_UTILIDAD3_PRECISION = 10
private const val SCHEMA_UTILIDAD4_PRECISION = 10
private const val SCHEMA_UTILIDAD5_PRECISION = 10

/**
 * Tabla base abstracta con campos comunes de item para todos los países.
 * Define el core de la entidad Item que es idéntico en VE y PA.
 */
abstract class BaseItemsTable(
    tableName: String = "item",
) : Table(tableName) {
    // Identificación
    val idItem = integer("id_item").autoIncrement()
    val codItem = varchar("cod_item", SCHEMA_COD_ITEM_MAX_LENGTH)
    val referencia = varchar("referencia", SCHEMA_REFERENCIA_MAX_LENGTH).nullable()
    val descripcion1 = varchar("descripcion1", SCHEMA_DESCRIPCION1_MAX_LENGTH)
    val descripcion2 = text("descripcion2").nullable()
    val descripcion3 = varchar("descripcion3", SCHEMA_DESCRIPCION3_MAX_LENGTH).nullable()

    // Códigos de barras
    val codigoBarras = varchar("codigo_barras", SCHEMA_CODIGO_BARRAS_MAX_LENGTH).default("")
    val codigoBarras2 = varchar("codigo_barras2", SCHEMA_CODIGO_BARRAS2_MAX_LENGTH).default("")
    val codigoBarras3 = varchar("codigo_barras3", SCHEMA_CODIGO_BARRAS3_MAX_LENGTH).default("")

    // Multimedia
    val foto = varchar("foto", SCHEMA_FOTO_MAX_LENGTH).nullable()
    val foto1 = varchar("foto1", SCHEMA_FOTO1_MAX_LENGTH).default("")
    val foto2 = varchar("foto2", SCHEMA_FOTO2_MAX_LENGTH).default("")
    val foto3 = varchar("foto3", SCHEMA_FOTO3_MAX_LENGTH).default("")
    val foto4 = varchar("foto4", SCHEMA_FOTO4_MAX_LENGTH).default("")

    // Categorización (cod_departamento o departamento_id según BD; filtro por departmentId usa el que exista)
    val codDepartamento = integer("cod_departamento").default(0)
    val departamentoId = integer("departamento_id").default(0)
    val seccionId = integer("seccion_id").default(0)
    val familiaId = integer("familia_id").default(0)
    val subfamiliaId = integer("subfamilia_id").default(0)
    val marcaId = integer("marca_id").default(0)
    val codLinea = integer("cod_linea").default(0)
    val lineaId = integer("linea_id").default(0)
    val origenId = integer("origen_id").default(0)
    val tipoId = integer("tipo_id").default(0)
    val tecnologiaId = integer("tecnologia_id").default(0)

    // Impuestos
    val montoExento = bool("monto_exento").default(false)
    val iva = decimal("iva", SCHEMA_IVA_PRECISION, 2).default(0.0.toBigDecimal())

    // Costos
    val costoActual = decimal("costo_actual", SCHEMA_COSTO_ACTUAL_PRECISION, 2).default(0.0.toBigDecimal())
    val costoPromedio = decimal("costo_promedio", SCHEMA_COSTO_PROMEDIO_PRECISION, 2).default(0.0.toBigDecimal())
    val costoAnterior = decimal("costo_anterior", SCHEMA_COSTO_ANTERIOR_PRECISION, 2).default(0.0.toBigDecimal())

    // Precios nivel 1
    val precio1 = decimal("precio1", SCHEMA_PRECIO1_PRECISION, 2).default(0.0.toBigDecimal())
    val utilidad1 = decimal("utilidad1", SCHEMA_UTILIDAD1_PRECISION, 2).default(0.0.toBigDecimal())
    val coniva1 = decimal("coniva1", SCHEMA_CONIVA1_PRECISION, 2).default(0.0.toBigDecimal())
    val descuento1 = decimal("descuento1", SCHEMA_DESCUENTO1_PRECISION, 2).default(0.0.toBigDecimal())

    // Precios nivel 2
    val precio2 = decimal("precio2", SCHEMA_PRECIO2_PRECISION, 2).default(0.0.toBigDecimal())
    val utilidad2 = decimal("utilidad2", SCHEMA_UTILIDAD2_PRECISION, 2).default(0.0.toBigDecimal())
    val coniva2 = decimal("coniva2", SCHEMA_CONIVA2_PRECISION, 2).default(0.0.toBigDecimal())
    val descuento2 = decimal("descuento2", SCHEMA_DESCUENTO2_PRECISION, 2).default(0.0.toBigDecimal())

    // Precios nivel 3
    val precio3 = decimal("precio3", SCHEMA_PRECIO3_PRECISION, 2).default(0.0.toBigDecimal())
    val utilidad3 = decimal("utilidad3", SCHEMA_UTILIDAD3_PRECISION, 2).default(0.0.toBigDecimal())
    val coniva3 = decimal("coniva3", SCHEMA_CONIVA3_PRECISION, 2).default(0.0.toBigDecimal())
    val descuento3 = decimal("descuento3", SCHEMA_DESCUENTO3_PRECISION, 2).default(0.0.toBigDecimal())

    // Precios nivel 4
    val precio4 = decimal("precio4", SCHEMA_PRECIO4_PRECISION, 2).default(0.0.toBigDecimal())
    val utilidad4 = decimal("utilidad4", SCHEMA_UTILIDAD4_PRECISION, 2).default(0.0.toBigDecimal())
    val coniva4 = decimal("coniva4", SCHEMA_CONIVA4_PRECISION, 2).default(0.0.toBigDecimal())
    val descuento4 = decimal("descuento4", SCHEMA_DESCUENTO4_PRECISION, 2).default(0.0.toBigDecimal())

    // Precios nivel 5
    val precio5 = decimal("precio5", SCHEMA_PRECIO5_PRECISION, 2).default(0.0.toBigDecimal())
    val utilidad5 = decimal("utilidad5", SCHEMA_UTILIDAD5_PRECISION, 2).default(0.0.toBigDecimal())
    val coniva5 = decimal("coniva5", SCHEMA_CONIVA5_PRECISION, 2).default(0.0.toBigDecimal())
    val descuento5 = decimal("descuento5", SCHEMA_DESCUENTO5_PRECISION, 2).default(0.0.toBigDecimal())

    val precio1Extra = decimal("precio1_extra", SCHEMA_PRECIO1_EXTRA_PRECISION, 2).nullable()
    val precio2Extra = decimal("precio2_extra", SCHEMA_PRECIO2_EXTRA_PRECISION, 2).nullable()
    val precio3Extra = decimal("precio3_extra", SCHEMA_PRECIO3_EXTRA_PRECISION, 2).nullable()
    val precio4Extra = decimal("precio4_extra", SCHEMA_PRECIO4_EXTRA_PRECISION, 2).nullable()
    val precio5Extra = decimal("precio5_extra", SCHEMA_PRECIO5_EXTRA_PRECISION, 2).nullable()

    // Estado
    val estatus = varchar("estatus", 1).default("A")
    val codItemForma = integer("cod_item_forma").default(1)
    val tipoProd = integer("tipo_prod").default(2)

    // Auditoría
    val usuarioCreacion = varchar("usuario_creacion", SCHEMA_USUARIO_CREACION_MAX_LENGTH).default("API")
    val fechaCreacion = varchar("fecha_creacion", SCHEMA_FECHA_CREACION_MAX_LENGTH).nullable()

    // Configuración
    val seriales = bool("seriales").default(false)
    val garantia = bool("garantia").default(false)
    val precioXEscala = bool("precio_x_escala").default(false)
    val comisionXItem = bool("comision_x_item").default(false)

    // Existencias
    val existenciaTotal = integer("existencia_total").default(0)
    val existenciaMin = integer("existencia_min").default(0)
    val existenciaMax = integer("existencia_max").default(0)

    // Campos adicionales base
    val codigoFabricante = varchar("codigo_fabricante", SCHEMA_CODIGO_FABRICANTE_MAX_LENGTH).nullable()
    val unidadEmpaque = varchar("unidad_empaque", SCHEMA_UNIDAD_EMPAQUE_MAX_LENGTH).nullable()
    val cantidadBulto = decimal("cantidad_bulto", SCHEMA_CANTIDAD_BULTO_PRECISION, 2).nullable()
    val unidadPorcion = varchar("unidad_porcion", SCHEMA_UNIDAD_PORCION_MAX_LENGTH).nullable()
    val unidadOEmpaque = varchar("unidad_o_empaque", SCHEMA_UNIDAD_O_EMPAQUE_MAX_LENGTH).nullable()
    val cantidad = integer("cantidad").default(0)
    val tipoItem = varchar("tipo_item", SCHEMA_TIPO_ITEM_MAX_LENGTH).nullable()
    val ubicacion1 = varchar("ubicacion1", SCHEMA_UBICACION1_MAX_LENGTH).nullable()
    val ubicacion2 = varchar("ubicacion2", SCHEMA_UBICACION2_MAX_LENGTH).nullable()
    val ubicacion3 = varchar("ubicacion3", SCHEMA_UBICACION3_MAX_LENGTH).nullable()

    override val primaryKey = PrimaryKey(idItem)
}

/**
 * Tabla de items para Venezuela (Schema TYPE_B).
 * Campos específicos: balanza, multimoneda.
 * Nota: 'vidible_pos' tiene un typo en la base de datos real.
 */
object ItemsTableVE : BaseItemsTable("item") {
    val balanza = bool("balanza").default(false)
    val idMonedaBase = integer("id_moneda_base").nullable()
    val idTipoCostoMultimoneda = integer("id_tipo_costo_multimoneda").nullable()
    val visiblePos = varchar("vidible_pos", 1).nullable()
}

/**
 * Tabla de items para Panamá (Schema TYPE_A).
 * Campos específicos: Kits, Gobierno.
 */
object ItemsTablePA : BaseItemsTable("item") {
    val visiblePos = char("visible_pos").default('T')
    val detallesKit = varchar("detalles_kit", 1).default("T")
    val idSegmentoGob = integer("id_segmento_gob").nullable()
    val idFamiliaGob = integer("id_familia_gob").nullable()
    val sumarItemsKit = bool("sumar_items_kit").default(false)
    val ticketItemsKit = bool("ticket_items_kit").default(false)
}

/**
 * Factory para obtener la tabla correcta según el país.
 */
object ItemsTableFactory {
    fun getTableForCountry(countryCode: String): BaseItemsTable =
        when (countryCode.uppercase()) {
            "VE" -> ItemsTableVE
            "PA" -> ItemsTablePA
            else -> ItemsTableVE
        }
}

/** Tipos de esquema: TYPE_A = Panamá, TYPE_B = Venezuela. */
enum class SchemaType {
    TYPE_A,
    TYPE_B,
}
