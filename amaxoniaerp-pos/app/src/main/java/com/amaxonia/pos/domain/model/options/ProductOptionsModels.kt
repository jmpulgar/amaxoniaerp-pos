package com.amaxonia.pos.domain.model.options

import kotlinx.serialization.Serializable
import java.util.Locale

@Serializable
data class SelectedOption(
    val idGrupo: String,
    val grupo: String,
    val tipo: String,
    val idGrupoItem: String,
    val idItem: String? = null,
    val codItem: String = "",
    val nombre: String,
    val precio: Double = 0.0,
    val cantidad: Double = 1.0,
    val veces: Int = 1,
)

@Serializable
data class ProductComposition(
    val idCombo: String = "",
    val opciones: List<SelectedOption> = emptyList(),
    val extraConIva: Double = 0.0,
    val extraSinIva: Double = 0.0,
) {
    val summaryText: String
        get() = opciones.joinToString(separator = ", ") { opt ->
            val extraText = if (opt.precio > 0) " (+$${String.format(Locale.US, "%.2f", opt.precio)})" else ""
            val timesText = if (opt.veces > 1) "${opt.veces}x " else ""
            "$timesText${opt.nombre}$extraText"
        }
}

@Serializable
data class ProductOptionGroup(
    val idGrupo: String,
    val tipo: String,
    val nombre: String,
    val minimo: Int,
    val maximo: Int,
    val opciones: List<ProductOptionItem>,
) {
    val isMandatory: Boolean get() = minimo > 0
    val isSingleChoice: Boolean get() = maximo == 1
    val isIncluded: Boolean get() = tipo.equals("INCLUIDO", ignoreCase = true)
}

@Serializable
data class ProductOptionItem(
    val idGrupoItem: String,
    val idItem: String?,
    val codItem: String,
    val nombre: String,
    val precioExtra: Double,
    val cantidad: Double,
    val isDefault: Boolean,
    val isSoldOut: Boolean,
    val photoUrl: String?,
)
