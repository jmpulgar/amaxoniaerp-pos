package com.amaxonia.pos.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PromocionDto(
    @Serializable(with = FlexibleStringSerializer::class)
    val id: String = "",
    val codigo: String = "",
    val inicio: String? = null,
    val fin: String? = null,
    val promocion: String = "",
    val imagen: String = "",
    @SerialName("descuento_global")
    @Serializable(with = FlexibleDoubleSerializer::class)
    val descuentoGlobal: Double = 0.0,
    @SerialName("id_item")
    @Serializable(with = FlexibleStringSerializer::class)
    val idItem: String = "",
    @Serializable(with = FlexibleBooleanSerializer::class)
    val activo: Boolean = true,
    val detalle: List<PromocionDetalleDto> = emptyList(),
)

@Serializable
data class PromocionDetalleDto(
    @SerialName("id_promocion_detalle")
    @Serializable(with = FlexibleStringSerializer::class)
    val idPromocionDetalle: String = "",
    @SerialName("id_item")
    @Serializable(with = FlexibleStringSerializer::class)
    val idItem: String = "",
    @SerialName("id_tipo_precio")
    @Serializable(with = FlexibleStringSerializer::class)
    val idTipoPrecio: String = "",
    @Serializable(with = FlexibleDoubleSerializer::class)
    val cantidad: Double = 0.0,
    @SerialName("cantidad_total")
    @Serializable(with = FlexibleDoubleSerializer::class)
    val cantidadTotal: Double = 0.0,
    @SerialName("unidad_empaque")
    val unidadEmpaque: String = "",
    @Serializable(with = FlexibleDoubleSerializer::class)
    val descuento: Double = 0.0,
    @SerialName("descuento_monto")
    @Serializable(with = FlexibleDoubleSerializer::class)
    val descuentoMonto: Double = 0.0,
    @Serializable(with = FlexibleDoubleSerializer::class)
    val precio: Double = 0.0,
    @Serializable(with = FlexibleDoubleSerializer::class)
    val impuesto: Double = 0.0,
    @SerialName("impuesto_promocion_detalle")
    @Serializable(with = FlexibleDoubleSerializer::class)
    val impuestoPromocionDetalle: Double = 0.0,
    @SerialName("impuesto_porcentaje")
    @Serializable(with = FlexibleDoubleSerializer::class)
    val impuestoPorcentaje: Double = 0.0,
    @Serializable(with = FlexibleDoubleSerializer::class)
    val importe: Double = 0.0,
    val grupo: String = "",
) {
    val resolvedTaxPercent: Double get() = impuestoPromocionDetalle.takeIf { it > 0.0 } ?: impuestoPorcentaje
}
