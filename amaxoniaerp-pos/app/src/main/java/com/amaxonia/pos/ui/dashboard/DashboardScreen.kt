package com.amaxonia.pos.ui.dashboard

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.AssignmentReturn
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.KeyboardArrowDown
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
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
import com.amaxonia.pos.R
import com.amaxonia.pos.composition.AppGraph
import com.amaxonia.pos.core.logging.SafeLog
import com.amaxonia.pos.domain.model.caja.Caja
import com.amaxonia.pos.domain.model.caja.CajaSessionStatus
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
import com.amaxonia.pos.ui.theme.SuccessGreen
import com.amaxonia.pos.ui.theme.WarningOrange
import kotlinx.coroutines.launch

private const val DASHBOARD_LOG_TAG = "DashboardScreen"

/** Ítems que faltan por debajo del último visible para disparar la carga de más productos. */
private const val PREFETCH_THRESHOLD_ITEMS = 6

/** Máximo de dígitos aceptados al escribir una cantidad manual. */
private const val MAX_QUANTITY_DIGITS = 5

@OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
// Raíz Compose conserva navegación y estado local; dividirla alteraría alcance de remember/effects.
@Suppress("LongParameterList", "LongMethod", "CyclomaticComplexMethod")
fun DashboardScreen(
    viewModel: DashboardViewModel =
        injectedViewModel {
            AppGraph.dashboard.dashboardViewModel()
        },
    onLogout: () -> Unit,
    onNavigateToClients: () -> Unit,
    onNavigateToProducts: () -> Unit,
    onNavigateToHistory: () -> Unit,
    onNavigateToCreditNotes: () -> Unit,
    onNavigateToReports: () -> Unit,
    onNavigateToPrinterSettings: () -> Unit,
    onNavigateToOfflineSettings: () -> Unit = {},
    onNavigateToCart: () -> Unit,
    onStartNewOrder: () -> Unit,
    onNavigateToCierreCaja: () -> Unit = {},
    onNavigateToDraftInvoices: () -> Unit = {},
    onNavigateToAreasMesas: () -> Unit = {},
    onNavigateToComanda: (areaId: Int, mesaId: Int, sesionId: Int) -> Unit = { _, _, _ -> },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val selectedTable by AppGraph.mesas.selectedTableHolder.selectedTable
        .collectAsStateWithLifecycle()
    val sesionMesaId by AppGraph.mesas.sesionMesaIdState.collectAsStateWithLifecycle()
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
    val pendingApertura by AppGraph.dashboard.pendingAperturaRequest.collectAsStateWithLifecycle()
    LaunchedEffect(pendingApertura) {
        if (pendingApertura) {
            viewModel.onAction(DashboardCajaUiAction.RequestAperturaActive)
            AppGraph.dashboard.consumeAperturaRequest()
        }
    }
    // Abre el selector de caja cuando otra pantalla lo solicita (p. ej. "Áreas y mesas" sin caja).
    val pendingCajaSelector by AppGraph.dashboard.pendingCajaSelectorRequest.collectAsStateWithLifecycle()
    LaunchedEffect(pendingCajaSelector) {
        if (pendingCajaSelector) {
            viewModel.onAction(DashboardCajaUiAction.Fetch(forceShowSelector = true))
            AppGraph.dashboard.consumeCajaSelectorRequest()
        }
    }
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current
    var showSellerSheet by remember { mutableStateOf(false) }
    val productGridState = rememberLazyGridState()
    val productListState = rememberLazyListState()
    val manualSyncInfos by AppGraph.sync.manualSyncWorkInfos(context).observeAsState(emptyList())
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
            lastVisible >= productGridState.layoutInfo.totalItemsCount - PREFETCH_THRESHOLD_ITEMS
        }
    }

    val listReachedBottom by remember {
        derivedStateOf {
            val lastVisible =
                productListState.layoutInfo.visibleItemsInfo
                    .lastOrNull()
                    ?.index ?: return@derivedStateOf false
            lastVisible >= productListState.layoutInfo.totalItemsCount - PREFETCH_THRESHOLD_ITEMS
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
            actions =
                CajaSelectorActions(
                    onSelectCaja = { caja -> viewModel.onAction(DashboardCajaUiAction.RequestApertura(caja)) },
                    onReload = { viewModel.onAction(DashboardCajaUiAction.Fetch()) },
                    onDismiss = {
                        if (state.hasActiveCaja) {
                            viewModel.onAction(DashboardCajaUiAction.SetSelectorVisible(false))
                        }
                    },
                ),
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
                    DrawerMenuItem(Icons.Default.CloudSync, "Ajustes Offline") {
                        scope.launch {
                            drawerState.close()
                            onNavigateToOfflineSettings()
                        }
                    }
                    DrawerMenuItem(Icons.Default.Lock, "Cerrar Caja") {
                        scope.launch {
                            drawerState.close()
                            onNavigateToCierreCaja()
                        }
                    }
                    DrawerMenuItem(Icons.Default.Refresh, "Actualizar datos") {
                        AppGraph.sync.enqueueManual(context)
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
                                    // Same Throwable boundary as the original catch: defensive
                                    // last-resort guard around Compose drawer state transitions.
                                    runCatching {
                                        SafeLog.d(DASHBOARD_LOG_TAG, "Drawer requested")
                                        if (drawerState.isClosed) {
                                            drawerState.snapTo(DrawerValue.Open)
                                            SafeLog.d(DASHBOARD_LOG_TAG, "Drawer opened")
                                        } else {
                                            SafeLog.d(DASHBOARD_LOG_TAG, "Drawer open request ignored")
                                        }
                                    }.onFailure { throwable ->
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

                val activeTable = selectedTable
                val activeSesion = sesionMesaId
                if (activeTable != null && activeSesion != null) {
                    MesaActiveOrderBanner(
                        mesaName = activeTable.mesa.displayName,
                        areaName = activeTable.area.displayName,
                        onReturnToComanda = {
                            onNavigateToComanda(activeTable.area.id, activeTable.mesa.id, activeSesion)
                        },
                    )
                }

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

internal fun sanitizeQuantityInput(value: String): String =
    value
        .filter { it.isDigit() }
        .trimStart('0')
        .ifBlank { "" }
        .take(MAX_QUANTITY_DIGITS)

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

@Composable
private fun MesaActiveOrderBanner(
    mesaName: String,
    areaName: String,
    onReturnToComanda: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        shape = RoundedCornerShape(12.dp),
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f),
            ) {
                Icon(
                    imageVector = Icons.Default.TableRestaurant,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(22.dp),
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "Comanda activa: $mesaName",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (areaName.isNotBlank()) {
                        Text(
                            text = areaName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            FilledTonalButton(
                onClick = onReturnToComanda,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                shape = RoundedCornerShape(8.dp),
            ) {
                Text(
                    "Volver",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
    }
}
