package com.amaxonia.pos.domain.model.cobranza

import kotlinx.serialization.Serializable

/** Factura con saldo pendiente (CxC) de un cliente. */
@Serializable
data class CobranzaPendingInvoiceDto(
    val idFactura: String,
    val codFactura: String = "",
    val numeroFiscal: String = "",
    val fecha: String = "",
    val fechaVencimiento: String = "",
    val montoOriginal: Double = 0.0,
    val saldo: Double = 0.0,
)

@Serializable
data class CobranzaPendientesResponseDto(
    val success: Boolean = false,
    val totalPendiente: Double = 0.0,
    val data: List<CobranzaPendingInvoiceDto> = emptyList(),
)

@Serializable
data class CobranzaFacturaInputDto(
    val idFactura: String,
    val monto: Double,
)

@Serializable
data class CobranzaPagoInputDto(
    val idFormaPago: Int,
    val monto: Double,
    val montoRecibido: Double = monto,
    val efectivoCambio: Double = 0.0,
    val referencia: String = "",
)

/** `POST api/pos/cobranzas`; [idCobranza] es la clave de idempotencia del dispositivo. */
@Serializable
data class CreateCobranzaRequestDto(
    val idCobranza: String,
    val idCliente: String,
    val idCaja: String,
    val idCajaSecuencia: String = "",
    val idSucursal: Int,
    val serieSucursal: String = "",
    val codVendedor: Int = 0,
    val observacion: String = "",
    val facturas: List<CobranzaFacturaInputDto>,
    val pagos: List<CobranzaPagoInputDto>,
)

@Serializable
data class CobranzaFacturaResultDto(
    val idFactura: String = "",
    val codFactura: String = "",
    val montoAplicado: Double = 0.0,
    val saldoAnterior: Double = 0.0,
    val saldoNuevo: Double = 0.0,
    val pagada: Boolean = false,
)

@Serializable
data class CreateCobranzaResponseDto(
    val success: Boolean = false,
    val idCobranza: String = "",
    val nroRecibo: String = "",
    val montoTotal: Double = 0.0,
    val facturas: List<CobranzaFacturaResultDto> = emptyList(),
    val duplicado: Boolean = false,
)
