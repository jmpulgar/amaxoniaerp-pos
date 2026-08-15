package com.amaxoniaerp.features.electronicinvoice.pac.thefactory

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

// ─── DTOs del Payload de envío para The Factory HKA ──────────────────────────
// Representan la estructura EXACTA del JSON que espera `POST /api/Enviar`.
// Campos opcionales son nullable con default null → kotlinx-serialization
// los omite cuando se configura con `encodeDefaults = false`.

val feJson =
    Json {
        encodeDefaults = false
        explicitNulls = false
        ignoreUnknownKeys = true
        prettyPrint = false
    }

/**
 * Wrapper raíz del documento electrónico.
 */
@Serializable
data class TheFactoryHkaDocumentoWrapper(
    val documento: TheFactoryHkaDocumento,
)

@Serializable
data class TheFactoryHkaDocumento(
    val codigoSucursalEmisor: String,
    val tipoSucursal: String? = null,
    val datosTransaccion: TheFactoryHkaDatosTransaccion,
    val listaItems: List<TheFactoryHkaItem>,
    val totalesSubTotales: TheFactoryHkaTotalesSubTotales,
    val serialDispositivo: String? = null,
    val pedidoComercialGlobal: TheFactoryHkaPedidoComercial? = null,
    val infoLogistica: TheFactoryHkaInfoLogistica? = null,
    val infoEntrega: TheFactoryHkaInfoEntrega? = null,
    val usoPosterior: TheFactoryHkaUsoPosterior? = null,
    val listaExtras: List<TheFactoryHkaExtra>? = null,
)

@Serializable
data class TheFactoryHkaDatosTransaccion(
    val tipoEmision: String,
    val tipoDocumento: String,
    val numeroDocumentoFiscal: String,
    val puntoFacturacionFiscal: String,
    val fechaEmision: String,
    val naturalezaOperacion: String,
    val tipoOperacion: String,
    val destinoOperacion: String,
    val formatoCAFE: String,
    val entregaCAFE: String,
    val envioContenedor: String,
    val procesoGeneracion: String,
    val tipoVenta: String,
    val informacionInteres: String? = null,
    val fechaInicioContingencia: String? = null,
    val motivoContingencia: String? = null,
    val fechaSalida: String? = null,
    val cliente: TheFactoryHkaCliente,
    val datosFacturaExportacion: TheFactoryHkaDatosExportacion? = null,
    val listaDocsFiscalReferenciados: List<TheFactoryHkaDocFiscalRef>? = null,
    val listaAutorizadosDescargaFEyEventos: List<TheFactoryHkaAutorizadoDescarga>? = null,
)

@Serializable
data class TheFactoryHkaCliente(
    val tipoClienteFE: String,
    val tipoContribuyente: String? = null,
    val numeroRUC: String? = null,
    val digitoVerificadorRUC: String? = null,
    val razonSocial: String,
    val direccion: String? = null,
    val codigoUbicacion: String? = null,
    val provincia: String? = null,
    val distrito: String? = null,
    val corregimiento: String? = null,
    val tipoIdentificacion: String? = null,
    val nroIdentificacionExtranjero: String? = null,
    val paisExtranjero: String? = null,
    val telefono1: String,
    val telefono2: String? = null,
    val telefono3: String? = null,
    val correoElectronico1: String? = null,
    val correoElectronico2: String? = null,
    val correoElectronico3: String? = null,
    val pais: String? = null,
    val paisOtro: String? = null,
)

@Serializable
data class TheFactoryHkaItem(
    val descripcion: String,
    val codigo: String,
    val unidadMedida: String = "und",
    val cantidad: String,
    val precioUnitario: String,
    val precioUnitarioDescuento: String? = null,
    val precioItem: String? = null,
    val precioAcarreo: String? = null,
    val precioSeguro: String? = null,
    val valorTotal: String,
    val codigoGTIN: String? = null,
    val cantGTINCom: String? = null,
    val codigoGTINInv: String? = null,
    val cantGTINComInv: String? = null,
    val tasaITBMS: String,
    val valorITBMS: String,
    val tasaISC: String? = null,
    val valorISC: String? = null,
    val fechaFabricacion: String? = null,
    val fechaCaducidad: String? = null,
    val codigoCPBSAbrev: String? = null,
    val codigoCPBS: String? = null,
    val unidadMedidaCPBS: String? = null,
    val infoItem: String? = null,
    val listaItemOTI: List<TheFactoryHkaItemOTI>? = null,
    val vehiculo: TheFactoryHkaVehiculo? = null,
    val medicina: TheFactoryHkaMedicina? = null,
    val pedidoComercialItem: TheFactoryHkaPedidoComercialItem? = null,
)

@Serializable
data class TheFactoryHkaItemOTI(
    val tasaOTI: String,
    val valorTasa: String,
)

@Serializable
data class TheFactoryHkaTotalesSubTotales(
    val totalPrecioNeto: String,
    val totalITBMS: String,
    val totalISC: String? = null,
    val totalMontoGravado: String? = null,
    val totalDescuento: String? = null,
    val totalAcarreoCobrado: String? = null,
    val valorSeguroCobrado: String? = null,
    val totalFactura: String,
    val totalValorRecibido: String,
    val vuelto: String? = null,
    val tiempoPago: String,
    val nroItems: String,
    val totalTodosItems: String,
    val listaDescBonificacion: List<TheFactoryHkaDescBonificacion>? = null,
    val listaFormaPago: List<TheFactoryHkaFormaPago>,
    val retencion: TheFactoryHkaRetencion? = null,
    val listaPagoPlazo: List<TheFactoryHkaPagoPlazo>? = null,
    val listaTotalOTI: List<TheFactoryHkaTotalOTI>? = null,
)

@Serializable
data class TheFactoryHkaFormaPago(
    val formaPagoFact: String,
    val descFormaPago: String? = null,
    val valorCuotaPagada: String,
)

@Serializable
data class TheFactoryHkaDescBonificacion(
    val descDescuento: String,
    val montoDescuento: String,
)

@Serializable
data class TheFactoryHkaRetencion(
    val codigoRetencion: String,
    val montoRetencion: String,
)

@Serializable
data class TheFactoryHkaTotalOTI(
    val codigoTotalOTI: String,
    val valorTotalOTI: String,
)

@Serializable
data class TheFactoryHkaPagoPlazo(
    val fechaVenceCuota: String,
    val valorCuota: String,
    val infoPagoCuota: String? = null,
)

// ─── DTOs opcionales (no siempre presentes) ──────────────────────────────────

@Serializable
data class TheFactoryHkaDatosExportacion(
    val condicionesEntrega: String? = null,
    val monedaOperExportacion: String? = null,
    val monedaOperExportacionNonDef: String? = null,
    val tipoDeCambio: String? = null,
    val montoMonedaExtranjera: String? = null,
    val puertoEmbarque: String? = null,
)

@Serializable
data class TheFactoryHkaDocFiscalRef(
    val fechaEmisionDocFiscalReferenciado: String? = null,
    val cufeFEReferenciada: String? = null,
    val nroFacturaPapel: String? = null,
    val nroFacturaImpFiscal: String? = null,
)

@Serializable
data class TheFactoryHkaAutorizadoDescarga(
    val tipoContribuyente: String? = null,
    val rucReceptor: String? = null,
    val digitoVerifRucReceptor: String? = null,
)

@Serializable
data class TheFactoryHkaVehiculo(
    val modalidadOperacionVenta: String? = null,
    val modalidadOperacionVentaNoDef: String? = null,
    val chasis: String? = null,
    val codigoColor: String? = null,
    val colorNombre: String? = null,
    val potenciaMotor: String? = null,
    val capacidadMotor: String? = null,
    val pesoNeto: String? = null,
    val pesoBruto: String? = null,
    val tipoCombustible: String? = null,
    val tipoCombustibleNoDef: String? = null,
    val numeroMotor: String? = null,
    val capacidadTraccion: String? = null,
    val distanciaEjes: String? = null,
    val anoModelo: String? = null,
    val anoFabricacion: String? = null,
    val tipoPintura: String? = null,
    val tipoPinturaNodef: String? = null,
    val tipoVehiculo: String? = null,
    val usoVehiculo: String? = null,
    val condicionVehiculo: String? = null,
    val capacidadPasajeros: String? = null,
)

@Serializable
data class TheFactoryHkaMedicina(
    val nroLote: String? = null,
    val cantProductosLote: String? = null,
)

@Serializable
data class TheFactoryHkaPedidoComercialItem(
    val nroPedidoCompraItem: String? = null,
    val nroItem: String? = null,
    val infoItem: String? = null,
)

@Serializable
data class TheFactoryHkaPedidoComercial(
    val nroPedidoCompraGlobal: String? = null,
    val listaNroAceptacion: List<String>? = null,
    val codigoReceptor: String? = null,
    val codigoSistemaEmisor: String? = null,
    val infoPedido: String? = null,
)

@Serializable
data class TheFactoryHkaInfoLogistica(
    val nroVolumenes: String? = null,
    val pesoCarga: String? = null,
    val unidadPesoTotal: String? = null,
    val licVehiculoCarga: String? = null,
    val razonSocialTransportista: String? = null,
    val tipoRucTransportista: String? = null,
    val rucTransportista: String? = null,
    val digitoVerifRucTransportista: String? = null,
    val infoLogisticaEmisor: String? = null,
)

@Serializable
data class TheFactoryHkaInfoEntrega(
    val tipoRucEntrega: String? = null,
    val numeroRucEntrega: String? = null,
    val digitoVerifRucEntrega: String? = null,
    val razonSocialEntrega: String? = null,
    val direccionEntrega: String? = null,
    val codigoUbicacionEntrega: String? = null,
    val provinciaEntrega: String? = null,
    val distritoEntrega: String? = null,
    val corregimientoEntrega: String? = null,
    val telefonoEntrega: String? = null,
    val telefonoEntregaAlt: String? = null,
)

@Serializable
data class TheFactoryHkaUsoPosterior(
    val cufe: String? = null,
)

@Serializable
data class TheFactoryHkaExtra(
    val attribute: String? = null,
    val enabled: String? = null,
    val id: String? = null,
    val orden: String? = null,
    val value: String? = null,
)
