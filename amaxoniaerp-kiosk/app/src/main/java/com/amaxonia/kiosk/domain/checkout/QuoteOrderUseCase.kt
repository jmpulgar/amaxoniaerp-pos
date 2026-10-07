package com.amaxonia.kiosk.domain.checkout

import com.amaxonia.kiosk.core.network.KioskQuoteResponse
import com.amaxonia.kiosk.domain.cart.OrderGraph
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId

/**
 * Returns the server quote for the current cart, quoting only when needed: the cached quote of the
 * [CheckoutSession] is reused while the cart is unchanged and the quote has not expired, so every
 * payment method (and every retry) charges exactly the same `quote.total` for the same order.
 */
class QuoteOrderUseCase(
    private val checkoutUseCase: CheckoutOrderUseCase,
    private val session: CheckoutSession,
    private val now: () -> Instant = Instant::now,
) {
    suspend operator fun invoke(orderGraph: OrderGraph): Result<KioskQuoteResponse> {
        val request = CheckoutOrderUseCase.buildQuoteRequest(orderGraph)
        val cached = session.quoteFor(request)?.takeUnless(::isExpired)
        if (cached == null && session.quote.value != null) {
            session.invalidateQuote()
        }
        return if (cached != null) {
            Result.success(cached)
        } else {
            checkoutUseCase
                .quote(request, session.idempotencyKeyFor(request))
                .onSuccess { session.storeQuote(request, it) }
        }
    }

    /**
     * A quote counts as expired [EXPIRY_MARGIN] before the server's deadline, so a payment never
     * starts on a quote the server is about to reject ("Tu pedido expiró"). An unreadable deadline
     * also counts as expired: re-quoting is always safe, reusing a dead quote is not.
     */
    private fun isExpired(quote: KioskQuoteResponse): Boolean {
        val expiresAt = parseExpiry(quote.expiresAt) ?: return true
        return !expiresAt.minus(EXPIRY_MARGIN).isAfter(now())
    }

    companion object {
        private val EXPIRY_MARGIN: Duration = Duration.ofSeconds(60)

        /**
         * Accepts an instant (`2026-10-07T15:04:05Z`), a date-time with offset, or a server-local
         * date-time without zone (`2026-10-07T10:04:05`, sent by backends before 0.0.7), which is read
         * in the kiosk's own time zone (kiosk and server run in the same country).
         */
        fun parseExpiry(
            value: String,
            zone: ZoneId = ZoneId.systemDefault(),
        ): Instant? {
            val text = value.trim()
            return runCatching { Instant.parse(text) }.getOrNull()
                ?: runCatching { OffsetDateTime.parse(text).toInstant() }.getOrNull()
                ?: runCatching { LocalDateTime.parse(text).atZone(zone).toInstant() }.getOrNull()
        }
    }
}
