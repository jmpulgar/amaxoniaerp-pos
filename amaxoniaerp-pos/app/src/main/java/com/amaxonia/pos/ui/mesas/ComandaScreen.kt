package com.amaxonia.pos.ui.mesas

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.LocalDining
import androidx.compose.material.icons.filled.Receipt
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.amaxonia.pos.domain.model.ItemCarrito
import com.amaxonia.pos.domain.model.mesas.EstadoPedidoMesa
import com.amaxonia.pos.domain.model.mesas.PedidoMesa
import com.amaxonia.pos.ui.cart.CartViewModel

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
                title = { Text("Comanda · $mesaNombre") },
                navigationIcon = {
                    IconButton(onClick = {
                        viewModel.onSalir()
                        onBack()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
                actions = {
                    IconButton(onClick = onCuenta) {
                        Icon(Icons.Filled.Receipt, contentDescription = "Cuenta")
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
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(contentPadding),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Section pendientes: items nuevos del carrito + pedidos PENDIENTE del backend.
            item {
                SectionHeader(
                    title = "Pendientes de enviar",
                    subtitle = "${cartLinesPendientes.size + state.pendientes.size} líneas",
                    icon = Icons.Filled.Receipt,
                )
            }
            if (state.isLoading && state.pendientes.isEmpty() && cartLinesPendientes.isEmpty()) {
                item { LoadingRow("Cargando pedidos…") }
            }
            if (cartLinesPendientes.isEmpty() && state.pendientes.isEmpty()) {
                item { EmptyHint("No hay líneas pendientes. Agrega productos para visualizarlos aquí.") }
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
                        modifier = Modifier.fillMaxWidth(),
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
                        Text(if (state.isSending) "Enviando…" else "Enviar comanda")
                    }
                }
            }

            // Section enviados: solo líneas que ya salieron de cocina (o del pos) salvo canceladas.
            item {
                SectionHeader(
                    title = "Enviados a cocina",
                    subtitle = "${state.enviados.size} líneas",
                    icon = Icons.Filled.LocalDining,
                )
            }
            if (state.enviados.isEmpty()) {
                item { EmptyHint("Aún no se ha enviado ninguna comanda.") }
            } else {
                items(state.enviados, key = { "env-${it.id}" }) { pedido ->
                    LineRowPedido(pedido, onCambiarEstado = onCambiarEstado)
                }
            }
        }

        // Banner de error/info fijo al pie sin tapar la lista.
        val mensaje = state.error ?: state.info
        if (mensaje != null) {
            Surface(
                color =
                    if (state.error != null) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer,
                shape = RoundedCornerShape(8.dp),
                modifier =
                    Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp)
                        .fillMaxWidth(),
            ) {
                Text(
                    text = mensaje,
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color =
                        if (state.error != null) {
                            MaterialTheme.colorScheme.onErrorContainer
                        } else {
                            MaterialTheme.colorScheme.onSecondaryContainer
                        },
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.fillMaxWidth())
        Text(subtitle, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    descripcion,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "Cant. $cantidad · $${"%.2f".format(total)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            BadgeEstado(EstadoPedidoMesa.PENDIENTE)
        }
    }
}

@Composable
private fun LineRowPedido(
    pedido: PedidoMesa,
    onCambiarEstado: ((Int, String) -> Unit)?,
) {
    val finalizado = EstadoPedidoMesa.FINALES.contains(pedido.estado)
    val cancelado = pedido.estado == EstadoPedidoMesa.CANCELADA
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors =
            CardDefaults.cardColors(
                containerColor =
                    if (finalizado) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface,
            ),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    pedido.itemDescripcion.ifBlank { "Producto ${pedido.productoId}" },
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textDecoration = if (cancelado) TextDecoration.LineThrough else TextDecoration.None,
                )
                Text(
                    "Cant. ${pedido.itemCantidad} · $${"%.2f".format(pedido.itemTotalConIva)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                pedido.comandaSecuencia?.let {
                    Text(
                        "Comanda #$it",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                }
            }
            BadgeEstado(pedido.estado)
        }
        if (onCambiarEstado != null && !finalizado) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                OutlinedButton(
                    onClick = { onCambiarEstado(pedido.id, siguienteEstadoDe(pedido.estado)) },
                ) {
                    Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.size(4.dp))
                    Text("A ${siguienteEstadoDe(pedido.estado)}")
                }
            }
        }
    }
}

@Composable
private fun BadgeEstado(estado: String) {
    val color =
        when (estado) {
            EstadoPedidoMesa.PENDIENTE -> Color(0xFFE0E7FF)
            EstadoPedidoMesa.ENVIADA -> Color(0xFFFEF3C7)
            EstadoPedidoMesa.EN_PREPARACION -> Color(0xFFFFEDD5)
            EstadoPedidoMesa.LISTA -> Color(0xFFDCFCE7)
            EstadoPedidoMesa.ENTREGADA -> Color(0xFFE5E7EB)
            EstadoPedidoMesa.CANCELADA -> Color(0xFFFEE2E2)
            else -> Color(0xFFE5E7EB)
        }
    Surface(color = color, shape = RoundedCornerShape(6.dp)) {
        Text(
            text = estado.replace('_', ' '),
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            color = Color(0xFF1F2937),
        )
    }
}

@Composable
private fun EmptyHint(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 6.dp),
    )
}

@Composable
private fun LoadingRow(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
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
