package com.amaxonia.kiosk.domain.checkout

import com.amaxonia.kiosk.core.network.KioskQuoteRequest
import com.amaxonia.kiosk.core.network.KioskQuoteResponse
import com.amaxonia.kiosk.domain.payment.PaymentTerminal
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Per-customer checkout state shared by the payment-method, card and Yappy screens:
 * - the single server quote every payment method charges (quoted once per cart content);
 * - the idempotency key, stable while the cart is unchanged so retries never duplicate orders;
 * - the in-flight payment, so a session reset can always cancel the terminal / Yappy charge.
 */
class CheckoutSession(
    private val paymentTerminal: PaymentTerminal,
    private val yappyCheckout: YappyCheckoutUseCase,
    private val cleanupScope: CoroutineScope,
) {
    private val _quote = MutableStateFlow<KioskQuoteResponse?>(null)
    val quote: StateFlow<KioskQuoteResponse?> = _quote.asStateFlow()

    private var quotedRequest: KioskQuoteRequest? = null
    private var keyRequest: KioskQuoteRequest? = null
    private var idempotencyKey: String? = null
    private var cardPaymentInFlight = false
    private var activeYappy: Pair<KioskQuoteResponse, YappyCharge>? = null

    @Synchronized
    fun idempotencyKeyFor(request: KioskQuoteRequest): String {
        val current = idempotencyKey
        return if (current != null && request == keyRequest) {
            current
        } else {
            UUID.randomUUID().toString().also {
                idempotencyKey = it
                keyRequest = request
            }
        }
    }

    /** The stored quote, only if it was computed for exactly this cart. */
    @Synchronized
    fun quoteFor(request: KioskQuoteRequest): KioskQuoteResponse? = _quote.value?.takeIf { request == quotedRequest }

    @Synchronized
    fun storeQuote(
        request: KioskQuoteRequest,
        quote: KioskQuoteResponse,
    ) {
        quotedRequest = request
        _quote.value = quote
    }

    /** The server rejected the quote (expired): drop it and its key so the next attempt re-quotes. */
    @Synchronized
    fun invalidateQuote() {
        _quote.value = null
        quotedRequest = null
        idempotencyKey = null
        keyRequest = null
    }

    @Synchronized
    fun setCardPaymentInFlight(inFlight: Boolean) {
        cardPaymentInFlight = inFlight
    }

    @Synchronized
    fun setActiveYappyCharge(
        quote: KioskQuoteResponse,
        charge: YappyCharge,
    ) {
        activeYappy = quote to charge
    }

    @Synchronized
    fun clearActiveYappyCharge() {
        activeYappy = null
    }

    /** Cancels the card terminal if a card payment is still waiting for the customer. */
    @Synchronized
    fun abortCardPayment() {
        if (cardPaymentInFlight) {
            cardPaymentInFlight = false
            cleanupScope.launch { paymentTerminal.cancelPayment() }
        }
    }

    /** Cancels (or, if Yappy already confirmed it, settles) the pending Yappy charge. */
    @Synchronized
    fun abortYappyCharge() {
        activeYappy?.let { (quote, charge) ->
            activeYappy = null
            cleanupScope.launch { yappyCheckout.cancelOrSettle(quote, charge) }
        }
    }

    /**
     * Cancels whatever payment is still running (card terminal and/or pending Yappy charge).
     * Runs on [cleanupScope] because the caller's scope is usually being torn down.
     */
    fun abortInFlightPayment() {
        abortCardPayment()
        abortYappyCharge()
    }

    /** Ends the customer session: aborts in-flight payments and forgets the quote. */
    fun clear() {
        abortInFlightPayment()
        invalidateQuote()
    }
}
