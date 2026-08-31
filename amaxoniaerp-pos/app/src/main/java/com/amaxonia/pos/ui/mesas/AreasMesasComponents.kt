@file:Suppress("CyclomaticComplexMethod", "LongMethod")

package com.amaxonia.pos.ui.mesas

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Deck
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PointOfSale
import androidx.compose.material.icons.filled.TableRestaurant
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amaxonia.pos.domain.model.mesas.Area
import com.amaxonia.pos.domain.model.mesas.EstadoMesaOperativo
import com.amaxonia.pos.domain.model.mesas.Mesa
import com.amaxonia.pos.ui.common.components.PosEmptyState
import com.amaxonia.pos.ui.common.components.PosFeedbackCard
import com.amaxonia.pos.ui.common.components.PosLoadingState
import com.amaxonia.pos.ui.common.components.PosStatusBadge
import com.amaxonia.pos.ui.common.components.PosVisualAction
import com.amaxonia.pos.ui.common.components.PosVisualTone
import com.amaxonia.pos.ui.theme.PosExtraShapes
import com.amaxonia.pos.ui.theme.PosStatusColors
import com.amaxonia.pos.ui.theme.PosTheme

internal const val ESTADO_CUENTA_SOLICITADA = "CUENTA_SOLICITADA"

data class MesaThemeColors(
    val bg: Color,
    val border: Color,
    val badgeBg: Color,
    val text: Color,
)

/**
 * Paleta de colores vivos y modernos para el módulo de mesas y áreas, con soporte para temas claro y oscuro.
 */
object MesaVisualColors {
    // DISPONIBLE - Claro
    val DisponibleBg = Color(0xFFF0FDF4)
    val DisponibleBorder = Color(0xFF10B981)
    val DisponibleBadgeBg = Color(0xFFDCFCE7)
    val DisponibleText = Color(0xFF065F46)
    val DisponibleAccent = Color(0xFF059669)

    // DISPONIBLE - Oscuro
    val DisponibleBgDark = Color(0xFF0F291E)
    val DisponibleBorderDark = Color(0xFF10B981)
    val DisponibleBadgeBgDark = Color(0xFF064E3B)
    val DisponibleTextDark = Color(0xFF6EE7B7)

    // OCUPADA - Claro
    val OcupadaBg = Color(0xFFFFFBEB)
    val OcupadaBorder = Color(0xFFF59E0B)
    val OcupadaBadgeBg = Color(0xFFFEF3C7)
    val OcupadaText = Color(0xFF92400E)
    val OcupadaAccent = Color(0xFFD97706)

    // OCUPADA - Oscuro
    val OcupadaBgDark = Color(0xFF2A1C08)
    val OcupadaBorderDark = Color(0xFFF59E0B)
    val OcupadaBadgeBgDark = Color(0xFF78350F)
    val OcupadaTextDark = Color(0xFFFCD34D)

    // CUENTA SOLICITADA - Claro
    val CuentaBg = Color(0xFFFFF1F2)
    val CuentaBorder = Color(0xFFE11D48)
    val CuentaBadgeBg = Color(0xFFFFE4E6)
    val CuentaText = Color(0xFF9F1239)
    val CuentaAccent = Color(0xFFE11D48)

    // CUENTA SOLICITADA - Oscuro
    val CuentaBgDark = Color(0xFF2B0E14)
    val CuentaBorderDark = Color(0xFFE11D48)
    val CuentaBadgeBgDark = Color(0xFF881337)
    val CuentaTextDark = Color(0xFFFDA4AF)

    // SELECCIONADA - Claro
    val SelectedBg = Color(0xFFEFF6FF)
    val SelectedBorder = Color(0xFF2563EB)
    val SelectedBadgeBg = Color(0xFFDBEAFE)
    val SelectedText = Color(0xFF1E40AF)

    // SELECCIONADA - Oscuro
    val SelectedBgDark = Color(0xFF0F2447)
    val SelectedBorderDark = Color(0xFF3B82F6)
    val SelectedBadgeBgDark = Color(0xFF1E3A8A)
    val SelectedTextDark = Color(0xFF93C5FD)

    fun getColors(
        estado: String?,
        isSelected: Boolean,
        isDark: Boolean,
    ): MesaThemeColors =
        when {
            isSelected ->
                if (isDark) {
                    MesaThemeColors(
                        bg = SelectedBgDark,
                        border = SelectedBorderDark,
                        badgeBg = SelectedBadgeBgDark,
                        text = SelectedTextDark,
                    )
                } else {
                    MesaThemeColors(
                        bg = SelectedBg,
                        border = SelectedBorder,
                        badgeBg = SelectedBadgeBg,
                        text = SelectedText,
                    )
                }
            estado == ESTADO_CUENTA_SOLICITADA ->
                if (isDark) {
                    MesaThemeColors(
                        bg = CuentaBgDark,
                        border = CuentaBorderDark,
                        badgeBg = CuentaBadgeBgDark,
                        text = CuentaTextDark,
                    )
                } else {
                    MesaThemeColors(
                        bg = CuentaBg,
                        border = CuentaBorder,
                        badgeBg = CuentaBadgeBg,
                        text = CuentaText,
                    )
                }
            estado == EstadoMesaOperativo.OCUPADA ->
                if (isDark) {
                    MesaThemeColors(
                        bg = OcupadaBgDark,
                        border = OcupadaBorderDark,
                        badgeBg = OcupadaBadgeBgDark,
                        text = OcupadaTextDark,
                    )
                } else {
                    MesaThemeColors(
                        bg = OcupadaBg,
                        border = OcupadaBorder,
                        badgeBg = OcupadaBadgeBg,
                        text = OcupadaText,
                    )
                }
            else ->
                if (isDark) {
                    MesaThemeColors(
                        bg = DisponibleBgDark,
                        border = DisponibleBorderDark,
                        badgeBg = DisponibleBadgeBgDark,
                        text = DisponibleTextDark,
                    )
                } else {
                    MesaThemeColors(
                        bg = DisponibleBg,
                        border = DisponibleBorder,
                        badgeBg = DisponibleBadgeBg,
                        text = DisponibleText,
                    )
                }
        }
}

/**
 * Selector horizontal de áreas: Pestañas/Pills táctiles con badge de cantidad y estado activo vibrante.
 */
@Composable
fun AreaChipRow(
    areas: List<Area>,
    selectedAreaId: Int?,
    enabled: Boolean,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(areas, key = { it.id }) { area ->
            val isSelected = area.id == selectedAreaId
            val containerColor =
                if (isSelected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surface
                }
            val contentColor =
                if (isSelected) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.onSurface
                }
            val borderColor =
                if (isSelected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)
                }

            Surface(
                modifier =
                    Modifier
                        .height(44.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .clickable(
                            enabled = enabled,
                            indication = ripple(),
                            interactionSource = remember { MutableInteractionSource() },
                        ) {
                            onSelect(area.id)
                        },
                shape = RoundedCornerShape(22.dp),
                color = containerColor,
                border = BorderStroke(if (isSelected) 1.5.dp else 1.dp, borderColor),
                shadowElevation = if (isSelected) 3.dp else 0.dp,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                ) {
                    Icon(
                        imageVector = if (isSelected) Icons.Default.Check else Icons.Default.Deck,
                        contentDescription = null,
                        tint = contentColor,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = area.displayName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium,
                        color = contentColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        shape = CircleShape,
                        color = if (isSelected) contentColor.copy(alpha = 0.22f) else MaterialTheme.colorScheme.surfaceVariant,
                    ) {
                        Text(
                            text = "${area.cantidadMesasActivas}",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = contentColor,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                        )
                    }
                }
            }
        }
    }
}

/**
 * Barra resumen de estados: Pills vivos con colores llamativos y contadores en tiempo real.
 */
@Composable
fun MesaStateLegend(
    totalMesas: Int,
    estados: Collection<String>,
    isLoading: Boolean,
    modifier: Modifier = Modifier,
) {
    val isDark = isSystemInDarkTheme()
    val cuentaSolicitada = estados.count { it == ESTADO_CUENTA_SOLICITADA }
    val ocupadas = estados.count { it == EstadoMesaOperativo.OCUPADA }
    val disponibles = (totalMesas - ocupadas - cuentaSolicitada).coerceAtLeast(0)

    val dispColors = MesaVisualColors.getColors(estado = EstadoMesaOperativo.DISPONIBLE, isSelected = false, isDark = isDark)
    val ocupColors = MesaVisualColors.getColors(estado = EstadoMesaOperativo.OCUPADA, isSelected = false, isDark = isDark)
    val cuentaColors = MesaVisualColors.getColors(estado = ESTADO_CUENTA_SOLICITADA, isSelected = false, isDark = isDark)

    LazyRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 2.dp),
    ) {
        item {
            LivelyStatusPill(
                label = "Disponibles",
                count = disponibles,
                dotColor = dispColors.border,
                containerColor = dispColors.bg,
                borderColor = dispColors.border.copy(alpha = if (isDark) 0.5f else 0.6f),
                textColor = dispColors.text,
                icon = Icons.Default.CheckCircle,
            )
        }
        item {
            LivelyStatusPill(
                label = "Ocupadas",
                count = ocupadas,
                dotColor = ocupColors.border,
                containerColor = ocupColors.bg,
                borderColor = ocupColors.border.copy(alpha = if (isDark) 0.5f else 0.6f),
                textColor = ocupColors.text,
                icon = Icons.Default.TableRestaurant,
            )
        }
        item {
            LivelyStatusPill(
                label = "Cuenta pedida",
                count = cuentaSolicitada,
                dotColor = cuentaColors.border,
                containerColor = cuentaColors.bg,
                borderColor = cuentaColors.border.copy(alpha = if (isDark) 0.5f else 0.6f),
                textColor = cuentaColors.text,
                icon = Icons.AutoMirrored.Filled.ReceiptLong,
            )
        }
        if (isLoading) {
            item {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(12.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Actualizando…",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LivelyStatusPill(
    label: String,
    count: Int,
    dotColor: Color,
    containerColor: Color,
    borderColor: Color,
    textColor: Color,
    icon: ImageVector,
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = containerColor,
        border = BorderStroke(1.dp, borderColor),
        shadowElevation = 0.5.dp,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
        ) {
            Box(
                modifier =
                    Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(dotColor),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "$label: ",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
                color = textColor,
            )
            Text(
                text = "$count",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.ExtraBold,
                color = textColor,
            )
        }
    }
}

/**
 * Tarjeta de mesa: viva, táctil y colorida con diseño moderno de POS de restaurante.
 */
@Composable
fun MesaCard(
    mesa: Mesa,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    /** Uno de [EstadoMesaOperativo] o `null` para "no hidratado". */
    estadoOperativo: String? = null,
) {
    val isDark = isSystemInDarkTheme()
    val visualColors = remember(estadoOperativo, isSelected, isDark) {
        MesaVisualColors.getColors(estadoOperativo, isSelected, isDark)
    }

    val containerColor by animateColorAsState(visualColors.bg, label = "mesaContainer")
    val borderColor by animateColorAsState(visualColors.border, label = "mesaBorder")
    val shape = RoundedCornerShape(16.dp)

    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().heightIn(min = MESA_CARD_MIN_HEIGHT),
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = BorderStroke(if (isSelected) 2.5.dp else 1.5.dp, borderColor),
        elevation =
            CardDefaults.cardElevation(
                defaultElevation = if (isSelected) 6.dp else 2.dp,
            ),
    ) {
        Column(
            modifier = Modifier.padding(14.dp).fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            // Fila superior: Nombre de mesa + Código + Chip de estado
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = mesa.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    mesa.displayCode?.let { code ->
                        Text(
                            text = "#$code",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = visualColors.text.copy(alpha = if (isDark) 0.95f else 0.85f),
                        )
                    }
                }
                if (estadoOperativo != null) {
                    MesaStatusChip(estado = estadoOperativo, isDark = isDark)
                } else if (isSelected) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(24.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }

            // Ilustración/Glyph visual de la mesa y sus sillas
            MesaVisualShapeGlyph(
                forma = mesa.forma ?: "rectangular",
                capacidad = mesa.capacidad,
                accentColor = visualColors.border,
                iconColor = visualColors.text,
                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
            )

            // Fila inferior: Capacidad y Etiqueta de Forma
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = visualColors.text.copy(alpha = if (isDark) 0.16f else 0.12f),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.People,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = visualColors.text,
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "${mesa.capacidad} pers.",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = visualColors.text,
                        )
                    }
                }
                mesa.forma?.let { forma ->
                    MesaShapeTag(forma = forma, contentColor = visualColors.text)
                }
            }
        }
    }
}

/** Glifo visual minimalista que representa la mesa y capacidad de forma limpia y atractiva. */
@Composable
private fun MesaVisualShapeGlyph(
    forma: String,
    capacidad: Int,
    accentColor: Color,
    iconColor: Color = accentColor,
    modifier: Modifier = Modifier,
) {
    val isRound = forma.contains("redond", ignoreCase = true) || forma.contains("circul", ignoreCase = true)
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = if (isRound) CircleShape else RoundedCornerShape(8.dp),
            color = accentColor.copy(alpha = 0.16f),
            border = BorderStroke(1.dp, accentColor.copy(alpha = 0.45f)),
            modifier = Modifier.size(if (isRound) 40.dp else 46.dp, 36.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.TableRestaurant,
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

/** Estado operativo con pill de color, icono y texto. */
@Composable
private fun MesaStatusChip(
    estado: String,
    isDark: Boolean = isSystemInDarkTheme(),
) {
    val colors = MesaVisualColors.getColors(estado = estado, isSelected = false, isDark = isDark)
    val (label, icon) =
        when (estado) {
            ESTADO_CUENTA_SOLICITADA ->
                Pair("Cuenta", Icons.AutoMirrored.Filled.ReceiptLong)
            EstadoMesaOperativo.OCUPADA ->
                Pair("Ocupada", Icons.Default.TableRestaurant)
            else ->
                Pair("Disponible", Icons.Default.CheckCircle)
        }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = colors.badgeBg,
        border = BorderStroke(1.dp, colors.border.copy(alpha = 0.7f)),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = colors.text,
                modifier = Modifier.size(12.dp),
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = colors.text,
            )
        }
    }
}

@Composable
private fun MesaShapeTag(
    forma: String,
    contentColor: Color,
) {
    Surface(
        shape = PosExtraShapes.Pill,
        color = contentColor.copy(alpha = 0.12f),
    ) {
        Text(
            text = forma.replaceFirstChar { it.uppercase() },
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            color = contentColor,
            maxLines = 1,
        )
    }
}

/** Estado vacío / informativo reutilizado por "sin áreas", "sin mesas" y "sin caja". */
@Composable
fun MesasInfoState(
    icon: ImageVector,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    action: InfoAction? = null,
) {
    PosEmptyState(
        icon = icon,
        title = title,
        message = message,
        modifier = modifier,
        action = action?.let { PosVisualAction(label = it.label, onClick = it.onClick) },
    )
}

/** Error con reintento explícito, para áreas y para mesas por separado. */
@Composable
fun MesasErrorState(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PosFeedbackCard(
        title = "No pudimos cargar la información",
        message = message,
        tone = PosVisualTone.Error,
        modifier = modifier,
        action = PosVisualAction(label = "Reintentar", onClick = onRetry),
    )
}

/** Aviso de que lo mostrado es la última configuración descargada, no datos frescos. */
@Composable
fun OfflineConfigBanner(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.small,
        color = PosStatusColors.pendingContainer,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Default.CloudOff,
                contentDescription = null,
                tint = PosStatusColors.pendingContent,
                modifier = Modifier.size(16.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Sin conexión: mostrando la última configuración descargada",
                style = MaterialTheme.typography.bodySmall,
                color = PosStatusColors.pendingContent,
            )
        }
    }
}

@Composable
fun MesasLoadingState(modifier: Modifier = Modifier) {
    PosLoadingState(message = "Cargando áreas y mesas…", modifier = modifier)
}

private val MESA_CARD_MIN_HEIGHT = 136.dp

@Preview(name = "MesaCard · Light", showBackground = true, backgroundColor = 0xFFF5F5F5)
@Composable
fun MesaCardPreview() {
    PosTheme(darkTheme = false) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            MesaCard(
                mesa =
                    Mesa(
                        id = 1,
                        areaId = 1,
                        codigo = "M01",
                        nombre = "Mesa 01",
                        capacidad = 4,
                        forma = "rectangular",
                    ),
                isSelected = false,
                onClick = {},
                modifier = Modifier.weight(1f),
            )
            MesaCard(
                mesa =
                    Mesa(
                        id = 2,
                        areaId = 1,
                        codigo = "M02",
                        nombre = "Mesa 02",
                        capacidad = 2,
                        forma = "redonda",
                    ),
                isSelected = true,
                onClick = {},
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Preview(name = "MesaCard · Dark", showBackground = true, backgroundColor = 0xFF0F141F)
@Composable
fun MesaCardDarkPreview() {
    PosTheme(darkTheme = true) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            MesaCard(
                mesa =
                    Mesa(
                        id = 1,
                        areaId = 1,
                        codigo = "M01",
                        nombre = "Mesa 01",
                        capacidad = 4,
                        forma = "rectangular",
                    ),
                isSelected = false,
                onClick = {},
                modifier = Modifier.weight(1f),
            )
            MesaCard(
                mesa =
                    Mesa(
                        id = 2,
                        areaId = 1,
                        codigo = "M02",
                        nombre = "Mesa 02",
                        capacidad = 2,
                        forma = "redonda",
                    ),
                isSelected = true,
                onClick = {},
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFF5F5F5)
@Composable
fun AreaChipRowPreview() {
    PosTheme {
        AreaChipRow(
            areas =
                listOf(
                    Area(id = 1, nombre = "Salón principal", cantidadMesasActivas = 12),
                    Area(id = 2, nombre = "Terraza", cantidadMesasActivas = 6),
                    Area(id = 3, nombre = "Bar", cantidadMesasActivas = 4),
                ),
            selectedAreaId = 1,
            enabled = true,
            onSelect = {},
            modifier = Modifier.padding(16.dp),
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFF5F5F5)
@Composable
fun MesasEmptyStatePreview() {
    PosTheme {
        MesasInfoState(
            icon = Icons.Default.TableRestaurant,
            title = "Esta área no tiene mesas",
            message = "Configura mesas para esta área en el sistema administrativo.",
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFF5F5F5)
@Composable
fun MesasNoCajaStatePreview() {
    PosTheme {
        MesasInfoState(
            icon = Icons.Default.PointOfSale,
            title = "Selecciona una caja",
            message = "Las áreas se muestran según la sucursal de la caja activa.",
            action = InfoAction(label = "Seleccionar caja", onClick = {}),
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFF5F5F5)
@Composable
fun AreasEmptyStatePreview() {
    PosTheme {
        MesasInfoState(
            icon = Icons.Default.Deck,
            title = "Esta sucursal no tiene áreas",
            message = "Crea áreas para la sucursal en el sistema administrativo.",
        )
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFF5F5F5)
@Composable
fun MesasErrorStatePreview() {
    PosTheme {
        MesasErrorState(
            message = "La caja activa no tiene una sucursal asignada",
            onRetry = {},
            modifier = Modifier.padding(16.dp),
        )
    }
}
