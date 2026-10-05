package com.amaxoniaerp.features.kiosk.domain

import io.ktor.server.auth.jwt.JWTPrincipal
import kotlinx.serialization.Serializable
import java.time.LocalDateTime

@Serializable
data class KioskPairingRequest(
    val countryCode: String,
    val companyDb: String,
    val pairingCode: String,
)

@Serializable
data class KioskPairingResponse(
    val deviceId: String,
    val deviceToken: String,
    val deviceName: String,
    val prefix: String,
)

@Serializable
data class KioskUnlockRequest(
    val password: String,
)

data class KioskDevice(
    val id: String,
    val nombre: String,
    val prefijoPedido: String,
    val idCaja: String,
    val idSucursal: Int,
    val idAlmacen: Int,
    val codVendedor: Int,
    val idClienteGenerico: String,
    val tokenHash: String?,
    val codigoEmparejamientoHash: String?,
    val codigoExpiraEn: LocalDateTime?,
    val activo: Boolean,
    val ultimoContacto: LocalDateTime?,
    val creadoEn: LocalDateTime,
)

data class KioskRequestContext(
    val countryCode: String,
    val companyDb: String,
    val deviceId: String,
    val deviceName: String,
    val prefix: String,
    val idCaja: String,
    val idSucursal: Int,
    val idAlmacen: Int,
    val codVendedor: Int,
    val idClienteGenerico: String,
    val principal: JWTPrincipal,
)
