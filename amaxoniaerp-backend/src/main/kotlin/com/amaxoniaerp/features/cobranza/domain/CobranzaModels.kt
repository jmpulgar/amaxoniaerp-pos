package com.amaxoniaerp.features.cobranza.domain

import com.amaxoniaerp.core.error.ApiException
import com.amaxoniaerp.core.error.ErrorCategory
import kotlinx.serialization.Serializable

/**
 * Factura con saldo pendiente (CxC). La CxC del ERP es la fila `caja_nueva` con
 * `comprobante='FACT'` y `status='Pendiente'`; el saldo es su monto menos los abonos
 * (`caja_nueva_detalle` no crédito) ya aplicados.
 */
@Serializable
data class CobranzaPendingInvoice(
    val idFactura: String,
    val codFactura: String,
    val numeroFiscal: String,
    val fecha: String,
    val fechaVencimiento: String,
    val montoOriginal: Double,
    val saldo: Double,
)

@Serializable
data class CobranzaPendientesResponse(
    val success: Boolean,
    val totalPendiente: Double,
    val data: List<CobranzaPendingInvoice>,
)

@Serializable
data class CobranzaFacturaInput(
    val idFactura: String,
    val monto: Double,
)

/**
 * Forma de pago del cobro. [monto] es lo que se aplica a la deuda; en efectivo
 * [montoRecibido] puede ser mayor y [efectivoCambio] es el vuelto entregado.
 */
@Serializable
data class CobranzaPagoInput(
    val idFormaPago: Int,
    val monto: Double,
    val montoRecibido: Double = monto,
    val efectivoCambio: Double = 0.0,
    val referencia: String = "",
)

/**
 * Cobro de una o varias facturas pendientes de un cliente con una o varias formas de pago.
 * [idCobranza] es la clave de idempotencia generada por el POS (se usa como
 * `caja_nueva_recibo.caja_recibo_id`): reenviar el mismo cobro no lo duplica.
 */
@Serializable
data class CreateCobranzaRequest(
    val idCobranza: String,
    val idCliente: String,
    val idCaja: String,
    val idCajaSecuencia: String = "",
    val idSucursal: Int,
    val serieSucursal: String = "",
    val codVendedor: Int = 0,
    val observacion: String = "",
    val facturas: List<CobranzaFacturaInput>,
    val pagos: List<CobranzaPagoInput>,
)

@Serializable
data class CobranzaFacturaResult(
    val idFactura: String,
    val codFactura: String,
    val montoAplicado: Double,
    val saldoAnterior: Double,
    val saldoNuevo: Double,
    val pagada: Boolean,
)

@Serializable
data class CreateCobranzaResponse(
    val success: Boolean,
    val idCobranza: String,
    val nroRecibo: String,
    val montoTotal: Double,
    val facturas: List<CobranzaFacturaResult> = emptyList(),
    val duplicado: Boolean = false,
)

class CobranzaValidationException(
    message: String,
) : ApiException(ErrorCategory.Validation, message)

class CobranzaStateException(
    message: String,
) : ApiException(ErrorCategory.DomainRule, message)
