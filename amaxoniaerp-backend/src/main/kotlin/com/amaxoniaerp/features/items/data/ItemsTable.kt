package com.amaxoniaerp.features.items.data

import com.amaxoniaerp.core.database.SchemaDimensions
import org.jetbrains.exposed.sql.Table

object ItemsTable : Table("item") {
    val idItem = integer("id_item").autoIncrement()
    val codItem = varchar("cod_item", SchemaDimensions.VARCHAR_LENGTH_20)
    val referencia = varchar("referencia", SchemaDimensions.VARCHAR_LENGTH_50).nullable()
    val descripcion1 = varchar("descripcion1", SchemaDimensions.VARCHAR_LENGTH_150)
    val codigoBarras = varchar("codigo_barras", SchemaDimensions.VARCHAR_LENGTH_50).default("")
    val codigoBarras2 = varchar("codigo_barras2", SchemaDimensions.VARCHAR_LENGTH_50).default("")
    val codigoBarras3 = varchar("codigo_barras3", SchemaDimensions.VARCHAR_LENGTH_50).default("")
    val foto = varchar("foto", SchemaDimensions.VARCHAR_LENGTH_60).nullable()

    val codDepartamento = integer("cod_departamento").default(0)
    val seccionId = integer("seccion_id").default(0)
    val familiaId = integer("familia_id").default(0)
    val subfamiliaId = integer("subfamilia_id").default(0)
    val marcaId = integer("marca_id").default(0)
    val lineaId = integer("linea_id").default(0)
    val idSegmentoGob = integer("id_segmento_gob").nullable()
    val idFamiliaGob = integer("id_familia_gob").nullable()

    val montoExento = bool("monto_exento")
    val iva = decimal("iva", SchemaDimensions.DECIMAL_PRECISION_10, 2).default(0.0.toBigDecimal())

    val costoActual = decimal("costo_actual", SchemaDimensions.DECIMAL_PRECISION_10, 2).default(0.0.toBigDecimal())
    val costoPromedio = decimal("costo_promedio", SchemaDimensions.DECIMAL_PRECISION_10, 2).default(0.0.toBigDecimal())
    val costoAnterior = decimal("costo_anterior", SchemaDimensions.DECIMAL_PRECISION_10, 2).default(0.0.toBigDecimal())
    val costoCif = decimal("costo_cif", SchemaDimensions.DECIMAL_PRECISION_9, 2).default(0.0.toBigDecimal())
    val costoFob = decimal("costo_fob", SchemaDimensions.DECIMAL_PRECISION_20, 2).default(0.0.toBigDecimal())
    val costoProcesamiento =
        decimal(
            "costo_procesamiento",
            SchemaDimensions.DECIMAL_PRECISION_10,
            SchemaDimensions.DECIMAL_SCALE_4,
        ).default(0.0.toBigDecimal())
    val comisionXItem = bool("comision_x_item")
    val costoFranco = decimal("costo_franco", SchemaDimensions.DECIMAL_PRECISION_10, 2).default(0.0.toBigDecimal())
    val costoOrigen = decimal("costo_origen", SchemaDimensions.DECIMAL_PRECISION_9, 2).default(0.0.toBigDecimal())

    val precio1 = decimal("precio1", SchemaDimensions.DECIMAL_PRECISION_10, 2).default(0.0.toBigDecimal())
    val utilidad1 = decimal("utilidad1", SchemaDimensions.DECIMAL_PRECISION_10, 2).default(0.0.toBigDecimal())
    val coniva1 = decimal("coniva1", SchemaDimensions.DECIMAL_PRECISION_10, 2).default(0.0.toBigDecimal())
    val descuento1 = decimal("descuento1", SchemaDimensions.DECIMAL_PRECISION_10, 2).default(0.0.toBigDecimal())

    val precio2 = decimal("precio2", SchemaDimensions.DECIMAL_PRECISION_10, 2).default(0.0.toBigDecimal())
    val utilidad2 = decimal("utilidad2", SchemaDimensions.DECIMAL_PRECISION_10, 2).default(0.0.toBigDecimal())
    val coniva2 = decimal("coniva2", SchemaDimensions.DECIMAL_PRECISION_10, 2).default(0.0.toBigDecimal())
    val descuento2 = decimal("descuento2", SchemaDimensions.DECIMAL_PRECISION_10, 2).default(0.0.toBigDecimal())

    val precio3 = decimal("precio3", SchemaDimensions.DECIMAL_PRECISION_10, 2).default(0.0.toBigDecimal())
    val utilidad3 = decimal("utilidad3", SchemaDimensions.DECIMAL_PRECISION_10, 2).default(0.0.toBigDecimal())
    val coniva3 = decimal("coniva3", SchemaDimensions.DECIMAL_PRECISION_10, 2).default(0.0.toBigDecimal())
    val descuento3 = decimal("descuento3", SchemaDimensions.DECIMAL_PRECISION_10, 2).default(0.0.toBigDecimal())

    val estatus = varchar("estatus", 1).default("A")
    val codItemForma = integer("cod_item_forma").default(1)
    val tipoProd = integer("tipo_prod").default(2)
    val usuarioCreacion = varchar("usuario_creacion", SchemaDimensions.VARCHAR_LENGTH_60).default("API")

    val cuentaContable1 = varchar("cuenta_contable1", SchemaDimensions.VARCHAR_LENGTH_25).default("")
    val cuentaContable2 = varchar("cuenta_contable2", SchemaDimensions.VARCHAR_LENGTH_25).default("")
    val codigoCuenta = varchar("codigo_cuenta", SchemaDimensions.VARCHAR_LENGTH_15).default("")
    val serial1 = varchar("serial1", SchemaDimensions.VARCHAR_LENGTH_25).default("")
    val origen = varchar("origen", SchemaDimensions.VARCHAR_LENGTH_45).default("")
    val temporada = varchar("temporada", SchemaDimensions.VARCHAR_LENGTH_45).default("")
    val mateCompoClase = varchar("mate_compo_clase", SchemaDimensions.VARCHAR_LENGTH_45).default("")
    val tejido = varchar("tejido", SchemaDimensions.VARCHAR_LENGTH_45).default("")
    val regSanit = varchar("reg_sanit", SchemaDimensions.VARCHAR_LENGTH_45).default("")
    val codBarraBulto = varchar("cod_barra_bulto", SchemaDimensions.VARCHAR_LENGTH_45).default("")
    val observacion = text("observacion").default("")
    val foto1 = varchar("foto1", SchemaDimensions.VARCHAR_LENGTH_60).default("")
    val foto2 = varchar("foto2", SchemaDimensions.VARCHAR_LENGTH_60).default("")
    val foto3 = varchar("foto3", SchemaDimensions.VARCHAR_LENGTH_60).default("")
    val foto4 = varchar("foto4", SchemaDimensions.VARCHAR_LENGTH_60).default("")
    val contLicenNro = varchar("cont_licen_nro", SchemaDimensions.VARCHAR_LENGTH_45).default("")
    val aprobArte = varchar("aprob_arte", SchemaDimensions.VARCHAR_LENGTH_45).default("")
    val propiedad = varchar("propiedad", SchemaDimensions.VARCHAR_LENGTH_45).default("")

    val cantidadBulto = decimal("cantidad_bulto", SchemaDimensions.DECIMAL_PRECISION_9, 2).default(1.0.toBigDecimal())
    val kilosBulto = decimal("kilos_bulto", SchemaDimensions.DECIMAL_PRECISION_9, 2).default(0.0.toBigDecimal())
    val proveedor = integer("proveedor").default(0)
    val posicionArancel = integer("posicion_arancel").default(0)
    val ubicacion = integer("ubicacion").default(0)
    val codigoBase = integer("codigo_base").default(0)
    val sinuso = integer("sinuso").default(0)
    val cubi1 = varchar("cubi1", SchemaDimensions.VARCHAR_LENGTH_5).default("")
    val cubi2 = varchar("cubi2", SchemaDimensions.VARCHAR_LENGTH_5).default("")
    val cubi3 = varchar("cubi3", SchemaDimensions.VARCHAR_LENGTH_5).default("")
    val cubi4 = decimal("cubi4", SchemaDimensions.DECIMAL_PRECISION_6, SchemaDimensions.DECIMAL_SCALE_4).default(0.0.toBigDecimal())
    val cubi5 = decimal("cubi5", SchemaDimensions.DECIMAL_PRECISION_6, SchemaDimensions.DECIMAL_SCALE_4).default(0.0.toBigDecimal())
    val unidadMedida = integer("unidad_medida").default(0)
    val innerBulto = decimal("inner_bulto", SchemaDimensions.DECIMAL_PRECISION_9, 2).default(0.0.toBigDecimal())
    val caracteristicas = varchar("caracteristicas", SchemaDimensions.VARCHAR_LENGTH_60).default("")
    val ingles = varchar("ingles", SchemaDimensions.VARCHAR_LENGTH_60).default("")
    val pesoNeto = decimal("peso_neto", SchemaDimensions.DECIMAL_PRECISION_9, 2).default(0.0.toBigDecimal())
    val procesamiento = integer("procesamiento").default(0)
    val sucursal = integer("sucursal").default(0)
    val turno = integer("turno").default(0)
    val horas = integer("horas").default(0)
    val rolSemanal = integer("rol_semanal").default(0)
    val codigoCrm = varchar("codigo_crm", SchemaDimensions.VARCHAR_LENGTH_50).default("")
    val unidadOEmpaque = varchar("unidad_o_empaque", SchemaDimensions.VARCHAR_LENGTH_10).default("UNIDAD")

    override val primaryKey = PrimaryKey(idItem)
}
