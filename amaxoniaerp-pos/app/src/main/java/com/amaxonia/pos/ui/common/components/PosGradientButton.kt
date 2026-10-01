package com.amaxonia.pos.ui.common.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.amaxonia.pos.ui.theme.PosPalette
import com.amaxonia.pos.ui.theme.cartBrandGradient

/**
 * Botón primario de acción con gradiente fluido de marca.
 *
 * Resuelve el problema de renderizado de Material 3 donde aplicar elevación sobre
 * un fondo transparente dibuja una sombra gris rectangular interior ("cuadrados").
 * Utiliza una sombra suave coloreada (glow de marca) y contiene el gradiente
 * en su interior respetando el [shape] sin cortes abruptos.
 */
@Composable
fun PosGradientButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = RoundedCornerShape(16.dp),
    gradient: Brush? = null,
    elevation: Dp = 2.5.dp,
    contentPadding: PaddingValues = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
    content: @Composable RowScope.() -> Unit,
) {
    val activeGradient = gradient ?: Brush.horizontalGradient(cartBrandGradient())
    val shadowColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)

    Button(
        onClick = onClick,
        enabled = enabled,
        modifier =
            modifier
                .shadow(
                    elevation = if (enabled) elevation else 0.dp,
                    shape = shape,
                    clip = false,
                    ambientColor = shadowColor,
                    spotColor = shadowColor,
                ),
        shape = shape,
        colors =
            ButtonDefaults.buttonColors(
                containerColor = Color.Transparent,
                contentColor = PosPalette.FixedWhite,
                disabledContainerColor = Color.Transparent,
                disabledContentColor = PosPalette.FixedWhite.copy(alpha = 0.5f),
            ),
        contentPadding = PaddingValues(0.dp),
        elevation = null,
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(
                        brush =
                            if (enabled) {
                                activeGradient
                            } else {
                                SolidColor(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f))
                            },
                        shape = shape,
                    )
                    .padding(contentPadding),
            contentAlignment = Alignment.Center,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                content = content,
            )
        }
    }
}
