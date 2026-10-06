package com.amaxonia.kiosk.ui.accessibility

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class KioskLanguage(
    val code: String,
    val displayName: String,
    val shortCode: String,
) {
    SPANISH("es", "Español", "ES"),
    ENGLISH("en", "English", "EN"),
    ;

    fun toggle(): KioskLanguage = if (this == SPANISH) ENGLISH else SPANISH
}

data class AccessibilityState(
    val isAccessibleMode: Boolean = false,
    val isHighContrast: Boolean = false,
    val language: KioskLanguage = KioskLanguage.SPANISH,
)

class AccessibilityManager {
    private val _state = MutableStateFlow(AccessibilityState())
    val state: StateFlow<AccessibilityState> = _state.asStateFlow()

    fun toggleAccessibleMode() {
        _state.update { it.copy(isAccessibleMode = !it.isAccessibleMode) }
    }

    fun setAccessibleMode(enabled: Boolean) {
        _state.update { it.copy(isAccessibleMode = enabled) }
    }

    fun toggleHighContrast() {
        _state.update { it.copy(isHighContrast = !it.isHighContrast) }
    }

    fun setHighContrast(enabled: Boolean) {
        _state.update { it.copy(isHighContrast = enabled) }
    }

    fun toggleLanguage() {
        _state.update { it.copy(language = it.language.toggle()) }
    }

    fun setLanguage(language: KioskLanguage) {
        _state.update { it.copy(language = language) }
    }

    fun reset() {
        _state.value = AccessibilityState()
    }
}
