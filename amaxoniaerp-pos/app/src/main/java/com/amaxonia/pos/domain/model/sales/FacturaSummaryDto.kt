package com.amaxonia.pos.domain.model.sales

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Mirrors the backend FacturaSummary returned by GET /facturas.
 */
@Serializable
data class FacturaSummaryDto(
    val id: String,
    val codigo: String,
    val codigoFiscal: String = "",
    val numeroDocumentoFiscal: String = "",
    val fecha: String,
    val fechaCreacion: String = "",
    val fechaDgi: String = "",
    val clienteNombre: String = "",
    val clienteIdentificacion: String = "",
    val total: Double,
    val estatus: String,
    val formaPago: String = "",
    val moneda: String = "USD",
    val items: Int = 0,
    val totalRef: Double? = null,
    val tasa: Float? = null,
    val abrMonedaSecundaria: String? = null,
)

@Serializable
data class FacturasListResponseDto(
    val data: List<FacturaSummaryDto>,
    val total: Long,
)

/**
 * Mirrors the backend FacturaDetalleItem returned by GET /facturas/{id}/detalle.
 */
@Serializable
data class FacturaDetalleItemDto(
    val id: String,
    val descripcion: String,
    val cantidad: Double,
    val precioUnitario: Double,
    val totalConIva: Double,
    val codigo: String = "",
    val referencia: String = "",
)

@Serializable
data class FacturaDetalleResponseDto(
    val idFactura: String,
    val codFactura: String,
    val items: List<FacturaDetalleItemDto>,
)

/**
 * Canonical reconciliation result returned by [SalesRepository.findByCorrelationId].
 *
 * Auditoría ítem 2 (INT-BE-001): after an HTTP 409, the POS must converge on
 * the existing invoice instead of leaving the cashier in a dead-end. The
 * caller may use this to mark the local ledger row as CONFIRMED with the
 * backend's own `idFactura` + `codFactura` and continue printing or fiscal
 * confirmation — without a second sale being submitted.
 */
@Serializable
data class ReconciledInvoice(
    val idFactura: String,
    val codFactura: String,
    val codEstatus: Int = 2,
    @SerialName("sesion_mesa_cerrada")
    val sesionMesaCerrada: Boolean = false,
)
