package com.amaxoniaerp.features.kiosk.domain

import io.ktor.server.auth.jwt.JWTPrincipal
import kotlinx.serialization.Serializable
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
