package com.amaxoniaerp.features.kiosk.route

import com.amaxoniaerp.features.kiosk.application.KioskService
import com.amaxoniaerp.features.kiosk.domain.KioskRequestContext
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.response.respond

suspend fun ApplicationCall.resolveKioskRequestContext(kioskService: KioskService): KioskRequestContext? =
    run {
        val principal = principal<JWTPrincipal>()
        if (principal == null) {
            respond(HttpStatusCode.Unauthorized, mapOf("error" to "Token de autenticación requerido"))
            return@run null
        }

        val role = principal.payload.getClaim("role")?.asString()
        val tokenType = principal.payload.getClaim("token_type")?.asString()
        if (role != "KIOSK" && tokenType != "kiosk") {
            respond(HttpStatusCode.Forbidden, mapOf("error" to "Se requiere rol KIOSK"))
            return@run null
        }

        val deviceId = principal.payload.getClaim("device_id")?.asString()
        val countryCode = principal.payload.getClaim("country_code")?.asString()?.uppercase()
        val companyDb = principal.payload.getClaim("admin_db")?.asString()
            ?: principal.payload.getClaim("company_db")?.asString()

        if (deviceId.isNullOrBlank() || countryCode.isNullOrBlank() || companyDb.isNullOrBlank()) {
            respond(HttpStatusCode.BadRequest, mapOf("error" to "Claims de kiosco incompletos en token"))
            return@run null
        }

        val activeDevice = kioskService.verifyDeviceActive(countryCode, companyDb, deviceId)
        if (activeDevice == null || !activeDevice.activo) {
            respond(HttpStatusCode.Unauthorized, mapOf("error" to "Dispositivo kiosco inactivo o revocado"))
            return@run null
        }

        KioskRequestContext(
            countryCode = countryCode,
            companyDb = companyDb,
            deviceId = deviceId,
            deviceName = activeDevice.nombre,
            prefix = activeDevice.prefijoPedido,
            idCaja = activeDevice.idCaja,
            idSucursal = activeDevice.idSucursal,
            idAlmacen = activeDevice.idAlmacen,
            codVendedor = activeDevice.codVendedor,
            idClienteGenerico = activeDevice.idClienteGenerico,
            principal = principal,
        )
    }
