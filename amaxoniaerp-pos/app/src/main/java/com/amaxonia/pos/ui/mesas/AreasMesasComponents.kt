@file:Suppress("CyclomaticComplexMethod", "LongMethod")

package com.amaxonia.pos.ui.mesas

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
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

/**
 * Selector horizontal de áreas. En teléfono es la forma más cómoda de cambiar de área sin
 * ocupar altura útil de la cuadrícula de mesas.
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
        contentPadding = PaddingValues(horizontal = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(areas, key = { it.id }) { area ->
            FilterChip(
                selected = area.id == selectedAreaId,
                enabled = enabled,
                onClick = { onSelect(area.id) },
                modifier = Modifier.heightIn(min = 48.dp),
                shape = PosExtraShapes.Pill,
                leadingIcon =
                    if (area.id == selectedAreaId) {
                        {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    } else {
                        null
                    },
                label = {
                    Text(
                        text = "${area.displayName} · ${area.cantidadMesasActivas}",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                colors =
                    FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ),
            )
        }
    }
}

@Composable
fun MesaStateLegend(
    totalMesas: Int,
    estados: Collection<String>,
    isLoading: Boolean,
    modifier: Modifier = Modifier,
) {
    val cuentaSolicitada = estados.count { it == ESTADO_CUENTA_SOLICITADA }
    val ocupadas = estados.count { it == EstadoMesaOperativo.OCUPADA }
    val disponibles = (totalMesas - ocupadas - cuentaSolicitada).coerceAtLeast(0)
    val suffix: (Int) -> String = { count -> if (estados.isEmpty()) "" else " · $count" }

    LazyRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 2.dp),
    ) {
        item {
            PosStatusBadge(
                label = "Disponible${suffix(disponibles)}",
                tone = PosVisualTone.Success,
                icon = Icons.Default.CheckCircle,
            )
        }
        item {
            PosStatusBadge(
                label = "Ocupada${suffix(ocupadas)}",
                tone = PosVisualTone.Warning,
                icon = Icons.Default.TableRestaurant,
            )
        }
        item {
            PosStatusBadge(
                label = "Cuenta solicitada${suffix(cuentaSolicitada)}",
                tone = PosVisualTone.Error,
                icon = Icons.AutoMirrored.Filled.ReceiptLong,
            )
        }
        if (isLoading) {
            item {
                PosStatusBadge(
                    label = "Actualizando",
                    tone = PosVisualTone.Info,
                )
            }
        }
    }
}

/**
 * Tarjeta de mesa: grande y táctil. Muestra solo datos de configuración (nombre, código,
 * capacidad y forma). Deliberadamente **no** hay indicador de disponible/ocupada: ese estado
 * llegará con la sesión de mesa y no puede deducirse de `activo`.
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
    val isOcupada = estadoOperativo == EstadoMesaOperativo.OCUPADA
    val isCuentaSolicitada = estadoOperativo == ESTADO_CUENTA_SOLICITADA
    val targetContainerColor =
        when {
            isSelected -> MaterialTheme.colorScheme.primaryContainer
            isCuentaSolicitada -> MaterialTheme.colorScheme.errorContainer
            isOcupada -> MaterialTheme.colorScheme.tertiaryContainer
            else -> MaterialTheme.colorScheme.surface
        }
    val targetContentColor =
        when {
            isSelected -> MaterialTheme.colorScheme.onPrimaryContainer
            isCuentaSolicitada -> MaterialTheme.colorScheme.onErrorContainer
            isOcupada -> MaterialTheme.colorScheme.onTertiaryContainer
            else -> MaterialTheme.colorScheme.onSurface
        }
    val containerColor by animateColorAsState(targetContainerColor, label = "mesaContainer")
    val contentColor by animateColorAsState(targetContentColor, label = "mesaContent")
    val borderColor =
        when {
            isSelected -> MaterialTheme.colorScheme.primary
            isCuentaSolicitada -> MaterialTheme.colorScheme.error
            isOcupada -> MaterialTheme.colorScheme.tertiary
            else -> MaterialTheme.colorScheme.outlineVariant
        }
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().heightIn(min = MESA_CARD_MIN_HEIGHT),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = BorderStroke(if (isSelected) 2.dp else 1.dp, borderColor),
        elevation =
            CardDefaults.cardElevation(
                defaultElevation = if (isSelected) 4.dp else 0.dp,
            ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp).fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = mesa.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = contentColor,
                    )
                    mesa.displayCode?.let { code ->
                        MesaSubtitle(text = code, colorOverride = contentColor)
                    }
                }
                if (estadoOperativo != null) {
                    MesaStatusChip(estado = estadoOperativo)
                }
            }
            MesaCapacityRow(capacidad = mesa.capacidad, contentColor = contentColor)
            mesa.forma?.let { forma ->
                MesaShapeTag(forma = forma, contentColor = contentColor)
            }
        }
    }
}

@Composable
private fun MesaSubtitle(
    text: String,
    colorOverride: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = colorOverride,
        maxLines = 1,
    )
}

@Composable
private fun MesaCapacityRow(
    capacidad: Int,
    contentColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = Icons.Default.People,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint =
                if (contentColor == MaterialTheme.colorScheme.onSurface) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    contentColor
                },
        )
        Spacer(modifier = Modifier.width(4.dp))
        MesaSubtitle(text = "Capacidad: $capacidad", colorOverride = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Estado operativo con icono y texto; el color nunca es la única señal visual. */
@Composable
private fun MesaStatusChip(estado: String) {
    val visual =
        when (estado) {
            ESTADO_CUENTA_SOLICITADA ->
                Triple("Cuenta", PosVisualTone.Error, Icons.AutoMirrored.Filled.ReceiptLong)
            EstadoMesaOperativo.OCUPADA ->
                Triple("Ocupada", PosVisualTone.Warning, Icons.Default.TableRestaurant)
            else ->
                Triple("Disponible", PosVisualTone.Success, Icons.Default.CheckCircle)
        }
    PosStatusBadge(label = visual.first, tone = visual.second, icon = visual.third)
}

@Composable
private fun MesaShapeTag(
    forma: String,
    contentColor: androidx.compose.ui.graphics.Color,
) {
    Surface(
        shape = PosExtraShapes.Pill,
        color = contentColor.copy(alpha = 0.08f),
    ) {
        Text(
            text = forma,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
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

@Preview(showBackground = true, backgroundColor = 0xFFF5F5F5)
@Composable
fun MesaCardPreview() {
    PosTheme {
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
