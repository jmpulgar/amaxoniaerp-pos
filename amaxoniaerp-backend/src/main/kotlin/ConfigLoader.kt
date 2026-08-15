package com.amaxoniaerp

import io.ktor.server.application.*
import io.ktor.server.config.*
import java.io.File

internal fun Application.loadConfigValue(
    key: String,
    path: String,
    dotenv: Map<String, String>,
): String? {
    val configValue =
        try {
            environment.config.propertyOrNull(path)?.getString()
        } catch (_: ApplicationConfigurationException) {
            null
        }

    return dotenv[key]
        ?: System.getenv(key)
        ?: configValue
}

internal fun loadDotEnv(): Map<String, String> {
    val envFile = findDotEnvFile() ?: return emptyMap()
    return loadDotEnv(envFile)
}

/**
 * Lookup de configuración tipo 12-factor: variables de entorno tienen prioridad sobre .env.
 * Uso: en producción se usan env vars; en desarrollo se puede usar .env sin exportar.
 */
fun getEnvLookup(): (String) -> String? {
    val dotenv = loadDotEnv()
    return { key -> System.getenv(key)?.takeIf { it.isNotBlank() } ?: dotenv[key]?.takeIf { it.isNotBlank() } }
}

data class JwtConfig(
    val domain: String,
    val audience: String,
    val secret: String,
    val realm: String?,
)

fun Application.loadJwtConfig(): JwtConfig {
    val dotenv = loadDotEnv()
    val domain =
        loadConfigValue("JWT_DOMAIN", "jwt.domain", dotenv)
            ?: error("Missing JWT_DOMAIN or jwt.domain")
    val audience =
        loadConfigValue("JWT_AUDIENCE", "jwt.audience", dotenv)
            ?: error("Missing JWT_AUDIENCE or jwt.audience")
    val secret =
        loadConfigValue("JWT_SECRET", "jwt.secret", dotenv)
            ?: error("Missing JWT_SECRET or jwt.secret")
    val realm = loadConfigValue("JWT_REALM", "jwt.realm", dotenv)

    return JwtConfig(
        domain = domain,
        audience = audience,
        secret = secret,
        realm = realm,
    )
}

private fun loadDotEnv(file: File): Map<String, String> {
    if (!file.exists()) return emptyMap()

    return file
        .readLines()
        .mapNotNull { line ->
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("#")) return@mapNotNull null
            val separatorIndex = trimmed.indexOf('=')
            if (separatorIndex <= 0) return@mapNotNull null

            val key = trimmed.substring(0, separatorIndex).trim()
            var value = trimmed.substring(separatorIndex + 1).trim()
            if (
                (value.startsWith("\"") && value.endsWith("\"")) ||
                (value.startsWith("'") && value.endsWith("'"))
            ) {
                value = value.substring(1, value.length - 1)
            }
            key to value
        }.toMap()
}

private fun findDotEnvFile(): File? {
    val cwd = File(System.getProperty("user.dir"))
    val direct = File(".env")
    if (direct.exists()) return direct
    val development = File(".env.development")
    if (development.exists()) return development

    var current: File? = cwd
    repeat(4) {
        val candidate = File(current, ".env")
        if (candidate.exists()) return candidate
        val devCandidate = File(current, ".env.development")
        if (devCandidate.exists()) return devCandidate
        current = current?.parentFile
    }
    return null
}
