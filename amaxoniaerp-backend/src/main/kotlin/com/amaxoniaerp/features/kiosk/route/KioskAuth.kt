package com.amaxoniaerp.features.kiosk.route

import com.amaxoniaerp.core.tenant.resolveCompanyRequestContext
import com.amaxoniaerp.core.tenant.userIdOrNull
import com.amaxoniaerp.features.kiosk.application.KioskCajaSelection
import com.amaxoniaerp.features.kiosk.application.KioskService
import com.amaxoniaerp.features.kiosk.domain.KioskRequestContext
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respond

/** Caja del ERP sobre la que vende el kiosco (elegida en el equipo tras el login del POS). */
const val KIOSK_CAJA_HEADER = "X-Kiosk-Caja"

/** Prefijo de los números de pedido del kiosco (1 a 5 caracteres A-Z / 0-9). */
const val KIOSK_PREFIX_HEADER = "X-Kiosk-Prefix"

private val KIOSK_PREFIX_PATTERN = Regex("^[A-Z0-9]{1,5}$")

/**
 * Resuelve el contexto del kiosco: el mismo token de empresa del POS (seam
 * [resolveCompanyRequestContext]) más los headers [KIOSK_CAJA_HEADER] y [KIOSK_PREFIX_HEADER].
 * Responde 401/403/400 y devuelve null cuando falta algo o la caja no es válida.
 */
suspend fun ApplicationCall.resolveKioskRequestContext(kioskService: KioskService): KioskRequestContext? =
    run {
        val company = resolveCompanyRequestContext() ?: return@run null

        val idCaja = request.headers[KIOSK_CAJA_HEADER]?.trim()
        if (idCaja.isNullOrEmpty()) {
            respond(HttpStatusCode.BadRequest, mapOf("error" to "Falta el header $KIOSK_CAJA_HEADER con la caja del kiosco"))
            return@run null
        }

        val rawPrefix = request.headers[KIOSK_PREFIX_HEADER]?.trim()
        if (rawPrefix.isNullOrEmpty()) {
            respond(
                HttpStatusCode.BadRequest,
                mapOf("error" to "Falta el header $KIOSK_PREFIX_HEADER con el prefijo de pedidos del kiosco"),
            )
            return@run null
        }
        val prefix = rawPrefix.uppercase()
        if (!KIOSK_PREFIX_PATTERN.matches(prefix)) {
            respond(
                HttpStatusCode.BadRequest,
                mapOf("error" to "$KIOSK_PREFIX_HEADER inválido: debe tener de 1 a 5 letras (A-Z) o números"),
            )
            return@run null
        }

        val context =
            kioskService.resolveContext(
                KioskCajaSelection(
                    countryCode = company.countryCode.uppercase(),
                    companyDb = company.adminDb,
                    userId = company.userIdOrNull(),
                    idCaja = idCaja,
                    prefix = prefix,
                ),
            )
        if (context == null) {
            respond(HttpStatusCode.BadRequest, mapOf("error" to "Caja del kiosco no válida"))
            return@run null
        }
        context
    }
