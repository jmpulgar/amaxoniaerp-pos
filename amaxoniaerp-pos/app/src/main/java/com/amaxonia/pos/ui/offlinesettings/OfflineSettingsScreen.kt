package com.amaxonia.pos.ui.offlinesettings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.People
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material.icons.rounded.Deselect
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.amaxonia.pos.domain.repository.OfflineSettingsStatus
import com.amaxonia.pos.ui.theme.ConfirmedContainer
import com.amaxonia.pos.ui.theme.ConfirmedContent
import com.amaxonia.pos.ui.theme.NeutralGray
import com.amaxonia.pos.ui.theme.OnlineGreen

/**
 * Pantalla de configuración para el Modo Offline y sincronización de catálogos locales (ADR-008).
 * Permite definir si se trabaja offline y el alcance de descarga para productos y clientes.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OfflineSettingsScreen(
    viewModel: OfflineSettingsViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.start()
        viewModel.refreshPreview()
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Ajustes de Visibilidad",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Volver",
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        bottomBar = {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                tonalElevation = 8.dp,
                shadowElevation = 8.dp,
                color = MaterialTheme.colorScheme.surface,
            ) {
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                            .navigationBarsPadding(),
                ) {
                    Button(
                        onClick = viewModel::apply,
                        enabled = state.status != OfflineSettingsStatus.APPLYING,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(52.dp),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        if (state.status == OfflineSettingsStatus.APPLYING) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.5.dp,
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text("Sincronizando catálogo...", fontWeight = FontWeight.Bold)
                        } else {
                            Icon(
                                imageVector = if (state.syncEnabled) Icons.Rounded.CloudSync else Icons.Rounded.Save,
                                contentDescription = null,
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (state.syncEnabled) "Guardar y Sincronizar" else "Guardar Ajustes de Visibilidad",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                            )
                        }
                    }
                }
            }
        },
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Mensaje de estado o resultado
            state.message?.let { message ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp),
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            // Interruptor Maestro: Modo Catálogo Offline (controla si se descarga a local)
            MasterOfflineCard(
                syncEnabled = state.syncEnabled,
                onSyncEnabledChange = viewModel::setSyncEnabled,
            )

            // Catálogo de Productos (SIEMPRE VISIBLE - define qué productos se muestran en el POS)
            ScopeCard(
                icon = Icons.Rounded.Inventory2,
                title = "Catálogo de Productos",
                subtitle = "Selecciona qué departamentos se mostrarán en el POS" +
                    if (state.syncEnabled) " y se descargarán para ventas sin red." else ".",
                modeAll = state.productModeAll,
                onModeAllSelected = { viewModel.setProductModeAll(true) },
                onCustomSelected = { viewModel.setProductModeAll(false) },
                customOptionLabel = "Por departamentos",
                allOptionSubtitle = "Muestra todos los productos disponibles en el POS",
                customOptionSubtitle = "Elige departamentos específicos a mostrar",
                selectedCount = state.selectedDepartmentIds.size,
                totalCount = state.departments.size,
                onSelectAll = {
                    viewModel.selectAllDepartments(state.departments.map { it.id }.toSet())
                },
                onClearAll = viewModel::clearDepartments,
                previewText = state.productPreview?.let {
                    if (state.syncEnabled) "≈ $it productos calculados para descarga"
                    else "≈ $it productos visibles"
                },
                items = state.departments,
                itemKey = { it.id.toString() },
                itemLabel = { it.name },
                isItemSelected = { it.id in state.selectedDepartmentIds },
                onItemToggle = { viewModel.toggleDepartment(it.id) },
                emptyMessage = "No hay departamentos disponibles (conéctate a internet para cargarlos).",
            )

            // Directorio de Clientes (SIEMPRE VISIBLE - define qué clientes se muestran en el POS)
            ScopeCard(
                icon = Icons.Rounded.People,
                title = "Directorio de Clientes",
                subtitle = "Selecciona qué clientes se mostrarán en el POS" +
                    if (state.syncEnabled) " y se descargarán para facturar sin red." else ".",
                modeAll = state.clientModeAll,
                onModeAllSelected = { viewModel.setClientModeAll(true) },
                onCustomSelected = { viewModel.setClientModeAll(false) },
                customOptionLabel = "Por sucursales",
                allOptionSubtitle = "Muestra todos los clientes de la empresa",
                customOptionSubtitle = "Solo clientes asignados a sucursales elegidas",
                selectedCount = state.selectedSucursalIds.size,
                totalCount = state.sucursales.size,
                onSelectAll = {
                    viewModel.selectAllSucursales(state.sucursales.map { it.id }.toSet())
                },
                onClearAll = viewModel::clearSucursales,
                previewText = state.clientPreview?.let {
                    if (state.syncEnabled) "≈ $it clientes calculados para descarga"
                    else "≈ $it clientes visibles"
                },
                items = state.sucursales,
                itemKey = { it.id },
                itemLabel = { it.nombre ?: it.id },
                isItemSelected = { it.id in state.selectedSucursalIds },
                onItemToggle = { viewModel.toggleSucursal(it.id) },
                emptyMessage = "No hay sucursales disponibles (conéctate a internet para cargarlas).",
            )

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

/** Tarjeta superior con el Switch maestro y estado visual claro */
@Composable
private fun MasterOfflineCard(
    syncEnabled: Boolean,
    onSyncEnabledChange: (Boolean) -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    modifier = Modifier.weight(1f).padding(end = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier =
                            Modifier
                                .size(44.dp)
                                .background(
                                    color =
                                        if (syncEnabled) {
                                            MaterialTheme.colorScheme.primaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.surfaceVariant
                                        },
                                    shape = CircleShape,
                                ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = if (syncEnabled) Icons.Rounded.CloudSync else Icons.Rounded.WifiOff,
                            contentDescription = null,
                            tint =
                                if (syncEnabled) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    NeutralGray
                                },
                            modifier = Modifier.size(24.dp),
                        )
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column {
                        Text(
                            text = "Modo Catálogo Offline",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            text =
                                if (syncEnabled) {
                                    "Descarga productos y clientes a este terminal para ventas sin internet."
                                } else {
                                    "Deshabilitado: Las ventas se consultarán en tiempo real en línea."
                                },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Switch(
                    checked = syncEnabled,
                    onCheckedChange = onSyncEnabledChange,
                )
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 12.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
            )

            // Chip de estado informativo
            if (syncEnabled) {
                Surface(
                    color = ConfirmedContainer,
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.CheckCircle,
                            contentDescription = null,
                            tint = OnlineGreen,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Offline Activo: configura abajo qué catálogos deseas almacenar.",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            color = ConfirmedContent,
                        )
                    }
                }
            } else {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Solo en línea: se ahorra memoria local y no se sincronizan catálogos.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

/** Tarjeta de alcance con selector de modo (Todos vs Personalizado) y lista de checkboxes */
@Composable
private fun <T> ScopeCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    modeAll: Boolean,
    onModeAllSelected: () -> Unit,
    onCustomSelected: () -> Unit,
    customOptionLabel: String,
    allOptionSubtitle: String,
    customOptionSubtitle: String,
    selectedCount: Int,
    totalCount: Int,
    onSelectAll: () -> Unit,
    onClearAll: () -> Unit,
    previewText: String?,
    items: List<T>,
    itemKey: (T) -> String,
    itemLabel: (T) -> String,
    isItemSelected: (T) -> Boolean,
    onItemToggle: (T) -> Unit,
    emptyMessage: String,
) {
    var searchQuery by remember { mutableStateOf("") }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // Encabezado de la tarjeta
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier =
                        Modifier
                            .size(38.dp)
                            .background(
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                shape = CircleShape,
                            ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = title,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // Selector de modo táctil para POS: 2 tarjetas de opción clara
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                ModeSelectionButton(
                    modifier = Modifier.weight(1f),
                    title = "Todos",
                    subtitle = allOptionSubtitle,
                    icon = Icons.Rounded.DoneAll,
                    selected = modeAll,
                    onClick = onModeAllSelected,
                )
                ModeSelectionButton(
                    modifier = Modifier.weight(1f),
                    title = customOptionLabel,
                    subtitle = customOptionSubtitle,
                    icon = Icons.Rounded.Tune,
                    selected = !modeAll,
                    onClick = onCustomSelected,
                )
            }

            // Si se elige 'Todos'
            if (modeAll) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(10.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Se sincronizarán todos los elementos existentes en la nube.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else {
                // Modo Personalizado: lista de elementos con checkboxes y buscador
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    // Barra de resumen y botones rápidos
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "$selectedCount de $totalCount seleccionados",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            IconButton(
                                onClick = onSelectAll,
                                enabled = selectedCount < totalCount,
                                modifier = Modifier.size(36.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.SelectAll,
                                    contentDescription = "Marcar todos",
                                    tint =
                                        if (selectedCount < totalCount) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.outlineVariant
                                        },
                                    modifier = Modifier.size(22.dp),
                                )
                            }
                            IconButton(
                                onClick = onClearAll,
                                enabled = selectedCount > 0,
                                modifier = Modifier.size(36.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Deselect,
                                    contentDescription = "Limpiar selección",
                                    tint =
                                        if (selectedCount > 0) {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        } else {
                                            MaterialTheme.colorScheme.outlineVariant
                                        },
                                    modifier = Modifier.size(22.dp),
                                )
                            }
                        }
                    }

                    if (totalCount == 0) {
                        Text(
                            text = emptyMessage,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 8.dp),
                        )
                    } else {
                        // Buscador si hay más de 5 elementos
                        if (totalCount > 5) {
                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                modifier = Modifier.fillMaxWidth(),
                                placeholder = { Text("Buscar...", style = MaterialTheme.typography.bodySmall) },
                                leadingIcon = {
                                    Icon(Icons.Rounded.Search, contentDescription = "Buscar", modifier = Modifier.size(18.dp))
                                },
                                trailingIcon = {
                                    if (searchQuery.isNotEmpty()) {
                                        IconButton(onClick = { searchQuery = "" }) {
                                            Icon(Icons.Rounded.Clear, contentDescription = "Borrar", modifier = Modifier.size(18.dp))
                                        }
                                    }
                                },
                                singleLine = true,
                                shape = RoundedCornerShape(10.dp),
                            )
                        }

                        val filteredItems =
                            if (searchQuery.isBlank()) {
                                items
                            } else {
                                items.filter { itemLabel(it).contains(searchQuery, ignoreCase = true) }
                            }

                        // Contenedor delimitado y scrolleable para evitar listas eternas
                        Box(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 200.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f))
                                    .border(
                                        width = 1.dp,
                                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                                        shape = RoundedCornerShape(8.dp),
                                    )
                                    .verticalScroll(rememberScrollState())
                                    .padding(4.dp),
                        ) {
                            if (filteredItems.isEmpty()) {
                                Text(
                                    text = "No se encontraron elementos",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(12.dp),
                                )
                            } else {
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(2.dp),
                                ) {
                                    filteredItems.forEach { item ->
                                        val selected = isItemSelected(item)
                                        Surface(
                                            modifier =
                                                Modifier
                                                    .fillMaxWidth()
                                                    .clickable { onItemToggle(item) },
                                            shape = RoundedCornerShape(6.dp),
                                            color =
                                                if (selected) {
                                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
                                                } else {
                                                    MaterialTheme.colorScheme.surface
                                                },
                                        ) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 2.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                            ) {
                                                Checkbox(
                                                    checked = selected,
                                                    onCheckedChange = { onItemToggle(item) },
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(
                                                    text = itemLabel(item),
                                                    style = MaterialTheme.typography.bodySmall,
                                                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Previsualización de elementos a descargar
                        previewText?.let { preview ->
                            Surface(
                                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f),
                                shape = RoundedCornerShape(8.dp),
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.CloudSync,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.secondary,
                                        modifier = Modifier.size(18.dp),
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = preview,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Medium,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Botón / tarjeta de selección para alternar entre 'Todos' y 'Personalizado' */
@Composable
private fun ModeSelectionButton(
    modifier: Modifier = Modifier,
    title: String,
    subtitle: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val borderColor =
        if (selected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.outlineVariant
        }
    val containerColor =
        if (selected) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
        } else {
            MaterialTheme.colorScheme.surface
        }

    OutlinedCard(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(if (selected) 2.dp else 1.dp, borderColor),
        colors = CardDefaults.outlinedCardColors(containerColor = containerColor),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (selected) MaterialTheme.colorScheme.primary else NeutralGray,
                    modifier = Modifier.size(20.dp),
                )
                if (selected) {
                    Icon(
                        imageVector = Icons.Rounded.CheckCircle,
                        contentDescription = "Seleccionado",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
            Text(
                text = title,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.bodyMedium,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp,
                lineHeight = 14.sp,
            )
        }
    }
}
