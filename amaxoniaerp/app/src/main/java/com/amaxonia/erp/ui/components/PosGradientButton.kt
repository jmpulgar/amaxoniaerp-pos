package com.amaxonia.erp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amaxonia.erp.ui.theme.PosPalette
import com.amaxonia.erp.ui.theme.cartBrandGradient

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

/**
 * Sobrecarga conveniente con texto y soporte de indicador de carga.
 */
@Composable
fun PosGradientButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    shape: Shape = RoundedCornerShape(16.dp),
    gradient: Brush? = null,
    elevation: Dp = 2.5.dp,
) {
    PosGradientButton(
        onClick = onClick,
        modifier = modifier.height(54.dp),
        enabled = enabled && !loading,
        shape = shape,
        gradient = gradient,
        elevation = elevation,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
    ) {
        if (loading) {
            CircularProgressIndicator(
                color = PosPalette.FixedWhite,
                modifier = Modifier.size(22.dp),
                strokeWidth = 2.5.dp,
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = "Procesando...",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = PosPalette.FixedWhite,
            )
        } else {
            Text(
                text = text,
                style =
                    MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                    ),
                color = PosPalette.FixedWhite,
            )
        }
    }
}
