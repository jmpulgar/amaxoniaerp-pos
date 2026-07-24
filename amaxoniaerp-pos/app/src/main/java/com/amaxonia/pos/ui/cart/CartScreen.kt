package com.amaxonia.pos.ui.cart

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material.icons.filled.Percent
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import com.amaxonia.pos.domain.model.CartItem
import com.amaxonia.pos.domain.model.ItemCarrito
import com.amaxonia.pos.domain.usecase.BigDecimalMoneyFormatter
import com.amaxonia.pos.domain.usecase.cart.ResolveClientImageUrlUseCase
import com.amaxonia.pos.ui.common.DependencyContainer
import com.amaxonia.pos.ui.common.SellerSelectorBottomSheet
import com.amaxonia.pos.ui.common.components.CartEmptyState
import com.amaxonia.pos.ui.common.components.QuantityStepper
import com.amaxonia.pos.ui.common.injectedViewModel
import com.amaxonia.pos.ui.payment.formatCurrencyLabel
import com.amaxonia.pos.ui.theme.PosExtraShapes
import com.amaxonia.pos.ui.theme.PosPalette
import com.amaxonia.pos.ui.theme.PosTextStyles
import com.amaxonia.pos.ui.theme.cartBrandGradient

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CartScreen(
    viewModel: CartViewModel =
        injectedViewModel {
            CartViewModel(
                stateCoordinator =
                    CartStateCoordinator(
                        DependencyContainer.cartRepository,
                        DependencyContainer.clientRepository,
                        DependencyContainer.posConfigurationRepository,
                        DependencyContainer.clientBranchRepository,
                        ResolveClientImageUrlUseCase(
                            DependencyContainer.posConfigurationRepository,
                            DependencyContainer.imageUrlResolver,
                        ),
                    ),
                configurationCoordinator =
                    CartConfigurationCoordinator(
                        DependencyContainer.posConfigurationRepository,
                        DependencyContainer.cajaRepository,
                    ),
                actionHandler =
                    CartActionHandler(
                        DependencyContainer.cartRepository,
                        DependencyContainer.refreshCartProductLotsUseCase,
                        DependencyContainer.saveDraftInvoiceUseCase,
                    ),
            )
        },
    onBack: () -> Unit,
    onCheckout: (Double) -> Unit,
    onSelectClient: () -> Unit, // Nuevo callback para ir a buscar cliente si no hay
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val currentOnCheckout by rememberUpdatedState(onCheckout)
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is CartUiEffect.Checkout -> currentOnCheckout(effect.total)
            }
        }
    }
    var showSellerSheet by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    var itemToEditPrice by androidx.compose.runtime.remember {
        androidx.compose.runtime.mutableStateOf<com.amaxonia.pos.domain.model.CartItem?>(null)
    }
    var itemToEditDiscount by androidx.compose.runtime.remember {
        androidx.compose.runtime.mutableStateOf<com.amaxonia.pos.domain.model.CartItem?>(null)
    }
    var priceInput by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf("") }
    var discountInput by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf("") }

    // Mensaje de exito (borrador guardado)
    state.orderSuccessMessage?.let { successMessage ->
        AlertDialog(
            onDismissRequest = {
                viewModel.onAction(CartUiAction.ClearMessage)
            },
            title = { Text("Borrador Guardado") },
            text = { Text(successMessage) },
            confirmButton = {
                Button(onClick = {
                    viewModel.onAction(CartUiAction.ClearMessage)
                    onBack()
                }) { Text("Aceptar") }
            },
        )
    }

    val editingPriceItem = itemToEditPrice
    if (editingPriceItem != null) {
        AlertDialog(
            onDismissRequest = { itemToEditPrice = null },
            confirmButton = {
                Button(onClick = {
                    val parsed = priceInput.toDoubleOrNull()
                    if (parsed != null) {
                        viewModel.onAction(CartUiAction.UpdateItemPrice(editingPriceItem.product.id, parsed))
                        itemToEditPrice = null
                    }
                }) { Text("Guardar") }
            },
            dismissButton = {
                TextButton(onClick = { itemToEditPrice = null }) { Text("Cancelar") }
            },
            title = { Text("Editar precio unitario") },
            text = {
                Column {
                    Text(editingPriceItem.product.description, fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = priceInput,
                        onValueChange = { priceInput = it },
                        label = { Text("Precio unitario con IVA") },
                        singleLine = true,
                    )
                }
            },
        )
    }

    val editingDiscountItem = itemToEditDiscount
    if (editingDiscountItem != null) {
        AlertDialog(
            onDismissRequest = { itemToEditDiscount = null },
            confirmButton = {
                Button(onClick = {
                    val parsed = discountInput.toDoubleOrNull()
                    if (parsed != null) {
                        viewModel.onAction(CartUiAction.UpdateItemDiscount(editingDiscountItem.product.id, parsed))
                        itemToEditDiscount = null
                    }
                }) { Text("Guardar") }
            },
            dismissButton = {
                TextButton(onClick = { itemToEditDiscount = null }) { Text("Cancelar") }
            },
            title = { Text("Aplicar descuento") },
            text = {
                Column {
                    Text(editingDiscountItem.product.description, fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = discountInput,
                        onValueChange = { discountInput = it },
                        label = { Text("Descuento (%)") },
                        singleLine = true,
                    )
                }
            },
        )
    }

    if (showSellerSheet) {
        SellerSelectorBottomSheet(
            sellers = state.availableSellers,
            selectedSellerId = state.currentSeller?.id,
            onSelect = { seller -> viewModel.onAction(CartUiAction.SelectSeller(seller.id)) },
            onDismiss = { showSellerSheet = false },
        )
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        "Carrito",
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Atrás",
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                },
                actions = {
                    if (state.items.isNotEmpty()) {
                        IconButton(onClick = { viewModel.onAction(CartUiAction.ClearCart) }) {
                            Icon(
                                Icons.Default.DeleteSweep,
                                contentDescription = "Limpiar carrito",
                                tint = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (state.items.isNotEmpty()) {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shadowElevation = 16.dp,
                    shape = PosExtraShapes.BottomBarTop,
                ) {
                    Column(modifier = Modifier.padding(24.dp).navigationBarsPadding()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Bottom,
                        ) {
                            Text("Total", style = MaterialTheme.typography.titleMedium)
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    "$${String.format(java.util.Locale.getDefault(), "%.2f", state.total)}",
                                    style = PosTextStyles.totalDisplay,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                                if (state.isMultiCurrency && state.totalBsText.isNotBlank()) {
                                    Text(
                                        "${formatCurrencyLabel(state.abrMonedaSecundaria)} ${state.totalBsText}",
                                        style = PosTextStyles.amountSecondary,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(24.dp))

                        // Botones de Acción
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            // 1. Boton GUARDAR BORRADOR (factura pendiente local)
                            OutlinedButton(
                                onClick = { viewModel.onAction(CartUiAction.SaveDraft) },
                                modifier = Modifier.weight(1f).height(56.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp),
                                shape = MaterialTheme.shapes.small,
                            ) {
                                Icon(Icons.Default.Save, null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Guardar", fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }

                            // 2. Botón PAGAR / FACTURAR
                            Button(
                                onClick = { viewModel.onAction(CartUiAction.Checkout) },
                                modifier = Modifier.weight(1f).height(56.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                shape = MaterialTheme.shapes.small,
                            ) {
                                Text(
                                    "Cobrar $${String.format(java.util.Locale.getDefault(), "%.2f", state.total)}",
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding).padding(16.dp)) {
            // SECCIÓN CLIENTE + VENDEDOR (panel consolidado)
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface, MaterialTheme.shapes.medium)
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.medium),
            ) {
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clickable { onSelectClient() }
                            .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val selectedClient = state.selectedClient
                    if (selectedClient != null) {
                        val clientPhotoUrl = state.selectedClientPhotoUrl
                        ClientAvatar(
                            clientPhotoUrl = clientPhotoUrl,
                            clientName = "${selectedClient.firstName} ${selectedClient.lastName}",
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                    } else {
                        Icon(Icons.Default.Person, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(16.dp))
                    }
                    if (selectedClient != null) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Cliente asignado:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                "${selectedClient.firstName} ${selectedClient.lastName}",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                            )
                        }
                        IconButton(onClick = { viewModel.onAction(CartUiAction.RemoveClient) }) {
                            Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error)
                        }
                    } else {
                        Text("Asignar Cliente", modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Icon(Icons.Default.Add, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clickable(enabled = state.availableSellers.isNotEmpty()) { showSellerSheet = true }
                            .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Default.Person, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Vendedor asignado:", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            text = state.currentSeller?.nombre ?: "Sin vendedor",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Icon(
                        Icons.Default.Autorenew,
                        contentDescription = "Cambiar vendedor",
                        tint =
                            if (state.availableSellers.isEmpty()) {
                                MaterialTheme.colorScheme.outline
                            } else {
                                MaterialTheme.colorScheme.primary
                            },
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (state.selectedClient != null && state.clientSucursales.isNotEmpty()) {
                ClientSucursalSelectorCard(
                    sucursales = state.clientSucursales,
                    selectedSucursal = state.selectedClientSucursal,
                    isRequiredMissing = state.isMissingRequiredClientSucursal,
                    onSelect = { branchId -> viewModel.onAction(CartUiAction.SelectClientBranch(branchId)) },
                )

                Spacer(modifier = Modifier.height(12.dp))
            }

            state.cartActionError?.let { message ->
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.errorContainer,
                ) {
                    Text(
                        text = message,
                        modifier = Modifier.padding(12.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))
            }

            if (state.items.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CartEmptyState()
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(state.displayItems, key = { it.id }) { displayItem ->
                        when (displayItem) {
                            is ItemCarrito.ProductoIndividual -> {
                                val item = displayItem.item
                                CartItemRow(
                                    item = item,
                                    onIncrease = { viewModel.onAction(CartUiAction.IncreaseQuantity(item.product.id)) },
                                    onDecrease = { viewModel.onAction(CartUiAction.DecreaseQuantity(item.product.id)) },
                                    onRemove = { viewModel.onAction(CartUiAction.RemoveItem(item.product.id)) },
                                    allowEditPrice = state.allowEditPrices,
                                    allowDiscount = state.allowDiscounts,
                                    onEditPrice = {
                                        itemToEditPrice = item
                                        priceInput = String.format(java.util.Locale.getDefault(), "%.2f", item.unitPriceWithTax)
                                    },
                                    onEditDiscount = {
                                        itemToEditDiscount = item
                                        discountInput = String.format(java.util.Locale.getDefault(), "%.2f", item.discountPercent)
                                    },
                                    onUnitChange = { unit ->
                                        viewModel.onAction(CartUiAction.UpdateItemUnit(item.product.id, unit))
                                    },
                                    onQuantityChange = { quantity ->
                                        viewModel.onAction(CartUiAction.UpdateItemQuantity(item.product.id, quantity))
                                    },
                                )
                            }
                            is ItemCarrito.PromocionAgrupada -> {
                                PromotionCartGroup(
                                    group = displayItem,
                                    onRemove = {
                                        viewModel.onAction(CartUiAction.RemovePromotion(displayItem.promocionId))
                                    },
                                    onQuantityChange = { times ->
                                        viewModel.onAction(
                                            CartUiAction.UpdatePromotionQuantity(displayItem.promocionId, times),
                                        )
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ClientSucursalSelectorCard(
    sucursales: List<com.amaxonia.pos.domain.model.ClientBranch>,
    selectedSucursal: com.amaxonia.pos.domain.model.ClientBranch?,
    isRequiredMissing: Boolean,
    onSelect: (Int) -> Unit,
) {
    var expanded by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    val hasMultiple = sucursales.size > 1

    Card(
        shape = MaterialTheme.shapes.medium,
        colors =
            CardDefaults.cardColors(
                containerColor =
                    if (isRequiredMissing) {
                        MaterialTheme.colorScheme.errorContainer
                    } else {
                        MaterialTheme.colorScheme.surface
                    },
            ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = if (hasMultiple) "Sucursal del cliente" else "Sucursal del cliente asignada",
                fontSize = 12.sp,
                color = if (isRequiredMissing) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(8.dp))

            if (hasMultiple) {
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = !expanded },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    OutlinedTextField(
                        value = selectedSucursal?.nombreSucursal.orEmpty(),
                        onValueChange = {},
                        readOnly = true,
                        singleLine = true,
                        isError = isRequiredMissing,
                        placeholder = { Text("Seleccionar sucursal") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        modifier =
                            Modifier
                                .menuAnchor(MenuAnchorType.PrimaryNotEditable, enabled = true)
                                .fillMaxWidth(),
                    )
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false },
                    ) {
                        sucursales.forEach { sucursal ->
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(sucursal.nombreSucursal, fontWeight = FontWeight.SemiBold)
                                        sucursal.direccion?.takeIf { it.isNotBlank() }?.let {
                                            Text(it, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                },
                                onClick = {
                                    onSelect(sucursal.sucursalId)
                                    expanded = false
                                },
                            )
                        }
                    }
                }
                if (isRequiredMissing) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Este cliente tiene varias sucursales. Selecciona una para continuar.",
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        fontSize = 12.sp,
                    )
                }
            } else {
                Text(
                    text = selectedSucursal?.nombreSucursal ?: sucursales.firstOrNull()?.nombreSucursal.orEmpty(),
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = if (isRequiredMissing) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurface,
                )
                selectedSucursal?.direccion?.takeIf { it.isNotBlank() }?.let {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(it, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun ClientAvatar(
    clientPhotoUrl: String,
    clientName: String,
    modifier: Modifier = Modifier,
) {
    val initials = buildInitials(clientName)
    val gradient = cartBrandGradient()

    Box(
        modifier =
            modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(Brush.linearGradient(gradient)),
        contentAlignment = Alignment.Center,
    ) {
        if (clientPhotoUrl.isBlank()) {
            Text(initials, color = PosPalette.FixedWhite, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        } else {
            SubcomposeAsyncImage(
                model = clientPhotoUrl,
                contentDescription = "Foto cliente",
                modifier = Modifier.fillMaxSize(),
                loading = { Text(initials, color = PosPalette.FixedWhite, fontWeight = FontWeight.Bold, fontSize = 13.sp) },
                error = { Text(initials, color = PosPalette.FixedWhite, fontWeight = FontWeight.Bold, fontSize = 13.sp) },
                success = { SubcomposeAsyncImageContent(modifier = Modifier.fillMaxSize()) },
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
private fun PromotionCartGroup(
    group: ItemCarrito.PromocionAgrupada,
    onRemove: () -> Unit,
    onQuantityChange: (Int) -> Unit,
) {
    val accent = if (group.promocionTipo == "KIT") MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary
    var timesText by androidx.compose.runtime.remember(group.promocionId, group.items.firstOrNull()?.promocionVeces) {
        androidx.compose.runtime.mutableStateOf((group.items.firstOrNull()?.promocionVeces ?: 1).toString())
    }
    val times = timesText.toIntOrNull() ?: 0
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier =
                        Modifier
                            .size(42.dp)
                            .clip(MaterialTheme.shapes.medium)
                            .background(
                                Brush.linearGradient(
                                    listOf(
                                        accent,
                                        MaterialTheme.colorScheme.primary,
                                    ),
                                ),
                            ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Default.LocalOffer, contentDescription = null, tint = PosPalette.FixedWhite)
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("PROMOCIÓN ${group.promocionCodigo}", color = accent, fontWeight = FontWeight.ExtraBold, fontSize = 11.sp)
                    Text(
                        group.promocionNombre,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 16.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        "EL PRODUCTO ESTÁ CONFORMADO POR:",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
                IconButton(onClick = onRemove) {
                    Icon(Icons.Default.Delete, contentDescription = "Quitar promoción", tint = MaterialTheme.colorScheme.error)
                }
            }

            Spacer(Modifier.height(10.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                group.items.forEach { item ->
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clip(MaterialTheme.shapes.medium)
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                                .padding(horizontal = 10.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(modifier = Modifier.size(7.dp).clip(CircleShape).background(accent))
                        Spacer(Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                item.product.description,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                "Cant. ${String.format(java.util.Locale.getDefault(), "%.2f", item.quantityDecimal)}",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(
                            "$ ${String.format(java.util.Locale.getDefault(), "%.2f", item.total)}",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = accent,
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Total promoción", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                Text(BigDecimalMoneyFormatter.money(group.total), fontWeight = FontWeight.ExtraBold, color = accent, fontSize = 16.sp)
            }

            Spacer(Modifier.height(10.dp))
            QuantityStepper(
                quantityText = timesText,
                onQuantityTextChange = { value ->
                    timesText = sanitizeQuantityInput(value)
                    timesText.toIntOrNull()?.takeIf { it >= 1 }?.let(onQuantityChange)
                },
                onDecrease = {
                    val next = ((timesText.toIntOrNull() ?: 1) - 1).coerceAtLeast(0)
                    if (next == 0) {
                        onRemove()
                    } else {
                        timesText = next.toString()
                        onQuantityChange(next)
                    }
                },
                onIncrease = {
                    val next = ((timesText.toIntOrNull() ?: 0) + 1).coerceAtLeast(1)
                    timesText = next.toString()
                    onQuantityChange(next)
                },
                onDone = {
                    if (times >= 1) onQuantityChange(times)
                },
                isError = timesText.isNotBlank() && times < 1,
                label = "Promociones",
            )
        }
    }
}


private fun sanitizeQuantityInput(value: String): String =
    value
        .filter { it.isDigit() }
        .trimStart('0')
        .ifBlank { "" }
        .take(5)

@Composable
fun CartItemRow(
    item: com.amaxonia.pos.domain.model.CartItem,
    onIncrease: () -> Unit,
    onDecrease: () -> Unit,
    onRemove: () -> Unit,
    allowEditPrice: Boolean,
    allowDiscount: Boolean,
    onEditPrice: () -> Unit,
    onEditDiscount: () -> Unit,
    onUnitChange: (String) -> Unit,
    onQuantityChange: (Int) -> Unit,
) {
    var unitMenuExpanded by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    var quantityText by androidx.compose.runtime.remember(item.product.id, item.quantity) {
        androidx.compose.runtime.mutableStateOf(item.quantity.toString())
    }
    val typedQuantity = quantityText.toIntOrNull() ?: 0

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(modifier = Modifier.padding(12.dp).fillMaxWidth()) {
            // Descripcion completa (hasta 2 lineas)
            Text(
                item.product.description,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Precio, controles de cantidad, total y acciones
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "$ ${String.format(
                        java.util.Locale.getDefault(),
                        "%.2f",
                        item.unitPriceWithTax,
                    )} / ${item.displayUnitLabel.lowercase()}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp,
                )

                Spacer(modifier = Modifier.weight(1f))

                Text(
                    "$ ${String.format(java.util.Locale.getDefault(), "%.2f", item.total)}",
                    style = PosTextStyles.priceTileLarge,
                    color = MaterialTheme.colorScheme.primary,
                )

                // Acciones compactas
                if (allowEditPrice) {
                    IconButton(onClick = onEditPrice, modifier = Modifier.size(28.dp)) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = "Editar precio",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
                if (allowDiscount) {
                    IconButton(onClick = onEditDiscount, modifier = Modifier.size(28.dp)) {
                        Icon(
                            Icons.Default.Percent,
                            contentDescription = "Aplicar descuento",
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
                IconButton(onClick = onRemove, modifier = Modifier.size(28.dp)) {
                    Icon(
                        Icons.Default.Delete,
                        null,
                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                        modifier = Modifier.size(16.dp),
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            QuantityStepper(
                quantityText = quantityText,
                onQuantityTextChange = { value ->
                    quantityText = sanitizeQuantityInput(value)
                    quantityText.toIntOrNull()?.takeIf { it >= 1 }?.let(onQuantityChange)
                },
                onDecrease = {
                    val next = (item.quantity - 1)
                    if (next <= 0) {
                        onRemove()
                    } else {
                        quantityText = next.toString()
                        onDecrease()
                    }
                },
                onIncrease = {
                    val next = item.quantity + 1
                    quantityText = next.toString()
                    onIncrease()
                },
                onDone = {
                    if (typedQuantity >= 1) onQuantityChange(typedQuantity)
                },
                isError = quantityText.isNotBlank() && typedQuantity < 1,
                label = "Cantidad",
            )

            if (item.product.canSwitchUnit) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Unidad:",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Box {
                        AssistChip(
                            onClick = { unitMenuExpanded = true },
                            label = { Text(item.displayUnitLabel) },
                            leadingIcon = {
                                Icon(Icons.Default.Autorenew, contentDescription = null, modifier = Modifier.size(16.dp))
                            },
                        )
                        DropdownMenu(
                            expanded = unitMenuExpanded,
                            onDismissRequest = { unitMenuExpanded = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text("UNIDAD") },
                                onClick = {
                                    unitMenuExpanded = false
                                    onUnitChange("UNIDAD")
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(item.product.packageLabel) },
                                onClick = {
                                    unitMenuExpanded = false
                                    onUnitChange("EMPAQUE")
                                },
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "Total unidades: ${String.format(java.util.Locale.getDefault(), "%.2f", item.quantityTotal)}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                    )
                }
            }

            // Descuento (condicional)
            if (item.discountPercent > 0.0) {
                Text(
                    "Desc: ${String.format(
                        java.util.Locale.getDefault(),
                        "%.2f",
                        item.discountPercent,
                    )}% (-$ ${String.format(java.util.Locale.getDefault(), "%.2f", item.discountAmountWithoutTax)})",
                    color = MaterialTheme.colorScheme.tertiary,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }

            // Lotes asignados (condicional)
            if (item.lotAssignments.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                item.lotAssignments.forEach { lot ->
                    val expiry = if (!lot.vencimiento.isNullOrBlank()) " - Vence: ${lot.vencimiento}" else ""
                    Text(
                        "Lote: ${lot.codigoLote} (${lot.cantidad} uds$expiry)",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
