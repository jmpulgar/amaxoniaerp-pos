package com.amaxonia.pos.ui.dashboard

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.AssignmentReturn
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Inventory
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PointOfSale
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.TableRestaurant
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.work.WorkInfo
import coil.compose.AsyncImage
import com.amaxonia.pos.R
import com.amaxonia.pos.core.logging.SafeLog
import com.amaxonia.pos.data.sync.SyncScheduler
import com.amaxonia.pos.domain.model.Promocion
import com.amaxonia.pos.domain.model.caja.Caja
import com.amaxonia.pos.domain.model.caja.CajaSessionStatus
import com.amaxonia.pos.domain.usecase.BigDecimalMoneyFormatter
import com.amaxonia.pos.domain.usecase.cart.ResolveClientBranchesUseCase
import com.amaxonia.pos.ui.common.DependencyContainer
import com.amaxonia.pos.ui.common.SellerSelectorBottomSheet
import com.amaxonia.pos.ui.common.components.AdaptiveAmountText
import com.amaxonia.pos.ui.common.components.CategoryChipRow
import com.amaxonia.pos.ui.common.components.PosMoneyInput
import com.amaxonia.pos.ui.common.components.QuantityStepper
import com.amaxonia.pos.ui.common.injectedViewModel
import com.amaxonia.pos.ui.common.shortName
import com.amaxonia.pos.ui.theme.InfoBlue
import com.amaxonia.pos.ui.theme.NeutralGray
import com.amaxonia.pos.ui.theme.OfflineRed
import com.amaxonia.pos.ui.theme.OnlineGreen
import com.amaxonia.pos.ui.theme.PosExtraShapes
import com.amaxonia.pos.ui.theme.PosPalette
import com.amaxonia.pos.ui.theme.PosTextStyles
import com.amaxonia.pos.ui.theme.SuccessGreen
import com.amaxonia.pos.ui.theme.WarningOrange
import kotlinx.coroutines.launch

private const val DASHBOARD_LOG_TAG = "DashboardScreen"

@OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
// Raíz Compose conserva navegación y estado local; dividirla alteraría alcance de remember/effects.
@Suppress("LongParameterList", "LongMethod", "CyclomaticComplexMethod")
fun DashboardScreen(
    viewModel: DashboardViewModel =
        injectedViewModel {
            val cajaCoordinator =
                DashboardCajaCoordinator(
                    DependencyContainer.cajaRepository,
                    DependencyContainer.cartRepository,
                    DependencyContainer.cashClosePrintingService,
                    DependencyContainer.cashCloseTicketPayloadBuilder,
                    DependencyContainer.networkMonitor,
                )
            DashboardViewModel(
                catalogCoordinator =
                    DashboardCatalogCoordinator(
                        DependencyContainer.productRepository,
                        DependencyContainer.reportRepository,
                        DependencyContainer.posConfigurationRepository,
                        DependencyContainer.serverEnvironment,
                        DashboardProductMapper(DependencyContainer.imageUrlResolver),
                    ),
                cartCoordinator =
                    DashboardCartCoordinator(
                        DependencyContainer.promotionRepository,
                        DependencyContainer.cartRepository,
                        ResolveClientBranchesUseCase(
                            DependencyContainer.posConfigurationRepository,
                            DependencyContainer.clientBranchRepository,
                        ),
                        DependencyContainer.refreshCartProductLotsUseCase,
                        cajaCoordinator,
                        DependencyContainer.appClock,
                    ),
                cajaCoordinator = cajaCoordinator,
            )
        },
    onLogout: () -> Unit,
    onNavigateToClients: () -> Unit,
    onNavigateToProducts: () -> Unit,
    onNavigateToHistory: () -> Unit,
    onNavigateToCreditNotes: () -> Unit,
    onNavigateToReports: () -> Unit,
    onNavigateToPrinterSettings: () -> Unit,
    onNavigateToCart: () -> Unit,
    onStartNewOrder: () -> Unit,
    onNavigateToCierreCaja: () -> Unit = {},
    onNavigateToDraftInvoices: () -> Unit = {},
    onNavigateToAreasMesas: () -> Unit = {},
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val currentOnNavigateToCart by rememberUpdatedState(onNavigateToCart)
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                DashboardUiEffect.NavigateToCart -> currentOnNavigateToCart()
            }
        }
    }
    // Abre el diálogo de apertura cuando se solicita desde otra pantalla
    // (p. ej. "Aperturar nueva caja" tras el cierre).
    val pendingApertura by DependencyContainer.pendingAperturaRequest.collectAsStateWithLifecycle()
    LaunchedEffect(pendingApertura) {
        if (pendingApertura) {
            viewModel.onAction(DashboardCajaUiAction.RequestAperturaActive)
            DependencyContainer.consumeAperturaRequest()
        }
    }
    // Abre el selector de caja cuando otra pantalla lo solicita (p. ej. "Áreas y mesas" sin caja).
    val pendingCajaSelector by DependencyContainer.pendingCajaSelectorRequest.collectAsStateWithLifecycle()
    LaunchedEffect(pendingCajaSelector) {
        if (pendingCajaSelector) {
            viewModel.onAction(DashboardCajaUiAction.Fetch(forceShowSelector = true))
            DependencyContainer.consumeCajaSelectorRequest()
        }
    }
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current
    var showSellerSheet by remember { mutableStateOf(false) }
    val productGridState = rememberLazyGridState()
    val productListState = rememberLazyListState()
    val manualSyncInfos by SyncScheduler.getManualSyncWorkInfos(context).observeAsState(emptyList())
    val isSyncRunning =
        manualSyncInfos.any { info ->
            info.state == WorkInfo.State.RUNNING || info.state == WorkInfo.State.ENQUEUED
        }

    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val searchFocusRequester = remember { FocusRequester() }

    LaunchedEffect(state.isSearchOpen) {
        if (state.isSearchOpen) {
            withFrameNanos { }
            searchFocusRequester.requestFocus()
            keyboardController?.show()
        }
    }

    DisposableEffect(Unit) {
        SafeLog.d(DASHBOARD_LOG_TAG, "Dashboard composed")
        onDispose {
            SafeLog.d(DASHBOARD_LOG_TAG, "Dashboard disposed")
        }
    }

    val productsToShow: List<DashboardProduct> = if (state.bottomSelected == 1) state.bestSellers else state.products

    val gridReachedBottom by remember {
        derivedStateOf {
            val lastVisible =
                productGridState.layoutInfo.visibleItemsInfo
                    .lastOrNull()
                    ?.index ?: return@derivedStateOf false
            lastVisible >= productGridState.layoutInfo.totalItemsCount - 6
        }
    }

    val listReachedBottom by remember {
        derivedStateOf {
            val lastVisible =
                productListState.layoutInfo.visibleItemsInfo
                    .lastOrNull()
                    ?.index ?: return@derivedStateOf false
            lastVisible >= productListState.layoutInfo.totalItemsCount - 6
        }
    }

    LaunchedEffect(gridReachedBottom, listReachedBottom, state.viewMode, state.bottomSelected) {
        if (state.bottomSelected == 0 && (gridReachedBottom || listReachedBottom)) {
            viewModel.onAction(DashboardCatalogUiAction.LoadMoreProducts)
        }
    }

    // --- Snackbar for auto-close notifications ---
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(state.autoCloseMessage) {
        state.autoCloseMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg, duration = SnackbarDuration.Long)
            viewModel.onAction(DashboardCajaUiAction.DismissAutoCloseMessage)
        }
    }

    LaunchedEffect(state.promotionMessage) {
        val msg = state.promotionMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(msg, duration = SnackbarDuration.Short)
        viewModel.onAction(DashboardSaleUiAction.ClearPromotionMessage)
    }

    state.automaticCloseTicketOffer?.let { offer ->
        val canPrint = offer.payload != null
        AlertDialog(
            onDismissRequest = {
                if (!state.isPrintingAutomaticCloseTicket) {
                    viewModel.onAction(DashboardCajaUiAction.DismissAutomaticCloseTicket)
                }
            },
            title = { Text("Imprimir cierre de caja") },
            text = {
                Text(
                    offer.unavailableReason
                        ?: "La caja anterior se cerró automáticamente. ¿Deseas imprimir el ticket con el resumen del cierre?",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.onAction(DashboardCajaUiAction.PrintAutomaticCloseTicket) },
                    enabled = !state.isPrintingAutomaticCloseTicket,
                ) {
                    Text(
                        when {
                            state.isPrintingAutomaticCloseTicket -> "Imprimiendo..."
                            canPrint -> "Imprimir"
                            else -> "Entendido"
                        },
                    )
                }
            },
            dismissButton = {
                if (canPrint) {
                    TextButton(
                        onClick = { viewModel.onAction(DashboardCajaUiAction.DismissAutomaticCloseTicket) },
                        enabled = !state.isPrintingAutomaticCloseTicket,
                    ) {
                        Text("No imprimir")
                    }
                }
            },
        )
    }

    // --- CajaSelectorSheet (replaces old AlertDialog) ---
    if (state.showCajaSelector) {
        CajaSelectorSheet(
            cajas = state.availableCajas,
            isLoading = state.isLoadingCajas,
            errorMessage = state.error,
            canDismiss = state.hasActiveCaja,
            onSelectCaja = { caja -> viewModel.onAction(DashboardCajaUiAction.RequestApertura(caja)) },
            onReload = { viewModel.onAction(DashboardCajaUiAction.Fetch()) },
            onDismiss = {
                if (state.hasActiveCaja) {
                    viewModel.onAction(DashboardCajaUiAction.SetSelectorVisible(false))
                }
            },
        )
    }

    state.aperturaCandidate?.takeIf { state.showAperturaPrompt }?.let { caja ->
        AperturaCajaDialog(
            caja = caja,
            isLoading = state.isLoadingCajas,
            onConfirm = { amount -> viewModel.onAction(DashboardCajaUiAction.ConfirmApertura(caja, amount)) },
            onDismiss = { viewModel.onAction(DashboardCajaUiAction.DismissApertura) },
        )
    }

    if (showSellerSheet) {
        SellerSelectorBottomSheet(
            sellers = state.availableSellers,
            selectedSellerId = state.currentSeller?.id,
            onSelect = { seller -> viewModel.onAction(DashboardSaleUiAction.SelectSeller(seller.id)) },
            onDismiss = { showSellerSheet = false },
        )
    }

    state.quantityPickerProduct?.let { product ->
        ProductQuantitySheet(
            product = product,
            onConfirm = { quantity ->
                viewModel.onAction(DashboardSaleUiAction.ConfirmProductQuantity(product, quantity))
            },
            onDismiss = { viewModel.onAction(DashboardSaleUiAction.DismissQuantityPicker) },
        )
    }

    state.pendingPromotionProduct?.takeIf { state.showPromotionChoice }?.let { product ->
        PromotionChoiceSheet(
            product = product,
            promotions = state.promotionOptions,
            onAddIndividual = { quantity ->
                viewModel.onAction(DashboardSaleUiAction.AddProductIndividualFromPromotionChoice(quantity))
            },
            onAddPromotion = { promo, times ->
                viewModel.onAction(DashboardSaleUiAction.AddPromotionFromChoice(promo, times))
            },
            onDismiss = { viewModel.onAction(DashboardSaleUiAction.DismissPromotionChoice) },
        )
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = MaterialTheme.colorScheme.primary,
                drawerContentColor = PosPalette.FixedWhite,
                modifier = Modifier.width(300.dp).fillMaxHeight(),
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(
                            painter = painterResource(id = R.drawable.brand_mark),
                            contentDescription = stringResource(R.string.brand_logo_description),
                            modifier =
                                Modifier
                                    .size(40.dp)
                                    .background(PosPalette.FixedWhite, MaterialTheme.shapes.small)
                                    .padding(4.dp),
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(stringResource(R.string.brand_name), fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.weight(1f))
                        Surface(shape = PosExtraShapes.Pill, color = PosPalette.FixedWhite) {
                            Text(
                                "Pro+",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.ExtraBold,
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(20.dp))
                    Text(state.sucursalNombre, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(10.dp))
                    Surface(
                        color = PosPalette.FixedWhite.copy(alpha = 0.2f),
                        shape = MaterialTheme.shapes.small,
                        modifier =
                            Modifier.fillMaxWidth().clickable {
                                scope.launch {
                                    drawerState.close()
                                    viewModel.onAction(DashboardCajaUiAction.Fetch(forceShowSelector = true))
                                }
                            },
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(state.cajaPrincipalNombre, color = PosPalette.FixedWhite, modifier = Modifier.weight(1f))
                            Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Cambiar caja", tint = PosPalette.FixedWhite)
                        }
                    }
                }
                HorizontalDivider(color = PosPalette.FixedWhite.copy(alpha = 0.2f))
                Column(
                    modifier =
                        Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState()),
                ) {
                    DrawerMenuItem(Icons.Default.People, "Clientes") {
                        scope.launch {
                            drawerState.close()
                            onNavigateToClients()
                        }
                    }
                    DrawerMenuItem(Icons.Default.ShoppingBag, "Productos") {
                        scope.launch {
                            drawerState.close()
                            onNavigateToProducts()
                        }
                    }
                    DrawerMenuItem(Icons.Default.PointOfSale, "POS", isSelected = true) {
                        scope.launch { drawerState.close() }
                    }
                    DrawerMenuItem(Icons.Default.TableRestaurant, "Áreas y mesas") {
                        scope.launch {
                            drawerState.close()
                            onNavigateToAreasMesas()
                        }
                    }
                    DrawerMenuItem(Icons.AutoMirrored.Filled.ListAlt, "Crear Pedido") {
                        scope.launch {
                            drawerState.close()
                            viewModel.onAction(DashboardSaleUiAction.StartNewOrder)
                            onStartNewOrder() // Navega a selección de cliente
                        }
                    }
                    DrawerMenuItem(Icons.AutoMirrored.Filled.ReceiptLong, "Historial Transacciones") {
                        scope.launch {
                            drawerState.close()
                            onNavigateToHistory()
                        }
                    }
                    DrawerMenuItem(Icons.AutoMirrored.Filled.AssignmentReturn, "Notas de crédito") {
                        scope.launch {
                            drawerState.close()
                            onNavigateToCreditNotes()
                        }
                    }
                    DrawerMenuItem(Icons.Default.BarChart, "Reportes") {
                        scope.launch {
                            drawerState.close()
                            onNavigateToReports()
                        }
                    }
                    DrawerMenuItem(Icons.Default.Description, "Facturas Pendientes") {
                        scope.launch {
                            drawerState.close()
                            onNavigateToDraftInvoices()
                        }
                    }
                    DrawerMenuItem(Icons.Default.Settings, "Configuracion POS") {
                        scope.launch {
                            drawerState.close()
                            onNavigateToPrinterSettings()
                        }
                    }
                    DrawerMenuItem(Icons.Default.Lock, "Cerrar Caja") {
                        scope.launch {
                            drawerState.close()
                            onNavigateToCierreCaja()
                        }
                    }
                    DrawerMenuItem(Icons.Default.Refresh, "Actualizar datos") {
                        SyncScheduler.enqueueManual(context)
                        scope.launch { drawerState.close() }
                    }
                }
                Column(modifier = Modifier.padding(24.dp)) {
                    Button(
                        onClick = onLogout,
                        colors = ButtonDefaults.buttonColors(containerColor = PosPalette.FixedWhite),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ExitToApp,
                            null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Cerrar Sesión", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    }
                }
            }
        },
    ) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            snackbarHost = {
                SnackbarHost(hostState = snackbarHostState) { snackbarData ->
                    val message = snackbarData.visuals.message
                    val isOnlineMessage = message.startsWith("Conexión restaurada")
                    val isOfflineMessage = message.startsWith("Sin conexión")
                    Snackbar(
                        snackbarData = snackbarData,
                        containerColor =
                            when {
                                isOnlineMessage -> OnlineGreen
                                isOfflineMessage -> OfflineRed
                                else -> MaterialTheme.colorScheme.inverseSurface
                            },
                        contentColor =
                            when {
                                isOnlineMessage || isOfflineMessage -> PosPalette.FixedWhite
                                else -> MaterialTheme.colorScheme.inverseOnSurface
                            },
                        actionColor = PosPalette.FixedWhite,
                        shape = MaterialTheme.shapes.medium,
                    )
                }
            },
            topBar = {
                TopAppBar(
                    title = {
                        if (state.isSearchOpen) {
                            TextField(
                                value = state.searchQuery,
                                onValueChange = { viewModel.onAction(DashboardCatalogUiAction.SetSearchQuery(it)) },
                                placeholder = { Text("Buscar...", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                                singleLine = true,
                                textStyle = TextStyle(fontSize = 16.sp, color = MaterialTheme.colorScheme.primary),
                                colors =
                                    TextFieldDefaults.colors(
                                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                                        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                                        focusedTextColor = MaterialTheme.colorScheme.primary,
                                        unfocusedTextColor = MaterialTheme.colorScheme.primary,
                                        cursorColor = MaterialTheme.colorScheme.primary,
                                        focusedIndicatorColor = PosPalette.Transparent,
                                        unfocusedIndicatorColor = PosPalette.Transparent,
                                    ),
                                shape = MaterialTheme.shapes.medium,
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .height(50.dp)
                                        .focusRequester(searchFocusRequester),
                            )
                        } else {
                            Text("Punto de Venta", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        }
                    },
                    navigationIcon = {
                        IconButton(
                            onClick = {
                                focusManager.clearFocus()
                                scope.launch {
                                    try {
                                        SafeLog.d(DASHBOARD_LOG_TAG, "Drawer requested")
                                        if (drawerState.isClosed) {
                                            drawerState.snapTo(DrawerValue.Open)
                                            SafeLog.d(DASHBOARD_LOG_TAG, "Drawer opened")
                                        } else {
                                            SafeLog.d(DASHBOARD_LOG_TAG, "Drawer open request ignored")
                                        }
                                    } catch (throwable: Throwable) {
                                        SafeLog.e(DASHBOARD_LOG_TAG, "Unable to open drawer", throwable)
                                        runCatching { drawerState.close() }
                                    }
                                }
                            },
                        ) {
                            Icon(Icons.Default.Menu, contentDescription = "Menu", tint = MaterialTheme.colorScheme.primary)
                        }
                    },
                    actions = {
                        IconButton(onClick = {
                            if (state.isSearchOpen) {
                                focusManager.clearFocus()
                                keyboardController?.hide()
                            }
                            viewModel.onAction(DashboardCatalogUiAction.ToggleSearch)
                        }) {
                            Icon(
                                imageVector = if (state.isSearchOpen) Icons.Default.Close else Icons.Default.Search,
                                contentDescription = "Buscar",
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                        IconButton(onClick = { /* TODO */ }) {
                            Icon(
                                Icons.Default.DocumentScanner,
                                contentDescription = "Escanear código",
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                        IconButton(onClick = { viewModel.onAction(DashboardCatalogUiAction.ToggleViewMode) }) {
                            Icon(
                                imageVector =
                                    if (state.viewMode ==
                                        ProductViewMode.GRID
                                    ) {
                                        Icons.AutoMirrored.Filled.ViewList
                                    } else {
                                        Icons.Default.GridView
                                    },
                                contentDescription = "Cambiar vista",
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                )
            },
            bottomBar = {
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(bottom = 10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        shape = PosExtraShapes.NavPill,
                        tonalElevation = 0.dp,
                        shadowElevation = 8.dp,
                        modifier =
                            Modifier
                                .padding(horizontal = 18.dp)
                                .height(58.dp)
                                .fillMaxWidth(),
                    ) {
                        Row(
                            modifier =
                                Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceEvenly,
                        ) {
                            BottomPillItem(
                                selected = state.bottomSelected == 0,
                                onClick = { viewModel.onAction(DashboardCatalogUiAction.SetBottomSelected(0)) },
                                icon = Icons.Default.GridView,
                                label = "Catálogo",
                            )
                            BottomPillItem(
                                selected = state.bottomSelected == 1,
                                onClick = { viewModel.onAction(DashboardCatalogUiAction.SetBottomSelected(1)) },
                                icon = Icons.Default.StarBorder,
                                label = "Top ventas",
                            )
                            BottomPillItem(
                                selected = state.bottomSelected == 2,
                                onClick = { viewModel.onAction(DashboardCatalogUiAction.SetBottomSelected(2)) },
                                icon = Icons.Default.EditNote,
                                label = "Manual",
                            )
                        }
                    }
                }
            },
        ) { paddingValues ->
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
            ) {
                if (isSyncRunning) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CircularProgressIndicator(
                                color = MaterialTheme.colorScheme.primary,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Sincronizando datos...",
                                style = TextStyle(fontSize = 12.sp, color = MaterialTheme.colorScheme.primary),
                            )
                        }
                    }
                }

                CajaStatusBanner(
                    session = state.cajaSession,
                    cajaName = state.cajaPrincipalNombre,
                    onAperturar = { viewModel.onAction(DashboardCajaUiAction.RequestAperturaActive) },
                    onSeleccionar = { viewModel.onAction(DashboardCajaUiAction.Fetch(forceShowSelector = true)) },
                )

                // --- Contenido Principal ---
                Box(modifier = Modifier.weight(1f)) {
                    if (state.error != null) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = state.error ?: "Error desconocido",
                                    color = MaterialTheme.colorScheme.error,
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Button(onClick = { viewModel.onAction(DashboardCatalogUiAction.Retry) }) {
                                    Text("Reintentar")
                                }
                            }
                        }
                    } else if (state.isInitialProductLoading || state.isInitialBestSellersLoading) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        }
                    } else {
                        // LOGICA PARA MOSTRAR CONTENIDO SEGUN PESTAÑA
                        if (state.bottomSelected == 2) {
                            // PESTAÑA 3: Entrada Manual
                            ManualEntryContent(
                                currentValue = state.manualEntryValue,
                                onKeyClick = { viewModel.onAction(DashboardSaleUiAction.ManualKey(it)) },
                                onClearClick = { viewModel.onAction(DashboardSaleUiAction.ManualClear) },
                                onBackspaceClick = { viewModel.onAction(DashboardSaleUiAction.ManualBackspace) },
                                onEnterClick = { viewModel.onAction(DashboardSaleUiAction.ManualSubmit) },
                            )
                        } else {
                            // PESTAÑAS 0 y 1: Grid o Lista de Productos
                            Column(
                                modifier =
                                    Modifier
                                        .fillMaxSize()
                                        .padding(horizontal = 16.dp),
                            ) {
                                Row(
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .padding(bottom = 12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    if (state.bottomSelected == 1) {
                                        Text(
                                            text = "Productos Más Vendidos",
                                            style = MaterialTheme.typography.titleMedium,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier.weight(1f),
                                        )
                                    } else {
                                        CategoryChipRow(
                                            departments = state.departments,
                                            selectedDepartmentId = state.selectedDepartmentId,
                                            onSelect = { id -> viewModel.onAction(DashboardCatalogUiAction.SelectDepartment(id)) },
                                            onMoreClick = { viewModel.onAction(DashboardCatalogUiAction.SetDepartmentPicker(true)) },
                                            modifier = Modifier.weight(1f),
                                        )
                                    }

                                    Surface(
                                        modifier =
                                            Modifier
                                                .padding(start = 8.dp)
                                                .clickable { showSellerSheet = true },
                                        color = MaterialTheme.colorScheme.surface,
                                        shape = PosExtraShapes.Pill,
                                        shadowElevation = 1.dp,
                                    ) {
                                        Row(
                                            modifier =
                                                Modifier
                                                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, PosExtraShapes.Pill)
                                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Person,
                                                contentDescription = "Vendedor",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(16.dp),
                                            )
                                            Text(
                                                text = state.currentSeller?.shortName() ?: "Vendedor",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                modifier = Modifier.padding(start = 6.dp),
                                            )
                                        }
                                    }
                                }
                                if (state.showDepartmentPicker) {
                                    ModalBottomSheet(
                                        onDismissRequest = {
                                            viewModel.onAction(DashboardCatalogUiAction.SetDepartmentPicker(false))
                                        },
                                    ) {
                                        LazyColumn(
                                            modifier =
                                                Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 16.dp)
                                                    .padding(bottom = 32.dp),
                                        ) {
                                            item {
                                                Text(
                                                    "Filtrar por departamento",
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 18.sp,
                                                    color = MaterialTheme.colorScheme.onSurface,
                                                    modifier = Modifier.padding(vertical = 8.dp),
                                                )
                                            }
                                            item {
                                                Surface(
                                                    modifier =
                                                        Modifier
                                                            .fillMaxWidth()
                                                            .clickable {
                                                                viewModel.onAction(DashboardCatalogUiAction.SelectDepartment(null))
                                                            },
                                                    color =
                                                        if (state.selectedDepartmentId ==
                                                            null
                                                        ) {
                                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                                                        } else {
                                                            PosPalette.Transparent
                                                        },
                                                ) {
                                                    Text(
                                                        "Todos",
                                                        modifier = Modifier.padding(16.dp),
                                                        fontSize = 16.sp,
                                                    )
                                                }
                                            }
                                            items(state.departments, key = { it.id }) { dept ->
                                                Surface(
                                                    modifier =
                                                        Modifier
                                                            .fillMaxWidth()
                                                            .clickable {
                                                                viewModel.onAction(DashboardCatalogUiAction.SelectDepartment(dept.id))
                                                            },
                                                    color =
                                                        if (state.selectedDepartmentId ==
                                                            dept.id
                                                        ) {
                                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                                                        } else {
                                                            PosPalette.Transparent
                                                        },
                                                ) {
                                                    Text(
                                                        dept.name,
                                                        modifier = Modifier.padding(16.dp),
                                                        fontSize = 16.sp,
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                                if (state.viewMode == ProductViewMode.GRID) {
                                    LazyVerticalGrid(
                                        state = productGridState,
                                        // 140dp: 2 columnas incluso en 320dp (densidad POS) sin
                                        // desbordar la fila de acciones de la tarjeta.
                                        columns = GridCells.Adaptive(minSize = 140.dp),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        verticalArrangement = Arrangement.spacedBy(12.dp),
                                        contentPadding = PaddingValues(bottom = 120.dp),
                                    ) {
                                        items(productsToShow, key = { it.id }) { product ->
                                            ProductCard(
                                                product = product,
                                                onAddClick = {
                                                    viewModel.onAction(DashboardSaleUiAction.AddProduct(product))
                                                },
                                                onQuantityClick = {
                                                    viewModel.onAction(DashboardSaleUiAction.ShowQuantityPicker(product))
                                                },
                                            )
                                        }
                                        if (state.isLoadingMore) {
                                            item(span = {
                                                androidx.compose.foundation.lazy.grid
                                                    .GridItemSpan(maxLineSpan)
                                            }) {
                                                Box(
                                                    modifier =
                                                        Modifier
                                                            .fillMaxWidth()
                                                            .padding(16.dp),
                                                    contentAlignment = Alignment.Center,
                                                ) {
                                                    CircularProgressIndicator(
                                                        color = MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.size(28.dp),
                                                    )
                                                }
                                            }
                                        }
                                    }
                                } else {
                                    LazyColumn(
                                        state = productListState,
                                        verticalArrangement = Arrangement.spacedBy(12.dp),
                                        contentPadding = PaddingValues(bottom = 120.dp),
                                    ) {
                                        items(productsToShow, key = { it.id }) { product ->
                                            ProductListRow(
                                                product = product,
                                                onAddClick = {
                                                    viewModel.onAction(DashboardSaleUiAction.AddProduct(product))
                                                },
                                                onQuantityClick = {
                                                    viewModel.onAction(DashboardSaleUiAction.ShowQuantityPicker(product))
                                                },
                                            )
                                        }
                                        if (state.isLoadingMore) {
                                            item {
                                                Box(
                                                    modifier =
                                                        Modifier
                                                            .fillMaxWidth()
                                                            .padding(16.dp),
                                                    contentAlignment = Alignment.Center,
                                                ) {
                                                    CircularProgressIndicator(
                                                        color = MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.size(28.dp),
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Botón flotante del carrito
                    if (state.cartItemCount > 0) {
                        Button(
                            onClick = { viewModel.onAction(DashboardSaleUiAction.Checkout) },
                            shape = MaterialTheme.shapes.medium,
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp, pressedElevation = 6.dp),
                            modifier =
                                Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(16.dp)
                                    .fillMaxWidth()
                                    .height(56.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f, fill = false),
                                ) {
                                    Icon(Icons.Default.ShoppingCart, null, tint = PosPalette.FixedWhite)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        if (state.cartItemCount == 1) "1 artículo" else "${state.cartItemCount} artículos",
                                        color = PosPalette.FixedWhite,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                // Total adaptive: montos grandes encogen sin recortarse en 320dp.
                                AdaptiveAmountText(
                                    text = "$${String.format(java.util.Locale.getDefault(), "%.2f", state.cartTotal)}",
                                    baseStyle = MaterialTheme.typography.titleMedium,
                                    color = PosPalette.FixedWhite,
                                    options =
                                        com.amaxonia.pos.ui.common.components.AdaptiveAmountOptions(
                                            fontWeight = FontWeight.Bold,
                                            minFontSizeSp = 13f,
                                            maxLines = 1,
                                        ),
                                )
                            }
                        }
                    }
                } // Fin Box Content
            } // Fin Column
        } // Fin Scaffold
    } // Fin ModalNavigationDrawer
} // <--- ESTA LLAVE FALTABA, CERRANDO LA FUNCION DashboardScreen

// AHORA ESTAS FUNCIONES ESTÁN FUERA, COMO DEBEN ESTAR

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProductQuantitySheet(
    product: DashboardProduct,
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
            Text(product.name, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)

            QuantityStepper(
                quantityText = quantityText,
                onQuantityTextChange = { quantityText = sanitizeQuantityInput(it) },
                onDecrease = { quantityText = ((quantityText.toIntOrNull() ?: 1) - 1).coerceAtLeast(1).toString() },
                onIncrease = { quantityText = ((quantityText.toIntOrNull() ?: 0) + 1).coerceAtLeast(1).toString() },
                onDone = { if (isValid) onConfirm(quantity) },
                isError = quantityText.isNotBlank() && !isValid,
                modifier = Modifier.fillMaxWidth(),
            )

            if (!isValid) {
                Text("La cantidad minima es 1.", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f).height(50.dp),
                    shape = MaterialTheme.shapes.medium,
                ) {
                    Text("Cancelar")
                }
                Button(
                    onClick = { if (isValid) onConfirm(quantity) },
                    enabled = isValid,
                    modifier = Modifier.weight(1f).height(50.dp),
                    shape = MaterialTheme.shapes.medium,
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
    product: DashboardProduct,
    promotions: List<Promocion>,
    onAddIndividual: (Int) -> Unit,
    onAddPromotion: (Promocion, Int) -> Unit,
    onDismiss: () -> Unit,
) {
    var individualQuantityText by remember { mutableStateOf("1") }
    val individualQuantity = individualQuantityText.toIntOrNull() ?: 0
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
                shape = MaterialTheme.shapes.extraLarge,
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
                                .size(48.dp)
                                .clip(MaterialTheme.shapes.medium)
                                .background(
                                    Brush.linearGradient(
                                        listOf(
                                            MaterialTheme.colorScheme.primary,
                                            MaterialTheme.colorScheme.tertiary,
                                        ),
                                    ),
                                ),
                    ) {
                        Icon(
                            Icons.Default.LocalOffer,
                            contentDescription = null,
                            tint = PosPalette.FixedWhite,
                            modifier = Modifier.align(Alignment.Center),
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Este producto tiene promoción",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 18.sp,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                        Text(
                            product.name,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.78f),
                            fontSize = 13.sp,
                        )
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Producto individual", fontWeight = FontWeight.Bold)
                    QuantityStepper(
                        quantityText = individualQuantityText,
                        onQuantityTextChange = { individualQuantityText = sanitizeQuantityInput(it) },
                        onDecrease = {
                            individualQuantityText =
                                ((individualQuantityText.toIntOrNull() ?: 1) - 1).coerceAtLeast(1).toString()
                        },
                        onIncrease = {
                            individualQuantityText =
                                ((individualQuantityText.toIntOrNull() ?: 0) + 1).coerceAtLeast(1).toString()
                        },
                        onDone = { if (individualQuantity >= 1) onAddIndividual(individualQuantity) },
                        isError = individualQuantityText.isNotBlank() && individualQuantity < 1,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedButton(
                        onClick = { if (individualQuantity >= 1) onAddIndividual(individualQuantity) },
                        enabled = individualQuantity >= 1,
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = MaterialTheme.shapes.medium,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary),
                    ) {
                        Icon(Icons.Default.ShoppingCart, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Vender producto individual", fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(Modifier.height(14.dp))
            Text("Promociones disponibles", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))

            LazyColumn(
                modifier = Modifier.heightIn(max = 420.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(promotions, key = { it.id }) { promo ->
                    PromotionOptionCard(promo = promo, onAddPromotion = { times -> onAddPromotion(promo, times) })
                }
            }
        }
    }
}

@Composable
private fun PromotionOptionCard(
    promo: Promocion,
    onAddPromotion: (Int) -> Unit,
) {
    val accent = if (promo.tipo == "KIT") MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary
    var timesText by remember { mutableStateOf("1") }
    val times = timesText.toIntOrNull() ?: 0
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Surface(color = accent.copy(alpha = 0.12f), shape = PosExtraShapes.Pill) {
                    Text(
                        text = promo.tipo,
                        color = accent,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    )
                }
                Spacer(Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(promo.nombre, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text("Código ${promo.codigo}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                }
                Text(BigDecimalMoneyFormatter.money(promo.total), color = accent, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp)
            }

            Spacer(Modifier.height(10.dp))
            Surface(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), shape = MaterialTheme.shapes.medium) {
                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    promo.detalles.take(4).forEach { detail ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(accent))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "${detail.cantidadTotal.stripTrailingZeros().toPlainString()} x ${detail.productName}",
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                BigDecimalMoneyFormatter.money(detail.totalConIva),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    if (promo.detalles.size > 4) {
                        Text("+${promo.detalles.size - 4} productos más", color = accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            QuantityStepper(
                quantityText = timesText,
                onQuantityTextChange = { timesText = sanitizeQuantityInput(it) },
                onDecrease = { timesText = ((timesText.toIntOrNull() ?: 1) - 1).coerceAtLeast(1).toString() },
                onIncrease = { timesText = ((timesText.toIntOrNull() ?: 0) + 1).coerceAtLeast(1).toString() },
                onDone = { if (times >= 1) onAddPromotion(times) },
                isError = timesText.isNotBlank() && times < 1,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(10.dp))
            Button(
                onClick = { if (times >= 1) onAddPromotion(times) },
                enabled = times >= 1,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = MaterialTheme.shapes.medium,
                colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = PosPalette.FixedWhite),
            ) {
                Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Agregar promoción x${times.coerceAtLeast(1)}", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun BottomPillItem(
    selected: Boolean,
    onClick: () -> Unit,
    icon: ImageVector,
    label: String,
) {
    val bg = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f) else PosPalette.Transparent
    val tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline

    Surface(
        color = bg,
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(
            modifier = Modifier.clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 5.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(24.dp))
            Text(
                text = label,
                fontSize = 10.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                maxLines = 1,
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
fun ProductCard(
    product: DashboardProduct,
    onAddClick: () -> Unit,
    onQuantityClick: () -> Unit,
) {
    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth().height(240.dp),
    ) {
        Column(modifier = Modifier.padding(12.dp).fillMaxSize()) {
            // Imagen flexible: absorbe el alto restante para que la tarjeta nunca
            // desborde en columnas estrechas (320dp → 2 columnas de ~138dp).
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clip(MaterialTheme.shapes.small)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                if (!product.imageUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = product.imageUrl,
                        contentDescription = product.name,
                        modifier = Modifier.fillMaxSize(),
                        onError = { SafeLog.w("ProductImage", "Dashboard product image load failed") },
                        onSuccess = { SafeLog.d("ProductImage", "Dashboard product image loaded") },
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Inventory,
                        contentDescription = "Sin imagen",
                        tint = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(44.dp),
                    )
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = product.name,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (!product.code.isNullOrBlank()) {
                Text(
                    text = "Ref: ${product.code}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            // Precio en su propia fila (adaptive: montos grandes encogen sin recortarse).
            AdaptiveAmountText(
                text = "$${String.format(java.util.Locale.getDefault(), "%.2f", product.price)}",
                baseStyle = PosTextStyles.priceTileLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.fillMaxWidth(),
                options =
                    com.amaxonia.pos.ui.common.components.AdaptiveAmountOptions(
                        minFontSizeSp = 12f,
                        maxLines = 1,
                    ),
            )
            Spacer(modifier = Modifier.height(6.dp))
            // Acciones alineadas a la derecha; targets ≥48dp vía minimum interactive.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = onQuantityClick,
                    modifier =
                        Modifier
                            .size(40.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.small),
                ) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = "Elegir cantidad",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp),
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(
                    onClick = onAddClick,
                    modifier =
                        Modifier
                            .size(40.dp)
                            .background(MaterialTheme.colorScheme.primary, MaterialTheme.shapes.small),
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = "Agregar una unidad",
                        tint = PosPalette.FixedWhite,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}

@Composable
fun ProductListRow(
    product: DashboardProduct,
    onAddClick: () -> Unit,
    onQuantityClick: () -> Unit,
) {
    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier =
                    Modifier
                        .size(56.dp)
                        .clip(MaterialTheme.shapes.small)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                if (!product.imageUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = product.imageUrl,
                        contentDescription = product.name,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Inventory,
                        contentDescription = "Sin imagen",
                        tint = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(28.dp),
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = product.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!product.code.isNullOrBlank()) {
                    Text(
                        text = "Ref: ${product.code}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                } else {
                    Spacer(Modifier.height(4.dp))
                }
                AdaptiveAmountText(
                    text = "$${String.format(java.util.Locale.getDefault(), "%.2f", product.price)}",
                    baseStyle = PosTextStyles.priceTileLarge,
                    color = MaterialTheme.colorScheme.primary,
                    options =
                        com.amaxonia.pos.ui.common.components.AdaptiveAmountOptions(
                            minFontSizeSp = 13f,
                            maxLines = 1,
                        ),
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onQuantityClick,
                    modifier =
                        Modifier
                            .size(40.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.small),
                ) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = "Elegir cantidad",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp),
                    )
                }
                IconButton(
                    onClick = onAddClick,
                    modifier =
                        Modifier
                            .size(40.dp)
                            .background(MaterialTheme.colorScheme.primary, MaterialTheme.shapes.small),
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = "Agregar una unidad",
                        tint = PosPalette.FixedWhite,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}

@Composable
fun DrawerMenuItem(
    icon: ImageVector,
    label: String,
    isSelected: Boolean = false,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable { onClick() }
                .padding(vertical = 16.dp, horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = label, tint = PosPalette.FixedWhite, modifier = Modifier.size(24.dp))
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = label,
            color = PosPalette.FixedWhite,
            fontSize = 16.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
        )
    }
}

@Composable
fun ManualEntryContent(
    currentValue: String,
    onKeyClick: (String) -> Unit,
    onClearClick: () -> Unit,
    onBackspaceClick: () -> Unit,
    onEnterClick: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Título de la sección
        Text(
            text = "Ingreso Manual",
            style =
                TextStyle(
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                ),
            modifier =
                Modifier
                    .align(Alignment.Start)
                    .padding(bottom = 24.dp),
        )

        // Pantalla del precio (Visor) — monto adaptive para que importes largos no se corten.
        Card(
            shape = MaterialTheme.shapes.medium,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = "Monto a cobrar",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(4.dp))
                AdaptiveAmountText(
                    text = if (currentValue.isEmpty()) "$ 0.00" else "$ $currentValue",
                    baseStyle =
                        TextStyle(
                            fontSize = 36.sp,
                            fontWeight = FontWeight.ExtraBold,
                        ),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.fillMaxWidth(),
                    options =
                        com.amaxonia.pos.ui.common.components.AdaptiveAmountOptions(
                            minFontSizeSp = 18f,
                            maxLines = 1,
                        ),
                )
            }
        }

        // Teclado Numérico (el bottomBar ya reserva su espacio vía paddingValues;
        // sin padding extra que desperdicie pantalla).
        Row(
            modifier =
                Modifier
                    .weight(1f)
                    .padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // Columna Izquierda (Números)
            Column(
                modifier = Modifier.weight(3f),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                val keys =
                    listOf(
                        listOf("1", "2", "3"),
                        listOf("4", "5", "6"),
                        listOf("7", "8", "9"),
                        listOf("C", "0", "000"),
                    )

                keys.forEach { row ->
                    Row(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        row.forEach { key ->
                            Button(
                                onClick = {
                                    if (key == "C") onClearClick() else onKeyClick(key)
                                },
                                shape = MaterialTheme.shapes.small,
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surface),
                                elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp),
                                contentPadding = PaddingValues(0.dp),
                                modifier =
                                    Modifier
                                        .weight(1f)
                                        .fillMaxHeight(),
                            ) {
                                Text(
                                    text = key,
                                    fontSize = if (key == "000") 20.sp else 24.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (key == "C") PosPalette.DangerRed else MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    }
                }
            }

            // Columna Derecha (Acciones)
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                // Botón Borrar
                Button(
                    onClick = onBackspaceClick,
                    shape = MaterialTheme.shapes.small,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp),
                    modifier =
                        Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Backspace,
                        contentDescription = "Borrar",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(26.dp),
                    )
                }

                // Botón ENTER
                Button(
                    onClick = onEnterClick,
                    shape = MaterialTheme.shapes.small,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp),
                    modifier =
                        Modifier
                            .weight(3f)
                            .fillMaxWidth(),
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Cobrar",
                        tint = PosPalette.FixedWhite,
                        modifier = Modifier.size(32.dp),
                    )
                }
            }
        }
    }
}

/**
 * Caja status banner with an urgency hierarchy: the happy path (ABIERTA) stays quiet so it
 * doesn't dominate the screen, while a caja needing attention (PENDIENTE_APERTURA) is prominent
 * with a filled action button.
 */
@Composable
private fun CajaStatusBanner(
    session: CajaSessionStatus,
    cajaName: String,
    onAperturar: () -> Unit,
    onSeleccionar: () -> Unit,
) {
    when (session) {
        CajaSessionStatus.ABIERTA ->
            CajaStatusBannerLow(
                accent = SuccessGreen,
                icon = Icons.Default.CheckCircle,
                text = if (cajaName.isBlank()) "Caja abierta" else "Caja abierta · $cajaName",
            )
        CajaSessionStatus.PENDIENTE_APERTURA ->
            CajaStatusBannerProminent(
                accent = WarningOrange,
                icon = Icons.Default.Lock,
                title = "Caja cerrada · pendiente de apertura",
                subtitle = "Apertura $cajaName para poder facturar",
                actionLabel = "Aperturar",
                onAction = onAperturar,
            )
        CajaSessionStatus.SIN_CAJA ->
            CajaStatusBannerProminent(
                accent = NeutralGray,
                icon = Icons.Default.PointOfSale,
                title = "Sin caja seleccionada",
                subtitle = "Selecciona una caja para comenzar a vender",
                actionLabel = "Seleccionar",
                onAction = onSeleccionar,
            )
        CajaSessionStatus.VERIFICANDO ->
            CajaStatusBannerLoading(accent = InfoBlue)
    }
}

@Composable
private fun CajaStatusBannerLow(
    accent: androidx.compose.ui.graphics.Color,
    icon: ImageVector,
    text: String,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.width(3.dp).height(14.dp).background(accent, MaterialTheme.shapes.extraSmall))
        Spacer(modifier = Modifier.width(8.dp))
        Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun CajaStatusBannerLoading(accent: androidx.compose.ui.graphics.Color) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(accent.copy(alpha = 0.08f))
                .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(color = accent, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            "Verificando caja…",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
// Firma mantiene contenido y acción explícitos del banner reutilizado.
@Suppress("LongParameterList")
private fun CajaStatusBannerProminent(
    accent: androidx.compose.ui.graphics.Color,
    icon: ImageVector,
    title: String,
    subtitle: String,
    actionLabel: String,
    onAction: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(accent.copy(alpha = 0.14f))
                .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(22.dp))
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = accent, style = MaterialTheme.typography.titleSmall)
            Text(
                subtitle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Button(
            onClick = onAction,
            shape = MaterialTheme.shapes.small,
            colors = ButtonDefaults.buttonColors(containerColor = accent),
        ) {
            Text(actionLabel, color = PosPalette.FixedWhite, style = MaterialTheme.typography.labelLarge)
        }
    }
}

/**
 * Diálogo de confirmación de apertura de caja con monto de efectivo opcional.
 */
@Composable
private fun AperturaCajaDialog(
    caja: Caja,
    isLoading: Boolean,
    onConfirm: (Double) -> Unit,
    onDismiss: () -> Unit,
) {
    var amount by remember { mutableStateOf(0.0) }
    val cajaLabel = caja.caja ?: caja.descripcion ?: "seleccionada"
    AlertDialog(
        onDismissRequest = { if (!isLoading) onDismiss() },
        title = { Text("Aperturar caja") },
        text = {
            Column {
                Text(
                    "¿Deseas aperturar la caja \"$cajaLabel\"? Necesitas una caja abierta para poder facturar.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 14.sp,
                )
                Spacer(modifier = Modifier.height(12.dp))
                PosMoneyInput(
                    label = "Monto de efectivo de apertura (opcional)",
                    value = amount,
                    onValueChange = { amount = it },
                )
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(amount) }, enabled = !isLoading) {
                Text(if (isLoading) "Aperturando…" else "Aperturar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isLoading) { Text("Cancelar") }
        },
    )
}
