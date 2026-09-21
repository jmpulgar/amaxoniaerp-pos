package com.amaxonia.pos.domain.model.caja

import kotlinx.serialization.Serializable

@Serializable
data class CajaStatusResponse(
    val isOpen: Boolean = false,
    val cajaSecuencia: CajaSecuencia? = null,
    val error: String? = null,
)
