package com.amaxonia.pos.ui.mesas

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.amaxonia.pos.domain.model.mesas.CuentaMesaResponse

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CuentaMesaScreen(
    mesaNombre: String,
    clientName: String?,
    viewModel: CuentaMesaViewModel,
    onBack: () -> Unit,
    onSelectClient: () -> Unit,
    onPay: (CuentaMesaResponse) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) {
        viewModel.load()
        viewModel.effects.collect { effect ->
            if (effect is CuentaMesaEffect.Pay) onPay(effect.cuenta)
        }
    }
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Cuenta · $mesaNombre") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Cliente", style = MaterialTheme.typography.labelMedium)
                        Text(clientName ?: "Sin seleccionar", fontWeight = FontWeight.SemiBold)
                    }
                    OutlinedButton(onClick = onSelectClient) {
                        Text(if (clientName == null) "Seleccionar" else "Cambiar")
                    }
                }
            }
            item {
                Button(
                    onClick = viewModel::crearCuentaCompleta,
                    enabled = !state.isSaving,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Crear cuenta con todo lo pendiente")
                }
            }
            item { Text("Dividir por producto o cantidad", style = MaterialTheme.typography.titleMedium) }
            items(state.pedidos, key = { "pedido-${it.id}" }) { pedido ->
                val disponible = state.disponible(pedido)
                if (disponible > 0.0) {
                    Card(Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(pedido.itemDescripcion)
                                Text(
                                    "Disponible ${"%.3f".format(disponible)} · $${"%.2f".format(pedido.itemTotalConIva)}",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                            OutlinedTextField(
                                value = state.cantidades[pedido.id].orEmpty(),
                                onValueChange = { viewModel.updateCantidad(pedido.id, it) },
                                label = { Text("Cantidad") },
                                singleLine = true,
                                modifier = Modifier.width(120.dp),
                            )
                        }
                    }
                }
            }
            item {
                OutlinedButton(
                    onClick = viewModel::crearDivision,
                    enabled = !state.isSaving,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Crear división seleccionada")
                }
            }
            if (state.cuentasActivas.isNotEmpty()) {
                item { Text("Cuentas por cobrar", style = MaterialTheme.typography.titleMedium) }
                items(state.cuentasActivas, key = { "cuenta-${it.id}" }) { cuenta ->
                    CuentaActivaCard(
                        cuenta = cuenta,
                        canPay = clientName != null && !state.isSaving,
                        onPay = { viewModel.pagar(cuenta) },
                        onCancel = { viewModel.cancelar(cuenta) },
                    )
                }
            }
            if (state.historicas.isNotEmpty()) {
                item { Text("Histórico", style = MaterialTheme.typography.titleMedium) }
                items(state.historicas, key = { "hist-${it.id}" }) { cuenta ->
                    Text("Cuenta #${cuenta.numeroCuenta} · ${cuenta.estado} · $${"%.2f".format(cuenta.total)}")
                }
            }
            if (state.isLoading || state.isSaving) {
                item { CircularProgressIndicator() }
            }
            state.error?.let { message -> item { Text(message, color = MaterialTheme.colorScheme.error) } }
            state.info?.let { message -> item { Text(message, color = MaterialTheme.colorScheme.primary) } }
        }
    }
}

@Composable
private fun CuentaActivaCard(
    cuenta: CuentaMesaResponse,
    canPay: Boolean,
    onPay: () -> Unit,
    onCancel: () -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Cuenta #${cuenta.numeroCuenta}", fontWeight = FontWeight.Bold)
            cuenta.detalle.forEach { line ->
                Text("${line.itemDescripcion} × ${line.cantidad}", style = MaterialTheme.typography.bodySmall)
            }
            Text("Total $${"%.2f".format(cuenta.total)}", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onCancel) { Text("Cancelar") }
                Button(onClick = onPay, enabled = canPay) { Text("Cobrar") }
            }
        }
    }
}
