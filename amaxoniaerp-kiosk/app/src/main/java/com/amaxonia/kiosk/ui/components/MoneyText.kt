package com.amaxonia.kiosk.ui.components

import com.amaxonia.kiosk.core.money.Money
import com.amaxonia.kiosk.core.network.KioskCurrencyConfig
import java.math.BigDecimal

/**
 * Reference amount in the secondary currency ("Ref. Bs 36.50"), or an empty string when the
 * company has no secondary currency configured (e.g. Panamá).
 */
fun Money.secondaryText(currency: KioskCurrencyConfig): String {
    val secondary = currency.secondary ?: return ""
    val rate = runCatching { BigDecimal(currency.rate) }.getOrDefault(BigDecimal.ZERO)
    return toSecondaryCurrency(rate, secondary)
}
