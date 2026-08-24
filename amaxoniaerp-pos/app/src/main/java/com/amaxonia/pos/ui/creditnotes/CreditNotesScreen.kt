package com.amaxonia.pos.ui.creditnotes

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.amaxonia.pos.composition.AppGraph
import com.amaxonia.pos.domain.model.creditnote.CreditNoteSourceInvoiceDetailDto
import com.amaxonia.pos.ui.common.injectedViewModel
import com.amaxonia.pos.ui.theme.PosPalette

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
                    onClick = viewModel::openInvoicePicker,
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

            if (state.isLoading || state.isSubmitting) {
                CreditNotesLoadingOverlay()
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

/** Velo de carga sobre el contenido durante carga o envío. */
@Composable
private fun CreditNotesLoadingOverlay() {
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(PosPalette.FixedBlack.copy(alpha = 0.08f)),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
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
        )
        Spacer(modifier = Modifier.height(12.dp))
        if (state.creditNotes.isEmpty()) {
            EmptyState(
                icon = Icons.AutoMirrored.Filled.ReceiptLong,
                title = "AÃºn no hay notas de crÃ©dito",
                subtitle = "Pulsa agregar para seleccionar una factura y generar una devoluciÃ³n",
            )
        } else {
            LazyColumn(contentPadding = PaddingValues(bottom = 100.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(state.creditNotes, key = { it.id }) { note ->
                    CreditNoteCard(note = note, onClick = { onOpenDetail(note.id) })
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
    onSelectInvoice: (String) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        SearchRow(value = state.invoiceSearchQuery, placeholder = "Buscar facturas", onValueChange = onSearchChange, onSearch = onSearch)
        Spacer(modifier = Modifier.height(12.dp))
        if (state.sourceInvoices.isEmpty()) {
            EmptyState(
                icon = Icons.Default.Inventory2,
                title = "No hay facturas elegibles",
                subtitle = "AparecerÃ¡n aquÃ­ las facturas con saldo disponible para devoluciÃ³n",
            )
        } else {
            LazyColumn(contentPadding = PaddingValues(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(state.sourceInvoices, key = { it.id }) { invoice ->
                    SourceInvoiceCard(invoice = invoice, onClick = { onSelectInvoice(invoice.id) })
                }
            }
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
        EmptyState(
            icon = Icons.AutoMirrored.Filled.ReceiptLong,
            title = "Selecciona una factura",
            subtitle = "El flujo de creaciÃ³n necesita una factura origen",
        )
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
                    "Generar la Nota de CrÃ©dito",
                    fontWeight = FontWeight.Bold,
                    fontSize = 24.sp,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    "Se anularÃ¡ la factura y se devolverÃ¡ la totalidad de las lÃ­neas.",
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
                if (invoice.tasa != null) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Tasa (Bs)", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
                        Text("Bs ${formatAmount(invoice.tasa)}", fontWeight = FontWeight.Medium, fontSize = 14.sp)
                    }
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
            Text("ObservaciÃ³n")
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
                Text("Â¿Desea generar abono a cuenta del cliente?", fontWeight = FontWeight.Bold, fontSize = 16.sp)
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
            Text("Generar nota de crÃ©dito", fontSize = 16.sp, fontWeight = FontWeight.Bold)
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
