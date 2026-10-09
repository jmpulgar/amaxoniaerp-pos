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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Inventory2
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
import com.amaxonia.erp.ui.util.forceShowKeyboardOnTouch
import com.amaxonia.erp.ui.components.PosStatusBadge
import com.amaxonia.erp.ui.components.PosVisualTone
import com.amaxonia.erp.ui.components.isLandscape
import com.amaxonia.erp.ui.theme.ConfirmedContainer
import com.amaxonia.erp.ui.theme.ConfirmedContent
import com.amaxonia.erp.ui.theme.PosExtraShapes
import com.amaxonia.erp.ui.theme.PosPalette
import com.amaxonia.erp.ui.theme.PosTextStyles
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.ModalBottomSheet
import com.amaxonia.erp.domain.model.ItemCarrito
import com.amaxonia.erp.domain.model.Promocion
import com.amaxonia.erp.ui.pos.components.PosCartWebTable
import com.amaxonia.erp.ui.pos.components.PosInvoiceFormControls
import com.amaxonia.erp.ui.pos.components.PosProductCatalogDrawer
import com.amaxonia.erp.ui.pos.components.PosTopControlsSection
import com.amaxonia.erp.ui.pos.components.PosTotalsActionCards
import com.amaxonia.erp.ui.pos.components.PosWebFooterBar
import com.amaxonia.erp.ui.pos.components.PosWebHeaderBar
import com.amaxonia.erp.ui.pos.components.ProductSelectionDialog
import com.amaxonia.erp.ui.pos.components.ClientSelectionDialog
import com.amaxonia.erp.ui.pos.components.SellerSelectionDialog
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.amaxonia.erp.data.remote.dto.DepartmentDto
import com.amaxonia.erp.ui.theme.FlowBrandBlue
import com.amaxonia.erp.ui.theme.FlowBrandGradient
import com.amaxonia.erp.ui.theme.FlowHeaderGradient
import com.amaxonia.erp.ui.theme.FlowTableBorder
import java.util.Locale

@Composable
fun PosTerminalScreen(
    viewModel: PosTerminalViewModel,
    onNavigateToCajas: () -> Unit = {},
    onOpenDrawer: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsState()
    var selectedTab by remember { mutableIntStateOf(0) } // 0: Catálogo, 1: Carrito
    val isLandscape = isLandscape()

    var editTarget by remember { mutableStateOf<CartEditTarget?>(null) }
    var editingItem by remember { mutableStateOf<CartItem?>(null) }
    var editValueText by remember { mutableStateOf("") }

    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
    ) {
        if (isLandscape) {
            // Diseño Master-Detail 1:1 Web POS para Modo Horizontal (Landscape)
            PosTerminalLandscapeLayout(
                state = state,
                onSearchQueryChange = viewModel::onSearchQueryChange,
                onDepartmentSelected = viewModel::onDepartmentSelected,
                onAddToCart = viewModel::addToCart,
                onQuantityPicker = viewModel::openQuantityPicker,
                onIncrement = viewModel::incrementQuantity,
                onDecrement = viewModel::decrementQuantity,
                onRemove = viewModel::removeFromCart,
                onEditQuantity = { item ->
                    editingItem = item
                    editTarget = CartEditTarget.QUANTITY
                    editValueText = if (item.quantity % 1.0 == 0.0) String.format(Locale.US, "%.0f", item.quantity) else String.format(Locale.US, "%.2f", item.quantity)
                },
                onEditPrice = { item ->
                    editingItem = item
                    editTarget = CartEditTarget.PRICE
                    editValueText = String.format(Locale.US, "%.2f", item.unitPriceWithTax)
                },
                onEditDiscount = { item ->
                    editingItem = item
                    editTarget = CartEditTarget.DISCOUNT
                    editValueText = if (item.discountPercent > 0.0) String.format(Locale.US, "%.0f", item.discountPercent) else ""
                },
                onPriceLevelChange = viewModel::updateItemPriceLevel,
                onUpdatePromotionQuantity = viewModel::updatePromotionQuantity,
                onRemovePromotion = viewModel::removePromotion,
                onClearCart = viewModel::clearCart,
                onOpenPayment = viewModel::openPaymentDialog,
                onSelectClient = { viewModel.openClientDialog() },
                onRemoveClient = { viewModel.removeSelectedClient() },
                onChangeSeller = { viewModel.openSellerSheet() },
                onSearchSellerByCode = viewModel::searchSellerByCode,
                onOpenCustomClientNameDialog = viewModel::openCustomClientNameDialog,
                onOpenGlobalDiscountDialog = viewModel::openGlobalDiscountDialog,
                onSelectBranch = viewModel::selectClientBranch,
                onNavigateToCajas = onNavigateToCajas,
                onRenovarCaja = viewModel::renovarCajaDiaAnterior,
                onToggleCatalog = viewModel::toggleCatalogDrawer,
                onCloseCatalog = { viewModel.setCatalogDrawerOpen(false) },
                onAddProductByCode = { code -> viewModel.addProductByCode(code) },
                onOpenProductDialog = viewModel::openProductDialog,
                onObservationChange = viewModel::onObservationChange,
                onDocumentTypeSelected = viewModel::onDocumentTypeSelected,
                onPrintReceipt = { viewModel.printReceipt() },
                onOpenDrawer = onOpenDrawer,
                onNextPage = viewModel::onCatalogNextPage,
                onPrevPage = viewModel::onCatalogPrevPage,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            // Diseño Adaptado Web POS para Modo Vertical (Portrait)
            PosTerminalPortraitLayout(
                state = state,
                selectedTab = selectedTab,
                onTabSelected = { selectedTab = it },
                onSearchQueryChange = viewModel::onSearchQueryChange,
                onDepartmentSelected = viewModel::onDepartmentSelected,
                onAddToCart = viewModel::addToCart,
                onQuantityPicker = viewModel::openQuantityPicker,
                onIncrement = viewModel::incrementQuantity,
                onDecrement = viewModel::decrementQuantity,
                onRemove = viewModel::removeFromCart,
                onEditQuantity = { item ->
                    editingItem = item
                    editTarget = CartEditTarget.QUANTITY
                    editValueText = if (item.quantity % 1.0 == 0.0) String.format(Locale.US, "%.0f", item.quantity) else String.format(Locale.US, "%.2f", item.quantity)
                },
                onEditPrice = { item ->
                    editingItem = item
                    editTarget = CartEditTarget.PRICE
                    editValueText = String.format(Locale.US, "%.2f", item.unitPriceWithTax)
                },
                onEditDiscount = { item ->
                    editingItem = item
                    editTarget = CartEditTarget.DISCOUNT
                    editValueText = if (item.discountPercent > 0.0) String.format(Locale.US, "%.0f", item.discountPercent) else ""
                },
                onPriceLevelChange = viewModel::updateItemPriceLevel,
                onUpdatePromotionQuantity = viewModel::updatePromotionQuantity,
                onRemovePromotion = viewModel::removePromotion,
                onClearCart = viewModel::clearCart,
                onOpenPayment = viewModel::openPaymentDialog,
                onSelectClient = { viewModel.openClientDialog() },
                onRemoveClient = { viewModel.removeSelectedClient() },
                onChangeSeller = { viewModel.openSellerSheet() },
                onSearchSellerByCode = viewModel::searchSellerByCode,
                onOpenCustomClientNameDialog = viewModel::openCustomClientNameDialog,
                onOpenGlobalDiscountDialog = viewModel::openGlobalDiscountDialog,
                onSelectBranch = viewModel::selectClientBranch,
                onNavigateToCajas = onNavigateToCajas,
                onRenovarCaja = viewModel::renovarCajaDiaAnterior,
                onToggleCatalog = viewModel::toggleCatalogDrawer,
                onCloseCatalog = { viewModel.setCatalogDrawerOpen(false) },
                onAddProductByCode = { code -> viewModel.addProductByCode(code) },
                onOpenProductDialog = viewModel::openProductDialog,
                onObservationChange = viewModel::onObservationChange,
                onDocumentTypeSelected = viewModel::onDocumentTypeSelected,
                onPrintReceipt = { viewModel.printReceipt() },
                onOpenDrawer = onOpenDrawer,
                onNextPage = viewModel::onCatalogNextPage,
                onPrevPage = viewModel::onCatalogPrevPage,
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
            onSetPaymentMethodAmount = viewModel::setPaymentMethodAmount,
            onClearPayments = viewModel::clearPayments,
            onSelectActiveInput = viewModel::setActivePaymentInputMethod,
            onPaymentMethodBadgeClick = viewModel::onPaymentMethodBadgeClick,
            onPaymentMethodTextChange = viewModel::onPaymentMethodTextChange,
            onClearSinglePaymentMethod = viewModel::onClearSinglePaymentMethod,
            onKeypadInput = viewModel::onKeypadInput,
            onToggleCashDenominations = viewModel::toggleCashDenominations,
            onAddCashDenomination = viewModel::onAddCashDenomination,
            onConfirmPayment = viewModel::processSale,
            onDismiss = viewModel::dismissPaymentDialog,
        )
    }

    if (state.showCustomClientNameDialog) {
        CustomClientNameDialog(
            initialName = state.customClientName,
            onConfirm = viewModel::setCustomClientName,
            onDismiss = viewModel::dismissCustomClientNameDialog,
        )
    }

    if (state.showGlobalDiscountDialog) {
        GlobalDiscountDialog(
            currentPercent = state.globalDiscountPercent,
            onConfirm = viewModel::setGlobalDiscount,
            onDismiss = viewModel::dismissGlobalDiscountDialog,
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

    if (state.showProductDialog) {
        ProductSelectionDialog(
            initialQuery = state.productDialogQuery,
            products = state.products,
            departments = state.departments,
            isLoading = state.isLoading,
            onAddToCart = { product ->
                viewModel.addToCart(product)
                viewModel.dismissProductDialog()
            },
            onDismiss = viewModel::dismissProductDialog,
        )
    }

    if (state.showSellerSheet) {
        SellerSelectionDialog(
            sellers = state.availableSellers,
            selectedSellerId = state.selectedSeller?.id,
            onSelect = viewModel::selectSeller,
            onDismiss = viewModel::dismissSellerSheet,
        )
    }

    state.quantityPickerProduct?.let { product ->
        ProductQuantitySheet(
            product = product,
            onConfirm = { qty -> viewModel.confirmProductQuantity(product, qty.toDouble()) },
            onDismiss = viewModel::dismissQuantityPicker,
        )
    }

    if (state.showPromotionChoice && state.pendingPromotionProduct != null) {
        PromotionChoiceSheet(
            product = state.pendingPromotionProduct!!,
            promotions = state.promotionOptions,
            onAddIndividual = { qty -> viewModel.addIndividualFromPromotionChoice(qty.toDouble()) },
            onAddPromotion = { promo, times -> viewModel.addPromotionToCart(promo, times) },
            onDismiss = viewModel::dismissPromotionChoice,
        )
    }

    if (editTarget != null && editingItem != null) {
        EditItemValueDialog(
            target = editTarget!!,
            item = editingItem!!,
            value = editValueText,
            onValueChange = { editValueText = it },
            onConfirm = { parsed ->
                val target = editTarget
                val item = editingItem
                if (target != null && item != null) {
                    when (target) {
                        CartEditTarget.QUANTITY -> viewModel.setItemQuantity(item.product.id, parsed)
                        CartEditTarget.PRICE -> viewModel.updateItemPrice(item.product.id, parsed)
                        CartEditTarget.DISCOUNT -> viewModel.updateItemDiscount(item.product.id, parsed)
                    }
                }
                editTarget = null
                editingItem = null
            },
            onDismiss = {
                editTarget = null
                editingItem = null
            },
        )
    }
}

/**
 * Layout 1:1 Web POS de Alto Rendimiento para Terminal POS en Modo Horizontal.
 * Cabecera Web azul con correlativo y herramientas rápidas.
 * Barra de controles superiores (Cliente, Vendedor, Código Barra, Observación, Descuento y Cobro directo).
 * Tabla contable oficial Web POS a la izquierda y Catálogo de Productos en Cajón lateral derecho deslizable.
 * Footer inferior Web con estados de caja, vendedor y selector de tipo de documento (Factura, Pre-Orden, etc.).
 */
@Composable
private fun PosTerminalLandscapeLayout(
    state: PosUiState,
    onSearchQueryChange: (String) -> Unit,
    onDepartmentSelected: (Int?) -> Unit,
    onAddToCart: (Product) -> Unit,
    onQuantityPicker: (Product) -> Unit,
    onIncrement: (String) -> Unit,
    onDecrement: (String) -> Unit,
    onRemove: (String) -> Unit,
    onEditPrice: (CartItem) -> Unit,
    onEditDiscount: (CartItem) -> Unit,
    onEditQuantity: (CartItem) -> Unit = {},
    onPriceLevelChange: (String, String) -> Unit,
    onUpdatePromotionQuantity: (String, Int) -> Unit,
    onRemovePromotion: (String) -> Unit,
    onClearCart: () -> Unit,
    onOpenPayment: () -> Unit,
    onSelectClient: () -> Unit,
    onRemoveClient: () -> Unit,
    onChangeSeller: () -> Unit,
    onSearchSellerByCode: (String) -> Unit = {},
    onOpenCustomClientNameDialog: () -> Unit = {},
    onOpenGlobalDiscountDialog: () -> Unit = {},
    onSelectBranch: (ClientBranch) -> Unit,
    onNavigateToCajas: () -> Unit,
    onRenovarCaja: () -> Unit = {},
    onToggleCatalog: () -> Unit,
    onCloseCatalog: () -> Unit,
    onAddProductByCode: (String) -> Unit,
    onOpenProductDialog: (String) -> Unit = {},
    onObservationChange: (String) -> Unit,
    onDocumentTypeSelected: (String) -> Unit,
    onPrintReceipt: () -> Unit,
    onOpenDrawer: () -> Unit = {},
    onNextPage: () -> Unit = {},
    onPrevPage: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFFF1F5F9)),
    ) {
        // Cabecera Web azul Flow ERP (44dp compacta)
        PosWebHeaderBar(
            correlativo = state.completedSaleInvoice ?: "#001-00112",
            cartItemCount = state.summary.itemCount,
            isCatalogOpen = state.isCatalogDrawerOpen,
            onOpenDrawer = onOpenDrawer,
            onClearCart = onClearCart,
            onSaveDraft = { /* Guardar borrador */ },
            onSearchInvoices = { /* Buscar documento */ },
            onPrintReceipt = onPrintReceipt,
            onToggleCatalog = onToggleCatalog,
        )

        // Área Central de Trabajo: 2 Columnas Maestras 1:1 Web POS
        Row(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Columna Izquierda: Facturación y Tabla contable
            Column(
                modifier = Modifier
                    .weight(1.25f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                PosInvoiceFormControls(
                    client = state.selectedClient,
                    seller = state.selectedSeller,
                    observationText = state.observationText,
                    customClientName = state.customClientName,
                    onSelectClient = onSelectClient,
                    onSelectSeller = onChangeSeller,
                    onSearchSellerByCode = onSearchSellerByCode,
                    onOpenCustomClientNameDialog = onOpenCustomClientNameDialog,
                    onAddProductByCode = onAddProductByCode,
                    onOpenProductDialog = onOpenProductDialog,
                    onObservationChange = onObservationChange,
                )

                if (state.clientBranches.isNotEmpty()) {
                    ClientSucursalSelectorCard(
                        sucursales = state.clientBranches,
                        selectedSucursal = state.selectedClientBranch,
                        isRequiredMissing = state.branchSelectionRequiredError,
                        onSelect = onSelectBranch,
                    )
                }

                PosCartWebTable(
                    cart = state.cart,
                    summary = state.summary,
                    branchName = state.sucursalNombre.orEmpty().ifEmpty { "Principal" },
                    onIncrement = onIncrement,
                    onDecrement = onDecrement,
                    onRemove = onRemove,
                    onEditPrice = onEditPrice,
                    onEditDiscount = onEditDiscount,
                    onEditQuantity = onEditQuantity,
                    onPriceLevelChange = onPriceLevelChange,
                    modifier = Modifier.weight(1f),
                )
            }

            // Columna Derecha: Tarjetas de Descuento / Pagar + Catálogo de Productos Web (Siempre visible)
            Column(
                modifier = Modifier
                    .weight(0.95f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                PosTotalsActionCards(
                    summary = state.summary,
                    globalDiscountPercent = state.globalDiscountPercent,
                    onOpenPayment = onOpenPayment,
                    onOpenGlobalDiscountDialog = onOpenGlobalDiscountDialog,
                )

                PosProductCatalogDrawer(
                    isOpen = true,
                    products = state.filteredProducts,
                    departments = state.departments,
                    selectedDepartmentId = state.selectedDepartmentId,
                    searchQuery = state.searchQuery,
                    isLoading = state.isLoading || state.isCatalogLoading,
                    currentPage = state.catalogCurrentPage,
                    totalPages = state.catalogTotalPages,
                    onNextPage = onNextPage,
                    onPrevPage = onPrevPage,
                    onSearchQueryChange = onSearchQueryChange,
                    onDepartmentSelected = onDepartmentSelected,
                    onAddToCart = onAddToCart,
                    onOpenOptions = onQuantityPicker,
                    onClose = {},
                    modifier = Modifier.weight(1f),
                )
            }
        }

        // Barra Inferior de Estado y Pestañas de Documentos (32dp compacta)
        PosWebFooterBar(
            isOnline = true,
            cajaName = state.activeCajaName,
            sellerName = state.selectedSeller?.nombre,
            sucursalName = state.sucursalNombre,
            activeDocumentType = state.activeDocumentType,
            onDocumentTypeSelected = onDocumentTypeSelected,
            onNavigateToCajas = onNavigateToCajas,
            onChangeSeller = onChangeSeller,
        )
    }
}

/**
 * Layout Adaptado Web POS para Pantallas en Modo Vertical (Teléfonos / Portrait).
 */
@Composable
private fun PosTerminalPortraitLayout(
    state: PosUiState,
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onDepartmentSelected: (Int?) -> Unit,
    onAddToCart: (Product) -> Unit,
    onQuantityPicker: (Product) -> Unit,
    onIncrement: (String) -> Unit,
    onDecrement: (String) -> Unit,
    onRemove: (String) -> Unit,
    onEditPrice: (CartItem) -> Unit,
    onEditDiscount: (CartItem) -> Unit,
    onEditQuantity: (CartItem) -> Unit = {},
    onPriceLevelChange: (String, String) -> Unit,
    onUpdatePromotionQuantity: (String, Int) -> Unit,
    onRemovePromotion: (String) -> Unit,
    onClearCart: () -> Unit,
    onOpenPayment: () -> Unit,
    onSelectClient: () -> Unit,
    onRemoveClient: () -> Unit,
    onChangeSeller: () -> Unit,
    onSearchSellerByCode: (String) -> Unit = {},
    onOpenCustomClientNameDialog: () -> Unit = {},
    onOpenGlobalDiscountDialog: () -> Unit = {},
    onSelectBranch: (ClientBranch) -> Unit,
    onNavigateToCajas: () -> Unit,
    onRenovarCaja: () -> Unit = {},
    onToggleCatalog: () -> Unit,
    onCloseCatalog: () -> Unit,
    onAddProductByCode: (String) -> Unit,
    onOpenProductDialog: (String) -> Unit = {},
    onObservationChange: (String) -> Unit,
    onDocumentTypeSelected: (String) -> Unit,
    onPrintReceipt: () -> Unit,
    onOpenDrawer: () -> Unit = {},
    onNextPage: () -> Unit = {},
    onPrevPage: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFFF1F5F9)),
    ) {
        // Cabecera Web azul Flow ERP
        PosWebHeaderBar(
            correlativo = state.completedSaleInvoice ?: "#001-00112",
            cartItemCount = state.summary.itemCount,
            isCatalogOpen = selectedTab == 0,
            onOpenDrawer = onOpenDrawer,
            onClearCart = onClearCart,
            onSaveDraft = { },
            onSearchInvoices = { },
            onPrintReceipt = onPrintReceipt,
            onToggleCatalog = {
                onTabSelected(if (selectedTab == 0) 1 else 0)
            },
        )

        // Pestañas estilizadas: Catálogo (0) vs Factura (1)
        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = Color.White,
            contentColor = FlowBrandBlue,
            indicator = { tabPositions ->
                if (selectedTab < tabPositions.size) {
                    TabRowDefaults.SecondaryIndicator(
                        Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                        color = FlowBrandBlue,
                        height = 2.5.dp,
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
                        fontSize = 12.sp,
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
                                Badge(containerColor = FlowBrandBlue) {
                                    Text("${state.summary.itemCount}", color = PosPalette.FixedWhite, fontSize = 10.sp)
                                }
                            }
                        },
                    ) {
                        Text(
                            "Factura ($${String.format(Locale.US, "%.2f", state.summary.total)})",
                            fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(horizontal = 6.dp),
                        )
                    }
                },
            )
        }

        // Contenido según pestaña seleccionada
        Box(modifier = Modifier.weight(1f)) {
            if (selectedTab == 0) {
                // Catálogo compacto Web
                Box(modifier = Modifier.fillMaxSize()) {
                    PosProductCatalogDrawer(
                        isOpen = true,
                        products = state.filteredProducts,
                        departments = state.departments,
                        selectedDepartmentId = state.selectedDepartmentId,
                        searchQuery = state.searchQuery,
                        isLoading = state.isLoading || state.isCatalogLoading,
                        currentPage = state.catalogCurrentPage,
                        totalPages = state.catalogTotalPages,
                        onNextPage = onNextPage,
                        onPrevPage = onPrevPage,
                        onSearchQueryChange = onSearchQueryChange,
                        onDepartmentSelected = onDepartmentSelected,
                        onAddToCart = onAddToCart,
                        onOpenOptions = onQuantityPicker,
                        onClose = { onTabSelected(1) },
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(6.dp),
                    )

                    // Botón flotante para ver factura si hay items en el carrito
                    if (state.summary.itemCount > 0) {
                        ExtendedFloatingActionButton(
                            onClick = { onTabSelected(1) },
                            icon = {
                                BadgedBox(
                                    badge = {
                                        Badge(containerColor = MaterialTheme.colorScheme.error) {
                                            Text("${state.summary.itemCount}", fontSize = 10.sp)
                                        }
                                    },
                                ) {
                                    Icon(Icons.Default.ShoppingCart, contentDescription = "Factura")
                                }
                            },
                            text = { Text("Ver Factura ($${String.format(Locale.US, "%.2f", state.summary.total)})") },
                            containerColor = FlowBrandBlue,
                            contentColor = PosPalette.FixedWhite,
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(16.dp),
                        )
                    }
                }
            } else {
                // Factura completa: Totales + Controles + Tabla contable
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    PosTotalsActionCards(
                        summary = state.summary,
                        globalDiscountPercent = state.globalDiscountPercent,
                        onOpenPayment = onOpenPayment,
                        onOpenGlobalDiscountDialog = onOpenGlobalDiscountDialog,
                    )

                    PosInvoiceFormControls(
                        client = state.selectedClient,
                        seller = state.selectedSeller,
                        observationText = state.observationText,
                        customClientName = state.customClientName,
                        onSelectClient = onSelectClient,
                        onSelectSeller = onChangeSeller,
                        onSearchSellerByCode = onSearchSellerByCode,
                        onOpenCustomClientNameDialog = onOpenCustomClientNameDialog,
                        onAddProductByCode = onAddProductByCode,
                        onOpenProductDialog = onOpenProductDialog,
                        onObservationChange = onObservationChange,
                    )

                    if (state.clientBranches.isNotEmpty()) {
                        ClientSucursalSelectorCard(
                            sucursales = state.clientBranches,
                            selectedSucursal = state.selectedClientBranch,
                            isRequiredMissing = state.branchSelectionRequiredError,
                            onSelect = onSelectBranch,
                        )
                    }

                    PosCartWebTable(
                        cart = state.cart,
                        summary = state.summary,
                        branchName = state.sucursalNombre.orEmpty().ifEmpty { "Principal" },
                        onIncrement = onIncrement,
                        onDecrement = onDecrement,
                        onRemove = onRemove,
                        onEditPrice = onEditPrice,
                        onEditDiscount = onEditDiscount,
                        onEditQuantity = onEditQuantity,
                        onPriceLevelChange = onPriceLevelChange,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        // Footer Web
        PosWebFooterBar(
            isOnline = true,
            cajaName = state.activeCajaName,
            sellerName = state.selectedSeller?.nombre,
            sucursalName = state.sucursalNombre,
            activeDocumentType = state.activeDocumentType,
            onDocumentTypeSelected = onDocumentTypeSelected,
            onNavigateToCajas = onNavigateToCajas,
            onChangeSeller = onChangeSeller,
        )
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
                    isDiaAnterior -> "${cajaName ?: "Caja"} (Vencida)"
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
                                    text = if (isDiaAnterior) "$fechaApertura • Jornada vencida" else fechaApertura,
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
    onQuantityPicker: (Product) -> Unit,
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
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .forceShowKeyboardOnTouch(),
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
                            onQuantityClick = { onQuantityPicker(product) },
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
    onQuantityClick: () -> Unit,
) {
    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(240.dp),
        shape = PosExtraShapes.CardRadius,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(modifier = Modifier.padding(12.dp).fillMaxSize()) {
            // Contenedor de Imagen o Icono flexible (weight 1f)
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                if (product.photoUrl.isNotBlank()) {
                    AsyncImage(
                        model =
                            ImageRequest.Builder(LocalContext.current)
                                .data(product.photoUrl)
                                .crossfade(true)
                                .build(),
                        contentDescription = product.description,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit,
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

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = product.description,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (product.code.isNotBlank()) {
                Text(
                    text = "Ref: ${product.code}",
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
            } else {
                Spacer(modifier = Modifier.height(4.dp))
            }

            Spacer(modifier = Modifier.height(6.dp))

            AdaptiveAmountText(
                text = "$${String.format(Locale.US, "%.2f", product.mainPrice)}",
                baseStyle = PosTextStyles.priceTileLarge.copy(fontSize = 15.sp),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.fillMaxWidth(),
                options = AdaptiveAmountOptions(minFontSizeSp = 12f, maxLines = 1),
            )

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = onQuantityClick,
                    modifier =
                        Modifier
                            .size(36.dp)
                            .background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(8.dp)),
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Elegir cantidad",
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(18.dp),
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                IconButton(
                    onClick = onAddToCart,
                    modifier =
                        Modifier
                            .size(36.dp)
                            .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp)),
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
    onEditPrice: (CartItem) -> Unit,
    onEditDiscount: (CartItem) -> Unit,
    onPriceLevelChange: (String, String) -> Unit,
    onUpdatePromotionQuantity: (String, Int) -> Unit,
    onRemovePromotion: (String) -> Unit,
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
                items(state.displayItems, key = { it.id }) { displayItem ->
                    when (displayItem) {
                        is ItemCarrito.ProductoIndividual -> {
                            CartItemRow(
                                item = displayItem.item,
                                onIncrement = { onIncrement(displayItem.item.product.id) },
                                onDecrement = { onDecrement(displayItem.item.product.id) },
                                onRemove = { onRemove(displayItem.item.product.id) },
                                onEditPrice = { onEditPrice(displayItem.item) },
                                onEditDiscount = { onEditDiscount(displayItem.item) },
                                onPriceLevelChange = { level -> onPriceLevelChange(displayItem.item.product.id, level) },
                                allowEditPrice = state.allowEditPrices,
                                allowDiscount = state.allowDiscounts,
                            )
                        }
                        is ItemCarrito.PromocionAgrupada -> {
                            PromotionCartGroup(
                                group = displayItem,
                                onRemove = { onRemovePromotion(displayItem.promocionId) },
                                onQuantityChange = { times -> onUpdatePromotionQuantity(displayItem.promocionId, times) },
                            )
                        }
                    }
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
                            gradient = FlowBrandGradient,
                            modifier = Modifier.weight(2f).height(48.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ProductThumbnail(
    imageUrl: String,
    description: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        modifier = modifier.clip(RoundedCornerShape(8.dp)),
    ) {
        if (imageUrl.isNotBlank()) {
            AsyncImage(
                model =
                    ImageRequest.Builder(LocalContext.current)
                        .data(imageUrl)
                        .crossfade(true)
                        .build(),
                contentDescription = description,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                Icon(
                    Icons.Default.ShoppingCart,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.40f),
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

@Composable
private fun CompactPriceLevelChip(
    item: CartItem,
    onPriceLevelChange: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val availableLevels = remember(item.product.prices) {
        item.product.prices.filter { it.pricePlusTax > 0.0 || it.price > 0.0 }.ifEmpty { item.product.prices }
    }
    val currentLabel = if (item.isManualPrice) "Manual" else "Lista ${item.selectedPriceLabel}"

    Box {
        Surface(
            shape = RoundedCornerShape(6.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.60f),
            modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable { expanded = true },
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
            ) {
                Icon(
                    Icons.Default.LocalOffer,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(11.dp),
                )
                Spacer(modifier = Modifier.width(3.dp))
                Text(
                    text = currentLabel,
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Icon(
                    Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(13.dp),
                )
            }
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            availableLevels.forEach { level ->
                val isSelected = !item.isManualPrice && item.selectedPriceLabel.equals(level.label, ignoreCase = true)
                val levelPrice = level.pricePlusTax.takeIf { it > 0.0 } ?: level.price
                DropdownMenuItem(
                    text = {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "Lista ${level.label}",
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                "$${String.format(Locale.US, "%.2f", levelPrice)}",
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 13.sp,
                            )
                        }
                    },
                    onClick = {
                        expanded = false
                        onPriceLevelChange(level.label)
                    },
                )
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
    onEditPrice: () -> Unit,
    onEditDiscount: () -> Unit,
    onPriceLevelChange: (String) -> Unit,
    allowEditPrice: Boolean,
    allowDiscount: Boolean,
) {
    Card(
        shape = PosExtraShapes.CardRadius,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(10.dp).fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
            ) {
                ProductThumbnail(
                    imageUrl = item.product.photoUrl,
                    description = item.product.description,
                    modifier = Modifier.size(44.dp),
                )

                Spacer(modifier = Modifier.width(10.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top,
                    ) {
                        Text(
                            text = item.product.description,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.5.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f).padding(end = 6.dp),
                        )
                        AdaptiveAmountText(
                            text = "$${String.format(Locale.US, "%.2f", item.totalWithTax)}",
                            baseStyle = PosTextStyles.priceTileLarge.copy(fontSize = 14.sp, fontWeight = FontWeight.ExtraBold),
                            color = MaterialTheme.colorScheme.primary,
                            options = AdaptiveAmountOptions(minFontSizeSp = 11f),
                        )
                    }

                    Spacer(modifier = Modifier.height(3.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(
                                text = "$${String.format(Locale.US, "%.2f", item.unitPriceWithTax)}/${item.product.unitPackage.lowercase()}",
                                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, fontWeight = FontWeight.Medium),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )

                            if (!item.isPromotionLine && item.product.prices.isNotEmpty()) {
                                CompactPriceLevelChip(
                                    item = item,
                                    onPriceLevelChange = onPriceLevelChange,
                                )
                            }
                        }

                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f),
                            modifier = Modifier.size(24.dp),
                        ) {
                            IconButton(onClick = onRemove, modifier = Modifier.fillMaxSize()) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = "Quitar del carrito",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(13.dp),
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Stepper and edit actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
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

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (item.discountPercent > 0.0) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.50f),
                        ) {
                            Text(
                                "-${String.format(Locale.US, "%.0f", item.discountPercent)}%",
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                            )
                        }
                    }

                    if (allowEditPrice && !item.isPromotionLine) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                            modifier = Modifier.size(30.dp),
                        ) {
                            IconButton(onClick = onEditPrice, modifier = Modifier.fillMaxSize()) {
                                Icon(
                                    Icons.Default.Edit,
                                    contentDescription = "Editar precio",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(14.dp),
                                )
                            }
                        }
                    }

                    if (allowDiscount && !item.isPromotionLine) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.55f),
                            modifier = Modifier.size(30.dp),
                        ) {
                            IconButton(onClick = onEditDiscount, modifier = Modifier.fillMaxSize()) {
                                Icon(
                                    Icons.Default.LocalOffer,
                                    contentDescription = "Aplicar descuento",
                                    tint = MaterialTheme.colorScheme.tertiary,
                                    modifier = Modifier.size(14.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PromotionCartGroup(
    group: ItemCarrito.PromocionAgrupada,
    onRemove: () -> Unit,
    onQuantityChange: (Int) -> Unit,
) {
    val accent = if (group.promocionTipo == "KIT") MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary
    val times = group.items.firstOrNull()?.promocionVeces ?: 1

    Card(
        shape = PosExtraShapes.CardRadius,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Header
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier =
                        Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(accent.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Default.LocalOffer,
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "PROMOCIÓN ${group.promocionCodigo}",
                        color = accent,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 11.sp,
                    )
                    Text(
                        group.promocionNombre,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 14.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        "EL PRODUCTO ESTÁ CONFORMADO POR:",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.40f),
                    modifier = Modifier.size(28.dp),
                ) {
                    IconButton(onClick = onRemove, modifier = Modifier.fillMaxSize()) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Quitar promoción",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(15.dp),
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // Group items preview
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                group.items.forEach { item ->
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(accent))
                        Spacer(Modifier.width(6.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                item.product.description,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                "Cant. ${String.format(Locale.US, "%.1f", item.quantity)}",
                                fontSize = 10.5.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(
                            "$${String.format(Locale.US, "%.2f", item.totalWithTax)}",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = accent,
                        )
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Total promoción", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp)
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = accent.copy(alpha = 0.12f),
                ) {
                    Text(
                        "$${String.format(Locale.US, "%.2f", group.total)}",
                        fontWeight = FontWeight.ExtraBold,
                        color = accent,
                        fontSize = 15.sp,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            // Quantity stepper
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Veces:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.70f),
                        modifier = Modifier.size(30.dp),
                    ) {
                        IconButton(
                            onClick = {
                                if (times <= 1) onRemove() else onQuantityChange(times - 1)
                            },
                            modifier = Modifier.fillMaxSize(),
                        ) {
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
                                "$times",
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
                        IconButton(
                            onClick = { onQuantityChange(times + 1) },
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            Icon(
                                Icons.Default.Add,
                                contentDescription = "Aumentar",
                                tint = PosPalette.FixedWhite,
                                modifier = Modifier.size(16.dp),
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
private fun ProductQuantitySheet(
    product: Product,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var quantityText by remember { mutableStateOf("1") }
    val quantity = quantityText.toIntOrNull() ?: 0
    val isValid = quantity >= 1

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        dragHandle = { BottomSheetDefaults.DragHandle() },
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 18.dp)
                    .padding(bottom = 22.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("Cantidad a agregar", fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
            Text(product.description, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.70f),
                    modifier = Modifier.size(44.dp),
                ) {
                    IconButton(
                        onClick = {
                            quantityText = ((quantityText.toIntOrNull() ?: 1) - 1).coerceAtLeast(1).toString()
                        },
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        Icon(Icons.Default.Remove, contentDescription = "Menos", tint = MaterialTheme.colorScheme.onSecondaryContainer)
                    }
                }

                Spacer(Modifier.width(16.dp))

                OutlinedTextField(
                    value = quantityText,
                    onValueChange = { value ->
                        quantityText = value.filter { it.isDigit() }.take(5)
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.width(100.dp),
                    textStyle = MaterialTheme.typography.titleMedium.copy(textAlign = TextAlign.Center, fontWeight = FontWeight.Bold),
                )

                Spacer(Modifier.width(16.dp))

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(44.dp),
                ) {
                    IconButton(
                        onClick = {
                            quantityText = ((quantityText.toIntOrNull() ?: 0) + 1).coerceAtLeast(1).toString()
                        },
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Más", tint = PosPalette.FixedWhite)
                    }
                }
            }

            if (!isValid) {
                Text("La cantidad mínima es 1.", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = PosExtraShapes.InputRadius,
                ) {
                    Text("Cancelar")
                }
                Button(
                    onClick = { if (isValid) onConfirm(quantity) },
                    enabled = isValid,
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = PosExtraShapes.InputRadius,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Agregar")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PromotionChoiceSheet(
    product: Product,
    promotions: List<Promocion>,
    onAddIndividual: (Int) -> Unit,
    onAddPromotion: (Promocion, Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var individualQuantityText by remember { mutableStateOf("1") }
    val individualQty = individualQuantityText.toIntOrNull() ?: 0

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        dragHandle = { BottomSheetDefaults.DragHandle() },
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 18.dp)
                    .padding(bottom = 22.dp),
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier =
                            Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Default.LocalOffer,
                            contentDescription = null,
                            tint = PosPalette.FixedWhite,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Este producto tiene promoción",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                        Text(
                            product.description,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                            fontSize = 12.sp,
                        )
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Producto individual", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedTextField(
                            value = individualQuantityText,
                            onValueChange = { individualQuantityText = it.filter { c -> c.isDigit() }.take(5) },
                            label = { Text("Cantidad") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f),
                        )
                        OutlinedButton(
                            onClick = { if (individualQty >= 1) onAddIndividual(individualQty) },
                            enabled = individualQty >= 1,
                            modifier = Modifier.height(52.dp),
                            shape = PosExtraShapes.InputRadius,
                        ) {
                            Icon(Icons.Default.ShoppingCart, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Vender individual")
                        }
                    }
                }
            }

            Spacer(Modifier.height(14.dp))
            Text("Promociones disponibles", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))

            LazyColumn(
                modifier = Modifier.heightIn(max = 380.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(promotions, key = { it.id }) { promo ->
                    var timesText by remember { mutableStateOf("1") }
                    val times = timesText.toIntOrNull() ?: 1
                    val accent = if (promo.tipo == "KIT") MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary

                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(color = accent.copy(alpha = 0.12f), shape = RoundedCornerShape(6.dp)) {
                                    Text(
                                        text = promo.tipo,
                                        color = accent,
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 10.5.sp,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    )
                                }
                                Spacer(Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(promo.nombre, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    Text("Código ${promo.codigo}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                                }
                                Text(
                                    "$${String.format(Locale.US, "%.2f", promo.totalAmount)}",
                                    color = accent,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 15.sp,
                                )
                            }

                            Spacer(Modifier.height(8.dp))

                            Surface(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f), shape = RoundedCornerShape(8.dp)) {
                                Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    promo.detalles.take(3).forEach { detail ->
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Box(modifier = Modifier.size(5.dp).clip(CircleShape).background(accent))
                                            Spacer(Modifier.width(6.dp))
                                            Text(
                                                text = "${detail.cantidadTotal.stripTrailingZeros().toPlainString()} x ${detail.productName}",
                                                modifier = Modifier.weight(1f),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                fontSize = 11.5.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                            Text(
                                                "$${String.format(Locale.US, "%.2f", detail.totalConIva.toDouble())}",
                                                fontSize = 11.5.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    }
                                    if (promo.detalles.size > 3) {
                                        Text("+${promo.detalles.size - 3} productos más", color = accent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }

                            Spacer(Modifier.height(10.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                OutlinedTextField(
                                    value = timesText,
                                    onValueChange = { timesText = it.filter { c -> c.isDigit() }.take(4) },
                                    label = { Text("Veces") },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.width(80.dp),
                                )

                                Button(
                                    onClick = { if (times >= 1) onAddPromotion(promo, times) },
                                    enabled = times >= 1,
                                    modifier = Modifier.weight(1f).height(52.dp),
                                    shape = PosExtraShapes.InputRadius,
                                    colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = PosPalette.FixedWhite),
                                ) {
                                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(6.dp))
                                    Text("Agregar promo x$times", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private enum class CartEditTarget { QUANTITY, PRICE, DISCOUNT }

@Composable
private fun EditItemValueDialog(
    target: CartEditTarget,
    item: CartItem,
    value: String,
    onValueChange: (String) -> Unit,
    onConfirm: (Double) -> Unit,
    onDismiss: () -> Unit,
) {
    val title = when (target) {
        CartEditTarget.QUANTITY -> "Editar cantidad"
        CartEditTarget.PRICE -> "Editar precio unitario"
        CartEditTarget.DISCOUNT -> "Aplicar descuento"
    }
    val label = when (target) {
        CartEditTarget.QUANTITY -> "Cantidad (${item.product.unitPackage.ifBlank { "UND" }})"
        CartEditTarget.PRICE -> "Precio unitario con IVA"
        CartEditTarget.DISCOUNT -> "Descuento (%)"
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(onClick = {
                val parsed = value.toDoubleOrNull()
                if (parsed != null && (target != CartEditTarget.QUANTITY || parsed > 0.0)) {
                    onConfirm(parsed)
                }
            }) {
                Text("Guardar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        },
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text(item.product.description, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = value,
                    onValueChange = onValueChange,
                    label = { Text(label) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth().forceShowKeyboardOnTouch(),
                )
            }
        },
    )
}

@Composable
private fun CustomClientNameDialog(
    initialName: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Facturar a Nombre de (CF)", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text(
                    "Ingrese el nombre de la persona o razón social para emitir la factura a Consumidor Final:",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nombre o Razón Social") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().forceShowKeyboardOnTouch(),
                )
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(name) }) {
                Text("Guardar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        },
    )
}

@Composable
private fun GlobalDiscountDialog(
    currentPercent: Double,
    onConfirm: (Double) -> Unit,
    onDismiss: () -> Unit,
) {
    var discountText by remember { mutableStateOf(if (currentPercent > 0.0) String.format(Locale.US, "%.0f", currentPercent) else "") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Descuento Global de la Venta", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Seleccione o ingrese el porcentaje de descuento global aplicable a toda la factura:",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    listOf(0, 5, 10, 15, 20).forEach { pct ->
                        OutlinedButton(
                            onClick = { discountText = pct.toString() },
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 2.dp, vertical = 4.dp),
                        ) {
                            Text("$pct%", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                OutlinedTextField(
                    value = discountText,
                    onValueChange = { discountText = it },
                    label = { Text("Porcentaje (%)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth().forceShowKeyboardOnTouch(),
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                val parsed = discountText.toDoubleOrNull() ?: 0.0
                onConfirm(parsed)
            }) {
                Text("Aplicar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
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
