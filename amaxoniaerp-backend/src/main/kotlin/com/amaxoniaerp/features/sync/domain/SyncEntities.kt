package com.amaxoniaerp.features.sync.domain

import kotlinx.serialization.Serializable

/**
 * Entidades del catálogo cubiertas por el change feed y el sync incremental.
 * Los nombres wire coinciden con el `entity_type` de `catalog_changes` y las
 * entradas `entities[]` del manifest (PLAN_OFFLINE_SYNC_DESIGN.md §3.1).
 */
enum class SyncEntityType(
    val wire: String,
) {
    PRODUCT("PRODUCT"),
    CLIENT("CLIENT"),
    CLIENT_BRANCH("CLIENT_BRANCH"),
    CLIENT_TYPE("CLIENT_TYPE"),
    PROMOTION("PROMOTION"),
    PROMOTION_DETAIL("PROMOTION_DETAIL"),
    PAYMENT_METHOD("PAYMENT_METHOD"),
    CAJA_PAYMENT_METHOD("CAJA_PAYMENT_METHOD"),
}

/**
 * DTO slim de PRODUCT: únicamente los campos que el POS almacena en Room.
 * El orden de declaración define el orden canónico del hash de contenido
 * (CatalogContentHash.ProductDigest): NO reordenar sin re-fijar el fixture
 * cruzado FIXTURE_DIGEST_V1.
 */
@Serializable
data class ProductSyncDto(
    val id: String,
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
data class PriceLevelSyncDto(
    val label: String,
    val price: Double = 0.0,
    val utilityPercent: Double = 0.0,
    val pricePlusUtility: Double = 0.0,
    val pricePlusTax: Double = 0.0,
    val unitPrice: Double = 0.0,
    val unitPricePlusTax: Double = 0.0,
    val discountPercent: Double = 0.0,
)

/**
 * DTO slim de CLIENT. Campos canónicos del hash de contenido (orden fijado,
 * espejo del que implementará el POS en F2 — PLAN §3.1.1):
 * code, rif, dv, nombre, apellido, direccion, direccionNivel1..3, telefonos,
 * email, activo, codTipoCliente, codTipoPrecio, tipoContribuyente, pais,
 * permiteCredito, limite, dias, idSucursal.
 */
@Serializable
data class ClientSyncDto(
    val id: String,
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

/** Espejo directo de `cliente_sucursal` (sucursales DEL cliente, feature PA). */
@Serializable
data class ClientBranchSyncDto(
    val sucursalId: Int,
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
    val id: Int,
    val descripcion: String = "",
    val tipoClienteFe: String? = null,
)

@Serializable
data class PromotionSyncDto(
    val id: String,
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
    val id: String,
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

/** Espejo directo de `caja_forma_pago` (catálogo de formas de pago). */
@Serializable
data class PaymentMethodSyncDto(
    val idFormaPago: Int,
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

/** Mapeo caja ↔ forma de pago (`caja_forma`, PK compuesta). */
@Serializable
data class CajaPaymentMethodSyncDto(
    val idCaja: String,
    val idFormaPago: Int,
    val activo: Int? = null,
)
