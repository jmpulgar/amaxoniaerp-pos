package com.amaxonia.kiosk.ui.accessibility

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * App chrome around every screen: the "pantalla baja" container that lowers the content and, on
 * ordering screens, the slim accessibility footer at the very bottom (as on fast-food self-order
 * kiosks). Shared by the activity and screenshot tests.
 */
@Composable
fun KioskChrome(
    state: AccessibilityState,
    isOrderingScreen: Boolean,
    actions: AccessibilityActions,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(modifier = modifier.fillMaxSize()) {
        AccessibleContainer(
            isAccessibleMode = isOrderingScreen && state.isAccessibleMode,
            modifier = Modifier.weight(1f),
            content = content,
        )
        if (isOrderingScreen) {
            AccessibilityBar(
                state = state,
                onToggleAccessibleMode = actions.onToggleAccessibleMode,
                onToggleLanguage = actions.onToggleLanguage,
            )
        }
    }
}
