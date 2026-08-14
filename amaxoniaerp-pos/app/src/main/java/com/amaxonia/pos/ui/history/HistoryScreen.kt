package com.amaxonia.pos.ui.history

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Receipt
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.ShoppingCart
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.amaxonia.pos.domain.model.Transaction
import com.amaxonia.pos.domain.model.TransactionStatus
import com.amaxonia.pos.domain.model.sales.FacturaDetalleItemDto
import com.amaxonia.pos.ui.common.DependencyContainer
import com.amaxonia.pos.ui.common.injectedViewModel
import com.amaxonia.pos.ui.payment.formatCurrencyLabel
import com.amaxonia.pos.ui.theme.PosPalette

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun HistoryScreen(
    viewModel: HistoryViewModel = injectedViewModel { HistoryViewModel(DependencyContainer.invoiceHistoryRepository) },
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    // Detail bottom sheet
    if (state.showDetalleSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { viewModel.dismissDetalle() },
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

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
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
        },
    ) { padding ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding),
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
            androidx.compose.animation.AnimatedVisibility(visible = filtersExpanded) {
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

            if (!state.isLoading || state.transactions.isNotEmpty()) {
                SummaryBar(
                    totalFacturas = state.summary.totalFacturas,
                    totalMonto = state.summary.ventasNetas,
                    currency = state.summary.moneda,
                )
            }

            when {
                state.isLoading && state.transactions.isEmpty() -> {
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
                state.error != null -> {
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
                                    text = state.error ?: "Error desconocido",
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    fontSize = 14.sp,
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Button(onClick = { viewModel.retry() }) {
                                    Text("Reintentar")
                                }
                            }
                        }
                    }
                }
                state.transactions.isEmpty() -> {
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
                else -> {
                    // Group transactions by dateHeader for sticky headers
                    val grouped =
                        remember(state.transactions) {
                            state.transactions.groupBy { it.dateHeader }
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
                        grouped.forEach { (dateHeader, transactions) ->
                            stickyHeader(key = "header_$dateHeader") {
                                DateStickyHeader(date = dateHeader)
                            }
                            items(
                                items = transactions,
                                key = { it.id },
                            ) { transaction ->
                                TransactionCard(
                                    transaction = transaction,
                                    onClick = { viewModel.onTransactionClick(transaction) },
                                )
                            }
                        }
                    }
                }
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
                com.amaxonia.pos.ui.common.components.AdaptiveAmountText(
                    text = "$currency ${String.format(java.util.Locale.getDefault(), "%.2f", totalMonto)}",
                    baseStyle =
                        MaterialTheme.typography.headlineSmall.copy(
                            fontWeight = FontWeight.Bold,
                            textAlign = androidx.compose.ui.text.style.TextAlign.End,
                        ),
                    color = PosPalette.FixedWhite,
                    modifier = Modifier.fillMaxWidth(),
                options = com.amaxonia.pos.ui.common.components.AdaptiveAmountOptions(
                    minFontSizeSp = 13f,
                ))
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
        androidx.compose.material3.FilledTonalIconButton(
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
    filter: com.amaxonia.pos.domain.repository.InvoiceHistoryFilter,
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
    filter: com.amaxonia.pos.domain.repository.InvoiceHistoryFilter,
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
    filter: com.amaxonia.pos.domain.repository.InvoiceHistoryFilter,
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
        androidx.compose.material3.OutlinedButton(
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

// ---------- Sticky Date Header ----------

@Composable
private fun DateStickyHeader(date: String) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.background)
                .padding(vertical = 10.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Rounded.CalendarToday,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(16.dp),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = date,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

// ---------- Transaction Card ----------

@Composable
internal fun TransactionCard(
    transaction: Transaction,
    onClick: () -> Unit,
) {
    ElevatedCard(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Receipt icon
            Box(
                modifier =
                    Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Rounded.Receipt,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp),
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            // Invoice info
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = transaction.invoiceNumber,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                StatusBadge(status = transaction.status)
                Spacer(modifier = Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Rounded.Schedule,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(13.dp),
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = transaction.time,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (transaction.clienteNombre.isNotBlank()) {
                        Spacer(modifier = Modifier.width(10.dp))
                        Icon(
                            Icons.Rounded.Person,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(13.dp),
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = transaction.clienteNombre,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                    }
                }
            }

            // Amount + arrow: adaptive para que montos enormes no colisionen con el cliente.
            Column(horizontalAlignment = Alignment.End) {
                com.amaxonia.pos.ui.common.components.AdaptiveAmountText(
                    text = "${transaction.currency} ${String.format(java.util.Locale.getDefault(), "%.2f", transaction.amount)}",
                    baseStyle =
                        MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                        ),
                    color = MaterialTheme.colorScheme.primary,
                options = com.amaxonia.pos.ui.common.components.AdaptiveAmountOptions(
                    minFontSizeSp = 11f,
                ))
                if (transaction.totalRef != null && transaction.totalRef > 0.0 && !transaction.abrMonedaSecundaria.isNullOrBlank()) {
                    Text(
                        text = "${formatCurrencyLabel(
                            transaction.abrMonedaSecundaria,
                        )} ${String.format(java.util.Locale.getDefault(), "%.2f", transaction.totalRef)}",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline,
                modifier =
                    Modifier
                        .padding(start = 4.dp)
                        .size(20.dp),
            )
        }
    }
}

// ---------- Status Badge ----------

@Composable
private fun StatusBadge(status: TransactionStatus) {
    val backgroundColor = Color(status.colorHex).copy(alpha = 0.1f)
    val textColor = Color(status.colorHex)

    Box(
        modifier =
            Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(backgroundColor)
                .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Text(
            text = status.label,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = textColor,
        )
    }
}

// ---------- Factura Detail Bottom Sheet ----------

@Composable
private fun FacturaDetalleSheetContent(
    transaction: Transaction?,
    items: List<FacturaDetalleItemDto>,
    isLoading: Boolean,
    error: String?,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
    ) {
        // Header
        if (transaction != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier =
                        Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Rounded.Receipt,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp),
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = transaction.invoiceNumber,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "${transaction.dateHeader}  ${transaction.time}",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        StatusBadge(status = transaction.status)
                    }
                }
            }

            // Client + payment info
            if (transaction.clienteNombre.isNotBlank() || transaction.formaPago.isNotBlank()) {
                Spacer(modifier = Modifier.height(12.dp))
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    elevation = CardDefaults.elevatedCardElevation(defaultElevation = 0.dp),
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        if (transaction.clienteNombre.isNotBlank()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Rounded.Person,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp),
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = transaction.clienteNombre,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                            if (transaction.clienteIdentificacion.isNotBlank()) {
                                Text(
                                    text = transaction.clienteIdentificacion,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(start = 24.dp),
                                )
                            }
                        }
                        if (transaction.formaPago.isNotBlank()) {
                            if (transaction.clienteNombre.isNotBlank()) {
                                Spacer(modifier = Modifier.height(6.dp))
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Rounded.ShoppingCart,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp),
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = transaction.formaPago.replaceFirstChar { it.uppercase() },
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(modifier = Modifier.height(12.dp))
        }

        // Section title
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Rounded.Inventory2,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Productos",
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (!isLoading && items.isNotEmpty()) {
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "(${items.size})",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        when {
            isLoading -> {
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(120.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp),
                            strokeWidth = 3.dp,
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            "Cargando productos...",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            error != null -> {
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors =
                        CardDefaults.elevatedCardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                        ),
                ) {
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
            items.isEmpty() -> {
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(80.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "Sin productos",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            else -> {
                LazyColumn(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height((items.size.coerceAtMost(8) * 72).dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(items, key = { it.id }) { item ->
                        DetalleItemRow(item = item, currency = transaction?.currency ?: "USD")
                    }
                }

                // Total row
                if (transaction != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Total",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Column(horizontalAlignment = Alignment.End) {
                            com.amaxonia.pos.ui.common.components.AdaptiveAmountText(
                                  text = "${transaction.currency} ${String.format(
                                      java.util.Locale.getDefault(),
                                      "%.2f",
                                      transaction.amount,
                                  )}",
                                  baseStyle =
                                      MaterialTheme.typography.titleLarge.copy(
                                          fontWeight = FontWeight.Bold,
                                      ),
                                  color = MaterialTheme.colorScheme.primary,
                              options = com.amaxonia.pos.ui.common.components.AdaptiveAmountOptions(
                                  minFontSizeSp = 13f,
                              ))
                            if (transaction.totalRef != null &&
                                transaction.totalRef > 0.0 &&
                                !transaction.abrMonedaSecundaria.isNullOrBlank()
                            ) {
                                Text(
                                    text = "${formatCurrencyLabel(
                                        transaction.abrMonedaSecundaria,
                                    )} ${String.format(java.util.Locale.getDefault(), "%.2f", transaction.totalRef)}",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ---------- Detalle Item Row ----------

@Composable
private fun DetalleItemRow(
    item: FacturaDetalleItemDto,
    currency: String,
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Qty badge
            Box(
                modifier =
                    Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text =
                        if (item.cantidad == item.cantidad.toLong().toDouble()) {
                            "${item.cantidad.toLong()}"
                        } else {
                            String.format(java.util.Locale.getDefault(), "%.1f", item.cantidad)
                        },
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.descripcion,
                    fontWeight = FontWeight.Medium,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (item.codigo.isNotBlank()) {
                    Text(
                        text = item.codigo,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            Column(horizontalAlignment = Alignment.End) {
                com.amaxonia.pos.ui.common.components.AdaptiveAmountText(
                    text = "$currency ${String.format(java.util.Locale.getDefault(), "%.2f", item.totalConIva)}",
                    baseStyle =
                        MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.Bold,
                        ),
                    color = MaterialTheme.colorScheme.onSurface,
                options = com.amaxonia.pos.ui.common.components.AdaptiveAmountOptions(
                    minFontSizeSp = 11f,
                ))
                Text(
                    text = "c/u ${String.format(java.util.Locale.getDefault(), "%.2f", item.precioUnitario)}",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
