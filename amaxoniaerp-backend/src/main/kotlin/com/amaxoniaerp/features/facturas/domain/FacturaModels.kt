package com.amaxoniaerp.features.facturas.domain

import kotlinx.serialization.Serializable

@Serializable
data class FacturaSummary(
    val id: String,
    val codigo: String,
    val codigoFiscal: String,
    val numeroDocumentoFiscal: String,
    val fecha: String,
    val fechaDgi: String,
    val clienteNombre: String,
    val clienteIdentificacion: String,
    val total: Double,
    val estatus: String,
    val formaPago: String,
    val items: Int = 0,
)

@Serializable
data class FacturasListResponse(
    val data: List<FacturaSummary>,
    val total: Long,
)
