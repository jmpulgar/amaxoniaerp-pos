package com.amaxoniaerp.core.database

import com.amaxoniaerp.loadConfigValue
import com.amaxoniaerp.loadDotEnv
import io.ktor.server.application.Application
import io.ktor.server.application.log
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.transactions.transaction

public data class DbConfig(
    val url: String,
    val user: String,
    val password: String,
    val driver: String?,
    val poolSize: Int?,
)

public fun Application.loadDbConfig(): DbConfig {
    val dotenv = loadDotEnv()

    val url =
        loadConfigValue("DB_CONFIG_URL", "db.config.url", dotenv)
            ?: error("Missing DB_CONFIG_URL or db.config.url")
    val user =
        loadConfigValue("DB_CONFIG_USER", "db.config.user", dotenv)
            ?: error("Missing DB_CONFIG_USER or db.config.user")
    val password = loadConfigValue("DB_CONFIG_PASS", "db.config.password", dotenv) ?: ""
    val poolSize = loadConfigValue("DB_CONFIG_POOL_SIZE", "db.config.poolSize", dotenv)?.toIntOrNull()
    val driver = loadConfigValue("DB_CONFIG_DRIVER", "db.config.driver", dotenv) ?: inferDriver(url)

    return DbConfig(
        url = url,
        user = user,
        password = password,
        driver = driver,
        poolSize = poolSize,
    )
}

public fun connectDatabase(dbConfig: DbConfig): Database =
    if (dbConfig.driver.isNullOrBlank()) {
        Database.connect(
            url = dbConfig.url,
            user = dbConfig.user,
            password = dbConfig.password,
        )
    } else {
        Database.connect(
            url = dbConfig.url,
            driver = dbConfig.driver,
            user = dbConfig.user,
            password = dbConfig.password,
        )
    }

public fun verifyDatabaseConnection(
    database: Database,
    log: org.slf4j.Logger,
) {
    try {
        transaction(database) {
            exec("SELECT 1")
        }
        log.info("Database connection OK")
    } catch (ex: Exception) {
        log.error("Database connection failed: ${ex.message}", ex)
        throw ex
    }
}

private fun inferDriver(url: String): String? =
    when {
        url.startsWith("jdbc:mysql:") -> "com.mysql.cj.jdbc.Driver"
        url.startsWith("jdbc:h2:") -> "org.h2.Driver"
        else -> null
    }
