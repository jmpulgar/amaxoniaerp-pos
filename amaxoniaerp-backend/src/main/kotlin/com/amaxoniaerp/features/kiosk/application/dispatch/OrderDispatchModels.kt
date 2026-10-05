package com.amaxoniaerp.features.kiosk.application.dispatch

enum class KioskDispatchDestination(val code: String) {
    RETIRO_MOSTRADOR("RETIRO_MOSTRADOR"),
    IMPRESORA_COCINA("IMPRESORA_COCINA"),
    MESAS("MESAS"),
    ;

    companion object {
        fun fromString(value: String?): KioskDispatchDestination =
            entries.firstOrNull { it.code.equals(value?.trim(), ignoreCase = true) } ?: RETIRO_MOSTRADOR
    }
}

data class KioskDispatchConfig(
    val kitchenPrinterIp: String? = null,
    val kitchenPrinterPort: Int = 9100,
)

sealed interface DispatchResult {
    val success: Boolean
    val destination: KioskDispatchDestination
    val orderNumber: String

    data class CounterPickup(
        override val orderNumber: String,
        val message: String,
    ) : DispatchResult {
        override val success: Boolean = true
        override val destination: KioskDispatchDestination = KioskDispatchDestination.RETIRO_MOSTRADOR
    }

    data class TableDelivery(
        override val orderNumber: String,
        val tableTent: String,
        val message: String,
    ) : DispatchResult {
        override val success: Boolean = true
        override val destination: KioskDispatchDestination = KioskDispatchDestination.MESAS
    }

    data class KitchenPrinter(
        override val orderNumber: String,
        val printerIp: String?,
        val port: Int,
        val bytesCount: Int,
        val printedSuccessfully: Boolean,
        val errorMessage: String? = null,
    ) : DispatchResult {
        override val success: Boolean = printedSuccessfully
        override val destination: KioskDispatchDestination = KioskDispatchDestination.IMPRESORA_COCINA
    }

    data class Failure(
        override val orderNumber: String,
        override val destination: KioskDispatchDestination,
        val errorMessage: String,
    ) : DispatchResult {
        override val success: Boolean = false
    }
}
