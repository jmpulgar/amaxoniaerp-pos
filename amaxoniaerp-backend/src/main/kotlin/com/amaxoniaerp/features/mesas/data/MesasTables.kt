package com.amaxoniaerp.features.mesas.data

import org.jetbrains.exposed.sql.Table

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
    val nombre = varchar("nombre", 255)
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
    val codigo = varchar("codigo", 100).nullable()
    val nombre = varchar("nombre", 255).nullable()
    val capacidad = integer("capacidad").nullable()
    val forma = varchar("forma", 50).nullable()
    val posicionX = decimal("posicion_x", 14, 2).nullable()
    val posicionY = decimal("posicion_y", 14, 2).nullable()
    val ancho = decimal("ancho", 14, 2).nullable()
    val alto = decimal("alto", 14, 2).nullable()
    val rotacion = decimal("rotacion", 14, 2).nullable()
    val activo = integer("activo").default(1)

    override val primaryKey = PrimaryKey(id)
}
