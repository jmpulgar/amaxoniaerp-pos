package com.amaxonia.erp.ui.pos

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest
import com.amaxonia.erp.data.remote.dto.FormaPagoDto
import com.amaxonia.erp.domain.model.Client
import com.amaxonia.erp.domain.model.ClientBranch
import com.amaxonia.erp.domain.model.Product
import com.amaxonia.erp.domain.model.SellerSummary
import com.amaxonia.erp.ui.common.SellerSelectorBottomSheet
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MenuAnchorType
import com.amaxonia.erp.ui.components.AdaptiveAmountOptions
import com.amaxonia.erp.ui.components.AdaptiveAmountText
import com.amaxonia.erp.ui.components.PosEmptyState
import com.amaxonia.erp.ui.components.PosFeedbackCard
import com.amaxonia.erp.ui.components.PosGradientButton
import com.amaxonia.erp.ui.components.PosStatusBadge
import com.amaxonia.erp.ui.components.PosVisualTone
import com.amaxonia.erp.ui.components.isLandscape
import com.amaxonia.erp.ui.theme.ConfirmedContainer
import com.amaxonia.erp.ui.theme.ConfirmedContent
import com.amaxonia.erp.ui.theme.PosExtraShapes
import com.amaxonia.erp.ui.theme.PosPalette
import com.amaxonia.erp.ui.theme.PosTextStyles
import java.util.Locale

@Composable
fun PosTerminalScreen(
    viewModel: PosTerminalViewModel,
    onNavigateToCajas: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsState()
    var selectedTab by remember { mutableIntStateOf(0) } // 0: Catálogo, 1: Carrito
    val isLandscape = isLandscape()

    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
    ) {
        if (isLandscape) {
            // Diseño Master-Detail para Modo Horizontal (Landscape)
            PosTerminalLandscapeLayout(
                state = state,
                onSearchQueryChange = viewModel::onSearchQueryChange,
                onDepartmentSelected = viewModel::onDepartmentSelected,
                onAddToCart = viewModel::addToCart,
                onIncrement = viewModel::incrementQuantity,
                onDecrement = viewModel::decrementQuantity,
                onRemove = viewModel::removeFromCart,
                onClearCart = viewModel::clearCart,
                onOpenPayment = viewModel::openPaymentDialog,
                onSelectClient = { viewModel.openClientDialog() },
                onRemoveClient = { viewModel.removeSelectedClient() },
                onChangeSeller = { viewModel.openSellerSheet() },
                onSelectBranch = viewModel::selectClientBranch,
                onNavigateToCajas = onNavigateToCajas,
                onRenovarCaja = viewModel::renovarCajaDiaAnterior,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            // Diseño Monocolumna con Pestañas para Modo Vertical (Portrait)
            PosTerminalPortraitLayout(
                state = state,
                selectedTab = selectedTab,
                onTabSelected = { selectedTab = it },
                onSearchQueryChange = viewModel::onSearchQueryChange,
                onDepartmentSelected = viewModel::onDepartmentSelected,
                onAddToCart = viewModel::addToCart,
                onIncrement = viewModel::incrementQuantity,
                onDecrement = viewModel::decrementQuantity,
                onRemove = viewModel::removeFromCart,
                onClearCart = viewModel::clearCart,
                onOpenPayment = viewModel::openPaymentDialog,
                onSelectClient = { viewModel.openClientDialog() },
                onRemoveClient = { viewModel.removeSelectedClient() },
                onChangeSeller = { viewModel.openSellerSheet() },
                onSelectBranch = viewModel::selectClientBranch,
                onNavigateToCajas = onNavigateToCajas,
                onRenovarCaja = viewModel::renovarCajaDiaAnterior,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }

    // --- Diálogos y Modales ---
    if (state.showPaymentDialog) {
        PaymentDialog(
            state = state,
            onReceivedAmountChange = viewModel::onReceivedAmountChange,
            onSelectPaymentMethod = viewModel::selectPaymentMethod,
            onConfirmPayment = viewModel::processSale,
            onDismiss = viewModel::dismissPaymentDialog,
        )
    }

    if (state.showAvisoCajaAnterior) {
        com.amaxonia.erp.ui.caja.AvisoCajaAnteriorDialog(
            fechaApertura = state.cajaFechaApertura,
            isRenovando = state.isRenovandoCaja,
            onDismiss = viewModel::dismissAvisoCajaAnterior,
            onRenovar = viewModel::renovarCajaDiaAnterior,
        )
    }

    if (state.showCajaWarningDialog) {
        CajaWarningDialog(
            onDismiss = viewModel::dismissCajaWarning,
            onGoToCajas = {
                viewModel.dismissCajaWarning()
                onNavigateToCajas()
            },
        )
    }

    if (state.completedSaleInvoice != null || state.completedSaleInfo != null) {
        SaleSuccessDialog(
            completedInfo = state.completedSaleInfo,
            fallbackInvoiceNumber = state.completedSaleInvoice.orEmpty(),
            isPrinting = state.isPrintingReceipt,
            printFeedback = state.printFeedbackMessage,
            isPrintSuccess = state.isPrintSuccess,
            onPrint = { viewModel.printReceipt() },
            onDismiss = {
                viewModel.dismissSuccessDialog()
                selectedTab = 0
            },
        )
    }

    if (state.showClientDialog) {
        ClientSelectionDialog(
            currentClient = state.selectedClient,
            onSearch = viewModel::searchClients,
            onClientSelected = viewModel::selectClient,
            onDismiss = viewModel::dismissClientDialog,
        )
    }

    if (state.showSellerSheet) {
        SellerSelectorBottomSheet(
            sellers = state.availableSellers,
            selectedSellerId = state.selectedSeller?.id,
            onSelect = viewModel::selectSeller,
            onDismiss = viewModel::dismissSellerSheet,
        )
    }
}

/**
 * Layout Master-Detail de Alto Rendimiento para Terminal POS en Modo Horizontal.
 * Catálogo a la izquierda (~58% de ancho) y Carrito con Cobro a la derecha (~42% de ancho).
 * Elimina la necesidad de pestañas y maximiza el área vertical de desplazamiento.
 */
@Composable
private fun PosTerminalLandscapeLayout(
    state: PosUiState,
    onSearchQueryChange: (String) -> Unit,
    onDepartmentSelected: (Int?) -> Unit,
    onAddToCart: (Product) -> Unit,
    onIncrement: (String) -> Unit,
    onDecrement: (String) -> Unit,
    onRemove: (String) -> Unit,
    onClearCart: () -> Unit,
    onOpenPayment: () -> Unit,
    onSelectClient: () -> Unit,
    onRemoveClient: () -> Unit,
    onChangeSeller: () -> Unit,
    onSelectBranch: (ClientBranch) -> Unit,
    onNavigateToCajas: () -> Unit,
    onRenovarCaja: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        PosHeaderBar(
            cajaName = state.activeCajaName,
            isCajaOpen = state.isCajaOpen,
            isDiaAnterior = state.isCajaDiaAnterior,
            fechaApertura = state.cajaFechaApertura,
            sucursalNombre = state.sucursalNombre,
            almacenNombre = state.almacenNombre,
            usuarioApertura = state.usuarioApertura,
            client = state.selectedClient,
            onClientClick = onSelectClient,
            onCajaClick = onNavigateToCajas,
            onRenovar = onRenovarCaja,
            isRenovando = state.isRenovandoCaja,
        )

        Row(
            modifier =
                Modifier
                    .weight(1f)
                    .fillMaxWidth(),
        ) {
        // --- Panel Izquierdo: Catálogo de Productos ---
        Column(
            modifier =
                Modifier
                    .weight(1.35f)
                    .fillMaxHeight(),
        ) {
            // Buscador compacto de 46dp
            OutlinedTextField(
                value = state.searchQuery,
                onValueChange = onSearchQueryChange,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                placeholder = { Text("Buscar producto...", fontSize = 13.sp) },
                leadingIcon = {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = "Buscar",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                },
                trailingIcon = {
                    if (state.searchQuery.isNotEmpty()) {
                        IconButton(onClick = { onSearchQueryChange("") }) {
                            Icon(Icons.Default.Clear, contentDescription = "Limpiar", modifier = Modifier.size(18.dp))
                        }
                    }
                },
                singleLine = true,
                shape = PosExtraShapes.InputRadius,
                colors =
                    OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    ),
            )

            // Chips de filtro por departamento compactos
            if (state.departments.isNotEmpty()) {
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 12.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    FilterChip(
                        selected = state.selectedDepartmentId == null,
                        onClick = { onDepartmentSelected(null) },
                        label = { Text("Todos", fontSize = 12.sp) },
                        shape = RoundedCornerShape(8.dp),
                        colors =
                            FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = PosPalette.FixedWhite,
                            ),
                    )
                    state.departments.forEach { dept ->
                        FilterChip(
                            selected = state.selectedDepartmentId == dept.id,
                            onClick = { onDepartmentSelected(dept.id) },
                            label = { Text(dept.name, fontSize = 12.sp) },
                            shape = RoundedCornerShape(8.dp),
                            colors =
                                FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                                    selectedLabelColor = PosPalette.FixedWhite,
                                ),
                        )
                    }
                }
            }

            // Cuadrícula de productos aprovechando toda la altura
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .weight(1f),
            ) {
                if (state.isLoading) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    }
                } else if (state.filteredProducts.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize().padding(16.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        PosEmptyState(
                            icon = Icons.Default.ShoppingCart,
                            title = "Sin productos",
                            message = "No se encontraron productos disponibles.",
                        )
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 135.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        items(state.filteredProducts, key = { it.id }) { product ->
                            ProductGridItem(
                                product = product,
                                onAddToCart = { onAddToCart(product) },
                            )
                        }
                    }
                }
            }
        }

        // Divisor vertical sutil
        VerticalDivider(
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
            modifier = Modifier.fillMaxHeight().width(1.dp),
        )

        // --- Panel Derecho: Carrito de Compras y Cobro Inmediato ---
        Column(
            modifier =
                Modifier
                    .weight(0.95f)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            // Panel superior consolidado: Cliente + Vendedor
            CartClientVendorPanel(
                state = state,
                onSelectClient = onSelectClient,
                onRemoveClient = onRemoveClient,
                onChangeSeller = onChangeSeller,
                modifier = Modifier.padding(bottom = 6.dp),
            )

            // Selector de Sucursal del Cliente (cuando tiene sucursales disponibles)
            if (state.clientBranches.isNotEmpty()) {
                ClientSucursalSelectorCard(
                    sucursales = state.clientBranches,
                    selectedSucursal = state.selectedClientBranch,
                    isRequiredMissing = state.branchSelectionRequiredError,
                    onSelect = onSelectBranch,
                    modifier = Modifier.padding(bottom = 6.dp),
                )
            }

            // Lista de renglones del carrito
            if (state.cart.isEmpty()) {
                Box(
                    modifier =
                        Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    PosEmptyState(
                        icon = Icons.Default.ShoppingCart,
                        title = "Carrito vacío",
                        message = "Toca productos del catálogo para agregarlos.",
                    )
                }
            } else {
                LazyColumn(
                    modifier =
                        Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(state.cart, key = { it.product.id }) { item ->
                        CartItemRow(
                            item = item,
                            onIncrement = { onIncrement(item.product.id) },
                            onDecrement = { onDecrement(item.product.id) },
                            onRemove = { onRemove(item.product.id) },
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Resumen de Totales y Botones de Cobro
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text("Subtotal", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        AdaptiveAmountText(
                            text = "$${String.format(Locale.US, "%.2f", state.summary.subtotal)}",
                            baseStyle = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text("IVA", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        AdaptiveAmountText(
                            text = "$${String.format(Locale.US, "%.2f", state.summary.tax)}",
                            baseStyle = MaterialTheme.typography.bodySmall,
                        )
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "Total General",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        AdaptiveAmountText(
                            text = "$${String.format(Locale.US, "%.2f", state.summary.total)}",
                            baseStyle = PosTextStyles.totalDisplay.copy(fontSize = 20.sp),
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (state.cart.isNotEmpty()) {
                            OutlinedButton(
                                onClick = onClearCart,
                                modifier = Modifier.weight(1f).height(44.dp),
                                shape = PosExtraShapes.InputRadius,
                                contentPadding = PaddingValues(horizontal = 6.dp),
                            ) {
                                Text("Vaciar", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }

                        PosGradientButton(
                            text = "Cobrar ($${String.format(Locale.US, "%.2f", state.summary.total)})",
                            onClick = onOpenPayment,
                            enabled = state.cart.isNotEmpty(),
                            shape = PosExtraShapes.InputRadius,
                            modifier = Modifier.weight(2f).height(44.dp),
                        )
                    }
                }
            }
        }
    }
}
}

/**
 * Layout Monocolumna con Pestañas para Teléfonos y Pantallas en Modo Vertical.
 */
@Composable
private fun PosTerminalPortraitLayout(
    state: PosUiState,
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onDepartmentSelected: (Int?) -> Unit,
    onAddToCart: (Product) -> Unit,
    onIncrement: (String) -> Unit,
    onDecrement: (String) -> Unit,
    onRemove: (String) -> Unit,
    onClearCart: () -> Unit,
    onOpenPayment: () -> Unit,
    onSelectClient: () -> Unit,
    onRemoveClient: () -> Unit,
    onChangeSeller: () -> Unit,
    onSelectBranch: (ClientBranch) -> Unit,
    onNavigateToCajas: () -> Unit,
    onRenovarCaja: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        // --- Barra Superior: Estado de Caja y Cliente ---
        PosHeaderBar(
            cajaName = state.activeCajaName,
            isCajaOpen = state.isCajaOpen,
            isDiaAnterior = state.isCajaDiaAnterior,
            fechaApertura = state.cajaFechaApertura,
            sucursalNombre = state.sucursalNombre,
            almacenNombre = state.almacenNombre,
            usuarioApertura = state.usuarioApertura,
            client = state.selectedClient,
            onClientClick = onSelectClient,
            onCajaClick = onNavigateToCajas,
            onRenovar = onRenovarCaja,
            isRenovando = state.isRenovandoCaja,
        )

        // --- Pestañas estilizadas ---
        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.primary,
            indicator = { tabPositions ->
                if (selectedTab < tabPositions.size) {
                    TabRowDefaults.SecondaryIndicator(
                        Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                        color = MaterialTheme.colorScheme.primary,
                        height = 3.dp,
                    )
                }
            },
        ) {
            Tab(
                selected = selectedTab == 0,
                onClick = { onTabSelected(0) },
                text = {
                    Text(
                        "Catálogo (${state.filteredProducts.size})",
                        fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Medium,
                    )
                },
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { onTabSelected(1) },
                text = {
                    BadgedBox(
                        badge = {
                            if (state.summary.itemCount > 0) {
                                Badge(containerColor = MaterialTheme.colorScheme.primary) {
                                    Text("${state.summary.itemCount}", color = PosPalette.FixedWhite)
                                }
                            }
                        },
                    ) {
                        Text(
                            "Carrito $${String.format(Locale.US, "%.2f", state.summary.total)}",
                            fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 8.dp),
                        )
                    }
                },
            )
        }

        // --- Contenido de la pestaña activa ---
        Box(modifier = Modifier.weight(1f)) {
            if (selectedTab == 0) {
                CatalogTab(
                    state = state,
                    onSearchQueryChange = onSearchQueryChange,
                    onDepartmentSelected = onDepartmentSelected,
                    onAddToCart = onAddToCart,
                    onViewCart = { onTabSelected(1) },
                )
            } else {
                CartTab(
                    state = state,
                    onIncrement = onIncrement,
                    onDecrement = onDecrement,
                    onRemove = onRemove,
                    onClearCart = onClearCart,
                    onOpenPayment = onOpenPayment,
                    onSelectClient = onSelectClient,
                    onRemoveClient = onRemoveClient,
                    onChangeSeller = onChangeSeller,
                    onSelectBranch = onSelectBranch,
                )
            }
        }
    }
}

@Composable
private fun PosHeaderBar(
    cajaName: String?,
    isCajaOpen: Boolean,
    isDiaAnterior: Boolean = false,
    fechaApertura: String? = null,
    sucursalNombre: String? = null,
    almacenNombre: String? = null,
    usuarioApertura: String? = null,
    client: Client?,
    onClientClick: () -> Unit,
    onCajaClick: () -> Unit,
    onRenovar: () -> Unit = {},
    isRenovando: Boolean = false,
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        border =
            BorderStroke(
                1.dp,
                if (isCajaOpen && isDiaAnterior) {
                    MaterialTheme.colorScheme.tertiary.copy(alpha = 0.4f)
                } else {
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                },
            ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Contenedor de estado de caja interactivo
            Row(
                modifier =
                    Modifier
                        .weight(1f)
                        .clip(PosExtraShapes.CardRadius)
                        .clickable { onCajaClick() }
                        .padding(vertical = 4.dp, horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                val badgeLabel = when {
                    !isCajaOpen -> "Caja Cerrada"
                    isDiaAnterior -> "${cajaName ?: "Caja"} (Día Anterior)"
                    else -> cajaName ?: "Caja"
                }
                val badgeTone = when {
                    !isCajaOpen -> PosVisualTone.Error
                    isDiaAnterior -> PosVisualTone.Warning
                    else -> PosVisualTone.Success
                }

                PosStatusBadge(
                    label = badgeLabel,
                    tone = badgeTone,
                    icon = if (isCajaOpen) (if (isDiaAnterior) Icons.Default.Warning else Icons.Default.LockOpen) else Icons.Default.Lock,
                )

                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    // Fila de metadatos de sesión: Fecha de apertura y Persona que abrió
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (!fechaApertura.isNullOrBlank()) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CalendarToday,
                                    contentDescription = null,
                                    modifier = Modifier.size(12.dp),
                                    tint = if (isDiaAnterior) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    text = fechaApertura,
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                                    color = if (isDiaAnterior) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }

                        if (!usuarioApertura.isNullOrBlank()) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Person,
                                    contentDescription = null,
                                    modifier = Modifier.size(12.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    text = "Por: $usuarioApertura",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }

                    // Fila de ubicación: Sucursal y Almacén
                    val branchText = sucursalNombre?.takeIf(String::isNotBlank) ?: "Sucursal Principal"
                    val locationParts = listOfNotNull(branchText, almacenNombre?.takeIf(String::isNotBlank))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Storefront,
                            contentDescription = null,
                            modifier = Modifier.size(12.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                        )
                        Text(
                            text = locationParts.joinToString(" • "),
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            // Derecha: Botón renovar si aplica + Selector rápido de cliente
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (isCajaOpen && isDiaAnterior) {
                    OutlinedButton(
                        onClick = onRenovar,
                        enabled = !isRenovando,
                        shape = PosExtraShapes.Pill,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.height(34.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.tertiary),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary),
                    ) {
                        if (isRenovando) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(12.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.tertiary,
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Renovando…", fontSize = 11.sp)
                        } else {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Renovar", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // Selector rápido de cliente
                Surface(
                    shape = PosExtraShapes.Pill,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.clickable { onClientClick() },
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = client?.name ?: "CONSUMIDOR FINAL",
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CatalogTab(
    state: PosUiState,
    onSearchQueryChange: (String) -> Unit,
    onDepartmentSelected: (Int?) -> Unit,
    onAddToCart: (Product) -> Unit,
    onViewCart: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Buscador con surfaceVariant
            OutlinedTextField(
                value = state.searchQuery,
                onValueChange = onSearchQueryChange,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text("Buscar producto...") },
                leadingIcon = {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = "Buscar",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                trailingIcon = {
                    if (state.searchQuery.isNotEmpty()) {
                        IconButton(onClick = { onSearchQueryChange("") }) {
                            Icon(Icons.Default.Clear, contentDescription = "Limpiar")
                        }
                    }
                },
                singleLine = true,
                shape = PosExtraShapes.InputRadius,
                colors =
                    OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    ),
            )

            // Chips de filtro por departamento
            if (state.departments.isNotEmpty()) {
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(
                        selected = state.selectedDepartmentId == null,
                        onClick = { onDepartmentSelected(null) },
                        label = { Text("Todos") },
                        colors =
                            FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = PosPalette.FixedWhite,
                            ),
                    )
                    state.departments.forEach { dept ->
                        FilterChip(
                            selected = state.selectedDepartmentId == dept.id,
                            onClick = { onDepartmentSelected(dept.id) },
                            label = { Text(dept.name) },
                            colors =
                                FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                                    selectedLabelColor = PosPalette.FixedWhite,
                                ),
                        )
                    }
                }
            }

            // Cuadrícula de productos
            if (state.isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            } else if (state.filteredProducts.isEmpty()) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    PosEmptyState(
                        icon = Icons.Default.ShoppingCart,
                        title = "Sin productos",
                        message = "No se encontraron productos disponibles para mostrar.",
                    )
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 150.dp),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 80.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(state.filteredProducts, key = { it.id }) { product ->
                        ProductGridItem(
                            product = product,
                            onAddToCart = { onAddToCart(product) },
                        )
                    }
                }
            }
        }

        // Botón flotante para ver carrito (en modo vertical)
        AnimatedVisibility(
            visible = state.summary.itemCount > 0,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier =
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp),
        ) {
            ExtendedFloatingActionButton(
                onClick = onViewCart,
                icon = { Icon(Icons.Default.ShoppingCart, contentDescription = null) },
                text = { Text("Ver Carrito (${state.summary.itemCount})") },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = PosPalette.FixedWhite,
            )
        }
    }
}

@Composable
private fun ProductGridItem(
    product: Product,
    onAddToCart: () -> Unit,
) {
    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(PosExtraShapes.CardRadius)
                .clickable { onAddToCart() },
        shape = PosExtraShapes.CardRadius,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Contenedor de Imagen o Icono
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(88.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                if (product.photoUrl.isNotBlank()) {
                    SubcomposeAsyncImage(
                        model =
                            ImageRequest.Builder(LocalContext.current)
                                .data(product.photoUrl)
                                .crossfade(true)
                                .build(),
                        contentDescription = product.description,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                        loading = {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center,
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                                )
                            }
                        },
                        error = {
                            Icon(
                                imageVector = Icons.Default.ShoppingCart,
                                contentDescription = null,
                                modifier = Modifier.size(36.dp),
                                tint = MaterialTheme.colorScheme.outline,
                            )
                        },
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.ShoppingCart,
                        contentDescription = null,
                        modifier = Modifier.size(36.dp),
                        tint = MaterialTheme.colorScheme.outline,
                    )
                }

                if (product.isExempt) {
                    Surface(
                        color = Color(0xFF15803D),
                        shape = RoundedCornerShape(6.dp),
                        modifier =
                            Modifier
                                .align(Alignment.TopEnd)
                                .padding(6.dp),
                    ) {
                        Text(
                            "EXENTO",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                            color = PosPalette.FixedWhite,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = product.description,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "Cód: ${product.code}",
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp),
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AdaptiveAmountText(
                    text = "$${String.format(Locale.US, "%.2f", product.mainPrice)}",
                    baseStyle = PosTextStyles.priceTileLarge.copy(fontSize = 16.sp),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                    options = AdaptiveAmountOptions(minFontSizeSp = 12f),
                )

                IconButton(
                    onClick = onAddToCart,
                    modifier =
                        Modifier
                            .size(34.dp)
                            .background(MaterialTheme.colorScheme.primary, CircleShape),
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Agregar una unidad",
                        tint = PosPalette.FixedWhite,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun CartTab(
    state: PosUiState,
    onIncrement: (String) -> Unit,
    onDecrement: (String) -> Unit,
    onRemove: (String) -> Unit,
    onClearCart: () -> Unit,
    onOpenPayment: () -> Unit,
    onSelectClient: () -> Unit,
    onRemoveClient: () -> Unit,
    onChangeSeller: () -> Unit,
    onSelectBranch: (ClientBranch) -> Unit,
) {
    if (state.cart.isEmpty()) {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(32.dp),
            contentAlignment = Alignment.Center,
        ) {
            PosEmptyState(
                icon = Icons.Default.ShoppingCart,
                title = "El carrito está vacío",
                message = "Selecciona productos en el catálogo para agregarlos a la venta.",
            )
        }
    } else {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            // Panel superior consolidado: Cliente + Vendedor
            CartClientVendorPanel(
                state = state,
                onSelectClient = onSelectClient,
                onRemoveClient = onRemoveClient,
                onChangeSeller = onChangeSeller,
                modifier = Modifier.padding(bottom = 8.dp),
            )

            // Selector de Sucursal del Cliente (cuando tiene sucursales disponibles)
            if (state.clientBranches.isNotEmpty()) {
                ClientSucursalSelectorCard(
                    sucursales = state.clientBranches,
                    selectedSucursal = state.selectedClientBranch,
                    isRequiredMissing = state.branchSelectionRequiredError,
                    onSelect = onSelectBranch,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }

            // Lista de renglones del carrito
            LazyColumn(
                modifier =
                    Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(state.cart, key = { it.product.id }) { item ->
                    CartItemRow(
                        item = item,
                        onIncrement = { onIncrement(item.product.id) },
                        onDecrement = { onDecrement(item.product.id) },
                        onRemove = { onRemove(item.product.id) },
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Resumen de Totales
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text("Subtotal", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        AdaptiveAmountText(
                            text = "$${String.format(Locale.US, "%.2f", state.summary.subtotal)}",
                            baseStyle = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text("IVA", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        AdaptiveAmountText(
                            text = "$${String.format(Locale.US, "%.2f", state.summary.tax)}",
                            baseStyle = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "Total General",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        AdaptiveAmountText(
                            text = "$${String.format(Locale.US, "%.2f", state.summary.total)}",
                            baseStyle = PosTextStyles.totalDisplay.copy(fontSize = 24.sp),
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        OutlinedButton(
                            onClick = onClearCart,
                            modifier = Modifier.weight(1f).height(48.dp),
                            shape = PosExtraShapes.InputRadius,
                        ) {
                            Text("Vaciar", fontWeight = FontWeight.SemiBold)
                        }

                        PosGradientButton(
                            text = "Cobrar ($${String.format(Locale.US, "%.2f", state.summary.total)})",
                            onClick = onOpenPayment,
                            shape = PosExtraShapes.InputRadius,
                            modifier = Modifier.weight(2f).height(48.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CartItemRow(
    item: CartItem,
    onIncrement: () -> Unit,
    onDecrement: () -> Unit,
    onRemove: () -> Unit,
) {
    Card(
        shape = PosExtraShapes.CardRadius,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.product.description,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "$${String.format(Locale.US, "%.2f", item.unitPriceWithTax)} c/u",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // Stepper de cantidad compacto de 30dp
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.70f),
                    modifier = Modifier.size(30.dp),
                ) {
                    IconButton(onClick = onDecrement, modifier = Modifier.fillMaxSize()) {
                        Icon(
                            Icons.Default.Remove,
                            contentDescription = "Disminuir",
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.size(34.dp, 30.dp),
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        Text(
                            "${item.quantity.toInt().coerceAtLeast(1)}",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold, fontSize = 13.sp),
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(30.dp),
                ) {
                    IconButton(onClick = onIncrement, modifier = Modifier.fillMaxSize()) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = "Aumentar",
                            tint = PosPalette.FixedWhite,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            AdaptiveAmountText(
                text = "$${String.format(Locale.US, "%.2f", item.totalWithTax)}",
                baseStyle = PosTextStyles.priceTileLarge.copy(fontSize = 14.sp),
                color = MaterialTheme.colorScheme.primary,
                options = AdaptiveAmountOptions(minFontSizeSp = 11f),
            )

            Spacer(modifier = Modifier.width(4.dp))

            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.40f),
                modifier = Modifier.size(26.dp),
            ) {
                IconButton(onClick = onRemove, modifier = Modifier.fillMaxSize()) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Eliminar",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun PaymentDialog(
    state: PosUiState,
    onReceivedAmountChange: (String) -> Unit,
    onSelectPaymentMethod: (FormaPagoDto) -> Unit,
    onConfirmPayment: () -> Unit,
    onDismiss: () -> Unit,
) {
    val isLandscape = isLandscape()

    AlertDialog(
        onDismissRequest = { if (!state.isProcessingSale) onDismiss() },
        shape = PosExtraShapes.DialogRadius,
        title = {
            Text(
                "Procesar Pago",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
        },
        text = {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
            ) {
                if (isLandscape) {
                    // Diseño de 2 Columnas para Modo Horizontal
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        // Columna Izquierda: Total y Formas de Pago
                        Column(modifier = Modifier.weight(1f)) {
                            Surface(
                                color = MaterialTheme.colorScheme.primaryContainer,
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                    Text(
                                        "Total a Pagar",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    )
                                    Text(
                                        "$${String.format(Locale.US, "%.2f", state.summary.total)}",
                                        style = PosTextStyles.totalDisplay.copy(fontSize = 24.sp),
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            Text(
                                "Forma de Pago:",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                state.paymentMethods.forEach { method ->
                                    val isSelected = state.selectedPaymentMethod?.idFormaPago == method.idFormaPago
                                    FilterChip(
                                        selected = isSelected,
                                        onClick = { onSelectPaymentMethod(method) },
                                        label = { Text(method.descripcion ?: method.codigo ?: "Pago", fontSize = 12.sp) },
                                        leadingIcon = {
                                            Icon(
                                                imageVector =
                                                    if (method.descripcion?.contains("Efectivo", ignoreCase = true) == true) {
                                                        Icons.Default.Payments
                                                    } else {
                                                        Icons.Default.CreditCard
                                                    },
                                                contentDescription = null,
                                                modifier = Modifier.size(14.dp),
                                            )
                                        },
                                        shape = RoundedCornerShape(8.dp),
                                        colors =
                                            FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                                selectedLabelColor = PosPalette.FixedWhite,
                                                selectedLeadingIconColor = PosPalette.FixedWhite,
                                            ),
                                    )
                                }
                            }
                        }

                        // Columna Derecha: Monto Recibido y Vuelto
                        Column(modifier = Modifier.weight(1f)) {
                            OutlinedTextField(
                                value = state.receivedAmountText,
                                onValueChange = onReceivedAmountChange,
                                label = { Text("Monto Recibido ($)", fontSize = 12.sp) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                modifier = Modifier.fillMaxWidth(),
                                shape = PosExtraShapes.InputRadius,
                                singleLine = true,
                                colors =
                                    OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                    ),
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            Surface(
                                shape = PosExtraShapes.InputRadius,
                                color = if (state.changeAmount > 0.0) ConfirmedContainer else MaterialTheme.colorScheme.surfaceVariant,
                                border =
                                    BorderStroke(
                                        1.dp,
                                        if (state.changeAmount > 0.0) ConfirmedContent.copy(alpha = 0.3f) else Color.Transparent,
                                    ),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        "Cambio / Vuelto:",
                                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                        color = if (state.changeAmount > 0.0) ConfirmedContent else MaterialTheme.colorScheme.onSurface,
                                    )
                                    Text(
                                        "$${String.format(Locale.US, "%.2f", state.changeAmount)}",
                                        style = PosTextStyles.priceTileLarge.copy(fontSize = 15.sp),
                                        color = if (state.changeAmount > 0.0) ConfirmedContent else MaterialTheme.colorScheme.onSurface,
                                    )
                                }
                            }

                            if (state.errorMessage != null) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Surface(
                                    color = MaterialTheme.colorScheme.errorContainer,
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text(
                                        text = state.errorMessage,
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                                        modifier = Modifier.padding(8.dp),
                                    )
                                }
                            }
                        }
                    }
                } else {
                    // Diseño Monocolumna para Modo Vertical
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Text(
                                    "Total a Pagar",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                )
                                Text(
                                    "$${String.format(Locale.US, "%.2f", state.summary.total)}",
                                    style = PosTextStyles.totalDisplay,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Text(
                            "Forma de Pago:",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            state.paymentMethods.forEach { method ->
                                val isSelected = state.selectedPaymentMethod?.idFormaPago == method.idFormaPago
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { onSelectPaymentMethod(method) },
                                    label = { Text(method.descripcion ?: method.codigo ?: "Pago") },
                                    leadingIcon = {
                                        Icon(
                                            imageVector =
                                                if (method.descripcion?.contains("Efectivo", ignoreCase = true) == true) {
                                                    Icons.Default.Payments
                                                } else {
                                                    Icons.Default.CreditCard
                                                },
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp),
                                        )
                                    },
                                    colors =
                                        FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                                            selectedLabelColor = PosPalette.FixedWhite,
                                            selectedLeadingIconColor = PosPalette.FixedWhite,
                                        ),
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        OutlinedTextField(
                            value = state.receivedAmountText,
                            onValueChange = onReceivedAmountChange,
                            label = { Text("Monto Recibido ($)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier.fillMaxWidth(),
                            shape = PosExtraShapes.InputRadius,
                            singleLine = true,
                            colors =
                                OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                ),
                        )

                        Spacer(modifier = Modifier.height(12.dp))
                        Surface(
                            shape = PosExtraShapes.InputRadius,
                            color = if (state.changeAmount > 0.0) ConfirmedContainer else MaterialTheme.colorScheme.surfaceVariant,
                            border =
                                BorderStroke(
                                    1.dp,
                                    if (state.changeAmount > 0.0) ConfirmedContent.copy(alpha = 0.3f) else Color.Transparent,
                                ),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    "Cambio / Vuelto:",
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (state.changeAmount > 0.0) ConfirmedContent else MaterialTheme.colorScheme.onSurface,
                                )
                                Text(
                                    "$${String.format(Locale.US, "%.2f", state.changeAmount)}",
                                    style = PosTextStyles.priceTileLarge,
                                    color = if (state.changeAmount > 0.0) ConfirmedContent else MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        }

                        if (state.errorMessage != null) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Surface(
                                color = MaterialTheme.colorScheme.errorContainer,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(
                                    text = state.errorMessage,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(10.dp),
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            PosGradientButton(
                text = "Confirmar y Facturar",
                onClick = onConfirmPayment,
                enabled = !state.isProcessingSale,
                loading = state.isProcessingSale,
                shape = PosExtraShapes.InputRadius,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        dismissButton = {
            if (!state.isProcessingSale) {
                OutlinedButton(
                    onClick = onDismiss,
                    shape = PosExtraShapes.InputRadius,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Cancelar")
                }
            }
        },
    )
}

@Composable
private fun CajaWarningDialog(
    onDismiss: () -> Unit,
    onGoToCajas: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = PosExtraShapes.DialogRadius,
        icon = {
            Icon(
                Icons.Default.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(40.dp),
            )
        },
        title = {
            Text(
                "Caja No Abierta",
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleLarge,
            )
        },
        text = {
            Text(
                "Para poder facturar y procesar pagos, debes tener una caja asignada y abierta en tu sesión. ¿Deseas ir al módulo de Cajas?",
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        confirmButton = {
            PosGradientButton(
                text = "Ir a Cajas",
                onClick = onGoToCajas,
                shape = PosExtraShapes.InputRadius,
            )
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss, shape = PosExtraShapes.InputRadius) {
                Text("Entendido")
            }
        },
    )
}

@Composable
private fun SaleSuccessDialog(
    completedInfo: CompletedSaleInfo?,
    fallbackInvoiceNumber: String,
    isPrinting: Boolean,
    printFeedback: String?,
    isPrintSuccess: Boolean,
    onPrint: () -> Unit,
    onDismiss: () -> Unit,
) {
    val invoiceNumber = completedInfo?.numeroFactura?.ifBlank { fallbackInvoiceNumber } ?: fallbackInvoiceNumber

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = PosExtraShapes.DialogRadius,
        icon = {
            Surface(
                shape = CircleShape,
                color = ConfirmedContainer,
                modifier = Modifier.size(64.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = null,
                        tint = ConfirmedContent,
                        modifier = Modifier.size(38.dp),
                    )
                }
            }
        },
        title = {
            Text(
                "¡Venta Procesada!",
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.primary,
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    "La factura se ha generado y procesado exitosamente en el ERP.",
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text(
                        "N° Factura: $invoiceNumber",
                        fontWeight = FontWeight.ExtraBold,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp),
                    )
                }

                val clientName = completedInfo?.clientName
                if (!clientName.isNullOrBlank()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(
                            Icons.Default.Person,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp),
                        )
                        Text(
                            clientName,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                if (completedInfo != null) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        ),
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    "Total Facturado",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    "$ ${String.format(Locale.US, "%.2f", completedInfo.total)}",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }

                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(
                                    "Recibido (${completedInfo.paymentMethodName})",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    "$ ${String.format(Locale.US, "%.2f", completedInfo.receivedAmount)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                            }

                            val hasChange = completedInfo.changeAmount > 0.009
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp),
                                color = if (hasChange) ConfirmedContainer else MaterialTheme.colorScheme.surface,
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        "CAMBIO / VUELTO",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = if (hasChange) ConfirmedContent else MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Text(
                                        "$ ${String.format(Locale.US, "%.2f", completedInfo.changeAmount)}",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = if (hasChange) ConfirmedContent else MaterialTheme.colorScheme.onSurface,
                                    )
                                }
                            }
                        }
                    }
                }

                if (printFeedback != null) {
                    PosFeedbackCard(
                        title = if (isPrintSuccess) "Impresión de Ticket" else "Aviso de Impresión",
                        message = printFeedback,
                        tone = if (isPrintSuccess) PosVisualTone.Success else PosVisualTone.Warning,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
        confirmButton = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = onPrint,
                    enabled = !isPrinting,
                    modifier = Modifier.fillMaxWidth(),
                    shape = PosExtraShapes.InputRadius,
                ) {
                    if (isPrinting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Imprimiendo ticket...")
                    } else {
                        Icon(
                            Icons.Default.Print,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            if (printFeedback != null) "Reimprimir Ticket" else "Imprimir Ticket",
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }

                PosGradientButton(
                    text = "Nueva Venta",
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    shape = PosExtraShapes.InputRadius,
                )
            }
        },
    )
}


@Composable
private fun ClientSelectionDialog(
    currentClient: Client?,
    onSearch: suspend (String) -> List<Client>,
    onClientSelected: (Client) -> Unit,
    onDismiss: () -> Unit,
) {
    var searchQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<Client>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }

    LaunchedEffect(searchQuery) {
        isSearching = true
        searchResults = onSearch(searchQuery)
        isSearching = false
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = PosExtraShapes.DialogRadius,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Seleccionar Cliente",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Clear, contentDescription = "Cerrar")
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 480.dp),
            ) {
                // Buscador
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Buscar por nombre, RUC, cédula o código...") },
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = "Limpiar")
                            }
                        }
                    },
                    singleLine = true,
                    shape = PosExtraShapes.InputRadius,
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Opción Rápida: Consumidor Final (CF)
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (currentClient?.code == "CF" || currentClient == null) {
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                        } else {
                            MaterialTheme.colorScheme.surface
                        },
                    ),
                    shape = PosExtraShapes.CardRadius,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            onClientSelected(
                                Client(id = "0", code = "CF", name = "CONSUMIDOR FINAL", identification = "CF")
                            )
                        },
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                            modifier = Modifier.size(36.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.Person,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "CONSUMIDOR FINAL",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                "Venta al público general (CF)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (currentClient?.code == "CF" || currentClient == null) {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                if (isSearching) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(32.dp),
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                } else if (searchResults.isEmpty() && searchQuery.isNotBlank()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "No se encontraron clientes para '$searchQuery'",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(searchResults.filter { it.code != "CF" && it.id != "0" }, key = { it.id.ifBlank { it.code } }) { client ->
                            val isSelected = currentClient?.id == client.id || (currentClient?.code == client.code && client.code.isNotBlank())
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isSelected) {
                                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                                    } else {
                                        MaterialTheme.colorScheme.surface
                                    },
                                ),
                                shape = PosExtraShapes.CardRadius,
                                border = BorderStroke(
                                    1.dp,
                                    if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onClientSelected(client) },
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Surface(
                                        shape = CircleShape,
                                        color = MaterialTheme.colorScheme.secondaryContainer,
                                        modifier = Modifier.size(34.dp),
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Icon(
                                                Icons.Default.Person,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.secondary,
                                                modifier = Modifier.size(18.dp),
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            client.fullName.ifBlank { client.name },
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        ) {
                                            if (client.identification.isNotBlank()) {
                                                Text(
                                                    "ID: ${client.identification}",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                )
                                            }
                                            if (client.code.isNotBlank()) {
                                                Text(
                                                    "Cód: ${client.code}",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                )
                                            }
                                        }
                                        if (client.address.isNotBlank()) {
                                            Text(
                                                client.address,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        }
                                    }
                                    if (isSelected) {
                                        Icon(
                                            Icons.Default.Check,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cerrar")
            }
        },
    )
}

@Composable
internal fun CartClientVendorPanel(
    state: PosUiState,
    onSelectClient: () -> Unit,
    onRemoveClient: () -> Unit,
    onChangeSeller: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = PosExtraShapes.CardRadius,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Sección Cliente
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onSelectClient() }
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val client = state.selectedClient
                val isCustomClient = client != null && client.code != "CF" && client.id != "0"

                Surface(
                    shape = CircleShape,
                    color = if (isCustomClient) {
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    },
                    modifier = Modifier.size(28.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.Person,
                            contentDescription = null,
                            tint = if (isCustomClient) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
                Spacer(modifier = Modifier.width(6.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Cliente",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                    Text(
                        text = if (isCustomClient) client!!.fullName.ifBlank { client.name } else "CONSUMIDOR FINAL",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, fontSize = 12.sp),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (isCustomClient) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.40f),
                        modifier = Modifier.size(24.dp),
                    ) {
                        IconButton(onClick = onRemoveClient, modifier = Modifier.fillMaxSize()) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "Quitar cliente",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(13.dp),
                            )
                        }
                    }
                } else {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.50f),
                        modifier = Modifier.size(22.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Default.Add,
                                contentDescription = "Asignar cliente",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(14.dp),
                            )
                        }
                    }
                }
            }

            // Divisor vertical
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(26.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.50f)),
            )

            // Sección Vendedor
            val canChange = state.availableSellers.isNotEmpty()
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(enabled = canChange) { onChangeSeller() }
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.60f),
                    modifier = Modifier.size(28.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.Storefront,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(15.dp),
                        )
                    }
                }
                Spacer(modifier = Modifier.width(6.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Vendedor",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                    Text(
                        text = state.selectedSeller?.nombre ?: "Sin vendedor",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold, fontSize = 12.sp),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (canChange) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.50f),
                        modifier = Modifier.size(22.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Default.Refresh,
                                contentDescription = "Cambiar vendedor",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(13.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ClientSucursalSelectorCard(
    sucursales: List<ClientBranch>,
    selectedSucursal: ClientBranch?,
    isRequiredMissing: Boolean,
    onSelect: (ClientBranch) -> Unit,
    modifier: Modifier = Modifier,
) {
    val hasMultiple = sucursales.size > 1

    Card(
        shape = PosExtraShapes.CardRadius,
        colors = CardDefaults.cardColors(
            containerColor = if (isRequiredMissing) {
                MaterialTheme.colorScheme.errorContainer
            } else {
                MaterialTheme.colorScheme.surface
            },
        ),
        border = BorderStroke(
            1.dp,
            if (isRequiredMissing) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outlineVariant,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Text(
                text = if (hasMultiple) "Sucursal del cliente" else "Sucursal del cliente asignada",
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (isRequiredMissing) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(6.dp))

            if (hasMultiple) {
                var expanded by remember { mutableStateOf(false) }

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
                        placeholder = { Text("Seleccionar sucursal...") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        shape = PosExtraShapes.InputRadius,
                        modifier = Modifier
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
                                    onSelect(sucursal)
                                    expanded = false
                                },
                            )
                        }
                    }
                }
                if (isRequiredMissing) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Este cliente tiene varias sucursales. Selecciona una para continuar.",
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            } else {
                Text(
                    text = selectedSucursal?.nombreSucursal ?: sucursales.firstOrNull()?.nombreSucursal.orEmpty(),
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = if (isRequiredMissing) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurface,
                )
                selectedSucursal?.direccion?.takeIf { it.isNotBlank() }?.let {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(it, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
