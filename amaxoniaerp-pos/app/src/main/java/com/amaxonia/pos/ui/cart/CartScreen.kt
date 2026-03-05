package com.amaxonia.pos.ui.cart

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import com.amaxonia.pos.ui.common.DependencyContainer
import com.amaxonia.pos.ui.common.SellerSelectorBottomSheet
import com.amaxonia.pos.ui.common.injectedViewModel
import com.amaxonia.pos.ui.theme.AmaxoniaBlue

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CartScreen(
    viewModel: CartViewModel = injectedViewModel {
        CartViewModel(
            DependencyContainer.cartRepository,
            DependencyContainer.clientRepository,
            DependencyContainer.localStore,
            DependencyContainer.apiConfigManager
        )
    },
    onBack: () -> Unit,
    onCheckout: (Double) -> Unit,
    onSelectClient: () -> Unit // Nuevo callback para ir a buscar cliente si no hay
) {
    val state by viewModel.state.collectAsState()
    var showSellerSheet by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }

    // Manejo del mensaje de éxito (Pedido creado)
    if (state.orderSuccessMessage != null) {
        AlertDialog(
            onDismissRequest = {
                viewModel.clearMessage()
                onBack() // Volver al dashboard
            },
            title = { Text("Pedido Creado") },
            text = { Text(state.orderSuccessMessage!!) },
            confirmButton = {
                Button(onClick = {
                    viewModel.clearMessage()
                    onBack()
                }) { Text("Aceptar") }
            }
        )
    }

    if (showSellerSheet) {
        SellerSelectorBottomSheet(
            sellers = state.availableSellers,
            selectedSellerId = state.currentSeller?.id,
            onSelect = { seller -> viewModel.selectSeller(seller.id) },
            onDismiss = { showSellerSheet = false }
        )
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        "Carrito",
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Atrás",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (state.items.isNotEmpty()) {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shadowElevation = 16.dp,
                    shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
                ) {
                    Column(modifier = Modifier.padding(24.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Total", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                            Text("$${String.format("%.2f", state.total)}", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = AmaxoniaBlue)
                        }
                        Spacer(modifier = Modifier.height(24.dp))

                        // Botones de Acción
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            // 1. Botón GUARDAR PEDIDO (Solo activo si hay cliente)
                            Button(
                                onClick = { viewModel.createOrder() },
                                enabled = state.selectedClient != null,
                                modifier = Modifier.weight(1f).height(50.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.Save, null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Guardar", fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }

                            // 2. Botón PAGAR / FACTURAR
                            Button(
                                onClick = { onCheckout(state.total) },
                                modifier = Modifier.weight(1f).height(50.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = AmaxoniaBlue),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Cobrar")
                            }
                        }
                    }
                }
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).padding(16.dp)) {

            // SECCIÓN CLIENTE
            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth().clickable { onSelectClient() }
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (state.selectedClient != null) {
                        val clientPhotoUrl = viewModel.getClientPhotoUrl(state.selectedClient!!)
                        ClientAvatar(
                            clientPhotoUrl = clientPhotoUrl,
                            clientName = "${state.selectedClient!!.firstName} ${state.selectedClient!!.lastName}"
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                    } else {
                        Icon(Icons.Default.Person, null, tint = AmaxoniaBlue)
                        Spacer(modifier = Modifier.width(16.dp))
                    }
                    if (state.selectedClient != null) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Cliente asignado:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("${state.selectedClient!!.firstName} ${state.selectedClient!!.lastName}", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }
                        IconButton(onClick = { viewModel.removeClient() }) {
                            Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error)
                        }
                    } else {
                        Text("Asignar Cliente", modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Icon(Icons.Default.Add, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = state.availableSellers.isNotEmpty()) { showSellerSheet = true }
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Person, null, tint = AmaxoniaBlue)
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Vendedor asignado:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            text = state.currentSeller?.nombre ?: "Sin vendedor",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Icon(
                        Icons.Default.Autorenew,
                        contentDescription = "Cambiar vendedor",
                        tint = if (state.availableSellers.isEmpty()) MaterialTheme.colorScheme.outline else AmaxoniaBlue
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (state.items.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("El carrito está vacío", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(state.items) { item ->
                        CartItemRow(
                            item = item,
                            onIncrease = { viewModel.increaseQuantity(item.product.id) },
                            onDecrease = { viewModel.decreaseQuantity(item.product.id) },
                            onRemove = { viewModel.removeItem(item.product.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ClientAvatar(
    clientPhotoUrl: String,
    clientName: String,
    modifier: Modifier = Modifier
) {
    val initials = buildInitials(clientName)
    val gradient = listOf(Color(0xFF1E88E5), Color(0xFF00ACC1))

    Box(
        modifier = modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(Brush.linearGradient(gradient)),
        contentAlignment = Alignment.Center
    ) {
        if (clientPhotoUrl.isBlank()) {
            Text(initials, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        } else {
            SubcomposeAsyncImage(
                model = clientPhotoUrl,
                contentDescription = "Foto cliente",
                modifier = Modifier.fillMaxSize(),
                loading = { Text(initials, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp) },
                error = { Text(initials, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp) },
                success = { SubcomposeAsyncImageContent(modifier = Modifier.fillMaxSize()) }
            )
        }
    }
}

private fun buildInitials(name: String): String {
    val parts = name.trim().split(" ").filter { it.isNotBlank() }
    if (parts.isEmpty()) return "CL"
    return parts.take(2).joinToString(separator = "") { it.first().uppercase() }
}

@Composable
fun CartItemRow(
    item: com.amaxonia.pos.domain.model.CartItem,
    onIncrease: () -> Unit,
    onDecrease: () -> Unit,
    onRemove: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(item.product.description, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("$ ${item.product.prices.firstOrNull()?.pricePlusTax ?: 0.0} / ud", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onDecrease, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Remove, null, tint = MaterialTheme.colorScheme.primary)
                }
                Text(
                    text = "${item.quantity}",
                    modifier = Modifier.padding(horizontal = 12.dp),
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
                IconButton(onClick = onIncrease, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Add, null, tint = MaterialTheme.colorScheme.primary)
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            Text(
                "$ ${String.format("%.2f", item.total)}",
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            IconButton(onClick = onRemove) {
                Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f))
            }
        }
    }
}
