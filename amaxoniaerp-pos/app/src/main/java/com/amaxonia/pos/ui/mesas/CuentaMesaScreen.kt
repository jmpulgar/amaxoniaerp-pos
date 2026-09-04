@file:Suppress("LongMethod", "LongParameterList", "MagicNumber")

package com.amaxonia.pos.ui.mesas

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.RestaurantMenu
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
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
import androidx.compose.material3.Surface
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
import com.amaxonia.pos.domain.model.mesas.PedidoMesa
import com.amaxonia.pos.ui.common.components.AdaptiveAmountOptions
import com.amaxonia.pos.ui.common.components.AdaptiveAmountText
import com.amaxonia.pos.ui.common.components.PosEmptyState
import com.amaxonia.pos.ui.common.components.PosFeedbackCard
import com.amaxonia.pos.ui.common.components.PosLoadingState
import com.amaxonia.pos.ui.common.components.PosStatusBadge
import com.amaxonia.pos.ui.common.components.PosVisualAction
import com.amaxonia.pos.ui.common.components.PosVisualTone
import com.amaxonia.pos.ui.theme.PosTextStyles

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
                            text = mesaNombre,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = "Cuenta y división",
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
                actions = {
                    if (state.cuentasActivas.isNotEmpty()) {
                        IconButton(onClick = { viewModel.setShowCuentasActivasSheet(true) }) {
                            BadgedBox(
                                badge = {
                                    Badge(
                                        containerColor = MaterialTheme.colorScheme.primary,
                                        contentColor = MaterialTheme.colorScheme.onPrimary,
                                    ) {
                                        Text("${state.cuentasActivas.size}")
                                    }
                                },
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ReceiptLong,
                                    contentDescription = "Cuentas activas",
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    }
                    if (state.historicas.isNotEmpty()) {
                        IconButton(onClick = { viewModel.setShowHistoricoSheet(true) }) {
                            Icon(
                                imageVector = Icons.Default.History,
                                contentDescription = "Historial",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
            )
        },
        bottomBar = {
            CuentaStickyBottomBar(
                state = state,
                onCrearCuentaCompleta = viewModel::crearYCobrarCuentaCompleta,
                onCrearDivision = viewModel::crearDivision,
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentAlignment = Alignment.TopCenter,
        ) {
            val disponibles = state.pedidosDisponibles

            LazyColumn(
                modifier = Modifier.fillMaxHeight().widthIn(max = 840.dp).fillMaxWidth(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                // Selector de modo superior: Cuenta Completa vs Dividir
                item {
                    CuentaModoSelector(
                        modoSeleccionado = state.modoSeleccionado,
                        totalCompleto = state.totalConsumoPendiente,
                        totalDisponibleProductos = disponibles.size,
                        cuentasActivasCount = state.cuentasActivas.size,
                        onModoChange = viewModel::setModo,
                    )
                }

                // Cliente asignado
                item {
                    CuentaClienteBar(
                        clientName = clientName,
                        onSelectClient = onSelectClient,
                    )
                }

                // Banner destacado si existen cuentas activas por cobrar
                if (state.cuentasActivas.isNotEmpty()) {
                    item {
                        CuentaActivasBanner(
                            cuentasActivasCount = state.cuentasActivas.size,
                            onClickVerCuentas = { viewModel.setShowCuentasActivasSheet(true) },
                        )
                    }
                }

                // Alerta de productos no entregados en cocina
                if (state.pedidosNoEntregados.isNotEmpty()) {
                    item {
                        PosFeedbackCard(
                            title = "Productos pendientes en cocina",
                            message =
                                "Hay ${state.pedidosNoEntregados.size} producto(s) en cocina que aún no han sido entregados a la mesa.",
                            tone = PosVisualTone.Warning,
                            action =
                                PosVisualAction(
                                    label = if (state.isDeliveringAll) "Entregando…" else "Marcar todos como entregados",
                                    onClick = viewModel::marcarTodosEntregados,
                                ),
                        )
                    }
                }

                // Mensajes de error o éxito
                state.error?.let { message ->
                    item {
                        PosFeedbackCard(
                            title = "No pudimos completar la acción",
                            message = message,
                            tone = PosVisualTone.Error,
                        )
                    }
                }

                state.info?.let { message ->
                    item {
                        PosFeedbackCard(
                            title = "Operación exitosa",
                            message = message,
                            tone = PosVisualTone.Success,
                        )
                    }
                }

                // Contenido dinámico según el modo seleccionado
                if (state.isLoading && state.pedidos.isEmpty() && state.cuentas.isEmpty()) {
                    item { PosLoadingState("Cargando consumos y cuentas…") }
                } else if (state.modoSeleccionado == CuentaModo.COMPLETA) {
                    // Modo Cuenta Completa: Resumen claro y limpio
                    item {
                        CuentaConsumoResumen(pedidos = disponibles)
                    }
                } else {
                    // Modo Dividir Cuenta: Selección interactiva por producto con steppers
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "Elige productos para esta división",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                            )

                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                TextButton(
                                    onClick = viewModel::seleccionarTodoParaDividir,
                                    enabled = disponibles.isNotEmpty(),
                                ) {
                                    Icon(Icons.Default.SelectAll, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Todos", style = MaterialTheme.typography.labelMedium)
                                }
                                TextButton(
                                    onClick = viewModel::deseleccionarTodoParaDividir,
                                    enabled = state.itemsDivisionSeleccionadosCount > 0,
                                ) {
                                    Icon(Icons.Default.RestartAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Limpiar", style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        }
                    }

                    if (disponibles.isEmpty()) {
                        item {
                            PosEmptyState(
                                icon = Icons.Default.RestaurantMenu,
                                title = "Sin productos disponibles para dividir",
                                message = "Los consumos pendientes aparecerán aquí una vez entregados.",
                            )
                        }
                    } else {
                        items(disponibles, key = { "pedido-${it.id}" }) { pedido ->
                            SplitProductInteractiveCard(
                                pedido = pedido,
                                disponible = state.disponible(pedido),
                                selectedQuantity = state.cantidadSeleccionada(pedido.id),
                                onToggle = { viewModel.toggleSeleccion(pedido.id) },
                                onIncrease = { viewModel.incrementarCantidad(pedido.id) },
                                onDecrease = { viewModel.decrementarCantidad(pedido.id) },
                            )
                        }
                    }
                }

                if (state.isSaving) {
                    item { PosLoadingState("Guardando cambios en la cuenta…") }
                }
            }
        }
    }

    // Modal Bottom Sheets
    if (state.showCuentasActivasSheet) {
        CuentaActivasBottomSheet(
            cuentasActivas = state.cuentasActivas,
            canPay = clientName != null && !state.isSaving,
            onPay = { cuenta ->
                viewModel.setShowCuentasActivasSheet(false)
                viewModel.pagar(cuenta)
            },
            onCancel = { cuenta ->
                viewModel.cancelar(cuenta)
            },
            onSelectClient = {
                viewModel.setShowCuentasActivasSheet(false)
                onSelectClient()
            },
            onDismiss = { viewModel.setShowCuentasActivasSheet(false) },
        )
    }

    if (state.showHistoricoSheet) {
        CuentaHistoricoBottomSheet(
            historicas = state.historicas,
            onDismiss = { viewModel.setShowHistoricoSheet(false) },
        )
    }
}

/** Barra de acción inferior fija con cálculos en tiempo real */
@Composable
private fun CuentaStickyBottomBar(
    state: CuentaMesaState,
    onCrearCuentaCompleta: () -> Unit,
    onCrearDivision: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 8.dp,
        tonalElevation = 3.dp,
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Box(modifier = Modifier.widthIn(max = 840.dp).fillMaxWidth()) {
                if (state.modoSeleccionado == CuentaModo.COMPLETA) {
                    val tieneConsumoPendiente = state.pedidosDisponibles.isNotEmpty()
                    val tieneCuentasActivas = state.cuentasActivas.isNotEmpty()

                    if (tieneConsumoPendiente || tieneCuentasActivas) {
                        val total =
                            if (tieneConsumoPendiente) {
                                state.totalConsumoPendiente
                            } else {
                                state.cuentasActivas.firstOrNull()?.total ?: 0.0
                            }
                        Button(
                            onClick = onCrearCuentaCompleta,
                            enabled = !state.isSaving && !state.isLoading,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                            shape = MaterialTheme.shapes.large,
                        ) {
                            if (state.isSaving) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                )
                                Spacer(Modifier.width(8.dp))
                            }
                            Text(
                                text = "Cobrar Cuenta Completa · ${formatMoney(total)}",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                } else {
                    // Modo Dividir Cuenta
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        AnimatedVisibility(
                            visible = state.itemsDivisionSeleccionadosCount > 0,
                            enter = expandVertically() + fadeIn(),
                            exit = shrinkVertically() + fadeOut(),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = "${state.itemsDivisionSeleccionadosCount} producto(s) seleccionados",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                AdaptiveAmountText(
                                    text = formatMoney(state.totalDivisionSeleccionada),
                                    baseStyle = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    options =
                                        AdaptiveAmountOptions(
                                            fontWeight = FontWeight.ExtraBold,
                                            minFontSizeSp = 14f,
                                            maxLines = 1,
                                        ),
                                )
                            }
                        }

                        Button(
                            onClick = onCrearDivision,
                            enabled = state.itemsDivisionSeleccionadosCount > 0 && !state.isSaving && !state.isLoading,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                            shape = MaterialTheme.shapes.large,
                        ) {
                            if (state.isSaving) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                )
                                Spacer(Modifier.width(8.dp))
                            }
                            Text(
                                text =
                                    if (state.itemsDivisionSeleccionadosCount > 0) {
                                        "Crear División · ${formatMoney(state.totalDivisionSeleccionada)}"
                                    } else {
                                        "Selecciona productos para dividir"
                                    },
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Tarjeta individual de cuenta activa con soporte para cobro y cancelación */
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
                            AdaptiveAmountOptions(
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
                AdaptiveAmountText(
                    text = formatMoney(cuenta.total),
                    baseStyle = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.primary,
                    options =
                        AdaptiveAmountOptions(
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
                    modifier = Modifier.weight(1f).heightIn(min = 50.dp),
                    shape = MaterialTheme.shapes.medium,
                ) {
                    Text("Cancelar")
                }
                Button(
                    onClick = onPay,
                    enabled = canPay,
                    modifier = Modifier.weight(1.4f).heightIn(min = 50.dp),
                    shape = MaterialTheme.shapes.medium,
                ) {
                    Text("Cobrar cuenta")
                }
            }
        }
    }
}

/** Tarjeta compatible con versiones previas y previews */
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
                        AdaptiveAmountOptions(
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
