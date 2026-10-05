package com.amaxoniaerp.features.kiosk.application

import java.util.concurrent.ConcurrentHashMap
import java.time.Instant

/**
 * Limitador de intentos fallidos de desbloqueo de kiosco.
 * Regla: Máximo 5 intentos fallidos en una ventana de 5 minutos por dispositivo.
 */
class UnlockRateLimiter(
    private val maxAttempts: Int = 5,
    private val windowSeconds: Long = 300L,
) {
    private data class AttemptWindow(
        var attempts: Int,
        var windowStart: Instant,
    )

    private val windows = ConcurrentHashMap<String, AttemptWindow>()

    /**
     * Retorna true si el dispositivo está bloqueado por demasiados intentos fallidos.
     */
    fun isRateLimited(deviceId: String): Boolean {
        val now = Instant.now()
        val window = windows[deviceId] ?: return false
        if (now.isAfter(window.windowStart.plusSeconds(windowSeconds))) {
            windows.remove(deviceId)
            return false
        }
        return window.attempts >= maxAttempts
    }

    /**
     * Registra un intento fallido para el dispositivo.
     */
    fun recordFailure(deviceId: String) {
        val now = Instant.now()
        windows.compute(deviceId) { _, current ->
            if (current == null || now.isAfter(current.windowStart.plusSeconds(windowSeconds))) {
                AttemptWindow(attempts = 1, windowStart = now)
            } else {
                current.attempts++
                current
            }
        }
    }

    /**
     * Limpia los intentos fallidos al tener éxito.
     */
    fun recordSuccess(deviceId: String) {
        windows.remove(deviceId)
    }

    fun remainingAttempts(deviceId: String): Int {
        val now = Instant.now()
        val window = windows[deviceId] ?: return maxAttempts
        if (now.isAfter(window.windowStart.plusSeconds(windowSeconds))) {
            return maxAttempts
        }
        return (maxAttempts - window.attempts).coerceAtLeast(0)
    }
}
