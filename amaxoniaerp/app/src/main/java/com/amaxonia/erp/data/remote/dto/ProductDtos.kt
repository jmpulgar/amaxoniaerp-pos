package com.amaxonia.erp.data.remote.dto

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive

@OptIn(ExperimentalSerializationApi::class)
object LenientStringSerializer : KSerializer<String?> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("LenientString", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: String?) {
        if (value != null) encoder.encodeString(value)
        else encoder.encodeNull()
    }

    override fun deserialize(decoder: Decoder): String? {
        val jsonInput = decoder as? JsonDecoder
        if (jsonInput != null) {
            val element = jsonInput.decodeJsonElement()
            return when (element) {
                is JsonNull -> null
                is JsonPrimitive -> element.content
                else -> element.toString()
            }
        }
        return decoder.decodeString()
    }
}

@OptIn(ExperimentalSerializationApi::class)
object LenientNonNullStringSerializer : KSerializer<String> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("LenientNonNullString", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: String) {
        encoder.encodeString(value)
    }

    override fun deserialize(decoder: Decoder): String {
        val jsonInput = decoder as? JsonDecoder
        if (jsonInput != null) {
            val element = jsonInput.decodeJsonElement()
            return when (element) {
                is JsonNull -> ""
                is JsonPrimitive -> element.content
                else -> element.toString()
            }
        }
        return runCatching { decoder.decodeString() }.getOrDefault("")
    }
}

@OptIn(ExperimentalSerializationApi::class)
object LenientDoubleSerializer : KSerializer<Double?> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("LenientDouble", PrimitiveKind.DOUBLE)

    override fun serialize(encoder: Encoder, value: Double?) {
        if (value != null) encoder.encodeDouble(value)
        else encoder.encodeNull()
    }

    override fun deserialize(decoder: Decoder): Double? {
        val jsonInput = decoder as? JsonDecoder
        if (jsonInput != null) {
            val element = jsonInput.decodeJsonElement()
            return when (element) {
                is JsonNull -> null
                is JsonPrimitive -> element.content.toDoubleOrNull()
                else -> null
            }
        }
        return runCatching { decoder.decodeDouble() }.getOrNull()
    }
}

@OptIn(ExperimentalSerializationApi::class)
object LenientBooleanSerializer : KSerializer<Boolean?> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("LenientBoolean", PrimitiveKind.BOOLEAN)

    override fun serialize(encoder: Encoder, value: Boolean?) {
        if (value != null) encoder.encodeBoolean(value)
        else encoder.encodeNull()
    }

    override fun deserialize(decoder: Decoder): Boolean? {
        val jsonInput = decoder as? JsonDecoder
        if (jsonInput != null) {
            val element = jsonInput.decodeJsonElement()
            return when (element) {
                is JsonNull -> null
                is JsonPrimitive -> {
                    val s = element.content.trim()
                    s.equals("true", ignoreCase = true) || s == "1" || s.equals("si", ignoreCase = true) || s.equals("t", ignoreCase = true)
                }
                else -> null
            }
        }
        return runCatching { decoder.decodeBoolean() }.getOrNull()
    }
}

@Serializable
data class ProductDto(
    val id: String? = null,
    val code: String? = null,
    val description: String? = null,
    val reference: String? = null,
    val barcode1: String? = null,
    val barcode2: String? = null,
    val barcode3: String? = null,
    val photoUrl: String? = null,
    @SerialName("photo_url")
    val photoUrlSnake: String? = null,
    @SerialName("foto")
    val foto: String? = null,
    @SerialName("foto1")
    val foto1: String? = null,
    val department: String? = null,
    @Serializable(with = LenientBooleanSerializer::class)
    val isExempt: Boolean? = null,
    @SerialName("is_exempt")
    @Serializable(with = LenientBooleanSerializer::class)
    val isExemptSnake: Boolean? = null,
    @SerialName("monto_exento")
    @Serializable(with = LenientStringSerializer::class)
    val montoExento: String? = null,
    @Serializable(with = LenientDoubleSerializer::class)
    val taxRate: Double? = null,
    @SerialName("tax_rate")
    @Serializable(with = LenientDoubleSerializer::class)
    val taxRateSnake: Double? = null,
    @SerialName("iva")
    @Serializable(with = LenientDoubleSerializer::class)
    val iva: Double? = null,
    @SerialName("porcentaje_iva")
    @Serializable(with = LenientDoubleSerializer::class)
    val porcentajeIva: Double? = null,
    val costActual: Double? = null,
    val unitPackage: String? = null,
    val prices: List<PriceDto> = emptyList(),
    @SerialName("posee_producto_combo")
    @Serializable(with = LenientStringSerializer::class)
    val poseeProductoCombo: String? = null,
    @SerialName("id_combo")
    @Serializable(with = LenientStringSerializer::class)
    val idCombo: String? = null,
    @SerialName("tipo_articulo")
    val tipoArticulo: String? = null,
    @SerialName("posee_variantes")
    @Serializable(with = LenientStringSerializer::class)
    val poseeVariantes: String? = null,
    @SerialName("variantes_total")
    val variantesTotal: Int? = null,
) {
    val rawPhoto: String
        get() = photoUrl?.takeIf { it.isNotBlank() }
            ?: photoUrlSnake?.takeIf { it.isNotBlank() }
            ?: foto?.takeIf { it.isNotBlank() }
            ?: foto1?.takeIf { it.isNotBlank() }
            ?: ""

    val resolvedTaxRate: Double
        get() = taxRate ?: iva ?: taxRateSnake ?: porcentajeIva ?: 0.0

    val resolvedIsExempt: Boolean
        get() = isExempt
            ?: isExemptSnake
            ?: (montoExento == "1" || montoExento.equals("true", ignoreCase = true) || montoExento.equals("si", ignoreCase = true))

    fun resolveEffectiveTaxRate(countryCode: String): Double {
        if (resolvedIsExempt) return 0.0
        val tax = resolvedTaxRate
        return if (tax > 0.0) {
            tax
        } else if (countryCode.equals("PA", ignoreCase = true)) {
            7.0 // Tasa estándar ITBMS Panamá
        } else {
            0.0
        }
    }
}

@Serializable
data class PriceDto(
    val label: String,
    val name: String? = null,
    @SerialName("descripcion")
    val descripcion: String? = null,
    val price: Double = 0.0,
    val utilityPercent: Double = 0.0,
    val pricePlusUtility: Double = 0.0,
    val pricePlusTax: Double = 0.0,
) {
    val resolvedName: String
        get() = name?.takeIf { it.isNotBlank() }
            ?: descripcion?.takeIf { it.isNotBlank() }
            ?: com.amaxonia.erp.domain.model.defaultPriceName(label)
}

@Serializable
data class CreateProductRequest(
    val code: String,
    val description: String,
    val departmentId: Int = 1,
    val barcode1: String = "",
    val price: Double = 0.0,
    val taxRate: Double = 0.0,
    val isExempt: Boolean = false,
)

@Serializable
data class DepartmentDto(
    val id: Int = 0,
    val name: String = "",
    val nombre: String? = null,
    val descripcion: String? = null,
) {
    val displayName: String
        get() = name.ifBlank { nombre ?: descripcion ?: "Departamento $id" }
}

@Serializable
data class DepartmentsResponse(
    val data: List<DepartmentDto> = emptyList(),
)
