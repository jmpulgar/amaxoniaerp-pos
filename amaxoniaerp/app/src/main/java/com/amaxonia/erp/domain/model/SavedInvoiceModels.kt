package com.amaxonia.erp.domain.model

import kotlinx.serialization.Serializable

enum class SavedInvoiceOrigin {
    ONLINE,
    OFFLINE_DRAFT,
}

enum class SavedInvoiceFilterTab {
    ALL,
    ONLINE,
    OFFLINE,
}

data class SavedInvoiceSummary(
    val id: String,
    val code: String,
    val clientName: String,
    val clientId: String? = null,
    val sellerId: Int? = null,
    val total: Double = 0.0,
    val itemCount: Int = 0,
    val date: String = "",
    val origin: SavedInvoiceOrigin = SavedInvoiceOrigin.ONLINE,
    val status: String = "En Espera",
    val payloadJson: String? = null,
    val codigoFiscal: String = "",
    val clienteIdentificacion: String = "",
    val sucursal: String = "",
    val caja: String = "",
    val vendedor: String = "",
    val formaPago: String = "Contado",
    val codEstatus: Int = 1,
)

@Serializable
data class DraftCartPayload(
    val clientId: String? = null,
    val clientName: String? = null,
    val sellerId: Int? = null,
    val observation: String = "",
    val globalDiscountPercent: Double = 0.0,
    val items: List<DraftCartItem> = emptyList(),
)

@Serializable
data class DraftCartItem(
    val productId: String,
    val quantity: Double,
    val unitPriceWithTax: Double,
    val discountPercent: Double = 0.0,
    val isManualPrice: Boolean = false,
    val selectedPriceLabel: String = "A",
    val description: String = "",
    val barcode: String = "",
)
