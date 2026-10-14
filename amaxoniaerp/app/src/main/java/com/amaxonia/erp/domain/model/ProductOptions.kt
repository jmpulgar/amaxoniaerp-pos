package com.amaxonia.erp.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

data class ProductOptionGroup(
    val id: String,
    val type: String, // "INCLUIDO", "COMBO", "MODIFICADOR"
    val name: String,
    val min: Int,
    val max: Int,
    val options: List<ProductOptionItem>,
) {
    val isIncluded: Boolean get() = type.equals("INCLUIDO", ignoreCase = true)
    val isCombo: Boolean get() = type.equals("COMBO", ignoreCase = true)
    val isModifier: Boolean get() = type.equals("MODIFICADOR", ignoreCase = true)
    val isMandatory: Boolean get() = min > 0
}

data class ProductOptionItem(
    val id: String,
    val productId: String? = null,
    val productCode: String = "",
    val name: String,
    val extraPrice: Double = 0.0,
    val quantity: Double = 1.0,
    val isDefault: Boolean = false,
    val isOutOfStock: Boolean = false,
    val photoUrl: String = "",
)

@Serializable
data class SelectedOption(
    @SerialName("id_grupo")
    val idGrupo: String = "",
    @SerialName("grupo")
    val grupo: String = "",
    @SerialName("tipo")
    val tipo: String = "",
    @SerialName("id_grupo_item")
    val idGrupoItem: String = "",
    @SerialName("id_item")
    val idItem: String? = null,
    @SerialName("cod_item")
    val codItem: String = "",
    @SerialName("nombre")
    val nombre: String = "",
    @SerialName("precio")
    val precio: Double = 0.0,
    @SerialName("cantidad")
    val cantidad: Double = 1.0,
    @SerialName("veces")
    val veces: Int = 1,
)

@Serializable
data class ProductComposition(
    @SerialName("id_combo")
    val idCombo: String = "",
    @SerialName("opciones")
    val opciones: List<SelectedOption> = emptyList(),
    @SerialName("extra_coniva")
    val extraConIva: Double = 0.0,
    @SerialName("extra_siniva")
    val extraSinIva: Double = 0.0,
)
