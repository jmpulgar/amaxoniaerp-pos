package com.amaxonia.kiosk.ui.accessibility

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * App chrome around every screen: the accessibility strip (ordering screens only) and the
 * "pantalla baja" container that lowers the content. Shared by the activity and screenshot tests.
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
        if (isOrderingScreen) {
            AccessibilityBar(
                state = state,
                onToggleAccessibleMode = actions.onToggleAccessibleMode,
                onToggleHighContrast = actions.onToggleHighContrast,
                onToggleLanguage = actions.onToggleLanguage,
            )
        }
        AccessibleContainer(
            isAccessibleMode = isOrderingScreen && state.isAccessibleMode,
            modifier = Modifier.weight(1f),
            content = content,
        )
    }
}
