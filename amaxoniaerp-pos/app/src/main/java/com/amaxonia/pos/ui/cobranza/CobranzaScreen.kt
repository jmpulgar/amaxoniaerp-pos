package com.amaxonia.pos.ui.cobranza

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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.amaxonia.pos.composition.AppGraph
import com.amaxonia.pos.domain.model.cobranza.CobranzaPendingInvoiceDto
import com.amaxonia.pos.domain.model.cobranza.CreateCobranzaResponseDto
import com.amaxonia.pos.domain.model.money.Money
import com.amaxonia.pos.domain.usecase.cobranza.CobranzaTotals
import com.amaxonia.pos.domain.usecase.cobranza.isCashForma
import com.amaxonia.pos.ui.common.injectedViewModel
import java.util.Locale

private val CONTENT_MAX_WIDTH = 760.dp

/**
 * Cobranza de facturas pendientes de un cliente con una o varias formas de pago
 * (equivalente al módulo web `cobranza_cliente`).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CobranzaScreen(
    selectedCliente: CobranzaCliente?,
    onBack: () -> Unit,
    onSelectCliente: () -> Unit,
    viewModel: CobranzaViewModel = injectedViewModel { AppGraph.cobranza.cobranzaViewModel() },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(selectedCliente) {
        if (selectedCliente != null && selectedCliente.id != state.cliente?.id) {
            viewModel.onAction(CobranzaAction.SelectCliente(selectedCliente))
        }
    }
    LaunchedEffect(state.error) {
        state.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.onAction(CobranzaAction.DismissError)
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Cobranza a clientes",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver", tint = MaterialTheme.colorScheme.primary)
                    }
                },
                actions = {
                    if (state.cliente != null) {
                        IconButton(onClick = { viewModel.onAction(CobranzaAction.Reload) }) {
                            Icon(Icons.Default.Refresh, "Actualizar", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        bottomBar = {
            if (state.selectedAmounts.isNotEmpty()) {
                CobranzaBottomBar(
                    totals = state.totals,
                    submitting = state.isSubmitting,
                    onSubmit = { viewModel.onAction(CobranzaAction.Submit) },
                )
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            LazyColumn(
                modifier = Modifier.widthIn(max = CONTENT_MAX_WIDTH).fillMaxWidth(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item { ClienteCard(state.cliente, onSelectCliente) }
                if (state.cliente != null) {
                    invoicesSection(state, viewModel::onAction)
                    if (state.selectedAmounts.isNotEmpty()) {
                        paymentsSection(state, viewModel::onAction)
                    }
                }
            }
        }
    }

    state.result?.let { result ->
        CobranzaResultDialog(result = result, onDismiss = { viewModel.onAction(CobranzaAction.DismissResult) })
    }
}

@Composable
private fun ClienteCard(
    cliente: CobranzaCliente?,
    onSelectCliente: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onSelectCliente),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Person, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Cliente", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    cliente?.nombre ?: "Selecciona el cliente a cobrar",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            TextButton(onClick = onSelectCliente) { Text(if (cliente == null) "Seleccionar" else "Cambiar") }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.invoicesSection(
    state: CobranzaState,
    onAction: (CobranzaAction) -> Unit,
) {
    item {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Facturas pendientes", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    "Saldo total: ${formatMoney(state.totalPendiente)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (state.invoices.isNotEmpty()) {
                TextButton(onClick = { onAction(CobranzaAction.ToggleAll) }) {
                    Text(if (state.selectedAmounts.size == state.invoices.size) "Quitar todas" else "Seleccionar todas")
                }
            }
        }
    }
    when {
        state.isLoadingInvoices ->
            item {
                Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            }
        state.invoices.isEmpty() ->
            item {
                Text(
                    "El cliente no tiene facturas pendientes por cobrar.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 16.dp),
                )
            }
        else ->
            items(state.invoices, key = { it.idFactura }) { invoice ->
                InvoiceRow(
                    invoice = invoice,
                    amount = state.selectedAmounts[invoice.idFactura],
                    onToggle = { onAction(CobranzaAction.ToggleInvoice(invoice.idFactura)) },
                    onAmountChange = { onAction(CobranzaAction.SetInvoiceAmount(invoice.idFactura, it)) },
                )
            }
    }
}

@Composable
private fun InvoiceRow(
    invoice: CobranzaPendingInvoiceDto,
    amount: String?,
    onToggle: () -> Unit,
    onAmountChange: (String) -> Unit,
) {
    val selected = amount != null
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f) else MaterialTheme.colorScheme.surface,
        tonalElevation = if (selected) 0.dp else 1.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(horizontal = 8.dp, vertical = 8.dp)) {
            Row(Modifier.clickable(onClick = onToggle), verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = selected, onCheckedChange = { onToggle() })
                Column(Modifier.weight(1f)) {
                    Text(
                        "Factura #${invoice.codFactura}" +
                            invoice.numeroFiscal
                                .takeIf { it.isNotBlank() }
                                ?.let { " · $it" }
                                .orEmpty(),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        "Emisión ${invoice.fecha}" +
                            invoice.fechaVencimiento
                                .takeIf { it.isNotBlank() }
                                ?.let { " · Vence $it" }
                                .orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Column(horizontalAlignment = Alignment.End, modifier = Modifier.padding(end = 8.dp)) {
                    Text(formatMoney(invoice.saldo), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
                    if (invoice.montoOriginal != invoice.saldo) {
                        Text(
                            "de ${formatMoney(invoice.montoOriginal)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            if (selected) {
                MoneyField(
                    label = "Monto a cobrar",
                    value = amount.orEmpty(),
                    onValueChange = onAmountChange,
                    modifier = Modifier.fillMaxWidth().padding(start = 48.dp, end = 8.dp, bottom = 4.dp),
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
private fun androidx.compose.foundation.lazy.LazyListScope.paymentsSection(
    state: CobranzaState,
    onAction: (CobranzaAction) -> Unit,
) {
    item {
        Column {
            Spacer(Modifier.height(4.dp))
            Text("Formas de pago", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                state.formasPago.forEach { forma ->
                    AssistChip(
                        onClick = { onAction(CobranzaAction.AddPayment(forma)) },
                        label = { Text(forma.descripcion ?: forma.siglas.orEmpty()) },
                        leadingIcon = { Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    )
                }
            }
        }
    }
    items(state.payments, key = { it.id }) { line -> PaymentLineCard(line, onAction) }
    item {
        OutlinedTextField(
            value = state.observacion,
            onValueChange = { onAction(CobranzaAction.SetObservacion(it)) },
            label = { Text("Observación (opcional)") },
            modifier = Modifier.fillMaxWidth(),
            maxLines = 3,
        )
    }
}

@Composable
private fun PaymentLineCard(
    line: PaymentInput,
    onAction: (CobranzaAction) -> Unit,
) {
    val isCash = isCashForma(line.formaPago)
    Surface(shape = RoundedCornerShape(14.dp), tonalElevation = 1.dp, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    line.formaPago.descripcion ?: line.formaPago.siglas.orEmpty(),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { onAction(CobranzaAction.RemovePayment(line.id)) }) {
                    Icon(Icons.Default.Close, contentDescription = "Quitar forma de pago")
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MoneyField(
                    label = if (isCash) "Recibido" else "Monto",
                    value = line.amountInput,
                    onValueChange = { onAction(CobranzaAction.SetPaymentAmount(line.id, it)) },
                    modifier = Modifier.weight(1f),
                )
                if (!isCash) {
                    OutlinedTextField(
                        value = line.referencia,
                        onValueChange = { onAction(CobranzaAction.SetPaymentReference(line.id, it.take(REFERENCIA_MAX))) },
                        label = { Text("Referencia") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun CobranzaBottomBar(
    totals: CobranzaTotals,
    submitting: Boolean,
    onSubmit: () -> Unit,
) {
    Surface(shadowElevation = 12.dp, color = MaterialTheme.colorScheme.surface) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Column(Modifier.widthIn(max = CONTENT_MAX_WIDTH).fillMaxWidth().padding(16.dp)) {
                TotalRow("Total a cobrar", totals.totalACobrar, emphasized = true)
                TotalRow("Recibido", totals.totalRecibido)
                if (totals.cambio > Money.ZERO) TotalRow("Vuelto", totals.cambio)
                if (totals.faltante > Money.ZERO) TotalRow("Falta", totals.faltante)
                totals.error?.takeIf { totals.totalRecibido > Money.ZERO }?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
                Spacer(Modifier.height(10.dp))
                Button(
                    onClick = onSubmit,
                    enabled = totals.isReady && !submitting,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                ) {
                    if (submitting) {
                        CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                    } else {
                        Text("Cobrar ${formatMoney(totals.totalACobrar.toDouble())}", fontWeight = FontWeight.ExtraBold)
                    }
                }
            }
        }
    }
}

@Composable
private fun TotalRow(
    label: String,
    amount: Money,
    emphasized: Boolean = false,
) {
    val style = if (emphasized) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium
    Row(Modifier.fillMaxWidth()) {
        Text(label, style = style, modifier = Modifier.weight(1f))
        Text(formatMoney(amount.toDouble()), style = style, fontWeight = if (emphasized) FontWeight.ExtraBold else FontWeight.SemiBold)
    }
}

@Composable
private fun MoneyField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { input -> onValueChange(input.filter { it.isDigit() || it == '.' || it == ',' }.replace(',', '.')) },
        label = { Text(label) },
        prefix = { Text("$ ") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = modifier,
    )
}

@Composable
private fun CobranzaResultDialog(
    result: CreateCobranzaResponseDto,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Cobro registrado") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Recibo #${result.nroRecibo} por ${formatMoney(result.montoTotal)}", fontWeight = FontWeight.Bold)
                result.facturas.forEach { factura ->
                    Text(
                        "Factura #${factura.codFactura}: abonado ${formatMoney(factura.montoAplicado)} · " +
                            if (factura.pagada) "pagada" else "saldo ${formatMoney(factura.saldoNuevo)}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                if (result.duplicado) {
                    Text(
                        "Este cobro ya se había registrado; no se duplicó.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = { Button(onClick = onDismiss) { Text("Aceptar") } },
        dismissButton = null,
    )
}

private const val REFERENCIA_MAX = 20

private fun formatMoney(amount: Double): String = "$ " + String.format(Locale.US, "%,.2f", amount)
