package com.amaxonia.pos.ui.history

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.rounded.Receipt
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.amaxonia.pos.composition.AppGraph
import com.amaxonia.pos.domain.model.Transaction
import com.amaxonia.pos.domain.repository.InvoiceHistoryFilter
import com.amaxonia.pos.ui.common.components.AdaptiveAmountOptions
import com.amaxonia.pos.ui.common.components.AdaptiveAmountText
import com.amaxonia.pos.ui.common.injectedViewModel
import com.amaxonia.pos.ui.theme.PosPalette
import java.util.Locale

@Composable
fun HistoryScreen(
    viewModel: HistoryViewModel = injectedViewModel { AppGraph.history.historyViewModel() },
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    // Detail bottom sheet
    HistoryDetalleSheet(state = state, onDismiss = viewModel::dismissDetalle)

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { HistoryTopAppBar(onBack = onBack) },
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding),
        ) {
            HistoryFiltersSection(state = state, viewModel = viewModel)

            if (!state.isLoading || state.transactions.isNotEmpty()) {
                SummaryBar(
                    totalFacturas = state.summary.totalFacturas,
                    totalMonto = state.summary.ventasNetas,
                    currency = state.summary.moneda,
                )
            }

            HistoryContent(state = state, viewModel = viewModel)
        }
    }
}

/** Hoja inferior con el detalle de la factura seleccionada. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HistoryDetalleSheet(
    state: HistoryState,
    onDismiss: () -> Unit,
) {
    if (!state.showDetalleSheet) return

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        tonalElevation = 0.dp,
    ) {
        FacturaDetalleSheetContent(
            transaction = state.selectedTransaction,
            items = state.detalleItems,
            isLoading = state.isLoadingDetalle,
            error = state.detalleError,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HistoryTopAppBar(onBack: () -> Unit) {
    TopAppBar(
        title = {
            Text(
                "Historial de Facturas",
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
        },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    "Volver",
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
    )
}

/** Búsqueda + filtros avanzados plegables. */
@Composable
private fun HistoryFiltersSection(
    state: HistoryState,
    viewModel: HistoryViewModel,
) {
    // Búsqueda siempre visible; los filtros avanzados se pliegan detrás del toggle para
    // que la lista ocupe la pantalla. Mismos callbacks del ViewModel, ensamblados aquí.
    var filtersExpanded by remember { mutableStateOf(false) }
    HistorySearchField(
        value = state.filter.search.orEmpty(),
        onValueChange = viewModel::onSearchChanged,
        filtersExpanded = filtersExpanded,
        onToggleFilters = { filtersExpanded = !filtersExpanded },
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
    AnimatedVisibility(visible = filtersExpanded) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            HistoryIdentityFilters(
                filter = state.filter,
                onUsuarioChanged = viewModel::onUsuarioChanged,
                onSucursalChanged = viewModel::onSucursalChanged,
            )
            HistoryDateRangeFilters(
                filter = state.filter,
                onFechaInicioChanged = viewModel::onFechaInicioChanged,
                onFechaFinChanged = viewModel::onFechaFinChanged,
            )
            HistoryStatusFilter(
                filter = state.filter,
                onEstatusChanged = viewModel::onEstatusChanged,
            )
            HistoryFilterActions(
                onApply = viewModel::applyFilters,
                onClear = viewModel::clearFilters,
            )
        }
    }
}

/** Estado principal del historial: carga, error, vacío o lista agrupada. */
@Composable
private fun HistoryContent(
    state: HistoryState,
    viewModel: HistoryViewModel,
) {
    when {
        state.isLoading && state.transactions.isEmpty() -> HistoryLoadingState()
        state.error != null -> HistoryErrorState(error = state.error, onRetry = viewModel::retry)
        state.transactions.isEmpty() -> HistoryEmptyState()
        else ->
            HistoryTransactionsList(
                transactions = state.transactions,
                onTransactionClick = viewModel::onTransactionClick,
            )
    }
}

@Composable
private fun HistoryLoadingState() {
    Box(
        Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                "Cargando facturas...",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun HistoryErrorState(
    error: String?,
    onRetry: () -> Unit,
) {
    Box(
        Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        ElevatedCard(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
            shape = RoundedCornerShape(16.dp),
            colors =
                CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                ),
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = error ?: "Error desconocido",
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    fontSize = 14.sp,
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = onRetry) {
                    Text("Reintentar")
                }
            }
        }
    }
}

@Composable
private fun HistoryEmptyState() {
    Box(
        Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Rounded.Receipt,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(64.dp),
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                "No hay facturas registradas",
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Las facturas apareceran aqui una vez que realices ventas",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.outline,
            )
        }
    }
}

/** Lista de transacciones agrupadas por fecha con headers sticky. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HistoryTransactionsList(
    transactions: List<Transaction>,
    onTransactionClick: (Transaction) -> Unit,
) {
    // Group transactions by dateHeader for sticky headers
    val grouped =
        remember(transactions) {
            transactions.groupBy { it.dateHeader }
        }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding =
            PaddingValues(
                start = 16.dp,
                end = 16.dp,
                bottom = 24.dp,
            ),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        grouped.forEach { (dateHeader, dateTransactions) ->
            stickyHeader(key = "header_$dateHeader") {
                DateStickyHeader(date = dateHeader)
            }
            items(
                items = dateTransactions,
                key = { it.id },
            ) { transaction ->
                TransactionCard(
                    transaction = transaction,
                    onClick = { onTransactionClick(transaction) },
                )
            }
        }
    }
}

// ---------- Summary Bar ----------

@Composable
internal fun SummaryBar(
    totalFacturas: Int,
    totalMonto: Double,
    currency: String,
    modifier: Modifier = Modifier,
) {
    ElevatedCard(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 12.dp),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.primary),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 4.dp),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Facturas cargadas",
                    style = MaterialTheme.typography.labelMedium,
                    color = PosPalette.FixedWhite.copy(alpha = 0.7f),
                )
                Text(
                    text = "$totalFacturas",
                    fontWeight = FontWeight.Bold,
                    fontSize = 22.sp,
                    color = PosPalette.FixedWhite,
                )
            }
            Column(horizontalAlignment = Alignment.End, modifier = Modifier.weight(1f)) {
                Text(
                    text = "Monto total",
                    style = MaterialTheme.typography.labelMedium,
                    color = PosPalette.FixedWhite.copy(alpha = 0.7f),
                )
                // Monto adaptive: totales enormes encogen sin recortarse en 320dp.
                AdaptiveAmountText(
                    text = "$currency ${String.format(Locale.getDefault(), "%.2f", totalMonto)}",
                    baseStyle =
                        MaterialTheme.typography.headlineSmall.copy(
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.End,
                        ),
                    color = PosPalette.FixedWhite,
                    modifier = Modifier.fillMaxWidth(),
                    options =
                        AdaptiveAmountOptions(
                            minFontSizeSp = 13f,
                        ),
                )
            }
        }
    }
}

/**
 * Búsqueda siempre visible + toggle de filtros avanzados. El estado de pliegue vive en el
 * caller para que las piezas sean puras y reutilizables en previews.
 */
@Composable
internal fun HistorySearchField(
    value: String,
    onValueChange: (String) -> Unit,
    filtersExpanded: Boolean,
    onToggleFilters: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text("Buscar factura") },
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
        FilledTonalIconButton(
            onClick = onToggleFilters,
            modifier = Modifier.size(48.dp),
        ) {
            Icon(
                imageVector = if (filtersExpanded) Icons.Filled.ExpandLess else Icons.Filled.FilterList,
                contentDescription = if (filtersExpanded) "Ocultar filtros" else "Mostrar filtros",
            )
        }
    }
}

@Composable
internal fun HistoryIdentityFilters(
    filter: InvoiceHistoryFilter,
    onUsuarioChanged: (String) -> Unit,
    onSucursalChanged: (String) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedTextField(
            value = filter.usuario.orEmpty(),
            onValueChange = onUsuarioChanged,
            label = { Text("Usuario") },
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
        OutlinedTextField(
            value = filter.sucursalId?.toString().orEmpty(),
            onValueChange = onSucursalChanged,
            label = { Text("Sucursal") },
            supportingText = { Text("ID numérico") },
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
internal fun HistoryDateRangeFilters(
    filter: InvoiceHistoryFilter,
    onFechaInicioChanged: (String) -> Unit,
    onFechaFinChanged: (String) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedTextField(
            value = filter.fechaInicio.orEmpty(),
            onValueChange = onFechaInicioChanged,
            label = { Text("Desde") },
            supportingText = { Text("AAAA-MM-DD") },
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
        OutlinedTextField(
            value = filter.fechaFin.orEmpty(),
            onValueChange = onFechaFinChanged,
            label = { Text("Hasta") },
            supportingText = { Text("AAAA-MM-DD") },
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
internal fun HistoryStatusFilter(
    filter: InvoiceHistoryFilter,
    onEstatusChanged: (String) -> Unit,
) {
    OutlinedTextField(
        value = filter.estatus.joinToString(","),
        onValueChange = onEstatusChanged,
        label = { Text("Estatus") },
        supportingText = { Text("Códigos separados por coma") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
internal fun HistoryFilterActions(
    onApply: () -> Unit,
    onClear: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        OutlinedButton(
            onClick = onClear,
            modifier = Modifier.weight(1f).height(48.dp),
        ) {
            Text("Limpiar")
        }
        Button(
            onClick = onApply,
            modifier = Modifier.weight(1f).height(48.dp),
        ) {
            Text("Aplicar")
        }
    }
}
