package com.amaxonia.pos.data.sync

import kotlinx.serialization.Serializable

/**
 * Espejo del contrato /api/sync/v1 (backend features/sync). Los campos deben
 * coincidir 1:1 con el wire format del servidor; el Json del POS usa
 * ignoreUnknownKeys para evolución additive-only (PLAN §9).
 */

@Serializable
data class PriceLevelSyncDto(
    val label: String = "",
    val price: Double = 0.0,
    val utilityPercent: Double = 0.0,
    val pricePlusUtility: Double = 0.0,
    val pricePlusTax: Double = 0.0,
    val unitPrice: Double = 0.0,
    val unitPricePlusTax: Double = 0.0,
    val discountPercent: Double = 0.0,
)

@Serializable
data class ProductSyncDto(
    val id: String = "",
    val code: String = "",
    val description: String = "",
    val reference: String = "",
    val barcode1: String = "",
    val barcode2: String = "",
    val barcode3: String = "",
    val department: Int = 0,
    val isExempt: Boolean = false,
    val taxRate: Double = 0.0,
    val costActual: Double = 0.0,
    val unitPackage: String = "",
    val bulkQuantity: Double = 1.0,
    val portionUnit: String? = null,
    val unitOrPackage: String = "UNIDAD",
    val estatus: String? = null,
    val prices: List<PriceLevelSyncDto> = emptyList(),
)

@Serializable
data class ClientSyncDto(
    val id: String = "",
    val code: String = "",
    val rif: String = "",
    val dv: String? = null,
    val nombre: String = "",
    val apellido: String? = null,
    val direccion: String = "",
    val direccionNivel1: String? = null,
    val direccionNivel2: String? = null,
    val direccionNivel3: String? = null,
    val telefonos: String = "",
    val email: String = "",
    val activo: Boolean = true,
    val codTipoCliente: Int = 1,
    val codTipoPrecio: Int = 2,
    val tipoContribuyente: Int = 1,
    val pais: Int = 1,
    val permiteCredito: Boolean = false,
    val limite: Double = 0.0,
    val dias: Int = 0,
    val idSucursal: Int? = null,
)

@Serializable
data class ClientBranchSyncDto(
    val sucursalId: Int = 0,
    val clienteCodigo: String = "",
    val nombreSucursal: String = "",
    val nombreContacto: String? = null,
    val telefonoContacto: String? = null,
    val correoContacto: String? = null,
    val direccion: String? = null,
    val observaciones: String? = null,
)

@Serializable
data class ClientTypeSyncDto(
    val id: Int = 0,
    val descripcion: String = "",
    val tipoClienteFe: String? = null,
)

@Serializable
data class PromotionSyncDto(
    val id: String = "",
    val idItem: String = "",
    val codigo: String = "",
    val inicio: String? = null,
    val fin: String? = null,
    val promocion: String = "",
    val imagen: String = "",
    val activo: Int = 1,
    val descuentoGlobal: Double = 0.0,
)

@Serializable
data class PromotionDetailSyncDto(
    val id: String = "",
    val idPromocion: String = "",
    val idItem: String = "",
    val cantidad: Double = 0.0,
    val cantidadTotal: Double = 0.0,
    val unidadEmpaque: String = "",
    val descuento: Double = 0.0,
    val descuentoMonto: Double = 0.0,
    val idTipoPrecio: String? = null,
    val precio: Double = 0.0,
    val impuesto: Double = 0.0,
    val impuestoPorcentaje: Double = 0.0,
    val importe: Double = 0.0,
    val grupo: String = "",
)

@Serializable
data class PaymentMethodSyncDto(
    val idFormaPago: Int = 0,
    val siglas: String? = null,
    val codigo: Int? = null,
    val descripcion: String? = null,
    val idCajaTpConcepto: Int? = null,
    val cuentaContable: String? = null,
    val idCajaTpRegistro: Int? = null,
    val formaPagoFact: String? = null,
    val activo: Int = 1,
    val pos: Int = 1,
    val imagen: String = "",
    val grupo: Int = 1,
    val orden: Int = 1,
    val idBancoCuenta: Int = 1,
    val idBancoOperacion: Int = 1,
    val tipoMoneda: String? = null,
)

@Serializable
data class CajaPaymentMethodSyncDto(
    val idCaja: String = "",
    val idFormaPago: Int = 0,
    val activo: Int? = null,
)

// ---------------------------------------------------------------------------
// Respuestas /api/sync/v1
// ---------------------------------------------------------------------------

@Serializable
data class SyncEntityStatDto(
    val type: String = "",
    val count: Long = 0,
    val hash: Long = 0,
)

@Serializable
data class SyncManifestDto(
    val hashScheme: String = "",
    val schemaVersion: Int = 0,
    val retentionDays: Int = 30,
    val latestChangeId: Long = 0,
    val entities: List<SyncEntityStatDto> = emptyList(),
)

@Serializable
data class SyncDeltaChangeDto(
    val changeId: Long = 0,
    val entityType: String = "",
    val entityId: String = "",
    val op: String = "",
    val payload: kotlinx.serialization.json.JsonElement? = null,
)

@Serializable
data class SyncDeltaResponseDto(
    val changes: List<SyncDeltaChangeDto> = emptyList(),
    val nextCursor: Long = 0,
    val hasMore: Boolean = false,
)

@Serializable
data class SyncBootstrapResponseDto(
    val entityType: String = "",
    val items: List<kotlinx.serialization.json.JsonElement> = emptyList(),
    val nextAfterId: String? = null,
    val hasMore: Boolean = false,
)

@Serializable
data class SyncScopePreviewDto(
    val entityType: String = "",
    val count: Long = 0,
)

@Serializable
data class SyncCatalogItemDto(
    val id: String = "",
    val nombre: String? = null,
    val conteo: Long? = null,
)
