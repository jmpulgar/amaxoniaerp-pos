package com.amaxoniaerp

import com.amaxoniaerp.core.database.DatabaseManager
import io.ktor.server.application.*

/**
 * Configuración de bases de datos (Multi-Tenant).
 * Inyecta lookup de config (env vars > .env) según 12-factor.
 */
fun Application.configureDatabases() {
    DatabaseManager.init(log, getEnvLookup())
}
