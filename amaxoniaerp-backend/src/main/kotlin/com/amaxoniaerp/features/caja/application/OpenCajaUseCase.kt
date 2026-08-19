package com.amaxoniaerp.features.caja.application

import com.amaxoniaerp.core.time.BusinessClock
import com.amaxoniaerp.features.caja.data.CajaRepository
import com.amaxoniaerp.features.caja.domain.AperturaRequest
import com.amaxoniaerp.features.caja.domain.CajaSecuencia
import org.jetbrains.exposed.sql.Database
import org.slf4j.LoggerFactory
import java.time.ZoneId

/**
 * Workflow de apertura de caja (TASK-043): si la caja tiene una secuencia
 * abierta se cierra automáticamente antes de abrir la siguiente; la nueva
 * secuencia se numera a partir del máximo existente. Los GETs de caja no
 * pertenecen a este workflow y permanecen como queries del repositorio.
 */
class OpenCajaUseCase(
    private val repository: CajaRepository,
    private val closeCaja: CloseCajaUseCase,
) {
    private val log = LoggerFactory.getLogger(OpenCajaUseCase::class.java)

    suspend fun execute(
        database: Database,
        countryCode: String,
        dbName: String,
        request: AperturaRequest,
        username: String,
    ): Result<CajaSecuencia> {
        val currentOpen = repository.getCajaStatus(database, dbName, request.idCaja)
        if (currentOpen != null) {
            closeCaja.autoClose(database, countryCode, currentOpen.idCajaSecuencia).fold(
                onSuccess = { },
                onFailure = { error ->
                    log.warn(
                        "No se pudo cerrar automaticamente la secuencia abierta. idSecuencia={}",
                        currentOpen.idCajaSecuencia,
                        error,
                    )
                    return Result.failure(
                        IllegalStateException(
                            "No se pudo cerrar automaticamente la secuencia abierta",
                            error,
                        ),
                    )
                },
            )
        }

        log.info(
            "openCaja reloj negocio: countryCode={} zone={} fechaAperturaLocal={} jvmDefaultZone={}",
            countryCode,
            BusinessClock.zoneForCountry(countryCode),
            BusinessClock.nowForCountry(countryCode),
            ZoneId.systemDefault(),
        )

        return repository
            .recordApertura(database, countryCode, request, username)
            .mapCatching {
                repository.getCajaStatus(database, dbName, request.idCaja)
                    ?: error("Failed to retrieve open caja.")
            }
    }
}
