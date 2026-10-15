package com.amaxonia.erp.ui.pos.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.amaxonia.erp.ui.components.isLargeScreen
import com.amaxonia.erp.ui.pos.CartItem
import com.amaxonia.erp.ui.theme.FlowBrandBlue
import com.amaxonia.erp.ui.theme.FlowTableBorder
import com.amaxonia.erp.ui.theme.FlowTableTextDark
import com.amaxonia.erp.ui.theme.FlowTableTextMuted
import java.util.Locale

private val QUICK_KITCHEN_NOTES = listOf(
    "Sin cebolla",
    "Sin sal",
    "Sin hielo",
    "Picante",
    "Término medio",
    "Bien cocido",
    "Para llevar",
    "Alergia",
)

/**
 * Diálogo de Nota de Cocina para una línea de producto en el POS.
 * Fiel al diseño de Flow UI (flow-app.css .rest-modal y main.js Rest__editarNota).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PosKitchenNoteDialog(
    item: CartItem,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var noteText by remember { mutableStateOf(item.kitchenNote) }
    val isLarge = isLargeScreen()

    val qtyText = if (item.quantity % 1.0 == 0.0) {
        String.format(Locale.US, "%.0f", item.quantity)
    } else {
        String.format(Locale.US, "%.2f", item.quantity)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier
                .widthIn(min = 340.dp, max = if (isLarge) 520.dp else 440.dp)
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            color = Color.White,
            tonalElevation = 6.dp,
            shadowElevation = 12.dp,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(if (isLarge) 24.dp else 18.dp),
            ) {
                // Cabecera: Icono + Título + Subtítulo + Botón Cerrar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(if (isLarge) 42.dp else 36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFFFFF7ED))
                                .border(1.dp, Color(0xFFFED7AA), RoundedCornerShape(10.dp)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.ChatBubbleOutline,
                                contentDescription = "Nota de cocina",
                                tint = Color(0xFFEA580C),
                                modifier = Modifier.size(if (isLarge) 22.dp else 18.dp),
                            )
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = item.product.description,
                                fontSize = if (isLarge) 16.sp else 14.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = FlowTableTextDark,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Cantidad: $qtyText ${item.product.unitPackage.ifBlank { "UND" }} • ${item.product.code.ifBlank { "Línea #${item.cartItemId.take(4)}" }}",
                                fontSize = if (isLarge) 12.sp else 11.sp,
                                color = FlowTableTextMuted,
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(32.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Cerrar",
                            tint = Color(0xFF94A3B8),
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Etiqueta del campo
                Text(
                    text = "Nota para cocina o preparación:",
                    fontSize = if (isLarge) 13.sp else 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF334155),
                )

                Spacer(modifier = Modifier.height(6.dp))

                // Campo de texto con límite de 150 caracteres
                OutlinedTextField(
                    value = noteText,
                    onValueChange = {
                        if (it.length <= 150) {
                            noteText = it
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = {
                        Text(
                            text = "Punto de cocción, ingredientes, alergias…",
                            fontSize = if (isLarge) 13.sp else 12.sp,
                            color = Color(0xFF94A3B8),
                        )
                    },
                    minLines = 3,
                    maxLines = 4,
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFFEA580C),
                        unfocusedBorderColor = FlowTableBorder,
                        focusedContainerColor = Color(0xFFFFFBF7),
                        unfocusedContainerColor = Color(0xFFFAFAFA),
                        focusedTextColor = FlowTableTextDark,
                        unfocusedTextColor = FlowTableTextDark,
                    ),
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp, bottom = 12.dp),
                    horizontalArrangement = Arrangement.End,
                ) {
                    Text(
                        text = "${noteText.length}/150",
                        fontSize = 10.5.sp,
                        color = if (noteText.length > 140) Color(0xFFDC2626) else Color(0xFF94A3B8),
                        fontWeight = FontWeight.Medium,
                    )
                }

                // Chips de notas rápidas predefinidas (igual que en el web)
                Text(
                    text = "Opciones rápidas:",
                    fontSize = if (isLarge) 12.sp else 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = FlowTableTextMuted,
                )

                Spacer(modifier = Modifier.height(6.dp))

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    QUICK_KITCHEN_NOTES.forEach { chip ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFFF1F5F9))
                                .border(0.5.dp, Color(0xFFE2E8F0), RoundedCornerShape(8.dp))
                                .clickable {
                                    val trimmed = noteText.trim()
                                    val next = if (trimmed.isEmpty()) {
                                        chip
                                    } else if (!trimmed.contains(chip, ignoreCase = true)) {
                                        "$trimmed, $chip"
                                    } else {
                                        trimmed
                                    }
                                    if (next.length <= 150) {
                                        noteText = next
                                    }
                                }
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                        ) {
                            Text(
                                text = "+ $chip",
                                fontSize = if (isLarge) 11.5.sp else 10.5.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFF475569),
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Botones de acción inferiores
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    if (item.kitchenNote.isNotBlank()) {
                        OutlinedButton(
                            onClick = {
                                onSave("")
                                onDismiss()
                            },
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = Color(0xFFDC2626),
                            ),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFECDD3)),
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.DeleteOutline,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Quitar nota",
                                fontSize = if (isLarge) 12.sp else 11.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    } else {
                        OutlinedButton(
                            onClick = onDismiss,
                            shape = RoundedCornerShape(8.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFCBD5E1)),
                        ) {
                            Text(
                                text = "Cancelar",
                                fontSize = if (isLarge) 12.sp else 11.sp,
                                color = Color(0xFF64748B),
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }

                    Button(
                        onClick = {
                            onSave(noteText.trim())
                            onDismiss()
                        },
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = FlowBrandBlue,
                        ),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Check,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Guardar nota",
                            fontSize = if (isLarge) 12.5.sp else 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                        )
                    }
                }
            }
        }
    }
}
