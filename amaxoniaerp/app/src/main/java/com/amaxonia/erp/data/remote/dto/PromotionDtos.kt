package com.amaxonia.erp.data.remote.dto

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive

object FlexibleStringSerializer : KSerializer<String> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("FlexibleString", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: String) = encoder.encodeString(value)

    override fun deserialize(decoder: Decoder): String {
        val jsonDecoder = decoder as? JsonDecoder ?: return decoder.decodeString()
        return jsonDecoder.decodeJsonElement().jsonPrimitive.content
    }
}

object FlexibleDoubleSerializer : KSerializer<Double> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("FlexibleDouble", PrimitiveKind.DOUBLE)

    override fun serialize(encoder: Encoder, value: Double) = encoder.encodeDouble(value)

    override fun deserialize(decoder: Decoder): Double {
        val jsonDecoder = decoder as? JsonDecoder ?: return decoder.decodeDouble()
        val primitive = jsonDecoder.decodeJsonElement().jsonPrimitive
        return primitive.doubleOrNull ?: primitive.content.toDoubleOrNull() ?: 0.0
    }
}

object FlexibleBooleanSerializer : KSerializer<Boolean> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("FlexibleBoolean", PrimitiveKind.BOOLEAN)

    override fun serialize(encoder: Encoder, value: Boolean) = encoder.encodeBoolean(value)

    override fun deserialize(decoder: Decoder): Boolean {
        val jsonDecoder = decoder as? JsonDecoder ?: return decoder.decodeBoolean()
        val primitive: JsonPrimitive = jsonDecoder.decodeJsonElement().jsonPrimitive
        return primitive.booleanOrNull ?: primitive.intOrNull?.let { it == 1 } ?: primitive.content.equals("1")
    }
}

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
