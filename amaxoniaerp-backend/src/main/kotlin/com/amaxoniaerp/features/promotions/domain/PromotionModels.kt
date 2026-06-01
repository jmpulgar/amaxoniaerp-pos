package com.amaxoniaerp.features.promotions.domain

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PromotionResponse(
    val id: String,
    val codigo: String,
    val inicio: String? = null,
    val fin: String? = null,
    val promocion: String,
    val imagen: String = "",
    @SerialName("descuento_global") val descuentoGlobal: Double = 0.0,
    @SerialName("id_item") val idItem: String = "",
    val activo: Boolean = true,
    val detalle: List<PromotionDetailResponse> = emptyList(),
)

@Serializable
data class PromotionDetailResponse(
    @SerialName("id_promocion_detalle") val idPromocionDetalle: String,
    @SerialName("id_item") val idItem: String,
    @SerialName("id_tipo_precio") val idTipoPrecio: String,
    val cantidad: Double,
    @SerialName("cantidad_total") val cantidadTotal: Double,
    @SerialName("unidad_empaque") val unidadEmpaque: String,
    val descuento: Double,
    @SerialName("descuento_monto") val descuentoMonto: Double,
    val precio: Double,
    val impuesto: Double,
    @SerialName("impuesto_promocion_detalle") val impuestoPromocionDetalle: Double,
    @SerialName("impuesto_porcentaje") val impuestoPorcentaje: Double,
    val importe: Double,
    val grupo: String,
)

@Serializable
data class PromotionsListResponse(
    val data: List<PromotionResponse>,
)
