package com.amaxoniaerp.features.electronicinvoice.domain

import java.math.BigDecimal

// ─── Contexto de datos para construir el payload de The Factory HKA Venezuela ─
// Desacopla el ORM del Builder. NO reutiliza los DTOs Panamá ([InvoiceFEContext],
// [FEClienteData], etc.) porque la estructura y los campos requeridos difieren:
// VE no tiene DV, tipoContribuyente, retenciones DGI ni CPBS; sí tiene IGTF,
// multimoneda real, serie+sucursal, y un identificador de transacción.

/**
 * Contexto completo de una factura venezolana listo para construir el payload.
 */
data class InvoiceVEContext(
    val config: VEConfigData,
    val factura: VEFacturaData,
    val comprador: VECompradorData,
    val detalles: List<VEDetalleData>,
    val formasPago: List<VEFormaPagoData>,
    val caja: VECajaData,
    val correlativoReservado: VECorrelativoReservado,
)

/**
 * Configuración FE Venezuela extraída de `parametros_generales`.
 *
 * `tokenEmpresa` y `tokenPassword` son SECRETO: no se loguean ni se devuelven
 * al POS.
 */
data class VEConfigData(
    /** tipo_facturacion == 5 → activa emisión HKA en VE. */
    val tipoFacturacion: Int,
    /** 0 = demostración, 1 = producción. */
    val tipoEntornoVe: Int,
    val tokenEmpresa: String,
    val tokenPassword: String,
    val baseUrl: String,
    val rif: String,
    val nombreEmpresa: String?,
    val direccion: String?,
    val telefonos: String?,
    /** Porcentaje IGTF tomado literal de parametros_generales (ej. 3.000000). */
    val igtf: BigDecimal,
    val procesoGeneracion: String,
    val tipoEmision: String,
    val codigoSucursalEmisorFallback: String,
    val puntoFacturacionFiscalFallback: String,
)

/**
 * Datos de cabecera de factura. Todos los montos en BigDecimal (VE trabaja en
 * VES, escala 2; el Builder aplicará RoundingMode en cada paso).
 */
data class VEFacturaData(
    val idFactura: String,
    val codFactura: String,
    val numeroDocumentoFiscal: String?,
    val numeroControlThka: String?,
    val tipoDocumento: String,
    val fechaFactura: String?,
    val fechaCreacion: String?,
    val facturarANombre: String,
    val facturarARuc: String,
    val facturarADireccion: String,
    val facturarATelefono: String,
    val totalTotalFactura: BigDecimal,
    val ivaTotalFactura: BigDecimal,
    val descuentosItemFactura: BigDecimal,
    val totalizarBaseImponible: BigDecimal,
    val totalizarMontoIva: BigDecimal,
    val totalizarTotalGeneral: BigDecimal,
    val montoItemsFactura: BigDecimal,
    val multiMoneda: String,
    val tasa: BigDecimal,
    val monedaBase: Int,
    val abrMonedaBase: String,
    val monedaSecundaria: Int,
    val abrMonedaSecundaria: String,
)

/** Comprador: se usa facturar_a* de la factura (el cliente comercial). */
data class VECompradorData(
    val nombreRazonSocial: String,
    val rif: String,
    val direccion: String?,
    val telefono: String?,
    val email: String?,
)

/** Línea de detalle. */
data class VEDetalleData(
    val descripcion: String,
    val codigo: String,
    val referencia: String?,
    val unidadEmpaque: String?,
    val cantidad: BigDecimal,
    val precioSinIva: BigDecimal,
    val descuento: BigDecimal,
    val montoDescuento: BigDecimal,
    val piva: BigDecimal,
    val totalSinIva: BigDecimal,
    val totalConIva: BigDecimal,
    val importeIsc: BigDecimal?,
    val porcentajeIsc: BigDecimal?,
    val importeOti: BigDecimal?,
    val importeAcarreo: BigDecimal?,
    val importeSeguro: BigDecimal?,
)

/**
 * Forma de pago con su metadata de moneda (sirve para IGTF).
 *
 * @param montoEnMonedaOrigen monto tomado literalmente de caja_nueva_detalle.
 * @param esDivisa true si esta forma de pago se efectuó en la moneda secundaria
 *   (divisa extranjera). Se usa para calcular la base imponible del IGTF.
 * @param montoRecibido efectivo recibido (cuando aplica).
 */
data class VEFormaPagoData(
    val idFormaPago: Int,
    val descripcion: String,
    val siglas: String?,
    val formaPagoFact: String?,
    val monto: BigDecimal,
    val esDivisa: Boolean,
    val montoRecibido: BigDecimal?,
    val tipoMoneda: String?,
)

/** Datos de la caja: serie fiscal + sucursal + correlativos PAC si los tuviera. */
data class VECajaData(
    val idCaja: String,
    val serieCaja: String,
    val serieSucursal: String?,
    val codigoSucursalEmisor: String,
    val puntoFacturacionFiscal: String,
)

/**
 * Número reservado atómicamente antes de llamar a HKA.
 *
 * @param numero número reservado (sin rellenar, ya numérico).
 * @param formato longitud esperada del número fiscal (para zero-padding).
 * @param siguiente siguiente contador ya incrementado en DB.
 */
data class VECorrelativoReservado(
    val numero: Int,
    val formato: Int,
) {
    /** Número fiscal final con ceros iniciales y longitud esperada. */
    fun numeroFormateado(): String = numero.toString().padStart(formato.coerceAtLeast(1), '0')
}
