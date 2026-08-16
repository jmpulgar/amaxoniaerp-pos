package com.amaxoniaerp.features.mesas.data

import org.jetbrains.exposed.sql.Table

private const val SCHEMA_ALTO_PRECISION = 14
private const val SCHEMA_ANCHO_PRECISION = 14
private const val SCHEMA_CODIGO_MAX_LENGTH = 100
private const val SCHEMA_FORMA_MAX_LENGTH = 50
private const val SCHEMA_NOMBRE_MAX_LENGTH = 255
private const val SCHEMA_POSICION_X_PRECISION = 14
private const val SCHEMA_POSICION_Y_PRECISION = 14
private const val SCHEMA_ROTACION_PRECISION = 14

/**
 * Áreas de una sucursal. La tabla física se sigue llamando `plantas` (nombre heredado del
 * administrativo); en el contrato público del POS y en la UI el concepto es "Área".
 *
 * Los tipos se declaran de forma tolerante a propósito: `activo` como entero (igual que
 * `caja.activo` en [com.amaxoniaerp.features.caja.data.CajaTable]) porque en MySQL es
 * `tinyint`, y `descripcion`/`imagen` como texto para aceptar tanto `varchar` como `text`.
 */
object PlantasTable : Table("plantas") {
    val id = integer("id")
    val sucursalId = integer("sucursal_id")
    val nombre = varchar("nombre", SCHEMA_NOMBRE_MAX_LENGTH)
    val descripcion = text("descripcion").nullable()
    val imagen = text("imagen").nullable()
    val orden = integer("orden").nullable()
    val activo = integer("activo").default(1)

    override val primaryKey = PrimaryKey(id)
}

/**
 * Mesas de un área (`mesas.planta_id` -> `plantas.id`).
 *
 * Las columnas de geometría se declaran `decimal` porque JDBC lee sin problema una columna
 * `int`, `decimal`, `float` o `double` como `BigDecimal`, mientras que lo contrario
 * (declarar `integer` sobre una columna decimal) truncaría o fallaría. En esta fase la
 * geometría solo se transporta: no se usa para pintar ningún plano.
 */
object MesasTable : Table("mesas") {
    val id = integer("id")
    val plantaId = integer("planta_id")
    val codigo = varchar("codigo", SCHEMA_CODIGO_MAX_LENGTH).nullable()
    val nombre = varchar("nombre", SCHEMA_NOMBRE_MAX_LENGTH).nullable()
    val capacidad = integer("capacidad").nullable()
    val forma = varchar("forma", SCHEMA_FORMA_MAX_LENGTH).nullable()
    val posicionX = decimal("posicion_x", SCHEMA_POSICION_X_PRECISION, 2).nullable()
    val posicionY = decimal("posicion_y", SCHEMA_POSICION_Y_PRECISION, 2).nullable()
    val ancho = decimal("ancho", SCHEMA_ANCHO_PRECISION, 2).nullable()
    val alto = decimal("alto", SCHEMA_ALTO_PRECISION, 2).nullable()
    val rotacion = decimal("rotacion", SCHEMA_ROTACION_PRECISION, 2).nullable()
    val activo = integer("activo").default(1)

    override val primaryKey = PrimaryKey(id)
}
