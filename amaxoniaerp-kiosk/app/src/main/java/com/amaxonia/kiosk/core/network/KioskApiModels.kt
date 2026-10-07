package com.amaxonia.kiosk.core.network

import kotlinx.serialization.Serializable

// --- System login (same contract as the POS: contracts/auth/*.json) ---

@Serializable
data class KioskLoginRequest(
    val username: String,
    val password: String,
)

@Serializable
data class KioskAuthUserDto(
    val id: Int,
    val username: String,
    val role: String = "",
)

@Serializable
data class KioskCompanyDto(
    val id: Int,
    val name: String,
    val rif: String? = null,
)

@Serializable
data class KioskLoginResponse(
    val token: String,
    val user: KioskAuthUserDto,
    val companies: List<KioskCompanyDto> = emptyList(),
    val countryCode: String? = null,
    val schemaType: String? = null,
)

@Serializable
data class KioskSelectCompanyRequest(
    val companyId: Int,
)

@Serializable
data class KioskCompanyDetailsDto(
    val id: Int,
    val name: String,
    val adminDb: String = "",
    val accountingDb: String = "",
    val payrollDb: String = "",
    val rif: String? = null,
)

@Serializable
data class KioskSelectCompanyResponse(
    val success: Boolean = true,
    val token: String,
    val currentCompany: KioskCompanyDetailsDto,
    val countryCode: String? = null,
    val schemaType: String? = null,
)

/** One row of `GET api/cajas` (backend `features/caja/domain/Caja`); only the fields the kiosk uses. */
@Serializable
data class KioskCajaDto(
    val idCaja: String,
    val codCaja: String? = null,
    val caja: String? = null,
    val descripcion: String? = null,
    val estatus: Int = CAJA_ACTIVE,
    val idSucursal: Int? = null,
    val serieCaja: String? = null,
    val sucursalNombre: String? = null,
    val sucursalCodigo: String? = null,
) {
    val isActive: Boolean
        get() = estatus == CAJA_ACTIVE

    /** Name shown to the operator: description, then the short name, then the code. */
    val displayName: String
        get() =
            listOf(descripcion, caja, codCaja)
                .firstOrNull { !it.isNullOrBlank() }
                ?.trim()
                ?: idCaja

    companion object {
        /** `caja.activo = 1` (same rule as the POS caja selector). */
        const val CAJA_ACTIVE = 1
    }
}

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
    val base: String = "USD",
    val secondary: String? = null,
    val rate: String = "1.0000",
)

@Serializable
data class KioskConfigResponse(
    val version: Int = 1,
    val brandColor: String? = null,
    val logoUrl: String? = null,
    val media: List<KioskMediaItem> = emptyList(),
    val diningModes: List<String> = emptyList(),
    val dispatch: String = "RETIRO_MOSTRADOR",
    val defaultCustomerId: String = "CF",
    val currency: KioskCurrencyConfig = KioskCurrencyConfig(),
    val country: String = "PA",
    val paymentMethods: List<String> = listOf("CARD"),
    /** Card payment methods of the company (`caja_forma_pago`: VISA, MASTERCARD, débito...). */
    val cardOptions: List<KioskCardOption> = emptyList(),
)

/** A card payment method the kiosk lists; [id] is `caja_forma_pago.id_forma_pago`, [image] a data URI. */
@Serializable
data class KioskCardOption(
    val id: Int,
    val name: String,
    val siglas: String = "",
    val image: String? = null,
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
    /** Preselected in the customizer (default option of the Combos module). */
    val isDefault: Boolean = false,
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
    val method: String,
    /** CARD only: the chosen `cardOptions[].id`; null = the company's default card method. */
    val paymentMethodId: Int? = null,
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

@Serializable
data class KioskYappyChargeResponse(
    val transactionId: String,
    val qrHash: String,
    val amount: String,
    val expiresInSec: Int,
)

@Serializable
data class KioskYappyStatusResponse(
    val transactionId: String,
    val status: String,
)

@Serializable
data class KioskErrorResponse(
    val error: String? = null,
)
