package com.amaxoniaerp.core.database

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.jetbrains.exposed.sql.Database
import org.slf4j.Logger
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * Parámetros JDBC comunes para MySQL (legacy: fechas 0000-00-00 convertidas a null), **sin** `serverTimezone`.
 *
 * `serverTimezone` se añade por país en [mysqlJdbcQueryString]: con `serverTimezone=UTC` el driver MySQL 8
 * convertía `LocalDateTime` de negocio (p. ej. 19:40 Caracas) al persistir en `DATETIME` y guardaba 23:40 UTC.
 * Debe coincidir con la zona usada en [com.amaxoniaerp.core.time.BusinessClock] para ese mismo `countryCode`.
 */
private const val MYSQL_JDBC_PARAMS_WITHOUT_TZ =
    "useSSL=false&characterEncoding=UTF-8&allowPublicKeyRetrieval=true&zeroDateTimeBehavior=CONVERT_TO_NULL"

/** Misma convención IANA que [com.amaxoniaerp.core.time.BusinessClock] (VE/PA). */
private fun jdbcServerTimezoneForCountry(countryCode: String): String =
    when (countryCode.uppercase()) {
        "VE" -> "America/Caracas"
        "PA" -> "America/Panama"
        else -> "UTC"
    }

private fun mysqlJdbcQueryString(countryCode: String): String {
    val tz = URLEncoder.encode(jdbcServerTimezoneForCountry(countryCode), StandardCharsets.UTF_8)
    return "$MYSQL_JDBC_PARAMS_WITHOUT_TZ&serverTimezone=$tz"
}

/**
 * Configuración de base de datos específica por país.
 * Valores cargados vía función inyectada (env vars + .env en desarrollo).
 */
data class CountryDbConfig(
    val countryCode: String,
    val host: String,
    val port: Int,
    val user: String,
    val password: String,
    val configDbName: String,
    val displayName: String
) {
    fun buildConfigJdbcUrl(): String =
        "jdbc:mysql://$host:$port/$configDbName?${mysqlJdbcQueryString(countryCode)}"

    fun buildCompanyJdbcUrl(companyDbName: String): String =
        "jdbc:mysql://$host:$port/$companyDbName?${mysqlJdbcQueryString(countryCode)}"
}

/**
 * Gestor de conexiones Multi-Tenant (Two-Tier).
 * NO mantiene una conexión maestra global al inicio.
 * Todas las conexiones son Lazy y bajo demanda.
 */
object DatabaseManager {

    private val logger by lazy { org.slf4j.LoggerFactory.getLogger(DatabaseManager::class.java) }

    /** Lookup inyectado: (key) -> value. Prioridad env vars (12-factor), luego .env en desarrollo. */
    @Volatile
    private var envLookup: ((String) -> String?)? = null

    /** Países soportados: solo Venezuela (VE) y Panamá (PA). */
    val countryConfigs: Map<String, CountryDbConfig> by lazy {
        val getEnv = envLookup ?: error("DatabaseManager.init(log, getEnv) debe llamarse antes de usar countryConfigs")
        mapOf(
            "VE" to loadCountryConfig(getEnv, "VE", "Venezuela"),
            "PA" to loadCountryConfig(getEnv, "PA", "Panamá")
        )
    }

    // Cache de DataSources
    private val configDataSources = mutableMapOf<String, HikariDataSource>()
    private val companyDataSources = mutableMapOf<String, HikariDataSource>()

    /**
     * Carga la configuración de un país desde el lookup inyectado (env + .env).
     */
    private fun loadCountryConfig(getEnv: (String) -> String?, countryCode: String, displayName: String): CountryDbConfig {
        val prefix = "DB_${countryCode}_"

        return CountryDbConfig(
            countryCode = countryCode,
            host = getEnv("${prefix}HOST") ?: getDefaultHost(countryCode),
            port = getEnv("${prefix}PORT")?.toIntOrNull() ?: 3306,
            user = getEnv("${prefix}USER") ?: "root",
            password = getEnv("${prefix}PASS") ?: "",
            configDbName = getEnv("${prefix}CONF_DB") ?: getDefaultConfigDb(countryCode),
            displayName = displayName
        )
    }

    private fun getDefaultHost(countryCode: String): String = when (countryCode) {
        "VE" -> "listoerp.app"
        "PA" -> "administrativo.amaxoniaerp.com"
        else -> "localhost"
    }

    private fun getDefaultConfigDb(countryCode: String): String = when (countryCode) {
        "VE" -> "facturacion_ve_conf"
        "PA" -> "selectra_conf_pyme"
        else -> "amaxonia_config"
    }

    /**
     * Inicialización (inyección de config + logging).
     * getEnv: lookup (key) -> value; prioridad env vars, luego .env (12-factor).
     */
    fun init(log: Logger, getEnv: (String) -> String?) {
        envLookup = getEnv
        log.info("DatabaseManager inicializado. Países configurados: ${countryConfigs.keys}")
        countryConfigs.forEach { (code, config) ->
            log.info("  $code -> ${config.host} / ${config.configDbName}")
        }
    }

    /**
     * Obtiene la base de datos de configuración para un país específico (Nivel 1).
     * Crea el pool de conexiones si no existe.
     */
    fun getConfigDatabase(countryCode: String): Database {
        val upperCode = countryCode.uppercase()
        val config = countryConfigs[upperCode]
            ?: throw IllegalArgumentException("País no soportado: $countryCode")

        return synchronized(configDataSources) {
            val dataSource = configDataSources.getOrPut(upperCode) {
                logger.info("Creando pool de conexión para CONFIG DB de $upperCode (${config.host})")
                createDataSource(config.buildConfigJdbcUrl(), config.user, config.password)
            }
            Database.connect(dataSource)
        }
    }

    /**
     * Legacy support: Connect to company database assuming default country (VE).
     * This is needed for legacy routes that haven't been migrated to multi-tenant yet.
     */
    fun connectToCompanyDb(companyDbName: String): Database {
        return connectToCompanyDb("VE", companyDbName)
    }

    /**
     * Conecta a la base de datos administrativa de una empresa específica (Nivel 2).
     */
    fun connectToCompanyDb(countryCode: String, companyDbName: String): Database {
        val upperCode = countryCode.uppercase()
        val cacheKey = "$upperCode:$companyDbName"

        val config = countryConfigs[upperCode]
            ?: throw IllegalArgumentException("País no soportado: $countryCode")

        return synchronized(companyDataSources) {
            val dataSource = companyDataSources.getOrPut(cacheKey) {
                logger.info("Creando pool de conexión para COMPANY DB $companyDbName en $upperCode")
                createDataSource(
                    config.buildCompanyJdbcUrl(companyDbName),
                    config.user,
                    config.password
                )
            }
            Database.connect(dataSource)
        }
    }

    /**
     * Crea un DataSource HikariCP configurado.
     */
    private fun createDataSource(jdbcUrl: String, username: String, password: String): HikariDataSource {
        val hikariConfig = HikariConfig().apply {
            this.jdbcUrl = jdbcUrl
            this.username = username
            this.password = password
            driverClassName = "com.mysql.cj.jdbc.Driver"
            maximumPoolSize = 10
            minimumIdle = 2
            idleTimeout = 300000  // 5 minutos
            connectionTimeout = 20000  // 20 segundos
            maxLifetime = 1200000  // 20 minutos
            isAutoCommit = false
            transactionIsolation = "TRANSACTION_READ_COMMITTED"
            addDataSourceProperty("cachePrepStmts", "true")
            addDataSourceProperty("prepStmtCacheSize", "250")
            addDataSourceProperty("prepStmtCacheSqlLimit", "2048")
            // Propiedades MySQL para estabilidad
            addDataSourceProperty("useServerPrepStmts", "true")
            addDataSourceProperty("useLocalSessionState", "true")
            addDataSourceProperty("rewriteBatchedStatements", "true")
            addDataSourceProperty("cacheResultSetMetadata", "true")
            addDataSourceProperty("cacheServerConfiguration", "true")
            addDataSourceProperty("elideSetAutoCommits", "true")
            addDataSourceProperty("maintainTimeStats", "false")
            
            validate()
        }
        return HikariDataSource(hikariConfig)
    }
    
    // Legacy support: Master DB property that throws if accessed before init
    val masterDb: Database
        get() = throw UnsupportedOperationException("masterDb global is deprecated. Use getConfigDatabase(countryCode)")
}
