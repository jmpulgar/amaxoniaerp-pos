package com.amaxonia.pos.ui.common.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Shared quantity stepper used by the Dashboard quantity/promotion sheets and the Cart line-item
 * editor (previously two near-identical private composables).
 */
@Composable
fun QuantityStepper(
    quantityText: String,
    onQuantityTextChange: (String) -> Unit,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
    onDone: () -> Unit,
    isError: Boolean,
    modifier: Modifier = Modifier,
    label: String = "Cantidad",
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        IconButton(
            onClick = onDecrease,
            modifier =
                Modifier
                    .size(44.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f), MaterialTheme.shapes.small),
        ) {
            Icon(Icons.Default.Remove, contentDescription = "Disminuir cantidad", tint = MaterialTheme.colorScheme.primary)
        }
        OutlinedTextField(
            value = quantityText,
            onValueChange = onQuantityTextChange,
            label = { Text(label) },
            singleLine = true,
            isError = isError,
            textStyle = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Bold),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onDone() }),
            modifier = Modifier.weight(1f),
        )
        IconButton(
            onClick = onIncrease,
            modifier =
                Modifier
                    .size(44.dp)
                    .background(MaterialTheme.colorScheme.primary, MaterialTheme.shapes.small),
        ) {
            Icon(Icons.Default.Add, contentDescription = "Aumentar cantidad", tint = MaterialTheme.colorScheme.onPrimary)
        }
    }
}
