package com.amaxonia.pos.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amaxonia.pos.ui.common.components.AdaptiveAmountOptions
import com.amaxonia.pos.ui.common.components.AdaptiveAmountText
import com.amaxonia.pos.ui.theme.PosPalette

/** Pesos del teclado numérico manual: columnas de números y botón Cobrar (ENTER). */
private const val KEYPAD_NUMBERS_WEIGHT = 3f
private const val CHARGE_BUTTON_WEIGHT = 3f

/** Contenido de la pantalla de ingreso manual de monto. */
@Composable
fun ManualEntryContent(
    currentValue: String,
    onKeyClick: (String) -> Unit,
    onClearClick: () -> Unit,
    onBackspaceClick: () -> Unit,
    onEnterClick: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ManualEntryTitle(
            modifier =
                Modifier
                    .align(Alignment.Start)
                    .padding(bottom = 24.dp),
        )
        ManualEntryAmount(currentValue = currentValue)

        // Teclado Numérico (el bottomBar ya reserva su espacio vía paddingValues;
        // sin padding extra que desperdicie pantalla).
        Row(
            modifier =
                Modifier
                    .weight(1f)
                    .padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            KeypadNumbersColumn(
                modifier = Modifier.weight(KEYPAD_NUMBERS_WEIGHT),
                onKeyClick = onKeyClick,
                onClearClick = onClearClick,
            )
            KeypadActionsColumn(
                modifier = Modifier.weight(1f),
                onBackspaceClick = onBackspaceClick,
                onEnterClick = onEnterClick,
            )
        }
    }
}

/** Título de la sección de ingreso manual. */
@Composable
private fun ManualEntryTitle(modifier: Modifier = Modifier) {
    Text(
        text = "Ingreso Manual",
        style =
            TextStyle(
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            ),
        modifier = modifier,
    )
}

/** Pantalla del precio (Visor) — monto adaptive para que importes largos no se corten. */
@Composable
private fun ManualEntryAmount(currentValue: String) {
    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = "Monto a cobrar",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(4.dp))
            AdaptiveAmountText(
                text = if (currentValue.isEmpty()) "$ 0.00" else "$ $currentValue",
                baseStyle =
                    TextStyle(
                        fontSize = 36.sp,
                        fontWeight = FontWeight.ExtraBold,
                    ),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.fillMaxWidth(),
                options =
                    AdaptiveAmountOptions(
                        minFontSizeSp = 18f,
                        maxLines = 1,
                    ),
            )
        }
    }
}

/** Columna izquierda del teclado (Números). */
@Composable
private fun KeypadNumbersColumn(
    modifier: Modifier = Modifier,
    onKeyClick: (String) -> Unit,
    onClearClick: () -> Unit,
) {
    val keys =
        listOf(
            listOf("1", "2", "3"),
            listOf("4", "5", "6"),
            listOf("7", "8", "9"),
            listOf("C", "0", "000"),
        )

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        keys.forEach { row ->
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                row.forEach { key ->
                    KeypadKeyButton(
                        key = key,
                        onKeyClick = onKeyClick,
                        onClearClick = onClearClick,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

/** Tecla individual del teclado numérico. */
@Composable
private fun KeypadKeyButton(
    key: String,
    onKeyClick: (String) -> Unit,
    onClearClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = {
            if (key == "C") onClearClick() else onKeyClick(key)
        },
        shape = MaterialTheme.shapes.small,
        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp),
        contentPadding = PaddingValues(0.dp),
        modifier =
            modifier
                .fillMaxHeight(),
    ) {
        Text(
            text = key,
            fontSize = if (key == "000") 20.sp else 24.sp,
            fontWeight = FontWeight.Bold,
            color = if (key == "C") PosPalette.DangerRed else MaterialTheme.colorScheme.primary,
        )
    }
}

/** Columna derecha del teclado (Acciones). */
@Composable
private fun KeypadActionsColumn(
    modifier: Modifier = Modifier,
    onBackspaceClick: () -> Unit,
    onEnterClick: () -> Unit,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // Botón Borrar
        Button(
            onClick = onBackspaceClick,
            shape = MaterialTheme.shapes.small,
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp),
            modifier =
                Modifier
                    .weight(1f)
                    .fillMaxWidth(),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Backspace,
                contentDescription = "Borrar",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(26.dp),
            )
        }

        // Botón ENTER
        Button(
            onClick = onEnterClick,
            shape = MaterialTheme.shapes.small,
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp),
            modifier =
                Modifier
                    .weight(CHARGE_BUTTON_WEIGHT)
                    .fillMaxWidth(),
        ) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = "Cobrar",
                tint = PosPalette.FixedWhite,
                modifier = Modifier.size(32.dp),
            )
        }
    }
}
