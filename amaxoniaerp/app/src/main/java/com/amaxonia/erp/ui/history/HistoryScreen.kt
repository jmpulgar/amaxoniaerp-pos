package com.amaxonia.erp.ui.history

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.rounded.AttachMoney
import androidx.compose.material.icons.rounded.CloudOff
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
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.amaxonia.erp.data.sync.SyncScheduler
import com.amaxonia.erp.domain.model.InvoiceHistoryFilter
import com.amaxonia.erp.domain.model.Transaction
import com.amaxonia.erp.ui.components.AdaptiveAmountOptions
import com.amaxonia.erp.ui.components.AdaptiveAmountText
import com.amaxonia.erp.ui.components.PosDatePickerField
import com.amaxonia.erp.ui.components.isLandscape
import com.amaxonia.erp.ui.theme.PosExtraShapes
import java.util.Locale

@Composable
fun HistoryScreen(
    viewModel: HistoryViewModel,
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val isLandscape = isLandscape()

    // Detail bottom sheet
    HistoryDetalleSheet(
        state = state,
        onDismiss = viewModel::dismissDetalle,
        onResend = viewModel::resendElectronicInvoice,
        onSyncPending = {
            SyncScheduler.enqueuePendingInvoices(context)
            viewModel.showOfflineSyncInitiated()
        },
        onReprint = viewModel::reprintInvoice,
        onDownloadPdf = { transaction -> viewModel.downloadAndOpenPdf(context, transaction) },
    )

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = if (isLandscape) 8.dp else 14.dp),
        ) {
            HistoryHeaderSection(
                state = state,
                viewModel = viewModel,
                isLandscape = isLandscape,
            )

            Spacer(modifier = Modifier.height(if (isLandscape) 8.dp else 10.dp))

            HistoryContent(
                state = state,
                viewModel = viewModel,
                isLandscape = isLandscape,
            )
        }
    }
}

/** Hoja inferior con el detalle de la factura seleccionada. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HistoryDetalleSheet(
    state: HistoryState,
    onDismiss: () -> Unit,
    onResend: (Transaction) -> Unit,
    onSyncPending: () -> Unit,
    onReprint: (Transaction) -> Unit,
    onDownloadPdf: (Transaction) -> Unit,
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
            actionError = state.detalleActionError,
            actionMessage = state.detalleMessage,
            isResending = state.isResendingFE,
            onResend = { state.selectedTransaction?.let(onResend) },
            onSyncPending = onSyncPending,
            isReprinting = state.isReprinting,
            onReprint = { state.selectedTransaction?.let(onReprint) },
            isDownloadingPdf = state.isDownloadingPdf,
            onDownloadPdf = { state.selectedTransaction?.let(onDownloadPdf) },
        )
    }
}

/**
 * Encabezado responsivo:
 * - En horizontal: Búsqueda + Toggle Filtros + Píldoras de Totales en una sola fila compacta.
 * - En vertical: Búsqueda + Toggle Filtros en fila 1; barra compacta de totales en fila 2.
 */
@Composable
private fun HistoryHeaderSection(
    state: HistoryState,
    viewModel: HistoryViewModel,
    isLandscape: Boolean,
) {
    var filtersExpanded by remember { mutableStateOf(false) }

    if (isLandscape) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            HistorySearchField(
                value = state.filter.search.orEmpty(),
                onValueChange = viewModel::onSearchChanged,
                filtersExpanded = filtersExpanded,
                onToggleFilters = { filtersExpanded = !filtersExpanded },
                modifier = Modifier.weight(1f),
            )

            if (!state.isLoading) {
                CompactSummaryPills(
                    totalFacturas = state.summary.totalFacturas,
                    totalMonto = state.summary.ventasNetas,
                    currency = state.summary.moneda,
                )
            }
        }
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            HistorySearchField(
                value = state.filter.search.orEmpty(),
                onValueChange = viewModel::onSearchChanged,
                filtersExpanded = filtersExpanded,
                onToggleFilters = { filtersExpanded = !filtersExpanded },
                modifier = Modifier.fillMaxWidth(),
            )

            if (!state.isLoading) {
                CompactSummaryBar(
                    totalFacturas = state.summary.totalFacturas,
                    totalMonto = state.summary.ventasNetas,
                    currency = state.summary.moneda,
                )
            }
        }
    }

    AnimatedVisibility(visible = filtersExpanded) {
        Column(
            modifier = Modifier.padding(top = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (isLandscape) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    PosDatePickerField(
                        label = "Desde",
                        value = state.filter.fechaInicio.orEmpty(),
                        onValueChange = viewModel::onFechaInicioChanged,
                        modifier = Modifier.weight(1f),
                    )
                    PosDatePickerField(
                        label = "Hasta",
                        value = state.filter.fechaFin.orEmpty(),
                        onValueChange = viewModel::onFechaFinChanged,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedButton(
                        onClick = viewModel::clearFilters,
                        enabled = !state.isLoading,
                        modifier = Modifier.height(44.dp),
                        shape = PosExtraShapes.InputRadius,
                    ) {
                        Text("Limpiar", fontSize = 13.sp)
                    }
                    Button(
                        onClick = viewModel::applyFilters,
                        enabled = !state.isLoading,
                        modifier = Modifier.height(44.dp),
                        shape = PosExtraShapes.InputRadius,
                    ) {
                        if (state.isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                        }
                        Text(if (state.isLoading) "Aplicando..." else "Aplicar", fontSize = 13.sp)
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    PosDatePickerField(
                        label = "Desde",
                        value = state.filter.fechaInicio.orEmpty(),
                        onValueChange = viewModel::onFechaInicioChanged,
                        modifier = Modifier.weight(1f),
                    )
                    PosDatePickerField(
                        label = "Hasta",
                        value = state.filter.fechaFin.orEmpty(),
                        onValueChange = viewModel::onFechaFinChanged,
                        modifier = Modifier.weight(1f),
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(
                        onClick = viewModel::clearFilters,
                        enabled = !state.isLoading,
                        modifier = Modifier.weight(1f).height(42.dp),
                        shape = PosExtraShapes.InputRadius,
                    ) {
                        Text("Limpiar", fontSize = 13.sp)
                    }
                    Button(
                        onClick = viewModel::applyFilters,
                        enabled = !state.isLoading,
                        modifier = Modifier.weight(1f).height(42.dp),
                        shape = PosExtraShapes.InputRadius,
                    ) {
                        if (state.isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                        }
                        Text(if (state.isLoading) "Aplicando..." else "Aplicar", fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

/** Píldoras compactas para mostrar los totales en la misma fila en modo horizontal. */
@Composable
private fun CompactSummaryPills(
    totalFacturas: Int,
    totalMonto: Double,
    currency: String,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Surface(
            shape = PosExtraShapes.InputRadius,
            color = MaterialTheme.colorScheme.surfaceVariant,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Receipt,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "$totalFacturas facturas",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }

        Surface(
            shape = PosExtraShapes.InputRadius,
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Rounded.AttachMoney,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(modifier = Modifier.width(2.dp))
                AdaptiveAmountText(
                    text = "$currency ${String.format(Locale.getDefault(), "%.2f", totalMonto)}",
                    baseStyle = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary,
                    options = AdaptiveAmountOptions(minFontSizeSp = 11f),
                )
            }
        }
    }
}

/** Barra de totales esbelta y compacta para modo vertical (no roba espacio). */
@Composable
private fun CompactSummaryBar(
    totalFacturas: Int,
    totalMonto: Double,
    currency: String,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = PosExtraShapes.InputRadius,
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Rounded.Receipt,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "$totalFacturas facturas",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            AdaptiveAmountText(
                text = "Total: $currency ${String.format(Locale.getDefault(), "%.2f", totalMonto)}",
                baseStyle = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary,
                options = AdaptiveAmountOptions(minFontSizeSp = 11f),
            )
        }
    }
}

/** Campo de búsqueda con estilo estandarizado del ERP. */
@Composable
internal fun HistorySearchField(
    value: String,
    onValueChange: (String) -> Unit,
    filtersExpanded: Boolean,
    onToggleFilters: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = { Text("Buscar por número, cliente...") },
            singleLine = true,
            modifier = Modifier.weight(1f),
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Buscar",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            trailingIcon = {
                if (value.isNotEmpty()) {
                    IconButton(onClick = { onValueChange("") }) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Limpiar búsqueda",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            },
            shape = PosExtraShapes.InputRadius,
            colors =
                OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                ),
        )

        FilledTonalIconButton(
            onClick = onToggleFilters,
            modifier = Modifier.size(48.dp),
            shape = PosExtraShapes.InputRadius,
        ) {
            Icon(
                imageVector = if (filtersExpanded) Icons.Filled.ExpandLess else Icons.Filled.FilterList,
                contentDescription = if (filtersExpanded) "Ocultar filtros" else "Mostrar filtros",
                tint = if (filtersExpanded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Estado principal del historial: carga, error, vacío o cuadrícula/lista de transacciones. */
@Composable
private fun HistoryContent(
    state: HistoryState,
    viewModel: HistoryViewModel,
    isLandscape: Boolean,
) {
    when {
        state.isLoading -> HistoryLoadingState()
        state.error != null -> HistoryErrorState(error = state.error, onRetry = viewModel::retry)
        state.isOffline && state.transactions.isEmpty() -> HistoryOfflineEmptyState()
        state.transactions.isEmpty() -> HistoryEmptyState()
        else ->
            Column(modifier = Modifier.fillMaxSize()) {
                if (state.isOffline) {
                    HistoryOfflineBanner()
                }
                HistoryTransactionsList(
                    transactions = state.transactions,
                    onTransactionClick = viewModel::onTransactionClick,
                    isLandscape = isLandscape,
                )
            }
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
            shape = PosExtraShapes.CardRadius,
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
                modifier = Modifier.size(56.dp),
            )
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                "No hay facturas registradas",
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "Las facturas aparecerán aquí una vez que realices ventas",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.outline,
            )
        }
    }
}

@Composable
private fun HistoryOfflineEmptyState() {
    Box(
        Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 32.dp),
        ) {
            Icon(
                Icons.Rounded.CloudOff,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(56.dp),
            )
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                "Sin conexión a internet",
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                "Solamente se mostrarán las transacciones que se hicieron de manera offline hasta que vuelva a haber conexión.",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.outline,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun HistoryOfflineBanner() {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
                .background(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = PosExtraShapes.InputRadius,
                ).padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            Icons.Rounded.CloudOff,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp),
        )
        Text(
            text = "Modo sin conexión: mostrando transacciones locales de este dispositivo",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Lista de transacciones:
 * - En horizontal: cuadrícula de 2 columnas con headers de fecha que abarcan todo el ancho.
 * - En vertical: lista estándar de 1 columna con headers pegajosos.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HistoryTransactionsList(
    transactions: List<Transaction>,
    onTransactionClick: (Transaction) -> Unit,
    isLandscape: Boolean,
) {
    val grouped =
        remember(transactions) {
            transactions.groupBy { it.dateHeader }
        }

    if (isLandscape) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            grouped.forEach { (dateHeader, dateTransactions) ->
                item(
                    span = { GridItemSpan(maxLineSpan) },
                    key = "header_$dateHeader",
                ) {
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
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
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
}
