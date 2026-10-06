package com.amaxonia.kiosk.domain.checkout

import com.amaxonia.kiosk.core.network.KioskQuoteResponse
import com.amaxonia.kiosk.domain.cart.OrderGraph
import java.time.Instant

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

    private fun isExpired(quote: KioskQuoteResponse): Boolean =
        runCatching { !Instant.parse(quote.expiresAt).isAfter(now()) }.getOrDefault(false)
}
