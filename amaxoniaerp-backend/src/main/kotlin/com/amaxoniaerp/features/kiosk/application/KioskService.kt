package com.amaxoniaerp.features.kiosk.application

import com.amaxoniaerp.JwtConfig
import com.amaxoniaerp.features.kiosk.data.KioskCatalogRepository
import com.amaxoniaerp.features.kiosk.data.KioskConfigRepository
import com.amaxoniaerp.features.kiosk.data.KioskDeviceRepository
import com.amaxoniaerp.features.kiosk.data.KioskOrderRepository
import com.amaxoniaerp.features.kiosk.domain.KioskCatalogResponse
import com.amaxoniaerp.features.kiosk.domain.KioskConfigResponse
import com.amaxoniaerp.features.kiosk.domain.KioskDevice
import com.amaxoniaerp.features.kiosk.domain.KioskPairingRequest
import com.amaxoniaerp.features.kiosk.domain.KioskPairingResponse
import com.amaxoniaerp.features.kiosk.domain.KioskPaymentRequest
import com.amaxoniaerp.features.kiosk.domain.KioskPayResponse
import com.amaxoniaerp.features.kiosk.domain.KioskQuoteRequest
import com.amaxoniaerp.features.kiosk.domain.KioskQuoteResponse
import com.amaxoniaerp.features.kiosk.domain.KioskRequestContext
import com.amaxoniaerp.features.kiosk.domain.KioskUnlockRequest
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.Database
import org.mindrot.jbcrypt.BCrypt
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.LocalDateTime

class KioskRateLimitException(message: String) : RuntimeException(message)
class KioskAuthenticationException(message: String) : RuntimeException(message)

class KioskService(
    private val kioskDeviceRepository: KioskDeviceRepository,
    private val unlockRateLimiter: UnlockRateLimiter,
    private val jwtConfig: JwtConfig,
    private val databaseResolver: (countryCode: String, companyDb: String) -> Database,
    private val kioskConfigRepository: KioskConfigRepository = KioskConfigRepository(),
    private val kioskCatalogRepository: KioskCatalogRepository = KioskCatalogRepository(),
    private val kioskOrderRepository: KioskOrderRepository = KioskOrderRepository(),
    private val placeKioskOrderService: PlaceKioskOrderService = PlaceKioskOrderService(kioskOrderRepository = kioskOrderRepository),
) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = false
        explicitNulls = false
    }

    suspend fun pairDevice(request: KioskPairingRequest): Result<KioskPairingResponse> = runCatching {
        val countryCode = request.countryCode.trim().uppercase()
        val companyDb = request.companyDb.trim()
        val pairingCode = request.pairingCode.trim()

        if (countryCode.length != 2) {
            throw IllegalArgumentException("countryCode debe tener 2 letras")
        }
        if (companyDb.isBlank() || companyDb.contains("..")) {
            throw IllegalArgumentException("companyDb inválido")
        }
        if (pairingCode.isBlank()) {
            throw IllegalArgumentException("pairingCode no puede estar vacío")
        }

        val database = databaseResolver(countryCode, companyDb)
        val candidates = kioskDeviceRepository.findCandidateDevices(database)
        val now = LocalDateTime.now()

        val hashedInputSha256 = sha256(pairingCode)

        val matchedDevice = candidates.firstOrNull { device ->
            val hash = device.codigoEmparejamientoHash
            val expira = device.codigoExpiraEn
            if (hash.isNullOrBlank()) return@firstOrNull false
            if (expira != null && now.isAfter(expira)) return@firstOrNull false

            if (hash.startsWith("$2")) {
                try {
                    BCrypt.checkpw(pairingCode, hash)
                } catch (_: Exception) {
                    false
                }
            } else {
                hash.equals(hashedInputSha256, ignoreCase = true)
            }
        } ?: throw KioskAuthenticationException("Código de emparejamiento inválido o expirado")

        val rawDeviceToken = generateSecureToken()
        val tokenHash = sha256(rawDeviceToken)

        val updated = kioskDeviceRepository.updateDevicePairing(
            database = database,
            deviceId = matchedDevice.id,
            tokenHash = tokenHash,
            now = now,
        )
        if (!updated) {
            throw IllegalStateException("No se pudo actualizar el estado del dispositivo")
        }

        val jwtToken = JWT.create()
            .withIssuer(jwtConfig.domain)
            .withAudience(jwtConfig.audience)
            .withClaim("token_type", "kiosk")
            .withClaim("role", "KIOSK")
            .withClaim("device_id", matchedDevice.id)
            .withClaim("device_name", matchedDevice.nombre)
            .withClaim("country_code", countryCode)
            .withClaim("admin_db", companyDb)
            .withClaim("company_db", companyDb)
            .withClaim("prefix", matchedDevice.prefijoPedido)
            .withClaim("box_id", matchedDevice.idCaja)
            .withClaim("branch_id", matchedDevice.idSucursal)
            .withClaim("warehouse_id", matchedDevice.idAlmacen)
            .withClaim("seller_code", matchedDevice.codVendedor)
            .withClaim("customer_id", matchedDevice.idClienteGenerico)
            .sign(Algorithm.HMAC256(jwtConfig.secret))

        KioskPairingResponse(
            deviceId = matchedDevice.id,
            deviceToken = jwtToken,
            deviceName = matchedDevice.nombre,
            prefix = matchedDevice.prefijoPedido,
        )
    }

    suspend fun verifyDeviceActive(countryCode: String, companyDb: String, deviceId: String): KioskDevice? {
        val database = databaseResolver(countryCode, companyDb)
        val device = kioskDeviceRepository.findDeviceById(database, deviceId) ?: return null
        kioskDeviceRepository.touchLastContact(database, deviceId, LocalDateTime.now())
        return device
    }

    suspend fun unlock(kioskContext: KioskRequestContext, request: KioskUnlockRequest): Result<Unit> = runCatching {
        val deviceId = kioskContext.deviceId
        if (unlockRateLimiter.isRateLimited(deviceId)) {
            throw KioskRateLimitException("Demasiados intentos fallidos. Intente de nuevo en 5 minutos.")
        }

        val password = request.password
        if (password.isBlank()) {
            unlockRateLimiter.recordFailure(deviceId)
            throw KioskAuthenticationException("Contraseña requerida")
        }

        val database = databaseResolver(kioskContext.countryCode, kioskContext.companyDb)
        val hashedClave = kioskDeviceRepository.getClaveKiosko(database)
            ?: throw IllegalStateException("No hay clave de kiosco configurada en parametros_generales")

        val valid = if (hashedClave.startsWith("$2")) {
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
            unlockRateLimiter.recordFailure(deviceId)
            throw KioskAuthenticationException("Contraseña de desbloqueo incorrecta")
        }

        unlockRateLimiter.recordSuccess(deviceId)
    }

    suspend fun getConfig(kioskContext: KioskRequestContext): Result<Pair<KioskConfigResponse, String>> = runCatching {
        val database = databaseResolver(kioskContext.countryCode, kioskContext.companyDb)
        val config = kioskConfigRepository.getKioskConfig(
            database = database,
            countryCode = kioskContext.countryCode,
            companyDb = kioskContext.companyDb,
        )
        val jsonString = json.encodeToString(KioskConfigResponse.serializer(), config)
        val etag = "\"" + sha256(jsonString) + "\""
        Pair(config, etag)
    }

    suspend fun getCatalog(kioskContext: KioskRequestContext): Result<Pair<KioskCatalogResponse, String>> = runCatching {
        val database = databaseResolver(kioskContext.countryCode, kioskContext.companyDb)
        val catalog = kioskCatalogRepository.getCatalog(
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
    ): Result<KioskQuoteResponse> = runCatching {
        if (idempotencyKey.isBlank()) {
            throw IllegalArgumentException("Header Idempotency-Key es requerido")
        }
        val database = databaseResolver(kioskContext.countryCode, kioskContext.companyDb)
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
    ): Result<KioskPayResponse> = runCatching {
        if (orderId.isBlank()) {
            throw IllegalArgumentException("orderId no puede estar vacío")
        }
        val database = databaseResolver(kioskContext.countryCode, kioskContext.companyDb)
        placeKioskOrderService.payOrder(
            database = database,
            kioskContext = kioskContext,
            orderId = orderId,
            request = request,
        )
    }

    private fun sha256(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun generateSecureToken(): String {
        val randomBytes = ByteArray(32)
        SecureRandom().nextBytes(randomBytes)
        return randomBytes.joinToString("") { "%02x".format(it) }
    }
}
