package com.amaxonia.kiosk.ui.navigation

object KioskDestinations {
    const val LOGIN = "login"
    const val CAJA_SETUP = "caja_setup"
    const val ATTRACT = "attract"
    const val MENU = "menu"
    const val CUSTOMIZER = "customizer"
    const val REVIEW = "review"
    const val DINING_MODE = "dining_mode"
    const val CUSTOMER_ID = "customer_id"
    const val TABLE_TENT = "table_tent"
    const val PAYMENT_METHOD = "payment_method"
    const val PAYMENT = "payment"
    const val YAPPY_PAYMENT = "yappy_payment"
    const val ORDER_NUMBER = "order_number"

    /** Routes where the idle timer must not run at all (no customer session in progress). */
    val idleExemptRoutes: Set<String> = setOf(LOGIN, CAJA_SETUP, ATTRACT, ORDER_NUMBER)

    /** Attract and the order number fade/scale instead of sliding: they open and close a session. */
    val sessionBoundaryRoutes: Set<String> = setOf(ATTRACT, ORDER_NUMBER, LOGIN, CAJA_SETUP)
}
