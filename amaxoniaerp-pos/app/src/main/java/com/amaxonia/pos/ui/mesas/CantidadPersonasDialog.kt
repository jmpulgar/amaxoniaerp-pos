package com.amaxonia.pos.ui.mesas

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

private const val DEFAULT_CANTIDAD_PERSONAS = 2
private const val MIN_CANTIDAD_PERSONAS = 1
private const val MAX_CANTIDAD_PERSONAS = 99

/**
 * Solicita la cantidad de personas antes de abrir la sesión de la mesa. El backend valida
 * `cantidad_personas >= 1`; aquí acotamos a 1..99 para evitar inputs absurdos por tecleo.
 *
 * - `mesaLabel`: texto informativo con el nombre/código de la mesa seleccionada.
 * - `onConfirm(cantidad)`: la cantidad solo se emite cuando es válida (1..99).
 * - `onDismiss`: al cancelar o cerrar afuera (NO abre nada).
 */
@Composable
fun CantidadPersonasDialog(
    mesaLabel: String,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var cantidadText by remember { mutableStateOf(DEFAULT_CANTIDAD_PERSONAS.toString()) }
    var cantidadError by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Abrir sesión de mesa") },
        text = {
            Column(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                Text(
                    text = mesaLabel,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = cantidadText,
                    onValueChange = { newValue ->
                        // Acepta solo dígitos para mantener el input limpio.
                        val digits = newValue.filter { it.isDigit() }
                        cantidadText = digits
                        cantidadError = null
                    },
                    label = { Text("Cantidad de personas") },
                    singleLine = true,
                    isError = cantidadError != null,
                    supportingText = cantidadError?.let { { Text(it) } },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val parsed = cantidadText.toIntOrNull()
                    when {
                        parsed == null -> cantidadError = "Ingresa un número válido"
                        parsed < MIN_CANTIDAD_PERSONAS -> cantidadError = "Mínimo $MIN_CANTIDAD_PERSONAS persona"
                        parsed > MAX_CANTIDAD_PERSONAS -> cantidadError = "Máximo $MAX_CANTIDAD_PERSONAS personas"
                        else -> {
                            cantidadError = null
                            onConfirm(parsed)
                        }
                    }
                },
            ) { Text("Abrir sesión") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        },
    )
}
