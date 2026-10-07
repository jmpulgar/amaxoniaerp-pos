package com.amaxoniaerp.features.kiosk.data

import com.amaxoniaerp.core.database.dbQuery
import com.amaxoniaerp.features.caja.data.CajaTablePA
import com.amaxoniaerp.features.kiosk.domain.yappy.YappyCredentials
import com.amaxoniaerp.features.kiosk.domain.yappy.YappyDevice
import com.amaxoniaerp.features.kiosk.domain.yappy.YappyKioskConfig
import com.amaxoniaerp.features.kiosk.domain.yappy.YappyQrType
import org.jetbrains.exposed.exceptions.ExposedSQLException
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.slf4j.LoggerFactory
import java.sql.SQLException
import java.util.concurrent.ConcurrentHashMap

/**
 * Lee la configuración Yappy que administra el PHP:
 * - credenciales y endpoints en `parametros_generales.yappy_*`;
 * - unidad de cobro por caja en `caja.yappy_device_id` / `caja.yappy_group_id`, con
 *   respaldo en `parametros_generales.yappy_id_unidad` / `yappy_id_grupo` (Yappy Express).
 *
 * Yappy es una billetera panameña: solo se consulta para empresas PA. Si el esquema del
 * tenant aún no tiene las columnas Yappy, se considera "no configurado".
 */
class KioskYappyConfigRepository(
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val logger = LoggerFactory.getLogger(KioskYappyConfigRepository::class.java)

    suspend fun findConfig(
        database: Database,
        countryCode: String,
        idCaja: String,
    ): YappyKioskConfig? {
        if (!countryCode.equals(YAPPY_COUNTRY, ignoreCase = true)) return null
        return try {
            dbQuery(database) { loadConfig(idCaja) }
        } catch (e: ExposedSQLException) {
            logger.warn("[YAPPY] Esquema sin columnas Yappy o error leyendo configuración: {}", e.javaClass.simpleName)
            null
        }
    }

    /**
     * Tipo de QR configurado en `parametros_generales.yappy_tipo_qr`, o null si la columna
     * opcional no existe en el tenant, está vacía o tiene un valor inválido (el llamador aplica
     * el respaldo `YAPPY_QR_TYPE` → DYN). Nunca lanza.
     *
     * La existencia de la columna se comprueba una vez por base de datos de empresa y se
     * cachea (la ausencia solo [ABSENT_COLUMN_RECHECK_MS], para detectar la columna si se agrega
     * después sin reiniciar); si la lectura falla (p. ej. se eliminó) se invalida la caché.
     */
    suspend fun findQrType(database: Database): YappyQrType? {
        val cached = qrTypeColumnPresence[database]?.takeIf { it.isFresh(clock()) }
        val hasColumn = cached?.present ?: probeQrTypeColumn(database) ?: return null
        if (!hasColumn) return null
        return try {
            val raw =
                dbQuery(database) {
                    KioskYappyQrTypeParametrosTable
                        .select(KioskYappyQrTypeParametrosTable.yappyTipoQr)
                        .orderBy(KioskYappyQrTypeParametrosTable.codEmpresa)
                        .limit(1)
                        .singleOrNull()
                        ?.get(KioskYappyQrTypeParametrosTable.yappyTipoQr)
                }
            YappyQrType.parseOrNull(raw)
        } catch (e: SQLException) {
            qrTypeColumnPresence.remove(database)
            logger.warn("[YAPPY] No se pudo leer parametros_generales.yappy_tipo_qr: {}", e.javaClass.simpleName)
            null
        }
    }

    /**
     * Comprueba con los metadatos de un `SELECT ... WHERE 1 = 0` (no lee filas ni falla por
     * columnas ausentes) si `parametros_generales` tiene `yappy_tipo_qr`. Cachea el resultado;
     * ante un error de base de datos devuelve null sin cachear para reintentar luego.
     */
    private suspend fun probeQrTypeColumn(database: Database): Boolean? =
        try {
            val present =
                dbQuery(database) {
                    TransactionManager.current().exec("SELECT * FROM parametros_generales WHERE 1 = 0") { rs ->
                        val meta = rs.metaData
                        (1..meta.columnCount).any { index ->
                            meta.getColumnLabel(index).equals(QR_TYPE_COLUMN, ignoreCase = true) ||
                                meta.getColumnName(index).equals(QR_TYPE_COLUMN, ignoreCase = true)
                        }
                    } ?: false
                }
            qrTypeColumnPresence[database] = ColumnPresence(present, clock())
            if (!present) {
                logger.info("[YAPPY] parametros_generales sin columna opcional {}; se usa YAPPY_QR_TYPE/DYN", QR_TYPE_COLUMN)
            }
            present
        } catch (e: SQLException) {
            logger.warn("[YAPPY] No se pudo comprobar la columna {}: {}", QR_TYPE_COLUMN, e.javaClass.simpleName)
            null
        }

    private fun loadConfig(idCaja: String): YappyKioskConfig? {
        val params =
            KioskYappyParametrosTable
                .selectAll()
                .orderBy(KioskYappyParametrosTable.codEmpresa)
                .limit(1)
                .singleOrNull() ?: return null

        val apiKey = params[KioskYappyParametrosTable.yappyApiKey].clean()
        val secretKey = params[KioskYappyParametrosTable.yappySecretKey].clean()
        val production = params[KioskYappyParametrosTable.yappyModoProduccion] == 1
        val endpoint =
            if (production) {
                params[KioskYappyParametrosTable.yappyEndpointProd]
            } else {
                params[KioskYappyParametrosTable.yappyEndpointSandbox]
            }.clean()?.trimEnd('/')

        if (apiKey == null || secretKey == null || endpoint.isNullOrBlank()) return null

        val caja =
            CajaTablePA
                .select(CajaTablePA.yappyDeviceId, CajaTablePA.yappyGroupId)
                .where { CajaTablePA.idCaja eq idCaja }
                .limit(1)
                .singleOrNull()
        val cajaDevice = caja?.get(CajaTablePA.yappyDeviceId).clean()
        val cajaGroup = caja?.get(CajaTablePA.yappyGroupId).clean()

        val device =
            if (cajaDevice != null && cajaGroup != null) {
                YappyDevice(deviceId = cajaDevice, groupId = cajaGroup)
            } else {
                val fallbackDevice = params[KioskYappyParametrosTable.yappyIdUnidad].clean()
                val fallbackGroup = params[KioskYappyParametrosTable.yappyIdGrupo].clean()
                if (fallbackDevice == null || fallbackGroup == null) return null
                YappyDevice(deviceId = fallbackDevice, groupId = fallbackGroup)
            }

        return YappyKioskConfig(
            credentials = YappyCredentials(apiKey = apiKey, secretKey = secretKey, baseUrl = endpoint),
            device = device,
        )
    }

    private fun String?.clean(): String? = this?.trim()?.takeIf { it.isNotEmpty() }

    /** Presencia de `parametros_generales.yappy_tipo_qr` por base de datos de empresa. */
    private val qrTypeColumnPresence = ConcurrentHashMap<Database, ColumnPresence>()

    private data class ColumnPresence(
        val present: Boolean,
        val checkedAtMs: Long,
    ) {
        fun isFresh(nowMs: Long): Boolean = present || nowMs - checkedAtMs < ABSENT_COLUMN_RECHECK_MS
    }

    private companion object {
        const val YAPPY_COUNTRY = "PA"
        const val QR_TYPE_COLUMN = "yappy_tipo_qr"
        const val ABSENT_COLUMN_RECHECK_MS = 10 * 60 * 1000L
    }
}
