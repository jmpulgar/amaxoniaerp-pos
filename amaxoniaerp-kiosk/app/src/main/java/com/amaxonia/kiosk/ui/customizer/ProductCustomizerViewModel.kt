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
    /** Wizard step: one per modifier group, then the "Revisar orden" step at [reviewStep]. */
    val currentStep: Int = 0,
    /** Furthest step the customer has reached; every step up to it can be revisited from the step list. */
    val furthestStep: Int = 0,
    /** The current step was left without meeting its minimum ("Siguiente" pressed too early). */
    val showStepError: Boolean = false,
) {
    val reviewStep: Int
        get() = item.modifierGroups.size

    val stepCount: Int
        get() = reviewStep + 1

    val isOnReviewStep: Boolean
        get() = currentStep == reviewStep

    fun isGroupSatisfied(group: KioskModifierGroupDto): Boolean = (selectedOptions[group.id]?.size ?: 0) in group.min..group.max

    /** Done = already visited and its selection is valid (shown with a check in the step list). */
    fun isStepDone(index: Int): Boolean =
        index < currentStep.coerceAtLeast(furthestStep) &&
            item.modifierGroups.getOrNull(index)?.let(::isGroupSatisfied) == true

    /** Steps up to the furthest reached, or any step whose previous groups are all valid. */
    fun canGoToStep(index: Int): Boolean =
        index in 0..reviewStep &&
            (index <= furthestStep || item.modifierGroups.take(index).all(::isGroupSatisfied))

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
                showStepError = false,
            )
        }
    }

    /**
     * Jumps to [index] from the step list or a "Cambiar" link on the review step. Only steps already
     * reached (or reachable because every previous group is valid) are allowed; selections are kept.
     */
    fun goToStep(index: Int): Boolean {
        if (!_uiState.value.canGoToStep(index)) return false
        _uiState.update {
            it.copy(currentStep = index, furthestStep = maxOf(it.furthestStep, index), showStepError = false)
        }
        return true
    }

    /** "Siguiente": moves on only when the current group meets its minimum; otherwise flags the error. */
    fun nextStep(): Boolean {
        val state = _uiState.value
        val group = state.item.modifierGroups.getOrNull(state.currentStep) ?: return false
        val satisfied = state.isGroupSatisfied(group)
        if (satisfied) {
            val target = state.currentStep + 1
            _uiState.update { it.copy(currentStep = target, furthestStep = maxOf(it.furthestStep, target), showStepError = false) }
        } else {
            _uiState.update { it.copy(showStepError = true) }
        }
        return satisfied
    }

    /** Auto-advance after a single-choice pick, unless the customer already moved to another step. */
    fun advanceFrom(step: Int) {
        if (_uiState.value.currentStep == step) nextStep()
    }

    /** "Atrás": the previous step, with its selections intact. False on the first step. */
    fun previousStep(): Boolean {
        val state = _uiState.value
        if (state.currentStep == 0) return false
        _uiState.update { it.copy(currentStep = it.currentStep - 1, showStepError = false) }
        return true
    }

    /** Opens the wizard directly on [step] (e.g. screenshots of the review step). */
    fun startAt(step: Int) {
        val target = step.coerceIn(0, _uiState.value.reviewStep)
        _uiState.update { it.copy(currentStep = target, furthestStep = maxOf(it.furthestStep, target), showStepError = false) }
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
            // Send the customer back to the first incomplete group, with the error shown.
            val firstInvalid = item.modifierGroups.indexOfFirst { it.id in errors }
            _uiState.update {
                it.copy(
                    validationErrors = errors,
                    currentStep = if (firstInvalid >= 0) firstInvalid else it.currentStep,
                    showStepError = true,
                )
            }
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
