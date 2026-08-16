package com.amaxoniaerp.features.items.data

import org.jetbrains.exposed.sql.Table

private const val SCHEMA_APROB_ARTE_MAX_LENGTH = 45
private const val SCHEMA_CANTIDAD_BULTO_PRECISION = 9
private const val SCHEMA_CARACTERISTICAS_MAX_LENGTH = 60
private const val SCHEMA_CODIGO_BARRAS2_MAX_LENGTH = 50
private const val SCHEMA_CODIGO_BARRAS3_MAX_LENGTH = 50
private const val SCHEMA_CODIGO_BARRAS_MAX_LENGTH = 50
private const val SCHEMA_CODIGO_CRM_MAX_LENGTH = 50
private const val SCHEMA_CODIGO_CUENTA_MAX_LENGTH = 15
private const val SCHEMA_COD_BARRA_BULTO_MAX_LENGTH = 45
private const val SCHEMA_COD_ITEM_MAX_LENGTH = 20
private const val SCHEMA_CONIVA1_PRECISION = 10
private const val SCHEMA_CONIVA2_PRECISION = 10
private const val SCHEMA_CONIVA3_PRECISION = 10
private const val SCHEMA_CONT_LICEN_NRO_MAX_LENGTH = 45
private const val SCHEMA_COSTO_ACTUAL_PRECISION = 10
private const val SCHEMA_COSTO_ANTERIOR_PRECISION = 10
private const val SCHEMA_COSTO_CIF_PRECISION = 9
private const val SCHEMA_COSTO_FOB_PRECISION = 20
private const val SCHEMA_COSTO_FRANCO_PRECISION = 10
private const val SCHEMA_COSTO_ORIGEN_PRECISION = 9
private const val SCHEMA_COSTO_PROCESAMIENTO_PRECISION = 10
private const val SCHEMA_COSTO_PROCESAMIENTO_SCALE = 4
private const val SCHEMA_COSTO_PROMEDIO_PRECISION = 10
private const val SCHEMA_CUBI1_MAX_LENGTH = 5
private const val SCHEMA_CUBI2_MAX_LENGTH = 5
private const val SCHEMA_CUBI3_MAX_LENGTH = 5
private const val SCHEMA_CUBI4_PRECISION = 6
private const val SCHEMA_CUBI4_SCALE = 4
private const val SCHEMA_CUBI5_PRECISION = 6
private const val SCHEMA_CUBI5_SCALE = 4
private const val SCHEMA_CUENTA_CONTABLE1_MAX_LENGTH = 25
private const val SCHEMA_CUENTA_CONTABLE2_MAX_LENGTH = 25
private const val SCHEMA_DESCRIPCION1_MAX_LENGTH = 150
private const val SCHEMA_DESCUENTO1_PRECISION = 10
private const val SCHEMA_DESCUENTO2_PRECISION = 10
private const val SCHEMA_DESCUENTO3_PRECISION = 10
private const val SCHEMA_FOTO1_MAX_LENGTH = 60
private const val SCHEMA_FOTO2_MAX_LENGTH = 60
private const val SCHEMA_FOTO3_MAX_LENGTH = 60
private const val SCHEMA_FOTO4_MAX_LENGTH = 60
private const val SCHEMA_FOTO_MAX_LENGTH = 60
private const val SCHEMA_INGLES_MAX_LENGTH = 60
private const val SCHEMA_INNER_BULTO_PRECISION = 9
private const val SCHEMA_IVA_PRECISION = 10
private const val SCHEMA_KILOS_BULTO_PRECISION = 9
private const val SCHEMA_MATE_COMPO_CLASE_MAX_LENGTH = 45
private const val SCHEMA_ORIGEN_MAX_LENGTH = 45
private const val SCHEMA_PESO_NETO_PRECISION = 9
private const val SCHEMA_PRECIO1_PRECISION = 10
private const val SCHEMA_PRECIO2_PRECISION = 10
private const val SCHEMA_PRECIO3_PRECISION = 10
private const val SCHEMA_PROPIEDAD_MAX_LENGTH = 45
private const val SCHEMA_REFERENCIA_MAX_LENGTH = 50
private const val SCHEMA_REG_SANIT_MAX_LENGTH = 45
private const val SCHEMA_SERIAL1_MAX_LENGTH = 25
private const val SCHEMA_TEJIDO_MAX_LENGTH = 45
private const val SCHEMA_TEMPORADA_MAX_LENGTH = 45
private const val SCHEMA_UNIDAD_O_EMPAQUE_MAX_LENGTH = 10
private const val SCHEMA_USUARIO_CREACION_MAX_LENGTH = 60
private const val SCHEMA_UTILIDAD1_PRECISION = 10
private const val SCHEMA_UTILIDAD2_PRECISION = 10
private const val SCHEMA_UTILIDAD3_PRECISION = 10

object ItemsTable : Table("item") {
    val idItem = integer("id_item").autoIncrement()
    val codItem = varchar("cod_item", SCHEMA_COD_ITEM_MAX_LENGTH)
    val referencia = varchar("referencia", SCHEMA_REFERENCIA_MAX_LENGTH).nullable()
    val descripcion1 = varchar("descripcion1", SCHEMA_DESCRIPCION1_MAX_LENGTH)
    val codigoBarras = varchar("codigo_barras", SCHEMA_CODIGO_BARRAS_MAX_LENGTH).default("")
    val codigoBarras2 = varchar("codigo_barras2", SCHEMA_CODIGO_BARRAS2_MAX_LENGTH).default("")
    val codigoBarras3 = varchar("codigo_barras3", SCHEMA_CODIGO_BARRAS3_MAX_LENGTH).default("")
    val foto = varchar("foto", SCHEMA_FOTO_MAX_LENGTH).nullable()

    val codDepartamento = integer("cod_departamento").default(0)
    val seccionId = integer("seccion_id").default(0)
    val familiaId = integer("familia_id").default(0)
    val subfamiliaId = integer("subfamilia_id").default(0)
    val marcaId = integer("marca_id").default(0)
    val lineaId = integer("linea_id").default(0)
    val idSegmentoGob = integer("id_segmento_gob").nullable()
    val idFamiliaGob = integer("id_familia_gob").nullable()

    val montoExento = bool("monto_exento")
    val iva = decimal("iva", SCHEMA_IVA_PRECISION, 2).default(0.0.toBigDecimal())

    val costoActual = decimal("costo_actual", SCHEMA_COSTO_ACTUAL_PRECISION, 2).default(0.0.toBigDecimal())
    val costoPromedio = decimal("costo_promedio", SCHEMA_COSTO_PROMEDIO_PRECISION, 2).default(0.0.toBigDecimal())
    val costoAnterior = decimal("costo_anterior", SCHEMA_COSTO_ANTERIOR_PRECISION, 2).default(0.0.toBigDecimal())
    val costoCif = decimal("costo_cif", SCHEMA_COSTO_CIF_PRECISION, 2).default(0.0.toBigDecimal())
    val costoFob = decimal("costo_fob", SCHEMA_COSTO_FOB_PRECISION, 2).default(0.0.toBigDecimal())
    val costoProcesamiento =
        decimal(
            "costo_procesamiento",
            SCHEMA_COSTO_PROCESAMIENTO_PRECISION,
            SCHEMA_COSTO_PROCESAMIENTO_SCALE,
        ).default(0.0.toBigDecimal())
    val comisionXItem = bool("comision_x_item")
    val costoFranco = decimal("costo_franco", SCHEMA_COSTO_FRANCO_PRECISION, 2).default(0.0.toBigDecimal())
    val costoOrigen = decimal("costo_origen", SCHEMA_COSTO_ORIGEN_PRECISION, 2).default(0.0.toBigDecimal())

    val precio1 = decimal("precio1", SCHEMA_PRECIO1_PRECISION, 2).default(0.0.toBigDecimal())
    val utilidad1 = decimal("utilidad1", SCHEMA_UTILIDAD1_PRECISION, 2).default(0.0.toBigDecimal())
    val coniva1 = decimal("coniva1", SCHEMA_CONIVA1_PRECISION, 2).default(0.0.toBigDecimal())
    val descuento1 = decimal("descuento1", SCHEMA_DESCUENTO1_PRECISION, 2).default(0.0.toBigDecimal())

    val precio2 = decimal("precio2", SCHEMA_PRECIO2_PRECISION, 2).default(0.0.toBigDecimal())
    val utilidad2 = decimal("utilidad2", SCHEMA_UTILIDAD2_PRECISION, 2).default(0.0.toBigDecimal())
    val coniva2 = decimal("coniva2", SCHEMA_CONIVA2_PRECISION, 2).default(0.0.toBigDecimal())
    val descuento2 = decimal("descuento2", SCHEMA_DESCUENTO2_PRECISION, 2).default(0.0.toBigDecimal())

    val precio3 = decimal("precio3", SCHEMA_PRECIO3_PRECISION, 2).default(0.0.toBigDecimal())
    val utilidad3 = decimal("utilidad3", SCHEMA_UTILIDAD3_PRECISION, 2).default(0.0.toBigDecimal())
    val coniva3 = decimal("coniva3", SCHEMA_CONIVA3_PRECISION, 2).default(0.0.toBigDecimal())
    val descuento3 = decimal("descuento3", SCHEMA_DESCUENTO3_PRECISION, 2).default(0.0.toBigDecimal())

    val estatus = varchar("estatus", 1).default("A")
    val codItemForma = integer("cod_item_forma").default(1)
    val tipoProd = integer("tipo_prod").default(2)
    val usuarioCreacion = varchar("usuario_creacion", SCHEMA_USUARIO_CREACION_MAX_LENGTH).default("API")

    val cuentaContable1 = varchar("cuenta_contable1", SCHEMA_CUENTA_CONTABLE1_MAX_LENGTH).default("")
    val cuentaContable2 = varchar("cuenta_contable2", SCHEMA_CUENTA_CONTABLE2_MAX_LENGTH).default("")
    val codigoCuenta = varchar("codigo_cuenta", SCHEMA_CODIGO_CUENTA_MAX_LENGTH).default("")
    val serial1 = varchar("serial1", SCHEMA_SERIAL1_MAX_LENGTH).default("")
    val origen = varchar("origen", SCHEMA_ORIGEN_MAX_LENGTH).default("")
    val temporada = varchar("temporada", SCHEMA_TEMPORADA_MAX_LENGTH).default("")
    val mateCompoClase = varchar("mate_compo_clase", SCHEMA_MATE_COMPO_CLASE_MAX_LENGTH).default("")
    val tejido = varchar("tejido", SCHEMA_TEJIDO_MAX_LENGTH).default("")
    val regSanit = varchar("reg_sanit", SCHEMA_REG_SANIT_MAX_LENGTH).default("")
    val codBarraBulto = varchar("cod_barra_bulto", SCHEMA_COD_BARRA_BULTO_MAX_LENGTH).default("")
    val observacion = text("observacion").default("")
    val foto1 = varchar("foto1", SCHEMA_FOTO1_MAX_LENGTH).default("")
    val foto2 = varchar("foto2", SCHEMA_FOTO2_MAX_LENGTH).default("")
    val foto3 = varchar("foto3", SCHEMA_FOTO3_MAX_LENGTH).default("")
    val foto4 = varchar("foto4", SCHEMA_FOTO4_MAX_LENGTH).default("")
    val contLicenNro = varchar("cont_licen_nro", SCHEMA_CONT_LICEN_NRO_MAX_LENGTH).default("")
    val aprobArte = varchar("aprob_arte", SCHEMA_APROB_ARTE_MAX_LENGTH).default("")
    val propiedad = varchar("propiedad", SCHEMA_PROPIEDAD_MAX_LENGTH).default("")

    val cantidadBulto = decimal("cantidad_bulto", SCHEMA_CANTIDAD_BULTO_PRECISION, 2).default(1.0.toBigDecimal())
    val kilosBulto = decimal("kilos_bulto", SCHEMA_KILOS_BULTO_PRECISION, 2).default(0.0.toBigDecimal())
    val proveedor = integer("proveedor").default(0)
    val posicionArancel = integer("posicion_arancel").default(0)
    val ubicacion = integer("ubicacion").default(0)
    val codigoBase = integer("codigo_base").default(0)
    val sinuso = integer("sinuso").default(0)
    val cubi1 = varchar("cubi1", SCHEMA_CUBI1_MAX_LENGTH).default("")
    val cubi2 = varchar("cubi2", SCHEMA_CUBI2_MAX_LENGTH).default("")
    val cubi3 = varchar("cubi3", SCHEMA_CUBI3_MAX_LENGTH).default("")
    val cubi4 = decimal("cubi4", SCHEMA_CUBI4_PRECISION, SCHEMA_CUBI4_SCALE).default(0.0.toBigDecimal())
    val cubi5 = decimal("cubi5", SCHEMA_CUBI5_PRECISION, SCHEMA_CUBI5_SCALE).default(0.0.toBigDecimal())
    val unidadMedida = integer("unidad_medida").default(0)
    val innerBulto = decimal("inner_bulto", SCHEMA_INNER_BULTO_PRECISION, 2).default(0.0.toBigDecimal())
    val caracteristicas = varchar("caracteristicas", SCHEMA_CARACTERISTICAS_MAX_LENGTH).default("")
    val ingles = varchar("ingles", SCHEMA_INGLES_MAX_LENGTH).default("")
    val pesoNeto = decimal("peso_neto", SCHEMA_PESO_NETO_PRECISION, 2).default(0.0.toBigDecimal())
    val procesamiento = integer("procesamiento").default(0)
    val sucursal = integer("sucursal").default(0)
    val turno = integer("turno").default(0)
    val horas = integer("horas").default(0)
    val rolSemanal = integer("rol_semanal").default(0)
    val codigoCrm = varchar("codigo_crm", SCHEMA_CODIGO_CRM_MAX_LENGTH).default("")
    val unidadOEmpaque = varchar("unidad_o_empaque", SCHEMA_UNIDAD_O_EMPAQUE_MAX_LENGTH).default("UNIDAD")

    override val primaryKey = PrimaryKey(idItem)
}
