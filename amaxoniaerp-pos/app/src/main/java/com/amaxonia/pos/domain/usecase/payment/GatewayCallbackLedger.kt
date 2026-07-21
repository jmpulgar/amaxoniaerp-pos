package com.amaxonia.pos.domain.usecase.payment

/**
 * Domain port for persisting gateway (HKA Rapid Pay) callback state on the
 * local ledger.
 *
 * Implementations wire this to [QueueGatewayCallbackUseCase] (Room-backed)
 * and schedule a [com.amaxonia.pos.data.sync.GatewayCallbackWorker]
 * reconciliation. The payment flow records the moment it launches the HKA
 * Intent (so a process death is recoverable) and the moment the bridge
 * delivers the result (so the row is marked resolved).
 *
 * The encrypted gateway command is NEVER persisted through this port — only
 * the short user-facing response code/message HKA returns in its callback
 * Intent extras.
 */
fun interface GatewayCallbackLedger {
    suspend fun recordOutcome(outcome: GatewayCallbackOutcome)

    suspend fun markAwaiting(
        correlationId: String,
        nextAttemptAt: Long,
    ) = recordOutcome(
        GatewayCallbackOutcome.Awaiting(
            correlationId = correlationId,
            nextAttemptAt = nextAttemptAt,
        ),
    )

    suspend fun markResolved(
        correlationId: String,
        responseCode: String,
        rawResponse: String? = null,
        message: String? = null,
    ) = recordOutcome(
        GatewayCallbackOutcome.Resolved(
            correlationId = correlationId,
            responseCode = responseCode,
            rawResponse = rawResponse,
            message = message,
        ),
    )
}

sealed interface GatewayCallbackOutcome {
    /**
     * Emitted just before launching the HKA Intent. Pins the row to the
     * [nextAttemptAt] deadline at which the reconciler worker should
     * surface it (default = NOW so the worker may pick it up immediately
     * after the lease window).
     */
    data class Awaiting(
        val correlationId: String,
        val nextAttemptAt: Long,
    ) : GatewayCallbackOutcome

    /**
     * Emitted once MainActivity receives the HKA callback Intent.
     *
     * - [responseCode]: short `codeRapidPay` extra ("200"/"400"). Always
     *   present. NEVER an encrypted command or card number.
     * - [rawResponse]: full JSON returned in `resultRapidPay`, kept verbatim
     *   for audit + reconciliation. May be null when HKA sends only the
     *   short code. Redacted of any card data upstream in
     *   `TheFactoryRapidPayClient.parseResultIntent`.
     * - [message]: user-facing message (`messageRapidPay` or extracted from
     *   the JSON). May be null.
     */
    data class Resolved(
        val correlationId: String,
        val responseCode: String,
        val rawResponse: String? = null,
        val message: String? = null,
    ) : GatewayCallbackOutcome
}
