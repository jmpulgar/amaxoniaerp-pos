package com.amaxonia.pos.ui.cart
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material.icons.filled.Percent
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Storefront
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
import com.amaxonia.pos.ui.common.components.AdaptiveAmountText
import com.amaxonia.pos.ui.common.components.CartEmptyState
import com.amaxonia.pos.ui.common.components.QuantityStepper
import com.amaxonia.pos.ui.common.injectedViewModel
import com.amaxonia.pos.ui.payment.formatCurrencyLabel
import com.amaxonia.pos.ui.theme.PosExtraShapes
import com.amaxonia.pos.ui.theme.PosPalette
import com.amaxonia.pos.ui.theme.PosTextStyles
import com.amaxonia.pos.ui.theme.cartBrandGradient

/** Peso del CTA Cobrar frente a Guardar en la barra inferior del carrito. */
private const val CHECKOUT_BUTTON_WEIGHT = 1.5f

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
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "Carrito",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        if (state.items.isNotEmpty()) {
                            Text(
                                if (state.items.size == 1) "1 artículo" else "${state.items.size} artículos",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Atrás",
                            tint = MaterialTheme.colorScheme.onSurface,
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
                CartBottomBar(
                    total = state.total,
                    secondaryTotal =
                        state.totalBsText
                            .takeIf { state.isMultiCurrency && it.isNotBlank() }
                            ?.let { "${formatCurrencyLabel(state.abrMonedaSecundaria)} $it" },
                    onSaveDraft = { viewModel.onAction(CartUiAction.SaveDraft) },
                    onCheckout = { viewModel.onAction(CartUiAction.Checkout) },
                )
            }
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding).padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 8.dp)) {
            // SECCIÓN CLIENTE + VENDEDOR (panel consolidado)
            CartClientVendorPanel(
                state = state,
                onSelectClient = onSelectClient,
                onRemoveClient = { viewModel.onAction(CartUiAction.RemoveClient) },
                onChangeSeller = { showSellerSheet = true },
            )

            Spacer(modifier = Modifier.height(12.dp))

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
                CartErrorBanner(message)

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

// ─────────────────────────────────────────────────────────────────────────────
// Bottom bar: total (adaptive) + Guardar borrador / Cobrar $XX.XX (adaptive, nunca elipsis).
// ─────────────────────────────────────────────────────────────────────────────

@Composable
internal fun CartBottomBar(
    total: Double,
    secondaryTotal: String?,
    onSaveDraft: () -> Unit,
    onCheckout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 16.dp,
        shape = PosExtraShapes.BottomBarTop,
    ) {
        BoxWithConstraints {
            val compact = maxHeight < 480.dp
            Column(
                modifier =
                    Modifier
                        .padding(horizontal = 20.dp, vertical = if (compact) 8.dp else 12.dp)
                        .navigationBarsPadding(),
            ) {
                CartBottomTotal(total = total, secondaryTotal = secondaryTotal, compact = compact)
                Spacer(modifier = Modifier.height(if (compact) 8.dp else 12.dp))
                CartBottomActions(total = total, onSaveDraft = onSaveDraft, onCheckout = onCheckout)
            }
        }
    }
}

@Composable
private fun CartBottomTotal(
    total: Double,
    secondaryTotal: String?,
    compact: Boolean,
) {
    val totalStyle =
        if (compact) {
            MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.ExtraBold)
        } else {
            PosTextStyles.totalDisplay
        }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(
            "Total",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Column(horizontalAlignment = Alignment.End) {
            AdaptiveAmountText(
                text = "$${String.format(java.util.Locale.getDefault(), "%.2f", total)}",
                baseStyle = totalStyle,
                color = MaterialTheme.colorScheme.primary,
                options =
                    com.amaxonia.pos.ui.common.components.AdaptiveAmountOptions(
                        minFontSizeSp = 16f,
                    ),
            )
            secondaryTotal?.takeIf { it.isNotBlank() }?.let { secondary ->
                Text(
                    secondary,
                    style = PosTextStyles.amountSecondary,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun CartBottomActions(
    total: Double,
    onSaveDraft: () -> Unit,
    onCheckout: () -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(modifier = Modifier.weight(1f)) {
            SaveDraftButton(onClick = onSaveDraft)
        }
        Box(modifier = Modifier.weight(CHECKOUT_BUTTON_WEIGHT)) {
            CheckoutButton(total = total, onClick = onCheckout)
        }
    }
}

@Composable
private fun SaveDraftButton(onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(56.dp),
        contentPadding = PaddingValues(horizontal = 8.dp),
        shape = MaterialTheme.shapes.medium,
    ) {
        Icon(Icons.Default.Save, null, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            "Guardar",
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun CheckoutButton(
    total: Double,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(56.dp),
        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
        contentPadding = PaddingValues(horizontal = 12.dp),
        shape = MaterialTheme.shapes.medium,
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp, pressedElevation = 4.dp),
    ) {
        AdaptiveAmountText(
            text = "Cobrar $${String.format(java.util.Locale.getDefault(), "%.2f", total)}",
            baseStyle = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
            color = MaterialTheme.colorScheme.onPrimary,
            options =
                com.amaxonia.pos.ui.common.components.AdaptiveAmountOptions(
                    minFontSizeSp = 13f,
                ),
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Cliente + vendedor: panel consolidado con tipografía tokenizada e iconos
// diferenciados (Person / Storefront). Mismos clicks y estados habilitados.
// ─────────────────────────────────────────────────────────────────────────────

@Composable
internal fun CartClientVendorPanel(
    state: CartState,
    onSelectClient: () -> Unit,
    onRemoveClient: () -> Unit,
    onChangeSeller: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface, MaterialTheme.shapes.medium)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.medium),
    ) {
        CartClientRow(state = state, onSelectClient = onSelectClient, onRemoveClient = onRemoveClient)
        HorizontalDivider(
            color = MaterialTheme.colorScheme.outlineVariant,
            modifier = Modifier.padding(horizontal = 14.dp),
        )
        CartSellerRow(state = state, onChangeSeller = onChangeSeller)
    }
}

@Composable
private fun CartClientRow(
    state: CartState,
    onSelectClient: () -> Unit,
    onRemoveClient: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable { onSelectClient() }
                .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val selectedClient = state.selectedClient
        if (selectedClient != null) {
            ClientAvatar(
                clientPhotoUrl = state.selectedClientPhotoUrl,
                clientName = "${selectedClient.firstName} ${selectedClient.lastName}",
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Cliente asignado",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "${selectedClient.firstName} ${selectedClient.lastName}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = onRemoveClient) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Quitar cliente",
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        } else {
            Icon(Icons.Default.Person, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                "Asignar cliente",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Icon(Icons.Default.Add, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun CartSellerRow(
    state: CartState,
    onChangeSeller: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(enabled = state.availableSellers.isNotEmpty()) { onChangeSeller() }
                .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Default.Storefront, null, tint = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                "Vendedor asignado",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = state.currentSeller?.nombre ?: "Sin vendedor",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
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

@Composable
private fun CartErrorBanner(message: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.errorContainer,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                Icons.Default.ErrorOutline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.size(20.dp),
            )
            Text(
                text = message,
                color = MaterialTheme.colorScheme.onErrorContainer,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
            )
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
                .size(40.dp)
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
            // Fila 1: descripción (flexible) + eliminar (target ≥48dp vía minimum interactive).
            Row(verticalAlignment = Alignment.Top) {
                Text(
                    item.product.description,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onRemove, modifier = Modifier.size(40.dp)) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Quitar del carrito",
                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.85f),
                        modifier = Modifier.size(18.dp),
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Fila 2: precio unitario (flexible) + total de línea (adaptive, nunca desborda).
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
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Spacer(modifier = Modifier.weight(1f))
                AdaptiveAmountText(
                    text = "$ ${String.format(java.util.Locale.getDefault(), "%.2f", item.total)}",
                    baseStyle = PosTextStyles.priceTileLarge,
                    color = MaterialTheme.colorScheme.primary,
                    options =
                        com.amaxonia.pos.ui.common.components.AdaptiveAmountOptions(
                            minFontSizeSp = 13f,
                        ),
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Fila 3: stepper (flexible) + acciones de precio/descuento con targets ≥48dp
            // (IconButton enforza minimum interactive size) y sin solapamiento.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
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
                    modifier = Modifier.weight(1f),
                )
                if (allowEditPrice) {
                    IconButton(onClick = onEditPrice, modifier = Modifier.size(40.dp)) {
                        Icon(
                            Icons.Default.Edit,
                            contentDescription = "Editar precio",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
                if (allowDiscount) {
                    IconButton(onClick = onEditDiscount, modifier = Modifier.size(40.dp)) {
                        Icon(
                            Icons.Default.Percent,
                            contentDescription = "Aplicar descuento",
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }

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
