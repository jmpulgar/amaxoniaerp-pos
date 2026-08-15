@file:Suppress("LongMethod", "LongParameterList", "MagicNumber")

package com.amaxonia.pos.ui.mesas

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.RestaurantMenu
import androidx.compose.material.icons.filled.Splitscreen
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.amaxonia.pos.domain.model.mesas.CuentaMesaResponse
import com.amaxonia.pos.domain.model.mesas.EstadoCuentaMesa
import com.amaxonia.pos.domain.model.mesas.PedidoMesa
import com.amaxonia.pos.ui.common.components.AdaptiveAmountText
import com.amaxonia.pos.ui.common.components.PosEmptyState
import com.amaxonia.pos.ui.common.components.PosFeedbackCard
import com.amaxonia.pos.ui.common.components.PosLoadingState
import com.amaxonia.pos.ui.common.components.PosSectionHeader
import com.amaxonia.pos.ui.common.components.PosStatusBadge
import com.amaxonia.pos.ui.common.components.PosVisualAction
import com.amaxonia.pos.ui.common.components.PosVisualTone
import com.amaxonia.pos.ui.theme.PosTextStyles
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CuentaMesaScreen(
    mesaNombre: String,
    clientName: String?,
    viewModel: CuentaMesaViewModel,
    onBack: () -> Unit,
    onSelectClient: () -> Unit,
    onPay: (CuentaMesaResponse) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) {
        viewModel.load()
        viewModel.effects.collect { effect ->
            if (effect is CuentaMesaEffect.Pay) onPay(effect.cuenta)
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "Cuenta y división",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = mesaNombre,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentAlignment = Alignment.TopCenter,
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxHeight().widthIn(max = 840.dp).fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                item {
                    CuentaOverview(
                        activeAccounts = state.cuentasActivas.size,
                        availableProducts = state.pedidos.count { state.disponible(it) > 0.0 },
                    )
                }
                item {
                    ClientCard(
                        clientName = clientName,
                        onSelectClient = onSelectClient,
                    )
                }
                state.error?.let { message ->
                    item {
                        PosFeedbackCard(
                            title = "No pudimos actualizar la cuenta",
                            message = message,
                            tone = PosVisualTone.Error,
                        )
                    }
                }
                state.info?.let { message ->
                    item {
                        PosFeedbackCard(
                            title = "Cuenta actualizada",
                            message = message,
                            tone = PosVisualTone.Success,
                        )
                    }
                }
                if (state.isLoading && state.pedidos.isEmpty() && state.cuentas.isEmpty()) {
                    item { PosLoadingState("Cargando consumos y cuentas…") }
                } else {
                    item {
                        FullAccountAction(
                            isSaving = state.isSaving,
                            onClick = viewModel::crearCuentaCompleta,
                        )
                    }
                    item {
                        PosSectionHeader(
                            title = "Dividir consumo",
                            subtitle = "Elige cantidades para crear una cuenta separada",
                            icon = Icons.Default.Splitscreen,
                        )
                    }
                    val disponibles = state.pedidos.filter { state.disponible(it) > 0.0 }
                    if (disponibles.isEmpty()) {
                        item {
                            PosEmptyState(
                                icon = Icons.Default.RestaurantMenu,
                                title = "Sin productos disponibles para dividir",
                                message = "Los consumos pendientes aparecerán aquí.",
                            )
                        }
                    } else {
                        items(disponibles, key = { "pedido-${it.id}" }) { pedido ->
                            SplitProductCard(
                                pedido = pedido,
                                disponible = state.disponible(pedido),
                                value = state.cantidades[pedido.id].orEmpty(),
                                onValueChange = { viewModel.updateCantidad(pedido.id, it) },
                            )
                        }
                        item {
                            OutlinedButton(
                                onClick = viewModel::crearDivision,
                                enabled = !state.isSaving,
                                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("Crear división seleccionada")
                            }
                        }
                    }
                }

                if (state.cuentasActivas.isNotEmpty()) {
                    item {
                        PosSectionHeader(
                            title = "Cuentas por cobrar",
                            subtitle = "${state.cuentasActivas.size} pendientes de pago",
                            icon = Icons.AutoMirrored.Filled.ReceiptLong,
                        )
                    }
                    items(state.cuentasActivas, key = { "cuenta-${it.id}" }) { cuenta ->
                        CuentaActivaCard(
                            cuenta = cuenta,
                            canPay = clientName != null && !state.isSaving,
                            onPay = { viewModel.pagar(cuenta) },
                            onCancel = { viewModel.cancelar(cuenta) },
                        )
                    }
                    if (clientName == null) {
                        item {
                            PosFeedbackCard(
                                title = "Selecciona un cliente para cobrar",
                                message = "La cuenta está lista. Falta asociar el cliente de la factura.",
                                tone = PosVisualTone.Info,
                                action =
                                    PosVisualAction(
                                        label = "Seleccionar cliente",
                                        onClick = onSelectClient,
                                    ),
                            )
                        }
                    }
                }

                if (state.historicas.isNotEmpty()) {
                    item {
                        PosSectionHeader(
                            title = "Histórico de la mesa",
                            subtitle = "${state.historicas.size} cuentas finalizadas",
                            icon = Icons.Default.CheckCircle,
                        )
                    }
                    items(state.historicas, key = { "hist-${it.id}" }) { cuenta ->
                        HistoricalAccountRow(cuenta)
                    }
                }

                if (state.isSaving) {
                    item { PosLoadingState("Guardando cambios en la cuenta…") }
                }
            }
        }
    }
}

@Composable
private fun CuentaOverview(
    activeAccounts: Int,
    availableProducts: Int,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ReceiptLong,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = "Cuenta solicitada",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            Column(
                horizontalAlignment = Alignment.Start,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                PosStatusBadge(
                    label = "$activeAccounts por cobrar",
                    tone = PosVisualTone.Info,
                    icon = Icons.AutoMirrored.Filled.ReceiptLong,
                )
                PosStatusBadge(
                    label = "$availableProducts productos disponibles",
                    tone = PosVisualTone.Neutral,
                    icon = Icons.Default.RestaurantMenu,
                )
            }
        }
    }
}

@Composable
private fun ClientCard(
    clientName: String?,
    onSelectClient: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = CardDefaults.outlinedCardBorder(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Default.Person,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp),
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Cliente de la factura",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = clientName ?: "Sin seleccionar",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            TextButton(onClick = onSelectClient) {
                Text(if (clientName == null) "Seleccionar" else "Cambiar")
            }
        }
    }
}

@Composable
private fun FullAccountAction(
    isSaving: Boolean,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "¿Una sola cuenta?",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Text(
                text = "Incluye automáticamente todo el consumo pendiente de la mesa.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Button(
                onClick = onClick,
                enabled = !isSaving,
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
            ) {
                if (isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text("Crear cuenta completa")
            }
        }
    }
}

@Composable
internal fun SplitProductCard(
    pedido: PedidoMesa,
    disponible: Double,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = CardDefaults.outlinedCardBorder(),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = pedido.itemDescripcion.ifBlank { "Producto ${pedido.productoId}" },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "Disponible ${formatQuantity(disponible)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.width(8.dp))
                AdaptiveAmountText(
                    text = formatMoney(pedido.itemTotalConIva),
                    baseStyle = PosTextStyles.priceTileLarge,
                    color = MaterialTheme.colorScheme.primary,
                    options =
                        com.amaxonia.pos.ui.common.components.AdaptiveAmountOptions(
                            minFontSizeSp = 13f,
                            maxLines = 1,
                        ),
                )
            }
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                label = { Text("Cantidad para esta división") },
                supportingText = { Text("Máximo ${formatQuantity(disponible)}") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
internal fun CuentaActivaCard(
    cuenta: CuentaMesaResponse,
    canPay: Boolean,
    onPay: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = CardDefaults.outlinedCardBorder(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Cuenta #${cuenta.numeroCuenta}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "${cuenta.detalle.size} productos",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                PosStatusBadge(
                    label = "Por cobrar",
                    tone = PosVisualTone.Pending,
                    icon = Icons.AutoMirrored.Filled.ReceiptLong,
                )
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            cuenta.detalle.forEach { line ->
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                    Text(
                        text = "${line.itemDescripcion} × ${formatQuantity(line.cantidad)}",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.width(12.dp))
                    AdaptiveAmountText(
                        text = formatMoney(line.itemTotalConIva),
                        baseStyle = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        options =
                            com.amaxonia.pos.ui.common.components.AdaptiveAmountOptions(
                                fontWeight = FontWeight.Bold,
                                minFontSizeSp = 11f,
                                maxLines = 1,
                                textAlign = androidx.compose.ui.text.style.TextAlign.End,
                            ),
                    )
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = "Total",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                // Total de la cuenta: hero adaptive — montos enormes encogen sin recortarse en 320dp.
                AdaptiveAmountText(
                    text = formatMoney(cuenta.total),
                    baseStyle = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.primary,
                    options =
                        com.amaxonia.pos.ui.common.components.AdaptiveAmountOptions(
                            fontWeight = FontWeight.ExtraBold,
                            minFontSizeSp = 16f,
                            maxLines = 1,
                        ),
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedButton(
                    onClick = onCancel,
                    modifier = Modifier.weight(1f).heightIn(min = 52.dp),
                ) {
                    Text("Cancelar")
                }
                Button(
                    onClick = onPay,
                    enabled = canPay,
                    modifier = Modifier.weight(1.4f).heightIn(min = 52.dp),
                ) {
                    Text("Cobrar cuenta")
                }
            }
        }
    }
}

@Composable
private fun HistoricalAccountRow(cuenta: CuentaMesaResponse) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = MaterialTheme.shapes.large,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Cuenta #${cuenta.numeroCuenta}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )
                cuenta.codFactura?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        text = "Factura $it",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                PosStatusBadge(
                    label = if (cuenta.estado == EstadoCuentaMesa.PAGADA) "Pagada" else "Cancelada",
                    tone =
                        if (cuenta.estado == EstadoCuentaMesa.PAGADA) {
                            PosVisualTone.Success
                        } else {
                            PosVisualTone.Error
                        },
                )
                Text(
                    text = formatMoney(cuenta.total),
                    modifier = Modifier.padding(top = 6.dp),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

private fun formatMoney(value: Double): String = "$ ${String.format(Locale.US, "%.2f", value)}"

private fun formatQuantity(value: Double): String = String.format(Locale.US, "%.3f", value).trimEnd('0').trimEnd('.')
