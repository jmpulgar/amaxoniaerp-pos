package com.amaxonia.pos.ui.common.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.amaxonia.pos.ui.theme.PosTextStyles

private val KEY_ROWS =
    listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9"),
        listOf("C", "0", "."),
    )

/**
 * Reusable POS numeric keypad: a 3x4 digit grid plus a caller-supplied action column
 * (e.g. backspace + a "cobrar"/confirm button). Emits the raw key string ("0"-"9", ".", "C", "BACK")
 * matching the existing PaymentUiAction.KeyPadInput contract.
 *
 * Callers may opt out of the action column with [actionColumn] = null and supply a full-width
 * primary action below the keypad instead, via [belowKeypad].
 */
@Composable
fun Keypad(
    onKey: (String) -> Unit,
    modifier: Modifier = Modifier,
    height: Dp? = 320.dp,
    actionColumn: (@Composable ColumnScope.() -> Unit)? = null,
    belowKeypad: (@Composable () -> Unit)? = null,
) {
    val sizeModifier = if (height != null) Modifier.height(height) else Modifier.fillMaxHeight()
    Column(modifier = modifier.fillMaxWidth().then(sizeModifier)) {
        Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
            // Numeric grid takes the whole width when there's no action column, or 3/4 when callers
            // still want the legacy backspace/confirm column.
            val gridWeight = if (actionColumn == null) 1f else KEY_GRID_WEIGHT
            Column(modifier = Modifier.weight(gridWeight)) {
                KEY_ROWS.forEach { rowKeys ->
                    Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        rowKeys.forEach { key ->
                            KeypadKey(key, Modifier.weight(1f)) { onKey(key) }
                        }
                    }
                }
            }
            if (actionColumn != null) {
                Column(modifier = Modifier.weight(ACTION_COLUMN_WEIGHT), content = actionColumn)
            }
        }
        belowKeypad?.invoke()
    }
}

/**
 * Single keypad key. Filled surface at rest with a hairline outline; animates to a
 * primary-tinted container while pressed for clear tactile feedback. Enforces a 44dp
 * minimum touch target (the row/column weights drive the actual size above that floor).
 */
@Composable
fun KeypadKey(
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val containerColor =
        if (pressed) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surface
        }
    val contentColor =
        if (pressed) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSurface
        }
    val borderColor =
        if (pressed) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
        } else {
            MaterialTheme.colorScheme.outlineVariant
        }
    Box(
        modifier =
            modifier
                .padding(5.dp)
                .fillMaxHeight()
                .defaultMinSize(minHeight = 44.dp)
                .clip(MaterialTheme.shapes.medium)
                .background(containerColor)
                .border(1.dp, borderColor, MaterialTheme.shapes.medium)
                .clickable(
                    interactionSource = interactionSource,
                    indication = ripple(),
                    onClick = onClick,
                ),
        contentAlignment = Alignment.Center,
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = text,
                tint = contentColor,
                modifier = Modifier.padding(4.dp),
            )
        } else {
            Text(
                text = text,
                style = PosTextStyles.keypadKey,
                color = contentColor,
            )
        }
    }
}

/**
 * Amount display shown above the keypad: label + hero amount (tabular figures, auto-shrinks
 * to fit) + optional secondary-currency line + optional error/positive border. Extra
 * business-specific rows (e.g. an "insufficient amount" reminder) are passed via [extraContent].
 */
@Composable
@Suppress("LongParameterList")
fun KeypadDisplay(
    label: String,
    amountText: String,
    modifier: Modifier = Modifier,
    currencyPrefix: String = "$ ",
    isError: Boolean = false,
    isPositive: Boolean = false,
    secondaryLine: String? = null,
    extraContent: @Composable ColumnScope.() -> Unit = {},
) {
    val borderColor =
        when {
            isError -> MaterialTheme.colorScheme.error
            isPositive -> MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
            else -> MaterialTheme.colorScheme.outlineVariant
        }
    val borderWidth = if (isError || isPositive) 1.5.dp else 1.dp
    val containerColor =
        when {
            isError -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.30f)
            isPositive -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.40f)
            else -> MaterialTheme.colorScheme.surface
        }
    val amountColor =
        if (isError) {
            MaterialTheme.colorScheme.error
        } else {
            MaterialTheme.colorScheme.primary
        }
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.medium)
                .background(containerColor)
                .border(borderWidth, borderColor, MaterialTheme.shapes.medium)
                .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(2.dp))
        AdaptiveAmountText(
            text = "$currencyPrefix$amountText",
            baseStyle = PosTextStyles.totalDisplay,
            color = amountColor,
            modifier = Modifier.fillMaxWidth(),
            minFontSizeSp = 18f,
        )
        if (secondaryLine != null) {
            Text(
                secondaryLine,
                style = PosTextStyles.amountSecondary,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        extraContent()
    }
}

private const val KEY_GRID_WEIGHT = 3f
private const val ACTION_COLUMN_WEIGHT = 1f
