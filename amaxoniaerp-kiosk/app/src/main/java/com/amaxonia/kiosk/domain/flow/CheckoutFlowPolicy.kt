package com.amaxonia.kiosk.domain.flow

import com.amaxonia.kiosk.core.network.KioskCardOption
import com.amaxonia.kiosk.core.network.KioskConfigResponse
import com.amaxonia.kiosk.domain.payment.PaymentMethod

const val DINING_MODE_EAT_IN = "COMER_AQUI"
const val DISPATCH_TABLES = "MESAS"

/** Pure navigation rules from spec §3, derived from the company config. */
object CheckoutFlowPolicy {
    /** The only enabled dining mode when the company has exactly one (DiningMode screen is skipped), else null. */
    fun singleDiningMode(config: KioskConfigResponse?): String? = config?.diningModes?.distinct()?.singleOrNull()

    /** TableTent is shown only for dispatch MESAS and dining mode "Comer aquí". */
    fun needsTableTent(
        config: KioskConfigResponse?,
        diningMode: String,
    ): Boolean = config?.dispatch == DISPATCH_TABLES && diningMode == DINING_MODE_EAT_IN

    fun availablePaymentMethods(
        config: KioskConfigResponse?,
        cardTerminalAvailable: Boolean,
    ): List<PaymentMethod> =
        PaymentMethod.available(
            configured = config?.paymentMethods ?: listOf(PaymentMethod.CARD.wireName),
            cardTerminalAvailable = cardTerminalAvailable,
            hasCardOptions = !config?.cardOptions.isNullOrEmpty(),
        )

    /** Card methods listed on the method screen (one tile each); empty = a single generic card tile. */
    fun cardOptions(config: KioskConfigResponse?): List<KioskCardOption> = config?.cardOptions.orEmpty()
}
