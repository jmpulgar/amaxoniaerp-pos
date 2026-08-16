package com.amaxoniaerp.features.items.data

import org.jetbrains.exposed.sql.Table
import com.amaxoniaerp.core.database.SchemaDimensions as S

/**
 * Tabla base abstracta con campos comunes de item para todos los países.
 * Define el core de la entidad Item que es idéntico en VE y PA.
 */
abstract class BaseItemsTable(
    tableName: String = "item",
) : Table(tableName) {
    // Identificación
    val idItem = integer("id_item").autoIncrement()
    val codItem = varchar("cod_item", S.VARCHAR_LENGTH_20)
    val referencia = varchar("referencia", S.VARCHAR_LENGTH_50).nullable()
    val descripcion1 = varchar("descripcion1", S.VARCHAR_LENGTH_150)
    val descripcion2 = text("descripcion2").nullable()
    val descripcion3 = varchar("descripcion3", S.VARCHAR_LENGTH_500).nullable()

    // Códigos de barras
    val codigoBarras = varchar("codigo_barras", S.VARCHAR_LENGTH_50).default("")
    val codigoBarras2 = varchar("codigo_barras2", S.VARCHAR_LENGTH_50).default("")
    val codigoBarras3 = varchar("codigo_barras3", S.VARCHAR_LENGTH_50).default("")

    // Multimedia
    val foto = varchar("foto", S.VARCHAR_LENGTH_60).nullable()
    val foto1 = varchar("foto1", S.VARCHAR_LENGTH_60).default("")
    val foto2 = varchar("foto2", S.VARCHAR_LENGTH_60).default("")
    val foto3 = varchar("foto3", S.VARCHAR_LENGTH_60).default("")
    val foto4 = varchar("foto4", S.VARCHAR_LENGTH_60).default("")

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
    val iva = decimal("iva", S.DECIMAL_PRECISION_10, 2).default(0.0.toBigDecimal())

    // Costos
    val costoActual = decimal("costo_actual", S.DECIMAL_PRECISION_10, 2).default(0.0.toBigDecimal())
    val costoPromedio = decimal("costo_promedio", S.DECIMAL_PRECISION_10, 2).default(0.0.toBigDecimal())
    val costoAnterior = decimal("costo_anterior", S.DECIMAL_PRECISION_10, 2).default(0.0.toBigDecimal())

    // Precios nivel 1
    val precio1 = decimal("precio1", S.DECIMAL_PRECISION_10, 2).default(0.0.toBigDecimal())
    val utilidad1 = decimal("utilidad1", S.DECIMAL_PRECISION_10, 2).default(0.0.toBigDecimal())
    val coniva1 = decimal("coniva1", S.DECIMAL_PRECISION_10, 2).default(0.0.toBigDecimal())
    val descuento1 = decimal("descuento1", S.DECIMAL_PRECISION_10, 2).default(0.0.toBigDecimal())

    // Precios nivel 2
    val precio2 = decimal("precio2", S.DECIMAL_PRECISION_10, 2).default(0.0.toBigDecimal())
    val utilidad2 = decimal("utilidad2", S.DECIMAL_PRECISION_10, 2).default(0.0.toBigDecimal())
    val coniva2 = decimal("coniva2", S.DECIMAL_PRECISION_10, 2).default(0.0.toBigDecimal())
    val descuento2 = decimal("descuento2", S.DECIMAL_PRECISION_10, 2).default(0.0.toBigDecimal())

    // Precios nivel 3
    val precio3 = decimal("precio3", S.DECIMAL_PRECISION_10, 2).default(0.0.toBigDecimal())
    val utilidad3 = decimal("utilidad3", S.DECIMAL_PRECISION_10, 2).default(0.0.toBigDecimal())
    val coniva3 = decimal("coniva3", S.DECIMAL_PRECISION_10, 2).default(0.0.toBigDecimal())
    val descuento3 = decimal("descuento3", S.DECIMAL_PRECISION_10, 2).default(0.0.toBigDecimal())

    // Precios nivel 4
    val precio4 = decimal("precio4", S.DECIMAL_PRECISION_10, 2).default(0.0.toBigDecimal())
    val utilidad4 = decimal("utilidad4", S.DECIMAL_PRECISION_10, 2).default(0.0.toBigDecimal())
    val coniva4 = decimal("coniva4", S.DECIMAL_PRECISION_10, 2).default(0.0.toBigDecimal())
    val descuento4 = decimal("descuento4", S.DECIMAL_PRECISION_10, 2).default(0.0.toBigDecimal())

    // Precios nivel 5
    val precio5 = decimal("precio5", S.DECIMAL_PRECISION_10, 2).default(0.0.toBigDecimal())
    val utilidad5 = decimal("utilidad5", S.DECIMAL_PRECISION_10, 2).default(0.0.toBigDecimal())
    val coniva5 = decimal("coniva5", S.DECIMAL_PRECISION_10, 2).default(0.0.toBigDecimal())
    val descuento5 = decimal("descuento5", S.DECIMAL_PRECISION_10, 2).default(0.0.toBigDecimal())

    val precio1Extra = decimal("precio1_extra", S.DECIMAL_PRECISION_10, 2).nullable()
    val precio2Extra = decimal("precio2_extra", S.DECIMAL_PRECISION_10, 2).nullable()
    val precio3Extra = decimal("precio3_extra", S.DECIMAL_PRECISION_10, 2).nullable()
    val precio4Extra = decimal("precio4_extra", S.DECIMAL_PRECISION_10, 2).nullable()
    val precio5Extra = decimal("precio5_extra", S.DECIMAL_PRECISION_10, 2).nullable()

    // Estado
    val estatus = varchar("estatus", 1).default("A")
    val codItemForma = integer("cod_item_forma").default(1)
    val tipoProd = integer("tipo_prod").default(2)

    // Auditoría
    val usuarioCreacion = varchar("usuario_creacion", S.VARCHAR_LENGTH_60).default("API")
    val fechaCreacion = varchar("fecha_creacion", S.VARCHAR_LENGTH_20).nullable()

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
    val codigoFabricante = varchar("codigo_fabricante", S.VARCHAR_LENGTH_50).nullable()
    val unidadEmpaque = varchar("unidad_empaque", S.VARCHAR_LENGTH_40).nullable()
    val cantidadBulto = decimal("cantidad_bulto", S.DECIMAL_PRECISION_9, 2).nullable()
    val unidadPorcion = varchar("unidad_porcion", S.VARCHAR_LENGTH_15).nullable()
    val unidadOEmpaque = varchar("unidad_o_empaque", S.VARCHAR_LENGTH_40).nullable()
    val cantidad = integer("cantidad").default(0)
    val tipoItem = varchar("tipo_item", S.VARCHAR_LENGTH_50).nullable()
    val ubicacion1 = varchar("ubicacion1", S.VARCHAR_LENGTH_50).nullable()
    val ubicacion2 = varchar("ubicacion2", S.VARCHAR_LENGTH_50).nullable()
    val ubicacion3 = varchar("ubicacion3", S.VARCHAR_LENGTH_50).nullable()

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
