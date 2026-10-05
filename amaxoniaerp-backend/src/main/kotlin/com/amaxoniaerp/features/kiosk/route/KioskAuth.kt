package com.amaxoniaerp.features.kiosk.route

import com.amaxoniaerp.core.tenant.extractKioskTokenClaims
import com.amaxoniaerp.core.tenant.hasKioskPrincipal
import com.amaxoniaerp.features.kiosk.application.KioskService
import com.amaxoniaerp.features.kiosk.domain.KioskRequestContext
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respond

suspend fun ApplicationCall.resolveKioskRequestContext(kioskService: KioskService): KioskRequestContext? =
    run {
        if (!hasKioskPrincipal()) {
            respond(HttpStatusCode.Unauthorized, mapOf("error" to "Token de autenticación requerido"))
            return@run null
        }

        val claims = extractKioskTokenClaims()
        if (claims == null) {
            respond(HttpStatusCode.Unauthorized, mapOf("error" to "Token de autenticación requerido"))
            return@run null
        }

        if (claims.role != "KIOSK" && claims.tokenType != "kiosk") {
            respond(HttpStatusCode.Forbidden, mapOf("error" to "Se requiere rol KIOSK"))
            return@run null
        }

        val deviceId = claims.deviceId
        val countryCode = claims.countryCode
        val companyDb = claims.companyDb

        if (deviceId.isBlank() || countryCode.isBlank() || companyDb.isBlank()) {
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
        )
    }
