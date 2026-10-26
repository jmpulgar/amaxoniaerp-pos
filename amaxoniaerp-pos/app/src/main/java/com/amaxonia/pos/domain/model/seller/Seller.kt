package com.amaxonia.pos.domain.model.seller

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Seller(
    val id: Int,
    val nombre: String,
    @SerialName("id_clientes")
    val idClientes: String? = null,
    @SerialName("crear_clientes")
    val crearClientes: Boolean = true,
) {
    fun allowedClientIds(): Set<String> =
        idClientes
            ?.split(",")
            ?.map { it.trim() }
            ?.filter { it.isNotBlank() }
            ?.toSet()
            .orEmpty()

    fun canCreateClients(): Boolean = crearClientes
}
