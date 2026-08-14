@file:Suppress(
    "CyclomaticComplexMethod",
    "LongMethod",
    "LongParameterList",
    "UnusedParameter",
)

package com.amaxonia.pos.ui.mesas

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.LocalDining
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material.icons.filled.RoomService
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.amaxonia.pos.domain.model.ItemCarrito
import com.amaxonia.pos.domain.model.mesas.EstadoPedidoMesa
import com.amaxonia.pos.domain.model.mesas.PedidoMesa
import com.amaxonia.pos.ui.cart.CartViewModel
import com.amaxonia.pos.ui.common.components.AdaptiveAmountText
import com.amaxonia.pos.ui.common.components.PosFeedbackCard
import com.amaxonia.pos.ui.common.components.PosLoadingState
import com.amaxonia.pos.ui.common.components.PosSectionHeader
import com.amaxonia.pos.ui.common.components.PosStatusBadge
import com.amaxonia.pos.ui.common.components.PosVisualAction
import com.amaxonia.pos.ui.common.components.PosVisualTone

/**
 * Pantalla de comanda de una sesión de mesa. Muestra dos secciones:
 *
 * 1. Pendientes de enviar: items PENDIENTE (los del carrito compartido se ven en la misma
 *    sección porque aún no se han persistido) + los PENDIENTE ya guardados en el backend.
 * 2. Enviados a cocina: líneas en estado ENVIADA, EN_PREPARACION, LISTA o ENTREGADA.
 *
 * Estados:
 * - Cada línea enviada muestra un badge con su estado y dos botones para avanzarlo al
 *   siguiente estado; los finales (ENTREGADA, CANCELADA) solo se muestran con tachado.
 * - Los PENDIENTE se envían en bloque con el botón "Enviar comanda".
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComandaScreen(
    mesaNombre: String,
    sesionId: Int,
    viewModel: ComandaViewModel,
    cartViewModel: CartViewModel,
    onBack: () -> Unit,
    onCuenta: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val cartState by cartViewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.cargar(skipSpinner = false)
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "Comanda",
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
                    IconButton(onClick = {
                        viewModel.onSalir()
                        onBack()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
                actions = {
                    TextButton(onClick = onCuenta) {
                        Icon(
                            Icons.AutoMirrored.Filled.ReceiptLong,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.size(6.dp))
                        Text("Cuenta")
                    }
                },
                colors =
                    TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                    ),
            )
        },
    ) { padding ->
        ComandaBody(
            state = state,
            cartLinesPendientes = cartState.displayItems,
            onEnviarComanda = viewModel::enviarComanda,
            onCambiarEstado = viewModel::cambiarEstado,
            onReintentar = { viewModel.cargar(skipSpinner = false) },
            contentPadding = padding,
        )
    }
}

@Composable
private fun ComandaBody(
    state: ComandaState,
    cartLinesPendientes: List<ItemCarrito>,
    onEnviarComanda: () -> Unit,
    onCambiarEstado: (Int, String) -> Unit,
    onReintentar: () -> Unit,
    contentPadding: PaddingValues,
) {
    LazyColumn(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(contentPadding),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            ComandaStatusSummary(
                pendientes = cartLinesPendientes.size + state.pendientes.size,
                enviados = state.enviados,
            )
        }
        val mensaje = state.error ?: state.info
        if (mensaje != null) {
            item {
                PosFeedbackCard(
                    title = if (state.error != null) "No pudimos actualizar la comanda" else "Comanda actualizada",
                    message = mensaje,
                    tone = if (state.error != null) PosVisualTone.Error else PosVisualTone.Success,
                    action =
                        if (state.error != null) {
                            PosVisualAction(label = "Reintentar", onClick = onReintentar)
                        } else {
                            null
                        },
                )
            }
        }
        // Section pendientes: items nuevos del carrito + pedidos PENDIENTE del backend.
        item {
            PosSectionHeader(
                title = "Pendientes de enviar",
                subtitle = "${cartLinesPendientes.size + state.pendientes.size} productos aún editables",
                icon = Icons.Filled.Receipt,
            )
        }
        if (state.isLoading && state.pendientes.isEmpty() && cartLinesPendientes.isEmpty()) {
            item { PosLoadingState("Cargando productos de la comanda…") }
        }
        if (cartLinesPendientes.isEmpty() && state.pendientes.isEmpty()) {
            item {
                EmptyHint(
                    title = "Todo está enviado",
                    text = "Los nuevos productos que agregues aparecerán aquí antes de enviarlos.",
                )
            }
        }
        items(cartLinesPendientes, key = { "cart-${it.id}" }) { display ->
            LineRowCarrito(display)
        }
        items(state.pendientes, key = { "pend-${it.id}" }) { pedido ->
            LineRowPedido(pedido, onCambiarEstado = null)
        }
        if (cartLinesPendientes.isNotEmpty() || state.pendientes.isNotEmpty()) {
            item {
                Button(
                    onClick = onEnviarComanda,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                    enabled = !state.isSending,
                ) {
                    if (state.isSending) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                        Spacer(Modifier.size(8.dp))
                    } else {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null)
                        Spacer(Modifier.size(8.dp))
                    }
                    Text(if (state.isSending) "Enviando a cocina…" else "Enviar comanda a cocina")
                }
            }
        }

        // Section enviados: solo líneas que ya salieron de cocina (o del pos) salvo canceladas.
        item {
            PosSectionHeader(
                title = "Seguimiento",
                subtitle = "${state.enviados.size} productos enviados",
                icon = Icons.Filled.LocalDining,
            )
        }
        if (state.enviados.isEmpty()) {
            item {
                EmptyHint(
                    title = "Sin productos en cocina",
                    text = "Envía la comanda para comenzar el seguimiento de preparación.",
                )
            }
        } else {
            items(state.enviados, key = { "env-${it.id}" }) { pedido ->
                LineRowPedido(pedido, onCambiarEstado = onCambiarEstado)
            }
        }
    }
}

@Composable
private fun ComandaStatusSummary(
    pendientes: Int,
    enviados: List<PedidoMesa>,
) {
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(vertical = 2.dp),
    ) {
        item {
            PedidoStatusBadge(EstadoPedidoMesa.PENDIENTE, count = pendientes)
        }
        listOf(
            EstadoPedidoMesa.ENVIADA,
            EstadoPedidoMesa.EN_PREPARACION,
            EstadoPedidoMesa.LISTA,
            EstadoPedidoMesa.ENTREGADA,
        ).forEach { estado ->
            item {
                PedidoStatusBadge(
                    estado = estado,
                    count = enviados.count { it.estado == estado },
                )
            }
        }
    }
}

@Composable
private fun LineRowCarrito(display: ItemCarrito) {
    val descripcion =
        when (display) {
            is ItemCarrito.ProductoIndividual ->
                display.item.product.description
                    .ifBlank { "Producto ${display.item.product.id}" }
            is ItemCarrito.PromocionAgrupada -> display.promocionNombre.ifBlank { "Promoción ${display.promocionCodigo}" }
        }
    val cantidad =
        when (display) {
            is ItemCarrito.ProductoIndividual -> display.item.quantityDecimal
            is ItemCarrito.PromocionAgrupada -> display.items.sumOf { it.quantityDecimal }
        }
    val total = display.total
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = CardDefaults.outlinedCardBorder(),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.Top) {
                Text(
                    descripcion,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                BadgeEstado(EstadoPedidoMesa.PENDIENTE)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                PosStatusBadge(
                    label = "Cantidad $cantidad",
                    tone = PosVisualTone.Neutral,
                    icon = Icons.Default.Restaurant,
                )
                Spacer(Modifier.weight(1f))
                AdaptiveAmountText(
                    text = "$ ${"%.2f".format(total)}",
                    baseStyle = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    minFontSizeSp = 12f,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
internal fun LineRowPedido(
    pedido: PedidoMesa,
    onCambiarEstado: ((Int, String) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val finalizado = EstadoPedidoMesa.FINALES.contains(pedido.estado)
    val cancelado = pedido.estado == EstadoPedidoMesa.CANCELADA
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors =
            CardDefaults.cardColors(
                containerColor =
                    if (finalizado) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface,
            ),
        border = CardDefaults.outlinedCardBorder(),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.Top) {
                Text(
                    pedido.itemDescripcion.ifBlank { "Producto ${pedido.productoId}" },
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textDecoration = if (cancelado) TextDecoration.LineThrough else TextDecoration.None,
                )
                BadgeEstado(pedido.estado)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                PosStatusBadge(
                    label = "Cantidad ${pedido.itemCantidad}",
                    tone = PosVisualTone.Neutral,
                    icon = Icons.Default.Restaurant,
                )
                Spacer(Modifier.weight(1f))
                Column(horizontalAlignment = Alignment.End) {
                    AdaptiveAmountText(
                        text = "$ ${"%.2f".format(pedido.itemTotalConIva)}",
                        baseStyle = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        minFontSizeSp = 12f,
                        maxLines = 1,
                    )
                    pedido.comandaSecuencia?.let {
                        Text(
                            "Comanda #$it",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            if (onCambiarEstado != null && !finalizado) {
                val siguiente = siguienteEstadoDe(pedido.estado)
                OutlinedButton(
                    onClick = { onCambiarEstado(pedido.id, siguiente) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.size(8.dp))
                    Text(actionLabelFor(siguiente))
                }
            }
        }
    }
}

@Composable
private fun BadgeEstado(estado: String) {
    PedidoStatusBadge(estado = estado)
}

@Composable
private fun PedidoStatusBadge(
    estado: String,
    count: Int? = null,
) {
    val visual =
        when (estado) {
            EstadoPedidoMesa.PENDIENTE ->
                Triple("Pendiente", PosVisualTone.Info, Icons.Default.Schedule)
            EstadoPedidoMesa.ENVIADA ->
                Triple("Enviada", PosVisualTone.Pending, Icons.AutoMirrored.Filled.Send)
            EstadoPedidoMesa.EN_PREPARACION ->
                Triple("En preparación", PosVisualTone.Warning, Icons.Default.HourglassTop)
            EstadoPedidoMesa.LISTA ->
                Triple("Lista", PosVisualTone.Success, Icons.Default.CheckCircle)
            EstadoPedidoMesa.ENTREGADA ->
                Triple("Entregada", PosVisualTone.Neutral, Icons.Default.RoomService)
            EstadoPedidoMesa.CANCELADA ->
                Triple("Cancelada", PosVisualTone.Error, Icons.Default.Cancel)
            else ->
                Triple(
                    estado.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() },
                    PosVisualTone.Neutral,
                    Icons.Default.Schedule,
                )
        }
    PosStatusBadge(
        label = if (count == null) visual.first else "${visual.first} · $count",
        tone = visual.second,
        icon = visual.third,
    )
}

@Composable
private fun EmptyHint(
    title: String,
    text: String,
) {
    PosFeedbackCard(
        title = title,
        message = text,
        tone = PosVisualTone.Neutral,
    )
}

/**
 * Siguiente estado válido para el flujo de comanda. Backend re-valida; esto solo sirve para
 * mostrar el label correcto en el botón de avance.
 */
private fun siguienteEstadoDe(estado: String): String =
    when (estado) {
        EstadoPedidoMesa.PENDIENTE -> EstadoPedidoMesa.ENVIADA
        EstadoPedidoMesa.ENVIADA -> EstadoPedidoMesa.EN_PREPARACION
        EstadoPedidoMesa.EN_PREPARACION -> EstadoPedidoMesa.LISTA
        EstadoPedidoMesa.LISTA -> EstadoPedidoMesa.ENTREGADA
        else -> EstadoPedidoMesa.ENTREGADA
    }

private fun actionLabelFor(estado: String): String =
    when (estado) {
        EstadoPedidoMesa.ENVIADA -> "Marcar como enviada"
        EstadoPedidoMesa.EN_PREPARACION -> "Marcar en preparación"
        EstadoPedidoMesa.LISTA -> "Marcar como lista"
        EstadoPedidoMesa.ENTREGADA -> "Marcar como entregada"
        else -> "Actualizar estado"
    }
