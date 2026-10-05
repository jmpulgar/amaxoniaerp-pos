package com.amaxonia.erp.domain.model

data class Sucursal(
    val id: String,
    val nombre: String,
    val codigo: String? = null,
    val serie: String? = null,
    val codigoSucursalEmisor: String? = null,
    val descripcion: String? = null,
    val defaultWarehouseId: Int? = null,
    val cajasCount: Int = 0,
    val isActive: Boolean = false,
)
