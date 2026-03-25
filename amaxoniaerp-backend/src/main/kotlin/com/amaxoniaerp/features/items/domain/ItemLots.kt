package com.amaxoniaerp.features.items.domain

import kotlinx.serialization.Serializable

@Serializable
data class ItemLotInfo(
    val idLoteItem: Int,
    val codigoLoteItem: String,
    val vencimiento: String? = null,
    val disponibilidad: Int,
    val idAlmacen: Int = 1
)

@Serializable
data class ItemLotsResponse(
    val itemId: Int,
    val poseeConfiguracionLote: Boolean,
    val lotes: List<ItemLotInfo>
)
