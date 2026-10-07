package com.amaxoniaerp.features.kiosk.domain.yappy

import java.math.BigDecimal

/**
 * Puerto hacia la API de Yappy (Banco General) para cobros con QR desde una unidad de cobro.
 *
 * Contrato espejo del integrado en el administrativo PHP (`caja.class.php`:
 * openYappySession / generarQrYappy / verificarPagoYappy). Las implementaciones:
 * - lanzan [YappySessionExpiredException] cuando Yappy rechaza el token de sesión (401/403);
 * - lanzan [YappyUpstreamException] ante cualquier otro error de transporte o respuesta;
 * - nunca registran api-key, secret-key ni el token de sesión.
 */
interface YappyGateway {
    /** Abre una sesión de dispositivo y devuelve el token que autoriza las demás operaciones. */
    suspend fun openSession(
        credentials: YappyCredentials,
        device: YappyDevice,
    ): String

    /** Genera un QR de cobro. El `hash` devuelto es el contenido que escanea la app Yappy del cliente. */
    suspend fun generateQr(
        credentials: YappyCredentials,
        sessionToken: String,
        qrType: YappyQrType,
        charge: YappyCharge,
    ): YappyQr

    suspend fun getTransactionStatus(
        credentials: YappyCredentials,
        sessionToken: String,
        transactionId: String,
    ): YappyTransactionStatus

    /** Anula una transacción pendiente. */
    suspend fun cancelTransaction(
        credentials: YappyCredentials,
        sessionToken: String,
        transactionId: String,
    )
}

/** Credenciales de comercio (parametros_generales.yappy_*) y la URL base del ambiente activo. */
data class YappyCredentials(
    val apiKey: String,
    val secretKey: String,
    val baseUrl: String,
) {
    override fun toString(): String = "YappyCredentials(baseUrl=$baseUrl)"
}

/** Unidad de cobro (dispositivo) y grupo registrados en el portal Yappy. */
data class YappyDevice(
    val deviceId: String,
    val groupId: String,
)

/** Configuración completa para cobrar con Yappy desde una caja. */
data class YappyKioskConfig(
    val credentials: YappyCredentials,
    val device: YappyDevice,
)

/**
 * Montos del cobro en dinero (escala 2). `total` debe ser exactamente `subTotal + tax`
 * (propina y descuento siempre 0 en el kiosco).
 */
data class YappyCharge(
    val subTotal: BigDecimal,
    val tax: BigDecimal,
    val total: BigDecimal,
    val orderId: String,
    val description: String,
)

data class YappyQr(
    val transactionId: String,
    val hash: String,
)

/** Tipo de QR de Yappy. DYN = dinámico de un solo uso (kiosco); HYB = híbrido (usado por el PHP). */
enum class YappyQrType {
    DYN,
    HYB,
    ;

    companion object {
        val DEFAULT = DYN

        /** Interpreta `YAPPY_QR_TYPE`; valores vacíos o desconocidos usan [DEFAULT]. */
        fun fromConfig(raw: String?): YappyQrType = parseOrNull(raw) ?: DEFAULT

        /** DYN/HYB (sin distinguir mayúsculas); null si está vacío o no es un tipo válido. */
        fun parseOrNull(raw: String?): YappyQrType? {
            val normalized = raw?.trim()?.uppercase().orEmpty()
            return entries.firstOrNull { it.name == normalized }
        }
    }
}

/** Estados normalizados que se exponen al kiosco. */
enum class YappyTransactionStatus {
    PENDING,
    COMPLETED,
    FAILED,
    DECLINED,
    EXPIRED,
    CANCELLED,
    ;

    companion object {
        /** Normaliza el estado crudo de Yappy. VOIDED/CANCELED equivalen a CANCELLED; lo desconocido es PENDING. */
        fun fromYappy(raw: String?): YappyTransactionStatus =
            when (val normalized = raw?.trim()?.uppercase().orEmpty()) {
                "VOIDED", "CANCELED" -> CANCELLED
                else -> entries.firstOrNull { it.name == normalized } ?: PENDING
            }
    }
}

/** Error de comunicación o respuesta inválida de Yappy. El mensaje es apto para el cliente. */
open class YappyUpstreamException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)

/** Yappy rechazó el token de sesión (401/403): hay que reabrir la sesión. */
class YappySessionExpiredException(
    message: String,
) : YappyUpstreamException(message)
