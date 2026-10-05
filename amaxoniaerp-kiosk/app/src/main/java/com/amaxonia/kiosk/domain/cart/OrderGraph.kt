package com.amaxonia.kiosk.domain.cart

import com.amaxonia.kiosk.core.money.Money
import com.amaxonia.kiosk.core.network.KioskCurrencyConfig
import com.amaxonia.kiosk.core.network.KioskItemDto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.UUID

data class SelectedModifier(
    val optionId: Int,
    val optionName: String,
    val extraPrice: Money,
)

data class CartLine(
    val id: String = UUID.randomUUID().toString(),
    val item: KioskItemDto,
    val quantity: Int = 1,
    val selectedModifiers: List<SelectedModifier> = emptyList(),
    val note: String? = null,
) {
    val unitPrice: Money
        get() {
            val basePrice = Money.fromString(item.price)
            val modifiersPrice = selectedModifiers.fold(Money.ZERO) { acc, mod -> acc + mod.extraPrice }
            return basePrice + modifiersPrice
        }

    val lineTotal: Money
        get() = unitPrice * quantity
}

class OrderGraph {
    private val _lines = MutableStateFlow<List<CartLine>>(emptyList())
    val lines: StateFlow<List<CartLine>> = _lines.asStateFlow()

    private val _diningMode = MutableStateFlow("COMER_AQUI")
    val diningMode: StateFlow<String> = _diningMode.asStateFlow()

    private val _tableTent = MutableStateFlow<String?>(null)
    val tableTent: StateFlow<String?> = _tableTent.asStateFlow()

    private val _customerId = MutableStateFlow("CF")
    val customerId: StateFlow<String> = _customerId.asStateFlow()

    private val _customerName = MutableStateFlow("Consumidor Final")
    val customerName: StateFlow<String> = _customerName.asStateFlow()

    private val _currencyConfig = MutableStateFlow(KioskCurrencyConfig())
    val currencyConfig: StateFlow<KioskCurrencyConfig> = _currencyConfig.asStateFlow()

    val totalItemCount: Int
        get() = _lines.value.sumOf { it.quantity }

    val subtotal: Money
        get() = _lines.value.fold(Money.ZERO) { acc, line -> acc + line.lineTotal }

    fun setCurrencyConfig(config: KioskCurrencyConfig) {
        _currencyConfig.value = config
    }

    fun addLine(
        item: KioskItemDto,
        modifiers: List<SelectedModifier> = emptyList(),
        note: String? = null,
        qty: Int = 1,
    ) {
        _lines.update { current ->
            // If item has no modifiers, combine with existing same item line
            if (modifiers.isEmpty() && note.isNullOrBlank()) {
                val index = current.indexOfFirst { it.item.id == item.id && it.selectedModifiers.isEmpty() && it.note.isNullOrBlank() }
                if (index >= 0) {
                    val existing = current[index]
                    val updated = existing.copy(quantity = existing.quantity + qty)
                    current.toMutableList().apply { set(index, updated) }
                } else {
                    current + CartLine(item = item, quantity = qty, selectedModifiers = modifiers, note = note)
                }
            } else {
                current + CartLine(item = item, quantity = qty, selectedModifiers = modifiers, note = note)
            }
        }
    }

    fun updateQuantity(
        lineId: String,
        newQty: Int,
    ) {
        _lines.update { current ->
            if (newQty <= 0) {
                current.filterNot { it.id == lineId }
            } else {
                current.map { if (it.id == lineId) it.copy(quantity = newQty) else it }
            }
        }
    }

    fun removeLine(lineId: String) {
        _lines.update { current -> current.filterNot { it.id == lineId } }
    }

    fun setDiningMode(mode: String) {
        _diningMode.value = mode
    }

    fun setTableTent(tent: String?) {
        _tableTent.value = tent
    }

    fun setCustomer(
        id: String,
        name: String,
    ) {
        _customerId.value = id
        _customerName.value = name
    }

    fun reset() {
        _lines.value = emptyList()
        _diningMode.value = "COMER_AQUI"
        _tableTent.value = null
        _customerId.value = "CF"
        _customerName.value = "Consumidor Final"
    }
}
