package com.amaxonia.pos.ui.mesas

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Deck
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PointOfSale
import androidx.compose.material.icons.filled.TableRestaurant
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.amaxonia.pos.domain.model.mesas.Area
import com.amaxonia.pos.domain.model.mesas.Mesa
import com.amaxonia.pos.ui.theme.PosExtraShapes
import com.amaxonia.pos.ui.theme.PosStatusColors
import com.amaxonia.pos.ui.theme.PosTheme

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
                shape = PosExtraShapes.Pill,
                label = { Text(text = area.displayName, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                colors =
                    FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ),
            )
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
) {
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().heightIn(min = MESA_CARD_MIN_HEIGHT),
        shape = MaterialTheme.shapes.medium,
        colors =
            CardDefaults.cardColors(
                containerColor =
                    if (isSelected) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surface
                    },
            ),
        border =
            if (isSelected) {
                BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
            } else {
                CardDefaults.outlinedCardBorder()
            },
    ) {
        Column(
            modifier = Modifier.padding(14.dp).fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = mesa.displayName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color =
                    if (isSelected) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
            )
            mesa.displayCode?.let { code -> MesaSubtitle(text = code) }
            MesaCapacityRow(capacidad = mesa.capacidad)
            mesa.forma?.let { forma -> MesaShapeTag(forma = forma) }
        }
    }
}

@Composable
private fun MesaSubtitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
    )
}

@Composable
private fun MesaCapacityRow(capacidad: Int) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = Icons.Default.People,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.width(4.dp))
        MesaSubtitle(text = "Capacidad: $capacidad")
    }
}

@Composable
private fun MesaShapeTag(forma: String) {
    Surface(shape = PosExtraShapes.Pill, color = MaterialTheme.colorScheme.surfaceVariant) {
        Text(
            text = forma,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
    Column(
        modifier = modifier.fillMaxWidth().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier =
                Modifier
                    .size(72.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.extraLarge),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(32.dp),
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        action?.let {
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = it.onClick) { Text(it.label) }
        }
    }
}

/** Error con reintento explícito, para áreas y para mesas por separado. */
@Composable
fun MesasErrorState(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Default.ErrorOutline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.size(20.dp),
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = message,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            Spacer(modifier = Modifier.width(8.dp))
            OutlinedButton(onClick = onRetry) { Text("Reintentar") }
        }
    }
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
    Box(modifier = modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
    }
}

private val MESA_CARD_MIN_HEIGHT = 104.dp

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
