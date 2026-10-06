package com.amaxoniaerp.features.kiosk.application

import com.amaxoniaerp.features.kiosk.data.KioskCajaRepository
import com.amaxoniaerp.features.kiosk.data.KioskCatalogRepository
import com.amaxoniaerp.features.kiosk.data.KioskComboRepository
import com.amaxoniaerp.features.kiosk.data.KioskConfigRepository
import com.amaxoniaerp.features.kiosk.data.KioskCustomerRepository
import com.amaxoniaerp.features.kiosk.data.KioskOrderRepository
import com.amaxoniaerp.features.kiosk.data.KioskSchemaInspector
import com.amaxoniaerp.features.kiosk.data.KioskSettingsRepository
import com.amaxoniaerp.features.kiosk.domain.KioskCatalogResponse
import com.amaxoniaerp.features.kiosk.domain.KioskConfigResponse
import com.amaxoniaerp.features.kiosk.domain.KioskPayResponse
import com.amaxoniaerp.features.kiosk.domain.KioskPaymentMethod
import com.amaxoniaerp.features.kiosk.domain.KioskPaymentRequest
import com.amaxoniaerp.features.kiosk.domain.KioskQuoteRequest
import com.amaxoniaerp.features.kiosk.domain.KioskQuoteResponse
import com.amaxoniaerp.features.kiosk.domain.KioskRequestContext
import com.amaxoniaerp.features.kiosk.domain.KioskUnlockRequest
import com.amaxoniaerp.features.kiosk.domain.KioskYappyQrResponse
import com.amaxoniaerp.features.kiosk.domain.KioskYappyStatusResponse
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.Database
import org.mindrot.jbcrypt.BCrypt
import java.security.MessageDigest

class KioskRateLimitException(
    message: String,
) : RuntimeException(message)

class KioskAuthenticationException(
    message: String,
) : RuntimeException(message)

/** El tenant no tiene las tablas `kiosco_pedido*` (falta la migración del administrativo). */
class KioskNotEnabledException : RuntimeException("El kiosco no está habilitado en esta empresa (falta migración)")

/** Caja y prefijo que el kiosco envía en `X-Kiosk-Caja` / `X-Kiosk-Prefix`. */
data class KioskCajaSelection(
    val countryCode: String,
    val companyDb: String,
    val userId: Int?,
    val idCaja: String,
    val prefix: String,
)

/**
 * Operaciones del kiosco de autoservicio. El kiosco usa el mismo login que el POS (token de
 * empresa) y vende sobre una caja del ERP elegida en el equipo; ver [resolveContext].
 */
class KioskService(
    private val unlockRateLimiter: UnlockRateLimiter,
    private val databaseResolver: (countryCode: String, companyDb: String) -> Database,
    private val schemaInspector: KioskSchemaInspector = KioskSchemaInspector(),
    private val kioskSettingsRepository: KioskSettingsRepository = KioskSettingsRepository(schemaInspector),
    private val kioskConfigRepository: KioskConfigRepository = KioskConfigRepository(kioskSettingsRepository),
    private val kioskCatalogRepository: KioskCatalogRepository = KioskCatalogRepository(KioskComboRepository(schemaInspector)),
    private val kioskOrderRepository: KioskOrderRepository = KioskOrderRepository(KioskComboRepository(schemaInspector)),
    private val placeKioskOrderService: PlaceKioskOrderService =
        PlaceKioskOrderService(kioskOrderRepository = kioskOrderRepository, kioskSettingsRepository = kioskSettingsRepository),
    private val kioskYappyService: KioskYappyService? = null,
    private val kioskCajaRepository: KioskCajaRepository = KioskCajaRepository(),
    private val kioskCustomerRepository: KioskCustomerRepository = KioskCustomerRepository(),
) {
    private val json =
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = false
            explicitNulls = false
        }

    /**
     * Construye el contexto del kiosco desde la caja elegida: debe existir y estar activa
     * (si no, null). Sucursal, almacén y vendedor salen de la caja igual que en `GET /api/cajas`
     * (almacén de la caja → por defecto de la sucursal → `parametros_generales.cod_almacen`;
     * vendedor del usuario → de la caja → de la sucursal → 0, como el POS). El cliente genérico
     * es `parametros_generales.default_cod_cliente_factura` resuelto a `clientes.id_cliente`.
     */
    suspend fun resolveContext(selection: KioskCajaSelection): KioskRequestContext? {
        val database = databaseResolver(selection.countryCode, selection.companyDb)
        val caja =
            kioskCajaRepository.findActiveCaja(database, selection.countryCode, selection.idCaja, selection.userId)
                ?: return null
        val settings = kioskSettingsRepository.load(database)
        val defaultCode = settings.defaultCodClienteFactura ?: DEFAULT_CUSTOMER_CODE
        val idClienteGenerico = kioskCustomerRepository.resolveClientId(database, defaultCode) ?: defaultCode

        return KioskRequestContext(
            countryCode = selection.countryCode,
            companyDb = selection.companyDb,
            deviceId = caja.idCaja,
            deviceName = caja.name,
            prefix = selection.prefix,
            idCaja = caja.idCaja,
            idSucursal = caja.idSucursal,
            idAlmacen = caja.idAlmacen,
            codVendedor = caja.codVendedor,
            idClienteGenerico = idClienteGenerico,
            userId = selection.userId,
        )
    }

    suspend fun unlock(
        kioskContext: KioskRequestContext,
        request: KioskUnlockRequest,
    ): Result<Unit> =
        runCatching {
            // Intentos por empresa + caja + usuario (antes: por dispositivo emparejado).
            val limiterKey = "${kioskContext.companyDb}|${kioskContext.idCaja}|${kioskContext.userId ?: 0}"
            if (unlockRateLimiter.isRateLimited(limiterKey)) {
                throw KioskRateLimitException("Demasiados intentos fallidos. Intente de nuevo en 5 minutos.")
            }

            val password = request.password
            if (password.isBlank()) {
                unlockRateLimiter.recordFailure(limiterKey)
                throw KioskAuthenticationException("Contraseña requerida")
            }

            val database = databaseResolver(kioskContext.countryCode, kioskContext.companyDb)
            val hashedClave =
                kioskSettingsRepository.load(database).claveKiosko
                    ?: throw IllegalStateException("No hay clave de kiosco configurada en parametros_generales")

            val valid =
                if (hashedClave.startsWith("$2")) {
                    try {
                        BCrypt.checkpw(password, hashedClave)
                    } catch (_: Exception) {
                        false
                    }
                } else {
                    // Soporte fallback si no fuera bcrypt
                    password == hashedClave
                }

            if (!valid) {
                unlockRateLimiter.recordFailure(limiterKey)
                throw KioskAuthenticationException("Contraseña de desbloqueo incorrecta")
            }

            unlockRateLimiter.recordSuccess(limiterKey)
        }

    suspend fun getConfig(kioskContext: KioskRequestContext): Result<Pair<KioskConfigResponse, String>> =
        runCatching {
            val database = databaseResolver(kioskContext.countryCode, kioskContext.companyDb)
            val baseConfig =
                kioskConfigRepository.getKioskConfig(
                    database = database,
                    countryCode = kioskContext.countryCode,
                    companyDb = kioskContext.companyDb,
                )
            val yappyAvailable = kioskYappyService?.isAvailable(database, kioskContext) == true
            val paymentMethods =
                if (yappyAvailable) {
                    listOf(KioskPaymentMethod.CARD, KioskPaymentMethod.YAPPY)
                } else {
                    listOf(KioskPaymentMethod.CARD)
                }
            val unversioned = baseConfig.copy(paymentMethods = paymentMethods, version = 0)
            val contentHash = sha256(json.encodeToString(KioskConfigResponse.serializer(), unversioned))
            // version deriva del contenido: cambia solo cuando cambia la configuración.
            val config = unversioned.copy(version = contentHash.take(VERSION_HEX_DIGITS).toInt(HEX_RADIX))
            val etag = "\"" + sha256(json.encodeToString(KioskConfigResponse.serializer(), config)) + "\""
            Pair(config, etag)
        }

    suspend fun getCatalog(kioskContext: KioskRequestContext): Result<Pair<KioskCatalogResponse, String>> =
        runCatching {
            val database = databaseResolver(kioskContext.countryCode, kioskContext.companyDb)
            val catalog =
                kioskCatalogRepository.getCatalog(
                    database = database,
                    countryCode = kioskContext.countryCode,
                    companyDb = kioskContext.companyDb,
                )
            val jsonString = json.encodeToString(KioskCatalogResponse.serializer(), catalog)
            val etag = "\"" + sha256(jsonString) + "\""
            Pair(catalog, etag)
        }

    suspend fun createQuote(
        kioskContext: KioskRequestContext,
        idempotencyKey: String,
        request: KioskQuoteRequest,
    ): Result<KioskQuoteResponse> =
        runCatching {
            if (idempotencyKey.isBlank()) {
                throw IllegalArgumentException("Header Idempotency-Key es requerido")
            }
            val database = requireKioskOrderTables(kioskContext)
            kioskOrderRepository.createQuote(
                database = database,
                kioskContext = kioskContext,
                idempotencyKey = idempotencyKey,
                request = request,
            )
        }

    suspend fun payOrder(
        kioskContext: KioskRequestContext,
        orderId: String,
        request: KioskPaymentRequest,
    ): Result<KioskPayResponse> =
        runCatching {
            if (orderId.isBlank()) {
                throw IllegalArgumentException("orderId no puede estar vacío")
            }
            val database = requireKioskOrderTables(kioskContext)
            placeKioskOrderService.payOrder(
                database = database,
                kioskContext = kioskContext,
                orderId = orderId,
                request = request,
            )
        }

    suspend fun createYappyQr(
        kioskContext: KioskRequestContext,
        orderId: String,
    ): Result<KioskYappyQrResponse> =
        runCatching {
            val yappy = kioskYappyService ?: throw KioskYappyNotConfiguredException()
            yappy.createQr(requireKioskOrderTables(kioskContext), kioskContext, orderId)
        }

    suspend fun getYappyStatus(
        kioskContext: KioskRequestContext,
        orderId: String,
        transactionId: String,
    ): Result<KioskYappyStatusResponse> =
        runCatching {
            val yappy = kioskYappyService ?: throw KioskYappyNotConfiguredException()
            val database = requireKioskOrderTables(kioskContext)
            yappy.getStatus(database, kioskContext, orderId, transactionId)
        }

    /** Anulación best-effort: el resultado solo informa errores inesperados para registrarlos. */
    suspend fun cancelYappy(
        kioskContext: KioskRequestContext,
        orderId: String,
        transactionId: String,
    ): Result<Unit> =
        runCatching {
            val yappy = kioskYappyService ?: return@runCatching
            val database = requireKioskOrderTables(kioskContext)
            yappy.cancel(database, kioskContext, orderId, transactionId)
        }

    /** Base de la empresa si tiene las tablas `kiosco_pedido*`; si no, [KioskNotEnabledException]. */
    private suspend fun requireKioskOrderTables(kioskContext: KioskRequestContext): Database {
        val database = databaseResolver(kioskContext.countryCode, kioskContext.companyDb)
        if (!schemaInspector.hasKioskOrderTables(database)) throw KioskNotEnabledException()
        return database
    }

    private fun sha256(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }
}

private const val DEFAULT_CUSTOMER_CODE = "CF"

/** 7 dígitos hex (28 bits) caben en un Int positivo. */
private const val VERSION_HEX_DIGITS = 7
private const val HEX_RADIX = 16
