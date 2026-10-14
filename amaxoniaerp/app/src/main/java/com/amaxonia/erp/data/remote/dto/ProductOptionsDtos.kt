package com.amaxonia.erp.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ProductOptionsResponseDto(
    val success: Boolean = true,
    val message: String? = null,
    @SerialName("id_combo")
    @Serializable(with = LenientStringSerializer::class)
    val idCombo: String? = null,
    val grupos: List<ProductOptionGroupDto> = emptyList(),
    val data: List<ProductOptionGroupDto> = emptyList(),
) {
    val resolvedGroups: List<ProductOptionGroupDto>
        get() = grupos.ifEmpty { data }
}

@Serializable
data class ProductOptionGroupDto(
    @SerialName("id_grupo")
    @Serializable(with = LenientNonNullStringSerializer::class)
    val idGrupo: String = "",
    val tipo: String = "", // "INCLUIDO", "COMBO", "MODIFICADOR"
    val nombre: String = "",
    @SerialName("nombre_grupo")
    val nombreGrupo: String? = null,
    val minimo: Int = 0,
    val maximo: Int = 1,
    @SerialName("maximo_veces")
    val maximoVeces: Int? = null,
    val adiciona: Int = 0,
    val opciones: List<ProductOptionItemDto> = emptyList(),
    val items: List<ProductOptionItemDto> = emptyList(),
) {
    val displayName: String
        get() = nombre.ifBlank { nombreGrupo.orEmpty() }

    val effectiveMax: Int
        get() = maximoVeces?.takeIf { it > 0 } ?: maximo.coerceAtLeast(1)

    val effectiveMin: Int
        get() = if (minimo > 0) minimo else if (tipo.equals("INCLUIDO", ignoreCase = true)) 0 else 1

    val resolvedOptions: List<ProductOptionItemDto>
        get() = opciones.ifEmpty { items }
}

@Serializable
data class ProductOptionItemDto(
    @SerialName("id_grupo_item")
    @Serializable(with = LenientNonNullStringSerializer::class)
    val idGrupoItem: String = "",
    @SerialName("id_item")
    @Serializable(with = LenientStringSerializer::class)
    val idItem: String? = null,
    @SerialName("cod_item")
    val codItem: String = "",
    val nombre: String = "",
    @SerialName("nombre_opcion")
    val nombreOpcion: String? = null,
    val descripcion1: String? = null,
    val precio: Double = 0.0,
    @SerialName("precio_extra")
    val precioExtra: Double? = null,
    val coniva1: Double? = null,
    val precio1: Double? = null,
    val cantidad: Double = 1.0,
    @SerialName("por_defecto")
    val porDefecto: Int = 0,
    val agotado: Int = 0,
    val foto: String? = null,
    val foto1: String? = null,
    val photoUrl: String? = null,
    @SerialName("photo_url")
    val photoUrlSnake: String? = null,
) {
    val rawPhoto: String
        get() = photoUrl?.takeIf { it.isNotBlank() }
            ?: photoUrlSnake?.takeIf { it.isNotBlank() }
            ?: foto?.takeIf { it.isNotBlank() }
            ?: foto1?.takeIf { it.isNotBlank() }
            ?: ""

    val resolvedId: String
        get() = idGrupoItem.ifBlank { idItem.orEmpty() }

    val displayName: String
        get() = nombre.ifBlank { nombreOpcion?.takeIf { it.isNotBlank() } ?: descripcion1.orEmpty() }

    fun resolvePrice(grupoAdiciona: Int): Double {
        if (precioExtra != null) return precioExtra
        if (precio > 0.0) return precio
        if (grupoAdiciona == 1) {
            return coniva1?.takeIf { it > 0.0 } ?: precio1 ?: 0.0
        }
        return 0.0
    }
}
