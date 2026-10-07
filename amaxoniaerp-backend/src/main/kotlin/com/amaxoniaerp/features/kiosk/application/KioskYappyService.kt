package com.amaxoniaerp.features.kiosk.application

import com.amaxoniaerp.features.kiosk.data.KioskOrderRepository
import com.amaxoniaerp.features.kiosk.data.KioskYappyConfigRepository
import com.amaxoniaerp.features.kiosk.domain.KioskOrderCharge
import com.amaxoniaerp.features.kiosk.domain.KioskOrderRecord
import com.amaxoniaerp.features.kiosk.domain.KioskRequestContext
import com.amaxoniaerp.features.kiosk.domain.KioskYappyQrResponse
import com.amaxoniaerp.features.kiosk.domain.KioskYappyStatusResponse
import com.amaxoniaerp.features.kiosk.domain.yappy.YappyCharge
import com.amaxoniaerp.features.kiosk.domain.yappy.YappyGateway
import com.amaxoniaerp.features.kiosk.domain.yappy.YappyKioskConfig
import com.amaxoniaerp.features.kiosk.domain.yappy.YappyQr
import com.amaxoniaerp.features.kiosk.domain.yappy.YappyQrType
import com.amaxoniaerp.features.kiosk.domain.yappy.YappyTransactionStatus
import com.amaxoniaerp.features.kiosk.domain.yappy.YappyUpstreamException
import org.jetbrains.exposed.exceptions.ExposedSQLException
import org.jetbrains.exposed.sql.Database
import org.slf4j.LoggerFactory
import java.time.LocalDateTime

/** La empresa/caja del kiosco no tiene credenciales o unidad de cobro Yappy. */
class KioskYappyNotConfiguredException : RuntimeException("Yappy no está configurado para este kiosco")

/** Confirmación server-side de un pago Yappy antes de facturar. */
fun interface KioskYappyPaymentVerifier {
    /**
     * true solo si [transactionId] es la transacción Yappy registrada en el pedido y Yappy la
     * reporta COMPLETED. Cualquier error de Yappy o configuración ausente devuelve false.
     */
    suspend fun isPaymentCompleted(
        database: Database,
        kioskContext: KioskRequestContext,
        order: KioskOrderRecord,
        transactionId: String,
    ): Boolean
}

/**
 * Cobro con QR Yappy de un pedido de kiosco: genera el QR, consulta su estado y lo anula.
 *
 * Fases cortas (ADR-005): lectura del pedido/configuración, llamada HTTP a Yappy sin
 * transacción SQL abierta y persistencia del transactionId en otra transacción.
 */
class KioskYappyService(
    private val kioskOrderRepository: KioskOrderRepository,
    private val yappyConfigRepository: KioskYappyConfigRepository,
    private val yappyGateway: YappyGateway,
    private val sessionManager: YappySessionManager,
    /** Respaldo (`YAPPY_QR_TYPE`) cuando `parametros_generales.yappy_tipo_qr` no existe o está vacío. */
    private val defaultQrType: YappyQrType = YappyQrType.DEFAULT,
    private val clock: () -> LocalDateTime = LocalDateTime::now,
) : KioskYappyPaymentVerifier {
    private val logger = LoggerFactory.getLogger(KioskYappyService::class.java)

    suspend fun isAvailable(
        database: Database,
        kioskContext: KioskRequestContext,
    ): Boolean = findConfig(database, kioskContext) != null

    suspend fun createQr(
        database: Database,
        kioskContext: KioskRequestContext,
        orderId: String,
    ): KioskYappyQrResponse {
        val order = loadOwnedOrder(database, kioskContext, orderId)
        check(order.estado == ESTADO_COTIZADO) { "El pedido se encuentra en estado ${order.estado}" }
        check(!clock().isAfter(order.quoteExpiraEn)) { "La cotización del pedido ha expirado" }

        val config = requireConfig(database, kioskContext)
        order.yappyTransactionId?.let { previous -> releasePreviousTransaction(kioskContext, config, order, previous) }

        val qrType = yappyConfigRepository.findQrType(database) ?: defaultQrType
        val charge = KioskOrderCharge.from(order)
        val qr = generateQr(kioskContext, config, qrType, order, charge)
        assignTransactionOrRelease(database, kioskContext, config, order, qr.transactionId)
        logger.info("[YAPPY] Pedido {} con QR {} transactionId={}", order.codigoPedido, qrType, qr.transactionId)

        return KioskYappyQrResponse(
            transactionId = qr.transactionId,
            qrHash = qr.hash,
            amount = charge.total.toPlainString(),
            expiresInSec = QR_EXPIRES_IN_SEC,
        )
    }

    /** Genera el QR en Yappy y valida el identificador de transacción devuelto. */
    private suspend fun generateQr(
        kioskContext: KioskRequestContext,
        config: YappyKioskConfig,
        qrType: YappyQrType,
        order: KioskOrderRecord,
        charge: KioskOrderCharge,
    ): YappyQr {
        val qr =
            sessionManager.withSession(sessionKey(kioskContext), config) { token ->
                yappyGateway.generateQr(
                    credentials = config.credentials,
                    sessionToken = token,
                    qrType = qrType,
                    charge =
                        YappyCharge(
                            subTotal = charge.subTotal,
                            tax = charge.tax,
                            total = charge.total,
                            orderId = order.codigoPedido,
                            description = "Kiosco ${kioskContext.deviceName}",
                        ),
                )
            }
        if (qr.transactionId.length > MAX_TRANSACTION_ID_LENGTH) {
            throw YappyUpstreamException("Yappy devolvió un identificador de transacción inválido")
        }
        return qr
    }

    /**
     * Sin columnas nuevas: pago_marca = 'YAPPY' y pago_referencia = transactionId (solo en COTIZADO).
     * Si el pedido dejó de estar COTIZADO mientras se generaba el QR, lo anula best-effort para no
     * dejarlo cobrable y falla con [IllegalStateException].
     */
    private suspend fun assignTransactionOrRelease(
        database: Database,
        kioskContext: KioskRequestContext,
        config: YappyKioskConfig,
        order: KioskOrderRecord,
        transactionId: String,
    ) {
        if (kioskOrderRepository.assignYappyTransaction(database, order.id, transactionId)) return
        runCatching {
            sessionManager.withSession(sessionKey(kioskContext), config) { token ->
                yappyGateway.cancelTransaction(config.credentials, token, transactionId)
            }
        }.onFailure { logger.warn("[YAPPY] No se pudo anular QR huérfano transactionId={}: {}", transactionId, it.message) }
        throw IllegalStateException("El pedido ${order.codigoPedido} ya no está pendiente de pago")
    }

    suspend fun getStatus(
        database: Database,
        kioskContext: KioskRequestContext,
        orderId: String,
        transactionId: String,
    ): KioskYappyStatusResponse {
        val order = loadOwnedOrder(database, kioskContext, orderId)
        check(order.yappyTransactionId == transactionId) { "La transacción Yappy no corresponde al pedido" }
        val config = requireConfig(database, kioskContext)
        val status = fetchStatus(kioskContext, config, transactionId)
        return KioskYappyStatusResponse(transactionId = transactionId, status = status.name)
    }

    /**
     * Anulación best-effort: nunca lanza. No anula un pago ya COMPLETED (el cliente pagó y el
     * kiosco debe confirmar el pedido); libera el transactionId del pedido (pago_marca /
     * pago_referencia, solo en COTIZADO) si la transacción quedó anulada o en un estado final
     * no pagado.
     */
    suspend fun cancel(
        database: Database,
        kioskContext: KioskRequestContext,
        orderId: String,
        transactionId: String,
    ) {
        try {
            val order = kioskOrderRepository.findOrderRecordById(database, orderId)
            val cancellable =
                order != null &&
                    order.idDispositivo == kioskContext.deviceId &&
                    order.estado == ESTADO_COTIZADO &&
                    order.yappyTransactionId == transactionId
            if (!cancellable) {
                logger.info("[YAPPY] Anulación omitida para pedido {} (no aplica a transactionId={})", orderId, transactionId)
                return
            }
            val config = findConfig(database, kioskContext) ?: return
            val released = cancelIfNotPaid(kioskContext, config, transactionId)
            if (released) {
                kioskOrderRepository.clearYappyTransaction(database, orderId, transactionId)
            }
        } catch (e: YappyUpstreamException) {
            logger.warn("[YAPPY] No se pudo anular transactionId={}: {}", transactionId, e.message)
        } catch (e: ExposedSQLException) {
            logger.warn("[YAPPY] Error de base de datos anulando transactionId={}: {}", transactionId, e.javaClass.simpleName)
        }
    }

    override suspend fun isPaymentCompleted(
        database: Database,
        kioskContext: KioskRequestContext,
        order: KioskOrderRecord,
        transactionId: String,
    ): Boolean {
        if (transactionId.isBlank() || order.yappyTransactionId != transactionId) return false
        val config = findConfig(database, kioskContext) ?: return false
        return try {
            fetchStatus(kioskContext, config, transactionId) == YappyTransactionStatus.COMPLETED
        } catch (e: YappyUpstreamException) {
            logger.warn("[YAPPY] No se pudo verificar transactionId={}: {}", transactionId, e.message)
            false
        }
    }

    /**
     * Antes de generar otro QR para el mismo pedido: si el anterior ya fue pagado se bloquea
     * (evita cobrar dos veces); si sigue pendiente o no se pudo consultar, se anula best-effort.
     */
    private suspend fun releasePreviousTransaction(
        kioskContext: KioskRequestContext,
        config: YappyKioskConfig,
        order: KioskOrderRecord,
        previousTransactionId: String,
    ) {
        val previousStatus =
            try {
                fetchStatus(kioskContext, config, previousTransactionId)
            } catch (e: YappyUpstreamException) {
                logger.warn("[YAPPY] Estado previo desconocido transactionId={}: {}", previousTransactionId, e.message)
                null
            }
        check(previousStatus != YappyTransactionStatus.COMPLETED) {
            "El pedido ${order.codigoPedido} ya tiene un pago Yappy completado; confirme el pago"
        }
        if (previousStatus == null || previousStatus == YappyTransactionStatus.PENDING) {
            try {
                sessionManager.withSession(sessionKey(kioskContext), config) { token ->
                    yappyGateway.cancelTransaction(config.credentials, token, previousTransactionId)
                }
            } catch (e: YappyUpstreamException) {
                logger.warn("[YAPPY] No se pudo anular QR previo transactionId={}: {}", previousTransactionId, e.message)
            }
        }
    }

    /** Devuelve true si la transacción ya no puede pagarse (anulada o en estado final no pagado). */
    private suspend fun cancelIfNotPaid(
        kioskContext: KioskRequestContext,
        config: YappyKioskConfig,
        transactionId: String,
    ): Boolean =
        sessionManager.withSession(sessionKey(kioskContext), config) { token ->
            when (yappyGateway.getTransactionStatus(config.credentials, token, transactionId)) {
                YappyTransactionStatus.COMPLETED -> {
                    logger.warn("[YAPPY] transactionId={} ya está COMPLETED; no se anula", transactionId)
                    false
                }
                YappyTransactionStatus.PENDING -> {
                    yappyGateway.cancelTransaction(config.credentials, token, transactionId)
                    true
                }
                else -> true
            }
        }

    private suspend fun fetchStatus(
        kioskContext: KioskRequestContext,
        config: YappyKioskConfig,
        transactionId: String,
    ): YappyTransactionStatus =
        sessionManager.withSession(sessionKey(kioskContext), config) { token ->
            yappyGateway.getTransactionStatus(config.credentials, token, transactionId)
        }

    private suspend fun loadOwnedOrder(
        database: Database,
        kioskContext: KioskRequestContext,
        orderId: String,
    ): KioskOrderRecord {
        require(orderId.isNotBlank()) { "orderId no puede estar vacío" }
        val order =
            kioskOrderRepository.findOrderRecordById(database, orderId)
                ?: throw IllegalArgumentException("Pedido $orderId no encontrado")
        require(order.idDispositivo == kioskContext.deviceId) { "El pedido no pertenece a este dispositivo" }
        return order
    }

    private suspend fun findConfig(
        database: Database,
        kioskContext: KioskRequestContext,
    ): YappyKioskConfig? = yappyConfigRepository.findConfig(database, kioskContext.countryCode, kioskContext.idCaja)

    private suspend fun requireConfig(
        database: Database,
        kioskContext: KioskRequestContext,
    ): YappyKioskConfig = findConfig(database, kioskContext) ?: throw KioskYappyNotConfiguredException()

    private fun sessionKey(kioskContext: KioskRequestContext): YappySessionKey =
        YappySessionKey(companyDb = kioskContext.companyDb, idCaja = kioskContext.idCaja)

    private companion object {
        const val ESTADO_COTIZADO = "COTIZADO"
        const val QR_EXPIRES_IN_SEC = 180
        const val MAX_TRANSACTION_ID_LENGTH = 64
    }
}
