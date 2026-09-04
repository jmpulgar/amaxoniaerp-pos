package com.amaxonia.pos.ui.creditnotes

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.amaxonia.pos.composition.AppGraph
import com.amaxonia.pos.domain.model.creditnote.CreditNoteSourceInvoiceDetailDto
import com.amaxonia.pos.ui.common.components.PosDatePickerField
import com.amaxonia.pos.ui.common.injectedViewModel
import com.amaxonia.pos.ui.theme.PosPalette
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreditNotesScreen(
    viewModel: CreditNotesViewModel =
        injectedViewModel {
            AppGraph.creditNotes.creditNotesViewModel()
        },
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var processingSeconds by remember { mutableIntStateOf(0) }

    LaunchedEffect(state.isSubmitting) {
        if (!state.isSubmitting) {
            processingSeconds = 0
            return@LaunchedEffect
        }
        processingSeconds = 0
        while (true) {
            delay(1_000)
            processingSeconds += 1
        }
    }

    CreditNotesSnackbarEffects(
        state = state,
        viewModel = viewModel,
        snackbarHostState = snackbarHostState,
    )

    CreditNoteDetailSheetOverlay(
        state = state,
        onProcessFiscal = viewModel::processSelectedCreditNoteFiscal,
        onDismiss = viewModel::dismissCreditNoteDetail,
    )

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            CreditNotesTopBar(
                state = state,
                onBack = onBack,
                onBackFromFlow = viewModel::backFromFlow,
                onRetry = viewModel::retry,
            )
        },
        floatingActionButton = {
            if (state.mode == CreditNotesMode.LIST) {
                FloatingActionButton(
                    onClick = { if (!state.isLoading) viewModel.openInvoicePicker() },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = PosPalette.FixedWhite,
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Agregar")
                }
            }
        },
    ) { padding ->
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding),
        ) {
            CreditNotesModeContent(state = state, viewModel = viewModel)

            if (state.isSubmitting) {
                ProcessingCreditNoteOverlay(
                    elapsedSeconds = processingSeconds,
                )
            }
        }
    }
}

/** Contenido según el modo del flujo (lista, selector de factura, creación). */
@Composable
private fun CreditNotesModeContent(
    state: CreditNotesState,
    viewModel: CreditNotesViewModel,
) {
    when (state.mode) {
        CreditNotesMode.LIST ->
            CreditNotesListContent(
                state = state,
                onSearchChange = viewModel::onSearchQueryChange,
                onSearch = viewModel::searchCreditNotes,
                onOpenDetail = viewModel::openCreditNoteDetail,
            )
        CreditNotesMode.INVOICE_PICKER ->
            CreditNoteInvoicePickerContent(
                state = state,
                onSearchChange = viewModel::onInvoiceSearchQueryChange,
                onSearch = viewModel::searchSourceInvoices,
                onDateFilterTypeSelect = viewModel::setInvoiceDateFilterType,
                onCustomFechaInicioChange = viewModel::onCustomInvoiceFechaInicioChange,
                onCustomFechaFinChange = viewModel::onCustomInvoiceFechaFinChange,
                onApplyCustomDateFilter = viewModel::applyCustomInvoiceDateFilter,
                onToggleCustomDateFilter = viewModel::toggleDateFilterCustomExpanded,
                onSelectInvoice = viewModel::selectInvoice,
            )
        CreditNotesMode.CREATE ->
            CreditNoteCreateContent(
                state = state,
                handlers =
                    CreditNoteCreateHandlers(
                        fields =
                            CreditNoteFieldHandlers(
                                onFechaChange = viewModel.formController::onFechaChange,
                                onPeriodoChange = viewModel.formController::onPeriodoChange,
                                onObservacionChange = viewModel.formController::onObservacionChange,
                            ),
                        onDevolverStockChange = viewModel.formController::onDevolverStockChange,
                        onGenerarAbonoChange = viewModel.formController::onGenerarAbonoChange,
                        onRefundMethodChange = viewModel.formController::onRefundMethodChange,
                        onSubmit = viewModel::submitCreditNote,
                    ),
            )
    }
}

/** Overlay de procesamiento con etapas y contador de segundos estilo Facturación Electrónica Panamá. */
@Composable
private fun ProcessingCreditNoteOverlay(elapsedSeconds: Int) {
    val estimatedSeconds = 30
    val progress = (elapsedSeconds / estimatedSeconds.toFloat()).coerceIn(0.08f, 0.94f)
    val secondsLeft = (estimatedSeconds - elapsedSeconds).coerceAtLeast(3)
    val stageTitle =
        when {
            elapsedSeconds < 4 -> "Preparando la nota de crédito"
            elapsedSeconds < 10 -> "Conectando con facturación electrónica"
            elapsedSeconds < 24 -> "Esperando autorización de la DGI"
            else -> "Últimos segundos de validación"
        }
    val stageSubtitle =
        when {
            elapsedSeconds < 4 -> "Validando nota de crédito, caja e inventario..."
            elapsedSeconds < 10 -> "Enviando el documento al proveedor fiscal..."
            elapsedSeconds < 24 -> "TheFactory está procesando el CUFE y el QR..."
            else -> "La respuesta está tardando un poco más de lo normal, seguimos esperando."
        }

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(PosPalette.FixedBlack.copy(alpha = 0.36f))
                .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        ElevatedCard(
            modifier = Modifier.fillMaxWidth().widthIn(max = 480.dp),
            shape = MaterialTheme.shapes.extraLarge,
            colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.elevatedCardElevation(defaultElevation = 10.dp),
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier =
                        Modifier
                            .size(74.dp)
                            .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.size(54.dp),
                        strokeWidth = 5.dp,
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                    )
                    Text(
                        text = "${elapsedSeconds}s",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))

                Text(
                    text = "Procesando nota de crédito",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stageTitle,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 6.dp),
                )
                Text(
                    text = stageSubtitle,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                    textAlign = TextAlign.Center,
                )

                Spacer(modifier = Modifier.height(18.dp))

                LinearProgressIndicator(
                    progress = { progress },
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(50)),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                )

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text =
                        if (elapsedSeconds < estimatedSeconds) {
                            "Tiempo estimado restante: ${secondsLeft}s"
                        } else {
                            "Está tomando más de lo habitual, no cierres la pantalla."
                        },
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(modifier = Modifier.height(12.dp))

                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    shape = MaterialTheme.shapes.medium,
                ) {
                    Text(
                        text = "Evita tocar atrás o cerrar la app.",
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }
    }
}

/** Muestra como snackbars los mensajes de error y éxito del flujo. */
@Composable
private fun CreditNotesSnackbarEffects(
    state: CreditNotesState,
    viewModel: CreditNotesViewModel,
    snackbarHostState: SnackbarHostState,
) {
    LaunchedEffect(state.error) {
        state.error?.let { snackbarHostState.showSnackbar(it) }
    }
    LaunchedEffect(state.successMessage) {
        state.successMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearSuccessMessage()
        }
    }
}

/** Hoja inferior con el detalle de la nota seleccionada. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreditNoteDetailSheetOverlay(
    state: CreditNotesState,
    onProcessFiscal: () -> Unit,
    onDismiss: () -> Unit,
) {
    val selectedCreditNote = state.selectedCreditNote
    if (state.showCreditNoteDetail && selectedCreditNote != null) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = onDismiss,
            sheetState = sheetState,
        ) {
            CreditNoteDetailSheet(
                detail = selectedCreditNote,
                isSubmitting = state.isSubmitting,
                onProcessFiscal = onProcessFiscal,
                currencySymbol = state.currencySymbol,
                isPanama = state.isPanama,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreditNotesTopBar(
    state: CreditNotesState,
    onBack: () -> Unit,
    onBackFromFlow: () -> Unit,
    onRetry: () -> Unit,
) {
    TopAppBar(
        title = { Text(screenTitle(state.mode), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold) },
        navigationIcon = {
            IconButton(onClick = {
                if (state.mode == CreditNotesMode.LIST) onBack() else onBackFromFlow()
            }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver", tint = MaterialTheme.colorScheme.primary)
            }
        },
        actions = {
            if (state.mode == CreditNotesMode.LIST) {
                IconButton(onClick = onRetry) {
                    Icon(Icons.Default.Refresh, contentDescription = "Actualizar", tint = MaterialTheme.colorScheme.primary)
                }
            }
        },
    )
}

@Composable
private fun CreditNotesListContent(
    state: CreditNotesState,
    onSearchChange: (String) -> Unit,
    onSearch: () -> Unit,
    onOpenDetail: (String) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        SearchRow(value = state.searchQuery, placeholder = "Buscar devoluciones", onValueChange = onSearchChange, onSearch = onSearch)
        Spacer(modifier = Modifier.height(12.dp))
        // El total sólo cambia con la lista: se memoiza para no re-sumar en
        // cada recomposición del banner.
        val creditNotesTotal = remember(state.creditNotes) { state.creditNotes.sumOf { it.total } }
        SummaryBanner(
            title = "Devoluciones registradas",
            value = state.creditNotes.size.toString(),
            amount = creditNotesTotal,
            currencySymbol = state.currencySymbol,
        )
        Spacer(modifier = Modifier.height(12.dp))
        when {
            state.isLoading && state.creditNotes.isEmpty() -> {
                CreditNotesLoadingState(message = "Cargando notas de crédito...")
            }
            !state.isLoading && state.creditNotes.isEmpty() -> {
                EmptyState(
                    icon = Icons.AutoMirrored.Filled.ReceiptLong,
                    title = "Aún no hay notas de crédito",
                    subtitle = "Pulsa agregar para seleccionar una factura y generar una devolución",
                )
            }
            else -> {
                Box(modifier = Modifier.fillMaxSize()) {
                    LazyColumn(contentPadding = PaddingValues(bottom = 100.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(state.creditNotes, key = { it.id }) { note ->
                            CreditNoteCard(
                                note = note,
                                onClick = { if (!state.isLoading) onOpenDetail(note.id) },
                                currencySymbol = state.currencySymbol,
                                isPanama = state.isPanama,
                            )
                        }
                    }
                    if (state.isLoading) {
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter),
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CreditNoteInvoicePickerContent(
    state: CreditNotesState,
    onSearchChange: (String) -> Unit,
    onSearch: () -> Unit,
    onDateFilterTypeSelect: (InvoiceDateFilterType) -> Unit,
    onCustomFechaInicioChange: (String) -> Unit,
    onCustomFechaFinChange: (String) -> Unit,
    onApplyCustomDateFilter: () -> Unit,
    onToggleCustomDateFilter: () -> Unit,
    onSelectInvoice: (String) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        SearchRow(value = state.invoiceSearchQuery, placeholder = "Buscar facturas", onValueChange = onSearchChange, onSearch = onSearch)
        Spacer(modifier = Modifier.height(10.dp))
        InvoiceDateFilterSection(
            filter = state.invoiceDateFilter,
            isCustomExpanded = state.isDateFilterCustomExpanded,
            onDateFilterTypeSelect = onDateFilterTypeSelect,
            onCustomFechaInicioChange = onCustomFechaInicioChange,
            onCustomFechaFinChange = onCustomFechaFinChange,
            onApplyCustomDateFilter = onApplyCustomDateFilter,
            onToggleCustomDateFilter = onToggleCustomDateFilter,
        )
        Spacer(modifier = Modifier.height(12.dp))
        when {
            state.isLoading && state.sourceInvoices.isEmpty() -> {
                CreditNotesLoadingState(message = "Buscando facturas elegibles...")
            }
            !state.isLoading && state.sourceInvoices.isEmpty() -> {
                EmptyState(
                    icon = Icons.Default.Inventory2,
                    title = "No hay facturas elegibles",
                    subtitle =
                        "No se encontraron facturas con saldo disponible para el periodo seleccionado " +
                            "(${state.invoiceDateFilter.displayLabel()})",
                )
            }
            else -> {
                Box(modifier = Modifier.fillMaxSize()) {
                    LazyColumn(contentPadding = PaddingValues(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(state.sourceInvoices, key = { it.id }) { invoice ->
                            SourceInvoiceCard(
                                invoice = invoice,
                                onClick = { if (!state.isLoading) onSelectInvoice(invoice.id) },
                            )
                        }
                    }
                    if (state.isLoading) {
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter),
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun InvoiceDateFilterSection(
    filter: InvoiceDateFilter,
    isCustomExpanded: Boolean,
    onDateFilterTypeSelect: (InvoiceDateFilterType) -> Unit,
    onCustomFechaInicioChange: (String) -> Unit,
    onCustomFechaFinChange: (String) -> Unit,
    onApplyCustomDateFilter: () -> Unit,
    onToggleCustomDateFilter: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(InvoiceDateFilterType.entries.toTypedArray(), key = { it.name }) { type ->
                val isSelected = filter.type == type
                FilterChip(
                    selected = isSelected,
                    onClick = {
                        if (type == InvoiceDateFilterType.PERSONALIZADO && isSelected) {
                            onToggleCustomDateFilter()
                        } else {
                            onDateFilterTypeSelect(type)
                        }
                    },
                    label = { Text(type.label, fontSize = 12.sp) },
                    leadingIcon = {
                        val icon =
                            when (type) {
                                InvoiceDateFilterType.HOY -> Icons.Default.CalendarToday
                                InvoiceDateFilterType.MES_ACTUAL -> Icons.Default.CalendarMonth
                                InvoiceDateFilterType.MES_ANTERIOR -> Icons.Default.CalendarToday
                                InvoiceDateFilterType.PERSONALIZADO -> Icons.Default.DateRange
                            }
                        Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
                    },
                    shape = RoundedCornerShape(8.dp),
                    colors =
                        FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        ),
                )
            }
        }

        AnimatedVisibility(visible = isCustomExpanded || filter.type == InvoiceDateFilterType.PERSONALIZADO) {
            ElevatedCard(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        PosDatePickerField(
                            label = "Desde",
                            value = filter.customFechaInicio,
                            onValueChange = onCustomFechaInicioChange,
                            modifier = Modifier.weight(1f),
                        )
                        PosDatePickerField(
                            label = "Hasta",
                            value = filter.customFechaFin,
                            onValueChange = onCustomFechaFinChange,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        Button(
                            onClick = onApplyCustomDateFilter,
                            shape = RoundedCornerShape(8.dp),
                        ) {
                            Text("Aplicar filtro")
                        }
                    }
                }
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(horizontal = 2.dp),
        ) {
            Icon(
                Icons.Default.Info,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "Mostrando: ${filter.displayLabel()}",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Handlers de los campos de texto del formulario de nota de crédito. */
private class CreditNoteFieldHandlers(
    val onFechaChange: (String) -> Unit,
    val onPeriodoChange: (String) -> Unit,
    val onObservacionChange: (String) -> Unit,
)

/** Handlers del formulario de creación de nota de crédito. */
private class CreditNoteCreateHandlers(
    val fields: CreditNoteFieldHandlers,
    val onDevolverStockChange: (Boolean) -> Unit,
    val onGenerarAbonoChange: (Boolean) -> Unit,
    val onRefundMethodChange: (Int?) -> Unit,
    val onSubmit: () -> Unit,
)

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun CreditNoteCreateContent(
    state: CreditNotesState,
    handlers: CreditNoteCreateHandlers,
) {
    val invoice = state.selectedInvoice
    if (invoice == null) {
        if (state.isLoading) {
            CreditNotesLoadingState(message = "Cargando detalle de la factura...")
        } else {
            EmptyState(
                icon = Icons.AutoMirrored.Filled.ReceiptLong,
                title = "Selecciona una factura",
                subtitle = "El flujo de creación necesita una factura origen",
            )
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Generar la Nota de Crédito",
                    fontWeight = FontWeight.Bold,
                    fontSize = 24.sp,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    "Se anulará la factura y se devolverá la totalidad de las líneas.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 14.sp,
                )
            }
        }

        item { CreditNoteSourceSummaryCard(invoice = invoice) }

        item { CreditNoteProductsSection(invoice = invoice) }

        item {
            CreditNoteFormFieldsSection(
                state = state,
                fields = handlers.fields,
                onDevolverStockChange = handlers.onDevolverStockChange,
            )
        }

        item {
            CreditNoteAbonoSection(
                state = state,
                onGenerarAbonoChange = handlers.onGenerarAbonoChange,
                onRefundMethodChange = handlers.onRefundMethodChange,
            )
        }

        item {
            CreditNoteSubmitButton(
                isSubmitting = state.isSubmitting,
                onSubmit = handlers.onSubmit,
            )
        }
    }
}

@Composable
private fun CreditNoteSourceSummaryCard(invoice: CreditNoteSourceInvoiceDetailDto) {
    ElevatedCard(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(invoice.codigo, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = MaterialTheme.colorScheme.primary)
                Text(invoice.clienteNombre, fontWeight = FontWeight.Medium)
                Text(invoice.clienteIdentificacion, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Subtotal", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
                    Text(
                        "${invoice.moneda} ${formatAmount(invoice.subtotalOriginal)}",
                        fontWeight = FontWeight.Medium,
                        fontSize = 14.sp,
                    )
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Total", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
                    Text(
                        "${invoice.moneda} ${formatAmount(invoice.totalOriginal)}",
                        fontWeight = FontWeight.Medium,
                        fontSize = 14.sp,
                    )
                }
                if (invoice.tasa != null && invoice.tasa > 1.0) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Tasa (Bs)", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
                        Text("Bs ${formatAmount(invoice.tasa)}", fontWeight = FontWeight.Medium, fontSize = 14.sp)
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Total USD", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
                        Text("USD ${formatAmount(invoice.totalUsd)}", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Total Bs", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
                        Text("Bs ${formatAmount(invoice.totalBs)}", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun CreditNoteProductsSection(invoice: CreditNoteSourceInvoiceDetailDto) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Rounded.Inventory2,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Productos (${invoice.lines.size})",
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            invoice.lines.forEach { line ->
                InvoiceLineReadOnlyCard(line = line, currency = invoice.moneda)
            }
        }
    }
}

@Composable
private fun CreditNoteFormFieldsSection(
    state: CreditNotesState,
    fields: CreditNoteFieldHandlers,
    onDevolverStockChange: (Boolean) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            OutlinedTextField(
                value = state.form.fecha,
                onValueChange = fields.onFechaChange,
                label = { Text("Fecha") },
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = state.form.periodo,
                onValueChange = fields.onPeriodoChange,
                label = { Text("Periodo") },
                modifier = Modifier.weight(1f),
            )
        }
        OutlinedTextField(value = state.form.observacion, onValueChange = fields.onObservacionChange, label = {
            Text("Observación")
        }, modifier = Modifier.fillMaxWidth())

        Row(verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.material3.Switch(
                checked = state.form.devolverStock,
                onCheckedChange = onDevolverStockChange,
                colors =
                    androidx.compose.material3.SwitchDefaults.colors(
                        checkedThumbColor = MaterialTheme.colorScheme.primary,
                        checkedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                    ),
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text("Devolver stock al inventario", fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun CreditNoteAbonoSection(
    state: CreditNotesState,
    onGenerarAbonoChange: (Boolean) -> Unit,
    onRefundMethodChange: (Int?) -> Unit,
) {
    ElevatedCard(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("¿Desea generar abono a cuenta del cliente?", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                AbonoYesNoOptions(
                    selected = state.form.generarAbono,
                    onSelect = onGenerarAbonoChange,
                )
            }

            if (!state.form.generarAbono) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Seleccione la forma de pago para realizar el reintegro al cliente:",
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    RefundMethodSelector(
                        methods = state.availableRefundMethods,
                        selectedId = state.form.idFormaPagoReintegro,
                        onSelected = onRefundMethodChange,
                    )
                }
            }
        }
    }
}

@Composable
private fun AbonoYesNoOptions(
    selected: Boolean,
    onSelect: (Boolean) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(24.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.clickable { onSelect(true) },
        ) {
            androidx.compose.material3.RadioButton(
                selected = selected,
                onClick = { onSelect(true) },
                colors =
                    androidx.compose.material3.RadioButtonDefaults.colors(
                        selectedColor = MaterialTheme.colorScheme.primary,
                    ),
            )
            Text(
                "Si",
                modifier = Modifier.padding(start = 8.dp),
                color =
                    if (selected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.clickable { onSelect(false) },
        ) {
            androidx.compose.material3.RadioButton(
                selected = !selected,
                onClick = { onSelect(false) },
                colors =
                    androidx.compose.material3.RadioButtonDefaults.colors(
                        selectedColor = MaterialTheme.colorScheme.primary,
                    ),
            )
            Text(
                "No",
                modifier = Modifier.padding(start = 8.dp),
                color =
                    if (!selected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
            )
        }
    }
}

@Composable
private fun CreditNoteSubmitButton(
    isSubmitting: Boolean,
    onSubmit: () -> Unit,
) {
    Button(
        onClick = onSubmit,
        modifier = Modifier.fillMaxWidth().height(56.dp),
        enabled = !isSubmitting,
        shape = RoundedCornerShape(12.dp),
        colors =
            androidx.compose.material3.ButtonDefaults
                .buttonColors(containerColor = MaterialTheme.colorScheme.primary),
    ) {
        if (isSubmitting) {
            androidx.compose.material3.CircularProgressIndicator(color = PosPalette.FixedWhite, modifier = Modifier.size(24.dp))
        } else {
            Text("Generar nota de crédito", fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun SearchRow(
    value: String,
    placeholder: String,
    onValueChange: (String) -> Unit,
    onSearch: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.weight(1f),
            placeholder = { Text(placeholder) },
            singleLine = true,
        )
        IconButton(onClick = onSearch) {
            Icon(Icons.Default.Search, contentDescription = "Buscar", tint = MaterialTheme.colorScheme.primary)
        }
    }
}
