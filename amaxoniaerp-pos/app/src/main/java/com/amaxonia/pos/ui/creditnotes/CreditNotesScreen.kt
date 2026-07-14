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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Receipt
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.amaxonia.pos.domain.model.creditnote.CreditNoteDetailDto
import com.amaxonia.pos.domain.model.creditnote.CreditNoteFiscalStatusDto
import com.amaxonia.pos.domain.model.creditnote.CreditNoteSourceInvoiceLineDto
import com.amaxonia.pos.domain.model.creditnote.CreditNoteSourceInvoiceSummaryDto
import com.amaxonia.pos.domain.model.creditnote.CreditNoteSummaryDto
import com.amaxonia.pos.domain.model.payment.FormaPago
import com.amaxonia.pos.domain.usecase.creditnote.ProcessCreditNoteFiscalUseCase
import com.amaxonia.pos.ui.common.DependencyContainer
import com.amaxonia.pos.ui.common.injectedViewModel
import com.amaxonia.pos.ui.theme.ConfirmedContainer
import com.amaxonia.pos.ui.theme.ConfirmedContent
import com.amaxonia.pos.ui.theme.PendingContainer
import com.amaxonia.pos.ui.theme.PendingContent
import com.amaxonia.pos.ui.theme.PosPalette

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreditNotesScreen(
    viewModel: CreditNotesViewModel =
        injectedViewModel {
            CreditNotesViewModel(
                creditNoteRepository = DependencyContainer.creditNoteRepository,
                cajaRepository = DependencyContainer.cajaRepository,
                formaPagoRepository = DependencyContainer.formaPagoRepository,
                processCreditNoteFiscal =
                    ProcessCreditNoteFiscalUseCase(
                        DependencyContainer.creditNoteRepository,
                        DependencyContainer.printerFactory,
                        DependencyContainer.posConfigurationRepository,
                    ),
            )
        },
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.error) {
        state.error?.let { snackbarHostState.showSnackbar(it) }
    }
    LaunchedEffect(state.successMessage) {
        state.successMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearSuccessMessage()
        }
    }

    val selectedCreditNote = state.selectedCreditNote
    if (state.showCreditNoteDetail && selectedCreditNote != null) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = viewModel::dismissCreditNoteDetail,
            sheetState = sheetState,
        ) {
            CreditNoteDetailSheet(
                detail = selectedCreditNote,
                isSubmitting = state.isSubmitting,
                onProcessFiscal = viewModel::processSelectedCreditNoteFiscal,
            )
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(screenTitle(state.mode), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = {
                        if (state.mode == CreditNotesMode.LIST) onBack() else viewModel.backFromFlow()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver", tint = MaterialTheme.colorScheme.primary)
                    }
                },
                actions = {
                    if (state.mode == CreditNotesMode.LIST) {
                        IconButton(onClick = viewModel::retry) {
                            Icon(Icons.Default.Refresh, contentDescription = "Actualizar", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                },
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
                        onFechaChange = viewModel::onFechaChange,
                        onPeriodoChange = viewModel::onPeriodoChange,
                        onObservacionChange = viewModel::onObservacionChange,
                        onDevolverStockChange = viewModel::onDevolverStockChange,
                        onGenerarAbonoChange = viewModel::onGenerarAbonoChange,
                        onRefundMethodChange = viewModel::onRefundMethodChange,
                        onSubmit = viewModel::submitCreditNote,
                    )
            }

            if (state.isLoading || state.isSubmitting) {
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
        }
    }
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
        SummaryBanner(
            title = "Devoluciones registradas",
            value = state.creditNotes.size.toString(),
            amount = state.creditNotes.sumOf { it.total },
        )
        Spacer(modifier = Modifier.height(12.dp))
        if (state.creditNotes.isEmpty()) {
            EmptyState(
                icon = Icons.AutoMirrored.Filled.ReceiptLong,
                title = "Aún no hay notas de crédito",
                subtitle = "Pulsa agregar para seleccionar una factura y generar una devolución",
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
                subtitle = "Aparecerán aquí las facturas con saldo disponible para devolución",
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

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun CreditNoteCreateContent(
    state: CreditNotesState,
    onFechaChange: (String) -> Unit,
    onPeriodoChange: (String) -> Unit,
    onObservacionChange: (String) -> Unit,
    onDevolverStockChange: (Boolean) -> Unit,
    onGenerarAbonoChange: (Boolean) -> Unit,
    onRefundMethodChange: (Int?) -> Unit,
    onSubmit: () -> Unit,
) {
    val invoice = state.selectedInvoice
    if (invoice == null) {
        EmptyState(
            icon = Icons.AutoMirrored.Filled.ReceiptLong,
            title = "Selecciona una factura",
            subtitle = "El flujo de creación necesita una factura origen",
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

        item {
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

        item {
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

        item {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    OutlinedTextField(
                        value = state.form.fecha,
                        onValueChange = onFechaChange,
                        label = { Text("Fecha") },
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = state.form.periodo,
                        onValueChange = onPeriodoChange,
                        label = { Text("Periodo") },
                        modifier = Modifier.weight(1f),
                    )
                }
                OutlinedTextField(value = state.form.observacion, onValueChange = onObservacionChange, label = {
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

        item {
            ElevatedCard(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
                Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("¿Desea generar abono a cuenta del cliente?", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.clickable { onGenerarAbonoChange(true) },
                            ) {
                                androidx.compose.material3.RadioButton(
                                    selected = state.form.generarAbono,
                                    onClick = { onGenerarAbonoChange(true) },
                                    colors =
                                        androidx.compose.material3.RadioButtonDefaults.colors(
                                            selectedColor = MaterialTheme.colorScheme.primary,
                                        ),
                                )
                                Text(
                                    "Si",
                                    modifier = Modifier.padding(start = 8.dp),
                                    color =
                                        if (state.form.generarAbono) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                )
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.clickable { onGenerarAbonoChange(false) },
                            ) {
                                androidx.compose.material3.RadioButton(
                                    selected = !state.form.generarAbono,
                                    onClick = { onGenerarAbonoChange(false) },
                                    colors =
                                        androidx.compose.material3.RadioButtonDefaults.colors(
                                            selectedColor = MaterialTheme.colorScheme.primary,
                                        ),
                                )
                                Text(
                                    "No",
                                    modifier = Modifier.padding(start = 8.dp),
                                    color =
                                        if (!state.form.generarAbono) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                )
                            }
                        }
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

        item {
            Button(
                onClick = onSubmit,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                enabled = !state.isSubmitting,
                shape = RoundedCornerShape(12.dp),
                colors =
                    androidx.compose.material3.ButtonDefaults
                        .buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            ) {
                if (state.isSubmitting) {
                    androidx.compose.material3.CircularProgressIndicator(color = PosPalette.FixedWhite, modifier = Modifier.size(24.dp))
                } else {
                    Text("Generar nota de crédito", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }
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

@Composable
private fun SummaryBanner(
    title: String,
    value: String,
    amount: Double,
) {
    ElevatedCard(
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.primary),
        shape = RoundedCornerShape(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(title, color = PosPalette.FixedWhite.copy(alpha = 0.8f), fontSize = 12.sp)
                Text(value, color = PosPalette.FixedWhite, fontWeight = FontWeight.Bold, fontSize = 24.sp)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("Monto total", color = PosPalette.FixedWhite.copy(alpha = 0.8f), fontSize = 12.sp)
                Text(formatAmount(amount), color = PosPalette.FixedWhite, fontWeight = FontWeight.Bold, fontSize = 24.sp)
            }
        }
    }
}

@Composable
private fun CreditNoteCard(
    note: CreditNoteSummaryDto,
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
            Box(
                modifier =
                    Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ReceiptLong,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp),
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = note.codigo,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))

                FiscalStatusChip(status = note.fiscalStatus)
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
                        text = note.fecha,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    if (note.clienteNombre.isNotBlank()) {
                        Spacer(modifier = Modifier.width(10.dp))
                        Icon(
                            Icons.Rounded.Person,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(13.dp),
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = note.clienteNombre,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                    }
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "Bs ${formatAmount(note.total)}",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = note.facturaCodigo,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SourceInvoiceCard(
    invoice: CreditNoteSourceInvoiceSummaryDto,
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

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = invoice.codigo,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
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
                        text = invoice.fecha,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (invoice.clienteNombre.isNotBlank()) {
                        Spacer(modifier = Modifier.width(10.dp))
                        Icon(
                            Icons.Rounded.Person,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(13.dp),
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = invoice.clienteNombre,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                    }
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "${invoice.moneda} ${formatAmount(invoice.remainingAmount)}",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.primary,
                )
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

@Composable
private fun InvoiceLineReadOnlyCard(
    line: CreditNoteSourceInvoiceLineDto,
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
            Box(
                modifier =
                    Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = formatQuantity(line.cantidadOriginal),
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = line.descripcion,
                    fontWeight = FontWeight.Medium,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (line.codigo.isNotBlank()) {
                    Text(
                        text = line.codigo,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "$currency ${formatAmount(line.totalConIvaOriginal)}",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "IVA: ${formatAmount(line.pIva)}%",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun FiscalStatusChip(status: CreditNoteFiscalStatusDto) {
    val isConfirmed = status == CreditNoteFiscalStatusDto.CONFIRMADA
    AssistChip(
        onClick = {},
        label = { Text(if (isConfirmed) "Fiscal confirmada" else "Fiscal pendiente") },
        leadingIcon = {
            Icon(
                imageVector = if (isConfirmed) Icons.Default.CheckCircle else Icons.Default.Print,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
            )
        },
        colors =
            AssistChipDefaults.assistChipColors(
                containerColor = if (isConfirmed) ConfirmedContainer else PendingContainer,
                labelColor = if (isConfirmed) ConfirmedContent else PendingContent,
                leadingIconContentColor = if (isConfirmed) ConfirmedContent else PendingContent,
            ),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RefundMethodSelector(
    methods: List<FormaPago>,
    selectedId: Int?,
    onSelected: (Int) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = methods.firstOrNull { it.idFormaPago == selectedId }?.descripcion.orEmpty()

    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
        OutlinedTextField(
            value = selectedLabel,
            onValueChange = {},
            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable),
            readOnly = true,
            label = { Text("Forma de pago de reintegro") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            methods.forEach { method ->
                androidx.compose.material3.DropdownMenuItem(
                    text = { Text(method.descripcion.orEmpty()) },
                    onClick = {
                        onSelected(method.idFormaPago)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun CreditNoteDetailSheet(
    detail: CreditNoteDetailDto,
    isSubmitting: Boolean,
    onProcessFiscal: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(24.dp)
                .padding(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
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
                    Icons.AutoMirrored.Filled.ReceiptLong,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp),
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = detail.codigo,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    FiscalStatusChip(status = detail.fiscalStatus)
                }
            }
        }

        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            elevation = CardDefaults.elevatedCardElevation(defaultElevation = 0.dp),
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (detail.clienteNombre.isNotBlank()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Rounded.Person,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = detail.clienteNombre,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    if (detail.clienteIdentificacion.isNotBlank()) {
                        Text(
                            text = detail.clienteIdentificacion,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 24.dp),
                        )
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outlineVariant)

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Factura origen", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
                    Text(detail.facturaCodigo, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Monto", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
                    Text(
                        "Bs ${formatAmount(detail.total)}",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }

        if (detail.fiscalStatus == CreditNoteFiscalStatusDto.PENDIENTE && detail.fiscalDocument != null) {
            Button(
                onClick = onProcessFiscal,
                enabled = !isSubmitting,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(12.dp),
                colors =
                    androidx.compose.material3.ButtonDefaults
                        .buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            ) {
                if (isSubmitting) {
                    CircularProgressIndicator(color = PosPalette.FixedWhite, modifier = Modifier.size(24.dp))
                } else {
                    Icon(Icons.Default.Print, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Procesar nota de crédito fiscal", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun EmptyState(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.outline)
            Spacer(modifier = Modifier.height(12.dp))
            Text(title, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Spacer(modifier = Modifier.height(6.dp))
            Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
        }
    }
}

private fun screenTitle(mode: CreditNotesMode): String =
    when (mode) {
        CreditNotesMode.LIST -> "Notas de crédito"
        CreditNotesMode.INVOICE_PICKER -> "Seleccionar factura"
        CreditNotesMode.CREATE -> "Nueva nota de crédito"
    }

private fun formatAmount(value: Double): String = String.format(java.util.Locale.getDefault(), "%.2f", value)

private fun formatQuantity(value: Double): String =
    if (value % 1.0 ==
        0.0
    ) {
        value.toInt().toString()
    } else {
        String.format(java.util.Locale.getDefault(), "%.3f", value)
    }
