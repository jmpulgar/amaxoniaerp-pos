package com.amaxonia.erp.ui.clients

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.amaxonia.erp.domain.model.Client
import com.amaxonia.erp.domain.model.TaxpayerType
import com.amaxonia.erp.ui.components.PosEmptyState
import com.amaxonia.erp.ui.components.PosFeedbackCard
import com.amaxonia.erp.ui.components.PosStatusBadge
import com.amaxonia.erp.ui.components.PosVisualAction
import com.amaxonia.erp.ui.components.PosVisualTone
import com.amaxonia.erp.ui.components.isLandscape
import com.amaxonia.erp.ui.theme.PosExtraShapes
import com.amaxonia.erp.ui.theme.PosPalette

@Composable
fun ClientListScreen(
    viewModel: ClientListViewModel,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val isLandscape = isLandscape()

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = if (isLandscape) 8.dp else 16.dp),
        ) {
            // Buscador estilizado con surfaceVariant y esquinas de 12dp
            OutlinedTextField(
                value = state.searchQuery,
                onValueChange = viewModel::onSearchQueryChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Buscar por nombre, código o identificación...") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Buscar",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                trailingIcon = {
                    if (state.searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.onSearchQueryChange("") }) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Limpiar",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                singleLine = true,
                shape = PosExtraShapes.InputRadius,
                colors =
                    OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                    ),
            )

            if (state.error != null) {
                Spacer(modifier = Modifier.height(8.dp))
                PosFeedbackCard(
                    title = "Error al cargar clientes",
                    message = state.error!!,
                    tone = PosVisualTone.Error,
                    action = PosVisualAction(label = "Reintentar", onClick = viewModel::retry),
                )
            }

            Spacer(modifier = Modifier.height(if (isLandscape) 8.dp else 12.dp))

            if (state.isLoading && state.clients.isEmpty()) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            } else if (state.clients.isEmpty()) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    PosEmptyState(
                        icon = Icons.Default.Person,
                        title = "Sin clientes",
                        message = "No se encontraron clientes registrados con los criterios de búsqueda.",
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
                    contentPadding = PaddingValues(bottom = 80.dp),
                ) {
                    items(state.clients, key = { it.id.ifBlank { it.code } }) { client ->
                        ClientCard(
                            client = client,
                            onClick = { viewModel.openEditForm(client) },
                        )
                    }

                    if (!state.endOfListReached) {
                        item {
                            Box(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 12.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (state.isLoading) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(24.dp),
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                } else {
                                    OutlinedButton(
                                        onClick = viewModel::loadMore,
                                        shape = PosExtraShapes.InputRadius,
                                    ) {
                                        Text("Cargar más")
                                    }
                                }
                            }
                        }
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
                    contentPadding = PaddingValues(bottom = 80.dp),
                ) {
                    items(state.clients, key = { it.id.ifBlank { it.code } }) { client ->
                        ClientCard(
                            client = client,
                            onClick = { viewModel.openEditForm(client) },
                        )
                    }

                    if (!state.endOfListReached) {
                        item {
                            Box(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 12.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (state.isLoading) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(24.dp),
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                } else {
                                    OutlinedButton(
                                        onClick = viewModel::loadMore,
                                        shape = PosExtraShapes.InputRadius,
                                    ) {
                                        Text("Cargar más")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Botón Flotante para Crear Cliente
        FloatingActionButton(
            onClick = viewModel::openCreateForm,
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = PosPalette.FixedWhite,
            shape = CircleShape,
            modifier =
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(20.dp),
        ) {
            Icon(Icons.Default.Add, contentDescription = "Nuevo Cliente")
        }
    }

    if (state.isFormOpen) {
        ClientFormDialog(
            client = state.editingClient,
            onDismiss = viewModel::closeForm,
            onConfirm = viewModel::saveClient,
        )
    }
}

@Composable
private fun ClientCard(
    client: Client,
    onClick: () -> Unit,
) {
    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(PosExtraShapes.CardRadius)
                .clickable(onClick = onClick),
        shape = PosExtraShapes.CardRadius,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Avatar Circular como en amaxoniaerp-pos
            Box(
                modifier =
                    Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(22.dp),
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = client.fullName.ifBlank { "Sin nombre" },
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f, fill = false),
                        maxLines = 1,
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    PosStatusBadge(
                        label = client.taxpayerType.label,
                        tone =
                            if (client.taxpayerType == TaxpayerType.JURIDICO) {
                                PosVisualTone.Success
                            } else {
                                PosVisualTone.Info
                            },
                    )
                }

                Spacer(modifier = Modifier.height(2.dp))

                if (client.identification.isNotBlank()) {
                    Text(
                        text = "Doc: ${client.identification}${if (client.dv.isNotBlank()) "-${client.dv}" else ""}",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                if (client.phone.isNotBlank() || client.email.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (client.phone.isNotBlank()) {
                            Icon(
                                Icons.Default.Phone,
                                contentDescription = null,
                                modifier = Modifier.size(12.dp),
                                tint = MaterialTheme.colorScheme.outline,
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = client.phone,
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        if (client.email.isNotBlank()) {
                            Icon(
                                Icons.Default.Email,
                                contentDescription = null,
                                modifier = Modifier.size(12.dp),
                                tint = MaterialTheme.colorScheme.outline,
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = client.email,
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ClientFormDialog(
    client: Client?,
    onDismiss: () -> Unit,
    onConfirm: (Client) -> Unit,
) {
    var identification by remember { mutableStateOf(client?.identification ?: "") }
    var name by remember { mutableStateOf(client?.name ?: "") }
    var lastName by remember { mutableStateOf(client?.lastName ?: "") }
    var phone by remember { mutableStateOf(client?.phone ?: "") }
    var email by remember { mutableStateOf(client?.email ?: "") }
    var address by remember { mutableStateOf(client?.address ?: "") }
    var isJuridico by remember { mutableStateOf(client?.taxpayerType == TaxpayerType.JURIDICO) }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = PosExtraShapes.DialogRadius,
        title = {
            Text(
                if (client == null) "Nuevo Cliente" else "Editar Cliente",
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleLarge,
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedTextField(
                    value = identification,
                    onValueChange = { identification = it },
                    label = { Text("Identificación / RIF *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = PosExtraShapes.InputRadius,
                    colors =
                        OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        ),
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nombre / Razón Social *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = PosExtraShapes.InputRadius,
                    colors =
                        OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        ),
                )
                OutlinedTextField(
                    value = lastName,
                    onValueChange = { lastName = it },
                    label = { Text("Apellido (opcional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = PosExtraShapes.InputRadius,
                    colors =
                        OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        ),
                )
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("Teléfono") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = PosExtraShapes.InputRadius,
                    colors =
                        OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        ),
                )
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Correo electrónico") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = PosExtraShapes.InputRadius,
                    colors =
                        OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        ),
                )
                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = { Text("Dirección") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = PosExtraShapes.InputRadius,
                    colors =
                        OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        ),
                )
            }
        },
        confirmButton = {
            val isFormValid = identification.isNotBlank() && name.isNotBlank()
            Button(
                onClick = {
                    if (isFormValid) {
                        onConfirm(
                            Client(
                                id = client?.id ?: "",
                                code = client?.code ?: "",
                                identification = identification.trim(),
                                name = name.trim(),
                                lastName = lastName.trim(),
                                phone = phone.trim(),
                                email = email.trim(),
                                address = address.trim(),
                                taxpayerType = if (isJuridico) TaxpayerType.JURIDICO else TaxpayerType.NATURAL,
                            ),
                        )
                    }
                },
                enabled = isFormValid,
                shape = PosExtraShapes.InputRadius,
            ) {
                Text("Guardar Cliente", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        },
    )
}
