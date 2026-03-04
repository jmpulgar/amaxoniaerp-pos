package com.amaxonia.pos.ui.products

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Store
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.amaxonia.pos.domain.model.ProductStock
import com.amaxonia.pos.ui.common.DependencyContainer
import com.amaxonia.pos.ui.common.injectedViewModel
import com.amaxonia.pos.ui.theme.AmaxoniaBlue
import com.amaxonia.pos.ui.theme.BgLightGray

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductListScreen(
    viewModel: ProductListViewModel = injectedViewModel {
        ProductListViewModel(
            DependencyContainer.productRepository,
            DependencyContainer.localStore,
            DependencyContainer.apiConfigManager
        )
    },
    onBack: () -> Unit,
    onNavigateToForm: (String?) -> Unit
) {
    val state by viewModel.state.collectAsState()
    val listState = rememberLazyListState()
    var selectedStock by remember { mutableStateOf<ProductStock?>(null) }

    val reachedBottom: Boolean by remember {
        derivedStateOf {
            val lastVisibleItem = listState.layoutInfo.visibleItemsInfo.lastOrNull()
            lastVisibleItem?.index != 0 && lastVisibleItem?.index == listState.layoutInfo.totalItemsCount - 1
        }
    }

    LaunchedEffect(reachedBottom) {
        if (reachedBottom) viewModel.loadMoreProducts()
    }

    Scaffold(
        containerColor = BgLightGray,
        topBar = {
            TopAppBar(
                title = { Text("Inventario", fontWeight = FontWeight.Bold, color = AmaxoniaBlue) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver", tint = AmaxoniaBlue)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BgLightGray)
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { onNavigateToForm(null) },
                containerColor = AmaxoniaBlue,
                contentColor = Color.White
            ) { Icon(Icons.Default.Add, null) }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).padding(horizontal = 16.dp)) {
            if (state.error != null) {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            state.error ?: "Error desconocido",
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        TextButton(onClick = { viewModel.retry() }) {
                            Text("Reintentar")
                        }
                    }
                }
            }
            if (state.isLoading && state.products.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = AmaxoniaBlue)
                }
            } else {
                OutlinedTextField(
                    value = state.searchQuery,
                    onValueChange = { viewModel.onSearchQueryChange(it) },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                    placeholder = { Text("Buscar producto, referencia o código") },
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = Color.White,
                        unfocusedContainerColor = Color.White,
                        focusedBorderColor = AmaxoniaBlue,
                        unfocusedBorderColor = Color.Transparent
                    )
                )
                LazyColumn(
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 80.dp)
                ) {
                    items(state.products, key = { it.id }) { product ->
                        LaunchedEffect(product.id) {
                            viewModel.ensureStockLoaded(product.id)
                        }
                        val imageUrl = viewModel.getProductImageUrl(product.photoUrl)
                        ProductItem(
                            product = product,
                            imageUrl = imageUrl,
                            stock = state.stockByProductId[product.id],
                            isStockLoading = state.loadingStockIds.contains(product.id),
                            onStockClick = {
                                val stock = state.stockByProductId[product.id]
                                if (stock != null) selectedStock = stock
                            },
                            onClick = { onNavigateToForm(product.id) }
                        )
                    }
                    if (state.isLoading) {
                        item {
                            Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(color = AmaxoniaBlue)
                            }
                        }
                    }
                }
            }
        }

        if (selectedStock != null) {
            ProductStockDialog(
                stock = selectedStock!!,
                onDismiss = { selectedStock = null }
            )
        }
    }
}

@Composable
fun ProductItem(
    product: com.amaxonia.pos.domain.model.Product,
    imageUrl: String,
    stock: ProductStock?,
    isStockLoading: Boolean,
    onStockClick: () -> Unit,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(60.dp).clip(RoundedCornerShape(8.dp)).background(Color.White),
                contentAlignment = Alignment.Center
            ) {
                if (imageUrl.isNotBlank()) {
                    AsyncImage(
                        model = imageUrl,
                        contentDescription = product.description,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(Icons.Default.Inventory2, null, tint = Color.Gray)
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(product.description, fontWeight = FontWeight.Bold, color = Color(0xFF2A3256))
                val stockText = when {
                    isStockLoading -> "Cant.: ..."
                    stock != null -> "Cant.: ${formatStock(stock.stockTotalDisponible)}"
                    else -> "Cant.: --"
                }
                Text(
                    "Ref: ${product.reference.ifBlank { product.code }} | $stockText",
                    fontSize = 12.sp,
                    color = Color.Gray,
                    modifier = Modifier.clickable(onClick = onStockClick)
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    "$${String.format("%.2f", product.prices.firstOrNull()?.pricePlusTax ?: 0.0)}",
                    fontWeight = FontWeight.Bold,
                    color = AmaxoniaBlue,
                    fontSize = 16.sp
                )
                Text("Precio A (Inc. Imp)", fontSize = 10.sp, color = Color.Gray)
            }
        }
    }
}

@Composable
private fun ProductStockDialog(
    stock: ProductStock,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cerrar") }
        },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Store, contentDescription = null, tint = AmaxoniaBlue)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Stock por almacén")
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp)
            ) {
                Text(
                    "Disponible total: ${formatStock(stock.stockTotalDisponible)}",
                    fontWeight = FontWeight.Bold,
                    color = AmaxoniaBlue
                )
                Spacer(modifier = Modifier.height(10.dp))
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(stock.almacenes, key = { it.almacenId }) { almacen ->
                        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFF7F9FC))) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(
                                    text = almacen.almacenNombre.ifBlank { "Almacén ${almacen.almacenId}" },
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text("Tipo: ${almacen.almacenTipo.ifBlank { "N/A" }}", fontSize = 12.sp, color = Color.Gray)
                                Text("Cant.: ${formatStock(almacen.cantidad)} | Muestra: ${formatStock(almacen.cantidadMuestra)}", fontSize = 12.sp)
                                Text("Precomp.: ${formatStock(almacen.cantidadPrecomprometida)} | Disp.: ${formatStock(almacen.cantidadDisponible)}", fontSize = 12.sp)
                                Text("Min/Max: ${formatStock(almacen.stockMinimo)} / ${formatStock(almacen.stockMaximo)}", fontSize = 12.sp, color = Color.Gray)
                            }
                        }
                    }
                }
            }
        }
    )
}

private fun formatStock(value: Double): String {
    val rounded = String.format("%.2f", value)
    return rounded.trimEnd('0').trimEnd('.').ifBlank { "0" }
}
