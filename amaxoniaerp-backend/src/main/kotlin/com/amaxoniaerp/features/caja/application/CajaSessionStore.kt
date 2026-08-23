package com.amaxoniaerp.features.caja.application

import com.amaxoniaerp.features.caja.domain.AperturaRequest
import com.amaxoniaerp.features.caja.domain.CajaCierreSaveRequest
import com.amaxoniaerp.features.caja.domain.CajaSecuencia
import com.amaxoniaerp.features.caja.domain.CajaSecuenciaData
import com.amaxoniaerp.features.caja.domain.CajaSecuenciaGuard

/**
 * Puerto de persistencia del ciclo de vida de la sesión de caja (apertura →
 * cierre). Cada operación tiene alcance de transacción: asume que ya existe
 * una transacción activa —la que abre [CajaSessionWorkflow]— y no abre
 * ninguna propia.
 */
interface CajaSessionStore {
    /** Secuencias abiertas de la caja, más reciente primero. */
    fun findOpenSecuencias(idCaja: String): List<CajaSecuencia>

    /** Guarda de la secuencia a cerrar; null si no existe. */
    fun findSecuenciaGuard(idSecuencia: String): CajaSecuenciaGuard?

    /** Facturas temporales pendientes asociadas a la secuencia. */
    fun countFacturasTemporalesPendientes(
        countryCode: String,
        idSecuencia: String,
    ): Int

    /** Persiste el cierre con reescritura de sus detalles. */
    fun writeCierre(
        request: CajaCierreSaveRequest,
        now: java.time.LocalDateTime,
        serieSucursal: String,
    )

    /** Datos completos de la secuencia; falla si no existe. */
    fun readSecuenciaData(
        countryCode: String,
        idSecuencia: String,
        verifyFacturasTemporales: Boolean,
    ): CajaSecuenciaData

    /** Siguiente código de secuencia de seis dígitos para la caja. */
    fun nextSecuenciaCode(idCaja: String): String

    /** Inserta la apertura con su detalle. */
    fun insertApertura(
        newId: String,
        request: AperturaRequest,
        username: String,
        now: java.time.LocalDateTime,
        nextSequence: String,
    )
}
