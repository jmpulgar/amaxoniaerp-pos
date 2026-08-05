package com.amaxoniaerp.features.electronicinvoice.pac.thefactory.venezuela

import kotlinx.serialization.Serializable

// ─── DTOs del Payload de envío para The Factory HKA Venezuela ────────────────
// Representan la estructura EXACTA del JSON que espera el endpoint de Emision VE.
//
// Política de serialización:
// - encodeDefaults = false (configurado en [feJsonVE]) para omitir null/campos
//   vacíos. No se fuerza ningun campo opcional.
// - Los String numéricos SIEMPRE van como String: The Factory valida tipos y el
//   redondeo a escala fija lo hace explícitamente el Builder con BigDecimal.
// - Los porcentajes se envían como string con formato "16.00", "8.00", "0.00".
//
// Los nombres de los campos del JSON se mantienen EXACTOS a los del Swagger VE:
// no se traducen, no se case-convierten, no se renombran. La coincidencia es
// un contrato con el PAC.

val feJsonVE = kotlinx.serialization.json.Json {
    encodeDefaults = false
    explicitNulls = false
    ignoreUnknownKeys = true
    prettyPrint = false
}

/** Wrapper raíz del documento electrónico venezolano. */
@Serializable
data class VenezuelaHkaDocumentoWrapper(
    val documento: VenezuelaHkaDocumento,
)

@Serializable
data class VenezuelaHkaDocumento(
    val codigoSucursalEmisor: String,
    val datosTransaccion: VenezuelaHkaDatosTransaccion,
    val listaItems: List<VenezuelaHkaItem>,
    val totalesSubTotales: VenezuelaHkaTotalesSubTotales,
    val listaExtras: List<VenezuelaHkaExtra>? = null,
)

@Serializable
data class VenezuelaHkaDatosTransaccion(
    val tipoEmision: String,
    val tipoDocumento: String,
    val numeroDocumentoFiscal: String,
    val puntoFacturacionFiscal: String,
    val fechaEmision: String,
    val procesoGeneracion: String,
    val transaccionId: String,
    val cliente: VenezuelaHkaCliente,
    val serie: String? = null,
    val sucursal: String? = null,
)

@Serializable
data class VenezuelaHkaCliente(
    val nombreRazonSocial: String,
    val numeroRif: String,
    val direccion: String? = null,
    val telefono: String? = null,
    val correoElectronico: String? = null,
)

@Serializable
data class VenezuelaHkaItem(
    val descripcion: String,
    val codigo: String,
    val referencia: String? = null,
    val unidadMedida: String,
    val cantidad: String,
    val precioUnitario: String,
    val precioUnitarioDescuento: String? = null,
    val montoDescuento: String? = null,
    val precioItem: String,
    val valorTotal: String,
    val alicuotaIva: String,
    val valorIva: String,
    val valorAcarreo: String? = null,
    val valorSeguro: String? = null,
    val valorIsc: String? = null,
    val porcentajeIsc: String? = null,
)

@Serializable
data class VenezuelaHkaTotalesSubTotales(
    val totalPrecioNeto: String,
    val totalIva: String,
    val totalDescuento: String,
    val totalAlicuotaGeneral: String,
    val totalAlicuotaReducido: String,
    val totalAlicuotaExento: String,
    val totalIsc: String? = null,
    val totalAcarreo: String? = null,
    val totalSeguro: String? = null,
    val totalMontoGravado: String,
    val montoTotalFactura: String,
    val montoTotalMonedaSecundaria: String? = null,
    val igtf: VenezuelaHkaIgtf? = null,
    val listaFormaPago: List<VenezuelaHkaFormaPago>,
    val totalValorRecibido: String,
    val montoAnticipo: String? = null,
    val vuelto: String? = null,
    val tiempoPago: String,
    val nroItems: String,
    val totalTodosItems: String,
    val tasaCambio: String? = null,
    val transaccionId: String? = null,
    val montoEnLetras: String,
)

/** Composición de IGTF (solo se serializa si hubo base imponible en divisa). */
@Serializable
data class VenezuelaHkaIgtf(
    val baseImponible: String,
    val porcentaje: String,
    val montoIgtf: String,
)

@Serializable
data class VenezuelaHkaFormaPago(
    val formaPagoFact: String,
    val montoPagado: String,
    val descripcion: String? = null,
    val montoMonedaSecundaria: String? = null,
    val montoRecibido: String? = null,
    val cambio: String? = null,
    val referencia: String? = null,
    val banco: String? = null,
)

@Serializable
data class VenezuelaHkaExtra(
    val attribute: String,
    val value: String,
)
