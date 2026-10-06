package com.amaxonia.kiosk.domain.payment

/** Payment methods a kiosk can offer. [wireName] is the value used by `/config` and `POST /pay`. */
enum class PaymentMethod(
    val wireName: String,
) {
    CARD("CARD"),
    YAPPY("YAPPY"),
    ;

    companion object {
        fun fromWire(value: String): PaymentMethod? = entries.firstOrNull { it.wireName.equals(value.trim(), ignoreCase = true) }

        /**
         * Methods the customer can actually use: the ones enabled in the company config, minus CARD
         * when this device has no working card terminal. Order follows the config.
         */
        fun available(
            configured: List<String>,
            cardTerminalAvailable: Boolean,
        ): List<PaymentMethod> =
            configured
                .mapNotNull(::fromWire)
                .distinct()
                .filter { it != CARD || cardTerminalAvailable }
    }
}
