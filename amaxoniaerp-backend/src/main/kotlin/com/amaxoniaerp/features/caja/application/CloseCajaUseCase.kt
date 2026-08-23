package com.amaxoniaerp.features.caja.application

import com.amaxoniaerp.features.caja.data.CajaRepository
import com.amaxoniaerp.features.caja.domain.buildAutoCloseRequest
import org.jetbrains.exposed.sql.Database

/**
 * Capacidad de auto-close transitoria mientras la apertura vive en
 * [OpenCajaUseCase]: cierra con los montos calculados y diferencias en cero.
 * El cierre público de sesión vive en [CajaSessionWorkflow].
 */
class CloseCajaUseCase(
    private val repository: CajaRepository,
) {
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
