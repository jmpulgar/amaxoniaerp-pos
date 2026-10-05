package com.amaxoniaerp.features.kiosk.domain

import io.ktor.server.auth.jwt.JWTPrincipal
import kotlinx.serialization.Serializable
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

@Serializable
data class KioskPairingRequest(
    val countryCode: String,
    val companyDb: String,
    val pairingCode: String,
)

@Serializable
data class KioskPairingResponse(
    val deviceId: String,
    val deviceToken: String,
    val deviceName: String,
    val prefix: String,
)

@Serializable
data class KioskUnlockRequest(
    val password: String,
)

@Serializable
data class KioskMediaItem(
    val type: String, // "IMAGE" or "VIDEO"
    val url: String,
    val durationSec: Int,
)

@Serializable
data class KioskCurrencyConfig(
    val base: String,
    val secondary: String?,
    val rate: String,
)

@Serializable
data class KioskConfigResponse(
    val version: Int,
    val brandColor: String?,
    val logoUrl: String?,
    val media: List<KioskMediaItem>,
    val diningModes: List<String>,
    val dispatch: String,
    val defaultCustomerId: String,
    val currency: KioskCurrencyConfig,
    val country: String,
)

@Serializable
data class KioskCategoryDto(
    val id: Int,
    val name: String,
    val iconUrl: String? = null,
    val order: Int = 0,
)

@Serializable
data class KioskModifierOptionDto(
    val id: Int,
    val name: String,
    val extraPrice: String,
    val soldOut: Boolean = false,
)

@Serializable
data class KioskModifierGroupDto(
    val id: Int,
    val name: String,
    val min: Int,
    val max: Int,
    val isMandatory: Boolean,
    val isCombo: Boolean,
    val options: List<KioskModifierOptionDto>,
)

@Serializable
data class KioskItemDto(
    val id: Int,
    val categoryId: Int,
    val name: String,
    val description: String?,
    val price: String,
    val taxRate: String,
    val imageUrl: String?,
    val soldOut: Boolean,
    val modifierGroups: List<KioskModifierGroupDto>,
)

@Serializable
data class KioskCatalogResponse(
    val categories: List<KioskCategoryDto>,
    val items: List<KioskItemDto>,
)

@Serializable
data class KioskQuoteLineRequest(
    val itemId: Int,
    val qty: Int,
    val note: String? = null,
    val modifiers: List<Int> = emptyList(),
)

@Serializable
data class KioskQuoteRequest(
    val diningMode: String,
    val tableTent: String? = null,
    val customerId: String? = null,
    val lines: List<KioskQuoteLineRequest>,
)

@Serializable
data class KioskQuoteLineModifierResponse(
    val id: Int,
    val name: String,
    val extraPrice: String,
)

@Serializable
data class KioskQuoteLineResponse(
    val line: Int,
    val itemId: Int,
    val name: String,
    val qty: Int,
    val unitPrice: String,
    val subtotal: String,
    val tax: String,
    val total: String,
    val note: String? = null,
    val modifiers: List<KioskQuoteLineModifierResponse> = emptyList(),
)

@Serializable
data class KioskQuoteResponse(
    val orderId: String,
    val formattedOrderNumber: String,
    val subtotal: String,
    val tax: String,
    val total: String,
    val expiresAt: String,
    val diningMode: String,
    val tableTent: String? = null,
    val customerId: String,
    val lines: List<KioskQuoteLineResponse>,
)

@Serializable
data class KioskPaymentRequest(
    val transactionId: String,
    val authCode: String,
    val reference: String,
    val last4: String,
    val brand: String,
    val amount: String,
)

@Serializable
data class KioskInvoiceInfo(
    val codFactura: String,
    val cufe: String? = null,
    val qr: String? = null,
    val fechaRecepcionDGI: String? = null,
    val numeroDocumentoFiscal: String? = null,
    val numeroControl: String? = null,
)

@Serializable
data class KioskReceiptLine(
    val qty: Int,
    val description: String,
    val price: String,
    val total: String,
    val modifiers: List<String> = emptyList(),
)

@Serializable
data class KioskReceipt(
    val companyName: String,
    val ruc: String? = null,
    val dv: String? = null,
    val address: String? = null,
    val orderNumber: String,
    val diningMode: String,
    val tableTent: String? = null,
    val customerName: String,
    val customerId: String,
    val date: String,
    val lines: List<KioskReceiptLine>,
    val subtotal: String,
    val tax: String,
    val total: String,
    val paymentBrand: String,
    val paymentLast4: String,
    val paymentAuthCode: String,
    val paymentReference: String,
    val invoiceNumber: String? = null,
    val cufe: String? = null,
    val qr: String? = null,
)

@Serializable
data class KioskPayResponse(
    val orderNumber: String,
    val invoice: KioskInvoiceInfo? = null,
    val dispatch: String,
    val receipt: KioskReceipt,
    val status: String,
)

data class KioskDevice(
    val id: String,
    val nombre: String,
    val prefijoPedido: String,
    val idCaja: String,
    val idSucursal: Int,
    val idAlmacen: Int,
    val codVendedor: Int,
    val idClienteGenerico: String,
    val tokenHash: String?,
    val codigoEmparejamientoHash: String?,
    val codigoExpiraEn: LocalDateTime?,
    val activo: Boolean,
    val ultimoContacto: LocalDateTime?,
    val creadoEn: LocalDateTime,
)

data class KioskRequestContext(
    val countryCode: String,
    val companyDb: String,
    val deviceId: String,
    val deviceName: String,
    val prefix: String,
    val idCaja: String,
    val idSucursal: Int,
    val idAlmacen: Int,
    val codVendedor: Int,
    val idClienteGenerico: String,
    val principal: JWTPrincipal,
)

data class KioskOrderRecord(
    val id: String,
    val idDispositivo: String,
    val numeroPedidoDiario: Int,
    val codigoPedido: String,
    val fecha: LocalDate,
    val estado: String,
    val modalidad: String,
    val portamesa: String?,
    val idCliente: String,
    val total: BigDecimal,
    val quoteExpiraEn: LocalDateTime,
    val pagoReferencia: String?,
    val pagoAutorizacion: String?,
    val pagoUltimos4: String?,
    val pagoMarca: String?,
    val idFactura: String?,
    val motivoRechazo: String?,
    val creadoEn: LocalDateTime,
    val actualizadoEn: LocalDateTime,
    val items: List<KioskOrderItemRecord>,
)

data class KioskOrderItemRecord(
    val idPedido: String,
    val linea: Int,
    val idItem: Int,
    val cantidad: BigDecimal,
    val precioUnitario: BigDecimal,
    val nota: String?,
    val modifiers: List<KioskOrderModifierRecord>,
)

data class KioskOrderModifierRecord(
    val idPedido: String,
    val linea: Int,
    val idModificador: Int,
    val nombre: String,
    val precioAdicional: BigDecimal,
)
