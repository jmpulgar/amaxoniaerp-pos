package com.amaxonia.pos.ui.common.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amaxonia.pos.ui.theme.PosTextStyles

/**
 * Teclado numérico táctil tipo POS de 4 columnas y 4 filas:
 * - Fila 1: 1 | 2 | 3 | [ Borrar / Backspace ] (2 filas de alto)
 * - Fila 2: 4 | 5 | 6 | (extensión Backspace)
 * - Fila 3: 7 | 8 | 9 | ENTER (2 filas de alto)
 * - Fila 4: C | 0 | . | (extensión ENTER)
 */
@Composable
fun Keypad(
    onKey: (String) -> Unit,
    modifier: Modifier = Modifier,
    onEnter: (() -> Unit)? = null,
    isProcessing: Boolean = false,
    isInsufficient: Boolean = false,
    height: Dp? = 320.dp,
) {
    val sizeModifier = if (height != null) Modifier.height(height) else Modifier.fillMaxHeight()

    Row(
        modifier = modifier.fillMaxWidth().then(sizeModifier),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        // Columnas 1, 2 y 3 (dígitos y acciones)
        Column(
            modifier = Modifier.weight(3f).fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            // Fila 1: 1, 2, 3
            Row(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                KeypadDigitKey(digit = "1", modifier = Modifier.weight(1f)) { onKey("1") }
                KeypadDigitKey(digit = "2", modifier = Modifier.weight(1f)) { onKey("2") }
                KeypadDigitKey(digit = "3", modifier = Modifier.weight(1f)) { onKey("3") }
            }
            // Fila 2: 4, 5, 6
            Row(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                KeypadDigitKey(digit = "4", modifier = Modifier.weight(1f)) { onKey("4") }
                KeypadDigitKey(digit = "5", modifier = Modifier.weight(1f)) { onKey("5") }
                KeypadDigitKey(digit = "6", modifier = Modifier.weight(1f)) { onKey("6") }
            }
            // Fila 3: 7, 8, 9
            Row(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                KeypadDigitKey(digit = "7", modifier = Modifier.weight(1f)) { onKey("7") }
                KeypadDigitKey(digit = "8", modifier = Modifier.weight(1f)) { onKey("8") }
                KeypadDigitKey(digit = "9", modifier = Modifier.weight(1f)) { onKey("9") }
            }
            // Fila 4: C, 0, .
            Row(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                KeypadClearKey(modifier = Modifier.weight(1f)) { onKey("C") }
                KeypadDigitKey(digit = "0", modifier = Modifier.weight(1f)) { onKey("0") }
                KeypadDigitKey(digit = ".", modifier = Modifier.weight(1f)) { onKey(".") }
            }
        }

        // Columna 4: Backspace (filas 1-2) y ENTER (filas 3-4)
        Column(
            modifier = Modifier.weight(1.15f).fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            KeypadBackspaceKey(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                onClick = { onKey("BACK") },
            )
            KeypadEnterKey(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                isProcessing = isProcessing,
                isInsufficient = isInsufficient,
                onClick = { onEnter?.invoke() ?: onKey("ENTER") },
            )
        }
    }
}

/** Botón numérico tipo tarjeta: fondo muy claro, bordes sutiles, esquinas redondeadas táctiles. */
@Composable
fun KeypadDigitKey(
    digit: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val shape = RoundedCornerShape(14.dp)

    val containerColor =
        if (pressed) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.8f)
        } else {
            MaterialTheme.colorScheme.surface
        }
    val contentColor =
        if (pressed) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onSurface
        }

    Surface(
        modifier =
            modifier
                .fillMaxHeight()
                .defaultMinSize(minHeight = 48.dp)
                .clip(shape)
                .clickable(
                    interactionSource = interactionSource,
                    indication = ripple(),
                    onClick = onClick,
                ),
        shape = shape,
        color = containerColor,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
        shadowElevation = if (pressed) 0.dp else 1.5.dp,
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = digit,
                style =
                    MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 24.sp,
                    ),
                color = contentColor,
            )
        }
    }
}

/** Botón C (Clear) para limpiar el monto introducido. */
@Composable
fun KeypadClearKey(
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val shape = RoundedCornerShape(14.dp)

    val containerColor =
        if (pressed) {
            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.70f)
        } else {
            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f)
        }
    val contentColor = MaterialTheme.colorScheme.error

    Surface(
        modifier =
            modifier
                .fillMaxHeight()
                .defaultMinSize(minHeight = 48.dp)
                .clip(shape)
                .clickable(
                    interactionSource = interactionSource,
                    indication = ripple(),
                    onClick = onClick,
                ),
        shape = shape,
        color = containerColor,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.25f)),
        shadowElevation = if (pressed) 0.dp else 1.dp,
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "C",
                style =
                    MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 22.sp,
                    ),
                color = contentColor,
            )
        }
    }
}

/** Botón de borrado (Backspace) con icono centrado y borde/estilo sutil (ocupa 2 filas de alto). */
@Composable
fun KeypadBackspaceKey(
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val shape = RoundedCornerShape(14.dp)

    val containerColor =
        if (pressed) {
            MaterialTheme.colorScheme.surfaceVariant
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.60f)
        }
    val contentColor =
        if (pressed) {
            MaterialTheme.colorScheme.onSurface
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        }

    Surface(
        modifier =
            modifier
                .fillMaxHeight()
                .defaultMinSize(minHeight = 48.dp)
                .clip(shape)
                .clickable(
                    interactionSource = interactionSource,
                    indication = ripple(),
                    onClick = onClick,
                ),
        shape = shape,
        color = containerColor,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
        shadowElevation = if (pressed) 0.dp else 1.dp,
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Backspace,
                contentDescription = "Borrar",
                tint = contentColor,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

/** Botón primario ENTER en color primario sólido con texto blanco en mayúsculas (ocupa 2 filas de alto). */
@Composable
fun KeypadEnterKey(
    modifier: Modifier = Modifier,
    isProcessing: Boolean = false,
    isInsufficient: Boolean = false,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(14.dp)

    val containerColor =
        if (isInsufficient) {
            MaterialTheme.colorScheme.error
        } else {
            MaterialTheme.colorScheme.primary
        }
    val contentColor = MaterialTheme.colorScheme.onPrimary

    Surface(
        modifier =
            modifier
                .fillMaxHeight()
                .defaultMinSize(minHeight = 48.dp)
                .clip(shape)
                .clickable(
                    enabled = !isProcessing,
                    interactionSource = interactionSource,
                    indication = ripple(),
                    onClick = onClick,
                ),
        shape = shape,
        color = containerColor,
        shadowElevation = 3.5.dp,
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            if (isProcessing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(26.dp),
                    strokeWidth = 3.dp,
                    color = contentColor,
                )
            } else {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        tint = contentColor,
                        modifier = Modifier.size(24.dp),
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "ENTER",
                        style =
                            MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 1.2.sp,
                                fontSize = 13.sp,
                            ),
                        color = contentColor,
                    )
                }
            }
        }
    }
}

/**
 * Amount display shown above the keypad: label + hero amount over a soft gradient surface.
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
    val targetContainer =
        when {
            isError -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.45f)
            isPositive -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.50f)
            else -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.22f)
        }
    val targetBorder =
        when {
            isError -> MaterialTheme.colorScheme.error
            isPositive -> MaterialTheme.colorScheme.primary
            else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
        }
    val containerColor by animateColorAsState(targetContainer, tween(COLOR_ANIM_MS), label = "displayContainer")
    val borderColor by animateColorAsState(targetBorder, tween(COLOR_ANIM_MS), label = "displayBorder")
    val amountColor by animateColorAsState(
        if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
        tween(COLOR_ANIM_MS),
        label = "displayAmount",
    )
    val gradient =
        Brush.verticalGradient(
            listOf(
                containerColor,
                lerp(containerColor, MaterialTheme.colorScheme.surface, GRADIENT_SURFACE_BLEND),
            ),
        )
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(shape)
                .background(gradient)
                .border(1.5.dp, borderColor, shape)
                .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(
                shape = RoundedCornerShape(6.dp),
                color =
                    if (isError) {
                        MaterialTheme.colorScheme.error.copy(
                            alpha = 0.15f,
                        )
                    } else {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                    },
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                )
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        AnimatedAmountText(
            amountText = amountText,
            currencyPrefix = currencyPrefix,
            amountColor = amountColor,
        )
        if (secondaryLine != null) {
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.50f),
                modifier = Modifier.padding(top = 2.dp),
            ) {
                Text(
                    secondaryLine,
                    style = PosTextStyles.amountSecondary.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
        }
        extraContent()
    }
}

/** Monto héroe con animación tipo odómetro en cada cambio de valor. */
@Composable
private fun AnimatedAmountText(
    amountText: String,
    currencyPrefix: String,
    amountColor: Color,
) {
    AnimatedContent(
        targetState = amountText,
        transitionSpec = {
            (
                slideInVertically(tween(AMOUNT_IN_ANIM_MS)) { it / ODOMETER_SLIDE_FRACTION } +
                    fadeIn(tween(AMOUNT_IN_ANIM_MS))
            ) togetherWith (
                slideOutVertically(tween(AMOUNT_OUT_ANIM_MS)) { -it / ODOMETER_SLIDE_FRACTION } +
                    fadeOut(tween(AMOUNT_OUT_ANIM_MS))
            )
        },
        label = "amountOdometer",
    ) { value ->
        AdaptiveAmountText(
            text = "$currencyPrefix$value",
            baseStyle = PosTextStyles.totalDisplay,
            color = amountColor,
            modifier = Modifier.fillMaxWidth(),
            options = AdaptiveAmountOptions(minFontSizeSp = 18f),
        )
    }
}

private const val COLOR_ANIM_MS = 280
private const val AMOUNT_IN_ANIM_MS = 220
private const val AMOUNT_OUT_ANIM_MS = 160
private const val ODOMETER_SLIDE_FRACTION = 3
private const val GRADIENT_SURFACE_BLEND = 0.55f
