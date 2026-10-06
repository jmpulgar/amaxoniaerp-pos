package com.amaxonia.kiosk.domain.session

import com.amaxonia.kiosk.domain.cart.OrderGraph
import com.amaxonia.kiosk.domain.checkout.CheckoutSession
import com.amaxonia.kiosk.ui.accessibility.AccessibilityManager
import com.amaxonia.kiosk.ui.idle.IdleTimerManager
import com.amaxonia.kiosk.ui.payment.CompletedOrderInfo
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * The single "back to Attract" reset used by every exit path (idle timeout, cancel buttons, order
 * finished): the next customer must never inherit the cart, dining mode, table tent, customer,
 * payment, accessibility mode, high contrast or language of the previous one.
 */
class SessionResetter(
    private val orderGraph: OrderGraph,
    private val completedOrderState: MutableStateFlow<CompletedOrderInfo?>,
    private val checkoutSession: CheckoutSession,
    private val accessibilityManager: AccessibilityManager,
    private val idleTimerManager: IdleTimerManager,
    private val onSessionEnded: () -> Unit,
) {
    fun resetSession() {
        checkoutSession.clear()
        orderGraph.reset()
        completedOrderState.value = null
        accessibilityManager.reset()
        idleTimerManager.setPaused(false)
        idleTimerManager.reset()
        onSessionEnded()
    }
}
