package com.amaxonia.kiosk.ui.customizer

import androidx.lifecycle.ViewModel
import com.amaxonia.kiosk.core.money.Money
import com.amaxonia.kiosk.core.network.KioskCurrencyConfig
import com.amaxonia.kiosk.core.network.KioskItemDto
import com.amaxonia.kiosk.core.network.KioskModifierGroupDto
import com.amaxonia.kiosk.core.network.KioskModifierOptionDto
import com.amaxonia.kiosk.domain.cart.OrderGraph
import com.amaxonia.kiosk.domain.cart.SelectedModifier
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

private const val MAX_NOTE_LENGTH = 80

data class CustomizerUiState(
    val item: KioskItemDto,
    val quantity: Int = 1,
    val selectedOptions: Map<Int, List<KioskModifierOptionDto>> = emptyMap(),
    val note: String = "",
    val currency: KioskCurrencyConfig = KioskCurrencyConfig(),
    val validationErrors: Map<Int, String> = emptyMap(),
) {
    val unitPrice: Money
        get() {
            val base = Money.fromString(item.price)
            val extras =
                selectedOptions.values.flatten().fold(Money.ZERO) { acc, opt ->
                    acc + Money.fromString(opt.extraPrice)
                }
            return base + extras
        }

    val totalPrice: Money
        get() = unitPrice * quantity

    val isValid: Boolean
        get() =
            item.modifierGroups.all { group ->
                val count = selectedOptions[group.id]?.size ?: 0
                count >= group.min && count <= group.max
            }
}

class ProductCustomizerViewModel(
    val item: KioskItemDto,
    private val orderGraph: OrderGraph,
) : ViewModel() {
    private val _uiState =
        MutableStateFlow(
            CustomizerUiState(
                item = item,
                currency = orderGraph.currencyConfig.value,
                selectedOptions = initialSelections(item),
            ),
        )
    val uiState: StateFlow<CustomizerUiState> = _uiState.asStateFlow()

    fun toggleOption(
        group: KioskModifierGroupDto,
        option: KioskModifierOptionDto,
    ) {
        if (option.soldOut) return

        _uiState.update { current ->
            val currentGroupSelections = current.selectedOptions[group.id].orEmpty()
            val isAlreadySelected = currentGroupSelections.any { it.id == option.id }

            val updatedGroupList =
                if (group.max == 1) {
                    if (isAlreadySelected && group.min == 0) {
                        emptyList()
                    } else {
                        listOf(option)
                    }
                } else {
                    if (isAlreadySelected) {
                        currentGroupSelections.filterNot { it.id == option.id }
                    } else if (currentGroupSelections.size < group.max) {
                        currentGroupSelections + option
                    } else {
                        currentGroupSelections
                    }
                }

            val updatedSelections = current.selectedOptions.toMutableMap()
            if (updatedGroupList.isEmpty()) {
                updatedSelections.remove(group.id)
            } else {
                updatedSelections[group.id] = updatedGroupList
            }

            val updatedErrors = current.validationErrors.toMutableMap()
            val count = updatedGroupList.size
            if (count in group.min..group.max) {
                updatedErrors.remove(group.id)
            }

            current.copy(
                selectedOptions = updatedSelections,
                validationErrors = updatedErrors,
            )
        }
    }

    fun incrementQuantity() {
        _uiState.update { it.copy(quantity = it.quantity + 1) }
    }

    fun decrementQuantity() {
        _uiState.update {
            if (it.quantity > 1) it.copy(quantity = it.quantity - 1) else it
        }
    }

    fun onNoteChanged(note: String) {
        _uiState.update { it.copy(note = note.take(MAX_NOTE_LENGTH)) }
    }

    fun addToCart(onSuccess: () -> Unit) {
        val current = _uiState.value
        val errors = mutableMapOf<Int, String>()

        for (group in item.modifierGroups) {
            val count = current.selectedOptions[group.id]?.size ?: 0
            if (count < group.min) {
                errors[group.id] =
                    if (group.min == 1 && group.max == 1) {
                        "Selección obligatoria (Elige 1)"
                    } else {
                        "Debe seleccionar al menos ${group.min}"
                    }
            }
        }

        if (errors.isNotEmpty()) {
            _uiState.update { it.copy(validationErrors = errors) }
            return
        }

        val allModifiers =
            current.selectedOptions.values.flatten().map { opt ->
                SelectedModifier(
                    optionId = opt.id,
                    optionName = opt.name,
                    extraPrice = Money.fromString(opt.extraPrice),
                )
            }

        orderGraph.addLine(
            item = item,
            modifiers = allModifiers,
            note = current.note.ifBlank { null },
            qty = current.quantity,
        )

        onSuccess()
    }

    private companion object {
        fun initialSelections(item: KioskItemDto): Map<Int, List<KioskModifierOptionDto>> {
            val map = mutableMapOf<Int, List<KioskModifierOptionDto>>()
            for (group in item.modifierGroups) {
                // Options flagged as default in the Combos module come preselected (never more than max).
                val defaults = group.options.filter { it.isDefault && !it.soldOut }.take(group.max.coerceAtLeast(0))
                if (defaults.isNotEmpty()) {
                    map[group.id] = defaults
                } else if (group.isMandatory && group.min == 1 && group.max == 1) {
                    // Mandatory single choice without a default: auto-select the first available option.
                    group.options.firstOrNull { !it.soldOut }?.let { map[group.id] = listOf(it) }
                }
            }
            return map
        }
    }
}
