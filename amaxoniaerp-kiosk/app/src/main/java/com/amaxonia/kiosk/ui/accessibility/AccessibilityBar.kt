package com.amaxonia.kiosk.ui.accessibility

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Accessible
import androidx.compose.material.icons.rounded.Contrast
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.amaxonia.kiosk.R
import com.amaxonia.kiosk.ui.components.KioskTouchTarget
import com.amaxonia.kiosk.ui.components.kioskPressable

/** Compact top strip with icon pills: low screen (wheelchair), high contrast and language. */
@Composable
fun AccessibilityBar(
    state: AccessibilityState,
    onToggleAccessibleMode: () -> Unit,
    onToggleHighContrast: () -> Unit,
    onToggleLanguage: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = colors.surfaceVariant,
        border = if (state.isHighContrast) BorderStroke(2.dp, colors.onSurface) else null,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            A11yPill(
                icon = Icons.AutoMirrored.Rounded.Accessible,
                label = stringResource(R.string.a11y_reach_mode),
                active = state.isAccessibleMode,
                onClick = onToggleAccessibleMode,
            )
            A11yPill(
                icon = Icons.Rounded.Contrast,
                label = stringResource(R.string.a11y_high_contrast),
                active = state.isHighContrast,
                onClick = onToggleHighContrast,
            )
            Spacer(Modifier.weight(1f))
            A11yPill(
                icon = Icons.Rounded.Translate,
                label = state.language.toggle().shortCode,
                active = false,
                onClick = onToggleLanguage,
                contentDescription = stringResource(R.string.a11y_language),
            )
        }
    }
}

@Composable
private fun A11yPill(
    icon: ImageVector,
    label: String,
    active: Boolean,
    onClick: () -> Unit,
    contentDescription: String? = null,
) {
    val colors = MaterialTheme.colorScheme
    val container by animateColorAsState(if (active) colors.primary else colors.surface, label = "a11y_pill_bg")
    val content = if (active) colors.onPrimary else colors.onSurface
    Surface(
        modifier =
            Modifier
                .kioskPressable(onClick = onClick)
                .semantics {
                    role = Role.Switch
                    selected = active
                },
        shape = RoundedCornerShape(percent = 50),
        color = container,
        contentColor = content,
        border = BorderStroke(2.dp, if (active) colors.primary else colors.outline),
    ) {
        Row(
            modifier = Modifier.height(KioskTouchTarget).padding(horizontal = 28.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = contentDescription, modifier = Modifier.size(36.dp))
            Spacer(Modifier.width(12.dp))
            Text(text = label, style = MaterialTheme.typography.titleSmall)
        }
    }
}
