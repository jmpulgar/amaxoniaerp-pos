package com.amaxoniaerp.features.kiosk.application

import com.amaxoniaerp.features.kiosk.domain.yappy.YappyGateway
import com.amaxoniaerp.features.kiosk.domain.yappy.YappyKioskConfig
import com.amaxoniaerp.features.kiosk.domain.yappy.YappySessionExpiredException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap

/** Una sesión Yappy por caja de cada empresa. */
data class YappySessionKey(
    val companyDb: String,
    val idCaja: String,
)

/**
 * Cachea en memoria el token de sesión Yappy por (empresa, caja), lo abre de forma perezosa
 * y, si Yappy lo rechaza (401/403), lo reabre una sola vez y reintenta la operación.
 *
 * El token no se persiste: tras reiniciar el backend se abre una sesión nueva al primer uso.
 * La apertura está serializada por clave para no abrir sesiones duplicadas en paralelo.
 */
class YappySessionManager(
    private val gateway: YappyGateway,
) {
    private val tokens = ConcurrentHashMap<YappySessionKey, String>()
    private val locks = ConcurrentHashMap<YappySessionKey, Mutex>()

    suspend fun <T> withSession(
        key: YappySessionKey,
        config: YappyKioskConfig,
        operation: suspend (sessionToken: String) -> T,
    ): T {
        val token = currentOrOpen(key, config)
        return try {
            operation(token)
        } catch (_: YappySessionExpiredException) {
            val refreshed = reopen(key, config, staleToken = token)
            operation(refreshed)
        }
    }

    private suspend fun currentOrOpen(
        key: YappySessionKey,
        config: YappyKioskConfig,
    ): String {
        tokens[key]?.let { return it }
        return lockFor(key).withLock {
            tokens[key] ?: open(key, config)
        }
    }

    private suspend fun reopen(
        key: YappySessionKey,
        config: YappyKioskConfig,
        staleToken: String,
    ): String =
        lockFor(key).withLock {
            val current = tokens[key]
            if (current != null && current != staleToken) {
                current
            } else {
                tokens.remove(key)
                open(key, config)
            }
        }

    private suspend fun open(
        key: YappySessionKey,
        config: YappyKioskConfig,
    ): String {
        val token = gateway.openSession(config.credentials, config.device)
        tokens[key] = token
        return token
    }

    private fun lockFor(key: YappySessionKey): Mutex = locks.computeIfAbsent(key) { Mutex() }
}
