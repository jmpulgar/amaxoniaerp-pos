package com.amaxonia.erp.ui.sucursales

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.amaxonia.erp.data.remote.SaveSucursalRequest
import com.amaxonia.erp.domain.model.Sucursal
import com.amaxonia.erp.ui.components.PosEmptyState
import com.amaxonia.erp.ui.components.PosFeedbackCard
import com.amaxonia.erp.ui.components.PosStatusBadge
import com.amaxonia.erp.ui.components.PosVisualAction
import com.amaxonia.erp.ui.components.PosVisualTone
import com.amaxonia.erp.ui.components.isLandscape
import com.amaxonia.erp.ui.theme.PosExtraShapes

@Composable
fun SucursalesScreen(
    viewModel: SucursalesViewModel,
    modifier: Modifier = Modifier,
    onSucursalChanged: ((String) -> Unit)? = null,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val isLandscape = isLandscape()

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = if (isLandscape) 10.dp else 16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Sucursales de la Empresa",
                    style =
                        if (isLandscape) {
                            MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        } else {
                            MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                        },
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    text = "Configuración y selección de la sucursal operativa",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = viewModel::loadSucursales) {
                    Icon(Icons.Default.Refresh, contentDescription = "Refrescar")
                }
                Spacer(modifier = Modifier.width(4.dp))
                Button(
                    onClick = viewModel::openCreateDialog,
                    shape = PosExtraShapes.InputRadius,
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Nueva")
                }
            }
        }

        Spacer(modifier = Modifier.height(if (isLandscape) 8.dp else 14.dp))

        if (state.error != null) {
            PosFeedbackCard(
                title = "Error al cargar sucursales",
                message = state.error!!,
                tone = PosVisualTone.Error,
                action = PosVisualAction(label = "Reintentar", onClick = viewModel::loadSucursales),
            )
            Spacer(modifier = Modifier.height(10.dp))
        }

        if (state.isLoading && state.sucursales.isEmpty()) {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        } else if (state.sucursales.isEmpty()) {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                PosEmptyState(
                    icon = Icons.Default.Storefront,
                    title = "Sin sucursales",
                    message = "No se encontraron sucursales registradas para esta empresa.",
                )
            }
        } else if (isLandscape) {
            // Cuadrícula de 2 columnas en modo horizontal
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .weight(1f),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(state.sucursales, key = { it.id }) { sucursal ->
                    val isSelected = state.activeSucursalId == sucursal.id
                    SucursalCard(
                        sucursal = sucursal,
                        isSelected = isSelected,
                        onSelect = {
                            viewModel.selectActiveSucursal(sucursal)
                            onSucursalChanged?.invoke(sucursal.nombre)
                        },
                        onEdit = { viewModel.openEditDialog(sucursal) },
                    )
                }
            }
        } else {
            // Monocolumna en modo vertical
            LazyColumn(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(state.sucursales, key = { it.id }) { sucursal ->
                    val isSelected = state.activeSucursalId == sucursal.id
                    SucursalCard(
                        sucursal = sucursal,
                        isSelected = isSelected,
                        onSelect = {
                            viewModel.selectActiveSucursal(sucursal)
                            onSucursalChanged?.invoke(sucursal.nombre)
                        },
                        onEdit = { viewModel.openEditDialog(sucursal) },
                    )
                }
            }
        }
    }

    if (state.showFormDialog) {
        SucursalFormDialog(
            initial = state.editingSucursal,
            isSaving = state.isSaving,
            errorMessage = state.formError,
            onDismiss = viewModel::dismissDialog,
            onSave = viewModel::saveSucursal,
        )
    }
}

@Composable
private fun SucursalCard(
    sucursal: Sucursal,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onEdit: () -> Unit,
) {
    val borderColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
    val containerColor =
        if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.05f) else MaterialTheme.colorScheme.surface

    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(PosExtraShapes.CardRadius)
                .clickable(onClick = onSelect),
        shape = PosExtraShapes.CardRadius,
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = BorderStroke(if (isSelected) 2.dp else 1.dp, borderColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                modifier = Modifier.size(42.dp),
                shape = CircleShape,
                color =
                    if (isSelected) {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                    } else {
                        MaterialTheme.colorScheme.secondaryContainer
                    },
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Storefront,
                        contentDescription = null,
                        tint =
                            if (isSelected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.secondary
                            },
                        modifier = Modifier.size(22.dp),
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = sucursal.nombre,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (isSelected) {
                        Spacer(modifier = Modifier.width(6.dp))
                        PosStatusBadge(
                            label = "ACTIVA",
                            tone = PosVisualTone.Success,
                        )
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Código: ${sucursal.codigo ?: sucursal.id}${if (sucursal.serie != null) " • Serie: ${sucursal.serie}" else ""}",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (!sucursal.descripcion.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = sucursal.descripcion,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            IconButton(onClick = onEdit) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = "Editar sucursal",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }

            RadioButton(
                selected = isSelected,
                onClick = onSelect,
                colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.primary),
            )
        }
    }
}

@Composable
private fun SucursalFormDialog(
    initial: Sucursal?,
    isSaving: Boolean,
    errorMessage: String?,
    onDismiss: () -> Unit,
    onSave: (SaveSucursalRequest) -> Unit,
) {
    var nombre by remember { mutableStateOf(initial?.nombre ?: "") }
    var codigo by remember { mutableStateOf(initial?.codigo ?: "") }
    var serie by remember { mutableStateOf(initial?.serie ?: "") }
    var codigoEmisor by remember { mutableStateOf(initial?.codigoSucursalEmisor ?: "") }
    var descripcion by remember { mutableStateOf(initial?.descripcion ?: "") }

    val isEditing = initial != null
    val isValid = nombre.isNotBlank()

    AlertDialog(
        onDismissRequest = { if (!isSaving) onDismiss() },
        shape = PosExtraShapes.DialogRadius,
        title = {
            Text(
                text = if (isEditing) "Editar Sucursal" else "Nueva Sucursal",
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleLarge,
            )
        },
        text = {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (errorMessage != null) {
                    PosFeedbackCard(
                        title = "Error",
                        message = errorMessage,
                        tone = PosVisualTone.Error,
                    )
                }

                OutlinedTextField(
                    value = nombre,
                    onValueChange = { nombre = it },
                    label = { Text("Nombre de Sucursal *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = PosExtraShapes.InputRadius,
                )

                OutlinedTextField(
                    value = codigo,
                    onValueChange = { codigo = it },
                    label = { Text("Código (ej: S1, 001)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = PosExtraShapes.InputRadius,
                )

                OutlinedTextField(
                    value = serie,
                    onValueChange = { serie = it },
                    label = { Text("Serie Fiscal (ej: A, 001)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = PosExtraShapes.InputRadius,
                )

                OutlinedTextField(
                    value = codigoEmisor,
                    onValueChange = { codigoEmisor = it },
                    label = { Text("Código Emisor DGI/PAC (Panamá)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = PosExtraShapes.InputRadius,
                )

                OutlinedTextField(
                    value = descripcion,
                    onValueChange = { descripcion = it },
                    label = { Text("Dirección / Descripción") },
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                    shape = PosExtraShapes.InputRadius,
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        SaveSucursalRequest(
                            sucursal = nombre.trim(),
                            codigo = codigo.trim().ifBlank { null },
                            serie = serie.trim().ifBlank { null },
                            codigoSucursalEmisor = codigoEmisor.trim().ifBlank { null },
                            descripcion = descripcion.trim().ifBlank { null },
                        ),
                    )
                },
                enabled = isValid && !isSaving,
                shape = PosExtraShapes.InputRadius,
            ) {
                if (isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                }
                Text("Guardar")
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                enabled = !isSaving,
                shape = PosExtraShapes.InputRadius,
            ) {
                Text("Cancelar")
            }
        },
    )
}
