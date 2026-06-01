package com.amaxonia.pos.ui.dashboard

import android.util.Log
import coil.compose.AsyncImage
import coil.compose.SubcomposeAsyncImage
import coil.compose.SubcomposeAsyncImageContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.AssignmentReturn
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amaxonia.pos.R
import com.amaxonia.pos.data.sync.SyncScheduler
import com.amaxonia.pos.domain.model.Promocion
import com.amaxonia.pos.domain.usecase.BigDecimalMoneyFormatter
import com.amaxonia.pos.ui.common.DependencyContainer
import com.amaxonia.pos.ui.common.SellerSelectorBottomSheet
import com.amaxonia.pos.ui.common.injectedViewModel
import com.amaxonia.pos.ui.common.shortName
import com.amaxonia.pos.ui.theme.AmaxoniaBlue
import androidx.work.WorkInfo
import kotlinx.coroutines.launch

private const val DASHBOARD_LOG_TAG = "DashboardScreen"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel = injectedViewModel {
            DashboardViewModel(
                DependencyContainer.productRepository,
                DependencyContainer.promotionRepository,
                DependencyContainer.reportRepository,
            DependencyContainer.cartRepository,
            DependencyContainer.cajaRepository,
            DependencyContainer.localStore,
            DependencyContainer.apiConfigManager
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
    onNavigateToDraftInvoices: () -> Unit = {}
) {
    val state by viewModel.state.collectAsState()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current
    var showSellerSheet by remember { mutableStateOf(false) }
    val productGridState = rememberLazyGridState()
    val productListState = rememberLazyListState()
    val isOnline by DependencyContainer.networkMonitor.isOnlineFlow.collectAsState(
        initial = DependencyContainer.networkMonitor.isOnline()
    )
    var hasSeenConnectivityState by remember { mutableStateOf(false) }
    val manualSyncInfos by SyncScheduler.getManualSyncWorkInfos(context).observeAsState(emptyList())
    val isSyncRunning = manualSyncInfos.any { info ->
        info.state == WorkInfo.State.RUNNING || info.state == WorkInfo.State.ENQUEUED
    }

    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current

    DisposableEffect(Unit) {
        Log.d(DASHBOARD_LOG_TAG, "Dashboard composed")
        onDispose {
            Log.d(DASHBOARD_LOG_TAG, "Dashboard disposed")
        }
    }

    val productsToShow: List<DashboardProduct> = if (state.bottomSelected == 1) state.bestSellers else state.products

    val gridReachedBottom by remember {
        derivedStateOf {
            val lastVisible = productGridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: return@derivedStateOf false
            lastVisible >= productGridState.layoutInfo.totalItemsCount - 6
        }
    }

    val listReachedBottom by remember {
        derivedStateOf {
            val lastVisible = productListState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: return@derivedStateOf false
            lastVisible >= productListState.layoutInfo.totalItemsCount - 6
        }
    }

    LaunchedEffect(gridReachedBottom, listReachedBottom, state.viewMode, state.bottomSelected) {
        if (state.bottomSelected == 0 && (gridReachedBottom || listReachedBottom)) {
            viewModel.loadMoreProducts()
        }
    }

    // --- Snackbar for auto-close notifications ---
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(state.autoCloseMessage) {
        state.autoCloseMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg, duration = SnackbarDuration.Long)
            viewModel.dismissAutoCloseMessage()
        }
    }

    LaunchedEffect(state.promotionMessage) {
        val msg = state.promotionMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(msg, duration = SnackbarDuration.Short)
        viewModel.clearPromotionMessage()
    }

    LaunchedEffect(isOnline) {
        if (!hasSeenConnectivityState) {
            hasSeenConnectivityState = true
            if (!isOnline) {
                snackbarHostState.showSnackbar(
                    message = "Sin conexión. Puedes seguir trabajando offline.",
                    duration = SnackbarDuration.Long
                )
            }
            return@LaunchedEffect
        }

        snackbarHostState.showSnackbar(
            message = if (isOnline) {
                SyncScheduler.enqueuePendingInvoices(context)
                "Conexión restaurada. Reenviando pendientes..."
            } else {
                "Sin conexión. Puedes seguir trabajando offline."
            },
            duration = SnackbarDuration.Long
        )
    }

    // --- CajaSelectorSheet (replaces old AlertDialog) ---
    if (state.showCajaSelector) {
        CajaSelectorSheet(
            cajas = state.availableCajas,
            isLoading = state.isLoadingCajas,
            errorMessage = state.error,
            onSelectCaja = { caja -> viewModel.selectAndOpenCaja(caja, 0.0) },
            onReload = { viewModel.fetchAvailableCajas() },
            onDismiss = {
                // Allow dismiss only if a caja was already selected
                if (state.cajaPrincipalNombre != "Caja no seleccionada") {
                    viewModel.setShowCajaSelector(false)
                }
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

    if (state.showPromotionChoice && state.pendingPromotionProduct != null) {
        PromotionChoiceSheet(
            product = state.pendingPromotionProduct!!,
            promotions = state.promotionOptions,
            onAddIndividual = viewModel::addProductIndividualFromPromotionChoice,
            onAddPromotion = viewModel::addPromotionFromChoice,
            onDismiss = viewModel::dismissPromotionChoice
        )
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = AmaxoniaBlue,
                drawerContentColor = Color.White,
                modifier = Modifier.width(300.dp).fillMaxHeight()
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(
                            painter = painterResource(id = R.drawable.ic_amaxonia),
                            contentDescription = "Logo Amaxonia",
                            modifier = Modifier
                                .size(40.dp)
                                .background(Color.White, RoundedCornerShape(8.dp))
                                .padding(4.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("Amaxonia ERP", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.weight(1f))
                        Surface(shape = RoundedCornerShape(50), color = Color.White) {
                            Text(
                                "Pro+",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                color = AmaxoniaBlue,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(20.dp))
                    Text(state.sucursalNombre, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(10.dp))
                    Surface(
                        color = Color.White.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth().clickable {
                            scope.launch { 
                                drawerState.close() 
                                viewModel.fetchAvailableCajas(forceShowSelector = true)
                            }
                        }
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(state.cajaPrincipalNombre, color = Color.White, modifier = Modifier.weight(1f))
                            Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Cambiar caja", tint = Color.White)
                        }
                    }
                }
                HorizontalDivider(color = Color.White.copy(alpha = 0.2f))
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
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
                    DrawerMenuItem(Icons.AutoMirrored.Filled.ListAlt, "Crear Pedido") {
                        scope.launch { 
                            drawerState.close()
                            viewModel.startNewOrder() // Limpia carrito
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
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ExitToApp, null, tint = AmaxoniaBlue, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Cerrar Sesión", color = AmaxoniaBlue, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
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
                        containerColor = when {
                            isOnlineMessage -> Color(0xFF16A34A)
                            isOfflineMessage -> Color(0xFFDC2626)
                            else -> MaterialTheme.colorScheme.inverseSurface
                        },
                        contentColor = when {
                            isOnlineMessage || isOfflineMessage -> Color.White
                            else -> MaterialTheme.colorScheme.inverseOnSurface
                        },
                        actionColor = Color.White,
                        shape = RoundedCornerShape(18.dp)
                    )
                }
            },
            topBar = {
                TopAppBar(
                    title = {
                        if (state.isSearchOpen) {
                            TextField(
                                value = state.searchQuery,
                                onValueChange = viewModel::setSearchQuery,
                                placeholder = { Text("Buscar...", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                                singleLine = true,
                                textStyle = TextStyle(fontSize = 16.sp, color = AmaxoniaBlue),
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                                    focusedTextColor = AmaxoniaBlue,
                                    unfocusedTextColor = AmaxoniaBlue,
                                    cursorColor = AmaxoniaBlue,
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent
                                ),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp)
                            )
                        } else {
                            Text("Punto de Venta", color = AmaxoniaBlue, fontWeight = FontWeight.Bold)
                        }
                    },
                    navigationIcon = {
                        IconButton(
                            onClick = {
                            focusManager.clearFocus()
                            scope.launch {
                                try {
                                    Log.d(DASHBOARD_LOG_TAG, "Drawer requested. current=${drawerState.currentValue}, target=${drawerState.targetValue}")
                                    if (drawerState.isClosed) {
                                        drawerState.snapTo(DrawerValue.Open)
                                        Log.d(DASHBOARD_LOG_TAG, "Drawer opened. current=${drawerState.currentValue}, target=${drawerState.targetValue}")
                                    } else {
                                        Log.d(DASHBOARD_LOG_TAG, "Drawer open ignored. current=${drawerState.currentValue}, target=${drawerState.targetValue}")
                                    }
                                } catch (throwable: Throwable) {
                                    Log.e(DASHBOARD_LOG_TAG, "Error opening drawer. current=${drawerState.currentValue}, target=${drawerState.targetValue}", throwable)
                                    runCatching { drawerState.close() }
                                }
                            }
                        }) {
                            Icon(Icons.Default.Menu, contentDescription = "Menu", tint = AmaxoniaBlue)
                        }
                    },
                    actions = {
                        IconButton(onClick = { viewModel.toggleSearch() }) {
                            Icon(
                                imageVector = if (state.isSearchOpen) Icons.Default.Close else Icons.Default.Search,
                                contentDescription = "Buscar",
                                tint = AmaxoniaBlue
                            )
                        }
                        IconButton(onClick = { /* TODO */ }) {
                            Icon(Icons.Default.DocumentScanner, contentDescription = "Escanear código", tint = AmaxoniaBlue)
                        }
                        IconButton(onClick = { viewModel.toggleViewMode() }) {
                            Icon(
                                imageVector = if (state.viewMode == ProductViewMode.GRID) Icons.AutoMirrored.Filled.ViewList else Icons.Default.GridView,
                                contentDescription = "Cambiar vista",
                                tint = AmaxoniaBlue
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
                )
            },
            bottomBar = {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(bottom = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(26.dp),
                        tonalElevation = 0.dp,
                        shadowElevation = 8.dp,
                        modifier = Modifier
                            .padding(horizontal = 18.dp)
                            .height(58.dp)
                            .fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            BottomPillItem(
                                selected = state.bottomSelected == 0,
                                onClick = { viewModel.setBottomSelected(0) },
                                icon = Icons.Default.GridView
                            )
                            BottomPillItem(
                                selected = state.bottomSelected == 1,
                                onClick = { viewModel.setBottomSelected(1) },
                                icon = Icons.Default.StarBorder
                            )
                            BottomPillItem(
                                selected = state.bottomSelected == 2,
                                onClick = { viewModel.setBottomSelected(2) },
                                icon = Icons.Default.EditNote
                            )
                        }
                    }
                }
            }
        ) { paddingValues ->
            Column(modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)) {
                if (isSyncRunning) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(
                                color = AmaxoniaBlue,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Sincronizando datos...",
                                style = TextStyle(fontSize = 12.sp, color = AmaxoniaBlue)
                            )
                        }
                    }
                }

                // --- Barra de Cliente Seleccionado ---
                if (state.selectedClient != null) {
                    val clientPhotoUrl = viewModel.getClientPhotoUrl(state.selectedClient!!)
                    Surface(
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                                .fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                ) {
                                    SelectedClientAvatar(
                                        clientPhotoUrl = clientPhotoUrl,
                                        clientName = "${state.selectedClient!!.firstName} ${state.selectedClient!!.lastName}"
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = "Cliente seleccionado:",
                                        style = TextStyle(fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    )
                                    Text(
                                        text = "${state.selectedClient!!.firstName} ${state.selectedClient!!.lastName}",
                                        style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.Bold, color = AmaxoniaBlue)
                                    )
                                }
                            }
                            IconButton(onClick = { viewModel.clearSelectedClient() }) {
                                Icon(Icons.Default.Close, contentDescription = "Quitar cliente", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }

                // --- Contenido Principal ---
                Box(modifier = Modifier.weight(1f)) {
                    if (state.error != null) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = state.error ?: "Error desconocido",
                                    color = MaterialTheme.colorScheme.error
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Button(onClick = { viewModel.retry() }) {
                                    Text("Reintentar")
                                }
                            }
                        }
                    } else if ((state.isLoading && state.products.isEmpty()) || (state.isLoadingBestSellers && state.bottomSelected == 1 && state.bestSellers.isEmpty())) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = AmaxoniaBlue)
                        }
                    } else {
                        // LOGICA PARA MOSTRAR CONTENIDO SEGUN PESTAÑA
                        if (state.bottomSelected == 2) {
                            // PESTAÑA 3: Entrada Manual
                            ManualEntryContent(
                                currentValue = state.manualEntryValue,
                                onKeyClick = viewModel::onManualKey,
                                onClearClick = viewModel::onManualClear,
                                onBackspaceClick = viewModel::onManualBackspace,
                                onEnterClick = viewModel::onManualSubmit
                            )
                        } else {
                            // PESTAÑAS 0 y 1: Grid o Lista de Productos
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 16.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 16.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable(enabled = state.bottomSelected != 1) {
                                                if (state.bottomSelected != 1) {
                                                    viewModel.setShowDepartmentPicker(true)
                                                }
                                            }
                                            .padding(vertical = 4.dp, horizontal = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = if (state.bottomSelected == 1) "Productos Más Vendidos" else state.selectedCategory,
                                            fontSize = 16.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        if (state.bottomSelected != 1) {
                                            Icon(
                                                Icons.Default.KeyboardArrowDown,
                                                contentDescription = "Cambiar Categoría",
                                                tint = MaterialTheme.colorScheme.onSurface,
                                                modifier = Modifier.padding(start = 8.dp)
                                            )
                                        }
                                    }

                                    Surface(
                                        modifier = Modifier
                                            .padding(start = 8.dp)
                                            .clickable { showSellerSheet = true },
                                        color = MaterialTheme.colorScheme.surface,
                                        shape = RoundedCornerShape(20.dp),
                                        shadowElevation = 1.dp,
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(20.dp))
                                                .padding(horizontal = 10.dp, vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Person,
                                                contentDescription = "Vendedor",
                                                tint = AmaxoniaBlue,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Text(
                                                text = state.currentSeller?.shortName() ?: "Vendedor",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                modifier = Modifier.padding(start = 6.dp)
                                            )
                                        }
                                    }
                                }
                                if (state.showDepartmentPicker) {
                                    ModalBottomSheet(
                                        onDismissRequest = { viewModel.setShowDepartmentPicker(false) }
                                    ) {
                                        LazyColumn(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 16.dp)
                                                .padding(bottom = 32.dp)
                                        ) {
                                            item {
                                                Text(
                                                    "Filtrar por departamento",
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 18.sp,
                                                    color = MaterialTheme.colorScheme.onSurface,
                                                    modifier = Modifier.padding(vertical = 8.dp)
                                                )
                                            }
                                            item {
                                                Surface(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .clickable { viewModel.selectDepartment(null) },
                                                    color = if (state.selectedDepartmentId == null) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f) else Color.Transparent
                                                ) {
                                                    Text(
                                                        "Todos",
                                                        modifier = Modifier.padding(16.dp),
                                                        fontSize = 16.sp
                                                    )
                                                }
                                            }
                                            items(state.departments, key = { it.id }) { dept ->
                                                Surface(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .clickable { viewModel.selectDepartment(dept.id) },
                                                    color = if (state.selectedDepartmentId == dept.id) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f) else Color.Transparent
                                                ) {
                                                    Text(
                                                        dept.name,
                                                        modifier = Modifier.padding(16.dp),
                                                        fontSize = 16.sp
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                                 if (state.viewMode == ProductViewMode.GRID) {
                                     LazyVerticalGrid(
                                        state = productGridState,
                                         columns = GridCells.Fixed(2),
                                         horizontalArrangement = Arrangement.spacedBy(12.dp),
                                         verticalArrangement = Arrangement.spacedBy(12.dp),
                                         contentPadding = PaddingValues(bottom = 120.dp)
                                     ) {
                                        items(productsToShow, key = { it.id }) { product ->
                                             ProductCard(product = product, onAddClick = { viewModel.addToCart(product) })
                                         }
                                        if (state.isLoadingMore) {
                                            item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(16.dp),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    CircularProgressIndicator(color = AmaxoniaBlue, modifier = Modifier.size(28.dp))
                                                }
                                            }
                                        }
                                     }
                                 } else {
                                     LazyColumn(
                                        state = productListState,
                                         verticalArrangement = Arrangement.spacedBy(12.dp),
                                         contentPadding = PaddingValues(bottom = 120.dp)
                                     ) {
                                        items(productsToShow, key = { it.id }) { product ->
                                             ProductListRow(product = product, onAddClick = { viewModel.addToCart(product) })
                                         }
                                        if (state.isLoadingMore) {
                                            item {
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(16.dp),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    CircularProgressIndicator(color = AmaxoniaBlue, modifier = Modifier.size(28.dp))
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
                            onClick = { onNavigateToCart() },
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = AmaxoniaBlue),
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(16.dp)
                                .fillMaxWidth()
                                .height(56.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.ShoppingCart, null, tint = Color.White)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("${state.cartItemCount} productos", color = Color.White)
                                }
                                Text("Total: $${String.format("%.2f", state.cartTotal)}", fontWeight = FontWeight.Bold, color = Color.White)
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
private fun PromotionChoiceSheet(
    product: DashboardProduct,
    promotions: List<Promocion>,
    onAddIndividual: () -> Unit,
    onAddPromotion: (Promocion) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        dragHandle = { BottomSheetDefaults.DragHandle() },
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 18.dp)
                .padding(bottom = 22.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(
                                Brush.linearGradient(
                                    listOf(
                                        MaterialTheme.colorScheme.primary,
                                        MaterialTheme.colorScheme.tertiary
                                    )
                                )
                            )
                    ) {
                        Icon(Icons.Default.LocalOffer, contentDescription = null, tint = Color.White, modifier = Modifier.align(Alignment.Center))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Este producto tiene promoción", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        Text(product.name, maxLines = 2, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.78f), fontSize = 13.sp)
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            OutlinedButton(
                onClick = onAddIndividual,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary)
            ) {
                Icon(Icons.Default.ShoppingCart, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Vender producto individual", fontWeight = FontWeight.Bold)
            }

            Spacer(Modifier.height(14.dp))
            Text("Promociones disponibles", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))

            LazyColumn(
                modifier = Modifier.heightIn(max = 420.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(promotions, key = { it.id }) { promo ->
                    PromotionOptionCard(promo = promo, onAddPromotion = { onAddPromotion(promo) })
                }
            }
        }
    }
}

@Composable
private fun PromotionOptionCard(
    promo: Promocion,
    onAddPromotion: () -> Unit
) {
    val accent = if (promo.tipo == "KIT") MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Surface(color = accent.copy(alpha = 0.12f), shape = RoundedCornerShape(999.dp)) {
                    Text(
                        text = promo.tipo,
                        color = accent,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
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
            Surface(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), shape = RoundedCornerShape(16.dp)) {
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
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(BigDecimalMoneyFormatter.money(detail.totalConIva), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    if (promo.detalles.size > 4) {
                        Text("+${promo.detalles.size - 4} productos más", color = accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            Button(
                onClick = onAddPromotion,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = Color.White)
            ) {
                Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Agregar promoción", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun BottomPillItem(
    selected: Boolean,
    onClick: () -> Unit,
    icon: ImageVector
) {
    val bg = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f) else Color.Transparent
    val tint = if (selected) AmaxoniaBlue else MaterialTheme.colorScheme.outline

    Surface(
        color = bg,
        shape = RoundedCornerShape(18.dp)
    ) {
        IconButton(
            onClick = onClick,
            modifier = Modifier.size(46.dp)
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(26.dp))
        }
    }
}

@Composable
fun ProductCard(
    product: DashboardProduct,
    onAddClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth().height(260.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp).fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(110.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                if (!product.imageUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = product.imageUrl,
                        contentDescription = product.name,
                        modifier = Modifier.fillMaxSize(),
                        onError = { android.util.Log.e("IMG_DASH", "FAIL url=${product.imageUrl} err=${it.result.throwable?.message}") },
                        onSuccess = { android.util.Log.d("IMG_DASH", "OK url=${product.imageUrl}") }
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Inventory,
                        contentDescription = "Sin imagen",
                        tint = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(48.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = product.name, 
                    fontWeight = FontWeight.Bold, 
                    fontSize = 16.sp, 
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
                if (!product.code.isNullOrBlank()) {
                    Text(
                        text = "Ref: ${product.code}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp),
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "$${String.format("%.2f", product.price)}",
                    fontWeight = FontWeight.Bold,
                    color = AmaxoniaBlue,
                    fontSize = 14.sp
                )
                IconButton(
                    onClick = onAddClick,
                    modifier = Modifier
                        .size(34.dp)
                        .background(AmaxoniaBlue, RoundedCornerShape(10.dp))
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Agregar", tint = Color.White, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

@Composable
fun ProductListRow(
    product: DashboardProduct,
    onAddClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                if (!product.imageUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = product.imageUrl,
                        contentDescription = product.name,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Inventory,
                        contentDescription = "Sin imagen",
                        tint = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = product.name, 
                    fontWeight = FontWeight.Bold, 
                    fontSize = 15.sp, 
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
                if (!product.code.isNullOrBlank()) {
                    Text(
                        text = "Ref: ${product.code}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                } else {
                    Spacer(Modifier.height(6.dp))
                }
                Text(
                    text = "$${String.format("%.2f", product.price)}",
                    fontWeight = FontWeight.Bold,
                    color = AmaxoniaBlue,
                    fontSize = 14.sp
                )
            }
            IconButton(
                onClick = onAddClick,
                modifier = Modifier
                    .size(40.dp)
                    .background(AmaxoniaBlue, RoundedCornerShape(12.dp))
            ) {
                Icon(Icons.Default.Add, null, tint = Color.White, modifier = Modifier.size(22.dp))
            }
        }
    }
}

@Composable
fun DrawerMenuItem(
    icon: ImageVector,
    label: String,
    isSelected: Boolean = false,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 16.dp, horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = label, tint = Color.White, modifier = Modifier.size(24.dp))
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = label,
            color = Color.White,
            fontSize = 16.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
        )
    }
}

@Composable
fun ManualEntryContent(
    currentValue: String,
    onKeyClick: (String) -> Unit,
    onClearClick: () -> Unit,
    onBackspaceClick: () -> Unit,
    onEnterClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Título de la sección
        Text(
            text = "Ingreso Manual",
            style = TextStyle(
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = AmaxoniaBlue
            ),
            modifier = Modifier
                .align(Alignment.Start)
                .padding(bottom = 24.dp)
        )

        // Pantalla del precio (Visor)
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "Monto a cobrar",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = if (currentValue.isEmpty()) "$ 0.00" else "$ $currentValue",
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Bold,
                    color = AmaxoniaBlue
                )
            }
        }

        // Teclado Numérico
        Row(
            modifier = Modifier
                .weight(1f)
                .padding(bottom = 80.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Columna Izquierda (Números)
            Column(
                modifier = Modifier.weight(3f),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                val keys = listOf(
                    listOf("1", "2", "3"),
                    listOf("4", "5", "6"),
                    listOf("7", "8", "9"),
                    listOf("C", "0", "000")
                )

                keys.forEach { row ->
                    Row(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        row.forEach { key ->
                            Button(
                                onClick = {
                                    if (key == "C") onClearClick() else onKeyClick(key)
                                },
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surface),
                                elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                            ) {
                                Text(
                                    text = key,
                                    fontSize = if (key == "000") 20.sp else 24.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (key == "C") Color.Red else AmaxoniaBlue
                                )
                            }
                        }
                    }
                }
            }

            // Columna Derecha (Acciones)
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Botón Borrar
                Button(
                    onClick = onBackspaceClick,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp),
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Backspace,
                        contentDescription = "Borrar",
                        tint = AmaxoniaBlue,
                        modifier = Modifier.size(26.dp)
                    )
                }

                // Botón ENTER
                Button(
                    onClick = onEnterClick,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AmaxoniaBlue),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp),
                    modifier = Modifier
                        .weight(3f)
                        .fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Cobrar",
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun SelectedClientAvatar(
    clientPhotoUrl: String,
    clientName: String,
    modifier: Modifier = Modifier
) {
    val initials = buildDashboardInitials(clientName)
    val gradient = listOf(Color(0xFF1E88E5), Color(0xFF00ACC1))

    Box(
        modifier = modifier
            .fillMaxSize()
            .clip(CircleShape)
            .background(Brush.linearGradient(gradient)),
        contentAlignment = Alignment.Center
    ) {
        if (clientPhotoUrl.isBlank()) {
            Text(initials, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
        } else {
            SubcomposeAsyncImage(
                model = clientPhotoUrl,
                contentDescription = "Foto cliente",
                modifier = Modifier.fillMaxSize(),
                loading = { Text(initials, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp) },
                error = { Text(initials, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp) },
                success = { SubcomposeAsyncImageContent(modifier = Modifier.fillMaxSize()) }
            )
        }
    }
}

private fun buildDashboardInitials(name: String): String {
    val parts = name.trim().split(" ").filter { it.isNotBlank() }
    if (parts.isEmpty()) return "CL"
    return parts.take(2).joinToString(separator = "") { it.first().uppercase() }
}
