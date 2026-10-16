package com.amaxonia.erp.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ClientDto(
    val id: String? = null,
    val code: String? = null,
    val identification: String? = null,
    val dv: String? = null,
    val name: String? = null,
    val lastName: String? = null,
    val address: String? = null,
    val phone: String? = null,
    val email: String? = null,
    val status: Boolean? = null,
    val taxpayerTypeId: Int? = null,
    val foreignAuthTypeId: String? = null,
    val countryId: Int? = null,
    val clientTypeId: Int? = null,
    val codTipoPrecio: Int? = null,
    val photoFilename: String? = null,
    val permiteCredito: Boolean = false,
    val diasCredito: Int = 0,
    @SerialName("aplica_itbms")
    @Serializable(with = LenientStringSerializer::class)
    val aplicaItbms: String? = null,
    val saldo: Double = 0.0,
    val nombreComercial: String? = null,
    val representante: String? = null,
    val telefono2: String? = null,
    val web: String? = null,
    val fax: String? = null,
    val codigoCrm: String? = null,
    val actividad: String? = null,
    val empresaZonaLibre: String? = null,
    val pedidosOnline: String? = null,
    val fnacimiento: String? = null,
    val genero: String? = null,
    val limiteCredito: Double = 0.0,
    val tolerancia: Int = 0,
    val porcParcial: Double = 0.0,
    val porcDescuentoGlobal: Double = 0.0,
    val cuentaContable: String? = null,
    val codVendedor: String? = null,
    val codZona: String? = null,
    val lugarEntrega: String? = null,
    val contribuyenteEspecial: String? = null,
    val retenidoPorCliente: String? = null,
    val codEntidad: String? = null,
    val clienteFidelizacion: Int = 0,
    val clienteFidelizacionSucursal: Int = 0,
    val clienteFidelizacionNivel: Int = 0,
    val clienteFidelizacionPuntos: Double = 0.0,
    val clienteFidelizacionFecha: String? = null,
    val addressLevel1: String? = null,
    val addressLevel2: String? = null,
    val addressLevel3: String? = null,
    val direccionCiudad: String? = null,
    val localidad: String? = null,
    val direccionSector: String? = null,
    val direccionUrbanizacion: String? = null,
    val direccionCalleAv: String? = null,
    val direccionEdificioCasa: String? = null,
    val direccionPiso: String? = null,
    val direccionOficina: String? = null,
    val direccionReferencia: String? = null,
    val alterna: String? = null,
    val alterna2: String? = null,
) {
    val resolvedAplicaItbms: Boolean
        get() = aplicaItbms == null || aplicaItbms == "1" || aplicaItbms.equals("true", ignoreCase = true) || aplicaItbms.equals("si", ignoreCase = true)
}

@Serializable
data class CreateClientRequest(
    val identification: String,
    val name: String,
    val lastName: String = "",
    val address: String = "",
    val phone: String = "",
    val email: String = "",
    val clientTypeId: Int = 1,
    val taxpayerTypeId: Int = 1,
    val foreignAuthTypeId: String? = null,
    val countryId: Int = 170,
    val dv: String = "",
    val nombreComercial: String = "",
    val representante: String = "",
    val phone2: String = "",
    val web: String = "",
    val fax: String = "",
    val codigoCrm: String = "",
    val actividad: String = "",
    val empresaZonaLibre: String = "0",
    val pedidosOnline: String = "0",
    val fnacimiento: String = "",
    val genero: String = "",
    val permiteCredito: Boolean = false,
    val limiteCredito: Double = 0.0,
    val diasCredito: Int = 0,
    val tolerancia: Int = 0,
    val porcParcial: Double = 0.0,
    val porcDescuentoGlobal: Double = 0.0,
    val codTipoPrecio: Int = 2,
    val cuentaContable: String = "",
    val codVendedor: String = "",
    val codZona: String = "",
    val lugarEntrega: String = "",
    val contribuyenteEspecial: String = "0",
    val retenidoPorCliente: String = "",
    val codEntidad: String = "",
    val aplicaItbms: String = "1",
    val clienteFidelizacion: Int = 0,
    val clienteFidelizacionSucursal: Int = 0,
    val clienteFidelizacionNivel: Int = 0,
    val clienteFidelizacionFecha: String = "",
    val addressLevel1: String = "",
    val addressLevel2: String = "",
    val addressLevel3: String = "",
    val direccionCiudad: String = "",
    val localidad: String = "",
    val direccionSector: String = "",
    val direccionUrbanizacion: String = "",
    val direccionCalleAv: String = "",
    val direccionEdificioCasa: String = "",
    val direccionPiso: String = "",
    val direccionOficina: String = "",
    val direccionReferencia: String = "",
    val alterna: String = "",
    val alterna2: String = "",
    val status: Boolean = true,
)

@Serializable
data class ClientSucursalDto(
    val sucursalId: Int,
    val clienteCodigo: String,
    val nombreSucursal: String,
    val nombreContacto: String? = null,
    val telefonoContacto: String? = null,
    val correoContacto: String? = null,
    val direccion: String? = null,
    val observaciones: String? = null,
)

@Serializable
data class AccountStatementItemDto(
    val id: String = "",
    val fecha: String = "",
    val fechaEmision: String = "",
    @SerialName("ffecha")
    val ffecha: String? = null,
    val fechaVencimiento: String = "",
    @SerialName("ffecha_vencimiento")
    val ffechaVencimiento: String? = null,
    val comprobante: String = "",
    val comprobanteNumero: String = "",
    @SerialName("comprobante_numero")
    val compNumSnake: String? = null,
    val serieNumero: String = "",
    @SerialName("serie_numero")
    val serieNumSnake: String? = null,
    val comprobanteFiscal: String = "",
    @SerialName("comprobante_fiscal")
    val compFiscalSnake: String? = null,
    val montoOriginal: Double = 0.0,
    @SerialName("monto_original")
    val montoOrigSnake: Double? = null,
    val montoPendiente: Double = 0.0,
    @SerialName("monto_pendiente")
    val montoPendSnake: Double? = null,
    val debito: Double = 0.0,
    @SerialName("DEUDA")
    val deudaSnake: Double? = null,
    val credito: Double = 0.0,
    @SerialName("ABONO")
    val abonoSnake: Double? = null,
    val saldo: Double = 0.0,
    @SerialName("SALDO")
    val saldoSnake: Double? = null,
    val status: String = "",
    val concepto: String = "",
    val observacionFactura: String = "",
    @SerialName("observacion_factura")
    val obsFactSnake: String? = null,
    val cliente: String = "",
)

@Serializable
data class AccountStatementDto(
    val clientId: String = "",
    val clientCode: String = "",
    val clientName: String = "",
    val identification: String = "",
    val totalDebt: Double = 0.0,
    val totalCredit: Double = 0.0,
    val currentBalance: Double = 0.0,
    val totalSaldoPendiente: Double? = null,
    val totalAnticipos: Double? = null,
    val totalNC: Double? = null,
    val items: List<AccountStatementItemDto> = emptyList(),
)
