package com.amaxonia.kiosk.core.network

import kotlinx.serialization.Serializable

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
    val type: String,
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
data class KioskPaymentResponse(
    val orderNumber: String,
    val status: String,
    val invoice: KioskInvoiceInfo? = null,
    val dispatch: String,
    val receipt: KioskReceipt,
)
