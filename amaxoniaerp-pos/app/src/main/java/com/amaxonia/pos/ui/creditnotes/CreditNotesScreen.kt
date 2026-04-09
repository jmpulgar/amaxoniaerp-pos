package com.amaxonia.pos.ui.creditnotes

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amaxonia.pos.domain.model.creditnote.CreditNoteDetailDto
import com.amaxonia.pos.domain.model.creditnote.CreditNoteFiscalStatusDto
import com.amaxonia.pos.domain.model.creditnote.CreditNoteSettlementTypeDto
import com.amaxonia.pos.domain.model.creditnote.CreditNoteSourceInvoiceDetailDto
import com.amaxonia.pos.domain.model.creditnote.CreditNoteSourceInvoiceLineDto
import com.amaxonia.pos.domain.model.creditnote.CreditNoteSourceInvoiceSummaryDto
import com.amaxonia.pos.domain.model.creditnote.CreditNoteSummaryDto
import com.amaxonia.pos.domain.model.payment.FormaPago
import com.amaxonia.pos.ui.common.DependencyContainer
import com.amaxonia.pos.ui.common.injectedViewModel
import com.amaxonia.pos.ui.theme.AmaxoniaBlue

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreditNotesScreen(
    viewModel: CreditNotesViewModel = injectedViewModel {
        CreditNotesViewModel(
            creditNoteRepository = DependencyContainer.creditNoteRepository,
            cajaRepository = DependencyContainer.cajaRepository,
            formaPagoRepository = DependencyContainer.formaPagoRepository,
            printerFactory = DependencyContainer.printerFactory,
            localStore = DependencyContainer.localStore,
        )
    },
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsState()
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
                title = { Text(screenTitle(state.mode), color = AmaxoniaBlue, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = {
                        if (state.mode == CreditNotesMode.LIST) onBack() else viewModel.backFromFlow()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver", tint = AmaxoniaBlue)
                    }
                },
                actions = {
                    if (state.mode == CreditNotesMode.LIST) {
                        IconButton(onClick = viewModel::retry) {
                            Icon(Icons.Default.Refresh, contentDescription = "Actualizar", tint = AmaxoniaBlue)
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            if (state.mode == CreditNotesMode.LIST) {
                FloatingActionButton(
                    onClick = viewModel::openInvoicePicker,
                    containerColor = AmaxoniaBlue,
                    contentColor = Color.White,
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Agregar")
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when (state.mode) {
                CreditNotesMode.LIST -> CreditNotesListContent(
                    state = state,
                    onSearchChange = viewModel::onSearchQueryChange,
                    onSearch = viewModel::searchCreditNotes,
                    onOpenDetail = viewModel::openCreditNoteDetail,
                )
                CreditNotesMode.INVOICE_PICKER -> CreditNoteInvoicePickerContent(
                    state = state,
                    onSearchChange = viewModel::onInvoiceSearchQueryChange,
                    onSearch = viewModel::searchSourceInvoices,
                    onSelectInvoice = viewModel::selectInvoice,
                )
                CreditNotesMode.CREATE -> CreditNoteCreateContent(
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
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.08f)),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(color = AmaxoniaBlue)
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
        SummaryBanner(title = "Devoluciones registradas", value = state.creditNotes.size.toString(), amount = state.creditNotes.sumOf { it.total })
        Spacer(modifier = Modifier.height(12.dp))
        if (state.creditNotes.isEmpty()) {
            EmptyState(icon = Icons.AutoMirrored.Filled.ReceiptLong, title = "Aún no hay notas de crédito", subtitle = "Pulsa agregar para seleccionar una factura y generar una devolución")
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
            EmptyState(icon = Icons.Default.Inventory2, title = "No hay facturas elegibles", subtitle = "Aparecerán aquí las facturas con saldo disponible para devolución")
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
        EmptyState(icon = Icons.AutoMirrored.Filled.ReceiptLong, title = "Selecciona una factura", subtitle = "El flujo de creación necesita una factura origen")
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Generar la Nota de Crédito", fontWeight = FontWeight.Bold, fontSize = 24.sp, color = MaterialTheme.colorScheme.onBackground)
                Text("Se anulará la factura y se devolverá la totalidad de las líneas.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
            }
        }

        item {
            ElevatedCard(shape = RoundedCornerShape(12.dp), colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(invoice.codigo, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = AmaxoniaBlue)
                    Text(invoice.clienteNombre, fontWeight = FontWeight.Medium)
                    Text(invoice.clienteIdentificacion, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Total a devolver: ${invoice.moneda} ${formatAmount(invoice.remainingAmount)}", fontWeight = FontWeight.Black, fontSize = 16.sp)
                }
            }
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    OutlinedTextField(value = state.form.fecha, onValueChange = onFechaChange, label = { Text("Fecha") }, modifier = Modifier.weight(1f))
                    OutlinedTextField(value = state.form.periodo, onValueChange = onPeriodoChange, label = { Text("Periodo") }, modifier = Modifier.weight(1f))
                }
                OutlinedTextField(value = state.form.observacion, onValueChange = onObservacionChange, label = { Text("Observación") }, modifier = Modifier.fillMaxWidth())
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.material3.Switch(
                        checked = state.form.devolverStock,
                        onCheckedChange = onDevolverStockChange,
                        colors = androidx.compose.material3.SwitchDefaults.colors(checkedThumbColor = AmaxoniaBlue, checkedTrackColor = AmaxoniaBlue.copy(alpha = 0.5f))
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("Devolver stock al inventario", fontWeight = FontWeight.Medium)
                }
            }
        }

        item {
            ElevatedCard(shape = RoundedCornerShape(12.dp), colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("¿Desea generar abono a cuenta del cliente?", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { onGenerarAbonoChange(true) }) {
                                androidx.compose.material3.RadioButton(
                                    selected = state.form.generarAbono,
                                    onClick = { onGenerarAbonoChange(true) },
                                    colors = androidx.compose.material3.RadioButtonDefaults.colors(selectedColor = AmaxoniaBlue)
                                )
                                Text("Si", modifier = Modifier.padding(start = 8.dp), color = if (state.form.generarAbono) AmaxoniaBlue else MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { onGenerarAbonoChange(false) }) {
                                androidx.compose.material3.RadioButton(
                                    selected = !state.form.generarAbono,
                                    onClick = { onGenerarAbonoChange(false) },
                                    colors = androidx.compose.material3.RadioButtonDefaults.colors(selectedColor = AmaxoniaBlue)
                                )
                                Text("No", modifier = Modifier.padding(start = 8.dp), color = if (!state.form.generarAbono) AmaxoniaBlue else MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }

                    if (!state.form.generarAbono) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Seleccione la forma de pago para realizar el reintegro al cliente:", fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
            Button(onClick = onSubmit, modifier = Modifier.fillMaxWidth().height(56.dp), enabled = !state.isSubmitting, shape = RoundedCornerShape(12.dp), colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = AmaxoniaBlue)) {
                if (state.isSubmitting) {
                    androidx.compose.material3.CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                } else {
                    Text("Generar nota de crédito", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            ElevatedCard(shape = RoundedCornerShape(16.dp), colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(invoice.codigo, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = AmaxoniaBlue)
                    Text(invoice.clienteNombre, fontWeight = FontWeight.Medium)
                    Text(invoice.clienteIdentificacion, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Disponible para devolver: ${invoice.moneda} ${formatAmount(invoice.remainingAmount)}", fontWeight = FontWeight.Bold)
                }
            }
        }

        item {
            ElevatedCard(shape = RoundedCornerShape(16.dp)) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(value = state.form.fecha, onValueChange = onFechaChange, label = { Text("Fecha") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = state.form.periodo, onValueChange = onPeriodoChange, label = { Text("Periodo") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = state.form.observacion, onValueChange = onObservacionChange, label = { Text("Observación") }, modifier = Modifier.fillMaxWidth())
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = state.form.anular, onClick = { onAnularChange(!state.form.anular) }, label = { Text("Anular factura") })
                        FilterChip(selected = state.form.devolverStock, onClick = { onDevolverStockChange(!state.form.devolverStock) }, label = { Text("Devolver stock") })
                    }
                }
            }
        }

        item {
            ElevatedCard(shape = RoundedCornerShape(16.dp)) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Destino de la nota de crédito", fontWeight = FontWeight.Bold)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SettlementChip("Sin salida", CreditNoteSettlementTypeDto.NINGUNO, state.form.settlementType, onSettlementTypeChange)
                        SettlementChip("Abono", CreditNoteSettlementTypeDto.ABONO, state.form.settlementType, onSettlementTypeChange)
                        SettlementChip("Reintegro", CreditNoteSettlementTypeDto.REINTEGRO, state.form.settlementType, onSettlementTypeChange)
                        SettlementChip("Certificado", CreditNoteSettlementTypeDto.CERTIFICADO_REGALO, state.form.settlementType, onSettlementTypeChange)
                    }

                    if (state.form.settlementType == CreditNoteSettlementTypeDto.REINTEGRO) {
                        RefundMethodSelector(
                            methods = state.availableRefundMethods,
                            selectedId = state.form.idFormaPagoReintegro,
                            onSelected = onRefundMethodChange,
                        )
                    }
                }
            }
        }

        item {
            Text("Líneas a devolver", fontWeight = FontWeight.Bold, fontSize = 17.sp, color = AmaxoniaBlue)
        }

        items(invoice.lines, key = { it.idDetalleFactura }) { line ->
            SourceInvoiceLineEditor(
                line = line,
                value = state.form.cantidades[line.idDetalleFactura].orEmpty(),
                onValueChange = { onQuantityChange(line.idDetalleFactura, it) },
                onUseMax = { onUseMax(line.idDetalleFactura) },
            )
        }

        item {
            Button(onClick = onSubmit, modifier = Modifier.fillMaxWidth().height(52.dp), enabled = !state.isSubmitting) {
                Text("Generar nota de crédito")
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
            Icon(Icons.Default.Search, contentDescription = "Buscar", tint = AmaxoniaBlue)
        }
    }
}

@Composable
private fun SummaryBanner(title: String, value: String, amount: Double) {
    ElevatedCard(colors = CardDefaults.elevatedCardColors(containerColor = AmaxoniaBlue), shape = RoundedCornerShape(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(title, color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp)
                Text(value, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 24.sp)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("Monto total", color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp)
                Text(formatAmount(amount), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 24.sp)
            }
        }
    }
}

@Composable
private fun CreditNoteCard(note: CreditNoteSummaryDto, onClick: () -> Unit) {
    ElevatedCard(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick), shape = RoundedCornerShape(16.dp)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(note.codigo, fontWeight = FontWeight.Bold, color = AmaxoniaBlue, fontSize = 16.sp)
                Spacer(modifier = Modifier.weight(1f))
                FiscalStatusChip(status = note.fiscalStatus)
            }
            Text(note.clienteNombre, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
            Text("Factura: ${note.facturaCodigo}", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(note.fecha, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(formatAmount(note.total), fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun SourceInvoiceCard(invoice: CreditNoteSourceInvoiceSummaryDto, onClick: () -> Unit) {
    ElevatedCard(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick), shape = RoundedCornerShape(16.dp)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(invoice.codigo, fontWeight = FontWeight.Bold, color = AmaxoniaBlue, fontSize = 16.sp)
            Text(invoice.clienteNombre, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(invoice.fecha, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Disponible: ${invoice.moneda} ${formatAmount(invoice.remainingAmount)}", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun SourceInvoiceLineEditor(
    line: CreditNoteSourceInvoiceLineDto,
    value: String,
    onValueChange: (String) -> Unit,
    onUseMax: () -> Unit,
) {
    ElevatedCard(shape = RoundedCornerShape(16.dp)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(line.descripcion, fontWeight = FontWeight.Bold)
            Text("Disponible: ${formatQuantity(line.cantidadDisponible)} / Original: ${formatQuantity(line.cantidadOriginal)}", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Total disponible: ${formatAmount(line.totalConIvaDisponible)}", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = value,
                    onValueChange = onValueChange,
                    modifier = Modifier.weight(1f),
                    label = { Text("Cantidad a devolver") },
                    singleLine = true,
                )
                TextButton(onClick = onUseMax) {
                    Text("Max")
                }
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
                modifier = Modifier.size(16.dp)
            )
        },
        colors = AssistChipDefaults.assistChipColors(
            containerColor = if (isConfirmed) Color(0xFFE8F5E9) else Color(0xFFFFF3E0),
            labelColor = if (isConfirmed) Color(0xFF1B5E20) else Color(0xFFE65100),
            leadingIconContentColor = if (isConfirmed) Color(0xFF1B5E20) else Color(0xFFE65100),
        )
    )
}

@Composable
private fun SettlementChip(
    label: String,
    type: CreditNoteSettlementTypeDto,
    selected: CreditNoteSettlementTypeDto,
    onSelected: (CreditNoteSettlementTypeDto) -> Unit,
) {
    FilterChip(selected = selected == type, onClick = { onSelected(type) }, label = { Text(label) })
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
                    }
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
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(detail.codigo, fontWeight = FontWeight.Bold, fontSize = 20.sp, color = AmaxoniaBlue)
                FiscalStatusChip(status = detail.fiscalStatus)
                Text(detail.clienteNombre, fontWeight = FontWeight.Medium)
                Text("Factura origen: ${detail.facturaCodigo}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Monto: ${formatAmount(detail.total)}", fontWeight = FontWeight.Bold)
            }
        }
        items(detail.lines) { line ->
            Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(line.descripcion, fontWeight = FontWeight.Bold)
                    Text("Cantidad: ${formatQuantity(line.cantidad)}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Total: ${formatAmount(line.totalConIva)}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        if (detail.fiscalStatus == CreditNoteFiscalStatusDto.PENDIENTE && detail.fiscalDocument != null) {
            item {
                Button(onClick = onProcessFiscal, enabled = !isSubmitting, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Print, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Procesar nota de crédito fiscal")
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

private fun screenTitle(mode: CreditNotesMode): String = when (mode) {
    CreditNotesMode.LIST -> "Notas de crédito"
    CreditNotesMode.INVOICE_PICKER -> "Seleccionar factura"
    CreditNotesMode.CREATE -> "Nueva nota de crédito"
}

private fun formatAmount(value: Double): String = String.format("%.2f", value)

private fun formatQuantity(value: Double): String {
    return if (value % 1.0 == 0.0) value.toInt().toString() else String.format("%.3f", value)
}
