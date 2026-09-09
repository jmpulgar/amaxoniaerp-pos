package com.amaxonia.pos.ui.cart

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.TableRestaurant
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amaxonia.pos.domain.model.CartItem
import com.amaxonia.pos.domain.model.ItemCarrito
import com.amaxonia.pos.ui.common.SellerSelectorBottomSheet
import com.amaxonia.pos.ui.common.components.CartEmptyState
import java.util.Locale

/** Qué valor de un ítem se está editando desde el carrito. */
internal enum class CartEditTarget {
    PRICE,
    DISCOUNT,
}

/** Estado de edición de precio/descuento de un ítem del carrito. */
internal class CartEditState {
    var target: CartEditTarget? by mutableStateOf(null)
    var item: CartItem? by mutableStateOf(null)
    var value: String by mutableStateOf("")

    fun start(
        newTarget: CartEditTarget,
        newItem: CartItem,
    ) {
        target = newTarget
        item = newItem
        value =
            String.format(
                Locale.getDefault(),
                "%.2f",
                when (newTarget) {
                    CartEditTarget.PRICE -> newItem.unitPriceWithTax
                    CartEditTarget.DISCOUNT -> newItem.discountPercent
                },
            )
    }

    fun clear() {
        target = null
        item = null
        value = ""
    }
}

/** Acciones que el contenido del carrito delega en la pantalla. */
internal class CartScreenActions(
    val onSelectClient: () -> Unit,
    val onChangeSeller: () -> Unit,
    val onStartEdit: (CartEditTarget, CartItem) -> Unit,
)

/** Diálogo de borrador guardado con éxito. */
@Composable
internal fun OrderSavedDialog(
    message: String,
    onDismiss: () -> Unit,
    onAccept: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Borrador Guardado") },
        text = { Text(message) },
        confirmButton = {
            Button(onClick = onAccept) { Text("Aceptar") }
        },
    )
}

/** Ítem y tipo de valor en edición. */
private class CartEditRequest(
    val target: CartEditTarget,
    val item: CartItem,
)

/** Diálogos de edición de precio/descuento del ítem en curso. */
@Composable
internal fun CartEditDialogs(
    editState: CartEditState,
    viewModel: CartViewModel,
) {
    val item = editState.item ?: return
    val target =
        editState.target
            ?: return
    EditItemValueDialog(
        request = CartEditRequest(target = target, item = item),
        value = editState.value,
        onValueChange = { editState.value = it },
        onConfirm = { parsed ->
            when (target) {
                CartEditTarget.PRICE ->
                    viewModel.onAction(CartUiAction.UpdateItemPrice(item.product.id, parsed))
                CartEditTarget.DISCOUNT ->
                    viewModel.onAction(CartUiAction.UpdateItemDiscount(item.product.id, parsed))
            }
            editState.clear()
        },
        onDismiss = editState::clear,
    )
}

/** Diálogo genérico para editar un valor numérico del ítem. */
@Composable
private fun EditItemValueDialog(
    request: CartEditRequest,
    value: String,
    onValueChange: (String) -> Unit,
    onConfirm: (Double) -> Unit,
    onDismiss: () -> Unit,
) {
    val title: String
    val label: String
    when (request.target) {
        CartEditTarget.PRICE -> {
            title = "Editar precio unitario"
            label = "Precio unitario con IVA"
        }
        CartEditTarget.DISCOUNT -> {
            title = "Aplicar descuento"
            label = "Descuento (%)"
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(onClick = {
                val parsed = value.toDoubleOrNull()
                if (parsed != null) {
                    onConfirm(parsed)
                }
            }) { Text("Guardar") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        },
        title = { Text(title) },
        text = {
            Column {
                Text(request.item.product.description, fontSize = 13.sp)
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = value,
                    onValueChange = onValueChange,
                    label = { Text(label) },
                    singleLine = true,
                )
            }
        },
    )
}

/** Overlay del mensaje de éxito al guardar un borrador. */
@Composable
internal fun CartOrderSavedOverlay(
    state: CartState,
    viewModel: CartViewModel,
    onBack: () -> Unit,
) {
    state.orderSuccessMessage?.let { successMessage ->
        OrderSavedDialog(
            message = successMessage,
            onDismiss = { viewModel.onAction(CartUiAction.ClearMessage) },
            onAccept = {
                viewModel.onAction(CartUiAction.ClearMessage)
                onBack()
            },
        )
    }
}

/** Overlay del selector de vendedor. */
@Composable
internal fun CartSellerSheetOverlay(
    state: CartState,
    viewModel: CartViewModel,
    onDismiss: () -> Unit,
) {
    SellerSelectorBottomSheet(
        sellers = state.availableSellers,
        selectedSellerId = state.currentSeller?.id,
        onSelect = { seller -> viewModel.onAction(CartUiAction.SelectSeller(seller.id)) },
        onDismiss = onDismiss,
    )
}

/** Barra superior del carrito con contador y limpieza. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CartTopAppBar(
    state: CartState,
    onBack: () -> Unit,
    onClearCart: () -> Unit,
) {
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
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.70f),
                        modifier = Modifier.padding(top = 2.dp),
                    ) {
                        Text(
                            text = if (state.items.size == 1) "1 artículo" else "${state.items.size} artículos",
                            style =
                                MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp,
                                ),
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        )
                    }
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
                Surface(
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f),
                    modifier = Modifier.padding(end = 8.dp),
                ) {
                    IconButton(onClick = onClearCart, modifier = Modifier.size(38.dp)) {
                        Icon(
                            Icons.Default.DeleteSweep,
                            contentDescription = "Limpiar carrito",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
        },
        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = MaterialTheme.colorScheme.background),
    )
}

/** Barra inferior con total y acciones (sólo con ítems). */
@Composable
internal fun CartBottomBarArea(
    state: CartState,
    viewModel: CartViewModel,
) {
    if (state.items.isNotEmpty()) {
        CartBottomBar(
            state = state,
            onSaveDraft = { viewModel.onAction(CartUiAction.SaveDraft) },
            onCheckout = { viewModel.onAction(CartUiAction.Checkout) },
        )
    }
}

/** Contenido principal del carrito: cliente/vendedor, sucursal, errores y lista. */
@Composable
internal fun CartScreenContent(
    state: CartState,
    viewModel: CartViewModel,
    actions: CartScreenActions,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 8.dp)) {
        if (state.sesionMesaId != null) {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f),
                shape = RoundedCornerShape(14.dp),
                shadowElevation = 1.dp,
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(36.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Default.TableRestaurant,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Modo Comanda: ${state.selectedTable?.mesa?.displayName ?: "Mesa"}",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        state.selectedTable?.area?.displayName?.takeIf { it.isNotBlank() }?.let { area ->
                            Text(
                                text = "Área: $area",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }

        // SECCIÓN CLIENTE + VENDEDOR (panel consolidado)
        CartClientVendorPanel(
            state = state,
            onSelectClient = actions.onSelectClient,
            onRemoveClient = { viewModel.onAction(CartUiAction.RemoveClient) },
            onChangeSeller = actions.onChangeSeller,
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
            CartItemsList(state = state, viewModel = viewModel, onStartEdit = actions.onStartEdit)
        }
    }
}

/** Lista de ítems del carrito (productos y promociones agrupadas). */
@Composable
private fun CartItemsList(
    state: CartState,
    viewModel: CartViewModel,
    onStartEdit: (CartEditTarget, CartItem) -> Unit,
) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        items(state.displayItems, key = { it.id }) { displayItem ->
            when (displayItem) {
                is ItemCarrito.ProductoIndividual -> {
                    val item = displayItem.item
                    CartItemRow(
                        item = item,
                        actions = buildCartItemActions(item, viewModel, onStartEdit),
                        allowEditPrice = state.allowEditPrices,
                        allowDiscount = state.allowDiscounts,
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

/** Construye las acciones de un ítem de carrito ligadas al ViewModel. */
private fun buildCartItemActions(
    item: CartItem,
    viewModel: CartViewModel,
    onStartEdit: (CartEditTarget, CartItem) -> Unit,
): CartItemActions =
    CartItemActions(
        onIncrease = { viewModel.onAction(CartUiAction.IncreaseQuantity(item.product.id)) },
        onDecrease = { viewModel.onAction(CartUiAction.DecreaseQuantity(item.product.id)) },
        onRemove = { viewModel.onAction(CartUiAction.RemoveItem(item.product.id)) },
        onUnitChange = { unit ->
            viewModel.onAction(CartUiAction.UpdateItemUnit(item.product.id, unit))
        },
        onQuantityChange = { quantity ->
            viewModel.onAction(CartUiAction.UpdateItemQuantity(item.product.id, quantity))
        },
        onPriceLevelChange = { level ->
            viewModel.onAction(CartUiAction.UpdateItemPriceLevel(item.product.id, level))
        },
        edit =
            CartItemEditActions(
                onEditPrice = { onStartEdit(CartEditTarget.PRICE, item) },
                onEditDiscount = { onStartEdit(CartEditTarget.DISCOUNT, item) },
            ),
    )
