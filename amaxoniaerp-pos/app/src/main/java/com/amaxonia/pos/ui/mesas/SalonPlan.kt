@file:Suppress(
    "CyclomaticComplexMethod",
    "LongMethod",
    "LongParameterList",
    "MagicNumber",
)

package com.amaxonia.pos.ui.mesas

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PanToolAlt
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import com.amaxonia.pos.core.logging.SafeLog
import com.amaxonia.pos.domain.model.mesas.EstadoMesaOperativo
import com.amaxonia.pos.domain.model.mesas.Lienzo
import com.amaxonia.pos.domain.model.mesas.Mesa
import com.amaxonia.pos.domain.model.mesas.SalonForma
import com.amaxonia.pos.domain.model.mesas.SalonGeometry
import com.amaxonia.pos.domain.model.mesas.ViewportCanvas
import com.amaxonia.pos.domain.model.mesas.hitTestTranslated
import kotlin.math.max
import kotlin.math.min

private const val TAG = "SalonPlan"
private const val MIN_ZOOM = 0.5f
private const val MAX_ZOOM = 4f
private const val INITIAL_ZOOM = 1f

/**
 * Plano visual del área con zoom y desplazamiento (solo lectura).
 *
 * - Escala el lienzo lógico (2000x1200) al tamaño del área visible conservando la relación de
 *   aspecto (mínimo de los factores de ancho y alto).
 * - Aplica zoom/pan gestual encima del escalado base sin deformar.
 * - Pinta cada [Mesa] con su forma y rotación alrededor del centro.
 * - Resalta la mesa seleccionada con borde primario.
 * - Si hay [imagenUrl] la pinta como fondo con Coil; si falla o no existe, se muestra un
 *   fondo neutro y el plano sigue siendo funcional.
 *
 * No se mueven ni se editan mesas: los gestos solo afectan a la cámara del plano.
 */
@Composable
fun SalonPlan(
    mesas: List<Mesa>,
    lienzo: Lienzo,
    imagenUrl: String?,
    selectedMesaId: Int?,
    onMesaClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
    /** MesaId -> estado operativo (DISPONIBLE/OCUPADA). Vacío = no hidratado. */
    estadosByMesaId: Map<Int, String> = emptyMap(),
) {
    var scale by remember { mutableFloatStateOf(INITIAL_ZOOM) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    val surfaceVariant = MaterialTheme.colorScheme.surfaceVariant
    val primary = MaterialTheme.colorScheme.primary
    val surface = MaterialTheme.colorScheme.surface
    val primaryContainer = MaterialTheme.colorScheme.primaryContainer
    val outline = MaterialTheme.colorScheme.outline
    // Colores para "Ocupada": tertiaryContainer (amarillo cálido) para distinguirla de la
    // seleccionada (primary). DISPONIBLE usa surface + outline, como antes.
    val ocupadaFill = MaterialTheme.colorScheme.tertiaryContainer
    val ocupadaBorder = MaterialTheme.colorScheme.tertiary
    val cuentaFill = MaterialTheme.colorScheme.errorContainer
    val cuentaBorder = MaterialTheme.colorScheme.error
    val labelColor = MaterialTheme.colorScheme.onSurface

    Box(
        modifier =
            modifier
                .fillMaxSize()
                .clip(MaterialTheme.shapes.medium)
                .background(surfaceVariant)
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale = (scale * zoom).coerceIn(MIN_ZOOM, MAX_ZOOM)
                        offsetX += pan.x
                        offsetY += pan.y
                    }
                },
    ) {
        // Fondo del plano: lo pinta Coil si la URL es válida. Si falla o no existe el fondo
        // neutro (surfaceVariant) del Box ya está pintado y el plano sigue operativo.
        if (!imagenUrl.isNullOrBlank()) {
            SubcomposeAsyncImage(
                model = imagenUrl,
                contentDescription = "Fondo del plano del área",
                modifier = Modifier.fillMaxSize(),
                loading = { /* el fondo neutro ya está visible debajo */ },
                error = {
                    SafeLog.w(TAG, "No se pudo cargar el fondo del plano: $imagenUrl")
                },
                success = { SubcomposeAsyncImageContent(modifier = Modifier.fillMaxSize()) },
            )
        }

        PlanoCanvas(
            mesas = mesas,
            lienzo = lienzo,
            scale = scale,
            offsetX = offsetX,
            offsetY = offsetY,
            selectedMesaId = selectedMesaId,
            onMesaClick = onMesaClick,
            primary = primary,
            primaryContainer = primaryContainer,
            surface = surface,
            outline = outline,
            ocupadaFill = ocupadaFill,
            ocupadaBorder = ocupadaBorder,
            cuentaFill = cuentaFill,
            cuentaBorder = cuentaBorder,
            labelColor = labelColor,
            estadosByMesaId = estadosByMesaId,
            modifier = Modifier.fillMaxSize(),
        )

        Surface(
            modifier =
                Modifier
                    .align(Alignment.TopStart)
                    .fillMaxWidth()
                    .padding(8.dp),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
            shadowElevation = 2.dp,
        ) {
            MesaStateLegend(
                totalMesas = mesas.size,
                estados = estadosByMesaId.values,
                isLoading = false,
                modifier = Modifier.padding(8.dp),
            )
        }

        Surface(
            modifier = Modifier.align(Alignment.BottomStart).padding(10.dp),
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.88f),
            contentColor = MaterialTheme.colorScheme.inverseOnSurface,
        ) {
            androidx.compose.foundation.layout.Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Default.PanToolAlt,
                    contentDescription = null,
                    modifier = Modifier.padding(end = 8.dp),
                )
                Text(
                    text = "Arrastra para mover · pellizca para ampliar",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium,
                )
            }
        }

        if (scale != INITIAL_ZOOM || offsetX != 0f || offsetY != 0f) {
            Surface(
                modifier =
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 72.dp, end = 10.dp),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 3.dp,
            ) {
                IconButton(
                    onClick = {
                        scale = INITIAL_ZOOM
                        offsetX = 0f
                        offsetY = 0f
                    },
                ) {
                    Icon(
                        imageVector = Icons.Default.Restore,
                        contentDescription = "Reiniciar zoom",
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

@Composable
private fun PlanoCanvas(
    mesas: List<Mesa>,
    lienzo: Lienzo,
    scale: Float,
    offsetX: Float,
    offsetY: Float,
    selectedMesaId: Int?,
    onMesaClick: (Int) -> Unit,
    primary: Color,
    primaryContainer: Color,
    surface: Color,
    outline: Color,
    ocupadaFill: Color,
    ocupadaBorder: Color,
    cuentaFill: Color,
    cuentaBorder: Color,
    labelColor: Color,
    estadosByMesaId: Map<Int, String>,
    modifier: Modifier,
) {
    Canvas(
        modifier =
            modifier
                .pointerInput(mesas, lienzo, scale, offsetX, offsetY) {
                    detectTapGestures(
                        onTap = { offset ->
                            val viewport =
                                ViewportCanvas(
                                    width = size.width.toFloat().coerceAtLeast(1f),
                                    height = size.height.toFloat().coerceAtLeast(1f),
                                )
                            val hit =
                                SalonGeometry.hitTestTranslated(
                                    mesas = mesas,
                                    lienzo = lienzo,
                                    viewport = viewport,
                                    extraScale = scale,
                                    offsetX = offsetX,
                                    offsetY = offsetY,
                                    xPx = offset.x,
                                    yPx = offset.y,
                                ) ?: return@detectTapGestures
                            onMesaClick(hit.id)
                        },
                    )
                },
    ) {
        val viewport = ViewportCanvas(size.width.coerceAtLeast(1f), size.height.coerceAtLeast(1f))
        val baseScale = SalonGeometry.escalaProporcional(lienzo, viewport).coerceAtLeast(0f)

        translate(left = offsetX, top = offsetY) {
            scale(baseScale * scale, pivot = Offset.Zero) {
                mesas.forEach { mesa ->
                    val isSelected = mesa.id == selectedMesaId
                    val isOcupada = estadosByMesaId[mesa.id] == EstadoMesaOperativo.OCUPADA
                    val isCuentaSolicitada = estadosByMesaId[mesa.id] == ESTADO_CUENTA_SOLICITADA
                    val forma = SalonForma.fromRaw(mesa.forma)
                    val width = mesa.ancho.toFloat().coerceAtLeast(1f)
                    val height = mesa.alto.toFloat().coerceAtLeast(1f)
                    val cx = (mesa.posicionX + mesa.ancho / 2.0).toFloat()
                    val cy = (mesa.posicionY + mesa.alto / 2.0).toFloat()
                    val fill =
                        when {
                            isSelected -> primaryContainer
                            isCuentaSolicitada -> cuentaFill
                            isOcupada -> ocupadaFill
                            else -> surface
                        }
                    val borderColor =
                        when {
                            isSelected -> primary
                            isCuentaSolicitada -> cuentaBorder
                            isOcupada -> ocupadaBorder
                            else -> outline
                        }
                    val borderWidth =
                        when {
                            isSelected -> 8f
                            isCuentaSolicitada -> 6f
                            isOcupada -> 6f
                            else -> 3f
                        }
                    drawMesaShape(
                        forma = forma,
                        cx = cx,
                        cy = cy,
                        width = width,
                        height = height,
                        rotDeg = mesa.rotacion.toFloat(),
                        fill = fill,
                        borderColor = borderColor,
                        borderWidth = borderWidth,
                        label = mesa.displayCode ?: mesa.displayName,
                        labelColor = labelColor,
                    )
                }
            }
        }
    }
}

/**
 * Pinta el contorno + relleno de una mesa según su forma, rotada alrededor del centro (cx,cy).
 * Se invoca ya dentro de un bloque `scale(...)` del canvas, así que las medidas están en
 * espacio lógico (px del lienzo 2000x1200).
 */
private fun DrawScope.drawMesaShape(
    forma: SalonForma,
    cx: Float,
    cy: Float,
    width: Float,
    height: Float,
    rotDeg: Float,
    fill: Color,
    borderColor: Color,
    borderWidth: Float,
    label: String,
    labelColor: Color,
) {
    val w = max(width, 1f)
    val h = max(height, 1f)
    val left = cx - w / 2f
    val top = cy - h / 2f
    val topLeft = Offset(left, top)
    val shapeSize = Size(w, h)
    val stroke = Stroke(width = borderWidth)

    if (rotDeg % 360f == 0f) {
        drawForma(forma, fill, borderColor, stroke, topLeft, shapeSize)
        drawMesaLabel(label, cx, cy, w, h, labelColor)
    } else {
        rotate(degrees = rotDeg, pivot = Offset(cx, cy)) {
            drawForma(forma, fill, borderColor, stroke, topLeft, shapeSize)
            drawMesaLabel(label, cx, cy, w, h, labelColor)
        }
    }
}

private fun DrawScope.drawMesaLabel(
    label: String,
    cx: Float,
    cy: Float,
    width: Float,
    height: Float,
    color: Color,
) {
    val safeLabel = label.take(12)
    val paint =
        android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = android.graphics.Paint.Align.CENTER
            textSize = (min(width, height) * 0.24f).coerceIn(16f, 52f)
            typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
            this.color = color.toArgb()
        }
    val baseline = cy - (paint.ascent() + paint.descent()) / 2f
    drawContext.canvas.nativeCanvas.drawText(safeLabel, cx, baseline, paint)
}

private fun DrawScope.drawForma(
    forma: SalonForma,
    fill: Color,
    borderColor: Color,
    stroke: Stroke,
    topLeft: Offset,
    shapeSize: Size,
) {
    when (forma) {
        SalonForma.RECTANGULAR, SalonForma.CUADRADA -> {
            drawRect(color = fill, topLeft = topLeft, size = shapeSize)
            drawRect(color = borderColor, topLeft = topLeft, size = shapeSize, style = stroke)
        }
        SalonForma.REDONDA -> {
            drawOval(color = fill, topLeft = topLeft, size = shapeSize)
            drawOval(color = borderColor, topLeft = topLeft, size = shapeSize, style = stroke)
        }
    }
}
