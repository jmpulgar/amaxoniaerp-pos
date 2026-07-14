package com.amaxonia.pos.core.logging

import android.util.Log
import com.amaxonia.pos.BuildConfig

/**
 * Debug-only application logging with defense-in-depth redaction.
 *
 * Callers must still avoid passing credentials or personal information. The redactor exists to
 * contain accidental key/value logging; release builds emit nothing through this API.
 */
object SafeLog {
    private val sensitiveAssignment =
        Regex(
            pattern =
                "(?i)(token|password|authorization|gatewayKey|customerCI|commerceRif|rif|" +
                    "rawResponse|resultJson|responseText|cmd)\\s*[:=]\\s*([^\\s,}]+)",
        )
    private val bearerToken = Regex("(?i)Bearer\\s+[A-Za-z0-9._~+/=-]+")
    private val jwt = Regex("[A-Za-z0-9_-]{8,}\\.[A-Za-z0-9_-]{8,}\\.[A-Za-z0-9_-]{8,}")

    fun d(
        tag: String,
        message: String,
    ) {
        if (BuildConfig.DEBUG) Log.d(tag, redact(message))
    }

    fun i(
        tag: String,
        message: String,
    ) {
        if (BuildConfig.DEBUG) Log.i(tag, redact(message))
    }

    fun w(
        tag: String,
        message: String,
    ) {
        if (BuildConfig.DEBUG) Log.w(tag, redact(message))
    }

    fun e(
        tag: String,
        message: String,
        throwable: Throwable? = null,
    ) {
        if (!BuildConfig.DEBUG) return
        val cause =
            throwable
                ?.javaClass
                ?.simpleName
                ?.let { " cause=$it" }
                .orEmpty()
        Log.e(tag, redact(message) + cause)
    }

    internal fun redact(message: String): String =
        message
            .replace(bearerToken, "Bearer [REDACTED]")
            .replace(jwt, "[REDACTED_JWT]")
            .replace(sensitiveAssignment) { match -> "${match.groupValues[1]}=[REDACTED]" }
}
