package com.amaxoniaerp.features.caja.data

import com.amaxoniaerp.features.caja.application.CajaSessionStore
import com.amaxoniaerp.features.caja.domain.AperturaRequest
import com.amaxoniaerp.features.caja.domain.CajaCierreSaveRequest
import com.amaxoniaerp.features.caja.domain.CajaSecuencia
import com.amaxoniaerp.features.caja.domain.CajaSecuenciaData
import com.amaxoniaerp.features.caja.domain.CajaSecuenciaGuard

/**
 * Adaptador Exposed del puerto de sesión de caja: ejecuta las primitivas de
 * [CajaSessionPrimitives] y lectores existentes dentro de la transacción
 * activa abierta por el workflow. No abre transacciones propias.
 */
class ExposedCajaSessionStore : CajaSessionStore {
    override fun findOpenSecuencias(idCaja: String): List<CajaSecuencia> = findOpenSecuenciaRows(idCaja).map(::mapOpenSecuenciaRow)

    override fun findSecuenciaGuard(idSecuencia: String): CajaSecuenciaGuard? = readSessionGuard(idSecuencia)

    override fun countFacturasTemporalesPendientes(
        countryCode: String,
        idSecuencia: String,
    ): Int = countFacturasTemporales(countryCode, idSecuencia)

    override fun writeCierre(
        request: CajaCierreSaveRequest,
        now: java.time.LocalDateTime,
        serieSucursal: String,
    ) {
        persistCierreRecord(request, now)
        rewriteCierreDetallesRecord(request, serieSucursal)
    }

    override fun readSecuenciaData(
        countryCode: String,
        idSecuencia: String,
        verifyFacturasTemporales: Boolean,
    ): CajaSecuenciaData = readCajaSecuenciaData(countryCode, idSecuencia, verifyFacturasTemporales)

    override fun nextSecuenciaCode(idCaja: String): String = resolveNextSecuenciaCode(idCaja)

    override fun insertApertura(
        newId: String,
        request: AperturaRequest,
        username: String,
        now: java.time.LocalDateTime,
        nextSequence: String,
    ) {
        insertAperturaRecord(newId, request, username, now, nextSequence)
    }
}
