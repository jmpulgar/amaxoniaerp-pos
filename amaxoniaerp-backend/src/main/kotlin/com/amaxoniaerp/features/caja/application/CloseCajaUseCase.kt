package com.amaxoniaerp.features.caja.application

import com.amaxoniaerp.features.caja.data.CajaRepository
import com.amaxoniaerp.features.caja.domain.CajaCierreSaveRequest
import com.amaxoniaerp.features.caja.domain.CajaCierreSaveResponse
import com.amaxoniaerp.features.caja.domain.buildAutoCloseRequest
import org.jetbrains.exposed.sql.Database

/**
 * Workflow de cierre de caja (TASK-043): valida el estado de la secuencia
 * (existencia, no cerrada, facturas temporales) dentro de la transacción y
 * persiste el cierre con sus detalles. `autoClose` cierra con los montos
 * calculados y diferencias en cero; lo usa [OpenCajaUseCase] al reabrir.
 */
class CloseCajaUseCase(
    private val repository: CajaRepository,
) {
    suspend fun close(
        database: Database,
        countryCode: String,
        request: CajaCierreSaveRequest,
    ): Result<CajaCierreSaveResponse> =
        repository.persistCierre(
            database = database,
            countryCode = countryCode,
            request = request,
            validateFacturasTemporales = true,
        )

    suspend fun autoClose(
        database: Database,
        countryCode: String,
        idSecuencia: String,
    ): Result<Unit> =
        repository
            .getCajaSecuenciaData(database, countryCode, idSecuencia, verifyFacturasTemporales = false)
            .fold(
                onSuccess = { data ->
                    repository
                        .persistCierre(
                            database = database,
                            countryCode = countryCode,
                            request = buildAutoCloseRequest(data),
                            validateFacturasTemporales = false,
                        ).map { Unit }
                },
                onFailure = { error ->
                    Result.failure(error)
                },
            )
}
