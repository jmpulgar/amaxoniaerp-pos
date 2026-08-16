package com.amaxoniaerp.features.companies.data

import com.amaxoniaerp.core.database.SchemaDimensions
import org.jetbrains.exposed.sql.Table

/**
 * Columnas comunes a VE y PA en parametros_generales.
 */
abstract class BaseParametrosGeneralesTable(
    name: String = "parametros_generales",
) : Table(name) {
    val codEmpresa = integer("cod_empresa")
    val defaultCodClienteFactura = varchar("default_cod_cliente_factura", SchemaDimensions.VARCHAR_LENGTH_80)
    val defaultIdFormaPagoFactura = integer("default_id_formapago_factura")
    val porcentajeImpuestoPrincipal = decimal("porcentaje_impuesto_principal", SchemaDimensions.DECIMAL_PRECISION_10, 2)
    val validarStock = varchar("validar_stock", 2)
    val diasVencimiento = integer("dias_vencimiento")
    val codAlmacen = integer("cod_almacen")
    val rif = varchar("rif", SchemaDimensions.VARCHAR_LENGTH_50).nullable()

    // moneda base existe en ambos pero con nombre de columna distinto en cada esquema
    val abrMonedaBase = varchar("moneda", SchemaDimensions.VARCHAR_LENGTH_50)
    val monedaBase = integer("moneda_base").nullable()
}

/** Venezuela: multimoneda + impresora fiscal + IGTF. */
object ParametrosGeneralesTableVE : BaseParametrosGeneralesTable() {
    val multiMoneda = varchar("multi_moneda", 2)
    val monedaSecundaria = integer("moneda_secundaria")
    val abrMonedaSecundaria = varchar("moneda_secundaria_abr", SchemaDimensions.VARCHAR_LENGTH_50)
    val igtf = decimal("igtf", SchemaDimensions.DECIMAL_PRECISION_10, SchemaDimensions.DECIMAL_SCALE_6).nullable()
    val impresionDirecta = varchar("impresion_directa", 2) // "Si" / "No"
}

/** Panamá: sin multimoneda, sin IGTF, impresion_directa es tinyint. */
object ParametrosGeneralesTablePA : BaseParametrosGeneralesTable() {
    val bloquearItbms = varchar("bloquear_itbms", 2).default("NO")
    val facturarCero = bool("facturar_cero").default(false)
    val impresionDirecta = bool("impresion_directa").default(false)
    val tipoFacturacion = integer("tipo_facturacion").default(0)
}

/** Devuelve la tabla correcta según el país. */
object ParametrosGeneralesTableFactory {
    fun forCountry(countryCode: String): BaseParametrosGeneralesTable =
        when (countryCode.uppercase()) {
            "VE" -> ParametrosGeneralesTableVE
            "PA" -> ParametrosGeneralesTablePA
            else -> throw IllegalArgumentException("País no soportado en ParametrosGenerales: $countryCode")
        }
}

// ─── TasasCambio ─────────────────────────────────────────────────────────────

/**
 * Columnas comunes de tasas_cambio.
 * VE: tiene facturado, divisa, tasa_inversa, monedabase.
 * PA: solo id y tasa_inversa (sin `facturado`, sin multimoneda real).
 */
abstract class BaseTasasCambioTable(
    name: String = "tasas_cambio",
) : Table(name) {
    val id = long("id")
    val tasaInversa = decimal("tasa_inversa", SchemaDimensions.DECIMAL_PRECISION_20, SchemaDimensions.DECIMAL_SCALE_8)
    override val primaryKey = PrimaryKey(id)
}

/** Venezuela: campos de multimoneda activos. */
object TasasCambioTableVE : BaseTasasCambioTable() {
    val divisa = integer("divisa").nullable()
    val monedabase = integer("monedabase").nullable()
    val facturado = varchar("facturado", 1)
}

/** Panamá: solo el campo de tasa (sin facturado, sin divisa/monedabase). */
object TasasCambioTablePA : BaseTasasCambioTable()

/** Devuelve la tabla correcta según el país. */
object TasasCambioTableFactory {
    fun forCountry(countryCode: String): BaseTasasCambioTable =
        when (countryCode.uppercase()) {
            "VE" -> TasasCambioTableVE
            "PA" -> TasasCambioTablePA
            else -> TasasCambioTablePA
        }
}
