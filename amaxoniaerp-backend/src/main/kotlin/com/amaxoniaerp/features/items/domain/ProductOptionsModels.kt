package com.amaxoniaerp.features.items.domain

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ProductOptionsResponse(
    val success: Boolean = true,
    @SerialName("id_combo")
    val idCombo: String? = null,
    val grupos: List<ProductOptionGroupResponse> = emptyList(),
)

@Serializable
data class ProductOptionGroupResponse(
    @SerialName("id_grupo")
    val idGrupo: String,
    val tipo: String = "COMBO",
    val nombre: String,
    @SerialName("nombre_grupo")
    val nombreGrupo: String? = null,
    val minimo: Int = 0,
    val maximo: Int = 1,
    @SerialName("maximo_veces")
    val maximoVeces: Int = 1,
    val adiciona: Int = 0,
    val opciones: List<ProductOptionItemResponse> = emptyList(),
    val items: List<ProductOptionItemResponse> = emptyList(),
)

@Serializable
data class ProductOptionItemResponse(
    @SerialName("id_grupo_item")
    val idGrupoItem: String,
    @SerialName("id_item")
    val idItem: String? = null,
    @SerialName("cod_item")
    val codItem: String = "",
    val nombre: String,
    @SerialName("nombre_opcion")
    val nombreOpcion: String? = null,
    val descripcion1: String? = null,
    val precio: Double = 0.0,
    @SerialName("precio_extra")
    val precioExtra: Double? = null,
    val coniva1: Double? = null,
    val cantidad: Double = 1.0,
    @SerialName("por_defecto")
    val porDefecto: Int = 0,
    val agotado: Int = 0,
    val foto: String? = null,
    val foto1: String? = null,
)
