package com.amaxonia.erp.ui.caja

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import com.amaxonia.erp.domain.model.SaveCajaRequest
import com.amaxonia.erp.domain.model.Sucursal
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
import com.amaxonia.erp.domain.model.Caja
import com.amaxonia.erp.domain.model.CierreCajaSummary
import com.amaxonia.erp.ui.components.PosEmptyState
import com.amaxonia.erp.ui.components.PosFeedbackCard
import com.amaxonia.erp.ui.components.PosGradientButton
import com.amaxonia.erp.ui.components.PosSectionHeader
import com.amaxonia.erp.ui.components.PosStatusBadge
import com.amaxonia.erp.ui.components.PosVisualAction
import com.amaxonia.erp.ui.components.PosVisualTone
import com.amaxonia.erp.ui.components.isLandscape
import com.amaxonia.erp.ui.theme.PosExtraShapes
import com.amaxonia.erp.ui.theme.PosTextStyles
import java.util.Locale
import kotlin.math.abs

@Composable
fun CajasOverviewScreen(
    viewModel: CajasOverviewViewModel,
    modifier: Modifier = Modifier,
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
                    text = "Control de Cajas",
                    style =
                        if (isLandscape) {
                            MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        } else {
                            MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                        },
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    text = "Apertura, arqueo, cierre y administración de cajas",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = viewModel::loadCajas) {
                    Icon(Icons.Default.Refresh, contentDescription = "Refrescar")
                }
                Spacer(modifier = Modifier.width(4.dp))
                Button(
                    onClick = viewModel::openCreateCajaDialog,
                    shape = PosExtraShapes.InputRadius,
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Nueva Caja")
                }
            }
        }

        Spacer(modifier = Modifier.height(if (isLandscape) 8.dp else 14.dp))

        if (state.error != null) {
            PosFeedbackCard(
                title = "Error en control de caja",
                message = state.error!!,
                tone = PosVisualTone.Error,
                action = PosVisualAction(label = "Reintentar", onClick = viewModel::loadCajas),
            )
            Spacer(modifier = Modifier.height(10.dp))
        }

        if (isLandscape) {
            // Diseño de 2 Columnas para Modo Horizontal
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .weight(1f),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // Columna Izquierda: Caja Activa Destacada
                Column(
                    modifier =
                        Modifier
                            .weight(1.05f)
                            .fillMaxHeight()
                            .verticalScroll(rememberScrollState()),
                ) {
                    if (state.activeCaja != null) {
                        ActiveCajaFeaturedCard(
                            caja = state.activeCaja!!,
                            isOpen = state.isCajaOpen,
                            isDiaAnterior = state.isCajaDiaAnterior,
                            fechaApertura = state.cajaFechaApertura,
                            secuencia = state.activeSecuencia,
                            isRenovando = state.isRenovandoCaja,
                            onOpenApertura = viewModel::openAperturaDialog,
                            onOpenCierre = viewModel::openCierreDialog,
                            onRenovar = viewModel::renovarCajaDiaAnterior,
                            onEdit = { viewModel.openEditCajaDialog(state.activeCaja!!) },
                        )
                    } else {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = PosExtraShapes.FeaturedCardRadius,
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        ) {
                            PosEmptyState(
                                icon = Icons.Default.AccountBalanceWallet,
                                title = "Ninguna caja seleccionada",
                                message = "Selecciona una caja del panel derecho para comenzar.",
                            )
                        }
                    }
                }

                // Columna Derecha: Listado de Cajas Disponibles
                Column(
                    modifier =
                        Modifier
                            .weight(0.95f)
                            .fillMaxHeight(),
                ) {
                    PosSectionHeader(
                        title = "Cajas Disponibles",
                        subtitle = "Toca para activar en esta terminal",
                        icon = Icons.Default.AccountBalanceWallet,
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    if (state.isLoading && state.cajas.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        }
                    } else if (state.cajas.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            PosEmptyState(
                                icon = Icons.Default.AccountBalanceWallet,
                                title = "Sin cajas disponibles",
                                message = "No hay cajas configuradas para la sucursal actual.",
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            items(state.cajas, key = { it.idCaja }) { caja ->
                                val isSelected = state.activeCaja?.idCaja == caja.idCaja
                                CajaListItemCard(
                                    caja = caja,
                                    statusInfo = state.cajasStatusMap[caja.idCaja],
                                    isSelected = isSelected,
                                    onSelect = { viewModel.selectCaja(caja) },
                                    onEdit = { viewModel.openEditCajaDialog(caja) },
                                )
                            }
                        }
                    }
                }
            }
        } else {
            // Diseño Monocolumna para Modo Vertical
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .weight(1f),
            ) {
                state.activeCaja?.let { active ->
                    ActiveCajaFeaturedCard(
                        caja = active,
                        isOpen = state.isCajaOpen,
                        isDiaAnterior = state.isCajaDiaAnterior,
                        fechaApertura = state.cajaFechaApertura,
                        secuencia = state.activeSecuencia,
                        isRenovando = state.isRenovandoCaja,
                        onOpenApertura = viewModel::openAperturaDialog,
                        onOpenCierre = viewModel::openCierreDialog,
                        onRenovar = viewModel::renovarCajaDiaAnterior,
                        onEdit = { viewModel.openEditCajaDialog(active) },
                    )
                    Spacer(modifier = Modifier.height(18.dp))
                }

                PosSectionHeader(
                    title = "Todas las Cajas Disponibles",
                    subtitle = "Selecciona una caja para operar en esta terminal",
                    icon = Icons.Default.AccountBalanceWallet,
                )
                Spacer(modifier = Modifier.height(10.dp))

                if (state.isLoading && state.cajas.isEmpty()) {
                    Box(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .weight(1f),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    }
                } else if (state.cajas.isEmpty()) {
                    Box(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .weight(1f),
                        contentAlignment = Alignment.Center,
                    ) {
                        PosEmptyState(
                            icon = Icons.Default.AccountBalanceWallet,
                            title = "Sin cajas disponibles",
                            message = "No hay cajas configuradas para la sucursal y empresa actual.",
                        )
                    }
                } else {
                    LazyColumn(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(state.cajas, key = { it.idCaja }) { caja ->
                            val isSelected = state.activeCaja?.idCaja == caja.idCaja
                            CajaListItemCard(
                                caja = caja,
                                statusInfo = state.cajasStatusMap[caja.idCaja],
                                isSelected = isSelected,
                                onSelect = { viewModel.selectCaja(caja) },
                                onEdit = { viewModel.openEditCajaDialog(caja) },
                            )
                        }
                    }
                }
            }
        }
    }

    if (state.showAvisoCajaAnterior) {
        AvisoCajaAnteriorDialog(
            fechaApertura = state.cajaFechaApertura,
            isRenovando = state.isRenovandoCaja,
            onDismiss = viewModel::dismissAvisoCajaAnterior,
            onRenovar = viewModel::renovarCajaDiaAnterior,
        )
    }

    if (state.isAperturaDialogOpen) {
        AperturaCajaDialog(
            cajaName = state.activeCaja?.displayName ?: "",
            isLoading = state.isLoading,
            onDismiss = viewModel::closeAperturaDialog,
            onConfirm = viewModel::aperturarCaja,
        )
    }

    if (state.isCierreDialogOpen) {
        CierreCajaDialog(
            cajaName = state.activeCaja?.displayName ?: "",
            isLoadingSummary = state.isLoadingSummary,
            summary = state.cierreSummary,
            isLoading = state.isLoading,
            onDismiss = viewModel::closeCierreDialog,
            onConfirm = viewModel::cerrarCaja,
        )
    }

    if (state.showCajaFormDialog) {
        CajaFormDialog(
            initial = state.editingCaja,
            sucursales = state.sucursales,
            isSaving = state.isSavingCaja,
            errorMessage = state.cajaFormError,
            onDismiss = viewModel::dismissCajaFormDialog,
            onSave = viewModel::saveCaja,
        )
    }
}

@Composable
private fun ActiveCajaFeaturedCard(
    caja: Caja,
    isOpen: Boolean,
    isDiaAnterior: Boolean,
    fechaApertura: String?,
    secuencia: com.amaxonia.erp.domain.model.CajaSecuencia?,
    isRenovando: Boolean,
    onOpenApertura: () -> Unit,
    onOpenCierre: () -> Unit,
    onRenovar: () -> Unit,
    onEdit: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = PosExtraShapes.FeaturedCardRadius,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f),
                ) {
                    Surface(
                        modifier = Modifier.size(44.dp),
                        shape = CircleShape,
                        color =
                            if (isOpen) {
                                if (isDiaAnterior) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.errorContainer
                            },
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (isOpen) Icons.Default.LockOpen else Icons.Default.Lock,
                                contentDescription = null,
                                tint =
                                    if (isOpen) {
                                        if (isDiaAnterior) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onPrimaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.onErrorContainer
                                    },
                                modifier = Modifier.size(22.dp),
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = caja.displayName,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        caja.sucursalNombre?.let { suc ->
                            Text(
                                text = "Sucursal: $suc",
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isOpen && isDiaAnterior) {
                        PosStatusBadge(
                            label = "VENCIDA",
                            tone = PosVisualTone.Warning,
                            icon = Icons.Default.Warning,
                        )
                    } else {
                        PosStatusBadge(
                            label = if (isOpen) "ABIERTA" else "CERRADA",
                            tone = if (isOpen) PosVisualTone.Success else PosVisualTone.Error,
                            icon = if (isOpen) Icons.Default.LockOpen else Icons.Default.Lock,
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    IconButton(onClick = onEdit) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Editar caja",
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (isOpen && isDiaAnterior) {
                Surface(
                    shape = PosExtraShapes.InputRadius,
                    color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.4f),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Warning,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Caja vencida (Abierta desde ${fechaApertura ?: "un día anterior"})",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Esta caja pertenece a una jornada anterior. Para registrar las ventas en la jornada correspondiente y mantener los reportes al día, renueva la caja con un clic.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = onRenovar,
                            enabled = !isRenovando,
                            shape = PosExtraShapes.InputRadius,
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            if (isRenovando) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onTertiary,
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Renovando…")
                            } else {
                                Text("Cerrar jornada anterior y abrir hoy")
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            if (isOpen && secuencia != null) {
                Surface(
                    shape = PosExtraShapes.InputRadius,
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "Monto de apertura:",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = String.format(Locale.US, "$%.2f", secuencia.montoApertura),
                                style =
                                    PosTextStyles.priceTileMedium.copy(
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold,
                                    ),
                            )
                        }
                        if (secuencia.usuarioApertura.isNotBlank()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    text = "Aperturado por:",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    text = secuencia.usuarioApertura,
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            // Botones de acción según estado
            if (!isOpen) {
                PosGradientButton(
                    text = "Aperturar Caja para Ventas",
                    onClick = onOpenApertura,
                    modifier = Modifier.fillMaxWidth(),
                    shape = PosExtraShapes.InputRadius,
                )
            } else {
                OutlinedButton(
                    onClick = onOpenCierre,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                    shape = PosExtraShapes.InputRadius,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
                ) {
                    Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Cerrar Caja (Arqueo Final)", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun CajaListItemCard(
    caja: Caja,
    statusInfo: CajaStatusBadgeInfo?,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onEdit: () -> Unit,
) {
    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(PosExtraShapes.CardRadius)
                .clickable(onClick = onSelect),
        shape = PosExtraShapes.CardRadius,
        colors =
            CardDefaults.cardColors(
                containerColor =
                    if (isSelected) {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.05f)
                    } else {
                        MaterialTheme.colorScheme.surface
                    },
            ),
        border =
            BorderStroke(
                if (isSelected) 2.dp else 1.dp,
                if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
            ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = caja.displayName,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "ID: ${caja.idCaja}${if (caja.sucursalNombre != null) " • ${caja.sucursalNombre}" else ""}",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onEdit) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Editar caja",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
                if (statusInfo != null) {
                    Spacer(modifier = Modifier.width(4.dp))
                    when {
                        statusInfo.isOpen && statusInfo.isDiaAnterior -> {
                            PosStatusBadge(
                                label = "VENCIDA",
                                tone = PosVisualTone.Warning,
                                icon = Icons.Default.Warning,
                            )
                        }
                        statusInfo.isOpen -> {
                            PosStatusBadge(
                                label = "ABIERTA",
                                tone = PosVisualTone.Success,
                                icon = Icons.Default.LockOpen,
                            )
                        }
                        else -> {
                            PosStatusBadge(
                                label = "CERRADA",
                                tone = PosVisualTone.Neutral,
                                icon = Icons.Default.Lock,
                            )
                        }
                    }
                }
                if (isSelected) {
                    Spacer(modifier = Modifier.width(4.dp))
                    PosStatusBadge(
                        label = "EN USO",
                        tone = PosVisualTone.Success,
                    )
                }
            }
        }
    }
}

@Composable
private fun AperturaCajaDialog(
    cajaName: String,
    isLoading: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (Double) -> Unit,
) {
    var amountText by remember { mutableStateOf("0.00") }

    AlertDialog(
        onDismissRequest = { if (!isLoading) onDismiss() },
        shape = PosExtraShapes.DialogRadius,
        title = {
            Text("Aperturar $cajaName", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "Indica el monto de efectivo base con el que inicia la jornada operativa.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = { Text("Fondo Inicial de Efectivo ($)") },
                    singleLine = true,
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
            Button(
                onClick = {
                    val amount = amountText.toDoubleOrNull() ?: 0.0
                    onConfirm(amount)
                },
                enabled = !isLoading,
                shape = PosExtraShapes.InputRadius,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Aperturando…")
                } else {
                    Text("Aperturar Ahora", fontWeight = FontWeight.Bold)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isLoading) { Text("Cancelar") }
        },
    )
}

@Composable
fun AvisoCajaAnteriorDialog(
    fechaApertura: String?,
    isRenovando: Boolean,
    onDismiss: () -> Unit,
    onRenovar: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = { if (!isRenovando) onDismiss() },
        shape = PosExtraShapes.DialogRadius,
        icon = {
            Surface(
                modifier = Modifier.size(52.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(28.dp),
                    )
                }
            }
        },
        title = {
            Text(
                text = "Caja Vencida (Jornada anterior)",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "La caja actual está abierta desde el ${fechaApertura ?: "un día anterior"} y corresponde a una jornada anterior (Vencida).",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "Para registrar las ventas en la jornada correspondiente y mantener los reportes al día, puedes realizar el cierre y renovación de la caja ahora, o continuar operando.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onRenovar,
                enabled = !isRenovando,
                shape = PosExtraShapes.InputRadius,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            ) {
                if (isRenovando) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Renovando…")
                } else {
                    Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Cerrar y abrir hoy")
                }
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                enabled = !isRenovando,
                shape = PosExtraShapes.InputRadius,
            ) {
                Text("Continuar")
            }
        },
    )
}

@Composable
private fun CierreCajaDialog(
    cajaName: String,
    isLoadingSummary: Boolean,
    summary: CierreCajaSummary?,
    isLoading: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (Double, String) -> Unit,
) {
    var efectivoFinalText by remember { mutableStateOf("") }
    var observaciones by remember { mutableStateOf("") }

    val efectivoEsperado = summary?.montoEfectivoTotal ?: 0.0
    val contado = efectivoFinalText.toDoubleOrNull() ?: 0.0
    val diferencia = if (efectivoFinalText.isNotBlank()) contado - efectivoEsperado else 0.0

    AlertDialog(
        onDismissRequest = { if (!isLoading) onDismiss() },
        shape = PosExtraShapes.DialogRadius,
        title = {
            Text("Cerrar $cajaName (Arqueo)", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge)
        },
        text = {
            if (isLoadingSummary) {
                Box(
                    modifier = Modifier.fillMaxWidth().height(160.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (summary != null) {
                        Surface(
                            shape = PosExtraShapes.InputRadius,
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Text("Fondo de apertura:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(String.format(Locale.US, "$%.2f", summary.openAmount), fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall)
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Text("Ventas en efectivo:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(String.format(Locale.US, "$%.2f", summary.montoEfectivoVentas), fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall)
                                }
                                if (summary.montoOtrosTotal > 0.0) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                    ) {
                                        Text("Otras formas de pago:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text(String.format(Locale.US, "$%.2f", summary.montoOtrosTotal), fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Text("Efectivo esperado en gaveta:", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                    Text(
                                        String.format(Locale.US, "$%.2f", summary.montoEfectivoTotal),
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            }
                        }
                    }

                    Text(
                        text = "Ingresa el conteo físico de efectivo en gaveta:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    OutlinedTextField(
                        value = efectivoFinalText,
                        onValueChange = { efectivoFinalText = it },
                        label = { Text("Efectivo Contado en Gaveta ($) *") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = PosExtraShapes.InputRadius,
                        colors =
                            OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            ),
                    )

                    if (efectivoFinalText.isNotBlank()) {
                        val diffTone = when {
                            abs(diferencia) < 0.001 -> PosVisualTone.Success
                            diferencia > 0.0 -> PosVisualTone.Info
                            else -> PosVisualTone.Error
                        }
                        val diffLabel = when {
                            abs(diferencia) < 0.001 -> "Cuadrado: $0.00"
                            diferencia > 0.0 -> String.format(Locale.US, "Sobrante: +$%.2f", diferencia)
                            else -> String.format(Locale.US, "Faltante: -$%.2f", abs(diferencia))
                        }
                        PosStatusBadge(label = diffLabel, tone = diffTone)
                    }

                    OutlinedTextField(
                        value = observaciones,
                        onValueChange = { observaciones = it },
                        label = { Text("Observaciones / Notas") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = PosExtraShapes.InputRadius,
                        colors =
                            OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            ),
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val efectivo = efectivoFinalText.toDoubleOrNull() ?: 0.0
                    onConfirm(efectivo, observaciones.trim())
                },
                enabled = !isLoading && !isLoadingSummary,
                shape = PosExtraShapes.InputRadius,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onError,
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Cerrando…", fontWeight = FontWeight.Bold)
                } else {
                    Text("Confirmar Cierre", fontWeight = FontWeight.Bold)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isLoading) { Text("Cancelar") }
        },
    )
}

@Composable
private fun CajaFormDialog(
    initial: Caja?,
    sucursales: List<Sucursal>,
    isSaving: Boolean,
    errorMessage: String?,
    onDismiss: () -> Unit,
    onSave: (SaveCajaRequest) -> Unit,
) {
    var cajaNombre by remember { mutableStateOf(initial?.caja ?: initial?.displayName ?: "") }
    var codigo by remember { mutableStateOf(initial?.codCaja ?: "") }
    var descripcion by remember { mutableStateOf(initial?.descripcion ?: "") }
    var serieCaja by remember { mutableStateOf(initial?.serieCaja?.takeIf { it.isNotBlank() } ?: "1") }
    var idSucursal by remember { mutableStateOf(initial?.idSucursal ?: sucursales.firstOrNull()?.id?.toIntOrNull()) }
    var fondoAperturaText by remember { mutableStateOf("0.00") }
    var impresoraModelo by remember { mutableStateOf("") }
    var codigoSucursalEmisor by remember { mutableStateOf(initial?.codigoSucursalEmisor ?: "") }
    var puntoFacturacionFiscal by remember { mutableStateOf("") }
    var codAlmacenText by remember { mutableStateOf(initial?.codAlmacen?.toString() ?: "") }
    var isActivo by remember { mutableStateOf(initial?.estatus != 0) }

    var sucursalExpanded by remember { mutableStateOf(false) }

    val isEditing = initial != null
    val isValid = cajaNombre.isNotBlank() && serieCaja.isNotBlank()

    AlertDialog(
        onDismissRequest = { if (!isSaving) onDismiss() },
        shape = PosExtraShapes.DialogRadius,
        title = {
            Text(
                text = if (isEditing) "Editar Caja" else "Nueva Caja",
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
                    value = cajaNombre,
                    onValueChange = { cajaNombre = it },
                    label = { Text("Nombre de la Caja *") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = PosExtraShapes.InputRadius,
                )

                OutlinedTextField(
                    value = codigo,
                    onValueChange = { codigo = it },
                    label = { Text("Código de Caja (ej: CJ-01)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = PosExtraShapes.InputRadius,
                )

                OutlinedTextField(
                    value = serieCaja,
                    onValueChange = { serieCaja = it },
                    label = { Text("Serie de Facturación * (ej: 1, 01)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = PosExtraShapes.InputRadius,
                )

                // Selector desplegable de sucursal
                val selectedSucursal = sucursales.find { it.id.toIntOrNull() == idSucursal }
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = selectedSucursal?.nombre ?: "Sin sucursal seleccionada",
                        onValueChange = {},
                        label = { Text("Sucursal Asignada") },
                        readOnly = true,
                        trailingIcon = {
                            IconButton(onClick = { sucursalExpanded = true }) {
                                Icon(Icons.Default.ArrowDropDown, contentDescription = "Seleccionar sucursal")
                            }
                        },
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clickable { sucursalExpanded = true },
                        shape = PosExtraShapes.InputRadius,
                    )
                    DropdownMenu(
                        expanded = sucursalExpanded,
                        onDismissRequest = { sucursalExpanded = false },
                    ) {
                        sucursales.forEach { suc ->
                            DropdownMenuItem(
                                text = { Text(suc.nombre) },
                                onClick = {
                                    idSucursal = suc.id.toIntOrNull()
                                    sucursalExpanded = false
                                },
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = descripcion,
                    onValueChange = { descripcion = it },
                    label = { Text("Ubicación / Descripción") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = PosExtraShapes.InputRadius,
                )

                OutlinedTextField(
                    value = fondoAperturaText,
                    onValueChange = { fondoAperturaText = it },
                    label = { Text("Fondo Base de Apertura ($)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = PosExtraShapes.InputRadius,
                )

                OutlinedTextField(
                    value = impresoraModelo,
                    onValueChange = { impresoraModelo = it },
                    label = { Text("Modelo de Impresora (opcional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = PosExtraShapes.InputRadius,
                )

                // Campos fiscales Panamá (DGI/PAC)
                OutlinedTextField(
                    value = codigoSucursalEmisor,
                    onValueChange = { codigoSucursalEmisor = it },
                    label = { Text("Cód. Sucursal Emisor DGI PAC (Panamá)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = PosExtraShapes.InputRadius,
                )

                OutlinedTextField(
                    value = puntoFacturacionFiscal,
                    onValueChange = { puntoFacturacionFiscal = it },
                    label = { Text("Punto Facturación Fiscal PAC (Panamá)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = PosExtraShapes.InputRadius,
                )

                // Almacén Venezuela
                OutlinedTextField(
                    value = codAlmacenText,
                    onValueChange = { codAlmacenText = it },
                    label = { Text("Cód. Almacén de Inventario (Venezuela)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = PosExtraShapes.InputRadius,
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = if (isActivo) "Caja Habilitada (Activa)" else "Caja Inactiva",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Switch(
                        checked = isActivo,
                        onCheckedChange = { isActivo = it },
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val fondo = fondoAperturaText.toDoubleOrNull()
                    val almacen = codAlmacenText.toIntOrNull()
                    onSave(
                        SaveCajaRequest(
                            id = initial?.idCaja,
                            codigo = codigo.trim().ifBlank { null },
                            caja = cajaNombre.trim(),
                            descripcion = descripcion.trim().ifBlank { null },
                            idSucursal = idSucursal,
                            serieCaja = serieCaja.trim(),
                            fondoApertura = fondo,
                            impresoraModelo = impresoraModelo.trim().ifBlank { null },
                            codigoSucursalEmisor = codigoSucursalEmisor.trim().ifBlank { null },
                            puntoFacturacionFiscal = puntoFacturacionFiscal.trim().ifBlank { null },
                            codAlmacen = almacen,
                            activo = if (isActivo) 1 else 0,
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
