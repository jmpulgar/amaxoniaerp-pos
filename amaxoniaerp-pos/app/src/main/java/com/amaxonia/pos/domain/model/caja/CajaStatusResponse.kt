package com.amaxonia.pos.domain.model.caja

import kotlinx.serialization.Serializable

@Serializable
data class CajaStatusResponse(
    val isOpen: Boolean = true,
    val cajaSecuencia: CajaSecuencia? = null,
    val error: String? = null,
)
