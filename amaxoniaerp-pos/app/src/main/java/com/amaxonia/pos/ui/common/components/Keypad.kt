package com.amaxonia.pos.ui.common.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
 * The action column historically consumed 1/4 of the horizontal space which forced the COBRAR
 * button into a vertical, wrap-prone layout. Callers may now opt out of the action column with
 * [compactActions] = false and supply a full-width primary action below the keypad instead, via
 * [belowKeypad].
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
                    Row(modifier = Modifier.weight(1f)) {
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

@Composable
fun KeypadKey(
    text: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier =
            modifier
                .fillMaxHeight()
                .padding(4.dp)
                .defaultMinSize(minHeight = 48.dp)
                .background(MaterialTheme.colorScheme.surface, MaterialTheme.shapes.small)
                .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = PosTextStyles.keypadKey, color = MaterialTheme.colorScheme.onSurface)
    }
}

/**
 * Amount display shown above the keypad: label + hero amount (tabular figures) + optional
 * secondary-currency line + optional error border. Extra business-specific rows (e.g. an
 * "insufficient amount" reminder) are passed via [extraContent].
 */
@Composable
// Parámetros mantienen slot Compose y opciones usadas por pantallas existentes.
@Suppress("LongParameterList")
fun KeypadDisplay(
    label: String,
    amountText: String,
    modifier: Modifier = Modifier,
    currencyPrefix: String = "$ ",
    isError: Boolean = false,
    secondaryLine: String? = null,
    extraContent: @Composable ColumnScope.() -> Unit = {},
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface, MaterialTheme.shapes.small)
                .border(
                    width = if (isError) 2.dp else 0.dp,
                    color = MaterialTheme.colorScheme.error,
                    shape = MaterialTheme.shapes.small,
                ).padding(16.dp),
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(currencyPrefix, style = PosTextStyles.totalDisplay, color = MaterialTheme.colorScheme.primary)
            Text(amountText, style = PosTextStyles.totalDisplay, color = MaterialTheme.colorScheme.primary)
        }
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
